import type { Metadata } from "next";

import { fetchFromBackend } from "@/lib/api/backend";
import {
  orgScopeSchema,
  orgUnitTreeSchema,
  type OrgScope,
  type OrgUnit,
} from "@/lib/api/contracts";
import { readAccessToken } from "@/lib/session";

export const metadata: Metadata = {
  title: "Organization | Bank Portal",
};

// The hierarchy is configuration, and what the reader may see of it depends on
// who is asking, so it is never served from a cache.
export const dynamic = "force-dynamic";

/** Colour by kind, so the shape of the bank reads at a glance. */
const TYPE_STYLES: Record<string, string> = {
  BANK: "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900",
  ZONE: "bg-blue-100 text-blue-900 dark:bg-blue-950 dark:text-blue-200",
  REGION: "bg-indigo-100 text-indigo-900 dark:bg-indigo-950 dark:text-indigo-200",
  BRANCH: "bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200",
};

const DEFAULT_TYPE_STYLE = "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200";

export default async function OrganizationPage() {
  const token = await readAccessToken();
  const [tree, scope] = await Promise.all([
    fetchFromBackend("/api/v1/organization/units", orgUnitTreeSchema, token ?? undefined),
    fetchFromBackend("/api/v1/organization/my-scope", orgScopeSchema, token ?? undefined),
  ]);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Organization</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
          The bank as the platform holds it: one configurable tree rather than a
          table per kind of unit. Units are dated rather than deleted, because a
          branch that closes keeps its loans.
        </p>
      </div>

      {scope.ok ? <ScopePanel scope={scope.data} /> : <Unavailable reason={scope.reason} />}

      <section>
        <h2 className="text-base font-semibold">Hierarchy</h2>
        {tree.ok ? (
          tree.data.length === 0 ? (
            <p className="mt-3 rounded-lg border border-dashed border-slate-300 px-4 py-6 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
              No units are configured yet.
            </p>
          ) : (
            <ul className="mt-4 space-y-1">
              {tree.data.map((unit) => (
                <UnitRow key={unit.id} unit={unit} depth={0} visible={scope.ok ? scope.data.visibleUnitCodes : []} />
              ))}
            </ul>
          )
        ) : (
          <Unavailable reason={tree.reason} />
        )}
      </section>
    </div>
  );
}

/**
 * What the reader may see, and the two facts that decide it.
 *
 * <p>Both are shown because "you can see three branches" is unactionable on its
 * own: an administrator needs to know whether to change the role or the posting.
 */
function ScopePanel({ scope }: { scope: OrgScope }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <h2 className="text-base font-semibold">Your scope</h2>
      <dl className="mt-4 grid gap-x-8 gap-y-4 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-slate-500 dark:text-slate-400">Widest role scope</dt>
          <dd className="mt-1 font-mono text-xs">{scope.scopeLevel}</dd>
        </div>
        <div>
          <dt className="text-slate-500 dark:text-slate-400">Posted to</dt>
          <dd className="mt-1 font-mono text-xs">
            {scope.postings.length === 0
              ? "nowhere"
              : scope.postings
                  .map((posting) => `${posting.code}${posting.primary ? " (home)" : ""}`)
                  .join(", ")}
          </dd>
        </div>
        <div>
          <dt className="text-slate-500 dark:text-slate-400">Units you may act on</dt>
          <dd className="mt-1 font-mono text-xs">{scope.visibleUnitCodes.length}</dd>
        </div>
      </dl>
    </section>
  );
}

function UnitRow({ unit, depth, visible }: { unit: OrgUnit; depth: number; visible: string[] }) {
  const inScope = visible.includes(unit.code);
  return (
    <li>
      <div
        className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md px-2 py-1.5 hover:bg-slate-50 dark:hover:bg-slate-800/60"
        style={{ paddingLeft: `${depth * 1.5 + 0.5}rem` }}
      >
        <span
          className={`rounded px-1.5 py-0.5 font-mono text-[10px] tracking-wide ${
            TYPE_STYLES[unit.unitType] ?? DEFAULT_TYPE_STYLE
          }`}
        >
          {unit.unitType}
        </span>
        <span className="font-mono text-xs text-slate-500 dark:text-slate-400">{unit.code}</span>
        <span className="text-sm">{unit.name}</span>
        {unit.city && (
          <span className="text-xs text-slate-400 dark:text-slate-500">{unit.city}</span>
        )}
        {/* Dimmed rather than hidden: knowing a branch exists is not the same as
            being able to act on it, and hiding it makes the tree look wrong. */}
        {!inScope && (
          <span className="ml-auto font-mono text-[10px] text-slate-400 dark:text-slate-600">
            outside your scope
          </span>
        )}
      </div>
      {unit.children.length > 0 && (
        <ul className="space-y-1">
          {unit.children.map((child) => (
            <UnitRow key={child.id} unit={child} depth={depth + 1} visible={visible} />
          ))}
        </ul>
      )}
    </li>
  );
}

function Unavailable({ reason }: { reason: string }) {
  return (
    <p className="mt-3 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
      {reason}
    </p>
  );
}
