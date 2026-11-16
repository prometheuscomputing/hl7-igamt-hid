package gov.nist.hit.hl7.igamt.examples.domain;

/**
 * A participant reused across scenarios. The ER7 stays on the example message.
 */
public class ExampleActor {
    private String id;
    private String name;
    private String system;
    private String organization;
    private String icon;
    private ActorCapability capability = ActorCapability.BOTH;

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public ActorCapability getCapability() {
        return capability == null ? ActorCapability.BOTH : capability;
    }

    public void setCapability(ActorCapability capability) {
        this.capability = capability;
    }
}
