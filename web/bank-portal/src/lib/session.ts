import "server-only";

import { cookies } from "next/headers";

import { authenticatedUserSchema, envelopeOf, type AuthenticatedUser, type TokenPair } from "@/lib/api/contracts";
import { serverEnv } from "@/lib/env";

/**
 * The signed-in session, as the portal keeps it.
 *
 * <p>Tokens live in httpOnly cookies and are never handed to the browser as
 * JavaScript values. That is the whole reason this application proxies the API
 * rather than letting the browser call Spring Boot: a token the page can read
 * is a token any injected script can read too.
 */
const ACCESS_COOKIE = "dlp_at";
const REFRESH_COOKIE = "dlp_rt";

/** Secure is conditional only so the cookie survives plain http on a workstation. */
const secure = process.env.NODE_ENV === "production";

export async function storeSession(tokens: TokenPair): Promise<void> {
  const jar = await cookies();
  const base = {
    httpOnly: true,
    sameSite: "lax" as const,
    secure,
    path: "/",
  };
  jar.set(ACCESS_COOKIE, tokens.accessToken, { ...base, maxAge: tokens.expiresInSeconds });
  jar.set(REFRESH_COOKIE, tokens.refreshToken, { ...base, maxAge: tokens.refreshExpiresInSeconds });
}

export async function clearSession(): Promise<void> {
  const jar = await cookies();
  jar.delete(ACCESS_COOKIE);
  jar.delete(REFRESH_COOKIE);
}

export async function readAccessToken(): Promise<string | null> {
  return (await cookies()).get(ACCESS_COOKIE)?.value ?? null;
}

export async function readRefreshToken(): Promise<string | null> {
  return (await cookies()).get(REFRESH_COOKIE)?.value ?? null;
}

/**
 * Who is signed in, according to the API rather than according to the cookie.
 *
 * <p>The access token is asked to prove itself on every render instead of being
 * decoded here. A token this application decoded itself would be a token it
 * trusted without checking the signature, and an expired or revoked session
 * would keep rendering a header for someone who is no longer signed in.
 *
 * <p>Returns null rather than throwing: a signed-out visitor is an ordinary
 * state, not an error.
 */
export async function currentUser(): Promise<AuthenticatedUser | null> {
  const token = await readAccessToken();
  if (!token) {
    return null;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), serverEnv.requestTimeoutMs);
  try {
    const response = await fetch(`${serverEnv.backendBaseUrl}/api/v1/auth/me`, {
      headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
      signal: controller.signal,
      cache: "no-store",
    });
    if (!response.ok) {
      return null;
    }
    const parsed = envelopeOf(authenticatedUserSchema).safeParse(await response.json());
    return parsed.success && parsed.data.success ? (parsed.data.data ?? null) : null;
  } catch {
    // An unreachable API is not a signed-in session.
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

/** True when the signed-in user holds the permission. Server-side only. */
export function permits(user: AuthenticatedUser | null, permission: string): boolean {
  return user?.permissions.includes(permission) ?? false;
}
