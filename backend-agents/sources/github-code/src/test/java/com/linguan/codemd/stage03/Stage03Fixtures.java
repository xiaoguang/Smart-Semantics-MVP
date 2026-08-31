package com.linguan.codemd.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.stage01.CapabilityProfileRef;
import com.linguan.codemd.stage01.CaptureProof;
import com.linguan.codemd.stage01.CodeFact;
import com.linguan.codemd.stage01.DeclaredFile;
import com.linguan.codemd.stage01.FactAtom;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.GapExpectationProfileRef;
import com.linguan.codemd.stage01.InventoryScope;
import com.linguan.codemd.stage01.Origin;
import com.linguan.codemd.stage01.ResourceBudget;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.EvidenceProjectionProfileRef;
import com.linguan.codemd.stage02.FlowCompilationProfileRef;
import com.linguan.codemd.stage02.FlowGap;
import com.linguan.codemd.stage02.FlowSlice;
import com.linguan.codemd.stage02.AllowedAtomView;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02ResourceBudget;
import com.linguan.codemd.stage02.Stage02Result;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.UnaryOperator;

/** Test-only frozen Stage 03 input and a provider that never calls a model. */
final class Stage03Fixtures {
    static final String FLOW_PROFILE_ID = "entry-rooted-sync-flow-v1";
    static final String FLOW_PROFILE_SHA256 = sha256Text(FLOW_PROFILE_ID + "\n");
    static final String EVIDENCE_PROFILE_ID = "model-evidence-projection-v1";
    static final String EVIDENCE_PROFILE_SHA256 = sha256Text(EVIDENCE_PROFILE_ID + "\n");
    static final String STAGE03_PROFILE_ID = "stage03-flow-meaning-v1";
    static final String STAGE03_PROFILE_SHA256 = sha256Text(STAGE03_PROFILE_ID + "\n");
    static final String KNOWLEDGE_PROFILE_ID = "repository-knowledge-v1";
    static final String KNOWLEDGE_PROFILE_SHA256 = sha256Text(KNOWLEDGE_PROFILE_ID + "\n");
    static final String NINE_SECTION_PROFILE_ID = "nine-section-reader-v1";
    static final String NINE_SECTION_PROFILE_SHA256 = sha256Text(NINE_SECTION_PROFILE_ID + "\n");
    static final String RESOURCE_ROOT = "stage01/reservation-v1";
    private static final String MANIFEST = RESOURCE_ROOT + "/fixture-manifest.properties";
    private static final String REPOSITORY_URL =
            "https://example.invalid/synthetic/inventory-reservation.git";
    private static final String REVISION = "synthetic-revision-1";
    private static final String RECEIPT_ID = "reservation-v1-manifest";
    private static final String CAPABILITY_PROFILE_ID = "java17-springmvc-mybatis-static-v0";
    private static final String CAPABILITY_PROFILE_SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String GAP_PROFILE_ID = "gap-expectation-profile-v1";
    private static final String GAP_PROFILE_SHA256 =
            "e63f976bba3fcc0acbc62c3b72f0a924d539d240d69ca86b7a598905fafc0f8a";
    private static final ObjectMapper JSON = new ObjectMapper();

    private Stage03Fixtures() {
    }

    static Path copyReservationSnapshot(Path temporaryDirectory) throws IOException {
        Path destination = temporaryDirectory.resolve("snapshot");
        Path resourceRoot = resourceRoot();
        for (FileSpec expected : expectedFiles()) {
            Path target = destination.resolve(expected.path());
            Files.createDirectories(target.getParent());
            Files.copy(resourceRoot.resolve(expected.path()), target);
        }
        return destination;
    }

    static Stage01Request stage01Request(Path root) {
        List<DeclaredFile> files = declaredFiles();
        String inventorySha256 = inventorySha256(files);
        String receiptSha256 = receiptSha256(inventorySha256);
        FrozenRepositoryRequest frozen = new FrozenRepositoryRequest(
                new Origin("SYNTHETIC_FIXTURE", REPOSITORY_URL, REVISION),
                new CaptureProof("SYNTHETIC_FIXTURE_MANIFEST", RECEIPT_ID,
                        REPOSITORY_URL, REVISION, inventorySha256, receiptSha256),
                root, new InventoryScope("BOUNDED_PATH_SET", ".", files.size()), files,
                "frozen-snapshot-v1", defaultStage01Budget(),
                new CapabilityProfileRef(CAPABILITY_PROFILE_ID, CAPABILITY_PROFILE_SHA256));
        return new Stage01Request("stage01-request-v1", frozen,
                new GapExpectationProfileRef(GAP_PROFILE_ID, GAP_PROFILE_SHA256));
    }

    static Stage02Request stage02Request(Path root) {
        Stage01Request stage01Request = stage01Request(root);
        Stage01Result stage01 = new Stage01Analyzer().analyze(stage01Request);
        return stage02Request(stage01Request, stage01.stage01ResultId(), defaultStage02Budget());
    }

    static Stage02Request stage02Request(Stage01Request stage01Request, String expectedStage01ResultId,
                                         Stage02ResourceBudget budget) {
        return new Stage02Request("stage02-request-v1", stage01Request, expectedStage01ResultId,
                new FlowCompilationProfileRef(FLOW_PROFILE_ID, FLOW_PROFILE_SHA256),
                new EvidenceProjectionProfileRef(EVIDENCE_PROFILE_ID, EVIDENCE_PROFILE_SHA256), budget);
    }

    static Stage02Result stage02Result(Path root) {
        Stage02Request request = stage02Request(root);
        return new Stage02Compiler().compile(request);
    }

    static Stage03Request stage03Request(Path root) {
        Stage02Request stage02Request = stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        return stage03Request(stage02Request, stage02.stage02ResultId(), registryBundle(stage02));
    }

    static Stage03Request stage03Request(Stage02Request stage02Request, String expectedStage02ResultId,
                                         RegistryBundle registries) {
        return new Stage03Request("stage03-request-v1", stage02Request, expectedStage02ResultId,
                registries,
                new FlowInterpretationProfileRef(STAGE03_PROFILE_ID, STAGE03_PROFILE_SHA256),
                new RepositoryKnowledgeProfileRef(KNOWLEDGE_PROFILE_ID, KNOWLEDGE_PROFILE_SHA256),
                new NineSectionGenerationProfileRef(NINE_SECTION_PROFILE_ID, NINE_SECTION_PROFILE_SHA256),
                new ModelRuntimePolicy("CODEX_SUBSCRIPTION", "LOGGED_IN_SESSION", "codex",
                        "gpt-5.6-luna", "xhigh", "read-only"),
                defaultStage03Budget());
    }

    /** Test-only frozen inventory with a genuinely independent second flow. */
    static Stage01Request independentTwoFlowRequest(Path root) throws IOException {
        Stage01Request base = stage01Request(root);
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
        String receiptSha256 = receiptSha256(inventorySha256);
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

    static RegistryBundle registryBundle(Stage02Result stage02) {
        Map<String, List<String>> atoms = atomsByFactKind(stage02);
        List<BusinessTermEntry> terms = List.of(
                term("TERM_RESERVATION_FLOW", "FLOW", "库存预留", atoms.get("HTTP_ENTRY"),
                        "FLOW_ROUTE_HANDLER_V1"),
                term("TERM_RESERVATION_REQUEST", "REQUEST", "预留请求", oneAtom(atoms.get("HTTP_ENTRY")),
                        "BOUND_TYPE_SIMPLE_NAME_V1"),
                term("TERM_INVENTORY_RECORD", "RECORD", "库存记录", atoms.get("INVENTORY_LOAD"),
                        "TABLE_OR_BOUND_TYPE_V1"),
                term("TERM_RESERVATION_RESULT", "RESULT", "预留结果", atoms.get("SUCCESS_RESULT"),
                        "BOUND_TYPE_SIMPLE_NAME_RESULT_V1"),
                term("TERM_INVALID_QUANTITY_OUTCOME", "OUTCOME", "数量不合法", atoms.get("QUANTITY_GUARD"),
                        "TECHNICAL_TERMINAL_V1"),
                term("TERM_INCREMENT_RESERVED_AND_VERSION", "ACTIVITY", "增加预留数量并递增版本",
                        atoms.get("OPTIMISTIC_UPDATE"), "ACTIVITY_CLAIM_OR_ANCHOR_V1"),
                term("TERM_UPDATE_COUNT_NOT_ONE_OUTCOME", "OUTCOME", "更新记录数不为一",
                        atoms.get("UPDATE_COUNT_GUARD"), "TECHNICAL_TERMINAL_V1"));
        BusinessTermRegistry businessTerms = new BusinessTermRegistry("business-term-registry-v1",
                "business-terms:reservation-v1", digest("business-terms:reservation-v1"), terms);
        TechnicalDisplayRegistry technicalDisplays = new TechnicalDisplayRegistry(
                "technical-display-registry-v1", "technical-displays:reservation-v1",
                digest("technical-displays:reservation-v1"), List.of(
                new TechnicalDisplayPolicy("FLOW_ROUTE_HANDLER_V1", "FLOW",
                        List.of("HTTP_METHOD_ROUTE_AND_HANDLER"), "TECHNICAL_FLOW_DISPLAY_V1"),
                new TechnicalDisplayPolicy("BOUND_TYPE_SIMPLE_NAME_V1", "REQUEST",
                        List.of("BOUND_TYPE_FQN"), "TECHNICAL_TYPE_DISPLAY_V1"),
                new TechnicalDisplayPolicy("TABLE_OR_BOUND_TYPE_V1", "RECORD",
                        List.of("SQL_TABLE", "BOUND_TYPE_FQN"), "TECHNICAL_RECORD_DISPLAY_V1"),
                new TechnicalDisplayPolicy("BOUND_TYPE_SIMPLE_NAME_RESULT_V1", "RESULT",
                        List.of("BOUND_TYPE_FQN"), "TECHNICAL_TYPE_DISPLAY_V1"),
                new TechnicalDisplayPolicy("TECHNICAL_TERMINAL_V1", "OUTCOME",
                        List.of("THROW_TYPE", "RETURN_TYPE"), "TECHNICAL_OUTCOME_DISPLAY_V1"),
                new TechnicalDisplayPolicy("ACTIVITY_CLAIM_OR_ANCHOR_V1", "ACTIVITY",
                        List.of("ADMITTED_CLAIM_TEMPLATE", "TECHNICAL_ANCHOR_KEY"),
                        "TECHNICAL_ACTIVITY_DISPLAY_V1")));
        ClaimRegistry claims = new ClaimRegistry("claim-registry-v1", "claims:reservation-v1",
                digest("claims:reservation-v1"), List.of(
                new ClaimEntry("INCREMENT_RESERVED_QUANTITY", "ACTIVITY",
                        List.of("SQL_ASSIGNMENT:reserved_qty:+quantity"), "CLAIM_INCREMENT_FIELD_V1"),
                new ClaimEntry("INCREMENT_VERSION", "ACTIVITY",
                        List.of("SQL_ASSIGNMENT:version:+1"), "CLAIM_INCREMENT_FIELD_V1"),
                new ClaimEntry("NON_SINGLE_ROW_UPDATE_THROWS", "OUTCOME",
                        List.of("CONDITION:updateCount!=1", "THROW:ConcurrentInventoryChange"),
                        "CLAIM_CONDITIONAL_THROW_V1"),
                new ClaimEntry("DECREMENT_ON_HAND", "ACTIVITY",
                        List.of("SQL_ASSIGNMENT:on_hand:-quantity"), "CLAIM_INCREMENT_FIELD_V1"),
                new ClaimEntry("CREATE_ORDER_RESERVATION", "ACTIVITY",
                        List.of("RELATION:ORDER_RESERVATION"), "CLAIM_RELATION_V1"),
                new ClaimEntry("RETRY_AFTER_NON_SINGLE_ROW_UPDATE", "OUTCOME",
                        List.of("POLICY:RETRY"), "CLAIM_POLICY_V1")));
        QuestionRegistry questions = new QuestionRegistry("question-registry-v1",
                "questions:reservation-v1", digest("questions:reservation-v1"), List.of(
                new QuestionEntry("ASK_MISSING_ROW_POLICY",
                        List.of("MISSING_RECORD_POLICY_NOT_PROVEN", "NO_MISSING_ROW_BRANCH_IN_LOAD_SCOPE"),
                        "QUESTION_CONFIRM_MISSING_ROW_POLICY_V1")));
        ReaderSentenceTemplateRegistry templates = new ReaderSentenceTemplateRegistry(
                "reader-template-registry-v1", "reader-templates:reservation-v1",
                digest("reader-templates:reservation-v1"), List.of());
        SectionOwnershipRegistry ownership = new SectionOwnershipRegistry(
                "section-ownership-registry-v1", "section-ownership:reservation-v1",
                digest("section-ownership:reservation-v1"), List.of());
        return Stage03Registries.freeze(businessTerms, technicalDisplays, claims, questions, templates, ownership);
    }

    static RegistryBundle emptyBusinessTerms(RegistryBundle original) {
        BusinessTermRegistry empty = new BusinessTermRegistry(original.businessTerms().schemaVersion(),
                original.businessTerms().registryId() + ":empty", digest(original.businessTerms().registryId() + ":empty"),
                List.of());
        return Stage03Registries.freeze(empty, original.technicalDisplays(), original.claims(),
                original.questions(), original.sentenceTemplates(), original.sectionOwnership());
    }

    static ScriptedModelProvider validProvider(Stage02Result stage02) {
        return new ScriptedModelProvider(stage02, UnaryOperator.identity(), UnaryOperator.identity(),
                UnaryOperator.identity(), false);
    }

    static ScriptedModelProvider provider(Stage02Result stage02, UnaryOperator<String> r1Mutation,
                                          UnaryOperator<String> r2Mutation,
                                          UnaryOperator<ObservedRuntimeIdentity> runtimeMutation) {
        return new ScriptedModelProvider(stage02, r1Mutation, r2Mutation, runtimeMutation, false);
    }

    static ScriptedModelProvider emptySelectionProvider(Stage02Result stage02) {
        return new ScriptedModelProvider(stage02, UnaryOperator.identity(), Stage03Fixtures::emptyReviews,
                UnaryOperator.identity(), true);
    }

    static ScriptedModelProvider emptySelectionProvider(Stage02Result stage02,
                                                        UnaryOperator<String> r2Mutation) {
        return new ScriptedModelProvider(stage02, UnaryOperator.identity(), r2Mutation,
                UnaryOperator.identity(), true);
    }

    static UnaryOperator<String> reverseArray(String field) {
        return response -> {
            try {
                var root = JSON.readTree(response);
                var array = root.get(field);
                if (array != null && array.isArray()) {
                    var reversed = JSON.createArrayNode();
                    for (int index = array.size() - 1; index >= 0; index--) {
                        reversed.add(array.get(index));
                    }
                    ((com.fasterxml.jackson.databind.node.ObjectNode) root).set(field, reversed);
                }
                return JSON.writeValueAsString(root);
            } catch (IOException invalidResponse) {
                throw new AssertionError("scripted response is not JSON", invalidResponse);
            }
        };
    }

    static final class ScriptedModelProvider implements StructuredModelProvider {
        private final Stage02Result stage02;
        private final UnaryOperator<String> r1Mutation;
        private final UnaryOperator<String> r2Mutation;
        private final UnaryOperator<ObservedRuntimeIdentity> runtimeMutation;
        private final boolean emptySelection;
        private final List<FlowModelTask> tasks = new ArrayList<>();

        private ScriptedModelProvider(Stage02Result stage02, UnaryOperator<String> r1Mutation,
                                      UnaryOperator<String> r2Mutation,
                                      UnaryOperator<ObservedRuntimeIdentity> runtimeMutation,
                                      boolean emptySelection) {
            this.stage02 = stage02;
            this.r1Mutation = r1Mutation;
            this.r2Mutation = r2Mutation;
            this.runtimeMutation = runtimeMutation;
            this.emptySelection = emptySelection;
        }

        @Override
        public ModelExecutionResult execute(FlowModelTask task) {
            tasks.add(task);
            String response = task.flowInterpretationRound() == 1 ? r1(task) : r2(task);
            ObservedRuntimeIdentity runtime = runtimeMutation.apply(new ObservedRuntimeIdentity(
                    "codex", "gpt-5.6-luna", "xhigh", "read-only"));
            return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(), response,
                    runtime, "started:" + task.taskSpecId() + ":" + task.flowInterpretationRound());
        }

        List<FlowModelTask> tasks() {
            return List.copyOf(tasks);
        }

        private String r1(FlowModelTask task) {
            if (emptySelection) {
                return r1Mutation.apply("{\"schemaVersion\":\"flow-interpretation-r1-v1\","
                        + "\"taskSpecId\":\"" + task.taskSpecId() + "\","
                        + "\"flowSliceId\":\"" + task.flowSliceId() + "\","
                        + "\"evidenceCapsuleId\":\"" + task.evidenceCapsuleId()
                        + "\",\"proposals\":[]}");
            }
            List<String> a01 = atomIds("HTTP_ENTRY");
            List<String> a02 = atomIds("QUANTITY_GUARD");
            List<String> a03 = atomIds("INVENTORY_LOAD");
            List<String> a04 = atomIds("AVAILABLE_FORMULA");
            List<String> a05 = atomIds("INSUFFICIENT_GUARD");
            List<String> a06 = atomIds("OPTIMISTIC_UPDATE");
            List<String> a07 = atomIds("UPDATE_COUNT_GUARD");
            List<String> a08 = atomIds("SUCCESS_RESULT");
            List<String> gaps = allowedGapIds(task);
            String flow = anchor(task, "FLOW");
            String request = anchor(task, "REQUEST");
            String record = anchor(task, "RECORD");
            String result = anchor(task, "RESULT");
            String activity = anchor(task, "ACTIVITY");
            String outcome = anchor(task, "OUTCOME");
            String question = gaps.isEmpty() ? "" : ", {\"proposalKey\":\"P09\",\"proposalType\":\"QUESTION_ONLY\","
                    + "\"questionKey\":\"ASK_MISSING_ROW_POLICY\",\"basisGapIds\":[\""
                    + gaps.get(0) + "\"]}";
            String json = "{\"schemaVersion\":\"flow-interpretation-r1-v1\","
                    + "\"taskSpecId\":\"" + task.taskSpecId() + "\","
                    + "\"flowSliceId\":\"" + task.flowSliceId() + "\","
                    + "\"evidenceCapsuleId\":\"" + task.evidenceCapsuleId() + "\",\"proposals\":["
                    + proposal("P01", "BUSINESS_TERM_SELECTION", flow, "TERM_RESERVATION_FLOW", a01)
                    + "," + proposal("P02", "BUSINESS_TERM_SELECTION", request, "TERM_RESERVATION_REQUEST", one(a01))
                    + "," + proposal("P03", "BUSINESS_TERM_SELECTION", record, "TERM_INVENTORY_RECORD", a03)
                    + "," + proposal("P04", "BUSINESS_TERM_SELECTION", result, "TERM_RESERVATION_RESULT", a08)
                    + "," + proposal("P05", "BUSINESS_TERM_SELECTION", outcome, "TERM_INVALID_QUANTITY_OUTCOME", a02)
                    + "," + claimProposal("P06", activity, "TERM_INCREMENT_RESERVED_AND_VERSION",
                    List.of("INCREMENT_RESERVED_QUANTITY", "INCREMENT_VERSION", "DECREMENT_ON_HAND",
                            "CREATE_ORDER_RESERVATION"), a06)
                    + "," + claimProposal("P07", outcome, "TERM_UPDATE_COUNT_NOT_ONE_OUTCOME",
                    List.of("NON_SINGLE_ROW_UPDATE_THROWS"), a07)
                    + "," + claimProposal("P08", activity, null,
                    List.of("RETRY_AFTER_NON_SINGLE_ROW_UPDATE"), a07)
                    + question + "]}";
            return r1Mutation.apply(json);
        }

        private String r2(FlowModelTask task) {
            List<String> proposalKeys = new ArrayList<>(List.of(
                    "P01", "P02", "P03", "P04", "P05", "P06", "P07", "P08"));
            List<String> a01 = atomIds("HTTP_ENTRY");
            List<String> a02 = atomIds("QUANTITY_GUARD");
            List<String> a03 = atomIds("INVENTORY_LOAD");
            List<String> a06 = atomIds("OPTIMISTIC_UPDATE");
            List<String> a07 = atomIds("UPDATE_COUNT_GUARD");
            List<String> a08 = atomIds("SUCCESS_RESULT");
            List<String> gaps = allowedGapIds(task);
            if (!gaps.isEmpty()) {
                proposalKeys.add("P09");
            }
            StringBuilder reviews = new StringBuilder();
            for (String key : proposalKeys) {
                if (reviews.length() > 0) {
                    reviews.append(',');
                }
                String decision = "P06".equals(key) ? "NARROW" : "P08".equals(key) ? "DROP"
                        : "P09".equals(key) ? "NEEDS_EVIDENCE" : "KEEP";
                reviews.append("{\"proposalKey\":\"").append(key)
                        .append("\",\"decision\":\"").append(decision).append("\"");
                switch (key) {
                    case "P01" -> reviews.append(",\"retainedBasisAtomIds\":[")
                            .append(quoted(a01)).append(']');
                    case "P02" -> reviews.append(",\"retainedBasisAtomIds\":[")
                            .append(quoted(one(a01))).append(']');
                    case "P03" -> reviews.append(",\"retainedBasisAtomIds\":[")
                            .append(quoted(a03)).append(']');
                    case "P04" -> reviews.append(",\"retainedBasisAtomIds\":[")
                            .append(quoted(a08)).append(']');
                    case "P05" -> reviews.append(",\"retainedBasisAtomIds\":[")
                            .append(quoted(a02)).append(']');
                    case "P06" -> reviews.append(",\"retainedClaimKeys\":[\"INCREMENT_RESERVED_QUANTITY\",\"INCREMENT_VERSION\"],")
                            .append("\"retainedBasisAtomIds\":[").append(quoted(a06)).append(']');
                    case "P07" -> reviews.append(",\"retainedClaimKeys\":[\"NON_SINGLE_ROW_UPDATE_THROWS\"],")
                            .append("\"retainedBasisAtomIds\":[").append(quoted(a07)).append(']');
                    case "P08" -> reviews.append(",\"retainedBasisAtomIds\":[")
                            .append(quoted(a07)).append(']');
                    case "P09" -> reviews.append(",\"retainedBasisGapIds\":[\"")
                            .append(gaps.get(0)).append("\"]");
                    default -> throw new AssertionError("unknown scripted proposal " + key);
                }
                reviews.append('}');
            }
            String json = "{\"schemaVersion\":\"flow-interpretation-r2-v1\","
                    + "\"taskSpecId\":\"" + task.taskSpecId() + "\","
                    + "\"flowSliceId\":\"" + task.flowSliceId() + "\",\"evidenceCapsuleId\":\""
                    + task.evidenceCapsuleId() + "\",\"reviews\":[" + reviews + "]}";
            return r2Mutation.apply(json);
        }

        private String anchor(FlowModelTask task, String kind) {
            try {
                JsonNode root = JSON.readTree(task.inputJson());
                String found = findAnchor(root, kind);
                return found == null ? "anchor:" + kind.toLowerCase() : found;
            } catch (IOException invalidTask) {
                return "anchor:" + kind.toLowerCase();
            }
        }

        private String findAnchor(JsonNode node, String kind) {
            if (node.isObject()) {
                JsonNode anchorKind = node.get("anchorKind");
                JsonNode key = node.get("anchorKey");
                if (key == null) {
                    key = node.get("targetAnchorKey");
                }
                if (anchorKind != null && kind.equals(anchorKind.asText()) && key != null) {
                    return key.asText();
                }
                var fields = node.fields();
                while (fields.hasNext()) {
                    String found = findAnchor(fields.next().getValue(), kind);
                    if (found != null) {
                        return found;
                    }
                }
            } else if (node.isArray()) {
                for (JsonNode child : node) {
                    String found = findAnchor(child, kind);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        private List<String> atomIds(String factKind) {
            return stage02.flowSlices().stream().flatMap(flow -> flow.factIds().stream())
                    .flatMap(factId -> stage02.evidenceCapsules().stream()
                            .flatMap(capsule -> capsule.allowedFacts().stream())
                            .filter(fact -> fact.factId().equals(factId) && fact.kind().equals(factKind))
                            .flatMap(fact -> fact.atoms().stream()).map(AllowedAtomView::atomId))
                    .distinct().sorted().toList();
        }

        private List<String> allowedGapIds(FlowModelTask task) {
            return stage02.evidenceCapsules().stream()
                    .filter(capsule -> capsule.evidenceCapsuleId().equals(task.evidenceCapsuleId())
                            && capsule.flowSliceId().equals(task.flowSliceId()))
                    .flatMap(capsule -> capsule.allowedGaps().stream())
                    .map(com.linguan.codemd.stage02.AllowedGapView::gapId)
                    .sorted().toList();
        }
    }

    private static String emptyReviews(String response) {
        try {
            JsonNode parsed = JSON.readTree(response);
            if (!parsed.isObject()) {
                throw new AssertionError("scripted response is not an object");
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) parsed).set("reviews", JSON.createArrayNode());
            return JSON.writeValueAsString(parsed);
        } catch (IOException invalidResponse) {
            throw new AssertionError("scripted response is not JSON", invalidResponse);
        }
    }

    private static String proposal(String key, String type, String anchor, String term, List<String> atoms) {
        return "{\"proposalKey\":\"" + key + "\",\"proposalType\":\"" + type
                + "\",\"targetAnchorKey\":\"" + anchor + "\",\"businessTermKey\":\""
                + term + "\",\"basisAtomIds\":[" + quoted(atoms) + "]}";
    }

    private static String claimProposal(String key, String anchor, String term, List<String> claims,
                                        List<String> atoms) {
        return "{\"proposalKey\":\"" + key + "\",\"proposalType\":\"STRUCTURED_CLAIM_SET\","
                + "\"targetAnchorKey\":\"" + anchor + "\"," + (term == null ? ""
                : "\"businessTermKey\":\"" + term + "\",") + "\"claimKeys\":[" + quoted(claims)
                + "],\"basisAtomIds\":[" + quoted(atoms) + "]}";
    }

    private static String quoted(List<String> values) {
        return values.stream().map(value -> "\"" + value + "\"").reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static List<String> one(List<String> values) {
        return values == null || values.isEmpty() ? List.of() : List.of(values.get(0));
    }

    private static List<String> oneAtom(List<String> values) {
        return one(values);
    }

    private static BusinessTermEntry term(String key, String anchorKind, String localizedValue,
                                          List<String> basis, String fallbackPolicy) {
        return new BusinessTermEntry(key, anchorKind, localizedValue,
                List.of("KIND", "ATTRIBUTE", "CONDITION", "LITERAL", "RELATIONSHIP"),
                basis == null ? List.of() : basis, 100,
                fallbackPolicy);
    }

    private static Map<String, List<String>> atomsByFactKind(Stage02Result stage02) {
        Map<String, List<String>> result = new HashMap<>();
        for (FlowSlice flow : stage02.flowSlices()) {
            for (String factId : flow.factIds()) {
                stage02.evidenceCapsules().stream().flatMap(capsule -> capsule.allowedFacts().stream())
                        .filter(fact -> fact.factId().equals(factId))
                        .forEach(fact -> result.put(fact.kind(), fact.atoms().stream()
                                .map(AllowedAtomView::atomId).sorted().toList()));
            }
        }
        return result;
    }

    private static Stage02ResourceBudget defaultStage02Budget() {
        return new Stage02ResourceBudget(128, 64, 20_000, 40_000, 128, 64,
                16_384, 262_144, 256);
    }

    private static Stage03ResourceBudget defaultStage03Budget() {
        return new Stage03ResourceBudget(64, 2, 262_144, 4_096, 9, 20_000, 256);
    }

    private static ResourceBudget defaultStage01Budget() {
        return new ResourceBudget(64, 4_194_304, 524_288, 200_000, 100_000,
                262_144, 100_000, 256);
    }

    private static List<DeclaredFile> declaredFiles() {
        return expectedFiles().stream().map(file -> new DeclaredFile(file.path(), file.mediaType(),
                file.sizeBytes(), file.sha256(), file.textEncoding())).toList();
    }

    private static List<FileSpec> expectedFiles() {
        Properties manifest = new Properties();
        try (InputStream input = Stage03Fixtures.class.getClassLoader().getResourceAsStream(MANIFEST)) {
            if (input == null) {
                throw new AssertionError("missing fixture manifest: " + MANIFEST);
            }
            manifest.load(input);
        } catch (IOException failure) {
            throw new AssertionError("cannot read fixture manifest", failure);
        }
        int count = Integer.parseInt(manifest.getProperty("file.count"));
        List<FileSpec> files = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "file." + index + ".";
            files.add(new FileSpec(manifest.getProperty(prefix + "path"),
                    manifest.getProperty(prefix + "mediaType"),
                    Long.parseLong(manifest.getProperty(prefix + "sizeBytes")),
                    manifest.getProperty(prefix + "sha256"), manifest.getProperty(prefix + "textEncoding")));
        }
        return List.copyOf(files);
    }

    private static Path resourceRoot() {
        try {
            return Path.of(Stage03Fixtures.class.getClassLoader().getResource(RESOURCE_ROOT).toURI());
        } catch (URISyntaxException | NullPointerException failure) {
            throw new AssertionError("missing fixture root: " + RESOURCE_ROOT, failure);
        }
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
                    .append("\",\"path\":\"").append(file.path()).append("\",\"sha256\":\"")
                    .append(file.sha256()).append("\",\"sizeBytes\":").append(file.sizeBytes())
                    .append(",\"textEncoding\":\"").append(file.textEncoding()).append("\"}");
        }
        canonical.append("],\"scope\":{\"declaredPathCount\":").append(files.size())
                .append(",\"kind\":\"BOUNDED_PATH_SET\",\"scopeRoot\":\".\"}}");
        return digest("declared-inventory-v1\n" + canonical);
    }

    private static String receiptSha256(String inventorySha256) {
        return digest("{\"boundRepositoryUrl\":\"" + REPOSITORY_URL
                + "\",\"boundRevision\":\"" + REVISION + "\",\"inventorySha256\":\""
                + inventorySha256 + "\",\"kind\":\"SYNTHETIC_FIXTURE_MANIFEST\",\"receiptId\":\""
                + RECEIPT_ID + "\"}");
    }

    private static String digest(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Text(String value) {
        return digest(value);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }

    private record FileSpec(String path, String mediaType, long sizeBytes, String sha256,
                            String textEncoding) {
    }

    private record SourceFile(String path, String contents) {
    }
}
