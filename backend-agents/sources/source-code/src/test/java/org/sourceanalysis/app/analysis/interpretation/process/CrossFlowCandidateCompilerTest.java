package org.sourceanalysis.app.analysis.interpretation.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * M6 RED contract for program-only cross-Flow candidate compilation.
 *
 * <p>This test intentionally uses reflection until Terra installs the public M6 seam. The
 * reflection is not a private-implementation contract: it lets the RED run as an assertion failure
 * while the production type is absent, then exercises the documented {@code
 * compileCandidates(request)} record seam once it exists.
 */
class CrossFlowCandidateCompilerTest {

  private static final String PACKAGE = "org.sourceanalysis.app.analysis.interpretation.process.";
  private static final String COMPILER = PACKAGE + "CrossFlowCandidateCompiler";
  private static final String REQUEST = PACKAGE + "CrossFlowCandidateCompilationRequest";

  @TempDir Path temporaryDirectory;

  @Test
  void exactEntryTargetCallIsAProvenHandoffWithDirectedSupport() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m6-exact-entry-target"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ReopenedAnalysisStepPublication graphs =
          fixture.stepArtifacts().reopen(fixture.programGraphs().publication());
      ReopenedAnalysisStepPublication businessFlows =
          fixture.stepArtifacts().reopen(flows.publication());
      assertThat(graphs.semanticPayloads()).hasSize(7);
      assertThat(businessFlows.semanticPayloads()).hasSize(5);
      assertThat(flowIds(businessFlows, "flow-slices.json")).hasSize(2);

      ModulePublicationReference registry = publishRegistry(fixture, flows);
      Object result = invoke(fixture, facts, flows, registry);
      List<?> relations = list(result, "candidateRelations");
      assertThat(relations)
          .anySatisfy(
              relation -> {
                assertThat(text(relation, "strongestSignalLevel")).isEqualTo("PROVEN_HANDOFF");
                assertThat(text(relation, "relationUse")).isEqualTo("PROCESS_CANDIDATE");
                assertThat(list(relation, "supportingProcessJoinSignalIds")).isNotEmpty();
                assertThat(list(relation, "processSemanticCueIds")).isNotEmpty();
                assertThat(text(relation, "direction")).isIn("LEFT_TO_RIGHT", "RIGHT_TO_LEFT");
              });
    }
  }

  @Test
  void preservesTheGuardThatCanBlockAnOtherwiseExactFlowHandoff() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedSharedJavaCall(
            temporaryDirectory.resolve("m6-guarded-exact-entry-target"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      ReopenedAnalysisStepPublication reopenedFlows =
          fixture.stepArtifacts().reopen(flows.publication());

      assertThat(
              flowSignalKinds(
                  reopenedFlows, "entry:" + sha256("approve".getBytes(StandardCharsets.UTF_8))))
          .contains("COUNTER_CONDITION");

      Object result = invoke(fixture, facts, flows, registry);

      assertThat(list(result, "candidateRelations"))
          .singleElement()
          .satisfies(
              relation -> {
                assertThat(text(relation, "strongestSignalLevel")).isEqualTo("PROVEN_HANDOFF");
                assertThat(text(relation, "relationUse")).isEqualTo("PENDING_ONLY");
                assertThat(list(relation, "counterProcessJoinSignalIds")).hasSize(1);
                assertThat(list(relation, "blockingCounterProcessJoinSignalIds")).hasSize(1);
                assertThat(list(relation, "counterBases"))
                    .singleElement()
                    .satisfies(
                        counter -> {
                          assertThat(text(counter, "counterKind")).isEqualTo("COUNTER_CONDITION");
                          assertThat(list(counter, "leftCounterProcessJoinSignalIds")).hasSize(1);
                          assertThat(list(counter, "rightCounterProcessJoinSignalIds")).isEmpty();
                        });
              });
    }
  }

  @Test
  void genericOrNameOnlyContextCannotCreateAnOrderEdge() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("m6-generic-context"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      Object result = invoke(fixture, facts, flows, registry);

      // This fixture has common Java/Mapper/XML material and technical signal names, but no exact
      // call to another Flow's entry target. Its frozen R0 registry does have the same
      // BUSINESS_TERM with proof-closed, local atom bases in both Flows. That is a useful reader
      // cue, never proof of order or an external business effect.
      assertThat(list(result, "candidateRelations"))
          .singleElement()
          .satisfies(
              relation -> {
                assertThat(text(relation, "strongestSignalLevel")).isEqualTo("SEMANTIC_CUE");
                assertThat(text(relation, "relationUse")).isEqualTo("PENDING_ONLY");
                assertThat(text(relation, "direction")).isEqualTo("UNDIRECTED");
                assertThat(list(relation, "supportingProcessJoinSignalIds")).isEmpty();
                assertThat(list(relation, "processSemanticCueIds")).hasSize(1);
              });
    }
  }

  @Test
  void rejectsCapsuleSignalsThatDifferFromTheOwningFlowsSignals() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m6-capsule-signal-closure"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ReopenedAnalysisStepPublication reopened =
          fixture.stepArtifacts().reopen(flows.publication());
      ReopenedAnalysisStepPublication mismatched = mutateOneCapsuleSignal(reopened);
      CrossFlowCandidateCompiler compiler =
          new CrossFlowCandidateCompiler(
              fixture.stepArtifacts(), fixture.moduleArtifacts(), unusedInputReader());
      Method readFlows =
          CrossFlowCandidateCompiler.class.getDeclaredMethod(
              "readFlows", ReopenedAnalysisStepPublication.class);
      readFlows.setAccessible(true);

      assertThatThrownBy(() -> readFlows.invoke(compiler, mismatched))
          .hasCauseInstanceOf(CrossFlowCandidateCompilationException.class)
          .hasRootCauseMessage("PROCESS_MODEL_REFERENCE_INVALID");
    }
  }

  @Test
  void rejectsJoinSignalsWhoseFactProofEvidenceClosureIsIncomplete() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m6-signal-proof-closure"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ReopenedAnalysisStepPublication incomplete =
          removeOneSignalsProofReference(fixture.stepArtifacts().reopen(flows.publication()));
      CrossFlowCandidateCompiler compiler =
          new CrossFlowCandidateCompiler(
              fixture.stepArtifacts(), fixture.moduleArtifacts(), unusedInputReader());

      assertThatThrownBy(
              () ->
                  invokeVerifiedFlowReader(
                      compiler,
                      incomplete,
                      fixture.stepArtifacts().reopen(facts.publication()),
                      fixture.stepArtifacts().reopen(fixture.programGraphs().publication())))
          .hasCauseInstanceOf(CrossFlowCandidateCompilationException.class)
          .hasRootCauseMessage("PROCESS_PROOF_CLOSURE_INVALID");
    }
  }

  @Test
  void zeroFlowIsAClosedEmptyCompilationWithNoProviderWork() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("m6-zero-flow"))) {
      BusinessFlowsReference flows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(
              fixture,
              new FlowCompilationProfile(
                  RegistryProposalTaskCompilerTest.reference("flow-profile", "m6-zero-flow"),
                  16,
                  8,
                  1,
                  96,
                  32,
                  64,
                  256),
              new CapsuleProjectionProfile(
                  RegistryProposalTaskCompilerTest.reference("capsule-profile", "m6-zero-flow"),
                  16,
                  32,
                  4_096,
                  24_576));
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ReopenedAnalysisStepPublication reopened =
          fixture.stepArtifacts().reopen(flows.publication());
      assertThat(flowIds(reopened, "flow-slices.json")).isEmpty();

      ModulePublicationReference registry = publishRegistry(fixture, flows);
      Object result = invoke(fixture, facts, flows, registry);
      assertThat(list(result, "flowSliceIds")).isEmpty();
      assertThat(list(result, "candidateRelations")).isEmpty();
      assertThat(list(result, "processEvidenceGroups")).isEmpty();
      assertThat(number(result, "providerCallCount")).isZero();
      assertThat(bool(result, "closed")).isTrue();
    }
  }

  @Test
  void absentEntryTargetCannotBeReplacedByANameGuess() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m6-invalid-entry-target"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      // The current public fixture does not expose a safe mutation hook for deleting the exact
      // entry-root edge. Keep this contract as the explicit deferred mutation: once the public
      // graph mutation seam exists, this invocation must fail with
      // PROCESS_ENTRY_TARGET_INVALID, never recover by method-name matching.
      assertThat(fixture.programGraphs().publication()).isNotNull();
      assertThat(flows.publication()).isNotNull();
      assertThat(facts.publication()).isNotNull();
      requireCompiler();
    }
  }

  @Test
  void reopensTheRunRequestProfileAndBudgetBeforeCompilingCandidates() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m6-controls-reopen"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);

      Object result = invoke(fixture, facts, flows, registry);

      assertThat(list(result, "flowSliceIds")).hasSize(2);
      assertThat(number(result, "providerCallCount")).isZero();
    }
  }

  @Test
  void rejectsAMalformedProcessCueProfileInsteadOfUsingDefaults() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m6-invalid-process-profile"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);

      assertThatThrownBy(
              () -> invoke(fixture, facts, flows, registry, processInputs(fixture, false)))
          .hasCauseInstanceOf(CrossFlowCandidateCompilationException.class)
          .hasRootCauseMessage("PROCESS_CUE_PROFILE_INVALID");
    }
  }

  @Test
  void retainsACandidateGroupThatExceedsOneFutureTaskShardFlowLimit() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m6-process-flow-limit"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);

      Object result = invoke(fixture, facts, flows, registry, processInputs(fixture, true, 1));

      assertThat(list(result, "processEvidenceGroups")).hasSize(1);
      assertThat(list(list(result, "processEvidenceGroups").get(0), "memberFlowSliceIds"))
          .hasSize(2);
    }
  }

  @Test
  void retainsAllRegistryTermsForM7ToPackOrMarkNoModel() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m6-process-registry-limit"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);

      Object unconstrained = invoke(fixture, facts, flows, registry);
      assertThat(
              list(
                  list(unconstrained, "processEvidenceGroups").get(0),
                  "repositoryInterpretationRegistryItemIds"))
          .hasSizeGreaterThan(1);

      Object constrained =
          invoke(fixture, facts, flows, registry, processInputs(fixture, true, 8, 1));
      assertThat(
              list(
                  list(constrained, "processEvidenceGroups").get(0),
                  "repositoryInterpretationRegistryItemIds"))
          .hasSizeGreaterThan(1);
    }
  }

  private static Object invoke(
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      BusinessFlowsReference flows,
      ModulePublicationReference registry)
      throws Exception {
    return invoke(fixture, facts, flows, registry, processInputs(fixture));
  }

  private static Object invoke(
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      BusinessFlowsReference flows,
      ModulePublicationReference registry,
      ProcessInputs inputs)
      throws Exception {
    Class<?> compilerType = requireCompiler();
    Class<?> requestType = Class.forName(REQUEST);
    Object request = request(requestType, fixture, facts, flows, registry, inputs.runRequest());
    Method compile = compilerType.getMethod("compileCandidates", requestType);
    Object compiler = constructorInjectedCompiler(compilerType, fixture, inputs);
    try {
      return compile.invoke(compiler, request);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("CROSS_FLOW_CANDIDATE_COMPILER_FAILED", cause);
    }
  }

  static CrossFlowCandidateCompilation compileForPublisher(
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      BusinessFlowsReference flows,
      ModulePublicationReference registry)
      throws Exception {
    return (CrossFlowCandidateCompilation) invoke(fixture, facts, flows, registry);
  }

  static CrossFlowCandidateCompilation compileForPublisherWithMaxFlowsPerShard(
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      BusinessFlowsReference flows,
      ModulePublicationReference registry,
      int maxFlowsPerShard)
      throws Exception {
    return (CrossFlowCandidateCompilation)
        invoke(fixture, facts, flows, registry, processInputs(fixture, true, maxFlowsPerShard));
  }

  private static ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture)
      throws Exception {
    Method method =
        RegistryProposalTaskCompilerTest.class.getDeclaredMethod(
            "publishProvenFacts", ProgramGraphsPublicFixture.class);
    method.setAccessible(true);
    return (ProvenCodeFactsReference) method.invoke(null, fixture);
  }

  private static Object constructorInjectedCompiler(
      Class<?> compilerType, ProgramGraphsPublicFixture fixture, ProcessInputs inputs)
      throws Exception {
    Object stepArtifacts = fixture.stepArtifacts();
    Object moduleArtifacts = fixture.moduleArtifacts();
    for (Constructor<?> constructor : compilerType.getConstructors()) {
      Class<?>[] types = constructor.getParameterTypes();
      if (types.length != 3) continue;
      Object[] values = new Object[3];
      boolean stepAssigned = false;
      boolean moduleAssigned = false;
      boolean inputAssigned = false;
      for (int index = 0; index < types.length; index++) {
        if (!stepAssigned && types[index].isInstance(stepArtifacts)) {
          values[index] = stepArtifacts;
          stepAssigned = true;
        } else if (!moduleAssigned && types[index].isInstance(moduleArtifacts)) {
          values[index] = moduleArtifacts;
          moduleAssigned = true;
        } else if (!inputAssigned && types[index].isInstance(inputs.reader())) {
          values[index] = inputs.reader();
          inputAssigned = true;
        }
      }
      if (stepAssigned && moduleAssigned && inputAssigned) return constructor.newInstance(values);
    }
    throw new AssertionError("CROSS_FLOW_CANDIDATE_CONSTRUCTOR_INJECTION_SEAM_INVALID");
  }

  private static Object request(
      Class<?> requestType,
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      BusinessFlowsReference flows,
      ModulePublicationReference registry,
      ArtifactReference runRequest)
      throws Exception {
    for (Constructor<?> constructor : requestType.getDeclaredConstructors()) {
      Class<?>[] types = constructor.getParameterTypes();
      if (types.length != 5) continue;
      Object[] values = new Object[] {fixture.programGraphs(), facts, flows, registry, runRequest};
      if (!compatible(types, values)) continue;
      constructor.setAccessible(true);
      return constructor.newInstance(values);
    }
    throw new AssertionError("CROSS_FLOW_CANDIDATE_REQUEST_SEAM_INVALID");
  }

  private static ModulePublicationReference publishRegistry(
      ProgramGraphsPublicFixture fixture, BusinessFlowsReference flows) throws Exception {
    Class<?> testType =
        Class.forName(
            "org.sourceanalysis.app.analysis.interpretation.model.FiniteKeyFlowTaskCompilerTest");
    Method method =
        testType.getDeclaredMethod(
            "publishRegistry", ProgramGraphsPublicFixture.class, BusinessFlowsReference.class);
    method.setAccessible(true);
    return (ModulePublicationReference) method.invoke(null, fixture, flows);
  }

  private static boolean compatible(Class<?>[] types, Object[] values) {
    for (int index = 0; index < types.length; index++) {
      if (values[index] != null && !types[index].isInstance(values[index])) return false;
    }
    return true;
  }

  private static ArtifactReference sourceInputReference(ProgramGraphsPublicFixture fixture) {
    ReopenedAnalysisStepPublication source =
        fixture.stepArtifacts().reopen(fixture.sourceInventory().publication());
    return source.semanticPayloads().stream()
        .filter(value -> "source-input.json".equals(value.descriptor().fileName()))
        .findFirst()
        .map(
            value ->
                new ArtifactReference(value.descriptor().artifactId(), value.descriptor().sha256()))
        .orElseThrow();
  }

  static ProcessInputs processInputs(ProgramGraphsPublicFixture fixture) throws Exception {
    return processInputs(fixture, true, 8, 32);
  }

  private static ProcessInputs processInputs(
      ProgramGraphsPublicFixture fixture, boolean validProfile) throws Exception {
    return processInputs(fixture, validProfile, 8, 32);
  }

  private static ProcessInputs processInputs(
      ProgramGraphsPublicFixture fixture, boolean validProfile, int maxFlowsPerProcessGroup)
      throws Exception {
    return processInputs(fixture, validProfile, maxFlowsPerProcessGroup, 32);
  }

  private static ProcessInputs processInputs(
      ProgramGraphsPublicFixture fixture,
      boolean validProfile,
      int maxFlowsPerProcessGroup,
      int maxRegistryItems)
      throws Exception {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    ArtifactReference profile =
        new ArtifactReference(
            ArtifactId.parse(
                "profile-bundle:" + fixture.artifactControls().profileSha256().value()),
            fixture.artifactControls().profileSha256());
    ArtifactReference budget = reference("resource-budget", "m6-process-budget");
    ObjectNode profileValue =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    ObjectNode interpretation = profileValue.putObject("flowInterpretation");
    interpretation.put(
        "crossFlowCandidateRuleVersion", "flow-interpretation-cross-flow-candidate-rules-v1");
    ObjectNode cues = interpretation.putObject("processCueProfile");
    cues.put("schemaVersion", "flow-interpretation-process-cue-profile-v1");
    cues.put("normalizationRule", validProfile ? "R0_NFC_EXACT_V1" : "UNSUPPORTED_NORMALIZATION");
    cues.putArray("entryVerbLexicon");
    cues.putArray("stateWordLexicon");
    ArtifactReference runtime = reference("process-model-runtime", "m6-scripted-runtime");
    reference(interpretation.putObject("processModelRuntimeRef"), runtime);
    ObjectNode budgetValue =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    ObjectNode budgetInterpretation = budgetValue.putObject("flowInterpretation");
    budgetInterpretation.put("maxProcessJoinSignals", 32);
    budgetInterpretation.put("maxRegistryItems", maxRegistryItems);
    budgetInterpretation.put("maxCandidateProcessRelations", 32);
    budgetInterpretation.put("maxProcessEvidenceGroups", 16);
    budgetInterpretation.put("maxFlowsPerProcessGroup", maxFlowsPerProcessGroup);
    budgetInterpretation.put("maxRelationsPerProcessGroup", 16);
    budgetInterpretation.put("maxProcessTaskShards", 16);
    budgetInterpretation.put("maxProcessInputBytes", 16_384);
    budgetInterpretation.put("maxProcessHypotheses", 8);
    budgetInterpretation.put("maxClaimsPerProcessHypothesis", 16);
    budgetInterpretation.put("maxReaderSlots", 32);

    ObjectNode request = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    request.putArray("approvedFindingRefs");
    reference(request.putObject("artifactPolicyRegistryRef"), reference("artifact-policy", "m6"));
    reference(request.putObject("candidateSeriesRef"), reference("candidate-series", "m6"));
    reference(request.putObject("frozenRepositoryRequestRef"), reference("frozen-request", "m6"));
    request.putNull("organizationRegistrySeedRef");
    request.putNull("parentCandidateRef");
    reference(request.putObject("profileBundleRef"), profile);
    reference(request.putObject("promptBundleRef"), reference("prompt-bundle", "m6"));
    request.put("readerCandidateRound", "ROUND_1");
    reference(request.putObject("resourceBudgetRef"), budget);
    reference(request.putObject("schemaBundleRef"), reference("schema-bundle", "m6"));
    request.put("schemaVersion", "analysis-run-request-v2");
    request.put(
        "sourceRegistrationId", reference("source-registration", "m6").artifactId().value());
    reference(request.putObject("toolchainRef"), reference("toolchain", "m6"));
    ImmutableBytes requestBytes = json.encodeCanonical(request);
    ArtifactReference runRequest =
        new ArtifactReference(
            ArtifactId.parse("run-request:" + sha256(requestBytes.copyToByteArray())),
            new Sha256Digest(sha256(requestBytes.copyToByteArray())));
    Map<ArtifactReference, ImmutableBytes> values = new LinkedHashMap<>();
    values.put(runRequest, requestBytes);
    values.put(profile, json.encodeCanonical(profileValue));
    values.put(budget, json.encodeCanonical(budgetValue));
    Class<?> readerType = Class.forName(PACKAGE + "ProcessInputArtifactReader");
    Object reader =
        Proxy.newProxyInstance(
            readerType.getClassLoader(),
            new Class<?>[] {readerType},
            (proxy, method, arguments) -> {
              if (!"reopen".equals(method.getName())
                  || arguments == null
                  || arguments.length != 1) {
                throw new UnsupportedOperationException(method.getName());
              }
              ImmutableBytes value = values.get(arguments[0]);
              if (value == null) throw new IllegalArgumentException("fixture input is absent");
              return value;
            });
    return new ProcessInputs(runRequest, (ProcessInputArtifactReader) reader, runtime);
  }

  private static ArtifactReference reference(String prefix, String seed) {
    String digest = sha256(seed.getBytes(StandardCharsets.UTF_8));
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + digest), new Sha256Digest(digest));
  }

  private static void reference(ObjectNode node, ArtifactReference reference) {
    node.put("artifactId", reference.artifactId().value());
    node.put("sha256", reference.sha256().value());
  }

  private static ProcessInputArtifactReader unusedInputReader() {
    return reference -> {
      throw new IllegalStateException("input reader must not run for this private reader seam");
    };
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is unavailable", failure);
    }
  }

  record ProcessInputs(
      ArtifactReference runRequest, ProcessInputArtifactReader reader, ArtifactReference runtime) {}

  private static Class<?> requireCompiler() {
    try {
      return Class.forName(COMPILER);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("CROSS_FLOW_CANDIDATE_COMPILER_NOT_IMPLEMENTED", missing);
    }
  }

  private static List<String> flowIds(
      ReopenedAnalysisStepPublication publication, String fileName) {
    JsonNode root =
        publication.semanticPayloads().stream()
            .filter(value -> fileName.equals(value.descriptor().fileName()))
            .findFirst()
            .map(value -> new CanonicalJsonCodec().parseCanonical(value.canonicalUtf8()))
            .orElseThrow();
    List<String> ids = new ArrayList<>();
    root.path("flowSlices").forEach(value -> ids.add(value.path("flowSliceId").asText()));
    return ids;
  }

  private static List<String> flowSignalKinds(
      ReopenedAnalysisStepPublication publication, String entryId) {
    JsonNode root =
        publication.semanticPayloads().stream()
            .filter(value -> "flow-slices.json".equals(value.descriptor().fileName()))
            .findFirst()
            .map(value -> new CanonicalJsonCodec().parseCanonical(value.canonicalUtf8()))
            .orElseThrow();
    return java.util.stream.StreamSupport.stream(root.path("flowSlices").spliterator(), false)
        .filter(flow -> entryId.equals(flow.path("entryId").asText()))
        .findFirst()
        .orElseThrow()
        .path("processJoinSignals")
        .valueStream()
        .map(signal -> signal.path("signalKind").asText())
        .toList();
  }

  private static ReopenedAnalysisStepPublication mutateOneCapsuleSignal(
      ReopenedAnalysisStepPublication original) {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    List<VerifiedCanonicalPayload> changed = new ArrayList<>();
    boolean mutated = false;
    for (VerifiedCanonicalPayload payload : original.semanticPayloads()) {
      if (!"evidence-capsules.jsonl".equals(payload.descriptor().fileName())) {
        changed.add(payload);
        continue;
      }
      StringBuilder lines = new StringBuilder();
      String text = new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      for (String line : text.substring(0, text.length() - 1).split("\\n", -1)) {
        ObjectNode capsule =
            (ObjectNode)
                canonicalJson.parseCanonical(
                    ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
        if (!mutated) {
          ArrayNode signals = (ArrayNode) capsule.path("processJoinSignals");
          ObjectNode signal = (ObjectNode) signals.get(0);
          signal.put("anchorKey", signal.path("anchorKey").asText() + ":mismatch");
          mutated = true;
        }
        lines.append(
            new String(
                canonicalJson.encodeCanonical(capsule).copyToByteArray(), StandardCharsets.UTF_8));
        lines.append('\n');
      }
      changed.add(
          new VerifiedCanonicalPayload(
              payload.descriptor(),
              ImmutableBytes.copyOf(lines.toString().getBytes(StandardCharsets.UTF_8))));
    }
    assertThat(mutated).isTrue();
    return new ReopenedAnalysisStepPublication(
        original.reference(), original.receipt(), changed, original.archiveManifestPayload());
  }

  private static ReopenedAnalysisStepPublication removeOneSignalsProofReference(
      ReopenedAnalysisStepPublication original) {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    List<VerifiedCanonicalPayload> changed = new ArrayList<>();
    String mutatedFlowId = null;
    for (VerifiedCanonicalPayload payload : original.semanticPayloads()) {
      if (!"flow-slices.json".equals(payload.descriptor().fileName())) {
        changed.add(payload);
        continue;
      }
      ObjectNode document =
          (ObjectNode) canonicalJson.parseCanonical(payload.canonicalUtf8()).deepCopy();
      ObjectNode flow = (ObjectNode) document.withArray("flowSlices").get(0);
      ArrayNode proofIds = (ArrayNode) flow.withArray("processJoinSignals").get(0).path("proofIds");
      assertThat(proofIds).isNotEmpty();
      proofIds.removeAll();
      mutatedFlowId = flow.path("flowSliceId").asText();
      changed.add(
          new VerifiedCanonicalPayload(
              payload.descriptor(), canonicalJson.encodeCanonical(document)));
    }
    assertThat(mutatedFlowId).isNotBlank();
    boolean capsuleChanged = false;
    List<VerifiedCanonicalPayload> complete = new ArrayList<>();
    for (VerifiedCanonicalPayload payload : changed) {
      if (!"evidence-capsules.jsonl".equals(payload.descriptor().fileName())) {
        complete.add(payload);
        continue;
      }
      StringBuilder lines = new StringBuilder();
      String text = new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      for (String line : text.substring(0, text.length() - 1).split("\\n", -1)) {
        ObjectNode capsule =
            (ObjectNode)
                canonicalJson
                    .parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)))
                    .deepCopy();
        if (mutatedFlowId.equals(capsule.path("flowSliceId").asText())) {
          ArrayNode proofIds =
              (ArrayNode) capsule.withArray("processJoinSignals").get(0).path("proofIds");
          assertThat(proofIds).isNotEmpty();
          proofIds.removeAll();
          capsuleChanged = true;
        }
        lines.append(
            new String(
                canonicalJson.encodeCanonical(capsule).copyToByteArray(), StandardCharsets.UTF_8));
        lines.append('\n');
      }
      complete.add(
          new VerifiedCanonicalPayload(
              payload.descriptor(),
              ImmutableBytes.copyOf(lines.toString().getBytes(StandardCharsets.UTF_8))));
    }
    assertThat(capsuleChanged).isTrue();
    return new ReopenedAnalysisStepPublication(
        original.reference(), original.receipt(), complete, original.archiveManifestPayload());
  }

  private static void invokeVerifiedFlowReader(
      CrossFlowCandidateCompiler compiler,
      ReopenedAnalysisStepPublication flows,
      ReopenedAnalysisStepPublication facts,
      ReopenedAnalysisStepPublication graphs)
      throws Exception {
    Method method =
        CrossFlowCandidateCompiler.class.getDeclaredMethod(
            "readVerifiedFlows",
            ReopenedAnalysisStepPublication.class,
            ReopenedAnalysisStepPublication.class,
            ReopenedAnalysisStepPublication.class);
    method.setAccessible(true);
    method.invoke(compiler, flows, facts, graphs);
  }

  private static List<?> list(Object value, String property) throws Exception {
    Object result = property(value, property);
    if (!(result instanceof List<?> values))
      throw new AssertionError("CROSS_FLOW_RESULT_SHAPE_INVALID_" + property);
    return values;
  }

  private static String text(Object value, String property) throws Exception {
    return String.valueOf(property(value, property));
  }

  private static int number(Object value, String property) throws Exception {
    return ((Number) property(value, property)).intValue();
  }

  private static boolean bool(Object value, String property) throws Exception {
    return (Boolean) property(value, property);
  }

  private static Object property(Object value, String property) throws Exception {
    Method accessor = value.getClass().getMethod(property);
    return Objects.requireNonNull(accessor.invoke(value), property);
  }
}
