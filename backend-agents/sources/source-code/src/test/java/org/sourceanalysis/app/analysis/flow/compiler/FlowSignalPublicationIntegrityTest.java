package org.sourceanalysis.app.analysis.flow.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Regression seam for M1 signal ownership, accounting, and receipt-last publication. */
class FlowSignalPublicationIntegrityTest {

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsForeignBasisAndAnchorMutationsBeforeAnyInstallThenPublishesOriginalUnchanged()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("flow-signal-integrity"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation original =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(fixture.applicationDiscovery(), fixture.programGraphs(), facts, profile());

      assertThat(original.flowSlices()).hasSize(2);
      FlowCompilation.FlowSlice firstFlow = original.flowSlices().get(0);
      FlowCompilation.FlowSlice otherFlow = original.flowSlices().get(1);
      FlowCompilation.ProcessJoinSignalV1 firstCall = explicitCall(firstFlow);
      FlowCompilation.ProcessJoinSignalV1 otherCall = explicitCall(otherFlow);

      FlowCompilation.ProcessJoinSignalV1 foreignBasis =
          new FlowCompilation.ProcessJoinSignalV1(
              null,
              firstCall.flowSliceId(),
              firstCall.signalKind(),
              firstCall.anchorKind(),
              firstCall.anchorKey(),
              firstCall.direction(),
              firstCall.specificity(),
              firstCall.claimScope(),
              otherCall.factIds(),
              otherCall.atomIds(),
              otherCall.proofIds(),
              otherCall.evidenceNodeIds(),
              otherCall.sourceLocators(),
              otherCall.gapIds());
      FlowCompilation foreignBasisCompilation =
          withSignal(firstFlow, firstCall.processJoinSignalId(), foreignBasis, original);

      FlowCompilation.ProcessJoinSignalV1 changedAnchor =
          new FlowCompilation.ProcessJoinSignalV1(
              null,
              firstCall.flowSliceId(),
              firstCall.signalKind(),
              firstCall.anchorKind(),
              firstCall.anchorKey() + ":mutated",
              firstCall.direction(),
              firstCall.specificity(),
              firstCall.claimScope(),
              firstCall.factIds(),
              firstCall.atomIds(),
              firstCall.proofIds(),
              firstCall.evidenceNodeIds(),
              firstCall.sourceLocators(),
              firstCall.gapIds());
      FlowCompilation changedAnchorCompilation =
          withSignal(firstFlow, firstCall.processJoinSignalId(), changedAnchor, original);

      assertThat(foreignBasis.processJoinSignalId()).isNotEqualTo(firstCall.processJoinSignalId());
      assertThat(changedAnchor.processJoinSignalId()).isNotEqualTo(firstCall.processJoinSignalId());
      assertPreservesNonSignalContent(original, foreignBasisCompilation);
      assertPreservesNonSignalContent(original, changedAnchorCompilation);

      FlowCompilationModulePublisher publisher =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts());
      assertRejected(publisher, fixture, facts, foreignBasisCompilation);
      assertRejected(publisher, fixture, facts, changedAnchorCompilation);

      ModulePublicationReference reference =
          publisher.publish(
              fixture.applicationDiscovery(), fixture.programGraphs(), facts, original);
      assertThat(reference.address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  fixture.programGraphs().publication().address().runId(),
                  AnalysisStepKey.BUSINESS_FLOWS,
                  1,
                  "flow-compiler"));
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(reference);
      assertOriginalPayload(reopened, original);
    }
  }

  private static void assertRejected(
      FlowCompilationModulePublisher publisher,
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      FlowCompilation candidate) {
    assertThatThrownBy(
            () ->
                publisher.publish(
                    fixture.applicationDiscovery(), fixture.programGraphs(), facts, candidate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("FLOW_ACCOUNTING_INVARIANT_BROKEN");
  }

  private static FlowCompilation withSignal(
      FlowCompilation.FlowSlice target,
      String replacedSignalId,
      FlowCompilation.ProcessJoinSignalV1 replacement,
      FlowCompilation original) {
    return withSignals(
        target,
        target.processJoinSignals().stream()
            .map(
                signal ->
                    signal.processJoinSignalId().equals(replacedSignalId) ? replacement : signal)
            .toList(),
        original);
  }

  private static FlowCompilation withSignals(
      FlowCompilation.FlowSlice target,
      List<FlowCompilation.ProcessJoinSignalV1> signals,
      FlowCompilation original) {
    List<FlowCompilation.FlowSlice> flows =
        original.flowSlices().stream()
            .map(
                flow ->
                    flow.flowSliceId().equals(target.flowSliceId()) ? copy(flow, signals) : flow)
            .toList();
    return new FlowCompilation(
        original.profile(),
        original.entryDispositions(),
        flows,
        original.entryContexts(),
        original.flowGaps());
  }

  private static FlowCompilation.FlowSlice copy(
      FlowCompilation.FlowSlice flow, List<FlowCompilation.ProcessJoinSignalV1> signals) {
    return new FlowCompilation.FlowSlice(
        flow.flowSliceId(),
        flow.entryId(),
        flow.trigger(),
        flow.rootNodeId(),
        flow.sharedSteps(),
        flow.factIds(),
        flow.atomIds(),
        flow.outcomePaths(),
        flow.gapIds(),
        signals);
  }

  private static FlowCompilation.ProcessJoinSignalV1 explicitCall(FlowCompilation.FlowSlice flow) {
    return flow.processJoinSignals().stream()
        .filter(signal -> "EXPLICIT_CALL".equals(signal.signalKind()))
        .findFirst()
        .orElseThrow();
  }

  private static void assertPreservesNonSignalContent(
      FlowCompilation expected, FlowCompilation actual) {
    assertThat(actual.profile()).isEqualTo(expected.profile());
    assertThat(actual.entryDispositions()).isEqualTo(expected.entryDispositions());
    assertThat(actual.flowGaps()).isEqualTo(expected.flowGaps());
    Map<String, FlowCompilation.FlowSlice> expectedById =
        expected.flowSlices().stream()
            .collect(Collectors.toMap(FlowCompilation.FlowSlice::flowSliceId, Function.identity()));
    for (FlowCompilation.FlowSlice actualFlow : actual.flowSlices()) {
      FlowCompilation.FlowSlice expectedFlow = expectedById.get(actualFlow.flowSliceId());
      assertThat(expectedFlow).isNotNull();
      assertThat(actualFlow.entryId()).isEqualTo(expectedFlow.entryId());
      assertThat(actualFlow.trigger()).isEqualTo(expectedFlow.trigger());
      assertThat(actualFlow.rootNodeId()).isEqualTo(expectedFlow.rootNodeId());
      assertThat(actualFlow.sharedSteps()).isEqualTo(expectedFlow.sharedSteps());
      assertThat(actualFlow.factIds()).isEqualTo(expectedFlow.factIds());
      assertThat(actualFlow.atomIds()).isEqualTo(expectedFlow.atomIds());
      assertThat(actualFlow.outcomePaths()).isEqualTo(expectedFlow.outcomePaths());
      assertThat(actualFlow.gapIds()).isEqualTo(expectedFlow.gapIds());
    }
  }

  private static void assertOriginalPayload(
      ReopenedModulePublication reopened, FlowCompilation expected) {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    JsonNode envelope = canonicalJson.parseCanonical(reopened.payloads().get(0).canonicalUtf8());
    assertThat(envelope.path("schemaVersion").asText())
        .isEqualTo("business-flows-flow-compilation-v5");
    JsonNode payload = envelope.path("payload");
    assertThat(payload.path("flowCompilationId").asText())
        .isEqualTo(expectedFlowCompilationId(expected));
    assertThat(payload.path("entryDispositions")).hasSize(expected.entryDispositions().size());
    assertThat(payload.path("flowGaps")).hasSize(expected.flowGaps().size());
    assertThat(payload.path("flowSlices")).hasSize(expected.flowSlices().size());

    Map<String, FlowCompilation.FlowSlice> expectedById =
        expected.flowSlices().stream()
            .collect(Collectors.toMap(FlowCompilation.FlowSlice::flowSliceId, Function.identity()));
    for (JsonNode serializedFlow : payload.path("flowSlices")) {
      FlowCompilation.FlowSlice expectedFlow =
          expectedById.get(serializedFlow.path("flowSliceId").asText());
      assertThat(expectedFlow).isNotNull();
      assertThat(serializedFlow.path("entryId").asText()).isEqualTo(expectedFlow.entryId());
      assertThat(serializedFlow.path("trigger").asText()).isEqualTo(expectedFlow.trigger());
      assertThat(serializedFlow.path("rootNodeId").asText()).isEqualTo(expectedFlow.rootNodeId());
      assertThat(jsonStrings(serializedFlow.path("sharedSteps")))
          .containsExactlyElementsOf(expectedFlow.sharedSteps());
      assertThat(jsonStrings(serializedFlow.path("factIds")))
          .containsExactlyElementsOf(expectedFlow.factIds());
      assertThat(jsonStrings(serializedFlow.path("atomIds")))
          .containsExactlyElementsOf(expectedFlow.atomIds());
      assertThat(jsonStrings(serializedFlow.path("gapIds")))
          .containsExactlyElementsOf(expectedFlow.gapIds());

      ArrayNode expectedOutcomes = JsonNodeFactory.instance.arrayNode();
      expectedFlow
          .outcomePaths()
          .forEach(outcome -> outcomeNode(expectedOutcomes.addObject(), outcome));
      assertThat(
              canonicalJson.encodeCanonical(serializedFlow.path("outcomePaths")).copyToByteArray())
          .containsExactly(canonicalJson.encodeCanonical(expectedOutcomes).copyToByteArray());

      ArrayNode expectedSignals = JsonNodeFactory.instance.arrayNode();
      expectedFlow
          .processJoinSignals()
          .forEach(signal -> signalNode(expectedSignals.addObject(), signal));
      assertThat(
              canonicalJson
                  .encodeCanonical(serializedFlow.path("processJoinSignals"))
                  .copyToByteArray())
          .containsExactly(canonicalJson.encodeCanonical(expectedSignals).copyToByteArray());
    }

    List<String> expectedSignalIds =
        expected.flowSlices().stream()
            .flatMap(flow -> flow.processJoinSignals().stream())
            .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
            .sorted()
            .toList();
    assertThat(jsonStrings(payload.path("coverage").path("processJoinSignalIds")))
        .containsExactlyElementsOf(expectedSignalIds);
  }

  private static ObjectNode signalNode(
      ObjectNode node, FlowCompilation.ProcessJoinSignalV1 signal) {
    node.put("processJoinSignalId", signal.processJoinSignalId());
    node.put("flowSliceId", signal.flowSliceId());
    node.put("signalKind", signal.signalKind());
    node.put("anchorKind", signal.anchorKind());
    node.put("anchorKey", signal.anchorKey());
    node.put("direction", signal.direction());
    node.put("specificity", signal.specificity());
    node.put("claimScope", signal.claimScope());
    strings(node.putArray("factIds"), signal.factIds());
    strings(node.putArray("atomIds"), signal.atomIds());
    strings(node.putArray("proofIds"), signal.proofIds());
    strings(node.putArray("evidenceNodeIds"), signal.evidenceNodeIds());
    ArrayNode locators = node.putArray("sourceLocators");
    signal.sourceLocators().forEach(locator -> locator(locators.addObject(), locator));
    strings(node.putArray("gapIds"), signal.gapIds());
    return node;
  }

  private static void outcomeNode(ObjectNode node, FlowCompilation.OutcomePath outcome) {
    node.put("outcomePathId", outcome.outcomePathId());
    ArrayNode decisions = node.putArray("decisions");
    outcome
        .decisions()
        .forEach(
            decision ->
                decisions
                    .addObject()
                    .put("guardNodeId", decision.guardNodeId())
                    .put("conditionAtomId", decision.conditionAtomId())
                    .put("polarity", decision.polarity())
                    .put("normalizedCondition", decision.normalizedCondition()));
    node.put("terminalNodeId", outcome.terminalNodeId());
    node.put("terminalKind", outcome.terminalKind());
    strings(node.putArray("terminalFactIds"), outcome.terminalFactIds());
    strings(node.putArray("requiredAtomIds"), outcome.requiredAtomIds());
    strings(node.putArray("requiredProofIds"), outcome.requiredProofIds());
  }

  private static void locator(ObjectNode node, SourceLocatorV1 locator) {
    node.put("fileId", locator.fileId().value());
    node.put("path", locator.path());
    node.put("startByte", locator.startByte());
    node.put("endByteExclusive", locator.endByteExclusive());
    node.put("startLine", locator.startLine());
    node.put("startColumn", locator.startColumn());
    node.put("endLine", locator.endLine());
    node.put("endColumn", locator.endColumn());
  }

  private static List<String> jsonStrings(JsonNode values) {
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
  }

  private static void strings(ArrayNode node, List<String> values) {
    values.forEach(node::add);
  }

  private static String expectedFlowCompilationId(FlowCompilation compilation) {
    List<String> values = new ArrayList<>();
    values.add(compilation.profile().profileRef().artifactId().value());
    values.add(compilation.profile().profileRef().sha256().value());
    compilation
        .entryDispositions()
        .forEach(
            value -> {
              values.add(value.entryId());
              values.add(value.disposition());
              values.add(value.flowSliceId() == null ? "" : value.flowSliceId());
              values.add(value.reasonCode() == null ? "" : value.reasonCode());
              values.addAll(value.gapIds());
            });
    compilation
        .flowGaps()
        .forEach(
            value -> {
              values.add(value.gapId());
              values.add(value.scope());
              values.add(value.reasonCode());
              values.addAll(value.affectedEntryIds());
              values.addAll(value.evidenceNodeIds());
            });
    compilation.flowSlices().stream()
        .flatMap(flow -> flow.processJoinSignals().stream())
        .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
        .sorted()
        .forEach(values::add);
    return contentId("flow-compilation", values.toArray(String[]::new));
  }

  private static String contentId(String prefix, String... values) {
    byte[][] framed = new byte[values.length + 1][];
    framed[0] = frame(prefix);
    for (int index = 0; index < values.length; index++) {
      framed[index + 1] = frame(values[index]);
    }
    return prefix + ":" + sha256(framed);
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) digest.update(value);
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture) {
    FactCandidateInputs inputs =
        new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
            .reopen(
                fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
    FactCandidateSet candidates =
        new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
    ProofDecisionSet decisions =
        new AtomicProofBuilder(fixture.sourceReader())
            .prove(
                candidates,
                inputs,
                fixture.sourceInventory(),
                ProofRuleRegistry.standardJavaBoundary());
    ModulePublicationReference candidatePublication =
        new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
            .publish(address(fixture, 1, "candidates"), inputs, candidates);
    ModulePublicationReference proofPublication =
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

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static FlowCompilationProfile profile() {
    return new FlowCompilationProfile(
        new ArtifactReference(
            ArtifactId.parse("flow-profile:" + digest("signal-integrity-profile")),
            new Sha256Digest(digest("signal-integrity-profile-bytes"))),
        16,
        8,
        64,
        96,
        32,
        64,
        256);
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
