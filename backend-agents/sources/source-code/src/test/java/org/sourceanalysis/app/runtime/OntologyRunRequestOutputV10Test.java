package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
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

/** Direct RED contracts for the independent formal ontology request/output wire. */
class OntologyRunRequestOutputV10Test {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  @TempDir Path temporaryDirectory;

  @Test
  void ontologyRequestOwnsOnlyTheOntologyBranchAndPreservesAllExactOperationReferences() {
    SelectedSourceBasis basis = preparedBasis();
    AnalysisStepPublicationReference r4 = evidencePublication('r');

    AnalysisRunRequest prepare =
        AnalysisRunRequest.ontology(
            basis,
            ontologyInputs(
                AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY,
                r4,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null));
    AnalysisRunRequest identify =
        AnalysisRunRequest.ontology(
            basis,
            ontologyInputs(
                AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY,
                r4,
                artifact("ontology-scope", '1'),
                null,
                corpusPublication('2'),
                List.of(),
                List.of(),
                artifact("ontology-prompt", '3'),
                artifact("ontology-model", '4'),
                artifact("ontology-scope", '1'),
                null));
    AnalysisRunRequest relate =
        AnalysisRunRequest.ontology(
            basis,
            ontologyInputs(
                AnalysisRunRequest.OntologyOperation.RELATE_ONTOLOGY,
                r4,
                artifact("ontology-scope", '5'),
                artifact("ontology-selection", '6'),
                corpusPublication('2'),
                List.of(identificationPublication('7')),
                List.of(),
                artifact("ontology-prompt", '8'),
                artifact("ontology-model", '9'),
                artifact("ontology-scope", '5'),
                artifact("ontology-selection", '6')));
    AnalysisRunRequest publish =
        AnalysisRunRequest.ontology(
            basis,
            ontologyInputs(
                AnalysisRunRequest.OntologyOperation.PUBLISH_ONTOLOGY,
                r4,
                artifact("ontology-scope", 'a'),
                artifact("ontology-selection", 'b'),
                corpusPublication('2'),
                List.of(identificationPublication('7')),
                List.of(relationPublication('c')),
                null,
                null,
                artifact("ontology-scope", 'a'),
                artifact("ontology-selection", 'b')));

    assertThat(List.of(prepare, identify, relate, publish))
        .allSatisfy(
            request -> {
              assertThat(request.requestKind()).isEqualTo(AnalysisRunRequest.RequestKind.ONTOLOGY);
              assertThat(request.technicalAnalysisInputs()).isNull();
              assertThat(request.ontologyInputs().evidencePublication()).isEqualTo(r4);
              assertThat(request.selectedSourceBasis()).isEqualTo(basis);
            });
    assertThat(prepare.ontologyInputs().ontologyProfileRef())
        .isEqualTo(artifact("ontology-profile", 'p'));
    assertThat(identify.ontologyInputs().corpusPublication()).isEqualTo(corpusPublication('2'));
    assertThat(relate.ontologyInputs().identificationPublications())
        .containsExactly(identificationPublication('7'));
    assertThat(publish.ontologyInputs().relationPublications())
        .containsExactly(relationPublication('c'));
  }

  @Test
  void ontologyInputRejectsWrongOperationShapeAndWrongR4Owner() {
    AnalysisStepPublicationReference r4 = evidencePublication('r');

    assertThatThrownBy(
            () ->
                ontologyInputs(
                    AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY,
                    r4,
                    artifact("ontology-scope", '1'),
                    null,
                    corpusPublication('2'),
                    List.of(),
                    List.of(),
                    null,
                    null,
                    artifact("ontology-scope", '1'),
                    null))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                ontologyInputs(
                    AnalysisRunRequest.OntologyOperation.RELATE_ONTOLOGY,
                    r4,
                    null,
                    artifact("ontology-selection", '3'),
                    corpusPublication('2'),
                    List.of(),
                    List.of(),
                    artifact("ontology-prompt", '4'),
                    artifact("ontology-model", '5'),
                    null,
                    artifact("ontology-selection", '3')))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                AnalysisRunRequest.ontology(
                    preparedBasis(),
                    ontologyInputs(
                        AnalysisRunRequest.OntologyOperation.PUBLISH_ONTOLOGY,
                        publication(runId('s'), AnalysisStepKey.PROGRAM_GRAPHS, 's'),
                        artifact("ontology-scope", '6'),
                        artifact("ontology-selection", '7'),
                        corpusPublication('2'),
                        List.of(identificationPublication('8')),
                        List.of(relationPublication('9')),
                        null,
                        null,
                        artifact("ontology-scope", '6'),
                        artifact("ontology-selection", '7'))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mixedLegacyAndOntologyBranchesAreRejectedWhileOldConstructionStillWorks() {
    SelectedSourceBasis basis = preparedBasis();
    AnalysisRunRequest.OntologyInputs ontology =
        ontologyInputs(
            AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY,
            evidencePublication('r'),
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            null,
            null,
            null);

    assertThatThrownBy(
            () ->
                new AnalysisRunRequest(
                    AnalysisRunRequest.RequestKind.ONTOLOGY,
                    legacyInputs(),
                    null,
                    null,
                    basis,
                    ontology))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new AnalysisRunRequest(
                    AnalysisRunRequest.RequestKind.ANALYSIS, null, null, null, basis, ontology))
        .isInstanceOf(IllegalArgumentException.class);

    AnalysisRunRequest legacy = legacyRequest();
    assertThat(legacy.requestKind()).isEqualTo(AnalysisRunRequest.RequestKind.ANALYSIS);
    assertThat(legacy.usesLegacyV2Wire()).isTrue();
  }

  @Test
  void ontologyIntentsDoNotReuseTheLegacyUpstreamRunIdChannel() {
    AnalysisRunId run = runId('x');
    for (String name :
        List.of("PREPARE_ONTOLOGY", "IDENTIFY_ONTOLOGY", "RELATE_ONTOLOGY", "PUBLISH_ONTOLOGY")) {
      AnalysisExecutionIntent intent = AnalysisExecutionIntent.valueOf(name);
      assertThat(new AnalysisStepExecutionRequest(run, intent, null, null).intent())
          .isEqualTo(intent);
      assertThatThrownBy(() -> new AnalysisStepExecutionRequest(run, intent, run, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ANALYSIS_EXECUTION_UPSTREAM_NOT_ALLOWED");
    }
  }

  @Test
  void ontologyRequestWritesOnlyV6FieldsAndReopensWithoutLegacyOrTechnicalFields()
      throws Exception {
    Path storeRoot = temporaryDirectory.resolve("ontology-request-v6-store");
    Files.createDirectories(storeRoot);
    AnalysisRunRequest request =
        AnalysisRunRequest.ontology(
            preparedBasis(),
            ontologyInputs(
                AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY,
                evidencePublication('r'),
                artifact("ontology-scope", '1'),
                null,
                corpusPublication('2'),
                List.of(),
                List.of(),
                artifact("ontology-prompt", '3'),
                artifact("ontology-model", '4'),
                artifact("ontology-scope", '1'),
                null));

    AnalysisRunReference queued;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued = RunStoreBootstrap.queueAnalysisRun(store, request);
    }

    ObjectNode wire =
        (ObjectNode)
            JSON.parseCanonical(
                ImmutableBytes.copyOf(Files.readAllBytes(requestPath(storeRoot, queued.runId()))));
    assertThat(wire.path("schemaVersion").textValue()).isEqualTo("analysis-run-request-v6");
    assertThat(wire.path("requestKind").textValue()).isEqualTo("ONTOLOGY");
    assertThat(fields(wire))
        .containsExactlyInAnyOrder(
            "schemaVersion", "requestKind", "selectedSourceBasis", "ontologyInputs");
    ObjectNode ontologyWire = (ObjectNode) wire.path("ontologyInputs");
    assertThat(fields(ontologyWire))
        .containsExactlyInAnyOrder(
            "operation",
            "evidencePublication",
            "ontologyProfileRef",
            "resourceBudgetRef",
            "schemaBundleRef",
            "toolchainRef",
            "artifactPolicyRegistryRef",
            "promptBundleRef",
            "modelBindingRef",
            "ontologyScopeRef",
            "ontologySelectionRef",
            "corpusPublication",
            "identificationPublications",
            "relationPublications");
    assertThat(ontologyWire.path("operation").textValue()).isEqualTo("IDENTIFY_ONTOLOGY");
    assertThat(
            ontologyWire
                .path("evidencePublication")
                .path("address")
                .path("analysisStepKey")
                .textValue())
        .isEqualTo("business-flows");
    assertThat(ontologyWire.path("ontologyScopeRef").path("artifactId").textValue())
        .isEqualTo(artifact("ontology-scope", '1').artifactId().value());
    assertThat(ontologyWire.path("ontologySelectionRef").isNull()).isTrue();
    assertThat(ontologyWire.path("promptBundleRef").path("artifactId").textValue())
        .isEqualTo(artifact("ontology-prompt", '3').artifactId().value());
    assertThat(ontologyWire.path("modelBindingRef").path("artifactId").textValue())
        .isEqualTo(artifact("ontology-model", '4').artifactId().value());
    assertThat(
            ontologyWire.path("corpusPublication").path("address").path("moduleNumber").intValue())
        .isEqualTo(2);
    assertThat(ontologyWire.path("identificationPublications")).isEmpty();
    assertThat(ontologyWire.path("relationPublications")).isEmpty();
    assertThat(wire.has("frozenRepositoryRequestRef")).isFalse();
    assertThat(wire.has("technicalAnalysisInputs")).isFalse();

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      PersistedAnalysisRunRequest reopened =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queued.runId());
      assertThat(reopened.request()).isEqualTo(request);
      assertThat(reopened.request().ontologyInputs().evidencePublication())
          .isEqualTo(evidencePublication('r'));
    }
  }

  @Test
  void legacyV2RequestStillWritesItsExactHistoricalFieldSetWithoutOntologyFields()
      throws Exception {
    Path storeRoot = temporaryDirectory.resolve("legacy-request-v2-isolation-store");
    Files.createDirectories(storeRoot);
    AnalysisRunReference queued;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued = RunStoreBootstrap.queueAnalysisRun(store, legacyRequest());
    }

    ObjectNode wire =
        (ObjectNode)
            JSON.parseCanonical(
                ImmutableBytes.copyOf(Files.readAllBytes(requestPath(storeRoot, queued.runId()))));
    assertThat(wire.path("schemaVersion").textValue()).isEqualTo("analysis-run-request-v2");
    assertThat(fields(wire))
        .containsExactlyInAnyOrder(
            "schemaVersion",
            "sourceRegistrationId",
            "frozenRepositoryRequestRef",
            "profileBundleRef",
            "resourceBudgetRef",
            "toolchainRef",
            "schemaBundleRef",
            "promptBundleRef",
            "organizationRegistrySeedRef",
            "artifactPolicyRegistryRef",
            "candidateSeriesRef",
            "readerCandidateRound",
            "parentCandidateRef",
            "approvedFindingRefs");
    assertThat(wire.has("requestKind")).isFalse();
    assertThat(wire.has("ontologyInputs")).isFalse();
    assertThat(wire.has("technicalAnalysisInputs")).isFalse();
  }

  @Test
  void ontologyOutputIsItsOwnV10BranchAndRoundTripsExactOwnerAndPublication() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("ontology-output-v10-store");
    Files.createDirectories(storeRoot);
    SelectedSourceBasis basis = preparedBasis();
    AnalysisRunRequest request =
        AnalysisRunRequest.ontology(
            basis,
            ontologyInputs(
                AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY,
                evidencePublication('r'),
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null));

    AnalysisRunReference queued;
    OntologyRunOutput expected;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued = RunStoreBootstrap.queueAnalysisRun(store, request);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      expected =
          new OntologyRunOutput(
              AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY,
              queued.runId(),
              basis,
              evidencePublication('r'),
              corpusPublication(queued.runId(), '2'));
    }
    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      RunStoreBootstrap.recordAnalysisRunOutput(
          store, queued.runId(), AnalysisRunOutput.ontology(expected));
    }

    ObjectNode wire =
        (ObjectNode)
            JSON.parseCanonical(
                ImmutableBytes.copyOf(Files.readAllBytes(outputPath(storeRoot, queued.runId()))));
    assertThat(wire.path("schemaVersion").textValue()).isEqualTo("analysis-run-output-v10");
    assertThat(wire.path("outputKind").textValue()).isEqualTo("ONTOLOGY");
    assertThat(wire.has("knowledgeCheckpoint")).isFalse();
    assertThat(wire.has("technicalOutput")).isFalse();

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      AnalysisRunOutput reopened =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, queued.runId()).orElseThrow();
      assertThat(reopened.ontologyOutput()).isEqualTo(expected);
      assertThat(reopened.businessMaterialCheckpoint()).isNull();
      assertThat(reopened.knowledgeCheckpoint()).isNull();
      assertThat(reopened.technicalOutput()).isNull();
    }
  }

  private static AnalysisRunRequest.OntologyInputs ontologyInputs(
      AnalysisRunRequest.OntologyOperation operation,
      AnalysisStepPublicationReference evidence,
      ArtifactReference scope,
      ArtifactReference selection,
      ModulePublicationReference corpus,
      List<ModulePublicationReference> identification,
      List<ModulePublicationReference> relation,
      ArtifactReference prompt,
      ArtifactReference model,
      ArtifactReference scopeAlias,
      ArtifactReference selectionAlias) {
    return new AnalysisRunRequest.OntologyInputs(
        operation,
        evidence,
        artifact("ontology-profile", 'p'),
        artifact("resource-budget", 'q'),
        artifact("schema-bundle", 's'),
        artifact("toolchain", 't'),
        artifact("artifact-policy-registry", 'u'),
        prompt,
        model,
        scope == null ? scopeAlias : scope,
        selection == null ? selectionAlias : selection,
        corpus,
        identification,
        relation);
  }

  private static AnalysisRunRequest legacyRequest() {
    return new AnalysisRunRequest(
        artifactId("source-registration", 'a'),
        artifact("frozen-repository-request", 'b'),
        artifact("profile-bundle", 'c'),
        artifact("resource-budget", 'd'),
        artifact("toolchain", 'e'),
        artifact("schema-bundle", 'f'),
        artifact("prompt-bundle", '1'),
        null,
        artifact("artifact-policy-registry", '2'),
        artifact("candidate-series", '3'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static AnalysisRunRequest.AnalysisInputs legacyInputs() {
    return legacyRequest().analysisInputs();
  }

  private static SelectedSourceBasis preparedBasis() {
    AnalysisStepPublicationReference publication =
        publication(runId('a'), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'b');
    ArtifactReference schema = artifact("schema-bundle", 'e');
    ArtifactReference policy = artifact("artifact-policy-registry", 'f');
    ArtifactId snapshot = artifactId("snapshot", '1');
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.PREPARED_V1,
        new PreparedSourceReference(
            snapshot,
            publication,
            schema,
            new ArtifactPolicyRegistryReference(policy.artifactId(), policy.sha256())),
        null,
        snapshot,
        digest('2'));
  }

  private static AnalysisStepPublicationReference evidencePublication(char identity) {
    return publication(runId(identity), AnalysisStepKey.BUSINESS_FLOWS, identity);
  }

  private static ModulePublicationReference corpusPublication(char identity) {
    return corpusPublication(runId(identity), identity);
  }

  private static ModulePublicationReference corpusPublication(AnalysisRunId owner, char identity) {
    return modulePublication(owner, 2, "ontology-corpus", identity);
  }

  private static ModulePublicationReference identificationPublication(char identity) {
    return modulePublication(runId(identity), 3, "ontology-identification", identity);
  }

  private static ModulePublicationReference relationPublication(char identity) {
    return modulePublication(runId(identity), 4, "ontology-relations", identity);
  }

  private static ModulePublicationReference modulePublication(
      AnalysisRunId owner, int number, String key, char identity) {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(owner, AnalysisStepKey.REPOSITORY_KNOWLEDGE, number, key),
        ModuleArtifactRoot.parse("module-root:" + identityHex(identity)),
        ModuleReceiptId.parse("module-receipt:" + identityHex(identity)),
        digest(identity));
  }

  private static AnalysisStepPublicationReference publication(
      AnalysisRunId run, AnalysisStepKey step, char identity) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(run, step),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + identityHex(identity)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + identityHex(identity)),
        digest(identity));
  }

  private static ArtifactReference artifact(String prefix, char identity) {
    return new ArtifactReference(artifactId(prefix, identity), digest(identity));
  }

  private static ArtifactId artifactId(String prefix, char identity) {
    return ArtifactId.parse(prefix + ":" + identityHex(identity));
  }

  private static AnalysisRunId runId(char identity) {
    return AnalysisRunId.parse("analysis-run:" + identityHex(identity));
  }

  private static Sha256Digest digest(char identity) {
    return new Sha256Digest(identityHex(identity));
  }

  private static String identityHex(char identity) {
    if (Character.digit(identity, 16) >= 0) {
      return String.valueOf(identity).repeat(64);
    }
    return String.format("%02x", (int) identity).repeat(32);
  }

  private static Path requestPath(Path storeRoot, AnalysisRunId runId) {
    return storeRoot.resolve("analysis-runs").resolve(runId.value()).resolve("run-request.json");
  }

  private static Path outputPath(Path storeRoot, AnalysisRunId runId) {
    return storeRoot.resolve("analysis-runs").resolve(runId.value()).resolve("run-output.json");
  }

  private static Set<String> fields(ObjectNode value) {
    Set<String> names = new HashSet<>();
    value.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
