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

/** Public-seam RED contracts for Stage 03 semantic reader completeness. */
class Stage03SemanticTest {

    @Test
    void frozenTemplateAndOwnershipBindingsDriveReaderItemsAndFailClosedWhenInvalid() throws Exception {
        Scenario scenario = scenario();
        RegistryBundle complete = semanticRegistries(scenario.request().registryBundle());
        Stage03Request request = withRegistries(scenario.request(), complete);
        Stage03Generator generator = new Stage03Generator();

        Stage03Result result = generator.generate(request, Stage03Fixtures.validProvider(scenario.stage02()));
        List<ReaderItem> items = result.nineSectionPlan().sections().stream()
                .flatMap(section -> section.items().stream()).toList();
        ReaderItem atom = items.stream().filter(item -> !item.basisAtomIds().isEmpty()).findFirst().orElseThrow();
        ReaderItem meaning = items.stream().filter(item -> !item.basisMeaningIds().isEmpty()).findFirst().orElseThrow();
        ReaderItem gap = items.stream().filter(item -> !item.basisGapIds().isEmpty()).findFirst().orElseThrow();

        RegistryBundle missingTemplate = semanticRegistries(scenario.request().registryBundle(),
                templates -> templates.stream().filter(template -> !"READER_ATOM_V1".equals(template.templateKey()))
                        .toList(), List.of());
        RegistryBundle duplicateOwner = semanticRegistries(scenario.request().registryBundle(), null,
                List.of(new OwnershipRule("PROVEN_VALUE", "section-5"),
                        new OwnershipRule("PROVEN_VALUE", "section-6"),
                        new OwnershipRule("BUSINESS_TERM", "section-6"),
                        new OwnershipRule("BOUNDED_QUESTION", "section-7"),
                        new OwnershipRule("TECHNICAL_DISPLAY", "section-1"),
                        new OwnershipRule("OUTCOME", "section-4"),
                        new OwnershipRule("FIELD", "section-5"),
                        new OwnershipRule("METRIC", "section-7")));

        assertAll(
                () -> assertEquals("section-5", atom.ownerSectionKey()),
                () -> assertEquals("section-6", meaning.ownerSectionKey()),
                () -> assertEquals("section-7", gap.ownerSectionKey()),
                () -> assertTrue(result.renderedDocument().markdown().contains("原子模板：")),
                () -> assertTrue(result.renderedDocument().markdown().contains("术语模板：")),
                () -> assertTrue(result.renderedDocument().markdown().contains("缺口模板：")),
                () -> assertFalse(items.stream().anyMatch(item -> item.templateKey().equals("READER_SCOPE_V1")),
                        "scope filler cannot replace typed registry-backed items"),
                () -> assertRegistryFailure(generator, withRegistries(request, missingTemplate)),
                () -> assertRegistryFailure(generator, withRegistries(request, duplicateOwner)));
    }

    @Test
    void forbiddenRegistryDisplayTemplateAndQuestionValuesFailBeforeReaderBody() throws Exception {
        Scenario scenario = scenario();
        RegistryBundle complete = semanticRegistries(scenario.request().registryBundle());
        String forbidden = "prompt provider runtime model gpt-5.6-luna "
                + "/absolute/repo/src/main/java/example/Foo.java "
                + "relative/mappers/InventoryMapper.xml settings.yml";

        List<BusinessTermEntry> terms = complete.businessTerms().terms().stream()
                .map(term -> new BusinessTermEntry(term.businessTermKey(), term.anchorKind(), forbidden,
                        term.eligibleAtomKinds(), term.minimumBasisAtomIds(), term.priority(),
                        term.technicalFallbackPolicyKey()))
                .toList();
        BusinessTermRegistry businessTerms = new BusinessTermRegistry(
                complete.businessTerms().schemaVersion(), complete.businessTerms().registryId(), "unchecked", terms);
        List<TechnicalDisplayPolicy> policies = complete.technicalDisplays().policies().stream()
                .map(policy -> "FLOW_ROUTE_HANDLER_V1".equals(policy.policyKey())
                        ? new TechnicalDisplayPolicy(policy.policyKey(), policy.anchorKind(), policy.resolutionOrder(),
                        forbidden)
                        : policy)
                .toList();
        TechnicalDisplayRegistry technicalDisplays = new TechnicalDisplayRegistry(
                complete.technicalDisplays().schemaVersion(), complete.technicalDisplays().registryId(),
                "unchecked", policies);
        List<QuestionEntry> questions = complete.questions().questions().stream()
                .map(question -> new QuestionEntry(forbidden, question.allowedGapReasonCodes(),
                        question.readerTemplateKey()))
                .toList();
        QuestionRegistry questionRegistry = new QuestionRegistry(complete.questions().schemaVersion(),
                complete.questions().registryId(), "unchecked", questions);
        List<ReaderSentenceTemplate> templates = complete.sentenceTemplates().templates().stream()
                .map(template -> "READER_ATOM_V1".equals(template.templateKey())
                        ? new ReaderSentenceTemplate(template.templateKey(), template.ownerSectionKey(), forbidden,
                        template.slots())
                        : template)
                .toList();
        ReaderSentenceTemplateRegistry templateRegistry = new ReaderSentenceTemplateRegistry(
                complete.sentenceTemplates().schemaVersion(), complete.sentenceTemplates().registryId(),
                "unchecked", templates);
        RegistryBundle unsafe = Stage03Registries.freeze(businessTerms, technicalDisplays, complete.claims(),
                questionRegistry, templateRegistry, complete.sectionOwnership());
        Stage03Request request = withRegistries(scenario.request(), unsafe);
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.validProvider(scenario.stage02());

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(request, provider));
        assertAll(
                () -> assertTrue(failure.code() == Stage03FailureCode.REGISTRY_INVALID
                                || failure.code() == Stage03FailureCode.READER_INVARIANT_BROKEN,
                        "unsafe registry values must fail closed: " + failure.code()),
                () -> assertTrue(provider.tasks().isEmpty(),
                        "registry safety must be checked before model-backed reader generation"));
    }

    private static void assertRegistryFailure(Stage03Generator generator, Stage03Request request) {
        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> generator.generate(request, Stage03Fixtures.validProvider(stage02For(request))));
        assertTrue(failure.code() == Stage03FailureCode.REGISTRY_INVALID
                        || failure.code() == Stage03FailureCode.READER_INVARIANT_BROKEN,
                "invalid reader registry must fail closed: " + failure.code());
    }

    private static Stage02Result stage02For(Stage03Request request) {
        return new Stage02Compiler().compile(request.stage02Request());
    }

    private static RegistryBundle semanticRegistries(RegistryBundle original) {
        return semanticRegistries(original, null, null);
    }

    private static RegistryBundle semanticRegistries(RegistryBundle original,
                                                      java.util.function.UnaryOperator<List<ReaderSentenceTemplate>> templateMutation,
                                                      List<OwnershipRule> ownershipOverride) {
        List<ReaderSentenceTemplate> templates = new ArrayList<>(List.of(
                template("READER_SCOPE_V1", "section-1", "范围：{technical-display}", "technical-display", "TECHNICAL_DISPLAY"),
                template("READER_ATOM_V1", "section-5", "原子模板：{proven-value}", "proven-value", "PROVEN_VALUE"),
                template("READER_TERM_V1", "section-6", "术语模板：{business-term}", "business-term", "BUSINESS_TERM"),
                template("READER_GAP_V1", "section-7", "缺口模板：{bounded-question}", "bounded-question", "BOUNDED_QUESTION"),
                new ReaderSentenceTemplate("READER_OUTCOME_V1", "section-4",
                        "终点模板：{outcome-terminal}；路径语义：{outcome-semantics}",
                        List.of(new TemplateSlotDeclaration("outcome-terminal", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("outcome-semantics", "TECHNICAL_DISPLAY"))),
                new ReaderSentenceTemplate("READER_FIELD_FORMULA_V1", "section-5",
                        "字段公式：{formula-role}；值：{formula-value}；运算符：{formula-operators}；操作数：{formula-operands}",
                        List.of(new TemplateSlotDeclaration("formula-role", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-value", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-operators", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-operands", "TECHNICAL_DISPLAY"))),
                new ReaderSentenceTemplate("READER_METRIC_FORMULA_V1", "section-7",
                        "指标公式：{formula-role}；值：{formula-value}；运算符：{formula-operators}；操作数：{formula-operands}",
                        List.of(new TemplateSlotDeclaration("formula-role", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-value", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-operators", "TECHNICAL_DISPLAY"),
                                new TemplateSlotDeclaration("formula-operands", "TECHNICAL_DISPLAY"))),
                template("CLAIM_INCREMENT_FIELD_V1", "section-4", "声明：{proven-value}", "proven-value", "PROVEN_VALUE"),
                template("CLAIM_CONDITIONAL_THROW_V1", "section-4", "条件结束：{proven-value}", "proven-value", "PROVEN_VALUE"),
                template("CLAIM_RELATION_V1", "section-6", "关系：{proven-value}", "proven-value", "PROVEN_VALUE"),
                template("CLAIM_POLICY_V1", "section-9", "政策：{bounded-question}", "bounded-question", "BOUNDED_QUESTION"),
                template("QUESTION_CONFIRM_MISSING_ROW_POLICY_V1", "section-9", "问题：{bounded-question}", "bounded-question", "BOUNDED_QUESTION"),
                template("TECHNICAL_FLOW_DISPLAY_V1", "section-1", "技术流程：{technical-display}", "technical-display", "TECHNICAL_DISPLAY"),
                template("TECHNICAL_TYPE_DISPLAY_V1", "section-3", "技术类型：{technical-display}", "technical-display", "TECHNICAL_DISPLAY"),
                template("TECHNICAL_RECORD_DISPLAY_V1", "section-3", "技术记录：{technical-display}", "technical-display", "TECHNICAL_DISPLAY"),
                template("TECHNICAL_OUTCOME_DISPLAY_V1", "section-4", "技术终点：{technical-display}", "technical-display", "TECHNICAL_DISPLAY"),
                template("TECHNICAL_ACTIVITY_DISPLAY_V1", "section-4", "技术活动：{technical-display}", "technical-display", "TECHNICAL_DISPLAY")));
        if (templateMutation != null) {
            templates = new ArrayList<>(templateMutation.apply(templates));
        }
        List<OwnershipRule> ownership = ownershipOverride == null ? List.of(
                new OwnershipRule("PROVEN_VALUE", "section-5"),
                new OwnershipRule("BUSINESS_TERM", "section-6"),
                new OwnershipRule("BOUNDED_QUESTION", "section-7"),
                new OwnershipRule("TECHNICAL_DISPLAY", "section-1"),
                new OwnershipRule("OUTCOME", "section-4"),
                new OwnershipRule("FIELD", "section-5"),
                new OwnershipRule("METRIC", "section-7")) : ownershipOverride;
        ReaderSentenceTemplateRegistry templateRegistry = new ReaderSentenceTemplateRegistry(
                "reader-template-registry-v1", "reader-templates:semantic-v1", "unchecked", templates);
        SectionOwnershipRegistry ownershipRegistry = new SectionOwnershipRegistry(
                "section-ownership-registry-v1", "section-ownership:semantic-v1", "unchecked", ownership);
        return Stage03Registries.freeze(original.businessTerms(), original.technicalDisplays(), original.claims(),
                original.questions(), templateRegistry, ownershipRegistry);
    }

    private static ReaderSentenceTemplate template(String key, String ownerSection, String pattern,
                                                   String slotKey, String slotKind) {
        return new ReaderSentenceTemplate(key, ownerSection, pattern,
                List.of(new TemplateSlotDeclaration(slotKey, slotKind)));
    }

    private static Stage03Request withRegistries(Stage03Request original, RegistryBundle registries) {
        return new Stage03Request(original.schemaVersion(), original.stage02Request(), original.expectedStage02ResultId(),
                registries, original.interpretationProfileRef(), original.knowledgeProfileRef(),
                original.nineSectionProfileRef(), original.modelRuntimePolicy(), original.resourceBudget());
    }

    private static Scenario scenario() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-semantic-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02));
        return new Scenario(request, stage02);
    }

    private record Scenario(Stage03Request request, Stage02Result stage02) {
    }
}
