package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Complete in-memory result of executing every persisted R0 task exactly once. */
public record RegistryProposalExecutionSet(
    String executionSetId,
    ModulePublicationReference taskSetPublicationRef,
    List<RegistryProposalRound> rounds,
    List<RegistryProposalGenerationReceipt> generationReceipts,
    List<BusinessRegistryProposal> validatedProposals,
    List<RegistryProposalFlowDisposition> flowDispositions) {

  public RegistryProposalExecutionSet {
    if (executionSetId == null || executionSetId.isBlank()) {
      throw new IllegalArgumentException("registry proposal execution set ID is required");
    }
    Objects.requireNonNull(taskSetPublicationRef, "task set publication reference");
    rounds = List.copyOf(rounds);
    generationReceipts = List.copyOf(generationReceipts);
    validatedProposals = List.copyOf(validatedProposals);
    flowDispositions = List.copyOf(flowDispositions);
    if (rounds.size() != generationReceipts.size() || rounds.size() != flowDispositions.size()) {
      throw new IllegalArgumentException("registry proposal execution set is not closed");
    }
  }
}
