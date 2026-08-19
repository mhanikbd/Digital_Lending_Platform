import { fetchFromBackend } from "@/lib/api/backend";
import { platformHealthSchema } from "@/lib/api/contracts";

/**
 * Backend-for-frontend proxy for the platform connectivity view.
 *
 * The browser calls this instead of Spring Boot directly, so the API needs no
 * public route and no CORS grant to the portal origin.
 */
export async function GET() {
  const result = await fetchFromBackend("/api/v1/platform/health", platformHealthSchema);

  if (!result.ok) {
    return Response.json(
      { ok: false, reason: result.reason, correlationId: result.correlationId },
      { status: 502 },
    );
  }

  return Response.json(
    { ok: true, health: result.data, correlationId: result.correlationId },
    { headers: { "Cache-Control": "no-store" } },
  );
}
