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

  private static String export(String format) throws Exception {
    ExportConfiguration configuration = ExportConfiguration.getBasicExportConfiguration(false, ExportType.IGDOCUMENT);
    ExportParameters parameters = new ExportParameters(false, true, format, TITLE, null, configuration,
        configuration.getExportFontConfiguration(), "test", Type.IGDOCUMENT);
    try (InputStream in = new ExportServiceImpl().exportSerializedElementToHtml(xml, null, parameters)) {
      return IOUtils.toString(in, StandardCharsets.UTF_8);
    }
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
