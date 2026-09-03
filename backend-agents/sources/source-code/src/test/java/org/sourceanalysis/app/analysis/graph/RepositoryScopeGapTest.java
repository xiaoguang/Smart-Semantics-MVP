package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** RED coverage for the distinction between source-local and repository-scope Gaps. */
class RepositoryScopeGapTest {

  @TempDir Path temporaryDirectory;

  @Test
  void allowsMalformedCodeStructureGapWithoutInventingAnEntryOwner() throws Exception {
    Path fixture =
        Path.of(
            "src/test/resources/analysis/graph/code-structure/src/main/java/com/example/MalformedController.java");
    byte[] bytes = Files.readAllBytes(fixture);
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("scope-local-gap-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "scope-local-gap-inventory"),
            reference("verified-snapshot", "scope-local-gap-snapshot"),
            controls("scope-local-gap-policy"),
            List.of(
                new CodeStructureSourceDocument(
                    id("file", fixture.toString()),
                    "src/main/java/com/example/MalformedController.java",
                    ImmutableBytes.copyOf(bytes),
                    new Sha256Digest(digest(bytes)))));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", "scope-local-gap-profile"),
            reference("application-profile", "scope-local-gap-profile"),
            reference("capability-report", "scope-local-gap-capability"),
            reference("entry-points", "scope-local-gap-entries"),
            reference("mapper-catalog", "scope-local-gap-mappers"),
            List.of());

    CodeStructureGraphDraft draft =
        new CodeStructureGraphBuilder()
            .buildStructure(
                source,
                discovery,
                new CodeStructureGraphProfile(reference("graph-profile", "scope-local-gap")));

    assertThat(draft.gapDrafts()).singleElement();
    GraphGapDraft gap = draft.gapDrafts().get(0);
    assertThat(gap.affectedEntryIds()).isEmpty();
    assertThat(gap.candidateElementIds()).isNotEmpty();
    assertThat(gap.sourceLocator().path())
        .isEqualTo("src/main/java/com/example/MalformedController.java");
    assertThat(draft.coverage().gapDispositions()).singleElement();
    assertThat(draft.coverage().gapDispositions().get(0).gapId()).isEqualTo(gap.gapId());
    assertThat(draft.coverage().closed()).isTrue();
  }

  @Test
  void rejectsEmptyEntryOwnerForCallControlAndDataFlowGaps() throws Exception {
    Path fixture =
        Path.of(
            "src/test/resources/analysis/graph/code-structure/src/main/java/com/example/MalformedController.java");
    byte[] bytes = Files.readAllBytes(fixture);
    CodeStructureSource source =
        new CodeStructureSource(
            "snapshot:" + digest("non-structure-owner-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("source-inventory", "non-structure-owner-inventory"),
            reference("verified-snapshot", "non-structure-owner-snapshot"),
            controls("non-structure-owner-policy"),
            List.of(
                new CodeStructureSourceDocument(
                    id("file", fixture.toString()),
                    "src/main/java/com/example/MalformedController.java",
                    ImmutableBytes.copyOf(bytes),
                    new Sha256Digest(digest(bytes)))));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            id("application-profile", "non-structure-owner-profile"),
            reference("application-profile", "non-structure-owner-profile"),
            reference("capability-report", "non-structure-owner-capability"),
            reference("entry-points", "non-structure-owner-entries"),
            reference("mapper-catalog", "non-structure-owner-mappers"),
            List.of(id("entry", "real-owner")));
    CodeStructureGraphDraft structure =
        new CodeStructureGraphBuilder()
            .buildStructure(
                source,
                discovery,
                new CodeStructureGraphProfile(reference("graph-profile", "non-structure-owner")));
    GraphGapDraft realGap = structure.gapDrafts().get(0);

    for (ProgramGraphKind graphKind :
        List.of(ProgramGraphKind.CALL, ProgramGraphKind.CONTROL_FLOW, ProgramGraphKind.DATA_FLOW)) {
      assertThatThrownBy(
              () ->
                  GraphGapDraft.forLocalOccurrence(
                      graphKind,
                      realGap.reasonCode(),
                      List.of(),
                      realGap.candidateElementIds(),
                      realGap.sourceLocator()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("entry");
    }
  }

  @Test
  void keepsBoundedScopeGapInIndexAndReceiptsWithoutCreatingGraphGapRow() throws Exception {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.create(temporaryDirectory)) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = graphPolicies(json);
      ArtifactControls controls = graphControls(policies);
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              fixture.handle(), json, policies, new ArtifactStoreLimits(8, 100_000, 300_000, 12));
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              fixture.handle(), json, policies, new ArtifactStoreLimits(8, 100_000, 300_000, 10));
      AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("bounded-scope-run"));

      List<CanonicalModulePayload> sourcePayloads = invokePayloads("sourcePayloads", json);
      InstalledModulePublication sourceModule =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 3, "publish"),
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  sourcePayloads));
      var sourceStep =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
                  new AnalysisStepPublisherModuleProvenance(sourceModule.reference()),
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  toStepPayloads(sourcePayloads),
                  null));

      List<CanonicalModulePayload> discoveryPayloads = invokePayloads("discoveryPayloads", json);
      InstalledModulePublication discoveryModule =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.APPLICATION_DISCOVERY, 4, "publish"),
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  discoveryPayloads));
      var discoveryStep =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                  new AnalysisStepPublisherModuleProvenance(discoveryModule.reference()),
                  List.of(sourceStep.reference()),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  toStepPayloads(discoveryPayloads),
                  null));

      CodeStructureSource originalSource = fixture.reopenedInputs().source();
      CodeStructureSource boundedSource =
          new CodeStructureSource(
              originalSource.snapshotId(),
              "BOUNDED_PATH_SET",
              false,
              publishedArtifact(sourceModule, "source-inventory.jsonl"),
              publishedArtifact(sourceModule, "verified-snapshot.json"),
              controls,
              originalSource.documents());
      CodeStructureDiscovery originalDiscovery =
          fixture.reopenedInputs().discovery().codeStructureDiscovery();
      CodeStructureDiscovery boundedDiscovery =
          new CodeStructureDiscovery(
              originalDiscovery.applicationProfileId(),
              publishedArtifact(discoveryModule, "application-profile.json"),
              publishedArtifact(discoveryModule, "capability-report.json"),
              publishedArtifact(discoveryModule, "entry-points.jsonl"),
              publishedArtifact(discoveryModule, "mapper-catalog.jsonl"),
              originalDiscovery.entryIds());
      ReopenedProgramGraphInputs boundedInputs =
          new ReopenedProgramGraphInputs(
              boundedSource,
              new ProgramGraphDiscoveryInputs(
                  boundedDiscovery,
                  fixture.reopenedInputs().discovery().entries(),
                  fixture.reopenedInputs().discovery().mapperCatalog()));
      ArtifactReference profile = fixture.graphProfileRef();

      CodeStructureGraphDraftReference structureReference =
          new CodeStructureGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 1, "code-structure"),
                  boundedSource,
                  boundedDiscovery,
                  new CodeStructureGraphBuilder()
                      .buildStructure(
                          boundedSource, boundedDiscovery, new CodeStructureGraphProfile(profile)));
      ReopenedCodeStructureGraph structure =
          new PersistedCodeStructureGraphReader(modules)
              .reopen(structureReference, boundedInputs, profile);
      CallGraphDraftReference callReference =
          new CallGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 2, "call-graph"),
                  structure,
                  boundedInputs,
                  new CallGraphBuilder()
                      .buildCalls(
                          new CallGraphInputs(structure, boundedInputs),
                          new CallGraphProfile(profile)));
      ReopenedCallGraph calls =
          new PersistedCallGraphReader(modules)
              .reopen(callReference, boundedInputs, structure, profile);
      ControlFlowGraphDraft controlDraft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(structure, calls, boundedInputs),
                  new ControlFlowGraphProfile(profile));
      ControlFlowGraphDraftReference controlReference =
          new ControlFlowGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 3, "control-flow"),
                  structure,
                  calls,
                  boundedInputs,
                  controlDraft);
      ReopenedControlFlowGraph control =
          new PersistedControlFlowGraphReader(modules)
              .reopen(controlReference, boundedInputs, structure, calls, profile);
      DataFlowGraphDraft dataDraft =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(structure, calls, control, boundedInputs),
                  new DataFlowGraphProfile(profile));
      DataFlowGraphDraftReference dataReference =
          new DataFlowGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 4, "data-flow"),
                  structure,
                  calls,
                  control,
                  boundedInputs,
                  dataDraft);
      ReopenedDataFlowGraph data =
          new PersistedDataFlowGraphReader(modules)
              .reopen(dataReference, boundedInputs, structure, calls, control, profile);
      EvidenceGraphDraft evidenceDraft =
          new EvidenceGraphBuilder()
              .buildEvidence(
                  List.of(structure.draft(), calls.draft(), control.draft(), data.draft()),
                  boundedSource);
      EvidenceGraphDraftReference evidenceReference =
          new EvidenceGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 5, "evidence-graph"),
                  structure,
                  calls,
                  control,
                  data,
                  boundedInputs,
                  evidenceDraft);
      ReopenedEvidenceGraph evidence =
          new PersistedEvidenceGraphReader(modules)
              .reopen(evidenceReference, boundedInputs, structure, calls, control, data, profile);

      ProgramGraphsReference publication =
          new ProgramGraphSetPublicationSpecifier(modules, steps)
              .specifyGraphSet(
                  new ProgramGraphsPublicationInputs(
                      sourceStep.reference(),
                      discoveryStep.reference(),
                      structure,
                      calls,
                      control,
                      data,
                      evidence),
                  controls);
      var reopened = steps.reopen(publication.publication());
      ArtifactId scopeGapId = structure.draft().coverage().scopeGapIds().get(0);
      byte[] graphGapBytes =
          reopened.semanticPayloads().stream()
              .filter(value -> value.descriptor().fileName().equals("graph-gaps.jsonl"))
              .findFirst()
              .orElseThrow()
              .canonicalUtf8()
              .copyToByteArray();
      assertThat(graphGapBytes).as("scope-only run must not create a graph gap row").isEmpty();
      JsonNode index =
          reopened.semanticPayloads().stream()
              .filter(value -> value.descriptor().fileName().equals("graph-index.json"))
              .findFirst()
              .map(value -> json.parseCanonical(value.canonicalUtf8()))
              .orElseThrow();
      assertThat(index.get("gapIds")).hasSize(1);
      assertThat(index.get("gapIds").get(0).textValue()).isEqualTo(scopeGapId.value());
      assertThat(index.get("status").textValue()).isEqualTo("SUCCEEDED_WITH_GAPS");
      assertThat(index.get("closed").booleanValue()).isTrue();
      assertThat(reopened.receipt().gapRefs()).containsExactly(scopeGapId.value());
    }
  }

  @SuppressWarnings("unchecked")
  private static List<CanonicalModulePayload> invokePayloads(
      String method, CanonicalJsonCodec json) {
    try {
      Method target =
          ProgramGraphGapProjectionTest.class.getDeclaredMethod(method, CanonicalJsonCodec.class);
      target.setAccessible(true);
      return (List<CanonicalModulePayload>) target.invoke(null, json);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
      throw new AssertionError(
          "existing frozen publication fixture is unavailable: " + method, failure);
    }
  }

  private static CanonicalArtifactPolicyRegistry graphPolicies(CanonicalJsonCodec json) {
    try {
      Method target =
          ProgramGraphGapProjectionTest.class.getDeclaredMethod(
              "policies", CanonicalJsonCodec.class);
      target.setAccessible(true);
      return (CanonicalArtifactPolicyRegistry) target.invoke(null, json);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
      throw new AssertionError("existing graph policy fixture is unavailable", failure);
    }
  }

  private static ArtifactControls graphControls(CanonicalArtifactPolicyRegistry policies) {
    try {
      Method target =
          ProgramGraphGapProjectionTest.class.getDeclaredMethod(
              "controls", CanonicalArtifactPolicyRegistry.class);
      target.setAccessible(true);
      return (ArtifactControls) target.invoke(null, policies);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
      throw new AssertionError("existing graph controls fixture is unavailable", failure);
    }
  }

  private static List<CanonicalAnalysisStepPayload> toStepPayloads(
      List<CanonicalModulePayload> payloads) {
    return payloads.stream()
        .map(
            payload ->
                new CanonicalAnalysisStepPayload(
                    payload.fileName(),
                    payload.artifactType(),
                    payload.schemaVersion(),
                    payload.artifactId(),
                    payload.mediaType(),
                    payload.canonicalUtf8()))
        .toList();
  }

  private static ArtifactReference publishedArtifact(
      InstalledModulePublication publication, String fileName) {
    return publication.artifactDescriptors().stream()
        .filter(value -> value.fileName().equals(fileName))
        .findFirst()
        .map(value -> new ArtifactReference(value.artifactId(), value.sha256()))
        .orElseThrow();
  }

  private static ArtifactControls controls(String value) {
    return new ArtifactControls(
        new Sha256Digest(digest("toolchain")),
        new Sha256Digest(digest("profile")),
        new Sha256Digest(digest("schema")),
        null,
        new org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference(
            id("artifact-policy-registry", value), new Sha256Digest(digest(value))));
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
