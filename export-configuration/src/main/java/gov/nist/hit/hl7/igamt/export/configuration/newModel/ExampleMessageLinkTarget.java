package gov.nist.hit.hl7.igamt.export.configuration.newModel;

/**
 * Where the example-message hyperlink in the guide points.
 */
public enum ExampleMessageLinkTarget {
	/** A relative link to the snapshot in the export folder. */
	RELATIVE,
	/** The example message page in IGAMT. */
	IGAMT,
	/** The snapshot in the export folder and the example message page in IGAMT. */
	BOTH,
	/** No link. The name is shown as text. */
	NONE
}
