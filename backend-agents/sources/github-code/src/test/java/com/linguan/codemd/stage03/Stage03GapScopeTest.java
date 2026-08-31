package com.linguan.codemd.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage02.FlowGap;
import com.linguan.codemd.stage02.FlowSlice;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public-seam regression for exact per-Flow/Capsule Gap scope. */
class Stage03GapScopeTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void tasksInterpretationsAndPendingQuestionsUseOnlyTheirFlowCapsuleGapUnion() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-gap-scope-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02));
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.validProvider(stage02);

        Stage03Result result = new Stage03Generator().generate(request, provider);
        Map<String, FlowSlice> flows = stage02.flowSlices().stream().collect(Collectors.toMap(
                FlowSlice::flowSliceId, Function.identity()));
        Map<String, EvidenceCapsule> capsules = stage02.evidenceCapsules().stream().collect(Collectors.toMap(
                EvidenceCapsule::flowSliceId, Function.identity()));
        Map<String, FlowInterpretationResult> interpretations = result.flowInterpretations().stream()
                .collect(Collectors.toMap(FlowInterpretationResult::flowSliceId, Function.identity()));
        Set<String> allFlowGapIds = stage02.flowGaps().stream().map(FlowGap::flowGapId)
                .collect(Collectors.toUnmodifiableSet());

        for (FlowModelTask task : provider.tasks()) {
            FlowSlice flow = flows.get(task.flowSliceId());
            EvidenceCapsule capsule = capsules.get(task.flowSliceId());
            Set<String> expected = new TreeSet<>(capsule.allowedGaps().stream()
                    .map(com.linguan.codemd.stage02.AllowedGapView::gapId).toList());
            expected.addAll(flow.gapIds());

            JsonNode taskRoot = JSON.readTree(task.inputJson());
            Set<String> taskGapIds = textSet(taskRoot.path("evidenceCapsule").path("allowedGaps"), "gapId");
            assertEquals(expected, taskGapIds,
                    "task must expose exactly this Flow/Capsule Gap union for " + task.flowSliceId());
            assertEquals(Set.copyOf(flow.gapIds()), textSet(taskRoot.path("flow").path("gapIds"), null),
                    "task Flow metadata must remain Flow-local");

            FlowInterpretationResult interpretation = interpretations.get(task.flowSliceId());
            Set<String> selectedQuestionGaps = interpretation.proposalDispositions().stream()
                    .flatMap(disposition -> disposition.retainedBasisGapIds().stream())
                    .collect(Collectors.toSet());
            assertTrue(expected.containsAll(selectedQuestionGaps),
                    "R1/R2 question basis must stay inside the Flow/Capsule Gap union");

            Set<String> sourceGapRefs = interpretation.interpretationGaps().stream()
                    .map(InterpretationGap::sourceGapId).collect(Collectors.toSet());
            Set<String> sourceStage02GapRefs = new HashSet<>(sourceGapRefs);
            sourceStage02GapRefs.retainAll(allFlowGapIds);
            assertTrue(expected.containsAll(sourceStage02GapRefs),
                    "source-backed interpretation Gaps must remain Flow-local");

            Set<String> foreignFlowGapIds = new HashSet<>(allFlowGapIds);
            foreignFlowGapIds.removeAll(flow.gapIds());
            assertTrue(sourceGapRefs.stream().noneMatch(foreignFlowGapIds::contains),
                    "another entry's FlowGap must not enter this interpretation");
        }

        result.repositoryBusinessModel().pendingQuestions().forEach(question ->
                assertTrue(question.sourceGapIds().stream().allMatch(allExpectedGapIds(stage02)::contains),
                        "repository pending questions must preserve only known source Gap IDs"));
    }

    private static Set<String> allExpectedGapIds(Stage02Result stage02) {
        Set<String> result = new HashSet<>(stage02.flowGaps().stream().map(FlowGap::flowGapId).toList());
        stage02.evidenceCapsules().stream().flatMap(capsule -> capsule.allowedGaps().stream())
                .map(com.linguan.codemd.stage02.AllowedGapView::gapId).forEach(result::add);
        stage02.flowSlices().forEach(flow -> result.addAll(flow.gapIds()));
        return Set.copyOf(result);
    }

    private static Set<String> textSet(JsonNode values, String field) {
        if (!values.isArray()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (JsonNode value : values) {
            if (field == null) {
                result.add(value.asText());
            } else if (value.has(field)) {
                JsonNode fieldValue = value.get(field);
                if (fieldValue.isArray()) {
                    fieldValue.forEach(item -> result.add(item.asText()));
                } else {
                    result.add(fieldValue.asText());
                }
            }
        }
        return Set.copyOf(result);
    }
}
