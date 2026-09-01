import type { NextConfig } from "next";

// Backend base URL for local/dev proxying. In production this rewrite isn't
// needed — Caddy handles routing /api to the backend (see infra/README.md) —
// but keeping it here is harmless and lets `next start` also work standalone.
const apiBaseUrl = process.env.API_BASE_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${apiBaseUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
