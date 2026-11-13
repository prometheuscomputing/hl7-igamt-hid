package gov.nist.hit.hl7.igamt.examples.dto;

/**
 * One place in the implementation guide that references an example message or snippet.
 */
public class ExampleMessageUsage {

	private String sectionId;
	private String sectionType;
	private String path;
	private String label;
	/** MESSAGE_SECTION or NARRATIVE */
	private String kind;

	public String getSectionId() {
		return sectionId;
	}

	public void setSectionId(String sectionId) {
		this.sectionId = sectionId;
	}

	public String getSectionType() {
		return sectionType;
	}

	public void setSectionType(String sectionType) {
		this.sectionType = sectionType;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}
}
