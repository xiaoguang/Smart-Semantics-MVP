package org.sourceanalysis.app.analysis.graph;

/** Stable failure returned when published inputs cannot safely seed Program Graph construction. */
final class ProgramGraphInputException extends IllegalArgumentException {

  private final String code;

  ProgramGraphInputException(String code) {
    super(code);
    this.code = code;
  }

  String code() {
    return code;
  }
}
