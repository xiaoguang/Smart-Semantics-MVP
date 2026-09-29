package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.List;

/** The finite, parser-neutral output from one frontend syntax-tool invocation. */
public record FrontendSyntaxScan(
    List<String> parsedSourcePaths,
    List<FrontendRequestObservation> requestObservations,
    List<FrontendDiagnosticRecord> diagnostics,
    List<FrontendSourceFileDisposition> fileDispositions) {

  public FrontendSyntaxScan {
    parsedSourcePaths = List.copyOf(parsedSourcePaths);
    requestObservations = List.copyOf(requestObservations);
    diagnostics = List.copyOf(diagnostics);
    fileDispositions = List.copyOf(fileDispositions);
  }
}
