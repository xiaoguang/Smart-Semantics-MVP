package org.sourceanalysis.app.capture.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
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
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Regression RED for counting failed source attempts and bytes toward preparation limits. */
class DirectorySourcePreparationBudgetTest {

  private static final ArtifactReference POLICY =
      new ArtifactReference(
          ArtifactId.parse(
              "source-preparation-policy:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
          Sha256Digest.parse("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void resolveTemporaryRootThroughFilesystemLinks() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void failedInputAttemptConsumesFileBudgetAndLeavesLaterFileUnchecked() throws Exception {
    Path sourceRoot = temporaryDirectory.resolve("source");
    Path outputRoot = temporaryDirectory.resolve("output");
    Files.createDirectories(sourceRoot);
    Files.writeString(sourceRoot.resolve("a-fails.txt"), "failure\n");
    Files.writeString(sourceRoot.resolve("b-would-succeed.txt"), "success\n");
    BudgetAccess access = new BudgetAccess(BudgetFailure.OPEN_FAILURE, "a-fails.txt");

    SourcePreparationResult result =
        new DirectorySourceOriginReader(access, outputRoot)
            .read(
                request(sourceRoot, new SourcePreparationLimits(1, 1_000_000L)),
                new StagedSourcePreparationBlobSink(outputRoot));

    assertThat(entry(result, "a-fails.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.UNAVAILABLE);
    assertThat(issueFor(result, "a-fails.txt").code())
        .isEqualTo(SourceIssue.Code.SOURCE_ENTRY_READ_FAILED);
    assertThat(entry(result, "b-would-succeed.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.UNCHECKED);
    assertThat(issueFor(result, "b-would-succeed.txt").code())
        .isEqualTo(SourceIssue.Code.VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED);
    assertThat(access.openedInputs()).containsExactly("a-fails.txt");
    assertThat(SourcePreparationReadinessEvaluator.assess(result).readiness())
        .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
  }

  @Test
  void bytesReadBeforeInputFailureConsumeByteBudgetAndDoNotAdmitTheNextFile() throws Exception {
    Path sourceRoot = temporaryDirectory.resolve("source");
    Path outputRoot = temporaryDirectory.resolve("output");
    Files.createDirectories(sourceRoot);
    Files.writeString(sourceRoot.resolve("a-fails.txt"), "x");
    Files.writeString(sourceRoot.resolve("b-would-succeed.txt"), "y");
    BudgetAccess access = new BudgetAccess(BudgetFailure.FAIL_AFTER_ONE_BYTE, "a-fails.txt");

    SourcePreparationResult result =
        new DirectorySourceOriginReader(access, outputRoot)
            .read(
                request(sourceRoot, new SourcePreparationLimits(8, 1L)),
                new StagedSourcePreparationBlobSink(outputRoot));

    assertThat(access.bytesDelivered()).isEqualTo(1L);
    assertThat(entry(result, "a-fails.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.UNAVAILABLE);
    assertThat(issueFor(result, "a-fails.txt").code())
        .isEqualTo(SourceIssue.Code.SOURCE_ENTRY_READ_FAILED);
    assertThat(entry(result, "b-would-succeed.txt").disposition())
        .isEqualTo(SourceEntry.Disposition.UNCHECKED);
    assertThat(issueFor(result, "b-would-succeed.txt").code())
        .isEqualTo(SourceIssue.Code.VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED);
    assertThat(access.openedInputs()).containsExactly("a-fails.txt");
    assertThat(SourcePreparationReadinessEvaluator.assess(result).readiness())
        .isEqualTo(SourcePreparationReadiness.NEEDS_DECISION);
  }

  @Test
  void directoryResourceLimitOnlyOffersRestartingThePreparation() throws Exception {
    Path sourceRoot = temporaryDirectory.resolve("source");
    Path outputRoot = temporaryDirectory.resolve("output");
    Files.createDirectories(sourceRoot);
    Files.writeString(sourceRoot.resolve("a-too-large.txt"), "too large\n");
    Files.writeString(sourceRoot.resolve("b-not-read.txt"), "not read\n");

    SourcePreparationResult result =
        new DirectorySourceOriginReader(new JdkDirectorySourceAccess(), outputRoot)
            .read(
                request(sourceRoot, new SourcePreparationLimits(8, 1L)),
                new StagedSourcePreparationBlobSink(outputRoot));

    assertThat(result.enumerationComplete()).isFalse();
    assertThat(issueFor(result, "a-too-large.txt").allowedActions())
        .containsExactly(SourceIssue.AllowedAction.NEW_PREPARATION);
  }

  private static SourcePreparationRequest request(Path sourceRoot, SourcePreparationLimits limits) {
    return new SourcePreparationRequest(
        SourcePreparationOperation.NEW,
        new DirectorySourceOrigin(
            "directory-budget-fixture", sourceRoot.toAbsolutePath().normalize()),
        null,
        List.of(),
        List.of(),
        List.of(),
        limits,
        POLICY);
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

  private enum BudgetFailure {
    OPEN_FAILURE,
    FAIL_AFTER_ONE_BYTE
  }

  private static final class BudgetAccess implements DirectorySourceAccess {
    private final DirectorySourceAccess delegate = new JdkDirectorySourceAccess();
    private final BudgetFailure failure;
    private final String failurePath;
    private final Set<String> opened = new java.util.LinkedHashSet<>();
    private long bytesDelivered;

    private BudgetAccess(BudgetFailure failure, String failurePath) {
      this.failure = failure;
      this.failurePath = failurePath;
    }

    @Override
    public java.nio.file.DirectoryStream<Path> openDirectory(Path directory) throws IOException {
      return delegate.openDirectory(directory);
    }

    @Override
    public BasicFileAttributes readAttributes(Path path) throws IOException {
      return delegate.readAttributes(path);
    }

    @Override
    public InputStream openInput(Path path) throws IOException {
      String name = path.getFileName().toString();
      opened.add(name);
      if (!name.equals(failurePath)) {
        return delegate.openInput(path);
      }
      if (failure == BudgetFailure.OPEN_FAILURE) {
        throw new IOException("injected open failure");
      }
      InputStream source = delegate.openInput(path);
      return new FilterInputStream(source) {
        private boolean delivered;

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
          if (delivered) {
            throw new IOException("injected failure after one byte");
          }
          int read = super.read(bytes, offset, Math.min(1, length));
          if (read > 0) {
            delivered = true;
            bytesDelivered += read;
          }
          return read;
        }
      };
    }

    private Set<String> openedInputs() {
      return opened;
    }

    private long bytesDelivered() {
      return bytesDelivered;
    }
  }
}
