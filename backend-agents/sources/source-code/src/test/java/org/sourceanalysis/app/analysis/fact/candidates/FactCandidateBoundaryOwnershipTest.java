package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;

/** RED for M1's rule that a boundary without a valid entry owner cannot disappear silently. */
class FactCandidateBoundaryOwnershipTest {

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsPersistedBoundaryWithNoEntryOwnerBeforeDenominatorIsDropped() {
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("base-graphs"));
        ProgramGraphsPublicFixture.PersistedGraphMutation mutation =
            ProgramGraphsPublicFixture.republishMutatedGraphs(
                fixture,
                temporaryDirectory.resolve("ownerless-graphs"),
                FactCandidateBoundaryOwnershipTest::removeApproveBoundaryOwner)) {
      assertThatThrownBy(
              () ->
                  new PersistedFactCandidateInputReader(mutation.steps(), mutation.sourceReader())
                      .reopen(mutation.source(), mutation.discovery(), mutation.graphs()))
          .isInstanceOf(FactCandidateReferenceException.class)
          .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
    }
  }

  private static List<CanonicalModulePayload> removeApproveBoundaryOwner(
      List<CanonicalModulePayload> payloads, CanonicalJsonCodec json) {
    CanonicalModulePayload dataFlow =
        payloads.stream()
            .filter(payload -> payload.fileName().equals("data-flow-graph.json"))
            .findFirst()
            .orElseThrow();
    ObjectNode document = (ObjectNode) json.parseCanonical(dataFlow.canonicalUtf8());
    boolean changed = false;
    for (JsonNode nodeValue : document.path("nodes")) {
      if (!"JAVA_BOUNDARY_INVOCATION".equals(nodeValue.path("kind").asText())
          || !hasOwner(nodeValue.path("owningEntryIds"), "entry:" + digest("approve"))) {
        continue;
      }
      ((ObjectNode) nodeValue).set("owningEntryIds", JsonNodeFactory.instance.arrayNode());
      changed = true;
      break;
    }
    if (!changed) throw new AssertionError("approve boundary was not found");
    CanonicalModulePayload changedData =
        ProgramGraphsPublicFixture.rebuildStandaloneGraphPayload(dataFlow, document, json);
    CanonicalModulePayload originalIndex =
        payloads.stream()
            .filter(payload -> payload.fileName().equals("graph-index.json"))
            .findFirst()
            .orElseThrow();
    CanonicalModulePayload changedIndex =
        ProgramGraphsPublicFixture.rebuildGraphIndex(
            originalIndex,
            payloads.stream()
                .map(
                    payload ->
                        payload.fileName().equals("data-flow-graph.json") ? changedData : payload)
                .toList(),
            json);
    return payloads.stream()
        .map(
            payload -> {
              if (payload.fileName().equals("data-flow-graph.json")) return changedData;
              if (payload.fileName().equals("graph-index.json")) return changedIndex;
              return payload;
            })
        .toList();
  }

  private static boolean hasOwner(JsonNode values, String expected) {
    if (values == null || !values.isArray()) return false;
    for (JsonNode value : values) {
      if (expected.equals(value.asText())) return true;
    }
    return false;
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
