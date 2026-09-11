package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

class CodeStructureGraphBuilderTest {

  @Test
  void producesDistinctJavaAndMyBatisStructureFromFrozenSourceBytes() throws Exception {
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "inventory"),
            reference("verified-snapshot", "verified"),
            controls(),
            List.of(
                document("src/main/java/com/example/DepotHeadController.java"),
                document("src/main/java/com/example/DepotHeadService.java"),
                document("src/main/java/com/example/DepotHead.java"),
                document("src/main/java/com/example/DepotHeadMapper.java"),
                document("src/main/resources/mapper/DepotHeadMapper.xml")));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", "java-spring-mybatis"),
            reference("application-profile", "profile"),
            reference("capability-report", "capability"),
            reference("entry-points", "entries"),
            reference("mapper-catalog", "mappers"),
            List.of(id("entry", "batch-set-status")));
    CodeStructureGraphProfile profile =
        new CodeStructureGraphProfile(reference("graph-profile", "code-structure-v1"));

    CodeStructureGraphDraft draft =
        new CodeStructureGraphBuilder().buildStructure(source, discovery, profile);

    assertThat(draft.graphKind()).isEqualTo(ProgramGraphKind.CODE_STRUCTURE);
    assertThat(draft.snapshotId()).isEqualTo(source.snapshotId());
    assertThat(draft.applicationProfileId()).isEqualTo(discovery.applicationProfileId());
    assertThat(draft.nodes())
        .extracting(DraftProgramNode::canonicalValue)
        .contains(
            "com.example.DepotHeadController",
            "com.example.DepotHeadController#batchSetStatus(java.lang.String,java.lang.String)",
            "com.example.DepotHeadService",
            "com.example.DepotHeadService#batchSetStatus(java.lang.String,java.lang.String)",
            "com.example.DepotHead.status",
            "com.example.DepotHeadMapper",
            "com.example.DepotHeadMapper#updateStatus(com.example.DepotHead)",
            "com.example.DepotHeadMapper#updateStatus(com.example.DepotHead)",
            "jsh_depot_head",
            "jsh_depot_head.status");
    assertThat(draft.nodes())
        .filteredOn(
            node ->
                node.canonicalValue()
                    .equals("com.example.DepotHeadMapper#updateStatus(com.example.DepotHead)"))
        .extracting(DraftProgramNode::kind)
        .containsExactlyInAnyOrder(ProgramNodeKind.METHOD, ProgramNodeKind.XML_STATEMENT);
    assertThat(draft.edges())
        .extracting(DraftProgramEdge::kind)
        .contains(
            ProgramEdgeKind.CONTAINS,
            ProgramEdgeKind.DECLARES,
            ProgramEdgeKind.STATEMENT_CONTAINS_SQL);
    assertThat(draft.coverage().candidateElementIds())
        .containsExactlyElementsOf(draft.coverage().exactElementIds());
    assertThat(draft.coverage().gapDispositions()).isEmpty();
    assertThat(draft.coverage().exclusionDispositions()).isEmpty();
  }

  @Test
  void accumulatesExactOccurrencesWhenTwoStaticStatementsUseTheSameTableAndColumn()
      throws Exception {
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("repeated-static-sql-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "repeated-static-sql-inventory"),
            reference("verified-snapshot", "repeated-static-sql-verified"),
            controls(),
            List.of(document("src/main/resources/mapper/RepeatedStatusMapper.xml")));

    CodeStructureGraphDraft draft =
        new CodeStructureGraphBuilder()
            .buildStructure(
                source,
                discovery("repeated-static-sql"),
                new CodeStructureGraphProfile(reference("graph-profile", "code-structure-v1")));

    DraftProgramNode table =
        draft.nodes().stream()
            .filter(
                candidate ->
                    candidate.kind() == ProgramNodeKind.SQL_TABLE
                        && candidate.canonicalValue().equals("jsh_depot_head"))
            .findFirst()
            .orElseThrow();
    DraftProgramNode column =
        draft.nodes().stream()
            .filter(
                candidate ->
                    candidate.kind() == ProgramNodeKind.SQL_COLUMN
                        && candidate.canonicalValue().equals("jsh_depot_head.status"))
            .findFirst()
            .orElseThrow();

    assertThat(
            draft.nodes().stream()
                .filter(candidate -> candidate.kind() == ProgramNodeKind.SQL_TABLE)
                .toList())
        .containsExactly(table);
    assertThat(table.evidenceDraftRefs()).hasSize(2);
    assertThat(column.evidenceDraftRefs()).hasSize(2);
    assertThat(
            draft.edges().stream()
                .filter(
                    edge ->
                        edge.kind() == ProgramEdgeKind.STATEMENT_CONTAINS_SQL
                            && edge.toNodeId().equals(table.nodeId()))
                .toList())
        .hasSize(2);
  }

  @Test
  void qualifiesDirectlyImportedMethodParameterTypesUsingTheirImportedFqn() throws Exception {
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("imported-parameter-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "imported-parameter-inventory"),
            reference("verified-snapshot", "imported-parameter-verified"),
            controls(),
            List.of(document("src/main/java/com/example/ImportedAmountHandler.java")));

    CodeStructureGraphDraft draft =
        new CodeStructureGraphBuilder()
            .buildStructure(
                source,
                discovery("imported-parameter"),
                new CodeStructureGraphProfile(reference("graph-profile", "code-structure-v1")));

    assertThat(draft.nodes())
        .extracting(DraftProgramNode::canonicalValue)
        .contains("com.example.ImportedAmountHandler#record(java.math.BigDecimal)")
        .doesNotContain("com.example.ImportedAmountHandler#record(com.example.BigDecimal)");
  }

  @Test
  void recordsMalformedJavaAsAnExplicitGapInsteadOfUsingAPartialParse() throws Exception {
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("malformed-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "malformed-inventory"),
            reference("verified-snapshot", "malformed-verified"),
            controls(),
            List.of(document("src/main/java/com/example/MalformedController.java")));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", "malformed-profile"),
            reference("application-profile", "malformed-profile"),
            reference("capability-report", "malformed-capability"),
            reference("entry-points", "malformed-entries"),
            reference("mapper-catalog", "malformed-mappers"),
            List.of());

    CodeStructureGraphDraft draft =
        new CodeStructureGraphBuilder()
            .buildStructure(
                source,
                discovery,
                new CodeStructureGraphProfile(reference("graph-profile", "code-structure-v1")));

    assertThat(draft.nodes()).isEmpty();
    assertThat(draft.edges()).isEmpty();
    GraphGapDraft gap = draft.gapDrafts().get(0);
    assertThat(gap.affectedEntryIds()).isEmpty();
    assertThat(gap.candidateElementIds())
        .containsExactly(draft.coverage().gapDispositions().get(0).candidateElementId());
    assertThat(gap.sourceLocator().fileId()).isEqualTo(source.documents().get(0).fileId());
    assertThat(gap.sourceLocator().path()).isEqualTo(source.documents().get(0).path());
    assertThat(gap.sourceLocator().startByte()).isEqualTo(0L);
    assertThat(gap.sourceLocator().endByteExclusive())
        .isEqualTo((long) source.documents().get(0).rawUtf8().size());
    assertThat(gap.sourceLocator().startLine()).isEqualTo(1);
    assertThat(gap.sourceLocator().startColumn()).isEqualTo(1);
    assertThat(gap.sourceLocator().endLine()).isEqualTo(8);
    assertThat(gap.sourceLocator().endColumn()).isEqualTo(1);
    assertThat(draft.coverage().closed()).isTrue();
  }

  @Test
  void recordsCustomXmlEntityAsAnExplicitGapInsteadOfParsingTheMapper() throws Exception {
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("entity-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "entity-inventory"),
            reference("verified-snapshot", "entity-verified"),
            controls(),
            List.of(document("src/main/resources/mapper/ExternalEntityMapper.xml")));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", "entity-profile"),
            reference("application-profile", "entity-profile"),
            reference("capability-report", "entity-capability"),
            reference("entry-points", "entity-entries"),
            reference("mapper-catalog", "entity-mappers"),
            List.of());

    CodeStructureGraphDraft draft =
        new CodeStructureGraphBuilder()
            .buildStructure(
                source,
                discovery,
                new CodeStructureGraphProfile(reference("graph-profile", "code-structure-v1")));

    assertThat(draft.nodes()).isEmpty();
    assertThat(draft.edges()).isEmpty();
    GraphGapDraft gap = draft.gapDrafts().get(0);
    assertThat(gap.affectedEntryIds()).isEmpty();
    assertThat(gap.candidateElementIds())
        .containsExactly(draft.coverage().gapDispositions().get(0).candidateElementId());
    assertThat(gap.sourceLocator().fileId()).isEqualTo(source.documents().get(0).fileId());
    assertThat(gap.sourceLocator().path()).isEqualTo(source.documents().get(0).path());
    assertThat(gap.sourceLocator().startByte()).isEqualTo(0L);
    assertThat(gap.sourceLocator().endByteExclusive())
        .isEqualTo((long) source.documents().get(0).rawUtf8().size());
    assertThat(gap.sourceLocator().startLine()).isEqualTo(1);
    assertThat(gap.sourceLocator().startColumn()).isEqualTo(1);
    assertThat(gap.sourceLocator().endLine()).isEqualTo(8);
    assertThat(gap.sourceLocator().endColumn()).isEqualTo(1);
    assertThat(draft.coverage().closed()).isTrue();
  }

  @Test
  void rejectsDistinctPathsThatClaimTheSameVerifiedFileIdentity() throws Exception {
    CodeStructureSourceDocument controller =
        document("src/main/java/com/example/DepotHeadController.java");
    CodeStructureSourceDocument service =
        document("src/main/java/com/example/DepotHeadService.java");
    CodeStructureSourceDocument forgedService =
        new CodeStructureSourceDocument(
            controller.fileId(), service.path(), service.rawUtf8(), service.sha256());

    assertThatThrownBy(
            () ->
                new CodeStructureSource(
                    "snapshot:" + digest("duplicate-file-id-snapshot"),
                    "COMPLETE_CAPTURE",
                    true,
                    reference("source-inventory", "duplicate-file-id-inventory"),
                    reference("verified-snapshot", "duplicate-file-id-verified"),
                    controls(),
                    List.of(controller, forgedService)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("file IDs");
  }

  @Test
  void preservesExactConfigurationAndProvenanceDraftsForLaterEvidenceReopening() throws Exception {
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("provenance-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "provenance-inventory"),
            reference("verified-snapshot", "provenance-snapshot"),
            controls(),
            List.of(
                document("src/main/resources/application.yml"),
                document("src/main/resources/mapper/DepotHeadMapper.xml")));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", "provenance-profile"),
            reference("application-profile", "provenance-profile"),
            reference("capability-report", "provenance-capability"),
            reference("entry-points", "provenance-entries"),
            reference("mapper-catalog", "provenance-mappers"),
            List.of());

    CodeStructureGraphDraft draft =
        new CodeStructureGraphBuilder()
            .buildStructure(
                source,
                discovery,
                new CodeStructureGraphProfile(reference("graph-profile", "code-structure-v2")));

    assertThat(draft.nodes())
        .extracting(DraftProgramNode::canonicalValue)
        .contains(
            "mybatis.mapper-locations", "classpath:mapper/DepotHeadMapper.xml", "jsh_depot_head");
    assertThat(draft.edges())
        .extracting(DraftProgramEdge::kind)
        .contains(ProgramEdgeKind.CONFIG_RESOLVES_RESOURCE, ProgramEdgeKind.STATEMENT_CONTAINS_SQL);

    Set<ArtifactId> referencedProvenance =
        new TreeSet<>(java.util.Comparator.comparing(ArtifactId::value));
    draft.nodes().forEach(node -> referencedProvenance.addAll(node.evidenceDraftRefs()));
    draft.edges().forEach(edge -> referencedProvenance.addAll(edge.evidenceDraftRefs()));
    assertThat(draft.provenanceDrafts())
        .extracting(ProvenanceDraftV1::provenanceDraftId)
        .containsExactlyElementsOf(referencedProvenance);

    DraftProgramNode configKey =
        draft.nodes().stream()
            .filter(node -> node.canonicalValue().equals("mybatis.mapper-locations"))
            .findFirst()
            .orElseThrow();
    ProvenanceDraftV1 configKeyProvenance =
        draft.provenanceDrafts().stream()
            .filter(
                draftProvenance ->
                    configKey.evidenceDraftRefs().contains(draftProvenance.provenanceDraftId()))
            .findFirst()
            .orElseThrow();
    assertThat(configKeyProvenance.ruleId()).isEqualTo("yaml-static-key-v1");
    assertThat(configKeyProvenance.sourceLocator().path())
        .isEqualTo("src/main/resources/application.yml");
    assertThat(configKeyProvenance.sourceLocator())
        .extracting(
            locator -> locator.startByte(),
            locator -> locator.endByteExclusive(),
            locator -> locator.startLine(),
            locator -> locator.startColumn(),
            locator -> locator.endLine(),
            locator -> locator.endColumn())
        .containsExactly(11L, 27L, 2, 3, 2, 19);
    assertThat(configKeyProvenance.excerptSha256())
        .isEqualTo(new Sha256Digest(digest("mapper-locations")));
  }

  @Test
  void preservesDeclarationAndSqlTokenSpansInsteadOfUsingWholeSourceFiles() throws Exception {
    CodeStructureSourceDocument controller =
        document("src/main/java/com/example/DepotHeadController.java");
    CodeStructureSourceDocument mapper = document("src/main/resources/mapper/DepotHeadMapper.xml");
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("declaration-span-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "declaration-span-inventory"),
            reference("verified-snapshot", "declaration-span-snapshot"),
            controls(),
            List.of(controller, mapper));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", "declaration-span-profile"),
            reference("application-profile", "declaration-span-profile"),
            reference("capability-report", "declaration-span-capability"),
            reference("entry-points", "declaration-span-entries"),
            reference("mapper-catalog", "declaration-span-mappers"),
            List.of());

    CodeStructureGraphDraft draft =
        new CodeStructureGraphBuilder()
            .buildStructure(
                source,
                discovery,
                new CodeStructureGraphProfile(reference("graph-profile", "code-structure-v2")));

    ProvenanceDraftV1 method =
        provenanceFor(
            draft,
            "com.example.DepotHeadController#batchSetStatus(java.lang.String,java.lang.String)",
            ProgramNodeKind.METHOD);
    assertThat(method.sourceLocator())
        .extracting(
            locator -> locator.path(),
            locator -> locator.startLine(),
            locator -> locator.startColumn(),
            locator -> locator.endLine(),
            locator -> locator.endColumn())
        .containsExactly("src/main/java/com/example/DepotHeadController.java", 4, 3, 6, 4);
    assertThat(method.sourceLocator().endByteExclusive()).isLessThan(controller.rawUtf8().size());

    ProvenanceDraftV1 table = provenanceFor(draft, "jsh_depot_head", ProgramNodeKind.SQL_TABLE);
    assertThat(table.sourceLocator())
        .extracting(
            locator -> locator.path(),
            locator -> locator.startLine(),
            locator -> locator.startColumn(),
            locator -> locator.endLine(),
            locator -> locator.endColumn())
        .containsExactly("src/main/resources/mapper/DepotHeadMapper.xml", 5, 12, 5, 26);
    assertThat(table.sourceLocator().endByteExclusive()).isLessThan(mapper.rawUtf8().size());

    ProvenanceDraftV1 column =
        provenanceFor(draft, "jsh_depot_head.status", ProgramNodeKind.SQL_COLUMN);
    assertThat(column.sourceLocator())
        .extracting(
            locator -> locator.startLine(),
            locator -> locator.startColumn(),
            locator -> locator.endLine(),
            locator -> locator.endColumn())
        .containsExactly(6, 9, 6, 15);
  }

  @Test
  void flattensNestedStaticConfigurationKeysWithoutLosingTheirResourceEvidence() throws Exception {
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("nested-config-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "nested-config-inventory"),
            reference("verified-snapshot", "nested-config-snapshot"),
            controls(),
            List.of(document("src/main/resources/nested-application.yml")));
    CodeStructureGraphDraft draft =
        new CodeStructureGraphBuilder()
            .buildStructure(
                source,
                discovery("nested-config"),
                new CodeStructureGraphProfile(reference("graph-profile", "code-structure-v2")));

    DraftProgramNode key =
        draft.nodes().stream()
            .filter(candidate -> candidate.kind() == ProgramNodeKind.CONFIGURATION_KEY)
            .findFirst()
            .orElseThrow();
    assertThat(key.canonicalValue()).isEqualTo("app.persistence.mapper-location");
    ProvenanceDraftV1 provenance =
        draft.provenanceDrafts().stream()
            .filter(candidate -> key.evidenceDraftRefs().contains(candidate.provenanceDraftId()))
            .findFirst()
            .orElseThrow();
    assertThat(provenance.sourceLocator())
        .extracting(
            locator -> locator.startLine(),
            locator -> locator.startColumn(),
            locator -> locator.endLine(),
            locator -> locator.endColumn())
        .containsExactly(3, 5, 3, 20);
  }

  private static ProvenanceDraftV1 provenanceFor(
      CodeStructureGraphDraft draft, String canonicalValue, ProgramNodeKind kind) {
    DraftProgramNode node =
        draft.nodes().stream()
            .filter(
                candidate ->
                    candidate.kind() == kind && candidate.canonicalValue().equals(canonicalValue))
            .findFirst()
            .orElseThrow();
    return draft.provenanceDrafts().stream()
        .filter(candidate -> node.evidenceDraftRefs().contains(candidate.provenanceDraftId()))
        .findFirst()
        .orElseThrow();
  }

  private static CodeStructureSourceDocument document(String resourcePath) throws Exception {
    byte[] bytes =
        Files.readAllBytes(
            Path.of("src/test/resources/analysis/graph/code-structure").resolve(resourcePath));
    return new CodeStructureSourceDocument(
        id("file", resourcePath),
        resourcePath,
        ImmutableBytes.copyOf(bytes),
        new Sha256Digest(digest(bytes)));
  }

  private static ArtifactControls controls() {
    return new ArtifactControls(
        new Sha256Digest(digest("toolchain")),
        new Sha256Digest(digest("profile")),
        new Sha256Digest(digest("schema")),
        null,
        new ArtifactPolicyRegistryReference(
            id("artifact-policy-registry", "policy"), new Sha256Digest(digest("policy"))));
  }

  private static CodeStructureDiscovery discovery(String value) {
    return new CodeStructureDiscovery(
        id("application-profile", value + "-profile"),
        reference("application-profile", value + "-profile"),
        reference("capability-report", value + "-capability"),
        reference("entry-points", value + "-entries"),
        reference("mapper-catalog", value + "-mappers"),
        List.of());
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(id(prefix, value), new Sha256Digest(digest(value)));
  }

  private static ArtifactId id(String prefix, String value) {
    return ArtifactId.parse(prefix + ":" + digest(value));
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
