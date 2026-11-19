# Browser test of the HTML export

`export-features.e2e.js` drives a real browser over four exported guides and checks the
interactive features of the HTML export: "Where used" blocks, hover previews on links and
the field detail panel.

It is a Playwright function, `async (page, options) => { ... }`, that returns a JSON string with
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

## Safety

Export reads a guide and writes a file. It must not save the guide or change its section tree.
The browser test is read-only: it opens HTML files that were already exported. Do not point it at
a live guide, and do not update MongoDB, the table of contents, or Message Infrastructure while
checking an export.

A structural test that needs to change a section tree uses a disposable copy created for that test.
It does not write an existing guide, including a QA clone.

## Running it

1. Export four guides from a running IGAMT and serve them from one folder as
   `rich-default.html`, `rich-all.html` (every export option enabled),
   `derived-diff.html` (a derived guide, differential export) and `base-default.html`,
   for example with `python3 -m http.server 8777`.
2. Run it in each browser engine (Playwright installed, or `PLAYWRIGHT=<path of the module>`):

```sh
node run.js chromium
node run.js firefox
node run.js webkit                 # the engine of Safari
node run.js chromium channel=chrome   # the installed Google Chrome
```

   The exit code is 0 when there is no failure.

3. Other exports, for example a data type library saved as `lib.html`, get the generic
   checks only (every "Where used" block, every field row, previews, console):

```sh
node run.js webkit pages=lib
```

4. Touch devices (emulated iPhone and iPad on WebKit, a phone and a tablet on Chromium;
   touch events, no hover, device viewport), on `rich-default.html` and `lib.html`:

```sh
node touch.js
```

## Notes for whoever changes the test

- Sections of the export render when they come near the window (`content-visibility`).
  The pointer helper `pointAt` therefore goes to an element the way a reader does, a jump to
  the section and then wheel steps, and checks with a hit test that the element is really
  under the pointer. After one long scripted jump WebKit can report an out-of-date position
  for an element, which a hit test catches.
- Firefox reports a keyboard-activated click like a mouse click (`detail` is 1), and Safari
  does not focus a button on click. The export script tracks the last input device itself.

The known-field checks expect the "Export Showcase" immunization guide (segments `PID_IZ`,
`OBX_IZ`, datatype `CX_IZ`, profile `Z22`). With other guides, keep the generic checks and
adjust that section.
