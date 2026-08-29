package com.linguan.codemd.analysis;

import com.linguan.codemd.discovery.SourceLocator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 source-only contract for CodeFacts, conditions, and fail-closed
 * receiver binding. No fixture is compiled, executed, or symbol-solver backed.
 */
class CodeFactAnalyzerTest {
    @Test
    void discoversUniqueDirectCallsAsExactCodeFactsWithCallSiteLocators() throws Exception {
        Path root = CodeFactFixtures.exactCallChain(Files.createTempDirectory("phase2-exact-call-"));

        AnalysisResult result = analyze(root);

        List<CodeFact> callFacts = result.codeFacts().stream()
                .filter(fact -> fact.kind().equals("CALL"))
                .toList();
        assertEquals(List.of(
                        "example.OrderController#approve -> example.OrderService#approve",
                        "example.OrderService#approve -> example.OrderMapper#approve"),
                callFacts.stream()
                        .map(fact -> fact.subject() + " -> " + fact.object())
                        .toList());
        assertTrue(callFacts.stream().allMatch(fact -> fact.resolution().equals("EXACT")),
                "a uniquely declared receiver must produce only EXACT call facts");
        assertEquals(new SourceLocator(
                        "src/main/java/example/OrderController.java", 10, 10),
                callFacts.get(0).sourceLocator());
        assertEquals(new SourceLocator(
                        "src/main/java/example/OrderService.java", 10, 10),
                callFacts.get(1).sourceLocator());
        assertTrue(result.gaps().isEmpty(), "the unique source fixture must have no gaps");
    }

    @Test
    void nonUniqueReceiverProducesUnresolvedGapAndNoExactCallFact() throws Exception {
        Path root = CodeFactFixtures.ambiguousReceiver(
                Files.createTempDirectory("phase2-ambiguous-receiver-"));

        AnalysisResult result = analyze(root);

        assertTrue(result.codeFacts().stream().noneMatch(fact -> fact.kind().equals("CALL")),
                "an ambiguous receiver must not be guessed into a call fact");
        List<Gap> unresolved = result.gaps().stream()
                .filter(gap -> gap.code().equals("UNRESOLVED"))
                .toList();
        assertEquals(1, unresolved.size());
        assertEquals(new SourceLocator("src/main/java/example/OrderService.java", 7, 7),
                unresolved.get(0).sourceLocator());
        assertTrue(unresolved.get(0).subject().contains("port.approve"));
    }

    @Test
    void changingGuardLiteralChangesConditionFactAndRemovesOldFact() throws Exception {
        Path approvedRoot = CodeFactFixtures.guardedFlow(
                Files.createTempDirectory("phase2-guard-approved-"), "APPROVED");
        Path cancelledRoot = CodeFactFixtures.guardedFlow(
                Files.createTempDirectory("phase2-guard-cancelled-"), "CANCELLED");

        List<ConditionFact> approved = analyze(approvedRoot).conditionFacts();
        List<ConditionFact> cancelled = analyze(cancelledRoot).conditionFacts();

        assertEquals(1, approved.size());
        assertEquals(1, cancelled.size());
        assertTrue(approved.get(0).expression().contains("APPROVED"));
        assertTrue(cancelled.get(0).expression().contains("CANCELLED"));
        assertNotEquals(approved.get(0).factId(), cancelled.get(0).factId(),
                "a changed guard literal must receive a new condition identity");
        assertNotEquals(approved.get(0).expression(), cancelled.get(0).expression());
        assertFalse(cancelled.stream().anyMatch(fact -> fact.expression().contains("APPROVED")),
                "the old guard condition must not remain admitted after mutation");
        assertEquals(new SourceLocator("src/main/java/example/OrderService.java", 5, 5),
                approved.get(0).sourceLocator());
        assertEquals(approved.get(0).sourceLocator(), cancelled.get(0).sourceLocator());
    }

    private static AnalysisResult analyze(Path root) {
        return new SourceAnalyzer().analyze(new AnalysisRequest(root));
    }
}
