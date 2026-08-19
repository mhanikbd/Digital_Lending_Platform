"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import type { ApplicationDetail, AvailableAction } from "@/lib/api/contracts";

/**
 * The actions the backend says this person may take.
 *
 * <p>Nothing here decides what to draw. The list arrives from
 * {@code available-actions}, already resolved against the role/state map and the
 * transition table, and this component renders it. That is the point of the
 * endpoint existing: a screen that worked out for itself which buttons a branch
 * manager should see would be the same hard-coding the specification forbids in
 * the backend, moved somewhere harder to audit.
 *
 * <p>Whether a reason is required comes with the action, so the box appears
 * because the configuration says so rather than because somebody remembered.
 */
export function ApplicationActions({
  applicationNo,
  actions,
  application,
}: {
  applicationNo: string;
  actions: AvailableAction[];
  application: ApplicationDetail;
}) {
  const router = useRouter();
  const [chosen, setChosen] = useState<AvailableAction | null>(null);
  const [reason, setReason] = useState("");
  const [approvedAmount, setApprovedAmount] = useState("");
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  // VIEW and EDIT move nothing, so they are not offered as buttons here.
  const moves = actions.filter((action) => action.toState);

  async function take(action: AvailableAction) {
    setPending(true);
    setProblem(null);

    const body: Record<string, unknown> = { action: action.action, toState: action.toState };
    if (reason.trim() !== "") {
      body.reason = reason.trim();
    }
    if (approvedAmount.trim() !== "") {
      body.approvedAmount = approvedAmount.trim();
    }

    try {
      const response = await fetch(`/bff/applications/${applicationNo}/actions`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      const payload = (await response.json().catch(() => null)) as
        | { ok?: boolean; reason?: string }
        | null;

      if (!response.ok || !payload?.ok) {
        // The backend's own refusal, which names the role or the state that
        // stopped it. Nothing to soften.
        setProblem(payload?.reason ?? "The action was refused.");
        return;
      }

      setChosen(null);
      setReason("");
      setApprovedAmount("");
      router.refresh();
    } catch {
      setProblem("The portal could not reach the API.");
    } finally {
      setPending(false);
    }
  }

  if (moves.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-slate-300 px-4 py-6 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
        You have no action on this application while it is {application.stateName.toLowerCase()}.
      </p>
    );
  }

  const needsAmount =
    chosen?.action === "APPROVE" || chosen?.action === "APPROVE_WITH_CONDITION";

  return (
    <div className="space-y-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <p className="text-[11px] font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
        Actions available to you
      </p>

      <div className="flex flex-wrap gap-2">
        {moves.map((action) => (
          <button
            key={`${action.action}-${action.toState}`}
            type="button"
            onClick={() => {
              setChosen(action);
              setProblem(null);
            }}
            title={`Sends the application to ${action.toState}`}
            className={
              chosen?.toState === action.toState
                ? "rounded-md border border-slate-900 bg-slate-900 px-3 py-1.5 text-xs font-medium text-white dark:border-slate-100 dark:bg-slate-100 dark:text-slate-900"
                : "rounded-md border border-slate-300 px-3 py-1.5 text-xs text-slate-700 hover:border-slate-500 dark:border-slate-700 dark:text-slate-300"
            }
          >
            {action.label}
          </button>
        ))}
      </div>

      {chosen && (
        <div className="space-y-3 border-t border-slate-100 pt-3 dark:border-slate-800">
          <p className="text-xs text-slate-600 dark:text-slate-400">
            {chosen.label} sends this application to{" "}
            <span className="font-mono text-[11px]">{chosen.toState}</span>.
          </p>

          {needsAmount && (
            <label className="block">
              <span className="mb-1 block text-xs text-slate-500 dark:text-slate-400">
                Approved amount — leave blank to approve the {application.requestedAmount} requested
              </span>
              <input
                value={approvedAmount}
                onChange={(event) => setApprovedAmount(event.target.value)}
                inputMode="decimal"
                placeholder={application.requestedAmount}
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
              />
            </label>
          )}

          <label className="block">
            <span className="mb-1 block text-xs text-slate-500 dark:text-slate-400">
              {chosen.action === "QUERY"
                ? "The question to put to the branch"
                : chosen.reasonRequired
                  ? "Reason — required"
                  : "Reason — optional"}
            </span>
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              rows={2}
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
            />
          </label>

          <div className="flex gap-2">
            <button
              type="button"
              disabled={pending || (chosen.reasonRequired && reason.trim() === "")}
              onClick={() => take(chosen)}
              className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 dark:bg-slate-100 dark:text-slate-900"
            >
              {pending ? "Working…" : `Confirm ${chosen.label.toLowerCase()}`}
            </button>
            <button
              type="button"
              onClick={() => {
                setChosen(null);
                setProblem(null);
              }}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-600 dark:border-slate-700 dark:text-slate-400"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {problem && (
        <p className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
          {problem}
        </p>
      )}
    </div>
  );
}
