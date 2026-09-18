package org.sourceanalysis.research.persistence;

import java.util.List;

/** A non-executing comparison of a Java parameter name/alias and observed XML placeholders. */
public record ParameterBinding(
    String declarationName, String explicitParamAlias, List<String> xmlExpressions, String limitation) {
  public ParameterBinding {
    xmlExpressions = List.copyOf(xmlExpressions);
  }
}
