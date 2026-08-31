package com.linguan.codemd.stage03;

import java.util.List;

/** Stable technical presentation policy for one anchor kind. */
public record TechnicalDisplayPolicy(String policyKey, String anchorKind,
                                     List<String> resolutionOrder, String displayTemplateKey) {
    public TechnicalDisplayPolicy {
        resolutionOrder = resolutionOrder == null ? null : List.copyOf(resolutionOrder);
    }
}
