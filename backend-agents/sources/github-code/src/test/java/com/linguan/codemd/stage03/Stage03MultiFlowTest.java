package com.linguan.codemd.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.stage01.CaptureProof;
import com.linguan.codemd.stage01.DeclaredFile;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.InventoryScope;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01FlowView;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02ResourceBudget;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage02.AllowedFactView;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage02.FlowSlice;
import com.linguan.codemd.stage02.ModelEvidenceSpan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public Stage 01 -> Stage 02 tracer for a genuinely independent second flow. */
class Stage03MultiFlowTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void independentControllersCompileIntoTwoFlowsAndEvidenceCapsules() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-independent-multiflow-"));
        Stage01Request stage01Request = independentTwoFlowRequest(root);

        Stage01Analyzer analyzer = new Stage01Analyzer();
        Stage01Result stage01 = analyzer.analyze(stage01Request);
        Stage01FlowView flowView = analyzer.flowView(stage01);
        assertEquals(Set.of("/reservations", "/shipments"), flowView.entries().stream()
                .map(entry -> entry.route()).collect(java.util.stream.Collectors.toSet()),
                "the frozen inventory must expose two independently implemented HTTP entries");

        Stage02Request stage02Request = Stage03Fixtures.stage02Request(stage01Request,
                stage01.stage01ResultId(), defaultStage02Budget());
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);

        assertAll(
                () -> assertEquals(2, stage02.flowSlices().size(),
                        () -> "each independently implemented entry must compile as its own FlowSlice; "
                                + "dispositions=" + stage02.entryDispositions()
                                + ", flowGaps=" + stage02.flowGaps()),
                () -> assertEquals(2, stage02.evidenceCapsules().size(),
                        () -> "each compiled FlowSlice must have exactly one EvidenceCapsule; "
                                + "dispositions=" + stage02.entryDispositions()
                                + ", flowGaps=" + stage02.flowGaps()));
    }

    @Test
    void eachCompiledCapsuleGetsOneIsolatedTwoRoundProviderTask() throws Exception {
        MultiFlowScenario scenario = multiFlowScenario();
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.emptySelectionProvider(scenario.stage02());

        new Stage03Generator().generate(scenario.request(), provider);

        assertEquals(4, provider.tasks().size(),
                "two compiled Capsules must produce exactly one R1/R2 pair each");
    }

    @Test
    void eachTaskCarriesOnlyItsCapsuleFlowLocalFactsProofsSpansAndGaps() throws Exception {
        MultiFlowScenario scenario = multiFlowScenario();
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.emptySelectionProvider(scenario.stage02());

        new Stage03Generator().generate(scenario.request(), provider);

        Map<String, FlowSlice> flows = scenario.stage02().flowSlices().stream().collect(Collectors.toMap(
                FlowSlice::flowSliceId, Function.identity()));
        Map<String, EvidenceCapsule> capsules = scenario.stage02().evidenceCapsules().stream().collect(Collectors.toMap(
                EvidenceCapsule::flowSliceId, Function.identity()));
        Map<String, List<FlowModelTask>> tasksByFlow = provider.tasks().stream().collect(Collectors.groupingBy(
                FlowModelTask::flowSliceId));
        assertEquals(flows.keySet(), tasksByFlow.keySet(),
                "every compiled Flow must have an isolated task identity");
        assertEquals(4, provider.tasks().size(), "two rounds for each of two Capsules");

        Set<String> allFlowIds = flows.keySet();
        Map<String, Set<String>> factsByFlow = new HashMap<>();
        Map<String, Set<String>> atomsByFlow = new HashMap<>();
        Map<String, Set<String>> proofsByFlow = new HashMap<>();
        Map<String, Set<String>> spansByFlow = new HashMap<>();
        Map<String, Set<String>> gapsByFlow = new HashMap<>();
        for (String flowId : allFlowIds) {
            FlowSlice flow = flows.get(flowId);
            EvidenceCapsule capsule = capsules.get(flowId);
            assertEquals(flowId, capsule.flowSliceId());
            factsByFlow.put(flowId, capsule.allowedFacts().stream().map(AllowedFactView::factId)
                    .collect(Collectors.toSet()));
            atomsByFlow.put(flowId, capsule.allowedFacts().stream().flatMap(fact -> fact.atoms().stream())
                    .map(com.linguan.codemd.stage02.AllowedAtomView::atomId).collect(Collectors.toSet()));
            proofsByFlow.put(flowId, capsule.allowedFacts().stream().flatMap(fact -> fact.atoms().stream())
                    .map(com.linguan.codemd.stage02.AllowedAtomView::proofId)
                    .collect(Collectors.toSet()));
            spansByFlow.put(flowId, capsule.modelEvidenceSpans().stream().map(ModelEvidenceSpan::modelEvidenceSpanId)
                    .collect(Collectors.toSet()));
            Set<String> gaps = new HashSet<>(flow.gapIds());
            capsule.allowedGaps().stream().map(com.linguan.codemd.stage02.AllowedGapView::gapId).forEach(gaps::add);
            gapsByFlow.put(flowId, gaps);
        }

        for (Map.Entry<String, List<FlowModelTask>> entry : tasksByFlow.entrySet()) {
            String flowId = entry.getKey();
            FlowSlice flow = flows.get(flowId);
            EvidenceCapsule capsule = capsules.get(flowId);
            List<FlowModelTask> tasks = entry.getValue();
            assertEquals(2, tasks.size(), "one R1/R2 pair per Capsule");
            assertEquals(1, tasks.stream().map(FlowModelTask::taskSpecId).distinct().count(),
                    "R1/R2 share one Capsule task identity");
            assertEquals(1, tasks.stream().map(FlowModelTask::isolatedSessionKey).distinct().count(),
                    "R1/R2 share one isolated session");

            Set<String> expectedFactIds = factsByFlow.get(flowId);
            Set<String> expectedAtomIds = atomsByFlow.get(flowId);
            Set<String> expectedProofIds = proofsByFlow.get(flowId);
            Set<String> expectedSpanIds = spansByFlow.get(flowId);
            Set<String> expectedGapIds = gapsByFlow.get(flowId);
            for (FlowModelTask task : tasks) {
                JsonNode root = JSON.readTree(task.inputJson());
                assertEquals(flowId, root.path("flowSliceId").asText());
                assertEquals(capsule.evidenceCapsuleId(), root.path("evidenceCapsuleId").asText());
                assertEquals(flowId, root.path("flow").path("flowSliceId").asText());
                assertEquals(capsule.evidenceCapsuleId(), root.path("evidenceCapsule")
                        .path("evidenceCapsuleId").asText());
                assertEquals(expectedFactIds, textSet(root.path("evidenceCapsule").path("allowedFacts"), "factId"));
                assertEquals(expectedAtomIds, nestedTextSet(root.path("evidenceCapsule").path("allowedFacts"),
                        "atoms", "atomId"));
                assertEquals(expectedProofIds, nestedTextSet(root.path("evidenceCapsule").path("allowedFacts"),
                        "atoms", "proofId"));
                assertEquals(expectedAtomIds, textSet(root.path("flow").path("atomIds"), null));
                assertEquals(Set.copyOf(flow.factIds()), textSet(root.path("flow").path("factIds"), null));
                assertEquals(Set.copyOf(flow.gapIds()), textSet(root.path("flow").path("gapIds"), null));
                assertEquals(expectedProofIds, nestedTextSet(root.path("flow").path("outcomes"),
                        "requiredProofIds", null));
                assertEquals(expectedSpanIds, textSet(root.path("evidenceCapsule").path("modelEvidenceSpans"),
                        "modelEvidenceSpanId"));
                assertEquals(Set.copyOf(capsule.outcomePathIds()),
                        textSet(root.path("evidenceCapsule").path("outcomePathIds"), null));
                assertEquals(expectedGapIds, union(textSet(root.path("flow").path("gapIds"), null),
                        textSet(root.path("evidenceCapsule").path("allowedGaps"), "gapId")));

                for (String foreignFlowId : allFlowIds) {
                    if (foreignFlowId.equals(flowId)) {
                        continue;
                    }
                    assertDisjoint(factsByFlow.get(foreignFlowId), expectedFactIds,
                            "foreign fact IDs must not enter " + flowId);
                    assertDisjoint(atomsByFlow.get(foreignFlowId), expectedAtomIds,
                            "foreign atom IDs must not enter " + flowId);
                    assertDisjoint(proofsByFlow.get(foreignFlowId), expectedProofIds,
                            "foreign proof IDs must not enter " + flowId);
                    assertDisjoint(spansByFlow.get(foreignFlowId), expectedSpanIds,
                            "foreign span IDs must not enter " + flowId);
                    assertDisjoint(gapsByFlow.get(foreignFlowId), expectedGapIds,
                            "foreign Gap IDs must not enter " + flowId);
                    String taskInput = task.inputJson();
                    for (String foreignId : union(union(union(factsByFlow.get(foreignFlowId),
                            atomsByFlow.get(foreignFlowId)), proofsByFlow.get(foreignFlowId)),
                            union(spansByFlow.get(foreignFlowId), gapsByFlow.get(foreignFlowId)))) {
                        org.junit.jupiter.api.Assertions.assertFalse(taskInput.contains(foreignId),
                                "task input must not serialize foreign ID " + foreignId);
                    }
                }
            }
        }
    }

    @Test
    void sameProvenInventoryTableMergesOneRecordObjectWhileDistinctTypesStaySeparate() throws Exception {
        MultiFlowScenario scenario = multiFlowScenario();
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.emptySelectionProvider(scenario.stage02());

        Stage03Result result = new Stage03Generator().generate(scenario.request(), provider);
        List<BusinessObject> records = result.repositoryBusinessModel().objects().stream()
                .filter(object -> "RECORD".equals(object.objectKind())).toList();
        List<BusinessObject> requests = result.repositoryBusinessModel().objects().stream()
                .filter(object -> "REQUEST".equals(object.objectKind())).toList();
        Set<String> flowIds = scenario.stage02().flowSlices().stream()
                .map(com.linguan.codemd.stage02.FlowSlice::flowSliceId).collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(1, records.size(),
                        "the same proven SQL table must be one repository RECORD object"),
                () -> assertEquals(2, requests.stream().map(BusinessObject::anchorKey).distinct().count(),
                        "different proven request types must remain distinct hard anchors"),
                () -> assertEquals(flowIds, result.repositoryBusinessModel().flows().stream()
                        .filter(flow -> flow.objectIds().stream().anyMatch(id -> records.stream()
                                .map(BusinessObject::objectId).anyMatch(id::equals)))
                        .map(BusinessFlow::flowSliceId).collect(Collectors.toSet()),
                        "both Flow records must retain a reference to the merged RECORD object"),
                () -> assertFalse(result.repositoryBusinessModel().relations().isEmpty(),
                        "typed proven object relations must be observable for the two flows"),
                () -> assertEquals(result.repositoryBusinessModel().accounting().ownedAtomIds().size(),
                        new HashSet<>(result.repositoryBusinessModel().accounting().ownedAtomIds()).size(),
                        "M6 atom ownership must be unique"));
    }

    @Test
    void pendingQuestionsKeepFlowLocalGapProvenance() throws Exception {
        MultiFlowScenario scenario = multiFlowScenario();
        Stage03Fixtures.ScriptedModelProvider provider = Stage03Fixtures.emptySelectionProvider(scenario.stage02());

        Stage03Result result = new Stage03Generator().generate(scenario.request(), provider);
        Map<String, String> gapOwner = new HashMap<>();
        Set<String> expectedGaps = new HashSet<>();
        for (FlowSlice flow : scenario.stage02().flowSlices()) {
            for (String gapId : flow.gapIds()) {
                assertTrue(gapOwner.putIfAbsent(gapId, flow.flowSliceId()) == null,
                        "a FlowGap must have one owning Flow");
                expectedGaps.add(gapId);
            }
            scenario.stage02().evidenceCapsules().stream()
                    .filter(capsule -> capsule.flowSliceId().equals(flow.flowSliceId()))
                    .flatMap(capsule -> capsule.allowedGaps().stream())
                    .map(com.linguan.codemd.stage02.AllowedGapView::gapId)
                    .forEach(gapId -> {
                        String previousOwner = gapOwner.putIfAbsent(gapId, flow.flowSliceId());
                        assertTrue(previousOwner == null || previousOwner.equals(flow.flowSliceId()),
                                "a Capsule Gap must not be shared across Flow owners");
                        expectedGaps.add(gapId);
                    });
        }

        Set<String> observedGaps = result.repositoryBusinessModel().pendingQuestions().stream()
                .flatMap(question -> question.sourceGapIds().stream()).collect(Collectors.toSet());
        assertEquals(expectedGaps, observedGaps,
                "repository pending questions must preserve every local Gap and no foreign Gap");
        result.repositoryBusinessModel().pendingQuestions().forEach(question -> {
            Set<String> owners = question.sourceGapIds().stream().map(gapOwner::get)
                    .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            assertEquals(1, owners.size(), "one pending question must not mix Flow-local Gap owners");
        });
    }

    private static MultiFlowScenario multiFlowScenario() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-independent-multiflow-run-"));
        Stage01Request stage01Request = independentTwoFlowRequest(root);
        Stage01Result stage01 = new Stage01Analyzer().analyze(stage01Request);
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(stage01Request,
                stage01.stage01ResultId(), defaultStage02Budget());
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        assertEquals(2, stage02.flowSlices().size(), "provider run requires the non-vacuous two-flow fixture");
        assertEquals(2, stage02.evidenceCapsules().size(), "provider run requires two Capsules");
        return new MultiFlowScenario(Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02)), stage02);
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

    private static Set<String> nestedTextSet(JsonNode values, String nestedField, String field) {
        if (!values.isArray()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (JsonNode value : values) {
            JsonNode nested = value.get(nestedField);
            if (nested == null || !nested.isArray()) {
                continue;
            }
            for (JsonNode child : nested) {
                if (field == null) {
                    result.add(child.asText());
                } else if (child.has(field)) {
                    result.add(child.path(field).asText());
                }
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new HashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private static void assertDisjoint(Set<String> foreign, Set<String> local, String message) {
        Set<String> overlap = new HashSet<>(foreign);
        overlap.retainAll(local);
        assertTrue(overlap.isEmpty(), message + ": " + overlap);
    }

    private static Stage01Request independentTwoFlowRequest(Path root) throws IOException {
        Stage01Request base = Stage03Fixtures.stage01Request(root);
        List<SourceFile> additional = List.of(
                new SourceFile("src/main/java/example/shipping/ShipmentController.java", """
                        package example.shipping;

                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.RequestBody;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping("/shipments")
                        final class ShipmentController {
                          private final ShipmentService service;
                          ShipmentController(ShipmentService service) { this.service = service; }
                          @PostMapping
                          ShipmentReceipt ship(@RequestBody ShipmentRequest request) {
                            return service.ship(request.sku(), request.quantity());
                          }
                        }
                        record ShipmentRequest(String sku, int quantity) {}
                        """),
                new SourceFile("src/main/java/example/shipping/ShipmentService.java", """
                        package example.shipping;

                        import org.springframework.stereotype.Service;

                        @Service
                        final class ShipmentService {
                          private final ShipmentMapper mapper;
                          ShipmentService(ShipmentMapper mapper) { this.mapper = mapper; }
                          ShipmentReceipt ship(String sku, int quantity) {
                            if (quantity <= 0) throw new InvalidShipmentQuantity();
                            ShipmentRow shipment = mapper.findBySku(sku);
                            int available = shipment.onHand() - shipment.reserved();
                            if (available < quantity) throw new InsufficientShipment();
                            int updateCount = mapper.addReservation(sku, quantity, shipment.version());
                            if (updateCount != 1) throw new ShipmentConflict();
                            return new ShipmentReceipt(sku, quantity);
                          }
                        }
                        record ShipmentRow(String sku, int onHand, int reserved, int version) {}
                        record ShipmentReceipt(String sku, int quantity) {}
                        final class InvalidShipmentQuantity extends RuntimeException {}
                        final class InsufficientShipment extends RuntimeException {}
                        final class ShipmentConflict extends RuntimeException {}
                        """),
                new SourceFile("src/main/java/example/shipping/ShipmentMapper.java", """
                        package example.shipping;

                        import org.apache.ibatis.annotations.Mapper;
                        import org.apache.ibatis.annotations.Param;

                        @Mapper
                        interface ShipmentMapper {
                          ShipmentRow findBySku(@Param("sku") String sku);
                          int addReservation(@Param("sku") String sku,
                                             @Param("quantity") int quantity,
                                             @Param("version") int version);
                        }
                        """),
                new SourceFile("src/main/resources/mappers/ShipmentMapper.xml", """
                        <?xml version="1.0" encoding="UTF-8" ?>
                        <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
                          "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
                        <mapper namespace="example.shipping.ShipmentMapper">
                          <select id="findBySku" resultType="example.shipping.ShipmentRow">
                            SELECT sku, on_hand AS onHand, reserved_qty AS reserved, version
                            FROM inventory
                            WHERE sku = #{sku}
                          </select>
                          <update id="addReservation">
                            UPDATE inventory
                            SET reserved_qty = reserved_qty + #{quantity}, version = version + 1
                            WHERE sku = #{sku} AND version = #{version}
                          </update>
                        </mapper>
                        """));

        List<DeclaredFile> files = new ArrayList<>(base.frozenRepositoryRequest().files());
        for (SourceFile source : additional) {
            Path path = root.resolve(source.path());
            Files.createDirectories(path.getParent());
            Files.writeString(path, source.contents(), StandardCharsets.UTF_8);
            byte[] bytes = source.contents().getBytes(StandardCharsets.UTF_8);
            files.add(new DeclaredFile(source.path(), source.path().endsWith(".xml") ? "XML" : "JAVA",
                    bytes.length, sha256(bytes), "UTF-8"));
        }

        String inventorySha256 = inventorySha256(files);
        String receiptSha256 = receiptSha256(base.frozenRepositoryRequest().captureProof(), inventorySha256);
        FrozenRepositoryRequest frozen = new FrozenRepositoryRequest(
                base.frozenRepositoryRequest().origin(),
                new CaptureProof(base.frozenRepositoryRequest().captureProof().kind(),
                        base.frozenRepositoryRequest().captureProof().receiptId(),
                        base.frozenRepositoryRequest().captureProof().boundRepositoryUrl(),
                        base.frozenRepositoryRequest().captureProof().boundRevision(),
                        inventorySha256, receiptSha256),
                root,
                new InventoryScope(base.frozenRepositoryRequest().inventoryScope().kind(),
                        base.frozenRepositoryRequest().inventoryScope().scopeRoot(), files.size()),
                files,
                base.frozenRepositoryRequest().verificationPolicyId(),
                base.frozenRepositoryRequest().resourceBudget(),
                base.frozenRepositoryRequest().capabilityProfileRef());
        return new Stage01Request(base.schemaVersion(), frozen, base.gapExpectationProfileRef());
    }

    private static Stage02ResourceBudget defaultStage02Budget() {
        return new Stage02ResourceBudget(128, 64, 20_000, 40_000, 128, 64,
                16_384, 262_144, 256);
    }

    private static String inventorySha256(List<DeclaredFile> files) {
        List<DeclaredFile> sorted = files.stream()
                .sorted(Comparator.comparing(DeclaredFile::path)).toList();
        StringBuilder canonical = new StringBuilder("{\"files\":[");
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) {
                canonical.append(',');
            }
            DeclaredFile file = sorted.get(index);
            canonical.append("{\"mediaType\":\"").append(file.mediaType())
                    .append("\",\"path\":\"").append(file.path())
                    .append("\",\"sha256\":\"").append(file.sha256())
                    .append("\",\"sizeBytes\":").append(file.sizeBytes())
                    .append(",\"textEncoding\":\"").append(file.textEncoding()).append("\"}");
        }
        canonical.append("],\"scope\":{\"declaredPathCount\":").append(files.size())
                .append(",\"kind\":\"BOUNDED_PATH_SET\",\"scopeRoot\":\".\"}}");
        return sha256(("declared-inventory-v1\n" + canonical).getBytes(StandardCharsets.UTF_8));
    }

    private static String receiptSha256(CaptureProof original, String inventorySha256) {
        String canonical = "{\"boundRepositoryUrl\":\"" + original.boundRepositoryUrl()
                + "\",\"boundRevision\":\"" + original.boundRevision()
                + "\",\"inventorySha256\":\"" + inventorySha256
                + "\",\"kind\":\"" + original.kind() + "\",\"receiptId\":\""
                + original.receiptId() + "\"}";
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record SourceFile(String path, String contents) {
    }

    private record MultiFlowScenario(Stage03Request request, Stage02Result stage02) {
    }
}
