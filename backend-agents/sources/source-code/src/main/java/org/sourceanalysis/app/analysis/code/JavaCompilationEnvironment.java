package org.sourceanalysis.app.analysis.code;

import java.util.List;
import java.util.Objects;

/** The module-isolated Java environment admitted from one selected source basis. */
public record JavaCompilationEnvironment(
    String sourceSnapshotId, List<JavaCompilationModuleEnvironment> modules) {

  public JavaCompilationEnvironment {
    if (sourceSnapshotId == null || sourceSnapshotId.isBlank()) {
      throw new IllegalArgumentException(
          "Java compilation environment source snapshot is required");
    }
    modules = List.copyOf(Objects.requireNonNull(modules, "Java compilation environment modules"));
    if (modules.isEmpty()) {
      throw new IllegalArgumentException("Java compilation environment modules are required");
    }
  }
}
