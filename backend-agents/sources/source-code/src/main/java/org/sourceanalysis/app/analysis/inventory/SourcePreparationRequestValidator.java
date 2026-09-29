package org.sourceanalysis.app.analysis.inventory;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Cross-request checks for one immutable preparation derivation; it never opens source storage. */
public final class SourcePreparationRequestValidator {

  private SourcePreparationRequestValidator() {}

  /** Validates a refresh or exclusion against the exact immutable parent request. */
  public static void validateDerived(
      SourcePreparationRequest parent, SourcePreparationRequest derived) {
    Objects.requireNonNull(parent, "parent source-preparation request");
    Objects.requireNonNull(derived, "derived source-preparation request");
    if (derived.operation() == SourcePreparationOperation.NEW) {
      throw new IllegalArgumentException("new preparation cannot be validated as a derivation");
    }
    if (!sameOrigin(parent.origin(), derived.origin())) {
      throw new IllegalArgumentException("derived preparation must retain its exact source origin");
    }
    if (!sameTargets(parent.declaredExclusions(), derived.declaredExclusions())) {
      throw new IllegalArgumentException("derived preparation must retain declared exclusions");
    }
    if (!parent.limits().equals(derived.limits())
        || !parent.policyRef().equals(derived.policyRef())) {
      throw new IllegalArgumentException("derived preparation must retain limits and policy");
    }
    if (derived.operation() == SourcePreparationOperation.REFRESH) {
      if (!sameTargets(parent.effectiveExclusions(), derived.effectiveExclusions())) {
        throw new IllegalArgumentException("refresh cannot replace or change effective exclusions");
      }
      return;
    }

    Set<SourcePreparationTarget> expected = new LinkedHashSet<>(parent.effectiveExclusions());
    expected.addAll(derived.targets());
    if (!expected.equals(new LinkedHashSet<>(derived.effectiveExclusions()))) {
      throw new IllegalArgumentException(
          "exclude must retain parent exclusions and add only exact targets");
    }
  }

  private static boolean sameOrigin(SourceOrigin first, SourceOrigin second) {
    if (first.kind() != second.kind()
        || !first.logicalIdentity().equals(second.logicalIdentity())
        || !first.canonicalRoot().equals(second.canonicalRoot())) {
      return false;
    }
    if (first instanceof GitCommitSourceOrigin firstGit
        && second instanceof GitCommitSourceOrigin secondGit) {
      return firstGit.commitId().equals(secondGit.commitId());
    }
    return !(first instanceof GitCommitSourceOrigin) && !(second instanceof GitCommitSourceOrigin);
  }

  private static boolean sameTargets(
      java.util.List<SourcePreparationTarget> first,
      java.util.List<SourcePreparationTarget> second) {
    return new LinkedHashSet<>(first).equals(new LinkedHashSet<>(second));
  }
}
