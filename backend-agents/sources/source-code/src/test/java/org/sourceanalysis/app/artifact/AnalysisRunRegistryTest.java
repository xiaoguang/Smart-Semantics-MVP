package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.ReaderCandidateRound;

/** Proves one queued run persists through a real store close and reopen. */
class AnalysisRunRegistryTest {

  @TempDir Path temporaryDirectory;

  @Test
  void queuesAContentAddressedRequestAndReopensTheSameQueuedRun() {
    AnalysisRunRequest request = request();
    AnalysisRunReference first;
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      first = RunStoreBootstrap.openAnalysisRunRegistry(handle).queue(request);
    }

    assertThat(first.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

    try (RunStoreHandle handle = RunStoreBootstrap.open(temporaryDirectory)) {
      AnalysisRunReference reopened =
          RunStoreBootstrap.openAnalysisRunRegistry(handle).reopen(first.runId());

      assertThat(reopened.runId()).isEqualTo(first.runId());
      assertThat(reopened.analysisRunRequestReference())
          .isEqualTo(first.analysisRunRequestReference());
      assertThat(reopened.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);
    }
  }

  @Test
  void freshReopensTheExactPersistedRequestValueAndCanonicalBytesForRuntimeComposition()
      throws Exception {
    AnalysisRunRequest request = request();
    AnalysisRunReference queued;
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      queued = RunStoreBootstrap.queueAnalysisRun(handle, request);
    }

    Class<?> persisted = typeOrNull("org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest");
    assertThat(persisted)
        .as(
            "runtime composition needs the persisted request bytes rather than a re-created request")
        .isNotNull();

    try (RunStoreHandle handle = RunStoreBootstrap.open(temporaryDirectory)) {
      Object reopened =
          RunStoreBootstrap.class
              .getMethod(
                  "reopenPersistedAnalysisRunRequest", RunStoreHandle.class, AnalysisRunId.class)
              .invoke(null, handle, queued.runId());
      AnalysisRunReference run =
          (AnalysisRunReference) reopened.getClass().getMethod("analysisRun").invoke(reopened);
      AnalysisRunRequest value =
          (AnalysisRunRequest) reopened.getClass().getMethod("request").invoke(reopened);
      ImmutableBytes bytes =
          (ImmutableBytes) reopened.getClass().getMethod("canonicalJson").invoke(reopened);

      assertThat(run).isEqualTo(queued);
      assertThat(value).isEqualTo(request);
      assertThat(new Sha256Digest(digest(bytes.copyToByteArray())))
          .isEqualTo(queued.analysisRunRequestReference().sha256());
    }
  }

  private static AnalysisRunRequest request() {
    return new AnalysisRunRequest(
        artifactId("source-registration", 'a'),
        reference("frozen-repository-request", 'b'),
        reference("profile-bundle", 'c'),
        reference("resource-budget", 'd'),
        reference("toolchain", 'e'),
        reference("schema-bundle", 'f'),
        reference("prompt-bundle", '1'),
        null,
        reference("artifact-policy-registry", '2'),
        reference("candidate-series", '3'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static ArtifactId artifactId(String prefix, char fill) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(fill).repeat(64));
  }

  private static ArtifactReference reference(String prefix, char fill) {
    return new ArtifactReference(
        artifactId(prefix, fill), new Sha256Digest(String.valueOf(fill).repeat(64)));
  }

  private static String digest(byte[] value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private static Class<?> typeOrNull(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }
}
