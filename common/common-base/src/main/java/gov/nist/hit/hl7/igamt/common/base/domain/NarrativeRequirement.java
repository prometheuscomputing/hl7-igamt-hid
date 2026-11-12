package gov.nist.hit.hl7.igamt.common.base.domain;

import java.io.Serializable;

/**
 * One structured narrative requirement with an explicit conformance verb.
 * Optional on TextSection; absent/empty keeps legacy Froala-only behavior.
 */
public class NarrativeRequirement implements Serializable {

  private static final long serialVersionUID = 1L;

  private String id;
  private int position;
  private ConformanceVerb verb;
  private String text;

  public NarrativeRequirement() {
  }

  public NarrativeRequirement(String id, int position, ConformanceVerb verb, String text) {
    this.id = id;
    this.position = position;
    this.verb = verb;
    this.text = text;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public ConformanceVerb getVerb() {
    return verb;
  }

  public void setVerb(ConformanceVerb verb) {
    this.verb = verb;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }
}
