package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.List;

/** One R1-selected finite key after R2 has made its permitted precision decision. */
public record InterpretationProposal(
    String interpretationProposalId,
    String registryProposalId,
    String flowSliceId,
    String provisionalKey,
    String selectedKey,
    List<String> basisAtomIds,
    List<String> basisGapIds,
    String r2Decision) {}
