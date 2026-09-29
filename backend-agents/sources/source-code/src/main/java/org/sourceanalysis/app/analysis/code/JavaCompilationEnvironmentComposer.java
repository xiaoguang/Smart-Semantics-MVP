package org.sourceanalysis.app.analysis.code;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;

/**
 * Composes one immutable, module-isolated Java analysis environment from explicit external input.
 *
 * <p>This adapter receives already exported classpath paths and declared target platforms. It does
 * not read Maven models, POMs, settings, repositories, or caches.
 */
final class JavaCompilationEnvironmentComposer {

  JavaCompilationEnvironmentResult compose(
      VerifiedSourceTextSet sourceTexts, List<JavaCompilationInputModule> configuredModules) {
    Objects.requireNonNull(sourceTexts, "verified source texts");
    List<JavaCompilationInputModule> modules =
        List.copyOf(Objects.requireNonNull(configuredModules, "configured Java modules"));
    List<JavaCompilationEnvironmentProblem> problems = new ArrayList<>();
    Map<String, JavaCompilationInputModule> byModule = uniqueModules(modules, problems);
    Set<String> ownedSourcePaths = new LinkedHashSet<>();
    Map<String, List<String>> moduleSources = new LinkedHashMap<>();
    for (JavaCompilationInputModule module : byModule.values()) {
      if (!module
          .compilationTarget()
          .acceptsPlatformVersion(module.targetPlatform().targetJdkVersion())) {
        problems.add(
            problem(
                "JAVA_COMPILATION_TARGET_UNRESOLVED",
                module.modulePath(),
                "declared compiler release does not match the selected target platform"));
      }
      List<String> sources = admittedJavaSources(module, sourceTexts, problems);
      moduleSources.put(module.modulePath(), sources);
      for (String source : sources) {
        if (!ownedSourcePaths.add(source)) {
          problems.add(
              problem(
                  "JAVA_SOURCE_PARTITION_INVALID",
                  module.modulePath(),
                  "source path is admitted by more than one configured module"));
        }
      }
      validateSourceEdges(module, byModule.keySet(), problems);
    }
    if (!problems.isEmpty()) {
      return blocked(problems);
    }

    List<JavaCompilationModuleEnvironment> environmentModules = new ArrayList<>();
    for (JavaCompilationInputModule module : byModule.values()) {
      try {
        VerifiedJavaProject project =
            VerifiedJavaProject.fromVerifiedSourceTextSetPartition(
                sourceTexts,
                module.sourceRoots(),
                moduleSources.get(module.modulePath()),
                module.classpath(),
                module.compilationTarget().sourceLevel());
        environmentModules.add(
            new JavaCompilationModuleEnvironment(
                module.modulePath(),
                project,
                module.classpathExportDigest(),
                module.targetPlatform(),
                module.compilationTarget(),
                module.sourceModuleDependencies()));
      } catch (RuntimeException invalid) {
        problems.add(
            problem(
                "JAVA_COMPILATION_INPUT_INVALID",
                module.modulePath(),
                "configured module cannot be projected from verified source and classpath input"));
      }
    }
    if (!problems.isEmpty()) {
      return blocked(problems);
    }
    return new JavaCompilationEnvironmentResult(
        JavaCompilationEnvironmentStatus.READY,
        new JavaCompilationEnvironment(sourceTexts.snapshotId(), environmentModules),
        List.of());
  }

  private static Map<String, JavaCompilationInputModule> uniqueModules(
      List<JavaCompilationInputModule> configured,
      List<JavaCompilationEnvironmentProblem> problems) {
    Map<String, JavaCompilationInputModule> modules = new LinkedHashMap<>();
    for (JavaCompilationInputModule module : configured) {
      JavaCompilationInputModule previous = modules.putIfAbsent(module.modulePath(), module);
      if (previous != null) {
        problems.add(
            problem(
                "JAVA_COMPILATION_MODULES_INVALID",
                module.modulePath(),
                "external compilation input contains a duplicate module"));
      }
    }
    if (modules.isEmpty()) {
      problems.add(
          problem("JAVA_COMPILATION_MODULES_INVALID", "external compilation input has no modules"));
    }
    return modules;
  }

  private static List<String> admittedJavaSources(
      JavaCompilationInputModule module,
      VerifiedSourceTextSet sourceTexts,
      List<JavaCompilationEnvironmentProblem> problems) {
    List<String> sources =
        sourceTexts.documents().stream()
            .map(VerifiedSourceTextDocument::path)
            .filter(path -> path.endsWith(".java"))
            .filter(path -> underAnyRoot(path, module.sourceRoots()))
            .toList();
    if (sources.isEmpty()) {
      problems.add(
          problem(
              "JAVA_SOURCE_PARTITION_INVALID",
              module.modulePath(),
              "configured source roots contain no verified Java source"));
    }
    return sources;
  }

  private static boolean underAnyRoot(String path, List<String> roots) {
    return roots.stream().anyMatch(root -> root.equals(".") || path.startsWith(root + "/"));
  }

  private static void validateSourceEdges(
      JavaCompilationInputModule module,
      Set<String> modulePaths,
      List<JavaCompilationEnvironmentProblem> problems) {
    for (SourceModuleDependency edge : module.sourceModuleDependencies()) {
      if (edge.modulePath().equals(module.modulePath())
          || !modulePaths.contains(edge.modulePath())) {
        problems.add(
            problem(
                "JAVA_SOURCE_MODULE_EDGE_INVALID",
                module.modulePath(),
                "source-module edge is not a distinct selected module"));
      }
    }
  }

  private static JavaCompilationEnvironmentResult blocked(
      List<JavaCompilationEnvironmentProblem> problems) {
    return new JavaCompilationEnvironmentResult(
        JavaCompilationEnvironmentStatus.BLOCKED, null, List.copyOf(problems));
  }

  private static JavaCompilationEnvironmentProblem problem(String code, String detail) {
    return new JavaCompilationEnvironmentProblem(code, null, detail);
  }

  private static JavaCompilationEnvironmentProblem problem(
      String code, String modulePath, String detail) {
    return new JavaCompilationEnvironmentProblem(code, modulePath, detail);
  }
}

record JavaCompilationInputModule(
    String modulePath,
    List<String> sourceRoots,
    List<java.nio.file.Path> classpath,
    String classpathExportDigest,
    JavaModuleTargetPlatform targetPlatform,
    JavaCompilationTarget compilationTarget,
    List<SourceModuleDependency> sourceModuleDependencies) {

  JavaCompilationInputModule {
    if (modulePath == null || modulePath.isBlank()) {
      throw new IllegalArgumentException("external compilation module path is required");
    }
    sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "external module source roots"));
    classpath = List.copyOf(Objects.requireNonNull(classpath, "external module classpath"));
    if (classpathExportDigest == null || !classpathExportDigest.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("external classpath export digest is required");
    }
    targetPlatform = Objects.requireNonNull(targetPlatform, "external module target platform");
    compilationTarget =
        Objects.requireNonNull(compilationTarget, "external module compilation target");
    sourceModuleDependencies =
        List.copyOf(
            Objects.requireNonNull(sourceModuleDependencies, "external module source edges"));
  }
}

record JavaCompilationEnvironmentResult(
    JavaCompilationEnvironmentStatus status,
    JavaCompilationEnvironment environment,
    List<JavaCompilationEnvironmentProblem> problems) {

  JavaCompilationEnvironmentResult {
    status = Objects.requireNonNull(status, "Java compilation environment status");
    problems =
        List.copyOf(Objects.requireNonNull(problems, "Java compilation environment problems"));
    if ((status == JavaCompilationEnvironmentStatus.READY) != (environment != null)) {
      throw new IllegalArgumentException(
          "Java compilation environment status and payload disagree");
    }
    if (status == JavaCompilationEnvironmentStatus.READY && !problems.isEmpty()) {
      throw new IllegalArgumentException("ready Java compilation environment cannot have problems");
    }
  }
}

record JavaCompilationEnvironmentProblem(String code, String modulePath, String detail) {

  JavaCompilationEnvironmentProblem {
    if (code == null || code.isBlank() || detail == null || detail.isBlank()) {
      throw new IllegalArgumentException(
          "Java compilation environment problem requires code and detail");
    }
    if (modulePath != null && (modulePath.isBlank() || modulePath.startsWith("/"))) {
      throw new IllegalArgumentException("Java compilation environment problem module is invalid");
    }
  }
}
