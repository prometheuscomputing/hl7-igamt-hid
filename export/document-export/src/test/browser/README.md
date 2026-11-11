# Browser test of the HTML export

`export-features.e2e.js` drives a real browser over four exported guides and checks the
interactive features of the HTML export: "Where used" blocks, hover previews on links and
the field detail panel.

It is a Playwright function, `async (page) => { ... }`, that returns a JSON string with
`failures` (empty when everything passes) and a `report` of counts per guide.

## What it checks

- Where used: recomputes the reverse index from the links in the page and compares it with
  every block (owners, kinds, field numbers), checks every row link has a target and that
  no id is repeated.
- Field panel: opens every field and component row and checks the panel against the row
  and the tables of its resource, then a set of known fields of the showcase guide
  (comments, definition texts, conformance statements, predicates, slicing, dynamic
  mapping, co-constraints, bindings), keyboard use, deep links, search results, phone
  width, print.
- Hover previews: one link of every kind and context; content, position inside the
  window, `aria-describedby`, hiding.
- No console error on any page.

## Running it

1. Export four guides from a running IGAMT and serve them from one folder as
   `rich-default.html`, `rich-all.html` (every export option enabled),
   `derived-diff.html` (a derived guide, differential export) and `base-default.html`,
   for example with `python3 -m http.server 8777`.
2. Run the function with Playwright:

```js
const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const test = require('./export-features.e2e.js');
  console.log(await test(page));
  await browser.close();
})();
```

The known-field checks expect the "Export Showcase" immunization guide (segments `PID_IZ`,
`OBX_IZ`, datatype `CX_IZ`, profile `Z22`). With other guides, keep the generic checks and
adjust that section.
