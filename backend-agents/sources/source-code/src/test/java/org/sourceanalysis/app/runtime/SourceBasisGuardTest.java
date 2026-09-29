package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceEntryExclusion;
import org.sourceanalysis.app.analysis.inventory.SourceOriginAttributes;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationLimits;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;
import org.sourceanalysis.app.analysis.inventory.SourceVersionCalculator;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;

/** Exact pre-execution source-basis equality checks. */
class SourceBasisGuardTest {

  @Test
  void exactPreparedBasisMatchPasses() {
    SelectedSourceBasis expectedFromSavedRequest = preparedBasis('a', "docs/generated", 'c');
    SelectedSourceBasis actualFromReopenedUpstream = preparedBasis('a', "docs/generated", 'c');

    assertThat(actualFromReopenedUpstream)
        .isEqualTo(expectedFromSavedRequest)
        .isNotSameAs(expectedFromSavedRequest);

    assertThatCode(
            () ->
                SourceBasisGuard.requireMatch(expectedFromSavedRequest, actualFromReopenedUpstream))
        .doesNotThrowAnyException();
  }

  @Test
  void sameLogicalSourceWithChangedExclusionsDoesNotMatch() {
    PreparedSelection savedRequestSelection = preparedSelection('a', "docs/generated", 'c');
    PreparedSelection actualSelection = preparedSelection('a', "docs/vendor", 'd');
    SelectedSourceBasis expectedFromSavedRequest = savedRequestSelection.basis();
    SelectedSourceBasis actualFromReopenedUpstream = actualSelection.basis();

    assertThat(savedRequestSelection.request().origin().logicalIdentity())
        .isEqualTo(actualSelection.request().origin().logicalIdentity());
    assertThat(savedRequestSelection.request().origin().canonicalRoot())
        .isEqualTo(actualSelection.request().origin().canonicalRoot());
    assertThat(savedRequestSelection.request().effectiveExclusions())
        .isNotEqualTo(actualSelection.request().effectiveExclusions());
    assertThat(expectedFromSavedRequest.snapshotId())
        .isNotEqualTo(actualFromReopenedUpstream.snapshotId());
    assertThat(expectedFromSavedRequest.effectiveScopeDigest())
        .isNotEqualTo(actualFromReopenedUpstream.effectiveScopeDigest());

    assertThatThrownBy(
            () ->
                SourceBasisGuard.requireMatch(expectedFromSavedRequest, actualFromReopenedUpstream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SOURCE_BASIS_MISMATCH");
  }

  @Test
  void legacyCaptureAndPreparedSourceBranchesDoNotMatchEvenForTheSameSnapshotId() {
    SelectedSourceBasis prepared = preparedBasis('e', "docs/generated", 'f');
    SelectedSourceBasis legacy = legacyBasisForSnapshot('1', prepared.snapshotId());

    assertThat(legacy.snapshotId()).isEqualTo(prepared.snapshotId());
    assertThat(legacy.kind()).isNotEqualTo(prepared.kind());
    assertThatThrownBy(() -> SourceBasisGuard.requireMatch(prepared, legacy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SOURCE_BASIS_MISMATCH");
  }

  @Test
  void doesNotInferExpectedBasisFromMaterialWhenSavedRequestBindingIsAbsent() {
    SelectedSourceBasis actualFromReopenedUpstream = preparedBasis('2', "docs/generated", '3');

    assertThatThrownBy(() -> SourceBasisGuard.requireMatch(null, actualFromReopenedUpstream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SOURCE_BASIS_NOT_BOUND");
  }

  private static SelectedSourceBasis preparedBasis(
      char publicationFill, String exclusionPath, char scopeFill) {
    return preparedSelection(publicationFill, exclusionPath, scopeFill).basis();
  }

  private static PreparedSelection preparedSelection(
      char publicationFill, String exclusionPath, char scopeFill) {
    SourcePreparationTarget exclusion =
        new SourcePreparationTarget(exclusionPath, SourcePreparationTarget.Kind.DIRECTORY);
    SourcePreparationRequest request =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            new DirectorySourceOrigin("fixture:catalogue", Path.of("/private/source")),
            null,
            List.of(),
            List.of(exclusion),
            List.of(exclusion),
            new SourcePreparationLimits(20, 100_000L),
            artifactReference("source-policy", '4'));
    ArtifactId snapshotId =
        SourceVersionCalculator.sourceVersionId(request, excludedDirectoryResult(exclusionPath));
    PreparedSourceReference preparedSource = preparedReference(publicationFill, snapshotId);
    SelectedSourceBasis basis =
        new SelectedSourceBasis(
            SelectedSourceBasis.Kind.PREPARED_V1,
            preparedSource,
            null,
            snapshotId,
            digest(scopeFill));
    return new PreparedSelection(request, basis);
  }

  private static SourcePreparationResult excludedDirectoryResult(String path) {
    SourceEntry entry =
        new SourceEntry(
            path,
            SourceEntry.Kind.DIRECTORY,
            SourceEntry.Disposition.EXCLUDED_BY_USER,
            null,
            null,
            null,
            null,
            null,
            new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
            null,
            List.of(),
            null,
            new SourceEntryExclusion("USER_DECLARED", SourcePreparationOperation.NEW, path));
    return new SourcePreparationResult(
        SourcePreparationResult.InspectionStatus.COMPLETED,
        false,
        List.of(entry),
        List.of(),
        List.of(path),
        List.of());
  }

  private static SelectedSourceBasis legacyBasisForSnapshot(char fill, ArtifactId snapshotId) {
    SourceRegistrationReference legacyCapture =
        new SourceRegistrationReference(
            ArtifactId.parse("source-registration:" + repeated(fill)),
            snapshotId.value(),
            artifactReference("snapshot-manifest", fill),
            artifactReference("capture-receipt", fill));
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.LEGACY_CAPTURE_V1, null, legacyCapture, snapshotId, digest(fill));
  }

  private static PreparedSourceReference preparedReference(char fill, ArtifactId sourceVersionId) {
    String repeated = repeated(fill);
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + repeated);
    AnalysisStepPublicationReference publication =
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            new AnalysisStepArtifactRoot("analysis-step-root:" + repeated),
            new AnalysisStepReceiptId("analysis-step-receipt:" + repeated),
            digest(fill));
    ArtifactPolicyRegistryReference policyRegistryRef =
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse("artifact-policy-registry:" + repeated), digest(fill));
    return new PreparedSourceReference(
        sourceVersionId, publication, artifactReference("schema-bundle", fill), policyRegistryRef);
  }

  private static ArtifactReference artifactReference(String prefix, char fill) {
    String repeated = repeated(fill);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + repeated), digest(fill));
  }

  private static Sha256Digest digest(char fill) {
    return Sha256Digest.parse(repeated(fill));
  }

  private static String repeated(char fill) {
    return String.valueOf(fill).repeat(64);
  }

  private record PreparedSelection(SourcePreparationRequest request, SelectedSourceBasis basis) {}
}
