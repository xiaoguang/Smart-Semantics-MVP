package org.sourceanalysis.app.analysis.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** RED contract for organizing already-read Java and persistence material into bounded packets. */
class CodeReadingMaterialBuilderTest {

  private static final String SNAPSHOT = "snapshot:" + "1".repeat(64);
  private static final String ENTRY_ID = "entry:neutral-register";
  private static final String CONTROLLER_KEY = "method:neutral-controller-register";
  private static final String SERVICE_KEY = "method:neutral-service-register";
  private static final String ALTERNATE_SERVICE_KEY = "method:neutral-service-register-alt";
  private static final String MAPPER_KEY = "method:neutral-mapper-find";
  private static final String CONTROLLER_FQN = "example.neutral.NeutralController";
  private static final String SERVICE_FQN = "example.neutral.NeutralService";
  private static final String ALTERNATE_SERVICE_FQN = "example.neutral.NeutralAlternateService";
  private static final String MAPPER_FQN = "example.neutral.NeutralMapper";
  private static final String CONTROLLER_PATH =
      "src/main/java/example/neutral/NeutralController.java";
  private static final String SERVICE_PATH = "src/main/java/example/neutral/NeutralService.java";
  private static final String ALTERNATE_SERVICE_PATH =
      "src/main/java/example/neutral/NeutralAlternateService.java";
  private static final String MAPPER_PATH = "src/main/java/example/neutral/NeutralMapper.java";
  private static final String XML_PATH = "src/main/resources/mapper/NeutralMapper.xml";
  private static final String CONTROLLER_SOURCE =
      "public String register(String label) { return service.register(label); }";
  private static final String SERVICE_SOURCE =
      "public String register(String label) { return mapper.find(label); }";
  private static final String ALTERNATE_SERVICE_SOURCE =
      "public String register(String label) { return mapper.find(label + \"-alt\"); }";
  private static final String MAPPER_SOURCE = "String find(@Param(\"label\") String label);";
  private static final String XML_SOURCE =
      "<mapper namespace=\"example.neutral.NeutralMapper\">"
          + "<select id=\"find\">SELECT label FROM neutral_table WHERE label = #{label}</select>"
          + "</mapper>";
  private static final String SQL_TRANSFORMATION = "parameter:label->placeholder";

  @Test
  void assemblesCompleteControllerServiceMapperAndXmlMaterialFromPublishedIndexes() {
    Fixture fixture = fixture(enabledPersistenceIndex());

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.header().sourceInventory()).isEqualTo(fixture.sourceInventory());
    assertThat(result.header().navigationPublication()).isEqualTo(fixture.navigationPublication());
    assertThat(result.header().persistencePublication())
        .isEqualTo(fixture.persistencePublication());
    assertThat(result.header().sourceSnapshotId()).isEqualTo(SNAPSHOT);
    assertThat(result.header().profile()).isEqualTo(fixture.profile());

    assertThat(result.packets())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.entries()).extracting(EntrySeed::entryId).containsExactly(ENTRY_ID);
              assertThat(packet.methods())
                  .extracting(EntryCodeContext.MethodCode::methodKey)
                  .containsExactlyInAnyOrder(CONTROLLER_KEY, SERVICE_KEY, MAPPER_KEY);
              assertThat(packet.methods())
                  .filteredOn(method -> MAPPER_KEY.equals(method.methodKey()))
                  .singleElement()
                  .satisfies(
                      method -> {
                        assertThat(method.source().text()).isEqualTo(MAPPER_SOURCE);
                        assertThat(method.parameters())
                            .singleElement()
                            .satisfies(
                                parameter -> {
                                  assertThat(parameter.name()).isEqualTo("label");
                                  assertThat(parameter.annotationTexts())
                                      .containsExactly("@Param(\"label\")");
                                });
                      });
              assertThat(packet.methods())
                  .filteredOn(method -> CONTROLLER_KEY.equals(method.methodKey()))
                  .singleElement()
                  .satisfies(
                      method -> assertThat(method.source().text()).isEqualTo(CONTROLLER_SOURCE));
              assertThat(packet.methods())
                  .filteredOn(method -> SERVICE_KEY.equals(method.methodKey()))
                  .singleElement()
                  .satisfies(
                      method -> assertThat(method.source().text()).isEqualTo(SERVICE_SOURCE));

              assertThat(packet.calls()).hasSize(2);
              assertThat(packet.calls())
                  .extracting(CodeReadingMaterialSet.EntryCall::entryId)
                  .containsOnly(ENTRY_ID);
              assertCallCarriesActualToFormal(packet, "call:controller-service", SERVICE_KEY);
              assertCallCarriesActualToFormal(packet, "call:service-mapper", MAPPER_KEY);

              assertThat(packet.persistence().resources())
                  .singleElement()
                  .satisfies(
                      resource -> {
                        assertThat(resource.resourcePath()).isEqualTo(XML_PATH);
                        assertThat(resource.rawSource()).isEqualTo(XML_SOURCE);
                      });
              assertThat(packet.persistence().statements())
                  .singleElement()
                  .satisfies(
                      statement -> {
                        assertThat(statement.resourceRef()).isEqualTo(XML_PATH);
                        assertThat(statement.statementId()).isEqualTo("find");
                        assertThat(statement.xmlSubtree().elementName()).isEqualTo("select");
                      });
              assertThat(packet.persistence().bindings())
                  .singleElement()
                  .satisfies(
                      binding -> {
                        assertThat(binding.javaInterfaceFqn()).isEqualTo(MAPPER_FQN);
                        assertThat(binding.methodKey()).isEqualTo(MAPPER_KEY);
                        assertThat(binding.parameters())
                            .singleElement()
                            .satisfies(
                                parameter -> {
                                  assertThat(parameter.name()).isEqualTo("label");
                                  assertThat(parameter.placeholderPaths()).containsExactly("label");
                                });
                      });
              assertThat(packet.persistence().sqlAnalyses())
                  .singleElement()
                  .satisfies(
                      sql -> {
                        assertThat(sql.statementRef()).isEqualTo("statement:" + XML_PATH + "#find");
                        assertThat(sql.analysisCopy()).contains("SELECT label FROM neutral_table");
                        assertThat(sql.status())
                            .isEqualTo(PersistenceMaterialIndex.SqlStatus.PARSED);
                        assertThat(sql.ast().kind()).isEqualTo("SELECT");
                      });
              assertThat(packet.persistence().diagnostics()).isEmpty();
              assertThat(packet.sourceReferences())
                  .hasSize(4)
                  .extracting(CodeReadingMaterialSet.SourceReference::sourceRef)
                  .allSatisfy(sourceRef -> assertThat(sourceRef).isNotBlank())
                  .doesNotHaveDuplicates();
              assertThat(packet.sourceReferences())
                  .extracting(reference -> reference.location().path())
                  .containsExactlyInAnyOrder(CONTROLLER_PATH, SERVICE_PATH, MAPPER_PATH, XML_PATH);
              assertThat(packet.sourceReferences())
                  .extracting(reference -> reference.location().unitRef())
                  .allSatisfy(unitRef -> assertThat(unitRef).isNotBlank())
                  .doesNotHaveDuplicates();
              assertThat(packet.unselectedUnits()).isEmpty();
              assertThat(packet.limitations()).isEmpty();
              String markdown =
                  Assertions.assertDoesNotThrow(() -> CodeReadingMaterialMarkdown.render(result));
              String packetMarkdown =
                  Assertions.assertDoesNotThrow(
                      () -> CodeReadingMaterialMarkdown.renderPacket(packet));
              assertThat(markdown)
                  .contains(
                      CONTROLLER_SOURCE,
                      SERVICE_SOURCE,
                      MAPPER_SOURCE,
                      XML_SOURCE,
                      "service.register(label)",
                      "mapper.find(label)",
                      "call:controller-service",
                      "call:service-mapper",
                      SERVICE_KEY,
                      MAPPER_KEY,
                      "POSITIONAL",
                      CONTROLLER_PATH,
                      SERVICE_PATH,
                      MAPPER_PATH,
                      XML_PATH,
                      "PARSED",
                      SQL_TRANSFORMATION,
                      "SELECT");
              assertThat(packetMarkdown)
                  .contains(
                      CONTROLLER_SOURCE,
                      SERVICE_SOURCE,
                      MAPPER_SOURCE,
                      XML_SOURCE,
                      "service.register(label)",
                      "mapper.find(label)",
                      "call:controller-service",
                      "call:service-mapper",
                      SERVICE_KEY,
                      MAPPER_KEY,
                      "POSITIONAL",
                      CONTROLLER_PATH,
                      SERVICE_PATH,
                      MAPPER_PATH,
                      XML_PATH,
                      "PARSED",
                      SQL_TRANSFORMATION,
                      "SELECT");
              assertThat(packet.selfContainedUtf8Bytes())
                  .isEqualTo(packetMarkdown.getBytes(StandardCharsets.UTF_8).length);
            });

    assertThat(result.coverage())
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.entryId()).isEqualTo(ENTRY_ID);
              assertThat(coverage.packetIds())
                  .containsExactlyElementsOf(
                      result.packets().stream()
                          .map(CodeReadingMaterialSet.Packet::packetId)
                          .toList());
              assertThat(coverage.status())
                  .isEqualTo(CodeReadingMaterialSet.CoverageStatus.COLLECTED);
              assertThat(coverage.limitations()).isEmpty();
            });
  }

  @Test
  void disabledPersistenceStillProducesCompleteJavaMaterialWithoutPersistenceSelection() {
    Fixture fixture = fixture(disabledPersistenceIndex());

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.header().persistencePublication())
        .isEqualTo(fixture.persistencePublication());
    assertThat(result.packets())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.entries()).extracting(EntrySeed::entryId).containsExactly(ENTRY_ID);
              assertThat(packet.methods())
                  .extracting(EntryCodeContext.MethodCode::methodKey)
                  .containsExactlyInAnyOrder(CONTROLLER_KEY, SERVICE_KEY, MAPPER_KEY);
              assertThat(packet.calls()).hasSize(2);
              assertThat(packet.persistence().resources()).isEmpty();
              assertThat(packet.persistence().statements()).isEmpty();
              assertThat(packet.persistence().bindings()).isEmpty();
              assertThat(packet.persistence().sqlAnalyses()).isEmpty();
              assertThat(packet.persistence().diagnostics()).isEmpty();
              assertThat(packet.sourceReferences())
                  .hasSize(3)
                  .extracting(CodeReadingMaterialSet.SourceReference::sourceRef)
                  .allSatisfy(sourceRef -> assertThat(sourceRef).isNotBlank())
                  .doesNotHaveDuplicates();
              assertThat(packet.sourceReferences())
                  .extracting(reference -> reference.location().path())
                  .containsExactlyInAnyOrder(CONTROLLER_PATH, SERVICE_PATH, MAPPER_PATH)
                  .doesNotContain(XML_PATH);
              assertThat(packet.sourceReferences())
                  .extracting(reference -> reference.location().unitRef())
                  .allSatisfy(unitRef -> assertThat(unitRef).isNotBlank())
                  .doesNotHaveDuplicates();
              assertThat(packet.selfContainedUtf8Bytes()).isPositive();
            });
    assertThat(result.coverage())
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.entryId()).isEqualTo(ENTRY_ID);
              assertThat(coverage.status())
                  .isEqualTo(CodeReadingMaterialSet.CoverageStatus.COLLECTED);
            });
  }

  @Test
  void emptyJavaIndexPublishesHeaderOnlyWithoutInventingCoverageOrPersistenceMaterial() {
    Fixture fixture =
        fixture(
            emptyJavaCodeIndex(),
            disabledPersistenceIndex(),
            new CodeReadingMaterialProfile(64_000L, 16));

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.header().sourceInventory()).isEqualTo(fixture.sourceInventory());
    assertThat(result.header().navigationPublication()).isEqualTo(fixture.navigationPublication());
    assertThat(result.header().persistencePublication())
        .isEqualTo(fixture.persistencePublication());
    assertThat(result.header().sourceSnapshotId()).isEqualTo(SNAPSHOT);
    assertThat(result.packets()).isEmpty();
    assertThat(result.coverage()).isEmpty();
  }

  @Test
  void uncollectedJavaEntryRemainsInCoverageWithoutAPlaceholderPacket() {
    Fixture fixture =
        fixture(
            uncollectedJavaCodeIndex(),
            disabledPersistenceIndex(),
            new CodeReadingMaterialProfile(64_000L, 16));

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.packets()).isEmpty();
    assertThat(result.coverage())
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.entryId()).isEqualTo("entry:uncollected");
              assertThat(coverage.packetIds()).isEmpty();
              assertThat(coverage.status())
                  .isEqualTo(CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED);
              assertThat(coverage.limitations()).containsExactly("JDT_SOURCE_UNAVAILABLE");
            });
  }

  @Test
  void deduplicatesSharedMethodsButKeepsCallsOwnedByEachEntry() {
    Fixture fixture =
        fixture(
            sharedMethodJavaCodeIndex(),
            enabledPersistenceIndex(),
            new CodeReadingMaterialProfile(64_000L, 2));

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.packets())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.entries())
                  .extracting(EntrySeed::entryId)
                  .containsExactlyInAnyOrder("entry:neutral-first", "entry:neutral-second");
              assertThat(packet.methods())
                  .extracting(EntryCodeContext.MethodCode::methodKey)
                  .containsExactlyInAnyOrder(
                      "method:neutral-first", "method:neutral-second", SERVICE_KEY, MAPPER_KEY);
              assertThat(packet.methods())
                  .filteredOn(method -> SERVICE_KEY.equals(method.methodKey()))
                  .hasSize(1);
              assertThat(packet.methods())
                  .filteredOn(method -> MAPPER_KEY.equals(method.methodKey()))
                  .hasSize(1);
              assertThat(packet.calls()).hasSize(4);
              assertThat(packet.calls())
                  .filteredOn(call -> "entry:neutral-first".equals(call.entryId()))
                  .hasSize(2);
              assertThat(packet.calls())
                  .filteredOn(call -> "entry:neutral-second".equals(call.entryId()))
                  .hasSize(2);
              assertThat(packet.persistence().resources()).hasSize(1);
              assertThat(packet.persistence().statements()).hasSize(1);
              assertThat(packet.persistence().bindings()).hasSize(1);
              assertThat(packet.persistence().sqlAnalyses()).hasSize(1);
              assertThat(packet.sourceReferences()).hasSize(5);
            });
    assertThat(result.coverage())
        .extracting(CodeReadingMaterialSet.EntryCoverage::entryId)
        .containsExactlyInAnyOrder("entry:neutral-first", "entry:neutral-second");
  }

  @Test
  void rendersShortNumberedCallTreeForSharedRecursiveMethodsWithoutDuplicatingBodies() {
    Fixture fixture =
        fixture(
            sharedRecursiveJavaCodeIndex(),
            enabledPersistenceIndex(),
            new CodeReadingMaterialProfile(64_000L, 2));

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.packets())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.entries())
                  .extracting(EntrySeed::entryId)
                  .containsExactlyInAnyOrder("entry:neutral-first", "entry:neutral-second");
              assertThat(packet.methods())
                  .extracting(EntryCodeContext.MethodCode::methodKey)
                  .containsExactlyInAnyOrder(
                      "method:neutral-first",
                      "method:neutral-second",
                      SERVICE_KEY,
                      ALTERNATE_SERVICE_KEY,
                      MAPPER_KEY);
              assertThat(packet.methods())
                  .filteredOn(method -> SERVICE_KEY.equals(method.methodKey()))
                  .hasSize(1);
              assertThat(packet.methods())
                  .filteredOn(method -> MAPPER_KEY.equals(method.methodKey()))
                  .hasSize(1);

              assertThat(packet.calls())
                  .extracting(call -> call.call().callKey())
                  .containsExactlyInAnyOrder(
                      "call:neutral-first-service-candidates",
                      "call:neutral-second-service-candidates",
                      "call:neutral-first-mapper",
                      "call:neutral-second-mapper",
                      "call:neutral-first-recursion",
                      "call:neutral-second-recursion");
              assertThat(packet.calls())
                  .filteredOn(call -> "entry:neutral-first".equals(call.entryId()))
                  .hasSize(3);
              assertThat(packet.calls())
                  .filteredOn(call -> "entry:neutral-second".equals(call.entryId()))
                  .hasSize(3);

              assertThat(packet.calls())
                  .filteredOn(
                      call -> "call:neutral-first-service-candidates".equals(call.call().callKey()))
                  .singleElement()
                  .satisfies(
                      call -> {
                        assertThat(call.call().actualArguments())
                            .extracting(EntryCodeContext.ActualArgument::expression)
                            .containsExactly("label");
                        assertThat(call.call().targets())
                            .extracting(EntryCodeContext.CallTarget::methodKey)
                            .containsExactlyInAnyOrder(SERVICE_KEY, ALTERNATE_SERVICE_KEY);
                        assertThat(call.call().targets())
                            .allSatisfy(
                                target ->
                                    assertThat(target.argumentAssociations())
                                        .singleElement()
                                        .satisfies(
                                            association -> {
                                              assertThat(association.actualOrdinals())
                                                  .containsExactly(0);
                                              assertThat(association.formalOrdinal()).isEqualTo(0);
                                            }));
                      });
              assertThat(packet.calls())
                  .filteredOn(call -> call.call().callKey().endsWith("-recursion"))
                  .allSatisfy(
                      call ->
                          assertThat(call.call().targets())
                              .extracting(EntryCodeContext.CallTarget::methodKey)
                              .containsExactly(SERVICE_KEY));

              String rendered =
                  Assertions.assertDoesNotThrow(
                      () -> CodeReadingMaterialMarkdown.renderPacket(packet));
              assertThat(rendered)
                  .contains("Call tree")
                  .contains("C1:", "C2:", "C3:", "C4:", "C5:", "C6:")
                  .contains("(cycle reference)")
                  .containsPattern("(?s).*\\bM[0-9]+\\b.*")
                  .containsPattern("(?s).*\\bC[0-9]+\\b.*")
                  .contains(
                      "public String first(String label) { return service.register(label); }",
                      "public String second(String label) { return service.register(label); }",
                      SERVICE_SOURCE,
                      ALTERNATE_SERVICE_SOURCE,
                      MAPPER_SOURCE,
                      "call:neutral-first-service-candidates",
                      "call:neutral-second-service-candidates",
                      "call:neutral-first-recursion",
                      "call:neutral-second-recursion",
                      SERVICE_KEY,
                      ALTERNATE_SERVICE_KEY,
                      MAPPER_KEY,
                      "label",
                      "POSITIONAL");
              assertThat(occurrences(rendered, SERVICE_SOURCE)).isEqualTo(1);
              assertThat(occurrences(rendered, MAPPER_SOURCE)).isEqualTo(1);
            });
  }

  @Test
  void packetCapacityKeepsRootAndRecordsWholeUnitOmissions() {
    Fixture fixture =
        fixture(
            capacityJavaCodeIndex(),
            capacityPersistenceIndex(),
            new CodeReadingMaterialProfile(8_192L, 1));

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.packets())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.entries())
                  .singleElement()
                  .extracting(EntrySeed::entryId)
                  .isEqualTo(ENTRY_ID);
              assertThat(packet.methods())
                  .extracting(EntryCodeContext.MethodCode::methodKey)
                  .containsExactly(CONTROLLER_KEY);
              assertThat(packet.persistence().resources()).isEmpty();
              assertThat(packet.persistence().statements()).isEmpty();
              assertThat(packet.persistence().bindings()).isEmpty();
              assertThat(packet.persistence().sqlAnalyses()).isEmpty();
              assertThat(packet.unselectedUnits())
                  .extracting(CodeReadingMaterialSet.UnselectedUnit::unitRef)
                  .contains(SERVICE_KEY, XML_PATH);
              assertThat(packet.unselectedUnits())
                  .allSatisfy(
                      unit -> {
                        assertThat(unit.entryId()).isEqualTo(ENTRY_ID);
                        assertThat(unit.reason()).isNotBlank();
                      });
              assertThat(packet.selfContainedUtf8Bytes())
                  .isEqualTo(
                      CodeReadingMaterialMarkdown.renderPacket(packet)
                          .getBytes(StandardCharsets.UTF_8)
                          .length);
            });
    assertThat(result.coverage())
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.status())
                  .isEqualTo(CodeReadingMaterialSet.CoverageStatus.COLLECTED_WITH_LIMITATIONS);
              assertThat(coverage.packetIds())
                  .containsExactlyElementsOf(
                      result.packets().stream()
                          .map(CodeReadingMaterialSet.Packet::packetId)
                          .toList());
            });
  }

  @Test
  void packetCapacityKeepsAllJavaUnitsButOmitsOversizedXmlAsAWholeUnit() {
    long packetLimit = 8_192L;
    Fixture fixture =
        fixture(
            javaCodeIndex(),
            oversizedXmlPersistenceIndex(),
            new CodeReadingMaterialProfile(packetLimit, 1));

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.packets())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.entries())
                  .singleElement()
                  .extracting(EntrySeed::entryId)
                  .isEqualTo(ENTRY_ID);
              assertThat(packet.methods())
                  .extracting(EntryCodeContext.MethodCode::methodKey)
                  .containsExactlyInAnyOrder(CONTROLLER_KEY, SERVICE_KEY, MAPPER_KEY);
              assertThat(packet.calls()).hasSize(2);
              assertThat(packet.persistence().resources()).isEmpty();
              assertThat(packet.persistence().statements()).isEmpty();
              assertThat(packet.persistence().sqlAnalyses()).isEmpty();
              assertThat(packet.unselectedUnits())
                  .filteredOn(unit -> XML_PATH.equals(unit.unitRef()))
                  .singleElement()
                  .satisfies(unit -> assertThat(unit.reason()).isNotBlank());
              String rendered =
                  Assertions.assertDoesNotThrow(
                      () -> CodeReadingMaterialMarkdown.renderPacket(packet));
              assertThat(rendered).doesNotContain("wide ".repeat(100));
              assertThat(packet.selfContainedUtf8Bytes())
                  .isEqualTo(rendered.getBytes(StandardCharsets.UTF_8).length)
                  .isLessThanOrEqualTo(packetLimit);
            });
    assertThat(result.coverage())
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.status())
                  .isEqualTo(CodeReadingMaterialSet.CoverageStatus.COLLECTED_WITH_LIMITATIONS);
              assertThat(coverage.limitations()).isNotEmpty();
            });
  }

  @Test
  void rejectsPersistenceIndexOwnedByAnotherSnapshotOrNavigationPublication() {
    Fixture snapshotMismatch =
        fixture(
            javaCodeIndex(),
            persistenceIndexWithOwnership("snapshot:" + "9".repeat(64), navigationPublication()),
            new CodeReadingMaterialProfile(64_000L, 16));
    assertThatThrownBy(
            () -> new DefaultCodeReadingMaterialBuilder().build(snapshotMismatch.request()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CODE_READING_MATERIALS_INPUT_SNAPSHOT_MISMATCH");

    Fixture navigationMismatch =
        fixture(
            javaCodeIndex(),
            persistenceIndexWithOwnership(SNAPSHOT, alternateNavigationPublication()),
            new CodeReadingMaterialProfile(64_000L, 16));
    assertThatThrownBy(
            () -> new DefaultCodeReadingMaterialBuilder().build(navigationMismatch.request()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CODE_READING_MATERIALS_INPUT_SNAPSHOT_MISMATCH");
  }

  @Test
  void retainsXmlAndReasonWhenSqlProjectionIsUnsupportedWithoutAnAnalysisCopy() {
    String reason = "dynamic fragment cannot form a complete SQL statement";
    Fixture fixture =
        fixture(
            javaCodeIndex(),
            unsupportedSqlPersistenceIndex(reason),
            new CodeReadingMaterialProfile(64_000L, 16));

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.packets())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.persistence().resources())
                  .singleElement()
                  .satisfies(
                      resource -> {
                        assertThat(resource.rawSource()).isEqualTo(XML_SOURCE);
                      });
              assertThat(packet.persistence().sqlAnalyses())
                  .singleElement()
                  .satisfies(
                      sql -> {
                        assertThat(sql.analysisCopy()).isNull();
                        assertThat(sql.status())
                            .isEqualTo(PersistenceMaterialIndex.SqlStatus.UNSUPPORTED);
                        assertThat(sql.reason()).isEqualTo(reason);
                      });
              String rendered =
                  Assertions.assertDoesNotThrow(
                      () -> CodeReadingMaterialMarkdown.renderPacket(packet));
              assertThat(rendered).contains(XML_SOURCE, reason, "UNSUPPORTED");
              assertThat(packet.selfContainedUtf8Bytes())
                  .isEqualTo(rendered.getBytes(StandardCharsets.UTF_8).length);
            });
  }

  @Test
  void rejectsMissingResourceClosureMemberAsCorruptedPersistenceIndex() {
    String missingPath = "src/main/resources/mapper/MissingDependency.xml";
    Fixture fixture =
        fixture(
            javaCodeIndex(),
            persistenceIndexWithResourceDependencies(List.of(missingPath)),
            new CodeReadingMaterialProfile(64_000L, 16));

    assertThatThrownBy(() -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()))
        .isInstanceOf(IllegalArgumentException.class)
        .satisfies(failure -> assertThat(failure.getMessage()).isNotBlank());
  }

  @Test
  void propagatesEntryLimitationsToPacketAndCoverage() {
    EntryCodeContext.Limitation limitation =
        new EntryCodeContext.Limitation(
            "DYNAMIC_DISPATCH",
            "navigation retained multiple candidate targets",
            List.of(CONTROLLER_KEY),
            List.of("call:controller-service"));
    Fixture fixture =
        fixture(
            javaCodeIndexWithLimitations(List.of(limitation)),
            enabledPersistenceIndex(),
            new CodeReadingMaterialProfile(64_000L, 16));

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.packets())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.limitations())
                  .containsExactly(
                      "DYNAMIC_DISPATCH: navigation retained multiple candidate targets");
            });
    assertThat(result.coverage())
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.status())
                  .isEqualTo(CodeReadingMaterialSet.CoverageStatus.COLLECTED_WITH_LIMITATIONS);
              assertThat(coverage.limitations())
                  .containsExactly(
                      "DYNAMIC_DISPATCH: navigation retained multiple candidate targets");
            });
  }

  @Test
  void keepsEntryLimitationAndUncollectedReasonOwnedByTheirEntry() {
    JavaCodeIndex base = sharedMethodJavaCodeIndex();
    JavaCodeIndex.EntryCollection first = base.entries().get(0);
    EntryCodeContext firstContext = first.context();
    EntryCodeContext.Limitation limitation =
        new EntryCodeContext.Limitation(
            "DYNAMIC_DISPATCH",
            "first entry retained multiple candidate targets",
            List.of(first.seed().methodKey()),
            List.of("call:neutral-first-service"));
    EntryCodeContext limitedFirst =
        new EntryCodeContext(
            firstContext.schemaVersion(),
            firstContext.entryId(),
            firstContext.entryMethodKey(),
            firstContext.methods(),
            firstContext.calls(),
            firstContext.supportingSources(),
            List.of(limitation),
            firstContext.technicalEnhancements());
    EntrySeed uncollectedSeed =
        new EntrySeed(
            "entry:uncollected", "method:uncollected", new SourceRange(0, 1, 1, 1), "HTTP");
    JavaCodeIndex index =
        new JavaCodeIndex(
            base.engine(),
            base.snapshotId(),
            base.snapshotRef(),
            base.catalog(),
            List.of(
                JavaCodeIndex.EntryCollection.collected(first.seed(), limitedFirst),
                base.entries().get(1),
                JavaCodeIndex.EntryCollection.notCollected(
                    uncollectedSeed, "JDT_SOURCE_UNAVAILABLE")),
            base.technicalEnhancements());
    Fixture fixture =
        fixture(index, enabledPersistenceIndex(), new CodeReadingMaterialProfile(64_000L, 16));

    CodeReadingMaterialSet result =
        Assertions.assertDoesNotThrow(
            () -> new DefaultCodeReadingMaterialBuilder().build(fixture.request()));

    assertThat(result.coverage())
        .filteredOn(coverage -> "entry:neutral-first".equals(coverage.entryId()))
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.status())
                  .isEqualTo(CodeReadingMaterialSet.CoverageStatus.COLLECTED_WITH_LIMITATIONS);
              assertThat(coverage.limitations())
                  .containsExactly(
                      "DYNAMIC_DISPATCH: first entry retained multiple candidate targets");
            });
    assertThat(result.coverage())
        .filteredOn(coverage -> "entry:neutral-second".equals(coverage.entryId()))
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.status())
                  .isEqualTo(CodeReadingMaterialSet.CoverageStatus.COLLECTED);
              assertThat(coverage.limitations()).isEmpty();
            });
    assertThat(result.coverage())
        .filteredOn(coverage -> "entry:uncollected".equals(coverage.entryId()))
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.status())
                  .isEqualTo(CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED);
              assertThat(coverage.limitations()).containsExactly("JDT_SOURCE_UNAVAILABLE");
            });
    String rendered =
        Assertions.assertDoesNotThrow(() -> CodeReadingMaterialMarkdown.render(result));
    assertThat(rendered).contains("JDT_SOURCE_UNAVAILABLE");
  }

  private static void assertCallCarriesActualToFormal(
      CodeReadingMaterialSet.Packet packet, String callKey, String targetMethodKey) {
    assertThat(packet.calls())
        .filteredOn(call -> callKey.equals(call.call().callKey()))
        .singleElement()
        .satisfies(
            entryCall -> {
              assertThat(entryCall.call().actualArguments())
                  .extracting(EntryCodeContext.ActualArgument::expression)
                  .containsExactly("label");
              assertThat(entryCall.call().targets())
                  .singleElement()
                  .satisfies(
                      target -> {
                        assertThat(target.methodKey()).isEqualTo(targetMethodKey);
                        assertThat(target.argumentAssociations())
                            .singleElement()
                            .satisfies(
                                association -> {
                                  assertThat(association.actualOrdinals()).containsExactly(0);
                                  assertThat(association.formalOrdinal()).isEqualTo(0);
                                });
                      });
            });
  }

  private static Fixture fixture(PersistenceMaterialIndex persistenceIndex) {
    return fixture(javaCodeIndex(), persistenceIndex, new CodeReadingMaterialProfile(64_000L, 16));
  }

  private static Fixture fixture(
      JavaCodeIndex javaCodeIndex,
      PersistenceMaterialIndex persistenceIndex,
      CodeReadingMaterialProfile profile) {
    VerifiedSourceInventoryReference sourceInventory =
        new VerifiedSourceInventoryReference(
            publication(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '1'));
    ProgramGraphsReference navigationPublication =
        new ProgramGraphsReference(publication(AnalysisStepKey.PROGRAM_GRAPHS, '2'));
    AnalysisStepPublicationReference persistencePublication =
        publication(AnalysisStepKey.PROVEN_CODE_FACTS, '3');
    CodeReadingMaterialRequest request =
        new CodeReadingMaterialRequest(
            sourceInventory,
            navigationPublication,
            persistencePublication,
            javaCodeIndex,
            persistenceIndex,
            profile);
    return new Fixture(
        request, sourceInventory, navigationPublication, persistencePublication, profile);
  }

  private static JavaCodeIndex javaCodeIndex() {
    return javaCodeIndexWithLimitations(List.of());
  }

  private static JavaCodeIndex javaCodeIndexWithLimitations(
      List<EntryCodeContext.Limitation> entryLimitations) {
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "material fixture has no strict graph or fact enrichment",
            List.of(),
            List.of(),
            null);
    EntryCodeContext.MethodCode controller =
        method(
            CONTROLLER_KEY,
            CONTROLLER_FQN,
            "register",
            CONTROLLER_PATH,
            CONTROLLER_SOURCE,
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode service =
        method(
            SERVICE_KEY,
            SERVICE_FQN,
            "register",
            SERVICE_PATH,
            SERVICE_SOURCE,
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode mapper =
        method(
            MAPPER_KEY,
            MAPPER_FQN,
            "find",
            MAPPER_PATH,
            MAPPER_SOURCE,
            List.of(
                new JavaDeclarationCatalog.ParameterView(
                    0, "label", "String", false, List.of("@Param(\"label\")"))),
            "String",
            false);
    EntrySeed seed =
        new EntrySeed(
            ENTRY_ID, CONTROLLER_KEY, new SourceRange(0, CONTROLLER_SOURCE.length(), 1, 1), "HTTP");
    EntryCodeContext context =
        new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            ENTRY_ID,
            CONTROLLER_KEY,
            List.of(controller, service, mapper),
            List.of(controllerServiceCall(), serviceMapperCall()),
            List.of(),
            entryLimitations,
            enhancements);
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            SNAPSHOT,
            List.of(CONTROLLER_PATH, SERVICE_PATH, MAPPER_PATH),
            List.of(
                type(CONTROLLER_PATH, CONTROLLER_FQN, CONTROLLER_KEY),
                type(SERVICE_PATH, SERVICE_FQN, SERVICE_KEY),
                type(MAPPER_PATH, MAPPER_FQN, MAPPER_KEY)),
            List.of(declaration(controller), declaration(service), declaration(mapper)),
            List.of(),
            List.of(),
            Map.of());
    ArtifactReference snapshotRef =
        new ArtifactReference(
            new ArtifactId("verified-snapshot:" + "6".repeat(64)),
            new Sha256Digest("7".repeat(64)));
    return new JavaCodeIndex(
        new EngineDescriptor("jdt", "fixture", Map.of("jdt", "fixture"), "17", List.of()),
        SNAPSHOT,
        snapshotRef,
        catalog,
        List.of(JavaCodeIndex.EntryCollection.collected(seed, context)),
        enhancements);
  }

  private static JavaCodeIndex emptyJavaCodeIndex() {
    return indexWithEntries(List.of(), List.of(), List.of());
  }

  private static JavaCodeIndex uncollectedJavaCodeIndex() {
    EntrySeed seed =
        new EntrySeed(
            "entry:uncollected", "method:uncollected", new SourceRange(0, 1, 1, 1), "HTTP");
    return indexWithEntries(
        List.of(JavaCodeIndex.EntryCollection.notCollected(seed, "JDT_SOURCE_UNAVAILABLE")),
        List.of(),
        List.of());
  }

  private static JavaCodeIndex sharedMethodJavaCodeIndex() {
    EntryCodeContext.MethodCode first =
        method(
            "method:neutral-first",
            "example.neutral.NeutralFirstController",
            "first",
            "src/main/java/example/neutral/NeutralFirstController.java",
            "public String first(String label) { return service.register(label); }",
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode second =
        method(
            "method:neutral-second",
            "example.neutral.NeutralSecondController",
            "second",
            "src/main/java/example/neutral/NeutralSecondController.java",
            "public String second(String label) { return service.register(label); }",
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode service =
        method(
            SERVICE_KEY,
            SERVICE_FQN,
            "register",
            SERVICE_PATH,
            SERVICE_SOURCE,
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode mapper =
        method(
            MAPPER_KEY,
            MAPPER_FQN,
            "find",
            MAPPER_PATH,
            MAPPER_SOURCE,
            List.of(
                new JavaDeclarationCatalog.ParameterView(
                    0, "label", "String", false, List.of("@Param(\"label\")"))),
            "String",
            false);
    EntrySeed firstSeed =
        new EntrySeed(
            "entry:neutral-first",
            first.methodKey(),
            new SourceRange(0, first.source().text().length(), 1, 1),
            "HTTP");
    EntrySeed secondSeed =
        new EntrySeed(
            "entry:neutral-second",
            second.methodKey(),
            new SourceRange(0, second.source().text().length(), 1, 1),
            "HTTP");
    EntryCodeContext firstContext =
        entryContext(
            firstSeed,
            first,
            service,
            mapper,
            "call:neutral-first-service",
            "call:neutral-first-mapper");
    EntryCodeContext secondContext =
        entryContext(
            secondSeed,
            second,
            service,
            mapper,
            "call:neutral-second-service",
            "call:neutral-second-mapper");
    return indexWithEntries(
        List.of(
            JavaCodeIndex.EntryCollection.collected(firstSeed, firstContext),
            JavaCodeIndex.EntryCollection.collected(secondSeed, secondContext)),
        List.of(first, second, service, mapper),
        List.of());
  }

  private static JavaCodeIndex sharedRecursiveJavaCodeIndex() {
    EntryCodeContext.MethodCode first =
        method(
            "method:neutral-first",
            "example.neutral.NeutralFirstController",
            "first",
            "src/main/java/example/neutral/NeutralFirstController.java",
            "public String first(String label) { return service.register(label); }",
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode second =
        method(
            "method:neutral-second",
            "example.neutral.NeutralSecondController",
            "second",
            "src/main/java/example/neutral/NeutralSecondController.java",
            "public String second(String label) { return service.register(label); }",
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode service =
        method(
            SERVICE_KEY,
            SERVICE_FQN,
            "register",
            SERVICE_PATH,
            SERVICE_SOURCE,
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode alternateService =
        method(
            ALTERNATE_SERVICE_KEY,
            ALTERNATE_SERVICE_FQN,
            "register",
            ALTERNATE_SERVICE_PATH,
            ALTERNATE_SERVICE_SOURCE,
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode mapper =
        method(
            MAPPER_KEY,
            MAPPER_FQN,
            "find",
            MAPPER_PATH,
            MAPPER_SOURCE,
            List.of(
                new JavaDeclarationCatalog.ParameterView(
                    0, "label", "String", false, List.of("@Param(\"label\")"))),
            "String",
            false);
    EntrySeed firstSeed =
        new EntrySeed(
            "entry:neutral-first",
            first.methodKey(),
            new SourceRange(0, first.source().text().length(), 1, 1),
            "HTTP");
    EntrySeed secondSeed =
        new EntrySeed(
            "entry:neutral-second",
            second.methodKey(),
            new SourceRange(0, second.source().text().length(), 1, 1),
            "HTTP");
    EntryCodeContext firstContext =
        recursiveEntryContext(
            first,
            firstSeed,
            service,
            alternateService,
            mapper,
            "call:neutral-first-service-candidates",
            "call:neutral-first-mapper",
            "call:neutral-first-recursion");
    EntryCodeContext secondContext =
        recursiveEntryContext(
            second,
            secondSeed,
            service,
            alternateService,
            mapper,
            "call:neutral-second-service-candidates",
            "call:neutral-second-mapper",
            "call:neutral-second-recursion");
    return indexWithEntries(
        List.of(
            JavaCodeIndex.EntryCollection.collected(firstSeed, firstContext),
            JavaCodeIndex.EntryCollection.collected(secondSeed, secondContext)),
        List.of(first, second, service, alternateService, mapper),
        List.of());
  }

  private static EntryCodeContext entryContext(
      EntrySeed seed,
      EntryCodeContext.MethodCode root,
      EntryCodeContext.MethodCode service,
      EntryCodeContext.MethodCode mapper,
      String rootCallKey,
      String mapperCallKey) {
    return new EntryCodeContext(
        EntryCodeContext.SCHEMA_VERSION,
        seed.entryId(),
        seed.methodKey(),
        List.of(root, service, mapper),
        List.of(
            call(
                rootCallKey,
                root.methodKey(),
                "service.register(label)",
                SERVICE_KEY,
                "BODY_INCLUDED",
                null,
                "IMPLEMENTATION",
                List.of("CALL_HIERARCHY", "IMPLEMENTATION")),
            call(
                mapperCallKey,
                service.methodKey(),
                "mapper.find(label)",
                MAPPER_KEY,
                "DECLARATION_ONLY",
                "mapper interface has no body",
                "DECLARATION",
                List.of("CALL_HIERARCHY", "ENGINE_BINDING"))),
        List.of(),
        List.of(),
        fixtureEnhancements());
  }

  private static EntryCodeContext recursiveEntryContext(
      EntryCodeContext.MethodCode root,
      EntrySeed seed,
      EntryCodeContext.MethodCode service,
      EntryCodeContext.MethodCode alternateService,
      EntryCodeContext.MethodCode mapper,
      String rootCallKey,
      String mapperCallKey,
      String recursionCallKey) {
    return new EntryCodeContext(
        EntryCodeContext.SCHEMA_VERSION,
        seed.entryId(),
        seed.methodKey(),
        List.of(root, service, alternateService, mapper),
        List.of(
            candidateCall(
                rootCallKey, root.methodKey(), service.methodKey(), alternateService.methodKey()),
            call(
                mapperCallKey,
                service.methodKey(),
                "mapper.find(label)",
                MAPPER_KEY,
                "DECLARATION_ONLY",
                "mapper interface has no body",
                "DECLARATION",
                List.of("CALL_HIERARCHY", "ENGINE_BINDING")),
            call(
                recursionCallKey,
                service.methodKey(),
                "service.register(label)",
                SERVICE_KEY,
                "BODY_INCLUDED",
                null,
                "IMPLEMENTATION",
                List.of("CALL_HIERARCHY", "IMPLEMENTATION"))),
        List.of(),
        List.of(),
        fixtureEnhancements());
  }

  private static EntryCodeContext.CallSite candidateCall(
      String callKey, String callerMethodKey, String primaryMethodKey, String alternateMethodKey) {
    String expression = "service.register(label)";
    EntryCodeContext.ArgumentAssociation association =
        new EntryCodeContext.ArgumentAssociation(List.of(0), 0, "POSITIONAL");
    return new EntryCodeContext.CallSite(
        callKey,
        callerMethodKey,
        "METHOD",
        new SourceRange(0, expression.length(), 1, 1),
        new SourceRange(0, expression.length(), 1, 1),
        expression,
        "service",
        List.of(new EntryCodeContext.ActualArgument(0, "label")),
        List.of(),
        false,
        List.of(
            new EntryCodeContext.CallTarget(
                primaryMethodKey,
                List.of("IMPLEMENTATION"),
                primaryMethodKey,
                List.of("CALL_HIERARCHY", "IMPLEMENTATION"),
                "BODY_INCLUDED",
                null,
                List.of(association)),
            new EntryCodeContext.CallTarget(
                alternateMethodKey,
                List.of("IMPLEMENTATION"),
                alternateMethodKey,
                List.of("CALL_HIERARCHY", "IMPLEMENTATION"),
                "BODY_INCLUDED",
                null,
                List.of(association))),
        "CANDIDATES",
        "navigation retained multiple declaration candidates");
  }

  private static JavaCodeIndex capacityJavaCodeIndex() {
    EntryCodeContext.MethodCode controller =
        method(
            CONTROLLER_KEY,
            CONTROLLER_FQN,
            "register",
            CONTROLLER_PATH,
            CONTROLLER_SOURCE,
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode wideService =
        method(
            SERVICE_KEY,
            SERVICE_FQN,
            "register",
            SERVICE_PATH,
            "public String register(String label) { /* "
                + "wide ".repeat(5_000)
                + "*/ return mapper.find(label); }",
            List.of(
                new JavaDeclarationCatalog.ParameterView(0, "label", "String", false, List.of())),
            "String",
            true);
    EntryCodeContext.MethodCode mapper =
        method(
            MAPPER_KEY,
            MAPPER_FQN,
            "find",
            MAPPER_PATH,
            MAPPER_SOURCE,
            List.of(
                new JavaDeclarationCatalog.ParameterView(
                    0, "label", "String", false, List.of("@Param(\"label\")"))),
            "String",
            false);
    EntrySeed seed =
        new EntrySeed(
            ENTRY_ID, CONTROLLER_KEY, new SourceRange(0, CONTROLLER_SOURCE.length(), 1, 1), "HTTP");
    return indexWithEntries(
        List.of(
            JavaCodeIndex.EntryCollection.collected(
                seed,
                entryContext(
                    seed,
                    controller,
                    wideService,
                    mapper,
                    "call:controller-service",
                    "call:service-mapper"))),
        List.of(controller, wideService, mapper),
        List.of());
  }

  private static JavaCodeIndex indexWithEntries(
      List<JavaCodeIndex.EntryCollection> entries,
      List<EntryCodeContext.MethodCode> methods,
      List<JavaDeclarationCatalog.TypeDeclaration> types) {
    EntryCodeContext.TechnicalEnhancements enhancements = fixtureEnhancements();
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            SNAPSHOT,
            methods.stream().map(method -> method.source().path()).distinct().toList(),
            types,
            methods.stream().map(CodeReadingMaterialBuilderTest::declaration).toList(),
            List.of(),
            List.of(),
            Map.of());
    return new JavaCodeIndex(
        new EngineDescriptor("jdt", "fixture", Map.of("jdt", "fixture"), "17", List.of()),
        SNAPSHOT,
        snapshotReference(),
        catalog,
        entries,
        enhancements);
  }

  private static EntryCodeContext.TechnicalEnhancements fixtureEnhancements() {
    return new EntryCodeContext.TechnicalEnhancements(
        EntryCodeContext.Availability.NOT_PRODUCED,
        "material fixture has no strict graph or fact enrichment",
        List.of(),
        List.of(),
        null);
  }

  private static ArtifactReference snapshotReference() {
    return new ArtifactReference(
        new ArtifactId("verified-snapshot:" + "6".repeat(64)), new Sha256Digest("7".repeat(64)));
  }

  private static EntryCodeContext.MethodCode method(
      String methodKey,
      String declaringType,
      String name,
      String path,
      String source,
      List<JavaDeclarationCatalog.ParameterView> parameters,
      String returnType,
      boolean bodyPresent) {
    return new EntryCodeContext.MethodCode(
        methodKey,
        "METHOD",
        declaringType,
        name,
        parameters,
        returnType,
        new EntryCodeContext.SourceSource(path, new SourceRange(0, source.length(), 1, 1), source),
        bodyPresent);
  }

  private static JavaDeclarationCatalog.MethodDeclarationView declaration(
      EntryCodeContext.MethodCode method) {
    return new JavaDeclarationCatalog.MethodDeclarationView(
        method.methodKey(),
        method.declaringType(),
        method.name(),
        method.kind(),
        method.parameters(),
        method.returnTypeText(),
        method.annotations(),
        method.source().path(),
        new SourceRange(
            method.source().startOffsetUtf16(),
            method.source().lengthUtf16(),
            method.source().startLine(),
            method.source().endLine()),
        method.bodyPresent());
  }

  private static JavaDeclarationCatalog.TypeDeclaration type(
      String path, String qualifiedName, String methodKey) {
    return new JavaDeclarationCatalog.TypeDeclaration(
        path,
        new SourceRange(0, 1, 1, 1),
        qualifiedName,
        qualifiedName.endsWith("Mapper") ? "INTERFACE" : "CLASS",
        List.of(),
        List.of(),
        List.of(methodKey),
        List.of());
  }

  private static EntryCodeContext.CallSite controllerServiceCall() {
    return call(
        "call:controller-service",
        CONTROLLER_KEY,
        "service.register(label)",
        SERVICE_KEY,
        "BODY_INCLUDED",
        null,
        "IMPLEMENTATION",
        List.of("CALL_HIERARCHY", "IMPLEMENTATION"));
  }

  private static EntryCodeContext.CallSite serviceMapperCall() {
    return call(
        "call:service-mapper",
        SERVICE_KEY,
        "mapper.find(label)",
        MAPPER_KEY,
        "DECLARATION_ONLY",
        "mapper interface has no body",
        "DECLARATION",
        List.of("CALL_HIERARCHY", "ENGINE_BINDING"));
  }

  private static EntryCodeContext.CallSite call(
      String callKey,
      String callerMethodKey,
      String expression,
      String targetMethodKey,
      String expansion,
      String reason,
      String role,
      List<String> navigationKinds) {
    return new EntryCodeContext.CallSite(
        callKey,
        callerMethodKey,
        "METHOD",
        new SourceRange(0, expression.length(), 1, 1),
        new SourceRange(0, expression.length(), 1, 1),
        expression,
        "service".equals(expression.substring(0, Math.min(7, expression.length())))
            ? "service"
            : "mapper",
        List.of(new EntryCodeContext.ActualArgument(0, "label")),
        List.of(),
        false,
        List.of(
            new EntryCodeContext.CallTarget(
                targetMethodKey,
                List.of(role),
                targetMethodKey,
                navigationKinds,
                expansion,
                reason,
                List.of(new EntryCodeContext.ArgumentAssociation(List.of(0), 0, "POSITIONAL")))),
        "LOCATED",
        null);
  }

  private static PersistenceMaterialIndex enabledPersistenceIndex() {
    return enabledPersistenceIndex(XML_SOURCE);
  }

  private static PersistenceMaterialIndex enabledPersistenceIndex(String xmlSource) {
    return enabledPersistenceIndex(xmlSource, List.of());
  }

  private static PersistenceMaterialIndex enabledPersistenceIndex(
      String xmlSource, List<String> dependencyPaths) {
    PersistenceMaterialIndex.XmlNode xmlSubtree =
        new PersistenceMaterialIndex.XmlNode(
            PersistenceMaterialIndex.XmlNodeKind.ELEMENT,
            "select",
            Map.of("id", "find"),
            null,
            List.of(
                new PersistenceMaterialIndex.XmlNode(
                    PersistenceMaterialIndex.XmlNodeKind.TEXT,
                    null,
                    Map.of(),
                    "SELECT label FROM neutral_table WHERE label = #{label}",
                    List.of())));
    String statementRef = "statement:" + XML_PATH + "#find";
    PersistenceMaterialIndex.Resource resource =
        new PersistenceMaterialIndex.Resource(XML_PATH, MAPPER_FQN, xmlSource, dependencyPaths);
    PersistenceMaterialIndex.Statement statement =
        new PersistenceMaterialIndex.Statement(
            statementRef, XML_PATH, MAPPER_FQN, "find", "select", null, xmlSubtree, List.of());
    PersistenceMaterialIndex.JavaBinding binding =
        new PersistenceMaterialIndex.JavaBinding(
            MAPPER_FQN,
            MAPPER_KEY,
            "find(java.lang.String)",
            "EXACT",
            List.of(
                new PersistenceMaterialIndex.ParameterBinding(
                    0,
                    "label",
                    "String",
                    List.of("@Param(\"label\")"),
                    List.of("label"),
                    List.of())),
            List.of(new PersistenceMaterialIndex.StatementRef(statementRef, null)),
            List.of());
    PersistenceMaterialIndex.SqlAnalysis sql =
        new PersistenceMaterialIndex.SqlAnalysis(
            statementRef,
            "SELECT label FROM neutral_table WHERE label = #{label}",
            List.of(SQL_TRANSFORMATION),
            new PersistenceMaterialIndex.SqlAstNode(
                "SELECT",
                null,
                Map.of(),
                List.of(
                    new PersistenceMaterialIndex.SqlAstNode(
                        "FROM", "neutral_table", Map.of(), List.of()))),
            PersistenceMaterialIndex.SqlStatus.PARSED,
            null);
    return new PersistenceMaterialIndex(
        new PersistenceMaterialIndex.Header(
            PersistenceMaterialIndex.Status.ENABLED,
            SNAPSHOT,
            navigationPublication(),
            List.of(
                new PersistenceMaterialIndex.Tool("mybatis", "3.5.19"),
                new PersistenceMaterialIndex.Tool("jsqlparser", "5.3"))),
        List.of(resource),
        List.of(statement),
        List.of(binding),
        List.of(sql),
        List.of());
  }

  private static PersistenceMaterialIndex capacityPersistenceIndex() {
    return enabledPersistenceIndex(
        "<mapper namespace=\"example.neutral.NeutralMapper\">"
            + "<select id=\"find\">SELECT label FROM neutral_table WHERE label = #{label}</select>"
            + "<!-- "
            + "wide ".repeat(5_000)
            + " -->"
            + "</mapper>");
  }

  private static PersistenceMaterialIndex oversizedXmlPersistenceIndex() {
    return enabledPersistenceIndex(
        "<mapper namespace=\"example.neutral.NeutralMapper\">"
            + "<select id=\"find\">SELECT label FROM neutral_table WHERE label = #{label}</select>"
            + "<!-- "
            + "wide ".repeat(5_000)
            + " -->"
            + "</mapper>");
  }

  private static PersistenceMaterialIndex unsupportedSqlPersistenceIndex(String reason) {
    PersistenceMaterialIndex base = enabledPersistenceIndex();
    PersistenceMaterialIndex.SqlAnalysis original = base.sqlAnalyses().get(0);
    PersistenceMaterialIndex.SqlAnalysis unsupported =
        new PersistenceMaterialIndex.SqlAnalysis(
            original.statementRef(),
            null,
            List.of("dynamic-fragment-retained"),
            null,
            PersistenceMaterialIndex.SqlStatus.UNSUPPORTED,
            reason);
    return new PersistenceMaterialIndex(
        base.header(),
        base.resources(),
        base.statements(),
        base.bindings(),
        List.of(unsupported),
        base.diagnostics());
  }

  private static PersistenceMaterialIndex persistenceIndexWithResourceDependencies(
      List<String> dependencyPaths) {
    return enabledPersistenceIndex(XML_SOURCE, dependencyPaths);
  }

  private static int occurrences(String text, String value) {
    int count = 0;
    int offset = 0;
    while ((offset = text.indexOf(value, offset)) >= 0) {
      count++;
      offset += value.length();
    }
    return count;
  }

  private static PersistenceMaterialIndex persistenceIndexWithOwnership(
      String sourceSnapshotId, ProgramGraphsReference navigation) {
    PersistenceMaterialIndex base = enabledPersistenceIndex();
    return new PersistenceMaterialIndex(
        new PersistenceMaterialIndex.Header(
            PersistenceMaterialIndex.Status.ENABLED,
            sourceSnapshotId,
            navigation,
            base.header().tools()),
        base.resources(),
        base.statements(),
        base.bindings(),
        base.sqlAnalyses(),
        base.diagnostics());
  }

  private static PersistenceMaterialIndex disabledPersistenceIndex() {
    return new PersistenceMaterialIndex(
        new PersistenceMaterialIndex.Header(
            PersistenceMaterialIndex.Status.DISABLED, SNAPSHOT, navigationPublication(), List.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static ProgramGraphsReference navigationPublication() {
    return new ProgramGraphsReference(publication(AnalysisStepKey.PROGRAM_GRAPHS, '2'));
  }

  private static ProgramGraphsReference alternateNavigationPublication() {
    return new ProgramGraphsReference(publication(AnalysisStepKey.PROGRAM_GRAPHS, '8'));
  }

  private static AnalysisStepPublicationReference publication(AnalysisStepKey key, char fill) {
    AnalysisRunId run = new AnalysisRunId("analysis-run:" + String.valueOf(fill).repeat(64));
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(run, key),
        new AnalysisStepArtifactRoot("analysis-step-root:" + String.valueOf(fill).repeat(64)),
        new AnalysisStepReceiptId("analysis-step-receipt:" + String.valueOf(fill).repeat(64)),
        new Sha256Digest(String.valueOf(fill).repeat(64)));
  }

  private record Fixture(
      CodeReadingMaterialRequest request,
      VerifiedSourceInventoryReference sourceInventory,
      ProgramGraphsReference navigationPublication,
      AnalysisStepPublicationReference persistencePublication,
      CodeReadingMaterialProfile profile) {}
}
