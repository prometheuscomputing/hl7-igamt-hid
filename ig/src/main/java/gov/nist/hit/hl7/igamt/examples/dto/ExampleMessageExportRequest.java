package gov.nist.hit.hl7.igamt.examples.dto;

public class ExampleMessageExportRequest {

	private boolean includeSnippets = true;
	private boolean includeValidation = true;
	/** When set, the archive contains only this snippet. */
	private String snippetId;

	public boolean isIncludeSnippets() {
		return includeSnippets;
	}

	public void setIncludeSnippets(boolean includeSnippets) {
		this.includeSnippets = includeSnippets;
	}

	public boolean isIncludeValidation() {
		return includeValidation;
	}

	public void setIncludeValidation(boolean includeValidation) {
		this.includeValidation = includeValidation;
	}

	public String getSnippetId() {
		return snippetId;
	}

	public void setSnippetId(String snippetId) {
		this.snippetId = snippetId;
	}
}
