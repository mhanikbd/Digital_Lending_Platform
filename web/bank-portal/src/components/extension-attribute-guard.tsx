/**
 * Strips attributes that browser extensions inject into the DOM before React
 * hydrates.
 *
 * Several common extensions mark elements they have processed - ad blockers
 * write `bis_skin_checked`, Grammarly writes `data-gr-ext-installed`, and the
 * Bitdefender-derived scripts write `__processed_<uuid>__`. The extension does
 * this to the server-rendered HTML while the page is still parsing, so React
 * compares its own render against a DOM that has grown attributes it never
 * emitted and reports a hydration mismatch. The elements hit are often
 * framework internals or <body> itself, which leaves no component to put
 * `suppressHydrationWarning` on.
 *
 * Matching is by **pattern, not by name**. The first version of this guard held
 * a list of exact attribute names and passed it to `attributeFilter`, which
 * cannot match `__processed_2aeba37e-...__`: that name carries a fresh uuid on
 * every page load, so no fixed list can ever contain it.
 *
 * The real fix is the operator disabling the extension for this origin. This
 * only stops the noise from burying genuine hydration bugs in the meantime, so
 * it is **development only** - production carries no observer and no cost.
 */
const PATTERNS = [
  "^bis_", // ad-blocker content scripts
  "^__processed_", // Bitdefender-derived, uuid-suffixed
  "^data-new-gr-", // Grammarly
  "^data-gr-", // Grammarly
  "^data-lt-", // LanguageTool
  "^cz-shortcut-listen$", // ColorZilla
];

const SCRIPT = `(function(){
var P=[${PATTERNS.map((pattern) => `/${pattern}/`).join(",")}];
function unwanted(name){for(var i=0;i<P.length;i++){if(P[i].test(name)){return true}}return false}
function strip(el){
  if(!el||!el.attributes){return}
  var attrs=el.attributes;
  for(var i=attrs.length-1;i>=0;i--){if(unwanted(attrs[i].name)){el.removeAttribute(attrs[i].name)}}
}
function sweep(){
  strip(document.documentElement);
  strip(document.body);
  var all=document.querySelectorAll("*");
  for(var i=0;i<all.length;i++){strip(all[i])}
}
sweep();
if(typeof MutationObserver!=="function"){return}
// No attributeFilter: the names are not known ahead of time, which is the whole
// reason the previous version missed them. The callback is a handful of regex
// tests, and this only ever runs in development.
var observer=new MutationObserver(function(records){
  for(var i=0;i<records.length;i++){
    var record=records[i];
    if(record.type==="attributes"&&record.attributeName&&unwanted(record.attributeName)
       &&record.target&&record.target.removeAttribute){
      record.target.removeAttribute(record.attributeName)
    }
  }
});
observer.observe(document.documentElement,{subtree:true,attributes:true});
})();`;

export function ExtensionAttributeGuard() {
  if (process.env.NODE_ENV === "production") return null;

  return (
    <script
      // Same type swap as ThemeScript: run it on the server-rendered document,
      // inert on the client so React neither warns nor re-runs it.
      type={typeof window === "undefined" ? "text/javascript" : "text/plain"}
      suppressHydrationWarning
      dangerouslySetInnerHTML={{ __html: SCRIPT }}
    />
  );
}
