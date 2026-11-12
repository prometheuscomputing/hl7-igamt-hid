package gov.nist.hit.hl7.igamt.export;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.hit.hl7.igamt.common.base.domain.*;
import gov.nist.hit.hl7.igamt.conformanceprofile.service.ConformanceProfileService;
import gov.nist.hit.hl7.igamt.datatype.service.DatatypeService;
import gov.nist.hit.hl7.igamt.examples.domain.*;
import gov.nist.hit.hl7.igamt.examples.dto.SnippetRenderResult;
import gov.nist.hit.hl7.igamt.examples.dto.SnippetRenderResult.SnippetPart;
import gov.nist.hit.hl7.igamt.examples.dto.parser.Point;
import gov.nist.hit.hl7.igamt.examples.repository.IgExampleMessagesRepository;
import gov.nist.hit.hl7.igamt.examples.service.ExampleMessageExportDataService;
import gov.nist.hit.hl7.igamt.examples.service.SnippetRenderService;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.*;
import gov.nist.hit.hl7.igamt.export.service.impl.NarrativeCardExportServiceImpl;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.IgDataModel;
import gov.nist.hit.hl7.igamt.ig.service.IgService;
import gov.nist.hit.hl7.igamt.segment.service.SegmentService;
import gov.nist.hit.hl7.igamt.serialization.newImplementation.service.ExampleMessageSerializationServiceImpl;
import gov.nist.hit.hl7.igamt.serialization.util.FroalaSerializationUtil;
import gov.nist.hit.hl7.igamt.valueset.service.ValuesetService;

/** Offline integration of the publication exporter with the real shared example serializers/collectors. */
public class NarrativeCardExportServiceTest {
  private static final String ROOT = "igamt-public-ig/";
  private static final String MESSAGE = "MSH|^~\\&|SENDER\nPID|1||12345";
  private static final String EXTRACT = "PID|1||12345";
  private NarrativeCardExportServiceImpl exporter;
  private Ig ig;
  private TextSection parent;
  private TextSection messageSection;
  private MessageSnippet selectedSnippet;

  @Before
  public void setup() throws Exception {
    ig = new Ig();
    ig.setId("guide-1");
    parent = section("parent-1", "Introduction", Type.TEXT, 1);
    parent.setDescription("<p>Before example.</p><div class=\"fr-exmsg\" data-message-id=\"message-1\" data-snippet-id=\"snippet-1\"><button class=\"fr-exmsg-remove\">Remove</button><pre>STALE PREVIEW</pre></div><p>After example.</p>");
    messageSection = section("message-section-1", "Message Example", Type.MESSAGESECTION, 1);
    messageSection.setMessageId("message-1");
    messageSection.setSnippetId("snippet-1");
    TextSection child = section("child-1", "Details", Type.TEXT, 2);
    child.setDescription("<p>Details remain.</p>");
    parent.setChildren(new HashSet<>(Arrays.asList(child, messageSection)));
    ig.setContent(new HashSet<>(Arrays.asList(parent)));

    ExampleMessage message = new ExampleMessage();
    message.setId("message-1");
    message.setName("VXU example");
    message.setMessage(MESSAGE);
    message.setDescription("<p>Message description.</p>");
    MessageSnippet snippet = new MessageSnippet();
    snippet.setId("snippet-1");
    snippet.setName("PID only");
    snippet.setDescription("<p>Snippet description.</p>");
    selectedSnippet = snippet;
    message.setSnippets(Arrays.asList(snippet));
    ExampleMessage uncited = new ExampleMessage();
    uncited.setId("message-2");
    uncited.setName("Uncited example");
    uncited.setMessage("MSH|UNCITED");
    IgExampleMessages examples = new IgExampleMessages();
    examples.setId(ig.getId());
    examples.setExampleMessages(Arrays.asList(message, uncited));

    SnippetRenderResult render = new SnippetRenderResult();
    render.setSnippetId(snippet.getId());
    render.setFullMessage(MESSAGE);
    render.setRenderedContent(EXTRACT);
    SnippetPart part = new SnippetPart();
    part.setStartLine(2);
    part.setEndLine(2);
    part.setHighlightStart(new Point(2, 1));
    part.setHighlightEnd(new Point(2, EXTRACT.length() + 1));
    render.setParts(Arrays.asList(part));

    IgExampleMessagesRepository repository = mock(IgExampleMessagesRepository.class);
    when(repository.findById(ig.getId())).thenReturn(Optional.of(examples));
    SnippetRenderService snippets = mock(SnippetRenderService.class);
    when(snippets.renderSnippet(ig.getId(), "message-1", "snippet-1")).thenReturn(render);
    ExampleMessageExportDataService data = new ExampleMessageExportDataService();
    ReflectionTestUtils.setField(data, "exampleMessagesRepository", repository);
    ReflectionTestUtils.setField(data, "snippetRenderService", snippets);
    IgService igService = mock(IgService.class);
    when(igService.generateDataModel(ig)).thenAnswer(invocation -> {
      IgDataModel model = new IgDataModel();
      model.setModel(ig);
      return model;
    });
    exporter = new NarrativeCardExportServiceImpl();
    ReflectionTestUtils.setField(exporter, "igService", igService);
    ReflectionTestUtils.setField(exporter, "froalaCleaner", new FroalaSerializationUtil());
    ReflectionTestUtils.setField(exporter, "exampleData", data);
    ReflectionTestUtils.setField(exporter, "exampleSerialization", new ExampleMessageSerializationServiceImpl());
    ReflectionTestUtils.setField(exporter, "conformanceProfileService", mock(ConformanceProfileService.class));
    ReflectionTestUtils.setField(exporter, "segmentService", mock(SegmentService.class));
    ReflectionTestUtils.setField(exporter, "datatypeService", mock(DatatypeService.class));
    ReflectionTestUtils.setField(exporter, "valuesetService", mock(ValuesetService.class));
  }

  @Test
  public void allSixteenContentAndLinkCombinationsUseSharedConfiguration() throws Exception {
    for (ExampleMessageExportMode mode : ExampleMessageExportMode.values()) {
      for (ExampleMessageLinkTarget link : ExampleMessageLinkTarget.values()) {
        ExampleMessageExportConfiguration config = config(mode, link);
        Map<String, String> files = export(config);
        Document narrative = Jsoup.parse(files.get(ROOT + "sections/parent-1.html"));
        Document section = Jsoup.parse(files.get(ROOT + "sections/message-section-1.html"));
        for (Document page : Arrays.asList(narrative, section)) {
          assertEquals(mode + "/" + link, config.isInline(), !page.select(".fr-exmsg-body").isEmpty());
          assertEquals(mode + "/" + link, config.isIgamtLink(), page.select(".fr-exmsg-info a").stream().anyMatch(a -> a.text().equals("Open in IGAMT")));
          assertEquals(mode + "/" + link, config.isRelativeLink() && config.producesFiles(), page.select(".fr-exmsg-info a[href^=../]").size() > 0);
          assertFalse(page.text().contains("STALE PREVIEW"));
          assertTrue(page.select(".fr-exmsg-remove").isEmpty());
          assertTrue(page.select(".fr-exmsg-info").text().contains("VXU example"));
        }
        assertEquals(config.producesFiles(), files.containsKey(ROOT + "ExampleMessages/Profile/VXU example - PID only.txt"));
        assertEquals(config.producesFiles(), files.containsKey(ROOT + "ExampleMessages/Profile/VXU example - PID only.html"));
        assertFalse(files.containsKey(ROOT + "ExampleMessages/Profile/Uncited example.txt"));
      }
    }
  }

  @Test
  public void includeOffRemovesSectionsAndWidgetsButRetainsSurroundingNarrative() throws Exception {
    ExampleMessageExportConfiguration config = config(ExampleMessageExportMode.BOTH, ExampleMessageLinkTarget.BOTH);
    config.setInclude(false);
    Map<String, String> files = export(config);
    assertFalse(files.containsKey(ROOT + "sections/message-section-1.html"));
    Document page = Jsoup.parse(files.get(ROOT + "sections/parent-1.html"));
    assertTrue(page.select(".fr-exmsg").isEmpty());
    assertTrue(page.text().contains("Before example."));
    assertTrue(page.text().contains("After example."));
    assertFalse(files.keySet().stream().anyMatch(p -> p.contains("ExampleMessages/")));
  }

  @Test
  public void explicitExportAllRetainsTextFilesWithIncludeOff() throws Exception {
    ExampleMessageExportConfiguration config = config(ExampleMessageExportMode.NONE, ExampleMessageLinkTarget.NONE);
    config.setInclude(false);
    config.setExportAllMessages(true);
    Map<String, String> files = export(config);
    assertTrue(files.containsKey(ROOT + "ExampleMessages/Profile/Uncited example.txt"));
    assertFalse(files.keySet().stream().anyMatch(p -> p.startsWith(ROOT + "ExampleMessages/") && p.endsWith(".html")));
  }

  @Test
  public void requirementsAndHierarchyHaveStableIdBasedRoutesAfterRename() throws Exception {
    parent.setRequirements(Arrays.asList(
        new NarrativeRequirement("req-later", 2, ConformanceVerb.MAY, "accept alternatives"),
        new NarrativeRequirement("req-first", 1, ConformanceVerb.SHALL, "accept identifiers")));
    Map<String, String> before = export(config(ExampleMessageExportMode.INLINE, ExampleMessageLinkTarget.NONE));
    parent.setLabel("A completely different title");
    parent.setPosition(6);
    Map<String, String> after = export(config(ExampleMessageExportMode.INLINE, ExampleMessageLinkTarget.NONE));
    assertEquals(before.keySet(), after.keySet());
    Document page = Jsoup.parse(after.get(ROOT + "sections/parent-1.html"));
    assertNotNull(page.getElementById("req-req-first"));
    assertTrue(page.select(".requirements li").first().text().contains("accept identifiers"));
    assertTrue(page.select(".children a[href=message-section-1.html]").size() > 0);
    assertTrue(page.select(".children a[href=child-1.html]").size() > 0);
    Map<?, ?> payload = new ObjectMapper().readValue(after.get(ROOT + "data/guide.json"), Map.class);
    assertEquals(Arrays.asList("parent-1", "message-section-1", "child-1"), payload.get("order"));
    Document child = Jsoup.parse(after.get(ROOT + "sections/child-1.html"));
    assertTrue(child.select("a[href=parent-1.html]").size() > 0);
  }

  @Test
  public void highlightedVisualSnapshotKeepsExtractedTextDownload() throws Exception {
    ExampleMessageExportConfiguration config = config(ExampleMessageExportMode.BOTH, ExampleMessageLinkTarget.RELATIVE);
    config.setSnippetMode(ExampleMessageSnippetExportMode.HIGHLIGHT);
    config.setFolderName("Published Examples");
    Map<String, String> files = export(config);
    for (String path : Arrays.asList("sections/parent-1.html", "sections/message-section-1.html")) {
      Document page = Jsoup.parse(files.get(ROOT + path));
      assertEquals(EXTRACT, page.select(".fr-exmsg-mark").text());
      assertTrue(page.select(".fr-exmsg-body").text().contains("MSH|"));
    }
    // Public publication uses extracted .txt alongside highlighted visual snapshots;
    // the classic/shared service itself is unchanged.
    assertEquals(EXTRACT, files.get(ROOT + "Published Examples/Profile/VXU example - PID only.txt"));
    assertEquals(EXTRACT, Jsoup.parse(files.get(ROOT + "Published Examples/Profile/VXU example - PID only.html")).select("mark").text());
  }

  @Test
  public void exportedLocalLinksResolveWithinExtractedSite() throws Exception {
    // Include a full-message citation as well: shared collectors deliberately collect only cited targets.
    messageSection.setSnippetId(null);
    Map<String, String> files = export(config(ExampleMessageExportMode.BOTH, ExampleMessageLinkTarget.RELATIVE));
    for (Map.Entry<String, String> file : files.entrySet()) {
      if (!file.getKey().endsWith(".html")) continue;
      Document page = Jsoup.parse(file.getValue());
      for (org.jsoup.nodes.Element anchor : page.select("a[href]")) {
        String href = anchor.attr("href");
        if (href.isEmpty() || href.startsWith("#") || href.startsWith("/") || href.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) continue;
        URI resolved = new URI("https://publication.test/" + file.getKey().replace(" ", "%20")).resolve(href);
        assertTrue(file.getKey() + " -> " + href, files.containsKey(resolved.getPath().substring(1)));
      }
    }
  }

  @Test
  public void snippetOnlyCitationDoesNotLinkToAnUncollectedParentSnapshot() throws Exception {
    Map<String, String> files = export(config(ExampleMessageExportMode.BOTH, ExampleMessageLinkTarget.RELATIVE));
    assertFalse(files.containsKey(ROOT + "ExampleMessages/Profile/VXU example.html"));
    assertTrue(files.containsKey(ROOT + "ExampleMessages/Profile/VXU example - PID only.html"));
    for (String path : Arrays.asList("sections/parent-1.html", "sections/message-section-1.html")) {
      Document page = Jsoup.parse(files.get(ROOT + path));
      assertTrue(page.select(".fr-exmsg-info a[href$=example.html]").isEmpty());
      assertEquals(1, page.select(".fr-exmsg-info a[href*=PID%20only.html]").size());
      assertTrue(page.select(".fr-exmsg-info").text().contains("VXU example"));
    }
  }

  @Test
  public void descriptionFlagAndMissingReferencesAreHandledBySharedServices() throws Exception {
    ExampleMessageExportConfiguration config = config(ExampleMessageExportMode.BOTH, ExampleMessageLinkTarget.NONE);
    config.setIncludeDescription(false);
    Map<String, String> files = export(config);
    assertTrue(Jsoup.parse(files.get(ROOT + "sections/parent-1.html")).select(".fr-exmsg-description").isEmpty());
    assertTrue(Jsoup.parse(files.get(ROOT + "ExampleMessages/Profile/VXU example - PID only.html")).select(".fr-exmsg-description").isEmpty());
    messageSection.setMessageId("deleted-message");
    Document missing = Jsoup.parse(export(config).get(ROOT + "sections/message-section-1.html"));
    assertTrue(missing.text().contains("no longer available"));
    assertTrue(missing.select(".fr-exmsg-info").isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void previewRewritesExamplesAndUsesAuthenticatedExampleDownloads() throws Exception {
    ExampleMessageExportConfiguration config = config(ExampleMessageExportMode.BOTH, ExampleMessageLinkTarget.RELATIVE);
    Map<String, Object> preview = exporter.preview(ig, config);
    List<Map<String, Object>> cards = (List<Map<String, Object>>) preview.get("cards");
    assertEquals(3, cards.size());
    Document page = Jsoup.parse((String) cards.get(0).get("narrativeHtml"));
    assertFalse(page.select(".fr-exmsg-body").isEmpty());
    assertFalse(page.select("a[href*=narrative-cards/example]").isEmpty());
    config.setInclude(false);
    assertEquals(2, ((List<?>) exporter.preview(ig, config).get("cards")).size());
  }

  @Test
  public void previewSnapshotUsesConfiguredRenderingAndRejectsPathsOutsideGeneratedFiles() throws Exception {
    ExampleMessageExportConfiguration config = config(ExampleMessageExportMode.BOTH, ExampleMessageLinkTarget.RELATIVE);
    config.setSnippetMode(ExampleMessageSnippetExportMode.HIGHLIGHT);
    String path = "ExampleMessages/Profile/VXU example - PID only.html";
    Document page = Jsoup.parse(new String(exporter.snapshot(ig, config, path), StandardCharsets.UTF_8));
    assertEquals(EXTRACT, page.select("mark").text());
    assertTrue(page.select("pre").text().contains("MSH|"));
    assertTrue(page.select("a").isEmpty());
    assertEquals(ExampleMessageSnippetExportMode.HIGHLIGHT, config.getSnippetMode());
    for (String rejected : Arrays.asList("../../etc/passwd", "ExampleMessages/Profile/Uncited example.html")) {
      try {
        exporter.snapshot(ig, config, rejected);
        fail("Non-published path should be rejected: " + rejected);
      } catch (IllegalArgumentException expected) {
        assertTrue(expected.getMessage().contains("not included"));
      }
    }
    config.setInclude(false);
    try {
      exporter.snapshot(ig, config, path);
      fail("Excluded examples must not be accessible through publication preview snapshots");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("not included"));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void snippetOnlyPreviewKeepsSnippetLinkAndOmitsAbsentParentLink() throws Exception {
    ExampleMessageExportConfiguration config = config(ExampleMessageExportMode.BOTH, ExampleMessageLinkTarget.RELATIVE);
    List<Map<String, Object>> cards = (List<Map<String, Object>>) exporter.preview(ig, config).get("cards");
    for (Map<String, Object> card : cards) {
      if ("child-1".equals(card.get("id"))) continue;
      Document page = Jsoup.parse(String.valueOf(card.get("narrativeHtml")));
      assertEquals(1, page.select(".fr-exmsg-info a").size());
      String href = page.select(".fr-exmsg-info a").first().attr("href");
      assertTrue(href.contains("VXU+example+-+PID+only.html"));
      assertTrue(page.select(".fr-exmsg-info").text().contains("VXU example"));
      assertTrue(page.select("a[href^=../]").isEmpty());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void unsafeAuthoredMarkupIsAbsentFromPreviewZipAndExampleSnapshot() throws Exception {
    String unsafe = "<svg><a xlink:href=\"javascript:alert(1)\">SVG payload</a></svg>"
        + "<math><mtext onclick=\"alert(1)\">Math payload</mtext></math>"
        + "<p onclick=\"alert(1)\">Safe readable text</p>"
        + "<a href=\"javascript:alert(1)\" onfocus=\"alert(1)\">Unsafe link</a>";
    parent.setDescription(unsafe + parent.getDescription());
    selectedSnippet.setDescription(unsafe);
    ExampleMessageExportConfiguration config = config(ExampleMessageExportMode.BOTH, ExampleMessageLinkTarget.RELATIVE);
    List<Map<String, Object>> cards = (List<Map<String, Object>>) exporter.preview(ig, config).get("cards");
    for (Map<String, Object> card : cards) assertNoActiveMarkup(Jsoup.parse(String.valueOf(card.get("narrativeHtml"))));
    Map<String, String> files = export(config);
    for (Map.Entry<String, String> file : files.entrySet()) {
      if (file.getKey().endsWith(".html")) assertNoActiveMarkup(Jsoup.parse(file.getValue()));
    }
    Document snapshot = Jsoup.parse(new String(exporter.snapshot(ig, config,
        "ExampleMessages/Profile/VXU example - PID only.html"), StandardCharsets.UTF_8));
    assertNoActiveMarkup(snapshot);
    assertTrue(snapshot.text().contains("Safe readable text"));
  }

  private static void assertNoActiveMarkup(Document html) {
    assertTrue(html.select("svg,math,iframe,object,embed,form").isEmpty());
    for (org.jsoup.nodes.Element element : html.getAllElements()) {
      for (org.jsoup.nodes.Attribute attribute : element.attributes()) {
        String key = attribute.getKey().toLowerCase(Locale.ROOT);
        String value = attribute.getValue().replaceAll("[\\x00-\\x20]", "").toLowerCase(Locale.ROOT);
        assertFalse(element.tagName() + " retained " + key, key.startsWith("on") || key.contains(":"));
        if (key.equals("href") || key.equals("src")) assertFalse(value.startsWith("javascript:"));
      }
    }
  }

  private static TextSection section(String id, String title, Type type, int position) {
    return new TextSection(id, "", type, position, title);
  }

  private static ExampleMessageExportConfiguration config(ExampleMessageExportMode mode, ExampleMessageLinkTarget link) {
    ExampleMessageExportConfiguration config = new ExampleMessageExportConfiguration();
    config.setMode(mode);
    config.setLinkTarget(link);
    return config;
  }

  private Map<String, String> export(ExampleMessageExportConfiguration config) throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(exporter.exportNarrativeCardsHtml(ig, config).getContent())) {
      ZipEntry entry;
      byte[] buffer = new byte[8192];
      while ((entry = zip.getNextEntry()) != null) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int read;
        while ((read = zip.read(buffer)) != -1) bytes.write(buffer, 0, read);
        files.put(entry.getName(), new String(bytes.toByteArray(), StandardCharsets.UTF_8));
      }
    }
    return files;
  }
}
