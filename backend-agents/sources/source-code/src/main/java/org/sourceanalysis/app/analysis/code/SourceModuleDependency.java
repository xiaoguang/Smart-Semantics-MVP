package org.sourceanalysis.app.analysis.code;

/** An explicit source-to-source module edge supplied with the compilation input. */
public record SourceModuleDependency(String modulePath, String kind) {

  public SourceModuleDependency {
    if (modulePath == null || modulePath.isBlank() || !"SOURCE_MODULE".equals(kind)) {
      throw new IllegalArgumentException("source module dependency is invalid");
    }
  }
}
