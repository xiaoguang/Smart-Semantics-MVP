package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;

/** RED for persisted SOURCE_EXCERPT byte-hash and locator invariants in Fact M1. */
class FactCandidateSourceExcerptIntegrityTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void sourceExcerptBytesAndLocatorMustBeRevalidatedBeforeCandidateEnumeration() {
    Throwable changedBytes =
        catchThrowable(
            () ->
                reopenMutatedGraphs(
                    "changed-bytes", FactCandidateSourceExcerptIntegrityTest::changeRawUtf8Only));
    Throwable invalidLocator =
        catchThrowable(
            () ->
                reopenMutatedGraphs(
                    "invalid-locator",
                    FactCandidateSourceExcerptIntegrityTest::makeLocatorInvalid));

    SoftAssertions softly = new SoftAssertions();
    softly
        .assertThat(changedBytes)
        .as("changed rawUtf8 must fail before enumeration")
        .isInstanceOf(FactCandidateReferenceException.class)
        .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
    softly
        .assertThat(invalidLocator)
        .as("invalid source locator must fail before enumeration")
        .isInstanceOf(FactCandidateReferenceException.class)
        .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
    softly.assertAll();
  }

  private void reopenMutatedGraphs(
      String mutationName,
      java.util.function.BiFunction<
              List<CanonicalModulePayload>, CanonicalJsonCodec, List<CanonicalModulePayload>>
          mutation) {
    try (ProgramGraphsPublicFixture base =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve(mutationName + "-base"));
        ProgramGraphsPublicFixture.PersistedGraphMutation changed =
            ProgramGraphsPublicFixture.republishMutatedGraphs(
                base, temporaryDirectory.resolve(mutationName + "-mutated"), mutation)) {
      new PersistedFactCandidateInputReader(changed.steps(), changed.sourceReader())
          .reopen(changed.source(), changed.discovery(), changed.graphs());
    }
  }

  private static List<CanonicalModulePayload> changeRawUtf8Only(
      List<CanonicalModulePayload> originals, CanonicalJsonCodec json) {
    CanonicalModulePayload evidencePayload = find(originals, "evidence-graph.json");
    ObjectNode evidence = (ObjectNode) json.parseCanonical(evidencePayload.canonicalUtf8());
    ObjectNode sourceExcerpt = firstSourceExcerpt(evidence.path("nodes"));
    String original = sourceExcerpt.path("rawUtf8").asText();
    sourceExcerpt.put("rawUtf8", original + " changed");
    List<CanonicalModulePayload> changed =
        replace(
            originals,
            "evidence-graph.json",
            ProgramGraphsPublicFixture.rebuildStandaloneGraphPayload(
                evidencePayload, evidence, json));
    return replace(
        changed,
        "graph-index.json",
        ProgramGraphsPublicFixture.rebuildGraphIndex(
            find(changed, "graph-index.json"), changed, json));
  }

  private static List<CanonicalModulePayload> makeLocatorInvalid(
      List<CanonicalModulePayload> originals, CanonicalJsonCodec json) {
    CanonicalModulePayload evidencePayload = find(originals, "evidence-graph.json");
    ObjectNode evidence = (ObjectNode) json.parseCanonical(evidencePayload.canonicalUtf8());
    ObjectNode locator = firstSourceExcerpt(evidence.path("nodes")).with("locator");
    locator.put("path", "/outside/repository.java");
    List<CanonicalModulePayload> changed =
        replace(
            originals,
            "evidence-graph.json",
            ProgramGraphsPublicFixture.rebuildStandaloneGraphPayload(
                evidencePayload, evidence, json));
    return replace(
        changed,
        "graph-index.json",
        ProgramGraphsPublicFixture.rebuildGraphIndex(
            find(changed, "graph-index.json"), changed, json));
  }

  private static ObjectNode firstSourceExcerpt(JsonNode nodes) {
    for (JsonNode node : nodes) {
      if ("SOURCE_EXCERPT".equals(node.path("kind").asText())) {
        return (ObjectNode) node.path("sourceExcerpt");
      }
    }
    throw new AssertionError("fixture must contain a source excerpt");
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
}
