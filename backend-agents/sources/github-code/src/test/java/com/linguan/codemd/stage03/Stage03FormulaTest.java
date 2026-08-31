package com.linguan.codemd.stage03;

import com.linguan.codemd.stage02.AllowedAtomView;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public-seam RED contract for formula and field semantic density. */
class Stage03FormulaTest {

    @Test
    void formulaMetricAndFieldReaderItemsPreserveActualFormulaAtomSemantics() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-formula-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        AllowedAtomView formulaAtom = stage02.evidenceCapsules().stream()
                .flatMap(capsule -> capsule.allowedFacts().stream())
                .filter(fact -> "AVAILABLE_FORMULA".equals(fact.kind()))
                .flatMap(fact -> fact.atoms().stream()).findFirst().orElseThrow();
        List<String> formulaBasis = stage02.evidenceCapsules().stream()
                .flatMap(capsule -> capsule.allowedFacts().stream())
                .filter(fact -> "AVAILABLE_FORMULA".equals(fact.kind()))
                .flatMap(fact -> fact.atoms().stream()).map(AllowedAtomView::atomId).sorted().toList();
        String formula = normalize(formulaAtom.value().canonical());
        List<String> tokens = List.of(formula.split(" "));
        Set<String> operandTokens = tokens.stream()
                .filter(token -> token.matches("[A-Za-z_][A-Za-z0-9_]*"))
                .collect(Collectors.toCollection(TreeSet::new));
        boolean structuredFormula = tokens.stream().anyMatch(token -> token.matches("[+\\-*/=<>]+"))
                && operandTokens.size() >= 2;

        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02));
        Stage03Result result = new Stage03Generator().generate(request, Stage03Fixtures.validProvider(stage02));
        RepositoryBusinessModel model = result.repositoryBusinessModel();
        List<ReaderItem> allItems = result.nineSectionPlan().sections().stream()
                .flatMap(section -> section.items().stream()).toList();
        Set<String> formulaOwnerKeys = result.nineSectionPlan().atomDispositions().stream()
                .filter(disposition -> formulaBasis.contains(disposition.atomId()))
                .map(AtomDisposition::ownerId).filter(owner -> owner != null)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> allAtomOwnerKeys = result.nineSectionPlan().atomDispositions().stream()
                .map(AtomDisposition::ownerId).filter(owner -> owner != null)
                .collect(Collectors.toCollection(TreeSet::new));

        if (!structuredFormula) {
            assertAll(
                    () -> assertTrue(model.metrics().isEmpty(),
                            "unstructured formula values must not create a metric"),
                    () -> assertTrue(model.pendingQuestions().stream().anyMatch(question ->
                                    question.text().contains("公式") || question.code().contains("FORMULA")),
                            "an unstructured formula must remain an explicit Gap/pending item"));
            return;
        }

        MetricDefinition metric = model.metrics().stream().findFirst().orElse(null);
        String metricDisplay = metric == null ? "" : metric.display();
        List<String> metricBasis = metric == null ? List.of() : metric.basisAtomIds();
        List<FieldDimension> formulaFields = model.fieldsAndDimensions().stream()
                .filter(field -> new TreeSet<>(field.basisAtomIds()).equals(new TreeSet<>(formulaBasis)))
                .toList();
        String fieldDisplay = formulaFields.size() == 1 ? formulaFields.get(0).display() : "";
        List<ReaderItem> fieldItems = sectionItems(result, "section-5").stream()
                .filter(item -> item.itemKind().contains("FIELD")
                        && item.basisAtomIds().isEmpty()
                        && !formulaOwnerKeys.isEmpty()
                        && item.referencedItemKeys().containsAll(formulaOwnerKeys))
                .toList();
        String fieldReaderText = fieldItems.size() == 1 ? slotText(fieldItems.get(0)) : "";
        List<ReaderItem> metricItems = sectionItems(result, "section-7").stream()
                .filter(item -> item.itemKind().contains("METRIC")
                        && item.basisAtomIds().isEmpty()
                        && !formulaOwnerKeys.isEmpty()
                        && item.referencedItemKeys().containsAll(formulaOwnerKeys))
                .toList();
        String metricReaderText = metricItems.size() == 1 ? slotText(metricItems.get(0)) : "";
        boolean fieldRendersFormulaSemantics = fieldReaderText.contains(formula)
                && fieldReaderText.contains(formulaAtom.role())
                && operandTokens.stream().allMatch(fieldReaderText::contains);
        boolean metricRendersFormulaSemantics = metricReaderText.contains(formula)
                && metricReaderText.contains(formulaAtom.role())
                && operandTokens.stream().allMatch(metricReaderText::contains);

        assertAll(
                () -> assertEquals(1, model.metrics().size(),
                        "the synthetic formula fact must produce one metric, not fixed/all-flow metrics"),
                () -> assertEquals(formulaBasis, metricBasis,
                        "metric basis must be exactly the AVAILABLE_FORMULA atom IDs"),
                () -> assertEquals(formulaBasis.size(), formulaOwnerKeys.size(),
                        "each formula atom must have one plan owner key"),
                () -> assertTrue(metricDisplay.contains(formula),
                        "metric display must preserve the actual normalized formula: " + formula),
                () -> tokens.forEach(token -> assertTrue(metricDisplay.contains(token),
                        "metric display lost formula operator/operand token " + token)),
                () -> assertEquals(1, formulaFields.size(),
                        "one field dimension must own the exact formula basis"),
                () -> assertTrue(fieldDisplay.contains(formulaAtom.role()),
                        "field display must preserve the actual formula atom role " + formulaAtom.role()),
                () -> assertTrue(operandTokens.stream().allMatch(fieldDisplay::contains),
                        "field display must preserve every actual formula operand"),
                () -> assertEquals(1, fieldItems.size(),
                        "section 5 must have one typed field item referencing formula atom owners"),
                () -> assertTrue(fieldRendersFormulaSemantics,
                        "section 5 field item must render actual formula, role, and operands"),
                () -> assertEquals(1, metricItems.size(),
                        "section 7 must have one typed metric item referencing formula atom owners"),
                () -> assertTrue(metricRendersFormulaSemantics,
                        "section 7 metric item must render actual formula, role, and operands"),
                () -> assertFalse(allItems.stream().anyMatch(item -> item.itemKind().contains("METRIC")
                                && item.referencedItemKeys().containsAll(formulaOwnerKeys)
                                && item.referencedItemKeys().stream().anyMatch(reference ->
                                allAtomOwnerKeys.contains(reference) && !formulaOwnerKeys.contains(reference))),
                        "formula metric reader items must not reference unrelated atom owners"));
    }

    private static List<ReaderItem> sectionItems(Stage03Result result, String sectionKey) {
        return result.nineSectionPlan().sections().stream()
                .filter(section -> sectionKey.equals(section.sectionKey()))
                .flatMap(section -> section.items().stream()).toList();
    }

    private static String slotText(ReaderItem item) {
        return item.slots().stream().map(Object::toString).collect(Collectors.joining("\n"));
    }

    private static String normalize(String value) {
        return Pattern.compile("\\s+").matcher(value).replaceAll(" ").trim();
    }
}
