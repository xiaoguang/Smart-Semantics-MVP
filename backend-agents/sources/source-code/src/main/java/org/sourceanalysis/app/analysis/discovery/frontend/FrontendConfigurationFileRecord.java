package org.sourceanalysis.app.analysis.discovery.frontend;

/** One selected frontend configuration source, retained as R0 identity without evaluating it. */
public record FrontendConfigurationFileRecord(String path, String sourceSha256) {

  public FrontendConfigurationFileRecord {
    if (path == null
        || path.isBlank()
        || path.startsWith("/")
        || path.contains("..")
        || sourceSha256 == null
        || !sourceSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("frontend configuration file identity is invalid");
    }
  }
}
