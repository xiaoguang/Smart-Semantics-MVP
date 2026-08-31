package com.linguan.codemd.stage03;

/** Identity the configured provider is required to demonstrate on every round. */
public record ExpectedRuntimeIdentity(String configuredAdapterId, String configuredAuthMode,
                                      String expectedUpstreamProvider, String expectedModel,
                                      String expectedReasoningEffort, String expectedSandbox) {
}
