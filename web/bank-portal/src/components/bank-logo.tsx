import { existsSync } from "node:fs";
import { join } from "node:path";

import { cn } from "@/lib/cn";

/**
 * The client bank's logo.
 *
 * Renders the official artwork from `public/`. The candidate list is ordered by
 * preference - a vector first if one is ever supplied, since the supplied PNG is
 * 595x187 and will soften on a high-density screen at large sizes.
 *
 * The typographic lockup below is a fallback for the case where the artwork is
 * missing (a fresh clone, a broken deploy). It is a placeholder, not a
 * reproduction of the mark, and should never be what ships.
 */
const CANDIDATES = [
  "images/logos/NRBC_Logo.svg",
  "images/logos/NRBC_Logo.png",
  "nrbc-bank-logo.svg",
  "nrbc-bank-logo.png",
] as const;

function findArtwork(): string | null {
  for (const name of CANDIDATES) {
    if (existsSync(join(process.cwd(), "public", name))) return `/${name}`;
  }
  return null;
}

export function BankLogo({
  size = "sm",
  className,
}: {
  /** `lg` is the primary identity above the platform name; `sm` is a corner mark. */
  size?: "sm" | "lg";
  className?: string;
}) {
  const artwork = findArtwork();
  const large = size === "lg";

  if (artwork) {
    return (
      // The artwork has a white background baked in rather than an alpha
      // channel, so on the dark palette it is boxed deliberately - rounded, with
      // matching padding - instead of sitting on the panel as a bare white
      // rectangle. On the light and green palettes the panel is already white,
      // so the wrapper is invisible.
      <span
        className={cn(
          // The chip is painted only on the dark palette, but its geometry is
          // reserved in every theme. Applying the padding on one theme alone made
          // that column 16px taller there, and because the column is vertically
          // centred, everything below it - the theme switcher most visibly -
          // jumped 8px on each switch into or out of dark.
          "inline-flex items-center justify-center rounded-xl px-3 py-2",
          "theme-dark:bg-white",
          className,
        )}
      >
        {/* Height is pinned - fluid at the large size so the lockup shrinks with
            the rest of the column - and width follows the 3.18:1 artwork.
            eslint-disable-next-line @next/next/no-img-element */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={artwork}
          alt="NRBC Bank"
          className={cn(
            "w-auto max-w-full object-contain",
            large ? "h-[clamp(1.75rem,6.5vh,5.5rem)] tiny:h-7" : "h-9",
          )}
        />
      </span>
    );
  }

  return (
    <div
      role="img"
      aria-label="NRBC Bank (provisional lockup)"
      title="Artwork missing - expected public/images/logos/NRBC_Logo.png"
      className={cn(
        // Bank marks are colour-locked, so on the dark palette the lockup sits
        // on its own light chip rather than being recoloured to suit the page.
        "inline-flex flex-col rounded-lg leading-none theme-dark:bg-white/95",
        large
          ? "items-center px-[clamp(0.9rem,2vh,1.75rem)] py-[clamp(0.5rem,1.4vh,1rem)]"
          : "items-end px-2.5 py-1",
        className,
      )}
    >
      <span
        className={cn(
          "font-extrabold tracking-tight",
          large ? "text-[length:var(--fs-logo)]" : "text-xl",
        )}
      >
        <span style={{ color: "#00693e" }}>NRBC</span>
        <span style={{ color: "#00a651" }}>BANK</span>
      </span>
      <span
        className={cn(
          "font-medium tracking-wide",
          large
            ? "mt-[var(--gap-tight)] text-[length:var(--fs-logo-note)] tiny:hidden"
            : "mt-0.5 text-[9px]",
        )}
        style={{ color: "#4a7059" }}
      >
        NRB Commercial Bank PLC
      </span>
    </div>
  );
}
