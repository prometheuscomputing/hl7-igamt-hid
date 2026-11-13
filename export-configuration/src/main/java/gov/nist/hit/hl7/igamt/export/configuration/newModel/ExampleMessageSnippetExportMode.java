package gov.nist.hit.hl7.igamt.export.configuration.newModel;

/**
 * How a snippet is written when the document or the bundle includes it.
 */
public enum ExampleMessageSnippetExportMode {
	/** Only the extracted snippet lines. */
	LINE,
	/** The full message, with the snippet highlighted. */
	HIGHLIGHT
}
