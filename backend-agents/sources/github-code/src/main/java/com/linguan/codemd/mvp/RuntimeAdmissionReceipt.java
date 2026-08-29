package com.linguan.codemd.mvp;

import java.util.List;
import java.util.Objects;

/** Immutable admission result for one recorded runtime receipt. */
public record RuntimeAdmissionReceipt(boolean admitted, List<String> findings) {
    public RuntimeAdmissionReceipt {
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        findings.forEach(finding -> requireText(finding, "finding"));
        if (admitted != findings.isEmpty()) {
            throw new IllegalArgumentException("admitted must match findings");
        }
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
