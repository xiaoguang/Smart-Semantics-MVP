package com.linguan.codemd.stage03;

import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public-seam RED contracts for M5--M7.  The provider below is deliberately
 * scripted: this suite must never call a model, inspect a private module, or
 * allow a test hook to manufacture an intermediate Stage 03 object.
 */
class Stage03GeneratorTest {
    private static final List<String> SECTION_HEADINGS = List.of(
            "文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系",
            "指标口径", "示例问题", "待确认事项");

    @Test
    void syntheticReplayRunsOneFlowInOneSessionForExactlyR1AndR2AndRendersNineSections()
            throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-positive-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request,
                stage02.stage02ResultId(), Stage03Fixtures.registryBundle(stage02));
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.validProvider(stage02);

        Stage03Result result = new Stage03Generator().generate(request, provider);

        assertEquals("stage03-result-v1", result.schemaVersion());
        assertTrue(result.stage03ResultId().matches("stage03-result:[0-9a-f]{64}"));
        assertEquals(stage02.stage02ResultId(), result.stage02ResultId());
        assertEquals(1, result.flowInterpretations().size());
        assertEquals(7, result.flowInterpretations().get(0).admittedMeanings().size());
        assertEquals(1, result.repositoryBusinessModel().flows().size());
        assertEquals(4, result.repositoryBusinessModel().outcomes().size());
        assertEquals(1, result.repositoryBusinessModel().metrics().size());
        assertEquals(5, result.repositoryBusinessModel().pendingQuestions().size());
        assertEquals(20, result.nineSectionPlan().atomDispositions().size());
        assertEquals(9, result.nineSectionPlan().sections().size());
        assertEquals(SECTION_HEADINGS, headings(result.renderedDocument().markdown()));
        assertTrue(result.renderedDocument().markdown().contains("available"));

        List<FlowModelTask> tasks = provider.tasks();
        assertEquals(2, tasks.size(), "one isolated Capsule requires exactly R1 and R2");
        assertEquals(List.of(1, 2), tasks.stream().map(FlowModelTask::flowInterpretationRound).toList());
        assertEquals(tasks.get(0).taskSpecId(), tasks.get(1).taskSpecId());
        assertEquals(tasks.get(0).flowSliceId(), tasks.get(1).flowSliceId());
        assertEquals(tasks.get(0).evidenceCapsuleId(), tasks.get(1).evidenceCapsuleId());
        assertEquals(tasks.get(0).isolatedSessionKey(), tasks.get(1).isolatedSessionKey());
        assertEquals(tasks.get(0).inputJsonSha256(), tasks.get(1).inputJsonSha256());
        assertFalse(tasks.get(0).inputJson().contains(root.toAbsolutePath().toString()));
    }

    @Test
    void stage02ReplayIdentityMismatchFailsBeforeProvider() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-replay-mismatch-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request,
                "stage02-result:" + "0".repeat(64), Stage03Fixtures.registryBundle(stage02));
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.validProvider(stage02);

        assertThrows(RuntimeException.class, () -> new Stage03Generator().generate(request, provider));
        assertTrue(provider.tasks().isEmpty(), "replay gate precedes every model call");
    }

    @Test
    void invalidR1ResponseStopsBeforeR2() throws Exception {
        Stage03Fixtures.ScriptedModelProvider provider = provider(
                response -> "{", UnaryOperator.identity(), UnaryOperator.identity());

        assertThrows(RuntimeException.class, () -> generate(provider));
        assertEquals(List.of(1), provider.tasks().stream()
                .map(FlowModelTask::flowInterpretationRound).toList());
    }

    @Test
    void unknownR1ControlledKeyStopsBeforeR2() throws Exception {
        Stage03Fixtures.ScriptedModelProvider provider = provider(
                response -> response.replace("TERM_RESERVATION_FLOW", "TERM_UNKNOWN"),
                UnaryOperator.identity(), UnaryOperator.identity());

        assertThrows(RuntimeException.class, () -> generate(provider));
        assertEquals(1, provider.tasks().size());
    }

    @Test
    void openR1ProseIsNotAcceptedAsASelection() throws Exception {
        Stage03Fixtures.ScriptedModelProvider provider = provider(
                response -> response.replace("\"proposals\"", "\"summary\":\"free prose\",\"proposals\""),
                UnaryOperator.identity(), UnaryOperator.identity());

        assertThrows(RuntimeException.class, () -> generate(provider));
        assertEquals(1, provider.tasks().size());
    }

    @Test
    void r2CannotAddAnUnreviewedProposal() throws Exception {
        Stage03Fixtures.ScriptedModelProvider provider = provider(
                UnaryOperator.identity(),
                response -> response.replace("]}", ",{\"proposalKey\":\"P10\",\"decision\":\"KEEP\"}]}") ,
                UnaryOperator.identity());

        assertThrows(RuntimeException.class, () -> generate(provider));
        assertEquals(List.of(1, 2), provider.tasks().stream()
                .map(FlowModelTask::flowInterpretationRound).toList());
    }

    @Test
    void r2CannotOmitAnR1Proposal() throws Exception {
        Stage03Fixtures.ScriptedModelProvider provider = provider(
                UnaryOperator.identity(),
                response -> response.replaceFirst(",\\{\"proposalKey\":\"P09\"[^}]*}", ""),
                UnaryOperator.identity());

        assertThrows(RuntimeException.class, () -> generate(provider));
        assertEquals(2, provider.tasks().size());
    }

    @Test
    void r2CannotExpandItsBasis() throws Exception {
        Stage03Fixtures.ScriptedModelProvider provider = provider(
                UnaryOperator.identity(),
                response -> response.replace("\"retainedBasisAtomIds\":[\"", "\"retainedBasisAtomIds\":[\"atom:unallowed\",\""),
                UnaryOperator.identity());

        assertThrows(RuntimeException.class, () -> generate(provider));
        assertEquals(2, provider.tasks().size());
    }

    @Test
    void observedRuntimeIdentityDriftStopsTheFlow() throws Exception {
        Stage03Fixtures.ScriptedModelProvider provider = provider(
                UnaryOperator.identity(), UnaryOperator.identity(),
                ignored -> new ObservedRuntimeIdentity("codex", "gpt-5.6-sol", "xhigh", "read-only"));

        assertThrows(RuntimeException.class, () -> generate(provider));
        assertEquals(1, provider.tasks().size());
    }

    @Test
    void emptyTermRegistryUsesTechnicalFallbackAndStillProducesNineSections() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-empty-registry-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        RegistryBundle registries = Stage03Fixtures.emptyBusinessTerms(
                Stage03Fixtures.registryBundle(stage02));
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request,
                stage02.stage02ResultId(), registries);
        Stage03Fixtures.ScriptedModelProvider provider =
                Stage03Fixtures.emptySelectionProvider(stage02);

        Stage03Result result = new Stage03Generator().generate(request, provider);

        assertEquals(2, provider.tasks().size());
        assertFalse(result.flowInterpretations().get(0).technicalFallbacks().isEmpty());
        assertTrue(result.flowInterpretations().get(0).interpretationGaps().stream()
                .map(Object::toString).anyMatch(value -> value.contains("NEEDS_TERM_REGISTRY")));
        assertEquals(SECTION_HEADINGS, headings(result.renderedDocument().markdown()));
    }

    @Test
    void allStage01ExpectationGapsRemainPendingQuestionsAndDoNotBecomeFacts() throws Exception {
        Stage03Result result = generateValid();

        assertEquals(5, result.repositoryBusinessModel().pendingQuestions().size());
        assertTrue(result.renderedDocument().markdown().contains("待确认事项"));
        assertTrue(result.renderedDocument().markdown().contains("尚未找到")
                || result.renderedDocument().markdown().contains("需要确认"));
    }

    @Test
    void outputConservesOutcomeClosureAndKeepsInternalIdentityOutOfReaderBody() throws Exception {
        Stage03Result result = generateValid();
        String markdown = result.renderedDocument().markdown();

        assertEquals(4, result.repositoryBusinessModel().outcomes().size());
        assertEquals(20, result.nineSectionPlan().atomDispositions().size());
        assertFalse(markdown.contains("stage02-result:"));
        assertFalse(markdown.contains("flow-slice:"));
        assertFalse(markdown.contains("evidence-capsule:"));
        assertFalse(markdown.contains("ReservationController.java"));
        assertFalse(markdown.contains("gpt-5.6"));
        assertFalse(markdown.matches("(?s).*\\b[0-9a-f]{64}\\b.*"));
        assertFalse(markdown.contains("{{"));
        assertFalse(markdown.contains("}}"));
    }

    @Test
    void responseOrderAndAbsoluteRootDoNotChangeResultIdentityOrMarkdown() throws Exception {
        Path rootA = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-determinism-a-"));
        Path rootB = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-determinism-b-"));
        Stage02Request requestA = Stage03Fixtures.stage02Request(rootA);
        Stage02Request requestB = Stage03Fixtures.stage02Request(rootB);
        Stage02Result stage02A = new Stage02Compiler().compile(requestA);
        Stage02Result stage02B = new Stage02Compiler().compile(requestB);
        Stage03Request stage03A = Stage03Fixtures.stage03Request(requestA, stage02A.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02A));
        Stage03Request stage03B = Stage03Fixtures.stage03Request(requestB, stage02B.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02B));
        Stage03Fixtures.ScriptedModelProvider providerA = Stage03Fixtures.validProvider(stage02A);
        UnaryOperator<String> reverse = response -> Stage03Fixtures.reverseArray("reviews")
                .apply(Stage03Fixtures.reverseArray("proposals").apply(response));
        Stage03Fixtures.ScriptedModelProvider providerB = Stage03Fixtures.provider(
                stage02B, reverse, reverse, UnaryOperator.identity());

        Stage03Result resultA = new Stage03Generator().generate(stage03A, providerA);
        Stage03Result resultB = new Stage03Generator().generate(stage03B, providerB);

        assertEquals(resultA.stage03ResultId(), resultB.stage03ResultId());
        assertEquals(resultA.renderedDocument().markdownSha256(),
                resultB.renderedDocument().markdownSha256());
        assertEquals(resultA.renderedDocument().markdown(), resultB.renderedDocument().markdown());
    }

    private static Stage03Fixtures.ScriptedModelProvider provider(
            UnaryOperator<String> r1Mutation, UnaryOperator<String> r2Mutation,
            UnaryOperator<ObservedRuntimeIdentity> runtimeMutation) throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-mutation-"));
        Stage02Result stage02 = Stage03Fixtures.stage02Result(root);
        return Stage03Fixtures.provider(stage02, r1Mutation, r2Mutation, runtimeMutation);
    }

    private static Stage03Result generate(Stage03Fixtures.ScriptedModelProvider provider)
            throws Exception {
        FlowModelTask task = provider.tasks().isEmpty() ? null : provider.tasks().get(0);
        if (task != null) {
            throw new AssertionError("provider has already been used");
        }
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-generate-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request,
                stage02.stage02ResultId(), Stage03Fixtures.registryBundle(stage02));
        return new Stage03Generator().generate(request, provider);
    }

    private static Stage03Result generateValid() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-generate-valid-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request,
                stage02.stage02ResultId(), Stage03Fixtures.registryBundle(stage02));
        return new Stage03Generator().generate(request, Stage03Fixtures.validProvider(stage02));
    }

    private static List<String> headings(String markdown) {
        return markdown.lines().filter(line -> line.startsWith("## "))
                .map(line -> line.substring(3)).toList();
    }
}
