package org.sourceanalysis.app.analysis.persistence.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex.SqlAstNode;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex.SqlStatus;
import org.sourceanalysis.app.artifact.AnalysisStepKey;

/** RED contract for the canonical Step 04 persistence-material publication and reader. */
class PersistenceMaterialPublicationTest {

  private static final String XML_PATH = "src/main/resources/mapper/OrderMapper.xml";
  private static final String MAPPER_NAMESPACE = "com.example.OrderMapper";

  @Test
  void publishesAndReopensDisabledHeaderWithoutReanalyzingInputs(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        createJavaIndexFixture(temporary.resolve("fixture"))) {
      ProgramGraphsReference navigation = navigation(fixture);
      String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
      PersistenceMaterialIndex disabled =
          new PersistenceMaterialIndex(
              new PersistenceMaterialIndex.Header(
                  PersistenceMaterialIndex.Status.DISABLED, snapshotId, navigation, List.of()),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of());

      PersistenceMaterialPublisher publisher =
          new PersistenceMaterialPublisher(fixture.moduleArtifacts(), fixture.stepArtifacts());
      var publication =
          assertDoesNotThrow(
              () ->
                  publisher.publish(
                      fixture.sourceInventory(),
                      fixture.applicationDiscovery(),
                      fixture.artifactControls(),
                      disabled));

      assertThat(publication.address().analysisStepKey())
          .isEqualTo(AnalysisStepKey.PROVEN_CODE_FACTS);
      assertThat(publication.address().runId())
          .isEqualTo(fixture.sourceInventory().publication().address().runId());
      PersistenceMaterialIndex reopened =
          assertDoesNotThrow(
              () -> new PersistenceMaterialReader(fixture.stepArtifacts()).reopen(publication));
      assertThat(reopened).isEqualTo(disabled);
      assertThat(reopened.header().status()).isEqualTo(PersistenceMaterialIndex.Status.DISABLED);
      assertThat(reopened.resources()).isEmpty();
      assertThat(reopened.statements()).isEmpty();
      assertThat(reopened.bindings()).isEmpty();
      assertThat(reopened.sqlAnalyses()).isEmpty();
      assertThat(reopened.diagnostics()).isEmpty();
    }
  }

  @Test
  void publishesAndReopensEnabledRawXmlAndSqlAstWithoutInvokingAnalyzers(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture =
        createJavaIndexFixture(temporary.resolve("fixture"))) {
      ProgramGraphsReference navigation = navigation(fixture);
      VerifiedSourceTextSet source = fixture.sourceReader().reopen(fixture.sourceInventory());
      String rawXml =
          new String(
              sourceDocument(source, XML_PATH).rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      PersistenceMaterialIndex.SqlAstNode updateAst =
          new SqlAstNode(
              "UPDATE",
              null,
              Map.of(),
              List.of(
                  new SqlAstNode("TABLE", "orders", Map.of(), List.of()),
                  new SqlAstNode("SET", "status = ?", Map.of(), List.of()),
                  new SqlAstNode("WHERE", "id = ?", Map.of(), List.of())));
      PersistenceMaterialIndex.XmlNode xmlSubtree =
          new PersistenceMaterialIndex.XmlNode(
              PersistenceMaterialIndex.XmlNodeKind.ELEMENT,
              "update",
              Map.of("id", "noop"),
              null,
              List.of(
                  new PersistenceMaterialIndex.XmlNode(
                      PersistenceMaterialIndex.XmlNodeKind.TEXT,
                      null,
                      Map.of(),
                      "UPDATE orders SET status = #{status}",
                      List.of())));
      PersistenceMaterialIndex.Resource resource =
          new PersistenceMaterialIndex.Resource(
              XML_PATH, MAPPER_NAMESPACE, rawXml, List.of(XML_PATH));
      PersistenceMaterialIndex.Statement statement =
          new PersistenceMaterialIndex.Statement(
              "statement:com.example.OrderMapper:noop",
              XML_PATH,
              MAPPER_NAMESPACE,
              "noop",
              "update",
              null,
              xmlSubtree,
              List.of());
      PersistenceMaterialIndex.SqlAnalysis sql =
          new PersistenceMaterialIndex.SqlAnalysis(
              statement.statementRef(),
              "UPDATE orders SET status = ? WHERE id = ?",
              List.of("placeholder:status->?", "placeholder:id->?"),
              updateAst,
              SqlStatus.PARSED,
              null);
      PersistenceMaterialIndex.ParameterBinding parameter =
          new PersistenceMaterialIndex.ParameterBinding(
              0, "status", "String", List.of("@Param(\"status\")"), List.of("status"), List.of());
      PersistenceMaterialIndex.JavaBinding binding =
          new PersistenceMaterialIndex.JavaBinding(
              MAPPER_NAMESPACE,
              "method:com.example.OrderMapper:noop",
              "void noop(java.lang.String)",
              "EXACT",
              List.of(parameter),
              List.of(new PersistenceMaterialIndex.StatementRef(statement.statementRef(), null)),
              List.of());
      PersistenceMaterialIndex.Diagnostic diagnostic =
          new PersistenceMaterialIndex.Diagnostic(
              "XML_SOURCE_RETAINED", XML_PATH, "raw mapper XML retained for later reading");
      PersistenceMaterialIndex enabled =
          new PersistenceMaterialIndex(
              new PersistenceMaterialIndex.Header(
                  PersistenceMaterialIndex.Status.ENABLED,
                  source.snapshotId(),
                  navigation,
                  List.of(
                      new PersistenceMaterialIndex.Tool("mybatis", "3.5.19"),
                      new PersistenceMaterialIndex.Tool("jsqlparser", "5.3"))),
              List.of(resource),
              List.of(statement),
              List.of(binding),
              List.of(sql),
              List.of(diagnostic));

      PersistenceMaterialPublisher publisher =
          new PersistenceMaterialPublisher(fixture.moduleArtifacts(), fixture.stepArtifacts());
      var publication =
          assertDoesNotThrow(
              () ->
                  publisher.publish(
                      fixture.sourceInventory(),
                      fixture.applicationDiscovery(),
                      fixture.artifactControls(),
                      enabled));
      PersistenceMaterialIndex reopened =
          assertDoesNotThrow(
              () -> new PersistenceMaterialReader(fixture.stepArtifacts()).reopen(publication));

      assertThat(reopened).isEqualTo(enabled);
      assertThat(reopened.resources())
          .singleElement()
          .satisfies(value -> assertThat(value.rawSource()).isEqualTo(rawXml));
      assertThat(reopened.statements())
          .singleElement()
          .satisfies(value -> assertThat(value.xmlSubtree()).isEqualTo(xmlSubtree));
      assertThat(reopened.resources())
          .singleElement()
          .satisfies(
              value -> assertThat(value.dependencyResourcePaths()).containsExactly(XML_PATH));
      assertThat(reopened.bindings())
          .singleElement()
          .satisfies(
              value -> {
                assertThat(value.methodKey()).isEqualTo("method:com.example.OrderMapper:noop");
                assertThat(value.parameters())
                    .singleElement()
                    .satisfies(
                        parameterValue -> {
                          assertThat(parameterValue.name()).isEqualTo("status");
                          assertThat(parameterValue.annotationTexts())
                              .containsExactly("@Param(\"status\")");
                          assertThat(parameterValue.placeholderPaths()).containsExactly("status");
                        });
              });
      assertThat(reopened.diagnostics())
          .singleElement()
          .satisfies(
              value -> {
                assertThat(value.code()).isEqualTo("XML_SOURCE_RETAINED");
                assertThat(value.subjectRef()).isEqualTo(XML_PATH);
                assertThat(value.detail()).contains("raw mapper XML");
              });
      assertThat(reopened.sqlAnalyses())
          .singleElement()
          .satisfies(
              value -> {
                assertThat(value.status()).isEqualTo(SqlStatus.PARSED);
                assertThat(value.ast()).isEqualTo(updateAst);
                assertThat(value.transformations())
                    .containsExactly("placeholder:status->?", "placeholder:id->?");
              });
    }
  }

  private static ProgramGraphsPublicFixture createJavaIndexFixture(Path root) {
    return ProgramGraphsPublicFixture.createForJavaCodeIndex(root);
  }

  private static ProgramGraphsReference navigation(ProgramGraphsPublicFixture fixture) {
    VerifiedSourceTextSet source = fixture.sourceReader().reopen(fixture.sourceInventory());
    String snapshotId = source.snapshotId();
    try (JavaCodeSession session = minimalJavaSession(snapshotId)) {
      return new ProgramGraphsExecution(
              fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
          .execute(
              fixture.sourceInventory(),
              fixture.applicationDiscovery(),
              session,
              fixture.artifactControls());
    }
  }

  private static JavaCodeSession minimalJavaSession(String snapshotId) {
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "PUBLICATION_TEST_NAVIGATION_ONLY",
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
        EntryCodeContext.MethodCode method =
            new EntryCodeContext.MethodCode(
                entry.methodKey(),
                "METHOD",
                "com.example.OrderController",
                "entry",
                List.of(),
                "void",
                new EntryCodeContext.SourceSource(
                    "src/main/java/com/example/OrderController.java",
                    entry.methodRange(),
                    "void entry() {}"),
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
            "jdt", "publication-test", Map.of("publication-test", "1"), "17", List.of());
      }

      @Override
      public void close() {}
    };
  }

  private static VerifiedSourceTextDocument sourceDocument(
      VerifiedSourceTextSet source, String path) {
    return source.documents().stream()
        .filter(document -> document.path().equals(path))
        .findFirst()
        .orElseThrow(() -> new AssertionError("fixture source path missing: " + path));
  }
}
