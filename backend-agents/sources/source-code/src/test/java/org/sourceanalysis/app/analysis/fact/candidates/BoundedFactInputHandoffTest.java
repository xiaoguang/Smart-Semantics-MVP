package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Public Fact seam: bounded repository scope remains a real, closed graph handoff. */
class BoundedFactInputHandoffTest {

  @TempDir Path temporaryDirectory;

  @Test
  void handsBoundedGraphPublicationToFactReaderWithSharedScopeAndGapClosure() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithBoundedPathSet(
            temporaryDirectory.resolve("bounded-fact-handoff"))) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ReopenedAnalysisStepPublication discovery =
          fixture.stepArtifacts().reopen(fixture.applicationDiscovery().publication());
      JsonNode capability =
          canonicalJson.parseCanonical(
              payload(discovery, "capability-report.json").canonicalUtf8());
      JsonNode repositoryEntryCoverage = requiredObject(capability, "repositoryEntryCoverage");
      assertThat(repositoryEntryCoverage.path("closed").isBoolean()).isTrue();
      assertThat(repositoryEntryCoverage.path("closed").booleanValue()).isFalse();

      ReopenedAnalysisStepPublication graphs =
          fixture.stepArtifacts().reopen(fixture.programGraphs().publication());
      Map<String, JsonNode> graphDocuments = new HashMap<>();
      for (String fileName :
          List.of(
              "code-structure-graph.json",
              "call-graph.json",
              "control-flow-graph.json",
              "data-flow-graph.json",
              "evidence-graph.json")) {
        graphDocuments.put(
            fileName, canonicalJson.parseCanonical(payload(graphs, fileName).canonicalUtf8()));
      }

      List<String> sharedScopeGapIds = null;
      for (String fileName :
          List.of(
              "code-structure-graph.json",
              "call-graph.json",
              "control-flow-graph.json",
              "data-flow-graph.json")) {
        JsonNode coverage = requiredObject(graphDocuments.get(fileName), "coverage");
        assertThat(coverage.path("closed").isBoolean()).isTrue();
        assertThat(coverage.path("closed").booleanValue()).isFalse();
        List<String> scopeGapIds = textValues(requiredArray(coverage, "scopeGapIds"));
        assertThat(scopeGapIds).isNotEmpty();
        if (sharedScopeGapIds == null) {
          sharedScopeGapIds = scopeGapIds;
        } else {
          assertThat(scopeGapIds).containsExactlyElementsOf(sharedScopeGapIds);
        }
      }
      assertThat(sharedScopeGapIds).isNotNull().isNotEmpty();
      Set<String> scopeGaps = Set.copyOf(sharedScopeGapIds);

      JsonNode evidenceCoverage =
          requiredObject(graphDocuments.get("evidence-graph.json"), "coverage");
      assertThat(evidenceCoverage.path("closed").isBoolean()).isTrue();
      assertThat(evidenceCoverage.path("closed").booleanValue()).isTrue();

      List<JsonNode> localGapRows =
          jsonLines(canonicalJson, payload(graphs, "graph-gaps.jsonl").canonicalUtf8());
      Set<String> localGaps = new HashSet<>();
      for (JsonNode gap : localGapRows) {
        assertThat(localGaps.add(requiredText(gap, "gapId"))).isTrue();
      }
      Set<String> allGraphGaps = new HashSet<>(localGaps);
      allGraphGaps.addAll(scopeGaps);

      JsonNode graphIndex =
          canonicalJson.parseCanonical(payload(graphs, "graph-index.json").canonicalUtf8());
      assertThat(graphIndex.path("closed").isBoolean()).isTrue();
      assertThat(graphIndex.path("closed").booleanValue()).isTrue();
      assertThat(textValues(requiredArray(graphIndex, "gapIds")))
          .containsExactlyInAnyOrderElementsOf(allGraphGaps);
      assertThat(new HashSet<>(graphs.receipt().gapRefs()))
          .containsExactlyInAnyOrderElementsOf(allGraphGaps);

      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      assertThat(inputs.entryIds()).isNotEmpty();
      assertThat(inputs.programGraphs()).hasSize(4);
    }
  }

  private static VerifiedCanonicalPayload payload(
      ReopenedAnalysisStepPublication publication, String fileName) {
    return publication.semanticPayloads().stream()
        .filter(value -> fileName.equals(value.descriptor().fileName()))
        .findFirst()
        .orElseThrow();
  }

  private static List<JsonNode> jsonLines(CanonicalJsonCodec canonicalJson, ImmutableBytes bytes) {
    String text = new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
    if (text.isEmpty()) return List.of();
    if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
    String[] lines = text.split("\\n", -1);
    List<JsonNode> values = new ArrayList<>();
    for (String line : lines) {
      assertThat(line).isNotEmpty();
      values.add(
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))));
    }
    return List.copyOf(values);
  }

  private static JsonNode requiredObject(JsonNode value, String field) {
    assertThat(value.isObject()).isTrue();
    JsonNode child = value.get(field);
    assertThat(child).isNotNull();
    assertThat(child.isObject()).isTrue();
    return child;
  }

  private static JsonNode requiredArray(JsonNode value, String field) {
    assertThat(value.isObject()).isTrue();
    JsonNode child = value.get(field);
    assertThat(child).isNotNull();
    assertThat(child.isArray()).isTrue();
    return child;
  }

  private static String requiredText(JsonNode value, String field) {
    assertThat(value.isObject()).isTrue();
    JsonNode child = value.get(field);
    assertThat(child).isNotNull();
    assertThat(child.isTextual()).isTrue();
    assertThat(child.textValue()).isNotBlank();
    return child.textValue();
  }

  private static List<String> textValues(JsonNode values) {
    assertThat(values.isArray()).isTrue();
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.asText()));
    return List.copyOf(result);
  }
}
