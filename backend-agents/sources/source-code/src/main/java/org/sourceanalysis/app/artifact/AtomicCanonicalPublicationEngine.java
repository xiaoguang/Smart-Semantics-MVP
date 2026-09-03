package org.sourceanalysis.app.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Shared package-private receipt-last filesystem publisher for the three canonical stores. */
final class AtomicCanonicalPublicationEngine {

  private static final String RECEIPT_FILE_NAME = "module-receipt.json";
  private static final String RECEIPT_SCHEMA = "module-receipt-v1";
  private static final String MODULE_ARTIFACT_ID_DOMAIN = "canonical-module-artifact-id-v1";
  private static final String MODULE_ARTIFACT_ROOT_DOMAIN = "canonical-module-artifact-root-v1";
  private static final String MODULE_RECEIPT_ID_DOMAIN = "canonical-module-receipt-id-v1";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Comparator<String> UTF8_ORDER =
      AtomicCanonicalPublicationEngine::compareUtf8;

  private final FileSystemRunStoreHandle runStore;
  private final CanonicalJsonCodec canonicalJson;
  private final CanonicalArtifactPolicyRegistry artifactPolicies;
  private final ArtifactStoreLimits limits;

  AtomicCanonicalPublicationEngine(
      FileSystemRunStoreHandle runStore,
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry artifactPolicies,
      ArtifactStoreLimits limits) {
    this.runStore = runStore;
    this.canonicalJson = canonicalJson;
    this.artifactPolicies = artifactPolicies;
    this.limits = limits;
  }

  InstalledModulePublication installModule(ModuleInstallRequest request) {
    ValidatedModuleInstall validated = validateInstallRequest(request);
    try {
      Path parent = createModuleParent(validated.request().address());
      String moduleName = moduleDirectoryName(validated.request().address());
      Path destination = parent.resolve(moduleName);
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        requireDirectoryWithoutLinks(destination);
        return reopenEquivalentOrRejectCollision(validated);
      }
      Path staging = Files.createTempDirectory(parent, moduleName + ".staging-");
      try {
        writePublication(staging, validated);
        verifyStagedPublication(staging, validated.reference());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
          throw collision();
        }
        moveAtomically(staging, destination);
      } finally {
        deleteIfStillPresent(staging);
      }
      return new InstalledModulePublication(
          validated.reference(), ModuleInstallDisposition.INSTALLED, validated.descriptors());
    } catch (ArtifactStoreException failure) {
      throw failure;
    } catch (IOException failure) {
      throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID");
    }
  }

  ReopenedModulePublication reopenModule(ModulePublicationReference reference) {
    if (reference == null) {
      throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID");
    }
    try {
      return reopenFromDirectory(existingModuleDirectory(reference.address()), reference);
    } catch (ArtifactStoreException failure) {
      throw failure;
    } catch (IOException failure) {
      throw invalidPublication();
    }
  }

  private InstalledModulePublication reopenEquivalentOrRejectCollision(
      ValidatedModuleInstall validated) {
    try {
      ReopenedModulePublication reopened = reopenModule(validated.reference());
      if (!reopened.payloads().stream()
              .map(VerifiedCanonicalPayload::descriptor)
              .toList()
              .equals(validated.descriptors())
          || !reopened.payloads().stream()
              .map(VerifiedCanonicalPayload::canonicalUtf8)
              .toList()
              .equals(
                  validated.request().payloads().stream()
                      .map(CanonicalModulePayload::canonicalUtf8)
                      .toList())) {
        throw collision();
      }
      return new InstalledModulePublication(
          validated.reference(),
          ModuleInstallDisposition.ALREADY_INSTALLED,
          validated.descriptors());
    } catch (ArtifactStoreException failure) {
      throw collision();
    }
  }

  private ValidatedModuleInstall validateInstallRequest(ModuleInstallRequest request) {
    try {
      if (request == null
          || request.address() == null
          || request.moduleVersion() == null
          || request.moduleVersion().isBlank()
          || request.upstreamArtifacts() == null
          || request.controls() == null
          || request.status() == null
          || request.gapRefs() == null
          || request.payloads() == null
          || request.payloads().isEmpty()
          || request.payloads().size() > limits.maxPayloadFiles()) {
        throw invalidInstall();
      }
      requireNonNegativeLimits();
      if (!artifactPolicies.reference().equals(request.controls().artifactPolicyRegistryRef())) {
        throw policyMismatch();
      }
      requireStrictReferences(request.upstreamArtifacts());
      requireStrictStrings(request.gapRefs());
      requireStrictPayloadFileOrder(request.payloads());
      List<ArtifactDescriptor> descriptors = new ArrayList<>();
      long totalBytes = 0L;
      for (CanonicalModulePayload payload : request.payloads()) {
        ArtifactDescriptor descriptor = validatePayload(request, payload);
        totalBytes = Math.addExact(totalBytes, descriptor.sizeBytes());
        if (descriptor.sizeBytes() > limits.maxArtifactBytes()
            || totalBytes > limits.maxPublicationBytes()) {
          throw invalidInstall();
        }
        descriptors.add(descriptor);
      }
      descriptors.sort(Comparator.comparing(ArtifactDescriptor::fileName, UTF8_ORDER));
      requireUniqueFileNamesAndArtifactIds(descriptors);
      requireExpectedPayloadSet(request.address(), descriptors, true);
      if (descriptors.size() + 1 > limits.maxDirectoryEntries()) {
        throw invalidInstall();
      }
      ModuleArtifactRoot root = moduleArtifactRoot(descriptors);
      ModuleReceipt receipt = receiptFor(request, descriptors, root);
      ImmutableBytes receiptBytes = canonicalJson.encodeCanonical(receiptNode(receipt, false));
      ModuleReceiptId receiptId =
          new ModuleReceiptId(
              "module-receipt:"
                  + sha256Hex(
                      concatenate(
                          frame(MODULE_RECEIPT_ID_DOMAIN), frame(receiptBytes.copyToByteArray()))));
      ModuleReceipt completedReceipt =
          new ModuleReceipt(
              receipt.schemaVersion(),
              receiptId,
              receipt.address(),
              receipt.moduleVersion(),
              receipt.upstreamArtifacts(),
              receipt.controls(),
              receipt.status(),
              receipt.payloadArtifacts(),
              receipt.moduleArtifactRoot(),
              receipt.gapRefs());
      ImmutableBytes completedReceiptBytes =
          canonicalJson.encodeCanonical(receiptNode(completedReceipt, true));
      ModulePublicationReference reference =
          new ModulePublicationReference(
              request.address(),
              root,
              receiptId,
              new Sha256Digest(sha256Hex(completedReceiptBytes.copyToByteArray())));
      return new ValidatedModuleInstall(
          request, descriptors, completedReceipt, completedReceiptBytes, reference);
    } catch (ArtifactStoreException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw invalidInstall();
    }
  }

  private ArtifactDescriptor validatePayload(
      ModuleInstallRequest request, CanonicalModulePayload payload) {
    if (payload == null
        || payload.fileName() == null
        || payload.artifactType() == null
        || payload.schemaVersion() == null
        || payload.artifactId() == null
        || payload.mediaType() == null
        || payload.canonicalUtf8() == null) {
      throw invalidInstall();
    }
    ModuleArtifactContract contract = moduleArtifactContract(payload);
    if (!contract.fileName().equals(payload.fileName())
        || !contract.addressFor(request.address().runId()).equals(request.address())) {
      throw invalidInstall();
    }
    if (!artifactPolicies.reference().equals(request.controls().artifactPolicyRegistryRef())) {
      throw policyMismatch();
    }
    CanonicalArtifactPolicy policy =
        artifactPolicies.resolve(
            new ArtifactPolicyKey(payload.artifactType(), payload.schemaVersion()));
    if (policy.envelopeKind() != contract.envelopeKind()
        || policy.mediaType() != payload.mediaType()
        || payload.canonicalUtf8().size() > limits.maxArtifactBytes()) {
      throw policyMismatch();
    }
    switch (policy.envelopeKind()) {
      case MODULE_ARTIFACT_JSON ->
          validateModuleArtifactEnvelope(request, payload, parseCanonicalPayload(payload), policy);
      case STANDALONE_JSON ->
          validateStandaloneJsonArtifact(payload, parseCanonicalPayload(payload), policy);
      case CANONICAL_JSONL -> validateCanonicalJsonlArtifact(payload, policy);
      case RAW_UTF8 -> throw invalidInstall();
    }
    return new ArtifactDescriptor(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8().size(),
        new Sha256Digest(sha256Hex(payload.canonicalUtf8().copyToByteArray())));
  }

  private void validateModuleArtifactEnvelope(
      ModuleInstallRequest request,
      CanonicalModulePayload payload,
      JsonNode parsed,
      CanonicalArtifactPolicy policy) {
    if (!(parsed instanceof ObjectNode envelope)
        || !fieldNames(envelope)
            .equals(
                Set.of(
                    "schemaVersion",
                    "artifactType",
                    "artifactId",
                    "producer",
                    "upstreamArtifacts",
                    "controls",
                    "completion",
                    "payload"))
        || !payload.schemaVersion().equals(requiredText(envelope, "schemaVersion"))
        || !payload.artifactType().equals(requiredText(envelope, "artifactType"))
        || !payload.artifactId().value().equals(requiredText(envelope, "artifactId"))
        || envelope.get("payload") == null) {
      throw invalidInstall();
    }
    requireSameCanonicalJson(
        producerNode(request.address(), request.moduleVersion()), envelope.get("producer"));
    requireSameCanonicalJson(
        upstreamNode(request.upstreamArtifacts()), envelope.get("upstreamArtifacts"));
    requireSameCanonicalJson(controlsNode(request.controls()), envelope.get("controls"));
    requireSameCanonicalJson(
        completionNode(request.status(), request.gapRefs()), envelope.get("completion"));
    ObjectNode withoutArtifactId = envelope.deepCopy();
    withoutArtifactId.remove("artifactId");
    String expectedId =
        policy.artifactIdPrefix()
            + ":"
            + sha256Hex(
                concatenate(
                    frame(MODULE_ARTIFACT_ID_DOMAIN),
                    frame(payload.schemaVersion()),
                    frame(payload.artifactType()),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    if (!expectedId.equals(payload.artifactId().value())) {
      throw invalidInstall();
    }
  }

  private JsonNode parseCanonicalPayload(CanonicalModulePayload payload) {
    try {
      return canonicalJson.parseCanonical(payload.canonicalUtf8());
    } catch (RuntimeException failure) {
      throw payloadNotCanonical();
    }
  }

  private void validateStandaloneJsonArtifact(
      CanonicalModulePayload payload, JsonNode parsed, CanonicalArtifactPolicy policy) {
    if (!(parsed instanceof ObjectNode document)
        || !payload.schemaVersion().equals(requiredTextForInstall(document, "schemaVersion"))
        || !payload.artifactType().equals(requiredTextForInstall(document, "artifactType"))
        || !payload.artifactId().value().equals(requiredTextForInstall(document, "artifactId"))) {
      throw invalidInstall();
    }
    ObjectNode withoutArtifactId = document.deepCopy();
    withoutArtifactId.remove("artifactId");
    String expectedId =
        policy.artifactIdPrefix()
            + ":"
            + sha256Hex(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(payload.schemaVersion()),
                    frame(payload.artifactType()),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    if (!expectedId.equals(payload.artifactId().value())) {
      throw invalidInstall();
    }
  }

  private void validateCanonicalJsonlArtifact(
      CanonicalModulePayload payload, CanonicalArtifactPolicy policy) {
    byte[] bytes = payload.canonicalUtf8().copyToByteArray();
    if (bytes.length == 0) {
      if (!policy.emptyJsonlAllowed()) {
        throw invalidInstall();
      }
    } else {
      requireCanonicalJsonlLines(payload, bytes);
    }
    String expectedId =
        policy.artifactIdPrefix()
            + ":"
            + sha256Hex(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(payload.schemaVersion()),
                    frame(payload.artifactType()),
                    frame(bytes)));
    if (!expectedId.equals(payload.artifactId().value())) {
      throw invalidInstall();
    }
  }

  private ModuleReceipt receiptFor(
      ModuleInstallRequest request, List<ArtifactDescriptor> descriptors, ModuleArtifactRoot root) {
    return new ModuleReceipt(
        RECEIPT_SCHEMA,
        null,
        request.address(),
        request.moduleVersion(),
        List.copyOf(request.upstreamArtifacts()),
        request.controls(),
        request.status(),
        List.copyOf(descriptors),
        root,
        List.copyOf(request.gapRefs()));
  }

  private void writePublication(Path staging, ValidatedModuleInstall validated) throws IOException {
    for (CanonicalModulePayload payload : validated.request().payloads()) {
      writeAndForce(staging.resolve(payload.fileName()), payload.canonicalUtf8().copyToByteArray());
    }
    writeAndForce(staging.resolve(RECEIPT_FILE_NAME), validated.receiptBytes().copyToByteArray());
    forceDirectory(staging);
  }

  private void verifyStagedPublication(Path staging, ModulePublicationReference reference) {
    try {
      ReopenedModulePublication reopened = reopenFromDirectory(staging, reference);
      if (!reopened.reference().equals(reference)) {
        throw invalidPublication();
      }
    } catch (RuntimeException failure) {
      throw invalidPublication();
    }
  }

  private ReopenedModulePublication reopenFromDirectory(
      Path directory, ModulePublicationReference reference) {
    try {
      ReceiptBytes receiptBytes = readReceipt(directory);
      ModuleReceipt receipt = receiptBytes.receipt();
      if (!artifactPolicies.reference().equals(receipt.controls().artifactPolicyRegistryRef())) {
        throw policyMismatch();
      }
      validateReceiptIdentity(receiptBytes, reference);
      List<VerifiedCanonicalPayload> payloads = readVerifiedPayloads(directory, receipt);
      List<ArtifactDescriptor> descriptors =
          payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList();
      requireExpectedPayloadSet(receipt.address(), descriptors, false);
      if (!moduleArtifactRoot(descriptors).equals(receipt.moduleArtifactRoot())) {
        throw invalidPublication();
      }
      requireExactDirectoryContents(directory, descriptors);
      return new ReopenedModulePublication(reference, receipt, payloads);
    } catch (ArtifactStoreException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw invalidPublication();
    }
  }

  private ReceiptBytes readReceipt(Path directory) throws IOException {
    Path receiptPath = directory.resolve(RECEIPT_FILE_NAME);
    if (!Files.isRegularFile(receiptPath, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(receiptPath)) {
      throw invalidPublication();
    }
    ImmutableBytes bytes = readBoundedFile(receiptPath, limits.maxPublicationBytes());
    ObjectNode node = requireObject(canonicalJson.parseCanonical(bytes));
    ModuleReceipt parsed = receiptFromNode(node);
    ImmutableBytes withoutId = canonicalJson.encodeCanonical(receiptNode(parsed, false));
    String expectedId =
        "module-receipt:"
            + sha256Hex(
                concatenate(frame(MODULE_RECEIPT_ID_DOMAIN), frame(withoutId.copyToByteArray())));
    if (!expectedId.equals(parsed.moduleReceiptId().value())) {
      throw invalidPublication();
    }
    if (parsed.payloadArtifacts().isEmpty()) {
      throw invalidPublication();
    }
    return new ReceiptBytes(parsed, bytes);
  }

  private void validateReceiptIdentity(
      ReceiptBytes receiptBytes, ModulePublicationReference reference) {
    ModuleReceipt receipt = receiptBytes.receipt();
    if (!receipt.address().equals(reference.address())
        || !receipt.moduleArtifactRoot().equals(reference.moduleArtifactRoot())
        || !receipt.moduleReceiptId().equals(reference.moduleReceiptId())) {
      throw invalidPublication();
    }
    if (!new Sha256Digest(sha256Hex(receiptBytes.canonicalUtf8().copyToByteArray()))
        .equals(reference.moduleReceiptSha256())) {
      throw invalidPublication();
    }
  }

  private List<VerifiedCanonicalPayload> readVerifiedPayloads(Path directory, ModuleReceipt receipt)
      throws IOException {
    List<VerifiedCanonicalPayload> result = new ArrayList<>();
    for (ArtifactDescriptor descriptor : receipt.payloadArtifacts()) {
      Path payloadPath = directory.resolve(descriptor.fileName());
      if (!Files.isRegularFile(payloadPath, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(payloadPath)) {
        throw invalidPublication();
      }
      ImmutableBytes bytes = readPayloadFile(payloadPath, descriptor);
      ArtifactDescriptor actual = validatePersistedDescriptor(receipt, descriptor, bytes);
      result.add(new VerifiedCanonicalPayload(actual, bytes));
    }
    return List.copyOf(result);
  }

  private ArtifactDescriptor validatePersistedDescriptor(
      ModuleReceipt receipt, ArtifactDescriptor expected, ImmutableBytes bytes) {
    ModuleInstallRequest request =
        new ModuleInstallRequest(
            receipt.address(),
            receipt.moduleVersion(),
            receipt.upstreamArtifacts(),
            receipt.controls(),
            receipt.status(),
            receipt.gapRefs(),
            List.of(
                new CanonicalModulePayload(
                    expected.fileName(),
                    expected.artifactType(),
                    expected.schemaVersion(),
                    expected.artifactId(),
                    expected.mediaType(),
                    bytes)));
    ArtifactDescriptor actual;
    try {
      actual = validatePayload(request, request.payloads().get(0));
    } catch (ArtifactStoreException failure) {
      throw invalidPublication();
    }
    if (!actual.equals(expected)) {
      throw invalidPublication();
    }
    return actual;
  }

  private void requireCanonicalJsonlLines(CanonicalModulePayload payload, byte[] bytes) {
    if (bytes[bytes.length - 1] != '\n') {
      throw payloadNotCanonical();
    }
    int lineStart = 0;
    for (int index = 0; index < bytes.length; index++) {
      if (bytes[index] != '\n') {
        continue;
      }
      if (index == lineStart) {
        throw payloadNotCanonical();
      }
      ImmutableBytes lineBytes = ImmutableBytes.copyOf(Arrays.copyOfRange(bytes, lineStart, index));
      JsonNode line =
          parseCanonicalPayload(
              new CanonicalModulePayload(
                  payload.fileName(),
                  payload.artifactType(),
                  payload.schemaVersion(),
                  payload.artifactId(),
                  payload.mediaType(),
                  lineBytes));
      if (!(line instanceof ObjectNode)) {
        throw payloadNotCanonical();
      }
      lineStart = index + 1;
    }
  }

  private ModuleReceipt receiptFromNode(ObjectNode node) {
    if (!fieldNames(node)
            .equals(
                Set.of(
                    "schemaVersion",
                    "moduleReceiptId",
                    "address",
                    "moduleVersion",
                    "upstreamArtifacts",
                    "controls",
                    "status",
                    "payloadArtifacts",
                    "moduleArtifactRoot",
                    "gapRefs"))
        || !RECEIPT_SCHEMA.equals(requiredText(node, "schemaVersion"))) {
      throw invalidPublication();
    }
    return new ModuleReceipt(
        RECEIPT_SCHEMA,
        ModuleReceiptId.parse(requiredText(node, "moduleReceiptId")),
        addressFromNode(requireObject(node.get("address"))),
        requiredText(node, "moduleVersion"),
        referencesFromNode(requireArray(node.get("upstreamArtifacts"))),
        controlsFromNode(requireObject(node.get("controls"))),
        ModuleCompletionStatus.valueOf(requiredText(node, "status")),
        descriptorsFromNode(requireArray(node.get("payloadArtifacts"))),
        ModuleArtifactRoot.parse(requiredText(node, "moduleArtifactRoot")),
        stringsFromNode(requireArray(node.get("gapRefs"))));
  }

  private ObjectNode receiptNode(ModuleReceipt receipt, boolean includeReceiptId) {
    ObjectNode node = JSON.createObjectNode();
    node.put("schemaVersion", receipt.schemaVersion());
    if (includeReceiptId) {
      node.put("moduleReceiptId", receipt.moduleReceiptId().value());
    }
    node.set("address", addressNode(receipt.address()));
    node.put("moduleVersion", receipt.moduleVersion());
    node.set("upstreamArtifacts", upstreamNode(receipt.upstreamArtifacts()));
    node.set("controls", controlsNode(receipt.controls()));
    node.put("status", receipt.status().name());
    node.set("payloadArtifacts", descriptorArray(receipt.payloadArtifacts()));
    node.put("moduleArtifactRoot", receipt.moduleArtifactRoot().value());
    node.set("gapRefs", stringArray(receipt.gapRefs()));
    return node;
  }

  private ObjectNode producerNode(ModulePublicationAddress address, String moduleVersion) {
    ObjectNode producer = JSON.createObjectNode();
    producer.set("address", addressNode(address));
    producer.put("moduleVersion", moduleVersion);
    return producer;
  }

  private ObjectNode addressNode(ModulePublicationAddress address) {
    ObjectNode node = JSON.createObjectNode();
    if (address instanceof AnalysisStepModuleAddress analysisStepAddress) {
      node.put("kind", "ANALYSIS_STEP");
      node.put("runId", analysisStepAddress.runId().value());
      node.put("analysisStepKey", analysisStepAddress.analysisStepKey().wireValue());
      node.put("moduleNumber", analysisStepAddress.moduleNumber());
      node.put("moduleKey", analysisStepAddress.moduleKey());
      return node;
    }
    throw invalidPublication();
  }

  private ModulePublicationAddress addressFromNode(ObjectNode node) {
    if (!fieldNames(node)
            .equals(Set.of("kind", "runId", "analysisStepKey", "moduleNumber", "moduleKey"))
        || !"ANALYSIS_STEP".equals(requiredText(node, "kind"))) {
      throw invalidPublication();
    }
    JsonNode number = node.get("moduleNumber");
    if (number == null || !number.canConvertToInt()) {
      throw invalidPublication();
    }
    return new AnalysisStepModuleAddress(
        AnalysisRunId.parse(requiredText(node, "runId")),
        AnalysisStepKey.parse(requiredText(node, "analysisStepKey")),
        number.intValue(),
        requiredText(node, "moduleKey"));
  }

  private ArrayNode upstreamNode(List<ArtifactReference> references) {
    ArrayNode node = JSON.createArrayNode();
    for (ArtifactReference reference : references) {
      node.addObject()
          .put("artifactId", reference.artifactId().value())
          .put("sha256", reference.sha256().value());
    }
    return node;
  }

  private List<ArtifactReference> referencesFromNode(ArrayNode node) {
    List<ArtifactReference> references = new ArrayList<>();
    for (JsonNode item : node) {
      ObjectNode object = requireObject(item);
      if (!fieldNames(object).equals(Set.of("artifactId", "sha256"))) {
        throw invalidPublication();
      }
      references.add(
          new ArtifactReference(
              ArtifactId.parse(requiredText(object, "artifactId")),
              Sha256Digest.parse(requiredText(object, "sha256"))));
    }
    requireStrictReferences(references);
    return List.copyOf(references);
  }

  private ObjectNode controlsNode(ArtifactControls controls) {
    ObjectNode node = JSON.createObjectNode();
    node.put("toolchainSha256", controls.toolchainSha256().value());
    node.put("profileSha256", controls.profileSha256().value());
    node.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) {
      node.putNull("promptBundleSha256");
    } else {
      node.put("promptBundleSha256", controls.promptBundleSha256().value());
    }
    node.putObject("artifactPolicyRegistryRef")
        .put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
    return node;
  }

  private ArtifactControls controlsFromNode(ObjectNode node) {
    if (!fieldNames(node)
        .equals(
            Set.of(
                "toolchainSha256",
                "profileSha256",
                "schemaBundleSha256",
                "promptBundleSha256",
                "artifactPolicyRegistryRef"))) {
      throw invalidPublication();
    }
    JsonNode prompt = node.get("promptBundleSha256");
    if (prompt == null || !(prompt.isNull() || prompt.isTextual())) {
      throw invalidPublication();
    }
    ObjectNode registry = requireObject(node.get("artifactPolicyRegistryRef"));
    if (!fieldNames(registry).equals(Set.of("artifactId", "sha256"))) {
      throw invalidPublication();
    }
    return new ArtifactControls(
        Sha256Digest.parse(requiredText(node, "toolchainSha256")),
        Sha256Digest.parse(requiredText(node, "profileSha256")),
        Sha256Digest.parse(requiredText(node, "schemaBundleSha256")),
        prompt.isNull() ? null : Sha256Digest.parse(prompt.textValue()),
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse(requiredText(registry, "artifactId")),
            Sha256Digest.parse(requiredText(registry, "sha256"))));
  }

  private ObjectNode completionNode(ModuleCompletionStatus status, List<String> gapRefs) {
    ObjectNode node = JSON.createObjectNode();
    node.put("status", status.name());
    node.set("gapRefs", stringArray(gapRefs));
    node.putNull("failureRef");
    return node;
  }

  private ArrayNode descriptorArray(List<ArtifactDescriptor> descriptors) {
    ArrayNode array = JSON.createArrayNode();
    for (ArtifactDescriptor descriptor : descriptors) {
      array
          .addObject()
          .put("fileName", descriptor.fileName())
          .put("artifactType", descriptor.artifactType())
          .put("schemaVersion", descriptor.schemaVersion())
          .put("artifactId", descriptor.artifactId().value())
          .put("mediaType", descriptor.mediaType().wireValue())
          .put("sizeBytes", descriptor.sizeBytes())
          .put("sha256", descriptor.sha256().value());
    }
    return array;
  }

  private List<ArtifactDescriptor> descriptorsFromNode(ArrayNode array) {
    if (array.isEmpty()
        || array.size() > limits.maxPayloadFiles()
        || array.size() + 1 > limits.maxDirectoryEntries()) {
      throw invalidPublication();
    }
    List<ArtifactDescriptor> descriptors = new ArrayList<>();
    for (JsonNode item : array) {
      ObjectNode node = requireObject(item);
      if (!fieldNames(node)
          .equals(
              Set.of(
                  "fileName",
                  "artifactType",
                  "schemaVersion",
                  "artifactId",
                  "mediaType",
                  "sizeBytes",
                  "sha256"))) {
        throw invalidPublication();
      }
      JsonNode size = node.get("sizeBytes");
      if (size == null
          || !size.isIntegralNumber()
          || !size.canConvertToLong()
          || size.longValue() < 0) {
        throw invalidPublication();
      }
      descriptors.add(
          new ArtifactDescriptor(
              requiredText(node, "fileName"),
              requiredText(node, "artifactType"),
              requiredText(node, "schemaVersion"),
              ArtifactId.parse(requiredText(node, "artifactId")),
              CanonicalMediaType.parse(requiredText(node, "mediaType")),
              size.longValue(),
              Sha256Digest.parse(requiredText(node, "sha256"))));
    }
    requireStrictDescriptorOrder(descriptors);
    requireUniqueFileNamesAndArtifactIds(descriptors);
    return List.copyOf(descriptors);
  }

  private ArrayNode stringArray(List<String> strings) {
    ArrayNode array = JSON.createArrayNode();
    for (String value : strings) {
      array.add(value);
    }
    return array;
  }

  private List<String> stringsFromNode(ArrayNode array) {
    List<String> values = new ArrayList<>();
    for (JsonNode value : array) {
      if (!value.isTextual()) {
        throw invalidPublication();
      }
      values.add(value.textValue());
    }
    requireStrictStrings(values);
    return List.copyOf(values);
  }

  private ModuleArtifactRoot moduleArtifactRoot(List<ArtifactDescriptor> descriptors) {
    byte[] bytes = frame(MODULE_ARTIFACT_ROOT_DOMAIN);
    bytes = concatenate(bytes, u32(descriptors.size()));
    for (ArtifactDescriptor descriptor : descriptors) {
      bytes = concatenate(bytes, frame(descriptorBytes(descriptor)));
    }
    return new ModuleArtifactRoot("module-root:" + sha256Hex(bytes));
  }

  private byte[] descriptorBytes(ArtifactDescriptor descriptor) {
    return concatenate(
        frame(descriptor.fileName()),
        frame(descriptor.artifactType()),
        frame(descriptor.schemaVersion()),
        frame(descriptor.artifactId().value()),
        frame(descriptor.mediaType().wireValue()),
        u64(descriptor.sizeBytes()),
        HexFormat.of().parseHex(descriptor.sha256().value()));
  }

  private Path createModuleParent(ModulePublicationAddress address) throws IOException {
    Path current = runStore.rootForStore();
    requireDirectoryWithoutLinks(current);
    for (String segment : moduleParentSegments(address)) {
      current = current.resolve(segment);
      createOrVerifyDirectoryWithoutLinks(current);
    }
    return current;
  }

  private Path existingModuleDirectory(ModulePublicationAddress address) throws IOException {
    Path current = runStore.rootForStore();
    requireDirectoryWithoutLinks(current);
    for (String segment : moduleParentSegments(address)) {
      current = current.resolve(segment);
      requireDirectoryWithoutLinks(current);
    }
    Path moduleDirectory = current.resolve(moduleDirectoryName(address));
    requireDirectoryWithoutLinks(moduleDirectory);
    return moduleDirectory;
  }

  private List<String> moduleParentSegments(ModulePublicationAddress address) {
    if (!(address instanceof AnalysisStepModuleAddress analysisStepAddress)) {
      throw invalidPublication();
    }
    return List.of(
        "runs",
        encodedSegment(analysisStepAddress.runId().value()),
        "steps",
        analysisStepAddress.analysisStepKey().directoryName(),
        "modules");
  }

  private String moduleDirectoryName(ModulePublicationAddress address) {
    if (!(address instanceof AnalysisStepModuleAddress analysisStepAddress)) {
      throw invalidPublication();
    }
    return String.format(
        Locale.ROOT,
        "%02d-%s",
        analysisStepAddress.moduleNumber(),
        analysisStepAddress.moduleKey());
  }

  private void createOrVerifyDirectoryWithoutLinks(Path directory) throws IOException {
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
      try {
        Files.createDirectory(directory);
      } catch (java.nio.file.FileAlreadyExistsException ignored) {
        // Reinspect the directory below so a raced symlink or regular file is never followed.
      }
    }
    requireDirectoryWithoutLinks(directory);
  }

  private void requireDirectoryWithoutLinks(Path directory) {
    if (Files.isSymbolicLink(directory)
        || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw invalidPublication();
    }
  }

  private void requireExactDirectoryContents(Path directory, List<ArtifactDescriptor> descriptors)
      throws IOException {
    Set<String> expected = new HashSet<>();
    expected.add(RECEIPT_FILE_NAME);
    for (ArtifactDescriptor descriptor : descriptors) {
      expected.add(descriptor.fileName());
    }
    try (Stream<Path> files = Files.list(directory)) {
      Set<String> actual = new HashSet<>();
      List<Path> entries = files.toList();
      if (entries.size() > limits.maxDirectoryEntries()) {
        throw invalidPublication();
      }
      for (Path file : entries) {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
          throw invalidPublication();
        }
        Path fileName = file.getFileName();
        if (fileName == null) {
          throw invalidPublication();
        }
        actual.add(fileName.toString());
      }
      if (!actual.equals(expected)) {
        throw invalidPublication();
      }
    }
  }

  private void moveAtomically(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException failure) {
      throw new ArtifactStoreException("ATOMIC_MOVE_UNSUPPORTED");
    }
  }

  private void writeAndForce(Path path, byte[] bytes) throws IOException {
    try (FileChannel channel =
        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
    }
  }

  private void forceDirectory(Path directory) throws IOException {
    try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    }
  }

  private void deleteIfStillPresent(Path path) {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    try (Stream<Path> files = Files.walk(path)) {
      files.sorted(Comparator.reverseOrder()).forEach(this::deleteIgnoringFailure);
    } catch (IOException ignored) {
      // A staging residue is never addressed as a publication and is safe to leave for diagnostics.
    }
  }

  private void deleteIgnoringFailure(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // A staging residue is not a successful publication.
    }
  }

  private void requireNonNegativeLimits() {
    if (limits.maxPayloadFiles() < 1
        || limits.maxArtifactBytes() < 0
        || limits.maxPublicationBytes() < 0
        || limits.maxDirectoryEntries() < 2) {
      throw invalidInstall();
    }
  }

  private static void requireStrictReferences(List<ArtifactReference> references) {
    ArtifactReference previous = null;
    for (ArtifactReference reference : references) {
      if (reference == null || (previous != null && compareReference(previous, reference) >= 0)) {
        throw invalidInstall();
      }
      previous = reference;
    }
  }

  private static void requireStrictStrings(List<String> values) {
    String previous = null;
    for (String value : values) {
      if (value == null
          || value.isBlank()
          || (previous != null && UTF8_ORDER.compare(previous, value) >= 0)) {
        throw invalidInstall();
      }
      previous = value;
    }
  }

  private static void requireStrictPayloadFileOrder(List<CanonicalModulePayload> payloads) {
    String previous = null;
    for (CanonicalModulePayload payload : payloads) {
      if (payload == null
          || payload.fileName() == null
          || (previous != null && UTF8_ORDER.compare(previous, payload.fileName()) >= 0)) {
        throw invalidInstall();
      }
      previous = payload.fileName();
    }
  }

  private static void requireUniqueFileNamesAndArtifactIds(List<ArtifactDescriptor> descriptors) {
    Set<String> fileNames = new HashSet<>();
    Set<ArtifactId> artifactIds = new HashSet<>();
    for (ArtifactDescriptor descriptor : descriptors) {
      if (descriptor == null
          || !fileNames.add(descriptor.fileName())
          || !artifactIds.add(descriptor.artifactId())) {
        throw invalidInstall();
      }
    }
  }

  private static void requireExpectedPayloadSet(
      ModulePublicationAddress address,
      List<ArtifactDescriptor> descriptors,
      boolean installRequest) {
    if (!(address instanceof AnalysisStepModuleAddress analysisStepAddress)) {
      throw installRequest ? invalidInstall() : invalidPublication();
    }
    List<String> expectedFileNames =
        switch (analysisStepAddress.analysisStepKey()) {
          case VERIFIED_SOURCE_INVENTORY ->
              switch (analysisStepAddress.moduleNumber()) {
                case 1 ->
                    "request-admission".equals(analysisStepAddress.moduleKey())
                        ? List.of("admitted-source-request.json")
                        : null;
                case 2 ->
                    "source-index".equals(analysisStepAddress.moduleKey())
                        ? List.of("verified-source-index.json")
                        : null;
                case 3 ->
                    "publish".equals(analysisStepAddress.moduleKey())
                        ? List.of(
                            "source-input.json", "source-inventory.jsonl", "verified-snapshot.json")
                        : null;
                default -> null;
              };
          case APPLICATION_DISCOVERY ->
              switch (analysisStepAddress.moduleNumber()) {
                case 1 ->
                    "application-profile".equals(analysisStepAddress.moduleKey())
                        ? List.of("application-profile-draft.json")
                        : null;
                case 2 ->
                    "http-entry".equals(analysisStepAddress.moduleKey())
                        ? List.of("http-entry-discovery.json")
                        : null;
                case 3 ->
                    "mapper-catalog".equals(analysisStepAddress.moduleKey())
                        ? List.of("mapper-catalog-draft.json")
                        : null;
                case 4 ->
                    "publish".equals(analysisStepAddress.moduleKey())
                        ? List.of(
                            "application-profile.json",
                            "capability-report.json",
                            "entry-points.jsonl",
                            "mapper-catalog.jsonl")
                        : null;
                default -> null;
              };
          case PROGRAM_GRAPHS ->
              switch (analysisStepAddress.moduleNumber()) {
                case 1 ->
                    "code-structure".equals(analysisStepAddress.moduleKey())
                        ? List.of("code-structure-draft.json")
                        : null;
                case 2 ->
                    "call-graph".equals(analysisStepAddress.moduleKey())
                        ? List.of("call-graph-draft.json")
                        : null;
                case 3 ->
                    "control-flow".equals(analysisStepAddress.moduleKey())
                        ? List.of("control-flow-draft.json")
                        : null;
                case 4 ->
                    "data-flow".equals(analysisStepAddress.moduleKey())
                        ? List.of("data-flow-draft.json")
                        : null;
                case 5 ->
                    "evidence-graph".equals(analysisStepAddress.moduleKey())
                        ? List.of("evidence-graph-draft.json")
                        : null;
                case 6 ->
                    "publish".equals(analysisStepAddress.moduleKey())
                        ? List.of(
                            "call-graph.json",
                            "code-structure-graph.json",
                            "control-flow-graph.json",
                            "data-flow-graph.json",
                            "evidence-graph.json",
                            "graph-gaps.jsonl",
                            "graph-index.json")
                        : null;
                default -> null;
              };
          case PROVEN_CODE_FACTS ->
              switch (analysisStepAddress.moduleNumber()) {
                case 1 ->
                    "candidates".equals(analysisStepAddress.moduleKey())
                        ? List.of("fact-candidate-set.json")
                        : null;
                case 2 ->
                    "proofs".equals(analysisStepAddress.moduleKey())
                        ? List.of("proof-decision-set.json")
                        : null;
                case 3 ->
                    "publish".equals(analysisStepAddress.moduleKey())
                        ? List.of(
                            "fact-accounting.json",
                            "gap-ledger.json",
                            "proof-pack.json",
                            "proven-facts.json")
                        : null;
                default -> null;
              };
          default -> null;
        };
    if (expectedFileNames == null
        || !descriptors.stream()
            .map(ArtifactDescriptor::fileName)
            .toList()
            .equals(expectedFileNames)) {
      throw installRequest ? invalidInstall() : invalidPublication();
    }
  }

  private static void requireStrictDescriptorOrder(List<ArtifactDescriptor> descriptors) {
    ArtifactDescriptor previous = null;
    for (ArtifactDescriptor descriptor : descriptors) {
      if (descriptor == null
          || (previous != null
              && UTF8_ORDER.compare(previous.fileName(), descriptor.fileName()) >= 0)) {
        throw invalidPublication();
      }
      previous = descriptor;
    }
  }

  private ImmutableBytes readBoundedFile(Path path, long maximumBytes) throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
      throw invalidPublication();
    }
    long size = Files.size(path);
    if (size < 0 || size > maximumBytes) {
      throw invalidPublication();
    }
    ImmutableBytes bytes = ImmutableBytes.copyOf(Files.readAllBytes(path));
    if (bytes.size() != size) {
      throw invalidPublication();
    }
    return bytes;
  }

  private ImmutableBytes readPayloadFile(Path path, ArtifactDescriptor descriptor)
      throws IOException {
    if (descriptor.sizeBytes() > limits.maxArtifactBytes()) {
      throw invalidPublication();
    }
    ImmutableBytes bytes = readBoundedFile(path, descriptor.sizeBytes());
    if (bytes.size() != descriptor.sizeBytes()) {
      throw invalidPublication();
    }
    return bytes;
  }

  private void requireSameCanonicalJson(JsonNode expected, JsonNode actual) {
    if (actual == null
        || !Arrays.equals(
            canonicalJson.encodeCanonical(expected).copyToByteArray(),
            canonicalJson.encodeCanonical(actual).copyToByteArray())) {
      throw invalidInstall();
    }
  }

  private static ObjectNode requireObject(JsonNode value) {
    if (value instanceof ObjectNode object) {
      return object;
    }
    throw invalidPublication();
  }

  private static ArrayNode requireArray(JsonNode value) {
    if (value instanceof ArrayNode array) {
      return array;
    }
    throw invalidPublication();
  }

  private static String requiredText(ObjectNode object, String name) {
    JsonNode value = object.get(name);
    if (value == null || !value.isTextual()) {
      throw invalidPublication();
    }
    return value.textValue();
  }

  private static String requiredTextForInstall(ObjectNode object, String name) {
    JsonNode value = object.get(name);
    if (value == null || !value.isTextual()) {
      throw invalidInstall();
    }
    return value.textValue();
  }

  private static Set<String> fieldNames(ObjectNode object) {
    Set<String> names = new HashSet<>();
    object.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static int compareReference(ArtifactReference first, ArtifactReference second) {
    int byId = UTF8_ORDER.compare(first.artifactId().value(), second.artifactId().value());
    return byId != 0 ? byId : UTF8_ORDER.compare(first.sha256().value(), second.sha256().value());
  }

  private static int compareUtf8(String first, String second) {
    byte[] firstBytes = first.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] secondBytes = second.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    int common = Math.min(firstBytes.length, secondBytes.length);
    for (int index = 0; index < common; index++) {
      int comparison =
          Integer.compare(
              Byte.toUnsignedInt(firstBytes[index]), Byte.toUnsignedInt(secondBytes[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(firstBytes.length, secondBytes.length);
  }

  private static String encodedSegment(String contentId) {
    return contentId.replace(":", "--");
  }

  private static byte[] frame(String text) {
    return frame(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] bytes) {
    return concatenate(u64(bytes.length), bytes);
  }

  private static byte[] u32(int value) {
    return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
  }

  private static byte[] u64(long value) {
    return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array();
  }

  private static byte[] concatenate(byte[]... segments) {
    int length = 0;
    for (byte[] segment : segments) {
      length = Math.addExact(length, segment.length);
    }
    byte[] joined = new byte[length];
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
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static ArtifactStoreException invalidInstall() {
    return new ArtifactStoreException("MODULE_INSTALL_REQUEST_INVALID");
  }

  private static ArtifactStoreException invalidPublication() {
    return new ArtifactStoreException("MODULE_PUBLICATION_INVALID");
  }

  private static ArtifactStoreException policyMismatch() {
    return new ArtifactStoreException("ARTIFACT_POLICY_MISMATCH");
  }

  private static ArtifactStoreException payloadNotCanonical() {
    return new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL");
  }

  private static ArtifactStoreException collision() {
    return new ArtifactStoreException("MODULE_PUBLICATION_COLLISION");
  }

  private static ModuleArtifactContract moduleArtifactContract(CanonicalModulePayload payload) {
    if ("VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST".equals(payload.artifactType())
        && "verified-source-inventory-admitted-source-request-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
          1,
          "request-admission",
          "admitted-source-request.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX".equals(payload.artifactType())
        && "verified-source-inventory-verified-source-index-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
          2,
          "source-index",
          "verified-source-index.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT".equals(payload.artifactType())
        && "verified-source-inventory-source-input-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
          3,
          "publish",
          "source-input.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY".equals(payload.artifactType())
        && "verified-source-inventory-source-inventory-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
          3,
          "publish",
          "source-inventory.jsonl",
          CanonicalEnvelopeKind.CANONICAL_JSONL);
    }
    if ("VERIFIED_SNAPSHOT".equals(payload.artifactType())
        && "verified-snapshot-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
          3,
          "publish",
          "verified-snapshot.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT".equals(payload.artifactType())
        && "application-discovery-application-profile-draft-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.APPLICATION_DISCOVERY,
          1,
          "application-profile",
          "application-profile-draft.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("APPLICATION_DISCOVERY_HTTP_ENTRY_DISCOVERY".equals(payload.artifactType())
        && "application-discovery-http-entry-discovery-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.APPLICATION_DISCOVERY,
          2,
          "http-entry",
          "http-entry-discovery.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("APPLICATION_DISCOVERY_MAPPER_CATALOG_DRAFT".equals(payload.artifactType())
        && "application-discovery-mapper-catalog-draft-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.APPLICATION_DISCOVERY,
          3,
          "mapper-catalog",
          "mapper-catalog-draft.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("APPLICATION_DISCOVERY_APPLICATION_PROFILE".equals(payload.artifactType())
        && "application-discovery-application-profile-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.APPLICATION_DISCOVERY,
          4,
          "publish",
          "application-profile.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("APPLICATION_DISCOVERY_CAPABILITY_REPORT".equals(payload.artifactType())
        && "application-discovery-capability-report-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.APPLICATION_DISCOVERY,
          4,
          "publish",
          "capability-report.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("APPLICATION_DISCOVERY_ENTRY_POINTS".equals(payload.artifactType())
        && "application-discovery-entry-points-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.APPLICATION_DISCOVERY,
          4,
          "publish",
          "entry-points.jsonl",
          CanonicalEnvelopeKind.CANONICAL_JSONL);
    }
    if ("APPLICATION_DISCOVERY_MAPPER_CATALOG".equals(payload.artifactType())
        && "application-discovery-mapper-catalog-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.APPLICATION_DISCOVERY,
          4,
          "publish",
          "mapper-catalog.jsonl",
          CanonicalEnvelopeKind.CANONICAL_JSONL);
    }
    if ("PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT".equals(payload.artifactType())
        && "program-graphs-code-structure-draft-v3".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          1,
          "code-structure",
          "code-structure-draft.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("PROGRAM_GRAPHS_CALL_GRAPH_DRAFT".equals(payload.artifactType())
        && "program-graphs-call-graph-draft-v3".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          2,
          "call-graph",
          "call-graph-draft.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT".equals(payload.artifactType())
        && "program-graphs-control-flow-draft-v3".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          3,
          "control-flow",
          "control-flow-draft.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("PROGRAM_GRAPHS_DATA_FLOW_DRAFT".equals(payload.artifactType())
        && "program-graphs-data-flow-draft-v3".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          4,
          "data-flow",
          "data-flow-draft.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT".equals(payload.artifactType())
        && "program-graphs-evidence-graph-draft-v3".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          5,
          "evidence-graph",
          "evidence-graph-draft.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH".equals(payload.artifactType())
        && "program-graphs-code-structure-graph-v1".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          6,
          "publish",
          "code-structure-graph.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("PROGRAM_GRAPHS_CALL_GRAPH".equals(payload.artifactType())
        && "program-graphs-call-graph-v1".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          6,
          "publish",
          "call-graph.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH".equals(payload.artifactType())
        && "program-graphs-control-flow-graph-v1".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          6,
          "publish",
          "control-flow-graph.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("PROGRAM_GRAPHS_DATA_FLOW_GRAPH".equals(payload.artifactType())
        && "program-graphs-data-flow-graph-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          6,
          "publish",
          "data-flow-graph.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("PROGRAM_GRAPHS_EVIDENCE_GRAPH".equals(payload.artifactType())
        && "program-graphs-evidence-graph-v3".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          6,
          "publish",
          "evidence-graph.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("PROGRAM_GRAPHS_GRAPH_GAP".equals(payload.artifactType())
        && "program-graphs-graph-gap-v1".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          6,
          "publish",
          "graph-gaps.jsonl",
          CanonicalEnvelopeKind.CANONICAL_JSONL);
    }
    if ("PROGRAM_GRAPHS_GRAPH_INDEX".equals(payload.artifactType())
        && "program-graphs-graph-index-v2".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROGRAM_GRAPHS,
          6,
          "publish",
          "graph-index.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("PROVEN_CODE_FACTS_FACT_CANDIDATE_SET".equals(payload.artifactType())
        && "proven-code-facts-fact-candidate-set-v1".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROVEN_CODE_FACTS,
          1,
          "candidates",
          "fact-candidate-set.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("PROVEN_CODE_FACTS_PROOF_DECISION_SET".equals(payload.artifactType())
        && "proven-code-facts-proof-decision-set-v1".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROVEN_CODE_FACTS,
          2,
          "proofs",
          "proof-decision-set.json",
          CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON);
    }
    if ("PROVEN_CODE_FACTS_FACT_ACCOUNTING".equals(payload.artifactType())
        && "proven-code-facts-fact-accounting-v1".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROVEN_CODE_FACTS,
          3,
          "publish",
          "fact-accounting.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("PROVEN_CODE_FACTS_GAP_LEDGER".equals(payload.artifactType())
        && "proven-code-facts-gap-ledger-v1".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROVEN_CODE_FACTS,
          3,
          "publish",
          "gap-ledger.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("PROVEN_CODE_FACTS_PROOF_PACK".equals(payload.artifactType())
        && "proven-code-facts-proof-pack-v1".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROVEN_CODE_FACTS,
          3,
          "publish",
          "proof-pack.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    if ("PROVEN_CODE_FACTS_PROVEN_FACTS".equals(payload.artifactType())
        && "proven-code-facts-proven-facts-v1".equals(payload.schemaVersion())) {
      return new ModuleArtifactContract(
          AnalysisStepKey.PROVEN_CODE_FACTS,
          3,
          "publish",
          "proven-facts.json",
          CanonicalEnvelopeKind.STANDALONE_JSON);
    }
    throw invalidInstall();
  }

  private record ValidatedModuleInstall(
      ModuleInstallRequest request,
      List<ArtifactDescriptor> descriptors,
      ModuleReceipt receipt,
      ImmutableBytes receiptBytes,
      ModulePublicationReference reference) {}

  private record ReceiptBytes(ModuleReceipt receipt, ImmutableBytes canonicalUtf8) {}

  private record ModuleArtifactContract(
      AnalysisStepKey analysisStepKey,
      int moduleNumber,
      String moduleKey,
      String fileName,
      CanonicalEnvelopeKind envelopeKind) {
    private AnalysisStepModuleAddress addressFor(AnalysisRunId runId) {
      return new AnalysisStepModuleAddress(runId, analysisStepKey, moduleNumber, moduleKey);
    }
  }
}
