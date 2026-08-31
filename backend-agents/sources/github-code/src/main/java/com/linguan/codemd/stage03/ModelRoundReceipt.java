package com.linguan.codemd.stage03;

/** Canonical audit receipt for one round, excluding raw response prose from reader output. */
public record ModelRoundReceipt(int round, String responseSha256, String startedReceiptId,
                                ObservedRuntimeIdentity observedRuntime) {
}
