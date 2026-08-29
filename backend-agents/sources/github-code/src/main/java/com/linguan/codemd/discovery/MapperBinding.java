package com.linguan.codemd.discovery;

import java.util.Objects;

/** A unique source-only MyBatis interface method to XML statement binding. */
public record MapperBinding(String mapperType, String methodName, String xmlPath,
                            String statementId) {
    public MapperBinding {
        mapperType = requireText(mapperType, "mapperType");
        methodName = requireText(methodName, "methodName");
        xmlPath = requireText(xmlPath, "xmlPath");
        statementId = requireText(statementId, "statementId");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
