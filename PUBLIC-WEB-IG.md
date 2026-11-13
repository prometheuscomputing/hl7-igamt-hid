# Public web IG

This additive publication runs alongside the classic HTML/Word exporter. It starts
from local `example-messages-export` at `52b83a62b35e6c910f36f8b414a41a5dddd30ee9`.
The September stash was used as a reference/three-way source; the stash is retained.

## Run locally

Build with Java 8 and Node 14:

```sh
export JAVA_HOME=/Users/abdelghani/.sdkman/candidates/java/8.0.492-zulu
export PATH="/Users/abdelghani/.nvm/versions/node/v14.20.0/bin:$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
(cd igamt-hl7-client-v2 && npm ci && npm run aot)
mvn -q package -DskipTests
scripts/run-public-web-ig.sh
```

The script defaults to port **9012**, Mongo `igamt_db` on **27018**, and local auth
on **8090**. It refuses an occupied port and never stops another app. Override
`IGAMT_PORT`, `IGAMT_DB_PORT`, `IGAMT_AUTH_URL`, `IGAMT_PUBLIC_KEY`, or `JAVA_HOME`
when needed. No database restore is required. Do not use `mvn install` for this
worktree: the parallel branch shares the Maven cache.

Open the guide and choose **Export → Public web IG (ZIP)** for the portable
publication. The in-app publication preview has been removed; previously saved
preview URLs redirect to the guide metadata page. The export uses the user's
existing default IG export configuration. The APIs also accept a saved `config` id belonging to
the authenticated user, or the built-in original configuration.

## Implementation boundaries

- `NarrativeCardExportController` authorizes read access and resolves the existing
  export configuration. It adds POST `/api/export/ig/{id}/narrative-cards`, GET
  `/narrative-cards/preview`, and GET `/narrative-cards/example?path=...`.
- `NarrativeCardExportServiceImpl` loads real IG registries and walks narrative
  sections and included MESSAGESECTION entries in depth-first order. Section and
  technical-page filenames are based on IDs, so title and position changes do not
  change their URLs. Requirements keep their IDs and `req-...` targets.
- `IgService.generateDataModel`, `ExampleMessageExportDataService.populate`, and
  `ExampleMessageSerializationService` own loading, snippet rendering, reference
  tracking, description handling and example block contents. Message-section
  fragments use the existing `exampleMessage.xsl` without modifying it.
- Snapshot HTML and text files use the existing collectors and safe naming rules.
  Card links are rebased one directory up. Uncollected parent snapshots are shown
  as names rather than broken links; only cited files are collected unless export
  all is on. Include-off retains surrounding narrative and removes message pages.
- HIGHLIGHT controls visual context in both inline and snapshot HTML. Raw snippet
  text uses extracted lines, as specified in EXAMPLE-MESSAGES.md. The public-only
  caller passes a copied LINE configuration to the text collector; the existing
  shared collector and classic exports are unchanged.
- Preview cards use the same backend walker and serializer as the ZIP. Snapshot
  preview resolves only generated collector entries; no filesystem lookup occurs.
- Public presentation assets are `publication.css`,
  `publication-accessibility.css`, and `publication.js` in document-export resources.
  Edit these for publication appearance and navigation; classic templates do not
  read them. Example configuration remains in the existing Example Messages tab.
- Requirement authoring remains below Froala and retains its M plugin. Legacy and
  TOC saves preserve omitted requirements; an explicit empty array clears them.
  Section clones receive new requirement IDs. Promotion from rich text is reviewed
  explicitly and source removal is opt-in.

Classic `ExportAndImportController`, shared example services, and classic XSLT
files have no changes in this branch. The public renderer strips active content
from narrative/technical HTML and escapes requirement text.

## Validation

- Frontend AOT build and full Maven package succeeded.
- 17 publication tests pass, including all 16 content/link combinations, include
  off/export-all, snapshot path restrictions, descriptions, missing references,
  highlighted snippets, requirement ordering, stable routes, and offline links.
- 3 publication controller tests, 2 Java section-clone tests, 2 controller preservation tests, and 4 focused
  ChromeHeadless requirement-form tests pass.
- Existing classic export trial generated HTML and a 46,642-byte Word document.
- Real local guides produced 308-entry and 561-entry ZIPs. The walkthrough adds
  three narrative/example nodes and requirements in memory only, yielding 311
  entries. No saved guide or example was changed by the generator.
- All three ZIPs passed a full local-file/anchor link scan with zero missing targets.

Tests:

```sh
mvn -q -pl web-app -am test \
  -Dtest=NarrativeCardExportServiceTest,NarrativeCardExportControllerTest,PublicWebExportConfigurationTest,NarrativeRequirementsPreservationTest,TextSectionRequirementsTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The Angular 7 test toolchain has incompatible installed Jasmine typings; focused
form tests were run using compatible scratch typings without changing dependency
manifests. The production AOT build uses the unchanged lockfile.

## Accessibility and scope

Baseline keyboard navigation, real links, skip navigation, disclosure labels,
current destination, visible focus, requirement targets, live announcements,
contrast and zoom-friendly navigation are implemented. This is not a Section 508
conformance certification; deeper accessibility work is deferred per the user.
Existing authored rich content still needs author-supplied image alternatives and
appropriate table headers. No new diagram/related-link authoring model, lifecycle
comments, version comparison or Q&A system is introduced in this port.

Snapshots are static HTML/text; live IGAMT links open the existing interactive
example view when the server and permissions are available. ZIPs can be opened
from disk; a localhost web server is not required.

## Publisher branding and modern navigation

Open **Export Configuration → Public web IG**. Create or select an editable
configuration, enter the organization name/footer, upload a logo, and choose
colors. Save and choose **Use as default** for the ZIP export.
The original configuration remains read-only. IGAMT navy `#021a4c` and yellow
`#F3C60D` are the defaults; preset palettes and a reset control are available.
Logos are packaged locally (PNG/JPEG/WebP/GIF, up to 1 MiB) with alternative text.
Link colors require 4.5:1 contrast against white. Primary/accent foregrounds are
chosen for contrast, and light primary colors use a readable heading fallback.

The public-only `publicWebConfiguration` is part of the existing saved export
configuration. Its `includeExampleLibrary` defaults to true: when examples are
enabled, every managed message/snippet appears in a searchable library with
styled HTML snapshots and ER7 text downloads. Narrative citations retain their
configured inline/snapshot/link modes. Disable the library to publish cited
snapshots without a library landing page; disable Example Messages to omit their
visible content. Classic exporters do not read these new settings.

The site includes full-text guide search (Ctrl/Cmd+K or `/`), contents filtering,
per-table filters, collapsible aligned tree tables, compact rows, copyable ER7,
and permanent links. Search includes narrative text, requirements, technical
content and examples; it works from `file://` without a server or remote assets.
Contents labels use IGAMT's name/extension and description, without truncation.
Segment/datatype tables expand linked component definitions with contextual
bindings; matching favors exact element IDs before position and guards cycles.
Field/component details start collapsed, and searches show matching descendants
with their ancestor rows. With JavaScript disabled, the complete content remains
visible. Narrative rich text and examples still come from the shared serializers.

Presentation remains separated from data loading: `publication.css` contains
components, `publication-accessibility.css` keyboard/print behavior, and
`publication.js` progressive interactions. Each ZIP includes editable
`assets/css/brand.css`, `data/branding.json`, a local logo asset when configured,
and `assets/search-index.js`. The API passes branding explicitly per request;
example configuration copies are explicit because this repository's older
Jackson may return the original object for same-class `convertValue` calls.

Additional verification:

```sh
# Test a generated ZIP's local files and fragment links:
python3 scripts/verify-public-web-ig.py /path/to/publication.zip

# Browser checks for the actual publication JavaScript (22 assertions):
python3 -m http.server 8782 --bind 127.0.0.1
# Open /scripts/publication-ui-regression.html, or open that file directly.
```

The branding editor has six focused ChromeHeadless checks, and its model has four
Java checks. Export regressions cover library enable/disable, uncited messages,
immutable saved modes, offline logo assets/search content, full resource names,
nested tree rows, cycle guards and contextual binding inheritance in addition to
the existing example-mode/security cases. These are basic accessibility and
functional checks, not a full Section 508 conformance audit.
