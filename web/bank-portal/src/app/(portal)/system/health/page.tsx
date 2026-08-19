import type { Metadata } from "next";

import { ConnectivityPanel } from "@/components/connectivity-panel";
import { fetchFromBackend } from "@/lib/api/backend";
import { platformHealthSchema, platformInfoSchema } from "@/lib/api/contracts";

export const metadata: Metadata = {
  title: "System health | Bank Portal",
};

// Connectivity must be measured on every request, never served from a cache.
export const dynamic = "force-dynamic";

export default async function SystemHealthPage() {
  const [health, info] = await Promise.all([
    fetchFromBackend("/api/v1/platform/health", platformHealthSchema),
    fetchFromBackend("/api/v1/platform/info", platformInfoSchema),
  ]);

  const initialData = health.ok
    ? ({ ok: true, health: health.data, correlationId: health.correlationId } as const)
    : ({ ok: false, reason: health.reason, correlationId: health.correlationId } as const);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">System health</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
          Proves the Milestone 1 vertical end to end: this page is rendered by
          Next.js, which calls Spring Boot server side, which in turn reaches
          PostgreSQL, Redis and object storage.
        </p>
      </div>

      <ConnectivityPanel initialData={initialData} />

      <section className="rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <header className="border-b border-slate-200 px-5 py-4 dark:border-slate-800">
          <h2 className="text-base font-semibold">Backend identity</h2>
        </header>

        {info.ok ? (
          <dl className="grid gap-x-8 gap-y-3 px-5 py-4 text-sm sm:grid-cols-2">
            <Detail label="Application" value={info.data.application} />
            <Detail label="API version" value={info.data.apiVersion} />
            <Detail label="Environment" value={info.data.environment} />
            <Detail
              label="Server time (UTC)"
              value={new Date(info.data.serverTime).toISOString()}
            />
            <Detail label="Correlation id" value={info.correlationId} />
          </dl>
        ) : (
          <div className="px-5 py-6 text-sm text-red-700 dark:text-red-400">{info.reason}</div>
        )}
      </section>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-slate-500 dark:text-slate-400">{label}</dt>
      <dd className="mt-0.5 font-mono text-xs break-all">{value}</dd>
    </div>
  );
}
