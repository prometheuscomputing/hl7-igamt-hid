package gov.nist.hit.hl7.igamt.export.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import gov.nist.hit.hl7.igamt.export.util.ExportHtmlEnhancer.Target;

public class ExportHtmlEnhancerTest {

  private static final String SEGMENT_TABLE = "<table class=\"contentTable\" summary=\".\"><col width=\"10%\"/>"
      + "<thead><tr><th>Seq</th><th>Element name</th><th>Data type</th><th>Usage</th><th>Cardinality</th><th>Value Set</th></tr></thead>"
      + "<tbody>"
      + "<tr><td>1</td><td>Set ID</td><td>SI</td><td>R</td><td>[1..1]</td><td></td></tr>"
      + "<tr><td>2</td><td>Sex</td><td>IS</td><td>C(RE/O)</td><td>[0..1]</td><td>HL70001</td></tr>"
      + "<tr><td>3</td><td>Unused</td><td>ST</td><td>X</td><td class=\"greyCell\"></td><td></td></tr>"
      + "</tbody></table>";

  private static Document enhance(String content, Target target) throws IOException {
    return enhance(content, target, "'Times New Roman', Times, serif;", "12");
  }

  private static Document enhance(String content, Target target, String font, String size) throws IOException {
    String raw = "<html><head><title>Guide</title></head><body data-format=\"" + (target == Target.WORD ? "docx" : "html")
        + "\" data-export-type=\"IGDOCUMENT\" data-font-family=\"" + font + "\" data-font-size=\"" + size + "\">"
        + "<div id=\"ig-cover\" class=\"ig-cover\"><p class=\"ig-title\">Guide</p></div>"
        + "<div id=\"ig-content\">" + content + "</div></body></html>";
    String out = ExportHtmlEnhancer.enhance(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)), target);
    return Jsoup.parse(out);
  }

  private static String resources() {
    return "<h1 id=\"s1\" data-kind=\"TEXT\">Introduction</h1><div class=\"ig-narrative\"><div class=\"fr-view\"><div><br/>Hello</div></div></div>"
        + "<h1 id=\"s2\" data-kind=\"PROFILE\">Infrastructure</h1>"
        + "<h2 id=\"segs\" data-kind=\"SEGMENTREGISTRY\">Segments</h2>"
        + "<h3 id=\"pid\" data-kind=\"Segment\" data-code=\"PID\">PID - Patient Identification</h3>" + SEGMENT_TABLE
        + "<h2 id=\"dts\" data-kind=\"DATATYPEREGISTRY\">Data Types</h2>"
        + "<h3 id=\"7si\" data-kind=\"Datatype\" data-code=\"SI\">SI - Sequence ID</h3><p>Sequence.</p>"
        + "<h2 id=\"vss\" data-kind=\"VALUESETREGISTRY\">Value Sets</h2>"
        + "<h3 id=\"vs1\" data-kind=\"Valueset\" data-code=\"HL70001\">HL70001 - Administrative Sex</h3><p>Codes.</p>";
  }

  @Test
  public void tablesAreTypedDecoratedAndLinked() throws IOException {
    Document doc = enhance(resources(), Target.HTML);
    Element table = doc.select("table.ig-table").first();
    assertThat(table.select("col")).isEmpty();
    assertThat(table.hasAttr("summary")).isFalse();
    assertThat(table.select("td.c-usage span.ig-usage.u-r").text()).isEqualTo("R");
    assertThat(table.select("td.c-usage span.ig-usage-conditional .ig-usage-branches").text()).isEqualTo("RE/O");
    assertThat(table.select("td.ig-na")).hasSize(1);
    assertThat(table.select("td.c-datatype a.ig-ref").attr("href")).isEqualTo("#7si");
    assertThat(table.select("td.c-valueset a.ig-ref").attr("href")).isEqualTo("#vs1");
    // IS and ST are not documented in this export: plain text, no dead link
    assertThat(table.select("td.c-datatype a.ig-ref")).hasSize(1);
    assertThat(table.parent().hasClass("ig-table-wrap")).isTrue();
  }

  @Test
  public void headingsGetNumbersKindsAndSections() throws IOException {
    Document doc = enhance(resources(), Target.HTML);
    assertThat(doc.select("#s1 .ig-num").text()).isEqualTo("1");
    assertThat(doc.select("#pid .ig-num").text()).isEqualTo("2.1.1");
    assertThat(doc.select("#pid .ig-code").text()).isEqualTo("PID");
    assertThat(doc.select("#pid .ig-kind").text()).isEqualTo("Segment");
    assertThat(doc.select("section.ig-resource[data-kind=Segment] > #pid")).hasSize(1);
    assertThat(doc.select("#ig-sidebar .ig-toc a[href=#pid]")).hasSize(1);
    assertThat(doc.select(".ig-inventory").text()).contains("segment").contains("data type").contains("value set");
  }

  @Test
  public void legacyLabelsBecomeFactsAndNotesKeepTheirBody() throws IOException {
    String content = "<h1 id=\"a\" data-kind=\"TEXT\">A</h1>"
        + "<b>HL7 Version : </b>2.5.1<br/><b>Role : </b>Sender<br/>"
        + "<b>Usage notes : </b><div class=\"fr-view\"><div>Batching is out of scope for this profile and is described elsewhere.</div></div><br/>"
        + "<span><b>Conformance Statements</b></span>"
        + "<span><span><b>Predicates</b></span><table><thead><tr><th>ID</th><th>Description</th></tr></thead><tbody><tr><td>P-1</td><td>Text</td></tr></tbody></table></span>";
    Document doc = enhance(content, Target.HTML);
    assertThat(doc.select("dl.ig-meta .ig-meta-item")).hasSize(2);
    assertThat(texts(doc.select("dl.ig-meta dd"))).containsExactly("2.5.1", "Sender");
    assertThat(doc.select(".ig-note .ig-subhead").text()).isEqualTo("Usage notes");
    assertThat(doc.select(".ig-note .ig-note-body").text()).startsWith("Batching is out of scope");
    assertThat(texts(doc.select("p.ig-subhead"))).contains("Conformance Statements", "Predicates");
    assertThat(doc.select("#ig-content br, #ig-content hr")).isEmpty();
  }

  @Test
  public void headerOnlyTableIsReplacedByAnExplanation() throws IOException {
    String content = "<h1 id=\"a\" data-kind=\"TEXT\">A</h1><table><thead><tr><th>Seq</th><th>Usage</th></tr></thead><tbody></tbody></table>";
    Document doc = enhance(content, Target.HTML);
    assertThat(doc.select("#ig-content table")).isEmpty();
    assertThat(doc.select("p.ig-empty").text()).contains("Nothing to list");
  }

  @Test
  public void messageStructureBecomesATree() throws IOException {
    String content = "<h1 id=\"a\" data-kind=\"TEXT\">A</h1><table><thead><tr><th>Path</th><th>Segment</th><th>Flavor</th><th>Element name</th><th>Usage</th><th>Cardinality</th></tr></thead><tbody>"
        + "<tr><td>1</td><td>MSH</td><td>MSH</td><td>Header</td><td>R</td><td>[1..1]</td></tr>"
        + "<tr><td></td><td>[</td><td></td><td>BEGIN PATIENT GROUP</td><td>RE</td><td>[0..1]</td></tr>"
        + "<tr><td>2.1</td><td>....PV1</td><td>PV1</td><td>Visit</td><td>R</td><td>[1..1]</td></tr>"
        + "<tr><td></td><td>]</td><td></td><td>END PATIENT GROUP</td><td></td><td></td></tr>"
        + "</tbody></table>";
    Document doc = enhance(content, Target.HTML);
    Element table = doc.select("table.ig-structure").first();
    assertThat(table).isNotNull();
    assertThat(table.select("tr.ig-group-begin")).hasSize(1);
    assertThat(table.select("tr.ig-group-end")).hasSize(1);
    assertThat(table.select("tr.ig-group-begin").text()).contains("Group PATIENT");
    assertThat(table.select("tr.ig-group-end").text()).contains("End of group PATIENT");
    assertThat(table.select("tbody tr").get(2).select(".ig-seg").text()).isEqualTo("PV1");
    assertThat(table.select("tbody tr").get(2).attr("data-depth")).isEqualTo("1");
  }

  @Test
  public void defaultFontIsLeftToTheDesignAndCustomFontIsHonoured() throws IOException {
    assertThat(enhance(resources(), Target.HTML).select("head style").html()).doesNotContain("Times New Roman");
    assertThat(enhance(resources(), Target.HTML, "Georgia, serif;", "11").select("head style").html()).contains("Georgia");
  }

  @Test
  public void wordTargetUsesWordStylesAndSafeBookmarks() throws IOException {
    Document doc = enhance(resources(), Target.WORD);
    assertThat(doc.select("script")).isEmpty();
    assertThat(doc.select("#ig-sidebar, #ig-search")).isEmpty();
    assertThat(doc.select("[id=7si]")).isEmpty();
    assertThat(doc.select("[id=ig_7si]")).hasSize(1);
    assertThat(doc.select("a[href=#ig_7si]")).isNotEmpty();
    assertThat(doc.select("table.IgTable")).hasSize(1);
    assertThat(doc.select("[data-kind], [data-code]")).isEmpty();
    assertThat(doc.text()).contains(WordUtil.TOC_MARKER);
    assertThat(doc.select("p.Title").text()).isEqualTo("Guide");
  }

  @Test
  public void shortLabelledValuesKeepLinksAndPictures() throws IOException {
    String content = "<h1 id=\"a\" data-kind=\"TEXT\">A</h1>"
        + "<b>Reference : </b><a href=\"https://example.org/spec\">the spec</a><br/>"
        + "<b>Logo : </b><img src=\"data:image/png;base64,AAAA\" alt=\"logo\"/><br/>"
        + "Plain <i>italic</i> and <a href=\"https://example.org\">link</a> tail";
    Document doc = enhance(content, Target.HTML);
    assertThat(doc.select("dl.ig-meta dd a[href=https://example.org/spec]").text()).isEqualTo("the spec");
    assertThat(doc.select("#ig-content img[alt=logo]")).hasSize(1);
    assertThat(doc.select("#ig-content p").last().html()).contains("<i>italic</i>").contains("tail");
    assertThat(doc.select("#ig-content p").last().text()).isEqualTo("Plain italic and link tail");
  }

  @Test
  public void embeddedPageFurnitureDoesNotBecomeSections() throws IOException {
    String content = "<h1 id=\"dts\" data-kind=\"DATATYPEREGISTRY\">Data Types</h1>"
        + "<h2 id=\"hd\" data-kind=\"Datatype\" data-code=\"HD_01\"><span class=\"masterDatatypeLabel\">MAS</span> - HD_01-Hierarchic Designator</h2>"
        + "<style>table, th, td { border: 2px solid black; }</style>"
        + "<center><table width=\"800\"><tr><td><div align=\"center\"><h2><u><b>HD_01 banner</b></u></h2></div></td></tr></table></center>"
        + "<table><thead><tr><th>Seq</th><th>Usage</th></tr></thead><tbody><tr><td>1</td><td>R</td></tr></tbody></table>";
    Document doc = enhance(content, Target.HTML);
    assertThat(doc.select("body style")).isEmpty();
    assertThat(doc.select("#ig-sidebar .ig-toc a")).hasSize(2);
    assertThat(doc.select(".ig-inventory").text()).contains("1").doesNotContain("2");
    assertThat(doc.select("#hd .ig-code").text()).isEqualTo("HD_01");
    assertThat(doc.select("#hd .ig-rname").text()).isEqualTo("Hierarchic Designator");
    assertThat(texts(doc.select("p.ig-subhead"))).contains("HD_01 banner");
  }

  @Test
  public void wordBookmarksStayUniqueAndPreKeepsItsLines() throws IOException {
    String longId = "0123456789012345678901234567890123456789";
    String content = "<h1 id=\"a-b\" data-kind=\"TEXT\">One</h1><h1 id=\"a_b\" data-kind=\"TEXT\">Two</h1>"
        + "<h1 id=\"" + longId + "X\" data-kind=\"TEXT\">Three</h1><h1 id=\"" + longId + "Y\" data-kind=\"TEXT\">Four</h1>"
        + "<div class=\"ig-narrative\"><pre>MSH|a\n  PID|b</pre></div>";
    Document doc = enhance(content, Target.WORD);
    java.util.Set<String> ids = new java.util.HashSet<String>();
    for (Element element : doc.select("[id]")) {
      assertThat(ids.add(element.id())).as("duplicate id %s", element.id()).isTrue();
      assertThat(element.id().length()).isLessThanOrEqualTo(40);
    }
    assertThat(ids).contains("ig_a_b", "ig_a_b_2");
    Element code = doc.select("p.IgCodeBlock").first();
    assertThat(code.select("br")).hasSize(1);
    assertThat(code.text()).contains("MSH|a").contains("PID|b");
  }

  @Test
  public void tablesThatHoldMoreThanABannerKeepTheirContent() throws IOException {
    String content = "<h1 id=\"a\" data-kind=\"TEXT\">A</h1>"
        + "<table><tr><td><h2>One</h2><h3>Two</h3></td></tr></table>"
        + "<table><tr><td><h2>Title</h2><p>Important description</p></td></tr></table>"
        + "<i>Leading</i> inline text";
    Document doc = enhance(content, Target.HTML);
    assertThat(doc.select("#ig-sidebar .ig-toc a")).hasSize(1);
    assertThat(doc.select("#ig-content").text()).contains("One").contains("Two").contains("Title").contains("Important description");
    assertThat(doc.select("#ig-content p").last().text()).isEqualTo("Leading inline text");
  }

  @Test
  public void resourcesListWhereTheyAreUsed() throws IOException {
    Document doc = enhance(resources(), Target.HTML);

    // field rows get an address of their own
    assertThat(doc.select("tr#pid-1 td.c-name").text()).isEqualTo("Set ID");
    assertThat(doc.select("tr#pid-3")).hasSize(1);

    Element datatype = doc.select("section.ig-resource[data-kind=Datatype]").first();
    Element used = datatype.select("details.ig-used").first();
    assertThat(used).isNotNull();
    assertThat(used.previousElementSibling().id()).isEqualTo("7si");
    assertThat(used.select(".ig-used-summary").text()).isEqualTo("Used by 1 segment");
    assertThat(used.select(".ig-used-kind").text()).isEqualTo("Segment");
    assertThat(used.select(".ig-used-entry > a.ig-ref").attr("href")).isEqualTo("#pid");
    assertThat(used.select(".ig-used-entry > a.ig-ref").text()).isEqualTo("PID");
    assertThat(used.select("a.ig-ref-row").attr("href")).isEqualTo("#pid-1");
    assertThat(used.select("a.ig-ref-row").text()).isEqualTo("PID-1");

    Element valueSet = doc.select("section.ig-resource[data-kind=Valueset]").first();
    assertThat(valueSet.select("details.ig-used a.ig-ref-row").text()).isEqualTo("PID-2");

    // nothing refers to the segment itself, and a resource never lists itself
    assertThat(doc.select("section.ig-resource[data-kind=Segment] details.ig-used")).isEmpty();
    assertThat(doc.select("details.ig-used")).hasSize(2);
  }

  @Test
  public void whereUsedCountsEachUserOnceAndGroupsByKind() throws IOException {
    String content = "<h1 id=\"p\" data-kind=\"PROFILE\">Infrastructure</h1>"
        + "<h2 id=\"cps\" data-kind=\"CONFORMANCEPROFILEREGISTRY\">Profiles</h2>"
        + "<h3 id=\"z22\" data-kind=\"ConformanceProfile\" data-code=\"Z22\">Z22</h3>"
        + "<table><thead><tr><th>Location</th><th>Value Set ID</th></tr></thead><tbody><tr><td>Z22-3.10</td><td>HL70001</td></tr></tbody></table>"
        + "<h2 id=\"segs\" data-kind=\"SEGMENTREGISTRY\">Segments</h2>"
        + "<h3 id=\"pid\" data-kind=\"Segment\" data-code=\"PID\">PID - Patient</h3>"
        + "<table><thead><tr><th>Seq</th><th>Element name</th><th>Data type</th><th>Usage</th><th>Value Set</th></tr></thead><tbody>"
        + "<tr><td>8</td><td>Sex</td><td>IS</td><td>R</td><td>HL70001</td></tr>"
        + "<tr><td>9</td><td>Other</td><td>IS</td><td>O</td><td>HL70001</td></tr></tbody></table>"
        + "<table><thead><tr><th>Location</th><th>Value Set ID</th></tr></thead><tbody><tr><td>PID-8</td><td>HL70001</td></tr></tbody></table>"
        + "<h3 id=\"nk1\" data-kind=\"Segment\" data-code=\"NK1\">NK1 - Next of kin</h3>"
        + "<table><thead><tr><th>Seq</th><th>Element name</th><th>Data type</th><th>Usage</th><th>Value Set</th></tr></thead><tbody>"
        + "<tr><td>15</td><td>Sex</td><td>IS</td><td>R</td><td>HL70001</td></tr></tbody></table>"
        + "<h2 id=\"vss\" data-kind=\"VALUESETREGISTRY\">Value Sets</h2>"
        + "<h3 id=\"vs1\" data-kind=\"Valueset\" data-code=\"HL70001\">HL70001 - Administrative Sex</h3><p>Codes.</p>";
    Document doc = enhance(content, Target.HTML);
    Element used = doc.select("section.ig-resource[data-kind=Valueset] details.ig-used").first();
    assertThat(used.select(".ig-used-summary").text()).isEqualTo("Used by 1 conformance profile and 2 segments");
    assertThat(texts(used.select(".ig-used-kind"))).containsExactly("Conformance profile", "Segments");
    assertThat(texts(used.select(".ig-used-entry > a.ig-ref"))).containsExactly("Z22", "PID", "NK1");
    // PID binds it on two fields and repeats one of them in its bindings table: listed once each
    assertThat(texts(used.select("a.ig-ref-row"))).containsExactly("PID-8", "PID-9", "NK1-15");
    assertThat(used.select("a.ig-ref-row").last().attr("href")).isEqualTo("#nk1-15");
  }

  @Test
  public void wordListsWhereUsedAtTheEndOfTheResource() throws IOException {
    Document doc = enhance(resources(), Target.WORD);
    assertThat(doc.select("details")).isEmpty();
    Element block = doc.select("div.ig-used").first();
    assertThat(block).isNotNull();
    assertThat(block.select("p.IgSubhead").text()).isEqualTo("Where used");
    assertThat(block.select("p.IgMeta").text()).isEqualTo("Segment: PID (PID-1)");
    assertThat(block.select("a[href=#ig_pid]")).hasSize(1);
    // rows are not bookmarks in Word, so only the resource is a link
    assertThat(block.select("a")).hasSize(1);
    assertThat(doc.select("tr[id]")).isEmpty();
    // it follows the content of the datatype it belongs to
    assertThat(block.previousElementSibling().text()).isEqualTo("Sequence.");
  }

  @Test
  public void repeatedIdsKeepOnlyTheirFirstElement() throws IOException {
    String content = "<h1 id=\"a\" data-kind=\"TEXT\">A</h1>"
        + "<table id=\"parent\"><thead><tr><th>Seq</th><th>Usage</th></tr></thead><tbody><tr><td>1</td><td>R</td></tr></tbody></table>"
        + "<table id=\"parent\"><thead><tr><th>Seq</th><th>Usage</th></tr></thead><tbody><tr><td>2</td><td>O</td></tr></tbody></table>"
        + "<h1 id=\"a\" data-kind=\"TEXT\">B</h1>";
    Document doc = enhance(content, Target.HTML);
    java.util.Set<String> ids = new java.util.HashSet<String>();
    for (Element element : doc.select("[id]")) {
      assertThat(ids.add(element.id())).as("duplicate id %s", element.id()).isTrue();
    }
    assertThat(doc.select("table#parent")).hasSize(1);
    // the second heading still gets an address of its own, and both are in the contents
    assertThat(doc.select("#ig-sidebar .ig-toc a")).hasSize(2);
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
