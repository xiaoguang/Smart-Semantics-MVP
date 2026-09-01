package com.linguan.codemd.target.artifacts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Filesystem implementation of the receipt-last Stage01–07 public publication boundary. */
public final class FileSystemCanonicalStageArtifactStore implements CanonicalStageArtifactStore {
    private static final ObjectMapper JSON =
            new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final String RECEIPT_FILE = "stage-receipt.json";
    private static final String RECEIPT_SCHEMA = "stage-receipt-v1";
    private static final Comparator<ArtifactDescriptor> BY_FILE_NAME =
            Comparator.comparing(ArtifactDescriptor::fileName);

    private final FileSystemRunStoreHandle runStore;
    private final CanonicalJsonCodec canonicalJson;
    private final CanonicalArtifactPolicyRegistry artifactPolicies;
    private final ArtifactStoreLimits limits;
    private final CanonicalModuleArtifactStore moduleStore;

    public FileSystemCanonicalStageArtifactStore(
            RunStoreHandle runStore,
            CanonicalJsonCodec canonicalJson,
            CanonicalArtifactPolicyRegistry artifactPolicies,
            ArtifactStoreLimits limits) {
        if (!(runStore instanceof FileSystemRunStoreHandle fileSystemRunStore)
                || canonicalJson == null
                || artifactPolicies == null
                || limits == null) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "store constructor arguments are invalid");
        }
        this.runStore = fileSystemRunStore;
        this.canonicalJson = canonicalJson;
        this.artifactPolicies = artifactPolicies;
        this.limits = limits;
        this.moduleStore =
                new FileSystemCanonicalModuleArtifactStore(
                        runStore, canonicalJson, artifactPolicies, limits);
    }

    @Override
    public synchronized InstalledStagePublication install(StageInstallRequest request) {
        if (request == null) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_REQUEST_INVALID", "request is required");
        }
        verifyRegistryReference(request.controls());
        List<VerifiedCanonicalPayload> payloads = verifyPayloads(request.semanticPayloads());
        List<ArtifactDescriptor> descriptors =
                payloads.stream().map(VerifiedCanonicalPayload::descriptor).sorted(BY_FILE_NAME).toList();
        StageSemanticSchemaRegistry.requireExact(request.address(), descriptors);
        verifyProvenance(request, payloads, descriptors);
        String root = descriptorListRoot("canonical-stage-artifact-root-v1", "stage-root", descriptors);
        PreparedReceipt prepared = prepareReceipt(request, descriptors, root);
        StagePublicationReference reference =
                new StagePublicationReference(
                        request.address(), root, prepared.receipt().stageReceiptId(), prepared.sha256());
        Path directory = stageDirectory(request.address());
        requireDirectory(directory, "stage directory");
        Path receiptPath = directory.resolve(RECEIPT_FILE);
        if (Files.exists(receiptPath, LinkOption.NOFOLLOW_LINKS)) {
            ReopenedStagePublication existing = reopen(reference);
            if (existing.reference().equals(reference)) {
                return new InstalledStagePublication(reference, "ALREADY_INSTALLED", descriptors, null);
            }
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_COLLISION", "stage address is already bound to different bytes");
        }
        rejectPartialPublicPublication(directory);
        installReceiptLast(directory, payloads, prepared.canonicalUtf8());
        reopen(reference);
        return new InstalledStagePublication(reference, "INSTALLED", descriptors, null);
    }

    @Override
    public synchronized ReopenedStagePublication reopen(StagePublicationReference reference) {
        if (reference == null) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "reference is required");
        }
        Path directory = stageDirectory(reference.address());
        requireDirectory(directory, "stage directory");
        byte[] receiptBytes = readRegularFile(directory.resolve(RECEIPT_FILE));
        StageReceipt receipt = parseReceipt(receiptBytes);
        verifyReceiptIdentity(receiptBytes, receipt, reference);
        StagePublicationAddress receiptAddress =
                new StagePublicationAddress(receipt.runId(), receipt.stageNumber(), receipt.stageKey());
        if (!reference.address().equals(receiptAddress)
                || !reference.stageArtifactRoot().equals(receipt.stageArtifactRoot())) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", "receipt and requested stage reference differ");
        }
        verifyRegistryReference(receipt.controls());
        StageSemanticSchemaRegistry.requireExact(receiptAddress, receipt.semanticArtifacts());
        requireExactPublicContents(directory, receipt.semanticArtifacts(), receipt.archiveManifest());
        List<VerifiedCanonicalPayload> payloads = new ArrayList<>();
        for (ArtifactDescriptor descriptor : receipt.semanticArtifacts()) {
            byte[] bytes = readRegularFile(directory.resolve(descriptor.fileName()));
            if (descriptor.sizeBytes() != bytes.length || !descriptor.sha256().equals(sha256(bytes))) {
                throw new ArtifactStoreException(
                        "STAGE_PUBLICATION_INVALID", "semantic descriptor does not match stored bytes");
            }
            payloads.add(verifyPayload(descriptor, ImmutableBytes.copyOf(bytes)));
        }
        payloads.sort(Comparator.comparing(payload -> payload.descriptor().fileName()));
        String recomputedRoot =
                descriptorListRoot(
                        "canonical-stage-artifact-root-v1",
                        "stage-root",
                        payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList());
        if (!reference.stageArtifactRoot().equals(recomputedRoot)) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "stage artifact root does not match");
        }
        return new ReopenedStagePublication(reference, receipt, List.copyOf(payloads), null);
    }

    private void verifyProvenance(
            StageInstallRequest request,
            List<VerifiedCanonicalPayload> payloads,
            List<ArtifactDescriptor> descriptors) {
        if (request.address().stageNumber() == 8) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_SCHEMA_UNAVAILABLE", "Stage08 archive publication is not implemented");
        }
        StagePublisherModuleProvenance provenance =
                (StagePublisherModuleProvenance) request.publicationProvenance();
        ModulePublicationReference publisherReference = provenance.publisherSpecificationModuleReference();
        int publisherModuleNumber =
                StageDefinitions.publisherModuleNumber(request.address().stageNumber(), request.address().stageKey());
        StageModuleAddress expectedAddress =
                new StageModuleAddress(
                        request.address().runId(),
                        request.address().stageNumber(),
                        request.address().stageKey(),
                        publisherModuleNumber,
                        "publish");
        if (!expectedAddress.equals(publisherReference.address())) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", "publisher module reference does not match stage publication slot");
        }
        ReopenedModulePublication publisher = moduleStore.reopen(publisherReference);
        List<ArtifactDescriptor> publisherDescriptors =
                publisher.payloads().stream().map(VerifiedCanonicalPayload::descriptor).sorted(BY_FILE_NAME).toList();
        if (!publisherDescriptors.equals(descriptors)) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", "publisher descriptors differ from requested semantic set");
        }
        for (int index = 0; index < payloads.size(); index++) {
            if (!Arrays.equals(
                    payloads.get(index).canonicalUtf8().copyToByteArray(),
                    publisher.payloads().get(index).canonicalUtf8().copyToByteArray())) {
                throw new ArtifactStoreException(
                        "STAGE_PUBLICATION_INVALID", "publisher payload bytes differ from requested semantic set");
            }
        }
    }

    private List<VerifiedCanonicalPayload> verifyPayloads(List<CanonicalStagePayload> sourcePayloads) {
        if (sourcePayloads.size() > limits.maxPayloadFiles()) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "semantic payload count exceeds store limit");
        }
        List<VerifiedCanonicalPayload> payloads = new ArrayList<>();
        for (CanonicalStagePayload payload : sourcePayloads) {
            byte[] bytes = payload.canonicalUtf8().copyToByteArray();
            payloads.add(
                    verifyPayload(
                            new ArtifactDescriptor(
                                    payload.fileName(),
                                    payload.artifactType(),
                                    payload.schemaVersion(),
                                    payload.artifactId(),
                                    payload.mediaType(),
                                    bytes.length,
                                    sha256(bytes)),
                            payload.canonicalUtf8()));
        }
        payloads.sort(Comparator.comparing(payload -> payload.descriptor().fileName()));
        long size = payloads.stream().mapToLong(payload -> payload.canonicalUtf8().size()).sum();
        if (size > limits.maxPublicationBytes()) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "stage publication exceeds store limit");
        }
        for (int index = 1; index < payloads.size(); index++) {
            if (payloads.get(index - 1)
                    .descriptor()
                    .fileName()
                    .equals(payloads.get(index).descriptor().fileName())) {
                throw new ArtifactStoreException(
                        "STAGE_PUBLICATION_REQUEST_INVALID", "semantic filenames must be unique");
            }
        }
        return List.copyOf(payloads);
    }

    private VerifiedCanonicalPayload verifyPayload(ArtifactDescriptor descriptor, ImmutableBytes bytes) {
        if (bytes.size() > limits.maxArtifactBytes()) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "semantic artifact exceeds store limit");
        }
        CanonicalArtifactPolicy policy =
                artifactPolicies.resolve(new ArtifactPolicyKey(descriptor.artifactType(), descriptor.schemaVersion()));
        if (!policy.mediaType().equals(descriptor.mediaType())) {
            throw new ArtifactStoreException("ARTIFACT_POLICY_MISMATCH", "media type differs from policy");
        }
        byte[] raw = bytes.copyToByteArray();
        String expectedId;
        switch (policy.envelopeKind()) {
            case "STANDALONE_JSON", "MODULE_ARTIFACT_JSON" -> {
                ObjectNode document = parseCanonicalObject(raw);
                if (!document.has("artifactId") || !document.get("artifactId").isTextual()) {
                    throw new ArtifactStoreException(
                            "STAGE_PUBLICATION_INVALID", "JSON semantic payload requires textual artifactId");
                }
                if (!Arrays.equals(raw, canonicalJson.canonicalize(document).copyToByteArray())) {
                    throw new ArtifactStoreException(
                            "STAGE_PUBLICATION_INVALID", "JSON semantic payload is not canonical");
                }
                ObjectNode withoutId = document.deepCopy();
                withoutId.remove("artifactId");
                String domain =
                        "STANDALONE_JSON".equals(policy.envelopeKind())
                                ? "canonical-standalone-json-artifact-id-v1"
                                : "canonical-module-artifact-id-v1";
                expectedId =
                        policy.artifactIdPrefix()
                                + ":"
                                + framedDigest(
                                        domain,
                                        descriptor.schemaVersion(),
                                        descriptor.artifactType(),
                                        canonicalJson.canonicalize(withoutId).copyToByteArray());
            }
            case "CANONICAL_JSONL" -> {
                verifyCanonicalJsonl(raw, policy.emptyJsonlAllowed());
                expectedId =
                        policy.artifactIdPrefix()
                                + ":"
                                + framedDigest(
                                        "canonical-jsonl-artifact-id-v1",
                                        descriptor.schemaVersion(),
                                        descriptor.artifactType(),
                                        raw);
            }
            default -> throw new ArtifactStoreException(
                    "ARTIFACT_POLICY_MISMATCH", "Stage01–07 does not accept RAW_UTF8 semantic payloads");
        }
        if (!expectedId.equals(descriptor.artifactId())) {
            throw new ArtifactStoreException(
                    "ARTIFACT_POLICY_MISMATCH", "artifact ID differs from canonical bytes");
        }
        return new VerifiedCanonicalPayload(descriptor, bytes);
    }

    private PreparedReceipt prepareReceipt(
            StageInstallRequest request, List<ArtifactDescriptor> descriptors, String root) {
        ObjectNode withoutId = JSON.createObjectNode();
        withoutId.put("schemaVersion", RECEIPT_SCHEMA);
        withoutId.put("stageArtifactRoot", root);
        withoutId.put("runId", request.address().runId());
        withoutId.put("stageNumber", request.address().stageNumber());
        withoutId.put("stageKey", request.address().stageKey());
        withoutId.set("publicationProvenance", provenanceNode(request.publicationProvenance()));
        withoutId.set("upstreamStageReferences", stageReferencesNode(request.upstreamStageReferences()));
        withoutId.put("status", request.status());
        withoutId.set("controls", controlsNode(request.controls()));
        withoutId.set("semanticArtifacts", descriptorsNode(descriptors));
        withoutId.putNull("archiveManifest");
        withoutId.put("gapCount", request.gapRefs().size());
        withoutId.set("gapRefs", stringsNode(request.gapRefs()));
        String receiptId =
                "stage-receipt:"
                        + framedDigest(
                                "canonical-stage-receipt-id-v1",
                                canonicalJson.canonicalize(withoutId).copyToByteArray());
        ObjectNode complete = withoutId.deepCopy();
        complete.put("stageReceiptId", receiptId);
        byte[] bytes = canonicalJson.canonicalize(complete).copyToByteArray();
        return new PreparedReceipt(
                new StageReceipt(
                        RECEIPT_SCHEMA,
                        receiptId,
                        root,
                        request.address().runId(),
                        request.address().stageNumber(),
                        request.address().stageKey(),
                        request.publicationProvenance(),
                        request.upstreamStageReferences(),
                        request.status(),
                        request.controls(),
                        descriptors,
                        null,
                        request.gapRefs().size(),
                        request.gapRefs()),
                ImmutableBytes.copyOf(bytes),
                sha256(bytes));
    }

    private StageReceipt parseReceipt(byte[] raw) {
        ObjectNode document = parseCanonicalObject(raw);
        if (!Arrays.equals(raw, canonicalJson.canonicalize(document).copyToByteArray())) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "receipt is not canonical JSON");
        }
        requireExactFields(
                document,
                List.of(
                        "archiveManifest",
                        "controls",
                        "gapCount",
                        "gapRefs",
                        "publicationProvenance",
                        "schemaVersion",
                        "semanticArtifacts",
                        "stageKey",
                        "stageNumber",
                        "stageArtifactRoot",
                        "stageReceiptId",
                        "status",
                        "upstreamStageReferences",
                        "runId"),
                "stage receipt");
        if (!RECEIPT_SCHEMA.equals(requiredText(document, "schemaVersion"))) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "unknown stage receipt schema");
        }
        JsonNode archive = document.get("archiveManifest");
        if (archive == null || !archive.isNull()) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "Stage01–07 receipt archiveManifest must be null");
        }
        JsonNode gapCount = document.get("gapCount");
        if (gapCount == null || !gapCount.canConvertToInt()) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "gapCount must be int");
        }
        List<String> gapRefs = parseStrings(requiredArray(document, "gapRefs"));
        if (gapCount.intValue() != gapRefs.size()) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "gapCount does not match gapRefs");
        }
        return new StageReceipt(
                RECEIPT_SCHEMA,
                requiredText(document, "stageReceiptId"),
                requiredText(document, "stageArtifactRoot"),
                requiredText(document, "runId"),
                requiredInt(document, "stageNumber"),
                requiredText(document, "stageKey"),
                parseProvenance(requiredObject(document, "publicationProvenance")),
                parseStageReferences(requiredArray(document, "upstreamStageReferences")),
                requiredText(document, "status"),
                parseControls(requiredObject(document, "controls")),
                parseDescriptors(requiredArray(document, "semanticArtifacts")),
                null,
                gapRefs.size(),
                gapRefs);
    }

    private void verifyReceiptIdentity(
            byte[] receiptBytes, StageReceipt receipt, StagePublicationReference reference) {
        ObjectNode withoutId = parseCanonicalObject(receiptBytes);
        withoutId.remove("stageReceiptId");
        String expectedId =
                "stage-receipt:"
                        + framedDigest(
                                "canonical-stage-receipt-id-v1",
                                canonicalJson.canonicalize(withoutId).copyToByteArray());
        if (!expectedId.equals(receipt.stageReceiptId())
                || !receipt.stageReceiptId().equals(reference.stageReceiptId())
                || !sha256(receiptBytes).equals(reference.stageReceiptSha256())) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "stage receipt identity does not match");
        }
    }

    private void installReceiptLast(
            Path directory, List<VerifiedCanonicalPayload> payloads, ImmutableBytes receiptBytes) {
        Path staging = null;
        try {
            Path parent = directory.getParent();
            staging = Files.createTempDirectory(parent, ".stage-staging-");
            for (VerifiedCanonicalPayload payload : payloads) {
                writeAndForce(
                        staging.resolve(payload.descriptor().fileName()),
                        payload.canonicalUtf8().copyToByteArray());
            }
            writeAndForce(staging.resolve(RECEIPT_FILE), receiptBytes.copyToByteArray());
            forceDirectory(staging);
            for (VerifiedCanonicalPayload payload : payloads) {
                moveAtomically(
                        staging.resolve(payload.descriptor().fileName()),
                        directory.resolve(payload.descriptor().fileName()));
            }
            moveAtomically(staging.resolve(RECEIPT_FILE), directory.resolve(RECEIPT_FILE));
            forceDirectory(directory);
        } catch (ArtifactStoreException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", "cannot install stage semantic publication", exception);
        } finally {
            if (staging != null) {
                deleteQuietly(staging);
            }
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new ArtifactStoreException(
                    "ATOMIC_MOVE_UNSUPPORTED", "stage publication requires atomic file moves", exception);
        }
    }

    private void rejectPartialPublicPublication(Path directory) {
        try (var paths = Files.list(directory)) {
            boolean hasPublic =
                    paths.anyMatch(path -> !"modules".equals(path.getFileName().toString()));
            if (hasPublic) {
                throw new ArtifactStoreException(
                        "STAGE_PUBLICATION_COLLISION", "stage directory has partial or foreign public files");
            }
        } catch (IOException exception) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", "cannot inspect stage directory", exception);
        }
    }

    private void requireExactPublicContents(
            Path directory, List<ArtifactDescriptor> descriptors, ArtifactDescriptor archiveManifest) {
        try (var paths = Files.list(directory)) {
            List<String> observed = paths.map(path -> path.getFileName().toString()).sorted().toList();
            List<String> expected = new ArrayList<>();
            expected.add("modules");
            expected.add(RECEIPT_FILE);
            descriptors.stream().map(ArtifactDescriptor::fileName).forEach(expected::add);
            if (archiveManifest != null) {
                expected.add(archiveManifest.fileName());
            }
            expected.sort(String::compareTo);
            if (!observed.equals(expected)) {
                throw new ArtifactStoreException(
                        "STAGE_PUBLICATION_INVALID", "stage directory has unexpected or missing public files");
            }
        } catch (IOException exception) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", "cannot enumerate stage directory", exception);
        }
    }

    private Path stageDirectory(StagePublicationAddress address) {
        return runStore.root()
                .resolve("runs")
                .resolve(address.runId())
                .resolve("stages")
                .resolve(String.format("%02d-%s", address.stageNumber(), address.stageKey()));
    }

    private void verifyRegistryReference(ArtifactControls controls) {
        if (!artifactPolicies.reference().equals(controls.artifactPolicyRegistryRef())) {
            throw new ArtifactStoreException(
                    "ARTIFACT_POLICY_MISMATCH", "policy registry reference does not match store");
        }
    }

    private void verifyCanonicalJsonl(byte[] bytes, boolean emptyAllowed) {
        if (bytes.length == 0) {
            if (!emptyAllowed) {
                throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "empty JSONL is not allowed");
            }
            return;
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "JSONL must not have a BOM");
        }
        String content;
        try {
            content =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (CharacterCodingException exception) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "JSONL is not strict UTF-8", exception);
        }
        if (!content.endsWith("\n") || content.indexOf('\r') >= 0) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "JSONL must use final-LF only");
        }
        for (String line : content.substring(0, content.length() - 1).split("\\n", -1)) {
            if (line.isEmpty()) {
                throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "JSONL must not contain blank lines");
            }
            ObjectNode object = parseCanonicalObject(line.getBytes(StandardCharsets.UTF_8));
            if (!Arrays.equals(
                    line.getBytes(StandardCharsets.UTF_8), canonicalJson.canonicalize(object).copyToByteArray())) {
                throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "JSONL line is not canonical JSON");
            }
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

    private static void deleteQuietly(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(
                    path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // A residue is never an installed publication and will be rejected on the next install.
                        }
                    });
        } catch (IOException ignored) {
            // A residue is never an installed publication and will be rejected on the next install.
        }
    }

    private static void requireDirectory(Path directory, String field) {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", field + " must be a non-symlink directory");
        }
    }

    private static byte[] readRegularFile(Path path) {
        try {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new ArtifactStoreException(
                        "STAGE_PUBLICATION_INVALID", "publication contains a missing or non-regular file");
            }
            return Files.readAllBytes(path);
        } catch (ArtifactStoreException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", "cannot read stage publication file", exception);
        }
    }

    private ObjectNode parseCanonicalObject(byte[] bytes) {
        try {
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.isObject()) {
                throw new ArtifactStoreException(
                        "STAGE_PUBLICATION_INVALID", "canonical JSON must be an object");
            }
            return (ObjectNode) node;
        } catch (ArtifactStoreException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", "cannot parse canonical JSON", exception);
        }
    }

    private static ObjectNode addressNode(StagePublicationAddress address) {
        ObjectNode node = JSON.createObjectNode();
        node.put("runId", address.runId());
        node.put("stageNumber", address.stageNumber());
        node.put("stageKey", address.stageKey());
        return node;
    }

    private static StagePublicationAddress parseAddress(ObjectNode node) {
        requireExactFields(node, List.of("runId", "stageKey", "stageNumber"), "stage address");
        JsonNode stageNumber = node.get("stageNumber");
        if (stageNumber == null || !stageNumber.canConvertToInt()) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "stageNumber must be int");
        }
        return new StagePublicationAddress(
                requiredText(node, "runId"), stageNumber.intValue(), requiredText(node, "stageKey"));
    }

    private static ObjectNode provenanceNode(StagePublicationProvenance provenance) {
        if (!(provenance instanceof StagePublisherModuleProvenance publisher)) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_SCHEMA_UNAVAILABLE", "Stage08 provenance serialization is not implemented");
        }
        ObjectNode node = JSON.createObjectNode();
        node.put("kind", "STAGE_PUBLISHER_MODULE");
        node.set(
                "publisherSpecificationModuleReference",
                moduleReferenceNode(publisher.publisherSpecificationModuleReference()));
        return node;
    }

    private static StagePublicationProvenance parseProvenance(ObjectNode node) {
        requireExactFields(
                node,
                List.of("kind", "publisherSpecificationModuleReference"),
                "stage publisher provenance");
        if (!"STAGE_PUBLISHER_MODULE".equals(requiredText(node, "kind"))) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "unknown stage provenance kind");
        }
        return new StagePublisherModuleProvenance(
                parseModuleReference(requiredObject(node, "publisherSpecificationModuleReference")));
    }

    private static ObjectNode moduleReferenceNode(ModulePublicationReference reference) {
        ObjectNode node = JSON.createObjectNode();
        node.set("address", moduleAddressNode(reference.address()));
        node.put("moduleArtifactRoot", reference.moduleArtifactRoot());
        node.put("moduleReceiptId", reference.moduleReceiptId());
        node.put("moduleReceiptSha256", reference.moduleReceiptSha256());
        return node;
    }

    private static ModulePublicationReference parseModuleReference(ObjectNode node) {
        requireExactFields(
                node,
                List.of("address", "moduleArtifactRoot", "moduleReceiptId", "moduleReceiptSha256"),
                "module publication reference");
        return new ModulePublicationReference(
                parseModuleAddress(requiredObject(node, "address")),
                requiredText(node, "moduleArtifactRoot"),
                requiredText(node, "moduleReceiptId"),
                requiredText(node, "moduleReceiptSha256"));
    }

    private static ObjectNode moduleAddressNode(ModulePublicationAddress address) {
        if (!(address instanceof StageModuleAddress stage)) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "publisher must be a stage module");
        }
        ObjectNode node = JSON.createObjectNode();
        node.put("kind", "STAGE");
        node.put("runId", stage.runId());
        node.put("stageNumber", stage.stageNumber());
        node.put("stageKey", stage.stageKey());
        node.put("moduleNumber", stage.moduleNumber());
        node.put("moduleKey", stage.moduleKey());
        return node;
    }

    private static ModulePublicationAddress parseModuleAddress(ObjectNode node) {
        requireExactFields(
                node,
                List.of("kind", "moduleKey", "moduleNumber", "runId", "stageKey", "stageNumber"),
                "publisher module address");
        if (!"STAGE".equals(requiredText(node, "kind"))) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "publisher module must be STAGE");
        }
        JsonNode stageNumber = node.get("stageNumber");
        JsonNode moduleNumber = node.get("moduleNumber");
        if (stageNumber == null
                || moduleNumber == null
                || !stageNumber.canConvertToInt()
                || !moduleNumber.canConvertToInt()) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "publisher module numbers must be int");
        }
        return new StageModuleAddress(
                requiredText(node, "runId"),
                stageNumber.intValue(),
                requiredText(node, "stageKey"),
                moduleNumber.intValue(),
                requiredText(node, "moduleKey"));
    }

    private static ArrayNode stageReferencesNode(List<StagePublicationReference> references) {
        ArrayNode array = JSON.createArrayNode();
        for (StagePublicationReference reference : references) {
            ObjectNode node = array.addObject();
            node.set("address", addressNode(reference.address()));
            node.put("stageArtifactRoot", reference.stageArtifactRoot());
            node.put("stageReceiptId", reference.stageReceiptId());
            node.put("stageReceiptSha256", reference.stageReceiptSha256());
        }
        return array;
    }

    private static List<StagePublicationReference> parseStageReferences(ArrayNode array) {
        List<StagePublicationReference> references = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isObject()) {
                throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "stage reference must be object");
            }
            ObjectNode value = (ObjectNode) node;
            requireExactFields(
                    value,
                    List.of("address", "stageArtifactRoot", "stageReceiptId", "stageReceiptSha256"),
                    "stage publication reference");
            references.add(
                    new StagePublicationReference(
                            parseAddress(requiredObject(value, "address")),
                            requiredText(value, "stageArtifactRoot"),
                            requiredText(value, "stageReceiptId"),
                            requiredText(value, "stageReceiptSha256")));
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
        ObjectNode registry = node.putObject("artifactPolicyRegistryRef");
        registry.put("artifactId", controls.artifactPolicyRegistryRef().artifactId());
        registry.put("sha256", controls.artifactPolicyRegistryRef().sha256());
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
        ObjectNode registry = requiredObject(node, "artifactPolicyRegistryRef");
        requireExactFields(registry, List.of("artifactId", "sha256"), "policy registry reference");
        JsonNode prompt = node.get("promptBundleSha256");
        if (prompt == null || !(prompt.isNull() || prompt.isTextual())) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", "promptBundleSha256 must be null or text");
        }
        return new ArtifactControls(
                requiredText(node, "toolchainSha256"),
                requiredText(node, "profileSha256"),
                requiredText(node, "schemaBundleSha256"),
                prompt.isNull() ? null : prompt.textValue(),
                new ArtifactPolicyRegistryReference(
                        requiredText(registry, "artifactId"), requiredText(registry, "sha256")));
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
                throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "descriptor must be object");
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
                throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "descriptor size must be long");
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
        for (JsonNode value : array) {
            if (!value.isTextual()) {
                throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "gap reference must be text");
            }
            values.add(ArtifactValues.text(value.textValue(), "gapRef"));
        }
        ArtifactValues.sortedUnique(values, "gapRefs");
        return List.copyOf(values);
    }

    private static ObjectNode requiredObject(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isObject()) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", field + " must be object");
        }
        return (ObjectNode) value;
    }

    private static ArrayNode requiredArray(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", field + " must be array");
        }
        return (ArrayNode) value;
    }

    private static String requiredText(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", field + " must be textual");
        }
        return value.textValue();
    }

    private static int requiredInt(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", field + " must be int");
        }
        return value.intValue();
    }

    private static void requireExactFields(ObjectNode node, List<String> fields, String subject) {
        List<String> observed = new ArrayList<>();
        node.fieldNames().forEachRemaining(observed::add);
        if (!observed.containsAll(fields) || observed.size() != fields.size()) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", subject + " fields do not match exact schema");
        }
    }

    private static String descriptorListRoot(
            String domain, String prefix, List<ArtifactDescriptor> descriptors) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(frame(domain.getBytes(StandardCharsets.UTF_8)));
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(descriptors.size()).array());
            for (ArtifactDescriptor descriptor : descriptors) {
                digest.update(frame(descriptorBytes(descriptor)));
            }
            return prefix + ":" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static byte[] descriptorBytes(ArtifactDescriptor descriptor) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            bytes.write(frame(descriptor.fileName().getBytes(StandardCharsets.UTF_8)));
            bytes.write(frame(descriptor.artifactType().getBytes(StandardCharsets.UTF_8)));
            bytes.write(frame(descriptor.schemaVersion().getBytes(StandardCharsets.UTF_8)));
            bytes.write(frame(descriptor.artifactId().getBytes(StandardCharsets.UTF_8)));
            bytes.write(frame(descriptor.mediaType().getBytes(StandardCharsets.UTF_8)));
            bytes.write(ByteBuffer.allocate(Long.BYTES).putLong(descriptor.sizeBytes()).array());
            bytes.write(HexFormat.of().parseHex(descriptor.sha256()));
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String framedDigest(String domain, String schema, String type, byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(frame(domain.getBytes(StandardCharsets.UTF_8)));
            digest.update(frame(schema.getBytes(StandardCharsets.UTF_8)));
            digest.update(frame(type.getBytes(StandardCharsets.UTF_8)));
            digest.update(frame(content));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String framedDigest(String domain, byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(frame(domain.getBytes(StandardCharsets.UTF_8)));
            digest.update(frame(content));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static byte[] frame(byte[] bytes) {
        return ByteBuffer.allocate(Long.BYTES + bytes.length).putLong(bytes.length).put(bytes).array();
    }

    private record PreparedReceipt(StageReceipt receipt, ImmutableBytes canonicalUtf8, String sha256) {}
}
