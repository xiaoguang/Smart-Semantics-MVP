package com.linguan.codemd.stage03;

import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02ResourceBudget;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public M6 → M7 information-density and conservation contract. */
class Stage03ReaderDensityTest {

    @Test
    void everyRepositoryKnowledgeItemHasTypedSectionOwnerAndCleanRenderedBusinessEvidence() throws Exception {
        Scenario scenario = scenario();
        Stage03Fixtures.ScriptedModelProvider provider =
                Stage03Fixtures.emptySelectionProvider(scenario.stage02());

        Stage03Result result = new Stage03Generator().generate(scenario.request(), provider);
        RepositoryBusinessModel model = result.repositoryBusinessModel();
        List<Executable> checks = new ArrayList<>();

        checks.add(() -> assertFalse(model.objects().isEmpty(),
                "the dual-flow fixture must produce repository BusinessObjects"));
        checks.add(() -> assertFalse(model.activities().isEmpty(),
                "the dual-flow fixture must produce repository BusinessActivities"));
        checks.add(() -> assertFalse(model.relations().isEmpty(),
                "the dual-flow fixture must produce typed ObjectRelations"));
        checks.add(() -> assertFalse(model.exampleQuestions().isEmpty(),
                "formula/outcome knowledge must produce answerable example questions"));
        checks.add(() -> assertEquals(9, result.nineSectionPlan().sections().size()));

        List<ReaderItem> objectItems = itemsIn(result, "section-3").stream()
                .filter(item -> item.itemKind().toUpperCase().contains("OBJECT")
                        && !"EMPTY_SECTION".equals(item.itemKind())).toList();
        List<ReaderItem> activityItems = itemsIn(result, "section-4").stream()
                .filter(item -> item.itemKind().toUpperCase().contains("ACTIVITY")
                        && !"EMPTY_SECTION".equals(item.itemKind())).toList();
        List<ReaderItem> relationItems = itemsIn(result, "section-6").stream()
                .filter(item -> item.itemKind().toUpperCase().contains("RELATION")
                        && !"EMPTY_SECTION".equals(item.itemKind())).toList();
        List<ReaderItem> questionItems = itemsIn(result, "section-8").stream()
                .filter(item -> item.itemKind().toUpperCase().contains("QUESTION")
                        && !"EMPTY_SECTION".equals(item.itemKind())).toList();

        checks.add(() -> assertFalse(objectItems.isEmpty(),
                "section 3 must contain typed object items, not only empty/fallback scope text"));
        checks.add(() -> assertFalse(activityItems.isEmpty(),
                "section 4 must contain typed activity items, not only atom/outcome fillers"));
        checks.add(() -> assertFalse(relationItems.isEmpty(),
                "section 6 must contain typed relation items, not an EMPTY_SECTION"));
        checks.add(() -> assertFalse(questionItems.isEmpty(),
                "section 8 must contain typed answerable-question items, not an EMPTY_SECTION"));
        for (String sectionKey : List.of("section-3", "section-4", "section-6", "section-8")) {
            checks.add(() -> assertTrue(itemsIn(result, sectionKey).stream()
                            .noneMatch(item -> "EMPTY_SECTION".equals(item.itemKind())),
                    sectionKey + " must not be represented by an EMPTY_SECTION when M6 knowledge exists"));
        }

        for (BusinessObject object : model.objects()) {
            checks.add(() -> assertExactlyOneBinding(objectItems, object.objectId(), object.basisAtomIds(),
                    "object " + object.objectId()));
            checks.add(() -> assertTrue(result.renderedDocument().markdown().contains(object.display()),
                    "Markdown must render the object business/technical display for " + object.objectId()));
            checks.add(() -> assertFalse(result.renderedDocument().markdown().contains(object.objectId()),
                    "Markdown must not leak object identity " + object.objectId()));
        }
        for (BusinessActivity activity : model.activities()) {
            checks.add(() -> assertExactlyOneBinding(activityItems, activity.activityId(), activity.basisAtomIds(),
                    "activity " + activity.activityId()));
            checks.add(() -> assertTrue(result.renderedDocument().markdown().contains(activity.display()),
                    "Markdown must render the activity display for " + activity.activityId()));
            checks.add(() -> assertFalse(result.renderedDocument().markdown().contains(activity.activityId()),
                    "Markdown must not leak activity identity " + activity.activityId()));
        }
        for (ObjectRelation relation : model.relations()) {
            checks.add(() -> assertExactlyOneBinding(relationItems, relation.relationId(), relation.basisAtomIds(),
                    "relation " + relation.relationId()));
            checks.add(() -> assertFalse(result.renderedDocument().markdown().contains(relation.relationId()),
                    "Markdown must not leak relation identity " + relation.relationId()));
        }
        for (AnswerableQuestion question : model.exampleQuestions()) {
            checks.add(() -> assertExactlyOneBinding(questionItems, question.questionId(), List.of(),
                    "answerable question " + question.questionId()));
            checks.add(() -> assertTrue(result.renderedDocument().markdown().contains(question.text()),
                    "Markdown must render answerable question text " + question.questionId()));
            checks.add(() -> assertFalse(result.renderedDocument().markdown().contains(question.questionId()),
                    "Markdown must not leak question identity " + question.questionId()));
        }

        String markdown = result.renderedDocument().markdown();
        checks.add(() -> assertTrue(markdown.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00
                        && codePoint <= 0x9FFF),
                "reader output must contain business-readable Chinese text"));
        checks.add(() -> assertFalse(markdown.matches("(?s).*\\b[0-9a-f]{64}\\b.*"),
                "reader output must not leak SHA/hash tokens"));
        checks.add(() -> assertFalse(markdown.contains("stage02-result:") || markdown.contains("flow-slice:")
                        || markdown.contains("evidence-capsule:") || markdown.contains("prompt")
                        || markdown.contains("provider") || markdown.contains("gpt-5.6"),
                "reader output must not contain internal identity or provider tokens"));
        assertAll(checks);
    }

    private static void assertExactlyOneBinding(List<ReaderItem> items, String m6Id, List<String> basis,
                                                 String label) {
        List<ReaderItem> matches = items.stream().filter(item -> item.referencedItemKeys().contains(m6Id)
                || (!basis.isEmpty() && Set.copyOf(item.basisAtomIds()).equals(Set.copyOf(basis)))).toList();
        assertEquals(1, matches.size(), label + " must have exactly one typed ReaderItem owner/reference");
        ReaderItem item = matches.get(0);
        assertTrue(item.referencedItemKeys().contains(m6Id)
                        || Set.copyOf(item.basisAtomIds()).equals(Set.copyOf(basis)),
                label + " binding must use its public ID or exact atom basis, never display-text matching");
    }

    private static List<ReaderItem> itemsIn(Stage03Result result, String sectionKey) {
        return result.nineSectionPlan().sections().stream()
                .filter(section -> sectionKey.equals(section.sectionKey()))
                .flatMap(section -> section.items().stream()).toList();
    }

    private static Scenario scenario() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-reader-density-"));
        Stage01Request stage01Request = Stage03Fixtures.independentTwoFlowRequest(root);
        Stage01Result stage01 = new Stage01Analyzer().analyze(stage01Request);
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(stage01Request,
                stage01.stage01ResultId(), defaultStage02Budget());
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        assertEquals(2, stage02.flowSlices().size(), "fixture must compile two independent flows");
        assertEquals(2, stage02.evidenceCapsules().size(), "fixture must create two evidence Capsules");
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02));
        return new Scenario(request, stage02);
    }

    private static Stage02ResourceBudget defaultStage02Budget() {
        return new Stage02ResourceBudget(128, 64, 20_000, 40_000, 128, 64,
                16_384, 262_144, 256);
    }

    private record Scenario(Stage03Request request, Stage02Result stage02) {
    }
}
