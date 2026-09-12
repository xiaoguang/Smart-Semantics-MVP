package org.sourceanalysis.app.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Receipt-last filesystem publisher for the semantic payload set of one analysis step. */
final class AtomicAnalysisStepPublicationEngine {

  private static final String RECEIPT_SCHEMA = "analysis-step-receipt-v1";
  private static final String ROOT_DOMAIN = "canonical-analysis-step-artifact-root-v1";
  private static final String RECEIPT_DOMAIN = "canonical-analysis-step-receipt-id-v1";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Comparator<String> UTF8_ORDER =
      AtomicAnalysisStepPublicationEngine::compareUtf8;

  private final FileSystemRunStoreHandle runStore;
  private final CanonicalJsonCodec canonicalJson;
  private final ArtifactStoreLimits limits;
  private final AtomicCanonicalPublicationEngine moduleEngine;

  AtomicAnalysisStepPublicationEngine(
      FileSystemRunStoreHandle runStore,
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry policies,
      ArtifactStoreLimits limits) {
    this.runStore = runStore;
    this.canonicalJson = canonicalJson;
    this.limits = limits;
    this.moduleEngine =
        new AtomicCanonicalPublicationEngine(runStore, canonicalJson, policies, limits);
  }

  InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request) {
    ValidatedInstall validated = validate(request);
    try {
      Path stepDirectory = createStepDirectory(validated.request().address());
      Path receiptPath =
          stepDirectory.resolve(validated.request().address().analysisStepKey().receiptFileName());
      if (Files.exists(receiptPath, LinkOption.NOFOLLOW_LINKS)) {
        return reopenEquivalentOrReject(validated);
      }
      requireNoPartialPublicPayloads(stepDirectory);

      Path stepParent = stepDirectory.getParent();
      if (stepParent == null) {
        throw invalidPublication();
      }
      Path staging =
          Files.createTempDirectory(
              stepParent,
              validated.request().address().analysisStepKey().directoryName()
                  + ".semantic.staging-");
      try {
        for (CanonicalAnalysisStepPayload payload : validated.request().semanticPayloads()) {
          writeAndForce(
              staging.resolve(payload.fileName()), payload.canonicalUtf8().copyToByteArray());
        }
        writeAndForce(
            staging.resolve(receiptPath.getFileName()), validated.receiptBytes().copyToByteArray());
        forceDirectory(staging);
        for (CanonicalAnalysisStepPayload payload : validated.request().semanticPayloads()) {
          moveAtomically(
              staging.resolve(payload.fileName()), stepDirectory.resolve(payload.fileName()));
        }
        moveAtomically(staging.resolve(receiptPath.getFileName()), receiptPath);
        forceDirectory(stepDirectory);
      } finally {
        deleteIfStillPresent(staging);
      }
      return new InstalledAnalysisStepPublication(
          validated.reference(), ModuleInstallDisposition.INSTALLED, validated.descriptors(), null);
    } catch (ArtifactStoreException failure) {
      throw failure;
    } catch (IOException failure) {
      throw invalidPublication();
    }
  }

  ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference) {
    if (reference == null) {
      throw invalidPublication();
    }
    try {
      Path directory = existingStepDirectory(reference.address());
      ReceiptBytes receiptBytes = readReceipt(directory, reference.address().analysisStepKey());
      AnalysisStepReceipt receipt = receiptBytes.receipt();
      validateReceiptIdentity(receiptBytes, reference);
      validateReceiptStructure(receipt);
      ReopenedModulePublication publisher =
          moduleEngine.reopenModule(
              ((AnalysisStepPublisherModuleProvenance) receipt.publicationProvenance())
                  .publisherSpecificationModuleReference());
      verifyPublisherMatchesReceipt(receipt, publisher);
      List<VerifiedCanonicalPayload> semanticPayloads = readSemanticPayloads(directory, receipt);
      List<ArtifactDescriptor> descriptors =
          semanticPayloads.stream().map(VerifiedCanonicalPayload::descriptor).toList();
      if (!analysisStepRoot(descriptors).equals(receipt.analysisStepArtifactRoot())) {
        throw invalidPublication();
      }
      requireExactDirectoryContents(directory, receipt.address().analysisStepKey(), descriptors);
      return new ReopenedAnalysisStepPublication(reference, receipt, semanticPayloads, null);
    } catch (ArtifactStoreException failure) {
      throw failure;
    } catch (RuntimeException | IOException failure) {
      throw invalidPublication();
    }
  }

  private ValidatedInstall validate(AnalysisStepInstallRequest request) {
    try {
      if (request == null
          || request.address() == null
          || request.publicationProvenance() == null
          || request.controls() == null
          || request.status() == null
          || request.gapRefs() == null
          || request.semanticPayloads() == null
          || request.archiveManifestSpecification() != null
          || !(request.publicationProvenance() instanceof AnalysisStepPublisherModuleProvenance)) {
        throw invalidInstall();
      }
      StepContract contract = stepContract(request);
      requireLimits(contract);
      requireReceiptState(contract, request, true);
      requireStrictPayloadOrder(request.semanticPayloads());
      ReopenedModulePublication publisher =
          moduleEngine.reopenModule(
              ((AnalysisStepPublisherModuleProvenance) request.publicationProvenance())
                  .publisherSpecificationModuleReference());
      verifyPublisherAddress(request.address(), publisher.reference().address());
      if (!request.controls().equals(publisher.receipt().controls())
          || request.status() != publisher.receipt().status()
          || !request.gapRefs().equals(publisher.receipt().gapRefs())) {
        throw invalidInstall();
      }
      List<ArtifactDescriptor> descriptors =
          request.semanticPayloads().stream().map(this::descriptor).toList();
      requireExpectedSemanticSet(contract, descriptors);
      if (!descriptors.equals(
              publisher.payloads().stream().map(VerifiedCanonicalPayload::descriptor).toList())
          || !request.semanticPayloads().stream()
              .map(CanonicalAnalysisStepPayload::canonicalUtf8)
              .toList()
              .equals(
                  publisher.payloads().stream()
                      .map(VerifiedCanonicalPayload::canonicalUtf8)
                      .toList())) {
        throw invalidInstall();
      }
      AnalysisStepArtifactRoot root = analysisStepRoot(descriptors);
      AnalysisStepReceipt incomplete =
          new AnalysisStepReceipt(
              RECEIPT_SCHEMA,
              null,
              request.address(),
              request.publicationProvenance(),
              request.upstreamAnalysisStepReferences(),
              request.controls(),
              request.status(),
              descriptors,
              null,
              root,
              request.gapRefs());
      ImmutableBytes withoutId = canonicalJson.encodeCanonical(receiptNode(incomplete, false));
      AnalysisStepReceiptId receiptId =
          new AnalysisStepReceiptId(
              "analysis-step-receipt:"
                  + sha256(concatenate(frame(RECEIPT_DOMAIN), frame(withoutId.copyToByteArray()))));
      AnalysisStepReceipt complete =
          new AnalysisStepReceipt(
              incomplete.schemaVersion(),
              receiptId,
              incomplete.address(),
              incomplete.publicationProvenance(),
              incomplete.upstreamAnalysisStepReferences(),
              incomplete.controls(),
              incomplete.status(),
              incomplete.semanticArtifacts(),
              null,
              incomplete.analysisStepArtifactRoot(),
              incomplete.gapRefs());
      ImmutableBytes receiptBytes = canonicalJson.encodeCanonical(receiptNode(complete, true));
      AnalysisStepPublicationReference reference =
          new AnalysisStepPublicationReference(
              request.address(),
              root,
              receiptId,
              new Sha256Digest(sha256(receiptBytes.copyToByteArray())));
      return new ValidatedInstall(request, descriptors, receiptBytes, reference);
    } catch (ArtifactStoreException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw invalidInstall();
    }
  }

  private ArtifactDescriptor descriptor(CanonicalAnalysisStepPayload payload) {
    if (payload == null
        || payload.fileName() == null
        || payload.artifactType() == null
        || payload.schemaVersion() == null
        || payload.artifactId() == null
        || payload.mediaType() == null
        || payload.canonicalUtf8() == null
        || payload.canonicalUtf8().size() > limits.maxArtifactBytes()) {
      throw invalidInstall();
    }
    return new ArtifactDescriptor(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8().size(),
        new Sha256Digest(sha256(payload.canonicalUtf8().copyToByteArray())));
  }

  private InstalledAnalysisStepPublication reopenEquivalentOrReject(ValidatedInstall expected) {
    try {
      ReopenedAnalysisStepPublication reopened = reopen(expected.reference());
      if (!reopened.semanticPayloads().stream()
          .map(VerifiedCanonicalPayload::descriptor)
          .toList()
          .equals(expected.descriptors())) {
        throw collision();
      }
      return new InstalledAnalysisStepPublication(
          expected.reference(),
          ModuleInstallDisposition.ALREADY_INSTALLED,
          expected.descriptors(),
          null);
    } catch (ArtifactStoreException failure) {
      throw collision();
    }
  }

  private void verifyPublisherAddress(
      AnalysisStepPublicationAddress stepAddress, ModulePublicationAddress publisherAddress) {
    StepContract contract = stepContract(stepAddress.analysisStepKey(), publisherAddress);
    if (!(publisherAddress instanceof AnalysisStepModuleAddress publisher)
        || !publisher.runId().equals(stepAddress.runId())
        || publisher.analysisStepKey() != stepAddress.analysisStepKey()
        || publisher.moduleNumber() != contract.publisherModuleNumber()
        || !contract.publisherModuleKey().equals(publisher.moduleKey())) {
      throw invalidInstall();
    }
  }

  private void verifyPublisherMatchesReceipt(
      AnalysisStepReceipt receipt, ReopenedModulePublication publisher) {
    verifyPublisherAddress(receipt.address(), publisher.reference().address());
    if (!receipt.controls().equals(publisher.receipt().controls())
        || receipt.status() != publisher.receipt().status()
        || !receipt.gapRefs().equals(publisher.receipt().gapRefs())) {
      throw invalidPublication();
    }
    List<ArtifactDescriptor> expected =
        publisher.payloads().stream().map(VerifiedCanonicalPayload::descriptor).toList();
    if (!receipt.semanticArtifacts().equals(expected)) {
      throw invalidPublication();
    }
  }

  private List<VerifiedCanonicalPayload> readSemanticPayloads(
      Path directory, AnalysisStepReceipt receipt) throws IOException {
    List<VerifiedCanonicalPayload> payloads = new ArrayList<>();
    for (ArtifactDescriptor descriptor : receipt.semanticArtifacts()) {
      Path path = directory.resolve(descriptor.fileName());
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
        throw invalidPublication();
      }
      byte[] bytes = Files.readAllBytes(path);
      if (bytes.length != descriptor.sizeBytes()
          || !sha256(bytes).equals(descriptor.sha256().value())) {
        throw invalidPublication();
      }
      payloads.add(new VerifiedCanonicalPayload(descriptor, ImmutableBytes.copyOf(bytes)));
    }
    return List.copyOf(payloads);
  }

  private ReceiptBytes readReceipt(Path directory, AnalysisStepKey key) throws IOException {
    Path receiptPath = directory.resolve(key.receiptFileName());
    if (!Files.isRegularFile(receiptPath, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(receiptPath)
        || Files.size(receiptPath) > limits.maxPublicationBytes()) {
      throw invalidPublication();
    }
    ImmutableBytes bytes = ImmutableBytes.copyOf(Files.readAllBytes(receiptPath));
    ObjectNode node = object(canonicalJson.parseCanonical(bytes));
    AnalysisStepReceipt receipt = receiptFromNode(node);
    AnalysisStepReceipt withoutId =
        new AnalysisStepReceipt(
            receipt.schemaVersion(),
            null,
            receipt.address(),
            receipt.publicationProvenance(),
            receipt.upstreamAnalysisStepReferences(),
            receipt.controls(),
            receipt.status(),
            receipt.semanticArtifacts(),
            receipt.archiveManifest(),
            receipt.analysisStepArtifactRoot(),
            receipt.gapRefs());
    String expectedId =
        "analysis-step-receipt:"
            + sha256(
                concatenate(
                    frame(RECEIPT_DOMAIN),
                    frame(
                        canonicalJson
                            .encodeCanonical(receiptNode(withoutId, false))
                            .copyToByteArray())));
    if (!expectedId.equals(receipt.analysisStepReceiptId().value())) {
      throw invalidPublication();
    }
    return new ReceiptBytes(receipt, bytes);
  }

  private void validateReceiptIdentity(
      ReceiptBytes receiptBytes, AnalysisStepPublicationReference reference) {
    AnalysisStepReceipt receipt = receiptBytes.receipt();
    if (!receipt.address().equals(reference.address())
        || !receipt.analysisStepArtifactRoot().equals(reference.analysisStepArtifactRoot())
        || !receipt.analysisStepReceiptId().equals(reference.analysisStepReceiptId())
        || !new Sha256Digest(sha256(receiptBytes.canonicalUtf8().copyToByteArray()))
            .equals(reference.analysisStepReceiptSha256())) {
      throw invalidPublication();
    }
  }

  private void validateReceiptStructure(AnalysisStepReceipt receipt) {
    StepContract contract = stepContract(receipt);
    if (!(receipt.publicationProvenance() instanceof AnalysisStepPublisherModuleProvenance)
        || receipt.archiveManifest() != null) {
      throw invalidPublication();
    }
    requireReceiptState(contract, receipt, false);
    requireExpectedSemanticSet(contract, receipt.semanticArtifacts());
  }

  private ObjectNode receiptNode(AnalysisStepReceipt receipt, boolean includeId) {
    ObjectNode node = JSON.createObjectNode();
    node.put("schemaVersion", receipt.schemaVersion());
    if (includeId) {
      node.put("analysisStepReceiptId", receipt.analysisStepReceiptId().value());
    }
    node.set("address", addressNode(receipt.address()));
    node.set("publicationProvenance", provenanceNode(receipt.publicationProvenance()));
    node.set(
        "upstreamAnalysisStepReferences",
        analysisStepReferences(receipt.upstreamAnalysisStepReferences()));
    node.set("controls", controlsNode(receipt.controls()));
    node.put("status", receipt.status().name());
    node.set("semanticArtifacts", descriptorArray(receipt.semanticArtifacts()));
    node.putNull("archiveManifest");
    node.put("analysisStepArtifactRoot", receipt.analysisStepArtifactRoot().value());
    node.put("gapCount", receipt.gapRefs().size());
    ArrayNode gapRefs = node.putArray("gapRefs");
    receipt.gapRefs().forEach(gapRefs::add);
    return node;
  }

  private AnalysisStepReceipt receiptFromNode(ObjectNode node) {
    Set<String> expectedFields =
        Set.of(
            "schemaVersion",
            "analysisStepReceiptId",
            "address",
            "publicationProvenance",
            "upstreamAnalysisStepReferences",
            "controls",
            "status",
            "semanticArtifacts",
            "archiveManifest",
            "analysisStepArtifactRoot",
            "gapCount",
            "gapRefs");
    if (!fieldNames(node).equals(expectedFields)
        || !RECEIPT_SCHEMA.equals(text(node, "schemaVersion"))
        || !node.get("archiveManifest").isNull()
        || !node.get("upstreamAnalysisStepReferences").isArray()
        || !node.get("gapRefs").isArray()
        || !node.get("gapCount").canConvertToInt()) {
      throw invalidPublication();
    }
    List<AnalysisStepPublicationReference> upstream =
        analysisStepReferencesFromNode(array(node.get("upstreamAnalysisStepReferences")));
    List<String> gapRefs = stringsFromNode(array(node.get("gapRefs")));
    if (node.get("gapCount").intValue() != gapRefs.size()) {
      throw invalidPublication();
    }
    return new AnalysisStepReceipt(
        RECEIPT_SCHEMA,
        AnalysisStepReceiptId.parse(text(node, "analysisStepReceiptId")),
        addressFromNode(object(node.get("address"))),
        provenanceFromNode(object(node.get("publicationProvenance"))),
        upstream,
        controlsFromNode(object(node.get("controls"))),
        ModuleCompletionStatus.valueOf(text(node, "status")),
        descriptorsFromNode(array(node.get("semanticArtifacts"))),
        null,
        AnalysisStepArtifactRoot.parse(text(node, "analysisStepArtifactRoot")),
        gapRefs);
  }

  private ObjectNode addressNode(AnalysisStepPublicationAddress address) {
    return JSON.createObjectNode()
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue());
  }

  private AnalysisStepPublicationAddress addressFromNode(ObjectNode node) {
    if (!fieldNames(node).equals(Set.of("runId", "analysisStepKey"))) {
      throw invalidPublication();
    }
    return new AnalysisStepPublicationAddress(
        AnalysisRunId.parse(text(node, "runId")),
        AnalysisStepKey.parse(text(node, "analysisStepKey")));
  }

  private ObjectNode provenanceNode(AnalysisStepPublicationProvenance provenance) {
    if (!(provenance instanceof AnalysisStepPublisherModuleProvenance publisher)) {
      throw invalidInstall();
    }
    ObjectNode node = JSON.createObjectNode();
    node.put("kind", "ANALYSIS_STEP_PUBLISHER_MODULE");
    node.set(
        "publisherSpecificationModuleReference",
        moduleReferenceNode(publisher.publisherSpecificationModuleReference()));
    return node;
  }

  private AnalysisStepPublicationProvenance provenanceFromNode(ObjectNode node) {
    if (!fieldNames(node).equals(Set.of("kind", "publisherSpecificationModuleReference"))
        || !"ANALYSIS_STEP_PUBLISHER_MODULE".equals(text(node, "kind"))) {
      throw invalidPublication();
    }
    return new AnalysisStepPublisherModuleProvenance(
        moduleReferenceFromNode(object(node.get("publisherSpecificationModuleReference"))));
  }

  private ObjectNode moduleReferenceNode(ModulePublicationReference reference) {
    ObjectNode node = JSON.createObjectNode();
    node.set("address", moduleAddressNode(reference.address()));
    node.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    node.put("moduleReceiptId", reference.moduleReceiptId().value());
    node.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
    return node;
  }

  private ModulePublicationReference moduleReferenceFromNode(ObjectNode node) {
    if (!fieldNames(node)
        .equals(
            Set.of("address", "moduleArtifactRoot", "moduleReceiptId", "moduleReceiptSha256"))) {
      throw invalidPublication();
    }
    return new ModulePublicationReference(
        moduleAddressFromNode(object(node.get("address"))),
        ModuleArtifactRoot.parse(text(node, "moduleArtifactRoot")),
        ModuleReceiptId.parse(text(node, "moduleReceiptId")),
        Sha256Digest.parse(text(node, "moduleReceiptSha256")));
  }

  private ObjectNode moduleAddressNode(ModulePublicationAddress address) {
    if (!(address instanceof AnalysisStepModuleAddress module)) {
      throw invalidInstall();
    }
    return JSON.createObjectNode()
        .put("kind", "ANALYSIS_STEP")
        .put("runId", module.runId().value())
        .put("analysisStepKey", module.analysisStepKey().wireValue())
        .put("moduleNumber", module.moduleNumber())
        .put("moduleKey", module.moduleKey());
  }

  private ModulePublicationAddress moduleAddressFromNode(ObjectNode node) {
    if (!fieldNames(node)
            .equals(Set.of("kind", "runId", "analysisStepKey", "moduleNumber", "moduleKey"))
        || !"ANALYSIS_STEP".equals(text(node, "kind"))
        || !node.get("moduleNumber").canConvertToInt()) {
      throw invalidPublication();
    }
    return new AnalysisStepModuleAddress(
        AnalysisRunId.parse(text(node, "runId")),
        AnalysisStepKey.parse(text(node, "analysisStepKey")),
        node.get("moduleNumber").intValue(),
        text(node, "moduleKey"));
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
    ObjectNode registry = object(node.get("artifactPolicyRegistryRef"));
    JsonNode prompt = node.get("promptBundleSha256");
    if (!fieldNames(registry).equals(Set.of("artifactId", "sha256"))
        || prompt == null
        || !(prompt.isNull() || prompt.isTextual())) {
      throw invalidPublication();
    }
    return new ArtifactControls(
        Sha256Digest.parse(text(node, "toolchainSha256")),
        Sha256Digest.parse(text(node, "profileSha256")),
        Sha256Digest.parse(text(node, "schemaBundleSha256")),
        prompt.isNull() ? null : Sha256Digest.parse(prompt.textValue()),
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse(text(registry, "artifactId")),
            Sha256Digest.parse(text(registry, "sha256"))));
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
    List<ArtifactDescriptor> descriptors = new ArrayList<>();
    for (JsonNode item : array) {
      ObjectNode node = object(item);
      if (!fieldNames(node)
              .equals(
                  Set.of(
                      "fileName",
                      "artifactType",
                      "schemaVersion",
                      "artifactId",
                      "mediaType",
                      "sizeBytes",
                      "sha256"))
          || !node.get("sizeBytes").canConvertToLong()) {
        throw invalidPublication();
      }
      descriptors.add(
          new ArtifactDescriptor(
              text(node, "fileName"),
              text(node, "artifactType"),
              text(node, "schemaVersion"),
              ArtifactId.parse(text(node, "artifactId")),
              CanonicalMediaType.parse(text(node, "mediaType")),
              node.get("sizeBytes").longValue(),
              Sha256Digest.parse(text(node, "sha256"))));
    }
    return List.copyOf(descriptors);
  }

  private void requireExpectedSemanticSet(
      StepContract contract, List<ArtifactDescriptor> descriptors) {
    List<String> names = descriptors.stream().map(ArtifactDescriptor::fileName).toList();
    if (!contract.semanticFileSets().contains(names)) {
      throw invalidPublication();
    }
  }

  private AnalysisStepArtifactRoot analysisStepRoot(List<ArtifactDescriptor> descriptors) {
    byte[] bytes = concatenate(frame(ROOT_DOMAIN), u32(descriptors.size()));
    for (ArtifactDescriptor descriptor : descriptors) {
      bytes = concatenate(bytes, frame(descriptorBytes(descriptor)));
    }
    return new AnalysisStepArtifactRoot("analysis-step-root:" + sha256(bytes));
  }

  private static byte[] descriptorBytes(ArtifactDescriptor descriptor) {
    return concatenate(
        frame(descriptor.fileName()),
        frame(descriptor.artifactType()),
        frame(descriptor.schemaVersion()),
        frame(descriptor.artifactId().value()),
        frame(descriptor.mediaType().wireValue()),
        u64(descriptor.sizeBytes()),
        java.util.HexFormat.of().parseHex(descriptor.sha256().value()));
  }

  private Path createStepDirectory(AnalysisStepPublicationAddress address) throws IOException {
    Path current = runStore.rootForStore();
    requireDirectory(current);
    for (String segment :
        List.of(
            "runs",
            encodedSegment(address.runId().value()),
            "steps",
            address.analysisStepKey().directoryName())) {
      current = current.resolve(segment);
      if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        Files.createDirectory(current);
      }
      requireDirectory(current);
    }
    return current;
  }

  private Path existingStepDirectory(AnalysisStepPublicationAddress address) throws IOException {
    Path current = runStore.rootForStore();
    requireDirectory(current);
    for (String segment :
        List.of(
            "runs",
            encodedSegment(address.runId().value()),
            "steps",
            address.analysisStepKey().directoryName())) {
      current = current.resolve(segment);
      requireDirectory(current);
    }
    return current;
  }

  private void requireNoPartialPublicPayloads(Path directory) throws IOException {
    try (Stream<Path> entries = Files.list(directory)) {
      for (Path entry : entries.toList()) {
        Path fileName = entry.getFileName();
        if (fileName == null) {
          throw invalidPublication();
        }
        String name = fileName.toString();
        if (!"modules".equals(name)) {
          throw invalidPublication();
        }
      }
    }
  }

  private void requireExactDirectoryContents(
      Path directory, AnalysisStepKey key, List<ArtifactDescriptor> descriptors)
      throws IOException {
    Set<String> expected = new HashSet<>();
    expected.add("modules");
    expected.add(key.receiptFileName());
    descriptors.forEach(descriptor -> expected.add(descriptor.fileName()));
    try (Stream<Path> entries = Files.list(directory)) {
      Set<String> actual = new HashSet<>();
      for (Path entry : entries.toList()) {
        Path fileName = entry.getFileName();
        if (fileName == null) {
          throw invalidPublication();
        }
        String name = fileName.toString();
        actual.add(name);
        if ("modules".equals(name)) {
          requireDirectory(entry);
        } else if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(entry)) {
          throw invalidPublication();
        }
      }
      if (!actual.equals(expected)) {
        throw invalidPublication();
      }
    }
  }

  private void requireLimits(StepContract contract) {
    int largestSet = contract.semanticFileSets().stream().mapToInt(List::size).max().orElseThrow();
    if (limits.maxPayloadFiles() < largestSet
        || limits.maxArtifactBytes() < 0
        || limits.maxPublicationBytes() < 0
        || limits.maxDirectoryEntries() < largestSet + 2) {
      throw invalidInstall();
    }
  }

  private static StepContract stepContract(AnalysisStepInstallRequest request) {
    return stepContract(
        request.address().analysisStepKey(),
        ((AnalysisStepPublisherModuleProvenance) request.publicationProvenance())
            .publisherSpecificationModuleReference()
            .address());
  }

  private static StepContract stepContract(AnalysisStepReceipt receipt) {
    return stepContract(
        receipt.address().analysisStepKey(),
        ((AnalysisStepPublisherModuleProvenance) receipt.publicationProvenance())
            .publisherSpecificationModuleReference()
            .address());
  }

  private static StepContract stepContract(
      AnalysisStepKey analysisStepKey, ModulePublicationAddress publisherAddress) {
    if (!(publisherAddress instanceof AnalysisStepModuleAddress publisher)
        || publisher.analysisStepKey() != analysisStepKey) {
      throw invalidInstall();
    }
    return switch (analysisStepKey) {
      case VERIFIED_SOURCE_INVENTORY ->
          new StepContract(
              3,
              "publish",
              List.of(
                  List.of("source-input.json", "source-inventory.jsonl", "verified-snapshot.json")),
              List.of());
      case APPLICATION_DISCOVERY ->
          new StepContract(
              4,
              "publish",
              List.of(
                  List.of(
                      "application-profile.json",
                      "capability-report.json",
                      "entry-points.jsonl",
                      "mapper-catalog.jsonl")),
              List.of(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY));
      case PROGRAM_GRAPHS ->
          publisher.moduleNumber() == 7 && "java-code-index".equals(publisher.moduleKey())
              ? new StepContract(
                  7,
                  "java-code-index",
                  List.of(List.of("java-code-index.jsonl")),
                  List.of(
                      AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
                      AnalysisStepKey.APPLICATION_DISCOVERY))
              : new StepContract(
                  6,
                  "publish",
                  List.of(
                      List.of(
                          "call-graph.json",
                          "code-structure-graph.json",
                          "control-flow-graph.json",
                          "data-flow-graph.json",
                          "evidence-graph.json",
                          "graph-gaps.jsonl",
                          "graph-index.json")),
                  List.of(
                      AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
                      AnalysisStepKey.APPLICATION_DISCOVERY));
      case PROVEN_CODE_FACTS ->
          new StepContract(
              3,
              "publish",
              List.of(
                  List.of("fact-accounting.json"),
                  List.of(
                      "fact-accounting.json",
                      "gap-ledger.json",
                      "proof-pack.json",
                      "proven-facts.json")),
              List.of(
                  AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
                  AnalysisStepKey.APPLICATION_DISCOVERY,
                  AnalysisStepKey.PROGRAM_GRAPHS));
      case BUSINESS_FLOWS ->
          new StepContract(
              3,
              "publish",
              List.of(
                  List.of(
                      "entry-dispositions.jsonl",
                      "evidence-capsules.jsonl",
                      "flow-coverage.json",
                      "flow-gaps.jsonl",
                      "flow-slices.json")),
              List.of(
                  AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
                  AnalysisStepKey.APPLICATION_DISCOVERY,
                  AnalysisStepKey.PROGRAM_GRAPHS,
                  AnalysisStepKey.PROVEN_CODE_FACTS));
      default -> throw invalidInstall();
    };
  }

  private static void requireReceiptState(
      StepContract contract, AnalysisStepInstallRequest request, boolean installation) {
    requireReceiptState(
        contract,
        request.upstreamAnalysisStepReferences(),
        request.status(),
        request.gapRefs(),
        installation);
  }

  private static void requireReceiptState(
      StepContract contract, AnalysisStepReceipt receipt, boolean installation) {
    requireReceiptState(
        contract,
        receipt.upstreamAnalysisStepReferences(),
        receipt.status(),
        receipt.gapRefs(),
        installation);
  }

  private static void requireReceiptState(
      StepContract contract,
      List<AnalysisStepPublicationReference> upstream,
      ModuleCompletionStatus status,
      List<String> gapRefs,
      boolean installation) {
    boolean validUpstream =
        upstream != null
            && upstream.size() == contract.upstreamStepKeys().size()
            && java.util.stream.IntStream.range(0, upstream.size())
                .allMatch(
                    index ->
                        upstream.get(index) != null
                            && upstream.get(index).address().analysisStepKey()
                                == contract.upstreamStepKeys().get(index));
    boolean validStatus =
        status != null
            && gapRefs != null
            && (gapRefs.isEmpty()
                ? status == ModuleCompletionStatus.SUCCEEDED
                : status == ModuleCompletionStatus.SUCCEEDED_WITH_GAPS);
    if (!validUpstream || !validStatus) {
      throw installation ? invalidInstall() : invalidPublication();
    }
    requireStrictStrings(gapRefs, installation);
  }

  private static void requireStrictStrings(List<String> values, boolean installation) {
    String previous = null;
    for (String value : values) {
      if (value == null
          || value.isBlank()
          || (previous != null && UTF8_ORDER.compare(previous, value) >= 0)) {
        throw installation ? invalidInstall() : invalidPublication();
      }
      previous = value;
    }
  }

  private ArrayNode analysisStepReferences(List<AnalysisStepPublicationReference> references) {
    ArrayNode values = JSON.createArrayNode();
    for (AnalysisStepPublicationReference reference : references) {
      ObjectNode value = values.addObject();
      value.set("address", addressNode(reference.address()));
      value.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
      value.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
      value.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
    }
    return values;
  }

  private List<AnalysisStepPublicationReference> analysisStepReferencesFromNode(ArrayNode values) {
    List<AnalysisStepPublicationReference> references = new ArrayList<>();
    for (JsonNode value : values) {
      ObjectNode node = object(value);
      if (!fieldNames(node)
          .equals(
              Set.of(
                  "address",
                  "analysisStepArtifactRoot",
                  "analysisStepReceiptId",
                  "analysisStepReceiptSha256"))) {
        throw invalidPublication();
      }
      references.add(
          new AnalysisStepPublicationReference(
              addressFromNode(object(node.get("address"))),
              AnalysisStepArtifactRoot.parse(text(node, "analysisStepArtifactRoot")),
              AnalysisStepReceiptId.parse(text(node, "analysisStepReceiptId")),
              Sha256Digest.parse(text(node, "analysisStepReceiptSha256"))));
    }
    return List.copyOf(references);
  }

  private List<String> stringsFromNode(ArrayNode values) {
    List<String> strings = new ArrayList<>();
    for (JsonNode value : values) {
      if (!value.isTextual()) {
        throw invalidPublication();
      }
      strings.add(value.textValue());
    }
    requireStrictStrings(strings, false);
    return List.copyOf(strings);
  }

  private static void requireStrictPayloadOrder(List<CanonicalAnalysisStepPayload> payloads) {
    String previous = null;
    for (CanonicalAnalysisStepPayload payload : payloads) {
      if (payload == null
          || payload.fileName() == null
          || (previous != null && UTF8_ORDER.compare(previous, payload.fileName()) >= 0)) {
        throw invalidInstall();
      }
      previous = payload.fileName();
    }
  }

  private static ObjectNode object(JsonNode node) {
    if (node instanceof ObjectNode object) {
      return object;
    }
    throw invalidPublication();
  }

  private static ArrayNode array(JsonNode node) {
    if (node instanceof ArrayNode array) {
      return array;
    }
    throw invalidPublication();
  }

  private static String text(ObjectNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isTextual()) {
      throw invalidPublication();
    }
    return value.textValue();
  }

  private static Set<String> fieldNames(ObjectNode node) {
    Set<String> fields = new HashSet<>();
    node.fieldNames().forEachRemaining(fields::add);
    return fields;
  }

  private static void requireDirectory(Path directory) {
    if (Files.isSymbolicLink(directory)
        || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw invalidPublication();
    }
  }

  private static void writeAndForce(Path path, byte[] bytes) throws IOException {
    try (FileChannel channel =
        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
    }
  }

  private static void forceDirectory(Path directory) throws IOException {
    try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    }
  }

  private static void moveAtomically(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException failure) {
      throw new ArtifactStoreException("ATOMIC_MOVE_UNSUPPORTED");
    }
  }

  private static void deleteIfStillPresent(Path path) {
    try {
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        try (Stream<Path> files = Files.walk(path)) {
          files
              .sorted(Comparator.reverseOrder())
              .forEach(AtomicAnalysisStepPublicationEngine::delete);
        }
      }
    } catch (IOException ignored) {
      // Staging residue has no receipt and is never a successful publication.
    }
  }

  private static void delete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best-effort staging cleanup cannot change publication success.
    }
  }

  private static String encodedSegment(String identifier) {
    return identifier.replace(":", "--");
  }

  private static int compareUtf8(String left, String right) {
    byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
    byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
    int length = Math.min(leftBytes.length, rightBytes.length);
    for (int index = 0; index < length; index++) {
      int comparison =
          Integer.compare(
              Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(leftBytes.length, rightBytes.length);
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return concatenate(u64(value.length), value);
  }

  private static byte[] u32(int value) {
    return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
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
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  private static ArtifactStoreException invalidInstall() {
    return new ArtifactStoreException("ANALYSIS_STEP_INSTALL_REQUEST_INVALID");
  }

  private static ArtifactStoreException invalidPublication() {
    return new ArtifactStoreException("ANALYSIS_STEP_PUBLICATION_INVALID");
  }

  private static ArtifactStoreException collision() {
    return new ArtifactStoreException("ANALYSIS_STEP_PUBLICATION_COLLISION");
  }

  private record ValidatedInstall(
      AnalysisStepInstallRequest request,
      List<ArtifactDescriptor> descriptors,
      ImmutableBytes receiptBytes,
      AnalysisStepPublicationReference reference) {}

  private record ReceiptBytes(AnalysisStepReceipt receipt, ImmutableBytes canonicalUtf8) {}

  private record StepContract(
      int publisherModuleNumber,
      String publisherModuleKey,
      List<List<String>> semanticFileSets,
      List<AnalysisStepKey> upstreamStepKeys) {}
}
