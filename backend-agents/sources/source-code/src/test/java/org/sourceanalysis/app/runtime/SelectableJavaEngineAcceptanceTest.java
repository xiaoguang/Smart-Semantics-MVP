package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.javaparser.JavaParserCodeEngine;
import org.sourceanalysis.app.analysis.code.jdt.JdtCodeEngine;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.artifact.ArtifactStoreException;

/** Final selection and identity checks shared by the two configured Java engines. */
class SelectableJavaEngineAcceptanceTest {

  @TempDir Path temporaryDirectory;

  @Test
  void yamlSelectsExactlyOneAdapterAndJavaParserDoesNotRequireJdtConfiguration() throws Exception {
    ToolFixture tools = executableJdtFixture();
    EffectiveEngineConfiguration jdt =
        new EngineConfigurationLoader().load(yaml("jdt", tools.installation(), tools.javaHome()));
    EffectiveEngineConfiguration javaParser =
        new EngineConfigurationLoader()
            .load("sourceAnalysis:\n  javaEngine: javaparser\n".getBytes(StandardCharsets.UTF_8));

    assertThat(new JavaCodeEngineFactory().create(jdt)).isExactlyInstanceOf(JdtCodeEngine.class);
    assertThat(new JavaCodeEngineFactory().create(javaParser))
        .isExactlyInstanceOf(JavaParserCodeEngine.class);
    assertThat(javaParser.jdt()).isNull();
  }

  @Test
  void persistedJavaIndexIdentityIncludesTheSelectedEngineDescriptor() {
    String jdtId;
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporaryDirectory.resolve("jdt"))) {
      jdtId = publishIndex(fixture, "jdt");
    }
    String javaParserId;
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(
            temporaryDirectory.resolve("javaparser"))) {
      javaParserId = publishIndex(fixture, "javaparser");
    }

    assertThat(javaParserId).isNotEqualTo(jdtId);
  }

  @Test
  void changingTheSelectedEngineRequiresANewRunAndCannotOverwriteThePersistedIndex() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(
            temporaryDirectory.resolve("same-run-engine-change"))) {
      ProgramGraphsReference original = publishGraphs(fixture, "jdt");
      String originalId = indexId(fixture, original);

      assertThatThrownBy(() -> publishGraphs(fixture, "javaparser"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("JAVA_CODE_INDEX_INVALID")
          .rootCause()
          .isInstanceOf(ArtifactStoreException.class)
          .hasMessage("MODULE_PUBLICATION_COLLISION");

      assertThat(indexId(fixture, original)).isEqualTo(originalId);
    }
  }

  private static String publishIndex(ProgramGraphsPublicFixture fixture, String engineId) {
    return indexId(fixture, publishGraphs(fixture, engineId));
  }

  private static ProgramGraphsReference publishGraphs(
      ProgramGraphsPublicFixture fixture, String engineId) {
    String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
    try (JavaCodeSession session = sourceOnlySession(snapshotId, engineId)) {
      return new ProgramGraphsExecution(
              fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
          .execute(
              fixture.sourceInventory(),
              fixture.applicationDiscovery(),
              session,
              fixture.artifactControls());
    }
  }

  private static String indexId(ProgramGraphsPublicFixture fixture, ProgramGraphsReference graphs) {
    return fixture
        .stepArtifacts()
        .reopen(graphs.publication())
        .semanticPayloads()
        .get(0)
        .descriptor()
        .artifactId()
        .value();
  }

  private static JavaCodeSession sourceOnlySession(String snapshotId, String engineId) {
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return new JavaDeclarationCatalog(
            snapshotId,
            List.of("src/main/java/com/example/OrderController.java"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of());
      }

      @Override
      public EntryCodeContext collect(EntrySeed entry) {
        EntryCodeContext.MethodCode method =
            new EntryCodeContext.MethodCode(
                entry.methodKey(),
                "METHOD",
                "com.example.OrderController",
                "entry",
                List.of(),
                "Object",
                new EntryCodeContext.SourceSource(
                    "src/main/java/com/example/OrderController.java",
                    entry.methodRange(),
                    "Object entry() { return service.call(); }"),
                true);
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            entry.methodKey(),
            List.of(method),
            List.of(),
            List.of(),
            List.of(),
            new EntryCodeContext.TechnicalEnhancements(
                EntryCodeContext.Availability.NOT_PRODUCED,
                "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_IDENTITY_TEST",
                List.of(),
                List.of(),
                null));
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            engineId,
            engineId + "-acceptance-v1",
            Map.of(engineId, "test-version"),
            "17",
            List.of("METHOD_SOURCE"));
      }

      @Override
      public void close() {}
    };
  }

  private ToolFixture executableJdtFixture() throws Exception {
    Path installation = Files.createDirectories(temporaryDirectory.resolve("tools/jdtls"));
    Path javaHome = Files.createDirectories(temporaryDirectory.resolve("tools/jdk"));
    Path java = Files.createDirectories(javaHome.resolve("bin")).resolve("java");
    Files.createDirectories(installation.resolve("plugins"));
    Files.writeString(
        installation.resolve("plugins/org.eclipse.equinox.launcher_1.0.jar"), "launcher\n");
    Files.writeString(
        installation.resolve("plugins/org.eclipse.jdt.ls.core_1.0.jar"), "jdt-ls-core\n");
    Files.writeString(installation.resolve("plugins/org.eclipse.jdt.core_1.0.jar"), "jdt-core\n");
    Files.createDirectories(installation.resolve(platformConfiguration()));
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
    return new ToolFixture(installation, javaHome);
  }

  private static String platformConfiguration() {
    String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    if (os.contains("mac")) return "config_mac";
    if (os.contains("win")) return "config_win";
    return "config_linux";
  }

  private static byte[] yaml(String engine, Path installation, Path javaHome) {
    return ("sourceAnalysis:\n"
            + "  javaEngine: "
            + engine
            + "\n"
            + "  jdt:\n"
            + "    installation: '"
            + installation
            + "'\n"
            + "    javaHome: '"
            + javaHome
            + "'\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private record ToolFixture(Path installation, Path javaHome) {}
}
