import type { Metadata } from "next";

import { BankLogo } from "@/components/bank-logo";
import { BrandLockup } from "@/components/brand-mark";
import { LifecycleRing } from "@/components/lifecycle-ring";
import { NaztechLogo } from "@/components/naztech-logo";
import { LoginForm } from "@/components/login-form";
import { ThemeSwitcher } from "@/components/theme-switcher";
import { fetchFromBackend } from "@/lib/api/backend";
import {
  demoAccountListSchema,
  platformHealthSchema,
  platformInfoSchema,
} from "@/lib/api/contracts";
import { cn } from "@/lib/cn";

export const metadata: Metadata = {
  title: "Sign in | Digital Lending Platform",
};

// The status line at the foot of the form is measured, not decorative.
export const dynamic = "force-dynamic";

export default async function LoginPage() {
  // The demo accounts are asked for unconditionally and simply absent when the
  // API declines. That endpoint exists only under the backend's local profile,
  // so the backend decides whether credentials are on offer - not an
  // environment name this application happens to be started with.
  const [health, info, demoAccounts] = await Promise.all([
    fetchFromBackend("/api/v1/platform/health", platformHealthSchema),
    fetchFromBackend("/api/v1/platform/info", platformInfoSchema),
    fetchFromBackend("/api/v1/auth/demo-accounts", demoAccountListSchema),
  ]);

  const online = health.ok && health.data.status === "UP";
  const statusLabel = !health.ok ? "API unreachable" : online ? "System online" : "Degraded";

  return (
    // h-dvh rather than min-h-screen: the page is sized to the viewport it is
    // in, and dvh is the unit that accounts for mobile browser chrome sliding in
    // and out. Each column then fits inside that height, instead of the document
    // growing and the whole page scrolling.
    <div className="grid h-dvh lg:grid-cols-[1.05fr_1fr]">
      {/* ---- Hero: the lifecycle the platform manages ------------------ */}
      <aside className="hero-surface relative hidden min-h-0 overflow-hidden lg:block">
        <div className="flex h-full flex-col gap-[var(--gap-stack)] p-[var(--pad-panel)]">
          <BrandLockup tone="hero" markClassName="size-10 short:size-8" />

          <div>
            <h1 className="text-[length:var(--fs-hero)] leading-[1.05] font-bold tracking-tight text-hero-ink">
              One Loan File.{" "}
              <span className="text-hero-accent">Every Stage.</span>
            </h1>
            <div className="mt-[var(--gap-tight)] h-1 w-16 rounded-full bg-hero-accent" />
          </div>

          {/* The centrepiece: every state, where it sits in the circuit, and
              the direction a file travels between them. */}
          <LifecycleRing className="min-h-0 flex-1" />

          <p className="font-mono text-xs tracking-[0.2em] text-hero-faint uppercase short:hidden">
            Milestone 19 &middot; Application &amp; workflow
          </p>
        </div>
      </aside>

      {/* ---- Sign in --------------------------------------------------- */}
      {/* Three bands: the theme control at the top, the form taking whatever
          height is left, and the status line on the floor. flex-1 on the middle
          band is what centres the form without pushing the other two around.

          overflow-y-auto is a safety valve, not the plan: the column fits at
          every size checked, but a 300px-tall window should still be usable. */}
      <main className="flex min-h-0 flex-col overflow-y-auto bg-canvas px-6 py-[var(--pad-panel)] tiny:px-4 tiny:py-3">
        <div className="flex justify-end">
          <ThemeSwitcher />
        </div>

        <div className="flex min-h-0 flex-1 items-center justify-center py-[var(--gap-stack)]">
          <div className="w-full max-w-[min(36rem,92%)]">
            <div className="flex flex-col items-center text-center">
              <BankLogo size="lg" />
              <h1 className="mt-[var(--gap-stack)] text-[length:var(--fs-brand)] leading-[1.04] font-bold tracking-tight text-balance text-ink">
                Digital Lending Platform
              </h1>
              <p className="mt-[var(--gap-tight)] text-[length:var(--fs-brand-sub)] leading-tight font-semibold text-brand theme-green:text-brand-strong">
                Bank Back Office
              </p>
            </div>

            <div className="mt-[var(--gap-stack)]">
              <h2 className="text-xl font-semibold tracking-tight text-ink tiny:text-base">
                Sign in
              </h2>
              <p className="mt-1 text-sm text-ink-muted short:hidden">
                Access your account to continue
              </p>

              {/* The picker lives inside the form because it fills the form.
                  Lifting that state out to sit beside it would buy nothing and
                  cost a round trip through this server component. */}
              <LoginForm demoAccounts={demoAccounts.ok ? demoAccounts.data : []} />
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-xs text-ink-muted">
          <span className="inline-flex items-center gap-2">
            <span
              aria-hidden
              className={cn(
                "size-2 rounded-full",
                !health.ok ? "bg-red-500" : online ? "bg-emerald-500" : "bg-amber-500",
              )}
            />
            {statusLabel}
          </span>
          <span className="text-line">|</span>
          <span>{info.ok ? `API ${info.data.apiVersion} · ${info.data.environment}` : "API —"}</span>
          <span className="text-line tiny:hidden">|</span>
          <span className="tiny:hidden">
            <span className="inline-flex items-center gap-1.5">
              {/* Fixed box: the silver and blue wordmarks are 4.09:1 and 4.25:1, so an
                  auto width would jog the row by 2px on every theme switch. */}
              Powered by <NaztechLogo className="h-4 w-[68px]" />
            </span>
          </span>
        </div>
      </main>
    </div>
  );
}
