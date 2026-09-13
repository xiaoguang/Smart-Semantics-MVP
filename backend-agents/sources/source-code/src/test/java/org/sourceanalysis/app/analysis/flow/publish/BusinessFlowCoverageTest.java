package org.sourceanalysis.app.analysis.flow.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Stored-artifact coverage for a two-Flow mixed model-eligibility publication. */
public class BusinessFlowCoverageTest {

  @TempDir Path temporaryDirectory;

  @Test
  void preservesBothCompleteCapsulesAndAccurateModelEligibilityUnderBudget() throws Exception {
    Map<String, Integer> generousSpanCounts;
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("mixed-eligibility-generous"))) {
      BusinessFlowsReference publication =
          BusinessFlowTestSupport.publishBusinessFlows(
              fixture, generousFlowProfile(), capsuleProfile(1_024));
      generousSpanCounts = capsuleSpanCounts(reopen(fixture, publication));
      assertThat(generousSpanCounts).hasSize(2);
      assertThat(generousSpanCounts.values()).allSatisfy(count -> assertThat(count).isPositive());
      assertThat(new HashSet<>(generousSpanCounts.values())).hasSize(2);
    }

    int spanBudget =
        generousSpanCounts.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("mixed-eligibility-bounded"))) {
      BusinessFlowsReference publication =
          BusinessFlowTestSupport.publishBusinessFlows(
              fixture, generousFlowProfile(), capsuleProfile(spanBudget));
      ReopenedAnalysisStepPublication reopened = reopen(fixture, publication);
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      JsonNode coverage =
          canonicalJson.parseCanonical(payload(reopened, "flow-coverage.json").canonicalUtf8());
      assertThat(requiredBoolean(coverage, "closed")).isTrue();

      List<String> flowIds = strings(requiredArray(coverage, "flowSliceIds"));
      List<JsonNode> capsuleLines = jsonLines(payload(reopened, "evidence-capsules.jsonl"));
      Map<String, JsonNode> capsulesByFlow = objectsById(capsuleLines, "flowSliceId");
      assertThat(flowIds).hasSize(2);
      assertThat(capsulesByFlow).hasSize(2);
      assertThat(capsulesByFlow.keySet()).containsExactlyInAnyOrderElementsOf(flowIds);

      Map<String, Integer> boundedSpanCounts =
          capsulesByFlow.values().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      capsule -> text(capsule, "flowSliceId"),
                      capsule -> strings(requiredArray(capsule, "modelEvidenceSpanIds")).size()));
      assertThat(boundedSpanCounts).containsExactlyInAnyOrderEntriesOf(generousSpanCounts);

      List<String> eligible = strings(requiredArray(coverage, "modelEligibleFlowSliceIds"));
      List<String> ineligible = strings(requiredArray(coverage, "modelIneligibleFlowSliceIds"));
      assertThat(eligible).hasSize(1);
      assertThat(ineligible).hasSize(1);
      assertThat(new HashSet<>(eligible)).doesNotContainAnyElementsOf(new HashSet<>(ineligible));
      Set<String> partition = new HashSet<>(eligible);
      partition.addAll(ineligible);
      assertThat(partition).containsExactlyInAnyOrderElementsOf(flowIds);

      Map<String, List<String>> mapping = new LinkedHashMap<>();
      for (JsonNode item : requiredArray(coverage, "modelIneligibilityByFlow")) {
        assertThat(item.isObject()).isTrue();
        String flowSliceId = text(item, "flowSliceId");
        List<String> gapIds = strings(requiredArray(item, "gapIds"));
        assertThat(gapIds).isNotEmpty();
        assertThat(mapping.put(flowSliceId, gapIds)).isNull();
      }
      assertThat(mapping.keySet()).containsExactlyElementsOf(ineligible);
      Set<String> mappedGapUnion = new HashSet<>();
      mapping.values().forEach(mappedGapUnion::addAll);
      List<String> coverageGapUnion = strings(requiredArray(coverage, "modelIneligibilityGapIds"));
      assertThat(coverageGapUnion).containsExactlyElementsOf(sorted(mappedGapUnion));

      Map<String, JsonNode> gapsById =
          objectsById(jsonLines(payload(reopened, "flow-gaps.jsonl")), "gapId");
      for (Map.Entry<String, List<String>> entry : mapping.entrySet()) {
        JsonNode capsule = capsulesByFlow.get(entry.getKey());
        assertThat(capsule).isNotNull();
        assertThat(strings(requiredArray(capsule, "modelIneligibilityGapIds")))
            .containsExactlyElementsOf(entry.getValue());
        Map<String, JsonNode> capsuleGaps =
            objectsById(requiredArray(capsule, "gapViews"), "gapId");
        for (String gapId : entry.getValue()) {
          JsonNode gap = gapsById.get(gapId);
          assertThat(gap).isNotNull();
          assertThat(text(gap, "reasonCode")).isEqualTo("CAPSULE_BUDGET_NO_SAFE_SPLIT");
          assertThat(text(gap, "scope")).isEqualTo("FLOW");
          assertThat(strings(requiredArray(gap, "affectedSemanticIds")))
              .containsExactly(entry.getKey());
          JsonNode capsuleGap = capsuleGaps.get(gapId);
          assertThat(capsuleGap).isNotNull();
          assertThat(text(capsuleGap, "reasonCode")).isEqualTo("CAPSULE_BUDGET_NO_SAFE_SPLIT");
          assertThat(strings(requiredArray(capsuleGap, "affectedSemanticIds")))
              .containsExactly(entry.getKey());
        }
      }

      for (JsonNode capsule : capsulesByFlow.values()) {
        int count = boundedSpanCounts.get(text(capsule, "flowSliceId"));
        boolean expectedEligible = count <= spanBudget;
        assertCompleteCapsule(capsule, expectedEligible, spanBudget);
      }
    }
  }

  private static void assertCompleteCapsule(
      JsonNode capsule, boolean expectedEligible, int spanBudget) {
    assertThat(text(capsule, "schemaVersion")).isEqualTo("business-flows-evidence-capsule-v8");
    assertThat(text(capsule, "artifactType")).isEqualTo("BUSINESS_FLOWS_EVIDENCE_CAPSULE");
    String flowSliceId = text(capsule, "flowSliceId");
    List<String> ineligibilityGapIds = strings(requiredArray(capsule, "modelIneligibilityGapIds"));
    assertThat(text(capsule, "modelEligibility"))
        .isEqualTo(expectedEligible ? "ELIGIBLE" : "INELIGIBLE");
    assertThat(ineligibilityGapIds).hasSize(expectedEligible ? 0 : 1);
    assertThat(requiredObject(capsule, "entryView")).isNotNull();

    List<JsonNode> facts = nodes(requiredArray(capsule, "factViews"));
    assertThat(facts).isNotEmpty();
    for (JsonNode fact : facts) {
      assertThat(fact.isObject()).isTrue();
      text(fact, "factId");
      List<JsonNode> atoms = nodes(requiredArray(fact, "atoms"));
      assertThat(atoms).isNotEmpty();
      for (JsonNode atom : atoms) {
        assertThat(atom.isObject()).isTrue();
        text(atom, "atomId");
        text(atom, "proofId");
        text(atom, "role");
        text(atom, "name");
        JsonNode value = requiredObject(atom, "value");
        text(value, "type");
        text(value, "canonical");
      }
    }

    List<JsonNode> outcomes = nodes(requiredArray(capsule, "outcomePathViews"));
    assertThat(outcomes).isNotEmpty();
    for (JsonNode outcome : outcomes) {
      assertThat(outcome.isObject()).isTrue();
      text(outcome, "outcomePathId");
      nodes(requiredArray(outcome, "decisions"));
      text(outcome, "terminalNodeId");
      text(outcome, "terminalKind");
      assertThat(requiredArray(outcome, "requiredAtomIds")).isNotEmpty();
      assertThat(requiredArray(outcome, "requiredProofIds")).isNotEmpty();
    }

    List<JsonNode> signals = nodes(requiredArray(capsule, "processJoinSignals"));
    assertThat(signals).hasSize(4);
    assertThat(signals.stream().map(signal -> text(signal, "signalKind")).toList())
        .containsExactlyInAnyOrder(
            "EXPLICIT_CALL", "EXPLICIT_CALL", "JAVA_TYPE_ANCHOR", "EXTERNAL_EFFECT_GAP");
    for (JsonNode signal : signals) {
      assertThat(signal.isObject()).isTrue();
      text(signal, "processJoinSignalId");
      assertThat(text(signal, "flowSliceId")).isEqualTo(flowSliceId);
      text(signal, "signalKind");
      text(signal, "anchorKind");
      text(signal, "anchorKey");
      text(signal, "direction");
      text(signal, "specificity");
      text(signal, "claimScope");
      assertThat(strings(requiredArray(signal, "factIds"))).isNotEmpty();
      assertThat(strings(requiredArray(signal, "atomIds"))).isNotEmpty();
      assertThat(strings(requiredArray(signal, "proofIds"))).isNotEmpty();
      assertThat(strings(requiredArray(signal, "evidenceNodeIds"))).isNotEmpty();
      nodes(requiredArray(signal, "sourceLocators"));
      strings(requiredArray(signal, "gapIds"));
    }

    List<String> spanIds = strings(requiredArray(capsule, "modelEvidenceSpanIds"));
    assertThat(spanIds).isNotEmpty();
    assertThat(spanIds.size())
        .isEqualTo(strings(requiredArray(capsule, "modelEvidenceSpans"), "spanId").size());
    if (expectedEligible) {
      assertThat(spanIds.size()).isLessThanOrEqualTo(spanBudget);
    } else {
      assertThat(spanIds.size()).isGreaterThan(spanBudget);
    }
    for (JsonNode span : nodes(requiredArray(capsule, "modelEvidenceSpans"))) {
      assertThat(span.isObject()).isTrue();
      text(span, "spanId");
      JsonNode excerpt = requiredObject(span, "sourceExcerpt");
      assertThat(text(excerpt, "rawUtf8")).isNotBlank();
      text(excerpt, "rawUtf8Sha256");
      requiredObject(excerpt, "locator");
      strings(requiredArray(span, "supportedAtomIds"));
      strings(requiredArray(span, "supportedOutcomePathIds"));
      strings(requiredArray(span, "supportedProcessJoinSignalIds"));
    }

    List<String> obligationIds = strings(requiredArray(capsule, "projectionObligationIds"));
    assertThat(obligationIds).isNotEmpty();
    List<JsonNode> obligations = nodes(requiredArray(capsule, "projectionObligations"));
    assertThat(obligations).hasSize(obligationIds.size());
    for (JsonNode obligation : obligations) {
      assertThat(obligation.isObject()).isTrue();
      text(obligation, "obligationId");
      text(obligation, "kind");
      text(obligation, "semanticItemId");
      List<String> satisfyingSpanIds = strings(requiredArray(obligation, "satisfyingSpanIds"));
      assertThat(satisfyingSpanIds).isNotEmpty().allMatch(spanIds::contains);
    }
  }

  private static Map<String, Integer> capsuleSpanCounts(
      ReopenedAnalysisStepPublication publication) {
    return objectsById(jsonLines(payload(publication, "evidence-capsules.jsonl")), "flowSliceId")
        .values()
        .stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                capsule -> text(capsule, "flowSliceId"),
                capsule -> strings(requiredArray(capsule, "modelEvidenceSpanIds")).size()));
  }

  private static ReopenedAnalysisStepPublication reopen(
      ProgramGraphsPublicFixture fixture, BusinessFlowsReference publication) {
    return fixture.stepArtifacts().reopen(publication.publication());
  }

  private static VerifiedCanonicalPayload payload(
      ReopenedAnalysisStepPublication publication, String fileName) {
    return publication.semanticPayloads().stream()
        .filter(value -> fileName.equals(value.descriptor().fileName()))
        .findFirst()
        .orElseThrow();
  }

  private static List<JsonNode> jsonLines(VerifiedCanonicalPayload payload) {
    String jsonl = new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    assertThat(jsonl).isNotBlank().endsWith("\n");
    return jsonl
        .lines()
        .map(
            line ->
                new CanonicalJsonCodec()
                    .parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
        .toList();
  }

  private static Map<String, JsonNode> objectsById(List<JsonNode> values, String fieldName) {
    Map<String, JsonNode> result = new HashMap<>();
    for (JsonNode value : values) {
      assertThat(value.isObject()).isTrue();
      String id = text(value, fieldName);
      assertThat(result.put(id, value)).isNull();
    }
    return Map.copyOf(result);
  }

  private static Map<String, JsonNode> objectsById(JsonNode values, String fieldName) {
    assertThat(values.isArray()).isTrue();
    return objectsById(nodes(values), fieldName);
  }

  private static List<JsonNode> nodes(JsonNode values) {
    assertThat(values.isArray()).isTrue();
    return StreamSupport.stream(values.spliterator(), false).toList();
  }

  private static List<String> strings(JsonNode values) {
    assertThat(values.isArray()).isTrue();
    return StreamSupport.stream(values.spliterator(), false)
        .map(
            value -> {
              assertThat(value.isTextual()).isTrue();
              assertThat(value.textValue()).isNotBlank();
              return value.textValue();
            })
        .toList();
  }

  private static List<String> strings(JsonNode values, String fieldName) {
    assertThat(values.isArray()).isTrue();
    return StreamSupport.stream(values.spliterator(), false)
        .map(value -> text(value, fieldName))
        .toList();
  }

  private static JsonNode requiredArray(JsonNode parent, String fieldName) {
    JsonNode value = parent.path(fieldName);
    assertThat(value.isArray()).as(fieldName).isTrue();
    return value;
  }

  private static JsonNode requiredObject(JsonNode parent, String fieldName) {
    JsonNode value = parent.path(fieldName);
    assertThat(value.isObject()).as(fieldName).isTrue();
    return value;
  }

  private static String text(JsonNode parent, String fieldName) {
    JsonNode value = parent.path(fieldName);
    assertThat(value.isTextual()).as(fieldName).isTrue();
    assertThat(value.textValue()).as(fieldName).isNotBlank();
    return value.textValue();
  }

  private static boolean requiredBoolean(JsonNode parent, String fieldName) {
    JsonNode value = parent.path(fieldName);
    assertThat(value.isBoolean()).as(fieldName).isTrue();
    return value.booleanValue();
  }

  private static List<String> sorted(Set<String> values) {
    return values.stream().sorted().toList();
  }

  private static FlowCompilationProfile generousFlowProfile() {
    return new FlowCompilationProfile(
        BusinessFlowTestSupport.reference("flow-profile", "mixed-eligibility-flow"),
        16,
        8,
        64,
        96,
        32,
        64,
        256);
  }

  private static CapsuleProjectionProfile capsuleProfile(int maxSpansPerCapsule) {
    return new CapsuleProjectionProfile(
        BusinessFlowTestSupport.reference(
            "capsule-profile", "mixed-eligibility-" + maxSpansPerCapsule),
        16,
        maxSpansPerCapsule,
        1_000_000,
        1_000_000);
  }
}
