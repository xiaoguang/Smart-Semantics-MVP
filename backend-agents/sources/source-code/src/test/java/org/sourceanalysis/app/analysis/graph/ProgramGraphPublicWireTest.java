package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceipt;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Retention guard for historical graph readers after strict graph producers were retired. */
class ProgramGraphPublicWireTest {

  @Test
  void currentFixtureKeepsSourceAndDiscoveryWireAvailableWithoutInstallingLegacyGraphs(
      @TempDir Path temporaryDirectory) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporaryDirectory.resolve("fixture"))) {
      assertThat(fixture.sourceInventory()).isNotNull();
      assertThat(fixture.applicationDiscovery()).isNotNull();
      assertThat(fixture.programGraphs()).isNull();
      assertThat(
              fixture
                  .stepArtifacts()
                  .reopen(fixture.applicationDiscovery().publication())
                  .semanticPayloads())
          .extracting(value -> value.descriptor().fileName())
          .contains("entry-points.jsonl", "mapper-catalog.jsonl");
    }
  }

  @Test
  void reopensAStoredCodeStructureGraphAndRejectsPayloadIdentityTampering() {
    HistoricalGraphFixture fixture = HistoricalGraphFixture.open();
    PersistedCodeStructureGraphReader reader =
        new PersistedCodeStructureGraphReader(fixture.store());

    ReopenedCodeStructureGraph reopened =
        reader.reopen(fixture.reference(), fixture.inputs(), fixture.graphProfile());
    assertThat(reopened.draft().graphId()).isEqualTo(fixture.draft().graphId());
    assertThat(reopened.draft().nodes()).isEmpty();
    assertThat(reopened.draft().coverage().closed()).isTrue();

    assertThatThrownBy(
            () ->
                new PersistedCodeStructureGraphReader(fixture.tamperedStore())
                    .reopen(fixture.reference(), fixture.inputs(), fixture.graphProfile()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("GRAPH_REFERENCE_BROKEN");
  }

  @Test
  void reopensEveryStoredHistoricalGraphPayloadWithPredecessorIdentity() {
    HistoricalGraphReaderFixture fixture = HistoricalGraphReaderFixture.open();
    ReopenedCodeStructureGraph structure =
        new PersistedCodeStructureGraphReader(fixture.store())
            .reopen(fixture.codeStructureReference(), fixture.inputs(), fixture.graphProfile());
    ReopenedCallGraph calls =
        new PersistedCallGraphReader(fixture.store())
            .reopen(fixture.callReference(), fixture.inputs(), structure, fixture.graphProfile());
    ReopenedControlFlowGraph controlFlow =
        new PersistedControlFlowGraphReader(fixture.store())
            .reopen(
                fixture.controlFlowReference(),
                fixture.inputs(),
                structure,
                calls,
                fixture.graphProfile());
    ReopenedDataFlowGraph dataFlow =
        new PersistedDataFlowGraphReader(fixture.store())
            .reopen(
                fixture.dataFlowReference(),
                fixture.inputs(),
                structure,
                calls,
                controlFlow,
                fixture.graphProfile());
    ReopenedEvidenceGraph evidence =
        new PersistedEvidenceGraphReader(fixture.store())
            .reopen(
                fixture.evidenceReference(),
                fixture.inputs(),
                structure,
                calls,
                controlFlow,
                dataFlow,
                fixture.graphProfile());

    assertThat(structure.draft().graphKind()).isEqualTo(ProgramGraphKind.CODE_STRUCTURE);
    assertThat(structure.draft().snapshotId()).isEqualTo("snapshot:" + "1".repeat(64));
    assertThat(structure.draft().applicationProfileId())
        .isEqualTo(ArtifactId.parse("application-profile:" + "2".repeat(64)));
    assertThat(structure.draft().coverage().closed()).isTrue();
    assertThat(calls.draft().graphKind()).isEqualTo(ProgramGraphKind.CALL);
    assertThat(calls.codeStructurePayloadRef()).isEqualTo(structure.payloadRef());
    assertThat(controlFlow.draft().graphKind()).isEqualTo(ProgramGraphKind.CONTROL_FLOW);
    assertThat(controlFlow.callGraphPayloadRef()).isEqualTo(calls.payloadRef());
    assertThat(dataFlow.draft().graphKind()).isEqualTo(ProgramGraphKind.DATA_FLOW);
    assertThat(dataFlow.controlFlowPayloadRef()).isEqualTo(controlFlow.payloadRef());
    assertThat(dataFlow.draft().worklistAccounting().enqueuedWorkItemIds()).isEmpty();
    assertThat(evidence.draft().graphKind()).isEqualTo(ProgramGraphKind.EVIDENCE);
    assertThat(evidence.dataFlowPayloadRef()).isEqualTo(dataFlow.payloadRef());
    assertThat(evidence.draft().coverage().closed()).isTrue();
  }

  @Test
  void rejectsCorruptPayloadAndPersistedUpstreamMismatch() {
    HistoricalGraphReaderFixture fixture = HistoricalGraphReaderFixture.open();
    ReopenedCodeStructureGraph structure =
        new PersistedCodeStructureGraphReader(fixture.store())
            .reopen(fixture.codeStructureReference(), fixture.inputs(), fixture.graphProfile());
    ReopenedCallGraph calls =
        new PersistedCallGraphReader(fixture.store())
            .reopen(fixture.callReference(), fixture.inputs(), structure, fixture.graphProfile());
    ReopenedControlFlowGraph controlFlow =
        new PersistedControlFlowGraphReader(fixture.store())
            .reopen(
                fixture.controlFlowReference(),
                fixture.inputs(),
                structure,
                calls,
                fixture.graphProfile());

    assertThatThrownBy(
            () ->
                new PersistedDataFlowGraphReader(fixture.corruptPayloadStore("data-flow"))
                    .reopen(
                        fixture.dataFlowReference(),
                        fixture.inputs(),
                        structure,
                        calls,
                        controlFlow,
                        fixture.graphProfile()))
        .isInstanceOf(GraphReferenceException.class)
        .hasMessage("GRAPH_REFERENCE_BROKEN");

    assertThatThrownBy(
            () ->
                new PersistedCallGraphReader(fixture.upstreamMismatchStore("call-graph"))
                    .reopen(
                        fixture.callReference(),
                        fixture.inputs(),
                        structure,
                        fixture.graphProfile()))
        .isInstanceOf(GraphReferenceException.class)
        .hasMessage("GRAPH_REFERENCE_BROKEN");
  }

  private static final class HistoricalGraphFixture {
    private static final String SNAPSHOT = "snapshot:" + "1".repeat(64);
    private static final ArtifactId APP = ArtifactId.parse("application-profile:" + "2".repeat(64));
    private static final AnalysisRunId RUN = AnalysisRunId.parse("analysis-run:" + "3".repeat(64));

    private final CodeStructureGraphDraft draft;
    private final ReopenedProgramGraphInputs inputs;
    private final ArtifactReference graphProfile;
    private final CodeStructureGraphDraftReference reference;
    private final ReopenedModulePublication saved;
    private final ReopenedModulePublication tampered;

    private HistoricalGraphFixture(
        CodeStructureGraphDraft draft,
        ReopenedProgramGraphInputs inputs,
        ArtifactReference graphProfile,
        CodeStructureGraphDraftReference reference,
        ReopenedModulePublication saved,
        ReopenedModulePublication tampered) {
      this.draft = draft;
      this.inputs = inputs;
      this.graphProfile = graphProfile;
      this.reference = reference;
      this.saved = saved;
      this.tampered = tampered;
    }

    static HistoricalGraphFixture open() {
      ArtifactControls controls =
          new ArtifactControls(
              new Sha256Digest("4".repeat(64)),
              new Sha256Digest("5".repeat(64)),
              new Sha256Digest("6".repeat(64)),
              null,
              new ArtifactPolicyRegistryReference(
                  ArtifactId.parse("artifact-policy-registry:" + "7".repeat(64)),
                  new Sha256Digest("8".repeat(64))));
      ArtifactReference sourceInventory = ref("source-inventory", '9');
      ArtifactReference verifiedSnapshot = ref("verified-snapshot", 'a');
      ArtifactReference appRef = ref("application-profile", 'b');
      ArtifactReference capability = ref("capability-report", 'c');
      ArtifactReference entries = ref("entry-points", 'd');
      ArtifactReference mappers = ref("mapper-catalog", 'e');
      ArtifactReference graphProfile = ref("graph-profile", 'f');
      CodeStructureSourceDocument document =
          new CodeStructureSourceDocument(
              ArtifactId.parse("file:" + "0".repeat(64)),
              "src/main/java/Empty.java",
              ImmutableBytes.copyOf("class Empty {}\n".getBytes(StandardCharsets.UTF_8)),
              new Sha256Digest(sha256("class Empty {}\n".getBytes(StandardCharsets.UTF_8))));
      CodeStructureSource source =
          new CodeStructureSource(
              SNAPSHOT,
              "COMPLETE_CAPTURE",
              true,
              sourceInventory,
              verifiedSnapshot,
              controls,
              List.of(document));
      CodeStructureDiscovery discovery =
          new CodeStructureDiscovery(APP, appRef, capability, entries, mappers, List.of());
      ReopenedProgramGraphInputs inputs =
          new ReopenedProgramGraphInputs(
              source, new ProgramGraphDiscoveryInputs(discovery, List.of(), List.of()));
      GraphCoverage coverage =
          new GraphCoverage(List.of(), List.of(), List.of(), List.of(), List.of(), true);
      ArtifactId graphId =
          CodeStructureGraphDraft.calculateGraphId(
              SNAPSHOT,
              APP,
              graphProfile,
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              coverage);
      CodeStructureGraphDraft draft =
          new CodeStructureGraphDraft(
              CodeStructureGraphDraft.SCHEMA_VERSION,
              ProgramGraphKind.CODE_STRUCTURE,
              graphId,
              SNAPSHOT,
              APP,
              graphProfile,
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              coverage);
      ModulePublicationReference publication =
          new ModulePublicationReference(
              new AnalysisStepModuleAddress(
                  RUN, AnalysisStepKey.PROGRAM_GRAPHS, 1, "code-structure"),
              ModuleArtifactRoot.parse("module-root:" + "1".repeat(64)),
              ModuleReceiptId.parse("module-receipt:" + "2".repeat(64)),
              new Sha256Digest("3".repeat(64)));
      CodeStructureGraphDraftReference reference =
          new CodeStructureGraphDraftReference(publication);
      ReopenedModulePublication saved = publication(reference, controls, draft, graphProfile, null);
      ReopenedModulePublication tampered =
          publication(
              reference,
              controls,
              draft,
              graphProfile,
              ArtifactId.parse("program-graphs-code-structure-graph:" + "9".repeat(64)));
      return new HistoricalGraphFixture(draft, inputs, graphProfile, reference, saved, tampered);
    }

    private static ReopenedModulePublication publication(
        CodeStructureGraphDraftReference reference,
        ArtifactControls controls,
        CodeStructureGraphDraft draft,
        ArtifactReference graphProfile,
        ArtifactId envelopeArtifactId) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      ObjectNode envelope = JsonNodeFactory.instance.objectNode();
      envelope.put("schemaVersion", CodeStructureGraphDraft.SCHEMA_VERSION);
      envelope.put("artifactType", "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT");
      ArtifactId descriptorArtifactId =
          ArtifactId.parse("program-graphs-code-structure-graph:" + "0".repeat(64));
      envelope.put(
          "artifactId",
          envelopeArtifactId == null ? descriptorArtifactId.value() : envelopeArtifactId.value());
      ObjectNode producer = envelope.putObject("producer");
      producer.put("moduleVersion", "v1");
      ObjectNode address = producer.putObject("address");
      address.put("kind", "ANALYSIS_STEP");
      address.put("runId", RUN.value());
      address.put("analysisStepKey", AnalysisStepKey.PROGRAM_GRAPHS.wireValue());
      address.put("moduleNumber", 1);
      address.put("moduleKey", "code-structure");
      byte[] sourceBytes = "class Empty {}\n".getBytes(StandardCharsets.UTF_8);
      CodeStructureSource source =
          new CodeStructureSource(
              SNAPSHOT,
              "COMPLETE_CAPTURE",
              true,
              ref("source-inventory", '9'),
              ref("verified-snapshot", 'a'),
              controls,
              List.of(
                  new CodeStructureSourceDocument(
                      ArtifactId.parse("file:" + "0".repeat(64)),
                      "src/main/java/Empty.java",
                      ImmutableBytes.copyOf(sourceBytes),
                      new Sha256Digest(sha256(sourceBytes)))));
      CodeStructureDiscovery discovery =
          new CodeStructureDiscovery(
              APP,
              ref("application-profile", 'b'),
              ref("capability-report", 'c'),
              ref("entry-points", 'd'),
              ref("mapper-catalog", 'e'),
              List.of());
      ProgramGraphInputBasis basis = ProgramGraphInputBasis.from(source, discovery, graphProfile);
      List<ArtifactReference> upstream =
          new java.util.ArrayList<>(
              List.of(
                  basis.sourceInventoryRef(),
                  basis.verifiedSnapshotRef(),
                  basis.applicationProfileRef(),
                  basis.capabilityReportRef(),
                  basis.entryPointsRef(),
                  basis.mapperCatalogRef(),
                  basis.graphProfileRef()));
      upstream.sort(Comparator.comparing(value -> value.artifactId().value()));
      references(envelope.putArray("upstreamArtifacts"), upstream);
      envelope.set("controls", controls(controls));
      ObjectNode completion = envelope.putObject("completion");
      completion.put("status", ModuleCompletionStatus.SUCCEEDED.name());
      completion.putArray("gapRefs");
      completion.putNull("failureRef");
      envelope.set("payload", draft(draft));
      ImmutableBytes bytes = json.encodeCanonical(envelope);
      ArtifactDescriptor descriptor =
          new ArtifactDescriptor(
              "code-structure-draft.json",
              "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT",
              CodeStructureGraphDraft.SCHEMA_VERSION,
              descriptorArtifactId,
              CanonicalMediaType.APPLICATION_JSON,
              bytes.size(),
              new Sha256Digest(sha256(bytes.copyToByteArray())));
      ModuleReceipt receipt =
          new ModuleReceipt(
              "module-receipt-v1",
              reference.publication().moduleReceiptId(),
              reference.publication().address(),
              "v1",
              upstream,
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(descriptor),
              reference.publication().moduleArtifactRoot(),
              List.of());
      return new ReopenedModulePublication(
          reference.publication(),
          receipt,
          List.of(new VerifiedCanonicalPayload(descriptor, bytes)));
    }

    private static ObjectNode draft(CodeStructureGraphDraft draft) {
      ObjectNode value = JsonNodeFactory.instance.objectNode();
      value.put("graphKind", draft.graphKind().name());
      value.put("graphId", draft.graphId().value());
      value.put("snapshotId", draft.snapshotId());
      value.put("applicationProfileId", draft.applicationProfileId().value());
      value.set("graphProfileRef", reference(draft.graphProfileRef()));
      value.putArray("entryIds");
      value.putArray("nodes");
      value.putArray("edges");
      value.putArray("gapDrafts");
      value.putArray("provenanceDrafts");
      ObjectNode coverage = value.putObject("coverage");
      coverage.putArray("candidateElementIds");
      coverage.putArray("exactElementIds");
      coverage.putArray("gapDispositions");
      coverage.putArray("exclusionDispositions");
      coverage.putArray("scopeGapIds");
      coverage.put("closed", true);
      return value;
    }

    private static ObjectNode controls(ArtifactControls values) {
      ObjectNode result = JsonNodeFactory.instance.objectNode();
      result.put("toolchainSha256", values.toolchainSha256().value());
      result.put("profileSha256", values.profileSha256().value());
      result.put("schemaBundleSha256", values.schemaBundleSha256().value());
      result.putNull("promptBundleSha256");
      result
          .putObject("artifactPolicyRegistryRef")
          .put("artifactId", values.artifactPolicyRegistryRef().artifactId().value())
          .put("sha256", values.artifactPolicyRegistryRef().sha256().value());
      return result;
    }

    private static void references(ArrayNode target, List<ArtifactReference> values) {
      values.forEach(value -> target.add(reference(value)));
    }

    private static ObjectNode reference(ArtifactReference value) {
      return JsonNodeFactory.instance
          .objectNode()
          .put("artifactId", value.artifactId().value())
          .put("sha256", value.sha256().value());
    }

    private static ArtifactReference ref(String prefix, char value) {
      String hex = String.valueOf(value).repeat(64);
      return new ArtifactReference(ArtifactId.parse(prefix + ":" + hex), new Sha256Digest(hex));
    }

    private static String sha256(byte[] bytes) {
      try {
        return java.util.HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      } catch (NoSuchAlgorithmException impossible) {
        throw new AssertionError(impossible);
      }
    }

    CanonicalModuleArtifactStore store() {
      return storeFor(saved);
    }

    CanonicalModuleArtifactStore tamperedStore() {
      return storeFor(tampered);
    }

    private CanonicalModuleArtifactStore storeFor(ReopenedModulePublication publication) {
      return new CanonicalModuleArtifactStore() {
        @Override
        public InstalledModulePublication install(ModuleInstallRequest request) {
          throw new AssertionError("historical graph reader fixture must not install");
        }

        @Override
        public CanonicalArtifactPolicy resolveArtifactPolicy(
            org.sourceanalysis.app.artifact.ArtifactPolicyKey key) {
          throw new AssertionError("historical graph reader fixture must not resolve policy");
        }

        @Override
        public ReopenedModulePublication reopen(ModulePublicationReference requested) {
          if (!publication.reference().equals(requested)) {
            throw new IllegalArgumentException("historical graph publication ownership mismatch");
          }
          return publication;
        }
      };
    }

    CodeStructureGraphDraft draft() {
      return draft;
    }

    ReopenedProgramGraphInputs inputs() {
      return inputs;
    }

    ArtifactReference graphProfile() {
      return graphProfile;
    }

    CodeStructureGraphDraftReference reference() {
      return reference;
    }
  }
}
