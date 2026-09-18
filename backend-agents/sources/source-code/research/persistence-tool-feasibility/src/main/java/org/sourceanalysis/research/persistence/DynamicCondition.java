package org.sourceanalysis.research.persistence;

import java.util.Map;

/** A DOM-level dynamic Mapper tag; its expression is deliberately not evaluated. */
public record DynamicCondition(String tag, String expression, Map<String, String> attributes) {
  public DynamicCondition {
    attributes = Map.copyOf(attributes);
  }
}
