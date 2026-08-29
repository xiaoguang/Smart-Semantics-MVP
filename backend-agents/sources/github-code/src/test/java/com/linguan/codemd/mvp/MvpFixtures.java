package com.linguan.codemd.mvp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small synthetic frozen snapshot used by the MVP contract tests.
 *
 * <p>The helper deliberately models the manifest as data rather than asking
 * the analyzer to discover this flow. That mirrors the first-day MVP boundary:
 * the verifier and the model-admission seam are tested before automatic source
 * discovery is introduced.</p>
 */
final class MvpFixtures {
    static final String COMMIT = "8c30ce7861570458920175e200bb2a6442713580";
    static final String FLOW_ID = "flow:approve-order";
    static final String TRACE_ITEM_KEY = "activity:flow:approve-order";

    private static final ObjectMapper JSON = new ObjectMapper();

    private MvpFixtures() {
    }

    static Fixture valid(Path root) throws IOException {
        Path snapshotRoot = root.resolve("snapshot");
        Path javaRoot = snapshotRoot.resolve("src/main/java/example");
        Path resourceRoot = snapshotRoot.resolve("src/main/resources/mapper");
        Files.createDirectories(javaRoot);
        Files.createDirectories(resourceRoot);

        Path controller = javaRoot.resolve("OrderController.java");
        Path service = javaRoot.resolve("OrderService.java");
        Path mapper = resourceRoot.resolve("OrderMapper.xml");

        write(controller, "package example;\n"
                + "public final class OrderController {\n"
                + "    private final OrderService service;\n"
                + "    public OrderController(OrderService service) { this.service = service; }\n"
                + "    public String approve(String id) {\n"
                + "        return service.approve(id);\n"
                + "    }\n"
                + "}\n");
        write(service, "package example;\n"
                + "public final class OrderService {\n"
                + "    public String approve(String id) {\n"
                + "        if (id == null) { return \"REJECTED\"; }\n"
                + "        return \"APPROVED:\" + id;\n"
                + "    }\n"
                + "}\n");
        write(mapper, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<mapper namespace=\"example.OrderMapper\">\n"
                + "  <update id=\"approve\">\n"
                + "    update orders set status = 'APPROVED' where id = #{id}\n"
                + "  </update>\n"
                + "</mapper>\n");

        String controllerPath = "src/main/java/example/OrderController.java";
        String servicePath = "src/main/java/example/OrderService.java";
        String mapperPath = "src/main/resources/mapper/OrderMapper.xml";

        Evidence controllerEvidence = evidence(
                "evidence:controller-approve", controllerPath, controller,
                5, 6, 5, 40);
        Evidence serviceEvidence = evidence(
                "evidence:service-guard-and-result", servicePath, service,
                3, 6, 5, 50);
        Evidence mapperEvidence = evidence(
                "evidence:mapper-approve-sql", mapperPath, mapper,
                3, 5, 3, 70);

        List<Evidence> evidence = List.of(controllerEvidence, serviceEvidence, mapperEvidence);
        List<Fact> facts = List.of(
                new Fact("fact:http-entry", "HTTP_ENTRY",
                        Map.of("method", "POST", "route", "/orders/approve"),
                        List.of(controllerEvidence.id())),
                new Fact("fact:order-object", "BUSINESS_OBJECT_ANCHOR",
                        Map.of("technicalType", "example.OrderService"),
                        List.of(serviceEvidence.id())),
                new Fact("fact:approval-guard", "GUARD",
                        Map.of("condition", "id != null", "rejects", "REJECTED"),
                        List.of(serviceEvidence.id())),
                new Fact("fact:status-write", "STATE_WRITE",
                        Map.of("field", "orders.status", "value", "APPROVED"),
                        List.of(serviceEvidence.id(), mapperEvidence.id()))
        );

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("origin", Map.of(
                "repositoryUrl", "https://github.com/jishenghua/jshERP.git",
                "commitSha", COMMIT));
        manifest.put("rootName", "synthetic-order-snapshot");
        manifest.put("anchors", List.of(Map.of(
                "anchorId", "anchor:order",
                "technicalRefs", List.of("example.OrderService", "orders"))));
        manifest.put("files", List.of(
                fileEntry(controllerPath, controller),
                fileEntry(servicePath, service),
                fileEntry(mapperPath, mapper)));
        manifest.put("evidence", evidence.stream().map(Evidence::asMap).toList());
        manifest.put("lockedFacts", facts.stream().map(Fact::asMap).toList());
        manifest.put("flows", List.of(Map.of(
                "flowId", FLOW_ID,
                "traceItemKey", TRACE_ITEM_KEY,
                "factIds", facts.stream().map(Fact::id).toList(),
                "evidenceIds", evidence.stream().map(Evidence::id).toList())));

        Path manifestPath = root.resolve("flow-manifest.json");
        JSON.writeValue(manifestPath.toFile(), manifest);
        return new Fixture(root, snapshotRoot, manifestPath, controllerEvidence,
                serviceEvidence, mapperEvidence);
    }

    static ModelProvider validProvider() {
        return task -> {
            if (task.round() == 1) {
                return r1(task, List.of("fact:http-entry", "fact:order-object",
                        "fact:approval-guard", "fact:status-write"),
                        List.of("evidence:controller-approve", "evidence:service-guard-and-result"));
            }
            return r2(task, "I1", "KEEP",
                    List.of("fact:http-entry", "fact:order-object", "fact:approval-guard", "fact:status-write"),
                    List.of("evidence:controller-approve", "evidence:service-guard-and-result"));
        };
    }

    static ModelProvider providerWithR1Fact(String factId) {
        return task -> task.round() == 1
                ? r1(task, List.of(factId), List.of("evidence:controller-approve"))
                : r2(task, "I1", "KEEP", List.of("fact:http-entry"),
                List.of("evidence:controller-approve"));
    }

    static ModelProvider providerWithR1Evidence(String evidenceId) {
        return task -> task.round() == 1
                ? r1(task, List.of("fact:http-entry"), List.of(evidenceId))
                : r2(task, "I1", "KEEP", List.of("fact:http-entry"),
                List.of("evidence:controller-approve"));
    }

    static ModelProvider providerWithR2EvidenceExpansion() {
        return task -> task.round() == 1
                ? r1(task, List.of("fact:http-entry"), List.of("evidence:controller-approve"))
                : r2(task, "I1", "KEEP",
                List.of("fact:http-entry", "fact:status-write"),
                List.of("evidence:controller-approve", "evidence:mapper-approve-sql"));
    }

    static ModelProvider providerWithR2NewProposal() {
        return task -> task.round() == 1
                ? r1(task, List.of("fact:http-entry"), List.of("evidence:controller-approve"))
                : r2(task, "I-NEW", "KEEP", List.of("fact:http-entry"),
                List.of("evidence:controller-approve"));
    }

    private static String r1(ModelTask task, List<String> facts, List<String> evidence) {
        return json(Map.of(
                "taskSpecId", task.taskSpecId(),
                "flowSliceId", task.flowSliceId(),
                "capsuleId", task.capsuleId(),
                "localEntities", List.of(Map.of(
                        "localKey", "E1",
                        "anchorRef", "anchor:order",
                        "proposedName", "订单",
                        "proposedRole", "PRIMARY_BUSINESS_RECORD",
                        "factBasis", facts)),
                "interpretations", List.of(Map.of(
                        "proposalKey", "I1",
                        "kind", "BUSINESS_ACTIVITY",
                        "proposedLabel", "审核订单",
                        "frame", Map.of(
                                "activityKind", "STATE_CHANGE",
                                "primaryObjectLocalKey", "E1",
                                "triggerFactRef", "fact:http-entry",
                                "conditionFactRefs", List.of("fact:approval-guard"),
                                "outcomeFactRefs", List.of("fact:status-write")),
                        "factBasis", facts,
                        "evidenceBasis", evidence,
                        "acknowledgedGapIds", List.of("gap:transaction-outcome"),
                        "alternatives", List.of(),
                        "privateRationale", "仅解释已锁定的流程事实")),
                "reportedGaps", List.of(Map.of(
                        "gapId", "gap:transaction-outcome",
                        "relatedFactIds", List.of("fact:status-write")))));
    }

    private static String r2(ModelTask task, String proposalKey, String decision,
                             List<String> facts, List<String> evidence) {
        return json(Map.of(
                "taskSpecId", task.taskSpecId(),
                "flowSliceId", task.flowSliceId(),
                "reviews", List.of(Map.of(
                        "proposalKey", proposalKey,
                        "decision", decision,
                        "factBasis", facts,
                        "evidenceBasis", evidence,
                        "acknowledgedGapIds", List.of("gap:transaction-outcome")))));
    }

    static String tamperExcerptHash(Fixture fixture) throws IOException {
        Map<String, Object> manifest = JSON.readValue(fixture.manifest().toFile(), Map.class);
        List<Map<String, Object>> entries = mutableMaps(manifest.get("evidence"));
        entries.get(0).put("excerptSha256", "0".repeat(64));
        manifest.put("evidence", entries);
        JSON.writeValue(fixture.manifest().toFile(), manifest);
        return fixture.controllerEvidence().excerptSha256();
    }

    static void tamperSource(Fixture fixture) throws IOException {
        Files.writeString(fixture.snapshotRoot().resolve(fixture.controllerEvidence().path()),
                "tampered source\n", StandardCharsets.UTF_8);
    }

    private static Evidence evidence(String id, String path, Path file,
                                     int startLine, int endLine,
                                     int startColumn, int endColumn) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String excerpt = String.join("\n", lines.subList(startLine - 1, endLine)) + "\n";
        return new Evidence(id, path, startLine, endLine, startColumn, endColumn,
                sha256(excerpt.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, Object> fileEntry(String path, Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return Map.of("path", path, "size", bytes.length, "sha256", sha256(bytes));
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mutableMaps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> entry : (List<Map<String, Object>>) value) {
            result.add(new LinkedHashMap<>(entry));
        }
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    record Fixture(Path root, Path snapshotRoot, Path manifest,
                   Evidence controllerEvidence, Evidence serviceEvidence,
                   Evidence mapperEvidence) {
    }

    record Evidence(String id, String path, int startLine, int endLine,
                    int startColumn, int endColumn, String excerptSha256) {
        Map<String, Object> asMap() {
            return Map.of(
                    "evidenceId", id,
                    "path", path,
                    "startLine", startLine,
                    "endLine", endLine,
                    "startColumn", startColumn,
                    "endColumn", endColumn,
                    "excerptSha256", excerptSha256);
        }
    }

    record Fact(String id, String kind, Map<String, String> lockedAtoms,
                List<String> evidenceIds) {
        Map<String, Object> asMap() {
            return Map.of(
                    "factId", id,
                    "kind", kind,
                    "lockedAtoms", lockedAtoms,
                    "evidenceIds", evidenceIds);
        }
    }
}
