package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** One closed link from an existing program element to source bytes and its deterministic rule. */
public record EvidenceEdge(
    ArtifactId edgeId,
    EvidenceEdgeKind kind,
    ArtifactId evidenceNodeId,
    ProgramGraphKind subjectGraphKind,
    ArtifactId subjectProgramElementId,
    ArtifactId ruleApplicationNodeId) {

  public EvidenceEdge {
    Objects.requireNonNull(edgeId, "evidence edge ID");
    if (!edgeId.value().startsWith("evidence-edge:")) {
      throw new IllegalArgumentException("evidence edge ID must use the evidence-edge prefix");
    }
    Objects.requireNonNull(kind, "evidence edge kind");
    Objects.requireNonNull(evidenceNodeId, "source evidence node ID");
    Objects.requireNonNull(subjectGraphKind, "subject graph kind");
    if (subjectGraphKind == ProgramGraphKind.EVIDENCE) {
      throw new IllegalArgumentException("evidence cannot support itself");
    }
    Objects.requireNonNull(subjectProgramElementId, "subject program element ID");
    Objects.requireNonNull(ruleApplicationNodeId, "rule application node ID");
  }
}
