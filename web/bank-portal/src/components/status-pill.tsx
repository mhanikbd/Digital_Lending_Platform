import { cn } from "@/lib/cn";

/** Small coloured label for an UP/DOWN state. Presentational only. */
export function StatusPill({ status }: { status: "UP" | "DOWN" }) {
  const up = status === "UP";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium",
        up
          ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
          : "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
      )}
    >
      <span
        aria-hidden
        className={cn("size-1.5 rounded-full", up ? "bg-emerald-600" : "bg-red-600")}
      />
      {status}
    </span>
  );
}
