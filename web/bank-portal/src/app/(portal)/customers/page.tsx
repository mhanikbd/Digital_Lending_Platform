import type { Metadata } from "next";

import { fetchFromBackend } from "@/lib/api/backend";
import { customerListSchema, orgScopeSchema, type CustomerSummary } from "@/lib/api/contracts";
import { readAccessToken } from "@/lib/session";

export const metadata: Metadata = {
  title: "Customers | Bank Portal",
};

// What this list contains depends on who is asking, so it is never cached.
export const dynamic = "force-dynamic";

const RISK_STYLES: Record<string, string> = {
  LOW: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300",
  MEDIUM: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-300",
  HIGH: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
};

const KYC_STYLES: Record<string, string> = {
  VERIFIED: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300",
  IN_PROGRESS: "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300",
  PENDING: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
  REJECTED: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
};

export default async function CustomersPage() {
  const token = await readAccessToken();
  const [customers, scope] = await Promise.all([
    fetchFromBackend("/api/v1/customers", customerListSchema, token ?? undefined),
    fetchFromBackend("/api/v1/organization/my-scope", orgScopeSchema, token ?? undefined),
  ]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Customers</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
          Existing customers of the bank. This list is narrowed by your
          organisational scope on the server, not here: a branch officer is sent
          their own branches and nobody else&rsquo;s.
        </p>
      </div>

      {scope.ok && (
        <p className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600 shadow-sm dark:border-slate-800 dark:bg-slate-900 dark:text-slate-400">
          Your scope is <span className="font-mono text-xs">{scope.data.scopeLevel}</span> over{" "}
          <span className="font-mono text-xs">{scope.data.visibleUnitCodes.length}</span> unit
          {scope.data.visibleUnitCodes.length === 1 ? "" : "s"}
          {scope.data.postings.length > 0 && (
            <>
              , posted to{" "}
              <span className="font-mono text-xs">
                {scope.data.postings.map((posting) => posting.code).join(", ")}
              </span>
            </>
          )}
          .
        </p>
      )}

      {customers.ok ? (
        customers.data.length === 0 ? (
          <p className="rounded-lg border border-dashed border-slate-300 px-4 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
            No customers fall within your scope.
          </p>
        ) : (
          <CustomerTable customers={customers.data} />
        )
      ) : (
        <p className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
          {customers.reason}
        </p>
      )}
    </div>
  );
}

function CustomerTable({ customers }: { customers: CustomerSummary[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <table className="w-full min-w-[46rem] text-sm">
        <thead className="border-b border-slate-200 text-left text-slate-500 dark:border-slate-800 dark:text-slate-400">
          <tr>
            <th className="px-5 py-2.5 font-medium">Customer</th>
            <th className="px-5 py-2.5 font-medium">Name</th>
            <th className="px-5 py-2.5 font-medium">Type</th>
            <th className="px-5 py-2.5 font-medium">Mobile</th>
            <th className="px-5 py-2.5 font-medium">Branch</th>
            <th className="px-5 py-2.5 font-medium">Risk</th>
            <th className="px-5 py-2.5 font-medium">KYC</th>
          </tr>
        </thead>
        <tbody>
          {customers.map((customer) => (
            <tr
              key={customer.customerId}
              className="border-t border-slate-100 dark:border-slate-800"
            >
              <td className="px-5 py-3 font-mono text-xs">{customer.customerId}</td>
              <td className="px-5 py-3">{customer.fullName}</td>
              <td className="px-5 py-3 font-mono text-[11px] text-slate-500 dark:text-slate-400">
                {customer.customerType}
              </td>
              <td className="px-5 py-3 font-mono text-xs">{customer.mobile}</td>
              <td className="px-5 py-3 text-slate-600 dark:text-slate-400">
                <span className="font-mono text-xs">{customer.branchCode ?? "—"}</span>
              </td>
              <td className="px-5 py-3">
                <Pill label={customer.riskProfile} styles={RISK_STYLES} />
              </td>
              <td className="px-5 py-3">
                <Pill label={customer.kycStatus} styles={KYC_STYLES} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** State as colour as well as text, so a queue reads at a glance. */
function Pill({ label, styles }: { label: string; styles: Record<string, string> }) {
  const style = styles[label] ?? "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300";
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 font-mono text-[10px] ${style}`}>
      {label}
    </span>
  );
}
