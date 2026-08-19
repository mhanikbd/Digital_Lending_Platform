import { cn } from "@/lib/cn";

/**
 * The vendor's own marks.
 *
 * Both colourways are rendered and one is revealed by CSS, which keeps this a
 * plain server component: no state, no effect, and nothing for hydration to
 * disagree about.
 *
 * The variant is chosen by the **surface**, not by a theme name. Silver only
 * reads on a dark ground and blue only on a light one, and the two surfaces in
 * this application decide their darkness by different mechanisms:
 *
 * <ul>
 *   <li>the sign-in screen switches on {@code data-theme}, so it uses the
 *       theme-* variants;</li>
 *   <li>the signed-in portal chrome sits outside that switch and follows the
 *       operating system, so it uses the {@code dark:} variant.</li>
 * </ul>
 */
const ICON_SILVER = "/images/logos/nav-icon.png";
const ICON_BLUE = "/images/logos/naztech-icon-Blue.png";
const LOGO_SILVER = "/images/logos/naztech%20full%20logo-silver.png";
const LOGO_BLUE = "/images/logos/naztech2.0-final-logo.png";

/** Which mechanism decides whether the ground behind the mark is dark. */
type Surface = "theme" | "portal";

/** The hexagon mark alone, for an icon slot. Square artwork. */
export function NaztechMark({
  surface = "theme",
  className,
}: {
  surface?: Surface;
  className?: string;
}) {
  const showSilver =
    surface === "portal"
      ? "hidden dark:block"
      : "hidden theme-dark:block theme-green:block";
  const showBlue =
    surface === "portal" ? "block dark:hidden" : "hidden theme-light:block";

  return (
    <>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={ICON_SILVER}
        alt="naztech"
        className={cn("object-contain", showSilver, className)}
      />
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={ICON_BLUE}
        alt="naztech"
        className={cn("object-contain", showBlue, className)}
      />
    </>
  );
}

/** The full wordmark, for a credit line. Roughly 4:1. */
export function NaztechLogo({ className }: { className?: string }) {
  return (
    <>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={LOGO_SILVER}
        alt="naztech"
        className={cn("hidden w-auto object-contain theme-dark:block", className)}
      />
      {/* Blue on both light and green: their sign-in panels are both white. */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={LOGO_BLUE}
        alt="naztech"
        className={cn(
          "hidden w-auto object-contain theme-light:block theme-green:block",
          className,
        )}
      />
    </>
  );
}
