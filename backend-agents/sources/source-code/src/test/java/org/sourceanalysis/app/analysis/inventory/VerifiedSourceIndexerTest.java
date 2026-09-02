package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequest;
import org.sourceanalysis.app.capture.localgit.LocalGitCommitCaptureAdapter;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceFile;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;

class VerifiedSourceIndexerTest {

  private static final CanonicalJsonCodec CANONICAL_JSON = new CanonicalJsonCodec();
  private static final ArtifactReference CAPTURE_POLICY = reference("capture-policy", 'a');
  private static final ArtifactReference RESOURCE_BUDGET = reference("resource-budget", 'b');

  @TempDir Path temporaryDirectory;

  @Test
  void reopensRegisteredBytesAndProducesStableTextAndMediaFileIdentities() throws Exception {
    CapturedFixture fixture = capturedFixture();
    VerifiedSourceIndex index =
        new VerifiedSourceIndexer().index(fixture.admittedRequest(), fixture.sourceRegistry());

    assertThat(index.snapshotId()).isEqualTo(fixture.capture().snapshotId());
    assertThat(index.requestIdentity()).isEqualTo(fixture.admittedRequest().requestIdentity());
    assertThat(index.verifiedRegularFileCount()).isEqualTo(2);
    assertThat(index.analyzableTextFileCount()).isEqualTo(1);
    assertThat(index.nonAnalyzableMediaFileCount()).isEqualTo(1);
    assertThat(index.verifiedFiles())
        .extracting(VerifiedSourceFile::path)
        .containsExactly("src/example/Catalogue.java", "static/logo.bin");
    assertThat(index.verifiedFiles())
        .filteredOn(file -> file.path().equals("src/example/Catalogue.java"))
        .singleElement()
        .satisfies(
            file -> {
              assertThat(file.fileId()).isEqualTo(fixture.textFileId());
              assertThat(file.textEncoding()).isEqualTo("UTF-8");
              assertThat(file.lineIndexDigest()).isNotNull();
            });
    assertThat(index.verifiedFiles())
        .filteredOn(file -> file.path().equals("static/logo.bin"))
        .singleElement()
        .satisfies(
            file -> {
              assertThat(file.fileId()).isEqualTo(fixture.binaryFileId());
              assertThat(file.textEncoding()).isNull();
              assertThat(file.lineIndexDigest()).isNull();
            });
    assertThat(index.toString()).doesNotContain(fixture.workspace().toString());
  }

  @Test
  void rejectsARegisteredBlobWhoseBytesNoLongerMatchTheFrozenManifest() throws Exception {
    CapturedFixture fixture = capturedFixture();
    RegisteredSourceFile textFile =
        fixture.capture().manifestEntries().stream()
            .filter(file -> file.path().equals("src/example/Catalogue.java"))
            .findFirst()
            .orElseThrow();
    Files.write(
        fixture
            .workspace()
            .resolve("snapshots")
            .resolve(fixture.capture().snapshotId())
            .resolve("blobs")
            .resolve(textFile.sha256().value()),
        "package example;\nfinal class Catalogux {}\n".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(
            () ->
                new VerifiedSourceIndexer()
                    .index(fixture.admittedRequest(), fixture.sourceRegistry()))
        .isInstanceOfSatisfying(
            VerifiedSourceIndexException.class,
            failure -> assertThat(failure.code()).isEqualTo("SOURCE_HASH_MISMATCH"));
  }

  private CapturedFixture capturedFixture() throws Exception {
    Path physicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Path repository = physicalTemporaryDirectory.resolve("repository");
    initialiseRepository(repository);
    Path sourceDirectory = repository.resolve("src/example");
    Files.createDirectories(sourceDirectory);
    Files.writeString(
        sourceDirectory.resolve("Catalogue.java"),
        "package example;\nfinal class Catalogue {}\n",
        StandardCharsets.UTF_8);
    Path staticDirectory = repository.resolve("static");
    Files.createDirectories(staticDirectory);
    Files.write(staticDirectory.resolve("logo.bin"), new byte[] {0, 1, 2, 3});
    runGit(repository, "add", ".");
    runGit(repository, "commit", "-m", "fixture");
    String commitId = runGit(repository, "rev-parse", "HEAD").trim();
    Path workspace = physicalTemporaryDirectory.resolve("capture-workspace");
    SourceRegistrationReference registration =
        new LocalGitCommitCaptureAdapter(workspace, Path.of("/usr/bin/git"))
            .capture(
                new LocalGitCaptureRequest(
                    "https://example.invalid/customer/catalogue.git",
                    commitId,
                    repository,
                    CAPTURE_POLICY,
                    RESOURCE_BUDGET));
    LocalGitSourceRegistry registry = new LocalGitSourceRegistry(workspace);
    RegisteredSourceCapture capture = registry.reopen(registration.sourceRegistrationId());

    List<CapturedRegularFile> admittedFiles =
        capture.manifestEntries().stream()
            .map(VerifiedSourceIndexerTest::capturedFile)
            .sorted(Comparator.comparing(CapturedRegularFile::path))
            .toList();
    List<ArtifactId> textFileIds =
        admittedFiles.stream()
            .filter(file -> file.analysisDisposition() == SourceAnalysisDisposition.ANALYZABLE_TEXT)
            .map(CapturedRegularFile::fileId)
            .toList();
    List<ArtifactId> mediaFileIds =
        admittedFiles.stream()
            .filter(
                file ->
                    file.analysisDisposition() == SourceAnalysisDisposition.NON_ANALYZABLE_MEDIA)
            .map(CapturedRegularFile::fileId)
            .toList();
    ArtifactReference frozenRequest = reference("frozen-request", 'c');
    AdmittedSourceRequest admittedRequest =
        new AdmittedSourceRequest(
            "run-request:" + "d".repeat(64),
            registration.sourceRegistrationId(),
            capture.declaredRepositoryIdentity(),
            capture.commitId(),
            frozenRequest,
            capture.captureReceiptRef(),
            capture.snapshotManifestRef(),
            InventoryScope.completeCapture(),
            true,
            admittedFiles.size(),
            admittedFiles,
            textFileIds,
            mediaFileIds,
            new RunRequestControls(
                reference("capability-profile", 'e'),
                RESOURCE_BUDGET,
                reference("toolchain", 'f'),
                reference("schema-bundle", '1'),
                reference("prompt-bundle", '2'),
                reference("artifact-policy-registry", '3')));
    ArtifactId textFileId =
        admittedFiles.stream()
            .filter(file -> file.path().equals("src/example/Catalogue.java"))
            .findFirst()
            .orElseThrow()
            .fileId();
    ArtifactId binaryFileId =
        admittedFiles.stream()
            .filter(file -> file.path().equals("static/logo.bin"))
            .findFirst()
            .orElseThrow()
            .fileId();
    return new CapturedFixture(
        workspace, registry, capture, admittedRequest, textFileId, binaryFileId);
  }

  private static CapturedRegularFile capturedFile(RegisteredSourceFile file) {
    return new CapturedRegularFile(
        fileId(file.path(), file.gitMode(), file.sizeBytes(), file.sha256()),
        file.path(),
        file.gitMode(),
        file.mediaType(),
        file.sizeBytes(),
        file.sha256(),
        SourceAnalysisDisposition.valueOf(file.analysisDisposition()),
        file.textEncoding());
  }

  private static ArtifactId fileId(
      String path, String gitMode, long sizeBytes, Sha256Digest sha256) {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("path", path);
    identity.put("gitMode", gitMode);
    identity.put("sizeBytes", sizeBytes);
    identity.put("sha256", sha256.value());
    byte[] identityBytes = CANONICAL_JSON.encodeCanonical(identity).copyToByteArray();
    return ArtifactId.parse(
        "file:"
            + sha256Hex(concatenate(frame("verified-source-file-id-v1"), frame(identityBytes))));
  }

  private static ArtifactReference reference(String prefix, char digit) {
    String digest = String.valueOf(digit).repeat(64);
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest), Sha256Digest.parse(digest));
  }

  private void initialiseRepository(Path repository) throws Exception {
    runGit(temporaryDirectory, "init", repository.toString());
    runGit(repository, "config", "user.name", "Test User");
    runGit(repository, "config", "user.email", "test@example.invalid");
  }

  private String runGit(Path directory, String... arguments) throws Exception {
    List<String> command = new ArrayList<>();
    command.add("git");
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
    int exitCode = process.waitFor();
    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(exitCode).withFailMessage("git stderr: %s", stderr).isZero();
    return stdout;
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static String sha256Hex(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record CapturedFixture(
      Path workspace,
      LocalGitSourceRegistry sourceRegistry,
      RegisteredSourceCapture capture,
      AdmittedSourceRequest admittedRequest,
      ArtifactId textFileId,
      ArtifactId binaryFileId) {}
}
