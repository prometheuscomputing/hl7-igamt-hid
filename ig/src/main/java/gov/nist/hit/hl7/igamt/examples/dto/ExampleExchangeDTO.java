package gov.nist.hit.hl7.igamt.examples.dto;

import gov.nist.hit.hl7.igamt.examples.domain.ExampleActor;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleScenario;

import java.util.ArrayList;
import java.util.List;

public class ExampleExchangeDTO {
    private List<ExampleActor> actors;
    private List<ExampleScenario> scenarios;

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
