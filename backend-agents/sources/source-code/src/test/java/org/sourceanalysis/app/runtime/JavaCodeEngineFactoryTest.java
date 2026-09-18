package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** RED contract tests for stage-one engine selection and failure isolation. */
class JavaCodeEngineFactoryTest {

  @TempDir Path temporaryDirectory;

  @Test
  void jdtSelectionCreatesTheJdtCodeEngine() throws Exception {
    ToolFixture tools = executableJdtFixture();
    Object configuration =
        EngineTestReflection.loadYaml(jdtYaml("jdt", tools.installation(), tools.javaHome()));

    Object engine = EngineTestReflection.factoryCreate(configuration);

    assertThat(engine).isNotNull();
    assertThat(engine.getClass().getName())
        .isEqualTo("org.sourceanalysis.app.analysis.code.jdt.JdtCodeEngine");
  }

  @Test
  void javaParserYamlSelectionIsRejectedBeforeEngineCreation() throws Exception {
    Path missingInstallation = temporaryDirectory.resolve("unused-installation");
    Path missingJavaHome = temporaryDirectory.resolve("unused-jdk");
    Throwable failure =
        EngineTestReflection.loadFailure(
            jdtYaml("javaparser", missingInstallation, missingJavaHome));

    assertThat(failure).isNotNull();
    assertThat(EngineTestReflection.codeOf(failure)).isEqualTo("ENGINE_CONFIGURATION_INVALID");
  }

  @Test
  void aJdtStartupOrIndexFailureIsReturnedWithoutJavaParserFallback() throws Exception {
    Path installation = Files.createDirectories(temporaryDirectory.resolve("jdtls"));
    Path javaHome = Files.createDirectories(temporaryDirectory.resolve("jdk"));
    Path fakeJava = Files.createDirectories(javaHome.resolve("bin")).resolve("java");
    Files.createDirectories(installation.resolve("plugins"));
    Files.writeString(
        installation.resolve("plugins/org.eclipse.equinox.launcher_1.0.jar"), "launcher\n");
    Files.writeString(
        installation.resolve("plugins/org.eclipse.jdt.ls.core_1.0.jar"), "jdt-ls-core\n");
    Files.writeString(installation.resolve("plugins/org.eclipse.jdt.core_1.0.jar"), "jdt-core\n");
    Files.createDirectories(installation.resolve("config_linux"));
    Files.createDirectories(installation.resolve("config_mac"));
    Files.createDirectories(installation.resolve("config_win"));
    Path processMarker = temporaryDirectory.resolve("jdt-started");
    String marker = processMarker.toString().replace("'", "'\"'\"'");
    Files.writeString(
        fakeJava,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"-version\" ]; then\n"
            + "  echo 'openjdk version \"21\"' >&2\n"
            + "  exit 0\n"
            + "fi\n"
            + "touch '"
            + marker
            + "'\n"
            + "echo JDT_STARTUP_FAILURE >&2\n"
            + "exit 71\n",
        StandardCharsets.UTF_8);
    assertThat(fakeJava.toFile().setExecutable(true, false)).isTrue();

    Object configuration = EngineTestReflection.loadYaml(jdtYaml("jdt", installation, javaHome));
    Object engine = EngineTestReflection.factoryCreate(configuration);
    Path snapshotRoot = Files.createDirectories(temporaryDirectory.resolve("snapshot"));
    Path sourceRoot = Files.createDirectories(snapshotRoot.resolve("src/main/java"));
    Path sourceFile =
        Files.createDirectories(sourceRoot.resolve("example")).resolve("Example.java");
    Files.writeString(sourceFile, "class Example {}\n", StandardCharsets.UTF_8);
    Path classpathEntry = Files.createFile(snapshotRoot.resolve("approved-dependency.jar"));
    Object project =
        EngineTestReflection.verifiedProject(
            snapshotRoot, sourceRoot, classpathEntry, "src/main/java/example/Example.java");

    Throwable failure = EngineTestReflection.openFailure(engine, project);

    assertThat(failure).as("JDT startup/index failure must be observable").isNotNull();
    assertThat(EngineTestReflection.codeOf(failure))
        .isNotEqualTo("ENGINE_NOT_INTEGRATED")
        .isNotEqualTo("JAVAPARSER_FALLBACK");
    assertThat(Files.exists(processMarker)).isTrue();
    assertThat(failure.getClass().getName())
        .isNotEqualTo("org.sourceanalysis.app.analysis.code.javaparser.JavaParserCodeEngine");
  }

  private ToolFixture executableJdtFixture() throws IOException {
    Path installation = Files.createDirectories(temporaryDirectory.resolve("tools/jdtls"));
    Path javaHome = Files.createDirectories(temporaryDirectory.resolve("tools/jdk"));
    Path java = Files.createDirectories(javaHome.resolve("bin")).resolve("java");
    Files.createDirectories(installation.resolve("plugins"));
    Files.writeString(
        installation.resolve("plugins/org.eclipse.equinox.launcher_1.0.jar"), "launcher\n");
    Files.writeString(
        installation.resolve("plugins/org.eclipse.jdt.ls.core_1.0.jar"), "jdt-ls-core\n");
    Files.writeString(installation.resolve("plugins/org.eclipse.jdt.core_1.0.jar"), "jdt-core\n");
    Files.createDirectories(installation.resolve("config_linux"));
    Files.createDirectories(installation.resolve("config_mac"));
    Files.createDirectories(installation.resolve("config_win"));
    Files.writeString(
        java,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"-version\" ]; then\n"
            + "  echo 'openjdk version \"21\"' >&2\n"
            + "  exit 0\n"
            + "fi\n"
            + "exit 71\n",
        StandardCharsets.UTF_8);
    assertThat(java.toFile().setExecutable(true, false)).isTrue();
    assertThat(Files.isExecutable(java)).isTrue();
    return new ToolFixture(installation, javaHome);
  }

  private static byte[] jdtYaml(String engine, Path installation, Path javaHome) {
    return ("sourceAnalysis:\n"
            + "  javaEngine: "
            + engine
            + "\n"
            + "  jdt:\n"
            + "    installation: '"
            + quote(installation.toString())
            + "'\n"
            + "    javaHome: '"
            + quote(javaHome.toString())
            + "'\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private static String quote(String value) {
    return value.replace("'", "''");
  }

  private record ToolFixture(Path installation, Path javaHome) {}
}
