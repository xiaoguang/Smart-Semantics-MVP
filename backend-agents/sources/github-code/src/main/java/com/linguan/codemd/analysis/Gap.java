package com.linguan.codemd.analysis;

import com.linguan.codemd.discovery.SourceLocator;

import java.util.Objects;

/** An explicit source-analysis limitation at an affected location. */
public record Gap(String code, String subject, SourceLocator sourceLocator) {
    public Gap {
        code = requireText(code, "code");
        subject = requireText(subject, "subject");
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
