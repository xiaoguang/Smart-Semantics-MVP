package com.linguan.codemd.stage04;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.CodeFact;
import com.linguan.codemd.stage01.FactAtom;
import com.linguan.codemd.stage01.Proof;
import com.linguan.codemd.stage01.ProofEdge;
import com.linguan.codemd.stage01.ProofNode;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage01.VerifiedFile;
import com.linguan.codemd.stage02.EvidenceProjectionProfileRef;
import com.linguan.codemd.stage02.FlowCompilationProfileRef;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02ResourceBudget;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.ExpectedRuntimeIdentity;
import com.linguan.codemd.stage03.FlowImprovementOverlay;
import com.linguan.codemd.stage03.FlowInterpretationProfileRef;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelRuntimePolicy;
import com.linguan.codemd.stage03.NineSectionGenerationProfileRef;
import com.linguan.codemd.stage03.ObservedRuntimeIdentity;
import com.linguan.codemd.stage03.RegistryBundle;
import com.linguan.codemd.stage03.RepositoryKnowledgeProfileRef;
import com.linguan.codemd.stage03.Stage03DeterministicReplay;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.Stage03ReplayRequest;
import com.linguan.codemd.stage03.Stage03ResourceBudget;
import com.linguan.codemd.stage03.Stage03Result;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static com.linguan.codemd.stage04.CandidateValidationSupport.canonicalBytes;
import static com.linguan.codemd.stage04.CandidateValidationSupport.concat;
import static com.linguan.codemd.stage04.CandidateValidationSupport.containsText;
import static com.linguan.codemd.stage04.CandidateValidationSupport.integer;
import static com.linguan.codemd.stage04.CandidateValidationSupport.parseCanonicalJson;
import static com.linguan.codemd.stage04.CandidateValidationSupport.parseCanonicalLines;
import static com.linguan.codemd.stage04.CandidateValidationSupport.sha256;
import static com.linguan.codemd.stage04.CandidateValidationSupport.strictUtf8;
import static com.linguan.codemd.stage04.CandidateValidationSupport.text;
import static com.linguan.codemd.stage04.CandidateValidationSupport.validDigest;

/**
 * Fresh-process Candidate validation and the factual-only D1 Trace path.  The
 * archive never provides an authoritative source excerpt: all source spans are
 * reopened through the registered frozen M1 request.
 */
@FunctionalInterface
interface SourceRegistry {
    Stage01Request resolve(String snapshotId);
}

final class CandidateValidationService {
    private static final String POLICY = "candidate-validation-policy-v2";
    private static final ObjectMapper REPLAY_JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private final Path workspace;
    private final SourceRegistry sources;
    private final CandidateStoreLimits limits;

    CandidateValidationService(Path workspace, SourceRegistry sources, CandidateStoreLimits limits) {
        Stage04Validation.require(workspace != null && sources != null && limits != null);
        this.workspace = workspace.toAbsolutePath().normalize();
        this.sources = sources;
        this.limits = limits;
    }

    ValidationReceipt validate(CandidateReference candidate) {
        return validate(candidate, null);
    }

    /** Recovery-only validation after the ledger has proven every archived started event. */
    ValidationReceipt validateForRecovery(CandidateReference candidate, RoundSlotView recoverySlot) {
        Stage04Validation.require(recoverySlot != null
                && RoundSlotState.STARTED_CONSUMED.name().equals(recoverySlot.slotState()));
        return validate(candidate, null);
    }

    private ValidationReceipt validate(CandidateReference candidate, RoundSlotView recoverySlot) {
        Stage04Validation.require(candidate != null);
        CandidateArchive archive = CandidateArchive.open(workspace, candidate, limits);
        List<ValidationCheck> checks = new ArrayList<>(archive.checks());
        checks.add(documentCheck(archive, candidate));
        checks.add(nineSectionCheck(archive));
        checks.add(sidecarCheck(archive, candidate));
        checks.add(candidateIdentityCheck(archive, candidate));
        checks.add(lifecycleEventClosureCheck(archive, recoverySlot));
        checks.add(sourceReplayCheck(archive));
        checks.add(archiveV2ClosureCheck(archive));
        checks.add(stage03ReplayCheck(archive));
        checks.sort(Comparator.comparing(ValidationCheck::checkCode));
        boolean valid = checks.stream().allMatch(check -> "PASS".equals(check.result()));
        ValidationReceipt receipt = receipt(candidate, archive.observedState(), valid, checks);
        installReceipt(receipt);
        return receipt;
    }

    private ValidationCheck documentCheck(CandidateArchive archive, CandidateReference candidate) {
        byte[] document = archive.bytes("document.md");
        return passOrFail("DOCUMENT_SHA256", document != null && candidate.documentSha256().equals(sha256(document)),
                M8FailureCode.DOCUMENT_HASH_MISMATCH);
    }

    private ValidationCheck nineSectionCheck(CandidateArchive archive) {
        byte[] document = archive.bytes("document.md");
        if (document == null) {
            return failed("NINE_SECTION_HEADINGS", M8FailureCode.NINE_SECTION_INVALID);
        }
        String value;
        try {
            value = strictUtf8(document);
        } catch (M8Exception invalid) {
            return failed("NINE_SECTION_HEADINGS", M8FailureCode.NINE_SECTION_INVALID);
        }
        List<String> headings = value.lines().filter(line -> line.startsWith("## "))
                .map(line -> line.substring(3)).toList();
        List<String> expected = List.of("文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项");
        return passOrFail("NINE_SECTION_HEADINGS", headings.equals(expected), M8FailureCode.NINE_SECTION_INVALID);
    }

    private ValidationCheck sidecarCheck(CandidateArchive archive, CandidateReference candidate) {
        JsonNode sidecar = archive.json("candidate.json");
        boolean matches = sidecar != null && candidate.candidateId().equals(text(sidecar, "candidateId"))
                && candidate.candidateContentId().equals(text(sidecar, "candidateContentId"))
                && candidate.documentSha256().equals(text(sidecar, "documentSha256"))
                && "candidate-v2".equals(text(sidecar, "schemaVersion"))
                && "archive-policy:immutable-candidate-v2".equals(text(sidecar, "archivePolicyId"))
                && candidate.seriesId().equals(text(sidecar, "seriesId"))
                && candidate.readerCandidateRound() == integer(sidecar, "readerCandidateRound")
                && candidate.status().equals(text(sidecar, "status"))
                && roundSlotMatches(sidecar)
                && validDigest(text(sidecar, "stage01ResultId"), "stage01-result:")
                && validDigest(text(sidecar, "stage02ResultId"), "stage02-result:")
                && validDigest(text(sidecar, "stage03ResultId"), "stage03-result:")
                && matchesArtifactSha(archive, "source-input.json", text(sidecar, "sourceInputSha256"))
                && matchesArtifactSha(archive, "registry-bundle.json", text(sidecar, "registryBundleSha256"))
                && matchesArtifactSha(archive, "model-rounds.jsonl", text(sidecar, "modelRoundsRoot"))
                && matchesArtifactSha(archive, "generation-receipts.jsonl", text(sidecar, "generationReceiptsRoot"))
                && matchesArtifactSha(archive, "trace.jsonl", text(sidecar, "traceRoot"));
        return passOrFail("CANDIDATE_SIDECAR", matches, M8FailureCode.ARCHIVE_MANIFEST_INVALID);
    }

    private static boolean roundSlotMatches(JsonNode sidecar) {
        String seriesId = text(sidecar, "seriesId");
        String slotId = text(sidecar, "roundSlotId");
        int round = integer(sidecar, "readerCandidateRound");
        if (!validDigest(seriesId, "series:") || !validDigest(slotId, "round-slot:") || (round != 1 && round != 2)) {
            return false;
        }
        String expected = "round-slot:" + Stage04Validation.sha256("round-slot-v1\n" + seriesId + "\n" + round);
        return expected.equals(slotId);
    }

    /** Every archived receipt must resolve to its exact durable started event. */
    private ValidationCheck lifecycleEventClosureCheck(CandidateArchive archive, RoundSlotView recoverySlot) {
        try {
            CandidateSeriesLedger ledger = new CandidateSeriesLedger(workspace);
            for (LifecycleReceiptOrigin origin : lifecycleReceiptOrigins(archive)) {
                LifecycleEventResolution resolved = ledger.resolveStartedEvent(origin.seriesId(), origin.readerRound(),
                        origin.roundSlotId(), text(origin.receipt(), "startedEventId"),
                        integer(origin.receipt(), "startedEventOrdinal"));
                if (resolved.evidence() != LifecycleEventEvidence.VERIFIED) {
                    return failed("LIFECYCLE_EVENT_CLOSURE", M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
                if (resolved.event().prestartAttemptCount() < 1
                        || !origin.roundSlotId().equals(resolved.slot().roundSlotId())
                        || !origin.seriesId().equals(resolved.slot().seriesId())
                        || origin.readerRound() != resolved.slot().readerCandidateRound()) {
                    return failed("LIFECYCLE_EVENT_CLOSURE", M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
            }
            return new ValidationCheck("LIFECYCLE_EVENT_CLOSURE", "PASS", null);
        } catch (RuntimeException invalid) {
            return failed("LIFECYCLE_EVENT_CLOSURE", M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
    }

    /**
     * Returns only the started-event references produced in this Candidate's
     * own reader slot. Recovery uses this closed set to restore an exact event
     * suffix before running the normal validator; copied Round-1 receipts in a
     * Round-2 archive retain their parent-slot origin.
     */
    List<LifecycleStartedEvent> currentSlotStartedEvents(CandidateReference candidate) {
        CandidateArchive archive = CandidateArchive.open(workspace, candidate, limits);
        JsonNode sidecar = archive.json("candidate.json");
        if (!roundSlotMatches(sidecar) || !candidate.candidateId().equals(text(sidecar, "candidateId"))) {
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        String seriesId = text(sidecar, "seriesId");
        String slotId = text(sidecar, "roundSlotId");
        int round = integer(sidecar, "readerCandidateRound");
        List<LifecycleStartedEvent> events = new ArrayList<>();
        for (LifecycleReceiptOrigin origin : lifecycleReceiptOrigins(archive)) {
            if (seriesId.equals(origin.seriesId()) && slotId.equals(origin.roundSlotId())
                    && round == origin.readerRound()) {
                events.add(new LifecycleStartedEvent(text(origin.receipt(), "startedEventId"),
                        integer(origin.receipt(), "startedEventOrdinal")));
            }
        }
        events.sort(Comparator.comparingInt(LifecycleStartedEvent::ordinal));
        for (int index = 1; index < events.size(); index++) {
            if (events.get(index - 1).ordinal() == events.get(index).ordinal()) {
                throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
            }
        }
        return List.copyOf(events);
    }

    /** Resolves every receipt to the slot that originally produced it. */
    private List<LifecycleReceiptOrigin> lifecycleReceiptOrigins(CandidateArchive archive) {
        JsonNode sidecar = archive.json("candidate.json");
        if (!roundSlotMatches(sidecar)) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        String seriesId = text(sidecar, "seriesId");
        String slotId = text(sidecar, "roundSlotId");
        int round = integer(sidecar, "readerCandidateRound");
        List<JsonNode> receipts = archive.jsonLines("generation-receipts.jsonl");
        if (round == 1) {
            return receipts.stream().map(receipt -> new LifecycleReceiptOrigin(receipt, seriesId, 1, slotId))
                    .toList();
        }

        String parentCandidateId = text(sidecar, "parentCandidateId");
        CandidateReference parentReference = CandidateArchive.referenceFor(workspace, parentCandidateId);
        CandidateArchive parent = CandidateArchive.open(workspace, parentReference, limits);
        JsonNode parentSidecar = parent.json("candidate.json");
        if (!roundSlotMatches(parentSidecar) || integer(parentSidecar, "readerCandidateRound") != 1
                || !seriesId.equals(text(parentSidecar, "seriesId"))
                || !parentCandidateId.equals(text(parentSidecar, "candidateId"))) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        Map<String, byte[]> parentReceipts = new TreeMap<>();
        for (JsonNode receipt : parent.jsonLines("generation-receipts.jsonl")) {
            String receiptId = text(receipt, "generationReceiptId");
            if (!validDigest(receiptId, "generation-receipt:")
                    || parentReceipts.put(receiptId, canonicalBytes(receipt)) != null) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
        }
        String parentSlotId = text(parentSidecar, "roundSlotId");
        List<LifecycleReceiptOrigin> origins = new ArrayList<>();
        for (JsonNode receipt : receipts) {
            byte[] parentBytes = parentReceipts.get(text(receipt, "generationReceiptId"));
            if (parentBytes != null && Arrays.equals(parentBytes, canonicalBytes(receipt))) {
                origins.add(new LifecycleReceiptOrigin(receipt, seriesId, 1, parentSlotId));
            } else {
                origins.add(new LifecycleReceiptOrigin(receipt, seriesId, 2, slotId));
            }
        }
        return List.copyOf(origins);
    }

    private record LifecycleReceiptOrigin(JsonNode receipt, String seriesId, int readerRound, String roundSlotId) {
        private boolean matches(RoundSlotView slot) {
            return slot != null && seriesId.equals(slot.seriesId()) && readerRound == slot.readerCandidateRound()
                    && roundSlotId.equals(slot.roundSlotId());
        }
    }

    private static boolean matchesArtifactSha(CandidateArchive archive, String artifact, String expectedSha256) {
        byte[] bytes = archive.bytes(artifact);
        return bytes != null && validDigest(expectedSha256, "") && sha256(bytes).equals(expectedSha256);
    }

    /** Recomputes the address from immutable artifacts and the canonical round lineage. */
    private ValidationCheck candidateIdentityCheck(CandidateArchive archive, CandidateReference candidate) {
        try {
            JsonNode sidecar = archive.json("candidate.json");
            JsonNode plan = archive.json("nine-section-plan.json");
            byte[] document = archive.bytes("document.md");
            byte[] source = archive.bytes("source-input.json");
            byte[] registry = archive.bytes("registry-bundle.json");
            byte[] models = archive.bytes("model-rounds.jsonl");
            byte[] receipts = archive.bytes("generation-receipts.jsonl");
            byte[] trace = archive.bytes("trace.jsonl");
            if (sidecar == null || plan == null || document == null || source == null || registry == null
                    || models == null || receipts == null || trace == null) {
                return failed("CANDIDATE_IDENTITY", M8FailureCode.ARCHIVE_MANIFEST_INVALID);
            }
            CandidateLineage lineage = sidecarLineage(sidecar);
            byte[] modelContent = CandidateAssembler.modelRoundContentArtifact(
                    archive.jsonLines("model-rounds.jsonl"));
            String contentId = CandidateAssembler.candidateContentId(sha256(document), text(plan, "nineSectionPlanId"),
                    text(sidecar, "stage01ResultId"), text(sidecar, "stage02ResultId"), text(sidecar, "stage03ResultId"),
                    sha256(source), sha256(registry), sha256(modelContent), sha256(trace));
            String candidateId = CandidateSeriesLedger.candidateId(contentId, text(sidecar, "seriesId"), lineage);
            boolean matches = contentId.equals(text(sidecar, "candidateContentId"))
                    && contentId.equals(candidate.candidateContentId())
                    && candidateId.equals(text(sidecar, "candidateId"))
                    && candidateId.equals(candidate.candidateId());
            return passOrFail("CANDIDATE_IDENTITY", matches, M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        } catch (RuntimeException invalid) {
            return failed("CANDIDATE_IDENTITY", M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
    }

    private static CandidateLineage sidecarLineage(JsonNode sidecar) {
        int round = integer(sidecar, "readerCandidateRound");
        if (round == 1) {
            if (sidecar.has("parentCandidateId") || sidecar.has("findingIds") || sidecar.has("correctiveAddendumId")) {
                throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
            }
            return new CandidateLineage(1, null, List.of(), null);
        }
        if (round != 2) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
        JsonNode findingIds = sidecar.get("findingIds");
        if (findingIds == null || !findingIds.isArray()) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
        List<String> findings = new ArrayList<>();
        for (JsonNode finding : findingIds) {
            if (!finding.isTextual()) {
                throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
            }
            findings.add(finding.asText());
        }
        return new CandidateLineage(2, text(sidecar, "parentCandidateId"), findings,
                text(sidecar, "correctiveAddendumId"));
    }

    private ValidationCheck sourceReplayCheck(CandidateArchive archive) {
        JsonNode snapshot = archive.json("verified-snapshot.json");
        JsonNode sidecar = archive.json("candidate.json");
        String snapshotId = text(snapshot, "snapshotId");
        if (!validDigest(snapshotId, "snapshot:")) {
            return failed("SOURCE_REOPEN", M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
        }
        Stage01Request request;
        try {
            request = sources.resolve(snapshotId);
        } catch (RuntimeException invalid) {
            return failed("SOURCE_REOPEN", M8FailureCode.SOURCE_REGISTRATION_INVALID);
        }
        if (request == null) {
            return failed("SOURCE_REOPEN", M8FailureCode.SOURCE_REGISTRATION_NOT_FOUND);
        }
        final Stage01Result replayed;
        try {
            replayed = new Stage01Analyzer().analyze(request);
        } catch (RuntimeException invalid) {
            return failed("SOURCE_REOPEN", M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
        }
        boolean sourceMatches = snapshotId.equals(replayed.verifiedSnapshot().snapshotId())
                && text(sidecar, "stage01ResultId").equals(replayed.stage01ResultId());
        if (!sourceMatches) {
            return failed("SOURCE_REOPEN", M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
        }
        try {
            return passOrFail("SOURCE_REOPEN", stage01ArtifactsMatch(archive, replayed),
                    M8FailureCode.TRACE_CLOSURE_BROKEN);
        } catch (RuntimeException invalid) {
            return failed("SOURCE_REOPEN", M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
    }

    /** Checks the immutable v2 preimage graph before attempting semantic replay. */
    private ValidationCheck archiveV2ClosureCheck(CandidateArchive archive) {
        try {
            JsonNode input = archive.json("source-input.json");
            JsonNode registries = archive.json("registry-bundle.json");
            JsonNode sidecar = archive.json("candidate.json");
            if (input == null || !"candidate-source-input-v2".equals(text(input, "schemaVersion"))
                    || !object(input, "stage01") || !object(input, "stage02") || !object(input, "stage03")
                    || registries == null || !"candidate-registry-bundle-v2".equals(text(registries, "schemaVersion"))
                    || !object(registries, "inputRegistryBundle") || !object(registries, "effectiveContract")
                    || sidecar == null) {
                return failed("ARCHIVE_V2_CLOSURE", M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            String stage01Id = text(sidecar, "stage01ResultId");
            String stage02Id = text(sidecar, "stage02ResultId");
            String stage03Id = text(sidecar, "stage03ResultId");
            JsonNode stage02 = input.get("stage02");
            JsonNode stage03 = input.get("stage03");
            if (!validDigest(stage01Id, "stage01-result:") || !validDigest(stage02Id, "stage02-result:")
                    || !validDigest(stage03Id, "stage03-result:")
                    || !stage01Id.equals(text(stage02, "expectedStage01ResultId"))
                    || !stage02Id.equals(text(stage03, "expectedStage02ResultId")) || !object(stage03,
                    "registryBundle")) {
                return failed("ARCHIVE_V2_CLOSURE", M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            JsonNode inputBundle = registries.get("inputRegistryBundle");
            if (!stage02Id.equals(text(stage03, "expectedStage02ResultId"))
                    || !text(inputBundle, "registryBundleId").equals(text(stage03.path("registryBundle"), "registryBundleId"))
                    || !exactRoundClosure(archive.jsonLines("model-rounds.jsonl"),
                    archive.jsonLines("generation-receipts.jsonl"))) {
                return failed("ARCHIVE_V2_CLOSURE", M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            return new ValidationCheck("ARCHIVE_V2_CLOSURE", "PASS", null);
        } catch (RuntimeException invalid) {
            return failed("ARCHIVE_V2_CLOSURE", M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
    }

    /** Replays M5--M7 from the archived, provider-free preimage. */
    private ValidationCheck stage03ReplayCheck(CandidateArchive archive) {
        try {
            JsonNode snapshot = archive.json("verified-snapshot.json");
            Stage01Request stage01 = sources.resolve(text(snapshot, "snapshotId"));
            if (stage01 == null) {
                return failed("STAGE03_DETERMINISTIC_REPLAY", M8FailureCode.SOURCE_REGISTRATION_NOT_FOUND);
            }
            JsonNode input = archive.json("source-input.json");
            JsonNode stage02Node = input == null ? null : input.get("stage02");
            JsonNode stage03Node = input == null ? null : input.get("stage03");
            JsonNode registryArchive = archive.json("registry-bundle.json");
            if (stage02Node == null || stage03Node == null || registryArchive == null) {
                return failed("STAGE03_DETERMINISTIC_REPLAY", M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            Stage02Request stage02Request = stage02Request(stage01, stage02Node);
            Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
            if (!stage02.stage02ResultId().equals(text(stage03Node, "expectedStage02ResultId"))) {
                return failed("STAGE03_DETERMINISTIC_REPLAY", M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
            }
            RegistryBundle registries = typed(registryArchive.get("inputRegistryBundle"), RegistryBundle.class);
            Stage03Request stage03Request = new Stage03Request("stage03-request-v1", stage02Request,
                    text(stage03Node, "expectedStage02ResultId"), registries,
                    typed(stage03Node.get("interpretationProfileRef"), FlowInterpretationProfileRef.class),
                    typed(stage03Node.get("knowledgeProfileRef"), RepositoryKnowledgeProfileRef.class),
                    typed(stage03Node.get("nineSectionProfileRef"), NineSectionGenerationProfileRef.class),
                    typed(stage03Node.get("modelRuntimePolicy"), ModelRuntimePolicy.class),
                    typed(stage03Node.get("resourceBudget"), Stage03ResourceBudget.class));
            Stage03ReplayRequest replay = new Stage03ReplayRequest("stage03-replay-request-v1", stage02Request,
                    stage02, stage03Request.expectedStage02ResultId(), registries,
                    stage03Request.interpretationProfileRef(), stage03Request.knowledgeProfileRef(),
                    stage03Request.nineSectionProfileRef(), stage03Request.modelRuntimePolicy(),
                    stage03Request.resourceBudget(),
                    improvementOverlays(archive), canonicalRounds(archive));
            Stage03Result replayed = new Stage03DeterministicReplay().replay(replay);
            Stage01Result replayedStage01 = new Stage01Analyzer().analyze(stage01);
            JsonNode sidecar = archive.json("candidate.json");
            boolean matches = replayed.stage03ResultId().equals(text(sidecar, "stage03ResultId"))
                    && replayed.renderedDocument().markdownSha256().equals(text(sidecar, "documentSha256"))
                    && replayed.nineSectionPlan().nineSectionPlanId().equals(text(archive.json("validation-baseline.json"),
                    "nineSectionPlanId"))
                    && stage02ArtifactsMatch(archive, stage02)
                    && stage03ArtifactsMatch(archive, replayedStage01, stage02, stage03Request, replayed);
            return passOrFail("STAGE03_DETERMINISTIC_REPLAY", matches, M8FailureCode.TRACE_CLOSURE_BROKEN);
        } catch (RuntimeException invalid) {
            return failed("STAGE03_DETERMINISTIC_REPLAY", M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
    }

    private static Stage02Request stage02Request(Stage01Request stage01, JsonNode node) {
        return new Stage02Request(text(node, "schemaVersion"), stage01, text(node, "expectedStage01ResultId"),
                typed(node.get("flowCompilationProfileRef"), FlowCompilationProfileRef.class),
                typed(node.get("evidenceProjectionProfileRef"), EvidenceProjectionProfileRef.class),
                typed(node.get("resourceBudget"), Stage02ResourceBudget.class));
    }

    private static boolean stage01ArtifactsMatch(CandidateArchive archive, Stage01Result replayed) {
        return artifactEquals(archive, "verified-snapshot.json", CandidateAssembler.verifiedSnapshotArtifact(replayed))
                && artifactEquals(archive, "repository-model.json", CandidateAssembler.repositoryModelArtifact(replayed))
                && artifactEquals(archive, "capability-report.json", CandidateAssembler.capabilityReportArtifact(replayed))
                && artifactEquals(archive, "proven-facts.json", CandidateAssembler.provenFactsArtifact(replayed))
                && artifactEquals(archive, "proof-pack.json", CandidateAssembler.proofPackArtifact(replayed))
                && artifactEquals(archive, "gap-ledger.json", CandidateAssembler.gapLedgerArtifact(replayed));
    }

    private static boolean stage02ArtifactsMatch(CandidateArchive archive, Stage02Result replayed) {
        return artifactEquals(archive, "flow-slices.json", CandidateAssembler.flowSlicesArtifact(replayed))
                && artifactEquals(archive, "evidence-capsules.json", CandidateAssembler.evidenceCapsulesArtifact(replayed));
    }

    private static boolean stage03ArtifactsMatch(CandidateArchive archive, Stage01Result stage01,
                                                  Stage02Result stage02, Stage03Request request,
                                                  Stage03Result replayed) {
        return artifactEquals(archive, "registry-bundle.json", CandidateAssembler.registryBundleArtifact(request))
                && artifactEquals(archive, "flow-interpretations.json",
                CandidateAssembler.flowInterpretationsArtifact(replayed))
                && artifactEquals(archive, "repository-business-model.json",
                CandidateAssembler.repositoryBusinessModelArtifact(replayed))
                && artifactEquals(archive, "nine-section-plan.json", CandidateAssembler.nineSectionPlanArtifact(replayed))
                && artifactEquals(archive, "trace.jsonl", CandidateAssembler.readerTraceArtifact(stage01, stage02,
                request, replayed))
                && artifactEquals(archive, "document.md",
                replayed.renderedDocument().markdown().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean artifactEquals(CandidateArchive archive, String name, byte[] expected) {
        byte[] actual = archive.bytes(name);
        return actual != null && Arrays.equals(actual, expected);
    }

    static List<CanonicalFlowRound> canonicalRounds(CandidateArchive archive) {
        List<JsonNode> models = archive.jsonLines("model-rounds.jsonl");
        List<CanonicalFlowRound> rounds = new ArrayList<>();
        for (JsonNode model : models) {
            JsonNode task = model.get("task");
            if (task == null || !task.isObject() || !object(model, "canonicalResponse")) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            FlowModelTask value = new FlowModelTask(text(task, "schemaVersion"), text(task, "taskSpecId"),
                    text(task, "taskKind"), text(task, "flowSliceId"), text(task, "evidenceCapsuleId"),
                    text(task, "isolatedSessionKey"), integer(task, "flowInterpretationRound"),
                    canonicalJsonString(task.get("inputJson")), text(task, "inputJsonSha256"),
                    canonicalJsonString(task.get("outputSchemaJson")), text(task, "outputSchemaSha256"),
                    typed(task.get("expectedRuntime"), ExpectedRuntimeIdentity.class));
            rounds.add(new CanonicalFlowRound("stage03-canonical-flow-round-v1", value,
                    canonicalJsonString(model.get("canonicalResponse")), text(model, "canonicalResponseSha256"),
                    text(model, "semanticResponseSha256"), typed(model.get("observedRuntime"),
                    ObservedRuntimeIdentity.class), text(model, "startedReceiptId")));
        }
        return List.copyOf(rounds);
    }

    /** Converts a previously validated archive's complete round evidence into an immutable reusable transcript. */
    static Stage03RunTranscript archivedTranscript(CandidateArchive archive) {
        List<JsonNode> models = archive.jsonLines("model-rounds.jsonl");
        List<JsonNode> receiptNodes = archive.jsonLines("generation-receipts.jsonl");
        if (!exactRoundClosure(models, receiptNodes)) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        Map<String, JsonNode> receipts = new TreeMap<>();
        for (JsonNode receipt : receiptNodes) {
            receipts.put(text(receipt, "modelRoundId"), receipt);
        }
        List<ArchivedModelRound> rounds = new ArrayList<>();
        List<GenerationRoundReceipt> generationReceipts = new ArrayList<>();
        for (JsonNode model : models) {
            JsonNode task = model.get("task");
            String modelRoundId = text(model, "modelRoundId");
            JsonNode receipt = receipts.get(modelRoundId);
            if (task == null || receipt == null) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            FlowModelTask taskValue = new FlowModelTask(text(task, "schemaVersion"), text(task, "taskSpecId"),
                    text(task, "taskKind"), text(task, "flowSliceId"), text(task, "evidenceCapsuleId"),
                    text(task, "isolatedSessionKey"), integer(task, "flowInterpretationRound"),
                    canonicalJsonString(task.get("inputJson")), text(task, "inputJsonSha256"),
                    canonicalJsonString(task.get("outputSchemaJson")), text(task, "outputSchemaSha256"),
                    typed(task.get("expectedRuntime"), ExpectedRuntimeIdentity.class));
            rounds.add(new ArchivedModelRound(modelRoundId, taskValue,
                    canonicalJsonString(model.get("canonicalResponse")), text(model, "canonicalResponseSha256"),
                    text(model, "semanticResponseSha256"), typed(model.get("observedRuntime"),
                    ObservedRuntimeIdentity.class), text(model, "startedReceiptId")));
            generationReceipts.add(new GenerationRoundReceipt(text(receipt, "generationReceiptId"), modelRoundId,
                    text(receipt, "taskSpecId"), text(receipt, "providerPolicyId"), text(receipt, "flowSliceId"),
                    text(receipt, "evidenceCapsuleId"),
                    integer(receipt, "round"), typed(receipt.get("expectedRuntime"), ExpectedRuntimeIdentity.class),
                    typed(receipt.get("observedRuntime"), ObservedRuntimeIdentity.class),
                    text(receipt, "preflightReceiptId"), text(receipt, "attemptId"), text(receipt, "startedEventId"),
                    integer(receipt, "startedEventOrdinal"), text(receipt, "startedReceiptId"),
                    text(receipt, "canonicalResponseSha256"), text(receipt, "semanticResponseSha256"),
                    text(receipt, "terminalStatus")));
        }
        return new Stage03RunTranscript(rounds, generationReceipts);
    }

    /**
     * The archive holds canonical task bytes.  Rebuild the finite overlay only
     * from those bytes, requiring the two round records for a Flow to agree,
     * so deterministic replay never uses a provider or inferred review prose.
     */
    private static List<FlowImprovementOverlay> improvementOverlays(CandidateArchive archive) {
        Map<String, FlowImprovementOverlay> byFlow = new TreeMap<>();
        for (JsonNode model : archive.jsonLines("model-rounds.jsonl")) {
            JsonNode task = model == null ? null : model.get("task");
            JsonNode input = task == null ? null : task.get("inputJson");
            if (task == null || !task.isObject() || input == null || !input.isObject()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            JsonNode overlayNode = input.get("improvementOverlay");
            if (overlayNode == null) {
                continue;
            }
            FlowImprovementOverlay overlay = typed(overlayNode, FlowImprovementOverlay.class);
            String flowSliceId = text(task, "flowSliceId");
            if (!flowSliceId.equals(overlay.flowSliceId())) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            FlowImprovementOverlay prior = byFlow.putIfAbsent(flowSliceId, overlay);
            if (prior != null && !prior.equals(overlay)) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
        }
        return List.copyOf(byFlow.values());
    }

    private static boolean exactRoundClosure(List<JsonNode> models, List<JsonNode> receipts) {
        if (models.size() != receipts.size()) {
            return false;
        }
        Map<String, JsonNode> receiptByModel = new TreeMap<>();
        for (JsonNode receipt : receipts) {
            if (receipt == null || !"generation-round-receipt-v2".equals(text(receipt, "schemaVersion"))
                    || receiptByModel.put(text(receipt, "modelRoundId"), receipt) != null
                    || !validDigest(text(receipt, "generationReceiptId"), "generation-receipt:")
                    || !validDigest(text(receipt, "modelRoundId"), "model-round:")
                    || !"ADMITTED".equals(text(receipt, "terminalStatus"))
                    || !object(receipt, "expectedRuntime") || !object(receipt, "observedRuntime")
                    || text(receipt, "providerPolicyId") == null || text(receipt, "preflightReceiptId") == null
                    || text(receipt, "attemptId") == null
                    || text(receipt, "startedEventId") == null || integer(receipt, "startedEventOrdinal") < 1
                    || text(receipt, "startedReceiptId") == null) {
                return false;
            }
        }
        Set<String> seen = new java.util.HashSet<>();
        for (JsonNode model : models) {
            JsonNode task = model == null ? null : model.get("task");
            String modelId = text(model, "modelRoundId");
            JsonNode receipt = receiptByModel.get(modelId);
            if (model == null || !"archived-model-round-v1".equals(text(model, "schemaVersion"))
                    || !validDigest(modelId, "model-round:") || task == null || !task.isObject()
                    || !object(task, "inputJson") || !object(task, "outputSchemaJson")
                    || !object(model, "canonicalResponse") || receipt == null
                    || !text(model, "generationReceiptId").equals(text(receipt, "generationReceiptId"))
                    || !text(task, "taskSpecId").equals(text(receipt, "taskSpecId"))
                    || !text(model, "flowSliceId").equals(text(task, "flowSliceId"))
                    || !text(model, "evidenceCapsuleId").equals(text(task, "evidenceCapsuleId"))
                    || integer(model, "round") != integer(task, "flowInterpretationRound")
                    || !text(model, "flowSliceId").equals(text(receipt, "flowSliceId"))
                    || !text(model, "evidenceCapsuleId").equals(text(receipt, "evidenceCapsuleId"))
                    || integer(model, "round") != integer(receipt, "round")
                    || !text(receipt, "flowSliceId").equals(text(task, "flowSliceId"))
                    || !text(receipt, "evidenceCapsuleId").equals(text(task, "evidenceCapsuleId"))
                    || integer(receipt, "round") != integer(task, "flowInterpretationRound")
                    || !text(model, "canonicalResponseSha256").equals(text(receipt, "canonicalResponseSha256"))
                    || !text(model, "semanticResponseSha256").equals(text(receipt, "semanticResponseSha256"))
                    || !seen.add(modelId)) {
                return false;
            }
            FlowModelTask taskValue;
            String response;
            ExpectedRuntimeIdentity receiptExpected;
            ObservedRuntimeIdentity modelObserved;
            ObservedRuntimeIdentity receiptObserved;
            try {
                taskValue = flowModelTask(task);
                response = canonicalJsonString(model.get("canonicalResponse"));
                receiptExpected = typed(receipt.get("expectedRuntime"), ExpectedRuntimeIdentity.class);
                modelObserved = typed(model.get("observedRuntime"), ObservedRuntimeIdentity.class);
                receiptObserved = typed(receipt.get("observedRuntime"), ObservedRuntimeIdentity.class);
            } catch (RuntimeException invalid) {
                return false;
            }
            if (!sha256(response.getBytes(StandardCharsets.UTF_8)).equals(text(model, "canonicalResponseSha256"))
                    || !modelId.equals(LifecycleProviderBridge.modelRoundId(taskValue,
                    text(model, "canonicalResponseSha256"), text(model, "semanticResponseSha256"),
                    modelObserved, text(model, "startedReceiptId")))
                    || !taskValue.expectedRuntime().equals(receiptExpected)
                    || !modelObserved.equals(receiptObserved)
                    || !text(model, "startedReceiptId").equals(text(receipt, "startedReceiptId"))
                    || !text(receipt, "generationReceiptId").equals(LifecycleProviderBridge.generationReceiptId(
                    modelId, text(receipt, "taskSpecId"), text(receipt, "providerPolicyId"),
                    text(receipt, "preflightReceiptId"), text(receipt, "attemptId"),
                    text(receipt, "startedEventId"), integer(receipt, "startedEventOrdinal"),
                    text(receipt, "startedReceiptId")))) {
                return false;
            }
            if (!improvementTaskClosure(model, task)) {
                return false;
            }
        }
        return seen.equals(receiptByModel.keySet());
    }

    private static FlowModelTask flowModelTask(JsonNode task) {
        return new FlowModelTask(text(task, "schemaVersion"), text(task, "taskSpecId"), text(task, "taskKind"),
                text(task, "flowSliceId"), text(task, "evidenceCapsuleId"), text(task, "isolatedSessionKey"),
                integer(task, "flowInterpretationRound"), canonicalJsonString(task.get("inputJson")),
                text(task, "inputJsonSha256"), canonicalJsonString(task.get("outputSchemaJson")),
                text(task, "outputSchemaSha256"), typed(task.get("expectedRuntime"), ExpectedRuntimeIdentity.class));
    }

    private static boolean improvementTaskClosure(JsonNode model, JsonNode task) {
        JsonNode input = task.get("inputJson");
        if (input == null || !input.isObject()) {
            return false;
        }
        JsonNode overlay = input.get("improvementOverlay");
        String baseDigest = text(model, "baseTaskInputSha256");
        String overlayDigest = text(model, "improvementOverlaySha256");
        if (overlay == null) {
            return baseDigest == null && overlayDigest == null;
        }
        if (!overlay.isObject() || !validDigest(baseDigest, "") || !validDigest(overlayDigest, "")) {
            return false;
        }
        ObjectNode base = ((ObjectNode) input).deepCopy();
        base.remove("improvementOverlay");
        try {
            FlowImprovementOverlay value = typed(overlay, FlowImprovementOverlay.class);
            return text(task, "flowSliceId").equals(value.flowSliceId())
                    && baseDigest.equals(sha256(canonicalBytes(base)))
                    && overlayDigest.equals(sha256(canonicalBytes(overlay)));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean object(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isObject();
    }

    private static String canonicalJsonString(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return new String(canonicalBytes(node), StandardCharsets.UTF_8);
    }

    private static <T> T typed(JsonNode node, Class<T> type) {
        if (node == null || node.isNull()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        try {
            return REPLAY_JSON.treeToValue(node, type);
        } catch (IOException | RuntimeException invalid) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
    }

    private ValidationReceipt receipt(CandidateReference candidate, String observedState, boolean valid,
                                      List<ValidationCheck> checks) {
        List<Map<String, String>> materialChecks = checks.stream()
                .map(check -> Map.of("checkCode", check.checkCode(), "findingCode",
                        check.findingCode() == null ? "" : check.findingCode(), "result", check.result()))
                .toList();
        String id = "validation-receipt:" + sha256(canonicalBytes(Map.of("candidateId", candidate.candidateId(),
                "checks", materialChecks, "observedState", observedState, "policy", POLICY,
                "schemaVersion", "validation-receipt-v2")));
        return new ValidationReceipt(id, candidate.candidateId(), valid, POLICY, checks);
    }

    private void installReceipt(ValidationReceipt receipt) {
        String digest = CandidateArchive.candidateDigest(receipt.candidateId());
        Path root = workspace.resolve("validations");
        Path candidateRoot = root.resolve(digest);
        CandidateArchive.requireNoSymlinkAncestor(workspace, M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK);
        CandidateArchive.requireNoSymlinkAncestor(root, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
        CandidateArchive.requireNoSymlinkAncestor(candidateRoot, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
        try {
            Files.createDirectories(candidateRoot);
            if (!Files.isDirectory(candidateRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(candidateRoot)) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            }
            Path target = candidateRoot.resolve(receipt.validationReceiptId().substring("validation-receipt:".length())
                    + ".json");
            byte[] bytes = canonicalBytes(Map.of("candidateId", receipt.candidateId(), "checks", receipt.checks(),
                    "policy", receipt.validatorPolicyId(), "schemaVersion", "validation-receipt-v2",
                    "valid", receipt.valid(), "validationReceiptId", receipt.validationReceiptId()));
            try {
                Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (java.nio.file.FileAlreadyExistsException alreadyPresent) {
                byte[] installed = CandidateValidationSupport.readBoundedRegular(target, limits.maxSidecarBytes(),
                        M8FailureCode.ARCHIVE_WRITE_FAILED);
                if (!Arrays.equals(bytes, installed)) {
                    throw Stage04Validation.failure(M8FailureCode.CANDIDATE_IDENTITY_COLLISION);
                }
            }
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (IOException failure) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
        }
    }

    private static ValidationCheck passOrFail(String check, boolean pass, M8FailureCode code) {
        return pass ? new ValidationCheck(check, "PASS", null) : failed(check, code);
    }

    private static ValidationCheck failed(String check, M8FailureCode code) {
        return new ValidationCheck(check, "FAIL", code.name());
    }
}

final class CandidateTraceResolver {
    private final Path workspace;
    private final SourceRegistry sources;
    private final CandidateStoreLimits limits;

    CandidateTraceResolver(Path workspace, SourceRegistry sources, CandidateStoreLimits limits) {
        Stage04Validation.require(workspace != null && sources != null && limits != null);
        this.workspace = workspace.toAbsolutePath().normalize();
        this.sources = sources;
        this.limits = limits;
    }

    TraceView trace(TraceQuery query) {
        Stage04Validation.require(query != null);
        CandidateReference reference = CandidateArchive.referenceFor(workspace, query.candidateId());
        ValidationReceipt validation = new CandidateValidationService(workspace, sources, limits).validate(reference);
        if (!validation.valid()) {
            if (validation.checks().stream().anyMatch(check -> M8FailureCode.SNAPSHOT_REOPEN_MISMATCH.name()
                    .equals(check.findingCode()))) {
                throw Stage04Validation.failure(M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
            }
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        CandidateArchive archive = CandidateArchive.open(workspace, reference, limits);
        TraceGraph graph = TraceGraph.from(archive);
        if (!graph.items().containsKey(query.readerItemKey())) {
            if (!graph.publicIds().contains(query.readerItemKey())) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            String modelId = text(archive.json("repository-business-model.json"), "repositoryBusinessModelId");
            if (modelId == null) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            return new TraceView(reference.candidateId(), query.readerItemKey(), "REFERENCE_ONLY",
                    List.of(new TraceHop("PUBLIC_REFERENCE", query.readerItemKey(), "ARCHIVED_IN", modelId)), List.of());
        }
        ResolutionState state = new ResolutionState(archive);
        Lineage lineage = resolveItem(query.readerItemKey(), graph, state, new java.util.HashSet<>());
        return new TraceView(reference.candidateId(), query.readerItemKey(), graph.kinds().get(query.readerItemKey()),
                lineage.hops(), lineage.sourceSpans());
    }

    private Lineage resolveItem(String itemKey, TraceGraph graph, ResolutionState state, Set<String> resolving) {
        if (!resolving.add(itemKey)) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        try {
            JsonNode item = graph.items().get(itemKey);
            if (item == null) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            return switch (graph.kinds().get(itemKey)) {
                case "FACT_SENTENCE" -> factLineage(itemKey, graph.values(item, "basisAtomIds"), state);
                case "ADMITTED_TERM" -> termLineage(itemKey, graph.values(item, "basisMeaningIds"), state);
                case "TECHNICAL_FALLBACK" -> fallbackLineage(itemKey, item, graph.record(itemKey), state);
                case "GAP_QUESTION" -> gapLineage(itemKey, graph.values(item, "basisGapIds"), state.archive());
                case "REFERENCE_ONLY" -> referenceLineage(itemKey, graph.values(item, "referencedItemKeys"),
                        graph, state, resolving);
                default -> throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            };
        } finally {
            resolving.remove(itemKey);
        }
    }

    private Lineage factLineage(String sourceId, List<String> atomIds, ResolutionState state) {
        Lineage lineage = Lineage.empty();
        for (String atomId : atomIds) {
            JsonNode fact = factFor(state.archive().json("proven-facts.json"), atomId);
            JsonNode atom = atomFor(fact, atomId);
            JsonNode proof = proofFor(state.archive().json("proof-pack.json"), text(atom, "proofId"));
            JsonNode node = proofNodeFor(state.archive().json("proof-pack.json"), text(proof, "rootProofNodeId"));
            SourceReplay source = state.sourceReplay();
            CodeFact replayFact = source.result().provenSourceFacts().provenFactSet().codeFacts().stream()
                    .filter(value -> value.factId().equals(text(fact, "factId"))).findFirst()
                    .orElseThrow(() -> Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN));
            FactAtom replayAtom = replayFact.atoms().stream().filter(value -> value.atomId().equals(atomId)).findFirst()
                    .orElseThrow(() -> Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN));
            Proof replayProof = source.result().provenSourceFacts().proofPack().proofs().stream()
                    .filter(value -> value.proofId().equals(replayAtom.proofId())).findFirst()
                    .orElseThrow(() -> Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN));
            ProofNode replayNode = source.result().provenSourceFacts().proofPack().nodes().stream()
                    .filter(value -> value.proofNodeId().equals(replayProof.rootProofNodeId())).findFirst()
                    .orElseThrow(() -> Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN));
            if (!replayProof.proofId().equals(text(proof, "proofId"))
                    || !replayProof.factId().equals(replayFact.factId())
                    || !replayProof.atomId().equals(replayAtom.atomId())
                    || !replayProof.rootProofNodeId().equals(text(proof, "rootProofNodeId"))
                    || !replayNode.proofNodeId().equals(text(node, "proofNodeId"))) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            lineage = lineage.plus(new TraceHop("READER_ITEM_TO_ATOM", sourceId, "BASIS_ATOM", atomId))
                    .plus(new TraceHop("ATOM_TO_FACT", atomId, "OWNED_BY", replayFact.factId()))
                    .plus(new TraceHop("FACT_TO_PROOF", replayFact.factId(), "PROVEN_BY_PROOF", replayProof.proofId()))
                    .plus(new TraceHop("PROOF_TO_PROOF_NODE", replayProof.proofId(), "ROOT", replayNode.proofNodeId()))
                    .plus(reopenSpan(source.request(), source.result(), replayNode))
                    .merge(proofDependencyClosure(state.archive().json("proof-pack.json"), proof, replayProof, source));
        }
        return lineage;
    }

    private Lineage termLineage(String itemKey, List<String> meaningIds, ResolutionState state) {
        Lineage lineage = Lineage.empty();
        for (String meaningId : meaningIds) {
            JsonNode meaning = uniqueByField(state.archive().json("flow-interpretations.json"), "meaningId", meaningId);
            String termKey = text(meaning, "businessTermKey");
            if (termKey == null || termKey.isBlank()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            JsonNode registry = inputRegistry(state.archive(), "businessTerms");
            String registryId = text(registry, "registryId");
            JsonNode term = uniqueDirectByField(registry.get("terms"), "businessTermKey", termKey);
            if (registryId == null || !term.path("priority").isIntegralNumber()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            List<String> basisAtoms = strictIds(meaning.get("basisAtomIds"));
            validateTermBinding(meaning, term, basisAtoms, state.archive());
            lineage = lineage.plus(new TraceHop("READER_ITEM_TO_MEANING", itemKey, "BASIS_MEANING", meaningId))
                    .plus(new TraceHop("MEANING_TO_BUSINESS_TERM", meaningId, "ADMITTED_TERM_KEY", termKey))
                    .plus(new TraceHop("BUSINESS_TERM_TO_REGISTRY_ENTRY", termKey, "FROZEN_REGISTRY_ENTRY", termKey))
                    .plus(new TraceHop("BUSINESS_TERM_TO_REGISTRY", termKey, "FROZEN_BUSINESS_TERM_REGISTRY",
                            registryId))
                    .plus(new TraceHop("BUSINESS_TERM_TO_PRIORITY", termKey, "REGISTRY_PRIORITY",
                            Integer.toString(term.path("priority").intValue())))
                    .merge(factLineage(meaningId, basisAtoms, state));
            for (String gapId : strictIds(meaning.get("basisGapIds"))) {
                if (!gapExists(gapId, state.archive())) {
                    throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
            }
        }
        return lineage;
    }

    private static JsonNode inputRegistry(CandidateArchive archive, String key) {
        JsonNode input = archive.json("registry-bundle.json").get("inputRegistryBundle");
        JsonNode registry = input == null ? null : input.get(key);
        if (registry == null || !registry.isObject()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return registry;
    }

    private static JsonNode uniqueDirectByField(JsonNode entries, String field, String expected) {
        JsonNode found = optionalDirectByField(entries, field, expected);
        if (found == null) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return found;
    }

    private static JsonNode optionalDirectByField(JsonNode entries, String field, String expected) {
        if (entries == null || !entries.isArray() || expected == null || expected.isBlank()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        JsonNode found = null;
        for (JsonNode entry : entries) {
            if (!entry.isObject()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            if (expected.equals(text(entry, field))) {
                if (found != null) {
                    throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
                found = entry;
            }
        }
        return found;
    }

    private Lineage fallbackLineage(String itemKey, JsonNode item, JsonNode persistedRecord,
                                    ResolutionState state) {
        if ("EMPTY_SECTION".equals(text(item, "itemKind"))) {
            return emptySectionLineage(itemKey, item, persistedRecord, state.archive());
        }
        String anchor = technicalAnchor(item);
        JsonNode resolution = uniqueOptionalByField(state.archive().json("flow-interpretations.json"), "anchorKey", anchor,
                "policyKey");
        if (resolution != null) {
            String policy = text(resolution, "policyKey");
            JsonNode frozenPolicy = uniqueDirectByField(inputRegistry(state.archive(), "technicalDisplays")
                    .get("policies"), "policyKey", policy);
            String template = text(frozenPolicy, "displayTemplateKey");
            List<TaskAnchorBinding> taskAnchors = taskAnchorsFor(state.archive(), anchor);
            JsonNode admittedBinding = technicalBindingFor(state.archive(), anchor);
            if (template == null || !template.equals(text(item, "templateKey"))
                    || !frozenPolicy.equals(admittedBinding.get("policy"))
                    || !text(resolution, "anchorKind").equals(text(frozenPolicy, "anchorKind"))
                    || !templateMatches(item, template)) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            List<String> bindings = strictIds(taskAnchors.get(0).anchor().get("provenBindings"));
            if (bindings.isEmpty()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            Lineage lineage = Lineage.empty()
                    .plus(new TraceHop("READER_ITEM_TO_ANCHOR", itemKey, "TECHNICAL_ANCHOR", anchor))
                    .plus(new TraceHop("ANCHOR_TO_POLICY", anchor, "TECHNICAL_FALLBACK_POLICY", policy))
                    .plus(new TraceHop("POLICY_TO_EFFECTIVE_TEMPLATE", policy, "READER_TEMPLATE", template));
            for (String selector : strictIds(frozenPolicy.get("resolutionOrder"))) {
                lineage = lineage.plus(new TraceHop("POLICY_TO_RESOLUTION_SELECTOR", policy, "RESOLUTION_ORDER",
                        selector));
            }
            for (TaskAnchorBinding taskAnchor : taskAnchors) {
                if (!strictIds(taskAnchor.anchor().get("provenBindings")).equals(bindings)) {
                    throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
                lineage = lineage.plus(new TraceHop("ANCHOR_TO_TASK_SPEC", anchor, "FROZEN_TASK_SPEC",
                                taskAnchor.taskSpecId()))
                        .plus(new TraceHop("TASK_SPEC_TO_ANCHOR", taskAnchor.taskSpecId(),
                                "UNIQUE_TASK_ANCHOR", anchor));
            }
            for (String binding : bindings) {
                lineage = lineage.plus(new TraceHop("ANCHOR_TO_PROVEN_BINDING", anchor, "BASIS", binding));
            }
            return lineage.merge(factLineage(anchor, atomBindings(bindings, state.archive()), state));
        }
        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
    }

    private static Lineage emptySectionLineage(String itemKey, JsonNode item, JsonNode persistedRecord,
                                                CandidateArchive archive) {
        String section = text(persistedRecord, "sectionKey");
        String profileId = text(persistedRecord, "profileId");
        String template = text(persistedRecord, "templateKey");
        JsonNode effectiveProfile = archive.json("registry-bundle.json").path("effectiveContract")
                .path("nineSectionProfile");
        if (!"EMPTY_SECTION".equals(text(persistedRecord, "fallbackSubtype"))
                || section == null || section.isBlank() || profileId == null || profileId.isBlank()
                || template == null || template.isBlank()
                || !section.equals(text(item, "ownerSectionKey"))
                || !template.equals(text(item, "templateKey"))
                || !profileId.equals(text(effectiveProfile, "profileId"))
                || nonEmptyItemsInOwnerSection(itemKey, archive) != 0) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return Lineage.empty().plus(new TraceHop("READER_ITEM_TO_ANCHOR", itemKey, "TECHNICAL_ANCHOR", section))
                .plus(new TraceHop("ANCHOR_TO_POLICY", section, "BUILT_IN_EMPTY_SECTION", template))
                .plus(new TraceHop("EMPTY_SECTION_TO_EFFECTIVE_TEMPLATE", itemKey, "READER_TEMPLATE", template))
                .plus(new TraceHop("EMPTY_SECTION_TO_ELIGIBLE_ITEMS", itemKey,
                        "ZERO_ELIGIBLE_READER_ITEMS", "0"));
    }

    private static Lineage gapLineage(String itemKey, List<String> gapIds, CandidateArchive archive) {
        Lineage lineage = Lineage.empty();
        for (String gapId : gapIds) {
            JsonNode stage01Gap = optionalDirectByField(archive.json("gap-ledger.json").get("expectationGaps"),
                    "gapId", gapId);
            if (stage01Gap != null) {
                lineage = lineage.plus(new TraceHop("READER_ITEM_TO_GAP", itemKey, "BASIS_GAP", gapId))
                        .merge(stage01GapProvenance(gapId, stage01Gap, archive));
                continue;
            }
            List<InterpretationGapOrigin> interpretations = interpretationGapOrigins(archive, gapId);
            if (!interpretations.isEmpty()) {
                lineage = lineage.plus(new TraceHop("READER_ITEM_TO_GAP", itemKey, "BASIS_GAP", gapId));
                for (InterpretationGapOrigin interpretation : interpretations) {
                    String sourceGapId = text(interpretation.gap(), "sourceGapId");
                    if (sourceGapId == null || sourceGapId.isBlank()) {
                        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                    }
                    lineage = lineage.plus(new TraceHop("GAP_TO_INTERPRETATION", gapId, "FLOW_INTERPRETATION_RESULT",
                                    interpretation.flowInterpretationResultId()))
                            .plus(new TraceHop("GAP_TO_SOURCE_GAP", gapId, "SOURCE_GAP_OR_ANCHOR", sourceGapId))
                            .merge(sourceGapProvenance(sourceGapId, archive));
                }
                continue;
            }
            FlowGapOrigin flow = flowGapOrigin(archive, gapId);
            if (flow != null) {
                lineage = lineage.plus(new TraceHop("READER_ITEM_TO_GAP", itemKey, "BASIS_GAP", gapId))
                        .merge(flowGapProvenance(gapId, flow));
                continue;
            }
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return lineage;
    }

    private static Lineage stage01GapProvenance(String gapId, JsonNode gap, CandidateArchive archive) {
        String reason = text(gap, "reasonCode");
        String questionKey = text(gap, "questionTemplateKey");
        String ledgerId = text(archive.json("gap-ledger.json"), "gapLedgerId");
        JsonNode absence = gap.get("absenceEvidence");
        JsonNode scopes = gap.get("searchedScope");
        JsonNode questions = inputRegistry(archive, "questions");
        String questionRegistryId = text(questions, "registryId");
        JsonNode question = optionalDirectByField(questions.get("questions"), "questionKey", questionKey);
        if (reason == null || questionKey == null || ledgerId == null || questionRegistryId == null
                || absence == null || !absence.isObject() || text(absence, "searchRuleId") == null
                || integer(absence, "matchedNodeCount") < 0 || scopes == null || !scopes.isArray() || scopes.isEmpty()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        Lineage lineage = Lineage.empty()
                .plus(new TraceHop("GAP_TO_LEDGER", gapId, "ARCHIVED_GAP_LEDGER", ledgerId))
                .plus(new TraceHop("GAP_TO_REASON", gapId, "REASON_CODE", reason))
                .plus(new TraceHop("GAP_TO_QUESTION_TEMPLATE", gapId, "QUESTION_TEMPLATE", questionKey))
                .plus(new TraceHop("GAP_TO_ABSENCE_SEARCH", gapId, "MISSING_EVIDENCE_SEARCH_RULE",
                        text(absence, "searchRuleId")))
                .plus(new TraceHop("GAP_TO_ABSENCE_COUNT", gapId, "MISSING_EVIDENCE_MATCHED_NODE_COUNT",
                        Integer.toString(integer(absence, "matchedNodeCount"))));
        if (question != null) {
            String readerTemplate = text(question, "readerTemplateKey");
            if (readerTemplate == null || !strictIds(question.get("allowedGapReasonCodes")).contains(reason)) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            lineage = lineage
                    .plus(new TraceHop("QUESTION_TEMPLATE_TO_REGISTRY", questionKey, "FROZEN_QUESTION_REGISTRY",
                            questionRegistryId))
                    .plus(new TraceHop("QUESTION_TEMPLATE_TO_READER_TEMPLATE", questionKey, "READER_TEMPLATE",
                            readerTemplate));
        }
        for (JsonNode scope : scopes) {
            String root = text(scope, "rootNodeId");
            String rule = text(scope, "scopeRuleId");
            if (root == null || rule == null) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            lineage = lineage.plus(new TraceHop("GAP_TO_SEARCHED_SCOPE", gapId, "SEARCHED_SCOPE_ROOT", root))
                    .plus(new TraceHop("GAP_TO_SEARCHED_SCOPE", gapId, "SEARCHED_SCOPE_RULE", rule));
        }
        return lineage;
    }

    private static Lineage sourceGapProvenance(String sourceGapId, CandidateArchive archive) {
        JsonNode stage01Gap = optionalDirectByField(archive.json("gap-ledger.json").get("expectationGaps"), "gapId",
                sourceGapId);
        if (stage01Gap != null) {
            return stage01GapProvenance(sourceGapId, stage01Gap, archive);
        }
        FlowGapOrigin flow = flowGapOrigin(archive, sourceGapId);
        if (flow != null) {
            return flowGapProvenance(sourceGapId, flow);
        }
        List<TaskAnchorBinding> anchors = taskAnchorsFor(archive, sourceGapId);
        Lineage lineage = Lineage.empty();
        for (TaskAnchorBinding anchor : anchors) {
            lineage = lineage.plus(new TraceHop("SOURCE_ANCHOR_TO_TASK_SPEC", sourceGapId, "FROZEN_TASK_SPEC",
                            anchor.taskSpecId()))
                    .plus(new TraceHop("TASK_SPEC_TO_ANCHOR", anchor.taskSpecId(), "UNIQUE_TASK_ANCHOR",
                            sourceGapId));
        }
        return lineage;
    }

    private static Lineage flowGapProvenance(String gapId, FlowGapOrigin flow) {
        String entryId = text(flow.gap(), "entryId");
        if (entryId == null || entryId.isBlank()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return Lineage.empty()
                .plus(new TraceHop("FLOW_GAP_TO_STAGE02", gapId, "STAGE02_RESULT", flow.stage02ResultId()))
                .plus(new TraceHop("FLOW_GAP_TO_ENTRY", gapId, "INCOMPLETE_ENTRY", entryId));
    }

    private static List<InterpretationGapOrigin> interpretationGapOrigins(CandidateArchive archive, String gapId) {
        List<InterpretationGapOrigin> found = new ArrayList<>();
        Set<String> resultIds = new HashSet<>();
        JsonNode values = archive.json("flow-interpretations.json");
        if (values == null || !values.isArray()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        for (JsonNode result : values) {
            String resultId = text(result, "flowInterpretationResultId");
            JsonNode gaps = result.get("interpretationGaps");
            if (resultId == null || gaps == null || !gaps.isArray()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            for (JsonNode gap : gaps) {
                if (!gap.isObject()) {
                    throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
                if (gapId.equals(text(gap, "interpretationGapId"))) {
                    if (!resultIds.add(resultId)) {
                        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                    }
                    found.add(new InterpretationGapOrigin(resultId, gap));
                }
            }
        }
        found.sort(Comparator.comparing(InterpretationGapOrigin::flowInterpretationResultId));
        return List.copyOf(found);
    }

    private static FlowGapOrigin flowGapOrigin(CandidateArchive archive, String gapId) {
        JsonNode root = archive.json("flow-slices.json");
        String stage02ResultId = text(root, "stage02ResultId");
        JsonNode gap = optionalDirectByField(root == null ? null : root.get("flowGaps"), "flowGapId", gapId);
        if (gap == null) {
            return null;
        }
        if (stage02ResultId == null || stage02ResultId.isBlank()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return new FlowGapOrigin(stage02ResultId, gap);
    }

    private record InterpretationGapOrigin(String flowInterpretationResultId, JsonNode gap) {
    }

    private record FlowGapOrigin(String stage02ResultId, JsonNode gap) {
    }

    private static List<TaskAnchorBinding> taskAnchorsFor(CandidateArchive archive, String anchorKey) {
        List<TaskAnchorBinding> found = new ArrayList<>();
        for (JsonNode model : archive.jsonLines("model-rounds.jsonl")) {
            JsonNode task = model.get("task");
            String taskSpecId = task == null ? null : text(task, "taskSpecId");
            JsonNode anchors = task == null ? null : task.path("inputJson").get("anchors");
            if (anchors == null || !anchors.isArray()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            JsonNode match = null;
            for (JsonNode anchor : anchors) {
                if (anchorKey.equals(text(anchor, "anchorKey"))) {
                    if (match != null) {
                        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                    }
                    match = anchor;
                }
            }
            if (match != null) {
                if (taskSpecId == null || taskSpecId.isBlank() || text(match, "anchorKind") == null
                        || text(match, "provenDisplay") == null) {
                    throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
                found.add(new TaskAnchorBinding(taskSpecId, match));
            }
        }
        if (found.isEmpty()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return List.copyOf(found);
    }

    private record TaskAnchorBinding(String taskSpecId, JsonNode anchor) {
    }

    private static JsonNode technicalBindingFor(CandidateArchive archive, String anchorKey) {
        JsonNode found = null;
        for (JsonNode model : archive.jsonLines("model-rounds.jsonl")) {
            JsonNode bindings = model.path("task").path("inputJson").path("registryAdmission")
                    .path("registries").path("technicalDisplays").get("anchorBindings");
            if (bindings == null || !bindings.isArray()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            for (JsonNode binding : bindings) {
                if (anchorKey.equals(text(binding, "anchorKey"))) {
                    if (binding.get("policy") == null || !binding.get("policy").isObject()
                            || (found != null && !found.equals(binding))) {
                        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                    }
                    found = binding;
                }
            }
        }
        if (found == null) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return found;
    }

    private static boolean templateMatches(JsonNode item, String templateKey) {
        if (templateKey == null || templateKey.isBlank() || !templateKey.equals(text(item, "templateKey"))) {
            return false;
        }
        JsonNode slots = item.get("slots");
        if (slots == null || !slots.isArray() || slots.size() != 1 || !"technical-display".equals(
                text(slots.get(0), "slotKey")) || text(slots.get(0), "value") == null) {
            return false;
        }
        String kind = text(item, "itemKind");
        if ("TECHNICAL_DISPLAY".equals(kind)) {
            return templateKey.matches("TECHNICAL_[A-Z0-9_]+_V1") && text(slots.get(0), "anchorKey") != null;
        }
        if ("EMPTY_SECTION".equals(kind)) {
            String owner = text(item, "ownerSectionKey");
            return owner != null && owner.matches("section-[1-9]")
                    && templateKey.equals("READER_EMPTY_SECTION_" + owner.substring("section-".length()) + "_V1")
                    && owner.equals(text(slots.get(0), "anchorKey"));
        }
        return false;
    }

    private static int nonEmptyItemsInOwnerSection(String itemKey, CandidateArchive archive) {
        JsonNode sections = archive.json("nine-section-plan.json").get("sections");
        if (sections == null || !sections.isArray()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        JsonNode owner = null;
        for (JsonNode section : sections) {
            for (JsonNode item : section.path("items")) {
                if (itemKey.equals(text(item, "readerItemKey"))) {
                    if (owner != null) {
                        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                    }
                    owner = section;
                }
            }
        }
        if (owner == null) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        int nonEmpty = 0;
        for (JsonNode item : owner.path("items")) {
            if (!"EMPTY_SECTION".equals(text(item, "itemKind"))) {
                nonEmpty++;
            }
        }
        return nonEmpty;
    }

    private static List<String> atomBindings(List<String> bindings, CandidateArchive archive) {
        Set<String> atoms = new java.util.TreeSet<>();
        Set<String> nodes = new java.util.TreeSet<>();
        for (String binding : bindings) {
            if (binding.startsWith("atom:")) {
                factFor(archive.json("proven-facts.json"), binding);
                atoms.add(binding);
            } else if (binding.startsWith("node:")) {
                nodes.add(binding);
            }
        }
        if (atoms.isEmpty() && !nodes.isEmpty()) {
            JsonNode facts = archive.json("proven-facts.json");
            for (JsonNode fact : facts.path("codeFacts")) {
                List<String> subjects = strictIds(fact.get("subjectNodeIds"));
                boolean linked = subjects.stream().anyMatch(nodes::contains);
                if (linked) {
                    for (JsonNode atom : fact.path("atoms")) {
                        String atomId = text(atom, "atomId");
                        if (atomId == null) {
                            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                        }
                        atoms.add(atomId);
                    }
                }
            }
        }
        if (atoms.isEmpty()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return List.copyOf(atoms);
    }

    private Lineage referenceLineage(String itemKey, List<String> references, TraceGraph graph, ResolutionState state,
                                     Set<String> resolving) {
        Lineage lineage = Lineage.empty();
        for (String reference : references) {
            lineage = lineage.plus(new TraceHop("READER_ITEM_TO_REFERENCE", itemKey, "REFERENCES", reference));
            if (graph.items().containsKey(reference)) {
                lineage = lineage.merge(resolveItem(reference, graph, state, resolving));
            } else if (!graph.publicIds().contains(reference)) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
        }
        return lineage;
    }

    private static JsonNode factFor(JsonNode facts, String atomId) {
        JsonNode codeFacts = facts == null ? null : facts.get("codeFacts");
        JsonNode found = null;
        if (codeFacts != null && codeFacts.isArray()) {
            for (JsonNode fact : codeFacts) {
                JsonNode atoms = fact.get("atoms");
                if (atoms != null && atoms.isArray()) {
                    for (JsonNode atom : atoms) {
                        if (atomId.equals(text(atom, "atomId"))) {
                            if (found != null) {
                                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                            }
                            found = fact;
                        }
                    }
                }
            }
        }
        if (found == null) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return found;
    }

    private static JsonNode atomFor(JsonNode fact, String atomId) {
        for (JsonNode atom : fact.path("atoms")) {
            if (atomId.equals(text(atom, "atomId"))) {
                return atom;
            }
        }
        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
    }

    private static JsonNode proofFor(JsonNode pack, String proofId) {
        for (JsonNode proof : pack.path("proofs")) {
            if (proofId != null && proofId.equals(text(proof, "proofId"))) {
                return proof;
            }
        }
        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
    }

    private static JsonNode proofNodeFor(JsonNode pack, String nodeId) {
        for (JsonNode node : pack.path("nodes")) {
            if (nodeId != null && nodeId.equals(text(node, "proofNodeId"))) {
                return node;
            }
        }
        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
    }

    /**
     * A factual Trace carries the entire frozen proof closure, rather than only
     * its root.  Each archived dependency is matched against a freshly proved
     * M2 closure before its source span is reopened.
     */
    private static Lineage proofDependencyClosure(JsonNode archivedPack, JsonNode archivedProof, Proof replayProof,
                                                  SourceReplay source) {
        if (!strictIds(archivedProof.get("requiredProofNodeIds")).equals(replayProof.requiredProofNodeIds())
                || !strictIds(archivedProof.get("requiredProofEdgeIds")).equals(replayProof.requiredProofEdgeIds())
                || !replayProof.status().equals(text(archivedProof, "status"))) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        Lineage lineage = Lineage.empty();
        for (String nodeId : replayProof.requiredProofNodeIds()) {
            JsonNode archivedNode = proofNodeFor(archivedPack, nodeId);
            ProofNode replayNode = source.result().provenSourceFacts().proofPack().nodes().stream()
                    .filter(value -> nodeId.equals(value.proofNodeId())).findFirst()
                    .orElseThrow(() -> Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN));
            if (!nodeId.equals(text(archivedNode, "proofNodeId"))
                    || !replayNode.repositoryNodeId().equals(text(archivedNode, "repositoryNodeId"))) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            lineage = lineage.plus(new TraceHop("PROOF_TO_PROOF_NODE", replayProof.proofId(), "REQUIRES", nodeId))
                    .plus(reopenSpan(source.request(), source.result(), replayNode));
        }
        for (String edgeId : replayProof.requiredProofEdgeIds()) {
            JsonNode archivedEdge = proofEdgeFor(archivedPack, edgeId);
            ProofEdge replayEdge = source.result().provenSourceFacts().proofPack().edges().stream()
                    .filter(value -> edgeId.equals(value.proofEdgeId())).findFirst()
                    .orElseThrow(() -> Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN));
            if (!edgeId.equals(text(archivedEdge, "proofEdgeId"))
                    || !replayEdge.repositoryEdgeId().equals(text(archivedEdge, "repositoryEdgeId"))
                    || !replayEdge.fromProofNodeId().equals(text(archivedEdge, "fromProofNodeId"))
                    || !replayEdge.toProofNodeId().equals(text(archivedEdge, "toProofNodeId"))
                    || !replayEdge.ruleId().equals(text(archivedEdge, "ruleId"))) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            proofNodeFor(archivedPack, replayEdge.fromProofNodeId());
            proofNodeFor(archivedPack, replayEdge.toProofNodeId());
            lineage = lineage.plus(new TraceHop("PROOF_TO_PROOF_EDGE", replayProof.proofId(), "REQUIRES", edgeId))
                    .plus(new TraceHop("PROOF_EDGE_TO_PROOF_NODE", edgeId, "FROM", replayEdge.fromProofNodeId()))
                    .plus(new TraceHop("PROOF_EDGE_TO_PROOF_NODE", edgeId, "TO", replayEdge.toProofNodeId()));
        }
        return lineage;
    }

    private static JsonNode proofEdgeFor(JsonNode pack, String edgeId) {
        JsonNode found = null;
        for (JsonNode edge : pack.path("edges")) {
            if (edgeId != null && edgeId.equals(text(edge, "proofEdgeId"))) {
                if (found != null) {
                    throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
                found = edge;
            }
        }
        if (found == null) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return found;
    }

    private static void validateTermBinding(JsonNode meaning, JsonNode term, List<String> basisAtomIds,
                                            CandidateArchive archive) {
        if (!text(meaning, "anchorKind").equals(text(term, "anchorKind"))
                || !text(meaning, "localizedValue").equals(text(term, "localizedValue"))) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        List<String> minimumBasis = strictIds(term.get("minimumBasisAtomIds"));
        if (!basisAtomIds.containsAll(minimumBasis)) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        List<String> eligibleKinds = strictIds(term.get("eligibleAtomKinds"));
        for (String atomId : basisAtomIds) {
            JsonNode atom = atomFor(factFor(archive.json("proven-facts.json"), atomId), atomId);
            String role = text(atom, "role");
            if (role == null || !eligibleKinds.contains(role)) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
        }
    }

    private SourceReplay reopenSource(CandidateArchive archive) {
        String snapshotId = text(archive.json("verified-snapshot.json"), "snapshotId");
        Stage01Request request;
        try {
            request = sources.resolve(snapshotId);
        } catch (RuntimeException invalid) {
            throw Stage04Validation.failure(M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
        }
        if (request == null) {
            throw Stage04Validation.failure(M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
        }
        try {
            return new SourceReplay(request, new Stage01Analyzer().analyze(request));
        } catch (RuntimeException invalid) {
            throw Stage04Validation.failure(M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
        }
    }

    private static VerifiedSourceSpan reopenSpan(Stage01Request request, Stage01Result replay, ProofNode node) {
        String relative = node.locator().path();
        Path root = request.frozenRepositoryRequest().snapshotRoot().toAbsolutePath().normalize();
        if (!safeRelative(relative)) {
            throw Stage04Validation.failure(M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
        }
        Path source = root.resolve(relative).normalize();
        if (!source.startsWith(root) || hasSymlink(root, source)
                || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw Stage04Validation.failure(M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
        }
        VerifiedFile file = replay.verifiedSnapshot().files().stream().filter(value -> relative.equals(value.path()))
                .findFirst().orElseThrow(() -> Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN));
        try {
            byte[] bytes = CandidateValidationSupport.readBoundedRegular(source, Math.max(1L, file.sizeBytes()),
                    M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
            int start = node.locator().startByte();
            int end = node.locator().endByteExclusive();
            if (bytes.length != file.sizeBytes() || !sha256(bytes).equals(file.sha256()) || start < 0 || end < start
                    || end > bytes.length) {
                throw Stage04Validation.failure(M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
            }
            byte[] excerpt = Arrays.copyOfRange(bytes, start, end);
            if (!sha256(excerpt).equals(node.spanSha256())) {
                throw Stage04Validation.failure(M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
            }
            return new VerifiedSourceSpan(relative, start, end, node.locator().startLine(), node.locator().startColumn(),
                    node.locator().endLine(), node.locator().endColumn(), file.sha256(), node.spanSha256(),
                    strictUtf8(excerpt), sha256(excerpt));
        } catch (M8Exception invalid) {
            throw invalid;
        }
    }

    static boolean safeRelative(String value) {
        try {
            if (value == null || value.isBlank() || value.indexOf(0) >= 0 || value.indexOf(92) >= 0
                    || value.matches("^[A-Za-z]:.*")) {
                return false;
            }
            Path path = Path.of(value);
            return !path.isAbsolute() && path.getNameCount() > 0 && !path.normalize().startsWith("..");
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean hasSymlink(Path root, Path file) {
        Path current = root;
        if (Files.isSymbolicLink(current)) {
            return true;
        }
        Path relative = root.relativize(file);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private static String technicalAnchor(JsonNode item) {
        JsonNode slots = item == null ? null : item.get("slots");
        String anchor = null;
        if (slots == null || !slots.isArray()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        for (JsonNode slot : slots) {
            String candidate = text(slot, "anchorKey");
            if (candidate != null) {
                if (anchor != null && !anchor.equals(candidate)) {
                    throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
                anchor = candidate;
            }
        }
        if (anchor == null || anchor.isBlank()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return anchor;
    }

    private static JsonNode uniqueByField(JsonNode root, String field, String id) {
        JsonNode found = uniqueOptionalByField(root, field, id, null);
        if (found == null) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return found;
    }

    private static JsonNode uniqueOptionalByField(JsonNode root, String field, String id, String requiredField) {
        if (id == null || id.isBlank()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        List<JsonNode> found = new ArrayList<>();
        collectByField(root, field, id, found);
        if (found.size() > 1) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        if (found.isEmpty()) {
            return null;
        }
        JsonNode result = found.get(0);
        if (requiredField != null && (text(result, requiredField) == null || text(result, requiredField).isBlank())) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return result;
    }

    private static void collectByField(JsonNode node, String field, String id, List<JsonNode> found) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            if (id.equals(text(node, field))) {
                found.add(node);
            }
            node.elements().forEachRemaining(value -> collectByField(value, field, id, found));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(value -> collectByField(value, field, id, found));
        }
    }

    private static List<String> strictIds(JsonNode values) {
        if (values == null || !values.isArray()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank() || !seen.add(value.asText())) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            result.add(value.asText());
        }
        return List.copyOf(result);
    }

    private static boolean gapExists(String gapId, CandidateArchive archive) {
        try {
            return optionalDirectByField(archive.json("gap-ledger.json").get("expectationGaps"), "gapId", gapId)
                    != null || !interpretationGapOrigins(archive, gapId).isEmpty() || flowGapOrigin(archive, gapId) != null;
        } catch (M8Exception missing) {
            return false;
        }
    }

    private final class ResolutionState {
        private final CandidateArchive archive;
        private SourceReplay sourceReplay;

        private ResolutionState(CandidateArchive archive) {
            this.archive = archive;
        }

        CandidateArchive archive() {
            return archive;
        }

        SourceReplay sourceReplay() {
            if (sourceReplay == null) {
                sourceReplay = reopenSource(archive);
            }
            return sourceReplay;
        }
    }

    private record SourceReplay(Stage01Request request, Stage01Result result) {
    }

    private record Lineage(List<TraceHop> hops, List<VerifiedSourceSpan> sourceSpans) {
        static Lineage empty() {
            return new Lineage(List.of(), List.of());
        }

        Lineage plus(TraceHop hop) {
            return merge(new Lineage(List.of(hop), List.of()));
        }

        Lineage plus(VerifiedSourceSpan span) {
            return merge(new Lineage(List.of(), List.of(span)));
        }

        Lineage merge(Lineage other) {
            Map<String, TraceHop> mergedHops = new LinkedHashMap<>();
            for (TraceHop hop : hops) {
                mergedHops.put(hop.hopType() + "\n" + hop.sourceId() + "\n" + hop.relation() + "\n" + hop.targetId(), hop);
            }
            for (TraceHop hop : other.hops) {
                mergedHops.put(hop.hopType() + "\n" + hop.sourceId() + "\n" + hop.relation() + "\n" + hop.targetId(), hop);
            }
            Map<String, VerifiedSourceSpan> mergedSpans = new LinkedHashMap<>();
            for (VerifiedSourceSpan span : sourceSpans) {
                mergedSpans.put(span.relativePath() + "\n" + span.startByte() + "\n" + span.endByteExclusive(), span);
            }
            for (VerifiedSourceSpan span : other.sourceSpans) {
                mergedSpans.put(span.relativePath() + "\n" + span.startByte() + "\n" + span.endByteExclusive(), span);
            }
            return new Lineage(List.copyOf(mergedHops.values()), List.copyOf(mergedSpans.values()));
        }
    }

    private record TraceGraph(Map<String, JsonNode> items, Map<String, String> kinds,
                              Map<String, JsonNode> records, Set<String> publicIds) {
        static TraceGraph from(CandidateArchive archive) {
            Map<String, JsonNode> items = new TreeMap<>();
            JsonNode plan = archive.json("nine-section-plan.json");
            JsonNode sections = plan == null ? null : plan.get("sections");
            if (sections == null || !sections.isArray()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            for (JsonNode section : sections) {
                JsonNode sectionItems = section.get("items");
                if (sectionItems == null || !sectionItems.isArray()) {
                    throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
                for (JsonNode item : sectionItems) {
                    String key = text(item, "readerItemKey");
                    if (key == null || key.isBlank() || items.put(key, item) != null) {
                        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                    }
                    for (String field : List.of("basisAtomIds", "basisMeaningIds", "basisGapIds",
                            "referencedItemKeys")) {
                        strictIds(item.get(field));
                    }
                }
            }
            Map<String, String> kinds = new TreeMap<>();
            for (Map.Entry<String, JsonNode> entry : items.entrySet()) {
                kinds.put(entry.getKey(), traceKind(entry.getValue()));
            }
            Map<String, JsonNode> records = new TreeMap<>();
            for (JsonNode record : archive.jsonLines("trace.jsonl")) {
                String key = text(record, "readerItemKey");
                if (key == null || records.put(key, record) != null || !items.containsKey(key)) {
                    throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
            }
            if (records.size() != items.size()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            for (Map.Entry<String, JsonNode> entry : items.entrySet()) {
                JsonNode record = records.get(entry.getKey());
                if (!kinds.get(entry.getKey()).equals(text(record, "traceKind"))) {
                    throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                }
                for (String field : List.of("basisAtomIds", "basisMeaningIds", "basisGapIds",
                        "referencedItemKeys")) {
                    if (!strictIds(entry.getValue().get(field)).equals(strictIds(record.get(field)))) {
                        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                    }
                }
            }
            Set<String> publicIds = publicIds(archive);
            for (Map.Entry<String, JsonNode> entry : items.entrySet()) {
                for (String reference : strictIds(entry.getValue().get("referencedItemKeys"))) {
                    if (reference.equals(entry.getKey()) || (!items.containsKey(reference) && !publicIds.contains(reference))) {
                        throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
                    }
                }
            }
            for (String key : items.keySet()) {
                visitReader(key, items, new java.util.HashSet<>(), new java.util.HashSet<>());
            }
            return new TraceGraph(Map.copyOf(items), Map.copyOf(kinds), Map.copyOf(records), Set.copyOf(publicIds));
        }

        List<String> values(JsonNode item, String field) {
            return strictIds(item.get(field));
        }

        JsonNode record(String itemKey) {
            JsonNode record = records.get(itemKey);
            if (record == null) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            return record;
        }

        private static String traceKind(JsonNode item) {
            List<String> atoms = strictIds(item.get("basisAtomIds"));
            List<String> meanings = strictIds(item.get("basisMeaningIds"));
            List<String> gaps = strictIds(item.get("basisGapIds"));
            List<String> references = strictIds(item.get("referencedItemKeys"));
            if (!atoms.isEmpty()) {
                return "FACT_SENTENCE";
            }
            if (!meanings.isEmpty()) {
                return "ADMITTED_TERM";
            }
            if (!gaps.isEmpty()) {
                return "GAP_QUESTION";
            }
            if (!references.isEmpty()) {
                return "REFERENCE_ONLY";
            }
            return "TECHNICAL_FALLBACK";
        }

        private static void visitReader(String key, Map<String, JsonNode> items, Set<String> visiting,
                                        Set<String> visited) {
            if (visited.contains(key)) {
                return;
            }
            if (!visiting.add(key)) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            for (String reference : strictIds(items.get(key).get("referencedItemKeys"))) {
                if (items.containsKey(reference)) {
                    visitReader(reference, items, visiting, visited);
                }
            }
            visiting.remove(key);
            visited.add(key);
        }

        private static Set<String> publicIds(CandidateArchive archive) {
            Set<String> result = new java.util.HashSet<>();
            for (String artifact : List.of("candidate.json", "proven-facts.json", "proof-pack.json", "gap-ledger.json",
                    "flow-slices.json", "evidence-capsules.json", "flow-interpretations.json",
                    "repository-business-model.json", "validation-baseline.json")) {
                collectPublicIds(archive.json(artifact), result);
            }
            return result;
        }

        private static void collectPublicIds(JsonNode node, Set<String> result) {
            if (node == null) {
                return;
            }
            if (node.isTextual()) {
                String value = node.asText();
                if (value.matches("[a-z][a-z0-9-]*:.+")) {
                    result.add(value);
                }
            } else if (node.isObject() || node.isArray()) {
                node.elements().forEachRemaining(value -> collectPublicIds(value, result));
            }
        }
    }
}

record ValidationReceipt(String validationReceiptId, String candidateId, boolean valid,
                         String validatorPolicyId, List<ValidationCheck> checks) {
    ValidationReceipt {
        Stage04Validation.identifier(validationReceiptId, "validation-receipt:");
        Stage04Validation.identifier(candidateId, "candidate:");
        Stage04Validation.requireText(validatorPolicyId);
        checks = List.copyOf(checks);
    }
}

record ValidationCheck(String checkCode, String result, String findingCode) {
    ValidationCheck {
        Stage04Validation.requireText(checkCode);
        Stage04Validation.require("PASS".equals(result) || "FAIL".equals(result));
        Stage04Validation.require(("PASS".equals(result) && findingCode == null)
                || ("FAIL".equals(result) && findingCode != null && !findingCode.isBlank()));
    }
}

record TraceQuery(String candidateId, String readerItemKey) {
    TraceQuery {
        Stage04Validation.identifier(candidateId, "candidate:");
        Stage04Validation.requireText(readerItemKey);
    }
}

record TraceView(String candidateId, String readerItemKey, String traceKind, List<TraceHop> hops,
                 List<VerifiedSourceSpan> sourceSpans) {
    TraceView {
        Stage04Validation.identifier(candidateId, "candidate:");
        Stage04Validation.requireText(readerItemKey);
        Stage04Validation.requireText(traceKind);
        hops = List.copyOf(hops);
        sourceSpans = List.copyOf(sourceSpans);
    }
}

record TraceHop(String hopType, String sourceId, String relation, String targetId) {
    TraceHop {
        Stage04Validation.requireText(hopType);
        Stage04Validation.requireText(sourceId);
        Stage04Validation.requireText(relation);
        Stage04Validation.requireText(targetId);
    }
}

record VerifiedSourceSpan(String relativePath, int startByte, int endByteExclusive, int startLine, int startColumn,
                          int endLine, int endColumn, String sourceFileSha256, String spanSha256,
                          String excerpt, String excerptSha256) {
    VerifiedSourceSpan {
        Stage04Validation.require(CandidateTraceResolver.safeRelative(relativePath) && startByte >= 0
                && endByteExclusive >= startByte && startLine >= 1 && startColumn >= 1 && endLine >= startLine
                && endColumn >= 1 && sourceFileSha256 != null && sourceFileSha256.matches("[0-9a-f]{64}")
                && spanSha256 != null && spanSha256.matches("[0-9a-f]{64}") && excerpt != null
                && excerptSha256 != null && excerptSha256.matches("[0-9a-f]{64}"));
    }
}

/** Strict, read-only view over the immutable archive format defined by C1. */
final class CandidateArchive {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final List<String> NON_MANIFEST = List.of("document.md", "candidate.json", "source-input.json",
            "verified-snapshot.json", "repository-model.json", "capability-report.json", "proven-facts.json",
            "proof-pack.json", "gap-ledger.json", "flow-slices.json", "evidence-capsules.json",
            "registry-bundle.json", "model-rounds.jsonl", "flow-interpretations.json",
            "repository-business-model.json", "nine-section-plan.json", "trace.jsonl",
            "generation-receipts.jsonl", "validation-baseline.json");
    private static final Set<String> EXPECTED = Set.copyOf(NON_MANIFEST);
    /** Bounds untrusted archive directory enumeration before materializing paths. */
    private static final int MAX_DIRECTORY_ENTRIES = 256;
    private final Map<String, byte[]> bytes;
    private final Map<String, JsonNode> json;
    private final Map<String, List<JsonNode>> jsonLines;
    private final List<ValidationCheck> checks;
    private final String state;

    private CandidateArchive(Map<String, byte[]> bytes, Map<String, JsonNode> json,
                             Map<String, List<JsonNode>> jsonLines, List<ValidationCheck> checks, String state) {
        this.bytes = bytes;
        this.json = json;
        this.jsonLines = jsonLines;
        this.checks = checks;
        this.state = state;
    }

    static CandidateArchive open(Path workspace, CandidateReference candidate, CandidateStoreLimits limits) {
        String digest = candidateDigest(candidate.candidateId());
        Path root = workspace.toAbsolutePath().normalize();
        Path directory = root.resolve("candidates").resolve(digest);
        requireNoSymlinkAncestor(root, M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK);
        requireNoSymlinkAncestor(directory, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
        Map<String, byte[]> bytes = new TreeMap<>();
        List<ValidationCheck> checks = new ArrayList<>();
        boolean structure = true;
        boolean oversized = false;
        try {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
                structure = false;
            } else {
                try (Stream<Path> values = Files.list(directory)) {
                    List<Path> files = new ArrayList<>();
                    var iterator = values.iterator();
                    while (iterator.hasNext()) {
                        if (files.size() == MAX_DIRECTORY_ENTRIES) {
                            oversized = true;
                            structure = false;
                            break;
                        }
                        files.add(iterator.next());
                    }
                    if (structure) {
                        Set<String> names = files.stream().map(path -> path.getFileName().toString())
                                .collect(java.util.stream.Collectors.toSet());
                        if (!names.equals(unionManifest()) || files.stream().anyMatch(Files::isSymbolicLink)
                                || files.stream().anyMatch(path -> !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))) {
                            structure = false;
                        }
                        long total = 0;
                        for (Path file : files) {
                            String name = file.getFileName().toString();
                            if (unionManifest().contains(name)) {
                                try {
                                    long size = CandidateValidationSupport.boundedRegularSize(file,
                                            limits.maxSidecarBytes(), M8FailureCode.ARCHIVE_MANIFEST_INVALID);
                                    total = Math.addExact(total, size);
                                    if (total > limits.maxCandidateBytes()) {
                                        throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
                                    }
                                } catch (M8Exception invalid) {
                                    if (M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name()
                                            .equals(invalid.failureCode())) {
                                        oversized = true;
                                    }
                                    structure = false;
                                }
                            }
                        }
                        if (structure) {
                            for (Path file : files) {
                                String name = file.getFileName().toString();
                                if (unionManifest().contains(name)) {
                                    bytes.put(name, CandidateValidationSupport.readBoundedRegular(file,
                                            limits.maxSidecarBytes(), M8FailureCode.ARCHIVE_MANIFEST_INVALID));
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException failure) {
            structure = false;
        }
        checks.add(new ValidationCheck("CANDIDATE_ARTIFACT_SIZE", oversized ? "FAIL" : "PASS",
                oversized ? M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name() : null));
        boolean manifest = structure && manifestMatches(bytes);
        checks.add(new ValidationCheck("ARCHIVE_MANIFEST", manifest ? "PASS" : "FAIL",
                manifest ? null : M8FailureCode.ARCHIVE_MANIFEST_INVALID.name()));
        Map<String, JsonNode> json = new TreeMap<>();
        Map<String, List<JsonNode>> lines = new TreeMap<>();
        boolean canonical = true;
        for (String name : unionManifest()) {
            byte[] content = bytes.get(name);
            if (content == null || "document.md".equals(name)) {
                continue;
            }
            try {
                String text = strictUtf8(content);
                if (name.endsWith(".json")) {
                    json.put(name, parseCanonicalJson(text, content));
                } else if (name.endsWith(".jsonl")) {
                    lines.put(name, parseCanonicalLines(text));
                }
            } catch (M8Exception invalid) {
                canonical = false;
            }
        }
        checks.add(new ValidationCheck("CANONICAL_ARTIFACTS", canonical ? "PASS" : "FAIL",
                canonical ? null : M8FailureCode.CANDIDATE_CANONICAL_JSON_INVALID.name()));
        String state = observedState(bytes);
        return new CandidateArchive(Map.copyOf(bytes), Map.copyOf(json), Map.copyOf(lines), List.copyOf(checks), state);
    }

    static CandidateReference referenceFor(Path workspace, String candidateId) {
        if (!FilesystemCandidateStore.digestIdentifier(candidateId, "candidate:")) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        String digest = candidateDigest(candidateId);
        Path sidecar = workspace.toAbsolutePath().normalize().resolve("candidates").resolve(digest).resolve("candidate.json");
        try {
            byte[] bytes = CandidateValidationSupport.readBoundedRegular(sidecar,
                    CandidateValidationSupport.DEFAULT_UNTRUSTED_RECORD_BYTES,
                    M8FailureCode.ARCHIVE_MANIFEST_INVALID);
            JsonNode node = parseCanonicalJson(strictUtf8(bytes), bytes);
            if (!candidateId.equals(text(node, "candidateId"))) {
                throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
            }
            return new CandidateReference(text(node, "seriesId"), integer(node, "readerCandidateRound"), candidateId,
                    text(node, "candidateContentId"), text(node, "documentSha256"), text(node, "status"));
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
    }

    byte[] bytes(String name) { return bytes.get(name); }
    JsonNode json(String name) { return json.get(name); }
    List<JsonNode> jsonLines(String name) { return jsonLines.getOrDefault(name, List.of()); }
    List<ValidationCheck> checks() { return checks; }
    String observedState() { return state; }

    static String candidateDigest(String candidateId) {
        if (!FilesystemCandidateStore.digestIdentifier(candidateId, "candidate:")) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        return candidateId.substring("candidate:".length());
    }

    static void requireNoSymlinkAncestor(Path requested, M8FailureCode code) {
        Path current = requested;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        if (current != null && Files.isSymbolicLink(current)) {
            throw Stage04Validation.failure(code);
        }
    }

    private static boolean manifestMatches(Map<String, byte[]> bytes) {
        try {
            if (!bytes.keySet().equals(unionManifest())) {
                return false;
            }
            JsonNode manifest = parseCanonicalJson(strictUtf8(bytes.get("archive-manifest.json")), bytes.get("archive-manifest.json"));
            JsonNode entries = manifest.get("entries");
            if (!"archive-manifest-v2".equals(text(manifest, "schemaVersion")) || entries == null || !entries.isArray()
                    || entries.size() != NON_MANIFEST.size()) {
                return false;
            }
            Map<String, JsonNode> listed = new TreeMap<>();
            for (JsonNode entry : entries) {
                String path = text(entry, "path");
                if (path == null || listed.put(path, entry) != null || !EXPECTED.contains(path)) {
                    return false;
                }
            }
            if (!listed.keySet().equals(EXPECTED)) {
                return false;
            }
            for (String name : NON_MANIFEST) {
                JsonNode entry = listed.get(name);
                if (entry == null || entry.path("size").asLong(-1) != bytes.get(name).length
                        || !sha256(bytes.get(name)).equals(text(entry, "sha256"))) {
                    return false;
                }
            }
            List<Map<String, Object>> entriesMaterial = new ArrayList<>();
            for (String name : NON_MANIFEST.stream().sorted().toList()) {
                JsonNode entry = listed.get(name);
                entriesMaterial.add(Map.of("path", name, "sha256", text(entry, "sha256"), "size", entry.get("size").longValue()));
            }
            String expectedId = "archive-manifest:" + sha256(concat("archive-manifest-v2\n",
                    canonicalBytes(Map.of("entries", entriesMaterial))));
            return expectedId.equals(text(manifest, "archiveManifestId"));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static Set<String> unionManifest() {
        java.util.TreeSet<String> values = new java.util.TreeSet<>(EXPECTED);
        values.add("archive-manifest.json");
        return Set.copyOf(values);
    }

    private static String observedState(Map<String, byte[]> bytes) {
        List<Map<String, Object>> entries = bytes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of("path", entry.getKey(), "sha256", sha256(entry.getValue()),
                        "size", entry.getValue().length)).toList();
        return sha256(canonicalBytes(Map.of("entries", entries)));
    }
}

/* Small shared canonical/UTF-8 helpers; archive input stays data-only. */
final class CandidateValidationSupport {
    static final long DEFAULT_UNTRUSTED_RECORD_BYTES = 200_000L;
    static final int DEFAULT_UNTRUSTED_DIRECTORY_ENTRIES = 256;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    private CandidateValidationSupport() {
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    static int integer(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.canConvertToInt() ? value.intValue() : Integer.MIN_VALUE;
    }

    static boolean validDigest(String value, String prefix) {
        return FilesystemCandidateStore.digestIdentifier(value, prefix);
    }

    static boolean containsText(JsonNode values, String expected) {
        if (values == null || !values.isArray()) {
            return false;
        }
        int count = 0;
        for (JsonNode value : values) {
            if (value.isTextual() && expected.equals(value.asText())) {
                count++;
            }
        }
        return count == 1;
    }

    static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_UTF8_INVALID);
        }
    }

    /**
     * Reads one local, untrusted record only after a no-follow size admission.
     * The streaming cap is repeated while reading and after close so a replaced
     * or growing file cannot turn a validation path into an unbounded allocation.
     */
    static byte[] readBoundedRegular(Path file, long maximumBytes, M8FailureCode unavailableCode) {
        Stage04Validation.require(file != null && maximumBytes > 0 && unavailableCode != null);
        try {
            java.nio.file.attribute.BasicFileAttributes before = Files.readAttributes(file,
                    java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (before.isSymbolicLink() || !before.isRegularFile()) {
                throw Stage04Validation.failure(unavailableCode);
            }
            if (before.size() > maximumBytes) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(before.size(), 8_192L));
            long copied = 0;
            java.util.Set<java.nio.file.OpenOption> options = java.util.Set.of(StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS);
            try (SeekableByteChannel channel = Files.newByteChannel(file, options)) {
                ByteBuffer buffer = ByteBuffer.allocate(8_192);
                while (channel.read(buffer) >= 0) {
                    buffer.flip();
                    int count = buffer.remaining();
                    if (count > 0) {
                        copied = Math.addExact(copied, count);
                        if (copied > maximumBytes) {
                            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
                        }
                        output.write(buffer.array(), buffer.arrayOffset() + buffer.position(), count);
                    }
                    buffer.clear();
                }
            }
            java.nio.file.attribute.BasicFileAttributes after = Files.readAttributes(file,
                    java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (after.isSymbolicLink() || !after.isRegularFile()) {
                throw Stage04Validation.failure(unavailableCode);
            }
            if (after.size() > maximumBytes) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
            }
            if (after.size() != copied) {
                throw Stage04Validation.failure(unavailableCode);
            }
            return output.toByteArray();
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (ArithmeticException overflow) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
        } catch (IOException unavailable) {
            throw Stage04Validation.failure(unavailableCode);
        }
    }

    /**
     * Materializes a finite directory only after admitting its directory node
     * without following links. The limit-plus-one entry is observed before it
     * can be retained, so callers never allocate an unbounded path list.
     */
    static List<Path> readBoundedDirectory(Path directory, int maximumEntries, M8FailureCode unavailableCode) {
        Stage04Validation.require(directory != null && maximumEntries > 0 && unavailableCode != null);
        Path normalized = directory.toAbsolutePath().normalize();
        try {
            java.nio.file.attribute.BasicFileAttributes before = Files.readAttributes(normalized,
                    java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (before.isSymbolicLink() || !before.isDirectory()) {
                throw Stage04Validation.failure(unavailableCode);
            }
            List<Path> entries = new ArrayList<>();
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(normalized)) {
                for (Path entry : stream) {
                    if (entries.size() >= maximumEntries) {
                        throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
                    }
                    Path child = entry.toAbsolutePath().normalize();
                    if (!normalized.equals(child.getParent())) {
                        throw Stage04Validation.failure(unavailableCode);
                    }
                    entries.add(child);
                }
            }
            java.nio.file.attribute.BasicFileAttributes after = Files.readAttributes(normalized,
                    java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (after.isSymbolicLink() || !after.isDirectory()) {
                throw Stage04Validation.failure(unavailableCode);
            }
            entries.sort(Comparator.comparing(path -> path.getFileName().toString()));
            return List.copyOf(entries);
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (IOException unavailable) {
            throw Stage04Validation.failure(unavailableCode);
        }
    }

    /**
     * Performs the no-follow, regular-file and hard-size admission used before
     * any caller allocates an archive/configuration byte array.  Callers that
     * need a collection budget can sum these admissions before opening files.
     */
    static long boundedRegularSize(Path file, long maximumBytes, M8FailureCode unavailableCode) {
        Stage04Validation.require(file != null && maximumBytes > 0 && unavailableCode != null);
        try {
            java.nio.file.attribute.BasicFileAttributes attributes = Files.readAttributes(file,
                    java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                throw Stage04Validation.failure(unavailableCode);
            }
            if (attributes.size() > maximumBytes) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
            }
            return attributes.size();
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (IOException invalid) {
            throw Stage04Validation.failure(unavailableCode);
        }
    }

    static JsonNode parseCanonicalJson(String text, byte[] original) {
        try (JsonParser parser = JSON.getFactory().createParser(text)) {
            JsonNode parsed = JSON.readTree(parser);
            if (parsed == null || parser.nextToken() != null || !Arrays.equals(original, canonicalBytes(parsed))) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_CANONICAL_JSON_INVALID);
            }
            return parsed;
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (IOException | RuntimeException invalid) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_CANONICAL_JSON_INVALID);
        }
    }

    static List<JsonNode> parseCanonicalLines(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        if (!text.endsWith("\n") || text.indexOf('\r') >= 0) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_CANONICAL_JSON_INVALID);
        }
        List<JsonNode> values = new ArrayList<>();
        for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
            if (line.isEmpty()) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_CANONICAL_JSON_INVALID);
            }
            values.add(parseCanonicalJson(line, line.getBytes(StandardCharsets.UTF_8)));
        }
        return List.copyOf(values);
    }

    static byte[] canonicalBytes(Object value) {
        try {
            return JSON.writeValueAsBytes(canonicalNode(JSON.valueToTree(value)));
        } catch (IOException | RuntimeException invalid) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
    }

    private static JsonNode canonicalNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> result.set(key, canonicalNode(value)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            for (JsonNode value : node) {
                result.add(canonicalNode(value));
            }
            return result;
        }
        return node;
    }

    static byte[] concat(String prefix, byte[] value) {
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] result = Arrays.copyOf(prefixBytes, prefixBytes.length + value.length);
        System.arraycopy(value, 0, result, prefixBytes.length, value.length);
        return result;
    }

    static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException unavailable) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
    }
}
