import { z } from "zod";

import { postToBackend } from "@/lib/api/backend";
import { clearSession, readRefreshToken } from "@/lib/session";

/**
 * Ends the session at both ends.
 *
 * <p>The cookies are cleared whatever the API says. A caller who has asked to
 * sign out must end up signed out of this browser even if the API is
 * unreachable; leaving them holding a session because the network failed would
 * be the wrong way round.
 */
export async function POST() {
  const refreshToken = await readRefreshToken();

  if (refreshToken) {
    await postToBackend("/api/v1/auth/logout", { refreshToken }, z.unknown());
  }
  await clearSession();

  return Response.json({ ok: true });
}
