package com.linguan.codemd.stage03;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage02.AllowedAtomView;
import com.linguan.codemd.stage02.AllowedFactView;
import com.linguan.codemd.stage02.AllowedGapView;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage02.FlowGap;
import com.linguan.codemd.stage02.FlowSlice;
import com.linguan.codemd.stage02.FlowStep;
import com.linguan.codemd.stage02.ModelEvidenceSpan;
import com.linguan.codemd.stage02.OutcomePath;
import com.linguan.codemd.stage02.ProjectionObligation;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage01.ExpectationGap;
import com.linguan.codemd.stage01.FlowEdgeView;
import com.linguan.codemd.stage01.FlowEntryView;
import com.linguan.codemd.stage01.FlowNodeView;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01FlowView;
import com.linguan.codemd.stage01.Stage01Result;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * M5--M7 controlled generation.  The provider can only select frozen keys; this
 * class replays Stage 02, validates both rounds, assembles facts, and renders the
 * final reader document itself.
 */
public final class Stage03Generator {
    private static final String RESULT_SCHEMA = "stage03-result-v1";
    private static final String INTERPRETATION_SCHEMA = "flow-interpretation-result-v1";
    private static final String PLAN_SCHEMA = "nine-section-plan-v1";
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> HEADINGS = List.of("文档说明", "业务目标", "业务对象", "业务活动",
            "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项");
    private static final Set<String> R1_ROOT = Set.of("schemaVersion", "taskSpecId", "flowSliceId",
            "evidenceCapsuleId", "proposals");
    private static final Set<String> R2_ROOT = Set.of("schemaVersion", "taskSpecId", "flowSliceId",
            "evidenceCapsuleId", "reviews");
    private static final Set<String> R1_TERM = Set.of("proposalKey", "proposalType", "targetAnchorKey",
            "businessTermKey", "basisAtomIds");
    private static final Set<String> R1_CLAIM = Set.of("proposalKey", "proposalType", "targetAnchorKey",
            "businessTermKey", "claimKeys", "basisAtomIds");
    private static final Set<String> R1_QUESTION = Set.of("proposalKey", "proposalType", "questionKey",
            "basisGapIds");
    private static final Set<String> R2_REVIEW = Set.of("proposalKey", "decision", "retainedBusinessTermKey",
            "retainedClaimKeys", "retainedBasisAtomIds", "retainedBasisGapIds");
    private static final Set<String> R2_DECISIONS = Set.of("KEEP", "NARROW", "DROP", "NEEDS_EVIDENCE");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern TEMPLATE_SLOT = Pattern.compile("\\{([a-z][a-z-]*)}");
    private static final Pattern UNIX_ABSOLUTE_PATH = Pattern.compile(
            "(?<![A-Za-z0-9_.:/-])/(?:[A-Za-z0-9_.-]+/)+[A-Za-z0-9_.-]+");
    private static final Pattern WINDOWS_DRIVE_ABSOLUTE_PATH = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_.-])[a-z]:[\\\\/](?:[A-Za-z0-9_. -]+[\\\\/])+[A-Za-z0-9_. -]+");
    private static final Pattern WINDOWS_UNC_PATH = Pattern.compile(
            "(?<![A-Za-z0-9_.-])\\\\\\\\[A-Za-z0-9_.-]+\\\\[A-Za-z0-9_.-]+(?:\\\\[A-Za-z0-9_.-]+)*");
    private static final Pattern RELATIVE_MULTI_SEGMENT_PATH = Pattern.compile(
            "(?<![A-Za-z0-9_.:/-])[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+){2,}");

    /** Atomically produces a deterministic result or throws a stable Stage 03 failure. */
    public Stage03Result generate(Stage03Request request, StructuredModelProvider provider) {
        return generate(request, provider, List.of());
    }

    /** Runs the normal M5--M7 protocol with finite, prevalidated Round-2 overlays only. */
    public Stage03Result generate(Stage03Request request, StructuredModelProvider provider,
                                  List<FlowImprovementOverlay> improvementOverlays) {
        return generate(request, provider, improvementOverlays, List.of());
    }

    /**
     * Round-2 may replay an unchanged Flow's already admitted canonical pair.
     * Only flows carrying an overlay can cross the provider boundary again.
     */
    public Stage03Result generate(Stage03Request request, StructuredModelProvider provider,
                                  List<FlowImprovementOverlay> improvementOverlays,
                                  List<CanonicalFlowRound> reusableCanonicalRounds) {
        validateRequest(request, provider);
        Stage01Analyzer stage01Analyzer = new Stage01Analyzer();
        Stage01Result stage01 = stage01Analyzer.analyze(request.stage02Request().stage01Request());
        Stage01FlowView flowView = stage01Analyzer.flowView(stage01);
        Stage02Result stage02 = new Stage02Compiler().compile(request.stage02Request());
        if (!stage01.stage01ResultId().equals(stage02.stage01ResultId())
                || !request.expectedStage02ResultId().equals(stage02.stage02ResultId())) {
            throw failure(Stage03FailureCode.STAGE02_RESULT_REPLAY_MISMATCH);
        }
        Map<String, FlowImprovementOverlay> overlays = indexOverlays(improvementOverlays);
        RoundSource rounds = reusableCanonicalRounds == null || reusableCanonicalRounds.isEmpty()
                ? new ProviderRounds(provider)
                : new ReusedProviderRounds(provider, reusableCanonicalRounds, overlays.keySet());
        return execute(request, stage01, flowView, stage02, rounds, overlays);
    }

    Stage03Result replay(Stage03ReplayRequest replay) {
        Stage03Request request = replayRequest(replay);
        Stage01Analyzer stage01Analyzer = new Stage01Analyzer();
        Stage01Result stage01 = stage01Analyzer.analyze(request.stage02Request().stage01Request());
        Stage01FlowView flowView = stage01Analyzer.flowView(stage01);
        Stage02Result stage02 = replay.replayedStage02Result();
        if (!request.expectedStage02ResultId().equals(stage02.stage02ResultId())
                || !stage01.stage01ResultId().equals(stage02.stage01ResultId())
                || !request.stage02Request().expectedStage01ResultId().equals(stage02.stage01ResultId())) {
            throw failure(Stage03FailureCode.STAGE02_RESULT_REPLAY_MISMATCH);
        }
        return execute(request, stage01, flowView, stage02, new ReplayRounds(replay.canonicalRounds()),
                indexOverlays(replay.improvementOverlays()));
    }

    private Stage03Result execute(Stage03Request request, Stage01Result stage01, Stage01FlowView flowView,
                                  Stage02Result stage02, RoundSource rounds,
                                  Map<String, FlowImprovementOverlay> improvementOverlays) {
        Map<String, String> stage01GapReasons = stage01.provenSourceFacts().gapLedger().expectationGaps().stream()
                .collect(java.util.stream.Collectors.toMap(ExpectationGap::gapId, ExpectationGap::reasonCode));
        RegistryIndex registries = RegistryIndex.of(request.registryBundle());
        if (stage02.flowSlices().size() > request.resourceBudget().maxFlowInterpretations()) {
            throw failure(Stage03FailureCode.RESOURCE_LIMIT_EXCEEDED);
        }

        Map<String, EvidenceCapsule> capsuleByFlow = capsulesByFlow(stage02.evidenceCapsules());
        List<FlowSlice> compiledFlows = stage02.flowSlices().stream()
                .sorted(Comparator.comparing(FlowSlice::flowSliceId)).toList();
        Set<String> knownFlows = compiledFlows.stream().map(FlowSlice::flowSliceId)
                .collect(java.util.stream.Collectors.toSet());
        if (!knownFlows.containsAll(improvementOverlays.keySet())) {
            throw failure(Stage03FailureCode.STAGE03_REQUEST_INVALID);
        }
        for (FlowSlice flow : compiledFlows) {
            EvidenceCapsule capsule = capsuleByFlow.get(flow.flowSliceId());
            if (capsule == null) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }
            Stage03CapsuleClosureValidator.validate(request.stage02Request().stage01Request(), stage01, stage02,
                    flow, capsule);
        }
        rounds.validateRoundSet(compiledFlows, capsuleByFlow);
        List<FlowInterpretationResult> interpretations = new ArrayList<>();
        for (FlowSlice flow : compiledFlows) {
            EvidenceCapsule capsule = capsuleByFlow.get(flow.flowSliceId());
            interpretations.add(interpret(flow, capsule, stage02, flowView, stage01GapReasons, request, registries,
                    rounds, improvementOverlays.get(flow.flowSliceId())));
        }
        interpretations.sort(Comparator.comparing(FlowInterpretationResult::flowSliceId));
        RepositoryBusinessModel model = assemble(stage02, interpretations, request, registries);
        NineSectionPlan plan = plan(model, stage02, interpretations, registries, request);
        RenderedNineSectionDocument document = render(plan, registries.readerContracts(), request);
        String resultId = "stage03-result:" + sha256(RESULT_SCHEMA + "\n" + stage02.stage02ResultId() + "\n"
                + request.registryBundle().registryBundleId() + "\n" + model.repositoryBusinessModelId() + "\n"
                + plan.nineSectionPlanId() + "\n" + document.markdownSha256() + "\n" + interpretations + "\n"
                + request.resourceBudget());
        return new Stage03Result(RESULT_SCHEMA, resultId, stage02.stage02ResultId(),
                request.registryBundle().registryBundleId(), interpretations, model, plan, document,
                new Stage03Validation(List.of("REPLAYED_STAGE02", "VALIDATED_R1_R2", "NINE_SECTIONS",
                        "CONSERVATION_CLOSED")), rounds.canonicalRounds());
    }

    private static void validateRequest(Stage03Request request, StructuredModelProvider provider) {
        if (provider == null) {
            throw failure(Stage03FailureCode.STAGE03_REQUEST_INVALID);
        }
        validateRequest(request, Stage03FailureCode.STAGE03_REQUEST_INVALID);
    }

    private static Stage03Request replayRequest(Stage03ReplayRequest replay) {
        if (replay == null || !"stage03-replay-request-v1".equals(replay.schemaVersion())
                || replay.replayedStage02Result() == null || replay.canonicalRounds() == null) {
            throw failure(Stage03FailureCode.STAGE03_REPLAY_INPUT_INVALID);
        }
        Stage03Request request = new Stage03Request("stage03-request-v1", replay.stage02Request(),
                replay.expectedStage02ResultId(), replay.registryBundle(), replay.interpretationProfileRef(),
                replay.knowledgeProfileRef(), replay.nineSectionProfileRef(), replay.modelRuntimePolicy(),
                replay.resourceBudget());
        validateRequest(request, Stage03FailureCode.STAGE03_REPLAY_INPUT_INVALID);
        return request;
    }

    private static void validateRequest(Stage03Request request, Stage03FailureCode code) {
        if (request == null || !"stage03-request-v1".equals(request.schemaVersion())
                || request.stage02Request() == null || blank(request.expectedStage02ResultId())
                || request.registryBundle() == null || request.resourceBudget() == null
                || request.modelRuntimePolicy() == null || request.interpretationProfileRef() == null
                || request.knowledgeProfileRef() == null || request.nineSectionProfileRef() == null
                || request.resourceBudget().roundsPerCapsule() != 2
                || request.resourceBudget().requiredSectionCount() != HEADINGS.size()
                || request.resourceBudget().maxTaskInputBytes() <= 0
                || request.resourceBudget().maxResponseBytes() <= 0
                || request.resourceBudget().maxDocumentBytes() <= 0) {
            throw failure(code);
        }
        validateProfile(request.interpretationProfileRef().profileId(), request.interpretationProfileRef().sha256(), code);
        validateProfile(request.knowledgeProfileRef().profileId(), request.knowledgeProfileRef().sha256(), code);
        validateProfile(request.nineSectionProfileRef().profileId(), request.nineSectionProfileRef().sha256(), code);
        ModelRuntimePolicy runtime = request.modelRuntimePolicy();
        if (blank(runtime.configuredAdapterId()) || blank(runtime.configuredAuthMode())
                || blank(runtime.expectedUpstreamProvider()) || blank(runtime.expectedModel())
                || blank(runtime.expectedReasoningEffort()) || blank(runtime.expectedSandbox())) {
            throw failure(code);
        }
    }

    private static void validateProfile(String id, String digest, Stage03FailureCode code) {
        if (blank(id) || !sha256(id + "\n").equals(digest)) {
            throw failure(code);
        }
    }

    private static Map<String, EvidenceCapsule> capsulesByFlow(List<EvidenceCapsule> capsules) {
        Map<String, EvidenceCapsule> indexed = new HashMap<>();
        for (EvidenceCapsule capsule : capsules) {
            if (indexed.put(capsule.flowSliceId(), capsule) != null) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }
        }
        return indexed;
    }

    private FlowInterpretationResult interpret(FlowSlice flow, EvidenceCapsule capsule, Stage02Result stage02,
                                                Stage01FlowView flowView,
                                                Map<String, String> stage01GapReasons, Stage03Request request,
                                                RegistryIndex registries,
                                                RoundSource rounds, FlowImprovementOverlay improvementOverlay) {
        CapsuleContext context = CapsuleContext.of(flow, capsule, stage02, flowView, stage01GapReasons);
        context.validateClosure();
        registries.requireUniqueFallbacks(context.anchorList());
        String taskSpecId = "task:" + sha256(stage02.stage02ResultId() + "\n" + flow.flowSliceId() + "\n"
                + capsule.evidenceCapsuleId() + "\n" + request.registryBundle().registryBundleId() + "\n"
                + request.interpretationProfileRef() + "\n" + request.knowledgeProfileRef() + "\n"
                + request.nineSectionProfileRef() + "\n" + request.modelRuntimePolicy() + "\n"
                + request.resourceBudget());
        String inputJson = taskInput(stage02, context, request, registries, taskSpecId, improvementOverlay);
        if (inputJson.getBytes(StandardCharsets.UTF_8).length > request.resourceBudget().maxTaskInputBytes()) {
            throw failure(Stage03FailureCode.RESOURCE_LIMIT_EXCEEDED);
        }
        String inputSha = sha256(inputJson);
        String session = "session:" + sha256(stage02.stage02ResultId() + "\n" + flow.flowSliceId() + "\n"
                + request.registryBundle().registryBundleId());
        ExpectedRuntimeIdentity expected = expectedRuntime(request.modelRuntimePolicy());
        FlowModelTask r1Task = task(taskSpecId, flow, capsule, session, 1, inputJson, inputSha,
                r1Schema(), expected);
        RoundExecution r1Round = rounds.execute(r1Task);
        ModelExecutionResult r1Response = r1Round.response();
        validateTransport(r1Response, r1Task, request.resourceBudget(), expected);
        List<R1Proposal> r1 = parseR1(r1Response.responseJson(), r1Task, context, registries);

        FlowModelTask r2Task = task(taskSpecId, flow, capsule, session, 2, inputJson, inputSha,
                r2Schema(), expected);
        RoundExecution r2Round = rounds.execute(r2Task);
        ModelExecutionResult r2Response = r2Round.response();
        validateTransport(r2Response, r2Task, request.resourceBudget(), expected);
        Map<String, R2Review> r2 = parseR2(r2Response.responseJson(), r2Task, r1, context);

        List<ProposalDisposition> dispositions = new ArrayList<>();
        List<AdmittedFlowMeaning> meanings = new ArrayList<>();
        List<InterpretationGap> gaps = new ArrayList<>();
        Set<String> representedAnchors = new HashSet<>();
        for (R1Proposal proposal : r1.stream().sorted(Comparator.comparing(R1Proposal::key)).toList()) {
            R2Review review = r2.get(proposal.key());
            Admission admission = admit(proposal, review, context, registries);
            dispositions.add(new ProposalDisposition(proposal.key(), admission.disposition(),
                    admission.claimKeys(), admission.atomIds(), admission.gapIds()));
            if (admission.meaning() != null) {
                meanings.add(admission.meaning());
                representedAnchors.add(admission.meaning().anchorKey());
            }
            if (admission.gap() != null) {
                gaps.add(admission.gap());
            }
        }
        List<TechnicalDisplayResolution> fallbacks = new ArrayList<>();
        for (Anchor anchor : context.anchorList()) {
            if (!representedAnchors.contains(anchor.key())) {
                TechnicalDisplayPolicy policy = registries.uniqueFallback(anchor);
                fallbacks.add(new TechnicalDisplayResolution(anchor.key(), anchor.kind(), policy.policyKey(),
                        technicalDisplay(anchor, policy, registries)));
                gaps.add(new InterpretationGap("interpretation-gap:" + sha256(anchor.key() + "\nNEEDS_TERM_REGISTRY"),
                        "NEEDS_TERM_REGISTRY", anchor.key()));
            }
        }
        meanings.sort(Comparator.comparing(AdmittedFlowMeaning::meaningId));
        dispositions.sort(Comparator.comparing(ProposalDisposition::proposalKey));
        gaps.sort(Comparator.comparing(InterpretationGap::interpretationGapId));
        fallbacks.sort(Comparator.comparing(TechnicalDisplayResolution::anchorKey));
        String r1Semantic = canonicalR1(taskSpecId, flow, capsule, r1);
        String r2Semantic = canonicalR2(taskSpecId, flow, capsule, r2);
        rounds.verifySemantic(r1Round, r1Semantic);
        rounds.verifySemantic(r2Round, r2Semantic);
        rounds.capture(r1Task, r1Round, r1Semantic);
        rounds.capture(r2Task, r2Round, r2Semantic);
        List<ModelRoundReceipt> receipts = List.of(receipt(1, r1Response, r1Semantic),
                receipt(2, r2Response, r2Semantic));
        String resultId = "flow-interpretation:" + sha256(taskSpecId + "\n" + meanings + "\n" + dispositions
                + "\n" + gaps + "\n" + fallbacks + "\n" + receipts);
        return new FlowInterpretationResult(INTERPRETATION_SCHEMA, resultId, taskSpecId, flow.flowSliceId(),
                capsule.evidenceCapsuleId(), meanings, dispositions, gaps, fallbacks, receipts);
    }

    private static FlowModelTask task(String taskSpecId, FlowSlice flow, EvidenceCapsule capsule, String session,
                                      int round, String inputJson, String inputSha, String schema,
                                      ExpectedRuntimeIdentity runtime) {
        return new FlowModelTask("flow-model-task-v1", taskSpecId, "FLOW_INTERPRETATION", flow.flowSliceId(),
                capsule.evidenceCapsuleId(), session, round, inputJson, inputSha, schema, sha256(schema), runtime);
    }

    private static ExpectedRuntimeIdentity expectedRuntime(ModelRuntimePolicy policy) {
        return new ExpectedRuntimeIdentity(policy.configuredAdapterId(), policy.configuredAuthMode(),
                policy.expectedUpstreamProvider(), policy.expectedModel(), policy.expectedReasoningEffort(),
                policy.expectedSandbox());
    }

    private static ModelExecutionResult execute(StructuredModelProvider provider, FlowModelTask task) {
        try {
            return provider.execute(task);
        } catch (Stage03Exception exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // A provider is an external boundary.  Its implementation exception is never part of
            // the public generation contract, and a failed round is intentionally not retried.
            throw failure(Stage03FailureCode.MODEL_RESPONSE_INVALID);
        }
    }

    private interface RoundSource {
        RoundExecution execute(FlowModelTask task);

        default void validateRoundSet(List<FlowSlice> flows, Map<String, EvidenceCapsule> capsules) {
            // Generation acquires one pair while executing each already-validated Flow.
        }

        default void verifySemantic(RoundExecution round, String canonicalSemanticJson) {
            // Provider-originated rounds acquire their transcript only after the strict parser succeeds.
        }

        default void capture(FlowModelTask task, RoundExecution round, String canonicalSemanticJson) {
            // Replay already owns its frozen canonical transcript.
        }

        default List<CanonicalFlowRound> canonicalRounds() {
            return List.of();
        }
    }

    private record RoundExecution(ModelExecutionResult response, CanonicalFlowRound transcript) {
    }

    private static final class ProviderRounds implements RoundSource {
        private final StructuredModelProvider provider;
        private final List<CanonicalFlowRound> captured = new ArrayList<>();

        private ProviderRounds(StructuredModelProvider provider) {
            this.provider = provider;
        }

        @Override
        public RoundExecution execute(FlowModelTask task) {
            return new RoundExecution(Stage03Generator.execute(provider, task), null);
        }

        @Override
        public void capture(FlowModelTask task, RoundExecution round, String canonicalSemanticJson) {
            ModelExecutionResult response = round.response();
            String canonical = canonicalResponseJson(response.responseJson(), Stage03FailureCode.MODEL_RESPONSE_INVALID,
                    false);
            captured.add(new CanonicalFlowRound("stage03-canonical-flow-round-v1", task, canonical,
                    sha256(canonical), sha256(canonicalSemanticJson), response.observedRuntime(),
                    response.startedReceiptId()));
        }

        @Override
        public List<CanonicalFlowRound> canonicalRounds() {
            return captured.stream().sorted(Comparator.comparing((CanonicalFlowRound value) -> value.task()
                            .flowSliceId()).thenComparing(value -> value.task().flowInterpretationRound()))
                    .toList();
        }
    }

    private static final class ReplayRounds implements RoundSource {
        private final List<CanonicalFlowRound> rounds;
        private final Map<String, CanonicalFlowRound> byRound = new HashMap<>();

        private ReplayRounds(List<CanonicalFlowRound> rounds) {
            this.rounds = List.copyOf(rounds);
            for (CanonicalFlowRound round : this.rounds) {
                validateReplayRoundInput(round);
            }
        }

        @Override
        public void validateRoundSet(List<FlowSlice> flows, Map<String, EvidenceCapsule> capsules) {
            List<String> expected = new ArrayList<>();
            for (FlowSlice flow : flows) {
                EvidenceCapsule capsule = capsules.get(flow.flowSliceId());
                if (capsule == null) {
                    throw failure(Stage03FailureCode.STAGE03_REPLAY_ROUND_SET_INVALID);
                }
                for (int round = 1; round <= 2; round++) {
                    expected.add(roundKey(flow.flowSliceId(), capsule.evidenceCapsuleId(), round));
                }
            }
            List<String> actual = new ArrayList<>();
            for (CanonicalFlowRound round : rounds) {
                FlowModelTask task = round.task();
                String key = roundKey(task.flowSliceId(), task.evidenceCapsuleId(), task.flowInterpretationRound());
                if (byRound.put(key, round) != null) {
                    throw failure(Stage03FailureCode.STAGE03_REPLAY_ROUND_SET_INVALID);
                }
                actual.add(key);
            }
            if (!actual.equals(expected) || !byRound.keySet().equals(Set.copyOf(expected))) {
                throw failure(Stage03FailureCode.STAGE03_REPLAY_ROUND_SET_INVALID);
            }
        }

        @Override
        public RoundExecution execute(FlowModelTask task) {
            CanonicalFlowRound round = byRound.get(roundKey(task.flowSliceId(), task.evidenceCapsuleId(),
                    task.flowInterpretationRound()));
            if (round == null) {
                throw failure(Stage03FailureCode.STAGE03_REPLAY_ROUND_SET_INVALID);
            }
            if (!task.equals(round.task())) {
                throw failure(Stage03FailureCode.STAGE03_REPLAY_TASK_MISMATCH);
            }
            if (!sha256(round.canonicalResponseJson()).equals(round.canonicalResponseSha256())) {
                throw failure(Stage03FailureCode.STAGE03_REPLAY_RESPONSE_DIGEST_MISMATCH);
            }
            return new RoundExecution(new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                    round.canonicalResponseJson(), round.observedRuntime(), round.startedReceiptId()), round);
        }

        @Override
        public void verifySemantic(RoundExecution execution, String canonicalSemanticJson) {
            if (!sha256(canonicalSemanticJson).equals(execution.transcript().semanticResponseSha256())) {
                throw failure(Stage03FailureCode.STAGE03_REPLAY_RESPONSE_DIGEST_MISMATCH);
            }
        }

        @Override
        public List<CanonicalFlowRound> canonicalRounds() {
            return rounds;
        }

        private static void validateReplayRoundInput(CanonicalFlowRound round) {
            if (round == null || !"stage03-canonical-flow-round-v1".equals(round.schemaVersion())
                    || round.task() == null || blank(round.canonicalResponseJson())
                    || !SHA256.matcher(round.canonicalResponseSha256()).matches()
                    || !SHA256.matcher(round.semanticResponseSha256()).matches()
                    || round.observedRuntime() == null || blank(round.startedReceiptId())) {
                throw failure(Stage03FailureCode.STAGE03_REPLAY_INPUT_INVALID);
            }
            canonicalResponseJson(round.canonicalResponseJson(), Stage03FailureCode.STAGE03_REPLAY_INPUT_INVALID,
                    true);
        }

        private static String roundKey(String flowSliceId, String capsuleId, int round) {
            if (blank(flowSliceId) || blank(capsuleId) || (round != 1 && round != 2)) {
                throw failure(Stage03FailureCode.STAGE03_REPLAY_ROUND_SET_INVALID);
            }
            return flowSliceId + "\n" + capsuleId + "\n" + round;
        }
    }

    /** Provider rounds for the affected Flow set plus exact replay for every other Flow. */
    private static final class ReusedProviderRounds implements RoundSource {
        private final StructuredModelProvider provider;
        private final Map<String, CanonicalFlowRound> reused = new HashMap<>();
        private final Set<String> overlayFlowIds;
        private final List<CanonicalFlowRound> captured = new ArrayList<>();

        private ReusedProviderRounds(StructuredModelProvider provider, List<CanonicalFlowRound> reusable,
                                    Set<String> overlayFlowIds) {
            this.provider = provider;
            this.overlayFlowIds = Set.copyOf(overlayFlowIds);
            if (reusable == null || reusable.isEmpty()) {
                throw failure(Stage03FailureCode.STAGE03_REQUEST_INVALID);
            }
            for (CanonicalFlowRound round : reusable) {
                ReplayRounds.validateReplayRoundInput(round);
                String key = ReplayRounds.roundKey(round.task().flowSliceId(), round.task().evidenceCapsuleId(),
                        round.task().flowInterpretationRound());
                if (reused.put(key, round) != null || this.overlayFlowIds.contains(round.task().flowSliceId())) {
                    throw failure(Stage03FailureCode.STAGE03_REQUEST_INVALID);
                }
            }
        }

        @Override
        public void validateRoundSet(List<FlowSlice> flows, Map<String, EvidenceCapsule> capsules) {
            Set<String> expected = new HashSet<>();
            for (FlowSlice flow : flows) {
                EvidenceCapsule capsule = capsules.get(flow.flowSliceId());
                if (capsule == null) {
                    throw failure(Stage03FailureCode.STAGE03_REPLAY_ROUND_SET_INVALID);
                }
                if (!overlayFlowIds.contains(flow.flowSliceId())) {
                    expected.add(ReplayRounds.roundKey(flow.flowSliceId(), capsule.evidenceCapsuleId(), 1));
                    expected.add(ReplayRounds.roundKey(flow.flowSliceId(), capsule.evidenceCapsuleId(), 2));
                }
            }
            if (!reused.keySet().equals(expected)) {
                throw failure(Stage03FailureCode.STAGE03_REPLAY_ROUND_SET_INVALID);
            }
        }

        @Override
        public RoundExecution execute(FlowModelTask task) {
            String key = ReplayRounds.roundKey(task.flowSliceId(), task.evidenceCapsuleId(),
                    task.flowInterpretationRound());
            CanonicalFlowRound round = reused.get(key);
            if (round == null) {
                if (!overlayFlowIds.contains(task.flowSliceId())) {
                    throw failure(Stage03FailureCode.STAGE03_REPLAY_ROUND_SET_INVALID);
                }
                return new RoundExecution(Stage03Generator.execute(provider, task), null);
            }
            if (!task.equals(round.task()) || !sha256(round.canonicalResponseJson()).equals(round.canonicalResponseSha256())) {
                throw failure(Stage03FailureCode.STAGE03_REPLAY_TASK_MISMATCH);
            }
            return new RoundExecution(new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                    round.canonicalResponseJson(), round.observedRuntime(), round.startedReceiptId()), round);
        }

        @Override
        public void verifySemantic(RoundExecution execution, String canonicalSemanticJson) {
            if (execution.transcript() != null
                    && !sha256(canonicalSemanticJson).equals(execution.transcript().semanticResponseSha256())) {
                throw failure(Stage03FailureCode.STAGE03_REPLAY_RESPONSE_DIGEST_MISMATCH);
            }
        }

        @Override
        public void capture(FlowModelTask task, RoundExecution execution, String canonicalSemanticJson) {
            if (execution.transcript() != null) {
                return;
            }
            ModelExecutionResult response = execution.response();
            String canonical = canonicalResponseJson(response.responseJson(), Stage03FailureCode.MODEL_RESPONSE_INVALID,
                    false);
            captured.add(new CanonicalFlowRound("stage03-canonical-flow-round-v1", task, canonical,
                    sha256(canonical), sha256(canonicalSemanticJson), response.observedRuntime(),
                    response.startedReceiptId()));
        }

        @Override
        public List<CanonicalFlowRound> canonicalRounds() {
            Map<String, CanonicalFlowRound> all = new HashMap<>(reused);
            for (CanonicalFlowRound round : captured) {
                String key = ReplayRounds.roundKey(round.task().flowSliceId(), round.task().evidenceCapsuleId(),
                        round.task().flowInterpretationRound());
                if (all.put(key, round) != null) {
                    throw failure(Stage03FailureCode.STAGE03_REPLAY_ROUND_SET_INVALID);
                }
            }
            return all.values().stream().sorted(Comparator.comparing((CanonicalFlowRound value) -> value.task()
                            .flowSliceId()).thenComparing(value -> value.task().flowInterpretationRound()))
                    .toList();
        }
    }

    private static void validateTransport(ModelExecutionResult result, FlowModelTask task,
                                          Stage03ResourceBudget budget, ExpectedRuntimeIdentity expected) {
        if (result == null || !task.taskSpecId().equals(result.taskSpecId())
                || task.flowInterpretationRound() != result.flowInterpretationRound()
                || blank(result.startedReceiptId()) || blank(result.responseJson())
                || result.responseJson().getBytes(StandardCharsets.UTF_8).length > budget.maxResponseBytes()) {
            throw failure(Stage03FailureCode.MODEL_RESPONSE_IDENTITY_MISMATCH);
        }
        ObservedRuntimeIdentity observed = result.observedRuntime();
        if (observed == null || !expected.expectedUpstreamProvider().equals(observed.upstreamProvider())
                || !expected.expectedModel().equals(observed.model())
                || !expected.expectedReasoningEffort().equals(observed.reasoningEffort())
                || !expected.expectedSandbox().equals(observed.sandbox())) {
            throw failure(Stage03FailureCode.MODEL_RUNTIME_IDENTITY_MISMATCH);
        }
    }

    private static ModelRoundReceipt receipt(int round, ModelExecutionResult response, String canonicalSemanticJson) {
        return new ModelRoundReceipt(round, sha256(canonicalSemanticJson), response.startedReceiptId(),
                response.observedRuntime());
    }

    private static String canonicalR1(String taskSpecId, FlowSlice flow, EvidenceCapsule capsule,
                                      List<R1Proposal> proposals) {
        ObjectNode root = responseIdentity("flow-interpretation-r1-v1", taskSpecId, flow, capsule);
        ArrayNode values = root.putArray("proposals");
        for (R1Proposal proposal : proposals.stream().sorted(Comparator.comparing(R1Proposal::key)).toList()) {
            ObjectNode value = values.addObject();
            value.put("proposalKey", proposal.key());
            value.put("proposalType", proposal.type());
            if (proposal.target() != null) {
                value.put("targetAnchorKey", proposal.target());
            }
            if (proposal.term() != null) {
                value.put("businessTermKey", proposal.term());
            }
            if (!proposal.claims().isEmpty()) {
                sortedStrings(value.putArray("claimKeys"), proposal.claims());
            }
            if (proposal.question() != null) {
                value.put("questionKey", proposal.question());
            }
            if (!proposal.atoms().isEmpty()) {
                sortedStrings(value.putArray("basisAtomIds"), proposal.atoms());
            }
            if (!proposal.gaps().isEmpty()) {
                sortedStrings(value.putArray("basisGapIds"), proposal.gaps());
            }
        }
        return canonicalJson(root);
    }

    private static String canonicalR2(String taskSpecId, FlowSlice flow, EvidenceCapsule capsule,
                                      Map<String, R2Review> reviews) {
        ObjectNode root = responseIdentity("flow-interpretation-r2-v1", taskSpecId, flow, capsule);
        ArrayNode values = root.putArray("reviews");
        for (R2Review review : reviews.values().stream().sorted(Comparator.comparing(R2Review::key)).toList()) {
            ObjectNode value = values.addObject();
            value.put("proposalKey", review.key());
            value.put("decision", review.decision());
            if (review.term() != null) {
                value.put("retainedBusinessTermKey", review.term());
            }
            if (!review.claims().isEmpty()) {
                sortedStrings(value.putArray("retainedClaimKeys"), review.claims());
            }
            if (!review.atoms().isEmpty()) {
                sortedStrings(value.putArray("retainedBasisAtomIds"), review.atoms());
            }
            if (!review.gaps().isEmpty()) {
                sortedStrings(value.putArray("retainedBasisGapIds"), review.gaps());
            }
        }
        return canonicalJson(root);
    }

    private static ObjectNode responseIdentity(String schemaVersion, String taskSpecId, FlowSlice flow,
                                               EvidenceCapsule capsule) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", schemaVersion);
        root.put("taskSpecId", taskSpecId);
        root.put("flowSliceId", flow.flowSliceId());
        root.put("evidenceCapsuleId", capsule.evidenceCapsuleId());
        return root;
    }

    private static String canonicalJson(ObjectNode node) {
        try {
            return JSON.writeValueAsString(canonical(node));
        } catch (JsonProcessingException impossible) {
            throw failure(Stage03FailureCode.MODEL_RESPONSE_INVALID);
        }
    }

    private static String canonicalResponseJson(String source, Stage03FailureCode code, boolean requireCanonical) {
        try {
            JsonNode parsed = JSON.readTree(source);
            if (parsed == null || !parsed.isObject()) {
                throw failure(code);
            }
            String canonical = JSON.writeValueAsString(canonical(parsed));
            if (requireCanonical && !canonical.equals(source)) {
                throw failure(code);
            }
            return canonical;
        } catch (JsonProcessingException invalid) {
            throw failure(code);
        }
    }

    private static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JSON.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> sorted.set(name, canonical(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode values = JSON.createArrayNode();
            for (JsonNode child : node) {
                values.add(canonical(child));
            }
            return values;
        }
        return node;
    }

    private static List<R1Proposal> parseR1(String response, FlowModelTask task, CapsuleContext context,
                                             RegistryIndex registries) {
        ObjectNode root = object(response, Stage03FailureCode.MODEL_RESPONSE_INVALID);
        requireFields(root, R1_ROOT, Stage03FailureCode.MODEL_RESPONSE_INVALID);
        requireIdentity(root, "flow-interpretation-r1-v1", task);
        ArrayNode proposals = array(root, "proposals", Stage03FailureCode.MODEL_RESPONSE_INVALID);
        Set<String> keys = new HashSet<>();
        List<R1Proposal> parsed = new ArrayList<>();
        for (JsonNode node : proposals) {
            if (!node.isObject()) {
                throw failure(Stage03FailureCode.MODEL_RESPONSE_INVALID);
            }
            ObjectNode proposal = (ObjectNode) node;
            String type = text(proposal, "proposalType", Stage03FailureCode.MODEL_RESPONSE_INVALID);
            Set<String> allowed = switch (type) {
                case "BUSINESS_TERM_SELECTION" -> R1_TERM;
                case "STRUCTURED_CLAIM_SET" -> R1_CLAIM;
                case "QUESTION_ONLY" -> R1_QUESTION;
                default -> throw failure(Stage03FailureCode.MODEL_RESPONSE_INVALID);
            };
            if (type.equals("STRUCTURED_CLAIM_SET") && !fieldNames(proposal).equals(R1_CLAIM)
                    && !fieldNames(proposal).equals(without(R1_CLAIM, "businessTermKey"))) {
                throw failure(Stage03FailureCode.MODEL_RESPONSE_INVALID);
            }
            if (!type.equals("STRUCTURED_CLAIM_SET")) {
                requireFields(proposal, allowed, Stage03FailureCode.MODEL_RESPONSE_INVALID);
            }
            String key = text(proposal, "proposalKey", Stage03FailureCode.MODEL_RESPONSE_INVALID);
            if (!key.matches("P[0-9]+") || !keys.add(key)) {
                throw failure(Stage03FailureCode.MODEL_RESPONSE_REFERENCE_INVALID);
            }
            String target = type.equals("QUESTION_ONLY") ? null
                    : text(proposal, "targetAnchorKey", Stage03FailureCode.MODEL_RESPONSE_INVALID);
            String term = proposal.has("businessTermKey")
                    ? text(proposal, "businessTermKey", Stage03FailureCode.MODEL_RESPONSE_INVALID) : null;
            List<String> claims = proposal.has("claimKeys")
                    ? textArray(proposal, "claimKeys", Stage03FailureCode.MODEL_RESPONSE_INVALID) : List.of();
            List<String> atoms = proposal.has("basisAtomIds")
                    ? textArray(proposal, "basisAtomIds", Stage03FailureCode.MODEL_RESPONSE_INVALID) : List.of();
            List<String> gaps = proposal.has("basisGapIds")
                    ? textArray(proposal, "basisGapIds", Stage03FailureCode.MODEL_RESPONSE_INVALID) : List.of();
            if (type.equals("QUESTION_ONLY") && (!atoms.isEmpty() || gaps.isEmpty())) {
                throw failure(Stage03FailureCode.MODEL_RESPONSE_REFERENCE_INVALID);
            }
            if (!type.equals("QUESTION_ONLY") && (atoms.isEmpty() || !gaps.isEmpty())) {
                throw failure(Stage03FailureCode.MODEL_RESPONSE_REFERENCE_INVALID);
            }
            if (target != null && context.anchor(target) == null) {
                throw failure(Stage03FailureCode.MODEL_RESPONSE_REFERENCE_INVALID);
            }
            String question = proposal.has("questionKey")
                    ? text(proposal, "questionKey", Stage03FailureCode.MODEL_RESPONSE_INVALID) : null;
            if (question != null) {
                gaps = resolveQuestionGaps(gaps, question, context, registries);
            }
            if (!context.atomIds().containsAll(atoms) || !context.gapIds().containsAll(gaps)
                    || !registries.knownTerms().containsAll(optional(term))
                    || !registries.knownClaims().containsAll(claims)
                    || !registries.knownQuestions().containsAll(optional(question))) {
                throw failure(Stage03FailureCode.MODEL_RESPONSE_REFERENCE_INVALID);
            }
            parsed.add(new R1Proposal(key, type, target, term, claims, question, atoms, gaps));
        }
        return parsed;
    }

    private static Map<String, R2Review> parseR2(String response, FlowModelTask task, List<R1Proposal> r1,
                                                   CapsuleContext context) {
        ObjectNode root = object(response, Stage03FailureCode.MODEL_RESPONSE_INVALID);
        requireFields(root, R2_ROOT, Stage03FailureCode.MODEL_RESPONSE_INVALID);
        requireIdentity(root, "flow-interpretation-r2-v1", task);
        if (r1.isEmpty()) {
            if (!array(root, "reviews", Stage03FailureCode.MODEL_RESPONSE_INVALID).isEmpty()) {
                throw failure(Stage03FailureCode.MODEL_REVIEW_NOT_CLOSED);
            }
            return Map.of();
        }
        Map<String, R1Proposal> proposals = r1.stream().collect(java.util.stream.Collectors.toMap(
                R1Proposal::key, proposal -> proposal));
        Map<String, R2Review> reviews = new HashMap<>();
        for (JsonNode node : array(root, "reviews", Stage03FailureCode.MODEL_RESPONSE_INVALID)) {
            if (!node.isObject()) {
                throw failure(Stage03FailureCode.MODEL_RESPONSE_INVALID);
            }
            ObjectNode review = (ObjectNode) node;
            requireAllowedFields(review, R2_REVIEW, Set.of("proposalKey", "decision"),
                    Stage03FailureCode.MODEL_RESPONSE_INVALID);
            String key = text(review, "proposalKey", Stage03FailureCode.MODEL_RESPONSE_INVALID);
            String decision = text(review, "decision", Stage03FailureCode.MODEL_RESPONSE_INVALID);
            if (!R2_DECISIONS.contains(decision) || !proposals.containsKey(key) || reviews.containsKey(key)) {
                throw failure(Stage03FailureCode.MODEL_REVIEW_NOT_CLOSED);
            }
            R1Proposal original = proposals.get(key);
            String term = review.has("retainedBusinessTermKey")
                    ? text(review, "retainedBusinessTermKey", Stage03FailureCode.MODEL_RESPONSE_INVALID) : null;
            List<String> claims = review.has("retainedClaimKeys")
                    ? textArray(review, "retainedClaimKeys", Stage03FailureCode.MODEL_RESPONSE_INVALID) : List.of();
            List<String> atoms = review.has("retainedBasisAtomIds")
                    ? textArray(review, "retainedBasisAtomIds", Stage03FailureCode.MODEL_RESPONSE_INVALID) : List.of();
            List<String> gaps = review.has("retainedBasisGapIds")
                    ? textArray(review, "retainedBasisGapIds", Stage03FailureCode.MODEL_RESPONSE_INVALID) : List.of();
            if ((term != null && !Objects.equals(term, original.term())) || !original.claims().containsAll(claims)
                    || !original.atoms().containsAll(atoms) || !original.gaps().containsAll(gaps)
                    || (!context.atomIds().containsAll(atoms)) || !context.gapIds().containsAll(gaps)) {
                throw failure(Stage03FailureCode.MODEL_REVIEW_NOT_CLOSED);
            }
            reviews.put(key, new R2Review(key, decision, term, claims, atoms, gaps));
        }
        if (!reviews.keySet().equals(proposals.keySet())) {
            throw failure(Stage03FailureCode.MODEL_REVIEW_NOT_CLOSED);
        }
        return reviews;
    }

    private static Admission admit(R1Proposal proposal, R2Review review, CapsuleContext context,
                                   RegistryIndex registries) {
        if ("QUESTION_ONLY".equals(proposal.type())) {
            InterpretationGap gap = new InterpretationGap("interpretation-gap:" + sha256(proposal.key() + "\n"
                    + proposal.gaps()), "NEEDS_EVIDENCE", proposal.gaps().get(0));
            return new Admission("NEEDS_EVIDENCE", List.of(), List.of(), proposal.gaps(), null, gap);
        }
        if ("DROP".equals(review.decision()) || "NEEDS_EVIDENCE".equals(review.decision())) {
            return new Admission(review.decision(), List.of(), List.of(), List.of(), null, null);
        }
        Anchor anchor = context.anchor(proposal.target());
        List<String> retainedAtoms = review.atoms().isEmpty() ? proposal.atoms() : review.atoms();
        List<String> requestedClaims = review.claims().isEmpty() ? proposal.claims() : review.claims();
        BusinessTermEntry term = proposal.term() == null ? null : registries.term(proposal.term());
        boolean termSupported = term == null || registries.termSupported(term, anchor, retainedAtoms, context);
        List<String> admittedClaims = requestedClaims.stream()
                .filter(claim -> registries.claimSupported(claim, anchor, retainedAtoms, context)).sorted().toList();
        boolean hasContent = term != null && termSupported || !admittedClaims.isEmpty();
        if (!hasContent) {
            return new Admission("DROP", List.of(), List.of(), List.of(), null, null);
        }
        String disposition = "NARROW".equals(review.decision()) || admittedClaims.size() != requestedClaims.size()
                ? "NARROW" : "KEEP";
        String localized = term == null ? null : term.localizedValue();
        String meaningId = "meaning:" + sha256(proposal.key() + "\n" + anchor.key() + "\n" + proposal.term()
                + "\n" + admittedClaims + "\n" + retainedAtoms);
        AdmittedFlowMeaning meaning = new AdmittedFlowMeaning(meaningId, anchor.key(), anchor.kind(), proposal.term(),
                localized, admittedClaims, retainedAtoms, List.of());
        return new Admission(disposition, admittedClaims, retainedAtoms, List.of(), meaning, null);
    }

    private static RepositoryBusinessModel assemble(Stage02Result stage02,
                                                    List<FlowInterpretationResult> interpretations,
                                                    Stage03Request request, RegistryIndex registries) {
        Map<String, FlowInterpretationResult> byFlow = interpretations.stream().collect(java.util.stream.Collectors
                .toMap(FlowInterpretationResult::flowSliceId, result -> result));
        Map<String, List<ObjectCandidate>> candidatesByObjectId = new TreeMap<>();
        List<BusinessActivity> activities = new ArrayList<>();
        List<BusinessFlow> flows = new ArrayList<>();
        List<BusinessOutcome> outcomes = new ArrayList<>();
        Map<String, ObjectRelation> relationsById = new TreeMap<>();
        List<MetricDefinition> metrics = new ArrayList<>();
        List<FieldDimension> fields = new ArrayList<>();
        List<PendingQuestion> pending = new ArrayList<>();
        Set<String> atomIds = new TreeSet<>();
        Set<String> meaningIds = new TreeSet<>();
        Set<String> gapIds = new TreeSet<>();
        List<FormulaDefinition> formulas = formulaDefinitions(stage02);
        for (EvidenceCapsule capsule : stage02.evidenceCapsules()) {
            for (AllowedFactView fact : capsule.allowedFacts()) {
                fact.atoms().forEach(atom -> atomIds.add(atom.atomId()));
            }
            for (AllowedGapView gap : capsule.allowedGaps()) {
                gapIds.add(gap.gapId());
                pending.add(pending(gap.gapId(), gap.code()));
            }
        }
        for (FlowGap gap : stage02.flowGaps()) {
            if (gapIds.add(gap.flowGapId())) {
                pending.add(pending(gap.flowGapId(), gap.code()));
            }
        }
        for (FlowInterpretationResult interpretation : interpretations) {
            interpretation.admittedMeanings().forEach(meaning -> meaningIds.add(meaning.meaningId()));
            interpretation.interpretationGaps().forEach(gap -> gapIds.add(gap.interpretationGapId()));
        }
        Map<String, EvidenceCapsule> capsules = capsulesByFlow(stage02.evidenceCapsules());
        for (FlowSlice flow : stage02.flowSlices().stream().sorted(Comparator.comparing(FlowSlice::flowSliceId)).toList()) {
            FlowInterpretationResult interpretation = byFlow.get(flow.flowSliceId());
            EvidenceCapsule capsule = capsules.get(flow.flowSliceId());
            if (interpretation == null || capsule == null) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }
            Map<String, AdmittedFlowMeaning> terms = meaningsByKind(interpretation.admittedMeanings());
            List<String> objectIds = new ArrayList<>();
            Map<String, ObjectCandidate> flowObjects = new TreeMap<>();
            for (String kind : List.of("REQUEST", "RECORD", "RESULT")) {
                AdmittedFlowMeaning meaning = terms.get(kind);
                String display = display(meaning, interpretation, kind);
                ObjectCandidate candidate = objectCandidate(kind, interpretation, capsule, display);
                candidatesByObjectId.computeIfAbsent(candidate.objectId(), ignored -> new ArrayList<>()).add(candidate);
                flowObjects.put(kind, candidate);
                objectIds.add(candidate.objectId());
            }
            addFlowRelation(relationsById, flow, flowObjects.get("REQUEST"), flowObjects.get("RECORD"),
                    "REQUEST_TO_RECORD");
            addFlowRelation(relationsById, flow, flowObjects.get("RECORD"), flowObjects.get("RESULT"),
                    "RECORD_TO_RESULT");
            AdmittedFlowMeaning activityMeaning = terms.get("ACTIVITY");
            String activityId = "activity:" + sha256(flow.flowSliceId() + "\n" + display(activityMeaning,
                    interpretation, "ACTIVITY"));
            activities.add(new BusinessActivity(activityId, anchorFor(interpretation, "ACTIVITY"),
                    display(activityMeaning, interpretation, "ACTIVITY"), flow.atomIds(), activityMeaning == null
                    ? List.of() : List.of(activityMeaning.meaningId())));
            List<String> outcomeIds = new ArrayList<>();
            for (OutcomePath outcome : flow.outcomePaths()) {
                String outcomeId = "outcome:" + sha256(outcome.outcomePathId());
                outcomes.add(new BusinessOutcome(outcomeId, outcome.outcomePathId(), "已证明的流程结束方式",
                        outcome.requiredAtomIds()));
                outcomeIds.add(outcomeId);
            }
            flows.add(new BusinessFlow("business-flow:" + sha256(flow.flowSliceId()), flow.flowSliceId(),
                    display(terms.get("FLOW"), interpretation, "FLOW"), outcomeIds, objectIds, List.of(activityId)));
        }
        for (FormulaDefinition formula : formulas) {
            metrics.add(new MetricDefinition("metric:formula:" + sha256(formula.atomId()), formula.formula(),
                    formula.basisAtomIds()));
            fields.add(new FieldDimension("field:formula:" + sha256(formula.atomId()), formula.role() + " 公式："
                    + formula.formula() + "；运算符：" + String.join("、", formula.operators()) + "；操作数："
                    + String.join("、", formula.operands()), formula.basisAtomIds()));
        }
        List<BusinessObject> objects = candidatesByObjectId.entrySet().stream().map(entry -> businessObject(
                entry.getKey(), entry.getValue())).sorted(Comparator.comparing(BusinessObject::objectId)).toList();
        List<ObjectRelation> relations = relationsById.values().stream().toList();
        activities.sort(Comparator.comparing(BusinessActivity::activityId));
        flows.sort(Comparator.comparing(BusinessFlow::flowId));
        outcomes.sort(Comparator.comparing(BusinessOutcome::outcomeId));
        fields.sort(Comparator.comparing(FieldDimension::fieldId));
        metrics.sort(Comparator.comparing(MetricDefinition::metricId));
        pending = distinctPending(pending);
        String modelId = "repository-business-model:" + sha256(stage02.stage02ResultId() + "\n" + objects + "\n"
                + activities + "\n" + flows + "\n" + outcomes + "\n" + fields + "\n" + relations + "\n" + metrics
                + "\n" + pending + "\n" + atomIds + "\n" + meaningIds + "\n" + gapIds + "\n"
                + request.knowledgeProfileRef());
        return new RepositoryBusinessModel("repository-business-model-v1", modelId, stage02.stage02ResultId(), objects,
                activities, flows, outcomes, fields, relations, metrics, exampleQuestions(metrics, outcomes), pending,
                List.of(), new KnowledgeAccounting(List.copyOf(atomIds), List.copyOf(meaningIds), List.copyOf(gapIds)));
    }

    private static ObjectCandidate objectCandidate(String kind, FlowInterpretationResult interpretation,
                                                    EvidenceCapsule capsule, String display) {
        String anchorKey = anchorFor(interpretation, kind);
        String expectedPrefix = "anchor:" + kind.toLowerCase(java.util.Locale.ROOT) + ":";
        if (!anchorKey.startsWith(expectedPrefix) || "anchor:unknown".equals(anchorKey)) {
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }
        List<String> basisAtomIds = switch (kind) {
            case "REQUEST" -> namedAtoms(capsule, "HTTP_ENTRY", "REQUEST_BODY").stream()
                    .map(AllowedAtomView::atomId).toList();
            case "RECORD" -> recordBasis(capsule);
            case "RESULT" -> atomsForFact(capsule, "SUCCESS_RESULT").stream().map(AllowedAtomView::atomId).toList();
            default -> throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        };
        if (basisAtomIds.isEmpty() || !safeReaderText(display)) {
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }
        String objectId = "object:" + sha256("hard-anchor-object-v1\n" + kind + "\n" + anchorKey);
        return new ObjectCandidate(objectId, kind, anchorKey, display, basisAtomIds);
    }

    private static BusinessObject businessObject(String objectId, List<ObjectCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }
        Set<String> kinds = candidates.stream().map(ObjectCandidate::kind).collect(java.util.stream.Collectors
                .toCollection(TreeSet::new));
        Set<String> anchors = candidates.stream().map(ObjectCandidate::anchorKey).collect(java.util.stream.Collectors
                .toCollection(TreeSet::new));
        if (kinds.size() != 1 || anchors.size() != 1) {
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }
        List<String> displays = candidates.stream().map(ObjectCandidate::display).distinct().sorted().toList();
        if (displays.size() != 1) {
            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
        }
        List<String> basis = candidates.stream().flatMap(candidate -> candidate.basisAtomIds().stream()).distinct()
                .sorted().toList();
        return new BusinessObject(objectId, anchors.iterator().next(), displays.get(0), kinds.iterator().next(), basis);
    }

    private static List<String> recordBasis(EvidenceCapsule capsule) {
        List<AllowedAtomView> tables = capsule.allowedFacts().stream().flatMap(fact -> fact.atoms().stream())
                .filter(atom -> "TABLE".equals(atom.name())).sorted(Comparator.comparing(AllowedAtomView::atomId)).toList();
        if (!tables.isEmpty()) {
            return tables.stream().map(AllowedAtomView::atomId).toList();
        }
        return atomsForFact(capsule, "INVENTORY_LOAD").stream().map(AllowedAtomView::atomId).toList();
    }

    private static List<AllowedAtomView> namedAtoms(EvidenceCapsule capsule, String factKind, String atomName) {
        List<AllowedAtomView> result = atomsForFact(capsule, factKind).stream()
                .filter(atom -> atomName.equals(atom.name())).toList();
        if (result.size() != 1) {
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }
        return result;
    }

    private static List<AllowedAtomView> atomsForFact(EvidenceCapsule capsule, String factKind) {
        List<AllowedFactView> facts = capsule.allowedFacts().stream().filter(fact -> factKind.equals(fact.kind()))
                .sorted(Comparator.comparing(AllowedFactView::factId)).toList();
        if (facts.size() != 1) {
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }
        return facts.get(0).atoms().stream().sorted(Comparator.comparing(AllowedAtomView::atomId)).toList();
    }

    private static void addFlowRelation(Map<String, ObjectRelation> relationsById, FlowSlice flow,
                                        ObjectCandidate source, ObjectCandidate target, String direction) {
        if (source == null || target == null || source.objectId().equals(target.objectId())) {
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }
        List<String> basis = new ArrayList<>(source.basisAtomIds());
        basis.addAll(target.basisAtomIds());
        basis = basis.stream().distinct().sorted().toList();
        if (basis.isEmpty() || !new HashSet<>(flow.atomIds()).containsAll(basis)) {
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }
        String relationId = "relation:" + sha256("proven-flow-object-link-v1\n" + direction + "\n"
                + flow.flowSliceId() + "\n" + source.objectId() + "\n" + target.objectId() + "\n" + basis);
        ObjectRelation relation = new ObjectRelation(relationId, source.objectId(), target.objectId(), basis);
        if (relationsById.putIfAbsent(relationId, relation) != null) {
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }
    }

    private static List<FormulaDefinition> formulaDefinitions(Stage02Result stage02) {
        Map<String, AllowedFactView> facts = new TreeMap<>();
        for (EvidenceCapsule capsule : stage02.evidenceCapsules()) {
            for (AllowedFactView fact : capsule.allowedFacts()) {
                if ("AVAILABLE_FORMULA".equals(fact.kind()) && facts.put(fact.factId(), fact) != null) {
                    throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
                }
            }
        }
        List<FormulaDefinition> formulas = new ArrayList<>();
        for (AllowedFactView fact : facts.values()) {
            List<AllowedAtomView> atoms = fact.atoms().stream().sorted(Comparator.comparing(AllowedAtomView::atomId))
                    .toList();
            if (atoms.size() != 1) {
                continue;
            }
            AllowedAtomView atom = atoms.get(0);
            if (atom.value() == null || blank(atom.value().canonical()) || blank(atom.role())) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            String formula = atom.value().canonical().replaceAll("\\s+", " ").trim();
            List<String> tokens = List.of(formula.split(" "));
            List<String> operators = tokens.stream().filter(token -> token.matches("[+\\-*/=<>]+"))
                    .distinct().sorted().toList();
            List<String> operands = tokens.stream().filter(token -> token.matches("[A-Za-z_][A-Za-z0-9_]*"))
                    .distinct().sorted().toList();
            if (!operators.isEmpty() && operands.size() >= 2) {
                formulas.add(new FormulaDefinition(atom.atomId(), atom.role(), formula, operators, operands,
                        List.of(atom.atomId())));
            }
        }
        return formulas.stream().sorted(Comparator.comparing(FormulaDefinition::atomId)).toList();
    }

    private static List<PendingQuestion> distinctPending(List<PendingQuestion> pending) {
        return pending.stream().collect(java.util.stream.Collectors.toMap(PendingQuestion::pendingQuestionId,
                question -> question, (first, ignored) -> first, TreeMap::new)).values().stream().toList();
    }

    private static PendingQuestion pending(String gapId, String code) {
        return new PendingQuestion("pending:" + sha256(gapId),
                "在本次静态代码搜索范围内，尚未找到相关处理的证据，需要确认对应业务政策。", code,
                List.of(gapId));
    }

    private static List<AnswerableQuestion> exampleQuestions(List<MetricDefinition> metrics,
                                                               List<BusinessOutcome> outcomes) {
        List<AnswerableQuestion> questions = new ArrayList<>();
        if (!metrics.isEmpty()) {
            questions.add(new AnswerableQuestion("question:available", "可用量 available 如何计算？",
                    List.of(metrics.get(0).metricId())));
        }
        if (!outcomes.isEmpty()) {
            questions.add(new AnswerableQuestion("question:outcome", "流程在条件不满足时如何结束？",
                    List.of(outcomes.get(0).outcomeId())));
        }
        return List.copyOf(questions);
    }

    private static Map<String, AdmittedFlowMeaning> meaningsByKind(List<AdmittedFlowMeaning> meanings) {
        Map<String, AdmittedFlowMeaning> byKind = new HashMap<>();
        for (AdmittedFlowMeaning meaning : meanings) {
            if (meaning.businessTermKey() != null) {
                byKind.merge(meaning.anchorKind(), meaning, (first, second) -> first.meaningId()
                        .compareTo(second.meaningId()) <= 0 ? first : second);
            }
        }
        return byKind;
    }

    private static String display(AdmittedFlowMeaning meaning, FlowInterpretationResult interpretation, String kind) {
        if (meaning != null && !blank(meaning.localizedValue())) {
            return meaning.localizedValue();
        }
        return interpretation.technicalFallbacks().stream().filter(fallback -> kind.equals(fallback.anchorKind()))
                .map(TechnicalDisplayResolution::resolvedDisplay).sorted().findFirst().orElse("已证明的技术结构");
    }

    private static String anchorFor(FlowInterpretationResult interpretation, String kind) {
        List<String> admitted = interpretation.admittedMeanings().stream()
                .filter(meaning -> kind.equals(meaning.anchorKind())).map(AdmittedFlowMeaning::anchorKey)
                .distinct().sorted().toList();
        if (!admitted.isEmpty()) {
            if (admitted.size() != 1) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }
            return admitted.get(0);
        }
        List<String> fallbacks = interpretation.technicalFallbacks().stream()
                .filter(fallback -> kind.equals(fallback.anchorKind())).map(TechnicalDisplayResolution::anchorKey)
                .distinct().sorted().toList();
        if (fallbacks.size() != 1) {
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }
        return fallbacks.get(0);
    }

    private static NineSectionPlan plan(RepositoryBusinessModel model, Stage02Result stage02,
                                        List<FlowInterpretationResult> interpretations,
                                        RegistryIndex registries, Stage03Request request) {
        ReaderContracts readerContracts = registries.readerContracts();
        Map<String, AllowedAtomView> atomViews = atomViews(stage02);
        Map<String, AdmittedFlowMeaning> meaningViews = interpretations.stream()
                .flatMap(value -> value.admittedMeanings().stream()).collect(java.util.stream.Collectors.toMap(
                        AdmittedFlowMeaning::meaningId, value -> value, (left, right) -> left, TreeMap::new));
        Map<String, PendingQuestion> pendingByGap = model.pendingQuestions().stream().flatMap(question -> question
                .sourceGapIds().stream().map(gapId -> Map.entry(gapId, question))).collect(java.util.stream.Collectors
                .toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, TreeMap::new));
        Map<String, List<ReaderItem>> itemsBySection = new TreeMap<>();
        for (int index = 0; index < HEADINGS.size(); index++) {
            itemsBySection.put(sectionKey(index), new ArrayList<>());
        }
        Map<String, String> atomOwners = new TreeMap<>();
        ReaderSentenceTemplate atomTemplate = readerContracts.itemTemplate("PROVEN_VALUE", "READER_ATOM_V1");
        for (String atomId : model.accounting().ownedAtomIds().stream().sorted().toList()) {
            AllowedAtomView atom = atomViews.get(atomId);
            if (atom == null || atom.value() == null || blank(atom.value().canonical())) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            String sectionKey = atomTemplate.ownerSectionKey();
            String itemKey = "reader:atom:" + sha256(atomId);
            itemsBySection.get(sectionKey).add(new ReaderItem(itemKey, sectionKey, "PROVEN_VALUE",
                    atomTemplate.templateKey(),
                    List.of(new ProvenValueSlot("proven-value", atomId, readerValue(atom.value().canonical()))),
                    List.of(), List.of(atomId), List.of(), List.of()));
            if (atomOwners.put(atomId, itemKey) != null) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
        }
        Map<String, String> meaningOwners = new TreeMap<>();
        ReaderSentenceTemplate termTemplate = readerContracts.itemTemplate("BUSINESS_TERM", "READER_TERM_V1");
        for (String meaningId : model.accounting().ownedMeaningIds().stream().sorted().toList()) {
            AdmittedFlowMeaning meaning = meaningViews.get(meaningId);
            if (meaning == null || blank(meaning.localizedValue())) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            String sectionKey = termTemplate.ownerSectionKey();
            String itemKey = "reader:meaning:" + sha256(meaningId);
            itemsBySection.get(sectionKey).add(new ReaderItem(itemKey, sectionKey, "BUSINESS_TERM",
                    termTemplate.templateKey(),
                    List.of(new BusinessTermSlot("business-term", meaningId, readerValue(meaning.localizedValue()))),
                    List.of(), List.of(), List.of(meaningId), List.of()));
            if (meaningOwners.put(meaningId, itemKey) != null) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
        }
        Map<String, String> gapOwners = new TreeMap<>();
        ReaderSentenceTemplate gapTemplate = readerContracts.itemTemplate("BOUNDED_QUESTION", "READER_GAP_V1");
        for (String gapId : model.accounting().ownedGapIds().stream().sorted().toList()) {
            PendingQuestion pending = pendingByGap.get(gapId);
            String text = pending == null ? "当前冻结静态证据未覆盖该待确认事项。" : pending.text();
            String sectionKey = gapTemplate.ownerSectionKey();
            String itemKey = "reader:gap:" + sha256(gapId);
            itemsBySection.get(sectionKey).add(new ReaderItem(itemKey, sectionKey, "BOUNDED_QUESTION",
                    gapTemplate.templateKey(),
                    List.of(new BoundedQuestionSlot("bounded-question", gapId, readerValue(text))), List.of(),
                    List.of(), List.of(), List.of(gapId)));
            if (gapOwners.put(gapId, itemKey) != null) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
        }
        Set<String> technicalItemKeys = new HashSet<>();
        for (FlowInterpretationResult interpretation : interpretations.stream()
                .sorted(Comparator.comparing(FlowInterpretationResult::flowSliceId)).toList()) {
            for (TechnicalDisplayResolution fallback : interpretation.technicalFallbacks().stream()
                    .sorted(Comparator.comparing(TechnicalDisplayResolution::anchorKey)).toList()) {
                ReaderSentenceTemplate template = readerContracts.technicalDisplayTemplate(
                        registries.technicalPolicy(fallback.policyKey()));
                String itemKey = "reader:technical:" + sha256(interpretation.flowSliceId() + "\n"
                        + fallback.anchorKey() + "\n" + fallback.policyKey());
                if (!technicalItemKeys.add(itemKey)) {
                    throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
                }
                String sectionKey = template.ownerSectionKey();
                itemsBySection.get(sectionKey).add(new ReaderItem(itemKey, sectionKey, "TECHNICAL_DISPLAY",
                        template.templateKey(), List.of(new TechnicalDisplaySlot("technical-display",
                        fallback.anchorKey(), readerValue(fallback.resolvedDisplay()))), List.of(), List.of(),
                        List.of(), List.of()));
            }
        }
        Map<String, BusinessOutcome> outcomesByPath = model.outcomes().stream().collect(java.util.stream.Collectors
                .toMap(BusinessOutcome::outcomePathId, outcome -> outcome, (left, right) -> {
                    throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
                }, TreeMap::new));
        List<OutcomePath> paths = stage02.flowSlices().stream().sorted(Comparator.comparing(FlowSlice::flowSliceId))
                .flatMap(flow -> flow.outcomePaths().stream().sorted(Comparator.comparing(OutcomePath::outcomePathId)))
                .toList();
        if (outcomesByPath.size() != paths.size() || !outcomesByPath.keySet().equals(paths.stream()
                .map(OutcomePath::outcomePathId).collect(java.util.stream.Collectors.toCollection(TreeSet::new)))) {
            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
        }
        ReaderSentenceTemplate outcomeTemplate = readerContracts.itemTemplate("OUTCOME", "READER_OUTCOME_V1");
        for (OutcomePath path : paths) {
            BusinessOutcome outcome = outcomesByPath.get(path.outcomePathId());
            if (outcome == null || !path.requiredAtomIds().equals(outcome.basisAtomIds())) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            List<String> references = new ArrayList<>();
            references.add(outcome.outcomeId());
            for (String atomId : path.requiredAtomIds()) {
                String owner = atomOwners.get(atomId);
                if (owner == null) {
                    throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
                }
                references.add(owner);
            }
            references = references.stream().distinct().sorted().toList();
            String sectionKey = outcomeTemplate.ownerSectionKey();
            itemsBySection.get(sectionKey).add(new ReaderItem("reader:outcome:" + sha256(path.outcomePathId()),
                    sectionKey, "OUTCOME_PATH", outcomeTemplate.templateKey(), List.of(
                    new TechnicalDisplaySlot("outcome-terminal", outcome.outcomeId(), readerValue(path.terminalKind())),
                    new TechnicalDisplaySlot("outcome-semantics", outcome.outcomeId(),
                            readerValue(outcomeSemantics(path)))), references, List.of(), List.of(), List.of()));
        }
        Map<String, FormulaDefinition> formulasByBasis = formulaDefinitions(stage02).stream().collect(
                java.util.stream.Collectors.toMap(formula -> formulaBasisKey(formula.basisAtomIds()), formula -> formula,
                        (left, right) -> {
                            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
                        }, TreeMap::new));
        Map<String, FieldDimension> fieldsByBasis = model.fieldsAndDimensions().stream().collect(
                java.util.stream.Collectors.toMap(field -> formulaBasisKey(field.basisAtomIds()), field -> field,
                        (left, right) -> {
                            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
                        }, TreeMap::new));
        Map<String, MetricDefinition> metricsByBasis = model.metrics().stream().collect(java.util.stream.Collectors
                .toMap(metric -> formulaBasisKey(metric.basisAtomIds()), metric -> metric, (left, right) -> {
                    throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
                }, TreeMap::new));
        if (!formulasByBasis.keySet().equals(fieldsByBasis.keySet())
                || !formulasByBasis.keySet().equals(metricsByBasis.keySet())) {
            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
        }
        ReaderSentenceTemplate fieldTemplate = readerContracts.itemTemplate("FIELD", "READER_FIELD_FORMULA_V1");
        ReaderSentenceTemplate metricTemplate = readerContracts.itemTemplate("METRIC", "READER_METRIC_FORMULA_V1");
        for (FormulaDefinition formula : formulasByBasis.values()) {
            String basisKey = formulaBasisKey(formula.basisAtomIds());
            FieldDimension field = fieldsByBasis.get(basisKey);
            MetricDefinition metric = metricsByBasis.get(basisKey);
            String expectedFieldDisplay = formula.role() + " 公式：" + formula.formula() + "；运算符："
                    + String.join("、", formula.operators()) + "；操作数：" + String.join("、", formula.operands());
            if (field == null || metric == null || !formula.basisAtomIds().equals(field.basisAtomIds())
                    || !formula.basisAtomIds().equals(metric.basisAtomIds())
                    || !expectedFieldDisplay.equals(field.display()) || !formula.formula().equals(metric.display())) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            String atomOwner = atomOwners.get(formula.atomId());
            if (atomOwner == null) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            String fieldItemKey = "reader:field:" + sha256(field.fieldId());
            String fieldSection = fieldTemplate.ownerSectionKey();
            itemsBySection.get(fieldSection).add(new ReaderItem(fieldItemKey, fieldSection, "FIELD_FORMULA",
                    fieldTemplate.templateKey(), List.of(
                    new TechnicalDisplaySlot("formula-role", field.fieldId(), readerValue(formula.role())),
                    new TechnicalDisplaySlot("formula-value", field.fieldId(), readerValue(formula.formula())),
                    new TechnicalDisplaySlot("formula-operators", field.fieldId(),
                            readerValue(String.join("、", formula.operators()))),
                    new TechnicalDisplaySlot("formula-operands", field.fieldId(),
                            readerValue(String.join("、", formula.operands())))),
                    List.of(atomOwner, field.fieldId()).stream().sorted().toList(), List.of(), List.of(), List.of()));
            String metricSection = metricTemplate.ownerSectionKey();
            itemsBySection.get(metricSection).add(new ReaderItem("reader:metric:" + sha256(metric.metricId()),
                    metricSection, "METRIC_FORMULA", metricTemplate.templateKey(), List.of(
                    new TechnicalDisplaySlot("formula-role", metric.metricId(), readerValue(formula.role())),
                    new TechnicalDisplaySlot("formula-value", metric.metricId(), readerValue(formula.formula())),
                    new TechnicalDisplaySlot("formula-operators", metric.metricId(),
                            readerValue(String.join("、", formula.operators()))),
                    new TechnicalDisplaySlot("formula-operands", metric.metricId(),
                            readerValue(String.join("、", formula.operands())))),
                    List.of(atomOwner, fieldItemKey, metric.metricId()).stream().sorted().toList(), List.of(), List.of(),
                    List.of()));
        }
        Map<String, BusinessObject> objectsById = model.objects().stream().collect(java.util.stream.Collectors.toMap(
                BusinessObject::objectId, object -> object, (left, right) -> {
                    throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
                }, TreeMap::new));
        ReaderSentenceTemplate objectTemplate = readerContracts.itemTemplate("BUSINESS_OBJECT",
                "READER_BUSINESS_OBJECT_V1");
        for (BusinessObject object : objectsById.values()) {
            if (blank(object.anchorKey()) || !safeReaderText(object.display()) || object.basisAtomIds().isEmpty()) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            String sectionKey = objectTemplate.ownerSectionKey();
            itemsBySection.get(sectionKey).add(new ReaderItem("reader:object:" + sha256(object.objectId()), sectionKey,
                    "BUSINESS_OBJECT", objectTemplate.templateKey(), List.of(new TechnicalDisplaySlot("object-display",
                    object.anchorKey(), readerValue(object.display()))), m6References(object.objectId(),
                    object.basisAtomIds(), atomOwners), List.of(), List.of(), List.of()));
        }
        ReaderSentenceTemplate activityTemplate = readerContracts.itemTemplate("BUSINESS_ACTIVITY",
                "READER_BUSINESS_ACTIVITY_V1");
        for (BusinessActivity activity : model.activities().stream()
                .sorted(Comparator.comparing(BusinessActivity::activityId)).toList()) {
            if (blank(activity.anchorKey()) || !safeReaderText(activity.display()) || activity.basisAtomIds().isEmpty()) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            String sectionKey = activityTemplate.ownerSectionKey();
            itemsBySection.get(sectionKey).add(new ReaderItem("reader:activity:" + sha256(activity.activityId()),
                    sectionKey, "BUSINESS_ACTIVITY", activityTemplate.templateKey(), List.of(
                    new TechnicalDisplaySlot("activity-display", activity.anchorKey(), readerValue(activity.display()))),
                    m6References(activity.activityId(), activity.basisAtomIds(), atomOwners), List.of(), List.of(),
                    List.of()));
        }
        ReaderSentenceTemplate relationTemplate = readerContracts.itemTemplate("OBJECT_RELATION",
                "READER_OBJECT_RELATION_V1");
        for (ObjectRelation relation : model.relations().stream()
                .sorted(Comparator.comparing(ObjectRelation::relationId)).toList()) {
            BusinessObject source = objectsById.get(relation.sourceObjectId());
            BusinessObject target = objectsById.get(relation.targetObjectId());
            if (source == null || target == null || !safeReaderText(source.display()) || !safeReaderText(target.display())
                    || relation.basisAtomIds().isEmpty()) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            String sectionKey = relationTemplate.ownerSectionKey();
            itemsBySection.get(sectionKey).add(new ReaderItem("reader:relation:" + sha256(relation.relationId()),
                    sectionKey, "OBJECT_RELATION", relationTemplate.templateKey(), List.of(
                    new TechnicalDisplaySlot("source-display", relation.relationId(), readerValue(source.display())),
                    new TechnicalDisplaySlot("target-display", relation.relationId(), readerValue(target.display()))),
                    m6References(relation.relationId(), relation.basisAtomIds(), atomOwners), List.of(), List.of(),
                    List.of()));
        }
        Set<String> answerSourceIds = new HashSet<>();
        model.metrics().forEach(metric -> answerSourceIds.add(metric.metricId()));
        model.outcomes().forEach(outcome -> answerSourceIds.add(outcome.outcomeId()));
        ReaderSentenceTemplate questionTemplate = readerContracts.itemTemplate("ANSWERABLE_QUESTION",
                "READER_ANSWERABLE_QUESTION_V1");
        for (AnswerableQuestion question : model.exampleQuestions().stream()
                .sorted(Comparator.comparing(AnswerableQuestion::questionId)).toList()) {
            if (blank(question.questionId()) || !safeReaderText(question.text()) || question.answerItemIds().isEmpty()
                    || !answerSourceIds.containsAll(question.answerItemIds())) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            List<String> references = new ArrayList<>(question.answerItemIds());
            references.add(question.questionId());
            String sectionKey = questionTemplate.ownerSectionKey();
            itemsBySection.get(sectionKey).add(new ReaderItem("reader:question:" + sha256(question.questionId()),
                    sectionKey, "ANSWERABLE_QUESTION", questionTemplate.templateKey(), List.of(
                    new BoundedQuestionSlot("answerable-question", question.questionId(), readerValue(question.text()))),
                    references.stream().distinct().sorted().toList(), List.of(), List.of(), List.of()));
        }
        for (int index = 0; index < HEADINGS.size(); index++) {
            String sectionKey = sectionKey(index);
            if (itemsBySection.get(sectionKey).isEmpty()) {
                ReaderSentenceTemplate empty = readerContracts.emptySectionTemplate(sectionKey);
                itemsBySection.get(sectionKey).add(new ReaderItem("reader:" + sectionKey + ":empty", sectionKey,
                        "EMPTY_SECTION", empty.templateKey(), List.of(new TechnicalDisplaySlot("technical-display",
                        sectionKey, readerValue("当前冻结静态证据范围内无额外内容。"))), List.of(), List.of(),
                        List.of(), List.of()));
            }
        }
        if (itemsBySection.values().stream().mapToInt(List::size).sum() > request.resourceBudget().maxReaderItems()) {
            throw failure(Stage03FailureCode.RESOURCE_LIMIT_EXCEEDED);
        }
        List<AtomDisposition> atoms = model.accounting().ownedAtomIds().stream().sorted()
                .map(atom -> new AtomDisposition(atom, "READER_BODY", atomOwners.get(atom))).toList();
        List<MeaningDisposition> meanings = model.accounting().ownedMeaningIds().stream().sorted()
                .map(meaning -> new MeaningDisposition(meaning, "READER_BODY", meaningOwners.get(meaning))).toList();
        List<GapDisposition> gaps = model.accounting().ownedGapIds().stream().sorted()
                .map(gap -> new GapDisposition(gap, "READER_BODY", gapOwners.get(gap))).toList();
        if (atomOwners.size() != atoms.size() || meaningOwners.size() != meanings.size() || gapOwners.size() != gaps.size()) {
            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
        }
        List<ReaderSection> sections = new ArrayList<>();
        for (int index = 0; index < HEADINGS.size(); index++) {
            sections.add(new ReaderSection(sectionKey(index), HEADINGS.get(index),
                    itemsBySection.get(sectionKey(index))));
        }
        String planId = "nine-section-plan:" + sha256(model.repositoryBusinessModelId() + "\n" + HEADINGS + "\n"
                + atoms + "\n" + meanings + "\n" + gaps + "\n" + request.nineSectionProfileRef());
        return new NineSectionPlan(PLAN_SCHEMA, planId, model.repositoryBusinessModelId(), sections, atoms, meanings,
                gaps, new ReaderCoverage(sections.size(), atoms.size(), meanings.size(), gaps.size()));
    }

    private static String outcomeSemantics(OutcomePath path) {
        if (path.decisions().isEmpty()) {
            return "无分支条件";
        }
        return path.decisions().stream().map(decision -> decision.polarity() + " expression="
                + decision.normalizedCondition()).collect(java.util.stream.Collectors.joining("；"));
    }

    private static String formulaBasisKey(List<String> basisAtomIds) {
        return basisAtomIds.stream().sorted().collect(java.util.stream.Collectors.joining("\n"));
    }

    private static List<String> m6References(String publicId, List<String> basisAtomIds,
                                             Map<String, String> atomOwners) {
        if (blank(publicId) || basisAtomIds == null || basisAtomIds.isEmpty()) {
            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
        }
        List<String> references = new ArrayList<>(List.of(publicId));
        for (String atomId : basisAtomIds.stream().sorted().toList()) {
            String owner = atomOwners.get(atomId);
            if (owner == null) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            references.add(owner);
        }
        return references.stream().distinct().sorted().toList();
    }

    private static Map<String, AllowedAtomView> atomViews(Stage02Result stage02) {
        Map<String, AllowedAtomView> values = new TreeMap<>();
        for (EvidenceCapsule capsule : stage02.evidenceCapsules()) {
            for (AllowedFactView fact : capsule.allowedFacts()) {
                for (AllowedAtomView atom : fact.atoms()) {
                    if (values.put(atom.atomId(), atom) != null) {
                        throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
                    }
                }
            }
        }
        return Map.copyOf(values);
    }

    private static String sectionKey(int index) {
        return "section-" + (index + 1);
    }

    private static String readerValue(String value) {
        if (!safeReaderText(value) || value.contains("{{") || value.contains("}}")) {
            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
        }
        return value;
    }

    private static boolean safeRegistryLiteral(String value) {
        return !blank(value) && !value.contains("\u0000") && !SHA256.matcher(value).find()
                && !containsForbiddenReaderContent(value);
    }

    private static boolean safeReaderText(String value) {
        return !blank(value) && !value.contains("\u0000") && !SHA256.matcher(value).find()
                && !containsForbiddenReaderContent(value);
    }

    private static boolean containsForbiddenReaderContent(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("prompt") || lower.contains("provider") || lower.contains("runtime")
                || lower.contains("model") || lower.contains("gpt-") || lower.contains("stage02-result:")
                || lower.contains("flow-slice:") || lower.contains("evidence-capsule:")
                || lower.contains("../") || lower.matches(".*\\.(java|xml|yml|yaml)\\b.*")
                || containsFilesystemPath(value);
    }

    private static boolean containsFilesystemPath(String value) {
        return UNIX_ABSOLUTE_PATH.matcher(value).find()
                || WINDOWS_DRIVE_ABSOLUTE_PATH.matcher(value).find()
                || WINDOWS_UNC_PATH.matcher(value).find()
                || RELATIVE_MULTI_SEGMENT_PATH.matcher(value).find();
    }

    private static RenderedNineSectionDocument render(NineSectionPlan plan, ReaderContracts readerContracts,
                                                       Stage03Request request) {
        if (plan.sections().size() != HEADINGS.size() || !plan.sections().stream().map(ReaderSection::heading).toList()
                .equals(HEADINGS)) {
            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
        }
        StringBuilder markdown = new StringBuilder();
        for (ReaderSection section : plan.sections()) {
            section(markdown, section.heading(), section.items(), readerContracts);
        }
        String value = markdown.toString();
        if (value.getBytes(StandardCharsets.UTF_8).length > request.resourceBudget().maxDocumentBytes()) {
            throw failure(Stage03FailureCode.RESOURCE_LIMIT_EXCEEDED);
        }
        ensureReaderClean(value);
        return new RenderedNineSectionDocument(request.nineSectionProfileRef().profileId(), sha256(value), value);
    }

    private static void section(StringBuilder markdown, String heading, List<ReaderItem> items,
                                ReaderContracts readerContracts) {
        if (items.isEmpty()) {
            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
        }
        markdown.append("## ").append(heading).append("\n\n");
        for (ReaderItem item : items) {
            markdown.append(renderItem(item, readerContracts)).append("\n");
        }
        markdown.append("\n");
    }

    private static String renderItem(ReaderItem item, ReaderContracts readerContracts) {
        ReaderSentenceTemplate template = readerContracts.templateFor(item);
        Map<String, String> slotValues = new TreeMap<>();
        Map<String, String> slotKinds = new TreeMap<>();
        for (ReaderSlot slot : item.slots()) {
            if (slotValues.put(slot.slotKey(), slotValue(slot)) != null) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            slotKinds.put(slot.slotKey(), slotKind(slot));
        }
        Map<String, String> expected = template.slots().stream().collect(java.util.stream.Collectors.toMap(
                TemplateSlotDeclaration::slotKey, TemplateSlotDeclaration::slotKind));
        if (!expected.equals(slotKinds)) {
            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
        }
        String literal = template.literalPattern();
        java.util.regex.Matcher matcher = TEMPLATE_SLOT.matcher(literal);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String value = slotValues.get(matcher.group(1));
            if (value == null) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            matcher.appendReplacement(rendered, java.util.regex.Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return "- " + readerValue(rendered.toString());
    }

    private static String slotKind(ReaderSlot slot) {
        if (slot instanceof ProvenValueSlot) {
            return "PROVEN_VALUE";
        }
        if (slot instanceof BusinessTermSlot) {
            return "BUSINESS_TERM";
        }
        if (slot instanceof TechnicalDisplaySlot) {
            return "TECHNICAL_DISPLAY";
        }
        if (slot instanceof BoundedQuestionSlot) {
            return "BOUNDED_QUESTION";
        }
        throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
    }

    private static String slotValue(ReaderSlot slot) {
        if (slot instanceof ProvenValueSlot value) {
            return readerValue(value.value());
        }
        if (slot instanceof BusinessTermSlot value) {
            return readerValue(value.value());
        }
        if (slot instanceof TechnicalDisplaySlot value) {
            return readerValue(value.value());
        }
        if (slot instanceof BoundedQuestionSlot value) {
            return readerValue(value.value());
        }
        throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
    }

    private static void ensureReaderClean(String markdown) {
        if (markdown.contains("stage02-result:") || markdown.contains("flow-slice:")
                || markdown.contains("evidence-capsule:") || markdown.contains("{{") || markdown.contains("}}")
                || SHA256.matcher(markdown).find() || markdown.contains("gpt-5.6")
                || markdown.contains(".java") || markdown.contains("\u0000")
                || containsFilesystemPath(markdown)) {
            throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
        }
    }

    private static String taskInput(Stage02Result stage02, CapsuleContext context, Stage03Request request,
                                    RegistryIndex registries, String taskSpecId,
                                    FlowImprovementOverlay improvementOverlay) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", "flow-task-input-v1");
        root.put("taskSpecId", taskSpecId);
        root.put("stage02ResultId", stage02.stage02ResultId());
        root.put("flowSliceId", context.flow().flowSliceId());
        root.put("evidenceCapsuleId", context.capsule().evidenceCapsuleId());
        root.set("flow", flowInput(context.flow()));
        root.set("evidenceCapsule", capsuleInput(context));
        ArrayNode anchors = root.putArray("anchors");
        for (Anchor anchor : context.anchorList()) {
            ObjectNode value = anchors.addObject();
            value.put("anchorKind", anchor.kind());
            value.put("anchorKey", anchor.key());
            sortedStrings(value.putArray("provenBindings"), anchor.provenBindings());
            value.put("provenDisplay", anchor.provenDisplay());
        }
        root.set("registryAdmission", registries.admissionInput(context));
        ObjectNode runtime = root.putObject("runtimePolicy");
        runtime.put("configuredAdapterId", request.modelRuntimePolicy().configuredAdapterId());
        runtime.put("configuredAuthMode", request.modelRuntimePolicy().configuredAuthMode());
        runtime.put("expectedUpstreamProvider", request.modelRuntimePolicy().expectedUpstreamProvider());
        runtime.put("expectedModel", request.modelRuntimePolicy().expectedModel());
        runtime.put("expectedReasoningEffort", request.modelRuntimePolicy().expectedReasoningEffort());
        runtime.put("expectedSandbox", request.modelRuntimePolicy().expectedSandbox());
        ObjectNode budget = root.putObject("resourceBudget");
        budget.put("maxFlowInterpretations", request.resourceBudget().maxFlowInterpretations());
        budget.put("roundsPerCapsule", request.resourceBudget().roundsPerCapsule());
        budget.put("maxTaskInputBytes", request.resourceBudget().maxTaskInputBytes());
        budget.put("maxResponseBytes", request.resourceBudget().maxResponseBytes());
        budget.put("requiredSectionCount", request.resourceBudget().requiredSectionCount());
        budget.put("maxDocumentBytes", request.resourceBudget().maxDocumentBytes());
        budget.put("maxReaderItems", request.resourceBudget().maxReaderItems());
        ObjectNode profiles = root.putObject("profiles");
        profiles.put("interpretationProfileId", request.interpretationProfileRef().profileId());
        profiles.put("knowledgeProfileId", request.knowledgeProfileRef().profileId());
        profiles.put("nineSectionProfileId", request.nineSectionProfileRef().profileId());
        profiles.put("registryBundleId", request.registryBundle().registryBundleId());
        if (improvementOverlay != null) {
            if (!context.flow().flowSliceId().equals(improvementOverlay.flowSliceId())) {
                throw failure(Stage03FailureCode.STAGE03_REQUEST_INVALID);
            }
            root.set("improvementOverlay", canonical(JSON.valueToTree(improvementOverlay)));
        }
        return canonicalTaskJson(root);
    }

    private static Map<String, FlowImprovementOverlay> indexOverlays(List<FlowImprovementOverlay> overlays) {
        if (overlays == null) {
            throw failure(Stage03FailureCode.STAGE03_REQUEST_INVALID);
        }
        Map<String, FlowImprovementOverlay> indexed = new TreeMap<>();
        try {
            for (FlowImprovementOverlay overlay : overlays) {
                if (overlay == null || indexed.put(overlay.flowSliceId(), overlay) != null) {
                    throw failure(Stage03FailureCode.STAGE03_REQUEST_INVALID);
                }
            }
            return Map.copyOf(indexed);
        } catch (IllegalArgumentException invalid) {
            throw failure(Stage03FailureCode.STAGE03_REQUEST_INVALID);
        }
    }

    /** Task bytes cross the generation/replay/archive boundary and are canonical by contract. */
    private static String canonicalTaskJson(ObjectNode root) {
        try {
            return JSON.writeValueAsString(canonical(root));
        } catch (JsonProcessingException impossible) {
            throw failure(Stage03FailureCode.STAGE03_REQUEST_INVALID);
        }
    }

    private static ObjectNode flowInput(FlowSlice flow) {
        ObjectNode node = JSON.createObjectNode();
        node.put("flowSliceId", flow.flowSliceId());
        node.put("entryId", flow.entryId());
        node.put("rootNodeId", flow.rootNodeId());
        ObjectNode trigger = node.putObject("trigger");
        trigger.put("kind", flow.trigger().kind());
        trigger.put("httpMethod", flow.trigger().httpMethod());
        trigger.put("route", flow.trigger().route());
        ArrayNode sharedSteps = node.putArray("sharedSteps");
        for (FlowStep step : flow.sharedSteps().stream().sorted(Comparator.comparing(FlowStep::flowStepId)).toList()) {
            ObjectNode value = sharedSteps.addObject();
            value.put("flowStepId", step.flowStepId());
            value.put("kind", step.kind());
            sortedStrings(value.putArray("repositoryNodeIds"), step.repositoryNodeIds());
            sortedStrings(value.putArray("factIds"), step.factIds());
            sortedStrings(value.putArray("atomIds"), step.atomIds());
        }
        sortedStrings(node.putArray("factIds"), flow.factIds());
        sortedStrings(node.putArray("atomIds"), flow.atomIds());
        sortedStrings(node.putArray("gapIds"), flow.gapIds());
        ArrayNode outcomes = node.putArray("outcomes");
        for (OutcomePath path : flow.outcomePaths().stream().sorted(Comparator.comparing(OutcomePath::outcomePathId)).toList()) {
            ObjectNode value = outcomes.addObject();
            value.put("outcomePathId", path.outcomePathId());
            value.put("terminalNodeId", path.terminalNodeId());
            value.put("terminalKind", path.terminalKind());
            ArrayNode decisions = value.putArray("decisions");
            path.decisions().forEach(decision -> decisions.addObject()
                    .put("guardNodeId", decision.guardNodeId())
                    .put("conditionAtomId", decision.conditionAtomId())
                    .put("polarity", decision.polarity())
                    .put("normalizedCondition", decision.normalizedCondition()));
            sortedStrings(value.putArray("terminalFactIds"), path.terminalFactIds());
            sortedStrings(value.putArray("requiredAtomIds"), path.requiredAtomIds());
            sortedStrings(value.putArray("requiredProofIds"), path.requiredProofIds());
        }
        return node;
    }

    private static ObjectNode capsuleInput(CapsuleContext context) {
        EvidenceCapsule capsule = context.capsule();
        ObjectNode node = JSON.createObjectNode();
        node.put("schemaVersion", capsule.schemaVersion());
        node.put("evidenceCapsuleId", capsule.evidenceCapsuleId());
        node.put("flowSliceId", capsule.flowSliceId());
        node.put("proofPackId", capsule.proofPackId());
        ObjectNode profile = node.putObject("projectionProfile");
        profile.put("profileId", capsule.projectionProfileRef().profileId());
        profile.put("sha256", capsule.projectionProfileRef().profileSha256());
        sortedStrings(node.putArray("outcomePathIds"), capsule.outcomePathIds());
        ArrayNode facts = node.putArray("allowedFacts");
        for (AllowedFactView fact : capsule.allowedFacts().stream().sorted(Comparator.comparing(AllowedFactView::factId)).toList()) {
            ObjectNode value = facts.addObject();
            value.put("factId", fact.factId());
            value.put("kind", fact.kind());
            ArrayNode atoms = value.putArray("atoms");
            for (AllowedAtomView atom : fact.atoms().stream().sorted(Comparator.comparing(AllowedAtomView::atomId)).toList()) {
                ObjectNode atomNode = atoms.addObject();
                atomNode.put("atomId", atom.atomId());
                atomNode.put("role", atom.role());
                atomNode.put("name", atom.name());
                atomNode.put("proofId", atom.proofId());
                atomNode.putObject("value").put("type", atom.value().type())
                        .put("canonical", atom.value().canonical());
            }
        }
        ArrayNode gaps = node.putArray("allowedGaps");
        for (AllowedGapView gap : capsule.allowedGaps().stream().sorted(Comparator.comparing(AllowedGapView::gapId)).toList()) {
            gaps.addObject().put("gapId", gap.gapId()).put("code", gap.code())
                    .put("reasonCode", context.gapReason(gap.gapId()));
        }
        ArrayNode spans = node.putArray("modelEvidenceSpans");
        for (ModelEvidenceSpan span : capsule.modelEvidenceSpans().stream()
                .sorted(Comparator.comparing(ModelEvidenceSpan::modelEvidenceSpanId)).toList()) {
            ObjectNode value = spans.addObject();
            value.put("modelEvidenceSpanId", span.modelEvidenceSpanId());
            value.put("sourceFileSha256", span.sourceFileSha256());
            value.put("excerpt", span.excerpt());
            value.put("excerptSha256", span.excerptSha256());
            ObjectNode locator = value.putObject("locator");
            locator.put("path", span.locator().path());
            locator.put("startByte", span.locator().startByte());
            locator.put("endByteExclusive", span.locator().endByteExclusive());
            locator.put("startLine", span.locator().startLine());
            locator.put("startColumn", span.locator().startColumn());
            locator.put("endLine", span.locator().endLine());
            locator.put("endColumn", span.locator().endColumn());
            sortedStrings(value.putArray("supportedAtomIds"), span.supportedAtomIds());
            sortedStrings(value.putArray("supportedOutcomePathIds"), span.supportedOutcomePathIds());
        }
        ArrayNode obligations = node.putArray("projectionObligations");
        for (ProjectionObligation obligation : capsule.projectionObligations().stream()
                .sorted(Comparator.comparing(ProjectionObligation::obligationId)).toList()) {
            ObjectNode value = obligations.addObject();
            value.put("obligationId", obligation.obligationId());
            value.put("kind", obligation.kind());
            value.put("subjectId", obligation.subjectId());
            sortedStrings(value.putArray("satisfyingSpanIds"), obligation.satisfyingSpanIds());
        }
        return node;
    }

    private static void sortedStrings(ArrayNode destination, Collection<String> values) {
        values.stream().sorted().forEach(destination::add);
    }

    private static String r1Schema() {
        return "{\"additionalProperties\":false,\"schemaVersion\":\"flow-interpretation-r1-v1\"}";
    }

    private static String r2Schema() {
        return "{\"additionalProperties\":false,\"schemaVersion\":\"flow-interpretation-r2-v1\"}";
    }

    private static ObjectNode object(String source, Stage03FailureCode code) {
        try {
            JsonNode node = JSON.readTree(source);
            if (node == null || !node.isObject()) {
                throw failure(code);
            }
            return (ObjectNode) node;
        } catch (JsonProcessingException invalid) {
            throw failure(code);
        }
    }

    private static void requireFields(ObjectNode node, Set<String> expected, Stage03FailureCode code) {
        if (!fieldNames(node).equals(expected)) {
            throw failure(code);
        }
    }

    private static void requireAllowedFields(ObjectNode node, Set<String> allowed, Set<String> required,
                                             Stage03FailureCode code) {
        Set<String> actual = fieldNames(node);
        if (!allowed.containsAll(actual) || !actual.containsAll(required)) {
            throw failure(code);
        }
    }

    private static List<String> resolveQuestionGaps(List<String> responseGaps, String questionKey,
                                                     CapsuleContext context, RegistryIndex registries) {
        QuestionEntry question = registries.question(questionKey);
        if (question == null || responseGaps.isEmpty() || !context.gapIds().containsAll(responseGaps)) {
            throw failure(Stage03FailureCode.MODEL_RESPONSE_REFERENCE_INVALID);
        }
        if (!responseGaps.stream().allMatch(gapId -> question.allowedGapReasonCodes()
                .contains(context.gapReason(gapId)))) {
            throw failure(Stage03FailureCode.MODEL_RESPONSE_REFERENCE_INVALID);
        }
        return responseGaps;
    }

    private static Set<String> fieldNames(ObjectNode node) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        return actual;
    }

    private static Set<String> without(Set<String> values, String excluded) {
        Set<String> result = new HashSet<>(values);
        result.remove(excluded);
        return result;
    }

    private static void requireIdentity(ObjectNode root, String schema, FlowModelTask task) {
        if (!schema.equals(text(root, "schemaVersion", Stage03FailureCode.MODEL_RESPONSE_IDENTITY_MISMATCH))
                || !task.taskSpecId().equals(text(root, "taskSpecId", Stage03FailureCode.MODEL_RESPONSE_IDENTITY_MISMATCH))
                || !task.flowSliceId().equals(text(root, "flowSliceId", Stage03FailureCode.MODEL_RESPONSE_IDENTITY_MISMATCH))
                || !task.evidenceCapsuleId().equals(text(root, "evidenceCapsuleId",
                Stage03FailureCode.MODEL_RESPONSE_IDENTITY_MISMATCH))) {
            throw failure(Stage03FailureCode.MODEL_RESPONSE_IDENTITY_MISMATCH);
        }
    }

    private static ArrayNode array(ObjectNode node, String field, Stage03FailureCode code) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw failure(code);
        }
        return (ArrayNode) value;
    }

    private static String text(ObjectNode node, String field, Stage03FailureCode code) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw failure(code);
        }
        return value.asText();
    }

    private static List<String> textArray(ObjectNode node, String field, Stage03FailureCode code) {
        ArrayNode values = array(node, field, code);
        Set<String> unique = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank() || !unique.add(value.asText())) {
                throw failure(code);
            }
            result.add(value.asText());
        }
        return List.copyOf(result);
    }

    private static Set<String> optional(String value) {
        return value == null ? Set.of() : Set.of(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String technicalDisplay(Anchor anchor, TechnicalDisplayPolicy policy, RegistryIndex registries) {
        policy.resolutionOrder().stream().filter(anchor.availableSlots()::contains).findFirst()
                .orElseThrow(() -> failure(Stage03FailureCode.TECHNICAL_FALLBACK_NOT_TOTAL));
        ReaderSentenceTemplate template = registries.readerContracts().technicalDisplayTemplate(policy);
        Map<String, String> declaredSlots = template.slots().stream().collect(java.util.stream.Collectors.toMap(
                TemplateSlotDeclaration::slotKey, TemplateSlotDeclaration::slotKind));
        if (!Map.of("technical-display", "TECHNICAL_DISPLAY").equals(declaredSlots)) {
            throw failure(Stage03FailureCode.REGISTRY_INVALID);
        }
        String display = readerValue(anchor.provenDisplay());
        java.util.regex.Matcher matcher = TEMPLATE_SLOT.matcher(template.literalPattern());
        StringBuffer rendered = new StringBuffer();
        int consumed = 0;
        while (matcher.find()) {
            if (!"technical-display".equals(matcher.group(1))) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            matcher.appendReplacement(rendered, java.util.regex.Matcher.quoteReplacement(display));
            consumed++;
        }
        matcher.appendTail(rendered);
        String value = rendered.toString();
        if (consumed != 1 || TEMPLATE_SLOT.matcher(value).find() || value.contains("{") || value.contains("}")) {
            throw failure(Stage03FailureCode.REGISTRY_INVALID);
        }
        return readerValue(value);
    }

    private static Stage03Exception failure(Stage03FailureCode code) {
        return new Stage03Exception(code);
    }

    private static String sha256(String source) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record FormulaDefinition(String atomId, String role, String formula, List<String> operators,
                                     List<String> operands, List<String> basisAtomIds) {
    }

    private record ObjectCandidate(String objectId, String kind, String anchorKey, String display,
                                   List<String> basisAtomIds) {
        private ObjectCandidate {
            basisAtomIds = List.copyOf(basisAtomIds);
        }
    }

    private record R1Proposal(String key, String type, String target, String term, List<String> claims,
                              String question, List<String> atoms, List<String> gaps) {
    }

    private record R2Review(String key, String decision, String term, List<String> claims,
                            List<String> atoms, List<String> gaps) {
    }

    private record Admission(String disposition, List<String> claimKeys, List<String> atomIds,
                             List<String> gapIds, AdmittedFlowMeaning meaning, InterpretationGap gap) {
    }

    private record Anchor(String key, String kind, Set<String> availableSlots, List<String> provenBindings,
                          String provenDisplay) {
        private Anchor {
            availableSlots = Set.copyOf(availableSlots);
            provenBindings = List.copyOf(provenBindings);
        }
    }

    private record CapsuleContext(FlowSlice flow, EvidenceCapsule capsule, Map<String, AllowedAtomView> atoms,
                                  Map<String, String> gaps, Map<String, Anchor> anchors,
                                  Set<String> factKinds) {
        static CapsuleContext of(FlowSlice flow, EvidenceCapsule capsule, Stage02Result stage02,
                                 Stage01FlowView flowView, Map<String, String> stage01GapReasons) {
            Map<String, AllowedAtomView> atoms = new TreeMap<>();
            Set<String> factKinds = new HashSet<>();
            for (AllowedFactView fact : capsule.allowedFacts()) {
                factKinds.add(fact.kind());
                for (AllowedAtomView atom : fact.atoms()) {
                    if (atoms.put(atom.atomId(), atom) != null) {
                        throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
                    }
                }
            }
            Map<String, String> gaps = new TreeMap<>();
            Map<String, AllowedGapView> capsuleGaps = new TreeMap<>();
            for (AllowedGapView gap : capsule.allowedGaps()) {
                if (gap == null || blank(gap.gapId()) || capsuleGaps.put(gap.gapId(), gap) != null) {
                    throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
                }
                String reason = stage01GapReasons.get(gap.gapId());
                if (blank(reason) || gaps.put(gap.gapId(), reason) != null) {
                    throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
                }
            }
            for (String gapId : flow.gapIds()) {
                if (capsuleGaps.containsKey(gapId)) {
                    continue;
                }
                List<FlowGap> matches = stage02.flowGaps().stream()
                        .filter(gap -> gapId.equals(gap.flowGapId())).toList();
                if (matches.size() != 1 || !flow.entryId().equals(matches.get(0).entryId())
                        || blank(matches.get(0).code()) || gaps.put(gapId, matches.get(0).code()) != null) {
                    throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
                }
            }
            Map<String, Anchor> anchors = anchors(flow, capsule, flowView);
            return new CapsuleContext(flow, capsule, Map.copyOf(atoms), Map.copyOf(gaps), Map.copyOf(anchors),
                    Set.copyOf(factKinds));
        }

        private static Map<String, Anchor> anchors(FlowSlice flow, EvidenceCapsule capsule,
                                                    Stage01FlowView flowView) {
            Map<String, FlowNodeView> nodes = new TreeMap<>();
            for (FlowNodeView node : flowView.nodes()) {
                if (node == null || nodes.put(node.nodeId(), node) != null) {
                    throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
                }
            }
            FlowEntryView entry = flowView.entries().stream().filter(candidate -> flow.entryId().equals(
                    candidate.entryId())).sorted(Comparator.comparing(FlowEntryView::entryId)).findFirst()
                    .orElseThrow(() -> failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN));
            FlowNodeView root = nodes.get(flow.rootNodeId());
            FlowNodeView entryMethod = nodes.get(entry.methodNodeId());
            if (root == null || entryMethod == null) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }

            AllowedFactView httpEntry = fact(capsule, "HTTP_ENTRY");
            AllowedAtomView requestAtom = atom(httpEntry, "REQUEST_BODY");
            AllowedFactView inventoryLoad = fact(capsule, "INVENTORY_LOAD");
            AllowedAtomView recordAtom = atom(inventoryLoad, null);
            AllowedAtomView tableAtom = capsule.allowedFacts().stream().flatMap(value -> value.atoms().stream())
                    .filter(value -> "TABLE".equals(value.name())).sorted(Comparator.comparing(AllowedAtomView::atomId))
                    .findFirst().orElse(null);
            AllowedFactView successResult = fact(capsule, "SUCCESS_RESULT");
            AllowedAtomView resultAtom = atom(successResult, null);
            OutcomePath outcome = flow.outcomePaths().stream().sorted(Comparator.comparing(OutcomePath::outcomePathId))
                    .findFirst().orElseThrow(() -> failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN));
            FlowStep activity = flow.sharedSteps().stream().sorted(Comparator.comparing(FlowStep::flowStepId))
                    .findFirst().orElseThrow(() -> failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN));

            Map<String, Anchor> result = new TreeMap<>();
            add(result, anchor("FLOW", entry.route(), anchorSlots("FLOW"), cleanDisplay(flow.trigger().httpMethod()
                    + " " + flow.trigger().route(), "已证明的 HTTP 入口"), List.of(entry.entryId(), flow.rootNodeId(),
                    entry.methodNodeId(), root.nodeId(), entryMethod.nodeId(), entry.httpMethod(), entry.route())));
            String requestBinding = boundRequestType(flowView, nodes, entryMethod, requestAtom);
            List<String> requestBindings = new ArrayList<>(bindings(httpEntry, requestAtom));
            requestBindings.add(requestBinding);
            add(result, anchor("REQUEST", requestBinding, anchorSlots("REQUEST"), cleanDisplay(requestAtom,
                    "已证明的请求类型"), requestBindings));
            List<String> recordBindings = new ArrayList<>(bindings(inventoryLoad, recordAtom));
            String recordBinding = inventoryLoad.kind() + ":" + (tableAtom == null ? recordAtom.name()
                    : tableAtom.name()) + ":" + atomValue(tableAtom == null ? recordAtom : tableAtom);
            if (tableAtom != null) {
                recordBindings.add(tableAtom.atomId());
                recordBindings.add(tableAtom.name());
                recordBindings.add(recordBinding);
            }
            add(result, anchor("RECORD", recordBinding, anchorSlots("RECORD"), cleanDisplay(
                    tableAtom == null ? recordAtom : tableAtom, "已证明的数据记录"), recordBindings));
            String resultBinding = resultBinding(successResult);
            List<String> resultBindings = new ArrayList<>(bindings(successResult, resultAtom));
            resultBindings.add(resultBinding);
            add(result, anchor("RESULT", resultBinding, anchorSlots("RESULT"), cleanDisplay(resultAtom,
                    "已证明的结果类型"), resultBindings));
            add(result, anchor("OUTCOME", outcome.terminalKind(), anchorSlots("OUTCOME"), cleanDisplay(
                    outcome.terminalKind(), "已证明的流程结束方式"), outcomeBindings(flow.outcomePaths())));
            String activityPrimary = activity.factIds().stream().sorted().findFirst().orElse(activity.flowStepId());
            add(result, anchor("ACTIVITY", activityPrimary, anchorSlots("ACTIVITY"), cleanDisplay(
                    activity.kind(), "已证明的处理活动"), activityBindings(activity)));
            return Map.copyOf(result);
        }

        private static void add(Map<String, Anchor> anchors, Anchor anchor) {
            if (anchors.put(anchor.key(), anchor) != null) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }
        }

        private static Anchor anchor(String kind, String primaryBinding, Set<String> slots, String display,
                                     List<String> bindings) {
            List<String> sorted = bindings.stream().filter(value -> !blank(value)).distinct().sorted().toList();
            if (blank(primaryBinding) || sorted.isEmpty() || !safeReaderText(display)) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }
            String key = "anchor:" + kind.toLowerCase(java.util.Locale.ROOT) + ":" + primaryBinding;
            return new Anchor(key, kind, slots, sorted, display);
        }

        private static AllowedFactView fact(EvidenceCapsule capsule, String kind) {
            return capsule.allowedFacts().stream().filter(value -> kind.equals(value.kind()))
                    .sorted(Comparator.comparing(AllowedFactView::factId)).findFirst()
                    .orElseThrow(() -> failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN));
        }

        private static AllowedAtomView atom(AllowedFactView fact, String preferredName) {
            List<AllowedAtomView> atoms = fact.atoms().stream().filter(value -> preferredName == null
                    || preferredName.equals(value.name())).sorted(Comparator.comparing(AllowedAtomView::atomId)).toList();
            if (!atoms.isEmpty()) {
                return atoms.get(0);
            }
            if (preferredName != null) {
                return atom(fact, null);
            }
            throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
        }

        private static List<String> bindings(AllowedFactView fact, AllowedAtomView atom) {
            List<String> result = new ArrayList<>(List.of(fact.factId(), fact.kind(), atom.atomId(), atom.proofId(),
                    atom.role(), atom.name()));
            if (atom.value() != null) {
                result.add(atom.value().canonical());
            }
            return result;
        }

        private static String boundRequestType(Stage01FlowView flowView, Map<String, FlowNodeView> nodes,
                                               FlowNodeView entryMethod, AllowedAtomView requestAtom) {
            String requestType = atomValue(requestAtom);
            String parameterPrefix = "v1:" + entryMethod.nodeId() + ":";
            List<String> candidates = new ArrayList<>();
            for (FlowEdgeView edge : flowView.edges()) {
                if (!"PARAMETER_TYPE".equals(edge.kind())) {
                    continue;
                }
                FlowNodeView parameter = nodes.get(edge.fromNodeId());
                FlowNodeView type = nodes.get(edge.toNodeId());
                if (parameter == null || type == null || !"JAVA_PARAMETER".equals(parameter.kind())
                        || !parameter.canonicalValue().startsWith(parameterPrefix)
                        || !"JAVA_RECORD_DECLARATION".equals(type.kind())) {
                    continue;
                }
                String candidate = unversioned(type.canonicalValue());
                if (candidate.equals(requestType) || candidate.endsWith("." + requestType)) {
                    candidates.add(candidate);
                }
            }
            List<String> exact = candidates.stream().distinct().sorted().toList();
            if (exact.size() != 1) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }
            return exact.get(0);
        }

        private static String resultBinding(AllowedFactView successResult) {
            StringBuilder canonical = new StringBuilder("success-result-atoms-v1");
            for (AllowedAtomView atom : successResult.atoms().stream()
                    .sorted(Comparator.comparing(AllowedAtomView::atomId)).toList()) {
                canonical.append('\n').append(atom.role()).append('\n').append(atom.name()).append('\n')
                        .append(atomValue(atom));
            }
            return successResult.kind() + ":" + sha256(canonical.toString());
        }

        private static String atomValue(AllowedAtomView atom) {
            if (atom == null || atom.value() == null || blank(atom.value().canonical())) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }
            return atom.value().canonical();
        }

        private static String unversioned(String value) {
            if (blank(value) || !value.startsWith("v1:") || value.length() == 3) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }
            return value.substring(3);
        }

        private static List<String> outcomeBindings(List<OutcomePath> outcomes) {
            List<String> result = new ArrayList<>();
            for (OutcomePath outcome : outcomes.stream().sorted(Comparator.comparing(OutcomePath::outcomePathId)).toList()) {
                result.add(outcome.outcomePathId());
                result.add(outcome.terminalNodeId());
                result.add(outcome.terminalKind());
                result.addAll(outcome.terminalFactIds());
                result.addAll(outcome.requiredAtomIds());
                result.addAll(outcome.requiredProofIds());
            }
            return result;
        }

        private static List<String> activityBindings(FlowStep step) {
            List<String> result = new ArrayList<>(List.of(step.flowStepId(), step.kind()));
            result.addAll(step.repositoryNodeIds());
            result.addAll(step.factIds());
            result.addAll(step.atomIds());
            return result;
        }

        private static String cleanDisplay(AllowedAtomView atom, String fallback) {
            return atom.value() == null ? fallback : cleanDisplay(atom.value().canonical(), fallback);
        }

        private static String cleanDisplay(String value, String fallback) {
            return safeReaderText(value) ? value : fallback;
        }

        void validateClosure() {
            Set<String> expectedGapIds = new TreeSet<>(flow.gapIds());
            expectedGapIds.addAll(capsule.allowedGaps().stream().map(AllowedGapView::gapId).toList());
            if (!flow.flowSliceId().equals(capsule.flowSliceId())
                    || !new TreeSet<>(flow.atomIds()).equals(new TreeSet<>(atoms.keySet()))
                    || !new TreeSet<>(flow.outcomePaths().stream().map(OutcomePath::outcomePathId).toList())
                    .equals(new TreeSet<>(capsule.outcomePathIds()))
                    || !expectedGapIds.equals(new TreeSet<>(gaps.keySet()))) {
                throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            }
        }

        Set<String> atomIds() {
            return atoms.keySet();
        }

        Set<String> gapIds() {
            return gaps.keySet();
        }

        String gapReason(String gapId) {
            return gaps.get(gapId);
        }

        Anchor anchor(String key) {
            return anchors.get(key);
        }

        List<Anchor> anchorList() {
            return anchors.values().stream().sorted(Comparator.comparing(Anchor::key)).toList();
        }

        boolean hasFact(String kind) {
            return factKinds.contains(kind);
        }

        private static Set<String> anchorSlots(String kind) {
            return switch (kind) {
                case "FLOW" -> Set.of("HTTP_METHOD_ROUTE_AND_HANDLER");
                case "REQUEST", "RESULT" -> Set.of("BOUND_TYPE_FQN");
                case "RECORD" -> Set.of("SQL_TABLE", "BOUND_TYPE_FQN");
                case "OUTCOME" -> Set.of("THROW_TYPE", "RETURN_TYPE");
                case "ACTIVITY" -> Set.of("ADMITTED_CLAIM_TEMPLATE", "TECHNICAL_ANCHOR_KEY");
                default -> throw failure(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
            };
        }
    }

    private record ReaderContracts(Map<String, ReaderSentenceTemplate> templates,
                                   Map<String, String> owners,
                                   Map<String, ReaderSentenceTemplate> emptySectionTemplates) {
        private static final Set<String> REQUIRED_KNOWLEDGE_KINDS = Set.of("PROVEN_VALUE", "BUSINESS_TERM",
                "BOUNDED_QUESTION", "TECHNICAL_DISPLAY", "OUTCOME", "FIELD", "METRIC");
        private static final Map<String, String> FIXED_M6_OWNERS = Map.of(
                "BUSINESS_OBJECT", "section-3",
                "BUSINESS_ACTIVITY", "section-4",
                "OBJECT_RELATION", "section-6",
                "ANSWERABLE_QUESTION", "section-8");
        private static final Set<String> SLOT_KINDS = Set.of("PROVEN_VALUE", "BUSINESS_TERM", "BOUNDED_QUESTION",
                "TECHNICAL_DISPLAY");

        static ReaderContracts of(ReaderSentenceTemplateRegistry templateRegistry,
                                  SectionOwnershipRegistry ownershipRegistry) {
            if (templateRegistry.templates().isEmpty() && ownershipRegistry.rules().isEmpty()) {
                return builtIns();
            }
            if (templateRegistry.templates().isEmpty() || ownershipRegistry.rules().isEmpty()) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            Map<String, ReaderSentenceTemplate> templates = new TreeMap<>(fixedBuiltInTemplates());
            Set<String> declaredTemplateKeys = new HashSet<>();
            for (ReaderSentenceTemplate template : templateRegistry.templates()) {
                validateTemplate(template);
                if (!declaredTemplateKeys.add(template.templateKey())
                        || fixedM6Templates().containsKey(template.templateKey())) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
                templates.put(template.templateKey(), template);
            }
            Map<String, String> owners = new TreeMap<>();
            for (OwnershipRule rule : ownershipRegistry.rules()) {
                if (rule == null || !REQUIRED_KNOWLEDGE_KINDS.contains(rule.knowledgeKind())
                        || !knownSection(rule.ownerSectionKey())
                        || owners.put(rule.knowledgeKind(), rule.ownerSectionKey()) != null) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
            }
            if (!owners.keySet().equals(REQUIRED_KNOWLEDGE_KINDS)) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            owners.putAll(FIXED_M6_OWNERS);
            ReaderContracts contracts = new ReaderContracts(Map.copyOf(templates), Map.copyOf(owners),
                    emptySectionBuiltIns());
            contracts.itemTemplate("PROVEN_VALUE", "READER_ATOM_V1");
            contracts.itemTemplate("BUSINESS_TERM", "READER_TERM_V1");
            contracts.itemTemplate("BOUNDED_QUESTION", "READER_GAP_V1");
            contracts.itemTemplate("OUTCOME", "READER_OUTCOME_V1");
            contracts.itemTemplate("FIELD", "READER_FIELD_FORMULA_V1");
            contracts.itemTemplate("METRIC", "READER_METRIC_FORMULA_V1");
            contracts.itemTemplate("BUSINESS_OBJECT", "READER_BUSINESS_OBJECT_V1");
            contracts.itemTemplate("BUSINESS_ACTIVITY", "READER_BUSINESS_ACTIVITY_V1");
            contracts.itemTemplate("OBJECT_RELATION", "READER_OBJECT_RELATION_V1");
            contracts.itemTemplate("ANSWERABLE_QUESTION", "READER_ANSWERABLE_QUESTION_V1");
            return contracts;
        }

        private static ReaderContracts builtIns() {
            Map<String, ReaderSentenceTemplate> templates = new TreeMap<>();
            templates.put("READER_ATOM_V1", template("READER_ATOM_V1", "section-4", "{proven-value}",
                    "proven-value", "PROVEN_VALUE"));
            templates.put("READER_TERM_V1", template("READER_TERM_V1", "section-2", "{business-term}",
                    "business-term", "BUSINESS_TERM"));
            templates.put("READER_GAP_V1", template("READER_GAP_V1", "section-9", "{bounded-question}",
                    "bounded-question", "BOUNDED_QUESTION"));
            templates.put("READER_TECHNICAL_V1", template("READER_TECHNICAL_V1", "section-1",
                    "{technical-display}", "technical-display", "TECHNICAL_DISPLAY"));
            templates.putAll(fixedBuiltInTemplates());
            templates.put("READER_OUTCOME_V1", new ReaderSentenceTemplate("READER_OUTCOME_V1", "section-4",
                    "终点：{outcome-terminal}；分支：{outcome-semantics}", List.of(
                    new TemplateSlotDeclaration("outcome-terminal", "TECHNICAL_DISPLAY"),
                    new TemplateSlotDeclaration("outcome-semantics", "TECHNICAL_DISPLAY"))));
            templates.put("READER_FIELD_FORMULA_V1", new ReaderSentenceTemplate("READER_FIELD_FORMULA_V1",
                    "section-5", "{formula-role}：{formula-value}；运算符：{formula-operators}；操作数："
                    + "{formula-operands}", List.of(
                    new TemplateSlotDeclaration("formula-role", "TECHNICAL_DISPLAY"),
                    new TemplateSlotDeclaration("formula-value", "TECHNICAL_DISPLAY"),
                    new TemplateSlotDeclaration("formula-operators", "TECHNICAL_DISPLAY"),
                    new TemplateSlotDeclaration("formula-operands", "TECHNICAL_DISPLAY"))));
            templates.put("READER_METRIC_FORMULA_V1", new ReaderSentenceTemplate("READER_METRIC_FORMULA_V1",
                    "section-7", "{formula-role}：{formula-value}；运算符：{formula-operators}；操作数："
                    + "{formula-operands}", List.of(
                    new TemplateSlotDeclaration("formula-role", "TECHNICAL_DISPLAY"),
                    new TemplateSlotDeclaration("formula-value", "TECHNICAL_DISPLAY"),
                    new TemplateSlotDeclaration("formula-operators", "TECHNICAL_DISPLAY"),
                    new TemplateSlotDeclaration("formula-operands", "TECHNICAL_DISPLAY"))));
            Map<String, String> owners = new TreeMap<>(Map.of("PROVEN_VALUE", "section-4", "BUSINESS_TERM",
                    "section-2", "BOUNDED_QUESTION", "section-9", "TECHNICAL_DISPLAY", "section-1", "OUTCOME",
                    "section-4", "FIELD", "section-5", "METRIC", "section-7"));
            owners.putAll(FIXED_M6_OWNERS);
            return new ReaderContracts(Map.copyOf(templates), owners, emptySectionBuiltIns());
        }

        private static Map<String, ReaderSentenceTemplate> fixedBuiltInTemplates() {
            Map<String, ReaderSentenceTemplate> templates = new TreeMap<>(builtInTechnicalTemplates());
            templates.putAll(fixedM6Templates());
            return Map.copyOf(templates);
        }

        private static Map<String, ReaderSentenceTemplate> fixedM6Templates() {
            Map<String, ReaderSentenceTemplate> templates = new TreeMap<>();
            templates.put("READER_BUSINESS_OBJECT_V1", template("READER_BUSINESS_OBJECT_V1", "section-3",
                    "对象：{object-display}", "object-display", "TECHNICAL_DISPLAY"));
            templates.put("READER_BUSINESS_ACTIVITY_V1", template("READER_BUSINESS_ACTIVITY_V1", "section-4",
                    "活动：{activity-display}", "activity-display", "TECHNICAL_DISPLAY"));
            templates.put("READER_OBJECT_RELATION_V1", new ReaderSentenceTemplate("READER_OBJECT_RELATION_V1",
                    "section-6", "已证明的流程方向：{source-display} → {target-display}", List.of(
                    new TemplateSlotDeclaration("source-display", "TECHNICAL_DISPLAY"),
                    new TemplateSlotDeclaration("target-display", "TECHNICAL_DISPLAY"))));
            templates.put("READER_ANSWERABLE_QUESTION_V1", template("READER_ANSWERABLE_QUESTION_V1", "section-8",
                    "{answerable-question}", "answerable-question", "BOUNDED_QUESTION"));
            return Map.copyOf(templates);
        }

        private static Map<String, ReaderSentenceTemplate> builtInTechnicalTemplates() {
            Map<String, ReaderSentenceTemplate> templates = new TreeMap<>();
            templates.put("TECHNICAL_FLOW_DISPLAY_V1", template("TECHNICAL_FLOW_DISPLAY_V1", "section-1",
                    "{technical-display}", "technical-display", "TECHNICAL_DISPLAY"));
            templates.put("TECHNICAL_TYPE_DISPLAY_V1", template("TECHNICAL_TYPE_DISPLAY_V1", "section-3",
                    "{technical-display}", "technical-display", "TECHNICAL_DISPLAY"));
            templates.put("TECHNICAL_RECORD_DISPLAY_V1", template("TECHNICAL_RECORD_DISPLAY_V1", "section-3",
                    "{technical-display}", "technical-display", "TECHNICAL_DISPLAY"));
            templates.put("TECHNICAL_OUTCOME_DISPLAY_V1", template("TECHNICAL_OUTCOME_DISPLAY_V1", "section-4",
                    "{technical-display}", "technical-display", "TECHNICAL_DISPLAY"));
            templates.put("TECHNICAL_ACTIVITY_DISPLAY_V1", template("TECHNICAL_ACTIVITY_DISPLAY_V1", "section-4",
                    "{technical-display}", "technical-display", "TECHNICAL_DISPLAY"));
            return Map.copyOf(templates);
        }

        ReaderSentenceTemplate itemTemplate(String knowledgeKind, String templateKey) {
            ReaderSentenceTemplate template = templates.get(templateKey);
            String owner = owners.get(knowledgeKind);
            if (template == null || owner == null || !owner.equals(template.ownerSectionKey())
                    || !slotSignature(knowledgeKind).equals(signature(template.slots()))) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            if (("OUTCOME".equals(knowledgeKind) && !"section-4".equals(template.ownerSectionKey()))
                    || ("FIELD".equals(knowledgeKind) && !"section-5".equals(template.ownerSectionKey()))
                    || ("METRIC".equals(knowledgeKind) && !"section-7".equals(template.ownerSectionKey()))) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            return template;
        }

        ReaderSentenceTemplate technicalDisplayTemplate(TechnicalDisplayPolicy policy) {
            if (policy == null || blank(policy.displayTemplateKey())) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            ReaderSentenceTemplate template = templates.get(policy.displayTemplateKey());
            if (template == null || !Map.of("technical-display", "TECHNICAL_DISPLAY")
                    .equals(signature(template.slots()))) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            java.util.regex.Matcher matcher = TEMPLATE_SLOT.matcher(template.literalPattern());
            int placeholders = 0;
            while (matcher.find()) {
                if (!"technical-display".equals(matcher.group(1))) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
                placeholders++;
            }
            String literalWithoutSlots = TEMPLATE_SLOT.matcher(template.literalPattern()).replaceAll("");
            if (placeholders != 1 || literalWithoutSlots.contains("{") || literalWithoutSlots.contains("}")) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            return template;
        }

        ReaderSentenceTemplate emptySectionTemplate(String sectionKey) {
            ReaderSentenceTemplate template = emptySectionTemplates.get(sectionKey);
            if (template == null || !sectionKey.equals(template.ownerSectionKey())
                    || !slotSignature("TECHNICAL_DISPLAY").equals(signature(template.slots()))) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            return template;
        }

        ReaderSentenceTemplate templateFor(ReaderItem item) {
            ReaderSentenceTemplate template = "EMPTY_SECTION".equals(item.itemKind())
                    ? emptySectionTemplates.get(item.ownerSectionKey()) : templates.get(item.templateKey());
            if (template == null || !template.templateKey().equals(item.templateKey())
                    || !template.ownerSectionKey().equals(item.ownerSectionKey())) {
                throw failure(Stage03FailureCode.READER_INVARIANT_BROKEN);
            }
            return template;
        }

        private static Map<String, ReaderSentenceTemplate> emptySectionBuiltIns() {
            Map<String, ReaderSentenceTemplate> templates = new TreeMap<>();
            for (int index = 0; index < HEADINGS.size(); index++) {
                String section = sectionKey(index);
                String key = "READER_EMPTY_SECTION_" + (index + 1) + "_V1";
                templates.put(section, template(key, section, "当前冻结静态证据范围内无额外内容：{technical-display}",
                        "technical-display", "TECHNICAL_DISPLAY"));
            }
            return Map.copyOf(templates);
        }

        private static ReaderSentenceTemplate template(String key, String section, String literal, String slotKey,
                                                       String slotKind) {
            return new ReaderSentenceTemplate(key, section, literal,
                    List.of(new TemplateSlotDeclaration(slotKey, slotKind)));
        }

        private static void validateTemplate(ReaderSentenceTemplate template) {
            if (template == null || blank(template.templateKey()) || !knownSection(template.ownerSectionKey())
                    || !safeRegistryLiteral(template.literalPattern()) || template.slots() == null
                    || template.slots().isEmpty()) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            Map<String, String> slots = new TreeMap<>();
            for (TemplateSlotDeclaration slot : template.slots()) {
                if (slot == null || blank(slot.slotKey()) || !SLOT_KINDS.contains(slot.slotKind())
                        || slots.put(slot.slotKey(), slot.slotKind()) != null) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
            }
            Set<String> placeholders = new TreeSet<>();
            java.util.regex.Matcher matcher = TEMPLATE_SLOT.matcher(template.literalPattern());
            while (matcher.find()) {
                placeholders.add(matcher.group(1));
            }
            String withoutPlaceholders = TEMPLATE_SLOT.matcher(template.literalPattern()).replaceAll("");
            if (!placeholders.equals(slots.keySet()) || withoutPlaceholders.contains("{")
                    || withoutPlaceholders.contains("}")) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
        }

        private static Map<String, String> signature(List<TemplateSlotDeclaration> slots) {
            Map<String, String> values = new TreeMap<>();
            for (TemplateSlotDeclaration slot : slots) {
                if (values.put(slot.slotKey(), slot.slotKind()) != null) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
            }
            return Map.copyOf(values);
        }

        private static Map<String, String> slotSignature(String knowledgeKind) {
            return switch (knowledgeKind) {
                case "PROVEN_VALUE" -> Map.of("proven-value", "PROVEN_VALUE");
                case "BUSINESS_TERM" -> Map.of("business-term", "BUSINESS_TERM");
                case "BOUNDED_QUESTION" -> Map.of("bounded-question", "BOUNDED_QUESTION");
                case "TECHNICAL_DISPLAY" -> Map.of("technical-display", "TECHNICAL_DISPLAY");
                case "OUTCOME" -> Map.of("outcome-terminal", "TECHNICAL_DISPLAY", "outcome-semantics",
                        "TECHNICAL_DISPLAY");
                case "FIELD", "METRIC" -> Map.of("formula-role", "TECHNICAL_DISPLAY", "formula-value",
                        "TECHNICAL_DISPLAY", "formula-operators", "TECHNICAL_DISPLAY", "formula-operands",
                        "TECHNICAL_DISPLAY");
                case "BUSINESS_OBJECT" -> Map.of("object-display", "TECHNICAL_DISPLAY");
                case "BUSINESS_ACTIVITY" -> Map.of("activity-display", "TECHNICAL_DISPLAY");
                case "OBJECT_RELATION" -> Map.of("source-display", "TECHNICAL_DISPLAY", "target-display",
                        "TECHNICAL_DISPLAY");
                case "ANSWERABLE_QUESTION" -> Map.of("answerable-question", "BOUNDED_QUESTION");
                default -> throw failure(Stage03FailureCode.REGISTRY_INVALID);
            };
        }

        private static boolean knownSection(String key) {
            return key != null && key.matches("section-[1-9]");
        }
    }

    private record RegistryIndex(RegistryBundle bundle, ReaderContracts readerContracts,
                                 Map<String, BusinessTermEntry> terms,
                                 Map<String, ClaimEntry> claims, Map<String, QuestionEntry> questions,
                                 Map<String, List<TechnicalDisplayPolicy>> displays) {
        static RegistryIndex of(RegistryBundle bundle) {
            try {
                if (bundle == null || blank(bundle.registryBundleId()) || bundle.businessTerms() == null
                        || bundle.technicalDisplays() == null || bundle.claims() == null || bundle.questions() == null
                        || bundle.sentenceTemplates() == null || bundle.sectionOwnership() == null) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
                validateRegistry(bundle.businessTerms().schemaVersion(), "business-term-registry-v1",
                        bundle.businessTerms().sha256(), Stage03RegistryCanonicalizer.businessTermsDigest(
                                bundle.businessTerms()));
                validateRegistry(bundle.technicalDisplays().schemaVersion(), "technical-display-registry-v1",
                        bundle.technicalDisplays().sha256(), Stage03RegistryCanonicalizer.technicalDisplaysDigest(
                                bundle.technicalDisplays()));
                validateRegistry(bundle.claims().schemaVersion(), "claim-registry-v1", bundle.claims().sha256(),
                        Stage03RegistryCanonicalizer.claimsDigest(bundle.claims()));
                validateRegistry(bundle.questions().schemaVersion(), "question-registry-v1", bundle.questions().sha256(),
                        Stage03RegistryCanonicalizer.questionsDigest(bundle.questions()));
                validateRegistry(bundle.sentenceTemplates().schemaVersion(), "reader-template-registry-v1",
                        bundle.sentenceTemplates().sha256(), Stage03RegistryCanonicalizer.templatesDigest(
                                bundle.sentenceTemplates()));
                validateRegistry(bundle.sectionOwnership().schemaVersion(), "section-ownership-registry-v1",
                        bundle.sectionOwnership().sha256(), Stage03RegistryCanonicalizer.ownershipDigest(
                                bundle.sectionOwnership()));
                String expectedBundleId = Stage03RegistryCanonicalizer.bundleId(bundle.businessTerms(),
                        bundle.technicalDisplays(), bundle.claims(), bundle.questions(), bundle.sentenceTemplates(),
                        bundle.sectionOwnership());
                if (!expectedBundleId.equals(bundle.registryBundleId())) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
                Map<String, List<TechnicalDisplayPolicy>> displays = displays(bundle);
                Map<String, BusinessTermEntry> terms = unique(bundle.businessTerms().terms(),
                        BusinessTermEntry::businessTermKey);
                Map<String, ClaimEntry> claims = unique(bundle.claims().claims(), ClaimEntry::claimKey);
                Map<String, QuestionEntry> questions = unique(bundle.questions().questions(), QuestionEntry::questionKey);
                validateEntries(terms, claims, questions, displays);
                ReaderContracts readerContracts = ReaderContracts.of(bundle.sentenceTemplates(),
                        bundle.sectionOwnership());
                displays.values().stream().flatMap(List::stream)
                        .forEach(readerContracts::technicalDisplayTemplate);
                return new RegistryIndex(bundle, readerContracts, terms, claims, questions, displays);
            } catch (Stage03Exception alreadyStable) {
                throw alreadyStable;
            } catch (RuntimeException malformed) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
        }

        private static void validateRegistry(String schema, String expectedSchema, String suppliedDigest,
                                             String computedDigest) {
            if (!expectedSchema.equals(schema) || blank(suppliedDigest) || !suppliedDigest.equals(computedDigest)) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
        }

        private static Map<String, List<TechnicalDisplayPolicy>> displays(RegistryBundle bundle) {
            Map<String, List<TechnicalDisplayPolicy>> result = new HashMap<>();
            Set<String> policyKeys = new HashSet<>();
            for (TechnicalDisplayPolicy policy : bundle.technicalDisplays().policies()) {
                if (policy == null || blank(policy.policyKey()) || blank(policy.anchorKind())
                        || policy.resolutionOrder() == null || policy.resolutionOrder().isEmpty()
                        || policy.resolutionOrder().stream().anyMatch(Stage03Generator::blank)
                        || blank(policy.displayTemplateKey())
                        || !safeRegistryLiteral(policy.displayTemplateKey())
                        || !policy.displayTemplateKey().matches("TECHNICAL_[A-Z_]+_V1")
                        || !policyKeys.add(policy.policyKey())) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
                result.computeIfAbsent(policy.anchorKind(), ignored -> new ArrayList<>()).add(policy);
            }
            return Map.copyOf(result);
        }

        private static void validateEntries(Map<String, BusinessTermEntry> terms, Map<String, ClaimEntry> claims,
                                            Map<String, QuestionEntry> questions,
                                            Map<String, List<TechnicalDisplayPolicy>> displays) {
            for (BusinessTermEntry term : terms.values()) {
                if (blank(term.anchorKind()) || !safeReaderText(term.localizedValue()) || term.eligibleAtomKinds() == null
                        || term.eligibleAtomKinds().isEmpty() || term.minimumBasisAtomIds() == null
                        || blank(term.technicalFallbackPolicyKey())) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
                TechnicalDisplayPolicy policy = displays.values().stream().flatMap(List::stream)
                        .filter(candidate -> term.technicalFallbackPolicyKey().equals(candidate.policyKey()))
                        .findFirst().orElse(null);
                if (policy == null || !term.anchorKind().equals(policy.anchorKind())) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
            }
            for (ClaimEntry claim : claims.values()) {
                if (blank(claim.targetAnchorKind()) || !safeRegistryLiteral(claim.readerTemplateKey())
                        || claim.requiredAtomPatterns() == null || claim.requiredAtomPatterns().isEmpty()) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
            }
            for (QuestionEntry question : questions.values()) {
                if (question.allowedGapReasonCodes() == null || question.allowedGapReasonCodes().isEmpty()
                        || !safeReaderText(question.questionKey())
                        || !safeRegistryLiteral(question.readerTemplateKey())) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
            }
        }

        private static <T> Map<String, T> unique(List<T> values, java.util.function.Function<T, String> key) {
            Map<String, T> result = new HashMap<>();
            for (T value : values) {
                if (value == null) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
                String itemKey = key.apply(value);
                if (blank(itemKey) || result.put(itemKey, value) != null) {
                    throw failure(Stage03FailureCode.REGISTRY_INVALID);
                }
            }
            return Map.copyOf(result);
        }

        Set<String> knownTerms() {
            return terms.keySet();
        }

        Set<String> knownClaims() {
            return claims.keySet();
        }

        Set<String> knownQuestions() {
            return questions.keySet();
        }

        BusinessTermEntry term(String key) {
            return terms.get(key);
        }

        QuestionEntry question(String key) {
            return questions.get(key);
        }

        TechnicalDisplayPolicy technicalPolicy(String policyKey) {
            List<TechnicalDisplayPolicy> policies = displays.values().stream().flatMap(List::stream)
                    .filter(policy -> policyKey.equals(policy.policyKey())).toList();
            if (policies.size() != 1) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            return policies.get(0);
        }

        ObjectNode admissionInput(CapsuleContext context) {
            ObjectNode admission = JSON.createObjectNode();
            admission.put("registryBundleId", bundle.registryBundleId());
            ObjectNode registries = admission.putObject("registries");
            ObjectNode businessTerms = registryIdentity(registries, "businessTerms", bundle.businessTerms());
            ArrayNode eligibleTerms = businessTerms.putArray("eligibleEntries");
            terms.values().stream().filter(term -> termEligibleForContext(term, context))
                    .sorted(Comparator.comparing(BusinessTermEntry::businessTermKey))
                    .forEach(term -> businessTermBinding(eligibleTerms.addObject(), term));

            ObjectNode claimsRegistry = registryIdentity(registries, "claims", bundle.claims());
            ArrayNode eligibleClaims = claimsRegistry.putArray("eligibleEntries");
            claims.values().stream().filter(claim -> claimEligibleForContext(claim, context))
                    .sorted(Comparator.comparing(ClaimEntry::claimKey))
                    .forEach(claim -> claimBinding(eligibleClaims.addObject(), claim));

            ObjectNode questionRegistry = registryIdentity(registries, "questions", bundle.questions());
            ArrayNode eligibleQuestions = questionRegistry.putArray("eligibleEntries");
            questions.values().stream().filter(question -> context.gapIds().stream()
                    .map(context::gapReason).anyMatch(question.allowedGapReasonCodes()::contains))
                    .sorted(Comparator.comparing(QuestionEntry::questionKey))
                    .forEach(question -> questionBinding(eligibleQuestions.addObject(), question));

            ObjectNode technicalDisplays = registryIdentity(registries, "technicalDisplays",
                    bundle.technicalDisplays());
            ArrayNode technicalBindings = technicalDisplays.putArray("anchorBindings");
            for (Anchor anchor : context.anchorList()) {
                ObjectNode binding = technicalBindings.addObject();
                binding.put("anchorKey", anchor.key());
                technicalDisplayBinding(binding.putObject("policy"), uniqueFallback(anchor));
            }

            ObjectNode templates = registryIdentity(registries, "sentenceTemplates", bundle.sentenceTemplates());
            ArrayNode templateEntries = templates.putArray("entries");
            bundle.sentenceTemplates().templates().stream().sorted(Comparator.comparing(ReaderSentenceTemplate::templateKey))
                    .forEach(template -> templateBinding(templateEntries.addObject(), template));

            ObjectNode ownership = registryIdentity(registries, "sectionOwnership", bundle.sectionOwnership());
            ArrayNode ownershipEntries = ownership.putArray("entries");
            bundle.sectionOwnership().rules().stream().sorted(Comparator.comparing(OwnershipRule::knowledgeKind))
                    .forEach(rule -> ownershipEntries.addObject().put("knowledgeKind", rule.knowledgeKind())
                            .put("ownerSectionKey", rule.ownerSectionKey()));
            return admission;
        }

        private static ObjectNode registryIdentity(ObjectNode parent, String field, BusinessTermRegistry registry) {
            return registryIdentity(parent, field, registry.schemaVersion(), registry.registryId(), registry.sha256());
        }

        private static ObjectNode registryIdentity(ObjectNode parent, String field, ClaimRegistry registry) {
            return registryIdentity(parent, field, registry.schemaVersion(), registry.registryId(), registry.sha256());
        }

        private static ObjectNode registryIdentity(ObjectNode parent, String field, QuestionRegistry registry) {
            return registryIdentity(parent, field, registry.schemaVersion(), registry.registryId(), registry.sha256());
        }

        private static ObjectNode registryIdentity(ObjectNode parent, String field, TechnicalDisplayRegistry registry) {
            return registryIdentity(parent, field, registry.schemaVersion(), registry.registryId(), registry.sha256());
        }

        private static ObjectNode registryIdentity(ObjectNode parent, String field,
                                                   ReaderSentenceTemplateRegistry registry) {
            return registryIdentity(parent, field, registry.schemaVersion(), registry.registryId(), registry.sha256());
        }

        private static ObjectNode registryIdentity(ObjectNode parent, String field, SectionOwnershipRegistry registry) {
            return registryIdentity(parent, field, registry.schemaVersion(), registry.registryId(), registry.sha256());
        }

        private static ObjectNode registryIdentity(ObjectNode parent, String field, String schemaVersion,
                                                   String registryId, String digest) {
            ObjectNode identity = parent.putObject(field);
            identity.put("schemaVersion", schemaVersion);
            identity.put("registryId", registryId);
            identity.put("sha256", digest);
            return identity;
        }

        private static void businessTermBinding(ObjectNode node, BusinessTermEntry term) {
            node.put("businessTermKey", term.businessTermKey());
            node.put("anchorKind", term.anchorKind());
            node.put("localizedValue", term.localizedValue());
            sortedStrings(node.putArray("eligibleAtomKinds"), term.eligibleAtomKinds());
            sortedStrings(node.putArray("minimumBasisAtomIds"), term.minimumBasisAtomIds());
            node.put("priority", term.priority());
            node.put("technicalFallbackPolicyKey", term.technicalFallbackPolicyKey());
        }

        private static void claimBinding(ObjectNode node, ClaimEntry claim) {
            node.put("claimKey", claim.claimKey());
            node.put("targetAnchorKind", claim.targetAnchorKind());
            sortedStrings(node.putArray("requiredAtomPatterns"), claim.requiredAtomPatterns());
            node.put("readerTemplateKey", claim.readerTemplateKey());
        }

        private static void questionBinding(ObjectNode node, QuestionEntry question) {
            node.put("questionKey", question.questionKey());
            sortedStrings(node.putArray("allowedGapReasonCodes"), question.allowedGapReasonCodes());
            node.put("readerTemplateKey", question.readerTemplateKey());
        }

        private static void technicalDisplayBinding(ObjectNode node, TechnicalDisplayPolicy policy) {
            node.put("policyKey", policy.policyKey());
            node.put("anchorKind", policy.anchorKind());
            ArrayNode resolutionOrder = node.putArray("resolutionOrder");
            policy.resolutionOrder().forEach(resolutionOrder::add);
            node.put("displayTemplateKey", policy.displayTemplateKey());
        }

        private static void templateBinding(ObjectNode node, ReaderSentenceTemplate template) {
            node.put("templateKey", template.templateKey());
            node.put("ownerSectionKey", template.ownerSectionKey());
            node.put("literalPattern", template.literalPattern());
            ArrayNode slots = node.putArray("slots");
            template.slots().stream().sorted(Comparator.comparing(TemplateSlotDeclaration::slotKey))
                    .forEach(slot -> slots.addObject().put("slotKey", slot.slotKey())
                            .put("slotKind", slot.slotKind()));
        }

        boolean termSupported(BusinessTermEntry term, Anchor anchor, List<String> retainedAtoms,
                              CapsuleContext context) {
            return term.anchorKind().equals(anchor.kind())
                    && retainedAtoms.containsAll(term.minimumBasisAtomIds())
                    && retainedAtoms.stream().map(context.atoms()::get).allMatch(atom -> atom != null
                    && term.eligibleAtomKinds().contains(atom.role()));
        }

        boolean claimSupported(String claimKey, Anchor anchor, List<String> retainedAtoms, CapsuleContext context) {
            ClaimEntry claim = claims.get(claimKey);
            return claim != null && claim.targetAnchorKind().equals(anchor.kind())
                    && claim.requiredAtomPatterns().stream().allMatch(pattern -> retainedAtoms.stream()
                    .map(context.atoms()::get).filter(Objects::nonNull).anyMatch(atom -> matches(pattern, atom)));
        }

        private static boolean termEligibleForContext(BusinessTermEntry term, CapsuleContext context) {
            return context.anchorList().stream().anyMatch(anchor -> term.anchorKind().equals(anchor.kind())
                    && context.atomIds().containsAll(term.minimumBasisAtomIds())
                    && term.minimumBasisAtomIds().stream().map(context.atoms()::get).allMatch(atom -> atom != null
                    && term.eligibleAtomKinds().contains(atom.role())));
        }

        private boolean claimEligibleForContext(ClaimEntry claim, CapsuleContext context) {
            return context.anchorList().stream().anyMatch(anchor -> claim.targetAnchorKind().equals(anchor.kind())
                    && claim.requiredAtomPatterns().stream().allMatch(pattern -> context.atomIds().stream()
                    .map(context.atoms()::get).filter(Objects::nonNull).anyMatch(atom -> matches(pattern, atom))));
        }

        private static boolean matches(String pattern, AllowedAtomView atom) {
            if (blank(pattern) || atom == null || atom.value() == null) {
                return false;
            }
            String[] pieces = pattern.split(":", -1);
            if (pieces.length < 2 || java.util.Arrays.stream(pieces).anyMatch(Stage03Generator::blank)) {
                return false;
            }
            String type = pieces[0];
            String candidate = (atom.role() + " " + atom.name() + " " + atom.value().canonical())
                    .replaceAll("[\\s#{}]", "").toLowerCase(java.util.Locale.ROOT);
            String expectedRole = switch (type) {
                case "SQL_ASSIGNMENT", "RELATION" -> "RELATIONSHIP";
                case "CONDITION" -> "CONDITION";
                case "THROW" -> "LITERAL";
                default -> "";
            };
            if (expectedRole.isEmpty() || !expectedRole.equals(atom.role())) {
                return false;
            }
            for (int index = 1; index < pieces.length; index++) {
                if (!candidate.contains(pieces[index].replaceAll("[\\s#{}]", "")
                        .toLowerCase(java.util.Locale.ROOT))) {
                    return false;
                }
            }
            return true;
        }

        void requireUniqueFallbacks(Collection<Anchor> anchors) {
            for (Anchor anchor : anchors) {
                uniqueFallback(anchor);
            }
        }

        TechnicalDisplayPolicy uniqueFallback(Anchor anchor) {
            List<TechnicalDisplayPolicy> candidates = displays.getOrDefault(anchor.kind(), List.of());
            if (candidates.size() != 1) {
                throw failure(Stage03FailureCode.TECHNICAL_FALLBACK_NOT_TOTAL);
            }
            TechnicalDisplayPolicy policy = candidates.get(0);
            if (policy.resolutionOrder().stream().noneMatch(anchor.availableSlots()::contains)) {
                throw failure(Stage03FailureCode.REGISTRY_INVALID);
            }
            readerContracts.technicalDisplayTemplate(policy);
            return policy;
        }
    }
}
