package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.runtime.AnalysisRunRequest.OntologyOperation;

/**
 * Direct contracts for the standalone ontology-config-v1 reader.
 *
 * <p>Schema-source paths are relative to the verified R0 bound later by the operation, never to the
 * configuration storage root.
 */
class OntologyConfigurationTest {

  @TempDir Path temporaryDirectory;

  @Test
  void omittedModelJobsLoadForPrepareAndPublishWithoutProviderOrAuthentication() throws Exception {
    OntologyConfiguration configuration = load(baseYaml("", "", "", ""));

    assertThat(configuration.models()).isNull();
    configuration.requireModels(OntologyOperation.PREPARE_ONTOLOGY);
    configuration.requireModels(OntologyOperation.PUBLISH_ONTOLOGY);
    assertThatThrownBy(() -> configuration.requireModels(OntologyOperation.IDENTIFY_ONTOLOGY))
        .hasMessageContaining("ONTOLOGY_MODEL");
    assertThatThrownBy(() -> configuration.requireModels(OntologyOperation.RELATE_ONTOLOGY))
        .hasMessageContaining("ONTOLOGY_MODEL");
  }

  @Test
  void ontologyConfigV1UsesClosedFieldsAndIndependentPositiveReadingUnits() throws Exception {
    OntologyConfiguration configuration = load(baseYaml("", "", "", ""));

    assertThat(configuration.reading().maxUnitBytes()).isEqualTo(100_001);
    assertThat(configuration.reading().maxRequestBytes()).isEqualTo(200_002);
    assertThat(configuration.reading().maxOutputBytes()).isEqualTo(300_003);
    assertThat(configuration.reading().maxOutputTokens()).isEqualTo(4_004);
    assertThat(configuration.reading().maxRequests()).isEqualTo(5_005);
    assertThat(configuration.reading().maxReadingRounds()).isEqualTo(6_006);
    assertThat(configuration.reading().maxActionsPerRound()).isEqualTo(7_007);
    assertThat(configuration.reading().maxNavigationEntries()).isEqualTo(8_008);
    assertThat(canonical(configuration)).contains("ontology-config-v1");

    assertThatThrownBy(() -> load(baseYaml("", "", "", "unexpectedTopLevel: true\n")))
        .hasMessageContaining("CONFIGURATION_INVALID");
    assertThatThrownBy(
            () ->
                load(
                    baseYaml("", "", "", "")
                        .replace(
                            "  maxUnitBytes: 100001",
                            "  maxUnitBytes: 100001\n  unknownBudget: 1")))
        .hasMessageContaining("CONFIGURATION_INVALID");
    assertThatThrownBy(
            () ->
                load(
                    baseYaml("", "", "", "")
                        .replace("  maxUnitBytes: 100001", "  maxUnitBytes: 0")))
        .hasMessageContaining("CONFIGURATION_INVALID");
  }

  @Test
  void storagePoliciesRemainDistinctEvenWhenAllAreExplicitAbsolutePaths() throws Exception {
    OntologyConfiguration configuration = load(baseYaml("", "", "", ""));

    assertThat(configuration.storage().root()).isAbsolute();
    assertThat(configuration.storage().preparedSourceArchive())
        .isNotEqualTo(configuration.storage().root());
    assertThat(configuration.storage().sourcePreparationPolicyRegistry())
        .isNotEqualTo(configuration.storage().evidencePolicyRegistry());
    assertThat(configuration.storage().evidencePolicyRegistry())
        .isNotEqualTo(configuration.storage().ontologyPolicyRegistry());
    assertThat(configuration.storage().sourcePreparationPolicyRegistry())
        .isNotEqualTo(configuration.storage().ontologyPolicyRegistry());
    assertThat(canonical(configuration))
        .contains(
            "preparedSourceArchive",
            "sourcePreparationPolicyRegistry",
            "evidencePolicyRegistry",
            "ontologyPolicyRegistry");
  }

  @Test
  void schemaSourcesAreOptionalAndRelativeToTheVerifiedR0() throws Exception {
    Path source = temporaryDirectory.resolve("source-root").resolve("ddl/schema.sql");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "create table orders(id bigint);", StandardCharsets.UTF_8);

    OntologyConfiguration empty = load(baseYaml("schemaSources: []\n", "", "", ""));
    OntologyConfiguration configured =
        load(baseYaml("schemaSources:\n  - ddl/schema.sql\n", "", "", ""));
    assertThat(empty).isNotNull();
    assertThat(configured).isNotNull();

    assertThatThrownBy(() -> load(baseYaml("schemaSources:\n  - /host/absolute.sql\n", "", "", "")))
        .hasMessageContaining("CONFIGURATION_INVALID");
    assertThatThrownBy(() -> load(baseYaml("schemaSources:\n  - ../outside.sql\n", "", "", "")))
        .hasMessageContaining("CONFIGURATION_INVALID");
    assertThatThrownBy(
            () ->
                load(
                    baseYaml(
                        "schemaSources:\n  - ddl/schema.sql\n  - ddl/schema.sql\n", "", "", "")))
        .hasMessageContaining("CONFIGURATION_INVALID");
  }

  @Test
  void promptOverridesBindActualUtf8ContentAndOldSnapshotStaysStable() throws Exception {
    Path prompt = temporaryDirectory.resolve("object.prompt");
    Files.writeString(prompt, "对象识别：逐字保留 UTF-8。", StandardCharsets.UTF_8);

    OntologyConfiguration first = load(baseYaml("", "object: '" + quote(prompt) + "'\n", "", ""));
    String firstCanonical = canonical(first);
    assertThat(first.prompts().get("object")).isEqualTo("对象识别：逐字保留 UTF-8。");

    Files.writeString(prompt, "对象识别：内容已经改变。", StandardCharsets.UTF_8);
    OntologyConfiguration second = load(baseYaml("", "object: '" + quote(prompt) + "'\n", "", ""));

    assertThat(first.prompts().get("object")).isEqualTo("对象识别：逐字保留 UTF-8。");
    assertThat(second.prompts().get("object")).isEqualTo("对象识别：内容已经改变。");
    assertThat(canonical(second)).isNotEqualTo(firstCanonical);

    assertThatThrownBy(() -> load(baseYaml("", "unknownPrompt: '" + quote(prompt) + "'\n", "", "")))
        .hasMessageContaining("CONFIGURATION_INVALID");
  }

  @Test
  void modelRoutingAcceptsOnlySurveyExtractRelateAndNeverFallsBackToLegacyRoutes()
      throws Exception {
    String modelJobs =
        "modelJobs:\n"
            + "  maxConcurrentJobs: 2\n"
            + "  providers:\n"
            + "    pro:\n"
            + "      kind: codexSubscription\n"
            + "      quotaScope: ontology-test-account\n"
            + "      model: gpt-5.6-luna\n"
            + "      reasoningEffort: high\n"
            + "      auth:\n"
            + "        mode: chatgpt\n"
            + "        codexHomeEnv: ONTOLOGY_CONFIG_TEST_MUST_NOT_BE_READ\n"
            + "  routing:\n"
            + "    survey: [pro]\n"
            + "    extract: [pro]\n"
            + "    relate: [pro]\n";
    OntologyConfiguration configuration = load(baseYaml("", "", modelJobs, ""));

    assertThat(configuration.models()).isNotNull();
    assertThat(configuration.models().routing()).containsOnlyKeys("survey", "extract", "relate");
    assertThat(configuration.models().routing().get("survey")).containsExactly("pro");
    assertThat(configuration.models().routing().get("extract")).containsExactly("pro");
    assertThat(configuration.models().routing().get("relate")).containsExactly("pro");
    configuration.requireModels(OntologyOperation.IDENTIFY_ONTOLOGY);
    configuration.requireModels(OntologyOperation.RELATE_ONTOLOGY);

    assertThatThrownBy(() -> load(baseYaml("", "", modelJobs + "    activity: [pro]\n", "")))
        .hasMessageContaining("CONFIGURATION_INVALID");
  }

  @Test
  void portableOntologyExampleLoadsAsFormalDeclarationWithoutAuthenticatingOrCreatingAProvider() {
    Path workspace = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    Path example = workspace.resolve("tools/repository-run/ontology.example.yaml").normalize();
    assertThat(example).isAbsolute();

    OntologyConfiguration configuration = OntologyConfiguration.load(example);

    assertThat(canonical(configuration)).contains("ontology-config-v1");
    assertThat(configuration.storage().root())
        .isEqualTo(Path.of("/absolute/path/to/ontology/run-store"));
    assertThat(configuration.storage().preparedSourceArchive())
        .isEqualTo(Path.of("/absolute/path/to/prepared-source-archive"));
    assertThat(configuration.storage().ontologyPolicyRegistry())
        .isEqualTo(
            Path.of(
                "/absolute/path/to/source-code/tools/repository-run/"
                    + "ontology-artifact-policy-set-v2.json"));
    assertThat(configuration.storage().upstreamArtifactPolicyRegistries())
        .containsExactly(
            Path.of(
                "/absolute/path/to/source-code/tools/repository-run/"
                    + "ontology-artifact-policy-set-v1.json"));
    assertThat(configuration.models()).isNotNull();
    assertThat(configuration.models().routing()).containsOnlyKeys("survey", "extract", "relate");
    assertThat(configuration.models().routing().get("survey")).containsExactly("luna");
    assertThat(configuration.models().routing().get("extract")).containsExactly("luna");
    assertThat(configuration.models().routing().get("relate")).containsExactly("luna");

    ModelJobProviderConfiguration luna = configuration.models().providers().get("luna");
    assertThat(luna).isNotNull();
    assertThat(luna.kind()).isEqualTo(ModelProviderKind.CODEX_SUBSCRIPTION);
    assertThat(luna.model()).isEqualTo("gpt-5.6-luna");
    assertThat(luna.reasoningEffort()).isEqualTo("high");
    assertThat(luna.authentication().mode()).isEqualTo("chatgpt");
    assertThat(luna.authentication().environmentNames()).containsExactly("CODEX_HOME");

    assertThat(configuration.reading().maxUnitBytes()).isPositive();
    assertThat(configuration.reading().maxRequestBytes()).isPositive();
    assertThat(configuration.reading().maxOutputBytes()).isPositive();
    assertThat(configuration.reading().maxOutputTokens()).isPositive();
    assertThat(configuration.reading().maxRequests()).isPositive();
    assertThat(configuration.reading().maxReadingRounds()).isPositive();
    assertThat(configuration.reading().maxActionsPerRound()).isPositive();
    assertThat(configuration.reading().maxNavigationEntries()).isPositive();

    configuration.requireModels(OntologyOperation.PREPARE_ONTOLOGY);
    configuration.requireModels(OntologyOperation.PUBLISH_ONTOLOGY);
  }

  private OntologyConfiguration load(String yaml) throws Exception {
    Path config = temporaryDirectory.resolve("ontology-config.yaml");
    Files.writeString(config, yaml, StandardCharsets.UTF_8);
    return OntologyConfiguration.load(config);
  }

  private String baseYaml(
      String schemaSources, String prompts, String modelJobs, String trailingFields)
      throws Exception {
    Path root = temporaryDirectory.resolve("source-root");
    Files.createDirectories(root);
    Path prepared = temporaryDirectory.resolve("prepared-source");
    Path sourcePolicy = temporaryDirectory.resolve("source-policy.json");
    Path evidencePolicy = temporaryDirectory.resolve("evidence-policy.json");
    Path ontologyPolicy = temporaryDirectory.resolve("ontology-policy.json");
    return "schemaVersion: ontology-config-v1\n"
        + "storage:\n"
        + "  root: '"
        + quote(root)
        + "'\n"
        + "  preparedSourceArchive: '"
        + quote(prepared)
        + "'\n"
        + "  sourcePreparationPolicyRegistry: '"
        + quote(sourcePolicy)
        + "'\n"
        + "  evidencePolicyRegistry: '"
        + quote(evidencePolicy)
        + "'\n"
        + "  ontologyPolicyRegistry: '"
        + quote(ontologyPolicy)
        + "'\n"
        + "reading:\n"
        + "  maxUnitBytes: 100001\n"
        + "  maxRequestBytes: 200002\n"
        + "  maxOutputBytes: 300003\n"
        + "  maxOutputTokens: 4004\n"
        + "  maxRequests: 5005\n"
        + "  maxReadingRounds: 6006\n"
        + "  maxActionsPerRound: 7007\n"
        + "  maxNavigationEntries: 8008\n"
        + schemaSources
        + (prompts.isBlank() ? "" : "prompts:\n  " + prompts)
        + modelJobs
        + trailingFields;
  }

  private static String canonical(OntologyConfiguration configuration) {
    return new String(
        configuration.canonicalConfiguration().copyToByteArray(), StandardCharsets.UTF_8);
  }

  private static String quote(Path path) {
    return path.toAbsolutePath().toString().replace("'", "''");
  }
}
