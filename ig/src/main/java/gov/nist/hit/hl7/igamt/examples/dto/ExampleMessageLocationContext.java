package gov.nist.hit.hl7.igamt.examples.dto;

public class ExampleMessageLocationContext {
    private String kind;
    private String label;
    private String detail;
    private String routeType;
    private String resourceId;
    private String resourceName;
    private String location;
    private String hl7Path;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getRouteType() {
        return routeType;
    }

    public void setRouteType(String routeType) {
        this.routeType = routeType;
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getHl7Path() {
        return hl7Path;
    }

    public void setHl7Path(String hl7Path) {
        this.hl7Path = hl7Path;
    }
}
