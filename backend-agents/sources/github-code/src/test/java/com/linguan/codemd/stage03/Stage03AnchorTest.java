package com.linguan.codemd.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.stage01.CodeFact;
import com.linguan.codemd.stage01.FlowEntryView;
import com.linguan.codemd.stage01.FlowNodeView;
import com.linguan.codemd.stage01.Proof;
import com.linguan.codemd.stage01.ProofNode;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01FlowView;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.AllowedAtomView;
import com.linguan.codemd.stage02.AllowedFactView;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage02.FlowSlice;
import com.linguan.codemd.stage02.FlowStep;
import com.linguan.codemd.stage02.OutcomePath;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public-seam RED contract: Stage 03 anchors must bind proven source identity. */
class Stage03AnchorTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ANCHOR_KINDS =
            Set.of("FLOW", "REQUEST", "RECORD", "RESULT", "OUTCOME", "ACTIVITY");

    @Test
    void taskAndInterpretationAnchorsCarryProvenStage01Stage02IdentityInsteadOfSyntheticFlowHashes()
            throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-anchor-"));
        Stage01Analyzer analyzer = new Stage01Analyzer();
        Stage01Result stage01 = analyzer.analyze(Stage03Fixtures.stage01Request(root));
        Stage01FlowView flowView = analyzer.flowView(stage01);
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(
                Stage03Fixtures.stage01Request(root), stage01.stage01ResultId(),
                new com.linguan.codemd.stage02.Stage02ResourceBudget(128, 64, 20_000, 40_000, 128,
                        64, 16_384, 262_144, 256));
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02));
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.validProvider(stage02);
        Stage03Result result = new Stage03Generator().generate(request, provider);

        Map<String, FlowInterpretationResult> interpretations = result.flowInterpretations().stream()
                .collect(Collectors.toMap(FlowInterpretationResult::flowSliceId, value -> value));
        assertEquals(stage02.flowSlices().size() * 2, provider.tasks().size());
        for (FlowModelTask task : provider.tasks()) {
            FlowSlice flow = stage02.flowSlices().stream()
                    .filter(candidate -> candidate.flowSliceId().equals(task.flowSliceId())).findFirst().orElseThrow();
            FlowEntryView entry = flowView.entries().stream()
                    .filter(candidate -> candidate.entryId().equals(flow.entryId())).findFirst().orElseThrow();
            EvidenceCapsule capsule = stage02.evidenceCapsules().stream()
                    .filter(candidate -> candidate.evidenceCapsuleId().equals(task.evidenceCapsuleId())
                            && candidate.flowSliceId().equals(task.flowSliceId()))
                    .findFirst().orElseThrow();
            Map<String, String> anchorKeys = anchors(JSON.readTree(task.inputJson()).path("anchors"));
            assertEquals(ANCHOR_KINDS, anchorKeys.keySet(), "task must expose all typed anchor kinds");
            Map<String, Set<String>> provenTokens = provenTokens(stage01, flowView, flow, capsule, entry);
            for (String kind : ANCHOR_KINDS) {
                String key = anchorKeys.get(kind);
                assertFalse(key.isBlank());
                assertNotSynthetic(key, flow.flowSliceId(), kind);
                assertTrue(provenTokens.get(kind).stream().anyMatch(key::contains),
                        () -> kind + " anchor must carry a proven source identity/value: " + key);
            }

            FlowInterpretationResult interpretation = interpretations.get(task.flowSliceId());
            assertTrue(interpretation.admittedMeanings().stream()
                            .map(AdmittedFlowMeaning::anchorKey).allMatch(anchorKeys::containsValue),
                    "admitted meanings must bind the same task anchor keys");
            assertTrue(interpretation.technicalFallbacks().stream()
                            .map(TechnicalDisplayResolution::anchorKey).allMatch(anchorKeys::containsValue),
                    "technical fallbacks must bind the same task anchor keys");
            assertTrue(interpretation.technicalFallbacks().stream()
                            .map(TechnicalDisplayResolution::resolvedDisplay)
                            .allMatch(display -> task.inputJson().contains(display)),
                    "technical fallback displays must be represented by the task's proven bindings");
        }
    }

    private static Map<String, String> anchors(JsonNode values) {
        Map<String, String> result = new HashMap<>();
        assertTrue(values.isArray());
        for (JsonNode value : values) {
            result.put(value.path("anchorKind").asText(), value.path("anchorKey").asText());
        }
        return Map.copyOf(result);
    }

    private static Map<String, Set<String>> provenTokens(Stage01Result stage01, Stage01FlowView view, FlowSlice flow,
                                                          EvidenceCapsule capsule, FlowEntryView entry) {
        Map<String, Set<String>> result = new HashMap<>();
        result.put("FLOW", new HashSet<>(List.of(flow.entryId(), flow.rootNodeId(), entry.entryId(),
                entry.methodNodeId(), entry.httpMethod(), entry.route())));
        result.get("FLOW").addAll(entry.routeNodeIds());

        result.put("REQUEST", new HashSet<>());
        result.put("RESULT", new HashSet<>());
        result.put("RECORD", new HashSet<>());
        result.put("ACTIVITY", new HashSet<>());
        Map<String, CodeFact> sourceFacts = stage01.provenSourceFacts().provenFactSet().codeFacts().stream()
                .collect(Collectors.toMap(CodeFact::factId, value -> value));
        Map<String, Proof> proofs = stage01.provenSourceFacts().proofPack().proofs().stream()
                .collect(Collectors.toMap(Proof::proofId, value -> value));
        Map<String, ProofNode> proofNodes = stage01.provenSourceFacts().proofPack().nodes().stream()
                .collect(Collectors.toMap(ProofNode::proofNodeId, value -> value));
        Map<String, FlowNodeView> nodes = view.nodes().stream()
                .collect(Collectors.toMap(FlowNodeView::nodeId, value -> value));
        for (AllowedFactView fact : capsule.allowedFacts()) {
            String anchorKind = switch (fact.kind()) {
                case "HTTP_ENTRY" -> "REQUEST";
                case "INVENTORY_LOAD" -> "RECORD";
                case "SUCCESS_RESULT" -> "RESULT";
                default -> "ACTIVITY";
            };
            Set<String> tokens = result.get(anchorKind);
            tokens.add(fact.factId());
            tokens.add(fact.kind());
            CodeFact sourceFact = sourceFacts.get(fact.factId());
            if (sourceFact != null) {
                tokens.addAll(sourceFact.subjectNodeIds());
            }
            for (AllowedAtomView atom : fact.atoms()) {
                tokens.add(atom.atomId());
                tokens.add(atom.role());
                tokens.add(atom.name());
                tokens.add(atom.proofId());
                if (atom.value() != null) {
                    tokens.add(atom.value().canonical());
                }
                Proof proof = proofs.get(atom.proofId());
                if (proof != null) {
                    tokens.add(proof.proofId());
                    tokens.add(proof.rootProofNodeId());
                    tokens.addAll(proof.requiredProofNodeIds());
                    for (String nodeId : proof.requiredProofNodeIds()) {
                        ProofNode proofNode = proofNodes.get(nodeId);
                        if (proofNode != null) {
                            tokens.add(proofNode.repositoryNodeId());
                            tokens.add(proofNode.proofNodeId());
                            tokens.add(proofNode.spanSha256());
                            FlowNodeView flowNode = nodes.get(proofNode.repositoryNodeId());
                            if (flowNode != null && flowNode.canonicalValue() != null) {
                                tokens.add(flowNode.canonicalValue());
                            }
                        }
                    }
                }
            }
        }
        for (FlowStep step : flow.sharedSteps()) {
            result.get("ACTIVITY").add(step.flowStepId());
            result.get("ACTIVITY").addAll(step.repositoryNodeIds());
            result.get("ACTIVITY").addAll(step.factIds());
            result.get("ACTIVITY").addAll(step.atomIds());
        }

        Set<String> outcomes = new HashSet<>();
        for (OutcomePath outcome : flow.outcomePaths()) {
            outcomes.add(outcome.outcomePathId());
            outcomes.add(outcome.terminalNodeId());
            outcomes.add(outcome.terminalKind());
            outcomes.addAll(outcome.terminalFactIds());
            outcomes.addAll(outcome.requiredAtomIds());
            outcomes.addAll(outcome.requiredProofIds());
            outcome.decisions().forEach(decision -> {
                outcomes.add(decision.guardNodeId());
                outcomes.add(decision.conditionAtomId());
                outcomes.add(decision.polarity());
                outcomes.add(decision.normalizedCondition());
            });
        }
        result.put("OUTCOME", outcomes);

        for (FlowStep step : flow.sharedSteps()) {
            for (String nodeId : step.repositoryNodeIds()) {
                FlowNodeView node = nodes.get(nodeId);
                if (node != null) {
                    result.get("ACTIVITY").add(node.nodeId());
                    if (node.canonicalValue() != null) {
                        result.get("ACTIVITY").add(node.canonicalValue());
                    }
                }
            }
        }
        return result.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                value -> Set.copyOf(value.getValue())));
    }

    private static void assertNotSynthetic(String actual, String flowSliceId, String kind) {
        String synthetic = "anchor:" + kind.toLowerCase() + ":" + sha256(flowSliceId + "\n" + kind);
        assertTrue(!synthetic.equals(actual), "anchor may not be only a flow-id/kind hash: " + actual);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
