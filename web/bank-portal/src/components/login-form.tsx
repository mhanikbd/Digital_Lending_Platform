"use client";

import { useId, useState, type FormEvent } from "react";
import { z } from "zod";

import { ArrowRightIcon, EyeIcon, EyeOffIcon, LockIcon, UserIcon } from "@/components/icons";
import { cn } from "@/lib/cn";

/**
 * Bank-user sign-in form.
 *
 * Shape only, deliberately. Authentication is Milestone 5, so there is no
 * endpoint to post to: the form validates locally and says so rather than
 * pretending to sign anyone in. The credential never leaves the browser, and
 * is never written to the console or to any log.
 *
 * When Milestone 5 lands, `onSubmit` posts to a portal route handler that
 * proxies the API, and the notice below is replaced by the server's error.
 */
const credentialsSchema = z.object({
  username: z.string().trim().min(1, "Enter your employee ID or username."),
  password: z.string().min(1, "Enter your password."),
});

type Field = "username" | "password";

const FIELD_STYLES =
  "w-full rounded-lg border bg-field py-[var(--pad-field)] pl-10 text-sm text-ink shadow-xs outline-none transition " +
  "placeholder:text-ink-subtle focus:ring-2 focus:ring-brand/35";

const ERROR_TEXT = "mt-1.5 text-xs text-red-600 theme-dark:text-red-400";

export function LoginForm() {
  const usernameId = useId();
  const passwordId = useId();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [revealed, setRevealed] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [notice, setNotice] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = credentialsSchema.safeParse({ username, password });

    if (!result.success) {
      const next: Partial<Record<Field, string>> = {};
      for (const issue of result.error.issues) {
        const field = issue.path[0];
        if ((field === "username" || field === "password") && !next[field]) {
          next[field] = issue.message;
        }
      }
      setErrors(next);
      setNotice(null);
      return;
    }

    setErrors({});
    setNotice(
      "Bank-user authentication arrives in Milestone 5. Your credentials were checked for completeness in this browser only — nothing was sent and no session was created.",
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mt-[var(--gap-stack)] space-y-[clamp(0.6rem,1.9vh,1.6rem)]">
      <div>
        <label htmlFor={usernameId} className="block text-sm font-medium text-ink">
          Employee ID
        </label>
        <div className="relative mt-1.5">
          <UserIcon className="pointer-events-none absolute top-1/2 left-3 size-4.5 -translate-y-1/2 text-ink-subtle" />
          <input
            id={usernameId}
            name="username"
            type="text"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="Enter your employee ID"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            aria-invalid={errors.username ? true : undefined}
            aria-describedby={errors.username ? `${usernameId}-error` : undefined}
            className={cn(
              FIELD_STYLES,
              "pr-3",
              errors.username ? "border-red-500" : "border-line focus:border-brand",
            )}
          />
        </div>
        {errors.username && (
          <p id={`${usernameId}-error`} className={ERROR_TEXT}>
            {errors.username}
          </p>
        )}
      </div>

      <div>
        <label htmlFor={passwordId} className="block text-sm font-medium text-ink">
          Password
        </label>
        <div className="relative mt-1.5">
          <LockIcon className="pointer-events-none absolute top-1/2 left-3 size-4.5 -translate-y-1/2 text-ink-subtle" />
          <input
            id={passwordId}
            name="password"
            type={revealed ? "text" : "password"}
            autoComplete="current-password"
            placeholder="Enter your password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={errors.password ? true : undefined}
            aria-describedby={errors.password ? `${passwordId}-error` : undefined}
            className={cn(
              FIELD_STYLES,
              "pr-11",
              errors.password ? "border-red-500" : "border-line focus:border-brand",
            )}
          />
          <button
            type="button"
            onClick={() => setRevealed((shown) => !shown)}
            aria-pressed={revealed}
            aria-label={revealed ? "Hide password" : "Show password"}
            className="absolute top-1/2 right-2 -translate-y-1/2 rounded-md p-1.5 text-ink-subtle transition hover:text-ink focus-visible:ring-2 focus-visible:ring-brand/40 focus-visible:outline-none"
          >
            {revealed ? <EyeOffIcon className="size-4.5" /> : <EyeIcon className="size-4.5" />}
          </button>
        </div>
        {errors.password && (
          <p id={`${passwordId}-error`} className={ERROR_TEXT}>
            {errors.password}
          </p>
        )}
      </div>

      <div className="flex items-center justify-between">
        <label className="flex items-center gap-2 text-sm text-ink-muted select-none">
          <input
            type="checkbox"
            name="remember"
            className="size-4 rounded border-line accent-brand"
          />
          Remember me
        </label>
        <span className="text-sm text-ink-subtle">Forgot password?</span>
      </div>

      <button
        type="submit"
        className="flex w-full items-center justify-center gap-2 rounded-lg bg-linear-to-r from-brand to-brand-strong px-4 py-[var(--pad-field)] text-sm font-semibold text-brand-ink shadow-sm transition hover:brightness-110 focus-visible:ring-2 focus-visible:ring-brand/50 focus-visible:ring-offset-2 focus-visible:ring-offset-canvas focus-visible:outline-none"
      >
        Sign in
        <ArrowRightIcon className="size-4" />
      </button>

      {notice && (
        <p
          role="status"
          className="rounded-lg border border-amber-400/60 bg-amber-50 px-4 py-3 text-xs leading-relaxed text-amber-900 theme-dark:bg-amber-950/40 theme-dark:text-amber-200"
        >
          {notice}
        </p>
      )}
    </form>
  );
}
