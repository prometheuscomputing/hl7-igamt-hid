package gov.nist.hit.hl7.igamt.examples.dto;

public class ExampleMessageValidationEntry {
    private String classification;
    private String category;
    private String description;
    private String path;
    private String er7Path;
    private String igPath;
    private String positionalPath;
    private int line;
    private int column;

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getEr7Path() {
        return er7Path;
    }

    public void setEr7Path(String er7Path) {
        this.er7Path = er7Path;
    }

    public String getIgPath() {
        return igPath;
    }

    public void setIgPath(String igPath) {
        this.igPath = igPath;
    }

    public String getPositionalPath() {
        return positionalPath;
    }

    public void setPositionalPath(String positionalPath) {
        this.positionalPath = positionalPath;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }
}
