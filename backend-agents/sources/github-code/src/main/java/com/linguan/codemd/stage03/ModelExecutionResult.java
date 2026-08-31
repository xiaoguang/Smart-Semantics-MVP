package com.linguan.codemd.stage03;

/** Structured transport result; raw response is validated before it affects a document. */
public record ModelExecutionResult(String taskSpecId, int flowInterpretationRound,
                                   String responseJson, ObservedRuntimeIdentity observedRuntime,
                                   String startedReceiptId) {
}
