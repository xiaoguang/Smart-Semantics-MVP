package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceEntryObservations;
import org.sourceanalysis.app.analysis.inventory.SourceObservation;
import org.sourceanalysis.app.analysis.inventory.SourceOriginAttributes;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationLimits;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationPublisher;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourceVersionCalculator;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.preparation.CapturedSourcePreparation;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.capture.preparation.SourcePreparationToolIdentity;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.SelectedSourceBasisProjector;

/** RED contract for the actual SourceAnalysisExecution admission seam. */
class SourceAnalysisExecutionSourceBasisAdmissionTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ArtifactStoreLimits LIMITS =
      new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 16);
  private static final byte[] SOURCE_BYTES =
      "package fixture;\nfinal class BasisInput {}\n".getBytes(StandardCharsets.UTF_8);

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsMismatchFromFreshlyReopenedSavedUpstreamBeforeAnyConsumerInitialization()
      throws Exception {
    PreparedSourcePublication selected = publishSource('a');
    PreparedSourcePublication reopenedUpstream = publishSource('b');
    SelectedSourceBasis expectedFromSavedSelection = reopenBasis(selected);
    AtomicInteger actualReopens = new AtomicInteger();
    AtomicInteger providerInitializations = new AtomicInteger();
    AtomicInteger activityProjectorInitializations = new AtomicInteger();
    AtomicInteger jdtInitializations = new AtomicInteger();

    assertThatThrownBy(
            () ->
                SourceAnalysisExecution.admitSelectedSourceBasis(
                    expectedFromSavedSelection,
                    () -> {
                      actualReopens.incrementAndGet();
                      return reopenBasis(reopenedUpstream);
                    },
                    () -> {
                      providerInitializations.incrementAndGet();
                      return new Object();
                    },
                    () -> {
                      activityProjectorInitializations.incrementAndGet();
                      return new Object();
                    },
                    () -> {
                      jdtInitializations.incrementAndGet();
                      return new Object();
                    }))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SOURCE_BASIS_MISMATCH");

    assertThat(actualReopens)
        .as("the actual basis is independently reopened from its saved upstream report")
        .hasValue(1);
    assertThat(providerInitializations).hasValue(0);
    assertThat(activityProjectorInitializations).hasValue(0);
    assertThat(jdtInitializations).hasValue(0);
  }

  @Test
  void exactReopenedBasisInitializesAndReturnsEachConsumerExactlyOnce() throws Exception {
    PreparedSourcePublication selected = publishSource('c');
    SelectedSourceBasis expectedFromSavedSelection = reopenBasis(selected);
    AtomicInteger actualReopens = new AtomicInteger();
    AtomicInteger providerInitializations = new AtomicInteger();
    AtomicInteger activityProjectorInitializations = new AtomicInteger();
    AtomicInteger jdtInitializations = new AtomicInteger();
    List<String> order = new ArrayList<>();
    Object provider = new Object();
    Object activityProjector = new Object();
    Object jdt = new Object();

    SourceAnalysisExecution.AdmittedExecution<Object, Object, Object> admitted =
        SourceAnalysisExecution.admitSelectedSourceBasis(
            expectedFromSavedSelection,
            () -> {
              actualReopens.incrementAndGet();
              order.add("actual-source-reopen");
              return reopenBasis(selected);
            },
            () -> {
              providerInitializations.incrementAndGet();
              order.add("model-provider");
              return provider;
            },
            () -> {
              activityProjectorInitializations.incrementAndGet();
              order.add("activity-projector");
              return activityProjector;
            },
            () -> {
              jdtInitializations.incrementAndGet();
              order.add("jdt");
              return jdt;
            });

    assertThat(admitted.modelProvider()).isSameAs(provider);
    assertThat(admitted.activityProjector()).isSameAs(activityProjector);
    assertThat(admitted.jdt()).isSameAs(jdt);
    assertThat(actualReopens).hasValue(1);
    assertThat(providerInitializations).hasValue(1);
    assertThat(activityProjectorInitializations).hasValue(1);
    assertThat(jdtInitializations).hasValue(1);
    assertThat(order)
        .containsExactly("actual-source-reopen", "model-provider", "activity-projector", "jdt");
  }

  private PreparedSourcePublication publishSource(char runIdentity) throws IOException {
    CanonicalArtifactPolicyRegistry policies =
        SourcePreparationPolicyFixture.load(
            Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
                .toAbsolutePath(),
            JSON);
    Path sourceRoot = temporaryDirectory.resolve("source-root");
    Files.createDirectories(sourceRoot);
    Path archiveRoot = temporaryDirectory.resolve("prepared-source-archive");
    Path storeRoot = temporaryDirectory.resolve("source-preparation-store");
    Files.createDirectories(storeRoot);

    SourceOriginAttributes attributes =
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null);
    Sha256Digest contentDigest = sha256(SOURCE_BYTES);
    ArtifactReference blobReference =
        new ArtifactReference(
            ArtifactId.parse("source-blob:" + contentDigest.value()), contentDigest);
    ArtifactId fileId =
        SourceVersionCalculator.fileId(
            "src/BasisInput.java", SOURCE_BYTES.length, contentDigest, attributes);
    SourceObservation observation =
        new SourceObservation(
            (long) SOURCE_BYTES.length, contentDigest, fileId, null, "fixture:src/BasisInput.java");
    SourceEntry entry =
        new SourceEntry(
            "src/BasisInput.java",
            SourceEntry.Kind.REGULAR_FILE,
            SourceEntry.Disposition.VERIFIED_TEXT,
            (long) SOURCE_BYTES.length,
            contentDigest,
            blobReference,
            fileId,
            "UTF-8",
            attributes,
            new SourceEntryObservations(observation, observation),
            List.of(),
            null,
            null);
    ArtifactReference policyReference =
        new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256());
    SourcePreparationRequest request =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            new DirectorySourceOrigin("fixture.invalid/source-basis", sourceRoot.toRealPath()),
            null,
            List.of(),
            List.of(),
            List.of(),
            new SourcePreparationLimits(20, 1_000_000L),
            policyReference);
    SourcePreparationResult result =
        new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.COMPLETED,
            true,
            List.of(entry),
            List.of(),
            List.of(),
            List.of());
    CapturedSourcePreparation capture =
        new CapturedSourcePreparation(
            request,
            result,
            ignored -> new ByteArrayInputStream(SOURCE_BYTES),
            new SourcePreparationToolIdentity(
                "verified-source-inventory/v3",
                "fixture",
                digest('d'),
                "fixture-vendor",
                "fixture-java",
                null));

    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, LIMITS);
      SavedSourcePreparation saved =
          new SourcePreparationPublisher(
                  modules, steps, new PreparedSourceArchive(archiveRoot), policies)
              .publish(runId(runIdentity), capture);
      return new PreparedSourcePublication(
          saved.reportReference(), storeRoot, archiveRoot, policies);
    }
  }

  private SelectedSourceBasis reopenBasis(PreparedSourcePublication publication) {
    try (RunStoreHandle handle = RunStoreBootstrap.open(publication.storeRoot())) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, publication.policies(), LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle, JSON, publication.policies(), LIMITS);
      SavedSourcePreparation reopened =
          new SourcePreparationReader(
                  modules, steps, new PreparedSourceArchive(publication.archiveRoot()))
              .reopen(publication.reportReference());
      return SelectedSourceBasisProjector.fromPrepared(reopened);
    }
  }

  private static AnalysisRunId runId(char identity) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(identity).repeat(64));
  }

  private static Sha256Digest sha256(byte[] bytes) {
    try {
      return Sha256Digest.parse(
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static Sha256Digest digest(char value) {
    return Sha256Digest.parse(String.valueOf(value).repeat(64));
  }

  private record PreparedSourcePublication(
      org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reportReference,
      Path storeRoot,
      Path archiveRoot,
      CanonicalArtifactPolicyRegistry policies) {}
}
