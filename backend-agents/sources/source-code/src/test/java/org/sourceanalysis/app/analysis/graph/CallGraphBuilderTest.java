package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

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
import java.util.Arrays;
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
  void ownsOnePhysicalServiceCallSiteByTheUnionOfBothDiscoveredEntries() throws Exception {
    ArtifactId firstEntryId = id("entry", "batch-set-status");
    ArtifactId secondEntryId = id("entry", "retry-batch-set-status");
    CallGraphDraft draft = multiEntryDraft(List.of(firstEntryId, secondEntryId));
    CallGraphDraft reversedEntryOrder = multiEntryDraft(List.of(secondEntryId, firstEntryId));

    String sharedServiceCall =
        "com.example.DepotHeadService#batchSetStatus(java.lang.String)"
            + ":depotHeadMapper.updateStatus(java.lang.String)";
    List<CallGraphNode> sharedCallSites =
        draft.nodes().stream()
            .filter(node -> node.kind() == CallGraphNodeKind.CALL_SITE)
            .filter(node -> node.canonicalValue().equals(sharedServiceCall))
            .toList();
    assertThat(sharedCallSites)
        .as("one physical Service call site must not be copied per entry")
        .singleElement()
        .satisfies(
            node ->
                assertThat(node.owningEntryIds())
                    .containsExactly(firstEntryId, secondEntryId)
                    .as("ownership is the sorted union of both entry roots"));

    ArtifactId sharedCallSiteId = sharedCallSites.get(0).nodeId();
    assertThat(draft.edges())
        .filteredOn(
            edge ->
                edge.fromNodeId().equals(sharedCallSiteId)
                    || edge.toNodeId().equals(sharedCallSiteId))
        .hasSize(2);
    assertThat(draft.coverage().candidateElementIds())
        .filteredOn(candidate -> candidate.equals(sharedCallSiteId))
        .hasSize(1);
    assertThat(reversedEntryOrder).isEqualTo(draft);
  }

  @Test
  void distinguishesTwoSameShapedFieldCallsByTheirExactSourceSpans() throws Exception {
    Fixture fixture = fixture("call-graph-same-shape");

    java.util.concurrent.atomic.AtomicReference<CallGraphDraft> result =
        new java.util.concurrent.atomic.AtomicReference<>();
    Throwable thrown =
        catchThrowable(
            () ->
                result.set(new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile())));

    assertThat(thrown)
        .as("two physical calls with the same receiver/name/arguments must not collide")
        .isNull();
    if (thrown != null) {
      return;
    }

    CallGraphDraft draft = result.get();
    String serviceCall =
        "com.example.DepotHeadService#batchSetStatus(java.lang.String)"
            + ":depotHeadMapper.updateStatus(java.lang.String)";
    List<CallGraphNode> serviceCallSites =
        draft.nodes().stream()
            .filter(node -> node.kind() == CallGraphNodeKind.CALL_SITE)
            .filter(node -> node.canonicalValue().equals(serviceCall))
            .toList();
    assertThat(serviceCallSites)
        .as("each MethodCallExpr is a separate physical call site")
        .hasSize(2)
        .extracting(CallGraphNode::nodeId)
        .doesNotHaveDuplicates();
    assertThat(draft.edges())
        .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
        .filteredOn(
            edge ->
                serviceCallSites.stream().anyMatch(node -> node.nodeId().equals(edge.fromNodeId())))
        .hasSize(2);
    assertThat(draft.provenanceDrafts())
        .filteredOn(
            provenance ->
                provenance.ruleId().equals("java-static-field-receiver-call-v1")
                    && provenance.sourceLocator().path().endsWith("DepotHeadService.java"))
        .hasSize(2)
        .extracting(provenance -> provenance.sourceLocator().startByte())
        .doesNotHaveDuplicates();
  }

  @Test
  void changesGraphIdentityWhenOnlyPhysicalCallOwnerSetChanges() throws Exception {
    ArtifactId firstEntryId = id("entry", "same-handler-first");
    ArtifactId secondEntryId = id("entry", "same-handler-second");
    CallGraphDraft oneOwner = sameHandlerOwnerDraft(List.of(firstEntryId));
    CallGraphDraft twoOwners = sameHandlerOwnerDraft(List.of(firstEntryId, secondEntryId));

    assertThat(oneOwner.nodes()).isNotEmpty();
    assertThat(twoOwners.nodes()).isNotEmpty();
    assertThat(oneOwner.nodes())
        .extracting(CallGraphNode::nodeId)
        .containsExactlyElementsOf(twoOwners.nodes().stream().map(CallGraphNode::nodeId).toList());
    assertThat(oneOwner.edges())
        .extracting(CallGraphEdge::edgeId)
        .containsExactlyElementsOf(twoOwners.edges().stream().map(CallGraphEdge::edgeId).toList());
    assertThat(oneOwner.nodes())
        .extracting(CallGraphNode::owningEntryIds)
        .doesNotContain(twoOwners.nodes().get(0).owningEntryIds());
    assertThat(oneOwner.graphId())
        .as("graph identity must include owner sets, not only the changed payload")
        .isNotEqualTo(twoOwners.graphId());
  }

  @Test
  void mergesOneAmbiguousPhysicalCallGapAcrossEntryOwnersAndRebuildsItsIdentity() throws Exception {
    ArtifactId firstEntryId = id("entry", "ambiguous-same-handler-first");
    ArtifactId secondEntryId = id("entry", "ambiguous-same-handler-second");
    CallGraphDraft forward =
        sameHandlerAmbiguousDraft(
            List.of(firstEntryId, secondEntryId), firstEntryId, secondEntryId);
    CallGraphDraft reversed =
        sameHandlerAmbiguousDraft(
            List.of(secondEntryId, firstEntryId), firstEntryId, secondEntryId);

    assertThat(forward.gapDrafts()).singleElement().satisfies(this::assertSharedAmbiguousGap);
    assertThat(forward.coverage().gapDispositions()).hasSize(1);
    assertThat(reversed).isEqualTo(forward);
  }

  private void assertSharedAmbiguousGap(GraphGapDraft gap) {
    ArtifactId firstEntryId = id("entry", "ambiguous-same-handler-first");
    ArtifactId secondEntryId = id("entry", "ambiguous-same-handler-second");
    assertThat(gap.reasonCode()).isEqualTo("CALL_TARGET_AMBIGUOUS");
    assertThat(gap.affectedEntryIds()).containsExactlyInAnyOrder(firstEntryId, secondEntryId);
    assertThat(gap.affectedEntryIds())
        .isSortedAccordingTo(java.util.Comparator.comparing(ArtifactId::value));
    assertThat(gap.candidateElementIds()).hasSize(1);
    assertThat(gap.sourceLocator().path()).endsWith("DepotHeadService.java");
    GraphGapDraft expected =
        GraphGapDraft.forLocalOccurrence(
            ProgramGraphKind.CALL,
            gap.reasonCode(),
            List.of(firstEntryId, secondEntryId),
            gap.candidateElementIds(),
            gap.sourceLocator());
    assertThat(gap.gapId()).isEqualTo(expected.gapId());
  }

  @Test
  void resolvesAServiceMapperCallWhenItsArgumentComesFromAnExactLocalDeclaration()
      throws Exception {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithTwoServiceStatements(temporaryDirectory)) {
      CallGraphDraft draft =
          new CallGraphBuilder()
              .buildCalls(
                  new CallGraphInputs(fixture.structure(), fixture.reopenedInputs()),
                  new CallGraphProfile(fixture.graphProfileRef()));
      ArtifactId serviceMethodId =
          structureNodeId(
              fixture.structure().draft(),
              ProgramNodeKind.METHOD,
              "com.example.DepotHeadService#batchSetStatus(java.lang.String)");
      ArtifactId mapperMethodId =
          structureNodeId(
              fixture.structure().draft(),
              ProgramNodeKind.METHOD,
              "com.example.DepotHeadMapper#updateStatus(java.lang.String)");
      ArtifactId mapperStatementId =
          structureNodeId(
              fixture.structure().draft(),
              ProgramNodeKind.XML_STATEMENT,
              "com.example.DepotHeadMapper#updateStatus()");

      List<CallGraphEdge> serviceMapperTargets =
          draft.edges().stream()
              .filter(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
              .filter(edge -> edge.toNodeId().equals(mapperMethodId))
              .filter(
                  edge ->
                      draft.nodes().stream()
                          .filter(node -> node.nodeId().equals(edge.fromNodeId()))
                          .anyMatch(
                              node ->
                                  node.canonicalValue()
                                      .equals(
                                          "com.example.DepotHeadService#batchSetStatus(java.lang.String)"
                                              + ":depotHeadMapper.updateStatus(java.lang.String)")))
              .toList();
      assertThat(serviceMapperTargets)
          .as("the local normalized value must not hide the Service to Mapper call")
          .singleElement()
          .satisfies(
              edge -> {
                assertThat(edge.fromNodeId()).isNotEqualTo(serviceMethodId);
                assertThat(edge.resolution()).isEqualTo(ProgramResolution.EXACT);
                assertThat(edge.evidenceDraftRefs()).isNotEmpty();
              });

      assertThat(draft.edges())
          .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.JAVA_METHOD_TO_XML_STATEMENT)
          .filteredOn(edge -> edge.fromNodeId().equals(mapperMethodId))
          .singleElement()
          .satisfies(
              edge -> {
                assertThat(edge.toNodeId()).isEqualTo(mapperStatementId);
                assertThat(edge.ruleId()).isEqualTo("mybatis-namespace-signature-binding-v1");
                assertThat(edge.resolution()).isEqualTo(ProgramResolution.EXACT);
                assertThat(edge.evidenceDraftRefs()).isNotEmpty();
              });
    }
  }

  @Test
  void declaresEveryNodeAndEdgeEvidenceReferenceInItsProvenanceRegistry() throws Exception {
    Fixture fixture = fixture();

    CallGraphDraft draft = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

    assertThat(
            draft.nodes().stream()
                .flatMap(node -> node.evidenceDraftRefs().stream())
                .collect(java.util.stream.Collectors.toSet()))
        .isSubsetOf(
            draft.provenanceDrafts().stream()
                .map(ProvenanceDraftV1::provenanceDraftId)
                .collect(java.util.stream.Collectors.toSet()));
    assertThat(
            draft.edges().stream()
                .flatMap(edge -> edge.evidenceDraftRefs().stream())
                .collect(java.util.stream.Collectors.toSet()))
        .isSubsetOf(
            draft.provenanceDrafts().stream()
                .map(ProvenanceDraftV1::provenanceDraftId)
                .collect(java.util.stream.Collectors.toSet()));
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
  void recordsOneAmbiguousCallTargetGapForNullAgainstTwoVisibleReferenceOverloads()
      throws Exception {
    Fixture fixture = fixture("call-graph-resolution-ambiguous");

    CallGraphDraft draft = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

    assertThat(draft.gapDrafts())
        .singleElement()
        .satisfies(
            gap -> {
              assertThat(gap.reasonCode()).isEqualTo("CALL_TARGET_AMBIGUOUS");
              assertThat(gap.affectedEntryIds()).containsExactlyElementsOf(draft.entryIds());
              assertThat(gap.sourceLocator().path())
                  .isEqualTo("src/main/java/com/example/DepotHeadService.java");
              assertThat(sourceBytes(fixture, gap.sourceLocator()))
                  .isEqualTo("auditClient.recordStatus(null)");
              assertThat(gap.candidateElementIds()).hasSize(1);
            });

    GraphGapDraft gap = draft.gapDrafts().get(0);
    assertThat(draft.coverage().gapDispositions())
        .singleElement()
        .satisfies(
            disposition -> {
              assertThat(disposition.candidateElementId())
                  .isEqualTo(gap.candidateElementIds().get(0));
              assertThat(disposition.gapId()).isEqualTo(gap.gapId());
            });
    assertThat(draft.nodes()).noneMatch(node -> node.canonicalValue().contains("recordStatus"));
    assertThat(draft.edges())
        .noneMatch(
            edge ->
                draft.nodes().stream()
                    .filter(node -> node.canonicalValue().contains("recordStatus"))
                    .map(CallGraphNode::nodeId)
                    .anyMatch(
                        nodeId ->
                            nodeId.equals(edge.fromNodeId()) || nodeId.equals(edge.toNodeId())));
  }

  @Test
  void keepsAmbiguousCallSiteDispositionStableWhenReferenceOverloadDeclarationOrderChanges()
      throws Exception {
    Path declaredStringFirstRoot = Files.createTempDirectory(temporaryDirectory, "string-first");
    Path declaredIntegerFirstRoot = Files.createTempDirectory(temporaryDirectory, "integer-first");
    Fixture stringFirst = fixture("call-graph-resolution-ambiguous", declaredStringFirstRoot);
    Fixture integerFirst =
        fixture("call-graph-resolution-ambiguous-reversed", declaredIntegerFirstRoot);
    CallGraphDraft declaredStringFirst =
        new CallGraphBuilder().buildCalls(stringFirst.inputs(), stringFirst.profile());
    CallGraphDraft declaredIntegerFirst =
        new CallGraphBuilder().buildCalls(integerFirst.inputs(), integerFirst.profile());

    assertThat(declaredIntegerFirst.gapDrafts()).isEqualTo(declaredStringFirst.gapDrafts());
    assertThat(declaredIntegerFirst.coverage().gapDispositions())
        .isEqualTo(declaredStringFirst.coverage().gapDispositions());
  }

  @Test
  void resolvesNullToTheReferenceOverloadWhenPrimitiveOverloadAlsoExists() throws Exception {
    Fixture fixture = fixture("call-graph-resolution-null-reference-compatible");

    CallGraphDraft draft = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());
    ArtifactId stringTarget =
        structureMethodId(
            fixture.inputs().structure().draft(),
            "com.example.AuditClient#recordStatus(java.lang.String)");
    List<ArtifactId> stringCallSites =
        draft.edges().stream()
            .filter(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
            .filter(edge -> edge.toNodeId().equals(stringTarget))
            .map(CallGraphEdge::fromNodeId)
            .toList();
    assertThat(stringCallSites).singleElement();
    ArtifactId stringCallSite = stringCallSites.get(0);

    assertThat(draft.gapDrafts()).isEmpty();
    assertThat(draft.edges())
        .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
        .filteredOn(edge -> edge.toNodeId().equals(stringTarget))
        .singleElement()
        .satisfies(edge -> assertThat(edge.toNodeId()).isEqualTo(stringTarget));
    assertThat(draft.edges())
        .filteredOn(edge -> edge.kind() == CallGraphEdgeKind.CALL_RETURN)
        .filteredOn(edge -> edge.fromNodeId().equals(stringTarget))
        .singleElement()
        .satisfies(
            edge -> {
              assertThat(edge.fromNodeId()).isEqualTo(stringTarget);
              assertThat(edge.toNodeId()).isEqualTo(stringCallSite);
            });
    assertThat(draft.nodes())
        .noneMatch(node -> node.canonicalValue().contains("recordStatus(int)"));
  }

  @Test
  void recordsTargetUnresolvedGapWhenNullIsPassedToPrimitiveOnlyTarget() throws Exception {
    Fixture fixture = fixture("call-graph-resolution-null-primitive-incompatible");

    CallGraphDraft draft = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

    assertThat(draft.gapDrafts())
        .singleElement()
        .satisfies(
            gap -> {
              assertThat(gap.reasonCode()).isEqualTo("CALL_TARGET_UNRESOLVED");
              assertThat(gap.affectedEntryIds()).containsExactlyElementsOf(draft.entryIds());
              assertThat(sourceBytes(fixture, gap.sourceLocator()))
                  .isEqualTo("auditClient.recordStatus(null)");
            });
    assertThat(draft.coverage().gapDispositions()).hasSize(1);
    assertThat(draft.gapDrafts())
        .noneMatch(gap -> gap.reasonCode().equals("CALL_ARGUMENT_TYPE_UNRESOLVED"));
    ArtifactId primitiveTarget =
        structureMethodId(
            fixture.inputs().structure().draft(), "com.example.AuditClient#recordStatus(int)");
    assertThat(draft.edges())
        .noneMatch(
            edge ->
                edge.toNodeId().equals(primitiveTarget)
                    || edge.fromNodeId().equals(primitiveTarget));
    assertThat(draft.nodes()).noneMatch(node -> node.canonicalValue().contains("recordStatus"));
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
  void recordsAGapWhenTheKnownMapperHasNoCandidateForTheCalledMethod() throws Exception {
    Fixture fixture = unresolvedMapperMethodFixture();

    CallGraphDraft draft = new CallGraphBuilder().buildCalls(fixture.inputs(), fixture.profile());

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

  private Fixture fixture(String fixtureRoot, boolean includeMapperMethodCandidate)
      throws Exception {
    return fixture(fixtureRoot, includeMapperMethodCandidate, temporaryDirectory);
  }

  private Fixture fixture(String fixtureRoot, Path storeRoot) throws Exception {
    return fixture(fixtureRoot, true, storeRoot);
  }

  private Fixture fixture(String fixtureRoot, boolean includeMapperMethodCandidate, Path storeRoot)
      throws Exception {
    try (PersistedFixture persisted =
        persistedFixture(fixtureRoot, includeMapperMethodCandidate, storeRoot)) {
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

  private PersistedFixture persistedFixture(
      String fixtureRoot, boolean includeMapperMethodCandidate) throws Exception {
    return persistedFixture(fixtureRoot, includeMapperMethodCandidate, temporaryDirectory);
  }

  private PersistedFixture persistedFixture(
      String fixtureRoot, boolean includeMapperMethodCandidate, Path storeRoot) throws Exception {
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
            source, new ProgramGraphDiscoveryInputs(discovery, List.of(entry), List.of(mapper)));
    RunStoreHandle handle = RunStoreBootstrap.openForTest(storeRoot);
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

  private static String sourceBytes(Fixture fixture, SourceLocatorV1 locator) {
    CodeStructureSourceDocument document =
        fixture.inputs().reopened().source().documents().stream()
            .filter(candidate -> candidate.fileId().equals(locator.fileId()))
            .findFirst()
            .orElseThrow();
    byte[] bytes = document.rawUtf8().copyToByteArray();
    return new String(
        Arrays.copyOfRange(
            bytes,
            Math.toIntExact(locator.startByte()),
            Math.toIntExact(locator.endByteExclusive())),
        StandardCharsets.UTF_8);
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        new Sha256Digest(digest("toolchain")),
        new Sha256Digest(digest("profile")),
        new Sha256Digest(digest("schema")),
        null,
        policies.reference());
  }

  private CallGraphDraft multiEntryDraft(List<ArtifactId> requestedEntryOrder) throws Exception {
    String fixtureRoot = "call-graph-multi-entry";
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    ArtifactReference graphProfile = reference("graph-profile", "multi-entry-call-graph-v2");
    ArtifactId firstEntryId = id("entry", "batch-set-status");
    ArtifactId secondEntryId = id("entry", "retry-batch-set-status");
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("multi-entry-call-graph-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "multi-entry-call-graph"),
            reference("verified-snapshot", "multi-entry-call-graph"),
            controls,
            List.of(
                document(fixtureRoot, "src/main/java/com/example/DepotHeadController.java"),
                document(fixtureRoot, "src/main/java/com/example/DepotHeadService.java"),
                document(fixtureRoot, "src/main/java/com/example/DepotHeadMapper.java"),
                document(fixtureRoot, "src/main/resources/mapper/DepotHeadMapper.xml")));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", "multi-entry-call-graph"),
            reference("application-profile", "multi-entry-call-graph"),
            reference("capability-report", "multi-entry-call-graph"),
            reference("entry-points", "multi-entry-call-graph"),
            reference("mapper-catalog", "multi-entry-call-graph"),
            List.of(firstEntryId, secondEntryId));
    List<HttpEntryPoint> entries =
        List.of(
            multiEntry(
                fixtureRoot,
                firstEntryId,
                "POST",
                "/depotHead/batchSetStatus",
                "batchSetStatus",
                "@PostMapping(\"/batchSetStatus\")"),
            multiEntry(
                fixtureRoot,
                secondEntryId,
                "POST",
                "/depotHead/batchSetStatus/retry",
                "retryBatchSetStatus",
                "@PostMapping(\"/batchSetStatus/retry\")"));
    List<HttpEntryPoint> requestedEntries =
        requestedEntryOrder.stream()
            .map(
                entryId ->
                    entries.stream()
                        .filter(entry -> entry.entryId().equals(entryId))
                        .findFirst()
                        .orElseThrow())
            .toList();
    MapperCatalogEntry mapper =
        new MapperCatalogEntry(
            id("mapper-catalog-entry", "multi-entry-depot-head"),
            "com.example.DepotHeadMapper",
            List.of(
                new MapperMethodCandidate(
                    id("mapper-method", "multi-entry-update-status"),
                    "updateStatus(java.lang.String)",
                    excerpt(
                        fixtureRoot,
                        "src/main/java/com/example/DepotHeadMapper.java",
                        "void updateStatus(String status);"))),
            "src/main/resources/mapper/DepotHeadMapper.xml",
            "com.example.DepotHeadMapper",
            List.of(
                new MapperStatementCandidate(
                    id("mapper-statement", "multi-entry-update-status"),
                    "updateStatus",
                    "update",
                    excerpt(
                        fixtureRoot,
                        "src/main/resources/mapper/DepotHeadMapper.xml",
                        "id=\"updateStatus\""))),
            "CANDIDATE_NOT_YET_BOUND");
    ReopenedProgramGraphInputs reopened =
        new ReopenedProgramGraphInputs(
            source, new ProgramGraphDiscoveryInputs(discovery, requestedEntries, List.of(mapper)));

    Path storeRoot =
        Files.createDirectory(
            temporaryDirectory.resolve(
                "multi-entry-"
                    + (requestedEntryOrder.get(0).equals(firstEntryId) ? "forward" : "reverse")));
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(storeRoot)) {
      FileSystemCanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(2, 100_000, 200_000, 8));
      CodeStructureGraphDraftReference structureReference =
          new CodeStructureGraphModulePublisher(store)
              .publish(
                  new AnalysisStepModuleAddress(
                      AnalysisRunId.parse("analysis-run:" + digest("multi-entry-call-graph-run")),
                      AnalysisStepKey.PROGRAM_GRAPHS,
                      1,
                      "code-structure"),
                  source,
                  discovery,
                  new CodeStructureGraphBuilder()
                      .buildStructure(
                          source, discovery, new CodeStructureGraphProfile(graphProfile)));
      ReopenedCodeStructureGraph structure =
          new PersistedCodeStructureGraphReader(store)
              .reopen(structureReference, reopened, graphProfile);
      return new CallGraphBuilder()
          .buildCalls(new CallGraphInputs(structure, reopened), new CallGraphProfile(graphProfile));
    }
  }

  private CallGraphDraft sameHandlerOwnerDraft(List<ArtifactId> requestedEntryOrder)
      throws Exception {
    return sameHandlerDraft(
        "call-graph",
        "same-handler-owner-call-graph",
        requestedEntryOrder,
        id("entry", "same-handler-first"),
        id("entry", "same-handler-second"));
  }

  private CallGraphDraft sameHandlerAmbiguousDraft(
      List<ArtifactId> requestedEntryOrder, ArtifactId firstEntryId, ArtifactId secondEntryId)
      throws Exception {
    return sameHandlerDraft(
        "call-graph-resolution-ambiguous",
        "same-handler-ambiguous-call-graph",
        requestedEntryOrder,
        firstEntryId,
        secondEntryId);
  }

  private CallGraphDraft sameHandlerDraft(
      String fixtureRoot,
      String graphProfileValue,
      List<ArtifactId> requestedEntryOrder,
      ArtifactId firstEntryId,
      ArtifactId secondEntryId)
      throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    ArtifactReference graphProfile = reference("graph-profile", graphProfileValue);
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest(graphProfileValue + "-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", graphProfileValue),
            reference("verified-snapshot", graphProfileValue),
            controls,
            List.of(
                document(fixtureRoot, "src/main/java/com/example/DepotHeadController.java"),
                document(fixtureRoot, "src/main/java/com/example/DepotHeadService.java"),
                document(fixtureRoot, "src/main/java/com/example/DepotHeadMapper.java"),
                document(fixtureRoot, "src/main/resources/mapper/DepotHeadMapper.xml")));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", graphProfileValue),
            reference("application-profile", graphProfileValue),
            reference("capability-report", graphProfileValue),
            reference("entry-points", graphProfileValue),
            reference("mapper-catalog", graphProfileValue),
            List.of(firstEntryId, secondEntryId));
    List<HttpEntryPoint> entries =
        List.of(
            sameHandlerEntry(fixtureRoot, firstEntryId, "POST", "/depotHead/batchSetStatus"),
            sameHandlerEntry(
                fixtureRoot, secondEntryId, "POST", "/depotHead/batchSetStatus/alias"));
    List<HttpEntryPoint> requestedEntries =
        requestedEntryOrder.stream()
            .map(
                entryId ->
                    entries.stream()
                        .filter(entry -> entry.entryId().equals(entryId))
                        .findFirst()
                        .orElseThrow())
            .toList();
    MapperCatalogEntry mapper =
        new MapperCatalogEntry(
            id("mapper-catalog-entry", graphProfileValue),
            "com.example.DepotHeadMapper",
            List.of(
                new MapperMethodCandidate(
                    id("mapper-method", graphProfileValue),
                    "updateStatus(java.lang.String)",
                    excerpt(
                        fixtureRoot,
                        "src/main/java/com/example/DepotHeadMapper.java",
                        "void updateStatus(String status);"))),
            "src/main/resources/mapper/DepotHeadMapper.xml",
            "com.example.DepotHeadMapper",
            List.of(
                new MapperStatementCandidate(
                    id("mapper-statement", graphProfileValue),
                    "updateStatus",
                    "update",
                    excerpt(
                        fixtureRoot,
                        "src/main/resources/mapper/DepotHeadMapper.xml",
                        "id=\"updateStatus\""))),
            "CANDIDATE_NOT_YET_BOUND");
    ReopenedProgramGraphInputs reopened =
        new ReopenedProgramGraphInputs(
            source, new ProgramGraphDiscoveryInputs(discovery, requestedEntries, List.of(mapper)));
    Path storeRoot =
        Files.createDirectory(
            temporaryDirectory.resolve(
                graphProfileValue
                    + "-"
                    + requestedEntryOrder.size()
                    + "-"
                    + digest(requestedEntryOrder.toString()).substring(0, 8)));
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(storeRoot)) {
      FileSystemCanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(2, 100_000, 200_000, 8));
      CodeStructureGraphDraftReference structureReference =
          new CodeStructureGraphModulePublisher(store)
              .publish(
                  new AnalysisStepModuleAddress(
                      AnalysisRunId.parse("analysis-run:" + digest(graphProfileValue + "-run")),
                      AnalysisStepKey.PROGRAM_GRAPHS,
                      1,
                      "code-structure"),
                  source,
                  discovery,
                  new CodeStructureGraphBuilder()
                      .buildStructure(
                          source, discovery, new CodeStructureGraphProfile(graphProfile)));
      ReopenedCodeStructureGraph structure =
          new PersistedCodeStructureGraphReader(store)
              .reopen(structureReference, reopened, graphProfile);
      return new CallGraphBuilder()
          .buildCalls(new CallGraphInputs(structure, reopened), new CallGraphProfile(graphProfile));
    }
  }

  private static HttpEntryPoint sameHandlerEntry(
      String fixtureRoot, ArtifactId entryId, String method, String route) throws Exception {
    return new HttpEntryPoint(
        entryId,
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        method,
        route,
        List.of("/depotHead", route.substring("/depotHead".length())),
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
  }

  private static HttpEntryPoint multiEntry(
      String fixtureRoot,
      ArtifactId entryId,
      String method,
      String route,
      String handlerMethod,
      String methodMapping)
      throws Exception {
    return new HttpEntryPoint(
        entryId,
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        method,
        route,
        List.of("/depotHead", route.substring("/depotHead".length())),
        "com.example.DepotHeadController#" + handlerMethod,
        List.of("status"),
        List.of(
            excerpt(
                fixtureRoot,
                "src/main/java/com/example/DepotHeadController.java",
                "class DepotHeadController"),
            excerpt(
                fixtureRoot, "src/main/java/com/example/DepotHeadController.java", methodMapping)));
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
