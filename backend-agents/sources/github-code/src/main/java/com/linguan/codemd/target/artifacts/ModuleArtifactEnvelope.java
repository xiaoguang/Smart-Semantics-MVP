package com.linguan.codemd.target.artifacts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Strict canonical wire envelope for an ordinary successful module artifact.
 *
 * <p>It is deliberately independent of a module's filesystem directory. The module store may
 * install these bytes only after this class has validated the policy-selected identity.
 */
public final class ModuleArtifactEnvelope {
    private static final ObjectMapper JSON =
            new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    private final ModuleArtifactEnvelopeDraft draft;
    private final ArtifactReference reference;
    private final ImmutableBytes canonicalUtf8;

    private ModuleArtifactEnvelope(
            ModuleArtifactEnvelopeDraft draft, ArtifactReference reference, ImmutableBytes canonicalUtf8) {
        this.draft = draft;
        this.reference = reference;
        this.canonicalUtf8 = canonicalUtf8;
    }

    /** Writes the policy-selected canonical envelope and computes its complete content identity. */
    public static ModuleArtifactEnvelope write(
            ModuleArtifactEnvelopeDraft draft, CanonicalArtifactPolicy policy) {
        requireModulePolicy(policy);
        if (draft == null) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", "draft must not be null");
        }
        CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
        ObjectNode withoutId = envelopeWithoutArtifactId(draft, policy);
        String artifactId = expectedArtifactId(policy, canonicalJson, withoutId);
        ObjectNode complete = withoutId.deepCopy();
        complete.put("artifactId", artifactId);
        ImmutableBytes canonicalUtf8 = canonicalJson.canonicalize(complete);
        return new ModuleArtifactEnvelope(
                copyDraft(draft),
                new ArtifactReference(artifactId, ArtifactValues.digest(canonicalUtf8.copyToByteArray())),
                canonicalUtf8);
    }

    /** Reopens and fully revalidates an already persisted envelope from its exact canonical bytes. */
    public static ModuleArtifactEnvelope parse(ImmutableBytes canonicalUtf8, CanonicalArtifactPolicy policy) {
        requireModulePolicy(policy);
        if (canonicalUtf8 == null) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "canonicalUtf8 must not be null");
        }
        CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
        byte[] raw = canonicalUtf8.copyToByteArray();
        ObjectNode document = parseObject(raw);
        if (!Arrays.equals(raw, canonicalJson.canonicalize(document).copyToByteArray())) {
            throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "module artifact bytes are not canonical JSON");
        }
        requireExactFields(
                document,
                List.of(
                        "artifactId",
                        "artifactType",
                        "completion",
                        "controls",
                        "payload",
                        "producer",
                        "schemaVersion",
                        "upstreamArtifacts"),
                "module artifact");
        String schemaVersion = requiredText(document, "schemaVersion");
        String artifactType = requiredText(document, "artifactType");
        if (!policy.key().schemaVersion().equals(schemaVersion)
                || !policy.key().artifactType().equals(artifactType)) {
            throw new ArtifactStoreException("ARTIFACT_POLICY_MISMATCH", "module artifact schema differs from policy");
        }
        String artifactId = requiredText(document, "artifactId");
        ArtifactValues.artifactId(artifactId, "artifactId");
        ModuleArtifactEnvelopeDraft draft =
                new ModuleArtifactEnvelopeDraft(
                        parseAddress(requiredObject(document, "producer").get("address")),
                        parseModuleVersion(requiredObject(document, "producer")),
                        parseReferences(requiredArray(document, "upstreamArtifacts")),
                        parseControls(requiredObject(document, "controls")),
                        parseCompletionStatus(requiredObject(document, "completion")),
                        parseCompletionGaps(requiredObject(document, "completion")),
                        requiredPayload(document));
        ObjectNode withoutId = document.deepCopy();
        withoutId.remove("artifactId");
        String expectedArtifactId = expectedArtifactId(policy, canonicalJson, withoutId);
        if (!expectedArtifactId.equals(artifactId)) {
            throw new ArtifactStoreException(
                    "ARTIFACT_POLICY_MISMATCH", "module artifact ID differs from canonical envelope");
        }
        return new ModuleArtifactEnvelope(
                draft,
                new ArtifactReference(artifactId, ArtifactValues.digest(raw)),
                ImmutableBytes.copyOf(raw));
    }

    public ModuleArtifactEnvelopeDraft draft() {
        return copyDraft(draft);
    }

    public ModulePublicationAddress address() {
        return draft.address();
    }

    public String moduleVersion() {
        return draft.moduleVersion();
    }

    public ArtifactReference reference() {
        return reference;
    }

    public ImmutableBytes canonicalUtf8() {
        return ImmutableBytes.copyOf(canonicalUtf8.copyToByteArray());
    }

    public JsonNode payload() {
        return draft.payload().deepCopy();
    }

    private static ModuleArtifactEnvelopeDraft copyDraft(ModuleArtifactEnvelopeDraft draft) {
        return new ModuleArtifactEnvelopeDraft(
                draft.address(),
                draft.moduleVersion(),
                draft.upstreamArtifacts(),
                draft.controls(),
                draft.status(),
                draft.gapRefs(),
                draft.payload());
    }

    private static void requireModulePolicy(CanonicalArtifactPolicy policy) {
        if (policy == null || !"MODULE_ARTIFACT_JSON".equals(policy.envelopeKind())) {
            throw new ArtifactStoreException(
                    "ARTIFACT_POLICY_MISMATCH", "module envelope requires a MODULE_ARTIFACT_JSON policy");
        }
        if (!"application/json".equals(policy.mediaType())) {
            throw new ArtifactStoreException(
                    "ARTIFACT_POLICY_MISMATCH", "module envelope requires application/json media type");
        }
    }

    private static ObjectNode envelopeWithoutArtifactId(
            ModuleArtifactEnvelopeDraft draft, CanonicalArtifactPolicy policy) {
        ObjectNode result = JSON.createObjectNode();
        result.put("schemaVersion", policy.key().schemaVersion());
        result.put("artifactType", policy.key().artifactType());
        ObjectNode producer = result.putObject("producer");
        producer.set("address", addressNode(draft.address()));
        producer.put("moduleVersion", draft.moduleVersion());
        result.set("upstreamArtifacts", referencesNode(draft.upstreamArtifacts()));
        result.set("controls", controlsNode(draft.controls()));
        ObjectNode completion = result.putObject("completion");
        completion.put("status", draft.status());
        ArrayNode gaps = completion.putArray("gapRefs");
        draft.gapRefs().forEach(gaps::add);
        completion.putNull("failureRef");
        result.set("payload", draft.payload().deepCopy());
        return result;
    }

    private static String expectedArtifactId(
            CanonicalArtifactPolicy policy, CanonicalJsonCodec canonicalJson, ObjectNode withoutId) {
        return policy.artifactIdPrefix()
                + ":"
                + framedDigest(
                        "canonical-module-artifact-id-v1",
                        policy.key().schemaVersion(),
                        policy.key().artifactType(),
                        canonicalJson.canonicalize(withoutId).copyToByteArray());
    }

    private static ObjectNode parseObject(byte[] raw) {
        try {
            JsonNode parsed = JSON.readTree(raw);
            if (parsed == null || !parsed.isObject()) {
                throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "module artifact must be an object");
            }
            return (ObjectNode) parsed;
        } catch (ArtifactStoreException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "cannot parse module artifact", exception);
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
        return ArtifactValues.text(value.textValue(), field);
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

    private static JsonNode requiredPayload(ObjectNode node) {
        JsonNode payload = node.get("payload");
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "payload must not be null");
        }
        return payload;
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

    private static ModulePublicationAddress parseAddress(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "producer address must be an object");
        }
        ObjectNode node = (ObjectNode) value;
        String kind = requiredText(node, "kind");
        if ("STAGE".equals(kind)) {
            requireExactFields(
                    node,
                    List.of("kind", "moduleKey", "moduleNumber", "runId", "stageKey", "stageNumber"),
                    "stage module address");
            return new StageModuleAddress(
                    requiredText(node, "runId"),
                    requiredInt(node, "stageNumber"),
                    requiredText(node, "stageKey"),
                    requiredInt(node, "moduleNumber"),
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
                    requiredInt(node, "moduleNumber"),
                    requiredText(node, "moduleKey"));
        }
        throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "unknown module address kind");
    }

    private static int requiredInt(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", field + " must be an integer");
        }
        return value.intValue();
    }

    private static String parseModuleVersion(ObjectNode producer) {
        requireExactFields(producer, List.of("address", "moduleVersion"), "module producer");
        return requiredText(producer, "moduleVersion");
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
                throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "upstream artifact must be an object");
            }
            ObjectNode reference = (ObjectNode) node;
            requireExactFields(reference, List.of("artifactId", "sha256"), "upstream artifact");
            references.add(new ArtifactReference(requiredText(reference, "artifactId"), requiredText(reference, "sha256")));
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
        ObjectNode policy = node.putObject("artifactPolicyRegistryRef");
        policy.put("artifactId", controls.artifactPolicyRegistryRef().artifactId());
        policy.put("sha256", controls.artifactPolicyRegistryRef().sha256());
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
        ObjectNode policy = requiredObject(node, "artifactPolicyRegistryRef");
        requireExactFields(policy, List.of("artifactId", "sha256"), "policy registry reference");
        JsonNode prompt = node.get("promptBundleSha256");
        if (prompt == null || !(prompt.isNull() || prompt.isTextual())) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_INVALID", "promptBundleSha256 must be null or textual");
        }
        return new ArtifactControls(
                requiredText(node, "toolchainSha256"),
                requiredText(node, "profileSha256"),
                requiredText(node, "schemaBundleSha256"),
                prompt.isNull() ? null : requiredText(node, "promptBundleSha256"),
                new ArtifactPolicyRegistryReference(requiredText(policy, "artifactId"), requiredText(policy, "sha256")));
    }

    private static String parseCompletionStatus(ObjectNode completion) {
        requireExactFields(completion, List.of("failureRef", "gapRefs", "status"), "module completion");
        JsonNode failure = completion.get("failureRef");
        if (failure == null || !failure.isNull()) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_INVALID", "successful module completion must have null failureRef");
        }
        String status = requiredText(completion, "status");
        if (!"SUCCEEDED".equals(status) && !"SUCCEEDED_WITH_GAPS".equals(status)) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "completion status is not a success status");
        }
        return status;
    }

    private static List<String> parseCompletionGaps(ObjectNode completion) {
        ArrayNode array = requiredArray(completion, "gapRefs");
        List<String> gaps = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isTextual()) {
                throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "gap reference must be textual");
            }
            gaps.add(ArtifactValues.text(node.textValue(), "gapRefs"));
        }
        return List.copyOf(gaps);
    }

    private static String framedDigest(String domain, Object... parts) {
        List<byte[]> frames = new ArrayList<>();
        frames.add(frame(domain.getBytes(StandardCharsets.UTF_8)));
        for (Object part : parts) {
            byte[] bytes =
                    part instanceof String text
                            ? text.getBytes(StandardCharsets.UTF_8)
                            : (byte[]) part;
            frames.add(frame(bytes));
        }
        return ArtifactValues.digest(frames.toArray(byte[][]::new));
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
}
