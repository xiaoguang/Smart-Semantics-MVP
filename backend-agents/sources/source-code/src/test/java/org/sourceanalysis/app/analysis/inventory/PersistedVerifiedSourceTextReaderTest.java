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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryException;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.AnalysisStepReceipt;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequest;
import org.sourceanalysis.app.capture.localgit.LocalGitCommitCaptureAdapter;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;

class PersistedVerifiedSourceTextReaderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void reopensOnlyInventoryMembersFromARegisteredFrozenSnapshot() throws Exception {
    CapturedFixture captured = capturedFixture();
    PublishedInventory published = publishedInventory(captured);
    PersistedVerifiedSourceTextReader handle =
        new PersistedVerifiedSourceTextReader(
            new FixedAnalysisStepStore(published.reopened()), captured.registry());

    VerifiedSourceTextSet texts =
        handle.reopen(new VerifiedSourceInventoryReference(published.reference()));

    assertThat(texts.snapshotId()).isEqualTo(captured.capture().snapshotId());
    assertThat(texts.inventoryScopeKind()).isEqualTo("COMPLETE_CAPTURE");
    assertThat(texts.repositoryCompletionEligible()).isTrue();
    assertThat(texts.documents())
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document.path()).isEqualTo("pom.xml");
              assertThat(document.rawUtf8().copyToByteArray())
                  .isEqualTo("<project/>\n".getBytes(StandardCharsets.UTF_8));
            });
  }

  @Test
  void rejectsAnInventoryMemberWhoseMetadataDoesNotMatchTheRegisteredCapture() throws Exception {
    CapturedFixture captured = capturedFixture();
    PublishedInventory published = publishedInventory(captured);
    List<VerifiedCanonicalPayload> alteredPayloads = new ArrayList<>();
    for (VerifiedCanonicalPayload payload : published.reopened().semanticPayloads()) {
      if (!"source-inventory.jsonl".equals(payload.descriptor().fileName())) {
        alteredPayloads.add(payload);
        continue;
      }
      String altered =
          new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8)
              .replace("\"mediaType\":\"text/plain\"", "\"mediaType\":\"application/xml\"");
      ImmutableBytes bytes = ImmutableBytes.copyOf(altered.getBytes(StandardCharsets.UTF_8));
      alteredPayloads.add(
          new VerifiedCanonicalPayload(
              descriptor(
                  payload.descriptor().fileName(),
                  payload.descriptor().artifactType(),
                  payload.descriptor().schemaVersion(),
                  new ArtifactReference(
                      payload.descriptor().artifactId(), payload.descriptor().sha256()),
                  bytes),
              bytes));
    }
    ReopenedAnalysisStepPublication alteredPublication =
        new ReopenedAnalysisStepPublication(
            published.reference(), published.reopened().receipt(), alteredPayloads, null);
    PersistedVerifiedSourceTextReader handle =
        new PersistedVerifiedSourceTextReader(
            new FixedAnalysisStepStore(alteredPublication), captured.registry());

    assertThatThrownBy(
            () -> handle.reopen(new VerifiedSourceInventoryReference(published.reference())))
        .isInstanceOfSatisfying(
            ApplicationDiscoveryException.class,
            failure -> assertThat(failure.code()).isEqualTo("SNAPSHOT_REOPEN_MISMATCH"));
  }

  private CapturedFixture capturedFixture() throws Exception {
    Path physicalDirectory = temporaryDirectory.toRealPath();
    Path repository = physicalDirectory.resolve("repository");
    runGit(physicalDirectory, "init", repository.toString());
    runGit(repository, "config", "user.name", "Test User");
    runGit(repository, "config", "user.email", "test@example.invalid");
    Files.writeString(repository.resolve("pom.xml"), "<project/>\n", StandardCharsets.UTF_8);
    runGit(repository, "add", "pom.xml");
    runGit(repository, "commit", "-m", "fixture");
    String commit = runGit(repository, "rev-parse", "HEAD").trim();

    Path workspace = physicalDirectory.resolve("capture-workspace");
    SourceRegistrationReference registration =
        new LocalGitCommitCaptureAdapter(workspace, Path.of("/usr/bin/git"))
            .capture(
                new LocalGitCaptureRequest(
                    "https://example.invalid/customer/profile.git",
                    commit,
                    repository,
                    reference("capture-policy", '1'),
                    reference("resource-budget", '2')));
    LocalGitSourceRegistry registry = new LocalGitSourceRegistry(workspace);
    return new CapturedFixture(registry.reopen(registration.sourceRegistrationId()), registry);
  }

  private static PublishedInventory publishedInventory(CapturedFixture captured) {
    RegisteredSourceCapture capture = captured.capture();
    byte[] rawUtf8 = "<project/>\n".getBytes(StandardCharsets.UTF_8);
    ArtifactId fileId = fileId("pom.xml", "100644", rawUtf8.length, sha256(rawUtf8));
    ArtifactReference sourceInputId = reference("verified-source-inventory-source-input", '3');
    ArtifactReference inventoryId = reference("verified-source-inventory-source-inventory", '4');
    ArtifactReference snapshotId = reference("verified-snapshot", '5');
    ArtifactReference capabilityProfile = reference("capability-profile", '6');
    ArtifactControls controls = controls();

    ObjectNode sourceInput = JsonNodeFactory.instance.objectNode();
    sourceInput.put("schemaVersion", "verified-source-inventory-source-input-v2");
    sourceInput.put("artifactType", "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT");
    sourceInput.put("artifactId", sourceInputId.artifactId().value());
    sourceInput.put("sourceRegistrationId", capture.sourceRegistrationRef().artifactId().value());
    ObjectNode scope = sourceInput.putObject("inventoryScope");
    scope.put("kind", "COMPLETE_CAPTURE");
    scope.putNull("scopeRoot");
    scope.put("declaredPathCount", 1);
    sourceInput.put("repositoryCompletionEligible", true);

    ObjectNode entry = JsonNodeFactory.instance.objectNode();
    entry.put("schemaVersion", "source-inventory-entry-v2");
    entry.put("fileId", fileId.value());
    entry.put("path", "pom.xml");
    entry.put("gitMode", "100644");
    entry.put("mediaType", "text/plain");
    entry.put("sizeBytes", rawUtf8.length);
    entry.put("sha256", sha256(rawUtf8));
    entry.put("analysisDisposition", "ANALYZABLE_TEXT");
    entry.put("textEncoding", "UTF-8");
    entry.put("lineIndexDigest", "7".repeat(64));
    ImmutableBytes inventoryBytes =
        ImmutableBytes.copyOf((canonical(entry) + "\n").getBytes(StandardCharsets.UTF_8));

    ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
    snapshot.put("schemaVersion", "verified-snapshot-v2");
    snapshot.put("artifactType", "VERIFIED_SNAPSHOT");
    snapshot.put("artifactId", snapshotId.artifactId().value());
    snapshot.put("snapshotId", capture.snapshotId());
    snapshot.put("repositoryCompletionEligible", true);
    snapshot.set("capabilityProfileRef", referenceNode(capabilityProfile));

    AnalysisStepPublicationAddress address =
        new AnalysisStepPublicationAddress(
            AnalysisRunId.parse("analysis-run:" + "8".repeat(64)),
            AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
    AnalysisStepPublicationReference reference =
        new AnalysisStepPublicationReference(
            address,
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + "9".repeat(64)),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + "a".repeat(64)),
            Sha256Digest.parse("b".repeat(64)));
    List<VerifiedCanonicalPayload> payloads =
        List.of(
            payload("source-input.json", sourceInputId, sourceInput),
            new VerifiedCanonicalPayload(
                descriptor(
                    "source-inventory.jsonl",
                    "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
                    "verified-source-inventory-source-inventory-v2",
                    inventoryId,
                    inventoryBytes),
                inventoryBytes),
            payload("verified-snapshot.json", snapshotId, snapshot));
    AnalysisStepReceipt receipt =
        new AnalysisStepReceipt(
            "analysis-step-receipt-v1",
            reference.analysisStepReceiptId(),
            address,
            new AnalysisStepPublisherModuleProvenance(
                new ModulePublicationReference(
                    new org.sourceanalysis.app.artifact.AnalysisStepModuleAddress(
                        address.runId(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 3, "publish"),
                    ModuleArtifactRoot.parse("module-root:" + "c".repeat(64)),
                    ModuleReceiptId.parse("module-receipt:" + "d".repeat(64)),
                    Sha256Digest.parse("e".repeat(64)))),
            List.of(),
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList(),
            null,
            reference.analysisStepArtifactRoot(),
            List.of());
    return new PublishedInventory(
        reference, new ReopenedAnalysisStepPublication(reference, receipt, payloads, null));
  }

  private static VerifiedCanonicalPayload payload(
      String fileName, ArtifactReference identity, ObjectNode document) {
    ImmutableBytes bytes =
        ImmutableBytes.copyOf(canonical(document).getBytes(StandardCharsets.UTF_8));
    String type = document.get("artifactType").textValue();
    String schema = document.get("schemaVersion").textValue();
    return new VerifiedCanonicalPayload(descriptor(fileName, type, schema, identity, bytes), bytes);
  }

  private static ArtifactDescriptor descriptor(
      String fileName,
      String artifactType,
      String schemaVersion,
      ArtifactReference identity,
      ImmutableBytes bytes) {
    return new ArtifactDescriptor(
        fileName,
        artifactType,
        schemaVersion,
        identity.artifactId(),
        CanonicalMediaType.APPLICATION_JSON,
        bytes.size(),
        Sha256Digest.parse(sha256(bytes.copyToByteArray())));
  }

  private static ArtifactControls controls() {
    return new ArtifactControls(
        Sha256Digest.parse("f".repeat(64)),
        Sha256Digest.parse("0".repeat(64)),
        Sha256Digest.parse("1".repeat(64)),
        null,
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse("artifact-policy-registry:" + "2".repeat(64)),
            Sha256Digest.parse("2".repeat(64))));
  }

  private static ArtifactReference reference(String prefix, char digit) {
    String value = String.valueOf(digit).repeat(64);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + value), Sha256Digest.parse(value));
  }

  private static ArtifactId fileId(String path, String gitMode, long size, String sha256) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("path", path);
    body.put("gitMode", gitMode);
    body.put("sizeBytes", size);
    body.put("sha256", sha256);
    return ArtifactId.parse(
        "file:"
            + sha256(
                concatenate(
                    frame("verified-source-file-id-v1"),
                    frame(canonical(body).getBytes(StandardCharsets.UTF_8)))));
  }

  private static ObjectNode referenceNode(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static String canonical(ObjectNode value) {
    return new String(
        new org.sourceanalysis.app.artifact.CanonicalJsonCodec()
            .encodeCanonical(value)
            .copyToByteArray(),
        StandardCharsets.UTF_8);
  }

  private static String runGit(Path directory, String... arguments) throws Exception {
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

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record CapturedFixture(
      RegisteredSourceCapture capture, LocalGitSourceRegistry registry) {}

  private record PublishedInventory(
      AnalysisStepPublicationReference reference, ReopenedAnalysisStepPublication reopened) {}

  private record FixedAnalysisStepStore(ReopenedAnalysisStepPublication publication)
      implements CanonicalAnalysisStepArtifactStore {

    @Override
    public org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication install(
        org.sourceanalysis.app.artifact.AnalysisStepInstallRequest request) {
      throw new UnsupportedOperationException(
          "test source reader never installs a source inventory");
    }

    @Override
    public ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference) {
      assertThat(reference).isEqualTo(publication.reference());
      return publication;
    }
  }
}
