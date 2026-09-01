package com.linguan.codemd.target.stage01.requestadmission;

/** Declared immutable source coverage for one frozen repository request. */
public record InventoryScope(Kind kind, String scopeRoot, int declaredPathCount) {
    public enum Kind {
        COMPLETE_CAPTURE,
        BOUNDED_PATH_SET
    }

    public InventoryScope {
        if (kind == null || declaredPathCount < 1) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "inventory scope is invalid");
        }
        if (kind == Kind.COMPLETE_CAPTURE && scopeRoot != null) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "complete capture cannot declare a scope root");
        }
        if (kind == Kind.BOUNDED_PATH_SET
                && (scopeRoot == null || scopeRoot.isBlank() || !scopeRoot.equals(scopeRoot.strip()))) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "bounded scope requires a canonical scope root");
        }
    }
}
