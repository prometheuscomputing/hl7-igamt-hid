package gov.nist.hit.hl7.igamt.examples.domain;

import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.Id;
import java.util.ArrayList;
import java.util.List;

@Document
public class IgExampleMessages {
    @Id
    private String id;
    private List<ExampleMessage> exampleMessages;
    private List<ExampleActor> actors;
    private List<ExampleScenario> scenarios;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ExampleMessage> getExampleMessages() {
        if(exampleMessages == null) {
            this.exampleMessages = new ArrayList<>();
        }
        return exampleMessages;
    }

    public void setExampleMessages(List<ExampleMessage> exampleMessages) {
        this.exampleMessages = exampleMessages;
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
