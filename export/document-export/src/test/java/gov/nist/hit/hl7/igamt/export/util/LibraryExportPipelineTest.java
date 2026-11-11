package gov.nist.hit.hl7.igamt.export.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
 * The export of a data type library, from the XML the application serializes for one (a complex
 * flavor, a primitive one and the date flavors) to the HTML and Word documents.
 */
public class LibraryExportPipelineTest {

  private static final String TITLE = "Immunization Data Type Library";

  private static String xml;
  private static Document html;

  @BeforeClass
  public static void exportOnce() throws Exception {
    try (InputStream in = LibraryExportPipelineTest.class.getResourceAsStream("/export/sample-library.xml")) {
      xml = IOUtils.toString(in, StandardCharsets.UTF_8);
    }
    html = Jsoup.parse(export("html"));
  }

  /** Same parameters as DlNewExportServiceImpl. */
  private static String export(String format) throws Exception {
    ExportConfiguration configuration = ExportConfiguration.getBasicExportConfiguration(false, ExportType.DATATYPELIBRARY);
    ExportParameters parameters = new ExportParameters("dl", false, true, format, TITLE, null, configuration,
        configuration.getExportFontConfiguration(), "test", Type.DATATYPELIBRARY);
    try (InputStream in = new ExportServiceImpl().exportSerializedElementToHtml(xml, null, parameters)) {
      return IOUtils.toString(in, StandardCharsets.UTF_8);
    }
  }

  private static Element flavor(String code) {
    for (Element section : html.select("section.ig-resource[data-kind=Datatype]")) {
      if (section.select("> .ig-heading .ig-code").text().equals(code)) {
        return section;
      }
    }
    throw new AssertionError("no data type " + code + " in the export");
  }

  private static List<String> texts(Elements elements) {
    List<String> texts = new ArrayList<String>();
    for (Element element : elements) {
      texts.add(element.text());
    }
    return texts;
  }

  @Test
  public void theLibraryIsOneDocumentInTheSameShellAsAGuide() {
    assertThat(html.select("html")).hasSize(1);
    assertThat(html.select("body")).hasSize(1);
    assertThat(html.select("#ig-main, #ig-sidebar, #ig-cover")).hasSize(3);
    assertThat(html.title()).isEqualTo(TITLE);
    // nothing left of the layout of the former library template
    assertThat(html.select("#ig-content font, #ig-content center, #ig-content .grisfonce, #ig-content .rose, #ig-content [width]")).isEmpty();
    assertThat(html.select("#ig-content").text()).doesNotContain("Standard Data Type Definition");
    assertThat(html.select("section.ig-resource[data-kind=Datatype]")).hasSize(5);
  }

  @Test
  public void aFlavorShowsItsFactsThenItsDefinitionNextToTheStandardUsage() {
    Element cx = flavor("CX_LIB");
    Element facts = cx.select("> dl.ig-meta").first();
    assertThat(cx.select("dl.ig-meta")).hasSize(1);
    assertThat(facts).isNotNull();
    assertThat(texts(facts.select("dt"))).containsExactly("Datatype Flavor", "Datatype Name", "Short Description", "HL7 Versions", "Status");
    assertThat(facts.select("dd").first().text()).isEqualTo("CX_LIB");
    assertThat(facts.text()).contains("Identifier with its assigning authority").contains("ACTIVE").contains("2.5.1");

    Element note = cx.select("> .ig-note").first();
    assertThat(note.select(".ig-subhead").text()).isEqualTo("Purpose and use");
    assertThat(note.select(".ig-note-body b").text()).isEqualTo("organizations");

    Element table = cx.select("> .ig-table-wrap > table.ig-table").first();
    assertThat(texts(table.select("thead th"))).containsExactly("Seq", "Element name", "Data type", "Usage", "Old usage", "Condition predicate");
    assertThat(table.select("thead th").get(0).className()).contains("c-seq");
    assertThat(table.select("thead th").get(1).className()).contains("c-name");
    assertThat(table.select("thead th").get(3).className()).contains("c-usage");
    assertThat(table.select("thead th").get(4).className()).contains("c-usage");

    // components in the order of their position, each with an address for links and for the detail panel
    Elements rows = table.select("tbody tr");
    assertThat(rows.size()).isGreaterThan(5);
    String headingId = cx.select("> .ig-heading").attr("id");
    for (int i = 0; i < rows.size(); i++) {
      assertThat(rows.get(i).select("td.c-seq").text()).isEqualTo(String.valueOf(i + 1));
      assertThat(rows.get(i).id()).isEqualTo(headingId + "-" + (i + 1));
    }
    assertThat(rows.get(0).select("td.c-name").text()).isEqualTo("ID Number");
    assertThat(rows.get(0).select("td.c-usage .ig-usage")).hasSize(2);
    assertThat(rows.get(2).select("td").last().text()).isEqualTo("If CX.2 is valued.");
    assertThat(rows.get(0).select("td").last().hasClass("ig-na")).isTrue();
  }

  @Test
  public void aPrimitiveFlavorShowsItsFactsAndNoEmptyTable() {
    Element st = flavor("ST_LIB");
    assertThat(st.select("> dl.ig-meta dd").first().text()).isEqualTo("ST_LIB");
    assertThat(st.select("table")).isEmpty();
    assertThat(st.text()).doesNotContain("Nothing to list");
  }

  @Test
  public void dateFlavorsShowTheirFormats() {
    for (String code : new String[] {"DT_LIB", "DTM_LIB"}) {
      Element date = flavor(code);
      assertThat(date.select("> dl.ig-meta dd").first().text()).isEqualTo(code);
      Element table = date.select("> .ig-table-wrap > table.ig-table").first();
      assertThat(table).as("format table of %s", code).isNotNull();
      assertThat(texts(table.select("thead th"))).containsExactly("#", "Value", "Format", "Usage");
      assertThat(table.select("tbody tr").first().select("td.c-code").text()).isEqualTo("YYYY");
    }
  }

  @Test
  public void theContentsListEveryFlavor() {
    Elements links = html.select("#ig-sidebar .ig-toc a[data-code]");
    assertThat(texts(html.select("#ig-sidebar .ig-toc a[data-code] .ig-toc-code"))).contains("CX_LIB", "HD_LIB", "ST_LIB", "DT_LIB", "DTM_LIB");
    for (Element link : links) {
      assertThat(html.getElementById(link.attr("href").substring(1))).as("target of %s", link.attr("href")).isNotNull();
    }
  }

  @Test
  public void theWordDocumentHasTheSameContent() throws Exception {
    DocumentMetadata metadata = new DocumentMetadata();
    metadata.setTitle(TITLE);
    InputStream wordHtml = IOUtils.toInputStream(export("docx"), StandardCharsets.UTF_8);
    ExportedFile word = WordUtil.convertHtmlToWord(new ExportedFile(wordHtml, TITLE, "id", ExportFormat.WORD), metadata, new Date(), "2.5.1");
    File docx = File.createTempFile("library-export-test", ".docx");
    try {
      try (InputStream in = word.getContent()) {
        FileUtils.copyInputStreamToFile(in, docx);
      }
      WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(docx);
      String body = XmlUtils.marshaltoString(wordPackage.getMainDocumentPart().getJaxbElement(), true, false);
      assertThat(body).contains("CX_LIB").contains("Datatype Flavor").contains("Old usage").contains("Condition predicate")
          .contains("If CX.2 is valued.").contains("Purpose and use").contains("YYYY");
      assertThat(body).contains("w:val=\"IgTable\"").contains("w:val=\"Heading1\"");
      assertThat(body).doesNotContain("Standard Data Type Definition");
    } finally {
      FileUtils.deleteQuietly(docx);
    }
  }
}
