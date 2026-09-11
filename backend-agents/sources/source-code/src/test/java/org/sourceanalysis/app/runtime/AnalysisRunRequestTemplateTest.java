package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Defines the bootstrap-owned, path-free request template used by the CLI start operation. */
class AnalysisRunRequestTemplateTest {

  @Test
  void createsOnlyRoundOneRequestsFromTheSuppliedRegisteredSourceIdentity() throws Exception {
    Object template = openTemplate();
    ArtifactId sourceRegistrationId = artifactId("source-registration", 'a');
    Method create = template.getClass().getMethod("create", ArtifactId.class);

    AnalysisRunRequest request =
        (AnalysisRunRequest) create.invoke(template, sourceRegistrationId);

    assertThat(request.sourceRegistrationId()).isEqualTo(sourceRegistrationId);
    assertThat(request.frozenRepositoryRequestRef()).isEqualTo(reference("frozen-request", 'b'));
    assertThat(request.profileBundleRef()).isEqualTo(reference("profile", 'c'));
    assertThat(request.resourceBudgetRef()).isEqualTo(reference("budget", 'd'));
    assertThat(request.toolchainRef()).isEqualTo(reference("toolchain", 'e'));
    assertThat(request.schemaBundleRef()).isEqualTo(reference("schema", 'f'));
    assertThat(request.promptBundleRef()).isEqualTo(reference("prompt", '1'));
    assertThat(request.organizationRegistrySeedRef()).isEqualTo(reference("organization", '2'));
    assertThat(request.artifactPolicyRegistryRef()).isEqualTo(reference("policy", '3'));
    assertThat(request.candidateSeriesRef()).isEqualTo(reference("series", '4'));
    assertThat(request.readerCandidateRound()).isEqualTo(ReaderCandidateRound.ROUND_1);
    assertThat(request.parentCandidateRef()).isNull();
    assertThat(request.approvedFindingRefs()).isEmpty();
  }

  @Test
  void rejectsANonSourceRegistrationIdentifierBeforeQueueing() throws Exception {
    Object template = openTemplate();
    Method create = template.getClass().getMethod("create", ArtifactId.class);

    assertThatThrownBy(() -> create.invoke(template, artifactId("wrong-prefix", 'a')))
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage("analysis run requires a source registration ID");
  }

  private static Object openTemplate() throws Exception {
    Class<?> type;
    try {
      type = Class.forName("org.sourceanalysis.app.runtime.AnalysisRunRequestTemplate");
    } catch (ClassNotFoundException missing) {
      fail("ANALYSIS_RUN_REQUEST_TEMPLATE_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
    Constructor<?> constructor =
        type.getConstructor(
            ArtifactReference.class,
            ArtifactReference.class,
            ArtifactReference.class,
            ArtifactReference.class,
            ArtifactReference.class,
            ArtifactReference.class,
            ArtifactReference.class,
            ArtifactReference.class,
            ArtifactReference.class);
    return constructor.newInstance(
        reference("frozen-request", 'b'),
        reference("profile", 'c'),
        reference("budget", 'd'),
        reference("toolchain", 'e'),
        reference("schema", 'f'),
        reference("prompt", '1'),
        reference("organization", '2'),
        reference("policy", '3'),
        reference("series", '4'));
  }

  private static ArtifactId artifactId(String prefix, char fill) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(fill).repeat(64));
  }

  private static ArtifactReference reference(String prefix, char fill) {
    return new ArtifactReference(
        artifactId(prefix, fill), new Sha256Digest(String.valueOf(fill).repeat(64)));
  }
}
