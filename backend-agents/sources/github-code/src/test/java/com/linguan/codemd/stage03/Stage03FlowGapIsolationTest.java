package com.linguan.codemd.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.CaptureProof;
import com.linguan.codemd.stage01.DeclaredFile;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.InventoryScope;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01FlowView;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.FlowGap;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02ResourceBudget;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public-seam regression for repository FlowGap versus compiled Capsule isolation. */
class Stage03FlowGapIsolationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void incompleteThirdEntryProducesBlockingFlowGapWithoutFlowOrCapsule() throws Exception {
        Scenario scenario = scenario();

        assertEquals(2, scenario.stage02().flowSlices().size());
        assertEquals(2, scenario.stage02().evidenceCapsules().size());
        assertFalse(scenario.foreignGapId().isBlank());
        assertTrue(scenario.stage02().flowGaps().stream()
                .filter(gap -> gap.entryId().equals(scenario.incompleteEntryId()))
                .allMatch(FlowGap::blocking));
        assertTrue(scenario.stage02().entryDispositions().stream()
                .anyMatch(disposition -> disposition.entryId().equals(scenario.incompleteEntryId())
                        && disposition.flowSliceId() == null && !disposition.gapIds().isEmpty()));
    }

    @Test
    void compiledTasksAndInterpretationsExcludeIncompleteEntryGap() throws Exception {
        Scenario scenario = scenario();
        Stage03Fixtures.ScriptedModelProvider provider =
                Stage03Fixtures.emptySelectionProvider(scenario.stage02());

        Stage03Result result = new Stage03Generator().generate(scenario.request(), provider);

        assertEquals(4, provider.tasks().size(), "two rounds for each of the two compiled Capsules");
        for (FlowModelTask task : provider.tasks()) {
            JsonNode input = JSON.readTree(task.inputJson());
            assertFalse(task.inputJson().contains(scenario.foreignGapId()),
                    "a compiled Flow task must not expose the incomplete entry Gap");
            Set<String> taskGapIds = textSet(input.path("flow").path("gapIds"));
            taskGapIds.addAll(textSet(input.path("evidenceCapsule").path("allowedGaps"), "gapId"));
            assertFalse(taskGapIds.contains(scenario.foreignGapId()));
        }
        result.flowInterpretations().forEach(interpretation -> {
            assertTrue(interpretation.interpretationGaps().stream()
                    .noneMatch(gap -> scenario.foreignGapId().equals(gap.sourceGapId())),
                    "interpretation gaps must remain owned by their compiled Flow");
            assertTrue(interpretation.proposalDispositions().stream()
                    .noneMatch(disposition -> disposition.retainedBasisGapIds()
                            .contains(scenario.foreignGapId())));
        });
        assertTrue(result.repositoryBusinessModel().pendingQuestions().stream()
                .flatMap(question -> question.sourceGapIds().stream())
                .anyMatch(scenario.foreignGapId()::equals),
                "the repository projection must preserve the incomplete-entry Gap");
        assertTrue(result.repositoryBusinessModel().accounting().ownedGapIds()
                .contains(scenario.foreignGapId()),
                "repository Gap accounting must retain the incomplete-entry Gap");
    }

    @Test
    void aProviderQuestionCannotReferenceAnotherEntryGap() throws Exception {
        Scenario base = scenario();
        RegistryBundle registries = registryAllowingThirdEntryReason(base.request().registryBundle(),
                base.stage02(), base.incompleteEntryId());
        Stage03Request request = Stage03Fixtures.stage03Request(base.stage02Request(),
                base.stage02().stage02ResultId(), registries);
        Stage03Fixtures.ScriptedModelProvider scripted = Stage03Fixtures.emptySelectionProvider(base.stage02(),
                response -> foreignReviewOnly(response, base.foreignGapId()));
        StructuredModelProvider provider = task -> {
            ModelExecutionResult response = scripted.execute(task);
            if (task.flowInterpretationRound() != 1) {
                return response;
            }
            return new ModelExecutionResult(response.taskSpecId(), response.flowInterpretationRound(),
                    replaceQuestionGap(response.responseJson(), "basisGapIds", base.foreignGapId()),
                    response.observedRuntime(), response.startedReceiptId());
        };

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(request, provider));

        assertEquals(Stage03FailureCode.MODEL_RESPONSE_REFERENCE_INVALID, failure.code(),
                "foreign FlowGap references must fail closed at the public Stage03 seam");
    }

    private static Scenario scenario() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-flowgap-isolation-"));
        Stage01Request stage01Request = independentTwoFlowPlusIncompleteEntry(root);
        Stage01Analyzer analyzer = new Stage01Analyzer();
        Stage01Result stage01 = analyzer.analyze(stage01Request);
        Stage01FlowView flowView = analyzer.flowView(stage01);
        var incomplete = flowView.entries().stream().filter(entry -> "/third-reservations".equals(entry.route())).findFirst()
                .orElseThrow(() -> new AssertionError("third incomplete HTTP entry was not discovered"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(stage01Request,
                stage01.stage01ResultId(), defaultStage02Budget());
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        assertEquals(2, stage02.flowSlices().size(),
                "fixture precondition: only the two complete entries compile to FlowSlices");
        assertEquals(2, stage02.evidenceCapsules().size(),
                "fixture precondition: only the two complete entries receive Capsules");
        List<FlowGap> foreignGaps = stage02.flowGaps().stream()
                .filter(gap -> incomplete.entryId().equals(gap.entryId())).toList();
        assertFalse(foreignGaps.isEmpty(), "fixture precondition: incomplete entry must retain a FlowGap");
        assertTrue(foreignGaps.stream().allMatch(FlowGap::blocking));
        FlowGap foreignGap = foreignGaps.get(0);
        return new Scenario(Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02)), stage02Request, stage02,
                incomplete.entryId(), foreignGap.flowGapId());
    }

    private static RegistryBundle registryAllowingThirdEntryReason(RegistryBundle original,
                                                                    Stage02Result stage02,
                                                                    String incompleteEntryId) {
        List<QuestionEntry> questions = new ArrayList<>(original.questions().questions());
        String reason = stage02.flowGaps().stream().filter(gap -> incompleteEntryId.equals(gap.entryId()))
                .map(FlowGap::code).findFirst().orElseThrow();
        questions.add(new QuestionEntry("ASK_FOREIGN_FLOW_GAP", List.of(reason),
                "QUESTION_CONFIRM_MISSING_ROW_POLICY_V1"));
        QuestionRegistry expanded = new QuestionRegistry(original.questions().schemaVersion(),
                original.questions().registryId() + ":foreign-gap-test", "ignored-by-freeze", questions);
        return Stage03Registries.freeze(original.businessTerms(), original.technicalDisplays(), original.claims(),
                expanded, original.sentenceTemplates(), original.sectionOwnership());
    }

    private static String replaceQuestionGap(String response, String field, String foreignGapId) {
        try {
            JsonNode parsed = JSON.readTree(response);
            boolean changed = false;
            String arrayName = field.equals("basisGapIds") ? "proposals" : "reviews";
            for (JsonNode node : parsed.path(arrayName)) {
                if (node.isObject() && (field.equals("basisGapIds")
                        ? "P09".equals(node.path("proposalKey").asText())
                        : "P09".equals(node.path("proposalKey").asText()))) {
                    if (field.equals("basisGapIds")) {
                        ((ObjectNode) node).put("questionKey", "ASK_FOREIGN_FLOW_GAP");
                    }
                    ((ObjectNode) node).set(field, JSON.createArrayNode().add(foreignGapId));
                    changed = true;
                }
            }
            if (!changed) {
                ObjectNode root = (ObjectNode) parsed;
                if (field.equals("basisGapIds")) {
                    ObjectNode proposal = root.withArray("proposals").addObject();
                    proposal.put("proposalKey", "P09");
                    proposal.put("proposalType", "QUESTION_ONLY");
                    proposal.put("questionKey", "ASK_FOREIGN_FLOW_GAP");
                    proposal.putArray("basisGapIds").add(foreignGapId);
                } else {
                    ObjectNode review = root.withArray("reviews").addObject();
                    review.put("proposalKey", "P09");
                    review.put("decision", "NEEDS_EVIDENCE");
                    review.putArray("retainedBasisGapIds").add(foreignGapId);
                }
            }
            return JSON.writeValueAsString(parsed);
        } catch (IOException invalidResponse) {
            throw new AssertionError("scripted response is not JSON", invalidResponse);
        }
    }

    private static String foreignReviewOnly(String response, String foreignGapId) {
        try {
            JsonNode parsed = JSON.readTree(response);
            ObjectNode root = (ObjectNode) parsed;
            ArrayNode reviews = JSON.createArrayNode();
            reviews.addObject().put("proposalKey", "P09").put("decision", "NEEDS_EVIDENCE")
                    .putArray("retainedBasisGapIds").add(foreignGapId);
            root.set("reviews", reviews);
            return JSON.writeValueAsString(root);
        } catch (IOException invalidResponse) {
            throw new AssertionError("scripted response is not JSON", invalidResponse);
        }
    }

    private static Set<String> textSet(JsonNode values) {
        return textSet(values, null);
    }

    private static Set<String> textSet(JsonNode values, String field) {
        Set<String> result = new HashSet<>();
        if (!values.isArray()) {
            return result;
        }
        for (JsonNode value : values) {
            JsonNode selected = field == null ? value : value.get(field);
            if (selected == null) {
                continue;
            }
            if (selected.isArray()) {
                selected.forEach(item -> result.add(item.asText()));
            } else {
                result.add(selected.asText());
            }
        }
        return result;
    }

    private static Stage01Request independentTwoFlowPlusIncompleteEntry(Path root) throws IOException {
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
                        """),
                new SourceFile("src/main/java/example/inventory/ThirdReservationController.java", """
                        package example.inventory;

                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.RequestBody;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping("/third-reservations")
                        final class ThirdReservationController {
                          private final ReservationService service;
                          ThirdReservationController(ReservationService service) { this.service = service; }
                          @PostMapping
                          ReservationReceipt reserve(@RequestBody ReservationRequest request) {
                            return service.reserve(request.sku(), request.quantity());
                          }
                        }
                        """)
        );
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
        List<DeclaredFile> sorted = files.stream().sorted(Comparator.comparing(DeclaredFile::path)).toList();
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

    private record Scenario(Stage03Request request, Stage02Request stage02Request, Stage02Result stage02,
                            String incompleteEntryId, String foreignGapId) {
    }
}
