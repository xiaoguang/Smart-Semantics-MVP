package org.sourceanalysis.app.analysis.material.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialMarkdown;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex.SqlStatus;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialPublisher;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.ArtifactView;
import org.sourceanalysis.app.runtime.BusinessCheckpointArtifactReader;
import org.sourceanalysis.app.runtime.BusinessOutputArtifactKey;
import org.sourceanalysis.app.runtime.CompletedBusinessArtifactReader;

/** RED contract for the reference-only canonical Step 05 material publication and reader. */
class CodeReadingMaterialPublicationTest {

  private static final String XML_PATH = "src/main/resources/mapper/OrderMapper.xml";
  private static final String CONTROLLER_PATH = "src/main/java/com/example/OrderController.java";
  private static final String SERVICE_KEY = "method:publication-service";

  @Test
  void publishesAndReopensDisabledJavaMaterialUsingOnlyPersistedReferences(
      @TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture = realFixture(temporary.resolve("fixture"))) {
      ProgramGraphsReference navigation = navigation(fixture);
      JavaCodeIndex javaIndex = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(navigation);
      PersistenceMaterialIndex disabledIndex = disabledPersistenceIndex(javaIndex, navigation);
      AnalysisStepPublicationReference persistencePublication =
          persistencePublication(fixture, disabledIndex);
      CodeReadingMaterialSet set =
          materialSet(javaIndex, fixture, navigation, persistencePublication, disabledIndex, false);

      AnalysisStepPublicationReference publication =
          assertDoesNotThrow(() -> publish(fixture, set));
      String payload = materialPayload(fixture, publication);
      assertThat(payload)
          .contains("methodKeys", javaIndex.entries().get(0).context().methods().get(0).methodKey())
          .contains("callKeys", javaIndex.entries().get(0).context().calls().get(0).callKey())
          .doesNotContain(javaIndex.entries().get(0).context().methods().get(0).source().text())
          .doesNotContain(XML_PATH)
          .doesNotContain("\"rawSource\"", "\"sourceText\"", "\"body\"");

      CodeReadingMaterialSet reopened =
          assertDoesNotThrow(
              () -> new CodeReadingMaterialReader(fixture.stepArtifacts()).reopen(publication));
      assertThat(reopened).isEqualTo(set);
      assertThat(reopened.packets())
          .singleElement()
          .satisfies(
              packet -> {
                assertThat(packet.persistence().resources()).isEmpty();
                assertThat(packet.persistence().statements()).isEmpty();
                assertThat(packet.persistence().bindings()).isEmpty();
                assertThat(packet.persistence().sqlAnalyses()).isEmpty();
                assertThat(packet.persistence().diagnostics()).isEmpty();
              });
    }
  }

  @Test
  void publishesAndReopensEnabledMaterialWithoutDuplicatingJavaOrXmlBodies(
      @TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture = realFixture(temporary.resolve("fixture"))) {
      ProgramGraphsReference navigation = navigation(fixture);
      JavaCodeIndex javaIndex = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(navigation);
      PersistenceMaterialIndex enabledIndex = enabledPersistenceIndex(fixture, navigation);
      AnalysisStepPublicationReference persistencePublication =
          persistencePublication(fixture, enabledIndex);
      CodeReadingMaterialSet set =
          materialSet(javaIndex, fixture, navigation, persistencePublication, enabledIndex, true);

      AnalysisStepPublicationReference publication =
          assertDoesNotThrow(() -> publish(fixture, set));
      String payload = materialPayload(fixture, publication);
      String methodBody = javaIndex.entries().get(0).context().methods().get(0).source().text();
      String xmlBody = enabledIndex.resources().get(0).rawSource();
      assertThat(payload)
          .contains("methodKeys", javaIndex.entries().get(0).context().methods().get(0).methodKey())
          .contains("callKeys", javaIndex.entries().get(0).context().calls().get(0).callKey())
          .contains(XML_PATH, enabledIndex.statements().get(0).statementRef())
          .doesNotContain(
              methodBody,
              xmlBody,
              jsonString(methodBody),
              jsonString(xmlBody),
              "\"rawSource\"",
              "\"sourceText\"",
              "\"body\"");

      CodeReadingMaterialSet reopened =
          assertDoesNotThrow(
              () -> new CodeReadingMaterialReader(fixture.stepArtifacts()).reopen(publication));
      assertThat(reopened).isEqualTo(set);
      assertThat(reopened.packets())
          .singleElement()
          .satisfies(
              packet -> {
                assertThat(packet.methods())
                    .extracting(EntryCodeContext.MethodCode::source)
                    .allSatisfy(source -> assertThat(source.text()).isNotBlank());
                assertThat(packet.persistence().resources())
                    .singleElement()
                    .satisfies(resource -> assertThat(resource.rawSource()).isEqualTo(xmlBody));
                assertThat(packet.persistence().sqlAnalyses())
                    .singleElement()
                    .satisfies(sql -> assertThat(sql.status()).isEqualTo(SqlStatus.PARSED));
              });
    }
  }

  @Test
  void readsCanonicalStepFiveArtifactByFollowingReceiptToFinalModule(@TempDir Path temporary)
      throws Exception {
    try (ProgramGraphsPublicFixture fixture = realFixture(temporary.resolve("fixture"))) {
      ProgramGraphsReference navigation = navigation(fixture);
      JavaCodeIndex javaIndex = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(navigation);
      PersistenceMaterialIndex disabledIndex = disabledPersistenceIndex(javaIndex, navigation);
      AnalysisStepPublicationReference persistencePublication =
          persistencePublication(fixture, disabledIndex);
      CodeReadingMaterialSet set =
          materialSet(javaIndex, fixture, navigation, persistencePublication, disabledIndex, false);
      AnalysisStepPublicationReference materialPublication = publish(fixture, set);
      BusinessOutputArtifactKey key = requiredBusinessKey("CODE_READING_MATERIALS");
      AnalysisRunOutput output =
          AnalysisRunOutput.readingMaterials(
              materialPublication.address().runId(), materialPublication);

      AtomicReference<AnalysisStepPublicationReference> reopenedStep = new AtomicReference<>();
      CanonicalAnalysisStepArtifactStore steps =
          (CanonicalAnalysisStepArtifactStore)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {CanonicalAnalysisStepArtifactStore.class},
                  (proxy, method, arguments) -> {
                    if ("reopen".equals(method.getName())) {
                      AnalysisStepPublicationReference reference =
                          (AnalysisStepPublicationReference) arguments[0];
                      reopenedStep.set(reference);
                      ReopenedAnalysisStepPublication saved =
                          fixture.stepArtifacts().reopen(reference);
                      // The reader must follow the receipt's final module, not this payload list.
                      return new ReopenedAnalysisStepPublication(
                          saved.reference(), saved.receipt(), List.of(), null);
                    }
                    throw new AssertionError("reader unexpectedly invoked " + method.getName());
                  });
      AtomicReference<ModulePublicationReference> reopenedModule = new AtomicReference<>();
      CanonicalModuleArtifactStore modules =
          (CanonicalModuleArtifactStore)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {CanonicalModuleArtifactStore.class},
                  (proxy, method, arguments) -> {
                    if ("reopen".equals(method.getName())) {
                      ModulePublicationReference reference =
                          (ModulePublicationReference) arguments[0];
                      reopenedModule.set(reference);
                      return fixture.moduleArtifacts().reopen(reference);
                    }
                    throw new AssertionError("reader unexpectedly invoked " + method.getName());
                  });

      CompletedBusinessArtifactReader reader = stepsAwareArtifactReader(modules, steps);
      ArtifactView view =
          assertDoesNotThrow(
              () -> reader.read(materialPublication.address().runId(), output, key, 2_000_000));

      ReopenedAnalysisStepPublication saved = fixture.stepArtifacts().reopen(materialPublication);
      ModulePublicationReference finalModule =
          ((org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance)
                  saved.receipt().publicationProvenance())
              .publisherSpecificationModuleReference();
      var payload =
          fixture.moduleArtifacts().reopen(finalModule).payloads().stream()
              .filter(value -> value.descriptor().fileName().equals("code-reading-materials.jsonl"))
              .findFirst()
              .orElseThrow();

      assertThat(reopenedStep).hasValue(materialPublication);
      assertThat(reopenedModule).hasValue(finalModule);
      assertThat(view.businessOutputArtifactKey()).isSameAs(key);
      assertThat(view.schemaVersion()).isEqualTo(CodeReadingMaterialPublisher.SCHEMA_VERSION);
      assertThat(view.mediaType()).isEqualTo("application/x-ndjson");
      assertThat(view.immutableReference())
          .isEqualTo(
              new ArtifactReference(
                  payload.descriptor().artifactId(), payload.descriptor().sha256()));
      assertThat(view.contentUtf8())
          .contains(
              "code-reading-material-set-v1",
              "\"recordType\":\"HEADER\"",
              "\"recordType\":\"PACKET\"")
          .doesNotContain("business-materials.jsonl");
    }
  }

  @Test
  void rejectsMissingPersistenceUpstreamInsteadOfRebuildingMaterial(@TempDir Path temporary) {
    try (ProgramGraphsPublicFixture fixture = realFixture(temporary.resolve("fixture"))) {
      ProgramGraphsReference navigation = navigation(fixture);
      JavaCodeIndex javaIndex = new JavaCodeIndexReader(fixture.stepArtifacts()).reopen(navigation);
      PersistenceMaterialIndex disabledIndex = disabledPersistenceIndex(javaIndex, navigation);
      CodeReadingMaterialSet set =
          materialSet(
              javaIndex,
              fixture,
              navigation,
              publication(AnalysisStepKey.PROVEN_CODE_FACTS, 'e'),
              disabledIndex,
              false);

      assertThatThrownBy(() -> publish(fixture, set))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("CODE_READING_MATERIAL_PUBLICATION_INVALID");
    }
  }

  private static ProgramGraphsPublicFixture realFixture(Path root) {
    return ProgramGraphsPublicFixture.createForJavaCodeIndex(root);
  }

  private static ProgramGraphsReference navigation(ProgramGraphsPublicFixture fixture) {
    String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
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
            "MATERIAL_PUBLICATION_TEST_NAVIGATION_ONLY",
            List.of(),
            List.of(),
            null);
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
        String rootSource = "void entry() { service.handle(status); }";
        String serviceSource = "String handle(String status) { return status; }";
        EntryCodeContext.MethodCode root =
            method(entry.methodKey(), "com.example.OrderController", "entry", rootSource);
        EntryCodeContext.MethodCode service =
            method(
                SERVICE_KEY,
                "com.example.OrderService",
                "handle",
                serviceSource,
                List.of(
                    new JavaDeclarationCatalog.ParameterView(
                        0, "status", "String", false, List.of())));
        EntryCodeContext.CallSite call =
            new EntryCodeContext.CallSite(
                "call:" + entry.entryId(),
                root.methodKey(),
                "METHOD",
                new SourceRange(0, 22, 1, 1),
                new SourceRange(0, 22, 1, 1),
                "service.handle(status)",
                "service",
                List.of(new EntryCodeContext.ActualArgument(0, "status")),
                List.of(),
                false,
                List.of(
                    new EntryCodeContext.CallTarget(
                        SERVICE_KEY,
                        List.of("DECLARATION"),
                        SERVICE_KEY,
                        List.of("ENGINE_BINDING"),
                        "DECLARATION_ONLY",
                        "service body is retained as a separate method unit",
                        List.of(
                            new EntryCodeContext.ArgumentAssociation(
                                List.of(0), 0, "POSITIONAL")))),
                "LOCATED",
                null);
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            entry.methodKey(),
            List.of(root, service),
            List.of(call),
            List.of(),
            List.of(),
            enhancements);
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "material-publication-test", Map.of("test", "1"), "17", List.of());
      }

      @Override
      public void close() {}
    };
  }

  private static EntryCodeContext.MethodCode method(
      String key, String declaringType, String name, String source) {
    return method(key, declaringType, name, source, List.of());
  }

  private static EntryCodeContext.MethodCode method(
      String key,
      String declaringType,
      String name,
      String source,
      List<JavaDeclarationCatalog.ParameterView> parameters) {
    return new EntryCodeContext.MethodCode(
        key,
        "METHOD",
        declaringType,
        name,
        parameters,
        "String",
        new EntryCodeContext.SourceSource(
            CONTROLLER_PATH, new SourceRange(0, source.length(), 1, 1), source),
        true);
  }

  private static PersistenceMaterialIndex disabledPersistenceIndex(
      JavaCodeIndex javaIndex, ProgramGraphsReference navigation) {
    return new PersistenceMaterialIndex(
        new PersistenceMaterialIndex.Header(
            PersistenceMaterialIndex.Status.DISABLED,
            javaIndex.snapshotId(),
            navigation,
            List.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static PersistenceMaterialIndex enabledPersistenceIndex(
      ProgramGraphsPublicFixture fixture, ProgramGraphsReference navigation) {
    VerifiedSourceTextSet source = fixture.sourceReader().reopen(fixture.sourceInventory());
    String rawXml =
        new String(
            sourceDocument(source, XML_PATH).rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    String statementRef = "statement:" + XML_PATH + "#noop";
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
            XML_PATH, "com.example.OrderMapper", rawXml, List.of(XML_PATH));
    PersistenceMaterialIndex.Statement statement =
        new PersistenceMaterialIndex.Statement(
            statementRef,
            XML_PATH,
            "com.example.OrderMapper",
            "noop",
            "update",
            null,
            xmlSubtree,
            List.of());
    PersistenceMaterialIndex.JavaBinding binding =
        new PersistenceMaterialIndex.JavaBinding(
            "com.example.OrderMapper",
            "method:publication-mapper-noop",
            "void noop(java.lang.String)",
            "EXACT",
            List.of(
                new PersistenceMaterialIndex.ParameterBinding(
                    0,
                    "status",
                    "String",
                    List.of("@Param(\"status\")"),
                    List.of("status"),
                    List.of())),
            List.of(new PersistenceMaterialIndex.StatementRef(statementRef, null)),
            List.of());
    PersistenceMaterialIndex.SqlAnalysis sql =
        new PersistenceMaterialIndex.SqlAnalysis(
            statementRef,
            "UPDATE orders SET status = ?",
            List.of("placeholder:status->?"),
            new PersistenceMaterialIndex.SqlAstNode(
                "UPDATE",
                null,
                Map.of(),
                List.of(
                    new PersistenceMaterialIndex.SqlAstNode(
                        "TABLE", "orders", Map.of(), List.of()))),
            SqlStatus.PARSED,
            null);
    PersistenceMaterialIndex.Diagnostic diagnostic =
        new PersistenceMaterialIndex.Diagnostic(
            "XML_SOURCE_RETAINED", XML_PATH, "raw mapper XML retained for reading");
    return new PersistenceMaterialIndex(
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
  }

  private static AnalysisStepPublicationReference persistencePublication(
      ProgramGraphsPublicFixture fixture, PersistenceMaterialIndex index) {
    return assertDoesNotThrow(
        () ->
            new PersistenceMaterialPublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
                .publish(
                    fixture.sourceInventory(),
                    fixture.applicationDiscovery(),
                    fixture.artifactControls(),
                    index));
  }

  private static CodeReadingMaterialSet materialSet(
      JavaCodeIndex javaIndex,
      ProgramGraphsPublicFixture fixture,
      ProgramGraphsReference navigation,
      AnalysisStepPublicationReference persistencePublication,
      PersistenceMaterialIndex persistenceIndex,
      boolean includePersistence) {
    JavaCodeIndex.EntryCollection entry = javaIndex.entries().get(0);
    EntryCodeContext context = entry.context();
    CodeReadingMaterialSet.PersistenceSelection persistence =
        includePersistence
            ? new CodeReadingMaterialSet.PersistenceSelection(
                persistenceIndex.resources(),
                persistenceIndex.statements(),
                persistenceIndex.bindings(),
                persistenceIndex.sqlAnalyses(),
                persistenceIndex.diagnostics())
            : new CodeReadingMaterialSet.PersistenceSelection(
                List.of(), List.of(), List.of(), List.of(), List.of());
    List<CodeReadingMaterialSet.SourceReference> sourceReferences =
        List.of(
            new CodeReadingMaterialSet.SourceReference(
                "S1",
                new CodeReadingMaterialSet.UnitLocation(
                    "METHOD", context.methods().get(0).methodKey(), CONTROLLER_PATH, 1, 1)),
            new CodeReadingMaterialSet.SourceReference(
                "S2",
                new CodeReadingMaterialSet.UnitLocation(
                    "METHOD", SERVICE_KEY, CONTROLLER_PATH, 1, 1)));
    if (includePersistence) {
      sourceReferences =
          List.of(
              sourceReferences.get(0),
              sourceReferences.get(1),
              new CodeReadingMaterialSet.SourceReference(
                  "S3",
                  new CodeReadingMaterialSet.UnitLocation("RESOURCE", XML_PATH, XML_PATH, 1, 1)));
    }
    CodeReadingMaterialSet.Packet packet =
        new CodeReadingMaterialSet.Packet(
            "packet:material-publication",
            List.of(entry.seed()),
            context.methods(),
            context.calls().stream()
                .map(call -> new CodeReadingMaterialSet.EntryCall(entry.seed().entryId(), call))
                .toList(),
            persistence,
            sourceReferences,
            List.of(),
            List.of(),
            0L);
    long bytes =
        CodeReadingMaterialMarkdown.renderPacket(packet).getBytes(StandardCharsets.UTF_8).length;
    packet =
        new CodeReadingMaterialSet.Packet(
            packet.packetId(),
            packet.entries(),
            packet.methods(),
            packet.calls(),
            packet.persistence(),
            packet.sourceReferences(),
            packet.unselectedUnits(),
            packet.limitations(),
            bytes);
    List<CodeReadingMaterialSet.EntryCoverage> coverage = new ArrayList<>();
    coverage.add(
        new CodeReadingMaterialSet.EntryCoverage(
            entry.seed().entryId(),
            List.of(packet.packetId()),
            CodeReadingMaterialSet.CoverageStatus.COLLECTED,
            List.of()));
    javaIndex.entries().stream()
        .skip(1)
        .forEach(
            unselected ->
                coverage.add(
                    new CodeReadingMaterialSet.EntryCoverage(
                        unselected.seed().entryId(),
                        List.of(),
                        CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED,
                        List.of("NOT_SELECTED_FOR_PUBLICATION_FIXTURE"))));
    return new CodeReadingMaterialSet(
        new CodeReadingMaterialSet.Header(
            fixture.sourceInventory(),
            navigation,
            persistencePublication,
            javaIndex.snapshotId(),
            new CodeReadingMaterialProfile(64_000L, 16)),
        List.of(packet),
        coverage);
  }

  private static AnalysisStepPublicationReference publish(
      ProgramGraphsPublicFixture fixture, CodeReadingMaterialSet set) {
    return new CodeReadingMaterialPublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
        .publish(fixture.applicationDiscovery(), fixture.artifactControls(), set);
  }

  private static String materialPayload(
      ProgramGraphsPublicFixture fixture, AnalysisStepPublicationReference publication) {
    var step = fixture.stepArtifacts().reopen(publication);
    assertThat(step.semanticPayloads())
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
    return new String(
        step.semanticPayloads().get(0).canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
  }

  private static CompletedBusinessArtifactReader stepsAwareArtifactReader(
      CanonicalModuleArtifactStore modules, CanonicalAnalysisStepArtifactStore steps)
      throws Exception {
    Constructor<?> constructor =
        Arrays.stream(BusinessCheckpointArtifactReader.class.getConstructors())
            .filter(
                candidate ->
                    Arrays.equals(
                        candidate.getParameterTypes(),
                        new Class<?>[] {
                          CanonicalModuleArtifactStore.class,
                          CanonicalAnalysisStepArtifactStore.class
                        }))
            .findFirst()
            .orElse(null);
    assertThat(constructor)
        .as("BusinessCheckpointArtifactReader must have a steps-aware constructor")
        .isNotNull();
    if (constructor == null) {
      throw new AssertionError("unreachable");
    }
    return (CompletedBusinessArtifactReader) constructor.newInstance(modules, steps);
  }

  private static BusinessOutputArtifactKey requiredBusinessKey(String name) {
    try {
      return Enum.valueOf(BusinessOutputArtifactKey.class, name);
    } catch (IllegalArgumentException missing) {
      throw new AssertionError("public business-artifact policy is missing " + name, missing);
    }
  }

  private static String jsonString(String value) {
    return new ObjectMapper().valueToTree(value).toString();
  }

  private static VerifiedSourceTextDocument sourceDocument(
      VerifiedSourceTextSet source, String path) {
    return source.documents().stream()
        .filter(document -> document.path().equals(path))
        .findFirst()
        .orElseThrow(() -> new AssertionError("fixture source path missing: " + path));
  }

  private static AnalysisStepPublicationReference publication(AnalysisStepKey key, char fill) {
    AnalysisRunId run = new AnalysisRunId("analysis-run:" + String.valueOf(fill).repeat(64));
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(run, key),
        new AnalysisStepArtifactRoot("analysis-step-root:" + String.valueOf(fill).repeat(64)),
        new AnalysisStepReceiptId("analysis-step-receipt:" + String.valueOf(fill).repeat(64)),
        new Sha256Digest(String.valueOf(fill).repeat(64)));
  }
}
