package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.cli.SourcePreparationPolicyFixture;
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
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.capture.preparation.CapturedSourcePreparation;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.capture.preparation.SourcePreparationToolIdentity;

class PreparedSourcePublicationTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ArtifactStoreLimits STORE_LIMITS =
      new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 16);
  private static final String ISSUE_ID = "read-failed-src-broken-java";

  @TempDir Path temporaryDirectory;

  @Test
  void publishesAndFreshReopensFourCanonicalFilesWithEmptyIssuesAndNoNewLineIndex()
      throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    byte[] stagedText = "package fixture;\nfinal class Main {}\n".getBytes(StandardCharsets.UTF_8);
    byte[] stagedMedia = new byte[] {0, 1, 2, 3};
    List<SourceEntry> entries =
        List.of(
            verifiedEntry("assets/logo.bin", stagedMedia, SourceEntry.Disposition.VERIFIED_MEDIA),
            verifiedEntry("src/Main.java", stagedText, SourceEntry.Disposition.VERIFIED_TEXT));
    SourceFixture fixture =
        sourceFixture(
            "ready",
            entries,
            List.of(),
            Map.of("assets/logo.bin", stagedMedia, "src/Main.java", stagedText));
    Map<String, Integer> stagedOpens = new HashMap<>();
    Path archiveRoot = temporaryDirectory.resolve("ready-archive");
    Path storeRoot = temporaryDirectory.resolve("ready-store");
    var saved = publish(policies, fixture, stagedOpens, archiveRoot, storeRoot, runId('a'));

    assertThat(saved.assessment().readiness()).isEqualTo(SourcePreparationReadiness.READY);
    assertThat(saved.sourceVersionReference()).isNotNull();
    assertThat(saved.sourceRegistrationRef()).isNotNull();
    assertThat(stagedOpens)
        .containsExactlyInAnyOrderEntriesOf(Map.of("assets/logo.bin", 1, "src/Main.java", 1));

    ReopenedAnalysisStepPublication publication = reopenRaw(policies, storeRoot, saved);
    assertReceiptAndPayloads(publication, ModuleCompletionStatus.SUCCEEDED, List.of());
    assertThat(
            JSON.parseCanonical(
                    payload(publication, "source-preparation-result.json").canonicalUtf8())
                .get("readiness")
                .textValue())
        .isEqualTo("READY");
    assertThat(payload(publication, "source-issues.jsonl").canonicalUtf8().copyToByteArray())
        .isEmpty();
    assertThat(payload(publication, "source-issues.jsonl").descriptor().sizeBytes()).isZero();
    assertInventoryHasNoLineIndexes(publication);
    assertPublicPayloadsDoNotContainSourceRoot(publication, fixture.origin().canonicalRoot());

    var reopened = reopenTyped(policies, archiveRoot, storeRoot, saved);
    assertThat(reopened.reportReference()).isEqualTo(saved.reportReference());
    assertThat(reopened.result()).isEqualTo(fixture.result());
    assertThat(reopened.assessment().readiness()).isEqualTo(SourcePreparationReadiness.READY);
  }

  @Test
  void publishesAndFreshReopensNamedPartialIssueAsNeedsDecisionWithAReceiptGap() throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    byte[] stagedText = "package fixture;\nfinal class Good {}\n".getBytes(StandardCharsets.UTF_8);
    SourceIssue issue =
        new SourceIssue(
            ISSUE_ID,
            SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
            SourceIssue.Category.ACCESS,
            SourceIssue.Scope.FILE,
            "src/Broken.java",
            SourceIssue.Operation.READ_INPUT,
            "Could not read src/Broken.java.",
            null,
            null,
            SourceIssue.Resolution.OPEN,
            Set.of(SourceIssue.AllowedAction.REFRESH_FILE, SourceIssue.AllowedAction.EXCLUDE_FILE),
            null);
    List<SourceEntry> entries =
        List.of(
            unavailableEntry("src/Broken.java", ISSUE_ID),
            verifiedEntry("src/Good.java", stagedText, SourceEntry.Disposition.VERIFIED_TEXT));
    SourceFixture fixture =
        sourceFixture("partial", entries, List.of(issue), Map.of("src/Good.java", stagedText));
    Map<String, Integer> stagedOpens = new HashMap<>();
    Path archiveRoot = temporaryDirectory.resolve("partial-archive");
    Path storeRoot = temporaryDirectory.resolve("partial-store");
    var saved = publish(policies, fixture, stagedOpens, archiveRoot, storeRoot, runId('b'));

    assertThat(saved.assessment().readiness()).isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    assertThat(saved.sourceVersionReference())
        .as("a safely locatable partial result remains refreshable/excludable")
        .isNotNull();
    assertThat(stagedOpens).containsExactlyEntriesOf(Map.of("src/Good.java", 1));

    ReopenedAnalysisStepPublication publication = reopenRaw(policies, storeRoot, saved);
    assertReceiptAndPayloads(
        publication,
        ModuleCompletionStatus.SUCCEEDED_WITH_GAPS,
        List.of("source-issue:" + ISSUE_ID));
    assertThat(
            JSON.parseCanonical(
                    payload(publication, "source-preparation-result.json").canonicalUtf8())
                .get("readiness")
                .textValue())
        .isEqualTo("NEEDS_DECISION");
    JsonNode reopenedIssue = jsonlRows(publication, "source-issues.jsonl").get(0);
    assertThat(reopenedIssue.get("issueId").textValue()).isEqualTo(ISSUE_ID);
    assertThat(reopenedIssue.get("code").textValue()).isEqualTo("SOURCE_ENTRY_READ_FAILED");
    assertThat(reopenedIssue.get("relativePath").textValue()).isEqualTo("src/Broken.java");
    assertInventoryHasNoLineIndexes(publication);
    assertPublicPayloadsDoNotContainSourceRoot(publication, fixture.origin().canonicalRoot());

    var reopened = reopenTyped(policies, archiveRoot, storeRoot, saved);
    assertThat(reopened.result()).isEqualTo(fixture.result());
    assertThat(reopened.assessment().readiness())
        .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    assertThat(reopened.sourceVersionReference()).isNotNull();
  }

  @Test
  void preparedSourceTextViewDoesNotExposeExcludedEntryEvenWhenItRetainsBlobReference()
      throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    byte[] includedBytes =
        "package fixture;\nfinal class Included {}\n".getBytes(StandardCharsets.UTF_8);
    byte[] excludedBytes =
        "package fixture;\nfinal class Excluded {}\n".getBytes(StandardCharsets.UTF_8);
    Path sourceRoot =
        Files.createDirectory(temporaryDirectory.resolve("prepared-text-view-source"));
    SourceOrigin origin =
        new DirectorySourceOrigin("fixture.invalid/prepared-text-view", sourceRoot.toRealPath());
    SourcePreparationTarget excludedTarget =
        new SourcePreparationTarget("src/Excluded.java", SourcePreparationTarget.Kind.FILE);
    SourcePreparationRequest request =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            origin,
            null,
            List.of(),
            List.of(excludedTarget),
            List.of(excludedTarget),
            new SourcePreparationLimits(20, 1_000_000L),
            policyRef(policies));
    SourceEntry included =
        verifiedEntry("src/Included.java", includedBytes, SourceEntry.Disposition.VERIFIED_TEXT);
    SourceEntry excludedContent =
        verifiedEntry("src/Excluded.java", excludedBytes, SourceEntry.Disposition.VERIFIED_TEXT);
    SourceEntry excluded =
        new SourceEntry(
            excludedContent.relativePath(),
            excludedContent.entryKind(),
            SourceEntry.Disposition.EXCLUDED_BY_USER,
            excludedContent.sizeBytes(),
            excludedContent.sha256(),
            excludedContent.blobRef(),
            excludedContent.fileId(),
            null,
            excludedContent.originAttributes(),
            excludedContent.observations(),
            List.of(),
            null,
            new SourceEntryExclusion(
                "USER_DECLARED", SourcePreparationOperation.NEW, excludedTarget.relativePath()));
    SourcePreparationResult result =
        new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.COMPLETED,
            true,
            List.of(included, excluded),
            List.of(),
            List.of(),
            List.of());
    SourceFixture fixture =
        new SourceFixture(origin, request, result, Map.of("src/Included.java", includedBytes));
    Path archiveRoot = temporaryDirectory.resolve("prepared-text-view-archive");
    Path storeRoot = temporaryDirectory.resolve("prepared-text-view-store");
    Map<String, Integer> stagedOpens = new HashMap<>();
    var saved = publish(policies, fixture, stagedOpens, archiveRoot, storeRoot, runId('c'));

    assertThat(saved.assessment().readiness())
        .isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
    assertThat(saved.sourceVersionReference()).isNotNull();
    SourceEntry savedExcluded =
        saved.result().entries().stream()
            .filter(entry -> entry.relativePath().equals("src/Excluded.java"))
            .findFirst()
            .orElseThrow();
    assertThat(savedExcluded.blobRef()).isNotNull();
    assertThat(stagedOpens).containsExactlyEntriesOf(Map.of("src/Included.java", 1));

    VerifiedSourceTextSet sourceTexts;
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      PreparedSourceArchive archive = new PreparedSourceArchive(archiveRoot);
      SourcePreparationReader preparations = new SourcePreparationReader(modules, steps, archive);
      sourceTexts =
          new PreparedVerifiedSourceTextReader(preparations, archive)
              .reopen(
                  new VerifiedSourceInventoryReference(
                      saved.sourceVersionReference().publication()));
    }

    assertThat(sourceTexts.snapshotId())
        .isEqualTo(saved.sourceVersionReference().sourceVersionId().value());
    assertThat(sourceTexts.inventoryScopeKind()).isEqualTo("BOUNDED_PATH_SET");
    assertThat(sourceTexts.repositoryCompletionEligible()).isFalse();
    assertThat(sourceTexts.documents())
        .extracting(VerifiedSourceTextDocument::path)
        .containsExactly("src/Included.java");
    VerifiedSourceTextDocument includedDocument = sourceTexts.documents().get(0);
    assertThat(includedDocument.gitMode()).isNull();
    assertThat(includedDocument.mediaType()).isEqualTo("text/plain; charset=UTF-8");
    assertThat(includedDocument.rawUtf8().copyToByteArray()).isEqualTo(includedBytes);
  }

  @Test
  void preparedSourceTextReaderRejectsNeedsDecisionEvenWhenVerifiedTextIsPresent()
      throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    byte[] acceptedText =
        "package fixture;\nfinal class Good {}\n".getBytes(StandardCharsets.UTF_8);
    SourceIssue issue =
        new SourceIssue(
            "read-failed-src-missing-java",
            SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
            SourceIssue.Category.ACCESS,
            SourceIssue.Scope.FILE,
            "src/Missing.java",
            SourceIssue.Operation.READ_INPUT,
            "Could not read src/Missing.java.",
            null,
            null,
            SourceIssue.Resolution.OPEN,
            Set.of(SourceIssue.AllowedAction.REFRESH_FILE, SourceIssue.AllowedAction.EXCLUDE_FILE),
            null);
    SourceFixture fixture =
        sourceFixture(
            "needs-decision-reader",
            List.of(
                unavailableEntry("src/Missing.java", issue.issueId()),
                verifiedEntry(
                    "src/Good.java", acceptedText, SourceEntry.Disposition.VERIFIED_TEXT)),
            List.of(issue),
            Map.of("src/Good.java", acceptedText));
    Path archiveRoot = temporaryDirectory.resolve("needs-decision-reader-archive");
    Path storeRoot = temporaryDirectory.resolve("needs-decision-reader-store");
    SavedSourcePreparation saved =
        publish(policies, fixture, new HashMap<>(), archiveRoot, storeRoot, runId('d'));

    assertThat(saved.assessment().readiness()).isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    assertThat(saved.sourceVersionReference()).isNotNull();
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      PreparedSourceArchive archive = new PreparedSourceArchive(archiveRoot);
      SourcePreparationReader preparations = new SourcePreparationReader(modules, steps, archive);

      assertThatThrownBy(
              () ->
                  new PreparedVerifiedSourceTextReader(preparations, archive)
                      .reopen(
                          new VerifiedSourceInventoryReference(
                              saved.sourceVersionReference().publication())))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("PREPARED_SOURCE_TEXT_REOPEN_INVALID");
    }
  }

  private SavedSourcePreparation publish(
      CanonicalArtifactPolicyRegistry policies,
      SourceFixture fixture,
      Map<String, Integer> stagedOpens,
      Path archiveRoot,
      Path storeRoot,
      AnalysisRunId runId)
      throws IOException {
    Files.createDirectory(storeRoot);
    PreparedSourceArchive archive = new PreparedSourceArchive(archiveRoot);
    CapturedSourcePreparation capture =
        new CapturedSourcePreparation(
            fixture.request(),
            fixture.result(),
            entry -> {
              stagedOpens.merge(entry.relativePath(), 1, Integer::sum);
              byte[] bytes = fixture.acceptedBytes().get(entry.relativePath());
              if (bytes == null) {
                throw new IOException("No accepted staged bytes for " + entry.relativePath());
              }
              return new ByteArrayInputStream(bytes);
            },
            toolIdentity());

    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      return new SourcePreparationPublisher(modules, steps, archive, policies)
          .publish(runId, capture);
    }
  }

  private static ReopenedAnalysisStepPublication reopenRaw(
      CanonicalArtifactPolicyRegistry policies, Path storeRoot, SavedSourcePreparation saved) {
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      return steps.reopen(saved.reportReference());
    }
  }

  private static SavedSourcePreparation reopenTyped(
      CanonicalArtifactPolicyRegistry policies,
      Path archiveRoot,
      Path storeRoot,
      SavedSourcePreparation saved) {
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      return new SourcePreparationReader(modules, steps, new PreparedSourceArchive(archiveRoot))
          .reopen(saved.reportReference());
    }
  }

  private SourceFixture sourceFixture(
      String suffix,
      List<SourceEntry> entries,
      List<SourceIssue> issues,
      Map<String, byte[]> acceptedBytes)
      throws IOException {
    Path sourceRoot = temporaryDirectory.resolve("customer-source-" + suffix);
    Path changedFile =
        sourceRoot.resolve(
            entries.stream()
                .filter(entry -> entry.disposition() == SourceEntry.Disposition.VERIFIED_TEXT)
                .findFirst()
                .orElseThrow()
                .relativePath());
    Files.createDirectories(changedFile.getParent());
    Files.writeString(changedFile, "current customer file changed after inspection\n");

    ArtifactReference policyRef = policyRef(policies());
    SourcePreparationRequest request =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            new DirectorySourceOrigin("fixture.invalid/" + suffix, sourceRoot.toRealPath()),
            null,
            List.of(),
            List.of(),
            List.of(),
            new SourcePreparationLimits(20, 1_000_000L),
            policyRef);
    SourcePreparationResult result =
        new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.COMPLETED,
            true,
            entries,
            issues,
            List.of(),
            List.of());
    return new SourceFixture(request.origin(), request, result, acceptedBytes);
  }

  private static SourceEntry verifiedEntry(
      String path, byte[] bytes, SourceEntry.Disposition disposition) {
    Sha256Digest sha256 = digest(bytes);
    SourceOriginAttributes attributes =
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null);
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
        null,
        null);
  }

  private static SourceEntry unavailableEntry(String path, String issueId) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.REGULAR_FILE,
        SourceEntry.Disposition.UNAVAILABLE,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(issueId),
        null,
        null);
  }

  private CanonicalArtifactPolicyRegistry policies() {
    return SourcePreparationPolicyFixture.load(
        Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
            .toAbsolutePath(),
        JSON);
  }

  private static ArtifactReference policyRef(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256());
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

  private static void assertReceiptAndPayloads(
      ReopenedAnalysisStepPublication publication,
      ModuleCompletionStatus status,
      List<String> gapRefs) {
    List<String> expectedNames =
        List.of(
            "source-input.json",
            "source-inventory.jsonl",
            "source-issues.jsonl",
            "source-preparation-result.json");
    assertThat(publication.receipt().status()).isEqualTo(status);
    assertThat(publication.receipt().gapRefs()).containsExactlyElementsOf(gapRefs);
    assertThat(publication.receipt().semanticArtifacts())
        .extracting(descriptor -> descriptor.fileName())
        .containsExactlyInAnyOrderElementsOf(expectedNames);
    assertThat(publication.semanticPayloads())
        .extracting(payload -> payload.descriptor().fileName())
        .containsExactlyInAnyOrderElementsOf(expectedNames);
    assertThat(publication.receipt().semanticArtifacts())
        .containsExactlyInAnyOrderElementsOf(
            publication.semanticPayloads().stream()
                .map(VerifiedCanonicalPayload::descriptor)
                .toList());
    for (VerifiedCanonicalPayload payload : publication.semanticPayloads()) {
      byte[] bytes = payload.canonicalUtf8().copyToByteArray();
      assertThat(payload.descriptor().sizeBytes()).isEqualTo(bytes.length);
      assertThat(payload.descriptor().sha256()).isEqualTo(digest(bytes));
    }
  }

  private static void assertInventoryHasNoLineIndexes(ReopenedAnalysisStepPublication publication) {
    String inventory =
        new String(
            payload(publication, "source-inventory.jsonl").canonicalUtf8().copyToByteArray(),
            StandardCharsets.UTF_8);
    for (String line : inventory.split("\\n")) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode entry =
          JSON.parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
      assertThat(entry.has("lineStartByteOffsets")).isFalse();
      assertThat(entry.has("lineIndexDigest")).isFalse();
    }
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
                JSON.parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
        .toList();
  }

  private static void assertPublicPayloadsDoNotContainSourceRoot(
      ReopenedAnalysisStepPublication publication, Path sourceRoot) {
    String localPath = sourceRoot.toString();
    for (VerifiedCanonicalPayload payload : publication.semanticPayloads()) {
      assertThat(new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8))
          .doesNotContain(localPath);
    }
  }

  private static VerifiedCanonicalPayload payload(
      ReopenedAnalysisStepPublication publication, String fileName) {
    return publication.semanticPayloads().stream()
        .filter(candidate -> candidate.descriptor().fileName().equals(fileName))
        .findFirst()
        .orElseThrow();
  }

  private static Sha256Digest digest(byte[] bytes) {
    try {
      return Sha256Digest.parse(
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private record SourceFixture(
      SourceOrigin origin,
      SourcePreparationRequest request,
      SourcePreparationResult result,
      Map<String, byte[]> acceptedBytes) {}
}
