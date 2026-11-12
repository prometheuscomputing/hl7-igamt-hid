package gov.nist.hit.hl7.igamt.serialization.newImplementation.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.domain.MessageSnippet;
import gov.nist.hit.hl7.igamt.examples.dto.SnippetRenderResult;
import gov.nist.hit.hl7.igamt.examples.dto.SnippetRenderResult.SnippetPart;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.ExampleMessageDataModel;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.IgDataModel;
import nu.xom.Attribute;
import nu.xom.Element;

@Service
public class ExampleMessageSerializationServiceImpl implements ExampleMessageSerializationService {

	static final String WIDGET_CLASS = "fr-exmsg";

	@Value("${host.url:http://localhost:9000}")
	private String hostUrl = "http://localhost:9000";
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
		if (snippetFound) {
			element.addAttribute(new Attribute("snippetName", nz(snippet.getName())));
		}
		if (configuration.isIncludeDescription()) {
			String description = descriptionMarkup(snippetFound ? snippet.getDescription() : message.getDescription());
			if (!description.isEmpty()) {
				Element descriptionElement = new Element("Description");
				descriptionElement.appendChild(description);
				element.appendChild(descriptionElement);
			}
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

		boolean files = configuration.isSeparateFiles();
		boolean inline = configuration.isInline();
		if (files && inline) {
			element.addAttribute(new Attribute("mode", "BOTH"));
		} else if (files) {
			element.addAttribute(new Attribute("mode", MODE_FILE));
		} else if (inline) {
			element.addAttribute(new Attribute("mode", MODE_INLINE));
		} else {
			element.addAttribute(new Attribute("mode", "NONE"));
		}
		if (configuration.isRelativeLink() && configuration.producesFiles()) {
			element.addAttribute(new Attribute("messagePage", pageHref(igDataModel, messageId, null)));
			if (snippetFound) {
				element.addAttribute(new Attribute("snippetPage", pageHref(igDataModel, messageId, snippetId)));
			}
		}
		if (configuration.isIgamtLink()) {
			String igamt = igamtUrl(igDataModel, messageId, snippetFound ? snippetId : null);
			if (!igamt.isEmpty()) {
				element.addAttribute(new Attribute("igamtHref", igamt));
			}
		}
		if (inline) {
			Element content = new Element("Content");
			appendLines(content, message, render, snippetFound, configuration.isHighlightSnippet());
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
			MessageSnippet snippet = dataModel.findSnippet(snippetId);
			SnippetRenderResult render = dataModel.getSnippetRender(snippetId);
			boolean snippetFound = snippet != null && render != null;
			dataModel.markReferenced(snippetFound ? snippetId : null);
			boolean snapshotLink = configuration.isRelativeLink() && configuration.producesFiles();
			String messagePage = snapshotLink ? pageHref(igDataModel, messageId, null) : "";
			String snippetPage = snippetFound && snapshotLink
					? pageHref(igDataModel, messageId, snippetId) : "";
			String igamt = configuration.isIgamtLink()
					? igamtUrl(igDataModel, messageId, snippetFound ? snippetId : null) : "";
			appendInfoTable(widget, nz(message.getName()), messagePage,
					snippetFound ? nz(snippet.getName()) : "", snippetPage, igamt);
			if (configuration.isIncludeDescription()) {
				String description = snippetFound ? dataModel.findSnippet(snippetId).getDescription() : message.getDescription();
				appendDescription(widget, description);
			}
			if (configuration.isInline()) {
				org.jsoup.nodes.Element body = widget.appendElement("div").addClass("fr-exmsg-body");
				appendNarrativeLines(body, message, render, snippetFound, configuration.isHighlightSnippet());
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

	private static String highlightedSource(ExampleMessage message, SnippetRenderResult render) {
		if (render != null && render.getFullMessage() != null) {
			return render.getFullMessage();
		}
		return message != null ? message.getMessage() : "";
	}

	private static void appendLines(Element content, ExampleMessage message, SnippetRenderResult render,
			boolean snippetFound, boolean highlight) {
		boolean marked = highlight && snippetFound;
		String text = marked ? highlightedSource(message, render) : contentOf(message, render, snippetFound);
		String[] rows = lines(text);
		List<int[]>[] marks = marked ? marks(rows, render != null ? render.getParts() : null) : null;
		for (int i = 0; i < rows.length; i++) {
			Element line = new Element("Line");
			if (marks != null && marks[i] != null) {
				appendSlices(line, rows[i], marks[i]);
			} else {
				line.appendChild(rows[i]);
			}
			content.appendChild(line);
		}
	}

	private static void appendNarrativeLines(org.jsoup.nodes.Element body, ExampleMessage message,
			SnippetRenderResult render, boolean snippetFound, boolean highlight) {
		boolean marked = highlight && snippetFound;
		String text = marked ? highlightedSource(message, render) : contentOf(message, render, snippetFound);
		String[] rows = lines(text);
		List<int[]>[] marks = marked ? marks(rows, render != null ? render.getParts() : null) : null;
		for (int i = 0; i < rows.length; i++) {
			org.jsoup.nodes.Element line = body.appendElement("div").addClass("fr-exmsg-line");
			if (marks != null && marks[i] != null) {
				appendNarrativeSlices(line, rows[i], marks[i]);
			} else if (rows[i].isEmpty()) {
				line.appendText("\u00a0");
			} else {
				line.text(rows[i]);
			}
		}
	}

	private static void appendSlices(Element line, String row, List<int[]> ranges) {
		int cursor = 0;
		for (int[] range : ranges) {
			if (range[0] > cursor) {
				Element text = new Element("Text");
				text.appendChild(row.substring(cursor, range[0]));
				line.appendChild(text);
			}
			Element mark = new Element("Mark");
			mark.appendChild(row.substring(range[0], range[1]));
			line.appendChild(mark);
			cursor = range[1];
		}
		if (cursor < row.length()) {
			Element text = new Element("Text");
			text.appendChild(row.substring(cursor));
			line.appendChild(text);
		}
	}

	private static void appendNarrativeSlices(org.jsoup.nodes.Element line, String row, List<int[]> ranges) {
		int cursor = 0;
		for (int[] range : ranges) {
			if (range[0] > cursor) {
				line.appendText(row.substring(cursor, range[0]));
			}
			line.appendElement("span").addClass("fr-exmsg-mark").text(row.substring(range[0], range[1]));
			cursor = range[1];
		}
		if (cursor < row.length()) {
			line.appendText(row.substring(cursor));
		}
	}

	/** Columns match the editor: 1-based, end exclusive. */
	@SuppressWarnings("unchecked")
	private static List<int[]>[] marks(String[] rows, List<SnippetPart> parts) {
		List<int[]>[] marks = new List[rows.length];
		if (parts == null) {
			return marks;
		}
		for (SnippetPart part : parts) {
			if (part == null || part.getHighlightStart() == null || part.getHighlightEnd() == null) {
				continue;
			}
			int startLine = part.getStartLine() < 1 ? 1 : part.getStartLine();
			int endLine = part.getEndLine() < startLine ? startLine : part.getEndLine();
			int startCol = part.getHighlightStart().getColumn();
			int endCol = part.getHighlightEnd().getColumn();
			for (int line = startLine; line <= endLine && line <= rows.length; line++) {
				String row = rows[line - 1];
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
		for (List<int[]> ranges : marks) {
			if (ranges == null || ranges.size() < 2) {
				continue;
			}
			Collections.sort(ranges, new Comparator<int[]>() {
				public int compare(int[] left, int[] right) {
					return Integer.compare(left[0], right[0]);
				}
			});
			List<int[]> merged = new ArrayList<int[]>();
			int[] current = ranges.get(0);
			for (int i = 1; i < ranges.size(); i++) {
				int[] next = ranges.get(i);
				if (next[0] <= current[1]) {
					current[1] = Math.max(current[1], next[1]);
				} else {
					merged.add(current);
					current = next;
				}
			}
			merged.add(current);
			ranges.clear();
			ranges.addAll(merged);
		}
		return marks;
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

	private static String descriptionMarkup(String html) {
		if (html == null || html.trim().isEmpty()) {
			return "";
		}
		Document doc = Jsoup.parseBodyFragment(html);
		doc.outputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml);
		doc.select("script, style, iframe, object, form").remove();
		String markup = doc.body().html().replaceAll("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD]", "").trim();
		if (Jsoup.parse(markup).text().trim().isEmpty() && doc.select("img").isEmpty()) {
			return "";
		}
		return markup;
	}

	private static void appendDescription(org.jsoup.nodes.Element parent, String html) {
		String markup = descriptionMarkup(html);
		if (markup.isEmpty()) {
			return;
		}
		parent.appendElement("div").addClass("fr-exmsg-description").html(markup);
	}

	private static Element warning(String text) {
		Element element = new Element("Warning");
		element.appendChild(nz(text));
		return element;
	}

	private static String nz(String value) {
		return value != null ? value : "";
	}

	private static void appendInfoTable(org.jsoup.nodes.Element parent, String messageName, String messagePage,
			String snippetName, String snippetPage, String igamtHref) {
		org.jsoup.nodes.Element table = parent.appendElement("table").addClass("fr-exmsg-info");
		addInfoRow(table, "Message", messageName, messagePage);
		if (snippetName != null && !snippetName.isEmpty()) {
			addInfoRow(table, "Snippet", snippetName, snippetPage);
		}
		if (igamtHref != null && !igamtHref.isEmpty()) {
			addInfoRow(table, "IGAMT", "Open in IGAMT", igamtHref);
		}
	}

	private static void addInfoRow(org.jsoup.nodes.Element table, String label, String value, String href) {
		org.jsoup.nodes.Element row = table.appendElement("tr");
		row.appendElement("th").text(label);
		org.jsoup.nodes.Element cell = row.appendElement("td");
		if (href != null && !href.isEmpty()) {
			cell.appendElement("a").attr("href", href).text(value != null ? value : "");
		} else {
			cell.text(value != null ? value : "");
		}
	}

	private String igamtUrl(IgDataModel igDataModel, String messageId, String snippetId) {
		if (messageId == null || messageId.isEmpty() || igDataModel == null || igDataModel.getModel() == null
				|| igDataModel.getModel().getId() == null) {
			return "";
		}
		String base = hostUrl == null ? "" : hostUrl.trim();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		StringBuilder url = new StringBuilder(base);
		url.append("/example-messages/").append(igDataModel.getModel().getId()).append("/message/").append(messageId);
		if (snippetId != null && !snippetId.isEmpty()) {
			ExampleMessageDataModel dataModel = igDataModel.getExampleMessage(messageId);
			if (dataModel != null && dataModel.findSnippet(snippetId) != null) {
				url.append("/snippet/").append(snippetId);
			}
		}
		return url.toString();
	}

	/**
	 * Link from index.html to the static page for this message. The page lives in the
	 * export folder, so the href is relative and works when the folder is opened as files.
	 */
	private static String pageHref(IgDataModel igDataModel, String messageId, String snippetId) {
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
