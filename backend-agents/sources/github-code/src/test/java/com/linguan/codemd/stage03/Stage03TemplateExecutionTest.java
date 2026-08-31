package com.linguan.codemd.stage03;

import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public-seam RED contract for executable technical display templates. */
class Stage03TemplateExecutionTest {
    private static final String CUSTOM_TEMPLATE_KEY = "TECHNICAL_FLOW_CUSTOM_V1";
    private static final String CUSTOM_MARKER = "自定义技术流程：";
    private static final String MISSING_TEMPLATE_KEY = "TECHNICAL_FLOW_MISSING_V1";

    @Test
    void frozenTechnicalDisplayTemplateControlsResolutionReaderItemsAndMarkdown() throws Exception {
        Scenario scenario = scenario();
        RegistryBundle registries = registriesWithFlowTemplate(scenario.request().registryBundle(),
                CUSTOM_TEMPLATE_KEY, true);
        Stage03Request request = withRegistries(scenario.request(), registries);
        Stage03Fixtures.ScriptedModelProvider provider =
                Stage03Fixtures.emptySelectionProvider(scenario.stage02());

        Stage03Result result = new Stage03Generator().generate(request, provider);
        ReaderSentenceTemplate template = registries.sentenceTemplates().templates().stream()
                .filter(candidate -> CUSTOM_TEMPLATE_KEY.equals(candidate.templateKey())).findFirst().orElseThrow();
        TechnicalDisplayResolution flowDisplay = result.flowInterpretations().stream()
                .flatMap(interpretation -> interpretation.technicalFallbacks().stream())
                .filter(display -> "FLOW".equals(display.anchorKind())).findFirst().orElseThrow();
        List<ReaderItem> items = result.nineSectionPlan().sections().stream()
                .flatMap(section -> section.items().stream()).toList();
        String markdown = result.renderedDocument().markdown();

        assertAll(
                () -> assertEquals(List.of(new TemplateSlotDeclaration("technical-display", "TECHNICAL_DISPLAY")),
                        template.slots(), "the custom template must expose its finite typed slot"),
                () -> assertTrue(flowDisplay.resolvedDisplay().contains(CUSTOM_MARKER),
                        "technical resolution must execute the declared template"),
                () -> assertTrue(result.repositoryBusinessModel().flows().stream()
                                .anyMatch(flow -> flow.display().contains(CUSTOM_MARKER)),
                        "the resolved technical display must reach the typed business model"),
                () -> assertTrue(items.stream().flatMap(item -> item.slots().stream())
                                .map(Object::toString).anyMatch(value -> value.contains(CUSTOM_MARKER)),
                        "a typed ReaderItem must carry the executed technical display"),
                () -> assertTrue(markdown.contains(CUSTOM_MARKER),
                        "Markdown must be rendered from the executed technical display item"),
                () -> assertFalse(markdown.contains(CUSTOM_TEMPLATE_KEY),
                        "template keys must not leak into reader prose"),
                () -> assertFalse(markdown.contains(flowDisplay.anchorKey()),
                        "anchor IDs must not leak into reader prose"));
    }

    @Test
    void missingTechnicalDisplayTemplateFailsBeforeProviderExecution() throws Exception {
        Scenario scenario = scenario();
        RegistryBundle registries = registriesWithFlowTemplate(scenario.request().registryBundle(),
                MISSING_TEMPLATE_KEY, false);
        Stage03Fixtures.ScriptedModelProvider provider =
                Stage03Fixtures.emptySelectionProvider(scenario.stage02());

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(withRegistries(scenario.request(), registries), provider));

        assertAll(
                () -> assertEquals(Stage03FailureCode.REGISTRY_INVALID, failure.code()),
                () -> assertTrue(provider.tasks().isEmpty(),
                        "a missing technical template must fail before any Provider task"));
    }

    private static RegistryBundle registriesWithFlowTemplate(RegistryBundle original, String displayTemplateKey,
                                                               boolean includeTemplate) {
        List<TechnicalDisplayPolicy> policies = original.technicalDisplays().policies().stream()
                .map(policy -> "FLOW".equals(policy.anchorKind())
                        ? new TechnicalDisplayPolicy(policy.policyKey(), policy.anchorKind(), policy.resolutionOrder(),
                        displayTemplateKey)
                        : policy)
                .toList();
        TechnicalDisplayRegistry technicalDisplays = new TechnicalDisplayRegistry(
                original.technicalDisplays().schemaVersion(), original.technicalDisplays().registryId(), "unchecked",
                policies);

        List<ReaderSentenceTemplate> templates = new ArrayList<>(List.of(
                template("READER_ATOM_V1", "section-4", "原子：{proven-value}", "proven-value", "PROVEN_VALUE"),
                template("READER_TERM_V1", "section-2", "术语：{business-term}", "business-term", "BUSINESS_TERM"),
                template("READER_GAP_V1", "section-9", "缺口：{bounded-question}", "bounded-question",
                        "BOUNDED_QUESTION"),
                new ReaderSentenceTemplate("READER_OUTCOME_V1", "section-4",
                        "终点：{outcome-terminal}；分支：{outcome-semantics}", List.of(
                        new TemplateSlotDeclaration("outcome-terminal", "TECHNICAL_DISPLAY"),
                        new TemplateSlotDeclaration("outcome-semantics", "TECHNICAL_DISPLAY"))),
                formulaTemplate("READER_FIELD_FORMULA_V1", "section-5"),
                formulaTemplate("READER_METRIC_FORMULA_V1", "section-7")));
        if (includeTemplate) {
            templates.add(new ReaderSentenceTemplate(CUSTOM_TEMPLATE_KEY, "section-1",
                    CUSTOM_MARKER + "{technical-display}",
                    List.of(new TemplateSlotDeclaration("technical-display", "TECHNICAL_DISPLAY"))));
        }
        ReaderSentenceTemplateRegistry sentenceTemplates = new ReaderSentenceTemplateRegistry(
                original.sentenceTemplates().schemaVersion(), "reader-templates:technical-execution-v1", "unchecked",
                templates);
        SectionOwnershipRegistry ownership = new SectionOwnershipRegistry(
                original.sectionOwnership().schemaVersion(), "section-ownership:technical-execution-v1", "unchecked",
                List.of(new OwnershipRule("PROVEN_VALUE", "section-4"),
                        new OwnershipRule("BUSINESS_TERM", "section-2"),
                        new OwnershipRule("BOUNDED_QUESTION", "section-9"),
                        new OwnershipRule("TECHNICAL_DISPLAY", "section-1"),
                        new OwnershipRule("OUTCOME", "section-4"),
                        new OwnershipRule("FIELD", "section-5"),
                        new OwnershipRule("METRIC", "section-7")));
        return Stage03Registries.freeze(original.businessTerms(), technicalDisplays, original.claims(),
                original.questions(), sentenceTemplates, ownership);
    }

    private static ReaderSentenceTemplate template(String key, String ownerSection, String literal,
                                                   String slotKey, String slotKind) {
        return new ReaderSentenceTemplate(key, ownerSection, literal,
                List.of(new TemplateSlotDeclaration(slotKey, slotKind)));
    }

    private static ReaderSentenceTemplate formulaTemplate(String key, String section) {
        return new ReaderSentenceTemplate(key, section,
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
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-template-execution-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        return new Scenario(Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02)), stage02);
    }

    private record Scenario(Stage03Request request, Stage02Result stage02) {
    }
}
