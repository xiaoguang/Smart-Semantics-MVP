package org.sourceanalysis.app.capture.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceIssue;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationLimits;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadinessEvaluator;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;
import org.sourceanalysis.app.analysis.inventory.SourceVersionCalculator;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Flow-level source-preparation contracts using test-only recording/delegating adapters. */
class DirectorySourcePreparationTest {

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void resolveTemporaryRootThroughFilesystemLinks() throws IOException {
    // Surefire may expose the temporary root through /var -> /private/var on macOS. Production
    // deliberately rejects that spelling for no-follow safety, so fixtures must use the physical
    // root while the dedicated symlink-root case below remains an intentional security test.
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void capturesDefaultDirectoryRangeAndRawBytesWithGitPolicyAndRealOutputRoot() throws Exception {
    Path sourceRoot = temporaryDirectory.resolve("source");
    Path outputRoot = temporaryDirectory.resolve("prepared-output");
    Files.createDirectories(sourceRoot.resolve("src"));
    Files.createDirectories(sourceRoot.resolve("target"));
    Files.createDirectories(sourceRoot.resolve("node_modules/pkg"));
    Files.createDirectories(sourceRoot.resolve(".git/objects"));
    Files.write(
        sourceRoot.resolve("src/Catalogue.java"),
        "class C {}\r\n".getBytes(StandardCharsets.UTF_8));
    Files.write(sourceRoot.resolve(".env"), "TOKEN=literal\n".getBytes(StandardCharsets.UTF_8));
    Files.write(
        sourceRoot.resolve("target/generated.xml"),
        "<generated/>\n".getBytes(StandardCharsets.UTF_8));
    Files.write(
        sourceRoot.resolve("node_modules/pkg/index.js"),
        "module.exports = 1;\n".getBytes(StandardCharsets.UTF_8));
    Files.write(sourceRoot.resolve("empty.txt"), new byte[0]);
    Files.write(sourceRoot.resolve("media.txt"), new byte[] {0, 1, 2, 3});
    byte[] bomText = utf8Bom("class Bom {}\r\n");
    Files.write(sourceRoot.resolve("bom.java"), bomText);
    byte[] invalidUtf8 = new byte[] {(byte) 0xc3, (byte) 0x28};
    Files.write(sourceRoot.resolve("invalid-utf8.txt"), invalidUtf8);
    byte[] largeUnicodeBytes = largeUnicodeBytes();
    Files.write(sourceRoot.resolve("large-unicode.txt"), largeUnicodeBytes);
    Files.write(sourceRoot.resolve(".git/objects/private"), new byte[] {0, 1, 2});

    // The extension and archive structure are deliberately real: classification still follows
    // bytes,
    // and the reader must not unpack or execute the archive.
    byte[] actualZip = actualZipBytes();
    Files.write(sourceRoot.resolve("archive.zip"), actualZip);

    SourcePreparationRequest request =
        newRequest(sourceRoot, new SourcePreparationLimits(32, 1_000_000L));
    RecordingBlobSink sink = RecordingBlobSink.under(outputRoot);
    SourcePreparationResult result =
        new DirectorySourceOriginReader(outputRoot).read(request, sink);

    assertThat(paths(result))
        .contains(
            ".env",
            "archive.zip",
            "bom.java",
            "empty.txt",
            "invalid-utf8.txt",
            "large-unicode.txt",
            "media.txt",
            "node_modules/pkg/index.js",
            "src/Catalogue.java",
            "target/generated.xml",
            ".git");
    assertThat(entry(result, "src/Catalogue.java").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
    assertThat(entry(result, "media.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_MEDIA);
    assertThat(entry(result, "bom.java").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
    assertThat(entry(result, "bom.java").textEncoding()).isEqualTo("UTF-8");
    assertThat(entry(result, "invalid-utf8.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_MEDIA);
    SourceEntry largeUnicode = entry(result, "large-unicode.txt");
    assertThat(largeUnicode.disposition()).isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
    assertThat(largeUnicodeBytes.length).isGreaterThan(8192);
    assertThat(largeUnicode.sizeBytes()).isEqualTo((long) largeUnicodeBytes.length);
    Sha256Digest largeUnicodeDigest = Sha256Digest.parse(sha256(largeUnicodeBytes));
    assertThat(largeUnicode.sha256()).isEqualTo(largeUnicodeDigest);
    assertThat(largeUnicode.fileId())
        .isEqualTo(
            SourceVersionCalculator.fileId(
                "large-unicode.txt",
                largeUnicodeBytes.length,
                largeUnicodeDigest,
                largeUnicode.originAttributes()));
    assertThat(entry(result, "archive.zip").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_MEDIA);
    assertThat(entry(result, ".git").disposition())
        .isEqualTo(SourceEntry.Disposition.SKIPPED_GIT_METADATA);
    assertThat(result.unknownSubtrees()).contains(".git");
    assertThat(sink.bytes("src/Catalogue.java"))
        .containsExactly("class C {}\r\n".getBytes(StandardCharsets.UTF_8));
    assertThat(sink.bytes("empty.txt")).isEmpty();
    assertThat(sink.bytes("bom.java")).containsExactly(bomText);
    assertThat(sink.bytes("invalid-utf8.txt")).containsExactly(invalidUtf8);
    assertThat(sink.bytes("large-unicode.txt")).containsExactly(largeUnicodeBytes);
    assertThat(sink.bytes("archive.zip")).containsExactly(actualZip);
    assertThat(SourcePreparationReadinessEvaluator.assess(result).readiness())
        .isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
  }

  @Test
  void excludesDeclaredDirectoryWithoutListingItAndRetainsUnmatchedExactDeclaration()
      throws Exception {
    Path sourceRoot = temporaryDirectory.resolve("source");
    Path outputRoot = temporaryDirectory.resolve("output");
    Files.createDirectories(sourceRoot.resolve("src"));
    Files.createDirectories(sourceRoot.resolve("vendor/private/deep"));
    Files.writeString(sourceRoot.resolve("src/keep.java"), "keep\n");
    Files.writeString(sourceRoot.resolve("vendor/private/deep/secret.java"), "secret\n");

    SourcePreparationTarget excludedDirectory =
        new SourcePreparationTarget("vendor/private", SourcePreparationTarget.Kind.DIRECTORY);
    SourcePreparationTarget missingDirectory =
        new SourcePreparationTarget("docs/not-present", SourcePreparationTarget.Kind.DIRECTORY);
    RecordingDirectoryAccess access = new RecordingDirectoryAccess(new JdkDirectorySourceAccess());
    SourcePreparationResult result =
        new DirectorySourceOriginReader(access, outputRoot)
            .read(
                newRequest(
                    sourceRoot,
                    new SourcePreparationLimits(16, 1_000_000L),
                    List.of(excludedDirectory, missingDirectory)),
                RecordingBlobSink.under(outputRoot));

    SourceEntry excluded = entry(result, "vendor/private");
    assertThat(excluded.disposition()).isEqualTo(SourceEntry.Disposition.EXCLUDED_BY_USER);
    assertThat(excluded.exclusion().coveredPath()).isEqualTo("vendor/private");
    assertThat(paths(result))
        .doesNotContain("vendor/private/deep", "vendor/private/deep/secret.java");
    assertThat(access.openedDirectories()).noneMatch(path -> path.endsWith("vendor/private"));
    assertThat(result.unknownSubtrees()).contains("vendor/private");
    assertThat(result.unmatchedExclusions()).containsExactly(missingDirectory);
    assertThat(SourcePreparationReadinessEvaluator.assess(result).readiness())
        .isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
  }

  @Test
  void neverFollowsLinksAndRejectsSourceOutputOverlapBeforeOpeningSource() throws Exception {
    Path sourceRoot = temporaryDirectory.resolve("source");
    Path outside = temporaryDirectory.resolve("outside");
    Path outputRoot = temporaryDirectory.resolve("output");
    Files.createDirectories(sourceRoot);
    Files.createDirectories(outside);
    Files.writeString(outside.resolve("sentinel.java"), "outside\n");
    Files.writeString(sourceRoot.resolve("safe.java"), "safe\n");
    Files.createSymbolicLink(
        sourceRoot.resolve("file-link.java"), outside.resolve("sentinel.java"));
    Files.createSymbolicLink(sourceRoot.resolve("directory-link"), outside);

    RecordingDirectoryAccess access = new RecordingDirectoryAccess(new JdkDirectorySourceAccess());
    SourcePreparationResult result =
        new DirectorySourceOriginReader(access, outputRoot)
            .read(
                newRequest(sourceRoot, new SourcePreparationLimits(16, 1_000_000L)),
                RecordingBlobSink.under(outputRoot));

    assertThat(entry(result, "file-link.java").disposition())
        .isEqualTo(SourceEntry.Disposition.SKIPPED_SYMLINK);
    assertThat(entry(result, "directory-link").disposition())
        .isEqualTo(SourceEntry.Disposition.SKIPPED_SYMLINK);
    assertThat(access.openedInputs())
        .doesNotContain("file-link.java", "directory-link", "sentinel.java");

    Path overlappingOutput = sourceRoot.resolve("output");
    RecordingDirectoryAccess overlapAccess =
        new RecordingDirectoryAccess(new JdkDirectorySourceAccess());
    SourcePreparationResult overlap =
        new DirectorySourceOriginReader(overlapAccess, overlappingOutput)
            .read(
                newRequest(sourceRoot, new SourcePreparationLimits(16, 1_000_000L)),
                RecordingBlobSink.under(overlappingOutput));
    assertThat(overlap.issues())
        .anySatisfy(issue -> assertThat(issue.category()).isEqualTo(SourceIssue.Category.REQUEST));
    assertThat(overlapAccess.openedDirectories()).isEmpty();
    assertThat(overlapAccess.openedInputs()).isEmpty();

    Path symlinkRoot = temporaryDirectory.resolve("source-root-link");
    Files.createSymbolicLink(symlinkRoot, outside);
    Path symlinkRootOutput = temporaryDirectory.resolve("symlink-root-output");
    RecordingDirectoryAccess symlinkRootAccess =
        new RecordingDirectoryAccess(new JdkDirectorySourceAccess());
    SourcePreparationResult symlinkRootResult =
        new DirectorySourceOriginReader(symlinkRootAccess, symlinkRootOutput)
            .read(
                newRequest(symlinkRoot, new SourcePreparationLimits(16, 1_000_000L)),
                RecordingBlobSink.under(symlinkRootOutput));
    assertThat(SourcePreparationReadinessEvaluator.assess(symlinkRootResult).readiness())
        .isEqualTo(SourcePreparationReadiness.BLOCKED);
    assertThat(symlinkRootAccess.openedInputs()).isEmpty();

    Path archiveRoot = temporaryDirectory.resolve("source-root.zip");
    Files.write(archiveRoot, actualZipBytes());
    Path archiveRootOutput = temporaryDirectory.resolve("archive-root-output");
    RecordingDirectoryAccess archiveRootAccess =
        new RecordingDirectoryAccess(new JdkDirectorySourceAccess());
    SourcePreparationResult archiveRootResult =
        new DirectorySourceOriginReader(archiveRootAccess, archiveRootOutput)
            .read(
                newRequest(archiveRoot, new SourcePreparationLimits(16, 1_000_000L)),
                RecordingBlobSink.under(archiveRootOutput));
    assertThat(SourcePreparationReadinessEvaluator.assess(archiveRootResult).readiness())
        .isEqualTo(SourcePreparationReadiness.BLOCKED);
    assertThat(archiveRootAccess.openedInputs()).isEmpty();
  }

  @Test
  void keepsSafeSiblingsAfterLocalFailuresButBlocksRootListingFailure() throws Exception {
    Path sourceRoot = temporaryDirectory.resolve("source");
    Path outputRoot = temporaryDirectory.resolve("output");
    Files.createDirectories(sourceRoot.resolve("src"));
    Files.createDirectories(sourceRoot.resolve("vendor/broken"));
    Files.writeString(sourceRoot.resolve("src/good.java"), "good\n");
    Files.writeString(sourceRoot.resolve("src/bad.java"), "bad\n");

    DirectorySourceAccess localFailures =
        new DelegatingDirectorySourceAccess(new JdkDirectorySourceAccess()) {
          @Override
          public DirectoryStream<Path> openDirectory(Path directory) throws IOException {
            if (directory.endsWith("vendor/broken")) {
              throw new IOException("injected local listing failure");
            }
            return super.openDirectory(directory);
          }

          @Override
          public InputStream openInput(Path path) throws IOException {
            if (path.endsWith("src/bad.java")) {
              throw new IOException("injected file read failure");
            }
            return super.openInput(path);
          }
        };
    SourcePreparationResult localResult =
        new DirectorySourceOriginReader(localFailures, outputRoot)
            .read(
                newRequest(sourceRoot, new SourcePreparationLimits(16, 1_000_000L)),
                RecordingBlobSink.under(outputRoot));

    assertThat(entry(localResult, "src/good.java").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
    assertThat(entry(localResult, "src/bad.java").disposition())
        .isEqualTo(SourceEntry.Disposition.UNAVAILABLE);
    assertThat(localResult.unknownSubtrees()).contains("vendor/broken");
    assertThat(localResult.issues())
        .extracting(SourceIssue::code)
        .contains(
            SourceIssue.Code.SOURCE_DIRECTORY_LIST_FAILED,
            SourceIssue.Code.SOURCE_ENTRY_READ_FAILED);
    assertThat(SourcePreparationReadinessEvaluator.assess(localResult).readiness())
        .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);

    DirectorySourceAccess rootFailure =
        new DelegatingDirectorySourceAccess(new JdkDirectorySourceAccess()) {
          @Override
          public DirectoryStream<Path> openDirectory(Path directory) throws IOException {
            if (directory.equals(sourceRoot)) {
              throw new IOException("injected root listing failure");
            }
            return super.openDirectory(directory);
          }
        };
    SourcePreparationResult rootResult =
        new DirectorySourceOriginReader(rootFailure, outputRoot)
            .read(
                newRequest(sourceRoot, new SourcePreparationLimits(16, 1_000_000L)),
                RecordingBlobSink.under(outputRoot));
    assertThat(rootResult.entries()).isEmpty();
    assertThat(rootResult.issues())
        .extracting(SourceIssue::code)
        .containsExactly(SourceIssue.Code.SOURCE_ROOT_LIST_FAILED);
    assertThat(SourcePreparationReadinessEvaluator.assess(rootResult).readiness())
        .isEqualTo(SourcePreparationReadiness.BLOCKED);
  }

  @Test
  void keepsUnknownChildTypeAndOffersFileOrDirectoryActionsWhenAttributesCannotBeRead()
      throws Exception {
    Path sourceRoot = temporaryDirectory.resolve("source");
    Path outputRoot = temporaryDirectory.resolve("output");
    Path uncertainChild = sourceRoot.resolve("uncertain");
    Files.createDirectories(uncertainChild.resolve("nested"));
    Files.writeString(uncertainChild.resolve("nested/secret.java"), "secret\n");
    Files.writeString(sourceRoot.resolve("safe.java"), "safe\n");

    RecordingDirectoryAccess access =
        new RecordingDirectoryAccess(new JdkDirectorySourceAccess()) {
          @Override
          public BasicFileAttributes readAttributes(Path path) throws IOException {
            if (path.equals(uncertainChild)) {
              throw new IOException("injected child attribute failure");
            }
            return super.readAttributes(path);
          }
        };
    SourcePreparationResult result =
        new DirectorySourceOriginReader(access, outputRoot)
            .read(
                newRequest(sourceRoot, new SourcePreparationLimits(16, 1_000_000L)),
                RecordingBlobSink.under(outputRoot));

    SourceEntry uncertain = entry(result, "uncertain");
    assertThat(uncertain.entryKind()).isEqualTo(SourceEntry.Kind.UNKNOWN);
    assertThat(uncertain.disposition()).isEqualTo(SourceEntry.Disposition.UNAVAILABLE);
    assertThat(result.unknownSubtrees()).contains("uncertain");
    assertThat(entry(result, "safe.java").disposition())
        .isEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);
    assertThat(access.openedDirectories())
        .noneMatch(path -> path.equals(uncertainChild) || path.startsWith(uncertainChild));
    assertThat(access.openedInputs()).doesNotContain("secret.java").contains("safe.java");

    SourceIssue issue =
        result.issues().stream()
            .filter(candidate -> uncertain.issueIds().contains(candidate.issueId()))
            .findFirst()
            .orElseThrow();
    assertThat(issue.scope()).isEqualTo(SourceIssue.Scope.DIRECTORY);
    assertThat(issue.allowedActions())
        .contains(
            SourceIssue.AllowedAction.REFRESH_FILE,
            SourceIssue.AllowedAction.REFRESH_DIRECTORY,
            SourceIssue.AllowedAction.EXCLUDE_FILE,
            SourceIssue.AllowedAction.EXCLUDE_DIRECTORY);
  }

  @Test
  void recordsObservedStatChangesAndStopsAtResourceLimitWithoutReadingEverything()
      throws Exception {
    Path sourceRoot = temporaryDirectory.resolve("source");
    Path outputRoot = temporaryDirectory.resolve("output");
    Files.createDirectories(sourceRoot);
    Files.writeString(sourceRoot.resolve("changed.java"), "before\n");
    Files.writeString(sourceRoot.resolve("second.java"), "second\n");
    Files.writeString(sourceRoot.resolve("third.java"), "third\n");

    DirectorySourceAccess changedOnClose =
        new DelegatingDirectorySourceAccess(new JdkDirectorySourceAccess()) {
          @Override
          public InputStream openInput(Path path) throws IOException {
            InputStream delegate = super.openInput(path);
            if (!path.endsWith("changed.java")) {
              return delegate;
            }
            return new FilterInputStream(delegate) {
              @Override
              public void close() throws IOException {
                super.close();
                Files.writeString(path, "after-with-a-different-size\n");
                Files.setLastModifiedTime(
                    path, FileTime.fromMillis(System.currentTimeMillis() + 2_000L));
              }
            };
          }
        };
    SourcePreparationResult changed =
        new DirectorySourceOriginReader(changedOnClose, outputRoot)
            .read(
                newRequest(sourceRoot, new SourcePreparationLimits(16, 1_000_000L)),
                RecordingBlobSink.under(outputRoot));
    assertThat(changed.issues())
        .extracting(SourceIssue::code)
        .containsAnyOf(
            SourceIssue.Code.SOURCE_SIZE_MISMATCH, SourceIssue.Code.SOURCE_CHANGED_DURING_READ);
    assertThat(entry(changed, "changed.java").disposition())
        .isNotEqualTo(SourceEntry.Disposition.VERIFIED_TEXT);

    SourcePreparationResult limited =
        new DirectorySourceOriginReader(new JdkDirectorySourceAccess(), outputRoot)
            .read(
                newRequest(sourceRoot, new SourcePreparationLimits(1, 1_000_000L)),
                RecordingBlobSink.under(outputRoot));
    assertThat(limited.inspectionStatus())
        .isEqualTo(SourcePreparationResult.InspectionStatus.ABORTED);
    assertThat(limited.enumerationComplete()).isFalse();
    assertThat(limited.issues())
        .extracting(SourceIssue::code)
        .contains(SourceIssue.Code.VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED);
    assertThat(SourcePreparationReadinessEvaluator.assess(limited).readiness())
        .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);

    Path byteLimitedRoot = temporaryDirectory.resolve("byte-limited-source");
    Path byteLimitedOutput = temporaryDirectory.resolve("byte-limited-output");
    Files.createDirectories(byteLimitedRoot);
    byte[] overLimit = largeUnicodeBytes();
    Files.write(byteLimitedRoot.resolve("over-limit.txt"), overLimit);
    SourcePreparationResult byteLimited =
        new DirectorySourceOriginReader(new JdkDirectorySourceAccess(), byteLimitedOutput)
            .read(
                newRequest(byteLimitedRoot, new SourcePreparationLimits(16, 8L)),
                RecordingBlobSink.under(byteLimitedOutput));
    assertThat(byteLimited.inspectionStatus())
        .isEqualTo(SourcePreparationResult.InspectionStatus.ABORTED);
    assertThat(byteLimited.enumerationComplete()).isFalse();
    assertThat(byteLimited.issues())
        .extracting(SourceIssue::code)
        .contains(SourceIssue.Code.VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED);
    assertThat(entry(byteLimited, "over-limit.txt").disposition())
        .isNotIn(SourceEntry.Disposition.VERIFIED_TEXT, SourceEntry.Disposition.VERIFIED_MEDIA);
    assertThat(SourcePreparationReadinessEvaluator.assess(byteLimited).readiness())
        .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
  }

  @Test
  void distinguishesSourceReadFailureFromOutputWriterFailure() throws Exception {
    Path sourceRoot = temporaryDirectory.resolve("source");
    Path outputRoot = temporaryDirectory.resolve("output");
    Files.createDirectories(sourceRoot);
    Files.writeString(sourceRoot.resolve("good.java"), "good\n");

    SourcePreparationResult outputFailure =
        new DirectorySourceOriginReader(new JdkDirectorySourceAccess(), outputRoot)
            .read(
                newRequest(sourceRoot, new SourcePreparationLimits(16, 1_000_000L)),
                new FailingOutputBlobSink(outputRoot, "good.java"));
    assertThat(outputFailure.issues())
        .extracting(SourceIssue::code)
        .contains(SourceIssue.Code.SOURCE_OUTPUT_FAILED);
    assertThat(SourcePreparationReadinessEvaluator.assess(outputFailure).readiness())
        .isEqualTo(SourcePreparationReadiness.BLOCKED);
  }

  private static SourcePreparationRequest newRequest(
      Path sourceRoot, SourcePreparationLimits limits) {
    return newRequest(sourceRoot, limits, List.of());
  }

  private static SourcePreparationRequest newRequest(
      Path sourceRoot, SourcePreparationLimits limits, List<SourcePreparationTarget> exclusions) {
    String digest = "a".repeat(64);
    ArtifactReference policy =
        new ArtifactReference(
            ArtifactId.parse("policy-bundle:" + digest), Sha256Digest.parse(digest));
    return new SourcePreparationRequest(
        SourcePreparationOperation.NEW,
        new DirectorySourceOrigin("directory-fixture", sourceRoot.toAbsolutePath().normalize()),
        null,
        List.of(),
        exclusions,
        exclusions,
        limits,
        policy);
  }

  private static List<String> paths(SourcePreparationResult result) {
    return result.entries().stream().map(SourceEntry::relativePath).sorted().toList();
  }

  private static SourceEntry entry(SourcePreparationResult result, String path) {
    return result.entries().stream()
        .filter(value -> value.relativePath().equals(path))
        .findFirst()
        .orElseThrow();
  }

  private static byte[] utf8Bom(String text) {
    byte[] body = text.getBytes(StandardCharsets.UTF_8);
    byte[] result = new byte[3 + body.length];
    result[0] = (byte) 0xef;
    result[1] = (byte) 0xbb;
    result[2] = (byte) 0xbf;
    System.arraycopy(body, 0, result, 3, body.length);
    return result;
  }

  private static byte[] actualZipBytes() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("entry.txt"));
      zip.write("archive payload\r\n".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return bytes.toByteArray();
  }

  private static byte[] largeUnicodeBytes() {
    StringBuilder text = new StringBuilder();
    for (int index = 0; index < 2_500; index++) {
      text.append("跨块🙂é\r\n");
    }
    return text.toString().getBytes(StandardCharsets.UTF_8);
  }

  // Test-only adapters; these implement the approved package-private JDK-only seam.
  private static class JdkDirectorySourceAccess implements DirectorySourceAccess {
    @Override
    public DirectoryStream<Path> openDirectory(Path directory) throws IOException {
      return Files.newDirectoryStream(directory);
    }

    @Override
    public BasicFileAttributes readAttributes(Path path) throws IOException {
      return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public InputStream openInput(Path path) throws IOException {
      return Files.newInputStream(path);
    }
  }

  private abstract static class DelegatingDirectorySourceAccess implements DirectorySourceAccess {
    private final DirectorySourceAccess delegate;

    DelegatingDirectorySourceAccess(DirectorySourceAccess delegate) {
      this.delegate = delegate;
    }

    @Override
    public DirectoryStream<Path> openDirectory(Path directory) throws IOException {
      return delegate.openDirectory(directory);
    }

    @Override
    public BasicFileAttributes readAttributes(Path path) throws IOException {
      return delegate.readAttributes(path);
    }

    @Override
    public InputStream openInput(Path path) throws IOException {
      return delegate.openInput(path);
    }
  }

  private static class RecordingDirectoryAccess extends DelegatingDirectorySourceAccess {
    private final Set<String> opened = new HashSet<>();
    private final Set<Path> directories = new HashSet<>();

    RecordingDirectoryAccess(DirectorySourceAccess delegate) {
      super(delegate);
    }

    @Override
    public DirectoryStream<Path> openDirectory(Path path) throws IOException {
      directories.add(path.toAbsolutePath().normalize());
      return super.openDirectory(path);
    }

    @Override
    public InputStream openInput(Path path) throws IOException {
      opened.add(path.getFileName().toString());
      return super.openInput(path);
    }

    Set<String> openedInputs() {
      return opened;
    }

    Set<Path> openedDirectories() {
      return directories;
    }
  }

  // The following sink helpers are test-only and write real staged bytes beneath outputRoot.
  private static final class RecordingBlobSink implements SourcePreparationBlobSink {
    static RecordingBlobSink under(Path outputRoot) throws IOException {
      Files.createDirectories(outputRoot);
      return new RecordingBlobSink(outputRoot);
    }

    private final Path outputRoot;
    private final Map<String, byte[]> bytes = new java.util.HashMap<>();

    private RecordingBlobSink(Path outputRoot) {
      this.outputRoot = outputRoot;
    }

    @Override
    public SourcePreparationBlobWriter begin(String path, long size) throws IOException {
      Path destination = outputRoot.resolve("blobs").resolve(path).normalize();
      if (!destination.startsWith(outputRoot.toAbsolutePath().normalize())) {
        throw new IOException("staged blob escaped output root");
      }
      Files.createDirectories(destination.getParent());
      return new SourcePreparationBlobWriter() {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean complete;

        @Override
        public void write(ByteBuffer chunk) {
          byte[] bytes = new byte[chunk.remaining()];
          chunk.get(bytes);
          buffer.writeBytes(bytes);
        }

        @Override
        public ArtifactReference complete() throws IOException {
          if (complete) {
            throw new IOException("blob writer completed twice");
          }
          complete = true;
          byte[] value = buffer.toByteArray();
          Files.write(destination, value);
          bytes.put(path, value);
          String digest = sha256(value);
          return new ArtifactReference(
              ArtifactId.parse("source-blob:" + digest), Sha256Digest.parse(digest));
        }

        @Override
        public void close() {}
      };
    }

    byte[] bytes(String path) {
      return java.util.Objects.requireNonNull(
          bytes.get(path), "blob writer did not complete for " + path);
    }
  }

  private static final class FailingOutputBlobSink implements SourcePreparationBlobSink {
    private final Path outputRoot;
    private final String failingPath;

    FailingOutputBlobSink(Path outputRoot, String failingPath) throws IOException {
      this.outputRoot = outputRoot;
      this.failingPath = failingPath;
      Files.createDirectories(outputRoot);
    }

    @Override
    public SourcePreparationBlobWriter begin(String path, long size) throws IOException {
      throw new IOException("injected output failure for " + failingPath);
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
