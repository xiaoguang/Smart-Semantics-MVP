package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression RED slice for semantic Proof closure.  This file deliberately owns
 * all mutation builders so it does not weaken or alter the original M3 fixtures.
 */
class ProofSemanticClosureRegressionTest {
    private static final String SERVICE =
            "src/main/java/example/inventory/ReservationService.java";
    private static final String CONTROLLER =
            "src/main/java/example/inventory/ReservationController.java";
    private static final String MAPPER =
            "src/main/java/example/inventory/InventoryMapper.java";
    private static final String XML =
            "src/main/resources/mappers/InventoryMapper.xml";
    private static final String XML_PATH = XML;

    private static final Set<String> CLOSURE_ATOMS = Set.of(
            "CONTROLLER_SERVICE_CALL", "SKU_PREDICATE", "AVAILABLE_FORMULA",
            "RESERVED_INCREMENT", "VERSION_INCREMENT", "VERSION_PREDICATE");

    @Test
    void everyProofHasCandidateRootAndReachableDependencyClosure() throws Exception {
        Path root = baseSnapshot("stage01-m3-closure-");
        Stage01Result result = analyze(root);
        ProofPack pack = result.provenSourceFacts().proofPack();
        Map<String, ProofNode> nodes = pack.nodes().stream()
                .collect(Collectors.toMap(ProofNode::proofNodeId, node -> node));
        Map<String, ProofEdge> edges = pack.edges().stream()
                .collect(Collectors.toMap(ProofEdge::proofEdgeId, edge -> edge));
        Map<String, Proof> proofs = pack.proofs().stream()
                .collect(Collectors.toMap(Proof::proofId, proof -> proof));

        for (CodeFact fact : result.provenSourceFacts().provenFactSet().codeFacts()) {
            for (FactAtom atom : fact.atoms()) {
                Proof proof = proofs.get(atom.proofId());
                assertNotNull(proof, "each atom must name a proof in the same ProofPack");
                assertEquals(fact.factId(), proof.factId());
                assertEquals(atom.atomId(), proof.atomId());
                assertFalse(proof.requiredProofNodeIds().isEmpty(),
                        "a CLOSED proof must have a non-empty candidate closure");
                assertTrue(proof.requiredProofNodeIds().contains(proof.rootProofNodeId()),
                        "proof root must belong to its candidate-specific required nodes");
                ProofNode rootNode = nodes.get(proof.rootProofNodeId());
                assertNotNull(rootNode, "proof root node must exist in the ProofPack");
                assertTrue(fact.subjectNodeIds().contains(rootNode.repositoryNodeId()),
                        "proof root must be this Fact's semantic subject, not an arbitrary global node");

                Map<String, Set<String>> adjacency = new HashMap<>();
                for (String edgeId : proof.requiredProofEdgeIds()) {
                    ProofEdge edge = edges.get(edgeId);
                    assertNotNull(edge, "every required proof edge must exist");
                    assertTrue(proof.requiredProofNodeIds().contains(edge.fromProofNodeId()));
                    assertTrue(proof.requiredProofNodeIds().contains(edge.toProofNodeId()));
                    adjacency.computeIfAbsent(edge.fromProofNodeId(), ignored -> new LinkedHashSet<>())
                            .add(edge.toProofNodeId());
                }
                if (CLOSURE_ATOMS.contains(atom.name())) {
                    assertFalse(proof.requiredProofEdgeIds().isEmpty(),
                            "binding-dependent atom cannot be CLOSED with only a node set");
                    Set<String> reachable = reachableFrom(proof.rootProofNodeId(), adjacency);
                    assertEquals(new HashSet<>(proof.requiredProofNodeIds()), reachable,
                            "all Proof dependency nodes must be reachable from its root through required edges");
                }
            }
        }
    }

    @Test
    void sqlAtomsRootSpansMustContainTheirActualSqlSemantics() throws Exception {
        Path root = baseSnapshot("stage01-m3-sql-root-");
        Stage01Result result = analyze(root);
        Map<String, ProofNode> nodes = proofNodes(result);
        Map<String, Proof> proofs = proofs(result);
        Map<String, String> expected = Map.of(
                "INVENTORY_LOAD/SKU_PREDICATE", "sku = #{sku}",
                "OPTIMISTIC_UPDATE/TABLE", "inventory",
                "OPTIMISTIC_UPDATE/RESERVED_INCREMENT", "reserved_qty = reserved_qty + #{quantity}",
                "OPTIMISTIC_UPDATE/VERSION_INCREMENT", "version = version + 1",
                "OPTIMISTIC_UPDATE/SKU_PREDICATE", "sku = #{sku}",
                "OPTIMISTIC_UPDATE/VERSION_PREDICATE", "version = #{version}");

        for (Map.Entry<String, String> assertion : expected.entrySet()) {
            String[] key = assertion.getKey().split("/", 2);
            FactAtom atom = atom(result, key[0], key[1]);
            Proof proof = proofs.get(atom.proofId());
            ProofNode rootNode = nodes.get(proof.rootProofNodeId());
            assertNotNull(rootNode);
            assertEquals(XML_PATH, rootNode.locator().path(),
                    "SQL atom root must be a semantic SQL span, not Java or XML opening tag");
            String span = spanText(root, rootNode.locator());
            assertTrue(normalize(span).contains(normalize(assertion.getValue())),
                    () -> assertion.getKey() + " root span did not contain SQL: " + span);
        }
    }

    @Test
    void availableAndOptimisticProofsExposeExplicitCrossFileBindingChains() throws Exception {
        Path root = baseSnapshot("stage01-m3-binding-chain-");
        Stage01Result result = analyze(root);
        Map<String, ProofNode> nodes = proofNodes(result);
        Map<String, Proof> proofs = proofs(result);

        Proof available = proofs.get(atom(result, "AVAILABLE_FORMULA", "AVAILABLE_FORMULA").proofId());
        assertHasLocator(nodes, available, "src/main/resources/mappers/InventoryMapper.xml", 5);
        assertHasLocator(nodes, available, SERVICE, 19);
        assertHasLocator(nodes, available, XML, 6);
        assertRules(proofEdges(result), available,
                "RESULTTYPE_FQN_TO_JAVA_PACKAGE_V1",
                "JAVA_PACKAGE_TO_RECORD_DECLARATION_V1",
                "SELECT_PROJECTION_TO_RECORD_COMPONENT_V1");

        Proof version = proofs.get(atom(result, "OPTIMISTIC_UPDATE", "VERSION_PREDICATE").proofId());
        for (LocatorExpectation expectation : List.of(
                new LocatorExpectation("src/main/resources/application.yml", 1),
                new LocatorExpectation(XML, 4),
                new LocatorExpectation(MAPPER, 1),
                new LocatorExpectation(XML, 5),
                new LocatorExpectation(SERVICE, 11),
                new LocatorExpectation(SERVICE, 14),
                new LocatorExpectation(MAPPER, 4),
                new LocatorExpectation(MAPPER, 9),
                new LocatorExpectation(XML, 10),
                new LocatorExpectation(XML, 13))) {
            assertHasLocator(nodes, version, expectation.path(), expectation.line());
        }
        assertRules(proofEdges(result), version,
                "CONFIG_RESOLVES_MAPPER_DOCUMENT_V1",
                "XML_NAMESPACE_TO_JAVA_PACKAGE_V1",
                "JAVA_PACKAGE_TO_INTERFACE_DECLARATION_V1",
                "RESULTTYPE_FQN_TO_JAVA_PACKAGE_V1",
                "JAVA_PACKAGE_TO_RECORD_DECLARATION_V1",
                "SELECT_RESULTTYPE_TO_BOUND_FIND_CALL_RESULT_V1",
                "RECORD_DECLARATION_TO_LOCAL_VARIABLE_TYPE_V1",
                "LOCAL_RECEIVER_TO_RECORD_ACCESSOR_V1",
                "RECEIVER_DECLARATION_TO_CALL_SITE_V1",
                "ACCESSOR_EXPRESSION_TO_CALL_ARGUMENT_V1",
                "CALL_ARGUMENT_POSITION_TO_MAPPER_PARAM_V1",
                "IMPORT_RESOLVES_MYBATIS_PARAM_ANNOTATION_V1",
                "MAPPER_METHOD_TO_PARAM_V1",
                "MAPPER_METHOD_TO_XML_STATEMENT_V1",
                "NAMESPACE_STATEMENT_TO_UPDATE_V1",
                "UPDATE_STATEMENT_CONTAINS_PREDICATE_V1",
                "MYBATIS_PARAM_TO_SQL_PREDICATE_V1");
    }

    @Test
    void decoyWorkflowAndStatementCannotDonateNodesToTheOriginalFacts() throws Exception {
        Path root = baseSnapshot("stage01-m3-decoy-");
        FrozenRepositoryRequest request = addFiles(root, Map.of(
                "src/main/java/decoy/inventory/DecoyWorkflow.java",
                "package decoy.inventory;\n"
                        + "final class DecoyWorkflow {\n"
                        + "  void unrelated(String sku, int quantity) {\n"
                        + "    if (quantity <= 0) throw new IllegalArgumentException();\n"
                        + "    String predicate = \"sku = #{sku}\";\n"
                        + "    String assignment = \"reserved_qty = reserved_qty + #{quantity}\";\n"
                        + "  }\n"
                        + "}\n",
                "src/main/resources/mappers/DecoyMapper.xml",
                "<mapper namespace=\"decoy.inventory.UnrelatedMapper\">\n"
                        + "  <select id=\"unrelated\" resultType=\"decoy.inventory.Row\">\n"
                        + "    SELECT sku, on_hand AS onHand, reserved_qty AS reserved, version\n"
                        + "    FROM inventory WHERE sku = #{sku}\n"
                        + "  </select>\n"
                        + "  <update id=\"unrelatedUpdate\">\n"
                        + "    UPDATE inventory SET reserved_qty = reserved_qty + #{quantity}\n"
                        + "    WHERE sku = #{sku} AND version = #{version}\n"
                        + "  </update>\n"
                        + "</mapper>\n"));
        Stage01Result result = new Stage01Analyzer().analyze(request);
        Set<String> basePaths = Stage01Fixtures.expectedFiles().stream()
                .map(Stage01Fixtures.FileSpec::path).collect(Collectors.toSet());
        Map<String, ProofNode> nodes = proofNodes(result);
        for (Proof proof : result.provenSourceFacts().proofPack().proofs()) {
            for (String nodeId : proof.requiredProofNodeIds()) {
                ProofNode node = nodes.get(nodeId);
                assertNotNull(node);
                assertTrue(basePaths.contains(node.locator().path()),
                        "original Fact closure borrowed a decoy node: " + node.locator().path());
            }
        }
    }

    @Test
    void semanticallyEquivalentRenamedWorkflowStillRegistersCandidates() throws Exception {
        Path root = baseSnapshot("stage01-m3-renamed-");
        FrozenRepositoryRequest request = renamedWorkflowRequest(root);
        Stage01Result result = new Stage01Analyzer().analyze(request);
        CandidateAccounting accounting = result.provenSourceFacts().provenFactSet().candidateAccounting();

        assertTrue(accounting.candidateFactCount() >= 4,
                "renaming symbols must not erase the candidate denominator");
        Set<String> candidateFacts = accounting.atomDispositions().stream()
                .map(AtomDisposition::candidateFactKey).collect(Collectors.toSet());
        assertTrue(candidateFacts.containsAll(Set.of("F01", "F02", "F03", "F08")),
                "HTTP, guard, SQL load, and return candidates must remain dispositioned");
        assertEquals(accounting.candidateAtomCount(), accounting.atomDispositions().size());
    }

    @Test
    void retryEvidenceClosesRetryHandlingExpectationGap() throws Exception {
        Path retryRoot = baseSnapshot("stage01-m3-retry-gap-");
        Stage01Result retry = analyze(retryRequest(retryRoot));
        assertNoExpectationGap(retry, "RETRY_HANDLING");
    }

    @Test
    void warehousePredicateClosesWarehouseScopeExpectationGap() throws Exception {
        Path warehouseRoot = baseSnapshot("stage01-m3-warehouse-gap-");
        Stage01Result warehouse = analyze(warehousePredicateRequest(warehouseRoot));
        assertNoExpectationGap(warehouse, "WAREHOUSE_KEY_SCOPE");
    }

    @Test
    void stage01RequestCarriesGapProfileAndExactJsonRejectsUnknownDigest() throws Exception {
        Class<?> requestType = loadRequired("com.linguan.codemd.stage01.Stage01Request");
        assertTrue(requestType.isRecord(), "Stage01Request must be an immutable record");
        assertTrue(Modifier.isPublic(requestType.getModifiers()));
        assertTrue(Arrays.stream(requestType.getRecordComponents())
                        .anyMatch(component -> component.getName().equals("gapExpectationProfileRef")
                                && component.getType().getSimpleName().equals("GapExpectationProfileRef")),
                "Stage01Request must carry GapExpectationProfileRef");
        loadRequired("com.linguan.codemd.stage01.GapExpectationProfileRef");

        Class<?> jsonType = loadRequired("com.linguan.codemd.stage01.Stage01RequestJson");
        Method parser = Arrays.stream(jsonType.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> (method.getName().equals("parse") || method.getName().equals("fromJson")))
                .filter(method -> method.getParameterCount() == 1
                        && method.getParameterTypes()[0].equals(String.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage01RequestJson needs an exact String parser"));

        Path root = baseSnapshot("stage01-m3-request-json-");
        FrozenRepositoryRequest frozen = Stage01Fixtures.request(root);
        String json = requestJson(frozen);
        try {
            Object parsed = parser.invoke(null, json);
            assertEquals(requestType, parsed.getClass());
        } catch (InvocationTargetException failure) {
            throw new AssertionError("valid exact Stage01Request JSON was rejected", failure.getCause());
        }

        String unknownDigest = json.replace(GAP_PROFILE_SHA256, "0".repeat(64));
        InvocationTargetException rejected = assertThrows(InvocationTargetException.class,
                () -> parser.invoke(null, unknownDigest),
                "unknown GapExpectationProfileRef digest must fail closed");
        assertTrue(rejected.getCause() instanceof Stage01Exception,
                "failure must expose the stable Stage01 failure type");
        assertEquals("PROFILE_REFERENCE_INVALID", ((Stage01Exception) rejected.getCause()).code());
    }

    private static final String GAP_PROFILE_SHA256 =
            "e63f976bba3fcc0acbc62c3b72f0a924d539d240d69ca86b7a598905fafc0f8a";

    private static Stage01Result analyze(Path root) {
        return new Stage01Analyzer().analyze(Stage01Fixtures.request(root));
    }

    private static Stage01Result analyze(FrozenRepositoryRequest request) {
        return new Stage01Analyzer().analyze(request);
    }

    private static Path baseSnapshot(String prefix) throws IOException {
        return Stage01Fixtures.copyReservationSnapshot(Files.createTempDirectory(prefix));
    }

    private static Map<String, ProofNode> proofNodes(Stage01Result result) {
        return result.provenSourceFacts().proofPack().nodes().stream()
                .collect(Collectors.toMap(ProofNode::proofNodeId, node -> node));
    }

    private static Map<String, Proof> proofs(Stage01Result result) {
        return result.provenSourceFacts().proofPack().proofs().stream()
                .collect(Collectors.toMap(Proof::proofId, proof -> proof));
    }

    private static List<ProofEdge> proofEdges(Stage01Result result) {
        return result.provenSourceFacts().proofPack().edges();
    }

    private static FactAtom atom(Stage01Result result, String kind, String name) {
        return result.provenSourceFacts().provenFactSet().codeFacts().stream()
                .filter(fact -> fact.kind().equals(kind))
                .flatMap(fact -> fact.atoms().stream())
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + kind + "/" + name));
    }

    private static Set<String> reachableFrom(String root, Map<String, Set<String>> adjacency) {
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!reached.add(current)) {
                continue;
            }
            pending.addAll(adjacency.getOrDefault(current, Set.of()));
        }
        return reached;
    }

    private static String spanText(Path root, ProofLocator locator) throws IOException {
        byte[] bytes = Files.readAllBytes(root.resolve(locator.path()));
        assertTrue(locator.startByte() >= 0 && locator.endByteExclusive() <= bytes.length);
        return new String(Arrays.copyOfRange(bytes, locator.startByte(), locator.endByteExclusive()),
                StandardCharsets.UTF_8);
    }

    private static String normalize(String text) {
        return text.toLowerCase().replaceAll("\\s+", "");
    }

    private static void assertHasLocator(Map<String, ProofNode> nodes, Proof proof,
                                         String path, int line) {
        assertTrue(proof.requiredProofNodeIds().stream().map(nodes::get).anyMatch(node ->
                        node != null && node.locator().path().equals(path)
                                && node.locator().startLine() <= line
                                && node.locator().endLine() >= line),
                () -> "missing proof dependency locator " + path + ":" + line);
    }

    private static void assertRules(List<ProofEdge> edges, Proof proof, String... expectedRules) {
        Set<String> actual = edges.stream()
                .filter(edge -> proof.requiredProofEdgeIds().contains(edge.proofEdgeId()))
                .map(ProofEdge::ruleId).collect(Collectors.toSet());
        assertTrue(actual.containsAll(Set.of(expectedRules)),
                () -> "missing explicit proof rules; expected " + Set.of(expectedRules) + " but got " + actual);
    }

    private static void assertNoExpectationGap(Stage01Result result, String expectationId) {
        assertFalse(result.provenSourceFacts().gapLedger().expectationGaps().stream()
                        .anyMatch(gap -> gap.expectationId().equals(expectationId)),
                "profile expectation should close after matching evidence: " + expectationId);
    }

    private static FrozenRepositoryRequest mutate(Path root, String path,
                                                  UnaryOperator<String> mutation) throws IOException {
        Path source = root.resolve(path);
        String before = Files.readString(source, StandardCharsets.UTF_8);
        String after = mutation.apply(before);
        if (before.equals(after)) {
            throw new AssertionError("mutation did not change " + path);
        }
        Files.writeString(source, after, StandardCharsets.UTF_8);
        return requestForCurrentBytes(root);
    }

    private static FrozenRepositoryRequest addFiles(Path root, Map<String, String> additions)
            throws IOException {
        List<DeclaredFile> files = new ArrayList<>(Stage01Fixtures.declaredFiles());
        for (Map.Entry<String, String> addition : additions.entrySet()) {
            Path target = root.resolve(addition.getKey());
            Files.createDirectories(target.getParent());
            byte[] bytes = addition.getValue().getBytes(StandardCharsets.UTF_8);
            Files.write(target, bytes);
            String mediaType = addition.getKey().endsWith(".xml") ? "XML" : "JAVA";
            files.add(new DeclaredFile(addition.getKey(), mediaType, bytes.length,
                    Stage01Fixtures.sha256(bytes), "UTF-8"));
        }
        return Stage01Fixtures.request(root, files, Stage01Fixtures.defaultBudget());
    }

    private static FrozenRepositoryRequest requestForCurrentBytes(Path root) throws IOException {
        List<DeclaredFile> files = new ArrayList<>();
        for (Stage01Fixtures.FileSpec spec : Stage01Fixtures.expectedFiles()) {
            byte[] bytes = Files.readAllBytes(root.resolve(spec.path()));
            files.add(new DeclaredFile(spec.path(), spec.mediaType(), bytes.length,
                    Stage01Fixtures.sha256(bytes), spec.textEncoding()));
        }
        return Stage01Fixtures.request(root, files, Stage01Fixtures.defaultBudget());
    }

    private static FrozenRepositoryRequest renamedWorkflowRequest(Path root) throws IOException {
        Map<String, UnaryOperator<String>> mutations = new LinkedHashMap<>();
        mutations.put(CONTROLLER, source -> rename(source,
                "ReservationController", "BookingController",
                "ReservationService", "BookingService",
                "ReservationRequest", "BookingRequest",
                "ReservationReceipt", "BookingReceipt",
                "reserve", "book", "request", "input", "sku", "code", "quantity", "amount"));
        mutations.put(SERVICE, source -> rename(source,
                "ReservationService", "BookingService",
                "ReservationReceipt", "BookingReceipt",
                "InventoryRow", "StockRow",
                "reserve", "book", "mapper", "repository",
                "inventory", "stock", "sku", "code", "quantity", "amount"));
        mutations.put(MAPPER, source -> rename(source,
                "InventoryMapper", "StockRepository",
                "InventoryRow", "StockRow",
                "findBySku", "findByCode",
                "addReservation", "applyBooking",
                "sku", "code", "quantity", "amount"));
        mutations.put(XML, source -> rename(source,
                "InventoryMapper", "StockRepository",
                "InventoryRow", "StockRow",
                "findBySku", "findByCode",
                "addReservation", "applyBooking",
                "sku", "code", "quantity", "amount"));
        FrozenRepositoryRequest request = null;
        for (Map.Entry<String, UnaryOperator<String>> mutation : mutations.entrySet()) {
            request = mutate(root, mutation.getKey(), mutation.getValue());
        }
        return request;
    }

    private static String rename(String source, String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("rename pairs must be even");
        }
        String result = source;
        for (int index = 0; index < pairs.length; index += 2) {
            result = result.replace(pairs[index], pairs[index + 1]);
        }
        return result;
    }

    private static FrozenRepositoryRequest retryRequest(Path root) throws IOException {
        return mutate(root, SERVICE, source -> source.replace(
                "    int updateCount = mapper.addReservation(sku, quantity, inventory.version());\n"
                        + "    if (updateCount != 1) throw new ConcurrentInventoryChange();\n"
                        + "    return new ReservationReceipt(sku, quantity);",
                "    for (int attempt = 0; attempt < 2; attempt++) {\n"
                        + "      try {\n"
                        + "        int updateCount = mapper.addReservation(sku, quantity, inventory.version());\n"
                        + "        if (updateCount != 1) throw new ConcurrentInventoryChange();\n"
                        + "        return new ReservationReceipt(sku, quantity);\n"
                        + "      } catch (ConcurrentInventoryChange retryable) {\n"
                        + "        if (attempt == 1) throw retryable;\n"
                        + "      }\n"
                        + "    }\n"
                        + "    throw new ConcurrentInventoryChange();"));
    }

    private static FrozenRepositoryRequest warehousePredicateRequest(Path root) throws IOException {
        return mutate(root, XML, source -> source.replace(
                "WHERE sku = #{sku}", "WHERE warehouse_id = #{warehouseId} AND sku = #{sku}"));
    }

    private static Class<?> loadRequired(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException failure) {
            fail("missing required public API type " + name + " (intentional RED)");
            throw new AssertionError(failure);
        }
    }

    private static String requestJson(FrozenRepositoryRequest request) {
        StringBuilder files = new StringBuilder();
        List<DeclaredFile> declarations = request.files().stream()
                .sorted(java.util.Comparator.comparing(DeclaredFile::path)).toList();
        for (int index = 0; index < declarations.size(); index++) {
            if (index > 0) {
                files.append(',');
            }
            DeclaredFile file = declarations.get(index);
            files.append("{\"path\":\"").append(json(file.path()))
                    .append("\",\"mediaType\":\"").append(file.mediaType())
                    .append("\",\"sizeBytes\":").append(file.sizeBytes())
                    .append(",\"sha256\":\"").append(file.sha256())
                    .append("\",\"textEncoding\":\"").append(file.textEncoding()).append("\"}");
        }
        return "{\"schemaVersion\":\"stage01-request-v1\",\"frozenRepositoryRequest\":{"
                + "\"schemaVersion\":\"frozen-repository-request-v1\","
                + "\"origin\":{\"kind\":\"SYNTHETIC_FIXTURE\",\"repositoryUrl\":\""
                + json(request.origin().repositoryUrl()) + "\",\"revision\":\""
                + json(request.origin().revision()) + "\"},"
                + "\"captureProof\":{\"kind\":\"SYNTHETIC_FIXTURE_MANIFEST\",\"receiptId\":\""
                + json(request.captureProof().receiptId()) + "\",\"boundRepositoryUrl\":\""
                + json(request.captureProof().boundRepositoryUrl()) + "\",\"boundRevision\":\""
                + json(request.captureProof().boundRevision()) + "\",\"inventorySha256\":\""
                + request.captureProof().inventorySha256() + "\",\"receiptSha256\":\""
                + request.captureProof().receiptSha256() + "\"},"
                + "\"snapshotRoot\":\"" + json(request.snapshotRoot().toAbsolutePath().toString()) + "\","
                + "\"inventoryScope\":{\"kind\":\"BOUNDED_PATH_SET\",\"scopeRoot\":\".\",\"declaredPathCount\":"
                + request.inventoryScope().declaredPathCount() + "},\"files\":[" + files + "],"
                + "\"verificationPolicyId\":\"" + request.verificationPolicyId() + "\","
                + "\"resourceBudget\":{\"maxFiles\":64,\"maxTotalBytes\":4194304,\"maxFileBytes\":524288,"
                + "\"maxAstNodes\":200000,\"maxXmlNodes\":100000,\"maxSqlChars\":262144,"
                + "\"maxControlFlowNodes\":100000,\"maxRecursionDepth\":256},"
                + "\"capabilityProfileRef\":{\"profileId\":\""
                + request.capabilityProfileRef().profileId() + "\",\"profileSha256\":\""
                + request.capabilityProfileRef().profileSha256() + "\"}},"
                + "\"gapExpectationProfileRef\":{\"profileId\":\"gap-expectation-profile-v1\","
                + "\"profileSha256\":\"" + GAP_PROFILE_SHA256 + "\"}}";
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record LocatorExpectation(String path, int line) {
    }
}
