package org.sourceanalysis.research.persistence;

import java.util.List;
import java.util.Objects;

/** A mapper method descriptor derived by the caller from a saved Java code index. */
public record MapperMethodDescriptor(
    String mapperFqn,
    String methodKey,
    String methodName,
    List<MapperParameter> parameters) {
  public MapperMethodDescriptor {
    Objects.requireNonNull(mapperFqn, "mapperFqn");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(methodName, "methodName");
    parameters = List.copyOf(parameters);
  }
}
