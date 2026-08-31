package com.linguan.codemd.stage03;

/** One ownership rule, retained even when the registry is intentionally empty. */
public record OwnershipRule(String knowledgeKind, String ownerSectionKey) {
}
