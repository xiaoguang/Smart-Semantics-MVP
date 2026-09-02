package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequest;
import org.sourceanalysis.app.capture.localgit.LocalGitCommitCaptureAdapter;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceFile;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;

class VerifiedSourceIndexModulePublisherTest {

  private static final CanonicalJsonCodec CANONICAL_JSON = new CanonicalJsonCodec();
  private static final ArtifactReference CAPTURE_POLICY = reference("capture-policy", 'a');
  private static final ArtifactReference RESOURCE_BUDGET = reference("resource-budget", 'b');

  @TempDir Path temporaryDirectory;

  @Test
  void freshReopensM1ThenPublishesAReceiptLastVerifiedIndexWithTheFrozenFullInventoryShard()
      throws Exception {
    CapturedFixture capture = capturedFixture();
    CanonicalArtifactPolicyRegistry policies = policies();
    ArtifactReference policyReference =
        new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256());
    ArtifactReference runRequest = reference("run-request", '1');
    ArtifactReference frozenRequest = reference("frozen-request", '2');
    ArtifactReference profile = reference("profile-bundle", '3');
    ArtifactReference budget = reference("resource-budget", '4');
    ArtifactReference toolchain = reference("toolchain", '5');
    ArtifactReference schema = reference("schema-bundle", '6');
    ArtifactReference prompt = reference("prompt-bundle", '7');
    ArtifactReference verificationPolicy = reference("verification-policy", '8');
    ArtifactReference capabilityProfile = reference("capability-profile", '9');
    AdmittedSourceRequest admitted =
        admitted(
            capture.capture(),
            runRequest,
            frozenRequest,
            profile,
            budget,
            toolchain,
            schema,
            prompt,
            policyReference);
    List<ArtifactReference> upstream =
        sorted(
            List.of(
                runRequest,
                capture.capture().sourceRegistrationRef(),
                frozenRequest,
                capture.capture().captureReceiptRef(),
                capture.capture().snapshotManifestRef(),
                budget,
                verificationPolicy,
                capabilityProfile));

    Path storeDirectory = temporaryDirectory.resolve("store");
    Files.createDirectory(storeDirectory);
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(storeDirectory)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle,
              CANONICAL_JSON,
              policies,
              new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));
      ModulePublicationReference m1 =
          new AdmittedSourceRequestModulePublisher(modules)
              .publish(
                  new AdmittedSourceRequestPublicationInput(
                      runId(),
                      runRequest,
                      capture.capture().sourceRegistrationRef(),
                      verificationPolicy,
                      capabilityProfile,
                      upstream,
                      admitted));

      ModulePublicationReference m2 =
          new VerifiedSourceIndexModulePublisher(modules).publish(m1, capture.registry());

      var reopened = modules.reopen(m2);
      assertThat(reopened.receipt().address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  runId(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 2, "source-index"));
      assertThat(reopened.receipt().upstreamArtifacts())
          .containsExactlyElementsOf(
              sorted(
                  List.of(
                      payloadReference(modules.reopen(m1)),
                      capture.capture().sourceRegistrationRef())));
      assertThat(reopened.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName()).isEqualTo("verified-source-index.json");
                JsonNode body =
                    CANONICAL_JSON.parseCanonical(payload.canonicalUtf8()).get("payload");
                assertThat(body.get("snapshotId").textValue())
                    .isEqualTo(capture.capture().snapshotId());
                assertThat(body.get("requestArtifactId").textValue())
                    .isEqualTo(payloadReference(modules.reopen(m1)).artifactId().value());
                assertThat(body.get("verifiedRegularFileCount").intValue()).isEqualTo(2);
                assertThat(body.get("analyzableTextFileCount").intValue()).isEqualTo(1);
                assertThat(body.get("nonAnalyzableMediaFileCount").intValue()).isEqualTo(1);
                assertThat(body.get("verifiedFiles")).hasSize(2);
                ArrayNode shards = (ArrayNode) body.get("shardReceipts");
                assertThat(shards).hasSize(1);
                ObjectNode shard = (ObjectNode) shards.get(0);
                List<String> fileIds =
                    body.get("verifiedFiles")
                        .valueStream()
                        .map(file -> file.get("fileId").textValue())
                        .sorted(VerifiedSourceIndexModulePublisherTest::compareUtf8)
                        .toList();
                assertThat(
                        shard
                            .get("denominatorFileIds")
                            .valueStream()
                            .map(JsonNode::textValue)
                            .toList())
                    .containsExactlyElementsOf(fileIds);
                assertThat(
                        shard
                            .get("verifiedFileIds")
                            .valueStream()
                            .map(JsonNode::textValue)
                            .toList())
                    .containsExactlyElementsOf(fileIds);
                assertThat(shard.get("status").textValue()).isEqualTo("SUCCEEDED");
                assertThat(shard.get("gapIds")).isEmpty();
                assertThat(shard.get("shardId").textValue())
                    .isEqualTo(
                        sourceShardId(payloadReference(modules.reopen(m1)).artifactId(), fileIds));
                assertThat(body.get("sourceIntegrity").textValue()).isEqualTo("VERIFIED");
              });
    }
  }

  private CapturedFixture capturedFixture() throws Exception {
    Path physicalDirectory = temporaryDirectory.toRealPath();
    Path repository = physicalDirectory.resolve("repository");
    initialiseRepository(repository);
    Path source = repository.resolve("src/example");
    Files.createDirectories(source);
    Files.writeString(
        source.resolve("Catalogue.java"),
        "package example;\nfinal class Catalogue {}\n",
        StandardCharsets.UTF_8);
    Path media = repository.resolve("static");
    Files.createDirectories(media);
    Files.write(media.resolve("logo.bin"), new byte[] {0, 1, 2, 3});
    runGit(repository, "add", ".");
    runGit(repository, "commit", "-m", "fixture");
    String commitId = runGit(repository, "rev-parse", "HEAD").trim();
    Path captureWorkspace = physicalDirectory.resolve("capture-workspace");
    SourceRegistrationReference registration =
        new LocalGitCommitCaptureAdapter(captureWorkspace, Path.of("/usr/bin/git"))
            .capture(
                new LocalGitCaptureRequest(
                    "https://example.invalid/customer/catalogue.git",
                    commitId,
                    repository,
                    CAPTURE_POLICY,
                    RESOURCE_BUDGET));
    LocalGitSourceRegistry registry = new LocalGitSourceRegistry(captureWorkspace);
    return new CapturedFixture(registry.reopen(registration.sourceRegistrationId()), registry);
  }

  private static AdmittedSourceRequest admitted(
      RegisteredSourceCapture capture,
      ArtifactReference runRequest,
      ArtifactReference frozenRequest,
      ArtifactReference profile,
      ArtifactReference budget,
      ArtifactReference toolchain,
      ArtifactReference schema,
      ArtifactReference prompt,
      ArtifactReference policyReference) {
    List<CapturedRegularFile> files =
        capture.manifestEntries().stream()
            .map(VerifiedSourceIndexModulePublisherTest::capturedFile)
            .sorted(Comparator.comparing(CapturedRegularFile::path))
            .toList();
    List<ArtifactId> text =
        files.stream()
            .filter(file -> file.analysisDisposition() == SourceAnalysisDisposition.ANALYZABLE_TEXT)
            .map(CapturedRegularFile::fileId)
            .toList();
    List<ArtifactId> media =
        files.stream()
            .filter(
                file ->
                    file.analysisDisposition() == SourceAnalysisDisposition.NON_ANALYZABLE_MEDIA)
            .map(CapturedRegularFile::fileId)
            .toList();
    return new AdmittedSourceRequest(
        runRequest.artifactId().value(),
        capture.sourceRegistrationRef().artifactId(),
        capture.declaredRepositoryIdentity(),
        capture.commitId(),
        frozenRequest,
        capture.captureReceiptRef(),
        capture.snapshotManifestRef(),
        InventoryScope.completeCapture(),
        true,
        files.size(),
        files,
        text,
        media,
        new RunRequestControls(profile, budget, toolchain, schema, prompt, policyReference));
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

  private static CanonicalArtifactPolicyRegistry policies() {
    ObjectNode withoutIdentity = JsonNodeFactory.instance.objectNode();
    withoutIdentity.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutIdentity.putArray("policies");
    addPolicy(
        policies,
        "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST",
        "verified-source-inventory-admitted-source-request-v2",
        "source-request");
    addPolicy(
        policies,
        "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX",
        "verified-source-inventory-verified-source-index-v2",
        "source-index");
    ObjectNode document = withoutIdentity.deepCopy();
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + sha256(
                frame(
                    "canonical-artifact-policy-registry-id-v2",
                    CANONICAL_JSON.encodeCanonical(withoutIdentity).copyToByteArray())));
    return CanonicalArtifactPolicyRegistry.load(
        CANONICAL_JSON.encodeCanonical(document), CANONICAL_JSON);
  }

  private static void addPolicy(
      ArrayNode policies, String artifactType, String schemaVersion, String artifactIdPrefix) {
    ObjectNode policy = policies.addObject();
    policy.put("artifactType", artifactType);
    policy.put("schemaVersion", schemaVersion);
    policy.put("artifactIdPrefix", artifactIdPrefix);
    policy.put("mediaType", "application/json");
    policy.put("envelopeKind", "MODULE_ARTIFACT_JSON");
    policy.put("emptyJsonlAllowed", false);
    policy.put("publicContentExposure", "METADATA_ONLY");
  }

  private static ArtifactReference payloadReference(
      org.sourceanalysis.app.artifact.ReopenedModulePublication publication) {
    assertThat(publication.payloads()).hasSize(1);
    var descriptor = publication.payloads().get(0).descriptor();
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private static String sourceShardId(ArtifactId requestArtifactId, List<String> fileIds) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("analysisStepKey", "verified-source-inventory");
    material.put("shardKind", "FULL_INVENTORY_VERIFICATION");
    material.put("requestArtifactId", requestArtifactId.value());
    ArrayNode denominator = material.putArray("denominatorFileIds");
    fileIds.forEach(denominator::add);
    ArrayNode verified = material.putArray("verifiedFileIds");
    fileIds.forEach(verified::add);
    material.put("status", "SUCCEEDED");
    material.putArray("gapIds");
    return "source-shard:"
        + sha256(
            concatenate(
                frame("verified-source-shard-id-v1"),
                frame(CANONICAL_JSON.encodeCanonical(material).copyToByteArray())));
  }

  private static ArtifactId fileId(
      String path, String gitMode, long sizeBytes, Sha256Digest sha256) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("path", path);
    material.put("gitMode", gitMode);
    material.put("sizeBytes", sizeBytes);
    material.put("sha256", sha256.value());
    return ArtifactId.parse(
        "file:"
            + sha256(
                concatenate(
                    frame("verified-source-file-id-v1"),
                    frame(CANONICAL_JSON.encodeCanonical(material).copyToByteArray()))));
  }

  private static AnalysisRunId runId() {
    return AnalysisRunId.parse("analysis-run:" + "d".repeat(64));
  }

  private static ArtifactReference reference(String prefix, char digit) {
    String digest = String.valueOf(digit).repeat(64);
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest), Sha256Digest.parse(digest));
  }

  private static List<ArtifactReference> sorted(List<ArtifactReference> references) {
    return references.stream()
        .sorted(Comparator.comparing(reference -> reference.artifactId().value()))
        .toList();
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

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(String domain, byte[] content) {
    return concatenate(frame(domain), frame(content));
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
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private record CapturedFixture(
      RegisteredSourceCapture capture, LocalGitSourceRegistry registry) {}
}
