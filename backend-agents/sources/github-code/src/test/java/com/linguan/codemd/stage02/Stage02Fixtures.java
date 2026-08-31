package com.linguan.codemd.stage02;

import com.linguan.codemd.stage01.CapabilityProfileRef;
import com.linguan.codemd.stage01.CaptureProof;
import com.linguan.codemd.stage01.DeclaredFile;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.GapExpectationProfileRef;
import com.linguan.codemd.stage01.InventoryScope;
import com.linguan.codemd.stage01.Origin;
import com.linguan.codemd.stage01.ResourceBudget;
import com.linguan.codemd.stage01.Stage01Request;

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
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * Test-only Stage 02 requests backed by the frozen six-file reservation
 * inventory. The helper deliberately rebuilds the receipt and inventory
 * material instead of asking production code to bless a mutation.
 */
final class Stage02Fixtures {
    static final String REPOSITORY_URL =
            "https://example.invalid/synthetic/inventory-reservation.git";
    static final String REVISION = "synthetic-revision-1";
    static final String CAPTURE_RECEIPT_ID = "reservation-v1-manifest";
    static final String CAPABILITY_PROFILE_ID = "java17-springmvc-mybatis-static-v0";
    static final String CAPABILITY_PROFILE_SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String GAP_PROFILE_ID = "gap-expectation-profile-v1";
    static final String GAP_PROFILE_SHA256 =
            "e63f976bba3fcc0acbc62c3b72f0a924d539d240d69ca86b7a598905fafc0f8a";
    static final String FLOW_PROFILE_ID = "entry-rooted-sync-flow-v1";
    static final String FLOW_PROFILE_SHA256 = sha256Text(FLOW_PROFILE_ID + "\n");
    static final String EVIDENCE_PROFILE_ID = "model-evidence-projection-v1";
    static final String EVIDENCE_PROFILE_SHA256 = sha256Text(EVIDENCE_PROFILE_ID + "\n");

    private static final String RESOURCE_ROOT = "stage01/reservation-v1";
    private static final String MANIFEST = RESOURCE_ROOT + "/fixture-manifest.properties";

    private Stage02Fixtures() {
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

    static Stage02Request request(Path snapshotRoot) {
        return request(snapshotRoot, defaultBudget());
    }

    static Stage02Request request(Path snapshotRoot, Stage02ResourceBudget budget) {
        return request(stage01Request(snapshotRoot), budget);
    }

    static Stage02Request request(Stage01Request stage01Request, Stage02ResourceBudget budget) {
        return new Stage02Request("stage02-request-v1", stage01Request,
                "stage01-result:" + "0".repeat(64),
                new FlowCompilationProfileRef(FLOW_PROFILE_ID, FLOW_PROFILE_SHA256),
                new EvidenceProjectionProfileRef(EVIDENCE_PROFILE_ID, EVIDENCE_PROFILE_SHA256),
                budget);
    }

    static Stage02Request requestWithExpectedResult(Path snapshotRoot, String expectedResultId) {
        return requestWithExpectedResult(stage01Request(snapshotRoot), expectedResultId);
    }

    /**
     * Rebinds only the expected replay identity while preserving the supplied
     * Stage 01 request, including mutation-specific declarations and hashes.
     */
    static Stage02Request requestWithExpectedResult(Stage01Request stage01Request,
                                                    String expectedResultId) {
        Stage02Request request = request(stage01Request, defaultBudget());
        return new Stage02Request(request.schemaVersion(), request.stage01Request(), expectedResultId,
                request.flowCompilationProfileRef(), request.evidenceProjectionProfileRef(),
                request.resourceBudget());
    }

    static Stage02Request requestWithFiles(Path snapshotRoot, List<DeclaredFile> files,
                                           Stage02ResourceBudget budget) {
        return request(stage01Request(snapshotRoot, files, defaultStage01Budget()), budget);
    }

    static Stage02Request requestWithStage01Budget(Path snapshotRoot, ResourceBudget budget) {
        return request(stage01Request(snapshotRoot, declaredFiles(), budget), defaultBudget());
    }

    static Stage02Request requestWithMutation(Path snapshotRoot, String path,
                                              UnaryOperator<String> mutation) throws IOException {
        String original = Files.readString(snapshotRoot.resolve(path), StandardCharsets.UTF_8);
        String changed = mutation.apply(original);
        if (original.equals(changed)) {
            throw new AssertionError("mutation did not change " + path);
        }
        Files.writeString(snapshotRoot.resolve(path), changed, StandardCharsets.UTF_8);

        List<DeclaredFile> declarations = new ArrayList<>(declaredFiles());
        declarations.removeIf(file -> file.path().equals(path));
        FileSpec expected = expectedFile(path);
        byte[] bytes = changed.getBytes(StandardCharsets.UTF_8);
        declarations.add(new DeclaredFile(path, expected.mediaType(), bytes.length,
                sha256(bytes), expected.textEncoding()));
        return requestWithFiles(snapshotRoot, declarations, defaultBudget());
    }

    static Stage02Request requestWithAdditionalController(Path snapshotRoot) throws IOException {
        String path = "src/main/java/example/inventory/SecondReservationController.java";
        String source = "package example.inventory;\n"
                + "import org.springframework.web.bind.annotation.PostMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestBody;\n"
                + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                + "import org.springframework.web.bind.annotation.RestController;\n"
                + "@RestController\n"
                + "@RequestMapping(\"/second-reservations\")\n"
                + "final class SecondReservationController {\n"
                + "  private final ReservationService service;\n"
                + "  SecondReservationController(ReservationService service) { this.service = service; }\n"
                + "  @PostMapping\n"
                + "  ReservationReceipt reserve(@RequestBody ReservationRequest request) {\n"
                + "    return service.reserve(request.sku(), request.quantity());\n"
                + "  }\n"
                + "}\n";
        Path target = snapshotRoot.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, source, StandardCharsets.UTF_8);
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        List<DeclaredFile> declarations = new ArrayList<>(declaredFiles());
        declarations.add(new DeclaredFile(path, "JAVA", bytes.length, sha256(bytes), "UTF-8"));
        return requestWithFiles(snapshotRoot, declarations, defaultBudget());
    }

    static Stage02ResourceBudget defaultBudget() {
        return new Stage02ResourceBudget(128, 64, 20_000, 40_000, 128,
                64, 16_384, 262_144, 256);
    }

    static Stage02ResourceBudget budget(int maxFlows, int maxOutcomesPerFlow,
                                        int maxFlowNodes, int maxFlowEdges,
                                        int maxCapsules, int maxSpansPerCapsule,
                                        int maxSpanBytes, int maxCapsuleUtf8Bytes,
                                        int maxTraversalDepth) {
        return new Stage02ResourceBudget(maxFlows, maxOutcomesPerFlow, maxFlowNodes,
                maxFlowEdges, maxCapsules, maxSpansPerCapsule, maxSpanBytes,
                maxCapsuleUtf8Bytes, maxTraversalDepth);
    }

    static List<DeclaredFile> declaredFiles() {
        return expectedFiles().stream()
                .map(file -> new DeclaredFile(file.path(), file.mediaType(), file.sizeBytes(),
                        file.sha256(), file.textEncoding()))
                .toList();
    }

    static Stage01Request stage01Request(Path snapshotRoot) {
        return stage01Request(snapshotRoot, declaredFiles(), defaultStage01Budget());
    }

    private static Stage01Request stage01Request(Path snapshotRoot, List<DeclaredFile> files,
                                                  ResourceBudget budget) {
        String inventorySha256 = inventorySha256(files);
        String receiptSha256 = receiptSha256(inventorySha256);
        FrozenRepositoryRequest frozen = new FrozenRepositoryRequest(
                new Origin("SYNTHETIC_FIXTURE", REPOSITORY_URL, REVISION),
                new CaptureProof("SYNTHETIC_FIXTURE_MANIFEST", CAPTURE_RECEIPT_ID,
                        REPOSITORY_URL, REVISION, inventorySha256, receiptSha256),
                snapshotRoot, new InventoryScope("BOUNDED_PATH_SET", ".", files.size()),
                files, "frozen-snapshot-v1", budget,
                new CapabilityProfileRef(CAPABILITY_PROFILE_ID, CAPABILITY_PROFILE_SHA256));
        return new Stage01Request("stage01-request-v1", frozen,
                new GapExpectationProfileRef(GAP_PROFILE_ID, GAP_PROFILE_SHA256));
    }

    private static ResourceBudget defaultStage01Budget() {
        return new ResourceBudget(64, 4_194_304, 524_288, 200_000, 100_000,
                262_144, 100_000, 256);
    }

    private static List<FileSpec> expectedFiles() {
        Properties manifest = new Properties();
        try (InputStream input = Stage02Fixtures.class.getClassLoader()
                .getResourceAsStream(MANIFEST)) {
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
            files.add(new FileSpec(
                    manifest.getProperty(prefix + "path"),
                    manifest.getProperty(prefix + "mediaType"),
                    Long.parseLong(manifest.getProperty(prefix + "sizeBytes")),
                    manifest.getProperty(prefix + "sha256"),
                    manifest.getProperty(prefix + "textEncoding")));
        }
        return List.copyOf(files);
    }

    private static FileSpec expectedFile(String path) {
        return expectedFiles().stream().filter(file -> file.path().equals(path)).findFirst()
                .orElseThrow(() -> new AssertionError("unknown fixture path: " + path));
    }

    private static Path resourceRoot() {
        try {
            return Path.of(Stage02Fixtures.class.getClassLoader()
                    .getResource(RESOURCE_ROOT).toURI());
        } catch (URISyntaxException | NullPointerException failure) {
            throw new AssertionError("missing fixture resource root: " + RESOURCE_ROOT, failure);
        }
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
            canonical.append("{\"mediaType\":\"").append(json(file.mediaType()))
                    .append("\",\"path\":\"").append(json(file.path()))
                    .append("\",\"sha256\":\"").append(json(file.sha256()))
                    .append("\",\"sizeBytes\":").append(file.sizeBytes())
                    .append(",\"textEncoding\":\"").append(json(file.textEncoding()))
                    .append("\"}");
        }
        canonical.append("],\"scope\":{\"declaredPathCount\":")
                .append(files.size())
                .append(",\"kind\":\"BOUNDED_PATH_SET\",\"scopeRoot\":\".\"}}");
        return sha256(("declared-inventory-v1\n" + canonical).getBytes(StandardCharsets.UTF_8));
    }

    private static String receiptSha256(String inventorySha256) {
        String canonical = "{\"boundRepositoryUrl\":\"" + json(REPOSITORY_URL)
                + "\",\"boundRevision\":\"" + json(REVISION)
                + "\",\"inventorySha256\":\"" + inventorySha256
                + "\",\"kind\":\"SYNTHETIC_FIXTURE_MANIFEST\",\"receiptId\":\""
                + json(CAPTURE_RECEIPT_ID) + "\"}";
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String sha256Text(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private record FileSpec(String path, String mediaType, long sizeBytes,
                            String sha256, String textEncoding) {
    }
}
