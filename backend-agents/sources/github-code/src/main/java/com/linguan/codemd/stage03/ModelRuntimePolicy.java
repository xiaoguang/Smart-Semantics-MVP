package com.linguan.codemd.stage03;

/** The sole configured runtime identity; this is not a fallback chain. */
public record ModelRuntimePolicy(String configuredAdapterId, String configuredAuthMode,
                                 String expectedUpstreamProvider, String expectedModel,
                                 String expectedReasoningEffort, String expectedSandbox) {
}
