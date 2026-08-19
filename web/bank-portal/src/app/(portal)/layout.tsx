import Link from "next/link";

import { BrandLockup } from "@/components/brand-mark";
import { publicEnv } from "@/lib/env";

/**
 * Chrome for the signed-in back office.
 *
 * It lives in a route group so that full-bleed pages outside the application
 * shell - sign in today, and later the error and maintenance screens - are not
 * wrapped in a header and footer that make no sense there.
 */
const NAVIGATION = [
  { href: "/", label: "Overview" },
  { href: "/system/health", label: "System health" },
];

export default function PortalLayout({ children }: LayoutProps<"/">) {
  return (
    <div className="flex min-h-full flex-col">
      <header className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-6 gap-y-3 px-6 py-3">
          <Link href="/" aria-label="Digital Lending Platform home">
            <BrandLockup markClassName="size-9" />
          </Link>
          <nav className="flex gap-4 text-sm">
            {NAVIGATION.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <span className="rounded-full bg-slate-100 px-3 py-1 font-mono text-xs uppercase tracking-wide text-slate-600 dark:bg-slate-800 dark:text-slate-300">
              {publicEnv.appEnv}
            </span>
            <Link
              href="/login"
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
            >
              Sign in
            </Link>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-10">{children}</main>

      <footer className="border-t border-slate-200 px-6 py-4 text-center text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400">
        Milestone 1 &mdash; platform foundation. Lending functionality is not enabled yet.
      </footer>
    </div>
  );
}
