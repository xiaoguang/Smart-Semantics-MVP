package com.linguan.codemd.stage03;

import com.linguan.codemd.stage02.BranchDecision;
import com.linguan.codemd.stage02.OutcomePath;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public-seam RED contract for preserving every compiled OutcomePath in M6/M7. */
class Stage03ProofDensityTest {

    @Test
    void everyCompiledOutcomeIsConservedAsOneTypedSectionFourReaderItem() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-outcome-density-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02));

        Stage03Result result = new Stage03Generator().generate(request, Stage03Fixtures.validProvider(stage02));
        List<OutcomePath> paths = stage02.flowSlices().stream()
                .flatMap(flow -> flow.outcomePaths().stream()).toList();
        List<BusinessOutcome> outcomes = result.repositoryBusinessModel().outcomes();
        List<ReaderItem> sectionFourItems = result.nineSectionPlan().sections().stream()
                .filter(section -> "section-4".equals(section.sectionKey()))
                .flatMap(section -> section.items().stream())
                .toList();

        assertEquals(paths.size(), outcomes.size(),
                "every compiled OutcomePath must become exactly one BusinessOutcome");

        List<Executable> checks = new ArrayList<>();
        for (OutcomePath path : paths) {
            BusinessOutcome outcome = outcomes.stream()
                    .filter(candidate -> path.outcomePathId().equals(candidate.outcomePathId()))
                    .findFirst().orElseThrow();
            List<ReaderItem> matchingItems = sectionFourItems.stream()
                    .filter(item -> item.itemKind().contains("OUTCOME")
                            && (item.basisAtomIds().equals(path.requiredAtomIds())
                            || item.referencedItemKeys().contains(outcome.outcomeId())))
                    .toList();
            ReaderItem readerItem = matchingItems.size() == 1 ? matchingItems.get(0) : null;
            String slotText = readerItem == null ? "" : readerItem.slots().stream()
                    .map(Stage03ProofDensityTest::slotText).collect(Collectors.joining("\n"));
            boolean branchSemantics = path.decisions().stream().allMatch(decision -> containsBranchSemantics(
                    slotText, decision));

            checks.add(() -> assertAll(path.outcomePathId(),
                    () -> assertEquals(path.requiredAtomIds(), outcome.basisAtomIds(),
                            "BusinessOutcome basis must equal the public OutcomePath required atoms"),
                    () -> assertEquals(1, matchingItems.size(),
                            "exactly one section-4 ReaderItem must own/reference this OutcomePath basis"),
                    () -> assertTrue(slotText.contains(path.terminalKind()),
                            "ReaderItem slots must preserve terminal kind " + path.terminalKind()),
                    () -> assertTrue(branchSemantics,
                            "ReaderItem slots must preserve every OutcomePath polarity/guard semantic")));
        }
        checks.add(() -> assertEquals(paths.size(), sectionFourItems.stream()
                                .filter(item -> item.itemKind().contains("OUTCOME")
                                        && !"EMPTY_SECTION".equals(item.itemKind()))
                                .count(),
                        "section 4 must contain one typed outcome item per compiled path"));
        assertAll(checks);
    }

    private static boolean containsBranchSemantics(String slotText, BranchDecision decision) {
        return slotText.contains(decision.polarity()) || slotText.contains(decision.guardNodeId())
                || slotText.contains(decision.conditionAtomId()) || slotText.contains(decision.normalizedCondition());
    }

    private static String slotText(ReaderSlot slot) {
        String value;
        if (slot instanceof ProvenValueSlot proven) {
            value = proven.value();
        } else if (slot instanceof BusinessTermSlot term) {
            value = term.value();
        } else if (slot instanceof TechnicalDisplaySlot technical) {
            value = technical.value();
        } else if (slot instanceof BoundedQuestionSlot question) {
            value = question.value();
        } else {
            value = "";
        }
        return slot + " " + value;
    }
}
