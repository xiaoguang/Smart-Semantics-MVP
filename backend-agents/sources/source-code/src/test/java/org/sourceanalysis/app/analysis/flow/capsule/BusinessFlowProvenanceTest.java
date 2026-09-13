package org.sourceanalysis.app.analysis.flow.capsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.fact.proofs.AtomicProofBuilder;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSet;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.proofs.ProofRuleRegistry;
import org.sourceanalysis.app.analysis.fact.publish.FactLedgerPublicationSpecifier;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.compiler.EntryRootedFlowCompiler;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.publish.FlowPublicationException;
import org.sourceanalysis.app.analysis.flow.publish.FlowPublicationSpecifier;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Public M2 seam: persisted Fact views retain their exact Step 04 artifact provenance. */
class BusinessFlowProvenanceTest {

  @TempDir Path temporaryDirectory;

  @Test
  void freshReopenedFactViewsCarryExactProvenFactsOriginAndAtoms() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("business-flow-provenance"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference flows =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
      CapsuleProjection projection =
          new EvidenceCapsuleProjector(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .project(
                  flows,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  capsuleProfile());
      ModulePublicationReference capsulePublication =
          new CapsuleProjectionModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .publish(
                  flows, fixture.sourceInventory(), fixture.programGraphs(), facts, projection);

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ReopenedAnalysisStepPublication reopenedFacts =
          fixture.stepArtifacts().reopen(facts.publication());
      VerifiedCanonicalPayload provenFactsPayload =
          reopenedFacts.semanticPayloads().stream()
              .filter(value -> "proven-facts.json".equals(value.descriptor().fileName()))
              .findFirst()
              .orElseThrow();
      ArtifactReference provenFactsReference =
          new ArtifactReference(
              provenFactsPayload.descriptor().artifactId(),
              provenFactsPayload.descriptor().sha256());
      JsonNode provenFacts = canonicalJson.parseCanonical(provenFactsPayload.canonicalUtf8());
      Map<String, JsonNode> factsById =
          objectsById(requiredArray(provenFacts, "codeFacts"), "factId");

      ReopenedModulePublication reopenedCapsule =
          fixture.moduleArtifacts().reopen(capsulePublication);
      JsonNode capsuleEnvelope =
          canonicalJson.parseCanonical(reopenedCapsule.payloads().get(0).canonicalUtf8());
      JsonNode capsules = requiredArray(requiredObject(capsuleEnvelope, "payload"), "capsules");
      assertThat(capsules).isNotEmpty();
      for (JsonNode capsule : capsules) {
        JsonNode factViews = requiredArray(capsule, "factViews");
        assertThat(factViews).isNotEmpty();
        for (JsonNode factView : factViews) {
          String factId = requiredText(factView, "factId");
          JsonNode provenFact = factsById.get(factId);
          assertThat(provenFact)
              .as("Fact view %s must resolve to one persisted proven Fact", factId)
              .isNotNull();

          JsonNode origin = factView.get("originFactArtifactRef");
          assertThat(origin).as("Fact view %s must carry Step 04 provenance", factId).isNotNull();
          if (origin != null) {
            assertArtifactReference(origin, provenFactsReference);
          }

          assertThat(requiredText(factView, "kind")).isEqualTo(requiredText(provenFact, "kind"));
          assertTextArray(
              requiredArray(factView, "subjectNodeIds"),
              requiredArray(provenFact, "subjectNodeIds"));
          assertFactAtoms(requiredArray(factView, "atoms"), requiredArray(provenFact, "atoms"));
        }
      }
    }
  }

  @Test
  void freshReopenedSourceGapViewsCarryExactLedgerAndEvidenceGraphProvenance() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("business-flow-source-gap-provenance"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference flows =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
      CapsuleProjection projection =
          new EvidenceCapsuleProjector(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .project(
                  flows,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  capsuleProfile());
      ModulePublicationReference capsulePublication =
          new CapsuleProjectionModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .publish(
                  flows, fixture.sourceInventory(), fixture.programGraphs(), facts, projection);

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ReopenedAnalysisStepPublication reopenedFacts =
          fixture.stepArtifacts().reopen(facts.publication());
      VerifiedCanonicalPayload gapLedgerPayload = payload(reopenedFacts, "gap-ledger.json");
      ArtifactReference gapLedgerReference = artifactReference(gapLedgerPayload);
      JsonNode gapLedger = canonicalJson.parseCanonical(gapLedgerPayload.canonicalUtf8());
      List<JsonNode> sourceGaps = new ArrayList<>();
      requiredArray(gapLedger, "gaps")
          .forEach(
              gap -> {
                if ("EXTERNAL_EFFECT".equals(requiredText(gap, "kind"))
                    && "DATA_FLOW_BINDING_UNPROVEN".equals(requiredText(gap, "code"))) {
                  sourceGaps.add(gap);
                }
              });
      assertThat(sourceGaps)
          .as("guarded fixture must publish real source external-effect gaps")
          .isNotEmpty();
      Map<String, JsonNode> sourceGapsById = new HashMap<>();
      sourceGaps.forEach(
          sourceGap ->
              assertThat(sourceGapsById.put(requiredText(sourceGap, "gapId"), sourceGap)).isNull());

      ReopenedAnalysisStepPublication reopenedGraphs =
          fixture.stepArtifacts().reopen(fixture.programGraphs().publication());
      VerifiedCanonicalPayload evidenceGraphPayload =
          payload(reopenedGraphs, "evidence-graph.json");
      ArtifactReference evidenceGraphReference = artifactReference(evidenceGraphPayload);
      JsonNode evidenceGraph = canonicalJson.parseCanonical(evidenceGraphPayload.canonicalUtf8());
      Map<String, JsonNode> evidenceNodesById =
          objectsById(requiredArray(evidenceGraph, "nodes"), "evidenceNodeId");
      for (JsonNode sourceGap : sourceGaps) {
        List<String> evidenceNodeIds = textValues(requiredArray(sourceGap, "evidenceNodeIds"));
        assertThat(evidenceNodeIds).isNotEmpty();
        assertThat(evidenceNodeIds).allMatch(evidenceNodesById::containsKey);
      }

      ReopenedModulePublication reopenedCapsule =
          fixture.moduleArtifacts().reopen(capsulePublication);
      JsonNode capsuleEnvelope =
          canonicalJson.parseCanonical(reopenedCapsule.payloads().get(0).canonicalUtf8());
      List<JsonNode> sourceGapViews = new ArrayList<>();
      for (JsonNode capsule :
          requiredArray(requiredObject(capsuleEnvelope, "payload"), "capsules")) {
        for (JsonNode gapView : requiredArray(capsule, "gapViews")) {
          if (sourceGapsById.containsKey(requiredText(gapView, "gapId"))) {
            sourceGapViews.add(gapView);
          }
        }
      }
      assertThat(sourceGapViews)
          .as("every real source gap must have one public M2 gap view")
          .hasSize(sourceGaps.size());
      assertThat(sourceGapViews).isNotEmpty();

      for (JsonNode gapView : sourceGapViews) {
        JsonNode sourceGap = sourceGapsById.get(requiredText(gapView, "gapId"));
        assertThat(requiredText(gapView, "originKind")).isEqualTo("PROVEN_CODE_FACTS_GAP_LEDGER");
        assertArtifactReference(requiredObject(gapView, "originGapLedgerRef"), gapLedgerReference);
        assertThat(requiredText(gapView, "scope")).isEqualTo("FACT");
        assertThat(requiredText(gapView, "reasonCode")).isEqualTo(requiredText(sourceGap, "code"));
        assertThat(requiredArray(gapView, "affectedSemanticIds"))
            .isEqualTo(requiredArray(sourceGap, "affectedCandidateDenominatorKeys"));
        JsonNode evidenceRefs = requiredArray(gapView, "evidenceRefs");
        assertThat(evidenceRefs).hasSize(1);
        assertArtifactReference(evidenceRefs.get(0), evidenceGraphReference);
      }
    }
  }

  @Test
  void freshReopenedBudgetGapViewsCarryCapsuleProjectionProvenance() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("business-flow-budget-gap-provenance"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference flows =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
      CapsuleProjectionProfile budgetProfile =
          new CapsuleProjectionProfile(
              reference("capsule-profile", "business-flow-provenance-budget"), 16, 32, 4_096, 1);
      CapsuleProjection projection =
          new EvidenceCapsuleProjector(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .project(
                  flows, fixture.sourceInventory(), fixture.programGraphs(), facts, budgetProfile);
      assertThat(projection.capsules()).isNotEmpty();
      assertThat(projection.capsules())
          .allSatisfy(
              capsule -> {
                assertThat(capsule.modelEligibility()).isEqualTo("INELIGIBLE");
                assertThat(capsule.modelEvidenceSpanIds()).isNotEmpty();
                assertThat(capsule.modelIneligibilityGapIds()).hasSize(1);
                assertThat(capsule.budgetUsage().sourceUtf8Bytes())
                    .isGreaterThan(budgetProfile.maxCapsuleUtf8Bytes());
              });

      ModulePublicationReference capsulePublication =
          new CapsuleProjectionModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .publish(
                  flows, fixture.sourceInventory(), fixture.programGraphs(), facts, projection);
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ReopenedModulePublication reopenedCapsule =
          fixture.moduleArtifacts().reopen(capsulePublication);
      JsonNode capsuleEnvelope =
          canonicalJson.parseCanonical(reopenedCapsule.payloads().get(0).canonicalUtf8());
      JsonNode payload = requiredObject(capsuleEnvelope, "payload");
      JsonNode persistedProfile = requiredObject(payload, "capsuleProjectionProfile");
      assertThat(persistedProfile.path("maxCapsuleUtf8Bytes").asInt()).isEqualTo(1);
      JsonNode capsules = requiredArray(payload, "capsules");
      assertThat(capsules).isNotEmpty();
      for (JsonNode capsule : capsules) {
        String flowSliceId = requiredText(capsule, "flowSliceId");
        assertThat(requiredText(capsule, "modelEligibility")).isEqualTo("INELIGIBLE");
        JsonNode budgetUsage = requiredObject(capsule, "budgetUsage");
        assertThat(budgetUsage.path("spanCount").asInt()).isPositive();
        assertThat(budgetUsage.path("sourceUtf8Bytes").asLong())
            .isGreaterThan(persistedProfile.path("maxCapsuleUtf8Bytes").asLong());

        List<String> ineligibilityGapIds =
            textValues(requiredArray(capsule, "modelIneligibilityGapIds"));
        assertThat(ineligibilityGapIds).hasSize(1);
        Map<String, JsonNode> gapViewsById =
            objectsById(requiredArray(capsule, "gapViews"), "gapId");
        JsonNode budgetGap = gapViewsById.get(ineligibilityGapIds.get(0));
        assertThat(budgetGap).isNotNull();
        assertThat(requiredText(budgetGap, "scope")).isEqualTo("FLOW");
        assertThat(requiredText(budgetGap, "reasonCode")).isEqualTo("CAPSULE_BUDGET_NO_SAFE_SPLIT");
        assertThat(requiredText(budgetGap, "originKind")).isEqualTo("CAPSULE_PROJECTION");
        assertThat(budgetGap.get("originGapLedgerRef")).isNotNull();
        assertThat(budgetGap.get("originGapLedgerRef").isNull()).isTrue();
        assertThat(requiredArray(budgetGap, "evidenceRefs")).isEmpty();
        assertThat(textValues(requiredArray(budgetGap, "affectedSemanticIds")))
            .containsExactly(flowSliceId);
      }
    }
  }

  @Test
  void publishesFreshReopenedM3FactAndSourceGapProvenanceWithPublishedCarrierVersions() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("business-flow-m3-provenance"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference flowCompilation =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
      CapsuleProjection projection =
          new EvidenceCapsuleProjector(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .project(
                  flowCompilation,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  capsuleProfile());
      ModulePublicationReference capsuleProjection =
          new CapsuleProjectionModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .publish(
                  flowCompilation,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  projection);

      BusinessFlowsReference businessFlows =
          new FlowPublicationSpecifier(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .specify(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs(),
                  facts,
                  flowCompilation,
                  capsuleProjection);
      ReopenedAnalysisStepPublication reopenedPublic =
          fixture.stepArtifacts().reopen(businessFlows.publication());
      assertThat(reopenedPublic.semanticPayloads())
          .extracting(value -> value.descriptor().fileName())
          .containsExactly(
              "entry-dispositions.jsonl",
              "evidence-capsules.jsonl",
              "flow-coverage.json",
              "flow-gaps.jsonl",
              "flow-slices.json");
      assertThat(reopenedPublic.semanticPayloads()).hasSize(5);
      Map<String, String> expectedSchemas =
          Map.of(
              "entry-dispositions.jsonl", "business-flows-entry-disposition-v2",
              "evidence-capsules.jsonl", "business-flows-evidence-capsule-v9",
              "flow-coverage.json", "business-flows-flow-coverage-v2",
              "flow-gaps.jsonl", "business-flows-flow-gap-v2",
              "flow-slices.json", "business-flows-flow-slices-v6");
      reopenedPublic
          .semanticPayloads()
          .forEach(
              value ->
                  assertThat(value.descriptor().schemaVersion())
                      .isEqualTo(expectedSchemas.get(value.descriptor().fileName())));

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ReopenedModulePublication reopenedCompiler =
          fixture.moduleArtifacts().reopen(flowCompilation);
      JsonNode compilerEnvelope =
          canonicalJson.parseCanonical(reopenedCompiler.payloads().get(0).canonicalUtf8());
      Map<String, JsonNode> m1FlowsById =
          objectsById(
              requiredArray(requiredObject(compilerEnvelope, "payload"), "flowSlices"),
              "flowSliceId");
      assertThat(m1FlowsById).isNotEmpty();
      JsonNode publicFlowSlices =
          canonicalJson.parseCanonical(payload(reopenedPublic, "flow-slices.json").canonicalUtf8());
      Map<String, JsonNode> flowSlicesById =
          objectsById(requiredArray(publicFlowSlices, "flowSlices"), "flowSliceId");
      assertThat(flowSlicesById).isNotEmpty();
      assertThat(flowSlicesById.keySet()).containsExactlyInAnyOrderElementsOf(m1FlowsById.keySet());
      List<JsonNode> publicCapsules =
          jsonLines(canonicalJson, payload(reopenedPublic, "evidence-capsules.jsonl"));
      assertThat(publicCapsules).isNotEmpty();
      assertThat(publicCapsules).hasSize(flowSlicesById.size());
      Map<String, JsonNode> publicCapsulesByFlow = new HashMap<>();
      publicCapsules.forEach(
          capsule -> {
            String flowSliceId = requiredText(capsule, "flowSliceId");
            assertThat(publicCapsulesByFlow.put(flowSliceId, capsule)).isNull();
            assertThat(flowSlicesById).containsKey(flowSliceId);
            JsonNode m1Flow = m1FlowsById.get(flowSliceId);
            assertThat(m1Flow).isNotNull();
            List<String> m1FactIds = textValues(requiredArray(m1Flow, "factIds"));
            assertThat(m1FactIds).isNotEmpty();
            Map<String, JsonNode> publicFactsById =
                objectsById(requiredArray(capsule, "factViews"), "factId");
            assertThat(publicFactsById).isNotEmpty();
            assertThat(publicFactsById.keySet()).containsExactlyInAnyOrderElementsOf(m1FactIds);
            assertThat(requiredText(capsule, "schemaVersion"))
                .isEqualTo("business-flows-evidence-capsule-v9");
          });
      assertThat(publicCapsulesByFlow.keySet())
          .containsExactlyInAnyOrderElementsOf(flowSlicesById.keySet());

      ReopenedAnalysisStepPublication reopenedFacts =
          fixture.stepArtifacts().reopen(facts.publication());
      VerifiedCanonicalPayload provenFactsPayload = payload(reopenedFacts, "proven-facts.json");
      ArtifactReference provenFactsReference = artifactReference(provenFactsPayload);
      Map<String, JsonNode> provenFactsById =
          objectsById(
              requiredArray(
                  canonicalJson.parseCanonical(provenFactsPayload.canonicalUtf8()), "codeFacts"),
              "factId");
      for (JsonNode capsule : publicCapsules) {
        for (JsonNode factView : requiredArray(capsule, "factViews")) {
          String factId = requiredText(factView, "factId");
          assertThat(provenFactsById).containsKey(factId);
          assertArtifactReference(
              requiredObject(factView, "originFactArtifactRef"), provenFactsReference);
        }
      }

      VerifiedCanonicalPayload gapLedgerPayload = payload(reopenedFacts, "gap-ledger.json");
      ArtifactReference gapLedgerReference = artifactReference(gapLedgerPayload);
      Map<String, JsonNode> sourceGapsById = new HashMap<>();
      requiredArray(canonicalJson.parseCanonical(gapLedgerPayload.canonicalUtf8()), "gaps")
          .forEach(
              gap -> {
                if ("EXTERNAL_EFFECT".equals(requiredText(gap, "kind"))
                    && "DATA_FLOW_BINDING_UNPROVEN".equals(requiredText(gap, "code"))) {
                  assertThat(sourceGapsById.put(requiredText(gap, "gapId"), gap)).isNull();
                }
              });
      assertThat(sourceGapsById).isNotEmpty();

      ReopenedAnalysisStepPublication reopenedGraphs =
          fixture.stepArtifacts().reopen(fixture.programGraphs().publication());
      VerifiedCanonicalPayload evidenceGraphPayload =
          payload(reopenedGraphs, "evidence-graph.json");
      ArtifactReference evidenceGraphReference = artifactReference(evidenceGraphPayload);
      Map<String, JsonNode> evidenceNodesById =
          objectsById(
              requiredArray(
                  canonicalJson.parseCanonical(evidenceGraphPayload.canonicalUtf8()), "nodes"),
              "evidenceNodeId");

      Map<String, JsonNode> m1GapsById =
          objectsById(
              requiredArray(requiredObject(compilerEnvelope, "payload"), "flowGaps"), "gapId");
      List<JsonNode> publicGaps =
          jsonLines(canonicalJson, payload(reopenedPublic, "flow-gaps.jsonl"));
      assertThat(publicGaps).isNotEmpty();
      for (Map.Entry<String, JsonNode> sourceGapEntry : sourceGapsById.entrySet()) {
        String gapId = sourceGapEntry.getKey();
        JsonNode sourceGap = sourceGapEntry.getValue();
        JsonNode m1Copy = m1GapsById.get(gapId);
        assertThat(m1Copy)
            .as("the guarded fixture must carry source Gap %s through M1's entry-only copy", gapId)
            .isNotNull();
        assertThat(requiredArray(m1Copy, "affectedSemanticIds"))
            .isNotEqualTo(requiredArray(sourceGap, "affectedCandidateDenominatorKeys"));

        List<JsonNode> matchingPublicGaps =
            publicGaps.stream()
                .filter(value -> gapId.equals(requiredText(value, "gapId")))
                .toList();
        assertThat(matchingPublicGaps).hasSize(1);
        JsonNode publicGap = matchingPublicGaps.get(0);
        assertThat(requiredText(publicGap, "schemaVersion"))
            .isEqualTo("business-flows-flow-gap-v2");
        assertThat(requiredText(publicGap, "scope")).isEqualTo("FACT");
        assertThat(requiredText(publicGap, "reasonCode"))
            .isEqualTo(requiredText(sourceGap, "code"));
        assertThat(requiredArray(publicGap, "affectedSemanticIds"))
            .isEqualTo(requiredArray(sourceGap, "affectedCandidateDenominatorKeys"));
        assertThat(requiredText(publicGap, "originKind")).isEqualTo("PROVEN_CODE_FACTS_GAP_LEDGER");
        assertArtifactReference(
            requiredObject(publicGap, "originGapLedgerRef"), gapLedgerReference);
        List<String> evidenceNodeIds = textValues(requiredArray(sourceGap, "evidenceNodeIds"));
        assertThat(evidenceNodeIds).isNotEmpty().allMatch(evidenceNodesById::containsKey);
        JsonNode evidenceRefs = requiredArray(publicGap, "evidenceRefs");
        assertThat(evidenceRefs).hasSize(1);
        assertArtifactReference(evidenceRefs.get(0), evidenceGraphReference);
      }
    }
  }

  @Test
  void normalizesCompilerOnlyEntryGapsAlongsideSourceLedgerGaps() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(
            temporaryDirectory.resolve("business-flow-zero-flow-gap-normalization"))) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(),
                  fixture.programGraphs(),
                  facts,
                  zeroFlowProfile());
      assertThat(compilation.entryDispositions()).hasSize(2);
      assertThat(compilation.entryDispositions())
          .allSatisfy(value -> assertThat(value.disposition()).isEqualTo("GAP"));
      assertThat(compilation.flowSlices()).isEmpty();
      assertThat(compilation.flowGaps()).hasSizeGreaterThan(2);

      ModulePublicationReference flowCompilation =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
      ReopenedModulePublication reopenedCompiler =
          fixture.moduleArtifacts().reopen(flowCompilation);
      JsonNode compilerEnvelope =
          canonicalJson.parseCanonical(reopenedCompiler.payloads().get(0).canonicalUtf8());
      JsonNode compilerPayload = requiredObject(compilerEnvelope, "payload");
      Map<String, JsonNode> m1DispositionsByEntry =
          objectsById(requiredArray(compilerPayload, "entryDispositions"), "entryId");
      Map<String, JsonNode> m1GapsById =
          objectsById(requiredArray(compilerPayload, "flowGaps"), "gapId");
      assertThat(m1DispositionsByEntry).hasSize(2);
      assertThat(requiredArray(compilerPayload, "flowSlices")).isEmpty();

      ReopenedAnalysisStepPublication reopenedFacts =
          fixture.stepArtifacts().reopen(facts.publication());
      VerifiedCanonicalPayload gapLedgerPayload = payload(reopenedFacts, "gap-ledger.json");
      JsonNode gapLedger = canonicalJson.parseCanonical(gapLedgerPayload.canonicalUtf8());
      Map<String, JsonNode> sourceGapsById = objectsById(requiredArray(gapLedger, "gaps"), "gapId");
      Set<String> sourceGapIds = sourceGapsById.keySet();
      Set<String> compilerOnlyGapIds = new HashSet<>();
      m1GapsById.values().stream()
          .filter(value -> "ENTRY".equals(requiredText(value, "scope")))
          .forEach(value -> compilerOnlyGapIds.add(requiredText(value, "gapId")));
      assertThat(compilerOnlyGapIds).hasSize(2);
      assertThat(compilerOnlyGapIds).doesNotContainAnyElementsOf(sourceGapIds);
      Set<String> expectedM1GapIds = new HashSet<>(sourceGapIds);
      expectedM1GapIds.addAll(compilerOnlyGapIds);
      assertThat(m1GapsById.keySet()).containsExactlyInAnyOrderElementsOf(expectedM1GapIds);
      m1DispositionsByEntry
          .values()
          .forEach(
              disposition -> {
                List<String> dispositionGapIds = textValues(requiredArray(disposition, "gapIds"));
                assertThat(dispositionGapIds).hasSize(1);
                assertThat(compilerOnlyGapIds).contains(dispositionGapIds.get(0));
                assertThat(
                        textValues(
                            requiredArray(
                                m1GapsById.get(dispositionGapIds.get(0)), "affectedSemanticIds")))
                    .containsExactly(requiredText(disposition, "entryId"));
              });
      assertThat(
              m1DispositionsByEntry.values().stream()
                  .flatMap(disposition -> textValues(requiredArray(disposition, "gapIds")).stream())
                  .collect(java.util.stream.Collectors.toSet()))
          .containsExactlyInAnyOrderElementsOf(compilerOnlyGapIds);

      CapsuleProjection projection =
          new EvidenceCapsuleProjector(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .project(
                  flowCompilation,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  capsuleProfile());
      assertThat(projection.capsules()).isEmpty();
      ModulePublicationReference capsuleProjection =
          new CapsuleProjectionModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .publish(
                  flowCompilation,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  projection);
      ReopenedModulePublication reopenedCapsule =
          fixture.moduleArtifacts().reopen(capsuleProjection);
      JsonNode capsuleEnvelope =
          canonicalJson.parseCanonical(reopenedCapsule.payloads().get(0).canonicalUtf8());
      assertThat(requiredArray(requiredObject(capsuleEnvelope, "payload"), "capsules")).isEmpty();

      BusinessFlowsReference businessFlows =
          assertDoesNotThrow(
              () ->
                  new FlowPublicationSpecifier(
                          fixture.moduleArtifacts(),
                          fixture.stepArtifacts(),
                          fixture.sourceReader())
                      .specify(
                          fixture.sourceInventory(),
                          fixture.applicationDiscovery(),
                          fixture.programGraphs(),
                          facts,
                          flowCompilation,
                          capsuleProjection),
              "M3 must normalize compiler-only ENTRY Gaps without dropping source provenance");
      ReopenedAnalysisStepPublication reopenedPublic =
          fixture.stepArtifacts().reopen(businessFlows.publication());
      assertThat(reopenedPublic.semanticPayloads()).hasSize(5);

      Map<String, JsonNode> publicEntriesById =
          objectsById(
              jsonLines(canonicalJson, payload(reopenedPublic, "entry-dispositions.jsonl")),
              "entryId");
      assertThat(publicEntriesById.keySet())
          .containsExactlyInAnyOrderElementsOf(m1DispositionsByEntry.keySet());
      JsonNode publicFlowSlices =
          canonicalJson.parseCanonical(payload(reopenedPublic, "flow-slices.json").canonicalUtf8());
      assertThat(requiredArray(publicFlowSlices, "flowSlices")).isEmpty();
      assertThat(jsonLines(canonicalJson, payload(reopenedPublic, "evidence-capsules.jsonl")))
          .isEmpty();

      Map<String, JsonNode> publicGapsById =
          objectsById(
              jsonLines(canonicalJson, payload(reopenedPublic, "flow-gaps.jsonl")), "gapId");
      assertThat(publicGapsById.keySet()).containsExactlyInAnyOrderElementsOf(expectedM1GapIds);
      ArtifactReference gapLedgerReference = artifactReference(gapLedgerPayload);
      ReopenedAnalysisStepPublication reopenedGraphs =
          fixture.stepArtifacts().reopen(fixture.programGraphs().publication());
      ArtifactReference evidenceGraphReference =
          artifactReference(payload(reopenedGraphs, "evidence-graph.json"));
      for (Map.Entry<String, JsonNode> sourceGapEntry : sourceGapsById.entrySet()) {
        JsonNode sourceGap = sourceGapEntry.getValue();
        JsonNode publicGap = publicGapsById.get(sourceGapEntry.getKey());
        assertThat(publicGap).isNotNull();
        assertThat(requiredText(publicGap, "scope")).isEqualTo("FACT");
        assertThat(requiredText(publicGap, "reasonCode"))
            .isEqualTo(requiredText(sourceGap, "code"));
        assertThat(requiredArray(publicGap, "affectedSemanticIds"))
            .isEqualTo(requiredArray(sourceGap, "affectedCandidateDenominatorKeys"));
        assertThat(requiredText(publicGap, "originKind")).isEqualTo("PROVEN_CODE_FACTS_GAP_LEDGER");
        assertArtifactReference(
            requiredObject(publicGap, "originGapLedgerRef"), gapLedgerReference);
        JsonNode evidenceRefs = requiredArray(publicGap, "evidenceRefs");
        if (requiredArray(sourceGap, "evidenceNodeIds").isEmpty()) {
          assertThat(evidenceRefs).isEmpty();
        } else {
          assertThat(evidenceRefs).hasSize(1);
          assertArtifactReference(evidenceRefs.get(0), evidenceGraphReference);
        }
      }
      for (String compilerOnlyGapId : compilerOnlyGapIds) {
        JsonNode compilerGap = m1GapsById.get(compilerOnlyGapId);
        JsonNode publicGap = publicGapsById.get(compilerOnlyGapId);
        assertThat(publicGap).isNotNull();
        assertThat(requiredText(publicGap, "scope")).isEqualTo("FLOW");
        assertThat(requiredText(publicGap, "reasonCode"))
            .isEqualTo(requiredText(compilerGap, "reasonCode"));
        assertThat(requiredArray(publicGap, "affectedSemanticIds"))
            .isEqualTo(requiredArray(compilerGap, "affectedSemanticIds"));
        assertThat(requiredText(publicGap, "originKind")).isEqualTo("FLOW_COMPILATION");
        assertThat(publicGap.get("originGapLedgerRef")).isNotNull();
        assertThat(publicGap.get("originGapLedgerRef").isNull()).isTrue();
        assertThat(requiredArray(publicGap, "evidenceRefs")).isEmpty();
      }

      JsonNode publicCoverage =
          canonicalJson.parseCanonical(
              payload(reopenedPublic, "flow-coverage.json").canonicalUtf8());
      assertThat(textValues(requiredArray(publicCoverage, "entryIds")))
          .containsExactlyInAnyOrderElementsOf(m1DispositionsByEntry.keySet());
      assertThat(textValues(requiredArray(publicCoverage, "compiledEntryIds"))).isEmpty();
      assertThat(textValues(requiredArray(publicCoverage, "gappedEntryIds")))
          .containsExactlyInAnyOrderElementsOf(m1DispositionsByEntry.keySet());
      assertThat(textValues(requiredArray(publicCoverage, "flowSliceIds"))).isEmpty();
      assertThat(textValues(requiredArray(publicCoverage, "capsuleIds"))).isEmpty();
      assertThat(textValues(requiredArray(publicCoverage, "gapIds")))
          .containsExactlyInAnyOrderElementsOf(expectedM1GapIds);
    }
  }

  @Test
  void rejectsSelfConsistentRehashedFactMutationBeforeStep05Receipt() {
    Path rootA = temporaryDirectory.resolve("business-flow-replay-root-a");
    Path rootB = temporaryDirectory.resolve("business-flow-replay-root-b");
    try (ProgramGraphsPublicFixture fixtureA =
            ProgramGraphsPublicFixture.createWithGuardedApprove(rootA);
        ProgramGraphsPublicFixture fixtureB =
            ProgramGraphsPublicFixture.createWithGuardedApprove(rootB)) {
      ProvenCodeFactsReference factsA = publishProvenFacts(fixtureA);
      ProvenCodeFactsReference factsB = publishProvenFacts(fixtureB);
      assertThat(fixtureA.artifactControls()).isEqualTo(fixtureB.artifactControls());
      assertThat(fixtureA.sourceInventory().publication())
          .isEqualTo(fixtureB.sourceInventory().publication());
      assertThat(fixtureA.applicationDiscovery().publication())
          .isEqualTo(fixtureB.applicationDiscovery().publication());
      assertThat(fixtureA.programGraphs().publication())
          .isEqualTo(fixtureB.programGraphs().publication());
      assertThat(factsA.publication()).isEqualTo(factsB.publication());

      FlowCompilation compilationA =
          new EntryRootedFlowCompiler(fixtureA.stepArtifacts())
              .compile(
                  fixtureA.applicationDiscovery(), fixtureA.programGraphs(), factsA, flowProfile());
      FlowCompilation compilationB =
          new EntryRootedFlowCompiler(fixtureB.stepArtifacts())
              .compile(
                  fixtureB.applicationDiscovery(), fixtureB.programGraphs(), factsB, flowProfile());
      ModulePublicationReference flowA =
          new FlowCompilationModulePublisher(fixtureA.moduleArtifacts(), fixtureA.stepArtifacts())
              .publish(
                  fixtureA.applicationDiscovery(), fixtureA.programGraphs(), factsA, compilationA);
      ModulePublicationReference flowB =
          new FlowCompilationModulePublisher(fixtureB.moduleArtifacts(), fixtureB.stepArtifacts())
              .publish(
                  fixtureB.applicationDiscovery(), fixtureB.programGraphs(), factsB, compilationB);
      assertThat(flowA).isEqualTo(flowB);

      CapsuleProjection projectionA =
          new EvidenceCapsuleProjector(
                  fixtureA.moduleArtifacts(), fixtureA.stepArtifacts(), fixtureA.sourceReader())
              .project(
                  flowA,
                  fixtureA.sourceInventory(),
                  fixtureA.programGraphs(),
                  factsA,
                  capsuleProfile());
      ModulePublicationReference capsuleA =
          new CapsuleProjectionModulePublisher(
                  fixtureA.moduleArtifacts(), fixtureA.stepArtifacts(), fixtureA.sourceReader())
              .publish(
                  flowA, fixtureA.sourceInventory(), fixtureA.programGraphs(), factsA, projectionA);
      ReopenedModulePublication originalM2 = fixtureA.moduleArtifacts().reopen(capsuleA);
      VerifiedCanonicalPayload originalPayload = originalM2.payloads().get(0);

      String runId = fixtureB.sourceInventory().publication().address().runId().value();
      assertThat(
              Files.exists(
                  businessFlowsArtifactPath(rootB, runId, "modules", "02-capsule-projector"),
                  LinkOption.NOFOLLOW_LINKS))
          .as("Root B must not have an M2 publication before the forged install")
          .isFalse();
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalModulePayload changedPayload = rehashOneFactAtom(originalPayload, canonicalJson);
      var changedInstallation =
          fixtureB
              .moduleArtifacts()
              .install(
                  new ModuleInstallRequest(
                      originalM2.receipt().address(),
                      originalM2.receipt().moduleVersion(),
                      originalM2.receipt().upstreamArtifacts(),
                      originalM2.receipt().controls(),
                      originalM2.receipt().status(),
                      originalM2.receipt().gapRefs(),
                      List.of(changedPayload)));
      ReopenedModulePublication changedM2 =
          fixtureB.moduleArtifacts().reopen(changedInstallation.reference());
      assertThat(changedM2.payloads().get(0).canonicalUtf8())
          .isEqualTo(changedPayload.canonicalUtf8());
      assertThat(changedM2.receipt().address()).isEqualTo(originalM2.receipt().address());
      assertThat(changedM2.receipt().moduleVersion())
          .isEqualTo(originalM2.receipt().moduleVersion());
      assertThat(changedM2.receipt().upstreamArtifacts())
          .isEqualTo(originalM2.receipt().upstreamArtifacts());
      assertThat(changedM2.receipt().controls()).isEqualTo(originalM2.receipt().controls());
      assertThat(changedM2.receipt().status()).isEqualTo(originalM2.receipt().status());
      assertThat(changedM2.receipt().gapRefs()).isEqualTo(originalM2.receipt().gapRefs());
      assertThat(changedM2.payloads().get(0).descriptor().artifactId())
          .isNotEqualTo(originalPayload.descriptor().artifactId());
      assertThat(changedM2.payloads().get(0).descriptor().sha256())
          .isNotEqualTo(originalPayload.descriptor().sha256());
      assertThat(changedM2.reference().moduleArtifactRoot())
          .isNotEqualTo(originalM2.reference().moduleArtifactRoot());

      Path receiptPath = businessFlowsArtifactPath(rootB, runId, "business-flows-receipt.json");
      Throwable replayFailure =
          catchThrowable(
              () ->
                  new FlowPublicationSpecifier(
                          fixtureB.moduleArtifacts(),
                          fixtureB.stepArtifacts(),
                          fixtureB.sourceReader())
                      .specify(
                          fixtureB.sourceInventory(),
                          fixtureB.applicationDiscovery(),
                          fixtureB.programGraphs(),
                          factsB,
                          flowB,
                          changedInstallation.reference()));
      assertThat(replayFailure)
          .as("M3 must reject a generic-valid but semantically stale M2 projection")
          .isInstanceOf(FlowPublicationException.class);
      assertThat(replayFailure.getMessage()).isNotBlank();
      assertThat(Files.exists(receiptPath, LinkOption.NOFOLLOW_LINKS))
          .as("semantic replay failure must not leave the Step 05 receipt")
          .isFalse();
      assertThat(
              Files.exists(
                  businessFlowsArtifactPath(rootB, runId, "modules", "03-publish"),
                  LinkOption.NOFOLLOW_LINKS))
          .as("semantic replay failure must not install the Step 05 module")
          .isFalse();
    }
  }

  private static void assertArtifactReference(JsonNode actual, ArtifactReference expected) {
    assertThat(requiredText(actual, "artifactId")).isEqualTo(expected.artifactId().value());
    assertThat(requiredText(actual, "sha256")).isEqualTo(expected.sha256().value());
  }

  private static VerifiedCanonicalPayload payload(
      ReopenedAnalysisStepPublication publication, String fileName) {
    return publication.semanticPayloads().stream()
        .filter(value -> fileName.equals(value.descriptor().fileName()))
        .findFirst()
        .orElseThrow();
  }

  private static ArtifactReference artifactReference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static List<JsonNode> jsonLines(
      CanonicalJsonCodec canonicalJson, VerifiedCanonicalPayload payload) {
    return new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8)
        .lines()
        .filter(value -> !value.isBlank())
        .map(
            value ->
                canonicalJson.parseCanonical(
                    ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8))))
        .toList();
  }

  private static List<String> textValues(JsonNode values) {
    assertThat(values.isArray()).isTrue();
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.asText()));
    return List.copyOf(result);
  }

  private static CanonicalModulePayload rehashOneFactAtom(
      VerifiedCanonicalPayload original, CanonicalJsonCodec canonicalJson) {
    ObjectNode envelope = (ObjectNode) canonicalJson.parseCanonical(original.canonicalUtf8());
    ObjectNode payload = (ObjectNode) requiredObject(envelope, "payload");
    int changed = 0;
    outer:
    for (JsonNode capsule : requiredArray(payload, "capsules")) {
      for (JsonNode factView : requiredArray(capsule, "factViews")) {
        for (JsonNode atom : requiredArray(factView, "atoms")) {
          ObjectNode value = (ObjectNode) requiredObject(atom, "value");
          String originalCanonical = requiredText(value, "canonical");
          value.put("canonical", originalCanonical + "-replayed");
          changed++;
          break outer;
        }
      }
    }
    assertThat(changed).as("the guarded fixture must contain one owning Fact atom").isEqualTo(1);
    payload.remove("capsuleProjectionId");
    payload.put(
        "capsuleProjectionId",
        "business-flows-capsule-projection:"
            + sha256Hex(
                frame("business-flows-capsule-projection-id-v2"),
                frame(canonicalJson.encodeCanonical(payload).copyToByteArray())));

    ObjectNode withoutArtifactId = envelope.deepCopy();
    withoutArtifactId.remove("artifactId");
    String artifactId =
        "business-flows-capsule-projection:"
            + sha256Hex(
                frame("canonical-module-artifact-id-v1"),
                frame(requiredText(envelope, "schemaVersion")),
                frame(requiredText(envelope, "artifactType")),
                frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray()));
    envelope.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        original.descriptor().fileName(),
        original.descriptor().artifactType(),
        original.descriptor().schemaVersion(),
        ArtifactId.parse(artifactId),
        original.descriptor().mediaType(),
        canonicalJson.encodeCanonical(envelope));
  }

  private static Path businessFlowsArtifactPath(Path root, String runId, String... segments) {
    Path result =
        root.resolve("runs")
            .resolve(runId.replace(":", "--"))
            .resolve("steps")
            .resolve("05-business-flows");
    for (String segment : segments) {
      result = result.resolve(segment);
    }
    return result;
  }

  private static String sha256Hex(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static void assertFactAtoms(JsonNode actualAtoms, JsonNode provenAtoms) {
    Map<String, JsonNode> actualById = objectsById(actualAtoms, "atomId");
    Map<String, JsonNode> provenById = objectsById(provenAtoms, "atomId");
    assertThat(actualById.keySet()).containsExactlyInAnyOrderElementsOf(provenById.keySet());
    for (Map.Entry<String, JsonNode> entry : actualById.entrySet()) {
      JsonNode actual = entry.getValue();
      JsonNode proven = provenById.get(entry.getKey());
      assertThat(requiredText(actual, "role")).isEqualTo(requiredText(proven, "role"));
      assertThat(requiredText(actual, "name")).isEqualTo(requiredText(proven, "name"));
      JsonNode actualValue = requiredObject(actual, "value");
      JsonNode provenValue = requiredObject(proven, "value");
      assertThat(requiredText(actualValue, "type")).isEqualTo(requiredText(provenValue, "type"));
      assertThat(requiredText(actualValue, "canonical"))
          .isEqualTo(requiredText(provenValue, "canonical"));
      assertThat(requiredText(actual, "proofId")).isEqualTo(requiredText(proven, "proofId"));
    }
  }

  private static void assertTextArray(JsonNode actual, JsonNode expected) {
    assertThat(actual).isEqualTo(expected);
  }

  private static ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture) {
    FactCandidateInputs inputs =
        new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
            .reopen(
                fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
    FactCandidateSet candidates =
        new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaFacts());
    ProofDecisionSet decisions =
        new AtomicProofBuilder(fixture.sourceReader())
            .prove(
                candidates,
                inputs,
                fixture.sourceInventory(),
                ProofRuleRegistry.standardJavaBoundary());
    var candidatePublication =
        new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
            .publish(address(fixture, 1, "candidates"), inputs, candidates);
    var proofPublication =
        new ProofDecisionSetModulePublisher(fixture.moduleArtifacts())
            .publish(address(fixture, 2, "proofs"), inputs, candidatePublication, decisions);
    return new FactLedgerPublicationSpecifier(fixture.moduleArtifacts(), fixture.stepArtifacts())
        .specifyCandidatesAndProofs(
            inputs,
            candidatePublication,
            proofPublication,
            fixture.sourceInventory(),
            fixture.applicationDiscovery(),
            fixture.programGraphs());
  }

  private static FlowCompilationProfile flowProfile() {
    return new FlowCompilationProfile(
        reference("flow-profile", "business-flow-provenance-flow"), 16, 8, 64, 96, 32, 64, 256);
  }

  private static FlowCompilationProfile zeroFlowProfile() {
    return new FlowCompilationProfile(
        reference("flow-profile", "business-flow-provenance-zero-flow"), 16, 8, 1, 96, 32, 64, 256);
  }

  private static CapsuleProjectionProfile capsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", "business-flow-provenance-capsule"), 16, 32, 4_096, 24_576);
  }

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest(value)), new Sha256Digest(digest(value + "-bytes")));
  }

  private static Map<String, JsonNode> objectsById(JsonNode values, String idField) {
    assertThat(values.isArray()).isTrue();
    Map<String, JsonNode> result = new HashMap<>();
    values.forEach(
        value -> {
          assertThat(value.isObject()).isTrue();
          String id = requiredText(value, idField);
          assertThat(result.put(id, value)).isNull();
        });
    return Map.copyOf(result);
  }

  private static Map<String, JsonNode> objectsById(List<JsonNode> values, String idField) {
    Map<String, JsonNode> result = new HashMap<>();
    values.forEach(
        value -> {
          assertThat(value.isObject()).isTrue();
          String id = requiredText(value, idField);
          assertThat(result.put(id, value)).isNull();
        });
    return Map.copyOf(result);
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

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
