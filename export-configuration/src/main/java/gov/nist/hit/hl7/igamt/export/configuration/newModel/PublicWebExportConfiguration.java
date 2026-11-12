package gov.nist.hit.hl7.igamt.export.configuration.newModel;

import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Publication identity only; classic document exporters do not read these settings. */
public class PublicWebExportConfiguration {
  public static final int MAX_LOGO_BYTES = 1024 * 1024;
  public static final String DEFAULT_PRIMARY = "#021a4c";
  public static final String DEFAULT_ACCENT = "#F3C60D";
  public static final String DEFAULT_LINK = "#075780";
  private static final Pattern LOGO = Pattern.compile("^data:image/(png|jpeg|webp|gif);base64,([A-Za-z0-9+/]+={0,2})$");
  private String organizationName = "";
  private String logoDataUrl = "";
  private String logoAlt = "";
  private String primaryColor = DEFAULT_PRIMARY;
  private String accentColor = DEFAULT_ACCENT;
  private String linkColor = DEFAULT_LINK;
  private String footerText = "";
  private boolean includeExampleLibrary = true;

  public String getOrganizationName() { return text(organizationName); }
  public void setOrganizationName(String value) { organizationName = text(value); }
  public String getLogoAlt() { return text(logoAlt); }
  public void setLogoAlt(String value) { logoAlt = text(value); }
  public String getFooterText() { return text(footerText); }
  public void setFooterText(String value) { footerText = text(value); }
  public String getPrimaryColor() { return color(primaryColor, DEFAULT_PRIMARY); }
  public void setPrimaryColor(String value) { primaryColor = color(value, DEFAULT_PRIMARY); }
  public String getAccentColor() { return color(accentColor, DEFAULT_ACCENT); }
  public void setAccentColor(String value) { accentColor = color(value, DEFAULT_ACCENT); }
  public String getLinkColor() { return accessibleLinkColor(linkColor); }
  public void setLinkColor(String value) { linkColor = accessibleLinkColor(value); }
  public boolean isIncludeExampleLibrary() { return includeExampleLibrary; }
  public void setIncludeExampleLibrary(boolean value) { includeExampleLibrary = value; }

  public String getLogoDataUrl() {
    // Mongo field mapping may bypass setters when reading older/corrupt documents.
    try { return validatedLogo(logoDataUrl); } catch (IllegalArgumentException invalid) { return ""; }
  }
  public void setLogoDataUrl(String value) { logoDataUrl = validatedLogo(value); }

  public PublicWebExportConfiguration copy() {
    PublicWebExportConfiguration copy = new PublicWebExportConfiguration();
    copy.setOrganizationName(getOrganizationName()); copy.setLogoAlt(getLogoAlt());
    copy.setLogoDataUrl(getLogoDataUrl()); copy.setPrimaryColor(getPrimaryColor());
    copy.setAccentColor(getAccentColor()); copy.setLinkColor(getLinkColor());
    copy.setFooterText(getFooterText()); copy.setIncludeExampleLibrary(isIncludeExampleLibrary());
    return copy;
  }

  private static String text(String value) { return value == null ? "" : value.trim(); }
  private static String color(String value, String fallback) {
    return value != null && value.matches("#[0-9a-fA-F]{6}") ? value : fallback;
  }

  private static String accessibleLinkColor(String value) {
    String candidate = color(value, DEFAULT_LINK);
    double luminance = 0;
    double[] weights = {0.2126, 0.7152, 0.0722};
    for (int channel = 0; channel < 3; channel++) {
      double srgb = Integer.parseInt(candidate.substring(1 + channel * 2, 3 + channel * 2), 16) / 255.0;
      double linear = srgb <= 0.03928 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
      luminance += weights[channel] * linear;
    }
    return 1.05 / (luminance + 0.05) >= 4.5 ? candidate : DEFAULT_LINK;
  }

  private static String validatedLogo(String value) {
    if (value == null || value.trim().isEmpty()) return "";
    if (value.length() > ((MAX_LOGO_BYTES + 2) / 3) * 4 + 40) {
      throw new IllegalArgumentException("Publication logo must be no larger than 1 MiB");
    }
    Matcher match = LOGO.matcher(value);
    if (!match.matches()) throw new IllegalArgumentException("Publication logo must be a PNG, JPEG, WebP or GIF data image");
    byte[] bytes;
    try { bytes = Base64.getDecoder().decode(match.group(2)); }
    catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Publication logo has invalid image data"); }
    if (bytes.length > MAX_LOGO_BYTES) throw new IllegalArgumentException("Publication logo must be no larger than 1 MiB");
    String type = match.group(1).toLowerCase(Locale.ROOT);
    boolean valid = false;
    if ("png".equals(type)) valid = starts(bytes, new int[] {137, 80, 78, 71, 13, 10, 26, 10});
    if ("jpeg".equals(type)) valid = starts(bytes, new int[] {255, 216, 255});
    if ("gif".equals(type)) valid = bytes.length >= 6 && (ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a"));
    if ("webp".equals(type)) valid = bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
    if (!valid) throw new IllegalArgumentException("Publication logo image type does not match its data");
    return value;
  }
  private static boolean starts(byte[] bytes, int[] signature) {
    if (bytes.length < signature.length) return false;
    for (int i = 0; i < signature.length; i++) if ((bytes[i] & 255) != signature[i]) return false;
    return true;
  }
  private static boolean ascii(byte[] bytes, int offset, String text) {
    if (bytes.length < offset + text.length()) return false;
    for (int i = 0; i < text.length(); i++) if (bytes[offset + i] != (byte) text.charAt(i)) return false;
    return true;
  }
}
