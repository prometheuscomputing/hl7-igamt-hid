# Public Web IG Export — Draft Developer Handoff

[Download the developer bundle](docs/public-web-ig/public-web-ig-draft.zip) · [Developer notes](docs/public-web-ig/DEVELOPER.md)

Paths in the bundle instructions below refer to the extracted handoff folder. The bundle includes an example website ZIP.

**This is a draft version to explore the web export and give developers a concrete starting point for further work.** It is a prototype for review, iteration, and integration; it is not a production release or an approved publication format.

The feature exports an IGAMT implementation guide as a portable, multi-page website with a FHIR-inspired reading experience. The content remains **HL7 v2**. This does not generate FHIR resources, run the FHIR IG Publisher, or establish FHIR conformance.

Prepared on November 18, 2026. The included example demonstrates the generated website. It is supplied for developer exploration and does not establish publication approval or content completeness.

## Start here

1. Unzip `examples/sample-public-web-export.zip`.
2. Open the extracted `igamt-public-ig/index.html` in a regular browser such as Chrome. No IGAMT server or startup script is needed to read the static site.
3. Explore the narrative sections, a message profile, its linked segments and datatypes, and a value set. Try the guide search, contents filter, table filters, and expand/collapse controls.
4. Read the limitations below before treating this as a complete replacement for the existing document export.
5. Use the feature branch or the included patch to continue development in your own IGAMT checkout.

## Bundle contents

- `README.md`: feature overview, usage, limitations, and a suggested development starting point.
- `DEVELOPER.md`: source map, integration instructions, build/test commands, and verification scope.
- `source/`: the changed implementation and test files, preserving repository-relative paths. This is a **partial source snapshot**, not a standalone application.
- `patches/public-web-ig-draft.patch`: feature changes for integration into a compatible checkout, excluding the local launcher and the superseded setup document.
- `examples/sample-public-web-export.zip`: portable example website, 7,334,886 bytes.
- `verification/`: example validation results and handoff checks.
- `manifest.json` and `SHA256SUMS`: provenance, file inventory, and checksums.

The bundle contains no local launch scripts named `run-public-web.sh` or `run-public-web-ig.sh`. The feature patch also excludes the launcher. Build outputs, dependencies, logs, credentials, and database backups are not included. The small link-checking utility and browser regression fixture are retained because they help developers validate the feature; they do not start IGAMT.

## Source and scope

- Repository: `prometheuscomputing/hl7-igamt-hid`
- Feature branch: `public-web-ig-on-example-messages`

The feature builds on existing example-message support. It introduces a separate **Export → Public web IG (ZIP)** action. The classic Word and HTML export actions remain available. The new renderer reuses existing example-message serialization rather than defining a separate message model.

The branch also contains structured narrative-requirement authoring, branding configuration, and persistence changes. Developers should review these changes as part of the feature; this is more than a stylesheet change.

## What the draft provides

- A home page and persistent table of contents, with links between narrative and technical pages.
- Narrative hierarchy and section numbers, Froala content, structured requirements, embedded message/snippet references, and dedicated message sections when enabled.
- Conformance-profile structures, segment fields, datatype components, value-set codes, and supported bindings, predicates, conformance statements, and pre/post descriptions.
- Search across generated content, contents filtering, table filtering, expandable tree rows, compact rows, and copyable links. Renaming a source item does not by itself change its generated page path.
- Optional example-message library, HTML snapshots, ER7 text downloads, and copy controls when the guide and configuration supply them.
- Publisher name, logo, footer, and colors. A logo is packaged locally; supported formats are PNG, JPEG, WebP, and GIF, up to 1 MiB.
- Static HTML/CSS/JavaScript and a local search index. Core navigation works without an application backend.

## How an author creates an export

Use an IGAMT build containing this feature. Open the guide and choose **Export → Public web IG (ZIP)**, then unzip the download and open `index.html`.

The toolbar export uses the user's **default IG export configuration**; it does not present a separate configuration picker. To change the result, open an editable IG export configuration, use its **Public web IG** tab for branding and example-library settings, save, and choose **Use as default**. Built-in configurations are read-only.

The existing **Example Messages** tab controls descriptions, inline content, snapshots, relative/application links, snippet presentation, and additional files. The Public web IG tab supplements those settings. It is not a substitute for reviewing them.

The example library defaults to enabled. If examples are enabled, enabling the library includes all managed messages and snippets, including those not cited in narrative text. Turning the library off does not disable ordinary narrative citations. Turning example inclusion off suppresses visible example content; however, an explicit **export all/additional files** setting can still retain raw text files. This behavior is covered by a source test and deserves a clearer UI before release.

The in-app publication-preview UI is not included in the current draft. Use the downloaded website for review. Backend preview-related routes still exist; removal of the UI did not remove those endpoints.

## Example export

`examples/sample-public-web-export.zip` contains a generated website for exploring the navigation, narrative pages, technical tables, and terminology display. Extract the complete ZIP and open `igamt-public-ig/index.html`; keep its folder structure intact.

Archive integrity and relative file/fragment links were checked successfully. These checks do not establish semantic completeness or visual fidelity. This sample does not demonstrate every feature or configuration combination. To evaluate example-message libraries and snapshots, also test a development guide containing managed messages and snippets.

## Limitations and work still needed

### Content completeness

The renderer explicitly loads conformance profiles, segments, datatypes, and value sets. It does not provide dedicated publication pages for every IGAMT artifact category. Composite profiles, profile components, co-constraint groups, and slicing need an explicit coverage review and appropriate rendering before anyone claims full guide parity.

Technical resources are loaded from guide registries; this is not the classic document export's complete selection/layout pipeline. Registry entries whose resources cannot be loaded are skipped. Add actionable export diagnostics so missing source resources cannot be confused with an intentionally small guide.

The current home-page builder presents the title, counts, and navigation. Source subtitle/release/version and publication-status metadata are not comprehensively represented there.

Value-set pages display the code collection present in the source object. They do not perform a fresh terminology-server expansion or automatically resolve external/dynamic terminology. Review empty and externally maintained sets, version details, comments, and ordering against the intended publication requirements.

### Static output and presentation

An export is a snapshot. Editing the source guide requires a new export. The site does not validate messages, edit the source, manage approvals, compare releases, or provide a discussion workflow. Generated links remain stable for the same source resources; cloned/rebuilt guides and different hosting locations can have different URLs.

Author-supplied external links and remote images can still need network access. Links back into IGAMT require a reachable application and appropriate access. The generated site's own assets are local, but this is not a guarantee that every authored resource is self-contained.

Rich text is sanitized. Embedded active content, forms, SVG/MathML, and some styling are removed. Review authored diagrams, equations, formatting, and tables for fidelity. Do not assume the web page reproduces every Froala detail.

The palette and layout are exploratory and configurable. Existing keyboard, focus, contrast, and responsive behavior do not constitute a completed Section 508/WCAG audit. Screen-reader behavior, authored image alternatives, complex tables, mobile use, and print output need further review.

### Engineering and release readiness

The exporter builds the archive and retains generated page content in memory; there is no measured production-scale capacity or concurrency target. Large guides, repeated navigation markup, deep component trees, and large search indexes need performance testing.

The project uses a legacy Angular 7 / Java 8-era toolchain. Earlier verification recorded frontend test typing compatibility workarounds. Fresh dependency installation, CI reproducibility, browser coverage, and a full regression run on the developer's integration branch remain required.

One embedded-browser download attempt in this session aborted with a server-side “Broken pipe”; the same export downloaded successfully in regular Chrome. Treat that as an observed delivery-path limitation, not a proven content-generation defect.

The exporter checks source read access, but the downloaded static files do not retain IGAMT authentication. There is no deployment or publishing workflow in this handoff. Review content and use the intended hosting/access model when sharing an export.

## Suggested developer starting point

First reproduce the included site's navigation and generate a new ZIP from a known development guide. Then agree on required artifact coverage and publication metadata, verify configuration behavior, and add diagnostics for omitted content. Use representative guides to compare source versus output, especially bindings, predicates, examples, snippets, external value sets, and currently unsupported artifact types.

Keep this labeled as a draft while improving the UI, data completeness, performance, and accessibility. Re-run classic Word/HTML export checks alongside the new web-export tests before proposing integration.
