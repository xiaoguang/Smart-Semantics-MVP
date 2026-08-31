package com.linguan.codemd.stage03;

import java.util.List;

/** Program's final disposition for one R1 proposal after the R2 constraint check. */
public record ProposalDisposition(String proposalKey, String disposition, List<String> retainedClaimKeys,
                                  List<String> retainedBasisAtomIds, List<String> retainedBasisGapIds) {
    public ProposalDisposition {
        retainedClaimKeys = List.copyOf(retainedClaimKeys);
        retainedBasisAtomIds = List.copyOf(retainedBasisAtomIds);
        retainedBasisGapIds = List.copyOf(retainedBasisGapIds);
    }
}
