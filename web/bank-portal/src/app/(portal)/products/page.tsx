import type { Metadata } from "next";

import { Empty, Warning } from "@/components/notices";
import { fetchFromBackend } from "@/lib/api/backend";
import { productListSchema, type ProductSummary, type ProductVersion } from "@/lib/api/contracts";
import { readAccessToken } from "@/lib/session";

export const metadata: Metadata = {
  title: "Products | Bank Portal",
};

// The catalogue changes when somebody activates a version, and a stale rate is
// worse than a slow page.
export const dynamic = "force-dynamic";

export default async function ProductsPage() {
  const token = await readAccessToken();
  const products = await fetchFromBackend(
    "/api/v1/products",
    productListSchema,
    token ?? undefined,
  );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Products</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
          What the bank sells, and the terms it currently sells on. A product
          holds nothing that gets repriced &mdash; the rate, the limits, the fees
          and the tenures all belong to a version, so an application approved
          today keeps today&rsquo;s terms however often the product is repriced
          afterwards.
        </p>
      </div>

      {products.ok ? (
        products.data.length === 0 ? (
          <Empty>No products are configured.</Empty>
        ) : (
          <div className="space-y-6">
            {products.data.map((product) => (
              <ProductCard key={product.code} product={product} />
            ))}
          </div>
        )
      ) : (
        <Warning>{products.reason}</Warning>
      )}
    </div>
  );
}

function ProductCard({ product }: { product: ProductSummary }) {
  const version = product.currentVersion;

  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <header className="flex flex-wrap items-baseline gap-x-3 gap-y-1 border-b border-slate-100 px-5 py-4 dark:border-slate-800">
        <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{product.name}</h2>
        {product.nameBn && (
          <span className="text-sm text-slate-500 dark:text-slate-400">{product.nameBn}</span>
        )}
        <span className="rounded-full bg-slate-100 px-2 py-0.5 font-mono text-[10px] text-slate-600 dark:bg-slate-800 dark:text-slate-300">
          {product.code}
        </span>
        <span className="font-mono text-[11px] text-slate-500 dark:text-slate-400">
          {product.productType} &middot; {product.category}
        </span>
        <span className="ml-auto font-mono text-[11px] text-slate-500 dark:text-slate-400">
          {product.versionCount} version{product.versionCount === 1 ? "" : "s"} issued
        </span>
      </header>

      {product.description && (
        <p className="px-5 pt-4 text-sm text-slate-600 dark:text-slate-400">
          {product.description}
        </p>
      )}

      {version ? (
        <VersionTerms version={version} currency={version.currency} />
      ) : (
        <p className="px-5 py-6 text-sm text-slate-500 dark:text-slate-400">
          No version of this product is currently on sale. It cannot be quoted or
          applied for until one is activated.
        </p>
      )}
    </section>
  );
}

function VersionTerms({ version, currency }: { version: ProductVersion; currency: string }) {
  return (
    <div className="px-5 py-4">
      <p className="mb-3 font-mono text-[11px] text-slate-500 dark:text-slate-400">
        Version {version.versionNo} &middot; {version.status} &middot; effective{" "}
        {version.effectiveFrom}
        {version.effectiveTo ? ` to ${version.effectiveTo}` : ""}
      </p>

      <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-3 lg:grid-cols-4">
        <Fact label="Amount">
          {currency} {version.minAmount} &ndash; {version.maxAmount}
        </Fact>
        <Fact label="Tenures">{version.tenures.join(", ")} months</Fact>
        <Fact label="Rate">{version.interestRate}% p.a.</Fact>
        <Fact label="Method">{version.interestMethod.replace(/_/g, " ").toLowerCase()}</Fact>
        <Fact label="Repayment">{version.repaymentFrequency.toLowerCase()}</Fact>
        <Fact label="Segment">{version.customerSegment}</Fact>
        <Fact label="Income multiple">{version.incomeMultiple ?? "not capped"}</Fact>
        <Fact label="Max debt burden">
          {version.maxDbr ? `${percent(version.maxDbr)} of income` : "not capped"}
        </Fact>
        <Fact label="Regulatory ceiling">{version.regulatoryMaxAmount ?? "none"}</Fact>
        <Fact label="Total exposure ceiling">{version.maxTotalExposure ?? "none"}</Fact>
        <Fact label="Offered share">{percent(version.recommendedRatio)} of the maximum</Fact>
        <Fact label="Security">
          {version.collateralRequired ? "collateral required" : "unsecured"}
          {version.guarantorRequired ? ", guarantor required" : ""}
        </Fact>
      </dl>

      {version.fees.length > 0 && (
        <div className="mt-5">
          <h3 className="mb-2 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
            Fees
          </h3>
          <div className="overflow-x-auto rounded-md border border-slate-200 dark:border-slate-800">
            <table className="w-full min-w-[34rem] text-sm">
              <thead className="border-b border-slate-200 text-left text-slate-500 dark:border-slate-800 dark:text-slate-400">
                <tr>
                  <th className="px-4 py-2 font-medium">Fee</th>
                  <th className="px-4 py-2 font-medium">Basis</th>
                  <th className="px-4 py-2 font-medium">VAT</th>
                  <th className="px-4 py-2 font-medium">Collected</th>
                </tr>
              </thead>
              <tbody>
                {version.fees.map((fee) => (
                  <tr key={fee.code} className="border-t border-slate-100 dark:border-slate-800">
                    <td className="px-4 py-2">{fee.name}</td>
                    <td className="px-4 py-2 font-mono text-xs">
                      {fee.calculationMethod === "FLAT"
                        ? `${currency} ${fee.flatAmount}`
                        : `${fee.rate}% of principal`}
                    </td>
                    <td className="px-4 py-2 font-mono text-xs">{fee.vatRate}%</td>
                    <td className="px-4 py-2 font-mono text-[11px] text-slate-500 dark:text-slate-400">
                      {fee.collectedAt.replace(/_/g, " ").toLowerCase()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {version.riskLimits.length > 0 && (
        <div className="mt-5">
          <h3 className="mb-2 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
            Ceilings by risk grade
          </h3>
          <div className="flex flex-wrap gap-2">
            {version.riskLimits.map((limit) => (
              <span
                key={limit.riskProfile}
                className="rounded-md border border-slate-200 px-3 py-1 font-mono text-xs text-slate-600 dark:border-slate-800 dark:text-slate-300"
              >
                {limit.riskProfile} &le; {currency} {limit.maxAmount}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
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
 * A stored fraction shown as a percentage.
 *
 * <p>The decimal point is shifted two places by string surgery rather than by
 * multiplying. These values are decimals the backend was careful to send as
 * text; turning them into JavaScript numbers to display them would undo that
 * care for no reason at all.
 */
function percent(fraction: string): string {
  const negative = fraction.startsWith("-");
  const [whole = "0", decimals = ""] = (negative ? fraction.slice(1) : fraction).split(".");
  const padded = decimals.padEnd(2, "0");

  const shiftedWhole = (whole + padded.slice(0, 2)).replace(/^0+(?=\d)/, "");
  const shiftedDecimals = padded.slice(2).replace(/0+$/, "");

  return `${negative ? "-" : ""}${shiftedWhole}${shiftedDecimals ? `.${shiftedDecimals}` : ""}%`;
}
