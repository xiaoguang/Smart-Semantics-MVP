package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialMarkdown;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialPublisher;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialReader;
import org.sourceanalysis.app.analysis.persistence.PersistenceConfiguration;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialPublisher;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.BusinessOutputArtifactKey;
import org.sourceanalysis.app.runtime.ReaderCandidateRound;

/** RED regression for applying the artifact budget to Markdown after material hydration. */
class SourceAnalysisMarkdownArtifactExecutionTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ArtifactStoreLimits LIMITS =
      new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);

  @TempDir Path temporaryDirectory;

  @Test
  void markdownArtifactUsesMarkdownBudgetInsteadOfRejectingLargerCanonicalJsonl() throws Exception {
    Path fixtureRoot = temporaryDirectory.resolve("source-fixture");
    Path runRoot = temporaryDirectory.resolve("target-run");
    Files.createDirectory(runRoot);
    AnalysisRunId queuedRunId;
    int markdownBytes;
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(fixtureRoot)) {
      VerifiedSourceInventoryReference oldSource = fixture.sourceInventory();
      ApplicationDiscoveryReference oldDiscovery = fixture.applicationDiscovery();
      ProgramGraphsReference oldNavigation =
          new ProgramGraphsExecution(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .execute(
                  oldSource, oldDiscovery, minimalJavaSession(fixture), fixture.artifactControls());

      AnalysisRunRequest request = request(fixture.artifactPolicies().reference());
      try (RunStoreHandle targetHandle = RunStoreBootstrap.openForTest(runRoot)) {
        AnalysisRunReference queued = RunStoreBootstrap.queueAnalysisRun(targetHandle, request);
        queuedRunId = queued.runId();
        CanonicalModuleArtifactStore modules =
            new FileSystemCanonicalModuleArtifactStore(
                targetHandle, JSON, fixture.artifactPolicies(), LIMITS);
        CanonicalAnalysisStepArtifactStore steps =
            new FileSystemCanonicalAnalysisStepArtifactStore(
                targetHandle, JSON, fixture.artifactPolicies(), LIMITS);

        VerifiedSourceInventoryReference source =
            new VerifiedSourceInventoryReference(
                cloneStep(
                    fixture.stepArtifacts().reopen(oldSource.publication()),
                    queued.runId(),
                    List.of(),
                    fixture.moduleArtifacts(),
                    modules,
                    steps,
                    fixture.artifactControls()));
        ApplicationDiscoveryReference discovery =
            new ApplicationDiscoveryReference(
                cloneStep(
                    fixture.stepArtifacts().reopen(oldDiscovery.publication()),
                    queued.runId(),
                    List.of(source.publication()),
                    fixture.moduleArtifacts(),
                    modules,
                    steps,
                    fixture.artifactControls()));
        ProgramGraphsReference navigation =
            new ProgramGraphsReference(
                cloneStep(
                    fixture.stepArtifacts().reopen(oldNavigation.publication()),
                    queued.runId(),
                    List.of(source.publication(), discovery.publication()),
                    fixture.moduleArtifacts(),
                    modules,
                    steps,
                    fixture.artifactControls()));

        PersistenceMaterialIndex persistence =
            new PersistenceMaterialIndex(
                new PersistenceMaterialIndex.Header(
                    PersistenceMaterialIndex.Status.DISABLED,
                    fixture.sourceReader().reopen(oldSource).snapshotId(),
                    navigation,
                    List.of()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        AnalysisStepPublicationReference persistencePublication =
            new PersistenceMaterialPublisher(modules, steps)
                .publish(source, discovery, fixture.artifactControls(), persistence);

        JavaCodeIndex javaIndex = new JavaCodeIndexReader(steps).reopen(navigation);
        CodeReadingMaterialSet materials =
            materialSet(javaIndex, source, navigation, persistencePublication);
        AnalysisStepPublicationReference materialPublication =
            new CodeReadingMaterialPublisher(modules, steps)
                .publish(discovery, fixture.artifactControls(), materials);

        int jsonlBytes =
            steps.reopen(materialPublication).semanticPayloads().get(0).canonicalUtf8().size();
        markdownBytes =
            CodeReadingMaterialMarkdown.render(
                    new CodeReadingMaterialReader(steps).reopen(materialPublication))
                .getBytes(StandardCharsets.UTF_8)
                .length;
        assertThat(jsonlBytes)
            .as("the fixture must exercise the JSONL-versus-Markdown budget boundary")
            .isGreaterThan(markdownBytes);

        RunStoreBootstrap.transitionAnalysisRun(
            targetHandle,
            queued.runId(),
            AnalysisRunLifecycleState.QUEUED,
            AnalysisRunLifecycleState.RUNNING);
        RunStoreBootstrap.recordAnalysisRunOutput(
            targetHandle,
            queued.runId(),
            AnalysisRunOutput.readingMaterials(queued.runId(), materialPublication));
        RunStoreBootstrap.transitionAnalysisRun(
            targetHandle,
            queued.runId(),
            AnalysisRunLifecycleState.RUNNING,
            AnalysisRunLifecycleState.FINISHED);
      }

      RepositoryRunConfiguration configuration = configuration(runRoot, fixture.artifactPolicies());
      Path outputPath = temporaryDirectory.resolve("materials.md");
      StringWriter output = new StringWriter();
      assertThatCode(
              () ->
                  SourceAnalysisExecution.executeArtifact(
                      configuration,
                      queuedRunId,
                      BusinessOutputArtifactKey.CODE_READING_MATERIALS,
                      markdownBytes,
                      "markdown",
                      outputPath,
                      new PrintWriter(output)))
          .doesNotThrowAnyException();
      assertThat(Files.readString(outputPath)).startsWith("# Code Reading Materials");
    }
  }

  private static RepositoryRunConfiguration configuration(
      Path runRoot, CanonicalArtifactPolicyRegistry policies) {
    return new RepositoryRunConfiguration(
        JSON,
        null,
        null,
        policies,
        policies,
        null,
        null,
        null,
        runRoot,
        null,
        null,
        null,
        PersistenceConfiguration.disabled(),
        null, // engineConfiguration
        null, // approvedClasspath
        null, // capturePolicyRef
        null, // candidateSeriesRef
        null, // capabilityProfileRef
        null, // verificationPolicyRef
        null, // profileBundleRef
        null, // resourceBudgetRef
        null, // toolchainRef
        null, // schemaBundleRef
        null, // promptBundleRef
        null, // graphProfileRef
        null, // selectedEntryIds
        null, // flowProfileRef
        null, // capsuleProfileRef
        null, // inventoryProfile
        LIMITS, // storeLimits
        null, // flowProfile
        null, // capsuleProfile
        null, // materialProfile
        null, // activityProfile
        null, // processDiscoveryProfile
        1, // maxMaterialsToStart
        new CodeReadingMaterialProfile(64_000L, 16)); // readingMaterialProfile
  }

  private static AnalysisRunRequest request(ArtifactPolicyRegistryReference policy) {
    return new AnalysisRunRequest(
        ArtifactId.parse("source-registration:" + "a".repeat(64)),
        reference("frozen-repository-request", 'b'),
        reference("profile-bundle", 'c'),
        reference("resource-budget", 'd'),
        reference("toolchain", 'e'),
        reference("schema-bundle", 'f'),
        reference("prompt-bundle", '1'),
        null,
        new ArtifactReference(policy.artifactId(), policy.sha256()),
        reference("candidate-series", '2'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static CodeReadingMaterialSet materialSet(
      JavaCodeIndex javaIndex,
      VerifiedSourceInventoryReference source,
      ProgramGraphsReference navigation,
      AnalysisStepPublicationReference persistence) {
    JavaCodeIndex.EntryCollection selected = javaIndex.entries().get(0);
    EntryCodeContext context = selected.context();
    CodeReadingMaterialSet.Packet packet =
        new CodeReadingMaterialSet.Packet(
            "packet:markdown-budget",
            List.of(selected.seed()),
            context.methods(),
            context.calls().stream()
                .map(call -> new CodeReadingMaterialSet.EntryCall(selected.seed().entryId(), call))
                .toList(),
            new CodeReadingMaterialSet.PersistenceSelection(
                List.of(), List.of(), List.of(), List.of(), List.of()),
            List.of(
                new CodeReadingMaterialSet.SourceReference(
                    "S1",
                    new CodeReadingMaterialSet.UnitLocation(
                        "METHOD",
                        context.methods().get(0).methodKey(),
                        context.methods().get(0).source().path(),
                        context.methods().get(0).source().startLine(),
                        context.methods().get(0).source().endLine()))),
            List.of(),
            List.of(),
            0L);
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
            CodeReadingMaterialMarkdown.renderPacket(packet)
                .getBytes(StandardCharsets.UTF_8)
                .length);
    List<CodeReadingMaterialSet.EntryCoverage> coverage = new ArrayList<>();
    coverage.add(
        new CodeReadingMaterialSet.EntryCoverage(
            selected.seed().entryId(),
            List.of(packet.packetId()),
            CodeReadingMaterialSet.CoverageStatus.COLLECTED,
            List.of()));
    javaIndex.entries().stream()
        .skip(1)
        .forEach(
            entry ->
                coverage.add(
                    new CodeReadingMaterialSet.EntryCoverage(
                        entry.seed().entryId(),
                        List.of(),
                        CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED,
                        List.of("NOT_SELECTED_FOR_MARKDOWN_BUDGET_FIXTURE"))));
    return new CodeReadingMaterialSet(
        new CodeReadingMaterialSet.Header(
            source,
            navigation,
            persistence,
            javaIndex.snapshotId(),
            new CodeReadingMaterialProfile(64_000L, 16)),
        List.of(packet),
        coverage);
  }

  private static AnalysisStepPublicationReference cloneStep(
      ReopenedAnalysisStepPublication original,
      AnalysisRunId targetRun,
      List<AnalysisStepPublicationReference> upstream,
      CanonicalModuleArtifactStore sourceModules,
      CanonicalModuleArtifactStore targetModules,
      CanonicalAnalysisStepArtifactStore targetSteps,
      ArtifactControls controls) {
    AnalysisStepPublisherModuleProvenance provenance =
        (AnalysisStepPublisherModuleProvenance) original.receipt().publicationProvenance();
    ReopenedModulePublication originalModule =
        sourceModules.reopen(provenance.publisherSpecificationModuleReference());
    AnalysisStepModuleAddress oldAddress =
        (AnalysisStepModuleAddress) originalModule.reference().address();
    AnalysisStepModuleAddress targetAddress =
        new AnalysisStepModuleAddress(
            targetRun,
            oldAddress.analysisStepKey(),
            oldAddress.moduleNumber(),
            oldAddress.moduleKey());
    var installedModule =
        targetModules.install(
            new ModuleInstallRequest(
                targetAddress,
                originalModule.receipt().moduleVersion(),
                List.of(),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                originalModule.payloads().stream()
                    .map(
                        payload ->
                            new CanonicalModulePayload(
                                payload.descriptor().fileName(),
                                payload.descriptor().artifactType(),
                                payload.descriptor().schemaVersion(),
                                payload.descriptor().artifactId(),
                                payload.descriptor().mediaType(),
                                payload.canonicalUtf8()))
                    .toList()));
    return targetSteps
        .install(
            new AnalysisStepInstallRequest(
                new org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress(
                    targetRun, original.reference().address().analysisStepKey()),
                new AnalysisStepPublisherModuleProvenance(installedModule.reference()),
                upstream,
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                original.semanticPayloads().stream()
                    .map(
                        payload ->
                            new CanonicalAnalysisStepPayload(
                                payload.descriptor().fileName(),
                                payload.descriptor().artifactType(),
                                payload.descriptor().schemaVersion(),
                                payload.descriptor().artifactId(),
                                payload.descriptor().mediaType(),
                                payload.canonicalUtf8()))
                    .toList(),
                null))
        .reference();
  }

  private static JavaCodeSession minimalJavaSession(ProgramGraphsPublicFixture fixture) {
    String snapshot = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return new JavaDeclarationCatalog(
            snapshot, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
      }

      @Override
      public EntryCodeContext collect(EntrySeed entry) {
        String source = "void entry() { }";
        EntryCodeContext.MethodCode method =
            new EntryCodeContext.MethodCode(
                entry.methodKey(),
                "METHOD",
                "example.NeutralController",
                "entry",
                List.of(),
                "void",
                new EntryCodeContext.SourceSource(
                    "src/main/java/com/example/OrderController.java", entry.methodRange(), source),
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
                "MARKDOWN_BUDGET_TEST",
                List.of(),
                List.of(),
                null));
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "markdown-budget-test", Map.of("jdt", "fixture"), "17", List.of());
      }

      @Override
      public void close() {}
    };
  }

  private static ArtifactReference reference(String prefix, char fill) {
    String value = String.valueOf(fill).repeat(64);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + value), new Sha256Digest(value));
  }
}
