package gov.nist.hit.hl7.igamt.examples.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import gov.nist.hit.hl7.igamt.compositeprofile.domain.CompositeProfileStructure;
import gov.nist.hit.hl7.igamt.conformanceprofile.domain.ConformanceProfile;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.domain.IgExampleMessages;
import gov.nist.hit.hl7.igamt.examples.domain.MessageSnippet;
import gov.nist.hit.hl7.igamt.examples.dto.SnippetRenderResult;
import gov.nist.hit.hl7.igamt.examples.repository.IgExampleMessagesRepository;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportMode;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.CompositeProfileDataModel;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.ConformanceProfileDataModel;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.ExampleMessageDataModel;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.IgDataModel;

/**
 * Builds the example message part of the export data model and, once the document has
 * been serialized, the files that go in the export archive next to the document.
 */
@Service
public class ExampleMessageExportDataService {

	private static final Logger logger = LoggerFactory.getLogger(ExampleMessageExportDataService.class);
	private static final String FILE_EXTENSION = ".txt";
	private static final int MAX_NAME_LENGTH = 80;

	@Autowired
	private IgExampleMessagesRepository exampleMessagesRepository;

	@Autowired
	private SnippetRenderService snippetRenderService;

	/**
	 * Loads the IG's example messages, renders every snippet once and assigns archive paths.
	 * Does nothing when the configuration excludes example messages.
	 */
	public void populate(IgDataModel igDataModel, ExampleMessageExportConfiguration configuration) {
		Map<String, ExampleMessageDataModel> result = new LinkedHashMap<>();
		igDataModel.setExampleMessages(result);
		if (configuration == null || (!configuration.isInclude() && !configuration.isExportAllMessages())) {
			return;
		}
		Ig ig = igDataModel.getModel();
		if (ig == null || ig.getId() == null) {
			return;
		}
		IgExampleMessages igExampleMessages = exampleMessagesRepository.findById(ig.getId()).orElse(null);
		if (igExampleMessages == null || igExampleMessages.getExampleMessages() == null) {
			return;
		}
		Set<String> usedPaths = new HashSet<>();
		for (ExampleMessage message : igExampleMessages.getExampleMessages()) {
			if (message == null || message.getId() == null) {
				continue;
			}
			ExampleMessageDataModel dataModel = new ExampleMessageDataModel();
			dataModel.setModel(message);
			dataModel.setProfileId(message.getProfileId());
			dataModel.setProfileLabel(profileLabel(igDataModel, message.getProfileId()));

			String folder = configuration.getFolderName() + "/" + safeName(dataModel.getProfileLabel(), "Profile");
			String messageName = safeName(message.getName(), "message");
			dataModel.setFilePath(uniquePath(folder + "/" + messageName + FILE_EXTENSION, usedPaths));

			if (message.getSnippets() != null) {
				for (MessageSnippet snippet : message.getSnippets()) {
					if (snippet == null || snippet.getId() == null) {
						continue;
					}
					dataModel.getSnippets().put(snippet.getId(), render(ig.getId(), message, snippet));
					String snippetName = safeName(snippet.getName(), "snippet");
					dataModel.getSnippetFilePaths().put(snippet.getId(),
							uniquePath(folder + "/" + messageName + " - " + snippetName + FILE_EXTENSION, usedPaths));
				}
			}
			result.put(message.getId(), dataModel);
		}
	}

	/**
	 * HTML pages for the static export folder. index.html links to these with a relative
	 * href, so opening the folder does not depend on the application.
	 */
	public Map<String, byte[]> collectStaticPages(IgDataModel igDataModel, ExampleMessageExportConfiguration configuration) {
		return collectStaticPages(igDataModel, configuration, true);
	}

	/**
	 * @param linkToGuide when false the page stands alone (the example-messages download has
	 *                    no guide index to return to)
	 */
	public Map<String, byte[]> collectStaticPages(IgDataModel igDataModel, ExampleMessageExportConfiguration configuration,
			boolean linkToGuide) {
		Map<String, byte[]> pages = new LinkedHashMap<>();
		if (configuration == null || !configuration.isInclude() || !configuration.producesFiles()
				|| igDataModel.getExampleMessages() == null) {
			return pages;
		}
		boolean all = configuration.isExportAllMessages();
		for (ExampleMessageDataModel dataModel : igDataModel.getExampleMessages().values()) {
			ExampleMessage message = dataModel.getModel();
			if (message == null) {
				continue;
			}
			if (all || dataModel.isMessageReferenced()) {
				String path = ExampleMessageExportConfiguration.pagePath(dataModel.getFilePath());
				pages.put(path, staticPage(dataModel.getLabel(null), message.getMessage(), path, linkToGuide,
						configuredDescription(configuration, message.getDescription())));
			}
			for (Map.Entry<String, String> snippetPath : dataModel.getSnippetFilePaths().entrySet()) {
				String snippetId = snippetPath.getKey();
				if (all || dataModel.getReferencedSnippetIds().contains(snippetId)) {
					SnippetRenderResult render = dataModel.getSnippetRender(snippetId);
					String path = ExampleMessageExportConfiguration.pagePath(snippetPath.getValue());
					pages.put(path, snippetPage(dataModel.getLabel(snippetId), message, render, configuration, path, linkToGuide,
							configuredDescription(configuration, snippetDescription(message, snippetId))));
				}
			}
		}
		return pages;
	}

	/**
	 * Archive path -> file content. Only referenced messages / snippets unless the
	 * configuration asks for everything; empty when the mode does not produce files.
	 */
	public Map<String, byte[]> collectFiles(IgDataModel igDataModel, ExampleMessageExportConfiguration configuration) {
		Map<String, byte[]> files = new LinkedHashMap<>();
		if (configuration == null || !configuration.producesFiles() || igDataModel.getExampleMessages() == null) {
			return files;
		}
		boolean all = configuration.isExportAllMessages();
		for (ExampleMessageDataModel dataModel : igDataModel.getExampleMessages().values()) {
			ExampleMessage message = dataModel.getModel();
			if (all || dataModel.isMessageReferenced()) {
				files.put(dataModel.getFilePath(), bytes(message.getMessage()));
			}
			for (Map.Entry<String, String> snippetPath : dataModel.getSnippetFilePaths().entrySet()) {
				String snippetId = snippetPath.getKey();
				if (all || dataModel.getReferencedSnippetIds().contains(snippetId)) {
					SnippetRenderResult render = dataModel.getSnippetRender(snippetId);
					files.put(snippetPath.getValue(), bytes(snippetText(message, render, configuration)));
				}
			}
		}
		return files;
	}

	/**
	 * Every message and snippet, using the guide configuration's folder name and snippet
	 * presentation so the folder can replace the one in the guide export.
	 */
	public static ExampleMessageExportConfiguration replacementFolder(ExampleMessageExportConfiguration guide) {
		ExampleMessageExportConfiguration configuration = new ExampleMessageExportConfiguration();
		configuration.setInclude(true);
		configuration.setExportAllMessages(true);
		configuration.setMode(ExampleMessageExportMode.SEPARATE_FILES);
			if (guide != null) {
			configuration.setFolderName(guide.getFolderName());
			configuration.setSnippetMode(guide.getSnippetMode());
			configuration.setIncludeDescription(guide.isIncludeDescription());
		}
		return configuration;
	}

	public byte[] zipFiles(Map<String, byte[]> files) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ZipOutputStream zipOut = new ZipOutputStream(baos);
		for (Map.Entry<String, byte[]> entry : files.entrySet()) {
			zipOut.putNextEntry(new ZipEntry(entry.getKey()));
			zipOut.write(entry.getValue() != null ? entry.getValue() : new byte[0]);
			zipOut.closeEntry();
		}
		zipOut.close();
		return baos.toByteArray();
	}

	private SnippetRenderResult render(String igId, ExampleMessage message, MessageSnippet snippet) {
		try {
			return snippetRenderService.renderSnippet(igId, message.getId(), snippet.getId());
		} catch (Exception e) {
			logger.warn("Snippet {} of example message {} could not be rendered for export: {}",
					snippet.getId(), message.getId(), e.getMessage());
			SnippetRenderResult fallback = new SnippetRenderResult();
			fallback.setMessageId(message.getId());
			fallback.setMessageName(message.getName());
			fallback.setSnippetId(snippet.getId());
			fallback.setSnippetName(snippet.getName());
			fallback.setSnippetDescription(snippet.getDescription());
			fallback.setFullMessage(message.getMessage());
			fallback.setRenderedContent(message.getMessage());
			fallback.getWarnings().add("The snippet could not be rendered; the full message is shown.");
			return fallback;
		}
	}

	private String profileLabel(IgDataModel igDataModel, String profileId) {
		if (profileId == null) {
			return "Profile";
		}
		if (igDataModel.getConformanceProfiles() != null) {
			for (ConformanceProfileDataModel cp : igDataModel.getConformanceProfiles()) {
				ConformanceProfile model = cp.getModel();
				if (model != null && profileId.equals(model.getId())) {
					return label(model.getName(), model.getIdentifier());
				}
			}
		}
		if (igDataModel.getCompositeProfile() != null) {
			for (CompositeProfileDataModel composite : igDataModel.getCompositeProfile()) {
				CompositeProfileStructure model = composite.getModel();
				if (model != null && profileId.equals(model.getId())) {
					return label(model.getName(), null);
				}
			}
		}
		return "Profile";
	}

	private String label(String name, String identifier) {
		if (name == null || name.isEmpty()) {
			return identifier != null && !identifier.isEmpty() ? identifier : "Profile";
		}
		return name;
	}

	private static byte[] snippetPage(String title, ExampleMessage message, SnippetRenderResult render,
			ExampleMessageExportConfiguration configuration, String archivePath, boolean linkToGuide, String descriptionHtml) {
		if (configuration != null && configuration.isHighlightSnippet()) {
			String full = render != null && render.getFullMessage() != null ? render.getFullMessage()
					: (message != null ? message.getMessage() : "");
			return staticPageHtml(title, highlightEr7(full, render != null ? render.getParts() : null), archivePath, linkToGuide, descriptionHtml);
		}
		String content = render != null && render.getRenderedContent() != null
				? render.getRenderedContent()
				: (message != null ? message.getMessage() : "");
		return staticPage(title, content, archivePath, linkToGuide, descriptionHtml);
	}

	private static String snippetText(ExampleMessage message, SnippetRenderResult render,
			ExampleMessageExportConfiguration configuration) {
		if (configuration != null && configuration.isHighlightSnippet()) {
			if (render != null && render.getFullMessage() != null) {
				return render.getFullMessage();
			}
			return message != null ? message.getMessage() : "";
		}
		if (render != null && render.getRenderedContent() != null) {
			return render.getRenderedContent();
		}
		return message != null ? message.getMessage() : "";
	}

	private static byte[] staticPage(String title, String body, String archivePath, boolean linkToGuide) {
		return staticPage(title, body, archivePath, linkToGuide, "");
	}

	private static byte[] staticPage(String title, String body, String archivePath, boolean linkToGuide, String descriptionHtml) {
		return staticPageHtml(title, escapeHtml(body), archivePath, linkToGuide, descriptionHtml);
	}

	private static byte[] staticPageHtml(String title, String bodyHtml, String archivePath, boolean linkToGuide, String descriptionHtml) {
		String safeTitle = escapeHtml(title != null ? title : "Example message");
		String backLink = "";
		if (linkToGuide) {
			int depth = archivePath == null ? 0 : archivePath.split("/").length - 1;
			StringBuilder back = new StringBuilder();
			for (int i = 0; i < depth; i++) {
				back.append("../");
			}
			back.append("index.html");
			backLink = "<p><a href=\"" + back + "\">Back to the guide</a></p>";
		}
		String html = "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"/>"
				+ "<title>" + safeTitle + "</title>"
				+ "<style>body{font-family:Georgia,serif;margin:24px;color:#222;}"
				+ "a{color:#1565c0;}pre{font-family:Consolas,Monaco,monospace;font-size:13px;"
				+ "white-space:pre-wrap;word-break:break-all;background:#f5f5f5;padding:12px;"
				+ "border:1px solid #ccc;}mark{background:#fff176;border-bottom:2px solid #f9a825;color:inherit;}"
				+ ".fr-exmsg-description{margin:0 0 16px;line-height:1.5;}</style></head><body>"
				+ backLink
				+ "<h1>" + safeTitle + "</h1>" + descriptionBlock(descriptionHtml) + "<pre>" + bodyHtml + "</pre></body></html>";
		return bytes(html);
	}

	private static String configuredDescription(ExampleMessageExportConfiguration configuration, String description) {
		if (configuration == null || !configuration.isIncludeDescription()) {
			return "";
		}
		return descriptionMarkup(description);
	}

	private static String snippetDescription(ExampleMessage message, String snippetId) {
		if (message == null || message.getSnippets() == null || snippetId == null) {
			return "";
		}
		for (MessageSnippet snippet : message.getSnippets()) {
			if (snippet != null && snippetId.equals(snippet.getId())) {
				return snippet.getDescription();
			}
		}
		return "";
	}

	private static String descriptionMarkup(String html) {
		if (html == null || html.trim().isEmpty()) {
			return "";
		}
		String markup = html.replaceAll("(?is)<script[^>]*>.*?</script>", "")
				.replaceAll("(?is)<style[^>]*>.*?</style>", "")
				.replaceAll("(?is)<iframe[^>]*>.*?</iframe>", "")
				.trim();
		String text = markup.replaceAll("(?is)<[^>]+>", "").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
		if (text.isEmpty() && !markup.toLowerCase().contains("<img")) {
			return "";
		}
		return markup;
	}

	private static String descriptionBlock(String markup) {
		if (markup == null || markup.isEmpty()) {
			return "";
		}
		return "<div class=\"fr-exmsg-description\">" + markup + "</div>";
	}

	private static String escapeHtml(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static byte[] bytes(String content) {
		return (content != null ? content : "").getBytes(StandardCharsets.UTF_8);
	}

	/** Keeps the name readable while being safe on every file system and in a zip entry. */
	static String safeName(String name, String fallback) {
		String base = name == null ? "" : name.trim();
		base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "-").replaceAll("\\s+", " ").trim();
		base = base.replaceAll("^[. -]+|[. -]+$", "");
		if (base.isEmpty()) {
			base = fallback;
		}
		if (base.length() > MAX_NAME_LENGTH) {
			base = base.substring(0, MAX_NAME_LENGTH).trim();
		}
		return base;
	}

	/**
	 * One example message as HTML and text. Snippets and the validation report are optional.
	 * validationHtml is included only when includeValidation is true.
	 */
	public byte[] exportOne(String igId, ExampleMessage message, boolean includeSnippets, boolean includeValidation, String validationHtml, String onlySnippetId) throws IOException {
		Map<String, byte[]> files = new LinkedHashMap<>();
		Set<String> used = new HashSet<>();
		String base = safeName(message != null ? message.getName() : null, "message");
		String body = message != null ? message.getMessage() : "";
		boolean snippetOnly = onlySnippetId != null && !onlySnippetId.isEmpty();
		if (!snippetOnly) {
			String textPath = uniquePath(base + ".txt", used);
			String pagePath = uniquePath(base + ".html", used);
			files.put(textPath, bytes(body));
			files.put(pagePath, staticPage(message != null ? message.getName() : base, body, pagePath, false,
					descriptionMarkup(message != null ? message.getDescription() : null)));
		}
		if ((includeSnippets || snippetOnly) && message != null && message.getSnippets() != null) {
			for (MessageSnippet snippet : message.getSnippets()) {
				if (snippet == null || (snippetOnly && !onlySnippetId.equals(snippet.getId()))) {
					continue;
				}
				String snippetBase = base + " - " + safeName(snippet.getName(), "snippet");
				SnippetRenderResult render = render(igId, message, snippet);
				String snippetText = uniquePath(snippetBase + ".txt", used);
				String snippetPage = uniquePath(snippetBase + ".html", used);
				files.put(snippetText, snippetTextBytes(render, body));
				files.put(snippetPage, snippetHtmlBytes(message, snippet, render, body));
			}
		}
		if (includeValidation && !snippetOnly) {
			files.put(uniquePath(base + "-validation.html", used), validationPage(base + " validation", validationHtml));
		}
		return zipFiles(files);
	}

	/**
	 * One file for one menu choice: the message as HTML, as text, or the validation report.
	 */
	public Download exportMessageFile(ExampleMessage message, String format, String validationHtml) {
		String name = safeName(message != null ? message.getName() : null, "message");
		String body = message != null ? message.getMessage() : "";
		if ("txt".equalsIgnoreCase(format) || "text".equalsIgnoreCase(format)) {
			return new Download(name + ".txt", "text/plain;charset=UTF-8", bytes(body));
		}
		if ("validation".equalsIgnoreCase(format)) {
			return new Download(name + "-validation.html", "text/html;charset=UTF-8",
					validationPage(name + " validation", validationHtml));
		}
		String title = message != null && message.getName() != null ? message.getName() : name;
		return new Download(name + ".html", "text/html;charset=UTF-8",
				staticPage(title, body, name + ".html", false, descriptionMarkup(message != null ? message.getDescription() : null)));
	}

	/**
	 * A snippet is two files, not an archive: the extracted text, and the full message
	 * with that snippet highlighted.
	 */
	public Download exportSnippet(String igId, ExampleMessage message, String snippetId, String format) {
		MessageSnippet snippet = findSnippet(message, snippetId);
		if (snippet == null || message == null) {
			return null;
		}
		SnippetRenderResult render = render(igId, message, snippet);
		String fallback = message.getMessage() != null ? message.getMessage() : "";
		boolean html = "html".equalsIgnoreCase(format);
		String name = safeName(snippet.getName(), "snippet");
		if (html) {
			return new Download(name + ".html", "text/html;charset=UTF-8", snippetHtmlBytes(message, snippet, render, fallback));
		}
		return new Download(name + ".txt", "text/plain;charset=UTF-8", snippetTextBytes(render, fallback));
	}

	private static MessageSnippet findSnippet(ExampleMessage message, String snippetId) {
		if (message == null || message.getSnippets() == null || snippetId == null) {
			return null;
		}
		for (MessageSnippet snippet : message.getSnippets()) {
			if (snippet != null && snippetId.equals(snippet.getId())) {
				return snippet;
			}
		}
		return null;
	}

	private static byte[] snippetTextBytes(SnippetRenderResult render, String fallback) {
		String content = render != null && render.getRenderedContent() != null
				? render.getRenderedContent()
				: fallback;
		return bytes(content);
	}

	private static byte[] snippetHtmlBytes(ExampleMessage message, MessageSnippet snippet, SnippetRenderResult render, String fallback) {
		String messageName = message != null && message.getName() != null ? message.getName() : "Message";
		String snippetName = snippet != null && snippet.getName() != null ? snippet.getName() : "Snippet";
		String full = render != null && render.getFullMessage() != null ? render.getFullMessage() : fallback;
		String description = descriptionMarkup(snippet != null ? snippet.getDescription() : null);
		return highlightedMessagePage(messageName + " — " + snippetName, full, render != null ? render.getParts() : null, description);
	}

	private static byte[] highlightedMessagePage(String title, String message, List<SnippetRenderResult.SnippetPart> parts, String descriptionHtml) {
		String safeTitle = escapeHtml(title);
		String html = "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"/>"
				+ "<title>" + safeTitle + "</title>"
				+ "<style>body{font-family:Georgia,serif;margin:24px;color:#222;}"
				+ "pre{font-family:Consolas,Monaco,monospace;font-size:13px;white-space:pre-wrap;"
				+ "word-break:break-all;background:#f5f5f5;padding:12px;border:1px solid #ccc;}"
				+ "mark{background:#fff176;border-bottom:2px solid #f9a825;color:inherit;}"
				+ ".fr-exmsg-description{margin:0 0 16px;line-height:1.5;}</style></head><body>"
				+ "<h1>" + safeTitle + "</h1>" + descriptionBlock(descriptionHtml) + "<pre>" + highlightEr7(message, parts) + "</pre></body></html>";
		return bytes(html);
	}

	/** Highlights snippet ranges inside the full message. Columns match the editor: 1-based, end exclusive. */
	private static String highlightEr7(String message, List<SnippetRenderResult.SnippetPart> parts) {
		String text = message == null ? "" : message;
		String[] lines = text.split("\n", -1);
		@SuppressWarnings("unchecked")
		List<int[]>[] marks = new List[lines.length];
		if (parts != null) {
			for (SnippetRenderResult.SnippetPart part : parts) {
				if (part == null || part.getHighlightStart() == null || part.getHighlightEnd() == null) {
					continue;
				}
				int startLine = part.getStartLine();
				int endLine = part.getEndLine();
				if (startLine < 1) {
					startLine = 1;
				}
				if (endLine < startLine) {
					endLine = startLine;
				}
				int startCol = part.getHighlightStart().getColumn();
				int endCol = part.getHighlightEnd().getColumn();
				for (int line = startLine; line <= endLine && line <= lines.length; line++) {
					String row = lines[line - 1];
					int from = line == startLine ? Math.max(0, startCol - 1) : 0;
					int to = line == endLine ? endCol - 1 : row.length();
					if (from > row.length()) {
						from = row.length();
					}
					if (to < from) {
						to = from;
					}
					if (to > row.length()) {
						to = row.length();
					}
					if (from == to) {
						continue;
					}
					if (marks[line - 1] == null) {
						marks[line - 1] = new ArrayList<int[]>();
					}
					marks[line - 1].add(new int[] { from, to });
				}
			}
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				out.append('\n');
			}
			out.append(paintLine(lines[i], marks[i]));
		}
		return out.toString();
	}

	private static String paintLine(String line, List<int[]> ranges) {
		if (ranges == null || ranges.isEmpty()) {
			return escapeHtml(line);
		}
		Collections.sort(ranges, new Comparator<int[]>() {
			public int compare(int[] left, int[] right) {
				return Integer.compare(left[0], right[0]);
			}
		});
		List<int[]> merged = new ArrayList<int[]>();
		for (int[] range : ranges) {
			if (merged.isEmpty() || merged.get(merged.size() - 1)[1] < range[0]) {
				merged.add(new int[] { range[0], range[1] });
			} else {
				int[] last = merged.get(merged.size() - 1);
				last[1] = Math.max(last[1], range[1]);
			}
		}
		StringBuilder out = new StringBuilder();
		int cursor = 0;
		for (int[] range : merged) {
			if (range[0] > cursor) {
				out.append(escapeHtml(line.substring(cursor, range[0])));
			}
			out.append("<mark>").append(escapeHtml(line.substring(range[0], range[1]))).append("</mark>");
			cursor = range[1];
		}
		if (cursor < line.length()) {
			out.append(escapeHtml(line.substring(cursor)));
		}
		return out.toString();
	}

	public static final class Download {
		public final String filename;
		public final String contentType;
		public final byte[] body;

		public Download(String filename, String contentType, byte[] body) {
			this.filename = filename;
			this.contentType = contentType;
			this.body = body;
		}
	}

	private static byte[] validationPage(String title, String html) {
		String body = html == null ? "" : html.trim();
		if (body.toLowerCase().contains("<html")) {
			return bytes(withReportStyles(body));
		}
		String safeTitle = escapeHtml(title);
		String page = "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"/><title>" + safeTitle + "</title>"
				+ reportStyleTag()
				+ "</head><body>"
				+ (body.isEmpty() ? "<p>No validation report was produced.</p>" : body)
				+ "</body></html>";
		return bytes(page);
	}

	/**
	 * The validator HTML links to report.css, which is not part of the archive.
	 * Inline the same rules used by the report shown in the example editor.
	 */
	private static String withReportStyles(String html) {
		String withoutLink = html.replaceAll("(?i)<link[^>]*href=[\"']report\\.css[\"'][^>]*>", "");
		String style = reportStyleTag();
		int head = withoutLink.toLowerCase().indexOf("</head>");
		if (head >= 0) {
			return withoutLink.substring(0, head) + style + withoutLink.substring(head);
		}
		return style + withoutLink;
	}

	private static String reportStyleTag() {
		return "<style>" + validationReportCss() + "</style>";
	}

	private static String validationReportCss() {
		InputStream in = ExampleMessageExportDataService.class.getResourceAsStream("/example-messages/validation-report.css");
		if (in == null) {
			return "";
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int read;
			while ((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "";
		} finally {
			try {
				in.close();
			} catch (IOException ignored) {
			}
		}
	}

	private static String uniquePath(String path, Set<String> used) {
		String candidate = path;
		int i = 2;
		while (!used.add(candidate.toLowerCase())) {
			int dot = path.lastIndexOf('.');
			candidate = dot > 0 ? path.substring(0, dot) + " (" + i + ")" + path.substring(dot) : path + " (" + i + ")";
			i++;
		}
		return candidate;
	}
}
