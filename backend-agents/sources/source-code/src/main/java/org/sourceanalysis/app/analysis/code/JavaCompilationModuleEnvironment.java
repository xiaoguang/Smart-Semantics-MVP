package org.sourceanalysis.app.analysis.code;

import java.util.List;
import java.util.Objects;

/** One source module's explicit project, target platform, and source-module edges. */
public record JavaCompilationModuleEnvironment(
    String modulePath,
    VerifiedJavaProject project,
    String classpathExportDigest,
    JavaModuleTargetPlatform targetPlatform,
    JavaCompilationTarget compilationTarget,
    List<SourceModuleDependency> sourceModuleDependencies) {

  public JavaCompilationModuleEnvironment {
    if (modulePath == null || modulePath.isBlank()) {
      throw new IllegalArgumentException("Java compilation module path is required");
    }
    project = Objects.requireNonNull(project, "Java compilation project");
    if (classpathExportDigest == null || !classpathExportDigest.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Java compilation classpath export digest is required");
    }
    targetPlatform = Objects.requireNonNull(targetPlatform, "Java target platform");
    compilationTarget = Objects.requireNonNull(compilationTarget, "Java compilation target");
    sourceModuleDependencies =
        List.copyOf(Objects.requireNonNull(sourceModuleDependencies, "source module dependencies"));
  }
}
