package gov.nist.hit.hl7.igamt.minidump.model;

public class MiniDumpValidationResult {
  private int igCount;
  private int referencedImageCount;
  private int includedImageCount;
  private String exportedBy;

  public int getIgCount() {
    return igCount;
  }

  public void setIgCount(int igCount) {
    this.igCount = igCount;
  }

  public int getReferencedImageCount() {
    return referencedImageCount;
  }

  public void setReferencedImageCount(int referencedImageCount) {
    this.referencedImageCount = referencedImageCount;
  }

  public int getIncludedImageCount() {
    return includedImageCount;
  }

  public void setIncludedImageCount(int includedImageCount) {
    this.includedImageCount = includedImageCount;
  }

  public String getExportedBy() {
    return exportedBy;
  }

  public void setExportedBy(String exportedBy) {
    this.exportedBy = exportedBy;
  }
}
