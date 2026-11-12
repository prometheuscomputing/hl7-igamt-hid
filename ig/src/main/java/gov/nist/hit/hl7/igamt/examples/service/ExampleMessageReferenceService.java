package gov.nist.hit.hl7.igamt.examples.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import gov.nist.hit.hl7.igamt.common.base.domain.TextSection;
import gov.nist.hit.hl7.igamt.common.base.domain.Type;
import gov.nist.hit.hl7.igamt.examples.dto.ExampleMessageUsage;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;

/**
 * Finds and clears guide references to an example message or one of its snippets.
 * References are message-example sections and narrative widgets.
 */
@Service
public class ExampleMessageReferenceService {

	public static final String KIND_SECTION = "MESSAGE_SECTION";
	public static final String KIND_NARRATIVE = "NARRATIVE";

	public List<ExampleMessageUsage> findUsages(Ig ig, String messageId, String snippetId) {
		List<ExampleMessageUsage> usages = new ArrayList<ExampleMessageUsage>();
		if (ig == null || ig.getContent() == null || messageId == null) {
			return usages;
		}
		for (TextSection section : sorted(ig.getContent())) {
			collect(section, String.valueOf(section.getPosition()), messageId, snippetId, usages);
		}
		return usages;
	}

	/**
	 * @param sectionId limit to one section; null clears every match
	 * @param kind      MESSAGE_SECTION, NARRATIVE, or null for both
	 */
	public void detach(Ig ig, String messageId, String snippetId, String sectionId, String kind) {
		if (ig == null || ig.getContent() == null || messageId == null) {
			return;
		}
		for (TextSection section : ig.getContent()) {
			detach(section, messageId, snippetId, sectionId, kind);
		}
	}

	private void collect(TextSection section, String path, String messageId, String snippetId, List<ExampleMessageUsage> usages) {
		if (section == null) {
			return;
		}
		if (referencesSection(section, messageId, snippetId)) {
			usages.add(usage(section, path, KIND_SECTION));
		}
		if (narrativeUses(section.getDescription(), messageId, snippetId)) {
			usages.add(usage(section, path, KIND_NARRATIVE));
		}
		if (section.getChildren() == null) {
			return;
		}
		for (TextSection child : sorted(section.getChildren())) {
			collect(child, path + "." + child.getPosition(), messageId, snippetId, usages);
		}
	}

	private void detach(TextSection section, String messageId, String snippetId, String sectionId, String kind) {
		if (section == null) {
			return;
		}
		boolean thisSection = sectionId == null || sectionId.isEmpty() || sectionId.equals(section.getId());
		if (thisSection && (kind == null || kind.isEmpty() || KIND_SECTION.equals(kind))
				&& referencesSection(section, messageId, snippetId)) {
			if (snippetId == null || snippetId.isEmpty()) {
				section.setMessageId(null);
				section.setSnippetId(null);
			} else {
				section.setSnippetId(null);
			}
		}
		if (thisSection && (kind == null || kind.isEmpty() || KIND_NARRATIVE.equals(kind))
				&& narrativeUses(section.getDescription(), messageId, snippetId)) {
			section.setDescription(removeWidgets(section.getDescription(), messageId, snippetId));
		}
		if (section.getChildren() != null) {
			for (TextSection child : section.getChildren()) {
				detach(child, messageId, snippetId, sectionId, kind);
			}
		}
	}

	private boolean referencesSection(TextSection section, String messageId, String snippetId) {
		if (!Type.MESSAGESECTION.equals(section.getType()) || !messageId.equals(section.getMessageId())) {
			return false;
		}
		if (snippetId == null || snippetId.isEmpty()) {
			return true;
		}
		return snippetId.equals(section.getSnippetId());
	}

	private ExampleMessageUsage usage(TextSection section, String path, String kind) {
		ExampleMessageUsage usage = new ExampleMessageUsage();
		usage.setSectionId(section.getId());
		usage.setSectionType(section.getType() != null ? section.getType().name() : Type.TEXT.name());
		usage.setPath(path);
		usage.setLabel(section.getLabel() != null ? section.getLabel() : "");
		usage.setKind(kind);
		return usage;
	}

	private List<TextSection> sorted(Set<TextSection> sections) {
		List<TextSection> list = new ArrayList<TextSection>(sections);
		Collections.sort(list);
		return list;
	}

	static boolean narrativeUses(String html, String messageId, String snippetId) {
		return firstMatchingWidget(html, messageId, snippetId) >= 0;
	}

	static String removeWidgets(String html, String messageId, String snippetId) {
		if (html == null || html.isEmpty()) {
			return html;
		}
		StringBuilder out = new StringBuilder();
		int index = 0;
		while (index < html.length()) {
			int start = firstMatchingWidget(html.substring(index), messageId, snippetId);
			if (start < 0) {
				out.append(html.substring(index));
				break;
			}
			start += index;
			out.append(html.substring(index, start));
			int end = endOfDiv(html, start);
			if (end < 0) {
				out.append(html.substring(start));
				break;
			}
			index = end;
		}
		return out.toString();
	}

	private static int firstMatchingWidget(String html, String messageId, String snippetId) {
		if (html == null || messageId == null) {
			return -1;
		}
		String lower = html.toLowerCase(Locale.ROOT);
		int from = 0;
		while (from < html.length()) {
			int start = lower.indexOf("<div", from);
			if (start < 0) {
				return -1;
			}
			int tagEnd = html.indexOf('>', start);
			if (tagEnd < 0) {
				return -1;
			}
			String open = html.substring(start, tagEnd + 1);
			if (isExampleWidget(open) && widgetMatches(open, messageId, snippetId)) {
				return start;
			}
			from = start + 4;
		}
		return -1;
	}

	private static boolean isExampleWidget(String openTag) {
		String classes = attribute(openTag, "class");
		if (classes == null) {
			return false;
		}
		for (String token : classes.split("\\s+")) {
			if ("fr-exmsg".equals(token)) {
				return true;
			}
		}
		return false;
	}

	private static boolean widgetMatches(String openTag, String messageId, String snippetId) {
		if (!messageId.equals(attribute(openTag, "data-message-id"))) {
			return false;
		}
		if (snippetId == null || snippetId.isEmpty()) {
			return true;
		}
		return snippetId.equals(attribute(openTag, "data-snippet-id"));
	}

	private static String attribute(String openTag, String name) {
		String lower = openTag.toLowerCase(Locale.ROOT);
		String needle = name.toLowerCase(Locale.ROOT) + "=";
		int at = lower.indexOf(needle);
		if (at < 0) {
			return null;
		}
		int valueStart = at + needle.length();
		if (valueStart >= openTag.length()) {
			return null;
		}
		char quote = openTag.charAt(valueStart);
		if (quote != '"' && quote != '\'') {
			return null;
		}
		int valueEnd = openTag.indexOf(quote, valueStart + 1);
		if (valueEnd < 0) {
			return null;
		}
		return openTag.substring(valueStart + 1, valueEnd);
	}

	private static int endOfDiv(String html, int start) {
		int depth = 0;
		int index = start;
		String lower = html.toLowerCase(Locale.ROOT);
		while (index < html.length()) {
			int open = lower.indexOf("<div", index);
			int close = lower.indexOf("</div>", index);
			if (close < 0) {
				return -1;
			}
			if (open >= 0 && open < close && isDivOpen(html, open)) {
				depth++;
				int tagEnd = html.indexOf('>', open);
				index = tagEnd < 0 ? open + 4 : tagEnd + 1;
			} else {
				depth--;
				index = close + 6;
				if (depth == 0) {
					return index;
				}
			}
		}
		return -1;
	}

	private static boolean isDivOpen(String html, int at) {
		int next = at + 4;
		if (next >= html.length()) {
			return false;
		}
		char ch = html.charAt(next);
		return ch == '>' || ch == '/' || Character.isWhitespace(ch);
	}
}
