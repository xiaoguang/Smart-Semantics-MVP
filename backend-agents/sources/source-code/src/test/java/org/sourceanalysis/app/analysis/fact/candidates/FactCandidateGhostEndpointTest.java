package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;

/** RED for M1 persisted CALL_TARGET endpoint closure. */
class FactCandidateGhostEndpointTest {

  @TempDir Path temporaryDirectory;

  @Test
  void ghostCallTargetEndpointFailsClosedBeforeCandidateEnumeration() {
    try (ProgramGraphsPublicFixture base =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("base"));
        ProgramGraphsPublicFixture.PersistedGraphMutation mutation =
            ProgramGraphsPublicFixture.republishMutatedGraphs(
                base,
                temporaryDirectory.resolve("mutated"),
                FactCandidateGhostEndpointTest::replaceCallTargetWithGhost)) {
      assertThatThrownBy(
              () ->
                  new PersistedFactCandidateInputReader(mutation.steps(), mutation.sourceReader())
                      .reopen(
                          mutation.source(), mutation.discovery(), mutation.graphs()))
          .isInstanceOf(FactCandidateReferenceException.class)
          .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
    }
  }

  private static List<CanonicalModulePayload> replaceCallTargetWithGhost(
      List<CanonicalModulePayload> originals, CanonicalJsonCodec json) {
    CanonicalModulePayload originalCall = find(originals, "call-graph.json");
    ObjectNode call = (ObjectNode) json.parseCanonical(originalCall.canonicalUtf8());
    ObjectNode target =
        objects(call.path("edges"))
            .stream()
            .filter(edge -> "CALL_TARGET".equals(edge.path("kind").asText()))
            .findFirst()
            .orElseThrow();
    target.put("toNodeId", "node:" + digest("ghost-call-target"));
    CanonicalModulePayload changedCall =
        ProgramGraphsPublicFixture.rebuildStandaloneGraphPayload(originalCall, call, json);
    List<CanonicalModulePayload> changed =
        replace(originals, "call-graph.json", changedCall);
    CanonicalModulePayload changedIndex =
        ProgramGraphsPublicFixture.rebuildGraphIndex(
            find(changed, "graph-index.json"), changed, json);
    return replace(changed, "graph-index.json", changedIndex);
  }

  private static List<CanonicalModulePayload> replace(
      List<CanonicalModulePayload> originals,
      String fileName,
      CanonicalModulePayload replacement) {
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
    List<ObjectNode> result = new ArrayList<>();
    values.forEach(value -> result.add((ObjectNode) value));
    return result;
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
