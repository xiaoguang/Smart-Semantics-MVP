package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** A source excerpt or a rule application; evidence nodes never introduce program facts. */
public record EvidenceNodeV2(
    ArtifactId evidenceNodeId,
    EvidenceNodeKind kind,
    SourceExcerptV1 sourceExcerpt,
    RuleApplicationV2 ruleApplication) {

  public EvidenceNodeV2 {
    Objects.requireNonNull(evidenceNodeId, "evidence node ID");
    if (!evidenceNodeId.value().startsWith("evidence-node:")) {
      throw new IllegalArgumentException("evidence node ID must use the evidence-node prefix");
    }
    Objects.requireNonNull(kind, "evidence node kind");
    boolean source = sourceExcerpt != null;
    boolean rule = ruleApplication != null;
    if ((kind == EvidenceNodeKind.SOURCE_EXCERPT) != (source && !rule)
        || (kind == EvidenceNodeKind.RULE_APPLICATION) != (!source && rule)) {
      throw new IllegalArgumentException("evidence node payload must match its kind");
    }
  }
}
