package org.sourceanalysis.research.persistence;

import java.util.List;

/** A candidate namespace/id association; it does not claim MyBatis runtime dispatch. */
public record JavaBinding(
    String methodKey,
    String mapperFqn,
    String methodName,
    List<String> statementKeys,
    List<ParameterBinding> parameterBindings,
    String limitation) {
  public JavaBinding {
    statementKeys = List.copyOf(statementKeys);
    parameterBindings = List.copyOf(parameterBindings);
  }
}
