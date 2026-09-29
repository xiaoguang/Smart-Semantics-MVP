package org.sourceanalysis.app.analysis.material.publish;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialPublisher;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** Reader-only regression for a saved historical Packet publication. */
class CodeReadingMaterialPublicationTest {

  private static final String CONTROLLER_PATH = "src/main/java/com/example/OrderController.java";

  @Test
  void reopensSavedV1MaterialWithoutInvokingAnyPacketProducer(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporary.resolve("fixture"))) {
      ProgramGraphsReference navigation = navigation(fixture);
      JavaCodeIndex index = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(navigation);
      AnalysisStepPublicationReference persistence =
          new PersistenceMaterialPublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.artifactControls(),
                  disabledPersistence(index, navigation));
      CodeReadingMaterialSet expected =
          new CodeReadingMaterialSet(
              new CodeReadingMaterialSet.Header(
                  fixture.sourceInventory(),
                  navigation,
                  persistence,
                  index.snapshotId(),
                  new CodeReadingMaterialProfile(64_000L, 16)),
              List.of(),
              index.entries().stream()
                  .map(
                      entry ->
                          new CodeReadingMaterialSet.EntryCoverage(
                              entry.seed().entryId(),
                              List.of(),
                              CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED,
                              List.of("HISTORICAL_FIXTURE_NOT_COLLECTED")))
                  .toList());

      AnalysisStepPublicationReference publication =
          HistoricalCodeReadingMaterialFixture.installV1(
              fixture.moduleArtifacts(),
              fixture.stepArtifacts(),
              fixture.applicationDiscovery(),
              fixture.artifactControls(),
              expected);

      assertThat(new CodeReadingMaterialReader(fixture.stepArtifacts()).reopen(publication))
          .isEqualTo(expected);
      assertThat(fixture.stepArtifacts().reopen(publication).semanticPayloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName())
                    .isEqualTo(CodeReadingMaterialPublisher.FILE_NAME);
                assertThat(payload.descriptor().artifactType())
                    .isEqualTo(CodeReadingMaterialPublisher.ARTIFACT_TYPE);
                assertThat(payload.descriptor().schemaVersion())
                    .isEqualTo(CodeReadingMaterialPublisher.SCHEMA_VERSION);
              });
    }
  }

  private static PersistenceMaterialIndex disabledPersistence(
      JavaCodeIndex index, ProgramGraphsReference navigation) {
    return new PersistenceMaterialIndex(
        new PersistenceMaterialIndex.Header(
            PersistenceMaterialIndex.Status.DISABLED, index.snapshotId(), navigation, List.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static ProgramGraphsReference navigation(ProgramGraphsPublicFixture fixture) {
    String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
    try (JavaCodeSession session = minimalSession(snapshotId)) {
      return new ProgramGraphsExecution(
              fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
          .execute(
              fixture.sourceInventory(),
              fixture.applicationDiscovery(),
              session,
              fixture.artifactControls());
    }
  }

  private static JavaCodeSession minimalSession(String snapshotId) {
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return new JavaDeclarationCatalog(
            snapshotId,
            List.of(CONTROLLER_PATH),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of());
      }

      @Override
      public EntryCodeContext collect(EntrySeed entry) {
        String source = "void entry() {}";
        EntryCodeContext.MethodCode method =
            new EntryCodeContext.MethodCode(
                entry.methodKey(),
                "METHOD",
                "com.example.OrderController",
                "entry",
                List.of(),
                "void",
                new EntryCodeContext.SourceSource(
                    CONTROLLER_PATH, new SourceRange(0, source.length(), 1, 1), source),
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
                "HISTORICAL_PACKET_FIXTURE",
                List.of(),
                List.of(),
                null));
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "historical-material-fixture", Map.of("test", "1"), "17", List.of());
      }

      @Override
      public void close() {}
    };
  }
}
