package org.sourceanalysis.research.persistence;

/** A MyBatis placeholder retained from a DOM text value, not an evaluated parameter. */
public record TemplateToken(String token, String expression, String kind) {}
