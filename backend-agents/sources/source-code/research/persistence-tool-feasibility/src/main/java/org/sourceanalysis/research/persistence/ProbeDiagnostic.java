package org.sourceanalysis.research.persistence;

/** A resource-local finding. Diagnostic text excludes source values and external entity content. */
public record ProbeDiagnostic(String code, String resourcePath, String message) {}
