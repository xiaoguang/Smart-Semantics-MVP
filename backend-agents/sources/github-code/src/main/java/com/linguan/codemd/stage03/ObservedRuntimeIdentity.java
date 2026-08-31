package com.linguan.codemd.stage03;

/** Identity observed from a structured provider execution. */
public record ObservedRuntimeIdentity(String upstreamProvider, String model,
                                      String reasoningEffort, String sandbox) {
}
