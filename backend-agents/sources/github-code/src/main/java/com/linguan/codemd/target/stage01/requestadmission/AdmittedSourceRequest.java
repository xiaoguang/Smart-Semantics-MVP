package com.linguan.codemd.target.stage01.requestadmission;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic M1 output: exactly the immutable regular-file denominator M2 must verify. */
public record AdmittedSourceRequest(
        String requestIdentity,
        String sourceRegistrationId,
        String originRepositoryUrl,
        String originRevision,
        InventoryScope inventoryScope,
        boolean repositoryCompletionEligible,
        int declaredPathCount,
        List<AdmittedSourceFile> files,
        List<String> analyzableTextFileIds,
        List<String> nonAnalyzableMediaFileIds,
        ArtifactReference capabilityProfileRef,
        ArtifactReference resourceBudgetRef,
        ArtifactReference verificationPolicyRef) {
    public AdmittedSourceRequest {
        if (requestIdentity == null || !requestIdentity.matches("source-request:[0-9a-f]{64}")
                || sourceRegistrationId == null || !sourceRegistrationId.matches("source-registration:[0-9a-f]{64}")
                || originRepositoryUrl == null || originRepositoryUrl.isBlank()
                || originRevision == null || !originRevision.matches("[0-9a-f]{40}")) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "admitted request identity is invalid");
        }
        Objects.requireNonNull(inventoryScope, "inventoryScope");
        files = List.copyOf(files);
        analyzableTextFileIds = List.copyOf(analyzableTextFileIds);
        nonAnalyzableMediaFileIds = List.copyOf(nonAnalyzableMediaFileIds);
        if (declaredPathCount != files.size()
                || inventoryScope.declaredPathCount() != declaredPathCount
                || files.isEmpty()) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "admitted file count is invalid");
        }
        List<String> expectedText =
                files.stream()
                        .filter(file -> file.analysisDisposition() == AnalysisDisposition.ANALYZABLE_TEXT)
                        .map(AdmittedSourceFile::fileId)
                        .sorted()
                        .toList();
        List<String> expectedMedia =
                files.stream()
                        .filter(file -> file.analysisDisposition() == AnalysisDisposition.NON_ANALYZABLE_MEDIA)
                        .map(AdmittedSourceFile::fileId)
                        .sorted()
                        .toList();
        if (!expectedText.equals(analyzableTextFileIds)
                || !expectedMedia.equals(nonAnalyzableMediaFileIds)
                || !Set.copyOf(expectedText).equals(Set.copyOf(analyzableTextFileIds))
                || !Set.copyOf(expectedMedia).equals(Set.copyOf(nonAnalyzableMediaFileIds))) {
            throw new AdmissionException(
                    "REQUEST_SCHEMA_INVALID", "admitted text/media partitions do not close to files");
        }
        if (repositoryCompletionEligible != (inventoryScope.kind() == InventoryScope.Kind.COMPLETE_CAPTURE)) {
            throw new AdmissionException(
                    "REQUEST_SCHEMA_INVALID", "repository completion eligibility differs from inventory scope");
        }
        Objects.requireNonNull(capabilityProfileRef, "capabilityProfileRef");
        Objects.requireNonNull(resourceBudgetRef, "resourceBudgetRef");
        Objects.requireNonNull(verificationPolicyRef, "verificationPolicyRef");
    }
}
