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
    assertThat(draft.coverage().gapDispositions())
        .singleElement()
        .satisfies(
            gap -> assertThat(gap.candidateElementId().value()).startsWith("program-element:"));
    assertThat(draft.coverage().closed()).isFalse();
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
    assertThat(draft.coverage().gapDispositions()).singleElement();
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
