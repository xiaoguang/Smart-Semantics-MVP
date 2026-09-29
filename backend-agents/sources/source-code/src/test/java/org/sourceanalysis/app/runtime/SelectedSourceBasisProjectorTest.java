package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.InventoryScope;
import org.sourceanalysis.app.analysis.inventory.PreparedSourcePublicationFacts;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceEntryExclusion;
import org.sourceanalysis.app.analysis.inventory.SourceOriginAttributes;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationAssessment;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationLimits;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadinessEvaluator;
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
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;

class SelectedSourceBasisProjectorTest {

  @Test
  void preparedScopeIsStableAcrossExclusionOrderButNotScopeChanges() {
    SourcePreparationTarget generated =
        target("docs/generated", SourcePreparationTarget.Kind.DIRECTORY);
    SourcePreparationTarget notes = target("notes.txt", SourcePreparationTarget.Kind.FILE);

    SelectedSourceBasis first =
        SelectedSourceBasisProjector.fromPrepared(
            saved(List.of(generated, notes), SourcePreparationReadiness.READY_WITH_EXCLUSIONS));
    SelectedSourceBasis reordered =
        SelectedSourceBasisProjector.fromPrepared(
            saved(List.of(notes, generated), SourcePreparationReadiness.READY_WITH_EXCLUSIONS));
    SelectedSourceBasis different =
        SelectedSourceBasisProjector.fromPrepared(
            saved(List.of(generated), SourcePreparationReadiness.READY_WITH_EXCLUSIONS));

    assertThat(first).isEqualTo(reordered);
    assertThat(first.effectiveScopeDigest()).isNotEqualTo(different.effectiveScopeDigest());
    assertThat(first.kind()).isEqualTo(SelectedSourceBasis.Kind.PREPARED_V1);
    assertThat(first.preparedSource())
        .isEqualTo(
            saved(List.of(generated, notes), SourcePreparationReadiness.READY_WITH_EXCLUSIONS)
                .sourceVersionReference());
  }

  @Test
  void preparedSourceCannotBeSelectedWhileItNeedsADecision() {
    assertThatThrownBy(
            () ->
                SelectedSourceBasisProjector.fromPrepared(
                    saved(List.of(), SourcePreparationReadiness.NEEDS_DECISION)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SOURCE_PREPARATION_NOT_READY");
  }

  @Test
  void legacyBasisPreservesFrozenScopeInsteadOfAssumingCompleteCapture() {
    SourceRegistrationReference legacy =
        new SourceRegistrationReference(
            ArtifactId.parse("source-registration:" + hex('a')),
            "snapshot:" + hex('b'),
            reference("snapshot-manifest", 'c'),
            reference("capture-receipt", 'd'));

    SelectedSourceBasis complete =
        SelectedSourceBasisProjector.fromLegacy(legacy, InventoryScope.completeCapture());
    SelectedSourceBasis bounded =
        SelectedSourceBasisProjector.fromLegacy(legacy, InventoryScope.boundedPathSet("src/main"));

    assertThat(complete.legacyCapture()).isEqualTo(legacy);
    assertThat(complete.snapshotId()).isEqualTo(bounded.snapshotId());
    assertThat(complete.effectiveScopeDigest()).isNotEqualTo(bounded.effectiveScopeDigest());
  }

  private static SavedSourcePreparation saved(
      List<SourcePreparationTarget> exclusions, SourcePreparationReadiness readiness) {
    AnalysisStepPublicationReference publication =
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(
                AnalysisRunId.parse("analysis-run:" + hex('1')),
                AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            new AnalysisStepArtifactRoot("analysis-step-root:" + hex('2')),
            new AnalysisStepReceiptId("analysis-step-receipt:" + hex('3')),
            digest('4'));
    ArtifactPolicyRegistryReference policy =
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse("artifact-policy-registry:" + hex('5')), digest('5'));
    SourcePreparationRequest request =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            new DirectorySourceOrigin("fixture:project", Path.of("/private/fixture")),
            null,
            List.of(),
            exclusions,
            exclusions,
            new SourcePreparationLimits(10, 1_000L),
            reference("source-policy", '8'));
    SourceEntry verified =
        new SourceEntry(
            "src/App.java",
            SourceEntry.Kind.REGULAR_FILE,
            SourceEntry.Disposition.VERIFIED_TEXT,
            1L,
            digest('6'),
            reference("source-blob", '6'),
            ArtifactId.parse("source-file:" + hex('6')),
            "UTF-8",
            new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
            null,
            List.of(),
            null,
            null);
    List<SourceEntry> entries = new java.util.ArrayList<>();
    entries.add(verified);
    for (SourcePreparationTarget exclusion : exclusions) {
      entries.add(
          new SourceEntry(
              exclusion.relativePath(),
              exclusion.kind() == SourcePreparationTarget.Kind.DIRECTORY
                  ? SourceEntry.Kind.DIRECTORY
                  : SourceEntry.Kind.REGULAR_FILE,
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
              new SourceEntryExclusion(
                  "USER_DECLARED", SourcePreparationOperation.NEW, exclusion.relativePath())));
    }
    if (readiness == SourcePreparationReadiness.NEEDS_DECISION) {
      entries.add(
          new SourceEntry(
              "src/Unreadable.java",
              SourceEntry.Kind.REGULAR_FILE,
              SourceEntry.Disposition.UNAVAILABLE,
              null,
              null,
              null,
              null,
              null,
              new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
              null,
              List.of(),
              null,
              null));
    }
    SourcePreparationResult result =
        new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.COMPLETED,
            true,
            entries,
            List.of(),
            List.of(),
            List.of());
    SourcePreparationAssessment assessment = SourcePreparationReadinessEvaluator.assess(result);
    assertThat(assessment.readiness()).isEqualTo(readiness);
    PreparedSourceReference prepared =
        new PreparedSourceReference(
            SourceVersionCalculator.sourceVersionId(request, result),
            publication,
            reference("schema-bundle", '7'),
            policy);
    PreparedSourcePublicationFacts facts =
        new PreparedSourcePublicationFacts(
            reference("capability-profile", '9'),
            reference("source-inventory", 'a'),
            reference("verified-snapshot", 'b'),
            new ArtifactControls(digest('c'), digest('d'), digest('e'), null, policy));
    return new SavedSourcePreparation(
        publication, prepared, request, result, assessment, null, facts);
  }

  private static SourcePreparationTarget target(String path, SourcePreparationTarget.Kind kind) {
    return new SourcePreparationTarget(path, kind);
  }

  private static ArtifactReference reference(String prefix, char fill) {
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + hex(fill)), digest(fill));
  }

  private static Sha256Digest digest(char fill) {
    return Sha256Digest.parse(hex(fill));
  }

  private static String hex(char fill) {
    return String.valueOf(fill).repeat(64);
  }
}
