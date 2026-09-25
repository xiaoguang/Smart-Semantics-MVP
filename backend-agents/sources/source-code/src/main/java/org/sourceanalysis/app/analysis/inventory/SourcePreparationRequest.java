package org.sourceanalysis.app.analysis.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Immutable private preparation intent, validated before a reader can inspect source bytes. */
public record SourcePreparationRequest(
    SourcePreparationOperation operation,
    SourceOrigin origin,
    PreparedSourceReference basePreparation,
    List<SourcePreparationTarget> targets,
    List<SourcePreparationTarget> declaredExclusions,
    List<SourcePreparationTarget> effectiveExclusions,
    SourcePreparationLimits limits,
    ArtifactReference policyRef) {

  public SourcePreparationRequest {
    Objects.requireNonNull(operation, "source-preparation operation");
    Objects.requireNonNull(origin, "source-preparation origin");
    targets = List.copyOf(targets);
    declaredExclusions = List.copyOf(declaredExclusions);
    effectiveExclusions = List.copyOf(effectiveExclusions);
    Objects.requireNonNull(limits, "source-preparation limits");
    Objects.requireNonNull(policyRef, "source-preparation policy reference");
    requireTargets(targets, "operation targets", true);
    requireTargets(declaredExclusions, "declared exclusions", false);
    requireTargets(effectiveExclusions, "effective exclusions", false);

    switch (operation) {
      case NEW -> {
        if (basePreparation != null || !targets.isEmpty()) {
          throw new IllegalArgumentException(
              "new preparation has neither a base nor operation targets");
        }
        if (!effectiveExclusions.equals(declaredExclusions)) {
          throw new IllegalArgumentException(
              "new preparation effective exclusions must equal declarations");
        }
      }
      case REFRESH -> {
        requireBaseAndTargets(basePreparation, targets, operation);
      }
      case EXCLUDE -> {
        requireBaseAndTargets(basePreparation, targets, operation);
        if (!effectiveExclusions.containsAll(targets)) {
          throw new IllegalArgumentException(
              "exclude preparation must add its exact targets to scope");
        }
      }
    }
  }

  private static void requireBaseAndTargets(
      PreparedSourceReference basePreparation,
      List<SourcePreparationTarget> targets,
      SourcePreparationOperation operation) {
    if (basePreparation == null || targets.isEmpty()) {
      throw new IllegalArgumentException(
          operation + " preparation requires a base and exact targets");
    }
  }

  private static void requireTargets(
      List<SourcePreparationTarget> targets, String label, boolean rejectAncestorOverlap) {
    if (targets.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(label + " must not contain null targets");
    }
    Set<String> paths = new HashSet<>();
    if (targets.stream().anyMatch(target -> !paths.add(target.relativePath()))) {
      throw new IllegalArgumentException(label + " must not contain duplicate targets");
    }
    if (!rejectAncestorOverlap) {
      return;
    }
    List<SourcePreparationTarget> ordered = new ArrayList<>(targets);
    ordered.sort(Comparator.comparing(SourcePreparationTarget::relativePath));
    for (int index = 0; index < ordered.size(); index++) {
      String path = ordered.get(index).relativePath();
      for (int later = index + 1; later < ordered.size(); later++) {
        if (ordered.get(later).relativePath().startsWith(path + "/")) {
          throw new IllegalArgumentException(label + " must not overlap by ancestor path");
        }
      }
    }
  }
}
