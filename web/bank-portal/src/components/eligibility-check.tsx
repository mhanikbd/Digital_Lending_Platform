"use client";

import { useState, type FormEvent } from "react";

import type {
  CustomerSummary,
  Eligibility,
  LimitFactor,
  ProductSummary,
  RuleGroupResult,
} from "@/lib/api/contracts";

/**
 * Assess a customer against a product.
 *
 * <p>The decision is entirely the backend's. This component chooses two things -
 * which customer and which product - and renders what comes back, including
 * every criterion that was tested and every limit that was considered. That is
 * deliberate: a banker who cannot say which rule declined somebody cannot answer
 * the customer standing in front of them.
 */
export function EligibilityCheck({
  customers,
  products,
}: {
  customers: CustomerSummary[];
  products: ProductSummary[];
}) {
  const sellable = products.filter((product) => product.currentVersion);

  const [customerId, setCustomerId] = useState(customers[0]?.customerId ?? "");
  const [productCode, setProductCode] = useState(sellable[0]?.code ?? "");
  const [assessment, setAssessment] = useState<Eligibility | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setProblem(null);

    try {
      const response = await fetch("/bff/eligibility", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ customerId, productCode }),
      });
      const body: unknown = await response.json().catch(() => null);
      const payload = body as { ok?: boolean; assessment?: Eligibility; reason?: string } | null;

      if (!response.ok || !payload?.ok || !payload.assessment) {
        setAssessment(null);
        setProblem(payload?.reason ?? "The assessment could not be run.");
        return;
      }
      setAssessment(payload.assessment);
    } catch {
      setAssessment(null);
      setProblem("The portal could not reach the API.");
    } finally {
      setPending(false);
    }
  }

  if (customers.length === 0 || sellable.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-slate-300 px-4 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
        {customers.length === 0
          ? "No customer falls within your scope, so there is nobody to assess."
          : "No product is currently on sale, so there is nothing to assess against."}
      </p>
    );
  }

  return (
    <div className="space-y-6">
      <form
        onSubmit={submit}
        className="grid gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm sm:grid-cols-3 dark:border-slate-800 dark:bg-slate-900"
      >
        <label className="sm:col-span-2">
          <span className="mb-1 block text-xs text-slate-500 dark:text-slate-400">Customer</span>
          <select
            value={customerId}
            onChange={(event) => {
              setCustomerId(event.target.value);
              setAssessment(null);
            }}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
          >
            {customers.map((customer) => (
              <option key={customer.customerId} value={customer.customerId}>
                {customer.customerId} — {customer.fullName} ({customer.kycStatus})
              </option>
            ))}
          </select>
        </label>

        <label>
          <span className="mb-1 block text-xs text-slate-500 dark:text-slate-400">Product</span>
          <select
            value={productCode}
            onChange={(event) => {
              setProductCode(event.target.value);
              setAssessment(null);
            }}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
          >
            {sellable.map((product) => (
              <option key={product.code} value={product.code}>
                {product.name}
              </option>
            ))}
          </select>
        </label>

        <div className="sm:col-span-3">
          <button
            type="submit"
            disabled={pending}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 dark:bg-slate-100 dark:text-slate-900"
          >
            {pending ? "Assessing…" : "Check eligibility"}
          </button>
        </div>
      </form>

      {problem && (
        <p className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
          {problem}
        </p>
      )}

      {assessment && <Assessment assessment={assessment} />}
    </div>
  );
}

function Assessment({ assessment }: { assessment: Eligibility }) {
  return (
    <div className="space-y-5">
      <div
        className={
          assessment.eligible
            ? "rounded-lg border border-emerald-300 bg-emerald-50 p-5 dark:border-emerald-900/60 dark:bg-emerald-950/30"
            : "rounded-lg border border-red-300 bg-red-50 p-5 dark:border-red-900/60 dark:bg-red-950/30"
        }
      >
        <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">
          {assessment.eligible ? "Eligible" : "Not eligible"} &mdash; {assessment.productName}{" "}
          version {assessment.productVersion}
        </p>

        {assessment.eligible ? (
          <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-4">
            <Figure
              label="Maximum"
              value={`${assessment.currency} ${assessment.maxAmount ?? "—"}`}
            />
            <Figure
              label="Offered"
              value={`${assessment.currency} ${assessment.recommendedAmount ?? "—"}`}
            />
            <Figure label="Rate" value={`${assessment.interestRate}% p.a.`} />
            <Figure label="Tenures" value={`${assessment.availableTenures.join(", ")} months`} />
          </dl>
        ) : (
          <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-slate-700 dark:text-slate-300">
            {assessment.reasons.map((reason) => (
              <li key={reason}>{reason}</li>
            ))}
          </ul>
        )}

        {assessment.evaluationId && (
          <p className="mt-4 font-mono text-[11px] text-slate-500 dark:text-slate-400">
            Recorded as {assessment.evaluationId}
          </p>
        )}
      </div>

      {assessment.criteria.map((group) => (
        <CriteriaTable key={group.code} group={group} />
      ))}

      {assessment.limits && (
        <section className="rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <h3 className="border-b border-slate-100 px-5 py-3 text-xs font-medium tracking-wide text-slate-500 uppercase dark:border-slate-800 dark:text-slate-400">
            How the amount was arrived at
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[38rem] text-sm">
              <thead className="border-b border-slate-200 text-left text-slate-500 dark:border-slate-800 dark:text-slate-400">
                <tr>
                  <th className="px-5 py-2 font-medium">Limit</th>
                  <th className="px-5 py-2 font-medium">Amount</th>
                  <th className="px-5 py-2 font-medium">Why</th>
                </tr>
              </thead>
              <tbody>
                {assessment.limits.factors.map((factor) => (
                  <FactorRow key={factor.code} factor={factor} />
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

function FactorRow({ factor }: { factor: LimitFactor }) {
  return (
    <tr
      className={
        factor.binding
          ? "border-t border-slate-100 bg-amber-50/60 dark:border-slate-800 dark:bg-amber-950/20"
          : "border-t border-slate-100 dark:border-slate-800"
      }
    >
      <td className="px-5 py-2">
        {factor.name}
        {factor.binding && (
          <span className="ml-2 rounded-full bg-amber-200 px-2 py-0.5 font-mono text-[10px] text-amber-900 dark:bg-amber-900 dark:text-amber-100">
            binding
          </span>
        )}
      </td>
      <td className="px-5 py-2 font-mono text-xs">
        {factor.amount ?? <span className="text-slate-400">not configured</span>}
      </td>
      <td className="px-5 py-2 text-slate-600 dark:text-slate-400">{factor.explanation}</td>
    </tr>
  );
}

function CriteriaTable({ group }: { group: RuleGroupResult }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <h3 className="flex flex-wrap items-center gap-x-3 border-b border-slate-100 px-5 py-3 dark:border-slate-800">
        <span className="text-sm font-medium text-slate-900 dark:text-slate-100">{group.name}</span>
        <span className="font-mono text-[11px] text-slate-500 dark:text-slate-400">
          {group.code} &middot; all rules must hold ({group.logic})
        </span>
        <span
          className={
            group.passed
              ? "ml-auto rounded-full bg-emerald-100 px-2 py-0.5 font-mono text-[10px] text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
              : "ml-auto rounded-full bg-red-100 px-2 py-0.5 font-mono text-[10px] text-red-800 dark:bg-red-950 dark:text-red-300"
          }
        >
          {group.passed ? "PASS" : "FAIL"}
        </span>
      </h3>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[38rem] text-sm">
          <thead className="border-b border-slate-200 text-left text-slate-500 dark:border-slate-800 dark:text-slate-400">
            <tr>
              <th className="px-5 py-2 font-medium">Criterion</th>
              <th className="px-5 py-2 font-medium">Their value</th>
              <th className="px-5 py-2 font-medium">Result</th>
            </tr>
          </thead>
          <tbody>
            {group.criteria.map((line) => (
              <tr key={line.attribute} className="border-t border-slate-100 dark:border-slate-800">
                <td className="px-5 py-2">
                  {line.criterion}
                  {!line.passed && line.message && (
                    <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                      {line.message}
                    </p>
                  )}
                </td>
                <td className="px-5 py-2 font-mono text-xs">
                  {line.actualValue ?? <span className="text-slate-400">unknown</span>}
                </td>
                <td className="px-5 py-2">
                  <span
                    className={
                      line.passed
                        ? "rounded-full bg-emerald-100 px-2 py-0.5 font-mono text-[10px] text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                        : "rounded-full bg-red-100 px-2 py-0.5 font-mono text-[10px] text-red-800 dark:bg-red-950 dark:text-red-300"
                    }
                  >
                    {line.passed ? "PASS" : "FAIL"}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-500 dark:text-slate-400">{label}</dt>
      <dd className="mt-0.5 font-mono text-sm text-slate-900 dark:text-slate-100">{value}</dd>
    </div>
  );
}
