package gov.nist.hit.hl7.igamt.examples.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered exchange between actors. Each step points at an existing example message.
 */
public class ExampleScenario {
    private String id;
    private String name;
    private String summary;
    private String leftActorId;
    private String rightActorId;
    private List<ScenarioStep> steps;

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

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLeftActorId() {
        return leftActorId;
    }

    public void setLeftActorId(String leftActorId) {
        this.leftActorId = leftActorId;
    }

    public String getRightActorId() {
        return rightActorId;
    }

    public void setRightActorId(String rightActorId) {
        this.rightActorId = rightActorId;
    }

    public List<ScenarioStep> getSteps() {
        if (steps == null) {
            steps = new ArrayList<ScenarioStep>();
        }
        return steps;
    }

    public void setSteps(List<ScenarioStep> steps) {
        this.steps = steps;
    }
}
