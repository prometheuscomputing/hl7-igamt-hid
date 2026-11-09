package gov.nist.hit.hl7.igamt.ig.domain.datamodel;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.domain.MessageSnippet;
import gov.nist.hit.hl7.igamt.examples.dto.SnippetRenderResult;

/**
 * Export view of one example message: the message, its profile label, the rendered
 * snippets and the archive paths the document links to. Serialization marks what the
 * document references so only those get written as files unless everything is requested.
 */
public class ExampleMessageDataModel implements Serializable {

	private static final long serialVersionUID = 1L;

	private ExampleMessage model;
	private String profileId;
	private String profileLabel;
	/** Archive path of the full message file, e.g. ExampleMessages/ORU_R01/Lab result.hl7 */
	private String filePath;
	/** snippetId -> archive path of the snippet file */
	private Map<String, String> snippetFilePaths = new HashMap<>();
	/** snippetId -> server side rendering (reduced content, warnings) */
	private Map<String, SnippetRenderResult> snippets = new HashMap<>();

	private boolean messageReferenced = false;
	private Set<String> referencedSnippetIds = new HashSet<>();

	public ExampleMessage getModel() {
		return model;
	}

	public void setModel(ExampleMessage model) {
		this.model = model;
	}

	public String getProfileId() {
		return profileId;
	}

	public void setProfileId(String profileId) {
		this.profileId = profileId;
	}

	public String getProfileLabel() {
		return profileLabel;
	}

	public void setProfileLabel(String profileLabel) {
		this.profileLabel = profileLabel;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public Map<String, String> getSnippetFilePaths() {
		return snippetFilePaths;
	}

	public Map<String, SnippetRenderResult> getSnippets() {
		return snippets;
	}

	public MessageSnippet findSnippet(String snippetId) {
		if (snippetId == null || model == null || model.getSnippets() == null) {
			return null;
		}
		return model.getSnippets().stream()
				.filter(s -> snippetId.equals(s.getId()))
				.findFirst()
				.orElse(null);
	}

	public SnippetRenderResult getSnippetRender(String snippetId) {
		return snippetId != null ? snippets.get(snippetId) : null;
	}

	public String getSnippetFilePath(String snippetId) {
		return snippetId != null ? snippetFilePaths.get(snippetId) : null;
	}

	public void markReferenced(String snippetId) {
		if (snippetId != null && !snippetId.isEmpty() && snippets.containsKey(snippetId)) {
			referencedSnippetIds.add(snippetId);
		} else {
			messageReferenced = true;
		}
	}

	public boolean isMessageReferenced() {
		return messageReferenced;
	}

	public Set<String> getReferencedSnippetIds() {
		return referencedSnippetIds;
	}

	public String getLabel(String snippetId) {
		String base = model != null && model.getName() != null ? model.getName() : "Example message";
		MessageSnippet snippet = findSnippet(snippetId);
		if (snippet != null && snippet.getName() != null && !snippet.getName().isEmpty()) {
			return base + " - " + snippet.getName();
		}
		return base;
	}
}
