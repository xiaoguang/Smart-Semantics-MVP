package org.sourceanalysis.research.persistence;

/** A join directly exposed by a JSqlParser AST visitor. */
public record JoinProjection(String joinKind, String right, String on) {}
