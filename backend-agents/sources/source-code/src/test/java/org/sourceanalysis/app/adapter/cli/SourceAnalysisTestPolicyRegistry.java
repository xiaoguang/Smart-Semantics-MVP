package org.sourceanalysis.app.adapter.cli;

import java.nio.file.Path;
import java.util.Objects;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Test bridge to the configured CLI's loader for the portable policy-set resource. */
public final class SourceAnalysisTestPolicyRegistry {

  private SourceAnalysisTestPolicyRegistry() {}

  public static CanonicalArtifactPolicyRegistry load(
      Path policySetPath, CanonicalJsonCodec canonicalJson) {
    return SourceAnalysisExecution.loadPolicies(
        Objects.requireNonNull(policySetPath, "policy set path"),
        Objects.requireNonNull(canonicalJson, "canonical JSON codec"));
  }
}
