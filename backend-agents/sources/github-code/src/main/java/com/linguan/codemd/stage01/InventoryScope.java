package com.linguan.codemd.stage01;

/** Explicit scope of the caller-provided frozen repository inventory. */
public record InventoryScope(String kind, String scopeRoot, int declaredPathCount) {
}
