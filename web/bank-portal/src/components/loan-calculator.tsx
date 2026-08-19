"use client";

import { useId, useState, type FormEvent } from "react";

import type { LoanQuote, ProductSummary } from "@/lib/api/contracts";

/**
 * The loan calculator, as a banker uses it.
 *
 * <p>Nothing is computed here. The amount and the tenure are collected, posted
 * to this application's own route handler, and every figure on screen comes
 * back from the backend - which is what §20 requires: a client may show an
 * indicative number, but the authoritative one is the server's, and it cannot
 * be if the browser is doing the arithmetic.
 *
 * <p>The tenures offered are the product's own, so an unofferable term cannot be
 * chosen. The backend refuses one anyway; the point of the list is that nobody
 * has to be refused to find out.
 */
export function LoanCalculator({ products }: { products: ProductSummary[] }) {
  const amountId = useId();
  const productId = useId();
  const tenureId = useId();

  const sellable = products.filter((product) => product.currentVersion);

  const [code, setCode] = useState(sellable[0]?.code ?? "");
  const [amount, setAmount] = useState("");
  const [tenure, setTenure] = useState<number | "">(
    sellable[0]?.currentVersion?.tenures[0] ?? "",
  );
  const [quote, setQuote] = useState<LoanQuote | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const selected = sellable.find((product) => product.code === code);
  const version = selected?.currentVersion;

  function onProductChange(next: string) {
    setCode(next);
    const chosen = sellable.find((product) => product.code === next);
    setTenure(chosen?.currentVersion?.tenures[0] ?? "");
    setQuote(null);
    setProblem(null);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setProblem(null);

    try {
      const response = await fetch("/bff/loan-calculator", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ productCode: code, amount, tenureMonths: tenure }),
      });
      const body: unknown = await response.json().catch(() => null);
      const payload = body as { ok?: boolean; quote?: LoanQuote; reason?: string } | null;

      if (!response.ok || !payload?.ok || !payload.quote) {
        setQuote(null);
        // The backend's own refusal, which names the bounds it applied.
        setProblem(payload?.reason ?? "The quotation could not be produced.");
        return;
      }
      setQuote(payload.quote);
    } catch {
      setQuote(null);
      setProblem("The portal could not reach the API.");
    } finally {
      setPending(false);
    }
  }

  if (sellable.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-slate-300 px-4 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
        No product is currently on sale, so nothing can be quoted.
      </p>
    );
  }

  return (
    <div className="space-y-6">
      <form
        onSubmit={submit}
        className="grid gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm sm:grid-cols-4 dark:border-slate-800 dark:bg-slate-900"
      >
        <label className="sm:col-span-2">
          <span className="mb-1 block text-xs text-slate-500 dark:text-slate-400">Product</span>
          <select
            id={productId}
            value={code}
            onChange={(event) => onProductChange(event.target.value)}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
          >
            {sellable.map((product) => (
              <option key={product.code} value={product.code}>
                {product.name} (v{product.currentVersion?.versionNo})
              </option>
            ))}
          </select>
        </label>

        <label>
          <span className="mb-1 block text-xs text-slate-500 dark:text-slate-400">
            Amount{version ? ` (${version.minAmount} – ${version.maxAmount})` : ""}
          </span>
          <input
            id={amountId}
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            inputMode="decimal"
            placeholder={version?.maxAmount ?? ""}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
          />
        </label>

        <label>
          <span className="mb-1 block text-xs text-slate-500 dark:text-slate-400">Tenure</span>
          <select
            id={tenureId}
            value={tenure}
            onChange={(event) => setTenure(Number(event.target.value))}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
          >
            {(version?.tenures ?? []).map((months) => (
              <option key={months} value={months}>
                {months} months
              </option>
            ))}
          </select>
        </label>

        <div className="sm:col-span-4">
          <button
            type="submit"
            disabled={pending || amount.trim() === ""}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 dark:bg-slate-100 dark:text-slate-900"
          >
            {pending ? "Calculating…" : "Calculate"}
          </button>
        </div>
      </form>

      {problem && (
        <p className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
          {problem}
        </p>
      )}

      {quote && <Quotation quote={quote} />}
    </div>
  );
}

function Quotation({ quote }: { quote: LoanQuote }) {
  return (
    <div className="space-y-5">
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <p className="mb-4 font-mono text-[11px] text-slate-500 dark:text-slate-400">
          {quote.productName} version {quote.productVersion} &middot; {quote.interestRate}% p.a.{" "}
          {quote.interestMethod.replace(/_/g, " ").toLowerCase()}
          {quote.rateNegotiated && " · negotiated rate"}
        </p>

        <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3 lg:grid-cols-4">
          <Figure label="Instalment" value={`${quote.currency} ${quote.instalment}`} emphasis />
          <Figure label="Instalments" value={`${quote.instalments}`} />
          <Figure label="Principal" value={`${quote.currency} ${quote.principal}`} />
          <Figure label="Interest" value={`${quote.currency} ${quote.totalInterest}`} />
          <Figure label="Fees" value={`${quote.currency} ${quote.totalFees}`} />
          <Figure label="VAT on fees" value={`${quote.currency} ${quote.totalVat}`} />
          <Figure label="Total payable" value={`${quote.currency} ${quote.totalPayable}`} emphasis />
          <Figure
            label="Reaches the account"
            value={`${quote.currency} ${quote.netDisbursement}`}
            emphasis
          />
        </dl>

        <p className="mt-4 text-xs text-slate-500 dark:text-slate-400">
          Fees taken at disbursement are deducted before the money reaches the
          customer, which is why the amount borrowed and the amount received are
          different figures.
        </p>
      </div>

      {quote.fees.length > 0 && (
        <Panel title="Fees">
          <table className="w-full min-w-[32rem] text-sm">
            <thead className="border-b border-slate-200 text-left text-slate-500 dark:border-slate-800 dark:text-slate-400">
              <tr>
                <th className="px-4 py-2 font-medium">Fee</th>
                <th className="px-4 py-2 font-medium">Amount</th>
                <th className="px-4 py-2 font-medium">VAT</th>
                <th className="px-4 py-2 font-medium">Total</th>
                <th className="px-4 py-2 font-medium">Collected</th>
              </tr>
            </thead>
            <tbody>
              {quote.fees.map((fee) => (
                <tr key={fee.code} className="border-t border-slate-100 dark:border-slate-800">
                  <td className="px-4 py-2">{fee.name}</td>
                  <td className="px-4 py-2 font-mono text-xs">{fee.amount}</td>
                  <td className="px-4 py-2 font-mono text-xs">{fee.vat}</td>
                  <td className="px-4 py-2 font-mono text-xs">{fee.total}</td>
                  <td className="px-4 py-2 font-mono text-[11px] text-slate-500 dark:text-slate-400">
                    {fee.collectedAt.replace(/_/g, " ").toLowerCase()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      )}

      <Panel title="Repayment schedule">
        <table className="w-full min-w-[32rem] text-sm">
          <thead className="border-b border-slate-200 text-left text-slate-500 dark:border-slate-800 dark:text-slate-400">
            <tr>
              <th className="px-4 py-2 font-medium">#</th>
              <th className="px-4 py-2 font-medium">Due</th>
              <th className="px-4 py-2 font-medium">Principal</th>
              <th className="px-4 py-2 font-medium">Interest</th>
              <th className="px-4 py-2 font-medium">Balance</th>
            </tr>
          </thead>
          <tbody>
            {quote.schedule.map((row) => (
              <tr key={row.number} className="border-t border-slate-100 dark:border-slate-800">
                <td className="px-4 py-1.5 font-mono text-xs">{row.number}</td>
                <td className="px-4 py-1.5 font-mono text-xs">{row.amountDue}</td>
                <td className="px-4 py-1.5 font-mono text-xs">{row.principal}</td>
                <td className="px-4 py-1.5 font-mono text-xs">{row.interest}</td>
                <td className="px-4 py-1.5 font-mono text-xs">{row.closingBalance}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>
    </div>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <h3 className="border-b border-slate-100 px-5 py-3 text-xs font-medium tracking-wide text-slate-500 uppercase dark:border-slate-800 dark:text-slate-400">
        {title}
      </h3>
      <div className="overflow-x-auto">{children}</div>
    </section>
  );
}

function Figure({
  label,
  value,
  emphasis = false,
}: {
  label: string;
  value: string;
  emphasis?: boolean;
}) {
  return (
    <div>
      <dt className="text-xs text-slate-500 dark:text-slate-400">{label}</dt>
      <dd
        className={
          emphasis
            ? "mt-0.5 font-mono text-base font-semibold text-slate-900 dark:text-slate-100"
            : "mt-0.5 font-mono text-sm text-slate-700 dark:text-slate-300"
        }
      >
        {value}
      </dd>
    </div>
  );
}
