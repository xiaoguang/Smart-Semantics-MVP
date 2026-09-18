package org.sourceanalysis.research.persistence;

import java.util.Objects;

/** A frozen Mapper XML resource supplied by the feasibility caller. */
public record MapperResource(String resourcePath, String rawXml) {
  public MapperResource {
    Objects.requireNonNull(resourcePath, "resourcePath");
    Objects.requireNonNull(rawXml, "rawXml");
  }
}
