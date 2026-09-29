package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

class TechnicalAnalysisRunOutputV8Test {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  @TempDir Path temporaryDirectory;

  @Test
  void blockedCollectCodeV8RoundTripsOnlyItsR1OwnedInstalledReportRefs() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("technical-output-v8-blocked-store");
    BlockedRun fixture = writeBlockedOutput(storeRoot);

    ObjectNode wire = outputWire(storeRoot, fixture.run().runId());
    assertThat(text(wire, "schemaVersion")).isEqualTo("analysis-run-output-v8");
    assertThat(text(wire, "outputKind")).isEqualTo("TECHNICAL");
    assertThat(text(wire, "runId")).isEqualTo(fixture.run().runId().value());
    assertThat(text(wire, "sourceRunId"))
        .isEqualTo(fixture.basis().preparedSource().publication().address().runId().value());
    assertThat(wire.path("technicalOutput").path("outputRunId").textValue())
        .isEqualTo(fixture.run().runId().value());
    assertThat(wire.path("technicalOutput").path("continuationStatus").textValue())
        .isEqualTo("BLOCKED");
    assertThat(wire.path("technicalOutput").path("inspectionStatus").textValue())
        .isEqualTo("CHECKS_COMPLETE");
    assertThat(strings(wire.path("technicalOutput").path("availableOutputs")))
        .containsExactly("JAVA_ANALYSIS_READINESS", "FRONTEND_HTTP_INDEX");

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      AnalysisRunOutput reopened =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, fixture.run().runId()).orElseThrow();
      assertThat(reopened).isEqualTo(fixture.output());
      assertThat(reopened.sourceRunId())
          .isEqualTo(fixture.basis().preparedSource().publication().address().runId());
      assertThat(reopened.technicalOutput().outputRunId()).isEqualTo(fixture.run().runId());
      assertThat(reopened.technicalOutput().selectedSourceBasis()).isEqualTo(fixture.basis());
      assertThat(reopened.technicalOutput().readinessReport())
          .isEqualTo(fixture.output().technicalOutput().readinessReport());
      assertThat(reopened.technicalOutput().frontendIndex())
          .isEqualTo(fixture.output().technicalOutput().frontendIndex());
      assertThat(reopened.technicalOutput().applicationDiscovery()).isNull();
      assertThat(reopened.technicalOutput().navigation()).isNull();
    }
  }

  @Test
  void rejectsAStaleOrExtraDerivedAvailableOutputOnV8Reopen() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("technical-output-v8-tamper-store");
    BlockedRun fixture = writeBlockedOutput(storeRoot);
    Path outputPath = outputPath(storeRoot, fixture.run().runId());
    ObjectNode wire = outputWire(storeRoot, fixture.run().runId());
    ((ArrayNode) wire.path("technicalOutput").path("availableOutputs")).add("JAVA_CODE_INDEX");
    Files.write(outputPath, JSON.encodeCanonical(wire).copyToByteArray());

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      assertThatThrownBy(
              () -> RunStoreBootstrap.reopenAnalysisRunOutput(store, fixture.run().runId()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ANALYSIS_RUN_OUTPUT_INVALID");
    }
  }

  @Test
  void persistenceRunNavigationMustBeTheExactRequestedStep03Publication() {
    AnalysisStepPublicationReference step01 = sourcePreparationPublication('a');
    SelectedSourceBasis basis = preparedBasis(step01);
    AnalysisRunId r1 = runId('b');
    AnalysisStepPublicationReference requestedStep03 =
        publication(r1, AnalysisStepKey.PROGRAM_GRAPHS, 'c');
    assertThatThrownBy(
            () ->
                new TechnicalRunOutput(
                    AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE,
                    runId('8'),
                    basis,
                    requestedStep03,
                    TechnicalInspectionStatus.CHECKS_COMPLETE,
                    TechnicalContinuationStatus.READY,
                    modulePublication(r1, 5, "java-analysis-readiness", '3'),
                    modulePublication(r1, 6, "frontend-http-discovery", '4'),
                    publication(r1, AnalysisStepKey.APPLICATION_DISCOVERY, '5'),
                    publication(r1, AnalysisStepKey.PROGRAM_GRAPHS, '6'),
                    publication(runId('8'), AnalysisStepKey.PROVEN_CODE_FACTS, '7'),
                    null,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TECHNICAL_RUN_OUTPUT_INVALID");
  }

  @Test
  void blockedPersistenceOutputMayOmitUninstalledStep04WithoutRelaxingItsR1Owner() {
    AnalysisStepPublicationReference step01 = sourcePreparationPublication('a');
    SelectedSourceBasis basis = preparedBasis(step01);
    AnalysisRunId r1 = runId('b');
    AnalysisRunId r2 = runId('c');
    ModulePublicationReference readiness = modulePublication(r1, 5, "java-analysis-readiness", '1');
    ModulePublicationReference frontend = modulePublication(r1, 6, "frontend-http-discovery", '2');
    AnalysisStepPublicationReference applicationDiscovery =
        publication(r1, AnalysisStepKey.APPLICATION_DISCOVERY, '3');
    AnalysisStepPublicationReference navigation =
        publication(r1, AnalysisStepKey.PROGRAM_GRAPHS, '4');

    assertThatCode(
            () -> {
              TechnicalRunOutput blocked =
                  new TechnicalRunOutput(
                      AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE,
                      r2,
                      basis,
                      navigation,
                      TechnicalInspectionStatus.CHECKS_INCOMPLETE,
                      TechnicalContinuationStatus.BLOCKED,
                      readiness,
                      frontend,
                      applicationDiscovery,
                      navigation,
                      null,
                      null,
                      List.of());
              AnalysisRunOutput output = AnalysisRunOutput.technical(blocked);
              assertThat(blocked.persistence()).isNull();
              assertThat(blocked.readingMaterials()).isNull();
              assertThat(blocked.availableOutputs())
                  .containsExactly(
                      TechnicalOutputArtifactKey.JAVA_ANALYSIS_READINESS,
                      TechnicalOutputArtifactKey.FRONTEND_HTTP_INDEX,
                      TechnicalOutputArtifactKey.APPLICATION_DISCOVERY,
                      TechnicalOutputArtifactKey.JAVA_CODE_INDEX);
              assertThat(output.sourceRunId())
                  .isEqualTo(basis.preparedSource().publication().address().runId());
            })
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () ->
                new TechnicalRunOutput(
                    AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE,
                    r2,
                    basis,
                    navigation,
                    TechnicalInspectionStatus.CHECKS_INCOMPLETE,
                    TechnicalContinuationStatus.BLOCKED,
                    readiness,
                    frontend,
                    applicationDiscovery,
                    publication(runId('d'), AnalysisStepKey.PROGRAM_GRAPHS, '5'),
                    null,
                    null,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TECHNICAL_RUN_OUTPUT_INVALID");
  }

  @Test
  void blockedMaterialsOutputMayOmitUninstalledStep05ButRetainsTheExactR2Step04() {
    AnalysisStepPublicationReference step01 = sourcePreparationPublication('a');
    SelectedSourceBasis basis = preparedBasis(step01);
    AnalysisRunId r1 = runId('b');
    AnalysisRunId r2 = runId('c');
    AnalysisRunId r3 = runId('d');
    AnalysisStepPublicationReference navigation =
        publication(r1, AnalysisStepKey.PROGRAM_GRAPHS, '1');
    AnalysisStepPublicationReference persistence =
        publication(r2, AnalysisStepKey.PROVEN_CODE_FACTS, '2');
    ModulePublicationReference readiness = modulePublication(r1, 5, "java-analysis-readiness", '3');
    ModulePublicationReference frontend = modulePublication(r1, 6, "frontend-http-discovery", '4');
    AnalysisStepPublicationReference applicationDiscovery =
        publication(r1, AnalysisStepKey.APPLICATION_DISCOVERY, '5');

    assertThatCode(
            () -> {
              TechnicalRunOutput blocked =
                  new TechnicalRunOutput(
                      AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS,
                      r3,
                      basis,
                      persistence,
                      TechnicalInspectionStatus.CHECKS_INCOMPLETE,
                      TechnicalContinuationStatus.BLOCKED,
                      readiness,
                      frontend,
                      applicationDiscovery,
                      navigation,
                      persistence,
                      null,
                      List.of());
              AnalysisRunOutput output = AnalysisRunOutput.technical(blocked);
              assertThat(blocked.persistence()).isSameAs(persistence);
              assertThat(blocked.readingMaterials()).isNull();
              assertThat(blocked.availableOutputs())
                  .containsExactly(
                      TechnicalOutputArtifactKey.JAVA_ANALYSIS_READINESS,
                      TechnicalOutputArtifactKey.FRONTEND_HTTP_INDEX,
                      TechnicalOutputArtifactKey.APPLICATION_DISCOVERY,
                      TechnicalOutputArtifactKey.JAVA_CODE_INDEX,
                      TechnicalOutputArtifactKey.PERSISTENCE_MATERIAL_INDEX);
              assertThat(output.sourceRunId())
                  .isEqualTo(basis.preparedSource().publication().address().runId());
            })
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () ->
                new TechnicalRunOutput(
                    AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS,
                    r3,
                    basis,
                    persistence,
                    TechnicalInspectionStatus.CHECKS_INCOMPLETE,
                    TechnicalContinuationStatus.BLOCKED,
                    readiness,
                    frontend,
                    applicationDiscovery,
                    navigation,
                    publication(runId('e'), AnalysisStepKey.PROVEN_CODE_FACTS, '6'),
                    null,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TECHNICAL_RUN_OUTPUT_INVALID");
  }

  @Test
  void readinessAndFrontendReportsAloneCannotMarkR1ReadyWithoutStep02AndStep03() {
    AnalysisStepPublicationReference step01 = sourcePreparationPublication('a');
    SelectedSourceBasis basis = preparedBasis(step01);
    assertThatThrownBy(
            () ->
                new TechnicalRunOutput(
                    AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                    runId('8'),
                    basis,
                    step01,
                    TechnicalInspectionStatus.CHECKS_COMPLETE,
                    TechnicalContinuationStatus.READY,
                    modulePublication(runId('8'), 5, "java-analysis-readiness", '1'),
                    modulePublication(runId('8'), 6, "frontend-http-discovery", '2'),
                    null,
                    null,
                    null,
                    null,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TECHNICAL_RUN_OUTPUT_INVALID");
  }

  @Test
  void continuableR1RequiresBothReportsButBlockedMayExposeOnlyInstalledReports() {
    AnalysisStepPublicationReference step01 = sourcePreparationPublication('a');
    SelectedSourceBasis basis = preparedBasis(step01);
    AnalysisRunId r1 = runId('8');
    ModulePublicationReference readiness = modulePublication(r1, 5, "java-analysis-readiness", '1');
    ModulePublicationReference frontend = modulePublication(r1, 6, "frontend-http-discovery", '2');
    AnalysisStepPublicationReference applicationDiscovery =
        publication(r1, AnalysisStepKey.APPLICATION_DISCOVERY, '3');
    AnalysisStepPublicationReference navigation =
        publication(r1, AnalysisStepKey.PROGRAM_GRAPHS, '4');

    for (TechnicalContinuationStatus continuationStatus :
        List.of(
            TechnicalContinuationStatus.READY,
            TechnicalContinuationStatus.READY_WITH_LIMITATIONS)) {
      assertThatThrownBy(
              () ->
                  new TechnicalRunOutput(
                      AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                      r1,
                      basis,
                      step01,
                      TechnicalInspectionStatus.CHECKS_COMPLETE,
                      continuationStatus,
                      null,
                      frontend,
                      applicationDiscovery,
                      navigation,
                      null,
                      null,
                      List.of()))
          .as("continuation=%s cannot omit the module-5 readiness report", continuationStatus)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("TECHNICAL_RUN_OUTPUT_INVALID");
      assertThatThrownBy(
              () ->
                  new TechnicalRunOutput(
                      AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                      r1,
                      basis,
                      step01,
                      TechnicalInspectionStatus.CHECKS_COMPLETE,
                      continuationStatus,
                      readiness,
                      null,
                      applicationDiscovery,
                      navigation,
                      null,
                      null,
                      List.of()))
          .as("continuation=%s cannot omit the module-6 frontend report", continuationStatus)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("TECHNICAL_RUN_OUTPUT_INVALID");
    }

    TechnicalRunOutput blockedWithOnlyReadiness =
        new TechnicalRunOutput(
            AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
            r1,
            basis,
            step01,
            TechnicalInspectionStatus.CHECKS_COMPLETE,
            TechnicalContinuationStatus.BLOCKED,
            readiness,
            null,
            null,
            null,
            null,
            null,
            List.of());
    assertThat(blockedWithOnlyReadiness.availableOutputs())
        .containsExactly(TechnicalOutputArtifactKey.JAVA_ANALYSIS_READINESS);
  }

  @Test
  void permitsMultipleProblemsWithTheSameCodeWhenTheirReferencesDiffer() {
    AnalysisStepPublicationReference step01 = sourcePreparationPublication('a');
    SelectedSourceBasis basis = preparedBasis(step01);
    TechnicalProblemReference first =
        new TechnicalProblemReference("DEPENDENCY_MISSING", reference("problem-artifact", 'b'));
    TechnicalProblemReference second =
        new TechnicalProblemReference("DEPENDENCY_MISSING", reference("problem-artifact", 'c'));

    TechnicalRunOutput output =
        new TechnicalRunOutput(
            AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
            runId('d'),
            basis,
            step01,
            TechnicalInspectionStatus.CHECKS_INCOMPLETE,
            TechnicalContinuationStatus.BLOCKED,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(first, second));

    assertThat(output.problems()).containsExactly(first, second);
  }

  @Test
  void problemsSnapshotsInputAndReturnsUnmodifiableList() {
    AnalysisStepPublicationReference step01 = sourcePreparationPublication('a');
    SelectedSourceBasis basis = preparedBasis(step01);
    TechnicalProblemReference first =
        new TechnicalProblemReference("DEPENDENCY_MISSING", reference("problem-artifact", 'b'));
    TechnicalProblemReference second =
        new TechnicalProblemReference("DEPENDENCY_MISSING", reference("problem-artifact", 'c'));
    List<TechnicalProblemReference> supplied = new ArrayList<>(List.of(first, second));

    TechnicalRunOutput output =
        new TechnicalRunOutput(
            AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
            runId('d'),
            basis,
            step01,
            TechnicalInspectionStatus.CHECKS_INCOMPLETE,
            TechnicalContinuationStatus.BLOCKED,
            null,
            null,
            null,
            null,
            null,
            null,
            supplied);

    supplied.clear();

    assertThat(output.problems()).containsExactly(first, second);
    assertThatThrownBy(() -> output.problems().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void assembledR3OutputKeepsTheR0R1R2AndR3OwnersAndExactUpstream() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("technical-output-v8-r3-owners-store");
    Files.createDirectory(storeRoot);
    AnalysisStepPublicationReference step01 = sourcePreparationPublication('a');
    SelectedSourceBasis basis = preparedBasis(step01);
    AnalysisRunId r1 = runId('b');
    AnalysisRunId r2 = runId('c');
    AnalysisStepPublicationReference step03 = publication(r1, AnalysisStepKey.PROGRAM_GRAPHS, '1');
    AnalysisStepPublicationReference step04 =
        publication(r2, AnalysisStepKey.PROVEN_CODE_FACTS, '2');
    AnalysisRunRequest.TechnicalAnalysisInputs inputs =
        new AnalysisRunRequest.TechnicalAnalysisInputs(
            AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS,
            reference("technical-profile", '3'),
            reference("resource-budget", '4'),
            reference("schema-bundle", '5'),
            reference("toolchain", '6'),
            reference("artifact-policy-registry", '7'),
            step04);

    AnalysisRunReference queued;
    AnalysisRunOutput expected;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued =
          RunStoreBootstrap.queueAnalysisRun(store, AnalysisRunRequest.technical(basis, inputs));
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      TechnicalRunOutput technical =
          new TechnicalRunOutput(
              AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS,
              queued.runId(),
              basis,
              step04,
              TechnicalInspectionStatus.CHECKS_COMPLETE,
              TechnicalContinuationStatus.READY,
              modulePublication(r1, 5, "java-analysis-readiness", '8'),
              modulePublication(r1, 6, "frontend-http-discovery", '9'),
              publication(r1, AnalysisStepKey.APPLICATION_DISCOVERY, 'a'),
              step03,
              step04,
              publication(queued.runId(), AnalysisStepKey.BUSINESS_FLOWS, 'b'),
              List.of());
      expected = AnalysisRunOutput.technical(technical);
      RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), expected);
    }

    ObjectNode wire = outputWire(storeRoot, queued.runId());
    assertThat(text(wire, "sourceRunId")).isEqualTo(step01.address().runId().value());
    ObjectNode technical = (ObjectNode) wire.path("technicalOutput");
    assertThat(text(technical, "outputRunId")).isEqualTo(queued.runId().value());
    assertThat(text(technical.path("upstreamPublication").path("address"), "runId"))
        .isEqualTo(r2.value());
    assertThat(text(technical.path("readinessReport").path("address"), "runId"))
        .isEqualTo(r1.value());
    assertThat(text(technical.path("frontendIndex").path("address"), "runId"))
        .isEqualTo(r1.value());
    assertThat(text(technical.path("applicationDiscovery").path("address"), "runId"))
        .isEqualTo(r1.value());
    assertThat(text(technical.path("navigation").path("address"), "runId")).isEqualTo(r1.value());
    assertThat(text(technical.path("persistence").path("address"), "runId")).isEqualTo(r2.value());
    assertThat(text(technical.path("readingMaterials").path("address"), "runId"))
        .isEqualTo(queued.runId().value());
    assertThat(strings(technical.path("availableOutputs")))
        .containsExactly(
            "JAVA_ANALYSIS_READINESS",
            "FRONTEND_HTTP_INDEX",
            "APPLICATION_DISCOVERY",
            "JAVA_CODE_INDEX",
            "PERSISTENCE_MATERIAL_INDEX",
            "CODE_READING_MATERIALS");

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      AnalysisRunOutput reopened =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, queued.runId()).orElseThrow();
      assertThat(reopened).isEqualTo(expected);
      assertThat(reopened.technicalOutput().selectedSourceBasis()).isEqualTo(basis);
      assertThat(reopened.technicalOutput().upstreamPublication()).isEqualTo(step04);
      assertThat(reopened.technicalOutput().navigation().address().runId()).isEqualTo(r1);
      assertThat(reopened.technicalOutput().persistence().address().runId()).isEqualTo(r2);
      assertThat(reopened.technicalOutput().readingMaterials().address().runId())
          .isEqualTo(queued.runId());
    }
  }

  @Test
  void legacyV7SourcePreparationOutputReopensWithoutInventingTechnicalReadiness() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("legacy-output-v7-store");
    Files.createDirectory(storeRoot);
    AnalysisRunReference queued;
    AnalysisRunOutput expected;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued =
          RunStoreBootstrap.queueAnalysisRun(
              store,
              AnalysisRunRequest.sourcePreparation(
                  reference("source-preparation-request", '1'),
                  reference("artifact-policy-registry", '2'),
                  reference("schema-bundle", '3'),
                  reference("resource-budget", '4'),
                  reference("preparation-profile", '5'),
                  reference("preparation-toolchain", '6')));
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      AnalysisStepPublicationReference step01 =
          publication(queued.runId(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '7');
      expected =
          AnalysisRunOutput.sourcePreparation(
              queued.runId(), step01, SourcePreparationReadiness.READY, preparedBasis(step01));
      RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), expected);
    }

    ObjectNode wire = outputWire(storeRoot, queued.runId());
    assertThat(text(wire, "schemaVersion")).isEqualTo("analysis-run-output-v7");
    assertThat(wire.has("technicalOutput")).isFalse();
    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      AnalysisRunOutput reopened =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, queued.runId()).orElseThrow();
      assertThat(reopened).isEqualTo(expected);
      assertThat(reopened.technicalOutput()).isNull();
      assertThat(reopened.sourcePreparationReadiness()).isEqualTo(SourcePreparationReadiness.READY);
    }
  }

  @Test
  void historicalV3ThroughV7OutputsKeepTheirOriginalBranchesWithoutTechnicalDefaults()
      throws Exception {
    List<String> expectedSchemas =
        List.of(
            "analysis-run-output-v3",
            "analysis-run-output-v4",
            "analysis-run-output-v5",
            "analysis-run-output-v6",
            "analysis-run-output-v7");

    for (int version = 3; version <= 7; version++) {
      Path storeRoot = temporaryDirectory.resolve("legacy-output-v" + version + "-store");
      Files.createDirectory(storeRoot);
      SelectedSourceBasis basis =
          version == 7 ? preparedBasis(sourcePreparationPublication('c')) : null;
      AnalysisRunRequest request =
          basis == null ? legacyAnalysisRequest() : preparedAnalysisRequest(basis);
      AnalysisRunReference queued;
      AnalysisRunOutput expected;
      try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
        queued = RunStoreBootstrap.queueAnalysisRun(store, request);
        RunStoreBootstrap.transitionAnalysisRun(
            store,
            queued.runId(),
            AnalysisRunLifecycleState.QUEUED,
            AnalysisRunLifecycleState.RUNNING);
        AnalysisStepPublicationReference materials =
            publication(queued.runId(), AnalysisStepKey.BUSINESS_FLOWS, '4');
        expected =
            switch (version) {
              case 3 ->
                  new AnalysisRunOutput(
                      queued.runId(), businessMaterials(queued.runId(), '1'), null, null, null);
              case 4 ->
                  new AnalysisRunOutput(
                      queued.runId(),
                      businessMaterials(queued.runId(), '1'),
                      activities(queued.runId(), '2'),
                      null,
                      null);
              case 5 -> AnalysisRunOutput.readingMaterials(queued.runId(), materials);
              case 6 ->
                  AnalysisRunOutput.step05Processes(
                      queued.runId(),
                      materials,
                      activities(queued.runId(), '2'),
                      processes(queued.runId(), '3'));
              case 7 ->
                  AnalysisRunOutput.analysisV7(
                      AnalysisRunOutput.readingMaterials(queued.runId(), materials), basis);
              default -> throw new IllegalArgumentException("unexpected legacy output version");
            };
        RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), expected);
        RunStoreBootstrap.transitionAnalysisRun(
            store,
            queued.runId(),
            AnalysisRunLifecycleState.RUNNING,
            AnalysisRunLifecycleState.FINISHED);
      }

      ObjectNode wire = outputWire(storeRoot, queued.runId());
      assertThat(text(wire, "schemaVersion")).isEqualTo(expectedSchemas.get(version - 3));
      assertThat(wire.has("technicalOutput")).isFalse();
      try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
        AnalysisRunOutput reopened =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, queued.runId()).orElseThrow();
        assertThat(reopened).isEqualTo(expected);
        assertThat(reopened.technicalOutput()).isNull();
      }
    }
  }

  private BlockedRun writeBlockedOutput(Path storeRoot) throws Exception {
    Files.createDirectory(storeRoot);
    AnalysisStepPublicationReference step01 = sourcePreparationPublication('a');
    SelectedSourceBasis basis = preparedBasis(step01);
    AnalysisRunRequest.TechnicalAnalysisInputs inputs =
        new AnalysisRunRequest.TechnicalAnalysisInputs(
            AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
            reference("technical-profile", '1'),
            reference("resource-budget", '2'),
            reference("schema-bundle", '3'),
            reference("toolchain", '4'),
            reference("artifact-policy-registry", '5'),
            step01);

    AnalysisRunReference queued;
    AnalysisRunOutput output;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued =
          RunStoreBootstrap.queueAnalysisRun(store, AnalysisRunRequest.technical(basis, inputs));
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      TechnicalRunOutput technical =
          new TechnicalRunOutput(
              AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
              queued.runId(),
              basis,
              step01,
              TechnicalInspectionStatus.CHECKS_COMPLETE,
              TechnicalContinuationStatus.BLOCKED,
              modulePublication(queued.runId(), 5, "java-analysis-readiness", '6'),
              modulePublication(queued.runId(), 6, "frontend-http-discovery", '7'),
              null,
              null,
              null,
              null,
              List.of());
      output = AnalysisRunOutput.technical(technical);
      RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), output);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FAILED);
    }
    return new BlockedRun(queued, basis, output);
  }

  private static ModulePublicationReference modulePublication(
      AnalysisRunId runId, int moduleNumber, String moduleKey, char identity) {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.APPLICATION_DISCOVERY, moduleNumber, moduleKey),
        ModuleArtifactRoot.parse("module-root:" + String.valueOf(identity).repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + String.valueOf(identity).repeat(64)),
        digest(identity));
  }

  private static ModulePublicationReference businessMaterials(AnalysisRunId runId, char identity) {
    return modulePublication(
        runId, AnalysisStepKey.FLOW_INTERPRETATION, 10, "business-material-builder", identity);
  }

  private static ModulePublicationReference activities(AnalysisRunId runId, char identity) {
    return modulePublication(
        runId, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer", identity);
  }

  private static ModulePublicationReference processes(AnalysisRunId runId, char identity) {
    return modulePublication(
        runId, AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "business-process-publisher", identity);
  }

  private static ModulePublicationReference modulePublication(
      AnalysisRunId runId,
      AnalysisStepKey step,
      int moduleNumber,
      String moduleKey,
      char identity) {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(runId, step, moduleNumber, moduleKey),
        ModuleArtifactRoot.parse("module-root:" + String.valueOf(identity).repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + String.valueOf(identity).repeat(64)),
        digest(identity));
  }

  private static AnalysisRunRequest legacyAnalysisRequest() {
    return new AnalysisRunRequest(
        artifactId("source-registration", '1'),
        reference("frozen-repository-request", '2'),
        reference("profile-bundle", '3'),
        reference("resource-budget", '4'),
        reference("toolchain", '5'),
        reference("schema-bundle", '6'),
        reference("prompt-bundle", '7'),
        null,
        reference("artifact-policy-registry", '8'),
        reference("candidate-series", '9'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static AnalysisRunRequest preparedAnalysisRequest(SelectedSourceBasis basis) {
    return AnalysisRunRequest.analysis(
        basis,
        reference("frozen-repository-request", '1'),
        reference("profile-bundle", '2'),
        reference("resource-budget", '3'),
        reference("toolchain", '4'),
        reference("schema-bundle", '5'),
        reference("prompt-bundle", '6'),
        null,
        reference("artifact-policy-registry", '7'),
        reference("candidate-series", '8'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static SelectedSourceBasis preparedBasis(
      AnalysisStepPublicationReference sourcePreparationPublication) {
    ArtifactReference schema = reference("schema-bundle", 'a');
    ArtifactReference policy = reference("artifact-policy-registry", 'b');
    ArtifactId snapshot = artifactId("snapshot", 'c');
    PreparedSourceReference preparedSource =
        new PreparedSourceReference(
            snapshot,
            sourcePreparationPublication,
            schema,
            new ArtifactPolicyRegistryReference(policy.artifactId(), policy.sha256()));
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.PREPARED_V1, preparedSource, null, snapshot, digest('d'));
  }

  private static AnalysisStepPublicationReference sourcePreparationPublication(char identity) {
    return publication(runId('a'), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, identity);
  }

  private static AnalysisStepPublicationReference publication(
      AnalysisRunId runId, AnalysisStepKey key, char identity) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, key),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + String.valueOf(identity).repeat(64)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + String.valueOf(identity).repeat(64)),
        digest(identity));
  }

  private static ObjectNode outputWire(Path storeRoot, AnalysisRunId runId) throws Exception {
    byte[] bytes = Files.readAllBytes(outputPath(storeRoot, runId));
    JsonNode parsed = JSON.parseCanonical(ImmutableBytes.copyOf(bytes));
    assertThat(parsed).isInstanceOf(ObjectNode.class);
    return (ObjectNode) parsed;
  }

  private static Path outputPath(Path storeRoot, AnalysisRunId runId) {
    return storeRoot.resolve("analysis-runs").resolve(runId.value()).resolve("run-output.json");
  }

  private static List<String> strings(JsonNode value) {
    List<String> strings = new ArrayList<>();
    value.forEach(item -> strings.add(item.textValue()));
    return List.copyOf(strings);
  }

  private static String text(JsonNode value, String field) {
    return value.path(field).textValue();
  }

  private static AnalysisRunId runId(char identity) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(identity).repeat(64));
  }

  private static ArtifactId artifactId(String prefix, char identity) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(identity).repeat(64));
  }

  private static ArtifactReference reference(String prefix, char identity) {
    return new ArtifactReference(
        artifactId(prefix, identity), new Sha256Digest(String.valueOf(identity).repeat(64)));
  }

  private static Sha256Digest digest(char identity) {
    return new Sha256Digest(String.valueOf(identity).repeat(64));
  }

  private record BlockedRun(
      AnalysisRunReference run, SelectedSourceBasis basis, AnalysisRunOutput output) {}
}
