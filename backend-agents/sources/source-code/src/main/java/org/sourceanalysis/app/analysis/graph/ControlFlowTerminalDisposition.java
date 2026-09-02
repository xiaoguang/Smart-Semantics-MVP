package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** A typed, closed outcome for a profile-stop terminal; never a fabricated successor. */
public record ControlFlowTerminalDisposition(
    ArtifactId terminalNodeId,
    ArtifactId candidateElementId,
    ControlFlowTerminalDispositionKind dispositionKind,
    ArtifactId gapId,
    String exclusionReasonCode) {

  public ControlFlowTerminalDisposition {
    Objects.requireNonNull(terminalNodeId, "profile-stop terminal node ID");
    Objects.requireNonNull(candidateElementId, "profile-stop candidate ID");
    Objects.requireNonNull(dispositionKind, "profile-stop disposition kind");
    if (dispositionKind == ControlFlowTerminalDispositionKind.GAP
        && (gapId == null || exclusionReasonCode != null)) {
      throw new IllegalArgumentException("profile-stop GAP disposition is invalid");
    }
    if (dispositionKind == ControlFlowTerminalDispositionKind.EXCLUSION
        && (gapId != null || exclusionReasonCode == null || exclusionReasonCode.isBlank())) {
      throw new IllegalArgumentException("profile-stop EXCLUSION disposition is invalid");
    }
  }
}
