import { loginResponseSchema } from "@/lib/api/contracts";
import { postToBackend } from "@/lib/api/backend";
import { storeSession } from "@/lib/session";

/**
 * Second factor for a sign-in that answered MFA_REQUIRED.
 *
 * <p>Only the challenge id and the code cross this boundary. The first step
 * issued no tokens, so there is no half-session to clean up if this is refused.
 */
export async function POST(request: Request) {
  const body: unknown = await request.json().catch(() => null);
  const result = await postToBackend("/api/v1/auth/bank/mfa", body, loginResponseSchema);

  if (!result.ok) {
    return Response.json(
      { ok: false, reason: result.reason, correlationId: result.correlationId },
      { status: result.status ?? 502 },
    );
  }
  if (!result.data.tokens) {
    return Response.json(
      { ok: false, reason: "The API accepted the code without issuing a session." },
      { status: 502 },
    );
  }

  await storeSession(result.data.tokens);
  return Response.json({ ok: true, status: "AUTHENTICATED", user: result.data.user });
}
