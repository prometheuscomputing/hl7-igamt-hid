package gov.nist.hit.hl7.igamt.web.app.ig;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import junit.framework.TestCase;
import gov.nist.hit.hl7.igamt.common.base.domain.ConformanceVerb;
import gov.nist.hit.hl7.igamt.common.base.domain.NarrativeRequirement;
import gov.nist.hit.hl7.igamt.common.base.domain.TextSection;
import gov.nist.hit.hl7.igamt.common.base.domain.Type;

public class NarrativeRequirementsPreservationTest extends TestCase {
  public void testTocOnlySavePreservesRequirementsAfterReorganization() throws Exception {
    TextSection previous = section("topic");
    previous.setRequirements(Arrays.asList(new NarrativeRequirement("stable", 1, ConformanceVerb.SHALL, "accept the value")));
    TextSection incoming = section("topic");
    incoming.setPosition(7);
    TextSection newParent = section("new-parent");
    newParent.setChildren(set(incoming));
    preserve(set(newParent), set(previous));
    assertEquals("stable", incoming.getRequirements().get(0).getId());
    assertEquals(7, incoming.getPosition());
  }

  public void testExplicitEmptyListClearsButMissingListDoesNot() throws Exception {
    TextSection previous = section("topic");
    previous.setRequirements(Arrays.asList(new NarrativeRequirement("stable", 1, ConformanceVerb.MAY, "accept the value")));
    TextSection incoming = section("topic");
    incoming.setRequirements(Collections.<NarrativeRequirement>emptyList());
    preserve(set(incoming), set(previous));
    assertTrue(incoming.getRequirements().isEmpty());
  }

  private void preserve(Set<TextSection> incoming, Set<TextSection> previous) throws Exception {
    Method method = IGDocumentController.class.getDeclaredMethod("preserveNarrativeRequirements", Set.class, Set.class);
    method.setAccessible(true);
    method.invoke(new IGDocumentController(), incoming, previous);
  }

  private TextSection section(String id) {
    return new TextSection(id, "", Type.TEXT, 1, id);
  }

  private Set<TextSection> set(TextSection section) {
    return new HashSet<>(Arrays.asList(section));
  }
}
