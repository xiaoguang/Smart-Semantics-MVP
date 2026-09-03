package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;

/** RED for M1 graph-profile lineage validation across each graph and the graph index. */
class FactCandidateGraphProfileReferenceTest {

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsGraphPublicationWhenCallAndIndexUseAnotherLegalGraphProfile() {
    try (ProgramGraphsPublicFixture base =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("base-graphs"));
        ProgramGraphsPublicFixture.PersistedGraphMutation mutation =
            ProgramGraphsPublicFixture.republishMutatedGraphs(
                base,
                temporaryDirectory.resolve("mutated-graphs"),
                FactCandidateGraphProfileReferenceTest::replaceCallAndIndexProfile)) {
      VerifiedSourceInventoryReference source = mutation.source();
      ApplicationDiscoveryReference discovery = mutation.discovery();

      assertThatThrownBy(
              () ->
                  new PersistedFactCandidateInputReader(
                          mutation.steps(), mutation.sourceReader())
                      .reopen(source, discovery, mutation.graphs()))
          .isInstanceOf(FactCandidateReferenceException.class)
          .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
    }
  }

  private static List<CanonicalModulePayload> replaceCallAndIndexProfile(
      List<CanonicalModulePayload> original, CanonicalJsonCodec json) {
    ArtifactReference decoy =
        new ArtifactReference(
            ArtifactId.parse("graph-profile:" + digest("unregistered-but-legal-profile")),
            new org.sourceanalysis.app.artifact.Sha256Digest(
                digest("unregistered-but-legal-profile")));
    List<CanonicalModulePayload> changed = new ArrayList<>();
    CanonicalModulePayload changedCall = null;
    for (CanonicalModulePayload payload : original) {
      if (payload.fileName().equals("call-graph.json")) {
        ObjectNode document = (ObjectNode) json.parseCanonical(payload.canonicalUtf8());
        document.set("graphProfileRef", referenceNode(decoy));
        changedCall = ProgramGraphsPublicFixture.rebuildStandaloneGraphPayload(payload, document, json);
      }
    }
    if (changedCall == null) throw new AssertionError("call graph payload missing");
    CanonicalModulePayload finalChangedCall = changedCall;
    for (CanonicalModulePayload payload : original) {
      if (payload.fileName().equals("graph-index.json")) {
        List<CanonicalModulePayload> graphPayloads =
            original.stream()
                .map(value -> value.fileName().equals("call-graph.json") ? finalChangedCall : value)
                .toList();
        CanonicalModulePayload rebuiltIndex =
            ProgramGraphsPublicFixture.rebuildGraphIndex(payload, graphPayloads, json);
        ObjectNode document = (ObjectNode) json.parseCanonical(rebuiltIndex.canonicalUtf8());
        document.set("graphProfileRef", referenceNode(decoy));
        changed.add(
            ProgramGraphsPublicFixture.rebuildStandaloneGraphPayload(rebuiltIndex, document, json));
      } else if (!payload.fileName().equals("call-graph.json")) {
        changed.add(payload);
      } else {
        changed.add(finalChangedCall);
      }
    }
    return List.copyOf(changed);
  }

  private static ObjectNode referenceNode(ArtifactReference value) {
    ObjectNode result = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    result.put("artifactId", value.artifactId().value());
    result.put("sha256", value.sha256().value());
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
