package org.sourceanalysis.app.adapter.cli;

import static org.sourceanalysis.app.adapter.cli.OntologySavedTaskContract.requireV2IdentificationTaskRange;
import static org.sourceanalysis.app.adapter.cli.OntologySavedTaskContract.requireV2TaskOutcomeReason;
import static org.sourceanalysis.app.adapter.cli.OntologySavedTaskContract.requireV2TaskOutcomes;
import static org.sourceanalysis.app.adapter.cli.OntologySavedTaskContract.requiredText;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.sourceanalysis.app.adapter.cli.OntologySavedTaskContract.TaskRecord;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.inventory.PreparedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidencePublisher;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyBusinessOverviewRenderer;
import org.sourceanalysis.app.analysis.ontology.OntologyCallBudgetProvider;
import org.sourceanalysis.app.analysis.ontology.OntologyDecisionRunner;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus;
import org.sourceanalysis.app.analysis.ontology.OntologyJobResultStore;
import org.sourceanalysis.app.analysis.ontology.OntologyReadingCoordinator;
import org.sourceanalysis.app.analysis.ontology.OntologyReadingPacket;
import org.sourceanalysis.app.analysis.ontology.OntologySchemaEvidence;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader;
import org.sourceanalysis.app.analysis.ontology.OntologyScopedAssembler;
import org.sourceanalysis.app.analysis.ontology.OntologySelectionReader;
import org.sourceanalysis.app.analysis.ontology.OntologyTaskOutcome;
import org.sourceanalysis.app.analysis.ontology.OntologyTaskRunner;
import org.sourceanalysis.app.analysis.ontology.OntologyTypedTaskRunner;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreException;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.runtime.AnalysisExecutionIntent;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.AnalysisStepExecutionRequest;
import org.sourceanalysis.app.runtime.ArtifactQuery;
import org.sourceanalysis.app.runtime.ArtifactView;
import org.sourceanalysis.app.runtime.LocalRepositoryAnalysisAgent;
import org.sourceanalysis.app.runtime.OntologyArtifactQueryKey;
import org.sourceanalysis.app.runtime.OntologyRunOutput;
import org.sourceanalysis.app.runtime.RepositoryAnalysisRunCoordinator;
import org.sourceanalysis.app.runtime.RunInspection;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.SelectedSourceBasisProjector;
import org.sourceanalysis.app.runtime.TechnicalRunOutput;

/** The ontology branch of the unique configured source-analysis dispatcher. */
final class OntologyAnalysisConfiguredRuntime {

  private static final String CONFIG_SCHEMA = "ontology-config-v1";
  private static final List<String> OPERATIONS =
      List.of("prepare-ontology", "identify-ontology", "relate-ontology", "publish-ontology");
  private static final ArtifactStoreLimits STORE_LIMITS =
      new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096);
  private static final String PROJECTION_RULE_V1 = "ontology-model-projection-v1";
  private static final String PROJECTION_RULE_V2 = "ontology-model-projection-v2";
  private static final String PROJECTION_RULE_V3 = "ontology-model-projection-v3";

  private OntologyAnalysisConfiguredRuntime() {}

  static boolean isOntologyOperation(String operation) {
    return OPERATIONS.contains(operation);
  }

  /**
   * Storage-only schema recognition preserves saved-query routing after a Prompt file disappears.
   */
  static boolean handles(Path configurationPath) {
    try {
      ObjectNode configuration =
          SourceAnalysisExecution.readConfiguration(configurationPath, new CanonicalJsonCodec());
      JsonNode schema = configuration.get("schemaVersion");
      return schema != null && schema.isTextual() && CONFIG_SCHEMA.equals(schema.textValue());
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  static int execute(
      Path configurationPath,
      String operation,
      List<String> options,
      PrintWriter output,
      PrintWriter errors,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory) {
    Objects.requireNonNull(configurationPath, "ontology configuration path");
    Objects.requireNonNull(operation, "ontology operation");
    Objects.requireNonNull(options, "ontology command options");
    Objects.requireNonNull(output, "ontology configured output");
    Objects.requireNonNull(errors, "ontology configured errors");
    try {
      Invocation invocation = Invocation.parse(operation, options);
      if (invocation.isQuery()) {
        return query(configurationPath, invocation, output);
      }
      OntologyConfiguration configuration = OntologyConfiguration.load(configurationPath);
      if (usesOntologyPayloadV3(
          SourceAnalysisExecution.loadPolicies(
              configuration.storage().ontologyPolicyRegistry(), new CanonicalJsonCodec()),
          "ONTOLOGY_IDENTIFICATION")) {
        configuration = configuration.forTypedV4();
      }
      return switch (invocation.operation()) {
        case "prepare-ontology" -> prepare(configuration, invocation, output);
        case "identify-ontology" -> identify(configuration, invocation, output, providerFactory);
        case "relate-ontology" -> relate(configuration, invocation, output, providerFactory);
        case "publish-ontology" -> publish(configuration, invocation, output);
        default -> throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
      };
    } catch (IllegalStateException failure) {
      errors.println("SOURCE_ANALYSIS_FAILED:" + failure.getMessage());
      errors.flush();
      return 2;
    } catch (RuntimeException failure) {
      String code = failure.getMessage();
      errors.println(
          "SOURCE_ANALYSIS_FAILED:"
              + (code != null && (code.startsWith("ONTOLOGY_") || code.contains("TOO_LARGE"))
                  ? code
                  : "ONTOLOGY_CONFIGURATION_INVALID"));
      errors.flush();
      return 2;
    }
  }

  private static int prepare(
      OntologyConfiguration configuration, Invocation invocation, PrintWriter output) {
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.storage().root())) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry ontologyPolicies =
          SourceAnalysisExecution.loadPolicies(
              configuration.storage().ontologyPolicyRegistry(), json);
      AdmittedEvidence admitted =
          admitEvidence(configuration, store, json, invocation.evidenceRunId());
      // A configured DDL path is an O0 input gate: it must already be one of the verified-text
      // documents admitted from the exact R0 basis. Unsupported SQL stays an honest post-queue
      // schema report, but absent, excluded, and non-text paths must not create a receipt.
      OntologySchemaEvidence.requireVerifiedTextMembers(
          admitted.sourceTexts(), configuration.schemaSources());
      AnalysisRunRequest request = prepareRequest(configuration, ontologyPolicies, admitted);
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              execution ->
                  executePrepare(execution, store, configuration, ontologyPolicies, admitted));
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunReference queued = agent.start(request);
      try {
        AnalysisRunReference completed =
            agent.executeStep(
                new AnalysisStepExecutionRequest(
                    queued.runId(), AnalysisExecutionIntent.PREPARE_ONTOLOGY, null, null));
        writeRunObservation(output, store, configuration.storage(), completed.runId(), json);
        output.flush();
        return 0;
      } catch (RuntimeException failedPostQueue) {
        writeRunObservation(output, store, configuration.storage(), queued.runId(), json);
        output.flush();
        return 2;
      }
    }
  }

  private static int identify(
      OntologyConfiguration configuration,
      Invocation invocation,
      PrintWriter output,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory) {
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.storage().root())) {
      AnalysisRunRequest corpusRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, invocation.runId()).request();
      AnalysisRunOutput corpusOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, invocation.runId())
              .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_CORPUS_RUN_INVALID"));
      if (corpusRequest.requestKind() != AnalysisRunRequest.RequestKind.ONTOLOGY
          || corpusRequest.ontologyInputs().operation()
              != AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY
          || corpusOutput.ontologyOutput() == null) {
        throw new IllegalArgumentException("ONTOLOGY_CORPUS_RUN_INVALID");
      }
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry ontologyPolicies =
          SourceAnalysisExecution.loadPolicies(
              configuration.storage().ontologyPolicyRegistry(), json);
      CanonicalArtifactPolicyRegistry corpusOwnerPolicies =
          exactSavedOntologyPolicies(
              configuration.storage(),
              corpusRequest.ontologyInputs().artifactPolicyRegistryRef(),
              json);
      AdmittedEvidence admitted =
          admitEvidence(
              configuration,
              store,
              json,
              corpusRequest.ontologyInputs().evidencePublication().address().runId());
      SavedCorpus savedCorpus =
          verifySavedCorpus(
              store,
              corpusOwnerPolicies,
              configuration,
              corpusRequest,
              corpusOutput.ontologyOutput().ontologyPublication(),
              admitted);
      OntologySavedTaskContract.requireCorpusProductionVersion(
          usesOntologyPayloadV3(ontologyPolicies, "ONTOLOGY_IDENTIFICATION"),
          usesFormalPacketV5(savedCorpus));
      ObjectNode scopeSnapshot = readCanonical(invocation.scope(), json);
      OntologyScopeReader.Scope scope =
          OntologyScopeReader.read(scopeSnapshot, savedCorpus.admitted().corpus());
      if ("ontology-scope-v2".equals(scope.schemaVersion()) && !usesFormalPacketV5(savedCorpus)) {
        throw new IllegalArgumentException("ONTOLOGY_SCOPE_CORPUS_VERSION_INVALID");
      }
      List<String> externalRunIds = scopeObjectRuns(scope);
      List<org.sourceanalysis.app.artifact.ModulePublicationReference> externalPublications =
          selectedStages(
              store, externalRunIds, AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY);
      SelectedFormalStage externalObjects =
          selectedFormalStage(
              configuration,
              store,
              configuration.storage(),
              savedCorpus,
              new OntologyStage(corpusRequest, corpusOutput),
              externalRunIds);
      for (OntologyScopeReader.Question question : scope.questions()) {
        if (!question.objectSources().isEmpty()
            && externalObjectInput(question, externalObjects).failure() != null
            && externalObjectInput(question, externalObjects).failure().category()
                == OntologyTaskOutcome.Category.SOURCE) {
          throw new IllegalArgumentException("ONTOLOGY_OBJECT_SOURCE_INVALID");
        }
      }
      ArtifactReference scopeReference =
          SourceAnalysisExecution.contentReference(
              "ontology-scope", json.encodeCanonical(scopeSnapshot));
      ArtifactReference promptReference = promptBundleReference(configuration, json);
      ModelBinding model =
          modelBinding(configuration, AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY);
      ModelBinding discoveryModel =
          scope.mode() == OntologyScopeReader.Mode.DISCOVERY
              ? modelBinding(configuration, "survey")
              : null;
      validateProviderBeforeQueue(model, providerFactory);
      if (discoveryModel != null) {
        validateProviderBeforeQueue(discoveryModel, providerFactory);
      }
      preflightIdentify(
          configuration,
          savedCorpus,
          scope,
          model,
          usesOntologyPayloadV2(ontologyPolicies, "ONTOLOGY_IDENTIFICATION"));
      AnalysisRunRequest request =
          identifyRequest(
              configuration,
              ontologyPolicies,
              admitted,
              corpusOutput,
              scopeReference,
              promptReference,
              model.reference(),
              externalPublications);
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              execution ->
                  executeIdentify(
                      execution,
                      store,
                      configuration,
                      ontologyPolicies,
                      savedCorpus,
                      scope,
                      scopeSnapshot,
                      model,
                      discoveryModel,
                      externalObjects,
                      providerFactory));
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunReference queued = agent.start(request);
      try {
        AnalysisRunReference completed =
            agent.executeStep(
                new AnalysisStepExecutionRequest(
                    queued.runId(), AnalysisExecutionIntent.IDENTIFY_ONTOLOGY, null, null));
        writeRunObservation(
            output, store, configuration.storage(), completed.runId(), new CanonicalJsonCodec());
        output.flush();
        return completed.lifecycleState() == AnalysisRunLifecycleState.FAILED ? 2 : 0;
      } catch (RuntimeException failedPublicInstall) {
        writeRunObservation(
            output, store, configuration.storage(), queued.runId(), new CanonicalJsonCodec());
        output.flush();
        return 2;
      }
    }
  }

  private static SavedCorpus verifySavedCorpus(
      RunStoreHandle store,
      CanonicalArtifactPolicyRegistry policies,
      OntologyConfiguration configuration,
      AnalysisRunRequest corpusRequest,
      org.sourceanalysis.app.artifact.ModulePublicationReference publication,
      AdmittedEvidence admitted) {
    ReopenedModulePublication reopened =
        new FileSystemCanonicalModuleArtifactStore(
                store, new CanonicalJsonCodec(), policies, STORE_LIMITS)
            .reopen(publication);
    List<String> fileNames =
        reopened.payloads().stream()
            .map(payload -> payload.descriptor().fileName())
            .sorted()
            .toList();
    VerifiedCanonicalPayload corpus =
        reopened.payloads().stream()
            .filter(
                payload ->
                    "ontology-corpus.json".equals(payload.descriptor().fileName())
                        && "ONTOLOGY_CORPUS".equals(payload.descriptor().artifactType())
                        && Set.of("ontology-corpus-v1", "ontology-corpus-v2")
                            .contains(payload.descriptor().schemaVersion()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID"));
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    JsonNode parsed = json.parseCanonical(corpus.canonicalUtf8());
    if (!(parsed instanceof ObjectNode saved)) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
    }
    String projectionRuleVersion = saved.path("projectionRuleVersion").asText();
    if (!(PROJECTION_RULE_V3.equals(projectionRuleVersion)
        ? "ontology-corpus-v2".equals(corpus.descriptor().schemaVersion())
            && "v2".equals(reopened.receipt().moduleVersion())
        : "ontology-corpus-v1".equals(corpus.descriptor().schemaVersion())
            && "v1".equals(reopened.receipt().moduleVersion()))) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
    }
    AdmittedEvidence restored =
        restoreSavedProjection(
            configuration,
            store,
            json,
            projectionRuleVersion,
            restoreSavedSchemaEvidence(admitted, saved, reopened, fileNames, json));
    if (corpusRequest.requestKind() != AnalysisRunRequest.RequestKind.ONTOLOGY
        || corpusRequest.ontologyInputs().operation()
            != AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY
        || !ontologyControls(corpusRequest).equals(reopened.receipt().controls())
        || !strictReferences(restored.evidencePayloadReferences())
            .equals(reopened.receipt().upstreamArtifacts())
        || !restored.corpus().sourceIdentity().equals(saved.path("contentSourceIdentity").asText())
        || !restored
            .evidencePublication()
            .address()
            .runId()
            .value()
            .equals(saved.path("evidenceRunId").asText())
        || !json.parseCanonical(restored.corpus().aliases().canonicalMapping())
            .equals(saved.path("aliases"))
        || !sourceBasisNode(restored.sourceBasis()).equals(saved.path("selectedSourceBasis"))
        || !analysisStepPublicationNode(restored.evidencePublication())
            .equals(saved.path("evidencePublication"))
        || !preparationBindings(restored.technicalInputs())
            .equals(saved.path("preparationBindings"))
        || !validProjectionRuleVersion(projectionRuleVersion)) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
    }
    OntologyConfiguration.Reading reading = preparationControls(saved.path("preparationControls"));
    String identity = saved.path("corpusIdentity").asText();
    if (identity.isBlank() || !identity.equals(formalCorpusIdentity(saved, json))) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
    }
    return new SavedCorpus(
        restored,
        identity,
        projectionRuleVersion,
        reading,
        new ArtifactReference(corpus.descriptor().artifactId(), corpus.descriptor().sha256()));
  }

  /**
   * Reopens only the saved O0 DDL payload; current configuration never re-parses or substitutes it.
   */
  private static AdmittedEvidence restoreSavedSchemaEvidence(
      AdmittedEvidence admitted,
      ObjectNode savedCorpus,
      ReopenedModulePublication reopened,
      List<String> fileNames,
      CanonicalJsonCodec json) {
    JsonNode state = savedCorpus.path("schemaEvidence");
    String status = state.path("status").asText();
    // Earlier one-file O0 bytes predate the explicit disabled marker and remain strict one-file
    // publications; they cannot gain a schema unit merely because a later config names one.
    if (status.isBlank()) {
      if (!fileNames.equals(List.of("ontology-corpus.json"))) {
        throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
      }
      return admitted;
    }
    if ("DISABLED".equals(status)) {
      if (!fileNames.equals(List.of("ontology-corpus.json"))) {
        throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
      }
      return admitted;
    }
    if (!"CONFIGURED".equals(status)
        || !fileNames.equals(List.of("ontology-corpus.json", "schema-evidence.json"))) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
    }
    VerifiedCanonicalPayload schema =
        reopened.payloads().stream()
            .filter(
                payload ->
                    "schema-evidence.json".equals(payload.descriptor().fileName())
                        && "SCHEMA_EVIDENCE".equals(payload.descriptor().artifactType())
                        && OntologySchemaEvidence.SCHEMA_VERSION.equals(
                            payload.descriptor().schemaVersion()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID"));
    ArtifactReference reference =
        new ArtifactReference(schema.descriptor().artifactId(), schema.descriptor().sha256());
    if (!artifactReferenceNode(reference).equals(state.path("reference"))) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
    }
    JsonNode savedEvidence = json.parseCanonical(schema.canonicalUtf8());
    OntologyEvidenceCorpus augmented =
        admitted.corpus().withSchemaEvidence(savedEvidence, admitted.sourceTexts());
    return admitted.withCorpus(augmented);
  }

  /**
   * Rebuilds a saved O0 corpus by its own explicit projection rule, never by the current target.
   */
  private static AdmittedEvidence restoreSavedProjection(
      OntologyConfiguration configuration,
      RunStoreHandle store,
      CanonicalJsonCodec json,
      String projectionRuleVersion,
      AdmittedEvidence admitted) {
    if (PROJECTION_RULE_V1.equals(projectionRuleVersion)) {
      return admitted;
    }
    if (!PROJECTION_RULE_V2.equals(projectionRuleVersion)
        && !PROJECTION_RULE_V3.equals(projectionRuleVersion)) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
    }
    List<JavaDeclarationCatalog.MethodDeclarationView> declarations =
        savedR2MethodDeclarations(configuration, store, json, admitted);
    OntologyEvidenceCorpus rebuilt =
        admitted.corpus().withPreparedSourceBodies(admitted.sourceTexts(), declarations);
    return admitted.withCorpus(
        PROJECTION_RULE_V3.equals(projectionRuleVersion)
            ? rebuilt.withBusinessLinkNavigation()
            : rebuilt);
  }

  /**
   * Reopens the exact saved backend index already bound by R4's R3 predecessor. This reads the
   * saved Java index only; it does not reopen a checkout or invoke a Java engine.
   */
  private static List<JavaDeclarationCatalog.MethodDeclarationView> savedR2MethodDeclarations(
      OntologyConfiguration configuration,
      RunStoreHandle store,
      CanonicalJsonCodec json,
      AdmittedEvidence admitted) {
    try {
      AnalysisRunRequest.TechnicalAnalysisInputs r4 = admitted.technicalInputs();
      AnalysisStepPublicationReference r3Persistence = r4.upstreamPublication();
      if (r3Persistence == null || r3Persistence.address() == null) {
        throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
      }
      AnalysisRunId r3RunId = r3Persistence.address().runId();
      AnalysisRunRequest r3Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, r3RunId).request();
      AnalysisRunOutput r3Output =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, r3RunId)
              .orElseThrow(
                  () -> new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID"));
      TechnicalRunOutput r3 = r3Output.technicalOutput();
      if (r3Request.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
          || r3Request.technicalAnalysisInputs().operation()
              != AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE
          || r3 == null
          || r3.operation() != AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE
          || !admitted.sourceBasis().equals(r3Request.selectedSourceBasis())
          || !admitted.sourceBasis().equals(r3.selectedSourceBasis())
          || !r3Persistence.equals(r3.persistence())) {
        throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
      }
      AnalysisStepPublicationReference r2Navigation =
          r3Request.technicalAnalysisInputs().upstreamPublication();
      if (r2Navigation == null
          || r2Navigation.address() == null
          || !r2Navigation.equals(r3.navigation())) {
        throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
      }
      AnalysisRunId r2RunId = r2Navigation.address().runId();
      AnalysisRunRequest r2Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, r2RunId).request();
      AnalysisRunOutput r2Output =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, r2RunId)
              .orElseThrow(
                  () -> new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID"));
      TechnicalRunOutput r2 = r2Output.technicalOutput();
      if (r2Request.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
          || r2Request.technicalAnalysisInputs().operation()
              != AnalysisRunRequest.TechnicalOperation.COLLECT_CODE
          || r2 == null
          || r2.operation() != AnalysisRunRequest.TechnicalOperation.COLLECT_CODE
          || !admitted.sourceBasis().equals(r2Request.selectedSourceBasis())
          || !admitted.sourceBasis().equals(r2.selectedSourceBasis())
          || !r2Navigation.equals(r2.navigation())) {
        throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
      }
      CanonicalArtifactPolicyRegistry policies =
          exactSavedArtifactPolicies(
              configuration.storage().evidencePolicyRegistry(),
              configuration.storage().upstreamArtifactPolicyRegistries(),
              r2Request.technicalAnalysisInputs().artifactPolicyRegistryRef(),
              json);
      if (!r2Request
          .technicalAnalysisInputs()
          .artifactPolicyRegistryRef()
          .equals(SourceAnalysisExecution.artifactReference(policies.reference()))) {
        throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
      }
      FileSystemCanonicalAnalysisStepArtifactStore r2Steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              store,
              json,
              policies,
              TechnicalAnalysisConfiguredRuntime.technicalPublicationStoreLimits());
      var reopenedR2 = r2Steps.reopen(r2Navigation);
      if (!r2Navigation.equals(reopenedR2.reference())
          || !technicalControls(r2Request).equals(reopenedR2.receipt().controls())) {
        throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
      }
      var index =
          new JavaCodeIndexReader(r2Steps)
              .reopen(new ProgramGraphsReference(r2Navigation), reopenedR2);
      if (!admitted.sourceTexts().snapshotId().equals(index.snapshotId())
          || !admitted.sourceTexts().snapshotId().equals(index.catalog().snapshotId())) {
        throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
      }
      return index.catalog().methods();
    } catch (IllegalArgumentException invalid) {
      if ("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID".equals(invalid.getMessage())) {
        throw invalid;
      }
      throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID", invalid);
    } catch (RuntimeException unavailable) {
      throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID", unavailable);
    }
  }

  private static String projectionRuleVersion(CanonicalArtifactPolicyRegistry policies) {
    if (usesOntologyPayloadV3(policies, "ONTOLOGY_IDENTIFICATION")) {
      if (!hasOntologyPolicy(policies, "ONTOLOGY_CORPUS", "ontology-corpus-v2")) {
        throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_POLICY_INVALID");
      }
      return PROJECTION_RULE_V3;
    }
    return usesOntologyPayloadV2(policies, "ONTOLOGY_IDENTIFICATION")
        ? PROJECTION_RULE_V2
        : PROJECTION_RULE_V1;
  }

  private static boolean validProjectionRuleVersion(String value) {
    return PROJECTION_RULE_V1.equals(value)
        || PROJECTION_RULE_V2.equals(value)
        || PROJECTION_RULE_V3.equals(value);
  }

  /** The private reading packet family is selected only by the verified, saved O0 rule. */
  private static boolean usesFormalPacketV4(SavedCorpus savedCorpus) {
    return PROJECTION_RULE_V2.equals(savedCorpus.projectionRuleVersion());
  }

  private static boolean usesFormalPacketV5(SavedCorpus savedCorpus) {
    return PROJECTION_RULE_V3.equals(savedCorpus.projectionRuleVersion());
  }

  private static OntologyConfiguration.Reading phaseReading(
      OntologyConfiguration configuration, SavedCorpus savedCorpus) {
    return usesFormalPacketV5(savedCorpus) ? configuration.reading() : savedCorpus.reading();
  }

  private static OntologyDecisionRunner.FormalReadingMaterial readingMaterial(
      OntologyConfiguration configuration, SavedCorpus corpus) {
    return usesFormalPacketV5(corpus)
        ? OntologyDecisionRunner.formalReadingMaterialV4(
            configuration.prompts().get("reading"), configuration.reading().maxOutputTokens())
        : OntologyDecisionRunner.formalReadingMaterial(
            configuration.prompts().get("reading"), configuration.reading().maxOutputTokens());
  }

  private static OntologyReadingPacket withClueContext(
      SavedCorpus corpus, OntologyReadingPacket packet, List<String> clues) {
    return usesFormalPacketV5(corpus)
        ? packet.withVisibleClues(corpus.admitted().corpus(), clues)
        : packet;
  }

  private static void preflightIdentify(
      OntologyConfiguration configuration,
      SavedCorpus savedCorpus,
      OntologyScopeReader.Scope scope,
      ModelBinding model,
      boolean taskOutcomeV2) {
    OntologyConfiguration.Reading activeReading = phaseReading(configuration, savedCorpus);
    if (scope.mode() == OntologyScopeReader.Mode.DISCOVERY) {
      new OntologyDecisionRunner(
              null,
              configuration.reading().maxRequestBytes(),
              configuration.reading().maxOutputBytes())
          .prepareFormalSurvey(
              savedCorpus.admitted().corpus(),
              configuration.reading().maxNavigationEntries(),
              OntologyDecisionRunner.formalSurveyMaterial(
                  configuration.prompts().get("survey"),
                  configuration.reading().maxOutputTokens()));
      return;
    }
    OntologyTypedTaskRunner.FormalCorpusBinding binding =
        new OntologyTypedTaskRunner.FormalCorpusBinding(
            savedCorpus.corpusIdentity(), savedCorpus.admitted().corpus().sourceIdentity());
    for (OntologyScopeReader.Question question : scope.questions()) {
      for (OntologyScopeReader.Task task : question.tasks()) {
        if (task.readingMode() == OntologyScopeReader.ReadingMode.MODEL) {
          // The first model-reading envelope depends on the model's actual reading response.
          // formalReadingCheck prepares and bounds that envelope before it initializes its
          // provider.
          continue;
        }
        OntologyReadingCoordinator coordinator =
            new OntologyReadingCoordinator(
                savedCorpus.admitted().corpus(),
                request -> {
                  throw new IllegalStateException("ONTOLOGY_READING_PROVIDER_NOT_ALLOWED");
                },
                activeReading.maxReadingRounds(),
                activeReading.maxActionsPerRound(),
                activeReading.maxUnitBytes(),
                activeReading.maxRequestBytes(),
                activeReading.maxNavigationEntries(),
                usesFormalPacketV4(savedCorpus));
        OntologyReadingCoordinator.FormalResult reading =
            coordinator.completeFormal(scope, question.questionId(), task.taskId());
        if (reading.frozenPacket() == null) {
          if (taskOutcomeV2) {
            // The v2 runner records this reading/material disposition per declared task after
            // queueing. Keep preflight provider-free without converting it into a run-wide gate.
            continue;
          }
          throw new IllegalArgumentException(reading.issueCode());
        }
        if (task.taskKind() != OntologyScopeReader.TaskKind.OBJECT) {
          continue;
        }
        OntologyTypedTaskRunner.prepareFormal(
            new OntologyTypedTaskRunner.FormalTask(
                binding,
                question.questionId(),
                task.taskId(),
                OntologyTaskRunner.TaskKind.valueOf(task.taskKind().name()),
                question.question(),
                withClueContext(
                    savedCorpus, reading.frozenPacket(), reading.state().selectedClues()),
                List.of(),
                new OntologyTypedTaskRunner.FormalPromptSnapshot(
                    promptFor(configuration, task.taskKind()),
                    configuration.prompts().get("review")),
                new OntologyTypedTaskRunner.FormalLimits(
                    configuration.reading().maxRequestBytes(),
                    configuration.reading().maxOutputBytes(),
                    configuration.reading().maxOutputTokens()),
                model.declaration(),
                OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION,
                null,
                usesFormalPacketV5(savedCorpus) ? reading.state().selectedClues() : List.of()));
      }
    }
  }

  private static int relate(
      OntologyConfiguration configuration,
      Invocation invocation,
      PrintWriter output,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory) {
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.storage().root())) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      ObjectNode selectionSnapshot = readCanonical(invocation.selection(), json);
      SelectedCorpus selectedCorpus =
          selectionCorpus(configuration, store, json, selectionSnapshot);
      OntologySelectionReader.Selection selection =
          OntologySelectionReader.read(
              selectionSnapshot, selectedCorpus.saved().admitted().corpus());
      if (selection.operation() != OntologySelectionReader.Operation.RELATE) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTION_OPERATION_INVALID");
      }
      OntologyStage corpus = selectedCorpus.stage();
      SavedCorpus savedCorpus = selectedCorpus.saved();
      AdmittedEvidence admitted = savedCorpus.admitted();
      List<org.sourceanalysis.app.artifact.ModulePublicationReference> identifications =
          selectedStages(
              store,
              selection.identificationRuns(),
              AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY);
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(
              configuration.storage().ontologyPolicyRegistry(), json);
      if (usesOntologyPayloadV2(policies, "ONTOLOGY_RELATIONS")
          && !selection.isV2()
          && !selection.questions().isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTION_VERSION_INVALID");
      }
      OntologySavedTaskContract.requireCorpusProductionVersion(
          usesOntologyPayloadV3(policies, "ONTOLOGY_IDENTIFICATION"),
          usesFormalPacketV5(savedCorpus));
      SelectedFormalStage selectedIdentifications =
          selectedFormalStage(
              configuration,
              store,
              configuration.storage(),
              savedCorpus,
              corpus,
              selection.identificationRuns());
      List<OntologyTypedTaskRunner.FormalResult> reviewedObjects =
          selectedIdentifications.results().stream()
              .map(SelectedFormalResult::result)
              .filter(result -> result.kind() == OntologyTaskRunner.TaskKind.OBJECT)
              .toList();
      if (!selection.questions().isEmpty() && !selection.isV2() && reviewedObjects.isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_OBJECT_REQUIRED");
      }
      ModelBinding model =
          selection.questions().isEmpty()
              ? null
              : modelBinding(configuration, AnalysisRunRequest.OntologyOperation.RELATE_ONTOLOGY);
      if (model != null) {
        validateProviderBeforeQueue(model, providerFactory);
      }
      ArtifactReference selectionRef =
          SourceAnalysisExecution.contentReference(
              "ontology-selection", json.encodeCanonical(selectionSnapshot));
      ArtifactReference promptReference = promptBundleReference(configuration, json);
      ArtifactReference notUsed = notUsedModelReference(json);
      AnalysisRunRequest request =
          ontologyRequest(
              AnalysisRunRequest.OntologyOperation.RELATE_ONTOLOGY,
              configuration,
              policies,
              admitted,
              promptReference,
              model == null ? notUsed : model.reference(),
              selectionRef,
              corpus.output().ontologyOutput().ontologyPublication(),
              identifications,
              List.of());
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              execution ->
                  executeRelate(
                      execution,
                      store,
                      configuration,
                      policies,
                      request,
                      savedCorpus,
                      selection,
                      selectionSnapshot,
                      selectedIdentifications,
                      model,
                      providerFactory));
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunReference queued = agent.start(request);
      try {
        AnalysisRunReference completed =
            agent.executeStep(
                new AnalysisStepExecutionRequest(
                    queued.runId(), AnalysisExecutionIntent.RELATE_ONTOLOGY, null, null));
        writeRunObservation(output, store, configuration.storage(), completed.runId(), json);
        output.flush();
        return completed.lifecycleState() == AnalysisRunLifecycleState.FAILED ? 2 : 0;
      } catch (RuntimeException failedPostQueue) {
        writeRunObservation(output, store, configuration.storage(), queued.runId(), json);
        output.flush();
        return 2;
      }
    }
  }

  private static int publish(
      OntologyConfiguration configuration, Invocation invocation, PrintWriter output) {
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.storage().root())) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      ObjectNode selectionSnapshot = readCanonical(invocation.selection(), json);
      SelectedCorpus selectedCorpus =
          selectionCorpus(configuration, store, json, selectionSnapshot);
      OntologySelectionReader.Selection selection =
          OntologySelectionReader.read(
              selectionSnapshot, selectedCorpus.saved().admitted().corpus());
      if (selection.operation() != OntologySelectionReader.Operation.PUBLISH) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTION_OPERATION_INVALID");
      }
      OntologyStage corpus = selectedCorpus.stage();
      SavedCorpus savedCorpus = selectedCorpus.saved();
      AdmittedEvidence admitted = savedCorpus.admitted();
      List<org.sourceanalysis.app.artifact.ModulePublicationReference> identifications =
          selectedStages(
              store,
              selection.identificationRuns(),
              AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY);
      List<org.sourceanalysis.app.artifact.ModulePublicationReference> relations =
          selectedStages(
              store,
              selection.relationRuns(),
              AnalysisRunRequest.OntologyOperation.RELATE_ONTOLOGY);
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(
              configuration.storage().ontologyPolicyRegistry(), json);
      OntologySavedTaskContract.requireCorpusProductionVersion(
          usesOntologyPayloadV3(policies, "ONTOLOGY_IDENTIFICATION"),
          usesFormalPacketV5(savedCorpus));
      SelectedFormalStage selectedIdentifications =
          selectedFormalStage(
              configuration,
              store,
              configuration.storage(),
              savedCorpus,
              corpus,
              selection.identificationRuns());
      requirePublicationObjectClosure(store, identifications);
      SelectedFormalStage selectedRelations =
          selectedRelationStage(
              configuration,
              store,
              configuration.storage(),
              savedCorpus,
              corpus,
              identifications,
              selectedIdentifications,
              selection.relationRuns(),
              selection.isV2());
      List<SelectedFormalResult> selected = new ArrayList<>(selectedIdentifications.results());
      selected.addAll(selectedRelations.results());
      List<SelectedTaskOutcome> selectedTaskOutcomes =
          new ArrayList<>(selectedIdentifications.taskOutcomes());
      selectedTaskOutcomes.addAll(selectedRelations.taskOutcomes());
      List<SelectedTaskDisposition> selectedDispositions =
          new ArrayList<>(selectedIdentifications.dispositions());
      selectedDispositions.addAll(selectedRelations.dispositions());
      List<SelectedFormalResult> assemblyResults = assemblyFormalResults(selected);
      OntologyScopedAssembler.FormalInput assemblyInput =
          new OntologyScopedAssembler.FormalInput(
              new OntologyTypedTaskRunner.FormalCorpusBinding(
                  savedCorpus.corpusIdentity(), admitted.corpus().sourceIdentity()),
              assemblyResults.stream().map(SelectedFormalResult::result).toList(),
              selectedFormalCoverage(admitted.corpus(), selected, selectedDispositions));
      OntologyScopedAssembler.BusinessFormalInput businessInput =
          usesFormalPacketV5(savedCorpus)
              ? publicationBusinessInput(
                  assemblyInput, selected, selectedDispositions, selectedTaskOutcomes)
              : null;
      ArtifactReference selectionRef =
          SourceAnalysisExecution.contentReference(
              "ontology-selection", json.encodeCanonical(selectionSnapshot));
      AnalysisRunRequest request =
          ontologyRequest(
              AnalysisRunRequest.OntologyOperation.PUBLISH_ONTOLOGY,
              configuration,
              policies,
              admitted,
              null,
              null,
              selectionRef,
              corpus.output().ontologyOutput().ontologyPublication(),
              identifications,
              relations);
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              execution ->
                  executePublish(
                      execution,
                      store,
                      configuration.storage(),
                      policies,
                      request,
                      savedCorpus,
                      selectionSnapshot,
                      assemblyInput,
                      businessInput,
                      selection.isV2(),
                      selectedTaskOutcomes));
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunReference queued = agent.start(request);
      try {
        AnalysisRunReference completed =
            agent.executeStep(
                new AnalysisStepExecutionRequest(
                    queued.runId(), AnalysisExecutionIntent.PUBLISH_ONTOLOGY, null, null));
        writeRunObservation(output, store, configuration.storage(), completed.runId(), json);
        output.flush();
        return completed.lifecycleState() == AnalysisRunLifecycleState.FAILED ? 2 : 0;
      } catch (RuntimeException failedPostQueue) {
        writeRunObservation(output, store, configuration.storage(), queued.runId(), json);
        output.flush();
        return 2;
      }
    }
  }

  private static void requirePublicationObjectClosure(
      RunStoreHandle store,
      List<org.sourceanalysis.app.artifact.ModulePublicationReference> identifications) {
    for (org.sourceanalysis.app.artifact.ModulePublicationReference selected : identifications) {
      AnalysisRunRequest owner =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, selected.address().runId())
              .request();
      if (!identifications.containsAll(owner.ontologyInputs().identificationPublications())) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
  }

  private static AnalysisRunRequest identifyRequest(
      OntologyConfiguration configuration,
      CanonicalArtifactPolicyRegistry ontologyPolicies,
      AdmittedEvidence admitted,
      AnalysisRunOutput corpusOutput,
      ArtifactReference scopeReference,
      ArtifactReference promptReference,
      ArtifactReference modelReference,
      List<org.sourceanalysis.app.artifact.ModulePublicationReference> externalPublications) {
    AnalysisRunRequest.TechnicalAnalysisInputs technical = admitted.technicalInputs();
    return AnalysisRunRequest.ontology(
        admitted.sourceBasis(),
        new AnalysisRunRequest.OntologyInputs(
            AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY,
            admitted.evidencePublication(),
            SourceAnalysisExecution.contentReference(
                "ontology-profile", configuration.canonicalConfiguration()),
            technical.resourceBudgetRef(),
            technical.schemaBundleRef(),
            technical.toolchainRef(),
            SourceAnalysisExecution.artifactReference(ontologyPolicies.reference()),
            promptReference,
            modelReference,
            scopeReference,
            null,
            corpusOutput.ontologyOutput().ontologyPublication(),
            externalPublications,
            List.of()));
  }

  private static SelectedCorpus selectionCorpus(
      OntologyConfiguration configuration,
      RunStoreHandle store,
      CanonicalJsonCodec json,
      JsonNode selection) {
    JsonNode corpusRun = selection.path("corpusRun");
    if (!corpusRun.isTextual()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTION_REQUIRED_FIELD");
    }
    OntologyStage corpus =
        ontologyStage(
            store,
            AnalysisRunId.parse(corpusRun.asText()),
            AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY);
    CanonicalArtifactPolicyRegistry policies =
        exactSavedOntologyPolicies(
            configuration.storage(),
            corpus.request().ontologyInputs().artifactPolicyRegistryRef(),
            json);
    AdmittedEvidence admitted =
        admitEvidence(
            configuration,
            store,
            json,
            corpus.request().ontologyInputs().evidencePublication().address().runId());
    SavedCorpus saved =
        verifySavedCorpus(
            store,
            policies,
            configuration,
            corpus.request(),
            corpus.output().ontologyOutput().ontologyPublication(),
            admitted);
    return new SelectedCorpus(corpus, saved);
  }

  private static OntologyStage ontologyStage(
      RunStoreHandle store, AnalysisRunId runId, AnalysisRunRequest.OntologyOperation operation) {
    AnalysisRunRequest request =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, runId).request();
    AnalysisRunOutput output =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, runId)
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID"));
    if (request.requestKind() != AnalysisRunRequest.RequestKind.ONTOLOGY
        || request.ontologyInputs().operation() != operation
        || output.ontologyOutput() == null
        || output.ontologyOutput().operation() != operation) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    return new OntologyStage(request, output);
  }

  private static List<org.sourceanalysis.app.artifact.ModulePublicationReference> selectedStages(
      RunStoreHandle store, List<String> runIds, AnalysisRunRequest.OntologyOperation operation) {
    return runIds.stream()
        .map(
            runId ->
                ontologyStage(store, AnalysisRunId.parse(runId), operation)
                    .output()
                    .ontologyOutput()
                    .ontologyPublication())
        .toList();
  }

  private static SelectedFormalStage selectedFormalStage(
      OntologyConfiguration configuration,
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      SavedCorpus savedCorpus,
      OntologyStage corpus,
      List<String> runIds) {
    return selectedFormalStage(
        configuration, store, storage, savedCorpus, corpus, runIds, new HashSet<>());
  }

  private static SelectedFormalStage selectedFormalStage(
      OntologyConfiguration configuration,
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      SavedCorpus savedCorpus,
      OntologyStage corpus,
      List<String> runIds,
      Set<String> openingRuns) {
    AdmittedEvidence admitted = savedCorpus.admitted();
    List<SelectedFormalResult> results = new ArrayList<>();
    List<SelectedTaskDisposition> dispositions = new ArrayList<>();
    List<SelectedTaskOutcome> taskOutcomes = new ArrayList<>();
    for (String runId : runIds) {
      if (!openingRuns.add(runId))
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      AnalysisRunId selectedRunId = AnalysisRunId.parse(runId);
      OntologyStage stage =
          ontologyStage(
              store, selectedRunId, AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY);
      AnalysisRunRequest request = stage.request();
      if (!request.selectedSourceBasis().equals(admitted.sourceBasis())
          || !request.ontologyInputs().evidencePublication().equals(admitted.evidencePublication())
          || !request
              .ontologyInputs()
              .corpusPublication()
              .equals(corpus.output().ontologyOutput().ontologyPublication())) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      FormalStageMembership memberships =
          identificationMemberships(
              store,
              storage,
              request,
              stage.output().ontologyOutput().ontologyPublication(),
              admitted.corpus());
      List<String> externalRunIds =
          request.ontologyInputs().identificationPublications().stream()
              .map(publication -> publication.address().runId().value())
              .sorted()
              .toList();
      OntologyScopeReader.Scope savedScope =
          OntologyScopeReader.read(memberships.stageSnapshot(), admitted.corpus());
      if (!externalRunIds.equals(scopeObjectRuns(savedScope))) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      SelectedFormalStage external =
          selectedFormalStage(
              configuration, store, storage, savedCorpus, corpus, externalRunIds, openingRuns);
      Map<String, Map<OntologyTypedTaskRunner.FormalIdentity, OntologyTypedTaskRunner.FormalResult>>
          externalByQuestion = new LinkedHashMap<>();
      for (OntologyScopeReader.Question question : savedScope.questions()) {
        RelationTaskInput input = externalObjectInput(question, external);
        Map<OntologyTypedTaskRunner.FormalIdentity, OntologyTypedTaskRunner.FormalResult> catalog =
            new LinkedHashMap<>();
        if (input.failure() == null) {
          input.catalog().forEach(result -> catalog.put(result.identity(), result));
        }
        externalByQuestion.put(question.questionId(), Map.copyOf(catalog));
      }
      OntologyJobResultStore jobs =
          new OntologyJobResultStore(ontologyJournal(configuration), selectedRunId);
      Map<String, OntologyTypedTaskRunner.FormalResult> reopened = new LinkedHashMap<>();
      Set<String> reopening = new HashSet<>();
      for (String producingTaskId : memberships.reviewed().keySet().stream().sorted().toList()) {
        OntologyTypedTaskRunner.FormalResult result =
            reopenSelectedFormalResult(
                admitted.corpus(),
                jobs,
                memberships.reviewed(),
                reopened,
                reopening,
                producingTaskId,
                externalByQuestion);
        if (!savedCorpus.corpusIdentity().equals(result.identity().corpusIdentity())) {
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        }
        results.add(
            new SelectedFormalResult(
                selectedRunId.value(),
                memberships.reviewed().get(producingTaskId).taskId(),
                result));
      }
      memberships
          .dispositions()
          .forEach(
              disposition ->
                  dispositions.add(
                      selectedTaskDisposition(selectedRunId.value(), memberships, disposition)));
      memberships
          .taskOutcomes()
          .forEach(
              (taskId, outcome) ->
                  taskOutcomes.add(
                      new SelectedTaskOutcome(selectedRunId.value(), taskId, outcome)));
      openingRuns.remove(runId);
    }
    return new SelectedFormalStage(results, dispositions, taskOutcomes);
  }

  private static FormalStageMembership identificationMemberships(
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      AnalysisRunRequest request,
      org.sourceanalysis.app.artifact.ModulePublicationReference publication,
      OntologyEvidenceCorpus corpus) {
    return formalMemberships(
        store,
        storage,
        request,
        publication,
        "ontology-identification.json",
        "ONTOLOGY_IDENTIFICATION",
        Set.of(
            "ontology-identification-v1",
            "ontology-identification-v2",
            "ontology-identification-v3"),
        "scope",
        corpus);
  }

  private static FormalStageMembership relationMemberships(
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      AnalysisRunRequest request,
      org.sourceanalysis.app.artifact.ModulePublicationReference publication,
      OntologyEvidenceCorpus corpus) {
    return formalMemberships(
        store,
        storage,
        request,
        publication,
        "ontology-relations.json",
        "ONTOLOGY_RELATIONS",
        Set.of("ontology-relations-v1", "ontology-relations-v2", "ontology-relations-v3"),
        "selection",
        corpus);
  }

  private static FormalStageMembership formalMemberships(
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      AnalysisRunRequest request,
      org.sourceanalysis.app.artifact.ModulePublicationReference publication,
      String fileName,
      String artifactType,
      Set<String> schemaVersions,
      String manifestField,
      OntologyEvidenceCorpus corpus) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry ownerPolicies =
        exactSavedOntologyPolicies(
            storage, request.ontologyInputs().artifactPolicyRegistryRef(), json);
    ReopenedModulePublication reopened =
        new FileSystemCanonicalModuleArtifactStore(store, json, ownerPolicies, STORE_LIMITS)
            .reopen(publication);
    VerifiedCanonicalPayload membership =
        reopened.payloads().stream()
            .filter(
                candidate ->
                    fileName.equals(candidate.descriptor().fileName())
                        && artifactType.equals(candidate.descriptor().artifactType())
                        && schemaVersions.contains(candidate.descriptor().schemaVersion()))
            .reduce(
                (first, second) -> {
                  throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
                })
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID"));
    JsonNode document = json.parseCanonical(membership.canonicalUtf8());
    String schemaVersion = membership.descriptor().schemaVersion();
    boolean v2 = schemaVersion.endsWith("-v2") || schemaVersion.endsWith("-v3");
    if (!schemaVersions.contains(schemaVersion)
        || !schemaVersion.equals(document.path("schemaVersion").asText())
        || !document.path("taskRecords").isArray()
        || !document.path("taskDispositions").isArray()
        || (v2 && !document.path("taskOutcomes").isArray())) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    requireStagePayloadClosure(store, storage, reopened, document, request, manifestField);
    Map<String, SavedFormalMembership> reviewed = new LinkedHashMap<>();
    Map<String, TaskRecord> taskRecords = new LinkedHashMap<>();
    for (JsonNode task : document.path("taskRecords")) {
      String taskId = task.path("taskId").asText();
      String producingTaskId = task.path("producingTaskId").asText();
      String jobKey = task.path("jobKey").asText();
      String taskKind = task.path("taskKind").asText();
      String status = task.path("status").asText();
      if (taskId.isBlank()
          || producingTaskId.isBlank()
          || jobKey.isBlank()
          || !Set.of("OBJECT", "ACTION", "ANALYTIC", "RELATE").contains(taskKind)
          || (!"REVIEWED".equals(status)
              && !"REJECTED".equals(status)
              && (!v2 || !"UNPROCESSED".equals(status)))
          || taskRecords.putIfAbsent(
                  taskId, new TaskRecord(producingTaskId, jobKey, taskKind, status))
              != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      if ("REVIEWED".equals(status)
          && reviewed.putIfAbsent(producingTaskId, new SavedFormalMembership(taskId, jobKey))
              != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
    Map<String, JsonNode> taskOutcomes =
        v2 ? requireV2TaskOutcomes(document.path("taskOutcomes"), taskRecords) : Map.of();
    if (v2 && "scope".equals(manifestField)) {
      requireV2IdentificationTaskRange(document, corpus, taskOutcomes);
    }
    List<OntologyScopedAssembler.TaskDisposition> dispositions = new ArrayList<>();
    Set<String> dispositionTasks = new HashSet<>();
    for (JsonNode disposition : document.path("taskDispositions")) {
      String taskId = disposition.path("taskId").asText();
      JsonNode rawProducer = disposition.get("producingTaskId");
      String producingTaskId =
          rawProducer == null || rawProducer.isNull() ? null : rawProducer.asText();
      String reason = disposition.path("reason").asText();
      OntologyScopedAssembler.TaskDispositionStatus status;
      try {
        status =
            OntologyScopedAssembler.TaskDispositionStatus.valueOf(
                disposition.path("status").asText());
      } catch (IllegalArgumentException invalid) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      if (taskId.isBlank() || reason.isBlank() || !dispositionTasks.add(taskId)) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      TaskRecord taskRecord = taskRecords.get(taskId);
      if (status == OntologyScopedAssembler.TaskDispositionStatus.REVIEWED) {
        SavedFormalMembership stageMembership = reviewed.get(producingTaskId);
        if (producingTaskId == null
            || stageMembership == null
            || !stageMembership.taskId().equals(taskId)
            || taskRecord == null
            || !"REVIEWED".equals(taskRecord.status())
            || !producingTaskId.equals(taskRecord.producingTaskId())) {
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        }
      } else if (status == OntologyScopedAssembler.TaskDispositionStatus.REJECTED) {
        if (producingTaskId == null
            || taskRecord == null
            || !"REJECTED".equals(taskRecord.status())
            || !producingTaskId.equals(taskRecord.producingTaskId())) {
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        }
      } else if (status == OntologyScopedAssembler.TaskDispositionStatus.UNPROCESSED) {
        if (producingTaskId != null
            || (taskRecord != null && (!v2 || !"UNPROCESSED".equals(taskRecord.status())))) {
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        }
      } else if (producingTaskId == null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      dispositions.add(
          new OntologyScopedAssembler.TaskDisposition(taskId, producingTaskId, status, reason));
    }
    if (dispositionTasks.size() < taskRecords.size()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    return new FormalStageMembership(
        reviewed,
        dispositions,
        taskOutcomes,
        document.path(manifestField).deepCopy(),
        "ontology-relations-v3".equals(schemaVersion)
            ? OntologyRelationReadingSelections.read(document, corpus)
            : Map.of());
  }

  private static void requireStagePayloadClosure(
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      ReopenedModulePublication reopened,
      JsonNode document,
      AnalysisRunRequest request,
      String manifestField) {
    if (!ontologyControls(request).equals(reopened.receipt().controls())
        || !expectedUpstreamPayloadReferences(store, storage, request)
            .equals(reopened.receipt().upstreamArtifacts())) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    requireSemanticUpstreams(document, request);
    if ("scope".equals(manifestField)) {
      requireScopeSnapshot(document.path(manifestField), request);
    } else if ("selection".equals(manifestField)) {
      requireSelectionSnapshot(document.path(manifestField), request);
    } else {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
  }

  private static List<ArtifactReference> expectedUpstreamPayloadReferences(
      RunStoreHandle store, OntologyConfiguration.Storage storage, AnalysisRunRequest request) {
    AnalysisRunRequest.OntologyInputs input = request.ontologyInputs();
    return switch (input.operation()) {
      case IDENTIFY_ONTOLOGY ->
          upstreamPayloadReferences(
              store,
              storage,
              concatenatePublications(
                  List.of(input.corpusPublication()), input.identificationPublications()));
      case RELATE_ONTOLOGY ->
          upstreamPayloadReferences(store, storage, input.identificationPublications());
      case PUBLISH_ONTOLOGY ->
          upstreamPayloadReferences(
              store,
              storage,
              concatenatePublications(
                  input.identificationPublications(), input.relationPublications()));
      default -> throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    };
  }

  private static OntologyTypedTaskRunner.FormalResult reopenSelectedFormalResult(
      OntologyEvidenceCorpus corpus,
      OntologyJobResultStore jobs,
      Map<String, SavedFormalMembership> memberships,
      Map<String, OntologyTypedTaskRunner.FormalResult> reopened,
      Set<String> reopening,
      String producingTaskId,
      Map<String, Map<OntologyTypedTaskRunner.FormalIdentity, OntologyTypedTaskRunner.FormalResult>>
          externalByQuestion) {
    OntologyTypedTaskRunner.FormalResult existing = reopened.get(producingTaskId);
    if (existing != null) {
      return existing;
    }
    SavedFormalMembership membership = memberships.get(producingTaskId);
    if (membership == null || !reopening.add(producingTaskId)) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    try {
      List<OntologyTypedTaskRunner.FormalResult> prior = new ArrayList<>();
      for (OntologyTypedTaskRunner.FormalIdentity priorIdentity :
          jobs.formalCatalogIdentities(membership.jobKey(), producingTaskId)) {
        OntologyTypedTaskRunner.FormalResult previous =
            memberships.containsKey(priorIdentity.producingTaskId())
                ? reopenSelectedFormalResult(
                    corpus,
                    jobs,
                    memberships,
                    reopened,
                    reopening,
                    priorIdentity.producingTaskId(),
                    externalByQuestion)
                : externalByQuestion
                    .getOrDefault(
                        jobs.readFormalMembership(membership.jobKey()).questionId(), Map.of())
                    .get(priorIdentity);
        if (previous == null || !previous.identity().equals(priorIdentity)) {
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        }
        prior.add(previous);
      }
      OntologyTypedTaskRunner.FormalResult restored =
          jobs.reopenFormalCompleted(corpus, membership.jobKey(), producingTaskId, prior);
      reopened.put(producingTaskId, restored);
      return restored;
    } finally {
      reopening.remove(producingTaskId);
    }
  }

  private static OntologyReadingPacket relationPacket(
      OntologyConfiguration configuration,
      OntologyEvidenceCorpus corpus,
      OntologySelectionReader.Question question,
      SavedCorpus savedCorpus) {
    Set<OntologyScopeReader.UnitUse> active = new LinkedHashSet<>(question.unitUses());
    if (!active.containsAll(question.requiredUnitUses())) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTION_REQUIRED_UNIT_UNREAD");
    }
    List<OntologyEvidenceCorpus.UnitHandle> selected = new ArrayList<>();
    OntologyEvidenceCorpus.AliasCatalog aliases = corpus.aliases();
    for (OntologyScopeReader.UnitUse use : active) {
      OntologyEvidenceCorpus.UnitHandle canonical = aliases.unit(use.unitRef());
      OntologyEvidenceCorpus.EntrySummary entry = aliases.entry(use.entryRef());
      OntologyEvidenceCorpus.UnitHandle actual =
          new OntologyEvidenceCorpus.UnitHandle(
              entry.entryId(), canonical.kind(), canonical.originalId());
      if (!aliases.unitUses(use.unitRef()).contains(actual)
          || !use.unitRef().equals(aliases.unitRef(actual))) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTION_REFERENCE_INVALID");
      }
      selected.add(actual);
    }
    if (usesFormalPacketV5(savedCorpus)) {
      return OntologyReadingPacket.formalV5(
              corpus, selected, configuration.reading().maxUnitBytes())
          .withVisibleClues(corpus, question.clueRefs());
    }
    return usesFormalPacketV4(savedCorpus)
        ? OntologyReadingPacket.formalV4(corpus, selected, configuration.reading().maxUnitBytes())
        : OntologyReadingPacket.formal(corpus, selected, configuration.reading().maxUnitBytes());
  }

  private static SelectedFormalStage selectedRelationStage(
      OntologyConfiguration configuration,
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      SavedCorpus savedCorpus,
      OntologyStage corpus,
      List<org.sourceanalysis.app.artifact.ModulePublicationReference> identifications,
      SelectedFormalStage selectedIdentifications,
      List<String> runIds,
      boolean v2Selection) {
    AdmittedEvidence admitted = savedCorpus.admitted();
    Map<SelectedFormalIdentityKey, OntologyTypedTaskRunner.FormalResult> priorByOwnerIdentity =
        selectedFormalResultsByOwnerIdentity(selectedIdentifications.results());
    Map<String, OntologyTypedTaskRunner.FormalResult> historicalPriorByProducer =
        v2Selection ? Map.of() : historicalResultsByProducer(selectedIdentifications.results());
    List<SelectedFormalResult> results = new ArrayList<>();
    List<SelectedTaskDisposition> dispositions = new ArrayList<>();
    List<SelectedTaskOutcome> taskOutcomes = new ArrayList<>();
    for (String runId : runIds) {
      AnalysisRunId selectedRunId = AnalysisRunId.parse(runId);
      OntologyStage stage =
          ontologyStage(store, selectedRunId, AnalysisRunRequest.OntologyOperation.RELATE_ONTOLOGY);
      AnalysisRunRequest request = stage.request();
      if (!request.selectedSourceBasis().equals(admitted.sourceBasis())
          || !request.ontologyInputs().evidencePublication().equals(admitted.evidencePublication())
          || !request
              .ontologyInputs()
              .corpusPublication()
              .equals(corpus.output().ontologyOutput().ontologyPublication())
          || (v2Selection
              ? !identifications.containsAll(request.ontologyInputs().identificationPublications())
              : !request.ontologyInputs().identificationPublications().equals(identifications))) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      FormalStageMembership memberships =
          relationMemberships(
              store,
              storage,
              request,
              stage.output().ontologyOutput().ontologyPublication(),
              admitted.corpus());
      Map<String, List<OntologySelectionReader.ObjectSource>> sourcesByRelationTask =
          v2Selection
              ? selectedObjectSourcesByRelationTask(memberships.stageSnapshot(), admitted.corpus())
              : Map.of();
      OntologyJobResultStore jobs =
          new OntologyJobResultStore(ontologyJournal(configuration), selectedRunId);
      for (String producingTaskId : memberships.reviewed().keySet().stream().sorted().toList()) {
        SavedFormalMembership membership = memberships.reviewed().get(producingTaskId);
        List<OntologyTypedTaskRunner.FormalResult> prior = new ArrayList<>();
        if (v2Selection) {
          for (OntologyTypedTaskRunner.FormalIdentity priorIdentity :
              jobs.formalCatalogIdentities(membership.jobKey(), producingTaskId)) {
            OntologyTypedTaskRunner.FormalResult previous =
                selectedPriorForRelationTask(
                    sourcesByRelationTask,
                    membership.taskId(),
                    selectedIdentifications.taskOutcomes(),
                    priorByOwnerIdentity,
                    priorIdentity);
            if (previous == null) {
              throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
            }
            prior.add(previous);
          }
        } else {
          for (String priorTaskId :
              jobs.formalCatalogProducers(membership.jobKey(), producingTaskId)) {
            OntologyTypedTaskRunner.FormalResult previous =
                historicalPriorByProducer.get(priorTaskId);
            if (previous == null) {
              throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
            }
            prior.add(previous);
          }
        }
        OntologyTypedTaskRunner.FormalResult restored =
            jobs.reopenFormalCompleted(
                admitted.corpus(), membership.jobKey(), producingTaskId, prior);
        if (restored.kind() != OntologyTaskRunner.TaskKind.RELATE
            || !savedCorpus.corpusIdentity().equals(restored.identity().corpusIdentity())) {
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        }
        if (!memberships.readingSelections().isEmpty()) {
          OntologyRelationReadingSelections.requireReviewedClues(
              memberships.readingSelections().get(membership.taskId()), restored.visibleClueRefs());
        }
        results.add(new SelectedFormalResult(selectedRunId.value(), membership.taskId(), restored));
      }
      memberships
          .dispositions()
          .forEach(
              disposition ->
                  dispositions.add(
                      selectedTaskDisposition(selectedRunId.value(), memberships, disposition)));
      memberships
          .taskOutcomes()
          .forEach(
              (taskId, outcome) ->
                  taskOutcomes.add(
                      new SelectedTaskOutcome(selectedRunId.value(), taskId, outcome)));
    }
    return new SelectedFormalStage(results, dispositions, taskOutcomes);
  }

  private static Map<SelectedFormalResultKey, OntologyTypedTaskRunner.FormalResult>
      selectedFormalResultsByOwnerProducer(List<SelectedFormalResult> selected) {
    Map<SelectedFormalResultKey, OntologyTypedTaskRunner.FormalResult> results =
        new LinkedHashMap<>();
    for (SelectedFormalResult item : selected) {
      SelectedFormalResultKey key =
          new SelectedFormalResultKey(item.runId(), item.result().identity().producingTaskId());
      if (results.putIfAbsent(key, item.result()) != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
    return Map.copyOf(results);
  }

  private static Map<String, OntologyTypedTaskRunner.FormalResult> historicalResultsByProducer(
      List<SelectedFormalResult> selected) {
    Map<String, OntologyTypedTaskRunner.FormalResult> results = new LinkedHashMap<>();
    for (SelectedFormalResult item : selected) {
      if (results.putIfAbsent(item.result().identity().producingTaskId(), item.result()) != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
    return Map.copyOf(results);
  }

  private static Map<SelectedFormalIdentityKey, OntologyTypedTaskRunner.FormalResult>
      selectedFormalResultsByOwnerIdentity(List<SelectedFormalResult> selected) {
    Map<SelectedFormalIdentityKey, OntologyTypedTaskRunner.FormalResult> results =
        new LinkedHashMap<>();
    for (SelectedFormalResult item : selected) {
      SelectedFormalIdentityKey key =
          new SelectedFormalIdentityKey(item.runId(), item.result().identity());
      if (results.putIfAbsent(key, item.result()) != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
    return Map.copyOf(results);
  }

  private static Map<String, List<OntologySelectionReader.ObjectSource>>
      selectedObjectSourcesByRelationTask(JsonNode savedSelection, OntologyEvidenceCorpus corpus) {
    OntologySelectionReader.Selection selection;
    try {
      selection = OntologySelectionReader.read(savedSelection, corpus);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID", invalid);
    }
    if (selection.operation() != OntologySelectionReader.Operation.RELATE || !selection.isV2()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    Map<String, List<OntologySelectionReader.ObjectSource>> sources = new LinkedHashMap<>();
    for (OntologySelectionReader.Question question : selection.questions()) {
      if (question.objectSources().isEmpty()
          || sources.putIfAbsent(question.taskId(), question.objectSources()) != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
    return Map.copyOf(sources);
  }

  private static OntologyTypedTaskRunner.FormalResult selectedPriorForRelationTask(
      Map<String, List<OntologySelectionReader.ObjectSource>> sourcesByRelationTask,
      String relationTaskId,
      List<SelectedTaskOutcome> selectedTaskOutcomes,
      Map<SelectedFormalIdentityKey, OntologyTypedTaskRunner.FormalResult> priorByOwnerIdentity,
      OntologyTypedTaskRunner.FormalIdentity identity) {
    List<OntologySelectionReader.ObjectSource> sources = sourcesByRelationTask.get(relationTaskId);
    if (sources == null || sources.isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    OntologyTypedTaskRunner.FormalResult selected = null;
    for (OntologySelectionReader.ObjectSource source : sources) {
      if (!matchesSelectedObjectSource(selectedTaskOutcomes, source, identity.producingTaskId())) {
        continue;
      }
      OntologyTypedTaskRunner.FormalResult candidate =
          priorByOwnerIdentity.get(
              new SelectedFormalIdentityKey(source.identificationRun(), identity));
      if (candidate == null) {
        continue;
      }
      if (selected != null && !sameFormalResult(selected, candidate)) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      selected = candidate;
    }
    return selected;
  }

  private static boolean matchesSelectedObjectSource(
      List<SelectedTaskOutcome> selectedTaskOutcomes,
      OntologySelectionReader.ObjectSource source,
      String producingTaskId) {
    return selectedTaskOutcomes.stream()
        .anyMatch(
            outcome ->
                source.identificationRun().equals(outcome.runId())
                    && source.questionId().equals(outcome.outcome().path("questionId").asText())
                    && "OBJECT".equals(outcome.outcome().path("taskKind").asText())
                    && "REVIEWED".equals(outcome.outcome().path("status").asText())
                    && producingTaskId.equals(outcome.outcome().path("producingTaskId").asText()));
  }

  private static OntologyScopedAssembler.ScopedCoverage selectedFormalCoverage(
      OntologyEvidenceCorpus corpus,
      List<SelectedFormalResult> selected,
      List<SelectedTaskDisposition> selectedDispositions) {
    List<OntologyScopedAssembler.ReadingDisposition> reading = new ArrayList<>();
    Map<SelectedFormalResultKey, SelectedFormalResult> selectedByOwnerProducer =
        selectedFormalResultEntries(selected);
    List<SelectedFormalResult> assemblyResults = assemblyFormalResults(selected);
    for (SelectedFormalResult item : assemblyResults) {
      for (OntologyReadingPacket.PackedUnit unit : item.result().packet().units()) {
        for (String entryId : unit.entryUses()) {
          reading.add(
              new OntologyScopedAssembler.ReadingDisposition(
                  item.taskId(),
                  corpus.aliases().entryRef(entryId),
                  item.result().packet().evidenceUnitRef(unit.localRef()),
                  OntologyScopedAssembler.ReadingDispositionStatus.READ,
                  "The exact saved formal packet unit was dispatched to this reviewed task."));
        }
      }
    }
    Set<SelectedFormalResultKey> coveredResults = new HashSet<>();
    Map<AssemblyDispositionKey, OntologyScopedAssembler.TaskDisposition> assemblyDispositions =
        new LinkedHashMap<>();
    for (SelectedTaskDisposition selectedDisposition : selectedDispositions) {
      OntologyScopedAssembler.TaskDisposition disposition = selectedDisposition.disposition();
      if (disposition.status() == OntologyScopedAssembler.TaskDispositionStatus.REVIEWED) {
        SelectedFormalResult selectedResult =
            selectedByOwnerProducer.get(
                new SelectedFormalResultKey(
                    selectedDisposition.runId(), disposition.producingTaskId()));
        if (selectedResult == null
            || !selectedResult.taskId().equals(disposition.taskId())
            || !coveredResults.add(
                new SelectedFormalResultKey(
                    selectedDisposition.runId(), disposition.producingTaskId()))) {
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        }
        disposition =
            new OntologyScopedAssembler.TaskDisposition(
                disposition.taskId(),
                disposition.producingTaskId(),
                disposition.status(),
                disposition.reason(),
                selectedResult.result().identity());
      }
      addAssemblyDisposition(assemblyDispositions, selectedDisposition, disposition);
    }
    if (coveredResults.size() != selectedByOwnerProducer.size()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    return new OntologyScopedAssembler.ScopedCoverage(
        new OntologyScopedAssembler.InputDenominators(
            corpus.navigation(0, Integer.MAX_VALUE).totalEntries(),
            corpus.frontendRequestCount(),
            corpus.schemaSourceCount()),
        reading,
        List.copyOf(assemblyDispositions.values()));
  }

  private static Map<SelectedFormalResultKey, SelectedFormalResult> selectedFormalResultEntries(
      List<SelectedFormalResult> selected) {
    Map<SelectedFormalResultKey, SelectedFormalResult> results = new LinkedHashMap<>();
    for (SelectedFormalResult item : selected) {
      SelectedFormalResultKey key =
          new SelectedFormalResultKey(item.runId(), item.result().identity().producingTaskId());
      if (results.putIfAbsent(key, item) != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
    return Map.copyOf(results);
  }

  private static List<SelectedFormalResult> assemblyFormalResults(
      List<SelectedFormalResult> selected) {
    Map<OntologyTypedTaskRunner.FormalIdentity, SelectedFormalResult> byIdentity =
        new LinkedHashMap<>();
    for (SelectedFormalResult item : selected) {
      OntologyTypedTaskRunner.FormalIdentity identity = item.result().identity();
      SelectedFormalResult existing = byIdentity.putIfAbsent(identity, item);
      if (existing != null && !sameFormalResult(existing.result(), item.result())) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
    return List.copyOf(byIdentity.values());
  }

  private static boolean sameFormalResult(
      OntologyTypedTaskRunner.FormalResult left, OntologyTypedTaskRunner.FormalResult right) {
    return left.identity().equals(right.identity())
        && left.questionId().equals(right.questionId())
        && left.kind() == right.kind()
        && left.packet().sourceIdentity().equals(right.packet().sourceIdentity())
        && left.packet().packetId().equals(right.packet().packetId())
        && left.packet().canonicalInput().equals(right.packet().canonicalInput())
        && left.packet().modelInput().equals(right.packet().modelInput())
        && left.packet().modelProjectionVersion().equals(right.packet().modelProjectionVersion())
        && left.packet().callContextEncoding().equals(right.packet().callContextEncoding())
        && left.rawCandidate().equals(right.rawCandidate())
        && left.review().equals(right.review())
        && left.diagnostics().equals(right.diagnostics())
        && left.catalogMapping().equals(right.catalogMapping())
        && left.extractRuntimeIdentity().equals(right.extractRuntimeIdentity())
        && left.reviewRuntimeIdentity().equals(right.reviewRuntimeIdentity())
        && left.status() == right.status();
  }

  private static void addAssemblyDisposition(
      Map<AssemblyDispositionKey, OntologyScopedAssembler.TaskDisposition> assemblyDispositions,
      SelectedTaskDisposition selected,
      OntologyScopedAssembler.TaskDisposition disposition) {
    AssemblyDispositionKey identity =
        disposition.reviewedResultIdentity() != null
            ? new AssemblyDispositionKey(
                disposition.reviewedResultIdentity(), null, null, null, null)
            : selected.questionId() == null
                ? disposition.producingTaskId() == null
                    ? new AssemblyDispositionKey(null, null, null, disposition.taskId(), null)
                    : new AssemblyDispositionKey(
                        null, null, null, null, disposition.producingTaskId())
                : new AssemblyDispositionKey(
                    null,
                    selected.runId(),
                    selected.questionId(),
                    disposition.taskId(),
                    selected.producingTaskId());
    OntologyScopedAssembler.TaskDisposition existing =
        assemblyDispositions.putIfAbsent(identity, disposition);
    if (existing != null && !existing.equals(disposition)) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
  }

  private static SelectedTaskDisposition selectedTaskDisposition(
      String runId,
      FormalStageMembership memberships,
      OntologyScopedAssembler.TaskDisposition disposition) {
    JsonNode outcome = memberships.taskOutcomes().get(disposition.taskId());
    if (outcome == null) {
      return new SelectedTaskDisposition(
          runId, null, disposition.producingTaskId(), disposition, List.of());
    }
    String questionId = requiredText(outcome, "questionId");
    JsonNode rawProducer = outcome.get("producingTaskId");
    String producingTaskId =
        rawProducer == null || rawProducer.isNull()
            ? null
            : requiredText(outcome, "producingTaskId");
    if (disposition.producingTaskId() != null
        && !disposition.producingTaskId().equals(producingTaskId)) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    List<String> selectedClues = new ArrayList<>();
    if ("RELATE".equals(outcome.path("taskKind").asText())) {
      JsonNode actualReading = memberships.readingSelections().get(disposition.taskId());
      if (actualReading != null) {
        actualReading.path("selectedClues").forEach(clue -> selectedClues.add(clue.asText()));
      } else {
        for (JsonNode question : memberships.stageSnapshot().path("questions")) {
          if (disposition.taskId().equals(question.path("taskId").asText())
              && questionId.equals(question.path("questionId").asText())) {
            question.path("clueRefs").forEach(clue -> selectedClues.add(clue.asText()));
          }
        }
      }
    }
    return new SelectedTaskDisposition(
        runId, questionId, producingTaskId, disposition, selectedClues);
  }

  private static ArtifactReference promptBundleReference(
      OntologyConfiguration configuration, CanonicalJsonCodec json) {
    ObjectNode prompts = JsonNodeFactory.instance.objectNode();
    configuration.prompts().forEach(prompts::put);
    return SourceAnalysisExecution.contentReference(
        "ontology-prompts", json.encodeCanonical(prompts));
  }

  private static void requireScopeSnapshot(JsonNode snapshot, AnalysisRunRequest request) {
    requireManifestReference(
        snapshot, "ontology-scope", request.ontologyInputs().ontologyScopeRef());
  }

  private static void requireSelectionSnapshot(JsonNode snapshot, AnalysisRunRequest request) {
    ArtifactReference expected = request.ontologyInputs().ontologyScopeRef();
    if (!expected.equals(request.ontologyInputs().ontologySelectionRef())) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    requireManifestReference(snapshot, "ontology-selection", expected);
  }

  private static void requireManifestReference(
      JsonNode snapshot, String artifactType, ArtifactReference expected) {
    if (!snapshot.isObject()
        || expected == null
        || !expected.equals(
            SourceAnalysisExecution.contentReference(
                artifactType, new CanonicalJsonCodec().encodeCanonical(snapshot)))) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
  }

  private static void copySemanticUpstreams(ObjectNode document, AnalysisRunRequest request) {
    document.set("semanticUpstreams", semanticUpstreamsDocument(request));
  }

  private static void requireSemanticUpstreams(JsonNode document, AnalysisRunRequest request) {
    if (!semanticUpstreamsDocument(request).equals(document.path("semanticUpstreams"))) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
  }

  private static ObjectNode semanticUpstreamsDocument(AnalysisRunRequest request) {
    if (request.requestKind() != AnalysisRunRequest.RequestKind.ONTOLOGY) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    AnalysisRunRequest.OntologyInputs inputs = request.ontologyInputs();
    ObjectNode upstreams = JsonNodeFactory.instance.objectNode();
    upstreams.set("corpusPublication", modulePublicationDocument(inputs.corpusPublication()));
    ArrayNode identifications = upstreams.putArray("identificationPublications");
    for (org.sourceanalysis.app.artifact.ModulePublicationReference publication :
        inputs.identificationPublications()) {
      identifications.add(modulePublicationDocument(publication));
    }
    ArrayNode relations = upstreams.putArray("relationPublications");
    for (org.sourceanalysis.app.artifact.ModulePublicationReference publication :
        inputs.relationPublications()) {
      relations.add(modulePublicationDocument(publication));
    }
    return upstreams;
  }

  private static ObjectNode modulePublicationDocument(
      org.sourceanalysis.app.artifact.ModulePublicationReference publication) {
    if (publication == null
        || !(publication.address() instanceof AnalysisStepModuleAddress address)) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    ObjectNode addressValue = value.putObject("address");
    addressValue.put("runId", address.runId().value());
    addressValue.put("analysisStepKey", address.analysisStepKey().wireValue());
    addressValue.put("moduleNumber", address.moduleNumber());
    addressValue.put("moduleKey", address.moduleKey());
    value.put("moduleArtifactRoot", publication.moduleArtifactRoot().value());
    value.put("moduleReceiptId", publication.moduleReceiptId().value());
    value.put("moduleReceiptSha256", publication.moduleReceiptSha256().value());
    return value;
  }

  private static ArtifactReference notUsedModelReference(CanonicalJsonCodec json) {
    ObjectNode declaration = JsonNodeFactory.instance.objectNode();
    declaration.put("schemaVersion", "ontology-model-not-used-v1");
    declaration.put("status", "NOT_USED");
    return SourceAnalysisExecution.contentReference(
        "ontology-model-not-used", json.encodeCanonical(declaration));
  }

  private static AnalysisRunRequest ontologyRequest(
      AnalysisRunRequest.OntologyOperation operation,
      OntologyConfiguration configuration,
      CanonicalArtifactPolicyRegistry policies,
      AdmittedEvidence admitted,
      ArtifactReference prompts,
      ArtifactReference model,
      ArtifactReference selection,
      org.sourceanalysis.app.artifact.ModulePublicationReference corpus,
      List<org.sourceanalysis.app.artifact.ModulePublicationReference> identifications,
      List<org.sourceanalysis.app.artifact.ModulePublicationReference> relations) {
    AnalysisRunRequest.TechnicalAnalysisInputs technical = admitted.technicalInputs();
    return AnalysisRunRequest.ontology(
        admitted.sourceBasis(),
        new AnalysisRunRequest.OntologyInputs(
            operation,
            admitted.evidencePublication(),
            SourceAnalysisExecution.contentReference(
                "ontology-profile", configuration.canonicalConfiguration()),
            technical.resourceBudgetRef(),
            technical.schemaBundleRef(),
            technical.toolchainRef(),
            SourceAnalysisExecution.artifactReference(policies.reference()),
            prompts,
            model,
            selection,
            selection,
            corpus,
            identifications,
            relations));
  }

  private static AnalysisRunOutput executeIdentify(
      AnalysisStepExecutionRequest execution,
      RunStoreHandle store,
      OntologyConfiguration configuration,
      CanonicalArtifactPolicyRegistry ontologyPolicies,
      SavedCorpus savedCorpus,
      OntologyScopeReader.Scope scope,
      JsonNode scopeSnapshot,
      ModelBinding model,
      ModelBinding discoveryModel,
      SelectedFormalStage externalObjects,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory) {
    AdmittedEvidence admitted = savedCorpus.admitted();
    if (execution.intent() != AnalysisExecutionIntent.IDENTIFY_ONTOLOGY
        || execution.upstreamRunId() != null
        || execution.exactMaterialId() != null) {
      throw new IllegalArgumentException("ONTOLOGY_EXECUTION_INPUT_INVALID");
    }
    AnalysisRunRequest persisted =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, execution.runId()).request();
    if (persisted.requestKind() != AnalysisRunRequest.RequestKind.ONTOLOGY
        || persisted.ontologyInputs().operation()
            != AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY
        || !persisted.selectedSourceBasis().equals(admitted.sourceBasis())) {
      throw new IllegalArgumentException("ONTOLOGY_EXECUTION_INPUT_INVALID");
    }
    requireScopeSnapshot(scopeSnapshot, persisted);
    OntologyJobResultStore jobs =
        new OntologyJobResultStore(ontologyJournal(configuration), execution.runId());
    List<OntologyTypedTaskRunner.PreparedFormalTask> prepared = new java.util.ArrayList<>();
    List<OntologyTypedTaskRunner.FormalResult> completed = new java.util.ArrayList<>();
    List<OntologyTaskOutcome> outcomes = new ArrayList<>();
    Map<String, OntologyTypedTaskRunner.FormalResult> reviewedByTaskId = new LinkedHashMap<>();
    boolean outcomeContractV2 = usesOntologyPayloadV2(ontologyPolicies, "ONTOLOGY_IDENTIFICATION");
    OntologyCallBudgetProvider.RunBudget budget =
        new OntologyCallBudgetProvider.RunBudget(configuration.reading().maxRequests());
    String problemCode = null;
    String problemTaskId = null;
    String problemStage = null;
    OntologyTaskOutcome.FailureReason problemFailure = null;
    String currentTaskId = null;
    String currentStage = null;
    OntologyTypedTaskRunner.FormalCorpusBinding binding =
        new OntologyTypedTaskRunner.FormalCorpusBinding(
            savedCorpus.corpusIdentity(), admitted.corpus().sourceIdentity());
    OntologyScopeReader.Scope workingScope = scope;
    DiscoveryStage discovery = null;
    OntologyTaskOutcome.FailureReason sharedFailure = null;
    OntologyDecisionRunner questionModelReading =
        scope.mode() == OntologyScopeReader.Mode.QUESTION
                && scope.questions().stream()
                    .flatMap(question -> question.tasks().stream())
                    .anyMatch(task -> task.readingMode() == OntologyScopeReader.ReadingMode.MODEL)
            ? new OntologyDecisionRunner(
                new OntologyCallBudgetProvider(
                    lazyProvider(providerFactory, configuration, model, execution.runId()), budget),
                configuration.reading().maxRequestBytes(),
                configuration.reading().maxOutputBytes(),
                jobs,
                readingMaterial(configuration, savedCorpus))
            : null;
    try {
      if (scope.mode() == OntologyScopeReader.Mode.DISCOVERY) {
        currentStage = "SURVEY";
        discovery =
            executeFormalDiscovery(
                configuration,
                scope,
                admitted.corpus(),
                jobs,
                budget,
                discoveryModel,
                execution.runId(),
                providerFactory);
        workingScope = discovery.selectedScope();
        if (discovery.failureReason() != null) {
          sharedFailure = discovery.failureReason();
          problemCode = sharedFailure.code();
          problemStage = sharedFailure.stage();
          problemFailure = sharedFailure;
        } else if (discovery.failureCode() != null) {
          throw new IllegalArgumentException(discovery.failureCode());
        }
      }
    } catch (RuntimeException failure) {
      sharedFailure = sharedFailureReason(failure, currentStage, List.of());
      problemCode = sharedFailure.code();
      problemStage = currentStage;
      problemFailure = sharedFailure;
    }
    if (sharedFailure == null) {
      taskLoop:
      for (OntologyScopeReader.Question question : workingScope.questions()) {
        List<OntologyTaskOutcome> questionOutcomes = new ArrayList<>();
        RelationTaskInput external = externalObjectInput(question, externalObjects);
        for (OntologyScopeReader.Task task : question.tasks()) {
          currentTaskId = task.taskId();
          List<OntologyTaskOutcome.TaskReference> dependencies =
              new ArrayList<>(
                  objectDependencies(execution.runId(), questionOutcomes, task.taskKind()));
          dependencies.addAll(external.dependencies());
          List<OntologyTaskOutcome.TaskReference> localDependencies =
              dependencies.stream()
                  .filter(dependency -> execution.runId().value().equals(dependency.runId()))
                  .toList();
          if (!dependenciesReviewed(questionOutcomes, localDependencies)
              || external.failure() != null) {
            OntologyTaskOutcome outcome =
                unprocessedOutcome(
                    question,
                    task,
                    dependencies,
                    external.failure() == null
                        ? dependencyFailureReason(dependencies)
                        : external.failure());
            outcomes.add(outcome);
            questionOutcomes.add(outcome);
            continue;
          }
          OntologyTypedTaskRunner.PreparedFormalTask item = null;
          try {
            currentStage = "READING";
            OntologyReadingCoordinator coordinator =
                identificationCoordinator(
                    configuration, savedCorpus, task, discovery, questionModelReading);
            OntologyReadingCoordinator.FormalResult reading =
                coordinator.completeFormal(workingScope, question.questionId(), task.taskId());
            if (reading.frozenPacket() == null) {
              if (outcomeContractV2) {
                OntologyTaskOutcome.FailureReason materialFailure =
                    materialReadingFailure(reading, dependencies);
                OntologyTaskOutcome outcome =
                    unprocessedOutcome(question, task, dependencies, materialFailure);
                outcomes.add(outcome);
                questionOutcomes.add(outcome);
                if (problemCode == null) {
                  problemCode = materialFailure.code();
                  problemStage = materialFailure.stage();
                  problemTaskId = task.taskId();
                  problemFailure = materialFailure;
                }
                continue;
              }
              throw new IllegalArgumentException(reading.issueCode());
            }
            currentStage = "PREPARE";
            item =
                prepareIdentificationTask(
                    configuration,
                    savedCorpus,
                    binding,
                    question,
                    task,
                    reading,
                    localDependencies,
                    reviewedByTaskId,
                    external,
                    outcomeContractV2,
                    model);
            prepared.add(item);
            currentStage = "MEMBERSHIP";
            jobs.recordFormalMembership(item);
            StructuredModelProvider provider =
                new OntologyCallBudgetProvider(
                    lazyProvider(providerFactory, configuration, model, execution.runId()), budget);
            OntologyTypedTaskRunner runner =
                new OntologyTypedTaskRunner(
                    provider,
                    configuration.reading().maxRequestBytes(),
                    configuration.reading().maxOutputBytes(),
                    jobs);
            currentStage = "DISPATCH";
            OntologyTypedTaskRunner.FormalResult result = runner.runFormal(item);
            completed.add(result);
            reviewedByTaskId.put(task.taskId(), result);
            OntologyTaskOutcome outcome = reviewedOutcome(question, task, item, dependencies);
            outcomes.add(outcome);
            questionOutcomes.add(outcome);
          } catch (OntologyDecisionRunner.FormalReadingModelOutputFailure localFailure) {
            OntologyTaskOutcome outcome =
                unprocessedOutcome(question, task, dependencies, localFailure.reason());
            outcomes.add(outcome);
            questionOutcomes.add(outcome);
            if (problemCode == null) {
              problemCode = localFailure.reason().code();
              problemStage = localFailure.reason().stage();
              problemTaskId = task.taskId();
              problemFailure = localFailure.reason();
            }
          } catch (OntologyTypedTaskRunner.FormalTaskFailure localFailure) {
            if (item == null) {
              throw localFailure;
            }
            OntologyTaskOutcome outcome =
                rejectedOutcome(question, task, item, dependencies, localFailure.reason());
            outcomes.add(outcome);
            questionOutcomes.add(outcome);
            if (problemCode == null) {
              problemCode = localFailure.reason().code();
              problemStage = localFailure.reason().stage();
              problemTaskId = task.taskId();
              problemFailure = localFailure.reason();
            }
          } catch (RuntimeException failure) {
            sharedFailure = sharedFailureReason(failure, currentStage, dependencies);
            OntologyTaskOutcome outcome =
                outcomeContractV2
                    ? unprocessedOutcome(question, task, item, dependencies, sharedFailure)
                    : rejectedOutcome(question, task, item, dependencies, sharedFailure);
            outcomes.add(outcome);
            questionOutcomes.add(outcome);
            problemCode = sharedFailure.code();
            problemStage = currentStage;
            problemTaskId = task.taskId();
            problemFailure = sharedFailure;
            break taskLoop;
          }
        }
      }
    }
    if (sharedFailure != null) {
      appendUnprocessedAfterSharedFailure(execution.runId(), workingScope, outcomes, sharedFailure);
    }
    try {
      OntologyScopedAssembler.FormalInput input =
          new OntologyScopedAssembler.FormalInput(
              binding,
              completed,
              formalCoverage(admitted.corpus(), workingScope, prepared, completed, outcomes));
      OntologyScopedAssembler assembler = new OntologyScopedAssembler();
      if (!usesFormalPacketV5(savedCorpus) || externalObjects.results().isEmpty()) {
        jobs.recordFormalAssembly(
            usesFormalPacketV5(savedCorpus)
                ? assembler.assembleFormalV2(
                    identificationBusinessInput(execution.runId(), input, outcomes))
                : assembler.assembleFormal(input));
      }
    } catch (RuntimeException assemblyFailure) {
      if (problemCode == null) {
        problemCode = publicFailureCode(assemblyFailure);
        problemTaskId = null;
        problemStage = "ASSEMBLY";
      }
    }
    OntologyRunOutput.Status status =
        problemCode == null ? OntologyRunOutput.Status.COMPLETED : OntologyRunOutput.Status.PARTIAL;
    try {
      AnalysisRunOutput output =
          identifyOutput(
              store,
              configuration.storage(),
              ontologyPolicies,
              execution.runId(),
              persisted,
              savedCorpus,
              scope,
              workingScope,
              scopeSnapshot,
              discovery == null ? null : discovery.document(),
              completed,
              prepared,
              outcomes,
              status);
      if (problemFailure == null) {
        jobs.recordFormalRuntimeObservation(
            budget.runtimeCounts(), problemCode, problemTaskId, problemStage);
      } else {
        jobs.recordFormalRuntimeObservation(budget.runtimeCounts(), problemFailure, problemTaskId);
      }
      return output;
    } catch (RuntimeException installFailure) {
      // The public module was not installed. Preserve prior task outcomes privately, but the
      // operation's terminal observation must report this later producer-owned install failure.
      jobs.recordFormalRuntimeObservation(
          budget.runtimeCounts(), terminalInstallFailureReason(installFailure));
      throw installFailure;
    }
  }

  private static OntologyReadingCoordinator identificationCoordinator(
      OntologyConfiguration configuration,
      SavedCorpus savedCorpus,
      OntologyScopeReader.Task task,
      DiscoveryStage discovery,
      OntologyDecisionRunner questionModelReading) {
    OntologyConfiguration.Reading reading = phaseReading(configuration, savedCorpus);
    OntologyDecisionRunner decisions =
        discovery == null ? questionModelReading : discovery.decisions();
    if (decisions == null) {
      if (task.readingMode() == OntologyScopeReader.ReadingMode.MODEL) {
        throw new IllegalStateException("ONTOLOGY_READING_PROVIDER_NOT_ALLOWED");
      }
      decisions =
          new OntologyDecisionRunner(
              request -> {
                throw new IllegalStateException("ONTOLOGY_READING_PROVIDER_NOT_ALLOWED");
              },
              reading.maxRequestBytes(),
              100_000);
    }
    return new OntologyReadingCoordinator(
        savedCorpus.admitted().corpus(), decisions,
        reading.maxReadingRounds(), reading.maxActionsPerRound(),
        reading.maxUnitBytes(), reading.maxRequestBytes(),
        reading.maxNavigationEntries(), usesFormalPacketV4(savedCorpus));
  }

  private static OntologyTypedTaskRunner.PreparedFormalTask prepareIdentificationTask(
      OntologyConfiguration configuration,
      SavedCorpus savedCorpus,
      OntologyTypedTaskRunner.FormalCorpusBinding binding,
      OntologyScopeReader.Question question,
      OntologyScopeReader.Task task,
      OntologyReadingCoordinator.FormalResult reading,
      List<OntologyTaskOutcome.TaskReference> localDependencies,
      Map<String, OntologyTypedTaskRunner.FormalResult> reviewedByTaskId,
      RelationTaskInput external,
      boolean outcomeContractV2,
      ModelBinding model) {
    List<OntologyTypedTaskRunner.FormalResult> objectCatalog =
        new ArrayList<>(reviewedObjectsFor(localDependencies, reviewedByTaskId));
    objectCatalog.addAll(external.catalog());
    boolean externalDependency = !question.objectSources().isEmpty();
    OntologyTypedTaskRunner.FormalTask formalTask =
        new OntologyTypedTaskRunner.FormalTask(
            binding,
            question.questionId(),
            task.taskId(),
            OntologyTaskRunner.TaskKind.valueOf(task.taskKind().name()),
            question.question(),
            withClueContext(savedCorpus, reading.frozenPacket(), reading.state().selectedClues()),
            objectCatalog,
            new OntologyTypedTaskRunner.FormalPromptSnapshot(
                promptFor(configuration, task.taskKind()), configuration.prompts().get("review")),
            new OntologyTypedTaskRunner.FormalLimits(
                configuration.reading().maxRequestBytes(),
                configuration.reading().maxOutputBytes(),
                configuration.reading().maxOutputTokens()),
            model.declaration(),
            externalDependency
                ? OntologyTypedTaskRunner.O1_EXTERNAL_TASK_DEPENDENCY_RULE_VERSION
                : outcomeContractV2
                    ? OntologyTypedTaskRunner.O1_TASK_DEPENDENCY_RULE_VERSION
                    : OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION,
            externalDependency ? external.dependencyFingerprint() : null,
            usesFormalPacketV5(savedCorpus) ? reading.state().selectedClues() : List.of());
    return OntologyTypedTaskRunner.prepareFormal(formalTask);
  }

  private static OntologyReadingCoordinator relationCoordinator(
      OntologyConfiguration configuration,
      SavedCorpus savedCorpus,
      OntologyJobResultStore jobs,
      OntologyCallBudgetProvider.RunBudget budget,
      ModelBinding model,
      AnalysisRunId runId,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory) {
    OntologyConfiguration.Reading reading = phaseReading(configuration, savedCorpus);
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            new OntologyCallBudgetProvider(
                lazyProvider(providerFactory, configuration, model, runId), budget),
            configuration.reading().maxRequestBytes(),
            configuration.reading().maxOutputBytes(),
            jobs,
            readingMaterial(configuration, savedCorpus));
    return new OntologyReadingCoordinator(
        savedCorpus.admitted().corpus(),
        decisions,
        reading.maxReadingRounds(),
        reading.maxActionsPerRound(),
        reading.maxUnitBytes(),
        reading.maxRequestBytes(),
        reading.maxNavigationEntries(),
        usesFormalPacketV4(savedCorpus));
  }

  private static AnalysisRunOutput executeRelate(
      AnalysisStepExecutionRequest execution,
      RunStoreHandle store,
      OntologyConfiguration configuration,
      CanonicalArtifactPolicyRegistry policies,
      AnalysisRunRequest request,
      SavedCorpus savedCorpus,
      OntologySelectionReader.Selection selection,
      JsonNode selectionSnapshot,
      SelectedFormalStage selectedIdentifications,
      ModelBinding model,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory) {
    if (execution.intent() != AnalysisExecutionIntent.RELATE_ONTOLOGY
        || execution.upstreamRunId() != null
        || execution.exactMaterialId() != null) {
      throw new IllegalArgumentException("ONTOLOGY_EXECUTION_INPUT_INVALID");
    }
    AnalysisRunRequest persisted =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, execution.runId()).request();
    if (!persisted.equals(request)) {
      throw new IllegalArgumentException("ONTOLOGY_EXECUTION_INPUT_INVALID");
    }
    requireSelectionSnapshot(selectionSnapshot, persisted);
    AdmittedEvidence admitted = savedCorpus.admitted();
    OntologyJobResultStore jobs =
        new OntologyJobResultStore(ontologyJournal(configuration), execution.runId());
    List<OntologyTypedTaskRunner.PreparedFormalTask> prepared = new ArrayList<>();
    List<OntologyTypedTaskRunner.FormalResult> completed = new ArrayList<>();
    OntologyCallBudgetProvider.RunBudget budget =
        new OntologyCallBudgetProvider.RunBudget(configuration.reading().maxRequests());
    String problemCode = null;
    String problemTaskId = null;
    String problemStage = null;
    OntologyTaskOutcome.FailureReason problemFailure = null;
    String currentTaskId = null;
    String currentStage = null;
    boolean outcomeContractV2 = selection.isV2();
    List<OntologyTaskOutcome> outcomes = new ArrayList<>();
    OntologyRelationReadingSelections readingSelections =
        new OntologyRelationReadingSelections(selection.questions());
    List<OntologyTypedTaskRunner.FormalResult> reviewedObjects =
        selectedIdentifications.results().stream()
            .map(SelectedFormalResult::result)
            .filter(result -> result.kind() == OntologyTaskRunner.TaskKind.OBJECT)
            .toList();
    if (!selection.questions().isEmpty()) {
      if (model == null || (!outcomeContractV2 && reviewedObjects.isEmpty())) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_OBJECT_REQUIRED");
      }
      OntologyTypedTaskRunner.FormalCorpusBinding binding =
          new OntologyTypedTaskRunner.FormalCorpusBinding(
              savedCorpus.corpusIdentity(), admitted.corpus().sourceIdentity());
      if (outcomeContractV2) {
        OntologyTaskOutcome.FailureReason sharedFailure = null;
        for (OntologySelectionReader.Question question : selection.questions()) {
          currentTaskId = question.taskId();
          RelationTaskInput input = relationTaskInput(question, selectedIdentifications);
          if (input.failure() != null) {
            OntologyTaskOutcome outcome =
                unprocessedRelationOutcome(question, null, input.dependencies(), input.failure());
            outcomes.add(outcome);
            if (problemCode == null) {
              problemCode = input.failure().code();
              problemTaskId = question.taskId();
              problemStage = input.failure().stage();
              problemFailure = input.failure();
            }
            continue;
          }
          OntologyTypedTaskRunner.PreparedFormalTask item = null;
          OntologyReadingCoordinator coordinator = null;
          boolean readingObserved = false;
          String readingFailureCode = "ONTOLOGY_READING_FAILED";
          try {
            currentStage = "READING";
            OntologyReadingPacket packet;
            List<String> visibleClues = question.clueRefs();
            if (question.readingMode() == OntologyScopeReader.ReadingMode.MODEL) {
              coordinator =
                  relationCoordinator(
                      configuration,
                      savedCorpus,
                      jobs,
                      budget,
                      model,
                      execution.runId(),
                      providerFactory);
              OntologyReadingCoordinator.FormalResult reading =
                  coordinator.completeFormal(question);
              readingSelections.observe(
                  question.taskId(), reading.state(), reading.status().name(), reading.issueCode());
              readingObserved = true;
              if (reading.frozenPacket() == null) {
                OntologyTaskOutcome.FailureReason materialFailure =
                    materialReadingFailure(reading, input.dependencies());
                outcomes.add(
                    unprocessedRelationOutcome(
                        question, null, input.dependencies(), materialFailure));
                if (problemCode == null) {
                  problemCode = materialFailure.code();
                  problemTaskId = question.taskId();
                  problemStage = materialFailure.stage();
                  problemFailure = materialFailure;
                }
                continue;
              }
              visibleClues = reading.state().selectedClues();
              packet = withClueContext(savedCorpus, reading.frozenPacket(), visibleClues);
            } else {
              packet = relationPacket(configuration, admitted.corpus(), question, savedCorpus);
              readingSelections.observe(question.taskId(), null, "READY", "");
              readingObserved = true;
            }
            currentStage = "PREPARE";
            OntologyTypedTaskRunner.FormalTask task =
                new OntologyTypedTaskRunner.FormalTask(
                    binding,
                    question.questionId(),
                    question.taskId(),
                    OntologyTaskRunner.TaskKind.RELATE,
                    question.question(),
                    packet,
                    input.catalog(),
                    new OntologyTypedTaskRunner.FormalPromptSnapshot(
                        configuration.prompts().get("relate"),
                        configuration.prompts().get("review")),
                    new OntologyTypedTaskRunner.FormalLimits(
                        configuration.reading().maxRequestBytes(),
                        configuration.reading().maxOutputBytes(),
                        configuration.reading().maxOutputTokens()),
                    model.declaration(),
                    OntologyTypedTaskRunner.O2_TASK_DEPENDENCY_RULE_VERSION,
                    input.dependencyFingerprint(),
                    usesFormalPacketV5(savedCorpus) ? visibleClues : List.of());
            item = OntologyTypedTaskRunner.prepareFormal(task);
            prepared.add(item);
            currentStage = "MEMBERSHIP";
            jobs.recordFormalMembership(item);
            OntologyTypedTaskRunner runner =
                new OntologyTypedTaskRunner(
                    new OntologyCallBudgetProvider(
                        lazyProvider(providerFactory, configuration, model, execution.runId()),
                        budget),
                    configuration.reading().maxRequestBytes(),
                    configuration.reading().maxOutputBytes(),
                    jobs);
            currentStage = "DISPATCH";
            OntologyTypedTaskRunner.FormalResult result = runner.runFormal(item);
            completed.add(result);
            outcomes.add(reviewedRelationOutcome(question, item, input.dependencies()));
          } catch (OntologyDecisionRunner.FormalReadingModelOutputFailure localFailure) {
            readingFailureCode = localFailure.reason().code();
            outcomes.add(
                unprocessedRelationOutcome(
                    question, null, input.dependencies(), localFailure.reason()));
            if (problemCode == null) {
              problemCode = localFailure.reason().code();
              problemTaskId = question.taskId();
              problemStage = localFailure.reason().stage();
              problemFailure = localFailure.reason();
            }
          } catch (OntologyTypedTaskRunner.FormalTaskFailure localFailure) {
            if (item == null) {
              throw localFailure;
            }
            outcomes.add(
                rejectedRelationOutcome(
                    question, item, input.dependencies(), localFailure.reason()));
            if (problemCode == null) {
              problemCode = localFailure.reason().code();
              problemTaskId = question.taskId();
              problemStage = localFailure.reason().stage();
              problemFailure = localFailure.reason();
            }
          } catch (RuntimeException failure) {
            sharedFailure = sharedFailureReason(failure, currentStage, input.dependencies());
            readingFailureCode = sharedFailure.code();
            outcomes.add(
                unprocessedRelationOutcome(question, item, input.dependencies(), sharedFailure));
            problemCode = sharedFailure.code();
            problemTaskId = question.taskId();
            problemStage = currentStage;
            problemFailure = sharedFailure;
            break;
          } finally {
            if (!readingObserved) {
              readingSelections.observe(
                  question.taskId(),
                  coordinator == null ? null : coordinator.observedFormalState(),
                  "FAILED",
                  readingFailureCode);
            }
          }
        }
        if (sharedFailure != null) {
          appendUnprocessedRelationOutcomes(
              selection, selectedIdentifications, outcomes, sharedFailure);
        }
      } else {
        try {
          for (OntologySelectionReader.Question question : selection.questions()) {
            currentTaskId = question.taskId();
            currentStage = "READING";
            OntologyReadingPacket packet =
                relationPacket(configuration, admitted.corpus(), question, savedCorpus);
            currentStage = "PREPARE";
            OntologyTypedTaskRunner.FormalTask task =
                new OntologyTypedTaskRunner.FormalTask(
                    binding,
                    question.questionId(),
                    question.taskId(),
                    OntologyTaskRunner.TaskKind.RELATE,
                    question.question(),
                    packet,
                    reviewedObjects,
                    new OntologyTypedTaskRunner.FormalPromptSnapshot(
                        configuration.prompts().get("relate"),
                        configuration.prompts().get("review")),
                    new OntologyTypedTaskRunner.FormalLimits(
                        configuration.reading().maxRequestBytes(),
                        configuration.reading().maxOutputBytes(),
                        configuration.reading().maxOutputTokens()),
                    model.declaration());
            OntologyTypedTaskRunner.PreparedFormalTask item =
                OntologyTypedTaskRunner.prepareFormal(task);
            prepared.add(item);
            currentStage = "MEMBERSHIP";
            jobs.recordFormalMembership(item);
            OntologyTypedTaskRunner runner =
                new OntologyTypedTaskRunner(
                    new OntologyCallBudgetProvider(
                        lazyProvider(providerFactory, configuration, model, execution.runId()),
                        budget),
                    configuration.reading().maxRequestBytes(),
                    configuration.reading().maxOutputBytes(),
                    jobs);
            currentStage = "DISPATCH";
            completed.add(runner.runFormal(item));
          }
        } catch (OntologyTypedTaskRunner.FormalTaskFailure localFailure) {
          problemCode = localFailure.reason().code();
          problemStage = localFailure.reason().stage();
          problemTaskId = currentTaskId;
          problemFailure = localFailure.reason();
        } catch (RuntimeException failure) {
          problemCode = publicFailureCode(failure);
          problemStage = currentStage;
          problemTaskId = currentTaskId;
        }
      }
    }
    ObjectNode relations =
        relationDocument(
            policies,
            readingSelections,
            selectionSnapshot,
            persisted,
            selection,
            outcomes,
            prepared,
            completed,
            problemCode);
    String relationSchemaVersion = relations.path("schemaVersion").asText();
    try {
      InstalledPublication installed =
          install(
              store,
              policies,
              execution.runId(),
              persisted,
              admitted.evidencePublication(),
              4,
              "ontology-relations",
              upstreamPayloadReferences(
                  store,
                  configuration.storage(),
                  persisted.ontologyInputs().identificationPublications()),
              List.of(
                  standalonePayload(
                      "ontology-relations.json",
                      "ONTOLOGY_RELATIONS",
                      relationSchemaVersion,
                      relations,
                      policies,
                      new CanonicalJsonCodec())));
      OntologyRunOutput.Status status =
          problemCode == null
              ? OntologyRunOutput.Status.COMPLETED
              : OntologyRunOutput.Status.PARTIAL;
      if (problemFailure == null) {
        jobs.recordFormalRuntimeObservation(
            budget.runtimeCounts(), problemCode, problemTaskId, problemStage);
      } else {
        jobs.recordFormalRuntimeObservation(budget.runtimeCounts(), problemFailure, problemTaskId);
      }
      return AnalysisRunOutput.ontology(
          new OntologyRunOutput(
              AnalysisRunRequest.OntologyOperation.RELATE_ONTOLOGY,
              execution.runId(),
              status,
              admitted.sourceBasis(),
              admitted.evidencePublication(),
              installed.modulePublication()));
    } catch (RuntimeException installFailure) {
      // See identify: task-local outcomes remain saved, while the operation ends at install.
      jobs.recordFormalRuntimeObservation(
          budget.runtimeCounts(), terminalInstallFailureReason(installFailure));
      throw installFailure;
    }
  }

  /** Projects only saved task results and observations; it never dispatches or changes scope. */
  private static ObjectNode relationDocument(
      CanonicalArtifactPolicyRegistry policies,
      OntologyRelationReadingSelections readingSelections,
      JsonNode selectionSnapshot,
      AnalysisRunRequest persisted,
      OntologySelectionReader.Selection selection,
      List<OntologyTaskOutcome> outcomes,
      List<OntologyTypedTaskRunner.PreparedFormalTask> prepared,
      List<OntologyTypedTaskRunner.FormalResult> completed,
      String problemCode) {
    boolean outcomeContractV2 = selection.isV2();
    ObjectNode relations = JsonNodeFactory.instance.objectNode();
    String relationSchemaVersion = stageSchemaVersion(policies, "ONTOLOGY_RELATIONS");
    relations.put("schemaVersion", relationSchemaVersion);
    if ("ontology-relations-v3".equals(relationSchemaVersion)) {
      relations.set("readingSelections", readingSelections.document());
    }
    relations.set("selection", selectionSnapshot.deepCopy());
    copySemanticUpstreams(relations, persisted);
    relations.put(
        "status",
        problemCode == null ? (completed.isEmpty() ? "UNDETERMINED" : "REVIEWED") : "PARTIAL");
    Map<String, OntologyTaskOutcome> outcomesByProducer = new LinkedHashMap<>();
    if (outcomeContractV2) {
      for (OntologyTaskOutcome outcome : outcomes) {
        if (outcome.producingTaskId() != null
            && outcomesByProducer.putIfAbsent(outcome.producingTaskId(), outcome) != null) {
          throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
        }
      }
    }
    var taskRecords = relations.putArray("taskRecords");
    for (OntologyTypedTaskRunner.PreparedFormalTask task : prepared) {
      ObjectNode row = taskRecords.addObject();
      row.put("taskId", task.task().taskId());
      row.put("producingTaskId", OntologyTypedTaskRunner.formalProducingTaskId(task));
      row.put("jobKey", task.jobKey());
      row.put("taskKind", task.task().kind().name());
      if (outcomeContractV2) {
        OntologyTaskOutcome outcome =
            outcomesByProducer.get(OntologyTypedTaskRunner.formalProducingTaskId(task));
        if (outcome == null) {
          throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
        }
        row.put("status", outcome.status().name());
      } else {
        boolean reviewed =
            completed.stream()
                .anyMatch(
                    result ->
                        result
                            .identity()
                            .producingTaskId()
                            .equals(OntologyTypedTaskRunner.formalProducingTaskId(task)));
        row.put("status", reviewed ? "REVIEWED" : "REJECTED");
      }
    }
    if (outcomeContractV2
        || (selection.questions().isEmpty()
            && usesOntologyPayloadV2(policies, "ONTOLOGY_RELATIONS"))) {
      ArrayNode outcomeNodes = relations.putArray("taskOutcomes");
      outcomes.forEach(outcome -> taskOutcome(outcomeNodes.addObject(), outcome));
    }
    ArrayNode dispositions = relations.putArray("taskDispositions");
    for (OntologyScopedAssembler.TaskDisposition task :
        outcomeContractV2
            ? relationTaskDispositions(selection, outcomes)
            : relationTaskDispositions(selection, prepared, completed)) {
      ObjectNode row = dispositions.addObject();
      row.put("taskId", task.taskId());
      if (task.producingTaskId() == null) {
        row.putNull("producingTaskId");
      } else {
        row.put("producingTaskId", task.producingTaskId());
      }
      row.put("status", task.status().name());
      row.put("reason", task.reason());
    }
    var links = relations.putArray("relations");
    var unresolved = relations.putArray("unresolved");
    for (OntologyTypedTaskRunner.FormalResult result : completed) {
      JsonNode review = new CanonicalJsonCodec().parseCanonical(result.review());
      review.path("definitions").path("links").forEach(link -> links.add(link.deepCopy()));
      review.path("unresolved").forEach(issue -> unresolved.add(issue.deepCopy()));
    }
    return relations;
  }

  private static AnalysisRunOutput executePublish(
      AnalysisStepExecutionRequest execution,
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      CanonicalArtifactPolicyRegistry policies,
      AnalysisRunRequest request,
      SavedCorpus savedCorpus,
      JsonNode selectionSnapshot,
      OntologyScopedAssembler.FormalInput assemblyInput,
      OntologyScopedAssembler.BusinessFormalInput businessInput,
      boolean selectionV2,
      List<SelectedTaskOutcome> selectedTaskOutcomes) {
    if (execution.intent() != AnalysisExecutionIntent.PUBLISH_ONTOLOGY
        || execution.upstreamRunId() != null
        || execution.exactMaterialId() != null) {
      throw new IllegalArgumentException("ONTOLOGY_EXECUTION_INPUT_INVALID");
    }
    AnalysisRunRequest persisted =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, execution.runId()).request();
    if (!persisted.equals(request)) {
      throw new IllegalArgumentException("ONTOLOGY_EXECUTION_INPUT_INVALID");
    }
    requireSelectionSnapshot(selectionSnapshot, persisted);
    AdmittedEvidence admitted = savedCorpus.admitted();
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    OntologyScopedAssembler.FormalAssembly assembly;
    try {
      OntologyScopedAssembler assembler = new OntologyScopedAssembler();
      assembly =
          businessInput == null
              ? assembler.assembleFormal(assemblyInput)
              : assembler.assembleFormalV2(businessInput);
    } catch (RuntimeException assemblyFailure) {
      new OntologyJobResultStore(
              SourceAnalysisExecution.checkedDirectory(storage.root(), "ontology-journal"),
              execution.runId())
          .recordFormalRuntimeObservation(
              new OntologyCallBudgetProvider.RuntimeCounts(0, 0, 0, 0),
              publicFailureCode(assemblyFailure),
              null,
              "ASSEMBLY");
      throw assemblyFailure;
    }
    ObjectNode review = (ObjectNode) json.parseCanonical(assembly.review());
    if (selectionV2 && hasBlockingAssemblyIssues(review)) {
      OntologyJobResultStore jobs =
          new OntologyJobResultStore(
              SourceAnalysisExecution.checkedDirectory(storage.root(), "ontology-journal"),
              execution.runId());
      // The pure assembly retains the precise structured issues privately; it is not a receipt.
      jobs.recordFormalAssembly(assembly);
      jobs.recordFormalRuntimeObservation(
          new OntologyCallBudgetProvider.RuntimeCounts(0, 0, 0, 0),
          "ONTOLOGY_ASSEMBLY_DEFINITION_CONFLICT",
          null,
          "ASSEMBLY");
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_DEFINITION_CONFLICT");
    }
    review.put(
        "typedReviewSchemaVersion",
        businessInput == null ? "ontology-typed-review-v3" : "ontology-typed-review-v4");
    review.set("selection", selectionSnapshot.deepCopy());
    copySemanticUpstreams(review, persisted);
    ObjectNode coverage = (ObjectNode) json.parseCanonical(assembly.coverage());
    String coverageSchemaVersion =
        businessInput != null
            ? "ontology-coverage-v3"
            : selectionV2 ? "ontology-coverage-v2" : "ontology-coverage-v1";
    String reviewSchemaVersion =
        businessInput != null
            ? "ontology-review-v3"
            : selectionV2 ? "ontology-review-v2" : "ontology-review-v1";
    if (selectionV2) {
      coverage.put("schemaVersion", coverageSchemaVersion);
      review.put("schemaVersion", reviewSchemaVersion);
      inheritedTaskOutcomes(coverage.putArray("taskOutcomes"), selectedTaskOutcomes);
      inheritedTaskOutcomes(review.putArray("taskOutcomes"), selectedTaskOutcomes);
    }
    InstalledPublication installed =
        install(
            store,
            policies,
            execution.runId(),
            persisted,
            admitted.evidencePublication(),
            5,
            "ontology-publisher",
            upstreamPayloadReferences(
                store,
                storage,
                concatenatePublications(
                    persisted.ontologyInputs().identificationPublications(),
                    persisted.ontologyInputs().relationPublications())),
            List.of(
                standalonePayload(
                    "ontology-coverage.json",
                    "ONTOLOGY_COVERAGE",
                    coverageSchemaVersion,
                    coverage,
                    policies,
                    json),
                standalonePayload(
                    "ontology-review.json",
                    "ONTOLOGY_REVIEW",
                    reviewSchemaVersion,
                    review,
                    policies,
                    json),
                jsonlPayload(
                    "ontology-sources.jsonl",
                    "ONTOLOGY_SOURCE_INDEX",
                    "ontology-source-v1",
                    assembly.sourceIndex(),
                    policies),
                standalonePayload(
                    "ontology.json",
                    "ONTOLOGY",
                    businessInput == null ? "ontology-v1" : "ontology-v2",
                    (ObjectNode) json.parseCanonical(assembly.ontology()),
                    policies,
                    json)));
    return AnalysisRunOutput.ontology(
        new OntologyRunOutput(
            AnalysisRunRequest.OntologyOperation.PUBLISH_ONTOLOGY,
            execution.runId(),
            publicationStatus(coverage),
            admitted.sourceBasis(),
            admitted.evidencePublication(),
            installed.modulePublication()));
  }

  /**
   * Runs the one bounded formal survey/priority chain and restores its output into the existing
   * Scope.
   */
  private static DiscoveryStage executeFormalDiscovery(
      OntologyConfiguration configuration,
      OntologyScopeReader.Scope requestedScope,
      OntologyEvidenceCorpus corpus,
      OntologyJobResultStore jobs,
      OntologyCallBudgetProvider.RunBudget budget,
      ModelBinding model,
      AnalysisRunId runId,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory) {
    if (model == null) {
      throw new IllegalArgumentException("ONTOLOGY_MODEL_CONFIGURATION_REQUIRED");
    }
    StructuredModelProvider boundedProvider =
        new OntologyCallBudgetProvider(
            lazyProvider(providerFactory, configuration, model, runId), budget);
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            boundedProvider,
            configuration.reading().maxRequestBytes(),
            configuration.reading().maxOutputBytes(),
            jobs,
            corpus.usesBusinessLinkNavigation()
                ? OntologyDecisionRunner.formalReadingMaterialV4(
                    configuration.prompts().get("reading"),
                    configuration.reading().maxOutputTokens())
                : OntologyDecisionRunner.formalReadingMaterial(
                    configuration.prompts().get("reading"),
                    configuration.reading().maxOutputTokens()));
    OntologyDecisionRunner.PreparedDecision preparedSurvey =
        runner.prepareFormalSurvey(
            corpus,
            configuration.reading().maxNavigationEntries(),
            OntologyDecisionRunner.formalSurveyMaterial(
                configuration.prompts().get("survey"), configuration.reading().maxOutputTokens()));
    OntologyDecisionRunner.Decision survey = runner.execute(preparedSurvey);
    List<DiscoveryQuestion> questions = surveyQuestions(corpus, survey);
    runner.saveFormalDecision("survey", preparedSurvey, survey, corpus);
    if (questions.isEmpty()) {
      return new DiscoveryStage(
          selectedDiscoveryScope(requestedScope, List.of()),
          discoveryDocument(preparedSurvey, survey, null, questions, List.of(), List.of()),
          null,
          runner);
    }
    List<OntologyDecisionRunner.FormalSurveyQuestion> formalQuestions =
        questions.stream()
            .map(
                question ->
                    new OntologyDecisionRunner.FormalSurveyQuestion(
                        question.questionRef(),
                        question.question(),
                        question.entryRefs(),
                        question.clueRefs()))
            .toList();
    try {
      OntologyDecisionRunner.PreparedDecision preparedPriority =
          runner.prepareFormalPrioritize(
              corpus,
              formalQuestions,
              configuration.reading().maxNavigationEntries(),
              requestedScope.purpose() == OntologyScopeReader.Purpose.SKELETON ? 1 : 3,
              OntologyDecisionRunner.formalPrioritizeMaterial(
                  configuration.prompts().get("prioritize"),
                  configuration.reading().maxOutputTokens()),
              requestedScope.purpose());
      OntologyDecisionRunner.Decision priority = runner.execute(preparedPriority);
      PrioritySelection selected = prioritySelection(questions, priority, requestedScope.purpose());
      runner.saveFormalDecision("prioritize", preparedPriority, priority, corpus);
      return new DiscoveryStage(
          selectedDiscoveryScope(requestedScope, selected.questions()),
          discoveryDocument(
              preparedSurvey,
              survey,
              priority,
              questions,
              selected.questionRefs(),
              selected.deferredQuestions()),
          null,
          runner);
    } catch (OntologyCallBudgetProvider.DispatchLimitExceeded exhausted) {
      // Preserve the producer-owned pre-dispatch fact for the operation-level typed outcome.
      throw exhausted;
    } catch (OntologyDecisionRunner.DecisionModelOutputFailure invalidPriority) {
      List<DeferredQuestion> deferred =
          questions.stream()
              .map(question -> new DeferredQuestion(question.questionRef(), invalidPriority.code()))
              .toList();
      return new DiscoveryStage(
          selectedDiscoveryScope(requestedScope, List.of()),
          discoveryDocument(preparedSurvey, survey, null, questions, List.of(), deferred),
          invalidPriority.code(),
          runner,
          sharedFailureReason(invalidPriority, "PRIORITIZE", List.of()));
    } catch (RuntimeException failedPriority) {
      String reason = publicFailureCode(failedPriority);
      List<DeferredQuestion> deferred =
          questions.stream()
              .map(question -> new DeferredQuestion(question.questionRef(), reason))
              .toList();
      return new DiscoveryStage(
          selectedDiscoveryScope(requestedScope, List.of()),
          discoveryDocument(preparedSurvey, survey, null, questions, List.of(), deferred),
          reason,
          runner);
    }
  }

  private static OntologyScopeReader.Scope selectedDiscoveryScope(
      OntologyScopeReader.Scope requestedScope, List<OntologyScopeReader.Question> questions) {
    return new OntologyScopeReader.Scope(
        requestedScope.schemaVersion(),
        OntologyScopeReader.Mode.DISCOVERY,
        OntologyScopeReader.SelectionMode.MODEL,
        requestedScope.purpose(),
        questions);
  }

  private static List<DiscoveryQuestion> surveyQuestions(
      OntologyEvidenceCorpus corpus, OntologyDecisionRunner.Decision survey) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    JsonNode output = json.parseCanonical(survey.output());
    List<DiscoveryQuestion> questions = new ArrayList<>();
    Set<String> sourceQuestionIds = new LinkedHashSet<>();
    int sequence = 1;
    for (JsonNode source : output.path("questions")) {
      String sourceQuestionId = source.path("questionId").asText();
      String question = source.path("question").asText();
      if (!sourceQuestionIds.add(sourceQuestionId) || question.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_SURVEY_QUESTION_INVALID");
      }
      List<String> entries = discoveryReferences(corpus, source.path("candidateEntryRefs"), true);
      List<String> clues = discoveryReferences(corpus, source.path("clueRefs"), false);
      questions.add(
          new DiscoveryQuestion(
              "Q" + sequence++, survey.jobKey(), sourceQuestionId, question, entries, clues));
    }
    return List.copyOf(questions);
  }

  private static List<String> discoveryReferences(
      OntologyEvidenceCorpus corpus, JsonNode references, boolean entry) {
    if (!references.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_SURVEY_REFERENCE_INVALID");
    }
    List<String> values = new ArrayList<>();
    Set<String> unique = new LinkedHashSet<>();
    String pattern = entry ? "E[1-9][0-9]*" : "K[1-9][0-9]*";
    for (JsonNode reference : references) {
      if (!reference.isTextual()
          || !reference.asText().matches(pattern)
          || !unique.add(reference.asText())) {
        throw new IllegalArgumentException("ONTOLOGY_SURVEY_REFERENCE_INVALID");
      }
      try {
        if (entry) {
          corpus.aliases().entry(reference.asText());
        } else {
          corpus.aliases().clue(reference.asText());
        }
      } catch (IllegalArgumentException unknown) {
        throw new IllegalArgumentException("ONTOLOGY_SURVEY_REFERENCE_INVALID");
      }
      values.add(reference.asText());
    }
    return List.copyOf(values);
  }

  private static PrioritySelection prioritySelection(
      List<DiscoveryQuestion> questions,
      OntologyDecisionRunner.Decision priority,
      OntologyScopeReader.Purpose purpose) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    JsonNode output = json.parseCanonical(priority.output());
    Map<String, DiscoveryQuestion> byRef = new LinkedHashMap<>();
    questions.forEach(question -> byRef.put(question.questionRef(), question));
    Set<String> handled = new LinkedHashSet<>();
    List<OntologyScopeReader.Question> selected = new ArrayList<>();
    List<String> refs = new ArrayList<>();
    for (JsonNode item : output.path("selectedQuestions")) {
      DiscoveryQuestion question = byRef.get(item.path("questionRef").asText());
      if (question == null || !handled.add(question.questionRef())) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
      }
      if (!item.path("taskKinds").isArray() || item.path("taskKinds").isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_TASK_INVALID");
      }
      List<OntologyScopeReader.Task> tasks = new ArrayList<>();
      Set<String> taskKinds = new LinkedHashSet<>();
      for (JsonNode kind : item.path("taskKinds")) {
        if (!kind.isTextual()
            || !Set.of("OBJECT", "ACTION", "ANALYTIC").contains(kind.asText())
            || !taskKinds.add(kind.asText())) {
          throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_TASK_INVALID");
        }
      }
      if (!taskKinds.contains("OBJECT")) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_OBJECT_REQUIRED");
      }
      if (purpose == OntologyScopeReader.Purpose.SKELETON && !taskKinds.equals(Set.of("OBJECT"))) {
        throw new IllegalArgumentException("ONTOLOGY_SCOPE_SKELETON_TASK_INVALID");
      }
      for (String kind : List.of("OBJECT", "ACTION", "ANALYTIC")) {
        if (taskKinds.contains(kind)) {
          tasks.add(
              new OntologyScopeReader.Task(
                  "discovery-"
                      + question.questionRef()
                      + "-"
                      + kind.toLowerCase(java.util.Locale.ROOT),
                  OntologyScopeReader.TaskKind.valueOf(kind),
                  OntologyScopeReader.ReadingMode.MODEL,
                  List.of(),
                  List.of()));
        }
      }
      String specificQuestion = item.path("specificQuestion").asText();
      if (specificQuestion.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
      }
      selected.add(
          new OntologyScopeReader.Question(
              question.questionRef(),
              specificQuestion,
              question.entryRefs(),
              question.clueRefs(),
              tasks));
      refs.add(question.questionRef());
    }
    List<DeferredQuestion> deferred = new ArrayList<>();
    for (JsonNode item : output.path("deferredQuestions")) {
      DiscoveryQuestion question = byRef.get(item.path("questionRef").asText());
      String reason = item.path("reason").asText();
      if (question == null || reason.isBlank() || !handled.add(question.questionRef())) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
      }
      deferred.add(new DeferredQuestion(question.questionRef(), reason));
    }
    for (DiscoveryQuestion question : questions) {
      if (handled.add(question.questionRef())) {
        deferred.add(
            new DeferredQuestion(question.questionRef(), "NOT_SELECTED_WITHIN_DECLARED_LIMIT"));
      }
    }
    return new PrioritySelection(List.copyOf(selected), List.copyOf(refs), List.copyOf(deferred));
  }

  private static ObjectNode discoveryDocument(
      OntologyDecisionRunner.PreparedDecision preparedSurvey,
      OntologyDecisionRunner.Decision survey,
      OntologyDecisionRunner.Decision priority,
      List<DiscoveryQuestion> questions,
      List<String> selectedQuestionRefs,
      List<DeferredQuestion> deferredQuestions) {
    ObjectNode discovery = JsonNodeFactory.instance.objectNode();
    ArrayNode pages = discovery.putArray("surveyPages");
    ObjectNode page = pages.addObject();
    page.put("pageIndex", 1);
    page.put("jobKey", survey.jobKey());
    JsonNode visibleScope =
        new CanonicalJsonCodec().parseCanonical(preparedSurvey.input()).path("visibleScope");
    ArrayNode entries = page.putArray("entryRefs");
    visibleScope.path("displayedEntryRefs").forEach(entries::add);
    ArrayNode clues = page.putArray("clueRefs");
    visibleScope.path("displayedClueRefs").forEach(clues::add);
    if (priority == null) {
      discovery.putNull("priorityJobKey");
    } else {
      discovery.put("priorityJobKey", priority.jobKey());
    }
    ArrayNode mappings = discovery.putArray("questions");
    for (DiscoveryQuestion question : questions) {
      ObjectNode item = mappings.addObject();
      item.put("questionRef", question.questionRef());
      item.put("surveyJobKey", question.surveyJobKey());
      item.put("questionId", question.sourceQuestionId());
      item.put("question", question.question());
      ArrayNode entryRefs = item.putArray("candidateEntryRefs");
      question.entryRefs().forEach(entryRefs::add);
      ArrayNode clueRefs = item.putArray("clueRefs");
      question.clueRefs().forEach(clueRefs::add);
    }
    ArrayNode selected = discovery.putArray("selectedQuestionRefs");
    selectedQuestionRefs.forEach(selected::add);
    ArrayNode deferred = discovery.putArray("deferredQuestions");
    for (DeferredQuestion item : deferredQuestions) {
      ObjectNode value = deferred.addObject();
      value.put("questionRef", item.questionRef());
      value.put("reason", item.reason());
    }
    return discovery;
  }

  private static OntologyScopedAssembler.ScopedCoverage formalCoverage(
      OntologyEvidenceCorpus corpus,
      OntologyScopeReader.Scope scope,
      List<OntologyTypedTaskRunner.PreparedFormalTask> prepared,
      List<OntologyTypedTaskRunner.FormalResult> completed,
      List<OntologyTaskOutcome> outcomes) {
    List<OntologyScopedAssembler.ReadingDisposition> reading = new java.util.ArrayList<>();
    List<OntologyScopedAssembler.TaskDisposition> tasks = new java.util.ArrayList<>();
    Map<String, OntologyTypedTaskRunner.PreparedFormalTask> preparedByProducingTaskId =
        new LinkedHashMap<>();
    for (OntologyTypedTaskRunner.PreparedFormalTask task : prepared) {
      String producingTaskId = OntologyTypedTaskRunner.formalProducingTaskId(task);
      if (preparedByProducingTaskId.putIfAbsent(producingTaskId, task) != null) {
        throw new IllegalArgumentException("ONTOLOGY_SCOPE_INVALID");
      }
    }
    for (OntologyTypedTaskRunner.FormalResult result : completed) {
      OntologyTypedTaskRunner.PreparedFormalTask task =
          preparedByProducingTaskId.get(result.identity().producingTaskId());
      if (task == null
          || !task.task().questionId().equals(result.questionId())
          || task.task().kind() != result.kind()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_RESULT_IDENTITY_INVALID");
      }
      for (OntologyReadingPacket.PackedUnit unit : result.packet().units()) {
        for (String entryId : unit.entryUses()) {
          reading.add(
              new OntologyScopedAssembler.ReadingDisposition(
                  task.task().taskId(),
                  corpus.aliases().entryRef(entryId),
                  result.packet().evidenceUnitRef(unit.localRef()),
                  OntologyScopedAssembler.ReadingDispositionStatus.READ,
                  "The exact packet unit was dispatched to the formal task."));
        }
      }
    }
    tasks.addAll(taskDispositions(scope, outcomes));
    return new OntologyScopedAssembler.ScopedCoverage(
        new OntologyScopedAssembler.InputDenominators(
            corpus.navigation(0, Integer.MAX_VALUE).totalEntries(),
            corpus.frontendRequestCount(),
            corpus.schemaSourceCount()),
        reading,
        tasks);
  }

  private static OntologyScopedAssembler.BusinessFormalInput identificationBusinessInput(
      AnalysisRunId runId,
      OntologyScopedAssembler.FormalInput input,
      List<OntologyTaskOutcome> outcomes) {
    Map<String, OntologyTypedTaskRunner.FormalResult> results = new LinkedHashMap<>();
    input.results().forEach(result -> results.put(result.identity().producingTaskId(), result));
    Map<String, OntologyTaskOutcome> outcomesByTask = new LinkedHashMap<>();
    outcomes.forEach(outcome -> outcomesByTask.put(outcome.taskId(), outcome));
    List<OntologyScopedAssembler.TaskDisposition> tasks = new ArrayList<>();
    List<OntologyScopedAssembler.BusinessTaskObligation> obligations = new ArrayList<>();
    for (OntologyScopedAssembler.TaskDisposition task : input.coverage().taskDispositions()) {
      OntologyTaskOutcome outcome = outcomesByTask.get(task.taskId());
      if (outcome == null) throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
      OntologyTypedTaskRunner.FormalResult reviewed = results.get(task.producingTaskId());
      OntologyScopedAssembler.TaskDisposition actual =
          reviewed == null
              ? task
              : new OntologyScopedAssembler.TaskDisposition(
                  task.taskId(),
                  task.producingTaskId(),
                  task.status(),
                  task.reason(),
                  reviewed.identity());
      tasks.add(actual);
      obligations.add(
          new OntologyScopedAssembler.BusinessTaskObligation(
              runId.value(),
              outcome.questionId(),
              outcome.taskKind(),
              actual,
              reviewed == null ? List.of() : reviewed.visibleClueRefs(),
              outcome.reason()));
    }
    return new OntologyScopedAssembler.BusinessFormalInput(
        new OntologyScopedAssembler.FormalInput(
            input.binding(),
            input.results(),
            new OntologyScopedAssembler.ScopedCoverage(
                input.coverage().inputDenominators(),
                input.coverage().readingDispositions(),
                tasks)),
        obligations);
  }

  private static OntologyScopedAssembler.BusinessFormalInput publicationBusinessInput(
      OntologyScopedAssembler.FormalInput input,
      List<SelectedFormalResult> results,
      List<SelectedTaskDisposition> dispositions,
      List<SelectedTaskOutcome> outcomes) {
    Map<SelectedFormalResultKey, OntologyTypedTaskRunner.FormalResult> reviewed =
        selectedFormalResultsByOwnerProducer(results);
    List<OntologyScopedAssembler.BusinessTaskObligation> obligations = new ArrayList<>();
    for (SelectedTaskDisposition selected : dispositions) {
      JsonNode outcome =
          outcomes.stream()
              .filter(
                  item ->
                      selected.runId().equals(item.runId())
                          && selected.disposition().taskId().equals(item.taskId()))
              .map(SelectedTaskOutcome::outcome)
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID"));
      OntologyTypedTaskRunner.FormalResult result =
          reviewed.get(new SelectedFormalResultKey(selected.runId(), selected.producingTaskId()));
      OntologyScopedAssembler.TaskDisposition disposition = selected.disposition();
      if (result != null) {
        disposition =
            new OntologyScopedAssembler.TaskDisposition(
                disposition.taskId(),
                disposition.producingTaskId(),
                disposition.status(),
                disposition.reason(),
                result.identity());
      }
      obligations.add(
          new OntologyScopedAssembler.BusinessTaskObligation(
              selected.runId(),
              selected.questionId(),
              OntologyTaskRunner.TaskKind.valueOf(outcome.path("taskKind").asText()),
              disposition,
              result == null ? selected.selectedClueRefs() : result.visibleClueRefs(),
              savedTaskFailureReason(outcome.path("reason"))));
    }
    return new OntologyScopedAssembler.BusinessFormalInput(input, obligations);
  }

  private static OntologyTaskOutcome.FailureReason savedTaskFailureReason(JsonNode reason) {
    if (reason.isNull()) return null;
    requireV2TaskOutcomeReason(reason);
    List<String> expected = new ArrayList<>();
    reason.path("expectedRefs").forEach(ref -> expected.add(ref.asText()));
    List<OntologyTaskOutcome.TaskReference> dependencies = new ArrayList<>();
    reason
        .path("dependencyTaskRefs")
        .forEach(
            ref ->
                dependencies.add(
                    new OntologyTaskOutcome.TaskReference(
                        ref.path("runId").asText(),
                        ref.path("questionId").asText(),
                        ref.path("taskId").asText())));
    return new OntologyTaskOutcome.FailureReason(
        reason.path("code").asText(),
        OntologyTaskOutcome.Category.valueOf(reason.path("category").asText()),
        reason.path("stage").isNull() ? null : reason.path("stage").asText(),
        reason.path("jsonPointer").isNull() ? null : reason.path("jsonPointer").asText(),
        reason.path("offendingRef").isNull() ? null : reason.path("offendingRef").asText(),
        expected,
        dependencies);
  }

  private static AnalysisRunOutput identifyOutput(
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      CanonicalArtifactPolicyRegistry policies,
      AnalysisRunId runId,
      AnalysisRunRequest persisted,
      SavedCorpus savedCorpus,
      OntologyScopeReader.Scope originalScope,
      OntologyScopeReader.Scope workingScope,
      JsonNode scopeSnapshot,
      ObjectNode discovery,
      List<OntologyTypedTaskRunner.FormalResult> completed,
      List<OntologyTypedTaskRunner.PreparedFormalTask> prepared,
      List<OntologyTaskOutcome> outcomes,
      OntologyRunOutput.Status status) {
    AdmittedEvidence admitted = savedCorpus.admitted();
    boolean outcomeContractV2 = usesOntologyPayloadV2(policies, "ONTOLOGY_IDENTIFICATION");
    String schemaVersion = stageSchemaVersion(policies, "ONTOLOGY_IDENTIFICATION");
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", schemaVersion);
    document.put("status", status.name());
    document.set("scope", scopeSnapshot.deepCopy());
    copySemanticUpstreams(document, persisted);
    if (discovery != null) {
      document.set(
          "selectedQuestions",
          selectedQuestionsDocument(admitted.corpus(), workingScope, prepared));
      document.set("discovery", discovery);
    }
    Map<String, OntologyTaskOutcome> outcomesByProducingTaskId = new LinkedHashMap<>();
    for (OntologyTaskOutcome outcome : outcomes) {
      if (outcome.producingTaskId() != null
          && outcomesByProducingTaskId.putIfAbsent(outcome.producingTaskId(), outcome) != null) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
      }
    }
    var records = document.putArray("taskRecords");
    for (OntologyTypedTaskRunner.PreparedFormalTask task : prepared) {
      String producingTaskId = OntologyTypedTaskRunner.formalProducingTaskId(task);
      OntologyTaskOutcome outcome = outcomesByProducingTaskId.get(producingTaskId);
      if (outcome == null || !task.jobKey().equals(outcome.jobKey())) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
      }
      ObjectNode row = records.addObject();
      row.put("taskId", outcome.taskId());
      row.put("producingTaskId", producingTaskId);
      row.put("jobKey", task.jobKey());
      row.put("taskKind", task.task().kind().name());
      row.put("status", outcome.status().name());
    }
    if (outcomeContractV2) {
      ArrayNode outcomeNodes = document.putArray("taskOutcomes");
      outcomes.forEach(outcome -> taskOutcome(outcomeNodes.addObject(), outcome));
    }
    ArrayNode dispositions = document.putArray("taskDispositions");
    for (OntologyScopedAssembler.TaskDisposition task : taskDispositions(workingScope, outcomes)) {
      ObjectNode row = dispositions.addObject();
      row.put("taskId", task.taskId());
      if (task.producingTaskId() == null) {
        row.putNull("producingTaskId");
      } else {
        row.put("producingTaskId", task.producingTaskId());
      }
      row.put("status", task.status().name());
      row.put("reason", task.reason());
    }
    InstalledPublication installed =
        install(
            store,
            policies,
            runId,
            persisted,
            admitted.evidencePublication(),
            3,
            "ontology-identification",
            expectedUpstreamPayloadReferences(store, storage, persisted),
            List.of(
                standalonePayload(
                    "ontology-identification.json",
                    "ONTOLOGY_IDENTIFICATION",
                    schemaVersion,
                    document,
                    policies,
                    new CanonicalJsonCodec())));
    return AnalysisRunOutput.ontology(
        new OntologyRunOutput(
            AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY,
            runId,
            status,
            admitted.sourceBasis(),
            admitted.evidencePublication(),
            installed.modulePublication()));
  }

  private static ArrayNode selectedQuestionsDocument(
      OntologyEvidenceCorpus corpus,
      OntologyScopeReader.Scope scope,
      List<OntologyTypedTaskRunner.PreparedFormalTask> prepared) {
    ArrayNode questions = JsonNodeFactory.instance.arrayNode();
    Map<String, OntologyTypedTaskRunner.PreparedFormalTask> preparedByTask = new LinkedHashMap<>();
    for (OntologyTypedTaskRunner.PreparedFormalTask task : prepared) {
      preparedByTask.put(task.task().taskId(), task);
    }
    for (OntologyScopeReader.Question question : scope.questions()) {
      ObjectNode item = questions.addObject();
      item.put("questionId", question.questionId());
      item.put("question", question.question());
      ArrayNode entries = item.putArray("entryRefs");
      question.entryRefs().forEach(entries::add);
      ArrayNode clues = item.putArray("clueRefs");
      question.clueRefs().forEach(clues::add);
      ArrayNode tasks = item.putArray("tasks");
      for (OntologyScopeReader.Task task : question.tasks()) {
        ObjectNode row = tasks.addObject();
        row.put("taskId", task.taskId());
        row.put("taskKind", task.taskKind().name());
        row.put("readingMode", task.readingMode().name());
        ArrayNode uses = row.putArray("unitUses");
        ArrayNode required = row.putArray("requiredUnitUses");
        OntologyTypedTaskRunner.PreparedFormalTask preparedTask = preparedByTask.get(task.taskId());
        if (preparedTask == null) {
          task.unitUses().forEach(use -> unitUse(uses, use));
          task.requiredUnitUses().forEach(use -> unitUse(required, use));
          continue;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (OntologyReadingPacket.PackedUnit unit : preparedTask.task().packet().units()) {
          for (String entryId : unit.entryUses()) {
            String unitRef = preparedTask.task().packet().evidenceUnitRef(unit.localRef());
            String entryRef = corpus.aliases().entryRef(entryId);
            if (seen.add(unitRef + "\u0000" + entryRef)) {
              ObjectNode use = uses.addObject();
              use.put("unitRef", unitRef);
              use.put("entryRef", entryRef);
              required.add(use.deepCopy());
            }
          }
        }
      }
    }
    return questions;
  }

  private static void unitUse(ArrayNode target, OntologyScopeReader.UnitUse use) {
    ObjectNode value = target.addObject();
    value.put("unitRef", use.unitRef());
    value.put("entryRef", use.entryRef());
  }

  private static boolean usesOntologyPayloadV2(
      CanonicalArtifactPolicyRegistry policies, String artifactType) {
    if (usesOntologyPayloadV3(policies, artifactType)) {
      return true;
    }
    try {
      policies.resolve(new ArtifactPolicyKey(artifactType, artifactTypeVersionV2(artifactType)));
      return true;
    } catch (RuntimeException unavailable) {
      return false;
    }
  }

  private static boolean hasOntologyPolicy(
      CanonicalArtifactPolicyRegistry policies, String type, String schema) {
    try {
      policies.resolve(new ArtifactPolicyKey(type, schema));
      return true;
    } catch (org.sourceanalysis.app.artifact.ArtifactStoreException unavailable) {
      if (!"ARTIFACT_POLICY_NOT_FOUND".equals(unavailable.code())) {
        throw unavailable;
      }
      return false;
    }
  }

  private static boolean usesOntologyPayloadV3(
      CanonicalArtifactPolicyRegistry policies, String type) {
    return hasOntologyPolicy(policies, type, artifactTypeVersionV2(type).replace("-v2", "-v3"));
  }

  private static String stageSchemaVersion(CanonicalArtifactPolicyRegistry policies, String type) {
    String v2 = artifactTypeVersionV2(type);
    return usesOntologyPayloadV3(policies, type)
        ? v2.replace("-v2", "-v3")
        : usesOntologyPayloadV2(policies, type) ? v2 : v2.replace("-v2", "-v1");
  }

  private static String artifactTypeVersionV2(String artifactType) {
    return switch (artifactType) {
      case "ONTOLOGY_IDENTIFICATION" -> "ontology-identification-v2";
      case "ONTOLOGY_RELATIONS" -> "ontology-relations-v2";
      case "ONTOLOGY_COVERAGE" -> "ontology-coverage-v2";
      case "ONTOLOGY_REVIEW" -> "ontology-review-v2";
      default -> throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_POLICY_INVALID");
    };
  }

  private static List<OntologyScopedAssembler.TaskDisposition> taskDispositions(
      OntologyScopeReader.Scope scope, List<OntologyTaskOutcome> outcomes) {
    Map<DeclaredTaskKey, OntologyTaskOutcome> outcomesByScopeTask = new LinkedHashMap<>();
    for (OntologyTaskOutcome outcome : outcomes) {
      if (outcomesByScopeTask.putIfAbsent(
              outcomeKey(outcome.questionId(), outcome.taskId()), outcome)
          != null) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
      }
    }
    List<OntologyScopedAssembler.TaskDisposition> dispositions = new ArrayList<>();
    for (OntologyScopeReader.Question question : scope.questions()) {
      for (OntologyScopeReader.Task scopeTask : question.tasks()) {
        OntologyTaskOutcome outcome =
            outcomesByScopeTask.get(outcomeKey(question.questionId(), scopeTask.taskId()));
        if (outcome == null
            || outcome.taskKind()
                != OntologyTaskRunner.TaskKind.valueOf(scopeTask.taskKind().name())) {
          throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
        }
        String coverageProducingTaskId =
            outcome.status() == OntologyTaskOutcome.Status.UNPROCESSED
                ? null
                : outcome.producingTaskId();
        dispositions.add(
            new OntologyScopedAssembler.TaskDisposition(
                scopeTask.taskId(),
                coverageProducingTaskId,
                OntologyScopedAssembler.TaskDispositionStatus.valueOf(outcome.status().name()),
                taskOutcomeDispositionReason(outcome)));
      }
    }
    return List.copyOf(dispositions);
  }

  private static List<OntologyTaskOutcome.TaskReference> objectDependencies(
      AnalysisRunId runId,
      List<OntologyTaskOutcome> earlierQuestionOutcomes,
      OntologyScopeReader.TaskKind taskKind) {
    if (taskKind == OntologyScopeReader.TaskKind.OBJECT) {
      return List.of();
    }
    return earlierQuestionOutcomes.stream()
        .filter(outcome -> outcome.taskKind() == OntologyTaskRunner.TaskKind.OBJECT)
        .map(
            outcome ->
                new OntologyTaskOutcome.TaskReference(
                    runId.value(), outcome.questionId(), outcome.taskId()))
        .toList();
  }

  private static boolean dependenciesReviewed(
      List<OntologyTaskOutcome> earlierQuestionOutcomes,
      List<OntologyTaskOutcome.TaskReference> dependencies) {
    Map<String, OntologyTaskOutcome> byTaskId = new LinkedHashMap<>();
    for (OntologyTaskOutcome outcome : earlierQuestionOutcomes) {
      byTaskId.put(outcome.taskId(), outcome);
    }
    return dependencies.stream()
        .allMatch(
            dependency -> {
              OntologyTaskOutcome outcome = byTaskId.get(dependency.taskId());
              return outcome != null
                  && outcome.questionId().equals(dependency.questionId())
                  && outcome.status() == OntologyTaskOutcome.Status.REVIEWED;
            });
  }

  private static List<OntologyTypedTaskRunner.FormalResult> reviewedObjectsFor(
      List<OntologyTaskOutcome.TaskReference> dependencies,
      Map<String, OntologyTypedTaskRunner.FormalResult> reviewedByTaskId) {
    List<OntologyTypedTaskRunner.FormalResult> reviewed = new ArrayList<>();
    for (OntologyTaskOutcome.TaskReference dependency : dependencies) {
      OntologyTypedTaskRunner.FormalResult result = reviewedByTaskId.get(dependency.taskId());
      if (result == null || !result.questionId().equals(dependency.questionId())) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_DEPENDENCY_INVALID");
      }
      reviewed.add(result);
    }
    return List.copyOf(reviewed);
  }

  private static OntologyTaskOutcome reviewedOutcome(
      OntologyScopeReader.Question question,
      OntologyScopeReader.Task task,
      OntologyTypedTaskRunner.PreparedFormalTask prepared,
      List<OntologyTaskOutcome.TaskReference> dependencies) {
    return new OntologyTaskOutcome(
        question.questionId(),
        task.taskId(),
        OntologyTaskRunner.TaskKind.valueOf(task.taskKind().name()),
        OntologyTaskOutcome.Status.REVIEWED,
        OntologyTypedTaskRunner.formalProducingTaskId(prepared),
        prepared.jobKey(),
        dependencies,
        null);
  }

  private static OntologyTaskOutcome rejectedOutcome(
      OntologyScopeReader.Question question,
      OntologyScopeReader.Task task,
      OntologyTypedTaskRunner.PreparedFormalTask prepared,
      List<OntologyTaskOutcome.TaskReference> dependencies,
      OntologyTaskOutcome.FailureReason reason) {
    return new OntologyTaskOutcome(
        question.questionId(),
        task.taskId(),
        OntologyTaskRunner.TaskKind.valueOf(task.taskKind().name()),
        OntologyTaskOutcome.Status.REJECTED,
        OntologyTypedTaskRunner.formalProducingTaskId(prepared),
        prepared.jobKey(),
        dependencies,
        reason);
  }

  private static OntologyTaskOutcome unprocessedOutcome(
      OntologyScopeReader.Question question,
      OntologyScopeReader.Task task,
      List<OntologyTaskOutcome.TaskReference> dependencies,
      OntologyTaskOutcome.FailureReason reason) {
    return unprocessedOutcome(question, task, null, dependencies, reason);
  }

  private static OntologyTaskOutcome unprocessedOutcome(
      OntologyScopeReader.Question question,
      OntologyScopeReader.Task task,
      OntologyTypedTaskRunner.PreparedFormalTask prepared,
      List<OntologyTaskOutcome.TaskReference> dependencies,
      OntologyTaskOutcome.FailureReason reason) {
    return new OntologyTaskOutcome(
        question.questionId(),
        task.taskId(),
        OntologyTaskRunner.TaskKind.valueOf(task.taskKind().name()),
        OntologyTaskOutcome.Status.UNPROCESSED,
        prepared == null ? null : OntologyTypedTaskRunner.formalProducingTaskId(prepared),
        prepared == null ? null : prepared.jobKey(),
        dependencies,
        reason);
  }

  private static OntologyTaskOutcome.FailureReason dependencyFailureReason(
      List<OntologyTaskOutcome.TaskReference> dependencies) {
    return new OntologyTaskOutcome.FailureReason(
        "DEPENDENCY_NOT_REVIEWED",
        OntologyTaskOutcome.Category.DEPENDENCY,
        null,
        null,
        null,
        List.of(),
        dependencies);
  }

  /** A completed formal reading reports its own bounded material disposition before preparation. */
  private static OntologyTaskOutcome.FailureReason materialReadingFailure(
      OntologyReadingCoordinator.FormalResult reading,
      List<OntologyTaskOutcome.TaskReference> dependencies) {
    if (reading.issueCode() == null || reading.issueCode().isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_READING_RESULT_INVALID");
    }
    return new OntologyTaskOutcome.FailureReason(
        reading.issueCode(),
        OntologyTaskOutcome.Category.MATERIAL,
        "READING",
        null,
        null,
        List.of(),
        dependencies);
  }

  private static OntologyTaskOutcome.FailureReason sharedFailureReason(
      RuntimeException failure,
      String stage,
      List<OntologyTaskOutcome.TaskReference> dependencies) {
    if (failure instanceof OntologyDecisionRunner.DecisionModelOutputFailure modelFailure) {
      return new OntologyTaskOutcome.FailureReason(
          modelFailure.code(),
          OntologyTaskOutcome.Category.MODEL_OUTPUT,
          modelFailure.stage() == null ? stage : modelFailure.stage(),
          null,
          null,
          List.of(),
          dependencies);
    }
    if (failure instanceof OntologyCallBudgetProvider.DispatchLimitExceeded) {
      return new OntologyTaskOutcome.FailureReason(
          OntologyCallBudgetProvider.DispatchLimitExceeded.CODE,
          OntologyTaskOutcome.Category.DISPATCH_LIMIT,
          stage,
          null,
          null,
          List.of(),
          dependencies);
    }
    if (failure instanceof StructuredModelProviderFailure providerFailure) {
      return new OntologyTaskOutcome.FailureReason(
          providerFailure.reasonCode(),
          OntologyTaskOutcome.Category.PROVIDER,
          stage,
          null,
          null,
          List.of(),
          dependencies);
    }
    if (failure instanceof OntologyJobResultStore.FormalStorageFailure storageFailure) {
      return new OntologyTaskOutcome.FailureReason(
          storageFailure.code(),
          OntologyTaskOutcome.Category.STORAGE,
          storageFailure.stage(),
          null,
          null,
          List.of(),
          dependencies);
    }
    return new OntologyTaskOutcome.FailureReason(
        "ONTOLOGY_RUNTIME_FAILURE",
        OntologyTaskOutcome.Category.UNKNOWN,
        stage,
        null,
        null,
        List.of(),
        dependencies);
  }

  /**
   * Creates the terminal observation for the canonical public installation boundary.
   *
   * <p>An artifact store publishes its own stable code, so it is the only producer that can
   * establish a storage/install disposition here. Other unexpected failures remain explicitly
   * unknown rather than being classified from exception text.
   */
  private static OntologyTaskOutcome.FailureReason terminalInstallFailureReason(
      RuntimeException failure) {
    if (failure instanceof ArtifactStoreException artifactFailure) {
      return new OntologyTaskOutcome.FailureReason(
          artifactFailure.code(),
          OntologyTaskOutcome.Category.STORAGE,
          "INSTALL",
          null,
          null,
          List.of(),
          List.of());
    }
    return new OntologyTaskOutcome.FailureReason(
        "ONTOLOGY_RUNTIME_FAILURE",
        OntologyTaskOutcome.Category.UNKNOWN,
        "INSTALL",
        null,
        null,
        List.of(),
        List.of());
  }

  private static void appendUnprocessedAfterSharedFailure(
      AnalysisRunId runId,
      OntologyScopeReader.Scope scope,
      List<OntologyTaskOutcome> outcomes,
      OntologyTaskOutcome.FailureReason sharedFailure) {
    Map<DeclaredTaskKey, OntologyTaskOutcome> existing = new LinkedHashMap<>();
    for (OntologyTaskOutcome outcome : outcomes) {
      existing.put(new DeclaredTaskKey(outcome.questionId(), outcome.taskId()), outcome);
    }
    for (OntologyScopeReader.Question question : scope.questions()) {
      List<OntologyTaskOutcome> earlierQuestionOutcomes = new ArrayList<>();
      for (OntologyScopeReader.Task task : question.tasks()) {
        DeclaredTaskKey key = new DeclaredTaskKey(question.questionId(), task.taskId());
        OntologyTaskOutcome outcome = existing.get(key);
        if (outcome == null) {
          List<OntologyTaskOutcome.TaskReference> dependencies =
              objectDependencies(runId, earlierQuestionOutcomes, task.taskKind());
          outcome =
              unprocessedOutcome(
                  question,
                  task,
                  dependencies,
                  sharedFailureWithDependencies(sharedFailure, dependencies));
          outcomes.add(outcome);
          existing.put(key, outcome);
        }
        earlierQuestionOutcomes.add(outcome);
      }
    }
  }

  private static OntologyTaskOutcome.FailureReason sharedFailureWithDependencies(
      OntologyTaskOutcome.FailureReason failure,
      List<OntologyTaskOutcome.TaskReference> dependencies) {
    return new OntologyTaskOutcome.FailureReason(
        failure.code(),
        failure.category(),
        failure.stage(),
        failure.jsonPointer(),
        failure.offendingRef(),
        failure.expectedRefs(),
        dependencies);
  }

  private static void taskOutcome(ObjectNode node, OntologyTaskOutcome outcome) {
    node.put("questionId", outcome.questionId());
    node.put("taskId", outcome.taskId());
    node.put("taskKind", outcome.taskKind().name());
    node.put("status", outcome.status().name());
    if (outcome.producingTaskId() == null) {
      node.putNull("producingTaskId");
      node.putNull("jobKey");
    } else {
      node.put("producingTaskId", outcome.producingTaskId());
      node.put("jobKey", outcome.jobKey());
    }
    taskReferences(node.putArray("dependencyTaskRefs"), outcome.dependencyTaskRefs());
    if (outcome.reason() == null) {
      node.putNull("reason");
      return;
    }
    OntologyTaskOutcome.FailureReason reason = outcome.reason();
    ObjectNode reasonNode = node.putObject("reason");
    reasonNode.put("code", reason.code());
    reasonNode.put("category", reason.category().name());
    nullableText(reasonNode, "stage", reason.stage());
    nullableText(reasonNode, "jsonPointer", reason.jsonPointer());
    nullableText(reasonNode, "offendingRef", reason.offendingRef());
    ArrayNode expectedRefs = reasonNode.putArray("expectedRefs");
    reason.expectedRefs().forEach(expectedRefs::add);
    taskReferences(reasonNode.putArray("dependencyTaskRefs"), reason.dependencyTaskRefs());
  }

  private static void taskReferences(
      ArrayNode target, List<OntologyTaskOutcome.TaskReference> references) {
    for (OntologyTaskOutcome.TaskReference reference : references) {
      ObjectNode node = target.addObject();
      node.put("runId", reference.runId());
      node.put("questionId", reference.questionId());
      node.put("taskId", reference.taskId());
    }
  }

  private static void inheritedTaskOutcomes(
      ArrayNode target, List<SelectedTaskOutcome> selectedTaskOutcomes) {
    Set<String> identities = new HashSet<>();
    for (SelectedTaskOutcome selected : selectedTaskOutcomes) {
      JsonNode source = selected.outcome();
      String questionId = source.path("questionId").asText();
      String taskId = source.path("taskId").asText();
      String identity = selected.runId() + "\u0000" + questionId + "\u0000" + taskId;
      if (questionId.isBlank() || taskId.isBlank() || !identities.add(identity)) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      ObjectNode row = target.addObject();
      row.put("runId", selected.runId());
      source
          .fields()
          .forEachRemaining(field -> row.set(field.getKey(), field.getValue().deepCopy()));
    }
  }

  private static void nullableText(ObjectNode node, String field, String value) {
    if (value == null) {
      node.putNull(field);
    } else {
      node.put(field, value);
    }
  }

  private static String taskOutcomeDispositionReason(OntologyTaskOutcome outcome) {
    return outcome.reason() == null
        ? "The exact extract and review pair was saved and reopened."
        : outcome.reason().code();
  }

  private static DeclaredTaskKey outcomeKey(String questionId, String taskId) {
    return new DeclaredTaskKey(questionId, taskId);
  }

  private record DeclaredTaskKey(String questionId, String taskId) {}

  private static RelationTaskInput relationTaskInput(
      OntologySelectionReader.Question question, SelectedFormalStage selectedIdentifications) {
    return objectSourceInput(question.objectSources(), selectedIdentifications);
  }

  private static List<String> scopeObjectRuns(OntologyScopeReader.Scope scope) {
    return scope.questions().stream()
        .flatMap(question -> question.objectSources().stream())
        .map(OntologyScopeReader.ObjectSource::identificationRun)
        .distinct()
        .sorted()
        .toList();
  }

  private static RelationTaskInput externalObjectInput(
      OntologyScopeReader.Question question, SelectedFormalStage selected) {
    if (question.objectSources().isEmpty()) {
      return new RelationTaskInput(List.of(), List.of(), null, null);
    }
    return objectSourceInput(
        question.objectSources().stream()
            .map(
                source ->
                    new OntologySelectionReader.ObjectSource(
                        source.identificationRun(), source.questionId()))
            .toList(),
        selected);
  }

  private static RelationTaskInput objectSourceInput(
      List<OntologySelectionReader.ObjectSource> objectSources,
      SelectedFormalStage selectedIdentifications) {
    Map<SelectedFormalResultKey, OntologyTypedTaskRunner.FormalResult> reviewedByOwnerProducer =
        selectedFormalResultsByOwnerProducer(selectedIdentifications.results());
    List<OntologyTaskOutcome.TaskReference> dependencies = new ArrayList<>();
    List<RelationSourceObject> sourceObjects = new ArrayList<>();
    for (OntologySelectionReader.ObjectSource source : objectSources) {
      List<SelectedTaskOutcome> questionOutcomes =
          selectedIdentifications.taskOutcomes().stream()
              .filter(
                  outcome ->
                      source.identificationRun().equals(outcome.runId())
                          && source
                              .questionId()
                              .equals(outcome.outcome().path("questionId").asText()))
              .toList();
      List<SelectedTaskOutcome> sourceOutcomes =
          questionOutcomes.stream()
              .filter(outcome -> "OBJECT".equals(outcome.outcome().path("taskKind").asText()))
              .toList();
      if (sourceOutcomes.isEmpty()) {
        boolean missingQuestion = questionOutcomes.isEmpty();
        return new RelationTaskInput(
            List.of(),
            List.of(),
            null,
            new OntologyTaskOutcome.FailureReason(
                missingQuestion ? "OBJECT_SOURCE_UNAVAILABLE" : "DEPENDENCY_NOT_REVIEWED",
                missingQuestion
                    ? OntologyTaskOutcome.Category.SOURCE
                    : OntologyTaskOutcome.Category.DEPENDENCY,
                missingQuestion ? "SOURCE" : "DEPENDENCY",
                null,
                source.identificationRun() + ":" + source.questionId(),
                List.of(),
                List.of()));
      }
      for (SelectedTaskOutcome sourceOutcome : sourceOutcomes) {
        JsonNode outcome = sourceOutcome.outcome();
        String taskId = sourceOutcome.taskId();
        dependencies.add(
            new OntologyTaskOutcome.TaskReference(
                sourceOutcome.runId(), source.questionId(), taskId));
        sourceObjects.add(new RelationSourceObject(source, sourceOutcome));
      }
    }
    List<OntologyTaskOutcome.TaskReference> frozenDependencies = List.copyOf(dependencies);
    if (sourceObjects.stream()
        .anyMatch(item -> !"REVIEWED".equals(item.outcome().outcome().path("status").asText()))) {
      return new RelationTaskInput(
          List.of(), frozenDependencies, null, dependencyFailureReason(frozenDependencies));
    }
    List<OntologyTypedTaskRunner.FormalResult> catalog = new ArrayList<>();
    for (RelationSourceObject sourceObject : sourceObjects) {
      String producingTaskId = sourceObject.outcome().outcome().path("producingTaskId").asText();
      OntologyTypedTaskRunner.FormalResult reviewed =
          reviewedByOwnerProducer.get(
              new SelectedFormalResultKey(sourceObject.outcome().runId(), producingTaskId));
      if (reviewed == null || reviewed.kind() != OntologyTaskRunner.TaskKind.OBJECT) {
        return new RelationTaskInput(
            List.of(),
            frozenDependencies,
            null,
            new OntologyTaskOutcome.FailureReason(
                "OBJECT_SOURCE_UNAVAILABLE",
                OntologyTaskOutcome.Category.SOURCE,
                "SOURCE",
                null,
                producingTaskId,
                List.of(),
                frozenDependencies));
      }
      catalog.add(reviewed);
    }
    return new RelationTaskInput(
        List.copyOf(catalog),
        frozenDependencies,
        relationTaskDependencyFingerprint(sourceObjects, catalog),
        null);
  }

  private static String relationTaskDependencyFingerprint(
      List<RelationSourceObject> sourceObjects,
      List<OntologyTypedTaskRunner.FormalResult> catalog) {
    if (sourceObjects.size() != catalog.size() || sourceObjects.isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    ObjectNode frozen = JsonNodeFactory.instance.objectNode();
    frozen.put("schemaVersion", "ontology-o2-object-sources-v1");
    ArrayNode objects = frozen.putArray("objects");
    for (int index = 0; index < sourceObjects.size(); index++) {
      RelationSourceObject sourceObject = sourceObjects.get(index);
      SelectedTaskOutcome outcome = sourceObject.outcome();
      OntologyTypedTaskRunner.FormalResult reviewed = catalog.get(index);
      ObjectNode object = objects.addObject();
      object.put("identificationRun", sourceObject.source().identificationRun());
      object.put("questionId", sourceObject.source().questionId());
      object.put("taskId", outcome.taskId());
      object.put("producingTaskId", outcome.outcome().path("producingTaskId").asText());
      object.put("jobKey", outcome.outcome().path("jobKey").asText());
      ObjectNode identity = object.putObject("definitionIdentity");
      identity.put("corpusIdentity", reviewed.identity().corpusIdentity());
      identity.put("producingTaskId", reviewed.identity().producingTaskId());
      identity.put("reviewVersion", reviewed.identity().reviewVersion());
    }
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    return "ontology-o2-object-sources-v1-"
        + sha256(json.encodeCanonical(frozen).copyToByteArray());
  }

  private static OntologyTaskOutcome reviewedRelationOutcome(
      OntologySelectionReader.Question question,
      OntologyTypedTaskRunner.PreparedFormalTask prepared,
      List<OntologyTaskOutcome.TaskReference> dependencies) {
    return new OntologyTaskOutcome(
        question.questionId(),
        question.taskId(),
        OntologyTaskRunner.TaskKind.RELATE,
        OntologyTaskOutcome.Status.REVIEWED,
        OntologyTypedTaskRunner.formalProducingTaskId(prepared),
        prepared.jobKey(),
        dependencies,
        null);
  }

  private static OntologyTaskOutcome rejectedRelationOutcome(
      OntologySelectionReader.Question question,
      OntologyTypedTaskRunner.PreparedFormalTask prepared,
      List<OntologyTaskOutcome.TaskReference> dependencies,
      OntologyTaskOutcome.FailureReason reason) {
    return new OntologyTaskOutcome(
        question.questionId(),
        question.taskId(),
        OntologyTaskRunner.TaskKind.RELATE,
        OntologyTaskOutcome.Status.REJECTED,
        OntologyTypedTaskRunner.formalProducingTaskId(prepared),
        prepared.jobKey(),
        dependencies,
        reason);
  }

  private static OntologyTaskOutcome unprocessedRelationOutcome(
      OntologySelectionReader.Question question,
      OntologyTypedTaskRunner.PreparedFormalTask prepared,
      List<OntologyTaskOutcome.TaskReference> dependencies,
      OntologyTaskOutcome.FailureReason reason) {
    return new OntologyTaskOutcome(
        question.questionId(),
        question.taskId(),
        OntologyTaskRunner.TaskKind.RELATE,
        OntologyTaskOutcome.Status.UNPROCESSED,
        prepared == null ? null : OntologyTypedTaskRunner.formalProducingTaskId(prepared),
        prepared == null ? null : prepared.jobKey(),
        dependencies,
        reason);
  }

  private static void appendUnprocessedRelationOutcomes(
      OntologySelectionReader.Selection selection,
      SelectedFormalStage selectedIdentifications,
      List<OntologyTaskOutcome> outcomes,
      OntologyTaskOutcome.FailureReason sharedFailure) {
    Set<String> completedTaskIds = new HashSet<>();
    outcomes.forEach(outcome -> completedTaskIds.add(outcome.taskId()));
    for (OntologySelectionReader.Question question : selection.questions()) {
      if (completedTaskIds.add(question.taskId())) {
        RelationTaskInput input = relationTaskInput(question, selectedIdentifications);
        List<OntologyTaskOutcome.TaskReference> dependencies = input.dependencies();
        outcomes.add(
            unprocessedRelationOutcome(
                question,
                null,
                dependencies,
                sharedFailureWithDependencies(sharedFailure, dependencies)));
      }
    }
  }

  private static List<OntologyScopedAssembler.TaskDisposition> relationTaskDispositions(
      OntologySelectionReader.Selection selection, List<OntologyTaskOutcome> outcomes) {
    Map<String, OntologyTaskOutcome> byTaskId = new LinkedHashMap<>();
    for (OntologyTaskOutcome outcome : outcomes) {
      if (outcome.taskKind() != OntologyTaskRunner.TaskKind.RELATE
          || byTaskId.putIfAbsent(outcome.taskId(), outcome) != null) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
      }
    }
    List<OntologyScopedAssembler.TaskDisposition> dispositions = new ArrayList<>();
    for (OntologySelectionReader.Question question : selection.questions()) {
      OntologyTaskOutcome outcome = byTaskId.get(question.taskId());
      if (outcome == null || !question.questionId().equals(outcome.questionId())) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
      }
      dispositions.add(
          new OntologyScopedAssembler.TaskDisposition(
              question.taskId(),
              outcome.status() == OntologyTaskOutcome.Status.UNPROCESSED
                  ? null
                  : outcome.producingTaskId(),
              OntologyScopedAssembler.TaskDispositionStatus.valueOf(outcome.status().name()),
              taskOutcomeDispositionReason(outcome)));
    }
    return List.copyOf(dispositions);
  }

  private record RelationTaskInput(
      List<OntologyTypedTaskRunner.FormalResult> catalog,
      List<OntologyTaskOutcome.TaskReference> dependencies,
      String dependencyFingerprint,
      OntologyTaskOutcome.FailureReason failure) {}

  private record RelationSourceObject(
      OntologySelectionReader.ObjectSource source, SelectedTaskOutcome outcome) {}

  private static List<OntologyScopedAssembler.TaskDisposition> relationTaskDispositions(
      OntologySelectionReader.Selection selection,
      List<OntologyTypedTaskRunner.PreparedFormalTask> prepared,
      List<OntologyTypedTaskRunner.FormalResult> completed) {
    Map<String, OntologyTypedTaskRunner.PreparedFormalTask> preparedByTask = new LinkedHashMap<>();
    for (OntologyTypedTaskRunner.PreparedFormalTask task : prepared) {
      if (preparedByTask.putIfAbsent(task.task().taskId(), task) != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTION_REFERENCE_INVALID");
      }
    }
    Set<String> reviewed = new HashSet<>();
    for (OntologyTypedTaskRunner.FormalResult result : completed) {
      reviewed.add(result.identity().producingTaskId());
    }
    List<OntologyScopedAssembler.TaskDisposition> dispositions = new ArrayList<>();
    for (OntologySelectionReader.Question question : selection.questions()) {
      OntologyTypedTaskRunner.PreparedFormalTask task = preparedByTask.get(question.taskId());
      if (task == null) {
        dispositions.add(
            new OntologyScopedAssembler.TaskDisposition(
                question.taskId(),
                null,
                OntologyScopedAssembler.TaskDispositionStatus.UNPROCESSED,
                "No immutable relation task was prepared after the saved runtime stopped before"
                    + " this selected question."));
        continue;
      }
      String producingTaskId = OntologyTypedTaskRunner.formalProducingTaskId(task);
      boolean wasReviewed = reviewed.contains(producingTaskId);
      dispositions.add(
          new OntologyScopedAssembler.TaskDisposition(
              question.taskId(),
              producingTaskId,
              wasReviewed
                  ? OntologyScopedAssembler.TaskDispositionStatus.REVIEWED
                  : OntologyScopedAssembler.TaskDispositionStatus.REJECTED,
              wasReviewed
                  ? "The exact saved relation extract and review pair was reopened."
                  : "The prepared relation task did not reach a reviewed result in the saved"
                      + " runtime."));
    }
    return List.copyOf(dispositions);
  }

  private static OntologyRunOutput.Status publicationStatus(ObjectNode coverage) {
    return switch (coverage.path("coverageStatus").asText()) {
      case "COMPLETE" -> OntologyRunOutput.Status.COMPLETED;
      case "INCOMPLETE", "UNDETERMINED" -> OntologyRunOutput.Status.PARTIAL;
      default -> throw new IllegalArgumentException("ONTOLOGY_COVERAGE_INVALID");
    };
  }

  private static AnalysisRunRequest prepareRequest(
      OntologyConfiguration configuration,
      CanonicalArtifactPolicyRegistry ontologyPolicies,
      AdmittedEvidence admitted) {
    AnalysisRunRequest.TechnicalAnalysisInputs technical = admitted.technicalInputs();
    return AnalysisRunRequest.ontology(
        admitted.sourceBasis(),
        new AnalysisRunRequest.OntologyInputs(
            AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY,
            admitted.evidencePublication(),
            SourceAnalysisExecution.contentReference(
                "ontology-profile", configuration.canonicalConfiguration()),
            technical.resourceBudgetRef(),
            technical.schemaBundleRef(),
            technical.toolchainRef(),
            SourceAnalysisExecution.artifactReference(ontologyPolicies.reference()),
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of()));
  }

  private static AnalysisRunOutput executePrepare(
      AnalysisStepExecutionRequest execution,
      RunStoreHandle store,
      OntologyConfiguration configuration,
      CanonicalArtifactPolicyRegistry ontologyPolicies,
      AdmittedEvidence admitted) {
    if (execution.intent() != AnalysisExecutionIntent.PREPARE_ONTOLOGY
        || execution.upstreamRunId() != null
        || execution.exactMaterialId() != null) {
      throw new IllegalArgumentException("ONTOLOGY_EXECUTION_INPUT_INVALID");
    }
    AnalysisRunRequest persisted =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, execution.runId()).request();
    if (persisted.requestKind() != AnalysisRunRequest.RequestKind.ONTOLOGY
        || persisted.ontologyInputs().operation()
            != AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY
        || !persisted.selectedSourceBasis().equals(admitted.sourceBasis())
        || !persisted
            .ontologyInputs()
            .evidencePublication()
            .equals(admitted.evidencePublication())) {
      throw new IllegalArgumentException("ONTOLOGY_EXECUTION_INPUT_INVALID");
    }
    String projectionRuleVersion = projectionRuleVersion(ontologyPolicies);
    AdmittedEvidence prepared = admitted;
    CanonicalModulePayload schemaPayload = null;
    ArtifactReference schemaReference = null;
    if (!configuration.schemaSources().isEmpty()) {
      ObjectNode schemaEvidence =
          OntologySchemaEvidence.project(
              admitted.sourceTexts(), configuration.schemaSources(), admitted.corpus());
      schemaPayload =
          standalonePayload(
              "schema-evidence.json",
              "SCHEMA_EVIDENCE",
              OntologySchemaEvidence.SCHEMA_VERSION,
              schemaEvidence,
              ontologyPolicies,
              new CanonicalJsonCodec());
      schemaReference = payloadReference(schemaPayload);
      prepared =
          admitted.withCorpus(
              admitted.corpus().withSchemaEvidence(schemaEvidence, admitted.sourceTexts()));
    }
    try {
      prepared =
          restoreSavedProjection(
              configuration, store, new CanonicalJsonCodec(), projectionRuleVersion, prepared);
    } catch (RuntimeException sourceFailure) {
      recordPrepareSourceFailure(store, configuration.storage(), execution.runId());
      throw sourceFailure;
    }
    List<CanonicalModulePayload> payloads = new ArrayList<>();
    payloads.add(
        corpusPayload(
            prepared,
            persisted,
            configuration.reading(),
            execution.runId(),
            ontologyPolicies,
            schemaReference,
            projectionRuleVersion,
            new CanonicalJsonCodec()));
    if (schemaPayload != null) {
      payloads.add(schemaPayload);
    }
    InstalledPublication installed =
        install(
            store,
            ontologyPolicies,
            execution.runId(),
            persisted,
            admitted.evidencePublication(),
            2,
            "ontology-corpus",
            admitted.evidencePayloadReferences(),
            List.copyOf(payloads));
    return AnalysisRunOutput.ontology(
        new OntologyRunOutput(
            AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY,
            execution.runId(),
            OntologyRunOutput.Status.COMPLETED,
            admitted.sourceBasis(),
            admitted.evidencePublication(),
            installed.modulePublication()));
  }

  /**
   * Records the producer-owned O0 source failure after queueing, with no invented task or receipt.
   */
  private static void recordPrepareSourceFailure(
      RunStoreHandle store, OntologyConfiguration.Storage storage, AnalysisRunId runId) {
    new OntologyJobResultStore(
            SourceAnalysisExecution.checkedDirectory(storage.root(), "ontology-journal"), runId)
        .recordFormalRuntimeObservation(
            new OntologyCallBudgetProvider.RuntimeCounts(0, 0, 0, 0),
            new OntologyTaskOutcome.FailureReason(
                "ONTOLOGY_PREPARED_SOURCE_BODY_INVALID",
                OntologyTaskOutcome.Category.SOURCE,
                "PREPARE",
                null,
                null,
                List.of(),
                List.of()));
  }

  private static AdmittedEvidence admitEvidence(
      OntologyConfiguration configuration,
      RunStoreHandle store,
      CanonicalJsonCodec json,
      AnalysisRunId evidenceRunId) {
    AnalysisRunRequest r4Request =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, evidenceRunId).request();
    RunInspection r4Inspection =
        new LocalRepositoryAnalysisAgent(store).inspect(evidenceRunId.value());
    AnalysisRunOutput r4Output = r4Inspection.output();
    if (r4Request.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
        || r4Output == null
        || r4Output.technicalOutput() == null) {
      throw new IllegalArgumentException("ONTOLOGY_EVIDENCE_RUN_INVALID");
    }
    TechnicalRunOutput technical = r4Output.technicalOutput();
    if (technical.operation() != AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS
        || technical.readingMaterials() == null) {
      throw new IllegalArgumentException("ONTOLOGY_EVIDENCE_RUN_INVALID");
    }
    AnalysisRunRequest.TechnicalAnalysisInputs inputs = r4Request.technicalAnalysisInputs();
    ArtifactControls controls =
        new ArtifactControls(
            inputs.toolchainRef().sha256(),
            inputs.technicalProfileRef().sha256(),
            inputs.schemaBundleRef().sha256(),
            null,
            new org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference(
                inputs.artifactPolicyRegistryRef().artifactId(),
                inputs.artifactPolicyRegistryRef().sha256()));
    CanonicalArtifactPolicyRegistry evidencePolicies =
        SourceAnalysisExecution.loadPolicies(
            configuration.storage().evidencePolicyRegistry(), json);
    if (!inputs
        .artifactPolicyRegistryRef()
        .equals(SourceAnalysisExecution.artifactReference(evidencePolicies.reference()))) {
      throw new IllegalArgumentException("ONTOLOGY_EVIDENCE_POLICY_MISMATCH");
    }
    ArtifactStoreLimits evidenceLimits =
        r4Request.technicalAnalysisInputs().operation()
                    == AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS
                && r4Request.technicalAnalysisInputs().wireVersion()
                    == AnalysisRunRequest.TechnicalWireVersion.V5
            ? TechnicalAnalysisConfiguredRuntime.entryEvidenceStoreLimits(
                r4Request.technicalAnalysisInputs().entryEvidenceProfile())
            : STORE_LIMITS;
    FileSystemCanonicalModuleArtifactStore evidenceModules =
        new FileSystemCanonicalModuleArtifactStore(store, json, evidencePolicies, evidenceLimits);
    FileSystemCanonicalAnalysisStepArtifactStore evidenceSteps =
        new FileSystemCanonicalAnalysisStepArtifactStore(
            store, json, evidencePolicies, evidenceLimits);
    EntryEvidenceReader evidenceReader = new EntryEvidenceReader(evidenceModules, evidenceSteps);
    SelectedSourceBasis sourceBasis = r4Request.selectedSourceBasis();
    var evidenceStep = evidenceSteps.reopen(technical.readingMaterials());
    if (!(evidenceStep.receipt().publicationProvenance()
        instanceof AnalysisStepPublisherModuleProvenance provenance)) {
      throw new IllegalArgumentException("ONTOLOGY_EVIDENCE_RUN_INVALID");
    }
    ReopenedModulePublication evidenceModule =
        evidenceModules.reopen(provenance.publisherSpecificationModuleReference());
    if (EntryEvidencePublisher.V2_MODULE_VERSION.equals(evidenceModule.receipt().moduleVersion())) {
      evidenceReader.reopenV2(technical.readingMaterials(), evidenceRunId, sourceBasis, controls);
    } else if (EntryEvidencePublisher.MODULE_VERSION.equals(
        evidenceModule.receipt().moduleVersion())) {
      EntryEvidenceReader.Directory directory = evidenceReader.reopen(technical.readingMaterials());
      JsonNode header = json.parseCanonical(directory.indexCanonicalJson()).path("header");
      if (!evidenceRunId.equals(technical.readingMaterials().address().runId())
          || !controls.equals(evidenceStep.receipt().controls())
          || !sourceBasisNode(sourceBasis).equals(header.path("sourceBasis"))) {
        throw new IllegalArgumentException("ONTOLOGY_EVIDENCE_RUN_INVALID");
      }
    } else {
      throw new IllegalArgumentException("ONTOLOGY_EVIDENCE_RUN_INVALID");
    }
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.open(evidenceReader, technical.readingMaterials());
    CanonicalArtifactPolicyRegistry sourcePolicies =
        SourceAnalysisExecution.loadPolicies(
            configuration.storage().sourcePreparationPolicyRegistry(), json);
    SourcePreparationReader sourceReader =
        new SourcePreparationReader(
            new FileSystemCanonicalModuleArtifactStore(store, json, sourcePolicies, STORE_LIMITS),
            new FileSystemCanonicalAnalysisStepArtifactStore(
                store, json, sourcePolicies, STORE_LIMITS),
            new PreparedSourceArchive(configuration.storage().preparedSourceArchive()));
    var savedPreparation = sourceReader.reopen(sourceBasis.preparedSource().publication());
    if (!SelectedSourceBasisProjector.fromPrepared(savedPreparation).equals(sourceBasis)) {
      throw new IllegalArgumentException("ONTOLOGY_SOURCE_BASIS_MISMATCH");
    }
    VerifiedSourceTextSet sourceTexts =
        new PreparedVerifiedSourceTextReader(
                sourceReader,
                new PreparedSourceArchive(configuration.storage().preparedSourceArchive()))
            .reopen(
                new VerifiedSourceInventoryReference(sourceBasis.preparedSource().publication()));
    return new AdmittedEvidence(
        sourceBasis,
        technical.readingMaterials(),
        inputs,
        corpus,
        sourceTexts,
        strictReferences(
            evidenceModule.payloads().stream()
                .map(
                    payload ->
                        new ArtifactReference(
                            payload.descriptor().artifactId(), payload.descriptor().sha256()))
                .toList()));
  }

  private static InstalledPublication install(
      RunStoreHandle store,
      CanonicalArtifactPolicyRegistry policies,
      AnalysisRunId runId,
      AnalysisRunRequest request,
      AnalysisStepPublicationReference evidence,
      int moduleNumber,
      String moduleKey,
      List<ArtifactReference> upstreamArtifacts,
      List<CanonicalModulePayload> payloads) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    FileSystemCanonicalModuleArtifactStore modules =
        new FileSystemCanonicalModuleArtifactStore(store, json, policies, STORE_LIMITS);
    FileSystemCanonicalAnalysisStepArtifactStore steps =
        new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, STORE_LIMITS);
    ArtifactControls controls = ontologyControls(request);
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.REPOSITORY_KNOWLEDGE, moduleNumber, moduleKey);
    InstalledModulePublication module =
        modules.install(
            new ModuleInstallRequest(
                address,
                producerVersion(moduleKey, payloads),
                strictReferences(upstreamArtifacts),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                payloads));
    List<CanonicalAnalysisStepPayload> semantic =
        payloads.stream()
            .map(
                payload ->
                    new CanonicalAnalysisStepPayload(
                        payload.fileName(),
                        payload.artifactType(),
                        payload.schemaVersion(),
                        payload.artifactId(),
                        payload.mediaType(),
                        payload.canonicalUtf8()))
            .toList();
    InstalledAnalysisStepPublication step =
        steps.install(
            new AnalysisStepInstallRequest(
                new AnalysisStepPublicationAddress(runId, AnalysisStepKey.REPOSITORY_KNOWLEDGE),
                new AnalysisStepPublisherModuleProvenance(module.reference()),
                List.of(evidence),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                semantic,
                null));
    return new InstalledPublication(module.reference(), step.reference());
  }

  private static String producerVersion(String moduleKey, List<CanonicalModulePayload> payloads) {
    boolean current =
        payloads.stream()
            .anyMatch(
                payload ->
                    switch (moduleKey) {
                      case "ontology-corpus" ->
                          "ontology-corpus-v2".equals(payload.schemaVersion());
                      case "ontology-identification" ->
                          "ontology-identification-v3".equals(payload.schemaVersion());
                      case "ontology-relations" ->
                          "ontology-relations-v3".equals(payload.schemaVersion());
                      case "ontology-publisher" -> "ontology-v2".equals(payload.schemaVersion());
                      default -> false;
                    });
    return current ? ("ontology-corpus".equals(moduleKey) ? "v2" : "v3") : "v1";
  }

  private static List<ArtifactReference> upstreamPayloadReferences(
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      List<org.sourceanalysis.app.artifact.ModulePublicationReference> publications) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    List<ArtifactReference> references = new ArrayList<>();
    for (org.sourceanalysis.app.artifact.ModulePublicationReference publication : publications) {
      if (publication == null || publication.address() == null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      AnalysisRunRequest owner =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, publication.address().runId())
              .request();
      if (owner.requestKind() != AnalysisRunRequest.RequestKind.ONTOLOGY) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      CanonicalArtifactPolicyRegistry policies =
          exactSavedOntologyPolicies(
              storage, owner.ontologyInputs().artifactPolicyRegistryRef(), json);
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, STORE_LIMITS);
      ReopenedModulePublication reopened = modules.reopen(publication);
      reopened
          .payloads()
          .forEach(
              payload ->
                  references.add(
                      new ArtifactReference(
                          payload.descriptor().artifactId(), payload.descriptor().sha256())));
    }
    return strictReferences(references);
  }

  /** Resolves a saved ontology owner only through the active and explicitly declared policies. */
  private static CanonicalArtifactPolicyRegistry exactSavedOntologyPolicies(
      OntologyConfiguration.Storage storage,
      ArtifactReference savedReference,
      CanonicalJsonCodec json) {
    return exactSavedArtifactPolicies(
        storage.ontologyPolicyRegistry(),
        storage.upstreamArtifactPolicyRegistries(),
        savedReference,
        json);
  }

  /**
   * Resolves a saved artifact owner from one active registry and the explicitly configured historic
   * registry paths. The caller names the active family; there is no file-name fallback or scan.
   */
  private static CanonicalArtifactPolicyRegistry exactSavedArtifactPolicies(
      Path activePolicyRegistry,
      List<Path> upstreamPolicyRegistries,
      ArtifactReference savedReference,
      CanonicalJsonCodec json) {
    if (activePolicyRegistry == null
        || upstreamPolicyRegistries == null
        || savedReference == null) {
      throw new IllegalArgumentException("ONTOLOGY_POLICY_MISMATCH");
    }
    List<Path> configured = new ArrayList<>();
    configured.add(activePolicyRegistry);
    configured.addAll(upstreamPolicyRegistries);
    CanonicalArtifactPolicyRegistry matched = null;
    for (Path path : configured) {
      CanonicalArtifactPolicyRegistry candidate = SourceAnalysisExecution.loadPolicies(path, json);
      ArtifactReference candidateReference =
          SourceAnalysisExecution.artifactReference(candidate.reference());
      if (!savedReference.equals(candidateReference)) {
        continue;
      }
      if (matched != null) {
        throw new IllegalArgumentException("ONTOLOGY_POLICY_MISMATCH");
      }
      matched = candidate;
    }
    if (matched == null) {
      throw new IllegalArgumentException("ONTOLOGY_POLICY_MISMATCH");
    }
    return matched;
  }

  private static ArtifactControls technicalControls(AnalysisRunRequest request) {
    AnalysisRunRequest.TechnicalAnalysisInputs inputs = request.technicalAnalysisInputs();
    return new ArtifactControls(
        inputs.toolchainRef().sha256(),
        inputs.technicalProfileRef().sha256(),
        inputs.schemaBundleRef().sha256(),
        null,
        new org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference(
            inputs.artifactPolicyRegistryRef().artifactId(),
            inputs.artifactPolicyRegistryRef().sha256()));
  }

  private static List<org.sourceanalysis.app.artifact.ModulePublicationReference>
      concatenatePublications(
          List<org.sourceanalysis.app.artifact.ModulePublicationReference> first,
          List<org.sourceanalysis.app.artifact.ModulePublicationReference> second) {
    List<org.sourceanalysis.app.artifact.ModulePublicationReference> all = new ArrayList<>(first);
    all.addAll(second);
    return List.copyOf(all);
  }

  private static List<ArtifactReference> strictReferences(List<ArtifactReference> references) {
    return references.stream()
        .sorted(
            Comparator.comparing((ArtifactReference reference) -> reference.artifactId().value())
                .thenComparing(reference -> reference.sha256().value()))
        .distinct()
        .toList();
  }

  private static ArtifactControls ontologyControls(AnalysisRunRequest request) {
    AnalysisRunRequest.OntologyInputs input = request.ontologyInputs();
    return new ArtifactControls(
        input.toolchainRef().sha256(),
        input.ontologyProfileRef().sha256(),
        input.schemaBundleRef().sha256(),
        input.promptBundleRef() == null ? null : input.promptBundleRef().sha256(),
        new org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference(
            input.artifactPolicyRegistryRef().artifactId(),
            input.artifactPolicyRegistryRef().sha256()));
  }

  private static ObjectNode readCanonical(Path path, CanonicalJsonCodec json) {
    try {
      JsonNode parsed = json.parseStrictJson(ImmutableBytes.copyOf(Files.readAllBytes(path)));
      if (!(parsed instanceof ObjectNode object)) {
        throw new IllegalArgumentException("ONTOLOGY_SCOPE_INVALID");
      }
      return object;
    } catch (java.io.IOException unreadable) {
      throw new IllegalArgumentException("ONTOLOGY_SCOPE_INVALID", unreadable);
    }
  }

  /** Keeps public runtime observations to stable failure codes rather than exception detail. */
  private static String publicFailureCode(RuntimeException failure) {
    if (failure instanceof StructuredModelProviderFailure providerFailure) {
      return stableStructuredReasonCode(providerFailure.reasonCode());
    }
    String code = failure.getMessage();
    return stableOntologyCode(code);
  }

  private static String stableOntologyCode(String code) {
    return code != null && code.matches("ONTOLOGY_[A-Z0-9_]+") ? code : "ONTOLOGY_RUNTIME_FAILED";
  }

  private static String stableStructuredReasonCode(String code) {
    return code != null && code.matches("[A-Z][A-Z0-9_]*") ? code : "ONTOLOGY_RUNTIME_FAILED";
  }

  private static Path ontologyJournal(OntologyConfiguration configuration) {
    return SourceAnalysisExecution.checkedDirectory(
        configuration.storage().root(), "ontology-journal");
  }

  private static String promptFor(
      OntologyConfiguration configuration, OntologyScopeReader.TaskKind taskKind) {
    return configuration.prompts().get(taskKind.name().toLowerCase(java.util.Locale.ROOT));
  }

  private static ModelBinding modelBinding(
      OntologyConfiguration configuration, AnalysisRunRequest.OntologyOperation operation) {
    String route =
        switch (operation) {
          case IDENTIFY_ONTOLOGY -> "extract";
          case RELATE_ONTOLOGY -> "relate";
          case PREPARE_ONTOLOGY, PUBLISH_ONTOLOGY ->
              throw new IllegalArgumentException("ONTOLOGY_MODEL_ROUTE_INVALID");
        };
    return modelBinding(configuration, route);
  }

  private static ModelBinding modelBinding(OntologyConfiguration configuration, String route) {
    configuration.requireModelRoute(route);
    String key = configuration.models().routing().get(route).get(0);
    ModelJobProviderConfiguration configured = configuration.models().providers().get(key);
    ModelRuntimeIdentityV1 identity =
        new ModelRuntimeIdentityV1(
            configured.kind() == ModelProviderKind.CODEX_SUBSCRIPTION
                ? "codex_subscription"
                : "openai_api",
            configured.model(),
            configured.reasoningEffort(),
            "read-only");
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    ObjectNode savedDeclaration =
        (ObjectNode)
            json.parseCanonical(json.encodeCanonical(configured.normalizedNonSecretNode()));
    OntologyTypedTaskRunner.FormalModelDeclaration declaration =
        new OntologyTypedTaskRunner.FormalModelDeclaration(
            key, configured.quotaScope(), identity, savedDeclaration);
    return new ModelBinding(
        declaration,
        configured,
        SourceAnalysisExecution.contentReference(
            "ontology-model",
            new CanonicalJsonCodec().encodeCanonical(configured.normalizedNonSecretNode())));
  }

  private static void validateProviderBeforeQueue(
      ModelBinding model,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory) {
    if (providerFactory != null) {
      return;
    }
    if (model.configured().kind() == ModelProviderKind.CODEX_SUBSCRIPTION
        && model.configured().executable() == null) {
      throw new IllegalArgumentException("ONTOLOGY_PROVIDER_EXECUTABLE_REQUIRED");
    }
    model.configured().validateExecutionEnvironment();
  }

  private static StructuredModelProvider provider(
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory,
      OntologyConfiguration configuration,
      ModelBinding model,
      AnalysisRunId runId) {
    if (providerFactory != null) {
      return Objects.requireNonNull(
          providerFactory.apply(model.declaration()), "ontology test provider");
    }
    String environment = model.configured().authentication().environmentNames().get(0);
    return SourceAnalysisExecution.journaledProvider(
        model.configured(),
        environment,
        SourceAnalysisExecution.providerJournalDirectory(
            ontologyJournal(configuration), model.declaration().key(), runId),
        model.declaration().expectedRuntimeIdentity());
  }

  /** Creates a task-local Provider only after the shared run budget accepts its first dispatch. */
  private static StructuredModelProvider lazyProvider(
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory,
      OntologyConfiguration configuration,
      ModelBinding model,
      AnalysisRunId runId) {
    return new StructuredModelProvider() {
      private StructuredModelProvider delegate;

      @Override
      public synchronized org.sourceanalysis.app.adapter.provider.StructuredModelResponse generate(
          org.sourceanalysis.app.adapter.provider.StructuredModelRequest request) {
        if (delegate == null) {
          try {
            delegate = provider(providerFactory, configuration, model, runId);
          } catch (RuntimeException initializationFailure) {
            // Construction/preflight occurs before a Provider has accepted this generated call.
            // Preserve that typed local fact; callers must not infer a remote request from it.
            throw new StructuredModelProviderFailure(
                "PROVIDER_INITIALIZATION_FAILED",
                false,
                false,
                "ONTOLOGY_PROVIDER_INITIALIZATION_FAILED",
                initializationFailure);
          }
        }
        return delegate.generate(request);
      }
    };
  }

  private static CanonicalModulePayload corpusPayload(
      AdmittedEvidence admitted,
      AnalysisRunRequest request,
      OntologyConfiguration.Reading reading,
      AnalysisRunId runId,
      CanonicalArtifactPolicyRegistry policies,
      ArtifactReference schemaEvidenceReference,
      String projectionRuleVersion,
      CanonicalJsonCodec json) {
    ObjectNode corpus = JsonNodeFactory.instance.objectNode();
    String schemaVersion =
        PROJECTION_RULE_V3.equals(projectionRuleVersion)
            ? "ontology-corpus-v2"
            : "ontology-corpus-v1";
    corpus.put("schemaVersion", schemaVersion);
    corpus.put("contentSourceIdentity", admitted.corpus().sourceIdentity());
    corpus.put("evidenceRunId", admitted.evidencePublication().address().runId().value());
    corpus.put("ownerRunId", runId.value());
    corpus.set("selectedSourceBasis", sourceBasisNode(admitted.sourceBasis()));
    corpus.set("evidencePublication", analysisStepPublicationNode(admitted.evidencePublication()));
    corpus.set("preparationBindings", preparationBindings(admitted.technicalInputs()));
    corpus.set("preparationControls", preparationControls(reading));
    ObjectNode schemaEvidence = corpus.putObject("schemaEvidence");
    if (schemaEvidenceReference == null) {
      schemaEvidence.put("status", "DISABLED");
    } else {
      schemaEvidence.put("status", "CONFIGURED");
      schemaEvidence.set("reference", artifactReferenceNode(schemaEvidenceReference));
    }
    if (!validProjectionRuleVersion(projectionRuleVersion)) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
    }
    corpus.put("projectionRuleVersion", projectionRuleVersion);
    corpus.set("aliases", json.parseCanonical(admitted.corpus().aliases().canonicalMapping()));
    corpus.put("corpusIdentity", formalCorpusIdentity(corpus, json));
    return standalonePayload(
        "ontology-corpus.json", "ONTOLOGY_CORPUS", schemaVersion, corpus, policies, json);
  }

  private static ArtifactReference payloadReference(CanonicalModulePayload payload) {
    return new ArtifactReference(
        payload.artifactId(),
        Sha256Digest.parse(sha256(payload.canonicalUtf8().copyToByteArray())));
  }

  private static ObjectNode sourceBasisNode(SelectedSourceBasis basis) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("kind", basis.kind().name());
    value.put("snapshotId", basis.snapshotId().value());
    value.put("effectiveScopeDigest", basis.effectiveScopeDigest().value());
    if (basis.kind() == SelectedSourceBasis.Kind.PREPARED_V1) {
      ObjectNode prepared = value.putObject("preparedSource");
      prepared.put("sourceVersionId", basis.preparedSource().sourceVersionId().value());
      prepared.set(
          "publication", analysisStepPublicationNode(basis.preparedSource().publication()));
      prepared.set(
          "schemaBundleRef", artifactReferenceNode(basis.preparedSource().schemaBundleRef()));
      ObjectNode policy = prepared.putObject("artifactPolicyRegistryRef");
      policy.put(
          "artifactId", basis.preparedSource().artifactPolicyRegistryRef().artifactId().value());
      policy.put("sha256", basis.preparedSource().artifactPolicyRegistryRef().sha256().value());
      value.putNull("legacyCapture");
      return value;
    }
    ObjectNode legacy = value.putObject("legacyCapture");
    legacy.put("sourceRegistrationId", basis.legacyCapture().sourceRegistrationId().value());
    legacy.put("snapshotId", basis.legacyCapture().snapshotId());
    legacy.set(
        "snapshotManifestRef", artifactReferenceNode(basis.legacyCapture().snapshotManifestRef()));
    legacy.set(
        "captureReceiptRef", artifactReferenceNode(basis.legacyCapture().captureReceiptRef()));
    value.putNull("preparedSource");
    return value;
  }

  private static ObjectNode analysisStepPublicationNode(
      AnalysisStepPublicationReference reference) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    ObjectNode address = value.putObject("address");
    address.put("runId", reference.address().runId().value());
    address.put("analysisStepKey", reference.address().analysisStepKey().wireValue());
    value.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    value.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    value.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
    return value;
  }

  private static ObjectNode artifactReferenceNode(ArtifactReference reference) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("artifactId", reference.artifactId().value());
    value.put("sha256", reference.sha256().value());
    return value;
  }

  private static ObjectNode preparationBindings(
      AnalysisRunRequest.TechnicalAnalysisInputs technical) {
    ObjectNode bindings = JsonNodeFactory.instance.objectNode();
    bindings.set("resourceBudgetRef", artifactReferenceNode(technical.resourceBudgetRef()));
    bindings.set("schemaBundleRef", artifactReferenceNode(technical.schemaBundleRef()));
    bindings.set("toolchainRef", artifactReferenceNode(technical.toolchainRef()));
    return bindings;
  }

  private static ObjectNode preparationControls(OntologyConfiguration.Reading reading) {
    ObjectNode controls = JsonNodeFactory.instance.objectNode();
    controls.put("maxUnitBytes", reading.maxUnitBytes());
    controls.put("maxRequestBytes", reading.maxRequestBytes());
    controls.put("maxOutputBytes", reading.maxOutputBytes());
    controls.put("maxOutputTokens", reading.maxOutputTokens());
    controls.put("maxRequests", reading.maxRequests());
    controls.put("maxReadingRounds", reading.maxReadingRounds());
    controls.put("maxActionsPerRound", reading.maxActionsPerRound());
    controls.put("maxNavigationEntries", reading.maxNavigationEntries());
    return controls;
  }

  private static OntologyConfiguration.Reading preparationControls(JsonNode value) {
    if (!(value instanceof ObjectNode controls)
        || !controls.path("maxUnitBytes").canConvertToInt()
        || !controls.path("maxRequestBytes").canConvertToInt()
        || !controls.path("maxOutputBytes").canConvertToInt()
        || !controls.path("maxOutputTokens").canConvertToInt()
        || !controls.path("maxRequests").canConvertToInt()
        || !controls.path("maxReadingRounds").canConvertToInt()
        || !controls.path("maxActionsPerRound").canConvertToInt()
        || !controls.path("maxNavigationEntries").canConvertToInt()) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID");
    }
    try {
      return new OntologyConfiguration.Reading(
          controls.path("maxUnitBytes").asInt(),
          controls.path("maxRequestBytes").asInt(),
          controls.path("maxOutputBytes").asInt(),
          controls.path("maxOutputTokens").asInt(),
          controls.path("maxRequests").asInt(),
          controls.path("maxReadingRounds").asInt(),
          controls.path("maxActionsPerRound").asInt(),
          controls.path("maxNavigationEntries").asInt());
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_RECEIPT_INVALID", invalid);
    }
  }

  private static String formalCorpusIdentity(ObjectNode corpus, CanonicalJsonCodec json) {
    ObjectNode basis = corpus.deepCopy();
    basis.remove("ownerRunId");
    basis.remove("corpusIdentity");
    // Module installation adds these transport-envelope fields after the O0 identity is formed.
    basis.remove("artifactType");
    basis.remove("artifactId");
    return "ontology-corpus:"
        + sha256(
            concatenate(
                frame("ontology-formal-corpus-v2"),
                frame(json.encodeCanonical(basis).copyToByteArray())));
  }

  private static CanonicalModulePayload standalonePayload(
      String fileName,
      String type,
      String schema,
      ObjectNode body,
      CanonicalArtifactPolicyRegistry policies,
      CanonicalJsonCodec json) {
    CanonicalArtifactPolicy policy = policies.resolve(new ArtifactPolicyKey(type, schema));
    ObjectNode envelope = body.deepCopy();
    envelope.put("schemaVersion", schema);
    envelope.put("artifactType", type);
    String artifactId =
        policy.artifactIdPrefix()
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(json.encodeCanonical(envelope).copyToByteArray())));
    envelope.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(envelope));
  }

  private static CanonicalModulePayload jsonlPayload(
      String fileName,
      String type,
      String schema,
      ImmutableBytes bytes,
      CanonicalArtifactPolicyRegistry policies) {
    CanonicalArtifactPolicy policy = policies.resolve(new ArtifactPolicyKey(type, schema));
    String artifactId =
        policy.artifactIdPrefix()
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(bytes.copyToByteArray())));
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes);
  }

  private static int query(Path configurationPath, Invocation invocation, PrintWriter output) {
    OntologyConfiguration.Storage storage = OntologyConfiguration.loadStorage(configurationPath);
    try (RunStoreHandle store = RunStoreBootstrap.open(storage.root())) {
      LocalRepositoryAnalysisAgent agent =
          new LocalRepositoryAnalysisAgent(
              store,
              null,
              null,
              null,
              null,
              (runId, saved, key, taskId, maxBytes) ->
                  readArtifact(storage, store, runId, saved, key, taskId, maxBytes));
      if ("inspect".equals(invocation.operation())) {
        RunInspection inspection = agent.inspect(invocation.runId().value());
        writeRunObservation(
            output, store, storage, inspection.analysisRun().runId(), new CanonicalJsonCodec());
      } else {
        ArtifactView view =
            agent.artifact(
                invocation.taskId() == null
                    ? ArtifactQuery.ontology(
                        invocation.runId().value(), invocation.artifactKey(), invocation.maxBytes())
                    : ArtifactQuery.ontologyTaskRecord(
                        invocation.runId().value(), invocation.taskId(), invocation.maxBytes()));
        output.print(view.contentUtf8());
      }
      output.flush();
      return 0;
    }
  }

  private static ArtifactView readArtifact(
      OntologyConfiguration.Storage storage,
      RunStoreHandle store,
      AnalysisRunId runId,
      AnalysisRunOutput saved,
      OntologyArtifactQueryKey key,
      String taskId,
      int maxBytes) {
    AnalysisRunRequest request =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, runId).request();
    if (request.requestKind() != AnalysisRunRequest.RequestKind.ONTOLOGY) {
      throw new IllegalArgumentException("ONTOLOGY_RUN_NOT_FOUND");
    }
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies =
        exactSavedOntologyPolicies(
            storage, request.ontologyInputs().artifactPolicyRegistryRef(), json);
    if (key == OntologyArtifactQueryKey.ONTOLOGY_TASK_INDEX) {
      AnalysisRunRequest.OntologyOperation operation = request.ontologyInputs().operation();
      if (operation != AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY
          && operation != AnalysisRunRequest.OntologyOperation.RELATE_ONTOLOGY) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_RECORD_UNAVAILABLE");
      }
      OntologyJobResultStore jobs =
          new OntologyJobResultStore(
              SourceAnalysisExecution.checkedDirectory(storage.root(), "ontology-journal"), runId);
      JsonNode taskOutcomes = null;
      if (saved != null && saved.ontologyOutput() != null) {
        ReopenedModulePublication publication =
            new FileSystemCanonicalModuleArtifactStore(store, json, policies, STORE_LIMITS)
                .reopen(saved.ontologyOutput().ontologyPublication());
        String expectedType =
            operation == AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY
                ? "ONTOLOGY_IDENTIFICATION"
                : "ONTOLOGY_RELATIONS";
        String expectedV2Schema =
            operation == AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY
                ? "ontology-identification-v2"
                : "ontology-relations-v2";
        for (VerifiedCanonicalPayload payload : publication.payloads()) {
          if (expectedType.equals(payload.descriptor().artifactType())
              && Set.of(expectedV2Schema, expectedV2Schema.replace("-v2", "-v3"))
                  .contains(payload.descriptor().schemaVersion())) {
            JsonNode document = json.parseCanonical(payload.canonicalUtf8());
            if (!document.path("taskOutcomes").isArray()) {
              throw new IllegalArgumentException("ONTOLOGY_TASK_RECORD_UNAVAILABLE");
            }
            taskOutcomes = document.path("taskOutcomes");
            break;
          }
        }
      }
      ImmutableBytes content =
          json.encodeCanonical(
              taskOutcomes == null ? jobs.formalTaskIndex() : jobs.formalTaskIndex(taskOutcomes));
      if (content.size() > maxBytes) {
        throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_TOO_LARGE");
      }
      return ArtifactView.ontology(
          runId,
          key,
          SourceAnalysisExecution.contentReference("ontology-task-index", content),
          key.schemaVersion(),
          CanonicalMediaType.APPLICATION_JSON.wireValue(),
          new String(content.copyToByteArray(), StandardCharsets.UTF_8));
    }
    if (taskId != null) {
      AnalysisRunRequest.OntologyOperation operation = request.ontologyInputs().operation();
      if (operation != AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY
          && operation != AnalysisRunRequest.OntologyOperation.RELATE_ONTOLOGY) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_RECORD_UNAVAILABLE");
      }
      ObjectNode observation =
          new OntologyJobResultStore(
                  SourceAnalysisExecution.checkedDirectory(storage.root(), "ontology-journal"),
                  runId)
              .formalTaskObservation(taskId);
      String observationSchemaVersion = observation.path("schemaVersion").asText();
      if (!key.acceptsSchemaVersion(observationSchemaVersion)) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_RECORD_UNAVAILABLE");
      }
      ImmutableBytes content = json.encodeCanonical(observation);
      if (content.size() > maxBytes) {
        throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_TOO_LARGE");
      }
      return ArtifactView.ontology(
          runId,
          key,
          SourceAnalysisExecution.contentReference("ontology-task-observation", content),
          observationSchemaVersion,
          CanonicalMediaType.APPLICATION_JSON.wireValue(),
          new String(content.copyToByteArray(), StandardCharsets.UTF_8));
    }
    if (key == OntologyArtifactQueryKey.ONTOLOGY_ASSEMBLY_DIAGNOSTIC) {
      if (request.ontologyInputs().operation()
          != AnalysisRunRequest.OntologyOperation.PUBLISH_ONTOLOGY) {
        throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_UNAVAILABLE");
      }
      OntologyJobResultStore jobs =
          new OntologyJobResultStore(
              SourceAnalysisExecution.checkedDirectory(storage.root(), "ontology-journal"), runId);
      OntologyJobResultStore.RuntimeObservation runtime = jobs.formalRuntimeObservation();
      if (!"ONTOLOGY_ASSEMBLY_DEFINITION_CONFLICT".equals(runtime.problemCode())
          || !"ASSEMBLY".equals(runtime.stage())) {
        throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_UNAVAILABLE");
      }
      JsonNode review = json.parseCanonical(jobs.readFormalAssembly().review());
      JsonNode issues = review.get("assemblyIssues");
      if (issues == null || !issues.isArray() || issues.isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_UNAVAILABLE");
      }
      ObjectNode diagnostic = JsonNodeFactory.instance.objectNode();
      diagnostic.put("schemaVersion", key.schemaVersion());
      diagnostic.put("runId", runId.value());
      ObjectNode provenance = diagnostic.putObject("provenance");
      provenance.set(
          "ontologySelectionRef",
          artifactReferenceNode(request.ontologyInputs().ontologySelectionRef()));
      provenance.set(
          "ontologyScopeRef", artifactReferenceNode(request.ontologyInputs().ontologyScopeRef()));
      diagnostic.set("assemblyIssues", issues.deepCopy());
      ImmutableBytes content = json.encodeCanonical(diagnostic);
      if (content.size() > maxBytes) {
        throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_TOO_LARGE");
      }
      return ArtifactView.ontology(
          runId,
          key,
          SourceAnalysisExecution.contentReference("ontology-assembly-diagnostic", content),
          key.schemaVersion(),
          CanonicalMediaType.APPLICATION_JSON.wireValue(),
          new String(content.copyToByteArray(), StandardCharsets.UTF_8));
    }
    if (saved == null || saved.ontologyOutput() == null) {
      throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_UNAVAILABLE");
    }
    ReopenedModulePublication publication =
        new FileSystemCanonicalModuleArtifactStore(store, json, policies, STORE_LIMITS)
            .reopen(saved.ontologyOutput().ontologyPublication());
    if (key == OntologyArtifactQueryKey.ONTOLOGY_BUSINESS_OVERVIEW) {
      if (request.ontologyInputs().operation()
          != AnalysisRunRequest.OntologyOperation.PUBLISH_ONTOLOGY) {
        throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_UNAVAILABLE");
      }
      ImmutableBytes html =
          ImmutableBytes.copyOf(
              new OntologyBusinessOverviewRenderer()
                  .render(
                      overviewPayload(publication, "ONTOLOGY", "ontology.json", "ontology-v2"),
                      overviewPayload(
                          publication,
                          "ONTOLOGY_COVERAGE",
                          "ontology-coverage.json",
                          "ontology-coverage-v3"),
                      overviewPayload(
                          publication,
                          "ONTOLOGY_SOURCE_INDEX",
                          "ontology-sources.jsonl",
                          "ontology-source-v1"),
                      overviewPayload(
                          publication,
                          "ONTOLOGY_REVIEW",
                          "ontology-review.json",
                          "ontology-review-v3"))
                  .getBytes(StandardCharsets.UTF_8));
      if (html.size() > maxBytes) throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_TOO_LARGE");
      return ArtifactView.ontology(
          runId,
          key,
          SourceAnalysisExecution.contentReference("ontology-business-overview", html),
          key.schemaVersion(),
          "text/html",
          new String(html.copyToByteArray(), StandardCharsets.UTF_8));
    }
    VerifiedCanonicalPayload payload =
        publication.payloads().stream()
            .filter(
                candidate ->
                    key.fileName().equals(candidate.descriptor().fileName())
                        && key.artifactType().equals(candidate.descriptor().artifactType())
                        && key.acceptsSchemaVersion(candidate.descriptor().schemaVersion()))
            .reduce(
                (first, second) -> {
                  throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_AMBIGUOUS");
                })
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_ARTIFACT_UNAVAILABLE"));
    if (payload.canonicalUtf8().size() > maxBytes) {
      throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_TOO_LARGE");
    }
    return ArtifactView.ontology(
        runId,
        key,
        new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256()),
        payload.descriptor().schemaVersion(),
        payload.descriptor().mediaType().wireValue(),
        new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static ImmutableBytes overviewPayload(
      ReopenedModulePublication publication, String type, String file, String schema) {
    return publication.payloads().stream()
        .filter(
            payload ->
                type.equals(payload.descriptor().artifactType())
                    && file.equals(payload.descriptor().fileName())
                    && schema.equals(payload.descriptor().schemaVersion()))
        .reduce(
            (first, second) -> {
              throw new IllegalArgumentException("ONTOLOGY_ARTIFACT_AMBIGUOUS");
            })
        .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_ARTIFACT_UNAVAILABLE"))
        .canonicalUtf8();
  }

  private static void writeRunObservation(
      PrintWriter output,
      RunStoreHandle store,
      OntologyConfiguration.Storage storage,
      AnalysisRunId runId,
      CanonicalJsonCodec json) {
    AnalysisRunReference run = RunStoreBootstrap.reopenAnalysisRun(store, runId);
    AnalysisRunRequest request =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, runId).request();
    if (request.requestKind() != AnalysisRunRequest.RequestKind.ONTOLOGY) {
      throw new IllegalArgumentException("ONTOLOGY_RUN_NOT_FOUND");
    }
    AnalysisRunOutput saved = RunStoreBootstrap.reopenAnalysisRunOutput(store, runId).orElse(null);
    ObjectNode observation = JsonNodeFactory.instance.objectNode();
    observation.put("runId", runId.value());
    observation.put("operation", request.ontologyInputs().operation().name());
    observation.put("lifecycleState", run.lifecycleState().name());
    if (saved == null || saved.ontologyOutput() == null) {
      observation.putNull("resultStatus");
    } else {
      observation.put("resultStatus", saved.ontologyOutput().status().name());
    }
    boolean complete =
        run.lifecycleState() == AnalysisRunLifecycleState.FINISHED
            && saved != null
            && saved.ontologyOutput() != null
            && saved.ontologyOutput().status() == OntologyRunOutput.Status.COMPLETED;
    observation.put("canContinue", complete);
    ArrayNode available = observation.putArray("availableArtifactKeys");
    List<JsonNode> taskOutcomes = new ArrayList<>();
    boolean payloadObservationV2 = false;
    if (saved != null && saved.ontologyOutput() != null) {
      CanonicalArtifactPolicyRegistry policies =
          exactSavedOntologyPolicies(
              storage, request.ontologyInputs().artifactPolicyRegistryRef(), json);
      List<VerifiedCanonicalPayload> payloads =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, STORE_LIMITS)
              .reopen(saved.ontologyOutput().ontologyPublication())
              .payloads();
      List<String> keys =
          payloads.stream().map(payload -> payload.descriptor().artifactType()).sorted().toList();
      keys.forEach(available::add);
      if (request.ontologyInputs().operation()
              == AnalysisRunRequest.OntologyOperation.PUBLISH_ONTOLOGY
          && payloads.stream()
              .anyMatch(
                  payload ->
                      "ONTOLOGY".equals(payload.descriptor().artifactType())
                          && "ontology-v2".equals(payload.descriptor().schemaVersion()))) {
        available.add(OntologyArtifactQueryKey.ONTOLOGY_BUSINESS_OVERVIEW.name());
      }
      for (VerifiedCanonicalPayload payload : payloads) {
        String artifactType = payload.descriptor().artifactType();
        if (!OntologyOperationObservationV2.isTaskOutcomePayloadArtifact(artifactType)) {
          continue;
        }
        JsonNode document = json.parseCanonical(payload.canonicalUtf8());
        String schemaVersion = document.path("schemaVersion").asText();
        if (!OntologyOperationObservationV2.isTaskOutcomePayload(artifactType, schemaVersion)) {
          continue;
        }
        if (!document.path("taskOutcomes").isArray()) {
          throw new IllegalArgumentException("ONTOLOGY_RUNTIME_OBSERVATION_INVALID");
        }
        payloadObservationV2 = true;
        document.path("taskOutcomes").forEach(outcome -> taskOutcomes.add(outcome.deepCopy()));
      }
    }
    ArrayNode problems = observation.putArray("problems");
    Integer dispatched = null;
    OntologyJobResultStore.RuntimeObservation runtime = null;
    AnalysisRunRequest.OntologyOperation operation = request.ontologyInputs().operation();
    String missingRuntimeProblemCode = null;
    if (operation == AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY) {
      dispatched = 0;
    }
    if (operation != AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY
        || (saved == null || saved.ontologyOutput() == null)) {
      try {
        runtime =
            new OntologyJobResultStore(
                    SourceAnalysisExecution.checkedDirectory(storage.root(), "ontology-journal"),
                    runId)
                .formalRuntimeObservation();
        dispatched = runtime.modelRequestsDispatched();
      } catch (IllegalArgumentException missing) {
        if (operation == AnalysisRunRequest.OntologyOperation.PUBLISH_ONTOLOGY) {
          dispatched = 0;
        }
        if (saved == null || saved.ontologyOutput() == null) {
          missingRuntimeProblemCode = "ONTOLOGY_RUNTIME_FAILED";
        }
      }
    }
    boolean observationV2 = payloadObservationV2 || (runtime != null && runtime.isV2());
    if (dispatched == null) observation.putNull("modelRequestsDispatched");
    else observation.put("modelRequestsDispatched", dispatched);
    if (observationV2) {
      OntologyOperationObservationV2.writeObserved(
          observation,
          problems,
          taskOutcomes,
          runId.value(),
          operation,
          runtime,
          missingRuntimeProblemCode);
    } else {
      String runtimeProblemCode =
          runtime == null ? missingRuntimeProblemCode : runtime.problemCode();
      if (runtimeProblemCode != null) {
        ObjectNode problem = problems.addObject();
        problem.put("code", runtimeProblemCode);
        if (runtime == null || runtime.taskId() == null) problem.putNull("taskId");
        else problem.put("taskId", runtime.taskId());
        if (runtime == null || runtime.stage() == null) problem.putNull("stage");
        else problem.put("stage", runtime.stage());
      }
    }
    output.print(
        new String(json.encodeCanonical(observation).copyToByteArray(), StandardCharsets.UTF_8));
  }

  /**
   * The assembler owns this structured signal; v2 publication must not install an incomplete form.
   */
  private static boolean hasBlockingAssemblyIssues(ObjectNode review) {
    JsonNode issues = review.get("assemblyIssues");
    if (issues == null || !issues.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
    }
    return !issues.isEmpty();
  }

  private static byte[] frame(byte[] value) {
    ByteBuffer framed = ByteBuffer.allocate(Long.BYTES + value.length).order(ByteOrder.BIG_ENDIAN);
    framed.putLong(value.length);
    framed.put(value);
    return framed.array();
  }

  private static byte[] concatenate(byte[]... values) {
    int size = 0;
    for (byte[] value : values) size = Math.addExact(size, value.length);
    ByteBuffer joined = ByteBuffer.allocate(size);
    for (byte[] value : values) joined.put(value);
    return joined.array();
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private record AdmittedEvidence(
      SelectedSourceBasis sourceBasis,
      AnalysisStepPublicationReference evidencePublication,
      AnalysisRunRequest.TechnicalAnalysisInputs technicalInputs,
      OntologyEvidenceCorpus corpus,
      VerifiedSourceTextSet sourceTexts,
      List<ArtifactReference> evidencePayloadReferences) {
    private AdmittedEvidence withCorpus(OntologyEvidenceCorpus replacement) {
      return new AdmittedEvidence(
          sourceBasis,
          evidencePublication,
          technicalInputs,
          replacement,
          sourceTexts,
          evidencePayloadReferences);
    }
  }

  private record InstalledPublication(
      org.sourceanalysis.app.artifact.ModulePublicationReference modulePublication,
      AnalysisStepPublicationReference stepPublication) {}

  private record OntologyStage(AnalysisRunRequest request, AnalysisRunOutput output) {}

  private record SavedCorpus(
      AdmittedEvidence admitted,
      String corpusIdentity,
      String projectionRuleVersion,
      OntologyConfiguration.Reading reading,
      ArtifactReference corpusPayloadReference) {}

  private record SelectedCorpus(OntologyStage stage, SavedCorpus saved) {}

  private record DiscoveryQuestion(
      String questionRef,
      String surveyJobKey,
      String sourceQuestionId,
      String question,
      List<String> entryRefs,
      List<String> clueRefs) {}

  private record DeferredQuestion(String questionRef, String reason) {}

  private record PrioritySelection(
      List<OntologyScopeReader.Question> questions,
      List<String> questionRefs,
      List<DeferredQuestion> deferredQuestions) {}

  private record DiscoveryStage(
      OntologyScopeReader.Scope selectedScope,
      ObjectNode document,
      String failureCode,
      OntologyDecisionRunner decisions,
      OntologyTaskOutcome.FailureReason failureReason) {
    private DiscoveryStage(
        OntologyScopeReader.Scope selectedScope,
        ObjectNode document,
        String failureCode,
        OntologyDecisionRunner decisions) {
      this(selectedScope, document, failureCode, decisions, null);
    }
  }

  private record ModelBinding(
      OntologyTypedTaskRunner.FormalModelDeclaration declaration,
      ModelJobProviderConfiguration configured,
      ArtifactReference reference) {}

  private record SavedFormalMembership(String taskId, String jobKey) {}

  private record FormalStageMembership(
      Map<String, SavedFormalMembership> reviewed,
      List<OntologyScopedAssembler.TaskDisposition> dispositions,
      Map<String, JsonNode> taskOutcomes,
      JsonNode stageSnapshot,
      Map<String, JsonNode> readingSelections) {
    private FormalStageMembership {
      reviewed = Map.copyOf(reviewed);
      dispositions = List.copyOf(dispositions);
      taskOutcomes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(taskOutcomes));
      stageSnapshot = stageSnapshot.deepCopy();
      readingSelections = Map.copyOf(readingSelections);
    }
  }

  private record SelectedFormalResult(
      String runId, String taskId, OntologyTypedTaskRunner.FormalResult result) {}

  private record SelectedFormalResultKey(String runId, String producingTaskId) {}

  private record SelectedFormalIdentityKey(
      String runId, OntologyTypedTaskRunner.FormalIdentity identity) {}

  private record AssemblyDispositionKey(
      OntologyTypedTaskRunner.FormalIdentity reviewedResultIdentity,
      String runId,
      String questionId,
      String taskId,
      String producingTaskId) {}

  private record SelectedTaskDisposition(
      String runId,
      String questionId,
      String producingTaskId,
      OntologyScopedAssembler.TaskDisposition disposition,
      List<String> selectedClueRefs) {
    private SelectedTaskDisposition {
      selectedClueRefs = List.copyOf(selectedClueRefs);
    }
  }

  private record SelectedTaskOutcome(String runId, String taskId, JsonNode outcome) {
    private SelectedTaskOutcome {
      outcome = outcome.deepCopy();
    }
  }

  private record SelectedFormalStage(
      List<SelectedFormalResult> results,
      List<SelectedTaskDisposition> dispositions,
      List<SelectedTaskOutcome> taskOutcomes) {
    private SelectedFormalStage {
      results = List.copyOf(results);
      dispositions = List.copyOf(dispositions);
      taskOutcomes = List.copyOf(taskOutcomes);
    }
  }

  private record Invocation(
      String operation,
      AnalysisRunId evidenceRunId,
      AnalysisRunId runId,
      Path scope,
      Path selection,
      OntologyArtifactQueryKey artifactKey,
      String taskId,
      int maxBytes) {

    private static Invocation parse(String operation, List<String> options) {
      if ((!OPERATIONS.contains(operation)
              && !"inspect".equals(operation)
              && !"artifact".equals(operation))
          || options.size() % 2 != 0) {
        throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
      }
      Map<String, String> values = new LinkedHashMap<>();
      for (int index = 0; index < options.size(); index += 2) {
        String name = options.get(index);
        String value = options.get(index + 1);
        if (value.isBlank() || values.putIfAbsent(name, value) != null) {
          throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
        }
      }
      return switch (operation) {
        case "prepare-ontology" ->
            new Invocation(
                operation,
                requiredRun(values, "--evidence-run", 1),
                null,
                null,
                null,
                null,
                null,
                0);
        case "identify-ontology" -> {
          requireOnly(values, "--corpus-run", "--scope");
          yield new Invocation(
              operation,
              null,
              requiredRun(values, "--corpus-run", 2),
              Path.of(values.get("--scope")),
              null,
              null,
              null,
              0);
        }
        case "relate-ontology", "publish-ontology" -> {
          requireOnly(values, "--selection");
          yield new Invocation(
              operation, null, null, null, Path.of(values.get("--selection")), null, null, 0);
        }
        case "inspect" ->
            new Invocation(
                operation, null, requiredRun(values, "--run", 1), null, null, null, null, 0);
        case "artifact" -> artifactInvocation(operation, values);
        default -> throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
      };
    }

    private static Invocation artifactInvocation(String operation, Map<String, String> values) {
      if (!values.containsKey("--run")
          || !values.containsKey("--key")
          || !values.containsKey("--max-bytes")
          || values.size() < 3
          || values.size() > 4) {
        throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
      }
      OntologyArtifactQueryKey key;
      try {
        key = OntologyArtifactQueryKey.valueOf(values.get("--key"));
      } catch (IllegalArgumentException invalid) {
        throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
      }
      String taskId = values.get("--task-id");
      if (key.taskObservation() != (taskId != null)) {
        throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
      }
      return new Invocation(
          operation,
          null,
          requiredRun(values, "--run", values.size()),
          null,
          null,
          key,
          taskId,
          positive(values));
    }

    private boolean isQuery() {
      return "inspect".equals(operation) || "artifact".equals(operation);
    }

    private static AnalysisRunId requiredRun(Map<String, String> values, String key, int size) {
      if (values.size() != size || !values.containsKey(key)) {
        throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
      }
      return AnalysisRunId.parse(values.get(key));
    }

    private static int positive(Map<String, String> values) {
      try {
        int value = Integer.parseInt(values.get("--max-bytes"));
        if (value < 1) throw new NumberFormatException();
        return value;
      } catch (RuntimeException invalid) {
        throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
      }
    }

    private static void requireOnly(Map<String, String> values, String... names) {
      if (values.size() != names.length)
        throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
      for (String name : names) {
        if (!values.containsKey(name))
          throw new IllegalArgumentException("ONTOLOGY_ARGUMENTS_INVALID");
      }
    }
  }
}
