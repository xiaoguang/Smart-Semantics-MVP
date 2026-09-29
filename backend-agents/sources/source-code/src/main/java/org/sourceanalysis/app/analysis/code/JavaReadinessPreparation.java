package org.sourceanalysis.app.analysis.code;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/**
 * Narrow public handoff from an external compilation input to a technical run coordinator.
 *
 * <p>The preparation accepts a named JSON file only. It does not invoke Maven, interpret POMs, scan
 * a dependency cache, resolve artifacts, or download anything.
 */
public final class JavaReadinessPreparation {

  /** The two outcomes relevant before JDT opens the supplied environment. */
  public enum Status {
    READY,
    BLOCKED
  }

  /** A stable, path-free diagnostic retained in a module-five readiness publication. */
  public record Problem(String code, String detail) {

    public Problem {
      if (code == null || !code.matches("[A-Z][A-Z0-9_]*")) {
        throw new IllegalArgumentException("Java readiness problem code is invalid");
      }
      if (detail == null || detail.isBlank()) {
        throw new IllegalArgumentException("Java readiness problem detail is required");
      }
    }
  }

  /** The selected R0 and one externally prepared compilation-input file. */
  public record Request(
      VerifiedSourceTextSet sourceTexts,
      SelectedSourceBasis selectedSourceBasis,
      Path compilationInput) {

    public Request {
      sourceTexts = Objects.requireNonNull(sourceTexts, "verified source texts");
      selectedSourceBasis = Objects.requireNonNull(selectedSourceBasis, "selected source basis");
      if (!sourceTexts.snapshotId().equals(selectedSourceBasis.snapshotId().value())) {
        throw new IllegalArgumentException(
            "Java readiness source texts and selected source basis must share source identity");
      }
      compilationInput = Objects.requireNonNull(compilationInput, "external compilation input");
      if (!compilationInput.isAbsolute()) {
        throw new IllegalArgumentException("external compilation input path must be absolute");
      }
      compilationInput = compilationInput.normalize();
    }
  }

  /**
   * The raw, externally prepared Maven-output locations selected for one technical run.
   *
   * <p>This is the v2 production handoff. It deliberately contains no caller-declared source roots,
   * compiler setting, module edge, JDK version, or execution-environment value; the reader derives
   * those facts from the effective POM and the selected JDK installation.
   */
  public record CompilationInput(Path projectDirectory, List<ModuleInput> modules) {

    public CompilationInput {
      // Existence is an execution-time readiness condition: it must yield a persisted R1 BLOCKED
      // report rather than reject the configured handoff before the run is admitted.
      projectDirectory = absolutePath(projectDirectory, "Maven project directory");
      modules = List.copyOf(Objects.requireNonNull(modules, "external compilation modules"));
      if (modules.isEmpty()) {
        throw new IllegalArgumentException("external compilation modules are required");
      }
    }
  }

  /** One selected Maven module and its already produced files. */
  public record ModuleInput(
      String modulePath,
      Path classpathFile,
      String classpathSeparator,
      Path effectivePomFile,
      Path targetJavaHome) {

    public ModuleInput {
      if (modulePath == null || modulePath.isBlank()) {
        throw new IllegalArgumentException("Maven module path is required");
      }
      classpathFile = absolutePath(classpathFile, "Maven classpath file");
      if (classpathSeparator == null || classpathSeparator.length() != 1) {
        throw new IllegalArgumentException("Maven classpath separator must be one character");
      }
      effectivePomFile = absolutePath(effectivePomFile, "Maven effective POM file");
      targetJavaHome = absolutePath(targetJavaHome, "target Java home");
    }
  }

  /** The v2 request used by the formal YAML configuration path. */
  public record V2Request(
      VerifiedSourceTextSet sourceTexts,
      SelectedSourceBasis selectedSourceBasis,
      CompilationInput compilationInput) {

    public V2Request {
      sourceTexts = Objects.requireNonNull(sourceTexts, "verified source texts");
      selectedSourceBasis = Objects.requireNonNull(selectedSourceBasis, "selected source basis");
      if (!sourceTexts.snapshotId().equals(selectedSourceBasis.snapshotId().value())) {
        throw new IllegalArgumentException(
            "Java readiness source texts and selected source basis must share source identity");
      }
      compilationInput = Objects.requireNonNull(compilationInput, "external compilation input");
    }
  }

  /** The actual environment is retained for the coordinator's later JDT binding. */
  public record Result(
      Status status, JavaCompilationEnvironment environment, List<Problem> problems) {

    public Result {
      status = Objects.requireNonNull(status, "Java readiness status");
      problems = List.copyOf(Objects.requireNonNull(problems, "Java readiness problems"));
      if ((status == Status.READY) != (environment != null)) {
        throw new IllegalArgumentException("Java readiness status and environment disagree");
      }
      if (status == Status.READY && !problems.isEmpty()) {
        throw new IllegalArgumentException("ready Java environment cannot carry blockers");
      }
      if (status == Status.BLOCKED && problems.isEmpty()) {
        throw new IllegalArgumentException("blocked Java environment requires a blocker");
      }
    }
  }

  /**
   * Reads and validates the supplied external handoff without performing dependency preparation.
   */
  public Result prepare(Request request) {
    Objects.requireNonNull(request, "Java readiness preparation request");
    JavaCompilationEnvironmentResult result =
        new JavaCompilationInputReader()
            .read(request.compilationInput(), request.selectedSourceBasis(), request.sourceTexts());
    if (result.status() == JavaCompilationEnvironmentStatus.READY) {
      return new Result(Status.READY, result.environment(), List.of());
    }
    return new Result(
        Status.BLOCKED,
        null,
        result.problems().stream()
            .map(problem -> new Problem(problem.code(), readinessDetail(problem)))
            .toList());
  }

  /** Reads the v2 typed handoff without creating or consuming an intermediate JSON file. */
  public Result prepare(V2Request request) {
    Objects.requireNonNull(request, "Java readiness preparation request");
    JavaCompilationEnvironmentResult result =
        new JavaCompilationInputReader()
            .read(request.compilationInput(), request.selectedSourceBasis(), request.sourceTexts());
    if (result.status() == JavaCompilationEnvironmentStatus.READY) {
      return new Result(Status.READY, result.environment(), List.of());
    }
    return new Result(
        Status.BLOCKED,
        null,
        result.problems().stream()
            .map(problem -> new Problem(problem.code(), readinessDetail(problem)))
            .toList());
  }

  private static Path absolutePath(Path path, String label) {
    path = Objects.requireNonNull(path, label).normalize();
    if (!path.isAbsolute()) {
      throw new IllegalArgumentException(label + " must be absolute");
    }
    return path;
  }

  private static String readinessDetail(JavaCompilationEnvironmentProblem problem) {
    if (problem.modulePath() == null || problem.modulePath().isBlank()) {
      return problem.detail();
    }
    return "module " + problem.modulePath() + ": " + problem.detail();
  }
}
