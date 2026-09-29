package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Direct classification contract for complete facts from one verified source inventory. */
class VerifiedSourceFileActivationRangeTest {

  @TempDir Path temporaryDirectory;

  private static final String SOURCE_VERSION_ID = "snapshot:" + "a".repeat(64);
  private static final ArtifactReference SOURCE_INVENTORY_REF =
      new ArtifactReference(
          ArtifactId.parse("source-inventory:" + "b".repeat(64)),
          Sha256Digest.parse("c".repeat(64)));

  @Test
  void verifiedMediaAndEnumeratedDirectoriesArePresent() {
    VerifiedSourceFileActivationRange range =
        range(
            true,
            List.of(verifiedMedia("assets/logo.bin"), enumeratedDirectory("src/generated")),
            List.of(),
            List.of(),
            List.of());

    assertClassification(range, "assets/logo.bin", "PRESENT");
    assertClassification(range, "src/generated", "PRESENT");
  }

  @Test
  void onlyCompleteEnumerationCanConfirmAnAbsentPath() {
    VerifiedSourceFileActivationRange complete =
        range(true, List.of(enumeratedDirectory("src")), List.of(), List.of(), List.of());
    VerifiedSourceFileActivationRange bounded =
        range(
            false,
            List.of(unavailableDirectory("vendor/unknown", "unknown-vendor")),
            List.of(directoryIssue("unknown-vendor", "vendor/unknown")),
            List.of("vendor/unknown"),
            List.of());

    assertClassification(complete, "src/never-present.xml", "ABSENT_CONFIRMED");
    assertClassification(bounded, "src/never-seen.xml", "UNKNOWN");
  }

  @Test
  void matchedAndUnmatchedFileOrDirectoryExclusionsCoverTheirScope() {
    VerifiedSourceFileActivationRange range =
        range(
            true,
            List.of(
                excluded("private/passwords.txt", SourceEntry.Kind.REGULAR_FILE),
                excluded("archive/retained", SourceEntry.Kind.DIRECTORY),
                excluded(
                    "archive/retained/old.txt", SourceEntry.Kind.REGULAR_FILE, "archive/retained"),
                excluded("vendor/a.jar", SourceEntry.Kind.REGULAR_FILE, "vendor")),
            List.of(),
            List.of(),
            List.of(
                new SourcePreparationTarget(
                    "notes/not-captured.txt", SourcePreparationTarget.Kind.FILE),
                new SourcePreparationTarget(
                    "unmatched-vendor", SourcePreparationTarget.Kind.DIRECTORY)));

    assertClassification(range, "private/passwords.txt", "EXCLUDED");
    assertClassification(range, "private/passwords.txt/child", "ABSENT_CONFIRMED");
    assertClassification(range, "archive/retained/old.txt", "EXCLUDED");
    assertClassification(range, "archive/retained/other.txt", "EXCLUDED");
    assertClassification(range, "vendor/b.jar", "EXCLUDED");
    assertClassification(range, "notes/not-captured.txt", "EXCLUDED");
    assertClassification(range, "unmatched-vendor", "EXCLUDED");
    assertClassification(range, "unmatched-vendor/lib.jar", "EXCLUDED");
  }

  @Test
  void unknownSubtreesRemainUnknownAtTheirRootAndBelow() {
    VerifiedSourceFileActivationRange range =
        range(
            false,
            List.of(unavailableUnknownDirectory("src/unreadable", "unreadable-src")),
            List.of(directoryIssue("unreadable-src", "src/unreadable")),
            List.of("src/unreadable"),
            List.of());

    assertClassification(range, "src/unreadable", "UNKNOWN");
    assertClassification(range, "src/unreadable/Generated.java", "UNKNOWN");
  }

  @Test
  void unresolvedDirectoriesAndUnfollowedSymlinksStayUnknownEvenAfterEnumerationCompletes() {
    VerifiedSourceFileActivationRange range =
        range(
            true,
            List.of(
                skippedSymlink("links/external"),
                unavailableDirectory("src/unavailable", "unavailable-src"),
                unsupportedDirectory("src/unsupported", "unsupported-src"),
                uncheckedDirectory("src/unchecked", "unchecked-src")),
            List.of(
                directoryIssue("unavailable-src", "src/unavailable"),
                directoryIssue("unsupported-src", "src/unsupported"),
                directoryIssue("unchecked-src", "src/unchecked")),
            List.of(),
            List.of());

    for (String path :
        List.of(
            "links/external",
            "links/external/Main.java",
            "src/unavailable",
            "src/unavailable/Main.java",
            "src/unsupported",
            "src/unsupported/Main.java",
            "src/unchecked",
            "src/unchecked/Main.java")) {
      assertClassification(range, path, "UNKNOWN");
    }
  }

  @Test
  void rangeRetainsTheSourceVersionAndInventoryReferenceItWasGiven() {
    VerifiedSourceFileActivationRange range =
        range(true, List.of(enumeratedDirectory("src")), List.of(), List.of(), List.of());

    assertThat(range.sourceVersionId()).isEqualTo(SOURCE_VERSION_ID);
    assertThat(range.sourceInventoryRef()).isEqualTo(SOURCE_INVENTORY_REF);
  }

  @Test
  void freshReaderReopensTheSavedR0InventoryAndReturnsItsBoundIdentity() throws IOException {
    VerifiedSourceFileActivationRangeR0Fixture.PublishedSource published =
        VerifiedSourceFileActivationRangeR0Fixture.publish(
            temporaryDirectory, "full-r0", "<project>owned</project>\n", "pom-file-key");
    VerifiedSourceTextSet texts = published.reopenTexts();

    VerifiedSourceFileActivationRange range = published.reopenRange(texts);

    assertThat(range.sourceVersionId()).isEqualTo(texts.snapshotId());
    assertThat(range.sourceVersionId()).isEqualTo(published.sourceVersionId());
    assertThat(range.sourceInventoryRef()).isEqualTo(texts.sourceInventoryRef());
    assertThat(range.sourceInventoryRef()).isEqualTo(published.sourceInventoryRef());
    assertClassification(range, "pom.xml", "PRESENT");
    assertClassification(range, "assets/logo.bin", "PRESENT");
    assertClassification(range, "assets", "PRESENT");
    assertClassification(range, "src/not-captured/activation.xml", "ABSENT_CONFIRMED");
  }

  @Test
  void freshReaderRejectsTextProjectionFromAnotherRealSourceVersion() throws IOException {
    VerifiedSourceFileActivationRangeR0Fixture.PublishedSource first =
        VerifiedSourceFileActivationRangeR0Fixture.publish(
            temporaryDirectory, "version-a", "<project>version-a</project>\n", "pom-file-key");
    VerifiedSourceFileActivationRangeR0Fixture.PublishedSource second =
        VerifiedSourceFileActivationRangeR0Fixture.publish(
            temporaryDirectory, "version-b", "<project>version-b</project>\n", "pom-file-key");

    assertThat(first.sourceVersionId()).isNotEqualTo(second.sourceVersionId());
    assertThat(first.sourceInventoryRef()).isNotEqualTo(second.sourceInventoryRef());
    VerifiedSourceTextSet firstTexts = first.reopenTexts();

    assertThatThrownBy(() -> second.reopenRange(firstTexts))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PREPARED_SOURCE_FILE_ACTIVATION_RANGE_REOPEN_INVALID");
  }

  @Test
  void freshReaderRejectsDifferentInventoryReferenceEvenForTheSameSourceVersion()
      throws IOException {
    VerifiedSourceFileActivationRangeR0Fixture.PublishedSource first =
        VerifiedSourceFileActivationRangeR0Fixture.publish(
            temporaryDirectory, "inventory-a", "<project>same</project>\n", "first-file-key");
    VerifiedSourceFileActivationRangeR0Fixture.PublishedSource second =
        VerifiedSourceFileActivationRangeR0Fixture.publish(
            temporaryDirectory, "inventory-b", "<project>same</project>\n", "second-file-key");

    assertThat(first.sourceVersionId()).isEqualTo(second.sourceVersionId());
    assertThat(first.sourceInventoryRef()).isNotEqualTo(second.sourceInventoryRef());
    VerifiedSourceTextSet firstTexts = first.reopenTexts();

    assertThatThrownBy(() -> second.reopenRange(firstTexts))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PREPARED_SOURCE_FILE_ACTIVATION_RANGE_REOPEN_INVALID");
  }

  private static VerifiedSourceFileActivationRange range(
      boolean enumerationComplete,
      List<SourceEntry> entries,
      List<SourceIssue> issues,
      List<String> unknownSubtrees,
      List<SourcePreparationTarget> unmatchedExclusions) {
    SourcePreparationResult result =
        new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.COMPLETED,
            enumerationComplete,
            entries,
            issues,
            unknownSubtrees,
            unmatchedExclusions);
    return new VerifiedSourceFileActivationRange(SOURCE_VERSION_ID, SOURCE_INVENTORY_REF, result);
  }

  private static void assertClassification(
      VerifiedSourceFileActivationRange range, String path, String expected) {
    assertThat(range.classify(path).toString()).isEqualTo(expected);
  }

  private static SourceEntry verifiedMedia(String path) {
    byte[] bytes = new byte[] {0, 1, 2, 3, 4};
    Sha256Digest digest = digest(bytes);
    SourceOriginAttributes attributes = directoryAttributes();
    ArtifactReference blob =
        new ArtifactReference(ArtifactId.parse("source-blob:" + digest.value()), digest);
    ArtifactId fileId = SourceVersionCalculator.fileId(path, bytes.length, digest, attributes);
    SourceObservation observation =
        new SourceObservation((long) bytes.length, digest, fileId, null, "fixture:" + path);
    return new SourceEntry(
        path,
        SourceEntry.Kind.REGULAR_FILE,
        SourceEntry.Disposition.VERIFIED_MEDIA,
        (long) bytes.length,
        digest,
        blob,
        fileId,
        null,
        attributes,
        new SourceEntryObservations(observation, observation),
        List.of(),
        null,
        null);
  }

  private static SourceEntry enumeratedDirectory(String path) {
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

  private static SourceEntry excluded(String path, SourceEntry.Kind kind) {
    return excluded(path, kind, path);
  }

  private static SourceEntry excluded(String path, SourceEntry.Kind kind, String coveredPath) {
    return new SourceEntry(
        path,
        kind,
        SourceEntry.Disposition.EXCLUDED_BY_USER,
        null,
        null,
        null,
        null,
        null,
        directoryAttributes(),
        null,
        List.of(),
        null,
        new SourceEntryExclusion("USER_REQUEST", SourcePreparationOperation.EXCLUDE, coveredPath));
  }

  private static SourceEntry skippedSymlink(String path) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.SYMLINK,
        SourceEntry.Disposition.SKIPPED_SYMLINK,
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

  private static SourceEntry unavailableDirectory(String path, String issueId) {
    return unresolvedDirectory(path, SourceEntry.Disposition.UNAVAILABLE, issueId);
  }

  private static SourceEntry unsupportedDirectory(String path, String issueId) {
    return unresolvedDirectory(path, SourceEntry.Disposition.UNSUPPORTED, issueId);
  }

  private static SourceEntry uncheckedDirectory(String path, String issueId) {
    return unresolvedDirectory(path, SourceEntry.Disposition.UNCHECKED, issueId);
  }

  private static SourceEntry unavailableUnknownDirectory(String path, String issueId) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.UNKNOWN,
        SourceEntry.Disposition.UNAVAILABLE,
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
  }

  private static SourceEntry unresolvedDirectory(
      String path, SourceEntry.Disposition disposition, String issueId) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.DIRECTORY,
        disposition,
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
  }

  private static SourceIssue directoryIssue(String issueId, String path) {
    return new SourceIssue(
        issueId,
        SourceIssue.Code.SOURCE_DIRECTORY_LIST_FAILED,
        SourceIssue.Category.ACCESS,
        SourceIssue.Scope.DIRECTORY,
        path,
        SourceIssue.Operation.LIST_DIRECTORY,
        "Directory contents were not established.",
        null,
        null,
        SourceIssue.Resolution.OPEN,
        Set.of(
            SourceIssue.AllowedAction.REFRESH_DIRECTORY,
            SourceIssue.AllowedAction.EXCLUDE_DIRECTORY),
        null);
  }

  private static SourceOriginAttributes directoryAttributes() {
    return new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null);
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
