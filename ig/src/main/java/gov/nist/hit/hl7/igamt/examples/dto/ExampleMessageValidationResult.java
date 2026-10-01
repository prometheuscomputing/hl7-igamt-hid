package gov.nist.hit.hl7.igamt.examples.dto;

import java.util.ArrayList;
import java.util.List;

public class ExampleMessageValidationResult {
    private String html;
    private String error;
    private int errors;
    private int alerts;
    private int warnings;
    private int affirmatives;
    private int informationals;
    private List<ExampleMessageValidationEntry> entries = new ArrayList<ExampleMessageValidationEntry>();

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getErrors() {
        return errors;
    }

    public void setErrors(int errors) {
        this.errors = errors;
    }

    public int getAlerts() {
        return alerts;
    }

    public void setAlerts(int alerts) {
        this.alerts = alerts;
    }

    public int getWarnings() {
        return warnings;
    }

    public void setWarnings(int warnings) {
        this.warnings = warnings;
    }

    public int getAffirmatives() {
        return affirmatives;
    }

    public void setAffirmatives(int affirmatives) {
        this.affirmatives = affirmatives;
    }

    public int getInformationals() {
        return informationals;
    }

    public void setInformationals(int informationals) {
        this.informationals = informationals;
    }

    public List<ExampleMessageValidationEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ExampleMessageValidationEntry> entries) {
        this.entries = entries;
    }
}
