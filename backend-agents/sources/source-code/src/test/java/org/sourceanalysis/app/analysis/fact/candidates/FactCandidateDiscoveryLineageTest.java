package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** RED for M1 validation of discovery-to-verified-source lineage. */
class FactCandidateDiscoveryLineageTest {

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsDiscoveryProfileWithUnrelatedSourceInventoryReferenceBeforeEnumeration() {
    try (ProgramGraphsPublicFixture base =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("base-graphs"));
        ProgramGraphsPublicFixture.PersistedGraphMutation mutation =
            ProgramGraphsPublicFixture.republishMutatedDiscovery(
                base,
                temporaryDirectory.resolve("mutated-discovery"),
                FactCandidateDiscoveryLineageTest::replaceSourceInventoryReference)) {
      assertThatThrownBy(
              () ->
                  new PersistedFactCandidateInputReader(mutation.steps(), mutation.sourceReader())
                      .reopen(mutation.source(), mutation.discovery(), mutation.graphs()))
          .isInstanceOf(FactCandidateReferenceException.class)
          .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
    }
  }

  private static List<CanonicalModulePayload> replaceSourceInventoryReference(
      List<CanonicalModulePayload> original, CanonicalJsonCodec json) {
    ArtifactReference unrelated =
        new ArtifactReference(
            ArtifactId.parse("source-inventory:" + digest("unrelated-source-inventory")),
            new Sha256Digest(digest("unrelated-source-inventory")));
    boolean changed = false;
    List<CanonicalModulePayload> result =
        original.stream()
            .map(
                payload -> {
                  if (!payload.fileName().equals("application-profile.json")) return payload;
                  ObjectNode document = (ObjectNode) json.parseCanonical(payload.canonicalUtf8());
                  document.set("sourceInventoryRef", referenceNode(unrelated));
                  return ProgramGraphsPublicFixture.rebuildStandaloneGraphPayload(
                      payload, document, json);
                })
            .toList();
    for (CanonicalModulePayload payload : result) {
      if (payload.fileName().equals("application-profile.json")) {
        changed = true;
        break;
      }
    }
    if (!changed) throw new AssertionError("application profile payload missing");
    return result;
  }

  private static ObjectNode referenceNode(ArtifactReference value) {
    return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", value.artifactId().value())
        .put("sha256", value.sha256().value());
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
