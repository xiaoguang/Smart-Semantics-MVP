package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.linguan.codemd.stage01.M2TestSupport.assertAmbiguousGap;
import static com.linguan.codemd.stage01.M2TestSupport.assertCoverageEquation;
import static com.linguan.codemd.stage01.M2TestSupport.assertOverLimitSite;
import static com.linguan.codemd.stage01.M2TestSupport.assertUnresolvedCallGap;
import static com.linguan.codemd.stage01.M2TestSupport.exactCallBindings;
import static com.linguan.codemd.stage01.M2TestSupport.member;
import static com.linguan.codemd.stage01.M2TestSupport.model;
import static com.linguan.codemd.stage01.M2TestSupport.number;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M2 ambiguity and capability-partition RED contracts. */
class CapabilityAccountingTest {
    @Test
    void wildcardImportReceiverCannotProduceAnExactCallTarget() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-wildcard-import-"));
        RepositoryUnderstanding understanding = M2TestSupport.understand(
                M2Fixtures.wildcardImportRequest(snapshotRoot));
        String callPath = "src/main/java/wildcard/WildcardService.java";

        assertFalse(exactCallBindings(model(understanding)).stream()
                        .anyMatch(binding -> binding.startsWith(callPath + ":6")),
                "wildcard import type resolution must not be guessed as exact");
        assertUnresolvedCallGap(understanding, callPath, 6, "WILDCARD");
        assertCoverageEquation(understanding);
    }

    @Test
    void callArityMismatchCannotProduceAnExactCallTarget() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-call-arity-"));
        RepositoryUnderstanding understanding = M2TestSupport.understand(
                M2Fixtures.arityMismatchRequest(snapshotRoot));
        String callPath = "src/main/java/example/inventory/ReservationService.java";

        assertFalse(exactCallBindings(model(understanding)).stream()
                        .anyMatch(binding -> binding.startsWith(callPath + ":11")),
                "a two-argument call cannot bind exactly to one-argument findBySku");
        assertUnresolvedCallGap(understanding, callPath, 11, "ARITY");
        assertCoverageEquation(understanding);
    }

    @Test
    void maxAstNodesBudgetProducesAnOverLimitJavaSiteAndCoverageCount() throws Exception {
        Path astRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-budget-ast-"));
        RepositoryUnderstanding ast = M2TestSupport.understand(M2Fixtures.requestWithBudget(
                astRoot, new ResourceBudget(64, 4_194_304, 524_288,
                        1, 100_000, 262_144, 100_000, 256)));
        assertOverLimitSite(ast, "JAVA_AST",
                "src/main/java/example/inventory/ReservationController.java", 1, "AST");
        assertOverLimitCoverage(ast);
    }

    @Test
    void maxXmlNodesBudgetProducesAnOverLimitXmlSiteAndCoverageCount() throws Exception {
        Path xmlRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-budget-xml-"));
        RepositoryUnderstanding xml = M2TestSupport.understand(M2Fixtures.requestWithBudget(
                xmlRoot, new ResourceBudget(64, 4_194_304, 524_288,
                        200_000, 1, 262_144, 100_000, 256)));
        assertOverLimitSite(xml, "MYBATIS_XML",
                "src/main/resources/mappers/InventoryMapper.xml", 1, "XML");
        assertOverLimitCoverage(xml);
    }

    @Test
    void maxSqlCharsBudgetProducesAnOverLimitSqlSiteAndCoverageCount() throws Exception {
        Path sqlRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-budget-sql-"));
        RepositoryUnderstanding sql = M2TestSupport.understand(M2Fixtures.requestWithBudget(
                sqlRoot, new ResourceBudget(64, 4_194_304, 524_288,
                        200_000, 100_000, 1, 100_000, 256)));
        assertOverLimitSite(sql, "STATIC_SQL",
                "src/main/resources/mappers/InventoryMapper.xml", 5, "SQL");
        assertOverLimitCoverage(sql);
    }

    @Test
    void maxControlFlowNodesBudgetProducesAnOverLimitCfgSiteAndCoverageCount() throws Exception {
        Path cfgRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-budget-cfg-"));
        RepositoryUnderstanding cfg = M2TestSupport.understand(M2Fixtures.requestWithBudget(
                cfgRoot, new ResourceBudget(64, 4_194_304, 524_288,
                        200_000, 100_000, 262_144, 1, 256)));
        assertOverLimitSite(cfg, "CONTROL_FLOW",
                "src/main/java/example/inventory/ReservationService.java", 9, "CONTROL");
        assertOverLimitCoverage(cfg);
    }

    @Test
    void overloadedMapperCallIsAmbiguousAndNeverAnExactCall() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-overload-"));
        FrozenRepositoryRequest request = M2Fixtures.overloadedMapperRequest(snapshotRoot);

        RepositoryUnderstanding understanding = M2TestSupport.understand(request);
        assertFalse(exactCallBindings(model(understanding)).stream()
                        .anyMatch(binding -> binding.startsWith(
                                "src/main/java/example/inventory/ReservationService.java:11")),
                "an overloaded null receiver call must not be admitted as exact");
        assertAmbiguousGap(understanding,
                "src/main/java/example/inventory/ReservationService.java", 11);
        assertCoverageEquation(understanding);
    }

    @Test
    void sameSimpleNameReceiverIsAmbiguousAndNeverAnExactCall() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-same-name-"));
        FrozenRepositoryRequest request = M2Fixtures.sameSimpleNameReceiverRequest(snapshotRoot);

        RepositoryUnderstanding understanding = M2TestSupport.understand(request);
        assertFalse(exactCallBindings(model(understanding)).stream()
                        .anyMatch(binding -> binding.startsWith(
                                "src/main/java/ambiguous/AmbiguousService.java:")),
                "same-simple-name imported receivers must not be guessed");
        assertAmbiguousGap(understanding,
                "src/main/java/ambiguous/AmbiguousService.java", 7);
        assertCoverageEquation(understanding);
        assertTrue(M2TestSupport.sites(understanding).stream()
                        .anyMatch(site -> "AMBIGUOUS".equals(M2TestSupport.text(site, "disposition"))),
                "the ambiguous site must contribute to the coverage denominator");
    }

    private static void assertOverLimitCoverage(RepositoryUnderstanding understanding) {
        assertCoverageEquation(understanding);
        Object coverage = member(member(understanding, "capabilityReport"), "coverage");
        assertTrue(number(coverage, "overLimitReachableSites") >= 1,
                "an OVER_LIMIT site must contribute to coverage");
    }
}
