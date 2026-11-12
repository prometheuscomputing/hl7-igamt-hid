package gov.nist.hit.hl7.igamt.common.base.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;



public class TextSection extends Section {

  private Set<TextSection> children = new HashSet<TextSection>();
  // For MESSAGESECTION type: references to example messages feature
  private String messageId;
  private String snippetId;

  /**
   * Optional ordered narrative requirements (SHALL/SHOULD/MAY).
   * null or empty = publish/edit description Froala only (legacy).
   */
  private List<NarrativeRequirement> requirements;

  public TextSection(String id, String description, Type type, int position, String label) {
    super(id, description, type, position, label);
  }



  public TextSection() {
    super();
  }

  public Set<TextSection> getChildren() {
    return children;
  }

  public void setChildren(Set<TextSection> children) {
    this.children = children;
  }

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public String getSnippetId() {
    return snippetId;
  }

  public void setSnippetId(String snippetId) {
    this.snippetId = snippetId;
  }

  public List<NarrativeRequirement> getRequirements() {
    return requirements;
  }

  public void setRequirements(List<NarrativeRequirement> requirements) {
    this.requirements = requirements;
  }

  @Override
  public TextSection clone() {
    TextSection clone = new TextSection();
    clone.setId(new ObjectId().toString());
    clone.setDescription(this.getDescription());
    clone.setType(this.getType());
    clone.setLabel(this.getLabel());
    clone.setPosition(this.getPosition());
    clone.setMessageId(this.messageId);
    clone.setSnippetId(this.snippetId);
    if (this.children != null && !this.children.isEmpty()) {
      clone.children = new HashSet<TextSection>();
      for (TextSection s : this.children) {
        clone.getChildren().add(s.clone());
      }
    }
    if (this.requirements != null && !this.requirements.isEmpty()) {
      clone.requirements = new ArrayList<NarrativeRequirement>();
      for (NarrativeRequirement r : this.requirements) {
        if (r == null) continue;
        NarrativeRequirement rc = new NarrativeRequirement();
        rc.setId(new ObjectId().toString());
        rc.setPosition(r.getPosition());
        rc.setVerb(r.getVerb());
        rc.setText(r.getText());
        clone.requirements.add(rc);
      }
    }

    return clone;


  };



}
