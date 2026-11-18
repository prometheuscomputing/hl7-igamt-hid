# Developer starting point

## Obtain the implementation

The feature branch is `public-web-ig-on-example-messages` in `prometheuscomputing/hl7-igamt-hid`.

If the branch is available in your checkout, inspect it directly. **Do not also apply the supplied patch to that branch**: it already contains these changes. The machine-specific launcher is excluded from the bundle and removed from the current feature branch.

Otherwise, the supplied patch contains the feature changes, with two intentional exclusions:

- `scripts/run-public-web-ig.sh`, the local launcher.
- `PUBLIC-WEB-IG.md`, the previous setup document containing local-machine instructions; this handoff's README and developer notes supersede it for bundle use.

The source snapshot has the same exclusions. There is no full Git repository, application binary, dependency cache, database, or authentication setup in the bundle. Use an existing complete checkout with the compatible base revision recorded in the machine-readable manifest and normal project dependencies.

From a clean development checkout at that compatible base revision:

```sh
git switch -c review/public-web-ig-draft
git apply --check /path/to/handoff/patches/public-web-ig-draft.patch
git apply /path/to/handoff/patches/public-web-ig-draft.patch
git diff --stat
```

If integrating onto a different base, review conflicts and existing example-message behavior. Do not copy the partial `source/` folder over an unrelated checkout without comparing it first.

## Request flow and code map

Repository-relative paths below refer to files in `source/` unless stated otherwise.

1. `igamt-hl7-client-v2/src/app/modules/ig/components/ig-edit-toolbar/ig-edit-toolbar.component.*` adds **Public web IG (ZIP)**. `modules/ig/services/ig.service.ts` submits the export form.
2. `web-app/src/main/java/gov/nist/hit/hl7/igamt/web/app/utility/NarrativeCardExportController.java` handles `POST /api/export/ig/{igId}/narrative-cards`. It requires READ access to the guide and resolves the default or explicitly requested export configuration. A non-original configuration must belong to the authenticated user.
3. `export/document-export/src/main/java/gov/nist/hit/hl7/igamt/export/service/impl/NarrativeCardExportServiceImpl.java` builds the data model, walks narratives/message sections, loads registries, renders pages, packages examples, creates the search index, and writes the ZIP. Begin here for output completeness and rendering changes.
4. Existing `IgService.generateDataModel`, `ExampleMessageExportDataService`, and `ExampleMessageSerializationService` provide shared guide/example behavior. These pre-existing dependencies are in the full repository, not duplicated in the partial source snapshot. Dedicated message sections use the existing `exampleMessage.xsl`.
5. `export/document-export/src/main/resources/publication.css`, `publication-accessibility.css`, and `publication.js` control the exported site's presentation and progressive interactions. Classic document templates do not consume these new publication assets.
6. `export-configuration/.../newModel/PublicWebExportConfiguration.java` defines publisher identity, colors, logo validation, and `includeExampleLibrary`. The matching Angular model and `public-web-export-configuration` component integrate with saved IG export configurations.
7. `common/common-base/.../domain/NarrativeRequirement.java`, `ConformanceVerb.java`, and `TextSection.java` add optional structured requirements with ordering, conformance verbs, and text. The `narrative-section-form` component provides authoring. `IGDocumentController.java` and the IG edit effect carry/preserve those values. Omitted requirements are preserved; an explicit empty array clears them. Cloning preserves separate requirement identities.
8. `igamt-hl7-client-v2/src/app/modules/ig/ig-routing.module.ts` redirects the removed in-app preview routes to metadata. The controller's authenticated backend `.../preview` and `.../example?path=...` endpoints still exist. The snapshot endpoint resolves generated collector entries, not arbitrary filesystem paths.

`manifest.json` contains the exact included paths and hashes. The feature includes initial public export/requirements support, branding/search/library improvements, and removal of the in-app preview UI.

## Configuration behavior worth preserving

The toolbar does not explicitly select a configuration. It uses the user's current default IG export configuration. The backend accepts a `config` parameter for callers that intentionally select one.

`publicWebConfiguration` contains organization name, logo data and alternative text, primary/accent/link colors, footer text, and example-library inclusion. Link color validation uses contrast against white. The original built-in configuration remains read-only in the editor.

Example settings are copied before the public renderer adjusts them; enabling the example library forces collection of all managed examples in the copy. It must not mutate the saved configuration. The older Jackson version in this repository is why the feature uses explicit copies rather than assuming same-class `convertValue` yields a new object.

Snippet HIGHLIGHT mode affects HTML context. Raw snippet text downloads retain extracted lines. Cited-only exports must not link to a parent snapshot that was never collected. Retain the tests covering these distinctions.

## Build using the project's normal environment

Earlier builds used Java 8 and Node 14. Confirm your selected JDK/Node versions and the existing lockfile before building this legacy project. No workstation-specific launcher or replacement runtime setup is supplied.

From the full repository root:

```sh
cd igamt-hl7-client-v2
npm ci
npm run aot
cd ..
mvn -q package -DskipTests
```

The package command above intentionally skips tests; it is **not** verification. Run the focused tests below separately. Use `package` rather than installing these draft artifacts into a shared Maven cache. Start the resulting application using your team's normal development configuration, with your own MongoDB, authentication service, key paths, and ports. Do not assume that database names or ports from another workstation are correct.

## Test entry points

Focused backend tests, from the full repository root:

```sh
mvn -q -pl web-app -am test \
  -Dtest=NarrativeCardExportServiceTest,NarrativeCardExportControllerTest,PublicWebExportConfigurationTest,NarrativeRequirementsPreservationTest,TextSectionRequirementsTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Frontend source tests are next to `public-web-export-configuration.component.ts` and `narrative-section-form.component.ts`. The client provides `npm run test-headless`; the old Angular/Jasmine dependency compatibility issue must be resolved reproducibly in your environment before claiming those tests pass in CI. The earlier implementation notes report scratch-typing workarounds; those are not included or represented as a clean install recipe.

The retained `scripts/publication-ui-regression.html` exercises publication JavaScript when placed in the full repository layout. Use it alongside actual exported guides, not as proof that all guide content renders correctly.

The retained Python utility requires only the standard library and checks relative file and fragment targets directly inside the ZIP:

```sh
python3 source/scripts/verify-public-web-ig.py examples/sample-public-web-export.zip
```

Run that command from this handoff folder. It does not check external URLs, clinical meaning, visual layout, or complete parity with the guide's data model.

## Minimum review scenarios

- Compare source and generated profile/segment/datatype/value-set content, including contextual bindings, conditional usage, statements, descriptions, and long tables.
- Use a populated guide to cover full-message and snippet citations, dedicated message sections, library on/off, LINE/HIGHLIGHT, descriptions, relative/application links, and explicit additional-file export. Inspect both visible HTML and raw ZIP contents.
- Rename/reorder a narrative and check links; clone a section with structured requirements and check that the copies remain independent. Verify omitted versus empty requirement arrays on save.
- Test built-in versus editable configurations, default selection, invalid/oversized logos, alternative text, colors, and whether export leaves the saved configuration unchanged.
- Test empty registries, missing resources, cycles, external value sets, unsupported artifact categories, and very large guides. Decide which should generate diagnostics or block export.
- Check keyboard navigation, a screen reader, narrow windows, zoom, print, offline assets, sanitization fidelity, and rich-text diagrams.
- Generate classic HTML and Word exports after integration and compare representative outputs. New feature tests do not replace classic regression checks.

## Verification provenance

Fresh handoff verification on November 18, 2026 covers the included sample ZIP's CRC integrity and relative file/fragment links, the feature patch against its exact base using an isolated Git index, source-file hashes, and the absence of excluded launchers. See `verification/` for results.

An earlier verification record reports 28 Java tests, six branding-editor browser tests, and 22 publication-JavaScript assertions, plus sample-site checks. These are historical results, not a fresh rerun on the final draft. No full frontend/backend rebuild or full regression suite was run solely to assemble this handoff.

No application code, source-guide content, or database records were edited while preparing the bundle. The machine-specific launch script is removed from the feature branch and excluded from the distributed patch and files.
