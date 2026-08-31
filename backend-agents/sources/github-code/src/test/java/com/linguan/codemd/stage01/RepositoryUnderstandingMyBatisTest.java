package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.linguan.codemd.stage01.M2TestSupport.assertCoverageEquation;
import static com.linguan.codemd.stage01.M2TestSupport.assertDynamicGap;
import static com.linguan.codemd.stage01.M2TestSupport.assertSiteDisposition;
import static com.linguan.codemd.stage01.M2TestSupport.exactMapperMethodBindings;
import static com.linguan.codemd.stage01.M2TestSupport.exactStatementSqlBindings;
import static com.linguan.codemd.stage01.M2TestSupport.member;
import static com.linguan.codemd.stage01.M2TestSupport.model;
import static com.linguan.codemd.stage01.M2TestSupport.nodeKindsAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M2 MyBatis/XML security and dynamic-SQL RED contracts. */
class RepositoryUnderstandingMyBatisTest {
    @Test
    void insertStatementIsUnsupportedWithoutExactStaticSqlNodes() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-insert-"));
        RepositoryUnderstanding understanding = M2TestSupport.understand(
                M2Fixtures.insertDeleteMapperRequest(snapshotRoot));
        Object repositoryModel = model(understanding);
        String xmlPath = "src/main/resources/mappers/InventoryMapper.xml";

        assertEquals(4, exactMapperMethodBindings(repositoryModel).size(),
                "all four declared mapper methods still bind to their XML statements");
        assertSiteDisposition(understanding, "STATIC_SQL", xmlPath, 15,
                "UNSUPPORTED", "UNSUPPORTED_SQL_OPERATION");
        assertTrue(exactStatementSqlBindings(repositoryModel, xmlPath, 15).isEmpty());
        assertFalse(nodeKindsAt(repositoryModel, xmlPath, 15, 18).stream()
                        .anyMatch(kind -> kind != null && kind.startsWith("SQL_")),
                "v0 insert gap must not expose exact SQL fragments");
        assertCoverageEquation(understanding);
    }

    @Test
    void deleteStatementIsUnsupportedWithoutExactStaticSqlNodes() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-delete-"));
        RepositoryUnderstanding understanding = M2TestSupport.understand(
                M2Fixtures.insertDeleteMapperRequest(snapshotRoot));
        Object repositoryModel = model(understanding);
        String xmlPath = "src/main/resources/mappers/InventoryMapper.xml";

        assertEquals(4, exactMapperMethodBindings(repositoryModel).size(),
                "all four declared mapper methods still bind to their XML statements");
        assertSiteDisposition(understanding, "STATIC_SQL", xmlPath, 19,
                "UNSUPPORTED", "UNSUPPORTED_SQL_OPERATION");
        assertTrue(exactStatementSqlBindings(repositoryModel, xmlPath, 19).isEmpty());
        assertFalse(nodeKindsAt(repositoryModel, xmlPath, 19, 21).stream()
                        .anyMatch(kind -> kind != null && kind.startsWith("SQL_")),
                "v0 delete gap must not expose exact SQL fragments");
        assertCoverageEquation(understanding);
    }

    @Test
    void duplicateStatementIdsInOneNamespaceAreAmbiguousWithoutExactMethodBinding()
            throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-duplicate-statement-"));
        RepositoryUnderstanding understanding = M2TestSupport.understand(
                M2Fixtures.duplicateMapperStatementRequest(snapshotRoot));
        Object repositoryModel = model(understanding);
        String xmlPath = "src/main/resources/mappers/InventoryMapper.xml";

        assertEquals(java.util.Set.of(
                        "src/main/java/example/inventory/InventoryMapper.java:8"
                                + " -> src/main/resources/mappers/InventoryMapper.xml:5"),
                exactMapperMethodBindings(repositoryModel),
                "both duplicate addReservation statements must reject exact binding");
        assertSiteDisposition(understanding, "MAPPER_METHOD_STATEMENT", xmlPath, 10,
                "AMBIGUOUS", "AMBIGUOUS_MAPPER_STATEMENT");
        assertSiteDisposition(understanding, "MAPPER_METHOD_STATEMENT", xmlPath, 15,
                "AMBIGUOUS", "AMBIGUOUS_MAPPER_STATEMENT");
        assertTrue(exactStatementSqlBindings(repositoryModel, xmlPath, 10).isEmpty());
        assertTrue(exactStatementSqlBindings(repositoryModel, xmlPath, 15).isEmpty());
        assertCoverageEquation(understanding);
    }

    @Test
    void standardMyBatisDoctypeBindsBothStatementsWithoutExternalRetrieval() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-doctype-"));
        ProxySelector previous = ProxySelector.getDefault();
        boolean[] networkAttempted = {false};
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                networkAttempted[0] = true;
                throw new AssertionError("M2 attempted external XML retrieval: " + uri);
            }

            @Override
            public void connectFailed(URI uri, java.net.SocketAddress address,
                                      java.io.IOException failure) {
                networkAttempted[0] = true;
                throw new AssertionError("M2 attempted external XML retrieval: " + uri, failure);
            }
        });
        try {
            RepositoryUnderstanding understanding = M2TestSupport.understand(snapshotRoot);
            Object repositoryModel = model(understanding);
            assertEquals(2, exactMapperMethodBindings(repositoryModel).size());
            assertTrue(exactMapperMethodBindings(repositoryModel).stream()
                    .anyMatch(binding -> binding.endsWith("InventoryMapper.xml:5")));
            assertTrue(exactMapperMethodBindings(repositoryModel).stream()
                    .anyMatch(binding -> binding.endsWith("InventoryMapper.xml:10")));
            assertFalse(networkAttempted[0],
                    "standard MyBatis DOCTYPE must parse with all external resolution disabled");
            assertCoverageEquation(understanding);
        } finally {
            ProxySelector.setDefault(previous);
        }
    }

    @Test
    void dynamicIfAndDollarSqlIsAnExplicitGapWithoutExactSqlBinding() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m2-dynamic-"));
        FrozenRepositoryRequest request = M2Fixtures.dynamicMapperRequest(snapshotRoot);

        RepositoryUnderstanding understanding = M2TestSupport.understand(request);
        Object repositoryModel = model(understanding);
        String xmlPath = "src/main/resources/mappers/InventoryMapper.xml";

        assertDynamicGap(understanding, xmlPath, 10);
        assertTrue(exactStatementSqlBindings(repositoryModel, xmlPath, 10).isEmpty(),
                "dynamic update must not receive an exact statement-to-SQL binding");
        assertEquals(2, exactMapperMethodBindings(repositoryModel).size(),
                "Java mapper method->XML statement identity may remain exact");
        assertCoverageEquation(understanding);
        Object coverage = member(member(understanding, "capabilityReport"), "coverage");
        assertTrue(((Number) member(coverage, "unsupportedReachableSites")).intValue() >= 1);
    }
}
