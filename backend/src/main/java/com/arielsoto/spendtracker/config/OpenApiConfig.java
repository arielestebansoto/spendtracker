package com.arielsoto.spendtracker.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Header of the OpenAPI spec that springdoc generates from the controllers, and the parts it cannot
 * infer: the security schemes and the error responses. Served only with the dev profile (springdoc.*
 * in application.yml and application-dev.yml).
 *
 * Every endpoint needs the session cookie that the OAuth2 login sets, and state-changing requests
 * also need the CSRF token. Swagger UI on the backend's host sends both once you log in through the
 * frontend, since cookies are shared across ports of the same host.
 */
@Configuration
class OpenApiConfig {

    static final String SESSION_COOKIE = "sessionCookie";

    static final String CSRF_HEADER = "csrfHeader";

    static final String PROBLEM_DETAIL = "ProblemDetail";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info()
                .title("SpendTracker API")
                .version("v1")
                .description("""
                    REST API of SpendTracker under /api/v1.

                    **Authentication.** Log in with Google or GitHub through the frontend; the backend sets a \
                    JSESSIONID session cookie. A request without a valid session gets 401 with an empty body.

                    **CSRF.** POST, PUT and DELETE need the X-XSRF-TOKEN header, with the value of the \
                    XSRF-TOKEN cookie (not the masked token in the body of GET /api/v1/security/csrf, which \
                    makes sure the cookie is set). Without it the request gets 403 with an empty body.

                    **Errors.** Every other error is an RFC 9457 Problem Detail (application/problem+json)."""))
            .components(new Components()
                .addSecuritySchemes(SESSION_COOKIE, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("JSESSIONID")
                    .description("Session cookie set by the OAuth2 login."))
                .addSecuritySchemes(CSRF_HEADER, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-XSRF-TOKEN")
                    .description("Value of the XSRF-TOKEN cookie. Required on POST, PUT and DELETE.")))
            .addSecurityItem(new SecurityRequirement()
                .addList(SESSION_COOKIE)
                .addList(CSRF_HEADER));
    }

    /**
     * Adds the errors that every operation can return, which springdoc cannot infer from
     * ApiExceptionHandler: the empty 401 and 403 of the Spring Security filters, and a Problem Detail
     * for any other error.
     *
     * The ProblemDetail schema is added here, not in openApi(): springdoc drops the schemas that no
     * operation references before the customizers run.
     */
    @Bean
    OpenApiCustomizer errorResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_DETAIL, problemDetailSchema());
            openApi.getPaths().values().forEach(OpenApiConfig::addErrorResponses);
        };
    }

    private static void addErrorResponses(PathItem path) {
        path.readOperationsMap().forEach((method, operation) -> {
            ApiResponses responses = operation.getResponses();
            responses.addApiResponse("401", new ApiResponse()
                .description("Not logged in. Empty body."));
            if (method != HttpMethod.GET) {
                responses.addApiResponse("403", new ApiResponse()
                    .description("Missing or invalid X-XSRF-TOKEN header. Empty body."));
            }
            responses.addApiResponse("default", new ApiResponse()
                .description("Any other error, as a Problem Detail.")
                .content(new Content().addMediaType("application/problem+json", new MediaType()
                    .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL)))));
        });
    }

    // The shape that ApiExceptionHandler returns. Extension properties go at the top level.
    private static Schema<?> problemDetailSchema() {
        return new ObjectSchema()
            .description("""
                RFC 9457 Problem Detail. Extension properties: a failed validation adds `errors` (the \
                invalid fields, each with `field` and `message`); a rejected receipt upload adds `reason` \
                (`invalid_file`, `rate_limit` with `window`, `ai_quota` with `resourceType`).""")
            .addProperty("type", new StringSchema().format("uri").example("about:blank"))
            .addProperty("title", new StringSchema().example("Not Found"))
            .addProperty("status", new IntegerSchema().example(404))
            .addProperty("detail", new StringSchema().example("Spend not found"))
            .addProperty("instance", new StringSchema().format("uri").example("/api/v1/spends/{id}"))
            .additionalProperties(true);
    }
}
