package gov.nist.hit.hl7.igamt.export.configuration.newModel;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Export options for the example messages feature (message sections and example
 * messages inserted in narratives).
 */
public class ExampleMessageExportConfiguration {

	/** Whether example message references are carried in the export at all. */
	private boolean include = true;

	/** Inline content in the document, or separate files linked from the document. */
	private ExampleMessageExportMode mode = ExampleMessageExportMode.INLINE;

	/**
	 * When true every example message and snippet of the IG is written as a file in the
	 * export archive, not only the ones referenced by the document.
	 */
	private boolean exportAllMessages = false;

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

	public boolean isExportAllMessages() {
		return exportAllMessages;
	}

	public void setExportAllMessages(boolean exportAllMessages) {
		this.exportAllMessages = exportAllMessages;
	}

	public boolean isSeparateFiles() {
		return include && ExampleMessageExportMode.SEPARATE_FILES.equals(getMode());
	}

	public boolean isInline() {
		return include && ExampleMessageExportMode.INLINE.equals(getMode());
	}

	/** Files are produced when linked from the document or when everything is requested. */
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
