import { NaztechMark } from "@/components/naztech-logo";
import { cn } from "@/lib/cn";

/**
 * Digital banking mark: a bank facade with the network nodes that make it
 * digital. Drawn inline rather than shipped as an asset so it takes its
 * gradient from the active theme and stays crisp at every size.
 */
export function BrandMark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 48 48"
      role="img"
      aria-label="Digital Lending Platform"
      className={cn("size-10", className)}
    >
      <defs>
        <linearGradient id="dlp-mark" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" style={{ stopColor: "var(--brand)" }} />
          <stop offset="100%" style={{ stopColor: "var(--brand-strong)" }} />
        </linearGradient>
      </defs>

      <rect x="2" y="2" width="44" height="44" rx="13" fill="url(#dlp-mark)" />

      {/* Bank facade: pediment, columns, plinth. */}
      <g style={{ fill: "var(--brand-ink)" }}>
        <path d="M24 11.5 36.5 19H11.5L24 11.5Z" />
        <rect x="14.5" y="21.5" width="3.2" height="10" rx="1" />
        <rect x="22.4" y="21.5" width="3.2" height="10" rx="1" />
        <rect x="30.3" y="21.5" width="3.2" height="10" rx="1" />
        <rect x="11.5" y="33.5" width="25" height="3.2" rx="1.4" />
      </g>

      {/* Network nodes: the digital half of the mark. */}
      <g
        style={{ stroke: "var(--brand-ink)" }}
        strokeOpacity="0.75"
        strokeWidth="1.2"
        strokeLinecap="round"
      >
        <path d="M36.5 24.5 40.5 24.5" />
        <path d="M36.5 29 39 31.5" />
      </g>
      <g style={{ fill: "var(--brand-ink)" }}>
        <circle cx="41.6" cy="24.5" r="1.7" />
        <circle cx="39.8" cy="32.3" r="1.4" />
      </g>
    </svg>
  );
}

/**
 * The mark next to the platform name.
 *
 * `hero` uses the themed hero tokens, for the sign-in screen. `portal` is the
 * signed-in chrome, which is outside the theme switch and so keeps the portal's
 * own palette: the tokens would otherwise resolve to the dark theme's light ink
 * on the portal's white header. The mark follows the same split.
 */
export function BrandLockup({
  tone = "portal",
  className,
  markClassName,
}: {
  tone?: "portal" | "hero";
  className?: string;
  markClassName?: string;
}) {
  const hero = tone === "hero";
  return (
    <div className={cn("flex items-center gap-3", className)}>
      {/* The vendor mark in both places; only the mechanism that decides the
          ground differs. See NaztechMark. */}
      <NaztechMark surface={hero ? "theme" : "portal"} className={markClassName} />
      <div className="leading-tight">
        <p
          className={cn(
            "font-semibold tracking-tight",
            hero ? "text-hero-ink" : "text-slate-900 dark:text-slate-100",
          )}
        >
          Digital Lending Platform
        </p>
        <p
          className={cn(
            "text-xs font-medium",
            hero ? "text-hero-accent" : "text-blue-700 dark:text-blue-400",
          )}
        >
          Loan Origination &amp; Management
        </p>
      </div>
    </div>
  );
}
