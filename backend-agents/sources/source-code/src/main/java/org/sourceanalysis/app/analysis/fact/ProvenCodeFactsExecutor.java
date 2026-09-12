package org.sourceanalysis.app.analysis.fact;

import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
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
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;

/**
 * Executes the existing M1 candidate, M2 Proof, and M3 ledger modules for one persisted graph set.
 *
 * <p>The executor is intentionally only an ordering seam: it reopens the three predecessor
 * publications, uses the standard frozen-Java registries, and publishes each module before the next
 * module reads it. It neither reparses source nor assigns business meaning.
 */
public final class ProvenCodeFactsExecutor {

  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore stepArtifacts;

  /** Creates the only path-free execution seam for the persisted Step 04 modules. */
  public ProvenCodeFactsExecutor(
      VerifiedSourceTextReader sourceReader,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts) {
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.stepArtifacts = Objects.requireNonNull(stepArtifacts, "analysis step artifact store");
  }

  /** Publishes candidates, Proof decisions, and the one four-file proven-code-facts publication. */
  public ProvenCodeFactsReference execute(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery,
      ProgramGraphsReference programGraphs) {
    Objects.requireNonNull(verifiedSource, "verified source inventory");
    Objects.requireNonNull(applicationDiscovery, "application discovery");
    Objects.requireNonNull(programGraphs, "program graphs");

    ReopenedAnalysisStepPublication graphPublication =
        stepArtifacts.reopen(programGraphs.publication());
    if (graphPublication.semanticPayloads().size() == 1
        && "java-code-index.jsonl"
            .equals(graphPublication.semanticPayloads().get(0).descriptor().fileName())) {
      return new FactLedgerPublicationSpecifier(moduleArtifacts, stepArtifacts)
          .specifyNotProduced(
              verifiedSource,
              applicationDiscovery,
              programGraphs,
              "STRICT_GRAPH_ENRICHMENT_NOT_PRODUCED_FOR_JDT");
    }

    FactCandidateInputs inputs =
        new PersistedFactCandidateInputReader(stepArtifacts, sourceReader)
            .reopen(verifiedSource, applicationDiscovery, programGraphs);
    FactCandidateSet candidates =
        new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaFacts());
    ModulePublicationReference candidatePublication =
        new FactCandidateSetModulePublisher(moduleArtifacts)
            .publish(address(verifiedSource, 1, "candidates"), inputs, candidates);

    ProofDecisionSet decisions =
        new AtomicProofBuilder(sourceReader)
            .prove(candidates, inputs, verifiedSource, ProofRuleRegistry.standardJavaBoundary());
    ModulePublicationReference proofPublication =
        new ProofDecisionSetModulePublisher(moduleArtifacts)
            .publish(address(verifiedSource, 2, "proofs"), inputs, candidatePublication, decisions);

    return new FactLedgerPublicationSpecifier(moduleArtifacts, stepArtifacts)
        .specifyCandidatesAndProofs(
            inputs,
            candidatePublication,
            proofPublication,
            verifiedSource,
            applicationDiscovery,
            programGraphs);
  }

  private static AnalysisStepModuleAddress address(
      VerifiedSourceInventoryReference source, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        source.publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }
}
