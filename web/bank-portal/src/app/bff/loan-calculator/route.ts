import { postToBackend } from "@/lib/api/backend";
import { loanQuoteSchema } from "@/lib/api/contracts";
import { readAccessToken } from "@/lib/session";

/**
 * Backend-for-frontend proxy for the loan calculator.
 *
 * <p>The browser never calls Spring Boot directly, so the API needs no public
 * route and no CORS grant - and the access token stays in an httpOnly cookie
 * the page cannot read.
 *
 * <p>The body is passed through unexamined. Validating it here would put a
 * second opinion about product terms in the browser tier, which is exactly what
 * §20 says must not happen: the backend's answer is the authoritative one, and
 * its refusals are part of that answer.
 */
export async function POST(request: Request) {
  const token = await readAccessToken();
  if (!token) {
    return Response.json({ ok: false, reason: "Your session has expired." }, { status: 401 });
  }

  const body: unknown = await request.json().catch(() => ({}));
  const result = await postToBackend("/api/v1/loan-calculator", body, loanQuoteSchema, token);

  if (!result.ok) {
    return Response.json(
      { ok: false, reason: result.reason, correlationId: result.correlationId },
      { status: result.status && result.status < 500 ? result.status : 502 },
    );
  }

  return Response.json(
    { ok: true, quote: result.data, correlationId: result.correlationId },
    { headers: { "Cache-Control": "no-store" } },
  );
}
