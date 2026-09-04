package org.sourceanalysis.app.analysis.interpretation.proposal;

/** The narrow R0 transport boundary; production adapters are added separately from this core. */
@FunctionalInterface
public interface RegistryProposalProvider {

  /** Starts exactly one provider call for one isolated R0 task. */
  RegistryProposalProviderResponse propose(RegistryProposalTask task);
}
