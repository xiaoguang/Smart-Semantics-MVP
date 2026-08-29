package com.linguan.codemd.analysis;

import com.linguan.codemd.discovery.SourceLocator;

import java.util.Objects;

/** A canonical Java `if` guard expression retained with its source location. */
public record ConditionFact(String factId, String expression, SourceLocator sourceLocator) {
    public ConditionFact {
        factId = requireText(factId, "factId");
        expression = requireText(expression, "expression");
        sourceLocator = Objects.requireNonNull(sourceLocator, "sourceLocator");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
