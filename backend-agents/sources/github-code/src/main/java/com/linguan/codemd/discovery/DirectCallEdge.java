package com.linguan.codemd.discovery;

import java.util.Objects;

/** A source-only direct call through one uniquely resolved field type. */
public record DirectCallEdge(String caller, String callee) {
    public DirectCallEdge {
        caller = requireText(caller, "caller");
        callee = requireText(callee, "callee");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
