package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.CodeFact;
import com.linguan.codemd.stage01.ExpectationGap;
import com.linguan.codemd.stage01.Proof;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.AllowedAtomView;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage02.FlowGap;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage03.AdmittedFlowMeaning;
import com.linguan.codemd.stage03.BusinessTermEntry;
import com.linguan.codemd.stage03.FlowInterpretationResult;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.InterpretationGap;
import com.linguan.codemd.stage03.ModelRoundReceipt;
import com.linguan.codemd.stage03.NineSectionPlan;
import com.linguan.codemd.stage03.ReaderItem;
import com.linguan.codemd.stage03.ReaderSection;
import com.linguan.codemd.stage03.TechnicalDisplayPolicy;
import com.linguan.codemd.stage03.TechnicalDisplayResolution;
import com.linguan.codemd.stage03.TechnicalDisplaySlot;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.RenderedNineSectionDocument;
import com.linguan.codemd.stage03.Stage03Result;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compiles one already-admitted M1--M7 result chain into the non-manifest
 * Candidate artifacts. Installation remains exclusively the store's concern.
 */
final class CandidateAssembler {
    private static final String ARCHIVE_POLICY_ID = "archive-policy:immutable-candidate-v2";
    private static final ObjectMapper JSON = new ObjectMapper();

    CandidateBundle assemble(CandidateAssemblyRequest request) {
        requireRequest(request);
        Stage01Result stage01 = request.stage01Result();
        Stage02Result stage02 = request.stage02Result();
        Stage03Result stage03 = request.stage03Result();
        validateStageLinkage(stage01, stage02, stage03);
        validateSlotEligibility(request, stage02);
        validatePlan(stage01, stage02, stage03);
        validateRenderedDocument(stage03.renderedDocument());
        ArchivePreimages preimages = archivePreimages(request);

        String candidateContentId = candidateContentId(stage01, stage02, stage03, preimages);
        CandidateIdentity identity = new CandidateSeriesLedger().identity(request.candidateSeriesRequest(),
                candidateContentId, request.lineage());
        validateSlotIdentity(request.roundSlot(), request.lineage(), identity, stage02);

        byte[] markdown = stage03.renderedDocument().markdown().getBytes(StandardCharsets.UTF_8);
        CandidateReference reference = new CandidateReference(identity.seriesId(), request.lineage().readerCandidateRound(),
                identity.candidateId(), candidateContentId, sha256(markdown), "UNPUBLISHED_CANDIDATE");
        Map<String, byte[]> artifacts = artifacts(request, reference, preimages);
        return new CandidateBundle(reference, artifacts);
    }

    private static void requireRequest(CandidateAssemblyRequest request) {
        if (request == null || request.candidateSeriesRequest() == null || request.lineage() == null
                || request.roundSlot() == null || request.stage01Result() == null || request.stage02Result() == null
                || request.stage03Request() == null || request.stage03Result() == null
                || request.stage03RunTranscript() == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
    }

    private static void validateStageLinkage(Stage01Result stage01, Stage02Result stage02, Stage03Result stage03) {
        if (!stage01.stage01ResultId().equals(stage02.stage01ResultId())
                || !stage02.stage02ResultId().equals(stage03.stage02ResultId())
                || !stage02.stage02ResultId().equals(stage03.repositoryBusinessModel().stage02ResultId())
                || !stage03.repositoryBusinessModel().repositoryBusinessModelId()
                .equals(stage03.nineSectionPlan().repositoryBusinessModelId())
                || !stage01.verifiedSnapshot().snapshotId().equals(stage01.repositoryUnderstanding().snapshotId())
                || !stage01.verifiedSnapshot().snapshotId().equals(stage01.provenSourceFacts().snapshotId())
                || !stage01.repositoryUnderstanding().repositoryModel().repositoryModelId()
                .equals(stage01.provenSourceFacts().repositoryModelId())) {
            throw closureBroken();
        }
        Set<String> flowIds = stage02.flowSlices().stream().map(flow -> flow.flowSliceId())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> capsuleIds = stage02.evidenceCapsules().stream().map(EvidenceCapsule::evidenceCapsuleId)
                .collect(java.util.stream.Collectors.toSet());
        for (FlowInterpretationResult interpretation : stage03.flowInterpretations()) {
            if (!flowIds.contains(interpretation.flowSliceId())
                    || !capsuleIds.contains(interpretation.evidenceCapsuleId())) {
                throw closureBroken();
            }
        }
    }

    private static void validateSlotEligibility(CandidateAssemblyRequest request, Stage02Result stage02) {
        RoundSlotView slot = request.roundSlot();
        CandidateLineage lineage = request.lineage();
        if (slot.readerCandidateRound() != lineage.readerCandidateRound()
                || !same(slot.parentCandidateId(), lineage.parentCandidateId())
                || !slot.findingIds().equals(lineage.findingIds())
                || !same(slot.correctiveAddendumId(), lineage.correctiveAddendumId())) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
        boolean started = RoundSlotState.STARTED_CONSUMED.name().equals(slot.slotState());
        boolean zeroCapsule = stage02.evidenceCapsules().isEmpty() && RoundSlotState.RESERVED.name().equals(slot.slotState())
                && slot.prestartAttemptCount() == 0;
        if (!started && !zeroCapsule) {
            throw closureBroken();
        }
    }

    private static void validateSlotIdentity(RoundSlotView slot, CandidateLineage lineage, CandidateIdentity identity,
                                             Stage02Result stage02) {
        if (!identity.canonicalRequestId().equals(slot.canonicalRequestId())
                || !identity.seriesId().equals(slot.seriesId())
                || slot.readerCandidateRound() != lineage.readerCandidateRound()) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
        boolean started = RoundSlotState.STARTED_CONSUMED.name().equals(slot.slotState());
        boolean zeroCapsule = stage02.evidenceCapsules().isEmpty() && RoundSlotState.RESERVED.name().equals(slot.slotState())
                && slot.prestartAttemptCount() == 0;
        if (!started && !zeroCapsule) {
            throw closureBroken();
        }
    }

    private static void validateRenderedDocument(RenderedNineSectionDocument document) {
        if (document == null || document.markdown() == null || !sha256(document.markdown()
                .getBytes(StandardCharsets.UTF_8)).equals(document.markdownSha256())) {
            throw closureBroken();
        }
    }

    private static void validatePlan(Stage01Result stage01, Stage02Result stage02, Stage03Result stage03) {
        NineSectionPlan plan = stage03.nineSectionPlan();
        if (!"nine-section-plan-v1".equals(plan.schemaVersion()) || !validId(plan.nineSectionPlanId(), "nine-section-plan:")
                || allZeroDigest(plan.nineSectionPlanId()) || plan.sections().size() != 9
                || plan.coverage().sectionCount() != 9) {
            throw closureBroken();
        }
        Set<String> itemKeys = new HashSet<>();
        Set<String> sectionKeys = new HashSet<>();
        for (ReaderSection section : plan.sections()) {
            if (!sectionKeys.add(section.sectionKey())) {
                throw closureBroken();
            }
            for (ReaderItem item : section.items()) {
                if (!section.sectionKey().equals(item.ownerSectionKey()) || !itemKeys.add(item.readerItemKey())) {
                    throw closureBroken();
                }
            }
        }
        Set<String> available = availableIds(stage01, stage02, stage03);
        available.addAll(itemKeys);
        for (ReaderSection section : plan.sections()) {
            for (ReaderItem item : section.items()) {
                if (!available.containsAll(item.basisAtomIds()) || !available.containsAll(item.basisMeaningIds())
                        || !available.containsAll(item.basisGapIds()) || !available.containsAll(item.referencedItemKeys())) {
                    throw closureBroken();
                }
            }
        }
    }

    private static Set<String> availableIds(Stage01Result stage01, Stage02Result stage02, Stage03Result stage03) {
        Set<String> ids = new TreeSet<>();
        collectIds(JSON.valueToTree(stage01), ids);
        collectIds(JSON.valueToTree(stage02), ids);
        collectIds(JSON.valueToTree(stage03.flowInterpretations()), ids);
        collectIds(JSON.valueToTree(stage03.repositoryBusinessModel()), ids);
        for (EvidenceCapsule capsule : stage02.evidenceCapsules()) {
            for (var fact : capsule.allowedFacts()) {
                for (AllowedAtomView atom : fact.atoms()) {
                    ids.add(atom.atomId());
                }
            }
        }
        return ids;
    }

    private static void collectIds(JsonNode node, Set<String> ids) {
        if (node.isTextual()) {
            String value = node.asText();
            if (value.indexOf(':') > 0) {
                ids.add(value);
            }
            return;
        }
        if (node.isContainerNode()) {
            node.elements().forEachRemaining(child -> collectIds(child, ids));
        }
    }

    private static void validateRoundReceipts(List<FlowInterpretationResult> interpretations) {
        for (FlowInterpretationResult interpretation : interpretations) {
            if (interpretation.roundReceipts().isEmpty()) {
                throw closureBroken();
            }
            Set<Integer> rounds = new HashSet<>();
            for (ModelRoundReceipt receipt : interpretation.roundReceipts()) {
                if (!rounds.add(receipt.round()) || receipt.round() < 1 || receipt.round() > 2
                        || !validSha(receipt.responseSha256()) || blank(receipt.startedReceiptId())
                        || receipt.observedRuntime() == null) {
                    throw closureBroken();
                }
            }
        }
    }

    private Map<String, byte[]> artifacts(CandidateAssemblyRequest request, CandidateReference reference,
                                           ArchivePreimages preimages) {
        Stage01Result stage01 = request.stage01Result();
        Stage02Result stage02 = request.stage02Result();
        Stage03Result stage03 = request.stage03Result();
        LinkedHashMap<String, byte[]> artifacts = new LinkedHashMap<>();
        artifacts.put("document.md", stage03.renderedDocument().markdown().getBytes(StandardCharsets.UTF_8));
        artifacts.put("candidate.json", json(candidateSidecar(request, reference, preimages)));
        artifacts.put("source-input.json", preimages.sourceInput());
        artifacts.put("verified-snapshot.json", verifiedSnapshotArtifact(stage01));
        artifacts.put("repository-model.json", repositoryModelArtifact(stage01));
        artifacts.put("capability-report.json", capabilityReportArtifact(stage01));
        artifacts.put("proven-facts.json", provenFactsArtifact(stage01));
        artifacts.put("proof-pack.json", proofPackArtifact(stage01));
        artifacts.put("gap-ledger.json", gapLedgerArtifact(stage01));
        artifacts.put("flow-slices.json", flowSlicesArtifact(stage02));
        artifacts.put("evidence-capsules.json", evidenceCapsulesArtifact(stage02));
        artifacts.put("registry-bundle.json", preimages.registryBundle());
        artifacts.put("model-rounds.jsonl", preimages.modelRounds());
        artifacts.put("flow-interpretations.json", flowInterpretationsArtifact(stage03));
        artifacts.put("repository-business-model.json", repositoryBusinessModelArtifact(stage03));
        artifacts.put("nine-section-plan.json", nineSectionPlanArtifact(stage03));
        artifacts.put("trace.jsonl", preimages.trace());
        artifacts.put("generation-receipts.jsonl", preimages.generationReceipts());
        artifacts.put("validation-baseline.json", json(validationBaseline(stage01, stage02, stage03, reference)));
        return artifacts;
    }

    private static Map<String, Object> candidateSidecar(CandidateAssemblyRequest request, CandidateReference reference,
                                                         ArchivePreimages preimages) {
        Stage01Result stage01 = request.stage01Result();
        Stage02Result stage02 = request.stage02Result();
        Stage03Result stage03 = request.stage03Result();
        TreeMap<String, Object> sidecar = new TreeMap<>();
        sidecar.put("archivePolicyId", ARCHIVE_POLICY_ID);
        sidecar.put("candidateContentId", reference.candidateContentId());
        sidecar.put("candidateId", reference.candidateId());
        sidecar.put("documentSha256", reference.documentSha256());
        sidecar.put("generationReceiptsRoot", sha256(preimages.generationReceipts()));
        sidecar.put("modelRoundsRoot", sha256(preimages.modelRounds()));
        sidecar.put("readerCandidateRound", reference.readerCandidateRound());
        sidecar.put("registryBundleSha256", sha256(preimages.registryBundle()));
        sidecar.put("roundSlotId", request.roundSlot().roundSlotId());
        sidecar.put("schemaVersion", "candidate-v2");
        sidecar.put("seriesId", reference.seriesId());
        sidecar.put("sourceInputSha256", sha256(preimages.sourceInput()));
        sidecar.put("stage01ResultId", stage01.stage01ResultId());
        sidecar.put("stage02ResultId", stage02.stage02ResultId());
        sidecar.put("stage03ResultId", stage03.stage03ResultId());
        sidecar.put("status", reference.status());
        sidecar.put("traceRoot", sha256(preimages.trace()));
        if (request.lineage().parentCandidateId() != null) {
            sidecar.put("parentCandidateId", request.lineage().parentCandidateId());
            sidecar.put("findingIds", request.lineage().findingIds());
            if (request.lineage().correctiveAddendumId() != null) {
                sidecar.put("correctiveAddendumId", request.lineage().correctiveAddendumId());
            }
        }
        return sidecar;
    }

    private static Map<String, Object> rootlessSourceInput(CandidateAssemblyRequest request) {
        TreeMap<String, Object> input = new TreeMap<>();
        CandidateSeriesRequest series = request.candidateSeriesRequest();
        Stage03Request stage03 = request.stage03Request();
        input.put("candidateSeriesRequest", Map.of("profileBundleId", series.profileBundleId(),
                "rootlessRequest", series.rootlessRequest(), "schemaVersion", series.schemaVersion()));
        input.put("schemaVersion", "candidate-source-input-v2");
        input.put("stage01", rootlessControls(stage03.stage02Request().stage01Request()));
        input.put("stage02", rootlessControls(stage03.stage02Request()));
        input.put("stage03", rootlessControls(stage03ControlProjection(stage03)));
        return input;
    }

    /** Source roots and registration lookup IDs are transport bindings, never Candidate content. */
    private static JsonNode rootlessControls(Object value) {
        return rootlessControls(JSON.valueToTree(value));
    }

    private static JsonNode rootlessControls(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> {
                if (!"snapshotRoot".equals(entry.getKey()) && !"sourceRegistrationId".equals(entry.getKey())) {
                    fields.put(entry.getKey(), entry.getValue());
                }
            });
            fields.forEach((key, child) -> result.set(key, rootlessControls(child)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            node.forEach(child -> result.add(rootlessControls(child)));
            return result;
        }
        return node;
    }

    private static JsonNode stage03ControlProjection(Stage03Request request) {
        ObjectNode projection = (ObjectNode) JSON.valueToTree(request);
        projection.put("registryBundleId", request.registryBundle().registryBundleId());
        return projection;
    }

    private static Map<String, Object> stage02FlowProjection(Stage02Result stage02) {
        return Map.of("entryDispositions", stage02.entryDispositions(), "flowGaps", stage02.flowGaps(),
                "flowSlices", sorted(stage02.flowSlices(), flow -> flow.flowSliceId()),
                "schemaVersion", "candidate-flow-slices-v1", "stage01ResultId", stage02.stage01ResultId(),
                "stage02ResultId", stage02.stage02ResultId());
    }

    private static Map<String, Object> stage02EvidenceProjection(Stage02Result stage02) {
        return Map.of("evidenceCapsules", sorted(stage02.evidenceCapsules(), EvidenceCapsule::evidenceCapsuleId),
                "schemaVersion", "candidate-evidence-capsules-v1", "stage02ResultId", stage02.stage02ResultId());
    }

    /* The validator reuses these projections so archive admission and replay
       compare one canonical byte contract rather than parallel serializers. */
    static byte[] verifiedSnapshotArtifact(Stage01Result stage01) {
        return json(stage01.verifiedSnapshot());
    }

    static byte[] repositoryModelArtifact(Stage01Result stage01) {
        return json(stage01.repositoryUnderstanding().repositoryModel());
    }

    static byte[] capabilityReportArtifact(Stage01Result stage01) {
        return json(stage01.repositoryUnderstanding().capabilityReport());
    }

    static byte[] provenFactsArtifact(Stage01Result stage01) {
        return json(stage01.provenSourceFacts().provenFactSet());
    }

    static byte[] proofPackArtifact(Stage01Result stage01) {
        return json(stage01.provenSourceFacts().proofPack());
    }

    static byte[] gapLedgerArtifact(Stage01Result stage01) {
        return json(stage01.provenSourceFacts().gapLedger());
    }

    static byte[] flowSlicesArtifact(Stage02Result stage02) {
        return json(stage02FlowProjection(stage02));
    }

    static byte[] evidenceCapsulesArtifact(Stage02Result stage02) {
        return json(stage02EvidenceProjection(stage02));
    }

    static byte[] flowInterpretationsArtifact(Stage03Result stage03) {
        return json(sorted(stage03.flowInterpretations(), FlowInterpretationResult::flowSliceId));
    }

    static byte[] repositoryBusinessModelArtifact(Stage03Result stage03) {
        return json(stage03.repositoryBusinessModel());
    }

    static byte[] nineSectionPlanArtifact(Stage03Result stage03) {
        return json(stage03.nineSectionPlan());
    }

    static byte[] readerTraceArtifact(Stage01Result stage01, Stage02Result stage02, Stage03Request request,
                                      Stage03Result stage03) {
        return traceLines(stage01, stage02, request, stage03);
    }

    static byte[] registryBundleArtifact(Stage03Request request) {
        return json(registryBundleProjection(request));
    }

    private static Map<String, Object> validationBaseline(Stage01Result stage01, Stage02Result stage02,
                                                            Stage03Result stage03, CandidateReference reference) {
        return Map.of("candidateId", reference.candidateId(), "documentSha256", reference.documentSha256(),
                "nineSectionPlanId", stage03.nineSectionPlan().nineSectionPlanId(), "schemaVersion",
                "candidate-validation-baseline-v1", "stage01ResultId", stage01.stage01ResultId(),
                "stage02ResultId", stage02.stage02ResultId(), "stage03Checks", stage03.validation().completedChecks(),
                "stage03ResultId", stage03.stage03ResultId());
    }

    private static byte[] traceLines(Stage01Result stage01, Stage02Result stage02, Stage03Request request,
                                     Stage03Result stage03) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (ReaderSection section : stage03.nineSectionPlan().sections()) {
            for (ReaderItem item : section.items()) {
                records.add(traceRecord(stage01, stage02, request, stage03, item));
            }
        }
        records.sort(Comparator.comparing(record -> (String) record.get("readerItemKey")));
        return jsonLines(records);
    }

    /**
     * Compiles the reader AST into immutable, self-describing lineage records.
     * The fields are projection material, not resolver hints: replay regenerates
     * these exact bytes before any TraceView is admitted.
     */
    private static Map<String, Object> traceRecord(Stage01Result stage01, Stage02Result stage02,
                                                    Stage03Request request, Stage03Result stage03,
                                                    ReaderItem item) {
        Map<String, Object> record = new TreeMap<>();
        String kind = traceKind(item);
        record.put("schemaVersion", "reader-lineage-v2");
        record.put("readerItemKey", item.readerItemKey());
        record.put("traceKind", kind);
        record.put("basisAtomIds", sortedStrings(item.basisAtomIds()));
        record.put("basisMeaningIds", sortedStrings(item.basisMeaningIds()));
        record.put("basisGapIds", sortedStrings(item.basisGapIds()));
        record.put("referencedItemKeys", sortedStrings(item.referencedItemKeys()));
        switch (kind) {
            case "FACT_SENTENCE" -> factTraceRefs(record, stage01, item);
            case "ADMITTED_TERM" -> termTraceRefs(record, request, stage03, item);
            case "TECHNICAL_FALLBACK" -> fallbackTraceRefs(record, request, stage03, item);
            case "GAP_QUESTION" -> gapTraceRefs(record, stage01, stage02, request, stage03, item);
            case "REFERENCE_ONLY" -> record.put("ownerReaderItemKeys", sortedStrings(item.referencedItemKeys()));
            default -> throw closureBroken();
        }
        return Map.copyOf(record);
    }

    private static void factTraceRefs(Map<String, Object> record, Stage01Result stage01, ReaderItem item) {
        List<String> factIds = new ArrayList<>();
        List<String> proofIds = new ArrayList<>();
        List<String> rootProofNodeIds = new ArrayList<>();
        for (String atomId : item.basisAtomIds()) {
            CodeFact fact = unique(stage01.provenSourceFacts().provenFactSet().codeFacts().stream()
                    .filter(candidate -> candidate.atoms().stream().anyMatch(atom -> atomId.equals(atom.atomId())))
                    .toList());
            Proof proof = unique(stage01.provenSourceFacts().proofPack().proofs().stream()
                    .filter(candidate -> fact.factId().equals(candidate.factId()) && atomId.equals(candidate.atomId()))
                    .toList());
            factIds.add(fact.factId());
            proofIds.add(proof.proofId());
            rootProofNodeIds.add(proof.rootProofNodeId());
        }
        record.put("factIds", sortedStrings(factIds));
        record.put("proofIds", sortedStrings(proofIds));
        record.put("proofPackId", stage01.provenSourceFacts().proofPack().proofPackId());
        record.put("rootProofNodeIds", sortedStrings(rootProofNodeIds));
    }

    private static void termTraceRefs(Map<String, Object> record, Stage03Request request, Stage03Result stage03,
                                      ReaderItem item) {
        List<Map<String, Object>> terms = new ArrayList<>();
        for (String meaningId : item.basisMeaningIds()) {
            AdmittedFlowMeaning meaning = unique(stage03.flowInterpretations().stream()
                    .flatMap(value -> value.admittedMeanings().stream())
                    .filter(value -> meaningId.equals(value.meaningId())).toList());
            BusinessTermEntry term = unique(request.registryBundle().businessTerms().terms().stream()
                    .filter(value -> meaning.businessTermKey().equals(value.businessTermKey())).toList());
            if (!meaning.anchorKind().equals(term.anchorKind()) || !meaning.localizedValue().equals(term.localizedValue())
                    || !meaning.basisAtomIds().containsAll(term.minimumBasisAtomIds())) {
                throw closureBroken();
            }
            Map<String, Object> value = new TreeMap<>();
            value.put("basisAtomIds", sortedStrings(meaning.basisAtomIds()));
            value.put("basisGapIds", sortedStrings(meaning.basisGapIds()));
            value.put("businessTermKey", term.businessTermKey());
            value.put("meaningId", meaning.meaningId());
            value.put("priority", term.priority());
            terms.add(Map.copyOf(value));
        }
        terms.sort(Comparator.comparing(value -> (String) value.get("meaningId")));
        record.put("businessTermRegistryId", request.registryBundle().businessTerms().registryId());
        record.put("termRefs", List.copyOf(terms));
    }

    private static void fallbackTraceRefs(Map<String, Object> record, Stage03Request request,
                                          Stage03Result stage03, ReaderItem item) {
        if ("EMPTY_SECTION".equals(item.itemKind())) {
            record.put("fallbackSubtype", "EMPTY_SECTION");
            record.put("sectionKey", item.ownerSectionKey());
            record.put("profileId", request.nineSectionProfileRef().profileId());
            record.put("templateKey", item.templateKey());
            return;
        }
        if (!"TECHNICAL_DISPLAY".equals(item.itemKind())) {
            throw closureBroken();
        }
        TechnicalFallbackRef match = null;
        for (FlowInterpretationResult interpretation : stage03.flowInterpretations()) {
            for (TechnicalDisplayResolution fallback : interpretation.technicalFallbacks()) {
                String expectedItemKey = "reader:technical:" + sha256((interpretation.flowSliceId() + "\n"
                        + fallback.anchorKey() + "\n" + fallback.policyKey()).getBytes(StandardCharsets.UTF_8));
                if (item.readerItemKey().equals(expectedItemKey)) {
                    if (match != null) {
                        throw closureBroken();
                    }
                    match = new TechnicalFallbackRef(interpretation, fallback);
                }
            }
        }
        if (match == null) {
            throw closureBroken();
        }
        TechnicalDisplayResolution fallback = match.fallback();
        TechnicalDisplayPolicy policy = unique(request.registryBundle().technicalDisplays().policies().stream()
                .filter(value -> fallback.policyKey().equals(value.policyKey())).toList());
        List<TechnicalDisplaySlot> slots = item.slots().stream().filter(TechnicalDisplaySlot.class::isInstance)
                .map(TechnicalDisplaySlot.class::cast).toList();
        if (slots.size() != 1 || !"technical-display".equals(slots.get(0).slotKey())
                || !fallback.anchorKey().equals(slots.get(0).anchorKey())
                || !fallback.resolvedDisplay().equals(slots.get(0).value())
                || !fallback.anchorKind().equals(policy.anchorKind())
                || !item.templateKey().equals(policy.displayTemplateKey())) {
            throw closureBroken();
        }
        record.put("anchorKey", fallback.anchorKey());
        record.put("evidenceCapsuleId", match.interpretation().evidenceCapsuleId());
        record.put("fallbackSubtype", "FLOW_TECHNICAL_DISPLAY");
        record.put("flowSliceId", match.interpretation().flowSliceId());
        record.put("policyId", policy.policyKey());
        record.put("policyKey", policy.policyKey());
        record.put("resolutionOrder", List.copyOf(policy.resolutionOrder()));
        record.put("taskSpecId", match.interpretation().taskSpecId());
        record.put("technicalDisplayRegistryId", request.registryBundle().technicalDisplays().registryId());
        record.put("templateKey", policy.displayTemplateKey());
    }

    private static void gapTraceRefs(Map<String, Object> record, Stage01Result stage01, Stage02Result stage02,
                                     Stage03Request request, Stage03Result stage03, ReaderItem item) {
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (String gapId : item.basisGapIds()) {
            ExpectationGap expectation = optional(stage01.provenSourceFacts().gapLedger().expectationGaps().stream()
                    .filter(value -> gapId.equals(value.gapId())).toList());
            Map<String, Object> value = new TreeMap<>();
            value.put("gapId", gapId);
            if (expectation != null) {
                value.put("absenceEvidence", expectation.absenceEvidence());
                value.put("origin", "STAGE01_EXPECTATION");
                value.put("questionKey", expectation.questionTemplateKey());
                value.put("reasonCode", expectation.reasonCode());
                value.put("searchedScope", expectation.searchedScope());
                request.registryBundle().questions().questions().stream()
                        .filter(question -> expectation.questionTemplateKey().equals(question.questionKey())).findFirst()
                        .ifPresent(question -> {
                            value.put("questionRegistryId", request.registryBundle().questions().registryId());
                            value.put("readerTemplateKey", question.readerTemplateKey());
                        });
            } else {
                List<InterpretationGapRef> interpretations = interpretationGaps(stage03, gapId);
                if (!interpretations.isEmpty()) {
                    value.put("origin", "INTERPRETATION_GAP");
                    List<Map<String, Object>> origins = new ArrayList<>();
                    for (InterpretationGapRef interpretation : interpretations) {
                        if (interpretation.gap().sourceGapId() == null || interpretation.gap().sourceGapId().isBlank()) {
                            throw closureBroken();
                        }
                        Map<String, Object> origin = new TreeMap<>();
                        origin.put("flowInterpretationResultId", interpretation.interpretation().flowInterpretationResultId());
                        origin.put("sourceGapId", interpretation.gap().sourceGapId());
                        origins.add(Map.copyOf(origin));
                    }
                    origins.sort(Comparator.comparing(origin -> (String) origin.get("flowInterpretationResultId")));
                    value.put("interpretationOrigins", List.copyOf(origins));
                } else {
                    FlowGap flow = optional(stage02.flowGaps().stream()
                            .filter(candidate -> gapId.equals(candidate.flowGapId())).toList());
                    if (flow == null) {
                        throw closureBroken();
                    }
                    value.put("entryId", flow.entryId());
                    value.put("origin", "FLOW_GAP");
                    value.put("reasonCode", flow.code());
                }
            }
            gaps.add(Map.copyOf(value));
        }
        gaps.sort(Comparator.comparing(value -> (String) value.get("gapId")));
        record.put("gapRefs", List.copyOf(gaps));
    }

    private static List<InterpretationGapRef> interpretationGaps(Stage03Result stage03, String gapId) {
        List<InterpretationGapRef> found = new ArrayList<>();
        Set<String> resultIds = new HashSet<>();
        for (FlowInterpretationResult interpretation : stage03.flowInterpretations()) {
            for (InterpretationGap gap : interpretation.interpretationGaps()) {
                if (gapId.equals(gap.interpretationGapId())) {
                    if (!resultIds.add(interpretation.flowInterpretationResultId())) {
                        throw closureBroken();
                    }
                    found.add(new InterpretationGapRef(interpretation, gap));
                }
            }
        }
        found.sort(Comparator.comparing(value -> value.interpretation().flowInterpretationResultId()));
        return List.copyOf(found);
    }

    private static <T> T unique(List<T> values) {
        if (values.size() != 1 || values.get(0) == null) {
            throw closureBroken();
        }
        return values.get(0);
    }

    private static <T> T optional(List<T> values) {
        if (values.size() > 1) {
            throw closureBroken();
        }
        return values.isEmpty() ? null : values.get(0);
    }

    private record TechnicalFallbackRef(FlowInterpretationResult interpretation,
                                        TechnicalDisplayResolution fallback) {
    }

    private record InterpretationGapRef(FlowInterpretationResult interpretation, InterpretationGap gap) {
    }

    /**
     * Material that has no Candidate-reference cycle and is therefore eligible
     * for the v2 Candidate content identity.  The run transcript is an input,
     * not a derivation from the reader result.
     */
    private static ArchivePreimages archivePreimages(CandidateAssemblyRequest request) {
        Stage03RunTranscript transcript = request.stage03RunTranscript();
        validateTranscript(request.stage03Result(), transcript);
        Map<String, GenerationRoundReceipt> receipts = transcript.generationReceipts().stream()
                .collect(java.util.stream.Collectors.toMap(GenerationRoundReceipt::modelRoundId,
                        receipt -> receipt, (first, duplicate) -> {
                            throw closureBroken();
                        }, TreeMap::new));
        return new ArchivePreimages(json(rootlessSourceInput(request)),
                registryBundleArtifact(request.stage03Request()),
                modelRoundLines(transcript.modelRounds(), receipts),
                modelRoundContentLines(transcript.modelRounds()),
                generationReceiptLines(transcript.generationReceipts()),
                readerTraceArtifact(request.stage01Result(), request.stage02Result(), request.stage03Request(),
                        request.stage03Result()));
    }

    private static void validateTranscript(Stage03Result stage03, Stage03RunTranscript transcript) {
        if (transcript == null || transcript.modelRounds() == null || transcript.generationReceipts() == null
                || stage03.canonicalRounds() == null
                || transcript.modelRounds().size() != stage03.canonicalRounds().size()
                || transcript.generationReceipts().size() != stage03.canonicalRounds().size()) {
            throw closureBroken();
        }
        Map<String, ArchivedModelRound> rounds = new TreeMap<>();
        for (ArchivedModelRound archived : transcript.modelRounds()) {
            if (archived == null || archived.task() == null || !validId(archived.modelRoundId(), "model-round:")
                    || rounds.put(roundKey(archived.task()), archived) != null
                    || !validSha(archived.canonicalResponseSha256()) || !validSha(archived.semanticResponseSha256())
                    || blank(archived.canonicalResponseJson())
                    || !archived.canonicalResponseSha256().equals(sha256(archived.canonicalResponseJson()
                    .getBytes(StandardCharsets.UTF_8))) || archived.observedRuntime() == null
                    || blank(archived.startedReceiptId())) {
                throw closureBroken();
            }
            canonicalJsonObject(archived.canonicalResponseJson());
        }
        Map<String, GenerationRoundReceipt> receipts = new TreeMap<>();
        for (GenerationRoundReceipt receipt : transcript.generationReceipts()) {
            if (receipt == null || !validId(receipt.generationReceiptId(), "generation-receipt:")
                    || !validId(receipt.modelRoundId(), "model-round:")
                    || receipts.put(receipt.modelRoundId(), receipt) != null
                    || receipt.round() < 1 || receipt.round() > 2 || receipt.expectedRuntime() == null
                    || receipt.observedRuntime() == null || blank(receipt.preflightReceiptId())
                    || blank(receipt.providerPolicyId())
                    || blank(receipt.attemptId()) || blank(receipt.startedEventId())
                    || receipt.startedEventOrdinal() < 1 || blank(receipt.startedReceiptId())
                    || !validSha(receipt.canonicalResponseSha256()) || !validSha(receipt.semanticResponseSha256())
                    || !"ADMITTED".equals(receipt.terminalStatus())) {
                throw closureBroken();
            }
        }
        Set<String> canonicalKeys = new HashSet<>();
        for (CanonicalFlowRound canonical : stage03.canonicalRounds()) {
            if (canonical == null || canonical.task() == null || !canonicalKeys.add(roundKey(canonical.task()))) {
                throw closureBroken();
            }
            ArchivedModelRound archived = rounds.get(roundKey(canonical.task()));
            if (archived == null || !canonical.task().equals(archived.task())
                    || !canonical.canonicalResponseJson().equals(archived.canonicalResponseJson())
                    || !canonical.canonicalResponseSha256().equals(archived.canonicalResponseSha256())
                    || !canonical.semanticResponseSha256().equals(archived.semanticResponseSha256())
                    || !canonical.observedRuntime().equals(archived.observedRuntime())
                    || !canonical.startedReceiptId().equals(archived.startedReceiptId())) {
                throw closureBroken();
            }
            if (!LifecycleProviderBridge.modelRoundId(archived.task(), archived.canonicalResponseSha256(),
                    archived.semanticResponseSha256(), archived.observedRuntime(), archived.startedReceiptId())
                    .equals(archived.modelRoundId())) {
                throw closureBroken();
            }
            GenerationRoundReceipt receipt = receipts.get(archived.modelRoundId());
            if (receipt == null || !canonical.task().taskSpecId().equals(receipt.taskSpecId())
                    || !canonical.task().flowSliceId().equals(receipt.flowSliceId())
                    || !canonical.task().evidenceCapsuleId().equals(receipt.evidenceCapsuleId())
                    || canonical.task().flowInterpretationRound() != receipt.round()
                    || !canonical.task().expectedRuntime().equals(receipt.expectedRuntime())
                    || !canonical.observedRuntime().equals(receipt.observedRuntime())
                    || !canonical.startedReceiptId().equals(receipt.startedReceiptId())
                    || !canonical.canonicalResponseSha256().equals(receipt.canonicalResponseSha256())
                    || !canonical.semanticResponseSha256().equals(receipt.semanticResponseSha256())) {
                throw closureBroken();
            }
            if (!LifecycleProviderBridge.generationReceiptId(receipt.modelRoundId(), receipt.taskSpecId(),
                    receipt.providerPolicyId(), receipt.preflightReceiptId(), receipt.attemptId(),
                    receipt.startedEventId(), receipt.startedEventOrdinal(), receipt.startedReceiptId())
                    .equals(receipt.generationReceiptId())) {
                throw closureBroken();
            }
        }
        if (!rounds.keySet().equals(canonicalKeys) || receipts.size() != rounds.size()) {
            throw closureBroken();
        }
    }

    private static Map<String, Object> registryBundleProjection(Stage03Request request) {
        return Map.of("schemaVersion", "candidate-registry-bundle-v2", "inputRegistryBundle",
                request.registryBundle(), "effectiveContract", Map.of("registryBundleId",
                        request.registryBundle().registryBundleId(), "interpretationProfile",
                        request.interpretationProfileRef(), "knowledgeProfile", request.knowledgeProfileRef(),
                        "nineSectionProfile", request.nineSectionProfileRef(), "resourceBudget",
                        request.resourceBudget()));
    }

    private static byte[] modelRoundLines(List<ArchivedModelRound> rounds,
                                          Map<String, GenerationRoundReceipt> receipts) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (ArchivedModelRound round : rounds.stream().sorted(Comparator.comparing(value -> roundKey(value.task())))
                .toList()) {
            GenerationRoundReceipt receipt = receipts.get(round.modelRoundId());
            if (receipt == null) {
                throw closureBroken();
            }
            Map<String, Object> record = new TreeMap<>();
            record.put("schemaVersion", "archived-model-round-v1");
            record.put("modelRoundId", round.modelRoundId());
            record.put("generationReceiptId", receipt.generationReceiptId());
            record.put("flowSliceId", round.task().flowSliceId());
            record.put("evidenceCapsuleId", round.task().evidenceCapsuleId());
            record.put("round", round.task().flowInterpretationRound());
            record.put("task", taskProjection(round.task()));
            applyImprovementTaskDigests(record, round.task());
            record.put("canonicalResponse", canonicalJsonObject(round.canonicalResponseJson()));
            record.put("canonicalResponseSha256", round.canonicalResponseSha256());
            record.put("semanticResponseSha256", round.semanticResponseSha256());
            record.put("observedRuntime", round.observedRuntime());
            record.put("startedReceiptId", round.startedReceiptId());
            records.add(record);
        }
        return jsonLines(records);
    }

    /**
     * Candidate content is independent of a private series slot's persisted
     * started-event ID. The archive still retains the linked lifecycle receipt;
     * this root captures only the replayable task/response/runtime material.
     */
    private static byte[] modelRoundContentLines(List<ArchivedModelRound> rounds) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (ArchivedModelRound round : rounds.stream().sorted(Comparator.comparing(value -> roundKey(value.task())))
                .toList()) {
            Map<String, Object> record = new TreeMap<>();
            record.put("schemaVersion", "candidate-model-round-content-v1");
            record.put("modelRoundId", round.modelRoundId());
            record.put("flowSliceId", round.task().flowSliceId());
            record.put("evidenceCapsuleId", round.task().evidenceCapsuleId());
            record.put("round", round.task().flowInterpretationRound());
            record.put("task", taskProjection(round.task()));
            applyImprovementTaskDigests(record, round.task());
            record.put("canonicalResponse", canonicalJsonObject(round.canonicalResponseJson()));
            record.put("canonicalResponseSha256", round.canonicalResponseSha256());
            record.put("semanticResponseSha256", round.semanticResponseSha256());
            record.put("observedRuntime", round.observedRuntime());
            record.put("startedReceiptId", round.startedReceiptId());
            records.add(record);
        }
        return jsonLines(records);
    }

    /** Rebuilds the same rootless content projection from the archived audit records. */
    static byte[] modelRoundContentArtifact(List<JsonNode> rounds) {
        if (rounds == null) {
            throw closureBroken();
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (JsonNode round : rounds) {
            if (round == null || !round.isObject()
                    || !"archived-model-round-v1".equals(requiredText(round, "schemaVersion"))) {
                throw closureBroken();
            }
            Map<String, Object> record = new TreeMap<>();
            record.put("schemaVersion", "candidate-model-round-content-v1");
            record.put("modelRoundId", required(round, "modelRoundId"));
            record.put("flowSliceId", required(round, "flowSliceId"));
            record.put("evidenceCapsuleId", required(round, "evidenceCapsuleId"));
            record.put("round", required(round, "round"));
            record.put("task", required(round, "task"));
            if (round.has("baseTaskInputSha256") || round.has("improvementOverlaySha256")) {
                record.put("baseTaskInputSha256", required(round, "baseTaskInputSha256"));
                record.put("improvementOverlaySha256", required(round, "improvementOverlaySha256"));
            }
            record.put("canonicalResponse", required(round, "canonicalResponse"));
            record.put("canonicalResponseSha256", required(round, "canonicalResponseSha256"));
            record.put("semanticResponseSha256", required(round, "semanticResponseSha256"));
            record.put("observedRuntime", required(round, "observedRuntime"));
            record.put("startedReceiptId", required(round, "startedReceiptId"));
            records.add(record);
        }
        return jsonLines(records);
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw closureBroken();
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isTextual()) {
            throw closureBroken();
        }
        return value.asText();
    }

    private static Map<String, Object> taskProjection(FlowModelTask task) {
        Map<String, Object> projection = new TreeMap<>();
        projection.put("schemaVersion", task.schemaVersion());
        projection.put("taskSpecId", task.taskSpecId());
        projection.put("taskKind", task.taskKind());
        projection.put("flowSliceId", task.flowSliceId());
        projection.put("evidenceCapsuleId", task.evidenceCapsuleId());
        projection.put("isolatedSessionKey", task.isolatedSessionKey());
        projection.put("flowInterpretationRound", task.flowInterpretationRound());
        projection.put("inputJson", canonicalJsonObject(task.inputJson()));
        projection.put("inputJsonSha256", task.inputJsonSha256());
        projection.put("outputSchemaJson", canonicalJsonObject(task.outputSchemaJson()));
        projection.put("outputSchemaSha256", task.outputSchemaSha256());
        projection.put("expectedRuntime", task.expectedRuntime());
        return projection;
    }

    /**
     * An improved task has one extra, finite input member.  Preserve the
     * original input bytes' identity separately from that member so archive
     * validation can prove the knowledge base was not widened.
     */
    private static void applyImprovementTaskDigests(Map<String, Object> record, FlowModelTask task) {
        ObjectNode input = (ObjectNode) canonicalJsonObject(task.inputJson()).deepCopy();
        JsonNode overlay = input.remove("improvementOverlay");
        if (overlay == null) {
            return;
        }
        if (!overlay.isObject()) {
            throw closureBroken();
        }
        record.put("baseTaskInputSha256", sha256(json(input)));
        record.put("improvementOverlaySha256", sha256(json(overlay)));
    }

    private static byte[] generationReceiptLines(List<GenerationRoundReceipt> receipts) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (GenerationRoundReceipt receipt : receipts.stream().sorted(Comparator.comparing(
                CandidateAssembler::receiptSortKey)).toList()) {
            Map<String, Object> record = new TreeMap<>();
            record.put("schemaVersion", "generation-round-receipt-v2");
            record.put("generationReceiptId", receipt.generationReceiptId());
            record.put("modelRoundId", receipt.modelRoundId());
            record.put("taskSpecId", receipt.taskSpecId());
            record.put("providerPolicyId", receipt.providerPolicyId());
            record.put("flowSliceId", receipt.flowSliceId());
            record.put("evidenceCapsuleId", receipt.evidenceCapsuleId());
            record.put("round", receipt.round());
            record.put("expectedRuntime", receipt.expectedRuntime());
            record.put("observedRuntime", receipt.observedRuntime());
            record.put("preflightReceiptId", receipt.preflightReceiptId());
            record.put("attemptId", receipt.attemptId());
            record.put("startedEventId", receipt.startedEventId());
            record.put("startedEventOrdinal", receipt.startedEventOrdinal());
            record.put("startedReceiptId", receipt.startedReceiptId());
            record.put("canonicalResponseSha256", receipt.canonicalResponseSha256());
            record.put("semanticResponseSha256", receipt.semanticResponseSha256());
            record.put("terminalStatus", receipt.terminalStatus());
            records.add(record);
        }
        return jsonLines(records);
    }

    private static String receiptSortKey(GenerationRoundReceipt receipt) {
        return receipt.flowSliceId() + "\n" + receipt.evidenceCapsuleId() + "\n" + receipt.round();
    }

    private static String roundKey(FlowModelTask task) {
        if (task == null || blank(task.flowSliceId()) || blank(task.evidenceCapsuleId())
                || (task.flowInterpretationRound() != 1 && task.flowInterpretationRound() != 2)) {
            throw closureBroken();
        }
        return task.flowSliceId() + "\n" + task.evidenceCapsuleId() + "\n" + task.flowInterpretationRound();
    }

    private static JsonNode canonicalJsonObject(String value) {
        if (blank(value)) {
            throw closureBroken();
        }
        try {
            JsonNode parsed = JSON.readTree(value);
            if (parsed == null || !parsed.isObject()) {
                throw closureBroken();
            }
            return canonicalNode(parsed);
        } catch (java.io.IOException invalid) {
            throw closureBroken();
        }
    }

    private static String candidateContentId(Stage01Result stage01, Stage02Result stage02, Stage03Result stage03,
                                             ArchivePreimages preimages) {
        return candidateContentId(stage03.renderedDocument().markdownSha256(), stage03.nineSectionPlan().nineSectionPlanId(),
                stage01.stage01ResultId(), stage02.stage02ResultId(), stage03.stage03ResultId(),
                sha256(preimages.sourceInput()), sha256(preimages.registryBundle()),
                sha256(preimages.modelRoundContent()), sha256(preimages.trace()));
    }

    /**
     * Candidate content commits replayable task/response/runtime material. The
     * complete model and lifecycle-receipt byte roots remain independently
     * committed by candidate.json and archive-manifest.json; their local
     * series event IDs are audit lineage, not product content.
     */
    static String candidateContentId(String documentSha256, String nineSectionPlanId, String stage01ResultId,
                                     String stage02ResultId, String stage03ResultId, String sourceInputRoot,
                                     String registryBundleRoot, String modelContentRoot, String traceRoot) {
        Map<String, Object> material = new TreeMap<>();
        material.put("archivePolicyId", ARCHIVE_POLICY_ID);
        material.put("documentSha256", documentSha256);
        material.put("modelContentRoot", modelContentRoot);
        material.put("nineSectionPlanId", nineSectionPlanId);
        material.put("registryBundleRoot", registryBundleRoot);
        material.put("schemaVersion", "candidate-content-v2");
        material.put("sourceInputRoot", sourceInputRoot);
        material.put("stage01ResultId", stage01ResultId);
        material.put("stage02ResultId", stage02ResultId);
        material.put("stage03ResultId", stage03ResultId);
        material.put("traceRoot", traceRoot);
        return "candidate-content:" + sha256(json(material));
    }

    private static String traceKind(ReaderItem item) {
        if (!item.basisAtomIds().isEmpty()) {
            return "FACT_SENTENCE";
        }
        if (!item.basisMeaningIds().isEmpty()) {
            return "ADMITTED_TERM";
        }
        if (!item.basisGapIds().isEmpty()) {
            return "GAP_QUESTION";
        }
        if (!item.referencedItemKeys().isEmpty()) {
            return "REFERENCE_ONLY";
        }
        return "TECHNICAL_FALLBACK";
    }

    private static byte[] jsonLines(List<Map<String, Object>> records) {
        StringBuilder lines = new StringBuilder();
        for (Map<String, Object> record : records) {
            lines.append(new String(json(record), StandardCharsets.UTF_8)).append('\n');
        }
        return lines.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] json(Object value) {
        try {
            return JSON.writeValueAsBytes(canonicalNode(JSON.valueToTree(value)));
        } catch (Exception failed) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
    }

    private static JsonNode canonicalNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = JSON.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> {
                if (!"snapshotRoot".equals(entry.getKey())) {
                    fields.put(entry.getKey(), entry.getValue());
                }
            });
            fields.forEach((key, value) -> object.set(key, canonicalNode(value)));
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = JSON.createArrayNode();
            for (JsonNode value : node) {
                array.add(canonicalNode(value));
            }
            return array;
        }
        return node;
    }

    private static <T> List<T> sorted(Collection<T> values, java.util.function.Function<T, String> key) {
        return values.stream().sorted(Comparator.comparing(key)).toList();
    }

    private static List<String> sortedStrings(Collection<String> values) {
        return values.stream().distinct().sorted().toList();
    }

    private static boolean validId(String value, String prefix) {
        return FilesystemCandidateStore.digestIdentifier(value, prefix);
    }

    private static boolean validSha(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean allZeroDigest(String identifier) {
        return identifier != null && identifier.endsWith("0".repeat(64));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean same(Object first, Object second) {
        return java.util.Objects.equals(first, second);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
    }

    private static M8Exception closureBroken() {
        return Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
    }

    private record ArchivePreimages(byte[] sourceInput, byte[] registryBundle, byte[] modelRounds,
                                    byte[] modelRoundContent, byte[] generationReceipts, byte[] trace) {
    }
}

record CandidateAssemblyRequest(CandidateSeriesRequest candidateSeriesRequest, CandidateLineage lineage,
                                RoundSlotView roundSlot, Stage01Result stage01Result, Stage02Result stage02Result,
                                Stage03Request stage03Request, Stage03Result stage03Result,
                                Stage03RunTranscript stage03RunTranscript) {
    CandidateAssemblyRequest(CandidateSeriesRequest candidateSeriesRequest, CandidateLineage lineage,
                             RoundSlotView roundSlot, Stage01Result stage01Result, Stage02Result stage02Result,
                             Stage03Request stage03Request, Stage03Result stage03Result) {
        this(candidateSeriesRequest, lineage, roundSlot, stage01Result, stage02Result, stage03Request,
                stage03Result, null);
    }

    CandidateAssemblyRequest(CandidateSeriesRequest candidateSeriesRequest, CandidateLineage lineage,
                             RoundSlotView roundSlot, Stage01Result stage01Result, Stage02Result stage02Result,
                             Stage03Result stage03Result) {
        this(candidateSeriesRequest, lineage, roundSlot, stage01Result, stage02Result, null, stage03Result, null);
    }
}
