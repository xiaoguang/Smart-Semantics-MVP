package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** A named, immutable diagnostic record installed with a technical run result. */
public record TechnicalProblemReference(String code, ArtifactReference reference) {

  public TechnicalProblemReference {
    if (code == null || !code.matches("[A-Z][A-Z0-9_]*")) {
      throw new IllegalArgumentException("technical problem code is invalid");
    }
    Objects.requireNonNull(reference, "technical problem reference");
  }
}
