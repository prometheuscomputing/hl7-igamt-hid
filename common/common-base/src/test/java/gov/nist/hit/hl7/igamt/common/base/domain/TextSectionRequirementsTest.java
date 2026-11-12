package gov.nist.hit.hl7.igamt.common.base.domain;

import java.util.Arrays;
import java.util.HashSet;
import junit.framework.TestCase;

public class TextSectionRequirementsTest extends TestCase {
  public void testCopyPreservesOrderAndLinksButCreatesIndependentRequirementIdentities() {
    TextSection source = new TextSection("section", "<p>Narrative</p>", Type.TEXT, 4, "Parent");
    source.setRequirements(Arrays.asList(new NarrativeRequirement("requirement", 2, ConformanceVerb.SHOULD, "accept the identifier")));
    TextSection child = new TextSection("child", "", Type.MESSAGESECTION, 7, "Example");
    child.setMessageId("message");
    child.setSnippetId("snippet");
    source.setChildren(new HashSet<>(Arrays.asList(child)));

    TextSection copy = source.clone();
    assertFalse(source.getId().equals(copy.getId()));
    assertEquals(4, copy.getPosition());
    NarrativeRequirement copiedRequirement = copy.getRequirements().get(0);
    assertFalse("requirement".equals(copiedRequirement.getId()));
    assertEquals(2, copiedRequirement.getPosition());
    assertEquals(ConformanceVerb.SHOULD, copiedRequirement.getVerb());
    copiedRequirement.setText("changed copy");
    assertEquals("accept the identifier", source.getRequirements().get(0).getText());
    TextSection copiedChild = copy.getChildren().iterator().next();
    assertEquals(7, copiedChild.getPosition());
    assertEquals("message", copiedChild.getMessageId());
    assertEquals("snippet", copiedChild.getSnippetId());
    assertFalse(child.getId().equals(copiedChild.getId()));
  }

  public void testLegacyNarrativeWithoutRequirementsStillCopies() {
    TextSection source = new TextSection("legacy", "<p>Existing content</p>", Type.TEXT, 1, "Legacy");
    TextSection copy = source.clone();
    assertEquals(source.getDescription(), copy.getDescription());
    assertNull(copy.getRequirements());
  }
}
