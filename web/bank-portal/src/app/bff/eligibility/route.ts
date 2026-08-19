import { postToBackend } from "@/lib/api/backend";
import { eligibilitySchema } from "@/lib/api/contracts";
import { readAccessToken } from "@/lib/session";

/**
 * Backend-for-frontend proxy for the eligibility check.
 *
 * <p>Same reasoning as the calculator: the token stays in an httpOnly cookie,
 * and the decision is the backend's. The scope filtering that decides whose
 * customers may be assessed is applied there too, so this route has nothing to
 * enforce beyond having a session at all.
 */
export async function POST(request: Request) {
  const token = await readAccessToken();
  if (!token) {
    return Response.json({ ok: false, reason: "Your session has expired." }, { status: 401 });
  }

  const body: unknown = await request.json().catch(() => ({}));
  const result = await postToBackend("/api/v1/eligibility/check", body, eligibilitySchema, token);

  if (!result.ok) {
    return Response.json(
      { ok: false, reason: result.reason, correlationId: result.correlationId },
      { status: result.status && result.status < 500 ? result.status : 502 },
    );
  }

  return Response.json(
    { ok: true, assessment: result.data, correlationId: result.correlationId },
    { headers: { "Cache-Control": "no-store" } },
  );
}
