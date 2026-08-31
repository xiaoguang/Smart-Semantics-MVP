package com.linguan.codemd.stage02;

/** Trigger admitted by the Stage 01 entry view. */
public record FlowTrigger(String kind, String httpMethod, String route) {
}
