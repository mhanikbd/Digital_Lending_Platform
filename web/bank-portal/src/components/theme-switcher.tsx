"use client";

import { cn } from "@/lib/cn";
import { THEME_STORAGE_KEY, THEMES, type ThemeId } from "@/lib/theme";

/**
 * Switches the palette by rewriting `data-theme` on <html>.
 *
 * Deliberately stateless. Which button looks active is decided in CSS by the
 * theme-* variants, not by React, so the control renders identically on the
 * server and in the browser and there is nothing for hydration to disagree
 * about — the attribute is already set by the inline script in the layout
 * before React ever runs.
 */
const ACTIVE: Record<ThemeId, string> = {
  dark: "theme-dark:bg-brand theme-dark:text-brand-ink theme-dark:shadow-sm",
  light: "theme-light:bg-brand theme-light:text-brand-ink theme-light:shadow-sm",
  green: "theme-green:bg-brand theme-green:text-brand-ink theme-green:shadow-sm",
};

/**
 * Lives at module scope on purpose: writing to the document from inside the
 * component body trips the compiler's immutability rule, and this is a DOM
 * command rather than component state.
 */
function applyTheme(theme: ThemeId) {
  document.documentElement.dataset.theme = theme;
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Private browsing or a locked-down profile: the theme still applies for
    // this visit, it just will not be remembered.
  }
}

export function ThemeSwitcher({ className }: { className?: string }) {
  return (
    <div
      role="group"
      aria-label="Colour theme"
      className={cn(
        "inline-flex items-center gap-1 rounded-full border border-line bg-panel p-1",
        className,
      )}
    >
      {THEMES.map((theme) => (
        <button
          key={theme.id}
          type="button"
          onClick={() => applyTheme(theme.id)}
          className={cn(
            "rounded-full px-3 py-1 text-xs font-medium text-ink-muted transition hover:text-ink",
            "focus-visible:ring-2 focus-visible:ring-brand/50 focus-visible:outline-none",
            ACTIVE[theme.id],
          )}
        >
          {theme.label}
        </button>
      ))}
    </div>
  );
}
