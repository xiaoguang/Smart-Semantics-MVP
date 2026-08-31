package com.linguan.codemd.stage04;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** A canonical, immutable §4.1 review finding. */
public record CandidateReviewFinding(
        String schemaVersion,
        String findingId,
        String candidateId,
        String validationReceiptId,
        String severity,
        String category,
        String permittedCorrection,
        String findingCode,
        List<String> flowSliceIds,
        List<String> readerItemKeys,
        List<Integer> sectionNumbers,
        String disposition,
        boolean correctiveAddendumRequired) {

    public CandidateReviewFinding {
        ReviewFindingCanonical.Normalized normalized = ReviewFindingCanonical.normalize(schemaVersion, candidateId,
                validationReceiptId, severity, category, permittedCorrection, findingCode, flowSliceIds,
                readerItemKeys, sectionNumbers, disposition, correctiveAddendumRequired);
        Stage04Validation.require(ReviewFindingCanonical.findingId(normalized).equals(findingId));
        schemaVersion = normalized.schemaVersion();
        candidateId = normalized.candidateId();
        validationReceiptId = normalized.validationReceiptId();
        severity = normalized.severity();
        category = normalized.category();
        permittedCorrection = normalized.permittedCorrection();
        findingCode = normalized.findingCode();
        flowSliceIds = normalized.flowSliceIds();
        readerItemKeys = normalized.readerItemKeys();
        sectionNumbers = normalized.sectionNumbers();
        disposition = normalized.disposition();
    }

    static CandidateReviewFinding from(CandidateReviewFindingDraft draft) {
        Stage04Validation.require(draft != null);
        ReviewFindingCanonical.Normalized normalized = ReviewFindingCanonical.normalize(draft.schemaVersion(),
                draft.candidateId(), draft.validationReceiptId(), draft.severity(), draft.category(),
                draft.permittedCorrection(), draft.findingCode(), draft.flowSliceIds(), draft.readerItemKeys(),
                draft.sectionNumbers(), draft.disposition(), draft.correctiveAddendumRequired());
        return new CandidateReviewFinding(normalized.schemaVersion(), ReviewFindingCanonical.findingId(normalized),
                normalized.candidateId(), normalized.validationReceiptId(), normalized.severity(),
                normalized.category(), normalized.permittedCorrection(), normalized.findingCode(),
                normalized.flowSliceIds(), normalized.readerItemKeys(), normalized.sectionNumbers(),
                normalized.disposition(), normalized.correctiveAddendumRequired());
    }
}

/** Shared canonical validation and identity material for the public finding records. */
final class ReviewFindingCanonical {
    static final String FINDING_SCHEMA = "candidate-review-finding-v1";
    static final String FINDING_SET_SCHEMA = "review-finding-set-v1";
    private static final Set<String> SEVERITIES = Set.of("FATAL", "WARNING");
    private static final Set<String> CATEGORIES = Set.of("INTERPRETATION", "TERM", "PRESENTATION",
            "OUT_OF_ROUND_SCOPE");
    private static final Set<String> CORRECTIONS = Set.of("NARROW_OR_DROP", "SELECT_FROZEN_TERM",
            "REORDER_OR_REPHRASE", "NONE");
    private static final Set<String> DISPOSITIONS = Set.of("APPROVED_FOR_ROUND_2", "NOT_ELIGIBLE");
    private static final Pattern FINDING_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");
    private static final ObjectMapper JSON = new ObjectMapper();

    private ReviewFindingCanonical() {
    }

    static Normalized normalize(String schemaVersion, String candidateId, String validationReceiptId,
                                String severity, String category, String permittedCorrection, String findingCode,
                                List<String> flowSliceIds, List<String> readerItemKeys,
                                List<Integer> sectionNumbers, String disposition,
                                boolean correctiveAddendumRequired) {
        Stage04Validation.require(FINDING_SCHEMA.equals(schemaVersion));
        Stage04Validation.require(FilesystemCandidateStore.digestIdentifier(candidateId, "candidate:"));
        Stage04Validation.require(FilesystemCandidateStore.digestIdentifier(validationReceiptId,
                "validation-receipt:"));
        Stage04Validation.require(SEVERITIES.contains(severity));
        Stage04Validation.require(CATEGORIES.contains(category));
        Stage04Validation.require(CORRECTIONS.contains(permittedCorrection));
        Stage04Validation.require(DISPOSITIONS.contains(disposition));
        Stage04Validation.require(findingCode != null && FINDING_CODE.matcher(findingCode).matches());

        List<String> normalizedFlowIds = sortedUniqueIds(flowSliceIds, "flow-slice:");
        List<String> normalizedItemKeys = sortedUniqueReaderKeys(readerItemKeys);
        List<Integer> normalizedSections = sortedUniqueSections(sectionNumbers);
        Stage04Validation.require(!normalizedFlowIds.isEmpty() || !normalizedItemKeys.isEmpty()
                || !normalizedSections.isEmpty());

        if ("INTERPRETATION".equals(category)) {
            Stage04Validation.require("NARROW_OR_DROP".equals(permittedCorrection));
        } else if ("TERM".equals(category)) {
            Stage04Validation.require("SELECT_FROZEN_TERM".equals(permittedCorrection));
        } else if ("PRESENTATION".equals(category)) {
            Stage04Validation.require("REORDER_OR_REPHRASE".equals(permittedCorrection));
        } else {
            Stage04Validation.require("NONE".equals(permittedCorrection)
                    && "NOT_ELIGIBLE".equals(disposition));
        }
        if ("FATAL".equals(severity)) {
            Stage04Validation.require(correctiveAddendumRequired);
        } else {
            Stage04Validation.require(!correctiveAddendumRequired);
        }
        if ("NONE".equals(permittedCorrection)) {
            Stage04Validation.require("NOT_ELIGIBLE".equals(disposition));
        }
        return new Normalized(schemaVersion, candidateId, validationReceiptId, severity, category,
                permittedCorrection, findingCode, normalizedFlowIds, normalizedItemKeys, normalizedSections,
                disposition, correctiveAddendumRequired);
    }

    static String findingId(Normalized value) {
        return "finding:" + Stage04Validation.sha256(canonicalJson(findingMaterial(value)));
    }

    static String findingSetSha256(String roundOneCandidateId, List<CandidateReviewFinding> findings) {
        Stage04Validation.require(FilesystemCandidateStore.digestIdentifier(roundOneCandidateId, "candidate:"));
        List<Map<String, Object>> material = new ArrayList<>();
        for (CandidateReviewFinding finding : findings) {
            Stage04Validation.require(finding != null && roundOneCandidateId.equals(finding.candidateId()));
            material.add(findingMaterial(new Normalized(finding.schemaVersion(), finding.candidateId(),
                    finding.validationReceiptId(), finding.severity(), finding.category(),
                    finding.permittedCorrection(), finding.findingCode(), finding.flowSliceIds(),
                    finding.readerItemKeys(), finding.sectionNumbers(), finding.disposition(),
                    finding.correctiveAddendumRequired())));
        }
        return Stage04Validation.sha256(canonicalJson(Map.of("findings", material, "roundOneCandidateId",
                roundOneCandidateId, "schemaVersion", FINDING_SET_SCHEMA)));
    }

    static boolean sha256(String value) {
        return value != null && HEX_64.matcher(value).matches();
    }

    private static List<String> sortedUniqueIds(List<String> values, String prefix) {
        Stage04Validation.require(values != null);
        Set<String> seen = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            Stage04Validation.require(FilesystemCandidateStore.digestIdentifier(value, prefix));
            Stage04Validation.require(seen.add(value));
            normalized.add(value);
        }
        normalized.sort(Comparator.naturalOrder());
        return List.copyOf(normalized);
    }

    private static List<String> sortedUniqueReaderKeys(List<String> values) {
        Stage04Validation.require(values != null);
        Set<String> seen = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            Stage04Validation.require(value != null && value.startsWith("reader:") && value.length() > "reader:".length()
                    && value.chars().noneMatch(Character::isWhitespace));
            Stage04Validation.require(seen.add(value));
            normalized.add(value);
        }
        normalized.sort(Comparator.naturalOrder());
        return List.copyOf(normalized);
    }

    private static List<Integer> sortedUniqueSections(List<Integer> values) {
        Stage04Validation.require(values != null);
        Set<Integer> seen = new HashSet<>();
        List<Integer> normalized = new ArrayList<>();
        for (Integer value : values) {
            Stage04Validation.require(value != null && value >= 1 && value <= 9 && seen.add(value));
            normalized.add(value);
        }
        normalized.sort(Comparator.naturalOrder());
        return List.copyOf(normalized);
    }

    private static Map<String, Object> findingMaterial(Normalized value) {
        Map<String, Object> material = new TreeMap<>();
        material.put("candidateId", value.candidateId());
        material.put("category", value.category());
        material.put("correctiveAddendumRequired", value.correctiveAddendumRequired());
        material.put("disposition", value.disposition());
        material.put("findingCode", value.findingCode());
        material.put("flowSliceIds", value.flowSliceIds());
        material.put("permittedCorrection", value.permittedCorrection());
        material.put("readerItemKeys", value.readerItemKeys());
        material.put("schemaVersion", value.schemaVersion());
        material.put("sectionNumbers", value.sectionNumbers());
        material.put("severity", value.severity());
        material.put("validationReceiptId", value.validationReceiptId());
        return material;
    }

    private static String canonicalJson(Map<String, ?> value) {
        try {
            return JSON.writeValueAsString(new TreeMap<>(value));
        } catch (JsonProcessingException impossible) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
    }

    record Normalized(String schemaVersion, String candidateId, String validationReceiptId, String severity,
                      String category, String permittedCorrection, String findingCode, List<String> flowSliceIds,
                      List<String> readerItemKeys, List<Integer> sectionNumbers, String disposition,
                      boolean correctiveAddendumRequired) {
    }
}
