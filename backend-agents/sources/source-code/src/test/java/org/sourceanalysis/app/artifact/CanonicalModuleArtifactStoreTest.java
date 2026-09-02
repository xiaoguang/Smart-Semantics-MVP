package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalModuleArtifactStoreTest {

  @TempDir Path emptyTemporaryDirectory;

  @Test
  void exposesThePathFreeModuleStoreAndItsFixedFilesystemConstructionSeam() {
    assertThat(typeOrNull("org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore"))
        .as("the module store must be a public path-free contract")
        .isNotNull();
    assertThat(typeOrNull("org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore"))
        .as("the filesystem implementation must be constructible only through its fixed seam")
        .isNotNull();
    assertThat(typeOrNull("org.sourceanalysis.app.artifact.RunStoreBootstrap"))
        .as("the only path-taking store bootstrap must exist")
        .isNotNull();
    assertThat(typeOrNull("org.sourceanalysis.app.artifact.RunStoreHandle"))
        .as("the store root must remain opaque after bootstrap")
        .isNotNull();
    assertThat(typeOrNull("org.sourceanalysis.app.artifact.ArtifactStoreLimits"))
        .as("the store must accept explicit resource limits")
        .isNotNull();
  }

  @Test
  void opensAnExistingEmptyTestRootThroughAnOpaqueHandle() {
    assertThatCode(
            () -> {
              try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
                assertThat(handle).isNotNull();
                assertThat(handle).isNotInstanceOf(Path.class);
              }
            })
        .doesNotThrowAnyException();
  }

  @Test
  void installsAndFreshReopensTheVerifiedSourceRequestModuleArtifactIdempotently() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = requestAdmissionPolicyRegistry(canonicalJson);
    ModuleInstallRequest request = requestAdmissionInstallRequest(canonicalJson, policies);

    assertThatCode(
            () -> {
              try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
                CanonicalModuleArtifactStore store =
                    new FileSystemCanonicalModuleArtifactStore(
                        handle,
                        canonicalJson,
                        policies,
                        new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));

                InstalledModulePublication installed = store.install(request);
                assertThat(installed.disposition()).isEqualTo(ModuleInstallDisposition.INSTALLED);
                assertThat(installed.artifactDescriptors())
                    .singleElement()
                    .satisfies(
                        descriptor ->
                            assertThat(descriptor.fileName())
                                .isEqualTo("admitted-source-request.json"));
                assertIndependentPublicationWire(
                    canonicalJson, request, installed.reference(), emptyTemporaryDirectory);

                ReopenedModulePublication reopened = store.reopen(installed.reference());
                assertThat(reopened.payloads())
                    .singleElement()
                    .satisfies(
                        payload ->
                            assertThat(payload.canonicalUtf8())
                                .isEqualTo(request.payloads().get(0).canonicalUtf8()));

                InstalledModulePublication repeated = store.install(request);
                assertThat(repeated.disposition())
                    .isEqualTo(ModuleInstallDisposition.ALREADY_INSTALLED);
                assertThat(repeated.reference()).isEqualTo(installed.reference());
              }
            })
        .doesNotThrowAnyException();
  }

  @Test
  void installsAndFreshReopensTheRegisteredVerifiedSourceIndexModuleArtifact() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = inventoryModulePolicyRegistry(canonicalJson);
    ModuleInstallRequest request = sourceIndexInstallRequest(canonicalJson, policies);

    assertThatCode(
            () -> {
              try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
                CanonicalModuleArtifactStore store =
                    new FileSystemCanonicalModuleArtifactStore(
                        handle,
                        canonicalJson,
                        policies,
                        new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));

                InstalledModulePublication installed = store.install(request);
                assertThat(installed.artifactDescriptors())
                    .singleElement()
                    .satisfies(
                        descriptor ->
                            assertThat(descriptor.fileName())
                                .isEqualTo("verified-source-index.json"));
                assertThat(store.reopen(installed.reference()).payloads())
                    .singleElement()
                    .satisfies(
                        payload ->
                            assertThat(payload.descriptor().artifactType())
                                .isEqualTo("VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX"));
              }
            })
        .doesNotThrowAnyException();
  }

  @Test
  void installsAndFreshReopensTheExactThreeVerifiedSourcePublisherArtifacts() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = publisherModulePolicyRegistry(canonicalJson);
    ModuleInstallRequest request = sourcePublicationInstallRequest(canonicalJson, policies);

    assertThatCode(
            () -> {
              try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
                CanonicalModuleArtifactStore store =
                    new FileSystemCanonicalModuleArtifactStore(
                        handle,
                        canonicalJson,
                        policies,
                        new ArtifactStoreLimits(3, 1_000_000, 2_000_000, 4));

                InstalledModulePublication installed = store.install(request);
                assertThat(installed.artifactDescriptors())
                    .extracting(ArtifactDescriptor::fileName)
                    .containsExactly(
                        "source-input.json", "source-inventory.jsonl", "verified-snapshot.json");
                assertThat(store.reopen(installed.reference()).payloads())
                    .extracting(payload -> payload.descriptor().artifactType())
                    .containsExactly(
                        "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
                        "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
                        "VERIFIED_SNAPSHOT");
              }
            })
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsAVerifiedSourcePublisherInstallThatOmitsOneOfItsContractualArtifacts() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = publisherModulePolicyRegistry(canonicalJson);
    ModuleInstallRequest complete = sourcePublicationInstallRequest(canonicalJson, policies);
    ModuleInstallRequest incomplete =
        new ModuleInstallRequest(
            complete.address(),
            complete.moduleVersion(),
            complete.upstreamArtifacts(),
            complete.controls(),
            complete.status(),
            complete.gapRefs(),
            List.of(complete.payloads().get(0), complete.payloads().get(2)));

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(3, 1_000_000, 2_000_000, 4));

      assertThatThrownBy(() -> store.install(incomplete))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("MODULE_INSTALL_REQUEST_INVALID"));
    }
  }

  @Test
  void rejectsAVerifiedSourcePublisherInstallWhosePayloadInputOrderIsNotCanonical() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = publisherModulePolicyRegistry(canonicalJson);
    ModuleInstallRequest complete = sourcePublicationInstallRequest(canonicalJson, policies);
    ModuleInstallRequest outOfOrder =
        new ModuleInstallRequest(
            complete.address(),
            complete.moduleVersion(),
            complete.upstreamArtifacts(),
            complete.controls(),
            complete.status(),
            complete.gapRefs(),
            List.of(
                complete.payloads().get(0),
                complete.payloads().get(2),
                complete.payloads().get(1)));

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(3, 1_000_000, 2_000_000, 4));

      assertThatThrownBy(() -> store.install(outOfOrder))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("MODULE_INSTALL_REQUEST_INVALID"));
    }
  }

  @Test
  void reportsAPersistedSourcePublisherPayloadMutationAsAnInvalidPublication() throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = publisherModulePolicyRegistry(canonicalJson);
    ModuleInstallRequest request = sourcePublicationInstallRequest(canonicalJson, policies);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(3, 1_000_000, 2_000_000, 4));
      InstalledModulePublication installed = store.install(request);
      Path sourceInput =
          emptyTemporaryDirectory
              .resolve("runs")
              .resolve(
                  "analysis-run--0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
              .resolve("steps")
              .resolve("01-verified-source-inventory")
              .resolve("modules")
              .resolve("03-publish")
              .resolve("source-input.json");
      ObjectNode mutated =
          (ObjectNode)
              canonicalJson.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(sourceInput)));
      mutated.put("sourceRegistrationId", "source-registration:" + String.valueOf('9').repeat(64));
      Files.write(sourceInput, canonicalJson.encodeCanonical(mutated).copyToByteArray());

      assertThatThrownBy(() -> store.reopen(installed.reference()))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("MODULE_PUBLICATION_INVALID"));
    }
  }

  @Test
  void rejectsAPublicationWhoseReceiptWasRemovedAfterInstallation() throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = requestAdmissionPolicyRegistry(canonicalJson);
    ModuleInstallRequest request = requestAdmissionInstallRequest(canonicalJson, policies);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));
      InstalledModulePublication installed = store.install(request);
      Files.delete(
          emptyTemporaryDirectory
              .resolve("runs")
              .resolve(
                  "analysis-run--0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
              .resolve("steps")
              .resolve("01-verified-source-inventory")
              .resolve("modules")
              .resolve("01-request-admission")
              .resolve("module-receipt.json"));

      assertThatThrownBy(() -> store.reopen(installed.reference()))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("MODULE_PUBLICATION_INVALID"));
    }
  }

  @Test
  void rejectsARequestWhoseControlsNameADifferentPolicyRegistryThanTheConfiguredStore() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry configuredPolicies =
        requestAdmissionPolicyRegistry(canonicalJson);
    CanonicalArtifactPolicyRegistry foreignPolicies =
        requestAdmissionPolicyRegistryWithAdditionalPolicy(canonicalJson);
    ModuleInstallRequest request = requestAdmissionInstallRequest(canonicalJson, foreignPolicies);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle,
              canonicalJson,
              configuredPolicies,
              new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));

      assertThatThrownBy(() -> store.install(request))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("ARTIFACT_POLICY_MISMATCH"));
    }
  }

  @Test
  void rejectsReopenWhoseReceiptNamesADifferentPolicyRegistryThanTheConfiguredStore()
      throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry configuredPolicies =
        requestAdmissionPolicyRegistry(canonicalJson);
    CanonicalArtifactPolicyRegistry foreignPolicies =
        requestAdmissionPolicyRegistryWithAdditionalPolicy(canonicalJson);
    ModuleInstallRequest request =
        requestAdmissionInstallRequest(canonicalJson, configuredPolicies);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle,
              canonicalJson,
              configuredPolicies,
              new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));
      InstalledModulePublication installed = store.install(request);
      ModulePublicationReference alteredReference =
          rewriteReceipt(
              canonicalJson,
              installed,
              emptyTemporaryDirectory,
              receipt -> {
                ObjectNode registry =
                    receipt.withObject("controls").withObject("artifactPolicyRegistryRef");
                registry.put("artifactId", foreignPolicies.reference().artifactId().value());
                registry.put("sha256", foreignPolicies.reference().sha256().value());
              });

      assertThatThrownBy(() -> store.reopen(alteredReference))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("ARTIFACT_POLICY_MISMATCH"));
    }
  }

  @Test
  void rejectsAnExistingSymbolicLinkInTheStoreParentChain() throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = requestAdmissionPolicyRegistry(canonicalJson);
    ModuleInstallRequest request = requestAdmissionInstallRequest(canonicalJson, policies);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      Path outsideRoot = Files.createDirectory(emptyTemporaryDirectory.resolve("outside"));
      Files.createSymbolicLink(emptyTemporaryDirectory.resolve("runs"), outsideRoot);
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));

      assertThatThrownBy(() -> store.install(request))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("MODULE_PUBLICATION_INVALID"));
    }
  }

  @Test
  void rejectsASelfConsistentReceiptThatDeclaresNoPayloadArtifacts() throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = requestAdmissionPolicyRegistry(canonicalJson);
    ModuleInstallRequest request = requestAdmissionInstallRequest(canonicalJson, policies);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory)) {
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(1, 1_000_000, 2_000_000, 4));
      InstalledModulePublication installed = store.install(request);
      Path directory = publicationDirectory(emptyTemporaryDirectory);
      Path receiptPath = directory.resolve("module-receipt.json");
      ObjectNode receipt =
          (ObjectNode)
              canonicalJson.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(receiptPath)));
      receipt.putArray("payloadArtifacts");
      String emptyRoot =
          "module-root:"
              + sha256Hex(concatenate(frame("canonical-module-artifact-root-v1"), u32(0)));
      receipt.put("moduleArtifactRoot", emptyRoot);
      receipt.remove("moduleReceiptId");
      String receiptId =
          "module-receipt:"
              + sha256Hex(
                  concatenate(
                      frame("canonical-module-receipt-id-v1"),
                      frame(canonicalJson.encodeCanonical(receipt).copyToByteArray())));
      receipt.put("moduleReceiptId", receiptId);
      byte[] receiptBytes = canonicalJson.encodeCanonical(receipt).copyToByteArray();
      Files.write(receiptPath, receiptBytes);
      Files.delete(directory.resolve("admitted-source-request.json"));
      ModulePublicationReference emptyPublicationReference =
          new ModulePublicationReference(
              installed.reference().address(),
              ModuleArtifactRoot.parse(emptyRoot),
              ModuleReceiptId.parse(receiptId),
              new Sha256Digest(sha256Hex(receiptBytes)));

      assertThatThrownBy(() -> store.reopen(emptyPublicationReference))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure -> assertThat(failure.code()).isEqualTo("MODULE_PUBLICATION_INVALID"));
    }
  }

  private static Class<?> typeOrNull(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }

  private static CanonicalArtifactPolicyRegistry requestAdmissionPolicyRegistry(
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

  private static CanonicalArtifactPolicyRegistry requestAdmissionPolicyRegistryWithAdditionalPolicy(
      CanonicalJsonCodec canonicalJson) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode withoutId = mapper.createObjectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    addRequestAdmissionPolicy(policies.addObject());
    ObjectNode extraPolicy = policies.addObject();
    extraPolicy.put("artifactType", "ZZZ_TEST_ONLY");
    extraPolicy.put("schemaVersion", "zzz-test-only-v1");
    extraPolicy.put("artifactIdPrefix", "zzz-test-only");
    extraPolicy.put("mediaType", "application/json");
    extraPolicy.put("envelopeKind", "MODULE_ARTIFACT_JSON");
    extraPolicy.put("emptyJsonlAllowed", false);
    extraPolicy.put("publicContentExposure", "METADATA_ONLY");
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

  private static CanonicalArtifactPolicyRegistry inventoryModulePolicyRegistry(
      CanonicalJsonCodec canonicalJson) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode withoutId = mapper.createObjectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    addRequestAdmissionPolicy(policies.addObject());
    ObjectNode sourceIndex = policies.addObject();
    sourceIndex.put("artifactType", "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX");
    sourceIndex.put("schemaVersion", "verified-source-inventory-verified-source-index-v2");
    sourceIndex.put("artifactIdPrefix", "source-index");
    sourceIndex.put("mediaType", "application/json");
    sourceIndex.put("envelopeKind", "MODULE_ARTIFACT_JSON");
    sourceIndex.put("emptyJsonlAllowed", false);
    sourceIndex.put("publicContentExposure", "METADATA_ONLY");
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

  private static CanonicalArtifactPolicyRegistry publisherModulePolicyRegistry(
      CanonicalJsonCodec canonicalJson) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode withoutId = mapper.createObjectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    addPolicy(
        policies.addObject(),
        "VERIFIED_SNAPSHOT",
        "verified-snapshot-v2",
        "verified-snapshot",
        "application/json",
        "STANDALONE_JSON",
        false);
    addPolicy(
        policies.addObject(),
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
        "verified-source-inventory-source-input-v2",
        "verified-source-inventory-source-input",
        "application/json",
        "STANDALONE_JSON",
        false);
    addPolicy(
        policies.addObject(),
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
        "verified-source-inventory-source-inventory-v2",
        "verified-source-inventory-source-inventory",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        false);
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

  private static void addRequestAdmissionPolicy(ObjectNode policy) {
    policy.put("artifactType", "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST");
    policy.put("schemaVersion", "verified-source-inventory-admitted-source-request-v2");
    policy.put("artifactIdPrefix", "source-request");
    policy.put("mediaType", "application/json");
    policy.put("envelopeKind", "MODULE_ARTIFACT_JSON");
    policy.put("emptyJsonlAllowed", false);
    policy.put("publicContentExposure", "METADATA_ONLY");
  }

  private static void addPolicy(
      ObjectNode policy,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      String mediaType,
      String envelopeKind,
      boolean emptyJsonlAllowed) {
    policy.put("artifactType", artifactType);
    policy.put("schemaVersion", schemaVersion);
    policy.put("artifactIdPrefix", artifactIdPrefix);
    policy.put("mediaType", mediaType);
    policy.put("envelopeKind", envelopeKind);
    policy.put("emptyJsonlAllowed", emptyJsonlAllowed);
    policy.put("publicContentExposure", "METADATA_ONLY");
  }

  private static ModuleInstallRequest requestAdmissionInstallRequest(
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
    ObjectNode withoutArtifactId =
        requestAdmissionEnvelopeWithoutArtifactId(
            canonicalJson, address, upstreamArtifacts, controls);
    String artifactId =
        "source-request:"
            + sha256Hex(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame("verified-source-inventory-admitted-source-request-v2"),
                    frame("VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST"),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode envelope = withoutArtifactId.deepCopy();
    envelope.put("artifactId", artifactId);
    CanonicalModulePayload payload =
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
        List.of(payload));
  }

  private static ModuleInstallRequest sourceIndexInstallRequest(
      CanonicalJsonCodec canonicalJson, CanonicalArtifactPolicyRegistry policies) {
    AnalysisRunId runId =
        AnalysisRunId.parse(
            "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 2, "source-index");
    List<ArtifactReference> upstreamArtifacts =
        List.of(reference("source-registration", '7'), reference("source-request", '8'));
    ArtifactControls controls =
        new ArtifactControls(digest('a'), digest('b'), digest('c'), null, policies.reference());
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode envelope = mapper.createObjectNode();
    envelope.put("schemaVersion", "verified-source-inventory-verified-source-index-v2");
    envelope.put("artifactType", "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX");
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
    payload.put("snapshotId", "snapshot:" + digest('d').value());
    payload.put("requestArtifactId", "source-request:" + digest('e').value());
    payload.put("verifiedRegularFileCount", 1);
    payload.put("analyzableTextFileCount", 1);
    payload.put("nonAnalyzableMediaFileCount", 0);
    payload.putArray("verifiedFiles");
    payload.putArray("shardReceipts");
    payload.put("sourceIntegrity", "VERIFIED");

    ObjectNode withoutArtifactId =
        (ObjectNode) canonicalJson.parseCanonical(canonicalJson.encodeCanonical(envelope));
    String artifactId =
        "source-index:"
            + sha256Hex(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame("verified-source-inventory-verified-source-index-v2"),
                    frame("VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX"),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    envelope.put("artifactId", artifactId);
    CanonicalModulePayload sourceIndex =
        new CanonicalModulePayload(
            "verified-source-index.json",
            "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX",
            "verified-source-inventory-verified-source-index-v2",
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
        List.of(sourceIndex));
  }

  private static ModuleInstallRequest sourcePublicationInstallRequest(
      CanonicalJsonCodec canonicalJson, CanonicalArtifactPolicyRegistry policies) {
    AnalysisRunId runId =
        AnalysisRunId.parse(
            "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 3, "publish");
    List<ArtifactReference> upstreamArtifacts =
        List.of(
            reference("frozen-request", '1'),
            reference("run-request", '2'),
            reference("source-index", '3'),
            reference("source-request", '4'));
    ArtifactControls controls =
        new ArtifactControls(digest('a'), digest('b'), digest('c'), null, policies.reference());
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode sourceInput = mapper.createObjectNode();
    sourceInput.put("schemaVersion", "verified-source-inventory-source-input-v2");
    sourceInput.put("artifactType", "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT");
    sourceInput.put("sourceInputId", "verified-source-inventory-input:" + digest('d').value());
    sourceInput.put("sourceRegistrationId", "source-registration:" + digest('e').value());
    ObjectNode verifiedSnapshot = mapper.createObjectNode();
    verifiedSnapshot.put("schemaVersion", "verified-snapshot-v2");
    verifiedSnapshot.put("artifactType", "VERIFIED_SNAPSHOT");
    verifiedSnapshot.put("snapshotId", "snapshot:" + digest('f').value());
    ObjectNode inventoryEntry = mapper.createObjectNode();
    inventoryEntry.put("schemaVersion", "source-inventory-entry-v2");
    inventoryEntry.put("fileId", "file:" + digest('7').value());
    inventoryEntry.put(
        "path", "jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java");

    return new ModuleInstallRequest(
        address,
        "v2",
        upstreamArtifacts,
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
                sourceInput),
            jsonlPayload(
                canonicalJson,
                "source-inventory.jsonl",
                "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
                "verified-source-inventory-source-inventory-v2",
                "verified-source-inventory-source-inventory",
                inventoryEntry),
            standalonePayload(
                canonicalJson,
                "verified-snapshot.json",
                "VERIFIED_SNAPSHOT",
                "verified-snapshot-v2",
                "verified-snapshot",
                verifiedSnapshot)));
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      ObjectNode document) {
    ObjectNode withoutArtifactId =
        (ObjectNode) canonicalJson.parseCanonical(canonicalJson.encodeCanonical(document));
    String artifactId =
        artifactIdPrefix
            + ":"
            + sha256Hex(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
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
      String artifactIdPrefix,
      ObjectNode entry) {
    ImmutableBytes canonicalLine = canonicalJson.encodeCanonical(entry);
    byte[] jsonl =
        concatenate(canonicalLine.copyToByteArray(), "\n".getBytes(StandardCharsets.UTF_8));
    String artifactId =
        artifactIdPrefix
            + ":"
            + sha256Hex(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(jsonl)));
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(jsonl));
  }

  private static ObjectNode requestAdmissionEnvelopeWithoutArtifactId(
      CanonicalJsonCodec canonicalJson,
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstreamArtifacts,
      ArtifactControls controls) {
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
    return canonicalJson.parseCanonical(canonicalJson.encodeCanonical(envelope)).deepCopy();
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

  private static void assertIndependentPublicationWire(
      CanonicalJsonCodec canonicalJson,
      ModuleInstallRequest request,
      ModulePublicationReference reference,
      Path root)
      throws Exception {
    Path publicationDirectory = publicationDirectory(root);
    try (Stream<Path> files = Files.list(publicationDirectory)) {
      assertThat(files.map(path -> path.getFileName().toString()).collect(Collectors.toSet()))
          .containsExactlyInAnyOrder("admitted-source-request.json", "module-receipt.json");
    }

    byte[] payloadBytes =
        Files.readAllBytes(publicationDirectory.resolve("admitted-source-request.json"));
    ObjectNode payload =
        (ObjectNode) canonicalJson.parseCanonical(ImmutableBytes.copyOf(payloadBytes));
    ObjectNode payloadWithoutId = payload.deepCopy();
    payloadWithoutId.remove("artifactId");
    String expectedArtifactId =
        "source-request:"
            + sha256Hex(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame("verified-source-inventory-admitted-source-request-v2"),
                    frame("VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST"),
                    frame(canonicalJson.encodeCanonical(payloadWithoutId).copyToByteArray())));
    assertThat(payload.path("artifactId").asText()).isEqualTo(expectedArtifactId);

    String payloadSha256 = sha256Hex(payloadBytes);
    byte[] descriptor =
        concatenate(
            frame("admitted-source-request.json"),
            frame("VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST"),
            frame("verified-source-inventory-admitted-source-request-v2"),
            frame(expectedArtifactId),
            frame("application/json"),
            u64(payloadBytes.length),
            HexFormat.of().parseHex(payloadSha256));
    String expectedRoot =
        "module-root:"
            + sha256Hex(
                concatenate(frame("canonical-module-artifact-root-v1"), u32(1), frame(descriptor)));
    assertThat(reference.moduleArtifactRoot().value()).isEqualTo(expectedRoot);

    byte[] receiptBytes = Files.readAllBytes(publicationDirectory.resolve("module-receipt.json"));
    ObjectNode receipt =
        (ObjectNode) canonicalJson.parseCanonical(ImmutableBytes.copyOf(receiptBytes));
    assertThat(receipt.path("moduleArtifactRoot").asText()).isEqualTo(expectedRoot);
    assertThat(receipt.path("payloadArtifacts"))
        .singleElement()
        .satisfies(
            descriptorNode -> {
              assertThat(descriptorNode.path("fileName").asText())
                  .isEqualTo("admitted-source-request.json");
              assertThat(descriptorNode.path("sizeBytes").asLong()).isEqualTo(payloadBytes.length);
              assertThat(descriptorNode.path("sha256").asText()).isEqualTo(payloadSha256);
            });
    ObjectNode receiptWithoutId = receipt.deepCopy();
    receiptWithoutId.remove("moduleReceiptId");
    String expectedReceiptId =
        "module-receipt:"
            + sha256Hex(
                concatenate(
                    frame("canonical-module-receipt-id-v1"),
                    frame(canonicalJson.encodeCanonical(receiptWithoutId).copyToByteArray())));
    assertThat(receipt.path("moduleReceiptId").asText()).isEqualTo(expectedReceiptId);
    assertThat(reference.moduleReceiptId().value()).isEqualTo(expectedReceiptId);
    assertThat(reference.moduleReceiptSha256().value()).isEqualTo(sha256Hex(receiptBytes));
    assertThat(canonicalJson.encodeCanonical(receipt).copyToByteArray())
        .containsExactly(receiptBytes);
    assertThat(request.payloads()).hasSize(1);
  }

  private static Path publicationDirectory(Path root) {
    return root.resolve("runs")
        .resolve("analysis-run--0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        .resolve("steps")
        .resolve("01-verified-source-inventory")
        .resolve("modules")
        .resolve("01-request-admission");
  }

  private static ModulePublicationReference rewriteReceipt(
      CanonicalJsonCodec canonicalJson,
      InstalledModulePublication installed,
      Path root,
      Consumer<ObjectNode> mutation)
      throws Exception {
    Path receiptPath = publicationDirectory(root).resolve("module-receipt.json");
    ObjectNode receipt =
        (ObjectNode)
            canonicalJson.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(receiptPath)));
    mutation.accept(receipt);
    receipt.remove("moduleReceiptId");
    String receiptId =
        "module-receipt:"
            + sha256Hex(
                concatenate(
                    frame("canonical-module-receipt-id-v1"),
                    frame(canonicalJson.encodeCanonical(receipt).copyToByteArray())));
    receipt.put("moduleReceiptId", receiptId);
    byte[] receiptBytes = canonicalJson.encodeCanonical(receipt).copyToByteArray();
    Files.write(receiptPath, receiptBytes);
    return new ModulePublicationReference(
        installed.reference().address(),
        ModuleArtifactRoot.parse(receipt.path("moduleArtifactRoot").asText()),
        ModuleReceiptId.parse(receiptId),
        new Sha256Digest(sha256Hex(receiptBytes)));
  }

  private static Sha256Digest digest(char digit) {
    return Sha256Digest.parse(String.valueOf(digit).repeat(64));
  }

  private static byte[] frame(String text) {
    return frame(text.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] bytes) {
    return ByteBuffer.allocate(Long.BYTES + bytes.length).putLong(bytes.length).put(bytes).array();
  }

  private static byte[] u32(int value) {
    return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
  }

  private static byte[] u64(long value) {
    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
  }

  private static byte[] concatenate(byte[]... segments) {
    int totalLength = 0;
    for (byte[] segment : segments) {
      totalLength += segment.length;
    }
    byte[] joined = new byte[totalLength];
    int offset = 0;
    for (byte[] segment : segments) {
      System.arraycopy(segment, 0, joined, offset, segment.length);
      offset += segment.length;
    }
    return joined;
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
