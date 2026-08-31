package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real, bounded acceptance for the fixed jshERP commit.  This test reads only
 * the eight declared files and never launches a customer process or build.
 */
class JshErpStage01AcceptanceTest {
    private static final String FIXED_REVISION =
            "8c30ce7861570458920175e200bb2a6442713580";
    private static final String REPOSITORY_URL = "https://github.com/jishenghua/jshERP.git";
    private static final String CHECKOUT_DIRECTORY =
            ".workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580";
    private static final String CAPABILITY_PROFILE_ID = "java8-springmvc-mybatis-static-v0";
    private static final String CAPABILITY_PROFILE_SHA256 = sha256(
            "java8-springmvc-mybatis-static-v0\n".getBytes(StandardCharsets.UTF_8));
    private static final String CAPTURE_RECEIPT_ID =
            "jshERP-8c30ce7861570458920175e200bb2a6442713580-eight-file-receipt";
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
            .map(SourceSpec::path).collect(java.util.stream.Collectors.toUnmodifiableSet());

    @Test
    void fixedCommitProducesHonestBoundedStage01Result() throws Exception {
        Path root = fixedCheckout();
        assumeCheckoutIsAvailable(root);
        FrozenRepositoryRequest request = request(root);
        Map<String, byte[]> currentBytes = currentBytes(root);

        byte[] mapperXml = currentBytes.get(DYNAMIC_MAPPER_PATH);
        String mapperText = new String(mapperXml, StandardCharsets.UTF_8);
        assertTrue(mapperText.contains("<!DOCTYPE mapper"),
                "the fixed mapper must exercise the standard MyBatis DOCTYPE path");

        Stage01Analyzer analyzer = new Stage01Analyzer();
        VerifiedSnapshot snapshot = analyzer.verify(request);
        assertEquals("verified-snapshot-v1", snapshot.schemaVersion());
        assertEquals("GIT_COMMIT", snapshot.origin().kind());
        assertEquals(REPOSITORY_URL, snapshot.origin().repositoryUrl());
        assertEquals(FIXED_REVISION, snapshot.origin().revision());
        assertEquals("UPSTREAM_CAPTURE_RECEIPT", snapshot.captureProof().kind());
        assertEquals("BOUNDED_PATH_SET", snapshot.inventoryScope().kind());
        assertEquals(".", snapshot.inventoryScope().scopeRoot());
        assertEquals(8, snapshot.inventoryScope().declaredPathCount());
        assertEquals(8, snapshot.sourceIntegrity().declaredFiles());
        assertEquals(8, snapshot.sourceIntegrity().verifiedFiles());
        assertEquals(8, snapshot.files().size());
        assertEquals(INVENTORY_PATHS, snapshot.files().stream().map(VerifiedFile::path)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        assertEquals(CAPABILITY_PROFILE_ID, snapshot.capabilityProfileRef().profileId());

        AtomicBoolean externalLookupAttempted = new AtomicBoolean();
        ProxySelector previousProxySelector = ProxySelector.getDefault();
        ProxySelector.setDefault(new TripwireProxySelector(externalLookupAttempted));
        Stage01Result first;
        Stage01Result second;
        try {
            first = analyzer.analyze(request);
            second = analyzer.analyze(request);
        } finally {
            ProxySelector.setDefault(previousProxySelector);
        }
        assertFalse(externalLookupAttempted.get(),
                "standard MyBatis DOCTYPE parsing must not perform external resolution");

        assertStableAcrossRuns(first, second);
        assertBoundedSnapshot(first.verifiedSnapshot());
        assertCapabilityAccounting(first.repositoryUnderstanding());
        assertProofClosure(first, currentBytes);
        assertLegacyFactsCannotBeCopied(first, currentBytes);
        assertDynamicMapperIsNotAdmittedAsStaticSql(first, currentBytes);
    }

    private static Path fixedCheckout() {
        return Path.of(System.getProperty("user.dir"), CHECKOUT_DIRECTORY)
                .toAbsolutePath().normalize();
    }

    private static void assumeCheckoutIsAvailable(Path root) {
        Assumptions.assumeTrue(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS),
                "BLOCKED: fixed jshERP checkout is absent: " + root);
        for (SourceSpec spec : SOURCE_SPECS) {
            Path source = root.resolve(spec.path());
            Assumptions.assumeTrue(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS),
                    "BLOCKED: fixed checkout file is absent or not regular: " + spec.path());
        }
    }

    private static FrozenRepositoryRequest request(Path root) throws Exception {
        List<DeclaredFile> files = SOURCE_SPECS.stream()
                .map(spec -> declaredFile(root, spec))
                .sorted(Comparator.comparing(DeclaredFile::path))
                .toList();
        InventoryScope scope = new InventoryScope("BOUNDED_PATH_SET", ".", files.size());
        String inventorySha256 = sha256(("declared-inventory-v1\n"
                + inventoryMaterial(scope, files)).getBytes(StandardCharsets.UTF_8));
        CaptureProof captureProof = new CaptureProof("UPSTREAM_CAPTURE_RECEIPT", CAPTURE_RECEIPT_ID,
                REPOSITORY_URL, FIXED_REVISION, inventorySha256, "pending");
        String receiptSha256 = sha256(receiptMaterial(captureProof)
                .getBytes(StandardCharsets.UTF_8));
        captureProof = new CaptureProof(captureProof.kind(), captureProof.receiptId(),
                captureProof.boundRepositoryUrl(), captureProof.boundRevision(),
                captureProof.inventorySha256(), receiptSha256);
        return new FrozenRepositoryRequest(
                new Origin("GIT_COMMIT", REPOSITORY_URL, FIXED_REVISION), captureProof, root,
                scope, files, "frozen-snapshot-v1", defaultBudget(),
                new CapabilityProfileRef(CAPABILITY_PROFILE_ID, CAPABILITY_PROFILE_SHA256));
    }

    private static DeclaredFile declaredFile(Path root, SourceSpec spec) {
        try {
            byte[] bytes = Files.readAllBytes(root.resolve(spec.path()));
            return new DeclaredFile(spec.path(), spec.mediaType(), bytes.length, sha256(bytes), "UTF-8");
        } catch (Exception failure) {
            throw new AssertionError("cannot read fixed source file " + spec.path(), failure);
        }
    }

    private static Map<String, byte[]> currentBytes(Path root) throws Exception {
        return SOURCE_SPECS.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                SourceSpec::path, spec -> {
                    try {
                        return Files.readAllBytes(root.resolve(spec.path()));
                    } catch (Exception failure) {
                        throw new AssertionError("cannot read fixed source file " + spec.path(), failure);
                    }
                }));
    }

    private static ResourceBudget defaultBudget() {
        return new ResourceBudget(64, 4_194_304, 524_288, 200_000, 100_000,
                262_144, 100_000, 256);
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
                    .append(",\"textEncoding\":").append(quoted(file.textEncoding()))
                    .append('}');
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

    private static void assertStableAcrossRuns(Stage01Result first, Stage01Result second) {
        assertEquals(first, second, "same fixed bytes must produce byte-identical records");
        assertEquals(first.stage01ResultId(), second.stage01ResultId());
        assertEquals(first.verifiedSnapshot().snapshotId(), second.verifiedSnapshot().snapshotId());
        assertEquals(first.repositoryUnderstanding().repositoryModel().repositoryModelId(),
                second.repositoryUnderstanding().repositoryModel().repositoryModelId());
        assertEquals(first.repositoryUnderstanding().capabilityReport().capabilityReportId(),
                second.repositoryUnderstanding().capabilityReport().capabilityReportId());
        assertEquals(first.provenSourceFacts().provenFactSet().provenFactSetId(),
                second.provenSourceFacts().provenFactSet().provenFactSetId());
        assertEquals(first.provenSourceFacts().proofPack().proofPackId(),
                second.provenSourceFacts().proofPack().proofPackId());
        assertEquals(first.provenSourceFacts().gapLedger().gapLedgerId(),
                second.provenSourceFacts().gapLedger().gapLedgerId());
        assertEquals(stageCounts(first), stageCounts(second),
                "M2/M3 result counts must be stable across two runs");
    }

    private static List<Integer> stageCounts(Stage01Result result) {
        RepositoryUnderstanding understanding = result.repositoryUnderstanding();
        RepositoryModel model = understanding.repositoryModel();
        ProvenSourceFacts facts = result.provenSourceFacts();
        ProvenFactSet factSet = facts.provenFactSet();
        CandidateAccounting accounting = factSet.candidateAccounting();
        int atomCount = factSet.codeFacts().stream().mapToInt(fact -> fact.atoms().size()).sum();
        return List.of(model.entries().size(), model.nodes().size(), model.edges().size(),
                model.controlFlows().size(), understanding.capabilityReport().sites().size(),
                factSet.codeFacts().size(), atomCount, accounting.candidateFactCount(),
                accounting.candidateAtomCount(), accounting.admittedFactCount(),
                accounting.rejectedFactCount(), accounting.admittedAtomDispositionCount(),
                accounting.rejectedAtomCount(), facts.proofPack().nodes().size(),
                facts.proofPack().edges().size(), facts.proofPack().proofs().size(),
                facts.gapLedger().capabilityGaps().size(), facts.gapLedger().factRejections().size(),
                facts.gapLedger().expectationGaps().size());
    }

    private static void assertBoundedSnapshot(VerifiedSnapshot snapshot) {
        assertEquals("BOUNDED_PATH_SET", snapshot.inventoryScope().kind());
        assertEquals(".", snapshot.inventoryScope().scopeRoot());
        assertEquals(8, snapshot.inventoryScope().declaredPathCount());
        assertEquals(8, snapshot.sourceIntegrity().declaredFiles());
        assertEquals(8, snapshot.sourceIntegrity().verifiedFiles());
    }

    private static void assertCapabilityAccounting(RepositoryUnderstanding understanding) {
        CapabilityReport report = understanding.capabilityReport();
        CapabilityCoverage coverage = report.coverage();
        assertEquals(report.sites().size(), coverage.reachableSemanticSites(),
                "every reachable site must remain in the capability denominator");
        assertEquals(coverage.reachableSemanticSites(), coverage.supportedSemanticSites()
                        + coverage.unsupportedReachableSites() + coverage.ambiguousReachableSites()
                        + coverage.overLimitReachableSites(),
                "capability coverage equation must close");
        Set<String> dispositions = Set.of("SUPPORTED", "UNSUPPORTED", "AMBIGUOUS", "OVER_LIMIT");
        Set<String> siteIds = new java.util.HashSet<>();
        for (CapabilitySite site : report.sites()) {
            assertText(site.disposition(), "capability site disposition must be explicit");
            assertTrue(dispositions.contains(site.disposition()),
                    "unknown capability disposition: " + site.disposition());
            assertTrue(siteIds.add(site.siteId()), "duplicate capability site id");
            if ("SUPPORTED".equals(site.disposition())) {
                assertNull(site.reasonCode(), "supported site must not carry a reason");
            } else {
                assertText(site.reasonCode(), "non-supported site needs a reason");
            }
        }
    }

    private static void assertProofClosure(Stage01Result result, Map<String, byte[]> sourceBytes) {
        ProvenSourceFacts facts = result.provenSourceFacts();
        ProofPack pack = facts.proofPack();
        Map<String, Proof> proofs = pack.proofs().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Proof::proofId, proof -> proof));
        Map<String, ProofNode> proofNodes = pack.nodes().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(ProofNode::proofNodeId, node -> node));
        Map<String, ProofEdge> proofEdges = pack.edges().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(ProofEdge::proofEdgeId, edge -> edge));
        for (ProofNode node : pack.nodes()) {
            assertProofNodeAgainstCurrentBytes(node, sourceBytes);
        }
        for (Proof proof : pack.proofs()) {
            assertEquals("CLOSED", proof.status());
            assertNotNull(proof.rootProofNodeId());
            assertTrue(proofNodes.containsKey(proof.rootProofNodeId()));
            assertFalse(proof.requiredProofNodeIds().isEmpty(), "CLOSED Proof needs node closure");
            for (String nodeId : proof.requiredProofNodeIds()) {
                assertTrue(proofNodes.containsKey(nodeId), "missing Proof node: " + nodeId);
            }
            for (String edgeId : proof.requiredProofEdgeIds()) {
                ProofEdge edge = proofEdges.get(edgeId);
                assertNotNull(edge, "missing Proof edge: " + edgeId);
                assertTrue(proofNodes.containsKey(edge.fromProofNodeId()));
                assertTrue(proofNodes.containsKey(edge.toProofNodeId()));
                assertText(edge.ruleId(), "Proof edge rule must be explicit");
            }
        }
        for (CodeFact fact : facts.provenFactSet().codeFacts()) {
            for (FactAtom atom : fact.atoms()) {
                assertText(atom.proofId(), "admitted atom must have a Proof");
                assertEquals(pack.proofPackId(), atom.proofPackId());
                Proof proof = proofs.get(atom.proofId());
                assertNotNull(proof, "admitted atom must reference a ProofPack proof");
                assertEquals("CLOSED", proof.status());
                assertEquals(fact.factId(), proof.factId());
                assertEquals(atom.atomId(), proof.atomId());
            }
        }
    }

    private static void assertProofNodeAgainstCurrentBytes(ProofNode node,
                                                             Map<String, byte[]> sourceBytes) {
        ProofLocator locator = node.locator();
        assertTrue(INVENTORY_PATHS.contains(locator.path()),
                "Proof node must point into the eight-file inventory: " + locator.path());
        byte[] bytes = sourceBytes.get(locator.path());
        assertNotNull(bytes, "missing current bytes for Proof node path");
        assertEquals(sha256(bytes), node.sourceFileSha256(), locator.path());
        assertTrue(locator.startByte() >= 0);
        assertTrue(locator.endByteExclusive() >= locator.startByte());
        assertTrue(locator.endByteExclusive() <= bytes.length);
        assertEquals(sha256(Arrays.copyOfRange(bytes, locator.startByte(), locator.endByteExclusive())),
                node.spanSha256(), "Proof span must match current source bytes");
    }

    private static void assertLegacyFactsCannotBeCopied(Stage01Result result,
                                                          Map<String, byte[]> sourceBytes) {
        Set<String> rejectedLegacyKinds = Set.of("STATE_AND_STOCK_GUARD", "PERSISTENCE_CALL");
        for (CodeFact fact : result.provenSourceFacts().provenFactSet().codeFacts()) {
            assertFalse(rejectedLegacyKinds.contains(fact.kind()),
                    "a Stage 00 rejected LockedFact kind cannot be copied into M3: " + fact.kind());
            if ("HTTP_ENTRY".equals(fact.kind())) {
                assertCompleteRouteProof(result, fact, sourceBytes);
            }
        }
    }

    private static void assertCompleteRouteProof(Stage01Result result, CodeFact fact,
                                                   Map<String, byte[]> sourceBytes) {
        Map<String, Proof> proofs = result.provenSourceFacts().proofPack().proofs().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Proof::proofId, proof -> proof));
        Map<String, ProofNode> nodes = result.provenSourceFacts().proofPack().nodes().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(ProofNode::proofNodeId, node -> node));
        Set<String> proofIds = fact.atoms().stream().map(FactAtom::proofId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> required = proofIds.stream().map(proofs::get).filter(java.util.Objects::nonNull)
                .flatMap(proof -> proof.requiredProofNodeIds().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertTrue(required.stream().map(nodes::get).filter(java.util.Objects::nonNull)
                        .anyMatch(node -> CONTROLLER_PATH.equals(node.locator().path())
                                && node.locator().startLine() == 43),
                "an admitted route must prove the DepotHeadController class prefix at line 43");
        assertTrue(required.stream().map(nodes::get).filter(java.util.Objects::nonNull)
                        .anyMatch(node -> CONTROLLER_PATH.equals(node.locator().path())
                                && node.locator().startLine() >= 178
                                && node.locator().startLine() <= 185),
                "an admitted route must prove its method suffix/body at lines 178-185");
        assertTrue(sourceBytes.containsKey(CONTROLLER_PATH));
    }

    private static void assertDynamicMapperIsNotAdmittedAsStaticSql(Stage01Result result,
                                                                      Map<String, byte[]> sourceBytes) {
        RepositoryModel model = result.repositoryUnderstanding().repositoryModel();
        CapabilityReport report = result.repositoryUnderstanding().capabilityReport();
        int dynamicLine = lineOf(sourceBytes.get(DYNAMIC_MAPPER_PATH),
                "<update id=\"updateByExampleSelective\"");
        List<CapabilitySite> relevantSites = report.sites().stream()
                .filter(site -> DYNAMIC_MAPPER_PATH.equals(site.locator().path()))
                .filter(site -> covers(site.locator(), dynamicLine))
                .toList();
        boolean explicitGap = relevantSites.stream().anyMatch(site ->
                ("UNSUPPORTED".equals(site.disposition()) || "AMBIGUOUS".equals(site.disposition()))
                        && site.reasonCode() != null);
        boolean rejectedFact = result.provenSourceFacts().gapLedger().factRejections().stream()
                .anyMatch(rejection -> rejection.locator() != null
                        && DYNAMIC_MAPPER_PATH.equals(rejection.locator().path())
                        && covers(rejection.locator(), dynamicLine));
        assertTrue(explicitGap || rejectedFact,
                "dynamic updateByExampleSelective must remain an explicit gap or rejection");
        assertFalse(relevantSites.stream().anyMatch(site -> "STATIC_SQL".equals(site.kind())
                        && "SUPPORTED".equals(site.disposition())),
                "dynamic updateByExampleSelective cannot be a supported static SQL site");

        Set<String> dynamicStatements = model.nodes().stream()
                .filter(node -> "XML_STATEMENT".equals(node.kind()))
                .filter(node -> node.canonicalValue().contains("update:updateByExampleSelective"))
                .map(RepositoryNode::nodeId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertFalse(model.edges().stream().anyMatch(edge -> dynamicStatements.contains(edge.fromNodeId())
                        && "STATEMENT_SQL_FRAGMENT".equals(edge.kind())
                        && "EXACT".equals(edge.resolution())),
                "dynamic mapper statement must not expose exact static SQL fragments");

        Set<String> dynamicSqlNodes = model.nodes().stream()
                .filter(node -> Set.of("SQL_TABLE", "SQL_ASSIGNMENT", "SQL_PREDICATE").contains(node.kind()))
                .filter(node -> DYNAMIC_MAPPER_PATH.equals(node.locator().path()))
                .filter(node -> covers(node.locator(), dynamicLine))
                .map(RepositoryNode::nodeId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> admittedProofNodeRepositoryIds = result.provenSourceFacts().proofPack().nodes().stream()
                .filter(node -> result.provenSourceFacts().proofPack().proofs().stream()
                        .anyMatch(proof -> proof.requiredProofNodeIds().contains(node.proofNodeId())))
                .map(ProofNode::repositoryNodeId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertTrue(java.util.Collections.disjoint(dynamicSqlNodes, admittedProofNodeRepositoryIds),
                "admitted facts must not prove dynamic SQL table/assignment/predicate nodes");
    }

    private static boolean covers(ProofLocator locator, int line) {
        return locator.startLine() <= line && locator.endLine() >= line;
    }

    private static boolean covers(RepositoryLocator locator, int line) {
        return locator.startLine() <= line && locator.endLine() >= line;
    }

    private static void assertText(String value, String message) {
        assertTrue(value != null && !value.isBlank(), message);
    }

    private static int lineOf(byte[] bytes, String token) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        int offset = text.indexOf(token);
        assertTrue(offset >= 0, "fixed source token not found: " + token);
        return (int) text.substring(0, offset).chars().filter(character -> character == '\n').count() + 1;
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

    private static final class TripwireProxySelector extends ProxySelector {
        private final AtomicBoolean attempted;

        private TripwireProxySelector(AtomicBoolean attempted) {
            this.attempted = attempted;
        }

        @Override
        public List<Proxy> select(URI uri) {
            attempted.set(true);
            throw new AssertionError("unexpected external resolution: " + uri);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, java.io.IOException failure) {
            attempted.set(true);
            throw new AssertionError("unexpected external resolution: " + uri, failure);
        }
    }
}
