package gov.nist.hit.hl7.igamt.export.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

/** What the authors of a guide wrote, before it is published or handed to the Word importer. */
public class ExportHtmlSanitizerTest {

  private static Document clean(String body) {
    Document document = Jsoup.parse("<html><head><meta charset=\"utf-8\"><title>t</title></head><body>" + body + "</body></html>");
    ExportHtmlSanitizer.clean(document);
    return document;
  }

  @Test
  public void nothingAnAuthorWroteCanRunAScript() {
    Document document = clean("<p onclick=\"alert(1)\" ONMOUSEOVER='x()'>text</p><img src=\"x\" onerror=\"alert(1)\">"
        + "<script>alert(2)</script><a href=\"javascript:alert(3)\">go</a><a href=\" java\tscript:alert(3)\">tab</a>"
        + "<a href=\"JaVaScRiPt:alert(3)\">case</a><a href=\"vbscript:x\">vb</a><a href=\"data:text/html,<script>alert(1)</script>\">data</a>"
        + "<svg onload=\"alert(4)\"><animate attributeName=\"href\" values=\"javascript:alert(1)\"></animate><circle r=\"3\"></circle></svg>"
        + "<object data=\"x.swf\"></object><embed src=\"x.swf\"><form action=\"https://example.org/steal\"><input name=\"a\"><button formaction=\"javascript:x\">b</button></form>"
        + "<div style=\"width: expression(alert(1))\">old</div><div style=\"background: url('java&#115;cript:alert(1)')\">bg</div>"
        + "<base href=\"https://example.org/\"><meta http-equiv=\"refresh\" content=\"0;url=https://example.org\"><link rel=\"stylesheet\" href=\"https://example.org/x.css\">"
        + "<iframe srcdoc=\"<script>alert(1)</script>\"></iframe><img src=\"data:text/html;base64,PHNjcmlwdD4=\">");
    String html = document.body().html().toLowerCase();
    assertThat(html).doesNotContain("<script").doesNotContain("alert(").doesNotContain("javascript:").doesNotContain("vbscript:")
        .doesNotContain("onclick").doesNotContain("onmouseover").doesNotContain("onerror").doesNotContain("onload")
        .doesNotContain("<object").doesNotContain("<embed").doesNotContain("<form").doesNotContain("<base").doesNotContain("<meta")
        .doesNotContain("<link").doesNotContain("<iframe").doesNotContain("srcdoc").doesNotContain("expression(").doesNotContain("data:text")
        .doesNotContain("formaction").doesNotContain("<animate");
    // what is harmless stays
    assertThat(document.select("p").first().text()).isEqualTo("text");
    assertThat(document.select("a")).hasSize(5);
    assertThat(document.select("input, button")).hasSize(2);
    assertThat(document.select("circle")).hasSize(1);
    // the head the stylesheets wrote is not touched
    assertThat(document.head().select("meta")).hasSize(1);
  }

  @Test
  public void ordinaryRichTextIsKeptAsItIs() {
    String body = "<h3 id=\"sec-1\" class=\"a b\">Title</h3><p style=\"color: #c00; text-align: center\">Text with <strong>bold</strong>, "
        + "<a href=\"https://www.cdc.gov/vaccines/\" title=\"t\">a link</a>, <a href=\"#sec-1\">an anchor</a>, <a href=\"mailto:a@example.org\">mail</a>, "
        + "<a href=\"ExampleMessages/Z22/VXU%20dose.html\">a file</a>, <a href=\"tel:+1555\">phone</a>.</p>"
        + "<table data-rows=\"2\"><tbody><tr><td colspan=\"2\" style=\"width: 50%\">cell</td></tr></tbody></table>"
        + "<img alt=\"dot\" width=\"8\" src=\"data:image/png;base64,iVBORw0KGgo=\"><img src=\"/api/storage/file?name=a.png\"><img src=\"https://example.org/a.png\">"
        + "<ul><li>one</li></ul><pre>MSH|^~\\&amp;|</pre>";
    assertThat(clean(body).body().html().replaceAll("\\s+", "")).isEqualTo(Jsoup.parse(body).body().html().replaceAll("\\s+", ""));
  }

  @Test
  public void markupPastedFromOfficeDoesNotReachTheWordImporter() {
    Document document = clean("<div v:shapes=\"x\" \"foo=\"1\" a<b=\"2\" lang=\"en\">Hello<o:p></o:p> <st1:place w:st=\"on\">Geneva</st1:place>"
        + "<!--[if gte mso 9]><xml><w:WordDocument></w:WordDocument></xml><![endif]--><span>a<!-- x -- y --->b</span></div>");
    String html = document.body().html();
    assertThat(html).doesNotContain("o:p").doesNotContain("st1:").doesNotContain("v:shapes").doesNotContain("<!--").doesNotContain("foo").doesNotContain("w:st");
    assertThat(document.body().text()).isEqualTo("Hello Genevaab");
    assertThat(document.select("div").first().attr("lang")).isEqualTo("en");
    // the Word importer reads the document as XML
    document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
    try {
      javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
          .parse(new org.xml.sax.InputSource(new java.io.StringReader("<r>" + document.body().html() + "</r>")));
    } catch (Exception e) {
      throw new AssertionError("not well-formed: " + e.getMessage() + "\n" + document.body().html());
    }
  }

  @Test
  public void anEmbeddedPageBecomesALinkToIt() {
    Document document = clean("<p>Watch:</p><iframe src=\"https://www.youtube.com/embed/abc\" width=\"640\"></iframe><iframe src=\"javascript:alert(1)\"></iframe>");
    assertThat(document.select("iframe")).isEmpty();
    assertThat(document.select("p.ig-embedded a").attr("href")).isEqualTo("https://www.youtube.com/embed/abc");
    assertThat(document.select("a")).hasSize(1);
  }

  @Test
  public void aLinkThatOpensAnotherWindowDoesNotHandItThePage() {
    assertThat(clean("<a href=\"https://example.org\" target=\"_blank\">x</a>").select("a").attr("rel")).isEqualTo("noopener noreferrer");
  }

  @Test
  public void thePolicyNamesTheScriptOfThePage() {
    // sha256 of "alert(1)", base64
    assertThat(ExportHtmlSanitizer.contentSecurityPolicy("alert(1)")).contains("script-src 'sha256-bhHHL3z2vDgxUt0W3dWQOrprscmda2Y5pLsLg4GF+pI='")
        .contains("default-src 'none'").contains("base-uri 'none'");
  }

  @Test
  public void attributesWrittenOnASecondBodyOrHtmlDoNotReachTheRealOnes() {
    Document document = clean("<p>x</p><body onload=\"alert(1)\" v:shapes=\"x\" class=\"kept\"><html onclick=\"alert(2)\" o:x=\"1\">");
    assertThat(document.body().hasAttr("onload")).isFalse();
    assertThat(document.body().hasAttr("v:shapes")).isFalse();
    assertThat(document.body().hasClass("kept")).isTrue();
    assertThat(document.select("html").first().attributes().size()).isEqualTo(0);
  }

  @Test
  public void picturesLinksAndStylesThatAreHarmlessAreKept() {
    Document document = clean("<img id=\"tiff\" src=\"data:image/tiff;base64,AAAA\"><img id=\"ico\" src=\"data:image/vnd.microsoft.icon;base64,AAAA\">"
        + "<img id=\"none\" src=\"data:image/;base64,AAAA\"><img id=\"page\" src=\"data:text/html;base64,AAAA\">"
        + "<a id=\"file\" href=\"Section 1: notes.html\">a</a><a id=\"query\" href=\"https://example.org/?q=javascript:x\">b</a>"
        + "<a id=\"spaced\" href=\"  \n javascript:alert(1)\">c</a><a id=\"ftp\" href=\"ftp://example.org/a\">d</a>"
        + "<p id=\"smooth\" style=\"scroll-behavior: smooth; color: red\">e</p><p id=\"htc\" style=\"color: red; behavior: url(x.htc)\">f</p>"
        + "<svg><style><img src=x onerror=alert(3)></style><circle r=\"1\"></circle></svg>");
    assertThat(document.select("#tiff[src], #ico[src], #none[src]")).hasSize(3);
    assertThat(document.select("#page").hasAttr("src")).isFalse();
    assertThat(document.select("#file").attr("href")).isEqualTo("Section 1: notes.html");
    assertThat(document.select("#query").hasAttr("href")).isTrue();
    assertThat(document.select("#spaced").hasAttr("href")).isFalse();
    assertThat(document.select("#ftp").hasAttr("href")).isFalse();
    assertThat(document.select("#smooth").attr("style")).contains("scroll-behavior");
    assertThat(document.select("#htc").hasAttr("style")).isFalse();
    assertThat(document.body().html()).doesNotContain("onerror").doesNotContain("<style");
    assertThat(document.select("circle")).hasSize(1);
  }

  @Test
  public void paddingInFrontOfAnAddressOrInsideItsSchemeHidesNothing() {
    StringBuilder blanks = new StringBuilder();
    StringBuilder tabs = new StringBuilder();
    for (int i = 0; i < 400; i++) {
      blanks.append(' ');
      tabs.append('\t');
    }
    Document document = clean("<a id=\"a\" href=\"" + blanks + "javascript:alert(1)\">a</a><img id=\"b\" src=\"" + blanks + "file:///etc/a.png\">"
        + "<a id=\"c\" href=\"j" + tabs + "avascript:alert(1)\">c</a><a id=\"d\" href=\"data:" + tabs + "text/html,x\">d</a>");
    assertThat(document.select("#a[href], #b[src], #c[href], #d[href]")).isEmpty();
  }
}
