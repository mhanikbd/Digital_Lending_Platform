"use client";

import { useRouter } from "next/navigation";
import { useId, useState, type FormEvent } from "react";
import { z } from "zod";

import { ArrowRightIcon, EyeIcon, EyeOffIcon, LockIcon, UserIcon } from "@/components/icons";
import type { DemoAccount } from "@/lib/api/contracts";
import { cn } from "@/lib/cn";

/**
 * Bank-user sign-in.
 *
 * <p>The credential is posted to this application's own route handler, never to
 * the API directly, and what comes back carries no token: the session is set as
 * httpOnly cookies by the server. Nothing on this page can read it, which is
 * the point.
 *
 * <p>Two steps, because the API may answer a correct password with a challenge
 * rather than a session. Which step is on screen is driven by that answer, not
 * by anything this component decides.
 */
const credentialsSchema = z.object({
  username: z.string().trim().min(1, "Enter your employee ID or username."),
  password: z.string().min(1, "Enter your password."),
});

const codeSchema = z.string().regex(/^[0-9]{6}$/, "Enter the 6 digit code.");

type Field = "username" | "password" | "code";
type Step = "credentials" | "mfa";

const FIELD_STYLES =
  "w-full rounded-lg border bg-field py-[var(--pad-field)] pl-10 text-sm text-ink shadow-xs outline-none transition " +
  "placeholder:text-ink-subtle focus:ring-2 focus:ring-brand/35";

const ERROR_TEXT = "mt-1.5 text-xs text-red-600 theme-dark:text-red-400";

export function LoginForm({ demoAccounts = [] }: { demoAccounts?: DemoAccount[] }) {
  const router = useRouter();
  const usernameId = useId();
  const passwordId = useId();
  const codeId = useId();

  const [step, setStep] = useState<Step>("credentials");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [revealed, setRevealed] = useState(false);
  const [pending, setPending] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [filledFrom, setFilledFrom] = useState<string | null>(null);

  /**
   * Loads a demonstration account into the form.
   *
   * <p>Fills, and stops there. Signing in is still a deliberate press, because
   * a card that signs you in the instant you brush it is a card that signs you
   * in as the wrong person while you are reading the list.
   */
  function fillFrom(account: DemoAccount) {
    setUsername(account.username);
    setPassword(account.password);
    setFilledFrom(account.username);
    setErrors({});
    setNotice(null);
  }

  /** Sends the signed-in operator on, and drops the credential from memory. */
  function onAuthenticated() {
    setPassword("");
    setCode("");
    // refresh() so the server components re-render knowing there is a session.
    router.replace("/");
    router.refresh();
  }

  async function submitCredentials(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = credentialsSchema.safeParse({ username, password });
    if (!parsed.success) {
      const next: Partial<Record<Field, string>> = {};
      for (const issue of parsed.error.issues) {
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
    setNotice(null);
    setPending(true);
    try {
      const response = await fetch("/bff/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(parsed.data),
      });
      const body = (await response.json()) as {
        ok: boolean;
        status?: string;
        reason?: string;
        mfaChallengeId?: string;
      };

      if (!body.ok) {
        setNotice(body.reason ?? "Sign-in was refused.");
        return;
      }
      if (body.status === "MFA_REQUIRED" && body.mfaChallengeId) {
        setChallengeId(body.mfaChallengeId);
        setStep("mfa");
        return;
      }
      onAuthenticated();
    } catch {
      setNotice("The portal could not reach the sign-in service.");
    } finally {
      setPending(false);
    }
  }

  async function submitCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = codeSchema.safeParse(code);
    if (!parsed.success) {
      setErrors({ code: parsed.error.issues[0]?.message });
      return;
    }

    setErrors({});
    setNotice(null);
    setPending(true);
    try {
      const response = await fetch("/bff/auth/mfa", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ challengeId, code: parsed.data }),
      });
      const body = (await response.json()) as { ok: boolean; reason?: string };

      if (!body.ok) {
        setNotice(body.reason ?? "That code was not accepted.");
        return;
      }
      onAuthenticated();
    } catch {
      setNotice("The portal could not reach the sign-in service.");
    } finally {
      setPending(false);
    }
  }

  if (step === "mfa") {
    return (
      <form onSubmit={submitCode} noValidate className="mt-[var(--gap-stack)] space-y-4">
        <div>
          <label htmlFor={codeId} className="block text-sm font-medium text-ink">
            Verification code
          </label>
          <p className="mt-1 text-xs text-ink-muted">
            Enter the 6 digit code sent to you to finish signing in.
          </p>
          <input
            id={codeId}
            name="code"
            inputMode="numeric"
            autoComplete="one-time-code"
            autoFocus
            placeholder="000000"
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/[^0-9]/g, "").slice(0, 6))}
            aria-invalid={errors.code ? true : undefined}
            className={cn(
              "mt-2 w-full rounded-lg border bg-field py-[var(--pad-field)] text-center font-mono text-lg",
              "tracking-[0.4em] text-ink outline-none focus:ring-2 focus:ring-brand/35",
              errors.code ? "border-red-500" : "border-line focus:border-brand",
            )}
          />
          {errors.code && <p className={ERROR_TEXT}>{errors.code}</p>}
        </div>

        <button type="submit" disabled={pending} className={primaryButton}>
          {pending ? "Verifying…" : "Verify and sign in"}
          {!pending && <ArrowRightIcon className="size-4" />}
        </button>

        <button
          type="button"
          onClick={() => {
            setStep("credentials");
            setChallengeId(null);
            setCode("");
            setNotice(null);
          }}
          className="w-full text-center text-xs font-medium text-ink-muted hover:text-ink"
        >
          Use a different account
        </button>

        {notice && <Notice>{notice}</Notice>}
      </form>
    );
  }

  return (
    <form onSubmit={submitCredentials} noValidate className="mt-[var(--gap-stack)] space-y-[clamp(0.6rem,1.9vh,1.6rem)]">
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
            onChange={(event) => {
              setUsername(event.target.value);
              setFilledFrom(null);
            }}
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
            onChange={(event) => {
              setPassword(event.target.value);
              setFilledFrom(null);
            }}
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

      <button type="submit" disabled={pending} className={primaryButton}>
        {pending ? "Signing in…" : "Sign in"}
        {!pending && <ArrowRightIcon className="size-4" />}
      </button>

      {notice && <Notice>{notice}</Notice>}

      {demoAccounts.length > 0 && (
        <DemoAccountPicker
          accounts={demoAccounts}
          selected={filledFrom}
          onPick={fillFrom}
        />
      )}
    </form>
  );
}

/**
 * The seeded accounts, one pill each.
 *
 * <p>Present only when the API offered any, which it does only under the local
 * profile. That is a better gate than reading the environment name here: it is
 * the backend that decides whether these accounts exist, so it should be the
 * backend that decides whether they are shown.
 *
 * <p>Labelled by role, because that is what a demonstrator is choosing between -
 * the person's name and their posting are on the tooltip, where they answer
 * "which branch is this one" without taking a line of the sign-in page to do it.
 *
 * <p>Clicking fills the form. It does not sign in: a control that signs you in
 * the instant you brush it signs you in as the wrong person while you are still
 * reading the row.
 */
function DemoAccountPicker({
  accounts,
  selected,
  onPick,
}: {
  accounts: DemoAccount[];
  selected: string | null;
  onPick: (account: DemoAccount) => void;
}) {
  return (
    <div className="tiny:hidden">
      <p className="text-[11px] font-medium tracking-wide text-ink-subtle uppercase">
        Demo accounts (click to fill)
      </p>

      <ul className="mt-2 flex flex-wrap gap-1.5">
        {accounts.map((account) => {
          const isSelected = selected === account.username;
          return (
            <li key={account.username}>
              <button
                type="button"
                onClick={() => onPick(account)}
                title={`${account.displayName} — ${account.note}`}
                aria-pressed={isSelected}
                className={cn(
                  "rounded-full border px-3 py-1 text-xs transition",
                  "focus-visible:ring-2 focus-visible:ring-brand/40 focus-visible:outline-none",
                  isSelected
                    ? "border-brand bg-brand/10 font-medium text-ink"
                    : "border-line bg-canvas text-ink-muted hover:border-brand/60 hover:text-ink",
                )}
              >
                {account.roleCode}
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

const primaryButton =
  "flex w-full items-center justify-center gap-2 rounded-lg bg-linear-to-r from-brand to-brand-strong " +
  "px-4 py-[var(--pad-field)] text-sm font-semibold text-brand-ink shadow-sm transition hover:brightness-110 " +
  "disabled:cursor-not-allowed disabled:opacity-70 focus-visible:ring-2 focus-visible:ring-brand/50 " +
  "focus-visible:ring-offset-2 focus-visible:ring-offset-canvas focus-visible:outline-none";

/**
 * Whatever the API said, verbatim. It answers every rejection with the same
 * sentence on purpose, so there is nothing here to soften or embellish.
 */
function Notice({ children }: { children: React.ReactNode }) {
  return (
    <p
      role="status"
      className="rounded-lg border border-red-400/60 bg-red-50 px-4 py-3 text-xs leading-relaxed text-red-800 theme-dark:bg-red-950/40 theme-dark:text-red-200"
    >
      {children}
    </p>
  );
}
