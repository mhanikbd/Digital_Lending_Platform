import Link from "next/link";
import { redirect } from "next/navigation";

import { BrandLockup } from "@/components/brand-mark";
import { SignOutButton } from "@/components/sign-out-button";
import { publicEnv } from "@/lib/env";
import { currentUser } from "@/lib/session";

/**
 * Chrome for the signed-in back office.
 *
 * <p>It lives in a route group so that full-bleed pages outside the application
 * shell - sign in today, and later the error and maintenance screens - are not
 * wrapped in a header and footer that make no sense there.
 *
 * <p>The group is also where the portal is closed. Every page beneath it is
 * behind this one check, so a new screen is protected by existing rather than
 * by its author remembering to guard it.
 */
const NAVIGATION = [
  { href: "/", label: "Overview" },
  { href: "/customers", label: "Customers" },
  { href: "/products", label: "Products" },
  { href: "/eligibility", label: "Eligibility" },
  { href: "/calculator", label: "Calculator" },
  { href: "/organization", label: "Organization" },
  { href: "/system/health", label: "System health" },
];

export default async function PortalLayout({ children }: LayoutProps<"/">) {
  const user = await currentUser();
  if (!user) {
    redirect("/login");
  }

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
            <span className="rounded-full bg-slate-100 px-3 py-1 font-mono text-xs tracking-wide text-slate-600 uppercase dark:bg-slate-800 dark:text-slate-300">
              {publicEnv.appEnv}
            </span>

            <div className="text-right leading-tight">
              <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
                {user.displayName}
              </p>
              {/* The roles held, not the permissions they resolve to: a header
                  is not the place for a list of forty codes. */}
              <p className="font-mono text-[11px] text-slate-500 dark:text-slate-400">
                {user.username}
                {user.roles.length > 0 && ` · ${user.roles.join(", ")}`}
              </p>
            </div>

            <SignOutButton />
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-10">{children}</main>

      <footer className="border-t border-slate-200 px-6 py-4 text-center text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400">
        Milestones 1 to 17 &mdash; foundation, access control, the customer master,
        the product catalogue, and the rule, eligibility and pricing engines.
        Loan applications and workflow are not enabled yet.
      </footer>
    </div>
  );
}
