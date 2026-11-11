/**
 *
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 *
 */
package gov.nist.hit.hl7.igamt.export.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

/**
 * Turns the structural HTML produced by the export stylesheets into the finished document.
 * <p>
 * The stylesheets only decide <em>what</em> is exported. This class decides how it reads: it
 * tidies the markup, groups the facts about a resource, decorates the tables (usage, cardinality,
 * message structure, cross references), numbers the sections, builds the navigation and adds the
 * stylesheet of the target format. The same clean-up feeds both targets, so the HTML and the Word
 * exports stay consistent.
 */
public class ExportHtmlEnhancer {

  public enum Target {
    HTML, WORD;

    public static Target fromFormat(String format) {
      return format != null && (format.equalsIgnoreCase("docx") || format.equalsIgnoreCase("word")) ? WORD : HTML;
    }
  }

  private static final String ASSETS = "/templates/assets/";
  private static final String CONTENT_ID = "ig-content";
  /**
   * Font every export configuration starts with. It only says that nobody picked a font, so the
   * document keeps its own typography; any other font is a choice and is applied.
   */
  private static final String UNSET_FONT = "times new roman";
  private static final String UNSET_FONT_SIZE = "12";

  private static final Pattern HEADING = Pattern.compile("h[1-6]");
  private static final Pattern SIMPLE_USAGE = Pattern.compile("R|RE|O|X|C|CE|B|W|IX|CAB|P|E|D");
  private static final Pattern CONDITIONAL_USAGE = Pattern.compile("C\\s*\\(\\s*([A-Z]{1,3})\\s*/\\s*([A-Z]{1,3})\\s*\\)");
  private static final Pattern GROUP_MARKER = Pattern.compile("^([.\\s\\u00a0]*)([\\[\\]])\\s*$");
  private static final Pattern LEADING_DOTS = Pattern.compile("^(\\.+)(\\S.*)$");
  private static final Pattern REFERENCE_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.\\-]*");

  private static final Map<String, String> KIND_LABELS = new LinkedHashMap<String, String>();
  private static final Map<String, String> REGISTRY_OF = new HashMap<String, String>();
  private static final Map<String, String> COLUMN_TYPES = new HashMap<String, String>();
  static {
    KIND_LABELS.put("ConformanceProfile", "Conformance profile");
    KIND_LABELS.put("CompositeProfile", "Composite profile");
    KIND_LABELS.put("ProfileComponent", "Profile component");
    KIND_LABELS.put("Segment", "Segment");
    KIND_LABELS.put("Datatype", "Data type");
    KIND_LABELS.put("Valueset", "Value set");
    KIND_LABELS.put("CoConstraintGroup", "Co-constraint group");

    REGISTRY_OF.put("CONFORMANCEPROFILEREGISTRY", "ConformanceProfile");
    REGISTRY_OF.put("COMPOSITEPROFILEREGISTRY", "CompositeProfile");
    REGISTRY_OF.put("PROFILECOMPONENTREGISTRY", "ProfileComponent");
    REGISTRY_OF.put("SEGMENTREGISTRY", "Segment");
    REGISTRY_OF.put("DATATYPEREGISTRY", "Datatype");
    REGISTRY_OF.put("VALUESETREGISTRY", "Valueset");
    REGISTRY_OF.put("COCONSTRAINTGROUPREGISTRY", "CoConstraintGroup");

    COLUMN_TYPES.put("usage", "usage");
    COLUMN_TYPES.put("cardinality", "card");
    COLUMN_TYPES.put("data type", "datatype");
    COLUMN_TYPES.put("datatype", "datatype");
    COLUMN_TYPES.put("segment", "segment");
    COLUMN_TYPES.put("flavor", "flavor");
    COLUMN_TYPES.put("value set", "valueset");
    COLUMN_TYPES.put("value sets", "valueset");
    COLUMN_TYPES.put("value set id", "valueset");
    COLUMN_TYPES.put("path", "path");
    COLUMN_TYPES.put("seq", "seq");
    COLUMN_TYPES.put("#", "seq");
    COLUMN_TYPES.put("position", "seq");
    COLUMN_TYPES.put("location", "path");
    COLUMN_TYPES.put("binding location", "path");
    COLUMN_TYPES.put("element name", "name");
    COLUMN_TYPES.put("name", "name");
    COLUMN_TYPES.put("value", "code");
    COLUMN_TYPES.put("code", "code");
    COLUMN_TYPES.put("code system", "codesystem");
    COLUMN_TYPES.put("binding strength", "strength");
    COLUMN_TYPES.put("strength", "strength");
    COLUMN_TYPES.put("length", "length");
    COLUMN_TYPES.put("conformance length", "length");
    COLUMN_TYPES.put("conf length", "length");
    COLUMN_TYPES.put("conf. length", "length");
    COLUMN_TYPES.put("constant value", "code");
    COLUMN_TYPES.put("format", "code");
    COLUMN_TYPES.put("id", "path");
    COLUMN_TYPES.put("description", "text");
    COLUMN_TYPES.put("comment", "text");
    COLUMN_TYPES.put("definition text", "text");
    COLUMN_TYPES.put("definition", "text");
    COLUMN_TYPES.put("true usage", "usage");
    COLUMN_TYPES.put("false usage", "usage");
    COLUMN_TYPES.put("comments", "text");
    COLUMN_TYPES.put("assertion", "text");
    COLUMN_TYPES.put("occurrence", "seq");
  }

  private final Target target;
  private final Document doc;
  private final Element content;
  /** kind -> (code -> anchor) of every resource documented in the export */
  private final Map<String, Map<String, String>> index = new HashMap<String, Map<String, String>>();
  private final List<Heading> outline = new ArrayList<Heading>();

  private static class Heading {
    Element element;
    int level;
    String id;
    String number;
    String kind;
    String code;
    String title;
    List<Heading> children = new ArrayList<Heading>();
  }

  private ExportHtmlEnhancer(Document doc, Target target) {
    this.doc = doc;
    this.target = target;
    Element root = doc.getElementById(CONTENT_ID);
    this.content = root != null ? root : doc.body();
  }

  /**
   * @param rawHtml output of the export stylesheet
   * @param target format the document is being produced for
   * @return the finished document: HTML5 for {@link Target#HTML}, well formed XHTML ready for the
   *         Word importer for {@link Target#WORD}
   */
  public static String enhance(InputStream rawHtml, Target target) throws IOException {
    Document doc = Jsoup.parse(rawHtml, "UTF-8", "");
    ExportHtmlEnhancer enhancer = new ExportHtmlEnhancer(doc, target);
    enhancer.run();
    return enhancer.serialize();
  }

  private void run() {
    normalizeHeadings();
    normalizeNarratives();
    flattenWrappers(content);
    normalizeFlow(content);
    collectOutline();
    decorateTables();
    if (target == Target.HTML) {
      renderHeadings();
      wrapSections();
      buildShell();
    } else {
      prepareForWord();
    }
    addStyles();
  }

  // ---------------------------------------------------------------- headings

  /** Older templates still write {@code <h3><u id="..." class="section3">title</u></h3>}. */
  private void normalizeHeadings() {
    for (Element underline : doc.select("h1 > u, h2 > u, h3 > u, h4 > u, h5 > u, h6 > u")) {
      Element heading = underline.parent();
      if (underline.hasAttr("id") && !heading.hasAttr("id")) {
        heading.attr("id", underline.attr("id"));
      }
      underline.unwrap();
    }
  }

  private void collectOutline() {
    int[] counters = new int[7];
    Heading[] open = new Heading[7];
    String[] registryKind = new String[7];
    for (Element element : content.select("h1, h2, h3, h4, h5, h6")) {
      if (isInNarrative(element)) {
        continue;
      }
      int level = element.tagName().charAt(1) - '0';
      Heading heading = new Heading();
      heading.element = element;
      heading.level = level;
      heading.title = element.text().trim();
      heading.kind = element.attr("data-kind");
      heading.code = element.attr("data-code").trim();
      if (!element.hasAttr("id") || element.attr("id").trim().isEmpty()) {
        element.attr("id", "ig-section-" + (outline.size() + 1));
      }
      heading.id = element.attr("id");

      // A resource listed under a registry inherits the registry's kind when the template did not say
      for (int i = level; i < registryKind.length; i++) {
        registryKind[i] = null;
      }
      if (REGISTRY_OF.containsKey(heading.kind)) {
        registryKind[level] = REGISTRY_OF.get(heading.kind);
      } else if (!KIND_LABELS.containsKey(heading.kind)) {
        // only the sections right under a registry are its resources; deeper ones are their parts
        String inherited = level > 1 ? registryKind[level - 1] : null;
        if ("DATATYPEGROUP".equals(heading.kind)) {
          registryKind[level] = inherited;
        } else {
          heading.kind = inherited != null ? inherited : "";
          if (inherited == null) {
            heading.code = "";
          }
        }
      }
      if (KIND_LABELS.containsKey(heading.kind) && heading.code.isEmpty()) {
        int dash = heading.title.indexOf('-');
        heading.code = dash > 0 ? heading.title.substring(0, dash).trim() : heading.title;
      }

      counters[level]++;
      for (int i = level + 1; i < counters.length; i++) {
        counters[i] = 0;
        open[i] = null;
      }
      StringBuilder number = new StringBuilder();
      for (int i = 1; i <= level; i++) {
        if (number.length() > 0) {
          number.append('.');
        }
        number.append(Math.max(counters[i], 1));
      }
      heading.number = number.toString();
      open[level] = heading;
      Heading parent = null;
      for (int i = level - 1; i > 0 && parent == null; i--) {
        parent = open[i];
      }
      if (parent != null) {
        parent.children.add(heading);
      }
      outline.add(heading);

      if (KIND_LABELS.containsKey(heading.kind) && !heading.code.isEmpty()) {
        Map<String, String> byCode = index.get(heading.kind);
        if (byCode == null) {
          byCode = new HashMap<String, String>();
          index.put(heading.kind, byCode);
        }
        if (!byCode.containsKey(heading.code)) {
          byCode.put(heading.code, heading.id);
        }
      }
    }
  }

  /** Name of a resource without the code that prefixes it in the section title. */
  private static String resourceName(Heading heading) {
    String title = heading.title;
    if (!heading.code.isEmpty() && title.startsWith(heading.code)) {
      String rest = title.substring(heading.code.length()).trim();
      if (rest.startsWith("-")) {
        rest = rest.substring(1).trim();
      }
      return rest;
    }
    return title;
  }

  private void renderHeadings() {
    for (Heading heading : outline) {
      Element element = heading.element;
      boolean resource = KIND_LABELS.containsKey(heading.kind);
      Element master = element.select("span.masterDatatypeLabel").first();
      element.empty();
      element.addClass("ig-heading");
      element.appendElement("span").addClass("ig-num").text(heading.number);
      if (resource) {
        element.addClass("ig-resource-heading");
        if (master != null) {
          element.appendElement("span").addClass("ig-master").text("MAS");
        }
        element.appendElement("span").addClass("ig-code").text(heading.code);
        String name = resourceName(heading);
        if (!name.isEmpty()) {
          element.appendElement("span").addClass("ig-rname").text(name);
        }
        element.appendElement("span").addClass("ig-kind").text(KIND_LABELS.get(heading.kind));
      } else {
        element.appendElement("span").addClass("ig-htitle").text(heading.title);
      }
      element.appendElement("a").addClass("ig-anchor").attr("href", "#" + heading.id)
          .attr("aria-label", "Link to this section").attr("title", "Copy link to this section").text("#");
    }
  }

  // ---------------------------------------------------------------- narrative

  private static boolean isInNarrative(Element element) {
    for (Element parent : element.parents()) {
      if (parent.hasClass("fr-view") || parent.hasClass("ig-narrative")) {
        return true;
      }
    }
    return element.hasClass("fr-view") || element.hasClass("ig-narrative");
  }

  /**
   * Rich text reaches the export as {@code <div class="fr-view">} blocks where every paragraph was
   * turned into a div that opens with a line break, and headings into paragraphs with inline
   * sizes. Bring that back to plain paragraphs and classed headings.
   */
  private void normalizeNarratives() {
    for (Element view : doc.select("div.fr-view")) {
      for (Element block : view.select("div")) {
        Node first = firstMeaningfulChild(block);
        if (first instanceof Element && ((Element) first).tagName().equals("br")) {
          first.remove();
        }
      }
      for (Element heading : view.select("p[style*=font-weight: bold][style*=font-size]")) {
        String style = heading.attr("style");
        String level = style.contains("16.0pt") ? "1" : style.contains("14.0pt") ? "2" : style.contains("12.0pt") ? "3" : "4";
        heading.removeAttr("style");
        heading.addClass("ig-nh").addClass("ig-nh" + level);
      }
      for (Element table : view.select("table")) {
        if (".".equals(table.attr("summary"))) {
          table.removeAttr("summary");
        }
      }
      for (Element image : view.select("img")) {
        if (".".equals(image.attr("alt"))) {
          image.attr("alt", "");
        }
      }
      for (Element block : view.select("div")) {
        if (block.children().isEmpty() && block.text().trim().isEmpty() && block != view) {
          block.remove();
        }
      }
    }
  }

  private static Node firstMeaningfulChild(Element element) {
    for (Node child : element.childNodes()) {
      if (child instanceof TextNode && ((TextNode) child).isBlank()) {
        continue;
      }
      return child;
    }
    return null;
  }

  // ---------------------------------------------------------------- flow

  private static boolean isBlock(Element element) {
    String tag = element.tagName();
    return tag.equals("table") || tag.equals("div") || tag.equals("ul") || tag.equals("ol") || tag.equals("p")
        || tag.equals("dl") || tag.equals("pre") || tag.equals("blockquote") || HEADING.matcher(tag).matches();
  }

  private static boolean containsBlock(Element element) {
    for (Element descendant : element.getAllElements()) {
      if (descendant != element && isBlock(descendant)) {
        return true;
      }
    }
    return false;
  }

  /** Spans wrapped around tables and other blocks only get in the way; lift their content. */
  private void flattenWrappers(Element root) {
    boolean changed = true;
    while (changed) {
      changed = false;
      for (Element child : new ArrayList<Element>(root.children())) {
        if (child.tagName().equals("span") && !isInNarrative(child) && containsBlock(child)) {
          child.unwrap();
          changed = true;
        }
      }
    }
  }

  /** The bold label a span or paragraph opens with, or null when it does not open with one. */
  private static Element labelOf(Element element) {
    Element current = element;
    while (true) {
      Node first = firstMeaningfulChild(current);
      if (!(first instanceof Element)) {
        return null;
      }
      Element firstElement = (Element) first;
      if (firstElement.tagName().equals("b") || firstElement.tagName().equals("strong")) {
        return firstElement.text().trim().isEmpty() ? null : firstElement;
      }
      if (!firstElement.tagName().equals("span")) {
        return null;
      }
      current = firstElement;
    }
  }

  private static String cleanLabel(String text) {
    String label = text.replace(' ', ' ').trim();
    while (label.endsWith(":")) {
      label = label.substring(0, label.length() - 1).trim();
    }
    return label;
  }

  private static boolean endsWithColon(String text) {
    return text.replace(' ', ' ').trim().endsWith(":");
  }

  /**
   * The resource templates write their content as a flat run of bold labels, text, line breaks
   * and tables. Rebuild it as: titled blocks, a list of facts, notes, and tables.
   */
  private void normalizeFlow(Element root) {
    List<Node> nodes = new ArrayList<Node>(root.childNodes());
    List<Node> out = new ArrayList<Node>();
    Element facts = null;

    for (int i = 0; i < nodes.size(); i++) {
      Node node = nodes.get(i);
      if (node instanceof TextNode) {
        TextNode text = (TextNode) node;
        if (!text.isBlank()) {
          Element paragraph = doc.createElement("p");
          paragraph.text(text.text().trim());
          out.add(paragraph);
          facts = null;
        }
        continue;
      }
      if (!(node instanceof Element)) {
        continue;
      }
      Element element = (Element) node;
      String tag = element.tagName();

      if (tag.equals("br") || tag.equals("hr")) {
        continue;
      }

      // <b>Label :</b> value <br/>
      if ((tag.equals("b") || tag.equals("strong")) && endsWithColon(element.text())) {
        List<Node> value = new ArrayList<Node>();
        int j = i + 1;
        while (j < nodes.size()) {
          Node next = nodes.get(j);
          if (next instanceof Element) {
            Element nextElement = (Element) next;
            String nextTag = nextElement.tagName();
            if (nextTag.equals("br")) {
              j++;
              break;
            }
            boolean nextLabel = (nextTag.equals("b") || nextTag.equals("strong")) && endsWithColon(nextElement.text());
            if (nextLabel || nextTag.equals("table") || HEADING.matcher(nextTag).matches()
                || (nextTag.equals("span") && labelOf(nextElement) != null)) {
              break;
            }
          }
          value.add(next);
          j++;
        }
        i = j - 1;
        facts = addLabelled(out, facts, cleanLabel(element.text()), value);
        continue;
      }

      if ((tag.equals("span") || tag.equals("p")) && !isInNarrative(element)) {
        Element label = labelOf(element);
        if (label != null) {
          String labelText = label.text();
          boolean onlyLabel = element.text().trim().equals(labelText.trim());
          if (onlyLabel && !containsBlock(element)) {
            out.add(subhead(cleanLabel(labelText)));
            facts = null;
            continue;
          }
          if (endsWithColon(labelText)) {
            Element holder = label;
            while (holder.parent() != element && holder.parent() != null
                && holder.parent().text().trim().equals(labelText.trim())) {
              holder = holder.parent();
            }
            List<Node> value = new ArrayList<Node>();
            collectAfter(element, holder, value);
            facts = addLabelled(out, facts, cleanLabel(labelText), value);
            continue;
          }
        }
      }

      facts = null;
      out.add(element);
    }

    // Only detach what is still a direct child: values may already have moved into a note or a list.
    for (Node node : nodes) {
      if (node.parent() == root) {
        node.remove();
      }
    }
    for (Node node : out) {
      root.appendChild(node);
    }
  }

  /** Everything that follows {@code marker} inside {@code container}, in document order. */
  private static void collectAfter(Element container, Element marker, List<Node> into) {
    Node current = marker;
    while (current != null && current != container) {
      Node sibling = current.nextSibling();
      while (sibling != null) {
        into.add(sibling);
        sibling = sibling.nextSibling();
      }
      current = current.parent();
    }
  }

  private Element subhead(String title) {
    Element subhead = doc.createElement("p");
    subhead.addClass("ig-subhead").text(title);
    return subhead;
  }

  /** Adds "label: value" either to the running list of facts or, when it is long, as a note. */
  private Element addLabelled(List<Node> out, Element facts, String label, List<Node> value) {
    boolean block = false;
    StringBuilder text = new StringBuilder();
    for (Node node : value) {
      if (node instanceof Element) {
        Element element = (Element) node;
        if (isBlock(element) || containsBlock(element)) {
          block = true;
        }
        text.append(element.text());
      } else if (node instanceof TextNode) {
        text.append(((TextNode) node).text());
      }
    }
    String plain = text.toString().replace(' ', ' ').trim();
    if (plain.isEmpty()) {
      return facts;
    }
    if (!block && plain.length() <= 90) {
      if (target == Target.WORD) {
        Element line = doc.createElement("p");
        line.addClass("ig-meta-line");
        line.appendElement("b").text(label + ": ");
        line.appendText(plain);
        out.add(line);
        return null;
      }
      Element list = facts;
      if (list == null) {
        list = doc.createElement("dl");
        list.addClass("ig-meta");
        out.add(list);
      }
      Element item = list.appendElement("div").addClass("ig-meta-item");
      item.appendElement("dt").text(label);
      item.appendElement("dd").text(plain);
      return list;
    }
    Element note = doc.createElement("div");
    note.addClass("ig-note");
    note.appendChild(subhead(label));
    Element body = note.appendElement("div").addClass("ig-note-body");
    for (Node node : value) {
      if (node instanceof Element && ((Element) node).tagName().equals("br") && body.childNodeSize() == 0) {
        continue;
      }
      body.appendChild(node);
    }
    out.add(note);
    return null;
  }

  // ---------------------------------------------------------------- tables

  private void decorateTables() {
    for (Element table : content.select("table")) {
      if (isInNarrative(table)) {
        continue;
      }
      table.select("col, colgroup").remove();
      table.removeAttr("summary");
      table.addClass("ig-table");
      for (Element row : table.select("tr[style]")) {
        if (row.attr("style").replace(" ", "").contains("background-color:white")) {
          row.removeAttr("style");
        }
      }

      List<Element> headerCells = headerCells(table);
      if (!headerCells.isEmpty() && bodyRows(table).isEmpty()) {
        // Every row was filtered out by the export configuration: say so instead of showing a bare header
        Element empty = doc.createElement("p").addClass("ig-empty").text("Nothing to list with the current export settings.");
        table.replaceWith(empty);
        continue;
      }
      boolean regular = !headerCells.isEmpty() && table.select("[colspan], [rowspan]").isEmpty();
      if (regular) {
        List<String> types = new ArrayList<String>();
        for (Element headerCell : headerCells) {
          String key = headerCell.text().replace(' ', ' ').trim().toLowerCase(Locale.ENGLISH);
          String type = COLUMN_TYPES.get(key);
          types.add(type);
          if (type != null) {
            headerCell.addClass("c-" + type);
          }
        }
        // "Value" names a code in a code table, and a part of the value elsewhere (date and time formats)
        if (!types.contains("codesystem")) {
          for (int i = 0; i < types.size(); i++) {
            if ("code".equals(types.get(i)) && "value".equals(headerCells.get(i).text().trim().toLowerCase(Locale.ENGLISH))) {
              types.set(i, "name");
              headerCells.get(i).removeClass("c-code").addClass("c-name");
            }
          }
        }
        boolean structure = types.contains("segment") && types.contains("flavor");
        if (structure) {
          table.addClass("ig-structure");
        }
        for (Element row : bodyRows(table)) {
          Elements cells = row.children();
          if (cells.size() != types.size()) {
            continue;
          }
          for (int i = 0; i < cells.size(); i++) {
            Element cell = cells.get(i);
            String type = types.get(i);
            if (type != null) {
              cell.addClass("c-" + type);
              decorateCell(cell, type, structure, row);
            }
          }
        }
      } else if (!table.select("[colspan], [rowspan]").isEmpty()) {
        table.addClass("ig-grid");
        decorateGrid(table);
      }
      Elements notApplicable = table.select("td.greyCell, th.greyCell");
      for (Element cell : notApplicable) {
        cell.addClass("ig-na");
      }
      // A table that is mostly "not applicable" (a profile component only lists what it changes)
      // reads better with plain empty cells than with a wall of hatching
      if (notApplicable.size() * 5 >= table.select("tbody td, tbody th").size() * 2) {
        table.addClass("ig-sparse");
      }
      if (target == Target.HTML) {
        Element wrapper = doc.createElement("div").addClass("ig-table-wrap");
        if (headerCells.size() >= 9) {
          wrapper.addClass("ig-wide");
        }
        table.replaceWith(wrapper);
        wrapper.appendChild(table);
      }
    }
  }

  /** Co-constraint tables: usage chips in the usage column, links on the value sets and datatypes. */
  private void decorateGrid(Element table) {
    int usageColumn = -1;
    List<Element> headerCells = headerCells(table);
    for (int i = 0; i < headerCells.size(); i++) {
      if ("usage".equals(headerCells.get(i).text().trim().toLowerCase(Locale.ENGLISH))) {
        usageColumn = i;
        break;
      }
    }
    if (usageColumn >= 0) {
      for (Element row : table.select("tbody > tr")) {
        Elements cells = row.children();
        if (cells.size() <= usageColumn) {
          continue;
        }
        Element cell = cells.get(usageColumn);
        Element chip = usageChip(cell.text().trim());
        if (chip != null) {
          cell.empty();
          cell.appendChild(chip);
          cell.addClass("c-usage");
        }
      }
    }
    for (Element valueSet : table.select("span.cc-valueset")) {
      link(valueSet, "Valueset");
    }
    for (Element datatype : table.select("span.cc-datatype")) {
      link(datatype, "Datatype");
    }
  }

  private static List<Element> headerCells(Element table) {
    for (Element child : table.children()) {
      if (child.tagName().equals("thead")) {
        for (Element row : child.children()) {
          if (row.tagName().equals("tr")) {
            return new ArrayList<Element>(row.children());
          }
        }
      }
    }
    return new ArrayList<Element>();
  }

  private static List<Element> bodyRows(Element table) {
    List<Element> rows = new ArrayList<Element>();
    for (Element child : table.children()) {
      if (child.tagName().equals("tbody")) {
        for (Element row : child.children()) {
          if (row.tagName().equals("tr")) {
            rows.add(row);
          }
        }
      }
    }
    return rows;
  }

  private void decorateCell(Element cell, String type, boolean structure, Element row) {
    boolean plain = cell.children().isEmpty() || (cell.children().size() == 1 && cell.child(0).tagName().equals("a")
        && cell.child(0).children().isEmpty());
    String text = cell.text().replace(' ', ' ').trim();
    if ("usage".equals(type)) {
      if (plain) {
        Element chip = usageChip(text);
        if (chip != null) {
          cell.empty().appendChild(chip);
        }
      }
    } else if ("strength".equals(type)) {
      if (plain && !text.isEmpty()) {
        cell.empty().appendElement("span").addClass("ig-strength").addClass("s-" + text.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z]", ""))
            .text(text);
      }
    } else if ("segment".equals(type) && structure) {
      decorateStructureCell(cell, text, row);
    } else if ("flavor".equals(type)) {
      link(cell, "Segment");
    } else if ("datatype".equals(type)) {
      link(cell, "Datatype");
    } else if ("valueset".equals(type)) {
      link(cell, "Valueset");
    }
  }

  /** A usage code as a chip; null when the text is not a usage code. */
  private Element usageChip(String text) {
    String code = text.replace(" ", "");
    if (SIMPLE_USAGE.matcher(code).matches()) {
      return doc.createElement("span").addClass("ig-usage").addClass("u-" + code.toLowerCase(Locale.ENGLISH)).text(code);
    }
    Matcher conditional = CONDITIONAL_USAGE.matcher(text);
    if (conditional.matches()) {
      Element chip = doc.createElement("span").addClass("ig-usage").addClass("u-c").addClass("ig-usage-conditional");
      chip.appendElement("span").addClass("ig-usage-c").text("C");
      chip.appendElement("span").addClass("ig-usage-branches").text(conditional.group(1) + "/" + conditional.group(2));
      chip.attr("title", "Conditional: " + conditional.group(1) + " when the condition is true, " + conditional.group(2) + " otherwise");
      return chip;
    }
    return null;
  }

  /**
   * The message structure marks nesting with leading dots ("........PID") and groups with rows
   * that only hold a bracket. Replace the dots with a real indent and tag the group rows.
   */
  private void decorateStructureCell(Element cell, String text, Element row) {
    Matcher group = GROUP_MARKER.matcher(text);
    int depth;
    if (group.matches()) {
      depth = group.group(1).replaceAll("[^.]", "").length() / 4;
      boolean begin = group.group(2).equals("[");
      row.addClass(begin ? "ig-group-begin" : "ig-group-end");
      cell.empty().appendElement("span").addClass("ig-bracket").text(begin ? "[" : "]");
      // "BEGIN PATIENT GROUP" / "END PATIENT GROUP" carry the group name
      for (Element sibling : row.children()) {
        String siblingText = sibling.text().trim();
        if (sibling != cell && sibling.children().isEmpty() && (siblingText.startsWith("BEGIN ") || siblingText.startsWith("END "))
            && siblingText.endsWith(" GROUP")) {
          String name = siblingText.substring(siblingText.indexOf(' ') + 1, siblingText.length() - " GROUP".length());
          sibling.empty();
          sibling.appendElement("span").addClass("ig-group-word").text(begin ? "Group" : "End of group");
          sibling.appendText(" ");
          sibling.appendElement("span").addClass("ig-group-name").text(name);
        }
      }
    } else {
      Matcher dots = LEADING_DOTS.matcher(text);
      if (dots.matches()) {
        depth = dots.group(1).length() / 4;
        cell.empty().appendElement("span").addClass("ig-seg").text(dots.group(2));
      } else {
        depth = 0;
        if (cell.children().isEmpty() && !text.isEmpty()) {
          cell.empty().appendElement("span").addClass("ig-seg").text(text);
        }
      }
    }
    row.attr("data-depth", String.valueOf(depth));
    if (depth > 0) {
      if (target == Target.WORD) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth * 3; i++) {
          indent.append(' ');
        }
        cell.prependText(indent.toString());
      } else {
        cell.attr("style", "--ig-depth:" + depth);
      }
    }
  }

  /** Turns the resource codes a cell lists into links to their definition in the document. */
  private void link(Element cell, String kind) {
    Map<String, String> byCode = index.get(kind);
    if (byCode == null || byCode.isEmpty()) {
      return;
    }
    for (TextNode textNode : textNodesOf(cell)) {
      if (textNode.parent() instanceof Element && ((Element) textNode.parent()).tagName().equals("a")
          && ((Element) textNode.parent()).hasAttr("href")) {
        continue;
      }
      String text = textNode.text();
      Matcher matcher = REFERENCE_TOKEN.matcher(text);
      List<Node> replacement = new ArrayList<Node>();
      int last = 0;
      boolean linked = false;
      while (matcher.find()) {
        String anchor = byCode.get(matcher.group());
        if (anchor == null) {
          continue;
        }
        if (matcher.start() > last) {
          replacement.add(new TextNode(text.substring(last, matcher.start()), ""));
        }
        Element reference = doc.createElement("a").addClass("ig-ref").attr("href", "#" + anchor).text(matcher.group());
        replacement.add(reference);
        last = matcher.end();
        linked = true;
      }
      if (!linked) {
        continue;
      }
      if (last < text.length()) {
        replacement.add(new TextNode(text.substring(last), ""));
      }
      for (Node node : replacement) {
        textNode.before(node);
      }
      textNode.remove();
    }
  }

  private static List<TextNode> textNodesOf(Element element) {
    List<TextNode> nodes = new ArrayList<TextNode>();
    for (Node child : element.childNodes()) {
      if (child instanceof TextNode) {
        nodes.add((TextNode) child);
      } else if (child instanceof Element) {
        nodes.addAll(textNodesOf((Element) child));
      }
    }
    return nodes;
  }

  // ---------------------------------------------------------------- HTML shell

  /** Nests each heading with what follows it, so a section can be styled and collapsed as a unit. */
  private void wrapSections() {
    List<Node> nodes = new ArrayList<Node>(content.childNodes());
    for (Node node : nodes) {
      node.remove();
    }
    Element[] open = new Element[7];
    for (Node node : nodes) {
      int level = 0;
      if (node instanceof Element && HEADING.matcher(((Element) node).tagName()).matches()) {
        level = ((Element) node).tagName().charAt(1) - '0';
      }
      if (level > 0) {
        Element heading = (Element) node;
        Element parent = content;
        for (int i = level - 1; i > 0; i--) {
          if (open[i] != null) {
            parent = open[i];
            break;
          }
        }
        for (int i = level; i < open.length; i++) {
          open[i] = null;
        }
        Element section = parent.appendElement("section").addClass("ig-section").addClass("ig-level-" + level);
        String kind = heading.attr("data-kind");
        for (Heading entry : outline) {
          if (entry.element == heading) {
            kind = entry.kind;
            break;
          }
        }
        if (!kind.isEmpty()) {
          section.attr("data-kind", kind);
          if (KIND_LABELS.containsKey(kind)) {
            section.addClass("ig-resource");
          }
        }
        section.appendChild(heading);
        open[level] = section;
      } else {
        Element parent = content;
        for (int i = open.length - 1; i > 0; i--) {
          if (open[i] != null) {
            parent = open[i];
            break;
          }
        }
        parent.appendChild(node);
      }
    }
  }

  private void buildShell() {
    Element body = doc.body();
    Element cover = doc.getElementById("ig-cover");
    String title = cover != null && cover.select(".ig-title").first() != null ? cover.select(".ig-title").first().text() : doc.title();
    doc.title(title);

    if (cover != null) {
      Element inventory = inventory();
      if (inventory != null) {
        cover.appendChild(inventory);
      }
    }

    Element app = doc.createElement("div").addClass("ig-app");
    Element topbar = app.appendElement("header").addClass("ig-topbar");
    topbar.appendElement("button").attr("type", "button").attr("id", "ig-nav-toggle").addClass("ig-icon-button")
        .attr("aria-label", "Show or hide the table of contents").attr("title", "Table of contents")
        .html("<svg viewBox=\"0 0 20 20\" width=\"18\" height=\"18\" aria-hidden=\"true\"><path d=\"M3 5h14M3 10h14M3 15h14\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\"/></svg>");
    topbar.appendElement("a").addClass("ig-topbar-title").attr("href", "#ig-top").text(title);
    Element search = topbar.appendElement("div").addClass("ig-search");
    search.appendElement("input").attr("type", "search").attr("id", "ig-search").attr("autocomplete", "off")
        .attr("spellcheck", "false").attr("placeholder", "Search sections, segments, data types, value sets")
        .attr("aria-label", "Search this document");
    search.appendElement("kbd").text("/");
    search.appendElement("div").attr("id", "ig-search-results").addClass("ig-search-results").attr("hidden", "hidden");
    Element actions = topbar.appendElement("div").addClass("ig-actions");
    actions.appendElement("button").attr("type", "button").attr("id", "ig-theme").addClass("ig-text-button").text("Dark");
    actions.appendElement("button").attr("type", "button").attr("id", "ig-print").addClass("ig-text-button").text("Print");

    Element sidebar = app.appendElement("aside").addClass("ig-sidebar").attr("id", "ig-sidebar");
    Element sidebarHead = sidebar.appendElement("div").addClass("ig-sidebar-head");
    sidebarHead.appendElement("span").addClass("ig-sidebar-title").text("Contents");
    sidebarHead.appendElement("button").attr("type", "button").attr("id", "ig-toc-collapse").addClass("ig-text-button")
        .text("Collapse all");
    Element nav = sidebar.appendElement("nav").attr("aria-label", "Table of contents");
    List<Heading> roots = new ArrayList<Heading>();
    for (Heading heading : outline) {
      boolean child = false;
      for (Heading other : outline) {
        if (other.children.contains(heading)) {
          child = true;
          break;
        }
      }
      if (!child) {
        roots.add(heading);
      }
    }
    nav.appendChild(tocList(roots));

    Element main = app.appendElement("main").addClass("ig-main").attr("id", "ig-main");
    main.appendElement("a").attr("id", "ig-top");
    for (Node node : new ArrayList<Node>(body.childNodes())) {
      main.appendChild(node);
    }
    Element footer = main.appendElement("footer").addClass("ig-footer");
    footer.appendElement("span").text("Exported " + new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).format(new Date()) + " with IGAMT");
    body.appendChild(app);
    body.appendElement("button").attr("type", "button").attr("id", "ig-to-top").addClass("ig-to-top")
        .attr("aria-label", "Back to top").attr("hidden", "hidden").text("↑");

    Element head = doc.head();
    head.prependElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1");
    head.prependElement("meta").attr("charset", "utf-8");
    head.appendElement("meta").attr("name", "generator").attr("content", "IGAMT");
    body.appendElement("script").appendChild(new org.jsoup.nodes.DataNode(asset("ig-export.js"), ""));
  }

  /** What the document holds, as links to the matching registries. */
  private Element inventory() {
    Map<String, int[]> counts = new LinkedHashMap<String, int[]>();
    Map<String, String> anchors = new HashMap<String, String>();
    for (String kind : KIND_LABELS.keySet()) {
      counts.put(kind, new int[] {0});
    }
    for (Heading heading : outline) {
      if (REGISTRY_OF.containsKey(heading.kind)) {
        anchors.put(REGISTRY_OF.get(heading.kind), heading.id);
      } else if (counts.containsKey(heading.kind)) {
        counts.get(heading.kind)[0]++;
      }
    }
    Element list = doc.createElement("ul").addClass("ig-inventory");
    for (Map.Entry<String, int[]> entry : counts.entrySet()) {
      int count = entry.getValue()[0];
      if (count == 0) {
        continue;
      }
      String label = KIND_LABELS.get(entry.getKey()).toLowerCase(Locale.ENGLISH) + (count == 1 ? "" : "s");
      Element item = list.appendElement("li");
      Element holder = anchors.containsKey(entry.getKey()) ? item.appendElement("a").attr("href", "#" + anchors.get(entry.getKey()))
          : item.appendElement("span");
      holder.appendElement("span").addClass("ig-inventory-count").text(String.valueOf(count));
      holder.appendElement("span").addClass("ig-inventory-label").text(label);
    }
    return list.children().isEmpty() ? null : list;
  }

  private Element tocList(List<Heading> headings) {
    Element list = doc.createElement("ul").addClass("ig-toc");
    for (Heading heading : headings) {
      Element item = list.appendElement("li");
      Element row = item.appendElement("div").addClass("ig-toc-row");
      if (!heading.children.isEmpty()) {
        item.addClass("ig-toc-parent");
        row.appendElement("button").attr("type", "button").addClass("ig-toc-toggle").attr("aria-expanded", "true")
            .attr("aria-label", "Show or hide the sections under " + heading.title);
      }
      Element link = row.appendElement("a").attr("href", "#" + heading.id);
      link.appendElement("span").addClass("ig-toc-num").text(heading.number);
      if (KIND_LABELS.containsKey(heading.kind)) {
        link.attr("data-kind", KIND_LABELS.get(heading.kind));
        link.attr("data-code", heading.code);
        link.appendElement("span").addClass("ig-toc-code").text(heading.code);
        String name = resourceName(heading);
        if (!name.isEmpty()) {
          link.appendElement("span").addClass("ig-toc-name").text(name);
        }
      } else {
        link.appendElement("span").addClass("ig-toc-title").text(heading.title);
      }
      if (!heading.children.isEmpty()) {
        item.appendChild(tocList(heading.children));
      }
    }
    return list;
  }

  // ---------------------------------------------------------------- Word

  /**
   * The Word importer understands a small part of CSS and names bookmarks after element ids, which
   * Word only accepts when they start with a letter. Keep the markup within what it handles.
   */
  private void prepareForWord() {
    Map<String, String> renamed = new HashMap<String, String>();
    for (Element element : doc.select("[id]")) {
      String id = element.attr("id");
      String safe = "ig_" + id.replaceAll("[^A-Za-z0-9_]", "_");
      if (safe.length() > 40) {
        safe = safe.substring(0, 40);
      }
      renamed.put(id, safe);
      element.attr("id", safe);
    }
    for (Element anchor : doc.select("a[href^=#]")) {
      String id = anchor.attr("href").substring(1);
      if (renamed.containsKey(id)) {
        anchor.attr("href", "#" + renamed.get(id));
      } else {
        anchor.unwrap();
      }
    }
    for (Element anchor : doc.select("a:not([href])")) {
      anchor.unwrap();
    }
    for (Element element : doc.select("[data-kind], [data-code], [data-depth]")) {
      element.removeAttr("data-kind");
      element.removeAttr("data-code");
      element.removeAttr("data-depth");
    }
    doc.select("script").remove();
    Element body = doc.body();
    for (String attribute : new String[] {"data-format", "data-export-type"}) {
      body.removeAttr(attribute);
    }

    // A class that names a Word style makes the importer apply that style
    for (Element subhead : doc.select("p.ig-subhead")) {
      subhead.attr("class", "IgSubhead");
    }
    for (Element line : doc.select("p.ig-caption")) {
      line.attr("class", "IgCaption");
    }
    for (Element line : doc.select("p.ig-condition")) {
      line.attr("class", "IgMeta");
    }
    for (Element line : doc.select("p.ig-meta-line")) {
      line.attr("class", "IgMeta");
    }
    for (Element code : doc.select("p.codeParagraph, pre")) {
      code.tagName("p").attr("class", "IgCodeBlock");
    }
    for (Element table : doc.select("table")) {
      boolean reference = table.hasClass("ig-table") && !table.hasClass("ig-grid") && !headerCells(table).isEmpty();
      table.attr("class", reference ? "IgTable" : "IgGridTable");
      table.removeAttr("style").removeAttr("width").removeAttr("border").removeAttr("cellpadding").removeAttr("cellspacing");
    }
    wordCover();
  }

  /** Cover page of the Word export, followed by the place of the table of contents. */
  private void wordCover() {
    Element body = doc.body();
    Element cover = doc.getElementById("ig_ig_cover");
    Element page = doc.createElement("div");
    if (cover != null) {
      Element picture = cover.select("img").first();
      if (picture != null) {
        page.appendElement("p").attr("class", "IgCoverPicture").appendChild(picture);
      } else {
        page.appendElement("p").attr("class", "IgCoverSpacer").text("\u00a0");
      }
      Element title = cover.select(".ig-title").first();
      if (title != null && !title.text().trim().isEmpty()) {
        page.appendElement("p").attr("class", "Title").text(title.text());
      }
      Element subtitle = cover.select(".ig-subtitle").first();
      if (subtitle != null && !subtitle.text().trim().isEmpty()) {
        page.appendElement("p").attr("class", "Subtitle").text(subtitle.text());
      }
      Elements facts = cover.select(".ig-fact");
      if (!facts.isEmpty()) {
        Element table = page.appendElement("table").attr("class", "IgFacts");
        Element tbody = table.appendElement("tbody");
        for (Element fact : facts) {
          Element row = tbody.appendElement("tr");
          row.appendElement("td").attr("style", "width:28%").appendElement("p").attr("class", "IgFactLabel")
              .text(fact.select("dt").text());
          row.appendElement("td").appendElement("p").attr("class", "IgFactValue").text(fact.select("dd").text());
        }
      }
      cover.remove();
    }
    page.appendElement("p").attr("class", "IgExported")
        .text("Exported " + new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).format(new Date()) + " with IGAMT");
    page.appendElement("p").text(WordUtil.TOC_MARKER);
    body.prependChild(page);
    page.unwrap();
  }

  // ---------------------------------------------------------------- output

  private void addStyles() {
    Element body = doc.body();
    String fontFamily = body.attr("data-font-family").trim();
    String fontSize = body.attr("data-font-size").trim();
    body.removeAttr("data-font-family");
    body.removeAttr("data-font-size");
    boolean customFont = !fontFamily.isEmpty() && !fontFamily.toLowerCase(Locale.ENGLISH).contains(UNSET_FONT);

    StringBuilder css = new StringBuilder();
    if (target == Target.HTML) {
      css.append(asset("ig-export.css"));
      if (customFont) {
        css.append("\n:root{--ig-font-body:").append(cssValue(fontFamily)).append(";}");
      }
      body.addClass("ig-doc");
    } else {
      css.append(asset("ig-export-word.css"));
      if (customFont) {
        css.append("\nbody, p, td, th, li, div{font-family:").append(cssValue(fontFamily)).append(";}");
      }
      if (fontSize.matches("\\d{1,2}") && (customFont || !UNSET_FONT_SIZE.equals(fontSize))) {
        css.append("\nbody, p, li, div{font-size:").append(fontSize).append("pt;}");
      }
    }
    doc.head().appendElement("style").attr("type", "text/css").appendChild(new org.jsoup.nodes.DataNode(css.toString(), ""));
  }

  private static String cssValue(String value) {
    return value.replaceAll("[;{}<>]", "").trim();
  }

  private static String asset(String name) {
    try (InputStream stream = ExportHtmlEnhancer.class.getResourceAsStream(ASSETS + name)) {
      if (stream == null) {
        throw new IllegalStateException("Missing export asset " + ASSETS + name);
      }
      return IOUtils.toString(stream, StandardCharsets.UTF_8.name());
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read export asset " + ASSETS + name, e);
    }
  }

  private String serialize() {
    Document.OutputSettings settings = doc.outputSettings();
    settings.charset(StandardCharsets.UTF_8);
    settings.prettyPrint(false);
    if (target == Target.WORD) {
      settings.syntax(Document.OutputSettings.Syntax.xml);
      settings.escapeMode(Entities.EscapeMode.xhtml);
      doc.select("meta").remove();
      Element html = doc.select("html").first();
      if (html != null) {
        html.attr("xmlns", "http://www.w3.org/1999/xhtml");
      }
      for (Node node : new ArrayList<Node>(doc.childNodes())) {
        if (node instanceof org.jsoup.nodes.DocumentType || node instanceof org.jsoup.nodes.Comment) {
          node.remove();
        }
      }
      return doc.outerHtml();
    }
    settings.syntax(Document.OutputSettings.Syntax.html);
    for (Node node : new ArrayList<Node>(doc.childNodes())) {
      if (node instanceof org.jsoup.nodes.DocumentType) {
        node.remove();
      }
    }
    return "<!DOCTYPE html>\n" + doc.outerHtml();
  }
}
