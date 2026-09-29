package org.sourceanalysis.app.analysis.code.jdt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.CodeEngineException;

/** One explicitly selected JDT execution environment and its canonical local JDK home. */
public record JdtTargetRuntime(String executionEnvironmentName, Path targetJdkHome) {

  public JdtTargetRuntime {
    if (executionEnvironmentName == null
        || executionEnvironmentName.isBlank()
        || executionEnvironmentName.indexOf('/') >= 0
        || executionEnvironmentName.indexOf('\\') >= 0) {
      throw invalid("JDT execution-environment name is invalid", null);
    }
    Objects.requireNonNull(targetJdkHome, "target JDK home");
    try {
      targetJdkHome = targetJdkHome.toRealPath();
    } catch (IOException failure) {
      throw invalid("JDT target JDK home cannot be resolved", failure);
    }
    if (!Files.isDirectory(targetJdkHome)
        || !Files.isExecutable(targetJdkHome.resolve("bin").resolve("java"))) {
      throw invalid("JDT target JDK home has no executable bin/java", null);
    }
  }

  String targetJdkVersion() {
    String prefix = "JavaSE-";
    if (!executionEnvironmentName.startsWith(prefix)) {
      throw invalid("JDT execution environment does not identify a JavaSE target version", null);
    }
    String version = executionEnvironmentName.substring(prefix.length());
    if (version.startsWith("1.")) {
      version = version.substring(2);
    }
    if (!version.matches("[1-9][0-9]*")) {
      throw invalid("JDT execution environment has an invalid JavaSE target version", null);
    }
    return version;
  }

  List<Path> targetPlatformEntries() {
    Path expectedArchive =
        targetJdkVersion().matches("[1-8]")
            ? targetJdkHome.resolve("jre").resolve("lib").resolve("rt.jar")
            : targetJdkHome.resolve("lib").resolve("jrt-fs.jar");
    final Path platformArchive;
    try {
      platformArchive = expectedArchive.toRealPath();
    } catch (IOException failure) {
      throw invalid("JDT target JDK platform archive cannot be resolved", failure);
    }
    if (!Files.isRegularFile(platformArchive) || !platformArchive.startsWith(targetJdkHome)) {
      throw invalid("JDT target JDK platform archive is invalid", null);
    }
    return List.of(platformArchive);
  }

  private static CodeEngineException invalid(String detail, Throwable cause) {
    return new CodeEngineException(CodeEngineException.ENGINE_CONFIGURATION_INVALID, detail, cause);
  }
}
