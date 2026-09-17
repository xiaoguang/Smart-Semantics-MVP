package org.sourceanalysis.research.persistence;

import java.util.Objects;

/** A parameter obtained from an already-read Java Mapper declaration. */
public record MapperParameter(String declarationName, String explicitParamAlias) {
  public MapperParameter {
    Objects.requireNonNull(declarationName, "declarationName");
  }
}
