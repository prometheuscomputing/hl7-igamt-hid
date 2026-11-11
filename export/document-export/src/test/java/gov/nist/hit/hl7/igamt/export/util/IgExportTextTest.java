package gov.nist.hit.hl7.igamt.export.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.junit.Before;
import org.junit.Test;

/**
 * The rules that decide whether a text of the guide is about a field, as the exported page applies
 * them: the block of ig-export.js that holds them runs here in the JavaScript engine of the JDK.
 */
public class IgExportTextTest {

  private ScriptEngine engine;

  @Before
  public void loadTextHelpers() throws Exception {
    engine = new ScriptEngineManager().getEngineByName("javascript");
    assumeNotNull(engine); // JDK 15 and later ship without one
    String script;
    try (InputStream stream = getClass().getResourceAsStream("/templates/assets/ig-export.js")) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      for (int read; (read = stream.read(buffer)) != -1;) {
        bytes.write(buffer, 0, read);
      }
      script = new String(bytes.toByteArray(), "UTF-8");
    }
    int start = script.indexOf("/* text-helpers:start");
    int end = script.indexOf("/* text-helpers:end */");
    assertTrue("the text helpers are marked in ig-export.js", start >= 0 && end > start);
    engine.eval(script.substring(start, end));
  }

  private boolean mentions(String code, String sequence, String text) throws Exception {
    engine.put("text", text);
    return (Boolean) engine.eval("IgExportText.mentionPattern('" + code + "', '" + sequence + "').test(text)");
  }

  private boolean column(String code, String sequence, String header) throws Exception {
    engine.put("text", header);
    return (Boolean) engine.eval("IgExportText.columnPattern('" + code + "', '" + sequence + "').test(text)");
  }

  private void assertMentions(boolean expected, String code, String sequence, String text) throws Exception {
    assertEquals(code + " " + sequence + " in \"" + text + "\"", expected, mentions(code, sequence, text));
  }

  @Test
  public void aFieldIsMentionedByItsOwnNumberOnly() throws Exception {
    assertMentions(true, "PID", "1", "PID-1");
    assertMentions(true, "PID", "1", "PID-1 (Set ID) shall be valued '1'.");
    assertMentions(true, "PID", "1", "If PID-1 is valued");
    assertMentions(false, "PID", "1", "PID-10");
    assertMentions(false, "PID", "1", "PID-11.1 (Identifier)");
    assertMentions(false, "PD1", "1", "PD1-12 (Protection Indicator)");
    assertMentions(false, "PID", "3", "PID-30");
  }

  @Test
  public void subElementsAndOccurrencesCountAsTheField() throws Exception {
    assertMentions(true, "PID", "3", "PID-3.5 (Identifier Type Code)");
    assertMentions(true, "PID", "3", "PID-3[1].4");
    assertMentions(true, "PID", "3", "ORDER.PID-3");
    assertMentions(true, "PID", "3", "(PID-3)");
  }

  @Test
  public void aFlavorIsAlsoMentionedUnderTheNameOfItsBase() throws Exception {
    assertMentions(true, "PID_IZ", "3", "PID-3");
    assertMentions(true, "PID_IZ", "3", "PID_IZ-3");
    assertMentions(false, "PID_IZ", "3", "PID_LAB-3");
    assertMentions(false, "PID", "3", "PID_IZ-3");
    assertMentions(false, "PID", "3", "XPID-3");
  }

  @Test
  public void componentsAreWrittenWithADotOrADash() throws Exception {
    assertMentions(true, "CX_IZ", "5", "CX.5");
    assertMentions(true, "CX_IZ", "5", "CX-5 (Identifier Type Code)");
    assertMentions(true, "CX_IZ", "5", "CX_IZ-5");
    assertMentions(false, "CX", "5", "CX.50");
    assertMentions(false, "CX", "5", "PID-3.5");
    assertMentions(false, "CX", "5", "XCX.5");
  }

  @Test
  public void identifiersOfStatementsAreNotMentions() throws Exception {
    assertMentions(false, "PID", "3", "IZ-PID-3");
    assertMentions(false, "PID", "10", "LRI-PID-10");
    assertMentions(false, "PID", "1", "IG-PID-1");
    assertMentions(false, "PID", "01", "IZ-PID-01");
  }

  @Test
  public void codesWithRegularExpressionCharactersAreTakenLiterally() throws Exception {
    assertMentions(true, "A.B", "1", "A.B-1");
    assertMentions(false, "A.B", "1", "AxB-1");
    assertEquals("a\\.b\\(c\\)", engine.eval("IgExportText.escapeRegExp('a.b(c)')"));
    assertEquals("PID", engine.eval("IgExportText.baseOf('PID_IZ_2')"));
    assertEquals("PID", engine.eval("IgExportText.baseOf('PID')"));
  }

  @Test
  public void aLocationIsTheFieldItselfAndNothingBelowIt() throws Exception {
    engine.put("text", "PID-3");
    assertEquals(true, engine.eval("IgExportText.locationPattern('PID_IZ', '3').test(text)"));
    engine.put("text", "PID_IZ-3");
    assertEquals(true, engine.eval("IgExportText.locationPattern('PID_IZ', '3').test(text)"));
    engine.put("text", "CX.5");
    assertEquals(true, engine.eval("IgExportText.locationPattern('CX', '5').test(text)"));
    engine.put("text", "PID-3.1");
    assertEquals(false, engine.eval("IgExportText.locationPattern('PID_IZ', '3').test(text)"));
    engine.put("text", "PID-30");
    assertEquals(false, engine.eval("IgExportText.locationPattern('PID_IZ', '3').test(text)"));
    engine.put("text", "see PID-3");
    assertEquals(false, engine.eval("IgExportText.locationPattern('PID_IZ', '3').test(text)"));
  }

  @Test
  public void coConstraintColumnsAreThoseOfTheFieldItself() throws Exception {
    assertTrue(column("OBX_IZ", "3", "OBX-3 (code)"));
    assertTrue(column("OBX_IZ", "3", "OBX_IZ-3 (code)"));
    assertTrue(column("OBX", "5", "OBX-5 (varies)"));
    assertEquals(false, column("OBX_IZ", "3", "OBX-3.1 (text)"));
    assertEquals(false, column("OBX_IZ", "3", "OBX-30"));
    assertEquals(false, column("OBX_IZ", "3", "If OBX-3"));
    assertEquals(false, column("OBX_IZ", "2", "OBX-3 (code)"));
  }
}
