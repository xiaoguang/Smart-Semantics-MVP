package com.linguan.codemd.stage03;

/** One round of one isolated evidence capsule; no filesystem or free prompt is exposed. */
public record FlowModelTask(String schemaVersion, String taskSpecId, String taskKind,
                            String flowSliceId, String evidenceCapsuleId, String isolatedSessionKey,
                            int flowInterpretationRound, String inputJson, String inputJsonSha256,
                            String outputSchemaJson, String outputSchemaSha256,
                            ExpectedRuntimeIdentity expectedRuntime) {
}
