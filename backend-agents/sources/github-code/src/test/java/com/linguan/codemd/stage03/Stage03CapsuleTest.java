package com.linguan.codemd.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.stage02.AllowedFactView;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage02.FlowSlice;
import com.linguan.codemd.stage02.ModelEvidenceSpan;
import com.linguan.codemd.stage02.OutcomePath;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public-seam RED contracts for Stage 03 capsule-local task evidence. */
class Stage03CapsuleTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void eachProviderTaskCarriesExactCapsuleProofFactsOutcomesAndEvidenceSpans() throws Exception {
        Scenario scenario = scenario();
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.validProvider(scenario.stage02());

        new Stage03Generator().generate(scenario.request(), provider);

        assertEquals(scenario.stage02().flowSlices().size() * 2, provider.tasks().size());
        for (FlowModelTask task : provider.tasks()) {
            JsonNode taskRoot = JSON.readTree(task.inputJson());
            FlowSlice flow = scenario.stage02().flowSlices().stream()
                    .filter(candidate -> candidate.flowSliceId().equals(task.flowSliceId())).findFirst().orElseThrow();
            EvidenceCapsule capsule = scenario.stage02().evidenceCapsules().stream()
                    .filter(candidate -> candidate.evidenceCapsuleId().equals(task.evidenceCapsuleId())
                            && candidate.flowSliceId().equals(task.flowSliceId()))
                    .findFirst().orElseThrow();
            JsonNode flowInput = taskRoot.path("flow");
            JsonNode capsuleInput = taskRoot.path("evidenceCapsule");
            assertTrue(taskRoot.isObject());
            assertEquals(capsule.proofPackId(), capsuleInput.path("proofPackId").asText());

            Set<String> expectedFactIds = capsule.allowedFacts().stream()
                    .map(AllowedFactView::factId).collect(Collectors.toSet());
            Set<String> inputFactIds = textSet(capsuleInput.path("allowedFacts"), "factId");
            assertEquals(expectedFactIds, inputFactIds, "task must carry only this capsule's fact IDs");
            for (AllowedFactView fact : capsule.allowedFacts()) {
                JsonNode inputFact = findByText(capsuleInput.path("allowedFacts"), "factId", fact.factId());
                assertNotNull(inputFact);
                assertEquals(fact.kind(), inputFact.path("kind").asText());
                assertEquals(fact.atoms().stream().map(atom -> atom.atomId()).collect(Collectors.toSet()),
                        textSet(inputFact.path("atoms"), "atomId"));
                fact.atoms().forEach(atom -> assertEquals(atom.proofId(),
                        findByText(inputFact.path("atoms"), "atomId", atom.atomId()).path("proofId").asText()));
            }

            Set<String> expectedProofIds = flow.outcomePaths().stream()
                    .flatMap(outcome -> outcome.requiredProofIds().stream()).collect(Collectors.toSet());
            Set<String> inputProofIds = textSet(flowInput.path("outcomes"), "requiredProofIds");
            assertEquals(expectedProofIds, inputProofIds,
                    "every outcome's required proof IDs must remain in the flow task");
            for (OutcomePath outcome : flow.outcomePaths()) {
                JsonNode inputOutcome = findByText(flowInput.path("outcomes"), "outcomePathId",
                        outcome.outcomePathId());
                assertNotNull(inputOutcome);
                assertEquals(Set.copyOf(outcome.requiredProofIds()),
                        textSet(inputOutcome.path("requiredProofIds"), null));
            }

            Set<String> expectedSpanIds = capsule.modelEvidenceSpans().stream()
                    .map(ModelEvidenceSpan::modelEvidenceSpanId).collect(Collectors.toSet());
            Set<String> inputSpanIds = textSet(capsuleInput.path("modelEvidenceSpans"), "modelEvidenceSpanId");
            assertEquals(expectedSpanIds, inputSpanIds, "task must not receive a foreign capsule span");
            for (ModelEvidenceSpan span : capsule.modelEvidenceSpans()) {
                JsonNode inputSpan = findByText(capsuleInput.path("modelEvidenceSpans"),
                        "modelEvidenceSpanId", span.modelEvidenceSpanId());
                assertNotNull(inputSpan);
                assertEquals(span.sourceFileSha256(), inputSpan.path("sourceFileSha256").asText());
                assertEquals(span.excerptSha256(), inputSpan.path("excerptSha256").asText());
                assertEquals(span.excerpt(), inputSpan.path("excerpt").asText());
                assertEquals(span.locator().path(), inputSpan.path("locator").path("path").asText());
            }

            Set<String> foreignSpanIds = scenario.stage02().evidenceCapsules().stream()
                    .filter(other -> !other.evidenceCapsuleId().equals(capsule.evidenceCapsuleId()))
                    .flatMap(other -> other.modelEvidenceSpans().stream())
                    .map(ModelEvidenceSpan::modelEvidenceSpanId).collect(Collectors.toSet());
            foreignSpanIds.forEach(spanId -> assertFalse(inputSpanIds.contains(spanId)));
        }
    }

    private static JsonNode findByText(JsonNode values, String field, String expected) {
        if (!values.isArray()) {
            return null;
        }
        for (JsonNode value : values) {
            if (field == null || expected.equals(value.path(field).asText())) {
                return value;
            }
        }
        return null;
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

    private static Scenario scenario() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-capsule-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02));
        return new Scenario(request, stage02);
    }

    private record Scenario(Stage03Request request, Stage02Result stage02) {
    }
}
