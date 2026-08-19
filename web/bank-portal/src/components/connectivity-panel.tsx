"use client";

import { useQuery } from "@tanstack/react-query";

import { StatusPill } from "@/components/status-pill";
import type { PlatformHealth } from "@/lib/api/contracts";

type ProxyResponse =
  | { ok: true; health: PlatformHealth; correlationId: string }
  | { ok: false; reason: string; correlationId: string };

const COMPONENT_LABELS: Record<string, string> = {
  database: "PostgreSQL",
  cache: "Redis",
  objectStorage: "MinIO object storage",
};

async function loadConnectivity(): Promise<ProxyResponse> {
  const response = await fetch("/bff/platform/health", { cache: "no-store" });
  return (await response.json()) as ProxyResponse;
}

/**
 * Live connectivity view. Rendered first on the server so the page is useful
 * without JavaScript, then kept current by polling the portal's own route
 * handler.
 */
export function ConnectivityPanel({ initialData }: { initialData: ProxyResponse }) {
  const { data, isFetching, refetch, dataUpdatedAt } = useQuery({
    queryKey: ["platform-health"],
    queryFn: loadConnectivity,
    initialData,
    refetchInterval: 15_000,
  });

  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <header className="flex flex-wrap items-center gap-3 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
        <h2 className="text-base font-semibold">Infrastructure connectivity</h2>
        {data.ok && <StatusPill status={data.health.status} />}
        <button
          type="button"
          onClick={() => void refetch()}
          disabled={isFetching}
          className="ml-auto rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:hover:bg-slate-800"
        >
          {isFetching ? "Checking..." : "Check now"}
        </button>
      </header>

      {data.ok ? (
        <table className="w-full text-sm">
          <thead className="text-left text-slate-500 dark:text-slate-400">
            <tr>
              <th className="px-5 py-2 font-medium">Dependency</th>
              <th className="px-5 py-2 font-medium">Status</th>
              <th className="px-5 py-2 font-medium">Detail</th>
            </tr>
          </thead>
          <tbody>
            {data.health.components.map((component) => (
              <tr
                key={component.name}
                className="border-t border-slate-100 dark:border-slate-800"
              >
                <td className="px-5 py-3">
                  {COMPONENT_LABELS[component.name] ?? component.name}
                </td>
                <td className="px-5 py-3">
                  <StatusPill status={component.status} />
                </td>
                <td className="px-5 py-3 text-slate-600 dark:text-slate-400">
                  {component.detail}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="px-5 py-6">
          <p className="text-sm text-red-700 dark:text-red-400">{data.reason}</p>
          <p className="mt-2 font-mono text-xs text-slate-500 dark:text-slate-400">
            correlation id: {data.correlationId}
          </p>
        </div>
      )}

      <footer className="border-t border-slate-200 px-5 py-3 text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400">
        Refreshed automatically every 15 seconds. Last checked{" "}
        {/* Two things here are client-specific and cannot agree with the server:
            dataUpdatedAt is stamped with Date.now() when the query is created,
            which happens once during server rendering and again in the browser,
            and toLocaleTimeString formats in the runtime's own locale and time
            zone. The operator's clock is the one that matters, so the DOM is
            allowed to win rather than this being reported as a mismatch. */}
        <time dateTime={new Date(dataUpdatedAt).toISOString()} suppressHydrationWarning>
          {new Date(dataUpdatedAt).toLocaleTimeString()}
        </time>
        .
      </footer>
    </section>
  );
}
