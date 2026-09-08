package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;

/** RED for persisted Evidence support-kind closure in Fact M1. */
class FactCandidateEvidenceSupportKindTest {

  @TempDir Path temporaryDirectory;

  @Test
  void programEdgeEvidenceCannotBeRelabeledAsProgramNodeSupport() {
    try (ProgramGraphsPublicFixture base =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("base"));
        ProgramGraphsPublicFixture.PersistedGraphMutation mutation =
            ProgramGraphsPublicFixture.republishMutatedGraphs(
                base,
                temporaryDirectory.resolve("mutated"),
                FactCandidateEvidenceSupportKindTest::relabeledProgramEdgeSupport)) {
      assertThatThrownBy(
              () ->
                  new PersistedFactCandidateInputReader(mutation.steps(), mutation.sourceReader())
                      .reopen(mutation.source(), mutation.discovery(), mutation.graphs()))
          .isInstanceOf(FactCandidateReferenceException.class)
          .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
    }
  }

  private static List<CanonicalModulePayload> relabeledProgramEdgeSupport(
      List<CanonicalModulePayload> originals, CanonicalJsonCodec json) {
    CanonicalModulePayload callPayload = find(originals, "call-graph.json");
    ObjectNode call = (ObjectNode) json.parseCanonical(callPayload.canonicalUtf8());
    Set<String> callEdgeIds = new HashSet<>();
    call.path("edges").forEach(edge -> callEdgeIds.add(edge.path("edgeId").asText()));

    CanonicalModulePayload evidencePayload = find(originals, "evidence-graph.json");
    ObjectNode evidence = (ObjectNode) json.parseCanonical(evidencePayload.canonicalUtf8());
    ObjectNode target =
        objects(evidence.path("edges")).stream()
            .filter(edge -> "SUPPORTS_PROGRAM_EDGE".equals(edge.path("kind").asText()))
            .filter(edge -> "CALL".equals(edge.path("subjectGraphKind").asText()))
            .filter(edge -> callEdgeIds.contains(edge.path("subjectProgramElementId").asText()))
            .findFirst()
            .orElse(null);
    assertThat(target).as("fixture must contain Evidence for a real CALL graph edge").isNotNull();
    target.put("kind", "SUPPORTS_PROGRAM_NODE");

    CanonicalModulePayload changedEvidence =
        ProgramGraphsPublicFixture.rebuildStandaloneGraphPayload(evidencePayload, evidence, json);
    List<CanonicalModulePayload> changed =
        replace(originals, "evidence-graph.json", changedEvidence);
    CanonicalModulePayload changedIndex =
        ProgramGraphsPublicFixture.rebuildGraphIndex(
            find(changed, "graph-index.json"), changed, json);
    return replace(changed, "graph-index.json", changedIndex);
  }

  private static List<CanonicalModulePayload> replace(
      List<CanonicalModulePayload> originals, String fileName, CanonicalModulePayload replacement) {
    return originals.stream()
        .map(payload -> fileName.equals(payload.fileName()) ? replacement : payload)
        .toList();
  }

  private static CanonicalModulePayload find(
      List<CanonicalModulePayload> payloads, String fileName) {
    return payloads.stream()
        .filter(payload -> fileName.equals(payload.fileName()))
        .findFirst()
        .orElseThrow();
  }

  private static List<ObjectNode> objects(JsonNode values) {
    ArrayList<ObjectNode> result = new ArrayList<>();
    values.forEach(value -> result.add((ObjectNode) value));
    return result;
  }
}
