package org.sourceanalysis.app.artifact;

/** The closed, ordered registry of semantic analysis steps. */
public enum AnalysisStepKey {
  VERIFIED_SOURCE_INVENTORY(
      "verified-source-inventory",
      1,
      "01-verified-source-inventory",
      "verified-source-inventory-receipt.json"),
  APPLICATION_DISCOVERY(
      "application-discovery", 2, "02-application-discovery", "application-discovery-receipt.json"),
  PROGRAM_GRAPHS("program-graphs", 3, "03-program-graphs", "program-graphs-receipt.json"),
  PROVEN_CODE_FACTS(
      "proven-code-facts", 4, "04-proven-code-facts", "proven-code-facts-receipt.json"),
  BUSINESS_FLOWS("business-flows", 5, "05-business-flows", "business-flows-receipt.json"),
  FLOW_INTERPRETATION(
      "flow-interpretation", 6, "06-flow-interpretation", "flow-interpretation-receipt.json"),
  REPOSITORY_KNOWLEDGE(
      "repository-knowledge", 7, "07-repository-knowledge", "repository-knowledge-receipt.json"),
  NINE_SECTION_DOCUMENT(
      "nine-section-document", 8, "08-nine-section-document", "nine-section-document-receipt.json");

  private final String wireValue;
  private final int order;
  private final String directoryName;
  private final String receiptFileName;

  AnalysisStepKey(String wireValue, int order, String directoryName, String receiptFileName) {
    this.wireValue = wireValue;
    this.order = order;
    this.directoryName = directoryName;
    this.receiptFileName = receiptFileName;
  }

  /** Parses one exact semantic analysis-step wire value. */
  public static AnalysisStepKey parse(String wireValue) {
    for (AnalysisStepKey candidate : values()) {
      if (candidate.wireValue.equals(wireValue)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("unknown analysis step key");
  }

  /** Returns the exact semantic analysis-step wire value. */
  public String wireValue() {
    return wireValue;
  }

  /** Returns the one-based position of this step in the closed registry. */
  public int order() {
    return order;
  }

  /** Returns the single directory segment for this step's runtime output. */
  public String directoryName() {
    return directoryName;
  }

  /** Returns the semantic receipt filename for this step. */
  public String receiptFileName() {
    return receiptFileName;
  }
}
