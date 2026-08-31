package com.linguan.codemd.stage03;

/** Bounded resources for controlled M5--M7 work. */
public record Stage03ResourceBudget(int maxFlowInterpretations, int roundsPerCapsule,
                                    int maxTaskInputBytes, int maxResponseBytes,
                                    int requiredSectionCount, int maxDocumentBytes,
                                    int maxReaderItems) {
}
