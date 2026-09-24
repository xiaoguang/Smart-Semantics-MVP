package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Step05ProcessSampleDriverTest {

  private static final String DRIVER_CLASS =
      "org.sourceanalysis.app.adapter.cli.Step05ProcessSampleDriver";

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsMalformedCatalogAndSelectedArgumentsBeforeConfigurationOrProvider() throws Exception {
    Path compiledDriver = compileDriverSource();

    Path missingConfiguration = temporaryDirectory.resolve("does-not-exist.yaml").toAbsolutePath();
    Path outputDirectory = temporaryDirectory.resolve("sample-output").toAbsolutePath();
    String activityBatch = "analysis-run:" + "e".repeat(64);
    String candidateId = "process-candidate:" + "1".repeat(64);
    String[] malformedCatalog = {
      missingConfiguration.toString(),
      activityBatch,
      "catalog",
      "-",
      outputDirectory.toString(),
      "unexpected-label=" + candidateId
    };
    String[] missingSelectedSource = {
      missingConfiguration.toString(),
      activityBatch,
      "selected",
      "-",
      outputDirectory.toString(),
      "sample=" + candidateId
    };

    assertInvalidArguments(runDriver(compiledDriver, "catalog", malformedCatalog));
    assertInvalidArguments(runDriver(compiledDriver, "selected", missingSelectedSource));
  }

  @Test
  void selectedCandidateLabelsRetainTheirInputOrderForTheManifest() throws Exception {
    Path compiledDriver = compileDriverSource();
    try (URLClassLoader loader =
        new URLClassLoader(
            new java.net.URL[] {compiledDriver.toUri().toURL()}, getClass().getClassLoader())) {
      Class<?> driver = Class.forName(DRIVER_CLASS, true, loader);
      var parser = driver.getDeclaredMethod("parse", String[].class);
      parser.setAccessible(true);
      Map<String, String> candidatesByLabel =
          Map.of(
              "alpha", "process-candidate:" + "1".repeat(64),
              "mu", "process-candidate:" + "2".repeat(64),
              "zeta", "process-candidate:" + "3".repeat(64));
      List<List<String>> labelOrders =
          List.of(
              List.of("alpha", "mu", "zeta"),
              List.of("alpha", "zeta", "mu"),
              List.of("mu", "alpha", "zeta"),
              List.of("mu", "zeta", "alpha"),
              List.of("zeta", "alpha", "mu"),
              List.of("zeta", "mu", "alpha"));

      for (List<String> labels : labelOrders) {
        String[] arguments = selectedArguments(labels, candidatesByLabel);
        Object parsed = parser.invoke(null, (Object) arguments);
        var selectedAccessor = parsed.getClass().getDeclaredMethod("selected");
        selectedAccessor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> selected = (Map<String, String>) selectedAccessor.invoke(parsed);

        assertThat(new ArrayList<>(selected.keySet()))
            .as("selected manifest rows preserve CLI label order %s", labels)
            .containsExactlyElementsOf(labels);
        assertThat(new ArrayList<>(selected.entrySet()))
            .containsExactlyElementsOf(
                labels.stream()
                    .map(label -> Map.entry(label, candidatesByLabel.get(label)))
                    .toList());
      }
    }
  }

  @Test
  void rejectsAnExistingOutputDirectoryBeforeLoadingConfigurationOrProvider() throws Exception {
    Path compiledDriver = compileDriverSource();
    Path missingConfiguration = temporaryDirectory.resolve("does-not-exist.yaml").toAbsolutePath();
    Path existingOutput = Files.createDirectories(temporaryDirectory.resolve("already-exists"));
    String[] arguments = {
      missingConfiguration.toString(),
      "analysis-run:" + "e".repeat(64),
      "catalog",
      "-",
      existingOutput.toString()
    };

    ProcessOutput result = runDriver(compiledDriver, "existing-output", arguments);

    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.stderr())
        .contains("STEP05_SAMPLE_ARGUMENTS_INVALID")
        .doesNotContain(
            "CONFIGURATION_INVALID",
            "MODEL_AUTH_ENV_MISSING",
            "MODEL_PROVIDER",
            "does-not-exist.yaml");
    assertThat(result.stdout()).doesNotContain("allocatedModelBatchId");
  }

  @Test
  void rejectsAnOutputWhoseParentDoesNotExistBeforeLoadingConfigurationOrProvider()
      throws Exception {
    Path compiledDriver = compileDriverSource();
    Path missingConfiguration = temporaryDirectory.resolve("does-not-exist.yaml").toAbsolutePath();
    Path missingParent = temporaryDirectory.resolve("missing-parent");
    Path output = missingParent.resolve("sample-output").toAbsolutePath();
    String[] arguments = {
      missingConfiguration.toString(),
      "analysis-run:" + "e".repeat(64),
      "catalog",
      "-",
      output.toString()
    };

    ProcessOutput result = runDriver(compiledDriver, "missing-output-parent", arguments);

    assertInvalidArguments(result);
    assertThat(result.stdout()).doesNotContain("allocatedModelBatchId");
    assertThat(missingParent).doesNotExist();
  }

  private Path compileDriverSource() throws IOException, URISyntaxException {
    Path driverSource =
        Path.of("tools/repository-run/acceptance/Step05ProcessSampleDriver.java")
            .toAbsolutePath()
            .normalize();
    assertThat(driverSource)
        .as("the standalone Step05 sample driver source must be available to compile")
        .isRegularFile();
    Path compiledDriver = Files.createDirectories(temporaryDirectory.resolve("compiled-driver"));
    compileDriver(driverSource, compiledDriver);
    return compiledDriver;
  }

  private String[] selectedArguments(List<String> labels, Map<String, String> candidatesByLabel) {
    List<String> arguments =
        new ArrayList<>(
            List.of(
                "/absolute/repository-run.yaml",
                "analysis-run:" + "e".repeat(64),
                "selected",
                "analysis-run:" + "f".repeat(64),
                temporaryDirectory.resolve("sample-output").toAbsolutePath().toString()));
    labels.forEach(label -> arguments.add(label + "=" + candidatesByLabel.get(label)));
    return arguments.toArray(String[]::new);
  }

  private void compileDriver(Path driverSource, Path compiledDriver)
      throws IOException, URISyntaxException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler)
        .as("a JDK compiler is required for this standalone tool check")
        .isNotNull();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
      Iterable<? extends JavaFileObject> sources =
          fileManager.getJavaFileObjects(driverSource.toFile());
      List<String> options =
          List.of("-classpath", testClasspath(compiledDriver), "-d", compiledDriver.toString());
      boolean compiled =
          compiler.getTask(null, fileManager, diagnostics, options, null, sources).call();
      assertThat(compiled)
          .as("standalone driver compilation diagnostics: " + diagnostics.getDiagnostics())
          .isTrue();
    }
  }

  private ProcessOutput runDriver(Path compiledDriver, String suffix, String[] arguments)
      throws Exception {
    Path stdout = temporaryDirectory.resolve(suffix + ".stdout");
    Path stderr = temporaryDirectory.resolve(suffix + ".stderr");
    List<String> command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                testClasspath(compiledDriver),
                DRIVER_CLASS));
    command.addAll(List.of(arguments));
    Process process =
        new ProcessBuilder(command)
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .start();
    boolean finished = process.waitFor(Duration.ofSeconds(15).toMillis(), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
    }
    assertThat(finished).as("sample driver process should exit promptly").isTrue();
    return new ProcessOutput(
        process.exitValue(), Files.readString(stdout), Files.readString(stderr));
  }

  private void assertInvalidArguments(ProcessOutput result) {
    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.stderr())
        .contains("STEP05_SAMPLE_ARGUMENTS_INVALID")
        .doesNotContain(
            "CONFIGURATION_INVALID",
            "MODEL_AUTH_ENV_MISSING",
            "MODEL_PROVIDER",
            "does-not-exist.yaml");
  }

  private String testClasspath(Path compiledDriver) throws URISyntaxException {
    String configuredClasspath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path", ""));
    Path mainClasses =
        Path.of(
            SourceAnalysisCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path testClasses =
        Path.of(
            Step05ProcessSampleDriverTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    return String.join(
        File.pathSeparator,
        compiledDriver.toString(),
        mainClasses.toString(),
        testClasses.toString(),
        configuredClasspath);
  }

  private record ProcessOutput(int exitCode, String stdout, String stderr) {}
}
