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

Open the guide and choose **Public web IG** for its preview, or **Export → Public
web IG (ZIP)** for the portable publication. It uses the user's existing default
IG export configuration. The APIs also accept a saved `config` id belonging to
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
- 12 publication tests pass, including all 16 content/link combinations, include
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
  -Dtest=NarrativeCardExportServiceTest,NarrativeCardExportControllerTest,NarrativeRequirementsPreservationTest,TextSectionRequirementsTest \
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
