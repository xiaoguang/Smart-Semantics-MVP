package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
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

/** RED contract for carrying an M2-local Gap into the shared graph-gap projection. */
class CallGraphGapCarrierTest {

  @TempDir Path temporaryDirectory;

  @Test
  void unresolvedMapperMethodCarriesOneSourceLocatedSharedGapWithStableIdentity() throws Exception {
    Fixture fixture = unresolvedMapperMethodFixture();

    CallGraphDraft first = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());
    CallGraphDraft second = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

    assertThat(first).isEqualTo(second);
    assertThat(first.edges())
        .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.JAVA_METHOD_TO_XML_STATEMENT)
        .isEmpty();
    GraphGapDisposition disposition =
        assertThat(first.coverage().gapDispositions()).singleElement().actual();

    List<GraphGapDraft> carriers = sharedGapDrafts(first);
    assertThat(carriers).hasSize(1);
    GraphGapDraft carrier = carriers.get(0);

    assertThat(carrier.gapId()).isEqualTo(disposition.gapId());
    assertThat(carrier.reasonCode()).isEqualTo("MAPPER_JAVA_METHOD_UNRESOLVED");
    assertThat(carrier.affectedEntryIds()).containsExactly(fixture.entryId());
    assertThat(carrier.candidateElementIds()).containsExactly(disposition.candidateElementId());
    assertThat(carrier.sourceLocator().path())
        .isEqualTo("src/main/java/com/example/DepotHeadService.java");
    assertThat(carrier.sourceLocator().startLine()).isEqualTo(7);
    assertThat(carrier.sourceLocator().endLine()).isEqualTo(7);
    assertThat(carrier.sourceLocator().startByte())
        .isLessThan(carrier.sourceLocator().endByteExclusive());

    GraphGapDraft.requireIdentity(ProgramGraphKind.CALL, carrier);
    assertThat(sharedGapDrafts(second)).containsExactly(carrier);
    assertThat(first.coverage().gapDispositions())
        .singleElement()
        .satisfies(
            gap -> {
              assertThat(gap.candidateElementId()).isEqualTo(carrier.candidateElementIds().get(0));
              assertThat(gap.gapId()).isEqualTo(carrier.gapId());
            });
  }

  @SuppressWarnings("unchecked")
  private static List<GraphGapDraft> sharedGapDrafts(CallGraphDraft draft) {
    try {
      Method accessor = draft.getClass().getMethod("gapDrafts");
      Object value = accessor.invoke(draft);
      assertThat(value).as("CallGraphDraft.gapDrafts() result").isInstanceOf(List.class);
      return (List<GraphGapDraft>) value;
    } catch (NoSuchMethodException missingV3Carrier) {
      fail("CALL_GRAPH_DRAFT_V3_REQUIRED: missing gapDrafts() shared carrier");
      return List.of();
    } catch (IllegalAccessException | InvocationTargetException inaccessible) {
      fail("CALL_GRAPH_DRAFT_GAP_CARRIER_UNREADABLE: " + inaccessible.getClass().getSimpleName());
      return List.of();
    }
  }

  private Fixture unresolvedMapperMethodFixture() throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    String fixtureRoot = "call-graph";
    ArtifactId entryId = id("entry", "batch-set-status");
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("call-graph-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "call-graph-inventory"),
            reference("verified-snapshot", "call-graph-snapshot"),
            controls,
            List.of(
                document(fixtureRoot, "src/main/java/com/example/DepotHeadController.java"),
                document(fixtureRoot, "src/main/java/com/example/DepotHeadService.java"),
                document(fixtureRoot, "src/main/java/com/example/DepotHeadMapper.java"),
                document(fixtureRoot, "src/main/resources/mapper/DepotHeadMapper.xml")));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", "call-graph"),
            reference("application-profile", "call-graph"),
            reference("capability-report", "call-graph"),
            reference("entry-points", "call-graph"),
            reference("mapper-catalog", "call-graph"),
            List.of(entryId));
    ArtifactReference graphProfile = reference("graph-profile", "code-structure-v2");
    HttpEntryPoint entry =
        new HttpEntryPoint(
            entryId,
            HttpEntryKind.SPRING_MVC_HTTP,
            "HTTP",
            "POST",
            "/depotHead/batchSetStatus",
            List.of("/depotHead", "/batchSetStatus"),
            "com.example.DepotHeadController#batchSetStatus",
            "method:" + "1".repeat(64),
            new org.sourceanalysis.app.analysis.code.SourceRange(0, 1, 1, 1),
            List.of("status"),
            List.of(
                excerpt(
                    fixtureRoot,
                    "src/main/java/com/example/DepotHeadController.java",
                    "class DepotHeadController"),
                excerpt(
                    fixtureRoot,
                    "src/main/java/com/example/DepotHeadController.java",
                    "batchSetStatus")));
    MapperCatalogEntry mapper =
        new MapperCatalogEntry(
            id("mapper-catalog-entry", "depot-head"),
            "com.example.DepotHeadMapper",
            List.of(),
            "src/main/resources/mapper/DepotHeadMapper.xml",
            "com.example.DepotHeadMapper",
            List.of(
                new MapperStatementCandidate(
                    id("mapper-statement", "update-status"),
                    "updateStatus",
                    "update",
                    excerpt(
                        fixtureRoot,
                        "src/main/resources/mapper/DepotHeadMapper.xml",
                        "id=\"updateStatus\""))),
            "CANDIDATE_NOT_YET_BOUND");
    ReopenedProgramGraphInputs reopened =
        new ReopenedProgramGraphInputs(
            source, new ProgramGraphDiscoveryInputs(discovery, List.of(entry), List.of(mapper)));
    RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory);
    try {
      FileSystemCanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(2, 100_000, 200_000, 8));
      CodeStructureGraphDraft structure =
          new CodeStructureGraphBuilder()
              .buildStructure(source, discovery, new CodeStructureGraphProfile(graphProfile));
      CodeStructureGraphDraftReference structureReference =
          new CodeStructureGraphModulePublisher(store)
              .publish(
                  new AnalysisStepModuleAddress(
                      AnalysisRunId.parse("analysis-run:" + digest("call-graph-module-run")),
                      AnalysisStepKey.PROGRAM_GRAPHS,
                      1,
                      "code-structure"),
                  source,
                  discovery,
                  structure);
      ReopenedCodeStructureGraph reopenedStructure =
          new PersistedCodeStructureGraphReader(store)
              .reopen(structureReference, reopened, graphProfile);
      return new Fixture(
          new CallGraphInputs(reopenedStructure, reopened),
          new CallGraphProfile(graphProfile),
          entryId);
    } catch (RuntimeException failure) {
      handle.close();
      throw failure;
    } finally {
      handle.close();
    }
  }

  private static CodeStructureSourceDocument document(String fixtureRoot, String path)
      throws Exception {
    byte[] bytes =
        Files.readAllBytes(
            Path.of("src/test/resources/analysis/graph").resolve(fixtureRoot).resolve(path));
    return new CodeStructureSourceDocument(
        id("file", path), path, ImmutableBytes.copyOf(bytes), new Sha256Digest(digest(bytes)));
  }

  private static SourceExcerptV1 excerpt(String fixtureRoot, String path, String value)
      throws Exception {
    byte[] source =
        Files.readAllBytes(
            Path.of("src/test/resources/analysis/graph").resolve(fixtureRoot).resolve(path));
    String text = new String(source, StandardCharsets.UTF_8);
    int startCharacter = text.indexOf(value);
    if (startCharacter < 0) {
      throw new IllegalArgumentException("fixture token is absent from frozen source");
    }
    int startByte = text.substring(0, startCharacter).getBytes(StandardCharsets.UTF_8).length;
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    int startLine =
        1
            + (int)
                text.substring(0, startCharacter)
                    .chars()
                    .filter(character -> character == '\n')
                    .count();
    int lineStart = text.lastIndexOf('\n', startCharacter - 1) + 1;
    int startColumn = startCharacter - lineStart + 1;
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
    entries
        .addObject()
        .put("artifactType", "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT")
        .put("schemaVersion", CodeStructureGraphDraft.SCHEMA_VERSION)
        .put("artifactIdPrefix", "code-structure-graph")
        .put("mediaType", "application/json")
        .put("envelopeKind", "MODULE_ARTIFACT_JSON")
        .put("emptyJsonlAllowed", false)
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
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

  private record Fixture(CallGraphInputs inputs, CallGraphProfile profile, ArtifactId entryId) {}
}
