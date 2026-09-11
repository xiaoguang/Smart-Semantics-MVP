package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunStoreBootstrapProductionTest {

  @TempDir Path emptyTemporaryDirectory;

  @Test
  void opensProductionRootInstallsClosesAndReopensTheSameModuleArtifact() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = requestAdmissionPolicies(canonicalJson);
    ModuleInstallRequest request = requestAdmission(canonicalJson, policies);

    ModulePublicationReference installedReference;
    try (RunStoreHandle firstHandle = RunStoreBootstrap.open(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore firstStore =
          new FileSystemCanonicalModuleArtifactStore(
              firstHandle,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));

      InstalledModulePublication installed = firstStore.install(request);
      assertThat(installed.disposition()).isEqualTo(ModuleInstallDisposition.INSTALLED);
      installedReference = installed.reference();
    }

    try (RunStoreHandle secondHandle = RunStoreBootstrap.open(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore secondStore =
          new FileSystemCanonicalModuleArtifactStore(
              secondHandle,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));

      ReopenedModulePublication reopened = secondStore.reopen(installedReference);
      assertThat(reopened.reference()).isEqualTo(installedReference);
      assertThat(reopened.payloads())
          .singleElement()
          .extracting(VerifiedCanonicalPayload::canonicalUtf8)
          .isEqualTo(request.payloads().get(0).canonicalUtf8());
    }
  }

  @Test
  void retainsTheRealRootWhenAnAncestorAliasIsRetargetedBeforeInstall() throws Exception {
    Path originalParent = emptyTemporaryDirectory.resolve("original-parent");
    Path originalStoreRoot = originalParent.resolve("store");
    Path replacementParent = emptyTemporaryDirectory.resolve("replacement-parent");
    Files.createDirectory(originalParent);
    Files.createDirectory(originalStoreRoot);
    Files.createDirectory(replacementParent);
    Path parentAlias = emptyTemporaryDirectory.resolve("parent-alias");
    Path finalComponentAlias = emptyTemporaryDirectory.resolve("final-component-alias");
    try {
      Files.createSymbolicLink(parentAlias, originalParent);
      Files.createSymbolicLink(finalComponentAlias, originalStoreRoot);
    } catch (IOException | UnsupportedOperationException | SecurityException failure) {
      Assumptions.assumeTrue(
          false, "the host cannot create symbolic links for this security test: " + failure);
      return;
    }

    Path aliasedStoreRoot = parentAlias.resolve("store");
    assertThat(Files.isSymbolicLink(parentAlias)).isTrue();
    assertThat(Files.isDirectory(aliasedStoreRoot, LinkOption.NOFOLLOW_LINKS)).isTrue();
    assertThatThrownBy(() -> RunStoreBootstrap.open(finalComponentAlias))
        .isInstanceOf(IllegalArgumentException.class);

    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = requestAdmissionPolicies(canonicalJson);
    ModuleInstallRequest request = requestAdmission(canonicalJson, policies);
    try (RunStoreHandle handle = RunStoreBootstrap.open(aliasedStoreRoot)) {
      Files.delete(parentAlias);
      Files.createSymbolicLink(parentAlias, replacementParent);

      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));
      InstalledModulePublication installed = store.install(request);

      assertThat(Files.exists(originalStoreRoot.resolve("runs"), LinkOption.NOFOLLOW_LINKS))
          .isTrue();
      assertThat(Files.exists(replacementParent.resolve("store"), LinkOption.NOFOLLOW_LINKS))
          .isFalse();
      assertThat(store.reopen(installed.reference()).payloads())
          .singleElement()
          .extracting(VerifiedCanonicalPayload::canonicalUtf8)
          .isEqualTo(request.payloads().get(0).canonicalUtf8());
    }
  }

  private static CanonicalArtifactPolicyRegistry requestAdmissionPolicies(
      CanonicalJsonCodec canonicalJson) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode withoutId = mapper.createObjectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    ObjectNode policy = policies.addObject();
    policy.put("artifactType", "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST");
    policy.put("schemaVersion", "verified-source-inventory-admitted-source-request-v2");
    policy.put("artifactIdPrefix", "source-request");
    policy.put("mediaType", "application/json");
    policy.put("envelopeKind", "MODULE_ARTIFACT_JSON");
    policy.put("emptyJsonlAllowed", false);
    policy.put("publicContentExposure", "METADATA_ONLY");

    ObjectNode document = withoutId.deepCopy();
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + sha256Hex(
                concatenate(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(
        canonicalJson.encodeCanonical(document), canonicalJson);
  }

  private static ModuleInstallRequest requestAdmission(
      CanonicalJsonCodec canonicalJson, CanonicalArtifactPolicyRegistry policies) {
    AnalysisRunId runId =
        AnalysisRunId.parse(
            "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 1, "request-admission");
    List<ArtifactReference> upstreamArtifacts =
        List.of(
            reference("capability-profile", '1'),
            reference("capture-receipt", '2'),
            reference("frozen-request", '3'),
            reference("resource-budget", '4'),
            reference("run-request", '5'),
            reference("snapshot-manifest", '6'),
            reference("source-registration", '7'),
            reference("verification-policy", '8'));
    ArtifactControls controls =
        new ArtifactControls(digest('a'), digest('b'), digest('c'), null, policies.reference());

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode envelope = mapper.createObjectNode();
    envelope.put("schemaVersion", "verified-source-inventory-admitted-source-request-v2");
    envelope.put("artifactType", "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST");
    ObjectNode producer = envelope.putObject("producer");
    producer.put("moduleVersion", "v2");
    ObjectNode producerAddress = producer.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", address.runId().value());
    producerAddress.put("analysisStepKey", address.analysisStepKey().wireValue());
    producerAddress.put("moduleNumber", address.moduleNumber());
    producerAddress.put("moduleKey", address.moduleKey());
    ArrayNode upstream = envelope.putArray("upstreamArtifacts");
    for (ArtifactReference reference : upstreamArtifacts) {
      upstream
          .addObject()
          .put("artifactId", reference.artifactId().value())
          .put("sha256", reference.sha256().value());
    }
    appendControls(envelope.putObject("controls"), controls);
    ObjectNode completion = envelope.putObject("completion");
    completion.put("status", "SUCCEEDED");
    completion.putArray("gapRefs");
    completion.putNull("failureRef");
    ObjectNode payload = envelope.putObject("payload");
    payload.put("requestIdentity", "analysis-run-request:" + digest('e').value());
    payload.put("sourceRegistrationId", "source-registration:" + digest('f').value());
    payload.put("originRepositoryUrl", "https://gitee.com/jishenghua/JSH_ERP.git");
    payload.put("originRevision", "8c30ce7861570458920175e200bb2a6442713580");
    payload
        .putObject("inventoryScope")
        .put("kind", "BOUNDED_PATH_SET")
        .put("scopeRoot", "jshERP-boot");
    payload.put("repositoryCompletionEligible", false);
    payload.put("declaredPathCount", 1);
    ObjectNode file = payload.putArray("files").addObject();
    file.put("path", "jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java");
    file.put("gitMode", "100644");
    file.put("mediaType", "text/x-java-source");
    file.put("sizeBytes", 128);
    file.put("sha256", digest('d').value());
    file.put("analysisDisposition", "ANALYZABLE_TEXT");
    file.put("textEncoding", "UTF-8");

    ObjectNode withoutArtifactId = envelope.deepCopy();
    String artifactId =
        "source-request:"
            + sha256Hex(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame("verified-source-inventory-admitted-source-request-v2"),
                    frame("VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST"),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    envelope.put("artifactId", artifactId);
    CanonicalModulePayload payloadArtifact =
        new CanonicalModulePayload(
            "admitted-source-request.json",
            "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST",
            "verified-source-inventory-admitted-source-request-v2",
            ArtifactId.parse(artifactId),
            CanonicalMediaType.APPLICATION_JSON,
            canonicalJson.encodeCanonical(envelope));
    return new ModuleInstallRequest(
        address,
        "v2",
        upstreamArtifacts,
        controls,
        ModuleCompletionStatus.SUCCEEDED,
        List.of(),
        List.of(payloadArtifact));
  }

  private static void appendControls(ObjectNode controlsNode, ArtifactControls controls) {
    controlsNode.put("toolchainSha256", controls.toolchainSha256().value());
    controlsNode.put("profileSha256", controls.profileSha256().value());
    controlsNode.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    controlsNode.putNull("promptBundleSha256");
    controlsNode
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)), digest(digit));
  }

  private static Sha256Digest digest(char digit) {
    return new Sha256Digest(String.valueOf(digit).repeat(64));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] bytes) {
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static byte[] concatenate(byte[]... segments) {
    int length = 0;
    for (byte[] segment : segments) {
      length += segment.length;
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] segment : segments) {
      System.arraycopy(segment, 0, result, offset, segment.length);
      offset += segment.length;
    }
    return result;
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
