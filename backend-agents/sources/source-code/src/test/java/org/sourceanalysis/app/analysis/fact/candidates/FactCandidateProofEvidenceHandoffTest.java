package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** RED for the complete public M1-to-M2 subject-evidence handoff. */
class FactCandidateProofEvidenceHandoffTest {

  @TempDir Path temporaryDirectory;

  @Test
  void applicableBoundaryCarriesEveryProofSubjectAndCompleteEvidencePayload() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("two-entry-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidateSet =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());

      assertThat(candidateSet.candidates()).hasSize(2);
      for (FactCandidateSet.FactCandidate candidate : candidateSet.candidates()) {
        Set<String> expectedSubjectIds = expectedProofSubjectIds(candidate);
        Set<String> actualSubjectIds =
            candidate.evidenceBySubject().stream()
                .map(FactCandidateSet.SubjectEvidenceBinding::subjectElementId)
                .collect(Collectors.toCollection(HashSet::new));
        assertThat(actualSubjectIds)
            .as("M1 evidence subjects for candidate %s", candidate.candidateFactKey())
            .containsExactlyInAnyOrderElementsOf(expectedSubjectIds);

        assertCompleteEvidenceNodePayloads(inputs, candidate);
      }
    }
  }

  private static Set<String> expectedProofSubjectIds(FactCandidateSet.FactCandidate candidate) {
    Set<String> expected = new HashSet<>();
    expected.add(candidate.invocationCallId());
    expected.add(candidate.boundaryNodeId());
    expected.add(candidate.callTargetEdgeId());
    candidate.orderedArguments().forEach(
        argument -> {
          expected.add(argument.argumentEdgeId());
          expected.addAll(argument.javaLocalOriginNodeIds());
        });
    expected.add(candidate.controlBlockId());
    if (candidate.guardId() != null) {
      expected.add(candidate.guardId());
    }
    return expected;
  }

  private static void assertCompleteEvidenceNodePayloads(
      FactCandidateInputs inputs, FactCandidateSet.FactCandidate candidate) {
    FactCandidateSet.SubjectEvidenceBinding sourceBinding =
        candidate.evidenceBySubject().stream().findFirst().orElseThrow();
    FactCandidateInputs.EvidenceNode sourceNode =
        inputs.evidenceGraph().nodesById().get(sourceBinding.sourceEvidenceNodeIds().get(0));
    assertThat(sourceNode).as("source Evidence node must be persisted").isNotNull();
    Object sourceExcerpt = invokePublicAccessor(sourceNode, "sourceExcerpt");
    assertThat(sourceExcerpt)
        .as("SOURCE_EXCERPT node must preserve complete SourceExcerptV1 payload")
        .isInstanceOf(SourceExcerptV1.class);
    SourceExcerptV1 typedExcerpt = (SourceExcerptV1) sourceExcerpt;
    assertThat(typedExcerpt.locator()).isNotNull();
    assertThat(typedExcerpt.rawUtf8()).isNotNull();
    assertThat(typedExcerpt.rawUtf8Sha256()).isNotNull();

    FactCandidateInputs.EvidenceNode ruleNode =
        inputs.evidenceGraph().nodesById().get(sourceBinding.ruleApplicationEvidenceNodeIds().get(0));
    assertThat(ruleNode).as("rule Evidence node must be persisted").isNotNull();
    Object ruleApplication = invokePublicAccessor(ruleNode, "ruleApplication");
    assertThat(ruleApplication)
        .as("RULE_APPLICATION node must preserve its complete public payload")
        .isNotNull();
    assertThat(invokePublicAccessor(ruleApplication, "ruleId"))
        .as("rule application ruleId")
        .isInstanceOf(String.class)
        .asString()
        .isNotBlank();
    assertThat(invokePublicAccessor(ruleApplication, "ruleVersion"))
        .as("rule application ruleVersion")
        .isInstanceOf(String.class)
        .asString()
        .isNotBlank();
    Object inputProgramElementIds = invokePublicAccessor(ruleApplication, "inputProgramElementIds");
    assertThat(inputProgramElementIds)
        .as("rule application inputProgramElementIds")
        .isInstanceOf(Iterable.class);
    assertThat((Iterable<?>) inputProgramElementIds)
        .as("rule application inputProgramElementIds")
        .isNotEmpty();
  }

  private static Object invokePublicAccessor(Object receiver, String accessor) {
    Method method;
    try {
      method = receiver.getClass().getMethod(accessor);
    } catch (NoSuchMethodException absent) {
      fail(
          "M1-to-M2 evidence handoff requires public accessor "
              + receiver.getClass().getName()
              + "."
              + accessor
              + "()"
              + "; complete evidence metadata is not exposed");
      return null;
    }
    assertThat(Modifier.isPublic(method.getModifiers()))
        .as("evidence accessor must be public: %s", accessor)
        .isTrue();
    try {
      return method.invoke(receiver);
    } catch (IllegalAccessException | InvocationTargetException failure) {
      fail("public evidence accessor could not be invoked: " + accessor, failure);
      return null;
    }
  }
}
