package gov.nist.hit.hl7.igamt.export.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

/**
 * Makes the text the authors of a guide wrote safe to publish and safe to hand to the Word importer.
 *
 * The stylesheets write rich text (section contents, comments, definition texts, descriptions) as
 * markup, and an exported guide is published to people who never used the application. Whatever
 * can run a script or load an active document is taken out: scripts, event handlers, links and
 * sources with a scheme other than the web ones, embedded documents. Markup that is not HTML at
 * all, as pasted from Word or Outlook ({@code <o:p>}, {@code v:shapes="..."}, conditional
 * comments), is taken out as well: the Word importer reads the document as XML and a single such
 * element would make the whole export fail.
 */
final class ExportHtmlSanitizer {

  /** Removed with what they hold. */
  private static final Set<String> REMOVED = new HashSet<String>(Arrays.asList("script", "object", "embed", "applet", "base", "frame",
      "frameset", "link", "meta", "template", "style", "plaintext", "animate", "animatemotion", "animatetransform", "set", "foreignobject"));
  /** Removed, what they hold is kept. */
  private static final Set<String> UNWRAPPED = new HashSet<String>(Arrays.asList("form", "noscript", "noembed", "noframes", "xml"));
  private static final Set<String> URL_ATTRIBUTES = new HashSet<String>(Arrays.asList("href", "src", "action", "formaction", "background",
      "poster", "cite", "data", "longdesc", "srcset", "ping", "manifest", "codebase"));
  private static final Set<String> LINK_SCHEMES = new HashSet<String>(Arrays.asList("http", "https", "mailto", "tel"));

  private static final int URL_START = 256;

  private static final Pattern ELEMENT_NAME = Pattern.compile("[a-z][a-z0-9]*");
  private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
  private static final Pattern SCHEME = Pattern.compile("^([a-z][a-z0-9+.-]*):");
  private static final Pattern IMAGE_DATA = Pattern.compile("^data:image/[a-z0-9.+-]*[;,]");
  private static final Pattern ACTIVE_STYLE = Pattern.compile("expression\\(|javascript:|vbscript:|-moz-binding|(?:^|;)behavior:|@import");

  private ExportHtmlSanitizer() {
  }

  /** Cleans the body of the document in place. The head, which the stylesheets write, is left alone. */
  static void clean(Document document) {
    Element body = document.body();
    if (body == null) {
      return;
    }
    removeComments(body);
    // attributes an author wrote on a second <body> or <html> are merged into the real ones by the parser
    cleanAttributes(body, "body");
    if (body.parent() != null) {
      cleanAttributes(body.parent(), "html");
    }
    for (Element element : new ArrayList<Element>(body.getAllElements())) {
      if (element == body || element.parent() == null) {
        continue;
      }
      String name = element.tagName().toLowerCase(Locale.ENGLISH);
      if (REMOVED.contains(name)) {
        element.remove();
      } else if ("iframe".equals(name)) {
        replaceFrame(element);
      } else if (UNWRAPPED.contains(name) || !ELEMENT_NAME.matcher(name).matches()) {
        element.unwrap();
      } else {
        cleanAttributes(element, name);
      }
    }
  }

  private static void removeComments(Node node) {
    for (Node child : new ArrayList<Node>(node.childNodes())) {
      if (child instanceof Comment) {
        child.remove();
      } else {
        removeComments(child);
      }
    }
  }

  /** An embedded page cannot be part of a document that is printed or read offline: its address is kept as a link. */
  private static void replaceFrame(Element frame) {
    String source = frame.attr("src").trim();
    if (isAllowedUrl(source, false) && SCHEME.matcher(normalized(source)).find()) {
      Element paragraph = frame.ownerDocument().createElement("p").addClass("ig-embedded");
      paragraph.appendText("Embedded content: ");
      paragraph.appendElement("a").attr("href", source).attr("rel", "noopener noreferrer").text(source);
      frame.replaceWith(paragraph);
    } else {
      frame.remove();
    }
  }

  private static void cleanAttributes(Element element, String elementName) {
    List<String> dropped = new ArrayList<String>();
    for (Attribute attribute : element.attributes()) {
      String key = attribute.getKey();
      String lower = key.toLowerCase(Locale.ENGLISH);
      String value = attribute.getValue();
      if (!ATTRIBUTE_NAME.matcher(key).matches() || lower.startsWith("on") || "srcdoc".equals(lower)) {
        dropped.add(key);
      } else if (URL_ATTRIBUTES.contains(lower)) {
        boolean image = "src".equals(lower) && ("img".equals(elementName) || "source".equals(elementName));
        if (!isAllowedUrl(value, image)) {
          dropped.add(key);
        }
      } else if ("style".equals(lower) && ACTIVE_STYLE.matcher(normalized(value).replaceAll("/\\*.*?\\*/", "").replace("\\", "")).find()) {
        dropped.add(key);
      }
    }
    for (String key : dropped) {
      element.removeAttr(key);
    }
    if ("a".equals(elementName) && element.hasAttr("target")) {
      element.attr("rel", "noopener noreferrer");
    }
  }

  /** Lower case, without the white space and control characters a browser ignores inside a scheme. */
  private static String normalized(String value) {
    return value.replaceAll("[\\s\\u0000-\\u0020\\u00A0\\u200B\\uFEFF]+", "").toLowerCase(Locale.ENGLISH);
  }

  /** A relative address, a fragment, or one of the schemes of the web; a picture may also carry its data. */
  private static boolean isAllowedUrl(String value, boolean image) {
    // only a picture carried as data is long, and its megabytes need no reading: its start says what it is
    String start = value.length() > URL_START && value.regionMatches(true, 0, "data:", 0, 5) ? value.substring(0, URL_START) : value;
    // as a browser reads it: tabs and line ends anywhere and the blanks in front do not count
    String url = start.replaceAll("[\\t\\r\\n]", "").replaceAll("^[\\s\\u0000-\\u0020\\u00A0\\u200B\\uFEFF]+", "").toLowerCase(Locale.ENGLISH);
    java.util.regex.Matcher scheme = SCHEME.matcher(url);
    if (!scheme.find()) {
      return true;
    }
    if ("data".equals(scheme.group(1))) {
      return image && IMAGE_DATA.matcher(url).find();
    }
    return LINK_SCHEMES.contains(scheme.group(1));
  }

  /**
   * A policy for the exported page, as a second line of defence: nothing but the script of the
   * export runs, even if a handler or a link slipped through. The page loads nothing but pictures.
   *
   * @param script the script of the export, exactly as it is written in the page
   */
  static String contentSecurityPolicy(String script) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(script.getBytes(StandardCharsets.UTF_8));
      return "default-src 'none'; script-src 'sha256-" + DatatypeConverter.printBase64Binary(digest) + "'; style-src 'unsafe-inline'; "
          + "img-src data: https: http:; media-src data: https: http:; base-uri 'none'; form-action 'none'";
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
