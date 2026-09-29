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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.artifact.ArtifactControls;
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
                          fixture.artifactControls(),
                          List.of(unknownEntryId)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(session.collectedEntryIds).isEmpty();
    }
  }

  @Test
  void legacySameRunRejectsMismatchedControlsBeforeReadingOrCollectingTheJavaCatalog(
      @TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("fixture"))) {
      ArtifactControls acceptedControls = fixture.artifactControls();
      ArtifactControls mismatchedControls =
          new ArtifactControls(
              Sha256Digest.parse("f".repeat(64)),
              acceptedControls.profileSha256(),
              acceptedControls.schemaBundleSha256(),
              acceptedControls.promptBundleSha256(),
              acceptedControls.artifactPolicyRegistryRef());
      assertThat(mismatchedControls).isNotEqualTo(acceptedControls);
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
                          mismatchedControls))
          .isInstanceOfSatisfying(
              GraphReferenceException.class,
              failure -> assertThat(failure.getMessage()).isEqualTo("GRAPH_REFERENCE_BROKEN"));

      assertThat(session.catalogCalls).isZero();
      assertThat(session.collectedEntryIds).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        CodeEngineException.JDT_PROTOCOL_INVALID,
        CodeEngineException.ENGINE_CONFIGURATION_INVALID,
        CodeEngineException.SOURCE_INVALID,
        CodeEngineException.JDT_INDEX_FAILED,
        CodeEngineException.JDT_SYNTAX_PROTOCOL_INVALID,
        CodeEngineException.JDT_SYNTAX_PROCESS_FAILED
      })
  void abortsIndexPublicationWhenSharedToolFails(String failureCode, @TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(
            temporary.resolve(failureCode.toLowerCase()))) {
      CodeEngineException codeEngineFailure =
          new CodeEngineException(failureCode, "injected code engine failure");
      CountingSession session =
          new CountingSession(
              fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId(),
              codeEngineFailure);

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
                          fixture.artifactControls()))
          .isInstanceOfSatisfying(
              CodeEngineException.class,
              failure -> assertThat(failure.code()).isEqualTo(failureCode));
      assertThat(session.collectedEntryIds).hasSize(1);
    }
  }

  @Test
  void keepsJdtQueryFailuresEntryLocal(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("fixture"))) {
      List<String> expectedEntryIds = entryIds(fixture);
      CodeEngineException queryFailure =
          new CodeEngineException(CodeEngineException.JDT_QUERY_FAILED, "one entry query failed");
      CountingSession session =
          new CountingSession(
              fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId(), queryFailure);

      ProgramGraphsReference graphs =
          new ProgramGraphsExecution(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .execute(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  session,
                  fixture.artifactControls());

      JavaCodeIndex reopened = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(graphs);
      assertThat(session.collectedEntryIds).containsExactlyInAnyOrderElementsOf(expectedEntryIds);
      assertThat(reopened.entries()).hasSize(expectedEntryIds.size());
      assertThat(reopened.entries())
          .filteredOn(entry -> entry.collectionStatus().equals("NOT_COLLECTED"))
          .singleElement()
          .satisfies(
              entry -> {
                assertThat(entry.context()).isNull();
                assertThat(entry.collectionStatus()).isEqualTo("NOT_COLLECTED");
                assertThat(entry.reason()).contains(CodeEngineException.JDT_QUERY_FAILED);
              });
      assertThat(reopened.entries())
          .filteredOn(entry -> entry.collectionStatus().equals("COLLECTED"))
          .singleElement()
          .satisfies(entry -> assertThat(entry.context()).isNotNull());
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
    private final CodeEngineException collectionFailure;
    private final List<String> collectedEntryIds = new ArrayList<>();
    private int catalogCalls;

    private CountingSession(String snapshotId) {
      this(snapshotId, null);
    }

    private CountingSession(String snapshotId, CodeEngineException collectionFailure) {
      this.snapshotId = snapshotId;
      this.collectionFailure = collectionFailure;
    }

    @Override
    public JavaDeclarationCatalog catalog() {
      catalogCalls++;
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
      if (collectionFailure != null && collectedEntryIds.size() == 1) {
        throw collectionFailure;
      }
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
