package org.sourceanalysis.research.persistence;

/** A JSqlParser-backed projection or an explicit limitation for one Mapper statement. */
public record SqlAnalysis(
    String statementKey,
    String statementId,
    String resourcePath,
    String status,
    String analysisCopy,
    SqlStructure structure,
    String reason) {}
