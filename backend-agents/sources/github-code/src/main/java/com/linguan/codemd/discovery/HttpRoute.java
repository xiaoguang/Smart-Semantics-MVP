package com.linguan.codemd.discovery;

import java.util.Objects;

/** One statically declared Spring MVC HTTP entry point. */
public record HttpRoute(String httpMethod, String path, SourceLocator controllerMethodLocator) {
    public HttpRoute {
        httpMethod = requireText(httpMethod, "httpMethod");
        path = requireText(path, "path");
        controllerMethodLocator = Objects.requireNonNull(controllerMethodLocator,
                "controllerMethodLocator");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
