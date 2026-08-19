/**
 * Shared theme constants.
 *
 * Deliberately not inside a "use client" module. The inline no-flash script is
 * a server component, and importing a plain constant across the client boundary
 * hands back a client reference rather than the value — which silently compiled
 * to `localStorage.getItem(undefined)` when this lived in the switcher.
 */
export const THEME_STORAGE_KEY = "dlp-theme";

export const THEMES = [
  { id: "dark", label: "Dark" },
  { id: "light", label: "Light" },
  { id: "green", label: "Green" },
] as const;

export type ThemeId = (typeof THEMES)[number]["id"];

/** The palette rendered on the server, before the script reads localStorage. */
export const DEFAULT_THEME: ThemeId = "green";
