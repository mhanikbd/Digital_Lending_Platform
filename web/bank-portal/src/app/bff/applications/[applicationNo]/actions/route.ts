import { postToBackend } from "@/lib/api/backend";
import { applicationDetailSchema } from "@/lib/api/contracts";
import { readAccessToken } from "@/lib/session";

/**
 * Backend-for-frontend proxy for taking a workflow action.
 *
 * <p>The body is passed through unexamined. Deciding here whether an action is
 * allowed would put a second opinion about the workflow in the browser tier -
 * which is the same hard-coding the specification forbids in the backend, merely
 * moved somewhere harder to audit. The backend refuses what it refuses, and its
 * message is what the operator sees.
 */
export async function POST(request: Request, context: RouteContext<"/bff/applications/[applicationNo]/actions">) {
  const token = await readAccessToken();
  if (!token) {
    return Response.json({ ok: false, reason: "Your session has expired." }, { status: 401 });
  }

  const { applicationNo } = await context.params;
  const body: unknown = await request.json().catch(() => ({}));

  const result = await postToBackend(
    `/api/v1/loan-applications/${encodeURIComponent(applicationNo)}/actions`,
    body,
    applicationDetailSchema,
    token,
  );

  if (!result.ok) {
    return Response.json(
      { ok: false, reason: result.reason, correlationId: result.correlationId },
      { status: result.status && result.status < 500 ? result.status : 502 },
    );
  }

  return Response.json(
    { ok: true, application: result.data, correlationId: result.correlationId },
    { headers: { "Cache-Control": "no-store" } },
  );
}
