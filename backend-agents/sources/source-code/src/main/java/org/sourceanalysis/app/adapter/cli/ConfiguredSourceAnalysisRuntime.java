package org.sourceanalysis.app.adapter.cli;

import java.io.PrintWriter;
import java.util.Objects;

/** Thin configured-runtime boundary behind the unique {@link SourceAnalysisCli} entry point. */
final class ConfiguredSourceAnalysisRuntime {

  private ConfiguredSourceAnalysisRuntime() {}

  /** Delegates one validated configured command to the application execution service. */
  public static int execute(String[] arguments, PrintWriter output, PrintWriter errors) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(errors, "errors");
    return SourceAnalysisExecution.execute(arguments, output, errors);
  }
}
