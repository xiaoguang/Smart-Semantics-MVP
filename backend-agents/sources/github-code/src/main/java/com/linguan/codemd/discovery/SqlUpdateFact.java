package com.linguan.codemd.discovery;

import java.util.Objects;

/** A deterministically provable static SQL UPDATE assignment. */
public record SqlUpdateFact(String mapperMethod, String table, String field, String value) {
    public SqlUpdateFact {
        mapperMethod = requireText(mapperMethod, "mapperMethod");
        table = requireText(table, "table");
        field = requireText(field, "field");
        value = requireText(value, "value");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
