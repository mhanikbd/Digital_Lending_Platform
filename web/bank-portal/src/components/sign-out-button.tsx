"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

/**
 * Ends the session.
 *
 * <p>Posts to the portal's own route handler, which revokes the refresh token
 * at the API and clears the cookies. There is nothing to clear on this side
 * because the browser never held the tokens in the first place.
 */
export function SignOutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);

  async function signOut() {
    setPending(true);
    try {
      await fetch("/bff/auth/logout", { method: "POST" });
    } catch {
      // The cookies are cleared by the handler regardless, and a failed call
      // must not leave someone stuck on a page they asked to leave.
    } finally {
      router.replace("/login");
      router.refresh();
    }
  }

  return (
    <button
      type="button"
      onClick={signOut}
      disabled={pending}
      className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-60 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
    >
      {pending ? "Signing out…" : "Sign out"}
    </button>
  );
}
