package org.sourceanalysis.app.analysis.code;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** An actual JDK installation and execution environment declared for one source module. */
public record JavaModuleTargetPlatform(
    String modulePath,
    String executionEnvironmentName,
    Path targetJdkHome,
    String targetJdkVersion) {

  public JavaModuleTargetPlatform {
    if (modulePath == null || modulePath.isBlank()) {
      throw new IllegalArgumentException("target-platform module path is required");
    }
    if (executionEnvironmentName == null || executionEnvironmentName.isBlank()) {
      throw new IllegalArgumentException("target-platform execution environment is required");
    }
    targetJdkVersion = JavaCompilationTarget.normalizedVersion(targetJdkVersion);
    try {
      targetJdkHome = Objects.requireNonNull(targetJdkHome, "target JDK home").toRealPath();
      if (!Files.isRegularFile(targetJdkHome.resolve("bin/java"))) {
        throw new IllegalArgumentException("target JDK home has no java launcher");
      }
      String actualVersion = releaseVersion(targetJdkHome);
      if (!targetJdkVersion.equals(actualVersion)) {
        throw new IllegalArgumentException("target JDK version does not match its selected home");
      }
      if (!expectedExecutionEnvironment(targetJdkVersion).equals(executionEnvironmentName)) {
        throw new IllegalArgumentException(
            "target execution environment does not match target JDK version");
      }
    } catch (IOException invalid) {
      throw new IllegalArgumentException("target JDK home cannot be verified", invalid);
    }
  }

  /**
   * Reads the concrete target platform identity from an explicitly selected JDK installation.
   *
   * <p>The caller selects only the JDK home. The version and JDT execution-environment name are
   * derived from that installation's {@code release} metadata rather than supplied as a second
   * handoff declaration.
   */
  static JavaModuleTargetPlatform fromTargetJdkHome(String modulePath, Path targetJdkHome) {
    try {
      Path realHome = Objects.requireNonNull(targetJdkHome, "target JDK home").toRealPath();
      if (!Files.isRegularFile(realHome.resolve("bin/java"))) {
        throw new IllegalArgumentException("target JDK home has no java launcher");
      }
      String version = releaseVersion(realHome);
      return new JavaModuleTargetPlatform(
          modulePath, expectedExecutionEnvironment(version), realHome, version);
    } catch (IOException invalid) {
      throw new IllegalArgumentException("target JDK home cannot be verified", invalid);
    }
  }

  private static String releaseVersion(Path home) throws IOException {
    for (String line : Files.readAllLines(home.resolve("release"))) {
      if (line.startsWith("JAVA_VERSION=")) {
        String raw = line.substring("JAVA_VERSION=".length()).replace("\"", "");
        return JavaCompilationTarget.normalizedVersion(raw);
      }
    }
    throw new IllegalArgumentException("target JDK release metadata has no JAVA_VERSION");
  }

  private static String expectedExecutionEnvironment(String version) {
    return Integer.parseInt(version) <= 8 ? "JavaSE-1." + version : "JavaSE-" + version;
  }
}
