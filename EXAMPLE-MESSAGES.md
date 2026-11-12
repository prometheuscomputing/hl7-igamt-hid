# Example messages and their export

This describes the example-message feature as it is built on `example-messages-export` (`4e12274c7`, same commit as `example-messages-2026-04-15`). The export and its configuration screen may be replaced. How a person points the guide at a message is in "How to link a message or a snippet". Everything the export writes, including the configuration, the files, and the HTML, is in "Export". A new design can add, drop, or combine options, as long as those considerations stay true.

## Message, snippet, and metadata

Stored in Mongo as one `IgExampleMessages` document per guide. The document id is the guide id. `exampleMessages` is the list of messages. This is not part of the conformance profile.

`ExampleMessage`:

| Field | Meaning |
| --- | --- |
| `id` | Stable id. Guide sections and Froala widgets store this, not the name. |
| `name` | Display name. Also the basis of the snapshot file name. Unique among messages of the same profile, case-insensitive. |
| `profileId` | Conformance profile or composite profile this message belongs to. |
| `message` | The ER7 text. This is the content that is parsed, validated, snapshotted, and inlined. |
| `description` | Metadata-tab rich text. This is what "Include the description from the metadata tab" exports. |
| `narrativeHTML` | A separate HTML string on the record. The current message screen does not edit it. Save round-trips whatever was already stored. Export does not print it. |
| `snippets` | The snippet list. |

`MessageSnippet`:

| Field | Meaning |
| --- | --- |
| `id` | Stable id. A section's `snippetId` and a widget's `data-snippet-id` store this. |
| `name` | Display name. Unique among snippets of the same message. Used in the snapshot file name as `{message} - {snippet}`. |
| `description` | Metadata-tab rich text for this snippet. When a reference names this snippet, export prints this description instead of the parent message description. |
| `messageReferences` | Paths of the selected nodes in the parent message tree (`positionalPath` values). This is how the snippet is cut out of the parent. It is not a pointer from the guide. |
| `narrativeHTML` | Same situation as on the message: stored, not edited by the current snippet screen, not exported. |

The metadata screen, for both, is a Name field plus a Description Froala editor. That editor does not have the **M** button. Name and description are the metadata. The ER7 and the tree are the Content tab, not metadata.

At export, `SnippetRenderResult` is computed and not stored. It carries `fullMessage`, `renderedContent` (the extracted lines), `parts` (each selected location, with 1-based lines and an exclusive end column), `warnings`, and `snippetNotFound`. `LINE` uses `renderedContent`. `HIGHLIGHT` uses `fullMessage` plus `parts`.

Guide pointers, which are not fields of the message:

| Pointer | Where it is stored | What export does |
| --- | --- | --- |
| Message section | `TextSection.type = MESSAGESECTION`, `messageId`, optional `snippetId`. `description` is HTML on the section. The message-section screen does not edit it, and **Add Message Example** stores an empty string. | If `description` is non-empty it is printed, then one generated block for the ids. The heading is the section title, unless that title is empty or `Message Example`, in which case it becomes `Message Example: {message} - {snippet}`. |
| Froala widget | `div.fr-exmsg` inside some HTML field (`data-message-id`, `data-snippet-id`). | The div is replaced. The surrounding HTML of that field is kept. |
| Usage record | Computed, not stored. `kind` is `MESSAGE_SECTION` or `NARRATIVE`. `sectionId`, `sectionType`, `path` (positions like `1.2`), `label`. | Delete and detach use this. Pre-definition and post-definition widgets are not included. |

## How to link a message or a snippet

A guide does not contain the ER7. It stores a pointer, and the export later reads the live message through that pointer. There are two pointers, and they are created in different places. Both can point at a whole message or at one snippet of that message. Both can exist in the same guide. A narrative section can also contain several widgets. One message section holds one pointer.

The ids are what is linked. The name is not. Renaming the message does not break the pointer. It does change the snapshot file name, which is an export concern and is described in Export.

Nothing here creates the example. You create the message first, then you link it.

### Create the message before you link it

On the implementation guide, the title bar has a button labeled **Example Messages**. It routes to `/example-messages/{igId}`. That screen is the store. The guide table of contents is not.

Messages are grouped under a conformance profile or a composite profile. **Create Example Message** opens a dialog titled "Create Example Message". The name must be unique among messages of that profile, compared without case. The new record gets a new id, the chosen `profileId`, and an empty ER7 until you type one on the Content tab.

The message editor is `/example-messages/{igId}/message/{messageId}`. Content is the ER7 and the parsed tree. Metadata is the name and the description. The description is the rich text that export can quote. That editor has no **M** button, so the description cannot contain another example widget.

A snippet is created on the message, not in the guide. On the Content tab, **Create Snippet** (the button above the tree, or the same command on a tree node's context menu) is enabled when the tree is not stale. The dialog asks for a name, unique among snippets of that message. The saved `messageReferences` are the positional paths of the nodes you selected. The snippet editor is `/example-messages/{igId}/message/{messageId}/snippet/{snippetId}`. A snippet has no ER7 of its own.

Copy Example Message and Copy Snippet make a new id. Links keep pointing at the original id. The copy is not linked until you attach it or insert it.

Open IG on that screen returns to the guide. The guide does not yet point at the message until you do one of the two things below.

### What you are choosing in the picker

Both link actions open the same dialog, `Select Example Message or Snippet`.

- Title: "Select Example Message or Snippet".
- The list is built from `GET /api/example-messages/{igId}`. Messages are grouped by profile. The profile label is `fixedName`, plus `#` and `variableName` when the profile has one. A profile with no display name is "Unknown Profile".
- A profile row is not selectable. Clicking it does nothing.
- A message row shows a blue **M** and the message name. Selecting it highlights the row `#bbdefb`.
- A snippet row is indented under its message, with a purple scissors icon and the snippet name. Selecting it highlights the row `#e1bee7`.
- Empty guide: "No example messages available." Confirm stays disabled.
- Cancel closes the dialog and changes nothing.
- Confirm is disabled until a message or a snippet is selected. The button label depends on who opened the dialog: **Attach** for a message section, **Insert** for Froala.

The result is three fields:

| Field | Message row | Snippet row |
| --- | --- | --- |
| `messageId` | that message's id | the parent message's id |
| `snippetId` | null | that snippet's id |
| `label` | the message name | the snippet name only, not "message - snippet" |

The dialog does not write anything by itself. The screen that opened it writes the result.

### Message section: a child of a narrative section

This is the structured link. The pointer is fields on a table-of-contents node, not HTML inside a paragraph.

Where the command is:

- Open the guide at `/ig/{igId}`.
- In the table of contents, right-click a narrative section. Those nodes have type `TEXT`.
- The menu items are **Add Section**, **Add Message Example** (a bold **M**), **Copy Section and Paste to the End**, and **Delete Section**.
- The menu is disabled when the guide is view-only or when it is a differential (`delta`).

**Add Message Example** does not open the picker. It appends a child immediately:

| Field on the new node | Value |
| --- | --- |
| `id` | a new GUID |
| `type` | `MESSAGESECTION` |
| `variableName` | `Message Example` |
| `description` | empty string |
| `messageId` | null |
| `snippetId` | null |
| `children` | empty |
| `leaf` | true |

The child shows in the table of contents with a blue **M** (`#2196F3`), then the section path and the label. Its DOM id is `TOC-MESSAGESECTION-{sectionId}`. Clicking it goes to `/ig/{igId}/messagesection/{sectionId}`.

You cannot add a message example from a message-section node. That node's menu has only **Delete Message Example**. You cannot add one from a profile, a segment, or any other registry node. The datatype library table of contents does not have this command at all. A message section is always created as a child of an IG narrative section.

The new node is not saved to the server until the table of contents is saved. Leaving an editor that has `saveTableOfContent: true` does that, which includes this editor.

#### The message-section screen

The screen is the title, then a box titled **Attached Example Message**, then a read-only view of the attached ER7. There is no Froala editor on this screen. You cannot type a narrative here, and you cannot press **M** here. `description` stays whatever was stored. A section created by **Add Message Example** has an empty `description`, so export prints no section narrative for it. If some other write put HTML into `description`, export still prints that HTML. That is not a path the current screen offers.

**Title.** Required. The input placeholder is "Section title". An empty title shows "Title is required." in red and the editor is not valid, so it will not save. View-only shows the title as text and hides the input.

The default title is `Message Example`. Export treats that exact text, compared without case, as a placeholder. An empty or whitespace title is treated the same way. In those cases the guide heading becomes `Message Example: ` plus the live label (`{message name}`, or `{message name} - {snippet name}` when the snippet still exists). Any other title is used as the heading, unchanged. Typing "VXU example" keeps "VXU example" even if the attached message is renamed later. Leaving the placeholder means the heading follows the message name.

**Nothing attached.** A green box says: "No example message is currently attached to this section. Click **Attach** to select one." While the message list is loading, a spinner says "Loading available messages...".

**Attach.** Shown when the guide is editable. The label is **Attach** when `messageId` is empty and **Change** when one is set. It opens the picker. Confirm writes `messageId` and `snippetId` onto the section through the editor dirty state. Cancel leaves the previous ids. The write is not on the server until the section is saved. Leaving this editor saves it (`onLeave.saveEditor` and `saveTableOfContent` are both true). Save calls `saveTextSection` with the section, including `label`, `messageId`, and `snippetId`.

**Detach on this screen.** Shown only when a message is attached and the guide is editable. It asks "Are you sure you want to detach the example message from this section?" with the action **Detach**. Confirm sets both `messageId` and `snippetId` to null and clears the preview. The section stays in the table of contents. This is not the delete-usage dialog. It does not look for other references. It only clears this section.

**Delete Message Example** on the table-of-contents menu removes the section node. The example message itself is not deleted. If that section was the only guide reference, the example can then be deleted from Example Messages. Detach keeps the section. Delete Message Example removes the section.

#### What the screen shows after a message is attached

The attached card is an indigo box (`#e8eaf6`, border `#c5cae9`):

- A blue **M**, the message name, and the profile in parentheses: `fixedName` or `fixedName#variableName`.
- If a snippet is attached and the render returned a name: a purple scissors icon, the snippet name, and the location in parentheses.

The location is built from the render parts. Each part prefers its HL7 path, otherwise `L{start}` or `L{start}-L{end}`. The card joins those with commas. The longer form used beside the URL is the path plus the line, for example `PID-3 (line 4)`, or `Line 4-6` when there is no HL7 path.

Under the card, **Message URL** is a read-only monospace field:

- Whole message: `{protocol}//{host}/example-messages/{igId}/message/{messageId}`
- Snippet: the same URL plus `?snippetId={snippetId}`

`host` is the browser's current host, not `host.url` from the server configuration. The field border is purple (`#6a1b9a`) when a snippet is selected and gray (`#ccc`) otherwise. When a snippet location exists, the label reads `Message URL — {location}`.

**Copy URL** is shown only when `navigator.clipboard` exists. It writes that exact string and the button says "Copied!" for 1.5 seconds.

**Open** is a router link to `/example-messages/{igId}/message/{messageId}`, with query `snippetId` when one is selected. It opens the example inside IGAMT. It is not the export link. The export IGAMT link, when the configuration asks for one, uses `/snippet/{snippetId}` on the path instead of the query parameter, and it uses the server's `host.url`.

Below that, the ER7 is a read-only CodeMirror editor (`mode: hl7v2`, line numbers, no cursor). A snippet starts in **Snippet Only**, which shows `renderedContent`. **Full Message** shows `fullMessage`. Those two buttons are only on this screen, and only when a snippet id is set and the render succeeded. They are not saved. Export does not read them. Export uses the configuration's line-or-highlight setting.

Highlights use the CodeMirror class `cm-snippet-highlight` on each part. In Full Message the line numbers are the part's original lines. In Snippet Only they are the lines of the extracted text. Columns on the wire are 1-based; CodeMirror wants 0-based, and the screen subtracts one.

**Message Not Found.** If `GET` of the message fails, an orange box says "The attached example message could not be found. It may have been deleted." The ids remain on the section until someone detaches or changes them. Export of a missing message is the missing block described in Export.

**Snippet warnings.** The render result's `warnings` are listed in a yellow box. If the snippet id no longer exists, `snippetNotFound` clears the snippet name and the URL drops the query parameter, and the full message is shown. The section's `snippetId` is still stored until you change it. If the render call itself fails, the warning is "The message may have changed since this snippet was created. The snippet could not be highlighted."

If the message record has a non-empty `narrativeHTML`, it is shown under the ER7 as read-only HTML, labeled "Narrative:". The current message editor does not edit `narrativeHTML`. Export does not print it.

A message section does not get a second pointer. **Change** replaces `messageId` and `snippetId`. To point at two examples, add two message sections, or insert widgets in a narrative section.

### Froala widget: a pointer inside rich text

This is the inline link. The pointer is a `div.fr-exmsg` in the HTML of a field that has the **M** button. It does not create a `MESSAGESECTION`. It does not set `messageId` on the section. The ids live only in the div.

Where **M** is on the toolbar, after the link button:

| Screen | Route or place | HTML field that receives the div |
| --- | --- | --- |
| IG narrative section | `/ig/{igId}/text/{sectionId}` | that section's `description` |
| Datatype-library narrative section | the library section editor | that library section's `description` |
| Segment, datatype, value set, or conformance profile definition | the resource editor | pre-definition, and separately post-definition |
| Profile component | the profile-component metadata editor | pre-definition, and separately post-definition |

**M** is not on the message-section screen, not on example-message or snippet metadata, not on definition text in the structure table, not on author notes or usage notes, not on comments, and not in view-only. The full list is in "What Froala supports, and what it does not".

A widget in a library narrative is stored on the library document. Guide export rewrites widgets while it serializes the implementation guide. It does not read the library document. A widget in a segment's pre-definition is rewritten when that segment is part of the guide being exported. A widget in pre-definition or post-definition is also invisible to usage search, so it does not block deleting the message.

#### Insert

1. Click in the narrative where the example should appear.
2. Press **M**. The toolbar title is "Insert Example Messages".
3. The editor saves the caret, then opens the picker. The confirm button says **Insert**.
4. Choose a message or a snippet and press **Insert**. Cancel restores the caret and inserts nothing.
5. The editor inserts the widget HTML, then an empty paragraph, and moves the caret into that paragraph. Another empty paragraph is added before the widget when the widget would otherwise be the first thing or would sit against another widget. That is so the caret can stand outside the widget.

The inserted HTML is:

```html
<div class="fr-exmsg fr-deletable" contenteditable="false"
     data-message-id="{messageId}"
     data-snippet-id="{snippetId or empty}"
     data-view="reduced|full"
     data-label="{picker label}">
  <a class="fr-exmsg-link" href="/example-messages/{igId}/message/{messageId}?snippetId={snippetId}">Example: {label}</a>
  <pre class="fr-exmsg-body">Loading…</pre>
</div>
```

`data-view` is `reduced` when a snippet was chosen and `full` when the whole message was chosen. `data-snippet-id` is an empty attribute for a whole message, not the word null. The caption is `Example: ` plus the picker label, so a snippet widget is captioned with the snippet name. The href uses a query parameter. A plain click on that anchor is cancelled so the widget can be selected. Ctrl-click or Command-click follows the href and opens the message in IGAMT.

The preview then replaces `Loading…`:

- Whole message: `GET` the message and show `message`.
- Snippet, `data-view` not `full`: `GET .../render` and show `renderedContent`, with the selected ranges highlighted.
- Snippet, `data-view` `full`: the same render, showing `fullMessage`, with highlights on the original lines.
- Render says the snippet is gone: the body becomes the full message, or the text "The attached snippet could not be found."
- The message cannot be loaded: "Example message could not be loaded." or "The attached example message could not be found."

Those preview strings are editor text. Export throws the preview away and builds the block again. Do not treat the saved `<pre>` as the exported body.

#### While you are editing the widget

The widget is `contenteditable="false"` and `fr-deletable`. Clicking it selects it. Arrow keys move the caret around it.

The trash control is titled "Remove Example Message". It is added in the editor and stripped from the HTML when Froala reads the content back (`html.processGet` removes `button.fr-exmsg-remove` and `div.fr-exmsg-views`). The saved HTML does not contain the trash button or the view bar. It does still contain `data-view`.

When `data-snippet-id` is non-empty, the editor adds **Snippet Only** and **Full Message**. Clicking one sets `data-view` to `reduced` or `full` and re-renders. That switch is the author's preview. Export ignores `data-view` and uses the export configuration.

You can insert any number of widgets in the same field, with ordinary paragraphs, lists, tables, images, and links between them. Each widget has its own message id and optional snippet id.

Removing the widget deletes that div. The surrounding narrative stays. There is no detach confirmation on the trash button. The usage dialog's **Detach** for kind `NARRATIVE` is the other direction: from the example-message delete flow, it removes matching divs from `section.description`. It only searches guide table-of-contents sections. It does not search pre-definition or post-definition, and it does not search a datatype library.

#### What is saved

On a narrative section, save writes the section, and the widget is inside `description`. On a resource, save writes the pre-definition or the post-definition that holds the div. The stored div is the pointer. The ER7 inside the `<pre>` is a cache of the last preview. The next time the editor opens, `renderAll` replaces it. Export replaces it too.

A pasted `div.fr-exmsg` is a link even on a screen that has no **M** button. Export rewrites any such div it finds in the guide. The missing button only stops the toolbar from inserting one.

### The two pointers side by side

| | Message section | Froala widget |
| --- | --- | --- |
| You create it by | **Add Message Example** on a `TEXT` section, then **Attach** | **M** in a rich-text field, then **Insert** |
| Stored on | `TextSection.messageId` and `TextSection.snippetId` | `data-message-id` and `data-snippet-id` on `div.fr-exmsg` |
| How many examples | one | as many divs as you insert |
| Section title | the section label, or `Message Example: {live label}` when the label is still the placeholder | the narrative section's own title; the widget is inside the body |
| Narrative around it | not edited on this screen; `description` is usually empty | whatever you typed before and after the div |
| Editor preview | CodeMirror, Snippet Only / Full Message | the widget body, same two views, stored as `data-view` |
| URL you can copy on the section | `?snippetId=` on the browser host | the anchor href, same query shape, path only |
| In the guide export | one generated block after the section HTML | each div rebuilt in place |
| If example messages are excluded | the whole section is omitted | the div is removed and the surrounding HTML stays |
| Detach from the example's delete dialog | kind `MESSAGE_SECTION` clears the ids; for a snippet it clears only `snippetId` | kind `NARRATIVE` removes the matching divs from `section.description` |
| Detach on the section screen | clears both ids and keeps the section | no equivalent; the trash button removes the div |
| Blocks delete of the example | yes, this section is a usage | yes, when the div is in a guide section's `description`; no, when the div is in pre-definition, post-definition, or a library |
| Several links to the same example | several message sections | several widgets, including inside one narrative |

A narrative section can hold widgets and also have message-section children. Those are separate. Export emits the narrative, with its widgets rewritten, and then emits each child message section with its own generated block. They are not merged.

### URLs, and which one is which

People use the word "link" for four different strings. They are not interchangeable.

| URL | Shape | Who writes it | When it works |
| --- | --- | --- | --- |
| Editor, whole message | `/example-messages/{igId}/message/{messageId}` | the widget href, the **Open** button, and the message-section URL field (the field also adds the browser origin) | IGAMT is open and the message still exists. A snippet query is optional. |
| Editor, snippet | the message URL plus `?snippetId={snippetId}` | the same screens | The message editor reads `snippetId` from the query and expands the snippet sidebar. |
| Snippet editor | `/example-messages/{igId}/message/{messageId}/snippet/{snippetId}` | the example-messages table of contents, and the exported IGAMT link | IGAMT route. The widget does not use this shape. The message-section **Open** button does not use this shape either; it uses the query. |
| Export, relative snapshot | `{folder}/{profile}/{file}.html` relative to the guide `index.html` | export, and only when the relative-link option is on and the snapshot files are actually produced | The HTML folder is intact. It does not include the browser origin, and the path is not shown as text. |
| Export, IGAMT | `{host.url}/example-messages/{igId}/message/{messageId}` or the same plus `/snippet/{snippetId}` | export, and only when the IGAMT-link option is on | That server and that guide are still there. `host.url` defaults to `http://localhost:9000`. The visible text is "Open in IGAMT", not the message name. |

The message name becomes a link only in the export, and only when the relative-link option is on and a snapshot page was assigned. Inside IGAMT, the name is text. The way you open the message from the guide editor is **Open**, **Copy URL**, or the widget anchor with Ctrl-click or Command-click.

Copy URL copies the editor URL. It does not copy a snapshot path, because the snapshot path does not exist until export, and it depends on the folder name and the message name at that moment.

### What happens to the link later

- Rename the message or the snippet. The ids still match. The editor caption (`data-label`) and the saved preview can be stale until the narrative is opened again. Export uses the current name.
- Edit the ER7. The next open and the next export render the snippet again. A reference that no longer resolves warns in the message editor after save, warns on the message-section screen, and at export falls back to the full message.
- Delete the message while a link remains. The delete dialog refuses. Detach first, or delete the message sections and the narrative widgets it lists. Pre-definition and post-definition widgets are not in that list. After a delete that those widgets did not block, export shows the missing text for them.
- Delete the snippet. A message section keeps `snippetId` until you change it. The screen drops the snippet from the URL and shows the full message. A widget keeps `data-snippet-id`. Export drops the Snippet row, warns, and shows the full message when the body is included.
- Exclude example messages from the export. Message sections disappear, including their titles. Widgets disappear from the narrative and the paragraphs around them stay. The links inside IGAMT are untouched, because exclude is an export flag.

The HTML each of those choices writes, the snapshot files, and the configuration that decides them are in Export. This section is only how the pointer gets into the guide.

## What Froala supports, and what it does not

Froala is the rich-text editor. Example messages are one optional plugin on top of it. The plugin is registered when the editor service is constructed, but it is not on the toolbar unless the screen asks for it.

`FroalaService.getConfig(includeExampleMessages)` defaults the flag to false. False removes `insertExampleMessage` from every toolbar size and removes `exampleMessage` from `pluginsEnabled`. True adds the **M** button after the link button and enables the plugin. On init, only the true path calls `renderAll()`, which fills each widget with the current ER7 preview.

### Screens where the M button is supported

The button inserts a widget into the HTML of that editor. It does not create a message section.

| Screen | Call |
| --- | --- |
| IG narrative section | `getConfig(true)` in `ig/.../narrative-section-form.component.ts` |
| Datatype-library narrative section | `getConfig(true)` in `library/.../narrative-section-form.component.ts` |
| Pre-definition and post-definition of a segment, datatype, value set, or conformance profile | `getConfig(true)` in `DefinitionEditorComponent`, which those editors extend |
| Profile-component pre-definition and post-definition | `getConfig(true)` in `profile-component-metadata.component.ts`. That component's Froala fields are the pre-definition and the post-definition. |

On those screens the author can:

- Open the picker and insert either a whole message or one snippet.
- See a live preview of the ER7, with snippet lines highlighted.
- Switch a snippet widget between Snippet Only and Full Message. That writes `data-view` and is not an export setting.
- Remove the widget.
- Put several widgets in the same narrative, between normal Froala paragraphs, lists, tables, images, and links.

The rest of Froala stays available on these screens: the normal toolbar, images (`jpeg`, `jpg`, `png`, `gif`), file upload (`pdf`, `msword`, `x-pdf`, `text/plain`, `xml`), image resize, and paste as plain text (`pastePlain: true`). The character counter is off, quick insert is off, and the editor height is 300. Uploaded images are stored through `api/storage/upload` and removed through `api/storage/file` when the image is deleted in the editor.

The saved widget is only an identifier plus a preview:

```html
<div class="fr-exmsg fr-deletable" contenteditable="false"
     data-message-id="..." data-snippet-id="..."
     data-view="reduced|full" data-label="...">
  <a class="fr-exmsg-link" href="/example-messages/{igId}/message/{messageId}?snippetId=...">Example: {label}</a>
  <pre class="fr-exmsg-body">...</pre>
</div>
```

The remove button and the view bar are stripped before the HTML is saved. The href in the editor uses a query parameter (`?snippetId=`). The exported IGAMT link uses a path (`/snippet/{snippetId}`). Those are different URLs.

### Screens where the M button is not supported

These still use Froala, with the ordinary toolbar, unless noted. They call `getConfig()` with no argument, so the button and the plugin are absent. Do not turn the button on here unless the product decision changes. The metadata description in particular must stay free of the button: that field is the description that export quotes, not a place to nest another example.

| Screen | Notes |
| --- | --- |
| Example-message metadata description | Metadata tab. Edits `description`. No M button. |
| Snippet metadata description | Same. |
| Definition text | `TextEditorDialog` opened from the structure table. Froala, no M button. |
| Author notes and usage notes | `ResourceMetadataEditorComponent` and the other metadata editors. Froala, no M button. |
| IG, library, conformance-profile, and composite-profile metadata editors | Froala, no M button. Some of those components assign a Froala config the template never uses. |
| Workspace home | Froala, no M button. |
| Documentation | `getDocumentationConfig()`, which also strips the plugin. |
| Comments | `TextEditorDialog` with `plain: true`. Not Froala at all. |
| View-only resources | The editor is not mounted. Existing HTML is shown with `froalaView` or `innerHTML`. A view-only HL7 segment (for example MSH) therefore has no toolbar. |

`narrativeHTML` on the message and on the snippet is not a Froala field in the current UI.

The export rewriter does not check which screen saved the HTML. If a `div.fr-exmsg` is present in any serialized attribute or text node, it is rewritten. The button being absent is what stops people from inserting one. It does not stop a pasted widget from being exported.

### What the plugin does not do

- It does not edit the ER7. The message text is edited on the example-message Content tab.
- It does not create or delete messages or snippets.
- It does not create a `MESSAGESECTION`.
- It does not store a durable copy of the ER7 inside the narrative. The preview is replaced at the next open and again at export.
- It does not apply the export configuration. Snippet line versus highlight, snapshot, and IGAMT versus relative are export settings. The editor switch is only `data-view`.
- The blue frame, the remove button, and the view switch are editor chrome. They are not part of the exported guide.

## Where the code lives

| Piece | Path |
| --- | --- |
| Configuration screen | `igamt-hl7-client-v2/src/app/modules/export-configuration/components/example-message-export-configuration/` |
| Tab host | `igamt-hl7-client-v2/src/app/modules/export-configuration/components/default-configuration/default-configuration.component.html` |
| Stored model | `export-configuration/src/main/java/gov/nist/hit/hl7/igamt/export/configuration/newModel/ExampleMessageExportConfiguration.java` |
| Content enum | `ExampleMessageExportMode.java` in the same package |
| Link enum | `ExampleMessageLinkTarget.java` in the same package. Git ignores `*target*`, so this file was added with `git add -f`. A plain `git add` will not see later edits. |
| Snippet enum | `ExampleMessageSnippetExportMode.java` |
| Parent object | `IgGeneralConfiguration.exampleMessages`. `getExampleMessages()` never returns null; a missing value becomes a new configuration with the Java defaults. |
| Files and pages | `ig/src/main/java/gov/nist/hit/hl7/igamt/examples/service/ExampleMessageExportDataService.java` |
| Reference tracking | `ig/src/main/java/gov/nist/hit/hl7/igamt/ig/domain/datamodel/ExampleMessageDataModel.java` |
| Guide HTML and Word body | `serialization/.../ExampleMessageSerializationServiceImpl.java` |
| Message sections | `serialization/.../SectionSerializationServiceImpl.java` (`SerializeMessageSection`) |
| Widget walk | `serialization/.../IgDataModelSerializationServiceImpl.java` (`rewriteExampleMessageWidgets`) |
| Message-section XSL | `export/document-export/src/main/resources/templates/profile/conformanceProfile/exampleMessage.xsl` |
| Export CSS | `export/document-export/src/main/resources/templates/style/globalStyle.xsl` |
| Guide download | `web-app/.../ExportAndImportController.java` `POST /api/export/ig/{igId}/{format}` |
| Example-messages downloads | `web-app/.../ExampleMessagesController.java` |
| Editor widget | `igamt-hl7-client-v2/src/app/modules/shared/froala/example-message.plugin.ts` |
| M button | `igamt-hl7-client-v2/src/app/modules/shared/services/froala.service.ts` `getConfig(includeExampleMessages = false)` |

The editor widget and the exported block are different. The editor keeps a blue frame, a remove button, and a Snippet Only / Full Message switch. The export throws that chrome away and rebuilds the block from the configuration.

## Current behavior worth knowing before you replace it

The configuration the user saved is what a guide export uses. Export all from Example Messages uses the default IG configuration and then forces a full snapshot folder. Checking the relative link also turns the snapshot on. Turning the snapshot off clears the relative link. Both links at once means the name points at the snapshot and a separate row points at IGAMT. The path is the address, not the visible text. A missing `includeDescription` still includes the description. Context-menu snippet HTML is always the highlighted full message, even when the guide configuration says line only.

Routes in the application:

- `/example-messages/{igId}/message/{messageId}`
- `/example-messages/{igId}/message/{messageId}/snippet/{snippetId}`

Delete lists guide usages and will not delete while any remain. Detach asks for confirmation because it cannot be undone. Copy assigns a unique name. The message editor parses the ER7, runs context-free validation, and can open the profile element that a segment or field corresponds to. Open IG returns to the guide.

## Delete dialog, exactly

Title: `Delete {name}`. While usages load, the text is `Looking for references in the guide…`. If the list is non-empty: `This example message is used in the guide. Detach the references, then delete it. Detach cannot be undone.` For a snippet the noun is `snippet`. If the list is empty: `Nothing in the guide references this example message.`

Each row is `{path} {label}` as a link to the section, a second line for the kind, and a Detach button. Detach on one row asks for confirmation, then `POST` detach with that `sectionId` and `kind`. Detach all confirms once and detaches every row. Delete stays disabled while `usages.length > 0` or while a request is in flight. Cancel closes the dialog and changes nothing.

`kind` `MESSAGE_SECTION` on a message clears both `messageId` and `snippetId` on that section. On a snippet it clears only `snippetId`, so the section remains a message section pointing at the parent message. `kind` `NARRATIVE` removes matching `div.fr-exmsg` widgets from `section.description` and leaves the rest of the HTML. A widget matches when `data-message-id` equals the message. For a snippet detach, `data-snippet-id` must also equal the snippet. A message detach removes every widget of that message, including ones that name a snippet.

## Froala plugin, step by step

`insertExampleMessage` is the command. The icon is a badge whose text is `M`. The toolbar title is `Insert Example Messages`. It is inserted after `insertLink` on each toolbar size that has a button list. If `insertLink` is absent, it is appended.

The picker is `FroalaExampleMessageService.pickAndInsert`. The author chooses a message, or a message and a snippet. The inserted HTML is `buildExampleMessageWidgetHtml`. `data-view` is `reduced` when a snippet id was chosen, otherwise `full`. The caption is `Example: {label}`. The body starts as `Loading…`. `renderAll` replaces the body with colored ER7 and, for a snippet, highlight ranges. `ensureViewToggle` adds Snippet Only and Full Message only when `data-snippet-id` is non-empty. `ensureRemoveButton` adds the trash control. Both of those controls are removed from the HTML that gets saved.

Clicking the widget selects it. Arrow keys move the caret around it instead of into it. The widget is `contenteditable="false"` and `fr-deletable`.

ER7 coloring inside the editor preview, not in the export:

| Token | Style |
| --- | --- |
| segment name | bold black |
| field separator | `#0707f1` |
| component separator | `#ac2200` |
| subcomponent separator | `rgb(0, 146, 0)` |
| continuation separator | `#FF00FF` |

The export body is plain monospace plus the yellow mark. It does not color separators.

`getConfig(false)` copies the default toolbar arrays and filters out `insertExampleMessage`, and filters `exampleMessage` out of `pluginsEnabled`. A shared Froala default that already contains the button cannot leak it onto a screen that did not opt in. `getConfig(true)` with no current `igId` adds nothing, because `exampleMessageEditorOptions` returns an empty object when `igId` is missing. The initialized handler calls `renderAll` only when the flag is true, an `igId` exists, and `editor.exampleMessage` is present.

Screens and the flag, again, as a checklist:

| UI | Froala | M button |
| --- | --- | --- |
| IG narrative section form | yes | yes |
| Library narrative section form | yes | yes |
| Segment, datatype, value set, conformance profile pre-definition | yes | yes |
| Those same resources' post-definition | yes | yes |
| Profile component pre-definition and post-definition | yes | yes |
| Definition text dialog | yes | no |
| Comment dialog | no, plain text | no |
| Author notes, usage notes, resource metadata | yes | no |
| Example message Metadata tab | yes, edits `description` | no |
| Snippet Metadata tab | yes, edits `description` | no |
| Workspace home | yes | no |
| Documentation | yes | no |
| View-only | not mounted, or `froalaView` | no |

Every rich-text screen, with or without M, also has: placeholder empty, image types `jpeg jpg png gif`, file types PDF, MS Word, `application/x-pdf`, plain text, XML, character counter off, quick-insert tags empty, Angular model updates immediately, image resize on, paste as plain text, height 300, image upload `api/storage/upload/`, image manager `api/storage/file`, and image-removed deletes that stored file. Upload params are the current ig, resource type, and resource id. Documentation uses ig `doc`.


## Export

This section is the whole export. It starts with the changes, then the XSLT and the path from the export button to the zip, then every configuration. The rest of the section is the same behavior in implementation order: why the options exist, the screen, the stored enums, the pipeline, the HTML, the files, the downloads, and the edge cases. How a person creates the pointer is in "How to link a message or a snippet". The message and snippet records are in "Message, snippet, and metadata".

### What changed in the export

The example block inside a guide used to be a blue frame. The title inside that frame was `Example: {message} - {snippet}`. Under the title the export printed the snapshot path as text, for example `ExampleMessages/test/test - testSnippet.txt`. The ER7 sat in a gray box. A highlighted snippet line was yellow. The reader saw a filesystem path in the narrative.

That frame is gone. The path is gone. The block is now a borderless table, then the description if that option is on, then the warnings, then the gray ER7 box only when full text is on. The table has no border, no background, and no blue rule. The labels are `Message`, `Snippet` when a snippet is attached, and `IGAMT` when the IGAMT link is on. The visible text of a snapshot link is the message name or the snippet name. The path stays in the `href`. The IGAMT row's visible text is `Open in IGAMT`, not the URL.

The configuration that produces that block also changed. It used to be one content choice and one link choice. It is now two content checkboxes and two link checkboxes, and the two links can both be on.

| What the author sees | What is stored | What the export used to do | What the export does now |
| --- | --- | --- | --- |
| Include example messages | `include` | Drop the references when off | Same. Message sections disappear. Widgets are removed and the paragraphs around them stay. |
| Include the full text in the export | half of `mode` | The text was tied to a single mode | On by itself stores `INLINE`. On together with the snapshot stores `BOTH`. Off together with the snapshot off stores `NONE`. |
| Export a snapshot of the examples | the other half of `mode` | Not its own checkbox | On by itself stores `SEPARATE_FILES`. On with full text stores `BOTH`. This is the checkbox that creates the HTML pages and the `.txt` files for cited examples. |
| Relative link to the snapshot | half of `linkTarget` | One link target | On by itself stores `RELATIVE`. On with IGAMT stores `BOTH`. Checking it turns the snapshot on, because a relative link with no file goes nowhere. |
| IGAMT URL | the other half of `linkTarget` | Could not be combined with the relative link | On by itself stores `IGAMT`. On with the relative link stores `BOTH`. It does not turn the snapshot on. It adds a row. It does not replace the name. |
| Export only the line / Export the highlighted content in the full message | `snippetMode` `LINE` or `HIGHLIGHT` | One snippet presentation | Unchanged in meaning. It now applies to the inline body and to the snapshot page and the snapshot `.txt`. It does not apply to a context-menu download. |
| Include the description from the metadata tab | `includeDescription` | Descriptions were included, and a missing value still means include | Same default. Unchecking it omits the metadata HTML from the guide block and from the snapshot page. |
| Also export every example message and snippet as files, even when the document does not reference them | `exportAllMessages` | Only cited examples were written | Off still writes only cited examples. On also writes the ones the guide never mentions, and it makes relative links resolvable even when the snapshot checkbox is off. |
| Folder name | `folderName` | `ExampleMessages` | Still the prefix of every relative href and of every file in the zip. Export all from Example Messages uses the same name so the folder can be dropped onto an older guide export. |

Two other export behaviors changed with the same work, and they do not read this configuration:

- The context menu on a message downloads one file: HTML, text, or the validation report. The snippet menu downloads one HTML file or one text file. The UI no longer asks the server for the zip that `POST .../message/{id}/export` can still build.
- Detach of a guide reference asks for confirmation before the pointer is removed. That is the editor, not the guide file. It is listed here because the export can only print a reference that is still attached.

`notMessageInfrastructure` ("Export Only Message Infrastructure Mode" on the IG Display card) was not part of this change. Nothing in the example-message export reads it. Leave it.

The editor chrome was not part of this change either. Inside IGAMT the widget can still show a blue frame, a remove button, and Snippet Only / Full Message. Those are stripped or ignored at export. `data-view` is not a configuration.

### XSLT, the flow, and where to change it

XSLT draws one of the two example blocks. It does not decide the configuration, it does not load the ER7, and it does not write the snapshot files. Those happen in Java before Saxon runs, and again in Java after Saxon returns HTML. If you change only `exampleMessage.xsl`, a Froala widget in a narrative will not change. If you change only the Java rewriter, a message section will not change. The two paths have to be edited together when the block itself changes.

#### What XSLT was given, and what it was not

`exampleMessage.xsl` matches the element `ExampleMessage`. That element exists only for a `MESSAGESECTION`. `SerializeMessageSection` builds a `Section` element, and `serializeReference` appends one `ExampleMessage` child. The stylesheet turns that child into `div.fr-exmsg`.

A Froala widget never becomes an `ExampleMessage` element. `rewriteExampleMessageWidgets` walks the finished XML and replaces the saved preview HTML, inside text nodes and inside attributes, with the final `div.fr-exmsg`. `sectionContent.xsl`, `preDef.xsl`, and `postDef.xsl` then paste that string with `disable-output-escaping="yes"`. Saxon does not parse it and does not apply `exampleMessage.xsl` to it. The widget HTML you see in the guide is the string Java wrote.

The snapshot page and the `.txt` are not XSLT either. `ExampleMessageExportDataService` builds them with Java strings after the guide HTML exists. Their `<mark>` and their `<style>` block are not `globalStyle.xsl`. A color change in the stylesheet does not change a snapshot page.

Context-menu HTML and the validation report are the same kind of Java string. They never enter `IGDocumentExport.xsl`.

Word uses the same stylesheet as HTML. `targetFormat` is `docx` instead of `html`. The result is still HTML. docx4j then imports that HTML into a `.docx`. The example block in Word is whatever `exampleMessage.xsl` and the widget rewriter emitted. The snapshot HTML pages are not imported into the document.

#### The flow, from the export button to the zip

1. The guide export button posts `POST /api/export/ig/{igId}/html` or `.../word`. The form carries `documentType=IGDOCUMENT`, an optional configuration id, and an optional filter decision.
2. `ExportAndImportController` loads that configuration, or the user's default IG configuration. `igGeneralConfiguration.exampleMessages` is the object every later step reads. The original locked configuration can be selected. The Example Messages tab is read-only on it.
3. `IgService.generateDataModel` builds the guide model. It does not yet attach example messages.
4. `IgNewExportServiceImpl.serializeIgDocumentToHtml` calls `ExampleMessageExportDataService.populate`. Populate loads `IgExampleMessages` for this guide id, renders every snippet once, and assigns `ExampleMessages/{profile}/{file}.txt` paths when `include` or `exportAllMessages` is true. No file is written here. The paths exist so later hrefs and the zip agree.
5. `IgDataModelSerializationServiceImpl.serializeDocument` writes the guide as XOM XML. For each table-of-contents section, `SectionSerializationServiceImpl` either calls `SerializeTextSection` or `SerializeMessageSection`. A message section with `include` false returns null and the section is absent from the XML, heading included. A message section with `include` true emits `Section` plus, when `messageId` is set, one `ExampleMessage` child. Ordinary narrative sections always emit. Their HTML is `SectionContent`.
6. Still inside `serializeDocument`, after every section is attached, `rewriteExampleMessageWidgets` walks every element. Any attribute or text node whose string contains `fr-exmsg` is passed through `rewriteNarrative`. That is the widget path. It runs for section narratives, pre-definition, post-definition, and any other field that was serialized with the class still in it. Message-section `ExampleMessage` elements are not touched by this walk, because they are XML elements, not an HTML string containing the class.
7. The XOM document is serialized to an XML string. `exportService.exportSerializedElementToHtml` runs Saxon on `/IGDocumentExport.xsl` with that string. Parameters include `targetFormat` (`html` or `docx`), the document title, fonts, and the column filters. Saxon returns one HTML document.
8. For Word, that HTML is handed to docx4j and becomes the `.docx`. For HTML, that document is the guide page.
9. The controller then asks for extra bytes. HTML calls `collectStaticPages` and `collectFiles`. Word calls `collectFiles` only. Pages require `include` and `producesFiles()`. Text files require `producesFiles()`. Both collectors write a message or a snippet only when it was `markReferenced` during step 5 or 6, unless `exportAllMessages` is true.
10. `writeExportedDocument` zips the result. HTML uses `staticSite=true`: the guide is `{document}/index.html` and every extra path is prefixed with `{document}/`, so a relative href written in step 5 resolves next to `index.html`. Word uses `staticSite=false`: the `.docx` and the `.txt` files sit at the zip root, and the HTML snapshot pages are not in the zip.

`markReferenced` during steps 5 and 6 is what step 9 publishes. A snippet citation marks the snippet id and not the parent message. The Message row can still link to the parent page. Step 9 will not write that page unless the message itself was cited or Additional files is on. Change the link and the collector together.

#### The stylesheet chain

Saxon loads one stylesheet, `export/document-export/src/main/resources/IGDocumentExport.xsl`. Imports are absolute classpath paths (`/templates/...`), resolved from that module's resources. The chain for an example is:

```text
IGDocumentExport.xsl
  template match="/"
    <style> calls globalStyle, froalaEditorStyle, then htmlStyle or wordStyle
    targetFormat html  -> displayHtmlContent
    targetFormat docx  -> displayWordContent

htmlContent.xsl / wordContent.xsl
  displaySection                         section.xsl
    displayInfoSection                   infoSection.xsl
      heading from @title and @h, only when the element name is Section
      displaySectionContent              sectionContent.xsl
        disable-output-escaping of SectionContent   (widget HTML is pasted here)
      displayProfileContent              profileContent.xsl
        when count(ExampleMessage) > 0
          apply-templates ExampleMessage
            exampleMessage.xsl           (message-section block is built here)
    then for-each child, sorted by @position, displaySection again
```

`profileContent.xsl` imports `exampleMessage.xsl`. The `displayProfileContent` template is one `xsl:choose`. The `ExampleMessage` branch is after datatype-library summary, profile component, composite profile, conformance profile, segment, datatype, and value set, and before constraints. The first branch whose `count(...)` is greater than zero wins, and the others are skipped. A normal message section has an `ExampleMessage` child and no profile child, so the example branch runs. A section that also contains a `ConformanceProfile`, `Segment`, `Datatype`, or `Valueset` child will take that earlier branch and will not apply `exampleMessage.xsl`. The `ExampleMessage` element is then ignored by the recursive `displaySection` walk too, because `displayInfoSection` returns immediately unless `name() = 'Section'`. If you add a new place that emits `ExampleMessage`, it has to be a `Section` that does not already match an earlier branch, or you have to add another `apply-templates` that is not inside that `choose`.

`section.xsl` still visits the `ExampleMessage` element, because `for-each select="*"` does not filter by name. The visit prints nothing. It is not a second copy of the block.

`SectionContent`, `@preDef`, and `@postDef` are pasted, not transformed. `authorNotes.xsl` and the usage-notes template do the same with `disable-output-escaping` when those attributes are non-empty. The widget rewriter has already replaced the preview inside those strings. XSLT will not strip a blue frame that Java left in the string, and it will not add the info table if Java forgot it.

`globalStyle.xsl` is called from the `style` element in `IGDocumentExport.xsl` for both `html` and `docx`. The example rules that were added there, and what they replaced:

| Selector | What it does now |
| --- | --- |
| `.fr-exmsg` | Block, margin `10px 0`, padding 0, border 0, transparent background. This is the removal of the blue frame. |
| `.fr-exmsg .fr-exmsg-info` | Border-collapse, no border, transparent. The info table is not a grid. |
| `.fr-exmsg .fr-exmsg-info th, td` | No border, padding `1px 12px 1px 0`, left, transparent, normal weight. |
| `.fr-exmsg .fr-exmsg-info th` | Color `#555`, weight 600, nowrap. The row label is the only bold cell. |
| `.fr-exmsg .fr-exmsg-info a` | Color `#1565c0`. |
| `.fr-exmsg .fr-exmsg-warning` | Block, bottom margin 8px, color `#b71c1c`. |
| `.fr-exmsg .fr-exmsg-description` | Block, bottom margin 8px, line-height 1.5. |
| `.fr-exmsg .fr-exmsg-body` and `pre.fr-exmsg-body` | Gray box `#f5f5f5`, border `1px solid #ccc`, radius 4px, padding 9.5px, Consolas/Monaco, 10pt. |
| `.fr-exmsg .fr-exmsg-line` | `white-space: pre-wrap`, so one div is one ER7 line. |
| `.fr-exmsg .fr-exmsg-mark` | Background `#fff176`, bottom border `2px solid #f9a825`. |

There is no `.fr-exmsg-file` rule anymore. That class was the visible path. Do not bring the path back as a row unless the product decision changes. `contentTable td, th { border: 1px solid black }` does not apply to this table, because the info table's class is `fr-exmsg-info`, not `contentTable`. The info-table rules repeat `border: 0` so a later table rule does not frame it.

Word receives the same `style` block in the HTML that docx4j imports. A rule docx4j drops will be missing in the `.docx` and still present in the HTML guide. The snapshot page does not use this stylesheet at all.

#### What `exampleMessage.xsl` does with one element

The template is `match="ExampleMessage"`. It always opens `div.fr-exmsg`. It does not read `include`, `mode` as a reason to skip the element, or the export configuration. Java already omitted the element when the section is excluded, and Java already chose which attributes exist. The stylesheet only looks at attributes and children that are present.

Order inside the div:

1. If `@mode` is not `MISSING`, a `table.fr-exmsg-info`. Row `Message`: `@name` inside an `a` when `@messagePage` is non-empty, otherwise the name as text. Row `Snippet` only when `@snippetName` is non-empty: `@snippetName` inside an `a` when `@snippetPage` is non-empty. Row `IGAMT` only when `@igamtHref` is non-empty: the link text is the literal `Open in IGAMT`, not `@name`.
2. If `Description` is non-empty, `div.fr-exmsg-description`, and the child is written with `disable-output-escaping="yes"`. Description is HTML Java already cleaned. Escaping it would show the tags as text.
3. Each `Warning` child becomes `div.fr-exmsg-warning`. The text is escaped.
4. If a `Content` child exists, `div.fr-exmsg-body`. Each `Content/Line` becomes `div.fr-exmsg-line`. A `Line` with element children walks them: `Mark` becomes `span.fr-exmsg-mark`, anything else is printed as text (`Text` is the usual sibling). A `Line` with no element children and empty string value prints `&#160;`. Any other line prints its text.

`@mode` values Java writes are `INLINE`, `SEPARATE_FILES`, `BOTH`, `NONE`, and `MISSING`. The stylesheet does not branch on `INLINE` versus `BOTH`. Presence of `Content` is the body. Presence of `@messagePage` is the relative link. Presence of `@igamtHref` is the IGAMT row. `@label`, `@profile`, `@messageId`, and `@snippetId` are on the element and the stylesheet does not print them. The path is not an attribute the stylesheet displays.

`MISSING` skips the table and still prints `Warning`. Java puts the missing sentence in that `Warning`. There is no `Content`.

A message-section heading is not this template. `displayInfoSection` prints `@title` of the `Section` as `h1` through `h6` from `@h`. Java's `useExampleNameAsTitle` rewrites that title before Saxon runs, when the stored label is empty or `Message Example`.

#### Where to change something

The running server reads the stylesheet and the Java classes from the packaged jar, not from the `src` tree. An XSLT or Java edit needs `mvn -pl bootstrap -am package -DskipTests` with Java 8, then a restart of `bootstrap/target/hl7-igamt.jar`. An Angular edit needs `npm run aot` in `igamt-hl7-client-v2` before that package, because the client is copied into `bootstrap/src/main/resources/public`. `public/` is gitignored. Saxon is XSLT 1.0. `xsl:import href="/templates/..."` will not see a file you only dropped next to the stylesheet unless that path is on the classpath inside the jar.

| You want to change | Edit this | Also edit, or the other path stays old | Do not edit |
| --- | --- | --- | --- |
| Message / Snippet / IGAMT labels, row order, or link text on a message section | `exampleMessage.xsl` | `appendInfoTable` and `addInfoRow` in `ExampleMessageSerializationServiceImpl`, or widgets keep the old table | The snapshot page builder, unless the page should say the same words |
| The same labels on a widget | `appendInfoTable` | `exampleMessage.xsl` | `data-label` in the Froala plugin. That caption is thrown away at export |
| Whether a name is a link | The `isRelativeLink() && producesFiles()` gate in `serializeReference` and the same gate in `rewriteNarrative` | Nothing in XSLT. The stylesheet links whenever `@messagePage` or `@snippetPage` is non-empty | The checkbox component alone. The screen writes the enum. The gate reads the enum |
| The IGAMT URL shape | `igamtUrl` in the serialization service | The editor href in `buildExampleMessageHref` if the in-app Open link should match. Today the editor uses `?snippetId=` and export uses `/snippet/{id}` | `exampleMessage.xsl`, which prints `@igamtHref` as it arrives |
| Yellow highlight, gray box, or the missing blue frame | `.fr-exmsg*` in `globalStyle.xsl` | The `<style>` string in `ExampleMessageExportDataService` for snapshot pages, and the editor SCSS if the in-app widget should match | `exampleMessage.xsl` class names, unless you rename the classes in Java too |
| Which lines are in the body | `appendLines` / `appendNarrativeLines`, using `isHighlightSnippet()` | The snippet `.txt` and snapshot `<pre>` in `ExampleMessageExportDataService` (`snippetText`, `highlightEr7`) | `exampleMessage.xsl`, which prints `Line` and `Mark` and does not know LINE from HIGHLIGHT |
| A new child in the block, such as a generated-on line | Add the element in `serializeReference` and the node in `rewriteNarrative`, then render it in `exampleMessage.xsl` | The snapshot page, if the line belongs there too | Only the XSL. Widgets will not grow the new child |
| A new export checkbox | The card in `example-message-export-configuration.component.html`, `applyChoices` if it is one of the four coupled boxes, the field on `ExampleMessageExportConfiguration` | Both serializers, and the XSL if the checkbox changes the block rather than only the files | `notMessageInfrastructure`. It is unrelated |
| Which examples get a file | `collectStaticPages` and `collectFiles`, and `markReferenced` | `pageHref`, so the Message row does not point at a page the collector skipped | `exampleMessage.xsl` |
| The file name or the folder | `sanitizeFolder`, `safeName`, `uniquePath` | The hrefs, because they are those paths passed through `relativeHref` | The visible link text. It is the name, not the path |
| HTML zip layout versus Word zip layout | `writeExportedDocument` (`staticSite`) and the two branches in `ExportAndImportController` | The hrefs if you stop placing pages next to `index.html` | `exampleMessage.xsl`. It does not know which zip it is in |
| The in-app blue frame, the M button, Snippet Only / Full Message | `example-message.plugin.ts` and the editor styles | Nothing in the export, unless you also want the export to obey `data-view`. It does not | `globalStyle.xsl` |

`ExampleMessageLinkTarget.java` matches the gitignore pattern `*target*`. After a change to that file, `git add -f` the file. A plain `git add` will not stage it. Do not edit `.gitignore` to fix that one file.

A change to the block is done when the same table comes out of both paths. Export a guide that has one message section and one narrative widget, with the same snippet, and compare the two `div.fr-exmsg` results. Then open the snippet `.html` in the zip if the snapshot checkbox was on, because that page is a third copy.

### Every configuration, and the export it produces

The screen is the Example Messages tab of an Implementation Guide export configuration (`/export-configuration?type=IGDOCUMENT`). Labels are bold. Each group is a card. Include and Export folder are always visible. Content, Links, Snippet, Description, and Additional files are hidden when Include is off. Hiding them does not clear the stored values.

The four checkboxes the author sees are not the four stored enums. `ensure()` derives the checkboxes from `mode` and `linkTarget` when the tab opens, and it does not emit a change. `applyChoices` writes the enums when the author clicks.

| Full text | Snapshot | Stored `mode` |
| --- | --- | --- |
| on | off | `INLINE` |
| off | on | `SEPARATE_FILES` |
| on | on | `BOTH` |
| off | off | `NONE` |

| Relative link | IGAMT URL | Stored `linkTarget` |
| --- | --- | --- |
| on | off | `RELATIVE` |
| off | on | `IGAMT` |
| on | on | `BOTH` |
| off | off | `NONE` |

A brand-new configuration, and the Java default, is full text on, snapshot off, relative on, IGAMT off, snippet `LINE`, description on, additional files off, folder `ExampleMessages`. Stored, that is `INLINE` + `RELATIVE`. The relative checkbox is on and the snapshot checkbox is off. In that state the relative checkbox does nothing, because no snapshot file exists, unless Additional files is also on. The names in the table are plain text. The ER7 is in the guide.

Checking **Relative link to the snapshot** forces **Export a snapshot of the examples** on. The source of the click matters. Unchecking the snapshot clears the relative link. Unchecking the snapshot does not get undone by the relative rule, because that rule runs only when the click was on the relative checkbox. Checking IGAMT never forces the snapshot. Checking full text never forces the snapshot. Checking the snapshot never forces a link.

`isInline()` is `include` and (`INLINE` or `BOTH`). `isSeparateFiles()` is `include` and (`SEPARATE_FILES` or `BOTH`). `producesFiles()` is `isSeparateFiles()` or `exportAllMessages`. A relative href is written only when `isRelativeLink()` and `producesFiles()`. An IGAMT href is written only when `isIgamtLink()`, and it does not need a file. The inline body is written only when `isInline()`.

The sixteen rows below are those four content states times those four link states. They assume Include is on, the description checkbox is off, Additional files is off, the folder is `ExampleMessages`, and the guide cites one snippet `testSnippet` of message `test` in profile `test`. Snippet presentation is called out after the sixteen, because it changes the body and the file contents without changing which rows and which files exist. Description, Additional files, Include, and the folder are the same kind of modifier. HTML and Word are then applied to whatever those choices produced.

#### 1. Full text on, snapshot off, relative on, IGAMT off

Stored: `mode=INLINE`, `linkTarget=RELATIVE`. This is the default.

The guide block has a Message row whose text is `test` and is not a link, a Snippet row whose text is `testSnippet` and is not a link, and no IGAMT row. The gray body is the extracted snippet lines, one `div.fr-exmsg-line` per line. There is no yellow mark.

The HTML zip has `index.html` and no `ExampleMessages/` entries for this citation. The Word file has the same table and the same lines, and no extra `.txt`. The relative checkbox is a lie until a file exists. It does not print the path, and it does not invent a link.

#### 2. Full text on, snapshot off, relative off, IGAMT on

Stored: `INLINE`, `IGAMT`.

Same body as 1. The Message and Snippet names are still not links. A third row says `IGAMT` and links `Open in IGAMT` to `{host.url}/example-messages/{igId}/message/{messageId}/snippet/{snippetId}`. No snapshot files. Word has the same third row. The link works only while that server and that guide still have the snippet.

#### 3. Full text on, snapshot off, relative on, IGAMT on

Stored: `INLINE`, `BOTH`.

Same as 2, because the relative half has no file to point at. Names are text. The IGAMT row is present. No snapshot files. This is what you get if a saved document has `linkTarget=BOTH` and `mode=INLINE` without going through the checkbox coupling. The screen would show relative on and snapshot off. The next time the author checks the relative box, `applyChoices('relative')` turns the snapshot on and the mode becomes `BOTH`.

#### 4. Full text on, snapshot off, relative off, IGAMT off

Stored: `INLINE`, `NONE`.

The table is Message and Snippet as text. No IGAMT row. The gray body is the extracted lines. No files. This is full text with every link refused.

#### 5. Full text off, snapshot on, relative on, IGAMT off

Stored: `SEPARATE_FILES`, `RELATIVE`.

No gray body in the guide. The Message name is a link to `ExampleMessages/test/test.html`. The Snippet name is a link to `ExampleMessages/test/test%20-%20testSnippet.html`. Spaces in the file name are `%20`. The path is not written as text. No IGAMT row.

The HTML zip, beside `index.html`, contains four entries for this citation:

- `ExampleMessages/test/test.html` is not written for a snippet-only citation. The Message link points at it anyway. The page is missing unless the full message is also cited somewhere, or Additional files is on. That is the hole in this configuration.
- `ExampleMessages/test/test - testSnippet.html` is written. It is the snapshot page: a back link to `../../index.html`, the title, and the extracted lines in a `<pre>`.
- `ExampleMessages/test/test - testSnippet.txt` is the same extracted lines.
- `test.txt` is not written for the same reason `test.html` is not.

Word does not contain those HTML pages. Word contains `test - testSnippet.txt` at the zip root under `ExampleMessages/test/`, and the links inside the document still point at the `.html` pages. Opening the Word zip alone does not make the links work. Placing that folder next to an HTML `index.html` does.

#### 6. Full text off, snapshot on, relative off, IGAMT on

Stored: `SEPARATE_FILES`, `IGAMT`.

No gray body. Message and Snippet are plain text, not links, even though the snapshot files exist. The IGAMT row is the only link. The HTML zip still has the snippet `.html` and `.txt`, and still omits the parent `test.html` for a snippet-only citation. The files are there so someone can replace the folder later. The guide does not point at them. Word has the `.txt` and the IGAMT row, and no relative href.

#### 7. Full text off, snapshot on, relative on, IGAMT on

Stored: `SEPARATE_FILES`, `BOTH`.

No gray body. Message and Snippet are relative links, as in 5, including the missing parent page. The IGAMT row is also present, as in 6. Both links are in the same table. They are not a fallback. A reader who can open the folder uses the name. A reader who can reach IGAMT uses `Open in IGAMT`.

#### 8. Full text off, snapshot on, relative off, IGAMT off

Stored: `SEPARATE_FILES`, `NONE`.

No gray body. Message and Snippet are text. No IGAMT row. The HTML zip still has the snippet snapshot page and the snippet `.txt`. Nothing in the guide points at them. This is a snapshot with the links turned off on purpose.

#### 9. Full text on, snapshot on, relative on, IGAMT off

Stored: `BOTH`, `RELATIVE`.

The gray body is in the guide, and the names are relative links. No IGAMT row. Files are the same set as 5. This is the configuration you get when the default is showing and the author checks **Export a snapshot of the examples**, or when the author checks the relative link and the coupling turns the snapshot on while full text stays on.

#### 10. Full text on, snapshot on, relative off, IGAMT on

Stored: `BOTH`, `IGAMT`.

The gray body is in the guide. Names are text. The IGAMT row is present. The snapshot files are written and the guide does not link to them. Checking IGAMT did not clear full text and did not clear the snapshot.

#### 11. Full text on, snapshot on, relative on, IGAMT on

Stored: `BOTH`, `BOTH`.

The gray body is in the guide. Message and Snippet are relative links. The IGAMT row is present. The snippet snapshot files are written. The parent `test.html` is still missing for a snippet-only citation. This is the configuration where every link and the text are on together. It is a legal state. The screen allows both link checkboxes at once, and both content checkboxes at once.

#### 12. Full text on, snapshot on, relative off, IGAMT off

Stored: `BOTH`, `NONE`.

The gray body is in the guide. Names are text. No IGAMT row. The snapshot files are written and not linked. Full text and a snapshot do not require a link.

#### 13. Full text off, snapshot off, relative on, IGAMT off

Stored: `NONE`, `RELATIVE`.

No gray body. Names are text, not links. No files. The relative flag is stored and idle. The screen can show this if `mode` was saved as `NONE` while `linkTarget` stayed `RELATIVE`. Clicking the relative checkbox from the screen would turn the snapshot on and leave this state.

#### 14. Full text off, snapshot off, relative off, IGAMT on

Stored: `NONE`, `IGAMT`.

No gray body. No files. Names are text. The IGAMT row is present and points at the live snippet. This is a name plus a live link, with no frozen text anywhere in the export.

#### 15. Full text off, snapshot off, relative on, IGAMT on

Stored: `NONE`, `BOTH`.

Same as 14. The relative half is idle. The IGAMT row is the only thing added to the names.

#### 16. Full text off, snapshot off, relative off, IGAMT off

Stored: `NONE`, `NONE`.

The table is Message and Snippet as text. No body, no IGAMT row, no files. The guide still says which example it was. It does not carry the text and it does not carry a way to open it.

#### Snippet presentation, applied on top of those sixteen

The Snippet card is two radios. They are stored as `snippetMode`. They do nothing to a reference that has no snippet. A whole-message reference always prints or snapshots the full ER7, with no yellow mark.

**Export only the line** (`LINE`). Wherever a body is included (rows 1, 2, 3, 4, 9, 10, 11, 12), the gray box contains only `renderedContent`, the extracted lines, with no `span.fr-exmsg-mark`. The snapshot page `<pre>` and the `.txt` are those same lines. The other lines of the parent message are not in the guide and not in the snippet file.

**Export the highlighted content in the full message** (`HIGHLIGHT`). The gray box, when a body is included, is the full parent message. Each selected range is a `span.fr-exmsg-mark`: background `#fff176`, bottom border `2px solid #f9a825`. Ranges are 1-based lines and an exclusive end column, sorted and merged on a line. The snapshot page uses `<mark>` with the same colors, not the span class. The snippet `.txt` is still only the extracted lines. Highlight does not put the full message into the `.txt`. The parent page, when it exists, is the full message with no highlight, because the highlight belongs to the snippet.

Switching the radio does not add or remove the Message row, the Snippet row, the IGAMT row, or the files. It changes the text inside the body and inside the snippet snapshot.

#### Description, applied on top of those sixteen

**Include the description from the metadata tab** defaults to on. A missing `includeDescription` also means on, so a configuration saved before the checkbox still prints the description.

When it is on, the block contains `div.fr-exmsg-description` after the table and before the warnings and the body. A snippet reference prints the snippet's metadata description. A message reference prints the message's metadata description. The section's own title is not this field. The widget caption is not this field. `narrativeHTML` on the message or the snippet is not this field.

The markup is the author's HTML after `script`, `style`, `iframe`, `object`, and `form` are removed. If nothing remains but whitespace and there is no `img`, the div is omitted. An image with no text is kept. The same HTML is placed on the snapshot page under the title. It is omitted from the page when the cleaned markup is empty.

When the checkbox is off, that div is absent in the guide and on the snapshot page. The table, the body, the links, and the files stay as the sixteen rows say. Context-menu HTML and text still include the description. This checkbox does not control those downloads.

#### Additional files, applied on top of those sixteen

**Also export every example message and snippet as files, even when the document does not reference them** sets `exportAllMessages`. It does not change `mode` or `linkTarget`. It does not add a body to the guide. It does not add a row.

It does two things the sixteen rows do not.

First, every message and every snippet of the guide gets a `.txt`, and, on an HTML export, an `.html` page. A message the narrative never cites is in the folder. The parent `test.html` that rows 5, 7, 9, and 11 were missing is written, so the Message link resolves.

Second, `producesFiles()` becomes true even when the snapshot checkbox is off. Rows 1, 3, 13, and 15, which stored a relative link and had no file, now write the href. The names become links. The files exist because Additional files is on, not because the snapshot checkbox is on. Rows 2, 4, 14, and 16 do not gain a relative href, because their link target has no relative half.

HTML pages are still skipped when Include is off. Additional files cannot be toggled on the screen while Include is off, but a saved document can have both `include: false` and `exportAllMessages: true`. In that document the guide body has no example blocks, `collectStaticPages` writes nothing, and `collectFiles` still writes the `.txt` files.

#### Include off

**Include example messages** off hides the other cards and drops every example from the guide body. A `MESSAGESECTION` is omitted, title included. A `div.fr-exmsg` is deleted and the surrounding narrative stays. `markReferenced` is not called for those removals. No inline body, no table, no IGAMT row, no relative href.

The folder name is still stored and still visible, because that card is outside the `*ngIf`. The hidden checkboxes are not reset. Turning Include back on shows whatever mode and links were stored.

#### Folder name

The Export folder card is always visible. The input is **Folder name**. The hint under it is: "Export all from Example Messages writes this same folder, using the default configuration. Replace that folder in the guide export and the links still open."

The value is the first path segment of every relative href and of every snapshot entry. `ExampleMessages/test/test.html` becomes `IZ-Examples/test/test.html` when the folder is `IZ-Examples`. The profile folder and the file name do not change. Slashes and `\/:*?"<>|` become spaces, the result is trimmed, a leading or trailing dot or hyphen is stripped, and an empty result or `.` or `..` becomes `ExampleMessages`. Longer than 80 characters is cut to 80.

Export all (`GET /api/example-messages/{igId}/export`) does not use the configuration open on the tab. It reads the user's default IG configuration, then forces `include=true`, `exportAllMessages=true`, and `mode=SEPARATE_FILES`, and it copies `folderName`, `snippetMode`, and `includeDescription`. It does not copy `linkTarget`, because that zip has no guide body. Replacing the folder inside a guide export keeps the relative links only when this name, the profile folder, and the file names still match. Renaming the message changes the file name. Changing the folder here without exporting again breaks the hrefs already written into an older `index.html`.

#### What each download reads

The sixteen rows describe a guide export that was given this configuration.

| Download | Which configuration | Body in the document | Snapshot HTML pages | `.txt` files | Relative links | IGAMT row |
| --- | --- | --- | --- | --- | --- | --- |
| Guide HTML, `POST /api/export/ig/{igId}/html` | the configuration chosen for that export, or the default IG configuration | yes, per the sixteen rows | yes when `include` and `producesFiles()` | yes when `producesFiles()` | per the rows, and only if files are produced | per the rows |
| Guide Word, `POST /api/export/ig/{igId}/word` | the same configuration | the same table and the same inline body, inside the `.docx` | never | yes, at the zip root, when `producesFiles()` | the hrefs are still written, and they point at HTML pages that are not in the Word zip | per the rows |
| Export all on the Example Messages screen | the default IG configuration, then snapshot forced for every example | no guide | yes, every message and every snippet | yes, every message and every snippet | none; each page links back to `../../index.html` | none |
| Message context menu, Export HTML | none | the full message, description included, no back link | that one page, as the download | no | no | no |
| Message context menu, Export Text | none | no | no | that one message | no | no |
| Message context menu, Export Validation Report | none | a validation report, not the example block | no | no | no | no |
| Snippet context menu, Export HTML | none | the full message with the snippet highlighted, even when the configuration says `LINE` | that one page | no | no | no |
| Snippet context menu, Export Text | none | no | no | the extracted lines, even when the configuration says `HIGHLIGHT` | no | no |

A message section and a Froala widget use the same sixteen rows. The section is an `ExampleMessage` XML element transformed by `exampleMessage.xsl` into `div.fr-exmsg`. The widget is rebuilt by `rewriteNarrative` into the same div. The table rows, the description, the body, and the links match. A section whose title is still `Message Example` is retitled `Message Example: {message} - {snippet}` in the guide heading. The widget does not change the narrative section's heading. If the section narrative also contains a widget, the export prints both blocks. They are not collapsed.

### Change the export if you need to

The current cards, enums, and file layout are one design, not a contract. You can offer different options. What follows is why the current options exist, so a replacement does not lose the cases people actually have.

#### People update examples after the guide is exported

An export is a copy taken at one moment. The example in IGAMT can change later: the ER7 text, the snippet selection, the name, or the metadata description. Anything written into the guide as text or as a file is frozen. A link back to IGAMT is not frozen. It opens whatever the message is now, and it works only while that IGAMT server and that guide are still there.

That is why both of these exist, and why both can be selected together:

- A snapshot is the frozen copy. It is a folder of HTML pages and `.txt` files next to the guide. Someone who receives only the export can read the example without IGAMT. If the author later fixes the message in IGAMT, that folder is stale until they export again.
- An IGAMT URL is the live copy. It is useful when the reader is expected to come back to the tool. It is useless for an archive that must still make sense after the guide is edited or the server is gone.

A relative link points at the snapshot page inside the export (`ExampleMessages/.../name.html` relative to `index.html`). It keeps working when the whole guide folder is moved, emailed, or opened from disk. It breaks if the snapshot folder is renamed, moved, or omitted. The visible text of that link is the message or snippet name. The path stays in the address only, because authors do not want a filesystem path in the narrative.

The folder name is configurable for one practical reason. Export all from the Example Messages screen writes that same folder, using the default IG configuration. A person can export the guide, later export the examples again, and drop the new folder on top of the old one. The relative links keep working only when the folder name, the profile folder, and the file names still match. Renaming a message changes its file name. A replacement export should either keep that replace-the-folder behavior or offer an explicit other way to refresh the frozen copy.

Other options you might add, instead of or beside the current ones:

- Embed the text and also keep a snapshot, which is today's "both".
- Link only to IGAMT, and still write the snapshot so someone can replace it later. Today that is snapshot on, relative link off, IGAMT on. The files exist, but the guide does not point at them.
- Write the snapshot without any link, and let the guide show only the name.
- A dated snapshot, a checksum, or a "generated on" line, so a reader can see that the frozen copy is older than the live message. Nothing like that is written today.
- One link that prefers the snapshot and falls back to IGAMT. Today the two links are separate rows, not a fallback.

Do not assume a relative link is enough. A reader who was given only a PDF or only the Word file cannot open a relative HTML page. Word export today attaches the `.txt` files and does not attach the HTML pages, and the links inside the document still point at those HTML pages.

#### What is configurable, and which export it affects

Configuration lives on the user's IG export configuration, under `igGeneralConfiguration.exampleMessages`. The original built-in configuration is locked. People save another configuration and then export with it. Export all from Example Messages does not use the configuration open on screen. It uses the default IG configuration, then forces a full snapshot folder (every message and snippet, the saved folder name, the saved snippet presentation, the saved description flag).

| Choice | Affects the guide HTML and Word body | Affects the snapshot files beside the guide | Affects a context-menu download of one message or snippet |
| --- | --- | --- | --- |
| Include example messages | yes, widgets are removed and message sections are dropped when off | HTML pages are skipped when off | no |
| Full text in the document | yes | no | no, those downloads always contain the text |
| Snapshot of the examples | only by making files exist so relative links can point at them | yes | no |
| Relative link | yes, the name becomes a link | no | no |
| IGAMT URL | yes, adds "Open in IGAMT" | no | no |
| Snippet line vs highlight | yes, the inline body | yes, the snippet page and the snippet `.txt` | no. Menu HTML is always the highlighted full message. Menu text is always the extracted lines |
| Metadata description | yes | yes, on the guide snapshot and on export-all | no, those downloads always include the description |
| Additional files | no, except that relative links can then resolve for examples the narrative never cited | yes, unreferenced messages and snippets are written too | no |
| Folder name | yes, it is the prefix of every relative href | yes | no |

Guide HTML is a zip: `{guide}/index.html` plus the snapshot folder beside it. Guide Word is a zip: the `.docx` plus `.txt` files at the zip root, without the HTML pages. Context-menu export is one file, not a zip. `POST .../message/{id}/export` can still build a zip; the UI does not call it.


### Pipeline

Guide export does the following, in this order.

1. Load the export configuration. A chosen configuration id wins. Otherwise the call uses `getConfigurationToApply(IGDOCUMENT, username)`, which is the user's default IG configuration.
2. Build `IgDataModel`.
3. `ExampleMessageExportDataService.populate` loads every example message of the guide, renders every snippet once, and assigns archive paths. It does this when `include` is true or `exportAllMessages` is true. Otherwise the model map stays empty.
4. Serialize the guide. Message sections become an `ExampleMessage` XML element. Every text node and attribute that contains the class `fr-exmsg` is rewritten. Serialization calls `markReferenced` for each example that the document actually uses.
5. After serialization, collect files from the referenced set (or from every example, when `exportAllMessages` is true).
6. Zip them with the document.

HTML and Word do not collect the same files.

- HTML (`staticSite = true`) collects HTML pages and `.txt` files. The zip entry for the guide is `{documentNameWithoutExtension}/index.html`. Extra files are prefixed with that same folder, so a snapshot page lands at `{document}/{folder}/{profile}/{name}.html` beside `index.html`.
- Word (`staticSite = false`) collects `.txt` files only. It does not collect the HTML pages. Extra files are placed at the zip root, next to the `.docx`, not under a guide folder. Relative links inside the Word document still point at the HTML pages. Those links resolve after the snapshot folder is placed beside an HTML `index.html`. They do not resolve inside the Word zip.

`POST /api/export/ig/{igId}/html` and `POST /api/export/ig/{igId}/word` are the guide downloads. Form fields are `documentType` (`IGDOCUMENT`), optional `config` (a configuration id), and optional `json` (an export filter decision). Sending `json` also saves that filter decision for the user, document, and configuration. Omit `json` to reuse the stored decision.

### Configuration screen

Route: `/export-configuration?type=IGDOCUMENT`.

The page has three document types: Implementation Guide, Datatype Library, Differential. Example messages exist only on the Implementation Guide type.

Inside a selected configuration, `p-tabView` order is:

1. IG Display
2. Conformance Profile
3. Example Messages
4. Composite Profile
5. Segment
6. Datatype
7. ValueSet
8. Font

The Example Messages panel is:

```html
<app-example-message-export-configuration
  [viewOnly]="currentConfiguration.original"
  [config]="currentConfiguration.igGeneralConfiguration"
  (detectChange)="change($event)">
</app-example-message-export-configuration>
```

`viewOnly` is the configuration's `original` flag. The built-in original configuration disables every control on this tab. Save and Reset on the page are disabled when `original` is true or when nothing has changed. Do not write example-message choices onto the locked original configuration. Create or edit another configuration and save it. A guide export uses a configuration only after that save. Export all from the Example Messages screen uses the default IG configuration, not the configuration that happens to be open.

The component reads and writes `config.exampleMessages`, which is `IgGeneralConfiguration.exampleMessages`. `detectChange` emits that general-configuration object, and the parent `change()` marks the configuration dirty.

#### Cards, controls, and bindings

The component fields `fullText`, `snapshot`, `relativeLink`, and `igamtLink` are view state. They are not stored. `ensure()` derives them from `mode` and `linkTarget` whenever the configuration input changes. `applyChoices(source)` writes `mode` and `linkTarget` back.

Labels are bold (`font-weight: 700`) through `ui-chkbox-label` and `ui-radiobutton-label`. Card titles use `h5.card-title.title-text.custom-card-title` with a `#f4f6f8` bar. Cards use the existing `card card-configuration` classes and sit in wrapping rows.

| Card title | Control | Label shown to the user | Stored field | When it is visible |
| --- | --- | --- | --- | --- |
| Include | checkbox | Include example messages | `include` | Always |
| Export folder | text input, label "Folder name" | the folder name | `folderName` | Always, including when example messages are excluded |
| Content | checkbox | Include the full text in the export | derived, writes `mode` | Only while `include` is true |
| Content | checkbox | Export a snapshot of the examples | derived, writes `mode` | Only while `include` is true |
| Links | checkbox | Relative link to the snapshot | derived, writes `linkTarget` | Only while `include` is true |
| Links | checkbox | IGAMT URL | derived, writes `linkTarget` | Only while `include` is true |
| Snippet | radio `LINE` | Export only the line | `snippetMode` | Only while `include` is true |
| Snippet | radio `HIGHLIGHT` | Export the highlighted content in the full message | `snippetMode` | Only while `include` is true |
| Description | checkbox | Include the description from the metadata tab | `includeDescription` | Only while `include` is true |
| Additional files | checkbox | Also export every example message and snippet as files, even when the document does not reference them | `exportAllMessages` | Only while `include` is true |

The folder hint under the input is: "Export all from Example Messages writes this same folder, using the default configuration. Replace that folder in the guide export and the links still open."

Unchecking Include hides Content, Links, Snippet, Description, and Additional files. It does not clear their stored values. The folder card stays.

#### How the four checkboxes become enums

Content:

| Full text | Snapshot | `mode` |
| --- | --- | --- |
| on | off | `INLINE` |
| off | on | `SEPARATE_FILES` |
| on | on | `BOTH` |
| off | off | `NONE` |

`SEPARATE_FILES` means snapshot. The UI does not show the words "separate files".

Links:

| Relative | IGAMT | `linkTarget` |
| --- | --- | --- |
| on | off | `RELATIVE` |
| off | on | `IGAMT` |
| on | on | `BOTH` |
| off | off | `NONE` |

Reading them back:

- Full text is on unless `mode` is `SEPARATE_FILES` or `NONE`.
- Snapshot is on when `mode` is `SEPARATE_FILES` or `BOTH`.
- Relative is on unless `linkTarget` is `IGAMT` or `NONE`.
- IGAMT is on when `linkTarget` is `IGAMT` or `BOTH`.

Coupling, applied only for the control the user just touched (`applyChoices` source):

- Checking **Relative link to the snapshot** also checks **Export a snapshot of the examples**, then writes both enums. A relative link with no snapshot would have no page to open, except when Additional files already produces pages.
- Unchecking **Export a snapshot of the examples** also unchecks the relative link.
- Full text and IGAMT do not force each other.
- Unchecking the relative link does not turn the snapshot off. The files can exist without a link.

`ensure()` fills a missing object with:

```text
include: true
mode: INLINE
exportAllMessages: false
snippetMode: LINE
linkTarget: RELATIVE
folderName: ExampleMessages
includeDescription: true
```

Empty `mode`, `snippetMode`, `linkTarget`, or `folderName` get those same defaults. `includeDescription` is set to true only when it is `null` or `undefined`. `ensure()` does not emit `detectChange`, but it does mutate the object in memory. The next Save persists the filled values.

The default display is therefore full text on, snapshot off, relative on, IGAMT off. The relative checkbox can be on while the snapshot checkbox is off. That is the Java default (`INLINE` + `RELATIVE`). In that state the relative choice does nothing until something produces files (`snapshot` or Additional files). Names are plain text.

### Stored values and runtime predicates

Java field defaults, used when the JSON property is absent:

| JSON property | Java type | Default when absent | Getter rule |
| --- | --- | --- | --- |
| `include` | `boolean` | `true` | the field |
| `mode` | enum | `INLINE` | null becomes `INLINE` |
| `snippetMode` | enum | `LINE` | null becomes `LINE` |
| `linkTarget` | enum | `RELATIVE` | null becomes `RELATIVE` |
| `exportAllMessages` | `boolean` | `false` | the field |
| `folderName` | `String` | `ExampleMessages` | always passed through `sanitizeFolder` |
| `includeDescription` | `Boolean` object | `null` | `null` means include. `isIncludeDescription()` is true when the field is null or true. |

Enums are stored by name: `INLINE`, `SEPARATE_FILES`, `BOTH`, `NONE`, `LINE`, `HIGHLIGHT`, `RELATIVE`, `IGAMT`.

Predicates the export actually branches on:

```text
isInline()         = include && (mode == INLINE || mode == BOTH)
isSeparateFiles()  = include && (mode == SEPARATE_FILES || mode == BOTH)
producesFiles()    = isSeparateFiles() || exportAllMessages
isHighlightSnippet() = snippetMode == HIGHLIGHT
isRelativeLink()   = linkTarget == RELATIVE || linkTarget == BOTH
isIgamtLink()      = linkTarget == IGAMT || linkTarget == BOTH
```

`include == false` makes `isInline()` and `isSeparateFiles()` false even if `mode` is `BOTH`. `exportAllMessages` can still make `producesFiles()` true. The Additional files checkbox is hidden while Include is off, but a previously saved `exportAllMessages: true` remains in the JSON.

Consequences:

| Predicate | What it turns on |
| --- | --- |
| `isInline()` | The message text is printed inside the guide document. |
| `producesFiles()` | `.txt` files may be written. |
| `isInclude()` and `producesFiles()` | HTML snapshot pages may be written. Pages require both. Text files require only `producesFiles()`. |
| `isRelativeLink()` and `producesFiles()` | The message name and the snippet name become links to the snapshot HTML pages. |
| `isIgamtLink()` | An extra row, "Open in IGAMT", is added. It does not replace the snapshot link. |
| `isIncludeDescription()` | Metadata HTML is printed above the message body, in the guide and on snapshot pages that go through the guide configuration. |
| `isHighlightSnippet()` | A snippet is the whole message with the snippet marked. Otherwise only the extracted snippet lines are written. |
| `exportAllMessages` | A message or snippet is written even when the document never references it. |

`NONE` + `NONE` with `include` true still prints the info table (the names) and omits the body, the snapshot, and both links.

#### Decision matrix

| Full text | Snapshot | Relative | IGAMT | Additional files | Body in the guide | Snapshot files for referenced examples | Name links to the snapshot | "Open in IGAMT" |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| on | off | on | off | off | yes | no | no | no |
| on | on | on | off | off | yes | yes | yes | no |
| off | on | on | off | off | no | yes | yes | no |
| on | off | off | on | off | yes | no | no | yes |
| on | on | off | on | off | yes | yes | no | yes |
| on | on | on | on | off | yes | yes | yes | yes |
| off | off | off | off | off | no | no | no | no |
| on | off | on | off | on | yes | yes, and unreferenced examples too | yes | no |
| off | off | off | off | on | no | text files only if Include was turned off after Additional files was saved; HTML pages require Include | no | no |

### What one example looks like in the guide

There are two emitters. They must stay the same.

1. Narrative rewrite, for widgets inside HTML strings (section narrative, pre-definition, post-definition, and any attribute that contains `fr-exmsg`). `rewriteNarrative` builds the HTML directly.
2. `exampleMessage.xsl`, for a message section. Serialization emits an `ExampleMessage` element and the XSL turns it into the same HTML.

Order inside `div.fr-exmsg`:

1. The info table, unless the message is missing.
2. The description, when the configuration includes it and the HTML is non-empty.
3. Warnings.
4. The body, only when `isInline()`.

#### Info table

`table.fr-exmsg-info`, no border, transparent background. Header cells are labels, not column titles from the message. Data cells hold the name or the link. The visible text is never a file path.

| Row label | When the row exists | Visible text | `href` when the link is active | `href` when it is not |
| --- | --- | --- | --- | --- |
| Message | Always, when the example exists | The example message's `name` | Snapshot page of the full message: `pageHref(ig, messageId, null)` | The name is plain text |
| Snippet | A snippet was requested and found, and its name is non-empty | The snippet's `name` | Snapshot page of that snippet: `pageHref(ig, messageId, snippetId)` | The name is plain text |
| IGAMT | `isIgamtLink()` and the URL is non-empty | The literal text `Open in IGAMT` | `{host.url}/example-messages/{igId}/message/{messageId}` or that URL plus `/snippet/{snippetId}` when the snippet exists | Row omitted |

`host.url` defaults to `http://localhost:9000`. Trailing slashes are removed. The IGAMT snippet URL is a path segment (`/snippet/{id}`). That is not the editor link. The editor link is `/example-messages/{igId}/message/{messageId}?snippetId={id}`.

The snapshot href is relative to `index.html`. It is the `.txt` archive path with the extension replaced by `.html`, then each path segment is UTF-8 URL-encoded and `+` is replaced with `%20`. Example: message `test`, snippet `testSnippet`, profile `test`, folder `ExampleMessages` produces:

```text
ExampleMessages/test/test.html
ExampleMessages/test/test%20-%20testSnippet.html
```

The table shows `test` and `testSnippet`. It does not show `ExampleMessages/test/test - testSnippet.txt`.

A missing message does not get the table. The narrative widget becomes `div.fr-exmsg.fr-exmsg-missing` containing `span.fr-exmsg-link` with the text `The referenced example message is no longer available.` The message-section XML uses `mode="MISSING"` and a `Warning` child with the same sentence, and the XSL skips the table because `mode` is `MISSING`.

A requested snippet that cannot be found produces the warning `The snippet attached to this section could not be found; the full message is shown.` (message section) or `The attached snippet could not be found; the full message is shown.` (narrative). The body falls back to the full message. There is no Snippet row. The IGAMT URL stays on the message, not on a snippet. Snippet render warnings are each a `div.fr-exmsg-warning`.

#### Description

Source is the snippet metadata description when a snippet was found, otherwise the example message metadata description. Both are the rich text from the metadata tab.

The markup is kept, with `script`, `style`, and `iframe` removed. If the remaining markup has no text and no `img`, the block is omitted. The wrapper is `div.fr-exmsg-description`. The class name also appears in the page CSS, so a search for the class name is not proof that a description was written. The sample message named `test` has an empty description, so its export has no description block.

Single-file downloads from the example-messages context menu always include this description. They do not consult `includeDescription`. The guide export and the export-all snapshot do consult it. `null` means include, so old configurations keep descriptions.

#### Body

Printed only when `isInline()`.

- No snippet, or snippet mode `LINE`: the full message, or only the extracted snippet lines (`renderedContent`).
- Snippet mode `HIGHLIGHT` and a snippet was found: the full message (`fullMessage`, else the message text), with the snippet ranges marked.

Each line is `div.fr-exmsg-line` inside `div.fr-exmsg-body`. An empty line is a non-breaking space so the line still takes height. A highlighted range is `span.fr-exmsg-mark` in the narrative HTML, and a `Mark` child of `Line` in the XML. The XSL turns `Mark` into `span.fr-exmsg-mark`. Unmarked pieces in the XML are `Text` children.

Highlight coordinates match the editor: line numbers are 1-based, the end column is exclusive. Ranges on the same line are merged.

#### CSS that belongs to the export

From `globalStyle.xsl`. Do not put the editor's blue frame back on these rules.

```text
.fr-exmsg                         block, margin 10px 0, padding 0, border 0, transparent
.fr-exmsg-info                    collapsed, no border, transparent, margin 0 0 8px
th, td                            no border, padding 1px 12px 1px 0, left, baseline, normal weight
th                                color #555, weight 600, nowrap
a                                 color #1565c0
.fr-exmsg-warning                 block, margin 0 0 8px, color #b71c1c
.fr-exmsg-description             block, margin 0 0 8px, line-height 1.5
.fr-exmsg-body                    Consolas/Monaco, 10pt, pre-wrap, break-all, color #333,
                                  background #f5f5f5, border 1px solid #ccc, radius 4px, padding 9.5px
.fr-exmsg-mark                    background #fff176, border-bottom 2px solid #f9a825
```

Snapshot pages use a simpler page: Georgia body, 24px margin, the same gray `pre`, and `mark` instead of `span.fr-exmsg-mark` for highlights. A guide-linked snapshot page starts with `Back to the guide`.

#### XML emitted for a message section

`ExampleMessage` attributes:

| Attribute | Value |
| --- | --- |
| `messageId`, `snippetId` | ids, snippet id empty when absent |
| `mode` | `BOTH`, `SEPARATE_FILES`, `INLINE`, `NONE`, or `MISSING` |
| `name` | message name |
| `profile` | profile label |
| `label` | `message` or `message - snippet` |
| `snippetName` | present only when the snippet was found |
| `messagePage` | present only for a relative link when files are produced |
| `snippetPage` | present only in that same case, and only when the snippet was found |
| `igamtHref` | present only for an IGAMT link |

Children: `Description` (raw HTML, emitted with `disable-output-escaping`), zero or more `Warning`, and `Content`/`Line`/`Text`/`Mark` when inline.

Message-section serialization returns null when `include` is false, so the whole section, including its narrative, is dropped. Ordinary `TEXT` sections are always serialized. Profile, segment, datatype, and value-set sections are untouched.

`notMessageInfrastructure` ("Export Only Message Infrastructure Mode" on the IG Display card) is still stored and still shown. No serializer reads it. Leave the checkbox where it is. Do not use it to drop narrative sections.

### Snapshot folder

Paths are assigned in `populate` for every message of the guide.

```text
{folder}/{profile}/{message}.txt
{folder}/{profile}/{message}.html
{folder}/{profile}/{message} - {snippet}.txt
{folder}/{profile}/{message} - {snippet}.html
```

`folder` is `sanitizeFolder(folderName)`. Illegal characters `\/:*?"<>|` and control characters become a space, whitespace collapses, leading and trailing dots, spaces, and hyphens are removed, length is capped at 80. Empty, `.`, and `..` become `ExampleMessages`.

`profile` is the conformance profile name, else its identifier, else the composite profile name, else `Profile`.

`message` and `snippet` go through `safeName`: the same illegal characters become a hyphen, length capped at 80, empty becomes `message` or `snippet`.

Collisions are case-insensitive. The second path gets ` (2)` before the extension, then ` (3)`, and so on.

Which of those paths are actually written:

- A message `.txt` and `.html` when `exportAllMessages` is true, or `markReferenced` was called for the message itself (a reference with no found snippet).
- A snippet `.txt` and `.html` when `exportAllMessages` is true, or that snippet id was marked.

`markReferenced(snippetId)` adds the snippet id when that snippet exists. It marks the message only when the id is null, empty, or not a known snippet. A widget that points at a snippet does not mark the parent message. The Message row still links to the full-message HTML page. That page is absent from the zip unless the full message is also referenced somewhere, or Additional files is on. The snippet page is present. Preserve this, or change both the link and the file collection together.

`collectStaticPages` skips everything when `include` is false, even if `producesFiles()` is true. `collectFiles` does not check `include`.

A snapshot HTML page for a message is the escaped message text inside `pre`. A snippet page uses `snippetMode`: `LINE` is the extracted text, `HIGHLIGHT` is the full message with `<mark>`. When `linkToGuide` is true, the back link is `../` repeated once per directory above the file, then `index.html`. `ExampleMessages/profile/file.html` is two directories down, so the link is `../../index.html`. That is correct when the folder sits beside `index.html`. It points outside an export-all zip that is opened by itself.

#### Replacing the folder

`GET /api/example-messages/{igId}/export` builds a zip whose root is the folder, not the guide. The file name is `ExampleMessages-{igId}.zip`.

It does not use the configuration currently open on the screen. It loads the user's default IG configuration and then `replacementFolder`:

```text
include = true
exportAllMessages = true
mode = SEPARATE_FILES
folderName, snippetMode, includeDescription copied from the default configuration
linkTarget left at its default RELATIVE, and unused, because this zip has no guide body
```

`linkToGuide` is true, so every page links back to `index.html`. Dropping this folder onto the same folder inside a guide HTML export replaces the snapshot and keeps the relative links, including Back to the guide, as long as the folder name matches.

### Context-menu downloads

These ignore the IG export configuration. One menu item downloads one file. There is no dialog and no zip.

Message menu: Export HTML, Export Text, Export Validation Report, Copy Example Message, Delete Example Message.

Snippet menu: Export HTML, Export Text, Copy Snippet, Delete Snippet.

| Call | Result |
| --- | --- |
| `GET .../message/{messageId}/export?format=html` | `{safeName}.html`, full message, description always included, no back link |
| `GET .../message/{messageId}/export?format=txt` or `text` | `{safeName}.txt`, raw message, `text/plain;charset=UTF-8` |
| `GET .../message/{messageId}/export?format=validation` | `{safeName}-validation.html`. The validator's `report.css` link is removed and `ig/src/main/resources/example-messages/validation-report.css` is inlined. An empty report becomes a paragraph. |
| `GET .../snippet/{snippetId}/export?format=html` | `{snippetSafeName}.html`. Always the full message with the snippet highlighted, description always included. This does not follow `snippetMode`. |
| `GET .../snippet/{snippetId}/export?format=txt` | `{snippetSafeName}.txt`, extracted snippet lines only (`renderedContent`, else the full message). |

`POST .../message/{messageId}/export` still zips HTML, text, optional snippets, and an optional validation report. The UI does not call it.

### Worked configuration documents

These are the JSON objects stored at `igGeneralConfiguration.exampleMessages`. Enums are their names. A missing object is replaced in Java by a new `ExampleMessageExportConfiguration`, which is the first document.

Default, which is also what a brand-new configuration shows before anyone touches the Example Messages tab:

```json
{
  "include": true,
  "mode": "INLINE",
  "snippetMode": "LINE",
  "linkTarget": "RELATIVE",
  "exportAllMessages": false,
  "folderName": "ExampleMessages",
  "includeDescription": true
}
```

Full text, no files. The relative checkbox looks on, and it does nothing, because `producesFiles()` is false. Names are plain text. The body is in the guide.

Snapshot plus relative link, no inline text:

```json
{ "include": true, "mode": "SEPARATE_FILES", "linkTarget": "RELATIVE", "snippetMode": "HIGHLIGHT", "includeDescription": true, "exportAllMessages": false, "folderName": "ExampleMessages" }
```

Both body and snapshot, both links:

```json
{ "include": true, "mode": "BOTH", "linkTarget": "BOTH", "snippetMode": "HIGHLIGHT", "includeDescription": true, "exportAllMessages": false, "folderName": "IZ-Examples" }
```

Live IGAMT link only, text in the guide, no files:

```json
{ "include": true, "mode": "INLINE", "linkTarget": "IGAMT", "snippetMode": "LINE", "includeDescription": false, "exportAllMessages": false, "folderName": "ExampleMessages" }
```

Names only. No body, no files, no links:

```json
{ "include": true, "mode": "NONE", "linkTarget": "NONE", "snippetMode": "LINE", "includeDescription": true, "exportAllMessages": false, "folderName": "ExampleMessages" }
```

Excluded. Widgets disappear. Message sections disappear. The other fields can still be sitting in the JSON because the UI hides them instead of clearing them:

```json
{ "include": false, "mode": "BOTH", "linkTarget": "BOTH", "exportAllMessages": true, "folderName": "ExampleMessages" }
```

That last document is a trap. `isInline()` and `isSeparateFiles()` are false because `include` is false. `producesFiles()` is still true because `exportAllMessages` is true. `collectStaticPages` returns nothing because it also requires `include`. `collectFiles` still writes the `.txt` files. The guide body has no example blocks. Do not "fix" this by guessing. If you change it, change both collectors and the hidden-checkbox behavior together.

`includeDescription: null` is not the same as false. Null means include. False means omit. The UI turns null into true when the tab is opened, and the next Save persists true.

### One widget, one configuration, the HTML that comes out

Take message `test` (`id` `M1`), snippet `testSnippet` (`id` `S1`), profile name `test`, folder `ExampleMessages`. The widget is `data-message-id="M1"` `data-snippet-id="S1"`. The parent text contains an `NK1` line. The snippet selects that line. The metadata descriptions are empty, so no description div is emitted. `host.url` is `http://localhost:9000`. The guide id is `IG1`.

`INLINE` + `RELATIVE` + `LINE` + include, no additional files. No `href`. Body is only the extracted line:

```html
<div class="fr-exmsg">
<table class="fr-exmsg-info">
<tr><th>Message</th><td>test</td></tr>
<tr><th>Snippet</th><td>testSnippet</td></tr>
</table>
<div class="fr-exmsg-body">
<div class="fr-exmsg-line">NK1|1|...</div>
</div>
</div>
```

`SEPARATE_FILES` + `RELATIVE` + `HIGHLIGHT`. No body in the guide. Names are links. The yellow mark is on the snapshot page, not in the guide:

```html
<div class="fr-exmsg">
<table class="fr-exmsg-info">
<tr><th>Message</th><td><a href="ExampleMessages/test/test.html">test</a></td></tr>
<tr><th>Snippet</th><td><a href="ExampleMessages/test/test%20-%20testSnippet.html">testSnippet</a></td></tr>
</table>
</div>
```

`BOTH` + `BOTH` + `HIGHLIGHT`. Body is the full message, the snippet line is `span.fr-exmsg-mark`, and the IGAMT row is extra. The IGAMT URL for a snippet is the snippet path, not the message path:

```html
<div class="fr-exmsg">
<table class="fr-exmsg-info">
<tr><th>Message</th><td><a href="ExampleMessages/test/test.html">test</a></td></tr>
<tr><th>Snippet</th><td><a href="ExampleMessages/test/test%20-%20testSnippet.html">testSnippet</a></td></tr>
<tr><th>IGAMT</th><td><a href="http://localhost:9000/example-messages/IG1/message/M1/snippet/S1">Open in IGAMT</a></td></tr>
</table>
<div class="fr-exmsg-body">
<div class="fr-exmsg-line">MSH|...</div>
<div class="fr-exmsg-line"><span class="fr-exmsg-mark">NK1|1|...</span></div>
<div class="fr-exmsg-line">PID|...</div>
</div>
</div>
```

`INLINE` + `IGAMT`, no snapshot. The message name is not a link. IGAMT is the only link. Body is present.

`NONE` + `NONE`. The table still exists. Message and snippet names are text. There is no body, no description, no warning unless the snippet is broken.

Same widget with the snippet deleted in IGAMT. `snippetFound` is false. There is no Snippet row. The body, if inline, is the full message. A warning div says `The attached snippet could not be found; the full message is shown.` The IGAMT href, if requested, stops at `/message/M1`.

Same widget with the message deleted. The div becomes:

```html
<div class="fr-exmsg fr-exmsg-missing">
<span class="fr-exmsg-link">The referenced example message is no longer available.</span>
</div>
```

A message section for the same pair does not store that HTML. It stores `messageId` and `snippetId`. Serialization emits:

```xml
<ExampleMessage messageId="M1" snippetId="S1" mode="BOTH" name="test" profile="test" label="test - testSnippet" snippetName="testSnippet" messagePage="ExampleMessages/test/test.html" snippetPage="ExampleMessages/test/test%20-%20testSnippet.html" igamtHref="http://localhost:9000/example-messages/IG1/message/M1/snippet/S1">
  <Content>
    <Line><Text>MSH|...</Text></Line>
    <Line><Mark>NK1|1|...</Mark></Line>
  </Content>
</ExampleMessage>
```

`exampleMessage.xsl` turns that into the same `div.fr-exmsg` as the narrative rewriter. `mode="MISSING"` skips the table and prints the warning. `Description` is raw HTML passed through with output escaping disabled. `Mark` becomes `span.fr-exmsg-mark`. A `Line` with no element children prints its text. An empty line prints a non-breaking space.

If the section narrative also contains a widget, you get two blocks: the rewritten widget inside the section HTML, and the XSL block from the ids. They are not deduplicated.

### File names, step by step

Start with `folderName`. `sanitizeFolder` does this, in order:

1. Null becomes empty.
2. Trim.
3. Replace every `\ / : * ? " < > |` and every control character with a space.
4. Collapse whitespace to a single space and trim.
5. Strip leading and trailing dots, spaces, and hyphens.
6. If the result is empty, `.`, or `..`, use `ExampleMessages`.
7. If it is longer than 80 characters, cut to 80 and trim. If that is empty, use `ExampleMessages`.

`My/Examples` becomes `My Examples`. `..` becomes `ExampleMessages`. `ExampleMessages` stays `ExampleMessages`.

Profile folder, `safeName(profileLabel, "Profile")`:

1. Trim.
2. Replace the same illegal characters with a hyphen, not a space.
3. Collapse whitespace, trim, strip leading and trailing dots, spaces, and hyphens.
4. Empty becomes the fallback (`Profile`, `message`, or `snippet`).
5. Cut to 80 characters.

Message file: `{folder}/{profile}/{safeName(message.name, "message")}.txt`. Snippet file: `{folder}/{profile}/{messageSafe} - {safeName(snippet.name, "snippet")}.txt`. The HTML page is the same path with `.html`.

`uniquePath` treats paths as case-insensitive. The first `test.txt` wins. The next `Test.txt` becomes `Test (2).txt`. Then `Test (3).txt`. The suffix is inserted before the extension. HTML and text are reserved separately, so `test.txt` and `test.html` do not collide with each other.

A guide HTML zip then prefixes the document folder. If the downloaded guide file would have been `Document-IG1.html`, the entries are:

```text
Document-IG1/index.html
Document-IG1/ExampleMessages/test/test.html
Document-IG1/ExampleMessages/test/test.txt
Document-IG1/ExampleMessages/test/test - testSnippet.html
Document-IG1/ExampleMessages/test/test - testSnippet.txt
```

The href inside `index.html` does not include `Document-IG1/`. It is relative to `index.html`.

Export all (`GET /api/example-messages/{igId}/export`) does not add the document folder. The zip name is `ExampleMessages-{igId}.zip`. Entries start at `ExampleMessages/...` or whatever `folderName` the default configuration uses. Each HTML page's back link is `../../index.html` from a file two directories down. That link works after the folder is copied beside a guide `index.html`. Opened alone, it points outside the zip.

Word zip, when files are produced:

```text
SomeGuide.docx
ExampleMessages/test/test.txt
ExampleMessages/test/test - testSnippet.txt
```

No `index.html`. No `.html` snapshot pages. Links in the document still say `ExampleMessages/test/test.html`.

### Who gets a file

`populate` assigns a path to every message and every snippet when `include` or `exportAllMessages` is true. Assigning a path does not write a file.

During serialization, each real use calls `markReferenced`:

- Widget or section with a found snippet: add that snippet id only. The parent message is not marked.
- Widget or section with no snippet, or a snippet id that is not on the message: mark the message itself.
- `include` false: widgets are removed without marking, and message sections return null without marking.

Then:

| Collector | Runs when | Writes a message file when | Writes a snippet file when |
| --- | --- | --- | --- |
| `collectStaticPages` | `include` and `producesFiles` | message was marked, or `exportAllMessages` | snippet id was marked, or `exportAllMessages` |
| `collectFiles` | `producesFiles` | same | same |

So a narrative that only cites `testSnippet` writes the snippet page and the snippet text. The Message row still links to `test.html`. That page is missing unless the full message is also cited somewhere, or Additional files is on. The snippet page is there. If you change one side, change the other.

Highlight on a snapshot snippet page uses `<mark>`, not `span.fr-exmsg-mark`. Columns are 1-based and the end column is exclusive, matching the editor. Ranges on one line are sorted and merged. The text is HTML-escaped before it goes inside `mark`. An empty extracted range is skipped.

A guide-linked page is:

```html
<!DOCTYPE html>
<html><head><meta charset="UTF-8"/>
<title>test - testSnippet</title>
<style>body{font-family:Georgia,serif;margin:24px;color:#222;}
a{color:#1565c0;}
pre{font-family:Consolas,Monaco,monospace;font-size:13px;white-space:pre-wrap;word-break:break-all;background:#f5f5f5;padding:12px;border:1px solid #ccc;}
mark{background:#fff176;border-bottom:2px solid #f9a825;color:inherit;}
.fr-exmsg-description{margin:0 0 16px;line-height:1.5;}</style>
</head><body>
<p><a href="../../index.html">Back to the guide</a></p>
<h1>test - testSnippet</h1>
<div class="fr-exmsg-description">...</div>
<pre>...</pre>
</body></html>
```

The description div is omitted when the markup is empty. The class in the style block is always there. Context-menu HTML uses the same page without the back link. `linkToGuide` false also drops the anchor color rule's only use, but the style block is the same function, so the back-link paragraph is the only structural difference. Snippet context-menu HTML uses `highlightedMessagePage`, which has no back link and always highlights, and the title is `{messageName} — {snippetName}` with an em dash.

Description cleaning keeps the author's HTML except `script`, `style`, and `iframe`. If stripping tags leaves no text and there is no `img`, the description is dropped. A description that is only an image is kept.

### API

All of these are under the application. The client calls them as relative `api/example-messages/...`. Export endpoints require `AccessResource('IGDOCUMENT', #id, READ)`.

| Method and path | Body | Returns |
| --- | --- | --- |
| `GET /api/example-messages/{igId}` | | `IgExampleMessagesDTO`. Creates the document if it does not exist. |
| `GET /api/example-messages/{igId}/message/{messageId}` | | `ExampleMessageDTO` including profile display element and snippet validations. |
| `POST /api/example-messages/{igId}/message` | `{ name, profileId }` | The tree, and `createdId` of the new message. |
| `POST /api/example-messages/{igId}/message/{messageId}` | `SaveMessageDTO`: `message`, `narrative`, `name`, `description` | Saved DTO. `narrative` is stored as `narrativeHTML`. After save, every snippet is re-resolved. The status text gains a warning when any snippet is not fully resolved. |
| `POST .../message/{messageId}/copy` | `{ name }` | New message, new id, copied description, narrative, ER7, and snippets. |
| `POST .../message/{messageId}/snippet` | `{ name, messageReferences: string[] }` | `messageReferences` are positional paths from the message tree. |
| `POST .../snippet/{snippetId}` | `{ name, description }` | Updates the snippet metadata. Does not change `messageReferences`. |
| `POST .../snippet/{snippetId}/copy` | `{ name }` | New snippet id, copied description, narrative, and references. |
| `GET .../message/{messageId}/parse` | | Parsed tree, or an error the editor shows on the tree. |
| `POST .../message/{messageId}/validate` | `{ message }` | Counts plus `html` report and `entries` with line, column, `er7Path`, `igPath`, `positionalPath`. |
| `GET .../message/{messageId}/locate` | query `positionalPath`, `hl7Path` | Where that element lives in the profile, so the editor can open it. |
| `GET .../snippet/{snippetId}/render` | | `SnippetRenderResult`. |
| `POST .../message/{messageId}/render` | `{ positionalPaths }` | Render a selection that is not saved as a snippet yet. |
| `GET .../message/{messageId}/usages` | | `ExampleMessageUsage[]` for the message, any snippet. |
| `GET .../snippet/{snippetId}/usages` | | Usages of that snippet only. |
| `POST .../message/{messageId}/detach` | `{ sectionId, kind }` | Detaches and returns the remaining usages. |
| `POST .../snippet/{snippetId}/detach` | `{ sectionId, kind }` | Same, limited to that snippet. |
| `DELETE .../message/{messageId}` | | Refused by the UI while usages remain. |
| `DELETE .../snippet/{snippetId}` | | Same. |
| `GET /api/example-messages/{igId}/export` | | Zip of the snapshot folder. Uses the default IG configuration, then `replacementFolder`. |
| `GET .../message/{messageId}/export?format=html\|txt\|text\|validation` | | One file. |
| `GET .../snippet/{snippetId}/export?format=html\|txt` | | One file. 404 when the snippet id is unknown. |
| `POST .../message/{messageId}/export` | `ExampleMessageExportRequest`: `includeSnippets` default true, `includeValidation` default true, optional `snippetId` | Zip. Not used by the UI. When `snippetId` is set, the zip contains only that snippet's `.txt` and `.html`. Validation is omitted for a snippet-only zip. |

Guide export is separate: `POST /api/export/ig/{igId}/html` or `.../word`, form fields `documentType=IGDOCUMENT`, optional `config` (configuration id), optional `json` (filter decision). Sending `json` saves that decision for this user, document, and configuration. Omit `json` to reuse the stored decision. HTML is always zipped because it is a site. Word is zipped when there are extra files or Excel co-constraint workbooks.

`replacementFolder` sets `include=true`, `exportAllMessages=true`, `mode=SEPARATE_FILES`, and copies `folderName`, `snippetMode`, and `includeDescription` from the default configuration. It does not copy `linkTarget`. There is no guide body in that zip to attach a link to.

### Edge cases already implemented

- Empty message name: file fallback `message`. Empty snippet name: `snippet`. Empty profile: `Profile`.
- Newlines in ER7 are normalized. CR LF and CR become LF. Characters below `0x20` other than tab and newline are dropped from the inline body. A trailing newline does not create an extra empty line.
- An empty inline line becomes `&#160;` so the gray box does not collapse that row.
- `&`, `<`, and `>` in the ER7 are escaped in snapshot pages. In the guide body, XOM and Jsoup escape them. A narrative line shows `&amp;` in the saved HTML source for an ampersand.
- Description HTML that is only whitespace or `&nbsp;` is omitted. Description HTML that contains an `img` and no text is kept.
- `script`, `style`, and `iframe` are removed from descriptions. The narrative rewriter also removes `object` and `form`. The snapshot description cleaner does not remove `object` and `form`. They are not the same function.
- Snippet highlight that starts past the end of a line is clamped. A backwards range is collapsed to empty and skipped.
- Two snippets with the same safe name in one profile folder get ` (2)`.
- `folderName` longer than 80 is cut. Message and snippet safe names are also cut at 80.
- IGAMT link with an empty `host.url` still returns a path starting at `/example-messages/...` because the trim-trailing-slash loop leaves an empty base and then appends the path.
- IGAMT link is omitted when the guide id or the message id is missing.
- A message section whose message was deleted serializes `mode="MISSING"` and does not include the info table.
- `notMessageInfrastructure` is displayed as "Export Only Message Infrastructure Mode" on the IG Display card. No serializer reads it. Narrative sections are not filtered by it.
- The original export configuration has `original=true`. Every control on the Example Messages tab receives `viewOnly` true. Save and Reset are disabled. Do not persist edits onto that configuration.
- Opening the tab runs `ensure()`. It fills null `mode`, `snippetMode`, `linkTarget`, `folderName`, and null `includeDescription` in memory and does not emit a change. The configuration becomes dirty only when the user edits. The next Save writes the filled defaults.
- PrimeNG checkbox `onChange` fires after `ngModel` updates. `applyChoices('relative')` sees the new boolean. The order of the two `if` statements matters: relative-on forces snapshot on; snapshot-off forces relative off. They are keyed by `source`, so unchecking snapshot does not get undone by the relative rule.

### What you can change, restated as questions

You are free to replace the cards, the enums, the table, and the file layout. Answer these before you do, because each one is a case someone already has:

1. After the guide is exported, the author fixes the ER7. Does the reader of the old export see the old text, the new text, or both?
2. The reader has only the HTML folder, no network. Which links still open?
3. The reader has only the Word file. Which links still open?
4. The author exports examples again and wants to drop them onto an existing guide export. What must stay stable: folder name, profile folder, message file name, snippet file name?
5. The author renames the message. Do old relative links break on purpose?
6. A snippet's parent lines moved. Do you show a warning and the full message, or a stale extract?
7. The guide cites a snippet but not the parent. Is the parent page in the zip, and does the Message row point at it?
8. A narrative widget and a message section cite the same snippet. Do you emit one block or two?
9. The metadata description contains an image. Is it in the guide, on the snapshot page, and in the context-menu HTML?
10. The M button is off on definition text and on metadata. If you turn it on, you change which HTML fields can hold a widget, and detach still will not see pre-definition or post-definition.
