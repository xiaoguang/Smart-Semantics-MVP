package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Direct behavior tests for immutable source-preparation result assessment. */
class SourcePreparationContractsTest {

  @Test
  void keepsAllOneHundredEntriesAndNeedsDecisionWhenOneReadFails() {
    List<SourceEntry> entries = verifiedTextEntries(99);
    entries.add(unavailableEntry("src/unreadable.java", "issue-unreadable"));
    SourceIssue issue =
        issue(
            "issue-unreadable",
            SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
            SourceIssue.Category.ACCESS,
            SourceIssue.Scope.FILE,
            "src/unreadable.java",
            SourceIssue.Operation.READ_INPUT,
            SourceIssue.Resolution.OPEN);

    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                COMPLETED, true, entries, List.of(issue), List.of(), List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    assertThat(assessment.summary().discoveredRegularFiles()).isEqualTo(100);
    assertThat(assessment.summary().verifiedTextFiles()).isEqualTo(99);
    assertThat(assessment.summary().unavailableKnownFiles()).isEqualTo(1);
    assertThat(assessment.summary().totalRegularFiles()).isEqualTo(100);
    assertThat(assessment.summary().enumerationComplete()).isTrue();
  }

  @Test
  void keepsTotalUnknownWhenDirectoryEnumerationLeavesANamedSubtreeUnresolved() {
    List<SourceEntry> entries = verifiedTextEntries(99);
    entries.add(enumeratedDirectoryEntry("src/unreadable-subtree"));
    SourceIssue issue =
        issue(
            "issue-subtree",
            SourceIssue.Code.SOURCE_DIRECTORY_LIST_FAILED,
            SourceIssue.Category.ACCESS,
            SourceIssue.Scope.DIRECTORY,
            "src/unreadable-subtree",
            SourceIssue.Operation.LIST_DIRECTORY,
            SourceIssue.Resolution.OPEN);

    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                COMPLETED,
                false,
                entries,
                List.of(issue),
                List.of("src/unreadable-subtree"),
                List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    assertThat(assessment.summary().verifiedTextFiles()).isEqualTo(99);
    assertThat(assessment.summary().totalRegularFiles()).isNull();
    assertThat(assessment.summary().enumerationComplete()).isFalse();
    assertThat(assessment.summary().unknownSubtrees()).containsExactly("src/unreadable-subtree");
  }

  @Test
  void permitsTheKnownTextRangeAfterAResolvedExplicitExclusion() {
    List<SourceEntry> entries = new ArrayList<>(List.of(textEntry("src/Ready.java")));
    entries.add(excludedEntry("docs/legacy.pdf", "issue-excluded"));
    SourceIssue issue =
        issue(
            "issue-excluded",
            SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
            SourceIssue.Category.ACCESS,
            SourceIssue.Scope.FILE,
            "docs/legacy.pdf",
            SourceIssue.Operation.READ_INPUT,
            SourceIssue.Resolution.RESOLVED_BY_EXCLUSION);

    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                COMPLETED, true, entries, List.of(issue), List.of(), List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
    assertThat(assessment.summary().discoveredRegularFiles()).isEqualTo(2);
    assertThat(assessment.summary().verifiedTextFiles()).isEqualTo(1);
    assertThat(assessment.summary().excludedKnownFiles()).isEqualTo(1);
    assertThat(assessment.summary().totalRegularFiles()).isEqualTo(2);
  }

  @Test
  void allowsAnExplicitlyExcludedUnknownSubtreeButDoesNotInventItsTotal() {
    List<SourceEntry> entries = new ArrayList<>(List.of(textEntry("src/Ready.java")));
    entries.add(excludedDirectoryEntry("vendor/ignored", "issue-vendor-excluded"));
    SourceIssue issue =
        issue(
            "issue-vendor-excluded",
            SourceIssue.Code.SOURCE_DIRECTORY_LIST_FAILED,
            SourceIssue.Category.ACCESS,
            SourceIssue.Scope.DIRECTORY,
            "vendor/ignored",
            SourceIssue.Operation.LIST_DIRECTORY,
            SourceIssue.Resolution.RESOLVED_BY_EXCLUSION);

    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                COMPLETED, false, entries, List.of(issue), List.of("vendor/ignored"), List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
    assertThat(assessment.summary().totalRegularFiles()).isNull();
    assertThat(assessment.summary().enumerationComplete()).isFalse();
    assertThat(assessment.summary().unknownSubtrees()).containsExactly("vendor/ignored");
  }

  @Test
  void doesNotTurnAResolvedRefreshObservationIntoAnExclusion() {
    SourceIssue issue =
        issue(
            "issue-refreshed",
            SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
            SourceIssue.Category.ACCESS,
            SourceIssue.Scope.FILE,
            "src/Ready.java",
            SourceIssue.Operation.READ_INPUT,
            SourceIssue.Resolution.RESOLVED_BY_REFRESH);

    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                COMPLETED,
                true,
                List.of(textEntry("src/Ready.java")),
                List.of(issue),
                List.of(),
                List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.READY);
  }

  @Test
  void reportsNoAnalyzableTextForMediaOnlyAndFullyExcludedRanges() {
    SourcePreparationAssessment mediaOnly =
        assess(
            new SourcePreparationResult(
                COMPLETED,
                true,
                List.of(mediaEntry("static/logo.png")),
                List.of(
                    issue(
                        "issue-no-text",
                        SourceIssue.Code.SOURCE_NO_ANALYZABLE_TEXT,
                        SourceIssue.Category.UNSUPPORTED,
                        SourceIssue.Scope.REQUEST,
                        null,
                        SourceIssue.Operation.READ_INPUT,
                        SourceIssue.Resolution.INFORMATIONAL)),
                List.of(),
                List.of()));
    SourcePreparationAssessment allExcluded =
        assess(
            new SourcePreparationResult(
                COMPLETED,
                true,
                List.of(excludedEntry("docs/manual.pdf", "issue-all-excluded")),
                List.of(
                    issue(
                        "issue-all-excluded",
                        SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
                        SourceIssue.Category.ACCESS,
                        SourceIssue.Scope.FILE,
                        "docs/manual.pdf",
                        SourceIssue.Operation.READ_INPUT,
                        SourceIssue.Resolution.RESOLVED_BY_EXCLUSION)),
                List.of(),
                List.of()));

    assertThat(mediaOnly.readiness()).isEqualTo(SourcePreparationReadiness.NO_ANALYZABLE_TEXT);
    assertThat(mediaOnly.summary().verifiedMediaFiles()).isEqualTo(1);
    assertThat(mediaOnly.summary().verifiedTextFiles()).isZero();
    assertThat(allExcluded.readiness()).isEqualTo(SourcePreparationReadiness.NO_ANALYZABLE_TEXT);
    assertThat(allExcluded.summary().excludedKnownFiles()).isEqualTo(1);
    assertThat(allExcluded.summary().verifiedTextFiles()).isZero();
  }

  @Test
  void resourceLimitAbortRemainsNeedsDecisionRatherThanBecomingBlocked() {
    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                SourcePreparationResult.InspectionStatus.ABORTED,
                false,
                List.of(textEntry("src/Ready.java"), enumeratedDirectoryEntry("src/rest")),
                List.of(
                    issue(
                        "issue-limit",
                        SourceIssue.Code.VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED,
                        SourceIssue.Category.RESOURCE_LIMIT,
                        SourceIssue.Scope.DIRECTORY,
                        "src/rest",
                        SourceIssue.Operation.READ_INPUT,
                        SourceIssue.Resolution.OPEN)),
                List.of("src/rest"),
                List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    assertThat(assessment.summary().totalRegularFiles()).isNull();
  }

  @ParameterizedTest
  @MethodSource("nonExcludableFailures")
  void doesNotTurnRequestIdentityRootOutputOrInternalFailureIntoUsableText(SourceIssue issue) {
    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                COMPLETED,
                true,
                List.of(textEntry("src/Ready.java")),
                List.of(issue),
                List.of(),
                List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.BLOCKED);
    assertThat(assessment.summary().verifiedTextFiles()).isEqualTo(1);
  }

  @Test
  void resultAndAssessmentCollectionsAreDefensivelyImmutable() {
    List<SourceEntry> entries = new ArrayList<>(List.of(textEntry("src/Ready.java")));
    List<SourceIssue> issues = new ArrayList<>();
    List<String> unknownSubtrees = new ArrayList<>();
    SourcePreparationTarget unmatched =
        new SourcePreparationTarget("docs/not-present", SourcePreparationTarget.Kind.DIRECTORY);
    List<SourcePreparationTarget> unmatchedExclusions = new ArrayList<>(List.of(unmatched));
    SourcePreparationResult result =
        new SourcePreparationResult(
            COMPLETED, true, entries, issues, unknownSubtrees, unmatchedExclusions);

    entries.add(textEntry("src/NotInResult.java"));
    unknownSubtrees.add("src/not-in-result");
    unmatchedExclusions.add(
        new SourcePreparationTarget("vendor/not-present", SourcePreparationTarget.Kind.DIRECTORY));

    assertThat(result.entries()).hasSize(1);
    assertThat(result.unknownSubtrees()).isEmpty();
    assertThat(result.unmatchedExclusions()).containsExactly(unmatched);
    assertThatThrownBy(() -> result.entries().add(textEntry("src/blocked.java")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.unknownSubtrees().add("src/blocked"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                result
                    .unmatchedExclusions()
                    .add(
                        new SourcePreparationTarget(
                            "src/blocked", SourcePreparationTarget.Kind.DIRECTORY)))
        .isInstanceOf(UnsupportedOperationException.class);

    SourcePreparationAssessment assessment = assess(result);
    assertThatThrownBy(
            () -> assessment.summary().unknownSubtrees().add("src/another-blocked-subtree"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                assessment
                    .summary()
                    .unmatchedExclusions()
                    .add(
                        new SourcePreparationTarget(
                            "src/another-blocked", SourcePreparationTarget.Kind.DIRECTORY)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void unaccountedUnavailableUncheckedAndUnsupportedEntriesCannotBecomeReadyWithoutIssues() {
    for (SourceEntry entry :
        List.of(
            unaccountedEntry("src/unreadable.java", SourceEntry.Disposition.UNAVAILABLE),
            unaccountedEntry("src/not-checked.java", SourceEntry.Disposition.UNCHECKED),
            unaccountedEntry("vendor/module", SourceEntry.Disposition.UNSUPPORTED))) {
      SourcePreparationAssessment assessment =
          assess(
              new SourcePreparationResult(
                  COMPLETED,
                  true,
                  List.of(textEntry("src/Ready.java"), entry),
                  List.of(),
                  List.of(),
                  List.of()));

      assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    }
  }

  @Test
  void initiallyExcludedUnknownDirectoryWithInformationalIssueIsReadyWithExclusions() {
    String issueId = "issue-vendor-initially-excluded";
    SourceIssue issue =
        issue(
            issueId,
            SourceIssue.Code.UNSUPPORTED_ENTRY,
            SourceIssue.Category.UNSUPPORTED,
            SourceIssue.Scope.DIRECTORY,
            "vendor/ignored",
            SourceIssue.Operation.LIST_DIRECTORY,
            SourceIssue.Resolution.INFORMATIONAL);

    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                COMPLETED,
                false,
                List.of(
                    textEntry("src/Ready.java"), excludedDirectoryEntry("vendor/ignored", issueId)),
                List.of(issue),
                List.of("vendor/ignored"),
                List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
    assertThat(assessment.summary().totalRegularFiles()).isNull();
  }

  @Test
  void incompleteEnumerationWithoutNamedUnknownOrOpenIssueCannotClaimReady() {
    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                COMPLETED,
                false,
                List.of(textEntry("src/Ready.java")),
                List.of(),
                List.of(),
                List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    assertThat(assessment.summary().totalRegularFiles()).isNull();
  }

  @Test
  void inheritanceMayCarryOnlyABaseVersionWhenThereIsNoFileIdentity() {
    SourceEntryInheritance inheritance =
        new SourceEntryInheritance(ArtifactId.parse("snapshot:" + "a".repeat(64)), null);

    assertThat(inheritance.baseSourceVersion().value()).startsWith("snapshot:");
    assertThat(inheritance.fileId()).isNull();
  }

  @Test
  void treatsGitMetadataRegularFilesAsPolicyExcludedWithoutReadingTheirBytes() {
    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                COMPLETED,
                true,
                List.of(textEntry("src/Ready.java"), skippedGitMetadataEntry(".git")),
                List.of(),
                List.of(),
                List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
    assertThat(assessment.summary().discoveredRegularFiles()).isEqualTo(2);
    assertThat(assessment.summary().verifiedTextFiles()).isEqualTo(1);
    assertThat(assessment.summary().excludedKnownFiles()).isEqualTo(1);
    assertThat(assessment.summary().totalRegularFiles()).isEqualTo(2);
  }

  @Test
  void treatsGitMetadataDirectoryAsPolicyExcludedWhileItsPhysicalTotalRemainsUnknown() {
    SourcePreparationAssessment assessment =
        assess(
            new SourcePreparationResult(
                COMPLETED,
                false,
                List.of(textEntry("src/Ready.java"), skippedGitMetadataDirectoryEntry(".git")),
                List.of(),
                List.of(".git"),
                List.of()));

    assertThat(assessment.readiness()).isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
    assertThat(assessment.summary().directoryEntries()).isEqualTo(1);
    assertThat(assessment.summary().totalRegularFiles()).isNull();
    assertThat(assessment.summary().unknownSubtrees()).containsExactly(".git");
  }

  @Test
  void abortedInspectionCannotClaimReadyEvenWhenItsEnumerationFlagIsTrue() {
    try {
      SourcePreparationResult result =
          new SourcePreparationResult(
              SourcePreparationResult.InspectionStatus.ABORTED,
              true,
              List.of(textEntry("src/Ready.java")),
              List.of(),
              List.of(),
              List.of());
      assertThat(assess(result).readiness()).isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    } catch (IllegalArgumentException expected) {
      // Implementations may reject the inconsistent raw fact at construction time.
    }
  }

  @Test
  void reportsNonRegularEntryDimensionsAndUnmatchedDeclarationsWithoutInventingFiles() {
    SourcePreparationTarget unmatched =
        new SourcePreparationTarget("docs/not-present", SourcePreparationTarget.Kind.DIRECTORY);
    SourcePreparationResult result =
        new SourcePreparationResult(
            COMPLETED,
            true,
            List.of(
                textEntry("src/Ready.java"),
                enumeratedDirectoryEntry("src"),
                skippedSymlinkEntry("src/link"),
                unsupportedSubmoduleEntry("vendor/module")),
            List.of(),
            List.of(),
            List.of(unmatched));

    SourcePreparationAssessment assessment = assess(result);

    assertThat(assessment.summary().directoryEntries()).isEqualTo(1);
    assertThat(assessment.summary().symlinkEntries()).isEqualTo(1);
    assertThat(assessment.summary().submoduleEntries()).isEqualTo(1);
    assertThat(assessment.summary().unmatchedExclusions()).containsExactly(unmatched);
    assertThat(result.unmatchedExclusions()).containsExactly(unmatched);
    assertThat(assessment.summary().discoveredRegularFiles()).isEqualTo(1);
  }

  @Test
  void rejectsShellBracketPatternsButAllowsLiteralBracketInventoryNames() {
    for (String bracketPattern : List.of("src/[AB].java", "src/[0-9].java")) {
      assertThatThrownBy(
              () -> new SourcePreparationTarget(bracketPattern, SourcePreparationTarget.Kind.FILE))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatCode(
            () -> new SourcePreparationTarget("literal[]", SourcePreparationTarget.Kind.FILE))
        .doesNotThrowAnyException();
  }

  @Test
  void excludedEntryCannotRetainTextEncoding() {
    byte[] bytes = "excluded bytes".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(
            () ->
                regularEntry(
                    "docs/legacy.txt",
                    SourceEntry.Disposition.EXCLUDED_BY_USER,
                    bytes,
                    "UTF-8",
                    List.of(),
                    new SourceEntryExclusion(
                        "USER_REQUEST", SourcePreparationOperation.EXCLUDE, "docs/legacy.txt")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Stream<Arguments> nonExcludableFailures() {
    return Stream.of(
        Arguments.of(
            issue(
                "issue-request",
                SourceIssue.Code.SOURCE_PATH_INVALID,
                SourceIssue.Category.REQUEST,
                SourceIssue.Scope.REQUEST,
                null,
                SourceIssue.Operation.VALIDATE_REQUEST,
                SourceIssue.Resolution.OPEN)),
        Arguments.of(
            issue(
                "issue-root",
                SourceIssue.Code.SOURCE_ROOT_LIST_FAILED,
                SourceIssue.Category.ACCESS,
                SourceIssue.Scope.ROOT,
                null,
                SourceIssue.Operation.LIST_DIRECTORY,
                SourceIssue.Resolution.OPEN)),
        Arguments.of(
            issue(
                "issue-identity",
                SourceIssue.Code.CAPTURE_IDENTITY_INVALID,
                SourceIssue.Category.SAVED_CONTENT_INTEGRITY,
                SourceIssue.Scope.ROOT,
                null,
                SourceIssue.Operation.VERIFY_IDENTITY,
                SourceIssue.Resolution.OPEN)),
        Arguments.of(
            issue(
                "issue-output",
                SourceIssue.Code.SOURCE_OUTPUT_FAILED,
                SourceIssue.Category.OUTPUT,
                SourceIssue.Scope.OUTPUT,
                null,
                SourceIssue.Operation.SAVE_OUTPUT,
                SourceIssue.Resolution.OPEN)),
        Arguments.of(
            issue(
                "issue-internal",
                SourceIssue.Code.SOURCE_PREPARATION_INTERNAL_ERROR,
                SourceIssue.Category.INTERNAL,
                SourceIssue.Scope.ROOT,
                null,
                SourceIssue.Operation.READ_INPUT,
                SourceIssue.Resolution.OPEN)));
  }

  private static SourcePreparationAssessment assess(SourcePreparationResult result) {
    return SourcePreparationReadinessEvaluator.assess(result);
  }

  private static SourceIssue issue(
      String issueId,
      SourceIssue.Code code,
      SourceIssue.Category category,
      SourceIssue.Scope scope,
      String relativePath,
      SourceIssue.Operation operation,
      SourceIssue.Resolution resolution) {
    Set<SourceIssue.AllowedAction> allowedActions =
        switch (resolution) {
          case OPEN ->
              switch (scope) {
                case FILE ->
                    Set.of(
                        SourceIssue.AllowedAction.REFRESH_FILE,
                        SourceIssue.AllowedAction.EXCLUDE_FILE);
                case DIRECTORY ->
                    Set.of(
                        SourceIssue.AllowedAction.REFRESH_DIRECTORY,
                        SourceIssue.AllowedAction.EXCLUDE_DIRECTORY);
                case REQUEST -> Set.of(SourceIssue.AllowedAction.FIX_CONFIGURATION);
                case ROOT -> Set.of(SourceIssue.AllowedAction.NEW_PREPARATION);
                case OUTPUT -> Set.of(SourceIssue.AllowedAction.FIX_OUTPUT);
              };
          case RESOLVED_BY_EXCLUSION, RESOLVED_BY_REFRESH, INFORMATIONAL -> Set.of();
        };
    return new SourceIssue(
        issueId,
        code,
        category,
        scope,
        relativePath,
        operation,
        code.name(),
        null,
        null,
        resolution,
        allowedActions,
        null);
  }

  private static List<SourceEntry> verifiedTextEntries(int count) {
    return IntStream.range(0, count)
        .mapToObj(index -> textEntry("src/File" + index + ".java"))
        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
  }

  private static SourceEntry textEntry(String path) {
    byte[] bytes =
        ("class Fixture { int value = " + path.length() + "; }\n").getBytes(StandardCharsets.UTF_8);
    return regularEntry(
        path, SourceEntry.Disposition.VERIFIED_TEXT, bytes, "UTF-8", List.of(), null);
  }

  private static SourceEntry mediaEntry(String path) {
    byte[] bytes = new byte[] {0, 1, 2, 3, 4};
    return regularEntry(path, SourceEntry.Disposition.VERIFIED_MEDIA, bytes, null, List.of(), null);
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

  private static SourceEntry excludedEntry(String path, String issueId) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.REGULAR_FILE,
        SourceEntry.Disposition.EXCLUDED_BY_USER,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(issueId),
        null,
        new SourceEntryExclusion("USER_REQUEST", SourcePreparationOperation.EXCLUDE, path));
  }

  private static SourceEntry enumeratedDirectoryEntry(String path) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.DIRECTORY,
        SourceEntry.Disposition.ENUMERATED_DIRECTORY,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(),
        null,
        null);
  }

  private static SourceEntry excludedDirectoryEntry(String path, String issueId) {
    return new SourceEntry(
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
        List.of(issueId),
        null,
        new SourceEntryExclusion("USER_REQUEST", SourcePreparationOperation.EXCLUDE, path));
  }

  private static SourceEntry unaccountedEntry(String path, SourceEntry.Disposition disposition) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.REGULAR_FILE,
        disposition,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(),
        null,
        null);
  }

  private static SourceEntry skippedGitMetadataEntry(String path) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.REGULAR_FILE,
        SourceEntry.Disposition.SKIPPED_GIT_METADATA,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(),
        null,
        null);
  }

  private static SourceEntry skippedGitMetadataDirectoryEntry(String path) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.DIRECTORY,
        SourceEntry.Disposition.SKIPPED_GIT_METADATA,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(),
        null,
        null);
  }

  private static SourceEntry skippedSymlinkEntry(String path) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.SYMLINK,
        SourceEntry.Disposition.SKIPPED_SYMLINK,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(),
        null,
        null);
  }

  private static SourceEntry unsupportedSubmoduleEntry(String path) {
    return new SourceEntry(
        path,
        SourceEntry.Kind.SUBMODULE,
        SourceEntry.Disposition.UNSUPPORTED,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of(),
        null,
        null);
  }

  private static SourceEntry regularEntry(
      String path,
      SourceEntry.Disposition disposition,
      byte[] bytes,
      String textEncoding,
      List<String> issueIds,
      SourceEntryExclusion exclusion) {
    String digest = sha256Hex(bytes);
    Sha256Digest sha256 = Sha256Digest.parse(digest);
    ArtifactReference blobRef =
        new ArtifactReference(ArtifactId.parse("source-blob:" + digest), sha256);
    SourceOriginAttributes originAttributes =
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null);
    ArtifactId fileId = sourceFileId(path, bytes.length, sha256, originAttributes);
    return new SourceEntry(
        path,
        SourceEntry.Kind.REGULAR_FILE,
        disposition,
        (long) bytes.length,
        sha256,
        blobRef,
        fileId,
        textEncoding,
        originAttributes,
        observations(path, bytes, sha256, fileId),
        issueIds,
        null,
        exclusion);
  }

  private static SourceEntryObservations observations(
      String path, byte[] bytes, Sha256Digest sha256, ArtifactId fileId) {
    SourceObservation observation =
        new SourceObservation((long) bytes.length, sha256, fileId, null, path);
    return new SourceEntryObservations(observation, observation);
  }

  private static ArtifactId sourceFileId(
      String path, int sizeBytes, Sha256Digest sha256, SourceOriginAttributes originAttributes) {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("format", "source-file-v2");
    identity.put("path", path);
    identity.put("entryKind", SourceEntry.Kind.REGULAR_FILE.name());
    identity.put("sizeBytes", sizeBytes);
    identity.put("sha256", sha256.value());
    ObjectNode origin = identity.putObject("originAttributes");
    origin.put("kind", originAttributes.kind().name());
    origin.putNull("gitMode");
    origin.putNull("gitBlobObjectId");
    origin.putNull("executable");
    String identityDigest =
        sha256Hex(new CanonicalJsonCodec().encodeCanonical(identity).copyToByteArray());
    return ArtifactId.parse("source-file:" + identityDigest);
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static final SourcePreparationResult.InspectionStatus COMPLETED =
      SourcePreparationResult.InspectionStatus.COMPLETED;
}
