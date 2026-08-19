import "server-only";

/**
 * Server-side configuration.
 *
 * The backend URL is deliberately not a NEXT_PUBLIC_ value: the browser must
 * never address Spring Boot directly. Requests go through this application's
 * own route handlers, which keeps the API reachable only inside the network and
 * removes the need for CORS between the portal and the API.
 */
export const serverEnv = {
  backendBaseUrl: process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080",
  requestTimeoutMs: Number(process.env.BACKEND_TIMEOUT_MS ?? 5000),
} as const;

/** Safe to render in the browser. */
export const publicEnv = {
  appEnv: process.env.NEXT_PUBLIC_APP_ENV ?? "local",
} as const;
