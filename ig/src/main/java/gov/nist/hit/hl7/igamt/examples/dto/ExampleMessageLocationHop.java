package gov.nist.hit.hl7.igamt.examples.dto;

public class ExampleMessageLocationHop {
    private String type;
    private String hl7Path;
    private String name;
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String pathId;
    private String positionalPath;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHl7Path() {
        return hl7Path;
    }

    public void setHl7Path(String hl7Path) {
        this.hl7Path = hl7Path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId;
    }

    public String getPositionalPath() {
        return positionalPath;
    }

    public void setPositionalPath(String positionalPath) {
        this.positionalPath = positionalPath;
    }
}
