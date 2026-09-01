package com.linguan.codemd.target.stage01.requestadmission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.linguan.codemd.target.artifacts.FileSystemCanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.ImmutableBytes;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelope;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelopeDraft;
import com.linguan.codemd.target.artifacts.ModuleInstallRequest;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.ReopenedModulePublication;
import com.linguan.codemd.target.artifacts.RunStoreBootstrap;
import com.linguan.codemd.target.artifacts.RunStoreHandle;
import com.linguan.codemd.target.artifacts.StageModuleAddress;
import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import com.linguan.codemd.target.stage01.sourceindex.RegisteredSnapshotRegistry;
import com.linguan.codemd.target.stage01.sourceindex.RegisteredSnapshotRegistry.RegisteredSnapshotHandle;
import com.linguan.codemd.target.stage01.sourceindex.RegisteredSourceFileMetadata;
import com.linguan.codemd.target.stage01.sourceindex.SourceIndexPublicationInput;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceIndex;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceIndexArtifact;
import com.linguan.codemd.target.stage01.sourceindex.VerifiedSourceIndexer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Stage01VerifiedSourceIndexerTest {
    private static final String RUN_ID = "analysis-run:" + "a".repeat(64);

    @TempDir Path temporaryDirectory;

    @Test
    void freshlyReopensM1AndVerifiesBothTextAndMediaThroughAnOpaqueSnapshotHandle()
            throws Exception {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicy requestPolicy = policy(
                "STAGE01_ADMITTED_SOURCE_REQUEST", "stage01-admitted-source-request-v2", "source-request");
        CanonicalArtifactPolicy indexPolicy = policy(
                "STAGE01_VERIFIED_SOURCE_INDEX", "stage01-verified-source-index-v2", "source-index");
        ArtifactPolicyRegistryReference policyReference =
                new ArtifactPolicyRegistryReference(
                        "artifact-policy-registry:" + "b".repeat(64), "b".repeat(64));
        CanonicalArtifactPolicyRegistry policies = registry(policyReference, requestPolicy, indexPolicy);
        ArtifactControls controls =
                new ArtifactControls(
                        "1".repeat(64), "2".repeat(64), "3".repeat(64), null, policyReference);
        byte[] media = {0, 1, 2};
        byte[] java = "class App {}\n".getBytes(StandardCharsets.UTF_8);
        AdmittedSourceFile mediaFile = AdmittedSourceFile.fromWire(
                "assets/logo.bin",
                "100644",
                "application/octet-stream",
                media.length,
                sha256(media),
                AnalysisDisposition.NON_ANALYZABLE_MEDIA,
                null);
        AdmittedSourceFile javaFile = AdmittedSourceFile.fromWire(
                "src/App.java",
                "100644",
                "text/x-java-source",
                java.length,
                sha256(java),
                AnalysisDisposition.ANALYZABLE_TEXT,
                "UTF-8");
        List<AdmittedSourceFile> files = List.of(mediaFile, javaFile);
        AdmittedSourceRequest request = new AdmittedSourceRequest(
                "source-request:" + "c".repeat(64),
                "source-registration:" + "d".repeat(64),
                "https://example.invalid/customer.git",
                "e".repeat(40),
                new InventoryScope(InventoryScope.Kind.COMPLETE_CAPTURE, null, 2),
                true,
                2,
                files,
                List.of(javaFile.fileId()),
                List.of(mediaFile.fileId()),
                reference("capability-profile", 'f'),
                reference("resource-budget", '0'),
                reference("verification-policy", '1'));

        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore store =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(1, 16_384, 16_384, 4));
            ModulePublicationReference m1 = installM1(store, requestPolicy, controls, request);
            RegisteredSnapshotRegistry snapshots = sourceRegistrationId -> new RegisteredSnapshotHandle() {
                @Override
                public String snapshotId() {
                    return "snapshot:" + "9".repeat(64);
                }

                @Override
                public RegisteredSourceFileMetadata inspect(String fileId) {
                    if (javaFile.fileId().equals(fileId)) {
                        return new RegisteredSourceFileMetadata(java.length, sha256Unchecked(java), true);
                    }
                    if (mediaFile.fileId().equals(fileId)) {
                        return new RegisteredSourceFileMetadata(media.length, sha256Unchecked(media), true);
                    }
                    throw new AssertionError("unknown source file ID");
                }

                @Override
                public ImmutableBytes read(String fileId) {
                    if (javaFile.fileId().equals(fileId)) {
                        return ImmutableBytes.copyOf(java);
                    }
                    if (mediaFile.fileId().equals(fileId)) {
                        return ImmutableBytes.copyOf(media);
                    }
                    throw new AssertionError("unknown source file ID");
                }
            };
            ModulePublicationReference m2 =
                    new VerifiedSourceIndexer()
                            .index(
                                    m1,
                                    snapshots,
                                    new SourceIndexPublicationInput(
                                            new StageModuleAddress(
                                                    RUN_ID, 1, "freeze-source", 2, "source-index"),
                                            "v2",
                                            controls),
                                    policies,
                                    store);
            ReopenedModulePublication reopened = store.reopen(m2);
            ModuleArtifactEnvelope envelope =
                    ModuleArtifactEnvelope.parse(reopened.payloads().get(0).canonicalUtf8(), indexPolicy);
            VerifiedSourceIndex verified = VerifiedSourceIndexArtifact.parse(envelope.payload());

            assertThat(verified.requestArtifactId())
                    .isEqualTo(store.reopen(m1).payloads().get(0).descriptor().artifactId());
            assertThat(verified.verifiedRegularFileCount()).isEqualTo(2);
            assertThat(verified.analyzableTextFileCount()).isEqualTo(1);
            assertThat(verified.nonAnalyzableMediaFileCount()).isEqualTo(1);
            assertThat(verified.verifiedFiles())
                    .filteredOn(file -> file.analysisDisposition() == AnalysisDisposition.ANALYZABLE_TEXT)
                    .singleElement()
                    .satisfies(file -> assertThat(file.lineIndexDigest()).isNotNull());
            assertThat(verified.verifiedFiles())
                    .filteredOn(file -> file.analysisDisposition() == AnalysisDisposition.NON_ANALYZABLE_MEDIA)
                    .singleElement()
                    .satisfies(file -> assertThat(file.lineIndexDigest()).isNull());
        }
    }

    @Test
    void rejectsAByteSourceWhoseIdentityChangesBetweenPreAndPostReadChecks() throws Exception {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicy requestPolicy = policy(
                "STAGE01_ADMITTED_SOURCE_REQUEST", "stage01-admitted-source-request-v2", "source-request");
        CanonicalArtifactPolicy indexPolicy = policy(
                "STAGE01_VERIFIED_SOURCE_INDEX", "stage01-verified-source-index-v2", "source-index");
        ArtifactPolicyRegistryReference policyReference =
                new ArtifactPolicyRegistryReference(
                        "artifact-policy-registry:" + "b".repeat(64), "b".repeat(64));
        CanonicalArtifactPolicyRegistry policies = registry(policyReference, requestPolicy, indexPolicy);
        ArtifactControls controls =
                new ArtifactControls("1".repeat(64), "2".repeat(64), "3".repeat(64), null, policyReference);
        byte[] java = "class Stable {}\n".getBytes(StandardCharsets.UTF_8);
        AdmittedSourceFile javaFile = AdmittedSourceFile.fromWire(
                "src/Stable.java",
                "100644",
                "text/x-java-source",
                java.length,
                sha256(java),
                AnalysisDisposition.ANALYZABLE_TEXT,
                "UTF-8");
        AdmittedSourceRequest request = new AdmittedSourceRequest(
                "source-request:" + "c".repeat(64),
                "source-registration:" + "d".repeat(64),
                "https://example.invalid/customer.git",
                "e".repeat(40),
                new InventoryScope(InventoryScope.Kind.COMPLETE_CAPTURE, null, 1),
                true,
                1,
                List.of(javaFile),
                List.of(javaFile.fileId()),
                List.of(),
                reference("capability-profile", 'f'),
                reference("resource-budget", '0'),
                reference("verification-policy", '1'));

        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore store =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(1, 16_384, 16_384, 4));
            ModulePublicationReference m1 = installM1(store, requestPolicy, controls, request);
            RegisteredSnapshotRegistry snapshots = sourceRegistrationId -> new RegisteredSnapshotHandle() {
                private int inspections;

                @Override
                public String snapshotId() {
                    return "snapshot:" + "9".repeat(64);
                }

                @Override
                public RegisteredSourceFileMetadata inspect(String fileId) {
                    inspections++;
                    return new RegisteredSourceFileMetadata(
                            java.length,
                            inspections == 1 ? sha256Unchecked(java) : "0".repeat(64),
                            true);
                }

                @Override
                public ImmutableBytes read(String fileId) {
                    return ImmutableBytes.copyOf(java);
                }
            };

            assertThatThrownBy(() -> new VerifiedSourceIndexer()
                            .index(
                                    m1,
                                    snapshots,
                                    new SourceIndexPublicationInput(
                                            new StageModuleAddress(
                                                    RUN_ID, 1, "freeze-source", 2, "source-index"),
                                            "v2",
                                            controls),
                                    policies,
                                    store))
                    .hasMessageStartingWith("SOURCE_HASH_MISMATCH:");
        }
    }

    private static ModulePublicationReference installM1(
            CanonicalModuleArtifactStore store,
            CanonicalArtifactPolicy policy,
            ArtifactControls controls,
            AdmittedSourceRequest request) {
        StageModuleAddress address = new StageModuleAddress(RUN_ID, 1, "freeze-source", 1, "request-admission");
        ModuleArtifactEnvelope envelope =
                ModuleArtifactEnvelope.write(
                        new ModuleArtifactEnvelopeDraft(
                                address,
                                "v2",
                                List.of(new ArtifactReference(
                                        request.sourceRegistrationId(), "d".repeat(64))),
                                controls,
                                "SUCCEEDED",
                                List.of(),
                                AdmittedSourceRequestArtifact.write(request)),
                        policy);
        return store.install(
                        new ModuleInstallRequest(
                                address,
                                "v2",
                                List.of(),
                                controls,
                                "SUCCEEDED",
                                List.of(),
                                List.of(
                                        new CanonicalModulePayload(
                                                "admitted-source-request.json",
                                                policy.key().artifactType(),
                                                policy.key().schemaVersion(),
                                                envelope.reference().artifactId(),
                                                policy.mediaType(),
                                                envelope.canonicalUtf8()))))
                .reference();
    }

    private static CanonicalArtifactPolicy policy(String type, String schema, String prefix) {
        return new CanonicalArtifactPolicy(
                new ArtifactPolicyKey(type, schema), prefix, "application/json", "MODULE_ARTIFACT_JSON", false, "METADATA_ONLY");
    }

    private static CanonicalArtifactPolicyRegistry registry(
            ArtifactPolicyRegistryReference reference, CanonicalArtifactPolicy... policies) {
        Map<ArtifactPolicyKey, CanonicalArtifactPolicy> byKey =
                java.util.Arrays.stream(policies).collect(java.util.stream.Collectors.toMap(CanonicalArtifactPolicy::key, policy -> policy));
        return new CanonicalArtifactPolicyRegistry() {
            @Override
            public ArtifactPolicyRegistryReference reference() {
                return reference;
            }

            @Override
            public CanonicalArtifactPolicy resolve(ArtifactPolicyKey key) {
                CanonicalArtifactPolicy policy = byKey.get(key);
                if (policy == null) {
                    throw new IllegalArgumentException("unknown fixture policy");
                }
                return policy;
            }
        };
    }

    private static ArtifactReference reference(String prefix, char fill) {
        String digest = String.valueOf(fill).repeat(64);
        return new ArtifactReference(prefix + ":" + digest, digest);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String sha256Unchecked(byte[] bytes) {
        try {
            return sha256(bytes);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
