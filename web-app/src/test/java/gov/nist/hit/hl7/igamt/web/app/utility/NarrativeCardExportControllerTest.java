package gov.nist.hit.hl7.igamt.web.app.utility;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import org.jsoup.Jsoup;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportType;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.IgGeneralConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.service.ExportConfigurationService;
import gov.nist.hit.hl7.igamt.export.service.NarrativeCardExportService;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.ig.service.IgService;

/** Unit checks for selected export configuration propagation; not a replacement for method-security tests. */
public class NarrativeCardExportControllerTest {
  private NarrativeCardExportController controller;
  private NarrativeCardExportService publication;
  private ExportConfigurationService configurations;
  private Authentication authentication;
  private Ig ig;
  private ExportConfiguration selected;
  private ExampleMessageExportConfiguration examples;

  @Before
  public void setup() throws Exception {
    controller = new NarrativeCardExportController();
    IgService igService = mock(IgService.class);
    ig = new Ig();
    ig.setId("guide-1");
    when(igService.findById(ig.getId())).thenReturn(ig);
    publication = mock(NarrativeCardExportService.class);
    configurations = mock(ExportConfigurationService.class);
    authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn("reader");
    selected = new ExportConfiguration();
    selected.setUsername("reader");
    examples = new ExampleMessageExportConfiguration();
    IgGeneralConfiguration general = new IgGeneralConfiguration();
    general.setExampleMessages(examples);
    selected.setIgGeneralConfiguration(general);
    when(configurations.getExportConfigurationWithType("config +", ExportType.IGDOCUMENT)).thenReturn(selected);
    when(configurations.getConfigurationToApply(ExportType.IGDOCUMENT, "reader")).thenReturn(selected);
    when(publication.preview(ig, examples)).thenAnswer(invocation -> preview());
    ReflectionTestUtils.setField(controller, "igService", igService);
    ReflectionTestUtils.setField(controller, "narrativeCardExportService", publication);
    ReflectionTestUtils.setField(controller, "configurations", configurations);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void selectedConfigurationIsEncodedOnSnapshotLinksAndReusedForSnapshot() throws Exception {
    Map<String, Object> result = controller.preview(ig.getId(), "config +", authentication);
    List<Map<String, Object>> cards = (List<Map<String, Object>>) result.get("cards");
    org.jsoup.nodes.Document html = Jsoup.parse(String.valueOf(cards.get(0).get("narrativeHtml")));
    assertEquals("/api/export/ig/guide-1/narrative-cards/example?path=Examples%2Fsample.html&config=config+%2B",
        html.select("a").first().attr("href"));
    assertEquals("https://outside.example/page", html.select("a").get(1).attr("href"));
    byte[] content = "<html>Configured snapshot</html>".getBytes(StandardCharsets.UTF_8);
    when(publication.snapshot(ig, examples, "Examples/sample.html")).thenReturn(content);
    MockHttpServletResponse response = new MockHttpServletResponse();
    controller.snapshot(ig.getId(), "Examples/sample.html", "config +", authentication, response);
    assertArrayEquals(content, response.getContentAsByteArray());
    verify(publication).snapshot(ig, examples, "Examples/sample.html");
    assertTrue(response.getHeader("Content-Security-Policy").contains("default-src 'none'"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void defaultConfigurationPreviewLeavesSnapshotUrlUnchanged() throws Exception {
    Map<String, Object> result = controller.preview(ig.getId(), null, authentication);
    List<Map<String, Object>> cards = (List<Map<String, Object>>) result.get("cards");
    String href = Jsoup.parse(String.valueOf(cards.get(0).get("narrativeHtml"))).select("a").first().attr("href");
    assertFalse(href.contains("config="));
    verify(configurations).getConfigurationToApply(ExportType.IGDOCUMENT, "reader");
  }

  @Test(expected = AccessDeniedException.class)
  public void anotherUsersConfigurationCannotBeUsedForPreview() throws Exception {
    selected.setUsername("different-author");
    controller.preview(ig.getId(), "config +", authentication);
  }

  private Map<String, Object> preview() {
    Map<String, Object> card = new HashMap<>();
    card.put("narrativeHtml", "<a href=\"/api/export/ig/guide-1/narrative-cards/example?path=Examples%2Fsample.html\">Snapshot</a>"
        + "<a href=\"https://outside.example/page\">External reference</a>");
    Map<String, Object> result = new HashMap<>();
    result.put("cards", Arrays.asList(card));
    return result;
  }
}
