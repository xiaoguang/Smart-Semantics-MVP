package com.linguan.codemd.stage03;

import java.util.List;

/** One controlled claim template and its required atom pattern. */
public record ClaimEntry(String claimKey, String targetAnchorKind,
                         List<String> requiredAtomPatterns, String readerTemplateKey) {
    public ClaimEntry {
        requiredAtomPatterns = requiredAtomPatterns == null ? null : List.copyOf(requiredAtomPatterns);
    }
}
