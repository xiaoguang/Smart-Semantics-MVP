package org.sourceanalysis.app.analysis.flow.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/**
 * M1 persistence seam: Flow compilation must be a canonical module artifact before M2 can use it.
 */
class FlowCompilationModulePublisherTest {

  private static final String PUBLISHER_CLASS =
      "org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher";

  @TempDir Path temporaryDirectory;

  @Test
  void installsOneCanonicalFlowCompilationArtifactWithTheExactPublicPredecessorSet()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("flow-module-graphs"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(fixture.applicationDiscovery(), fixture.programGraphs(), facts, profile());

      ModulePublicationReference reference = publish(fixture, facts, compilation);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(reference);

      assertThat(reopened.receipt().address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  fixture.programGraphs().publication().address().runId(),
                  AnalysisStepKey.BUSINESS_FLOWS,
                  1,
                  "flow-compiler"));
      assertThat(reopened.payloads()).hasSize(1);
      assertThat(reopened.payloads().get(0).descriptor().fileName())
          .isEqualTo("flow-compilation.json");
      assertThat(reopened.payloads().get(0).descriptor().artifactType())
          .isEqualTo("BUSINESS_FLOWS_FLOW_COMPILATION");
      assertThat(reopened.payloads().get(0).descriptor().schemaVersion())
          .isEqualTo("business-flows-flow-compilation-v5");
      assertThat(reopened.receipt().upstreamArtifacts()).hasSize(13);
    }
  }

  @Test
  void persistsFreshReopenedProcessJoinSignalsAndSignalBoundFlowCompilationIdentity()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("flow-module-signals"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(fixture.applicationDiscovery(), fixture.programGraphs(), facts, profile());

      ModulePublicationReference reference = publish(fixture, facts, compilation);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(reference);
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      JsonNode envelope = canonicalJson.parseCanonical(reopened.payloads().get(0).canonicalUtf8());
      JsonNode payload = envelope.path("payload");

      assertThat(envelope.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-compilation-v5");
      assertThat(compilation.flowSlices()).hasSize(2);
      assertThat(
              compilation.flowSlices().stream()
                  .flatMap(flow -> flow.processJoinSignals().stream())
                  .filter(signal -> "EXPLICIT_CALL".equals(signal.signalKind())))
          .hasSize(4);
      JsonNode serializedProfile = payload.path("flowCompilationProfile");
      assertThat(serializedProfile.path("maxProcessJoinSignalsPerFlow").asInt())
          .isEqualTo(compilation.profile().maxProcessJoinSignalsPerFlow());
      assertThat(serializedProfile.path("maxProcessJoinSignalBasisRefs").asInt())
          .isEqualTo(compilation.profile().maxProcessJoinSignalBasisRefs());

      Map<String, List<FlowCompilation.ProcessJoinSignalV1>> signalsByFlow =
          compilation.flowSlices().stream()
              .collect(
                  Collectors.toMap(
                      FlowCompilation.FlowSlice::flowSliceId,
                      FlowCompilation.FlowSlice::processJoinSignals));
      assertThat(payload.path("flowSlices")).hasSize(compilation.flowSlices().size());
      for (JsonNode serializedFlow : payload.path("flowSlices")) {
        String flowSliceId = serializedFlow.path("flowSliceId").asText();
        ArrayNode expectedSignals = JsonNodeFactory.instance.arrayNode();
        signalsByFlow.get(flowSliceId).forEach(signal -> expectedSignals.add(signalNode(signal)));
        assertThat(
                canonicalJson
                    .encodeCanonical(serializedFlow.path("processJoinSignals"))
                    .copyToByteArray())
            .containsExactly(canonicalJson.encodeCanonical(expectedSignals).copyToByteArray());
      }

      List<String> expectedSignalIds =
          compilation.flowSlices().stream()
              .flatMap(flow -> flow.processJoinSignals().stream())
              .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
              .sorted()
              .toList();
      assertThat(jsonStrings(payload.path("coverage").path("processJoinSignalIds")))
          .containsExactlyElementsOf(expectedSignalIds);
      assertThat(payload.path("flowCompilationId").asText())
          .isEqualTo(expectedFlowCompilationId(compilation));
    }
  }

  private static ModulePublicationReference publish(
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      FlowCompilation compilation)
      throws Exception {
    try {
      Class<?> publisherType = Class.forName(PUBLISHER_CLASS);
      Object publisher =
          publisherType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class)
              .newInstance(fixture.moduleArtifacts(), fixture.stepArtifacts());
      Method method =
          publisherType.getMethod(
              "publish",
              org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference.class,
              org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
              ProvenCodeFactsReference.class,
              FlowCompilation.class);
      return (ModulePublicationReference)
          method.invoke(
              publisher,
              fixture.applicationDiscovery(),
              fixture.programGraphs(),
              facts,
              compilation);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("FLOW_COMPILATION_MODULE_PUBLISHER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("FLOW_COMPILATION_MODULE_PUBLISHER_FAILED", cause);
    }
  }

  private static FlowCompilationProfile profile() {
    return new FlowCompilationProfile(
        new ArtifactReference(
            ArtifactId.parse("flow-profile:" + digest("module-publish-profile")),
            new Sha256Digest(digest("module-publish-profile-bytes"))),
        16,
        8,
        64,
        96,
        32,
        64,
        256);
  }

  private static ObjectNode signalNode(FlowCompilation.ProcessJoinSignalV1 signal) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
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

  private ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture) {
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

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
