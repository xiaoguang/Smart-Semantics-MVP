package org.sourceanalysis.app.analysis.fact.proofs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;

/** RED for M2's typed, persisted Proof-decision reader. */
class PersistedProofDecisionSetReaderTest {

  private static final String READER_CLASS =
      "org.sourceanalysis.app.analysis.fact.proofs.PersistedProofDecisionSetReader";

  @TempDir Path temporaryDirectory;

  @Test
  void freshReopensThePersistedProofSetOnlyWithItsExactM1AndSourceLineage() throws Exception {
    Files.createDirectory(temporaryDirectory.resolve("proof-reader-publication"));
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("proof-reader-graphs"));
        RunStoreHandle handle =
            RunStoreBootstrap.openForTest(temporaryDirectory.resolve("proof-reader-publication"))) {
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle,
              new CanonicalJsonCodec(),
              fixture.artifactPolicies(),
              new ArtifactStoreLimits(8, 2_000_000, 4_000_000, 16));
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      ProofDecisionSet expected =
          new AtomicProofBuilder(fixture.sourceReader())
              .prove(
                  candidates,
                  inputs,
                  fixture.sourceInventory(),
                  ProofRuleRegistry.standardJavaBoundary());
      ModulePublicationReference candidatePublication =
          new FactCandidateSetModulePublisher(store)
              .publish(address(fixture, 1, "candidates"), inputs, candidates);
      ModulePublicationReference proofPublication =
          new ProofDecisionSetModulePublisher(store)
              .publish(address(fixture, 2, "proofs"), inputs, candidatePublication, expected);

      assertThat(reopen(store, proofPublication, inputs, candidatePublication, candidates))
          .isEqualTo(expected);
    }
  }

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static ProofDecisionSet reopen(
      CanonicalModuleArtifactStore store,
      ModulePublicationReference proofPublication,
      FactCandidateInputs inputs,
      ModulePublicationReference candidatePublication,
      FactCandidateSet candidates)
      throws Exception {
    try {
      Class<?> readerType = Class.forName(READER_CLASS);
      Method reopen =
          readerType.getMethod(
              "reopen",
              ModulePublicationReference.class,
              FactCandidateInputs.class,
              ModulePublicationReference.class,
              FactCandidateSet.class);
      Object result =
          reopen.invoke(
              readerType.getConstructor(CanonicalModuleArtifactStore.class).newInstance(store),
              proofPublication,
              inputs,
              candidatePublication,
              candidates);
      if (result instanceof ProofDecisionSet decisions) return decisions;
      throw new AssertionError("PERSISTED_PROOF_DECISION_READER_RETURN_TYPE_INVALID");
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("PERSISTED_PROOF_DECISION_READER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("PERSISTED_PROOF_DECISION_READER_FAILED", cause);
    }
  }
}
