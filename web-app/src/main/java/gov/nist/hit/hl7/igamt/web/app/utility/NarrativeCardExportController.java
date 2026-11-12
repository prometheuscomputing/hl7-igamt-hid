package gov.nist.hit.hl7.igamt.web.app.utility;

import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportType;
import gov.nist.hit.hl7.igamt.export.configuration.service.ExportConfigurationService;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import gov.nist.hit.hl7.igamt.export.domain.ExportedFile;
import gov.nist.hit.hl7.igamt.export.exception.ExportException;
import gov.nist.hit.hl7.igamt.export.service.NarrativeCardExportService;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.ig.service.IgService;

/**
 * Separate public web IG ZIP export endpoint (narratives + structures).
 * Does not alter classic Word/HTML document export under /api/export/ig/{igId}/{format}.
 */
@RestController
public class NarrativeCardExportController {

  @Autowired
  private IgService igService;

  @Autowired
  private NarrativeCardExportService narrativeCardExportService;

  @Autowired private ExportConfigurationService configurations;

  @RequestMapping(value = "/api/export/ig/{igId}/narrative-cards", method = RequestMethod.POST)
  @PreAuthorize("AccessResource('IGDOCUMENT', #igId, READ)")
  public @ResponseBody void exportNarrativeCards(@PathVariable("igId") String igId, @RequestParam(required = false) String config, Authentication authentication, HttpServletResponse response)
      throws ExportException {
    try {
      Ig ig = igService.findById(igId);
      if (ig == null) {
        throw new ExportException("IG not found: " + igId);
      }
      ExportedFile exportedFile = narrativeCardExportService.exportNarrativeCardsHtml(ig, configuration(config, authentication));
      response.setContentType("application/zip");
      response.setHeader("Content-disposition", "attachment;filename=" + exportedFile.getFileName());
      FileCopyUtils.copy(exportedFile.getContent(), response.getOutputStream());
    } catch (ExportException e) {
      throw e;
    } catch (Exception e) {
      throw new ExportException(e, "Error while exporting public web IG for IG " + igId);
    }
  }
  @RequestMapping(value = "/api/export/ig/{igId}/narrative-cards/preview", method = RequestMethod.GET)
  @PreAuthorize("AccessResource('IGDOCUMENT', #igId, READ)")
  public java.util.Map<String, Object> preview(@PathVariable("igId") String igId,
      @RequestParam(required = false) String config, Authentication authentication) throws Exception {
    Ig ig = igService.findById(igId);
    if (ig == null) throw new ExportException("IG not found: " + igId);
    java.util.Map<String, Object> result = narrativeCardExportService.preview(ig, configuration(config, authentication));
    if (config != null && !config.trim().isEmpty()) {
      @SuppressWarnings("unchecked")
      java.util.List<java.util.Map<String, Object>> cards = (java.util.List<java.util.Map<String, Object>>) result.get("cards");
      for (java.util.Map<String, Object> card : cards) {
        org.jsoup.nodes.Document html = org.jsoup.Jsoup.parseBodyFragment(String.valueOf(card.get("narrativeHtml")));
        for (org.jsoup.nodes.Element link : html.select("a[href]")) {
          String href = link.attr("href");
          if (href.startsWith("/api/export/ig/" + igId + "/narrative-cards/example?")) {
            link.attr("href", href + "&config=" + java.net.URLEncoder.encode(config, "UTF-8"));
          }
        }
        card.put("narrativeHtml", html.body().html());
      }
    }
    return result;
  }

  @RequestMapping(value = "/api/export/ig/{igId}/narrative-cards/example", method = RequestMethod.GET)
  @PreAuthorize("AccessResource('IGDOCUMENT', #igId, READ)")
  public void snapshot(@PathVariable("igId") String igId, @RequestParam String path,
      @RequestParam(required = false) String config, Authentication authentication, HttpServletResponse response) throws Exception {
    Ig ig = igService.findById(igId);
    if (ig == null) { response.sendError(404); return; }
    try {
      byte[] page = narrativeCardExportService.snapshot(ig, configuration(config, authentication), path);
      response.setContentType("text/html;charset=UTF-8");
      response.setHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src data: http: https:; base-uri 'none'; form-action 'none'");
      response.getOutputStream().write(page);
    } catch (IllegalArgumentException missing) { response.sendError(404); }
  }

  private ExampleMessageExportConfiguration configuration(String id, Authentication authentication) throws ExportException {
    String username = authentication.getPrincipal().toString();
    ExportConfiguration config = id == null || id.trim().isEmpty()
        ? configurations.getConfigurationToApply(ExportType.IGDOCUMENT, username)
        : configurations.getExportConfigurationWithType(id, ExportType.IGDOCUMENT);
    if (config == null) throw new ExportException("Missing export configuration");
    if (!config.isOriginal() && !username.equals(config.getUsername())) {
      throw new org.springframework.security.access.AccessDeniedException("Export configuration is not available to this user");
    }
    return config.getIgGeneralConfiguration() == null || config.getIgGeneralConfiguration().getExampleMessages() == null
        ? new ExampleMessageExportConfiguration() : config.getIgGeneralConfiguration().getExampleMessages();
  }

}
