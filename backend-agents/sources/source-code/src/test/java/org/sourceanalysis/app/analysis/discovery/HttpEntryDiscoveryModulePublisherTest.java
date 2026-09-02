package org.sourceanalysis.app.analysis.discovery;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

class HttpEntryDiscoveryModulePublisherTest {

  @TempDir Path temporaryDirectory;

  @Test
  void exposesAPathFreeReceiptLastPublicationSeamForHttpEntryDiscovery() {
    Class<?> publisher =
        typeOrNull("org.sourceanalysis.app.analysis.discovery.HttpEntryDiscoveryModulePublisher");
    Class<?> reference =
        typeOrNull("org.sourceanalysis.app.analysis.discovery.HttpEntryDiscoveryDraftReference");

    assertThat(publisher)
        .as("M2 must persist entry discovery before M4 can publish application discovery")
        .isNotNull();
    assertThat(reference).as("M2 requires a typed fresh-reopen handoff").isNotNull();
    assertThat(publisher.getDeclaredConstructors())
        .as("HTTP-entry publication must not accept a caller filesystem path")
        .allSatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));
  }

  @Test
  void publishesAndFreshReopensTheCanonicalHttpEntryDiscoveryDraft() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    VerifiedSourceTextDocument controller =
        text(
            "src/main/java/com/example/DepotHeadController.java",
            """
            package com.example;

            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/depotHead")
            public class DepotHeadController {
              @PostMapping("/batchSetStatus")
              public String batchSetStatus(String status, String ids) {
                return "ok";
              }
            }
            """);
    ApplicationProfile profile = profile(controller, controls);
    VerifiedSourceTextReader sourceHandle = reference -> sourceTextSet(controller, controls);
    HttpEntryDiscovery discovery =
        new SpringHttpEntryDiscoverer(sourceHandle).discoverEntries(profile, frozenSource());
    AnalysisStepModuleAddress profileAddress = address(1, "application-profile");
    AnalysisStepModuleAddress entryAddress = address(2, "http-entry");

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      FileSystemCanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(2, 100_000, 200_000, 8));
      ApplicationProfileDraftReference profileDraft =
          new ApplicationProfileModulePublisher(store).publish(profileAddress, profile);

      HttpEntryDiscoveryDraftReference draft =
          new HttpEntryDiscoveryModulePublisher(store)
              .publish(entryAddress, profileDraft, profile, discovery);

      var reopened = store.reopen(draft.publication());
      assertThat(reopened.receipt().address()).isEqualTo(entryAddress);
      assertThat(reopened.receipt().status()).isEqualTo(ModuleCompletionStatus.SUCCEEDED);
      assertThat(reopened.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName()).isEqualTo("http-entry-discovery.json");
                assertThat(payload.descriptor().artifactType())
                    .isEqualTo("APPLICATION_DISCOVERY_HTTP_ENTRY_DISCOVERY");
                assertThat(payload.descriptor().schemaVersion())
                    .isEqualTo("application-discovery-http-entry-discovery-v2");
                assertThat(
                        canonicalJson
                            .parseCanonical(payload.canonicalUtf8())
                            .at("/payload/entries"))
                    .hasSize(1);
                assertThat(
                        canonicalJson
                            .parseCanonical(payload.canonicalUtf8())
                            .at("/payload/shardReceipts"))
                    .hasSize(1);
              });
    }
  }

  private static AnalysisStepModuleAddress address(int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        AnalysisRunId.parse("analysis-run:" + "1".repeat(64)),
        AnalysisStepKey.APPLICATION_DISCOVERY,
        moduleNumber,
        moduleKey);
  }

  private static ApplicationProfile profile(
      VerifiedSourceTextDocument document, ArtifactControls controls) {
    SourceExcerptV1 frameworkExcerpt =
        new SourceExcerptV1(
            new SourceLocatorV1(document.fileId(), document.path(), 0, 7, 1, 1, 1, 8),
            ImmutableBytes.copyOf("package".getBytes(StandardCharsets.UTF_8)),
            Sha256Digest.parse(sha256("package".getBytes(StandardCharsets.UTF_8))));
    return new ApplicationProfile(
        ArtifactId.parse("application-profile:" + "2".repeat(64)),
        "snapshot:" + "3".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        ApplicationLanguage.JAVA,
        8,
        List.of(
            new FrameworkSignal(
                FrameworkSignalKind.SPRING_MVC,
                frameworkExcerpt,
                SignalDisposition.SUPPORTED,
                null)),
        List.of(),
        reference("capability-profile", '4'),
        reference("verified-source-inventory-source-inventory", '5'),
        reference("verified-snapshot", '6'),
        controls);
  }

  private static VerifiedSourceTextSet sourceTextSet(
      VerifiedSourceTextDocument document, ArtifactControls controls) {
    return new VerifiedSourceTextSet(
        "snapshot:" + "3".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", '4'),
        reference("verified-source-inventory-source-inventory", '5'),
        reference("verified-snapshot", '6'),
        controls,
        List.of(document));
  }

  private static VerifiedSourceInventoryReference frozenSource() {
    return new VerifiedSourceInventoryReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(
                AnalysisRunId.parse("analysis-run:" + "7".repeat(64)),
                AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + "8".repeat(64)),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + "9".repeat(64)),
            digest('a')));
  }

  private static VerifiedSourceTextDocument text(String path, String content) {
    byte[] rawUtf8 = content.getBytes(StandardCharsets.UTF_8);
    String sha256 = sha256(rawUtf8);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse(
            "file:" + sha256((path + "\\n" + sha256).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        "text/plain",
        rawUtf8.length,
        Sha256Digest.parse(sha256),
        ImmutableBytes.copyOf(rawUtf8));
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(digest('b'), digest('c'), digest('d'), null, policies.reference());
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)), digest(digit));
  }

  private static Sha256Digest digest(char digit) {
    return Sha256Digest.parse(String.valueOf(digit).repeat(64));
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec canonicalJson) {
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    policy(
        policies,
        "APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT",
        "application-discovery-application-profile-draft-v2",
        "application-profile");
    policy(
        policies,
        "APPLICATION_DISCOVERY_HTTP_ENTRY_DISCOVERY",
        "application-discovery-http-entry-discovery-v2",
        "http-entry-discovery");
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

  private static void policy(
      ArrayNode policies, String artifactType, String schemaVersion, String artifactIdPrefix) {
    policies
        .addObject()
        .put("artifactType", artifactType)
        .put("schemaVersion", schemaVersion)
        .put("artifactIdPrefix", artifactIdPrefix)
        .put("mediaType", "application/json")
        .put("envelopeKind", "MODULE_ARTIFACT_JSON")
        .put("emptyJsonlAllowed", false)
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
  }

  private static byte[] frame(String domain, byte[] value) {
    return concatenate(frame(domain.getBytes(StandardCharsets.UTF_8)), frame(value));
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
