package gov.nist.hit.hl7.igamt.examples.dto;

import java.util.ArrayList;
import java.util.List;

public class IgExampleMessagesDTO {
    private String id;
    private String title;
    private String createdId;
    private List<ProfileExampleMessages> profileExampleMessages;

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

    public List<ProfileExampleMessages> getProfileExampleMessages() {
        if(profileExampleMessages == null) {
            this.profileExampleMessages = new ArrayList<>();
        }
        return profileExampleMessages;
    }

    public void setProfileExampleMessages(List<ProfileExampleMessages> profileExampleMessages) {
        this.profileExampleMessages = profileExampleMessages;
    }
}
