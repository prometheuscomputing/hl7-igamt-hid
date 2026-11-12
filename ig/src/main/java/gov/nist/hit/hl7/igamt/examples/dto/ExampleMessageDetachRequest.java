package gov.nist.hit.hl7.igamt.examples.dto;

/**
 * Clears one guide reference, or every reference when sectionId is empty.
 */
public class ExampleMessageDetachRequest {

	private String sectionId;
	/** MESSAGE_SECTION or NARRATIVE. Empty clears both kinds. */
	private String kind;

	public String getSectionId() {
		return sectionId;
	}

	public void setSectionId(String sectionId) {
		this.sectionId = sectionId;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}
}
