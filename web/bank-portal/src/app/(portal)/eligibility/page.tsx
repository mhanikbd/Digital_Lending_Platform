import type { Metadata } from "next";

import { EligibilityCheck } from "@/components/eligibility-check";
import { Warning } from "@/components/notices";
import { fetchFromBackend } from "@/lib/api/backend";
import { customerListSchema, productListSchema } from "@/lib/api/contracts";
import { readAccessToken } from "@/lib/session";

export const metadata: Metadata = {
  title: "Eligibility | Bank Portal",
};

export const dynamic = "force-dynamic";

export default async function EligibilityPage() {
  const token = await readAccessToken();
  const [customers, products] = await Promise.all([
    fetchFromBackend("/api/v1/customers", customerListSchema, token ?? undefined),
    fetchFromBackend("/api/v1/products", productListSchema, token ?? undefined),
  ]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Eligibility</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
          Whether a customer qualifies, and for how much. The criteria are
          configuration rather than code, so what is tested here can be changed
          with an insert; the amount is the lowest of every configured limit, and
          the engine reports each one whether it bound or not. Every check is
          recorded, pass or fail, with the values it was decided on.
        </p>
      </div>

      {customers.ok && products.ok ? (
        <EligibilityCheck customers={customers.data} products={products.data} />
      ) : (
        // Whichever of the two failed; if both did, the customer list is named,
        // because that is the one a banker will chase first.
        <Warning>{!customers.ok ? customers.reason : !products.ok ? products.reason : ""}</Warning>
      )}
    </div>
  );
}
