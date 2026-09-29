package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.cli.SourceAnalysisTestPolicyRegistry;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaCompilationEnvironment;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.JavaReadinessPreparation;
import org.sourceanalysis.app.analysis.inventory.PreparedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.SelectedSourceBasisProjector;

/**
 * Failsafe-only navigation against a saved R0 and externally prepared Maven/JDK inputs.
 *
 * <p>The test never walks a checkout, invokes Maven, or manufactures a source snapshot. Every
 * source byte is reopened from the configured prepared-source archive and every compiler fact is
 * read by {@link JavaReadinessPreparation} from the named external handoff.
 */
class JdtFixedSourceNavigationIT {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String RUN_STORE = "sourceanalysis.jdt.testRunStore";
  private static final String PREPARED_SOURCE_ARCHIVE =
      "sourceanalysis.jdt.testPreparedSourceArchive";
  private static final String SOURCE_PREPARATION_POLICY =
      "sourceanalysis.jdt.testSourcePreparationPolicy";
  private static final String SOURCE_RUN = "sourceanalysis.jdt.testSourcePreparationRunId";
  private static final String COMPILATION_INPUT = "sourceanalysis.jdt.testCompilationInput";
  private static final String PROJECT_DIRECTORY = "sourceanalysis.jdt.testProjectDirectory";
  private static final String TOOL_JAVA_HOME = "sourceanalysis.jdt.testJavaHome";
  private static final String JDT_DISTRIBUTION = "sourceanalysis.jdt.testDistribution";

  @Test
  void fixedR0Jdk8NavigationKeepsNestedAndMapperOwnership() throws Exception {
    FixedInputs inputs = fixedInputs();
    try (RunStoreHandle store = RunStoreBootstrap.open(inputs.runStore())) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      var policies = SourceAnalysisTestPolicyRegistry.load(inputs.sourcePolicy(), json);
      ArtifactStoreLimits limits =
          new ArtifactStoreLimits(256, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, limits);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, limits);
      PreparedSourceArchive archive = new PreparedSourceArchive(inputs.sourceArchive());
      SourcePreparationReader preparationReader =
          new SourcePreparationReader(modules, steps, archive);

      AnalysisRunOutput sourceOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, inputs.sourceRun()).orElseThrow();
      assertThat(sourceOutput.sourcePreparationCheckpoint()).isNotNull();
      SavedSourcePreparation saved =
          preparationReader.reopen(sourceOutput.sourcePreparationCheckpoint());
      SelectedSourceBasis sourceBasis = SelectedSourceBasisProjector.fromPrepared(saved);
      assertThat(sourceOutput.selectedSourceBasis()).isEqualTo(sourceBasis);
      VerifiedSourceInventoryReference sourceReference =
          new VerifiedSourceInventoryReference(sourceOutput.sourcePreparationCheckpoint());
      VerifiedSourceTextSet sourceTexts =
          new PreparedVerifiedSourceTextReader(preparationReader, archive).reopen(sourceReference);

      JavaReadinessPreparation.Result readiness =
          new JavaReadinessPreparation()
              .prepare(
                  new JavaReadinessPreparation.V2Request(
                      sourceTexts, sourceBasis, inputs.compilationInput()));
      assertThat(readiness.status()).isEqualTo(JavaReadinessPreparation.Status.READY);
      JavaCompilationEnvironment environment = readiness.environment();
      assertThat(environment).isNotNull();
      assertThat(environment.sourceSnapshotId()).isEqualTo(sourceTexts.snapshotId());
      assertThat(environment.modules()).isNotEmpty();
      assertThat(environment.modules())
          .allSatisfy(
              module -> {
                assertThat(module.targetPlatform().targetJdkVersion()).isEqualTo("8");
                assertThat(module.compilationTarget().sourceLevel()).isEqualTo("8");
              });

      EffectiveEngineConfiguration configuration =
          new EffectiveEngineConfiguration(
              EffectiveEngineConfiguration.JDT,
              new EffectiveEngineConfiguration.JdtConfiguration(
                  inputs.jdtDistribution(),
                  inputs.toolJavaHome(),
                  Duration.ofSeconds(180),
                  Duration.ofSeconds(60),
                  Duration.ofSeconds(15)));
      try (JavaCodeSession session = new JdtCodeEngine(configuration).open(environment)) {
        JavaDeclarationCatalog catalog = session.catalog();
        JavaDeclarationCatalog.MethodDeclarationView accountList =
            method(catalog, "AccountController", "getList", "search");
        JavaDeclarationCatalog.MethodDeclarationView financialBillNo =
            method(catalog, "AccountHeadController", "getFinancialBillNoByBillId");

        EntryCodeContext listContext =
            session.collect(
                new EntrySeed(
                    "entry:fixed-account-list",
                    accountList.methodKey(),
                    accountList.sourceRange(),
                    "HTTP GET"));
        EntryCodeContext financialContext =
            session.collect(
                new EntrySeed(
                    "entry:fixed-financial-bill-no",
                    financialBillNo.methodKey(),
                    financialBillNo.sourceRange(),
                    "HTTP GET"));

        EntryCodeContext.CallSite outerPageSizeCall =
            listContext.calls().stream()
                .filter(call -> call.expression().startsWith("pageDomain.setPageSize("))
                .findFirst()
                .orElseThrow();
        EntryCodeContext.CallSite innerConvertCall =
            listContext.calls().stream()
                .filter(call -> call.expression().startsWith("Convert.toInt("))
                .findFirst()
                .orElseThrow();
        assertThat(outerPageSizeCall.expression()).contains("Convert.toInt(");
        assertThat(outerPageSizeCall.targets())
            .anySatisfy(target -> assertThat(target.displayName()).contains("setPageSize"));
        assertThat(outerPageSizeCall.targets())
            .noneMatch(target -> target.displayName().contains("Convert.toInt"));
        assertThat(innerConvertCall.targets())
            .anySatisfy(target -> assertThat(target.displayName()).contains("Convert.toInt"));
        assertThat(innerConvertCall.targets())
            .noneMatch(target -> target.displayName().contains("setPageSize"));

        EntryCodeContext.CallSite mapperCall =
            financialContext.calls().stream()
                .filter(
                    call ->
                        call.expression()
                            .contains("accountHeadMapperEx.getFinancialBillNoByBillId"))
                .findFirst()
                .orElseThrow();
        assertThat(mapperCall.targets()).isNotEmpty();
        assertThat(mapperCall.targets())
            .anySatisfy(target -> assertThat(target.displayName()).contains("AccountHeadMapperEx"));

        EntryCodeContext.CallSite loggerCall =
            financialContext.calls().stream()
                .filter(call -> call.expression().startsWith("logger.error("))
                .findFirst()
                .orElseThrow();
        assertThat(loggerCall.targets())
            .noneMatch(target -> target.displayName().contains("AjaxResult.error"));

        JavaDeclarationCatalog.MethodDeclarationView addAccountHead =
            method(catalog, "AccountHeadController", "addAccountHeadAndDetail");
        EntryCodeContext addAccountHeadContext =
            session.collect(
                new EntrySeed(
                    "entry:fixed-account-head-add",
                    addAccountHead.methodKey(),
                    addAccountHead.sourceRange(),
                    "HTTP POST"));
        EntryCodeContext.CallSite billNoCall =
            addAccountHeadContext.calls().stream()
                .filter(call -> call.expression().contains("accountHead.getBillNo("))
                .findFirst()
                .orElseThrow();
        assertThat(billNoCall.targets()).isNotEmpty();
        assertThat(billNoCall.targets())
            .anySatisfy(target -> assertThat(target.displayName()).contains("AccountHead"));
        assertThat(billNoCall.targets())
            .noneMatch(target -> target.displayName().contains("GeneratedCriteria"));

        // These seven historical R1 failures are deliberately bounded to one session. A failure
        // is accepted only as the known JDT query failure; a separate named-session recheck is a
        // manual follow-up and is never hidden as an automatic retry here.
        List<EntrySeed> historicalFailedEntries =
            List.of(
                seed(
                    catalog,
                    "entry:historical-material-batch-set-status",
                    "MaterialController",
                    "batchSetStatus"),
                seed(
                    catalog,
                    "entry:historical-material-delete",
                    "MaterialController",
                    "deleteResource"),
                seed(
                    catalog,
                    "entry:historical-material-batch-update",
                    "MaterialController",
                    "batchUpdate"),
                seed(
                    catalog,
                    "entry:historical-material-import",
                    "MaterialController",
                    "importExcel"),
                seed(
                    catalog,
                    "entry:historical-material-name-check",
                    "MaterialController",
                    "checkIsNameExist"),
                seed(
                    catalog,
                    "entry:historical-material-delete-batch",
                    "MaterialController",
                    "batchDeleteResource"),
                seed(
                    catalog,
                    "entry:historical-depot-statistics",
                    "DepotHeadController",
                    "getBuyAndSaleStatistics"));
        List<String> recheckTimings = new ArrayList<>();
        List<String> recheckFailures = new ArrayList<>();
        for (EntrySeed failedEntry : historicalFailedEntries) {
          long startedAt = System.nanoTime();
          try {
            EntryCodeContext context = session.collect(failedEntry);
            assertThat(context.entryId()).isEqualTo(failedEntry.entryId());
            assertThat(context.methods()).isNotEmpty();
            if ("entry:historical-depot-statistics".equals(failedEntry.entryId())) {
              EntryCodeContext.CallSite depotMapperCall =
                  context.calls().stream()
                      .filter(
                          call ->
                              call.expression()
                                  .contains("depotHeadMapperEx.getBuyAndSaleStatisticsList"))
                      .findFirst()
                      .orElseThrow();
              assertThat(depotMapperCall.targets()).isNotEmpty();
              assertThat(depotMapperCall.targets())
                  .allSatisfy(
                      target -> assertThat(target.displayName()).contains("DepotHeadMapperEx"));
            }
          } catch (CodeEngineException failure) {
            assertThat(failure.code()).isEqualTo(CodeEngineException.JDT_QUERY_FAILED);
            recheckFailures.add(failedEntry.entryId() + ": " + failure.getMessage());
          } finally {
            recheckTimings.add(
                failedEntry.entryId()
                    + "="
                    + Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
                    + "ms");
          }
        }
        assertThat(recheckTimings).hasSize(historicalFailedEntries.size());
        System.out.println(
            "JdtFixedSourceNavigationIT directCases=nested,accountHeadBillNo,depotMapper");
        System.out.println("JdtFixedSourceNavigationIT historicalRecheckTimings=" + recheckTimings);
        System.out.println(
            "JdtFixedSourceNavigationIT historicalRecheckFailures=" + recheckFailures);
        assertThat(recheckFailures)
            .withFailMessage(
                "historical seven-entry same-session recheck exceeded one JDT failure; "
                    + "timings=%s failures=%s",
                recheckTimings, recheckFailures)
            .size()
            .isLessThanOrEqualTo(1);
      }
    }
  }

  private static EntrySeed seed(
      JavaDeclarationCatalog catalog, String entryId, String typeSuffix, String methodName) {
    JavaDeclarationCatalog.MethodDeclarationView declaration =
        method(catalog, typeSuffix, methodName);
    return new EntrySeed(entryId, declaration.methodKey(), declaration.sourceRange(), "HTTP");
  }

  private static JavaDeclarationCatalog.MethodDeclarationView method(
      JavaDeclarationCatalog catalog, String typeSuffix, String name) {
    return method(catalog, typeSuffix, name, null);
  }

  private static JavaDeclarationCatalog.MethodDeclarationView method(
      JavaDeclarationCatalog catalog, String typeSuffix, String name, String requiredParameter) {
    return catalog.methods().stream()
        .filter(candidate -> candidate.declaringType().endsWith(typeSuffix))
        .filter(candidate -> candidate.name().equals(name))
        .filter(
            candidate ->
                requiredParameter == null
                    || candidate.parameters().stream()
                        .anyMatch(parameter -> parameter.name().equals(requiredParameter)))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "fixed R0 catalog is missing " + typeSuffix + "#" + name));
  }

  private static FixedInputs fixedInputs() throws IOException {
    Path runStore = requiredPath(RUN_STORE, true);
    Path sourceArchive = requiredPath(PREPARED_SOURCE_ARCHIVE, true);
    Path sourcePolicy = requiredPath(SOURCE_PREPARATION_POLICY, false);
    Path compilationInput = requiredPath(COMPILATION_INPUT, false);
    Path projectDirectory = requiredPath(PROJECT_DIRECTORY, true);
    Path toolJavaHome = requiredPath(TOOL_JAVA_HOME, true);
    Path jdtDistribution = requiredPath(JDT_DISTRIBUTION, true);
    AnalysisRunId sourceRun = AnalysisRunId.parse(requiredProperty(SOURCE_RUN));
    JavaReadinessPreparation.CompilationInput input =
        readCompilationInput(compilationInput, projectDirectory);
    return new FixedInputs(
        runStore, sourceArchive, sourcePolicy, sourceRun, input, toolJavaHome, jdtDistribution);
  }

  private static JavaReadinessPreparation.CompilationInput readCompilationInput(
      Path inputFile, Path projectDirectory) throws IOException {
    JsonNode root = JSON.readTree(inputFile.toFile());
    JsonNode modules = root.path("modules");
    if (!modules.isArray() || modules.isEmpty()) {
      throw new IllegalStateException("fixed R0 compilation input has no modules");
    }
    List<JavaReadinessPreparation.ModuleInput> configured =
        java.util.stream.StreamSupport.stream(modules.spliterator(), false)
            .map(
                module ->
                    new JavaReadinessPreparation.ModuleInput(
                        text(module, "modulePath"),
                        absoluteText(module, "classpathFile"),
                        text(module, "classpathSeparator"),
                        absoluteText(module, "effectivePomFile"),
                        absoluteText(module, "targetJdkHome")))
            .toList();
    return new JavaReadinessPreparation.CompilationInput(projectDirectory, configured);
  }

  private static String text(JsonNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalStateException("fixed R0 compilation input is missing " + field);
    }
    return value.textValue();
  }

  private static Path absoluteText(JsonNode parent, String field) {
    Path value = Path.of(text(parent, field)).toAbsolutePath().normalize();
    if (!value.isAbsolute()) {
      throw new IllegalStateException("fixed R0 compilation input path is not absolute: " + field);
    }
    return value;
  }

  private static Path requiredPath(String property, boolean directory) {
    Path value = Path.of(requiredProperty(property)).toAbsolutePath().normalize();
    if (directory ? !Files.isDirectory(value) : !Files.isRegularFile(value)) {
      throw new IllegalStateException(
          "Missing required fixed-source-jdt-it prerequisite: " + property + " (" + value + ")");
    }
    return value;
  }

  private static String requiredProperty(String property) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Missing required fixed-source-jdt-it prerequisite: " + property);
    }
    return value;
  }

  private record FixedInputs(
      Path runStore,
      Path sourceArchive,
      Path sourcePolicy,
      AnalysisRunId sourceRun,
      JavaReadinessPreparation.CompilationInput compilationInput,
      Path toolJavaHome,
      Path jdtDistribution) {

    private FixedInputs {
      Objects.requireNonNull(runStore);
      Objects.requireNonNull(sourceArchive);
      Objects.requireNonNull(sourcePolicy);
      Objects.requireNonNull(sourceRun);
      Objects.requireNonNull(compilationInput);
      Objects.requireNonNull(toolJavaHome);
      Objects.requireNonNull(jdtDistribution);
    }
  }
}
