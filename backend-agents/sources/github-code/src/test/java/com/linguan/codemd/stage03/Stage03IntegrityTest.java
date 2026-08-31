package com.linguan.codemd.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public-seam integrity RED contracts for the typed M5--M7 output.
 *
 * <p>These tests deliberately exercise only the scripted provider and
 * {@link Stage03Generator#generate(Stage03Request, StructuredModelProvider)}.
 * The mutations retain the public request shape and do not reach private
 * parser, registry, or rendering helpers.</p>
 */
class Stage03IntegrityTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void readerItemsOwnEveryBodyDispositionExactlyOnceAndMarkdownUsesTypedItems() throws Exception {
        Scenario scenario = scenario();
        Stage03Result result = generate(scenario, Stage03Fixtures.validProvider(scenario.stage02()));
        NineSectionPlan plan = result.nineSectionPlan();
        List<ReaderItem> items = plan.sections().stream().flatMap(section -> section.items().stream()).toList();

        Set<String> bodyAtoms = plan.atomDispositions().stream()
                .filter(disposition -> "READER_BODY".equals(disposition.disposition()))
                .map(AtomDisposition::atomId).collect(Collectors.toSet());
        Set<String> bodyMeanings = plan.meaningDispositions().stream()
                .filter(disposition -> "READER_BODY".equals(disposition.disposition()))
                .map(MeaningDisposition::meaningId).collect(Collectors.toSet());
        Set<String> bodyGaps = plan.gapDispositions().stream()
                .filter(disposition -> "READER_BODY".equals(disposition.disposition()))
                .map(GapDisposition::gapId).collect(Collectors.toSet());

        assertEquals(bodyAtoms, basisAtoms(items), "every reader-body atom needs one typed owner");
        assertEquals(bodyMeanings, basisMeanings(items), "every reader-body meaning needs one typed owner");
        assertEquals(bodyGaps, basisGaps(items), "every reader-body gap needs one typed owner");
        assertTrue(basisAtomCounts(items).values().stream().allMatch(count -> count == 1L),
                "each atom must have exactly one ReaderItem owner");
        assertTrue(basisMeaningCounts(items).values().stream().allMatch(count -> count == 1L),
                "each meaning must have exactly one ReaderItem owner");
        assertTrue(basisGapCounts(items).values().stream().allMatch(count -> count == 1L),
                "each gap must have exactly one ReaderItem owner");
        assertTrue(items.stream().anyMatch(item -> !item.slots().isEmpty()),
                "typed reader items must carry renderable slots");
        assertTrue(items.stream().allMatch(item -> item.slots().stream().allMatch(slot ->
                        result.renderedDocument().markdown().contains(slotValue(slot)))),
                "Markdown must be behaviorally coupled to typed ReaderItem slots");
    }

    @Test
    void sameRegistryIdsAndDigestsWithMutatedBusinessTermContentFailClosed() throws Exception {
        Scenario scenario = scenario();
        BusinessTermRegistry original = scenario.request().registryBundle().businessTerms();
        BusinessTermEntry first = original.terms().get(0);
        List<BusinessTermEntry> terms = new ArrayList<>(original.terms());
        terms.set(0, new BusinessTermEntry(first.businessTermKey(), first.anchorKind(),
                first.localizedValue() + "（篡改）", first.eligibleAtomKinds(), first.minimumBasisAtomIds(),
                first.priority(), first.technicalFallbackPolicyKey()));
        RegistryBundle mutated = withBusinessTerms(scenario.request().registryBundle(),
                new BusinessTermRegistry(original.schemaVersion(), original.registryId(), original.sha256(), terms));

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(withRegistries(scenario.request(), mutated),
                        Stage03Fixtures.validProvider(scenario.stage02())));

        assertEquals(Stage03FailureCode.REGISTRY_INVALID, failure.code());
    }

    @Test
    void malformedRegistryEntryIsNormalizedToStableRegistryInvalidNotRawNpe() throws Exception {
        Scenario scenario = scenario();
        BusinessTermRegistry original = scenario.request().registryBundle().businessTerms();
        BusinessTermEntry first = original.terms().get(0);
        List<BusinessTermEntry> terms = new ArrayList<>(original.terms());
        terms.set(0, new BusinessTermEntry(first.businessTermKey(), first.anchorKind(), null,
                first.eligibleAtomKinds(), first.minimumBasisAtomIds(), first.priority(),
                first.technicalFallbackPolicyKey()));
        RegistryBundle mutated = withBusinessTerms(scenario.request().registryBundle(),
                new BusinessTermRegistry(original.schemaVersion(), original.registryId(), original.sha256(), terms));

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(withRegistries(scenario.request(), mutated),
                        Stage03Fixtures.validProvider(scenario.stage02())));

        assertEquals(Stage03FailureCode.REGISTRY_INVALID, failure.code());
    }

    @Test
    void taskInputContainsOnlyNormalizedCapsuleDataAndFiniteRegistryAdmissionKeys() throws Exception {
        Scenario scenario = scenario();
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.validProvider(scenario.stage02());
        generate(scenario, provider);
        assertEquals(2, provider.tasks().size());

        FlowModelTask task = provider.tasks().get(0);
        JsonNode input = JSON.readTree(task.inputJson());
        assertNotNull(input);
        assertEquals("flow-task-input-v1", input.path("schemaVersion").asText());
        assertEquals(task.flowSliceId(), input.path("flowSliceId").asText());
        assertEquals(task.evidenceCapsuleId(), input.path("evidenceCapsuleId").asText());

        var flow = scenario.stage02().flowSlices().stream()
                .filter(candidate -> candidate.flowSliceId().equals(task.flowSliceId())).findFirst().orElseThrow();
        var capsule = scenario.stage02().evidenceCapsules().stream()
                .filter(candidate -> candidate.evidenceCapsuleId().equals(task.evidenceCapsuleId()))
                .findFirst().orElseThrow();
        assertTrue(task.inputJson().contains(flow.entryId()), "normalized Flow entry must be task-visible");
        assertTrue(task.inputJson().contains(flow.rootNodeId()), "normalized Flow root must be task-visible");
        assertTrue(task.inputJson().contains(flow.trigger().route()), "normalized Flow trigger must be task-visible");
        capsule.allowedFacts().forEach(fact -> {
            assertTrue(task.inputJson().contains(fact.factId()), "task must include fact " + fact.factId());
            fact.atoms().forEach(atom -> assertTrue(task.inputJson().contains(atom.atomId()),
                    "task must include atom " + atom.atomId()));
        });
        capsule.outcomePathIds().forEach(outcome -> assertTrue(task.inputJson().contains(outcome),
                "task must include outcome " + outcome));
        capsule.allowedGaps().forEach(gap -> assertTrue(task.inputJson().contains(gap.gapId()),
                "task must include gap " + gap.gapId()));
        capsule.modelEvidenceSpans().forEach(span -> assertTrue(task.inputJson().contains(span.modelEvidenceSpanId()),
                "task must include evidence span " + span.modelEvidenceSpanId()));

        assertTrue(task.inputJson().contains("TERM_RESERVATION_FLOW"));
        assertTrue(task.inputJson().contains("INCREMENT_RESERVED_QUANTITY"));
        assertTrue(task.inputJson().contains("ASK_MISSING_ROW_POLICY"));
        assertTrue(task.inputJson().contains("TECHNICAL_FLOW_DISPLAY_V1"));
        assertFalse(task.inputJson().contains("TERM_UNKNOWN"));
        assertFalse(task.inputJson().contains(flow.entryId() + "-other"));
    }

    @Test
    void termEligibilityAndClaimRequiredPatternsCannotBeBypassedByKnownKeys() throws Exception {
        Scenario scenario = scenario();
        BusinessTermRegistry terms = scenario.request().registryBundle().businessTerms();
        BusinessTermEntry first = terms.terms().get(0);
        List<BusinessTermEntry> changedTerms = new ArrayList<>(terms.terms());
        changedTerms.set(0, new BusinessTermEntry(first.businessTermKey(), first.anchorKind(), first.localizedValue(),
                List.of("UNRELATED_ATOM_KIND"), first.minimumBasisAtomIds(), first.priority(),
                first.technicalFallbackPolicyKey()));
        RegistryBundle termMutation = withBusinessTerms(scenario.request().registryBundle(),
                new BusinessTermRegistry(terms.schemaVersion(), terms.registryId(), terms.sha256(), changedTerms));
        Stage03Exception termFailure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(withRegistries(scenario.request(), termMutation),
                        Stage03Fixtures.validProvider(scenario.stage02())));
        assertEquals(Stage03FailureCode.REGISTRY_INVALID, termFailure.code());

        ClaimRegistry claims = scenario.request().registryBundle().claims();
        ClaimEntry firstClaim = claims.claims().get(0);
        List<ClaimEntry> changedClaims = new ArrayList<>(claims.claims());
        changedClaims.set(0, new ClaimEntry(firstClaim.claimKey(), firstClaim.targetAnchorKind(),
                List.of("UNPROVEN_REQUIRED_PATTERN"), firstClaim.readerTemplateKey()));
        RegistryBundle claimMutation = withClaims(scenario.request().registryBundle(),
                new ClaimRegistry(claims.schemaVersion(), claims.registryId(), claims.sha256(), changedClaims));
        Stage03Exception claimFailure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(withRegistries(scenario.request(), claimMutation),
                        Stage03Fixtures.validProvider(scenario.stage02())));
        assertEquals(Stage03FailureCode.REGISTRY_INVALID, claimFailure.code());
    }

    @Test
    void fallbackResolutionOrderAndTemplateAreEnforcedInsteadOfIgnored() throws Exception {
        Scenario scenario = scenario();
        TechnicalDisplayRegistry original = scenario.request().registryBundle().technicalDisplays();
        TechnicalDisplayPolicy first = original.policies().get(0);
        List<TechnicalDisplayPolicy> policies = new ArrayList<>(original.policies());
        policies.set(0, new TechnicalDisplayPolicy(first.policyKey(), first.anchorKind(),
                List.of("UNSUPPORTED_PROVEN_SLOT"), "UNSUPPORTED_DISPLAY_TEMPLATE"));
        RegistryBundle mutated = withTechnicalDisplays(scenario.request().registryBundle(),
                new TechnicalDisplayRegistry(original.schemaVersion(), original.registryId(), original.sha256(), policies));

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(withRegistries(scenario.request(), mutated),
                        Stage03Fixtures.validProvider(scenario.stage02())));
        assertEquals(Stage03FailureCode.REGISTRY_INVALID, failure.code());
    }

    @Test
    void r2CannotReplaceAQuestionGapWithAnotherOrSilentlyCloseIt() throws Exception {
        Scenario scenario = scenario();
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.provider(scenario.stage02(),
                UnaryOperator.identity(), Stage03IntegrityTest::replaceP09GapWithUnknown,
                UnaryOperator.identity());

        Stage03Exception failure = assertThrows(Stage03Exception.class, () -> generate(scenario, provider));
        assertEquals(Stage03FailureCode.MODEL_REVIEW_NOT_CLOSED, failure.code());
        assertEquals(List.of(1, 2), provider.tasks().stream()
                .map(FlowModelTask::flowInterpretationRound).toList());
    }

    @Test
    void emptyR1RequiresAnEmptyR2InsteadOfDiscardingUnexpectedReviews() throws Exception {
        Scenario scenario = scenario();
        Stage03Fixtures.ScriptedModelProvider provider =
                Stage03Fixtures.emptySelectionProvider(scenario.stage02(), UnaryOperator.identity());

        Stage03Exception failure = assertThrows(Stage03Exception.class, () -> generate(scenario, provider));
        assertEquals(Stage03FailureCode.MODEL_REVIEW_NOT_CLOSED, failure.code());
        assertEquals(List.of(1, 2), provider.tasks().stream()
                .map(FlowModelTask::flowInterpretationRound).toList());
    }

    @Test
    void maxReaderItemsIsAnEnforcedOutputBudget() throws Exception {
        Scenario scenario = scenario();
        Stage03ResourceBudget old = scenario.request().resourceBudget();
        Stage03Request constrained = withBudget(scenario.request(), new Stage03ResourceBudget(
                old.maxFlowInterpretations(), old.roundsPerCapsule(), old.maxTaskInputBytes(), old.maxResponseBytes(),
                old.requiredSectionCount(), old.maxDocumentBytes(), 1));

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(constrained, Stage03Fixtures.validProvider(scenario.stage02())));
        assertEquals(Stage03FailureCode.RESOURCE_LIMIT_EXCEEDED, failure.code());
    }

    @Test
    void providerFailureIsNormalizedToStableStage03Exception() throws Exception {
        Scenario scenario = scenario();
        StructuredModelProvider throwingProvider = task -> {
            throw new IllegalStateException("provider transport failed");
        };

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(scenario.request(), throwingProvider));
        assertNotNull(failure.code());
    }

    private static Scenario scenario() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-integrity-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02));
        return new Scenario(request, stage02Request, stage02);
    }

    private static Stage03Result generate(Scenario scenario, StructuredModelProvider provider) {
        return new Stage03Generator().generate(scenario.request(), provider);
    }

    private static RegistryBundle withBusinessTerms(RegistryBundle original, BusinessTermRegistry businessTerms) {
        return new RegistryBundle(original.registryBundleId(), businessTerms, original.technicalDisplays(),
                original.claims(), original.questions(), original.sentenceTemplates(), original.sectionOwnership());
    }

    private static RegistryBundle withClaims(RegistryBundle original, ClaimRegistry claims) {
        return new RegistryBundle(original.registryBundleId(), original.businessTerms(), original.technicalDisplays(),
                claims, original.questions(), original.sentenceTemplates(), original.sectionOwnership());
    }

    private static RegistryBundle withTechnicalDisplays(RegistryBundle original,
                                                         TechnicalDisplayRegistry technicalDisplays) {
        return new RegistryBundle(original.registryBundleId(), original.businessTerms(), technicalDisplays,
                original.claims(), original.questions(), original.sentenceTemplates(), original.sectionOwnership());
    }

    private static Stage03Request withRegistries(Stage03Request original, RegistryBundle registries) {
        return new Stage03Request(original.schemaVersion(), original.stage02Request(), original.expectedStage02ResultId(),
                registries, original.interpretationProfileRef(), original.knowledgeProfileRef(),
                original.nineSectionProfileRef(), original.modelRuntimePolicy(), original.resourceBudget());
    }

    private static Stage03Request withBudget(Stage03Request original, Stage03ResourceBudget budget) {
        return new Stage03Request(original.schemaVersion(), original.stage02Request(), original.expectedStage02ResultId(),
                original.registryBundle(), original.interpretationProfileRef(), original.knowledgeProfileRef(),
                original.nineSectionProfileRef(), original.modelRuntimePolicy(), budget);
    }

    private static String replaceP09GapWithUnknown(String response) {
        try {
            ObjectNode root = (ObjectNode) JSON.readTree(response);
            for (JsonNode node : root.withArray("reviews")) {
                if ("P09".equals(node.path("proposalKey").asText())) {
                    ((ObjectNode) node).set("retainedBasisGapIds", JSON.createArrayNode().add("gap:substituted"));
                }
            }
            return JSON.writeValueAsString(root);
        } catch (Exception invalidResponse) {
            throw new AssertionError("scripted response is not JSON", invalidResponse);
        }
    }

    private static Set<String> basisAtoms(List<ReaderItem> items) {
        return items.stream().flatMap(item -> item.basisAtomIds().stream()).collect(Collectors.toSet());
    }

    private static java.util.Map<String, Long> basisAtomCounts(List<ReaderItem> items) {
        return items.stream().flatMap(item -> item.basisAtomIds().stream())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
    }

    private static Set<String> basisMeanings(List<ReaderItem> items) {
        return items.stream().flatMap(item -> item.basisMeaningIds().stream()).collect(Collectors.toSet());
    }

    private static java.util.Map<String, Long> basisMeaningCounts(List<ReaderItem> items) {
        return items.stream().flatMap(item -> item.basisMeaningIds().stream())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
    }

    private static Set<String> basisGaps(List<ReaderItem> items) {
        return items.stream().flatMap(item -> item.basisGapIds().stream()).collect(Collectors.toSet());
    }

    private static java.util.Map<String, Long> basisGapCounts(List<ReaderItem> items) {
        return items.stream().flatMap(item -> item.basisGapIds().stream())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
    }

    private static String slotValue(ReaderSlot slot) {
        if (slot instanceof ProvenValueSlot value) {
            return value.value();
        }
        if (slot instanceof BusinessTermSlot value) {
            return value.value();
        }
        if (slot instanceof TechnicalDisplaySlot value) {
            return value.value();
        }
        if (slot instanceof BoundedQuestionSlot value) {
            return value.value();
        }
        throw new AssertionError("unrecognized public ReaderSlot " + slot.getClass());
    }

    private record Scenario(Stage03Request request, Stage02Request stage02Request, Stage02Result stage02) {
    }
}
