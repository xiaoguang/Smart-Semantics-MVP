package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/** Direct contracts for request validation, source identity, and selected source basis. */
class SourcePreparationRequestVersionBasisTest {

  @Test
  void newRequiresNoBaseAndNoRefreshTargetsButMayCarryItsDeclaredExclusions() {
    SourcePreparationRequest request =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            directoryOrigin("catalogue", "/private/source-a"),
            null,
            List.of(),
            List.of(target("docs/legacy", SourcePreparationTarget.Kind.DIRECTORY)),
            List.of(target("docs/legacy", SourcePreparationTarget.Kind.DIRECTORY)),
            limits(),
            policyRef('a'));

    assertThat(request.basePreparation()).isNull();
    assertThat(request.targets()).isEmpty();
    assertThat(request.effectiveExclusions())
        .containsExactlyElementsOf(request.declaredExclusions());
  }

  @Test
  void refreshAndExcludeRequireACompleteBaseAndNonEmptyExactTargets() {
    PreparedSourceReference base = preparedReference('b');

    assertThatThrownBy(
            () ->
                request(SourcePreparationOperation.REFRESH, base, List.of(), List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                request(
                    SourcePreparationOperation.EXCLUDE,
                    null,
                    List.of(target("docs/legacy", SourcePreparationTarget.Kind.DIRECTORY)),
                    List.of(),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);

    request(
        SourcePreparationOperation.REFRESH,
        base,
        List.of(target("src/Catalogue.java", SourcePreparationTarget.Kind.FILE)),
        List.of(),
        List.of());
    request(
        SourcePreparationOperation.EXCLUDE,
        base,
        List.of(target("docs/legacy", SourcePreparationTarget.Kind.DIRECTORY)),
        List.of(),
        List.of(target("docs/legacy", SourcePreparationTarget.Kind.DIRECTORY)));
  }

  @Test
  void rejectsRootTraversalAliasGlobDuplicateAndAncestorOverlapBeforeReading() {
    for (String invalidPath :
        List.of(
            "",
            ".",
            "/absolute",
            "../outside",
            "src/../outside",
            "src\\Catalogue.java",
            "src//Catalogue.java",
            "src/*.java",
            "src/[AB].java",
            "src/[0-9].java",
            "src/[]A].java",
            "src/[]].java",
            "nul\u0000name")) {
      assertThatThrownBy(
              () ->
                  request(
                      SourcePreparationOperation.REFRESH,
                      preparedReference('c'),
                      List.of(target(invalidPath, SourcePreparationTarget.Kind.FILE)),
                      List.of(),
                      List.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    assertThatThrownBy(
            () ->
                request(
                    SourcePreparationOperation.REFRESH,
                    preparedReference('d'),
                    List.of(
                        target("src/Catalogue.java", SourcePreparationTarget.Kind.FILE),
                        target("src/Catalogue.java", SourcePreparationTarget.Kind.FILE)),
                    List.of(),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                request(
                    SourcePreparationOperation.REFRESH,
                    preparedReference('e'),
                    List.of(
                        target("src", SourcePreparationTarget.Kind.DIRECTORY),
                        target("src/Catalogue.java", SourcePreparationTarget.Kind.FILE)),
                    List.of(),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatCode(
            () ->
                request(
                    SourcePreparationOperation.REFRESH,
                    preparedReference('f'),
                    List.of(
                        target("a..b", SourcePreparationTarget.Kind.FILE),
                        target("literal[]", SourcePreparationTarget.Kind.FILE)),
                    List.of(),
                    List.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void carriesParentExclusionsAndRejectsDeclarationPolicyOrLimitReplacement() {
    SourcePreparationTarget prior = target("docs/legacy", SourcePreparationTarget.Kind.DIRECTORY);
    SourcePreparationTarget added =
        target("vendor/ignored", SourcePreparationTarget.Kind.DIRECTORY);
    SourcePreparationRequest parent =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            directoryOrigin("catalogue", "/private/source-a"),
            null,
            List.of(),
            List.of(prior),
            List.of(prior),
            limits(),
            policyRef('f'));

    SourcePreparationRequest validExclude =
        new SourcePreparationRequest(
            SourcePreparationOperation.EXCLUDE,
            directoryOrigin("catalogue", "/private/source-a"),
            preparedReference('f'),
            List.of(added),
            List.of(prior),
            List.of(prior, added),
            limits(),
            policyRef('f'));
    SourcePreparationRequestValidator.validateDerived(parent, validExclude);

    SourcePreparationRequest droppedParentExclusion =
        new SourcePreparationRequest(
            SourcePreparationOperation.REFRESH,
            directoryOrigin("catalogue", "/private/source-a"),
            preparedReference('f'),
            List.of(target("src/Catalogue.java", SourcePreparationTarget.Kind.FILE)),
            List.of(),
            List.of(),
            limits(),
            policyRef('f'));
    assertThatThrownBy(
            () -> SourcePreparationRequestValidator.validateDerived(parent, droppedParentExclusion))
        .isInstanceOf(IllegalArgumentException.class);

    SourcePreparationTarget child = target("docs/legacy/old", SourcePreparationTarget.Kind.FILE);
    SourcePreparationTarget parentDirectory =
        target("docs", SourcePreparationTarget.Kind.DIRECTORY);
    SourcePreparationRequest parentWithChildExclusion =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            directoryOrigin("catalogue", "/private/missing-source-root"),
            null,
            List.of(),
            List.of(child),
            List.of(child),
            limits(),
            policyRef('9'));
    SourcePreparationRequest derivedParentExclusion =
        new SourcePreparationRequest(
            SourcePreparationOperation.EXCLUDE,
            directoryOrigin("catalogue", "/private/missing-source-root"),
            preparedReference('9'),
            List.of(parentDirectory),
            List.of(child),
            List.of(child, parentDirectory),
            limits(),
            policyRef('9'));
    assertThatCode(
            () ->
                SourcePreparationRequestValidator.validateDerived(
                    parentWithChildExclusion, derivedParentExclusion))
        .doesNotThrowAnyException();

    SourcePreparationRequest changedPolicy =
        new SourcePreparationRequest(
            SourcePreparationOperation.REFRESH,
            directoryOrigin("catalogue", "/private/missing-source-root"),
            preparedReference('9'),
            List.of(target("src/Catalogue.java", SourcePreparationTarget.Kind.FILE)),
            List.of(child),
            List.of(child),
            limits(),
            policyRef('e'));
    assertThatThrownBy(
            () ->
                SourcePreparationRequestValidator.validateDerived(
                    parentWithChildExclusion, changedPolicy))
        .isInstanceOf(IllegalArgumentException.class);

    SourcePreparationRequest changedLimits =
        new SourcePreparationRequest(
            SourcePreparationOperation.REFRESH,
            directoryOrigin("catalogue", "/private/missing-source-root"),
            preparedReference('9'),
            List.of(target("src/Catalogue.java", SourcePreparationTarget.Kind.FILE)),
            List.of(child),
            List.of(child),
            new SourcePreparationLimits(101, 1_000_000L),
            policyRef('9'));
    assertThatThrownBy(
            () ->
                SourcePreparationRequestValidator.validateDerived(
                    parentWithChildExclusion, changedLimits))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sourceOriginsRequireAbsoluteCanonicalRootsButDoNotTouchTheFileSystem() {
    Path missingAbsoluteRoot =
        Path.of("/private/definitely-missing-source-root").toAbsolutePath().normalize();
    SourceOrigin directory = new DirectorySourceOrigin("catalogue", missingAbsoluteRoot);
    SourceOrigin git =
        new GitCommitSourceOrigin(
            "catalogue", missingAbsoluteRoot, "0123456789abcdef0123456789abcdef01234567");

    assertThat(directory.canonicalRoot()).isEqualTo(missingAbsoluteRoot);
    assertThat(git.canonicalRoot()).isEqualTo(missingAbsoluteRoot);
    assertThatThrownBy(
            () -> new DirectorySourceOrigin("catalogue", Path.of("relative-source-root")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new GitCommitSourceOrigin(
                    "catalogue",
                    Path.of("relative-source-root"),
                    "0123456789abcdef0123456789abcdef01234567"))
        .isInstanceOf(IllegalArgumentException.class);

    SourcePreparationRequest parent =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            directory,
            null,
            List.of(),
            List.of(),
            List.of(),
            limits(),
            policyRef('e'));
    SourcePreparationRequest excludeWhileRootIsUnavailable =
        new SourcePreparationRequest(
            SourcePreparationOperation.EXCLUDE,
            directory,
            preparedReference('e'),
            List.of(target("docs/legacy", SourcePreparationTarget.Kind.DIRECTORY)),
            List.of(),
            List.of(target("docs/legacy", SourcePreparationTarget.Kind.DIRECTORY)),
            limits(),
            policyRef('e'));
    assertThatCode(
            () ->
                SourcePreparationRequestValidator.validateDerived(
                    parent, excludeWhileRootIsUnavailable))
        .doesNotThrowAnyException();
  }

  @Test
  void directoryOriginNeedsNoGitCommitWhileGitOriginPreservesExactCommit() {
    SourceOrigin directory = directoryOrigin("catalogue", "/private/source-a");
    SourceOrigin git =
        new GitCommitSourceOrigin(
            "catalogue", Path.of("/private/source-a"), "0123456789abcdef0123456789abcdef01234567");

    assertThat(directory.kind()).isEqualTo(SourceOrigin.Kind.DIRECTORY);
    assertThat(directory.logicalIdentity()).isEqualTo("catalogue");
    assertThat(git.kind()).isEqualTo(SourceOrigin.Kind.GIT_COMMIT);
    assertThat(((GitCommitSourceOrigin) git).commitId())
        .isEqualTo("0123456789abcdef0123456789abcdef01234567");
    assertThatThrownBy(
            () ->
                new GitCommitSourceOrigin(
                    "catalogue", Path.of("/private/source-a"), "0123456789abcdef"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preparedAndLegacySelectedBasisRequireOneCompleteBranch() {
    PreparedSourceReference prepared = preparedReference('1');
    SourceRegistrationReference legacy = legacyReference('2');
    ArtifactId preparedSnapshot = prepared.sourceVersionId();
    ArtifactId legacySnapshot = ArtifactId.parse(legacy.snapshotId());
    Sha256Digest scope = Sha256Digest.parse("4".repeat(64));

    SelectedSourceBasis preparedBasis =
        new SelectedSourceBasis(
            SelectedSourceBasis.Kind.PREPARED_V1, prepared, null, preparedSnapshot, scope);
    SelectedSourceBasis legacyBasis =
        new SelectedSourceBasis(
            SelectedSourceBasis.Kind.LEGACY_CAPTURE_V1, null, legacy, legacySnapshot, scope);

    assertThat(preparedBasis.preparedSource()).isEqualTo(prepared);
    assertThat(preparedBasis.snapshotId()).isEqualTo(prepared.sourceVersionId());
    assertThat(preparedBasis.legacyCapture()).isNull();
    assertThat(legacyBasis.legacyCapture()).isEqualTo(legacy);
    assertThat(legacyBasis.snapshotId()).isEqualTo(legacySnapshot);
    assertThat(legacySnapshot.value()).isEqualTo(legacy.snapshotId());
    assertThat(legacyBasis.preparedSource()).isNull();
    assertThatThrownBy(
            () ->
                new SelectedSourceBasis(
                    SelectedSourceBasis.Kind.PREPARED_V1,
                    prepared,
                    legacy,
                    preparedSnapshot,
                    scope))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new SelectedSourceBasis(
                    SelectedSourceBasis.Kind.LEGACY_CAPTURE_V1, null, null, legacySnapshot, scope))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sourceFileV2UsesOnlyStableContentIdentityAndSourceBasisUsesParentScopeAndPolicy() {
    SourceEntry entryA = Fixture.textEntry("src/Catalogue.java", "fixture-bytes");
    SourceEntry entryWithOtherDiagnostics =
        Fixture.withDiagnostics(entryA, "later-mtime", "different-file-key");

    ArtifactId expectedFileId =
        Fixture.expectedSourceFileId(
            entryA.relativePath(), entryA.sizeBytes(), entryA.sha256(), entryA.originAttributes());
    assertThat(
            SourceVersionCalculator.fileId(
                entryA.relativePath(),
                entryA.sizeBytes(),
                entryA.sha256(),
                entryA.originAttributes()))
        .isEqualTo(expectedFileId);
    assertThat(
            SourceVersionCalculator.fileId(
                entryWithOtherDiagnostics.relativePath(),
                entryWithOtherDiagnostics.sizeBytes(),
                entryWithOtherDiagnostics.sha256(),
                entryWithOtherDiagnostics.originAttributes()))
        .isEqualTo(expectedFileId);
    assertThat(
            SourceVersionCalculator.fileId(
                "src/Other.java", entryA.sizeBytes(), entryA.sha256(), entryA.originAttributes()))
        .isNotEqualTo(expectedFileId);
    assertThat(
            SourceVersionCalculator.fileId(
                entryA.relativePath(),
                entryA.sizeBytes() + 1,
                entryA.sha256(),
                entryA.originAttributes()))
        .isNotEqualTo(expectedFileId);
    assertThat(
            SourceVersionCalculator.fileId(
                entryA.relativePath(),
                entryA.sizeBytes(),
                Sha256Digest.parse("f".repeat(64)),
                entryA.originAttributes()))
        .isNotEqualTo(expectedFileId);
    SourceOriginAttributes executableDirectoryFile =
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, true);
    assertThat(
            SourceVersionCalculator.fileId(
                entryA.relativePath(),
                entryA.sizeBytes(),
                entryA.sha256(),
                executableDirectoryFile))
        .isNotEqualTo(expectedFileId);

    SourcePreparationRequest requestA =
        versionRequest(
            directoryOrigin("catalogue", "/private/source-a"),
            preparedReference('5'),
            SourcePreparationOperation.REFRESH,
            policyRef('6'));
    SourcePreparationRequest sameBytesDifferentDiagnostics =
        versionRequest(
            directoryOrigin("catalogue", "/private/source-b"),
            preparedReference('5'),
            SourcePreparationOperation.REFRESH,
            policyRef('6'));
    SourcePreparationResult resultA = Fixture.result(List.of(entryA));
    SourcePreparationResult resultWithOtherDiagnostics =
        Fixture.result(List.of(entryWithOtherDiagnostics));

    assertThat(SourceVersionCalculator.sourceVersionId(requestA, resultA))
        .isEqualTo(
            SourceVersionCalculator.sourceVersionId(
                sameBytesDifferentDiagnostics, resultWithOtherDiagnostics));
    assertThat(SourceVersionCalculator.sourceVersionId(requestA, resultA).value())
        .matches("snapshot:[0-9a-f]{64}");

    SourcePreparationRequest differentParent =
        versionRequest(
            directoryOrigin("catalogue", "/private/source-a"),
            preparedReference('9'),
            SourcePreparationOperation.REFRESH,
            policyRef('6'));
    assertThat(SourceVersionCalculator.sourceVersionId(requestA, resultA))
        .isNotEqualTo(SourceVersionCalculator.sourceVersionId(differentParent, resultA));
  }

  @Test
  void everyDerivedOperationGetsANewVersionEvenWhenBytesAndScopeAreUnchanged() {
    SourceEntry entry = Fixture.textEntry("src/Catalogue.java", "fixture-bytes");
    SourcePreparationRequest refresh =
        versionRequest(
            directoryOrigin("catalogue", "/private/source-a"),
            preparedReference('7'),
            SourcePreparationOperation.REFRESH,
            policyRef('8'));
    SourcePreparationRequest excludeSameBytes =
        versionRequest(
            directoryOrigin("catalogue", "/private/source-a"),
            preparedReference('7'),
            SourcePreparationOperation.EXCLUDE,
            policyRef('8'));
    SourcePreparationResult result = Fixture.result(List.of(entry));

    assertThat(SourceVersionCalculator.sourceVersionId(excludeSameBytes, result))
        .isNotEqualTo(SourceVersionCalculator.sourceVersionId(refresh, result));
  }

  private static SourcePreparationRequest versionRequest(
      SourceOrigin origin,
      PreparedSourceReference base,
      SourcePreparationOperation operation,
      ArtifactReference policyRef) {
    List<SourcePreparationTarget> effectiveExclusions =
        operation == SourcePreparationOperation.EXCLUDE
            ? List.of(target("src/Catalogue.java", SourcePreparationTarget.Kind.FILE))
            : List.of();
    return new SourcePreparationRequest(
        operation,
        origin,
        base,
        List.of(target("src/Catalogue.java", SourcePreparationTarget.Kind.FILE)),
        List.of(),
        effectiveExclusions,
        limits(),
        policyRef);
  }

  private static SourcePreparationRequest request(
      SourcePreparationOperation operation,
      PreparedSourceReference base,
      List<SourcePreparationTarget> targets,
      List<SourcePreparationTarget> declaredExclusions,
      List<SourcePreparationTarget> effectiveExclusions) {
    return new SourcePreparationRequest(
        operation,
        directoryOrigin("catalogue", "/private/source-a"),
        base,
        targets,
        declaredExclusions,
        effectiveExclusions,
        limits(),
        policyRef('a'));
  }

  private static SourcePreparationTarget target(String path, SourcePreparationTarget.Kind kind) {
    return new SourcePreparationTarget(path, kind);
  }

  private static SourcePreparationLimits limits() {
    return new SourcePreparationLimits(100, 1_000_000L);
  }

  private static DirectorySourceOrigin directoryOrigin(String logicalIdentity, String root) {
    return new DirectorySourceOrigin(logicalIdentity, Path.of(root));
  }

  private static ArtifactReference policyRef(char fill) {
    return reference("policy-bundle", fill);
  }

  private static ArtifactReference reference(String prefix, char fill) {
    String digest = String.valueOf(fill).repeat(64);
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest), Sha256Digest.parse(digest));
  }

  private static PreparedSourceReference preparedReference(char fill) {
    String digest = String.valueOf(fill).repeat(64);
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest);
    AnalysisStepPublicationReference publication =
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            new AnalysisStepArtifactRoot("analysis-step-root:" + digest),
            new AnalysisStepReceiptId("analysis-step-receipt:" + digest),
            Sha256Digest.parse(digest));
    return new PreparedSourceReference(
        ArtifactId.parse("snapshot:" + digest),
        publication,
        reference("schema-bundle", fill),
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse("artifact-policy-registry:" + digest), Sha256Digest.parse(digest)));
  }

  private static SourceRegistrationReference legacyReference(char fill) {
    String digest = String.valueOf(fill).repeat(64);
    return new SourceRegistrationReference(
        ArtifactId.parse("source-registration:" + digest),
        "snapshot:" + digest,
        reference("snapshot-manifest", fill),
        reference("capture-receipt", fill));
  }

  private static final class Fixture {

    private static SourceEntry textEntry(String path, String label) {
      return regularEntry(
          path,
          ("class Fixture { String value = \"" + label + "\"; }\n")
              .getBytes(StandardCharsets.UTF_8),
          "UTF-8");
    }

    private static SourceEntry regularEntry(String path, byte[] bytes, String textEncoding) {
      String digest = sha256Hex(bytes);
      Sha256Digest sha256 = Sha256Digest.parse(digest);
      SourceOriginAttributes originAttributes =
          new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null);
      ArtifactId fileId =
          SourceVersionCalculator.fileId(path, bytes.length, sha256, originAttributes);
      SourceObservation observation =
          new SourceObservation((long) bytes.length, sha256, fileId, null, path);
      return new SourceEntry(
          path,
          SourceEntry.Kind.REGULAR_FILE,
          SourceEntry.Disposition.VERIFIED_TEXT,
          (long) bytes.length,
          sha256,
          new ArtifactReference(ArtifactId.parse("source-blob:" + digest), sha256),
          fileId,
          textEncoding,
          originAttributes,
          new SourceEntryObservations(observation, observation),
          List.of(),
          null,
          null);
    }

    private static SourceEntry withDiagnostics(
        SourceEntry entry, String modifiedAt, String fileKey) {
      SourceObservation observation =
          new SourceObservation(
              entry.sizeBytes(),
              entry.sha256(),
              entry.fileId(),
              Instant.parse("2025-01-01T00:00:00Z").plusSeconds(modifiedAt.length()),
              fileKey);
      return new SourceEntry(
          entry.relativePath(),
          entry.entryKind(),
          entry.disposition(),
          entry.sizeBytes(),
          entry.sha256(),
          entry.blobRef(),
          entry.fileId(),
          entry.textEncoding(),
          entry.originAttributes(),
          new SourceEntryObservations(observation, observation),
          entry.issueIds(),
          entry.inheritedFrom(),
          entry.exclusion());
    }

    private static SourcePreparationResult result(List<SourceEntry> entries) {
      return new SourcePreparationResult(
          SourcePreparationResult.InspectionStatus.COMPLETED,
          true,
          entries,
          List.of(),
          List.of(),
          List.of());
    }

    private static ArtifactId expectedSourceFileId(
        String path, long sizeBytes, Sha256Digest sha256, SourceOriginAttributes originAttributes) {
      ObjectNode identity = JsonNodeFactory.instance.objectNode();
      identity.put("format", "source-file-v2");
      identity.put("path", path);
      identity.put("entryKind", SourceEntry.Kind.REGULAR_FILE.name());
      identity.put("sizeBytes", sizeBytes);
      identity.put("sha256", sha256.value());
      ObjectNode origin = identity.putObject("originAttributes");
      origin.put("kind", originAttributes.kind().name());
      if (originAttributes.gitMode() == null) {
        origin.putNull("gitMode");
      } else {
        origin.put("gitMode", originAttributes.gitMode());
      }
      if (originAttributes.gitBlobObjectId() == null) {
        origin.putNull("gitBlobObjectId");
      } else {
        origin.put("gitBlobObjectId", originAttributes.gitBlobObjectId());
      }
      if (originAttributes.executable() == null) {
        origin.putNull("executable");
      } else {
        origin.put("executable", originAttributes.executable());
      }
      return ArtifactId.parse(
          "source-file:"
              + sha256Hex(new CanonicalJsonCodec().encodeCanonical(identity).copyToByteArray()));
    }

    private static String sha256Hex(byte[] bytes) {
      try {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      } catch (NoSuchAlgorithmException impossible) {
        throw new IllegalStateException(impossible);
      }
    }
  }
}
