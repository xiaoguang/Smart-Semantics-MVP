package com.linguan.codemd.analysis;

import com.linguan.codemd.discovery.SourceLocator;

import java.util.Objects;

/** One source-derived code predicate whose target binding has been classified. */
public record CodeFact(String factId,
                       String kind,
                       String subject,
                       String object,
                       String resolution,
                       SourceLocator sourceLocator) {
    public CodeFact {
        factId = requireText(factId, "factId");
        kind = requireText(kind, "kind");
        subject = requireText(subject, "subject");
        object = requireText(object, "object");
        resolution = requireText(resolution, "resolution");
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
