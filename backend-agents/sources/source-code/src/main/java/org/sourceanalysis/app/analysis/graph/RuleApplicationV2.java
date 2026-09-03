package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** A versioned deterministic rule applied to one or more existing program elements. */
public record RuleApplicationV2(
    String ruleId, String ruleVersion, List<ArtifactId> inputProgramElementIds) {

  public RuleApplicationV2 {
    if (ruleId == null || !ruleId.matches("[a-z][a-z0-9-]{0,95}-v[1-9][0-9]*")) {
      throw new IllegalArgumentException("evidence rule ID is invalid");
    }
    if (ruleVersion == null || !ruleVersion.matches("v[1-9][0-9]*")) {
      throw new IllegalArgumentException("evidence rule version is invalid");
    }
    Objects.requireNonNull(inputProgramElementIds, "rule input program element IDs");
    inputProgramElementIds =
        inputProgramElementIds.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (inputProgramElementIds.isEmpty()
        || inputProgramElementIds.size() != inputProgramElementIds.stream().distinct().count()) {
      throw new IllegalArgumentException(
          "rule input program element IDs must be nonempty and distinct");
    }
    inputProgramElementIds = List.copyOf(inputProgramElementIds);
  }
}
