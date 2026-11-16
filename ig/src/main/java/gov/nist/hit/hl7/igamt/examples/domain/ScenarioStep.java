package gov.nist.hit.hl7.igamt.examples.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * One arrow on the sequence diagram. It names a profile, then the example messages of that profile.
 */
public class ScenarioStep {
    private String id;
    private String fromActorId;
    private String toActorId;
    private String profileId;
    private List<String> exampleMessageIds;
    private String title;
    private String description;
    private String trigger;
    private List<String> pinnedSnippetIds;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public List<String> getPinnedSnippetIds() {
        if (pinnedSnippetIds == null) {
            pinnedSnippetIds = new ArrayList<String>();
        }
        return pinnedSnippetIds;
    }

    public void setPinnedSnippetIds(List<String> pinnedSnippetIds) {
        this.pinnedSnippetIds = pinnedSnippetIds;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFromActorId() {
        return fromActorId;
    }

    public void setFromActorId(String fromActorId) {
        this.fromActorId = fromActorId;
    }

    public String getToActorId() {
        return toActorId;
    }

    public void setToActorId(String toActorId) {
        this.toActorId = toActorId;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public List<String> getExampleMessageIds() {
        if (exampleMessageIds == null) {
            exampleMessageIds = new ArrayList<String>();
        }
        return exampleMessageIds;
    }

    public void setExampleMessageIds(List<String> exampleMessageIds) {
        this.exampleMessageIds = exampleMessageIds;
    }
}
