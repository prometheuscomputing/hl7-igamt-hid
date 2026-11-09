package gov.nist.hit.hl7.igamt.export.configuration.newModel;

/**
 * How example messages and snippets referenced by the document are carried in the export.
 */
public enum ExampleMessageExportMode {
	/** The message / snippet content is printed inside the HTML / Word document. */
	INLINE,
	/** The content is written to files next to the document and the document links to them. */
	SEPARATE_FILES
}
