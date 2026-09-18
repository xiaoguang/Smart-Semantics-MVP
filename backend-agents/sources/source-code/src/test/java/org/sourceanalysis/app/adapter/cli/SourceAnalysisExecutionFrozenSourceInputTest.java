package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.persistence.PersistenceConfiguration;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationAddress;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** RED contract for reopening old Step01/Step05 inputs with the configured input policy. */
class SourceAnalysisExecutionFrozenSourceInputTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ArtifactStoreLimits LIMITS =
      new ArtifactStoreLimits(16, 1_000_000, 2_000_000, 128);

  @TempDir Path temporaryDirectory;

  @Test
  void processInputStepStoreReopensAStoredPublicationWithTheInputPolicyRegistry() {
    CanonicalArtifactPolicyRegistry inputPolicies =
        loadPolicies("tools/repository-run/jdt-artifact-policy-set-pre-process-discovery-v1.json");
    CanonicalArtifactPolicyRegistry outputPolicies =
        loadPolicies("tools/repository-run/jdt-artifact-policy-set-v1.json");
    AnalysisRunId runId = runId();
    ArtifactControls controls =
        new ArtifactControls(
            digest('a'), digest('b'), digest('c'), null, inputPolicies.reference());

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore inputModules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, inputPolicies, LIMITS);
      ModuleInstallRequest publisherRequest = publisherRequest(runId, controls);
      ModulePublicationReference publisher = inputModules.install(publisherRequest).reference();
      CanonicalAnalysisStepArtifactStore inputSteps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, inputPolicies, LIMITS);
      AnalysisStepInstallRequest stepRequest =
          new AnalysisStepInstallRequest(
              new AnalysisStepPublicationAddress(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
              new AnalysisStepPublisherModuleProvenance(publisher),
              List.of(),
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(),
              publisherRequest.payloads().stream()
                  .map(SourceAnalysisExecutionFrozenSourceInputTest::stepPayload)
                  .toList(),
              null);
      var installed = inputSteps.install(stepRequest);

      CanonicalAnalysisStepArtifactStore outputSteps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, outputPolicies, LIMITS);
      assertThatThrownBy(() -> outputSteps.reopen(installed.reference()))
          .hasMessage("ARTIFACT_POLICY_MISMATCH");

      RepositoryRunConfiguration configuration = configuration(outputPolicies, inputPolicies);
      CanonicalAnalysisStepArtifactStore processInputSteps =
          SourceAnalysisExecution.inputStepArtifacts(configuration, handle);

      assertThat(processInputSteps.reopen(installed.reference()).reference())
          .isEqualTo(installed.reference());
    }
  }

  private RepositoryRunConfiguration configuration(
      CanonicalArtifactPolicyRegistry outputPolicies,
      CanonicalArtifactPolicyRegistry inputPolicies) {
    return new RepositoryRunConfiguration(
        JSON,
        null,
        null,
        outputPolicies,
        inputPolicies,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        PersistenceConfiguration.disabled(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        LIMITS,
        null,
        null,
        null,
        null,
        null,
        1);
  }

  private CanonicalArtifactPolicyRegistry loadPolicies(String file) {
    return SourceAnalysisExecution.loadPolicies(Path.of(file).toAbsolutePath(), JSON);
  }

  private static ModuleInstallRequest publisherRequest(
      AnalysisRunId runId, ArtifactControls controls) {
    ModulePublicationAddress address =
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
                "source-input.json",
                "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
                "verified-source-inventory-source-input-v2",
                "verified-source-inventory-source-input",
                document(
                    "verified-source-inventory-source-input-v2",
                    "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT")),
            jsonlPayload(),
            standalonePayload(
                "verified-snapshot.json",
                "VERIFIED_SNAPSHOT",
                "verified-snapshot-v2",
                "verified-snapshot",
                document("verified-snapshot-v2", "VERIFIED_SNAPSHOT"))));
  }

  private static CanonicalAnalysisStepPayload stepPayload(CanonicalModulePayload payload) {
    return new CanonicalAnalysisStepPayload(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8());
  }

  private static ObjectNode document(String schemaVersion, String artifactType) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", schemaVersion);
    document.put("artifactType", artifactType);
    return document;
  }

  private static CanonicalModulePayload standalonePayload(
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
                    frame(JSON.encodeCanonical(document).copyToByteArray())));
    document.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        JSON.encodeCanonical(document));
  }

  private static CanonicalModulePayload jsonlPayload() {
    ObjectNode entry = JsonNodeFactory.instance.objectNode();
    entry.put("schemaVersion", "source-inventory-entry-v2");
    entry.put("path", "src/example/Catalogue.java");
    byte[] bytes =
        concatenate(
            JSON.encodeCanonical(entry).copyToByteArray(), "\n".getBytes(StandardCharsets.UTF_8));
    String artifactId =
        "verified-source-inventory-source-inventory"
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame("verified-source-inventory-source-inventory-v2"),
                    frame("VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY"),
                    frame(bytes)));
    return new CanonicalModulePayload(
        "source-inventory.jsonl",
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
        "verified-source-inventory-source-inventory-v2",
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private static ArtifactReference reference(String prefix, char fill) {
    String value = String.valueOf(fill).repeat(64);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + value), new Sha256Digest(value));
  }

  private static Sha256Digest digest(char fill) {
    return new Sha256Digest(String.valueOf(fill).repeat(64));
  }

  private static AnalysisRunId runId() {
    return AnalysisRunId.parse("analysis-run:" + "0".repeat(64));
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
