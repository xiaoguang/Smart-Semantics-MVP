package org.sourceanalysis.app.analysis.discovery.frontend;

/**
 * Produces finite, source-bound frontend request-chain observations without exposing parser ASTs to
 * Java callers.
 */
@FunctionalInterface
public interface FrontendSyntaxTool {

  FrontendSyntaxScan scan(FrontendSyntaxInput input, FrontendHttpConfiguration configuration);
}
