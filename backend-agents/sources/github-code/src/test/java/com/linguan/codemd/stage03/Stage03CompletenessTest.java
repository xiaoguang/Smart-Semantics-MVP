package com.linguan.codemd.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two bounded completeness RED contracts at the public Stage 03 seam.
 * Every assertion uses public request/result records and a scripted provider.
 */
class Stage03CompletenessTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void taskAdmissionContainsChildRegistryDigestsAndEveryTypedBinding() throws Exception {
        Scenario scenario = scenario();
        RegistryBundle bundle = registryWithReaderBindings(scenario.request().registryBundle());
        Stage03Request request = withRegistries(scenario.request(), bundle);
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.validProvider(scenario.stage02());

        new Stage03Generator().generate(request, provider);

        assertTrue(provider.tasks().size() >= 1);
        JsonNode input = JSON.readTree(provider.tasks().get(0).inputJson());
        String inputText = input.toString();

        assertAll(
                () -> assertRegistryIdentityPresent(inputText, bundle.businessTerms().registryId(),
                        bundle.businessTerms().sha256()),
                () -> assertRegistryIdentityPresent(inputText, bundle.technicalDisplays().registryId(),
                        bundle.technicalDisplays().sha256()),
                () -> assertRegistryIdentityPresent(inputText, bundle.claims().registryId(),
                        bundle.claims().sha256()),
                () -> assertRegistryIdentityPresent(inputText, bundle.questions().registryId(),
                        bundle.questions().sha256()),
                () -> assertRegistryIdentityPresent(inputText, bundle.sentenceTemplates().registryId(),
                        bundle.sentenceTemplates().sha256()),
                () -> assertRegistryIdentityPresent(inputText, bundle.sectionOwnership().registryId(),
                        bundle.sectionOwnership().sha256()),
                () -> assertTermBindingPresent(inputText, bundle),
                () -> assertClaimBindingPresent(inputText, bundle),
                () -> assertQuestionBindingPresent(inputText, bundle),
                () -> assertTechnicalPolicyPresent(inputText, bundle),
                () -> assertTemplateBindingPresent(inputText, bundle),
                () -> assertOwnershipBindingPresent(inputText, bundle));
    }

    @Test
    void interpretationAndResultIdentityUseCanonicalRoundReceipts() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-identity-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        RegistryBundle registries = registryWithAlternativeFlowTerm(Stage03Fixtures.registryBundle(stage02));
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(), registries);

        Stage03Result baseline = new Stage03Generator().generate(request,
                Stage03Fixtures.validProvider(stage02));
        UnaryOperator<String> semanticallyEquivalent = response ->
                canonicalObjectOrder(Stage03Fixtures.reverseArray("reviews")
                        .apply(Stage03Fixtures.reverseArray("proposals").apply(response)));
        Stage03Result normalized = new Stage03Generator().generate(request,
                Stage03Fixtures.provider(stage02, semanticallyEquivalent, semanticallyEquivalent,
                        UnaryOperator.identity()));
        Stage03Fixtures.ScriptedModelProvider changedTermProvider = Stage03Fixtures.provider(stage02,
                response -> response.replace("TERM_RESERVATION_FLOW", "TERM_RESERVATION_FLOW_ALIAS"),
                UnaryOperator.identity(), UnaryOperator.identity());
        Stage03Result changedTerm = new Stage03Generator().generate(request, changedTermProvider);

        List<String> baselineReceiptHashes = baseline.flowInterpretations().get(0).roundReceipts().stream()
                .map(ModelRoundReceipt::responseSha256).toList();
        List<String> normalizedReceiptHashes = normalized.flowInterpretations().get(0).roundReceipts().stream()
                .map(ModelRoundReceipt::responseSha256).toList();
        assertAll(
                () -> assertEquals(baselineReceiptHashes, normalizedReceiptHashes,
                        "JSON key/array ordering must not alter canonical round receipts"),
                () -> assertEquals(baseline.flowInterpretations().get(0).flowInterpretationResultId(),
                        normalized.flowInterpretations().get(0).flowInterpretationResultId()),
                () -> assertEquals(baseline.stage03ResultId(), normalized.stage03ResultId()),
                () -> assertNotEquals(baseline.flowInterpretations().get(0).flowInterpretationResultId(),
                        changedTerm.flowInterpretations().get(0).flowInterpretationResultId(),
                        "a valid registry-backed term selection must change interpretation identity"),
                () -> assertNotEquals(baseline.stage03ResultId(), changedTerm.stage03ResultId(),
                        "a changed interpretation must change the Stage 03 result identity"));
    }

    private static Scenario scenario() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-completeness-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02));
        return new Scenario(request, stage02Request, stage02);
    }

    private static Stage03Request withRegistries(Stage03Request original, RegistryBundle registries) {
        return new Stage03Request(original.schemaVersion(), original.stage02Request(), original.expectedStage02ResultId(),
                registries, original.interpretationProfileRef(), original.knowledgeProfileRef(),
                original.nineSectionProfileRef(), original.modelRuntimePolicy(), original.resourceBudget());
    }

    private static RegistryBundle registryWithReaderBindings(RegistryBundle original) {
        List<ReaderSentenceTemplate> templateEntries = List.of(
                new ReaderSentenceTemplate("READER_SCOPE_V1", "section-1",
                        "模板驱动范围：{technical-display}",
                        List.of(new TemplateSlotDeclaration("technical-display", "TECHNICAL_DISPLAY"))),
                new ReaderSentenceTemplate("READER_ATOM_V1", "section-5",
                        "模板驱动原子：{proven-value}",
                        List.of(new TemplateSlotDeclaration("proven-value", "PROVEN_VALUE"))),
                new ReaderSentenceTemplate("READER_TERM_V1", "section-6",
                        "模板驱动术语：{business-term}",
                        List.of(new TemplateSlotDeclaration("business-term", "BUSINESS_TERM"))),
                new ReaderSentenceTemplate("READER_GAP_V1", "section-7",
                        "模板驱动缺口：{bounded-question}",
                        List.of(new TemplateSlotDeclaration("bounded-question", "BOUNDED_QUESTION"))),
                new ReaderSentenceTemplate("READER_OUTCOME_V1", "section-4",
                        "模板驱动终点：{outcome-terminal}；路径语义：{outcome-semantics}",
                        List.of(new TemplateSlotDeclaration("outcome-terminal", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("outcome-semantics", "TECHNICAL_DISPLAY"))),
                new ReaderSentenceTemplate("READER_FIELD_FORMULA_V1", "section-5",
                        "模板驱动字段公式：{formula-role}；值：{formula-value}；运算符：{formula-operators}；操作数：{formula-operands}",
                        List.of(new TemplateSlotDeclaration("formula-role", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-value", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-operators", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-operands", "TECHNICAL_DISPLAY"))),
                new ReaderSentenceTemplate("READER_METRIC_FORMULA_V1", "section-7",
                        "模板驱动指标公式：{formula-role}；值：{formula-value}；运算符：{formula-operators}；操作数：{formula-operands}",
                        List.of(new TemplateSlotDeclaration("formula-role", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-value", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-operators", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-operands", "TECHNICAL_DISPLAY"))),
                new ReaderSentenceTemplate("CLAIM_INCREMENT_FIELD_V1", "section-4",
                        "模板驱动字段声明：{proven-value}",
                        List.of(new TemplateSlotDeclaration("proven-value", "PROVEN_VALUE"))),
                new ReaderSentenceTemplate("CLAIM_CONDITIONAL_THROW_V1", "section-4",
                        "模板驱动条件声明：{proven-value}",
                        List.of(new TemplateSlotDeclaration("proven-value", "PROVEN_VALUE"))),
                new ReaderSentenceTemplate("CLAIM_RELATION_V1", "section-6",
                        "模板驱动关系声明：{proven-value}",
                        List.of(new TemplateSlotDeclaration("proven-value", "PROVEN_VALUE"))),
                new ReaderSentenceTemplate("CLAIM_POLICY_V1", "section-9",
                        "模板驱动策略声明：{bounded-question}",
                        List.of(new TemplateSlotDeclaration("bounded-question", "BOUNDED_QUESTION"))),
                new ReaderSentenceTemplate("QUESTION_CONFIRM_MISSING_ROW_POLICY_V1", "section-9",
                        "模板驱动问题：{bounded-question}",
                        List.of(new TemplateSlotDeclaration("bounded-question", "BOUNDED_QUESTION"))),
                new ReaderSentenceTemplate("TECHNICAL_FLOW_DISPLAY_V1", "section-1",
                        "模板驱动流程：{technical-display}",
                        List.of(new TemplateSlotDeclaration("technical-display", "TECHNICAL_DISPLAY"))),
                new ReaderSentenceTemplate("TECHNICAL_TYPE_DISPLAY_V1", "section-3",
                        "模板驱动类型：{technical-display}",
                        List.of(new TemplateSlotDeclaration("technical-display", "TECHNICAL_DISPLAY"))),
                new ReaderSentenceTemplate("TECHNICAL_RECORD_DISPLAY_V1", "section-3",
                        "模板驱动记录：{technical-display}",
                        List.of(new TemplateSlotDeclaration("technical-display", "TECHNICAL_DISPLAY"))),
                new ReaderSentenceTemplate("TECHNICAL_OUTCOME_DISPLAY_V1", "section-4",
                        "模板驱动终点：{technical-display}",
                        List.of(new TemplateSlotDeclaration("technical-display", "TECHNICAL_DISPLAY"))),
                new ReaderSentenceTemplate("TECHNICAL_ACTIVITY_DISPLAY_V1", "section-4",
                        "模板驱动活动：{technical-display}",
                        List.of(new TemplateSlotDeclaration("technical-display", "TECHNICAL_DISPLAY"))));
        ReaderSentenceTemplateRegistry templates = new ReaderSentenceTemplateRegistry(
                "reader-template-registry-v1", "reader-templates:complete-v1", "unchecked", templateEntries);
        SectionOwnershipRegistry ownership = new SectionOwnershipRegistry("section-ownership-registry-v1",
                "section-ownership:complete-v1", "unchecked",
                List.of(new OwnershipRule("PROVEN_VALUE", "section-5"),
                        new OwnershipRule("BUSINESS_TERM", "section-6"),
                        new OwnershipRule("BOUNDED_QUESTION", "section-7"),
                        new OwnershipRule("TECHNICAL_DISPLAY", "section-1"),
                        new OwnershipRule("OUTCOME", "section-4"),
                        new OwnershipRule("FIELD", "section-5"),
                        new OwnershipRule("METRIC", "section-7")));
        return Stage03Registries.freeze(original.businessTerms(), original.technicalDisplays(), original.claims(),
                original.questions(), templates, ownership);
    }

    private static RegistryBundle registryWithAlternativeFlowTerm(RegistryBundle original) {
        List<BusinessTermEntry> terms = new ArrayList<>(original.businessTerms().terms());
        BusinessTermEntry flow = terms.stream()
                .filter(term -> "TERM_RESERVATION_FLOW".equals(term.businessTermKey())).findFirst().orElseThrow();
        terms.add(new BusinessTermEntry("TERM_RESERVATION_FLOW_ALIAS", flow.anchorKind(), "库存预留别名",
                flow.eligibleAtomKinds(), flow.minimumBasisAtomIds(), flow.priority() + 1,
                flow.technicalFallbackPolicyKey()));
        BusinessTermRegistry changedTerms = new BusinessTermRegistry(original.businessTerms().schemaVersion(),
                original.businessTerms().registryId(), "unchecked", terms);
        return Stage03Registries.freeze(changedTerms, original.technicalDisplays(), original.claims(),
                original.questions(), original.sentenceTemplates(), original.sectionOwnership());
    }

    private static void assertRegistryIdentityPresent(String text, String registryId, String sha256) {
        assertTrue(text.contains(registryId), "task must carry child registry id " + registryId);
        assertTrue(text.contains(sha256), "task must carry child registry digest " + registryId);
    }

    private static void assertTermBindingPresent(String text, RegistryBundle bundle) {
        BusinessTermEntry term = bundle.businessTerms().terms().stream()
                .filter(value -> "TERM_RESERVATION_FLOW".equals(value.businessTermKey())).findFirst().orElseThrow();
        assertTrue(text.contains(term.businessTermKey()));
        term.minimumBasisAtomIds().forEach(atom -> assertTrue(text.contains(atom)));
        term.eligibleAtomKinds().forEach(kind -> assertTrue(text.contains(kind)));
        assertTrue(text.contains(term.technicalFallbackPolicyKey()));
    }

    private static void assertClaimBindingPresent(String text, RegistryBundle bundle) {
        ClaimEntry claim = bundle.claims().claims().stream()
                .filter(value -> "INCREMENT_RESERVED_QUANTITY".equals(value.claimKey())).findFirst().orElseThrow();
        assertTrue(text.contains(claim.claimKey()));
        claim.requiredAtomPatterns().forEach(pattern -> assertTrue(text.contains(pattern)));
        assertTrue(text.contains(claim.readerTemplateKey()));
    }

    private static void assertQuestionBindingPresent(String text, RegistryBundle bundle) {
        QuestionEntry question = bundle.questions().questions().get(0);
        assertTrue(text.contains(question.questionKey()));
        question.allowedGapReasonCodes().forEach(reason -> assertTrue(text.contains(reason)));
        assertTrue(text.contains(question.readerTemplateKey()));
    }

    private static void assertTechnicalPolicyPresent(String text, RegistryBundle bundle) {
        TechnicalDisplayPolicy policy = bundle.technicalDisplays().policies().stream()
                .filter(value -> "FLOW_ROUTE_HANDLER_V1".equals(value.policyKey())).findFirst().orElseThrow();
        assertTrue(text.contains(policy.policyKey()));
        policy.resolutionOrder().forEach(slot -> assertTrue(text.contains(slot)));
        assertTrue(text.contains(policy.displayTemplateKey()));
    }

    private static void assertTemplateBindingPresent(String text, RegistryBundle bundle) {
        ReaderSentenceTemplate template = bundle.sentenceTemplates().templates().get(0);
        assertTrue(text.contains(template.templateKey()));
        assertTrue(text.contains(template.ownerSectionKey()));
        assertTrue(text.contains(template.literalPattern()));
        template.slots().forEach(slot -> {
            assertTrue(text.contains(slot.slotKey()));
            assertTrue(text.contains(slot.slotKind()));
        });
    }

    private static void assertOwnershipBindingPresent(String text, RegistryBundle bundle) {
        OwnershipRule rule = bundle.sectionOwnership().rules().get(0);
        assertTrue(text.contains(rule.knowledgeKind()));
        assertTrue(text.contains(rule.ownerSectionKey()));
    }

    private static String canonicalObjectOrder(String response) {
        try {
            return JSON.writeValueAsString(canonical(JSON.readTree(response)));
        } catch (Exception invalidResponse) {
            throw new AssertionError("scripted response is not JSON", invalidResponse);
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
            List<JsonNode> children = new ArrayList<>();
            for (JsonNode child : node) {
                children.add(canonical(child));
            }
            children.sort(Comparator.comparing(JsonNode::toString));
            children.forEach(values::add);
            return values;
        }
        return node;
    }

    private record Scenario(Stage03Request request, Stage02Request stage02Request, Stage02Result stage02) {
    }
}
