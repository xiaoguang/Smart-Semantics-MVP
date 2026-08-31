package com.linguan.codemd.stage03;

/** The only model boundary: it accepts a frozen structured task and returns structured JSON. */
@FunctionalInterface
public interface StructuredModelProvider {
    ModelExecutionResult execute(FlowModelTask task);
}
