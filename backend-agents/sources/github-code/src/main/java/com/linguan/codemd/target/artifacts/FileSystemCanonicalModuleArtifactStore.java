package com.linguan.codemd.target.artifacts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.channels.FileChannel;
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
import java.util.HexFormat;
import java.util.List;

/** Filesystem implementation of the module-level receipt-last publication boundary. */
public final class FileSystemCanonicalModuleArtifactStore implements CanonicalModuleArtifactStore {
    private static final ObjectMapper JSON =
            new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final String RECEIPT_FILE = "module-receipt.json";
    private static final String RECEIPT_SCHEMA = "module-receipt-v1";
    private static final Comparator<ArtifactDescriptor> BY_FILE_NAME =
            Comparator.comparing(ArtifactDescriptor::fileName);

    private final FileSystemRunStoreHandle runStore;
    private final CanonicalJsonCodec canonicalJson;
    private final CanonicalArtifactPolicyRegistry artifactPolicies;
    private final ArtifactStoreLimits limits;

    public FileSystemCanonicalModuleArtifactStore(
            RunStoreHandle runStore,
            CanonicalJsonCodec canonicalJson,
            CanonicalArtifactPolicyRegistry artifactPolicies,
            ArtifactStoreLimits limits) {
        if (!(runStore instanceof FileSystemRunStoreHandle fileSystemRunStore)
                || canonicalJson == null
                || artifactPolicies == null
                || limits == null) {
            throw new ArtifactStoreException(
                    "MODULE_INSTALL_REQUEST_INVALID", "store constructor arguments are invalid");
        }
        this.runStore = fileSystemRunStore;
        this.canonicalJson = canonicalJson;
        this.artifactPolicies = artifactPolicies;
        this.limits = limits;
    }

    @Override
    public synchronized InstalledModulePublication install(ModuleInstallRequest request) {
        if (request == null) {
            throw new ArtifactStoreException("MODULE_INSTALL_REQUEST_INVALID", "request must not be null");
        }
        verifyRegistryReference(request.controls());
        List<VerifiedCanonicalPayload> payloads = verifyRequestPayloads(request.payloads());
        List<ArtifactDescriptor> descriptors =
                payloads.stream().map(VerifiedCanonicalPayload::descriptor).sorted(BY_FILE_NAME).toList();
        StageSemanticSchemaRegistry.requirePublisherPayloads(request.address(), descriptors);
        String moduleArtifactRoot = descriptorListRoot("canonical-module-artifact-root-v1", "module-root", descriptors);
        PreparedReceipt preparedReceipt = prepareReceipt(request, descriptors, moduleArtifactRoot);
        ModulePublicationReference reference =
                new ModulePublicationReference(
                        request.address(),
                        moduleArtifactRoot,
                        preparedReceipt.receipt().moduleReceiptId(),
                        preparedReceipt.sha256());
        Path destination = moduleDirectory(request.address());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            ModulePublicationReference existingReference = storedReference(destination);
            reopen(existingReference);
            if (existingReference.equals(reference)) {
                return new InstalledModulePublication(reference, "ALREADY_INSTALLED", descriptors);
            }
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_COLLISION", "module address is already bound to different bytes");
        }
        installAtomically(destination, payloads, preparedReceipt.canonicalUtf8());
        return new InstalledModulePublication(reference, "INSTALLED", descriptors);
    }

    private ModulePublicationReference storedReference(Path destination) {
        requireDirectory(destination, "module directory");
        byte[] receiptBytes = readRegularFile(destination.resolve(RECEIPT_FILE));
        ModuleReceipt receipt = parseReceipt(receiptBytes);
        return new ModulePublicationReference(
                receipt.address(),
                receipt.moduleArtifactRoot(),
                receipt.moduleReceiptId(),
                sha256(receiptBytes));
    }

    @Override
    public synchronized ReopenedModulePublication reopen(ModulePublicationReference reference) {
        if (reference == null) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "reference must not be null");
        }
        Path directory = moduleDirectory(reference.address());
        requireDirectory(directory, "module directory");
        byte[] receiptBytes = readRegularFile(directory.resolve(RECEIPT_FILE));
        ModuleReceipt receipt = parseReceipt(receiptBytes);
        verifyReceiptIdentity(receiptBytes, receipt, reference);
        if (!reference.address().equals(receipt.address())
                || !reference.moduleArtifactRoot().equals(receipt.moduleArtifactRoot())) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_INVALID", "receipt and requested module reference differ");
        }
        verifyRegistryReference(receipt.controls());
        requireExactDirectoryContents(directory, receipt.payloadArtifacts());

        List<VerifiedCanonicalPayload> payloads = new ArrayList<>();
        for (ArtifactDescriptor descriptor : receipt.payloadArtifacts()) {
            byte[] bytes = readRegularFile(directory.resolve(descriptor.fileName()));
            if (!descriptor.sha256().equals(sha256(bytes)) || descriptor.sizeBytes() != bytes.length) {
                throw new ArtifactStoreException(
                        "MODULE_PUBLICATION_INVALID", "payload descriptor does not match stored bytes");
            }
            payloads.add(verifyPayload(descriptor, ImmutableBytes.copyOf(bytes)));
        }
        payloads.sort(Comparator.comparing(payload -> payload.descriptor().fileName()));
        String recomputedRoot =
                descriptorListRoot(
                        "canonical-module-artifact-root-v1",
                        "module-root",
                        payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList());
        if (!reference.moduleArtifactRoot().equals(recomputedRoot)) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "module artifact root does not match");
        }
        return new ReopenedModulePublication(reference, receipt, List.copyOf(payloads));
    }

    private List<VerifiedCanonicalPayload> verifyRequestPayloads(List<CanonicalModulePayload> payloads) {
        if (payloads.size() > limits.maxPayloadFiles()) {
            throw new ArtifactStoreException("MODULE_INSTALL_REQUEST_INVALID", "payload count exceeds store limit");
        }
        List<VerifiedCanonicalPayload> verified = new ArrayList<>();
        for (CanonicalModulePayload payload : payloads) {
            verified.add(
                    verifyPayload(
                            new ArtifactDescriptor(
                                    payload.fileName(),
                                    payload.artifactType(),
                                    payload.schemaVersion(),
                                    payload.artifactId(),
                                    payload.mediaType(),
                                    payload.canonicalUtf8().size(),
                                    sha256(payload.canonicalUtf8().copyToByteArray())),
                            payload.canonicalUtf8()));
        }
        verified.sort(Comparator.comparing(payload -> payload.descriptor().fileName()));
        for (int index = 1; index < verified.size(); index++) {
            if (verified.get(index - 1)
                    .descriptor()
                    .fileName()
                    .equals(verified.get(index).descriptor().fileName())) {
                throw new ArtifactStoreException(
                        "MODULE_INSTALL_REQUEST_INVALID", "payload filenames must be unique");
            }
        }
        long totalBytes = verified.stream().mapToLong(payload -> payload.canonicalUtf8().size()).sum();
        if (totalBytes > limits.maxPublicationBytes()) {
            throw new ArtifactStoreException(
                    "MODULE_INSTALL_REQUEST_INVALID", "publication size exceeds store limit");
        }
        return List.copyOf(verified);
    }

    private VerifiedCanonicalPayload verifyPayload(ArtifactDescriptor descriptor, ImmutableBytes bytes) {
        if (bytes.size() > limits.maxArtifactBytes()) {
            throw new ArtifactStoreException(
                    "MODULE_INSTALL_REQUEST_INVALID", "artifact size exceeds store limit");
        }
        CanonicalArtifactPolicy policy =
                artifactPolicies.resolve(new ArtifactPolicyKey(descriptor.artifactType(), descriptor.schemaVersion()));
        if (!policy.mediaType().equals(descriptor.mediaType())) {
            throw new ArtifactStoreException("ARTIFACT_POLICY_MISMATCH", "media type differs from policy");
        }
        byte[] raw = bytes.copyToByteArray();
        String expectedArtifactId = expectedArtifactId(policy, descriptor, raw);
        if (!expectedArtifactId.equals(descriptor.artifactId())) {
            throw new ArtifactStoreException("ARTIFACT_POLICY_MISMATCH", "artifact ID differs from canonical bytes");
        }
        return new VerifiedCanonicalPayload(descriptor, bytes);
    }

    private String expectedArtifactId(
            CanonicalArtifactPolicy policy, ArtifactDescriptor descriptor, byte[] rawBytes) {
        if ("CANONICAL_JSONL".equals(policy.envelopeKind())) {
            verifyCanonicalJsonl(rawBytes, policy.emptyJsonlAllowed());
            return policy.artifactIdPrefix()
                    + ":"
                    + framedDigest(
                            "canonical-jsonl-artifact-id-v1",
                            descriptor.schemaVersion(),
                            descriptor.artifactType(),
                            rawBytes);
        }
        if (!("STANDALONE_JSON".equals(policy.envelopeKind())
                || "MODULE_ARTIFACT_JSON".equals(policy.envelopeKind()))) {
            throw new ArtifactStoreException(
                    "ARTIFACT_POLICY_MISMATCH", "module store requires a registered canonical JSON or JSONL policy");
        }
        ObjectNode document = parseCanonicalObject(rawBytes);
        if (!document.has("artifactId") || !document.get("artifactId").isTextual()) {
            throw new ArtifactStoreException(
                    "MODULE_PAYLOAD_NOT_CANONICAL", "canonical JSON payload requires textual artifactId");
        }
        byte[] canonical = canonicalJson.canonicalize(document).copyToByteArray();
        if (!Arrays.equals(rawBytes, canonical)) {
            throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "payload bytes are not canonical JSON");
        }
        if ("MODULE_ARTIFACT_JSON".equals(policy.envelopeKind())) {
            ModuleArtifactEnvelope envelope =
                    ModuleArtifactEnvelope.parse(ImmutableBytes.copyOf(rawBytes), policy);
            if (!descriptor.artifactId().equals(envelope.reference().artifactId())
                    || !descriptor.sha256().equals(envelope.reference().sha256())) {
                throw new ArtifactStoreException(
                        "ARTIFACT_POLICY_MISMATCH", "module envelope reference differs from descriptor");
            }
        }
        ObjectNode withoutId = document.deepCopy();
        withoutId.remove("artifactId");
        String domain =
                "STANDALONE_JSON".equals(policy.envelopeKind())
                        ? "canonical-standalone-json-artifact-id-v1"
                        : "canonical-module-artifact-id-v1";
        return policy.artifactIdPrefix()
                + ":"
                + framedDigest(
                        domain,
                        descriptor.schemaVersion(),
                        descriptor.artifactType(),
                        canonicalJson.canonicalize(withoutId).copyToByteArray());
    }

    private void verifyCanonicalJsonl(byte[] rawBytes, boolean emptyAllowed) {
        if (rawBytes.length == 0) {
            if (!emptyAllowed) {
                throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "empty JSONL is not allowed");
            }
            return;
        }
        if (rawBytes[0] == (byte) 0xEF
                && rawBytes.length >= 3
                && rawBytes[1] == (byte) 0xBB
                && rawBytes[2] == (byte) 0xBF) {
            throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "JSONL must not have a UTF-8 BOM");
        }
        String content;
        try {
            content =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(rawBytes))
                            .toString();
        } catch (CharacterCodingException exception) {
            throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "JSONL is not strict UTF-8", exception);
        }
        if (!content.endsWith("\n") || content.indexOf('\r') >= 0) {
            throw new ArtifactStoreException(
                    "MODULE_PAYLOAD_NOT_CANONICAL", "JSONL must use LF and end with one LF");
        }
        String[] lines = content.substring(0, content.length() - 1).split("\\n", -1);
        for (String line : lines) {
            if (line.isEmpty()) {
                throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "JSONL must not contain blank lines");
            }
            ObjectNode object = parseCanonicalObject(line.getBytes(StandardCharsets.UTF_8));
            if (!Arrays.equals(
                    line.getBytes(StandardCharsets.UTF_8), canonicalJson.canonicalize(object).copyToByteArray())) {
                throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "JSONL line is not canonical JSON");
            }
        }
    }

    private PreparedReceipt prepareReceipt(
            ModuleInstallRequest request, List<ArtifactDescriptor> descriptors, String moduleArtifactRoot) {
        ObjectNode withoutId = JSON.createObjectNode();
        withoutId.put("schemaVersion", RECEIPT_SCHEMA);
        withoutId.set("address", addressNode(request.address()));
        withoutId.put("moduleVersion", request.moduleVersion());
        withoutId.set("upstreamArtifacts", referencesNode(request.upstreamArtifacts()));
        withoutId.set("controls", controlsNode(request.controls()));
        withoutId.put("status", request.status());
        withoutId.set("payloadArtifacts", descriptorsNode(descriptors));
        withoutId.put("moduleArtifactRoot", moduleArtifactRoot);
        withoutId.set("gapRefs", stringsNode(request.gapRefs()));
        String receiptId =
                "module-receipt:"
                        + framedDigest(
                                "canonical-module-receipt-id-v1",
                                canonicalJson.canonicalize(withoutId).copyToByteArray());
        ObjectNode complete = withoutId.deepCopy();
        complete.put("moduleReceiptId", receiptId);
        byte[] canonicalBytes = canonicalJson.canonicalize(complete).copyToByteArray();
        ModuleReceipt receipt =
                new ModuleReceipt(
                        RECEIPT_SCHEMA,
                        receiptId,
                        request.address(),
                        request.moduleVersion(),
                        request.upstreamArtifacts(),
                        request.controls(),
                        request.status(),
                        descriptors,
                        moduleArtifactRoot,
                        request.gapRefs());
        return new PreparedReceipt(receipt, ImmutableBytes.copyOf(canonicalBytes), sha256(canonicalBytes));
    }

    private ModuleReceipt parseReceipt(byte[] rawBytes) {
        ObjectNode document = parseCanonicalObject(rawBytes);
        if (!Arrays.equals(rawBytes, canonicalJson.canonicalize(document).copyToByteArray())) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "receipt bytes are not canonical JSON");
        }
        requireExactFields(
                document,
                List.of(
                        "address",
                        "controls",
                        "gapRefs",
                        "moduleArtifactRoot",
                        "moduleReceiptId",
                        "moduleVersion",
                        "payloadArtifacts",
                        "schemaVersion",
                        "status",
                        "upstreamArtifacts"),
                "module receipt");
        String schemaVersion = requiredText(document, "schemaVersion");
        if (!RECEIPT_SCHEMA.equals(schemaVersion)) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "unknown module receipt schema");
        }
        return new ModuleReceipt(
                schemaVersion,
                requiredText(document, "moduleReceiptId"),
                parseAddress(requiredObject(document, "address")),
                requiredText(document, "moduleVersion"),
                parseReferences(requiredArray(document, "upstreamArtifacts")),
                parseControls(requiredObject(document, "controls")),
                requiredText(document, "status"),
                parseDescriptors(requiredArray(document, "payloadArtifacts")),
                requiredText(document, "moduleArtifactRoot"),
                parseStrings(requiredArray(document, "gapRefs")));
    }

    private void verifyReceiptIdentity(
            byte[] receiptBytes, ModuleReceipt receipt, ModulePublicationReference reference) {
        ObjectNode receiptNode = parseCanonicalObject(receiptBytes);
        receiptNode.remove("moduleReceiptId");
        String expectedId =
                "module-receipt:"
                        + framedDigest(
                                "canonical-module-receipt-id-v1",
                                canonicalJson.canonicalize(receiptNode).copyToByteArray());
        if (!expectedId.equals(receipt.moduleReceiptId())
                || !receipt.moduleReceiptId().equals(reference.moduleReceiptId())
                || !sha256(receiptBytes).equals(reference.moduleReceiptSha256())) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "module receipt identity does not match");
        }
    }

    private void installAtomically(
            Path destination, List<VerifiedCanonicalPayload> payloads, ImmutableBytes receiptBytes) {
        try {
            Path parent = destination.getParent();
            Files.createDirectories(parent);
            requireDirectory(parent, "module parent directory");
            Path staging = Files.createTempDirectory(parent, ".module-staging-");
            for (VerifiedCanonicalPayload payload : payloads) {
                writeAndForce(staging.resolve(payload.descriptor().fileName()), payload.canonicalUtf8().copyToByteArray());
            }
            writeAndForce(staging.resolve(RECEIPT_FILE), receiptBytes.copyToByteArray());
            forceDirectory(staging);
            try {
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new ArtifactStoreException(
                        "ATOMIC_MOVE_UNSUPPORTED", "module publication requires an atomic directory move", exception);
            } catch (FileAlreadyExistsException exception) {
                throw new ArtifactStoreException(
                        "MODULE_PUBLICATION_COLLISION", "module address is already installed", exception);
            }
            forceDirectory(parent);
        } catch (ArtifactStoreException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "cannot atomically install module", exception);
        }
    }

    private Path moduleDirectory(ModulePublicationAddress address) {
        Path root = runStore.root();
        if (address instanceof StageModuleAddress stage) {
            return root.resolve("runs")
                    .resolve(stage.runId())
                    .resolve("stages")
                    .resolve(String.format("%02d-%s", stage.stageNumber(), stage.stageKey()))
                    .resolve("modules")
                    .resolve(String.format("%02d-%s", stage.moduleNumber(), stage.moduleKey()));
        }
        ValidationModuleAddress validation = (ValidationModuleAddress) address;
        return root.resolve("validations")
                .resolve(validation.validationId())
                .resolve("modules")
                .resolve("01-run-validator");
    }

    private void verifyRegistryReference(ArtifactControls controls) {
        if (!artifactPolicies.reference().equals(controls.artifactPolicyRegistryRef())) {
            throw new ArtifactStoreException(
                    "ARTIFACT_POLICY_MISMATCH", "artifact policy registry reference does not match store");
        }
    }

    private static void writeAndForce(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static void requireDirectory(Path directory, String field) {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", field + " must be a non-symlink directory");
        }
    }

    private static byte[] readRegularFile(Path path) {
        try {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new ArtifactStoreException(
                        "MODULE_PUBLICATION_INVALID", "publication contains a missing or non-regular file");
            }
            return Files.readAllBytes(path);
        } catch (ArtifactStoreException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "cannot read publication file", exception);
        }
    }

    private static void requireExactDirectoryContents(Path directory, List<ArtifactDescriptor> descriptors) {
        try (var paths = Files.list(directory)) {
            List<String> observed = paths.map(path -> path.getFileName().toString()).sorted().toList();
            List<String> expected = new ArrayList<>();
            expected.add(RECEIPT_FILE);
            descriptors.stream().map(ArtifactDescriptor::fileName).sorted().forEach(expected::add);
            expected.sort(String::compareTo);
            if (!observed.equals(expected)) {
                throw new ArtifactStoreException(
                        "MODULE_PUBLICATION_INVALID", "publication directory has unexpected or missing files");
            }
        } catch (IOException exception) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "cannot enumerate publication directory", exception);
        }
    }

    private ObjectNode parseCanonicalObject(byte[] bytes) {
        try {
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.isObject()) {
                throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "canonical JSON must be an object");
            }
            return (ObjectNode) node;
        } catch (ArtifactStoreException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "cannot parse canonical JSON", exception);
        }
    }

    private static void requireExactFields(ObjectNode node, List<String> expected, String subject) {
        List<String> observed = new ArrayList<>();
        node.fieldNames().forEachRemaining(observed::add);
        if (!observed.containsAll(expected) || observed.size() != expected.size()) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_INVALID", subject + " fields do not match the exact schema");
        }
    }

    private static String requiredText(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", field + " must be textual");
        }
        return value.textValue();
    }

    private static ObjectNode requiredObject(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isObject()) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", field + " must be an object");
        }
        return (ObjectNode) value;
    }

    private static ArrayNode requiredArray(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", field + " must be an array");
        }
        return (ArrayNode) value;
    }

    private static ObjectNode addressNode(ModulePublicationAddress address) {
        ObjectNode node = JSON.createObjectNode();
        if (address instanceof StageModuleAddress stage) {
            node.put("kind", "STAGE");
            node.put("runId", stage.runId());
            node.put("stageNumber", stage.stageNumber());
            node.put("stageKey", stage.stageKey());
            node.put("moduleNumber", stage.moduleNumber());
            node.put("moduleKey", stage.moduleKey());
        } else {
            ValidationModuleAddress validation = (ValidationModuleAddress) address;
            node.put("kind", "VALIDATION");
            node.put("runId", validation.runId());
            node.put("validationId", validation.validationId());
            node.put("moduleNumber", validation.moduleNumber());
            node.put("moduleKey", validation.moduleKey());
        }
        return node;
    }

    private static ModulePublicationAddress parseAddress(ObjectNode node) {
        String kind = requiredText(node, "kind");
        if ("STAGE".equals(kind)) {
            requireExactFields(
                    node,
                    List.of("kind", "moduleKey", "moduleNumber", "runId", "stageKey", "stageNumber"),
                    "stage module address");
            return new StageModuleAddress(
                    requiredText(node, "runId"),
                    node.get("stageNumber").intValue(),
                    requiredText(node, "stageKey"),
                    node.get("moduleNumber").intValue(),
                    requiredText(node, "moduleKey"));
        }
        if ("VALIDATION".equals(kind)) {
            requireExactFields(
                    node,
                    List.of("kind", "moduleKey", "moduleNumber", "runId", "validationId"),
                    "validation module address");
            return new ValidationModuleAddress(
                    requiredText(node, "runId"),
                    requiredText(node, "validationId"),
                    node.get("moduleNumber").intValue(),
                    requiredText(node, "moduleKey"));
        }
        throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "unknown module address kind");
    }

    private static ArrayNode referencesNode(List<ArtifactReference> references) {
        ArrayNode array = JSON.createArrayNode();
        for (ArtifactReference reference : references) {
            ObjectNode node = array.addObject();
            node.put("artifactId", reference.artifactId());
            node.put("sha256", reference.sha256());
        }
        return array;
    }

    private static List<ArtifactReference> parseReferences(ArrayNode array) {
        List<ArtifactReference> references = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isObject()) {
                throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "artifact reference must be an object");
            }
            ObjectNode value = (ObjectNode) node;
            requireExactFields(value, List.of("artifactId", "sha256"), "artifact reference");
            references.add(new ArtifactReference(requiredText(value, "artifactId"), requiredText(value, "sha256")));
        }
        return List.copyOf(references);
    }

    private static ObjectNode controlsNode(ArtifactControls controls) {
        ObjectNode node = JSON.createObjectNode();
        node.put("toolchainSha256", controls.toolchainSha256());
        node.put("profileSha256", controls.profileSha256());
        node.put("schemaBundleSha256", controls.schemaBundleSha256());
        if (controls.promptBundleSha256() == null) {
            node.putNull("promptBundleSha256");
        } else {
            node.put("promptBundleSha256", controls.promptBundleSha256());
        }
        ObjectNode policyReference = node.putObject("artifactPolicyRegistryRef");
        policyReference.put("artifactId", controls.artifactPolicyRegistryRef().artifactId());
        policyReference.put("sha256", controls.artifactPolicyRegistryRef().sha256());
        return node;
    }

    private static ArtifactControls parseControls(ObjectNode node) {
        requireExactFields(
                node,
                List.of(
                        "artifactPolicyRegistryRef",
                        "profileSha256",
                        "promptBundleSha256",
                        "schemaBundleSha256",
                        "toolchainSha256"),
                "artifact controls");
        ObjectNode policyReference = requiredObject(node, "artifactPolicyRegistryRef");
        requireExactFields(policyReference, List.of("artifactId", "sha256"), "policy registry reference");
        JsonNode prompt = node.get("promptBundleSha256");
        if (prompt == null || !(prompt.isNull() || prompt.isTextual())) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_INVALID", "promptBundleSha256 must be null or textual");
        }
        return new ArtifactControls(
                requiredText(node, "toolchainSha256"),
                requiredText(node, "profileSha256"),
                requiredText(node, "schemaBundleSha256"),
                prompt.isNull() ? null : prompt.textValue(),
                new ArtifactPolicyRegistryReference(
                        requiredText(policyReference, "artifactId"), requiredText(policyReference, "sha256")));
    }

    private static ArrayNode descriptorsNode(List<ArtifactDescriptor> descriptors) {
        ArrayNode array = JSON.createArrayNode();
        for (ArtifactDescriptor descriptor : descriptors) {
            ObjectNode node = array.addObject();
            node.put("fileName", descriptor.fileName());
            node.put("artifactType", descriptor.artifactType());
            node.put("schemaVersion", descriptor.schemaVersion());
            node.put("artifactId", descriptor.artifactId());
            node.put("mediaType", descriptor.mediaType());
            node.put("sizeBytes", descriptor.sizeBytes());
            node.put("sha256", descriptor.sha256());
        }
        return array;
    }

    private static List<ArtifactDescriptor> parseDescriptors(ArrayNode array) {
        List<ArtifactDescriptor> descriptors = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isObject()) {
                throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "descriptor must be an object");
            }
            ObjectNode value = (ObjectNode) node;
            requireExactFields(
                    value,
                    List.of(
                            "artifactId",
                            "artifactType",
                            "fileName",
                            "mediaType",
                            "schemaVersion",
                            "sha256",
                            "sizeBytes"),
                    "artifact descriptor");
            JsonNode size = value.get("sizeBytes");
            if (size == null || !size.canConvertToLong()) {
                throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "sizeBytes must be a long");
            }
            descriptors.add(
                    new ArtifactDescriptor(
                            requiredText(value, "fileName"),
                            requiredText(value, "artifactType"),
                            requiredText(value, "schemaVersion"),
                            requiredText(value, "artifactId"),
                            requiredText(value, "mediaType"),
                            size.longValue(),
                            requiredText(value, "sha256")));
        }
        descriptors.sort(BY_FILE_NAME);
        return List.copyOf(descriptors);
    }

    private static ArrayNode stringsNode(List<String> values) {
        ArrayNode array = JSON.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private static List<String> parseStrings(ArrayNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isTextual()) {
                throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "gap reference must be textual");
            }
            values.add(node.textValue());
        }
        ArtifactValues.sortedUnique(values, "gapRefs");
        return List.copyOf(values);
    }

    private static String descriptorListRoot(
            String domain, String prefix, List<ArtifactDescriptor> descriptors) {
        List<ArtifactDescriptor> sorted = descriptors.stream().sorted(BY_FILE_NAME).toList();
        byte[] count = new byte[Integer.BYTES];
        int size = sorted.size();
        for (int offset = Integer.BYTES - 1; offset >= 0; offset--) {
            count[offset] = (byte) size;
            size >>>= Byte.SIZE;
        }
        List<byte[]> parts = new ArrayList<>();
        parts.add(frame(domain.getBytes(StandardCharsets.UTF_8)));
        parts.add(count);
        for (ArtifactDescriptor descriptor : sorted) {
            parts.add(frame(descriptorBytes(descriptor)));
        }
        return prefix + ":" + sha256(parts.toArray(byte[][]::new));
    }

    private static byte[] descriptorBytes(ArtifactDescriptor descriptor) {
        List<byte[]> parts =
                List.of(
                        frame(descriptor.fileName().getBytes(StandardCharsets.UTF_8)),
                        frame(descriptor.artifactType().getBytes(StandardCharsets.UTF_8)),
                        frame(descriptor.schemaVersion().getBytes(StandardCharsets.UTF_8)),
                        frame(descriptor.artifactId().getBytes(StandardCharsets.UTF_8)),
                        frame(descriptor.mediaType().getBytes(StandardCharsets.UTF_8)),
                        longBytes(descriptor.sizeBytes()),
                        HexFormat.of().parseHex(descriptor.sha256()));
        int length = parts.stream().mapToInt(part -> part.length).sum();
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static String framedDigest(String domain, Object... parts) {
        List<byte[]> frames = new ArrayList<>();
        frames.add(frame(domain.getBytes(StandardCharsets.UTF_8)));
        for (Object part : parts) {
            byte[] bytes = part instanceof String text ? text.getBytes(StandardCharsets.UTF_8) : (byte[]) part;
            frames.add(frame(bytes));
        }
        return sha256(frames.toArray(byte[][]::new));
    }

    private static byte[] frame(byte[] bytes) {
        byte[] framed = new byte[Long.BYTES + bytes.length];
        long length = bytes.length;
        for (int offset = Long.BYTES - 1; offset >= 0; offset--) {
            framed[offset] = (byte) length;
            length >>>= Byte.SIZE;
        }
        System.arraycopy(bytes, 0, framed, Long.BYTES, bytes.length);
        return framed;
    }

    private static byte[] longBytes(long value) {
        byte[] bytes = new byte[Long.BYTES];
        for (int offset = Long.BYTES - 1; offset >= 0; offset--) {
            bytes[offset] = (byte) value;
            value >>>= Byte.SIZE;
        }
        return bytes;
    }

    private static String sha256(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record PreparedReceipt(ModuleReceipt receipt, ImmutableBytes canonicalUtf8, String sha256) {}
}
