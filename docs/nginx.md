# Nginx reverse proxy (production)

Nginx terminates TLS for `spendtracker.arielsoto.dev` and proxies to the `frontend` (port 3000) and `backend` (port 8080) containers over the external Docker network `web` (see `docker-compose.prod.yml`). The config is not deployed from this repo: it lives on the EC2 host and is edited by hand. This page is the reference copy; update it whenever the server config changes.

| File on the EC2 host | Mounted in the container as |
| --- | --- |
| `server/nginx/nginx.conf` | `/etc/nginx/nginx.conf` |
| `server/nginx/conf.d/spendtracker.arielsoto.dev.conf` | `/etc/nginx/conf.d/spendtracker.arielsoto.dev.conf` |

## Rate limits and body size

All zones are keyed by client IP (`$binary_remote_addr`). Nginx receives clients directly, with no load balancer or CDN in front, so `$remote_addr` is the client's IP. If a proxy or CDN is ever added in front, set up `real_ip` first, or every client shares one bucket.

| Location | Zone | Rate | Burst | Max body |
| --- | --- | --- | --- | --- |
| `/` (frontend) | `frontend` | 30 r/s | 50 | 10M |
| `/api` | `api` | 10 r/s | 20 | 10M |
| `/api/login`, `/oauth2`, `/login/oauth2` | `auth` | 5 r/m | 5 | 10M |
| `= /api/v1/spends/from-receipt` | `upload` | 10 r/m | 3 | 6M |

A rejected request gets **429** (`limit_req_status 429`; the nginx default is 503) or **413** for a body over the limit. These responses are nginx HTML pages, not JSON, so the frontend must not expect a JSON body on 429 or 413.

### Receipt upload

The upload endpoint has its own `upload` zone and body size, so a flood of multi-megabyte uploads is cut at nginx before Spring parses them, and it does not use up the `api` budget of the rest of the app. It is one layer of three:

1. **Nginx, per IP**: 10 per minute, burst 3 (this page).
2. **Backend, per user**: 5 per minute and 30 per day (`app.receipt-upload-limit`, `ReceiptUploadRateLimiter`). The nginx limit is looser on purpose, so several users behind one IP are not blocked by nginx.
3. **Backend, AI quota**: monthly Textract and Bedrock usage per user (`app.ai-usage`).

**Keep the body sizes in sync.** The backend accepts a receipt of up to 5 MB (`spring.servlet.multipart.max-file-size: 5MB`, `max-request-size: 6MB` in `backend/src/main/resources/application.yml`, repeated in `ReceiptValidator`). `client_max_body_size 6M` on the upload location leaves room for the multipart overhead. If the backend limit changes, change this one too.

## Config

### `nginx.conf`

```nginx
events {
}

http {
    # Hide send server info
    server_tokens off;

    log_format main escape=json
        '{'
        '"timestamp":"$time_iso8601",'
        '"remote_addr":"$remote_addr",'
        '"host":"$host",'
        '"request":"$request",'
        '"status":"$status",'
        '"request_time":"$request_time",'
        '"upstream_response_time":"$upstream_response_time",'
        '"user_agent":"$http_user_agent"'
        '}';

    access_log /dev/stdout main;
    error_log /dev/stderr warn;

    proxy_connect_timeout 5s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
    send_timeout 60s;

    client_max_body_size 10M;

    gzip on;
    gzip_vary on;
    gzip_min_length 1024;

    gzip_types
        text/plain
        text/css
        application/json
        application/javascript
        application/xml
        text/xml
        image/svg+xml;

    limit_req_zone $binary_remote_addr zone=frontend:10m rate=30r/s;
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
    limit_req_zone $binary_remote_addr zone=auth:10m rate=5r/m;
    limit_req_zone $binary_remote_addr zone=upload:10m rate=10r/m;

    # Rejected requests get 429 Too Many Requests instead of the default 503.
    limit_req_status 429;

    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;

    # Impide que tu aplicación se cargue dentro de un iframe
    add_header X-Frame-Options "DENY" always;

    # Evita que el navegador "adivine" tipos de archivo
    add_header X-Content-Type-Options "nosniff" always;

    # Controla qué información se envía en el header:
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # Monto todas las configuraciones
    include /etc/nginx/conf.d/*.conf;
}
```

### `conf.d/spendtracker.arielsoto.dev.conf`

```nginx
server {

    listen 80;

    server_name spendtracker.arielsoto.dev;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }


    location / {
        return 301 https://$host$request_uri;
    }
}

server {

    listen 443 ssl;
    http2 on;

    server_name spendtracker.arielsoto.dev;

    ssl_certificate     /etc/letsencrypt/live/spendtracker.arielsoto.dev/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/spendtracker.arielsoto.dev/privkey.pem;

    location / {
        limit_req zone=frontend burst=50 nodelay;

        proxy_pass http://frontend:3000;
    }

    location /api/login {
        limit_req zone=auth burst=5 nodelay;

        proxy_pass http://backend:8080;
    }

    location /oauth2 {
        limit_req zone=auth burst=5 nodelay;

        proxy_pass http://backend:8080;
    }

    location /login/oauth2 {
        limit_req zone=auth burst=5 nodelay;

        proxy_pass http://backend:8080;
    }

    # Receipt upload: own rate limit and body size (5 MB receipt + multipart overhead).
    # An exact match wins over the /api prefix below.
    location = /api/v1/spends/from-receipt {
        limit_req zone=upload burst=3 nodelay;
        client_max_body_size 6M;

        proxy_pass http://backend:8080;
    }

    location /api {
        limit_req zone=api burst=20 nodelay;

        proxy_pass http://backend:8080;

        # Buffering when OCR async, heavy responses:
        # proxy_buffering on;
        # proxy_buffers 16 16k;
        # proxy_buffer_size 16k;

    }
}
```

## Applying a change

On the EC2 host, edit the files under `server/nginx/`, then test and reload inside the nginx container (find its name with `docker ps`):

```bash
docker exec <nginx-container> nginx -t
docker exec <nginx-container> nginx -s reload
```

`nginx -t` validates the config without applying it; only reload if it reports `syntax is ok` and `test is successful`. A reload keeps open connections, so it causes no downtime.

To check the upload limit after a reload, send more than 4 requests in a row to the endpoint. Each one is rejected by the backend (no session), but from the 5th one nginx answers 429 before the backend sees it:

```bash
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST https://spendtracker.arielsoto.dev/api/v1/spends/from-receipt
done
```

## Optional

The global `client_max_body_size 10M` could drop to `1M`: the receipt upload is the only endpoint that takes a file, and it has its own limit. Not applied yet.
