package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;
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

class AdmittedSourceRequestModulePublisherTest {

  @TempDir Path emptyTemporaryDirectory;

  @Test
  void exposesAPathFreeM1PublisherForTheAdmittedSourceRequestArtifact() {
    Class<?> publisher =
        typeOrNull(
            "org.sourceanalysis.app.analysis.inventory.AdmittedSourceRequestModulePublisher");
    Class<?> input =
        typeOrNull(
            "org.sourceanalysis.app.analysis.inventory.AdmittedSourceRequestPublicationInput");

    assertThat(publisher).as("M1 must publish its admitted request before M2 runs").isNotNull();
    assertThat(input).as("M1 publication needs a typed, path-free input").isNotNull();
    assertThat(publisher.getDeclaredConstructors())
        .allSatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));
  }

  @Test
  void persistsAndFreshReopensTheCompleteAdmittedSourceRequestBeforeSourceIndexing() {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(json);
    ArtifactReference policyReference =
        new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256());
    ArtifactReference runRequest = reference("run-request", '1');
    ArtifactReference sourceRegistration = reference("source-registration", '2');
    ArtifactReference frozenRequest = reference("frozen-request", '3');
    ArtifactReference captureReceipt = reference("capture-receipt", '4');
    ArtifactReference snapshotManifest = reference("snapshot-manifest", '5');
    ArtifactReference profile = reference("profile-bundle", '6');
    ArtifactReference budget = reference("resource-budget", '7');
    ArtifactReference toolchain = reference("toolchain", '8');
    ArtifactReference schema = reference("schema-bundle", '9');
    ArtifactReference prompt = reference("prompt-bundle", 'a');
    ArtifactReference verificationPolicy = reference("verification-policy", 'd');
    ArtifactReference capabilityProfile = reference("capability-profile", 'e');
    AdmittedSourceRequest admitted =
        new AdmittedSourceRequest(
            runRequest.artifactId().value(),
            sourceRegistration.artifactId(),
            "https://example.invalid/customer/erp.git",
            "8c30ce7861570458920175e200bb2a6442713580",
            frozenRequest,
            captureReceipt,
            snapshotManifest,
            InventoryScope.completeCapture(),
            true,
            2,
            List.of(
                file("docs/readme.txt", 'b', SourceAnalysisDisposition.ANALYZABLE_TEXT, "UTF-8"),
                file("images/logo.png", 'c', SourceAnalysisDisposition.NON_ANALYZABLE_MEDIA, null)),
            List.of(ArtifactId.parse("file:" + "b".repeat(64))),
            List.of(ArtifactId.parse("file:" + "c".repeat(64))),
            new RunRequestControls(profile, budget, toolchain, schema, prompt, policyReference));
    List<ArtifactReference> upstream =
        sorted(
            List.of(
                runRequest,
                sourceRegistration,
                frozenRequest,
                captureReceipt,
                snapshotManifest,
                budget,
                verificationPolicy,
                capabilityProfile));

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle, json, policies, new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));
      ModulePublicationReference publication =
          new AdmittedSourceRequestModulePublisher(modules)
              .publish(
                  new AdmittedSourceRequestPublicationInput(
                      runId(),
                      runRequest,
                      sourceRegistration,
                      verificationPolicy,
                      capabilityProfile,
                      upstream,
                      admitted));

      var reopened = modules.reopen(publication);
      assertThat(reopened.receipt().upstreamArtifacts()).isEqualTo(upstream);
      assertThat(reopened.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName())
                    .isEqualTo("admitted-source-request.json");
                JsonNode document = json.parseCanonical(payload.canonicalUtf8());
                JsonNode body = document.get("payload");
                assertThat(body.get("requestIdentity").textValue())
                    .isEqualTo(runRequest.artifactId().value());
                assertThat(body.get("sourceRegistrationId").textValue())
                    .isEqualTo(sourceRegistration.artifactId().value());
                assertThat(body.get("originRevision").textValue())
                    .isEqualTo("8c30ce7861570458920175e200bb2a6442713580");
                assertThat(body.get("repositoryCompletionEligible").booleanValue()).isTrue();
                assertThat(body.get("files").isArray()).isTrue();
                assertThat(body.get("files").get(0).get("path").textValue())
                    .isEqualTo("docs/readme.txt");
                assertThat(body.get("files").get(1).get("analysisDisposition").textValue())
                    .isEqualTo("NON_ANALYZABLE_MEDIA");
              });
      assertThat(modules.reopen(publication).reference()).isEqualTo(publication);
    }
  }

  private static Class<?> typeOrNull(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec json) {
    ObjectNode withoutIdentity = JsonNodeFactory.instance.objectNode();
    withoutIdentity.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutIdentity.putArray("policies");
    ObjectNode policy = policies.addObject();
    policy.put("artifactType", "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST");
    policy.put("schemaVersion", "verified-source-inventory-admitted-source-request-v2");
    policy.put("artifactIdPrefix", "source-request");
    policy.put("mediaType", "application/json");
    policy.put("envelopeKind", "MODULE_ARTIFACT_JSON");
    policy.put("emptyJsonlAllowed", false);
    policy.put("publicContentExposure", "METADATA_ONLY");
    ObjectNode document = withoutIdentity.deepCopy();
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + sha256(
                frame(
                    "canonical-artifact-policy-registry-id-v2",
                    json.encodeCanonical(withoutIdentity).copyToByteArray())));
    return CanonicalArtifactPolicyRegistry.load(json.encodeCanonical(document), json);
  }

  private static CapturedRegularFile file(
      String path, char identity, SourceAnalysisDisposition disposition, String encoding) {
    return new CapturedRegularFile(
        ArtifactId.parse("file:" + String.valueOf(identity).repeat(64)),
        path,
        "100644",
        disposition == SourceAnalysisDisposition.ANALYZABLE_TEXT ? "text/plain" : "image/png",
        10L,
        Sha256Digest.parse(String.valueOf(identity).repeat(64)),
        disposition,
        encoding);
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

  private static byte[] frame(String domain, byte[] content) {
    return concatenate(
        frame(domain.getBytes(java.nio.charset.StandardCharsets.UTF_8)), frame(content));
  }

  private static byte[] frame(byte[] value) {
    return java.nio.ByteBuffer.allocate(Long.BYTES + value.length)
        .order(java.nio.ByteOrder.BIG_ENDIAN)
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
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }
}
