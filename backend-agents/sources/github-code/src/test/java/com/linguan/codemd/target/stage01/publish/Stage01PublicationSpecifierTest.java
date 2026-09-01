package com.linguan.codemd.target.stage01.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.linguan.codemd.target.artifacts.ArtifactControls;
import com.linguan.codemd.target.artifacts.ArtifactPolicyKey;
import com.linguan.codemd.target.artifacts.ArtifactPolicyRegistryReference;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.ArtifactStoreLimits;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicy;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicyRegistry;
import com.linguan.codemd.target.artifacts.CanonicalJsonCodec;
import com.linguan.codemd.target.artifacts.CanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.CanonicalModulePayload;
import com.linguan.codemd.target.artifacts.CanonicalStageArtifactStore;
import com.linguan.codemd.target.artifacts.FileSystemCanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.FileSystemCanonicalStageArtifactStore;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelope;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelopeDraft;
import com.linguan.codemd.target.artifacts.ModuleInstallRequest;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.RunStoreBootstrap;
import com.linguan.codemd.target.artifacts.RunStoreHandle;
import com.linguan.codemd.target.artifacts.StageModuleAddress;
import com.linguan.codemd.target.artifacts.StagePublicationAddress;
import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceFile;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceRequest;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceRequestArtifact;
import com.linguan.codemd.target.stage01.requestadmission.InventoryScope;
import com.linguan.codemd.target.stage01.sourceindex.SourceShardReceipt;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceFile;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceIndex;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceIndexArtifact;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Stage01PublicationSpecifierTest {
    private static final String RUN_ID = "analysis-run:" + "a".repeat(64);

    @TempDir Path temporaryDirectory;

    @Test
    void publishesExactlyThreeSemanticFilesThenLetsTheStageStoreAppendTheFourthReceipt()
            throws Exception {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicy requestPolicy = policy(
                "STAGE01_ADMITTED_SOURCE_REQUEST", "stage01-admitted-source-request-v2", "source-request", "MODULE_ARTIFACT_JSON");
        CanonicalArtifactPolicy indexPolicy = policy(
                "STAGE01_VERIFIED_SOURCE_INDEX", "stage01-verified-source-index-v2", "source-index", "MODULE_ARTIFACT_JSON");
        CanonicalArtifactPolicy sourceInputPolicy = policy(
                "STAGE01_SOURCE_INPUT", "stage01-source-input-v2", "stage01-source-input", "STANDALONE_JSON");
        CanonicalArtifactPolicy inventoryPolicy = policy(
                "STAGE01_SOURCE_INVENTORY", "stage01-source-inventory-v2", "stage01-source-inventory", "CANONICAL_JSONL");
        CanonicalArtifactPolicy snapshotPolicy = policy(
                "VERIFIED_SNAPSHOT", "verified-snapshot-v2", "verified-snapshot", "STANDALONE_JSON");
        ArtifactPolicyRegistryReference policyReference =
                new ArtifactPolicyRegistryReference("artifact-policy-registry:" + "b".repeat(64), "b".repeat(64));
        CanonicalArtifactPolicyRegistry policies = registry(
                policyReference, requestPolicy, indexPolicy, sourceInputPolicy, inventoryPolicy, snapshotPolicy);
        ArtifactControls controls =
                new ArtifactControls("1".repeat(64), "2".repeat(64), "3".repeat(64), "4".repeat(64), policyReference);
        Fixture fixture = fixture(controls, requestPolicy, indexPolicy);
        byte[] frozenRequestBytes = frozenRequestJson(fixture);
        ArtifactReference frozenRequest = contentReference("frozen-request", frozenRequestBytes);
        byte[] analysisRequestBytes = analysisRequestJson(fixture, frozenRequest);
        ArtifactReference analysisRequest = analysisRequestReference(analysisRequestBytes);
        Stage01InputArtifactRegistry inputs = reference -> {
            if (reference.equals(analysisRequest)) {
                return com.linguan.codemd.target.artifacts.ImmutableBytes.copyOf(analysisRequestBytes);
            }
            if (reference.equals(frozenRequest)) {
                return com.linguan.codemd.target.artifacts.ImmutableBytes.copyOf(frozenRequestBytes);
            }
            throw new AssertionError("unexpected input reference");
        };

        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore moduleStore =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 32_768, 64_000, 8));
            ModulePublicationReference m1 = installM1(moduleStore, fixture, requestPolicy);
            ModulePublicationReference m2 = installM2(moduleStore, fixture, indexPolicy, m1);
            CanonicalStageArtifactStore stageStore =
                    new FileSystemCanonicalStageArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 32_768, 64_000, 8));

            Stage01Reference published = new Stage01PublicationSpecifier()
                    .publish(
                            new Stage01PublicationSpecificationInput(
                                    new StagePublicationAddress(RUN_ID, 1, "freeze-source"),
                                    m1,
                                    m2,
                                    analysisRequest,
                                    frozenRequest),
                            inputs,
                            policies,
                            moduleStore,
                            stageStore);

            assertThat(moduleStore.reopen(published.publisherModuleReference()).payloads())
                    .extracting(payload -> payload.descriptor().fileName())
                    .containsExactly("source-input.json", "source-inventory.jsonl", "verified-snapshot.json");
            assertThat(stageStore.reopen(published.stagePublicationReference()).semanticPayloads())
                    .extracting(payload -> payload.descriptor().fileName())
                    .containsExactly("source-input.json", "source-inventory.jsonl", "verified-snapshot.json");
            assertThat(published.stagePublicationReference().address())
                    .isEqualTo(new StagePublicationAddress(RUN_ID, 1, "freeze-source"));
        }
    }

    private static Fixture fixture(ArtifactControls controls, CanonicalArtifactPolicy requestPolicy, CanonicalArtifactPolicy indexPolicy)
            throws Exception {
        byte[] bytes = "class App {}\n".getBytes(StandardCharsets.UTF_8);
        AdmittedSourceFile file = AdmittedSourceFile.fromWire(
                "src/App.java",
                "100644",
                "text/x-java-source",
                bytes.length,
                sha256(bytes),
                AnalysisDisposition.ANALYZABLE_TEXT,
                "UTF-8");
        ArtifactReference registration = ref("source-registration", '7');
        ArtifactReference captureReceipt = ref("capture-receipt", '8');
        ArtifactReference manifest = ref("snapshot-manifest", '9');
        AdmittedSourceRequest request = new AdmittedSourceRequest(
                "source-request:" + "c".repeat(64),
                registration.artifactId(),
                "https://example.invalid/customer.git",
                "d".repeat(40),
                new InventoryScope(InventoryScope.Kind.COMPLETE_CAPTURE, null, 1),
                true,
                1,
                List.of(file),
                List.of(file.fileId()),
                List.of(),
                ref("capability-profile", 'e'),
                ref("resource-budget", 'f'),
                ref("verification-policy", '0'));
        return new Fixture(controls, requestPolicy, indexPolicy, request, file, registration, captureReceipt, manifest);
    }

    private static ModulePublicationReference installM1(
            CanonicalModuleArtifactStore store, Fixture fixture, CanonicalArtifactPolicy policy) {
        StageModuleAddress address = new StageModuleAddress(RUN_ID, 1, "freeze-source", 1, "request-admission");
        ModuleArtifactEnvelope envelope = ModuleArtifactEnvelope.write(
                new ModuleArtifactEnvelopeDraft(
                        address,
                        "v2",
                        List.of(fixture.captureReceipt(), fixture.snapshotManifest(), fixture.registration()),
                        fixture.controls(),
                        "SUCCEEDED",
                        List.of(),
                        AdmittedSourceRequestArtifact.write(fixture.request())),
                policy);
        return store.install(new ModuleInstallRequest(
                        address,
                        "v2",
                        List.of(fixture.captureReceipt(), fixture.snapshotManifest(), fixture.registration()),
                        fixture.controls(),
                        "SUCCEEDED",
                        List.of(),
                        List.of(new CanonicalModulePayload(
                                "admitted-source-request.json",
                                policy.key().artifactType(),
                                policy.key().schemaVersion(),
                                envelope.reference().artifactId(),
                                policy.mediaType(),
                                envelope.canonicalUtf8()))))
                .reference();
    }

    private static ModulePublicationReference installM2(
            CanonicalModuleArtifactStore store, Fixture fixture, CanonicalArtifactPolicy policy, ModulePublicationReference m1) {
        var m1Payload = store.reopen(m1).payloads().get(0).descriptor();
        var m1Reference = new ArtifactReference(m1Payload.artifactId(), m1Payload.sha256());
        VerifiedSourceFile file = new VerifiedSourceFile(
                fixture.file().fileId(),
                fixture.file().path(),
                fixture.file().gitMode(),
                fixture.file().mediaType(),
                fixture.file().sizeBytes(),
                fixture.file().sha256(),
                fixture.file().analysisDisposition(),
                "UTF-8",
                "1".repeat(64));
        VerifiedSourceIndex index = new VerifiedSourceIndex(
                "snapshot:" + "2".repeat(64),
                m1Reference.artifactId(),
                1,
                1,
                0,
                List.of(file),
                List.of(new SourceShardReceipt(
                        "source-shard:" + "3".repeat(64), List.of(file.fileId()), List.of(file.fileId()), "SUCCEEDED", List.of())),
                "VERIFIED");
        StageModuleAddress address = new StageModuleAddress(RUN_ID, 1, "freeze-source", 2, "source-index");
        ModuleArtifactEnvelope envelope = ModuleArtifactEnvelope.write(
                new ModuleArtifactEnvelopeDraft(
                        address,
                        "v2",
                        List.of(fixture.registration(), m1Reference),
                        fixture.controls(),
                        "SUCCEEDED",
                        List.of(),
                        VerifiedSourceIndexArtifact.write(index)),
                policy);
        return store.install(new ModuleInstallRequest(
                        address,
                        "v2",
                        List.of(fixture.registration(), m1Reference),
                        fixture.controls(),
                        "SUCCEEDED",
                        List.of(),
                        List.of(new CanonicalModulePayload(
                                "verified-source-index.json",
                                policy.key().artifactType(),
                                policy.key().schemaVersion(),
                                envelope.reference().artifactId(),
                                policy.mediaType(),
                                envelope.canonicalUtf8()))))
                .reference();
    }

    private static byte[] analysisRequestJson(Fixture fixture, ArtifactReference frozenRequest) {
        return ("{" +
                "\"approvedFindingRefs\":[],\"artifactPolicyRegistryRef\":" + referenceJson(fixture.controls().artifactPolicyRegistryRef()) + "," +
                "\"candidateSeriesRef\":" + referenceJson(ref("candidate-series", '1')) + "," +
                "\"frozenRepositoryRequestRef\":" + referenceJson(frozenRequest) + "," +
                "\"organizationRegistrySeedRef\":null,\"parentCandidateRef\":null," +
                "\"profileBundleRef\":" + referenceJson(fixture.request().capabilityProfileRef()) + "," +
                "\"promptBundleRef\":" + referenceJson(ref("prompt-bundle", '2')) + "," +
                "\"readerCandidateRound\":\"ROUND_1\",\"resourceBudgetRef\":" + referenceJson(fixture.request().resourceBudgetRef()) + "," +
                "\"schemaBundleRef\":" + referenceJson(ref("schema-bundle", '3')) + "," +
                "\"schemaVersion\":\"analysis-run-request-v2\",\"sourceRegistrationId\":\"" + fixture.request().sourceRegistrationId() + "\"," +
                "\"toolchainRef\":" + referenceJson(ref("toolchain", '4')) + "}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] frozenRequestJson(Fixture fixture) {
        return ("{" +
                "\"capabilityProfileRef\":" + referenceJson(fixture.request().capabilityProfileRef()) + "," +
                "\"captureReceiptRef\":" + referenceJson(fixture.captureReceipt()) + "," +
                "\"expectedOrigin\":{\"kind\":\"LOCAL_GIT\",\"repositoryUrl\":\"" + fixture.request().originRepositoryUrl() + "\",\"revision40\":\"" + fixture.request().originRevision() + "\"}," +
                "\"inventoryScope\":{\"declaredPathCount\":1,\"kind\":\"COMPLETE_CAPTURE\",\"scopeRoot\":null}," +
                "\"resourceBudgetRef\":" + referenceJson(fixture.request().resourceBudgetRef()) + "," +
                "\"schemaVersion\":\"frozen-repository-request-v2\",\"snapshotManifestRef\":" + referenceJson(fixture.snapshotManifest()) + "," +
                "\"verificationPolicyRef\":" + referenceJson(fixture.request().verificationPolicyRef()) + "}").getBytes(StandardCharsets.UTF_8);
    }

    private static String referenceJson(ArtifactReference reference) {
        return "{\"artifactId\":\"" + reference.artifactId() + "\",\"sha256\":\"" + reference.sha256() + "\"}";
    }

    private static String referenceJson(ArtifactPolicyRegistryReference reference) {
        return "{\"artifactId\":\"" + reference.artifactId() + "\",\"sha256\":\"" + reference.sha256() + "\"}";
    }

    private static CanonicalArtifactPolicy policy(String type, String schema, String prefix, String envelopeKind) {
        return new CanonicalArtifactPolicy(
                new ArtifactPolicyKey(type, schema),
                prefix,
                "CANONICAL_JSONL".equals(envelopeKind) ? "application/x-ndjson" : "application/json",
                envelopeKind,
                false,
                "METADATA_ONLY");
    }

    private static CanonicalArtifactPolicyRegistry registry(
            ArtifactPolicyRegistryReference reference, CanonicalArtifactPolicy... policies) {
        Map<ArtifactPolicyKey, CanonicalArtifactPolicy> entries = java.util.Arrays.stream(policies)
                .collect(java.util.stream.Collectors.toMap(CanonicalArtifactPolicy::key, policy -> policy));
        return new CanonicalArtifactPolicyRegistry() {
            @Override
            public ArtifactPolicyRegistryReference reference() {
                return reference;
            }

            @Override
            public CanonicalArtifactPolicy resolve(ArtifactPolicyKey key) {
                CanonicalArtifactPolicy policy = entries.get(key);
                if (policy == null) {
                    throw new IllegalArgumentException("fixture policy is missing");
                }
                return policy;
            }
        };
    }

    private static ArtifactReference ref(String prefix, char character) {
        String digest = String.valueOf(character).repeat(64);
        return new ArtifactReference(prefix + ":" + digest, digest);
    }

    private static ArtifactReference contentReference(String prefix, byte[] bytes) throws Exception {
        String digest = sha256(bytes);
        return new ArtifactReference(prefix + ":" + digest, digest);
    }

    private static ArtifactReference analysisRequestReference(byte[] bytes) throws Exception {
        java.security.MessageDigest digest = MessageDigest.getInstance("SHA-256");
        frame(digest, "analysis-run-request-id-v2".getBytes(StandardCharsets.UTF_8));
        frame(digest, bytes);
        return new ArtifactReference(
                "run-request:" + java.util.HexFormat.of().formatHex(digest.digest()), sha256(bytes));
    }

    private static void frame(java.security.MessageDigest digest, byte[] bytes) {
        digest.update(java.nio.ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        digest.update(bytes);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Fixture(
            ArtifactControls controls,
            CanonicalArtifactPolicy requestPolicy,
            CanonicalArtifactPolicy indexPolicy,
            AdmittedSourceRequest request,
            AdmittedSourceFile file,
            ArtifactReference registration,
            ArtifactReference captureReceipt,
            ArtifactReference snapshotManifest) {}
}
