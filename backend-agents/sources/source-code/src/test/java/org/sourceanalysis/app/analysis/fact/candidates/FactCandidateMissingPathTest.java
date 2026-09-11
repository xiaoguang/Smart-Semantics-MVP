package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphKind;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** RED for M1's persisted relation/evidence closure and entry-local denominator. */
class FactCandidateMissingPathTest {

  @TempDir Path temporaryDirectory;

  @Test
  void persistedMissingArgumentEvidenceRejectsOnlyItsEntryBoundaryCombination() {
    Path baseRoot = temporaryDirectory.resolve("base-graphs");
    Path mutatedRoot = temporaryDirectory.resolve("mutated-graphs");
    try (ProgramGraphsPublicFixture base = ProgramGraphsPublicFixture.create(baseRoot);
        PersistedMutation mutation =
            PersistedMutation.republishWithoutApproveArgumentEvidence(base, mutatedRoot)) {
      FactCandidateInputs baselineInputs =
          new PersistedFactCandidateInputReader(base.stepArtifacts(), base.sourceReader())
              .reopen(base.sourceInventory(), base.applicationDiscovery(), base.programGraphs());
      FactCandidateSet baseline =
          new FactCandidateEnumerator()
              .enumerate(baselineInputs, FactRegistry.standardJavaBoundary());
      List<String> baselineExactKeys =
          baseline.candidates().stream()
              .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .toList();

      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(mutation.steps(), mutation.sourceReader())
              .reopen(mutation.source(), mutation.discovery(), mutation.graphs());

      FactCandidateSet result =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      List<FactCandidateSet.FactCandidate> boundaryCandidates =
          result.candidates().stream()
              .filter(candidate -> "JAVA_BOUNDARY_INVOCATION".equals(candidate.kind()))
              .toList();
      List<String> exactKeys =
          result.candidates().stream()
              .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .toList();

      assertThat(exactKeys).containsExactlyElementsOf(baselineExactKeys);
      assertThat(boundaryCandidates)
          .extracting(FactCandidateSet.FactCandidate::entryId)
          .containsExactly("entry:" + digest("cancel"));
      assertThat(result.candidates()).hasSize(baselineExactKeys.size() + 1);
      assertThat(result.notApplicableDispositions()).hasSize(1);
      FactCandidateSet.NotApplicableDisposition disposition =
          result.notApplicableDispositions().get(0);
      assertThat(disposition.entryId()).isEqualTo("entry:" + digest("approve"));
      assertThat(disposition.templateKey()).isEqualTo("JAVA_BOUNDARY_INVOCATION");
      assertThat(disposition.missingRoles()).anyMatch(value -> value.startsWith("EVIDENCE:"));
      assertThat(disposition.reasonCode()).isEqualTo("DATA_FLOW_BINDING_UNPROVEN");

      // The generic boundary candidate records only the Java invocation. It must not manufacture
      // a database/message/search effect after the argument evidence was removed.
      assertThat(boundaryCandidates)
          .allSatisfy(candidate -> assertThat(candidate.staticTargetType()).isNotBlank());
    }
  }

  /** Test-only public-store republisher; no private store fields or Fact raw JSON are used. */
  private static final class PersistedMutation implements AutoCloseable {

    private final RunStoreHandle handle;
    private final CanonicalAnalysisStepArtifactStore steps;
    private final VerifiedSourceInventoryReference source;
    private final ApplicationDiscoveryReference discovery;
    private final ProgramGraphsReference graphs;
    private final VerifiedSourceTextReader sourceReader;

    private PersistedMutation(
        RunStoreHandle handle,
        CanonicalAnalysisStepArtifactStore steps,
        VerifiedSourceInventoryReference source,
        ApplicationDiscoveryReference discovery,
        ProgramGraphsReference graphs,
        VerifiedSourceTextReader sourceReader) {
      this.handle = handle;
      this.steps = steps;
      this.source = source;
      this.discovery = discovery;
      this.graphs = graphs;
      this.sourceReader = sourceReader;
    }

    static PersistedMutation republishWithoutApproveArgumentEvidence(
        ProgramGraphsPublicFixture base, Path mutationRoot) {
      try {
        Files.createDirectory(mutationRoot);
        CanonicalJsonCodec json = new CanonicalJsonCodec();
        var policies = base.artifactPolicies();
        ArtifactControls controls = base.artifactControls();
        RunStoreHandle handle = RunStoreBootstrap.openForTest(mutationRoot);
        ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
        CanonicalModuleArtifactStore modules =
            new FileSystemCanonicalModuleArtifactStore(handle, json, policies, limits);
        CanonicalAnalysisStepArtifactStore steps =
            new FileSystemCanonicalAnalysisStepArtifactStore(handle, json, policies, limits);
        ReopenedAnalysisStepPublication baseSource =
            base.stepArtifacts().reopen(base.sourceInventory().publication());
        ReopenedAnalysisStepPublication baseDiscovery =
            base.stepArtifacts().reopen(base.applicationDiscovery().publication());
        ReopenedAnalysisStepPublication baseGraphs =
            base.stepArtifacts().reopen(base.programGraphs().publication());
        AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("missing-path-run"));
        InstalledAnalysisStepPublication sourceStep =
            copyStep(baseSource, runId, modules, steps, List.of(), controls, json);
        List<CanonicalModulePayload> discoveryPayloads =
            modulePayloads(baseDiscovery.semanticPayloads());
        CanonicalModulePayload capabilityPayload =
            find(discoveryPayloads, "capability-report.json");
        ObjectNode capabilityDocument =
            (ObjectNode) json.parseCanonical(capabilityPayload.canonicalUtf8());
        ObjectNode sourcePublication =
            (ObjectNode) capabilityDocument.get("verifiedSourceInventoryPublicationRef");
        if (sourcePublication == null) {
          throw new IllegalStateException("capability report has no source publication reference");
        }
        sourcePublication.put(
            "analysisStepKey", sourceStep.reference().address().analysisStepKey().wireValue());
        sourcePublication.put(
            "analysisStepArtifactRoot", sourceStep.reference().analysisStepArtifactRoot().value());
        sourcePublication.put(
            "analysisStepReceiptId", sourceStep.reference().analysisStepReceiptId().value());
        sourcePublication.put(
            "analysisStepReceiptSha256",
            sourceStep.reference().analysisStepReceiptSha256().value());
        CanonicalModulePayload reboundCapability =
            standalonePayload(capabilityPayload, capabilityDocument, json);
        List<CanonicalModulePayload> reboundDiscoveryPayloads =
            discoveryPayloads.stream()
                .map(
                    payload ->
                        "capability-report.json".equals(payload.fileName())
                            ? reboundCapability
                            : payload)
                .toList();
        InstalledAnalysisStepPublication discoveryStep =
            copyStep(
                baseDiscovery,
                runId,
                modules,
                steps,
                List.of(sourceStep.reference()),
                controls,
                json,
                reboundDiscoveryPayloads);

        List<CanonicalModulePayload> originalGraphPayloads =
            modulePayloads(baseGraphs.semanticPayloads());
        CanonicalModulePayload changedData =
            mutateDataFlow(
                find(originalGraphPayloads, "data-flow-graph.json"),
                json,
                "entry:" + digest("approve"));
        CanonicalModulePayload changedIndex =
            mutateIndex(find(originalGraphPayloads, "graph-index.json"), json, changedData);
        List<CanonicalModulePayload> changedGraphPayloads =
            originalGraphPayloads.stream()
                .map(
                    payload ->
                        switch (payload.fileName()) {
                          case "data-flow-graph.json" -> changedData;
                          case "graph-index.json" -> changedIndex;
                          default -> payload;
                        })
                .toList();
        InstalledModulePublication graphModule =
            modules.install(
                new ModuleInstallRequest(
                    new AnalysisStepModuleAddress(
                        runId, AnalysisStepKey.PROGRAM_GRAPHS, 6, "publish"),
                    "v1",
                    List.of(),
                    controls,
                    ModuleCompletionStatus.SUCCEEDED,
                    List.of(),
                    changedGraphPayloads));
        InstalledAnalysisStepPublication graphStep =
            steps.install(
                new AnalysisStepInstallRequest(
                    new AnalysisStepPublicationAddress(runId, AnalysisStepKey.PROGRAM_GRAPHS),
                    new AnalysisStepPublisherModuleProvenance(graphModule.reference()),
                    List.of(sourceStep.reference(), discoveryStep.reference()),
                    controls,
                    ModuleCompletionStatus.SUCCEEDED,
                    List.of(),
                    changedGraphPayloads.stream().map(PersistedMutation::stepPayload).toList(),
                    null));
        return new PersistedMutation(
            handle,
            steps,
            new VerifiedSourceInventoryReference(sourceStep.reference()),
            new ApplicationDiscoveryReference(discoveryStep.reference()),
            new ProgramGraphsReference(graphStep.reference()),
            base.sourceReader());
      } catch (java.io.IOException failure) {
        throw new IllegalStateException("cannot create persisted graph mutation fixture", failure);
      }
    }

    CanonicalAnalysisStepArtifactStore steps() {
      return steps;
    }

    VerifiedSourceInventoryReference source() {
      return source;
    }

    ApplicationDiscoveryReference discovery() {
      return discovery;
    }

    ProgramGraphsReference graphs() {
      return graphs;
    }

    VerifiedSourceTextReader sourceReader() {
      return sourceReader;
    }

    @Override
    public void close() {
      handle.close();
    }

    private static InstalledAnalysisStepPublication copyStep(
        ReopenedAnalysisStepPublication base,
        AnalysisRunId runId,
        CanonicalModuleArtifactStore modules,
        CanonicalAnalysisStepArtifactStore steps,
        List<org.sourceanalysis.app.artifact.AnalysisStepPublicationReference> upstream,
        ArtifactControls controls,
        CanonicalJsonCodec json) {
      return copyStep(
          base,
          runId,
          modules,
          steps,
          upstream,
          controls,
          json,
          modulePayloads(base.semanticPayloads()));
    }

    private static InstalledAnalysisStepPublication copyStep(
        ReopenedAnalysisStepPublication base,
        AnalysisRunId runId,
        CanonicalModuleArtifactStore modules,
        CanonicalAnalysisStepArtifactStore steps,
        List<org.sourceanalysis.app.artifact.AnalysisStepPublicationReference> upstream,
        ArtifactControls controls,
        CanonicalJsonCodec json,
        List<CanonicalModulePayload> payloads) {
      AnalysisStepKey key = base.reference().address().analysisStepKey();
      int moduleNumber = key == AnalysisStepKey.VERIFIED_SOURCE_INVENTORY ? 3 : 4;
      InstalledModulePublication module =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(runId, key, moduleNumber, "publish"),
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  payloads));
      return steps.install(
          new AnalysisStepInstallRequest(
              new AnalysisStepPublicationAddress(runId, key),
              new AnalysisStepPublisherModuleProvenance(module.reference()),
              upstream,
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(),
              payloads.stream().map(PersistedMutation::stepPayload).toList(),
              null));
    }

    private static CanonicalModulePayload mutateDataFlow(
        CanonicalModulePayload original, CanonicalJsonCodec json, String owningEntryId) {
      ObjectNode document = (ObjectNode) json.parseCanonical(original.canonicalUtf8());
      String boundaryId =
          stream(document.path("nodes"))
              .filter(node -> "JAVA_BOUNDARY_INVOCATION".equals(node.path("kind").asText()))
              .filter(node -> hasText(node.path("owningEntryIds"), owningEntryId))
              .map(node -> node.path("nodeId").asText())
              .findFirst()
              .orElseThrow();
      String argumentId =
          stream(document.path("nodes"))
              .filter(node -> boundaryId.equals(node.path("nodeId").asText()))
              .flatMap(node -> stream(node.path("boundaryInvocation").path("orderedArguments")))
              .map(node -> node.path("argumentNodeId").asText())
              .findFirst()
              .orElseThrow();
      ObjectNode edge =
          stream(document.path("edges"))
              .filter(node -> "ARGUMENT_TO_BOUNDARY".equals(node.path("kind").asText()))
              .filter(node -> argumentId.equals(node.path("fromNodeId").asText()))
              .filter(node -> boundaryId.equals(node.path("toNodeId").asText()))
              .map(node -> (ObjectNode) node)
              .findFirst()
              .orElseThrow();
      edge.putArray("evidenceNodeIds");
      return standalonePayload(original, document, json);
    }

    private static CanonicalModulePayload mutateIndex(
        CanonicalModulePayload original,
        CanonicalJsonCodec json,
        CanonicalModulePayload changedData) {
      ObjectNode document = (ObjectNode) json.parseCanonical(original.canonicalUtf8());
      ObjectNode descriptor =
          stream(document.path("graphs"))
              .filter(
                  node -> ProgramGraphKind.DATA_FLOW.name().equals(node.path("graphKind").asText()))
              .map(node -> (ObjectNode) node)
              .findFirst()
              .orElseThrow();
      ObjectNode reference = (ObjectNode) descriptor.get("artifactRef");
      reference.put("artifactId", changedData.artifactId().value());
      reference.put("sha256", digest(changedData.canonicalUtf8().copyToByteArray()));
      return standalonePayload(original, document, json);
    }

    private static CanonicalModulePayload standalonePayload(
        CanonicalModulePayload original, ObjectNode document, CanonicalJsonCodec json) {
      String prefix =
          original
              .artifactId()
              .value()
              .substring(0, original.artifactId().value().lastIndexOf(':'));
      ObjectNode withoutId = document.deepCopy();
      withoutId.remove("artifactId");
      String id =
          prefix
              + ":"
              + digest(
                  concat(
                      frame("canonical-standalone-json-artifact-id-v1"),
                      frame(original.schemaVersion()),
                      frame(original.artifactType()),
                      frame(json.encodeCanonical(withoutId).copyToByteArray())));
      document.put("artifactId", id);
      return new CanonicalModulePayload(
          original.fileName(),
          original.artifactType(),
          original.schemaVersion(),
          ArtifactId.parse(id),
          original.mediaType(),
          json.encodeCanonical(document));
    }

    private static List<CanonicalModulePayload> modulePayloads(
        List<VerifiedCanonicalPayload> payloads) {
      return payloads.stream()
          .map(
              payload ->
                  new CanonicalModulePayload(
                      payload.descriptor().fileName(),
                      payload.descriptor().artifactType(),
                      payload.descriptor().schemaVersion(),
                      payload.descriptor().artifactId(),
                      payload.descriptor().mediaType(),
                      payload.canonicalUtf8()))
          .toList();
    }

    private static CanonicalAnalysisStepPayload stepPayload(CanonicalModulePayload payload) {
      return new CanonicalAnalysisStepPayload(
          payload.fileName(),
          payload.artifactType(),
          payload.schemaVersion(),
          payload.artifactId(),
          payload.mediaType(),
          payload.canonicalUtf8());
    }

    private static CanonicalModulePayload find(
        List<CanonicalModulePayload> payloads, String fileName) {
      return payloads.stream()
          .filter(value -> fileName.equals(value.fileName()))
          .findFirst()
          .orElseThrow();
    }

    private static boolean hasText(JsonNode values, String expected) {
      if (values == null || !values.isArray()) return false;
      for (JsonNode value : values) if (expected.equals(value.asText())) return true;
      return false;
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode values) {
      if (values == null || !values.isArray()) return java.util.stream.Stream.empty();
      List<JsonNode> result = new ArrayList<>();
      values.forEach(result::add);
      return result.stream();
    }
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
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

  private static byte[] concat(byte[]... values) {
    int length = 0;
    for (byte[] value : values) length += value.length;
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }
}
