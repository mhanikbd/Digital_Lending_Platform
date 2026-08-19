import Link from "next/link";
import type { Metadata } from "next";

import { ApplicationActions } from "@/components/application-actions";
import { Warning } from "@/components/notices";
import { fetchFromBackend } from "@/lib/api/backend";
import {
  applicationDetailSchema,
  availableActionListSchema,
  type ApplicationDetail,
} from "@/lib/api/contracts";
import { readAccessToken } from "@/lib/session";

export const metadata: Metadata = {
  title: "Application | Bank Portal",
};

export const dynamic = "force-dynamic";

export default async function ApplicationDetailPage({
  params,
}: PageProps<"/applications/[applicationNo]">) {
  const { applicationNo } = await params;
  const token = await readAccessToken();

  const [application, actions] = await Promise.all([
    fetchFromBackend(
      `/api/v1/loan-applications/${encodeURIComponent(applicationNo)}`,
      applicationDetailSchema,
      token ?? undefined,
    ),
    fetchFromBackend(
      `/api/v1/loan-applications/${encodeURIComponent(applicationNo)}/available-actions`,
      availableActionListSchema,
      token ?? undefined,
    ),
  ]);

  if (!application.ok) {
    return (
      <div className="space-y-4">
        <Link href="/applications" className="text-sm text-slate-500 hover:text-slate-900">
          &larr; Back to applications
        </Link>
        <Warning>{application.reason}</Warning>
      </div>
    );
  }

  const file = application.data;

  return (
    <div className="space-y-6">
      <div>
        <Link href="/applications" className="text-sm text-slate-500 hover:text-slate-900 dark:hover:text-slate-100">
          &larr; Back to applications
        </Link>
        <h1 className="mt-2 font-mono text-2xl font-semibold tracking-tight">
          {file.applicationNo}
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Step {file.stepNo} &middot; {file.stepName} &mdash; {file.stateName}
          <span className="ml-2 font-mono text-[11px] text-slate-500 dark:text-slate-400">
            {file.stateCode}
          </span>
        </p>
      </div>

      <ApplicationActions
        applicationNo={file.applicationNo}
        actions={actions.ok ? actions.data : []}
        application={file}
      />

      <Panel title="The loan">
        <dl className="grid grid-cols-2 gap-x-6 gap-y-3 px-5 py-4 text-sm sm:grid-cols-3 lg:grid-cols-4">
          <Fact label="Requested">
            {file.currency} {file.requestedAmount}
          </Fact>
          <Fact label="Approved">
            {file.approvedAmount ? `${file.currency} ${file.approvedAmount}` : "not yet"}
          </Fact>
          <Fact label="Tenure">{file.requestedTenureMonths} months</Fact>
          <Fact label="Rate">{file.interestRate}% p.a.</Fact>
          <Fact label="Method">{file.interestMethod.replace(/_/g, " ").toLowerCase()}</Fact>
          <Fact label="Instalment">
            {file.instalmentAmount ? `${file.currency} ${file.instalmentAmount}` : "—"}
          </Fact>
          <Fact label="Total payable">
            {file.totalPayable ? `${file.currency} ${file.totalPayable}` : "—"}
          </Fact>
          <Fact label="Reaches the account">
            {file.netDisbursement ? `${file.currency} ${file.netDisbursement}` : "—"}
          </Fact>
          <Fact label="Product">
            {file.productName} v{file.productVersion}
          </Fact>
          <Fact label="Purpose">
            {file.purposeCode}
            {file.purposeDetail ? ` — ${file.purposeDetail}` : ""}
          </Fact>
          <Fact label="Channel">{file.sourceChannel.replace(/_/g, " ").toLowerCase()}</Fact>
          <Fact label="Branch">{file.branchCode ?? "—"}</Fact>
        </dl>
        <p className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400">
          The rate and the quotation are the ones recorded when the file was
          raised, against version {file.productVersion} of the product. Repricing
          the product does not change them.
        </p>
      </Panel>

      {file.applicants.length > 0 && (
        <Panel title="Applicant, as declared">
          <dl className="grid grid-cols-2 gap-x-6 gap-y-3 px-5 py-4 text-sm sm:grid-cols-3">
            <Fact label="Name">{file.applicants[0].fullName}</Fact>
            <Fact label="Mobile">{file.applicants[0].mobile}</Fact>
            <Fact label="National ID">{file.applicants[0].nationalId ?? "—"}</Fact>
            <Fact label="Occupation">{file.applicants[0].occupation ?? "—"}</Fact>
            <Fact label="Employer">{file.applicants[0].employerName ?? "—"}</Fact>
            <Fact label="Customer">{file.customerId}</Fact>
          </dl>
        </Panel>
      )}

      {file.financial && (
        <Panel title="Finances the decision rests on">
          <dl className="grid grid-cols-2 gap-x-6 gap-y-3 px-5 py-4 text-sm sm:grid-cols-3 lg:grid-cols-4">
            <Fact label="Monthly income">{file.financial.monthlyIncome}</Fact>
            <Fact label="Other income">{file.financial.otherMonthlyIncome}</Fact>
            <Fact label="Monthly expense">{file.financial.monthlyExpense}</Fact>
            <Fact label="Existing borrowing">{file.financial.existingLiabilities}</Fact>
            <Fact label="Existing instalment">{file.financial.existingEmi}</Fact>
            <Fact label="Debt burden">
              {file.financial.debtBurdenRatio
                ? `${percent(file.financial.debtBurdenRatio)} of income`
                : "not computed"}
            </Fact>
          </dl>
        </Panel>
      )}

      {file.queries.length > 0 && (
        <Panel title="Queries">
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {file.queries.map((query) => (
              <li key={query.queryNo} className="px-5 py-4">
                <p className="text-sm text-slate-900 dark:text-slate-100">
                  <span className="font-mono text-[11px] text-slate-500 dark:text-slate-400">
                    Q{query.queryNo} · {query.status}
                  </span>{" "}
                  {query.question}
                </p>
                <p className="mt-0.5 font-mono text-[10px] text-slate-500 dark:text-slate-400">
                  raised by {query.raisedBy}
                  {query.raisedByRole ? ` (${query.raisedByRole})` : ""}
                </p>
                {query.responses.map((response) => (
                  <p
                    key={response.respondedAt}
                    className="mt-2 border-l-2 border-slate-200 pl-3 text-sm text-slate-700 dark:border-slate-700 dark:text-slate-300"
                  >
                    {response.response}
                    <span className="ml-2 font-mono text-[10px] text-slate-500 dark:text-slate-400">
                      {response.respondedBy}
                      {response.respondedByRole ? ` (${response.respondedByRole})` : ""}
                    </span>
                  </p>
                ))}
              </li>
            ))}
          </ul>
        </Panel>
      )}

      <Panel title="Where it has been">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[40rem] text-sm">
            <thead className="border-b border-slate-200 text-left text-slate-500 dark:border-slate-800 dark:text-slate-400">
              <tr>
                <th className="px-5 py-2 font-medium">From</th>
                <th className="px-5 py-2 font-medium">To</th>
                <th className="px-5 py-2 font-medium">Action</th>
                <th className="px-5 py-2 font-medium">By</th>
                <th className="px-5 py-2 font-medium">Reason</th>
              </tr>
            </thead>
            <tbody>
              {file.history.map((row) => (
                <tr
                  key={`${row.occurredAt}-${row.toState}`}
                  className="border-t border-slate-100 dark:border-slate-800"
                >
                  <td className="px-5 py-2 font-mono text-[11px] text-slate-500 dark:text-slate-400">
                    {row.fromState ?? "—"}
                  </td>
                  <td className="px-5 py-2 font-mono text-[11px]">{row.toState}</td>
                  <td className="px-5 py-2 font-mono text-[11px]">{row.action}</td>
                  <td className="px-5 py-2 font-mono text-[11px]">
                    {row.actorUsername}
                    {row.actorRole ? ` · ${row.actorRole}` : ""}
                  </td>
                  <td className="px-5 py-2 text-slate-600 dark:text-slate-400">
                    {row.reason ?? "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
    </div>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <h2 className="border-b border-slate-100 px-5 py-3 text-xs font-medium tracking-wide text-slate-500 uppercase dark:border-slate-800 dark:text-slate-400">
        {title}
      </h2>
      {children}
    </section>
  );
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-slate-500 dark:text-slate-400">{label}</dt>
      <dd className="mt-0.5 text-slate-900 dark:text-slate-100">{children}</dd>
    </div>
  );
}

/**
 * A stored fraction shown as a percentage, by shifting the decimal point rather
 * than multiplying. These are decimals the backend was careful to send as text.
 */
function percent(fraction: string): string {
  const negative = fraction.startsWith("-");
  const [whole = "0", decimals = ""] = (negative ? fraction.slice(1) : fraction).split(".");
  const padded = decimals.padEnd(2, "0");

  const shiftedWhole = (whole + padded.slice(0, 2)).replace(/^0+(?=\d)/, "");
  const shiftedDecimals = padded.slice(2).replace(/0+$/, "");

  return `${negative ? "-" : ""}${shiftedWhole}${shiftedDecimals ? `.${shiftedDecimals}` : ""}%`;
}
