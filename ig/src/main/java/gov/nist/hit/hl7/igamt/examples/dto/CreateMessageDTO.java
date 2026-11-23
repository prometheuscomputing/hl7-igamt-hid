package gov.nist.hit.hl7.igamt.examples.dto;

public class CreateMessageDTO {
    private String name;
    private String profileId;
    /** The ER7 text to start with. May be left out: the message is then created empty. */
    private String message;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
