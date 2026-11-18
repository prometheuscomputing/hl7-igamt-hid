package gov.nist.hit.hl7.igamt.serialization.util;

/** The characters an XML 1.0 document can carry. Text pasted from elsewhere often holds others, and one is enough to make a document unreadable. */
public final class XmlCharacters {

  private XmlCharacters() {
  }

  /** The text without the characters XML cannot carry: control characters, halves of a pair, U+FFFE and U+FFFF. */
  public static String keepValid(String text) {
    StringBuilder kept = new StringBuilder(text.length());
    for (int i = 0; i < text.length();) {
      int point = text.codePointAt(i);
      i += Character.charCount(point);
      if (point == 0x9 || point == 0xA || point == 0xD || (point >= 0x20 && point <= 0xD7FF) || (point >= 0xE000 && point <= 0xFFFD)
          || (point >= 0x10000 && point <= 0x10FFFF)) {
        kept.appendCodePoint(point);
      }
    }
    return kept.toString();
  }
}
