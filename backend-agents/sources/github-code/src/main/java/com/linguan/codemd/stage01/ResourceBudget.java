package com.linguan.codemd.stage01;

/** Immutable upper limits accepted with a Stage 01 frozen repository request. */
public record ResourceBudget(int maxFiles, long maxTotalBytes, long maxFileBytes,
                             int maxAstNodes, int maxXmlNodes, int maxSqlChars,
                             int maxControlFlowNodes, int maxRecursionDepth) {
}
