/**
 * The two things a data-backed page has to say when it has no data.
 *
 * <p>Kept apart because they mean different things. Empty is an ordinary state -
 * nothing matched, nobody has configured anything yet - and should not look like
 * an alarm. A warning is the platform admitting it could not answer, and should.
 */
export function Empty({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-lg border border-dashed border-slate-300 px-4 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
      {children}
    </p>
  );
}

export function Warning({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
      {children}
    </p>
  );
}
