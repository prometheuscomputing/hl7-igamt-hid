package gov.nist.hit.hl7.igamt.examples.dto;

import java.util.ArrayList;
import java.util.List;

public class ExampleMessageLocation {
    private String igId;
    private String profileId;
    private String hl7Path;
    private String type;
    private String name;
    private String routeType;
    private String resourceId;
    private String resourceName;
    private String location;
    private boolean ambiguous;
    private String error;
    private List<ExampleMessageLocationHop> hops = new ArrayList<ExampleMessageLocationHop>();
    private List<ExampleMessageLocationContext> contexts = new ArrayList<ExampleMessageLocationContext>();

    public String getIgId() {
        return igId;
    }

    public void setIgId(String igId) {
        this.igId = igId;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getHl7Path() {
        return hl7Path;
    }

    public void setHl7Path(String hl7Path) {
        this.hl7Path = hl7Path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean isAmbiguous() {
        return ambiguous;
    }

    public void setAmbiguous(boolean ambiguous) {
        this.ambiguous = ambiguous;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<ExampleMessageLocationHop> getHops() {
        return hops;
    }

    public void setHops(List<ExampleMessageLocationHop> hops) {
        this.hops = hops;
    }

    public List<ExampleMessageLocationContext> getContexts() {
        return contexts;
    }

    public void setContexts(List<ExampleMessageLocationContext> contexts) {
        this.contexts = contexts;
    }
}
