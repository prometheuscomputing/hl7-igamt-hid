package gov.nist.hit.hl7.igamt.export.configuration.newModel;

/**
 * How example messages and snippets referenced by the document are carried in the export.
 */
public enum ExampleMessageExportMode {
	/** The message text is printed inside the HTML / Word document. */
	INLINE,
	/** A snapshot of each example is written into the export folder. */
	SEPARATE_FILES,
	/** The document contains the full text and also the snapshot files. */
	BOTH,
	/** Example messages stay in the document as names only, with no text and no snapshot. */
	NONE
}
