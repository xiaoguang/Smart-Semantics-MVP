package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

class VerifiedSourceInventoryPublicationSpecifierTest {

  @TempDir Path emptyTemporaryDirectory;

  @Test
  void exposesThePathFreeM3PublicationSeamBeforeWritingSourceInventoryArtifacts() {
    Class<?> specifier =
        typeOrNull(
            "org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryPublicationSpecifier");
    Class<?> input =
        typeOrNull(
            "org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryPublicationSpecificationInputV1");
    Class<?> reference =
        typeOrNull("org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference");

    assertThat(specifier).as("M3 must be an explicit inventory publication seam").isNotNull();
    assertThat(input).as("M3 input must be a closed, typed request").isNotNull();
    assertThat(reference)
        .as("a successful M3 publication must return a typed reference")
        .isNotNull();
    assertThat(specifier.getDeclaredConstructors())
        .as("the M3 seam receives stores and no caller-owned source path")
        .allSatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));
  }

  @Test
  void projectsFreshM1AndM2PublicationsToTheExactThreeFileSourceInventorySet() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = sourceInventoryPolicies(canonicalJson);
    AnalysisRunId runId = runId();
    ArtifactControls controls = controls(policies);
    InputArtifacts inputArtifacts = inputArtifacts(canonicalJson, policies);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(3, 1_000_000, 2_000_000, 8));
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(3, 1_000_000, 2_000_000, 8));
      InstalledModulePublication admitted =
          modules.install(
              admittedRequest(canonicalJson, policies, runId, controls, inputArtifacts));
      InstalledModulePublication indexed =
          modules.install(sourceIndex(canonicalJson, policies, runId, controls, admitted));

      VerifiedSourceInventoryReference inventory =
          new VerifiedSourceInventoryPublicationSpecifier(modules, steps, inputArtifacts::reopen)
              .publish(
                  new VerifiedSourceInventoryPublicationSpecificationInputV1(
                      new org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress(
                          runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
                      admitted.reference(),
                      indexed.reference(),
                      inputArtifacts.runRequest(),
                      inputArtifacts.frozenRequest()));

      var reopened = steps.reopen(inventory.publication());
      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly("source-input.json", "source-inventory.jsonl", "verified-snapshot.json");
      assertThat(reopened.receipt().publicationProvenance())
          .isInstanceOf(AnalysisStepPublisherModuleProvenance.class);
      AnalysisStepPublisherModuleProvenance publisher =
          (AnalysisStepPublisherModuleProvenance) reopened.receipt().publicationProvenance();
      assertThat(publisher.publisherSpecificationModuleReference().address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 3, "publish"));

      JsonNode sourceInput =
          canonicalJson.parseCanonical(payload(reopened, "source-input.json").canonicalUtf8());
      assertThat(sourceInput.get("sourceRegistrationId").textValue())
          .isEqualTo("source-registration:" + String.valueOf('3').repeat(64));
      assertThat(sourceInput.get("frozenRepositoryRequestRef").get("artifactId").textValue())
          .isEqualTo(inputArtifacts.frozenRequest().artifactId().value());

      JsonNode snapshot =
          canonicalJson.parseCanonical(payload(reopened, "verified-snapshot.json").canonicalUtf8());
      assertThat(snapshot.get("snapshotId").textValue())
          .isEqualTo("snapshot:" + String.valueOf('4').repeat(64));
      assertThat(snapshot.get("trackedRegularFileCount").intValue()).isEqualTo(1);
      assertThat(snapshot.get("analyzableTextFileCount").intValue()).isEqualTo(1);

      String inventoryLine =
          new String(
                  payload(reopened, "source-inventory.jsonl").canonicalUtf8().copyToByteArray(),
                  StandardCharsets.UTF_8)
              .strip();
      JsonNode inventoryEntry =
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(inventoryLine.getBytes(StandardCharsets.UTF_8)));
      assertThat(inventoryEntry.get("path").textValue()).isEqualTo("src/DepotHeadService.java");
      assertThat(inventoryEntry.get("lineIndexDigest").textValue())
          .isEqualTo(String.valueOf('6').repeat(64));
    }
  }

  private static org.sourceanalysis.app.artifact.VerifiedCanonicalPayload payload(
      org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication publication,
      String fileName) {
    return publication.semanticPayloads().stream()
        .filter(candidate -> candidate.descriptor().fileName().equals(fileName))
        .findFirst()
        .orElseThrow();
  }

  private static ModuleInstallRequest admittedRequest(
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry policies,
      AnalysisRunId runId,
      ArtifactControls controls,
      InputArtifacts inputArtifacts) {
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 1, "request-admission");
    List<ArtifactReference> upstream =
        sorted(
            List.of(
                reference("capture-receipt", '5'),
                inputArtifacts.frozenRequest(),
                inputArtifacts.runRequest(),
                reference("snapshot-manifest", '7')));
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("requestIdentity", inputArtifacts.runRequest().artifactId().value());
    body.put("sourceRegistrationId", "source-registration:" + String.valueOf('3').repeat(64));
    body.put("originRepositoryUrl", "https://example.invalid/customer/jshERP.git");
    body.put("originRevision", "8c30ce7861570458920175e200bb2a6442713580");
    body.putObject("inventoryScope").put("kind", "COMPLETE_CAPTURE").putNull("scopeRoot");
    body.put("repositoryCompletionEligible", true);
    body.put("declaredPathCount", 1);
    ObjectNode file = body.putArray("files").addObject();
    file.put("path", "src/DepotHeadService.java");
    file.put("gitMode", "100644");
    file.put("mediaType", "text/x-java-source");
    file.put("sizeBytes", 100);
    file.put("sha256", String.valueOf('8').repeat(64));
    file.put("analysisDisposition", "ANALYZABLE_TEXT");
    file.put("textEncoding", "UTF-8");
    return moduleRequest(
        canonicalJson,
        policies,
        address,
        controls,
        upstream,
        "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST",
        "verified-source-inventory-admitted-source-request-v2",
        "source-request",
        "admitted-source-request.json",
        body);
  }

  private static ModuleInstallRequest sourceIndex(
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry policies,
      AnalysisRunId runId,
      ArtifactControls controls,
      InstalledModulePublication admitted) {
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 2, "source-index");
    ArtifactReference admittedPayload =
        new ArtifactReference(
            admitted.artifactDescriptors().get(0).artifactId(),
            admitted.artifactDescriptors().get(0).sha256());
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("snapshotId", "snapshot:" + String.valueOf('4').repeat(64));
    body.put("requestArtifactId", admittedPayload.artifactId().value());
    body.put("verifiedRegularFileCount", 1);
    body.put("analyzableTextFileCount", 1);
    body.put("nonAnalyzableMediaFileCount", 0);
    ObjectNode file = body.putArray("verifiedFiles").addObject();
    file.put("fileId", "file:" + String.valueOf('9').repeat(64));
    file.put("path", "src/DepotHeadService.java");
    file.put("gitMode", "100644");
    file.put("mediaType", "text/x-java-source");
    file.put("sizeBytes", 100);
    file.put("sha256", String.valueOf('8').repeat(64));
    file.put("analysisDisposition", "ANALYZABLE_TEXT");
    file.put("textEncoding", "UTF-8");
    file.put("lineIndexDigest", String.valueOf('6').repeat(64));
    ObjectNode shard = body.putArray("shardReceipts").addObject();
    shard.put("shardId", "source-shard:" + String.valueOf('a').repeat(64));
    shard.putArray("denominatorFileIds").add("file:" + String.valueOf('9').repeat(64));
    shard.putArray("verifiedFileIds").add("file:" + String.valueOf('9').repeat(64));
    shard.put("status", "SUCCEEDED");
    shard.putArray("gapIds");
    body.put("sourceIntegrity", "VERIFIED");
    return moduleRequest(
        canonicalJson,
        policies,
        address,
        controls,
        sorted(List.of(admittedPayload, reference("source-registration", '3'))),
        "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX",
        "verified-source-inventory-verified-source-index-v2",
        "source-index",
        "verified-source-index.json",
        body);
  }

  private static ModuleInstallRequest moduleRequest(
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry policies,
      AnalysisStepModuleAddress address,
      ArtifactControls controls,
      List<ArtifactReference> upstream,
      String artifactType,
      String schemaVersion,
      String artifactPrefix,
      String fileName,
      ObjectNode body) {
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", schemaVersion);
    withoutId.put("artifactType", artifactType);
    withoutId.set("producer", producer(address));
    withoutId.set("upstreamArtifacts", references(upstream));
    withoutId.set("controls", controls(controls));
    withoutId.set("completion", completion());
    withoutId.set("payload", body);
    String artifactId =
        artifactPrefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray())));
    ObjectNode document = withoutId.deepCopy();
    document.put("artifactId", artifactId);
    return new ModuleInstallRequest(
        address,
        "v2",
        upstream,
        controls,
        ModuleCompletionStatus.SUCCEEDED,
        List.of(),
        List.of(
            new CanonicalModulePayload(
                fileName,
                artifactType,
                schemaVersion,
                ArtifactId.parse(artifactId),
                CanonicalMediaType.APPLICATION_JSON,
                canonicalJson.encodeCanonical(document))));
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode producer = JsonNodeFactory.instance.objectNode();
    ObjectNode addressNode = producer.putObject("address");
    addressNode.put("kind", "ANALYSIS_STEP");
    addressNode.put("runId", address.runId().value());
    addressNode.put("analysisStepKey", address.analysisStepKey().wireValue());
    addressNode.put("moduleNumber", address.moduleNumber());
    addressNode.put("moduleKey", address.moduleKey());
    producer.put("moduleVersion", "v2");
    return producer;
  }

  private static ArrayNode references(List<ArtifactReference> references) {
    ArrayNode array = JsonNodeFactory.instance.arrayNode();
    for (ArtifactReference reference : references) {
      array
          .addObject()
          .put("artifactId", reference.artifactId().value())
          .put("sha256", reference.sha256().value());
    }
    return array;
  }

  private static ObjectNode controls(ArtifactControls controls) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("toolchainSha256", controls.toolchainSha256().value());
    node.put("profileSha256", controls.profileSha256().value());
    node.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    node.putNull("promptBundleSha256");
    ObjectNode registry = node.putObject("artifactPolicyRegistryRef");
    registry.put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value());
    registry.put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
    return node;
  }

  private static ObjectNode completion() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("status", "SUCCEEDED");
    node.putArray("gapRefs");
    node.putNull("failureRef");
    return node;
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
        "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST",
        "verified-source-inventory-admitted-source-request-v2",
        "source-request",
        "application/json",
        "MODULE_ARTIFACT_JSON");
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
    addPolicy(
        policies.addObject(),
        "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX",
        "verified-source-inventory-verified-source-index-v2",
        "source-index",
        "application/json",
        "MODULE_ARTIFACT_JSON");
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
      String artifactPrefix,
      String mediaType,
      String envelopeKind) {
    policy.put("artifactType", artifactType);
    policy.put("schemaVersion", schemaVersion);
    policy.put("artifactIdPrefix", artifactPrefix);
    policy.put("mediaType", mediaType);
    policy.put("envelopeKind", envelopeKind);
    policy.put("emptyJsonlAllowed", false);
    policy.put("publicContentExposure", "METADATA_ONLY");
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(digest('a'), digest('b'), digest('c'), null, policies.reference());
  }

  private static InputArtifacts inputArtifacts(
      CanonicalJsonCodec canonicalJson, CanonicalArtifactPolicyRegistry policies) {
    ArtifactReference captureReceipt = reference("capture-receipt", '5');
    ArtifactReference snapshotManifest = reference("snapshot-manifest", '7');
    ArtifactReference verificationPolicy = reference("verification-policy", 'a');
    ArtifactReference capabilityProfile = reference("capability-profile", 'b');
    ArtifactReference resourceBudget = reference("resource-budget", 'c');
    ObjectNode frozen = JsonNodeFactory.instance.objectNode();
    frozen.put("schemaVersion", "frozen-repository-request-v2");
    ObjectNode origin = frozen.putObject("expectedOrigin");
    origin.put("kind", "GIT_SHA1_COMMIT");
    origin.put("repositoryUrl", "https://example.invalid/customer/jshERP.git");
    origin.put("revision40", "8c30ce7861570458920175e200bb2a6442713580");
    frozen.set("captureReceiptRef", referenceNode(captureReceipt));
    frozen.set("snapshotManifestRef", referenceNode(snapshotManifest));
    ObjectNode scope = frozen.putObject("inventoryScope");
    scope.put("kind", "COMPLETE_CAPTURE");
    scope.putNull("scopeRoot");
    scope.put("declaredPathCount", 1);
    frozen.set("verificationPolicyRef", referenceNode(verificationPolicy));
    frozen.set("capabilityProfileRef", referenceNode(capabilityProfile));
    frozen.set("resourceBudgetRef", referenceNode(resourceBudget));
    ImmutableBytes frozenBytes = canonicalJson.encodeCanonical(frozen);
    ArtifactReference frozenRequest = contentReference("frozen-request", frozenBytes);

    ObjectNode request = JsonNodeFactory.instance.objectNode();
    request.putArray("approvedFindingRefs");
    request.set(
        "artifactPolicyRegistryRef",
        referenceNode(
            new ArtifactReference(
                policies.reference().artifactId(), policies.reference().sha256())));
    request.set("candidateSeriesRef", referenceNode(reference("candidate-series", 'd')));
    request.set("frozenRepositoryRequestRef", referenceNode(frozenRequest));
    request.putNull("organizationRegistrySeedRef");
    request.putNull("parentCandidateRef");
    request.set("profileBundleRef", referenceNode(reference("profile-bundle", 'e')));
    request.set("promptBundleRef", referenceNode(reference("prompt-bundle", 'f')));
    request.put("readerCandidateRound", "ROUND_1");
    request.set("resourceBudgetRef", referenceNode(resourceBudget));
    request.set("schemaBundleRef", referenceNode(reference("schema-bundle", '1')));
    request.put("schemaVersion", "analysis-run-request-v2");
    request.put("sourceRegistrationId", "source-registration:" + String.valueOf('3').repeat(64));
    request.set("toolchainRef", referenceNode(reference("toolchain", '2')));
    ImmutableBytes requestBytes = canonicalJson.encodeCanonical(request);
    ArtifactReference runRequest =
        new ArtifactReference(
            ArtifactId.parse(
                "run-request:"
                    + sha256(
                        concatenate(
                            frame("analysis-run-request-id-v2"),
                            frame(requestBytes.copyToByteArray())))),
            new Sha256Digest(sha256(requestBytes.copyToByteArray())));
    Map<ArtifactReference, ImmutableBytes> inputs = new LinkedHashMap<>();
    inputs.put(runRequest, requestBytes);
    inputs.put(frozenRequest, frozenBytes);
    return new InputArtifacts(runRequest, frozenRequest, Map.copyOf(inputs));
  }

  private static ObjectNode referenceNode(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static ArtifactReference contentReference(String prefix, ImmutableBytes bytes) {
    String digest = sha256(bytes.copyToByteArray());
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + digest), new Sha256Digest(digest));
  }

  private static AnalysisRunId runId() {
    return AnalysisRunId.parse(
        "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)), digest(digit));
  }

  private static Sha256Digest digest(char digit) {
    return Sha256Digest.parse(String.valueOf(digit).repeat(64));
  }

  private static List<ArtifactReference> sorted(List<ArtifactReference> references) {
    return references.stream()
        .sorted(Comparator.comparing(reference -> reference.artifactId().value()))
        .toList();
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

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record InputArtifacts(
      ArtifactReference runRequest,
      ArtifactReference frozenRequest,
      Map<ArtifactReference, ImmutableBytes> contents) {

    private ImmutableBytes reopen(ArtifactReference reference) {
      ImmutableBytes bytes = contents.get(reference);
      if (bytes == null) {
        throw new IllegalArgumentException("unknown input reference");
      }
      return bytes;
    }
  }

  private static Class<?> typeOrNull(String qualifiedName) {
    try {
      return Class.forName(qualifiedName);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }
}
