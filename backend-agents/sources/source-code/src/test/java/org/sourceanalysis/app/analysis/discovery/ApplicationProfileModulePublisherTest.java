package org.sourceanalysis.app.analysis.discovery;

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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

class ApplicationProfileModulePublisherTest {

  @TempDir Path temporaryDirectory;

  @Test
  void exposesAPathFreeApplicationProfilePublicationSeam() {
    Class<?> publisher =
        typeOrNull("org.sourceanalysis.app.analysis.discovery.ApplicationProfileModulePublisher");
    Class<?> reference =
        typeOrNull("org.sourceanalysis.app.analysis.discovery.ApplicationProfileDraftReference");

    assertThat(publisher)
        .as("M1 must publish the detected profile before M2/M3 consume it")
        .isNotNull();
    assertThat(reference).as("M1 requires a typed fresh-reopen handoff").isNotNull();
    assertThat(publisher.getDeclaredConstructors())
        .as("application-profile publication must not accept a caller filesystem path")
        .allSatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));
  }

  @Test
  void publishesAndFreshReopensTheCanonicalApplicationProfileDraft() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    ApplicationProfile profile = profile(controls);
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            AnalysisRunId.parse("analysis-run:" + "1".repeat(64)),
            AnalysisStepKey.APPLICATION_DISCOVERY,
            1,
            "application-profile");

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      FileSystemCanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(2, 100_000, 200_000, 8));

      ApplicationProfileDraftReference draft =
          new ApplicationProfileModulePublisher(store).publish(address, profile);

      var reopened = store.reopen(draft.publication());
      assertThat(reopened.receipt().address()).isEqualTo(address);
      assertThat(reopened.receipt().status()).isEqualTo(ModuleCompletionStatus.SUCCEEDED);
      assertThat(reopened.receipt().controls()).isEqualTo(controls);
      assertThat(reopened.receipt().upstreamArtifacts())
          .containsExactly(
              profile.capabilityProfileRef(),
              profile.verifiedSnapshotRef(),
              profile.sourceInventoryRef());
      assertThat(reopened.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName())
                    .isEqualTo("application-profile-draft.json");
                assertThat(payload.descriptor().artifactType())
                    .isEqualTo("APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT");
                assertThat(payload.descriptor().schemaVersion())
                    .isEqualTo("application-discovery-application-profile-draft-v2");
                JsonNode document = canonicalJson.parseCanonical(payload.canonicalUtf8());
                assertThat(document.get("payload").get("applicationProfileId").textValue())
                    .isEqualTo(profile.applicationProfileId().value());
                assertThat(
                        document
                            .get("payload")
                            .get("capabilityProfileRef")
                            .get("artifactId")
                            .textValue())
                    .isEqualTo(profile.capabilityProfileRef().artifactId().value());
              });
    }
  }

  private static ApplicationProfile profile(ArtifactControls controls) {
    return new ApplicationProfile(
        ArtifactId.parse("application-profile:" + "2".repeat(64)),
        "snapshot:" + "3".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        ApplicationLanguage.JAVA,
        8,
        List.of(),
        List.of(),
        reference("capability-profile", '4'),
        reference("verified-source-inventory-source-inventory", '5'),
        reference("verified-snapshot", '6'),
        controls);
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    ArtifactPolicyRegistryReference policyReference = policies.reference();
    return new ArtifactControls(
        Sha256Digest.parse("a".repeat(64)),
        Sha256Digest.parse("b".repeat(64)),
        Sha256Digest.parse("c".repeat(64)),
        null,
        policyReference);
  }

  private static ArtifactReference reference(String prefix, char digit) {
    String value = String.valueOf(digit).repeat(64);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + value), Sha256Digest.parse(value));
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec canonicalJson) {
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    policies
        .addObject()
        .put("artifactType", "APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT")
        .put("schemaVersion", "application-discovery-application-profile-draft-v2")
        .put("artifactIdPrefix", "application-profile")
        .put("mediaType", "application/json")
        .put("envelopeKind", "MODULE_ARTIFACT_JSON")
        .put("emptyJsonlAllowed", false)
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
    withoutId.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + sha256(
                frame(
                    "canonical-artifact-policy-registry-id-v2",
                    canonicalJson.encodeCanonical(withoutId).copyToByteArray())));
    return CanonicalArtifactPolicyRegistry.load(
        canonicalJson.encodeCanonical(withoutId), canonicalJson);
  }

  private static byte[] frame(String domain, byte[] value) {
    byte[] domainBytes = domain.getBytes(StandardCharsets.UTF_8);
    return concatenate(frame(domainBytes), frame(value));
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

  private static Class<?> typeOrNull(String qualifiedName) {
    try {
      return Class.forName(qualifiedName);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }
}
