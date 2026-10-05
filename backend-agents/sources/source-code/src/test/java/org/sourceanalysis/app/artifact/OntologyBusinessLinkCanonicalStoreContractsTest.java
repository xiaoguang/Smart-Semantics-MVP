package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Direct filesystem-store contracts for the versioned business-link ontology artifacts. */
class OntologyBusinessLinkCanonicalStoreContractsTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String POLICY_SET_PATH =
      "tools/repository-run/ontology-artifact-policy-set-v3.json";

  @TempDir Path temporaryDirectory;

  @Test
  void installsAndFreshReopensCorpusV2AndV3TypedModulesAndStepPublications() throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policyRegistry(canonicalJson);
    ArtifactControls controls = controls(policies);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore modules = moduleStore(handle, canonicalJson, policies);
      CanonicalAnalysisStepArtifactStore steps = stepStore(handle, canonicalJson, policies);

      List<ProducerFixture> producers =
          List.of(
              producer(
                  canonicalJson,
                  policies,
                  1,
                  2,
                  "ontology-corpus",
                  "v2",
                  "ONTOLOGY_CORPUS",
                  "ontology-corpus-v2",
                  "ontology-corpus.json"),
              producer(
                  canonicalJson,
                  policies,
                  2,
                  3,
                  "ontology-identification",
                  "v3",
                  "ONTOLOGY_IDENTIFICATION",
                  "ontology-identification-v3",
                  "ontology-identification.json"),
              producer(
                  canonicalJson,
                  policies,
                  3,
                  4,
                  "ontology-relations",
                  "v3",
                  "ONTOLOGY_RELATIONS",
                  "ontology-relations-v3",
                  "ontology-relations.json"),
              publisherV3(canonicalJson, policies));

      for (ProducerFixture producer : producers) {
        AnalysisRunId runId = runId(producer.runIdentity());
        ArtifactControls runControls = controls;
        ModuleInstallRequest moduleRequest = moduleRequest(runId, producer, runControls);
        InstalledModulePublication installedModule = modules.install(moduleRequest);
        ReopenedModulePublication reopenedModule = modules.reopen(installedModule.reference());

        assertThat(reopenedModule.reference()).isEqualTo(installedModule.reference());
        assertThat(reopenedModule.receipt().moduleVersion()).isEqualTo(producer.moduleVersion());
        assertThat(reopenedModule.payloads())
            .extracting(payload -> payload.descriptor().fileName())
            .containsExactlyElementsOf(producer.expectedFileNames());
        assertThat(reopenedModule.payloads())
            .extracting(payload -> payload.descriptor().schemaVersion())
            .containsExactlyElementsOf(producer.expectedSchemaVersions());
        assertThat(reopenedModule.payloads())
            .extracting(payload -> payload.canonicalUtf8())
            .containsExactlyElementsOf(
                producer.payloads().stream().map(CanonicalModulePayload::canonicalUtf8).toList());

        InstalledAnalysisStepPublication installedStep =
            steps.install(stepRequest(runId, producer, runControls, installedModule.reference()));
        ReopenedAnalysisStepPublication reopenedStep = steps.reopen(installedStep.reference());

        assertThat(reopenedStep.reference()).isEqualTo(installedStep.reference());
        assertThat(reopenedStep.receipt().address())
            .isEqualTo(
                new AnalysisStepPublicationAddress(runId, AnalysisStepKey.REPOSITORY_KNOWLEDGE));
        assertThat(reopenedStep.receipt().publicationProvenance())
            .isEqualTo(new AnalysisStepPublisherModuleProvenance(installedModule.reference()));
        assertThat(reopenedStep.receipt().upstreamAnalysisStepReferences())
            .containsExactly(businessFlowsReference(runId));
        assertThat(reopenedStep.semanticPayloads())
            .extracting(payload -> payload.descriptor().fileName())
            .containsExactlyElementsOf(producer.expectedFileNames());
        assertThat(reopenedStep.semanticPayloads())
            .extracting(payload -> payload.descriptor().schemaVersion())
            .containsExactlyElementsOf(producer.expectedSchemaVersions());
        assertThat(reopenedStep.semanticPayloads())
            .extracting(payload -> payload.canonicalUtf8())
            .containsExactlyElementsOf(
                producer.payloads().stream().map(CanonicalModulePayload::canonicalUtf8).toList());
      }
    }
  }

  @Test
  void corpusV2MayCarrySchemaEvidenceV1AndFreshReopensBothFiles() throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policyRegistry(canonicalJson);
    ArtifactControls controls = controls(policies);
    AnalysisRunId runId = runId('8');
    List<CanonicalModulePayload> payloads =
        List.of(
            standalonePayload(
                canonicalJson,
                policies,
                "ontology-corpus.json",
                "ONTOLOGY_CORPUS",
                "ontology-corpus-v2",
                "corpus-v2-with-schema-sidecar"),
            standalonePayload(
                canonicalJson,
                policies,
                "schema-evidence.json",
                "SCHEMA_EVIDENCE",
                "schema-evidence-v1",
                "optional-schema-sidecar-v1"));
    ProducerFixture corpusWithSchemaSidecar =
        new ProducerFixture(
            '8',
            2,
            "ontology-corpus",
            "v2",
            payloads,
            List.of("ontology-corpus.json", "schema-evidence.json"),
            List.of("ontology-corpus-v2", "schema-evidence-v1"));

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore modules = moduleStore(handle, canonicalJson, policies);
      CanonicalAnalysisStepArtifactStore steps = stepStore(handle, canonicalJson, policies);
      InstalledModulePublication installedModule =
          modules.install(moduleRequest(runId, corpusWithSchemaSidecar, controls));
      ReopenedModulePublication reopenedModule = modules.reopen(installedModule.reference());
      InstalledAnalysisStepPublication installedStep =
          steps.install(
              stepRequest(runId, corpusWithSchemaSidecar, controls, installedModule.reference()));
      ReopenedAnalysisStepPublication reopenedStep = steps.reopen(installedStep.reference());

      assertThat(reopenedModule.receipt().moduleVersion()).isEqualTo("v2");
      assertThat(reopenedModule.payloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly("ontology-corpus.json", "schema-evidence.json");
      assertThat(reopenedModule.payloads())
          .extracting(payload -> payload.descriptor().schemaVersion())
          .containsExactly("ontology-corpus-v2", "schema-evidence-v1");
      assertThat(reopenedModule.payloads())
          .extracting(payload -> payload.canonicalUtf8())
          .containsExactlyElementsOf(
              payloads.stream().map(CanonicalModulePayload::canonicalUtf8).toList());
      assertThat(reopenedStep.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly("ontology-corpus.json", "schema-evidence.json");
      assertThat(reopenedStep.semanticPayloads())
          .extracting(payload -> payload.descriptor().schemaVersion())
          .containsExactly("ontology-corpus-v2", "schema-evidence-v1");
      assertThat(reopenedStep.semanticPayloads())
          .extracting(payload -> payload.canonicalUtf8())
          .containsExactlyElementsOf(
              payloads.stream().map(CanonicalModulePayload::canonicalUtf8).toList());
    }
  }

  @Test
  void rejectsV2ProducerCarryingCorpusV1EvenWithHistoricalSchemaEvidence() throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policyRegistry(canonicalJson);
    ArtifactControls controls = controls(policies);
    AnalysisRunId runId = runId('5');
    List<CanonicalModulePayload> mixedPayloads =
        List.of(
            standalonePayload(
                canonicalJson,
                policies,
                "ontology-corpus.json",
                "ONTOLOGY_CORPUS",
                "ontology-corpus-v1",
                "corpus-v1-under-v2-producer"),
            standalonePayload(
                canonicalJson,
                policies,
                "schema-evidence.json",
                "SCHEMA_EVIDENCE",
                "schema-evidence-v1",
                "legacy-sidecar"));

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore modules = moduleStore(handle, canonicalJson, policies);

      assertThatThrownBy(
              () ->
                  modules.install(
                      new ModuleInstallRequest(
                          moduleAddress(runId, 2, "ontology-corpus"),
                          "v2",
                          List.of(),
                          controls,
                          ModuleCompletionStatus.SUCCEEDED,
                          List.of(),
                          mixedPayloads)))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("MODULE_INSTALL_REQUEST_INVALID"));
      assertThat(receiptFiles(temporaryDirectory)).isEmpty();
    }
  }

  @Test
  void rejectsV3PublisherWhoseOntologyCoverageAndReviewRemainOnHistoricalSchemas()
      throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policyRegistry(canonicalJson);
    ArtifactControls controls = controls(policies);
    AnalysisRunId runId = runId('6');
    List<CanonicalModulePayload> mixedPublisherPayloads =
        List.of(
            standalonePayload(
                canonicalJson,
                policies,
                "ontology-coverage.json",
                "ONTOLOGY_COVERAGE",
                "ontology-coverage-v2",
                "coverage-v2"),
            standalonePayload(
                canonicalJson,
                policies,
                "ontology-review.json",
                "ONTOLOGY_REVIEW",
                "ontology-review-v2",
                "review-v2"),
            sourceIndexPayload(canonicalJson, policies),
            standalonePayload(
                canonicalJson,
                policies,
                "ontology.json",
                "ONTOLOGY",
                "ontology-v1",
                "ontology-v1"));

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore modules = moduleStore(handle, canonicalJson, policies);

      assertThatThrownBy(
              () ->
                  modules.install(
                      new ModuleInstallRequest(
                          moduleAddress(runId, 5, "ontology-publisher"),
                          "v3",
                          List.of(),
                          controls,
                          ModuleCompletionStatus.SUCCEEDED,
                          List.of(),
                          mixedPublisherPayloads)))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("MODULE_INSTALL_REQUEST_INVALID"));
      assertThat(receiptFiles(temporaryDirectory)).isEmpty();
    }
  }

  @Test
  void preservesHistoricalCorpusV1AndSchemaEvidenceBytesUnderTheExpandedPolicyRegistry()
      throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policyRegistry(canonicalJson);
    ArtifactControls controls = controls(policies);
    AnalysisRunId runId = runId('7');
    List<CanonicalModulePayload> historicalPayloads =
        List.of(
            standalonePayload(
                canonicalJson,
                policies,
                "ontology-corpus.json",
                "ONTOLOGY_CORPUS",
                "ontology-corpus-v1",
                "historical-corpus-v1"),
            standalonePayload(
                canonicalJson,
                policies,
                "schema-evidence.json",
                "SCHEMA_EVIDENCE",
                "schema-evidence-v1",
                "historical-schema-evidence-v1"));
    ProducerFixture historical =
        new ProducerFixture(
            '7',
            2,
            "ontology-corpus",
            "v1",
            historicalPayloads,
            List.of("ontology-corpus.json", "schema-evidence.json"),
            List.of("ontology-corpus-v1", "schema-evidence-v1"));

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore modules = moduleStore(handle, canonicalJson, policies);
      CanonicalAnalysisStepArtifactStore steps = stepStore(handle, canonicalJson, policies);
      InstalledModulePublication installedModule =
          modules.install(moduleRequest(runId, historical, controls));
      ReopenedModulePublication reopenedModule = modules.reopen(installedModule.reference());
      InstalledAnalysisStepPublication installedStep =
          steps.install(stepRequest(runId, historical, controls, installedModule.reference()));
      ReopenedAnalysisStepPublication reopenedStep = steps.reopen(installedStep.reference());

      assertThat(reopenedModule.receipt().moduleVersion()).isEqualTo("v1");
      assertThat(reopenedModule.payloads())
          .extracting(payload -> payload.descriptor().schemaVersion())
          .containsExactly("ontology-corpus-v1", "schema-evidence-v1");
      assertThat(reopenedModule.payloads())
          .extracting(payload -> payload.canonicalUtf8())
          .containsExactlyElementsOf(
              historicalPayloads.stream().map(CanonicalModulePayload::canonicalUtf8).toList());
      assertThat(reopenedStep.semanticPayloads())
          .extracting(payload -> payload.descriptor().schemaVersion())
          .containsExactly("ontology-corpus-v1", "schema-evidence-v1");
      assertThat(reopenedStep.semanticPayloads())
          .extracting(payload -> payload.canonicalUtf8())
          .containsExactlyElementsOf(
              historicalPayloads.stream().map(CanonicalModulePayload::canonicalUtf8).toList());
    }
  }

  private static ProducerFixture producer(
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry policies,
      int runIdentity,
      int moduleNumber,
      String moduleKey,
      String moduleVersion,
      String artifactType,
      String schemaVersion,
      String fileName) {
    CanonicalModulePayload payload =
        standalonePayload(
            canonicalJson,
            policies,
            fileName,
            artifactType,
            schemaVersion,
            schemaVersion + "-document");
    return new ProducerFixture(
        Character.forDigit(runIdentity, 16),
        moduleNumber,
        moduleKey,
        moduleVersion,
        List.of(payload),
        List.of(fileName),
        List.of(schemaVersion));
  }

  private static ProducerFixture publisherV3(
      CanonicalJsonCodec canonicalJson, CanonicalArtifactPolicyRegistry policies) {
    return new ProducerFixture(
        '4',
        5,
        "ontology-publisher",
        "v3",
        List.of(
            standalonePayload(
                canonicalJson,
                policies,
                "ontology-coverage.json",
                "ONTOLOGY_COVERAGE",
                "ontology-coverage-v3",
                "coverage-v3"),
            standalonePayload(
                canonicalJson,
                policies,
                "ontology-review.json",
                "ONTOLOGY_REVIEW",
                "ontology-review-v3",
                "review-v3"),
            sourceIndexPayload(canonicalJson, policies),
            standalonePayload(
                canonicalJson,
                policies,
                "ontology.json",
                "ONTOLOGY",
                "ontology-v2",
                "ontology-v2")),
        List.of(
            "ontology-coverage.json",
            "ontology-review.json",
            "ontology-sources.jsonl",
            "ontology.json"),
        List.of("ontology-coverage-v3", "ontology-review-v3", "ontology-source-v1", "ontology-v2"));
  }

  private static ModuleInstallRequest moduleRequest(
      AnalysisRunId runId, ProducerFixture producer, ArtifactControls controls) {
    return new ModuleInstallRequest(
        moduleAddress(runId, producer.moduleNumber(), producer.moduleKey()),
        producer.moduleVersion(),
        List.of(),
        controls,
        ModuleCompletionStatus.SUCCEEDED,
        List.of(),
        producer.payloads());
  }

  private static AnalysisStepInstallRequest stepRequest(
      AnalysisRunId runId,
      ProducerFixture producer,
      ArtifactControls controls,
      ModulePublicationReference publisherReference) {
    return new AnalysisStepInstallRequest(
        new AnalysisStepPublicationAddress(runId, AnalysisStepKey.REPOSITORY_KNOWLEDGE),
        new AnalysisStepPublisherModuleProvenance(publisherReference),
        List.of(businessFlowsReference(runId)),
        controls,
        ModuleCompletionStatus.SUCCEEDED,
        List.of(),
        producer.payloads().stream()
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

  private static AnalysisStepModuleAddress moduleAddress(
      AnalysisRunId runId, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        runId, AnalysisStepKey.REPOSITORY_KNOWLEDGE, moduleNumber, moduleKey);
  }

  private static AnalysisStepPublicationReference businessFlowsReference(AnalysisRunId runId) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, AnalysisStepKey.BUSINESS_FLOWS),
        new AnalysisStepArtifactRoot("analysis-step-root:" + "1".repeat(64)),
        new AnalysisStepReceiptId("analysis-step-receipt:" + "2".repeat(64)),
        new Sha256Digest("3".repeat(64)));
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry policies,
      String fileName,
      String artifactType,
      String schemaVersion,
      String label) {
    CanonicalArtifactPolicy policy =
        policies.resolve(new ArtifactPolicyKey(artifactType, schemaVersion));
    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", schemaVersion);
    withoutArtifactId.put("artifactType", artifactType);
    withoutArtifactId.putObject("payload").put("fixtureValue", label);
    String artifactId =
        policy.artifactIdPrefix()
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode document = withoutArtifactId.deepCopy();
    document.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(document));
  }

  private static CanonicalModulePayload sourceIndexPayload(
      CanonicalJsonCodec canonicalJson, CanonicalArtifactPolicyRegistry policies) {
    String artifactType = "ONTOLOGY_SOURCE_INDEX";
    String schemaVersion = "ontology-source-v1";
    CanonicalArtifactPolicy policy =
        policies.resolve(new ArtifactPolicyKey(artifactType, schemaVersion));
    byte[] bytes =
        concatenate(
            canonicalJson.encodeCanonical(JsonNodeFactory.instance.objectNode()).copyToByteArray(),
            new byte[] {'\n'});
    String artifactId =
        policy.artifactIdPrefix()
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(bytes)));
    return new CanonicalModulePayload(
        "ontology-sources.jsonl",
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private static CanonicalArtifactPolicyRegistry policyRegistry(CanonicalJsonCodec canonicalJson)
      throws IOException {
    JsonNode policySet = JSON.readTree(Files.readString(Path.of(POLICY_SET_PATH)));
    if (policySet == null || !policySet.path("policies").isArray()) {
      throw new AssertionError("business-link artifact policy-set fixture is unavailable");
    }
    List<JsonNode> sortedPolicies = new java.util.ArrayList<>();
    policySet.path("policies").forEach(sortedPolicies::add);
    sortedPolicies.sort(
        Comparator.comparing((JsonNode policy) -> policy.path("artifactType").asText())
            .thenComparing(policy -> policy.path("schemaVersion").asText()));

    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    sortedPolicies.forEach(policy -> policies.add(policy.deepCopy()));
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

  private static CanonicalModuleArtifactStore moduleStore(
      RunStoreHandle handle,
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry policies) {
    return new FileSystemCanonicalModuleArtifactStore(
        handle, canonicalJson, policies, new ArtifactStoreLimits(4, 1_000_000, 4_000_000, 8));
  }

  private static CanonicalAnalysisStepArtifactStore stepStore(
      RunStoreHandle handle,
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry policies) {
    return new FileSystemCanonicalAnalysisStepArtifactStore(
        handle, canonicalJson, policies, new ArtifactStoreLimits(4, 1_000_000, 4_000_000, 8));
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        new Sha256Digest("a".repeat(64)),
        new Sha256Digest("b".repeat(64)),
        new Sha256Digest("c".repeat(64)),
        null,
        policies.reference());
  }

  private static AnalysisRunId runId(char identity) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(identity).repeat(64));
  }

  private static List<Path> receiptFiles(Path root) throws IOException {
    if (!Files.exists(root.resolve("runs"))) {
      return List.of();
    }
    try (var paths = Files.walk(root.resolve("runs"))) {
      return paths
          .filter(path -> path.getFileName().toString().equals("module-receipt.json"))
          .toList();
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return concatenate(u64(value.length), value);
  }

  private static byte[] u64(long value) {
    return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private record ProducerFixture(
      char runIdentity,
      int moduleNumber,
      String moduleKey,
      String moduleVersion,
      List<CanonicalModulePayload> payloads,
      List<String> expectedFileNames,
      List<String> expectedSchemaVersions) {}
}
