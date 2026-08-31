package com.linguan.codemd.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public-seam RED contracts for complete task admission and path cleanliness. */
class Stage03TaskBodyTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void taskInputExposesAllProfilesAndResourceBudgetFieldsExactly() throws Exception {
        Scenario scenario = scenario();
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.validProvider(scenario.stage02());

        new Stage03Generator().generate(scenario.request(), provider);

        JsonNode input = JSON.readTree(provider.tasks().get(0).inputJson());
        JsonNode profiles = input.path("profiles");
        JsonNode budget = input.path("resourceBudget");
        Stage03ResourceBudget expected = scenario.request().resourceBudget();
        assertAll(
                () -> assertEquals(scenario.request().knowledgeProfileRef().profileId(),
                        profiles.path("knowledgeProfileId").asText()),
                () -> assertEquals(scenario.request().nineSectionProfileRef().profileId(),
                        profiles.path("nineSectionProfileId").asText()),
                () -> assertEquals(expected.maxFlowInterpretations(), budget.path("maxFlowInterpretations").asInt()),
                () -> assertEquals(expected.roundsPerCapsule(), budget.path("roundsPerCapsule").asInt()),
                () -> assertEquals(expected.maxTaskInputBytes(), budget.path("maxTaskInputBytes").asInt()),
                () -> assertEquals(expected.maxResponseBytes(), budget.path("maxResponseBytes").asInt()),
                () -> assertEquals(expected.requiredSectionCount(), budget.path("requiredSectionCount").asInt()),
                () -> assertEquals(expected.maxDocumentBytes(), budget.path("maxDocumentBytes").asInt()),
                () -> assertEquals(expected.maxReaderItems(), budget.path("maxReaderItems").asInt()));
    }

    @ParameterizedTest(name = "unsafe {0} value is rejected before provider execution")
    @MethodSource("unsafeRegistryValues")
    void arbitraryRegistryPathsFailBeforeProvider(String field, String value) throws Exception {
        Scenario scenario = scenario();
        RegistryBundle unsafe = registryWithPath(scenario.request().registryBundle(), field, value);
        Stage03Request request = withRegistries(scenario.request(), unsafe);
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.validProvider(scenario.stage02());

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(request, provider));

        assertAll(
                () -> assertTrue(failure.code() == Stage03FailureCode.REGISTRY_INVALID
                                || failure.code() == Stage03FailureCode.READER_INVARIANT_BROKEN,
                        "path-containing registry value must fail closed: " + failure.code()),
                () -> assertTrue(provider.tasks().isEmpty(),
                        "path safety must be checked before any Provider task"));
    }

    private static Stream<Arguments> unsafeRegistryValues() {
        List<String> values = List.of("/opt/customer/note.txt", "C:\\customer\\note.txt",
                "\\\\server\\share\\note.txt", "config/customer/note.txt");
        return values.stream().flatMap(value -> Stream.of(
                Arguments.of("localizedValue", value),
                Arguments.of("templateLiteral", value),
                Arguments.of("questionTemplateKey", value)));
    }

    private static RegistryBundle registryWithPath(RegistryBundle original, String field, String value) {
        List<BusinessTermEntry> terms = new ArrayList<>(original.businessTerms().terms());
        if ("localizedValue".equals(field)) {
            BusinessTermEntry flow = terms.stream()
                    .filter(term -> "TERM_RESERVATION_FLOW".equals(term.businessTermKey())).findFirst().orElseThrow();
            terms.replaceAll(term -> term.businessTermKey().equals(flow.businessTermKey())
                    ? new BusinessTermEntry(term.businessTermKey(), term.anchorKind(), value,
                    term.eligibleAtomKinds(), term.minimumBasisAtomIds(), term.priority(),
                    term.technicalFallbackPolicyKey()) : term);
        }
        BusinessTermRegistry businessTerms = new BusinessTermRegistry(original.businessTerms().schemaVersion(),
                original.businessTerms().registryId(), "unchecked", terms);

        List<QuestionEntry> questions = new ArrayList<>(original.questions().questions());
        if ("questionTemplateKey".equals(field)) {
            QuestionEntry question = questions.get(0);
            questions.set(0, new QuestionEntry(question.questionKey(), question.allowedGapReasonCodes(), value));
        }
        QuestionRegistry questionRegistry = new QuestionRegistry(original.questions().schemaVersion(),
                original.questions().registryId(), "unchecked", questions);

        List<ReaderSentenceTemplate> templates = List.of(
                new ReaderSentenceTemplate("READER_ATOM_V1", "section-4",
                        "原子：" + ("templateLiteral".equals(field) ? value + "：" : "") + "{proven-value}",
                        List.of(new TemplateSlotDeclaration("proven-value", "PROVEN_VALUE"))),
                new ReaderSentenceTemplate("READER_TERM_V1", "section-2", "术语：{business-term}",
                        List.of(new TemplateSlotDeclaration("business-term", "BUSINESS_TERM"))),
                new ReaderSentenceTemplate("READER_GAP_V1", "section-9", "缺口：{bounded-question}",
                        List.of(new TemplateSlotDeclaration("bounded-question", "BOUNDED_QUESTION"))),
                new ReaderSentenceTemplate("READER_OUTCOME_V1", "section-4",
                        "终点：{outcome-terminal}；分支：{outcome-semantics}",
                        List.of(new TemplateSlotDeclaration("outcome-terminal", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("outcome-semantics", "TECHNICAL_DISPLAY"))),
                formulaTemplate("READER_FIELD_FORMULA_V1"), formulaTemplate("READER_METRIC_FORMULA_V1"));
        ReaderSentenceTemplateRegistry sentenceTemplates = new ReaderSentenceTemplateRegistry(
                original.sentenceTemplates().schemaVersion(), original.sentenceTemplates().registryId(), "unchecked",
                templates);
        SectionOwnershipRegistry ownership = new SectionOwnershipRegistry(
                original.sectionOwnership().schemaVersion(), original.sectionOwnership().registryId(), "unchecked",
                List.of(new OwnershipRule("PROVEN_VALUE", "section-4"),
                        new OwnershipRule("BUSINESS_TERM", "section-2"),
                        new OwnershipRule("BOUNDED_QUESTION", "section-9"),
                        new OwnershipRule("TECHNICAL_DISPLAY", "section-1"),
                        new OwnershipRule("OUTCOME", "section-4"),
                        new OwnershipRule("FIELD", "section-5"),
                        new OwnershipRule("METRIC", "section-7")));
        return Stage03Registries.freeze(businessTerms, original.technicalDisplays(), original.claims(),
                questionRegistry, sentenceTemplates, ownership);
    }

    private static ReaderSentenceTemplate formulaTemplate(String key) {
        return new ReaderSentenceTemplate(key, key.contains("FIELD") ? "section-5" : "section-7",
                "公式：{formula-role}；值：{formula-value}；运算符：{formula-operators}；操作数：{formula-operands}",
                List.of(new TemplateSlotDeclaration("formula-role", "TECHNICAL_DISPLAY"),
                        new TemplateSlotDeclaration("formula-value", "TECHNICAL_DISPLAY"),
                        new TemplateSlotDeclaration("formula-operators", "TECHNICAL_DISPLAY"),
                        new TemplateSlotDeclaration("formula-operands", "TECHNICAL_DISPLAY")));
    }

    private static Stage03Request withRegistries(Stage03Request original, RegistryBundle registries) {
        return new Stage03Request(original.schemaVersion(), original.stage02Request(), original.expectedStage02ResultId(),
                registries, original.interpretationProfileRef(), original.knowledgeProfileRef(),
                original.nineSectionProfileRef(), original.modelRuntimePolicy(), original.resourceBudget());
    }

    private static Scenario scenario() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-task-body-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        return new Scenario(Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02)), stage02);
    }

    private record Scenario(Stage03Request request, Stage02Result stage02) {
    }
}
