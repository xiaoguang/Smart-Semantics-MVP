package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage03.BusinessTermRegistry;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.ExpectedRuntimeIdentity;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.ObservedRuntimeIdentity;
import com.linguan.codemd.stage03.RegistryBundle;
import com.linguan.codemd.stage03.Stage03Generator;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.Stage03Result;
import com.linguan.codemd.stage03.Stage03ScenarioBridge;
import com.linguan.codemd.stage03.Stage03Registries;
import com.linguan.codemd.stage03.StructuredModelProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact D2 Trace references for registry-backed terms, fallbacks, and gaps. */
class Stage04TraceReferenceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private static final CandidateStoreLimits FLOW_GAP_LIMITS = new CandidateStoreLimits(1_000_000, 300_000);

    @Test
    void admittedTermTraceIncludesRegistryIdentityAndPriority() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-trace-term-").install();
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        JsonNode traceRecord = traceRecords(directory).stream()
                .filter(value -> "ADMITTED_TERM".equals(value.path("traceKind").asText()))
                .findFirst().orElseThrow(() -> new AssertionError("fixture must contain an admitted term"));
        String itemKey = traceRecord.path("readerItemKey").asText();
        String meaningId = traceRecord.path("basisMeaningIds").get(0).asText();
        JsonNode meaning = uniqueByField(readJson(directory.resolve("flow-interpretations.json")), "meaningId", meaningId);
        String termKey = meaning.path("businessTermKey").asText();
        JsonNode registry = readJson(directory.resolve("registry-bundle.json")).path("inputRegistryBundle")
                .path("businessTerms");
        JsonNode term = uniqueByField(registry.path("terms"), "businessTermKey", termKey);

        TraceView trace = new CandidateTraceResolver(fixture.archiveWorkspace(), fixture.registry(), LIMITS)
                .trace(new TraceQuery(fixture.candidate().candidateId(), itemKey));
        Set<String> ids = hopIds(trace);
        assertTrue(ids.contains(meaningId));
        assertTrue(ids.contains(termKey));
        assertTrue(ids.contains(registry.path("registryId").asText()),
                "term Trace must bind to the exact frozen business-term registry");
        assertTrue(ids.contains(Integer.toString(term.path("priority").asInt())),
                "term Trace must retain the selected registry priority");
    }

    @Test
    void technicalFallbackTraceIncludesTaskSpecResolutionOrderAndOneTaskAnchor() throws Exception {
        CandidateFixture fixture = fallbackFixture("stage04-trace-fallback-");
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        JsonNode item = planItems(directory).stream()
                .filter(value -> "TECHNICAL_DISPLAY".equals(value.path("itemKind").asText()))
                .findFirst().orElseThrow(() -> new AssertionError("fallback fixture must contain a display item"));
        String itemKey = item.path("readerItemKey").asText();
        String anchorKey = item.path("slots").get(0).path("anchorKey").asText();
        JsonNode resolution = uniqueByField(readJson(directory.resolve("flow-interpretations.json")),
                "anchorKey", anchorKey);
        String policyKey = resolution.path("policyKey").asText();
        JsonNode policy = uniqueByField(readJson(directory.resolve("registry-bundle.json")).path("inputRegistryBundle")
                .path("technicalDisplays").path("policies"), "policyKey", policyKey);
        JsonNode model = modelRounds(directory).stream()
                .filter(value -> hasAnchor(value.path("task").path("inputJson").path("anchors"), anchorKey))
                .findFirst().orElseThrow(() -> new AssertionError("fallback task must contain its anchor"));
        String taskSpecId = model.path("task").path("taskSpecId").asText();

        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(), LIMITS)
                .validate(fixture.candidate());
        assertTrue(validation.valid(), () -> "fallback fixture must validate before Trace: " + validation.checks());
        TraceView trace = new CandidateTraceResolver(fixture.archiveWorkspace(), fixture.registry(), LIMITS)
                .trace(new TraceQuery(fixture.candidate().candidateId(), itemKey));
        Set<String> ids = hopIds(trace);
        assertTrue(ids.contains(anchorKey));
        assertTrue(ids.contains(policyKey));
        assertTrue(ids.contains(taskSpecId), "fallback Trace must point to the unique frozen task specification");
        for (JsonNode order : policy.path("resolutionOrder")) {
            assertTrue(ids.contains(order.asText()),
                    "fallback Trace must retain every frozen resolution-order selector");
        }
        long taskAnchorCount = model.path("task").path("inputJson").path("anchors").findValues("anchorKey").stream()
                .filter(value -> anchorKey.equals(value.asText())).count();
        assertEquals(1L, taskAnchorCount,
                "the selected frozen task must contain exactly one anchor for the fallback");
    }

    @Test
    void gapTraceCarriesStage01QuestionRefsAndConstructibleFlowAndInterpretationProvenance() throws Exception {
        Stage04CandidateFixture.Fixture ordinary = Stage04CandidateFixture.create("stage04-trace-gaps-").install();
        Path ordinaryDirectory = candidateDirectory(ordinary.archiveWorkspace(), ordinary.candidate());
        List<JsonNode> ordinaryTrace = traceRecords(ordinaryDirectory);
        JsonNode gapLedger = readJson(ordinaryDirectory.resolve("gap-ledger.json"));
        JsonNode questionRegistry = readJson(ordinaryDirectory.resolve("registry-bundle.json"))
                .path("inputRegistryBundle").path("questions");
        Set<String> questionKeys = new HashSet<>();
        questionRegistry.path("questions").forEach(value -> questionKeys.add(value.path("questionKey").asText()));
        JsonNode stage01GapItem = null;
        for (JsonNode candidate : ordinaryTrace) {
            if (!"GAP_QUESTION".equals(candidate.path("traceKind").asText())
                    || !candidate.path("basisGapIds").get(0).asText().startsWith("gap:")) {
                continue;
            }
            String candidateGapId = candidate.path("basisGapIds").get(0).asText();
            JsonNode candidateGap = uniqueByField(gapLedger.path("expectationGaps"), "gapId", candidateGapId);
            if (questionKeys.contains(candidateGap.path("questionTemplateKey").asText())) {
                stage01GapItem = candidate;
                break;
            }
        }
        assertNotNull(stage01GapItem, "fixture must contain a Stage01 Gap with a registered question template");
        String stage01GapId = stage01GapItem.path("basisGapIds").get(0).asText();
        JsonNode stage01Gap = uniqueByField(gapLedger.path("expectationGaps"), "gapId", stage01GapId);
        JsonNode question = uniqueByField(questionRegistry.path("questions"), "questionKey",
                stage01Gap.path("questionTemplateKey").asText());
        TraceView stage01Trace = trace(ordinary, stage01GapItem.path("readerItemKey").asText());
        Set<String> stage01Ids = hopIds(stage01Trace);
        assertTrue(stage01Ids.contains(stage01GapId));
        assertTrue(stage01Ids.contains(stage01Gap.path("questionTemplateKey").asText()));
        assertTrue(stage01Ids.contains(questionRegistry.path("registryId").asText()));
        assertTrue(stage01Ids.contains(stage01Gap.path("reasonCode").asText()));
        assertTrue(stage01Ids.contains(question.path("readerTemplateKey").asText()));

        JsonNode interpretationItem = ordinaryTrace.stream()
                .filter(value -> "GAP_QUESTION".equals(value.path("traceKind").asText()))
                .filter(value -> value.path("basisGapIds").get(0).asText().startsWith("interpretation-gap:"))
                .findFirst().orElseThrow(() -> new AssertionError("fixture must contain an InterpretationGap"));
        String interpretationGapId = interpretationItem.path("basisGapIds").get(0).asText();
        JsonNode interpretationGap = flowInterpretations(ordinaryDirectory).stream()
                .flatMap(value -> children(value.path("interpretationGaps")).stream())
                .filter(value -> interpretationGapId.equals(value.path("interpretationGapId").asText()))
                .findFirst().orElseThrow();
        Set<String> interpretationIds = hopIds(trace(ordinary, interpretationItem.path("readerItemKey").asText()));
        assertTrue(interpretationIds.contains(interpretationGapId));
        assertTrue(interpretationIds.contains(interpretationGap.path("sourceGapId").asText()));

        CandidateFixture flowGap = flowGapFixture("stage04-trace-flow-gap-");
        JsonNode flowGapItem = traceRecords(candidateDirectory(flowGap.archiveWorkspace(), flowGap.candidate())).stream()
                .filter(value -> "GAP_QUESTION".equals(value.path("traceKind").asText()))
                .filter(value -> value.path("basisGapIds").get(0).asText().startsWith("flow-gap:"))
                .findFirst().orElseThrow(() -> new AssertionError("real incomplete entry must produce a FlowGap item"));
        String flowGapId = flowGapItem.path("basisGapIds").get(0).asText();
        Set<String> flowGapIds = hopIds(trace(flowGap, flowGapItem.path("readerItemKey").asText()));
        assertTrue(flowGapIds.contains(flowGapId));
        assertTrue(flowGapIds.contains(flowGap.incompleteEntryId()),
                "FlowGap Trace must retain the owning incomplete entry anchor");
    }

    private static TraceView trace(Stage04CandidateFixture.Fixture fixture, String itemKey) {
        return new CandidateTraceResolver(fixture.archiveWorkspace(), fixture.registry(), LIMITS)
                .trace(new TraceQuery(fixture.candidate().candidateId(), itemKey));
    }

    private static TraceView trace(CandidateFixture fixture, String itemKey) {
        return new CandidateTraceResolver(fixture.archiveWorkspace(), fixture.registry(), FLOW_GAP_LIMITS)
                .trace(new TraceQuery(fixture.candidate().candidateId(), itemKey));
    }

    private static CandidateFixture fallbackFixture(String prefix) throws Exception {
        Path workspace = Files.createTempDirectory(prefix + "workspace-");
        Stage03ScenarioBridge.Scenario scenario = Stage03ScenarioBridge.reservation(workspace.resolve("source"));
        RegistryBundle original = scenario.stage03Request().registryBundle();
        BusinessTermRegistry empty = new BusinessTermRegistry(original.businessTerms().schemaVersion(),
                original.businessTerms().registryId() + ":empty-trace", "ignored", List.of());
        RegistryBundle registries = Stage03Registries.freeze(empty, original.technicalDisplays(), original.claims(),
                original.questions(), original.sentenceTemplates(), original.sectionOwnership());
        Stage03Request request = new Stage03Request("stage03-request-v1", scenario.stage02Request(),
                scenario.stage02Result().stage02ResultId(), registries, scenario.stage03Request().interpretationProfileRef(),
                scenario.stage03Request().knowledgeProfileRef(), scenario.stage03Request().nineSectionProfileRef(),
                scenario.stage03Request().modelRuntimePolicy(), scenario.stage03Request().resourceBudget());
        return buildCandidate(workspace, scenario.stage01Request(), scenario.stage01Result(), scenario.stage02Result(),
                request, emptyAdapter("trace-fallback"), "source-registration:" + "b".repeat(64));
    }

    private static CandidateFixture flowGapFixture(String prefix) throws Exception {
        Object scenario = flowGapScenario();
        Stage03Request request = (Stage03Request) accessor(scenario, "request");
        Stage02Request stage02Request = (Stage02Request) accessor(scenario, "stage02Request");
        Stage02Result stage02 = (Stage02Result) accessor(scenario, "stage02");
        String incompleteEntryId = (String) accessor(scenario, "incompleteEntryId");
        Stage01Request stage01Request = stage02Request.stage01Request();
        Stage01Result stage01 = new Stage01Analyzer().analyze(stage01Request);
        Path workspace = stage01Request.frozenRepositoryRequest().snapshotRoot().getParent();
        return buildCandidate(workspace, stage01Request, stage01, stage02, request,
                emptyAdapter("trace-flow-gap"), "source-registration:" + "c".repeat(64), incompleteEntryId);
    }

    private static Object flowGapScenario() throws Exception {
        Class<?> test = Class.forName("com.linguan.codemd.stage03.Stage03FlowGapIsolationTest");
        Method method = test.getDeclaredMethod("scenario");
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static Object accessor(Object value, String name) throws Exception {
        Method method = value.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(value);
    }

    private static CandidateFixture buildCandidate(Path workspace, Stage01Request stage01Request,
                                                   Stage01Result stage01, Stage02Result stage02,
                                                   Stage03Request request, ProviderRuntimeAdapter adapter,
                                                   String registrationId) throws Exception {
        return buildCandidate(workspace, stage01Request, stage01, stage02, request, adapter, registrationId, null);
    }

    private static CandidateFixture buildCandidate(Path workspace, Stage01Request stage01Request,
                                                   Stage01Result stage01, Stage02Result stage02,
                                                   Stage03Request request, ProviderRuntimeAdapter adapter,
                                                   String registrationId, String incompleteEntryId) throws Exception {
        CandidateSeriesRequest series = new CandidateSeriesRequest("candidate-series-request-v1", registrationId,
                "rootless-request:trace-reference", "profile-bundle:java-spring-mybatis-nine-section-v0",
                stage01Request.frozenRepositoryRequest().snapshotRoot());
        Path archive = workspace.resolve("trace-archive");
        CandidateSeriesLedger ledger = new CandidateSeriesLedger(archive);
        RoundSlotView reserved = ledger.reserve(new RoundSlotRequest(series, 1, null, List.of(), null));
        LifecycleProviderBridge bridge = new LifecycleProviderBridge(ledger, reserved,
                new ProviderPolicy("provider-policy:trace-reference"), adapter);
        Stage03Result stage03 = new Stage03Generator().generate(request, bridge);
        Stage03RunTranscript transcript = bridge.seal(stage03);
        CandidateBundle bundle = new CandidateAssembler().assemble(new CandidateAssemblyRequest(series,
                new CandidateLineage(1, null, List.of(), null), bridge.currentSlot(), stage01, stage02,
                request, stage03, transcript));
        CandidateStoreLimits installLimits = incompleteEntryId == null ? LIMITS : FLOW_GAP_LIMITS;
        CandidateReference candidate = new FilesystemCandidateStore(archive, installLimits).install(bundle);
        String snapshotId = stage01.verifiedSnapshot().snapshotId();
        SourceRegistry sourceRegistry = requested -> snapshotId.equals(requested) ? stage01Request : null;
        return new CandidateFixture(workspace, archive, sourceRegistry, candidate, incompleteEntryId);
    }

    private static ProviderRuntimeAdapter emptyAdapter(String label) {
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                int round = task.flowInterpretationRound();
                return new ProviderPreflightReceipt(true, "preflight:" + label + "-" + round,
                        "attempt:" + label + "-" + round);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                int round = task.flowInterpretationRound();
                String started = "upstream-started:" + label + "-" + round;
                sink.onThreadStarted(new ThreadStartedEvent(started));
                ObjectNode response = JSON.createObjectNode()
                        .put("schemaVersion", round == 1 ? "flow-interpretation-r1-v1" : "flow-interpretation-r2-v1")
                        .put("taskSpecId", task.taskSpecId()).put("flowSliceId", task.flowSliceId())
                        .put("evidenceCapsuleId", task.evidenceCapsuleId());
                response.set(round == 1 ? "proposals" : "reviews", JSON.createArrayNode());
                ExpectedRuntimeIdentity expected = task.expectedRuntime();
                ObservedRuntimeIdentity observed = new ObservedRuntimeIdentity(expected.expectedUpstreamProvider(),
                        expected.expectedModel(), expected.expectedReasoningEffort(), expected.expectedSandbox());
                return new ModelExecutionResult(task.taskSpecId(), round,
                        new String(CandidateValidationSupport.canonicalBytes(response), StandardCharsets.UTF_8),
                        observed, started);
            }
        };
    }

    private static List<JsonNode> traceRecords(Path directory) throws Exception {
        List<JsonNode> values = new ArrayList<>();
        for (String line : Files.readString(directory.resolve("trace.jsonl"), StandardCharsets.UTF_8).split("\\n")) {
            if (!line.isBlank()) {
                values.add(JSON.readTree(line));
            }
        }
        return values;
    }

    private static JsonNode readJson(Path path) throws Exception {
        JsonNode value = JSON.readTree(Files.readAllBytes(path));
        if (value == null) {
            throw new AssertionError("missing JSON " + path);
        }
        return value;
    }

    private static JsonNode readObject(Path path) throws Exception {
        return readJson(path);
    }

    private static List<JsonNode> children(JsonNode value) {
        List<JsonNode> result = new ArrayList<>();
        value.elements().forEachRemaining(result::add);
        return result;
    }

    private static List<JsonNode> modelRounds(Path directory) throws Exception {
        return lines(directory.resolve("model-rounds.jsonl"));
    }

    private static List<JsonNode> flowInterpretations(Path directory) throws Exception {
        JsonNode value = JSON.readTree(Files.readAllBytes(directory.resolve("flow-interpretations.json")));
        List<JsonNode> result = new ArrayList<>();
        value.elements().forEachRemaining(result::add);
        return result;
    }

    private static List<JsonNode> lines(Path path) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        for (String line : Files.readString(path, StandardCharsets.UTF_8).split("\\n")) {
            if (!line.isBlank()) {
                result.add(JSON.readTree(line));
            }
        }
        return result;
    }

    private static List<JsonNode> planItems(Path directory) throws Exception {
        JsonNode plan = readObject(directory.resolve("nine-section-plan.json"));
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode section : plan.path("sections")) {
            section.path("items").forEach(result::add);
        }
        return result;
    }

    private static JsonNode uniqueByField(JsonNode values, String field, String expected) {
        List<JsonNode> matches = new ArrayList<>();
        collectByField(values, field, expected, matches);
        assertEquals(1, matches.size(), "expected one " + field + "=" + expected);
        return matches.get(0);
    }

    private static void collectByField(JsonNode value, String field, String expected, List<JsonNode> matches) {
        if (value == null) {
            return;
        }
        if (value.isObject() && expected.equals(value.path(field).asText())) {
            matches.add(value);
        }
        value.elements().forEachRemaining(child -> collectByField(child, field, expected, matches));
    }

    private static Set<String> hopIds(TraceView trace) {
        return trace.hops().stream().flatMap(hop -> Stream.of(hop.sourceId(), hop.targetId()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static boolean hasAnchor(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.path("anchorKey").asText())) {
                return true;
            }
        }
        return false;
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private record CandidateFixture(Path workspace, Path archiveWorkspace, SourceRegistry registry,
                                    CandidateReference candidate, String incompleteEntryId) {
    }
}
