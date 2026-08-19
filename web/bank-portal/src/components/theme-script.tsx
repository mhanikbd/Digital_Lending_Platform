import { THEME_STORAGE_KEY, THEMES } from "@/lib/theme";

/**
 * Applies the stored theme before the browser paints.
 *
 * The server cannot know which theme this operator chose, so it renders the
 * default and this script corrects the attribute synchronously while the
 * browser is still parsing the body — early enough that nobody sees the default
 * flash first. <html> carries suppressHydrationWarning because of it.
 *
 * The type swap is the pattern from the Next.js "preventing flash before
 * hydration" guide: `text/javascript` on the server so the browser runs it on a
 * hard navigation, `text/plain` on the client so React neither warns about a
 * script in the tree nor tries to re-run it on a soft navigation.
 */
const ALLOWED = JSON.stringify(THEMES.map((theme) => theme.id));

const SCRIPT =
  `try{var k=${JSON.stringify(THEME_STORAGE_KEY)},a=${ALLOWED},t=localStorage.getItem(k);` +
  `if(a.indexOf(t)>-1){document.documentElement.dataset.theme=t}}catch(e){}`;

export function ThemeScript() {
  return (
    <script
      type={typeof window === "undefined" ? "text/javascript" : "text/plain"}
      suppressHydrationWarning
      dangerouslySetInnerHTML={{ __html: SCRIPT }}
    />
  );
}
