package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** One Java Mapper interface method candidate; it is not a call-graph or XML binding. */
public record MapperMethodCandidate(
    ArtifactId methodCandidateId, String signature, SourceExcerptV1 declarationExcerpt) {

  public MapperMethodCandidate {
    Objects.requireNonNull(methodCandidateId, "method candidate ID");
    if (signature == null || signature.isBlank()) {
      throw new IllegalArgumentException("Mapper method signature is required");
    }
    Objects.requireNonNull(declarationExcerpt, "method declaration excerpt");
  }
}
