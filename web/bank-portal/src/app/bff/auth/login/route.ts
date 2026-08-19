import { loginResponseSchema } from "@/lib/api/contracts";
import { postToBackend } from "@/lib/api/backend";
import { storeSession } from "@/lib/session";

/**
 * Bank-user sign-in, proxied.
 *
 * <p>The browser posts here rather than to Spring Boot, for two reasons. The
 * API needs no public route and no CORS grant to the portal origin; and the
 * tokens it returns are put straight into httpOnly cookies, so no script on the
 * page can ever read them. Nothing token-shaped is returned in the body.
 */
export async function POST(request: Request) {
  const body: unknown = await request.json().catch(() => null);
  const result = await postToBackend("/api/v1/auth/bank/login", body, loginResponseSchema);

  if (!result.ok) {
    return Response.json(
      { ok: false, reason: result.reason, correlationId: result.correlationId },
      { status: result.status ?? 502 },
    );
  }

  if (result.data.status === "MFA_REQUIRED") {
    return Response.json({
      ok: true,
      status: "MFA_REQUIRED",
      mfaChallengeId: result.data.mfaChallengeId,
      mfaExpiresInSeconds: result.data.mfaExpiresInSeconds,
    });
  }

  if (!result.data.tokens) {
    return Response.json(
      { ok: false, reason: "The API reported a sign-in without issuing a session." },
      { status: 502 },
    );
  }

  await storeSession(result.data.tokens);

  // The user is echoed so the form can greet by name without a second round
  // trip. The tokens are not: they are in the cookie jar and nowhere else.
  return Response.json({ ok: true, status: "AUTHENTICATED", user: result.data.user });
}
