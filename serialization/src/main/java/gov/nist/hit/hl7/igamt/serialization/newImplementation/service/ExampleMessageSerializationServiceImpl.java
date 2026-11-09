package gov.nist.hit.hl7.igamt.serialization.newImplementation.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.domain.MessageSnippet;
import gov.nist.hit.hl7.igamt.examples.dto.SnippetRenderResult;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.ExampleMessageDataModel;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.IgDataModel;
import nu.xom.Attribute;
import nu.xom.Element;

@Service
public class ExampleMessageSerializationServiceImpl implements ExampleMessageSerializationService {

	static final String WIDGET_CLASS = "fr-exmsg";
	private static final String MODE_INLINE = "INLINE";
	private static final String MODE_FILE = "SEPARATE_FILES";
	private static final String MODE_MISSING = "MISSING";
	private static final String MISSING_TEXT = "The referenced example message is no longer available.";

	@Override
	public Element serializeReference(IgDataModel igDataModel, String messageId, String snippetId,
			ExampleMessageExportConfiguration configuration) {
		if (configuration == null || !configuration.isInclude() || messageId == null || messageId.isEmpty()) {
			return null;
		}
		Element element = new Element("ExampleMessage");
		element.addAttribute(new Attribute("messageId", messageId));
		element.addAttribute(new Attribute("snippetId", snippetId != null ? snippetId : ""));

		ExampleMessageDataModel dataModel = igDataModel != null ? igDataModel.getExampleMessage(messageId) : null;
		if (dataModel == null || dataModel.getModel() == null) {
			element.addAttribute(new Attribute("mode", MODE_MISSING));
			element.addAttribute(new Attribute("label", "Example message"));
			element.appendChild(warning(MISSING_TEXT));
			return element;
		}

		ExampleMessage message = dataModel.getModel();
		MessageSnippet snippet = dataModel.findSnippet(snippetId);
		SnippetRenderResult render = dataModel.getSnippetRender(snippetId);
		boolean snippetRequested = snippetId != null && !snippetId.isEmpty();
		boolean snippetFound = snippet != null && render != null;

		element.addAttribute(new Attribute("name", nz(message.getName())));
		element.addAttribute(new Attribute("profile", nz(dataModel.getProfileLabel())));
		element.addAttribute(new Attribute("label", dataModel.getLabel(snippetFound ? snippetId : null)));
		element.addAttribute(new Attribute("appHref", appHref(igDataModel, messageId, snippetFound ? snippetId : null)));
		if (snippetFound) {
			element.addAttribute(new Attribute("snippetName", nz(snippet.getName())));
		}
		String description = snippetFound ? snippet.getDescription() : message.getDescription();
		if (description != null && !description.trim().isEmpty()) {
			Element descriptionElement = new Element("Description");
			descriptionElement.appendChild(plainText(description));
			element.appendChild(descriptionElement);
		}
		if (snippetRequested && !snippetFound) {
			element.appendChild(warning("The snippet attached to this section could not be found; the full message is shown."));
		}
		if (snippetFound && render.getWarnings() != null) {
			for (String text : render.getWarnings()) {
				element.appendChild(warning(text));
			}
		}

		dataModel.markReferenced(snippetFound ? snippetId : null);

		if (configuration.isSeparateFiles()) {
			String path = snippetFound ? dataModel.getSnippetFilePath(snippetId) : dataModel.getFilePath();
			element.addAttribute(new Attribute("mode", MODE_FILE));
			element.addAttribute(new Attribute("href", nz(path)));
		} else {
			element.addAttribute(new Attribute("mode", MODE_INLINE));
			Element content = new Element("Content");
			for (String line : lines(contentOf(message, render, snippetFound))) {
				Element lineElement = new Element("Line");
				lineElement.appendChild(line);
				content.appendChild(lineElement);
			}
			element.appendChild(content);
		}
		return element;
	}

	@Override
	public String rewriteNarrative(String html, IgDataModel igDataModel, ExampleMessageExportConfiguration configuration) {
		if (html == null || !html.contains(WIDGET_CLASS)) {
			return html;
		}
		Document doc = Jsoup.parseBodyFragment(html);
		doc.outputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml);
		Elements widgets = doc.select("div." + WIDGET_CLASS);
		if (widgets.isEmpty()) {
			return html;
		}
		for (org.jsoup.nodes.Element widget : widgets) {
			if (configuration == null || !configuration.isInclude()) {
				widget.remove();
				continue;
			}
			String messageId = widget.attr("data-message-id");
			String snippetId = widget.attr("data-snippet-id");
			ExampleMessageDataModel dataModel = igDataModel != null ? igDataModel.getExampleMessage(messageId) : null;
			java.util.List<String> attributeNames = new java.util.ArrayList<String>();
			for (org.jsoup.nodes.Attribute attribute : widget.attributes()) {
				attributeNames.add(attribute.getKey());
			}
			for (String attributeName : attributeNames) {
				widget.removeAttr(attributeName);
			}
			widget.addClass(WIDGET_CLASS);
			widget.empty();
			if (dataModel == null || dataModel.getModel() == null) {
				widget.addClass("fr-exmsg-missing");
				widget.appendElement("span").addClass("fr-exmsg-link").text(MISSING_TEXT);
				continue;
			}
			ExampleMessage message = dataModel.getModel();
			SnippetRenderResult render = dataModel.getSnippetRender(snippetId);
			boolean snippetFound = render != null && dataModel.findSnippet(snippetId) != null;
			String label = "Example: " + dataModel.getLabel(snippetFound ? snippetId : null);
			dataModel.markReferenced(snippetFound ? snippetId : null);
			String appLink = appHref(igDataModel, messageId, snippetFound ? snippetId : null);
			widget.appendElement("a").addClass("fr-exmsg-link").attr("href", appLink).text(label);

			if (configuration.isSeparateFiles()) {
				String path = snippetFound ? dataModel.getSnippetFilePath(snippetId) : dataModel.getFilePath();
				widget.appendElement("span").addClass("fr-exmsg-file").text(nz(path));
			} else {
				org.jsoup.nodes.Element body = widget.appendElement("div").addClass("fr-exmsg-body");
				for (String line : lines(contentOf(message, render, snippetFound))) {
					body.appendElement("div").addClass("fr-exmsg-line").text(line.isEmpty() ? "\u00a0" : line);
				}
			}
			if (snippetId != null && !snippetId.isEmpty() && !snippetFound) {
				widget.appendElement("div").addClass("fr-exmsg-warning")
						.text("The attached snippet could not be found; the full message is shown.");
			} else if (snippetFound && render.getWarnings() != null) {
				for (String text : render.getWarnings()) {
					widget.appendElement("div").addClass("fr-exmsg-warning").text(text);
				}
			}
		}
		return doc.body().html();
	}

	private static String contentOf(ExampleMessage message, SnippetRenderResult render, boolean snippetFound) {
		if (snippetFound && render.getRenderedContent() != null) {
			return render.getRenderedContent();
		}
		return message.getMessage();
	}

	/** Normalizes line endings and drops characters XML cannot carry (MLLP framing, etc.). */
	private static String[] lines(String content) {
		if (content == null) {
			return new String[0];
		}
		String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
		StringBuilder clean = new StringBuilder(normalized.length());
		for (int i = 0; i < normalized.length(); i++) {
			char c = normalized.charAt(i);
			if (c == '\n' || c == '\t' || c >= 0x20) {
				clean.append(c);
			}
		}
		String text = clean.toString();
		if (text.endsWith("\n")) {
			text = text.substring(0, text.length() - 1);
		}
		return text.split("\n", -1);
	}

	private static String plainText(String html) {
		return Jsoup.parse(html).text();
	}

	private static Element warning(String text) {
		Element element = new Element("Warning");
		element.appendChild(nz(text));
		return element;
	}

	private static String nz(String value) {
		return value != null ? value : "";
	}

	/**
	 * Link from index.html to the static page for this message. The page lives in the
	 * export folder, so the href is relative and works when the folder is opened as files.
	 */
	private static String appHref(IgDataModel igDataModel, String messageId, String snippetId) {
		if (messageId == null || messageId.isEmpty() || igDataModel == null) {
			return "";
		}
		ExampleMessageDataModel dataModel = igDataModel.getExampleMessage(messageId);
		if (dataModel == null) {
			return "";
		}
		boolean snippet = snippetId != null && !snippetId.isEmpty() && dataModel.findSnippet(snippetId) != null;
		String filePath = snippet ? dataModel.getSnippetFilePath(snippetId) : dataModel.getFilePath();
		return ExampleMessageExportConfiguration.relativeHref(ExampleMessageExportConfiguration.pagePath(filePath));
	}
}
