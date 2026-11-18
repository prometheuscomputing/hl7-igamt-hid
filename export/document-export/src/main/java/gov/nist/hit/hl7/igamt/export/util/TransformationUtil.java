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
package gov.nist.hit.hl7.igamt.export.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.UUID;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.FileUtils;

import gov.nist.hit.hl7.igamt.export.domain.ExportParameters;
import gov.nist.hit.hl7.igamt.export.domain.XSLTIncludeUriResolver;

/**
 *
 * @author Maxence Lefort on May 8, 2018.
 */
public class TransformationUtil {

  public static File doTransformToTempHtml(String xmlContent, String xsltPath, ExportParameters exportParameters) throws IOException, TransformerException {
    File tmpXmlFile = File.createTempFile("temp" + UUID.randomUUID().toString(), ".xml");
    File tmpHtmlFile = File.createTempFile("temp" + UUID.randomUUID().toString(), ".html");
    boolean done = false;
    try (InputStream stylesheet = TransformationUtil.class.getResourceAsStream(xsltPath)) {
      FileUtils.writeStringToFile(tmpXmlFile, xmlContent, Charset.forName("UTF-8"));
      TransformerFactory factoryTf = TransformerFactory.newInstance("org.apache.xalan.processor.TransformerFactoryImpl", null);
      factoryTf.setURIResolver(new XSLTIncludeUriResolver());
      Source xslt = new StreamSource(stylesheet);
      Transformer transformer = factoryTf.newTransformer(xslt);
      Map<String, String> exportPararMap = exportParameters.toMap();
      for (Map.Entry<String, String> param : exportPararMap.entrySet()) {
        if (param != null && param.getKey() != null && param.getValue() != null) {
          transformer.setParameter(param.getKey(), param.getValue());
        }
      }
      transformer.setErrorListener(STRICT);
      transformer.transform(new StreamSource(tmpXmlFile), new StreamResult(tmpHtmlFile));
      done = true;
      return tmpHtmlFile;
    } finally {
      // the XML holds the whole guide: never leave it behind, nor a half-written result
      FileUtils.deleteQuietly(tmpXmlFile);
      if (!done) {
        FileUtils.deleteQuietly(tmpHtmlFile);
      }
    }
  }

  /**
   * Xalan's default listener prints stylesheet errors and carries on, which yields a truncated
   * document that still looks like a successful export. Fail instead.
   */
  private static final ErrorListener STRICT = new ErrorListener() {
    @Override
    public void warning(TransformerException exception) {
      // warnings do not affect the output
    }

    @Override
    public void error(TransformerException exception) throws TransformerException {
      throw exception;
    }

    @Override
    public void fatalError(TransformerException exception) throws TransformerException {
      throw exception;
    }
  };

}
