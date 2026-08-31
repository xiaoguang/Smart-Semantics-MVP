package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static com.linguan.codemd.stage01.M2TestSupport.assertCoverageEquation;
import static com.linguan.codemd.stage01.M2TestSupport.controlFlows;
import static com.linguan.codemd.stage01.M2TestSupport.entries;
import static com.linguan.codemd.stage01.M2TestSupport.exactCallBindings;
import static com.linguan.codemd.stage01.M2TestSupport.exactMapperMethodBindings;
import static com.linguan.codemd.stage01.M2TestSupport.member;
import static com.linguan.codemd.stage01.M2TestSupport.model;
import static com.linguan.codemd.stage01.M2TestSupport.number;
import static com.linguan.codemd.stage01.M2TestSupport.terminalLocators;
import static com.linguan.codemd.stage01.M2TestSupport.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M2 route, cross-layer call, mapper binding, and CFG RED contracts. */
class RepositoryUnderstandingRouteCallTest {
    @Test
    void publicM2SeamReturnsUnderstandingAndDoesNotAcceptAnUnverifiedPath() throws Exception {
        java.lang.reflect.Method seam = Stage01Analyzer.class.getMethod(
                "understand", FrozenRepositoryRequest.class);

        assertEquals(RepositoryUnderstanding.class, seam.getReturnType());
        assertThrows(NoSuchMethodException.class,
                () -> Stage01Analyzer.class.getMethod("understand", Path.class),
                "callers must provide the receipt-bound FrozenRepositoryRequest, not a Path");
    }

    @Test
    void publicM2SeamRunsM1AndRejectsHashDriftBeforeSemanticUnderstanding() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-m1-gate-"));
        Path source = snapshotRoot.resolve(
                "src/main/java/example/inventory/InventoryMapper.java");
        byte[] tampered = Files.readAllBytes(source);
        tampered[0] = (byte) (tampered[0] ^ 1);
        Files.write(source, tampered);

        Stage01Exception failure = assertThrows(Stage01Exception.class,
                () -> new Stage01Analyzer().understand(Stage01Fixtures.request(snapshotRoot)));
        assertEquals("SOURCE_HASH_MISMATCH", failure.code(),
                "M2 must not parse or report a model from unverified bytes");
    }

    @Test
    void sixFileReservationBuildsRouteCallsMapperBindingsAndFourTerminals() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-route-"));

        RepositoryUnderstanding understanding = M2TestSupport.understand(snapshotRoot);
        Object repositoryModel = model(understanding);

        assertEquals(1, entries(repositoryModel).size(),
                "the synthetic inventory has one HTTP entry");
        Object entry = entries(repositoryModel).get(0);
        assertEquals("SPRING_MVC_HTTP", text(entry, "kind"));
        assertEquals("POST", text(entry, "httpMethod"));
        assertEquals("/reservations", text(entry, "route"));

        assertEquals(Set.of(
                        "src/main/java/example/inventory/ReservationController.java:15"
                                + " -> src/main/java/example/inventory/ReservationService.java:9",
                        "src/main/java/example/inventory/ReservationService.java:11"
                                + " -> src/main/java/example/inventory/InventoryMapper.java:8",
                        "src/main/java/example/inventory/ReservationService.java:14"
                                + " -> src/main/java/example/inventory/InventoryMapper.java:9"),
                exactCallBindings(repositoryModel),
                "Controller->Service and both Service->Mapper calls must be exact");
        assertEquals(Set.of(
                        "src/main/java/example/inventory/InventoryMapper.java:8"
                                + " -> src/main/resources/mappers/InventoryMapper.xml:5",
                        "src/main/java/example/inventory/InventoryMapper.java:9"
                                + " -> src/main/resources/mappers/InventoryMapper.xml:10"),
                exactMapperMethodBindings(repositoryModel),
                "both mapper methods must bind to their XML statements");

        assertEquals(Set.of(
                        "src/main/java/example/inventory/ReservationService.java:10",
                        "src/main/java/example/inventory/ReservationService.java:13",
                        "src/main/java/example/inventory/ReservationService.java:15",
                        "src/main/java/example/inventory/ReservationService.java:16"),
                terminalLocators(repositoryModel),
                "the service CFG has exactly four reachable terminals");
        assertEquals(1, controlFlows(repositoryModel).size());
        assertEquals(text(entry, "entryId"),
                text(controlFlows(repositoryModel).get(0), "entryId"));
        assertCoverageEquation(understanding);
        Object coverage = member(member(understanding, "capabilityReport"), "coverage");
        assertEquals(13, number(coverage, "reachableSemanticSites"));
        assertEquals(13, number(coverage, "supportedSemanticSites"));
        assertEquals(0, number(coverage, "unsupportedReachableSites"));
        assertEquals(0, number(coverage, "ambiguousReachableSites"));
        assertEquals(0, number(coverage, "overLimitReachableSites"));
    }

    @Test
    void undeclaredControllerUnderSnapshotRootCannotChangeUnderstandingIdentityOrGraph()
            throws Exception {
        Path cleanRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-inventory-clean-"));
        Path extraRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-inventory-extra-"));
        Path maliciousController = extraRoot.resolve(
                "src/main/java/example/inventory/MaliciousController.java");
        Files.createDirectories(maliciousController.getParent());
        Files.writeString(maliciousController,
                "package example.inventory;\n"
                        + "import org.springframework.web.bind.annotation.PostMapping;\n"
                        + "import org.springframework.web.bind.annotation.RestController;\n"
                        + "@RestController\n"
                        + "final class MaliciousController {\n"
                        + "  @PostMapping(\"/malicious\")\n"
                        + "  void execute() {}\n"
                        + "}\n");

        RepositoryUnderstanding clean = M2TestSupport.understand(cleanRoot);
        RepositoryUnderstanding withUndeclaredFile = M2TestSupport.understand(extraRoot);

        assertEquals(clean.snapshotId(), withUndeclaredFile.snapshotId(),
                "M1 identity is the declared inventory, not a directory scan");
        assertEquals(text(model(clean), "repositoryModelId"),
                text(model(withUndeclaredFile), "repositoryModelId"));
        assertEquals(text(member(clean, "capabilityReport"), "capabilityReportId"),
                text(member(withUndeclaredFile, "capabilityReport"), "capabilityReportId"));
        assertEquals(exactCallBindings(model(clean)),
                exactCallBindings(model(withUndeclaredFile)));
        assertEquals(1, entries(model(withUndeclaredFile)).size());
        assertTrue(entries(model(withUndeclaredFile)).stream()
                        .noneMatch(candidate -> "/malicious".equals(text(candidate, "route"))),
                "an undeclared controller must not become an HTTP entry");
    }

    @Test
    void localSameNameMappingAnnotationsWithoutSpringImportsCannotCreateAnExactEntry()
            throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-local-mapping-"));
        RepositoryUnderstanding understanding = M2TestSupport.understand(
                M2Fixtures.unresolvedLocalMappingRequest(snapshotRoot));

        assertEquals(1, entries(model(understanding)).size(),
                "only the imported Spring controller is an HTTP entry");
        assertTrue(entries(model(understanding)).stream()
                        .noneMatch(entry -> "/not-spring".equals(text(entry, "route"))),
                "same-simple-name local annotations are not Spring MVC annotations");
        assertCoverageEquation(understanding);
    }
}
