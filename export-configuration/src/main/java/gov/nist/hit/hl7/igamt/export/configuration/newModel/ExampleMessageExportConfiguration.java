package gov.nist.hit.hl7.igamt.export.configuration.newModel;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * How an implementation guide carries its example messages.
 * The folder name is shared with the example-messages export, so that folder can
 * replace the one in the guide and the relative links still resolve.
 */
public class ExampleMessageExportConfiguration {

	/** Whether example message references are carried in the export at all. */
	private boolean include = true;

	/** Full text in the document, a snapshot in the export folder, both, or neither. */
	private ExampleMessageExportMode mode = ExampleMessageExportMode.INLINE;

	/** Extracted snippet lines, or the full message with the snippet highlighted. */
	private ExampleMessageSnippetExportMode snippetMode = ExampleMessageSnippetExportMode.LINE;

	/** Relative link to the snapshot, the IGAMT page, both, or neither. */
	private ExampleMessageLinkTarget linkTarget = ExampleMessageLinkTarget.RELATIVE;

	/**
	 * When true every example message and snippet of the IG is written as a file in the
	 * export archive, not only the ones referenced by the document.
	 */
	private boolean exportAllMessages = false;

	/**
	 * Folder written next to the guide. The example-messages export uses the same name,
	 * so one folder can replace the other.
	 */
	private String folderName = FOLDER_NAME;

	/**
	 * The description written on the example message or snippet metadata tab.
	 * Missing means include, so configurations saved before this option keep the text.
	 */
	private Boolean includeDescription;

	public static final String FOLDER_NAME = "ExampleMessages";

	public boolean isInclude() {
		return include;
	}

	public void setInclude(boolean include) {
		this.include = include;
	}

	public ExampleMessageExportMode getMode() {
		return mode != null ? mode : ExampleMessageExportMode.INLINE;
	}

	public void setMode(ExampleMessageExportMode mode) {
		this.mode = mode;
	}

	public ExampleMessageSnippetExportMode getSnippetMode() {
		return snippetMode != null ? snippetMode : ExampleMessageSnippetExportMode.LINE;
	}

	public void setSnippetMode(ExampleMessageSnippetExportMode snippetMode) {
		this.snippetMode = snippetMode;
	}

	public boolean isHighlightSnippet() {
		return ExampleMessageSnippetExportMode.HIGHLIGHT.equals(getSnippetMode());
	}

	public ExampleMessageLinkTarget getLinkTarget() {
		return linkTarget != null ? linkTarget : ExampleMessageLinkTarget.RELATIVE;
	}

	public void setLinkTarget(ExampleMessageLinkTarget linkTarget) {
		this.linkTarget = linkTarget;
	}

	public boolean isRelativeLink() {
		ExampleMessageLinkTarget target = getLinkTarget();
		return ExampleMessageLinkTarget.RELATIVE.equals(target) || ExampleMessageLinkTarget.BOTH.equals(target);
	}

	public boolean isIgamtLink() {
		ExampleMessageLinkTarget target = getLinkTarget();
		return ExampleMessageLinkTarget.IGAMT.equals(target) || ExampleMessageLinkTarget.BOTH.equals(target);
	}

	public boolean isExportAllMessages() {
		return exportAllMessages;
	}

	public void setExportAllMessages(boolean exportAllMessages) {
		this.exportAllMessages = exportAllMessages;
	}

	public String getFolderName() {
		return sanitizeFolder(folderName);
	}

	public void setFolderName(String folderName) {
		this.folderName = folderName;
	}

	public boolean isIncludeDescription() {
		return includeDescription == null || includeDescription;
	}

	public void setIncludeDescription(Boolean includeDescription) {
		this.includeDescription = includeDescription;
	}

	/** A single folder name. Slashes and parent segments are not kept. */
	public static String sanitizeFolder(String name) {
		String base = name == null ? "" : name.trim();
		base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
		base = base.replaceAll("^[. -]+|[. -]+$", "");
		if (base.isEmpty() || ".".equals(base) || "..".equals(base)) {
			return FOLDER_NAME;
		}
		if (base.length() > 80) {
			base = base.substring(0, 80).trim();
		}
		return base.isEmpty() ? FOLDER_NAME : base;
	}

	public boolean isSeparateFiles() {
		return include && (ExampleMessageExportMode.SEPARATE_FILES.equals(getMode())
				|| ExampleMessageExportMode.BOTH.equals(getMode()));
	}

	public boolean isInline() {
		return include && (ExampleMessageExportMode.INLINE.equals(getMode())
				|| ExampleMessageExportMode.BOTH.equals(getMode()));
	}

	/** Files are produced when a snapshot is exported or when everything is requested. */
	public boolean producesFiles() {
		return isSeparateFiles() || exportAllMessages;
	}

	/** HTML page that sits next to the raw message file inside the static export folder. */
	public static String pagePath(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return "";
		}
		int slash = filePath.lastIndexOf('/');
		int dot = filePath.lastIndexOf('.');
		if (dot > slash) {
			return filePath.substring(0, dot) + ".html";
		}
		return filePath + ".html";
	}

	/** Href relative to index.html. Spaces and other reserved characters are encoded. */
	public static String relativeHref(String archivePath) {
		if (archivePath == null || archivePath.isEmpty()) {
			return "";
		}
		String[] parts = archivePath.split("/");
		StringBuilder href = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				href.append('/');
			}
			try {
				href.append(URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20"));
			} catch (UnsupportedEncodingException e) {
				href.append(parts[i]);
			}
		}
		return href.toString();
	}
}
