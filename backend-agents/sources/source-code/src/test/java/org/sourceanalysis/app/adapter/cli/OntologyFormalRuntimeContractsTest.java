package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionCommand;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaCompilationEnvironment;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexPublicationSpecifier;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendRequestObservation;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceFileDisposition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSyntaxInput;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSyntaxScan;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSyntaxTool;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.inventory.PreparedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyCallBudgetProvider;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.analysis.ontology.OntologyJobResultStore;
import org.sourceanalysis.app.analysis.ontology.OntologyTypedTaskRunner;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.LocalRepositoryAnalysisAgent;
import org.sourceanalysis.app.runtime.OntologyRunOutput;
import org.sourceanalysis.app.runtime.TechnicalRunOutput;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Direct contracts for the unique ontology CLI → real Agent/store/query path. */
class OntologyFormalRuntimeContractsTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final CanonicalJsonCodec CANONICAL = new CanonicalJsonCodec();
  private static final String EXTRACT_PROVIDER_KEY = "scripted-extract";
  private static final String RELATE_PROVIDER_KEY = "scripted-relate";
  private static final Path SOURCE_PREPARATION_POLICY_SET =
      Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
          .toAbsolutePath();
  private static final Path TECHNICAL_POLICY_SET =
      Path.of("tools/repository-run/technical-analysis-artifact-policy-set-v3.json")
          .toAbsolutePath();
  private static final Path ONTOLOGY_POLICY_SET =
      Path.of("tools/repository-run/ontology-artifact-policy-set-v1.json").toAbsolutePath();
  private static final Path ONTOLOGY_POLICY_SET_V2 =
      Path.of("tools/repository-run/ontology-artifact-policy-set-v2.json").toAbsolutePath();
  private static final Path ONTOLOGY_POLICY_SET_V3 =
      Path.of("tools/repository-run/ontology-artifact-policy-set-v3.json").toAbsolutePath();
  private static final Path ONTOLOGY_POLICY_SET_V4 =
      Path.of("tools/repository-run/ontology-artifact-policy-set-v4.json").toAbsolutePath();
  private static final Path ONTOLOGY_POLICY_SET_V5 =
      Path.of("tools/repository-run/ontology-artifact-policy-set-v5.json").toAbsolutePath();
  private static final String JAVA_PATH = "src/main/java/fixture/RecordHandler.java";
  private static final String JAVA_SOURCE =
      "package fixture;\n"
          + "import org.springframework.web.bind.annotation.GetMapping;\n"
          + "import org.springframework.web.bind.annotation.RequestMapping;\n"
          + "@RequestMapping(\"/api\")\n"
          + "final class RecordHandler {\n"
          + "  @GetMapping(\"/records\")\n"
          + "  public String list() { return \"neutral\"; }\n"
          + "}\n";
  private static final String SOURCE_SUPPLEMENT_JAVA_SOURCE =
      "package fixture;\n"
          + "import org.springframework.web.bind.annotation.GetMapping;\n"
          + "import org.springframework.web.bind.annotation.RequestMapping;\n"
          + "@RequestMapping(\"/api\")\n"
          + "final class RecordHandler {\n"
          + "  @GetMapping(\"/records\")\n"
          + "  public String list() { return helper(); }\n"
          + "  private String helper() { return \"restricted\"; }\n"
          + "  private String dormant() { return \"UNSELECTED_SOURCE_SENTINEL\"; }\n"
          + "}\n";
  private static final String DDL_HANDLER_SOURCE =
      "package fixture;\n"
          + "import java.util.List;\n"
          + "import org.springframework.web.bind.annotation.GetMapping;\n"
          + "import org.springframework.web.bind.annotation.RequestMapping;\n"
          + "@RequestMapping(\"/api\")\n"
          + "final class RecordHandler {\n"
          + "  private final RecordMapper mapper;\n"
          + "  @GetMapping(\"/records\")\n"
          + "  public List<String> list() { return mapper.findAll(); }\n"
          + "}\n";
  private static final String DDL_MAPPER_SOURCE =
      "package fixture;\n"
          + "import java.util.List;\n"
          + "public interface RecordMapper { List<String> findAll(); }\n";
  private static final String DDL_MAPPER_XML =
      "<mapper namespace=\"fixture.RecordMapper\">\n"
          + "  <select id=\"findAll\" resultType=\"string\">\n"
          + "    SELECT record_table.id FROM record_table "
          + "JOIN parent_table ON record_table.parent_id = parent_table.id\n"
          + "  </select>\n"
          + "</mapper>\n";
  private static final String DDL_JAVA_MAPPER_PATH = "src/main/java/fixture/RecordMapper.java";
  private static final String DDL_XML_MAPPER_PATH = "src/main/resources/mapper/RecordMapper.xml";
  private static final String FRONTEND_REQUEST_PATH = "web/src/pages/NeutralRecords.vue";
  private static final String FRONTEND_REQUEST_SOURCE =
      "export default { load() { return this.$http.get('/api/records') } };\n";
  private static final String DDL_SOURCE_PATH = "schema/record-schema.sql";
  private static final String DDL_INLINE_CONSTRAINTS_PATH = "schema/inline-constraints.sql";
  private static final String DDL_TEXT =
      "CREATE TABLE record_table (\n"
          + "  id BIGINT NOT NULL,\n"
          + "  code VARCHAR(64) NULL,\n"
          + "  parent_id BIGINT,\n"
          + "  label VARCHAR(32) DEFAULT 'NOT NULL',\n"
          + "  CONSTRAINT pk_record PRIMARY KEY (id),\n"
          + "  CONSTRAINT uq_record_code UNIQUE (code),\n"
          + "  CONSTRAINT fk_record_parent FOREIGN KEY (parent_id) REFERENCES parent_table (id),\n"
          + "  INDEX ix_record_code (code)\n"
          + ");\n"
          + "CREATE TABLE parent_table (\n"
          + "  id BIGINT NOT NULL,\n"
          + "  code VARCHAR(32) NULL,\n"
          + "  CONSTRAINT pk_parent PRIMARY KEY (id),\n"
          + "  CONSTRAINT uq_parent_code UNIQUE (code),\n"
          + "  INDEX ix_parent_code (code)\n"
          + ");\n";
  private static final String DDL_INLINE_CONSTRAINTS_TEXT =
      "CREATE TABLE record_table (\n" + "  id INT PRIMARY KEY,\n" + "  code INT UNIQUE\n" + ");\n";

  @TempDir Path temporaryDirectory;

  @Test
  void realProviderWithoutCodexExecutableFailsBeforeQueueOrModelDispatch() throws Exception {
    TechnicalFixture fixture = prepareRealR4("missing-codex-executable");
    Path config =
        writeOntologyConfig(
            "ontology-missing-codex-executable.yaml", fixture, fixture.archive(), null, true);
    CliResult prepared =
        executePublic(config, "prepare-ontology", "--evidence-run", fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    Path scope = writeExplicitScope(temporaryDirectory.resolve("missing-executable-scope.json"));

    CliResult refused =
        executePublic(
            config,
            "identify-ontology",
            "--corpus-run",
            runId(prepared),
            "--scope",
            scope.toString());

    assertThat(refused.exitCode()).isEqualTo(2);
    assertThat(refused.stderr()).contains("ONTOLOGY_PROVIDER_EXECUTABLE_REQUIRED");
    assertThat(refused.stdout()).isEmpty();
  }

  @Test
  void allFourFormalVerbsStayOnTheUniqueConfiguredCliAndInvalidOntologyConfigIsNamed()
      throws Exception {
    Path malformed = temporaryDirectory.resolve("malformed-ontology.yaml");
    Files.writeString(
        malformed,
        "schemaVersion: ontology-config-v1\nunknownControl: true\n",
        StandardCharsets.UTF_8);
    Path missing = temporaryDirectory.resolve("missing-ontology.yaml");

    List<String[]> invocations =
        List.of(
            new String[] {"prepare-ontology", "--evidence-run", runId('a')},
            new String[] {
              "identify-ontology",
              "--corpus-run",
              runId('b'),
              "--scope",
              temporaryDirectory.resolve("scope.json").toString()
            },
            new String[] {
              "relate-ontology", "--selection", temporaryDirectory.resolve("relate.json").toString()
            },
            new String[] {
              "publish-ontology",
              "--selection",
              temporaryDirectory.resolve("publish.json").toString()
            });

    for (String[] invocation : invocations) {
      assertOntologyConfigurationDiagnostic(executePublic(missing, invocation));
      assertOntologyConfigurationDiagnostic(executePublic(malformed, invocation));
    }
  }

  @Test
  void realPreparedR4ProducesReopenableO0AndQueriesSurvivePromptRemoval() throws Exception {
    TechnicalFixture fixture = prepareRealR4("o0-reopen");
    Path override = temporaryDirectory.resolve("temporary-object-prompt.txt");
    Files.writeString(
        override, "Neutral fixture prompt; no customer or ERP answers.", StandardCharsets.UTF_8);
    Path ontologyConfig =
        writeOntologyConfig("ontology-o0.yaml", fixture, fixture.archive(), override, false);

    CliResult prepared =
        executePublic(ontologyConfig, "prepare-ontology", "--evidence-run", fixture.r4RunId());

    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    assertThat(Files.deleteIfExists(override)).isTrue();

    CliResult inspected = executePublic(ontologyConfig, "inspect", "--run", o0RunId);
    CliResult corpusArtifact = artifact(ontologyConfig, o0RunId, "ONTOLOGY_CORPUS");
    CliResult lowerCaseAlias = artifact(ontologyConfig, o0RunId, "ontology-corpus");

    assertThat(inspected.exitCode()).withFailMessage(inspected.stderr()).isZero();
    assertThat(inspected.stdout()).contains(o0RunId);
    assertThat(corpusArtifact.exitCode()).withFailMessage(corpusArtifact.stderr()).isZero();
    assertThat(corpusArtifact.stderr()).isEmpty();
    JsonNode corpus = JSON.readTree(corpusArtifact.stdout());
    assertThat(corpus.path("schemaVersion").asText()).isEqualTo("ontology-corpus-v1");
    assertThat(corpus.findValuesAsText("ref")).contains("E1", "U1");
    assertThat(formalEntryRefs(corpus)).containsExactly("E1");
    assertThat(lowerCaseAlias.exitCode()).isNotZero();
    assertThat(lowerCaseAlias.stdout()).isEmpty();

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      AnalysisRunId o0 = AnalysisRunId.parse(o0RunId);
      AnalysisRunOutput output = new LocalRepositoryAnalysisAgent(store).inspect(o0RunId).output();
      AnalysisRunRequest request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, o0).request();
      AnalysisStepPublicationReference actualR4 = fixture.r4Publication();
      assertThat(output).isNotNull();
      assertThat(output.ontologyOutput()).isNotNull();
      assertThat(output.ontologyOutput().evidencePublication()).isEqualTo(actualR4);
      assertThat(request.ontologyInputs().evidencePublication()).isEqualTo(actualR4);
      assertThat(request.ontologyInputs().ontologyScopeRef()).isNull();
    }

    Path mismatchedConfig =
        writeOntologyConfig(
            "ontology-wrong-r0.yaml",
            fixture,
            Files.createDirectory(temporaryDirectory.resolve("wrong-source-archive")),
            null,
            true);
    Path scope = writeExplicitScope(temporaryDirectory.resolve("mismatch-scope.json"));
    AtomicInteger providerFactories = new AtomicInteger();
    CliResult refused =
        executeWithFactory(
            mismatchedConfig,
            ignored -> {
              providerFactories.incrementAndGet();
              throw new AssertionError("source/archive mismatch must precede Provider creation");
            },
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(refused.exitCode()).isNotZero();
    assertThat(refused.stdout()).isEmpty();
    assertThat(refused.stderr()).contains("ONTOLOGY");
    assertThat(providerFactories).hasValue(0);
  }

  @Test
  void realR4AdmissionUsesItsSavedEntryBudgetForMoreThanSixtyFourRoutes() throws Exception {
    TechnicalFixture fixture = prepareRealR4WithHttpRouteCount("o0-sixty-five-routes", 65);
    Path ontologyConfig =
        writeOntologyConfig(
            "ontology-sixty-five-routes.yaml", fixture, fixture.archive(), null, false);

    CliResult prepared =
        executePublic(ontologyConfig, "prepare-ontology", "--evidence-run", fixture.r4RunId());

    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    CliResult corpusResult = artifact(ontologyConfig, o0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(corpusResult);
    JsonNode corpus = JSON.readTree(corpusResult.stdout());
    List<String> expectedEntryRefs = new ArrayList<>();
    for (int index = 1; index <= 65; index++) {
      expectedEntryRefs.add("E" + index);
    }
    assertThat(formalEntryRefs(corpus)).containsExactlyInAnyOrderElementsOf(expectedEntryRefs);

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      AnalysisRunRequest r4Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                  store, AnalysisRunId.parse(fixture.r4RunId()))
              .request();
      AnalysisRunRequest o0Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, AnalysisRunId.parse(o0RunId))
              .request();
      assertThat(r4Request.technicalAnalysisInputs().entryEvidenceProfile().maxEntries())
          .isEqualTo(65);
      assertThat(o0Request.ontologyInputs().operation())
          .isEqualTo(AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY);
    }
    assertCorpusPreparationWire(corpus, fixture, o0RunId, 8);
  }

  @Test
  void explicitO0O1ZeroQuestionO2O3UsesSavedTaskPairAndNoProviderForNonModelStages()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("explicit-pipeline");
    Path prompt = temporaryDirectory.resolve("explicit-object-prompt.txt");
    Files.writeString(prompt, "Neutral OBJECT extraction prompt.", StandardCharsets.UTF_8);
    Path ontologyConfig =
        writeOntologyConfig("ontology-explicit.yaml", fixture, fixture.archive(), prompt, true);
    ObjectNode identifyPromptMap = JSON.createObjectNode();
    OntologyConfiguration.load(ontologyConfig).prompts().forEach(identifyPromptMap::put);
    String identifyPromptSha =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(CANONICAL.encodeCanonical(identifyPromptMap).copyToByteArray()));
    AtomicInteger providerFactories = new AtomicInteger();
    ScriptedModel model =
        new ScriptedModel(
            candidate("ontology-typed-candidate-v3"), review("ontology-typed-review-v3"));
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return model.provider(declaration.expectedRuntimeIdentity());
            };

    CliResult prepared =
        executeWithFactory(
            ontologyConfig,
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    assertThat(providerFactories).hasValue(0);

    CliResult corpusResult = artifact(ontologyConfig, o0RunId, "ONTOLOGY_CORPUS");
    assertThat(corpusResult.exitCode()).isZero();
    JsonNode corpus = JSON.readTree(corpusResult.stdout());
    assertThat(corpus.findValuesAsText("ref")).contains("E1", "U1");
    assertThat(formalEntryRefs(corpus)).containsExactly("E1");
    Path scope = writeExplicitScope(temporaryDirectory.resolve("explicit-scope.json"));
    ImmutableBytes scopeBytes = ImmutableBytes.copyOf(Files.readAllBytes(scope));
    JsonNode scopeSnapshot = CANONICAL.parseCanonical(scopeBytes);
    String scopeSha =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(scopeBytes.copyToByteArray()));

    CliResult identified =
        executeWithFactory(
            ontologyConfig,
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    String o1RunId = runId(identified);
    assertThat(Files.deleteIfExists(scope)).isTrue();
    assertThat(providerFactories.get()).isPositive();
    assertThat(model.requests).hasSize(2);
    assertThat(model.requests.get(0).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(model.requests.get(1).taskKind()).contains("OBJECT", "REVIEW");
    int callsAfterIdentify = providerFactories.get();
    Files.deleteIfExists(prompt);
    Path noPromptConfig =
        writeOntologyConfig(
            "ontology-explicit-no-current-override.yaml", fixture, fixture.archive(), null, true);
    ObjectNode zeroTaskPromptMap = JSON.createObjectNode();
    OntologyConfiguration.load(noPromptConfig).prompts().forEach(zeroTaskPromptMap::put);
    String zeroTaskPromptSha =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(CANONICAL.encodeCanonical(zeroTaskPromptMap).copyToByteArray()));

    Path relateSelection =
        writeSelection(
            temporaryDirectory.resolve("zero-question-relate.json"),
            "RELATE",
            o0RunId,
            List.of(o1RunId),
            List.of(),
            true);
    ImmutableBytes relateSelectionBytes =
        ImmutableBytes.copyOf(Files.readAllBytes(relateSelection));
    JsonNode relateSelectionSnapshot = CANONICAL.parseCanonical(relateSelectionBytes);
    String relateSelectionSha =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(relateSelectionBytes.copyToByteArray()));
    CliResult related =
        executeWithFactory(
            noPromptConfig,
            providerFactory,
            "relate-ontology",
            "--selection",
            relateSelection.toString());
    assertThat(related.exitCode()).withFailMessage(related.stderr()).isZero();
    String o2RunId = runId(related);
    assertThat(Files.deleteIfExists(relateSelection)).isTrue();
    int callsAfterZeroTaskO2 = providerFactories.get();
    assertThat(callsAfterZeroTaskO2).isEqualTo(callsAfterIdentify);

    Path publishSelection =
        writeSelection(
            temporaryDirectory.resolve("explicit-publish.json"),
            "PUBLISH",
            o0RunId,
            List.of(o1RunId),
            List.of(o2RunId),
            false);
    ImmutableBytes publishSelectionBytes =
        ImmutableBytes.copyOf(Files.readAllBytes(publishSelection));
    JsonNode publishSelectionSnapshot = CANONICAL.parseCanonical(publishSelectionBytes);
    String publishSelectionSha =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(publishSelectionBytes.copyToByteArray()));
    CliResult published =
        executeWithFactory(
            noPromptConfig,
            providerFactory,
            "publish-ontology",
            "--selection",
            publishSelection.toString());
    assertThat(published.exitCode()).withFailMessage(published.stderr()).isZero();
    String o3RunId = runId(published);
    assertThat(Files.deleteIfExists(publishSelection)).isTrue();
    assertThat(providerFactories).hasValue(callsAfterZeroTaskO2);
    assertThat(model.requests).hasSize(2);

    Path queryConfig =
        writeOntologyConfig("ontology-query.yaml", fixture, fixture.archive(), null, false);
    for (String runId : List.of(o0RunId, o1RunId, o2RunId, o3RunId)) {
      assertThat(executePublic(queryConfig, "inspect", "--run", runId).exitCode()).isZero();
    }
    CliResult identification = artifact(queryConfig, o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identification);
    JsonNode identificationDocument = JSON.readTree(identification.stdout());
    JsonNode objectTaskRecord = null;
    for (JsonNode taskRecord : identificationDocument.path("taskRecords")) {
      if ("object-task".equals(taskRecord.path("taskId").asText())) {
        objectTaskRecord = taskRecord;
        break;
      }
    }
    assertThat(objectTaskRecord).isNotNull();
    assertThat(objectTaskRecord.path("taskKind").asText()).isEqualTo("OBJECT");
    assertThat(objectTaskRecord.path("status").asText()).isEqualTo("REVIEWED");
    String producingTaskId = objectTaskRecord.path("producingTaskId").asText();
    assertThat(producingTaskId).isNotBlank();
    assertThat(objectTaskRecord.path("jobKey").asText()).isNotBlank();
    assertThat(identificationDocument.path("scope")).isEqualTo(scopeSnapshot);
    int callsBeforeTaskObservation = providerFactories.get();
    CliResult taskObservationResult =
        executePublic(
            queryConfig,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            producingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(taskObservationResult);
    JsonNode taskObservation = JSON.readTree(taskObservationResult.stdout());
    assertThat(taskObservation.path("schemaVersion").asText())
        .isEqualTo("ontology-task-observation-v1");
    assertThat(taskObservation.path("runId").asText()).isEqualTo(o1RunId);
    assertThat(taskObservation.path("taskId").asText()).isEqualTo("object-task");
    assertThat(taskObservation.path("producingTaskId").asText()).isEqualTo(producingTaskId);
    assertThat(taskObservation.path("jobKey").asText())
        .isEqualTo(objectTaskRecord.path("jobKey").asText());
    assertThat(taskObservation.path("taskKind").asText()).isEqualTo("OBJECT");
    assertThat(taskObservation.path("status").asText()).isEqualTo("REVIEWED");
    assertSavedFormalRequest(
        taskObservation.path("extract").path("request"), model.requests.get(0));
    assertSavedFormalRequest(taskObservation.path("review").path("request"), model.requests.get(1));
    assertSavedFormalResponseAndValidation(
        taskObservation.path("extract"),
        candidate("ontology-typed-candidate-v3"),
        "VALID_CANDIDATE");
    assertSavedFormalResponseAndValidation(
        taskObservation.path("review"), review("ontology-typed-review-v3"), "REVIEW_VALID");
    JsonNode completion = taskObservation.path("completion");
    assertThat(completion.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(completion.path("review"))
        .isEqualTo(JSON.readTree(review("ontology-typed-review-v3").copyToByteArray()));
    assertThat(completion.path("identity").path("corpusIdentity").asText()).isNotBlank();
    assertThat(completion.path("identity").path("producingTaskId").asText())
        .isEqualTo(producingTaskId);
    assertThat(completion.path("identity").path("reviewVersion").asText()).isNotBlank();
    assertThat(providerFactories).hasValue(callsBeforeTaskObservation);
    assertThat(model.requests).hasSize(2);

    CliResult relations = artifact(queryConfig, o2RunId, "ONTOLOGY_RELATIONS");
    CliResult ontology = artifact(queryConfig, o3RunId, "ONTOLOGY");
    CliResult coverage = artifact(queryConfig, o3RunId, "ONTOLOGY_COVERAGE");
    CliResult sources = artifact(queryConfig, o3RunId, "ONTOLOGY_SOURCE_INDEX");
    CliResult review = artifact(queryConfig, o3RunId, "ONTOLOGY_REVIEW");

    assertArtifactAvailable(identification);
    assertArtifactAvailable(relations);
    assertArtifactAvailable(ontology);
    assertArtifactAvailable(coverage);
    assertArtifactAvailable(sources);
    assertArtifactAvailable(review);
    JsonNode relationDocument = JSON.readTree(relations.stdout());
    JsonNode reviewDocument = JSON.readTree(review.stdout());
    assertThat(relationDocument.path("selection")).isEqualTo(relateSelectionSnapshot);
    assertThat(reviewDocument.path("selection")).isEqualTo(publishSelectionSnapshot);
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      var o1Persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, AnalysisRunId.parse(o1RunId));
      var o2Persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, AnalysisRunId.parse(o2RunId));
      var o3Persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, AnalysisRunId.parse(o3RunId));
      AnalysisRunRequest.OntologyInputs o1Inputs = o1Persisted.request().ontologyInputs();
      AnalysisRunRequest.OntologyInputs o2Inputs = o2Persisted.request().ontologyInputs();
      AnalysisRunRequest.OntologyInputs o3Inputs = o3Persisted.request().ontologyInputs();
      assertThat(o1Inputs.ontologyScopeRef().sha256().value()).isEqualTo(scopeSha);
      assertThat(o1Inputs.ontologyScopeRef().artifactId().value())
          .isEqualTo("ontology-scope:" + scopeSha);
      assertThat(o1Inputs.promptBundleRef().sha256().value()).isEqualTo(identifyPromptSha);
      assertThat(o1Inputs.promptBundleRef().artifactId().value())
          .isEqualTo("ontology-prompts:" + identifyPromptSha);
      assertThat(o2Inputs.ontologyScopeRef().sha256().value()).isEqualTo(relateSelectionSha);
      assertThat(o2Inputs.ontologyScopeRef().artifactId().value())
          .isEqualTo("ontology-selection:" + relateSelectionSha);
      assertThat(o2Inputs.ontologySelectionRef().sha256().value()).isEqualTo(relateSelectionSha);
      assertThat(o2Inputs.ontologySelectionRef().artifactId().value())
          .isEqualTo("ontology-selection:" + relateSelectionSha);
      assertThat(o2Inputs.promptBundleRef().sha256().value()).isEqualTo(zeroTaskPromptSha);
      assertThat(o2Inputs.promptBundleRef().artifactId().value())
          .isEqualTo("ontology-prompts:" + zeroTaskPromptSha);
      assertThat(o2Inputs.promptBundleRef().sha256().value())
          .isNotEqualTo(o2Inputs.ontologySelectionRef().sha256().value());
      assertThat(o3Inputs.ontologyScopeRef().sha256().value()).isEqualTo(publishSelectionSha);
      assertThat(o3Inputs.ontologyScopeRef().artifactId().value())
          .isEqualTo("ontology-selection:" + publishSelectionSha);
      assertThat(o3Inputs.ontologySelectionRef().sha256().value()).isEqualTo(publishSelectionSha);
      assertThat(o3Inputs.ontologySelectionRef().artifactId().value())
          .isEqualTo("ontology-selection:" + publishSelectionSha);
      JsonNode o1Request = CANONICAL.parseCanonical(o1Persisted.canonicalJson());
      JsonNode o2Request = CANONICAL.parseCanonical(o2Persisted.canonicalJson());
      JsonNode o3Request = CANONICAL.parseCanonical(o3Persisted.canonicalJson());
      assertThat(identificationDocument.path("semanticUpstreams").path("corpusPublication"))
          .isEqualTo(o1Request.path("ontologyInputs").path("corpusPublication"));
      assertThat(
              identificationDocument.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(o1Request.path("ontologyInputs").path("identificationPublications"));
      assertThat(identificationDocument.path("semanticUpstreams").path("relationPublications"))
          .isEqualTo(o1Request.path("ontologyInputs").path("relationPublications"));
      assertThat(relationDocument.path("semanticUpstreams").path("corpusPublication"))
          .isEqualTo(o2Request.path("ontologyInputs").path("corpusPublication"));
      assertThat(relationDocument.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(o2Request.path("ontologyInputs").path("identificationPublications"));
      assertThat(relationDocument.path("semanticUpstreams").path("relationPublications"))
          .isEqualTo(o2Request.path("ontologyInputs").path("relationPublications"));
      assertThat(reviewDocument.path("semanticUpstreams").path("corpusPublication"))
          .isEqualTo(o3Request.path("ontologyInputs").path("corpusPublication"));
      assertThat(reviewDocument.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(o3Request.path("ontologyInputs").path("identificationPublications"));
      assertThat(reviewDocument.path("semanticUpstreams").path("relationPublications"))
          .isEqualTo(o3Request.path("ontologyInputs").path("relationPublications"));
    }
    assertThat(ontology.stdout()).contains("Neutral source-backed record", "PARTIAL");
    assertThat(coverage.stdout()).contains("UNDETERMINED");
    assertThat(review.stdout()).contains("object-task", "ontology-typed-review-v3");
    assertThat(model.requests)
        .allSatisfy(request -> assertThat(request.requestedMaxOutputTokens()).isPositive());
  }

  @Test
  void explicitObjectActionAnalyticAndUnresolvedRelatePairsPublishWithoutNonModelProviders()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("explicit-typed-task-chain");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfiguration("ontology-explicit-typed-chain.yaml", fixture);
    AtomicInteger providerFactories = new AtomicInteger();
    ExplicitTypedPipelineScript script = new ExplicitTypedPipelineScript(configured.prompts());
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return script.provider(declaration);
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    assertThat(providerFactories).hasValue(0);
    assertThat(script.requests).isEmpty();
    String o0RunId = runId(prepared);
    CliResult corpusResult =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "artifact",
            "--run",
            o0RunId,
            "--key",
            "ONTOLOGY_CORPUS",
            "--max-bytes",
            "524288");
    assertArtifactAvailable(corpusResult);
    assertThat(formalEntryRefs(JSON.readTree(corpusResult.stdout()))).containsExactly("E1");
    assertThat(providerFactories).hasValue(0);

    Path explicitScope =
        writeExplicitTypedTaskScope(temporaryDirectory.resolve("three-kind-scope.json"));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            explicitScope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    String o1RunId = runId(identified);
    assertThat(script.requests).hasSize(6);
    assertThat(script.requests.get(0).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(script.requests.get(1).taskKind()).contains("OBJECT", "REVIEW");
    assertThat(script.requests.get(2).taskKind()).contains("ACTION", "EXTRACT");
    assertThat(script.requests.get(3).taskKind()).contains("ACTION", "REVIEW");
    assertThat(script.requests.get(4).taskKind()).contains("ANALYTIC", "EXTRACT");
    assertThat(script.requests.get(5).taskKind()).contains("ANALYTIC", "REVIEW");
    assertThat(script.inputAt(0).path("reviewedCatalog").path("entries")).isEmpty();
    assertThat(script.catalogRefAt(2, "objects")).isNotBlank();
    assertThat(script.catalogRefAt(4, "objects")).isNotBlank();
    // ANALYTIC's exact same-question dependency is the reviewed OBJECT, not the ACTION task.
    JsonNode analyticReviewedEntries = script.inputAt(4).path("reviewedCatalog").path("entries");
    assertThat(analyticReviewedEntries.size()).isEqualTo(1);
    assertThat(analyticReviewedEntries.get(0).path("definitionType").asText()).isEqualTo("objects");
    for (int index : List.of(0, 2, 4)) {
      assertThat(script.inputAt(index).path("readingPacket"))
          .isEqualTo(script.inputAt(index + 1).path("readingPacket"));
      assertThat(containsText(script.inputAt(index), "return \"neutral\"")).isTrue();
      assertThat(containsText(script.inputAt(index + 1), "return \"neutral\"")).isTrue();
    }

    CliResult identificationResult =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_IDENTIFICATION",
            "--max-bytes",
            "524288");
    assertArtifactAvailable(identificationResult);
    JsonNode identification = JSON.readTree(identificationResult.stdout());
    List<JsonNode> o1TaskRecords = new ArrayList<>();
    for (JsonNode taskRecord : identification.path("taskRecords")) {
      o1TaskRecords.add(taskRecord);
    }
    assertThat(o1TaskRecords).hasSize(3);
    assertThat(o1TaskRecords.stream().map(value -> value.path("taskId").asText()).toList())
        .containsExactlyInAnyOrder("object-task", "action-task", "analytic-task");
    assertThat(o1TaskRecords)
        .allSatisfy(
            taskRecord -> {
              assertThat(taskRecord.path("producingTaskId").asText()).isNotBlank();
              assertThat(taskRecord.path("jobKey").asText()).isNotBlank();
              assertThat(taskRecord.path("status").asText()).isEqualTo("REVIEWED");
            });
    int factoriesAfterO1 = providerFactories.get();
    assertThat(factoriesAfterO1).isPositive();
    assertThat(script.providerKeys)
        .containsExactly(EXTRACT_PROVIDER_KEY, EXTRACT_PROVIDER_KEY, EXTRACT_PROVIDER_KEY);

    Path relateSelection =
        writeRelateSelection(
            temporaryDirectory.resolve("one-reviewable-relate.json"), o0RunId, o1RunId);
    CliResult related =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            relateSelection.toString());
    assertThat(related.exitCode()).withFailMessage(related.stderr()).isZero();
    String o2RunId = runId(related);
    assertThat(script.requests).hasSize(8);
    assertThat(script.requests.get(6).taskKind()).contains("RELATE", "EXTRACT");
    assertThat(script.requests.get(7).taskKind()).contains("RELATE", "REVIEW");
    assertThat(script.catalogRefAt(6, "objects")).isNotBlank();
    assertThat(script.catalogNameAt(6, "objects")).isEqualTo("Neutral source-backed record");
    assertThat(providerFactories.get()).isGreaterThan(factoriesAfterO1);
    assertThat(script.providerKeys)
        .containsExactly(
            EXTRACT_PROVIDER_KEY, EXTRACT_PROVIDER_KEY, EXTRACT_PROVIDER_KEY, RELATE_PROVIDER_KEY);
    int factoriesAfterO2 = providerFactories.get();

    CliResult relationResult =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "artifact",
            "--run",
            o2RunId,
            "--key",
            "ONTOLOGY_RELATIONS",
            "--max-bytes",
            "524288");
    assertArtifactAvailable(relationResult);
    JsonNode relations = JSON.readTree(relationResult.stdout());
    assertThat(containsText(relations, ExplicitTypedPipelineScript.RELATION_UNRESOLVED_ID))
        .isTrue();
    JsonNode relateMembership = null;
    for (JsonNode taskRecord : relations.path("taskRecords")) {
      if ("relate-task".equals(taskRecord.path("taskId").asText())) {
        relateMembership = taskRecord;
        break;
      }
    }
    assertThat(relateMembership).isNotNull();
    assertThat(relateMembership.path("taskKind").asText()).isEqualTo("RELATE");
    assertThat(relateMembership.path("status").asText()).isEqualTo("REVIEWED");
    String relateProducingTaskId = relateMembership.path("producingTaskId").asText();
    assertThat(relateProducingTaskId).isNotBlank();
    assertThat(relateMembership.path("jobKey").asText()).isNotBlank();

    CliResult relateObservation =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "artifact",
            "--run",
            o2RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            relateProducingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(relateObservation);
    JsonNode savedRelate = JSON.readTree(relateObservation.stdout());
    assertThat(savedRelate.path("schemaVersion").asText())
        .isEqualTo("ontology-task-observation-v1");
    assertThat(containsText(savedRelate, ExplicitTypedPipelineScript.RELATION_UNRESOLVED_ID))
        .isTrue();
    assertThat(providerFactories).hasValue(factoriesAfterO2);
    assertThat(script.requests).hasSize(8);

    Path publishSelection =
        writeSelection(
            temporaryDirectory.resolve("three-kind-publish.json"),
            "PUBLISH",
            o0RunId,
            List.of(o1RunId),
            List.of(o2RunId),
            false);
    CliResult published =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            publishSelection.toString());
    assertThat(published.exitCode()).withFailMessage(published.stderr()).isZero();
    String o3RunId = runId(published);
    assertThat(providerFactories).hasValue(factoriesAfterO2);

    for (String runId : List.of(o0RunId, o1RunId, o2RunId, o3RunId)) {
      CliResult inspected =
          executeWithFactory(configured.path(), providerFactory, "inspect", "--run", runId);
      assertThat(inspected.exitCode()).withFailMessage(inspected.stderr()).isZero();
    }
    CliResult ontologyResult =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "artifact",
            "--run",
            o3RunId,
            "--key",
            "ONTOLOGY",
            "--max-bytes",
            "524288");
    CliResult reviewResult =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "artifact",
            "--run",
            o3RunId,
            "--key",
            "ONTOLOGY_REVIEW",
            "--max-bytes",
            "524288");
    assertArtifactAvailable(ontologyResult);
    assertArtifactAvailable(reviewResult);
    JsonNode ontology = JSON.readTree(ontologyResult.stdout());
    JsonNode review = JSON.readTree(reviewResult.stdout());
    assertThat(ontology.path("operations")).hasSize(1);
    assertThat(ontology.path("rules")).hasSize(1);
    assertThat(ontology.path("dimensions")).hasSize(1);
    assertThat(ontology.path("measures")).hasSize(1);
    assertThat(ontology.path("linkTypes").size()).isZero();
    assertThat(containsText(review, ExplicitTypedPipelineScript.RELATION_UNRESOLVED_ID)).isTrue();
    assertThat(providerFactories).hasValue(factoriesAfterO2);
    assertThat(script.requests).hasSize(8);
  }

  @Test
  void businessLinkV3RuntimeCarriesScopeV2SkeletonThroughRelationsAndFourFilePublisher()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("business-link-v3-runtime-chain");
    ConfiguredTypedPipeline configured =
        writeBusinessLinkTypedPipelineConfigurationV3(
            "ontology-business-link-v3-runtime.yaml", fixture);
    AtomicInteger providerFactories = new AtomicInteger();
    List<StructuredModelRequest> requests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String kind = input.path("taskKind").asText();
                boolean review = request.taskKind().contains("REVIEW");
                String responseSchema =
                    review ? "ontology-typed-review-v4" : "ontology-typed-candidate-v4";
                JsonNode outputSchema = CANONICAL.parseCanonical(request.outputJsonSchema());
                assertThat(
                        outputSchema
                            .path("properties")
                            .path("schemaVersion")
                            .path("const")
                            .asText())
                    .isEqualTo(responseSchema);
                List<String> required = new ArrayList<>();
                outputSchema.path("required").forEach(value -> required.add(value.asText()));
                assertThat(required).contains("clueDispositions");
                ImmutableBytes response =
                    switch (kind) {
                      case "OBJECT" -> {
                        List<String> objectRequired = new ArrayList<>();
                        outputSchema
                            .path("properties")
                            .path("definitions")
                            .path("properties")
                            .path("objects")
                            .path("items")
                            .path("required")
                            .forEach(value -> objectRequired.add(value.asText()));
                        assertThat(objectRequired).contains("displayRole");
                        yield businessLinkFormalObjectResponseV4(
                            responseSchema, input.path("questionId").asText(), "E1");
                      }
                      case "RELATE" ->
                          businessLinkFormalUnresolvedRelateResponseV4(
                              responseSchema,
                              ExplicitTypedPipelineScript.catalogRef(input, "objects"));
                      default ->
                          throw new AssertionError("unexpected skeleton pipeline task " + kind);
                    };
                JsonNode responseDocument = CANONICAL.parseCanonical(response);
                assertThat(responseDocument.path("clueDispositions").isArray()).isTrue();
                if ("OBJECT".equals(kind)) {
                  assertThat(
                          responseDocument
                              .path("definitions")
                              .path("objects")
                              .get(0)
                              .path("displayRole")
                              .asText())
                      .isEqualTo("MAIN");
                }
                return new StructuredModelResponse(response, identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    String o0RunId = runId(prepared);
    CliResult corpusResult = artifact(configured.path(), o0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(corpusResult);
    assertThat(JSON.readTree(corpusResult.stdout()).path("schemaVersion").asText())
        .isEqualTo("ontology-corpus-v2");
    Path scope =
        writeBusinessLinkSkeletonScopeV2(
            temporaryDirectory.resolve("business-link-skeleton-scope-v2.json"));
    ImmutableBytes scopeBytes = ImmutableBytes.copyOf(Files.readAllBytes(scope));
    JsonNode scopeSnapshot = CANONICAL.parseCanonical(scopeBytes);

    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(cliDiagnostics(identified)).isZero();
    String o1RunId = runId(identified);
    JsonNode identification =
        JSON.readTree(artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(identification.path("schemaVersion").asText())
        .isEqualTo("ontology-identification-v3");
    assertThat(identification.path("scope")).isEqualTo(scopeSnapshot);
    JsonNode skeletonTaskRecord = findTaskRecord(identification, "object-skeleton");
    assertThat(skeletonTaskRecord.path("status").asText()).isEqualTo("REVIEWED");
    String skeletonProducingTaskId = skeletonTaskRecord.path("producingTaskId").asText();
    assertThat(skeletonProducingTaskId).isNotBlank();
    CliResult skeletonTaskResult =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            skeletonProducingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(skeletonTaskResult);
    JsonNode skeletonTask = JSON.readTree(skeletonTaskResult.stdout());
    assertThat(skeletonTask.path("completion").path("status").asText()).isEqualTo("REVIEWED");
    String skeletonReviewBase64 =
        skeletonTask.path("review").path("response").path("rawResponseBase64").asText();
    assertThat(skeletonReviewBase64).isNotBlank();
    JsonNode skeletonReview = JSON.readTree(Base64.getDecoder().decode(skeletonReviewBase64));
    assertThat(
            skeletonReview.path("definitions").path("objects").get(0).path("displayRole").asText())
        .isEqualTo("MAIN");

    Path relateSelection =
        writeRelateSelectionV2(
            temporaryDirectory.resolve("business-link-skeleton-relate-v2.json"),
            o0RunId,
            List.of(o1RunId),
            new SelectedRelateQuestion(
                "Q_RELATE", "relation-task", List.of(new ObjectSource(o1RunId, "Q_SKELETON"))));
    CliResult related =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            relateSelection.toString());
    assertThat(related.exitCode()).withFailMessage(cliDiagnostics(related)).isIn(0, 2);
    assertThat(related.stdout()).isNotBlank();
    String o2RunId = runId(related);
    JsonNode relations =
        JSON.readTree(artifact(configured.path(), o2RunId, "ONTOLOGY_RELATIONS").stdout());
    assertThat(relations.path("schemaVersion").asText()).isEqualTo("ontology-relations-v3");
    assertThat(relations.path("selection").path("schemaVersion").asText())
        .isEqualTo("ontology-selection-v2");
    assertThat(relations.path("taskOutcomes").get(0).path("status").asText()).isEqualTo("REVIEWED");

    Path publishSelection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("business-link-skeleton-publish-v2.json"),
            o0RunId,
            List.of(o1RunId),
            List.of(o2RunId));
    int providerFactoriesBeforePublish = providerFactories.get();
    CliResult published =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            publishSelection.toString());
    assertThat(published.exitCode()).withFailMessage(cliDiagnostics(published)).isIn(0, 2);
    assertThat(published.stdout()).isNotBlank();
    String o3RunId = runId(published);
    assertThat(providerFactories).hasValue(providerFactoriesBeforePublish);
    CliResult inspected = executePublic(configured.path(), "inspect", "--run", o3RunId);
    assertThat(inspected.exitCode()).withFailMessage(cliDiagnostics(inspected)).isZero();

    List<String> publicKeys = availablePublicArtifactKeys(configured.path(), o3RunId);
    assertThat(publicKeys)
        .containsExactlyInAnyOrder(
            "ONTOLOGY", "ONTOLOGY_COVERAGE", "ONTOLOGY_REVIEW", "ONTOLOGY_SOURCE_INDEX");
    JsonNode ontology = JSON.readTree(artifact(configured.path(), o3RunId, "ONTOLOGY").stdout());
    JsonNode coverage =
        JSON.readTree(artifact(configured.path(), o3RunId, "ONTOLOGY_COVERAGE").stdout());
    JsonNode review =
        JSON.readTree(artifact(configured.path(), o3RunId, "ONTOLOGY_REVIEW").stdout());
    assertThat(ontology.path("schemaVersion").asText()).isEqualTo("ontology-v2");
    assertThat(coverage.path("schemaVersion").asText()).isEqualTo("ontology-coverage-v3");
    assertThat(review.path("schemaVersion").asText()).isEqualTo("ontology-review-v3");

    int providerFactoriesBeforeOverview = providerFactories.get();
    CliResult businessOverview =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            o3RunId,
            "--key",
            "ONTOLOGY_BUSINESS_OVERVIEW",
            "--max-bytes",
            "5242880");
    assertThat(businessOverview.exitCode())
        .withFailMessage(cliDiagnostics(businessOverview))
        .isZero();
    assertThat(businessOverview.stdout())
        .startsWith("<!doctype html>")
        .contains("<pre class=\"mermaid\" id=\"ontology-business-graph\">")
        .contains("<script src=\"data:text/javascript;base64,")
        .contains("mermaid.run({querySelector: '#ontology-business-graph'});")
        .doesNotContain("<script src=\"https://", "<script src=\"http://");

    CliResult oversizedBusinessOverview =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            o3RunId,
            "--key",
            "ONTOLOGY_BUSINESS_OVERVIEW",
            "--max-bytes",
            "1");
    assertThat(oversizedBusinessOverview.exitCode()).isNotZero();
    assertThat(oversizedBusinessOverview.stdout()).isEmpty();
    assertThat(oversizedBusinessOverview.stderr()).contains("ONTOLOGY_ARTIFACT_TOO_LARGE");
    assertThat(providerFactories).hasValue(providerFactoriesBeforeOverview);

    ReopenedModulePublication o0Module =
        reopenedOntologyModule(fixture.runStore(), o0RunId, ONTOLOGY_POLICY_SET_V3);
    ReopenedModulePublication o1Module =
        reopenedOntologyModule(fixture.runStore(), o1RunId, ONTOLOGY_POLICY_SET_V3);
    ReopenedModulePublication o2Module =
        reopenedOntologyModule(fixture.runStore(), o2RunId, ONTOLOGY_POLICY_SET_V3);
    ReopenedModulePublication o3Module =
        reopenedOntologyModule(fixture.runStore(), o3RunId, ONTOLOGY_POLICY_SET_V3);
    assertThat(o0Module.payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .containsExactly("ontology-corpus-v2");
    assertThat(o1Module.payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .containsExactly("ontology-identification-v3");
    assertThat(o2Module.payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .containsExactly("ontology-relations-v3");
    assertThat(o3Module.payloads())
        .extracting(payload -> payload.descriptor().fileName())
        .containsExactly(
            "ontology-coverage.json",
            "ontology-review.json",
            "ontology-sources.jsonl",
            "ontology.json");
    assertThat(o3Module.payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .containsExactly(
            "ontology-coverage-v3", "ontology-review-v3", "ontology-source-v1", "ontology-v2");
    assertThat(o1Module.receipt().upstreamArtifacts())
        .containsAll(modulePayloadReferences(o0Module));
    assertThat(o2Module.receipt().upstreamArtifacts())
        .containsExactlyInAnyOrderElementsOf(modulePayloadReferences(o1Module));
    List<ArtifactReference> expectedO3Upstreams =
        new ArrayList<>(modulePayloadReferences(o1Module));
    expectedO3Upstreams.addAll(modulePayloadReferences(o2Module));
    assertThat(o3Module.receipt().upstreamArtifacts())
        .containsExactlyInAnyOrderElementsOf(expectedO3Upstreams);
    assertThat(requests)
        .extracting(request -> ExplicitTypedPipelineScript.input(request).path("taskKind").asText())
        .containsExactly("OBJECT", "OBJECT", "RELATE", "RELATE");
  }

  @Test
  void modelReadingRelateSelectionUsesReviewedObjectsAndPersistsActualMaterial() throws Exception {
    TechnicalFixture fixture = prepareRealR4("business-link-model-reading-relate");
    ConfiguredTypedPipeline configured =
        writeBusinessLinkTypedPipelineConfigurationV3(
            "ontology-business-link-model-reading-relate.yaml", fixture);
    String configurationText = Files.readString(configured.path(), StandardCharsets.UTF_8);
    assertThat(configurationText).contains("maxRequests: 8");
    Files.writeString(
        configured.path(),
        configurationText.replace("maxRequests: 8", "maxRequests: 4"),
        StandardCharsets.UTF_8);
    assertThat(Files.readString(configured.path(), StandardCharsets.UTF_8))
        .contains("maxRequests: 4");

    AtomicInteger providerFactories = new AtomicInteger();
    AtomicInteger readingRounds = new AtomicInteger();
    AtomicReference<String> readUnitRef = new AtomicReference<>();
    AtomicReference<String> expectedMethodRef = new AtomicReference<>();
    AtomicReference<String> selectedClueRef = new AtomicReference<>();
    AtomicReference<String> selectedClueMeaning = new AtomicReference<>();
    List<StructuredModelRequest> relateRequests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String schemaVersion = input.path("schemaVersion").asText();
                if ("ontology-reading-input-v4".equals(schemaVersion)) {
                  relateRequests.add(request);
                  assertThat(input.path("taskKind").asText()).isEqualTo("RELATE");
                  assertThat(input.path("readingMode").asText()).isEqualTo("MODEL");
                  assertThat(input.path("questionId").asText()).isEqualTo("Q_RELATE");
                  int round = readingRounds.incrementAndGet();
                  if (round == 1) {
                    JsonNode navigation = input.path("visibleScope").path("navigation");
                    JsonNode selectedEntry = null;
                    for (JsonNode candidate : navigation) {
                      if ("E1".equals(candidate.path("entryRef").asText())) {
                        selectedEntry = candidate;
                        break;
                      }
                    }
                    assertThat(selectedEntry).isNotNull();
                    JsonNode visibleClues = selectedEntry.path("clues");
                    assertThat(visibleClues.size()).isGreaterThan(0);
                    selectedClueRef.set(visibleClues.get(0).path("clueRef").asText());
                    selectedClueMeaning.set(visibleClues.get(0).path("keyDisplay").asText());
                    assertThat(selectedClueRef.get()).matches("K[1-9][0-9]*");
                    assertThat(selectedClueMeaning.get()).isNotBlank();

                    JsonNode available = input.path("visibleScope").path("availableUnitUses");
                    boolean selectedMethodAvailable = false;
                    for (JsonNode use : available) {
                      if (expectedMethodRef.get().equals(use.path("unitRef").asText())
                          && "E1".equals(use.path("entryRef").asText())) {
                        selectedMethodAvailable = true;
                      }
                    }
                    assertThat(available.size()).isGreaterThan(0);
                    assertThat(selectedMethodAvailable).isTrue();
                    readUnitRef.set(expectedMethodRef.get());
                    return new StructuredModelResponse(
                        businessLinkModelReadingResponseV4(
                            "NEEDS_MORE_MATERIAL",
                            readUnitRef.get(),
                            "E1",
                            selectedClueRef.get(),
                            false),
                        identity);
                  }
                  assertThat(round).isEqualTo(2);
                  assertThat(stringValues(input.path("visibleScope").path("selectedEntryRefs")))
                      .containsExactly("E1");
                  assertThat(stringValues(input.path("visibleScope").path("selectedClueRefs")))
                      .contains(selectedClueRef.get());
                  assertThat(input.path("visibleScope").path("activeUnitUses").toString())
                      .contains(readUnitRef.get(), "E1");
                  assertThat(containsText(input.path("readingPacket"), "public String list()"))
                      .isTrue();
                  assertThat(containsText(input.path("readingPacket"), "return \"neutral\""))
                      .isTrue();
                  return new StructuredModelResponse(
                      businessLinkModelReadingResponseV4(
                          "READY_TO_EXTRACT", readUnitRef.get(), "E1", null, true),
                      identity);
                }
                if ("ontology-typed-formal-input-v4".equals(schemaVersion)
                    && "RELATE".equals(input.path("taskKind").asText())) {
                  relateRequests.add(request);
                  assertThat(input.path("questionId").asText()).isEqualTo("Q_RELATE");
                  assertThat(input.path("taskKind").asText()).isEqualTo("RELATE");
                  assertThat(stringValues(input.path("visibleClueRefs")))
                      .containsExactly(selectedClueRef.get());
                  JsonNode packet = input.path("readingPacket");
                  assertThat(packet.path("visibleClues").size()).isEqualTo(1);
                  JsonNode packetClue = packet.path("visibleClues").get(0);
                  assertThat(packetClue.path("ref").asText()).isEqualTo(selectedClueRef.get());
                  assertThat(packetClue.path("value").asText())
                      .isEqualTo(selectedClueMeaning.get());
                  assertThat(containsText(packet, "public String list() { return \"neutral\"; }"))
                      .isTrue();
                  JsonNode reviewedObject =
                      ExplicitTypedPipelineScript.catalogEntry(input, "objects");
                  assertThat(reviewedObject.path("definition").path("displayRole").asText())
                      .isEqualTo("MAIN");
                  assertThat(reviewedObject.path("definition").path("name").asText())
                      .isEqualTo("Neutral source-backed record");
                  boolean review = request.taskKind().contains("REVIEW");
                  String responseSchema =
                      review ? "ontology-typed-review-v4" : "ontology-typed-candidate-v4";
                  String objectRef = reviewedObject.path("catalogRef").asText();
                  return new StructuredModelResponse(
                      businessLinkRelateResponseWithClueDispositionV4(
                          responseSchema, objectRef, selectedClueRef.get()),
                      identity);
                }
                assertThat(input.path("taskKind").asText()).isEqualTo("OBJECT");
                String responseSchema =
                    request.taskKind().contains("REVIEW")
                        ? "ontology-typed-review-v4"
                        : "ontology-typed-candidate-v4";
                return new StructuredModelResponse(
                    businessLinkFormalObjectResponseV4(
                        responseSchema, input.path("questionId").asText(), "E1"),
                    identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    String o0RunId = runId(prepared);
    JsonNode corpus =
        JSON.readTree(artifact(configured.path(), o0RunId, "ONTOLOGY_CORPUS").stdout());
    assertThat(corpus.path("schemaVersion").asText()).isEqualTo("ontology-corpus-v2");
    String entryRef = entryRef(corpus);
    String methodRef = unitRef(corpus, "JAVA_METHOD", "method:neutral-list");
    assertThat(entryRef).isEqualTo("E1");
    expectedMethodRef.set(methodRef);

    Path skeletonScope =
        writeBusinessLinkSkeletonScopeV2(
            temporaryDirectory.resolve("business-link-model-read-skeleton-scope.json"));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            skeletonScope.toString());
    assertThat(identified.exitCode()).withFailMessage(cliDiagnostics(identified)).isZero();
    String o1RunId = runId(identified);
    JsonNode identification =
        JSON.readTree(artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    JsonNode skeletonRecord = findTaskRecord(identification, "object-skeleton");
    assertThat(skeletonRecord.path("status").asText()).isEqualTo("REVIEWED");
    String skeletonProducingTaskId = skeletonRecord.path("producingTaskId").asText();
    CliResult skeletonObservationResult =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            skeletonProducingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(skeletonObservationResult);
    JsonNode skeletonObservation = JSON.readTree(skeletonObservationResult.stdout());
    String skeletonReviewBase64 =
        skeletonObservation.path("review").path("response").path("rawResponseBase64").asText();
    JsonNode actualReviewedObject =
        JSON.readTree(Base64.getDecoder().decode(skeletonReviewBase64))
            .path("definitions")
            .path("objects")
            .get(0);
    assertThat(actualReviewedObject.path("displayRole").asText()).isEqualTo("MAIN");

    Path relateSelection =
        writeBusinessLinkModelReadRelateSelectionV2(
            temporaryDirectory.resolve("business-link-model-read-relate-selection.json"),
            o0RunId,
            o1RunId,
            entryRef);
    CliResult related =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            relateSelection.toString());
    assertThat(related.exitCode()).withFailMessage(cliDiagnostics(related)).isZero();
    String o2RunId = runId(related);
    JsonNode o2Observation =
        assertCommandAndInspectionAgree(related, configured.path(), "RELATE_ONTOLOGY", o2RunId, 4);
    assertThat(o2Observation.path("modelRequestsDispatched").asInt()).isEqualTo(4);
    assertThat(readingRounds).hasValue(2);
    assertThat(relateRequests).hasSize(4);
    JsonNode relations =
        JSON.readTree(artifact(configured.path(), o2RunId, "ONTOLOGY_RELATIONS").stdout());
    assertThat(relations.path("status").asText()).isEqualTo("REVIEWED");
    JsonNode savedQuestion = relations.path("selection").path("questions").get(0);
    assertThat(savedQuestion.path("readingMode").asText()).isEqualTo("MODEL");
    assertThat(savedQuestion.path("unitUses").size()).isZero();
    JsonNode readingSelection = relations.path("readingSelections").get(0);
    assertThat(readingSelection.path("taskId").asText()).isEqualTo("model-relate-task");
    assertThat(readingSelection.path("questionId").asText()).isEqualTo("Q_RELATE");
    assertThat(readingSelection.path("status").asText()).isEqualTo("READY");
    assertThat(stringValues(readingSelection.path("selectedEntries"))).containsExactly(entryRef);
    assertThat(stringValues(readingSelection.path("selectedClues")))
        .containsExactly(selectedClueRef.get());

    JsonNode relateTaskRecord = findTaskRecord(relations, "model-relate-task");
    assertThat(relateTaskRecord.path("status").asText()).isEqualTo("REVIEWED");
    String relateProducingTaskId = relateTaskRecord.path("producingTaskId").asText();
    CliResult relateObservationResult =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            o2RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            relateProducingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(relateObservationResult);
    JsonNode relateObservation = JSON.readTree(relateObservationResult.stdout());
    JsonNode extractRequest =
        JSON.readTree(relateRequests.get(2).untrustedInputJson().copyToByteArray());
    JsonNode reviewRequest =
        JSON.readTree(relateRequests.get(3).untrustedInputJson().copyToByteArray());
    assertSavedFormalRequest(
        relateObservation.path("extract").path("request"), relateRequests.get(2));
    assertSavedFormalRequest(
        relateObservation.path("review").path("request"), relateRequests.get(3));
    assertThat(extractRequest.path("readingPacket")).isEqualTo(reviewRequest.path("readingPacket"));
    assertThat(extractRequest.path("reviewedCatalog"))
        .isEqualTo(reviewRequest.path("reviewedCatalog"));
    assertThat(
            extractRequest
                .path("reviewedCatalog")
                .path("entries")
                .get(0)
                .path("definition")
                .path("name")
                .asText())
        .isEqualTo(actualReviewedObject.path("name").asText());
    assertThat(providerFactories.get()).isGreaterThan(0);
    Path publicationSelection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("business-link-model-read-publication.json"),
            o0RunId,
            List.of(o1RunId),
            List.of(o2RunId));
    int factoriesBeforePublication = providerFactories.get();
    CliResult publication =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            publicationSelection.toString());
    assertThat(publication.exitCode()).withFailMessage(cliDiagnostics(publication)).isZero();
    assertThat(providerFactories).hasValue(factoriesBeforePublication);
    assertThat(relateRequests).hasSize(4);
    CliResult publishedCoverage =
        artifact(configured.path(), runId(publication), "ONTOLOGY_COVERAGE");
    assertArtifactAvailable(publishedCoverage);
    assertThat(JSON.readTree(publishedCoverage.stdout()).path("clueDispositions").toString())
        .contains(selectedClueRef.get());
  }

  @Test
  void newBusinessLinkModelO1UsesCurrentPhaseReadingBoundsNotSavedCorpusBounds() throws Exception {
    TechnicalFixture fixture = prepareRealR4("business-link-current-phase-reading-bounds");
    ConfiguredTypedPipeline preparationConfiguration =
        writeBusinessLinkTypedPipelineConfigurationV3(
            "ontology-business-link-current-phase-preparation.yaml", fixture);
    AtomicInteger providerFactories = new AtomicInteger();
    AtomicInteger readingRounds = new AtomicInteger();
    AtomicReference<String> selectedUnitRef = new AtomicReference<>();
    AtomicReference<String> selectedEntryRef = new AtomicReference<>();
    List<StructuredModelRequest> requests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                if ("ontology-reading-input-v4".equals(input.path("schemaVersion").asText())) {
                  assertThat(input.path("taskKind").asText()).isEqualTo("OBJECT");
                  int round = readingRounds.incrementAndGet();
                  if (round == 1) {
                    JsonNode availableUses = input.path("visibleScope").path("availableUnitUses");
                    assertThat(availableUses.isArray()).isTrue();
                    assertThat(availableUses.size()).isGreaterThan(0);
                    selectedUnitRef.set(availableUses.get(0).path("unitRef").asText());
                    selectedEntryRef.set(availableUses.get(0).path("entryRef").asText());
                    assertThat(selectedUnitRef.get()).matches("U[1-9][0-9]*");
                    assertThat(selectedEntryRef.get()).isEqualTo("E1");
                    return new StructuredModelResponse(
                        businessLinkModelReadingResponseV4(
                            "NEEDS_MORE_MATERIAL",
                            selectedUnitRef.get(),
                            selectedEntryRef.get(),
                            null,
                            false),
                        identity);
                  }
                  assertThat(round).isEqualTo(2);
                  return new StructuredModelResponse(
                      businessLinkModelReadingResponseV4(
                          "READY_TO_EXTRACT",
                          selectedUnitRef.get(),
                          selectedEntryRef.get(),
                          null,
                          true),
                      identity);
                }
                assertThat(input.path("schemaVersion").asText())
                    .isEqualTo("ontology-typed-formal-input-v4");
                assertThat(input.path("taskKind").asText()).isEqualTo("OBJECT");
                String responseSchema =
                    request.taskKind().contains("REVIEW")
                        ? "ontology-typed-review-v4"
                        : "ontology-typed-candidate-v4";
                return new StructuredModelResponse(
                    businessLinkFormalObjectResponseV4(
                        responseSchema, input.path("questionId").asText(), selectedEntryRef.get()),
                    identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            preparationConfiguration.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    String o0RunId = runId(prepared);
    CliResult o0BeforeResult =
        artifact(preparationConfiguration.path(), o0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(o0BeforeResult);
    JsonNode o0Before = JSON.readTree(o0BeforeResult.stdout());
    assertThat(o0Before.path("preparationControls").path("maxActionsPerRound").asInt())
        .isEqualTo(4);
    assertThat(o0Before.path("preparationControls").path("maxNavigationEntries").asInt())
        .isEqualTo(8);
    String originalCorpusIdentity = o0Before.path("corpusIdentity").asText();
    assertThat(originalCorpusIdentity).isNotBlank();
    assertThat(providerFactories).hasValue(0);

    ConfiguredTypedPipeline currentConfiguration =
        writeBusinessLinkTypedPipelineConfigurationV3(
            "ontology-business-link-current-phase-identification.yaml", fixture);
    String narrowedConfiguration =
        Files.readString(currentConfiguration.path(), StandardCharsets.UTF_8);
    assertThat(narrowedConfiguration).contains("maxActionsPerRound: 4", "maxNavigationEntries: 8");
    narrowedConfiguration =
        narrowedConfiguration
            .replace("maxActionsPerRound: 4", "maxActionsPerRound: 1")
            .replace("maxNavigationEntries: 8", "maxNavigationEntries: 2");
    Files.writeString(currentConfiguration.path(), narrowedConfiguration, StandardCharsets.UTF_8);

    Path modelScope =
        writeBusinessLinkModelSkeletonScopeV2(
            temporaryDirectory.resolve("business-link-current-phase-model-skeleton.json"));
    CliResult identified =
        executeWithFactory(
            currentConfiguration.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            modelScope.toString());
    assertThat(identified.exitCode()).withFailMessage(cliDiagnostics(identified)).isZero();
    assertThat(readingRounds).hasValue(2);

    StructuredModelRequest firstReadingRequest =
        requests.stream()
            .filter(
                request ->
                    "ontology-reading-input-v4"
                        .equals(
                            ExplicitTypedPipelineScript.input(request)
                                .path("schemaVersion")
                                .asText()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("O1 did not dispatch its v4 reading request"));
    JsonNode actualInput = ExplicitTypedPipelineScript.input(firstReadingRequest);
    assertThat(actualInput.path("maxActionsPerRound").asInt()).isEqualTo(1);
    assertThat(actualInput.path("maxNavigationEntries").asInt()).isEqualTo(2);
    JsonNode actualProviderSchema =
        CANONICAL.parseCanonical(firstReadingRequest.outputJsonSchema());
    assertThat(actualProviderSchema.path("properties").path("actions").path("maxItems").asInt())
        .isEqualTo(1);
    assertThat(
            actualProviderSchema
                .path("$defs")
                .path("query")
                .path("properties")
                .path("limit")
                .path("maximum")
                .asInt())
        .isEqualTo(2);
    assertThat(
            actualProviderSchema
                .path("$defs")
                .path("literalSearch")
                .path("properties")
                .path("limit")
                .path("maximum")
                .asInt())
        .isEqualTo(2);

    CliResult o0AfterResult = artifact(currentConfiguration.path(), o0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(o0AfterResult);
    JsonNode o0After = JSON.readTree(o0AfterResult.stdout());
    assertThat(o0After.path("corpusIdentity").asText()).isEqualTo(originalCorpusIdentity);
    assertThat(o0After.path("preparationControls")).isEqualTo(o0Before.path("preparationControls"));
    assertThat(o0AfterResult.stdout()).isEqualTo(o0BeforeResult.stdout());
  }

  @Test
  void relationReadingSelectionsRejectAnotherKnownClueForExplicitOrUnstartedTasks()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("business-link-reading-selections-owner");
    OntologyEvidenceCorpus corpus = reopenedPreparedOntologyEvidenceCorpus(fixture);
    String entryId = corpus.navigation(0, 1).entries().get(0).entryId();
    String entryRef = corpus.aliases().entryRef(entryId);
    String clueRef =
        corpus.entryClues(entryId, Integer.MAX_VALUE).clues().stream()
            .map(clue -> corpus.aliases().clueRef(clue.kind(), clue.lookupKey()))
            .findFirst()
            .orElseThrow();
    ObjectNode document = JSON.createObjectNode();
    ObjectNode question = document.putObject("selection").putArray("questions").addObject();
    question.put("questionId", "Q_EXPLICIT");
    question.put("taskId", "T_EXPLICIT");
    question.put("readingMode", "EXPLICIT");
    question.putArray("entryRefs").add(entryRef);
    question.putArray("clueRefs");
    ObjectNode reading = document.putArray("readingSelections").addObject();
    reading.put("questionId", "Q_EXPLICIT");
    reading.put("taskId", "T_EXPLICIT");
    reading.put("status", "READY");
    reading.put("issueCode", "");
    reading.putArray("selectedEntries").add(entryRef);
    reading.putArray("selectedClues");
    assertThat(OntologyRelationReadingSelections.read(document, corpus)).containsKey("T_EXPLICIT");
    reading.withArray("selectedClues").add(clueRef);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> OntologyRelationReadingSelections.read(document, corpus))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_SELECTED_RUN_INVALID");
    question.put("readingMode", "MODEL");
    assertThat(OntologyRelationReadingSelections.read(document, corpus)).containsKey("T_EXPLICIT");
    reading.put("status", "NOT_STARTED");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> OntologyRelationReadingSelections.read(document, corpus))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_SELECTED_RUN_INVALID");
  }

  @Test
  void reviewedRelationReadingCluesMustEqualTheFrozenTaskSelection() {
    ObjectNode actualReading = JSON.createObjectNode();
    actualReading.put("status", "READY");
    actualReading.putArray("selectedClues").add("K1");
    OntologyRelationReadingSelections.requireReviewedClues(actualReading, List.of("K1"));
    actualReading.withArray("selectedClues").add("K2");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                OntologyRelationReadingSelections.requireReviewedClues(
                    actualReading, List.of("K1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_SELECTED_RUN_INVALID");
    actualReading.withArray("selectedClues").removeAll();
    actualReading.withArray("selectedClues").add("K1");
    actualReading.put("status", "FAILED");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                OntologyRelationReadingSelections.requireReviewedClues(
                    actualReading, List.of("K1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_SELECTED_RUN_INVALID");
  }

  @Test
  void modelReadingFailureKeepsSelectedClueInO3DispositionDenominator() throws Exception {
    TechnicalFixture fixture = prepareRealR4("business-link-model-reading-failure-clue");
    ConfiguredTypedPipeline configured =
        writeBusinessLinkTypedPipelineConfigurationV3(
            "ontology-business-link-model-reading-failure-clue.yaml", fixture);
    String configurationText = Files.readString(configured.path(), StandardCharsets.UTF_8);
    assertThat(configurationText).contains("maxRequests: 8");
    Files.writeString(
        configured.path(),
        configurationText.replace("maxRequests: 8", "maxRequests: 4"),
        StandardCharsets.UTF_8);

    AtomicInteger readingRounds = new AtomicInteger();
    AtomicInteger providerFactories = new AtomicInteger();
    AtomicReference<String> methodRef = new AtomicReference<>();
    AtomicReference<String> clueRef = new AtomicReference<>();
    List<StructuredModelRequest> o2Requests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String schemaVersion = input.path("schemaVersion").asText();
                if ("ontology-reading-input-v4".equals(schemaVersion)) {
                  o2Requests.add(request);
                  assertThat(input.path("taskKind").asText()).isEqualTo("RELATE");
                  assertThat(input.path("readingMode").asText()).isEqualTo("MODEL");
                  assertThat(input.path("questionId").asText()).isEqualTo("Q_MODEL_PARTIAL");
                  int round = readingRounds.incrementAndGet();
                  if (round == 1) {
                    JsonNode selectedEntry = null;
                    for (JsonNode candidate : input.path("visibleScope").path("navigation")) {
                      if ("E1".equals(candidate.path("entryRef").asText())) {
                        selectedEntry = candidate;
                        break;
                      }
                    }
                    assertThat(selectedEntry).isNotNull();
                    assertThat(selectedEntry.path("clues").size()).isGreaterThan(0);
                    clueRef.set(selectedEntry.path("clues").get(0).path("clueRef").asText());
                    assertThat(clueRef.get()).matches("K[1-9][0-9]*");
                    boolean methodIsAvailable = false;
                    for (JsonNode use : input.path("visibleScope").path("availableUnitUses")) {
                      if (methodRef.get().equals(use.path("unitRef").asText())
                          && "E1".equals(use.path("entryRef").asText())) {
                        methodIsAvailable = true;
                      }
                    }
                    assertThat(methodIsAvailable).isTrue();
                    return new StructuredModelResponse(
                        businessLinkModelReadingResponseV4(
                            "NEEDS_MORE_MATERIAL", methodRef.get(), "E1", clueRef.get(), false),
                        identity);
                  }
                  assertThat(round).isEqualTo(2);
                  assertThat(stringValues(input.path("visibleScope").path("selectedClueRefs")))
                      .containsExactly(clueRef.get());
                  assertThat(containsText(input.path("readingPacket"), "public String list()"))
                      .isTrue();
                  return new StructuredModelResponse(
                      businessLinkModelReadingResponseV4(
                          "NEEDS_MORE_MATERIAL", methodRef.get(), "E1", null, true),
                      identity);
                }
                if ("ontology-typed-formal-input-v4".equals(schemaVersion)
                    && "RELATE".equals(input.path("taskKind").asText())) {
                  o2Requests.add(request);
                  assertThat(input.path("questionId").asText()).isEqualTo("Q_EXPLICIT_RELATE");
                  assertThat(input.path("visibleClueRefs").size()).isZero();
                  assertThat(containsText(input.path("readingPacket"), "public String list()"))
                      .isTrue();
                  String responseSchema =
                      request.taskKind().contains("REVIEW")
                          ? "ontology-typed-review-v4"
                          : "ontology-typed-candidate-v4";
                  String objectRef = ExplicitTypedPipelineScript.catalogRef(input, "objects");
                  return new StructuredModelResponse(
                      businessLinkFormalUnresolvedRelateResponseV4(responseSchema, objectRef),
                      identity);
                }
                assertThat(input.path("schemaVersion").asText())
                    .isEqualTo("ontology-typed-formal-input-v4");
                assertThat(input.path("taskKind").asText()).isEqualTo("OBJECT");
                String responseSchema =
                    request.taskKind().contains("REVIEW")
                        ? "ontology-typed-review-v4"
                        : "ontology-typed-candidate-v4";
                return new StructuredModelResponse(
                    businessLinkFormalObjectResponseV4(
                        responseSchema, input.path("questionId").asText(), "E1"),
                    identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    String o0RunId = runId(prepared);
    JsonNode corpus =
        JSON.readTree(artifact(configured.path(), o0RunId, "ONTOLOGY_CORPUS").stdout());
    String entryRef = entryRef(corpus);
    assertThat(entryRef).isEqualTo("E1");
    methodRef.set(unitRef(corpus, "JAVA_METHOD", "method:neutral-list"));

    Path skeletonScope =
        writeBusinessLinkSkeletonScopeV2(
            temporaryDirectory.resolve("business-link-model-failure-skeleton-scope.json"));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            skeletonScope.toString());
    assertThat(identified.exitCode()).withFailMessage(cliDiagnostics(identified)).isZero();
    String o1RunId = runId(identified);
    Path relateSelection =
        writeBusinessLinkModelFailureAndExplicitRelateSelectionV2(
            temporaryDirectory.resolve("business-link-model-failure-two-relates.json"),
            o0RunId,
            o1RunId,
            entryRef,
            methodRef.get());
    CliResult related =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            relateSelection.toString());
    assertThat(related.exitCode()).withFailMessage(cliDiagnostics(related)).isIn(0, 2);
    String o2RunId = runId(related);
    JsonNode o2Observation =
        assertCommandAndInspectionAgree(related, configured.path(), "RELATE_ONTOLOGY", o2RunId, 4);
    assertThat(o2Observation.path("modelRequestsDispatched").asInt()).isEqualTo(4);
    assertThat(readingRounds).hasValue(2);
    assertThat(o2Requests).hasSize(4);
    JsonNode relations =
        JSON.readTree(artifact(configured.path(), o2RunId, "ONTOLOGY_RELATIONS").stdout());
    assertThat(relations.path("status").asText()).isEqualTo("PARTIAL");
    JsonNode failedReading = null;
    for (JsonNode selection : relations.path("readingSelections")) {
      if ("model-relate-partial".equals(selection.path("taskId").asText())) {
        failedReading = selection;
        break;
      }
    }
    assertThat(failedReading).isNotNull();
    assertThat(failedReading.path("questionId").asText()).isEqualTo("Q_MODEL_PARTIAL");
    assertThat(failedReading.path("status").asText()).isEqualTo("INCOMPLETE");
    assertThat(failedReading.path("issueCode").asText()).isEqualTo("ONTOLOGY_READING_NO_PROGRESS");
    assertThat(stringValues(failedReading.path("selectedEntries"))).containsExactly(entryRef);
    assertThat(stringValues(failedReading.path("selectedClues"))).containsExactly(clueRef.get());

    Path publishSelection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("business-link-model-failure-publish-v2.json"),
            o0RunId,
            List.of(o1RunId),
            List.of(o2RunId));
    int providerFactoriesBeforePublish = providerFactories.get();
    int requestsBeforePublish = o2Requests.size();
    CliResult published =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            publishSelection.toString());
    assertThat(published.exitCode()).withFailMessage(cliDiagnostics(published)).isIn(0, 2);
    String o3RunId = runId(published);
    assertThat(providerFactories).hasValue(providerFactoriesBeforePublish);
    assertThat(o2Requests).hasSize(requestsBeforePublish);
    CliResult businessCoverageResult = artifact(configured.path(), o3RunId, "ONTOLOGY_COVERAGE");
    assertArtifactAvailable(businessCoverageResult);
    JsonNode businessCoverage = JSON.readTree(businessCoverageResult.stdout());
    JsonNode failedClueDisposition = null;
    for (JsonNode disposition : businessCoverage.path("clueDispositions")) {
      if ("model-relate-partial".equals(disposition.path("taskId").asText())
          && clueRef.get().equals(disposition.path("clueRef").asText())) {
        failedClueDisposition = disposition;
        break;
      }
    }
    assertThat(failedClueDisposition).isNotNull();
    assertThat(failedClueDisposition.path("outcome").asText()).isEqualTo("UNPROCESSED");
  }

  @Test
  void scopeV2EnrichmentUsesItsReviewedSkeletonAndPublishRequiresTheExternalIdentification()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("business-link-enrichment-external-identification");
    ConfiguredTypedPipeline configured =
        writeBusinessLinkTypedPipelineConfigurationV3(
            "ontology-business-link-enrichment-external.yaml", fixture);
    AtomicInteger providerFactories = new AtomicInteger();
    List<StructuredModelRequest> requests = new ArrayList<>();
    AtomicReference<String> externalObjectCatalogRef = new AtomicReference<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String kind = input.path("taskKind").asText();
                boolean review = request.taskKind().contains("REVIEW");
                String responseSchema =
                    review ? "ontology-typed-review-v4" : "ontology-typed-candidate-v4";
                assertThat(input.path("schemaVersion").asText())
                    .isEqualTo("ontology-typed-formal-input-v4");
                assertThat(
                        CANONICAL
                            .parseCanonical(request.outputJsonSchema())
                            .path("properties")
                            .path("schemaVersion")
                            .path("const")
                            .asText())
                    .isEqualTo(responseSchema);
                JsonNode visibleEntries = input.path("reviewedCatalog").path("entries");
                ImmutableBytes response;
                if ("OBJECT".equals(kind)) {
                  assertThat(visibleEntries).isEmpty();
                  String questionId = input.path("questionId").asText();
                  response =
                      businessLinkFormalObjectResponseV4(
                          responseSchema,
                          questionId,
                          "E1",
                          "Q_RELATION_OBJECT".equals(questionId)
                              ? "Neutral independent relation endpoint"
                              : "Neutral source-backed record",
                          "A neutral source-backed record shape for " + questionId + ".");
                } else if ("ACTION".equals(kind) || "ANALYTIC".equals(kind)) {
                  assertThat(visibleEntries).hasSize(1);
                  JsonNode visibleObject = visibleEntries.get(0);
                  assertThat(visibleObject.path("definitionType").asText()).isEqualTo("objects");
                  assertThat(visibleObject.path("definition").path("displayRole").asText())
                      .isEqualTo("MAIN");
                  String objectRef = visibleObject.path("catalogRef").asText();
                  assertThat(objectRef).matches("B[1-9][0-9]*");
                  String previous = externalObjectCatalogRef.get();
                  if (previous == null) {
                    externalObjectCatalogRef.set(objectRef);
                  } else {
                    assertThat(objectRef).isEqualTo(previous);
                  }
                  String questionId = input.path("questionId").asText();
                  response =
                      "ACTION".equals(kind)
                          ? businessLinkFormalActionResponseV4(
                              responseSchema, questionId, "E1", objectRef)
                          : businessLinkFormalAnalyticResponseV4(
                              responseSchema,
                              questionId,
                              "E1",
                              objectRef,
                              visibleObject.path("propertyRefs").get(0).asText());
                } else if ("RELATE".equals(kind)) {
                  assertThat(visibleEntries).hasSize(1);
                  response =
                      businessLinkFormalUnresolvedRelateResponseV4(
                          responseSchema, visibleEntries.get(0).path("catalogRef").asText());
                } else {
                  throw new AssertionError("unexpected business-link task kind " + kind);
                }
                JsonNode responseDocument = CANONICAL.parseCanonical(response);
                assertThat(responseDocument.path("clueDispositions").isArray()).isTrue();
                return new StructuredModelResponse(response, identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    String o0RunId = runId(prepared);
    assertThat(requests).isEmpty();

    Path skeletonScope =
        writeBusinessLinkSkeletonScopeV2(
            temporaryDirectory.resolve("business-link-enrichment-skeleton-scope.json"));
    CliResult skeletonIdentified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            skeletonScope.toString());
    assertThat(skeletonIdentified.exitCode())
        .withFailMessage(cliDiagnostics(skeletonIdentified))
        .isZero();
    String skeletonO1RunId = runId(skeletonIdentified);
    ReopenedModulePublication skeletonO1 =
        reopenedOntologyModule(fixture.runStore(), skeletonO1RunId, ONTOLOGY_POLICY_SET_V3);

    Path enrichmentScope =
        writeBusinessLinkEnrichmentScopeV2(
            temporaryDirectory.resolve("business-link-enrichment-scope-v2.json"), skeletonO1RunId);
    int requestsBeforeEnrichment = requests.size();
    CliResult enriched =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            enrichmentScope.toString());
    assertThat(enriched.exitCode()).withFailMessage(cliDiagnostics(enriched)).isZero();
    String enrichmentO1RunId = runId(enriched);
    assertThat(requests.subList(requestsBeforeEnrichment, requests.size()))
        .extracting(
            request -> {
              JsonNode input = ExplicitTypedPipelineScript.input(request);
              return input.path("questionId").asText()
                  + "/"
                  + input.path("taskKind").asText()
                  + "/"
                  + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT");
            })
        .containsExactly(
            "Q_ENRICHMENT/ACTION/EXTRACT",
            "Q_ENRICHMENT/ACTION/REVIEW",
            "Q_ENRICHMENT/ANALYTIC/EXTRACT",
            "Q_ENRICHMENT/ANALYTIC/REVIEW");
    assertThat(externalObjectCatalogRef.get()).isNotBlank();

    CliResult enrichmentIdentificationResult =
        artifact(configured.path(), enrichmentO1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(enrichmentIdentificationResult);
    JsonNode enrichmentIdentification = JSON.readTree(enrichmentIdentificationResult.stdout());
    JsonNode actionTaskRecord = findTaskRecord(enrichmentIdentification, "action-enrichment");
    assertThat(actionTaskRecord.path("status").asText()).isEqualTo("REVIEWED");
    String actionProducingTaskId = actionTaskRecord.path("producingTaskId").asText();
    assertThat(actionProducingTaskId).isNotBlank();

    CliResult actionTaskResult =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            enrichmentO1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            actionProducingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(actionTaskResult);
    JsonNode actionTask = JSON.readTree(actionTaskResult.stdout());
    assertThat(actionTask.path("completion").path("status").asText()).isEqualTo("REVIEWED");
    String actionReviewBase64 =
        actionTask.path("review").path("response").path("rawResponseBase64").asText();
    assertThat(actionReviewBase64).isNotBlank();
    JsonNode actionReview = JSON.readTree(Base64.getDecoder().decode(actionReviewBase64));
    assertThat(
            stringValues(
                actionReview
                    .path("definitions")
                    .path("operations")
                    .get(0)
                    .path("targetObjectRefs")))
        .containsExactly(externalObjectCatalogRef.get());

    ReopenedModulePublication enrichmentO1 =
        reopenedOntologyModule(fixture.runStore(), enrichmentO1RunId, ONTOLOGY_POLICY_SET_V3);
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      var persistedEnrichment =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
              store, AnalysisRunId.parse(enrichmentO1RunId));
      assertThat(persistedEnrichment.request().ontologyInputs().identificationPublications())
          .containsExactly(skeletonO1.reference());
      JsonNode persistedRequest = CANONICAL.parseCanonical(persistedEnrichment.canonicalJson());
      JsonNode identification =
          JSON.readTree(
              artifact(configured.path(), enrichmentO1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
      assertThat(identification.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(persistedRequest.path("ontologyInputs").path("identificationPublications"));
    }

    Path independentObjectScope =
        writeBusinessLinkStandaloneSkeletonScopeV2(
            temporaryDirectory.resolve("business-link-enrichment-independent-skeleton.json"));
    CliResult independentObjectIdentified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            independentObjectScope.toString());
    assertThat(independentObjectIdentified.exitCode())
        .withFailMessage(cliDiagnostics(independentObjectIdentified))
        .isZero();
    String independentO1RunId = runId(independentObjectIdentified);
    ReopenedModulePublication independentO1 =
        reopenedOntologyModule(fixture.runStore(), independentO1RunId, ONTOLOGY_POLICY_SET_V3);

    Path relationSelection =
        writeRelateSelectionV2(
            temporaryDirectory.resolve("business-link-enrichment-independent-relation.json"),
            o0RunId,
            List.of(independentO1RunId),
            new SelectedRelateQuestion(
                "Q_RELATION_OBJECT",
                "relation-to-independent-object",
                List.of(new ObjectSource(independentO1RunId, "Q_RELATION_OBJECT"))));
    CliResult related =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            relationSelection.toString());
    assertThat(related.exitCode()).withFailMessage(cliDiagnostics(related)).isZero();
    String o2RunId = runId(related);
    ReopenedModulePublication o2 =
        reopenedOntologyModule(fixture.runStore(), o2RunId, ONTOLOGY_POLICY_SET_V3);
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      var persistedO2 =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, AnalysisRunId.parse(o2RunId));
      assertThat(persistedO2.request().ontologyInputs().identificationPublications())
          .containsExactly(independentO1.reference());
    }

    Path missingExternalSelection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("business-link-enrichment-publish-missing-external.json"),
            o0RunId,
            List.of(enrichmentO1RunId, independentO1RunId),
            List.of(o2RunId));
    int providerFactoriesBeforeMissingExternal = providerFactories.get();
    int requestsBeforeMissingExternal = requests.size();
    CliResult missingExternal =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            missingExternalSelection.toString());
    assertThat(missingExternal.exitCode()).isNotZero();
    assertThat(missingExternal.stdout()).isEmpty();
    assertThat(missingExternal.stderr()).contains("ONTOLOGY_SELECTED_RUN_INVALID");
    assertThat(providerFactories).hasValue(providerFactoriesBeforeMissingExternal);
    assertThat(requests).hasSize(requestsBeforeMissingExternal);

    Path completeExternalSelection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("business-link-enrichment-publish-complete-union.json"),
            o0RunId,
            List.of(skeletonO1RunId, enrichmentO1RunId, independentO1RunId),
            List.of(o2RunId));
    CliResult published =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            completeExternalSelection.toString());
    assertThat(published.exitCode()).withFailMessage(cliDiagnostics(published)).isZero();
    String o3RunId = runId(published);
    assertThat(requests).hasSize(requestsBeforeMissingExternal);
    ReopenedModulePublication o3 =
        reopenedOntologyModule(fixture.runStore(), o3RunId, ONTOLOGY_POLICY_SET_V3);
    assertThat(o3.receipt().upstreamArtifacts())
        .containsAll(modulePayloadReferences(skeletonO1))
        .containsAll(modulePayloadReferences(enrichmentO1))
        .containsAll(modulePayloadReferences(independentO1))
        .containsAll(modulePayloadReferences(o2));
  }

  @Test
  void scopeV3EnrichmentUsesOnlyObjectsFromTheExplicitlySelectedLinkTask() throws Exception {
    TechnicalFixture fixture = prepareRealR4("business-link-scope-v3-exact-task-source");
    ConfiguredTypedPipeline configured =
        writeBusinessLinkTypedPipelineConfigurationV4(
            "ontology-business-link-scope-v3-exact-task-source.yaml", fixture);
    OntologyConfiguration loadedConfiguration =
        OntologyConfiguration.load(configured.path()).forTypedV4().forJointLinks();
    assertThat(loadedConfiguration.prompts()).containsKeys("link", "linkReview");
    AtomicInteger providerFactories = new AtomicInteger();
    AtomicInteger linkCandidates = new AtomicInteger();
    List<StructuredModelRequest> requests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String kind = input.path("taskKind").asText();
                boolean review = request.taskKind().contains("REVIEW");
                ImmutableBytes response;
                if ("LINK".equals(kind)) {
                  if (review) {
                    ObjectNode reviewed = (ObjectNode) input.path("actualDraft").deepCopy();
                    reviewed.put("schemaVersion", "ontology-link-review-v1");
                    response = CANONICAL.encodeCanonical(reviewed);
                  } else {
                    String source =
                        linkCandidates.incrementAndGet() == 1 ? "unselected" : "selected";
                    response =
                        businessLinkRuntimeJointResponse(
                            "ontology-link-candidate-v1", input, source);
                  }
                } else if ("ACTION".equals(kind)) {
                  JsonNode entries = input.path("reviewedCatalog").path("entries");
                  assertThat(entries).hasSize(2);
                  assertThat(entries)
                      .allSatisfy(
                          entry ->
                              assertThat(entry.path("definitionType").asText())
                                  .isEqualTo("objects"));
                  assertThat(entries)
                      .extracting(entry -> entry.path("definition").path("name").asText())
                      .containsExactlyInAnyOrder("selected-source", "selected-target");
                  response =
                      businessLinkFormalActionResponseV4(
                          review ? "ontology-typed-review-v4" : "ontology-typed-candidate-v4",
                          input.path("questionId").asText(),
                          "E1",
                          entries.get(0).path("catalogRef").asText());
                } else if ("RELATE".equals(kind)) {
                  JsonNode entries = input.path("reviewedCatalog").path("entries");
                  assertThat(entries).hasSize(2);
                  assertThat(entries)
                      .extracting(entry -> entry.path("definitionType").asText())
                      .containsOnly("objects");
                  assertThat(entries)
                      .extracting(entry -> entry.path("definition").path("name").asText())
                      .containsExactlyInAnyOrder("selected-source", "selected-target");
                  List<String> objectRefs =
                      arrayValues(entries).stream()
                          .map(entry -> entry.path("catalogRef").asText())
                          .toList();
                  response =
                      businessLinkFormalUnresolvedRelateResponseV4(
                          review ? "ontology-typed-review-v4" : "ontology-typed-candidate-v4",
                          objectRefs);
                } else {
                  throw new AssertionError("unexpected scope-v3 source-selection task " + kind);
                }
                return new StructuredModelResponse(response, identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    String corpusRunId = runId(prepared);
    assertThat(requests).isEmpty();

    Path sourceScope =
        writeBusinessLinkTwoLinkScopeV3(
            temporaryDirectory.resolve("business-link-two-link-source-scope-v3.json"));
    CliResult sourceIdentified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            corpusRunId,
            "--scope",
            sourceScope.toString());
    assertThat(sourceIdentified.exitCode())
        .withFailMessage(cliDiagnostics(sourceIdentified))
        .isZero();
    String sourceRunId = runId(sourceIdentified);
    assertThat(requests)
        .extracting(StructuredModelRequest::taskKind)
        .containsExactly(
            "ONTOLOGY_FORMAL_LINK_EXTRACT",
            "ONTOLOGY_FORMAL_LINK_REVIEW",
            "ONTOLOGY_FORMAL_LINK_EXTRACT",
            "ONTOLOGY_FORMAL_LINK_REVIEW");

    Path publishSelection =
        writePublishSelectionV3(
            temporaryDirectory.resolve("business-link-direct-publish-selection-v3.json"),
            corpusRunId,
            List.of(sourceRunId));
    JsonNode savedSelection = JSON.readTree(Files.readAllBytes(publishSelection));
    assertThat(savedSelection.path("schemaVersion").asText()).isEqualTo("ontology-selection-v3");
    assertThat(savedSelection.path("relationRuns")).isEmpty();
    int requestsBeforePublish = requests.size();
    CliResult published =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            publishSelection.toString());
    assertThat(published.exitCode()).withFailMessage(cliDiagnostics(published)).isZero();
    String publishedRunId = runId(published);
    assertThat(requests).hasSize(requestsBeforePublish);
    JsonNode ontology =
        JSON.readTree(artifact(configured.path(), publishedRunId, "ONTOLOGY").stdout());
    assertThat(ontology.path("objectTypes")).hasSize(4);
    assertThat(ontology.path("linkTypes")).hasSize(2);
    assertThat(ontology.path("operations")).isEmpty();
    JsonNode coverage =
        JSON.readTree(artifact(configured.path(), publishedRunId, "ONTOLOGY_COVERAGE").stdout());
    JsonNode review =
        JSON.readTree(artifact(configured.path(), publishedRunId, "ONTOLOGY_REVIEW").stdout());
    assertThat(coverage.path("schemaVersion").asText()).isEqualTo("ontology-coverage-v4");
    assertThat(review.path("schemaVersion").asText()).isEqualTo("ontology-review-v4");
    assertThat(review.path("taskReviewSchemaVersions"))
        .containsExactly(JSON.getNodeFactory().textNode("ontology-link-review-v1"));
    assertThat(review.has("typedReviewSchemaVersion")).isFalse();
    ReopenedModulePublication publishedModule =
        reopenedOntologyModule(fixture.runStore(), publishedRunId, ONTOLOGY_POLICY_SET_V4);
    assertThat(publishedModule.payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .containsExactlyInAnyOrder(
            "ontology-v2", "ontology-source-v1", "ontology-coverage-v4", "ontology-review-v4");
    assertThat(
            reopenedOntologyModule(fixture.runStore(), sourceRunId, ONTOLOGY_POLICY_SET_V4)
                .payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .containsExactly("ontology-identification-v4");
    CliResult overview =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            publishedRunId,
            "--key",
            "ONTOLOGY_BUSINESS_OVERVIEW",
            "--max-bytes",
            "5242880");
    assertThat(overview.exitCode()).withFailMessage(cliDiagnostics(overview)).isZero();
    assertThat(overview.stdout()).startsWith("<!doctype html>").contains("ontology-business-graph");
    assertThat(requests).hasSize(requestsBeforePublish);

    Path enrichmentScope =
        writeBusinessLinkEnrichmentScopeV3ForTask(
            temporaryDirectory.resolve("business-link-selected-link-enrichment-v3.json"),
            sourceRunId,
            "Q_LINK_SOURCE",
            "link-task-b");
    int requestsBeforeEnrichment = requests.size();
    CliResult enriched =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            corpusRunId,
            "--scope",
            enrichmentScope.toString());
    assertThat(enriched.exitCode()).withFailMessage(cliDiagnostics(enriched)).isZero();
    String enrichedRunId = runId(enriched);
    assertThat(requests.subList(requestsBeforeEnrichment, requests.size()))
        .extracting(
            request -> {
              JsonNode input = ExplicitTypedPipelineScript.input(request);
              return input.path("taskKind").asText()
                  + "/"
                  + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT");
            })
        .containsExactly("ACTION/EXTRACT", "ACTION/REVIEW");
    for (StructuredModelRequest request :
        requests.subList(requestsBeforeEnrichment, requests.size())) {
      JsonNode entries =
          ExplicitTypedPipelineScript.input(request).path("reviewedCatalog").path("entries");
      assertThat(entries)
          .extracting(entry -> entry.path("definition").path("name").asText())
          .containsExactlyInAnyOrder("selected-source", "selected-target");
      assertThat(entries)
          .extracting(entry -> entry.path("definitionType").asText())
          .containsOnly("objects");
    }

    int requestsBeforeRejectedSources = requests.size();
    Path missingTaskScope =
        writeBusinessLinkEnrichmentScopeV3ForTask(
            temporaryDirectory.resolve("business-link-missing-task-source-v3.json"),
            sourceRunId,
            "Q_LINK_SOURCE",
            "missing-link-task");
    CliResult missingTask =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            corpusRunId,
            "--scope",
            missingTaskScope.toString());
    assertThat(missingTask.exitCode()).withFailMessage(cliDiagnostics(missingTask)).isNotZero();
    assertThat(requests).hasSize(requestsBeforeRejectedSources);

    Path actionTaskScope =
        writeBusinessLinkEnrichmentScopeV3ForTask(
            temporaryDirectory.resolve("business-link-action-task-source-v3.json"),
            enrichedRunId,
            "Q_LINK_CONSUMER",
            "action-after-selected-link");
    CliResult actionTask =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            corpusRunId,
            "--scope",
            actionTaskScope.toString());
    assertThat(actionTask.exitCode()).withFailMessage(cliDiagnostics(actionTask)).isNotZero();
    assertThat(requests).hasSize(requestsBeforeRejectedSources);

    CliResult sourceIdentificationResult =
        artifact(configured.path(), sourceRunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(sourceIdentificationResult);
    JsonNode sourceIdentification = JSON.readTree(sourceIdentificationResult.stdout());
    JsonNode selectedLinkTask = findTaskRecord(sourceIdentification, "link-task-b");
    assertThat(selectedLinkTask.path("status").asText()).isEqualTo("REVIEWED");
    String selectedLinkProducingTaskId = selectedLinkTask.path("producingTaskId").asText();
    assertThat(selectedLinkProducingTaskId).isNotBlank();
    JsonNode savedSelectedLink =
        queryTaskObservation(configured.path(), sourceRunId, selectedLinkProducingTaskId);
    assertThat(
            savedSelectedLink.path("completion").path("identity").path("producingTaskId").asText())
        .isEqualTo(selectedLinkProducingTaskId);
    assertThat(savedSelectedLink.path("completion").path("review").path("schemaVersion").asText())
        .isEqualTo("ontology-link-review-v1");

    Path relationSelection =
        writeBusinessLinkRelateSelectionV3ForTask(
            temporaryDirectory.resolve("business-link-selected-link-relate-v3.json"),
            corpusRunId,
            sourceRunId,
            "Q_LINK_SOURCE",
            "link-task-b");
    JsonNode savedRelationSelection = JSON.readTree(Files.readAllBytes(relationSelection));
    assertThat(savedRelationSelection.path("schemaVersion").asText())
        .isEqualTo("ontology-selection-v3");
    assertThat(
            savedRelationSelection
                .path("questions")
                .get(0)
                .path("objectSources")
                .get(0)
                .path("taskIds"))
        .containsExactly(JSON.getNodeFactory().textNode("link-task-b"));
    int requestsBeforeRelate = requests.size();
    CliResult related =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            relationSelection.toString());
    assertThat(related.exitCode()).withFailMessage(cliDiagnostics(related)).isZero();
    String relationRunId = runId(related);
    assertThat(requests.subList(requestsBeforeRelate, requests.size()))
        .extracting(
            request -> {
              JsonNode input = ExplicitTypedPipelineScript.input(request);
              return input.path("taskKind").asText()
                  + "/"
                  + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT");
            })
        .containsExactly("RELATE/EXTRACT", "RELATE/REVIEW");
    int factoriesAfterRelate = providerFactories.get();

    CliResult relationResult = artifact(configured.path(), relationRunId, "ONTOLOGY_RELATIONS");
    assertArtifactAvailable(relationResult);
    JsonNode relations = JSON.readTree(relationResult.stdout());
    assertThat(relations.path("selection").path("schemaVersion").asText())
        .isEqualTo("ontology-selection-v3");
    JsonNode savedObjectSource =
        relations.path("selection").path("questions").get(0).path("objectSources").get(0);
    assertThat(savedObjectSource.path("identificationRun").asText()).isEqualTo(sourceRunId);
    assertThat(savedObjectSource.path("questionId").asText()).isEqualTo("Q_LINK_SOURCE");
    assertThat(savedObjectSource.path("taskIds"))
        .containsExactly(JSON.getNodeFactory().textNode("link-task-b"));
    JsonNode relationTask = findTaskRecord(relations, "relate-selected-link");
    assertThat(relationTask.path("status").asText()).isEqualTo("REVIEWED");
    String relationProducingTaskId = relationTask.path("producingTaskId").asText();
    assertThat(relationProducingTaskId).isNotBlank();
    JsonNode savedRelation =
        queryTaskObservation(configured.path(), relationRunId, relationProducingTaskId);
    assertThat(savedRelation.path("completion").path("identity").path("producingTaskId").asText())
        .isEqualTo(relationProducingTaskId);
    assertThat(savedRelation.path("completion").path("status").asText()).isEqualTo("REVIEWED");
    ReopenedModulePublication relationModule =
        reopenedOntologyModule(fixture.runStore(), relationRunId, ONTOLOGY_POLICY_SET_V4);
    assertThat(relationModule.payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .contains("ontology-relations-v4");

    Path relationPublishSelection =
        writePublishSelectionV3WithRelations(
            temporaryDirectory.resolve("business-link-selected-link-relate-publish-v3.json"),
            corpusRunId,
            List.of(sourceRunId),
            List.of(relationRunId));
    JsonNode savedRelationPublishSelection =
        JSON.readTree(Files.readAllBytes(relationPublishSelection));
    assertThat(savedRelationPublishSelection.path("relationRuns"))
        .containsExactly(JSON.getNodeFactory().textNode(relationRunId));
    int requestsBeforeRelationPublish = requests.size();
    CliResult relationPublished =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            relationPublishSelection.toString());
    assertThat(relationPublished.exitCode())
        .withFailMessage(cliDiagnostics(relationPublished))
        .isZero();
    String relationPublishedRunId = runId(relationPublished);
    assertThat(requests).hasSize(requestsBeforeRelationPublish);
    assertThat(providerFactories).hasValue(factoriesAfterRelate);
    ReopenedModulePublication relationPublishedModule =
        reopenedOntologyModule(fixture.runStore(), relationPublishedRunId, ONTOLOGY_POLICY_SET_V4);
    assertThat(relationPublishedModule.receipt().upstreamArtifacts())
        .containsAll(modulePayloadReferences(relationModule));
    CliResult relationPublishedReviewResult =
        artifact(configured.path(), relationPublishedRunId, "ONTOLOGY_REVIEW");
    assertArtifactAvailable(relationPublishedReviewResult);
    JsonNode relationPublishedReview = JSON.readTree(relationPublishedReviewResult.stdout());
    assertThat(relationPublishedReview.path("selection").path("relationRuns"))
        .containsExactly(JSON.getNodeFactory().textNode(relationRunId));
    assertThat(relationPublishedReview.path("taskReviewSchemaVersions"))
        .contains(
            JSON.getNodeFactory().textNode("ontology-link-review-v1"),
            JSON.getNodeFactory().textNode("ontology-typed-review-v4"));
  }

  @Test
  void
      enrichmentSourceQuestionWithoutObjectsIsSavedAsDependencyFailureButUnknownQuestionIsRejected()
          throws Exception {
    BusinessLinkRuntimeFixture runtime = prepareBusinessLinkRuntime("enrichment-source-no-object");
    Path skeletonScope =
        writeBusinessLinkSkeletonScopeV2(
            temporaryDirectory.resolve("enrichment-source-no-object-skeleton.json"));
    CliResult skeleton = identifyBusinessLink(runtime, skeletonScope);
    assertThat(skeleton.exitCode()).withFailMessage(cliDiagnostics(skeleton)).isZero();
    String skeletonRunId = runId(skeleton);

    Path actionAndAnalyticScope =
        writeBusinessLinkEnrichmentScopeV2(
            temporaryDirectory.resolve("enrichment-source-no-object-action-analytic.json"),
            skeletonRunId);
    CliResult actionAndAnalytic = identifyBusinessLink(runtime, actionAndAnalyticScope);
    assertThat(actionAndAnalytic.exitCode())
        .withFailMessage(cliDiagnostics(actionAndAnalytic))
        .isZero();
    String sourceRunId = runId(actionAndAnalytic);
    JsonNode sourceIdentification =
        JSON.readTree(
            artifact(runtime.configured().path(), sourceRunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(arrayValues(sourceIdentification.path("taskOutcomes")))
        .extracting(
            outcome ->
                outcome.path("questionId").asText() + "/" + outcome.path("taskKind").asText())
        .containsExactly("Q_ENRICHMENT/ACTION", "Q_ENRICHMENT/ANALYTIC");

    Path dependentScope =
        writeBusinessLinkQuestionScopeV2ForTasks(
            temporaryDirectory.resolve("enrichment-source-no-object-dependent.json"),
            "ENRICHMENT",
            "Q_DEPENDENT_ENRICHMENT",
            List.of(new ObjectSource(sourceRunId, "Q_ENRICHMENT")),
            List.of(new ScopedTask("dependent-action", "ACTION")));
    int callsBeforeDependency = runtime.requests().size();
    int factoriesBeforeDependency = runtime.providerFactories().get();
    CliResult dependent = identifyBusinessLink(runtime, dependentScope);
    assertThat(dependent.exitCode()).withFailMessage(cliDiagnostics(dependent)).isIn(0, 2);
    assertThat(dependent.stdout()).isNotBlank();
    String dependentRunId = runId(dependent);
    JsonNode dependentIdentification =
        JSON.readTree(
            artifact(runtime.configured().path(), dependentRunId, "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode unprocessed = findTaskOutcome(dependentIdentification, "dependent-action");
    assertThat(unprocessed.path("questionId").asText()).isEqualTo("Q_DEPENDENT_ENRICHMENT");
    assertThat(unprocessed.path("status").asText()).isEqualTo("UNPROCESSED");
    assertThat(unprocessed.path("reason").path("code").asText())
        .isEqualTo("DEPENDENCY_NOT_REVIEWED");
    assertThat(unprocessed.path("reason").path("category").asText()).isEqualTo("DEPENDENCY");
    assertThat(unprocessed.path("producingTaskId").isNull()).isTrue();
    assertThat(unprocessed.path("jobKey").isNull()).isTrue();
    assertThat(runtime.requests()).hasSize(callsBeforeDependency);
    assertThat(runtime.providerFactories()).hasValue(factoriesBeforeDependency);

    Path unknownQuestionScope =
        writeBusinessLinkQuestionScopeV2ForTasks(
            temporaryDirectory.resolve("enrichment-source-no-object-unknown-question.json"),
            "ENRICHMENT",
            "Q_UNKNOWN_DEPENDENCY_SOURCE",
            List.of(new ObjectSource(sourceRunId, "Q_NOT_IN_SAVED_SCOPE")),
            List.of(new ScopedTask("unknown-source-action", "ACTION")));
    CliResult unknownQuestion = identifyBusinessLink(runtime, unknownQuestionScope);
    assertThat(unknownQuestion.exitCode()).isNotZero();
    assertThat(unknownQuestion.stdout()).isEmpty();
    assertThat(unknownQuestion.stderr()).contains("ONTOLOGY_OBJECT_SOURCE_INVALID");
    assertThat(runtime.requests()).hasSize(callsBeforeDependency);
    assertThat(runtime.providerFactories()).hasValue(factoriesBeforeDependency);
  }

  @Test
  void enrichmentWithLocalObjectIsBlockedWhenItsExternalObjectReviewWasRejected() throws Exception {
    BusinessLinkRuntimeFixture runtime =
        prepareBusinessLinkRuntime("enrichment-rejected-object-source");
    runtime.rejectedObjectReviewQuestion().set("Q_REJECTED_SOURCE");
    Path rejectedSourceScope =
        writeBusinessLinkQuestionScopeV2ForTasks(
            temporaryDirectory.resolve("enrichment-rejected-object-source-scope.json"),
            "SKELETON",
            "Q_REJECTED_SOURCE",
            List.of(),
            List.of(new ScopedTask("rejected-source-object", "OBJECT")));
    CliResult rejectedSource = identifyBusinessLink(runtime, rejectedSourceScope);
    assertThat(rejectedSource.exitCode())
        .withFailMessage(cliDiagnostics(rejectedSource))
        .isIn(0, 2);
    String rejectedSourceRunId = runId(rejectedSource);
    JsonNode rejectedIdentification =
        JSON.readTree(
            artifact(runtime.configured().path(), rejectedSourceRunId, "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode rejectedObject = findTaskOutcome(rejectedIdentification, "rejected-source-object");
    assertThat(rejectedObject.path("status").asText()).isEqualTo("REJECTED");
    assertThat(rejectedObject.path("reason").path("category").asText()).isEqualTo("MODEL_OUTPUT");

    Path localObjectScope =
        writeBusinessLinkQuestionScopeV2ForTasks(
            temporaryDirectory.resolve("enrichment-rejected-object-source-local-object.json"),
            "ENRICHMENT",
            "Q_LOCAL_OBJECT_BLOCKED",
            List.of(new ObjectSource(rejectedSourceRunId, "Q_REJECTED_SOURCE")),
            List.of(new ScopedTask("local-object-blocked", "OBJECT")));
    int callsBeforeBlockedObject = runtime.requests().size();
    int factoriesBeforeBlockedObject = runtime.providerFactories().get();
    CliResult blocked = identifyBusinessLink(runtime, localObjectScope);
    assertThat(blocked.exitCode()).withFailMessage(cliDiagnostics(blocked)).isIn(0, 2);
    assertThat(blocked.stdout()).isNotBlank();
    String blockedRunId = runId(blocked);
    JsonNode blockedIdentification =
        JSON.readTree(
            artifact(runtime.configured().path(), blockedRunId, "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode blockedObject = findTaskOutcome(blockedIdentification, "local-object-blocked");
    assertThat(blockedObject.path("questionId").asText()).isEqualTo("Q_LOCAL_OBJECT_BLOCKED");
    assertThat(blockedObject.path("status").asText()).isEqualTo("UNPROCESSED");
    assertThat(blockedObject.path("reason").path("code").asText())
        .isEqualTo("DEPENDENCY_NOT_REVIEWED");
    assertThat(blockedObject.path("reason").path("category").asText()).isEqualTo("DEPENDENCY");
    assertThat(arrayValues(blockedObject.path("dependencyTaskRefs")))
        .extracting(
            dependency ->
                dependency.path("runId").asText()
                    + "/"
                    + dependency.path("questionId").asText()
                    + "/"
                    + dependency.path("taskId").asText())
        .containsExactly(rejectedSourceRunId + "/Q_REJECTED_SOURCE/rejected-source-object");
    assertThat(runtime.requests()).hasSize(callsBeforeBlockedObject);
    assertThat(runtime.providerFactories()).hasValue(factoriesBeforeBlockedObject);
  }

  @Test
  void enrichmentObjectRefinementReceivesExactExternalCatalogAndPersistsItsDependencyIdentity()
      throws Exception {
    BusinessLinkRuntimeFixture runtime = prepareBusinessLinkRuntime("enrichment-object-refinement");
    Path skeletonScope =
        writeBusinessLinkSkeletonScopeV2(
            temporaryDirectory.resolve("enrichment-object-refinement-skeleton.json"));
    CliResult skeleton = identifyBusinessLink(runtime, skeletonScope);
    assertThat(skeleton.exitCode()).withFailMessage(cliDiagnostics(skeleton)).isZero();
    String skeletonRunId = runId(skeleton);
    ReopenedModulePublication sourcePublication =
        reopenedOntologyModule(
            runtime.technicalFixture().runStore(), skeletonRunId, ONTOLOGY_POLICY_SET_V3);
    JsonNode sourceIdentification =
        JSON.readTree(
            artifact(runtime.configured().path(), skeletonRunId, "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode sourceObjectOutcome = findTaskOutcome(sourceIdentification, "object-skeleton");
    assertThat(sourceObjectOutcome.path("status").asText()).isEqualTo("REVIEWED");

    Path localObjectScope =
        writeBusinessLinkQuestionScopeV2ForTasks(
            temporaryDirectory.resolve("enrichment-object-refinement-local-object.json"),
            "ENRICHMENT",
            "Q_LOCAL_OBJECT_REFINEMENT",
            List.of(new ObjectSource(skeletonRunId, "Q_SKELETON")),
            List.of(new ScopedTask("local-refinement-object", "OBJECT")));
    CliResult refined = identifyBusinessLink(runtime, localObjectScope);
    assertThat(refined.exitCode()).withFailMessage(cliDiagnostics(refined)).isIn(0, 2);
    String refinedRunId = runId(refined);
    JsonNode refinedIdentification =
        JSON.readTree(
            artifact(runtime.configured().path(), refinedRunId, "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode localObjectOutcome = findTaskOutcome(refinedIdentification, "local-refinement-object");
    assertThat(localObjectOutcome.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(arrayValues(localObjectOutcome.path("dependencyTaskRefs")))
        .extracting(
            dependency ->
                dependency.path("runId").asText()
                    + "/"
                    + dependency.path("questionId").asText()
                    + "/"
                    + dependency.path("taskId").asText())
        .containsExactly(skeletonRunId + "/Q_SKELETON/object-skeleton");

    List<StructuredModelRequest> refinementRequests =
        runtime.requests().stream()
            .filter(
                request ->
                    "Q_LOCAL_OBJECT_REFINEMENT"
                        .equals(
                            ExplicitTypedPipelineScript.input(request).path("questionId").asText()))
            .toList();
    assertThat(refinementRequests).hasSize(2);
    JsonNode extractInput = ExplicitTypedPipelineScript.input(refinementRequests.get(0));
    JsonNode reviewInput = ExplicitTypedPipelineScript.input(refinementRequests.get(1));
    JsonNode externalCatalogEntries = extractInput.path("reviewedCatalog").path("entries");
    assertThat(externalCatalogEntries).hasSize(1);
    assertThat(reviewInput.path("reviewedCatalog")).isEqualTo(extractInput.path("reviewedCatalog"));
    JsonNode externalCatalogObject = externalCatalogEntries.get(0);
    assertThat(externalCatalogObject.path("definitionType").asText()).isEqualTo("objects");
    assertThat(externalCatalogObject.path("definition").path("localId").asText()).isEqualTo("O1");
    assertThat(externalCatalogObject.path("definition").path("scope").path("questionRef").asText())
        .isEqualTo("Q_SKELETON");

    JsonNode sourceTaskObservation =
        queryTaskObservation(
            runtime.configured().path(),
            skeletonRunId,
            sourceObjectOutcome.path("producingTaskId").asText());
    JsonNode sourceIdentity = sourceTaskObservation.path("completion").path("identity");
    JsonNode localTaskObservation =
        queryTaskObservation(
            runtime.configured().path(),
            refinedRunId,
            localObjectOutcome.path("producingTaskId").asText());
    JsonNode localCompletion = localTaskObservation.path("completion");
    assertThat(localCompletion.path("taskDependencyRuleVersion").asText())
        .isEqualTo("ontology-task-dependency-v2");
    assertThat(localCompletion.path("taskDependencyFingerprint").asText()).isNotBlank();
    JsonNode savedExternalCatalog = localCompletion.path("catalogMapping").path("entries");
    assertThat(savedExternalCatalog).hasSize(1);
    assertThat(savedExternalCatalog.get(0).path("identity").path("corpusIdentity").asText())
        .isEqualTo(sourceIdentity.path("corpusIdentity").asText());
    assertThat(savedExternalCatalog.get(0).path("identity").path("producingTaskId").asText())
        .isEqualTo(sourceIdentity.path("producingTaskId").asText());
    assertThat(savedExternalCatalog.get(0).path("identity").path("reviewVersion").asText())
        .isEqualTo(sourceIdentity.path("reviewVersion").asText());
    assertThat(savedExternalCatalog.get(0).path("identity").path("localId").asText())
        .isEqualTo("O1");
    JsonNode localReview = localCompletion.path("review");
    assertThat(localReview.path("definitions").path("objects")).hasSize(1);
    assertThat(localReview.path("definitions").path("objects").get(0).path("localId").asText())
        .isEqualTo("O1");
    assertThat(localCompletion.path("identity").path("producingTaskId").asText())
        .isNotEqualTo(sourceIdentity.path("producingTaskId").asText());

    try (RunStoreHandle store = RunStoreBootstrap.open(runtime.technicalFixture().runStore())) {
      var persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
              store, AnalysisRunId.parse(refinedRunId));
      assertThat(persisted.request().ontologyInputs().identificationPublications())
          .containsExactly(sourcePublication.reference());
      JsonNode persistedRequest = CANONICAL.parseCanonical(persisted.canonicalJson());
      assertThat(refinedIdentification.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(persistedRequest.path("ontologyInputs").path("identificationPublications"));
    }
  }

  @Test
  void invalidMiddleObjectReviewIsRejectedWithoutLosingThirdTaskOrItsCoverage() throws Exception {
    TechnicalFixture fixture = prepareRealR4("three-object-task-isolation");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-three-object-task-isolation.yaml", fixture);
    List<StructuredModelRequest> requests = new ArrayList<>();
    Map<String, ImmutableBytes> responsesByRequestId = new java.util.LinkedHashMap<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                int callIndex = requests.size();
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String questionId = input.path("questionId").asText();
                boolean review = request.taskKind().contains("REVIEW");
                ImmutableBytes response =
                    review && callIndex == 3
                        ? formalTaskObjectResponseWithDanglingReference(
                            "ontology-typed-review-v3", questionId, "E1", "O3")
                        : formalTaskObjectResponse(
                            review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3",
                            questionId,
                            "E1");
                responsesByRequestId.put(request.taskId(), response);
                return new StructuredModelResponse(response, identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);

    Path scope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("three-object-task-scope.json"),
            new ScopedQuestion(
                "Q1",
                List.of(
                    new ScopedTask("object-first", "OBJECT"),
                    new ScopedTask("object-middle", "OBJECT"),
                    new ScopedTask("object-third", "OBJECT"))));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    String o1RunId = runId(identified);

    assertThat(requests).hasSize(6);
    assertThat(
            requests.stream()
                .map(
                    request -> {
                      JsonNode input = ExplicitTypedPipelineScript.input(request);
                      return input.path("questionId").asText()
                          + "/"
                          + input.path("taskKind").asText()
                          + "/"
                          + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT");
                    })
                .toList())
        .containsExactly(
            "Q1/OBJECT/EXTRACT",
            "Q1/OBJECT/REVIEW",
            "Q1/OBJECT/EXTRACT",
            "Q1/OBJECT/REVIEW",
            "Q1/OBJECT/EXTRACT",
            "Q1/OBJECT/REVIEW");

    CliResult inspection = executePublic(configured.path(), "inspect", "--run", o1RunId);
    assertThat(inspection.exitCode()).withFailMessage(inspection.stderr()).isZero();
    assertThat(JSON.readTree(inspection.stdout()).path("resultStatus").asText())
        .isEqualTo("PARTIAL");
    CliResult identificationResult =
        artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identificationResult);
    JsonNode identification = JSON.readTree(identificationResult.stdout());
    JsonNode outcomes = identification.path("taskOutcomes");
    assertThat(outcomes.isArray()).isTrue();
    assertThat(arrayValues(outcomes).stream().map(item -> item.path("taskId").asText()).toList())
        .containsExactly("object-first", "object-middle", "object-third");
    assertThat(arrayValues(outcomes).stream().map(item -> item.path("status").asText()).toList())
        .containsExactly("REVIEWED", "REJECTED", "REVIEWED");

    List<JsonNode> memberships = arrayValues(identification.path("taskRecords"));
    assertThat(memberships).hasSize(3);
    assertThat(memberships.stream().map(item -> item.path("taskId").asText()).toList())
        .containsExactly("object-first", "object-middle", "object-third");
    assertThat(memberships.stream().map(item -> item.path("status").asText()).toList())
        .containsExactly("REVIEWED", "REJECTED", "REVIEWED");

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      var inspectionResult = new LocalRepositoryAnalysisAgent(store).inspect(o1RunId);
      assertThat(inspectionResult.output().ontologyOutput().status())
          .isEqualTo(OntologyRunOutput.Status.PARTIAL);
    }

    for (String taskId : List.of("object-first", "object-middle", "object-third")) {
      JsonNode outcome =
          arrayValues(outcomes).stream()
              .filter(item -> taskId.equals(item.path("taskId").asText()))
              .findFirst()
              .orElseThrow();
      JsonNode membership =
          memberships.stream()
              .filter(item -> taskId.equals(item.path("taskId").asText()))
              .findFirst()
              .orElseThrow();
      String producingTaskId = outcome.path("producingTaskId").asText();
      String jobKey = outcome.path("jobKey").asText();
      assertThat(producingTaskId).isNotBlank();
      assertThat(jobKey).isNotBlank();
      assertThat(membership.path("producingTaskId").asText()).isEqualTo(producingTaskId);
      assertThat(membership.path("jobKey").asText()).isEqualTo(jobKey);

      CliResult observationResult =
          executeWithFactory(
              configured.path(),
              providerFactory,
              "artifact",
              "--run",
              o1RunId,
              "--key",
              "ONTOLOGY_TASK_RECORD",
              "--task-id",
              producingTaskId,
              "--max-bytes",
              "524288");
      assertArtifactAvailable(observationResult);
      JsonNode observation = JSON.readTree(observationResult.stdout());
      assertThat(observation.path("taskId").asText()).isEqualTo(taskId);
      assertThat(observation.path("producingTaskId").asText()).isEqualTo(producingTaskId);
      assertThat(observation.path("jobKey").asText()).isEqualTo(jobKey);
      for (String stage : List.of("extract", "review")) {
        JsonNode savedStage = observation.path(stage);
        assertThat(savedStage.path("request").isObject()).isTrue();
        assertThat(savedStage.path("response").isObject()).isTrue();
        assertThat(savedStage.path("validation").isObject()).isTrue();
        String requestId = savedStage.path("request").path("taskId").asText();
        ImmutableBytes expectedResponse = responsesByRequestId.get(requestId);
        assertThat(expectedResponse).isNotNull();
        String expectedResponseBase64 =
            Base64.getEncoder().encodeToString(expectedResponse.copyToByteArray());
        assertThat(savedStage.path("response").path("rawResponseBase64").asText())
            .isEqualTo(expectedResponseBase64);
        assertThat(savedStage.path("validation").path("rawResponseBase64").asText())
            .isEqualTo(expectedResponseBase64);
      }
    }
  }

  @Test
  void failedObjectBlocksOnlyItsSameQuestionActionWhileIndependentTasksContinue() throws Exception {
    TechnicalFixture fixture = prepareRealR4("question-local-task-isolation");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-question-local-task-isolation.yaml", fixture);
    List<StructuredModelRequest> requests = new ArrayList<>();
    Map<String, ImmutableBytes> responsesByRequestId = new java.util.LinkedHashMap<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String questionId = input.path("questionId").asText();
                String kind = input.path("taskKind").asText();
                boolean review = request.taskKind().contains("REVIEW");
                String schemaVersion =
                    review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3";
                String entryRef = "E1";
                ImmutableBytes response;
                if ("OBJECT".equals(kind)) {
                  response =
                      review && "Q1".equals(questionId)
                          ? formalTaskObjectResponseWithDanglingReference(
                              schemaVersion, questionId, entryRef, "O3")
                          : formalTaskObjectResponse(schemaVersion, questionId, entryRef);
                } else if ("ACTION".equals(kind)) {
                  String objectRef = ExplicitTypedPipelineScript.catalogRef(input, "objects");
                  response =
                      formalTaskActionResponse(schemaVersion, questionId, entryRef, objectRef);
                  if (review && "Q2".equals(questionId)) {
                    response =
                        formalTaskActionResponseWithDanglingObjectReference(
                            schemaVersion, questionId, entryRef, objectRef, "B999");
                  }
                } else if ("ANALYTIC".equals(kind)) {
                  JsonNode object = ExplicitTypedPipelineScript.catalogEntry(input, "objects");
                  response =
                      formalTaskAnalyticResponse(
                          schemaVersion,
                          questionId,
                          entryRef,
                          object.path("catalogRef").asText(),
                          object.path("propertyRefs").path(0).asText());
                } else {
                  throw new AssertionError("unexpected typed task kind " + kind);
                }
                responsesByRequestId.put(request.taskId(), response);
                return new StructuredModelResponse(response, identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    Path scope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("question-local-task-scope.json"),
            new ScopedQuestion(
                "Q1",
                List.of(
                    new ScopedTask("q1-object", "OBJECT"), new ScopedTask("q1-action", "ACTION"))),
            new ScopedQuestion(
                "Q2",
                List.of(
                    new ScopedTask("q2-object", "OBJECT"),
                    new ScopedTask("q2-action", "ACTION"),
                    new ScopedTask("q2-analytic", "ANALYTIC"))));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    String o1RunId = runId(identified);

    assertThat(requests).hasSize(8);
    assertThat(
            requests.stream()
                .map(
                    request -> {
                      JsonNode input = ExplicitTypedPipelineScript.input(request);
                      return input.path("questionId").asText()
                          + "/"
                          + input.path("taskKind").asText()
                          + "/"
                          + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT");
                    })
                .toList())
        .containsExactly(
            "Q1/OBJECT/EXTRACT",
            "Q1/OBJECT/REVIEW",
            "Q2/OBJECT/EXTRACT",
            "Q2/OBJECT/REVIEW",
            "Q2/ACTION/EXTRACT",
            "Q2/ACTION/REVIEW",
            "Q2/ANALYTIC/EXTRACT",
            "Q2/ANALYTIC/REVIEW");

    CliResult inspection = executePublic(configured.path(), "inspect", "--run", o1RunId);
    assertThat(inspection.exitCode()).withFailMessage(inspection.stderr()).isZero();
    JsonNode operationObservation = JSON.readTree(inspection.stdout());
    assertThat(operationObservation.path("resultStatus").asText()).isEqualTo("PARTIAL");
    assertThat(operationObservation.path("schemaVersion").asText())
        .isEqualTo("ontology-operation-observation-v2");
    assertThat(arrayValues(operationObservation.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly(
            "q1-object/REJECTED",
            "q1-action/UNPROCESSED",
            "q2-object/REVIEWED",
            "q2-action/REJECTED",
            "q2-analytic/REVIEWED");
    List<JsonNode> problems = arrayValues(operationObservation.path("problems"));
    assertThat(problems)
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("category").asText())
        .containsExactly(
            "q1-object/MODEL_OUTPUT", "q1-action/DEPENDENCY", "q2-action/MODEL_OUTPUT");
    assertThat(problems)
        .filteredOn(problem -> "MODEL_OUTPUT".equals(problem.path("category").asText()))
        .allSatisfy(
            problem -> {
              assertThat(problem.path("code").asText()).isNotBlank();
              assertThat(problem.path("stage").asText()).isEqualTo("REVIEW_VALIDATION");
            });
    List<JsonNode> nextActions = arrayValues(operationObservation.path("nextActions"));
    assertThat(nextActions)
        .extracting(item -> item.path("kind").asText())
        .contains("QUERY_TASK", "PUBLISH_REVIEWED_PART");
    JsonNode queryTask =
        nextActions.stream()
            .filter(action -> "QUERY_TASK".equals(action.path("kind").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(queryTask.path("requiresNewRun").asBoolean()).isFalse();
    assertThat(arrayValues(queryTask.path("taskRefs")))
        .extracting(
            reference ->
                reference.path("runId").asText()
                    + "/"
                    + reference.path("questionId").asText()
                    + "/"
                    + reference.path("taskId").asText())
        .containsExactly(
            o1RunId + "/Q1/q1-object",
            o1RunId + "/Q2/q2-object",
            o1RunId + "/Q2/q2-action",
            o1RunId + "/Q2/q2-analytic");
    JsonNode partialPublish =
        nextActions.stream()
            .filter(action -> "PUBLISH_REVIEWED_PART".equals(action.path("kind").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(partialPublish.path("requiresNewRun").asBoolean()).isTrue();
    assertThat(stringValues(partialPublish.path("upstreamRunRefs"))).containsExactly(o1RunId);
    assertThat(arrayValues(partialPublish.path("taskRefs")))
        .extracting(
            reference ->
                reference.path("runId").asText()
                    + "/"
                    + reference.path("questionId").asText()
                    + "/"
                    + reference.path("taskId").asText())
        .containsExactly(o1RunId + "/Q2/q2-object", o1RunId + "/Q2/q2-analytic");

    JsonNode identification =
        JSON.readTree(artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    JsonNode outcomes = identification.path("taskOutcomes");
    assertThat(arrayValues(outcomes).stream().map(item -> item.path("taskId").asText()).toList())
        .containsExactly("q1-object", "q1-action", "q2-object", "q2-action", "q2-analytic");
    assertThat(arrayValues(outcomes).stream().map(item -> item.path("status").asText()).toList())
        .containsExactly("REJECTED", "UNPROCESSED", "REVIEWED", "REJECTED", "REVIEWED");
    CliResult taskIndexResult =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_INDEX",
            "--max-bytes",
            "524288");
    assertArtifactAvailable(taskIndexResult);
    JsonNode taskIndex = JSON.readTree(taskIndexResult.stdout());
    assertThat(taskIndex.path("schemaVersion").asText()).isEqualTo("ontology-task-index-v2");
    assertThat(taskIndex.path("runId").asText()).isEqualTo(o1RunId);
    assertThat(arrayValues(taskIndex.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly(
            "q1-object/REJECTED",
            "q1-action/UNPROCESSED",
            "q2-object/REVIEWED",
            "q2-action/REJECTED",
            "q2-analytic/REVIEWED");
    JsonNode unpreparedAction =
        arrayValues(taskIndex.path("taskOutcomes")).stream()
            .filter(item -> "q1-action".equals(item.path("taskId").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(unpreparedAction.path("producingTaskId").isNull()).isTrue();
    assertThat(unpreparedAction.path("jobKey").isNull()).isTrue();
    assertThat(arrayValues(taskIndex.path("taskRecords")))
        .extracting(item -> item.path("taskId").asText())
        .doesNotContain("q1-action");
    JsonNode blockedAction =
        arrayValues(outcomes).stream()
            .filter(item -> "q1-action".equals(item.path("taskId").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(blockedAction.path("producingTaskId").isNull()).isTrue();
    assertThat(blockedAction.path("jobKey").isNull()).isTrue();
    assertThat(arrayValues(blockedAction.path("dependencyTaskRefs")))
        .singleElement()
        .satisfies(
            dependency -> {
              assertThat(dependency.path("questionId").asText()).isEqualTo("Q1");
              assertThat(dependency.path("taskId").asText()).isEqualTo("q1-object");
            });
    JsonNode analyticReviewRequest =
        requests.stream()
            .filter(
                request ->
                    "ANALYTIC"
                            .equals(
                                ExplicitTypedPipelineScript.input(request)
                                    .path("taskKind")
                                    .asText())
                        && request.taskKind().contains("REVIEW"))
            .map(request -> ExplicitTypedPipelineScript.input(request))
            .findFirst()
            .orElseThrow();
    assertThat(arrayValues(analyticReviewRequest.path("reviewedCatalog").path("entries")))
        .extracting(item -> item.path("definitionType").asText())
        .containsExactly("objects");
    assertThat(
            containsText(
                analyticReviewRequest.path("reviewedCatalog"), "Neutral source-backed record"))
        .isTrue();
  }

  @Test
  void allFailedQuestionStopsAfterMalformedObjectWithoutOfferingPartialPublication()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("all-failed-question-terminates");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-all-failed-question-v2.yaml", fixture);
    List<StructuredModelRequest> requests = new ArrayList<>();
    byte[] malformedJson =
        "{\"schemaVersion\":\"ontology-typed-candidate-v3\",".getBytes(StandardCharsets.UTF_8);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider> factory =
        declaration -> {
          ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
          return request -> {
            requests.add(request);
            assertThat(request.taskKind()).contains("OBJECT", "EXTRACT");
            return new StructuredModelResponse(ImmutableBytes.copyOf(malformedJson), identity);
          };
        };
    CliResult prepared =
        executeWithFactory(
            configured.path(), factory, "prepare-ontology", "--evidence-run", fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    Path scope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("all-failed-question-scope.json"),
            new ScopedQuestion(
                "Q1",
                List.of(
                    new ScopedTask("bad-object", "OBJECT"),
                    new ScopedTask("blocked-action", "ACTION"))));

    CliResult identified =
        executeWithFactory(
            configured.path(),
            factory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());

    assertThat(identified.exitCode()).isNotZero();
    assertThat(identified.stdout()).withFailMessage(identified.stderr()).isNotBlank();
    String o1RunId = runId(identified);
    assertThat(requests).hasSize(1);
    JsonNode observation =
        assertCommandAndInspectionAgree(
            identified, configured.path(), "IDENTIFY_ONTOLOGY", o1RunId, 1);
    assertThat(observation.path("lifecycleState").asText()).isEqualTo("FAILED");
    assertThat(observation.path("resultStatus").asText()).isIn("BLOCKED", "PARTIAL");
    assertThat(arrayValues(observation.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly("bad-object/REJECTED", "blocked-action/UNPROCESSED");
    JsonNode blockedAction =
        arrayValues(observation.path("taskOutcomes")).stream()
            .filter(item -> "blocked-action".equals(item.path("taskId").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(blockedAction.path("producingTaskId").isNull()).isTrue();
    assertThat(blockedAction.path("jobKey").isNull()).isTrue();
    assertThat(arrayValues(observation.path("nextActions")))
        .extracting(action -> action.path("kind").asText())
        .containsExactly("QUERY_TASK");
    JsonNode query =
        arrayValues(observation.path("nextActions")).stream()
            .filter(action -> "QUERY_TASK".equals(action.path("kind").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(arrayValues(query.path("taskRefs")))
        .extracting(
            reference ->
                reference.path("runId").asText()
                    + "/"
                    + reference.path("questionId").asText()
                    + "/"
                    + reference.path("taskId").asText())
        .containsExactly(o1RunId + "/Q1/bad-object");
    JsonNode identification =
        JSON.readTree(artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(arrayValues(identification.path("taskRecords")))
        .extracting(item -> item.path("taskId").asText())
        .containsExactly("bad-object");
    JsonNode taskIndex =
        JSON.readTree(artifact(configured.path(), o1RunId, "ONTOLOGY_TASK_INDEX").stdout());
    assertThat(arrayValues(taskIndex.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly("bad-object/REJECTED", "blocked-action/UNPROCESSED");
    assertThat(arrayValues(taskIndex.path("taskRecords")))
        .extracting(item -> item.path("taskId").asText())
        .containsExactly("bad-object");
  }

  @Test
  void v2PreparedR2LocationSuppliesSavedHelperBodyToBothTypedRequestsWithoutChangingV1Corpus()
      throws Exception {
    TechnicalFixture fixture =
        prepareRealR4(
            "v2-prepared-source-body-formal-request",
            SOURCE_SUPPLEMENT_JAVA_SOURCE,
            1,
            OntologyFormalRuntimeContractsTest::supplementalSourceSession);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        unusedProvider =
            declaration -> {
              throw new AssertionError("O0 preparation must not initialize a model Provider");
            };
    ConfiguredTypedPipeline legacyConfiguration =
        writeTypedPipelineConfiguration("source-supplement-legacy-v1.yaml", fixture);
    CliResult legacyPrepared =
        executeWithFactory(
            legacyConfiguration.path(),
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(legacyPrepared.exitCode())
        .withFailMessage(
            "legacy prepare-ontology failed. stdout:\n%s\nstderr:\n%s",
            legacyPrepared.stdout(), legacyPrepared.stderr())
        .isZero();
    String legacyO0RunId = runId(legacyPrepared);
    CliResult legacyCorpusResult =
        artifact(legacyConfiguration.path(), legacyO0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(legacyCorpusResult);
    JsonNode legacyCorpus = JSON.readTree(legacyCorpusResult.stdout());
    assertThat(legacyCorpus.path("projectionRuleVersion").asText())
        .isEqualTo("ontology-model-projection-v1");
    assertThat(legacyCorpus.path("aliases").path("units"))
        .noneMatch(
            unit ->
                "JAVA_METHOD".equals(unit.path("kind").asText())
                    && "method:neutral-helper".equals(unit.path("originalId").asText()));

    ConfiguredTypedPipeline currentConfiguration =
        writeTypedPipelineConfigurationV2("source-supplement-current-v2.yaml", fixture);
    CliResult currentPrepared =
        executeWithFactory(
            currentConfiguration.path(),
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(currentPrepared.exitCode())
        .withFailMessage(
            "v2 prepare-ontology failed. stdout:\n%s\nstderr:\n%s",
            currentPrepared.stdout(), currentPrepared.stderr())
        .isZero();
    String currentO0RunId = runId(currentPrepared);
    CliResult currentCorpusResult =
        artifact(currentConfiguration.path(), currentO0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(currentCorpusResult);
    JsonNode currentCorpus = JSON.readTree(currentCorpusResult.stdout());
    assertThat(currentCorpus.path("projectionRuleVersion").asText())
        .isEqualTo("ontology-model-projection-v2");
    String helperUnitRef = unitRef(currentCorpus, "JAVA_METHOD", "method:neutral-helper");
    String entryRef = entryRef(currentCorpus);

    CliResult repeatedCurrentPrepared =
        executeWithFactory(
            currentConfiguration.path(),
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(repeatedCurrentPrepared.exitCode())
        .withFailMessage(
            "repeated v2 prepare-ontology failed. stdout:\n%s\nstderr:\n%s",
            repeatedCurrentPrepared.stdout(), repeatedCurrentPrepared.stderr())
        .isZero();
    JsonNode repeatedCurrentCorpus =
        JSON.readTree(
            artifact(currentConfiguration.path(), runId(repeatedCurrentPrepared), "ONTOLOGY_CORPUS")
                .stdout());
    assertThat(repeatedCurrentCorpus.path("projectionRuleVersion").asText())
        .isEqualTo("ontology-model-projection-v2");
    assertThat(unitRef(repeatedCurrentCorpus, "JAVA_METHOD", "method:neutral-helper"))
        .isEqualTo(helperUnitRef);
    assertThat(entryRef(repeatedCurrentCorpus)).isEqualTo(entryRef);

    EntryEvidenceReader.Directory savedEvidence = reopenedEvidenceDirectory(fixture);
    JsonNode savedEntry =
        JSON.readTree(savedEvidence.entries().get(0).canonicalJson().copyToByteArray());
    assertThat(savedEntry.path("java").path("calls"))
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.path("resolution").asText()).isEqualTo("NAVIGATION_CONFLICT");
              assertThat(call.path("targets")).isEmpty();
              assertThat(call.path("observations").get(0).path("association").asText())
                  .isEqualTo("UNCONFIRMED");
              assertThat(call.path("observations").get(0).path("displayIdentity").asText())
                  .isEqualTo("fixture.RecordHandler.helper() : String");
            });

    List<StructuredModelRequest> requests = new ArrayList<>();
    int helperDeclarationStart =
        SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf("private String helper() { return \"restricted\"; }");
    int helperNameStart = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf("helper", helperDeclarationStart);
    SourceRange expectedUnconfirmedHelperSite =
        sourceRange(
            SOURCE_SUPPLEMENT_JAVA_SOURCE, helperNameStart, helperNameStart + "helper".length());
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                boolean review = request.taskKind().contains("REVIEW");
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                JsonNode packet = input.path("readingPacket");
                assertThat(packet.path("schemaVersion").asText())
                    .isEqualTo("ontology-model-reading-v4");
                List<JsonNode> selectedHelperBodies =
                    arrayValues(packet.path("units")).stream()
                        .filter(unit -> "JAVA_METHOD".equals(unit.path("kind").asText()))
                        .filter(
                            unit ->
                                "private String helper() { return \"restricted\"; }"
                                    .equals(unit.path("content").path("sourceText").asText()))
                        .toList();
                assertThat(selectedHelperBodies).hasSize(1);
                JsonNode selectedHelper = selectedHelperBodies.get(0);
                assertThat(
                        arrayValues(selectedHelper.path("entryUses")).stream()
                            .map(JsonNode::asText)
                            .toList())
                    .containsExactly(entryRef);
                JsonNode sourceSupplement = selectedHelper.path("content").path("sourceSupplement");
                assertThat(sourceSupplement.path("evidenceNature").asText())
                    .isEqualTo("UNCONFIRMED_COMPLETE_DECLARATION_CANDIDATE");
                assertThat(sourceSupplement.path("doesNotConfirmCallEdge").asBoolean()).isTrue();
                assertThat(arrayValues(sourceSupplement.path("observations")))
                    .singleElement()
                    .satisfies(
                        candidate -> {
                          assertThat(candidate.path("association").asText())
                              .isEqualTo("UNCONFIRMED");
                          assertThat(candidate.path("detail").asText())
                              .isEqualTo("NAVIGATION_CONFLICT_NOT_EXPANDED");
                          assertThat(containsRange(candidate, expectedUnconfirmedHelperSite))
                              .isTrue();
                        });
                assertThat(request.systemInstructions())
                    .isEqualTo(currentConfiguration.prompts().get(review ? "review" : "object"));
                assertThat(
                        containsText(input, "private String helper() { return \"restricted\"; }"))
                    .isTrue();
                assertThat(containsText(input, "UNSELECTED_SOURCE_SENTINEL")).isFalse();
                assertThat(containsText(input, "UNCONFIRMED")).isTrue();
                assertThat(containsText(input, "NAVIGATION_CONFLICT_NOT_EXPANDED")).isTrue();
                assertThat(containsRange(input, expectedUnconfirmedHelperSite))
                    .as("EXTRACT and REVIEW must receive the candidate's exact source site")
                    .isTrue();
                return new StructuredModelResponse(
                    formalTaskObjectResponse(
                        review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3",
                        "Q1",
                        entryRef),
                    identity);
              };
            };
    Path scope =
        writeSingleUnitObjectScope(
            temporaryDirectory.resolve("source-supplement-formal-scope.json"),
            "Q1",
            "supplemented-helper",
            entryRef,
            helperUnitRef);
    CliResult identified =
        executeWithFactory(
            currentConfiguration.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            currentO0RunId,
            "--scope",
            scope.toString());

    assertThat(identified.exitCode())
        .withFailMessage(
            "identify-ontology failed. stdout:\n%s\nstderr:\n%s",
            identified.stdout(), identified.stderr())
        .isZero();
    String o1RunId = runId(identified);
    assertThat(requests)
        .extracting(request -> request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT")
        .containsExactly("EXTRACT", "REVIEW");
    JsonNode identification =
        JSON.readTree(
            artifact(currentConfiguration.path(), o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(arrayValues(identification.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly("supplemented-helper/REVIEWED");
    assertThat(artifact(legacyConfiguration.path(), legacyO0RunId, "ONTOLOGY_CORPUS").stdout())
        .isEqualTo(legacyCorpusResult.stdout());
  }

  @Test
  void selectionV2ObjectSourcesBlockOnlyTheirUnreviewedQuestion() throws Exception {
    TechnicalFixture fixture = prepareRealR4("selection-v2-question-local-object-sources");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-selection-v2-object-sources.yaml", fixture);
    List<StructuredModelRequest> requests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String questionId = input.path("questionId").asText();
                String kind = input.path("taskKind").asText();
                boolean review = request.taskKind().contains("REVIEW");
                String schemaVersion =
                    review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3";
                ImmutableBytes response;
                if ("OBJECT".equals(kind)) {
                  response =
                      review && "Q1".equals(questionId)
                          ? formalTaskObjectResponseWithDanglingReference(
                              schemaVersion, questionId, "E1", "O3")
                          : formalTaskObjectResponse(
                              schemaVersion,
                              questionId,
                              "E1",
                              "Neutral " + questionId + " record shape",
                              "A distinct neutral source-backed shape for " + questionId + ".");
                } else if ("RELATE".equals(kind)) {
                  JsonNode object = ExplicitTypedPipelineScript.catalogEntry(input, "objects");
                  response =
                      formalTaskUnresolvedRelateResponse(
                          schemaVersion, object.path("catalogRef").asText());
                } else {
                  throw new AssertionError("unexpected selected task kind " + kind);
                }
                return new StructuredModelResponse(response, identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    Path scope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("selection-v2-object-sources-scope.json"),
            new ScopedQuestion("Q1", List.of(new ScopedTask("q1-object", "OBJECT"))),
            new ScopedQuestion("Q2", List.of(new ScopedTask("q2-object", "OBJECT"))));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isIn(0, 2);
    String o1RunId = runId(identified);
    JsonNode identification =
        JSON.readTree(artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(arrayValues(identification.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly("q1-object/REJECTED", "q2-object/REVIEWED");

    Path selection =
        writeRelateSelectionV2(
            temporaryDirectory.resolve("selection-v2-object-sources-relate.json"),
            o0RunId,
            List.of(o1RunId),
            new SelectedRelateQuestion("Q1", "q1-relate", List.of(new ObjectSource(o1RunId, "Q1"))),
            new SelectedRelateQuestion(
                "Q2", "q2-relate", List.of(new ObjectSource(o1RunId, "Q2"))));
    int requestsBeforeRelate = requests.size();
    CliResult related =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            selection.toString());
    assertThat(related.exitCode()).withFailMessage(related.stderr()).isIn(0, 2);
    assertThat(related.stdout()).isNotBlank();
    String o2RunId = runId(related);
    assertThat(o2RunId).isNotBlank();
    List<StructuredModelRequest> relateRequests =
        requests.subList(requestsBeforeRelate, requests.size());
    assertThat(relateRequests)
        .extracting(
            request ->
                ExplicitTypedPipelineScript.input(request).path("questionId").asText()
                    + "/"
                    + ExplicitTypedPipelineScript.input(request).path("taskKind").asText()
                    + "/"
                    + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT"))
        .containsExactly("Q2/RELATE/EXTRACT", "Q2/RELATE/REVIEW");
    JsonNode q2RelateInput = ExplicitTypedPipelineScript.input(relateRequests.get(0));
    assertThat(containsText(q2RelateInput.path("reviewedCatalog"), "Neutral Q2 record shape"))
        .isTrue();
    assertThat(containsText(q2RelateInput.path("reviewedCatalog"), "Neutral Q1 record shape"))
        .isFalse();

    CliResult relationsResult = artifact(configured.path(), o2RunId, "ONTOLOGY_RELATIONS");
    assertArtifactAvailable(relationsResult);
    JsonNode relations = JSON.readTree(relationsResult.stdout());
    assertThat(arrayValues(relations.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly("q1-relate/UNPROCESSED", "q2-relate/REVIEWED");
    assertThat(
            arrayValues(
                arrayValues(relations.path("taskOutcomes")).get(0).path("dependencyTaskRefs")))
        .singleElement()
        .satisfies(
            dependency -> {
              assertThat(dependency.path("runId").asText()).isEqualTo(o1RunId);
              assertThat(dependency.path("questionId").asText()).isEqualTo("Q1");
              assertThat(dependency.path("taskId").asText()).isEqualTo("q1-object");
            });
  }

  @Test
  void publishV2ClosesTheUnionOfIndependentO2SourcesAndRejectsAnOmittedO1() throws Exception {
    TechnicalFixture fixture = prepareRealR4("selection-v2-publish-union-closure");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-selection-v2-publish-union.yaml", fixture);
    List<StructuredModelRequest> requests = new ArrayList<>();
    AtomicInteger providerFactories = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String questionId = input.path("questionId").asText();
                String kind = input.path("taskKind").asText();
                boolean review = request.taskKind().contains("REVIEW");
                String schemaVersion =
                    review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3";
                ImmutableBytes response;
                if ("OBJECT".equals(kind)) {
                  response =
                      formalTaskObjectResponse(
                          schemaVersion,
                          questionId,
                          "E1",
                          "Neutral " + questionId + " record shape",
                          "A neutral source-backed record shape for " + questionId + ".");
                } else if ("ACTION".equals(kind)) {
                  String objectRef = ExplicitTypedPipelineScript.catalogRef(input, "objects");
                  response =
                      review
                          ? formalTaskActionResponseWithDanglingObjectReference(
                              schemaVersion, questionId, "E1", objectRef, "B999")
                          : formalTaskActionResponse(schemaVersion, questionId, "E1", objectRef);
                } else if ("RELATE".equals(kind)) {
                  String objectRef = ExplicitTypedPipelineScript.catalogRef(input, "objects");
                  response = formalTaskUnresolvedRelateResponse(schemaVersion, objectRef);
                } else {
                  throw new AssertionError("unexpected selected task kind " + kind);
                }
                return new StructuredModelResponse(response, identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);

    Path firstScope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("selection-v2-first-o1-scope.json"),
            new ScopedQuestion(
                "Q1",
                List.of(
                    new ScopedTask("first-object", "OBJECT"),
                    new ScopedTask("first-action", "ACTION"))));
    CliResult firstIdentified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            firstScope.toString());
    assertThat(firstIdentified.exitCode()).withFailMessage(firstIdentified.stderr()).isIn(0, 2);
    String firstO1RunId = runId(firstIdentified);
    JsonNode firstIdentification =
        JSON.readTree(
            artifact(configured.path(), firstO1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(arrayValues(firstIdentification.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly("first-object/REVIEWED", "first-action/REJECTED");

    Path secondScope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("selection-v2-second-o1-scope.json"),
            new ScopedQuestion("Q2", List.of(new ScopedTask("second-object", "OBJECT"))));
    CliResult secondIdentified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            secondScope.toString());
    assertThat(secondIdentified.exitCode()).withFailMessage(secondIdentified.stderr()).isZero();
    String secondO1RunId = runId(secondIdentified);

    Path firstRelationSelection =
        writeRelateSelectionV2(
            temporaryDirectory.resolve("selection-v2-first-o2.json"),
            o0RunId,
            List.of(firstO1RunId),
            new SelectedRelateQuestion(
                "Q1", "first-relate", List.of(new ObjectSource(firstO1RunId, "Q1"))));
    CliResult firstRelated =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            firstRelationSelection.toString());
    assertThat(firstRelated.exitCode()).withFailMessage(firstRelated.stderr()).isIn(0, 2);
    assertThat(firstRelated.stdout()).isNotBlank();
    String firstO2RunId = runId(firstRelated);
    assertThat(firstO2RunId).isNotBlank();

    Path secondRelationSelection =
        writeRelateSelectionV2(
            temporaryDirectory.resolve("selection-v2-second-o2.json"),
            o0RunId,
            List.of(secondO1RunId),
            new SelectedRelateQuestion(
                "Q2", "second-relate", List.of(new ObjectSource(secondO1RunId, "Q2"))));
    CliResult secondRelated =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            secondRelationSelection.toString());
    assertThat(secondRelated.exitCode()).withFailMessage(secondRelated.stderr()).isIn(0, 2);
    assertThat(secondRelated.stdout()).isNotBlank();
    String secondO2RunId = runId(secondRelated);
    assertThat(secondO2RunId).isNotBlank();

    int providersBeforeO3 = providerFactories.get();
    int requestsBeforeO3 = requests.size();
    Path incompletePublishSelection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("selection-v2-publish-missing-upstream.json"),
            o0RunId,
            List.of(firstO1RunId),
            List.of(firstO2RunId, secondO2RunId));
    CliResult refused =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            incompletePublishSelection.toString());
    assertThat(refused.exitCode()).isNotZero();
    assertThat(refused.stdout()).isEmpty();
    assertThat(refused.stderr()).contains("ONTOLOGY");
    assertThat(providerFactories).hasValue(providersBeforeO3);
    assertThat(requests).hasSize(requestsBeforeO3);

    Path completePublishSelection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("selection-v2-publish-complete-union.json"),
            o0RunId,
            List.of(firstO1RunId, secondO1RunId),
            List.of(firstO2RunId, secondO2RunId));
    CliResult published =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            completePublishSelection.toString());
    assertThat(published.exitCode()).withFailMessage(published.stderr()).isIn(0, 2);
    assertThat(published.stdout()).withFailMessage(published.stderr()).isNotBlank();
    String o3RunId = runId(published);
    assertThat(o3RunId).isNotBlank();
    assertThat(providerFactories).hasValue(providersBeforeO3);
    assertThat(requests).hasSize(requestsBeforeO3);
    CliResult coverageResult = artifact(configured.path(), o3RunId, "ONTOLOGY_COVERAGE");
    assertArtifactAvailable(coverageResult);
    JsonNode coverage = JSON.readTree(coverageResult.stdout());
    assertThat(coverage.path("coverageStatus").asText()).isEqualTo("INCOMPLETE");
    assertThat(arrayValues(coverage.path("taskDispositions")))
        .anySatisfy(
            disposition -> {
              assertThat(disposition.path("taskId").asText()).isEqualTo("first-action");
              assertThat(disposition.path("status").asText()).isEqualTo("REJECTED");
            });
    assertThat(arrayValues(coverage.path("taskOutcomes")))
        .anySatisfy(
            outcome -> {
              assertThat(outcome.path("taskId").asText()).isEqualTo("first-action");
              assertThat(outcome.path("status").asText()).isEqualTo("REJECTED");
              assertThat(outcome.path("questionId").asText()).isEqualTo("Q1");
            });
  }

  @Test
  void publishV2KeepsSameLocalTaskIdsOwnedByTheirOriginalRuns() throws Exception {
    CrossRunSameTaskO3Inputs inputs = prepareCrossRunSameTaskO3Inputs(false);
    Path selection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("same-local-task-owner-publish.json"),
            inputs.o0RunId(),
            List.of(inputs.firstO1RunId(), inputs.secondO1RunId()),
            List.of(inputs.firstO2RunId(), inputs.secondO2RunId()));
    int requestsBeforePublish = inputs.requests().size();
    CliResult published =
        executeWithFactory(
            inputs.configured().path(),
            inputs.providerFactory(),
            "publish-ontology",
            "--selection",
            selection.toString());

    assertThat(published.exitCode()).withFailMessage(published.stderr()).isIn(0, 2);
    assertThat(published.stdout()).withFailMessage(published.stderr()).isNotBlank();
    String o3RunId = runId(published);
    assertThat(o3RunId).isNotBlank();
    assertThat(inputs.requests()).hasSize(requestsBeforePublish);
    JsonNode observation =
        assertCommandAndInspectionAgree(
            published, inputs.configured().path(), "PUBLISH_ONTOLOGY", o3RunId, 0);
    assertThat(observation.path("lifecycleState").asText()).isEqualTo("FINISHED");
    JsonNode queryAction =
        arrayValues(observation.path("nextActions")).stream()
            .filter(action -> "QUERY_TASK".equals(action.path("kind").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(queryAction.path("requiresNewRun").asBoolean()).isFalse();
    assertThat(arrayValues(queryAction.path("taskRefs")))
        .extracting(
            reference ->
                reference.path("runId").asText()
                    + "/"
                    + reference.path("questionId").asText()
                    + "/"
                    + reference.path("taskId").asText())
        .containsExactly(
            inputs.firstO1RunId() + "/Q1/shared-task",
            inputs.secondO1RunId() + "/Q2/shared-task",
            inputs.firstO2RunId() + "/R1/shared-task",
            inputs.secondO2RunId() + "/R2/shared-task");
    assertThat(arrayValues(observation.path("nextActions")))
        .extracting(action -> action.path("kind").asText())
        .containsExactly("QUERY_TASK");

    JsonNode firstIdentification =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.firstO1RunId(), "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode secondIdentification =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.secondO1RunId(), "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode firstRelations =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.firstO2RunId(), "ONTOLOGY_RELATIONS")
                .stdout());
    JsonNode secondRelations =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.secondO2RunId(), "ONTOLOGY_RELATIONS")
                .stdout());
    List<String> expectedOwners =
        List.of(
            inputs.firstO1RunId()
                + "/Q1/shared-task/"
                + findTaskRecord(firstIdentification, "shared-task")
                    .path("producingTaskId")
                    .asText(),
            inputs.secondO1RunId()
                + "/Q2/shared-task/"
                + findTaskRecord(secondIdentification, "shared-task")
                    .path("producingTaskId")
                    .asText(),
            inputs.firstO2RunId()
                + "/R1/shared-task/"
                + findTaskRecord(firstRelations, "shared-task").path("producingTaskId").asText(),
            inputs.secondO2RunId()
                + "/R2/shared-task/"
                + findTaskRecord(secondRelations, "shared-task").path("producingTaskId").asText());
    CliResult coverageResult = artifact(inputs.configured().path(), o3RunId, "ONTOLOGY_COVERAGE");
    CliResult reviewResult = artifact(inputs.configured().path(), o3RunId, "ONTOLOGY_REVIEW");
    assertArtifactAvailable(coverageResult);
    assertArtifactAvailable(reviewResult);
    JsonNode coverage = JSON.readTree(coverageResult.stdout());
    JsonNode review = JSON.readTree(reviewResult.stdout());
    assertThat(coverage.path("schemaVersion").asText()).isEqualTo("ontology-coverage-v2");
    assertThat(review.path("schemaVersion").asText()).isEqualTo("ontology-review-v2");
    assertThat(inheritedTaskOutcomeOwners(coverage.path("taskOutcomes")))
        .containsExactlyElementsOf(expectedOwners);
    assertThat(inheritedTaskOutcomeOwners(review.path("taskOutcomes")))
        .containsExactlyElementsOf(expectedOwners);
    assertThat(inheritedTaskOutcomeOwners(coverage.path("taskOutcomes"))).doesNotHaveDuplicates();
  }

  @Test
  void publishV2AcceptsByteIdenticalFormalResultsFromDistinctO1Owners() throws Exception {
    CrossRunSameTaskO3Inputs inputs = prepareCrossRunSameTaskO3Inputs(false, false, true);
    JsonNode firstIdentification =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.firstO1RunId(), "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode secondIdentification =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.secondO1RunId(), "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode firstO1Task = findTaskRecord(firstIdentification, "shared-task");
    JsonNode secondO1Task = findTaskRecord(secondIdentification, "shared-task");
    assertThat(inputs.firstO1RunId()).isNotEqualTo(inputs.secondO1RunId());
    assertThat(firstO1Task.path("jobKey").asText()).isNotBlank();
    assertThat(firstO1Task.path("jobKey").asText()).isEqualTo(secondO1Task.path("jobKey").asText());
    assertThat(firstO1Task.path("producingTaskId").asText())
        .isEqualTo(secondO1Task.path("producingTaskId").asText());
    assertThat(firstO1Task.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(secondO1Task.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(arrayValues(firstIdentification.path("taskOutcomes")))
        .extracting(
            item ->
                item.path("questionId").asText()
                    + "/"
                    + item.path("taskId").asText()
                    + "/"
                    + item.path("status").asText())
        .containsExactly("Q1/shared-task/REVIEWED");
    assertThat(arrayValues(secondIdentification.path("taskOutcomes")))
        .extracting(
            item ->
                item.path("questionId").asText()
                    + "/"
                    + item.path("taskId").asText()
                    + "/"
                    + item.path("status").asText())
        .containsExactly("Q1/shared-task/REVIEWED");

    CliResult firstO1TaskResult =
        executePublic(
            inputs.configured().path(),
            "artifact",
            "--run",
            inputs.firstO1RunId(),
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            firstO1Task.path("producingTaskId").asText(),
            "--max-bytes",
            "524288");
    CliResult secondO1TaskResult =
        executePublic(
            inputs.configured().path(),
            "artifact",
            "--run",
            inputs.secondO1RunId(),
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            secondO1Task.path("producingTaskId").asText(),
            "--max-bytes",
            "524288");
    assertArtifactAvailable(firstO1TaskResult);
    assertArtifactAvailable(secondO1TaskResult);
    JsonNode firstO1TaskObservation = JSON.readTree(firstO1TaskResult.stdout());
    JsonNode secondO1TaskObservation = JSON.readTree(secondO1TaskResult.stdout());
    assertThat(firstO1TaskObservation.path("runId").asText()).isEqualTo(inputs.firstO1RunId());
    assertThat(secondO1TaskObservation.path("runId").asText()).isEqualTo(inputs.secondO1RunId());
    assertThat(firstO1TaskObservation.path("extract").path("request"))
        .isEqualTo(secondO1TaskObservation.path("extract").path("request"));
    assertThat(firstO1TaskObservation.path("review").path("request"))
        .isEqualTo(secondO1TaskObservation.path("review").path("request"));
    assertThat(firstO1TaskObservation.path("extract").path("response").path("rawResponseBase64"))
        .isEqualTo(
            secondO1TaskObservation.path("extract").path("response").path("rawResponseBase64"));
    assertThat(firstO1TaskObservation.path("review").path("response").path("rawResponseBase64"))
        .isEqualTo(
            secondO1TaskObservation.path("review").path("response").path("rawResponseBase64"));
    assertThat(firstO1TaskObservation.path("completion").path("identity"))
        .isEqualTo(secondO1TaskObservation.path("completion").path("identity"));
    assertThat(firstO1TaskObservation.path("completion").path("identity").path("reviewVersion"))
        .isEqualTo(
            secondO1TaskObservation.path("completion").path("identity").path("reviewVersion"));
    assertThat(firstO1TaskObservation.path("completion").path("review"))
        .isEqualTo(secondO1TaskObservation.path("completion").path("review"));

    JsonNode firstRelations =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.firstO2RunId(), "ONTOLOGY_RELATIONS")
                .stdout());
    JsonNode secondRelations =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.secondO2RunId(), "ONTOLOGY_RELATIONS")
                .stdout());
    assertThat(findTaskRecord(firstRelations, "shared-task").path("status").asText())
        .isEqualTo("REVIEWED");
    assertThat(findTaskRecord(secondRelations, "shared-task").path("status").asText())
        .isEqualTo("REVIEWED");
    assertThat(objectSourceOwners(firstRelations)).containsExactly(inputs.firstO1RunId() + "/Q1");
    assertThat(objectSourceOwners(secondRelations)).containsExactly(inputs.secondO1RunId() + "/Q1");

    Path selection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("byte-identical-cross-owner-publish.json"),
            inputs.o0RunId(),
            List.of(inputs.firstO1RunId(), inputs.secondO1RunId()),
            List.of(inputs.firstO2RunId(), inputs.secondO2RunId()));
    int requestsBeforePublish = inputs.requests().size();
    CliResult published =
        executeWithFactory(
            inputs.configured().path(),
            inputs.providerFactory(),
            "publish-ontology",
            "--selection",
            selection.toString());

    assertThat(published.exitCode()).withFailMessage(published.stderr()).isIn(0, 2);
    assertThat(published.stdout()).withFailMessage(published.stderr()).isNotBlank();
    String o3RunId = runId(published);
    JsonNode observation =
        assertCommandAndInspectionAgree(
            published, inputs.configured().path(), "PUBLISH_ONTOLOGY", o3RunId, 0);
    assertThat(observation.path("lifecycleState").asText()).isEqualTo("FINISHED");
    assertThat(observation.path("resultStatus").asText()).isIn("COMPLETED", "PARTIAL");
    assertThat(inputs.requests()).hasSize(requestsBeforePublish);

    CliResult ontology = artifact(inputs.configured().path(), o3RunId, "ONTOLOGY");
    CliResult coverageResult = artifact(inputs.configured().path(), o3RunId, "ONTOLOGY_COVERAGE");
    CliResult reviewResult = artifact(inputs.configured().path(), o3RunId, "ONTOLOGY_REVIEW");
    assertArtifactAvailable(ontology);
    assertArtifactAvailable(coverageResult);
    assertArtifactAvailable(reviewResult);
    JsonNode coverage = JSON.readTree(coverageResult.stdout());
    JsonNode review = JSON.readTree(reviewResult.stdout());
    List<String> expectedOwners =
        List.of(
            inputs.firstO1RunId()
                + "/Q1/shared-task/"
                + firstO1Task.path("producingTaskId").asText(),
            inputs.secondO1RunId()
                + "/Q1/shared-task/"
                + secondO1Task.path("producingTaskId").asText(),
            inputs.firstO2RunId()
                + "/R1/shared-task/"
                + findTaskRecord(firstRelations, "shared-task").path("producingTaskId").asText(),
            inputs.secondO2RunId()
                + "/R2/shared-task/"
                + findTaskRecord(secondRelations, "shared-task").path("producingTaskId").asText());
    assertThat(inheritedTaskOutcomeOwners(observation.path("taskOutcomes")))
        .containsExactlyElementsOf(expectedOwners);
    assertThat(inheritedTaskOutcomeOwners(coverage.path("taskOutcomes")))
        .containsExactlyElementsOf(expectedOwners);
    assertThat(inheritedTaskOutcomeOwners(review.path("taskOutcomes")))
        .containsExactlyElementsOf(expectedOwners);
  }

  @Test
  void publishV2PreservesEqualLocalProblemsFromBothPartialIdentificationOwners() throws Exception {
    CrossRunSameTaskO3Inputs inputs = prepareCrossRunSameTaskO3Inputs(false, true);
    JsonNode firstIdentification =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.firstO1RunId(), "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode secondIdentification =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.secondO1RunId(), "ONTOLOGY_IDENTIFICATION")
                .stdout());
    CliResult firstInspectionResult =
        executePublic(inputs.configured().path(), "inspect", "--run", inputs.firstO1RunId());
    CliResult secondInspectionResult =
        executePublic(inputs.configured().path(), "inspect", "--run", inputs.secondO1RunId());
    assertThat(firstInspectionResult.exitCode())
        .withFailMessage(firstInspectionResult.stderr())
        .isZero();
    assertThat(secondInspectionResult.exitCode())
        .withFailMessage(secondInspectionResult.stderr())
        .isZero();
    JsonNode firstInspection = JSON.readTree(firstInspectionResult.stdout());
    JsonNode secondInspection = JSON.readTree(secondInspectionResult.stdout());
    List<JsonNode> firstProblems = arrayValues(firstInspection.path("problems"));
    List<JsonNode> secondProblems = arrayValues(secondInspection.path("problems"));
    assertThat(firstProblems).hasSize(1);
    assertThat(secondProblems).hasSize(1);
    JsonNode firstProblem = firstProblems.get(0);
    JsonNode secondProblem = secondProblems.get(0);
    assertThat(firstProblem.path("taskId").asText()).isEqualTo("shared-invalid");
    assertThat(secondProblem).isEqualTo(firstProblem);
    assertThat(arrayValues(firstIdentification.path("taskOutcomes")))
        .extracting(
            item ->
                item.path("questionId").asText()
                    + "/"
                    + item.path("taskId").asText()
                    + "/"
                    + item.path("status").asText())
        .containsExactly(
            "Qbad/shared-invalid/REJECTED",
            "Q1/reviewed-left/REVIEWED",
            "Q1/reviewed-right/REVIEWED");
    assertThat(arrayValues(secondIdentification.path("taskOutcomes")))
        .extracting(
            item ->
                item.path("questionId").asText()
                    + "/"
                    + item.path("taskId").asText()
                    + "/"
                    + item.path("status").asText())
        .containsExactly(
            "Qbad/shared-invalid/REJECTED",
            "Q1/reviewed-left/REVIEWED",
            "Q1/reviewed-right/REVIEWED");

    Path selection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("partial-identification-duplicate-problems-publish.json"),
            inputs.o0RunId(),
            List.of(inputs.firstO1RunId(), inputs.secondO1RunId()),
            List.of(inputs.firstO2RunId(), inputs.secondO2RunId()));
    int requestsBeforePublish = inputs.requests().size();
    CliResult published =
        executeWithFactory(
            inputs.configured().path(),
            inputs.providerFactory(),
            "publish-ontology",
            "--selection",
            selection.toString());

    assertThat(published.exitCode()).isIn(0, 2);
    assertThat(published.stdout()).withFailMessage(published.stderr()).isNotBlank();
    String o3RunId = runId(published);
    JsonNode observation =
        assertCommandAndInspectionAgree(
            published, inputs.configured().path(), "PUBLISH_ONTOLOGY", o3RunId, 0);
    assertThat(inputs.requests()).hasSize(requestsBeforePublish);
    List<JsonNode> problems = arrayValues(observation.path("problems"));
    assertThat(problems).hasSize(2);
    assertThat(
            problems.stream()
                .map(
                    problem ->
                        problem.path("taskId").asText()
                            + "/"
                            + problem.path("code").asText()
                            + "/"
                            + problem.path("category").asText()
                            + "/"
                            + problem.path("stage").asText())
                .toList())
        .containsExactlyElementsOf(
            List.of(problemIdentity(firstProblem), problemIdentity(secondProblem)));
    assertThat(problems.get(0)).isEqualTo(problems.get(1));
    List<String> rejectedOwners =
        arrayValues(observation.path("taskOutcomes")).stream()
            .filter(item -> "shared-invalid".equals(item.path("taskId").asText()))
            .map(
                item ->
                    item.path("runId").asText()
                        + "/"
                        + item.path("questionId").asText()
                        + "/"
                        + item.path("taskId").asText())
            .toList();
    assertThat(rejectedOwners)
        .containsExactly(
            inputs.firstO1RunId() + "/Qbad/shared-invalid",
            inputs.secondO1RunId() + "/Qbad/shared-invalid");
  }

  @Test
  void publishV2RetainsSameLocalUnprocessedDependencyTaskFromBothOwners() throws Exception {
    CrossRunSameTaskO3Inputs inputs = prepareCrossRunSameTaskO3Inputs(false, false, false, true);
    JsonNode firstIdentification =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.firstO1RunId(), "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode secondIdentification =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.secondO1RunId(), "ONTOLOGY_IDENTIFICATION")
                .stdout());
    JsonNode firstBlocked =
        arrayValues(firstIdentification.path("taskOutcomes")).stream()
            .filter(item -> "shared-unprocessed".equals(item.path("taskId").asText()))
            .findFirst()
            .orElseThrow();
    JsonNode secondBlocked =
        arrayValues(secondIdentification.path("taskOutcomes")).stream()
            .filter(item -> "shared-unprocessed".equals(item.path("taskId").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(firstBlocked.path("questionId").asText()).isEqualTo("Qbad");
    assertThat(secondBlocked.path("questionId").asText()).isEqualTo("Qbad");
    assertThat(firstBlocked.path("status").asText()).isEqualTo("UNPROCESSED");
    assertThat(secondBlocked.path("status").asText()).isEqualTo("UNPROCESSED");
    assertThat(firstBlocked.path("reason").path("code").asText())
        .isEqualTo("DEPENDENCY_NOT_REVIEWED");
    assertThat(secondBlocked.path("reason").path("code").asText())
        .isEqualTo("DEPENDENCY_NOT_REVIEWED");
    assertThat(arrayValues(firstBlocked.path("dependencyTaskRefs")))
        .singleElement()
        .satisfies(
            dependency -> {
              assertThat(dependency.path("runId").asText()).isEqualTo(inputs.firstO1RunId());
              assertThat(dependency.path("questionId").asText()).isEqualTo("Qbad");
              assertThat(dependency.path("taskId").asText()).isEqualTo("bad-object-first");
            });
    assertThat(arrayValues(secondBlocked.path("dependencyTaskRefs")))
        .singleElement()
        .satisfies(
            dependency -> {
              assertThat(dependency.path("runId").asText()).isEqualTo(inputs.secondO1RunId());
              assertThat(dependency.path("questionId").asText()).isEqualTo("Qbad");
              assertThat(dependency.path("taskId").asText()).isEqualTo("bad-object-second");
            });
    assertThat(firstBlocked.path("reason")).isNotEqualTo(secondBlocked.path("reason"));
    assertThat(arrayValues(firstIdentification.path("taskRecords")))
        .extracting(item -> item.path("taskId").asText())
        .doesNotContain("shared-unprocessed");
    assertThat(arrayValues(secondIdentification.path("taskRecords")))
        .extracting(item -> item.path("taskId").asText())
        .doesNotContain("shared-unprocessed");

    Path selection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("same-local-unprocessed-dependency-publish.json"),
            inputs.o0RunId(),
            List.of(inputs.firstO1RunId(), inputs.secondO1RunId()),
            List.of(inputs.firstO2RunId(), inputs.secondO2RunId()));
    int requestsBeforePublish = inputs.requests().size();
    CliResult published =
        executeWithFactory(
            inputs.configured().path(),
            inputs.providerFactory(),
            "publish-ontology",
            "--selection",
            selection.toString());

    assertThat(published.exitCode()).isIn(0, 2);
    assertThat(published.stdout()).withFailMessage(published.stderr()).isNotBlank();
    String o3RunId = runId(published);
    JsonNode observation =
        assertCommandAndInspectionAgree(
            published, inputs.configured().path(), "PUBLISH_ONTOLOGY", o3RunId, 0);
    assertThat(observation.path("lifecycleState").asText()).isEqualTo("FAILED");
    assertThat(observation.path("resultStatus").asText()).isEqualTo("PARTIAL");
    assertThat(inputs.requests()).hasSize(requestsBeforePublish);

    CliResult coverageResult = artifact(inputs.configured().path(), o3RunId, "ONTOLOGY_COVERAGE");
    CliResult reviewResult = artifact(inputs.configured().path(), o3RunId, "ONTOLOGY_REVIEW");
    assertArtifactAvailable(coverageResult);
    assertArtifactAvailable(reviewResult);
    JsonNode coverage = JSON.readTree(coverageResult.stdout());
    JsonNode review = JSON.readTree(reviewResult.stdout());
    assertThat(coverage.path("coverageStatus").asText()).isEqualTo("INCOMPLETE");
    for (JsonNode document : List.of(observation, coverage, review)) {
      List<JsonNode> blockedOwners =
          arrayValues(document.path("taskOutcomes")).stream()
              .filter(item -> "shared-unprocessed".equals(item.path("taskId").asText()))
              .toList();
      assertThat(blockedOwners)
          .extracting(
              item ->
                  item.path("runId").asText()
                      + "/"
                      + item.path("questionId").asText()
                      + "/"
                      + item.path("taskId").asText()
                      + "/"
                      + item.path("status").asText())
          .containsExactly(
              inputs.firstO1RunId() + "/Qbad/shared-unprocessed/UNPROCESSED",
              inputs.secondO1RunId() + "/Qbad/shared-unprocessed/UNPROCESSED");
    }
    assertThat(arrayValues(coverage.path("taskDispositions")))
        .filteredOn(item -> "shared-unprocessed".equals(item.path("taskId").asText()))
        .hasSize(2);
  }

  @Test
  void validV2SelectionPersistsDeterministicAssemblyConflictAsFailedWithoutReceipt()
      throws Exception {
    CrossRunSameTaskO3Inputs inputs = prepareCrossRunSameTaskO3Inputs(true);
    Path selection =
        writePublishSelectionV2(
            temporaryDirectory.resolve("same-local-task-assembly-conflict-publish.json"),
            inputs.o0RunId(),
            List.of(inputs.firstO1RunId(), inputs.secondO1RunId()),
            List.of(inputs.firstO2RunId(), inputs.secondO2RunId()));
    int requestsBeforePublish = inputs.requests().size();
    CliResult published =
        executeWithFactory(
            inputs.configured().path(),
            inputs.providerFactory(),
            "publish-ontology",
            "--selection",
            selection.toString());

    assertThat(published.exitCode()).isNotZero();
    assertThat(published.stdout()).withFailMessage(published.stderr()).isNotBlank();
    String o3RunId = runId(published);
    assertThat(o3RunId).isNotBlank();
    assertThat(inputs.requests()).hasSize(requestsBeforePublish);
    CliResult inspectResult =
        executePublic(inputs.configured().path(), "inspect", "--run", o3RunId);
    assertThat(inspectResult.exitCode()).withFailMessage(inspectResult.stderr()).isZero();
    JsonNode inspection = JSON.readTree(inspectResult.stdout());
    assertThat(inspection.path("lifecycleState").asText()).isEqualTo("FAILED");
    assertThat(inspection.path("operation").asText()).isEqualTo("PUBLISH_ONTOLOGY");
    assertThat(inspection.path("modelRequestsDispatched").asInt()).isZero();
    assertThat(arrayValues(inspection.path("problems")))
        .singleElement()
        .satisfies(
            problem -> {
              assertThat(problem.path("code").asText())
                  .isEqualTo("ONTOLOGY_ASSEMBLY_DEFINITION_CONFLICT");
              assertThat(problem.path("category").asText()).isEqualTo("ASSEMBLY");
              assertThat(problem.path("stage").asText()).isEqualTo("ASSEMBLY");
            });
    CliResult diagnosticResult =
        artifact(inputs.configured().path(), o3RunId, "ONTOLOGY_ASSEMBLY_DIAGNOSTIC");
    assertArtifactAvailable(diagnosticResult);
    JsonNode diagnostic = JSON.readTree(diagnosticResult.stdout());
    assertThat(diagnostic.path("ontology").isMissingNode()).isTrue();
    assertThat(diagnostic.path("publicationStatus").isMissingNode()).isTrue();
    assertThat(diagnosticResult.stdout()).doesNotContain("DRAFT_REVIEWABLE", "ontologyBase64");
    JsonNode firstRelations =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.firstO2RunId(), "ONTOLOGY_RELATIONS")
                .stdout());
    JsonNode secondRelations =
        JSON.readTree(
            artifact(inputs.configured().path(), inputs.secondO2RunId(), "ONTOLOGY_RELATIONS")
                .stdout());
    assertThat(arrayValues(diagnostic.path("assemblyIssues")))
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.path("code").asText()).isEqualTo("IDENTITY_CANONICAL_CONFLICT");
              assertThat(stringValues(issue.path("definitionRefs")))
                  .hasSize(2)
                  .allMatch(reference -> reference.matches("ontology-object:[0-9a-f]{64}"));
              assertThat(stringValues(issue.path("producingTaskIds")))
                  .containsExactlyInAnyOrder(
                      findTaskRecord(firstRelations, "shared-task")
                          .path("producingTaskId")
                          .asText(),
                      findTaskRecord(secondRelations, "shared-task")
                          .path("producingTaskId")
                          .asText());
            });
    assertThat(availablePublicArtifactKeys(inputs.configured().path(), o3RunId))
        .doesNotContain(
            "ONTOLOGY", "ONTOLOGY_COVERAGE", "ONTOLOGY_REVIEW", "ONTOLOGY_SOURCE_INDEX");
    assertArtifactAvailable(
        artifact(inputs.configured().path(), inputs.firstO1RunId(), "ONTOLOGY_IDENTIFICATION"));
    assertArtifactAvailable(
        artifact(inputs.configured().path(), inputs.secondO1RunId(), "ONTOLOGY_IDENTIFICATION"));
    assertArtifactAvailable(
        artifact(inputs.configured().path(), inputs.firstO2RunId(), "ONTOLOGY_RELATIONS"));
    assertArtifactAvailable(
        artifact(inputs.configured().path(), inputs.secondO2RunId(), "ONTOLOGY_RELATIONS"));
  }

  @Test
  void malformedFormalReadingResponseIsLocalAndPreservesTheActualResponse() throws Exception {
    TechnicalFixture fixture = prepareRealR4("malformed-formal-reading-local-outcome");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-malformed-formal-reading-v2.yaml", fixture);
    CliResult prepared =
        executePublic(configured.path(), "prepare-ontology", "--evidence-run", fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    JsonNode corpus =
        JSON.readTree(artifact(configured.path(), o0RunId, "ONTOLOGY_CORPUS").stdout());
    String entryRef = entryRef(corpus);
    String methodRef = unitRef(corpus, "JAVA_METHOD", "method:neutral-list");
    Path scope =
        writeMixedReadingAndExplicitObjectScope(
            temporaryDirectory.resolve("malformed-formal-reading-scope.json"), entryRef, methodRef);
    byte[] malformedReadingResponse =
        "{\"schemaVersion\":\"reading-response-v3\",\"actions\":[".getBytes(StandardCharsets.UTF_8);
    List<StructuredModelRequest> requests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider> factory =
        declaration -> {
          ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
          return request -> {
            requests.add(request);
            JsonNode input = ExplicitTypedPipelineScript.input(request);
            if (request.taskKind().contains("READING")) {
              assertThat(input.path("questionId").asText()).isEqualTo("Q1");
              return new StructuredModelResponse(
                  ImmutableBytes.copyOf(malformedReadingResponse), identity);
            }
            String questionId = input.path("questionId").asText();
            assertThat(questionId).isEqualTo("Q2");
            boolean review = request.taskKind().contains("REVIEW");
            String schemaVersion =
                review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3";
            return new StructuredModelResponse(
                formalTaskObjectResponse(schemaVersion, questionId, entryRef), identity);
          };
        };

    CliResult identified =
        executeWithFactory(
            configured.path(),
            factory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isIn(0, 2);
    assertThat(identified.stdout()).isNotBlank();
    String o1RunId = runId(identified);
    assertThat(requests).hasSize(3);
    assertThat(requests.get(0).taskKind()).contains("READING");
    assertThat(requests.get(1).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(requests.get(2).taskKind()).contains("OBJECT", "REVIEW");
    assertThat(ExplicitTypedPipelineScript.input(requests.get(1)).path("questionId").asText())
        .isEqualTo("Q2");

    JsonNode identification =
        JSON.readTree(artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(identification.path("schemaVersion").asText())
        .isEqualTo("ontology-identification-v2");
    List<JsonNode> outcomes = arrayValues(identification.path("taskOutcomes"));
    assertThat(outcomes)
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly("bad-reading-task/UNPROCESSED", "independent-object-task/REVIEWED");
    JsonNode failedReading = outcomes.get(0);
    assertThat(failedReading.path("reason").path("category").asText()).isEqualTo("MODEL_OUTPUT");
    assertThat(failedReading.path("reason").path("stage").asText()).isEqualTo("READING");
    assertThat(failedReading.path("producingTaskId").isNull()).isTrue();
    assertThat(failedReading.path("jobKey").isNull()).isTrue();
    assertThat(arrayValues(identification.path("taskRecords")))
        .extracting(item -> item.path("taskId").asText())
        .containsExactly("independent-object-task");

    PrivateModelJobResultStore privateJobs =
        new PrivateModelJobResultStore(
            fixture.runStore().resolve("ontology-journal"),
            AnalysisRunId.parse(o1RunId),
            "ontology");
    List<ObjectNode> failures = privateJobs.listTerminalFailures();
    assertThat(failures).hasSize(1);
    String readingJobKey = failures.get(0).path("jobKey").asText();
    assertThat(readingJobKey).startsWith("ontology-decision-");
    ObjectNode savedResponse =
        privateJobs
            .readStageAttemptRecord(readingJobKey, "formal_reading", 1, "response")
            .orElseThrow();
    assertThat(Base64.getDecoder().decode(savedResponse.path("rawResponseBase64").asText()))
        .containsExactly(malformedReadingResponse);
    assertThat(privateJobs.listReviewedResults())
        .extracting(item -> item.path("taskId").asText())
        .containsExactly("independent-object-task");
  }

  @Test
  void newIdentificationPolicyReopensHistoricalV1CorpusWithoutChangingItsReceipt()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("policy-provenance-v1-o0-v2-o1");
    Path o0Configuration =
        writeOntologyConfig(
            "ontology-policy-provenance-o0-v1.yaml", fixture, fixture.archive(), null, false);
    byte[] historicalPolicyBytes = Files.readAllBytes(ONTOLOGY_POLICY_SET);
    AtomicInteger providerFactories = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              throw new AssertionError("O0 must not initialize a model Provider");
            };

    CliResult prepared =
        executeWithFactory(
            o0Configuration,
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    CliResult originalCorpusResult = artifact(o0Configuration, o0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(originalCorpusResult);
    JsonNode originalCorpus = JSON.readTree(originalCorpusResult.stdout());
    assertThat(originalCorpus.path("ownerRunId").asText()).isEqualTo(o0RunId);

    ReopenedModulePublication originalO0Module =
        reopenedOntologyModule(fixture.runStore(), o0RunId);
    var originalO0Receipt = originalO0Module.receipt();
    var originalV1PolicyReference =
        SourceAnalysisExecution.loadPolicies(ONTOLOGY_POLICY_SET, CANONICAL).reference();
    assertThat(originalO0Receipt.controls().artifactPolicyRegistryRef())
        .isEqualTo(originalV1PolicyReference);
    ArtifactReference originalCorpusReference =
        modulePayloadReference(originalO0Module, "ONTOLOGY_CORPUS");

    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-policy-provenance-o1-v2.yaml", fixture);
    ExplicitTypedPipelineScript script = new ExplicitTypedPipelineScript(configured.prompts());
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        typedProviderFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return script.provider(declaration);
            };
    Path scope = writeExplicitScope(temporaryDirectory.resolve("policy-provenance-scope.json"));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            typedProviderFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    String o1RunId = runId(identified);
    assertThat(script.requests).hasSize(2);

    CliResult reopenedCorpusResult = artifact(configured.path(), o0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(reopenedCorpusResult);
    assertThat(JSON.readTree(reopenedCorpusResult.stdout())).isEqualTo(originalCorpus);
    ReopenedModulePublication reopenedO0Module =
        reopenedOntologyModule(fixture.runStore(), o0RunId);
    assertThat(reopenedO0Module.receipt()).isEqualTo(originalO0Receipt);
    assertThat(reopenedO0Module.receipt().controls().artifactPolicyRegistryRef())
        .isEqualTo(originalV1PolicyReference);

    ReopenedModulePublication o1Module =
        reopenedOntologyModule(fixture.runStore(), o1RunId, ONTOLOGY_POLICY_SET_V2);
    var expectedV2PolicyReference =
        SourceAnalysisExecution.loadPolicies(ONTOLOGY_POLICY_SET_V2, CANONICAL).reference();
    assertThat(o1Module.receipt().controls().artifactPolicyRegistryRef())
        .isEqualTo(expectedV2PolicyReference);
    assertThat(o1Module.receipt().upstreamArtifacts()).contains(originalCorpusReference);
    assertThat(Files.readAllBytes(ONTOLOGY_POLICY_SET)).containsExactly(historicalPolicyBytes);
    assertThat(providerFactories).hasValue(1);
  }

  @Test
  void v2RelationReopensV1CorpusThroughItsSavedV2IdentificationOwner() throws Exception {
    TechnicalFixture fixture = prepareRealR4("v2-o1-v1-o0-owner-chain");
    Path v1Configuration =
        writeOntologyConfig(
            "v2-o1-v1-o0-owner-chain-o0.yaml", fixture, fixture.archive(), null, false);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        unusedProvider =
            declaration -> {
              throw new AssertionError("O0 preparation must not initialize a model Provider");
            };
    CliResult prepared =
        executeWithFactory(
            v1Configuration,
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    ReopenedModulePublication o0Module = reopenedOntologyModule(fixture.runStore(), o0RunId);
    ArtifactReference o0CorpusReference = modulePayloadReference(o0Module, "ONTOLOGY_CORPUS");

    ConfiguredTypedPipeline v2Configuration =
        writeTypedPipelineConfigurationV2("v2-o1-v1-o0-owner-chain-o1-v2.yaml", fixture);
    AtomicInteger providerFactories = new AtomicInteger();
    ExplicitTypedPipelineScript script = new ExplicitTypedPipelineScript(v2Configuration.prompts());
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return script.provider(declaration);
            };
    Path scope = writeExplicitScope(temporaryDirectory.resolve("v2-o1-v1-o0-owner-scope.json"));
    CliResult identified =
        executeWithFactory(
            v2Configuration.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    String o1RunId = runId(identified);
    ReopenedModulePublication o1Module =
        reopenedOntologyModule(fixture.runStore(), o1RunId, ONTOLOGY_POLICY_SET_V2);
    var v2PolicyReference =
        SourceAnalysisExecution.loadPolicies(ONTOLOGY_POLICY_SET_V2, CANONICAL).reference();
    assertThat(o1Module.receipt().controls().artifactPolicyRegistryRef())
        .isEqualTo(v2PolicyReference);
    assertThat(o1Module.receipt().upstreamArtifacts()).contains(o0CorpusReference);

    Path v1RelationSelection =
        writeSelection(
            temporaryDirectory.resolve("v2-o1-v1-o0-owner-chain-relate-v1.json"),
            "RELATE",
            o0RunId,
            List.of(o1RunId),
            List.of(),
            true);
    JsonNode selectionSnapshot =
        CANONICAL.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(v1RelationSelection)));
    int factoriesBeforeO2 = providerFactories.get();
    CliResult related =
        executeWithFactory(
            v2Configuration.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            v1RelationSelection.toString());
    assertThat(related.exitCode()).withFailMessage(related.stderr()).isZero();
    assertThat(related.stdout()).isNotBlank();
    String o2RunId = runId(related);
    assertThat(o2RunId).isNotBlank();
    assertThat(providerFactories).hasValue(factoriesBeforeO2);
    assertThat(script.requests).hasSize(2);

    CliResult relationsResult = artifact(v2Configuration.path(), o2RunId, "ONTOLOGY_RELATIONS");
    assertArtifactAvailable(relationsResult);
    JsonNode relations = JSON.readTree(relationsResult.stdout());
    assertThat(relations.path("selection")).isEqualTo(selectionSnapshot);
    assertThat(relations.path("selection").path("schemaVersion").asText())
        .isEqualTo("ontology-selection-v1");
    assertThat(relations.path("selection").path("questions")).isEmpty();
    assertThat(relations.path("status").asText()).isEqualTo("UNDETERMINED");

    ReopenedModulePublication o2Module =
        reopenedOntologyModule(fixture.runStore(), o2RunId, ONTOLOGY_POLICY_SET_V2);
    assertThat(o2Module.reference().address().runId().value()).isEqualTo(o2RunId);
    assertThat(o2Module.receipt().controls().artifactPolicyRegistryRef())
        .isEqualTo(v2PolicyReference);
    assertThat(o2Module.receipt().upstreamArtifacts())
        .containsAll(modulePayloadReferences(o1Module));
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      var persistedO2 =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, AnalysisRunId.parse(o2RunId));
      assertThat(persistedO2.request().ontologyInputs().identificationPublications())
          .containsExactly(o1Module.reference());
      JsonNode o2Request = CANONICAL.parseCanonical(persistedO2.canonicalJson());
      assertThat(relations.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(o2Request.path("ontologyInputs").path("identificationPublications"));
    }
  }

  @Test
  void v2ConsumerReadsHistoricalV1IdentificationOnlyThroughItsExactOwnerRegistry()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("v2-consumer-historical-v1-identification");
    byte[] v1PolicyBytes = Files.readAllBytes(ONTOLOGY_POLICY_SET);
    ConfiguredTypedPipeline v1Configuration =
        writeTypedPipelineConfiguration("historical-v1-identification.yaml", fixture);
    AtomicInteger providerFactories = new AtomicInteger();
    ExplicitTypedPipelineScript script = new ExplicitTypedPipelineScript(v1Configuration.prompts());
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return script.provider(declaration);
            };
    CliResult prepared =
        executeWithFactory(
            v1Configuration.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);

    Path scope =
        writeExplicitScope(temporaryDirectory.resolve("historical-v1-identification-scope.json"));
    CliResult identified =
        executeWithFactory(
            v1Configuration.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    String o1RunId = runId(identified);
    CliResult identificationBeforeResult =
        artifact(v1Configuration.path(), o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identificationBeforeResult);
    JsonNode identificationBefore = JSON.readTree(identificationBeforeResult.stdout());
    assertThat(identificationBefore.path("schemaVersion").asText())
        .isEqualTo("ontology-identification-v1");
    assertThat(identificationBefore.path("taskOutcomes").isMissingNode()).isTrue();
    JsonNode objectTask = findTaskRecord(identificationBefore, "object-task");
    String producingTaskId = objectTask.path("producingTaskId").asText();
    assertThat(producingTaskId).isNotBlank();
    CliResult taskBeforeResult =
        executePublic(
            v1Configuration.path(),
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            producingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(taskBeforeResult);
    JsonNode taskBefore = JSON.readTree(taskBeforeResult.stdout());
    String originalExtractResponse =
        taskBefore.path("extract").path("response").path("rawResponseBase64").asText();
    String originalReviewResponse =
        taskBefore.path("review").path("response").path("rawResponseBase64").asText();
    assertThat(originalExtractResponse).isNotBlank();
    assertThat(originalReviewResponse).isNotBlank();

    ReopenedModulePublication historicalO1Before =
        reopenedOntologyModule(fixture.runStore(), o1RunId, ONTOLOGY_POLICY_SET);
    var historicalO1ReceiptBefore = historicalO1Before.receipt();
    var expectedV1PolicyReference =
        SourceAnalysisExecution.loadPolicies(ONTOLOGY_POLICY_SET, CANONICAL).reference();
    assertThat(historicalO1ReceiptBefore.controls().artifactPolicyRegistryRef())
        .isEqualTo(expectedV1PolicyReference);
    assertThat(
            historicalO1Before.payloads().stream()
                .filter(
                    payload ->
                        "ONTOLOGY_IDENTIFICATION".equals(payload.descriptor().artifactType()))
                .findFirst()
                .orElseThrow()
                .descriptor()
                .schemaVersion())
        .isEqualTo("ontology-identification-v1");

    ConfiguredTypedPipeline v2Configuration =
        writeTypedPipelineConfigurationV2("historical-v1-identification-consumer-v2.yaml", fixture);
    Path activeV1RelationSelection =
        writeRelateSelection(
            temporaryDirectory.resolve("historical-v1-identification-active-relate-v1.json"),
            o0RunId,
            o1RunId);
    int factoriesBeforeActiveV1 = providerFactories.get();
    int requestsBeforeActiveV1 = script.requests.size();
    CliResult activeV1Refused =
        executeWithFactory(
            v2Configuration.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            activeV1RelationSelection.toString());
    assertThat(activeV1Refused.exitCode()).isNotZero();
    assertThat(activeV1Refused.stdout()).isEmpty();
    assertThat(activeV1Refused.stderr()).contains("ONTOLOGY_SELECTION_VERSION_INVALID");
    assertThat(providerFactories).hasValue(factoriesBeforeActiveV1);
    assertThat(script.requests).hasSize(requestsBeforeActiveV1);

    Path v1RelationSelection =
        writeSelection(
            temporaryDirectory.resolve("historical-v1-identification-relate-v1.json"),
            "RELATE",
            o0RunId,
            List.of(o1RunId),
            List.of(),
            true);
    JsonNode selectionSnapshot =
        CANONICAL.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(v1RelationSelection)));
    int factoriesBeforeO2 = providerFactories.get();
    CliResult related =
        executeWithFactory(
            v2Configuration.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            v1RelationSelection.toString());
    assertThat(related.exitCode()).withFailMessage(related.stderr()).isZero();
    assertThat(related.stdout()).isNotBlank();
    String o2RunId = runId(related);
    assertThat(o2RunId).isNotBlank();
    assertThat(providerFactories).hasValue(factoriesBeforeO2);
    assertThat(script.requests).hasSize(2);

    CliResult relationsResult = artifact(v2Configuration.path(), o2RunId, "ONTOLOGY_RELATIONS");
    assertArtifactAvailable(relationsResult);
    JsonNode relations = JSON.readTree(relationsResult.stdout());
    assertThat(relations.path("selection")).isEqualTo(selectionSnapshot);
    assertThat(relations.path("selection").path("schemaVersion").asText())
        .isEqualTo("ontology-selection-v1");
    assertThat(relations.path("selection").path("questions")).isEmpty();

    ReopenedModulePublication o2Module =
        reopenedOntologyModule(fixture.runStore(), o2RunId, ONTOLOGY_POLICY_SET_V2);
    assertThat(o2Module.reference().address().runId().value()).isEqualTo(o2RunId);
    assertThat(o2Module.receipt().upstreamArtifacts())
        .containsAll(modulePayloadReferences(historicalO1Before));
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      var persistedO2 =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, AnalysisRunId.parse(o2RunId));
      assertThat(persistedO2.request().ontologyInputs().identificationPublications())
          .containsExactly(historicalO1Before.reference());
      JsonNode o2Request = CANONICAL.parseCanonical(persistedO2.canonicalJson());
      assertThat(relations.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(o2Request.path("ontologyInputs").path("identificationPublications"));
    }

    CliResult identificationAfterResult =
        artifact(v2Configuration.path(), o1RunId, "ONTOLOGY_IDENTIFICATION");
    CliResult taskAfterResult =
        executePublic(
            v2Configuration.path(),
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            producingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(identificationAfterResult);
    assertArtifactAvailable(taskAfterResult);
    assertThat(identificationAfterResult.stdout()).isEqualTo(identificationBeforeResult.stdout());
    assertThat(taskAfterResult.stdout()).isEqualTo(taskBeforeResult.stdout());
    JsonNode taskAfter = JSON.readTree(taskAfterResult.stdout());
    assertThat(taskAfter.path("extract").path("response").path("rawResponseBase64").asText())
        .isEqualTo(originalExtractResponse);
    assertThat(taskAfter.path("review").path("response").path("rawResponseBase64").asText())
        .isEqualTo(originalReviewResponse);
    assertThat(reopenedOntologyModule(fixture.runStore(), o1RunId, ONTOLOGY_POLICY_SET).receipt())
        .isEqualTo(historicalO1ReceiptBefore);
    assertThat(Files.readAllBytes(ONTOLOGY_POLICY_SET)).containsExactly(v1PolicyBytes);

    String v2Yaml = Files.readString(v2Configuration.path(), StandardCharsets.UTF_8);
    String v1RegistryBlock =
        "  upstreamArtifactPolicyRegistries:\n    - " + yaml(ONTOLOGY_POLICY_SET) + "\n";
    assertThat(v2Yaml).contains(v1RegistryBlock);
    Path missingRegistryConfig =
        temporaryDirectory.resolve("historical-v1-consumer-missing-owner-policy.yaml");
    Files.writeString(
        missingRegistryConfig, v2Yaml.replace(v1RegistryBlock, ""), StandardCharsets.UTF_8);
    CliResult missingRegistry =
        executeWithFactory(
            missingRegistryConfig,
            providerFactory,
            "relate-ontology",
            "--selection",
            v1RelationSelection.toString());
    assertThat(missingRegistry.exitCode()).isNotZero();
    assertThat(missingRegistry.stdout()).isEmpty();
    assertThat(missingRegistry.stderr()).contains("ONTOLOGY");
    assertThat(providerFactories).hasValue(factoriesBeforeO2);

    String duplicateRegistryYaml =
        v2Yaml.replace(
            v1RegistryBlock, v1RegistryBlock + "    - " + yaml(ONTOLOGY_POLICY_SET) + "\n");
    Path duplicateRegistryConfig =
        temporaryDirectory.resolve("historical-v1-consumer-duplicate-owner-policy.yaml");
    Files.writeString(duplicateRegistryConfig, duplicateRegistryYaml, StandardCharsets.UTF_8);
    CliResult duplicateRegistry =
        executeWithFactory(
            duplicateRegistryConfig,
            providerFactory,
            "relate-ontology",
            "--selection",
            v1RelationSelection.toString());
    assertThat(duplicateRegistry.exitCode()).isNotZero();
    assertThat(duplicateRegistry.stdout()).isEmpty();
    assertThat(duplicateRegistry.stderr()).contains("ONTOLOGY");
    assertThat(providerFactories).hasValue(factoriesBeforeO2);
  }

  @Test
  void v3ProducerRejectsHistoricalV1CorpusBeforeModelProviderDispatch() throws Exception {
    TechnicalFixture fixture = prepareRealR4("v3-producer-historical-v1-corpus-rejection");
    ConfiguredTypedPipeline v1Configuration =
        writeTypedPipelineConfiguration("historical-v1-corpus-owner.yaml", fixture);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        unusedProvider =
            declaration -> {
              throw new AssertionError("O0 preparation must not initialize a model Provider");
            };
    CliResult prepared =
        executeWithFactory(
            v1Configuration.path(),
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    String o0RunId = runId(prepared);
    JsonNode historicalCorpus =
        JSON.readTree(artifact(v1Configuration.path(), o0RunId, "ONTOLOGY_CORPUS").stdout());
    assertThat(historicalCorpus.path("schemaVersion").asText()).isEqualTo("ontology-corpus-v1");
    ReopenedModulePublication historicalO0 = reopenedOntologyModule(fixture.runStore(), o0RunId);
    assertThat(historicalO0.receipt().controls().artifactPolicyRegistryRef())
        .isEqualTo(
            SourceAnalysisExecution.loadPolicies(ONTOLOGY_POLICY_SET, CANONICAL).reference());

    ConfiguredTypedPipeline v3Configuration =
        writeBusinessLinkTypedPipelineConfigurationV3(
            "business-link-v3-historical-corpus-consumer.yaml", fixture);
    String v3Yaml = Files.readString(v3Configuration.path(), StandardCharsets.UTF_8);
    String activeRegistryAndReading =
        "  ontologyPolicyRegistry: " + yaml(ONTOLOGY_POLICY_SET_V3) + "\nreading:";
    String v3WithHistoricalOwner =
        v3Yaml.replace(
            activeRegistryAndReading,
            "  ontologyPolicyRegistry: "
                + yaml(ONTOLOGY_POLICY_SET_V3)
                + "\n  upstreamArtifactPolicyRegistries:\n    - "
                + yaml(ONTOLOGY_POLICY_SET)
                + "\nreading:");
    assertThat(v3WithHistoricalOwner).isNotEqualTo(v3Yaml);
    Files.writeString(v3Configuration.path(), v3WithHistoricalOwner, StandardCharsets.UTF_8);

    String entryRef = entryRef(historicalCorpus);
    String unitRef = unitRef(historicalCorpus, "JAVA_METHOD", "method:neutral-list");
    Path modelScope =
        writeSingleUnitObjectScope(
            temporaryDirectory.resolve("v3-historical-v1-model-scope.json"),
            "Q1",
            "historical-model-object",
            entryRef,
            unitRef,
            "MODEL");
    AtomicInteger providerFactories = new AtomicInteger();
    AtomicInteger providerCalls = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return request -> {
                providerCalls.incrementAndGet();
                throw new IllegalStateException("HISTORICAL_CORPUS_REACHED_PROVIDER");
              };
            };

    CliResult refused =
        executeWithFactory(
            v3Configuration.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            modelScope.toString());

    assertThat(refused.stderr())
        .withFailMessage(cliDiagnostics(refused))
        .contains("ONTOLOGY_CORPUS_VERSION_INVALID");
    assertThat(refused.exitCode()).isNotZero();
    assertThat(refused.stdout()).isEmpty();
    assertThat(providerFactories).hasValue(0);
    assertThat(providerCalls).hasValue(0);
  }

  @Test
  void providerReportedInvalidJsonIsLocalToItsObjectTask() throws Exception {
    assertMalformedObjectOutputIsTaskLocal("provider-reported-invalid-json", true);
  }

  @Test
  void malformedRawStructuredResponseIsSavedAndOnlyRejectsItsObjectTask() throws Exception {
    assertMalformedObjectOutputIsTaskLocal("raw-structured-invalid-json", false);
  }

  private void assertMalformedObjectOutputIsTaskLocal(String fixtureName, boolean providerFailure)
      throws Exception {
    TechnicalFixture fixture = prepareRealR4(fixtureName);
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-" + fixtureName + "-v2.yaml", fixture);
    byte[] malformedBytes =
        "{\"private\":\"MALFORMED_OUTPUT_SENTINEL\"".getBytes(StandardCharsets.UTF_8);
    ImmutableBytes malformedResponse = ImmutableBytes.copyOf(malformedBytes);
    String malformedText = new String(malformedBytes, StandardCharsets.UTF_8);
    List<StructuredModelRequest> requests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String questionId = input.path("questionId").asText();
                boolean review = request.taskKind().contains("REVIEW");
                String schemaVersion =
                    review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3";
                if ("Q1".equals(questionId)) {
                  if (providerFailure) {
                    throw new StructuredModelProviderFailure(
                        "INVALID_JSON",
                        true,
                        true,
                        "CODEX_SUBSCRIPTION_RESPONSE_INVALID_JSON",
                        null,
                        malformedResponse);
                  }
                  return new StructuredModelResponse(malformedResponse, identity);
                }
                return new StructuredModelResponse(
                    formalTaskObjectResponse(schemaVersion, questionId, "E1"), identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    Path scope =
        writeFormalTaskScope(
            temporaryDirectory.resolve(fixtureName + "-scope.json"),
            new ScopedQuestion("Q1", List.of(new ScopedTask("bad-json-object", "OBJECT"))),
            new ScopedQuestion("Q2", List.of(new ScopedTask("independent-object", "OBJECT"))));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isIn(0, 2);
    assertThat(identified.stdout()).isNotBlank().doesNotContain(malformedText);
    assertThat(identified.stderr()).doesNotContain(malformedText);
    String o1RunId = runId(identified);

    assertThat(requests)
        .extracting(
            request -> {
              JsonNode input = ExplicitTypedPipelineScript.input(request);
              return input.path("questionId").asText()
                  + "/"
                  + input.path("taskKind").asText()
                  + "/"
                  + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT");
            })
        .containsExactly("Q1/OBJECT/EXTRACT", "Q2/OBJECT/EXTRACT", "Q2/OBJECT/REVIEW");

    CliResult inspectResult = executePublic(configured.path(), "inspect", "--run", o1RunId);
    assertThat(inspectResult.exitCode()).withFailMessage(inspectResult.stderr()).isZero();
    JsonNode inspect = JSON.readTree(inspectResult.stdout());
    assertThat(inspect.path("runId").asText()).isEqualTo(o1RunId);
    assertThat(inspect.path("resultStatus").asText()).isEqualTo("PARTIAL");
    CliResult identificationResult =
        artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identificationResult);
    JsonNode identification = JSON.readTree(identificationResult.stdout());
    List<JsonNode> outcomes = arrayValues(identification.path("taskOutcomes"));
    assertThat(outcomes)
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly("bad-json-object/REJECTED", "independent-object/REVIEWED");
    JsonNode badOutcome = outcomes.get(0);
    assertThat(badOutcome.path("reason").path("category").asText()).isEqualTo("MODEL_OUTPUT");
    JsonNode goodOutcome = outcomes.get(1);
    assertThat(goodOutcome.path("producingTaskId").asText()).isNotBlank();
    assertThat(goodOutcome.path("jobKey").asText()).isNotBlank();

    CliResult badTaskResult =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            badOutcome.path("producingTaskId").asText(),
            "--max-bytes",
            "524288");
    assertArtifactAvailable(badTaskResult);
    JsonNode badTask = JSON.readTree(badTaskResult.stdout());
    assertThat(badTask.path("status").asText()).isEqualTo("REJECTED");
    assertThat(badTask.path("extract").path("request").isObject()).isTrue();
    JsonNode review = badTask.path("review");
    assertThat(review.path("request").isNull() || review.path("request").isMissingNode()).isTrue();
    assertThat(review.path("response").isNull() || review.path("response").isMissingNode())
        .isTrue();
    if (providerFailure) {
      assertThat(badTask.path("extract").path("response").isNull()).isTrue();
      JsonNode providerOutcome = badTask.path("extract").path("outcome");
      assertThat(providerOutcome.path("reasonCode").asText()).isEqualTo("INVALID_JSON");
      assertThat(providerOutcome.path("requestStarted").asBoolean()).isTrue();
      assertThat(providerOutcome.path("requestEnded").asBoolean()).isTrue();
      assertThat(providerOutcome.path("privateFailureOutputStored").asBoolean()).isTrue();
      assertThat(badTask.path("failure").path("failureCode").asText()).isEqualTo("INVALID_JSON");
    } else {
      String expectedRaw = Base64.getEncoder().encodeToString(malformedBytes);
      assertThat(badTask.path("extract").path("response").path("rawResponseBase64").asText())
          .isEqualTo(expectedRaw);
      assertThat(badTask.path("extract").path("validation").isNull()).isTrue();
      assertThat(badTask.path("extract").path("outcome").isNull()).isTrue();
    }

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      var inspection = new LocalRepositoryAnalysisAgent(store).inspect(o1RunId);
      assertThat(inspection.output().ontologyOutput().status())
          .isEqualTo(OntologyRunOutput.Status.PARTIAL);
    }
  }

  @Test
  void oversizedCompleteUnitIsLocalAndSmallerIndependentObjectStillRuns() throws Exception {
    TechnicalFixture fixture = prepareRealR4("formal-material-unit-isolation");
    OntologyEvidenceCorpus corpus = reopenedPreparedOntologyEvidenceCorpus(fixture);
    String entryId = corpus.navigation(0, 1).entries().get(0).entryId();
    String entryRef = corpus.aliases().entryRef(entryId);
    JsonNode aliases = JSON.readTree(corpus.aliases().canonicalMapping().copyToByteArray());
    List<MeasuredUnit> measuredUnits = new ArrayList<>();
    for (JsonNode unit : aliases.path("units")) {
      for (JsonNode use : unit.path("uses")) {
        if (entryId.equals(use.path("entryId").asText())) {
          OntologyEvidenceCorpus.UnitHandle handle =
              new OntologyEvidenceCorpus.UnitHandle(
                  entryId,
                  UnitKind.valueOf(use.path("kind").asText()),
                  use.path("originalId").asText());
          measuredUnits.add(
              new MeasuredUnit(unit.path("ref").asText(), corpus.unitMetadata(handle).unitBytes()));
          break;
        }
      }
    }
    MeasuredUnit smallUnit =
        measuredUnits.stream().min(Comparator.comparingInt(MeasuredUnit::unitBytes)).orElseThrow();
    MeasuredUnit largeUnit =
        measuredUnits.stream().max(Comparator.comparingInt(MeasuredUnit::unitBytes)).orElseThrow();
    assertThat(smallUnit.unitRef()).isNotEqualTo(largeUnit.unitRef());
    assertThat(largeUnit.unitBytes()).isGreaterThan(smallUnit.unitBytes());

    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("formal-material-unit-isolation-v2.yaml", fixture);
    String configurationText = Files.readString(configured.path(), StandardCharsets.UTF_8);
    String boundedConfiguration =
        configurationText.replace(
            "maxUnitBytes: 1048576", "maxUnitBytes: " + smallUnit.unitBytes());
    assertThat(boundedConfiguration).isNotEqualTo(configurationText);
    Files.writeString(configured.path(), boundedConfiguration, StandardCharsets.UTF_8);

    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        unusedProvider =
            declaration -> {
              throw new AssertionError("O0 preparation must not initialize a model Provider");
            };
    CliResult prepared =
        executeWithFactory(
            configured.path(),
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode())
        .withFailMessage(
            "v2 O0 prepare failed. stdout:\n%s\nstderr:\n%s", prepared.stdout(), prepared.stderr())
        .isZero();
    String o0RunId = runId(prepared);
    CliResult preparedCorpusResult = artifact(configured.path(), o0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(preparedCorpusResult);
    JsonNode preparedCorpus = JSON.readTree(preparedCorpusResult.stdout());
    assertThat(preparedCorpus.path("projectionRuleVersion").asText())
        .isEqualTo("ontology-model-projection-v2");
    assertThat(preparedCorpus.path("contentSourceIdentity").asText())
        .isEqualTo(corpus.sourceIdentity());
    assertThat(preparedCorpus.path("aliases"))
        .isEqualTo(JSON.readTree(corpus.aliases().canonicalMapping().copyToByteArray()));

    List<StructuredModelRequest> requests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String questionId = input.path("questionId").asText();
                String schemaVersion =
                    request.taskKind().contains("REVIEW")
                        ? "ontology-typed-review-v3"
                        : "ontology-typed-candidate-v3";
                return new StructuredModelResponse(
                    formalTaskObjectResponse(schemaVersion, questionId, entryRef), identity);
              };
            };
    Path scope =
        writeMeasuredObjectTaskScope(
            temporaryDirectory.resolve("formal-material-unit-isolation-scope.json"),
            entryRef,
            largeUnit.unitRef(),
            smallUnit.unitRef());
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isIn(0, 2);
    assertThat(identified.stdout()).isNotBlank();
    String o1RunId = runId(identified);

    CliResult identificationResult =
        artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identificationResult);
    JsonNode identification = JSON.readTree(identificationResult.stdout());
    assertThat(identification.path("schemaVersion").asText())
        .isEqualTo("ontology-identification-v2");
    List<JsonNode> outcomes = arrayValues(identification.path("taskOutcomes"));
    assertThat(outcomes)
        .withFailMessage(
            "fresh-v2 capacity outcomes=%s; measured complete units=%s; saved O0 aliases=%s",
            outcomes, measuredUnits, preparedCorpus.path("aliases"))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly("large-object/UNPROCESSED", "small-object/REVIEWED");
    assertThat(outcomes.get(0).path("producingTaskId").isNull()).isTrue();
    assertThat(outcomes.get(0).path("reason").path("category").asText()).isEqualTo("MATERIAL");
    assertThat(outcomes.get(0).path("reason").path("code").asText())
        .isEqualTo("ONTOLOGY_UNIT_TOO_LARGE");
    assertThat(outcomes.get(1).path("producingTaskId").asText()).isNotBlank();
    assertThat(arrayValues(identification.path("taskRecords")))
        .extracting(item -> item.path("taskId").asText())
        .containsExactly("small-object");
    JsonNode inspect =
        JSON.readTree(executePublic(configured.path(), "inspect", "--run", o1RunId).stdout());
    assertThat(inspect.path("resultStatus").asText()).isEqualTo("PARTIAL");
    assertThat(arrayValues(inspect.path("problems")))
        .singleElement()
        .satisfies(
            problem -> {
              assertThat(problem.path("taskId").asText()).isEqualTo("large-object");
              assertThat(problem.path("code").asText()).isEqualTo("ONTOLOGY_UNIT_TOO_LARGE");
              assertThat(problem.path("category").asText()).isEqualTo("MATERIAL");
            });
    List<String> actualRequests =
        requests.stream()
            .map(
                request -> {
                  JsonNode input = ExplicitTypedPipelineScript.input(request);
                  return input.path("questionId").asText()
                      + "/"
                      + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT");
                })
            .toList();
    assertThat(actualRequests)
        .withFailMessage(
            "capacity-task outcomes=%s; actual model phases=%s",
            identification.path("taskOutcomes"), actualRequests)
        .containsExactly("Q2/EXTRACT", "Q2/REVIEW");
  }

  @Test
  void technicalR2ReaderRetainsItsOriginal256MiBArtifactBudget() {
    assertThat(TechnicalAnalysisConfiguredRuntime.technicalPublicationStoreLimits())
        .isEqualTo(new ArtifactStoreLimits(64, 256L * 1024L * 1024L, 512L * 1024L * 1024L, 4_096));
  }

  @Test
  void brokenR2AfterO0AdmissionLeavesInspectableSourceFailureWithoutCorpusOrProvider()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("v2-o0-broken-saved-r2");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("v2-o0-broken-saved-r2.yaml", fixture);
    AnalysisStepPublicationReference r2Publication = savedR2Publication(fixture);
    Path brokenPayload;
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      CanonicalArtifactPolicyRegistry technicalPolicies =
          SourceAnalysisExecution.loadPolicies(TECHNICAL_POLICY_SET, CANONICAL);
      ReopenedAnalysisStepPublication r2Step =
          new FileSystemCanonicalAnalysisStepArtifactStore(
                  store,
                  CANONICAL,
                  technicalPolicies,
                  TechnicalAnalysisConfiguredRuntime.technicalPublicationStoreLimits())
              .reopen(r2Publication);
      VerifiedCanonicalPayload savedIndex =
          r2Step.semanticPayloads().stream()
              .filter(
                  payload ->
                      JavaCodeIndexPublicationSpecifier.ARTIFACT_TYPE.equals(
                          payload.descriptor().artifactType()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("R2 Java index payload is absent"));
      brokenPayload =
          technicalStepDirectory(fixture.runStore(), r2Publication)
              .resolve(savedIndex.descriptor().fileName());
      assertThat(Files.isRegularFile(brokenPayload)).isTrue();
      assertThat(Files.size(brokenPayload)).isEqualTo(savedIndex.descriptor().sizeBytes());
      assertThat(Files.deleteIfExists(brokenPayload)).isTrue();
    }

    AtomicInteger providerFactories = new AtomicInteger();
    CliResult prepared =
        executeWithFactory(
            configured.path(),
            declaration -> {
              providerFactories.incrementAndGet();
              throw new AssertionError("O0 source failure must not initialize a model Provider");
            },
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode())
        .withFailMessage(
            "queued O0 source failure must remain queryable. stdout:\n%s\nstderr:\n%s",
            prepared.stdout(), prepared.stderr())
        .isEqualTo(2);
    assertThat(Files.exists(brokenPayload)).isFalse();
    String o0RunId = runId(prepared);

    CliResult inspected = executePublic(configured.path(), "inspect", "--run", o0RunId);
    assertThat(inspected.exitCode()).withFailMessage(inspected.stderr()).isZero();
    JsonNode observation = JSON.readTree(inspected.stdout());
    assertThat(observation.path("runId").asText()).isEqualTo(o0RunId);
    assertThat(observation.path("operation").asText()).isEqualTo("PREPARE_ONTOLOGY");
    assertThat(observation.path("lifecycleState").asText()).isEqualTo("FAILED");
    assertThat(arrayValues(observation.path("availableArtifactKeys"))).isEmpty();
    assertThat(observation.path("modelRequestsDispatched").asInt(-1)).isZero();
    assertThat(arrayValues(observation.path("problems")))
        .singleElement()
        .satisfies(
            problem -> {
              assertThat(problem.path("code").asText())
                  .isEqualTo("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
              assertThat(problem.path("category").asText()).isEqualTo("SOURCE");
              assertThat(problem.path("stage").asText()).isEqualTo("PREPARE");
            });
    CliResult absentCorpus = artifact(configured.path(), o0RunId, "ONTOLOGY_CORPUS");
    assertThat(absentCorpus.exitCode()).isNotZero();
    assertThat(absentCorpus.stdout()).isEmpty();
    assertThat(providerFactories).hasValue(0);
  }

  @Test
  void sourceIdentityMismatchStopsBeforeAnyFormalTaskOrPublication() throws Exception {
    TechnicalFixture fixture = prepareRealR4("formal-source-identity-shared-stop");
    Path o0Configuration =
        writeOntologyConfig(
            "formal-source-identity-o0-v1.yaml", fixture, fixture.archive(), null, false);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        unusedProvider =
            declaration -> {
              throw new AssertionError("O0 preparation must not initialize a model Provider");
            };
    CliResult prepared =
        executeWithFactory(
            o0Configuration,
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    CliResult originalCorpus = artifact(o0Configuration, o0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(originalCorpus);

    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("formal-source-identity-o1-v2.yaml", fixture);
    Path wrongArchive =
        Files.createDirectory(temporaryDirectory.resolve("formal-source-identity-wrong-archive"));
    String original = Files.readString(configured.path(), StandardCharsets.UTF_8);
    String wrongSource = original.replace(yaml(fixture.archive()), yaml(wrongArchive));
    assertThat(wrongSource).isNotEqualTo(original);
    Files.writeString(configured.path(), wrongSource, StandardCharsets.UTF_8);
    AtomicInteger providerFactories = new AtomicInteger();
    Path scope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("formal-source-identity-scope.json"),
            new ScopedQuestion("Q1", List.of(new ScopedTask("first-object", "OBJECT"))),
            new ScopedQuestion("Q2", List.of(new ScopedTask("second-object", "OBJECT"))));
    CliResult refused =
        executeWithFactory(
            configured.path(),
            declaration -> {
              providerFactories.incrementAndGet();
              throw new AssertionError(
                  "source identity mismatch must stop before Provider creation");
            },
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(refused.exitCode()).isNotZero();
    assertThat(refused.stdout()).isEmpty();
    assertThat(refused.stderr()).contains("ONTOLOGY");
    assertThat(providerFactories).hasValue(0);
  }

  @Test
  void privateJournalWriteFailureStopsTheBindingAndLeavesEveryTaskOutcomeQueryable()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("formal-storage-shared-stop");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("formal-storage-shared-stop-v2.yaml", fixture);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        unusedProvider =
            declaration -> {
              throw new AssertionError("O0 preparation must not initialize a model Provider");
            };
    CliResult prepared =
        executeWithFactory(
            configured.path(),
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    List<StructuredModelRequest> requests = new ArrayList<>();
    AtomicReference<BlockedAttempt> blockedAttempt = new AtomicReference<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String questionId = input.path("questionId").asText();
                if ("Q1".equals(questionId) && request.taskKind().contains("EXTRACT")) {
                  try {
                    blockedAttempt.set(blockFormalResponseAttempt(fixture.runStore(), questionId));
                  } catch (Exception setupFailure) {
                    throw new AssertionError(
                        "could not obstruct Q1's private response journal", setupFailure);
                  }
                }
                String schemaVersion =
                    request.taskKind().contains("REVIEW")
                        ? "ontology-typed-review-v3"
                        : "ontology-typed-candidate-v3";
                return new StructuredModelResponse(
                    formalTaskObjectResponse(schemaVersion, questionId, "E1"), identity);
              };
            };
    Path scope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("formal-storage-shared-stop-scope.json"),
            new ScopedQuestion("Q0", List.of(new ScopedTask("earlier-reviewed-object", "OBJECT"))),
            new ScopedQuestion("Q1", List.of(new ScopedTask("storage-failing-object", "OBJECT"))),
            new ScopedQuestion("Q2", List.of(new ScopedTask("must-not-dispatch", "OBJECT"))));
    CliResult identified;
    try {
      identified =
          executeWithFactory(
              configured.path(),
              providerFactory,
              "identify-ontology",
              "--corpus-run",
              o0RunId,
              "--scope",
              scope.toString());
    } finally {
      restoreFormalResponseAttempt(blockedAttempt.get());
    }
    assertThat(identified.exitCode()).isNotZero();
    assertThat(identified.stdout()).isNotBlank();
    String o1RunId = runId(identified);
    assertThat(requests)
        .extracting(
            request ->
                ExplicitTypedPipelineScript.input(request).path("questionId").asText()
                    + "/"
                    + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT"))
        .containsExactly("Q0/EXTRACT", "Q0/REVIEW", "Q1/EXTRACT");

    JsonNode inspection =
        JSON.readTree(executePublic(configured.path(), "inspect", "--run", o1RunId).stdout());
    assertThat(inspection.path("lifecycleState").asText()).isEqualTo("FAILED");
    assertThat(arrayValues(inspection.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly(
            "earlier-reviewed-object/REVIEWED",
            "storage-failing-object/UNPROCESSED",
            "must-not-dispatch/UNPROCESSED");
    List<JsonNode> outcomes = arrayValues(inspection.path("taskOutcomes"));
    assertThat(outcomes.get(1).path("reason").path("category").asText()).isEqualTo("STORAGE");
    assertThat(outcomes.get(2).path("reason")).isEqualTo(outcomes.get(1).path("reason"));
    assertThat(outcomes.get(1).path("producingTaskId").asText()).isNotBlank();
    assertThat(outcomes.get(2).path("producingTaskId").isNull()).isTrue();
    assertThat(arrayValues(inspection.path("nextActions")))
        .extracting(action -> action.path("kind").asText())
        .doesNotContain("PUBLISH_REVIEWED_PART");
    JsonNode queryAction =
        arrayValues(inspection.path("nextActions")).stream()
            .filter(action -> "QUERY_TASK".equals(action.path("kind").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(arrayValues(queryAction.path("taskRefs")))
        .extracting(
            reference ->
                reference.path("runId").asText()
                    + "/"
                    + reference.path("questionId").asText()
                    + "/"
                    + reference.path("taskId").asText())
        .containsExactly(
            o1RunId + "/Q0/earlier-reviewed-object", o1RunId + "/Q1/storage-failing-object");
    CliResult identificationResult =
        artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identificationResult);
    JsonNode identification = JSON.readTree(identificationResult.stdout());
    assertThat(arrayValues(identification.path("taskRecords")))
        .extracting(item -> item.path("taskId").asText())
        .containsExactly("earlier-reviewed-object", "storage-failing-object");
    CliResult taskObservationResult =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            outcomes.get(1).path("producingTaskId").asText(),
            "--max-bytes",
            "524288");
    assertArtifactAvailable(taskObservationResult);
    JsonNode taskObservation = JSON.readTree(taskObservationResult.stdout());
    assertThat(
            taskObservation.path("extract").path("response").isNull()
                || taskObservation.path("extract").path("response").isMissingNode())
        .isTrue();
    assertThat(
            taskObservation.path("extract").path("validation").isNull()
                || taskObservation.path("extract").path("validation").isMissingNode())
        .isTrue();
    assertThat(
            taskObservation.path("review").path("request").isNull()
                || taskObservation.path("review").path("request").isMissingNode())
        .isTrue();
    assertThat(
            taskObservation.path("review").path("response").isNull()
                || taskObservation.path("review").path("response").isMissingNode())
        .isTrue();
  }

  @Test
  void codexAdapterOutputBudgetFailureRejectsOnlyItsObjectAndDoesNotRetry() throws Exception {
    TechnicalFixture fixture = prepareRealR4("formal-provider-output-budget-local");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("formal-provider-output-budget-local-v2.yaml", fixture);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        unusedProvider =
            declaration -> {
              throw new AssertionError("O0 preparation must not initialize a model Provider");
            };
    CliResult prepared =
        executeWithFactory(
            configured.path(),
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);

    List<String> commandPrompts = new ArrayList<>();
    byte[] oversizedResponse = new byte[100_001];
    java.util.Arrays.fill(oversizedResponse, (byte) 'x');
    AtomicInteger commandCalls = new AtomicInteger();
    CodexSubscriptionCommand command =
        (profile, prompt, schema) -> {
          commandPrompts.add(prompt);
          int ordinal = commandCalls.incrementAndGet();
          if (ordinal == 1) {
            return ImmutableBytes.copyOf(oversizedResponse);
          }
          JsonNode input = providerInputFromPrompt(prompt);
          assertThat(input.path("questionId").asText()).isEqualTo("Q2");
          String schemaVersion =
              ordinal == 2 ? "ontology-typed-candidate-v3" : "ontology-typed-review-v3";
          if (ordinal > 3) {
            throw new AssertionError("only Q2 EXTRACT and REVIEW may follow the oversized Q1");
          }
          return formalTaskObjectResponse(schemaVersion, "Q2", "E1");
        };
    CodexSubscriptionProfile profile =
        new CodexSubscriptionProfile(
            temporaryDirectory.resolve("unused-codex-command"),
            "gpt-5.6-luna",
            "high",
            Duration.ofSeconds(2));
    AtomicInteger providerFactories = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              assertThat(declaration.expectedRuntimeIdentity().upstreamProvider())
                  .isEqualTo("codex_subscription");
              return new CodexSubscriptionStructuredProvider(profile, command);
            };
    Path scope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("formal-provider-output-budget-scope.json"),
            new ScopedQuestion("Q1", List.of(new ScopedTask("oversized-object", "OBJECT"))),
            new ScopedQuestion("Q2", List.of(new ScopedTask("later-object", "OBJECT"))));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isIn(0, 2);
    assertThat(identified.stdout()).isNotBlank();
    String o1RunId = runId(identified);
    assertThat(commandCalls).hasValue(3);
    assertThat(commandPrompts).hasSize(3);
    assertThat(providerFactories).hasValue(2);
    assertThat(providerInputFromPrompt(commandPrompts.get(0)).path("questionId").asText())
        .isEqualTo("Q1");
    assertThat(providerInputFromPrompt(commandPrompts.get(1)).path("questionId").asText())
        .isEqualTo("Q2");
    assertThat(providerInputFromPrompt(commandPrompts.get(2)).path("questionId").asText())
        .isEqualTo("Q2");

    JsonNode identification =
        JSON.readTree(artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(arrayValues(identification.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText() + "/" + item.path("status").asText())
        .containsExactly("oversized-object/REJECTED", "later-object/REVIEWED");
    JsonNode oversizedOutcome = arrayValues(identification.path("taskOutcomes")).get(0);
    assertThat(oversizedOutcome.path("reason").path("code").asText())
        .isEqualTo("RESPONSE_BUDGET_EXCEEDED");
    assertThat(oversizedOutcome.path("reason").path("category").asText()).isEqualTo("MODEL_OUTPUT");
    CliResult oversizedObservation =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            oversizedOutcome.path("producingTaskId").asText(),
            "--max-bytes",
            "524288");
    assertArtifactAvailable(oversizedObservation);
    JsonNode oversizedTask = JSON.readTree(oversizedObservation.stdout());
    assertThat(oversizedTask.path("extract").path("outcome").path("reasonCode").asText())
        .isEqualTo("RESPONSE_BUDGET_EXCEEDED");
    assertThat(oversizedTask.path("extract").path("outcome").path("requestStarted").asBoolean())
        .isTrue();
    assertThat(oversizedTask.path("extract").path("outcome").path("requestEnded").asBoolean())
        .isTrue();
    assertThat(
            oversizedTask
                .path("extract")
                .path("outcome")
                .path("privateFailureOutputStored")
                .asBoolean())
        .isTrue();
    assertThat(
            oversizedTask.path("extract").path("response").isNull()
                || oversizedTask.path("extract").path("response").isMissingNode())
        .isTrue();
    assertThat(
            oversizedTask.path("review").path("request").isNull()
                || oversizedTask.path("review").path("request").isMissingNode())
        .isTrue();
    assertThat(oversizedObservation.stdout())
        .doesNotContain(new String(oversizedResponse, StandardCharsets.UTF_8));

    String jobKey = oversizedTask.path("jobKey").asText();
    assertThat(jobKey).isNotBlank();
    Path privateOutcomePath =
        fixture
            .runStore()
            .resolve("ontology-journal")
            .resolve("model-jobs")
            .resolve(o1RunId.substring("analysis-run:".length()))
            .resolve("ontology")
            .resolve(jobKey)
            .resolve("formal-typed-extract")
            .resolve("attempt-1")
            .resolve("outcome.json");
    JsonNode privateOutcome = JSON.readTree(Files.readAllBytes(privateOutcomePath));
    assertThat(privateOutcome.path("reasonCode").asText()).isEqualTo("RESPONSE_BUDGET_EXCEEDED");
    assertThat(privateOutcome.path("rawResponseBase64").asText())
        .isEqualTo(Base64.getEncoder().encodeToString(oversizedResponse));
    assertThat(privateOutcome.has("providerDiagnosticBase64")).isFalse();
  }

  @Test
  void locallyEndedProviderTimeoutKeepsRemoteOutcomeUnknownAndBlocksTheSharedBinding()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("provider-timeout-shared-binding");
    Path o0Configuration =
        writeOntologyConfig(
            "ontology-provider-timeout-o0.yaml", fixture, fixture.archive(), null, false);
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-provider-timeout-o1-v2.yaml", fixture);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        unusedProvider =
            declaration -> {
              throw new AssertionError("O0 preparation must not initialize a model Provider");
            };
    CliResult prepared =
        executeWithFactory(
            o0Configuration,
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);

    List<StructuredModelRequest> requests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration ->
                request -> {
                  requests.add(request);
                  throw new StructuredModelProviderFailure(
                      "REQUEST_TIMEOUT", true, true, "CODEX_SUBSCRIPTION_TIMEOUT", null);
                };
    Path scope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("provider-timeout-scope.json"),
            new ScopedQuestion("Q1", List.of(new ScopedTask("timeout-object", "OBJECT"))),
            new ScopedQuestion("Q2", List.of(new ScopedTask("later-object", "OBJECT"))));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).isNotZero();
    assertThat(identified.stdout()).isNotBlank();
    assertThat(identified.stdout()).doesNotContain("REMOTE_CANCELLED", "REMOTE_CANCELED");
    String o1RunId = runId(identified);
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).taskKind()).contains("OBJECT", "EXTRACT");

    CliResult inspectionResult = executePublic(configured.path(), "inspect", "--run", o1RunId);
    assertThat(inspectionResult.exitCode()).withFailMessage(inspectionResult.stderr()).isZero();
    JsonNode inspection = JSON.readTree(inspectionResult.stdout());
    assertThat(inspection.path("schemaVersion").asText())
        .isEqualTo("ontology-operation-observation-v2");
    assertThat(inspection.path("lifecycleState").asText()).isEqualTo("FAILED");
    assertThat(arrayValues(inspection.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText())
        .containsExactly("timeout-object", "later-object");
    JsonNode laterTaskOutcome =
        arrayValues(inspection.path("taskOutcomes")).stream()
            .filter(item -> "later-object".equals(item.path("taskId").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(laterTaskOutcome.path("status").asText()).isEqualTo("UNPROCESSED");
    assertThat(laterTaskOutcome.path("producingTaskId").isNull()).isTrue();
    JsonNode counts = inspection.path("modelRequestCounts");
    assertThat(counts.path("reservedAttempts").asInt()).isEqualTo(1);
    assertThat(counts.path("confirmedStarted").asInt()).isEqualTo(1);
    assertThat(counts.path("confirmedEnded").asInt()).isEqualTo(1);
    assertThat(counts.path("outcomeUnknown").asInt()).isEqualTo(1);

    JsonNode identification =
        JSON.readTree(artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    JsonNode attemptedTask = findTaskRecord(identification, "timeout-object");
    String producingTaskId = attemptedTask.path("producingTaskId").asText();
    assertThat(producingTaskId).isNotBlank();
    CliResult taskObservationResult =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            producingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(taskObservationResult);
    JsonNode taskObservation = JSON.readTree(taskObservationResult.stdout());
    assertThat(taskObservation.path("schemaVersion").asText())
        .isEqualTo("ontology-task-observation-v2");
    JsonNode extractOutcome = taskObservation.path("extract").path("outcome");
    assertThat(extractOutcome.path("requestStarted").asBoolean()).isTrue();
    assertThat(extractOutcome.path("requestEnded").asBoolean()).isTrue();
    assertThat(extractOutcome.path("localProcessState").asText()).isEqualTo("EXITED");
    assertThat(extractOutcome.path("remoteOutcome").asText()).isEqualTo("UNKNOWN");
    assertThat(taskObservationResult.stdout())
        .doesNotContain("REMOTE_CANCELLED", "REMOTE_CANCELED", "REMOTE_TERMINATED");
  }

  @Test
  void providerPreflightFailureIsReservedButNotCountedAsConfirmedStarted() throws Exception {
    TechnicalFixture fixture = prepareRealR4("provider-preflight-unstarted");
    Path o0Configuration =
        writeOntologyConfig(
            "ontology-provider-preflight-o0.yaml", fixture, fixture.archive(), null, false);
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-provider-preflight-o1-v2.yaml", fixture);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        unusedProvider =
            declaration -> {
              throw new AssertionError("O0 preparation must not initialize a model Provider");
            };
    CliResult prepared =
        executeWithFactory(
            o0Configuration,
            unusedProvider,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);

    Path executable = temporaryDirectory.resolve("fake-codex-preflight-api-login");
    Path processCalls = temporaryDirectory.resolve("preflight-process-calls.txt");
    String quotedProcessCalls = "'" + processCalls.toString().replace("'", "'\\''") + "'";
    Files.writeString(
        executable,
        "#!/bin/sh\nprintf '%s\\n' \"$1\" >> "
            + quotedProcessCalls
            + "\n"
            + "echo 'Logged in using an API key'\nexit 0\n",
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }
    CodexSubscriptionProfile profile =
        new CodexSubscriptionProfile(executable, "gpt-5.6-terra", "xhigh", Duration.ofSeconds(2));
    AtomicInteger providerFactories = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return new CodexSubscriptionStructuredProvider(profile);
            };
    Path scope =
        writeFormalTaskScope(
            temporaryDirectory.resolve("provider-preflight-scope.json"),
            new ScopedQuestion("Q1", List.of(new ScopedTask("first-object", "OBJECT"))),
            new ScopedQuestion("Q2", List.of(new ScopedTask("second-object", "OBJECT"))));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).isNotZero();
    String o1RunId = runId(identified);
    assertThat(providerFactories).hasValue(1);
    assertThat(Files.readAllLines(processCalls, StandardCharsets.UTF_8)).containsExactly("login");

    CliResult inspectionResult = executePublic(configured.path(), "inspect", "--run", o1RunId);
    assertThat(inspectionResult.exitCode()).withFailMessage(inspectionResult.stderr()).isZero();
    JsonNode inspection = JSON.readTree(inspectionResult.stdout());
    assertThat(inspection.path("schemaVersion").asText())
        .isEqualTo("ontology-operation-observation-v2");
    assertThat(inspection.path("lifecycleState").asText()).isEqualTo("FAILED");
    assertThat(arrayValues(inspection.path("taskOutcomes")))
        .extracting(item -> item.path("taskId").asText())
        .containsExactly("first-object", "second-object");
    JsonNode counts = inspection.path("modelRequestCounts");
    assertThat(counts.path("reservedAttempts").asInt()).isEqualTo(1);
    assertThat(counts.path("confirmedStarted").asInt()).isZero();
    assertThat(counts.path("confirmedEnded").asInt()).isZero();
    assertThat(counts.path("outcomeUnknown").asInt()).isZero();
  }

  @Test
  void savedCorpusIdentitySurvivesLaterConfigurationAndRejectsForeignO1BeforeDispatch()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("corpus-identity-ancestry");
    Path firstO0Config =
        writeOntologyConfig(
            "ontology-corpus-identity-first.yaml", fixture, fixture.archive(), null, false);
    Path changedO0Config =
        writeOntologyConfig(
            "ontology-corpus-identity-controls.yaml", fixture, fixture.archive(), null, false);
    Files.writeString(
        changedO0Config,
        Files.readString(changedO0Config, StandardCharsets.UTF_8)
            .replace("maxNavigationEntries: 8", "maxNavigationEntries: 7"),
        StandardCharsets.UTF_8);
    AtomicInteger providerFactories = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              throw new AssertionError("O0 must not initialize a model Provider");
            };

    CliResult firstPrepared =
        executeWithFactory(
            firstO0Config,
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    CliResult secondPrepared =
        executeWithFactory(
            changedO0Config,
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(firstPrepared.exitCode()).withFailMessage(firstPrepared.stderr()).isZero();
    assertThat(secondPrepared.exitCode()).withFailMessage(secondPrepared.stderr()).isZero();
    String firstO0RunId = runId(firstPrepared);
    String secondO0RunId = runId(secondPrepared);
    CliResult firstCorpusResult = artifact(firstO0Config, firstO0RunId, "ONTOLOGY_CORPUS");
    CliResult secondCorpusResult = artifact(changedO0Config, secondO0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(firstCorpusResult);
    assertArtifactAvailable(secondCorpusResult);
    JsonNode firstCorpus = JSON.readTree(firstCorpusResult.stdout());
    JsonNode secondCorpus = JSON.readTree(secondCorpusResult.stdout());
    String savedCorpusIdentity = firstCorpus.path("corpusIdentity").asText();
    assertThat(savedCorpusIdentity).isNotBlank();
    assertThat(secondCorpus.path("corpusIdentity").asText())
        .isNotBlank()
        .isNotEqualTo(savedCorpusIdentity);
    assertThat(firstCorpus.path("contentSourceIdentity").asText())
        .isNotBlank()
        .isEqualTo(secondCorpus.path("contentSourceIdentity").asText());
    assertThat(firstCorpus.path("evidenceRunId").asText()).isEqualTo(fixture.r4RunId());
    assertCorpusPreparationWire(firstCorpus, fixture, firstO0RunId, 8);
    assertCorpusPreparationWire(secondCorpus, fixture, secondO0RunId, 7);
    assertThat(providerFactories).hasValue(0);

    ReopenedModulePublication r4Module = reopenedEvidenceModule(fixture);
    ReopenedModulePublication firstO0Module =
        reopenedOntologyModule(fixture.runStore(), firstO0RunId);
    List<ArtifactReference> exactR4Payloads = modulePayloadReferences(r4Module);
    assertThat(firstO0Module.receipt().upstreamArtifacts()).containsAll(exactR4Payloads);
    ArtifactReference savedCorpusReference =
        modulePayloadReference(firstO0Module, "ONTOLOGY_CORPUS");

    ConfiguredTypedPipeline typed =
        writeTypedPipelineConfiguration("ontology-corpus-identity-later-stage.yaml", fixture);
    String changedPrompt =
        "Neutral later-stage OBJECT prompt, deliberately distinct from O0 config.";
    Map<String, String> changedPromptText =
        Map.of(
            "object", changedPrompt,
            "action", typed.prompts().get("action"),
            "analytic", typed.prompts().get("analytic"),
            "relate", typed.prompts().get("relate"),
            "review", typed.prompts().get("review"));
    Path objectPrompt =
        temporaryDirectory.resolve("ontology-corpus-identity-later-stage.yaml-object.txt");
    Files.writeString(objectPrompt, changedPrompt, StandardCharsets.UTF_8);
    String laterConfig = Files.readString(typed.path(), StandardCharsets.UTF_8);
    Files.writeString(
        typed.path(), laterConfig.replace("gpt-5.6-luna", "gpt-6.1-sol"), StandardCharsets.UTF_8);
    ExplicitTypedPipelineScript script = new ExplicitTypedPipelineScript(changedPromptText);
    AtomicInteger laterFactories = new AtomicInteger();
    List<OntologyTypedTaskRunner.FormalModelDeclaration> laterDeclarations = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        laterProvider =
            declaration -> {
              laterFactories.incrementAndGet();
              laterDeclarations.add(declaration);
              return script.provider(declaration);
            };
    Path scope = writeExplicitScope(temporaryDirectory.resolve("saved-corpus-identity-scope.json"));
    CliResult identified =
        executeWithFactory(
            typed.path(),
            laterProvider,
            "identify-ontology",
            "--corpus-run",
            firstO0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    String o1RunId = runId(identified);
    JsonNode o1Observation =
        assertCommandAndInspectionAgree(identified, typed.path(), "IDENTIFY_ONTOLOGY", o1RunId, 2);
    assertThat(o1Observation.path("lifecycleState").asText()).isEqualTo("FINISHED");
    assertThat(o1Observation.path("resultStatus").asText()).isEqualTo("COMPLETED");
    assertThat(o1Observation.path("canContinue").asBoolean()).isTrue();
    assertThat(stringValues(o1Observation.path("availableArtifactKeys")))
        .contains("ONTOLOGY_IDENTIFICATION")
        .containsExactlyElementsOf(availablePublicArtifactKeys(typed.path(), o1RunId));
    assertThat(script.requests).hasSize(2);
    assertThat(script.requests.get(0).systemInstructions()).isEqualTo(changedPrompt);
    assertThat(laterDeclarations).hasSize(1);
    assertThat(laterDeclarations)
        .allSatisfy(
            declaration ->
                assertThat(declaration.expectedRuntimeIdentity().model()).isEqualTo("gpt-6.1-sol"));
    JsonNode identification =
        JSON.readTree(artifact(typed.path(), o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    JsonNode objectTask = findTaskRecord(identification, "object-task");
    String producingTaskId = objectTask.path("producingTaskId").asText();
    assertThat(producingTaskId).isNotBlank();
    JsonNode observation =
        JSON.readTree(
            executePublic(
                    typed.path(),
                    "artifact",
                    "--run",
                    o1RunId,
                    "--key",
                    "ONTOLOGY_TASK_RECORD",
                    "--task-id",
                    producingTaskId,
                    "--max-bytes",
                    "524288")
                .stdout());
    assertThat(observation.path("completion").path("identity").path("corpusIdentity").asText())
        .isEqualTo(savedCorpusIdentity);
    assertThat(observation.path("extract").path("response").path("runtimeIdentity"))
        .isEqualTo(canonicalNode(laterDeclarations.get(0).expectedRuntimeIdentity()));
    ReopenedModulePublication o1Module = reopenedOntologyModule(fixture.runStore(), o1RunId);
    assertThat(o1Module.receipt().upstreamArtifacts()).contains(savedCorpusReference);

    Path foreignRelation =
        writeRelateSelection(
            temporaryDirectory.resolve("foreign-o0-relation.json"), secondO0RunId, o1RunId);
    int factoriesBeforeForeignRun = laterFactories.get();
    CliResult refused =
        executeWithFactory(
            typed.path(),
            laterProvider,
            "relate-ontology",
            "--selection",
            foreignRelation.toString());
    assertThat(refused.exitCode()).isNotZero();
    assertThat(refused.stdout()).isEmpty();
    assertThat(refused.stderr()).contains("ONTOLOGY");
    assertThat(laterFactories).hasValue(factoriesBeforeForeignRun);

    Path zeroQuestionForeignRelation =
        writeSelection(
            temporaryDirectory.resolve("foreign-o0-zero-question-relate.json"),
            "RELATE",
            secondO0RunId,
            List.of(o1RunId),
            List.of(),
            true);
    CliResult refusedWithoutQuestions =
        executeWithFactory(
            typed.path(),
            laterProvider,
            "relate-ontology",
            "--selection",
            zeroQuestionForeignRelation.toString());
    assertThat(refusedWithoutQuestions.exitCode()).isNotZero();
    assertThat(refusedWithoutQuestions.stdout()).isEmpty();
    assertThat(refusedWithoutQuestions.stderr()).contains("ONTOLOGY_SELECTED_RUN_INVALID");
    assertThat(laterFactories).hasValue(factoriesBeforeForeignRun);
  }

  @Test
  void publishConsumesBothSelectedO1ResultsAndTheActualUnresolvedO2Result() throws Exception {
    TechnicalFixture fixture = prepareRealR4("two-o1-publication");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfiguration("ontology-two-o1.yaml", fixture);
    ObjectNode promptMap = JSON.createObjectNode();
    OntologyConfiguration.load(configured.path()).prompts().forEach(promptMap::put);
    String promptSha =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(CANONICAL.encodeCanonical(promptMap).copyToByteArray()));
    AtomicInteger providerFactories = new AtomicInteger();
    List<OntologyTypedTaskRunner.FormalModelDeclaration> declarations = new ArrayList<>();
    MultiO1TypedPipelineScript script = new MultiO1TypedPipelineScript(configured.prompts());
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              declarations.add(declaration);
              return script.provider(declaration.expectedRuntimeIdentity());
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    assertThat(providerFactories).hasValue(0);

    Path firstScope =
        writeExplicitObjectScope(
            temporaryDirectory.resolve("two-o1-first-scope.json"), "Q1", "first-object-task");
    ImmutableBytes firstScopeBytes = ImmutableBytes.copyOf(Files.readAllBytes(firstScope));
    JsonNode firstScopeSnapshot = CANONICAL.parseCanonical(firstScopeBytes);
    String firstScopeSha =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(firstScopeBytes.copyToByteArray()));
    CliResult firstIdentified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            firstScope.toString());
    assertThat(firstIdentified.exitCode()).withFailMessage(firstIdentified.stderr()).isZero();
    String firstO1RunId = runId(firstIdentified);
    assertThat(Files.deleteIfExists(firstScope)).isTrue();
    JsonNode firstO1Observation =
        assertCommandAndInspectionAgree(
            firstIdentified, configured.path(), "IDENTIFY_ONTOLOGY", firstO1RunId, 2);
    assertThat(firstO1Observation.path("lifecycleState").asText()).isEqualTo("FINISHED");
    assertThat(firstO1Observation.path("resultStatus").asText()).isEqualTo("COMPLETED");

    Path secondScope =
        writeExplicitObjectScope(
            temporaryDirectory.resolve("two-o1-second-scope.json"), "Q2", "second-object-task");
    ImmutableBytes secondScopeBytes = ImmutableBytes.copyOf(Files.readAllBytes(secondScope));
    JsonNode secondScopeSnapshot = CANONICAL.parseCanonical(secondScopeBytes);
    String secondScopeSha =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(secondScopeBytes.copyToByteArray()));
    CliResult secondIdentified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            secondScope.toString());
    assertThat(secondIdentified.exitCode()).withFailMessage(secondIdentified.stderr()).isZero();
    String secondO1RunId = runId(secondIdentified);
    assertThat(Files.deleteIfExists(secondScope)).isTrue();
    JsonNode secondO1Observation =
        assertCommandAndInspectionAgree(
            secondIdentified, configured.path(), "IDENTIFY_ONTOLOGY", secondO1RunId, 2);
    assertThat(secondO1Observation.path("lifecycleState").asText()).isEqualTo("FINISHED");
    assertThat(secondO1Observation.path("resultStatus").asText()).isEqualTo("COMPLETED");
    assertThat(script.requests).hasSize(4);
    assertThat(script.requests.get(0).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(script.requests.get(1).taskKind()).contains("OBJECT", "REVIEW");
    assertThat(script.requests.get(2).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(script.requests.get(3).taskKind()).contains("OBJECT", "REVIEW");
    JsonNode firstIdentification =
        JSON.readTree(
            artifact(configured.path(), firstO1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    JsonNode secondIdentification =
        JSON.readTree(
            artifact(configured.path(), secondO1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(firstIdentification.path("scope")).isEqualTo(firstScopeSnapshot);
    assertThat(secondIdentification.path("scope")).isEqualTo(secondScopeSnapshot);
    JsonNode firstTask = findTaskRecord(firstIdentification, "first-object-task");
    JsonNode secondTask = findTaskRecord(secondIdentification, "second-object-task");
    assertThat(firstTask.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(secondTask.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(firstTask.path("producingTaskId").asText())
        .isNotBlank()
        .isNotEqualTo(secondTask.path("producingTaskId").asText());

    Path relateSelection =
        writeMultiRelateSelection(
            temporaryDirectory.resolve("two-o1-relate.json"),
            o0RunId,
            List.of(firstO1RunId, secondO1RunId));
    ImmutableBytes relateSelectionBytes =
        ImmutableBytes.copyOf(Files.readAllBytes(relateSelection));
    JsonNode relateSelectionSnapshot = CANONICAL.parseCanonical(relateSelectionBytes);
    String relateSelectionSha =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(relateSelectionBytes.copyToByteArray()));
    CliResult related =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            relateSelection.toString());
    assertThat(related.exitCode()).withFailMessage(related.stderr()).isZero();
    String o2RunId = runId(related);
    assertThat(Files.deleteIfExists(relateSelection)).isTrue();
    JsonNode o2Observation =
        assertCommandAndInspectionAgree(related, configured.path(), "RELATE_ONTOLOGY", o2RunId, 2);
    assertThat(o2Observation.path("lifecycleState").asText()).isEqualTo("FINISHED");
    assertThat(o2Observation.path("resultStatus").asText()).isIn("COMPLETED", "PARTIAL");
    assertThat(script.requests).hasSize(6);
    assertThat(script.requests.get(4).taskKind()).contains("RELATE", "EXTRACT");
    assertThat(script.requests.get(5).taskKind()).contains("RELATE", "REVIEW");
    JsonNode relateInput = MultiO1TypedPipelineScript.input(script.requests.get(4));
    List<String> reviewedObjectRefs =
        textFieldValues(relateInput.path("reviewedCatalog"), "catalogRef");
    assertThat(reviewedObjectRefs).contains("B1", "B2");
    assertThat(reviewedObjectRefs).doesNotHaveDuplicates();
    assertThat(declarations).hasSize(3);
    assertThat(declarations.subList(0, 2))
        .allSatisfy(
            declaration -> {
              assertThat(declaration.key()).isEqualTo(EXTRACT_PROVIDER_KEY);
              assertThat(declaration.expectedRuntimeIdentity().model()).isEqualTo("gpt-5.6-luna");
            });
    assertThat(declarations.subList(2, 3))
        .allSatisfy(
            declaration -> {
              assertThat(declaration.key()).isEqualTo(RELATE_PROVIDER_KEY);
              assertThat(declaration.expectedRuntimeIdentity().model()).isEqualTo("gpt-5.6-sol");
            });
    CliResult relationArtifact = artifact(configured.path(), o2RunId, "ONTOLOGY_RELATIONS");
    assertArtifactAvailable(relationArtifact);
    JsonNode relations = JSON.readTree(relationArtifact.stdout());
    assertThat(relations.path("selection")).isEqualTo(relateSelectionSnapshot);
    assertThat(containsText(relations, MultiO1TypedPipelineScript.UNRESOLVED_ID)).isTrue();
    JsonNode relateTask = findTaskRecord(relations, "multi-relate-task");
    String relateProducingTaskId = relateTask.path("producingTaskId").asText();
    assertThat(relateTask.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(relateProducingTaskId).isNotBlank();
    CliResult relateObservationResult =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            o2RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            relateProducingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(relateObservationResult);
    JsonNode relateObservation = JSON.readTree(relateObservationResult.stdout());
    assertThat(
            relateObservation.path("completion").path("identity").path("producingTaskId").asText())
        .isEqualTo(relateProducingTaskId);
    assertThat(
            relateObservation
                .path("extract")
                .path("response")
                .path("runtimeIdentity")
                .path("model")
                .asText())
        .isEqualTo(declarations.get(2).expectedRuntimeIdentity().model());
    assertThat(
            relateObservation
                .path("review")
                .path("response")
                .path("runtimeIdentity")
                .path("model")
                .asText())
        .isEqualTo(declarations.get(2).expectedRuntimeIdentity().model());

    ReopenedModulePublication firstO1Module =
        reopenedOntologyModule(fixture.runStore(), firstO1RunId);
    ReopenedModulePublication secondO1Module =
        reopenedOntologyModule(fixture.runStore(), secondO1RunId);
    ReopenedModulePublication o2Module = reopenedOntologyModule(fixture.runStore(), o2RunId);
    assertThat(o2Module.receipt().upstreamArtifacts())
        .containsAll(modulePayloadReferences(firstO1Module))
        .containsAll(modulePayloadReferences(secondO1Module));

    Path publishSelection =
        writeSelection(
            temporaryDirectory.resolve("two-o1-publish.json"),
            "PUBLISH",
            o0RunId,
            List.of(firstO1RunId, secondO1RunId),
            List.of(o2RunId),
            false);
    ImmutableBytes publishSelectionBytes =
        ImmutableBytes.copyOf(Files.readAllBytes(publishSelection));
    JsonNode publishSelectionSnapshot = CANONICAL.parseCanonical(publishSelectionBytes);
    String publishSelectionSha =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(publishSelectionBytes.copyToByteArray()));
    int factoriesBeforeO3 = providerFactories.get();
    CliResult published =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            publishSelection.toString());
    assertThat(published.exitCode()).withFailMessage(published.stderr()).isZero();
    String o3RunId = runId(published);
    assertThat(Files.deleteIfExists(publishSelection)).isTrue();
    JsonNode o3Observation =
        assertCommandAndInspectionAgree(
            published, configured.path(), "PUBLISH_ONTOLOGY", o3RunId, 0);
    assertThat(o3Observation.path("lifecycleState").asText()).isEqualTo("FINISHED");
    assertThat(providerFactories).hasValue(factoriesBeforeO3);
    assertThat(script.requests).hasSize(6);

    CliResult ontologyArtifact = artifact(configured.path(), o3RunId, "ONTOLOGY");
    CliResult reviewArtifact = artifact(configured.path(), o3RunId, "ONTOLOGY_REVIEW");
    assertArtifactAvailable(ontologyArtifact);
    assertArtifactAvailable(reviewArtifact);
    JsonNode ontology = JSON.readTree(ontologyArtifact.stdout());
    JsonNode review = JSON.readTree(reviewArtifact.stdout());
    assertThat(review.path("selection")).isEqualTo(publishSelectionSnapshot);
    assertThat(ontology.path("objectTypes")).hasSize(2);
    List<String> globalIds = new ArrayList<>();
    List<String> actualObjectTaskIds = new ArrayList<>();
    ontology.path("objectTypes").forEach(object -> globalIds.add(object.path("globalId").asText()));
    ontology
        .path("definitionIndex")
        .forEach(
            index -> {
              if ("objects".equals(index.path("definitionType").asText())) {
                actualObjectTaskIds.add(index.path("producingTaskId").asText());
              }
            });
    assertThat(globalIds).hasSize(2).doesNotHaveDuplicates();
    assertThat(actualObjectTaskIds)
        .containsExactlyInAnyOrder(
            firstTask.path("producingTaskId").asText(),
            secondTask.path("producingTaskId").asText());
    assertThat(containsText(review, MultiO1TypedPipelineScript.UNRESOLVED_ID)).isTrue();
    assertThat(review.path("taskResults")).hasSize(3);
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      var firstO1Persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
              store, AnalysisRunId.parse(firstO1RunId));
      var secondO1Persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
              store, AnalysisRunId.parse(secondO1RunId));
      var o2Persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, AnalysisRunId.parse(o2RunId));
      var o3Persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, AnalysisRunId.parse(o3RunId));
      AnalysisRunRequest.OntologyInputs firstO1Inputs = firstO1Persisted.request().ontologyInputs();
      AnalysisRunRequest.OntologyInputs secondO1Inputs =
          secondO1Persisted.request().ontologyInputs();
      AnalysisRunRequest.OntologyInputs o2Inputs = o2Persisted.request().ontologyInputs();
      AnalysisRunRequest.OntologyInputs o3Inputs = o3Persisted.request().ontologyInputs();
      assertThat(firstO1Inputs.ontologyScopeRef().sha256().value()).isEqualTo(firstScopeSha);
      assertThat(firstO1Inputs.ontologyScopeRef().artifactId().value())
          .isEqualTo("ontology-scope:" + firstScopeSha);
      assertThat(secondO1Inputs.ontologyScopeRef().sha256().value()).isEqualTo(secondScopeSha);
      assertThat(secondO1Inputs.ontologyScopeRef().artifactId().value())
          .isEqualTo("ontology-scope:" + secondScopeSha);
      for (AnalysisRunRequest.OntologyInputs o1Inputs : List.of(firstO1Inputs, secondO1Inputs)) {
        assertThat(o1Inputs.promptBundleRef().sha256().value()).isEqualTo(promptSha);
        assertThat(o1Inputs.promptBundleRef().artifactId().value())
            .isEqualTo("ontology-prompts:" + promptSha);
      }
      assertThat(o2Inputs.ontologyScopeRef().sha256().value()).isEqualTo(relateSelectionSha);
      assertThat(o2Inputs.ontologyScopeRef().artifactId().value())
          .isEqualTo("ontology-selection:" + relateSelectionSha);
      assertThat(o2Inputs.ontologySelectionRef().sha256().value()).isEqualTo(relateSelectionSha);
      assertThat(o2Inputs.ontologySelectionRef().artifactId().value())
          .isEqualTo("ontology-selection:" + relateSelectionSha);
      assertThat(o2Inputs.promptBundleRef().sha256().value()).isEqualTo(promptSha);
      assertThat(o2Inputs.promptBundleRef().artifactId().value())
          .isEqualTo("ontology-prompts:" + promptSha);
      assertThat(o2Inputs.promptBundleRef().sha256().value())
          .isNotEqualTo(o2Inputs.ontologySelectionRef().sha256().value());
      assertThat(o3Inputs.ontologyScopeRef().sha256().value()).isEqualTo(publishSelectionSha);
      assertThat(o3Inputs.ontologyScopeRef().artifactId().value())
          .isEqualTo("ontology-selection:" + publishSelectionSha);
      assertThat(o3Inputs.ontologySelectionRef().sha256().value()).isEqualTo(publishSelectionSha);
      assertThat(o3Inputs.ontologySelectionRef().artifactId().value())
          .isEqualTo("ontology-selection:" + publishSelectionSha);
      JsonNode firstO1Request = CANONICAL.parseCanonical(firstO1Persisted.canonicalJson());
      JsonNode secondO1Request = CANONICAL.parseCanonical(secondO1Persisted.canonicalJson());
      JsonNode o2Request = CANONICAL.parseCanonical(o2Persisted.canonicalJson());
      JsonNode o3Request = CANONICAL.parseCanonical(o3Persisted.canonicalJson());
      assertThat(firstIdentification.path("semanticUpstreams").path("corpusPublication"))
          .isEqualTo(firstO1Request.path("ontologyInputs").path("corpusPublication"));
      assertThat(firstIdentification.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(firstO1Request.path("ontologyInputs").path("identificationPublications"));
      assertThat(firstIdentification.path("semanticUpstreams").path("relationPublications"))
          .isEqualTo(firstO1Request.path("ontologyInputs").path("relationPublications"));
      assertThat(secondIdentification.path("semanticUpstreams").path("corpusPublication"))
          .isEqualTo(secondO1Request.path("ontologyInputs").path("corpusPublication"));
      assertThat(secondIdentification.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(secondO1Request.path("ontologyInputs").path("identificationPublications"));
      assertThat(secondIdentification.path("semanticUpstreams").path("relationPublications"))
          .isEqualTo(secondO1Request.path("ontologyInputs").path("relationPublications"));
      assertThat(relations.path("semanticUpstreams").path("corpusPublication"))
          .isEqualTo(o2Request.path("ontologyInputs").path("corpusPublication"));
      assertThat(relations.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(o2Request.path("ontologyInputs").path("identificationPublications"));
      assertThat(relations.path("semanticUpstreams").path("relationPublications"))
          .isEqualTo(o2Request.path("ontologyInputs").path("relationPublications"));
      assertThat(review.path("semanticUpstreams").path("corpusPublication"))
          .isEqualTo(o3Request.path("ontologyInputs").path("corpusPublication"));
      assertThat(review.path("semanticUpstreams").path("identificationPublications"))
          .isEqualTo(o3Request.path("ontologyInputs").path("identificationPublications"));
      assertThat(review.path("semanticUpstreams").path("relationPublications"))
          .isEqualTo(o3Request.path("ontologyInputs").path("relationPublications"));
    }
    ReopenedModulePublication o3Module = reopenedOntologyModule(fixture.runStore(), o3RunId);
    assertThat(o3Module.receipt().upstreamArtifacts())
        .containsAll(modulePayloadReferences(firstO1Module))
        .containsAll(modulePayloadReferences(secondO1Module))
        .containsAll(modulePayloadReferences(o2Module));
    assertThat(providerFactories).hasValue(factoriesBeforeO3);
  }

  @Test
  void maxRequestsCountsActualDispatchesAcrossDependentO1Tasks() throws Exception {
    TechnicalFixture fixture = prepareRealR4("shared-typed-request-cap");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfiguration("ontology-shared-cap.yaml", fixture);
    Files.writeString(
        configured.path(),
        Files.readString(configured.path(), StandardCharsets.UTF_8)
            .replace("maxRequests: 8", "maxRequests: 2"),
        StandardCharsets.UTF_8);
    AtomicInteger providerFactories = new AtomicInteger();
    ExplicitTypedPipelineScript script = new ExplicitTypedPipelineScript(configured.prompts());
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return script.provider(declaration);
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    Path scope = writeExplicitTypedTaskScope(temporaryDirectory.resolve("shared-cap-scope.json"));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.stdout()).isNotBlank();
    String o1RunId = runId(identified);
    JsonNode capObservation =
        assertCommandAndInspectionAgree(
            identified, configured.path(), "IDENTIFY_ONTOLOGY", o1RunId, 2);
    assertThat(script.requests).hasSize(2);
    assertThat(script.requests.get(0).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(script.requests.get(1).taskKind()).contains("OBJECT", "REVIEW");
    assertThat(script.requests)
        .noneSatisfy(request -> assertThat(request.taskKind()).contains("ACTION", "ANALYTIC"));

    assertThat(capObservation.path("resultStatus").asText()).isIn("PARTIAL", "BLOCKED");
    assertThat(capObservation.path("canContinue").asBoolean()).isFalse();
    assertThat(arrayValues(capObservation.path("problems")))
        .anySatisfy(
            problem -> {
              assertThat(problem.path("taskId").asText()).isEqualTo("action-task");
              assertThat(problem.path("category").asText()).isEqualTo("DISPATCH_LIMIT");
              assertThat(problem.path("stage").asText()).isEqualTo("EXTRACT");
              assertThat(problem.path("code").asText())
                  .isEqualTo(OntologyCallBudgetProvider.DispatchLimitExceeded.CODE);
            });
    JsonNode capCounts = capObservation.path("modelRequestCounts");
    assertThat(capCounts.path("reservedAttempts").asInt()).isEqualTo(2);
    assertThat(capCounts.path("confirmedStarted").asInt()).isEqualTo(2);
    assertThat(capCounts.path("confirmedEnded").asInt()).isEqualTo(2);
    assertThat(capCounts.path("outcomeUnknown").asInt()).isZero();
    System.out.println("TASK7_PUBLIC_CAP_OBSERVATION " + JSON.writeValueAsString(capObservation));

    CliResult identificationArtifact =
        artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identificationArtifact);
    JsonNode identification = JSON.readTree(identificationArtifact.stdout());
    assertThat(identification.path("status").asText()).isIn("PARTIAL", "BLOCKED");
    JsonNode objectTask = findTaskRecord(identification, "object-task");
    assertThat(objectTask.path("status").asText()).isEqualTo("REVIEWED");
    List<JsonNode> preparedTaskRecords = new ArrayList<>();
    identification.path("taskRecords").forEach(preparedTaskRecords::add);
    assertThat(preparedTaskRecords.stream().map(record -> record.path("taskId").asText()).toList())
        .containsExactlyInAnyOrder("object-task", "action-task", "analytic-task");
    JsonNode actionTask = findTaskRecord(identification, "action-task");
    assertThat(actionTask.path("status").asText()).isEqualTo("REJECTED");
    JsonNode analyticTask = findTaskRecord(identification, "analytic-task");
    assertThat(analyticTask.path("status").asText()).isEqualTo("REJECTED");
    JsonNode actionDisposition = null;
    JsonNode analyticDisposition = null;
    for (JsonNode disposition : identification.path("taskDispositions")) {
      if ("action-task".equals(disposition.path("taskId").asText())) {
        actionDisposition = disposition;
      } else if ("analytic-task".equals(disposition.path("taskId").asText())) {
        analyticDisposition = disposition;
      }
    }
    assertThat(actionDisposition).isNotNull();
    assertThat(actionDisposition.path("status").asText()).isEqualTo("REJECTED");
    assertThat(actionDisposition.path("producingTaskId").asText())
        .isEqualTo(actionTask.path("producingTaskId").asText());
    assertThat(actionDisposition.path("reason").asText())
        .isEqualTo(OntologyCallBudgetProvider.DispatchLimitExceeded.CODE);
    assertThat(analyticDisposition).isNotNull();
    assertThat(analyticDisposition.path("status").asText()).isEqualTo("REJECTED");
    assertThat(analyticDisposition.path("producingTaskId").asText())
        .isEqualTo(analyticTask.path("producingTaskId").asText());
    assertThat(analyticDisposition.path("reason").asText())
        .isEqualTo(OntologyCallBudgetProvider.DispatchLimitExceeded.CODE);
    List<ObjectNode> completed =
        new PrivateModelJobResultStore(
                fixture.runStore().resolve("ontology-journal"),
                AnalysisRunId.parse(o1RunId),
                "ontology")
            .listReviewedResults();
    assertThat(completed).hasSize(1);
    assertThat(completed.get(0).path("identity").path("producingTaskId").asText())
        .isEqualTo(objectTask.path("producingTaskId").asText());
    OntologyJobResultStore jobs =
        new OntologyJobResultStore(
            fixture.runStore().resolve("ontology-journal"), AnalysisRunId.parse(o1RunId));
    assertThat(
            jobs.readFormalMembership(completed.get(0).path("jobKey").asText()).producingTaskId())
        .isEqualTo(objectTask.path("producingTaskId").asText());
    assertThat(providerFactories).hasValue(1);
    assertThat(providerFactories.get()).isPositive();

    Path zeroTaskRelationSelection =
        writeSelection(
            temporaryDirectory.resolve("shared-cap-zero-task-relate.json"),
            "RELATE",
            o0RunId,
            List.of(o1RunId),
            List.of(),
            true);
    int factoriesBeforeO2 = providerFactories.get();
    CliResult related =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            zeroTaskRelationSelection.toString());
    assertThat(related.exitCode()).isIn(0, 2);
    assertThat(related.stdout()).isNotBlank();
    String o2RunId = runId(related);
    JsonNode o2Observation =
        assertCommandAndInspectionAgree(related, configured.path(), "RELATE_ONTOLOGY", o2RunId, 0);
    assertThat(o2Observation.path("modelRequestsDispatched").asInt()).isZero();
    CliResult relationsArtifact = artifact(configured.path(), o2RunId, "ONTOLOGY_RELATIONS");
    assertArtifactAvailable(relationsArtifact);
    assertThat(providerFactories).hasValue(factoriesBeforeO2);
    assertThat(script.requests).hasSize(2);

    Path publishSelection =
        writeSelection(
            temporaryDirectory.resolve("shared-cap-publish.json"),
            "PUBLISH",
            o0RunId,
            List.of(o1RunId),
            List.of(o2RunId),
            false);
    int factoriesBeforeO3 = providerFactories.get();
    CliResult published =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            publishSelection.toString());
    assertThat(published.exitCode()).isIn(0, 2);
    assertThat(published.stdout()).isNotBlank();
    String o3RunId = runId(published);
    JsonNode o3Observation =
        assertCommandAndInspectionAgree(
            published, configured.path(), "PUBLISH_ONTOLOGY", o3RunId, 0);
    assertThat(providerFactories).hasValue(factoriesBeforeO3);
    assertThat(script.requests).hasSize(2);

    List<String> publishedKeys = availablePublicArtifactKeys(configured.path(), o3RunId);
    assertThat(publishedKeys)
        .containsExactlyInAnyOrder(
            "ONTOLOGY", "ONTOLOGY_COVERAGE", "ONTOLOGY_REVIEW", "ONTOLOGY_SOURCE_INDEX");
    CliResult ontologyArtifact = artifact(configured.path(), o3RunId, "ONTOLOGY");
    CliResult coverageArtifact = artifact(configured.path(), o3RunId, "ONTOLOGY_COVERAGE");
    CliResult sourceIndexArtifact = artifact(configured.path(), o3RunId, "ONTOLOGY_SOURCE_INDEX");
    CliResult reviewArtifact = artifact(configured.path(), o3RunId, "ONTOLOGY_REVIEW");
    assertArtifactAvailable(ontologyArtifact);
    assertArtifactAvailable(coverageArtifact);
    assertArtifactAvailable(sourceIndexArtifact);
    assertArtifactAvailable(reviewArtifact);
    JsonNode ontology = JSON.readTree(ontologyArtifact.stdout());
    assertThat(ontology.path("objectTypes")).hasSize(1);
    assertThat(ontology.path("objectTypes").get(0).path("name").asText())
        .isEqualTo("Neutral source-backed record");
    JsonNode coverage = JSON.readTree(coverageArtifact.stdout());
    assertThat(coverage.path("coverageStatus").asText()).isEqualTo("INCOMPLETE");
    List<JsonNode> publishedTaskDispositions = arrayValues(coverage.path("taskDispositions"));
    JsonNode publishedObjectTask =
        publishedTaskDispositions.stream()
            .filter(disposition -> "object-task".equals(disposition.path("taskId").asText()))
            .findFirst()
            .orElse(null);
    JsonNode publishedActionTask =
        publishedTaskDispositions.stream()
            .filter(disposition -> "action-task".equals(disposition.path("taskId").asText()))
            .findFirst()
            .orElse(null);
    JsonNode publishedAnalyticTask =
        publishedTaskDispositions.stream()
            .filter(disposition -> "analytic-task".equals(disposition.path("taskId").asText()))
            .findFirst()
            .orElse(null);
    assertThat(publishedObjectTask).isNotNull();
    assertThat(publishedObjectTask.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(publishedObjectTask.path("producingTaskId").asText())
        .isEqualTo(objectTask.path("producingTaskId").asText());
    assertThat(publishedActionTask).isNotNull();
    assertThat(publishedActionTask.path("status").asText()).isEqualTo("REJECTED");
    assertThat(publishedActionTask.path("producingTaskId").asText())
        .isEqualTo(actionTask.path("producingTaskId").asText());
    assertThat(publishedActionTask.path("reason").asText())
        .isEqualTo(OntologyCallBudgetProvider.DispatchLimitExceeded.CODE);
    assertThat(publishedAnalyticTask).isNotNull();
    assertThat(publishedAnalyticTask.path("status").asText()).isEqualTo("REJECTED");
    assertThat(publishedAnalyticTask.path("producingTaskId").asText())
        .isEqualTo(analyticTask.path("producingTaskId").asText());
    assertThat(publishedAnalyticTask.path("reason").asText())
        .isEqualTo(OntologyCallBudgetProvider.DispatchLimitExceeded.CODE);
    assertThat(o3Observation.path("canContinue").asBoolean()).isFalse();
  }

  @Test
  void v1RelationReviewDispatchLimitIsTypedInPublicObservationWithoutTaskOutcomes()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("v1-relation-review-dispatch-limit");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfiguration("ontology-v1-relation-dispatch-limit.yaml", fixture);
    AtomicInteger providerFactories = new AtomicInteger();
    ExplicitTypedPipelineScript script = new ExplicitTypedPipelineScript(configured.prompts());
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return script.provider(declaration);
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);

    Path objectScope =
        writeExplicitObjectScope(
            temporaryDirectory.resolve("v1-relation-dispatch-object-scope.json"),
            "Q1",
            "object-task");
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            objectScope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    String o1RunId = runId(identified);
    assertThat(script.requests).hasSize(2);
    assertThat(script.requests.get(0).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(script.requests.get(1).taskKind()).contains("OBJECT", "REVIEW");

    CliResult identificationArtifact =
        artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identificationArtifact);
    JsonNode identification = JSON.readTree(identificationArtifact.stdout());
    assertThat(identification.path("schemaVersion").asText())
        .isEqualTo("ontology-identification-v1");
    assertThat(identification.has("taskOutcomes")).isFalse();

    Path constrainedO2Configuration =
        temporaryDirectory.resolve("ontology-v1-relation-dispatch-limit-o2.yaml");
    String constrainedConfiguration =
        Files.readString(configured.path(), StandardCharsets.UTF_8)
            .replace("maxRequests: 8", "maxRequests: 1");
    assertThat(constrainedConfiguration).isNotEqualTo(Files.readString(configured.path()));
    assertThat(constrainedConfiguration).contains("maxRequests: 1");
    Files.writeString(constrainedO2Configuration, constrainedConfiguration, StandardCharsets.UTF_8);

    Path selection =
        writeRelateSelection(
            temporaryDirectory.resolve("v1-relation-review-dispatch-limit-selection.json"),
            o0RunId,
            o1RunId);
    CliResult related =
        executeWithFactory(
            constrainedO2Configuration,
            providerFactory,
            "relate-ontology",
            "--selection",
            selection.toString());
    assertThat(related.exitCode()).withFailMessage(related.stderr()).isIn(0, 2);
    assertThat(related.stdout()).isNotBlank();
    String o2RunId = runId(related);
    JsonNode observation =
        assertCommandAndInspectionAgree(
            related, constrainedO2Configuration, "RELATE_ONTOLOGY", o2RunId, 1);
    assertThat(observation.path("resultStatus").asText()).isEqualTo("PARTIAL");
    assertThat(arrayValues(observation.path("problems"))).hasSize(1);
    JsonNode problem = arrayValues(observation.path("problems")).get(0);
    assertThat(problem.path("taskId").asText()).isEqualTo("relate-task");
    assertThat(problem.path("category").asText()).isEqualTo("DISPATCH_LIMIT");
    assertThat(problem.path("code").asText())
        .isEqualTo(OntologyCallBudgetProvider.DispatchLimitExceeded.CODE);
    assertThat(problem.path("stage").asText()).isEqualTo("REVIEW");
    JsonNode counts = observation.path("modelRequestCounts");
    assertThat(counts.path("reservedAttempts").asInt()).isEqualTo(1);
    assertThat(counts.path("confirmedStarted").asInt()).isEqualTo(1);
    assertThat(counts.path("confirmedEnded").asInt()).isEqualTo(1);
    assertThat(counts.path("outcomeUnknown").asInt()).isZero();

    CliResult relationArtifact =
        artifact(constrainedO2Configuration, o2RunId, "ONTOLOGY_RELATIONS");
    assertArtifactAvailable(relationArtifact);
    JsonNode relations = JSON.readTree(relationArtifact.stdout());
    assertThat(relations.path("schemaVersion").asText()).isEqualTo("ontology-relations-v1");
    assertThat(relations.has("taskOutcomes")).isFalse();
    List<JsonNode> relationTasks = arrayValues(relations.path("taskRecords"));
    assertThat(relationTasks).hasSize(1);
    JsonNode relationTask = relationTasks.get(0);
    assertThat(relationTask.path("taskId").asText()).isEqualTo("relate-task");
    assertThat(relationTask.path("taskKind").asText()).isEqualTo("RELATE");
    assertThat(relationTask.path("status").asText()).isEqualTo("REJECTED");
    assertThat(relationTask.path("producingTaskId").asText()).isNotBlank();
    assertThat(relationTask.path("jobKey").asText()).isNotBlank();
    assertThat(script.requests).hasSize(3);
    assertThat(script.requests.get(2).taskKind()).contains("RELATE", "EXTRACT");
    assertThat(providerFactories).hasValue(2);
  }

  @Test
  void taskLocalMalformedOutputDoesNotMaskIdentificationInstallFailure() throws Exception {
    TechnicalFixture fixture = prepareRealR4("local-output-before-identification-install-failure");
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2(
            "ontology-local-output-before-install-failure-v2.yaml", fixture);
    ObjectNode policySet =
        (ObjectNode)
            JSON.readTree(Files.readString(ONTOLOGY_POLICY_SET_V2, StandardCharsets.UTF_8));
    ArrayNode retainedPolicies = JSON.createArrayNode();
    int removedPolicies = 0;
    for (JsonNode policy : policySet.path("policies")) {
      if ("ONTOLOGY_IDENTIFICATION".equals(policy.path("artifactType").asText())) {
        removedPolicies++;
      } else {
        retainedPolicies.add(policy.deepCopy());
      }
    }
    assertThat(removedPolicies).isEqualTo(2);
    policySet.set("policies", retainedPolicies);
    Path restrictedPolicySet =
        temporaryDirectory.resolve("ontology-v2-policy-without-identification.json");
    Files.write(restrictedPolicySet, JSON.writeValueAsBytes(policySet));
    String originalYaml = Files.readString(configured.path(), StandardCharsets.UTF_8);
    assertThat(originalYaml).contains(yaml(ONTOLOGY_POLICY_SET_V2));
    Path configuration =
        temporaryDirectory.resolve("ontology-local-output-before-install-restricted.yaml");
    Files.writeString(
        configuration,
        originalYaml.replace(yaml(ONTOLOGY_POLICY_SET_V2), yaml(restrictedPolicySet)),
        StandardCharsets.UTF_8);

    byte[] malformedBytes =
        "{\"private\":\"LOCAL_MODEL_OUTPUT_BEFORE_INSTALL_SENTINEL\""
            .getBytes(StandardCharsets.UTF_8);
    ImmutableBytes malformedResponse = ImmutableBytes.copyOf(malformedBytes);
    List<StructuredModelRequest> requests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                return new StructuredModelResponse(malformedResponse, identity);
              };
            };
    CliResult prepared =
        executeWithFactory(
            configuration,
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    Path scope =
        writeExplicitScope(temporaryDirectory.resolve("install-failure-object-scope.json"));
    CliResult failed =
        executeWithFactory(
            configuration,
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(failed.exitCode()).isNotZero();
    assertThat(failed.stdout())
        .isNotBlank()
        .doesNotContain("LOCAL_MODEL_OUTPUT_BEFORE_INSTALL_SENTINEL");
    assertThat(failed.stderr()).doesNotContain("LOCAL_MODEL_OUTPUT_BEFORE_INSTALL_SENTINEL");
    String o1RunId = runId(failed);
    JsonNode failureObservation =
        assertCommandAndInspectionAgree(failed, configuration, "IDENTIFY_ONTOLOGY", o1RunId, 1);
    assertThat(failureObservation.path("lifecycleState").asText()).isEqualTo("FAILED");
    assertThat(failureObservation.path("resultStatus").isNull()).isTrue();
    assertThat(arrayValues(failureObservation.path("availableArtifactKeys"))).isEmpty();
    assertThat(arrayValues(failureObservation.path("problems")))
        .anySatisfy(
            problem -> {
              assertThat(problem.path("category").asText()).isEqualTo("STORAGE");
              assertThat(problem.path("stage").asText()).isEqualTo("INSTALL");
              assertThat(problem.path("code").asText())
                  .isNotEqualTo("ONTOLOGY_FORMAL_RESPONSE_INVALID");
            });
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).taskKind()).contains("OBJECT", "EXTRACT");

    CliResult noIdentificationReceipt = artifact(configuration, o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertThat(noIdentificationReceipt.exitCode()).isNotZero();
    assertThat(noIdentificationReceipt.stdout()).isEmpty();
    CliResult taskIndexResult = artifact(configuration, o1RunId, "ONTOLOGY_TASK_INDEX");
    assertArtifactAvailable(taskIndexResult);
    JsonNode rejectedTask = findTaskRecord(JSON.readTree(taskIndexResult.stdout()), "object-task");
    assertThat(rejectedTask.path("status").asText()).isEqualTo("REJECTED");
    String producingTaskId = rejectedTask.path("producingTaskId").asText();
    assertThat(producingTaskId).isNotBlank();
    CliResult privateTaskResult =
        executePublic(
            configuration,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            producingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(privateTaskResult);
    JsonNode privateTask = JSON.readTree(privateTaskResult.stdout());
    assertThat(privateTask.path("status").asText()).isEqualTo("REJECTED");
    assertThat(privateTask.path("failure").path("failureCode").asText()).isEqualTo("INVALID_JSON");
    assertThat(privateTask.path("extract").path("response").path("rawResponseBase64").asText())
        .isEqualTo(Base64.getEncoder().encodeToString(malformedBytes));
    JsonNode reviewRequest = privateTask.path("review").path("request");
    assertThat(reviewRequest.isNull() || reviewRequest.isMissingNode()).isTrue();
  }

  @Test
  void savedPairRemainsQueryableWhenIdentificationPolicyRejectsPublicInstallation()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("private-pair-public-install-failure");
    Path configuredWithRestrictedPolicy =
        writeTypedPipelineConfiguration("ontology-private-pair-rejected-install.yaml", fixture)
            .path();
    Path configuration =
        writePolicySetWithout(
            configuredWithRestrictedPolicy,
            "ONTOLOGY_IDENTIFICATION",
            temporaryDirectory.resolve("ontology-policy-without-identification.json"));
    assertThat(Files.isRegularFile(configuration)).isTrue();
    AtomicInteger providerFactories = new AtomicInteger();
    ScriptedModel model =
        new ScriptedModel(
            formalTaskObjectResponse("ontology-typed-candidate-v3", "Q1", "E1"),
            formalTaskObjectResponse("ontology-typed-review-v3", "Q1", "E1"));
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return model.provider(declaration.expectedRuntimeIdentity());
            };

    CliResult prepared =
        executeWithFactory(
            configuration,
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    assertArtifactAvailable(artifact(configuration, o0RunId, "ONTOLOGY_CORPUS"));
    Path scope = writeExplicitScope(temporaryDirectory.resolve("rejected-install-scope.json"));
    CliResult failed =
        executeWithFactory(
            configuration,
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(failed.exitCode()).isNotZero();
    assertThat(failed.stdout()).isNotBlank();
    assertThat(model.requests).hasSize(2);
    assertThat(model.requests.get(0).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(model.requests.get(1).taskKind()).contains("OBJECT", "REVIEW");
    String o1RunId = runId(failed);
    assertThat(o1RunId).isNotEqualTo(o0RunId);
    JsonNode failureObservation =
        assertCommandAndInspectionAgree(failed, configuration, "IDENTIFY_ONTOLOGY", o1RunId, 2);
    assertThat(failureObservation.path("lifecycleState").asText()).isEqualTo("FAILED");
    assertThat(failureObservation.path("resultStatus").isNull()).isTrue();
    assertThat(failureObservation.path("canContinue").asBoolean()).isFalse();
    assertThat(failureObservation.path("availableArtifactKeys").size()).isZero();
    assertThat(arrayValues(failureObservation.path("problems"))).isNotEmpty();
    System.out.println(
        "TASK7_PUBLIC_FAILED_INSTALL_OBSERVATION " + JSON.writeValueAsString(failureObservation));

    CliResult noInventedPublicResult = artifact(configuration, o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertThat(noInventedPublicResult.exitCode()).isNotZero();
    assertThat(noInventedPublicResult.stdout()).isEmpty();

    int factoriesBeforeTaskIndex = providerFactories.get();
    CliResult taskIndexResult =
        executePublic(
            configuration,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_INDEX",
            "--max-bytes",
            "524288");
    assertArtifactAvailable(taskIndexResult);
    JsonNode taskIndex = JSON.readTree(taskIndexResult.stdout());
    Set<String> taskIndexFields = new LinkedHashSet<>();
    taskIndex.fieldNames().forEachRemaining(taskIndexFields::add);
    assertThat(taskIndexFields).containsExactlyInAnyOrder("schemaVersion", "runId", "taskRecords");
    assertThat(taskIndex.path("schemaVersion").asText()).isEqualTo("ontology-task-index-v1");
    assertThat(taskIndex.path("runId").asText()).isEqualTo(o1RunId);
    assertThat(taskIndex.path("taskRecords").isArray()).isTrue();
    List<JsonNode> indexedTasks = arrayValues(taskIndex.path("taskRecords"));
    assertThat(indexedTasks).hasSize(1);
    JsonNode indexedObjectTask = indexedTasks.get(0);
    assertThat(indexedObjectTask.isObject()).isTrue();
    Set<String> taskFields = new LinkedHashSet<>();
    indexedObjectTask.fieldNames().forEachRemaining(taskFields::add);
    assertThat(taskFields)
        .containsExactlyInAnyOrder("taskId", "producingTaskId", "jobKey", "taskKind", "status");
    assertThat(indexedObjectTask.path("taskId").asText()).isEqualTo("object-task");
    assertThat(indexedObjectTask.path("taskKind").asText()).isEqualTo("OBJECT");
    assertThat(indexedObjectTask.path("status").asText()).isEqualTo("REVIEWED");
    String actualProducingTaskId = indexedObjectTask.path("producingTaskId").asText();
    String actualJobKey = indexedObjectTask.path("jobKey").asText();
    assertThat(actualProducingTaskId).isNotBlank();
    assertThat(actualProducingTaskId).isNotEqualTo("object-task");
    assertThat(actualJobKey).isNotBlank();
    assertThat(stringValues(failureObservation.path("availableArtifactKeys")))
        .doesNotContain("ONTOLOGY_TASK_INDEX");

    CliResult observationResult =
        executePublic(
            configuration,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            actualProducingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(observationResult);
    JsonNode observation = JSON.readTree(observationResult.stdout());
    assertThat(observation.path("runId").asText()).isEqualTo(o1RunId);
    assertThat(observation.path("taskId").asText()).isEqualTo("object-task");
    assertThat(observation.path("producingTaskId").asText()).isEqualTo(actualProducingTaskId);
    assertThat(observation.path("jobKey").asText()).isEqualTo(actualJobKey);
    assertThat(observation.path("status").asText()).isEqualTo("REVIEWED");
    assertSavedFormalResponseAndValidation(
        observation.path("extract"),
        formalTaskObjectResponse("ontology-typed-candidate-v3", "Q1", "E1"),
        "VALID_CANDIDATE");
    assertSavedFormalResponseAndValidation(
        observation.path("review"),
        formalTaskObjectResponse("ontology-typed-review-v3", "Q1", "E1"),
        "REVIEW_VALID");
    JsonNode completion = observation.path("completion");
    assertThat(completion.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(completion.path("review"))
        .isEqualTo(
            JSON.readTree(
                formalTaskObjectResponse("ontology-typed-review-v3", "Q1", "E1")
                    .copyToByteArray()));
    assertThat(observation.path("completion").path("identity").path("producingTaskId").asText())
        .isEqualTo(actualProducingTaskId);
    assertThat(providerFactories).hasValue(factoriesBeforeTaskIndex);
    assertThat(model.requests).hasSize(2);

    TechnicalFixture relationFixture = prepareRealR4("private-relation-public-install-failure");
    ConfiguredTypedPipeline relationPipeline =
        writeTypedPipelineConfiguration(
            "ontology-private-relation-rejected-install.yaml", relationFixture);
    Path relationConfiguration =
        writePolicySetWithout(
            relationPipeline.path(),
            "ONTOLOGY_RELATIONS",
            temporaryDirectory.resolve("ontology-policy-without-relations.json"));
    AtomicInteger relationProviderFactories = new AtomicInteger();
    ExplicitTypedPipelineScript relationScript =
        new ExplicitTypedPipelineScript(relationPipeline.prompts());
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        relationProviderFactory =
            declaration -> {
              relationProviderFactories.incrementAndGet();
              return relationScript.provider(declaration);
            };

    CliResult relationPrepared =
        executeWithFactory(
            relationConfiguration,
            relationProviderFactory,
            "prepare-ontology",
            "--evidence-run",
            relationFixture.r4RunId());
    assertThat(relationPrepared.exitCode()).withFailMessage(relationPrepared.stderr()).isZero();
    String relationO0RunId = runId(relationPrepared);
    assertThat(relationProviderFactories).hasValue(0);
    assertArtifactAvailable(artifact(relationConfiguration, relationO0RunId, "ONTOLOGY_CORPUS"));

    Path relationScope =
        writeExplicitObjectScope(
            temporaryDirectory.resolve("private-relation-object-scope.json"), "Q1", "object-task");
    CliResult relationObject =
        executeWithFactory(
            relationConfiguration,
            relationProviderFactory,
            "identify-ontology",
            "--corpus-run",
            relationO0RunId,
            "--scope",
            relationScope.toString());
    assertThat(relationObject.exitCode()).withFailMessage(relationObject.stderr()).isZero();
    String relationO1RunId = runId(relationObject);
    assertThat(relationProviderFactories).hasValue(1);
    assertThat(relationScript.requests).hasSize(2);
    assertArtifactAvailable(
        artifact(relationConfiguration, relationO1RunId, "ONTOLOGY_IDENTIFICATION"));

    Path relationSelection =
        writeRelateSelection(
            temporaryDirectory.resolve("private-relation-selected.json"),
            relationO0RunId,
            relationO1RunId);
    CliResult failedRelation =
        executeWithFactory(
            relationConfiguration,
            relationProviderFactory,
            "relate-ontology",
            "--selection",
            relationSelection.toString());
    assertThat(failedRelation.exitCode()).isNotZero();
    assertThat(relationProviderFactories).hasValue(2);
    assertThat(relationScript.requests).hasSize(4);
    assertThat(relationScript.requests.get(2).taskKind()).contains("RELATE", "EXTRACT");
    assertThat(relationScript.requests.get(3).taskKind()).contains("RELATE", "REVIEW");
    assertThat(failedRelation.stdout()).isNotBlank();
    String relationO2RunId = runId(failedRelation);
    JsonNode relationFailureObservation =
        assertCommandAndInspectionAgree(
            failedRelation, relationConfiguration, "RELATE_ONTOLOGY", relationO2RunId, 2);
    assertThat(relationFailureObservation.path("lifecycleState").asText()).isEqualTo("FAILED");
    assertThat(relationFailureObservation.path("resultStatus").isNull()).isTrue();
    assertThat(relationFailureObservation.path("canContinue").asBoolean()).isFalse();
    assertThat(stringValues(relationFailureObservation.path("availableArtifactKeys")))
        .doesNotContain("ONTOLOGY_RELATIONS", "ONTOLOGY_TASK_INDEX");
    assertThat(arrayValues(relationFailureObservation.path("problems"))).isNotEmpty();

    CliResult absentRelations =
        artifact(relationConfiguration, relationO2RunId, "ONTOLOGY_RELATIONS");
    assertThat(absentRelations.exitCode()).isNotZero();
    assertThat(absentRelations.stdout()).isEmpty();
    CliResult relationTaskIndexResult =
        executePublic(
            relationConfiguration,
            "artifact",
            "--run",
            relationO2RunId,
            "--key",
            "ONTOLOGY_TASK_INDEX",
            "--max-bytes",
            "524288");
    assertArtifactAvailable(relationTaskIndexResult);
    JsonNode relationTaskIndex = JSON.readTree(relationTaskIndexResult.stdout());
    Set<String> relationIndexFields = new LinkedHashSet<>();
    relationTaskIndex.fieldNames().forEachRemaining(relationIndexFields::add);
    assertThat(relationIndexFields)
        .containsExactlyInAnyOrder("schemaVersion", "runId", "taskRecords");
    assertThat(relationTaskIndex.path("schemaVersion").asText())
        .isEqualTo("ontology-task-index-v1");
    assertThat(relationTaskIndex.path("runId").asText()).isEqualTo(relationO2RunId);
    List<JsonNode> indexedRelations = arrayValues(relationTaskIndex.path("taskRecords"));
    assertThat(indexedRelations).hasSize(1);
    JsonNode indexedRelation = indexedRelations.get(0);
    Set<String> indexedRelationFields = new LinkedHashSet<>();
    indexedRelation.fieldNames().forEachRemaining(indexedRelationFields::add);
    assertThat(indexedRelationFields)
        .containsExactlyInAnyOrder("taskId", "producingTaskId", "jobKey", "taskKind", "status");
    assertThat(indexedRelation.path("taskId").asText()).isEqualTo("relate-task");
    assertThat(indexedRelation.path("taskKind").asText()).isEqualTo("RELATE");
    assertThat(indexedRelation.path("status").asText()).isEqualTo("REVIEWED");
    String relationProducingTaskId = indexedRelation.path("producingTaskId").asText();
    String relationJobKey = indexedRelation.path("jobKey").asText();
    assertThat(relationProducingTaskId).isNotBlank();
    assertThat(relationJobKey).isNotBlank();
    CliResult relationTaskObservationResult =
        executePublic(
            relationConfiguration,
            "artifact",
            "--run",
            relationO2RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            relationProducingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(relationTaskObservationResult);
    JsonNode relationTaskObservation = JSON.readTree(relationTaskObservationResult.stdout());
    assertThat(relationTaskObservation.path("runId").asText()).isEqualTo(relationO2RunId);
    assertThat(relationTaskObservation.path("taskId").asText()).isEqualTo("relate-task");
    assertThat(relationTaskObservation.path("producingTaskId").asText())
        .isEqualTo(relationProducingTaskId);
    assertThat(relationTaskObservation.path("jobKey").asText()).isEqualTo(relationJobKey);
    assertThat(relationTaskObservation.path("taskKind").asText()).isEqualTo("RELATE");
    assertThat(relationTaskObservation.path("status").asText()).isEqualTo("REVIEWED");
    assertSavedFormalRequest(
        relationTaskObservation.path("extract").path("request"), relationScript.requests.get(2));
    assertSavedFormalRequest(
        relationTaskObservation.path("review").path("request"), relationScript.requests.get(3));
    assertSavedFormalResponseAndValidation(
        relationTaskObservation.path("extract"),
        formalTaskUnresolvedRelateResponse("ontology-typed-candidate-v3", "B1"),
        "VALID_CANDIDATE");
    assertSavedFormalResponseAndValidation(
        relationTaskObservation.path("review"),
        formalTaskUnresolvedRelateResponse("ontology-typed-review-v3", "B1"),
        "REVIEW_VALID");
    assertThat(
            relationTaskObservation
                .path("completion")
                .path("identity")
                .path("producingTaskId")
                .asText())
        .isEqualTo(relationProducingTaskId);
    assertThat(relationProviderFactories).hasValue(2);
    assertThat(relationScript.requests).hasSize(4);
  }

  @Test
  void correctedReviewProjectionPreservesActualTaskIdentityRuntimeAndReversibleEvidence()
      throws Exception {
    TechnicalFixture fixture = prepareRealR4("formal-correction-publication");
    Path configuration =
        writeTypedPipelineConfiguration("ontology-correction-publication.yaml", fixture).path();
    AtomicInteger providerFactories = new AtomicInteger();
    ImmutableBytes candidate = formalTaskObjectResponse("ontology-typed-candidate-v3", "Q1", "E1");
    ImmutableBytes correctedReview =
        formalTaskObjectCorrectionReview(
            "Q1",
            "E1",
            "Neutral reviewed record",
            "The reviewed neutral record remains source-backed.");
    ScriptedModel model = new ScriptedModel(candidate, correctedReview);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return model.provider(declaration.expectedRuntimeIdentity());
            };

    CliResult prepared =
        executeWithFactory(
            configuration,
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    Path scope = writeExplicitScope(temporaryDirectory.resolve("correction-scope.json"));
    CliResult identified =
        executeWithFactory(
            configuration,
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    String o1RunId = runId(identified);
    JsonNode identification =
        JSON.readTree(artifact(configuration, o1RunId, "ONTOLOGY_IDENTIFICATION").stdout());
    JsonNode task = findTaskRecord(identification, "object-task");
    String producingTaskId = task.path("producingTaskId").asText();
    CliResult taskRecord =
        executePublic(
            configuration,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            producingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(taskRecord);
    JsonNode observation = JSON.readTree(taskRecord.stdout());
    JsonNode actualCorrection =
        observation.path("completion").path("review").path("corrections").get(0);
    assertThat(actualCorrection.path("targetLocalId").asText()).isEqualTo("O1");
    assertThat(actualCorrection.path("changeKind").asText()).isEqualTo("CHANGED");
    assertThat(actualCorrection.path("reason").asText()).isNotBlank();
    assertThat(actualCorrection.path("evidenceRefs").get(0).asText()).isEqualTo("S1");

    CliResult related =
        executeWithFactory(
            configuration,
            providerFactory,
            "relate-ontology",
            "--selection",
            writeSelection(
                    temporaryDirectory.resolve("correction-zero-relate.json"),
                    "RELATE",
                    o0RunId,
                    List.of(o1RunId),
                    List.of(),
                    true)
                .toString());
    assertThat(related.exitCode()).withFailMessage(related.stderr()).isZero();
    String o2RunId = runId(related);
    int callsBeforeO3 = model.requests.size();
    CliResult published =
        executeWithFactory(
            configuration,
            providerFactory,
            "publish-ontology",
            "--selection",
            writeSelection(
                    temporaryDirectory.resolve("correction-publish.json"),
                    "PUBLISH",
                    o0RunId,
                    List.of(o1RunId),
                    List.of(o2RunId),
                    false)
                .toString());
    assertThat(published.exitCode()).withFailMessage(published.stderr()).isZero();
    String o3RunId = runId(published);
    assertThat(model.requests).hasSize(callsBeforeO3);

    CliResult reviewArtifact = artifact(configuration, o3RunId, "ONTOLOGY_REVIEW");
    CliResult sourceIndexArtifact = artifact(configuration, o3RunId, "ONTOLOGY_SOURCE_INDEX");
    assertArtifactAvailable(reviewArtifact);
    assertThat(sourceIndexArtifact.exitCode()).isZero();
    JsonNode publicReview = JSON.readTree(reviewArtifact.stdout());
    JsonNode projectedTask = findReviewTask(publicReview, producingTaskId);
    assertThat(projectedTask.path("reviewVersion").asText())
        .isEqualTo(observation.path("completion").path("identity").path("reviewVersion").asText());
    assertThat(projectedTask.path("extractRuntimeIdentity"))
        .isEqualTo(observation.path("extract").path("response").path("runtimeIdentity"));
    assertThat(projectedTask.path("reviewRuntimeIdentity"))
        .isEqualTo(observation.path("review").path("response").path("runtimeIdentity"));
    JsonNode projectedCorrection = projectedTask.path("corrections").get(0);
    assertThat(projectedCorrection.path("targetLocalId").asText()).isEqualTo("O1");
    assertThat(projectedCorrection.path("changeKind").asText()).isEqualTo("CHANGED");
    assertThat(projectedCorrection.path("reason").asText())
        .isEqualTo(actualCorrection.path("reason").asText());
    String projectedSourceRef = projectedCorrection.path("evidenceRefs").get(0).asText();
    assertThat(projectedSourceRef).startsWith("ontology-source:");
    JsonNode source = sourceIndexRow(sourceIndexArtifact.stdout(), "S1");
    assertThat(source.path("sourceRef").asText()).isEqualTo(projectedSourceRef);
    assertThat(source.path("packetId").asText())
        .isEqualTo(observation.path("completion").path("packetId").asText());
    assertThat(source.path("localRef").asText()).isEqualTo("S1");
    assertThat(providerFactories.get()).isPositive();
  }

  @Test
  void invalidScopeAndActualEnvelopeCapacityAreRejectedBeforeProviderFactory() throws Exception {
    TechnicalFixture fixture = prepareRealR4("preflight-before-provider");
    Path prompt = temporaryDirectory.resolve("preflight-object-prompt.txt");
    Files.writeString(
        prompt,
        "Neutral configured extraction prompt for the capacity check.",
        StandardCharsets.UTF_8);
    Path normalConfig =
        writeOntologyConfig("ontology-preflight.yaml", fixture, fixture.archive(), prompt, true);
    AtomicInteger providerFactories = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        forbiddenFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              throw new AssertionError(
                  "O0, rejected scope, and rejected envelope must precede Provider creation");
            };

    CliResult prepared =
        executeWithFactory(
            normalConfig,
            forbiddenFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    assertThat(providerFactories).hasValue(0);
    String o0RunId = runId(prepared);
    CliResult corpusBefore =
        executeWithFactory(
            normalConfig,
            forbiddenFactory,
            "artifact",
            "--run",
            o0RunId,
            "--key",
            "ONTOLOGY_CORPUS",
            "--max-bytes",
            "524288");
    assertArtifactAvailable(corpusBefore);
    JsonNode admittedCorpus = JSON.readTree(corpusBefore.stdout());
    assertThat(admittedCorpus.path("schemaVersion").asText()).isEqualTo("ontology-corpus-v1");
    assertThat(formalEntryRefs(admittedCorpus)).containsExactly("E1");

    Path unknownUnitScope =
        writeScopeWithUnknownUse(
            temporaryDirectory.resolve("unknown-unit-scope.json"), "E1", "U999");
    CliResult unknownUnit =
        executeWithFactory(
            normalConfig,
            forbiddenFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            unknownUnitScope.toString());
    assertThat(unknownUnit.exitCode()).isNotZero();
    assertThat(unknownUnit.stderr()).contains("ONTOLOGY_SCOPE_UNIT_USE_INVALID");
    assertThat(providerFactories).hasValue(0);

    Path unknownEntryScope =
        writeScopeWithUnknownUse(
            temporaryDirectory.resolve("unknown-entry-scope.json"), "E999", "U1");
    CliResult unknownEntry =
        executeWithFactory(
            normalConfig,
            forbiddenFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            unknownEntryScope.toString());
    assertThat(unknownEntry.exitCode()).isNotZero();
    assertThat(unknownEntry.stderr()).contains("ONTOLOGY_SCOPE_ENTRY_REF_INVALID");
    assertThat(providerFactories).hasValue(0);

    Path validScope = writeExplicitScope(temporaryDirectory.resolve("valid-tight-scope.json"));
    Path insufficientEnvelopeConfig =
        writeOntologyConfig(
            "ontology-tight-envelope.yaml", fixture, fixture.archive(), prompt, true, 100_001);
    CliResult insufficientEnvelope =
        executeWithFactory(
            insufficientEnvelopeConfig,
            forbiddenFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            validScope.toString());
    assertThat(insufficientEnvelope.exitCode()).isNotZero();
    assertThat(insufficientEnvelope.stderr()).contains("TOO_LARGE");
    assertThat(providerFactories).hasValue(0);

    CliResult corpusAfter =
        executeWithFactory(
            normalConfig,
            forbiddenFactory,
            "artifact",
            "--run",
            o0RunId,
            "--key",
            "ONTOLOGY_CORPUS",
            "--max-bytes",
            "524288");
    assertArtifactAvailable(corpusAfter);
    assertThat(corpusAfter.stdout()).isEqualTo(corpusBefore.stdout());
    assertThat(formalEntryRefs(JSON.readTree(corpusAfter.stdout()))).containsExactly("E1");
    assertThat(providerFactories).hasValue(0);
  }

  @Test
  void failedReviewRunAndItsSavedTaskStagesRemainQueryableByActualMembership() throws Exception {
    TechnicalFixture fixture = prepareRealR4("failed-review-query");
    Path prompt = temporaryDirectory.resolve("failed-review-object-prompt.txt");
    Files.writeString(
        prompt, "Neutral extraction prompt for saved failure observation.", StandardCharsets.UTF_8);
    Path ontologyConfig =
        writeOntologyConfig(
            "ontology-failed-review.yaml", fixture, fixture.archive(), prompt, true);
    AtomicInteger providerFactories = new AtomicInteger();
    List<StructuredModelRequest> dispatched = new ArrayList<>();
    String providerFailureCode = "TASK7_SCRIPTED_REVIEW_FAILURE";
    String privateDiagnostic = "ONTOLOGY_PRIVATE_PROVIDER_DIAGNOSTIC_SENTINEL";
    assertThat(privateDiagnostic).startsWith("ONTOLOGY_").isNotEqualTo(providerFailureCode);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return request -> {
                dispatched.add(request);
                if (dispatched.size() == 1) {
                  return new StructuredModelResponse(
                      candidate("ontology-typed-candidate-v3"),
                      declaration.expectedRuntimeIdentity());
                }
                if (dispatched.size() == 2) {
                  throw new StructuredModelProviderFailure(
                      providerFailureCode,
                      true,
                      true,
                      privateDiagnostic,
                      null,
                      ImmutableBytes.copyOf(privateDiagnostic.getBytes(StandardCharsets.UTF_8)));
                }
                throw new AssertionError("a failed REVIEW must not be retried");
              };
            };

    CliResult prepared =
        executeWithFactory(
            ontologyConfig,
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    assertThat(providerFactories).hasValue(0);

    Path explicitScope = writeExplicitScope(temporaryDirectory.resolve("failed-review-scope.json"));
    CliResult failed =
        executeWithFactory(
            ontologyConfig,
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            explicitScope.toString());
    assertThat(failed.exitCode()).isNotZero();
    assertThat(failed.stdout()).isNotBlank();
    assertThat(failed.stdout()).doesNotContain(privateDiagnostic);
    assertThat(failed.stdout()).contains(providerFailureCode);
    assertThat(failed.stderr()).doesNotContain(privateDiagnostic);
    String o1RunId = runId(failed);
    JsonNode failedObservation =
        assertCommandAndInspectionAgree(failed, ontologyConfig, "IDENTIFY_ONTOLOGY", o1RunId, 2);
    assertThat(arrayValues(failedObservation.path("problems")))
        .anySatisfy(
            problem -> assertThat(problem.path("code").asText()).isEqualTo(providerFailureCode));
    CliResult failedInspection = executePublic(ontologyConfig, "inspect", "--run", o1RunId);
    assertThat(failedInspection.exitCode()).isZero();
    assertThat(failedInspection.stdout()).doesNotContain(privateDiagnostic);
    assertThat(failedInspection.stdout()).contains(providerFailureCode);
    JsonNode failedInspectionObservation = JSON.readTree(failedInspection.stdout());
    assertThat(arrayValues(failedInspectionObservation.path("problems")))
        .anySatisfy(
            problem -> assertThat(problem.path("code").asText()).isEqualTo(providerFailureCode));
    assertThat(o1RunId).isNotEqualTo(o0RunId);
    assertThat(providerFactories.get()).isPositive();
    assertThat(dispatched).hasSize(2);
    assertThat(dispatched.get(0).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(dispatched.get(1).taskKind()).contains("OBJECT", "REVIEW");

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      var inspection = new LocalRepositoryAnalysisAgent(store).inspect(o1RunId);
      assertThat(inspection.analysisRun().lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED)
          .isNotEqualTo(AnalysisRunLifecycleState.FINISHED);
      assertThat(inspection.output()).isNotNull();
      assertThat(inspection.output().ontologyOutput()).isNotNull();
      assertThat(inspection.output().ontologyOutput().operation())
          .isEqualTo(AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY);
      assertThat(inspection.output().ontologyOutput().status())
          .isIn(OntologyRunOutput.Status.PARTIAL, OntologyRunOutput.Status.BLOCKED);
    }

    CliResult identificationArtifact =
        executeWithFactory(
            ontologyConfig,
            providerFactory,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_IDENTIFICATION",
            "--max-bytes",
            "524288");
    assertArtifactAvailable(identificationArtifact);
    JsonNode identification = JSON.readTree(identificationArtifact.stdout());
    assertThat(identification.path("schemaVersion").asText())
        .isEqualTo("ontology-identification-v1");
    List<JsonNode> taskMemberships = new ArrayList<>();
    for (JsonNode taskRecord : identification.path("taskRecords")) {
      if ("object-task".equals(taskRecord.path("taskId").asText())) {
        taskMemberships.add(taskRecord);
      }
    }
    assertThat(taskMemberships).hasSize(1);
    JsonNode membership = taskMemberships.get(0);
    String producingTaskId = membership.path("producingTaskId").asText();
    String jobKey = membership.path("jobKey").asText();
    assertThat(producingTaskId).isNotBlank();
    assertThat(jobKey).isNotBlank();
    assertThat(membership.path("taskKind").asText()).isEqualTo("OBJECT");
    assertThat(membership.path("status").asText()).isNotBlank();

    int factoriesAfterFailure = providerFactories.get();
    CliResult taskObservationResult =
        executeWithFactory(
            ontologyConfig,
            providerFactory,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            producingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(taskObservationResult);
    JsonNode taskObservation = JSON.readTree(taskObservationResult.stdout());
    assertThat(taskObservation.path("schemaVersion").asText())
        .isEqualTo("ontology-task-observation-v1");
    assertThat(taskObservation.path("runId").asText()).isEqualTo(o1RunId);
    assertThat(taskObservation.path("taskId").asText()).isEqualTo("object-task");
    assertThat(taskObservation.path("producingTaskId").asText()).isEqualTo(producingTaskId);
    assertThat(taskObservation.path("jobKey").asText()).isEqualTo(jobKey);
    assertThat(taskObservation.path("taskKind").asText()).isEqualTo("OBJECT");
    assertThat(taskObservation.path("status").asText())
        .isEqualTo(membership.path("status").asText());

    JsonNode extract = taskObservation.path("extract");
    assertThat(extract.path("request").isObject()).isTrue();
    assertThat(extract.path("response").isObject()).isTrue();
    assertThat(extract.path("validation").path("disposition").asText())
        .isEqualTo("VALID_CANDIDATE");
    assertSavedFormalRequest(extract.path("request"), dispatched.get(0));
    assertSavedFormalResponseAndValidation(
        extract, candidate("ontology-typed-candidate-v3"), "VALID_CANDIDATE");
    assertThat(extract.path("outcome").isNull() || extract.path("outcome").isMissingNode())
        .isTrue();

    JsonNode reviewStage = taskObservation.path("review");
    assertThat(reviewStage.path("request").isObject()).isTrue();
    assertSavedFormalRequest(reviewStage.path("request"), dispatched.get(1));
    assertThat(
            reviewStage.path("response").isNull() || reviewStage.path("response").isMissingNode())
        .isTrue();
    assertThat(
            reviewStage.path("validation").isNull()
                || reviewStage.path("validation").isMissingNode())
        .isTrue();
    JsonNode reviewOutcome = reviewStage.path("outcome");
    assertThat(reviewOutcome.path("dispatchState").asText()).isEqualTo("FAILED");
    assertThat(reviewOutcome.path("reasonCode").asText()).isEqualTo(providerFailureCode);
    assertThat(reviewOutcome.path("requestStarted").asBoolean()).isTrue();
    assertThat(reviewOutcome.path("requestEnded").asBoolean()).isTrue();
    assertThat(reviewOutcome.path("privateFailureOutputStored").asBoolean()).isTrue();
    assertThat(reviewOutcome.has("rawResponseBase64")).isFalse();
    assertThat(taskObservation.path("failure").path("failureCode").asText())
        .isEqualTo(providerFailureCode);
    assertThat(
            taskObservation.path("completion").isNull()
                || taskObservation.path("completion").isMissingNode())
        .isTrue();
    assertThat(taskObservationResult.stdout()).doesNotContain(privateDiagnostic);

    CliResult missingTaskId =
        executeWithFactory(
            ontologyConfig,
            providerFactory,
            "artifact",
            "--run",
            o1RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--max-bytes",
            "524288");
    assertThat(missingTaskId.exitCode()).isNotZero();

    CliResult taskFromAnotherRun =
        executeWithFactory(
            ontologyConfig,
            providerFactory,
            "artifact",
            "--run",
            o0RunId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            producingTaskId,
            "--max-bytes",
            "524288");
    assertThat(taskFromAnotherRun.exitCode()).isNotZero();
    assertThat(providerFactories).hasValue(factoriesAfterFailure);
    assertThat(dispatched).hasSize(2);
  }

  @Test
  void modelDiscoverySelectionFeedsV2RelationObjectSourceConsumer() throws Exception {
    TechnicalFixture fixture = prepareRealR4("formal-discovery-chain");
    ConfiguredDiscovery configured =
        writeFormalDiscoveryConfigurationV2(
            "ontology-formal-discovery.yaml", fixture, fixture.archive(), 524288);
    AtomicInteger providerFactories = new AtomicInteger();
    FormalDiscoveryScript script = new FormalDiscoveryScript(configured.prompts(), false);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return script.provider(declaration.expectedRuntimeIdentity());
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    assertThat(providerFactories).hasValue(0);
    assertThat(script.requests).isEmpty();
    String o0RunId = runId(prepared);
    CliResult corpusArtifact = artifact(configured.path(), o0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(corpusArtifact);
    JsonNode corpus = JSON.readTree(corpusArtifact.stdout());
    assertThat(formalEntryRefs(corpus)).containsExactly("E1");

    Path discoveryScope =
        writeModelDiscoveryScope(temporaryDirectory.resolve("formal-discovery-scope.json"));
    JsonNode originalScope =
        CANONICAL.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(discoveryScope)));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            discoveryScope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    String o1RunId = runId(identified);
    assertThat(providerFactories.get()).isPositive();
    assertThat(script.requests).hasSize(6);
    assertThat(script.requests.get(0).taskKind()).contains("SURVEY");
    assertThat(script.requests.get(1).taskKind()).contains("PRIORITIZE");
    assertThat(script.requests.get(2).taskKind()).contains("READING");
    assertThat(script.requests.get(3).taskKind()).contains("READING");
    assertThat(script.requests.get(4).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(script.requests.get(5).taskKind()).contains("OBJECT", "REVIEW");

    JsonNode surveyInput = script.inputAt(0);
    assertThat(surveyInput.path("schemaVersion").asText()).isEqualTo("ontology-survey-input-v3");
    assertThat(script.shortRefs(surveyInput, "E[1-9][0-9]*")).contains(script.entryRef);
    assertThat(script.shortRefs(surveyInput, "K[1-9][0-9]*")).contains(script.clueRef);
    assertThat(script.shortRefs(surveyInput, "U[1-9][0-9]*")).isNotEmpty();
    assertThat(hasField(surveyInput, "viewId")).isFalse();
    assertThat(hasField(surveyInput, "corpusIdentity")).isFalse();
    assertThat(hasField(surveyInput, "sourceIdentity")).isFalse();
    assertThat(hasField(surveyInput, "taskId")).isFalse();
    assertThat(containsText(surveyInput, "return \"neutral\"")).isFalse();
    assertThat(script.requests.get(0).systemInstructions())
        .isEqualTo(configured.prompts().get("survey"));

    JsonNode priorityInput = script.inputAt(1);
    assertThat(priorityInput.path("schemaVersion").asText())
        .isEqualTo("ontology-prioritize-input-v4");
    assertThat(textFieldValues(priorityInput, "questionRef")).contains(script.questionRef);
    assertThat(script.shortRefs(priorityInput, "E[1-9][0-9]*")).contains(script.entryRef);
    assertThat(script.shortRefs(priorityInput, "K[1-9][0-9]*")).contains(script.clueRef);
    assertThat(containsText(priorityInput, "return \"neutral\"")).isFalse();
    assertThat(script.requests.get(1).systemInstructions())
        .isEqualTo(configured.prompts().get("prioritize"));

    JsonNode firstReadingInput = script.inputAt(2);
    JsonNode secondReadingInput = script.inputAt(3);
    assertThat(firstReadingInput.path("schemaVersion").asText())
        .isEqualTo("ontology-reading-input-v3");
    assertThat(secondReadingInput.path("schemaVersion").asText())
        .isEqualTo("ontology-reading-input-v3");
    assertThat(firstReadingInput.has("maxUnitBytes")).isFalse();
    assertThat(firstReadingInput.has("maxRequestBytes")).isFalse();
    assertThat(secondReadingInput.has("maxUnitBytes")).isFalse();
    assertThat(secondReadingInput.has("maxRequestBytes")).isFalse();
    assertThat(containsText(firstReadingInput, "return \"neutral\"")).isFalse();
    assertThat(containsText(secondReadingInput, "return \"neutral\"")).isTrue();
    assertThat(script.shortRefs(secondReadingInput, "U[1-9][0-9]*")).contains(script.readUnitRef);
    assertThat(script.shortRefs(secondReadingInput, "E[1-9][0-9]*")).contains(script.entryRef);
    assertThat(script.requests.get(2).systemInstructions())
        .isEqualTo(configured.prompts().get("reading"));
    assertThat(script.requests.get(3).systemInstructions())
        .isEqualTo(configured.prompts().get("reading"));
    assertThat(containsText(script.inputAt(4), "return \"neutral\"")).isTrue();
    assertThat(containsText(script.inputAt(5), "return \"neutral\"")).isTrue();
    assertThat(script.inputAt(4).path("schemaVersion").asText())
        .isEqualTo("ontology-typed-formal-input-v3");
    assertThat(script.inputAt(5).path("schemaVersion").asText())
        .isEqualTo("ontology-typed-formal-input-v3");
    assertThat(script.inputAt(4).path("questionId").asText()).isEqualTo(script.questionRef);
    assertThat(script.inputAt(4).path("readingPacket"))
        .isEqualTo(script.inputAt(5).path("readingPacket"));
    assertThat(script.requests.get(4).systemInstructions())
        .isEqualTo(configured.prompts().get("object"));
    assertThat(script.requests.get(5).systemInstructions())
        .isEqualTo(configured.prompts().get("review"));

    CliResult identificationArtifact =
        artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identificationArtifact);
    JsonNode identification = JSON.readTree(identificationArtifact.stdout());
    assertThat(identification.path("schemaVersion").asText())
        .isEqualTo("ontology-identification-v2");
    assertThat(identification.path("scope")).isEqualTo(originalScope);
    assertThat(identification.path("scope").path("mode").asText()).isEqualTo("DISCOVERY");
    assertThat(identification.path("scope").path("selectionMode").asText()).isEqualTo("MODEL");
    assertThat(identification.path("scope").path("questions")).isEmpty();

    assertThat(identification.path("selectedQuestions")).hasSize(1);
    JsonNode selectedQuestion = identification.path("selectedQuestions").get(0);
    Set<String> selectedQuestionFields = new LinkedHashSet<>();
    selectedQuestion.fieldNames().forEachRemaining(selectedQuestionFields::add);
    assertThat(selectedQuestionFields)
        .containsExactlyInAnyOrder("questionId", "question", "entryRefs", "clueRefs", "tasks");
    assertThat(selectedQuestion.path("questionId").asText()).isEqualTo(script.questionRef);
    assertThat(selectedQuestion.path("question").asText())
        .isEqualTo("What record-shaped value is supported by this selected source?");
    assertThat(stringValues(selectedQuestion.path("entryRefs"))).containsExactly(script.entryRef);
    assertThat(stringValues(selectedQuestion.path("clueRefs"))).containsExactly(script.clueRef);
    assertThat(selectedQuestion.path("tasks")).hasSize(1);
    JsonNode selectedTask = selectedQuestion.path("tasks").get(0);
    Set<String> selectedTaskFields = new LinkedHashSet<>();
    selectedTask.fieldNames().forEachRemaining(selectedTaskFields::add);
    assertThat(selectedTaskFields)
        .containsExactlyInAnyOrder(
            "taskId", "taskKind", "readingMode", "unitUses", "requiredUnitUses");
    assertThat(selectedTask.path("taskKind").asText()).isEqualTo("OBJECT");
    assertThat(selectedTask.path("readingMode").asText()).isEqualTo("MODEL");
    assertThat(selectedTask.path("taskId").asText()).isNotBlank();
    assertThat(arrayValues(selectedTask.path("unitUses")))
        .anySatisfy(
            use -> {
              assertThat(use.path("unitRef").asText()).isEqualTo(script.readUnitRef);
              assertThat(use.path("entryRef").asText()).isEqualTo(script.entryRef);
            });

    JsonNode discovery = identification.path("discovery");
    Set<String> discoveryFields = new LinkedHashSet<>();
    discovery.fieldNames().forEachRemaining(discoveryFields::add);
    assertThat(discoveryFields)
        .containsExactlyInAnyOrder(
            "surveyPages",
            "priorityJobKey",
            "questions",
            "selectedQuestionRefs",
            "deferredQuestions");
    assertThat(discovery.path("surveyPages")).hasSize(1);
    JsonNode surveyPage = discovery.path("surveyPages").get(0);
    assertThat(surveyPage.path("pageIndex").asInt()).isEqualTo(1);
    String surveyJobKey = surveyPage.path("jobKey").asText();
    String priorityJobKey = discovery.path("priorityJobKey").asText();
    assertThat(surveyJobKey).isNotBlank();
    assertThat(priorityJobKey).isNotBlank();
    assertThat(stringValues(surveyPage.path("entryRefs"))).contains(script.entryRef);
    assertThat(stringValues(surveyPage.path("clueRefs"))).contains(script.clueRef);
    assertThat(discovery.path("questions")).hasSize(1);
    JsonNode questionMapping = discovery.path("questions").get(0);
    Set<String> questionMappingFields = new LinkedHashSet<>();
    questionMapping.fieldNames().forEachRemaining(questionMappingFields::add);
    assertThat(questionMappingFields)
        .containsExactlyInAnyOrder(
            "questionRef",
            "surveyJobKey",
            "questionId",
            "question",
            "candidateEntryRefs",
            "clueRefs");
    assertThat(questionMapping.path("questionRef").asText()).isEqualTo(script.questionRef);
    assertThat(questionMapping.path("surveyJobKey").asText()).isEqualTo(surveyJobKey);
    assertThat(questionMapping.path("questionId").asText()).isEqualTo("Q1");
    assertThat(questionMapping.path("question").asText())
        .isEqualTo("What record-shaped value is supported by this selected source?");
    assertThat(stringValues(questionMapping.path("candidateEntryRefs")))
        .containsExactly(script.entryRef);
    assertThat(stringValues(questionMapping.path("clueRefs"))).containsExactly(script.clueRef);
    assertThat(stringValues(discovery.path("selectedQuestionRefs")))
        .containsExactly(script.questionRef);
    assertThat(discovery.path("deferredQuestions")).isEmpty();

    PrivateModelJobResultStore decisionStore =
        new PrivateModelJobResultStore(
            fixture.runStore().resolve("ontology-journal"),
            AnalysisRunId.parse(o1RunId),
            "ontology");
    JsonNode savedSurvey = decisionStore.readActivityReadingPlan(surveyJobKey).orElseThrow();
    JsonNode savedPriority = decisionStore.readActivityReadingPlan(priorityJobKey).orElseThrow();
    assertThat(savedSurvey.path("jobKey").asText()).isEqualTo(surveyJobKey);
    assertThat(savedSurvey.path("input").path("schemaVersion").asText())
        .isEqualTo("ontology-survey-input-v3");
    assertThat(savedSurvey.path("validatedResponse").path("schemaVersion").asText())
        .isEqualTo("ontology-survey-response-v3");
    assertThat(savedPriority.path("jobKey").asText()).isEqualTo(priorityJobKey);
    assertThat(savedPriority.path("input").path("schemaVersion").asText())
        .isEqualTo("ontology-prioritize-input-v4");
    assertThat(savedPriority.path("validatedResponse").path("schemaVersion").asText())
        .isEqualTo("ontology-prioritize-response-v4");

    assertThat(identification.path("taskRecords")).hasSize(1);
    JsonNode taskRecord = identification.path("taskRecords").get(0);
    assertThat(taskRecord.path("taskKind").asText()).isEqualTo("OBJECT");
    assertThat(selectedTask.path("taskId").asText()).isEqualTo(taskRecord.path("taskId").asText());
    assertThat(taskRecord.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(taskRecord.path("producingTaskId").asText()).isNotBlank();
    assertThat(taskRecord.path("jobKey").asText()).isNotBlank();
    assertThat(identification.path("taskOutcomes")).hasSize(1);
    JsonNode selectedOutcome = identification.path("taskOutcomes").get(0);
    assertThat(selectedOutcome.path("questionId").asText()).isEqualTo(script.questionRef);
    assertThat(selectedOutcome.path("taskId").asText())
        .isEqualTo(selectedTask.path("taskId").asText());
    assertThat(selectedOutcome.path("taskKind").asText()).isEqualTo("OBJECT");
    assertThat(selectedOutcome.path("status").asText()).isEqualTo("REVIEWED");
    assertThat(selectedOutcome.path("producingTaskId").asText())
        .isEqualTo(taskRecord.path("producingTaskId").asText());

    String selectedQuestionId = selectedQuestion.path("questionId").asText();
    String selectedTaskId = selectedTask.path("taskId").asText();
    ConfiguredTypedPipeline relateConfigured =
        writeTypedPipelineConfigurationV2("formal-discovery-selected-question-o2-v2.yaml", fixture);
    List<StructuredModelRequest> relateRequests = new ArrayList<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        relateProviderFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                relateRequests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                assertThat(input.path("taskKind").asText()).isEqualTo("RELATE");
                JsonNode selectedObject =
                    ExplicitTypedPipelineScript.catalogEntry(input, "objects");
                String responseSchema =
                    request.taskKind().contains("REVIEW")
                        ? "ontology-typed-review-v3"
                        : "ontology-typed-candidate-v3";
                return new StructuredModelResponse(
                    formalTaskUnresolvedRelateResponse(
                        responseSchema, selectedObject.path("catalogRef").asText()),
                    identity);
              };
            };
    Path relateSelection =
        writeRelateSelectionV2(
            temporaryDirectory.resolve("formal-discovery-selected-question-relate-v2.json"),
            o0RunId,
            List.of(o1RunId),
            new SelectedRelateQuestion(
                "Q1",
                "discovery-relate-task",
                List.of(new ObjectSource(o1RunId, selectedQuestionId))));
    CliResult related =
        executeWithFactory(
            relateConfigured.path(),
            relateProviderFactory,
            "relate-ontology",
            "--selection",
            relateSelection.toString());
    assertThat(related.exitCode()).withFailMessage(related.stderr()).isIn(0, 2);
    assertThat(related.stdout()).isNotBlank();
    String o2RunId = runId(related);
    assertThat(o2RunId).isNotBlank();
    assertThat(relateRequests)
        .extracting(
            request -> {
              JsonNode input = ExplicitTypedPipelineScript.input(request);
              return input.path("questionId").asText()
                  + "/"
                  + input.path("taskKind").asText()
                  + "/"
                  + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT");
            })
        .containsExactly("Q1/RELATE/EXTRACT", "Q1/RELATE/REVIEW");
    JsonNode relateExtractInput = ExplicitTypedPipelineScript.input(relateRequests.get(0));
    JsonNode relateReviewInput = ExplicitTypedPipelineScript.input(relateRequests.get(1));
    JsonNode restoredObject =
        ExplicitTypedPipelineScript.catalogEntry(relateExtractInput, "objects");
    assertThat(restoredObject.path("definition").path("name").asText())
        .isEqualTo("Neutral source-backed record");
    assertThat(relateExtractInput.path("readingPacket"))
        .isEqualTo(relateReviewInput.path("readingPacket"));
    assertThat(relateExtractInput.path("reviewedCatalog"))
        .isEqualTo(relateReviewInput.path("reviewedCatalog"));

    CliResult relationArtifact = artifact(relateConfigured.path(), o2RunId, "ONTOLOGY_RELATIONS");
    assertArtifactAvailable(relationArtifact);
    JsonNode relations = JSON.readTree(relationArtifact.stdout());
    JsonNode relateOutcome = arrayValues(relations.path("taskOutcomes")).get(0);
    assertThat(relateOutcome.path("taskId").asText()).isEqualTo("discovery-relate-task");
    assertThat(relateOutcome.path("status").asText()).isEqualTo("REVIEWED");
    JsonNode dependency = arrayValues(relateOutcome.path("dependencyTaskRefs")).get(0);
    assertThat(dependency.path("runId").asText()).isEqualTo(o1RunId);
    assertThat(dependency.path("questionId").asText()).isEqualTo(selectedQuestionId);
    assertThat(dependency.path("taskId").asText()).isEqualTo(selectedTaskId);
  }

  @Test
  void unknownPrioritizedQuestionIsRejectedAfterTwoDispatchesBeforeTypedWork() throws Exception {
    TechnicalFixture fixture = prepareRealR4("formal-discovery-invalid-priority");
    ConfiguredDiscovery configured =
        writeFormalDiscoveryConfiguration(
            "ontology-invalid-priority.yaml", fixture, fixture.archive(), 524288);
    AtomicInteger providerFactories = new AtomicInteger();
    FormalDiscoveryScript script = new FormalDiscoveryScript(configured.prompts(), true);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return script.provider(declaration.expectedRuntimeIdentity());
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    Path discoveryScope =
        writeModelDiscoveryScope(temporaryDirectory.resolve("invalid-priority-scope.json"));
    CliResult refused =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            discoveryScope.toString());

    assertThat(refused.exitCode()).isNotZero();
    assertThat(script.requests).hasSize(2);
    assertThat(script.requests.get(0).taskKind()).contains("SURVEY");
    assertThat(script.requests.get(1).taskKind()).contains("PRIORITIZE");
    assertThat(script.availableQuestionRefs).doesNotContain("Q999");
    assertThat(script.invalidPriorityRef).isEqualTo("Q999");

    String failedO1RunId = runId(refused);
    JsonNode failureObservation =
        assertCommandAndInspectionAgree(
            refused, configured.path(), "IDENTIFY_ONTOLOGY", failedO1RunId, 2);
    assertThat(stringValues(failureObservation.path("availableArtifactKeys")))
        .doesNotContain("ONTOLOGY_TASK_INDEX");
    int factoriesAfterFailure = providerFactories.get();
    CliResult taskIndexResult = artifact(configured.path(), failedO1RunId, "ONTOLOGY_TASK_INDEX");
    assertArtifactAvailable(taskIndexResult);
    JsonNode taskIndex = JSON.readTree(taskIndexResult.stdout());
    Set<String> indexFields = new LinkedHashSet<>();
    taskIndex.fieldNames().forEachRemaining(indexFields::add);
    assertThat(indexFields).containsExactlyInAnyOrder("schemaVersion", "runId", "taskRecords");
    assertThat(taskIndex.path("schemaVersion").asText()).isEqualTo("ontology-task-index-v1");
    assertThat(taskIndex.path("runId").asText()).isEqualTo(failedO1RunId);
    assertThat(taskIndex.path("taskRecords")).isEmpty();
    assertThat(providerFactories.get()).isEqualTo(factoriesAfterFailure);
    assertThat(script.requests).hasSize(2);
  }

  @Test
  void businessLinkSkeletonDiscoveryKeepsPurposeAndRejectsPrioritizedAction() throws Exception {
    TechnicalFixture fixture = prepareRealR4("business-link-skeleton-discovery");
    ConfiguredDiscovery configured =
        writeBusinessLinkDiscoveryConfigurationV3(
            "ontology-business-link-skeleton-discovery.yaml", fixture);
    Path scope =
        writeBusinessLinkSkeletonDiscoveryScopeV2(
            temporaryDirectory.resolve("business-link-skeleton-discovery-scope-v2.json"));
    CliResult prepared =
        executePublic(configured.path(), "prepare-ontology", "--evidence-run", fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    String o0RunId = runId(prepared);
    CliResult corpus = artifact(configured.path(), o0RunId, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(corpus);
    assertThat(JSON.readTree(corpus.stdout()).path("schemaVersion").asText())
        .isEqualTo("ontology-corpus-v2");

    FormalDiscoveryScript objectOnly =
        new FormalDiscoveryScript(configured.prompts(), false, true, false);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        objectOnlyFactory =
            declaration -> objectOnly.provider(declaration.expectedRuntimeIdentity());
    CliResult identified =
        executeWithFactory(
            configured.path(),
            objectOnlyFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(cliDiagnostics(identified)).isZero();
    assertThat(objectOnly.requests).hasSize(6);
    JsonNode priorityInput = objectOnly.inputAt(1);
    assertThat(priorityInput.path("purpose").asText()).isEqualTo("SKELETON");
    JsonNode prioritySchema =
        CANONICAL.parseCanonical(objectOnly.requests.get(1).outputJsonSchema());
    JsonNode taskKindsSchema =
        prioritySchema.path("$defs").path("selected").path("properties").path("taskKinds");
    List<String> allowedTaskKinds = new ArrayList<>();
    taskKindsSchema
        .path("items")
        .path("enum")
        .forEach(value -> allowedTaskKinds.add(value.asText()));
    assertThat(allowedTaskKinds).containsExactly("OBJECT");
    assertThat(taskKindsSchema.path("maxItems").asInt()).isEqualTo(1);
    assertThat(
            objectOnly.requests.stream()
                .map(StructuredModelRequest::taskKind)
                .noneMatch(kind -> kind.contains("ACTION") || kind.contains("ANALYTIC")))
        .isTrue();
    String o1RunId = runId(identified);
    CliResult identificationArtifact =
        artifact(configured.path(), o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identificationArtifact);
    JsonNode identification = JSON.readTree(identificationArtifact.stdout());
    assertThat(identification.path("scope").path("schemaVersion").asText())
        .isEqualTo("ontology-scope-v2");
    assertThat(identification.path("scope").path("purpose").asText()).isEqualTo("SKELETON");
    JsonNode selectedQuestions = identification.path("selectedQuestions");
    assertThat(selectedQuestions).hasSize(1);
    assertThat(selectedQuestions.get(0).path("tasks")).hasSize(1);
    assertThat(selectedQuestions.get(0).path("tasks").get(0).path("taskKind").asText())
        .isEqualTo("OBJECT");
    assertThat(identification.path("taskOutcomes").get(0).path("status").asText())
        .isEqualTo("REVIEWED");

    FormalDiscoveryScript actionPriority =
        new FormalDiscoveryScript(configured.prompts(), false, true, true);
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        actionPriorityFactory =
            declaration -> actionPriority.provider(declaration.expectedRuntimeIdentity());
    CliResult refused =
        executeWithFactory(
            configured.path(),
            actionPriorityFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(refused.exitCode()).isNotZero();
    assertThat(refused.stderr()).isEmpty();
    String refusedRunId = runId(refused);
    JsonNode refusalReport =
        assertCommandAndInspectionAgree(
            refused, configured.path(), "IDENTIFY_ONTOLOGY", refusedRunId, 2);
    assertThat(arrayValues(refusalReport.path("problems")))
        .anySatisfy(
            problem -> {
              assertThat(problem.path("code").asText()).isEqualTo("ONTOLOGY_DECISION_INVALID");
              assertThat(problem.path("category").asText()).isEqualTo("MODEL_OUTPUT");
              assertThat(problem.path("stage").asText()).isEqualTo("PRIORITIZE");
            });
    assertThat(actionPriority.requests).hasSize(2);
    assertThat(actionPriority.requests.get(0).taskKind()).contains("SURVEY");
    assertThat(actionPriority.requests.get(1).taskKind()).contains("PRIORITIZE");
  }

  @Test
  void oversizedFormalSurveyEnvelopeIsRejectedBeforeProviderFactory() throws Exception {
    TechnicalFixture fixture = prepareRealR4("formal-survey-preflight");
    ConfiguredDiscovery configured =
        writeFormalDiscoveryConfiguration(
            "ontology-formal-survey-tight.yaml", fixture, fixture.archive(), 100001);
    AtomicInteger providerFactories = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        forbiddenFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return request -> {
                throw new AssertionError(
                    "survey request must be rejected before Provider dispatch");
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            forbiddenFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    Path discoveryScope =
        writeModelDiscoveryScope(temporaryDirectory.resolve("tight-survey-scope.json"));
    CliResult refused =
        executeWithFactory(
            configured.path(),
            forbiddenFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            discoveryScope.toString());

    assertThat(refused.exitCode()).isNotZero();
    assertThat(refused.stderr()).contains("TOO_LARGE");
    assertThat(providerFactories).hasValue(0);
  }

  @Test
  void explicitQuestionCanUseModelReadingBeforeItsTypedObjectPair() throws Exception {
    TechnicalFixture fixture = prepareRealR4("question-model-reading");
    Path configuration =
        writeOntologyConfig(
            "ontology-question-model-reading.yaml", fixture, fixture.archive(), null, true);
    CliResult prepared =
        executePublic(configuration, "prepare-ontology", "--evidence-run", fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    JsonNode corpus = JSON.readTree(artifact(configuration, o0RunId, "ONTOLOGY_CORPUS").stdout());
    String entryRef = entryRef(corpus);
    String methodRef = unitRef(corpus, "JAVA_METHOD", "method:neutral-list");
    Path scope =
        writeSingleUnitObjectScope(
            temporaryDirectory.resolve("question-model-reading-scope.json"),
            "Q1",
            "object-task",
            entryRef,
            methodRef,
            "MODEL");
    JsonNode originalScope =
        CANONICAL.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(scope)));
    AtomicInteger providerFactories = new AtomicInteger();
    ScriptedModel model =
        new ScriptedModel(
            formalReadingResponse("NEEDS_MORE_MATERIAL", methodRef, entryRef, false),
            formalReadingResponse("READY_TO_EXTRACT", methodRef, entryRef, true),
            formalTaskObjectResponse("ontology-typed-candidate-v3", "Q1", entryRef),
            formalTaskObjectResponse("ontology-typed-review-v3", "Q1", entryRef));
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider> factory =
        declaration -> {
          providerFactories.incrementAndGet();
          return model.provider(declaration.expectedRuntimeIdentity());
        };

    CliResult identified =
        executeWithFactory(
            configuration,
            factory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    assertThat(model.requests).hasSize(4);
    assertThat(providerFactories.get()).isGreaterThan(0);
    assertThat(model.requests.get(0).taskKind()).contains("READING");
    assertThat(model.requests.get(1).taskKind()).contains("READING");
    assertThat(model.requests.get(2).taskKind()).contains("OBJECT", "EXTRACT");
    assertThat(model.requests.get(3).taskKind()).contains("OBJECT", "REVIEW");
    for (StructuredModelRequest request : model.requests) {
      assertThat(request.taskKind()).doesNotContain("SURVEY", "PRIORITIZE");
    }
    JsonNode firstReadingInput = modelInput(model.requests.get(0));
    JsonNode secondReadingInput = modelInput(model.requests.get(1));
    assertThat(firstReadingInput.path("schemaVersion").asText())
        .isEqualTo("ontology-reading-input-v3");
    assertThat(secondReadingInput.path("schemaVersion").asText())
        .isEqualTo("ontology-reading-input-v3");
    assertThat(matchingTextValues(firstReadingInput, "U[1-9][0-9]*")).contains(methodRef);
    assertThat(matchingTextValues(firstReadingInput, "E[1-9][0-9]*")).contains(entryRef);
    assertThat(containsText(firstReadingInput, "return \"neutral\"")).isFalse();
    assertThat(containsText(secondReadingInput, "return \"neutral\"")).isTrue();
    JsonNode extractInput = modelInput(model.requests.get(2));
    JsonNode reviewInput = modelInput(model.requests.get(3));
    assertThat(extractInput.path("schemaVersion").asText())
        .isEqualTo("ontology-typed-formal-input-v3");
    assertThat(extractInput.path("questionId").asText()).isEqualTo("Q1");
    assertThat(containsText(extractInput, "return \"neutral\"")).isTrue();
    assertThat(reviewInput.path("readingPacket")).isEqualTo(extractInput.path("readingPacket"));

    String o1RunId = runId(identified);
    CliResult identificationResult = artifact(configuration, o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identificationResult);
    JsonNode identification = JSON.readTree(identificationResult.stdout());
    assertThat(identification.path("scope")).isEqualTo(originalScope);
    assertThat(identification.path("scope").path("mode").asText()).isEqualTo("QUESTION");
    assertThat(identification.path("scope").path("selectionMode").asText()).isEqualTo("EXPLICIT");
    assertThat(
            identification
                .path("scope")
                .path("questions")
                .get(0)
                .path("tasks")
                .get(0)
                .path("readingMode")
                .asText())
        .isEqualTo("MODEL");
    assertThat(identification.path("discovery").isMissingNode()).isTrue();
    assertThat(findTaskRecord(identification, "object-task").path("status").asText())
        .isEqualTo("REVIEWED");
  }

  @Test
  void unconfiguredSameR0DdlKeepsDisabledO0AndExistingPacketAliasesStable() throws Exception {
    TechnicalFixture fixture =
        prepareRealR4WithDdl(
            "ddl-disabled-o0",
            Map.of(DDL_SOURCE_PATH, DDL_TEXT.getBytes(StandardCharsets.UTF_8)),
            List.of());
    Path configuration =
        writeOntologyConfigWithSchemaSources(
            "ontology-ddl-disabled.yaml", fixture, List.of(), true, 1_048_576);
    CliResult firstPrepared =
        executePublic(configuration, "prepare-ontology", "--evidence-run", fixture.r4RunId());
    assertThat(firstPrepared.exitCode()).withFailMessage(firstPrepared.stderr()).isZero();
    String firstO0 = runId(firstPrepared);
    CliResult secondPrepared =
        executePublic(configuration, "prepare-ontology", "--evidence-run", fixture.r4RunId());
    assertThat(secondPrepared.exitCode()).withFailMessage(secondPrepared.stderr()).isZero();
    String secondO0 = runId(secondPrepared);

    CliResult firstCorpusResult = artifact(configuration, firstO0, "ONTOLOGY_CORPUS");
    CliResult secondCorpusResult = artifact(configuration, secondO0, "ONTOLOGY_CORPUS");
    assertArtifactAvailable(firstCorpusResult);
    assertArtifactAvailable(secondCorpusResult);
    JsonNode firstCorpus = JSON.readTree(firstCorpusResult.stdout());
    JsonNode secondCorpus = JSON.readTree(secondCorpusResult.stdout());
    assertThat(firstCorpus.path("schemaEvidence").isObject()).isTrue();
    assertThat(containsText(firstCorpus.path("schemaEvidence"), "DISABLED")).isTrue();
    assertThat(secondCorpus.path("schemaEvidence")).isEqualTo(firstCorpus.path("schemaEvidence"));
    assertThat(firstCorpus.path("contentSourceIdentity").asText())
        .isEqualTo(secondCorpus.path("contentSourceIdentity").asText());
    assertThat(firstCorpus.path("aliases")).isEqualTo(secondCorpus.path("aliases"));
    assertThat(reopenedOntologyModule(fixture.runStore(), firstO0).payloads())
        .extracting(payload -> payload.descriptor().fileName())
        .containsExactly("ontology-corpus.json");
    CliResult absentSchemaEvidence = artifact(configuration, firstO0, "SCHEMA_EVIDENCE");
    assertThat(absentSchemaEvidence.exitCode()).isNotZero();
    assertThat(absentSchemaEvidence.stdout()).isEmpty();

    String entryRef = entryRef(firstCorpus);
    String methodRef = unitRef(firstCorpus, "JAVA_METHOD", "method:neutral-list");
    Path scope =
        writeSingleUnitObjectScope(
            temporaryDirectory.resolve("ddl-disabled-packet-scope.json"),
            "Q1",
            "object-task",
            entryRef,
            methodRef);
    ScriptedModel firstModel =
        new ScriptedModel(
            candidate("ontology-typed-candidate-v3"), review("ontology-typed-review-v3"));
    ScriptedModel secondModel =
        new ScriptedModel(
            candidate("ontology-typed-candidate-v3"), review("ontology-typed-review-v3"));
    AtomicInteger providerFactories = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider> firstFactory =
        declaration -> {
          providerFactories.incrementAndGet();
          return firstModel.provider(declaration.expectedRuntimeIdentity());
        };
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        secondFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return secondModel.provider(declaration.expectedRuntimeIdentity());
            };
    CliResult firstIdentified =
        executeWithFactory(
            configuration,
            firstFactory,
            "identify-ontology",
            "--corpus-run",
            firstO0,
            "--scope",
            scope.toString());
    assertThat(firstIdentified.exitCode()).withFailMessage(firstIdentified.stderr()).isZero();
    CliResult secondIdentified =
        executeWithFactory(
            configuration,
            secondFactory,
            "identify-ontology",
            "--corpus-run",
            secondO0,
            "--scope",
            scope.toString());
    assertThat(secondIdentified.exitCode()).withFailMessage(secondIdentified.stderr()).isZero();
    assertThat(firstModel.requests).hasSize(2);
    assertThat(secondModel.requests).hasSize(2);
    JsonNode firstPacket = modelInput(firstModel.requests.get(0)).path("readingPacket");
    JsonNode secondPacket = modelInput(secondModel.requests.get(0)).path("readingPacket");
    assertThat(firstPacket).isEqualTo(secondPacket);
    assertThat(firstPacket.path("units")).hasSize(1);
    assertThat(firstPacket.path("units").get(0).path("ref").asText()).isEqualTo("S1");
    assertThat(containsText(firstPacket, "return mapper.findAll()")).isTrue();
    assertThat(providerFactories.get()).isEqualTo(2);
  }

  @Test
  void supportedCreateFileUsesOnlyActualR4TableLiteralAndPreservesDeclaredKindsAndNullability()
      throws Exception {
    TechnicalFixture fixture =
        prepareRealR4WithDdl(
            "ddl-ast-and-real-table",
            Map.of(
                DDL_SOURCE_PATH,
                DDL_TEXT.getBytes(StandardCharsets.UTF_8),
                DDL_INLINE_CONSTRAINTS_PATH,
                DDL_INLINE_CONSTRAINTS_TEXT.getBytes(StandardCharsets.UTF_8)),
            List.of());
    JsonNode r4Entry = actualTableR4Entry(fixture, "record_table");
    JsonNode r4SqlAnalysis = actualTableSqlAnalysis(r4Entry, "record_table");
    assertThat(r4SqlAnalysis.path("status").asText()).isEqualTo("PARSED");
    assertThat(containsSqlTableNode(r4SqlAnalysis.path("ast"), "parent_table")).isTrue();

    Path configuration =
        writeOntologyConfigWithSchemaSources(
            "ontology-ddl-supported.yaml",
            fixture,
            List.of(DDL_SOURCE_PATH, DDL_INLINE_CONSTRAINTS_PATH),
            false,
            1_048_576);
    CliResult prepared =
        executePublic(configuration, "prepare-ontology", "--evidence-run", fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    CliResult schemaResult = artifact(configuration, o0RunId, "SCHEMA_EVIDENCE");
    assertArtifactAvailable(schemaResult);
    JsonNode schemaEvidence = JSON.readTree(schemaResult.stdout());
    assertThat(schemaEvidence.path("artifactType").asText()).isEqualTo("SCHEMA_EVIDENCE");
    assertThat(schemaEvidence.path("schemaVersion").asText()).isEqualTo("schema-evidence-v1");
    assertThat(schemaEvidence.path("files")).hasSize(2);
    JsonNode file = schemaFile(schemaEvidence, DDL_SOURCE_PATH);
    assertThat(file.path("path").asText()).isEqualTo(DDL_SOURCE_PATH);
    assertThat(file.path("fileId").asText()).isNotBlank();
    assertThat(file.path("sha256").asText()).matches("[0-9a-f]{64}");
    assertThat(file.path("byteLength").asLong())
        .isEqualTo(DDL_TEXT.getBytes(StandardCharsets.UTF_8).length);
    assertThat(file.path("range").path("startOffsetUtf16").asInt()).isZero();
    assertThat(file.path("range").path("lengthUtf16").asInt()).isEqualTo(DDL_TEXT.length());
    assertThat(file.path("parseStatus").asText()).isEqualTo("PARSED");
    assertThat(file.path("structuralCoverage").asText()).isEqualTo("COMPLETE");
    assertThat(file.path("modelEligible").asBoolean()).isTrue();

    assertThat(file.path("declarations")).hasSize(2);
    JsonNode declaration = namedItem(file.path("declarations"), "tableName", "record_table");
    JsonNode parentDeclaration = namedItem(file.path("declarations"), "tableName", "parent_table");
    assertThat(containsText(declaration, "DDL_DECLARED")).isTrue();
    JsonNode idColumn = namedItem(declaration.path("columns"), "name", "id");
    JsonNode codeColumn = namedItem(declaration.path("columns"), "name", "code");
    JsonNode parentColumn = namedItem(declaration.path("columns"), "name", "parent_id");
    JsonNode labelColumn = namedItem(declaration.path("columns"), "name", "label");
    assertThat(containsText(idColumn, "BIGINT")).isTrue();
    assertThat(containsText(idColumn, "NOT_NULL")).isTrue();
    assertThat(codeColumn.path("type").asText().replaceAll("\\s+", "")).isEqualTo("VARCHAR(64)");
    assertThat(containsText(codeColumn, "NULLABLE")).isTrue();
    assertThat(containsText(parentColumn, "UNKNOWN")).isTrue();
    assertThat(labelColumn.path("declaredNullability").asText()).isEqualTo("UNKNOWN");
    assertThat(declaration.path("constraints").findValuesAsText("kind"))
        .contains("PRIMARY_KEY", "UNIQUE", "FOREIGN_KEY", "INDEX");
    assertThat(parentDeclaration.path("constraints").findValuesAsText("kind"))
        .contains("PRIMARY_KEY", "UNIQUE", "INDEX");

    assertThat(file.path("entryUses")).hasSize(2);
    JsonNode recordUse = namedItem(file.path("entryUses"), "matchedTableValue", "record_table");
    JsonNode parentUse = namedItem(file.path("entryUses"), "matchedTableValue", "parent_table");
    assertThat(recordUse.path("entryId").asText()).isEqualTo(r4Entry.path("entryId").asText());
    assertThat(parentUse.path("entryId").asText()).isEqualTo(r4Entry.path("entryId").asText());
    assertThat(recordUse.path("sqlUnitId").asText()).isNotBlank();
    assertThat(parentUse.path("sqlUnitId").asText())
        .isEqualTo(recordUse.path("sqlUnitId").asText());
    JsonNode ordinaryIndex = namedItem(declaration.path("constraints"), "kind", "INDEX");
    assertThat(ordinaryIndex.path("referencedTable").isNull()).isTrue();
    assertThat(ordinaryIndex.path("referencedColumns")).isEmpty();
    for (JsonNode entryUse : List.of(recordUse, parentUse)) {
      assertThat(entryUse.path("associationStatus").asText()).isEqualTo("TABLE_MATCH_CANDIDATE");
      assertThat(entryUse.path("sqlStatus").asText())
          .isEqualTo(r4SqlAnalysis.path("status").asText());
    }
    assertThat(file.path("limitations").isArray()).isTrue();

    JsonNode inlineFile = schemaFile(schemaEvidence, DDL_INLINE_CONSTRAINTS_PATH);
    if ("COMPLETE".equals(inlineFile.path("structuralCoverage").asText())) {
      JsonNode inlineDeclaration =
          namedItem(inlineFile.path("declarations"), "tableName", "record_table");
      assertThat(inlineFile.path("parseStatus").asText()).isEqualTo("PARSED");
      assertThat(inlineFile.path("modelEligible").asBoolean()).isTrue();
      assertThat(
              containsText(
                  namedItem(inlineDeclaration.path("constraints"), "kind", "PRIMARY_KEY")
                      .path("columns"),
                  "id"))
          .isTrue();
      assertThat(
              containsText(
                  namedItem(inlineDeclaration.path("constraints"), "kind", "UNIQUE")
                      .path("columns"),
                  "code"))
          .isTrue();
    } else {
      assertThat(inlineFile.path("parseStatus").asText()).isIn("UNSUPPORTED", "UNKNOWN");
      assertThat(inlineFile.path("structuralCoverage").asText()).isEqualTo("UNKNOWN");
      assertThat(inlineFile.path("declarations")).isEmpty();
      assertThat(inlineFile.path("modelEligible").asBoolean()).isFalse();
    }

    ReopenedModulePublication module = reopenedOntologyModule(fixture.runStore(), o0RunId);
    assertThat(module.payloads())
        .extracting(payload -> payload.descriptor().fileName())
        .containsExactly("ontology-corpus.json", "schema-evidence.json");
    JsonNode installedCorpus =
        JSON.readTree(artifact(configuration, o0RunId, "ONTOLOGY_CORPUS").stdout());
    assertThat(schemaUnitRef(installedCorpus, file.path("fileId").asText())).isNotBlank();
    long inlineFileUnits =
        schemaUnitAliases(installedCorpus, inlineFile.path("fileId").asText()).size();
    assertThat(inlineFileUnits).isEqualTo(inlineFile.path("modelEligible").asBoolean() ? 1L : 0L);
    ArtifactReference schemaReference = modulePayloadReference(module, "SCHEMA_EVIDENCE");
    assertThat(installedCorpus.findValuesAsText("artifactId"))
        .contains(schemaReference.artifactId().value());
    assertThat(installedCorpus.findValuesAsText("sha256"))
        .contains(schemaReference.sha256().value());
  }

  @Test
  void installedSchemaSourceReachesFrozenTypedPacketAndReversesToTheSameR0File() throws Exception {
    TechnicalFixture fixture =
        prepareRealR4WithDdl(
            "ddl-formal-read-and-reverse",
            Map.of(DDL_SOURCE_PATH, DDL_TEXT.getBytes(StandardCharsets.UTF_8)),
            List.of());
    Path configuredSchema =
        writeOntologyConfigWithSchemaSources(
            "ontology-ddl-read.yaml", fixture, List.of(DDL_SOURCE_PATH), true, 1_048_576);
    CliResult prepared =
        executePublic(configuredSchema, "prepare-ontology", "--evidence-run", fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    CliResult corpusResult = artifact(configuredSchema, o0RunId, "ONTOLOGY_CORPUS");
    CliResult schemaResult = artifact(configuredSchema, o0RunId, "SCHEMA_EVIDENCE");
    assertArtifactAvailable(corpusResult);
    assertArtifactAvailable(schemaResult);
    JsonNode corpus = JSON.readTree(corpusResult.stdout());
    JsonNode schemaEvidence = JSON.readTree(schemaResult.stdout());
    JsonNode schemaFile = schemaFile(schemaEvidence, DDL_SOURCE_PATH);
    JsonNode actualSqlAnalysis =
        actualTableSqlAnalysis(actualTableR4Entry(fixture, "record_table"), "record_table");
    assertThat(actualSqlAnalysis.path("status").asText()).isEqualTo("PARSED");
    String entryRef = entryRef(corpus);
    String schemaUnitRef = schemaUnitRef(corpus, schemaFile.path("fileId").asText());
    Path scope =
        writeSingleUnitObjectScope(
            temporaryDirectory.resolve("ddl-source-task-scope.json"),
            "Q1",
            "schema-object-task",
            entryRef,
            schemaUnitRef);
    Path downstreamWithoutCurrentSchemaSource =
        writeOntologyConfigWithSchemaSources(
            "ontology-ddl-downstream-empty-config.yaml", fixture, List.of(), true, 1_048_576);
    ScriptedModel model =
        new ScriptedModel(
            candidate("ontology-typed-candidate-v3"), review("ontology-typed-review-v3"));
    AtomicInteger providerFactories = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              return model.provider(declaration.expectedRuntimeIdentity());
            };
    CliResult identified =
        executeWithFactory(
            downstreamWithoutCurrentSchemaSource,
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    String o1RunId = runId(identified);
    assertThat(model.requests).hasSize(2);
    JsonNode extractInput = modelInput(model.requests.get(0));
    JsonNode reviewInput = modelInput(model.requests.get(1));
    JsonNode packet = extractInput.path("readingPacket");
    assertThat(packet.path("units")).hasSize(1);
    JsonNode modelUnit = packet.path("units").get(0);
    assertThat(modelUnit.path("ref").asText()).isEqualTo("S1");
    assertThat(modelUnit.path("entryUses").isArray()).isTrue();
    assertThat(modelUnit.path("entryUses").get(0).asText()).isEqualTo(entryRef);
    assertThat(modelUnit.path("content").path("sourceText").asText()).isEqualTo(DDL_TEXT);
    JsonNode modelContent = modelUnit.path("content");
    assertThat(modelContent.path("declarations")).hasSize(2);
    assertThat(containsText(modelContent, "record_table")).isTrue();
    assertThat(containsText(modelContent, "parent_table")).isTrue();
    assertThat(containsText(modelContent, "TABLE_MATCH_CANDIDATE")).isTrue();
    assertThat(containsText(modelContent, "PARSED")).isTrue();
    assertThat(containsText(modelContent, "FULL_FILE_RANGE_ONLY")).isTrue();
    assertThat(modelContent.findValuesAsText("sqlStatus"))
        .contains(actualSqlAnalysis.path("status").asText());
    assertThat(modelUnit.findValue("entryId")).isNull();
    assertThat(modelUnit.findValue("sqlUnitId")).isNull();
    assertThat(modelUnit.findValue("fileId")).isNull();
    assertThat(modelUnit.findValue("sha256")).isNull();
    assertThat(modelUnit.findValue("receipt")).isNull();
    assertThat(reviewInput.path("readingPacket")).isEqualTo(packet);
    assertThat(model.requests.get(0).requestedMaxOutputTokens()).isPositive();
    assertThat(providerFactories.get()).isGreaterThan(0);

    CliResult identifiedPayload =
        artifact(downstreamWithoutCurrentSchemaSource, o1RunId, "ONTOLOGY_IDENTIFICATION");
    assertArtifactAvailable(identifiedPayload);
    JsonNode identification = JSON.readTree(identifiedPayload.stdout());
    assertThat(findTaskRecord(identification, "schema-object-task").path("status").asText())
        .isEqualTo("REVIEWED");
    int factoriesAfterIdentification = providerFactories.get();
    Path zeroQuestionRelationSelection =
        writeSelection(
            temporaryDirectory.resolve("ddl-zero-question-relate.json"),
            "RELATE",
            o0RunId,
            List.of(o1RunId),
            List.of(),
            true);
    CliResult related =
        executeWithFactory(
            downstreamWithoutCurrentSchemaSource,
            providerFactory,
            "relate-ontology",
            "--selection",
            zeroQuestionRelationSelection.toString());
    assertThat(related.exitCode()).withFailMessage(related.stderr()).isZero();
    String o2RunId = runId(related);
    assertThat(providerFactories.get()).isEqualTo(factoriesAfterIdentification);
    assertThat(model.requests).hasSize(2);
    CliResult published =
        executeWithFactory(
            downstreamWithoutCurrentSchemaSource,
            providerFactory,
            "publish-ontology",
            "--selection",
            writeSelection(
                    temporaryDirectory.resolve("ddl-source-publish.json"),
                    "PUBLISH",
                    o0RunId,
                    List.of(o1RunId),
                    List.of(o2RunId),
                    false)
                .toString());
    assertThat(published.exitCode()).withFailMessage(published.stderr()).isZero();
    assertThat(providerFactories.get()).isEqualTo(factoriesAfterIdentification);
    assertThat(model.requests).hasSize(2);
    String o3RunId = runId(published);
    CliResult sourceIndex =
        artifact(downstreamWithoutCurrentSchemaSource, o3RunId, "ONTOLOGY_SOURCE_INDEX");
    assertArtifactAvailable(sourceIndex);
    JsonNode sourceRow = sourceIndexRow(sourceIndex.stdout(), "S1");
    assertThat(sourceRow.path("evidenceUnitRef").asText()).isEqualTo(schemaUnitRef);
    assertThat(sourceRow.path("originalId").asText()).isEqualTo(schemaFile.path("fileId").asText());
    assertThat(sourceRow.findValuesAsText("path")).contains(DDL_SOURCE_PATH);
    assertThat(sourceRow.findValuesAsText("sha256")).contains(schemaFile.path("sha256").asText());
    assertThat(sourceRow.findValue("range")).isEqualTo(schemaFile.path("range"));
    CliResult reopenedSchema =
        artifact(downstreamWithoutCurrentSchemaSource, o0RunId, "SCHEMA_EVIDENCE");
    assertThat(reopenedSchema.exitCode()).isZero();
    assertThat(reopenedSchema.stdout()).isEqualTo(schemaResult.stdout());
    assertThat(model.requests).hasSize(2);
  }

  @Test
  void unsupportedAndUnmatchedSchemaFilesStayHonestWithoutBlockingUnrelatedObjectTask()
      throws Exception {
    String malformed = "CREATE TABLE broken_record (id BIGINT,";
    String mixed =
        "CREATE TABLE mixed_record (id INTEGER NOT NULL);\n"
            + "INSERT INTO mixed_record (id) VALUES (1);\n";
    String unmatched = "CREATE TABLE unrelated_record (id INTEGER NOT NULL);\n";
    Map<String, byte[]> files =
        Map.of(
            "schema/00-malformed.sql", malformed.getBytes(StandardCharsets.UTF_8),
            "schema/01-mixed.sql", mixed.getBytes(StandardCharsets.UTF_8),
            "schema/02-unmatched.sql", unmatched.getBytes(StandardCharsets.UTF_8));
    TechnicalFixture fixture = prepareRealR4WithDdlAndRequest("ddl-fail-closed", files, List.of());
    List<String> schemaPaths = files.keySet().stream().sorted().toList();
    Path configuration =
        writeOntologyConfigWithSchemaSources(
            "ontology-ddl-fail-closed.yaml", fixture, schemaPaths, true, 1_048_576);
    CliResult prepared =
        executePublic(configuration, "prepare-ontology", "--evidence-run", fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    CliResult schemaResult = artifact(configuration, o0RunId, "SCHEMA_EVIDENCE");
    assertArtifactAvailable(schemaResult);
    JsonNode schemaEvidence = JSON.readTree(schemaResult.stdout());
    assertThat(schemaEvidence.path("files")).hasSize(3);
    JsonNode malformedFile = schemaFile(schemaEvidence, "schema/00-malformed.sql");
    JsonNode mixedFile = schemaFile(schemaEvidence, "schema/01-mixed.sql");
    JsonNode unmatchedFile = schemaFile(schemaEvidence, "schema/02-unmatched.sql");
    assertThat(malformedFile.path("parseStatus").asText()).isEqualTo("UNKNOWN");
    assertThat(malformedFile.path("structuralCoverage").asText()).isEqualTo("UNKNOWN");
    assertThat(malformedFile.path("declarations")).isEmpty();
    assertThat(malformedFile.path("diagnostics")).isNotEmpty();
    assertThat(malformedFile.path("modelEligible").asBoolean()).isFalse();
    assertThat(mixedFile.path("parseStatus").asText()).isEqualTo("UNSUPPORTED");
    assertThat(mixedFile.path("structuralCoverage").asText()).isEqualTo("UNKNOWN");
    assertThat(mixedFile.path("declarations")).isEmpty();
    assertThat(mixedFile.path("modelEligible").asBoolean()).isFalse();
    assertThat(unmatchedFile.path("parseStatus").asText()).isEqualTo("PARSED");
    assertThat(unmatchedFile.path("structuralCoverage").asText()).isEqualTo("COMPLETE");
    assertThat(unmatchedFile.path("declarations")).hasSize(1);
    assertThat(unmatchedFile.path("entryUses")).isEmpty();
    assertThat(unmatchedFile.path("modelEligible").asBoolean()).isFalse();

    JsonNode corpus = JSON.readTree(artifact(configuration, o0RunId, "ONTOLOGY_CORPUS").stdout());
    String entryRef = entryRef(corpus);
    String methodRef = unitRef(corpus, "JAVA_METHOD", "method:neutral-list");
    Path scope =
        writeSingleUnitObjectScope(
            temporaryDirectory.resolve("unrelated-object-scope.json"),
            "Q1",
            "java-object-task",
            entryRef,
            methodRef);
    ScriptedModel model =
        new ScriptedModel(
            candidate("ontology-typed-candidate-v3"), review("ontology-typed-review-v3"));
    AtomicInteger providerFactories = new AtomicInteger();
    CliResult identified =
        executeWithFactory(
            configuration,
            declaration -> {
              providerFactories.incrementAndGet();
              return model.provider(declaration.expectedRuntimeIdentity());
            },
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(identified.exitCode()).withFailMessage(identified.stderr()).isZero();
    assertThat(model.requests).hasSize(2);
    assertThat(containsText(modelInput(model.requests.get(0)), "return mapper.findAll()")).isTrue();
    assertThat(containsText(modelInput(model.requests.get(0)), "CREATE TABLE")).isFalse();
    assertThat(providerFactories.get()).isGreaterThan(0);

    int savedFrontendRequestCount = savedRequestCoverageCount(fixture);
    assertThat(savedFrontendRequestCount).isEqualTo(1);
    JsonNode savedIdentificationCoverage =
        JSON.readTree(
            new OntologyJobResultStore(
                    fixture.runStore().resolve("ontology-journal"),
                    AnalysisRunId.parse(runId(identified)))
                .readFormalAssembly()
                .coverage()
                .copyToByteArray());

    Path zeroQuestionRelation =
        writeSelection(
            temporaryDirectory.resolve("ddl-fail-closed-zero-question-relate.json"),
            "RELATE",
            o0RunId,
            List.of(runId(identified)),
            List.of(),
            true);
    CliResult related =
        executeWithFactory(
            configuration,
            declaration -> model.provider(declaration.expectedRuntimeIdentity()),
            "relate-ontology",
            "--selection",
            zeroQuestionRelation.toString());
    assertThat(related.exitCode()).isIn(0, 2);
    String o2RunId = runId(related);
    assertThat(model.requests).hasSize(2);

    Path publicationSelection =
        writeSelection(
            temporaryDirectory.resolve("ddl-fail-closed-publish.json"),
            "PUBLISH",
            o0RunId,
            List.of(runId(identified)),
            List.of(o2RunId),
            false);
    CliResult published =
        executeWithFactory(
            configuration,
            declaration -> model.provider(declaration.expectedRuntimeIdentity()),
            "publish-ontology",
            "--selection",
            publicationSelection.toString());
    assertThat(published.exitCode()).isIn(0, 2);
    String o3RunId = runId(published);
    assertThat(model.requests).hasSize(2);
    CliResult coverageArtifact = artifact(configuration, o3RunId, "ONTOLOGY_COVERAGE");
    assertArtifactAvailable(coverageArtifact);
    JsonNode publicCoverage = JSON.readTree(coverageArtifact.stdout());

    org.assertj.core.api.SoftAssertions.assertSoftly(
        softly -> {
          softly
              .assertThat(
                  savedIdentificationCoverage
                      .path("inputDenominators")
                      .path("frontendRequests")
                      .asInt())
              .as("O1 private coverage counts actual R4 REQUEST_COVERAGE rows")
              .isEqualTo(savedFrontendRequestCount);
          softly
              .assertThat(
                  savedIdentificationCoverage.path("inputDenominators").path("ddlSources").asInt())
              .as("O1 private coverage counts all three selected O0 schema files")
              .isEqualTo(3);
          softly
              .assertThat(publicCoverage.path("inputDenominators").path("frontendRequests").asInt())
              .as("O3 public coverage counts actual R4 REQUEST_COVERAGE rows")
              .isEqualTo(savedFrontendRequestCount);
          softly
              .assertThat(publicCoverage.path("inputDenominators").path("ddlSources").asInt())
              .as("O3 public coverage counts unsupported selected O0 schema files")
              .isEqualTo(3);
        });
  }

  @Test
  void excludedNonTextMissingAndOversizedSchemaSourcesFailClosedAtTheirExistingGates()
      throws Exception {
    TechnicalFixture admissionFixture =
        prepareRealR4WithDdl(
            "ddl-invalid-source-admission",
            Map.of(
                "schema/excluded.sql",
                DDL_TEXT.getBytes(StandardCharsets.UTF_8),
                "schema/binary.bin",
                new byte[] {0x00, 0x01, 0x02, 0x03}),
            List.of("schema/excluded.sql"));
    AtomicInteger rejectedSourceFactories = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        forbiddenFactory =
            declaration -> {
              rejectedSourceFactories.incrementAndGet();
              throw new AssertionError("O0 source admission is provider-free");
            };
    for (String sourcePath :
        List.of("schema/excluded.sql", "schema/binary.bin", "schema/unavailable.sql")) {
      Path configuration =
          writeOntologyConfigWithSchemaSources(
              "ontology-ddl-rejected-"
                  + sourcePath.substring(sourcePath.lastIndexOf('/') + 1)
                  + ".yaml",
              admissionFixture,
              List.of(sourcePath),
              true,
              1_048_576);
      CliResult refused =
          executeWithFactory(
              configuration,
              forbiddenFactory,
              "prepare-ontology",
              "--evidence-run",
              admissionFixture.r4RunId());
      assertThat(refused.exitCode()).isNotZero();
      assertThat(refused.stderr()).isNotBlank();
      assertThat(refused.stdout()).isEmpty();
    }
    assertThat(rejectedSourceFactories).hasValue(0);

    String oversizedDdl = "-- neutral complete-source size fixture\n".repeat(40) + DDL_TEXT;
    assertThat(oversizedDdl.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(512);
    TechnicalFixture oversizedFixture =
        prepareRealR4WithDdl(
            "ddl-complete-unit-capacity",
            Map.of(DDL_SOURCE_PATH, oversizedDdl.getBytes(StandardCharsets.UTF_8)),
            List.of());
    Path oversizedConfiguration =
        writeOntologyConfigWithSchemaSources(
            "ontology-ddl-unit-capacity.yaml",
            oversizedFixture,
            List.of(DDL_SOURCE_PATH),
            true,
            512);
    CliResult prepared =
        executePublic(
            oversizedConfiguration,
            "prepare-ontology",
            "--evidence-run",
            oversizedFixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);
    JsonNode schemaEvidence =
        JSON.readTree(artifact(oversizedConfiguration, o0RunId, "SCHEMA_EVIDENCE").stdout());
    JsonNode file = schemaFile(schemaEvidence, DDL_SOURCE_PATH);
    assertThat(file.path("byteLength").asLong())
        .isEqualTo(oversizedDdl.getBytes(StandardCharsets.UTF_8).length);
    assertThat(file.path("modelEligible").asBoolean()).isTrue();
    JsonNode corpus =
        JSON.readTree(artifact(oversizedConfiguration, o0RunId, "ONTOLOGY_CORPUS").stdout());
    String entryRef = entryRef(corpus);
    String schemaUnitRef = schemaUnitRef(corpus, file.path("fileId").asText());
    Path scope =
        writeSingleUnitObjectScope(
            temporaryDirectory.resolve("oversized-schema-unit-scope.json"),
            "Q1",
            "oversized-schema-task",
            entryRef,
            schemaUnitRef);
    CliResult refusedTask =
        executeWithFactory(
            oversizedConfiguration,
            forbiddenFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            scope.toString());
    assertThat(refusedTask.exitCode()).isNotZero();
    assertThat(refusedTask.stderr()).contains("TOO_LARGE");
    assertThat(refusedTask.stdout()).isEmpty();
    assertThat(rejectedSourceFactories).hasValue(0);
  }

  private static ImmutableBytes formalSurveyResponse(String entryRef, String clueRef) {
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", "ontology-survey-response-v3");
    ObjectNode hypothesis = response.putArray("systemHypotheses").addObject();
    hypothesis.put("type", "UNKNOWN");
    hypothesis.putArray("observedEntryRefs").add(entryRef);
    hypothesis.put("uncertainty", "One neutral entry does not establish a complete system model.");
    ObjectNode question = response.putArray("questions").addObject();
    question.put("questionId", "Q1");
    question.put("question", "What record-shaped value is supported by this selected source?");
    question.putArray("candidateEntryRefs").add(entryRef);
    question.putArray("clueRefs").add(clueRef);
    question.putArray("searchTerms");
    response.putArray("unresolved");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalPrioritizeResponse(String questionRef) {
    return formalPrioritizeResponse(questionRef, List.of("OBJECT"));
  }

  private static ImmutableBytes formalPrioritizeResponse(
      String questionRef, List<String> taskKinds) {
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", "ontology-prioritize-response-v4");
    ObjectNode selected = response.putArray("selectedQuestions").addObject();
    selected.put("questionRef", questionRef);
    selected.put(
        "specificQuestion", "What record-shaped value is supported by this selected source?");
    selected.put("selectionReason", "The bounded source page contains one readable entry.");
    selected.putArray("currentUnknowns");
    ArrayNode selectedTaskKinds = selected.putArray("taskKinds");
    taskKinds.forEach(selectedTaskKinds::add);
    response.putArray("deferredQuestions");
    response.putArray("unresolved");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes businessLinkFormalReadingResponseV4(
      String decision, String unitRef, String entryRef, boolean read) {
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", "reading-response-v4");
    response.put("decision", decision);
    response.putObject("entrySelection").putArray("addRefs");
    ((ObjectNode) response.path("entrySelection")).putArray("remove");
    response.putObject("clueSelection").putArray("addRefs");
    ((ObjectNode) response.path("clueSelection")).putArray("remove");
    ArrayNode retained = response.putArray("retainedUnitUses");
    if (read) {
      ObjectNode use = retained.addObject();
      use.put("unitRef", unitRef);
      use.put("entryRef", entryRef);
    }
    response.putArray("requiredUnitUses");
    ArrayNode actions = response.putArray("actions");
    if (!read) {
      ObjectNode action = actions.addObject();
      action.put("kind", "READ");
      action.put("unitRef", unitRef);
      action.put("entryRef", entryRef);
    }
    response.putArray("unresolved");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes businessLinkModelReadingResponseV4(
      String decision, String unitRef, String entryRef, String clueRef, boolean read) {
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", "reading-response-v4");
    response.put("decision", decision);
    ObjectNode entrySelection = response.putObject("entrySelection");
    entrySelection.putArray("addRefs");
    entrySelection.putArray("remove");
    ObjectNode clueSelection = response.putObject("clueSelection");
    if (clueRef == null) {
      clueSelection.putArray("addRefs");
    } else {
      clueSelection.putArray("addRefs").add(clueRef);
    }
    clueSelection.putArray("remove");
    ArrayNode retained = response.putArray("retainedUnitUses");
    if (read) {
      ObjectNode use = retained.addObject();
      use.put("unitRef", unitRef);
      use.put("entryRef", entryRef);
    }
    response.putArray("requiredUnitUses");
    ArrayNode actions = response.putArray("actions");
    if (!read) {
      ObjectNode action = actions.addObject();
      action.put("kind", "READ");
      action.put("unitRef", unitRef);
      action.put("entryRef", entryRef);
    }
    response.putArray("unresolved");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalReadingResponse(
      String decision, String unitRef, String entryRef, boolean read) {
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", "reading-response-v3");
    response.put("decision", decision);
    ObjectNode entrySelection = response.putObject("entrySelection");
    entrySelection.putArray("addRefs");
    entrySelection.putArray("remove");
    ObjectNode clueSelection = response.putObject("clueSelection");
    clueSelection.putArray("addRefs");
    clueSelection.putArray("remove");
    ArrayNode retained = response.putArray("retainedUnitUses");
    if (read) {
      ObjectNode use = retained.addObject();
      use.put("unitRef", unitRef);
      use.put("entryRef", entryRef);
    }
    response.putArray("requiredUnitUses");
    ArrayNode actions = response.putArray("actions");
    if (!read) {
      ObjectNode action = actions.addObject();
      action.put("kind", "READ");
      action.put("unitRef", unitRef);
      action.put("entryRef", entryRef);
    }
    response.putArray("unresolved");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalObjectResponse(
      String schemaVersion, String questionRef, String entryRef) throws Exception {
    ObjectNode response =
        (ObjectNode) JSON.readTree(objectResponse(schemaVersion).copyToByteArray());
    ObjectNode object = (ObjectNode) response.path("definitions").path("objects").get(0);
    ObjectNode scope = (ObjectNode) object.path("scope");
    scope.put("questionRef", questionRef);
    ArrayNode entryUses = (ArrayNode) scope.path("entryUseRefs");
    entryUses.removeAll();
    entryUses.add(entryRef);
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalTaskObjectResponse(
      String schemaVersion, String questionRef, String entryRef) {
    ObjectNode definitions = JSON.createObjectNode();
    ObjectNode object = definitions.putArray("objects").addObject();
    object.put("localId", "O1");
    object.put("name", "Neutral source-backed record");
    object.put("definition", "A neutral record-shaped value visible in the selected method.");
    object.put("origin", "IMPLEMENTATION");
    object.put("certainty", "INFERRED");
    object.set("scope", formalTaskScope(questionRef, entryRef));
    object.putArray("evidenceRefs").add("S1");
    object.putArray("identities");
    ObjectNode property = object.putArray("properties").addObject();
    property.put("localId", "P1");
    property.put("name", "neutralResult");
    property.put("definition", "The method's neutral return value; its semantic type is unknown.");
    ObjectNode sourceBinding = property.putArray("sourceBindings").addObject();
    sourceBinding.put("kind", "JAVA_MEMBER");
    sourceBinding.put("owner", "fixture.RecordHandler");
    sourceBinding.put("name", "list");
    sourceBinding.putNull("expression");
    sourceBinding.putArray("evidenceRefs").add("S1");
    sourceBinding.putArray("unknowns");
    property.set("dataType", typedValueUnknown());
    property.put("nullable", "UNKNOWN");
    property.putNull("derivation");
    property.set("unit", typedValueUnknown());
    property.putArray("evidenceRefs").add("S1");
    property.putArray("unknowns");
    object.putArray("backing");
    object.putArray("variants");
    ObjectNode identityUnknown = object.putArray("unknowns").addObject();
    identityUnknown.put("field", "identities");
    identityUnknown.put("reason", "The neutral source fixture does not confirm an identity.");
    identityUnknown.putArray("missingUnitRefs");
    object.put("definitionCompleteness", "PARTIAL");
    return typedTaskResponse(schemaVersion, "OBJECT", definitions, JSON.createArrayNode());
  }

  private static ImmutableBytes businessLinkFormalObjectResponseV4(
      String schemaVersion, String questionRef, String entryRef) {
    return businessLinkFormalObjectResponseV4(
        schemaVersion,
        questionRef,
        entryRef,
        "Neutral source-backed record",
        "A neutral record-shaped value visible in the selected method.");
  }

  private static ImmutableBytes businessLinkFormalObjectResponseV4(
      String schemaVersion,
      String questionRef,
      String entryRef,
      String objectName,
      String objectDefinition) {
    ObjectNode response =
        (ObjectNode)
            CANONICAL.parseCanonical(
                formalTaskObjectResponse(
                    schemaVersion, questionRef, entryRef, objectName, objectDefinition));
    ((ObjectNode) response.path("definitions").path("objects").get(0)).put("displayRole", "MAIN");
    response.putArray("clueDispositions");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes businessLinkRuntimeJointResponse(
      String schemaVersion, JsonNode input, String namePrefix) {
    String questionRef = input.path("questionId").asText();
    JsonNode firstUnit = input.path("readingPacket").path("units").get(0);
    String sourceRef = firstUnit.path("ref").asText();
    String entryRef = firstUnit.path("entryUses").get(0).asText();
    String fromKey = namePrefix + "-source";
    String toKey = namePrefix + "-target";
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", schemaVersion);
    response.put("taskKind", "LINK");
    response
        .putArray("objects")
        .add(runtimeLinkObject(questionRef, entryRef, sourceRef, fromKey))
        .add(runtimeLinkObject(questionRef, entryRef, sourceRef, toKey));
    ObjectNode link = response.putArray("links").addObject();
    link.put("fromKey", fromKey);
    link.put("toKey", toKey);
    link.put("name", "Record reference");
    link.put("definition", "One selected record refers to another through a source value.");
    link.put("certainty", "INFERRED");
    link.set("scope", runtimeLinkScope(questionRef, entryRef));
    ObjectNode mechanism = link.putArray("mechanism").addObject();
    mechanism.put("description", "The source passes a record-reference value.");
    ObjectNode expression = mechanism.putObject("expression");
    expression.put("language", "JAVA");
    expression.put("text", "sourceRecordCode");
    ObjectNode binding = expression.putArray("bindings").addObject();
    binding.put("symbol", "sourceRecordCode");
    binding.put("definitionRef", fromKey);
    binding.putNull("propertyRef");
    expression.putArray("evidenceRefs").add(sourceRef);
    mechanism.putArray("targetObjectRefs").add(toKey);
    mechanism.putArray("sourceBindings");
    mechanism.putArray("evidenceRefs").add(sourceRef);
    mechanism.putArray("unknowns");
    link.putArray("conditions");
    ObjectNode cardinality = link.putObject("cardinality");
    cardinality.put("basis", "UNKNOWN");
    cardinality.put("value", "UNKNOWN");
    cardinality.putArray("evidenceRefs");
    cardinality.putArray("unknowns");
    link.putArray("evidenceRefs").add(sourceRef);
    link.putArray("unknowns");
    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", input.path("visibleClueRefs").get(0).asText());
    disposition.put("outcome", "LINK_SUPPORTED");
    disposition.putArray("linkIndexes").add(0);
    disposition.put("reason", "The selected source supports this candidate relation.");
    response.putArray("unresolved");
    response.putArray("corrections");
    return CANONICAL.encodeCanonical(response);
  }

  private static ObjectNode runtimeLinkObject(
      String questionRef, String entryRef, String sourceRef, String objectKey) {
    ObjectNode object = JSON.createObjectNode();
    object.put("objectKey", objectKey);
    object.put("name", objectKey);
    object.put("definition", "A record endpoint visible in the selected technical source.");
    object.put("displayRole", "TECHNICAL_OR_UNKNOWN");
    object.put("certainty", "INFERRED");
    object.set("scope", runtimeLinkScope(questionRef, entryRef));
    ObjectNode backing = object.putArray("backing").addObject();
    backing.put("kind", "JAVA_MEMBER");
    backing.put("owner", "example.RecordHandler");
    backing.put("name", "sourceRecordCode");
    backing.putNull("expression");
    backing.putArray("evidenceRefs").add(sourceRef);
    backing.putArray("unknowns");
    object.putArray("variants");
    object.putArray("evidenceRefs").add(sourceRef);
    ObjectNode unknown = object.putArray("unknowns").addObject();
    unknown.put("field", "identity");
    unknown.put("reason", "Identity was not investigated by the LINK skeleton task.");
    unknown.putArray("missingUnitRefs");
    return object;
  }

  private static ObjectNode runtimeLinkScope(String questionRef, String entryRef) {
    ObjectNode scope = JSON.createObjectNode();
    scope.put("questionRef", questionRef);
    scope.putArray("entryUseRefs").add(entryRef);
    scope.putArray("variants");
    return scope;
  }

  private static ImmutableBytes businessLinkFormalActionResponseV4(
      String schemaVersion, String questionRef, String entryRef, String objectRef) {
    ObjectNode response =
        (ObjectNode)
            CANONICAL.parseCanonical(
                formalTaskActionResponse(schemaVersion, questionRef, entryRef, objectRef));
    response.putArray("clueDispositions");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes businessLinkFormalAnalyticResponseV4(
      String schemaVersion,
      String questionRef,
      String entryRef,
      String objectRef,
      String propertyRef) {
    ObjectNode response =
        (ObjectNode)
            CANONICAL.parseCanonical(
                formalTaskAnalyticResponse(
                    schemaVersion, questionRef, entryRef, objectRef, propertyRef));
    response.putArray("clueDispositions");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalTaskObjectResponseWithDanglingReference(
      String schemaVersion, String questionRef, String entryRef, String danglingRef) {
    ObjectNode response;
    try {
      response =
          (ObjectNode)
              JSON.readTree(
                  formalTaskObjectResponse(schemaVersion, questionRef, entryRef).copyToByteArray());
    } catch (Exception invalidFixture) {
      throw new AssertionError("could not specialize neutral object response", invalidFixture);
    }
    ObjectNode unresolved = response.putArray("unresolved").addObject();
    unresolved.put("issueId", "UNRESOLVED-OBJECT-REFERENCE");
    unresolved.put("proposedKind", "OBJECT");
    unresolved.put("description", "The final review retained one invalid definition reference.");
    unresolved.putArray("knownDefinitionRefs").add(danglingRef);
    unresolved.putArray("relatedLocalDefinitionRefs");
    unresolved.putArray("missingRequirements");
    unresolved.putArray("evidenceRefs").add("S1");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalTaskObjectResponse(
      String schemaVersion, String questionRef, String entryRef, String name, String definition) {
    ObjectNode response;
    try {
      response =
          (ObjectNode)
              JSON.readTree(
                  formalTaskObjectResponse(schemaVersion, questionRef, entryRef).copyToByteArray());
    } catch (Exception invalidFixture) {
      throw new AssertionError("could not specialize neutral object response", invalidFixture);
    }
    ObjectNode object = (ObjectNode) response.path("definitions").path("objects").get(0);
    object.put("name", name);
    object.put("definition", definition);
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalTaskObjectCorrectionReview(
      String questionRef, String entryRef, String name, String definition) {
    ObjectNode response;
    try {
      response =
          (ObjectNode)
              JSON.readTree(
                  formalTaskObjectResponse(
                          "ontology-typed-review-v3", questionRef, entryRef, name, definition)
                      .copyToByteArray());
    } catch (Exception invalidFixture) {
      throw new AssertionError("could not build corrected neutral review", invalidFixture);
    }
    ObjectNode correction = response.putArray("corrections").addObject();
    correction.put("targetLocalId", "O1");
    correction.put("changeKind", "CHANGED");
    correction.put(
        "reason", "The reviewed neutral definition is more precise for the same source.");
    correction.putArray("evidenceRefs").add("S1");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalTaskActionResponse(
      String schemaVersion, String questionRef, String entryRef, String objectRef) {
    ObjectNode definitions = JSON.createObjectNode();
    ObjectNode operation = definitions.putArray("operations").addObject();
    operation.put("localId", "A1");
    operation.put("name", "Neutral record operation");
    operation.put("definition", "A neutral operation over the reviewed record-shaped value.");
    operation.put("origin", "IMPLEMENTATION");
    operation.put("certainty", "INFERRED");
    operation.set("scope", formalTaskScope(questionRef, entryRef));
    operation.putArray("evidenceRefs").add("S1");
    operation.putArray("unknowns");
    operation.put("kind", "MUTATION");
    operation.putArray("targetObjectRefs").add(objectRef);
    operation.putArray("parameters");
    operation
        .putArray("preconditions")
        .add(
            formalSemanticItem(
                "The selected operation has a source-backed precondition.", objectRef));
    operation.putArray("rejections");
    ObjectNode effect = operation.putArray("effects").addObject();
    effect.put("description", "A neutral effect retained without business interpretation.");
    effect.putNull("expression");
    effect.putArray("targetObjectRefs").add(objectRef);
    effect.putArray("sourceBindings");
    effect.putArray("evidenceRefs").add("S1");
    effect.putArray("unknowns");
    effect
        .putArray("conditions")
        .add(formalSemanticItem("The nested condition targets the reviewed object.", objectRef));
    ObjectNode entryUse = operation.putArray("entryUses").addObject();
    entryUse.put("entryRef", entryRef);
    entryUse.putArray("conditions");
    entryUse.putArray("evidenceRefs").add("S1");
    entryUse.putArray("unknowns");

    ObjectNode rule = definitions.putArray("rules").addObject();
    rule.put("localId", "R1");
    rule.put("name", "Neutral operation rule");
    rule.put("definition", "A neutral rule owned by the reviewed operation.");
    rule.put("origin", "IMPLEMENTATION");
    rule.put("certainty", "INFERRED");
    rule.set("scope", formalTaskScope(questionRef, entryRef));
    rule.putArray("evidenceRefs").add("S1");
    rule.putArray("unknowns");
    rule.put("ownerRef", "A1");
    rule.putArray("applicability")
        .add(formalSemanticItem("The rule applies to the selected record operation.", objectRef));
    rule.set(
        "condition", formalSemanticItem("The rule condition uses the reviewed object.", objectRef));
    ObjectNode consequence = rule.putObject("consequence");
    consequence.put("description", "A neutral consequence retained from the selected source.");
    consequence.putNull("expression");
    consequence.putArray("targetObjectRefs").add(objectRef);
    consequence.putArray("sourceBindings");
    consequence.putArray("evidenceRefs").add("S1");
    consequence.putArray("unknowns");
    consequence
        .putArray("conditions")
        .add(
            formalSemanticItem(
                "The consequence condition targets the reviewed object.", objectRef));
    return typedTaskResponse(schemaVersion, "ACTION", definitions, JSON.createArrayNode());
  }

  private static ImmutableBytes formalTaskActionResponseWithDanglingObjectReference(
      String schemaVersion,
      String questionRef,
      String entryRef,
      String objectRef,
      String danglingRef) {
    ObjectNode response;
    try {
      response =
          (ObjectNode)
              JSON.readTree(
                  formalTaskActionResponse(schemaVersion, questionRef, entryRef, objectRef)
                      .copyToByteArray());
    } catch (Exception invalidFixture) {
      throw new AssertionError("could not specialize neutral action response", invalidFixture);
    }
    ArrayNode targets =
        (ArrayNode) response.path("definitions").path("operations").get(0).path("targetObjectRefs");
    targets.removeAll();
    targets.add(danglingRef);
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalTaskAnalyticResponse(
      String schemaVersion,
      String questionRef,
      String entryRef,
      String objectRef,
      String propertyRef) {
    ObjectNode definitions = JSON.createObjectNode();
    ObjectNode dimension = definitions.putArray("dimensions").addObject();
    dimension.put("localId", "D1");
    dimension.put("name", "Neutral grouping dimension");
    dimension.put("definition", "A structural dimension tied to a reviewed object property.");
    dimension.put("origin", "IMPLEMENTATION");
    dimension.put("certainty", "INFERRED");
    dimension.set("scope", formalTaskScope(questionRef, entryRef));
    dimension.putArray("evidenceRefs").add("S1");
    dimension.putArray("unknowns");
    dimension.putArray("ownerRefs").add(objectRef);
    dimension.putArray("sourceBindings");
    dimension.putArray("roles").add("GROUPING");
    dimension.set("grain", formalGrain(propertyRef));
    dimension.putArray("joinPath");

    ObjectNode measure = definitions.putArray("measures").addObject();
    measure.put("localId", "V1");
    measure.put("name", "Neutral source-bound measure");
    measure.put("definition", "A source-bound measure with an unknown unit.");
    measure.put("origin", "IMPLEMENTATION");
    measure.put("certainty", "INFERRED");
    measure.set("scope", formalTaskScope(questionRef, entryRef));
    measure.putArray("evidenceRefs").add("S1");
    measure.putArray("unknowns");
    measure.putArray("ownerRefs").add(objectRef);
    measure.set("inputGrain", formalGrain(propertyRef));
    measure.set("expression", formalExpression(objectRef, propertyRef));
    measure.putNull("aggregation");
    measure.putArray("filters");
    measure.putArray("postProcessing");
    measure.set("unit", typedValueUnknown());
    definitions.putArray("metrics");
    return typedTaskResponse(schemaVersion, "ANALYTIC", definitions, JSON.createArrayNode());
  }

  private static ImmutableBytes formalTaskUnresolvedRelateResponse(
      String schemaVersion, String objectRef) {
    ObjectNode definitions = JSON.createObjectNode();
    definitions.putArray("links");
    ObjectNode unresolved = JSON.createObjectNode();
    unresolved.put("issueId", ExplicitTypedPipelineScript.RELATION_UNRESOLVED_ID);
    unresolved.put("proposedKind", "RELATE");
    unresolved.put(
        "description",
        "This one-entry neutral fixture provides no second reviewed object endpoint for a"
            + " relation.");
    unresolved.putArray("knownDefinitionRefs").add(objectRef);
    unresolved.putArray("relatedLocalDefinitionRefs");
    unresolved.putArray("missingRequirements");
    unresolved.putArray("evidenceRefs").add("S1");
    ArrayNode unresolvedItems = JSON.createArrayNode().add(unresolved);
    ObjectNode response;
    try {
      response =
          (ObjectNode)
              JSON.readTree(
                  typedTaskResponse(schemaVersion, "RELATE", definitions, unresolvedItems)
                      .copyToByteArray());
    } catch (Exception invalidFixture) {
      throw new AssertionError(
          "could not build neutral unresolved RELATE response", invalidFixture);
    }
    response.putArray("identityDecisions");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes businessLinkFormalUnresolvedRelateResponseV4(
      String schemaVersion, String objectRef) {
    ObjectNode response =
        (ObjectNode)
            CANONICAL.parseCanonical(formalTaskUnresolvedRelateResponse(schemaVersion, objectRef));
    response.putArray("clueDispositions");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes businessLinkFormalUnresolvedRelateResponseV4(
      String schemaVersion, List<String> objectRefs) {
    ObjectNode response =
        (ObjectNode)
            CANONICAL.parseCanonical(formalTaskUnresolvedRelateResponse(schemaVersion, objectRefs));
    response.putArray("clueDispositions");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes businessLinkRelateResponseWithClueDispositionV4(
      String schemaVersion, String objectRef, String clueRef) {
    ObjectNode response =
        (ObjectNode)
            CANONICAL.parseCanonical(
                businessLinkFormalUnresolvedRelateResponseV4(schemaVersion, objectRef));
    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", clueRef);
    disposition.put("outcome", "NOT_A_BUSINESS_LINK");
    disposition.putArray("linkRefs");
    disposition.put("reason", "The selected source does not support a business link.");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalTaskUnresolvedRelateResponse(
      String schemaVersion, List<String> objectRefs) {
    ObjectNode definitions = JSON.createObjectNode();
    definitions.putArray("links");
    ObjectNode unresolved = JSON.createObjectNode();
    unresolved.put("issueId", ExplicitTypedPipelineScript.RELATION_UNRESOLVED_ID);
    unresolved.put("proposedKind", "RELATE");
    unresolved.put(
        "description",
        "The neutral selected evidence does not establish a relation between these reviewed"
            + " endpoints.");
    ArrayNode knownRefs = unresolved.putArray("knownDefinitionRefs");
    objectRefs.forEach(knownRefs::add);
    unresolved.putArray("relatedLocalDefinitionRefs");
    unresolved.putArray("missingRequirements");
    unresolved.putArray("evidenceRefs").add("S1");
    ArrayNode unresolvedItems = JSON.createArrayNode().add(unresolved);
    ObjectNode response;
    try {
      response =
          (ObjectNode)
              JSON.readTree(
                  typedTaskResponse(schemaVersion, "RELATE", definitions, unresolvedItems)
                      .copyToByteArray());
    } catch (Exception invalidFixture) {
      throw new AssertionError(
          "could not build neutral unresolved RELATE response", invalidFixture);
    }
    response.putArray("identityDecisions");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes formalTaskSameObjectRelateResponse(
      String schemaVersion, List<String> objectRefs, String canonicalRef) {
    if (objectRefs.size() != 2 || !objectRefs.contains(canonicalRef)) {
      throw new IllegalArgumentException("test identity decision needs both real object endpoints");
    }
    ObjectNode response;
    try {
      response =
          (ObjectNode)
              JSON.readTree(
                  formalTaskUnresolvedRelateResponse(schemaVersion, objectRefs).copyToByteArray());
    } catch (Exception invalidFixture) {
      throw new AssertionError(
          "could not extend the valid unresolved RELATE fixture", invalidFixture);
    }
    ObjectNode decision = response.putArray("identityDecisions").addObject();
    decision.put("decisionId", "same-reviewed-object");
    decision.put("kind", "SAME_OBJECT");
    decision.put("leftRef", objectRefs.get(0));
    decision.put("rightRef", objectRefs.get(1));
    decision.put("canonicalRef", canonicalRef);
    decision.putArray("conditions");
    decision.putArray("evidenceRefs").add("S1");
    decision.putArray("unknowns");
    return CANONICAL.encodeCanonical(response);
  }

  private static ImmutableBytes typedTaskResponse(
      String schemaVersion, String taskKind, ObjectNode definitions, ArrayNode unresolved) {
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", schemaVersion);
    response.put("taskKind", taskKind);
    response.set("definitions", definitions);
    response.set("unresolved", unresolved);
    response.putArray("corrections");
    return CANONICAL.encodeCanonical(response);
  }

  private static ObjectNode formalTaskScope(String questionRef, String entryRef) {
    ObjectNode scope = JSON.createObjectNode();
    scope.put("questionRef", questionRef);
    scope.putArray("entryUseRefs").add(entryRef);
    scope.putArray("variants");
    return scope;
  }

  private static ObjectNode formalSemanticItem(String description, String objectRef) {
    ObjectNode item = JSON.createObjectNode();
    item.put("description", description);
    item.putNull("expression");
    item.putArray("targetObjectRefs").add(objectRef);
    item.putArray("sourceBindings");
    item.putArray("evidenceRefs").add("S1");
    item.putArray("unknowns");
    return item;
  }

  private static ObjectNode formalGrain(String propertyRef) {
    ObjectNode grain = JSON.createObjectNode();
    grain.put("description", "A neutral fixture grain tied to one retained property.");
    grain.putArray("keyRefs").add(propertyRef);
    grain.putArray("unknowns");
    return grain;
  }

  private static ObjectNode formalExpression(String objectRef, String propertyRef) {
    ObjectNode expression = JSON.createObjectNode();
    expression.put("language", "DERIVED");
    expression.put("text", "the selected source value");
    ObjectNode binding = expression.putArray("bindings").addObject();
    binding.put("symbol", "value");
    binding.put("definitionRef", objectRef);
    binding.put("propertyRef", propertyRef);
    expression.putArray("evidenceRefs").add("S1");
    return expression;
  }

  private static ObjectNode typedValueUnknown() {
    ObjectNode value = JSON.createObjectNode();
    value.put("status", "UNKNOWN");
    value.putNull("value");
    return value;
  }

  private static List<String> textFieldValues(JsonNode node, String fieldName) {
    List<String> values = new ArrayList<>();
    collectTextFieldValues(node, fieldName, values);
    return values;
  }

  private static void collectTextFieldValues(JsonNode node, String fieldName, List<String> values) {
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (fieldName.equals(field.getKey()) && field.getValue().isTextual()) {
          values.add(field.getValue().asText());
        }
        collectTextFieldValues(field.getValue(), fieldName, values);
      }
    } else if (node.isArray()) {
      node.forEach(value -> collectTextFieldValues(value, fieldName, values));
    }
  }

  private static List<String> matchingTextValues(JsonNode node, String pattern) {
    List<String> values = new ArrayList<>();
    collectMatchingTextValues(node, pattern, values);
    return values;
  }

  private static void collectMatchingTextValues(
      JsonNode node, String pattern, List<String> values) {
    if (node.isTextual()) {
      if (node.asText().matches(pattern)) {
        values.add(node.asText());
      }
    } else if (node.isObject() || node.isArray()) {
      node.elements().forEachRemaining(value -> collectMatchingTextValues(value, pattern, values));
    }
  }

  private static boolean hasField(JsonNode node, String fieldName) {
    if (node.isObject()) {
      if (node.has(fieldName)) {
        return true;
      }
      Iterator<JsonNode> values = node.elements();
      while (values.hasNext()) {
        if (hasField(values.next(), fieldName)) {
          return true;
        }
      }
    } else if (node.isArray()) {
      Iterator<JsonNode> values = node.elements();
      while (values.hasNext()) {
        if (hasField(values.next(), fieldName)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean containsText(JsonNode node, String text) {
    if (node.isTextual()) {
      return node.asText().contains(text);
    }
    if (node.isObject() || node.isArray()) {
      Iterator<JsonNode> values = node.elements();
      while (values.hasNext()) {
        if (containsText(values.next(), text)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean containsRange(JsonNode node, SourceRange expected) {
    if (node.isObject()
        && node.path("startOffsetUtf16").isIntegralNumber()
        && node.path("startOffsetUtf16").asInt() == expected.startOffsetUtf16()
        && node.path("lengthUtf16").isIntegralNumber()
        && node.path("lengthUtf16").asInt() == expected.lengthUtf16()
        && node.path("startLine").isIntegralNumber()
        && node.path("startLine").asInt() == expected.startLine()
        && node.path("endLine").isIntegralNumber()
        && node.path("endLine").asInt() == expected.endLine()) {
      return true;
    }
    if (node.isObject() || node.isArray()) {
      Iterator<JsonNode> values = node.elements();
      while (values.hasNext()) {
        if (containsRange(values.next(), expected)) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  void policyV5CliRunsLeanLinksTypeComparisonAndFourFilePublication() throws Exception {
    TechnicalFixture fixture = prepareRealR4("lean-link-type-correspondence-v5-cli");
    ConfiguredTypedPipeline configured =
        writeBusinessLinkTypedPipelineConfigurationV5(
            "ontology-lean-link-type-correspondence-v5.yaml", fixture);
    List<StructuredModelRequest> requests = new ArrayList<>();
    List<JsonNode> typeInputs = new ArrayList<>();
    AtomicInteger linkExtracts = new AtomicInteger();
    AtomicInteger typeExtracts = new AtomicInteger();
    AtomicInteger typeReviews = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String taskKind = input.path("taskKind").asText();
                boolean review = request.taskKind().contains("REVIEW");
                ImmutableBytes response;
                if ("LINK".equals(taskKind)) {
                  assertThat(requestSchemaVersion(request))
                      .isEqualTo(review ? "ontology-link-review-v2" : "ontology-link-candidate-v2");
                  if (review) {
                    ObjectNode reviewed = (ObjectNode) input.path("actualDraft").deepCopy();
                    assertThat(reviewed.path("schemaVersion").asText())
                        .isEqualTo("ontology-link-candidate-v2");
                    reviewed.put("schemaVersion", "ontology-link-review-v2");
                    response = CANONICAL.encodeCanonical(reviewed);
                  } else {
                    int sequence = linkExtracts.incrementAndGet();
                    response =
                        leanRuntimeLinkResponse(
                            "ontology-link-candidate-v2", input, Integer.toString(sequence));
                  }
                } else if ("TYPE_COMPARE".equals(taskKind)) {
                  assertThat(requestSchemaVersion(request))
                      .isEqualTo(
                          review
                              ? "ontology-object-type-review-v1"
                              : "ontology-object-type-candidate-v1");
                  assertThat(input.path("readingPacket").path("schemaVersion").asText())
                      .isEqualTo("ontology-model-reading-v7");
                  assertThat(input.path("reviewedCatalog").path("entries").size()).isEqualTo(2);
                  typeInputs.add(input.deepCopy());
                  if (review) {
                    int sequence = typeReviews.incrementAndGet();
                    ObjectNode reviewed = (ObjectNode) input.path("actualDraft").deepCopy();
                    assertCurrentTypeEvidence(input, reviewed);
                    reviewed.put(
                        "schemaVersion",
                        sequence == 2
                            ? "ontology-object-type-review-invalid"
                            : "ontology-object-type-review-v1");
                    response = CANONICAL.encodeCanonical(reviewed);
                  } else {
                    int sequence = typeExtracts.incrementAndGet();
                    ObjectNode candidate =
                        (ObjectNode)
                            CANONICAL.parseCanonical(
                                typeCompareRuntimeResponse(
                                    input, sequence == 3 ? "DISTINCT" : "SAME_OBJECT_TYPE"));
                    assertCurrentTypeEvidence(input, candidate);
                    if (sequence == 1) {
                      // A bad candidate is local to this generated pair; later pairs still run.
                      candidate.put("schemaVersion", "ontology-object-type-candidate-invalid");
                    }
                    response = CANONICAL.encodeCanonical(candidate);
                  }
                } else {
                  throw new AssertionError("unexpected v5 correspondence task " + taskKind);
                }
                return new StructuredModelResponse(response, identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    String corpusRunId = runId(prepared);
    assertThat(requests).isEmpty();
    assertThat(Files.readString(configured.path(), StandardCharsets.UTF_8))
        .contains(yaml(ONTOLOGY_POLICY_SET_V5));

    Path linkScope =
        writeBusinessLinkThreeLinkScopeV3(
            temporaryDirectory.resolve("lean-link-type-source-scope-v3.json"));
    CliResult identified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            corpusRunId,
            "--scope",
            linkScope.toString());
    assertThat(identified.exitCode()).withFailMessage(cliDiagnostics(identified)).isZero();
    String identificationRunId = runId(identified);
    JsonNode identification =
        JSON.readTree(
            artifact(configured.path(), identificationRunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(identification.path("schemaVersion").asText())
        .isEqualTo("ontology-identification-v5");
    assertThat(arrayValues(identification.path("taskRecords")))
        .extracting(row -> row.path("status").asText())
        .containsExactly("REVIEWED", "REVIEWED", "REVIEWED");
    assertThat(requests)
        .extracting(StructuredModelRequest::taskKind)
        .containsExactly(
            "ONTOLOGY_FORMAL_LINK_EXTRACT",
            "ONTOLOGY_FORMAL_LINK_REVIEW",
            "ONTOLOGY_FORMAL_LINK_EXTRACT",
            "ONTOLOGY_FORMAL_LINK_REVIEW",
            "ONTOLOGY_FORMAL_LINK_EXTRACT",
            "ONTOLOGY_FORMAL_LINK_REVIEW");

    Path compareSelection =
        writeObjectTypeCorrespondenceSelectionV4(
            temporaryDirectory.resolve("lean-link-type-selection-v4.json"),
            corpusRunId,
            identificationRunId,
            List.of("link-task-a", "link-task-b", "link-task-c"));
    JsonNode selected = JSON.readTree(Files.readAllBytes(compareSelection));
    assertThat(selected.path("schemaVersion").asText()).isEqualTo("ontology-selection-v4");
    assertThat(selected.path("relationProfile").asText()).isEqualTo("OBJECT_TYPE_CORRESPONDENCE");
    int typeRequestStart = requests.size();
    CliResult related =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            compareSelection.toString());
    assertThat(related.exitCode()).withFailMessage(cliDiagnostics(related)).isIn(0, 2);
    String relationRunId = runId(related);
    JsonNode relations =
        JSON.readTree(artifact(configured.path(), relationRunId, "ONTOLOGY_RELATIONS").stdout());
    assertThat(relations.path("schemaVersion").asText()).isEqualTo("ontology-relations-v5");
    assertThat(relations.path("relationProfile").asText()).isEqualTo("OBJECT_TYPE_CORRESPONDENCE");
    assertThat(relations.path("effectiveSelection").path("relationProfile").asText())
        .isEqualTo("OBJECT_TYPE_CORRESPONDENCE");
    assertThat(relations.path("typeCandidates").isArray()).isTrue();
    List<JsonNode> candidates = arrayValues(relations.path("typeCandidates"));
    assertThat(candidates).hasSize(12);
    assertThat(candidates).extracting(row -> row.path("pairRef").asText()).doesNotHaveDuplicates();
    assertThat(candidates).extracting(row -> row.path("taskId").asText()).doesNotHaveDuplicates();
    List<JsonNode> typeTaskRecords = arrayValues(relations.path("taskRecords"));
    assertThat(typeTaskRecords.size()).isBetween(3, 4);
    assertThat(typeTaskRecords.subList(0, 3))
        .extracting(row -> row.path("status").asText())
        .containsExactly("REVIEWED", "REJECTED", "REVIEWED");
    if (typeTaskRecords.size() == 4) {
      assertThat(typeTaskRecords.get(3).path("status").asText()).isEqualTo("UNPROCESSED");
    }
    assertThat(typeTaskRecords)
        .extracting(row -> row.path("taskId").asText())
        .containsExactlyElementsOf(
            candidates.subList(0, typeTaskRecords.size()).stream()
                .map(row -> row.path("taskId").asText())
                .toList());
    List<JsonNode> comparisonOutcomes = arrayValues(relations.path("taskOutcomes"));
    assertThat(comparisonOutcomes).hasSize(12);
    assertThat(comparisonOutcomes.subList(0, 3))
        .extracting(row -> row.path("status").asText())
        .containsExactly("REVIEWED", "REJECTED", "REVIEWED");
    assertThat(comparisonOutcomes.subList(3, 12))
        .allSatisfy(row -> assertThat(row.path("status").asText()).isEqualTo("UNPROCESSED"));
    List<StructuredModelRequest> comparisonRequests =
        requests.subList(typeRequestStart, requests.size());
    assertThat(comparisonRequests)
        .extracting(StructuredModelRequest::taskKind)
        .containsExactly(
            "ONTOLOGY_FORMAL_TYPE_COMPARE_EXTRACT",
            "ONTOLOGY_FORMAL_TYPE_COMPARE_REVIEW",
            "ONTOLOGY_FORMAL_TYPE_COMPARE_EXTRACT",
            "ONTOLOGY_FORMAL_TYPE_COMPARE_REVIEW",
            "ONTOLOGY_FORMAL_TYPE_COMPARE_EXTRACT",
            "ONTOLOGY_FORMAL_TYPE_COMPARE_REVIEW");
    assertThat(typeInputs).hasSize(6);
    assertThat(comparisonRequests)
        .extracting(StructuredModelRequest::taskId)
        .doesNotHaveDuplicates();
    assertThat(relations.path("status").asText()).isEqualTo("PARTIAL");

    JsonNode successfulTask =
        typeTaskRecords.stream()
            .filter(row -> "REVIEWED".equals(row.path("status").asText()))
            .findFirst()
            .orElseThrow();
    JsonNode typeObservation =
        queryTaskObservation(
            configured.path(), relationRunId, successfulTask.path("producingTaskId").asText());
    assertThat(typeObservation.path("schemaVersion").asText())
        .isEqualTo("ontology-task-observation-v2");
    JsonNode savedTypeResult = typeObservation.path("completion");
    assertThat(savedTypeResult.path("schemaVersion").asText())
        .isEqualTo("ontology-formal-typed-job-result-v5");
    assertThat(savedTypeResult.path("privateReadingPacket").path("schemaVersion").asText())
        .isEqualTo("ontology-reading-packet-v7");
    assertThat(savedTypeResult.path("review").path("schemaVersion").asText())
        .isEqualTo("ontology-object-type-review-v1");
    JsonNode typeProjection = savedTypeResult.path("definitionDocument");
    assertThat(typeProjection.path("definitions").path("objects").isEmpty()).isTrue();
    assertThat(typeProjection.path("definitions").path("links").isEmpty()).isTrue();
    assertThat(typeProjection.path("identityDecisions")).hasSize(1);
    assertThat(typeProjection.path("identityDecisions").get(0).path("leftRef").asText())
        .isEqualTo("B1");
    assertThat(typeProjection.path("identityDecisions").get(0).path("rightRef").asText())
        .isEqualTo("B2");
    Set<String> savedPacketRefs = new LinkedHashSet<>();
    savedTypeResult
        .path("privateReadingPacket")
        .path("units")
        .forEach(unit -> savedPacketRefs.add(unit.path("localRef").asText()));
    assertThat(savedPacketRefs).isNotEmpty();
    for (String side : List.of("left", "right")) {
      List<String> refs =
          arrayValues(savedTypeResult.path("comparisonBinding").path(side).path("sourceRefs"))
              .stream()
              .map(JsonNode::asText)
              .toList();
      assertThat(refs).isNotEmpty();
      assertThat(savedPacketRefs).containsAll(refs);
    }

    Path emptySelection =
        writeObjectTypeCorrespondenceSelectionV4(
            temporaryDirectory.resolve("lean-link-type-empty-selection-v4.json"),
            corpusRunId,
            identificationRunId,
            List.of("link-task-a"));
    int requestsBeforeEmptySelection = requests.size();
    CliResult emptyRelated =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            emptySelection.toString());
    assertThat(emptyRelated.exitCode()).withFailMessage(cliDiagnostics(emptyRelated)).isIn(0, 2);
    String emptyRelationRunId = runId(emptyRelated);
    JsonNode emptyRelations =
        JSON.readTree(
            artifact(configured.path(), emptyRelationRunId, "ONTOLOGY_RELATIONS").stdout());
    assertThat(emptyRelations.path("typeCandidates").isArray()).isTrue();
    assertThat(emptyRelations.path("typeCandidates").isEmpty()).isTrue();
    assertThat(containsText(emptyRelations, "NO_TYPE_CANDIDATES")).isTrue();
    assertThat(requests).hasSize(requestsBeforeEmptySelection);

    Path capacityConfiguration = temporaryDirectory.resolve("lean-type-capacity-v5.yaml");
    String capacityYaml =
        Files.readString(configured.path(), StandardCharsets.UTF_8)
            .replace("maxUnitBytes: 1048576", "maxUnitBytes: 1");
    assertThat(capacityYaml).contains("maxUnitBytes: 1");
    Files.writeString(capacityConfiguration, capacityYaml, StandardCharsets.UTF_8);
    CliResult capacityRelated =
        executeWithFactory(
            capacityConfiguration,
            providerFactory,
            "relate-ontology",
            "--selection",
            compareSelection.toString());
    assertThat(capacityRelated.exitCode()).isEqualTo(2);
    JsonNode capacityRelations =
        JSON.readTree(
            artifact(capacityConfiguration, runId(capacityRelated), "ONTOLOGY_RELATIONS").stdout());
    assertThat(capacityRelations.path("preparationFailures")).hasSize(12);
    assertThat(capacityRelations.path("taskRecords")).isEmpty();
    assertThat(capacityRelations.path("taskOutcomes")).hasSize(12);
    JsonNode capacityTaskIndex =
        JSON.readTree(
            artifact(capacityConfiguration, runId(capacityRelated), "ONTOLOGY_TASK_INDEX")
                .stdout());
    assertThat(capacityTaskIndex.path("schemaVersion").asText())
        .isEqualTo("ontology-task-index-v2");
    assertThat(capacityTaskIndex.path("taskOutcomes"))
        .isEqualTo(capacityRelations.path("taskOutcomes"));
    assertThat(requests).hasSize(requestsBeforeEmptySelection);
    for (JsonNode failure : capacityRelations.path("preparationFailures")) {
      assertThat(candidates.stream().map(candidate -> candidate.path("taskId").asText()).toList())
          .contains(failure.path("taskId").asText());
      assertThat(failure.path("capacity").path("boundary").asText())
          .isEqualTo("COMPLETE_UNIT_BODY");
      assertThat(failure.path("capacity").path("limitBytes").asLong()).isEqualTo(1);
      assertThat(failure.path("capacity").path("measuredBytes").asLong()).isGreaterThan(1);
    }

    Path publishSelection =
        writePublishSelectionV4WithRelations(
            temporaryDirectory.resolve("lean-link-type-publish-v4.json"),
            corpusRunId,
            List.of(identificationRunId),
            List.of(relationRunId, emptyRelationRunId));
    int requestsBeforePublish = requests.size();
    CliResult published =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "publish-ontology",
            "--selection",
            publishSelection.toString());
    assertThat(published.exitCode()).withFailMessage(cliDiagnostics(published)).isIn(0, 2);
    String publishedRunId = runId(published);
    assertThat(requests).hasSize(requestsBeforePublish);
    assertThat(availablePublicArtifactKeys(configured.path(), publishedRunId))
        .containsExactlyInAnyOrder(
            "ONTOLOGY", "ONTOLOGY_COVERAGE", "ONTOLOGY_REVIEW", "ONTOLOGY_SOURCE_INDEX");
    JsonNode ontology =
        JSON.readTree(artifact(configured.path(), publishedRunId, "ONTOLOGY").stdout());
    JsonNode coverage =
        JSON.readTree(artifact(configured.path(), publishedRunId, "ONTOLOGY_COVERAGE").stdout());
    JsonNode review =
        JSON.readTree(artifact(configured.path(), publishedRunId, "ONTOLOGY_REVIEW").stdout());
    assertThat(ontology.path("schemaVersion").asText()).isEqualTo("ontology-v2");
    assertThat(coverage.path("schemaVersion").asText()).isEqualTo("ontology-coverage-v5");
    assertThat(review.path("schemaVersion").asText()).isEqualTo("ontology-review-v5");
    ReopenedModulePublication o0Module =
        reopenedOntologyModule(fixture.runStore(), corpusRunId, ONTOLOGY_POLICY_SET_V5);
    ReopenedModulePublication o1Module =
        reopenedOntologyModule(fixture.runStore(), identificationRunId, ONTOLOGY_POLICY_SET_V5);
    ReopenedModulePublication o2Module =
        reopenedOntologyModule(fixture.runStore(), relationRunId, ONTOLOGY_POLICY_SET_V5);
    ReopenedModulePublication o3Module =
        reopenedOntologyModule(fixture.runStore(), publishedRunId, ONTOLOGY_POLICY_SET_V5);
    assertThat(o0Module.payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .containsExactly("ontology-corpus-v2");
    assertThat(o1Module.payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .containsExactly("ontology-identification-v5");
    assertThat(o2Module.payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .containsExactly("ontology-relations-v5");
    assertThat(o3Module.payloads())
        .extracting(payload -> payload.descriptor().fileName())
        .containsExactlyInAnyOrder(
            "ontology-coverage.json",
            "ontology-review.json",
            "ontology-sources.jsonl",
            "ontology.json");
    assertThat(o3Module.payloads())
        .extracting(payload -> payload.descriptor().schemaVersion())
        .containsExactlyInAnyOrder(
            "ontology-coverage-v5", "ontology-review-v5", "ontology-source-v1", "ontology-v2");
    assertThat(o3Module.receipt().upstreamArtifacts())
        .containsAll(modulePayloadReferences(o1Module))
        .containsAll(modulePayloadReferences(o2Module));
    Set<String> typeCandidateTaskIds =
        candidates.stream()
            .map(row -> row.path("taskId").asText())
            .collect(java.util.stream.Collectors.toSet());
    List<JsonNode> publishedTypeOutcomes =
        arrayValues(review.path("taskOutcomes")).stream()
            .filter(row -> typeCandidateTaskIds.contains(row.path("taskId").asText()))
            .toList();
    assertThat(publishedTypeOutcomes).hasSize(12);
    assertThat(publishedTypeOutcomes)
        .filteredOn(row -> "UNPROCESSED".equals(row.path("status").asText()))
        .hasSize(9);
    CliResult overview =
        executePublic(
            configured.path(),
            "artifact",
            "--run",
            publishedRunId,
            "--key",
            "ONTOLOGY_BUSINESS_OVERVIEW",
            "--max-bytes",
            "5242880");
    assertThat(overview.exitCode()).withFailMessage(cliDiagnostics(overview)).isZero();
    assertThat(overview.stdout()).startsWith("<!doctype html>").contains("ontology-business-graph");
  }

  @Test
  void policyV5ReviewedLinkObjectsFeedActionAndAnalyticAsExactExternalSources() throws Exception {
    TechnicalFixture fixture = prepareRealR4("lean-link-v5-action-analytic-consumer");
    ConfiguredTypedPipeline configured =
        writeBusinessLinkTypedPipelineConfigurationV5(
            "ontology-lean-link-v5-action-analytic-consumer.yaml", fixture);
    List<StructuredModelRequest> requests = new ArrayList<>();
    AtomicInteger linkExtracts = new AtomicInteger();
    AtomicReference<Set<JsonNode>> selectedLinkDefinitions = new AtomicReference<>(Set.of());
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String taskKind = input.path("taskKind").asText();
                boolean review = request.taskKind().contains("REVIEW");
                ImmutableBytes response;
                if ("LINK".equals(taskKind)) {
                  assertThat(requestSchemaVersion(request))
                      .isEqualTo(review ? "ontology-link-review-v2" : "ontology-link-candidate-v2");
                  assertThat(input.path("readingPacket").path("schemaVersion").asText())
                      .isEqualTo("ontology-model-reading-v7");
                  if (review) {
                    ObjectNode reviewed = (ObjectNode) input.path("actualDraft").deepCopy();
                    reviewed.put("schemaVersion", "ontology-link-review-v2");
                    response = CANONICAL.encodeCanonical(reviewed);
                  } else {
                    response =
                        leanRuntimeLinkResponse(
                            "ontology-link-candidate-v2",
                            input,
                            Integer.toString(linkExtracts.incrementAndGet()));
                  }
                } else if ("ACTION".equals(taskKind) || "ANALYTIC".equals(taskKind)) {
                  assertThat(input.path("questionId").asText()).isEqualTo("Q_LINK_CONSUMER");
                  assertThat(input.path("readingPacket").path("schemaVersion").asText())
                      .isEqualTo("ontology-model-reading-v5");
                  assertThat(requestSchemaVersion(request))
                      .isEqualTo(
                          review ? "ontology-typed-review-v4" : "ontology-typed-candidate-v4");
                  JsonNode entries = input.path("reviewedCatalog").path("entries");
                  assertThat(entries).hasSize(2);
                  assertThat(entries)
                      .extracting(row -> row.path("definitionType").asText())
                      .containsOnly("objects");
                  Set<JsonNode> receivedDefinitions =
                      arrayValues(entries).stream()
                          .map(row -> row.path("definition"))
                          .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                  Set<JsonNode> expectedDefinitions =
                      selectedLinkDefinitions.get().stream()
                          .map(
                              definition -> {
                                ObjectNode projected = (ObjectNode) definition.deepCopy();
                                projected.remove("evidenceRefs");
                                return (JsonNode) projected;
                              })
                          .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                  assertThat(receivedDefinitions)
                      .containsExactlyInAnyOrderElementsOf(expectedDefinitions);
                  assertThat(entries)
                      .allSatisfy(
                          row -> {
                            assertThat(row.path("propertyRefs").isArray()).isTrue();
                            assertThat(row.path("propertyRefs").isEmpty()).isTrue();
                          });
                  if (review) {
                    ObjectNode reviewed = (ObjectNode) input.path("actualDraft").deepCopy();
                    reviewed.put("schemaVersion", "ontology-typed-review-v4");
                    response = CANONICAL.encodeCanonical(reviewed);
                  } else if ("ACTION".equals(taskKind)) {
                    response =
                        businessLinkFormalActionResponseV4(
                            "ontology-typed-candidate-v4",
                            input.path("questionId").asText(),
                            "E1",
                            entries.get(0).path("catalogRef").asText());
                  } else {
                    ObjectNode definitions = JSON.createObjectNode();
                    definitions.putArray("dimensions");
                    definitions.putArray("measures");
                    definitions.putArray("metrics");
                    ArrayNode unresolved = JSON.createArrayNode();
                    ObjectNode limitation = unresolved.addObject();
                    limitation.put("issueId", "ANALYTIC_PROPERTY_SCOPE_NOT_INVESTIGATED");
                    limitation.put("proposedKind", "ANALYTIC");
                    limitation.put(
                        "description",
                        "The selected LINK skeleton did not investigate object properties or analytic grain.");
                    limitation
                        .putArray("knownDefinitionRefs")
                        .add(entries.get(0).path("catalogRef").asText());
                    limitation.putArray("relatedLocalDefinitionRefs");
                    ObjectNode requirement = limitation.putArray("missingRequirements").addObject();
                    requirement.put("field", "property and grain evidence");
                    requirement.put(
                        "reason",
                        "The reviewed LINK object explicitly retains properties as uninvestigated.");
                    requirement.putArray("unitRefs");
                    limitation
                        .putArray("evidenceRefs")
                        .add(input.path("readingPacket").path("units").get(0).path("ref").asText());
                    ObjectNode candidate =
                        (ObjectNode)
                            CANONICAL.parseCanonical(
                                typedTaskResponse(
                                    "ontology-typed-candidate-v4",
                                    "ANALYTIC",
                                    definitions,
                                    unresolved));
                    candidate.putArray("clueDispositions");
                    response = CANONICAL.encodeCanonical(candidate);
                  }
                } else {
                  throw new AssertionError("unexpected policy-v5 consumer task " + taskKind);
                }
                return new StructuredModelResponse(response, identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    String corpusRunId = runId(prepared);

    Path linkScope =
        writeBusinessLinkTwoLinkScopeV3(
            temporaryDirectory.resolve("lean-link-v5-action-analytic-source-scope.json"));
    CliResult identifiedLink =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            corpusRunId,
            "--scope",
            linkScope.toString());
    assertThat(identifiedLink.exitCode()).withFailMessage(cliDiagnostics(identifiedLink)).isZero();
    String linkRunId = runId(identifiedLink);
    JsonNode linkIdentification =
        JSON.readTree(artifact(configured.path(), linkRunId, "ONTOLOGY_IDENTIFICATION").stdout());
    JsonNode selectedLinkTask = findTaskRecord(linkIdentification, "link-task-a");
    assertThat(selectedLinkTask.path("status").asText()).isEqualTo("REVIEWED");
    JsonNode linkCompletion =
        queryTaskObservation(
                configured.path(), linkRunId, selectedLinkTask.path("producingTaskId").asText())
            .path("completion");
    assertThat(linkCompletion.path("schemaVersion").asText())
        .isEqualTo("ontology-formal-typed-job-result-v5");
    assertThat(linkCompletion.path("review").path("schemaVersion").asText())
        .isEqualTo("ontology-link-review-v2");
    assertThat(linkCompletion.path("privateReadingPacket").path("schemaVersion").asText())
        .isEqualTo("ontology-reading-packet-v7");
    Set<JsonNode> sourceDefinitions =
        arrayValues(linkCompletion.path("definitionDocument").path("definitions").path("objects"))
            .stream()
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    assertThat(sourceDefinitions).hasSize(2);
    assertThat(sourceDefinitions)
        .extracting(definition -> definition.path("name").asText())
        .containsExactlyInAnyOrder("Customer", "Auxiliary 1");
    selectedLinkDefinitions.set(sourceDefinitions);
    JsonNode sourceIdentity = linkCompletion.path("identity");

    ObjectNode enrichmentScope = JSON.createObjectNode();
    enrichmentScope.put("schemaVersion", "ontology-scope-v3");
    enrichmentScope.put("mode", "QUESTION");
    enrichmentScope.put("selectionMode", "EXPLICIT");
    enrichmentScope.put("purpose", "ENRICHMENT");
    ObjectNode question = enrichmentScope.putArray("questions").addObject();
    question.put("questionId", "Q_LINK_CONSUMER");
    question.put(
        "question",
        "Describe only source-supported actions and analytic limits for these objects.");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    ObjectNode source = question.putArray("objectSources").addObject();
    source.put("identificationRun", linkRunId);
    source.put("questionId", "Q_LINK_SOURCE");
    source.putArray("taskIds").add("link-task-a");
    ArrayNode enrichmentTasks = question.putArray("tasks");
    for (String kind : List.of("ACTION", "ANALYTIC")) {
      ObjectNode task = enrichmentTasks.addObject();
      task.put("taskId", "consumer-" + kind.toLowerCase());
      task.put("taskKind", kind);
      task.put("readingMode", "EXPLICIT");
      task.putArray("anchorRefs");
      addUse(task.putArray("unitUses"), "U1", "E1");
      addUse(task.putArray("requiredUnitUses"), "U1", "E1");
    }
    Path enrichmentPath =
        writeJson(
            temporaryDirectory.resolve("lean-link-v5-action-analytic-enrichment-scope.json"),
            enrichmentScope);
    int requestsBeforeEnrichment = requests.size();
    CliResult enriched =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            corpusRunId,
            "--scope",
            enrichmentPath.toString());
    assertThat(enriched.exitCode()).withFailMessage(cliDiagnostics(enriched)).isZero();
    String enrichmentRunId = runId(enriched);
    assertThat(requests.subList(requestsBeforeEnrichment, requests.size()))
        .extracting(StructuredModelRequest::taskKind)
        .containsExactly(
            "ONTOLOGY_FORMAL_ACTION_EXTRACT",
            "ONTOLOGY_FORMAL_ACTION_REVIEW",
            "ONTOLOGY_FORMAL_ANALYTIC_EXTRACT",
            "ONTOLOGY_FORMAL_ANALYTIC_REVIEW");
    JsonNode enrichment =
        JSON.readTree(
            artifact(configured.path(), enrichmentRunId, "ONTOLOGY_IDENTIFICATION").stdout());
    assertThat(enrichment.path("schemaVersion").asText()).isEqualTo("ontology-identification-v5");
    assertThat(arrayValues(enrichment.path("taskRecords")))
        .extracting(row -> row.path("taskId").asText() + "/" + row.path("status").asText())
        .containsExactly("consumer-action/REVIEWED", "consumer-analytic/REVIEWED");

    for (String taskId : List.of("consumer-action", "consumer-analytic")) {
      JsonNode taskRecord = findTaskRecord(enrichment, taskId);
      JsonNode completion =
          queryTaskObservation(
                  configured.path(), enrichmentRunId, taskRecord.path("producingTaskId").asText())
              .path("completion");
      assertThat(completion.path("schemaVersion").asText())
          .isEqualTo("ontology-formal-typed-job-result-v3");
      assertThat(completion.path("review").path("schemaVersion").asText())
          .isEqualTo("ontology-typed-review-v4");
      assertThat(completion.path("privateReadingPacket").path("schemaVersion").asText())
          .isEqualTo("ontology-reading-packet-v5");
      assertThat(completion.path("catalogMapping").path("schemaVersion").asText())
          .isEqualTo("ontology-reviewed-catalog-v4");
      JsonNode catalogEntries = completion.path("catalogMapping").path("entries");
      assertThat(catalogEntries).hasSize(2);
      assertThat(catalogEntries)
          .allSatisfy(
              entry -> {
                assertThat(entry.path("identity").path("corpusIdentity").asText())
                    .isEqualTo(sourceIdentity.path("corpusIdentity").asText());
                assertThat(entry.path("identity").path("producingTaskId").asText())
                    .isEqualTo(sourceIdentity.path("producingTaskId").asText());
                assertThat(entry.path("identity").path("reviewVersion").asText())
                    .isEqualTo(sourceIdentity.path("reviewVersion").asText());
              });
      assertThat(arrayValues(catalogEntries))
          .extracting(entry -> entry.path("identity").path("localId").asText())
          .containsExactlyInAnyOrderElementsOf(
              sourceDefinitions.stream()
                  .map(definition -> definition.path("localId").asText())
                  .toList());
    }
  }

  private ConfiguredTypedPipeline writeBusinessLinkTypedPipelineConfigurationV5(
      String name, TechnicalFixture fixture) throws Exception {
    ConfiguredTypedPipeline configured =
        writeBusinessLinkTypedPipelineConfigurationV4(name, fixture);
    String original = Files.readString(configured.path(), StandardCharsets.UTF_8);
    assertThat(original).contains(yaml(ONTOLOGY_POLICY_SET_V4));
    String v5 =
        original
            .replace(yaml(ONTOLOGY_POLICY_SET_V4), yaml(ONTOLOGY_POLICY_SET_V5))
            .replace("maxRequests: 8", "maxRequests: 6");
    assertThat(v5).isNotEqualTo(original).contains(yaml(ONTOLOGY_POLICY_SET_V5));
    assertThat(v5).contains("maxRequests: 6");
    Files.writeString(configured.path(), v5, StandardCharsets.UTF_8);
    return configured;
  }

  private Path writeBusinessLinkThreeLinkScopeV3(Path path) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v3");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    root.put("purpose", "SKELETON");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q_LINK_SOURCE");
    question.put("question", "Describe only the source-backed record endpoints and links.");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs").add("K1");
    question.putArray("objectSources");
    ArrayNode tasks = question.putArray("tasks");
    for (String taskId : List.of("link-task-a", "link-task-b", "link-task-c")) {
      ObjectNode task = tasks.addObject();
      task.put("taskId", taskId);
      task.put("taskKind", "LINK");
      task.put("readingMode", "TECHNICAL_BUNDLE");
      task.putArray("anchorRefs").add("K1");
      addUse(task.putArray("unitUses"), "U1", "E1");
      addUse(task.putArray("requiredUnitUses"), "U1", "E1");
    }
    return writeJson(path, root);
  }

  private Path writeObjectTypeCorrespondenceSelectionV4(
      Path path, String corpusRun, String identificationRun, List<String> sourceTaskIds)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v4");
    root.put("operation", "RELATE");
    root.put("relationProfile", "OBJECT_TYPE_CORRESPONDENCE");
    root.put("corpusRun", corpusRun);
    root.putArray("identificationRuns").add(identificationRun);
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q_TYPE_COMPARE");
    question.put("question", "Compare only the selected reviewed object definitions.");
    question.put("taskId", "compare-candidates");
    question.put("readingMode", "TECHNICAL_BUNDLE");
    question.putArray("entryRefs");
    question.putArray("clueRefs");
    question.putArray("unitUses");
    question.putArray("requiredUnitUses");
    ObjectNode source = question.putArray("objectSources").addObject();
    source.put("identificationRun", identificationRun);
    source.put("questionId", "Q_LINK_SOURCE");
    ArrayNode taskIds = source.putArray("taskIds");
    sourceTaskIds.forEach(taskIds::add);
    return writeJson(path, root);
  }

  private Path writePublishSelectionV4WithRelations(
      Path path, String corpusRun, List<String> identificationRuns, List<String> relationRuns)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v4");
    root.put("operation", "PUBLISH");
    root.put("corpusRun", corpusRun);
    ArrayNode identifications = root.putArray("identificationRuns");
    identificationRuns.forEach(identifications::add);
    ArrayNode relations = root.putArray("relationRuns");
    relationRuns.forEach(relations::add);
    return writeJson(path, root);
  }

  private String requestSchemaVersion(StructuredModelRequest request) {
    return CANONICAL
        .parseCanonical(request.outputJsonSchema())
        .path("properties")
        .path("schemaVersion")
        .path("const")
        .asText();
  }

  private ImmutableBytes leanRuntimeLinkResponse(
      String schemaVersion, JsonNode input, String suffix) {
    String questionId = input.path("questionId").asText();
    JsonNode source = input.path("readingPacket").path("units").get(0);
    String sourceRef = source.path("ref").asText();
    String entryRef = source.path("entryUses").get(0).asText();
    String customerKey = "customer";
    String auxiliaryKey = "auxiliary-" + suffix;
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", schemaVersion);
    response.put("taskKind", "LINK");
    response
        .putArray("objects")
        .add(leanRuntimeLinkObject(questionId, entryRef, sourceRef, customerKey, "Customer"))
        .add(
            leanRuntimeLinkObject(
                questionId, entryRef, sourceRef, auxiliaryKey, "Auxiliary " + suffix));
    ObjectNode link = response.putArray("links").addObject();
    link.put("fromKey", customerKey);
    link.put("toKey", auxiliaryKey);
    link.put("name", "selected record reference");
    link.put("definition", "The selected source passes a value between record endpoints.");
    link.put("certainty", "INFERRED");
    link.set("scope", leanRuntimeLinkScope(questionId, entryRef));
    ObjectNode mechanism = link.putObject("mechanism");
    mechanism.put("text", "The source passes the selected record key.");
    mechanism.putArray("objectKeys").add(auxiliaryKey);
    mechanism.putArray("evidenceRefs").add(sourceRef);
    ObjectNode condition = link.putArray("conditions").addObject();
    condition.put("text", "The operation uses the key only in this selected path.");
    condition.putArray("objectKeys").add(customerKey);
    condition.putArray("evidenceRefs").add(sourceRef);
    condition.putArray("unknowns");
    link.putArray("evidenceRefs").add(sourceRef);
    ObjectNode unknown = link.putArray("unknowns").addObject();
    unknown.put("field", "cardinality");
    unknown.put("reason", "Relationship multiplicity was not investigated.");
    unknown.putArray("missingUnitRefs");
    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", input.path("visibleClueRefs").get(0).asText());
    disposition.put("outcome", "LINK_SUPPORTED");
    disposition.putArray("linkIndexes").add(0);
    disposition.put("reason", "The selected source supports this local link.");
    response.putArray("unresolved");
    response.putArray("corrections");
    return CANONICAL.encodeCanonical(response);
  }

  private ObjectNode leanRuntimeLinkObject(
      String questionId, String entryRef, String sourceRef, String objectKey, String name) {
    ObjectNode object = JSON.createObjectNode();
    object.put("objectKey", objectKey);
    object.put("name", name);
    object.put("definition", name + " is a record endpoint visible in the selected method.");
    object.put("displayRole", "TECHNICAL_OR_UNKNOWN");
    object.put("certainty", "INFERRED");
    object.set("scope", leanRuntimeLinkScope(questionId, entryRef));
    object.putArray("backing");
    object.putArray("variants");
    object.putArray("evidenceRefs").add(sourceRef);
    ArrayNode unknowns = object.putArray("unknowns");
    for (String field : List.of("identities", "properties")) {
      ObjectNode unknown = unknowns.addObject();
      unknown.put("field", field);
      unknown.put("reason", "This field was not investigated by the LINK task.");
      unknown.putArray("missingUnitRefs");
    }
    return object;
  }

  private ObjectNode leanRuntimeLinkScope(String questionId, String entryRef) {
    ObjectNode scope = JSON.createObjectNode();
    scope.put("questionRef", questionId);
    scope.putArray("entryUseRefs").add(entryRef);
    scope.putArray("variants");
    return scope;
  }

  private ImmutableBytes typeCompareRuntimeResponse(JsonNode input, String decisionKind) {
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", "ontology-object-type-candidate-v1");
    response.put("taskKind", "TYPE_COMPARE");
    response.put("comparisonScope", "SELECTED_OBJECT_DEFINITIONS");
    ObjectNode decision = response.putObject("decision");
    decision.put("kind", decisionKind);
    decision.put("explanation", "The selected reviewed definitions support this comparison.");
    decision.putArray("conditions");
    ArrayNode citations = decision.putArray("evidenceRefs");
    for (String catalogRef : List.of("B1", "B2")) {
      JsonNode entry = catalogEntryByRef(input, catalogRef);
      assertThat(entry.path("sourceRefs").isArray()).isTrue();
      assertThat(entry.path("sourceRefs").isEmpty()).isFalse();
      citations.add(entry.path("sourceRefs").get(0).asText());
    }
    decision.putArray("unknowns");
    response.putArray("corrections");
    return CANONICAL.encodeCanonical(response);
  }

  private void assertCurrentTypeEvidence(JsonNode input, JsonNode response) {
    Set<String> packetRefs = new LinkedHashSet<>();
    input
        .path("readingPacket")
        .path("units")
        .forEach(unit -> packetRefs.add(unit.path("ref").asText()));
    assertThat(packetRefs).isNotEmpty();
    Set<String> leftRefs = textValues(catalogEntryByRef(input, "B1").path("sourceRefs"));
    Set<String> rightRefs = textValues(catalogEntryByRef(input, "B2").path("sourceRefs"));
    assertThat(leftRefs).isNotEmpty();
    assertThat(rightRefs).isNotEmpty();
    assertThat(packetRefs).containsAll(leftRefs).containsAll(rightRefs);
    Set<String> cited = textValues(response.path("decision").path("evidenceRefs"));
    assertThat(cited).containsAnyElementsOf(leftRefs).containsAnyElementsOf(rightRefs);
    assertThat(packetRefs).containsAll(cited);
  }

  private JsonNode catalogEntryByRef(JsonNode input, String catalogRef) {
    for (JsonNode entry : input.path("reviewedCatalog").path("entries")) {
      if (catalogRef.equals(entry.path("catalogRef").asText())) return entry;
    }
    throw new AssertionError("missing selected object catalog entry " + catalogRef);
  }

  private Set<String> textValues(JsonNode array) {
    Set<String> result = new LinkedHashSet<>();
    array.forEach(value -> result.add(value.asText()));
    return result;
  }

  private TechnicalFixture prepareRealR4(String name) throws Exception {
    return prepareRealR4(name, JAVA_SOURCE, 16, OntologyFormalRuntimeContractsTest::session);
  }

  private TechnicalFixture prepareRealR4WithDdl(
      String name, Map<String, byte[]> schemaFiles, List<String> exclusions) throws Exception {
    return prepareRealR4(
        name,
        DDL_HANDLER_SOURCE,
        16,
        OntologyFormalRuntimeContractsTest::sessionWithMapper,
        new SchemaRuntimeFixture(schemaFiles, exclusions));
  }

  private TechnicalFixture prepareRealR4WithDdlAndRequest(
      String name, Map<String, byte[]> schemaFiles, List<String> exclusions) throws Exception {
    return prepareRealR4(
        name,
        DDL_HANDLER_SOURCE,
        16,
        OntologyFormalRuntimeContractsTest::sessionWithMapper,
        new SchemaRuntimeFixture(schemaFiles, exclusions),
        true);
  }

  private TechnicalFixture prepareRealR4WithHttpRouteCount(String name, int routeCount)
      throws Exception {
    if (routeCount < 1) {
      throw new IllegalArgumentException("fixture route count must be positive");
    }
    String javaSource = multiRouteJavaSource(routeCount);
    return prepareRealR4(
        name,
        javaSource,
        routeCount,
        environment -> multiRouteSession(environment, javaSource, routeCount));
  }

  private TechnicalFixture prepareRealR4(
      String name,
      String javaSource,
      int maxEntries,
      Function<JavaCompilationEnvironment, JavaCodeSession> sessionFactory)
      throws Exception {
    return prepareRealR4(name, javaSource, maxEntries, sessionFactory, null);
  }

  private TechnicalFixture prepareRealR4(
      String name,
      String javaSource,
      int maxEntries,
      Function<JavaCompilationEnvironment, JavaCodeSession> sessionFactory,
      SchemaRuntimeFixture schemaFixture)
      throws Exception {
    return prepareRealR4(name, javaSource, maxEntries, sessionFactory, schemaFixture, false);
  }

  private TechnicalFixture prepareRealR4(
      String name,
      String javaSource,
      int maxEntries,
      Function<JavaCompilationEnvironment, JavaCodeSession> sessionFactory,
      SchemaRuntimeFixture schemaFixture,
      boolean frontendRequestEnabled)
      throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve(name + "-source"));
    Files.createDirectories(sourceRoot.resolve("src/main/java/fixture"));
    Files.writeString(
        sourceRoot.resolve("pom.xml"), pom("neutral-" + name), StandardCharsets.UTF_8);
    Files.writeString(sourceRoot.resolve(JAVA_PATH), javaSource, StandardCharsets.UTF_8);
    if (schemaFixture != null) {
      Files.createDirectories(sourceRoot.resolve(DDL_XML_MAPPER_PATH).getParent());
      Files.writeString(
          sourceRoot.resolve(DDL_JAVA_MAPPER_PATH), DDL_MAPPER_SOURCE, StandardCharsets.UTF_8);
      Files.writeString(
          sourceRoot.resolve(DDL_XML_MAPPER_PATH), DDL_MAPPER_XML, StandardCharsets.UTF_8);
      for (Map.Entry<String, byte[]> schemaFile : schemaFixture.schemaFiles().entrySet()) {
        Path file = sourceRoot.resolve(schemaFile.getKey());
        Files.createDirectories(file.getParent());
        Files.write(file, schemaFile.getValue());
      }
    }
    Path frontendNodeIdentity = physicalRoot.resolve(name + "-frontend-node-identity");
    if (frontendRequestEnabled) {
      Path frontendSource = sourceRoot.resolve(FRONTEND_REQUEST_PATH);
      Files.createDirectories(frontendSource.getParent());
      Files.writeString(frontendSource, FRONTEND_REQUEST_SOURCE, StandardCharsets.UTF_8);
      Files.writeString(
          frontendNodeIdentity, "fixture launcher; never executed\n", StandardCharsets.UTF_8);
    }
    Path preparationWorkspace = Files.createDirectory(physicalRoot.resolve(name + "-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve(name + "-run-store"));
    Path archive = preparationWorkspace.resolve("prepared-source-archive");
    Path sourceConfig = physicalRoot.resolve(name + "-source-preparation.yaml");
    Files.writeString(
        sourceConfig,
        """
        schemaVersion: source-preparation-config-v1
        source:
          kind: DIRECTORY
          identity: task7-neutral-technical-entry
          root: %s
        exclusions: %s
        limits:
          maxFiles: 64
          maxTotalBytes: 262144
        paths:
          preparationWorkspace: %s
          runStore: %s
        policyRegistry: %s
        """
            .formatted(
                yaml(sourceRoot),
                sourceExclusionsYaml(schemaFixture),
                yaml(preparationWorkspace),
                yaml(runStore),
                yaml(SOURCE_PREPARATION_POLICY_SET)),
        StandardCharsets.UTF_8);
    CliResult prepared = executePublic(sourceConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String r0RunId = runId(prepared);

    Path classpath = physicalRoot.resolve(name + "-classpath.txt");
    Files.writeString(classpath, "", StandardCharsets.UTF_8);
    Path targetJavaHome = physicalRoot.resolve(name + "-target-jdk");
    Files.createDirectories(targetJavaHome.resolve("bin"));
    Files.writeString(targetJavaHome.resolve("bin/java"), "fixture launcher; never executed\n");
    Files.writeString(targetJavaHome.resolve("release"), "JAVA_VERSION=\"17.0.1\"\n");
    Path effectivePom = physicalRoot.resolve(name + "-effective-pom.xml");
    Files.writeString(
        effectivePom,
        effectivePom(name, sourceRoot.resolve("src/main/java")),
        StandardCharsets.UTF_8);
    Path jdtInstallation = Files.createDirectory(physicalRoot.resolve(name + "-jdt-installation"));
    Files.createDirectories(jdtInstallation.resolve("bin"));
    Files.writeString(jdtInstallation.resolve("bin/jdtls"), "fixture; never executed\n");

    Path technicalConfig = physicalRoot.resolve(name + "-technical-v3.yaml");
    String frontendConfiguration =
        frontendRequestEnabled
            ? """
                enabled: true
                nodeExecutable: %s
                sourceRoots: ["web/src"]
                configurationFiles: []
                aliases:
                  "@/": "web/src/"
                """
                .formatted(yaml(frontendNodeIdentity))
                .replaceAll("(?m)^", "  ")
            : "  enabled: false\n";
    Files.writeString(
        technicalConfig,
        """
        schemaVersion: technical-analysis-config-v3
        source:
          preparationRunId: %s
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          artifactPolicyRegistry: %s
        java:
          compilationInput:
            projectDirectory: %s
            modules:
              - modulePath: .
                classpathFile: %s
                classpathSeparator: ":"
                effectivePomFile: %s
                targetJavaHome: %s
          jdtInstallation: %s
          toolJavaHome: %s
        frontend:
        %s
        persistence:
          plugins: [mybatis]
        evidence:
          httpMappings: []
          maxEntryUtf8Bytes: 65536
          maxPublicationUtf8Bytes: 1048576
          maxEntries: %d
        """
            .formatted(
                r0RunId,
                yaml(runStore),
                yaml(archive),
                yaml(SOURCE_PREPARATION_POLICY_SET),
                yaml(TECHNICAL_POLICY_SET),
                yaml(sourceRoot),
                yaml(classpath),
                yaml(effectivePom),
                yaml(targetJavaHome),
                yaml(jdtInstallation),
                yaml(targetJavaHome),
                frontendConfiguration,
                maxEntries),
        StandardCharsets.UTF_8);

    AtomicInteger nodeCalls = new AtomicInteger();
    TechResult frontend =
        executeTechnical(
            technicalConfig,
            "collect-frontend",
            List.of(),
            environment -> {
              throw new AssertionError("disabled frontend must not open a Java session");
            },
            () -> {
              nodeCalls.incrementAndGet();
              if (!frontendRequestEnabled) {
                throw new AssertionError("disabled frontend must not construct a Node tool");
              }
              return (input, frontendConfigurationValue) ->
                  neutralFrontendRequestScan(input, FRONTEND_REQUEST_PATH);
            });
    assertThat(frontend.exitCode()).withFailMessage(frontend.stderr()).isZero();
    TechResult code =
        executeTechnical(
            technicalConfig,
            "collect-code",
            List.of(),
            sessionFactory,
            () -> {
              nodeCalls.incrementAndGet();
              throw new AssertionError("Node must remain disabled for collect-code");
            });
    assertThat(code.exitCode()).withFailMessage(code.stderr()).isZero();
    TechResult persistence =
        executeTechnical(
            technicalConfig,
            "analyze-persistence",
            List.of("--code-run", runId(code.stdout())),
            environment -> {
              throw new AssertionError("persistence must reopen its named saved code result");
            },
            () -> {
              nodeCalls.incrementAndGet();
              throw new AssertionError("persistence must not initialize Node");
            });
    assertThat(persistence.exitCode()).withFailMessage(persistence.stderr()).isZero();
    TechResult materials =
        executeTechnical(
            technicalConfig,
            "assemble-materials",
            List.of(
                "--persistence-run",
                runId(persistence.stdout()),
                "--frontend-run",
                runId(frontend.stdout())),
            environment -> {
              throw new AssertionError("R4 assembly must reopen installed R1–R3 outputs");
            },
            () -> {
              nodeCalls.incrementAndGet();
              throw new AssertionError("R4 assembly must not initialize Node");
            });
    assertThat(materials.exitCode()).withFailMessage(materials.stderr()).isZero();
    assertThat(nodeCalls).hasValue(frontendRequestEnabled ? 1 : 0);
    String r4RunId = runId(materials.stdout());
    AnalysisStepPublicationReference r4Publication;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunOutput output = new LocalRepositoryAnalysisAgent(store).inspect(r4RunId).output();
      TechnicalRunOutput technical = output.technicalOutput();
      assertThat(technical).isNotNull();
      assertThat(technical.readingMaterials()).isNotNull();
      r4Publication = technical.readingMaterials();
    }
    return new TechnicalFixture(runStore, archive, r0RunId, r4RunId, r4Publication);
  }

  private static String sourceExclusionsYaml(SchemaRuntimeFixture schemaFixture) {
    if (schemaFixture == null || schemaFixture.exclusions().isEmpty()) {
      return "[]";
    }
    return "\n"
        + schemaFixture.exclusions().stream()
            .sorted()
            .map(path -> "  - path: \"" + path + "\"\n    kind: FILE")
            .reduce((left, right) -> left + "\n" + right)
            .orElseThrow();
  }

  private static String multiRouteJavaSource(int routeCount) {
    StringBuilder source =
        new StringBuilder(
            "package fixture;\n"
                + "import org.springframework.web.bind.annotation.GetMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                + "@RequestMapping(\"/api\")\n"
                + "final class RecordHandler {\n");
    for (int index = 1; index <= routeCount; index++) {
      String suffix = String.format("%03d", index);
      source
          .append("  @GetMapping(\"/records/")
          .append(suffix)
          .append("\")\n")
          .append("  public String list")
          .append(suffix)
          .append("() { return \"neutral\"; }\n");
    }
    return source.append("}\n").toString();
  }

  private static JavaCodeSession multiRouteSession(
      JavaCompilationEnvironment environment, String source, int routeCount) {
    int typeStart = source.indexOf("final class RecordHandler");
    SourceRange typeRange = sourceRange(source, typeStart, source.lastIndexOf('}') + 1);
    int classAnnotationStart = source.indexOf("@RequestMapping");
    int classAnnotationEnd = source.indexOf('\n', classAnnotationStart);
    SourceRange classAnnotationRange =
        sourceRange(source, classAnnotationStart, classAnnotationEnd);
    int classMappingName = source.indexOf("RequestMapping", classAnnotationStart);
    SourceRange classAnnotationName =
        sourceRange(source, classMappingName, classMappingName + "RequestMapping".length());
    String classAnnotationKey = "annotation:neutral-class-request-mapping";
    List<String> methodKeys = new ArrayList<>();
    List<JavaDeclarationCatalog.MethodDeclarationView> methods = new ArrayList<>();
    List<JavaDeclarationCatalog.AnnotationView> annotations = new ArrayList<>();
    annotations.add(
        new JavaDeclarationCatalog.AnnotationView(
            classAnnotationKey,
            "RequestMapping",
            "org.springframework.web.bind.annotation.RequestMapping",
            "(\"/api\")",
            classAnnotationRange,
            classAnnotationName,
            Map.of("value", Map.of("kind", "STRING", "value", "/api")),
            JAVA_PATH));
    for (int index = 1; index <= routeCount; index++) {
      String suffix = String.format("%03d", index);
      String methodKey = "method:neutral-list-" + suffix;
      String methodName = "list" + suffix;
      String route = "/records/" + suffix;
      String annotationText = "@GetMapping(\"" + route + "\")";
      String methodText = "public String " + methodName + "() { return \"neutral\"; }";
      int annotationStart = source.indexOf(annotationText);
      int methodStart = source.indexOf(methodText);
      SourceRange methodRange = sourceRange(source, methodStart, methodStart + methodText.length());
      SourceRange annotationRange =
          sourceRange(source, annotationStart, annotationStart + annotationText.length());
      int mappingName = source.indexOf("GetMapping", annotationStart);
      SourceRange annotationName =
          sourceRange(source, mappingName, mappingName + "GetMapping".length());
      String annotationKey = "annotation:neutral-get-mapping-" + suffix;
      methodKeys.add(methodKey);
      methods.add(
          new JavaDeclarationCatalog.MethodDeclarationView(
              methodKey,
              "fixture.RecordHandler",
              methodName,
              "METHOD",
              List.of("public"),
              List.of(),
              "String",
              List.of(annotationKey),
              JAVA_PATH,
              methodRange,
              true));
      annotations.add(
          new JavaDeclarationCatalog.AnnotationView(
              annotationKey,
              "GetMapping",
              "org.springframework.web.bind.annotation.GetMapping",
              "(\"" + route + "\")",
              annotationRange,
              annotationName,
              Map.of("value", Map.of("kind", "STRING", "value", route)),
              JAVA_PATH));
    }
    List<String> files =
        environment.modules().stream()
            .flatMap(module -> module.project().sourceEntries().stream())
            .filter(path -> path.endsWith(".java"))
            .distinct()
            .sorted()
            .toList();
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            environment.sourceSnapshotId(),
            files,
            List.of(
                new JavaDeclarationCatalog.TypeDeclaration(
                    JAVA_PATH,
                    typeRange,
                    "fixture.RecordHandler",
                    "CLASS",
                    List.of(classAnnotationKey),
                    List.of(),
                    methodKeys,
                    List.of())),
            methods,
            annotations,
            List.of(),
            Map.of());
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return catalog;
      }

      @Override
      public EntryCodeContext collect(org.sourceanalysis.app.analysis.code.EntrySeed entry) {
        JavaDeclarationCatalog.MethodDeclarationView selected =
            methods.stream()
                .filter(method -> method.methodKey().equals(entry.methodKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("entry method missing from neutral catalog"));
        int start = selected.sourceRange().startOffsetUtf16();
        String methodSourceText =
            source.substring(start, start + selected.sourceRange().lengthUtf16());
        EntryCodeContext.SourceSource methodSource =
            new EntryCodeContext.SourceSource(JAVA_PATH, selected.sourceRange(), methodSourceText);
        EntryCodeContext.MethodCode method =
            new EntryCodeContext.MethodCode(
                selected.methodKey(),
                selected.kind(),
                selected.declaringType(),
                selected.name(),
                List.of(),
                selected.returnTypeText(),
                methodSource,
                selected.hasBody());
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            selected.methodKey(),
            List.of(method),
            List.of(),
            List.of(),
            List.of(),
            new EntryCodeContext.TechnicalEnhancements(
                EntryCodeContext.Availability.NOT_PRODUCED,
                "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_TASK7_FIXTURE",
                List.of(),
                List.of(),
                null));
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt",
            "task7-neutral-multi-entry-fixture",
            Map.of("jdt", "not-started"),
            "17",
            List.of());
      }

      @Override
      public void close() {}
    };
  }

  private static JavaCodeSession sessionWithMapper(JavaCompilationEnvironment environment) {
    String handlerMethodKey = "method:neutral-list";
    String mapperMethodKey = "method:neutral-record-mapper-find-all";
    int handlerMethodStart = DDL_HANDLER_SOURCE.indexOf("public List<String> list()");
    int handlerMethodEnd = DDL_HANDLER_SOURCE.indexOf('}', handlerMethodStart) + 1;
    int callStart = DDL_HANDLER_SOURCE.indexOf("mapper.findAll()", handlerMethodStart);
    int callEnd = callStart + "mapper.findAll()".length();
    int handlerTypeStart = DDL_HANDLER_SOURCE.indexOf("final class RecordHandler");
    int handlerTypeEnd = DDL_HANDLER_SOURCE.lastIndexOf('}') + 1;
    int mapperMethodStart = DDL_MAPPER_SOURCE.indexOf("List<String> findAll();");
    int mapperMethodEnd = mapperMethodStart + "List<String> findAll();".length();
    int mapperTypeStart = DDL_MAPPER_SOURCE.indexOf("public interface RecordMapper");
    int mapperTypeEnd = DDL_MAPPER_SOURCE.lastIndexOf('}') + 1;
    SourceRange handlerMethodRange =
        sourceRange(DDL_HANDLER_SOURCE, handlerMethodStart, handlerMethodEnd);
    SourceRange callRange = sourceRange(DDL_HANDLER_SOURCE, callStart, callEnd);
    SourceRange handlerTypeRange =
        sourceRange(DDL_HANDLER_SOURCE, handlerTypeStart, handlerTypeEnd);
    SourceRange mapperMethodRange =
        sourceRange(DDL_MAPPER_SOURCE, mapperMethodStart, mapperMethodEnd);
    SourceRange mapperTypeRange = sourceRange(DDL_MAPPER_SOURCE, mapperTypeStart, mapperTypeEnd);
    int classAnnotationStart = DDL_HANDLER_SOURCE.indexOf("@RequestMapping");
    int classAnnotationEnd = DDL_HANDLER_SOURCE.indexOf('\n', classAnnotationStart);
    SourceRange classAnnotationRange =
        sourceRange(DDL_HANDLER_SOURCE, classAnnotationStart, classAnnotationEnd);
    int classMappingName = DDL_HANDLER_SOURCE.indexOf("RequestMapping", classAnnotationStart);
    SourceRange classAnnotationName =
        sourceRange(
            DDL_HANDLER_SOURCE, classMappingName, classMappingName + "RequestMapping".length());
    int methodAnnotationStart = DDL_HANDLER_SOURCE.indexOf("@GetMapping");
    int methodAnnotationEnd = DDL_HANDLER_SOURCE.indexOf('\n', methodAnnotationStart);
    SourceRange methodAnnotationRange =
        sourceRange(DDL_HANDLER_SOURCE, methodAnnotationStart, methodAnnotationEnd);
    int methodMappingName = DDL_HANDLER_SOURCE.indexOf("GetMapping", methodAnnotationStart);
    SourceRange methodAnnotationName =
        sourceRange(
            DDL_HANDLER_SOURCE, methodMappingName, methodMappingName + "GetMapping".length());
    String classAnnotationKey = "annotation:ddl-neutral-class-request-mapping";
    String methodAnnotationKey = "annotation:ddl-neutral-get-mapping";
    List<String> files =
        environment.modules().stream()
            .flatMap(module -> module.project().sourceEntries().stream())
            .filter(path -> path.endsWith(".java"))
            .distinct()
            .sorted()
            .toList();
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            environment.sourceSnapshotId(),
            files,
            List.of(
                new JavaDeclarationCatalog.TypeDeclaration(
                    JAVA_PATH,
                    handlerTypeRange,
                    "fixture.RecordHandler",
                    "CLASS",
                    List.of(classAnnotationKey),
                    List.of(),
                    List.of(handlerMethodKey),
                    List.of()),
                new JavaDeclarationCatalog.TypeDeclaration(
                    DDL_JAVA_MAPPER_PATH,
                    mapperTypeRange,
                    "fixture.RecordMapper",
                    "INTERFACE",
                    List.of(),
                    List.of(),
                    List.of(mapperMethodKey),
                    List.of())),
            List.of(
                new JavaDeclarationCatalog.MethodDeclarationView(
                    handlerMethodKey,
                    "fixture.RecordHandler",
                    "list",
                    "METHOD",
                    List.of("public"),
                    List.of(),
                    "List<String>",
                    List.of(methodAnnotationKey),
                    JAVA_PATH,
                    handlerMethodRange,
                    true),
                new JavaDeclarationCatalog.MethodDeclarationView(
                    mapperMethodKey,
                    "fixture.RecordMapper",
                    "findAll",
                    "METHOD",
                    List.of("public", "abstract"),
                    List.of(),
                    "List<String>",
                    List.of(),
                    DDL_JAVA_MAPPER_PATH,
                    mapperMethodRange,
                    false)),
            List.of(
                new JavaDeclarationCatalog.AnnotationView(
                    classAnnotationKey,
                    "RequestMapping",
                    "org.springframework.web.bind.annotation.RequestMapping",
                    "(\"/api\")",
                    classAnnotationRange,
                    classAnnotationName,
                    Map.of("value", Map.of("kind", "STRING", "value", "/api")),
                    JAVA_PATH),
                new JavaDeclarationCatalog.AnnotationView(
                    methodAnnotationKey,
                    "GetMapping",
                    "org.springframework.web.bind.annotation.GetMapping",
                    "(\"/records\")",
                    methodAnnotationRange,
                    methodAnnotationName,
                    Map.of("value", Map.of("kind", "STRING", "value", "/records")),
                    JAVA_PATH)),
            List.of(),
            Map.of());
    EntryCodeContext.SourceSource handlerSource =
        new EntryCodeContext.SourceSource(
            JAVA_PATH,
            handlerMethodRange,
            DDL_HANDLER_SOURCE.substring(handlerMethodStart, handlerMethodEnd));
    EntryCodeContext.SourceSource mapperSource =
        new EntryCodeContext.SourceSource(
            DDL_JAVA_MAPPER_PATH,
            mapperMethodRange,
            DDL_MAPPER_SOURCE.substring(mapperMethodStart, mapperMethodEnd));
    EntryCodeContext.CallSite mapperCall =
        new EntryCodeContext.CallSite(
            "call:neutral-record-mapper-find-all",
            handlerMethodKey,
            "METHOD",
            callRange,
            callRange,
            "mapper.findAll()",
            List.of(
                new EntryCodeContext.CallTarget(
                    mapperMethodKey,
                    List.of("DECLARATION"),
                    "DECLARATION_ONLY",
                    "The neutral fixture exposes the mapper declaration but does not expand its"
                        + " body.")));
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return catalog;
      }

      @Override
      public EntryCodeContext collect(org.sourceanalysis.app.analysis.code.EntrySeed entry) {
        EntryCodeContext.MethodCode handlerMethod =
            new EntryCodeContext.MethodCode(
                handlerMethodKey,
                "METHOD",
                "fixture.RecordHandler",
                "list",
                List.of(),
                "List<String>",
                handlerSource,
                true);
        EntryCodeContext.MethodCode mapperMethod =
            new EntryCodeContext.MethodCode(
                mapperMethodKey,
                "METHOD",
                "fixture.RecordMapper",
                "findAll",
                List.of(),
                "List<String>",
                mapperSource,
                false);
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            handlerMethodKey,
            List.of(handlerMethod, mapperMethod),
            List.of(mapperCall),
            List.of(),
            List.of(),
            new EntryCodeContext.TechnicalEnhancements(
                EntryCodeContext.Availability.NOT_PRODUCED,
                "SCRIPTED_NEUTRAL_MAPPER_FIXTURE",
                List.of(),
                List.of(),
                null));
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "scripted-java-fixture",
            "task8-neutral-mapper",
            Map.of("jdt", "not-started"),
            "17",
            List.of());
      }

      @Override
      public void close() {}
    };
  }

  private static JavaCodeSession session(JavaCompilationEnvironment environment) {
    String methodKey = "method:neutral-list";
    int methodStart = JAVA_SOURCE.indexOf("public String list()");
    int methodEnd = JAVA_SOURCE.indexOf('}', methodStart) + 1;
    int typeStart = JAVA_SOURCE.indexOf("final class RecordHandler");
    int typeEnd = JAVA_SOURCE.lastIndexOf('}') + 1;
    SourceRange methodRange = sourceRange(JAVA_SOURCE, methodStart, methodEnd);
    SourceRange typeRange = sourceRange(JAVA_SOURCE, typeStart, typeEnd);
    int classAnnotationStart = JAVA_SOURCE.indexOf("@RequestMapping");
    int classAnnotationEnd = JAVA_SOURCE.indexOf('\n', classAnnotationStart);
    SourceRange classAnnotationRange =
        sourceRange(JAVA_SOURCE, classAnnotationStart, classAnnotationEnd);
    int classMappingName = JAVA_SOURCE.indexOf("RequestMapping", classAnnotationStart);
    SourceRange classAnnotationName =
        sourceRange(JAVA_SOURCE, classMappingName, classMappingName + "RequestMapping".length());
    int annotationStart = JAVA_SOURCE.indexOf("@GetMapping");
    int annotationEnd = JAVA_SOURCE.indexOf('\n', annotationStart);
    SourceRange annotationRange = sourceRange(JAVA_SOURCE, annotationStart, annotationEnd);
    int mappingName = JAVA_SOURCE.indexOf("GetMapping", annotationStart);
    SourceRange annotationName =
        sourceRange(JAVA_SOURCE, mappingName, mappingName + "GetMapping".length());
    String classAnnotationKey = "annotation:neutral-class-request-mapping";
    String annotationKey = "annotation:neutral-get-mapping";
    List<String> files =
        environment.modules().stream()
            .flatMap(module -> module.project().sourceEntries().stream())
            .filter(path -> path.endsWith(".java"))
            .distinct()
            .sorted()
            .toList();
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            environment.sourceSnapshotId(),
            files,
            List.of(
                new JavaDeclarationCatalog.TypeDeclaration(
                    JAVA_PATH,
                    typeRange,
                    "fixture.RecordHandler",
                    "CLASS",
                    List.of(classAnnotationKey),
                    List.of(),
                    List.of(methodKey),
                    List.of())),
            List.of(
                new JavaDeclarationCatalog.MethodDeclarationView(
                    methodKey,
                    "fixture.RecordHandler",
                    "list",
                    "METHOD",
                    List.of("public"),
                    List.of(),
                    "String",
                    List.of(annotationKey),
                    JAVA_PATH,
                    methodRange,
                    true)),
            List.of(
                new JavaDeclarationCatalog.AnnotationView(
                    classAnnotationKey,
                    "RequestMapping",
                    "org.springframework.web.bind.annotation.RequestMapping",
                    "(\"/api\")",
                    classAnnotationRange,
                    classAnnotationName,
                    Map.of("value", Map.of("kind", "STRING", "value", "/api")),
                    JAVA_PATH),
                new JavaDeclarationCatalog.AnnotationView(
                    annotationKey,
                    "GetMapping",
                    "org.springframework.web.bind.annotation.GetMapping",
                    "(\"/records\")",
                    annotationRange,
                    annotationName,
                    Map.of("value", Map.of("kind", "STRING", "value", "/records")),
                    JAVA_PATH)),
            List.of(),
            Map.of());
    EntryCodeContext.SourceSource methodSource =
        new EntryCodeContext.SourceSource(
            JAVA_PATH, methodRange, JAVA_SOURCE.substring(methodStart, methodEnd));
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return catalog;
      }

      @Override
      public EntryCodeContext collect(org.sourceanalysis.app.analysis.code.EntrySeed entry) {
        EntryCodeContext.MethodCode method =
            new EntryCodeContext.MethodCode(
                methodKey,
                "METHOD",
                "fixture.RecordHandler",
                "list",
                List.of(),
                "String",
                methodSource,
                true);
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            methodKey,
            List.of(method),
            List.of(),
            List.of(),
            List.of(),
            new EntryCodeContext.TechnicalEnhancements(
                EntryCodeContext.Availability.NOT_PRODUCED,
                "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_TASK7_FIXTURE",
                List.of(),
                List.of(),
                null));
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "task7-neutral-fixture", Map.of("jdt", "not-started"), "17", List.of());
      }

      @Override
      public void close() {}
    };
  }

  private static JavaCodeSession supplementalSourceSession(JavaCompilationEnvironment environment) {
    String routeMethodKey = "method:neutral-list";
    String helperMethodKey = "method:neutral-helper";
    String helperDeclaration = "private String helper() { return \"restricted\"; }";
    int routeMethodStart = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf("public String list()");
    int routeMethodEnd = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf('}', routeMethodStart) + 1;
    int helperStart = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf(helperDeclaration);
    int helperEnd = helperStart + helperDeclaration.length();
    int callStart = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf("helper()", routeMethodStart);
    int typeStart = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf("final class RecordHandler");
    int typeEnd = SOURCE_SUPPLEMENT_JAVA_SOURCE.lastIndexOf('}') + 1;
    SourceRange routeMethodRange =
        sourceRange(SOURCE_SUPPLEMENT_JAVA_SOURCE, routeMethodStart, routeMethodEnd);
    SourceRange helperRange = sourceRange(SOURCE_SUPPLEMENT_JAVA_SOURCE, helperStart, helperEnd);
    int helperNameStart = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf("helper", helperStart);
    SourceRange helperNameRange =
        sourceRange(
            SOURCE_SUPPLEMENT_JAVA_SOURCE, helperNameStart, helperNameStart + "helper".length());
    SourceRange callRange =
        sourceRange(SOURCE_SUPPLEMENT_JAVA_SOURCE, callStart, callStart + "helper()".length());
    SourceRange typeRange = sourceRange(SOURCE_SUPPLEMENT_JAVA_SOURCE, typeStart, typeEnd);
    int classAnnotationStart = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf("@RequestMapping");
    int classAnnotationEnd = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf('\n', classAnnotationStart);
    SourceRange classAnnotationRange =
        sourceRange(SOURCE_SUPPLEMENT_JAVA_SOURCE, classAnnotationStart, classAnnotationEnd);
    int classMappingName =
        SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf("RequestMapping", classAnnotationStart);
    SourceRange classAnnotationName =
        sourceRange(
            SOURCE_SUPPLEMENT_JAVA_SOURCE,
            classMappingName,
            classMappingName + "RequestMapping".length());
    int routeAnnotationStart = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf("@GetMapping");
    int routeAnnotationEnd = SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf('\n', routeAnnotationStart);
    SourceRange routeAnnotationRange =
        sourceRange(SOURCE_SUPPLEMENT_JAVA_SOURCE, routeAnnotationStart, routeAnnotationEnd);
    int routeMappingName =
        SOURCE_SUPPLEMENT_JAVA_SOURCE.indexOf("GetMapping", routeAnnotationStart);
    SourceRange routeAnnotationName =
        sourceRange(
            SOURCE_SUPPLEMENT_JAVA_SOURCE,
            routeMappingName,
            routeMappingName + "GetMapping".length());
    String classAnnotationKey = "annotation:source-supplement-class";
    String routeAnnotationKey = "annotation:source-supplement-route";
    List<String> files =
        environment.modules().stream()
            .flatMap(module -> module.project().sourceEntries().stream())
            .filter(path -> path.endsWith(".java"))
            .distinct()
            .sorted()
            .toList();
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            environment.sourceSnapshotId(),
            files,
            List.of(
                new JavaDeclarationCatalog.TypeDeclaration(
                    JAVA_PATH,
                    typeRange,
                    "fixture.RecordHandler",
                    "CLASS",
                    List.of(classAnnotationKey),
                    List.of(),
                    List.of(routeMethodKey, helperMethodKey),
                    List.of())),
            List.of(
                new JavaDeclarationCatalog.MethodDeclarationView(
                    routeMethodKey,
                    "fixture.RecordHandler",
                    "list",
                    "METHOD",
                    List.of("public"),
                    List.of(),
                    "String",
                    List.of(routeAnnotationKey),
                    JAVA_PATH,
                    routeMethodRange,
                    true),
                new JavaDeclarationCatalog.MethodDeclarationView(
                    helperMethodKey,
                    "fixture.RecordHandler",
                    "helper",
                    "METHOD",
                    List.of("private"),
                    List.of(),
                    "String",
                    List.of(),
                    JAVA_PATH,
                    helperRange,
                    true)),
            List.of(
                new JavaDeclarationCatalog.AnnotationView(
                    classAnnotationKey,
                    "RequestMapping",
                    "org.springframework.web.bind.annotation.RequestMapping",
                    "(\"/api\")",
                    classAnnotationRange,
                    classAnnotationName,
                    Map.of("value", Map.of("kind", "STRING", "value", "/api")),
                    JAVA_PATH),
                new JavaDeclarationCatalog.AnnotationView(
                    routeAnnotationKey,
                    "GetMapping",
                    "org.springframework.web.bind.annotation.GetMapping",
                    "(\"/records\")",
                    routeAnnotationRange,
                    routeAnnotationName,
                    Map.of("value", Map.of("kind", "STRING", "value", "/records")),
                    JAVA_PATH)),
            List.of(),
            Map.of());
    EntryCodeContext.SourceSource routeSource =
        new EntryCodeContext.SourceSource(
            JAVA_PATH,
            routeMethodRange,
            SOURCE_SUPPLEMENT_JAVA_SOURCE.substring(routeMethodStart, routeMethodEnd));
    EntryCodeContext.CallTarget unconfirmedHelper =
        new EntryCodeContext.CallTarget(
            null,
            List.of("DECLARATION"),
            "fixture.RecordHandler.helper() : String",
            List.of("DEFINITION"),
            "NOT_EXPANDED",
            "NAVIGATION_CONFLICT_NOT_EXPANDED",
            List.of());
    EntryCodeContext.CallObservation unconfirmedObservation =
        new EntryCodeContext.CallObservation(
            "UNCONFIRMED_NAVIGATION_LOCATION",
            "JDT_LANGUAGE_SERVER",
            "REPOSITORY_SOURCE",
            helperNameRange,
            "UNCONFIRMED",
            null,
            null,
            "SOURCE",
            "fixture.RecordHandler.helper() : String",
            "NAVIGATION_CONFLICT_NOT_EXPANDED");
    EntryCodeContext.CallSite call =
        new EntryCodeContext.CallSite(
            "call:neutral-helper",
            routeMethodKey,
            "METHOD",
            callRange,
            helperNameRange,
            "helper()",
            null,
            List.of(),
            List.of(),
            false,
            List.of(unconfirmedHelper),
            "NAVIGATION_CONFLICT",
            "CALL_SITE_ASSOCIATION_UNCONFIRMED",
            List.of(unconfirmedObservation));
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return catalog;
      }

      @Override
      public EntryCodeContext collect(org.sourceanalysis.app.analysis.code.EntrySeed entry) {
        EntryCodeContext.MethodCode method =
            new EntryCodeContext.MethodCode(
                routeMethodKey,
                "METHOD",
                "fixture.RecordHandler",
                "list",
                List.of(),
                "String",
                routeSource,
                true);
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            routeMethodKey,
            List.of(method),
            List.of(call),
            List.of(),
            List.of(),
            new EntryCodeContext.TechnicalEnhancements(
                EntryCodeContext.Availability.NOT_PRODUCED,
                "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_SOURCE_SUPPLEMENT_FIXTURE",
                List.of(),
                List.of(),
                null));
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt",
            "task7-source-supplement-fixture",
            Map.of("jdt", "not-started"),
            "17",
            List.of());
      }

      @Override
      public void close() {}
    };
  }

  private Path writeOntologyConfig(
      String name, TechnicalFixture fixture, Path archive, Path promptOverride, boolean withModels)
      throws Exception {
    return writeOntologyConfig(name, fixture, archive, promptOverride, withModels, 524288);
  }

  private Path writeOntologyConfig(
      String name,
      TechnicalFixture fixture,
      Path archive,
      Path promptOverride,
      boolean withModels,
      int maxRequestBytes)
      throws Exception {
    Path path = temporaryDirectory.resolve(name);
    String promptSection =
        promptOverride == null ? "" : "prompts:\n  object: " + yaml(promptOverride) + "\n";
    String modelSection = withModels ? modelJobsYaml() : "";
    Files.writeString(
        path,
        """
        schemaVersion: ontology-config-v1
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          evidencePolicyRegistry: %s
          ontologyPolicyRegistry: %s
        reading:
          maxUnitBytes: 1048576
          maxRequestBytes: %d
          maxOutputBytes: 100000
          maxOutputTokens: 2048
          maxRequests: 8
          maxReadingRounds: 4
          maxActionsPerRound: 4
          maxNavigationEntries: 8
        schemaSources: []
        %s%s
        """
            .formatted(
                yaml(fixture.runStore()),
                yaml(archive),
                yaml(SOURCE_PREPARATION_POLICY_SET),
                yaml(TECHNICAL_POLICY_SET),
                yaml(ONTOLOGY_POLICY_SET),
                maxRequestBytes,
                promptSection,
                modelSection),
        StandardCharsets.UTF_8);
    return path;
  }

  private Path writeOntologyConfigWithSchemaSources(
      String name,
      TechnicalFixture fixture,
      List<String> schemaSources,
      boolean withModels,
      int maxUnitBytes)
      throws Exception {
    Path configuration =
        writeOntologyConfig(name, fixture, fixture.archive(), null, withModels, 524_288);
    String schemaSourceSection =
        schemaSources.isEmpty()
            ? "schemaSources: []"
            : "schemaSources:\n"
                + schemaSources.stream()
                    .map(source -> "  - \"" + source + "\"")
                    .reduce((left, right) -> left + "\n" + right)
                    .orElseThrow();
    String configuredText =
        Files.readString(configuration, StandardCharsets.UTF_8)
            .replace("maxUnitBytes: 1048576", "maxUnitBytes: " + maxUnitBytes)
            .replace("schemaSources: []", schemaSourceSection);
    Files.writeString(configuration, configuredText, StandardCharsets.UTF_8);
    return configuration;
  }

  private ConfiguredDiscovery writeFormalDiscoveryConfiguration(
      String name, TechnicalFixture fixture, Path archive, int maxRequestBytes) throws Exception {
    Path configuration = writeOntologyConfig(name, fixture, archive, null, true, maxRequestBytes);
    Map<String, String> promptText =
        Map.of(
            "survey",
            "Neutral survey instruction: cite only shown source refs and state uncertainty.",
            "prioritize",
            "Neutral prioritization instruction: select only an actual supplied question and"
                + " preserve its refs.",
            "reading",
            "Neutral reading instruction: use only visible short refs and read source before"
                + " extracting.",
            "object",
            "Neutral OBJECT instruction: describe only the source-backed shape and retain unknown"
                + " identity.",
            "review",
            "Neutral REVIEW instruction: check the candidate against this same supplied source.");
    StringBuilder promptSection = new StringBuilder("prompts:\n");
    for (String key : List.of("survey", "prioritize", "reading", "object", "review")) {
      Path prompt = temporaryDirectory.resolve(name + "-" + key + ".txt");
      Files.writeString(prompt, promptText.get(key), StandardCharsets.UTF_8);
      promptSection.append("  ").append(key).append(": ").append(yaml(prompt)).append('\n');
    }
    String source = Files.readString(configuration, StandardCharsets.UTF_8);
    Files.writeString(
        configuration,
        source.replace(
            "schemaVersion: ontology-config-v1\n",
            "schemaVersion: ontology-config-v1\n" + promptSection),
        StandardCharsets.UTF_8);
    return new ConfiguredDiscovery(configuration, promptText);
  }

  private ConfiguredDiscovery writeFormalDiscoveryConfigurationV2(
      String name, TechnicalFixture fixture, Path archive, int maxRequestBytes) throws Exception {
    ConfiguredDiscovery configured =
        writeFormalDiscoveryConfiguration(name, fixture, archive, maxRequestBytes);
    String source = Files.readString(configured.path(), StandardCharsets.UTF_8);
    assertThat(source).contains(yaml(ONTOLOGY_POLICY_SET));
    String v2Configuration =
        source.replace(yaml(ONTOLOGY_POLICY_SET), yaml(ONTOLOGY_POLICY_SET_V2));
    assertThat(v2Configuration).isNotEqualTo(source);
    v2Configuration =
        v2Configuration.replace(
            "  ontologyPolicyRegistry: " + yaml(ONTOLOGY_POLICY_SET_V2) + "\nreading:",
            "  ontologyPolicyRegistry: "
                + yaml(ONTOLOGY_POLICY_SET_V2)
                + "\n  upstreamArtifactPolicyRegistries:\n    - "
                + yaml(ONTOLOGY_POLICY_SET)
                + "\nreading:");
    assertThat(v2Configuration).contains("upstreamArtifactPolicyRegistries");
    Files.writeString(configured.path(), v2Configuration, StandardCharsets.UTF_8);
    return configured;
  }

  private Path writeModelDiscoveryScope(Path path) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "DISCOVERY");
    root.put("selectionMode", "MODEL");
    root.putArray("questions");
    return writeJson(path, root);
  }

  private Path writeBusinessLinkSkeletonDiscoveryScopeV2(Path path) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v2");
    root.put("purpose", "SKELETON");
    root.put("mode", "DISCOVERY");
    root.put("selectionMode", "MODEL");
    root.putArray("questions");
    return writeJson(path, root);
  }

  private ConfiguredTypedPipeline writeTypedPipelineConfiguration(
      String name, TechnicalFixture fixture) throws Exception {
    Path configuration = writeOntologyConfig(name, fixture, fixture.archive(), null, true);
    Map<String, String> promptText =
        Map.of(
            "object", "Neutral OBJECT prompt: describe only the selected source shape.",
            "action", "Neutral ACTION prompt: use only the actual reviewed object catalog.",
            "analytic", "Neutral ANALYTIC prompt: retain unknown type and unit information.",
            "relate",
                "Neutral RELATE prompt: state only what the reviewed object catalog supports.",
            "review", "Neutral review prompt: check this draft against the same frozen source.");
    StringBuilder promptSection = new StringBuilder("prompts:\n");
    for (String key : List.of("object", "action", "analytic", "relate", "review")) {
      Path prompt = temporaryDirectory.resolve(name + "-" + key + ".txt");
      Files.writeString(prompt, promptText.get(key), StandardCharsets.UTF_8);
      promptSection.append("  ").append(key).append(": ").append(yaml(prompt)).append('\n');
    }
    String source = Files.readString(configuration, StandardCharsets.UTF_8);
    Files.writeString(
        configuration,
        source
            .replace(modelJobsYaml(), typedModelJobsYaml())
            .replace(
                "schemaVersion: ontology-config-v1\n",
                "schemaVersion: ontology-config-v1\n" + promptSection),
        StandardCharsets.UTF_8);
    return new ConfiguredTypedPipeline(configuration, promptText);
  }

  private ConfiguredTypedPipeline writeTypedPipelineConfigurationV2(
      String name, TechnicalFixture fixture) throws Exception {
    ConfiguredTypedPipeline configured = writeTypedPipelineConfiguration(name, fixture);
    String source = Files.readString(configured.path(), StandardCharsets.UTF_8);
    assertThat(source).contains(yaml(ONTOLOGY_POLICY_SET));
    String v2Configuration =
        source.replace(yaml(ONTOLOGY_POLICY_SET), yaml(ONTOLOGY_POLICY_SET_V2));
    assertThat(v2Configuration).isNotEqualTo(source);
    v2Configuration =
        v2Configuration.replace(
            "  ontologyPolicyRegistry: " + yaml(ONTOLOGY_POLICY_SET_V2) + "\nreading:",
            "  ontologyPolicyRegistry: "
                + yaml(ONTOLOGY_POLICY_SET_V2)
                + "\n  upstreamArtifactPolicyRegistries:\n    - "
                + yaml(ONTOLOGY_POLICY_SET)
                + "\nreading:");
    assertThat(v2Configuration).contains("upstreamArtifactPolicyRegistries");
    Files.writeString(configured.path(), v2Configuration, StandardCharsets.UTF_8);
    return configured;
  }

  private ConfiguredTypedPipeline writeBusinessLinkTypedPipelineConfigurationV3(
      String name, TechnicalFixture fixture) throws Exception {
    ConfiguredTypedPipeline configured = writeTypedPipelineConfiguration(name, fixture);
    String source = Files.readString(configured.path(), StandardCharsets.UTF_8);
    assertThat(source).contains(yaml(ONTOLOGY_POLICY_SET));
    String v3Configuration =
        source.replace(yaml(ONTOLOGY_POLICY_SET), yaml(ONTOLOGY_POLICY_SET_V3));
    assertThat(v3Configuration).isNotEqualTo(source).contains(yaml(ONTOLOGY_POLICY_SET_V3));
    Files.writeString(configured.path(), v3Configuration, StandardCharsets.UTF_8);
    return configured;
  }

  private ConfiguredTypedPipeline writeBusinessLinkTypedPipelineConfigurationV4(
      String name, TechnicalFixture fixture) throws Exception {
    ConfiguredTypedPipeline configured = writeTypedPipelineConfiguration(name, fixture);
    String source = Files.readString(configured.path(), StandardCharsets.UTF_8);
    assertThat(source).contains(yaml(ONTOLOGY_POLICY_SET));
    String v4Configuration =
        source.replace(yaml(ONTOLOGY_POLICY_SET), yaml(ONTOLOGY_POLICY_SET_V4));
    assertThat(v4Configuration).isNotEqualTo(source).contains(yaml(ONTOLOGY_POLICY_SET_V4));
    Path linkPrompt = temporaryDirectory.resolve(name + "-link.txt");
    String linkPromptText =
        "Neutral LINK prompt: cite only the supplied technical source and preserve unknowns.";
    Files.writeString(linkPrompt, linkPromptText, StandardCharsets.UTF_8);
    v4Configuration =
        v4Configuration.replace("prompts:\n", "prompts:\n  link: " + yaml(linkPrompt) + "\n");
    assertThat(v4Configuration).contains("  link: " + yaml(linkPrompt));
    Files.writeString(configured.path(), v4Configuration, StandardCharsets.UTF_8);
    Map<String, String> prompts = new LinkedHashMap<>(configured.prompts());
    prompts.put("link", linkPromptText);
    return new ConfiguredTypedPipeline(configured.path(), Map.copyOf(prompts));
  }

  private ConfiguredDiscovery writeBusinessLinkDiscoveryConfigurationV3(
      String name, TechnicalFixture fixture) throws Exception {
    ConfiguredDiscovery configured =
        writeFormalDiscoveryConfiguration(name, fixture, fixture.archive(), 524288);
    String source = Files.readString(configured.path(), StandardCharsets.UTF_8);
    assertThat(source).contains(yaml(ONTOLOGY_POLICY_SET));
    String v3Configuration =
        source.replace(yaml(ONTOLOGY_POLICY_SET), yaml(ONTOLOGY_POLICY_SET_V3));
    assertThat(v3Configuration).isNotEqualTo(source).contains(yaml(ONTOLOGY_POLICY_SET_V3));
    Files.writeString(configured.path(), v3Configuration, StandardCharsets.UTF_8);
    return configured;
  }

  private CrossRunSameTaskO3Inputs prepareCrossRunSameTaskO3Inputs(boolean conflictingRelations)
      throws Exception {
    return prepareCrossRunSameTaskO3Inputs(conflictingRelations, false);
  }

  private CrossRunSameTaskO3Inputs prepareCrossRunSameTaskO3Inputs(
      boolean conflictingRelations, boolean partialIdentifications) throws Exception {
    return prepareCrossRunSameTaskO3Inputs(conflictingRelations, partialIdentifications, false);
  }

  private CrossRunSameTaskO3Inputs prepareCrossRunSameTaskO3Inputs(
      boolean conflictingRelations, boolean partialIdentifications, boolean identicalOwnerOutputs)
      throws Exception {
    return prepareCrossRunSameTaskO3Inputs(
        conflictingRelations, partialIdentifications, identicalOwnerOutputs, false);
  }

  private CrossRunSameTaskO3Inputs prepareCrossRunSameTaskO3Inputs(
      boolean conflictingRelations,
      boolean partialIdentifications,
      boolean identicalOwnerOutputs,
      boolean unprocessedDependencyOwners)
      throws Exception {
    String fixtureName;
    if (conflictingRelations) {
      fixtureName = "o3-same-task-assembly-conflict";
    } else if (identicalOwnerOutputs) {
      fixtureName = "o3-same-task-identical-result-owners";
    } else if (unprocessedDependencyOwners) {
      fixtureName = "o3-same-local-unprocessed-dependency-owners";
    } else if (partialIdentifications) {
      fixtureName = "o3-same-task-partial-problem-owners";
    } else {
      fixtureName = "o3-same-task-ownership";
    }
    boolean partialOwners = partialIdentifications || unprocessedDependencyOwners;
    TechnicalFixture fixture = prepareRealR4(fixtureName);
    ConfiguredTypedPipeline configured =
        writeTypedPipelineConfigurationV2("ontology-" + fixtureName + "-v2.yaml", fixture);
    List<StructuredModelRequest> requests = new ArrayList<>();
    AtomicInteger nextObjectOrdinal = new AtomicInteger();
    AtomicInteger activeObjectOrdinal = new AtomicInteger();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String questionId = input.path("questionId").asText();
                String kind = input.path("taskKind").asText();
                boolean review = request.taskKind().contains("REVIEW");
                String schemaVersion =
                    review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3";
                ImmutableBytes response;
                if ("OBJECT".equals(kind)) {
                  if (!review) {
                    activeObjectOrdinal.set(nextObjectOrdinal.incrementAndGet());
                  }
                  int objectOrdinal = activeObjectOrdinal.get();
                  if (partialOwners && "Qbad".equals(questionId) && review) {
                    response =
                        formalTaskObjectResponseWithDanglingReference(
                            schemaVersion, questionId, "E1", "O999");
                  } else if (identicalOwnerOutputs) {
                    response = formalTaskObjectResponse(schemaVersion, questionId, "E1");
                  } else {
                    response =
                        formalTaskObjectResponse(
                            schemaVersion,
                            questionId,
                            "E1",
                            "Neutral " + questionId + " object " + objectOrdinal,
                            "A distinct source-backed "
                                + questionId
                                + " object "
                                + objectOrdinal
                                + ".");
                  }
                } else if ("RELATE".equals(kind)) {
                  List<String> objectRefs =
                      textFieldValues(input.path("reviewedCatalog"), "catalogRef").stream()
                          .filter(reference -> reference.matches("B[1-9][0-9]*"))
                          .toList();
                  if (identicalOwnerOutputs) {
                    assertThat(objectRefs).containsExactly("B1");
                  } else {
                    assertThat(objectRefs).containsExactlyInAnyOrder("B1", "B2");
                  }
                  if (conflictingRelations) {
                    response =
                        formalTaskSameObjectRelateResponse(
                            schemaVersion, objectRefs, "R1".equals(questionId) ? "B1" : "B2");
                  } else {
                    response = formalTaskUnresolvedRelateResponse(schemaVersion, objectRefs);
                  }
                } else {
                  throw new AssertionError("unexpected cross-run O3 task kind " + kind);
                }
                return new StructuredModelResponse(response, identity);
              };
            };

    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(prepared.stderr()).isZero();
    String o0RunId = runId(prepared);

    assertThat(requests).isEmpty();
    Path firstScope;
    if (partialIdentifications) {
      firstScope =
          writePartialDuplicateProblemScope(
              temporaryDirectory.resolve(fixtureName + "-first-o1-scope.json"));
    } else if (unprocessedDependencyOwners) {
      firstScope =
          writePartialDependencyDuplicateProblemScope(
              temporaryDirectory.resolve(fixtureName + "-first-o1-scope.json"), "bad-object-first");
    } else {
      firstScope =
          writeExplicitObjectScope(
              temporaryDirectory.resolve(fixtureName + "-first-o1-scope.json"),
              "Q1",
              "shared-task");
    }
    CliResult firstIdentified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            firstScope.toString());
    assertThat(firstIdentified.exitCode()).withFailMessage(firstIdentified.stderr()).isIn(0, 2);
    String firstO1RunId = runId(firstIdentified);
    assertThat(requests).hasSize(partialOwners ? 6 : 2);

    Path secondScope;
    if (partialIdentifications) {
      secondScope =
          writePartialDuplicateProblemScope(
              temporaryDirectory.resolve(fixtureName + "-second-o1-scope.json"));
    } else if (unprocessedDependencyOwners) {
      secondScope =
          writePartialDependencyDuplicateProblemScope(
              temporaryDirectory.resolve(fixtureName + "-second-o1-scope.json"),
              "bad-object-second");
    } else {
      secondScope =
          writeExplicitObjectScope(
              temporaryDirectory.resolve(fixtureName + "-second-o1-scope.json"),
              identicalOwnerOutputs ? "Q1" : "Q2",
              "shared-task");
    }
    CliResult secondIdentified =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "identify-ontology",
            "--corpus-run",
            o0RunId,
            "--scope",
            secondScope.toString());
    assertThat(secondIdentified.exitCode()).withFailMessage(secondIdentified.stderr()).isIn(0, 2);
    String secondO1RunId = runId(secondIdentified);
    assertThat(requests).hasSize(partialOwners ? 12 : 4);

    List<ObjectSource> objectSources;
    if (partialOwners || identicalOwnerOutputs) {
      objectSources = List.of(new ObjectSource(firstO1RunId, "Q1"));
    } else {
      objectSources =
          List.of(new ObjectSource(firstO1RunId, "Q1"), new ObjectSource(secondO1RunId, "Q2"));
    }
    Path firstRelationSelection =
        writeRelateSelectionV2(
            temporaryDirectory.resolve(fixtureName + "-first-o2-selection.json"),
            o0RunId,
            List.of(firstO1RunId, secondO1RunId),
            new SelectedRelateQuestion("R1", "shared-task", objectSources));
    CliResult firstRelated =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            firstRelationSelection.toString());
    assertThat(firstRelated.exitCode()).withFailMessage(firstRelated.stderr()).isIn(0, 2);
    String firstO2RunId = runId(firstRelated);
    CliResult firstIdentificationArtifact =
        artifact(configured.path(), firstO1RunId, "ONTOLOGY_IDENTIFICATION");
    CliResult secondIdentificationArtifact =
        artifact(configured.path(), secondO1RunId, "ONTOLOGY_IDENTIFICATION");
    CliResult firstRelationArtifact =
        artifact(configured.path(), firstO2RunId, "ONTOLOGY_RELATIONS");
    List<String> dispatchedTasks =
        requests.stream()
            .map(
                request -> {
                  JsonNode input = ExplicitTypedPipelineScript.input(request);
                  return input.path("questionId").asText()
                      + "/"
                      + input.path("taskId").asText()
                      + "/"
                      + input.path("taskKind").asText()
                      + "/"
                      + (request.taskKind().contains("REVIEW") ? "REVIEW" : "EXTRACT");
                })
            .toList();
    assertThat(requests)
        .withFailMessage(
            "first O2 model-dispatch count mismatch; first O1=%s; second O1=%s; "
                + "first O2 stdout=%s stderr=%s; saved relation artifact exit=%s stdout=%s "
                + "stderr=%s; dispatched phases=%s",
            firstIdentificationArtifact.stdout(),
            secondIdentificationArtifact.stdout(),
            firstRelated.stdout(),
            firstRelated.stderr(),
            firstRelationArtifact.exitCode(),
            firstRelationArtifact.stdout(),
            firstRelationArtifact.stderr(),
            dispatchedTasks)
        .hasSize(partialOwners ? 14 : 6);

    Path secondRelationSelection =
        writeRelateSelectionV2(
            temporaryDirectory.resolve(fixtureName + "-second-o2-selection.json"),
            o0RunId,
            List.of(firstO1RunId, secondO1RunId),
            new SelectedRelateQuestion(
                "R2",
                "shared-task",
                partialOwners || identicalOwnerOutputs
                    ? List.of(new ObjectSource(secondO1RunId, "Q1"))
                    : objectSources));
    CliResult secondRelated =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "relate-ontology",
            "--selection",
            secondRelationSelection.toString());
    assertThat(secondRelated.exitCode()).withFailMessage(secondRelated.stderr()).isIn(0, 2);
    String secondO2RunId = runId(secondRelated);
    assertThat(requests).hasSize(partialOwners ? 16 : 8);
    return new CrossRunSameTaskO3Inputs(
        fixture,
        configured,
        providerFactory,
        requests,
        o0RunId,
        firstO1RunId,
        secondO1RunId,
        firstO2RunId,
        secondO2RunId);
  }

  private Path writeMixedReadingAndExplicitObjectScope(Path path, String entryRef, String unitRef)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ArrayNode questions = root.putArray("questions");
    addSingleUnitQuestion(
        questions.addObject(), "Q1", "bad-reading-task", entryRef, unitRef, "MODEL");
    addSingleUnitQuestion(
        questions.addObject(), "Q2", "independent-object-task", entryRef, unitRef, "EXPLICIT");
    return writeJson(path, root);
  }

  private static void addSingleUnitQuestion(
      ObjectNode question,
      String questionId,
      String taskId,
      String entryRef,
      String unitRef,
      String readingMode) {
    question.put("questionId", questionId);
    question.put("question", "Which source-backed neutral shape is visible in " + questionId + "?");
    question.putArray("entryRefs").add(entryRef);
    question.putArray("clueRefs");
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", taskId);
    task.put("taskKind", "OBJECT");
    task.put("readingMode", readingMode);
    ArrayNode selectedUses = task.putArray("unitUses");
    if ("EXPLICIT".equals(readingMode)) {
      addUse(selectedUses, unitRef, entryRef);
    }
    addUse(task.putArray("requiredUnitUses"), unitRef, entryRef);
  }

  private Path writeExplicitScope(Path path) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q1");
    question.put("question", "Which source-backed record shape is visible at this entry?");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", "object-task");
    task.put("taskKind", "OBJECT");
    task.put("readingMode", "EXPLICIT");
    unitUses(task.putArray("unitUses"));
    unitUses(task.putArray("requiredUnitUses"));
    return writeJson(path, root);
  }

  private Path writeSingleUnitObjectScope(
      Path path, String questionId, String taskId, String entryRef, String unitRef)
      throws Exception {
    return writeSingleUnitObjectScope(path, questionId, taskId, entryRef, unitRef, "EXPLICIT");
  }

  private Path writeSingleUnitObjectScope(
      Path path,
      String questionId,
      String taskId,
      String entryRef,
      String unitRef,
      String readingMode)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", questionId);
    question.put("question", "Which neutral source-backed record shape is visible in this entry?");
    question.putArray("entryRefs").add(entryRef);
    question.putArray("clueRefs");
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", taskId);
    task.put("taskKind", "OBJECT");
    task.put("readingMode", readingMode);
    ArrayNode selectedUses = task.putArray("unitUses");
    if (!"MODEL".equals(readingMode)) {
      addUse(selectedUses, unitRef, entryRef);
    }
    addUse(task.putArray("requiredUnitUses"), unitRef, entryRef);
    return writeJson(path, root);
  }

  private static void addUse(ArrayNode uses, String unitRef, String entryRef) {
    ObjectNode use = uses.addObject();
    use.put("unitRef", unitRef);
    use.put("entryRef", entryRef);
  }

  private Path writeExplicitObjectScope(Path path, String questionId, String taskId)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", questionId);
    question.put("question", "Which neutral record shape is visible in this selected entry?");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", taskId);
    task.put("taskKind", "OBJECT");
    task.put("readingMode", "EXPLICIT");
    unitUses(task.putArray("unitUses"));
    unitUses(task.putArray("requiredUnitUses"));
    return writeJson(path, root);
  }

  private Path writeBusinessLinkSkeletonScopeV2(Path path) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v2");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    root.put("purpose", "SKELETON");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q_SKELETON");
    question.put("question", "Describe only the selected neutral source-backed object shape.");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    question.putArray("objectSources");
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", "object-skeleton");
    task.put("taskKind", "OBJECT");
    task.put("readingMode", "EXPLICIT");
    unitUses(task.putArray("unitUses"));
    unitUses(task.putArray("requiredUnitUses"));
    return writeJson(path, root);
  }

  private Path writeBusinessLinkTwoLinkScopeV3(Path path) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v3");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    root.put("purpose", "SKELETON");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q_LINK_SOURCE");
    question.put(
        "question", "Describe only source-backed record links and their endpoint objects.");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs").add("K1");
    question.putArray("objectSources");
    ArrayNode tasks = question.putArray("tasks");
    for (String taskId : List.of("link-task-a", "link-task-b")) {
      ObjectNode task = tasks.addObject();
      task.put("taskId", taskId);
      task.put("taskKind", "LINK");
      task.put("readingMode", "TECHNICAL_BUNDLE");
      task.putArray("anchorRefs").add("K1");
      addUse(task.putArray("unitUses"), "U1", "E1");
      addUse(task.putArray("requiredUnitUses"), "U1", "E1");
    }
    return writeJson(path, root);
  }

  private Path writeBusinessLinkEnrichmentScopeV3ForTask(
      Path path, String sourceRunId, String sourceTaskId) throws Exception {
    return writeBusinessLinkEnrichmentScopeV3ForTask(
        path, sourceRunId, "Q_LINK_SOURCE", sourceTaskId);
  }

  private Path writeBusinessLinkEnrichmentScopeV3ForTask(
      Path path, String sourceRunId, String sourceQuestionId, String sourceTaskId)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v3");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    root.put("purpose", "ENRICHMENT");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q_LINK_CONSUMER");
    question.put(
        "question", "Describe only an action supported by the selected object definitions.");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    ObjectNode source = question.putArray("objectSources").addObject();
    source.put("identificationRun", sourceRunId);
    source.put("questionId", sourceQuestionId);
    source.putArray("taskIds").add(sourceTaskId);
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", "action-after-selected-link");
    task.put("taskKind", "ACTION");
    task.put("readingMode", "EXPLICIT");
    task.putArray("anchorRefs");
    addUse(task.putArray("unitUses"), "U1", "E1");
    addUse(task.putArray("requiredUnitUses"), "U1", "E1");
    return writeJson(path, root);
  }

  private Path writeBusinessLinkModelSkeletonScopeV2(Path path) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v2");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    root.put("purpose", "SKELETON");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q_MODEL_SKELETON");
    question.put("question", "Describe the selected source-backed object shape.");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    question.putArray("objectSources");
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", "model-object-skeleton");
    task.put("taskKind", "OBJECT");
    task.put("readingMode", "MODEL");
    task.putArray("unitUses");
    task.putArray("requiredUnitUses");
    return writeJson(path, root);
  }

  private Path writeBusinessLinkEnrichmentScopeV2(Path path, String skeletonIdentificationRun)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v2");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    root.put("purpose", "ENRICHMENT");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q_ENRICHMENT");
    question.put(
        "question", "Describe only the selected actions and analytics for the reviewed object.");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    ObjectNode objectSource = question.putArray("objectSources").addObject();
    objectSource.put("identificationRun", skeletonIdentificationRun);
    objectSource.put("questionId", "Q_SKELETON");
    addBusinessLinkTask(question, "action-enrichment", "ACTION");
    addBusinessLinkTask(question, "analytic-enrichment", "ANALYTIC");
    return writeJson(path, root);
  }

  private BusinessLinkRuntimeFixture prepareBusinessLinkRuntime(String fixtureName)
      throws Exception {
    TechnicalFixture fixture = prepareRealR4(fixtureName);
    ConfiguredTypedPipeline configured =
        writeBusinessLinkTypedPipelineConfigurationV3(
            "ontology-" + fixtureName + "-v3.yaml", fixture);
    AtomicInteger providerFactories = new AtomicInteger();
    List<StructuredModelRequest> requests = new ArrayList<>();
    AtomicReference<String> rejectedObjectReviewQuestion = new AtomicReference<>();
    Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
        providerFactory =
            declaration -> {
              providerFactories.incrementAndGet();
              ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
              return request -> {
                requests.add(request);
                JsonNode input = ExplicitTypedPipelineScript.input(request);
                String questionId = input.path("questionId").asText();
                String taskKind = input.path("taskKind").asText();
                boolean review = request.taskKind().contains("REVIEW");
                String responseSchema =
                    review ? "ontology-typed-review-v4" : "ontology-typed-candidate-v4";
                ImmutableBytes response;
                if ("OBJECT".equals(taskKind)) {
                  response = businessLinkFormalObjectResponseV4(responseSchema, questionId, "E1");
                  if (review && questionId.equals(rejectedObjectReviewQuestion.get())) {
                    ObjectNode invalidReview = (ObjectNode) CANONICAL.parseCanonical(response);
                    ((ObjectNode) invalidReview.path("definitions").path("objects").get(0))
                        .put("displayRole", "AUTO_PROMOTED");
                    response = CANONICAL.encodeCanonical(invalidReview);
                  }
                } else if ("ACTION".equals(taskKind) || "ANALYTIC".equals(taskKind)) {
                  JsonNode visibleEntries = input.path("reviewedCatalog").path("entries");
                  String objectRef = visibleEntries.path(0).path("catalogRef").asText();
                  if ("ACTION".equals(taskKind)) {
                    response =
                        businessLinkFormalActionResponseV4(
                            responseSchema, questionId, "E1", objectRef);
                  } else {
                    String propertyRef =
                        visibleEntries.path(0).path("propertyRefs").path(0).asText();
                    response =
                        businessLinkFormalAnalyticResponseV4(
                            responseSchema, questionId, "E1", objectRef, propertyRef);
                  }
                } else {
                  throw new AssertionError("unexpected business-link task kind " + taskKind);
                }
                return new StructuredModelResponse(response, identity);
              };
            };
    CliResult prepared =
        executeWithFactory(
            configured.path(),
            providerFactory,
            "prepare-ontology",
            "--evidence-run",
            fixture.r4RunId());
    assertThat(prepared.exitCode()).withFailMessage(cliDiagnostics(prepared)).isZero();
    assertThat(providerFactories).hasValue(0);
    assertThat(requests).isEmpty();
    return new BusinessLinkRuntimeFixture(
        fixture,
        configured,
        runId(prepared),
        providerFactories,
        requests,
        rejectedObjectReviewQuestion,
        providerFactory);
  }

  private CliResult identifyBusinessLink(BusinessLinkRuntimeFixture runtime, Path scope) {
    return executeWithFactory(
        runtime.configured().path(),
        runtime.providerFactory(),
        "identify-ontology",
        "--corpus-run",
        runtime.corpusRunId(),
        "--scope",
        scope.toString());
  }

  private Path writeBusinessLinkQuestionScopeV2ForTasks(
      Path path,
      String purpose,
      String questionId,
      List<ObjectSource> objectSources,
      List<ScopedTask> tasks)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v2");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    root.put("purpose", purpose);
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", questionId);
    question.put("question", "Describe only the neutral source-backed content selected here.");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    ArrayNode sources = question.putArray("objectSources");
    for (ObjectSource source : objectSources) {
      ObjectNode row = sources.addObject();
      row.put("identificationRun", source.identificationRun());
      row.put("questionId", source.questionId());
    }
    for (ScopedTask task : tasks) {
      addBusinessLinkTask(question, task.taskId(), task.taskKind());
    }
    return writeJson(path, root);
  }

  private JsonNode queryTaskObservation(Path configuration, String runId, String producingTaskId)
      throws Exception {
    CliResult observation =
        executePublic(
            configuration,
            "artifact",
            "--run",
            runId,
            "--key",
            "ONTOLOGY_TASK_RECORD",
            "--task-id",
            producingTaskId,
            "--max-bytes",
            "524288");
    assertArtifactAvailable(observation);
    return JSON.readTree(observation.stdout());
  }

  private Path writeBusinessLinkStandaloneSkeletonScopeV2(Path path) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v2");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    root.put("purpose", "SKELETON");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q_RELATION_OBJECT");
    question.put("question", "Describe this independent selected relation endpoint.");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    question.putArray("objectSources");
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", "independent-relation-object");
    task.put("taskKind", "OBJECT");
    task.put("readingMode", "EXPLICIT");
    unitUses(task.putArray("unitUses"));
    unitUses(task.putArray("requiredUnitUses"));
    return writeJson(path, root);
  }

  private static void addBusinessLinkTask(ObjectNode question, String taskId, String taskKind) {
    ObjectNode task = question.withArray("tasks").addObject();
    task.put("taskId", taskId);
    task.put("taskKind", taskKind);
    task.put("readingMode", "EXPLICIT");
    unitUses(task.putArray("unitUses"));
    unitUses(task.putArray("requiredUnitUses"));
  }

  private Path writeExplicitTypedTaskScope(Path path) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q1");
    question.put("question", "What neutral source-backed shape is visible in this entry?");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    addExplicitTask(question, "object-task", "OBJECT");
    addExplicitTask(question, "action-task", "ACTION");
    addExplicitTask(question, "analytic-task", "ANALYTIC");
    return writeJson(path, root);
  }

  private Path writeFormalTaskScope(Path path, ScopedQuestion... questions) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ArrayNode questionNodes = root.putArray("questions");
    for (ScopedQuestion question : questions) {
      ObjectNode questionNode = questionNodes.addObject();
      questionNode.put("questionId", question.questionId());
      questionNode.put("question", "Which source-backed shape is visible in this entry?");
      questionNode.putArray("entryRefs").add("E1");
      questionNode.putArray("clueRefs");
      for (ScopedTask task : question.tasks()) {
        addExplicitTask(questionNode, task.taskId(), task.taskKind());
      }
    }
    return writeJson(path, root);
  }

  private Path writePartialDuplicateProblemScope(Path path) throws Exception {
    return writeFormalTaskScope(
        path,
        new ScopedQuestion("Qbad", List.of(new ScopedTask("shared-invalid", "OBJECT"))),
        new ScopedQuestion(
            "Q1",
            List.of(
                new ScopedTask("reviewed-left", "OBJECT"),
                new ScopedTask("reviewed-right", "OBJECT"))));
  }

  private Path writePartialDependencyDuplicateProblemScope(Path path, String failedObjectTaskId)
      throws Exception {
    return writeFormalTaskScope(
        path,
        new ScopedQuestion(
            "Qbad",
            List.of(
                new ScopedTask(failedObjectTaskId, "OBJECT"),
                new ScopedTask("shared-unprocessed", "ACTION"))),
        new ScopedQuestion(
            "Q1",
            List.of(
                new ScopedTask("reviewed-left", "OBJECT"),
                new ScopedTask("reviewed-right", "OBJECT"))));
  }

  private Path writeMeasuredObjectTaskScope(
      Path path, String entryRef, String oversizedUnitRef, String fittingUnitRef) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ArrayNode questions = root.putArray("questions");
    addMeasuredObjectQuestion(
        questions.addObject(), "Q1", "large-object", entryRef, oversizedUnitRef);
    addMeasuredObjectQuestion(
        questions.addObject(), "Q2", "small-object", entryRef, fittingUnitRef);
    return writeJson(path, root);
  }

  private static void addMeasuredObjectQuestion(
      ObjectNode question, String questionId, String taskId, String entryRef, String unitRef) {
    question.put("questionId", questionId);
    question.put("question", "Which complete saved source unit supports " + questionId + "?");
    question.putArray("entryRefs").add(entryRef);
    question.putArray("clueRefs");
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", taskId);
    task.put("taskKind", "OBJECT");
    task.put("readingMode", "EXPLICIT");
    addUse(task.putArray("unitUses"), unitRef, entryRef);
    addUse(task.putArray("requiredUnitUses"), unitRef, entryRef);
  }

  private static void addExplicitTask(ObjectNode question, String taskId, String taskKind) {
    ObjectNode task = question.withArray("tasks").addObject();
    task.put("taskId", taskId);
    task.put("taskKind", taskKind);
    task.put("readingMode", "EXPLICIT");
    unitUses(task.putArray("unitUses"));
    unitUses(task.putArray("requiredUnitUses"));
  }

  private Path writeScopeWithUnknownUse(Path path, String entryRef, String unitRef)
      throws Exception {
    ObjectNode root = (ObjectNode) JSON.readTree(Files.readString(writeExplicitScope(path)));
    ObjectNode question = (ObjectNode) root.path("questions").get(0);
    ArrayNode entryRefs = (ArrayNode) question.path("entryRefs");
    entryRefs.removeAll();
    entryRefs.add(entryRef);
    ObjectNode task = (ObjectNode) question.path("tasks").get(0);
    for (String field : List.of("unitUses", "requiredUnitUses")) {
      ArrayNode uses = (ArrayNode) task.path(field);
      uses.removeAll();
      ObjectNode use = uses.addObject();
      use.put("unitRef", unitRef);
      use.put("entryRef", entryRef);
    }
    return writeJson(path, root);
  }

  private Path writeSelection(
      Path path,
      String operation,
      String corpusRun,
      List<String> identificationRuns,
      List<String> otherRuns,
      boolean relate)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v1");
    root.put("operation", operation);
    root.put("corpusRun", corpusRun);
    ArrayNode identifications = root.putArray("identificationRuns");
    identificationRuns.forEach(identifications::add);
    if (relate) {
      root.putArray("questions");
    } else {
      ArrayNode relations = root.putArray("relationRuns");
      otherRuns.forEach(relations::add);
    }
    return writeJson(path, root);
  }

  private Path writeRelateSelection(Path path, String corpusRun, String identificationRun)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v1");
    root.put("operation", "RELATE");
    root.put("corpusRun", corpusRun);
    root.putArray("identificationRuns").add(identificationRun);
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q2");
    question.put("question", "Does the selected reviewed evidence establish a relation?");
    question.put("taskId", "relate-task");
    question.put("readingMode", "EXPLICIT");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    unitUses(question.putArray("unitUses"));
    unitUses(question.putArray("requiredUnitUses"));
    return writeJson(path, root);
  }

  private Path writeMultiRelateSelection(
      Path path, String corpusRun, List<String> identificationRuns) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v1");
    root.put("operation", "RELATE");
    root.put("corpusRun", corpusRun);
    ArrayNode identifications = root.putArray("identificationRuns");
    identificationRuns.forEach(identifications::add);
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q3");
    question.put("question", "Does the actual reviewed catalog establish a relation?");
    question.put("taskId", "multi-relate-task");
    question.put("readingMode", "EXPLICIT");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    unitUses(question.putArray("unitUses"));
    unitUses(question.putArray("requiredUnitUses"));
    return writeJson(path, root);
  }

  private Path writeRelateSelectionV2(
      Path path,
      String corpusRun,
      List<String> identificationRuns,
      SelectedRelateQuestion... questions)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v2");
    root.put("operation", "RELATE");
    root.put("corpusRun", corpusRun);
    ArrayNode identifications = root.putArray("identificationRuns");
    identificationRuns.forEach(identifications::add);
    ArrayNode selectedQuestions = root.putArray("questions");
    for (SelectedRelateQuestion selected : questions) {
      ObjectNode question = selectedQuestions.addObject();
      question.put("questionId", selected.questionId());
      question.put(
          "question", "Does the explicitly selected reviewed evidence establish a relation?");
      question.put("taskId", selected.taskId());
      question.put("readingMode", "EXPLICIT");
      question.putArray("entryRefs").add("E1");
      question.putArray("clueRefs");
      unitUses(question.putArray("unitUses"));
      unitUses(question.putArray("requiredUnitUses"));
      ArrayNode objectSources = question.putArray("objectSources");
      for (ObjectSource source : selected.objectSources()) {
        ObjectNode objectSource = objectSources.addObject();
        objectSource.put("identificationRun", source.identificationRun());
        objectSource.put("questionId", source.questionId());
      }
    }
    return writeJson(path, root);
  }

  private Path writeBusinessLinkModelReadRelateSelectionV2(
      Path path, String corpusRun, String identificationRun, String selectedEntryRef)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v2");
    root.put("operation", "RELATE");
    root.put("corpusRun", corpusRun);
    root.putArray("identificationRuns").add(identificationRun);
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q_RELATE");
    question.put("question", "Determine whether the selected evidence supports a relation.");
    question.put("taskId", "model-relate-task");
    question.put("readingMode", "MODEL");
    question.putArray("entryRefs").add(selectedEntryRef);
    question.putArray("clueRefs");
    question.putArray("unitUses");
    question.putArray("requiredUnitUses");
    ObjectNode objectSource = question.putArray("objectSources").addObject();
    objectSource.put("identificationRun", identificationRun);
    objectSource.put("questionId", "Q_SKELETON");
    return writeJson(path, root);
  }

  private Path writeBusinessLinkModelFailureAndExplicitRelateSelectionV2(
      Path path,
      String corpusRun,
      String identificationRun,
      String selectedEntryRef,
      String selectedUnitRef)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v2");
    root.put("operation", "RELATE");
    root.put("corpusRun", corpusRun);
    root.putArray("identificationRuns").add(identificationRun);
    ArrayNode questions = root.putArray("questions");
    ObjectNode modelQuestion = questions.addObject();
    modelQuestion.put("questionId", "Q_MODEL_PARTIAL");
    modelQuestion.put("question", "Read the selected evidence before deciding its relation.");
    modelQuestion.put("taskId", "model-relate-partial");
    modelQuestion.put("readingMode", "MODEL");
    modelQuestion.putArray("entryRefs").add(selectedEntryRef);
    modelQuestion.putArray("clueRefs");
    modelQuestion.putArray("unitUses");
    modelQuestion.putArray("requiredUnitUses");
    ObjectNode modelObjectSource = modelQuestion.putArray("objectSources").addObject();
    modelObjectSource.put("identificationRun", identificationRun);
    modelObjectSource.put("questionId", "Q_SKELETON");

    ObjectNode explicitQuestion = questions.addObject();
    explicitQuestion.put("questionId", "Q_EXPLICIT_RELATE");
    explicitQuestion.put("question", "Check the explicitly selected evidence for a relation.");
    explicitQuestion.put("taskId", "explicit-relate-task");
    explicitQuestion.put("readingMode", "EXPLICIT");
    explicitQuestion.putArray("entryRefs").add(selectedEntryRef);
    explicitQuestion.putArray("clueRefs");
    ObjectNode activeUse = explicitQuestion.putArray("unitUses").addObject();
    activeUse.put("unitRef", selectedUnitRef);
    activeUse.put("entryRef", selectedEntryRef);
    ObjectNode requiredUse = explicitQuestion.putArray("requiredUnitUses").addObject();
    requiredUse.put("unitRef", selectedUnitRef);
    requiredUse.put("entryRef", selectedEntryRef);
    ObjectNode explicitObjectSource = explicitQuestion.putArray("objectSources").addObject();
    explicitObjectSource.put("identificationRun", identificationRun);
    explicitObjectSource.put("questionId", "Q_SKELETON");
    return writeJson(path, root);
  }

  private Path writePublishSelectionV2(
      Path path, String corpusRun, List<String> identificationRuns, List<String> relationRuns)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v2");
    root.put("operation", "PUBLISH");
    root.put("corpusRun", corpusRun);
    ArrayNode identifications = root.putArray("identificationRuns");
    identificationRuns.forEach(identifications::add);
    ArrayNode relations = root.putArray("relationRuns");
    relationRuns.forEach(relations::add);
    return writeJson(path, root);
  }

  private Path writePublishSelectionV3(Path path, String corpusRun, List<String> identificationRuns)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v3");
    root.put("operation", "PUBLISH");
    root.put("corpusRun", corpusRun);
    ArrayNode identifications = root.putArray("identificationRuns");
    identificationRuns.forEach(identifications::add);
    root.putArray("relationRuns");
    return writeJson(path, root);
  }

  private Path writeBusinessLinkRelateSelectionV3ForTask(
      Path path,
      String corpusRun,
      String identificationRun,
      String sourceQuestionId,
      String sourceTaskId)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v3");
    root.put("operation", "RELATE");
    root.put("corpusRun", corpusRun);
    root.putArray("identificationRuns").add(identificationRun);
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q_LINK_RELATION");
    question.put("question", "Relate only the explicitly selected LINK objects.");
    question.put("taskId", "relate-selected-link");
    question.put("readingMode", "EXPLICIT");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs");
    unitUses(question.putArray("unitUses"));
    unitUses(question.putArray("requiredUnitUses"));
    ObjectNode objectSource = question.putArray("objectSources").addObject();
    objectSource.put("identificationRun", identificationRun);
    objectSource.put("questionId", sourceQuestionId);
    objectSource.putArray("taskIds").add(sourceTaskId);
    return writeJson(path, root);
  }

  private Path writePublishSelectionV3WithRelations(
      Path path, String corpusRun, List<String> identificationRuns, List<String> relationRuns)
      throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v3");
    root.put("operation", "PUBLISH");
    root.put("corpusRun", corpusRun);
    ArrayNode identifications = root.putArray("identificationRuns");
    identificationRuns.forEach(identifications::add);
    ArrayNode relations = root.putArray("relationRuns");
    relationRuns.forEach(relations::add);
    return writeJson(path, root);
  }

  private static void unitUses(ArrayNode array) {
    ObjectNode use = array.addObject();
    use.put("unitRef", "U1");
    use.put("entryRef", "E1");
  }

  private static Path writeJson(Path path, JsonNode value) throws Exception {
    Files.write(path, CANONICAL.encodeCanonical(value).copyToByteArray());
    return path;
  }

  private static ImmutableBytes candidate(String schemaVersion) {
    return objectResponse(schemaVersion);
  }

  private static void assertSavedFormalRequest(
      JsonNode savedRequest, StructuredModelRequest dispatchedRequest) throws Exception {
    assertThat(savedRequest.path("taskId").asText()).isEqualTo(dispatchedRequest.taskId());
    assertThat(savedRequest.path("taskKind").asText()).isEqualTo(dispatchedRequest.taskKind());
    assertThat(savedRequest.path("systemInstructions").asText())
        .isEqualTo(dispatchedRequest.systemInstructions());
    assertThat(savedRequest.path("untrustedInput"))
        .isEqualTo(JSON.readTree(dispatchedRequest.untrustedInputJson().copyToByteArray()));
    assertThat(savedRequest.path("outputSchema"))
        .isEqualTo(JSON.readTree(dispatchedRequest.outputJsonSchema().copyToByteArray()));
  }

  private static void assertSavedFormalResponseAndValidation(
      JsonNode savedStage, ImmutableBytes scriptedResponse, String disposition) throws Exception {
    String responseBase64 = savedStage.path("response").path("rawResponseBase64").asText();
    assertThat(responseBase64).isNotBlank();
    JsonNode actualResponse = JSON.readTree(Base64.getDecoder().decode(responseBase64));
    JsonNode expectedResponse = JSON.readTree(scriptedResponse.copyToByteArray());
    assertThat(actualResponse).isEqualTo(expectedResponse);
    JsonNode validation = savedStage.path("validation");
    assertThat(validation.path("disposition").asText()).isEqualTo(disposition);
    assertThat(validation.path("rawResponseBase64").asText()).isEqualTo(responseBase64);
    assertThat(
            JSON.readTree(
                Base64.getDecoder().decode(validation.path("rawResponseBase64").asText())))
        .isEqualTo(expectedResponse);
  }

  private static ImmutableBytes review(String schemaVersion) {
    return objectResponse(schemaVersion);
  }

  private static ImmutableBytes objectResponse(String schemaVersion) {
    ObjectNode result = JSON.createObjectNode();
    result.put("schemaVersion", schemaVersion);
    result.put("taskKind", "OBJECT");
    ObjectNode definitions = result.putObject("definitions");
    ObjectNode object = definitions.putArray("objects").addObject();
    object.put("localId", "O1");
    object.put("name", "Neutral source-backed record");
    object.put("definition", "A neutral record-shaped value visible in the selected method.");
    object.put("origin", "IMPLEMENTATION");
    object.put("certainty", "INFERRED");
    ObjectNode scope = object.putObject("scope");
    scope.put("questionRef", "Q1");
    scope.putArray("entryUseRefs").add("E1");
    scope.putArray("variants");
    object.putArray("evidenceRefs").add("S1");
    object.putArray("identities");
    object.putArray("properties");
    object.putArray("backing");
    object.putArray("variants");
    ArrayNode unknowns = object.putArray("unknowns");
    ObjectNode identityUnknown = unknowns.addObject();
    identityUnknown.put("field", "identity");
    identityUnknown.put(
        "reason", "The neutral source fixture does not confirm a business identity.");
    identityUnknown.putArray("missingUnitRefs");
    object.put("definitionCompleteness", "PARTIAL");
    result.putArray("unresolved");
    result.putArray("corrections");
    return CANONICAL.encodeCanonical(result);
  }

  private static String modelJobsYaml() {
    return """
    modelJobs:
      maxConcurrentJobs: 1
      providers:
        scripted:
          kind: codexSubscription
          quotaScope: task7-scripted-neutral
          model: gpt-5.6-luna
          reasoningEffort: high
          auth:
            mode: chatgpt
            codexHomeEnv: ONTOLOGY_TASK7_FIXTURE_MUST_NOT_READ_AUTH
      routing:
        survey: [scripted]
        extract: [scripted]
        relate: [scripted]
    """;
  }

  private static String typedModelJobsYaml() {
    return """
    modelJobs:
      maxConcurrentJobs: 1
      providers:
        scripted-extract:
          kind: codexSubscription
          quotaScope: task7-scripted-extract
          model: gpt-5.6-luna
          reasoningEffort: high
          auth:
            mode: chatgpt
            codexHomeEnv: ONTOLOGY_TASK7_FIXTURE_MUST_NOT_READ_AUTH
        scripted-relate:
          kind: codexSubscription
          quotaScope: task7-scripted-relate
          model: gpt-5.6-sol
          reasoningEffort: high
          auth:
            mode: chatgpt
            codexHomeEnv: ONTOLOGY_TASK7_FIXTURE_MUST_NOT_READ_AUTH
      routing:
        survey: [scripted-extract]
        extract: [scripted-extract]
        relate: [scripted-relate]
    """;
  }

  private static CliResult artifact(Path config, String runId, String key) {
    return executePublic(config, "artifact", "--run", runId, "--key", key, "--max-bytes", "524288");
  }

  private static CliResult executePublic(Path config, String... command) {
    return invoke(config, command, null);
  }

  private static CliResult executeWithFactory(
      Path config,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory,
      String... command) {
    return invoke(config, command, providerFactory);
  }

  private static String cliDiagnostics(CliResult result) {
    return "exitCode: "
        + result.exitCode()
        + "\nstdout:\n"
        + result.stdout()
        + "\nstderr:\n"
        + result.stderr();
  }

  private static CliResult invoke(
      Path config,
      String[] command,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory) {
    String[] arguments = new String[command.length + 2];
    arguments[0] = "--config";
    arguments[1] = config.toAbsolutePath().toString();
    System.arraycopy(command, 0, arguments, 2, command.length);
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    PrintWriter out = new PrintWriter(stdout, true, StandardCharsets.UTF_8);
    PrintWriter err = new PrintWriter(stderr, true, StandardCharsets.UTF_8);
    int status =
        providerFactory == null
            ? SourceAnalysisCli.executeConfigured(arguments, out, err)
            : SourceAnalysisCli.executeConfigured(arguments, out, err, providerFactory);
    return new CliResult(
        status, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  private static TechResult executeTechnical(
      Path config,
      String operation,
      List<String> options,
      java.util.function.Function<JavaCompilationEnvironment, JavaCodeSession> sessionOpener,
      java.util.function.Supplier<FrontendSyntaxTool> frontendSupplier) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    int status =
        TechnicalAnalysisConfiguredRuntime.execute(
            config.toAbsolutePath(),
            operation,
            options,
            new PrintWriter(stdout, true, StandardCharsets.UTF_8),
            new PrintWriter(stderr, true, StandardCharsets.UTF_8),
            sessionOpener,
            frontendSupplier);
    return new TechResult(
        status, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  private static String runId(CliResult result) throws Exception {
    return runId(result.stdout());
  }

  private static String runId(String json) throws Exception {
    return JSON.readTree(json).path("runId").asText();
  }

  private static void assertOntologyConfigurationDiagnostic(CliResult result) {
    assertThat(result.exitCode()).isNotZero();
    assertThat(result.stdout()).isEmpty();
    assertThat(result.stderr()).contains("ONTOLOGY").doesNotContain("CREDENTIAL", "API_KEY");
  }

  private static void assertArtifactAvailable(CliResult artifact) {
    assertThat(artifact.exitCode()).withFailMessage(artifact.stderr()).isZero();
    assertThat(artifact.stderr()).isEmpty();
    assertThat(artifact.stdout()).isNotBlank();
  }

  private JsonNode assertCommandAndInspectionAgree(
      CliResult command, Path configuration, String operation, String expectedRunId, int dispatches)
      throws Exception {
    assertThat(command.stdout()).isNotBlank();
    JsonNode commandObservation = JSON.readTree(command.stdout());
    assertObservationShape(commandObservation);
    assertThat(commandObservation.path("runId").asText()).isEqualTo(expectedRunId);
    assertThat(commandObservation.path("operation").asText()).isEqualTo(operation);
    assertThat(commandObservation.path("modelRequestsDispatched").isIntegralNumber()).isTrue();
    assertThat(commandObservation.path("modelRequestsDispatched").asInt()).isEqualTo(dispatches);

    CliResult inspected = executePublic(configuration, "inspect", "--run", expectedRunId);
    assertThat(inspected.exitCode()).withFailMessage(inspected.stderr()).isZero();
    JsonNode inspectObservation = JSON.readTree(inspected.stdout());
    assertObservationShape(inspectObservation);
    assertThat(inspectObservation.path("runId").asText()).isEqualTo(expectedRunId);
    assertThat(inspectObservation.path("operation").asText()).isEqualTo(operation);
    assertThat(inspectObservation.path("lifecycleState"))
        .isEqualTo(commandObservation.path("lifecycleState"));
    assertThat(inspectObservation.path("resultStatus"))
        .isEqualTo(commandObservation.path("resultStatus"));
    assertThat(inspectObservation.path("canContinue"))
        .isEqualTo(commandObservation.path("canContinue"));
    assertThat(inspectObservation.path("availableArtifactKeys"))
        .isEqualTo(commandObservation.path("availableArtifactKeys"));
    assertThat(inspectObservation.path("problems")).isEqualTo(commandObservation.path("problems"));
    assertThat(inspectObservation.path("modelRequestsDispatched").isIntegralNumber()).isTrue();
    assertThat(inspectObservation.path("modelRequestsDispatched").asInt()).isEqualTo(dispatches);
    return commandObservation;
  }

  private static void assertObservationShape(JsonNode observation) {
    Set<String> fields = new LinkedHashSet<>();
    observation.fieldNames().forEachRemaining(fields::add);
    boolean v2 =
        "ontology-operation-observation-v2".equals(observation.path("schemaVersion").asText());
    if (v2) {
      assertThat(fields)
          .containsExactlyInAnyOrder(
              "runId",
              "operation",
              "lifecycleState",
              "resultStatus",
              "canContinue",
              "availableArtifactKeys",
              "problems",
              "modelRequestsDispatched",
              "schemaVersion",
              "taskOutcomes",
              "modelRequestCounts",
              "nextActions");
      assertThat(observation.path("taskOutcomes").isArray()).isTrue();
      assertThat(observation.path("nextActions").isArray()).isTrue();
      JsonNode counts = observation.path("modelRequestCounts");
      assertThat(counts.isObject()).isTrue();
      Set<String> countFields = new LinkedHashSet<>();
      counts.fieldNames().forEachRemaining(countFields::add);
      assertThat(countFields)
          .containsExactlyInAnyOrder(
              "reservedAttempts", "confirmedStarted", "confirmedEnded", "outcomeUnknown");
    } else {
      assertThat(fields)
          .containsExactlyInAnyOrder(
              "runId",
              "operation",
              "lifecycleState",
              "resultStatus",
              "canContinue",
              "availableArtifactKeys",
              "problems",
              "modelRequestsDispatched");
    }
    assertThat(observation.path("runId").asText()).isNotBlank();
    assertThat(observation.path("operation").asText())
        .isIn("PREPARE_ONTOLOGY", "IDENTIFY_ONTOLOGY", "RELATE_ONTOLOGY", "PUBLISH_ONTOLOGY");
    assertThat(observation.path("lifecycleState").asText())
        .isIn("QUEUED", "RUNNING", "FINISHED", "FAILED");
    assertThat(
            observation.path("resultStatus").isNull()
                || observation.path("resultStatus").isTextual())
        .isTrue();
    if (observation.path("resultStatus").isTextual()) {
      assertThat(observation.path("resultStatus").asText()).isIn("COMPLETED", "PARTIAL", "BLOCKED");
    }
    assertThat(observation.path("canContinue").isBoolean()).isTrue();
    assertThat(observation.path("availableArtifactKeys").isArray()).isTrue();
    List<String> availableKeys = stringValues(observation.path("availableArtifactKeys"));
    Set<String> allowedKeys =
        Set.of(
            "ONTOLOGY_CORPUS",
            "ONTOLOGY_IDENTIFICATION",
            "ONTOLOGY_RELATIONS",
            "ONTOLOGY",
            "ONTOLOGY_COVERAGE",
            "ONTOLOGY_SOURCE_INDEX",
            "ONTOLOGY_REVIEW");
    assertThat(allowedKeys.containsAll(availableKeys)).isTrue();
    assertThat(availableKeys).doesNotHaveDuplicates().isSorted();
    assertThat(observation.path("problems").isArray()).isTrue();
    for (JsonNode problem : observation.path("problems")) {
      Set<String> problemFields = new LinkedHashSet<>();
      problem.fieldNames().forEachRemaining(problemFields::add);
      if (v2) {
        assertThat(problemFields).containsExactlyInAnyOrder("code", "taskId", "stage", "category");
        assertThat(problem.path("category").asText()).isNotBlank();
      } else {
        assertThat(problemFields).containsExactlyInAnyOrder("code", "taskId", "stage");
      }
      assertThat(problem.path("code").asText()).isNotBlank();
      assertNullableText(problem.path("taskId"));
      assertNullableText(problem.path("stage"));
    }
    JsonNode dispatched = observation.path("modelRequestsDispatched");
    assertThat(dispatched.isNull() || dispatched.isIntegralNumber()).isTrue();
  }

  private static void assertNullableText(JsonNode value) {
    assertThat(value.isNull() || (value.isTextual() && !value.asText().isBlank())).isTrue();
  }

  private List<String> availablePublicArtifactKeys(Path configuration, String runId) {
    List<String> candidates =
        List.of(
            "ONTOLOGY",
            "ONTOLOGY_CORPUS",
            "ONTOLOGY_COVERAGE",
            "ONTOLOGY_IDENTIFICATION",
            "ONTOLOGY_RELATIONS",
            "ONTOLOGY_REVIEW",
            "ONTOLOGY_SOURCE_INDEX");
    List<String> actual = new ArrayList<>();
    for (String key : candidates) {
      CliResult result = artifact(configuration, runId, key);
      if (result.exitCode() == 0) {
        assertArtifactAvailable(result);
        actual.add(key);
      } else {
        assertThat(result.stdout()).isEmpty();
      }
    }
    return List.copyOf(actual);
  }

  private static List<String> stringValues(JsonNode array) {
    List<String> values = new ArrayList<>();
    array.forEach(value -> values.add(value.asText()));
    return List.copyOf(values);
  }

  private static List<JsonNode> arrayValues(JsonNode array) {
    List<JsonNode> values = new ArrayList<>();
    array.forEach(values::add);
    return List.copyOf(values);
  }

  private static List<String> inheritedTaskOutcomeOwners(JsonNode taskOutcomes) {
    return arrayValues(taskOutcomes).stream()
        .map(
            outcome ->
                outcome.path("runId").asText()
                    + "/"
                    + outcome.path("questionId").asText()
                    + "/"
                    + outcome.path("taskId").asText()
                    + "/"
                    + outcome.path("producingTaskId").asText())
        .toList();
  }

  private static List<String> objectSourceOwners(JsonNode relations) {
    JsonNode questions = relations.path("selection").path("questions");
    if (!questions.isArray() || questions.size() != 1) {
      throw new AssertionError("relation run must retain its one exact selected question");
    }
    return arrayValues(questions.get(0).path("objectSources")).stream()
        .map(
            source ->
                source.path("identificationRun").asText()
                    + "/"
                    + source.path("questionId").asText())
        .toList();
  }

  private static String problemIdentity(JsonNode problem) {
    return problem.path("taskId").asText()
        + "/"
        + problem.path("code").asText()
        + "/"
        + problem.path("category").asText()
        + "/"
        + problem.path("stage").asText();
  }

  private static JsonNode providerInputFromPrompt(String prompt) {
    String marker = "结构化输入：\n";
    String endMarker = "\n\n只返回与提供 JSON Schema 相符的 JSON";
    int start = prompt.indexOf(marker);
    int end = start < 0 ? -1 : prompt.indexOf(endMarker, start + marker.length());
    if (start < 0 || end < 0) {
      throw new AssertionError("Codex command received no bounded structured input section");
    }
    try {
      return JSON.readTree(prompt.substring(start + marker.length(), end));
    } catch (Exception invalidInput) {
      throw new AssertionError("Codex command structured input was not JSON", invalidInput);
    }
  }

  private static BlockedAttempt blockFormalResponseAttempt(Path runStore, String questionId)
      throws Exception {
    Path modelJobs = runStore.resolve("ontology-journal").resolve("model-jobs");
    Path requestPath;
    try (var paths = Files.walk(modelJobs)) {
      requestPath =
          paths
              .filter(path -> "request.json".equals(path.getFileName().toString()))
              .filter(
                  path ->
                      path.getParent() != null
                          && path.getParent().getParent() != null
                          && "formal-typed-extract"
                              .equals(path.getParent().getParent().getFileName().toString()))
              .filter(
                  path -> {
                    try {
                      JsonNode request = JSON.readTree(Files.readAllBytes(path));
                      return questionId.equals(
                          request.path("untrustedInput").path("questionId").asText());
                    } catch (Exception invalidRequest) {
                      throw new AssertionError(
                          "saved formal EXTRACT request was not readable", invalidRequest);
                    }
                  })
              .findFirst()
              .orElseThrow(() -> new AssertionError("missing saved request for " + questionId));
    }
    byte[] requestBytes = Files.readAllBytes(requestPath);
    Path attemptDirectory = requestPath.getParent();
    Files.delete(requestPath);
    Files.delete(attemptDirectory);
    Files.writeString(
        attemptDirectory, "test-only non-directory journal obstruction", StandardCharsets.UTF_8);
    return new BlockedAttempt(attemptDirectory, requestBytes);
  }

  private static void restoreFormalResponseAttempt(BlockedAttempt blocked) throws Exception {
    if (blocked == null) {
      return;
    }
    Files.delete(blocked.attemptDirectory());
    Files.createDirectory(blocked.attemptDirectory());
    Files.write(blocked.attemptDirectory().resolve("request.json"), blocked.requestBytes());
  }

  private void assertCorpusPreparationWire(
      JsonNode corpus, TechnicalFixture fixture, String o0RunId, int maxNavigationEntries)
      throws Exception {
    assertThat(corpus.path("ownerRunId").asText()).isEqualTo(o0RunId);
    assertThat(corpus.path("evidenceRunId").asText()).isEqualTo(fixture.r4RunId());
    assertThat(corpus.path("contentSourceIdentity").asText()).isNotBlank();
    assertThat(corpus.path("corpusIdentity").asText()).isNotBlank();
    assertThat(corpus.path("projectionRuleVersion").asText())
        .isEqualTo("ontology-model-projection-v1");

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      AnalysisRunRequest request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, AnalysisRunId.parse(o0RunId))
              .request();
      AnalysisRunRequest r4Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                  store, AnalysisRunId.parse(fixture.r4RunId()))
              .request();
      AnalysisRunRequest.OntologyInputs inputs = request.ontologyInputs();
      AnalysisRunRequest.TechnicalAnalysisInputs technical = r4Request.technicalAnalysisInputs();
      assertThat(inputs.operation())
          .isEqualTo(AnalysisRunRequest.OntologyOperation.PREPARE_ONTOLOGY);
      assertThat(request.selectedSourceBasis()).isEqualTo(r4Request.selectedSourceBasis());
      assertThat(corpus.path("selectedSourceBasis")).isEqualTo(r4SelectedSourceBasis(fixture));
      assertThat(
              corpus
                  .path("selectedSourceBasis")
                  .path("preparedSource")
                  .path("publication")
                  .isObject())
          .isTrue();
      assertThat(corpus.path("selectedSourceBasis").path("snapshotId").asText())
          .startsWith("snapshot:");
      assertThat(corpus.path("selectedSourceBasis").path("effectiveScopeDigest").asText())
          .matches("[0-9a-f]{64}");
      assertThat(corpus.path("evidencePublication"))
          .isEqualTo(analysisStepPublicationNode(inputs.evidencePublication()));

      ObjectNode bindings = JSON.createObjectNode();
      bindings.set("resourceBudgetRef", artifactReferenceNode(technical.resourceBudgetRef()));
      bindings.set("schemaBundleRef", artifactReferenceNode(technical.schemaBundleRef()));
      bindings.set("toolchainRef", artifactReferenceNode(technical.toolchainRef()));
      assertThat(corpus.path("preparationBindings")).isEqualTo(bindings);

      ObjectNode controls = JSON.createObjectNode();
      controls.put("maxUnitBytes", 1_048_576);
      controls.put("maxRequestBytes", 524_288);
      controls.put("maxOutputBytes", 100_000);
      controls.put("maxOutputTokens", 2_048);
      controls.put("maxRequests", 8);
      controls.put("maxReadingRounds", 4);
      controls.put("maxActionsPerRound", 4);
      controls.put("maxNavigationEntries", maxNavigationEntries);
      assertThat(corpus.path("preparationControls")).isEqualTo(controls);
    }
  }

  private static JsonNode canonicalNode(Object value) {
    return CANONICAL.parseCanonical(CANONICAL.encodeCanonical(JSON.valueToTree(value)));
  }

  private JsonNode r4SelectedSourceBasis(TechnicalFixture fixture) throws Exception {
    ReopenedModulePublication module = reopenedEvidenceModule(fixture);
    for (VerifiedCanonicalPayload payload : module.payloads()) {
      if ("ENTRY_EVIDENCE_INDEX".equals(payload.descriptor().artifactType())) {
        JsonNode index = CANONICAL.parseCanonical(payload.canonicalUtf8());
        JsonNode basis = index.path("header").path("sourceBasis");
        if (basis.isObject()) {
          return basis.deepCopy();
        }
      }
    }
    throw new AssertionError("verified R4 module lacks its exact header.sourceBasis wire");
  }

  private static ObjectNode artifactReferenceNode(ArtifactReference reference) {
    ObjectNode value = JSON.createObjectNode();
    value.put("artifactId", reference.artifactId().value());
    value.put("sha256", reference.sha256().value());
    return value;
  }

  private static ObjectNode analysisStepPublicationNode(
      AnalysisStepPublicationReference reference) {
    ObjectNode value = JSON.createObjectNode();
    ObjectNode address = value.putObject("address");
    address.put("runId", reference.address().runId().value());
    address.put("analysisStepKey", reference.address().analysisStepKey().wireValue());
    value.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    value.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    value.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
    return value;
  }

  private static JsonNode findTaskRecord(JsonNode document, String taskId) {
    for (JsonNode record : document.path("taskRecords")) {
      if (taskId.equals(record.path("taskId").asText())) {
        return record;
      }
    }
    throw new AssertionError("missing actual task record " + taskId);
  }

  private static JsonNode findTaskOutcome(JsonNode document, String taskId) {
    return arrayValues(document.path("taskOutcomes")).stream()
        .filter(outcome -> taskId.equals(outcome.path("taskId").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing task outcome " + taskId));
  }

  private static JsonNode findReviewTask(JsonNode review, String producingTaskId) {
    for (JsonNode task : review.path("taskResults")) {
      if (producingTaskId.equals(task.path("producingTaskId").asText())) {
        return task;
      }
    }
    throw new AssertionError("missing actual review task " + producingTaskId);
  }

  private static JsonNode sourceIndexRow(String jsonl, String localRef) throws Exception {
    for (String line : jsonl.lines().toList()) {
      if (!line.isBlank()) {
        JsonNode row = JSON.readTree(line);
        if (localRef.equals(row.path("localRef").asText())) {
          return row;
        }
      }
    }
    throw new AssertionError("missing reversible source index row " + localRef);
  }

  private ReopenedModulePublication reopenedEvidenceModule(TechnicalFixture fixture)
      throws Exception {
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(TECHNICAL_POLICY_SET, CANONICAL);
      ArtifactStoreLimits limits = testStoreLimits();
      ReopenedAnalysisStepPublication step =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, CANONICAL, policies, limits)
              .reopen(fixture.r4Publication());
      assertThat(step.reference()).isEqualTo(fixture.r4Publication());
      assertThat(step.receipt().publicationProvenance())
          .isInstanceOf(AnalysisStepPublisherModuleProvenance.class);
      AnalysisStepPublisherModuleProvenance provenance =
          (AnalysisStepPublisherModuleProvenance) step.receipt().publicationProvenance();
      ReopenedModulePublication module =
          new FileSystemCanonicalModuleArtifactStore(store, CANONICAL, policies, limits)
              .reopen(provenance.publisherSpecificationModuleReference());
      assertThat(module.payloads()).isNotEmpty();
      return module;
    }
  }

  private EntryEvidenceReader.Directory reopenedEvidenceDirectory(TechnicalFixture fixture)
      throws Exception {
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(TECHNICAL_POLICY_SET, CANONICAL);
      ArtifactStoreLimits limits = testStoreLimits();
      return new EntryEvidenceReader(
              new FileSystemCanonicalModuleArtifactStore(store, CANONICAL, policies, limits),
              new FileSystemCanonicalAnalysisStepArtifactStore(store, CANONICAL, policies, limits))
          .reopenV2(fixture.r4Publication());
    }
  }

  private OntologyEvidenceCorpus reopenedOntologyEvidenceCorpus(TechnicalFixture fixture)
      throws Exception {
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(TECHNICAL_POLICY_SET, CANONICAL);
      ArtifactStoreLimits limits = testStoreLimits();
      EntryEvidenceReader reader =
          new EntryEvidenceReader(
              new FileSystemCanonicalModuleArtifactStore(store, CANONICAL, policies, limits),
              new FileSystemCanonicalAnalysisStepArtifactStore(store, CANONICAL, policies, limits));
      return OntologyEvidenceCorpus.open(reader, fixture.r4Publication());
    }
  }

  private OntologyEvidenceCorpus reopenedPreparedOntologyEvidenceCorpus(TechnicalFixture fixture)
      throws Exception {
    OntologyEvidenceCorpus base = reopenedOntologyEvidenceCorpus(fixture);
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      AnalysisRunRequest r4Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                  store, AnalysisRunId.parse(fixture.r4RunId()))
              .request();
      AnalysisRunId r3RunId =
          r4Request.technicalAnalysisInputs().upstreamPublication().address().runId();
      AnalysisRunRequest r3Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, r3RunId).request();
      AnalysisStepPublicationReference r2Publication =
          r3Request.technicalAnalysisInputs().upstreamPublication();

      CanonicalArtifactPolicyRegistry technicalPolicies =
          SourceAnalysisExecution.loadPolicies(TECHNICAL_POLICY_SET, CANONICAL);
      FileSystemCanonicalAnalysisStepArtifactStore technicalSteps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              store,
              CANONICAL,
              technicalPolicies,
              TechnicalAnalysisConfiguredRuntime.technicalPublicationStoreLimits());
      List<JavaDeclarationCatalog.MethodDeclarationView> declarations =
          new JavaCodeIndexReader(technicalSteps)
              .reopen(new ProgramGraphsReference(r2Publication))
              .catalog()
              .methods();

      CanonicalArtifactPolicyRegistry sourcePolicies =
          SourceAnalysisExecution.loadPolicies(SOURCE_PREPARATION_POLICY_SET, CANONICAL);
      FileSystemCanonicalModuleArtifactStore sourceModules =
          new FileSystemCanonicalModuleArtifactStore(
              store, CANONICAL, sourcePolicies, testStoreLimits());
      FileSystemCanonicalAnalysisStepArtifactStore sourceSteps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              store, CANONICAL, sourcePolicies, testStoreLimits());
      PreparedSourceArchive archive = new PreparedSourceArchive(fixture.archive());
      SourcePreparationReader preparationReader =
          new SourcePreparationReader(sourceModules, sourceSteps, archive);
      VerifiedSourceTextSet sourceTexts =
          new PreparedVerifiedSourceTextReader(preparationReader, archive)
              .reopen(
                  new VerifiedSourceInventoryReference(
                      r4Request.selectedSourceBasis().preparedSource().publication()));
      return base.withPreparedSourceBodies(sourceTexts, declarations);
    }
  }

  private AnalysisStepPublicationReference savedR2Publication(TechnicalFixture fixture)
      throws Exception {
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      AnalysisRunRequest r4Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                  store, AnalysisRunId.parse(fixture.r4RunId()))
              .request();
      AnalysisRunId r3RunId =
          r4Request.technicalAnalysisInputs().upstreamPublication().address().runId();
      AnalysisRunRequest r3Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, r3RunId).request();
      return r3Request.technicalAnalysisInputs().upstreamPublication();
    }
  }

  private static Path technicalStepDirectory(
      Path runStore, AnalysisStepPublicationReference publication) {
    return runStore
        .resolve("runs")
        .resolve(publication.address().runId().value().replace(":", "--"))
        .resolve("steps")
        .resolve(publication.address().analysisStepKey().directoryName());
  }

  private int savedRequestCoverageCount(TechnicalFixture fixture) throws Exception {
    String coverage =
        new String(
            reopenedEvidenceDirectory(fixture).frontendCoverageCanonicalJsonl().copyToByteArray(),
            StandardCharsets.UTF_8);
    int requestRows = 0;
    for (String line : coverage.lines().toList()) {
      if (!line.isBlank()
          && "REQUEST_COVERAGE".equals(JSON.readTree(line).path("recordType").asText())) {
        requestRows++;
      }
    }
    return requestRows;
  }

  private static FrontendSyntaxScan neutralFrontendRequestScan(
      FrontendSyntaxInput input, String sourcePath) {
    VerifiedSourceTextDocument page =
        input.sourceTexts().documents().stream()
            .filter(document -> document.path().equals(sourcePath))
            .findFirst()
            .orElseThrow(() -> new AssertionError("neutral frontend page is absent from R0"));
    String source = new String(page.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    String call = "this.$http.get('/api/records')";
    int callStart = source.indexOf(call);
    if (callStart < 0) {
      throw new AssertionError("neutral frontend request call is absent from saved R0 text");
    }
    FrontendRequestObservation request =
        new FrontendRequestObservation(
            "request:neutral-records",
            sourcePath,
            page.sha256().value(),
            "NeutralRecords#load",
            sourceRange(source, callStart, callStart + call.length()),
            "GET",
            "'/api/records'",
            "/api/records",
            "DIRECT",
            List.of(),
            List.of(),
            null,
            null,
            null);
    return new FrontendSyntaxScan(
        List.of(sourcePath),
        List.of(request),
        List.of(),
        List.of(),
        List.of(
            new FrontendSourceFileDisposition(
                sourcePath, page.sha256().value(), FrontendSourceFileDisposition.Status.PARSED)));
  }

  private JsonNode actualTableR4Entry(TechnicalFixture fixture, String tableName) throws Exception {
    ReopenedModulePublication module = reopenedEvidenceModule(fixture);
    JsonNode index = modulePayload(module, "ENTRY_EVIDENCE_INDEX");
    assertThat(index.path("entries")).hasSize(1);
    String fileName = index.path("entries").get(0).path("file").asText();
    JsonNode entry = modulePayloadFile(module, fileName);
    assertThat(entry.path("entryId").asText()).isNotBlank();
    actualTableSqlAnalysis(entry, tableName);
    return entry;
  }

  private static JsonNode actualTableSqlAnalysis(JsonNode entry, String tableName) {
    for (JsonNode analysis : entry.path("persistence").path("sqlAnalyses")) {
      if ("PARSED".equals(analysis.path("status").asText())
          && containsSqlTableNode(analysis.path("ast"), tableName)) {
        return analysis;
      }
    }
    throw new AssertionError("missing actual parsed R4 TABLE node " + tableName);
  }

  private static boolean containsSqlTableNode(JsonNode node, String tableName) {
    if ("TABLE".equals(node.path("kind").asText())
        && tableName.equals(node.path("value").asText())) {
      return true;
    }
    if (node.isContainerNode()) {
      Iterator<JsonNode> children = node.elements();
      while (children.hasNext()) {
        if (containsSqlTableNode(children.next(), tableName)) {
          return true;
        }
      }
    }
    return false;
  }

  private static JsonNode modulePayload(ReopenedModulePublication module, String artifactType)
      throws Exception {
    return module.payloads().stream()
        .filter(payload -> artifactType.equals(payload.descriptor().artifactType()))
        .findFirst()
        .map(payload -> CANONICAL.parseCanonical(payload.canonicalUtf8()))
        .orElseThrow(() -> new AssertionError("missing installed payload type " + artifactType));
  }

  private static JsonNode modulePayloadFile(ReopenedModulePublication module, String fileName)
      throws Exception {
    return module.payloads().stream()
        .filter(payload -> fileName.equals(payload.descriptor().fileName()))
        .findFirst()
        .map(payload -> CANONICAL.parseCanonical(payload.canonicalUtf8()))
        .orElseThrow(() -> new AssertionError("missing installed payload file " + fileName));
  }

  private static JsonNode schemaFile(JsonNode schemaEvidence, String path) {
    for (JsonNode file : schemaEvidence.path("files")) {
      if (path.equals(file.path("path").asText())) {
        return file;
      }
    }
    throw new AssertionError("missing schema evidence file " + path);
  }

  private static String entryRef(JsonNode corpus) {
    assertThat(corpus.path("aliases").path("entries")).hasSize(1);
    return corpus.path("aliases").path("entries").get(0).path("ref").asText();
  }

  private static String unitRef(JsonNode corpus, String kind, String originalId) {
    List<JsonNode> matches = new ArrayList<>();
    for (JsonNode unit : corpus.path("aliases").path("units")) {
      if (kind.equals(unit.path("kind").asText())
          && originalId.equals(unit.path("originalId").asText())) {
        matches.add(unit);
      }
    }
    assertThat(matches).hasSize(1);
    return matches.get(0).path("ref").asText();
  }

  private static String schemaUnitRef(JsonNode corpus, String schemaFileId) {
    List<JsonNode> matches = schemaUnitAliases(corpus, schemaFileId);
    assertThat(matches)
        .as("schema evidence fileId must name its reversible schema-file unit")
        .hasSize(1);
    return matches.get(0).path("ref").asText();
  }

  private static List<JsonNode> schemaUnitAliases(JsonNode corpus, String schemaFileId) {
    List<JsonNode> matches = new ArrayList<>();
    for (JsonNode unit : corpus.path("aliases").path("units")) {
      if (schemaFileId.equals(unit.path("originalId").asText())) {
        matches.add(unit);
      }
    }
    return List.copyOf(matches);
  }

  private static JsonNode modelInput(StructuredModelRequest request) throws Exception {
    return JSON.readTree(request.untrustedInputJson().copyToByteArray());
  }

  private static JsonNode namedItem(JsonNode array, String field, String value) {
    for (JsonNode item : array) {
      if (value.equals(item.path(field).asText())) {
        return item;
      }
    }
    throw new AssertionError("missing item " + field + "=" + value);
  }

  private ReopenedModulePublication reopenedOntologyModule(Path runStore, String runId)
      throws Exception {
    return reopenedOntologyModule(runStore, runId, ONTOLOGY_POLICY_SET);
  }

  private ReopenedModulePublication reopenedOntologyModule(
      Path runStore, String runId, Path policySetPath) throws Exception {
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunOutput output = new LocalRepositoryAnalysisAgent(store).inspect(runId).output();
      assertThat(output).isNotNull();
      assertThat(output.ontologyOutput()).isNotNull();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(policySetPath, CANONICAL);
      return new FileSystemCanonicalModuleArtifactStore(
              store, CANONICAL, policies, testStoreLimits())
          .reopen(output.ontologyOutput().ontologyPublication());
    }
  }

  private static List<ArtifactReference> modulePayloadReferences(ReopenedModulePublication module) {
    return module.payloads().stream()
        .map(payload -> artifactReference(payload.descriptor()))
        .toList();
  }

  private static ArtifactReference modulePayloadReference(
      ReopenedModulePublication module, String artifactType) {
    return module.payloads().stream()
        .filter(payload -> artifactType.equals(payload.descriptor().artifactType()))
        .map(payload -> artifactReference(payload.descriptor()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing payload type " + artifactType));
  }

  private static ArtifactReference artifactReference(
      org.sourceanalysis.app.artifact.ArtifactDescriptor descriptor) {
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private Path writePolicySetWithout(
      Path originalConfiguration, String artifactType, Path policySetPath) throws Exception {
    ObjectNode policySet =
        (ObjectNode) JSON.readTree(Files.readString(ONTOLOGY_POLICY_SET, StandardCharsets.UTF_8));
    ArrayNode policies = JSON.createArrayNode();
    int removed = 0;
    for (JsonNode policy : policySet.path("policies")) {
      if (artifactType.equals(policy.path("artifactType").asText())) {
        removed++;
      } else {
        policies.add(policy.deepCopy());
      }
    }
    assertThat(removed).isEqualTo(1);
    policySet.set("policies", policies);
    Files.write(policySetPath, JSON.writeValueAsBytes(policySet));

    String originalYaml = Files.readString(originalConfiguration, StandardCharsets.UTF_8);
    assertThat(originalYaml).contains(yaml(ONTOLOGY_POLICY_SET));
    String restrictedYaml = originalYaml.replace(yaml(ONTOLOGY_POLICY_SET), yaml(policySetPath));
    Path restrictedConfiguration =
        originalConfiguration.resolveSibling(
            originalConfiguration.getFileName().toString().replace(".yaml", "-restricted.yaml"));
    Files.writeString(restrictedConfiguration, restrictedYaml, StandardCharsets.UTF_8);
    return restrictedConfiguration;
  }

  private static ArtifactStoreLimits testStoreLimits() {
    return new ArtifactStoreLimits(128, 64L * 1024L * 1024L, 128L * 1024L * 1024L, 4_096);
  }

  private static List<String> formalEntryRefs(JsonNode corpus) {
    return corpus.findValuesAsText("ref").stream()
        .filter(reference -> reference.matches("E[1-9][0-9]*"))
        .toList();
  }

  private static String runId(char hex) {
    return "analysis-run:" + String.valueOf(hex).repeat(64);
  }

  private static String pom(String artifactId) {
    return """
    <project xmlns="http://maven.apache.org/POM/4.0.0">
      <modelVersion>4.0.0</modelVersion>
      <groupId>fixture.ontology</groupId>
      <artifactId>%s</artifactId>
      <version>1.0</version>
      <dependencies>
        <dependency>
          <groupId>org.springframework</groupId>
          <artifactId>spring-webmvc</artifactId>
          <version>6.1.0</version>
        </dependency>
      </dependencies>
    </project>
    """
        .formatted(artifactId);
  }

  private static String effectivePom(String name, Path sourceDirectory) {
    return """
    <project xmlns="http://maven.apache.org/POM/4.0.0">
      <modelVersion>4.0.0</modelVersion>
      <groupId>fixture.ontology</groupId>
      <artifactId>neutral-%s</artifactId>
      <version>1.0</version>
      <dependencies>
        <dependency>
          <groupId>org.springframework</groupId>
          <artifactId>spring-webmvc</artifactId>
          <version>6.1.0</version>
        </dependency>
      </dependencies>
      <build>
        <sourceDirectory>%s</sourceDirectory>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration><release>17</release></configuration>
          </plugin>
        </plugins>
      </build>
    </project>
    """
        .formatted(name, xml(sourceDirectory.toAbsolutePath().normalize().toString()));
  }

  private static String yaml(Path value) {
    return "\""
        + value.toAbsolutePath().normalize().toString().replace("\\", "\\\\").replace("\"", "\\\"")
        + "\"";
  }

  private static String xml(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static SourceRange sourceRange(String source, int start, int end) {
    return new SourceRange(start, end - start, lineAt(source, start), lineAt(source, end - 1));
  }

  private static int lineAt(String source, int offset) {
    return 1
        + (int) source.substring(0, offset).chars().filter(character -> character == '\n').count();
  }

  private record TechnicalFixture(
      Path runStore,
      Path archive,
      String r0RunId,
      String r4RunId,
      AnalysisStepPublicationReference r4Publication) {}

  private record SchemaRuntimeFixture(Map<String, byte[]> schemaFiles, List<String> exclusions) {
    private SchemaRuntimeFixture {
      schemaFiles = Map.copyOf(schemaFiles);
      exclusions = List.copyOf(exclusions);
    }
  }

  private record ConfiguredDiscovery(Path path, Map<String, String> prompts) {}

  private record ConfiguredTypedPipeline(Path path, Map<String, String> prompts) {}

  private record BusinessLinkRuntimeFixture(
      TechnicalFixture technicalFixture,
      ConfiguredTypedPipeline configured,
      String corpusRunId,
      AtomicInteger providerFactories,
      List<StructuredModelRequest> requests,
      AtomicReference<String> rejectedObjectReviewQuestion,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory) {}

  private record ScopedQuestion(String questionId, List<ScopedTask> tasks) {}

  private record ScopedTask(String taskId, String taskKind) {}

  private record SelectedRelateQuestion(
      String questionId, String taskId, List<ObjectSource> objectSources) {
    private SelectedRelateQuestion {
      objectSources = List.copyOf(objectSources);
    }
  }

  private record ObjectSource(String identificationRun, String questionId) {}

  private record CrossRunSameTaskO3Inputs(
      TechnicalFixture fixture,
      ConfiguredTypedPipeline configured,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          providerFactory,
      List<StructuredModelRequest> requests,
      String o0RunId,
      String firstO1RunId,
      String secondO1RunId,
      String firstO2RunId,
      String secondO2RunId) {}

  private record MeasuredUnit(String unitRef, int unitBytes) {}

  private record BlockedAttempt(Path attemptDirectory, byte[] requestBytes) {}

  private record CliResult(int exitCode, String stdout, String stderr) {}

  private record TechResult(int exitCode, String stdout, String stderr) {}

  private static final class ScriptedModel {
    private final Deque<ImmutableBytes> responses;
    private final List<StructuredModelRequest> requests = new ArrayList<>();

    private ScriptedModel(ImmutableBytes... responses) {
      this.responses = new ArrayDeque<>(List.of(responses));
    }

    private StructuredModelProvider provider(ModelRuntimeIdentityV1 identity) {
      return request -> {
        requests.add(request);
        if (responses.isEmpty()) {
          throw new AssertionError("the exact EXPLICIT fixture declares one extract/review pair");
        }
        return new StructuredModelResponse(responses.removeFirst(), identity);
      };
    }
  }

  private static final class MultiO1TypedPipelineScript {
    private static final String UNRESOLVED_ID = ExplicitTypedPipelineScript.RELATION_UNRESOLVED_ID;

    private final Map<String, String> prompts;
    private final List<StructuredModelRequest> requests = new ArrayList<>();

    private MultiO1TypedPipelineScript(Map<String, String> prompts) {
      this.prompts = prompts;
    }

    private StructuredModelProvider provider(ModelRuntimeIdentityV1 identity) {
      return request -> {
        requests.add(request);
        JsonNode input = ExplicitTypedPipelineScript.input(request);
        String kind = input.path("taskKind").asText();
        boolean review = request.taskKind().contains("REVIEW");
        String promptKey = review ? "review" : ExplicitTypedPipelineScript.extractPromptKey(kind);
        assertThat(request.systemInstructions()).isEqualTo(prompts.get(promptKey));
        assertThat(input.path("readingPacket").isObject()).isTrue();
        assertThat(matchingTextValues(input, "E[1-9][0-9]*")).contains("E1");
        assertThat(containsText(input, "return \"neutral\"")).isTrue();
        String schemaVersion = review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3";
        String questionId = input.path("questionId").asText();
        ImmutableBytes response;
        if ("OBJECT".equals(kind)) {
          assertThat(input.path("reviewedCatalog").path("entries")).isEmpty();
          String label = "Q1".equals(questionId) ? "first" : "second";
          response =
              formalTaskObjectResponse(
                  schemaVersion,
                  questionId,
                  "E1",
                  "Neutral " + label + " record shape",
                  "A distinct neutral source-backed record shape for " + label + ".");
        } else if ("RELATE".equals(kind)) {
          List<String> objectRefs =
              textFieldValues(input.path("reviewedCatalog"), "catalogRef").stream()
                  .filter(reference -> reference.matches("B[1-9][0-9]*"))
                  .toList();
          assertThat(objectRefs).containsExactlyInAnyOrder("B1", "B2");
          response = formalTaskUnresolvedRelateResponse(schemaVersion, objectRefs);
        } else {
          throw new AssertionError("unexpected multi-O1 fixture kind " + kind);
        }
        return new StructuredModelResponse(response, identity);
      };
    }

    private static JsonNode input(StructuredModelRequest request) {
      return ExplicitTypedPipelineScript.input(request);
    }
  }

  private static final class ExplicitTypedPipelineScript {
    private static final String RELATION_UNRESOLVED_ID = "UNRESOLVED-RELATION-NOT-ESTABLISHED";

    private final Map<String, String> prompts;
    private final List<StructuredModelRequest> requests = new ArrayList<>();
    private final List<String> providerKeys = new ArrayList<>();

    private ExplicitTypedPipelineScript(Map<String, String> prompts) {
      this.prompts = prompts;
    }

    private StructuredModelProvider provider(
        OntologyTypedTaskRunner.FormalModelDeclaration declaration) {
      providerKeys.add(declaration.key());
      ModelRuntimeIdentityV1 identity = declaration.expectedRuntimeIdentity();
      return request -> {
        requests.add(request);
        JsonNode input = input(request);
        String kind = input.path("taskKind").asText();
        boolean review = request.taskKind().contains("REVIEW");
        String promptKey = review ? "review" : extractPromptKey(kind);
        assertThat(request.systemInstructions()).isEqualTo(prompts.get(promptKey));
        assertThat(input.path("readingPacket").isObject()).isTrue();
        assertThat(matchingTextValues(input, "E[1-9][0-9]*")).contains("E1");
        assertThat(containsText(input, "return \"neutral\"")).isTrue();
        String schemaVersion = review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3";
        String questionRef = input.path("questionId").asText();
        String entryRef = "E1";
        ImmutableBytes response =
            switch (kind) {
              case "OBJECT" -> {
                assertThat(input.path("reviewedCatalog").path("entries")).isEmpty();
                yield formalTaskObjectResponse(schemaVersion, questionRef, entryRef);
              }
              case "ACTION" -> {
                String objectRef = catalogRef(input, "objects");
                yield formalTaskActionResponse(schemaVersion, questionRef, entryRef, objectRef);
              }
              case "ANALYTIC" -> {
                JsonNode object = catalogEntry(input, "objects");
                String objectRef = object.path("catalogRef").asText();
                String propertyRef = object.path("propertyRefs").path(0).asText();
                assertThat(propertyRef).startsWith(objectRef + ".P");
                yield formalTaskAnalyticResponse(
                    schemaVersion, questionRef, entryRef, objectRef, propertyRef);
              }
              case "RELATE" -> {
                String objectRef = catalogRef(input, "objects");
                yield formalTaskUnresolvedRelateResponse(schemaVersion, objectRef);
              }
              default -> throw new AssertionError("unexpected typed task kind " + kind);
            };
        return new StructuredModelResponse(response, identity);
      };
    }

    private JsonNode inputAt(int requestIndex) {
      return input(requests.get(requestIndex));
    }

    private String catalogRefAt(int requestIndex, String definitionType) {
      return catalogRef(inputAt(requestIndex), definitionType);
    }

    private String catalogNameAt(int requestIndex, String definitionType) {
      return catalogEntry(inputAt(requestIndex), definitionType)
          .path("definition")
          .path("name")
          .asText();
    }

    private static String extractPromptKey(String kind) {
      return switch (kind) {
        case "OBJECT" -> "object";
        case "ACTION" -> "action";
        case "ANALYTIC" -> "analytic";
        case "RELATE" -> "relate";
        default -> throw new AssertionError("unexpected typed task kind " + kind);
      };
    }

    private static String catalogRef(JsonNode input, String definitionType) {
      return catalogEntry(input, definitionType).path("catalogRef").asText();
    }

    private static JsonNode catalogEntry(JsonNode input, String definitionType) {
      for (JsonNode entry : input.path("reviewedCatalog").path("entries")) {
        if (definitionType.equals(entry.path("definitionType").asText())) {
          return entry;
        }
      }
      throw new AssertionError("the actual reviewed catalog did not contain " + definitionType);
    }

    private static JsonNode input(StructuredModelRequest request) {
      try {
        return JSON.readTree(request.untrustedInputJson().copyToByteArray());
      } catch (Exception invalidRequest) {
        throw new AssertionError("typed request input is not valid JSON", invalidRequest);
      }
    }
  }

  private static final class FormalDiscoveryScript {
    private final Map<String, String> prompts;
    private final boolean rejectUnknownPriority;
    private final boolean businessLinkV4;
    private final boolean prioritizeAction;
    private final List<StructuredModelRequest> requests = new ArrayList<>();
    private final Set<String> availableQuestionRefs = new LinkedHashSet<>();
    private final String invalidPriorityRef = "Q999";
    private String entryRef;
    private String clueRef;
    private String questionRef;
    private String readUnitRef;
    private int readingRounds;

    private FormalDiscoveryScript(Map<String, String> prompts, boolean rejectUnknownPriority) {
      this(prompts, rejectUnknownPriority, false, false);
    }

    private FormalDiscoveryScript(
        Map<String, String> prompts,
        boolean rejectUnknownPriority,
        boolean businessLinkV4,
        boolean prioritizeAction) {
      this.prompts = prompts;
      this.rejectUnknownPriority = rejectUnknownPriority;
      this.businessLinkV4 = businessLinkV4;
      this.prioritizeAction = prioritizeAction;
    }

    private StructuredModelProvider provider(ModelRuntimeIdentityV1 identity) {
      return request -> {
        requests.add(request);
        JsonNode input = input(request);
        String schemaVersion = input.path("schemaVersion").asText();
        if ("ontology-survey-input-v3".equals(schemaVersion)) {
          assertThat(request.systemInstructions()).isEqualTo(prompts.get("survey"));
          entryRef = firstMatchingRef(input, "E[1-9][0-9]*");
          clueRef = firstMatchingRef(input, "K[1-9][0-9]*");
          assertThat(matchingTextValues(input, "U[1-9][0-9]*")).isNotEmpty();
          assertThat(hasField(input, "viewId")).isFalse();
          assertThat(hasField(input, "corpusIdentity")).isFalse();
          assertThat(hasField(input, "taskId")).isFalse();
          assertThat(containsText(input, "return \"neutral\"")).isFalse();
          return new StructuredModelResponse(formalSurveyResponse(entryRef, clueRef), identity);
        }
        if ("ontology-prioritize-input-v4".equals(schemaVersion)) {
          assertThat(request.systemInstructions()).isEqualTo(prompts.get("prioritize"));
          availableQuestionRefs.addAll(textFieldValues(input, "questionRef"));
          assertThat(availableQuestionRefs).hasSize(1);
          questionRef = availableQuestionRefs.iterator().next();
          assertThat(matchingTextValues(input, "E[1-9][0-9]*")).contains(entryRef);
          assertThat(matchingTextValues(input, "K[1-9][0-9]*")).contains(clueRef);
          assertThat(containsText(input, "return \"neutral\"")).isFalse();
          String selectedRef = rejectUnknownPriority ? invalidPriorityRef : questionRef;
          return new StructuredModelResponse(
              formalPrioritizeResponse(
                  selectedRef, prioritizeAction ? List.of("OBJECT", "ACTION") : List.of("OBJECT")),
              identity);
        }
        if ("ontology-reading-input-v3".equals(schemaVersion)
            || (businessLinkV4 && "ontology-reading-input-v4".equals(schemaVersion))) {
          assertThat(request.systemInstructions()).isEqualTo(prompts.get("reading"));
          readingRounds++;
          if (readingRounds == 1) {
            assertThat(containsText(input, "return \"neutral\"")).isFalse();
            JsonNode available = input.path("visibleScope").path("availableUnitUses");
            assertThat(available.isArray()).isTrue();
            for (JsonNode use : available) {
              if (entryRef.equals(use.path("entryRef").asText())) {
                readUnitRef = use.path("unitRef").asText();
                break;
              }
            }
            assertThat(readUnitRef).matches("U[1-9][0-9]*");
            if (businessLinkV4) {
              return new StructuredModelResponse(
                  businessLinkFormalReadingResponseV4(
                      "NEEDS_MORE_MATERIAL", readUnitRef, entryRef, false),
                  identity);
            }
            return new StructuredModelResponse(
                formalReadingResponse("NEEDS_MORE_MATERIAL", readUnitRef, entryRef, false),
                identity);
          }
          assertThat(readingRounds).isEqualTo(2);
          assertThat(containsText(input, "return \"neutral\"")).isTrue();
          if (businessLinkV4) {
            return new StructuredModelResponse(
                businessLinkFormalReadingResponseV4(
                    "READY_TO_EXTRACT", readUnitRef, entryRef, true),
                identity);
          }
          return new StructuredModelResponse(
              formalReadingResponse("READY_TO_EXTRACT", readUnitRef, entryRef, true), identity);
        }
        if (businessLinkV4 && request.taskKind().contains("EXTRACT")) {
          String taskKind = input.path("taskKind").asText();
          String currentQuestion = input.path("questionId").asText();
          String currentEntry = firstMatchingRef(input, "E[1-9][0-9]*");
          if ("ACTION".equals(taskKind)) {
            assertThat(request.systemInstructions()).isEqualTo(prompts.get("action"));
            return new StructuredModelResponse(
                businessLinkFormalActionResponseV4(
                    "ontology-typed-candidate-v4",
                    currentQuestion,
                    currentEntry,
                    ExplicitTypedPipelineScript.catalogRef(input, "objects")),
                identity);
          }
          assertThat(request.systemInstructions()).isEqualTo(prompts.get("object"));
          return new StructuredModelResponse(
              businessLinkFormalObjectResponseV4(
                  "ontology-typed-candidate-v4", currentQuestion, currentEntry),
              identity);
        }
        if (businessLinkV4 && request.taskKind().contains("REVIEW")) {
          String taskKind = input.path("taskKind").asText();
          String currentQuestion = input.path("questionId").asText();
          String currentEntry = firstMatchingRef(input, "E[1-9][0-9]*");
          if ("ACTION".equals(taskKind)) {
            assertThat(request.systemInstructions()).isEqualTo(prompts.get("action"));
            return new StructuredModelResponse(
                businessLinkFormalActionResponseV4(
                    "ontology-typed-review-v4",
                    currentQuestion,
                    currentEntry,
                    ExplicitTypedPipelineScript.catalogRef(input, "objects")),
                identity);
          }
          assertThat(request.systemInstructions()).isEqualTo(prompts.get("review"));
          return new StructuredModelResponse(
              businessLinkFormalObjectResponseV4(
                  "ontology-typed-review-v4", currentQuestion, currentEntry),
              identity);
        }
        if (request.taskKind().contains("EXTRACT")) {
          assertThat(request.systemInstructions()).isEqualTo(prompts.get("object"));
          assertThat(containsText(input, "return \"neutral\"")).isTrue();
          try {
            return new StructuredModelResponse(
                formalObjectResponse("ontology-typed-candidate-v3", questionRef, entryRef),
                identity);
          } catch (Exception invalidFixture) {
            throw new AssertionError("could not build neutral typed candidate", invalidFixture);
          }
        }
        if (request.taskKind().contains("REVIEW")) {
          assertThat(request.systemInstructions()).isEqualTo(prompts.get("review"));
          assertThat(containsText(input, "return \"neutral\"")).isTrue();
          try {
            return new StructuredModelResponse(
                formalObjectResponse("ontology-typed-review-v3", questionRef, entryRef), identity);
          } catch (Exception invalidFixture) {
            throw new AssertionError("could not build neutral typed review", invalidFixture);
          }
        }
        throw new AssertionError("unexpected formal discovery request: " + request.taskKind());
      };
    }

    private JsonNode inputAt(int index) {
      return input(requests.get(index));
    }

    private List<String> shortRefs(JsonNode input, String pattern) {
      return matchingTextValues(input, pattern);
    }

    private static JsonNode input(StructuredModelRequest request) {
      try {
        return JSON.readTree(request.untrustedInputJson().copyToByteArray());
      } catch (Exception invalidRequest) {
        throw new AssertionError("scripted request input is not valid JSON", invalidRequest);
      }
    }

    private static String firstMatchingRef(JsonNode input, String pattern) {
      return matchingTextValues(input, pattern).stream()
          .findFirst()
          .orElseThrow(
              () -> new AssertionError("formal request did not display a " + pattern + " ref"));
    }
  }
}
