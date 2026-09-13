package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** RED tests for selecting a bounded subset of discovered entries at the public graph seam. */
class ProgramGraphsSelectedEntryExecutionTest {

  @Test
  void collectsOnlySelectedEntryAndRetainsUnselectedDenominator(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("fixture"))) {
      List<String> entryIds = entryIds(fixture);
      String selectedEntryId = entryIds.get(0);
      CountingSession session =
          new CountingSession(
              fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId());

      ProgramGraphsReference graphs =
          new ProgramGraphsExecution(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .execute(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  session,
                  new ArtifactReference(
                      ArtifactId.parse("graph-profile:" + "0".repeat(64)),
                      Sha256Digest.parse("0".repeat(64))),
                  fixture.artifactControls(),
                  List.of(selectedEntryId));

      JavaCodeIndex reopened = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(graphs);
      assertThat(reopened.entries()).hasSize(2);
      assertThat(session.collectedEntryIds).containsExactly(selectedEntryId);
      assertThat(reopened.entries())
          .filteredOn(entry -> entry.seed().entryId().equals(selectedEntryId))
          .singleElement()
          .satisfies(entry -> assertThat(entry.context()).isNotNull());
      assertThat(reopened.entries())
          .filteredOn(entry -> !entry.seed().entryId().equals(selectedEntryId))
          .singleElement()
          .satisfies(
              entry -> {
                assertThat(entry.context()).isNull();
                assertThat(entry.reason()).isEqualTo("NOT_SELECTED_FOR_SAMPLE");
                assertThat(entry.collectionStatus()).isEqualTo("NOT_COLLECTED");
              });
    }
  }

  @Test
  void rejectsUnknownSelectedEntryBeforeCollectingAnyEntry(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("fixture"))) {
      List<String> entryIds = entryIds(fixture);
      String unknownEntryId = "entry:" + "f".repeat(64);
      assertThat(entryIds).doesNotContain(unknownEntryId);
      CountingSession session =
          new CountingSession(
              fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId());

      assertThatThrownBy(
              () ->
                  new ProgramGraphsExecution(
                          fixture.sourceReader(),
                          fixture.moduleArtifacts(),
                          fixture.stepArtifacts())
                      .execute(
                          fixture.sourceInventory(),
                          fixture.applicationDiscovery(),
                          session,
                          new ArtifactReference(
                              ArtifactId.parse("graph-profile:" + "0".repeat(64)),
                              Sha256Digest.parse("0".repeat(64))),
                          fixture.artifactControls(),
                          List.of(unknownEntryId)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(session.collectedEntryIds).isEmpty();
    }
  }

  private static List<String> entryIds(ProgramGraphsPublicFixture fixture) {
    ReopenedAnalysisStepPublication discovery =
        fixture.stepArtifacts().reopen(fixture.applicationDiscovery().publication());
    VerifiedCanonicalPayload payload =
        discovery.semanticPayloads().stream()
            .filter(value -> "entry-points.jsonl".equals(value.descriptor().fileName()))
            .findFirst()
            .orElseThrow();
    String content = new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    List<String> ids = new ArrayList<>();
    for (String line : content.strip().split("\\R")) {
      JsonNode entry =
          new CanonicalJsonCodec()
              .parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
      ids.add(entry.path("entryId").textValue());
    }
    return List.copyOf(ids);
  }

  private static final class CountingSession implements JavaCodeSession {
    private final String snapshotId;
    private final List<String> collectedEntryIds = new ArrayList<>();

    private CountingSession(String snapshotId) {
      this.snapshotId = snapshotId;
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
      collectedEntryIds.add(entry.entryId());
      EntryCodeContext.SourceSource source =
          new EntryCodeContext.SourceSource(
              "src/main/java/com/example/OrderController.java",
              entry.methodRange(),
              "public Object entry() { return status; }");
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
          "selected-entry-test-adapter-v1",
          Map.of("jdtls", "1.61.0"),
          "17",
          List.of("METHODS"));
    }

    @Override
    public void close() {}
  }
}
