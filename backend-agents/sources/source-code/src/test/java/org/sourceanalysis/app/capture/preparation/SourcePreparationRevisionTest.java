package org.sourceanalysis.app.capture.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.cli.SourcePreparationPolicyFixture;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.GitCommitSourceOrigin;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.PreparedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceEntryInheritance;
import org.sourceanalysis.app.analysis.inventory.SourceIssue;
import org.sourceanalysis.app.analysis.inventory.SourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationLimits;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationPublisher;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
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
import org.sourceanalysis.app.capture.localgit.FixedGitObjectAccess;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;

/** Direct service contracts for immutable source refresh and explicit exclusions. */
class SourcePreparationRevisionTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ArtifactStoreLimits STORE_LIMITS =
      new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 16);

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void usePhysicalTemporaryRoot() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void rejectsMismatchedCompleteBaseAndOriginRootBeforeSourceOrStagingAccess() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("preflight-source"));
    write(sourceRoot, "src/Base.java", "class Base {}\n");
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("private-output"), true)) {
      SavedSourcePreparation base = fixture.seedDirectory(sourceRoot);
      SourcePreparationTarget target = fileTarget("src/Base.java");
      PreparedSourceReference actualBase = base.sourceVersionReference();
      PreparedSourceReference mismatchedBase =
          new PreparedSourceReference(
              ArtifactId.parse("snapshot:" + "0".repeat(64)),
              actualBase.publication(),
              actualBase.schemaBundleRef(),
              actualBase.artifactPolicyRegistryRef());

      fixture.assertRejectedBeforeOriginAccess(
          fixture.request(
              SourcePreparationOperation.REFRESH,
              base.request().origin(),
              mismatchedBase,
              List.of(target),
              base.request().declaredExclusions(),
              base.request().effectiveExclusions()));

      Path otherRoot = Files.createDirectory(temporaryDirectory.resolve("different-source-root"));
      SourceOrigin differentRoot =
          new DirectorySourceOrigin(base.request().origin().logicalIdentity(), otherRoot);
      fixture.assertRejectedBeforeOriginAccess(
          fixture.request(
              SourcePreparationOperation.REFRESH,
              differentRoot,
              actualBase,
              List.of(target),
              base.request().declaredExclusions(),
              base.request().effectiveExclusions()));

      SourceOrigin differentIdentity =
          new DirectorySourceOrigin("fixture:other-logical-source", sourceRoot.toRealPath());
      fixture.assertRejectedBeforeOriginAccess(
          fixture.request(
              SourcePreparationOperation.REFRESH,
              differentIdentity,
              actualBase,
              List.of(target),
              base.request().declaredExclusions(),
              base.request().effectiveExclusions()));
    }
  }

  @Test
  void rejectsNewWhenSourceAndPrivateOutputOverlapBeforeCreatingOutputOrReadingSource()
      throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("overlap-source"));
    write(sourceRoot, "src/Main.java", "class Main {}\n");
    Path overlappingOutput = sourceRoot.resolve("private-output");
    try (RevisionFixture fixture = new RevisionFixture(overlappingOutput, false)) {
      SourcePreparationRequest request =
          fixture.request(
              SourcePreparationOperation.NEW,
              new DirectorySourceOrigin("fixture:overlap", sourceRoot.toRealPath()),
              null,
              List.of(),
              List.of(),
              List.of());

      assertThatThrownBy(() -> fixture.prepare(request)).isInstanceOf(Exception.class);

      fixture.assertNoOriginAccess();
      assertThat(fixture.staging.openCount()).hasValue(0);
      assertThat(Files.exists(overlappingOutput)).isFalse();
    }
  }

  @Test
  void rejectsCaptureWhenQueuedPreparationInputsDifferFromCapturedToolIdentity() throws Exception {
    String commit = "c".repeat(40);
    String blobId = "b".repeat(40);
    Path repository = Path.of("/private/fixture-queued-git-repository");
    GitCommitSourceOrigin origin =
        new GitCommitSourceOrigin("fixture:queued-git-source", repository, commit);
    byte[] sourceBytes = "class QueuedIdentity {}\n".getBytes(StandardCharsets.UTF_8);
    FixtureGitObjectAccess gitAccess =
        new FixtureGitObjectAccess(
            repository,
            commit,
            List.of(new FixedGitObjectAccess.TreeEntry("100644", "blob", blobId, "src/Main.java")),
            Map.of(blobId, sourceBytes));
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("queued-identity-output"), true)) {
      fixture.useGitObjectAccess(gitAccess);
      Path identityClasses = temporaryDirectory.resolve("queued-identity-classes");
      write(identityClasses, "org/sourceanalysis/app/Identity.class", new byte[] {0x11, 0x22});
      AtomicReference<String> measuredGitVersion = new AtomicReference<>("git version queued");
      AtomicInteger identityProbes = new AtomicInteger();
      SourcePreparationToolIdentityFactory identities =
          new SourcePreparationToolIdentityFactory(
              identityClasses,
              "Fixture Vendor",
              "Fixture Java",
              () -> {
                identityProbes.incrementAndGet();
                return measuredGitVersion.get();
              });
      SourcePreparationRequest request =
          fixture.request(
              SourcePreparationOperation.NEW, origin, null, List.of(), List.of(), List.of());
      SourcePreparationService service =
          new SourcePreparationService(
              fixture.privateOutputRoot,
              fixture.publisher,
              fixture.reader,
              fixture.archive,
              identities,
              fixture.directoryAccess,
              fixture.gitObjects,
              fixture.staging);
      PreparedSourceArchive.PreparationInputReferences queuedInputs =
          service.preparationInputs(request);
      AnalysisRunRequest runRequest =
          AnalysisRunRequest.sourcePreparation(
              queuedInputs.preparationRequestRef(),
              queuedInputs.policyRegistryRef(),
              queuedInputs.schemaBundleRef(),
              queuedInputs.resourceBudgetRef(),
              queuedInputs.preparationProfileRef(),
              queuedInputs.preparationToolchainRef());
      AnalysisRunReference queued =
          RunStoreBootstrap.queueAnalysisRun(fixture.storeHandle, runRequest);
      AnalysisRunRequest.SourcePreparationInputs savedInputs =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(fixture.storeHandle, queued.runId())
              .request()
              .sourcePreparationInputs();
      PreparedSourceArchive.PreparationInputReferences expectedFromPersistedRun =
          new PreparedSourceArchive.PreparationInputReferences(
              savedInputs.sourcePreparationRequestRef(),
              savedInputs.artifactPolicyRegistryRef(),
              savedInputs.schemaBundleRef(),
              savedInputs.resourceBudgetRef(),
              savedInputs.preparationProfileRef(),
              savedInputs.preparationToolchainRef());
      assertThat(expectedFromPersistedRun).isEqualTo(queuedInputs);
      assertThat(identityProbes).hasValue(1);
      measuredGitVersion.set("git version captured");
      List<Path> storeBefore = treePaths(fixture.storeRoot);
      List<Path> archiveBefore = treePaths(fixture.archiveRoot);

      assertThatThrownBy(() -> service.prepare(queued.runId(), request, expectedFromPersistedRun))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("SOURCE_PREPARATION_INPUTS_CHANGED");

      assertThat(identityProbes).hasValue(2);
      assertThat(fixture.gitObjectOpens).hasValue(1);
      assertThat(gitAccess.openedCommits()).containsExactly(commit);
      assertThat(fixture.staging.openCount()).hasValue(1);
      assertThat(treePaths(fixture.storeRoot)).containsExactlyElementsOf(storeBefore);
      assertThat(treePaths(fixture.archiveRoot)).containsExactlyElementsOf(archiveBefore);
      assertThat(RunStoreBootstrap.reopenAnalysisRunOutput(fixture.storeHandle, queued.runId()))
          .isEmpty();
    }
  }

  @Test
  void refreshesOnlyTheNamedFileAndKeepsTheSavedParentVersionImmutable() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("single-refresh-source"));
    byte[] targetBytes = "class Target {}\n".getBytes(StandardCharsets.UTF_8);
    write(sourceRoot, "src/Target.java", targetBytes);
    write(sourceRoot, "src/Other.java", "class Other { int version = 1; }\n");
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("private-output"), true)) {
      SavedSourcePreparation base = fixture.seedDirectory(sourceRoot);
      SourceEntry parentTarget = entry(base, "src/Target.java");
      SourceEntry parentOther = entry(base, "src/Other.java");

      write(sourceRoot, "src/Other.java", "class Other { int version = 2; }\n");
      SourcePreparationRequest refresh =
          fixture.request(
              SourcePreparationOperation.REFRESH,
              base.request().origin(),
              base.sourceVersionReference(),
              List.of(fileTarget("src/Target.java")),
              base.request().declaredExclusions(),
              base.request().effectiveExclusions());

      SavedSourcePreparation revised = fixture.prepare(refresh);

      assertThat(fixture.directoryAccess.openInputPaths())
          .containsExactly(sourceRoot.resolve("src/Target.java"));
      assertThat(fixture.staging.openCount()).hasValue(1);
      assertThat(revised.sourceVersionReference().sourceVersionId())
          .isNotEqualTo(base.sourceVersionReference().sourceVersionId());
      assertThat(entry(revised, "src/Target.java").sha256()).isEqualTo(parentTarget.sha256());
      SourceEntry inheritedOther = entry(revised, "src/Other.java");
      assertThat(inheritedOther.sha256()).isEqualTo(parentOther.sha256());
      assertThat(inheritedOther.inheritedFrom())
          .isEqualTo(
              new SourceEntryInheritance(
                  base.sourceVersionReference().sourceVersionId(), parentOther.fileId()));

      SavedSourcePreparation reopenedParent = fixture.reader.reopen(base.reportReference());
      assertThat(reopenedParent.reportReference()).isEqualTo(base.reportReference());
      assertThat(reopenedParent.result()).isEqualTo(base.result());
      assertThat(reopenedParent.sourceVersionReference()).isEqualTo(base.sourceVersionReference());
      try (InputStream frozenParentBytes =
          fixture.archive.open(base.sourceVersionReference().sourceVersionId(), parentOther)) {
        assertThat(frozenParentBytes.readAllBytes())
            .isEqualTo("class Other { int version = 1; }\n".getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  @Test
  void refreshingADirectoryReplacesItsSubtreeWithoutReopeningUntouchedEntries() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("directory-refresh-source"));
    write(sourceRoot, "src/pkg/Keep.java", "class Keep { int version = 1; }\n");
    write(sourceRoot, "src/pkg/Removed.java", "class Removed {}\n");
    write(sourceRoot, "src/outside/Outside.java", "class Outside {}\n");
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("private-output"), true)) {
      SavedSourcePreparation base = fixture.seedDirectory(sourceRoot);
      SourceEntry outside = entry(base, "src/outside/Outside.java");
      write(sourceRoot, "src/pkg/Keep.java", "class Keep { int version = 2; }\n");
      Files.delete(sourceRoot.resolve("src/pkg/Removed.java"));
      write(sourceRoot, "src/pkg/Added.java", "class Added {}\n");
      SourcePreparationRequest refresh =
          fixture.request(
              SourcePreparationOperation.REFRESH,
              base.request().origin(),
              base.sourceVersionReference(),
              List.of(directoryTarget("src/pkg")),
              base.request().declaredExclusions(),
              base.request().effectiveExclusions());

      SavedSourcePreparation revised = fixture.prepare(refresh);

      assertThat(paths(revised.result()))
          .contains(
              "src/pkg", "src/pkg/Keep.java", "src/pkg/Added.java", "src/outside/Outside.java")
          .doesNotContain("src/pkg/Removed.java");
      assertThat(fixture.directoryAccess.openInputPaths())
          .containsExactlyInAnyOrder(
              sourceRoot.resolve("src/pkg/Keep.java"), sourceRoot.resolve("src/pkg/Added.java"));
      assertThat(entry(revised, "src/outside/Outside.java").inheritedFrom().baseSourceVersion())
          .isEqualTo(base.sourceVersionReference().sourceVersionId());
      assertThat(entry(revised, "src/outside/Outside.java").sha256()).isEqualTo(outside.sha256());
    }
  }

  @Test
  void excludesKnownFilesAndDirectoriesOfflineAndRefreshRetainsCumulativeExclusions()
      throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("exclude-source"));
    write(sourceRoot, "src/pkg/Excluded.java", "class Excluded { int version = 1; }\n");
    write(sourceRoot, "src/pkg/Refreshed.java", "class Refreshed { int version = 1; }\n");
    write(sourceRoot, "src/keep/Kept.java", "class Kept {}\n");
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("private-output"), true)) {
      SavedSourcePreparation base = fixture.seedDirectory(sourceRoot);
      SourcePreparationTarget excludedFile = fileTarget("src/pkg/Excluded.java");
      SourcePreparationRequest excludeFile =
          fixture.request(
              SourcePreparationOperation.EXCLUDE,
              base.request().origin(),
              base.sourceVersionReference(),
              List.of(excludedFile),
              base.request().declaredExclusions(),
              List.of(excludedFile));

      SavedSourcePreparation fileExcluded = fixture.prepare(excludeFile);

      assertThat(entry(fileExcluded, "src/pkg/Excluded.java").disposition())
          .isEqualTo(SourceEntry.Disposition.EXCLUDED_BY_USER);
      assertThat(entry(fileExcluded, "src/keep/Kept.java").inheritedFrom().baseSourceVersion())
          .isEqualTo(base.sourceVersionReference().sourceVersionId());
      assertThat(fixture.staging.openCount()).hasValue(0);
      fixture.assertNoOriginAccess();

      Files.delete(sourceRoot.resolve("src/pkg/Excluded.java"));
      Files.delete(sourceRoot.resolve("src/pkg/Refreshed.java"));
      Files.delete(sourceRoot.resolve("src/keep/Kept.java"));
      deleteTree(sourceRoot);
      SourcePreparationTarget excludedDirectory = directoryTarget("src/pkg");
      SourcePreparationRequest excludeDirectory =
          fixture.request(
              SourcePreparationOperation.EXCLUDE,
              fileExcluded.request().origin(),
              fileExcluded.sourceVersionReference(),
              List.of(excludedDirectory),
              fileExcluded.request().declaredExclusions(),
              List.of(excludedFile, excludedDirectory));

      SavedSourcePreparation directoryExcluded = fixture.prepare(excludeDirectory);

      assertThat(entry(directoryExcluded, "src/pkg").disposition())
          .isEqualTo(SourceEntry.Disposition.EXCLUDED_BY_USER);
      assertThat(entry(directoryExcluded, "src/keep/Kept.java").inheritedFrom().baseSourceVersion())
          .isEqualTo(fileExcluded.sourceVersionReference().sourceVersionId());
      assertThat(fixture.staging.openCount()).hasValue(0);
      fixture.assertNoOriginAccess();

      SourcePreparationRequest clearsInheritedExclusion =
          fixture.request(
              SourcePreparationOperation.REFRESH,
              fileExcluded.request().origin(),
              fileExcluded.sourceVersionReference(),
              List.of(directoryTarget("src/pkg")),
              fileExcluded.request().declaredExclusions(),
              List.of());
      fixture.assertRejectedBeforeOriginAccess(clearsInheritedExclusion);
      assertThat(fixture.staging.openCount()).hasValue(0);

      Files.createDirectories(sourceRoot);
      write(sourceRoot, "src/pkg/Excluded.java", "class Excluded { int version = 99; }\n");
      write(sourceRoot, "src/pkg/Refreshed.java", "class Refreshed { int version = 2; }\n");
      write(sourceRoot, "src/pkg/Added.java", "class Added {}\n");
      write(sourceRoot, "src/keep/Kept.java", "class Kept {}\n");
      SourcePreparationRequest refreshWithExclusion =
          fixture.request(
              SourcePreparationOperation.REFRESH,
              directoryExcluded.request().origin(),
              directoryExcluded.sourceVersionReference(),
              List.of(directoryTarget("src")),
              directoryExcluded.request().declaredExclusions(),
              directoryExcluded.request().effectiveExclusions());

      SavedSourcePreparation refreshed = fixture.prepare(refreshWithExclusion);

      assertThat(entry(refreshed, "src/pkg").disposition())
          .isEqualTo(SourceEntry.Disposition.EXCLUDED_BY_USER);
      assertThat(fixture.directoryAccess.openInputPaths())
          .containsExactly(sourceRoot.resolve("src/keep/Kept.java"))
          .doesNotContain(
              sourceRoot.resolve("src/pkg/Excluded.java"),
              sourceRoot.resolve("src/pkg/Refreshed.java"),
              sourceRoot.resolve("src/pkg/Added.java"));
      assertThat(refreshed.request().effectiveExclusions())
          .containsExactly(excludedFile, excludedDirectory);
      assertThat(fixture.staging.openCount()).hasValue(1);
    }
  }

  @Test
  void refreshingUnknownSavedFileCanDiscoverItsActualTypeAfterAccessIsRepaired() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("unknown-refresh-source"));
    String unknownPath = "src/PreviouslyUnknown.java";
    Path sourceFile = sourceRoot.resolve(unknownPath);
    write(sourceRoot, unknownPath, "class NewlyKnown {}\n");
    write(sourceRoot, "src/Keep.java", "class Keep {}\n");
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("private-output"), true)) {
      fixture.directoryAccess.failReadAttributes(sourceFile);
      SavedSourcePreparation base = fixture.seedDirectory(sourceRoot, false);

      SourceEntry unknown = entry(base, unknownPath);
      assertThat(unknown.entryKind()).isEqualTo(SourceEntry.Kind.UNKNOWN);
      assertThat(base.result().unknownSubtrees()).contains(unknownPath);
      fixture.directoryAccess.restoreReadAttributes(sourceFile);
      fixture.directoryAccess.reset();

      SourcePreparationRequest refresh =
          fixture.request(
              SourcePreparationOperation.REFRESH,
              base.request().origin(),
              base.sourceVersionReference(),
              List.of(fileTarget(unknownPath)),
              base.request().declaredExclusions(),
              base.request().effectiveExclusions());

      SavedSourcePreparation revised = fixture.prepare(refresh);

      SourceEntry discovered = entry(revised, unknownPath);
      assertThat(discovered.entryKind()).isEqualTo(SourceEntry.Kind.REGULAR_FILE);
      assertThat(discovered.disposition()).isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
      assertThat(revised.result().unknownSubtrees()).doesNotContain(unknownPath);
      assertThat(revised.result().issues())
          .noneMatch(issue -> unknownPath.equals(issue.relativePath()));
      assertThat(fixture.directoryAccess.openInputPaths()).containsExactly(sourceFile);
    }
  }

  @Test
  void gitRefreshUsesOnlyItsSavedCommitAndRejectsAnotherCommitBeforeGitAccess() throws Exception {
    String savedCommit = "a".repeat(40);
    String otherCommit = "b".repeat(40);
    String refreshedBlobId = "1".repeat(40);
    String inheritedBlobId = "2".repeat(40);
    Path repository = Path.of("/private/fixture-git-repository");
    GitCommitSourceOrigin origin =
        new GitCommitSourceOrigin("fixture:git-source", repository, savedCommit);
    byte[] originalRefreshBytes =
        "class Refresh { int version = 1; }\n".getBytes(StandardCharsets.UTF_8);
    byte[] inheritedBytes = "class Inherited {}\n".getBytes(StandardCharsets.UTF_8);
    FixtureGitObjectAccess gitAccess =
        new FixtureGitObjectAccess(
            repository,
            savedCommit,
            List.of(
                new FixedGitObjectAccess.TreeEntry(
                    "100644", "blob", refreshedBlobId, "src/Refresh.java"),
                new FixedGitObjectAccess.TreeEntry(
                    "100644", "blob", inheritedBlobId, "src/Inherited.java")),
            Map.of(refreshedBlobId, originalRefreshBytes, inheritedBlobId, inheritedBytes));
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("private-output"), true)) {
      fixture.useGitObjectAccess(gitAccess);
      SavedSourcePreparation base =
          fixture.prepare(
              fixture.request(
                  SourcePreparationOperation.NEW, origin, null, List.of(), List.of(), List.of()));
      assertThat(paths(base.result())).containsExactly("src/Inherited.java", "src/Refresh.java");

      gitAccess.resetCalls();
      SourcePreparationRequest refresh =
          fixture.request(
              SourcePreparationOperation.REFRESH,
              origin,
              base.sourceVersionReference(),
              List.of(fileTarget("src/Refresh.java")),
              base.request().declaredExclusions(),
              base.request().effectiveExclusions());

      SavedSourcePreparation revised = fixture.prepare(refresh);

      assertThat(gitAccess.openedCommits()).containsExactly(savedCommit);
      assertThat(gitAccess.openedBlobIds()).containsExactly(refreshedBlobId);
      assertThat(entry(revised, "src/Inherited.java").inheritedFrom().baseSourceVersion())
          .isEqualTo(base.sourceVersionReference().sourceVersionId());

      GitCommitSourceOrigin changedCommitOrigin =
          new GitCommitSourceOrigin("fixture:git-source", repository, otherCommit);
      SourcePreparationRequest mixedCommitRefresh =
          fixture.request(
              SourcePreparationOperation.REFRESH,
              changedCommitOrigin,
              base.sourceVersionReference(),
              List.of(fileTarget("src/Refresh.java")),
              base.request().declaredExclusions(),
              base.request().effectiveExclusions());
      fixture.assertRejectedBeforeOriginAccess(mixedCommitRefresh);
      assertThat(gitAccess.openedCommits()).containsExactly(savedCommit);
      assertThat(gitAccess.openedBlobIds()).containsExactly(refreshedBlobId);
    }
  }

  @Test
  void corruptedInheritedBlobBecomesNamedIntegrityIssueAndIsNotPublishedAsVerified()
      throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("corrupt-inherited-source"));
    write(sourceRoot, "src/Refresh.java", "class Refresh { int version = 1; }\n");
    write(sourceRoot, "src/Inherited.java", "class Inherited { int frozen = 1; }\n");
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("private-output"), true)) {
      SavedSourcePreparation base = fixture.seedDirectory(sourceRoot);
      SourceEntry inherited = entry(base, "src/Inherited.java");
      Path inheritedBlob =
          fixture
              .archiveRoot
              .resolve(base.sourceVersionReference().sourceVersionId().value())
              .resolve("blobs")
              .resolve(inherited.sha256().value());
      assertThat(Files.isRegularFile(inheritedBlob)).isTrue();
      fixture.directoryAccess.corruptArchiveBlobWhenReadAttributes(
          sourceRoot.resolve("src/Refresh.java"), inheritedBlob);

      SourcePreparationRequest refresh =
          fixture.request(
              SourcePreparationOperation.REFRESH,
              base.request().origin(),
              base.sourceVersionReference(),
              List.of(fileTarget("src/Refresh.java")),
              base.request().declaredExclusions(),
              base.request().effectiveExclusions());
      SavedSourcePreparation revised = fixture.prepare(refresh);

      assertThat(fixture.directoryAccess.archiveBlobCorrupted()).isTrue();
      SourceEntry unavailableInherited = entry(revised, "src/Inherited.java");
      assertThat(unavailableInherited.disposition()).isEqualTo(SourceEntry.Disposition.UNAVAILABLE);
      assertThat(unavailableInherited.disposition())
          .isNotIn(SourceEntry.Disposition.VERIFIED_TEXT, SourceEntry.Disposition.VERIFIED_MEDIA);
      SourceIssue integrityIssue =
          revised.result().issues().stream()
              .filter(issue -> "src/Inherited.java".equals(issue.relativePath()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("missing inherited-blob integrity issue"));
      assertThat(integrityIssue.category()).isEqualTo(SourceIssue.Category.SAVED_CONTENT_INTEGRITY);
      assertThat(integrityIssue.scope()).isEqualTo(SourceIssue.Scope.FILE);
      assertThat(integrityIssue.operation()).isEqualTo(SourceIssue.Operation.READ_SAVED_CONTENT);
      assertThat(unavailableInherited.issueIds()).containsExactly(integrityIssue.issueId());
    }
  }

  @Test
  void offlineExclusionDowngradesOnlyAnotherCorruptedInheritedFile() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("exclude-corrupt-source"));
    byte[] excludedBytes = "class Excluded {}\n".getBytes(StandardCharsets.UTF_8);
    byte[] corruptBytes = "class Corrupt { int version = 1; }\n".getBytes(StandardCharsets.UTF_8);
    byte[] healthyBytes = "class Healthy {}\n".getBytes(StandardCharsets.UTF_8);
    write(sourceRoot, "src/Excluded.java", excludedBytes);
    write(sourceRoot, "src/Corrupt.java", corruptBytes);
    write(sourceRoot, "src/Healthy.java", healthyBytes);
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("private-output"), true)) {
      SavedSourcePreparation base = fixture.seedDirectory(sourceRoot);
      SourceEntry baseCorrupt = entry(base, "src/Corrupt.java");
      Path corruptBlob =
          fixture
              .archiveRoot
              .resolve(base.sourceVersionReference().sourceVersionId().value())
              .resolve("blobs")
              .resolve(baseCorrupt.sha256().value());
      assertThat(Files.isRegularFile(corruptBlob)).isTrue();
      deleteTree(sourceRoot);

      SourcePreparationTarget exclusion = fileTarget("src/Excluded.java");
      SourcePreparationRequest request =
          fixture.request(
              SourcePreparationOperation.EXCLUDE,
              base.request().origin(),
              base.sourceVersionReference(),
              List.of(exclusion),
              base.request().declaredExclusions(),
              List.of(exclusion));
      Function<AnalysisStepPublicationReference, SavedSourcePreparation> baseOpener =
          reportReference -> {
            SavedSourcePreparation reopened = fixture.reader.reopen(reportReference);
            if (base.reportReference().equals(reportReference)) {
              try {
                Files.write(corruptBlob, "corrupt bytes".getBytes(StandardCharsets.UTF_8));
              } catch (IOException failure) {
                throw new UncheckedIOException(failure);
              }
            }
            return reopened;
          };
      SourcePreparationService service =
          new SourcePreparationService(
              fixture.privateOutputRoot,
              fixture.publisher,
              fixture.reader,
              fixture.archive,
              fixture.identities,
              fixture.directoryAccess,
              fixture.gitObjects,
              fixture.staging,
              baseOpener);

      SavedSourcePreparation revised = service.prepare(fixture.nextRunId(), request);

      SourceEntry excluded = entry(revised, "src/Excluded.java");
      assertThat(excluded.disposition()).isEqualTo(SourceEntry.Disposition.EXCLUDED_BY_USER);
      assertThat(excluded.blobRef()).isNotNull();
      SourceEntry unavailable = entry(revised, "src/Corrupt.java");
      assertThat(unavailable.disposition()).isEqualTo(SourceEntry.Disposition.UNAVAILABLE);
      assertThat(unavailable.blobRef()).isNull();
      SourceIssue integrityIssue =
          revised.result().issues().stream()
              .filter(issue -> "src/Corrupt.java".equals(issue.relativePath()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("missing inherited-blob integrity issue"));
      assertThat(integrityIssue.category()).isEqualTo(SourceIssue.Category.SAVED_CONTENT_INTEGRITY);
      assertThat(integrityIssue.operation()).isEqualTo(SourceIssue.Operation.READ_SAVED_CONTENT);
      assertThat(unavailable.issueIds()).containsExactly(integrityIssue.issueId());

      SourceEntry healthy = entry(revised, "src/Healthy.java");
      assertThat(healthy.disposition()).isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
      assertThat(healthy.inheritedFrom().baseSourceVersion())
          .isEqualTo(base.sourceVersionReference().sourceVersionId());
      try (InputStream reopenedHealthy =
          fixture.archive.open(revised.sourceVersionReference().sourceVersionId(), healthy)) {
        assertThat(reopenedHealthy.readAllBytes()).isEqualTo(healthyBytes);
      }
      assertThat(fixture.staging.openCount()).hasValue(0);
      fixture.assertNoOriginAccess();
    }
  }

  @Test
  void offlineExclusionMakesGoodInheritedFileReadableAndLeavesProblemBaseUnchanged()
      throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("read-closure-source"));
    String brokenPath = "src/Broken.java";
    String goodPath = "src/Good.java";
    Path brokenFile = sourceRoot.resolve(brokenPath);
    byte[] brokenBytes = "class Broken {}\n".getBytes(StandardCharsets.UTF_8);
    byte[] goodBytes = "class Good { int value = 7; }\n".getBytes(StandardCharsets.UTF_8);
    write(sourceRoot, brokenPath, brokenBytes);
    write(sourceRoot, goodPath, goodBytes);
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("read-closure-output"), true)) {
      fixture.directoryAccess.failOpenInput(brokenFile);
      DirectorySourceOrigin origin =
          new DirectorySourceOrigin("fixture:read-closure-source", sourceRoot.toRealPath());
      SourcePreparationRequest initialRequest =
          fixture.request(
              SourcePreparationOperation.NEW, origin, null, List.of(), List.of(), List.of());
      SavedSourcePreparation base = fixture.prepare(initialRequest);
      fixture.directoryAccess.reset();

      SourceEntry unavailable = entry(base, brokenPath);
      assertThat(base.assessment().readiness())
          .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
      assertThat(unavailable.disposition()).isEqualTo(SourceEntry.Disposition.UNAVAILABLE);
      SourceIssue readIssue =
          base.result().issues().stream()
              .filter(issue -> brokenPath.equals(issue.relativePath()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("missing local source-read issue"));
      assertThat(readIssue.code()).isEqualTo(SourceIssue.Code.SOURCE_ENTRY_READ_FAILED);
      assertThat(readIssue.resolution()).isEqualTo(SourceIssue.Resolution.OPEN);

      SourcePreparationTarget exclusion = fileTarget(brokenPath);
      SourcePreparationRequest request =
          fixture.request(
              SourcePreparationOperation.EXCLUDE,
              base.request().origin(),
              base.sourceVersionReference(),
              List.of(exclusion),
              base.request().declaredExclusions(),
              List.of(exclusion));
      deleteTree(sourceRoot);

      SavedSourcePreparation derived = fixture.prepare(request);

      assertThat(derived.assessment().readiness())
          .isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
      assertThat(entry(derived, brokenPath).disposition())
          .isEqualTo(SourceEntry.Disposition.EXCLUDED_BY_USER);
      fixture.assertNoOriginAccess();
      VerifiedSourceTextSet readable =
          new PreparedVerifiedSourceTextReader(fixture.reader, fixture.archive)
              .reopen(new VerifiedSourceInventoryReference(derived.reportReference()));
      assertThat(readable.documents().stream().map(document -> document.path()).toList())
          .containsExactly(goodPath);
      assertThat(readable.documents().get(0).mediaType()).isEqualTo("text/plain; charset=UTF-8");
      assertThat(readable.documents().get(0).rawUtf8().copyToByteArray()).isEqualTo(goodBytes);

      SavedSourcePreparation reopenedBase = fixture.reader.reopen(base.reportReference());
      assertThat(reopenedBase.reportReference()).isEqualTo(base.reportReference());
      assertThat(reopenedBase.sourceVersionReference()).isEqualTo(base.sourceVersionReference());
      assertThat(reopenedBase.result()).isEqualTo(base.result());
      assertThat(reopenedBase.assessment()).isEqualTo(base.assessment());
      assertThat(entry(reopenedBase, brokenPath).disposition())
          .isEqualTo(SourceEntry.Disposition.UNAVAILABLE);
      assertThat(
              reopenedBase.result().issues().stream()
                  .filter(issue -> brokenPath.equals(issue.relativePath()))
                  .findFirst()
                  .orElseThrow()
                  .resolution())
          .isEqualTo(SourceIssue.Resolution.OPEN);
      try (InputStream originalGood =
          fixture.archive.open(
              base.sourceVersionReference().sourceVersionId(), entry(reopenedBase, goodPath))) {
        assertThat(originalGood.readAllBytes()).isEqualTo(goodBytes);
      }
    }
  }

  @Test
  void unknownSubtreeRequiresDirectoryExclusionAndKeepsItsPhysicalCountUnknown() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("unknown-exclusion-source"));
    String unknownPath = "src/Unknown";
    Path unknownDirectory = sourceRoot.resolve(unknownPath);
    Files.createDirectories(unknownDirectory);
    write(sourceRoot, "src/Unknown/Hidden.java", "class Hidden {}\n");
    write(sourceRoot, "src/Good.java", "class Good {}\n");
    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("unknown-exclusion-output"), true)) {
      fixture.directoryAccess.failReadAttributes(unknownDirectory);
      DirectorySourceOrigin origin =
          new DirectorySourceOrigin("fixture:unknown-exclusion", sourceRoot.toRealPath());
      SourcePreparationRequest initialRequest =
          fixture.request(
              SourcePreparationOperation.NEW, origin, null, List.of(), List.of(), List.of());
      SavedSourcePreparation base = fixture.prepare(initialRequest);
      fixture.directoryAccess.reset();

      SourceEntry unknown = entry(base, unknownPath);
      assertThat(unknown.entryKind()).isEqualTo(SourceEntry.Kind.UNKNOWN);
      assertThat(unknown.disposition()).isEqualTo(SourceEntry.Disposition.UNAVAILABLE);
      assertThat(base.result().unknownSubtrees()).containsExactly(unknownPath);
      assertThat(base.assessment().summary().totalRegularFiles()).isNull();
      SourceIssue enumerationIssue =
          base.result().issues().stream()
              .filter(issue -> unknownPath.equals(issue.relativePath()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("missing unknown subtree issue"));
      assertThat(enumerationIssue.scope()).isEqualTo(SourceIssue.Scope.DIRECTORY);
      assertThat(enumerationIssue.allowedActions())
          .contains(
              SourceIssue.AllowedAction.EXCLUDE_FILE, SourceIssue.AllowedAction.EXCLUDE_DIRECTORY);
      deleteTree(sourceRoot);

      SourcePreparationTarget fileExclusion = fileTarget(unknownPath);
      SourcePreparationRequest unsafeFileExclusion =
          fixture.request(
              SourcePreparationOperation.EXCLUDE,
              base.request().origin(),
              base.sourceVersionReference(),
              List.of(fileExclusion),
              base.request().declaredExclusions(),
              List.of(fileExclusion));
      List<Path> storeBeforeFileExclusion = treePaths(fixture.storeRoot);
      List<Path> archiveBeforeFileExclusion = treePaths(fixture.archiveRoot);
      assertThatThrownBy(() -> fixture.prepare(unsafeFileExclusion))
          .isInstanceOf(IOException.class);
      assertThat(treePaths(fixture.storeRoot)).containsExactlyElementsOf(storeBeforeFileExclusion);
      assertThat(treePaths(fixture.archiveRoot))
          .containsExactlyElementsOf(archiveBeforeFileExclusion);

      SourcePreparationTarget directoryExclusion = directoryTarget(unknownPath);
      SourcePreparationRequest safeDirectoryExclusion =
          fixture.request(
              SourcePreparationOperation.EXCLUDE,
              base.request().origin(),
              base.sourceVersionReference(),
              List.of(directoryExclusion),
              base.request().declaredExclusions(),
              List.of(directoryExclusion));
      SavedSourcePreparation derived = fixture.prepare(safeDirectoryExclusion);

      assertThat(derived.assessment().readiness())
          .isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
      assertThat(entry(derived, unknownPath).entryKind()).isEqualTo(SourceEntry.Kind.UNKNOWN);
      assertThat(entry(derived, unknownPath).disposition())
          .isEqualTo(SourceEntry.Disposition.EXCLUDED_BY_USER);
      assertThat(derived.result().enumerationComplete()).isFalse();
      assertThat(derived.result().unknownSubtrees()).containsExactly(unknownPath);
      assertThat(derived.assessment().summary().totalRegularFiles()).isNull();
      assertThat(derived.assessment().summary().verifiedTextFiles()).isEqualTo(1);
      assertThat(fixture.staging.openCount()).hasValue(1);
      fixture.assertNoOriginAccess();
      SavedSourcePreparation reopenedBase = fixture.reader.reopen(base.reportReference());
      assertThat(reopenedBase.result()).isEqualTo(base.result());
      assertThat(reopenedBase.assessment()).isEqualTo(base.assessment());
    }
  }

  @Test
  void excludingTheKnownFileThatTriggeredAResourceAbortKeepsUnenumeratedScopeUnready()
      throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("resource-abort-source"));
    write(sourceRoot, "a-accepted.java", "class Accepted {}\n");
    write(sourceRoot, "b-limit-trigger.java", "class Trigger {}\n");
    write(sourceRoot, "c-not-enumerated.java", "class NotEnumerated {}\n");
    SourceOrigin origin =
        new DirectorySourceOrigin("fixture:resource-abort", sourceRoot.toRealPath());

    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("resource-abort-output"), true)) {
      SourcePreparationRequest initial =
          new SourcePreparationRequest(
              SourcePreparationOperation.NEW,
              origin,
              null,
              List.of(),
              List.of(),
              List.of(),
              new SourcePreparationLimits(1, 1_000_000L),
              new ArtifactReference(
                  fixture.policies.reference().artifactId(),
                  fixture.policies.reference().sha256()));
      SavedSourcePreparation base = fixture.prepare(initial);
      SourcePreparationTarget excluded = fileTarget("b-limit-trigger.java");
      SourcePreparationRequest exclusion =
          new SourcePreparationRequest(
              SourcePreparationOperation.EXCLUDE,
              origin,
              base.sourceVersionReference(),
              List.of(excluded),
              initial.declaredExclusions(),
              List.of(excluded),
              initial.limits(),
              initial.policyRef());

      SavedSourcePreparation derived = fixture.prepare(exclusion);

      assertThat(entry(derived, "b-limit-trigger.java").disposition())
          .isEqualTo(SourceEntry.Disposition.EXCLUDED_BY_USER);
      assertThat(paths(derived.result())).doesNotContain("c-not-enumerated.java");
      assertThat(derived.result().enumerationComplete()).isFalse();
      assertThat(derived.assessment().summary().totalRegularFiles()).isNull();
      assertThat(derived.assessment().readiness())
          .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
    }
  }

  @Test
  void newPreparationCanExcludeAKnownFileBeforeApplyingTheResourceLimit() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("preexcluded-source"));
    write(sourceRoot, "a-excluded.java", "class Excluded {}\n");
    write(sourceRoot, "b-included.java", "class Included {}\n");
    write(sourceRoot, "c-included.java", "class AlsoIncluded {}\n");
    SourceOrigin origin = new DirectorySourceOrigin("fixture:preexcluded", sourceRoot.toRealPath());
    SourcePreparationTarget excluded = fileTarget("a-excluded.java");

    try (RevisionFixture fixture =
        new RevisionFixture(temporaryDirectory.resolve("preexcluded-output"), true)) {
      SourcePreparationRequest initial =
          new SourcePreparationRequest(
              SourcePreparationOperation.NEW,
              origin,
              null,
              List.of(),
              List.of(excluded),
              List.of(excluded),
              new SourcePreparationLimits(2, 1_000_000L),
              new ArtifactReference(
                  fixture.policies.reference().artifactId(),
                  fixture.policies.reference().sha256()));

      SavedSourcePreparation prepared = fixture.prepare(initial);

      assertThat(entry(prepared, "a-excluded.java").disposition())
          .isEqualTo(SourceEntry.Disposition.EXCLUDED_BY_USER);
      assertThat(entry(prepared, "b-included.java").disposition())
          .isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
      assertThat(entry(prepared, "c-included.java").disposition())
          .isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
      assertThat(prepared.result().enumerationComplete()).isTrue();
      assertThat(prepared.assessment().readiness())
          .isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
    }
  }

  private static SourcePreparationTarget fileTarget(String path) {
    return new SourcePreparationTarget(path, SourcePreparationTarget.Kind.FILE);
  }

  private static SourcePreparationTarget directoryTarget(String path) {
    return new SourcePreparationTarget(path, SourcePreparationTarget.Kind.DIRECTORY);
  }

  private static SourceEntry entry(SavedSourcePreparation saved, String path) {
    return saved.result().entries().stream()
        .filter(candidate -> candidate.relativePath().equals(path))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing prepared source entry " + path));
  }

  private static List<String> paths(SourcePreparationResult result) {
    return result.entries().stream().map(SourceEntry::relativePath).toList();
  }

  private static List<Path> treePaths(Path root) throws IOException {
    if (!Files.exists(root)) {
      return List.of();
    }
    try (var paths = Files.walk(root)) {
      return paths.map(root::relativize).sorted().toList();
    }
  }

  private static void write(Path root, String relativePath, String content) throws IOException {
    Path path = root.resolve(relativePath);
    Files.createDirectories(path.getParent());
    Files.writeString(path, content, StandardCharsets.UTF_8);
  }

  private static void write(Path root, String relativePath, byte[] content) throws IOException {
    Path path = root.resolve(relativePath);
    Files.createDirectories(path.getParent());
    Files.write(path, content);
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }

  private final class RevisionFixture implements AutoCloseable {

    private final Path privateOutputRoot;
    private final Path archiveRoot = temporaryDirectory.resolve("prepared-source-archive");
    private final Path storeRoot = temporaryDirectory.resolve("canonical-store");
    private final Path seedOutputRoot = temporaryDirectory.resolve("seed-staging");
    private final CanonicalArtifactPolicyRegistry policies;
    private final RunStoreHandle storeHandle;
    private final CanonicalModuleArtifactStore modules;
    private final CanonicalAnalysisStepArtifactStore steps;
    private final PreparedSourceArchive archive = new PreparedSourceArchive(archiveRoot);
    private final SourcePreparationPublisher publisher;
    private final SourcePreparationReader reader;
    private final CountingDirectorySourceAccess directoryAccess =
        new CountingDirectorySourceAccess();
    private final AtomicInteger gitObjectOpens = new AtomicInteger();
    private FixedGitObjectAccess gitObjectDelegate =
        (repository, commit) -> {
          throw new IOException("unexpected Git object access in this fixture");
        };
    private final FixedGitObjectAccess gitObjects =
        (repository, commit) -> {
          gitObjectOpens.incrementAndGet();
          return gitObjectDelegate.open(repository, commit);
        };
    private final AtomicInteger gitVersionProbes = new AtomicInteger();
    private final RecordingStagingFactory staging = new RecordingStagingFactory();
    private final SourcePreparationToolIdentityFactory identities;
    private int runSequence = 1;

    private RevisionFixture(Path privateOutputRoot, boolean createOutputRoot) throws IOException {
      this.privateOutputRoot = privateOutputRoot.toAbsolutePath().normalize();
      if (createOutputRoot) {
        Files.createDirectories(this.privateOutputRoot);
      }
      Files.createDirectory(storeRoot);
      storeHandle = RunStoreBootstrap.open(storeRoot);
      policies =
          SourcePreparationPolicyFixture.load(
              Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
                  .toAbsolutePath(),
              JSON);
      modules =
          new FileSystemCanonicalModuleArtifactStore(storeHandle, JSON, policies, STORE_LIMITS);
      steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              storeHandle, JSON, policies, STORE_LIMITS);
      publisher = new SourcePreparationPublisher(modules, steps, archive, policies);
      reader = new SourcePreparationReader(modules, steps, archive);
      Path identityClasses = temporaryDirectory.resolve("identity-classes");
      Path identityClass = identityClasses.resolve("org/sourceanalysis/app/Identity.class");
      Files.createDirectories(identityClass.getParent());
      Files.write(identityClass, new byte[] {0x01, 0x02, 0x03});
      identities =
          new SourcePreparationToolIdentityFactory(
              identityClasses,
              "Fixture Vendor",
              "Fixture Java",
              () -> {
                gitVersionProbes.incrementAndGet();
                return "git version fixture";
              });
    }

    @Override
    public void close() throws IOException {
      storeHandle.close();
    }

    private SourcePreparationService service(SourcePreparationReader savedReader) {
      return new SourcePreparationService(
          privateOutputRoot,
          publisher,
          savedReader,
          archive,
          identities,
          directoryAccess,
          gitObjects,
          staging);
    }

    private void useGitObjectAccess(FixedGitObjectAccess access) {
      gitObjectDelegate = access;
    }

    private SavedSourcePreparation prepare(SourcePreparationRequest request) throws IOException {
      return service(reader).prepare(nextRunId(), request);
    }

    private SourcePreparationRequest request(
        SourcePreparationOperation operation,
        SourceOrigin origin,
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
          new SourcePreparationLimits(50, 1_000_000L),
          new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256()));
    }

    private SavedSourcePreparation seedDirectory(Path sourceRoot) throws IOException {
      return seedDirectory(sourceRoot, true);
    }

    private SavedSourcePreparation seedDirectory(Path sourceRoot, boolean expectComplete)
        throws IOException {
      DirectorySourceOrigin origin =
          new DirectorySourceOrigin("fixture:revision-source", sourceRoot.toRealPath());
      SourcePreparationRequest request =
          request(SourcePreparationOperation.NEW, origin, null, List.of(), List.of(), List.of());
      Files.createDirectories(seedOutputRoot);
      StagedSourcePreparationBlobSink seedSink =
          new StagedSourcePreparationBlobSink(seedOutputRoot);
      SourcePreparationResult result =
          new DirectorySourceOriginReader(directoryAccess, seedOutputRoot).read(request, seedSink);
      if (result.inspectionStatus() != SourcePreparationResult.InspectionStatus.COMPLETED
          || result.enumerationComplete() != expectComplete) {
        throw new AssertionError("directory seed completeness did not match its test fixture");
      }
      CapturedSourcePreparation capture =
          new CapturedSourcePreparation(
              request,
              result,
              entry ->
                  Files.newInputStream(
                      seedOutputRoot
                          .resolve("source-preparation-blobs")
                          .resolve(entry.relativePath())),
              identities.detect(origin));
      SavedSourcePreparation saved = publisher.publish(nextRunId(), capture);
      directoryAccess.reset();
      return saved;
    }

    private AnalysisRunId nextRunId() {
      char fill = Character.forDigit(runSequence++, 16);
      return AnalysisRunId.parse("analysis-run:" + String.valueOf(fill).repeat(64));
    }

    private void assertRejectedBeforeOriginAccess(SourcePreparationRequest request)
        throws Exception {
      directoryAccess.reset();
      int gitBefore = gitObjectOpens.get();
      int probesBefore = gitVersionProbes.get();
      int stagingBefore = staging.openCount().get();
      assertThatThrownBy(() -> service(reader).prepare(nextRunId(), request))
          .as("invalid base/origin must be rejected before customer source access")
          .isInstanceOf(Exception.class);
      assertNoOriginAccess();
      assertThat(gitObjectOpens).hasValue(gitBefore);
      assertThat(gitVersionProbes).hasValue(probesBefore);
      assertThat(staging.openCount()).hasValue(stagingBefore);
    }

    private void assertNoOriginAccess() {
      assertThat(directoryAccess.attributePaths()).isEmpty();
      assertThat(directoryAccess.openDirectoryPaths()).isEmpty();
      assertThat(directoryAccess.openInputPaths()).isEmpty();
    }
  }

  private static final class CountingDirectorySourceAccess implements DirectorySourceAccess {

    private final DirectorySourceAccess delegate = new JdkDirectorySourceAccess();
    private final List<Path> attributePaths = new ArrayList<>();
    private final List<Path> openDirectoryPaths = new ArrayList<>();
    private final List<Path> openInputPaths = new ArrayList<>();
    private final Set<Path> failedAttributePaths = new HashSet<>();
    private final Set<Path> failedInputPaths = new HashSet<>();
    private Path corruptionTriggerPath;
    private Path archiveBlobToCorrupt;
    private boolean archiveBlobCorrupted;

    @Override
    public DirectoryStream<Path> openDirectory(Path directory) throws IOException {
      openDirectoryPaths.add(directory.toAbsolutePath().normalize());
      return delegate.openDirectory(directory);
    }

    @Override
    public BasicFileAttributes readAttributes(Path path) throws IOException {
      Path normalized = path.toAbsolutePath().normalize();
      attributePaths.add(normalized);
      if (!archiveBlobCorrupted && normalized.equals(corruptionTriggerPath)) {
        Files.write(
            archiveBlobToCorrupt, "corrupted archived bytes".getBytes(StandardCharsets.UTF_8));
        archiveBlobCorrupted = true;
      }
      if (failedAttributePaths.contains(normalized)) {
        throw new IOException("injected attribute failure");
      }
      return delegate.readAttributes(path);
    }

    @Override
    public InputStream openInput(Path path) throws IOException {
      Path normalized = path.toAbsolutePath().normalize();
      openInputPaths.add(normalized);
      if (failedInputPaths.contains(normalized)) {
        throw new IOException("injected input failure");
      }
      return delegate.openInput(path);
    }

    private void reset() {
      attributePaths.clear();
      openDirectoryPaths.clear();
      openInputPaths.clear();
    }

    private void failReadAttributes(Path path) {
      failedAttributePaths.add(path.toAbsolutePath().normalize());
    }

    private void restoreReadAttributes(Path path) {
      failedAttributePaths.remove(path.toAbsolutePath().normalize());
    }

    private void failOpenInput(Path path) {
      failedInputPaths.add(path.toAbsolutePath().normalize());
    }

    private void corruptArchiveBlobWhenReadAttributes(Path sourcePath, Path archiveBlob) {
      corruptionTriggerPath = sourcePath.toAbsolutePath().normalize();
      archiveBlobToCorrupt = archiveBlob;
    }

    private boolean archiveBlobCorrupted() {
      return archiveBlobCorrupted;
    }

    private List<Path> attributePaths() {
      return List.copyOf(attributePaths);
    }

    private List<Path> openDirectoryPaths() {
      return List.copyOf(openDirectoryPaths);
    }

    private List<Path> openInputPaths() {
      return List.copyOf(openInputPaths);
    }
  }

  private static final class FixtureGitObjectAccess implements FixedGitObjectAccess {

    private final Path repository;
    private final String expectedCommit;
    private final List<TreeEntry> treeEntries;
    private final Map<String, byte[]> blobs;
    private final List<String> openedCommits = new ArrayList<>();
    private final List<String> openedBlobIds = new ArrayList<>();

    private FixtureGitObjectAccess(
        Path repository,
        String expectedCommit,
        List<TreeEntry> treeEntries,
        Map<String, byte[]> blobs) {
      this.repository = repository;
      this.expectedCommit = expectedCommit;
      this.treeEntries = List.copyOf(treeEntries);
      this.blobs = Map.copyOf(blobs);
    }

    @Override
    public Session open(Path repository, String exactCommitId) throws IOException {
      openedCommits.add(exactCommitId);
      if (!this.repository.equals(repository) || !expectedCommit.equals(exactCommitId)) {
        throw new IOException(
            "fixture Git object access received a different repository or commit");
      }
      return new Session() {
        @Override
        public String treeObjectId() {
          return "f".repeat(40);
        }

        @Override
        public DirectoryStream<TreeEntry> entries() {
          return new DirectoryStream<>() {
            @Override
            public Iterator<TreeEntry> iterator() {
              return treeEntries.iterator();
            }

            @Override
            public void close() {
              // No external resource is held by this fixture stream.
            }
          };
        }

        @Override
        public BlobInput openBlob(String objectId) throws IOException {
          openedBlobIds.add(objectId);
          byte[] bytes = blobs.get(objectId);
          if (bytes == null) {
            throw new IOException("fixture Git blob is not available");
          }
          return new BlobInput(bytes.length, new ByteArrayInputStream(bytes));
        }

        @Override
        public void close() {
          // No external resource is held by this fixture session.
        }
      };
    }

    private void resetCalls() {
      openedCommits.clear();
      openedBlobIds.clear();
    }

    private List<String> openedCommits() {
      return List.copyOf(openedCommits);
    }

    private List<String> openedBlobIds() {
      return List.copyOf(openedBlobIds);
    }
  }

  private final class RecordingStagingFactory implements SourcePreparationStagingFactory {

    private final AtomicInteger openCount = new AtomicInteger();

    @Override
    public SourcePreparationStaging open(Path privateOutputRoot, AnalysisRunId runId)
        throws IOException {
      openCount.incrementAndGet();
      Path runRoot = privateOutputRoot.resolve("source-preparation-staging").resolve(runId.value());
      Files.createDirectories(runRoot);
      StagedSourcePreparationBlobSink sink = new StagedSourcePreparationBlobSink(runRoot);
      return new SourcePreparationStaging() {
        @Override
        public SourcePreparationBlobSink sink() {
          return sink;
        }

        @Override
        public SourcePreparationBlobReader acceptedBytes() {
          return entry ->
              Files.newInputStream(
                  runRoot.resolve("source-preparation-blobs").resolve(entry.relativePath()));
        }

        @Override
        public void close() {
          // Per-test staging is temporary and is cleaned with the JUnit temporary root.
        }
      };
    }

    private AtomicInteger openCount() {
      return openCount;
    }
  }
}
