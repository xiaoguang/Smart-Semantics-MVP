package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** One static MyBatis XML statement candidate; it is not yet bound to a Java call. */
public record MapperStatementCandidate(
    ArtifactId statementCandidateId,
    String statementId,
    String statementKind,
    SourceExcerptV1 declarationExcerpt) {

  public MapperStatementCandidate {
    Objects.requireNonNull(statementCandidateId, "statement candidate ID");
    if (statementId == null
        || statementId.isBlank()
        || statementKind == null
        || statementKind.isBlank()) {
      throw new IllegalArgumentException("Mapper XML statement identity is required");
    }
    Objects.requireNonNull(declarationExcerpt, "statement declaration excerpt");
  }
}
