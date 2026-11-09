package gov.nist.hit.hl7.igamt.examples.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

			String folder = ExampleMessageExportConfiguration.FOLDER_NAME + "/" + safeName(dataModel.getProfileLabel(), "Profile");
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
		if (configuration == null || !configuration.isInclude() || igDataModel.getExampleMessages() == null) {
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
				pages.put(path, staticPage(dataModel.getLabel(null), message.getMessage(), path, linkToGuide));
			}
			for (Map.Entry<String, String> snippetPath : dataModel.getSnippetFilePaths().entrySet()) {
				String snippetId = snippetPath.getKey();
				if (all || dataModel.getReferencedSnippetIds().contains(snippetId)) {
					SnippetRenderResult render = dataModel.getSnippetRender(snippetId);
					String content = render != null && render.getRenderedContent() != null
							? render.getRenderedContent()
							: message.getMessage();
					String path = ExampleMessageExportConfiguration.pagePath(snippetPath.getValue());
					pages.put(path, staticPage(dataModel.getLabel(snippetId), content, path, linkToGuide));
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
					String content = render != null && render.getRenderedContent() != null
							? render.getRenderedContent()
							: message.getMessage();
					files.put(snippetPath.getValue(), bytes(content));
				}
			}
		}
		return files;
	}

	/**
	 * Configuration used by the example-messages feature to dump every message and snippet
	 * as files, independent of a document export.
	 */
	public static ExampleMessageExportConfiguration allFilesConfiguration() {
		ExampleMessageExportConfiguration configuration = new ExampleMessageExportConfiguration();
		configuration.setInclude(true);
		configuration.setExportAllMessages(true);
		configuration.setMode(ExampleMessageExportMode.SEPARATE_FILES);
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

	private static byte[] staticPage(String title, String body, String archivePath, boolean linkToGuide) {
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
				+ "border:1px solid #ccc;}</style></head><body>"
				+ backLink
				+ "<h1>" + safeTitle + "</h1><pre>" + escapeHtml(body) + "</pre></body></html>";
		return bytes(html);
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
