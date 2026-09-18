package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
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
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.persistence.PersistenceConfiguration;

/** Verifies the retained JDT-only technical continuation after retired producers are removed. */
class TechnicalAnalysisWorkflowTest {

  @Test
  void continuesThePersistedDiscoveryPrefixThroughNavigationPersistenceAndMaterials(
      @TempDir Path temporaryDirectory) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporaryDirectory.resolve("fixture"))) {
      var source = fixture.sourceReader().reopen(fixture.sourceInventory());
      String javaSource =
          source.documents().stream()
              .filter(value -> value.path().endsWith("OrderController.java"))
              .findFirst()
              .map(value -> new String(value.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8))
              .orElseThrow();
      try (JavaCodeSession session = new FixtureJdtSession(source.snapshotId(), javaSource)) {
        TechnicalAnalysisWorkflowResult result =
            new TechnicalAnalysisWorkflow(
                    fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
                .continueAfterDiscovery(
                    new TechnicalDiscoveryWorkflowResult(
                        fixture.sourceInventory(), fixture.applicationDiscovery()),
                    session,
                    fixture.artifactControls(),
                    PersistenceConfiguration.disabled(),
                    new CodeReadingMaterialProfile(64_000L, 16));

        assertThat(result.navigation()).isNotNull();
        assertThat(result.persistence()).isNotNull();
        assertThat(result.readingMaterials()).isNotNull();
        assertThat(
                fixture
                    .stepArtifacts()
                    .reopen(result.navigation().publication())
                    .semanticPayloads())
            .extracting(value -> value.descriptor().fileName())
            .containsExactly("java-code-index.jsonl");
        assertThat(fixture.stepArtifacts().reopen(result.persistence()).semanticPayloads())
            .extracting(value -> value.descriptor().fileName())
            .containsExactly("persistence-material-index.jsonl");
        assertThat(fixture.stepArtifacts().reopen(result.readingMaterials()).semanticPayloads())
            .extracting(value -> value.descriptor().fileName())
            .containsExactly("code-reading-materials.jsonl");
      }
    }
  }

  private static final class FixtureJdtSession implements JavaCodeSession {
    private final String snapshotId;
    private final String source;

    private FixtureJdtSession(String snapshotId, String source) {
      this.snapshotId = snapshotId;
      this.source = source;
    }

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
      SourceRange whole = new SourceRange(0, source.length(), 1, 1 + (int) source.lines().count());
      EntryCodeContext.MethodCode method =
          new EntryCodeContext.MethodCode(
              entry.methodKey(),
              "METHOD",
              "com.example.OrderController",
              "entry",
              List.of(),
              "Object",
              new EntryCodeContext.SourceSource(
                  "src/main/java/com/example/OrderController.java", whole, source),
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
              "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_TEST_JDT",
              List.of(),
              List.of(),
              null));
    }

    @Override
    public EngineDescriptor descriptor() {
      return new EngineDescriptor(
          "jdt",
          "technical-workflow-test-jdt-v1",
          Map.of("jdtls", "fixture"),
          "17",
          List.of("METHODS"));
    }

    @Override
    public void close() {}
  }
}
