package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Independent M1/M2 integrity regressions.  This class deliberately does not use
 * the shared Stage01 or M2 fixture builders so its request proof and expectations
 * remain an independent contract.
 */
class RepositoryIntegrityRegressionTest {
    private static final String RESOURCE_ROOT = "stage01/reservation-v1";
    private static final String REPOSITORY_URL =
            "https://example.invalid/synthetic/inventory-reservation.git";
    private static final String REVISION = "integrity-revision-1";
    private static final String RECEIPT_ID = "integrity-receipt-1";
    private static final String POLICY_ID = "frozen-snapshot-v1";
    private static final String JAVA17_PROFILE = "java17-springmvc-mybatis-static-v0";
    private static final String JAVA8_PROFILE = "java8-springmvc-mybatis-static-v0";
    private static final String JAVA17_DIGEST =
            "a8775d349a7b4e6f254691dd4f65d51833fbfc2bcbffe451508ec6d6ce78860a";
    private static final String JAVA8_DIGEST =
            "b908a3e8b769ccc2bd5f7b5642d55b8a166eba973d8f310abdf1c244b752c598";
    private static final String INVALID_DIGEST =
            "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String MAPPER_PATH =
            "src/main/resources/mappers/InventoryMapper.xml";
    private static final String SERVICE_PATH =
            "src/main/java/example/inventory/ReservationService.java";
    private static final String RESERVATION_CONTROLLER_PATH =
            "src/main/java/example/inventory/ReservationController.java";
    private static final String CALL_TEXT = "mapper.findBySku(sku)";
    private static final String CALL_SPAN_SHA256 =
            "6282d56589b57ef5a3b53f238812236d94e9a08aefdee85ef6852df0d4e404c0";

    private static final ResourceBudget DEFAULT_BUDGET = new ResourceBudget(
            64, 4_194_304, 524_288, 200_000, 100_000, 262_144, 100_000, 256);
    private static final List<FileSpec> BASE_FILES = List.of(
            new FileSpec("pom.xml", "MAVEN_POM", 416,
                    "b303caf9ab5ab5188a1dd36eb5d7d14d90d77fdd82639325251b9a666c2ddfab"),
            new FileSpec("src/main/java/example/inventory/InventoryMapper.java", "JAVA", 362,
                    "dc797a56aac4a1ab7f65f155b3644277862539f434306104996dd24ddbebba4f"),
            new FileSpec("src/main/java/example/inventory/ReservationController.java", "JAVA", 695,
                    "ef388c73eab798ef2a3c9208c04ba77642fd1b2e90ef28bf5432b0eb915ed6de"),
            new FileSpec("src/main/java/example/inventory/ReservationService.java", "JAVA", 1033,
                    "8b2a0718bac3774e185331ad2b0fdb929b3d5d451b0d8d56358b9ffb28056186"),
            new FileSpec("src/main/resources/application.yml", "YAML", 51,
                    "56729aa40b81c04c7c51456290b48dd6cf3041575d02a27cd975dfa4e70ef649"),
            new FileSpec(MAPPER_PATH, "XML", 594,
                    "ac9b916d8e8b09461f9963c47b022e69cc06444d85e6fd137f2d6ee892f5118b"));

    @Test
    void arbitraryProfileDigestIsRejected() throws Exception {
        Path root = copyBaseSnapshot();

        Stage01Exception failure = assertThrows(Stage01Exception.class,
                () -> understand(request(root, baseDeclarations(), DEFAULT_BUDGET,
                        JAVA17_PROFILE, INVALID_DIGEST, REVISION, RECEIPT_ID)));

        assertEquals("PROFILE_REFERENCE_INVALID", failure.code());
    }

    @Test
    void java8ProfileUsesItsParserAndDoesNotAdmitJava17OnlySyntax() throws Exception {
        Path root = copyBaseSnapshot();
        String java17Only = "package example.inventory;\n"
                + "final class Java17Only {\n"
                + "  String text() { return \"\"\"\n"
                + "      reservation\n"
                + "      \"\"\"; }\n"
                + "}\n";
        List<DeclaredFile> files = withFiles(root, Map.of(
                "src/main/java/example/inventory/Java17Only.java", java17Only));

        RepositoryUnderstanding understanding = understand(request(root, files, DEFAULT_BUDGET,
                JAVA8_PROFILE, JAVA8_DIGEST, REVISION, RECEIPT_ID));

        assertTrue(sites(understanding).stream().anyMatch(site ->
                        "JAVA_PARSE".equals(site.kind())
                                && "UNSUPPORTED".equals(site.disposition())
                                && "JAVA_PARSE_UNRESOLVED".equals(site.reasonCode())
                                && "src/main/java/example/inventory/Java17Only.java"
                                .equals(site.locator().path())),
                "Java 17-only syntax under the Java 8 profile must be a parse gap");
        assertFalse(understanding.repositoryModel().nodes().stream().anyMatch(node ->
                        "JAVA_TYPE".equals(node.kind())
                                && "src/main/java/example/inventory/Java17Only.java"
                                .equals(node.locator().path())),
                "Java 17-only syntax must not become an exact Java type");
    }

    @Test
    void mapperLocationPatternDoesNotBindADeclaredDecoyXml() throws Exception {
        Path root = copyBaseSnapshot();
        Path decoy = root.resolve("src/main/resources/decoy/InventoryMapper.xml");
        Files.createDirectories(decoy.getParent());
        Files.copy(root.resolve(MAPPER_PATH), decoy);
        List<DeclaredFile> files = withAdditionalFile(root,
                "src/main/resources/decoy/InventoryMapper.xml",
                Files.readAllBytes(decoy), "XML");

        RepositoryUnderstanding understanding = understand(request(root, files, DEFAULT_BUDGET,
                JAVA17_PROFILE, JAVA17_DIGEST, REVISION, RECEIPT_ID));
        List<RepositoryEdge> configEdges = understanding.repositoryModel().edges().stream()
                .filter(edge -> "CONFIG_RESOLVES_MAPPER".equals(edge.kind()))
                .toList();

        assertEquals(1, configEdges.size(),
                "application mapper-locations must resolve exactly one matching XML");
        RepositoryNode target = node(understanding, configEdges.get(0).toNodeId());
        assertEquals(MAPPER_PATH, target.locator().path());
        assertFalse(configEdges.stream().anyMatch(edge ->
                        "src/main/resources/decoy/InventoryMapper.xml"
                                .equals(node(understanding, edge.toNodeId()).locator().path())),
                "a declared XML outside mappers/*.xml must remain unbound");
    }

    @Test
    void callCapabilitySitesContainOnlyTheirReachableHttpOwner() throws Exception {
        Path root = copyBaseSnapshot();
        String controller = "package example.second;\n"
                + "import org.springframework.web.bind.annotation.PostMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                + "import org.springframework.web.bind.annotation.RestController;\n"
                + "@RestController\n"
                + "@RequestMapping(\"/second\")\n"
                + "final class SecondController {\n"
                + "  private final SecondService service;\n"
                + "  SecondController(SecondService service) { this.service = service; }\n"
                + "  @PostMapping\n"
                + "  void execute() { service.reserve(); }\n"
                + "}\n";
        String service = "package example.second;\n"
                + "final class SecondService {\n"
                + "  void reserve() {}\n"
                + "}\n";
        List<DeclaredFile> files = withFiles(root, Map.of(
                "src/main/java/example/second/SecondController.java", controller,
                "src/main/java/example/second/SecondService.java", service));

        RepositoryUnderstanding understanding = understand(request(root, files, DEFAULT_BUDGET,
                JAVA17_PROFILE, JAVA17_DIGEST, REVISION, RECEIPT_ID));
        Map<String, String> routeToEntryId = understanding.repositoryModel().entries().stream()
                .collect(Collectors.toMap(RepositoryEntry::route, RepositoryEntry::entryId));
        assertEquals(Set.of("/reservations", "/second"), routeToEntryId.keySet());

        long directSiteCount = sites(understanding).stream()
                .filter(site -> "DIRECT_FIELD_CALL".equals(site.kind())).count();
        assertEquals(4, directSiteCount,
                "the two entries must expose the three original and one second call sites");
        for (CapabilitySite site : sites(understanding)) {
            if (!"DIRECT_FIELD_CALL".equals(site.kind())) {
                continue;
            }
            String expectedRoute = site.locator().path().contains("SecondController")
                    ? "/second" : "/reservations";
            assertEquals(Set.of(routeToEntryId.get(expectedRoute)), Set.copyOf(site.entryIds()),
                    "a call site must list only its reachable HTTP owner: " + site.locator());
        }
    }

    @Test
    void allM2IdsChangeWhenSnapshotIdentityChanges() throws Exception {
        Path firstRoot = copyBaseSnapshot();
        Path revisionRoot = copyBaseSnapshot();
        Path bytesRoot = copyBaseSnapshot();
        String originalService = Files.readString(bytesRoot.resolve(SERVICE_PATH));
        Files.writeString(bytesRoot.resolve(SERVICE_PATH),
                originalService.replace("int available =", "int available  ="));

        RepositoryUnderstanding first = understand(request(firstRoot, baseDeclarations(),
                DEFAULT_BUDGET, JAVA17_PROFILE, JAVA17_DIGEST, "revision-a", "receipt-a"));
        RepositoryUnderstanding revision = understand(request(revisionRoot, baseDeclarations(),
                DEFAULT_BUDGET, JAVA17_PROFILE, JAVA17_DIGEST, "revision-b", "receipt-b"));
        List<DeclaredFile> byteFiles = withFiles(bytesRoot,
                Map.of(SERVICE_PATH, Files.readString(bytesRoot.resolve(SERVICE_PATH))));
        RepositoryUnderstanding bytes = understand(request(bytesRoot, byteFiles, DEFAULT_BUDGET,
                JAVA17_PROFILE, JAVA17_DIGEST, "revision-a", "receipt-a"));

        assertNotEquals(first.snapshotId(), revision.snapshotId());
        assertNotEquals(nodeIds(first), nodeIds(revision));
        assertNotEquals(edgeIds(first), edgeIds(revision));
        assertNotEquals(siteIds(first), siteIds(revision));
        assertNotEquals(first.repositoryModel().repositoryModelId(),
                revision.repositoryModel().repositoryModelId());
        assertNotEquals(first.capabilityReport().capabilityReportId(),
                revision.capabilityReport().capabilityReportId());

        assertNotEquals(first.snapshotId(), bytes.snapshotId());
        assertNotEquals(nodeIds(first), nodeIds(bytes));
        assertNotEquals(edgeIds(first), edgeIds(bytes));
        assertNotEquals(siteIds(first), siteIds(bytes));
        assertNotEquals(first.repositoryModel().repositoryModelId(),
                bytes.repositoryModel().repositoryModelId());
        assertNotEquals(first.capabilityReport().capabilityReportId(),
                bytes.capabilityReport().capabilityReportId());
    }

    @Test
    void locatorEndIsExclusiveAndSpanIsTheSemanticByteSlice() throws Exception {
        Path root = copyBaseSnapshot();
        RepositoryUnderstanding understanding = understand(request(root, baseDeclarations(),
                DEFAULT_BUDGET, JAVA17_PROFILE, JAVA17_DIGEST, REVISION, RECEIPT_ID));
        RepositoryNode call = understanding.repositoryModel().nodes().stream()
                .filter(node -> "JAVA_CALL".equals(node.kind()))
                .filter(node -> ("v1:" + CALL_TEXT).equals(node.canonicalValue()))
                .filter(node -> SERVICE_PATH.equals(node.locator().path()))
                .findFirst().orElseThrow();

        RepositoryLocator locator = call.locator();
        assertEquals(367, locator.startByte());
        assertEquals(388, locator.endByteExclusive());
        assertEquals(11, locator.startLine());
        assertEquals(30, locator.startColumn());
        assertEquals(11, locator.endLine());
        assertEquals(51, locator.endColumn(), "end column is an exclusive position");
        assertEquals(CALL_SPAN_SHA256, call.spanSha256());
        byte[] service = Files.readAllBytes(root.resolve(SERVICE_PATH));
        assertArrayEquals(CALL_TEXT.getBytes(StandardCharsets.UTF_8),
                java.util.Arrays.copyOfRange(service, locator.startByte(), locator.endByteExclusive()));
    }

    @Test
    void m1StopsWhenObservedFileBytesGrowPastBudgetBeforeM2() throws Exception {
        Path root = copyBaseSnapshot();
        byte[] large = "x".repeat(4096).getBytes(StandardCharsets.UTF_8);
        String path = "src/main/java/example/inventory/ObservedTooLarge.java";
        Path largePath = root.resolve(path);
        Files.createDirectories(largePath.getParent());
        Files.write(largePath, large);
        List<DeclaredFile> files = new ArrayList<>(baseDeclarations());
        files.add(new DeclaredFile(path, "JAVA", 64, sha256(large), "UTF-8"));
        ResourceBudget budget = new ResourceBudget(64, 4_194_304, 128,
                200_000, 100_000, 262_144, 100_000, 256);

        Stage01Exception failure = assertThrows(Stage01Exception.class,
                () -> understand(request(root, files, budget, JAVA17_PROFILE, JAVA17_DIGEST,
                        REVISION, RECEIPT_ID)));

        assertEquals("M1_RESOURCE_LIMIT_EXCEEDED", failure.code(),
                "M1 must stop on observed growth before M2 can parse any source");
    }

    @Test
    void astBudgetDoesNotEmitExactJavaNodesBeyondTheLimit() throws Exception {
        Path root = copyBaseSnapshot();
        ResourceBudget budget = new ResourceBudget(64, 4_194_304, 524_288,
                1, 100_000, 262_144, 100_000, 256);
        RepositoryUnderstanding understanding = understand(request(root, baseDeclarations(), budget,
                JAVA17_PROFILE, JAVA17_DIGEST, REVISION, RECEIPT_ID));

        assertOverLimit(understanding, "JAVA_AST", RESERVATION_CONTROLLER_PATH, "AST_NODE_LIMIT");
        assertEquals(3, countSites(understanding, "JAVA_AST", "OVER_LIMIT"));
        assertFalse(understanding.repositoryModel().nodes().stream().anyMatch(node ->
                        node.locator().path().endsWith(".java")),
                "AST limit must prevent exact Java nodes from being emitted");
        assertEquals(3, understanding.capabilityReport().coverage().overLimitReachableSites());
    }

    @Test
    void xmlBudgetDoesNotEmitExactMapperNodesBeyondTheLimit() throws Exception {
        Path root = copyBaseSnapshot();
        ResourceBudget budget = new ResourceBudget(64, 4_194_304, 524_288,
                200_000, 1, 262_144, 100_000, 256);
        RepositoryUnderstanding understanding = understand(request(root, baseDeclarations(), budget,
                JAVA17_PROFILE, JAVA17_DIGEST, REVISION, RECEIPT_ID));

        assertOverLimit(understanding, "MYBATIS_XML", MAPPER_PATH, "XML_NODE_LIMIT");
        assertFalse(understanding.repositoryModel().nodes().stream().anyMatch(node ->
                        MAPPER_PATH.equals(node.locator().path())),
                "XML limit must prevent namespace, statement, and SQL nodes");
        assertEquals(1, countSites(understanding, "MYBATIS_XML", "OVER_LIMIT"));
        assertEquals(1, understanding.capabilityReport().coverage().overLimitReachableSites());
    }

    private static RepositoryUnderstanding understand(FrozenRepositoryRequest request) {
        return new Stage01Analyzer().understand(request);
    }

    private static RepositoryNode node(RepositoryUnderstanding understanding, String nodeId) {
        return understanding.repositoryModel().nodes().stream()
                .filter(candidate -> nodeId.equals(candidate.nodeId()))
                .findFirst().orElseThrow();
    }

    private static List<CapabilitySite> sites(RepositoryUnderstanding understanding) {
        return understanding.capabilityReport().sites();
    }

    private static Set<String> nodeIds(RepositoryUnderstanding understanding) {
        return understanding.repositoryModel().nodes().stream()
                .map(RepositoryNode::nodeId).collect(Collectors.toSet());
    }

    private static Set<String> edgeIds(RepositoryUnderstanding understanding) {
        return understanding.repositoryModel().edges().stream()
                .map(RepositoryEdge::edgeId).collect(Collectors.toSet());
    }

    private static Set<String> siteIds(RepositoryUnderstanding understanding) {
        return sites(understanding).stream().map(CapabilitySite::siteId).collect(Collectors.toSet());
    }

    private static void assertOverLimit(RepositoryUnderstanding understanding, String kind,
                                        String path, String reason) {
        assertTrue(sites(understanding).stream().anyMatch(site -> kind.equals(site.kind())
                        && path.equals(site.locator().path())
                        && "OVER_LIMIT".equals(site.disposition())
                        && reason.equals(site.reasonCode())),
                () -> "expected OVER_LIMIT/" + reason + " at " + path);
    }

    private static long countSites(RepositoryUnderstanding understanding, String kind,
                                   String disposition) {
        return sites(understanding).stream()
                .filter(site -> kind.equals(site.kind()))
                .filter(site -> disposition.equals(site.disposition()))
                .count();
    }

    private static Path copyBaseSnapshot() throws IOException {
        Path destination = Files.createTempDirectory("stage01-integrity-").resolve("snapshot");
        for (FileSpec file : BASE_FILES) {
            Path target = destination.resolve(file.path());
            Files.createDirectories(target.getParent());
            String resource = RESOURCE_ROOT + "/" + file.path();
            try (InputStream input = RepositoryIntegrityRegressionTest.class.getClassLoader()
                    .getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IOException("missing test fixture resource " + resource);
                }
                Files.copy(input, target);
            }
        }
        return destination;
    }

    private static List<DeclaredFile> baseDeclarations() {
        return BASE_FILES.stream()
                .map(file -> new DeclaredFile(file.path(), file.mediaType(), file.sizeBytes(),
                        file.sha256(), "UTF-8"))
                .toList();
    }

    private static List<DeclaredFile> withFiles(Path root, Map<String, String> replacements)
            throws IOException {
        List<DeclaredFile> files = new ArrayList<>(baseDeclarations());
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            byte[] bytes = replacement.getValue().getBytes(StandardCharsets.UTF_8);
            Path target = root.resolve(replacement.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            files.removeIf(file -> replacement.getKey().equals(file.path()));
            files.add(new DeclaredFile(replacement.getKey(), mediaType(replacement.getKey()),
                    bytes.length, sha256(bytes), "UTF-8"));
        }
        return List.copyOf(files);
    }

    private static List<DeclaredFile> withAdditionalFile(Path root, String path, byte[] bytes,
                                                          String mediaType) throws IOException {
        List<DeclaredFile> files = new ArrayList<>(baseDeclarations());
        files.add(new DeclaredFile(path, mediaType, bytes.length, sha256(bytes), "UTF-8"));
        return List.copyOf(files);
    }

    private static String mediaType(String path) {
        if (path.endsWith(".java")) {
            return "JAVA";
        }
        if (path.endsWith(".xml")) {
            return "XML";
        }
        if (path.endsWith(".yml") || path.endsWith(".yaml")) {
            return "YAML";
        }
        if (path.endsWith("pom.xml")) {
            return "MAVEN_POM";
        }
        throw new AssertionError("unknown test media type for " + path);
    }

    private static FrozenRepositoryRequest request(Path root, List<DeclaredFile> files,
                                                   ResourceBudget budget, String profileId,
                                                   String profileDigest, String revision,
                                                   String receiptId) {
        String inventory = inventorySha256(files);
        String receipt = sha256(("{\"boundRepositoryUrl\":\"" + REPOSITORY_URL
                + "\",\"boundRevision\":\"" + revision
                + "\",\"inventorySha256\":\"" + inventory
                + "\",\"kind\":\"SYNTHETIC_FIXTURE_MANIFEST\",\"receiptId\":\""
                + receiptId + "\"}").getBytes(StandardCharsets.UTF_8));
        return new FrozenRepositoryRequest(
                new Origin("SYNTHETIC_FIXTURE", REPOSITORY_URL, revision),
                new CaptureProof("SYNTHETIC_FIXTURE_MANIFEST", receiptId, REPOSITORY_URL,
                        revision, inventory, receipt), root,
                new InventoryScope("BOUNDED_PATH_SET", ".", files.size()), files, POLICY_ID,
                budget, new CapabilityProfileRef(profileId, profileDigest));
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
                    .append(",\"textEncoding\":\"").append(file.textEncoding())
                    .append("\"}");
        }
        canonical.append("],\"scope\":{\"declaredPathCount\":").append(files.size())
                .append(",\"kind\":\"BOUNDED_PATH_SET\",\"scopeRoot\":\".\"}}");
        return sha256(("declared-inventory-v1\n" + canonical)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record FileSpec(String path, String mediaType, long sizeBytes, String sha256) {
    }
}
