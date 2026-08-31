package com.linguan.codemd.stage04;

import com.linguan.codemd.stage03.ExpectedRuntimeIdentity;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ObservedRuntimeIdentity;

import java.util.List;

/** Immutable, lifecycle-proven preimage for one completed Stage 03 run. */
record Stage03RunTranscript(List<ArchivedModelRound> modelRounds,
                            List<GenerationRoundReceipt> generationReceipts) {
    Stage03RunTranscript {
        modelRounds = List.copyOf(modelRounds);
        generationReceipts = List.copyOf(generationReceipts);
    }
}

/** Canonical task and response bytes captured by the lifecycle bridge. */
record ArchivedModelRound(String modelRoundId, FlowModelTask task, String canonicalResponseJson,
                          String canonicalResponseSha256, String semanticResponseSha256,
                          ObservedRuntimeIdentity observedRuntime, String startedReceiptId) {
}

/** The actual preflight, started-event, and admission closure for one archived model round. */
record GenerationRoundReceipt(String generationReceiptId, String modelRoundId, String taskSpecId,
                              String providerPolicyId, String flowSliceId, String evidenceCapsuleId, int round,
                              ExpectedRuntimeIdentity expectedRuntime, ObservedRuntimeIdentity observedRuntime,
                              String preflightReceiptId, String attemptId, String startedEventId,
                              int startedEventOrdinal, String startedReceiptId,
                              String canonicalResponseSha256, String semanticResponseSha256,
                              String terminalStatus) {
}
