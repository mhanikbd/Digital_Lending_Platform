import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Produces a self-contained server bundle for the runtime container stage.
  output: "standalone",

  // Do not advertise the framework version.
  poweredByHeader: false,

  reactStrictMode: true,

  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "no-referrer" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
