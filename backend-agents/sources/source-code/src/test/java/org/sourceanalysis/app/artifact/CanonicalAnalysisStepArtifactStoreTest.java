package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalAnalysisStepArtifactStoreTest {

  @TempDir java.nio.file.Path emptyTemporaryDirectory;

  @Test
  void installsAndFreshReopensTheExactVerifiedSourceInventoryPublicSet() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = sourceInventoryPolicies(canonicalJson);
    AnalysisRunId runId =
        AnalysisRunId.parse(
            "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    ArtifactControls controls =
        new ArtifactControls(digest('a'), digest('b'), digest('c'), null, policies.reference());
    ModuleInstallRequest publisherRequest =
        publisherRequest(canonicalJson, policies, runId, controls);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore moduleStore =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(3, 1_000_000, 2_000_000, 8));
      InstalledModulePublication publisher = moduleStore.install(publisherRequest);
      CanonicalAnalysisStepArtifactStore stepStore =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(3, 1_000_000, 2_000_000, 8));

      AnalysisStepInstallRequest request =
          new AnalysisStepInstallRequest(
              new AnalysisStepPublicationAddress(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
              new AnalysisStepPublisherModuleProvenance(publisher.reference()),
              List.of(),
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(),
              publisherRequest.payloads().stream()
                  .map(
                      payload ->
                          new CanonicalAnalysisStepPayload(
                              payload.fileName(),
                              payload.artifactType(),
                              payload.schemaVersion(),
                              payload.artifactId(),
                              payload.mediaType(),
                              payload.canonicalUtf8()))
                  .toList(),
              null);

      InstalledAnalysisStepPublication installed = stepStore.install(request);
      assertThat(installed.disposition()).isEqualTo(ModuleInstallDisposition.INSTALLED);
      assertThat(installed.semanticArtifactDescriptors())
          .extracting(ArtifactDescriptor::fileName)
          .containsExactly("source-input.json", "source-inventory.jsonl", "verified-snapshot.json");
      assertThat(installed.archiveManifestDescriptor()).isNull();

      ReopenedAnalysisStepPublication reopened = stepStore.reopen(installed.reference());
      assertThat(reopened.reference()).isEqualTo(installed.reference());
      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly("source-input.json", "source-inventory.jsonl", "verified-snapshot.json");
      assertThat(stepStore.install(request).disposition())
          .isEqualTo(ModuleInstallDisposition.ALREADY_INSTALLED);
    }
  }

  @Test
  void rejectsAReferenceWhoseReceiptDigestDoesNotMatchThePersistedReceipt() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = sourceInventoryPolicies(canonicalJson);
    AnalysisRunId runId =
        AnalysisRunId.parse(
            "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    ArtifactControls controls =
        new ArtifactControls(digest('a'), digest('b'), digest('c'), null, policies.reference());
    ModuleInstallRequest publisherRequest =
        publisherRequest(canonicalJson, policies, runId, controls);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore moduleStore =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(3, 1_000_000, 2_000_000, 8));
      InstalledModulePublication publisher = moduleStore.install(publisherRequest);
      CanonicalAnalysisStepArtifactStore stepStore =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(3, 1_000_000, 2_000_000, 8));
      AnalysisStepInstallRequest request = request(runId, controls, publisher, publisherRequest);
      AnalysisStepPublicationReference installed = stepStore.install(request).reference();
      AnalysisStepPublicationReference altered =
          new AnalysisStepPublicationReference(
              installed.address(),
              installed.analysisStepArtifactRoot(),
              installed.analysisStepReceiptId(),
              digest('9'));

      assertThatThrownBy(() -> stepStore.reopen(altered))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("ANALYSIS_STEP_PUBLICATION_INVALID"));
    }
  }

  private static AnalysisStepInstallRequest request(
      AnalysisRunId runId,
      ArtifactControls controls,
      InstalledModulePublication publisher,
      ModuleInstallRequest publisherRequest) {
    return new AnalysisStepInstallRequest(
        new AnalysisStepPublicationAddress(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
        new AnalysisStepPublisherModuleProvenance(publisher.reference()),
        List.of(),
        controls,
        ModuleCompletionStatus.SUCCEEDED,
        List.of(),
        publisherRequest.payloads().stream()
            .map(
                payload ->
                    new CanonicalAnalysisStepPayload(
                        payload.fileName(),
                        payload.artifactType(),
                        payload.schemaVersion(),
                        payload.artifactId(),
                        payload.mediaType(),
                        payload.canonicalUtf8()))
            .toList(),
        null);
  }

  private static CanonicalArtifactPolicyRegistry sourceInventoryPolicies(
      CanonicalJsonCodec canonicalJson) {
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    addPolicy(
        policies.addObject(),
        "VERIFIED_SNAPSHOT",
        "verified-snapshot-v2",
        "verified-snapshot",
        "application/json",
        "STANDALONE_JSON");
    addPolicy(
        policies.addObject(),
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
        "verified-source-inventory-source-input-v2",
        "verified-source-inventory-source-input",
        "application/json",
        "STANDALONE_JSON");
    addPolicy(
        policies.addObject(),
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
        "verified-source-inventory-source-inventory-v2",
        "verified-source-inventory-source-inventory",
        "application/x-ndjson",
        "CANONICAL_JSONL");

    ObjectNode document = withoutId.deepCopy();
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + sha256(
                concatenate(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(
        canonicalJson.encodeCanonical(document), canonicalJson);
  }

  private static void addPolicy(
      ObjectNode policy,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      String mediaType,
      String envelopeKind) {
    policy.put("artifactType", artifactType);
    policy.put("schemaVersion", schemaVersion);
    policy.put("artifactIdPrefix", artifactIdPrefix);
    policy.put("mediaType", mediaType);
    policy.put("envelopeKind", envelopeKind);
    policy.put("emptyJsonlAllowed", false);
    policy.put("publicContentExposure", "METADATA_ONLY");
  }

  private static ModuleInstallRequest publisherRequest(
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry policies,
      AnalysisRunId runId,
      ArtifactControls controls) {
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 3, "publish");
    return new ModuleInstallRequest(
        address,
        "v2",
        List.of(
            reference("frozen-request", '1'),
            reference("run-request", '2'),
            reference("source-index", '3'),
            reference("source-request", '4')),
        controls,
        ModuleCompletionStatus.SUCCEEDED,
        List.of(),
        List.of(
            standalonePayload(
                canonicalJson,
                "source-input.json",
                "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
                "verified-source-inventory-source-input-v2",
                "verified-source-inventory-source-input",
                document(
                    "verified-source-inventory-source-input-v2",
                    "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT")),
            jsonlPayload(
                canonicalJson,
                "source-inventory.jsonl",
                "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
                "verified-source-inventory-source-inventory-v2",
                "verified-source-inventory-source-inventory"),
            standalonePayload(
                canonicalJson,
                "verified-snapshot.json",
                "VERIFIED_SNAPSHOT",
                "verified-snapshot-v2",
                "verified-snapshot",
                document("verified-snapshot-v2", "VERIFIED_SNAPSHOT"))));
  }

  private static ObjectNode document(String schemaVersion, String artifactType) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", schemaVersion);
    document.put("artifactType", artifactType);
    return document;
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String prefix,
      ObjectNode document) {
    String artifactId =
        prefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(canonicalJson.encodeCanonical(document).copyToByteArray())));
    document.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(document));
  }

  private static CanonicalModulePayload jsonlPayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String prefix) {
    ObjectNode entry = JsonNodeFactory.instance.objectNode();
    entry.put("schemaVersion", "source-inventory-entry-v2");
    entry.put("path", "src/example/Catalogue.java");
    byte[] bytes =
        concatenate(
            canonicalJson.encodeCanonical(entry).copyToByteArray(),
            "\n".getBytes(StandardCharsets.UTF_8));
    String artifactId =
        prefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(bytes)));
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private static ArtifactReference reference(String prefix, char digit) {
    String value = String.valueOf(digit).repeat(64);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + value), new Sha256Digest(value));
  }

  private static Sha256Digest digest(char digit) {
    return new Sha256Digest(String.valueOf(digit).repeat(64));
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

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length += value.length;
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
