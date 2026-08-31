package com.linguan.codemd.stage03;

import java.util.List;

/** One registry backed term selection, never model supplied prose. */
public record BusinessTermEntry(String businessTermKey, String anchorKind, String localizedValue,
                                List<String> eligibleAtomKinds, List<String> minimumBasisAtomIds,
                                int priority, String technicalFallbackPolicyKey) {
    public BusinessTermEntry {
        eligibleAtomKinds = eligibleAtomKinds == null ? List.of() : List.copyOf(eligibleAtomKinds);
        minimumBasisAtomIds = minimumBasisAtomIds == null ? List.of() : List.copyOf(minimumBasisAtomIds);
    }
}
