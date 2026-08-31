package com.linguan.codemd.stage01;

import java.util.List;
import java.util.Objects;

/** Canonically ordered admitted facts plus total candidate accounting. */
public record ProvenFactSet(String provenFactSetId, List<CodeFact> codeFacts,
                             CandidateAccounting candidateAccounting) {
    public ProvenFactSet {
        provenFactSetId = requireText(provenFactSetId, "provenFactSetId");
        codeFacts = List.copyOf(codeFacts);
        candidateAccounting = Objects.requireNonNull(candidateAccounting, "candidateAccounting");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
