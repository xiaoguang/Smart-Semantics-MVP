package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * RED for the M1 module publication seam.
 *
 * <p>M1 is not complete when enumeration only returns an in-memory record. The complete candidate
 * set must be installed as a canonical module artifact and must be independently reopenable. This
 * test intentionally discovers the expected public publisher seam reflectively so it compiles
 * before the production publisher exists; the first failure is therefore the missing seam rather
 * than a hand-built JSON or mocked store.
 */
class FactCandidateModuleArtifactTest {

  private static final String PUBLISHER_CLASS =
      "org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher";
  private static final String ARTIFACT_TYPE = "PROVEN_CODE_FACTS_FACT_CANDIDATE_SET";
  private static final String SCHEMA_VERSION = "proven-code-facts-fact-candidate-set-v3";

  @TempDir Path temporaryDirectory;

  @Test
  void publishesCompleteCandidateSetAndFreshReopensTheSameIdentity() throws Exception {
    Files.createDirectory(temporaryDirectory.resolve("candidate-publication"));
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("graph-input"));
        RunStoreHandle handle =
            RunStoreBootstrap.openForTest(temporaryDirectory.resolve("candidate-publication"))) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = fixture.artifactPolicies();
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, json, policies, new ArtifactStoreLimits(8, 2_000_000, 4_000_000, 16));

      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidateSet =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());

      AnalysisStepModuleAddress destination =
          new AnalysisStepModuleAddress(
              fixture.programGraphs().publication().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              1,
              "candidates");

      ModulePublicationReference reference =
          invokePublisher(store, destination, inputs, candidateSet);
      ReopenedModulePublication reopened = store.reopen(reference);

      assertThat(reopened.reference()).isEqualTo(reference);
      assertThat(reopened.payloads()).hasSize(1);
      VerifiedCanonicalPayload payload = reopened.payloads().get(0);
      assertThat(payload.descriptor().fileName()).isEqualTo("fact-candidate-set.json");
      assertThat(payload.descriptor().artifactType()).isEqualTo(ARTIFACT_TYPE);
      assertThat(payload.descriptor().schemaVersion()).isEqualTo(SCHEMA_VERSION);

      JsonNode envelope = json.parseCanonical(payload.canonicalUtf8());
      assertThat(envelope.path("artifactType").asText()).isEqualTo(ARTIFACT_TYPE);
      assertThat(envelope.path("schemaVersion").asText()).isEqualTo(SCHEMA_VERSION);
      assertThat(envelope.path("upstreamArtifacts").isArray()).isTrue();
      assertThat(referenceValues(envelope.path("upstreamArtifacts")))
          .containsExactlyElementsOf(referenceValues(inputs.candidateModuleUpstreamArtifacts()));

      JsonNode body = envelope.path("payload");
      assertThat(body.isObject()).isTrue();
      assertThat(body.path("candidateSetId").asText())
          .isEqualTo(candidateSet.candidateSetId().value());
      assertThat(body.path("sourceGraphRoots").size()).isEqualTo(5);
      assertThat(body.path("candidates").size()).isEqualTo(candidateSet.candidates().size());
      assertThat(body.path("notApplicableDispositions").size())
          .isEqualTo(candidateSet.notApplicableDispositions().size());
      assertThat(body.path("denominator").isObject()).isTrue();

      ReopenedModulePublication reopenedAgain = store.reopen(reference);
      assertThat(reopenedAgain.reference()).isEqualTo(reopened.reference());
      assertThat(reopenedAgain.payloads().get(0).canonicalUtf8())
          .isEqualTo(reopened.payloads().get(0).canonicalUtf8());
      assertThat(
              new String(
                  reopenedAgain.payloads().get(0).canonicalUtf8().copyToByteArray(),
                  StandardCharsets.UTF_8))
          .contains(candidateSet.candidateSetId().value());
    }
  }

  @Test
  void publishesExactCallUnionAndFreshTypedReopensTheSameV3Bytes() throws Exception {
    Files.createDirectory(temporaryDirectory.resolve("exact-call-publication"));
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.createWithSharedJavaCall(
                temporaryDirectory.resolve("exact-call-graphs"));
        RunStoreHandle handle =
            RunStoreBootstrap.openForTest(temporaryDirectory.resolve("exact-call-publication"))) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = fixture.artifactPolicies();
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, json, policies, new ArtifactStoreLimits(8, 2_000_000, 4_000_000, 16));
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      List<ExactRow> expectedRows = expectedExactRows(inputs);
      assertThat(expectedRows).hasSize(3);
      assertThat(expectedRows)
          .anySatisfy(
              row ->
                  assertThat(row.targetCanonicalMethod())
                      .isEqualTo("com.example.OrderService#dispatch(java.lang.String)"));
      assertIndependentEvidencePremises(inputs, expectedRows);

      FactRegistry registry = FactRegistry.standardJavaBoundary();
      FactCandidateSet candidateSet = new FactCandidateEnumerator().enumerate(inputs, registry);
      assertThat(candidateSet.candidates())
          .filteredOn(candidate -> "JAVA_EXACT_CALL".equals(candidate.candidateFactKey()))
          .hasSize(3);

      AnalysisStepModuleAddress destination =
          new AnalysisStepModuleAddress(
              fixture.programGraphs().publication().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              1,
              "candidates");
      ModulePublicationReference reference =
          invokePublisher(store, destination, inputs, candidateSet);
      ReopenedModulePublication reopened = store.reopen(reference);
      VerifiedCanonicalPayload payload = reopened.payloads().get(0);
      assertThat(payload.descriptor().schemaVersion()).isEqualTo(SCHEMA_VERSION);

      JsonNode envelope = json.parseCanonical(payload.canonicalUtf8());
      assertThat(envelope.path("schemaVersion").asText()).isEqualTo(SCHEMA_VERSION);
      JsonNode body = envelope.path("payload");
      assertThat(body.isObject()).isTrue();
      Map<String, ExactRow> rowsByDenominator =
          expectedRows.stream()
              .collect(Collectors.toMap(ExactRow::denominatorKey, Function.identity()));
      List<String> exactDenominatorKeys =
          strings(body.path("denominator").path("applicableKeys")).stream()
              .filter(value -> value.endsWith("|JAVA_EXACT_CALL"))
              .toList();
      assertThat(exactDenominatorKeys)
          .containsExactlyElementsOf(expectedRows.stream().map(ExactRow::denominatorKey).toList());

      List<JsonNode> exactCandidates = new ArrayList<>();
      body.path("candidates")
          .forEach(
              candidate -> {
                if ("JAVA_EXACT_CALL".equals(candidate.path("candidateFactKey").asText())) {
                  exactCandidates.add(candidate);
                }
              });
      assertThat(exactCandidates).hasSize(3);
      for (JsonNode candidate : exactCandidates) {
        assertThat(candidate.isObject()).isTrue();
        assertThat(fieldNames(candidate))
            .containsExactlyInAnyOrder(
                "candidateFactKey",
                "entryId",
                "kind",
                "callSiteNodeId",
                "callTargetEdgeId",
                "targetMethodNodeId",
                "targetCanonicalMethod",
                "evidenceNodeIdsBySubject",
                "requiredAtoms");
        assertThat(candidate.path("kind").asText()).isEqualTo("JAVA_EXACT_CALL");
        assertThat(candidate.path("candidateFactKey").asText()).isEqualTo("JAVA_EXACT_CALL");
        ExactRow row =
            rowsByDenominator.get(
                candidate.path("entryId").asText()
                    + "|"
                    + candidate.path("callTargetEdgeId").asText()
                    + "|JAVA_EXACT_CALL");
        assertThat(row).isNotNull();
        assertThat(candidate.path("callSiteNodeId").asText()).isEqualTo(row.callSiteNodeId());
        assertThat(candidate.path("targetMethodNodeId").asText())
            .isEqualTo(row.targetMethodNodeId());
        assertThat(candidate.path("targetCanonicalMethod").asText())
            .isEqualTo(row.targetCanonicalMethod());

        JsonNode evidence = candidate.path("evidenceNodeIdsBySubject");
        assertThat(evidence.isArray()).isTrue();
        assertThat(evidence.size()).isEqualTo(3);
        List<String> evidenceSubjects = new ArrayList<>();
        evidence.forEach(
            binding -> {
              assertThat(fieldNames(binding))
                  .containsExactlyInAnyOrder(
                      "subjectElementId",
                      "sourceEvidenceNodeIds",
                      "ruleApplicationEvidenceNodeIds");
              evidenceSubjects.add(binding.path("subjectElementId").asText());
            });
        assertThat(evidenceSubjects)
            .containsExactlyInAnyOrder(
                row.callSiteNodeId(), row.callTargetEdgeId(), row.targetMethodNodeId());

        JsonNode atoms = candidate.path("requiredAtoms");
        assertThat(atoms.isArray()).isTrue();
        assertThat(atoms.size()).isEqualTo(4);
        Map<String, JsonNode> atomsByKey = new HashMap<>();
        atoms.forEach(atom -> atomsByKey.put(atom.path("atomKey").asText(), atom));
        assertThat(atomsByKey.keySet())
            .containsExactlyInAnyOrder(
                "INVOCATION_CALL_ID",
                "STATIC_TARGET_TYPE",
                "STATIC_TARGET_METHOD",
                "STATIC_TARGET_SIGNATURE");
        assertAtom(atomsByKey, "INVOCATION_CALL_ID", "RELATIONSHIP", "SYMBOL_REF");
        assertAtom(atomsByKey, "STATIC_TARGET_TYPE", "ATTRIBUTE", "STRING");
        assertAtom(atomsByKey, "STATIC_TARGET_METHOD", "ATTRIBUTE", "STRING");
        assertAtom(atomsByKey, "STATIC_TARGET_SIGNATURE", "ATTRIBUTE", "STRING");
      }

      FactCandidateSet reopenedTyped =
          new PersistedFactCandidateSetReader(store).reopen(reference, inputs, registry);
      assertThat(reopenedTyped).isEqualTo(candidateSet);
      assertThat(reopenedTyped.candidateSetId()).isEqualTo(candidateSet.candidateSetId());
      ReopenedModulePublication reopenedAgain = store.reopen(reference);
      assertThat(reopenedAgain.payloads().get(0).canonicalUtf8())
          .isEqualTo(reopened.payloads().get(0).canonicalUtf8());
    }
  }

  private static List<ExactRow> expectedExactRows(FactCandidateInputs inputs) {
    FactCandidateInputs.PublicProgramGraph codeStructure =
        inputs.graph(org.sourceanalysis.app.analysis.graph.ProgramGraphKind.CODE_STRUCTURE);
    FactCandidateInputs.PublicProgramGraph calls =
        inputs.graph(org.sourceanalysis.app.analysis.graph.ProgramGraphKind.CALL);
    List<ExactRow> result = new ArrayList<>();
    calls.edgesById().values().stream()
        .filter(edge -> "CALL_TARGET".equals(edge.kind()))
        .filter(edge -> "EXACT".equals(edge.resolution()))
        .filter(edge -> "java-static-field-receiver-call-v1".equals(edge.ruleId()))
        .forEach(
            edge -> {
              FactCandidateInputs.PublicProgramNode callSite =
                  calls.nodesById().get(edge.fromNodeId());
              FactCandidateInputs.PublicProgramNode target =
                  codeStructure.nodesById().get(edge.toNodeId());
              if (callSite == null
                  || !"CALL_SITE".equals(callSite.kind())
                  || target == null
                  || !"METHOD".equals(target.kind())) {
                return;
              }
              callSite.owningEntryIds().stream()
                  .filter(inputs.entryIds()::contains)
                  .forEach(
                      entryId ->
                          result.add(
                              new ExactRow(
                                  entryId,
                                  callSite.nodeId(),
                                  edge.edgeId(),
                                  target.nodeId(),
                                  target.canonicalValue())));
            });
    return result.stream().sorted(Comparator.comparing(ExactRow::denominatorKey)).toList();
  }

  private static void assertIndependentEvidencePremises(
      FactCandidateInputs inputs, List<ExactRow> rows) {
    FactCandidateInputs.PublicProgramGraph codeStructure =
        inputs.graph(org.sourceanalysis.app.analysis.graph.ProgramGraphKind.CODE_STRUCTURE);
    FactCandidateInputs.PublicProgramGraph calls =
        inputs.graph(org.sourceanalysis.app.analysis.graph.ProgramGraphKind.CALL);
    for (ExactRow row : rows) {
      FactCandidateInputs.PublicProgramNode callSite = calls.nodesById().get(row.callSiteNodeId());
      FactCandidateInputs.PublicProgramEdge edge = calls.edgesById().get(row.callTargetEdgeId());
      FactCandidateInputs.PublicProgramNode target =
          codeStructure.nodesById().get(row.targetMethodNodeId());
      assertThat(callSite).isNotNull();
      assertThat(edge).isNotNull();
      assertThat(target).isNotNull();
      assertThat(callSite.kind()).isEqualTo("CALL_SITE");
      assertThat(edge.kind()).isEqualTo("CALL_TARGET");
      assertThat(edge.resolution()).isEqualTo("EXACT");
      assertThat(edge.ruleId()).isEqualTo("java-static-field-receiver-call-v1");
      assertThat(target.kind()).isEqualTo("METHOD");
      assertThat(
              inputs
                  .evidenceGraph()
                  .closureFor(
                      org.sourceanalysis.app.analysis.graph.ProgramGraphKind.CALL,
                      FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
                      callSite.nodeId(),
                      callSite.sourceEvidenceNodeIds()))
          .isNotNull();
      assertThat(
              inputs
                  .evidenceGraph()
                  .closureFor(
                      org.sourceanalysis.app.analysis.graph.ProgramGraphKind.CALL,
                      FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_EDGE,
                      edge.edgeId(),
                      edge.sourceEvidenceNodeIds()))
          .isNotNull();
      assertThat(
              inputs
                  .evidenceGraph()
                  .closureFor(
                      org.sourceanalysis.app.analysis.graph.ProgramGraphKind.CODE_STRUCTURE,
                      FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
                      target.nodeId(),
                      target.sourceEvidenceNodeIds()))
          .isNotNull();
    }
  }

  private static List<String> fieldNames(JsonNode value) {
    List<String> names = new ArrayList<>();
    value.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static List<String> strings(JsonNode value) {
    assertThat(value.isArray()).isTrue();
    List<String> result = new ArrayList<>();
    value.forEach(item -> result.add(item.asText()));
    return result;
  }

  private static void assertAtom(
      Map<String, JsonNode> atoms, String key, String role, String valueType) {
    JsonNode atom = atoms.get(key);
    assertThat(atom).isNotNull();
    assertThat(atom.path("role").asText()).isEqualTo(role);
    assertThat(atom.path("valueType").asText()).isEqualTo(valueType);
  }

  private record ExactRow(
      String entryId,
      String callSiteNodeId,
      String callTargetEdgeId,
      String targetMethodNodeId,
      String targetCanonicalMethod) {

    String denominatorKey() {
      return entryId + "|" + callTargetEdgeId + "|JAVA_EXACT_CALL";
    }
  }

  private static ModulePublicationReference invokePublisher(
      CanonicalModuleArtifactStore store,
      AnalysisStepModuleAddress destination,
      FactCandidateInputs inputs,
      FactCandidateSet candidateSet)
      throws Exception {
    Class<?> publisherType;
    try {
      publisherType = Class.forName(PUBLISHER_CLASS);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLICATION_NOT_IMPLEMENTED", missing);
    }
    Constructor<?> constructor;
    try {
      constructor = publisherType.getConstructor(CanonicalModuleArtifactStore.class);
    } catch (NoSuchMethodException missing) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLISHER_CONSTRUCTOR_MISSING", missing);
    }
    Method publish;
    try {
      publish =
          publisherType.getMethod(
              "publish",
              AnalysisStepModuleAddress.class,
              FactCandidateInputs.class,
              FactCandidateSet.class);
    } catch (NoSuchMethodException missing) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLISHER_SEAM_MISSING", missing);
    }
    Object result;
    try {
      result = publish.invoke(constructor.newInstance(store), destination, inputs, candidateSet);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLICATION_FAILED", cause);
    }
    if (!(result instanceof ModulePublicationReference reference)) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLISHER_RETURN_TYPE_INVALID");
    }
    return reference;
  }

  private static List<String> referenceValues(JsonNode values) {
    List<String> result = new ArrayList<>();
    values.forEach(
        value ->
            result.add(value.path("artifactId").asText() + "|" + value.path("sha256").asText()));
    return result;
  }

  private static List<String> referenceValues(List<ArtifactReference> values) {
    return values.stream()
        .sorted(Comparator.comparing(value -> value.artifactId().value()))
        .map(value -> value.artifactId().value() + "|" + value.sha256().value())
        .toList();
  }
}
