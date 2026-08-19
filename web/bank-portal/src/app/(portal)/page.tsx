import Link from "next/link";

/**
 * Landing page for the back-office portal.
 *
 * <p>Two lists, and the split is the point. What is live is what a banker can
 * actually do today; what is planned is shown as text rather than as a dead
 * link, so the portal never implies a screen that does not exist.
 */
const LIVE_SECTIONS = [
  {
    name: "Customers",
    href: "/customers",
    detail: "The customer master, narrowed to your organisational scope",
  },
  {
    name: "Products",
    href: "/products",
    detail: "The catalogue and the terms each product is currently sold on",
  },
  {
    name: "Eligibility",
    href: "/eligibility",
    detail: "Whether a customer qualifies, for how much, and why",
  },
  {
    name: "Loan calculator",
    href: "/calculator",
    detail: "Instalment, interest, fees, VAT and the full repayment schedule",
  },
  {
    name: "Organization",
    href: "/organization",
    detail: "The bank's own hierarchy and the scope rules that follow from it",
  },
  {
    name: "System health",
    href: "/system/health",
    detail: "Whether the database, cache and object storage are reachable",
  },
];

const PLANNED_SECTIONS = [
  { name: "Administration", detail: "Users, roles, branches, workflow, approval matrix" },
  { name: "Loan processing", detail: "Application queue, returned, pending, approved, rejected, disbursed" },
  { name: "Credit", detail: "Credit queue, analysis workspace, CIB, screening, queries" },
  { name: "Approval", detail: "Approval queue, conditional approval, escalation, group approval" },
  { name: "Credit administration", detail: "Disbursement queue, CBS requests, retry, reconciliation" },
  { name: "Collection", detail: "Overdue, NPL, recovery dashboards" },
];

export default function OverviewPage() {
  return (
    <div className="space-y-10">
      <section>
        <h1 className="text-2xl font-semibold tracking-tight">Bank back office</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
          The foundation, access control and the customer master are in place,
          and so is the decisioning half of the platform: the product catalogue
          and its versioning, the rule engine, the eligibility and amount
          engines, and the pricing calculator. Loan applications and the workflow
          that moves them are delivered from here.
        </p>
      </section>

      <section>
        <h2 className="text-base font-semibold">Available now</h2>
        <ul className="mt-4 grid gap-3 sm:grid-cols-2">
          {LIVE_SECTIONS.map((section) => (
            <li key={section.href}>
              <Link
                href={section.href}
                className="block rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-sm transition hover:border-slate-400 dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-600"
              >
                <p className="text-sm font-medium">{section.name}</p>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{section.detail}</p>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section>
        <h2 className="text-base font-semibold">Still to come</h2>
        <ul className="mt-4 grid gap-3 sm:grid-cols-2">
          {PLANNED_SECTIONS.map((section) => (
            <li
              key={section.name}
              className="rounded-lg border border-dashed border-slate-300 px-4 py-3 dark:border-slate-700"
            >
              <p className="text-sm font-medium">{section.name}</p>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{section.detail}</p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
