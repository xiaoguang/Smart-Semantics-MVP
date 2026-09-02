package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
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
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

class CallGraphModulePublisherTest {

  @TempDir Path temporaryDirectory;

  @Test
  void persistsTheCallGraphWithTheFreshReopenedStructureAsAnExactUpstreamArtifact() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    CodeStructureSource source = source(controls);
    CodeStructureDiscovery discovery = discovery();
    ArtifactReference graphProfile = reference("graph-profile", "call-graph");
    ReopenedProgramGraphInputs reopened =
        new ReopenedProgramGraphInputs(
            source, new ProgramGraphDiscoveryInputs(discovery, List.of(), List.of()));
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "1".repeat(64));

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      FileSystemCanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(2, 100_000, 200_000, 8));
      CodeStructureGraphDraft structureDraft =
          new CodeStructureGraphBuilder()
              .buildStructure(source, discovery, new CodeStructureGraphProfile(graphProfile));
      CodeStructureGraphDraftReference structureReference =
          new CodeStructureGraphModulePublisher(store)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 1, "code-structure"),
                  source,
                  discovery,
                  structureDraft);
      ReopenedCodeStructureGraph structure =
          new PersistedCodeStructureGraphReader(store)
              .reopen(structureReference, reopened, graphProfile);
      CallGraphDraft draft =
          new CallGraphDraft(
              CallGraphDraft.SCHEMA_VERSION,
              ProgramGraphKind.CALL,
              id("program-graph", "call"),
              source.snapshotId(),
              discovery.applicationProfileId(),
              graphProfile,
              discovery.entryIds(),
              List.of(),
              List.of(),
              List.of(),
              new GraphCoverage(List.of(), List.of(), List.of(), List.of(), List.of(), true));

      CallGraphDraftReference reference =
          new CallGraphModulePublisher(store)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 2, "call-graph"),
                  structure,
                  reopened,
                  draft);

      var reopenedPublication = store.reopen(reference.publication());
      assertThat(reopenedPublication.receipt().address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  runId, AnalysisStepKey.PROGRAM_GRAPHS, 2, "call-graph"));
      assertThat(reopenedPublication.receipt().status())
          .isEqualTo(ModuleCompletionStatus.SUCCEEDED);
      assertThat(reopenedPublication.receipt().controls()).isEqualTo(controls);
      assertThat(reopenedPublication.receipt().upstreamArtifacts())
          .containsExactlyInAnyOrder(
              source.sourceInventoryRef(),
              source.verifiedSnapshotRef(),
              discovery.applicationProfileRef(),
              discovery.capabilityReportRef(),
              discovery.entryPointsRef(),
              discovery.mapperCatalogRef(),
              graphProfile,
              structure.payloadRef());
      assertThat(reopenedPublication.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName()).isEqualTo("call-graph-draft.json");
                assertThat(payload.descriptor().artifactType())
                    .isEqualTo("PROGRAM_GRAPHS_CALL_GRAPH_DRAFT");
                assertThat(payload.descriptor().schemaVersion())
                    .isEqualTo(CallGraphDraft.SCHEMA_VERSION);
                JsonNode body =
                    canonicalJson.parseCanonical(payload.canonicalUtf8()).get("payload");
                assertThat(body.get("graphKind").textValue()).isEqualTo("CALL");
                assertThat(body.get("snapshotId").textValue()).isEqualTo(source.snapshotId());
              });
    }
  }

  @Test
  void executesCallsOnlyAfterFreshlyReopeningThePersistedStructureFromTheSameInputs() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    CodeStructureSource source = source(controls);
    CodeStructureDiscovery discovery = discovery();
    ArtifactReference graphProfile = reference("graph-profile", "call-graph-execution");
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "3".repeat(64));
    VerifiedSourceInventoryReference sourceReference =
        new VerifiedSourceInventoryReference(
            analysisStepReference(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, "source"));
    ApplicationDiscoveryReference discoveryReference =
        new ApplicationDiscoveryReference(
            analysisStepReference(runId, AnalysisStepKey.APPLICATION_DISCOVERY, "discovery"));
    AtomicInteger reopenCount = new AtomicInteger();

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      FileSystemCanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(2, 100_000, 200_000, 8));
      CodeStructureGraphDraft structureDraft =
          new CodeStructureGraphBuilder()
              .buildStructure(source, discovery, new CodeStructureGraphProfile(graphProfile));
      CodeStructureGraphDraftReference structureReference =
          new CodeStructureGraphModulePublisher(store)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 1, "code-structure"),
                  source,
                  discovery,
                  structureDraft);
      ProgramGraphInputReader inputs =
          (actualSource, actualDiscovery) -> {
            assertThat(actualSource).isEqualTo(sourceReference);
            assertThat(actualDiscovery).isEqualTo(discoveryReference);
            reopenCount.incrementAndGet();
            return new ReopenedProgramGraphInputs(
                source, new ProgramGraphDiscoveryInputs(discovery, List.of(), List.of()));
          };

      CallGraphDraftReference reference =
          new CallGraphExecution(
                  inputs,
                  new PersistedCodeStructureGraphReader(store),
                  new CallGraphBuilder(),
                  new CallGraphModulePublisher(store))
              .execute(
                  sourceReference,
                  discoveryReference,
                  structureReference,
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 2, "call-graph"),
                  new CallGraphProfile(graphProfile));

      assertThat(reopenCount.get()).isEqualTo(1);
      assertThat(store.reopen(reference.publication()).payloads())
          .singleElement()
          .satisfies(
              payload ->
                  assertThat(payload.descriptor().fileName()).isEqualTo("call-graph-draft.json"));
    }
  }

  @Test
  void freshReopensThePersistedCallGraphOnlyAgainstItsSameStructureAndInputs() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    CodeStructureSource source = source(controls);
    CodeStructureDiscovery discovery = discovery();
    ArtifactReference graphProfile = reference("graph-profile", "call-graph-reader");
    ReopenedProgramGraphInputs reopened =
        new ReopenedProgramGraphInputs(
            source, new ProgramGraphDiscoveryInputs(discovery, List.of(), List.of()));
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "4".repeat(64));

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
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
                  emptyCallGraph(source, discovery, graphProfile));

      ReopenedCallGraph callGraph =
          new PersistedCallGraphReader(store)
              .reopen(callReference, reopened, structure, graphProfile);

      assertThat(callGraph.reference()).isEqualTo(callReference);
      assertThat(callGraph.draft().graphKind()).isEqualTo(ProgramGraphKind.CALL);
      assertThat(callGraph.basis()).isEqualTo(structure.basis());
      assertThat(callGraph.codeStructurePayloadRef()).isEqualTo(structure.payloadRef());
    }
  }

  private static CallGraphDraft emptyCallGraph(
      CodeStructureSource source,
      CodeStructureDiscovery discovery,
      ArtifactReference graphProfile) {
    return new CallGraphDraft(
        CallGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.CALL,
        id("program-graph", "call"),
        source.snapshotId(),
        discovery.applicationProfileId(),
        graphProfile,
        discovery.entryIds(),
        List.of(),
        List.of(),
        List.of(),
        new GraphCoverage(List.of(), List.of(), List.of(), List.of(), List.of(), true));
  }

  private static CodeStructureSource source(ArtifactControls controls) {
    byte[] bytes = "package com.example; class DepotHead {}".getBytes(StandardCharsets.UTF_8);
    return new CodeStructureSource(
        "snapshot:" + "2".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        reference("source-inventory", "inventory"),
        reference("verified-snapshot", "snapshot"),
        controls,
        List.of(
            new CodeStructureSourceDocument(
                id("file", "source"),
                "src/main/java/com/example/DepotHead.java",
                ImmutableBytes.copyOf(bytes),
                new Sha256Digest(digest(bytes)))));
  }

  private static CodeStructureDiscovery discovery() {
    return new CodeStructureDiscovery(
        id("application-profile", "profile"),
        reference("application-profile", "profile"),
        reference("capability-report", "capability"),
        reference("entry-points", "entries"),
        reference("mapper-catalog", "mappers"),
        List.of(id("entry", "depot-head")));
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        new Sha256Digest("a".repeat(64)),
        new Sha256Digest("b".repeat(64)),
        new Sha256Digest("c".repeat(64)),
        null,
        policies.reference());
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(id(prefix, value), new Sha256Digest(digest(value)));
  }

  private static ArtifactId id(String prefix, String value) {
    return ArtifactId.parse(prefix + ":" + digest(value));
  }

  private static org.sourceanalysis.app.artifact.AnalysisStepPublicationReference
      analysisStepReference(AnalysisRunId runId, AnalysisStepKey key, String value) {
    return new org.sourceanalysis.app.artifact.AnalysisStepPublicationReference(
        new org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress(runId, key),
        org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot.parse(
            "analysis-step-root:" + digest(value + "-root")),
        org.sourceanalysis.app.artifact.AnalysisStepReceiptId.parse(
            "analysis-step-receipt:" + digest(value + "-receipt")),
        new Sha256Digest(digest(value + "-receipt-sha")));
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
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
