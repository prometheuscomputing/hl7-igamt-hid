package gov.nist.hit.hl7.igamt.export.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.xml.transform.TransformerException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.BeforeClass;
import org.junit.Test;

import gov.nist.hit.hl7.igamt.common.base.domain.DocumentMetadata;
import gov.nist.hit.hl7.igamt.common.base.domain.Type;
import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportType;
import gov.nist.hit.hl7.igamt.export.domain.ExportFormat;
import gov.nist.hit.hl7.igamt.export.domain.ExportParameters;
import gov.nist.hit.hl7.igamt.export.domain.ExportedFile;
import gov.nist.hit.hl7.igamt.export.service.impl.ExportServiceImpl;

/**
 * Runs the real stylesheets and post-processing on a small serialized implementation guide
 * (a conformance profile with co-constraints and slicing, segments, datatypes, value sets,
 * a profile component and narrative sections).
 */
public class ExportPipelineTest {

  private static final String TITLE = "HL7 Version 2.5.1 Implementation Guide: Immunization Messaging (Export Showcase)";

  private static String xml;
  private static Document html;

  @BeforeClass
  public static void exportOnce() throws Exception {
    try (InputStream in = ExportPipelineTest.class.getResourceAsStream("/export/sample-ig.xml")) {
      xml = IOUtils.toString(in, StandardCharsets.UTF_8);
    }
    html = Jsoup.parse(export("html"));
  }

  /** The export of the sample guide with this markup added to the text of its first section. */
  private static String exportWith(String format, String authorMarkup) throws Exception {
    String escaped = authorMarkup.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    String changed = xml.replaceFirst("<SectionContent>", java.util.regex.Matcher.quoteReplacement("<SectionContent>" + escaped));
    assertThat(changed).isNotEqualTo(xml);
    return export(format, changed);
  }

  private static String export(String format) throws Exception {
    return export(format, xml);
  }

  private static String export(String format, String xml) throws Exception {
    ExportConfiguration configuration = ExportConfiguration.getBasicExportConfiguration(false, ExportType.IGDOCUMENT);
    ExportParameters parameters = new ExportParameters(false, true, format, TITLE, null, configuration,
        configuration.getExportFontConfiguration(), "test", Type.IGDOCUMENT);
    try (InputStream in = new ExportServiceImpl().exportSerializedElementToHtml(xml, null, parameters)) {
      return IOUtils.toString(in, StandardCharsets.UTF_8);
    }
  }

  @Test
  public void exportLeavesTheSerializedGuideUnchanged() throws Exception {
    String before = xml;
    String htmlOut = export("html");
    String docxHtml = export("docx");
    assertThat(xml).isEqualTo(before);
    assertThat(before).contains("type=\"PROFILE\"");
    assertThat(before).contains("title=\"Message Infrastructure\"");
    assertThat(htmlOut).contains("Message Infrastructure");
    assertThat(docxHtml).contains("Message Infrastructure");
  }

  @Test
  public void htmlIsOneSelfContainedFile() {
    assertThat(html.select("link[rel=stylesheet], script[src], img[src^=http]")).isEmpty();
    assertThat(html.select("head style").html()).contains("--ig-accent");
    assertThat(html.select("body script").html()).contains("ig-search");
  }

  @Test
  public void htmlHasCoverNavigationAndSearch() {
    assertThat(html.title()).isEqualTo(TITLE);
    assertThat(html.select("#ig-cover .ig-title").text()).isEqualTo(TITLE);
    assertThat(html.select("#ig-cover img.ig-cover-picture").attr("src")).startsWith("data:image/png;base64,");
    assertThat(html.select("#ig-cover .ig-fact").size()).isGreaterThan(2);
    assertThat(html.select("#ig-sidebar ul.ig-toc li").size()).isGreaterThan(10);
    assertThat(html.select("#ig-search")).hasSize(1);
    assertThat(html.select("#ig-theme")).hasSize(1);
  }

  /** On paper there is no sidebar: the first three levels of the contents follow the cover. */
  @Test
  public void thePrintedDocumentHasItsContentsAfterTheCover() {
    org.jsoup.select.Elements printed = html.select("nav.ig-print-toc");
    assertThat(printed).hasSize(1);
    assertThat(printed.first().previousElementSibling().id()).isEqualTo("ig-cover");
    org.jsoup.select.Elements links = printed.select("a[href^=#]");
    assertThat(links.size()).isGreaterThan(10);
    for (org.jsoup.nodes.Element link : links) {
      assertThat(html.getElementById(link.attr("href").substring(1))).as("target of %s", link.attr("href")).isNotNull();
    }
    // every first-level section, in the order of the sidebar, and nothing deeper than the third level
    assertThat(texts(printed.select("nav > ol > li > a"))).isEqualTo(texts(html.select("#ig-sidebar nav > ul.ig-toc > li > .ig-toc-row > a")));
    assertThat(printed.select("ol ol ol ol")).isEmpty();
    assertThat(printed.select("[id]")).isEmpty();
  }

  /** Text written straight into a table, outside any cell, is shown above the table by a browser and as a ragged row by Word. */
  @Test
  public void noTableHoldsTextOutsideItsCells() {
    for (org.jsoup.nodes.Element part : html.select("table, thead, tbody, tfoot, tr")) {
      assertThat(part.ownText().trim()).as("text directly in <%s> of the table starting with: %s", part.tagName(),
          part.text().substring(0, Math.min(80, part.text().length()))).isEmpty();
    }
  }

  /** Every row of every table has as many cells, counting the ones that span, as the first row. */
  @Test
  public void everyRowOfATableHasTheSameNumberOfColumns() {
    int checked = 0;
    for (org.jsoup.nodes.Element table : html.select("#ig-content table")) {
      if (!table.select("[rowspan]").isEmpty()) {
        continue; // a cell that spans rows takes the place of cells in the rows below
      }
      int expected = -1;
      java.util.List<org.jsoup.nodes.Element> rows = new java.util.ArrayList<org.jsoup.nodes.Element>();
      for (org.jsoup.nodes.Element child : table.children()) {
        if (child.tagName().equals("tr")) {
          rows.add(child);
        } else if (child.tagName().matches("thead|tbody|tfoot")) {
          for (org.jsoup.nodes.Element row : child.children()) {
            if (row.tagName().equals("tr")) {
              rows.add(row);
            }
          }
        }
      }
      for (org.jsoup.nodes.Element row : rows) {
        int columns = 0;
        for (org.jsoup.nodes.Element cell : row.children()) {
          if (cell.tagName().matches("th|td")) {
            columns += cell.hasAttr("colspan") ? Integer.parseInt(cell.attr("colspan").trim()) : 1;
          }
        }
        if (expected < 0) {
          expected = columns;
        }
        assertThat(columns).as("columns of the row \"%s\"", row.text().substring(0, Math.min(80, row.text().length()))).isEqualTo(expected);
        checked++;
      }
    }
    assertThat(checked).isGreaterThan(100);
  }

  @Test
  public void headingsAreNumberedInDocumentOrder() {
    assertThat(html.select("#ig-content .ig-heading .ig-num").first().text()).isEqualTo("1");
    assertThat(texts(html.select("#ig-content .ig-heading .ig-num"))).contains("1.1", "2", "2.1");
  }

  @Test
  public void everyInternalLinkHasATarget() {
    Set<String> ids = new HashSet<String>();
    for (Element element : html.select("[id]")) {
      ids.add(element.id());
    }
    assertThat(html.select("a.ig-ref").size()).isGreaterThan(5);
    for (Element anchor : html.select("a[href^=#]")) {
      assertThat(ids).as("target of %s", anchor.outerHtml()).contains(anchor.attr("href").substring(1));
    }
  }

  @Test
  public void resourcesAreSectionsWithTypedTables() {
    assertThat(html.select("section.ig-resource[data-kind=Segment]").size()).isEqualTo(3);
    assertThat(html.select("section.ig-resource[data-kind=ConformanceProfile]").size()).isEqualTo(1);
    assertThat(html.select("table.ig-structure tr.ig-group-begin")).isNotEmpty();
    assertThat(html.select("td.c-usage span.ig-usage.u-r")).isNotEmpty();
    assertThat(html.select("td.c-usage span.ig-usage-conditional")).isNotEmpty();
    assertThat(html.select("table.ig-table col, table.ig-table[summary]")).isEmpty();
  }

  @Test
  public void authorNotesKeepTheirText() {
    assertThat(html.select(".ig-note").size()).isGreaterThan(0);
    for (Element note : html.select(".ig-note")) {
      assertThat(note.select(".ig-note-body").text()).as(note.select(".ig-subhead").text()).isNotEmpty();
    }
  }

  @Test
  public void coConstraintsAreOneFlatTable() {
    Element grid = html.select(".ig-coconstraint table.ig-grid").first();
    assertThat(grid).isNotNull();
    assertThat(grid.select("table")).hasSize(1);
    assertThat(grid.select("span.cc-code")).isNotEmpty();
    assertThat(grid.select("tr tr")).isEmpty();
    assertThat(html.select(".ig-coconstraint .ig-caption").first().text()).startsWith("Segment ");
  }

  @Test
  public void aCoConstraintTableNamesTheSegmentFlavorItIsFor() throws Exception {
    // the detail panel of a field lists the co-constraint tables of its own flavor only
    assertThat(html.select(".ig-coconstraint")).isNotEmpty();
    assertThat(html.select(".ig-coconstraint:not([data-segment])")).isEmpty();
    String segment = html.select(".ig-coconstraint").first().attr("data-segment");
    assertThat(segment).isNotEmpty();
    Element heading = html.getElementById(segment);
    assertThat(heading).as("the section of segment %s", segment).isNotNull();
    assertThat(heading.parent().attr("data-kind")).isEqualTo("Segment");
    assertThat(heading.select(".ig-code").text()).startsWith("OBX");
    // an attribute for the script of the page only
    assertThat(export("docx")).doesNotContain("data-segment");
  }

  @Test
  public void slicingCommentsAndDynamicMappingAreTables() {
    assertThat(texts(html.select("p.ig-subhead"))).contains("Slicing", "Comments", "Definition texts", "Dynamic mapping", "Co-constraints");
    assertThat(html.select("table.ig-annotations")).isNotEmpty();
    assertThat(html.select("p.ig-caption").text()).contains("sliced by occurrence").contains("is selected by the value of");
  }

  @Test
  public void narrativeKeepsNonAsciiText() {
    assertThat(html.text()).contains("café");
  }

  @Test
  public void wordDocumentHasStylesContentsAndValidBookmarks() throws Exception {
    DocumentMetadata metadata = new DocumentMetadata();
    metadata.setTitle(TITLE);
    metadata.setOrgName("Example Public Health Informatics Consortium");
    // a title with a format specifier used to break the table of contents
    String wordSource = export("docx").replaceFirst(">Introduction<", ">Introduction 100%s sure<");
    // two pictures in the text, next to the one of the cover
    String picture = "<p><img alt=\"dot\" width=\"8\" height=\"8\" src=\"data:image/png;base64,"
        + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==\"/></p>";
    wordSource = wordSource.replaceFirst("</body>", java.util.regex.Matcher.quoteReplacement(picture + picture + "</body>"));
    assertThat(wordSource).contains("100%s sure");
    InputStream wordHtml = IOUtils.toInputStream(wordSource, StandardCharsets.UTF_8);
    ExportedFile source = new ExportedFile(wordHtml, TITLE, "id", ExportFormat.WORD);
    ExportedFile word = WordUtil.convertHtmlToWord(source, metadata, new Date(), "2.5.1");

    File docx = File.createTempFile("export-test", ".docx");
    try {
      try (InputStream in = word.getContent()) {
        FileUtils.copyInputStreamToFile(in, docx);
      }
      WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(docx);
      String body = XmlUtils.marshaltoString(wordPackage.getMainDocumentPart().getJaxbElement(), true, false);

      assertThat(body).contains("w:val=\"Title\"").contains("w:val=\"Heading1\"").contains("w:val=\"Heading2\"");
      assertThat(body).contains("w:val=\"IgTable\"").contains("w:val=\"IgGridTable\"").contains("w:val=\"TOCHeading\"");
      assertThat(body).contains("TOC \\o").doesNotContain(WordUtil.TOC_MARKER);
      assertThat(body).contains("café");
      assertThat(body).doesNotContainPattern("<w:bookmarkStart[^>]*w:name=\"[^A-Za-z_]");
      assertThat(body).contains("100%s sure");
      java.util.regex.Matcher names = java.util.regex.Pattern.compile("<w:bookmarkStart[^>]*w:name=\"([^\"]+)\"").matcher(body);
      Set<String> seen = new HashSet<String>();
      while (names.find()) {
        assertThat(seen.add(names.group(1))).as("duplicate bookmark %s", names.group(1)).isTrue();
      }
      assertThat(seen.size()).isGreaterThan(10);
      // what strict readers of the format require: a pattern on every shading, an id of its own for every picture
      java.util.regex.Matcher shadings = java.util.regex.Pattern.compile("<w:shd [^>]*>").matcher(body);
      int shadingCount = 0;
      while (shadings.find()) {
        shadingCount++;
        assertThat(shadings.group()).contains("w:val=");
      }
      assertThat(shadingCount).isGreaterThan(0);
      java.util.regex.Matcher pictures = java.util.regex.Pattern.compile("<wp:docPr [^>]*\\bid=\"(\\d+)\"").matcher(body);
      Set<String> pictureIds = new HashSet<String>();
      while (pictures.find()) {
        assertThat(pictureIds.add(pictures.group(1))).as("duplicate picture id %s", pictures.group(1)).isTrue();
      }
      assertThat(pictureIds.size()).isGreaterThanOrEqualTo(2);
      assertThat(wordPackage.getMainDocumentPart().getStyleDefinitionsPart().getJaxbElement().getStyle().size()).isGreaterThan(20);
      assertThat(wordPackage.getMainDocumentPart().getNumberingDefinitionsPart()).isNotNull();
      assertThat(wordPackage.getDocumentModel().getSections().get(0).getHeaderFooterPolicy().getDefaultHeader()).isNotNull();
      assertThat(wordPackage.getDocumentModel().getSections().get(0).getHeaderFooterPolicy().getDefaultFooter()).isNotNull();
    } finally {
      FileUtils.deleteQuietly(docx);
    }
  }

  /**
   * Word stops on a question ("This document contains fields that may refer to other files. Do you
   * want to update the fields in this document?") when the settings ask for an update of the fields
   * or when a field is marked as out of date. The contents must be complete without either.
   */
  @Test
  public void wordDocumentOpensWithNothingToAnswer() throws Exception {
    DocumentMetadata metadata = new DocumentMetadata();
    metadata.setTitle(TITLE);
    InputStream wordHtml = IOUtils.toInputStream(export("docx"), StandardCharsets.UTF_8);
    ExportedFile word = WordUtil.convertHtmlToWord(new ExportedFile(wordHtml, TITLE, "id", ExportFormat.WORD), metadata, new Date(), "2.5.1");
    File docx = File.createTempFile("export-test", ".docx");
    try {
      try (InputStream in = word.getContent()) {
        FileUtils.copyInputStreamToFile(in, docx);
      }
      WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(docx);
      String settings = XmlUtils.marshaltoString(wordPackage.getMainDocumentPart().getDocumentSettingsPart().getJaxbElement(), true, false);
      assertThat(settings).doesNotContain("updateFields");
      // without this Word opens the document in "Compatibility Mode"
      assertThat(settings).containsPattern("<w:compatSetting [^>]*w:name=\"compatibilityMode\"[^>]*w:val=\"15\"");
      assertThat(settings.split("w:name=\"compatibilityMode\"", -1)).hasSize(2);

      String body = XmlUtils.marshaltoString(wordPackage.getMainDocumentPart().getJaxbElement(), true, false);
      assertThat(body).doesNotContain("w:dirty");

      // the contents: one table, in the container Word gives its own, every entry a link to a heading
      assertThat(body.split("<w:docPartGallery w:val=\"Table of Contents\"", -1)).hasSize(2);
      int start = body.indexOf("<w:sdtContent>");
      String contents = body.substring(start, body.indexOf("</w:sdtContent>", start));
      assertThat(contents).contains("w:val=\"TOCHeading\"").contains("TOC \\o");
      assertThat(contents.split("w:fldCharType=\"begin\"", -1)).hasSize(2);
      assertThat(contents.split("w:fldCharType=\"end\"", -1)).hasSize(2);
      Set<String> bookmarks = new HashSet<String>();
      java.util.regex.Matcher names = java.util.regex.Pattern.compile("<w:bookmarkStart[^>]*w:name=\"([^\"]+)\"").matcher(body);
      while (names.find()) {
        bookmarks.add(names.group(1));
      }
      java.util.regex.Matcher entries = java.util.regex.Pattern.compile("<w:hyperlink [^>]*w:anchor=\"([^\"]+)\"").matcher(contents);
      int entryCount = 0;
      while (entries.find()) {
        entryCount++;
        assertThat(bookmarks).as("heading of the contents entry %s", entries.group(1)).contains(entries.group(1));
      }
      assertThat(entryCount).isGreaterThan(10);
      assertThat(contents.split("<w:pStyle w:val=\"TOC\\d\"", -1).length - 1).isEqualTo(entryCount);
    } finally {
      FileUtils.deleteQuietly(docx);
    }
  }

  /** A table inside a cell of another one used to get the width of the page, and its columns that of the page's share. */
  @Test
  public void aTableInsideACellIsAsWideAsTheCell() throws Exception {
    DocumentMetadata metadata = new DocumentMetadata();
    metadata.setTitle(TITLE);
    String nested = "<table><tbody><tr><td>Outer label</td><td><p>Before</p>"
        + "<table><tbody><tr><th>Inner label</th><td>Inner value that is a good deal longer than its label</td></tr></tbody></table></td></tr></tbody></table>";
    String wordSource = export("docx").replaceFirst("</body>", java.util.regex.Matcher.quoteReplacement(nested + "</body>"));
    InputStream wordHtml = IOUtils.toInputStream(wordSource, StandardCharsets.UTF_8);
    ExportedFile word = WordUtil.convertHtmlToWord(new ExportedFile(wordHtml, TITLE, "id", ExportFormat.WORD), metadata, new Date(), "2.5.1");
    File docx = File.createTempFile("export-test", ".docx");
    try {
      try (InputStream in = word.getContent()) {
        FileUtils.copyInputStreamToFile(in, docx);
      }
      WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(docx);
      org.docx4j.wml.Tbl outer = null;
      for (Object node : WordUtil.getAllElementFromObject(wordPackage.getMainDocumentPart(), org.docx4j.wml.Tbl.class)) {
        String text = XmlUtils.marshaltoString(node, true, false);
        if (text.contains("Outer label")) {
          outer = (org.docx4j.wml.Tbl) node;
          break;
        }
      }
      assertThat(outer).isNotNull();
      java.util.List<Object> inside = WordUtil.getAllElementFromObject(outer.getContent().get(0), org.docx4j.wml.Tbl.class);
      assertThat(inside).hasSize(1);
      org.docx4j.wml.Tbl inner = (org.docx4j.wml.Tbl) inside.get(0);
      int cell = outer.getTblGrid().getGridCol().get(1).getW().intValue();
      int innerWidth = 0;
      for (org.docx4j.wml.TblGridCol column : inner.getTblGrid().getGridCol()) {
        innerWidth += column.getW().intValue();
      }
      assertThat(innerWidth).isLessThanOrEqualTo(cell).isGreaterThan(cell / 2);
      int outerWidth = 0;
      for (org.docx4j.wml.TblGridCol column : outer.getTblGrid().getGridCol()) {
        outerWidth += column.getW().intValue();
      }
      assertThat(innerWidth).isLessThan(outerWidth);
    } finally {
      FileUtils.deleteQuietly(docx);
    }
  }

  /** One paragraph pasted from Word or Outlook used to make the whole guide impossible to export to Word. */
  @Test
  public void textPastedFromOfficeOrWithScriptsStillExportsToWord() throws Exception {
    DocumentMetadata metadata = new DocumentMetadata();
    // a control character, and a character outside the basic plane where the title is cut
    StringBuilder title = new StringBuilder("Guide\u000B with a tab\u0007 ");
    while (title.length() < 108) {
      title.append('x');
    }
    title.append(new String(Character.toChars(0x1F489))).append(" and more text after the cut");
    metadata.setTitle(title.toString());
    metadata.setOrgName("Org \uFFFE<&>");
    String pasted = "<div class=\"MsoNormal\" v:shapes=\"x\" \"foo=\"1\" a<b=\"2\">Pasted<o:p></o:p> text<!--[if gte mso 9]><xml><w:WordDocument/></xml><![endif]-->"
        + "<span>a<!-- x -- y --->b</span></div><hr><table><tbody><tr><td>Only column</td></tr></tbody></table>"
        + "<p onclick=\"alert(1)\">click</p><script>alert(2)</script><a href=\"javascript:alert(3)\">go</a>";
    String wordSource = exportWith("docx", pasted);
    ExportedFile word = WordUtil.convertHtmlToWord(new ExportedFile(IOUtils.toInputStream(wordSource, StandardCharsets.UTF_8), TITLE, "id", ExportFormat.WORD),
        metadata, new Date(), "2.5.1");
    File docx = File.createTempFile("export-test", ".docx");
    try {
      try (InputStream in = word.getContent()) {
        FileUtils.copyInputStreamToFile(in, docx);
      }
      WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(docx);
      String body = XmlUtils.marshaltoString(wordPackage.getMainDocumentPart().getJaxbElement(), true, false);
      assertThat(body).contains("Pasted").contains("Only column").contains("click").doesNotContain("alert(").doesNotContain("MsoNormal\" v:");
      // the rule is a paragraph of this document, not the one object the importer shares between documents
      assertThat(body).contains("w:val=\"IgRule\"");
      // a table of one column is as wide as the text
      java.util.regex.Matcher one = java.util.regex.Pattern.compile("<w:tblGrid><w:gridCol w:w=\"(\\d+)\"/></w:tblGrid>").matcher(body.replaceAll(">\\s+<", "><"));
      assertThat(one.find()).isTrue();
      assertThat(Integer.parseInt(one.group(1))).isLessThanOrEqualTo(9700).isGreaterThan(9000);
      String header = XmlUtils.marshaltoString(wordPackage.getDocumentModel().getSections().get(0).getHeaderFooterPolicy().getDefaultHeader().getJaxbElement(), true, false);
      assertThat(header).contains("Guide with a tab").contains("\u2026").doesNotContain("and more text");
    } finally {
      FileUtils.deleteQuietly(docx);
    }
  }

  @Test
  public void textOnAColouredBackgroundIsBlackOrWhiteWhicheverReadsBetter() throws Exception {
    String markup = "<table><tbody><tr><td style=\"background-color:#EC330C\">removed</td><td style=\"background-color: #1b2a41;\">dark</td>"
        + "<td style=\"background-color: rgb(255, 241, 118)\">pale</td><td style=\"background-color:#123; color: #fff\">chosen</td>"
        + "<td style=\"background-color: rgba(0, 0, 0, 0)\">clear</td><td style=\"background-color: papayawhip\">named</td></tr></tbody></table>";
    for (String format : new String[] { "html", "docx" }) {
      Document page = Jsoup.parse(exportWith(format, markup));
      assertThat(page.select("td:containsOwn(removed)").attr("style")).as(format).endsWith("color: #000000");
      assertThat(page.select("td:containsOwn(dark)").attr("style")).as(format).endsWith("color: #ffffff");
      assertThat(page.select("td:containsOwn(pale)").attr("style")).as(format).endsWith("color: #000000");
      assertThat(page.select("td:containsOwn(chosen)").attr("style")).as(format).endsWith("color: #fff");
      assertThat(page.select("td:containsOwn(clear)").attr("style")).as(format).doesNotContain("; color");
      assertThat(page.select("td:containsOwn(named)").attr("style")).as(format).doesNotContain("; color");
    }
    assertThat(ExportHtmlEnhancer.rgb("#abc")).containsExactly(0xaa, 0xbb, 0xcc);
    assertThat(ExportHtmlEnhancer.rgb("url(x.png)")).isNull();
  }

  @Test
  public void wordIsGivenNothingToFetchFromTheServer() throws Exception {
    String wordSource = exportWith("docx", "<p>before <img src=\"http://127.0.0.1:1/internal.png\" alt=\"Diagram\"> <img src=\"/api/x.png\">"
        + "<img id=\"kept\" src=\"data:image/png;base64,iVBORw0KGgo=\"> after</p>"
        + "<div style=\"color: red; background-image: url(http://127.0.0.1:1/b.png)\">styled</div>");
    Document word = Jsoup.parse(wordSource);
    assertThat(word.select("img[src^=http], img[src^=/]")).isEmpty();
    assertThat(word.select("img[src^=data:]")).isNotEmpty();
    assertThat(word.select("a[href=http://127.0.0.1:1/internal.png]").text()).isEqualTo("Diagram");
    assertThat(word.select("div:containsOwn(styled)").attr("style")).contains("color: red").doesNotContain("url");
  }

  @Test
  public void thePageRunsNoScriptButItsOwn() throws Exception {
    Document page = Jsoup.parse(exportWith("html",
        "<p onclick=\"alert(1)\">click</p><script>alert(2)</script><a href=\"javascript:alert(3)\">go</a><img src=\"x\" onerror=\"alert(4)\">"));
    assertThat(page.select("#ig-content").text()).contains("click").contains("go");
    assertThat(page.select("script")).hasSize(1);
    assertThat(page.select("#ig-content script, [onclick], [onerror], a[href^=javascript]")).isEmpty();
    assertThat(page.body().html()).doesNotContain("alert(2)").doesNotContain("alert(3)");
    String policy = page.select("meta[http-equiv=Content-Security-Policy]").attr("content");
    byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(page.select("script").first().data().getBytes(StandardCharsets.UTF_8));
    assertThat(policy).contains("script-src 'sha256-" + javax.xml.bind.DatatypeConverter.printBase64Binary(digest) + "'").contains("default-src 'none'");
    // the policy comes before anything that could load or run
    assertThat(page.head().children().indexOf(page.select("meta[http-equiv]").first())).isLessThanOrEqualTo(1);
  }

  @Test
  public void stylesheetFailureStopsTheExport() {
    ExportConfiguration configuration = ExportConfiguration.getBasicExportConfiguration(false, ExportType.IGDOCUMENT);
    final ExportParameters parameters = new ExportParameters(false, true, "html", TITLE, null, configuration,
        configuration.getExportFontConfiguration(), "test", Type.IGDOCUMENT);
    assertThatThrownBy(() -> TransformationUtil.doTransformToTempHtml("<Document/>", "/export/failing.xsl", parameters))
        .isInstanceOf(TransformerException.class);
  }

  /** Text of each element (jsoup 1.9 has no eachText). */
  private static java.util.List<String> texts(org.jsoup.select.Elements elements) {
    java.util.List<String> texts = new java.util.ArrayList<String>();
    for (Element element : elements) {
      texts.add(element.text());
    }
    return texts;
  }
}
