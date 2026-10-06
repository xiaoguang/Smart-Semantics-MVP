package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.List;

/** The finite, parser-neutral output from one frontend syntax-tool invocation. */
public record FrontendSyntaxScan(
    List<String> parsedSourcePaths,
    List<FrontendRequestObservation> requestObservations,
    List<FrontendPageContext> pageContexts,
    List<FrontendDiagnosticRecord> diagnostics,
    List<FrontendSourceFileDisposition> fileDispositions) {

  public FrontendSyntaxScan {
    parsedSourcePaths = List.copyOf(parsedSourcePaths);
    requestObservations = List.copyOf(requestObservations);
    pageContexts = List.copyOf(pageContexts);
    diagnostics = List.copyOf(diagnostics);
    fileDispositions = List.copyOf(fileDispositions);
  }

  /**
   * Retains callers and fixtures written before page-instance contexts entered the private
   * protocol.
   */
  public FrontendSyntaxScan(
      List<String> parsedSourcePaths,
      List<FrontendRequestObservation> requestObservations,
      List<FrontendDiagnosticRecord> diagnostics,
      List<FrontendSourceFileDisposition> fileDispositions) {
    this(parsedSourcePaths, requestObservations, List.of(), diagnostics, fileDispositions);
  }
}
