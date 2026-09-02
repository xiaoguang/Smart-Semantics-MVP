package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.discovery.MapperMethodCandidate;
import org.sourceanalysis.app.analysis.discovery.MapperStatementCandidate;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

class ControlFlowGraphBuilderTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void projectsEachFreshReopenedCallPairIntoOneInterproceduralEntryTraversal() {
    try (Fixture fixture = Fixture.create(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      assertThat(draft.graphKind()).isEqualTo(ProgramGraphKind.CONTROL_FLOW);
      assertThat(draft.entryIds()).containsExactly(fixture.entryId());
      assertThat(draft.nodes())
          .extracting(ControlFlowNode::kind)
          .contains(
              ControlFlowNodeKind.ENTRY,
              ControlFlowNodeKind.ENTRY_RETURN_TERMINAL,
              ControlFlowNodeKind.CALLEE_RETURN_TERMINAL);
      assertThat(draft.edges())
          .filteredOn(edge -> edge.kind() == ControlFlowEdgeKind.CALL)
          .hasSameSizeAs(
              fixture.calls().draft().edges().stream()
                  .filter(
                      edge ->
                          edge.kind() == CallGraphEdgeKind.CALL_TARGET
                              || edge.kind() == CallGraphEdgeKind.JAVA_METHOD_TO_XML_STATEMENT)
                  .toList());
      assertThat(draft.edges())
          .filteredOn(edge -> edge.kind() == ControlFlowEdgeKind.RETURN)
          .hasSameSizeAs(
              fixture.calls().draft().edges().stream()
                  .filter(edge -> edge.kind() == CallGraphEdgeKind.CALL_RETURN)
                  .toList());
      assertThat(draft.semanticTraversalOrder())
          .singleElement()
          .satisfies(
              traversal -> {
                assertThat(traversal.entryId()).isEqualTo(fixture.entryId());
                assertThat(traversal.nodeIds()).isNotEmpty();
                assertThat(traversal.edgeIds()).isNotEmpty();
              });
      assertThat(draft.coverage().candidateElementIds())
          .containsExactlyInAnyOrderElementsOf(draft.coverage().exactElementIds());
    }
  }

  private record Fixture(
      RunStoreHandle handle,
      ArtifactId entryId,
      ArtifactReference graphProfileRef,
      ReopenedProgramGraphInputs reopenedInputs,
      ReopenedCodeStructureGraph structure,
      ReopenedCallGraph calls)
      implements AutoCloseable {

    private static Fixture create(java.nio.file.Path temporaryDirectory) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
      ArtifactControls controls = controls(policies);
      ArtifactReference graphProfile = reference("graph-profile", "control-flow-v2");
      ArtifactId entryId = id("entry", "batch-set-status");
      CodeStructureSource source = source(controls);
      CodeStructureDiscovery discovery = discovery(entryId);
      ReopenedProgramGraphInputs reopened =
          new ReopenedProgramGraphInputs(
              source,
              new ProgramGraphDiscoveryInputs(
                  discovery, List.of(entry(entryId)), List.of(mapperCatalog())));
      AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("control-flow-run"));
      RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory);
      try {
        FileSystemCanonicalModuleArtifactStore store =
            new FileSystemCanonicalModuleArtifactStore(
                handle, canonicalJson, policies, new ArtifactStoreLimits(2, 100_000, 200_000, 8));
        CodeStructureGraphDraftReference structureReference =
            new CodeStructureGraphModulePublisher(store)
                .publish(
                    new AnalysisStepModuleAddress(
                        runId, AnalysisStepKey.PROGRAM_GRAPHS, 1, "code-structure"),
                    source,
                    discovery,
                    new CodeStructureGraphBuilder()
                        .buildStructure(
                            source, discovery, new CodeStructureGraphProfile(graphProfile)));
        ReopenedCodeStructureGraph structure =
            new PersistedCodeStructureGraphReader(store)
                .reopen(structureReference, reopened, graphProfile);
        CallGraphDraftReference callReference =
            new CallGraphModulePublisher(store)
                .publish(
                    new AnalysisStepModuleAddress(
                        runId, AnalysisStepKey.PROGRAM_GRAPHS, 2, "call-graph"),
                    structure,
                    reopened,
                    new CallGraphBuilder()
                        .buildCalls(
                            new CallGraphInputs(structure, reopened),
                            new CallGraphProfile(graphProfile)));
        ReopenedCallGraph calls =
            new PersistedCallGraphReader(store)
                .reopen(callReference, reopened, structure, graphProfile);
        return new Fixture(handle, entryId, graphProfile, reopened, structure, calls);
      } catch (RuntimeException failure) {
        handle.close();
        throw failure;
      }
    }

    @Override
    public void close() {
      handle.close();
    }
  }

  private static CodeStructureSource source(ArtifactControls controls) {
    return new CodeStructureSource(
        "snapshot:" + digest("control-flow-snapshot"),
        "COMPLETE_CAPTURE",
        true,
        reference("source-inventory", "control-flow-inventory"),
        reference("verified-snapshot", "control-flow-snapshot"),
        controls,
        List.of(
            document(
                "src/main/java/com/example/DepotHeadController.java",
                """
                package com.example;

                class DepotHeadController {
                  private final DepotHeadService depotHeadService = new DepotHeadService();

                  void batchSetStatus(String status) {
                    depotHeadService.batchSetStatus(status);
                  }
                }
                """),
            document(
                "src/main/java/com/example/DepotHeadService.java",
                """
                package com.example;

                class DepotHeadService {
                  private final DepotHeadMapper depotHeadMapper = null;

                  void batchSetStatus(String status) {
                    depotHeadMapper.updateStatus(status);
                  }
                }
                """),
            document(
                "src/main/java/com/example/DepotHeadMapper.java",
                """
                package com.example;

                interface DepotHeadMapper {
                  void updateStatus(String status);
                }
                """),
            document(
                "src/main/resources/mapper/DepotHeadMapper.xml",
                """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="com.example.DepotHeadMapper">
                  <update id="updateStatus">
                    UPDATE jsh_depot_head SET status = #{status}
                  </update>
                </mapper>
                """)));
  }

  private static CodeStructureSourceDocument document(String path, String source) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    return new CodeStructureSourceDocument(
        id("file", path), path, ImmutableBytes.copyOf(bytes), new Sha256Digest(digest(bytes)));
  }

  private static CodeStructureDiscovery discovery(ArtifactId entryId) {
    return new CodeStructureDiscovery(
        id("application-profile", "control-flow"),
        reference("application-profile", "control-flow"),
        reference("capability-report", "control-flow"),
        reference("entry-points", "control-flow"),
        reference("mapper-catalog", "control-flow"),
        List.of(entryId));
  }

  private static HttpEntryPoint entry(ArtifactId entryId) {
    return new HttpEntryPoint(
        entryId,
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        "POST",
        "/depotHead/batchSetStatus",
        List.of("/depotHead", "/batchSetStatus"),
        "com.example.DepotHeadController#batchSetStatus",
        List.of("status"),
        List.of(
            excerpt(
                "src/main/java/com/example/DepotHeadController.java", "class DepotHeadController"),
            excerpt("src/main/java/com/example/DepotHeadController.java", "batchSetStatus")));
  }

  private static MapperCatalogEntry mapperCatalog() {
    return new MapperCatalogEntry(
        id("mapper-catalog-entry", "depot-head"),
        "com.example.DepotHeadMapper",
        List.of(
            new MapperMethodCandidate(
                id("mapper-method", "update-status"),
                "updateStatus(java.lang.String)",
                excerpt(
                    "src/main/java/com/example/DepotHeadMapper.java",
                    "void updateStatus(String status);"))),
        "src/main/resources/mapper/DepotHeadMapper.xml",
        "com.example.DepotHeadMapper",
        List.of(
            new MapperStatementCandidate(
                id("mapper-statement", "update-status"),
                "updateStatus",
                "update",
                excerpt("src/main/resources/mapper/DepotHeadMapper.xml", "id=\"updateStatus\""))),
        "CANDIDATE_NOT_YET_BOUND");
  }

  private static SourceExcerptV1 excerpt(String path, String value) {
    String text =
        switch (path) {
          case "src/main/java/com/example/DepotHeadController.java" ->
              """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """;
          case "src/main/java/com/example/DepotHeadMapper.java" ->
              """
          package com.example;

          interface DepotHeadMapper {
            void updateStatus(String status);
          }
          """;
          case "src/main/resources/mapper/DepotHeadMapper.xml" ->
              """
          <?xml version="1.0" encoding="UTF-8" ?>
          <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
          <mapper namespace="com.example.DepotHeadMapper">
            <update id="updateStatus">
              UPDATE jsh_depot_head SET status = #{status}
            </update>
          </mapper>
          """;
          default -> throw new IllegalArgumentException("unknown fixture source");
        };
    int startCharacter = text.indexOf(value);
    if (startCharacter < 0) {
      throw new IllegalArgumentException("fixture token is absent");
    }
    int startByte = text.substring(0, startCharacter).getBytes(StandardCharsets.UTF_8).length;
    int startLine =
        1
            + (int)
                text.substring(0, startCharacter)
                    .chars()
                    .filter(character -> character == '\n')
                    .count();
    int lineStart = text.lastIndexOf('\n', startCharacter - 1) + 1;
    int startColumn = startCharacter - lineStart + 1;
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return new SourceExcerptV1(
        new SourceLocatorV1(
            id("file", path),
            path,
            startByte,
            startByte + bytes.length,
            startLine,
            startColumn,
            startLine,
            startColumn + value.length()),
        ImmutableBytes.copyOf(bytes),
        new Sha256Digest(digest(bytes)));
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        new Sha256Digest(digest("toolchain")),
        new Sha256Digest(digest("profile")),
        new Sha256Digest(digest("schema")),
        null,
        policies.reference());
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec canonicalJson) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode entries = document.putArray("policies");
    policy(entries, "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT", CallGraphDraft.SCHEMA_VERSION, "call-graph");
    policy(
        entries,
        "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT",
        CodeStructureGraphDraft.SCHEMA_VERSION,
        "code-structure-graph");
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + digest(
                concatenate(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(canonicalJson.encodeCanonical(document).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(
        canonicalJson.encodeCanonical(document), canonicalJson);
  }

  private static void policy(
      ArrayNode entries, String artifactType, String schemaVersion, String artifactIdPrefix) {
    entries
        .addObject()
        .put("artifactType", artifactType)
        .put("schemaVersion", schemaVersion)
        .put("artifactIdPrefix", artifactIdPrefix)
        .put("mediaType", "application/json")
        .put("envelopeKind", "MODULE_ARTIFACT_JSON")
        .put("emptyJsonlAllowed", false)
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(id(prefix, value), new Sha256Digest(digest(value)));
  }

  private static ArtifactId id(String prefix, String value) {
    return ArtifactId.parse(prefix + ":" + digest(value));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
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
