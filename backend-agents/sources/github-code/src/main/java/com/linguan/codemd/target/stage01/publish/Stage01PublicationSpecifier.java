package com.linguan.codemd.target.stage01.publish;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.target.artifacts.ArtifactControls;
import com.linguan.codemd.target.artifacts.ArtifactPolicyKey;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicy;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicyRegistry;
import com.linguan.codemd.target.artifacts.CanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.CanonicalModulePayload;
import com.linguan.codemd.target.artifacts.CanonicalStageArtifactStore;
import com.linguan.codemd.target.artifacts.CanonicalStagePayload;
import com.linguan.codemd.target.artifacts.ImmutableBytes;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelope;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelopeDraft;
import com.linguan.codemd.target.artifacts.ModuleInstallRequest;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.ReopenedModulePublication;
import com.linguan.codemd.target.artifacts.StageInstallRequest;
import com.linguan.codemd.target.artifacts.StageModuleAddress;
import com.linguan.codemd.target.artifacts.StagePublisherModuleProvenance;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceRequest;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceRequestArtifact;
import com.linguan.codemd.target.stage01.sourceindex.SourceShardReceipt;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceFile;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceIndex;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceIndexArtifact;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** M3: publishes exactly the Stage01 semantic set from freshly reopened M1/M2 and frozen inputs. */
public final class Stage01PublicationSpecifier {
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final ArtifactPolicyKey M1 =
            new ArtifactPolicyKey("STAGE01_ADMITTED_SOURCE_REQUEST", "stage01-admitted-source-request-v2");
    private static final ArtifactPolicyKey M2 =
            new ArtifactPolicyKey("STAGE01_VERIFIED_SOURCE_INDEX", "stage01-verified-source-index-v2");
    private static final ArtifactPolicyKey SOURCE_INPUT =
            new ArtifactPolicyKey("STAGE01_SOURCE_INPUT", "stage01-source-input-v2");
    private static final ArtifactPolicyKey INVENTORY =
            new ArtifactPolicyKey("STAGE01_SOURCE_INVENTORY", "stage01-source-inventory-v2");
    private static final ArtifactPolicyKey SNAPSHOT = new ArtifactPolicyKey("VERIFIED_SNAPSHOT", "verified-snapshot-v2");

    public Stage01Reference publish(
            Stage01PublicationSpecificationInput input,
            Stage01InputArtifactRegistry inputs,
            CanonicalArtifactPolicyRegistry policies,
            CanonicalModuleArtifactStore moduleStore,
            CanonicalStageArtifactStore stageStore) {
        if (input == null || inputs == null || policies == null || moduleStore == null || stageStore == null) {
            throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", "M3 arguments must not be null");
        }
        CanonicalArtifactPolicy m1Policy = policies.resolve(M1);
        CanonicalArtifactPolicy m2Policy = policies.resolve(M2);
        CanonicalArtifactPolicy sourceInputPolicy = policies.resolve(SOURCE_INPUT);
        CanonicalArtifactPolicy inventoryPolicy = policies.resolve(INVENTORY);
        CanonicalArtifactPolicy snapshotPolicy = policies.resolve(SNAPSHOT);
        ReopenedModulePublication m1 = moduleStore.reopen(input.admittedSourceRequestPublication());
        ReopenedModulePublication m2 = moduleStore.reopen(input.verifiedSourceIndexPublication());
        requireAddress(m1, 1, "request-admission");
        requireAddress(m2, 2, "source-index");
        requireSameControlsAndStatus(m1, m2);
        ModuleArtifactEnvelope m1Envelope = envelope(m1, m1Policy);
        ModuleArtifactEnvelope m2Envelope = envelope(m2, m2Policy);
        AdmittedSourceRequest request = AdmittedSourceRequestArtifact.parse(m1Envelope.payload());
        VerifiedSourceIndex index = VerifiedSourceIndexArtifact.parse(m2Envelope.payload());
        ArtifactReference m1Payload = payloadReference(m1);
        ArtifactReference m2Payload = payloadReference(m2);
        if (!m1Payload.artifactId().equals(index.requestArtifactId())) {
            throw new Stage01PublicationException("CAPTURE_IDENTITY_INVALID", "M2 does not bind the reopened M1 payload");
        }
        RunInput run = parseRun(reopen(inputs, input.analysisRunRequestRef()), input.analysisRunRequestRef());
        FrozenInput frozen = parseFrozen(reopen(inputs, input.frozenRepositoryRequestRef()));
        validateCrossInputs(input, m1Envelope, request, run, frozen);

        List<ArtifactReference> upstreams = sorted(m1Payload, m2Payload, input.analysisRunRequestRef(), input.frozenRepositoryRequestRef());
        List<Payload> payloads = List.of(
                sourceInputPayload(sourceInputPolicy, request, frozen, run),
                inventoryPayload(inventoryPolicy, index),
                verifiedSnapshotPayload(snapshotPolicy, request, frozen, run, index));
        StageModuleAddress publisherAddress =
                new StageModuleAddress(input.destination().runId(), 1, "freeze-source", 3, "publish");
        ModulePublicationReference publisher = moduleStore.install(new ModuleInstallRequest(
                        publisherAddress,
                        "v2",
                        upstreams,
                        m1.receipt().controls(),
                        m1.receipt().status(),
                        m1.receipt().gapRefs(),
                        payloads.stream().map(Payload::modulePayload).toList()))
                .reference();
        var reopenedPublisher = moduleStore.reopen(publisher);
        if (reopenedPublisher.payloads().size() != 3) {
            throw new Stage01PublicationException("CAPTURE_IDENTITY_INVALID", "M3 must install exactly three payloads");
        }
        var stage = stageStore.install(new StageInstallRequest(
                        input.destination(),
                        new StagePublisherModuleProvenance(publisher),
                        List.of(),
                        m1.receipt().controls(),
                        m1.receipt().status(),
                        m1.receipt().gapRefs(),
                        payloads.stream().map(Payload::stagePayload).toList(),
                        null))
                .reference();
        stageStore.reopen(stage);
        return new Stage01Reference(publisher, stage);
    }

    private static Payload sourceInputPayload(
            CanonicalArtifactPolicy policy, AdmittedSourceRequest request, FrozenInput frozen, RunInput run) {
        ObjectNode document = JSON.createObjectNode();
        String sourceInputId = "stage01-input:" + digest("stage01-source-input-id-v2", request.requestIdentity());
        document.put("sourceInputId", sourceInputId);
        document.put("sourceRegistrationId", request.sourceRegistrationId());
        reference(document, "frozenRepositoryRequestRef", run.frozenRequestRef());
        reference(document, "captureReceiptRef", frozen.captureReceiptRef());
        reference(document, "snapshotManifestRef", frozen.snapshotManifestRef());
        scope(document, request);
        document.put("repositoryCompletionEligible", request.repositoryCompletionEligible());
        reference(document, "profileBundleRef", run.profileBundleRef());
        reference(document, "resourceBudgetRef", run.resourceBudgetRef());
        reference(document, "toolchainRef", run.toolchainRef());
        reference(document, "schemaBundleRef", run.schemaBundleRef());
        reference(document, "promptBundleRef", run.promptBundleRef());
        reference(document, "artifactPolicyRegistryRef", run.artifactPolicyRegistryRef());
        return standalone("source-input.json", policy, document);
    }

    private static Payload inventoryPayload(CanonicalArtifactPolicy policy, VerifiedSourceIndex index) {
        List<VerifiedSourceFile> files = index.verifiedFiles().stream().sorted(Comparator.comparing(VerifiedSourceFile::path)).toList();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (VerifiedSourceFile file : files) {
            ObjectNode node = JSON.createObjectNode();
            node.put("schemaVersion", "source-inventory-entry-v2");
            node.put("fileId", file.fileId());
            node.put("path", file.path());
            node.put("gitMode", file.gitMode());
            node.put("mediaType", file.mediaType());
            node.put("sizeBytes", file.sizeBytes());
            node.put("sha256", file.sha256());
            node.put("analysisDisposition", file.analysisDisposition().name());
            nullable(node, "textEncoding", file.textEncoding());
            nullable(node, "lineIndexDigest", file.lineIndexDigest());
            bytes.writeBytes(new com.linguan.codemd.target.artifacts.CanonicalJsonCodec().canonicalize(node).copyToByteArray());
            bytes.write('\n');
        }
        byte[] canonical = bytes.toByteArray();
        String artifactId = policy.artifactIdPrefix() + ":" + digest(
                "canonical-jsonl-artifact-id-v1",
                policy.key().schemaVersion().getBytes(StandardCharsets.UTF_8),
                policy.key().artifactType().getBytes(StandardCharsets.UTF_8),
                canonical);
        return new Payload(
                new CanonicalModulePayload(
                        "source-inventory.jsonl", policy.key().artifactType(), policy.key().schemaVersion(), artifactId,
                        policy.mediaType(), ImmutableBytes.copyOf(canonical)),
                new CanonicalStagePayload(
                        "source-inventory.jsonl", policy.key().artifactType(), policy.key().schemaVersion(), artifactId,
                        policy.mediaType(), ImmutableBytes.copyOf(canonical)));
    }

    private static Payload verifiedSnapshotPayload(
            CanonicalArtifactPolicy policy,
            AdmittedSourceRequest request,
            FrozenInput frozen,
            RunInput run,
            VerifiedSourceIndex index) {
        ObjectNode material = JSON.createObjectNode();
        material.put("originRepositoryUrl", request.originRepositoryUrl());
        material.put("originRevision", request.originRevision());
        reference(material, "captureReceiptRef", frozen.captureReceiptRef());
        reference(material, "snapshotManifestRef", frozen.snapshotManifestRef());
        scope(material, request);
        reference(material, "verificationPolicyRef", frozen.verificationPolicyRef());
        reference(material, "capabilityProfileRef", run.profileBundleRef());
        reference(material, "resourceBudgetRef", run.resourceBudgetRef());
        ArrayNode identityFiles = material.putArray("files");
        index.verifiedFiles().forEach(file -> identityFiles.addObject()
                .put("path", file.path())
                .put("gitMode", file.gitMode())
                .put("mediaType", file.mediaType())
                .put("sizeBytes", file.sizeBytes())
                .put("sha256", file.sha256())
                .put("analysisDisposition", file.analysisDisposition().name())
                .put("textEncoding", file.textEncoding() == null ? "<null>" : file.textEncoding()));
        String snapshotId = "snapshot:" + digest(
                "verified-snapshot-id-v2", new com.linguan.codemd.target.artifacts.CanonicalJsonCodec().canonicalize(material).copyToByteArray());
        ObjectNode document = JSON.createObjectNode();
        document.put("snapshotId", snapshotId);
        document.put("declaredRepositoryIdentity", request.originRepositoryUrl());
        document.put("objectFormat", "SHA1");
        document.put("originRevision", request.originRevision());
        reference(document, "captureReceiptRef", frozen.captureReceiptRef());
        reference(document, "snapshotManifestRef", frozen.snapshotManifestRef());
        scope(document, request);
        document.put("repositoryCompletionEligible", request.repositoryCompletionEligible());
        reference(document, "verificationPolicyRef", frozen.verificationPolicyRef());
        reference(document, "capabilityProfileRef", run.profileBundleRef());
        reference(document, "resourceBudgetRef", run.resourceBudgetRef());
        document.put("trackedRegularFileCount", index.verifiedRegularFileCount());
        document.put("verifiedRegularFileCount", index.verifiedRegularFileCount());
        document.put("unverifiedRegularFileCount", 0);
        document.put("analyzableTextFileCount", index.analyzableTextFileCount());
        document.put("nonAnalyzableMediaFileCount", index.nonAnalyzableMediaFileCount());
        List<String> all = index.verifiedFiles().stream().map(VerifiedSourceFile::fileId).sorted().toList();
        strings(document.putArray("trackedRegularFileIds"), all);
        strings(document.putArray("verifiedRegularFileIds"), all);
        strings(document.putArray("unverifiedRegularFileIds"), List.of());
        strings(document.putArray("analyzableTextFileIds"), index.verifiedFiles().stream()
                .filter(file -> file.textEncoding() != null).map(VerifiedSourceFile::fileId).sorted().toList());
        strings(document.putArray("nonAnalyzableMediaFileIds"), index.verifiedFiles().stream()
                .filter(file -> file.textEncoding() == null).map(VerifiedSourceFile::fileId).sorted().toList());
        ArrayNode shards = document.putArray("shardReceipts");
        index.shardReceipts().forEach(shard -> shard(shards.addObject(), shard));
        ObjectNode proof = document.putObject("accountingProof");
        proof.put("trackedEqualsVerifiedUnionUnverified", true);
        proof.put("verifiedEqualsAnalyzableTextUnionNonAnalyzableMedia", true);
        document.put("sourceIntegrity", index.sourceIntegrity());
        return standalone("verified-snapshot.json", policy, document);
    }

    private static Payload standalone(String fileName, CanonicalArtifactPolicy policy, ObjectNode body) {
        com.linguan.codemd.target.artifacts.CanonicalJsonCodec codec = new com.linguan.codemd.target.artifacts.CanonicalJsonCodec();
        String artifactId = policy.artifactIdPrefix() + ":" + digest(
                "canonical-standalone-json-artifact-id-v1",
                policy.key().schemaVersion().getBytes(StandardCharsets.UTF_8),
                policy.key().artifactType().getBytes(StandardCharsets.UTF_8),
                codec.canonicalize(body).copyToByteArray());
        ObjectNode complete = body.deepCopy();
        complete.put("artifactId", artifactId);
        ImmutableBytes bytes = codec.canonicalize(complete);
        return new Payload(
                new CanonicalModulePayload(fileName, policy.key().artifactType(), policy.key().schemaVersion(), artifactId, policy.mediaType(), bytes),
                new CanonicalStagePayload(fileName, policy.key().artifactType(), policy.key().schemaVersion(), artifactId, policy.mediaType(), bytes));
    }

    private static void validateCrossInputs(
            Stage01PublicationSpecificationInput input,
            ModuleArtifactEnvelope m1,
            AdmittedSourceRequest request,
            RunInput run,
            FrozenInput frozen) {
        if (!input.frozenRepositoryRequestRef().equals(run.frozenRequestRef())
                || !request.sourceRegistrationId().equals(run.sourceRegistrationId())
                || !request.sourceRegistrationId().equals(referenceById(m1, request.sourceRegistrationId()).artifactId())
                || !request.originRepositoryUrl().equals(frozen.repositoryUrl())
                || !request.originRevision().equals(frozen.revision())
                || !request.inventoryScope().equals(frozen.inventoryScope())
                || !request.capabilityProfileRef().equals(run.profileBundleRef())
                || !request.resourceBudgetRef().equals(run.resourceBudgetRef())
                || !request.verificationPolicyRef().equals(frozen.verificationPolicyRef())) {
            throw new Stage01PublicationException("CAPTURE_IDENTITY_INVALID", "M1, run input and frozen input differ");
        }
    }

    private static ArtifactReference referenceById(ModuleArtifactEnvelope envelope, String id) {
        return envelope.draft().upstreamArtifacts().stream()
                .filter(reference -> id.equals(reference.artifactId()))
                .findFirst()
                .orElseThrow(() -> new Stage01PublicationException("CAPTURE_IDENTITY_INVALID", "M1 input reference is missing"));
    }

    private static ImmutableBytes reopen(Stage01InputArtifactRegistry inputs, ArtifactReference reference) {
        try {
            ImmutableBytes bytes = inputs.reopen(reference);
            if (bytes == null || !sha256(bytes.copyToByteArray()).equals(reference.sha256())) {
                throw new Stage01PublicationException("CAPTURE_IDENTITY_INVALID", "referenced run input differs from its digest");
            }
            return bytes;
        } catch (Stage01PublicationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new Stage01PublicationException("CAPTURE_IDENTITY_INVALID", "referenced run input cannot be reopened", exception);
        }
    }

    private static ModuleArtifactEnvelope envelope(ReopenedModulePublication publication, CanonicalArtifactPolicy policy) {
        if (publication.payloads().size() != 1) {
            throw new Stage01PublicationException("CAPTURE_IDENTITY_INVALID", "upstream module payload count is invalid");
        }
        return ModuleArtifactEnvelope.parse(publication.payloads().get(0).canonicalUtf8(), policy);
    }

    private static ArtifactReference payloadReference(ReopenedModulePublication publication) {
        var descriptor = publication.payloads().get(0).descriptor();
        return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
    }

    private static void requireAddress(ReopenedModulePublication publication, int moduleNumber, String moduleKey) {
        if (!(publication.reference().address() instanceof StageModuleAddress address)
                || address.stageNumber() != 1
                || !"freeze-source".equals(address.stageKey())
                || address.moduleNumber() != moduleNumber
                || !moduleKey.equals(address.moduleKey())) {
            throw new Stage01PublicationException("CAPTURE_IDENTITY_INVALID", "upstream module address is invalid");
        }
    }

    private static void requireSameControlsAndStatus(ReopenedModulePublication left, ReopenedModulePublication right) {
        if (!left.receipt().controls().equals(right.receipt().controls())
                || !left.receipt().status().equals(right.receipt().status())
                || !left.receipt().gapRefs().equals(right.receipt().gapRefs())) {
            throw new Stage01PublicationException("CAPTURE_IDENTITY_INVALID", "M1 and M2 receipts differ");
        }
    }

    private static RunInput parseRun(ImmutableBytes bytes, ArtifactReference expected) {
        ObjectNode root = parsed(bytes);
        exact(root, Set.of("approvedFindingRefs", "artifactPolicyRegistryRef", "candidateSeriesRef", "frozenRepositoryRequestRef",
                "organizationRegistrySeedRef", "parentCandidateRef", "profileBundleRef", "promptBundleRef", "readerCandidateRound",
                "resourceBudgetRef", "schemaBundleRef", "schemaVersion", "sourceRegistrationId", "toolchainRef"));
        if (!"analysis-run-request-v2".equals(text(root, "schemaVersion")) || !"ROUND_1".equals(text(root, "readerCandidateRound"))
                || !array(root, "approvedFindingRefs").isEmpty() || !root.get("organizationRegistrySeedRef").isNull()
                || !root.get("parentCandidateRef").isNull()) {
            throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", "analysis run input is not a supported round-one request");
        }
        if (!expected.artifactId().equals("run-request:" + digest("analysis-run-request-id-v2", bytes.copyToByteArray()))) {
            throw new Stage01PublicationException("CAPTURE_IDENTITY_INVALID", "analysis run identity does not match its bytes");
        }
        return new RunInput(
                text(root, "sourceRegistrationId"),
                reference(root, "frozenRepositoryRequestRef"),
                reference(root, "profileBundleRef"),
                reference(root, "resourceBudgetRef"),
                reference(root, "toolchainRef"),
                reference(root, "schemaBundleRef"),
                reference(root, "promptBundleRef"),
                reference(root, "artifactPolicyRegistryRef"));
    }

    private static FrozenInput parseFrozen(ImmutableBytes bytes) {
        ObjectNode root = parsed(bytes);
        exact(root, Set.of("capabilityProfileRef", "captureReceiptRef", "expectedOrigin", "inventoryScope", "resourceBudgetRef",
                "schemaVersion", "snapshotManifestRef", "verificationPolicyRef"));
        if (!"frozen-repository-request-v2".equals(text(root, "schemaVersion"))) {
            throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", "frozen request schema is invalid");
        }
        ObjectNode origin = object(root, "expectedOrigin");
        exact(origin, Set.of("kind", "repositoryUrl", "revision40"));
        if (!"LOCAL_GIT".equals(text(origin, "kind"))) {
            throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", "frozen request origin kind is invalid");
        }
        ObjectNode scope = object(root, "inventoryScope");
        exact(scope, Set.of("declaredPathCount", "kind", "scopeRoot"));
        com.linguan.codemd.target.stage01.requestadmission.InventoryScope inventoryScope = new com.linguan.codemd.target.stage01.requestadmission.InventoryScope(
                com.linguan.codemd.target.stage01.requestadmission.InventoryScope.Kind.valueOf(text(scope, "kind")),
                scope.get("scopeRoot").isNull() ? null : text(scope, "scopeRoot"),
                integer(scope, "declaredPathCount"));
        return new FrozenInput(
                text(origin, "repositoryUrl"),
                text(origin, "revision40"),
                inventoryScope,
                reference(root, "captureReceiptRef"),
                reference(root, "snapshotManifestRef"),
                reference(root, "verificationPolicyRef"));
    }

    private static ObjectNode parsed(ImmutableBytes bytes) {
        try {
            JsonNode node = JSON.readTree(bytes.copyToByteArray());
            if (!(node instanceof ObjectNode object)
                    || !Arrays.equals(bytes.copyToByteArray(), new com.linguan.codemd.target.artifacts.CanonicalJsonCodec().canonicalize(object).copyToByteArray())) {
                throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", "run input must be canonical JSON");
            }
            return object;
        } catch (Stage01PublicationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", "run input cannot be parsed", exception);
        }
    }

    private static void scope(ObjectNode document, AdmittedSourceRequest request) {
        ObjectNode scope = document.putObject("inventoryScope");
        scope.put("kind", request.inventoryScope().kind().name());
        if (request.inventoryScope().scopeRoot() == null) scope.putNull("scopeRoot"); else scope.put("scopeRoot", request.inventoryScope().scopeRoot());
        scope.put("declaredPathCount", request.declaredPathCount());
    }

    private static void shard(ObjectNode node, SourceShardReceipt receipt) {
        node.put("shardId", receipt.shardId());
        strings(node.putArray("denominatorFileIds"), receipt.denominatorFileIds());
        strings(node.putArray("verifiedFileIds"), receipt.verifiedFileIds());
        node.put("status", receipt.status());
        strings(node.putArray("gapIds"), receipt.gapIds());
    }

    private static void reference(ObjectNode node, String name, ArtifactReference ref) {
        ObjectNode value = node.putObject(name); value.put("artifactId", ref.artifactId()); value.put("sha256", ref.sha256());
    }
    private static ArtifactReference reference(ObjectNode node, String name) {
        ObjectNode value = object(node, name); exact(value, Set.of("artifactId", "sha256")); return new ArtifactReference(text(value, "artifactId"), text(value, "sha256"));
    }
    private static void strings(ArrayNode array, List<String> values) { values.forEach(array::add); }
    private static void nullable(ObjectNode node, String name, String value) { if (value == null) node.putNull(name); else node.put(name, value); }
    private static List<ArtifactReference> sorted(ArtifactReference... values) { return Arrays.stream(values).sorted(Comparator.comparing(ArtifactReference::artifactId)).toList(); }
    private static void exact(ObjectNode object, Set<String> expected) { Set<String> actual = new LinkedHashSet<>(); object.fieldNames().forEachRemaining(actual::add); if (!actual.equals(expected)) throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", "input fields are not exact"); }
    private static ObjectNode object(ObjectNode parent, String name) { JsonNode value = parent.get(name); if (!(value instanceof ObjectNode object)) throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", name + " must be object"); return object; }
    private static ArrayNode array(ObjectNode parent, String name) { JsonNode value = parent.get(name); if (!(value instanceof ArrayNode array)) throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", name + " must be array"); return array; }
    private static String text(ObjectNode parent, String name) { JsonNode value = parent.get(name); if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", name + " must be text"); return value.textValue(); }
    private static int integer(ObjectNode parent, String name) { JsonNode value = parent.get(name); if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", name + " must be integer"); return value.intValue(); }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static String digest(String domain, String... values) { return digest(domain, Arrays.stream(values).map(value -> value.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new)); }
    private static String digest(String domain, byte[]... values) { try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); frame(digest, domain.getBytes(StandardCharsets.UTF_8)); for (byte[] value : values) frame(digest, value); return HexFormat.of().formatHex(digest.digest()); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static void frame(MessageDigest digest, byte[] bytes) { digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array()); digest.update(bytes); }

    private record Payload(CanonicalModulePayload modulePayload, CanonicalStagePayload stagePayload) {}
    private record RunInput(String sourceRegistrationId, ArtifactReference frozenRequestRef, ArtifactReference profileBundleRef,
            ArtifactReference resourceBudgetRef, ArtifactReference toolchainRef, ArtifactReference schemaBundleRef,
            ArtifactReference promptBundleRef, ArtifactReference artifactPolicyRegistryRef) {}
    private record FrozenInput(String repositoryUrl, String revision, com.linguan.codemd.target.stage01.requestadmission.InventoryScope inventoryScope,
            ArtifactReference captureReceiptRef, ArtifactReference snapshotManifestRef, ArtifactReference verificationPolicyRef) {}
}
