package gov.nist.hit.hl7.igamt.examples.dto;

import gov.nist.hit.hl7.igamt.examples.domain.ExampleActor;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleScenario;

import java.util.ArrayList;
import java.util.List;

public class IgExampleMessagesDTO {
    private String id;
    private String title;
    private String createdId;
    private boolean viewOnly;
    private List<ProfileExampleMessages> profileExampleMessages;
    private List<ExampleActor> actors;
    private List<ExampleScenario> scenarios;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCreatedId() {
        return createdId;
    }

    public void setCreatedId(String createdId) {
        this.createdId = createdId;
    }

    public boolean isViewOnly() {
        return viewOnly;
    }

    public void setViewOnly(boolean viewOnly) {
        this.viewOnly = viewOnly;
    }

    public List<ProfileExampleMessages> getProfileExampleMessages() {
        if(profileExampleMessages == null) {
            this.profileExampleMessages = new ArrayList<>();
        }
        return profileExampleMessages;
    }

    public void setProfileExampleMessages(List<ProfileExampleMessages> profileExampleMessages) {
        this.profileExampleMessages = profileExampleMessages;
    }

    public List<ExampleActor> getActors() {
        if (actors == null) {
            actors = new ArrayList<ExampleActor>();
        }
        return actors;
    }

    public void setActors(List<ExampleActor> actors) {
        this.actors = actors;
    }

    public List<ExampleScenario> getScenarios() {
        if (scenarios == null) {
            scenarios = new ArrayList<ExampleScenario>();
        }
        return scenarios;
    }

    public void setScenarios(List<ExampleScenario> scenarios) {
        this.scenarios = scenarios;
    }
}
