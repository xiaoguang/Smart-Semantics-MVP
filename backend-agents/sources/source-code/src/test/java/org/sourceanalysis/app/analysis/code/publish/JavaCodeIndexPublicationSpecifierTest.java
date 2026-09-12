package org.sourceanalysis.app.analysis.code.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.sourceanalysis.app.analysis.fact.ProvenCodeFactsExecutor;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

class JavaCodeIndexPublicationSpecifierTest {

  @Test
  void publishesReopensNavigationOnlyStepAndSkipsStrictFactEnumeration(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("fixture"))) {
      String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
      try (JavaCodeSession session = fakeSession(snapshotId)) {
        var graphs =
            new ProgramGraphsExecution(
                    fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
                .execute(
                    fixture.sourceInventory(),
                    fixture.applicationDiscovery(),
                    session,
                    fixture.artifactControls());

        var reopenedStep = fixture.stepArtifacts().reopen(graphs.publication());
        assertThat(reopenedStep.semanticPayloads())
            .singleElement()
            .satisfies(
                payload ->
                    assertThat(payload.descriptor().fileName()).isEqualTo("java-code-index.jsonl"));

        JavaCodeIndex index = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(graphs);
        assertThat(index.engine().engineId()).isEqualTo("jdt");
        assertThat(index.snapshotId()).isEqualTo(snapshotId);
        assertThat(index.entries()).hasSize(2).allMatch(value -> value.context() != null);
        assertThat(index.entries())
            .allSatisfy(
                entry -> {
                  assertThat(entry.context().entryId()).isEqualTo(entry.seed().entryId());
                  assertThat(entry.context().methods())
                      .extracting(EntryCodeContext.MethodCode::methodKey)
                      .containsExactly(entry.seed().methodKey());
                });

        var facts =
            new ProvenCodeFactsExecutor(
                    fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
                .execute(fixture.sourceInventory(), fixture.applicationDiscovery(), graphs);
        var reopenedFacts = fixture.stepArtifacts().reopen(facts.publication());
        assertThat(reopenedFacts.semanticPayloads())
            .singleElement()
            .satisfies(
                payload -> {
                  assertThat(payload.descriptor().fileName()).isEqualTo("fact-accounting.json");
                  JsonNode document =
                      new CanonicalJsonCodec().parseCanonical(payload.canonicalUtf8());
                  assertThat(document.path("schemaVersion").textValue())
                      .isEqualTo("proven-code-facts-fact-accounting-v4");
                  assertThat(document.path("availability").textValue()).isEqualTo("NOT_PRODUCED");
                  assertThat(document.path("reason").textValue()).isNotBlank();
                  assertThat(document.path("navigationInputRef").path("artifactId").textValue())
                      .isEqualTo(
                          reopenedStep.semanticPayloads().get(0).descriptor().artifactId().value());
                  assertThat(document.path("candidateFactCount").isNull()).isTrue();
                  assertThat(document.has("candidateDenominatorKeys")).isFalse();
                  assertThat(document.has("proofPackRef")).isFalse();
                });
      }
    }
  }

  private static JavaCodeSession fakeSession(String snapshotId) {
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_TEST_JDT",
            List.of(),
            List.of(),
            null);
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
        EntryCodeContext.SourceSource source =
            new EntryCodeContext.SourceSource(
                "src/main/java/com/example/OrderController.java",
                entry.methodRange(),
                "public Object entry() { return service.call(); }");
        EntryCodeContext.MethodCode method =
            new EntryCodeContext.MethodCode(
                entry.methodKey(),
                "METHOD",
                "com.example.OrderController",
                "entry",
                List.of(),
                "Object",
                source,
                true);
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            entry.methodKey(),
            List.of(method),
            List.of(),
            List.of(),
            List.of(),
            enhancements);
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "test-adapter-v1", Map.of("jdtls", "1.61.0"), "17", List.of("METHODS"));
      }

      @Override
      public void close() {}
    };
  }
}
