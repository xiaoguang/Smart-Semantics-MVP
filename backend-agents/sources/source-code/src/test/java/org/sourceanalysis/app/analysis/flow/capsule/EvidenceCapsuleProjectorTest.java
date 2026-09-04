package org.sourceanalysis.app.analysis.flow.capsule;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.sourceanalysis.app.analysis.flow.compiler.EntryRootedFlowCompiler;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** M2 public seam: one persisted Flow is projected into one closed, same-flow evidence capsule. */
class EvidenceCapsuleProjectorTest {

  private static final String PROJECTOR_CLASS =
      "org.sourceanalysis.app.analysis.flow.capsule.EvidenceCapsuleProjector";
  private static final String PROFILE_CLASS =
      "org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile";

  @TempDir Path temporaryDirectory;

  @Test
  void projectsOneSameFlowEvidenceCapsuleForEveryPersistedCompiledFlow() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-projector"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference compiled =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);

      Object projection = project(fixture, facts, compiled, profile(24_576));
      List<?> capsules = listProperty(projection, "capsules");
      List<?> spans = listProperty(projection, "modelEvidenceSpans");
      List<?> obligations = listProperty(projection, "projectionObligations");

      assertThat(capsules).hasSize(compilation.flowSlices().size());
      assertThat(spans).isNotEmpty();
      assertThat(obligations).isNotEmpty();
      assertThat(strings(capsules, "flowSliceId").stream().collect(Collectors.toUnmodifiableSet()))
          .isEqualTo(
              compilation.flowSlices().stream()
                  .map(FlowCompilation.FlowSlice::flowSliceId)
                  .collect(Collectors.toUnmodifiableSet()));
      assertThat(strings(capsules, "evidenceCapsuleId")).doesNotHaveDuplicates();
      assertThat(strings(capsules, "modelEligibility")).containsOnly("ELIGIBLE");

      Map<String, FlowCompilation.FlowSlice> flowsById =
          compilation.flowSlices().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      FlowCompilation.FlowSlice::flowSliceId, flow -> flow));
      for (Object capsule : capsules) {
        FlowCompilation.FlowSlice flow = flowsById.get(property(capsule, "flowSliceId"));
        assertThat(flow).isNotNull();
        assertThat(strings(listProperty(capsule, "factViews"), "factId"))
            .containsExactlyElementsOf(flow.factIds());
        assertThat(strings(listProperty(capsule, "outcomePathViews"), "outcomePathId"))
            .containsExactlyElementsOf(
                flow.outcomePaths().stream()
                    .map(FlowCompilation.OutcomePath::outcomePathId)
                    .toList());
        assertThat(listProperty(capsule, "modelEvidenceSpanIds")).isNotEmpty();
        assertThat(listProperty(capsule, "projectionObligationIds")).isNotEmpty();
        assertThat(
                listProperty(capsule, "registryProposalBasisAtomIds").stream()
                    .map(Object::toString)
                    .toList())
            .containsExactlyElementsOf(flow.atomIds());
      }
    }
  }

  @Test
  void retainsAnOverBudgetFlowAsModelIneligibleInsteadOfDiscardingItsEvidence() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-projector-budget"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference compiled =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);

      Object projection = project(fixture, facts, compiled, profile(1));
      List<?> capsules = listProperty(projection, "capsules");

      assertThat(capsules).hasSize(compilation.flowSlices().size());
      assertThat(strings(capsules, "modelEligibility")).containsOnly("INELIGIBLE");
      assertThat(capsules)
          .allSatisfy(
              capsule -> {
                assertThat(listProperty(capsule, "modelIneligibilityGapIds")).isNotEmpty();
                assertThat(listProperty(capsule, "modelEvidenceSpanIds")).isNotEmpty();
              });
    }
  }

  @Test
  void assignsEveryProjectedEvidenceSpanAndObligationToExactlyOneFlowCapsule() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-projector-flow-local-evidence"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference compiled =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);

      Object projection = project(fixture, facts, compiled, profile(24_576));
      List<?> capsules = listProperty(projection, "capsules");
      Map<String, Object> obligationsById =
          listProperty(projection, "projectionObligations").stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      obligation -> property(obligation, "obligationId").toString(),
                      obligation -> obligation));
      Set<String> referencedSpanIds = new HashSet<>();
      Set<String> referencedObligationIds = new HashSet<>();
      for (Object capsule : capsules) {
        List<String> capsuleSpanIds = values(listProperty(capsule, "modelEvidenceSpanIds"));
        for (String spanId : capsuleSpanIds) {
          assertThat(referencedSpanIds.add(spanId)).as("span id %s", spanId).isTrue();
        }
        for (String obligationId : values(listProperty(capsule, "projectionObligationIds"))) {
          assertThat(referencedObligationIds.add(obligationId))
              .as("obligation id %s", obligationId)
              .isTrue();
          assertThat(values(listProperty(obligationsById.get(obligationId), "satisfyingSpanIds")))
              .allMatch(capsuleSpanIds::contains);
        }
      }

      assertThat(referencedSpanIds)
          .containsExactlyInAnyOrderElementsOf(
              strings(listProperty(projection, "modelEvidenceSpans"), "spanId"));
      assertThat(referencedObligationIds)
          .containsExactlyInAnyOrderElementsOf(
              strings(listProperty(projection, "projectionObligations"), "obligationId"));
    }
  }

  private Object project(
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      ModulePublicationReference compilation,
      CapsuleProjectionProfile profile)
      throws Exception {
    try {
      Class<?> profileType = Class.forName(PROFILE_CLASS);
      Object reflectedProfile =
          profileType
              .getConstructor(ArtifactReference.class, int.class, int.class, int.class, int.class)
              .newInstance(
                  profile.profileRef(),
                  profile.maxCapsules(),
                  profile.maxSpansPerCapsule(),
                  profile.maxSpanBytes(),
                  profile.maxCapsuleUtf8Bytes());
      Class<?> projectorType = Class.forName(PROJECTOR_CLASS);
      Object projector =
          projectorType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class,
                  org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader.class)
              .newInstance(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader());
      return projectorType
          .getMethod(
              "project",
              ModulePublicationReference.class,
              org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference.class,
              org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
              ProvenCodeFactsReference.class,
              profileType)
          .invoke(
              projector,
              compilation,
              fixture.sourceInventory(),
              fixture.programGraphs(),
              facts,
              reflectedProfile);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("EVIDENCE_CAPSULE_PROJECTOR_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("EVIDENCE_CAPSULE_PROJECTOR_FAILED", cause);
    }
  }

  private ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture) {
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
        new ArtifactReference(
            ArtifactId.parse("flow-profile:" + digest("capsule-flow-profile")),
            new Sha256Digest(digest("capsule-flow-profile-bytes"))),
        16,
        8,
        64,
        96,
        32);
  }

  private static CapsuleProjectionProfile profile(int maxCapsuleUtf8Bytes) {
    return new CapsuleProjectionProfile(
        new ArtifactReference(
            ArtifactId.parse("capsule-profile:" + digest("closed-same-flow-capsule")),
            new Sha256Digest(digest("closed-same-flow-capsule-bytes"))),
        16,
        32,
        4_096,
        maxCapsuleUtf8Bytes);
  }

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static List<?> listProperty(Object target, String property) {
    Object value = property(target, property);
    if (!(value instanceof List<?> values)) {
      throw new AssertionError("CAPSULE_PROJECTION_SHAPE_INVALID");
    }
    return values;
  }

  private static List<String> strings(List<?> values, String property) {
    return values.stream().map(value -> property(value, property).toString()).toList();
  }

  private static List<String> values(List<?> values) {
    return values.stream().map(Object::toString).toList();
  }

  private static Object property(Object target, String property) {
    try {
      return target.getClass().getMethod(property).invoke(target);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("CAPSULE_PROJECTION_SHAPE_INVALID", failure);
    }
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
