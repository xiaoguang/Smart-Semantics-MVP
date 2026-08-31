package com.linguan.codemd.stage02;

import com.linguan.codemd.stage01.CapabilityProfileRef;
import com.linguan.codemd.stage01.CaptureProof;
import com.linguan.codemd.stage01.DeclaredFile;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.GapExpectationProfileRef;
import com.linguan.codemd.stage01.InventoryScope;
import com.linguan.codemd.stage01.Origin;
import com.linguan.codemd.stage01.ResourceBudget;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bounded honest disposition for the approved fixed jshERP eight-file
 * inventory. This test never builds, runs, refreshes, or scans the customer
 * checkout and does not require a complete Flow/Capsule to count as success.
 */
class JshErpStage02AcceptanceTest {
    private static final String FIXED_REVISION =
            "8c30ce7861570458920175e200bb2a6442713580";
    private static final String REPOSITORY_URL = "https://github.com/jishenghua/jshERP.git";
    private static final String CHECKOUT_DIRECTORY =
            ".workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580";
    private static final String CAPABILITY_PROFILE_ID = "java8-springmvc-mybatis-static-v0";
    private static final String CAPABILITY_PROFILE_SHA256 = sha256Text(
            CAPABILITY_PROFILE_ID + "\n");
    private static final String CAPTURE_RECEIPT_ID =
            "jshERP-8c30ce7861570458920175e200bb2a6442713580-eight-file-receipt";
    private static final String GAP_PROFILE_ID = "gap-expectation-profile-v1";
    private static final String GAP_PROFILE_SHA256 =
            "e63f976bba3fcc0acbc62c3b72f0a924d539d240d69ca86b7a598905fafc0f8a";
    private static final String CONTROLLER_PATH =
            "jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java";
    private static final String DYNAMIC_MAPPER_PATH =
            "jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml";
    private static final List<SourceSpec> SOURCE_SPECS = List.of(
            new SourceSpec("jshERP-boot/pom.xml", "MAVEN_POM"),
            new SourceSpec("jshERP-boot/src/main/resources/application.yml", "YAML"),
            new SourceSpec(CONTROLLER_PATH, "JAVA"),
            new SourceSpec("jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java", "JAVA"),
            new SourceSpec("jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHead.java", "JAVA"),
            new SourceSpec("jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHeadExample.java", "JAVA"),
            new SourceSpec("jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/DepotHeadMapper.java", "JAVA"),
            new SourceSpec(DYNAMIC_MAPPER_PATH, "XML"));
    private static final Set<String> INVENTORY_PATHS = SOURCE_SPECS.stream()
            .map(SourceSpec::path).collect(Collectors.toUnmodifiableSet());

    @Test
    void fixedEightFileCheckoutProducesHonestStage02EntryDisposition() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"), CHECKOUT_DIRECTORY)
                .toAbsolutePath().normalize();
        assumeFixedCheckout(root);
        Stage01Request stage01Request = request(root);
        Stage01Analyzer analyzer = new Stage01Analyzer();
        Stage01Result stage01 = analyzer.analyze(stage01Request);

        Stage02Request request = new Stage02Request("stage02-request-v1", stage01Request,
                stage01.stage01ResultId(),
                new FlowCompilationProfileRef(Stage02Fixtures.FLOW_PROFILE_ID,
                        Stage02Fixtures.FLOW_PROFILE_SHA256),
                new EvidenceProjectionProfileRef(Stage02Fixtures.EVIDENCE_PROFILE_ID,
                        Stage02Fixtures.EVIDENCE_PROFILE_SHA256),
                Stage02Fixtures.defaultBudget());
        Stage02Result result = new Stage02Compiler().compile(request);

        assertNotNull(result);
        assertEquals(stage01.stage01ResultId(), result.stage01ResultId());
        assertEquals(stage01.repositoryUnderstanding().repositoryModel().entries().size(),
                result.entryDispositions().size(),
                "every discovered jshERP entry needs exactly one disposition");
        assertEquals(stage01.repositoryUnderstanding().repositoryModel().entries().size(),
                result.coverageReport().discoveredEntryCoverage().denominator());
        long compiled = result.entryDispositions().stream()
                .filter(disposition -> "COMPILED".equals(disposition.disposition())).count();
        assertEquals(compiled, result.coverageReport().discoveredEntryCoverage().numerator());
        assertTrue(result.entryDispositions().stream().allMatch(disposition ->
                Set.of("COMPILED", "GAP", "EXCLUDED").contains(disposition.disposition())));

        // The fixed eight-file acceptance must remain honest about the current
        // reservation Fact registry: no guessed DepotHead Capsule is allowed.
        assertTrue(result.flowSlices().isEmpty(),
                "unsupported/insufficiently proven DepotHead flow must remain a blocking Gap");
        assertTrue(result.evidenceCapsules().isEmpty(),
                "a gapped jshERP entry cannot produce model Evidence");
        assertTrue(result.entryDispositions().stream().allMatch(disposition ->
                !"COMPILED".equals(disposition.disposition())
                        || result.flowSlices().stream().anyMatch(flow ->
                        flow.flowSliceId().equals(disposition.flowSliceId()))));
        for (EvidenceCapsule capsule : result.evidenceCapsules()) {
            assertTrue(capsule.modelEvidenceSpans().stream().allMatch(span ->
                    INVENTORY_PATHS.contains(span.locator().path())));
            assertFalse(capsule.modelEvidenceSpans().stream().anyMatch(span ->
                    span.locator().path().equals(DYNAMIC_MAPPER_PATH)));
        }
    }

    private static void assumeFixedCheckout(Path root) throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS),
                "BLOCKED: fixed jshERP checkout is absent");
        Assumptions.assumeTrue(FIXED_REVISION.equals(git(root, "rev-parse", "HEAD").trim()),
                "BLOCKED: fixed jshERP checkout revision drifted");
        Assumptions.assumeTrue(git(root, "status", "--porcelain").trim().isEmpty(),
                "BLOCKED: fixed jshERP checkout is not clean");
        for (SourceSpec spec : SOURCE_SPECS) {
            Assumptions.assumeTrue(Files.isRegularFile(root.resolve(spec.path()), LinkOption.NOFOLLOW_LINKS),
                    "BLOCKED: fixed checkout file is absent: " + spec.path());
        }
    }

    private static String git(Path root, String... args) throws Exception {
        String[] command = new String[args.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = root.toString();
        System.arraycopy(args, 0, command, 3, args.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), "fixed checkout git read failed: " + output);
        return output;
    }

    private static Stage01Request request(Path root) throws Exception {
        List<DeclaredFile> files = SOURCE_SPECS.stream()
                .map(spec -> declaredFile(root, spec))
                .sorted(Comparator.comparing(DeclaredFile::path)).toList();
        InventoryScope scope = new InventoryScope("BOUNDED_PATH_SET", ".", files.size());
        String inventorySha256 = sha256Text("declared-inventory-v1\n" + inventoryMaterial(scope, files));
        CaptureProof pending = new CaptureProof("UPSTREAM_CAPTURE_RECEIPT", CAPTURE_RECEIPT_ID,
                REPOSITORY_URL, FIXED_REVISION, inventorySha256, "pending");
        CaptureProof capture = new CaptureProof(pending.kind(), pending.receiptId(),
                pending.boundRepositoryUrl(), pending.boundRevision(), pending.inventorySha256(),
                sha256Text(receiptMaterial(pending)));
        FrozenRepositoryRequest frozen = new FrozenRepositoryRequest(
                new Origin("GIT_COMMIT", REPOSITORY_URL, FIXED_REVISION), capture, root, scope,
                files, "frozen-snapshot-v1", new ResourceBudget(64, 4_194_304, 524_288,
                200_000, 100_000, 262_144, 100_000, 256),
                new CapabilityProfileRef(CAPABILITY_PROFILE_ID, CAPABILITY_PROFILE_SHA256));
        return new Stage01Request("stage01-request-v1", frozen,
                new GapExpectationProfileRef(GAP_PROFILE_ID, GAP_PROFILE_SHA256));
    }

    private static DeclaredFile declaredFile(Path root, SourceSpec spec) {
        try {
            byte[] bytes = Files.readAllBytes(root.resolve(spec.path()));
            return new DeclaredFile(spec.path(), spec.mediaType(), bytes.length, sha256(bytes), "UTF-8");
        } catch (Exception failure) {
            throw new AssertionError("cannot read fixed source file " + spec.path(), failure);
        }
    }

    private static String inventoryMaterial(InventoryScope scope, List<DeclaredFile> files) {
        StringBuilder json = new StringBuilder("{\"files\":[");
        for (int index = 0; index < files.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            DeclaredFile file = files.get(index);
            json.append("{\"mediaType\":").append(quoted(file.mediaType()))
                    .append(",\"path\":").append(quoted(file.path()))
                    .append(",\"sha256\":").append(quoted(file.sha256()))
                    .append(",\"sizeBytes\":").append(file.sizeBytes())
                    .append(",\"textEncoding\":").append(quoted(file.textEncoding())).append('}');
        }
        return json.append("],\"scope\":{\"declaredPathCount\":")
                .append(scope.declaredPathCount()).append(",\"kind\":")
                .append(quoted(scope.kind())).append(",\"scopeRoot\":")
                .append(quoted(scope.scopeRoot())).append("}}").toString();
    }

    private static String receiptMaterial(CaptureProof proof) {
        return "{\"boundRepositoryUrl\":" + quoted(proof.boundRepositoryUrl())
                + ",\"boundRevision\":" + quoted(proof.boundRevision())
                + ",\"inventorySha256\":" + quoted(proof.inventorySha256())
                + ",\"kind\":" + quoted(proof.kind()) + ",\"receiptId\":"
                + quoted(proof.receiptId()) + "}";
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String sha256Text(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record SourceSpec(String path, String mediaType) {
    }
}
