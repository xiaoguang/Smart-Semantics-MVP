package org.sourceanalysis.app.analysis.code.jdt;

import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.JavaCompilationEnvironment;
import org.sourceanalysis.app.analysis.code.JavaCompilationModuleEnvironment;
import org.sourceanalysis.app.analysis.code.SourceModuleDependency;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;

/** Immutable, snapshot-bound input for one multi-project JDT language-server workspace. */
final class JdtWorkspaceBinding {

  private final List<ModuleBinding> modules;
  private final String snapshotId;
  private final Map<String, String> projectNamesByModulePath;

  JdtWorkspaceBinding(List<ModuleBinding> modules) {
    this.modules = List.copyOf(Objects.requireNonNull(modules, "JDT workspace modules"));
    if (this.modules.isEmpty()) {
      throw new IllegalArgumentException("JDT workspace requires at least one module");
    }
    snapshotId = this.modules.get(0).project().snapshotId();
    Object inventoryRef = this.modules.get(0).project().sourceInventoryRef();
    Map<String, String> names = new LinkedHashMap<>();
    Map<String, String> modulesByProjectName = new LinkedHashMap<>();
    Map<String, String> ownersBySourcePath = new LinkedHashMap<>();
    Map<String, Path> targetHomesByExecutionEnvironment = new LinkedHashMap<>();
    for (ModuleBinding module : this.modules) {
      if (!snapshotId.equals(module.project().snapshotId())
          || !inventoryRef.equals(module.project().sourceInventoryRef())) {
        throw new IllegalArgumentException(
            "JDT workspace modules must share one verified source identity");
      }
      String projectName = stableProjectName(module.modulePath());
      if (names.putIfAbsent(module.modulePath(), projectName) != null) {
        throw new IllegalArgumentException("JDT workspace module paths must be unique");
      }
      if (modulesByProjectName.putIfAbsent(projectName, module.modulePath()) != null) {
        throw new IllegalArgumentException("JDT workspace project identities must be unique");
      }
      for (String sourcePath : module.project().sourceEntries()) {
        String prior = ownersBySourcePath.putIfAbsent(sourcePath, module.modulePath());
        if (prior != null) {
          throw new IllegalArgumentException(
              "one verified source path cannot belong to more than one JDT module");
        }
      }
      Path priorRuntime =
          targetHomesByExecutionEnvironment.putIfAbsent(
              module.targetRuntime().executionEnvironmentName(),
              module.targetRuntime().targetJdkHome());
      if (priorRuntime != null && !priorRuntime.equals(module.targetRuntime().targetJdkHome())) {
        throw new IllegalArgumentException(
            "one JDT execution environment cannot select multiple target JDK homes");
      }
    }
    for (ModuleBinding module : this.modules) {
      for (String dependencyPath : module.sourceModuleDependencyPaths()) {
        if (dependencyPath.equals(module.modulePath()) || !names.containsKey(dependencyPath)) {
          throw new IllegalArgumentException(
              "JDT source-module dependency is not a distinct workspace module");
        }
      }
    }
    projectNamesByModulePath = Map.copyOf(names);
  }

  /**
   * Adapts the already-admitted external compilation environment without recreating a project
   * model.
   */
  static JdtWorkspaceBinding from(JavaCompilationEnvironment environment) {
    Objects.requireNonNull(environment, "Java compilation environment");
    return new JdtWorkspaceBinding(
        environment.modules().stream()
            .map(module -> moduleBinding(environment.sourceSnapshotId(), module))
            .toList());
  }

  private static ModuleBinding moduleBinding(
      String sourceSnapshotId, JavaCompilationModuleEnvironment module) {
    if (!sourceSnapshotId.equals(module.project().snapshotId())) {
      throw new IllegalArgumentException(
          "Java compilation environment module does not match its source snapshot");
    }
    if (!module.modulePath().equals(module.targetPlatform().modulePath())) {
      throw new IllegalArgumentException(
          "Java compilation environment target platform does not match its module");
    }
    return new ModuleBinding(
        module.modulePath(),
        module.project(),
        new JdtTargetRuntime(
            module.targetPlatform().executionEnvironmentName(),
            module.targetPlatform().targetJdkHome()),
        module.sourceModuleDependencies().stream()
            .map(SourceModuleDependency::modulePath)
            .toList());
  }

  List<ModuleBinding> modules() {
    return modules;
  }

  String snapshotId() {
    return snapshotId;
  }

  String projectName(String modulePath) {
    String projectName = projectNamesByModulePath.get(modulePath);
    if (projectName == null) {
      throw new IllegalArgumentException("JDT workspace module is unknown");
    }
    return projectName;
  }

  /** One module's immutable source partition, target runtime, and direct project edges. */
  record ModuleBinding(
      String modulePath,
      VerifiedJavaProject project,
      JdtTargetRuntime targetRuntime,
      List<String> sourceModuleDependencyPaths) {

    ModuleBinding {
      modulePath = canonicalModulePath(modulePath);
      project = Objects.requireNonNull(project, "JDT workspace module project");
      targetRuntime = Objects.requireNonNull(targetRuntime, "JDT workspace module target runtime");
      sourceModuleDependencyPaths =
          List.copyOf(
              Objects.requireNonNull(
                  sourceModuleDependencyPaths, "JDT workspace source-module dependencies"));
      sourceModuleDependencyPaths =
          sourceModuleDependencyPaths.stream()
              .map(JdtWorkspaceBinding::canonicalModulePath)
              .toList();
      if (new LinkedHashSet<>(sourceModuleDependencyPaths).size()
          != sourceModuleDependencyPaths.size()) {
        throw new IllegalArgumentException(
            "JDT workspace source-module dependencies cannot repeat");
      }
    }
  }

  private static String canonicalModulePath(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("JDT workspace module path is required");
    }
    try {
      Path path = Path.of(value).normalize();
      if (path.isAbsolute() || path.startsWith("..")) {
        throw new IllegalArgumentException("JDT workspace module path must be snapshot relative");
      }
      String normalized = path.toString().replace('\\', '/');
      return normalized.isEmpty() || ".".equals(normalized) ? "." : normalized;
    } catch (InvalidPathException invalid) {
      throw new IllegalArgumentException("JDT workspace module path is invalid", invalid);
    }
  }

  private String stableProjectName(String modulePath) {
    try {
      byte[] identity = (snapshotId + '\u0000' + modulePath).getBytes(StandardCharsets.UTF_8);
      return "source-analysis-"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
