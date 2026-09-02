package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.sourceanalysis.app.analysis.discovery.MapperMethodCandidate;
import org.sourceanalysis.app.analysis.discovery.MapperStatementCandidate;
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

class CallGraphBuilderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void resolvesOneStaticControllerToServiceCallAndItsReturnPair() throws Exception {
    Fixture fixture = fixture();

    CallGraphDraft draft = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

    assertThat(draft.graphKind()).isEqualTo(ProgramGraphKind.CALL);
    assertThat(draft.nodes())
        .extracting(CallGraphNode::canonicalValue)
        .contains(
            "com.example.DepotHeadController#batchSetStatus(java.lang.String):depotHeadService.batchSetStatus(java.lang.String)");
    assertThat(draft.edges())
        .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
        .anySatisfy(
            edge ->
                assertThat(edge.toNodeId())
                    .isEqualTo(
                        structureMethodId(
                            fixture.inputs().structure().draft(),
                            "com.example.DepotHeadService#batchSetStatus(java.lang.String)")));
    assertThat(draft.edges())
        .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.CALL_RETURN)
        .anySatisfy(
            edge ->
                assertThat(draft.nodes())
                    .extracting(CallGraphNode::nodeId)
                    .contains(edge.toNodeId()));
    assertThat(draft.coverage().candidateElementIds())
        .containsAll(draft.coverage().exactElementIds());
  }

  @Test
  void resolvesTheExactServiceToMapperAndMapperToXmlStatementChain() throws Exception {
    Fixture fixture = fixture();

    CallGraphDraft draft = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

    assertThat(draft.edges())
        .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
        .extracting(CallGraphEdge::toNodeId)
        .contains(
            structureNodeId(
                fixture.inputs().structure().draft(),
                ProgramNodeKind.METHOD,
                "com.example.DepotHeadService#batchSetStatus(java.lang.String)"),
            structureNodeId(
                fixture.inputs().structure().draft(),
                ProgramNodeKind.METHOD,
                "com.example.DepotHeadMapper#updateStatus(java.lang.String)"));
    assertThat(draft.edges())
        .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.JAVA_METHOD_TO_XML_STATEMENT)
        .singleElement()
        .satisfies(
            edge -> {
              assertThat(edge.fromNodeId())
                  .isEqualTo(
                      structureNodeId(
                          fixture.inputs().structure().draft(),
                          ProgramNodeKind.METHOD,
                          "com.example.DepotHeadMapper#updateStatus(java.lang.String)"));
              assertThat(edge.toNodeId())
                  .isEqualTo(
                      structureNodeId(
                          fixture.inputs().structure().draft(),
                          ProgramNodeKind.XML_STATEMENT,
                          "com.example.DepotHeadMapper#updateStatus()"));
            });
  }

  @Test
  void recordsAGapInsteadOfChoosingOneOverloadedHttpHandler() throws Exception {
    Fixture fixture = overloadedEntryFixture();

    CallGraphDraft draft = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

    assertThat(draft.nodes()).isEmpty();
    assertThat(draft.edges()).isEmpty();
    assertThat(draft.coverage().gapDispositions()).hasSize(1);
  }

  @Test
  void recordsAGapWhenAnExplicitImportWouldMakeTheReceiverTargetDifferent() throws Exception {
    Fixture fixture = importedDecoyFixture();

    CallGraphDraft draft = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

    assertThat(draft.edges())
        .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
        .isEmpty();
    assertThat(draft.coverage().gapDispositions()).hasSize(1);
  }

  @Test
  void recordsAGapWhenTheKnownMapperHasNoCandidateForTheCalledMethod()
      throws Exception {
    Fixture fixture = unresolvedMapperMethodFixture();

    CallGraphDraft draft =
        new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

    assertThat(draft.edges())
        .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.JAVA_METHOD_TO_XML_STATEMENT)
        .isEmpty();
    assertThat(draft.coverage().gapDispositions()).hasSize(1);
  }

  @Test
  void rebuildsTheSameCanonicalCallGraphFromTheSameFreshReopenedInputs() throws Exception {
    Fixture fixture = fixture();

    CallGraphDraft first = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());
    CallGraphDraft second = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

    assertThat(second).isEqualTo(first);
  }

  @Test
  void rejectsADifferentCallGraphProfileBeforeItCanResolveCalls() throws Exception {
    Fixture fixture = fixture();

    assertThatThrownBy(
            () ->
                new CallGraphBuilder()
                    .buildCalls(
                        fixture.inputs(),
                        new CallGraphProfile(reference("graph-profile", "different-profile"))))
        .isInstanceOf(GraphReferenceException.class)
        .hasMessage("GRAPH_REFERENCE_BROKEN");
  }

  @Test
  void rejectsPersistedStructureWhenItsControlsDoNotMatchTheFreshReopenedInputs() throws Exception {
    try (PersistedFixture persisted = persistedFixture("call-graph")) {
      ArtifactControls changedControls =
          new ArtifactControls(
              new Sha256Digest(digest("different-toolchain")),
              persisted.source().controls().profileSha256(),
              persisted.source().controls().schemaBundleSha256(),
              persisted.source().controls().promptBundleSha256(),
              persisted.source().controls().artifactPolicyRegistryRef());
      CodeStructureSource changedSource =
          new CodeStructureSource(
              persisted.source().snapshotId(),
              persisted.source().inventoryScopeKind(),
              persisted.source().repositoryCompletionEligible(),
              persisted.source().sourceInventoryRef(),
              persisted.source().verifiedSnapshotRef(),
              changedControls,
              persisted.source().documents());
      ReopenedProgramGraphInputs changedInputs =
          new ReopenedProgramGraphInputs(changedSource, persisted.reopened().discovery());

      assertThatThrownBy(
              () ->
                  persisted
                      .reader()
                      .reopen(persisted.reference(), changedInputs, persisted.graphProfileRef()))
          .isInstanceOf(GraphReferenceException.class)
          .hasMessage("GRAPH_REFERENCE_BROKEN");
    }
  }

  private Fixture fixture() throws Exception {
    return fixture("call-graph");
  }

  private Fixture overloadedEntryFixture() throws Exception {
    return fixture("call-graph-overload");
  }

  private Fixture importedDecoyFixture() throws Exception {
    return fixture("call-graph-import-decoy");
  }

  private Fixture unresolvedMapperMethodFixture() throws Exception {
    return fixture("call-graph", false);
  }

  private Fixture fixture(String fixtureRoot) throws Exception {
    return fixture(fixtureRoot, true);
  }

  private Fixture fixture(String fixtureRoot, boolean includeMapperMethodCandidate) throws Exception {
    try (PersistedFixture persisted = persistedFixture(fixtureRoot, includeMapperMethodCandidate)) {
      ReopenedCodeStructureGraph reopenedStructure =
          persisted
              .reader()
              .reopen(persisted.reference(), persisted.reopened(), persisted.graphProfileRef());
      return new Fixture(
          new CallGraphInputs(reopenedStructure, persisted.reopened()),
          new CallGraphProfile(persisted.graphProfileRef()));
    }
  }

  private PersistedFixture persistedFixture(String fixtureRoot) throws Exception {
    return persistedFixture(fixtureRoot, true);
  }

  private PersistedFixture persistedFixture(String fixtureRoot, boolean includeMapperMethodCandidate)
      throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
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
    ArtifactId entryId = id("entry", "batch-set-status");
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", "call-graph"),
            reference("application-profile", "call-graph"),
            reference("capability-report", "call-graph"),
            reference("entry-points", "call-graph"),
            reference("mapper-catalog", "call-graph"),
            List.of(entryId));
    ArtifactReference profile = reference("graph-profile", "code-structure-v2");
    CodeStructureGraphDraft structure =
        new CodeStructureGraphBuilder()
            .buildStructure(source, discovery, new CodeStructureGraphProfile(profile));
    HttpEntryPoint entry =
        new HttpEntryPoint(
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
            includeMapperMethodCandidate
                ? List.of(
                    new MapperMethodCandidate(
                        id("mapper-method", "update-status"),
                        "updateStatus(java.lang.String)",
                        excerpt(
                            fixtureRoot,
                            "src/main/java/com/example/DepotHeadMapper.java",
                            "void updateStatus(String status);")))
                : List.of(),
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
            source,
            new ProgramGraphDiscoveryInputs(
                discovery, List.of(entry), List.of(mapper)));
    RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory);
    try {
      FileSystemCanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(2, 100_000, 200_000, 8));
      CodeStructureGraphDraftReference persisted =
          new CodeStructureGraphModulePublisher(store)
              .publish(
                  new org.sourceanalysis.app.artifact.AnalysisStepModuleAddress(
                      org.sourceanalysis.app.artifact.AnalysisRunId.parse(
                          "analysis-run:" + digest("call-graph-module-run")),
                      org.sourceanalysis.app.artifact.AnalysisStepKey.PROGRAM_GRAPHS,
                      1,
                      "code-structure"),
                  source,
                  discovery,
                  structure);
      return new PersistedFixture(
          handle,
          source,
          reopened,
          profile,
          persisted,
          new PersistedCodeStructureGraphReader(store));
    } catch (RuntimeException failure) {
      handle.close();
      throw failure;
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

  private static ArtifactId structureMethodId(CodeStructureGraphDraft structure, String value) {
    return structureNodeId(structure, ProgramNodeKind.METHOD, value);
  }

  private static ArtifactId structureNodeId(
      CodeStructureGraphDraft structure, ProgramNodeKind kind, String value) {
    return structure.nodes().stream()
        .filter(node -> node.kind() == kind && node.canonicalValue().equals(value))
        .map(DraftProgramNode::nodeId)
        .findFirst()
        .orElseThrow();
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

  private record Fixture(CallGraphInputs inputs, CallGraphProfile profile) {}

  private record PersistedFixture(
      RunStoreHandle handle,
      CodeStructureSource source,
      ReopenedProgramGraphInputs reopened,
      ArtifactReference graphProfileRef,
      CodeStructureGraphDraftReference reference,
      PersistedCodeStructureGraphReader reader)
      implements AutoCloseable {

    @Override
    public void close() {
      handle.close();
    }
  }
}
