package com.linguan.codemd.target.stage01.requestadmission;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.target.artifacts.ArtifactControls;
import com.linguan.codemd.target.artifacts.ArtifactPolicyKey;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicy;
import com.linguan.codemd.target.artifacts.CanonicalArtifactPolicyRegistry;
import com.linguan.codemd.target.artifacts.CanonicalJsonCodec;
import com.linguan.codemd.target.artifacts.CanonicalModuleArtifactStore;
import com.linguan.codemd.target.artifacts.CanonicalModulePayload;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelope;
import com.linguan.codemd.target.artifacts.ModuleArtifactEnvelopeDraft;
import com.linguan.codemd.target.artifacts.ModuleInstallRequest;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.ReopenedModulePublication;
import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import com.linguan.codemd.target.stage01.capture.LocalGitSnapshotEntry;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Strict M1 admission from an exact analysis request to a rootless source-file denominator. */
public final class FrozenRequestAdmission {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());
    private static final CanonicalJsonCodec CANONICAL = new CanonicalJsonCodec();
    private static final Set<String> ANALYSIS_FIELDS = Set.of(
            "schemaVersion",
            "sourceRegistrationId",
            "frozenRepositoryRequestRef",
            "profileBundleRef",
            "resourceBudgetRef",
            "toolchainRef",
            "schemaBundleRef",
            "promptBundleRef",
            "organizationRegistrySeedRef",
            "artifactPolicyRegistryRef",
            "candidateSeriesRef",
            "readerCandidateRound",
            "parentCandidateRef",
            "approvedFindingRefs");
    private static final Set<String> FROZEN_FIELDS = Set.of(
            "schemaVersion",
            "expectedOrigin",
            "captureReceiptRef",
            "snapshotManifestRef",
            "inventoryScope",
            "verificationPolicyRef",
            "capabilityProfileRef",
            "resourceBudgetRef");

    public AdmittedSourceRequest admit(byte[] exactRequestJson, CaptureReceiptView capture, ProfileView profile) {
        if (exactRequestJson == null || capture == null || profile == null) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "admission input must be present");
        }
        JsonNode analysis = parseCanonical(exactRequestJson, ANALYSIS_FIELDS);
        requireText(analysis, "schemaVersion", "analysis-run-request-v2");
        requireText(analysis, "readerCandidateRound", "ROUND_1");
        requireNull(analysis, "organizationRegistrySeedRef");
        requireNull(analysis, "parentCandidateRef");
        requireEmptyArray(analysis, "approvedFindingRefs");
        String sourceRegistrationId = text(analysis, "sourceRegistrationId");
        ArtifactReference frozenReference = reference(analysis, "frozenRepositoryRequestRef");
        if (capture.frozenRepositoryRequestRef() == null || !capture.frozenRepositoryRequestRef().equals(frozenReference)
                || !frozenReference.sha256().equals(sha256(capture.frozenRepositoryRequestBytes().copyToByteArray()))) {
            throw new AdmissionException("CAPTURE_IDENTITY_INVALID", "frozen repository request reference is not bound to its bytes");
        }
        if (!sourceRegistrationId.equals(capture.sourceRegistration().sourceRegistrationId())) {
            throw new AdmissionException("SOURCE_REGISTRATION_NOT_FOUND", "request does not name the registered capture");
        }
        ArtifactReference analysisBudgetReference = reference(analysis, "resourceBudgetRef");
        if (!analysisBudgetReference.equals(profile.resourceBudgetRef())) {
            throw new AdmissionException("PROFILE_REFERENCE_INVALID", "analysis request budget differs from profile");
        }

        JsonNode frozen = parseCanonical(capture.frozenRepositoryRequestBytes().copyToByteArray(), FROZEN_FIELDS);
        requireText(frozen, "schemaVersion", "frozen-repository-request-v2");
        ArtifactReference verificationPolicyRef = verifyFrozenBindings(frozen, capture, profile);
        InventoryScope scope = inventoryScope(frozen.get("inventoryScope"));
        List<AdmittedSourceFile> files = admittedFiles(capture.snapshotManifest(), profile);
        if (scope.declaredPathCount() != files.size()) {
            throw new AdmissionException("CAPTURE_IDENTITY_INVALID", "frozen inventory count differs from capture");
        }
        boolean eligible = scope.kind() == InventoryScope.Kind.COMPLETE_CAPTURE;
        List<String> textFileIds = files.stream()
                .filter(file -> file.analysisDisposition() == AnalysisDisposition.ANALYZABLE_TEXT)
                .map(AdmittedSourceFile::fileId)
                .sorted(FrozenRequestAdmission::compareUtf8)
                .toList();
        List<String> mediaFileIds = files.stream()
                .filter(file -> file.analysisDisposition() == AnalysisDisposition.NON_ANALYZABLE_MEDIA)
                .map(AdmittedSourceFile::fileId)
                .sorted(FrozenRequestAdmission::compareUtf8)
                .toList();
        return new AdmittedSourceRequest(
                "source-request:" + framedDigest("stage01-admitted-source-request-id-v2", exactRequestJson),
                sourceRegistrationId,
                capture.sourceRegistration().declaredRepositoryIdentity(),
                capture.sourceRegistration().commitId(),
                scope,
                eligible,
                files.size(),
                files,
                textFileIds,
                mediaFileIds,
                profile.capabilityProfileRef(),
                profile.resourceBudgetRef(),
                verificationPolicyRef);
    }

    /** Admits then persists M1; M2 receives only the returned fresh-reopened module reference. */
    public ModulePublicationReference admitAndInstall(
            byte[] exactRequestJson,
            CaptureReceiptView capture,
            ProfileView profile,
            FrozenRequestAdmissionPublicationInput publication,
            CanonicalArtifactPolicyRegistry policies,
            CanonicalModuleArtifactStore store) {
        if (publication == null || policies == null || store == null) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 publication input must be present");
        }
        if (!publication.analysisRunRequestRef().sha256().equals(sha256(exactRequestJson))) {
            throw new AdmissionException("CAPTURE_IDENTITY_INVALID", "analysis request reference differs from bytes");
        }
        if (!publication.controls().artifactPolicyRegistryRef().equals(policies.reference())) {
            throw new AdmissionException("PROFILE_REFERENCE_INVALID", "M1 controls name another policy registry");
        }
        AdmittedSourceRequest admitted = admit(exactRequestJson, capture, profile);
        CanonicalArtifactPolicy policy =
                policies.resolve(
                        new ArtifactPolicyKey(
                                "STAGE01_ADMITTED_SOURCE_REQUEST", "stage01-admitted-source-request-v2"));
        List<ArtifactReference> upstream =
                sortedUpstreamReferences(publication.analysisRunRequestRef(), capture, admitted);
        String status = admitted.repositoryCompletionEligible() ? "SUCCEEDED" : "SUCCEEDED_WITH_GAPS";
        ModuleArtifactEnvelope envelope =
                ModuleArtifactEnvelope.write(
                        new ModuleArtifactEnvelopeDraft(
                                publication.address(),
                                publication.moduleVersion(),
                                upstream,
                                publication.controls(),
                                status,
                                List.of(),
                                AdmittedSourceRequestArtifact.write(admitted)),
                        policy);
        ModuleInstallRequest request =
                new ModuleInstallRequest(
                        publication.address(),
                        publication.moduleVersion(),
                        upstream,
                        publication.controls(),
                        status,
                        List.of(),
                        List.of(
                                new CanonicalModulePayload(
                                        "admitted-source-request.json",
                                        policy.key().artifactType(),
                                        policy.key().schemaVersion(),
                                        envelope.reference().artifactId(),
                                        policy.mediaType(),
                                        envelope.canonicalUtf8())));
        ModulePublicationReference reference = store.install(request).reference();
        ReopenedModulePublication reopened = store.reopen(reference);
        if (reopened.payloads().size() != 1) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 reopened payload count is invalid");
        }
        ModuleArtifactEnvelope replay =
                ModuleArtifactEnvelope.parse(reopened.payloads().get(0).canonicalUtf8(), policy);
        if (!AdmittedSourceRequestArtifact.parse(replay.payload()).equals(admitted)) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 reopen differs from admitted request");
        }
        return reference;
    }

    private static ArtifactReference verifyFrozenBindings(
            JsonNode frozen, CaptureReceiptView capture, ProfileView profile) {
        JsonNode origin = frozen.get("expectedOrigin");
        requireExactFields(origin, Set.of("kind", "repositoryUrl", "revision40"));
        requireText(origin, "kind", "LOCAL_GIT");
        requireText(origin, "repositoryUrl", capture.sourceRegistration().declaredRepositoryIdentity());
        requireText(origin, "revision40", capture.sourceRegistration().commitId());
        if (!reference(frozen, "captureReceiptRef").equals(capture.captureReceiptReference())
                || !reference(frozen, "snapshotManifestRef").equals(capture.captureReceipt().snapshotManifestRef())
                || !reference(frozen, "capabilityProfileRef").equals(profile.capabilityProfileRef())
                || !reference(frozen, "resourceBudgetRef").equals(profile.resourceBudgetRef())) {
            throw new AdmissionException("CAPTURE_IDENTITY_INVALID", "frozen request binding differs from capture or profile");
        }
        return reference(frozen, "verificationPolicyRef");
    }

    private static List<ArtifactReference> sortedUpstreamReferences(
            ArtifactReference analysisRunRequestRef, CaptureReceiptView capture, AdmittedSourceRequest admitted) {
        List<ArtifactReference> upstream = new ArrayList<>(List.of(
                analysisRunRequestRef,
                capture.frozenRepositoryRequestRef(),
                capture.captureReceiptReference(),
                capture.captureReceipt().snapshotManifestRef(),
                capture.captureResult().sourceRegistrationReference(),
                admitted.capabilityProfileRef(),
                admitted.resourceBudgetRef(),
                admitted.verificationPolicyRef()));
        upstream.sort(Comparator.comparing(ArtifactReference::artifactId));
        for (int index = 1; index < upstream.size(); index++) {
            if (upstream.get(index - 1).artifactId().equals(upstream.get(index).artifactId())) {
                throw new AdmissionException("CAPTURE_IDENTITY_INVALID", "M1 upstream artifact references overlap");
            }
        }
        return List.copyOf(upstream);
    }

    private static List<AdmittedSourceFile> admittedFiles(List<LocalGitSnapshotEntry> entries, ProfileView profile) {
        if (entries.isEmpty() || entries.size() > profile.maxFiles()) {
            throw new AdmissionException("STAGE01_RESOURCE_LIMIT_EXCEEDED", "file count violates profile");
        }
        List<LocalGitSnapshotEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(LocalGitSnapshotEntry::path, FrozenRequestAdmission::compareUtf8));
        Set<String> paths = new HashSet<>();
        long totalBytes = 0;
        List<AdmittedSourceFile> admitted = new ArrayList<>();
        for (LocalGitSnapshotEntry entry : sorted) {
            AdmittedSourceFile.validatePath(entry.path());
            if (!paths.add(entry.path())) {
                throw new AdmissionException("DUPLICATE_SOURCE_PATH", "capture has duplicate source paths");
            }
            if (entry.sizeBytes() > profile.maxFileBytes() || Long.MAX_VALUE - totalBytes < entry.sizeBytes()) {
                throw new AdmissionException("STAGE01_RESOURCE_LIMIT_EXCEEDED", "file size violates profile");
            }
            totalBytes += entry.sizeBytes();
            if (totalBytes > profile.maxTotalBytes()) {
                throw new AdmissionException("STAGE01_RESOURCE_LIMIT_EXCEEDED", "total source size violates profile");
            }
            admitted.add(AdmittedSourceFile.fromCapture(entry));
        }
        return List.copyOf(admitted);
    }

    private static InventoryScope inventoryScope(JsonNode node) {
        requireExactFields(node, Set.of("kind", "scopeRoot", "declaredPathCount"));
        String kind = text(node, "kind");
        JsonNode scopeRoot = node.get("scopeRoot");
        return new InventoryScope(
                InventoryScope.Kind.valueOf(kind),
                scopeRoot == null || scopeRoot.isNull() ? null : text(node, "scopeRoot"),
                integer(node, "declaredPathCount"));
    }

    private static JsonNode parseCanonical(byte[] bytes, Set<String> requiredFields) {
        try {
            JsonNode node = JSON.readTree(bytes);
            requireExactFields(node, requiredFields);
            if (!Arrays.equals(bytes, CANONICAL.canonicalize(node).copyToByteArray())) {
                throw new AdmissionException("REQUEST_SCHEMA_INVALID", "JSON input is not canonical");
            }
            return node;
        } catch (java.io.IOException exception) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "JSON input cannot be parsed");
        }
    }

    private static ArtifactReference reference(JsonNode node, String field) {
        JsonNode reference = node.get(field);
        requireExactFields(reference, Set.of("artifactId", "sha256"));
        return new ArtifactReference(text(reference, "artifactId"), text(reference, "sha256"));
    }

    private static void requireExactFields(JsonNode node, Set<String> required) {
        if (node == null || !node.isObject()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "JSON record must be an object");
        }
        Set<String> actual = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(required)) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "JSON fields are not exact");
        }
    }

    private static void requireText(JsonNode node, String field, String expected) {
        if (!expected.equals(text(node, field))) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " differs from the required value");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " must be text");
        }
        return value.textValue();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " must be an integer");
        }
        return value.intValue();
    }

    private static void requireNull(JsonNode node, String field) {
        if (node.get(field) == null || !node.get(field).isNull()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " must be null");
        }
    }

    private static void requireEmptyArray(JsonNode node, String field) {
        if (node.get(field) == null || !node.get(field).isArray() || !node.get(field).isEmpty()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", field + " must be an empty array");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String framedDigest(String domain, byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeFrame(output, domain.getBytes(StandardCharsets.UTF_8));
        for (byte[] value : values) {
            writeFrame(output, value);
        }
        return sha256(output.toByteArray());
    }

    private static void writeFrame(ByteArrayOutputStream output, byte[] value) {
        output.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(value.length).array());
        output.writeBytes(value);
    }

    private static int compareUtf8(String left, String right) {
        return Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
