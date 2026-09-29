package org.sourceanalysis.app.adapter.cli;

import java.nio.file.Path;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Loads the source-preparation policy set through the production policy-set adapter. */
public final class SourcePreparationPolicyFixture {

  private SourcePreparationPolicyFixture() {}

  public static CanonicalArtifactPolicyRegistry load(
      Path policySet, CanonicalJsonCodec canonicalJson) {
    return SourceAnalysisExecution.loadPolicies(policySet, canonicalJson);
  }
}
