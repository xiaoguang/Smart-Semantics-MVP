package org.sourceanalysis.app.capture.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.GitCommitSourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceIssue;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationLimits;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadinessEvaluator;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.ConstrainedGitObjectAccess;
import org.sourceanalysis.app.capture.localgit.FixedGitObjectAccess;

/** RED coverage for the validated fixed-commit Git reader and shared streaming checker. */
class GitSourcePreparationTest {

  private static final ArtifactReference POLICY =
      new ArtifactReference(
          ArtifactId.parse(
              "source-preparation-policy:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
          Sha256Digest.parse("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
  private static final String GITLINK_OBJECT = "0123456789012345678901234567890123456789";

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void resolveTemporaryRootThroughFilesystemLinks() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void readsOnlyTheExactCommitAndNulSafeUnicodeTreeWithoutOpeningLinksOrSubmodules()
      throws Exception {
    Fixture fixture = createTreeFixture();
    Files.writeString(
        fixture.repository().resolve("README.md"), "worktree mutation\n", StandardCharsets.UTF_8);
    Files.writeString(
        fixture.repository().resolve("untracked.txt"), "must not enter\n", StandardCharsets.UTF_8);
    Path output = temporaryDirectory.resolve("prepared-output");

    SourcePreparationResult result =
        new GitSourceOriginReader(output, trustedGitExecutable())
            .read(
                request(fixture, new SourcePreparationLimits(32, 1_000_000L)),
                new StagedSourcePreparationBlobSink(output));

    assertThat(paths(result))
        .contains(
            "README.md",
            "line\nbreak.txt",
            "link.txt",
            "target.txt",
            "资料/跨块🙂.txt",
            "vendor/submodule")
        .doesNotContain("untracked.txt");
    assertThat(entry(result, "README.md").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
    assertThat(stagedBytes(output, "README.md"))
        .containsExactly("committed README\n".getBytes(StandardCharsets.UTF_8));
    assertThat(entry(result, "control.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_MEDIA);
    assertThat(stagedBytes(output, "control.txt"))
        .containsExactly(new byte[] {'c', 'o', 'n', 't', 'r', 'o', 'l', 0x01, 0x0a});
    assertThat(entry(result, "link.txt").entryKind()).isEqualTo(SourceEntry.Kind.SYMLINK);
    assertThat(entry(result, "link.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.SKIPPED_SYMLINK);
    assertThat(issueFor(result, "link.txt").resolution())
        .isEqualTo(SourceIssue.Resolution.INFORMATIONAL);
    assertThat(entry(result, "vendor/submodule").entryKind()).isEqualTo(SourceEntry.Kind.SUBMODULE);
    assertThat(entry(result, "vendor/submodule").disposition())
        .isEqualTo(SourceEntry.Disposition.UNSUPPORTED);
    assertThat(issueFor(result, "vendor/submodule").resolution())
        .isEqualTo(SourceIssue.Resolution.OPEN);
    assertThat(Files.exists(output.resolve("source-preparation-blobs/link.txt"))).isFalse();
    assertThat(Files.exists(output.resolve("source-preparation-blobs/vendor/submodule"))).isFalse();
    assertThat(SourcePreparationReadinessEvaluator.assess(result).readiness())
        .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
  }

  @Test
  void preservesSafeSiblingsWhenOneCommitBlobFailsAndDoesNotOpenAnExcludedBlob() throws Exception {
    Fixture fixture = createSimpleFixture();
    SourcePreparationTarget excluded =
        new SourcePreparationTarget("excluded.txt", SourcePreparationTarget.Kind.FILE);
    RecordingFixedGitObjectAccess access =
        new RecordingFixedGitObjectAccess(realAccess(), Set.of("broken.txt"));
    Path output = temporaryDirectory.resolve("prepared-output");

    SourcePreparationResult result =
        new GitSourceOriginReader(access, output)
            .read(
                request(fixture, new SourcePreparationLimits(32, 1_000_000L), List.of(excluded)),
                new StagedSourcePreparationBlobSink(output));

    assertThat(entry(result, "good.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
    assertThat(entry(result, "broken.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.UNAVAILABLE);
    assertThat(issueFor(result, "broken.txt").code())
        .isEqualTo(SourceIssue.Code.SOURCE_ENTRY_READ_FAILED);
    assertThat(entry(result, "excluded.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.EXCLUDED_BY_USER);
    assertThat(access.openedPaths())
        .contains("good.txt", "broken.txt")
        .doesNotContain("excluded.txt");
    assertThat(SourcePreparationReadinessEvaluator.assess(result).readiness())
        .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
  }

  @Test
  void stopsWithOutputFailureWhenBlobReadAndStagedWriterCloseBothFail() throws Exception {
    Fixture fixture = createSimpleFixture();
    Path output = temporaryDirectory.resolve("prepared-output");
    RecordingFixedGitObjectAccess access =
        new RecordingFixedGitObjectAccess(realAccess(), Set.of(), Set.of("a-first.txt"));
    SourcePreparationBlobSink sink =
        new CloseFailingBlobSink(new StagedSourcePreparationBlobSink(output), "a-first.txt");

    SourcePreparationResult result =
        new GitSourceOriginReader(access, output)
            .read(request(fixture, new SourcePreparationLimits(32, 1_000_000L)), sink);

    assertThat(result.issues())
        .extracting(SourceIssue::code)
        .contains(SourceIssue.Code.SOURCE_ENTRY_READ_FAILED, SourceIssue.Code.SOURCE_OUTPUT_FAILED);
    assertThat(result.inspectionStatus())
        .isEqualTo(SourcePreparationResult.InspectionStatus.ABORTED);
    assertThat(result.enumerationComplete()).isFalse();
    assertThat(access.openedPaths()).contains("a-first.txt").hasSize(1);
  }

  @Test
  void verifiesZeroByteBlobAfterTheByteBudgetHasBeenConsumed() throws Exception {
    Fixture fixture = createByteBudgetFixture();
    Path output = temporaryDirectory.resolve("prepared-output");
    RecordingFixedGitObjectAccess access =
        new RecordingFixedGitObjectAccess(realAccess(), Set.of());

    SourcePreparationResult result =
        new GitSourceOriginReader(access, output)
            .read(
                request(fixture, new SourcePreparationLimits(32, 6L)),
                new StagedSourcePreparationBlobSink(output));

    assertThat(entry(result, "a-first.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
    SourceEntry empty = entry(result, "b-empty.txt");
    assertThat(empty.disposition()).isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
    assertThat(empty.sizeBytes()).isZero();
    assertThat(empty.blobRef()).isNotNull();
    assertThat(empty.sha256()).isNotNull();
    assertThat(stagedBytes(output, "b-empty.txt")).isEmpty();
    assertThat(access.openedPaths()).contains("b-empty.txt");
    assertThat(entry(result, "c-next.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.UNCHECKED);
    assertThat(issueFor(result, "c-next.txt").code())
        .isEqualTo(SourceIssue.Code.VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED);
  }

  @Test
  void enforcesSharedFileAndByteLimitsWhileRetainingUncheckedCommitEntries() throws Exception {
    Fixture fixture = createSimpleFixture();
    Path output = temporaryDirectory.resolve("prepared-output");
    RecordingFixedGitObjectAccess access =
        new RecordingFixedGitObjectAccess(realAccess(), Set.of());

    SourcePreparationResult result =
        new GitSourceOriginReader(access, output)
            .read(
                request(fixture, new SourcePreparationLimits(1, 6L)),
                new StagedSourcePreparationBlobSink(output));

    assertThat(entry(result, "a-first.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
    assertThat(entry(result, "b-second.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.UNCHECKED);
    assertThat(issueFor(result, "b-second.txt").code())
        .isEqualTo(SourceIssue.Code.VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED);
    assertThat(issueFor(result, "b-second.txt").allowedActions())
        .containsExactly(SourceIssue.AllowedAction.NEW_PREPARATION);
    assertThat(access.openedPaths()).containsExactly("a-first.txt");
    assertThat(result.inspectionStatus())
        .isEqualTo(SourcePreparationResult.InspectionStatus.ABORTED);
    assertThat(SourcePreparationReadinessEvaluator.assess(result).readiness())
        .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
  }

  @Test
  void blocksUnsafeObjectSourcesBeforeExposingTreeFacts() throws Exception {
    Fixture fixture = createSimpleFixture();
    Files.writeString(
        fixture.repository().resolve(".git/objects/info/alternates"),
        "/outside/object-store\n",
        StandardCharsets.UTF_8);
    Path output = temporaryDirectory.resolve("prepared-output");
    SourcePreparationResult result =
        new GitSourceOriginReader(output, trustedGitExecutable())
            .read(
                request(fixture, new SourcePreparationLimits(32, 1_000_000L)),
                new StagedSourcePreparationBlobSink(output));

    assertThat(SourcePreparationReadinessEvaluator.assess(result).readiness())
        .isEqualTo(SourcePreparationReadiness.BLOCKED);
    assertThat(result.entries()).isEmpty();
    assertThat(result.issues())
        .anySatisfy(
            issue -> {
              assertThat(issue.scope()).isEqualTo(SourceIssue.Scope.ROOT);
              assertThat(issue.category()).isEqualTo(SourceIssue.Category.ACCESS);
            });
  }

  @Test
  void rejectsSourceOutputOverlapBeforeOpeningValidatedGitSession() throws Exception {
    Fixture fixture = createSimpleFixture();
    Path output = fixture.repository().resolve("prepared-output");
    RecordingFixedGitObjectAccess access =
        new RecordingFixedGitObjectAccess(realAccess(), Set.of());

    SourcePreparationResult result =
        new GitSourceOriginReader(access, output)
            .read(
                request(fixture, new SourcePreparationLimits(32, 1_000_000L)),
                new StagedSourcePreparationBlobSink(output));

    assertThat(SourcePreparationReadinessEvaluator.assess(result).readiness())
        .isEqualTo(SourcePreparationReadiness.BLOCKED);
    assertThat(result.issues())
        .anySatisfy(issue -> assertThat(issue.category()).isEqualTo(SourceIssue.Category.REQUEST));
    assertThat(access.openCalls()).isZero();
  }

  private FixedGitObjectAccess realAccess() throws IOException {
    return new ConstrainedGitObjectAccess(
        temporaryDirectory.resolve("git-workspace"), trustedGitExecutable());
  }

  private Path trustedGitExecutable() {
    return Path.of("/usr/bin/git");
  }

  private SourcePreparationRequest request(Fixture fixture, SourcePreparationLimits limits) {
    return request(fixture, limits, List.of());
  }

  private SourcePreparationRequest request(
      Fixture fixture, SourcePreparationLimits limits, List<SourcePreparationTarget> exclusions) {
    return new SourcePreparationRequest(
        SourcePreparationOperation.NEW,
        new GitCommitSourceOrigin(
            "https://example.invalid/fixture.git",
            fixture.repository().toAbsolutePath().normalize(),
            fixture.commitId()),
        null,
        List.of(),
        exclusions,
        exclusions,
        limits,
        POLICY);
  }

  private Fixture createTreeFixture() throws Exception {
    Path repository = initialiseRepository("tree-repository");
    Files.writeString(
        repository.resolve("README.md"), "committed README\n", StandardCharsets.UTF_8);
    Files.writeString(repository.resolve("target.txt"), "symlink target\n", StandardCharsets.UTF_8);
    Files.createDirectories(repository.resolve("资料"));
    Files.writeString(repository.resolve("资料/跨块🙂.txt"), "跨块🙂é\r\n", StandardCharsets.UTF_8);
    Files.writeString(
        repository.resolve("line\nbreak.txt"), "NUL-safe tree path\n", StandardCharsets.UTF_8);
    Files.write(
        repository.resolve("control.txt"),
        new byte[] {'c', 'o', 'n', 't', 'r', 'o', 'l', 0x01, 0x0a});
    Files.createSymbolicLink(repository.resolve("link.txt"), Path.of("target.txt"));
    runGit(repository, "add", ".");
    runGit(
        repository,
        "update-index",
        "--add",
        "--cacheinfo",
        "160000," + GITLINK_OBJECT + ",vendor/submodule");
    runGit(repository, "commit", "-m", "tree fixture");
    return new Fixture(repository, runGit(repository, "rev-parse", "HEAD").trim());
  }

  private Fixture createSimpleFixture() throws Exception {
    Path repository = initialiseRepository("simple-repository");
    Files.writeString(repository.resolve("a-first.txt"), "first\n", StandardCharsets.UTF_8);
    Files.writeString(repository.resolve("b-second.txt"), "second\n", StandardCharsets.UTF_8);
    Files.writeString(repository.resolve("good.txt"), "good\n", StandardCharsets.UTF_8);
    Files.writeString(repository.resolve("broken.txt"), "broken\n", StandardCharsets.UTF_8);
    Files.writeString(repository.resolve("excluded.txt"), "excluded\n", StandardCharsets.UTF_8);
    runGit(repository, "add", ".");
    runGit(repository, "commit", "-m", "simple fixture");
    return new Fixture(repository, runGit(repository, "rev-parse", "HEAD").trim());
  }

  private Fixture createByteBudgetFixture() throws Exception {
    Path repository = initialiseRepository("byte-budget-repository");
    Files.writeString(repository.resolve("a-first.txt"), "first\n", StandardCharsets.UTF_8);
    Files.write(repository.resolve("b-empty.txt"), new byte[0]);
    Files.writeString(repository.resolve("c-next.txt"), "next\n", StandardCharsets.UTF_8);
    runGit(repository, "add", ".");
    runGit(repository, "commit", "-m", "byte budget fixture");
    return new Fixture(repository, runGit(repository, "rev-parse", "HEAD").trim());
  }

  private Path initialiseRepository(String name) throws Exception {
    Path repository = temporaryDirectory.resolve(name);
    runGit(temporaryDirectory, "init", repository.toString());
    runGit(repository, "config", "user.name", "Test User");
    runGit(repository, "config", "user.email", "test@example.invalid");
    return repository;
  }

  private String runGit(Path directory, String... arguments) throws Exception {
    java.util.ArrayList<String> command = new java.util.ArrayList<>();
    command.add("git");
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
    int exitCode = process.waitFor();
    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(exitCode).withFailMessage("git stderr: %s", stderr).isZero();
    return stdout;
  }

  private static List<String> paths(SourcePreparationResult result) {
    return result.entries().stream().map(SourceEntry::relativePath).toList();
  }

  private static SourceEntry entry(SourcePreparationResult result, String path) {
    return result.entries().stream()
        .filter(candidate -> candidate.relativePath().equals(path))
        .findFirst()
        .orElseThrow();
  }

  private static SourceIssue issueFor(SourcePreparationResult result, String path) {
    SourceEntry sourceEntry = entry(result, path);
    return result.issues().stream()
        .filter(issue -> sourceEntry.issueIds().contains(issue.issueId()))
        .findFirst()
        .orElseThrow();
  }

  private static byte[] stagedBytes(Path outputRoot, String relativePath) throws IOException {
    return Files.readAllBytes(outputRoot.resolve("source-preparation-blobs").resolve(relativePath));
  }

  private record Fixture(Path repository, String commitId) {}

  private static final class RecordingFixedGitObjectAccess implements FixedGitObjectAccess {
    private final FixedGitObjectAccess delegate;
    private final Set<String> failingPaths;
    private final Set<String> sourceReadFailingPaths;
    private final Set<String> openedPaths = new java.util.LinkedHashSet<>();
    private int openCalls;

    private RecordingFixedGitObjectAccess(FixedGitObjectAccess delegate, Set<String> failingPaths) {
      this(delegate, failingPaths, Set.of());
    }

    private RecordingFixedGitObjectAccess(
        FixedGitObjectAccess delegate,
        Set<String> failingPaths,
        Set<String> sourceReadFailingPaths) {
      this.delegate = delegate;
      this.failingPaths = Set.copyOf(failingPaths);
      this.sourceReadFailingPaths = Set.copyOf(sourceReadFailingPaths);
    }

    @Override
    public Session open(Path repository, String exactCommitId) throws IOException {
      openCalls++;
      Session session = delegate.open(repository, exactCommitId);
      return new Session() {
        private final Map<String, String> pathsByObjectId = new HashMap<>();

        @Override
        public String treeObjectId() {
          return session.treeObjectId();
        }

        @Override
        public DirectoryStream<TreeEntry> entries() throws IOException {
          DirectoryStream<TreeEntry> entries = session.entries();
          return new DirectoryStream<>() {
            @Override
            public Iterator<TreeEntry> iterator() {
              Iterator<TreeEntry> iterator = entries.iterator();
              return new Iterator<>() {
                @Override
                public boolean hasNext() {
                  return iterator.hasNext();
                }

                @Override
                public TreeEntry next() {
                  TreeEntry entry = iterator.next();
                  pathsByObjectId.put(entry.objectId(), entry.path());
                  return entry;
                }
              };
            }

            @Override
            public void close() throws IOException {
              entries.close();
            }
          };
        }

        @Override
        public BlobInput openBlob(String objectId) throws IOException {
          String path = pathsByObjectId.get(objectId);
          if (path != null) {
            openedPaths.add(path);
          }
          if (path != null && failingPaths.contains(path)) {
            throw new IOException("injected blob read failure for " + path);
          }
          FixedGitObjectAccess.BlobInput blob = session.openBlob(objectId);
          if (path == null || !sourceReadFailingPaths.contains(path)) {
            return blob;
          }
          return new FixedGitObjectAccess.BlobInput(
              blob.sizeBytes(),
              new FilterInputStream(blob.stream()) {
                private boolean deliveredByte;

                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                  if (deliveredByte) {
                    throw new IOException("injected blob stream failure for " + path);
                  }
                  deliveredByte = true;
                  return super.read(bytes, offset, Math.min(length, 1));
                }
              });
        }

        @Override
        public void close() throws IOException {
          session.close();
        }
      };
    }

    private Set<String> openedPaths() {
      return openedPaths;
    }

    private int openCalls() {
      return openCalls;
    }
  }

  private static final class CloseFailingBlobSink implements SourcePreparationBlobSink {
    private final SourcePreparationBlobSink delegate;
    private final String failingPath;

    private CloseFailingBlobSink(SourcePreparationBlobSink delegate, String failingPath) {
      this.delegate = delegate;
      this.failingPath = failingPath;
    }

    @Override
    public SourcePreparationBlobWriter begin(String relativePath, long expectedSizeBytes)
        throws IOException {
      SourcePreparationBlobWriter writer = delegate.begin(relativePath, expectedSizeBytes);
      if (!relativePath.equals(failingPath)) {
        return writer;
      }
      return new SourcePreparationBlobWriter() {
        @Override
        public void write(ByteBuffer chunk) throws IOException {
          writer.write(chunk);
        }

        @Override
        public ArtifactReference complete() throws IOException {
          return writer.complete();
        }

        @Override
        public void close() throws IOException {
          writer.close();
          throw new IOException("injected staged writer close failure for " + failingPath);
        }
      };
    }
  }
}
