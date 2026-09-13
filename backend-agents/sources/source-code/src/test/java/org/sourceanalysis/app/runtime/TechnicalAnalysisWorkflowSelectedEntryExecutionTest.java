package org.sourceanalysis.app.runtime;

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
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** RED for forwarding bounded entry selection through the public runtime workflow. */
class TechnicalAnalysisWorkflowSelectedEntryExecutionTest {

  @Test
  void forwardsRuntimeSelectionAndRejectsUnknownBeforeCollecting(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("selected"))) {
      String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
      String source =
          new String(
              fixture
                  .sourceReader()
                  .reopen(fixture.sourceInventory())
                  .documents()
                  .get(0)
                  .rawUtf8()
                  .copyToByteArray(),
              StandardCharsets.UTF_8);
      List<String> entryIds = entryIds(fixture);
      String selectedEntryId = entryIds.get(0);
      CountingSession selectedSession = new CountingSession(snapshotId, source);

      TechnicalAnalysisWorkflowResult result =
          new TechnicalAnalysisWorkflow(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .continueAfterDiscovery(
                  new TechnicalDiscoveryWorkflowResult(
                      fixture.sourceInventory(), fixture.applicationDiscovery()),
                  selectedSession,
                  reference("graph-profile", 'a', 'b'),
                  fixture.artifactControls(),
                  new FlowCompilationProfile(
                      reference("flow-profile", 'c', 'd'), 16, 8, 64, 96, 32, 64, 256),
                  new CapsuleProjectionProfile(
                      reference("capsule-profile", 'e', 'f'), 16, 32, 4_096, 100_000),
                  List.of(selectedEntryId));

      assertThat(selectedSession.collectedEntryIds).containsExactly(selectedEntryId);
      JavaCodeIndex selectedIndex =
          new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(result.programGraphs());
      assertThat(selectedIndex.entries()).hasSize(2);
      assertThat(selectedIndex.entries())
          .filteredOn(entry -> entry.seed().entryId().equals(selectedEntryId))
          .singleElement()
          .satisfies(entry -> assertThat(entry.context()).isNotNull());
      assertThat(selectedIndex.entries())
          .filteredOn(entry -> !entry.seed().entryId().equals(selectedEntryId))
          .singleElement()
          .satisfies(
              entry -> {
                assertThat(entry.context()).isNull();
                assertThat(entry.reason()).isEqualTo("NOT_SELECTED_FOR_SAMPLE");
              });
    }

    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("unknown"))) {
      String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
      String source =
          new String(
              fixture
                  .sourceReader()
                  .reopen(fixture.sourceInventory())
                  .documents()
                  .get(0)
                  .rawUtf8()
                  .copyToByteArray(),
              StandardCharsets.UTF_8);
      CountingSession unknownSession = new CountingSession(snapshotId, source);
      String unknownEntryId = "entry:" + "f".repeat(64);

      assertThatThrownBy(
              () ->
                  new TechnicalAnalysisWorkflow(
                          fixture.sourceReader(),
                          fixture.moduleArtifacts(),
                          fixture.stepArtifacts())
                      .continueAfterDiscovery(
                          new TechnicalDiscoveryWorkflowResult(
                              fixture.sourceInventory(), fixture.applicationDiscovery()),
                          unknownSession,
                          reference("graph-profile", 'a', 'b'),
                          fixture.artifactControls(),
                          new FlowCompilationProfile(
                              reference("flow-profile", 'c', 'd'), 16, 8, 64, 96, 32, 64, 256),
                          new CapsuleProjectionProfile(
                              reference("capsule-profile", 'e', 'f'), 16, 32, 4_096, 100_000),
                          List.of(unknownEntryId)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(unknownSession.collectedEntryIds).isEmpty();
    }
  }

  private static ArtifactReference reference(String prefix, char first, char second) {
    String digest = ("" + first + second).repeat(32);
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest), Sha256Digest.parse(digest));
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
    private final String source;
    private final List<String> collectedEntryIds = new ArrayList<>();

    private CountingSession(String snapshotId, String source) {
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
      collectedEntryIds.add(entry.entryId());
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
          "selected-runtime-test-adapter-v1",
          Map.of("jdtls", "1.61.0"),
          "17",
          List.of("METHODS"));
    }

    @Override
    public void close() {}
  }
}
