package com.linguan.codemd.target.stage01.requestadmission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.ArtifactControls;
import com.linguan.codemd.target.artifacts.ArtifactPolicyKey;
import com.linguan.codemd.target.artifacts.ArtifactPolicyRegistryReference;
import com.linguan.codemd.target.artifacts.ArtifactStoreLimits;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicy;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicyRegistry;
import com.linguan.codemd.target.artifacts.CanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.CanonicalJsonCodec;
import com.linguan.codemd.target.artifacts.FileSystemCanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.ImmutableBytes;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelope;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.ReopenedModulePublication;
import com.linguan.codemd.target.artifacts.RunStoreBootstrap;
import com.linguan.codemd.target.artifacts.RunStoreHandle;
import com.linguan.codemd.target.artifacts.StageModuleAddress;
import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import com.linguan.codemd.target.stage01.capture.LocalGitCaptureReceipt;
import com.linguan.codemd.target.stage01.capture.LocalGitCaptureResult;
import com.linguan.codemd.target.stage01.capture.LocalGitSnapshotEntry;
import com.linguan.codemd.target.stage01.capture.SourceRegistration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Stage01FrozenRequestAdmissionTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CanonicalJsonCodec CANONICAL = new CanonicalJsonCodec();

    @TempDir Path temporaryDirectory;

    @Test
    void admitsTheCompleteCapturedInventoryAsSortedRootlessFiles() {
        CaptureReceiptView capture = captureView();
        ProfileView profile = new ProfileView(
                reference("capability-profile", 'c'), reference("resource-budget", 'b'), 10, 10_000, 5_000);
        byte[] frozenRequestBytes = frozenRequest(capture, profile);
        ArtifactReference frozenRequestRef = reference("frozen-request", frozenRequestBytes);
        byte[] analysisRunRequestBytes = analysisRunRequest(capture.sourceRegistration().sourceRegistrationId(), frozenRequestRef);

        AdmittedSourceRequest admitted = new FrozenRequestAdmission().admit(
                analysisRunRequestBytes,
                capture.withFrozenRepositoryRequest(frozenRequestRef, ImmutableBytes.copyOf(frozenRequestBytes)),
                profile);

        assertThat(admitted.sourceRegistrationId()).isEqualTo(capture.sourceRegistration().sourceRegistrationId());
        assertThat(admitted.originRevision()).isEqualTo("a".repeat(40));
        assertThat(admitted.repositoryCompletionEligible()).isTrue();
        assertThat(admitted.files()).extracting(AdmittedSourceFile::path)
                .containsExactly("assets/logo.bin", "run.sh", "src/App.java");
        assertThat(admitted.analyzableTextFileIds()).hasSize(2);
        assertThat(admitted.nonAnalyzableMediaFileIds()).hasSize(1);
        assertThat(admitted.analyzableTextFileIds())
                .doesNotContainAnyElementsOf(admitted.nonAnalyzableMediaFileIds());
        assertThat(List.of(AdmittedSourceRequest.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .doesNotContain("repositoryPath", "snapshotRoot", "storagePath");
    }

    @Test
    void installsAndFreshlyReopensTheExactM1ArtifactWithoutAnInMemoryHandoff() {
        CaptureReceiptView capture = captureView();
        ProfileView profile = new ProfileView(
                reference("capability-profile", 'c'), reference("resource-budget", 'b'), 10, 10_000, 5_000);
        byte[] frozenRequestBytes = frozenRequest(capture, profile);
        ArtifactReference frozenRequestRef = reference("frozen-request", frozenRequestBytes);
        CaptureReceiptView boundCapture =
                capture.withFrozenRepositoryRequest(frozenRequestRef, ImmutableBytes.copyOf(frozenRequestBytes));
        byte[] analysisRunRequestBytes = analysisRunRequest(capture.sourceRegistration().sourceRegistrationId(), frozenRequestRef);
        ArtifactReference analysisRunRequestRef = reference("analysis-run-request", analysisRunRequestBytes);
        CanonicalArtifactPolicy policy = new CanonicalArtifactPolicy(
                new ArtifactPolicyKey("STAGE01_ADMITTED_SOURCE_REQUEST", "stage01-admitted-source-request-v2"),
                "source-request",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false,
                "METADATA_ONLY");
        ArtifactPolicyRegistryReference registryReference =
                new ArtifactPolicyRegistryReference(
                        "artifact-policy-registry:" + "9".repeat(64), "9".repeat(64));
        CanonicalArtifactPolicyRegistry policies = policyRegistry(policy, registryReference);
        FrozenRequestAdmissionPublicationInput publication =
                new FrozenRequestAdmissionPublicationInput(
                        new StageModuleAddress(
                                "analysis-run:" + "f".repeat(64), 1, "freeze-source", 1, "request-admission"),
                        "v2",
                        analysisRunRequestRef,
                        new ArtifactControls(
                                "1".repeat(64),
                                "2".repeat(64),
                                "3".repeat(64),
                                null,
                                registryReference));

        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore store =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, CANONICAL, policies, new ArtifactStoreLimits(1, 16_384, 16_384, 4));
            ModulePublicationReference reference =
                    new FrozenRequestAdmission()
                            .admitAndInstall(
                                    analysisRunRequestBytes, boundCapture, profile, publication, policies, store);
            ReopenedModulePublication reopened = store.reopen(reference);
            ModuleArtifactEnvelope envelope =
                    ModuleArtifactEnvelope.parse(reopened.payloads().get(0).canonicalUtf8(), policy);
            AdmittedSourceRequest replayed = AdmittedSourceRequestArtifact.parse(envelope.payload());

            assertThat(reopened.payloads()).hasSize(1);
            assertThat(replayed).isEqualTo(new FrozenRequestAdmission().admit(analysisRunRequestBytes, boundCapture, profile));
            assertThat(envelope.draft().upstreamArtifacts())
                    .extracting(ArtifactReference::artifactId)
                    .containsExactlyElementsOf(
                            envelope.draft().upstreamArtifacts().stream()
                                    .map(ArtifactReference::artifactId)
                                    .sorted()
                                    .toList());
            assertThat(envelope.draft().upstreamArtifacts()).hasSize(8);
        }
    }

    @Test
    void refusesAPersistedTextPartitionThatDoesNotCloseToThePersistedFileSet() {
        CaptureReceiptView capture = captureView();
        ProfileView profile = new ProfileView(
                reference("capability-profile", 'c'), reference("resource-budget", 'b'), 10, 10_000, 5_000);
        byte[] frozenRequestBytes = frozenRequest(capture, profile);
        ArtifactReference frozenRequestRef = reference("frozen-request", frozenRequestBytes);
        byte[] analysisRunRequestBytes = analysisRunRequest(capture.sourceRegistration().sourceRegistrationId(), frozenRequestRef);
        AdmittedSourceRequest admitted =
                new FrozenRequestAdmission()
                        .admit(
                                analysisRunRequestBytes,
                                capture.withFrozenRepositoryRequest(
                                        frozenRequestRef, ImmutableBytes.copyOf(frozenRequestBytes)),
                                profile);
        ObjectNode tampered = AdmittedSourceRequestArtifact.write(admitted);
        tampered.putArray("analyzableTextFileIds");

        AdmissionException exception =
                assertThrows(AdmissionException.class, () -> AdmittedSourceRequestArtifact.parse(tampered));

        assertThat(exception.code()).isEqualTo("REQUEST_SCHEMA_INVALID");
    }

    private static CaptureReceiptView captureView() {
        ArtifactReference manifestReference = reference("snapshot-manifest", 'a');
        ArtifactReference captureReceiptReference = reference("capture-receipt", 'b');
        ArtifactReference capturePolicyReference = reference("capture-policy", 'c');
        LocalGitCaptureReceipt receipt = new LocalGitCaptureReceipt(
                captureReceiptReference.artifactId(),
                "https://example.invalid/customer/repository.git",
                "SHA1",
                "a".repeat(40),
                "b".repeat(40),
                "snapshot:" + "d".repeat(64),
                manifestReference,
                3,
                2,
                1,
                0,
                "FORBIDDEN",
                "DISABLED",
                capturePolicyReference);
        SourceRegistration registration = new SourceRegistration(
                "source-registration:" + "e".repeat(64),
                receipt.declaredRepositoryIdentity(),
                receipt.commitId(),
                receipt.snapshotId(),
                manifestReference,
                captureReceiptReference,
                3);
        LocalGitCaptureResult result = new LocalGitCaptureResult(
                receipt,
                registration,
                List.of(
                        entry("src/App.java", "100644", AnalysisDisposition.ANALYZABLE_TEXT),
                        entry("assets/logo.bin", "100644", AnalysisDisposition.NON_ANALYZABLE_MEDIA),
                        entry("run.sh", "100755", AnalysisDisposition.ANALYZABLE_TEXT)));
        return new CaptureReceiptView(result);
    }

    private static LocalGitSnapshotEntry entry(String path, String mode, AnalysisDisposition disposition) {
        return new LocalGitSnapshotEntry(
                path,
                mode,
                "d".repeat(40),
                10,
                "e".repeat(64),
                disposition == AnalysisDisposition.ANALYZABLE_TEXT ? "text/plain" : "application/octet-stream",
                disposition,
                disposition == AnalysisDisposition.ANALYZABLE_TEXT ? "UTF-8" : null);
    }

    private static byte[] frozenRequest(CaptureReceiptView capture, ProfileView profile) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", "frozen-repository-request-v2");
        ObjectNode origin = root.putObject("expectedOrigin");
        origin.put("kind", "LOCAL_GIT");
        origin.put("repositoryUrl", capture.sourceRegistration().declaredRepositoryIdentity());
        origin.put("revision40", capture.sourceRegistration().commitId());
        reference(root, "captureReceiptRef", capture.captureReceipt().captureReceiptId(), capture.captureReceiptReference().sha256());
        reference(root, "snapshotManifestRef", capture.captureReceipt().snapshotManifestRef());
        ObjectNode scope = root.putObject("inventoryScope");
        scope.put("kind", "COMPLETE_CAPTURE");
        scope.putNull("scopeRoot");
        scope.put("declaredPathCount", 3);
        reference(root, "verificationPolicyRef", reference("verification-policy", 'd'));
        reference(root, "capabilityProfileRef", profile.capabilityProfileRef());
        reference(root, "resourceBudgetRef", profile.resourceBudgetRef());
        return canonical(root);
    }

    private static byte[] analysisRunRequest(String sourceRegistrationId, ArtifactReference frozenRequestRef) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", "analysis-run-request-v2");
        root.put("sourceRegistrationId", sourceRegistrationId);
        reference(root, "frozenRepositoryRequestRef", frozenRequestRef);
        reference(root, "profileBundleRef", reference("profile-bundle", 'a'));
        reference(root, "resourceBudgetRef", reference("resource-budget", 'b'));
        reference(root, "toolchainRef", reference("toolchain", 'c'));
        reference(root, "schemaBundleRef", reference("schema-bundle", 'd'));
        reference(root, "promptBundleRef", reference("prompt-bundle", 'e'));
        root.putNull("organizationRegistrySeedRef");
        reference(root, "artifactPolicyRegistryRef", reference("artifact-policy-registry", 'f'));
        reference(root, "candidateSeriesRef", reference("candidate-series", 'a'));
        root.put("readerCandidateRound", "ROUND_1");
        root.putNull("parentCandidateRef");
        root.putArray("approvedFindingRefs");
        return canonical(root);
    }

    private static ArtifactReference reference(String prefix, char fill) {
        String digest = String.valueOf(fill).repeat(64);
        return new ArtifactReference(prefix + ":" + digest, digest);
    }

    private static ArtifactReference reference(String prefix, byte[] bytes) {
        String digest = sha256(bytes);
        return new ArtifactReference(prefix + ":" + digest, digest);
    }

    private static CanonicalArtifactPolicyRegistry policyRegistry(
            CanonicalArtifactPolicy policy, ArtifactPolicyRegistryReference registryReference) {
        return new CanonicalArtifactPolicyRegistry() {
            @Override
            public ArtifactPolicyRegistryReference reference() {
                return registryReference;
            }

            @Override
            public CanonicalArtifactPolicy resolve(ArtifactPolicyKey key) {
                if (!policy.key().equals(key)) {
                    throw new IllegalArgumentException("unexpected fixture policy lookup");
                }
                return policy;
            }
        };
    }

    private static void reference(ObjectNode root, String field, ArtifactReference reference) {
        reference(root, field, reference.artifactId(), reference.sha256());
    }

    private static void reference(ObjectNode root, String field, String artifactId, String sha256) {
        ObjectNode node = root.putObject(field);
        node.put("artifactId", artifactId);
        node.put("sha256", sha256);
    }

    private static byte[] canonical(ObjectNode node) {
        return CANONICAL.canonicalize(node).copyToByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
