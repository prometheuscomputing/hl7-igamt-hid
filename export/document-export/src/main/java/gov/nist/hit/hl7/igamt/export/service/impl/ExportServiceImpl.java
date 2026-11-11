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
package gov.nist.hit.hl7.igamt.export.service.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import gov.nist.hit.hl7.igamt.export.domain.ExportParameters;
import gov.nist.hit.hl7.igamt.export.exception.ExportException;
import gov.nist.hit.hl7.igamt.export.service.ExportService;
import gov.nist.hit.hl7.igamt.export.util.ExportHtmlEnhancer;
import gov.nist.hit.hl7.igamt.export.util.TransformationUtil;

/**
 *
 * @author Maxence Lefort on May 8, 2018.
 */
@Service("exportService")
public class ExportServiceImpl implements ExportService {

  private static final String DEFAULT_STYLESHEET = "/IGDocumentExport.xsl";

  @Override
  public InputStream exportSerializedElementToHtml(String serializedElement, String xsltPath,
      ExportParameters exportParameters) throws ExportException {
    File htmlFile = null;
    try {
      htmlFile = TransformationUtil.doTransformToTempHtml(serializedElement,
          xsltPath != null ? xsltPath : DEFAULT_STYLESHEET, exportParameters);
      try (InputStream rawHtml = FileUtils.openInputStream(htmlFile)) {
        String document = ExportHtmlEnhancer.enhance(rawHtml,
            ExportHtmlEnhancer.Target.fromFormat(exportParameters.getTargetFormat()));
        return new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8));
      }
    } catch (Exception exception) {
      throw new ExportException(exception);
    } finally {
      FileUtils.deleteQuietly(htmlFile);
    }
  }

}
