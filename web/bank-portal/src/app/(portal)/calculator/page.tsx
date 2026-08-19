import type { Metadata } from "next";

import { LoanCalculator } from "@/components/loan-calculator";
import { Warning } from "@/components/notices";
import { fetchFromBackend } from "@/lib/api/backend";
import { productListSchema } from "@/lib/api/contracts";
import { readAccessToken } from "@/lib/session";

export const metadata: Metadata = {
  title: "Loan calculator | Bank Portal",
};

export const dynamic = "force-dynamic";

export default async function CalculatorPage() {
  const token = await readAccessToken();
  const products = await fetchFromBackend(
    "/api/v1/products",
    productListSchema,
    token ?? undefined,
  );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Loan calculator</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
          The instalment, the interest, every fee with its VAT shown separately,
          the total payable and the full repayment schedule. The rate comes from
          the product version, not from this page, and every figure below is
          computed on the server &mdash; a client may show an indicative number,
          but the authoritative one is the backend&rsquo;s.
        </p>
      </div>

      {products.ok ? (
        <LoanCalculator products={products.data} />
      ) : (
        <Warning>{products.reason}</Warning>
      )}
    </div>
  );
}
