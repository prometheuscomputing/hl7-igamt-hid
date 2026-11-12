package gov.nist.hit.hl7.igamt.export.configuration.newModel;

import java.util.Base64;
import junit.framework.TestCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportConfiguration;

public class PublicWebExportConfigurationTest extends TestCase {
  private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aKaYAAAAASUVORK5CYII=";

  public void testIdentityAndPaletteRoundTripWithoutChangingDefaults() throws Exception {
    PublicWebExportConfiguration theme = new PublicWebExportConfiguration();
    assertTrue(theme.isIncludeExampleLibrary());
    theme.setOrganizationName("Organization"); theme.setLogoDataUrl(PNG); theme.setLogoAlt("Organization logo");
    theme.setFooterText("Publication owner"); theme.setPrimaryColor("#173E2C"); theme.setAccentColor("#D5E7BA");
    theme.setLinkColor("#21623D"); theme.setIncludeExampleLibrary(false);
    ObjectMapper mapper = new ObjectMapper();
    PublicWebExportConfiguration loaded = mapper.readValue(mapper.writeValueAsString(theme), PublicWebExportConfiguration.class);
    assertEquals(PNG, loaded.getLogoDataUrl()); assertEquals("Organization logo", loaded.getLogoAlt());
    assertEquals("#173E2C", loaded.getPrimaryColor()); assertEquals("#D5E7BA", loaded.getAccentColor());
    assertEquals("#21623D", loaded.getLinkColor()); assertFalse(loaded.isIncludeExampleLibrary());
    PublicWebExportConfiguration copy = loaded.copy(); copy.setOrganizationName("Copy"); copy.setLogoDataUrl("");
    assertEquals("Organization", loaded.getOrganizationName()); assertEquals(PNG, loaded.getLogoDataUrl());
    ExportConfiguration legacy = new ExportConfiguration(); legacy.setPublicWebConfiguration(null);
    assertEquals(PublicWebExportConfiguration.DEFAULT_PRIMARY, legacy.getPublicWebConfiguration().getPrimaryColor());
  }

  public void testInvalidAndLowContrastColorsFallBackToSafeDefaults() {
    PublicWebExportConfiguration theme = new PublicWebExportConfiguration();
    theme.setPrimaryColor("red; background:url(javascript:bad)");
    theme.setAccentColor("#fff"); theme.setLinkColor("#ffffff");
    assertEquals(PublicWebExportConfiguration.DEFAULT_PRIMARY, theme.getPrimaryColor());
    assertEquals(PublicWebExportConfiguration.DEFAULT_ACCENT, theme.getAccentColor());
    assertEquals(PublicWebExportConfiguration.DEFAULT_LINK, theme.getLinkColor());
  }

  public void testLogoRejectsRemoteSvgMislabeledAndOversizedData() {
    for (String invalid : new String[] {"https://example.test/logo.png", "data:image/svg+xml;base64,PHN2Zz4=", "data:image/png;base64,PHNjcmlwdD4=", "data:image/png;base64,not base64"}) {
      assertRejected(invalid);
    }
    assertRejected("data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[PublicWebExportConfiguration.MAX_LOGO_BYTES + 1]));
  }

  public void testFieldMappedLegacyLogoCannotBypassValidation() throws Exception {
    PublicWebExportConfiguration theme = new PublicWebExportConfiguration();
    java.lang.reflect.Field field = PublicWebExportConfiguration.class.getDeclaredField("logoDataUrl");
    field.setAccessible(true); field.set(theme, "javascript:alert(1)");
    assertEquals("", theme.getLogoDataUrl());
  }

  private void assertRejected(String value) {
    try { new PublicWebExportConfiguration().setLogoDataUrl(value); fail("Invalid logo was accepted"); }
    catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("logo")); }
  }
}
