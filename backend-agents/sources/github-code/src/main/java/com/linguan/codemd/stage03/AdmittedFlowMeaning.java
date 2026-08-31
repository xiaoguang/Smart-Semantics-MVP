package com.linguan.codemd.stage03;

import java.util.List;

/** A registry-resolved meaning with explicit atom or gap basis. */
public record AdmittedFlowMeaning(String meaningId, String anchorKey, String anchorKind,
                                  String businessTermKey, String localizedValue, List<String> claimKeys,
                                  List<String> basisAtomIds, List<String> basisGapIds) {
    public AdmittedFlowMeaning {
        claimKeys = List.copyOf(claimKeys);
        basisAtomIds = List.copyOf(basisAtomIds);
        basisGapIds = List.copyOf(basisGapIds);
    }
}
