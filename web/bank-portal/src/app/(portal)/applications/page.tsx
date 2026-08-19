import Link from "next/link";
import type { Metadata } from "next";

import { Empty, Warning } from "@/components/notices";
import { fetchFromBackend } from "@/lib/api/backend";
import {
  applicationListSchema,
  workflowStateListSchema,
  type ApplicationSummary,
  type WorkflowStateInfo,
} from "@/lib/api/contracts";
import { readAccessToken } from "@/lib/session";

export const metadata: Metadata = {
  title: "Applications | Bank Portal",
};

// A queue that is cached is a queue two people work at once.
export const dynamic = "force-dynamic";

const STEP_STYLES: Record<number, string> = {
  1: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
  2: "bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300",
  3: "bg-indigo-100 text-indigo-800 dark:bg-indigo-950 dark:text-indigo-300",
  4: "bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-300",
  5: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-300",
  6: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300",
  9: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
};

export default async function ApplicationsPage({
  searchParams,
}: PageProps<"/applications">) {
  const params = await searchParams;
  const state = typeof params.state === "string" ? params.state : undefined;
  const token = await readAccessToken();

  const [applications, states] = await Promise.all([
    fetchFromBackend(
      state ? `/api/v1/loan-applications?state=${encodeURIComponent(state)}` : "/api/v1/loan-applications",
      applicationListSchema,
      token ?? undefined,
    ),
    fetchFromBackend("/api/v1/workflow/states", workflowStateListSchema, token ?? undefined),
  ]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Applications</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
          The loan queue, narrowed by your organisational scope on the server. A
          file records the product version it was judged under and the quotation
          it was given, so re-opening one years later reproduces what the
          approver actually saw.
        </p>
      </div>

      {states.ok && <StateFilter states={states.data} selected={state} />}

      {applications.ok ? (
        applications.data.length === 0 ? (
          <Empty>
            {state
              ? "No application is sitting in that state within your scope."
              : "No application falls within your scope yet."}
          </Empty>
        ) : (
          <ApplicationTable applications={applications.data} />
        )
      ) : (
        <Warning>{applications.reason}</Warning>
      )}
    </div>
  );
}

/**
 * The states, as a filter.
 *
 * <p>Read from the workflow configuration rather than hard-coded, so a bank that
 * adds a state gets it in the filter without a release - which is the same
 * reason the backend keeps them in a table.
 */
function StateFilter({
  states,
  selected,
}: {
  states: WorkflowStateInfo[];
  selected?: string;
}) {
  const byStep = new Map<number, WorkflowStateInfo[]>();
  for (const state of states) {
    const bucket = byStep.get(state.stepNo) ?? [];
    bucket.push(state);
    byStep.set(state.stepNo, bucket);
  }

  return (
    <div className="space-y-2 rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <p className="text-[11px] font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
        Filter by workflow state
      </p>
      <div className="flex flex-wrap gap-1.5">
        <Link
          href="/applications"
          className={pill(!selected)}
        >
          All
        </Link>
        {[...byStep.entries()]
          .sort(([a], [b]) => a - b)
          .flatMap(([, group]) => group)
          .map((state) => (
            <Link
              key={state.code}
              href={`/applications?state=${encodeURIComponent(state.code)}`}
              title={`Step ${state.stepNo} · ${state.stepName}${state.description ? ` — ${state.description}` : ""}`}
              className={pill(selected === state.code)}
            >
              {state.code}
            </Link>
          ))}
      </div>
    </div>
  );
}

function pill(active: boolean): string {
  return active
    ? "rounded-full border border-slate-900 bg-slate-900 px-2.5 py-0.5 font-mono text-[10px] text-white dark:border-slate-100 dark:bg-slate-100 dark:text-slate-900"
    : "rounded-full border border-slate-200 px-2.5 py-0.5 font-mono text-[10px] text-slate-600 hover:border-slate-400 dark:border-slate-700 dark:text-slate-400 dark:hover:border-slate-500";
}

function ApplicationTable({ applications }: { applications: ApplicationSummary[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <table className="w-full min-w-[52rem] text-sm">
        <thead className="border-b border-slate-200 text-left text-slate-500 dark:border-slate-800 dark:text-slate-400">
          <tr>
            <th className="px-5 py-2.5 font-medium">Application</th>
            <th className="px-5 py-2.5 font-medium">Customer</th>
            <th className="px-5 py-2.5 font-medium">Product</th>
            <th className="px-5 py-2.5 font-medium">Amount</th>
            <th className="px-5 py-2.5 font-medium">Step</th>
            <th className="px-5 py-2.5 font-medium">State</th>
            <th className="px-5 py-2.5 font-medium">Branch</th>
          </tr>
        </thead>
        <tbody>
          {applications.map((application) => (
            <tr
              key={application.applicationNo}
              className="border-t border-slate-100 dark:border-slate-800"
            >
              <td className="px-5 py-3">
                <Link
                  href={`/applications/${application.applicationNo}`}
                  className="font-mono text-xs underline-offset-2 hover:underline"
                >
                  {application.applicationNo}
                </Link>
              </td>
              <td className="px-5 py-3">
                {application.customerName}
                <span className="ml-2 font-mono text-[10px] text-slate-500 dark:text-slate-400">
                  {application.customerId}
                </span>
              </td>
              <td className="px-5 py-3">{application.productName}</td>
              <td className="px-5 py-3 font-mono text-xs">
                {application.approvedAmount ?? application.requestedAmount}
                {application.approvedAmount && (
                  <span className="ml-1.5 text-[10px] text-emerald-700 dark:text-emerald-400">
                    approved
                  </span>
                )}
              </td>
              <td className="px-5 py-3">
                <span
                  className={`inline-flex rounded-full px-2 py-0.5 font-mono text-[10px] ${
                    STEP_STYLES[application.stepNo] ?? STEP_STYLES[1]
                  }`}
                >
                  {application.stepNo} · {application.stepName}
                </span>
              </td>
              <td className="px-5 py-3 font-mono text-[11px] text-slate-600 dark:text-slate-400">
                {application.stateCode}
              </td>
              <td className="px-5 py-3 font-mono text-xs text-slate-500 dark:text-slate-400">
                {application.branchCode ?? "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
