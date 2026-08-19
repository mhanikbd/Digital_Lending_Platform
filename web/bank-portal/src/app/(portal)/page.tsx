import Link from "next/link";

/**
 * Landing page for the back-office portal.
 *
 * The sections listed here are the Milestone 35 to 38 deliverables. They are
 * shown as a roadmap rather than as dead links so that the foundation is
 * honest about what does and does not exist yet.
 */
const PLANNED_SECTIONS = [
  { name: "Administration", detail: "Users, roles, branches, products, rules, workflow, approval matrix" },
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
          The platform foundation is in place: infrastructure, the Spring Boot
          API, database migrations and this portal. Lending functionality is
          delivered milestone by milestone from here.
        </p>
        <Link
          href="/system/health"
          className="mt-5 inline-flex rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-slate-300"
        >
          View system health
        </Link>
      </section>

      <section>
        <h2 className="text-base font-semibold">Planned sections</h2>
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
