package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.cli.SourcePreparationPolicyFixture;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreException;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.capture.preparation.CapturedSourcePreparation;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.capture.preparation.SourcePreparationToolIdentity;

class PreparedSourcePublicationBoundaryTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ArtifactStoreLimits STORE_LIMITS =
      new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 16);

  @TempDir Path temporaryDirectory;

  @Test
  void distinguishesACompleteEmptyOriginFromARootBlockedCapture() throws Exception {
    CanonicalArtifactPolicyRegistry policies = sourcePolicies();
    Path storeRoot = temporaryDirectory.resolve("empty-and-blocked-store");
    Path archiveRoot = temporaryDirectory.resolve("empty-and-blocked-archive");
    SourceOrigin emptyOrigin = directoryOrigin("empty", "empty-source");
    SourcePreparationResult emptyResult =
        result(
            true, List.of(), List.of(noAnalyzableTextIssue("empty-source-has-no-text")), List.of());
    SavedSourcePreparation empty =
        publish(
            policies,
            capture(
                policies,
                emptyOrigin,
                SourcePreparationOperation.NEW,
                null,
                List.of(),
                emptyResult,
                Map.of()),
            runId('a'),
            archiveRoot,
            storeRoot);

    assertThat(empty.assessment().readiness())
        .isEqualTo(SourcePreparationReadiness.NO_ANALYZABLE_TEXT);
    assertThat(empty.sourceVersionReference()).isNotNull();
    assertThat(reopen(policies, archiveRoot, storeRoot, empty.reportReference()).result())
        .isEqualTo(emptyResult);

    SourceIssue rootIssue =
        new SourceIssue(
            "missing-root",
            SourceIssue.Code.SOURCE_ROOT_NOT_FOUND,
            SourceIssue.Category.ACCESS,
            SourceIssue.Scope.ROOT,
            null,
            SourceIssue.Operation.LIST_DIRECTORY,
            "The configured source root is not available.",
            null,
            null,
            SourceIssue.Resolution.OPEN,
            Set.of(
                SourceIssue.AllowedAction.FIX_CONFIGURATION,
                SourceIssue.AllowedAction.NEW_PREPARATION),
            null);
    SourcePreparationResult blockedResult = result(false, List.of(), List.of(rootIssue), List.of());
    SavedSourcePreparation blocked =
        publish(
            policies,
            capture(
                policies,
                directoryOrigin("missing", "missing-source"),
                SourcePreparationOperation.NEW,
                null,
                List.of(),
                blockedResult,
                Map.of()),
            runId('b'),
            archiveRoot,
            storeRoot);

    assertThat(blocked.assessment().readiness()).isEqualTo(SourcePreparationReadiness.BLOCKED);
    assertThat(blocked.sourceVersionReference()).isNull();
    assertThat(blocked.sourceRegistrationRef()).isNull();
    assertThat(
            reopen(policies, archiveRoot, storeRoot, blocked.reportReference())
                .sourceVersionReference())
        .isNull();
    JsonNode blockedPublicResult =
        JSON.parseCanonical(
            payload(
                    reopenRaw(policies, storeRoot, blocked.reportReference()),
                    "source-preparation-result.json")
                .canonicalUtf8());
    assertThat(blockedPublicResult.get("sourceVersionId").isNull()).isTrue();
  }

  @Test
  void preservesUnknownSubtreeChoicesAndRecognizesACompletelyExcludedScope() throws Exception {
    CanonicalArtifactPolicyRegistry policies = sourcePolicies();
    Path storeRoot = temporaryDirectory.resolve("scope-store");
    Path archiveRoot = temporaryDirectory.resolve("scope-archive");
    byte[] acceptedText = bytes("class Known {}\n");
    String issueId = "unknown-child-enumeration-failed";
    SourceIssue unknownIssue =
        new SourceIssue(
            issueId,
            SourceIssue.Code.SOURCE_DIRECTORY_LIST_FAILED,
            SourceIssue.Category.ACCESS,
            SourceIssue.Scope.DIRECTORY,
            "src/private",
            SourceIssue.Operation.LIST_DIRECTORY,
            "The child entry could not be classified or enumerated.",
            null,
            null,
            SourceIssue.Resolution.OPEN,
            Set.of(
                SourceIssue.AllowedAction.REFRESH_FILE,
                SourceIssue.AllowedAction.REFRESH_DIRECTORY,
                SourceIssue.AllowedAction.EXCLUDE_FILE,
                SourceIssue.AllowedAction.EXCLUDE_DIRECTORY),
            null);
    SourceEntry unknownEntry =
        new SourceEntry(
            "src/private",
            SourceEntry.Kind.UNKNOWN,
            SourceEntry.Disposition.UNCHECKED,
            null,
            null,
            null,
            null,
            null,
            directoryAttributes(),
            null,
            List.of(issueId),
            null,
            null);
    SourceEntry knownEntry =
        verifiedEntry(
            "src/Known.java",
            acceptedText,
            SourceEntry.Disposition.VERIFIED_TEXT,
            directoryAttributes(),
            null);
    SourcePreparationResult unknownResult =
        new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.COMPLETED,
            false,
            List.of(knownEntry, unknownEntry),
            List.of(unknownIssue),
            List.of("src/private"),
            List.of());
    SavedSourcePreparation unknown =
        publish(
            policies,
            capture(
                policies,
                directoryOrigin("unknown", "unknown-source"),
                SourcePreparationOperation.NEW,
                null,
                List.of(),
                unknownResult,
                Map.of("src/Known.java", acceptedText)),
            runId('c'),
            archiveRoot,
            storeRoot);
    SavedSourcePreparation reopenedUnknown =
        reopen(policies, archiveRoot, storeRoot, unknown.reportReference());

    assertThat(reopenedUnknown.assessment().readiness())
        .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    assertThat(reopenedUnknown.assessment().summary().totalRegularFiles()).isNull();
    assertThat(reopenedUnknown.result().unknownSubtrees()).containsExactly("src/private");
    assertThat(reopenedUnknown.result().entries())
        .anySatisfy(
            entry -> {
              assertThat(entry.relativePath()).isEqualTo("src/private");
              assertThat(entry.entryKind()).isEqualTo(SourceEntry.Kind.UNKNOWN);
            });
    assertThat(reopenedUnknown.result().issues().get(0).allowedActions())
        .containsExactlyInAnyOrder(
            SourceIssue.AllowedAction.REFRESH_FILE,
            SourceIssue.AllowedAction.REFRESH_DIRECTORY,
            SourceIssue.AllowedAction.EXCLUDE_FILE,
            SourceIssue.AllowedAction.EXCLUDE_DIRECTORY);

    String excludedIssueId = "manual-pdf-excluded";
    SourceEntry excludedEntry =
        new SourceEntry(
            "docs/manual.pdf",
            SourceEntry.Kind.REGULAR_FILE,
            SourceEntry.Disposition.EXCLUDED_BY_USER,
            null,
            null,
            null,
            null,
            null,
            directoryAttributes(),
            null,
            List.of(excludedIssueId),
            null,
            new SourceEntryExclusion(
                "USER_REQUEST", SourcePreparationOperation.EXCLUDE, "docs/manual.pdf"));
    SourceIssue excludedIssue =
        new SourceIssue(
            excludedIssueId,
            SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
            SourceIssue.Category.ACCESS,
            SourceIssue.Scope.FILE,
            "docs/manual.pdf",
            SourceIssue.Operation.READ_INPUT,
            "The file was explicitly excluded from this preparation.",
            null,
            null,
            SourceIssue.Resolution.RESOLVED_BY_EXCLUSION,
            Set.of(),
            null);
    SourcePreparationResult excludedResult =
        result(
            true,
            List.of(excludedEntry),
            List.of(noAnalyzableTextIssue("excluded-scope-has-no-text"), excludedIssue),
            List.of());
    SourcePreparationTarget excludedTarget =
        new SourcePreparationTarget("docs/manual.pdf", SourcePreparationTarget.Kind.FILE);
    SourcePreparationRequest excludedRequest =
        request(
            policies,
            directoryOrigin("excluded", "excluded-source"),
            SourcePreparationOperation.NEW,
            null,
            List.of(),
            List.of(excludedTarget),
            List.of(excludedTarget));
    SavedSourcePreparation excluded =
        publish(
            policies,
            capture(excludedRequest, excludedResult, Map.of()),
            runId('d'),
            archiveRoot,
            storeRoot);

    assertThat(excluded.assessment().readiness())
        .isEqualTo(SourcePreparationReadiness.NO_ANALYZABLE_TEXT);
    assertThat(excluded.assessment().summary().excludedKnownFiles()).isEqualTo(1);
    SourcePreparationResult reopenedExcluded =
        reopen(policies, archiveRoot, storeRoot, excluded.reportReference()).result();
    assertThat(reopenedExcluded).isEqualTo(excludedResult);
    assertThat(reopenedExcluded.issues())
        .extracting(SourceIssue::issueId)
        .containsExactly("excluded-scope-has-no-text", excludedIssueId);
  }

  @Test
  void freshReopenRetainsDirectoryGitMediaAndInheritedEntryFacts() throws Exception {
    CanonicalArtifactPolicyRegistry policies = sourcePolicies();
    Path storeRoot = temporaryDirectory.resolve("metadata-store");
    Path archiveRoot = temporaryDirectory.resolve("metadata-archive");
    SourceOrigin directoryOrigin = directoryOrigin("directory", "directory-source");
    byte[] baseText = bytes("class Main { int version = 1; }\n");
    byte[] media = new byte[] {0, 2, 4, 6};
    SourceEntry sourceDirectory = directoryEntry("src");
    SourceEntry assetDirectory = directoryEntry("assets");
    SourceEntry baseTextEntry =
        verifiedEntry(
            "src/Main.java",
            baseText,
            SourceEntry.Disposition.VERIFIED_TEXT,
            directoryAttributes(),
            null);
    SourceEntry baseMediaEntry =
        verifiedEntry(
            "assets/logo.bin",
            media,
            SourceEntry.Disposition.VERIFIED_MEDIA,
            directoryAttributes(),
            null);
    SourcePreparationResult baseResult =
        result(
            true,
            List.of(assetDirectory, baseMediaEntry, sourceDirectory, baseTextEntry),
            List.of(),
            List.of());
    SavedSourcePreparation base =
        publish(
            policies,
            capture(
                policies,
                directoryOrigin,
                SourcePreparationOperation.NEW,
                null,
                List.of(),
                baseResult,
                Map.of("src/Main.java", baseText, "assets/logo.bin", media)),
            runId('e'),
            archiveRoot,
            storeRoot);
    SavedSourcePreparation reopenedBase =
        reopen(policies, archiveRoot, storeRoot, base.reportReference());
    assertThat(reopenedBase.result()).isEqualTo(baseResult);
    assertThat(reopenedBase.result().entries())
        .extracting(SourceEntry::relativePath)
        .containsExactly("assets", "assets/logo.bin", "src", "src/Main.java");
    assertThat(
            readArchived(
                archiveRoot, base.sourceVersionReference().sourceVersionId(), baseTextEntry))
        .containsExactly(baseText);
    assertThat(
            readArchived(
                archiveRoot, base.sourceVersionReference().sourceVersionId(), baseMediaEntry))
        .containsExactly(media);

    byte[] refreshedText = bytes("class Main { int version = 2; }\n");
    SourceEntry inheritedMedia =
        verifiedEntry(
            "assets/logo.bin",
            media,
            SourceEntry.Disposition.VERIFIED_MEDIA,
            directoryAttributes(),
            new SourceEntryInheritance(
                base.sourceVersionReference().sourceVersionId(), baseMediaEntry.fileId()));
    SourcePreparationRequest refreshRequest =
        request(
            policies,
            directoryOrigin,
            SourcePreparationOperation.REFRESH,
            base.sourceVersionReference(),
            List.of(
                new SourcePreparationTarget("src/Main.java", SourcePreparationTarget.Kind.FILE)),
            List.of(),
            List.of());
    SourceEntry refreshedTextEntry =
        verifiedEntry(
            "src/Main.java",
            refreshedText,
            SourceEntry.Disposition.VERIFIED_TEXT,
            directoryAttributes(),
            null);
    SourcePreparationResult refreshResult =
        result(
            true,
            List.of(assetDirectory, inheritedMedia, sourceDirectory, refreshedTextEntry),
            List.of(),
            List.of());
    SavedSourcePreparation refreshed =
        publish(
            policies,
            capture(
                refreshRequest,
                refreshResult,
                Map.of("src/Main.java", refreshedText, "assets/logo.bin", media)),
            runId('f'),
            archiveRoot,
            storeRoot);
    SavedSourcePreparation reopenedRefresh =
        reopen(policies, archiveRoot, storeRoot, refreshed.reportReference());

    assertThat(reopenedRefresh.result()).isEqualTo(refreshResult);
    assertThat(reopenedRefresh.result().entries())
        .extracting(SourceEntry::relativePath)
        .containsExactly("assets", "assets/logo.bin", "src", "src/Main.java");
    SourceEntry reopenedInheritedMedia = entry(reopenedRefresh.result(), "assets/logo.bin");
    assertThat(reopenedInheritedMedia.inheritedFrom()).isEqualTo(inheritedMedia.inheritedFrom());
    assertThat(
            jsonlRows(
                reopenRaw(policies, storeRoot, refreshed.reportReference()),
                "source-inventory.jsonl"))
        .anySatisfy(
            row -> {
              assertThat(row.get("relativePath").textValue()).isEqualTo("assets/logo.bin");
              assertThat(row.get("inheritedFrom").textValue())
                  .isEqualTo(base.sourceVersionReference().sourceVersionId().value());
            });

    SourceOrigin gitOrigin =
        new GitCommitSourceOrigin(
            "fixture.invalid/git",
            temporaryDirectory.resolve("git-checkout").toAbsolutePath().normalize(),
            "a".repeat(40));
    SourceOriginAttributes gitTextAttributes =
        new SourceOriginAttributes(
            SourceOriginAttributes.Kind.GIT_COMMIT, "100644", "b".repeat(40), null);
    SourceOriginAttributes gitMediaAttributes =
        new SourceOriginAttributes(
            SourceOriginAttributes.Kind.GIT_COMMIT, "100755", "c".repeat(40), null);
    byte[] gitText = bytes("class GitMain {}\n");
    byte[] gitMedia = new byte[] {1, 3, 5, 7};
    SourceEntry gitTextEntry =
        verifiedEntry(
            "src/GitMain.java",
            gitText,
            SourceEntry.Disposition.VERIFIED_TEXT,
            gitTextAttributes,
            null);
    SourceEntry gitMediaEntry =
        verifiedEntry(
            "assets/git-logo.bin",
            gitMedia,
            SourceEntry.Disposition.VERIFIED_MEDIA,
            gitMediaAttributes,
            null);
    SourcePreparationResult gitResult =
        result(true, List.of(gitMediaEntry, gitTextEntry), List.of(), List.of());
    SavedSourcePreparation gitSaved =
        publish(
            policies,
            capture(
                policies,
                gitOrigin,
                SourcePreparationOperation.NEW,
                null,
                List.of(),
                gitResult,
                Map.of("src/GitMain.java", gitText, "assets/git-logo.bin", gitMedia)),
            runId('1'),
            archiveRoot,
            storeRoot);
    SavedSourcePreparation reopenedGit =
        reopen(policies, archiveRoot, storeRoot, gitSaved.reportReference());

    assertThat(reopenedGit.result()).isEqualTo(gitResult);
    assertThat(reopenedGit.result().entries())
        .extracting(SourceEntry::relativePath)
        .containsExactly("assets/git-logo.bin", "src/GitMain.java");
    List<JsonNode> gitRows =
        jsonlRows(
            reopenRaw(policies, storeRoot, gitSaved.reportReference()), "source-inventory.jsonl");
    assertThat(gitRows)
        .anySatisfy(
            row -> {
              assertThat(row.get("relativePath").textValue()).isEqualTo("src/GitMain.java");
              assertThat(row.get("originAttributes").get("gitMode").textValue())
                  .isEqualTo("100644");
              assertThat(row.get("originAttributes").get("gitBlobObjectId").textValue())
                  .isEqualTo("b".repeat(40));
            });
    assertThat(gitRows)
        .anySatisfy(
            row -> {
              assertThat(row.get("relativePath").textValue()).isEqualTo("assets/git-logo.bin");
              assertThat(row.get("originAttributes").get("gitMode").textValue())
                  .isEqualTo("100755");
              assertThat(row.get("disposition").textValue()).isEqualTo("VERIFIED_MEDIA");
            });
  }

  @Test
  void rejectsUnverifiedBytesAndARegistryMissingThePreparationPolicies() throws Exception {
    byte[] expected = bytes("class Expected {}\n");
    byte[] changed = bytes("class Changed  {}\n");
    CanonicalArtifactPolicyRegistry policies = sourcePolicies();
    SourceEntry entry =
        verifiedEntry(
            "src/Expected.java",
            expected,
            SourceEntry.Disposition.VERIFIED_TEXT,
            directoryAttributes(),
            null);
    SourcePreparationResult result = result(true, List.of(entry), List.of(), List.of());
    CapturedSourcePreparation wrongBytes =
        capture(
            policies,
            directoryOrigin("wrong-bytes", "wrong-bytes-source"),
            SourcePreparationOperation.NEW,
            null,
            List.of(),
            result,
            Map.of(entry.relativePath(), changed));

    assertThatThrownBy(
            () ->
                publish(
                    policies,
                    wrongBytes,
                    runId('2'),
                    temporaryDirectory.resolve("wrong-bytes-archive"),
                    temporaryDirectory.resolve("wrong-bytes-store")))
        .isInstanceOf(IOException.class);

    CanonicalArtifactPolicyRegistry historicalPolicies = historicalPolicies();
    CapturedSourcePreparation wrongPolicy =
        capture(
            historicalPolicies,
            directoryOrigin("wrong-policy", "wrong-policy-source"),
            SourcePreparationOperation.NEW,
            null,
            List.of(),
            result,
            Map.of(entry.relativePath(), expected));

    assertThatThrownBy(
            () ->
                publish(
                    historicalPolicies,
                    wrongPolicy,
                    runId('3'),
                    temporaryDirectory.resolve("wrong-policy-archive"),
                    temporaryDirectory.resolve("wrong-policy-store")))
        .isInstanceOf(ArtifactStoreException.class)
        .hasMessage("ARTIFACT_POLICY_NOT_FOUND");
  }

  @Test
  void failedFinalInstallAndInvalidSavedReferencesNeverProduceAReopenableSource() throws Exception {
    CanonicalArtifactPolicyRegistry policies = sourcePolicies();
    byte[] text = bytes("class Durable {}\n");
    SourceEntry entry =
        verifiedEntry(
            "src/Durable.java",
            text,
            SourceEntry.Disposition.VERIFIED_TEXT,
            directoryAttributes(),
            null);
    SourcePreparationResult result = result(true, List.of(entry), List.of(), List.of());
    CapturedSourcePreparation capture =
        capture(
            policies,
            directoryOrigin("install-failure", "install-failure-source"),
            SourcePreparationOperation.NEW,
            null,
            List.of(),
            result,
            Map.of(entry.relativePath(), text));
    Path interruptedStore = temporaryDirectory.resolve("interrupted-store");
    Path interruptedArchive = temporaryDirectory.resolve("interrupted-archive");
    Files.createDirectories(interruptedStore);
    try (RunStoreHandle handle = RunStoreBootstrap.open(interruptedStore)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore delegate =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore interruptedSteps =
          new CanonicalAnalysisStepArtifactStore() {
            @Override
            public InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request) {
              throw new IllegalStateException("injected final step installation failure");
            }

            @Override
            public ReopenedAnalysisStepPublication reopen(
                AnalysisStepPublicationReference reference) {
              return delegate.reopen(reference);
            }
          };
      assertThatThrownBy(
              () ->
                  new SourcePreparationPublisher(
                          modules,
                          interruptedSteps,
                          new PreparedSourceArchive(interruptedArchive),
                          policies)
                      .publish(runId('4'), capture))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("injected final step installation failure");
    }

    Path savedStore = temporaryDirectory.resolve("saved-reference-store");
    Path savedArchive = temporaryDirectory.resolve("saved-reference-archive");
    SavedSourcePreparation saved = publish(policies, capture, runId('5'), savedArchive, savedStore);
    AnalysisStepPublicationReference badReference =
        new AnalysisStepPublicationReference(
            saved.reportReference().address(),
            saved.reportReference().analysisStepArtifactRoot(),
            saved.reportReference().analysisStepReceiptId(),
            Sha256Digest.parse("0".repeat(64)));
    assertThatThrownBy(() -> reopen(policies, savedArchive, savedStore, badReference))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("prepared source publication cannot be reopened");

    Path frozenBlob =
        savedArchive
            .resolve(saved.sourceVersionReference().sourceVersionId().value())
            .resolve("blobs")
            .resolve(entry.blobRef().sha256().value());
    Files.write(frozenBlob, bytes("tampered archived bytes\n"));
    assertThatThrownBy(() -> reopen(policies, savedArchive, savedStore, saved.reportReference()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("prepared source publication cannot be reopened");
  }

  @Test
  void freshReopenRejectsMissingM1ReceiptWhileStepM3AndPrivateArchiveRemainValid()
      throws Exception {
    assertMissingModuleReceiptIsRejected('6', "m1", "01-request-admission");
  }

  @Test
  void freshReopenRejectsMissingM2ReceiptWhileStepM3AndPrivateArchiveRemainValid()
      throws Exception {
    assertMissingModuleReceiptIsRejected('7', "m2", "02-source-index");
  }

  @Test
  void freshReopenKeepsPublicationLinksSeparateForSameSourceVersionAcrossRuns() throws Exception {
    CanonicalArtifactPolicyRegistry policies = sourcePolicies();
    byte[] text = bytes("class ReusedCapture {}\n");
    SourceEntry entry =
        verifiedEntry(
            "src/ReusedCapture.java",
            text,
            SourceEntry.Disposition.VERIFIED_TEXT,
            directoryAttributes(),
            null);
    SourcePreparationResult result = result(true, List.of(entry), List.of(), List.of());
    CapturedSourcePreparation capture =
        capture(
            policies,
            directoryOrigin("same-capture", "same-capture-source"),
            SourcePreparationOperation.NEW,
            null,
            List.of(),
            result,
            Map.of(entry.relativePath(), text));
    Path storeRoot = temporaryDirectory.resolve("same-capture-store");
    Path archiveRoot = temporaryDirectory.resolve("same-capture-archive");
    SavedSourcePreparation first = publish(policies, capture, runId('8'), archiveRoot, storeRoot);
    SavedSourcePreparation second = publish(policies, capture, runId('9'), archiveRoot, storeRoot);

    assertThat(first.sourceVersionReference().sourceVersionId())
        .isEqualTo(second.sourceVersionReference().sourceVersionId());
    assertThat(first.reportReference().address()).isNotEqualTo(second.reportReference().address());
    SavedSourcePreparation reopenedFirst =
        reopen(policies, archiveRoot, storeRoot, first.reportReference());
    SavedSourcePreparation reopenedSecond =
        reopen(policies, archiveRoot, storeRoot, second.reportReference());

    assertThat(reopenedFirst.result()).isEqualTo(result);
    assertThat(reopenedSecond.result()).isEqualTo(result);
    assertThat(reopenedFirst.sourceVersionReference().publication())
        .isEqualTo(first.reportReference());
    assertThat(reopenedSecond.sourceVersionReference().publication())
        .isEqualTo(second.reportReference());
  }

  @Test
  void publicationLinksAreIdempotentAndRejectConflictingRefsWithoutChangingSavedBytes()
      throws Exception {
    CanonicalArtifactPolicyRegistry policies = sourcePolicies();
    byte[] text = bytes("class ImmutableLinks {}\n");
    SourceEntry entry =
        verifiedEntry(
            "src/ImmutableLinks.java",
            text,
            SourceEntry.Disposition.VERIFIED_TEXT,
            directoryAttributes(),
            null);
    CapturedSourcePreparation capture =
        capture(
            policies,
            directoryOrigin("immutable-links", "immutable-links-source"),
            SourcePreparationOperation.NEW,
            null,
            List.of(),
            result(true, List.of(entry), List.of(), List.of()),
            Map.of(entry.relativePath(), text));
    Path storeRoot = temporaryDirectory.resolve("immutable-links-store");
    Path archiveRoot = temporaryDirectory.resolve("immutable-links-archive");
    SavedSourcePreparation saved = publish(policies, capture, runId('a'), archiveRoot, storeRoot);
    ArtifactId sourceVersionId = saved.sourceVersionReference().sourceVersionId();
    AnalysisStepPublicationAddress reportAddress = saved.reportReference().address();
    PreparedSourceArchive archive = new PreparedSourceArchive(archiveRoot);
    PreparedSourceArchive.PublicationLinks savedLinks =
        archive.reopenPublicationLinks(sourceVersionId, reportAddress);
    Path linksFile = publicationLinksFile(archiveRoot, sourceVersionId);
    byte[] originalLinksBytes = Files.readAllBytes(linksFile);

    archive.savePublicationLinks(
        sourceVersionId, reportAddress, savedLinks.requestAdmission(), savedLinks.sourceIndex());
    assertThat(Files.readAllBytes(linksFile)).containsExactly(originalLinksBytes);
    assertThat(archive.reopenPublicationLinks(sourceVersionId, reportAddress))
        .isEqualTo(savedLinks);

    Sha256Digest conflictingReceiptDigest =
        Sha256Digest.parse(
            savedLinks.requestAdmission().moduleReceiptSha256().value().equals("0".repeat(64))
                ? "1".repeat(64)
                : "0".repeat(64));
    ModulePublicationReference conflictingM1 =
        new ModulePublicationReference(
            savedLinks.requestAdmission().address(),
            savedLinks.requestAdmission().moduleArtifactRoot(),
            savedLinks.requestAdmission().moduleReceiptId(),
            conflictingReceiptDigest);

    assertThatThrownBy(
            () ->
                archive.savePublicationLinks(
                    sourceVersionId, reportAddress, conflictingM1, savedLinks.sourceIndex()))
        .isInstanceOf(IOException.class);
    assertThat(Files.readAllBytes(linksFile)).containsExactly(originalLinksBytes);
    assertThat(archive.reopenPublicationLinks(sourceVersionId, reportAddress))
        .isEqualTo(savedLinks);
  }

  private void assertMissingModuleReceiptIsRejected(
      char runIdentity, String fixtureName, String moduleDirectory) throws Exception {
    CanonicalArtifactPolicyRegistry policies = sourcePolicies();
    byte[] text = bytes("class LinkedModules {}\n");
    SourceEntry entry =
        verifiedEntry(
            "src/LinkedModules.java",
            text,
            SourceEntry.Disposition.VERIFIED_TEXT,
            directoryAttributes(),
            null);
    CapturedSourcePreparation capture =
        capture(
            policies,
            directoryOrigin(
                "publication-links-" + fixtureName, "publication-links-source-" + fixtureName),
            SourcePreparationOperation.NEW,
            null,
            List.of(),
            result(true, List.of(entry), List.of(), List.of()),
            Map.of(entry.relativePath(), text));
    Path storeRoot = temporaryDirectory.resolve("publication-links-" + fixtureName + "-store");
    Path archiveRoot = temporaryDirectory.resolve("publication-links-" + fixtureName + "-archive");
    SavedSourcePreparation saved =
        publish(policies, capture, runId(runIdentity), archiveRoot, storeRoot);

    ReopenedAnalysisStepPublication step = reopenRaw(policies, storeRoot, saved.reportReference());
    assertThat(step.semanticPayloads()).hasSize(4);
    ModulePublicationReference m3Reference =
        ((AnalysisStepPublisherModuleProvenance) step.receipt().publicationProvenance())
            .publisherSpecificationModuleReference();
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      assertThat(modules.reopen(m3Reference).payloads()).hasSize(4);
    }
    assertThat(
            new PreparedSourceArchive(archiveRoot)
                .reopen(saved.sourceVersionReference().sourceVersionId())
                .result())
        .isEqualTo(capture.result());

    Path upstreamReceipt = moduleReceipt(storeRoot, moduleDirectory);
    assertThat(upstreamReceipt).exists();
    Files.delete(upstreamReceipt);

    assertThat(reopenRaw(policies, storeRoot, saved.reportReference()).semanticPayloads())
        .hasSize(4);
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      assertThat(modules.reopen(m3Reference).payloads()).hasSize(4);
    }
    assertThat(
            new PreparedSourceArchive(archiveRoot)
                .reopen(saved.sourceVersionReference().sourceVersionId())
                .result())
        .isEqualTo(capture.result());
    assertThatThrownBy(() -> reopen(policies, archiveRoot, storeRoot, saved.reportReference()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("prepared source publication cannot be reopened");
  }

  private SavedSourcePreparation publish(
      CanonicalArtifactPolicyRegistry policies,
      CapturedSourcePreparation capture,
      AnalysisRunId runId,
      Path archiveRoot,
      Path storeRoot)
      throws IOException {
    Files.createDirectories(storeRoot);
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      return new SourcePreparationPublisher(
              modules, steps, new PreparedSourceArchive(archiveRoot), policies)
          .publish(runId, capture);
    }
  }

  private SavedSourcePreparation reopen(
      CanonicalArtifactPolicyRegistry policies,
      Path archiveRoot,
      Path storeRoot,
      AnalysisStepPublicationReference reportReference) {
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      return new SourcePreparationReader(modules, steps, new PreparedSourceArchive(archiveRoot))
          .reopen(reportReference);
    }
  }

  private ReopenedAnalysisStepPublication reopenRaw(
      CanonicalArtifactPolicyRegistry policies,
      Path storeRoot,
      AnalysisStepPublicationReference reportReference) {
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      return steps.reopen(reportReference);
    }
  }

  private static Path moduleReceipt(Path storeRoot, String moduleDirectory) throws IOException {
    try (var paths = Files.walk(storeRoot)) {
      return paths
          .filter(path -> path.getFileName().toString().equals("module-receipt.json"))
          .filter(path -> path.getParent().getFileName().toString().equals(moduleDirectory))
          .findFirst()
          .orElseThrow(() -> new IOException("module receipt not found: " + moduleDirectory));
    }
  }

  private static Path publicationLinksFile(Path archiveRoot, ArtifactId sourceVersionId)
      throws IOException {
    Path directory = archiveRoot.resolve(sourceVersionId.value()).resolve("publication-links");
    try (var paths = Files.list(directory)) {
      List<Path> files = paths.filter(Files::isRegularFile).toList();
      assertThat(files).hasSize(1);
      return files.get(0);
    }
  }

  private CapturedSourcePreparation capture(
      CanonicalArtifactPolicyRegistry policies,
      SourceOrigin origin,
      SourcePreparationOperation operation,
      PreparedSourceReference base,
      List<SourcePreparationTarget> targets,
      SourcePreparationResult result,
      Map<String, byte[]> acceptedBytes) {
    return capture(
        request(policies, origin, operation, base, targets, List.of(), List.of()),
        result,
        acceptedBytes);
  }

  private CapturedSourcePreparation capture(
      SourcePreparationRequest request,
      SourcePreparationResult result,
      Map<String, byte[]> acceptedBytes) {
    return new CapturedSourcePreparation(
        request,
        result,
        entry -> {
          byte[] bytes = acceptedBytes.get(entry.relativePath());
          if (bytes == null) {
            throw new IOException("No accepted bytes for " + entry.relativePath());
          }
          return new ByteArrayInputStream(bytes);
        },
        toolIdentity());
  }

  private SourcePreparationRequest request(
      CanonicalArtifactPolicyRegistry policies,
      SourceOrigin origin,
      SourcePreparationOperation operation,
      PreparedSourceReference base,
      List<SourcePreparationTarget> targets,
      List<SourcePreparationTarget> declaredExclusions,
      List<SourcePreparationTarget> effectiveExclusions) {
    return new SourcePreparationRequest(
        operation,
        origin,
        base,
        targets,
        declaredExclusions,
        effectiveExclusions,
        new SourcePreparationLimits(20, 1_000_000L),
        new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256()));
  }

  private static SourceEntry verifiedEntry(
      String path,
      byte[] bytes,
      SourceEntry.Disposition disposition,
      SourceOriginAttributes attributes,
      SourceEntryInheritance inheritance) {
    Sha256Digest sha256 = digest(bytes);
    ArtifactReference blobRef =
        new ArtifactReference(ArtifactId.parse("source-blob:" + sha256.value()), sha256);
    ArtifactId fileId = SourceVersionCalculator.fileId(path, bytes.length, sha256, attributes);
    SourceObservation observed =
        new SourceObservation((long) bytes.length, sha256, fileId, null, "fixture:" + path);
    return new SourceEntry(
        path,
        SourceEntry.Kind.REGULAR_FILE,
        disposition,
        (long) bytes.length,
        sha256,
        blobRef,
        fileId,
        disposition == SourceEntry.Disposition.VERIFIED_TEXT ? "UTF-8" : null,
        attributes,
        new SourceEntryObservations(observed, observed),
        List.of(),
        inheritance,
        null);
  }

  private static SourceEntry directoryEntry(String path) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.DIRECTORY,
        SourceEntry.Disposition.ENUMERATED_DIRECTORY,
        null,
        null,
        null,
        null,
        null,
        directoryAttributes(),
        null,
        List.of(),
        null,
        null);
  }

  private static SourceOriginAttributes directoryAttributes() {
    return new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null);
  }

  private static SourcePreparationResult result(
      boolean enumerationComplete,
      List<SourceEntry> entries,
      List<SourceIssue> issues,
      List<String> unknownSubtrees) {
    return new SourcePreparationResult(
        SourcePreparationResult.InspectionStatus.COMPLETED,
        enumerationComplete,
        entries,
        issues,
        unknownSubtrees,
        List.of());
  }

  private SourceOrigin directoryOrigin(String logicalIdentity, String rootName) {
    return new DirectorySourceOrigin(
        "fixture.invalid/" + logicalIdentity,
        temporaryDirectory.resolve(rootName).toAbsolutePath().normalize());
  }

  private CanonicalArtifactPolicyRegistry sourcePolicies() {
    return SourcePreparationPolicyFixture.load(
        Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
            .toAbsolutePath(),
        JSON);
  }

  private CanonicalArtifactPolicyRegistry historicalPolicies() {
    return SourcePreparationPolicyFixture.load(
        Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json").toAbsolutePath(), JSON);
  }

  private static SourceIssue noAnalyzableTextIssue(String issueId) {
    return new SourceIssue(
        issueId,
        SourceIssue.Code.SOURCE_NO_ANALYZABLE_TEXT,
        SourceIssue.Category.UNSUPPORTED,
        SourceIssue.Scope.REQUEST,
        null,
        SourceIssue.Operation.READ_INPUT,
        "The completed source scope contains no analyzable text.",
        null,
        null,
        SourceIssue.Resolution.INFORMATIONAL,
        Set.of(),
        null);
  }

  private static List<JsonNode> jsonlRows(
      ReopenedAnalysisStepPublication publication, String fileName) {
    String jsonl =
        new String(
            payload(publication, fileName).canonicalUtf8().copyToByteArray(),
            StandardCharsets.UTF_8);
    return jsonl
        .lines()
        .filter(line -> !line.isBlank())
        .map(
            line ->
                JSON.parseCanonical(
                    org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(bytes(line))))
        .toList();
  }

  private static VerifiedCanonicalPayload payload(
      ReopenedAnalysisStepPublication publication, String fileName) {
    return publication.semanticPayloads().stream()
        .filter(candidate -> candidate.descriptor().fileName().equals(fileName))
        .findFirst()
        .orElseThrow();
  }

  private static SourceEntry entry(SourcePreparationResult result, String path) {
    return result.entries().stream()
        .filter(candidate -> candidate.relativePath().equals(path))
        .findFirst()
        .orElseThrow();
  }

  private static byte[] readArchived(
      PreparedSourceArchive archive, ArtifactId sourceVersionId, SourceEntry entry)
      throws IOException {
    try (InputStream input = archive.open(sourceVersionId, entry)) {
      return input.readAllBytes();
    }
  }

  private static byte[] readArchived(
      Path archiveRoot, ArtifactId sourceVersionId, SourceEntry entry) throws IOException {
    return readArchived(new PreparedSourceArchive(archiveRoot), sourceVersionId, entry);
  }

  private static SourcePreparationToolIdentity toolIdentity() {
    return new SourcePreparationToolIdentity(
        "verified-source-inventory/v3",
        "fixture",
        Sha256Digest.parse("c".repeat(64)),
        "fixture-vendor",
        "fixture-java",
        null);
  }

  private static AnalysisRunId runId(char identity) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(identity).repeat(64));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static Sha256Digest digest(byte[] bytes) {
    try {
      return Sha256Digest.parse(
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
