package com.linguan.codemd.stage03;

/** Conflict shape retained for a future failure receipt; success always has none. */
public record KnowledgeConflict(String code, String firstId, String secondId) {
}
