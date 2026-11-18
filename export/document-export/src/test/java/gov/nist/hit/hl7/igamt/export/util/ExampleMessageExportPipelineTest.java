package gov.nist.hit.hl7.igamt.export.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.relationships.Relationship;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.BeforeClass;
import org.junit.Test;

import gov.nist.hit.hl7.igamt.common.base.domain.DocumentMetadata;
import gov.nist.hit.hl7.igamt.common.base.domain.Type;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.domain.MessageSnippet;
import gov.nist.hit.hl7.igamt.examples.dto.SnippetRenderResult;
import gov.nist.hit.hl7.igamt.examples.service.ExampleMessageExportDataService;
import gov.nist.hit.hl7.igamt.examples.dto.SnippetRenderResult.SnippetPart;
import gov.nist.hit.hl7.igamt.examples.dto.parser.Point;
import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportType;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportMode;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageLinkTarget;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageSnippetExportMode;
import gov.nist.hit.hl7.igamt.export.domain.ExportFormat;
import gov.nist.hit.hl7.igamt.export.domain.ExportParameters;
import gov.nist.hit.hl7.igamt.export.domain.ExportedFile;
import gov.nist.hit.hl7.igamt.export.service.impl.ExportServiceImpl;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.ExampleMessageDataModel;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.IgDataModel;
import gov.nist.hit.hl7.igamt.serialization.newImplementation.service.ExampleMessageSerializationServiceImpl;
import nu.xom.Attribute;

/**
 * Example messages in the exported guide, from the two places that write them (a message section and
 * a widget pasted in rich text, both through the real serializer) to the HTML and Word documents.
 */
public class ExampleMessageExportPipelineTest {

  private static final String TITLE = "Immunization Messaging";
  private static final String[] LINES = {
      "MSH|^~\\&|MYEHR|DCS|MYIIS|MYSTATEIIS|20260705103000-0500||VXU^V04^VXU_V04|45646ug|P|2.5.1|||ER|AL|||||Z22^CDCPHINVS",
      "PID|1||432155^^^DCS^MR||Patient^Johnny^New^^^^L|Lastname^Sally|20260214|M||2106-3^White^CDCREC",
      "",
      "ORC|RE||197023^DCS|||||||^Clerk^Myron||^Pediatric^MARY^^^^^^^L^^^MD",
      "RXA|0|1|20260415||08^HepB-Peds^CVX|.5|mL^mL^UCUM||00^new immunization record^NIP001|<&>" };
  private static final String PATIENT_NAME = "Patient^Johnny^New^^^^L";
  private static final String MESSAGE_PAGE = "ExampleMessages/VXU_V04/Unsolicited%20update.html";
  private static final String SNIPPET_PAGE = "ExampleMessages/VXU_V04/Unsolicited%20update%20-%20Patient%20name.html";

  private static Document html;
  private static Document wordHtml;
  private static String wordBody;
  private static List<String> wordLinks;

  @BeforeClass
  public static void exportOnce() throws Exception {
    String xml = guide(configuration(ExampleMessageExportMode.BOTH, ExampleMessageSnippetExportMode.HIGHLIGHT));
    String forWeb = export(xml, "html");
    html = Jsoup.parse(forWeb);
    String forWord = export(xml, "docx");
    wordHtml = Jsoup.parse(forWord);

    DocumentMetadata metadata = new DocumentMetadata();
    metadata.setTitle(TITLE);
    ExportedFile word = WordUtil.convertHtmlToWord(
        new ExportedFile(IOUtils.toInputStream(forWord, StandardCharsets.UTF_8), TITLE, "id", ExportFormat.WORD), metadata, new Date(), "2.5.1");
    File docx = File.createTempFile("example-message-export-test", ".docx");
    try {
      try (InputStream in = word.getContent()) {
        FileUtils.copyInputStreamToFile(in, docx);
      }
      String dump = System.getProperty("ig.export.dump");
      if (dump != null) {
        FileUtils.copyFile(docx, new File(dump, "example-messages.docx"));
        FileUtils.writeStringToFile(new File(dump, "example-messages.html"), forWeb, StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(new File(dump, "example-messages-word.html"), forWord, StandardCharsets.UTF_8);
      }
      WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(docx);
      wordBody = XmlUtils.marshaltoString(wordPackage.getMainDocumentPart().getJaxbElement(), true, false);
      wordLinks = new ArrayList<String>();
      for (Relationship relationship : wordPackage.getMainDocumentPart().getRelationshipsPart().getRelationships().getRelationship()) {
        if ("External".equals(relationship.getTargetMode())) {
          wordLinks.add(relationship.getTarget());
        }
      }
    } finally {
      FileUtils.deleteQuietly(docx);
    }
  }

  // ---------------------------------------------------------------- fixture

  private static ExampleMessageExportConfiguration configuration(ExampleMessageExportMode mode, ExampleMessageSnippetExportMode snippetMode) {
    ExampleMessageExportConfiguration configuration = new ExampleMessageExportConfiguration();
    configuration.setInclude(true);
    configuration.setMode(mode);
    configuration.setSnippetMode(snippetMode);
    configuration.setLinkTarget(ExampleMessageLinkTarget.BOTH);
    configuration.setIncludeDescription(true);
    return configuration;
  }

  private static IgDataModel model() {
    return model(String.join("\r", LINES));
  }

  private static IgDataModel model(String text) {
    Ig ig = new Ig();
    ig.setId("ig-1");
    ig.setName(TITLE);

    ExampleMessage message = new ExampleMessage();
    message.setId("msg-1");
    message.setName("Unsolicited update");
    message.setDescription("<p>A newborn's <b>first</b> hepatitis B dose.</p>");
    message.setProfileId("cp-1");
    message.setMessage(text);

    MessageSnippet snippet = new MessageSnippet();
    snippet.setId("snip-1");
    snippet.setName("Patient name");
    snippet.setDescription("<p>The legal name, in PID-5.</p>");
    message.getSnippets().add(snippet);

    int column = LINES[1].indexOf(PATIENT_NAME) + 1;
    SnippetPart part = new SnippetPart();
    part.setStartLine(2);
    part.setEndLine(2);
    part.setHighlightStart(new Point(1, column));
    part.setHighlightEnd(new Point(1, column + PATIENT_NAME.length()));
    SnippetRenderResult render = new SnippetRenderResult();
    render.setSnippetId("snip-1");
    render.setSnippetName("Patient name");
    render.setRenderedContent(LINES[1]);
    render.setFullMessage(message.getMessage());
    render.setParts(new ArrayList<SnippetPart>(Arrays.asList(part)));

    ExampleMessageDataModel dataModel = new ExampleMessageDataModel();
    dataModel.setModel(message);
    dataModel.setProfileId("cp-1");
    dataModel.setProfileLabel("VXU_V04");
    dataModel.setFilePath("ExampleMessages/VXU_V04/Unsolicited update.txt");
    dataModel.getSnippetFilePaths().put("snip-1", "ExampleMessages/VXU_V04/Unsolicited update - Patient name.txt");
    dataModel.getSnippets().put("snip-1", render);

    IgDataModel igDataModel = new IgDataModel();
    igDataModel.setModel(ig);
    Map<String, ExampleMessageDataModel> messages = new LinkedHashMap<String, ExampleMessageDataModel>();
    messages.put(message.getId(), dataModel);
    igDataModel.setExampleMessages(messages);
    return igDataModel;
  }

  private static String widget(String messageId, String snippetId) {
    return "<div class=\"fr-exmsg\" contenteditable=\"false\" data-message-id=\"" + messageId + "\" data-snippet-id=\"" + snippetId + "\">"
        + "<a class=\"fr-exmsg-link\" href=\"/example-messages/ig-1/message/" + messageId + "\">Example: stale</a>"
        + "<pre class=\"fr-exmsg-body\">Loading</pre></div>";
  }

  private static nu.xom.Element section(String id, int position, String title, String type, String narrative) {
    nu.xom.Element section = new nu.xom.Element("Section");
    section.addAttribute(new Attribute("id", id));
    section.addAttribute(new Attribute("position", String.valueOf(position)));
    section.addAttribute(new Attribute("title", title));
    section.addAttribute(new Attribute("type", type));
    section.addAttribute(new Attribute("h", "1"));
    nu.xom.Element content = new nu.xom.Element("SectionContent");
    content.appendChild("<div class=\"fr-view\">" + narrative + "</div>");
    section.appendChild(content);
    return section;
  }

  /**
   * A guide with the same example four times: a message section for the snippet, one for the whole
   * message, a widget for each in rich text, then a section and a widget whose message is gone.
   */
  private static String guide(ExampleMessageExportConfiguration configuration) {
    ExampleMessageSerializationServiceImpl serializer = new ExampleMessageSerializationServiceImpl();
    IgDataModel model = model();
    nu.xom.Element document = new nu.xom.Element("Document");
    nu.xom.Element metadata = new nu.xom.Element("Metadata");
    metadata.addAttribute(new Attribute("title", TITLE));
    document.appendChild(metadata);

    nu.xom.Element snippetSection = section("sec-1", 1, "Patient name", "MESSAGESECTION", "<p>How the name is sent.</p>");
    snippetSection.appendChild(serializer.serializeReference(model, "msg-1", "snip-1", configuration));
    document.appendChild(snippetSection);

    nu.xom.Element messageSection = section("sec-2", 2, "Whole message", "MESSAGESECTION", "<p>The complete update.</p>");
    messageSection.appendChild(serializer.serializeReference(model, "msg-1", null, configuration));
    document.appendChild(messageSection);

    String narrative = "<p>Before the examples.</p>" + widget("msg-1", "snip-1") + "<p>Between the examples.</p>" + widget("msg-1", "")
        + "<p>After the examples.</p>" + widget("gone", "");
    document.appendChild(section("sec-3", 3, "In the text", "TEXT", serializer.rewriteNarrative(narrative, model, configuration)));

    nu.xom.Element missingSection = section("sec-4", 4, "Removed example", "MESSAGESECTION", "<p>It was deleted.</p>");
    missingSection.appendChild(serializer.serializeReference(model, "gone", null, configuration));
    document.appendChild(missingSection);
    return new nu.xom.Document(document).toXML();
  }

  private static String export(String xml, String format) throws Exception {
    ExportConfiguration configuration = ExportConfiguration.getBasicExportConfiguration(false, ExportType.IGDOCUMENT);
    ExportParameters parameters = new ExportParameters(false, true, format, TITLE, null, configuration,
        configuration.getExportFontConfiguration(), "test", Type.IGDOCUMENT);
    try (InputStream in = new ExportServiceImpl().exportSerializedElementToHtml(xml, null, parameters)) {
      return IOUtils.toString(in, StandardCharsets.UTF_8);
    }
  }

  private static List<String> texts(Elements elements) {
    List<String> texts = new ArrayList<String>();
    for (Element element : elements) {
      texts.add(element.text());
    }
    return texts;
  }

  /** The block reduced to what a reader sees: element names, classes, links and text. */
  private static String shape(Element element) {
    StringBuilder shape = new StringBuilder();
    shape.append('<').append(element.tagName()).append('.').append(element.className());
    if (element.hasAttr("href")) {
      shape.append(" href=").append(element.attr("href"));
    }
    shape.append('>').append(element.ownText().replace(' ', ' ').trim());
    for (Element child : element.children()) {
      shape.append(shape(child));
    }
    return shape.append("</>").toString();
  }

  // ---------------------------------------------------------------- HTML

  @Test
  public void aSectionAndAWidgetShowTheSameExampleTheSameWay() {
    Elements examples = html.select("#ig-content .fr-exmsg");
    assertThat(examples).hasSize(6);
    // section then widget: the snippet, the whole message, the missing one
    assertThat(shape(examples.get(2))).isEqualTo(shape(examples.get(0)));
    assertThat(shape(examples.get(3))).isEqualTo(shape(examples.get(1)));
    assertThat(shape(examples.get(4))).isEqualTo(shape(examples.get(5)));
  }

  @Test
  public void anExampleNamesItsMessageAndSnippetAndLinksToBoth() {
    for (Element example : html.select("#ig-content .fr-exmsg:not(.fr-exmsg-missing)")) {
      Element facts = example.select("> table.fr-exmsg-info").first();
      assertThat(facts).isNotNull();
      // not one of the guide's data tables
      assertThat(facts.hasClass("ig-table")).isFalse();
      assertThat(facts.parent().hasClass("ig-table-wrap")).isFalse();
      assertThat(facts.select("thead, caption")).isEmpty();
      assertThat(facts.select("th").first().text()).isEqualTo("Message");
      assertThat(facts.select("tr").first().select("td a").attr("href")).isEqualTo(MESSAGE_PAGE);
      assertThat(facts.select("tr").first().select("td").text()).isEqualTo("Unsolicited update");
      assertThat(facts.select("tr").last().select("td a").attr("href")).isEqualTo("http://localhost:9000/example-messages/ig-1/message/msg-1"
          + (facts.select("tr").size() == 3 ? "/snippet/snip-1" : ""));
      assertThat(facts.select("tr").last().select("td").text()).isEqualTo("Open in IGAMT");
    }
    Element snippet = html.select("#ig-content .fr-exmsg").first();
    assertThat(texts(snippet.select("table.fr-exmsg-info th"))).containsExactly("Message", "Snippet", "IGAMT");
    assertThat(snippet.select("table.fr-exmsg-info tr").get(1).select("td a").attr("href")).isEqualTo(SNIPPET_PAGE);
    assertThat(snippet.select("> .fr-exmsg-description").text()).isEqualTo("The legal name, in PID-5.");
    Element whole = html.select("#ig-content .fr-exmsg").get(1);
    assertThat(texts(whole.select("table.fr-exmsg-info th"))).containsExactly("Message", "IGAMT");
    assertThat(whole.select("> .fr-exmsg-description b").text()).isEqualTo("first");
  }

  @Test
  public void theMessageKeepsItsLinesAndTheSnippetIsMarked() {
    for (Element example : html.select("#ig-content .fr-exmsg:not(.fr-exmsg-missing)")) {
      Elements lines = example.select("> .fr-exmsg-body > .fr-exmsg-line");
      assertThat(lines).hasSize(LINES.length);
      for (int i = 0; i < LINES.length; i++) {
        assertThat(lines.get(i).text().replace(' ', ' ').trim()).isEqualTo(LINES[i]);
      }
      assertThat(example.select("pre, br")).isEmpty();
    }
    for (int index : new int[] {0, 2}) {
      Elements marks = html.select("#ig-content .fr-exmsg").get(index).select(".fr-exmsg-line .fr-exmsg-mark");
      assertThat(texts(marks)).containsExactly(PATIENT_NAME);
    }
    assertThat(html.select("#ig-content .fr-exmsg").get(1).select(".fr-exmsg-mark")).isEmpty();
  }

  @Test
  public void aMissingExampleSaysSo() {
    Elements missing = html.select("#ig-content .fr-exmsg.fr-exmsg-missing");
    assertThat(missing).hasSize(2);
    for (Element example : missing) {
      assertThat(example.text()).isEqualTo("The referenced example message is no longer available.");
      assertThat(example.select("table, a")).isEmpty();
    }
  }

  @Test
  public void theTextAroundAWidgetStaysInPlace() {
    Element section = html.getElementById(html.select("#ig-sidebar .ig-toc a:contains(In the text)").attr("href").substring(1)).parent();
    List<String> order = new ArrayList<String>();
    for (Element element : section.select(".fr-view > p, .fr-view > .fr-exmsg")) {
      order.add(element.hasClass("fr-exmsg") ? "example" : element.text());
    }
    assertThat(order).containsExactly("Before the examples.", "example", "Between the examples.", "example", "After the examples.", "example");
  }

  @Test
  public void theStylesheetCoversEveryPartOfTheBlock() {
    String css = html.select("head style").html();
    for (String part : new String[] {".fr-exmsg-info", ".fr-exmsg-description", ".fr-exmsg-warning", ".fr-exmsg-body", ".fr-exmsg-line",
        ".fr-exmsg-mark", ".fr-exmsg-missing"}) {
      assertThat(css).contains(part);
    }
    // names of the former block, which nothing writes any more
    assertThat(css).doesNotContain("fr-exmsg-link").doesNotContain("fr-exmsg-file");
  }

  // ---------------------------------------------------------------- Word

  @Test
  public void wordLinksOnlyToWhatTheReaderHas() {
    // the snapshot pages are not part of the Word download: the text files beside the document are
    assertThat(wordHtml.select(".fr-exmsg a[href$=.html]")).isEmpty();
    assertThat(wordLinks).doesNotContain(MESSAGE_PAGE, SNIPPET_PAGE);
    assertThat(wordLinks).contains("ExampleMessages/VXU_V04/Unsolicited%20update.txt",
        "ExampleMessages/VXU_V04/Unsolicited%20update%20-%20Patient%20name.txt",
        "http://localhost:9000/example-messages/ig-1/message/msg-1", "http://localhost:9000/example-messages/ig-1/message/msg-1/snippet/snip-1");
    for (String link : wordLinks) {
      assertThat(link).doesNotEndWith(".html");
    }
  }

  @Test
  public void wordKeepsTheMessageAsShadedMonospaceLines() {
    Document body = Jsoup.parse(wordBody, "", org.jsoup.parser.Parser.xmlParser());
    int messageLines = 0;
    for (Element paragraph : body.select("w|p")) {
      String text = paragraph.select("w|t").text();
      if (!text.startsWith("MSH|") && !text.startsWith("PID|") && !text.startsWith("ORC|") && !text.startsWith("RXA|")) {
        continue;
      }
      messageLines++;
      assertThat(paragraph.select("w|pPr > w|pStyle").attr("w:val")).as("style of %s", text).isEqualTo("IgExample");
      assertThat(paragraph.select("w|br")).isEmpty();
    }
    // four examples of four written lines each; the empty line is a paragraph of the same style
    assertThat(messageLines).isEqualTo(16);
    assertThat(body.select("w|p:has(w|pPr > w|pStyle[w:val=IgExample])")).hasSize(20);
    assertThat(wordBody).contains("|&lt;&amp;&gt;");
  }

  @Test
  public void wordMarksTheSnippet() {
    Document body = Jsoup.parse(wordBody, "", org.jsoup.parser.Parser.xmlParser());
    List<String> marked = new ArrayList<String>();
    for (Element run : body.select("w|r:has(w|rPr > w|shd[w:fill=FFF176])")) {
      marked.add(run.select("w|t").text());
    }
    assertThat(marked).containsExactly(PATIENT_NAME, PATIENT_NAME);
  }

  @Test
  public void wordShowsTheFactsWithoutTheLookOfADataTable() {
    Document body = Jsoup.parse(wordBody, "", org.jsoup.parser.Parser.xmlParser());
    Elements tables = body.select("w|tbl:has(w|tblPr > w|tblStyle[w:val=IgExampleFacts])");
    assertThat(tables).hasSize(4);
    assertThat(tables.first().select("w|tr")).hasSize(3);
    assertThat(tables.first().text()).contains("Message").contains("Unsolicited update").contains("Snippet").contains("Patient name")
        .contains("Open in IGAMT");
    assertThat(wordBody).contains("The referenced example message is no longer available.").contains("The legal name, in PID-5.");
    // the description keeps its distance from the facts, and what is bold in it stays bold
    Element description = body.select("w|p:has(w|t:containsOwn(The legal name, in PID-5.))").first();
    assertThat(description.select("w|pPr > w|spacing").attr("w:before")).isEqualTo("100");
    assertThat(body.select("w|r:has(w|rPr > w|b):has(w|t:containsOwn(first))")).hasSize(2);
  }

  // ---------------------------------------------------------------- other configurations

  @Test
  public void withoutASnapshotThereIsNoLinkToOne() throws Exception {
    String xml = guide(configuration(ExampleMessageExportMode.INLINE, ExampleMessageSnippetExportMode.LINE));
    Document inline = Jsoup.parse(export(xml, "html"));
    Element snippet = inline.select("#ig-content .fr-exmsg").first();
    assertThat(snippet.select("table.fr-exmsg-info a[href$=.html]")).isEmpty();
    assertThat(snippet.select("table.fr-exmsg-info tr").first().select("td").text()).isEqualTo("Unsolicited update");
    // only the line of the snippet, nothing marked
    assertThat(texts(snippet.select(".fr-exmsg-body > .fr-exmsg-line"))).containsExactly(LINES[1]);
    assertThat(snippet.select(".fr-exmsg-mark")).isEmpty();
  }

  @Test
  public void aSnippetThatIsGoneIsSaidBeforeTheFullMessage() throws Exception {
    ExampleMessageExportConfiguration configuration = configuration(ExampleMessageExportMode.INLINE, ExampleMessageSnippetExportMode.LINE);
    ExampleMessageSerializationServiceImpl serializer = new ExampleMessageSerializationServiceImpl();
    IgDataModel model = model();
    nu.xom.Element document = new nu.xom.Element("Document");
    nu.xom.Element fromSection = section("sec-1", 1, "Section", "MESSAGESECTION", "<p>Text.</p>");
    fromSection.appendChild(serializer.serializeReference(model, "msg-1", "gone", configuration));
    document.appendChild(fromSection);
    document.appendChild(section("sec-2", 2, "Widget", "TEXT", serializer.rewriteNarrative(widget("msg-1", "gone"), model, configuration)));
    Elements examples = Jsoup.parse(export(new nu.xom.Document(document).toXML(), "html")).select("#ig-content .fr-exmsg");
    assertThat(examples).hasSize(2);
    for (Element example : examples) {
      List<String> parts = new ArrayList<String>();
      for (Element child : example.children()) {
        parts.add(child.className());
      }
      assertThat(parts).containsExactly("fr-exmsg-info", "fr-exmsg-description", "fr-exmsg-warning", "fr-exmsg-body");
      assertThat(example.select(".fr-exmsg-warning").text()).contains("could not be found; the full message is shown");
      assertThat(example.select(".fr-exmsg-body > .fr-exmsg-line")).hasSize(LINES.length);
    }
    assertThat(shape(examples.get(1))).isEqualTo(shape(examples.get(0)));
  }

  @Test
  public void wordKeepsTheSpacesOfAMessage() throws Exception {
    ExampleMessageExportConfiguration configuration = configuration(ExampleMessageExportMode.INLINE, ExampleMessageSnippetExportMode.LINE);
    ExampleMessageSerializationServiceImpl serializer = new ExampleMessageSerializationServiceImpl();
    IgDataModel model = model(" OBX|A\tB   C|D E ");
    nu.xom.Element document = new nu.xom.Element("Document");
    nu.xom.Element fromSection = section("sec-1", 1, "Section", "MESSAGESECTION", "<p>Text.</p>");
    fromSection.appendChild(serializer.serializeReference(model, "msg-1", null, configuration));
    document.appendChild(fromSection);
    document.appendChild(section("sec-2", 2, "Widget", "TEXT", serializer.rewriteNarrative(widget("msg-1", ""), model, configuration)));
    String forWord = export(new nu.xom.Document(document).toXML(), "docx");
    Elements lines = Jsoup.parse(forWord).select("p.IgExample");
    assertThat(lines).hasSize(2);
    for (Element line : lines) {
      assertThat(line.textNodes().get(0).getWholeText()).isEqualTo("\u00a0OBX|A\u00a0\u00a0\u00a0\u00a0B \u00a0\u00a0C|D E\u00a0");
    }

    DocumentMetadata metadata = new DocumentMetadata();
    metadata.setTitle(TITLE);
    ExportedFile word = WordUtil.convertHtmlToWord(
        new ExportedFile(IOUtils.toInputStream(forWord, StandardCharsets.UTF_8), TITLE, "id", ExportFormat.WORD), metadata, new Date(), "2.5.1");
    File docx = File.createTempFile("example-message-spaces-test", ".docx");
    try {
      try (InputStream in = word.getContent()) {
        FileUtils.copyInputStreamToFile(in, docx);
      }
      String body = XmlUtils.marshaltoString(WordprocessingMLPackage.load(docx).getMainDocumentPart().getJaxbElement(), true, false);
      assertThat(body).contains(">\u00a0OBX|A\u00a0\u00a0\u00a0\u00a0B \u00a0\u00a0C|D E\u00a0</w:t>");
    } finally {
      FileUtils.deleteQuietly(docx);
    }
  }

  @Test
  public void everyFileAnExampleLinksToIsInTheDownload() throws Exception {
    // a guide that cites only a snippet: its block links to the whole message too
    ExampleMessageExportConfiguration configuration = configuration(ExampleMessageExportMode.SEPARATE_FILES, ExampleMessageSnippetExportMode.LINE);
    ExampleMessageSerializationServiceImpl serializer = new ExampleMessageSerializationServiceImpl();
    for (boolean fromSection : new boolean[] {true, false}) {
      IgDataModel model = model();
      nu.xom.Element document = new nu.xom.Element("Document");
      if (fromSection) {
        nu.xom.Element section = section("sec-1", 1, "Section", "MESSAGESECTION", "<p>Text.</p>");
        section.appendChild(serializer.serializeReference(model, "msg-1", "snip-1", configuration));
        document.appendChild(section);
      } else {
        document.appendChild(section("sec-1", 1, "Widget", "TEXT", serializer.rewriteNarrative(widget("msg-1", "snip-1"), model, configuration)));
      }
      String xml = new nu.xom.Document(document).toXML();
      ExampleMessageExportDataService download = new ExampleMessageExportDataService();
      java.util.Set<String> wordFiles = download.collectFiles(model, configuration).keySet();
      java.util.Set<String> pageFiles = download.collectStaticPages(model, configuration).keySet();

      Elements wordTargets = Jsoup.parse(export(xml, "docx")).select(".fr-exmsg a[href]:not([href^=http])");
      Elements pageTargets = Jsoup.parse(export(xml, "html")).select(".fr-exmsg a[href]:not([href^=http])");
      assertThat(wordTargets).hasSize(2);
      assertThat(pageTargets).hasSize(2);
      for (Element link : wordTargets) {
        assertThat(wordFiles).as("files beside the Word document").contains(java.net.URLDecoder.decode(link.attr("href"), "UTF-8"));
      }
      for (Element link : pageTargets) {
        assertThat(pageFiles).as("pages beside index.html").contains(java.net.URLDecoder.decode(link.attr("href"), "UTF-8"));
      }
    }
  }

  @Test
  public void aSnapshotOnlyExampleIsItsFactsAndDescription() throws Exception {
    String xml = guide(configuration(ExampleMessageExportMode.SEPARATE_FILES, ExampleMessageSnippetExportMode.LINE));
    Document files = Jsoup.parse(export(xml, "html"));
    Element snippet = files.select("#ig-content .fr-exmsg").first();
    assertThat(snippet.select(".fr-exmsg-body")).isEmpty();
    assertThat(snippet.select("table.fr-exmsg-info a").first().attr("href")).isEqualTo(MESSAGE_PAGE);
    assertThat(Jsoup.parse(export(xml, "docx")).select(".fr-exmsg a[href$=.html]")).isEmpty();
  }
}
