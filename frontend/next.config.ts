import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  // `next dev` only: hosts other than localhost allowed to load the dev server's resources, such as
  // the machine's Wi-Fi IP when testing on a phone (docs/local-development.md). Comma-separated.
  allowedDevOrigins: process.env.ALLOWED_DEV_ORIGINS?.split(",").map((origin) => origin.trim()),
};

export default nextConfig;
