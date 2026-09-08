package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphKind;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;

/** RED for M1 graph-index descriptor lineage validation. */
class FactCandidateGraphIndexDescriptorTest {

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsGraphIndexWhenDescriptorGraphIdDoesNotMatchItsPublicGraph() {
    try (ProgramGraphsPublicFixture base =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("base"));
        ProgramGraphsPublicFixture.PersistedGraphMutation mutation =
            ProgramGraphsPublicFixture.republishMutatedGraphs(
                base,
                temporaryDirectory.resolve("mutated"),
                FactCandidateGraphIndexDescriptorTest::replaceDataFlowDescriptorGraphId)) {
      assertThatThrownBy(
              () ->
                  new PersistedFactCandidateInputReader(mutation.steps(), mutation.sourceReader())
                      .reopen(mutation.source(), mutation.discovery(), mutation.graphs()))
          .isInstanceOf(FactCandidateReferenceException.class)
          .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
    }
  }

  private static List<CanonicalModulePayload> replaceDataFlowDescriptorGraphId(
      List<CanonicalModulePayload> originals, CanonicalJsonCodec json) {
    CanonicalModulePayload originalIndex =
        originals.stream()
            .filter(payload -> payload.fileName().equals("graph-index.json"))
            .findFirst()
            .orElseThrow();
    ObjectNode index = (ObjectNode) json.parseCanonical(originalIndex.canonicalUtf8());
    ObjectNode descriptor =
        objects(index.path("graphs")).stream()
            .filter(
                value -> ProgramGraphKind.DATA_FLOW.name().equals(value.path("graphKind").asText()))
            .findFirst()
            .orElseThrow();
    String originalGraphId = descriptor.path("graphId").asText();
    String replacementGraphId =
        "program-graphs-data-flow-graph:" + digest("forged-descriptor-graph-id");
    if (replacementGraphId.equals(originalGraphId)) {
      throw new AssertionError("test mutation did not change graphId");
    }
    descriptor.put("graphId", replacementGraphId);
    CanonicalModulePayload changedIndex =
        ProgramGraphsPublicFixture.rebuildStandaloneGraphPayload(originalIndex, index, json);
    List<CanonicalModulePayload> changed = new ArrayList<>();
    for (CanonicalModulePayload payload : originals) {
      changed.add(payload.fileName().equals("graph-index.json") ? changedIndex : payload);
    }
    return List.copyOf(changed);
  }

  private static List<ObjectNode> objects(JsonNode values) {
    List<ObjectNode> result = new ArrayList<>();
    values.forEach(value -> result.add((ObjectNode) value));
    return result;
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
