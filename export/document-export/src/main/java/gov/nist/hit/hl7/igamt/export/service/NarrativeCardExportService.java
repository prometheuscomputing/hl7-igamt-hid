package gov.nist.hit.hl7.igamt.export.service;

import gov.nist.hit.hl7.igamt.export.domain.ExportedFile;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;

/**
 * Generates a web-first narrative-card HTML export from an IG's TextSection tree.
 * Separate from the classic Word/HTML document export pipeline.
 */
public interface NarrativeCardExportService {

  /**
   * Build a self-contained HTML publication of narrative cards from {@link Ig#getContent()}.
   */
  ExportedFile exportNarrativeCardsHtml(Ig ig, gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration configuration) throws Exception;
  java.util.Map<String, Object> preview(Ig ig, gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration configuration) throws Exception;
  byte[] snapshot(Ig ig, gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration configuration, String path) throws Exception;
}
