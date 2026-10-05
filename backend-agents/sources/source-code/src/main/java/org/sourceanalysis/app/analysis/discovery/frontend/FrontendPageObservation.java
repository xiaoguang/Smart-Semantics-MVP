package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.SourceRange;

/** One finite, source-located observation inside a page-instance context. */
public record FrontendPageObservation(
    String observationId,
    Kind kind,
    String fromUnitRef,
    String toUnitRef,
    SourceRange callRange,
    String eventName,
    List<String> actualArguments,
    List<String> formalParameters,
    List<FrontendArgumentBinding> argumentBindings,
    String detail) {

  public enum Kind {
    TEMPLATE_EVENT_BINDING,
    COMPONENT_EMIT,
    EVENT_CALLBACK_BINDING,
    DIRECT_CALL,
    PROMISE_CALLBACK,
    HTTP_WRAPPER_CALL,
    INSTANCE_METHOD_BOUNDARY
  }

  public FrontendPageObservation {
    if (observationId == null
        || observationId.isBlank()
        || kind == null
        || fromUnitRef == null
        || fromUnitRef.isBlank()
        || (toUnitRef != null && toUnitRef.isBlank())
        || (eventName != null && eventName.isBlank())
        || detail == null
        || detail.isBlank()) {
      throw new IllegalArgumentException("frontend page observation identity is invalid");
    }
    callRange = Objects.requireNonNull(callRange, "frontend page observation call range");
    actualArguments = immutableExpressions(actualArguments, "frontend page actual arguments");
    formalParameters = immutableExpressions(formalParameters, "frontend page formal parameters");
    argumentBindings = List.copyOf(argumentBindings);
  }

  private static List<String> immutableExpressions(List<String> values, String label) {
    values = List.copyOf(values);
    if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException(label + " are invalid");
    }
    return values;
  }
}
