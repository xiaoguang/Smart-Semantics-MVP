package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;

/** Direct process-entry contracts for the lightweight source-preparation CLI. */
class SourcePreparationCliTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path POLICY_SET =
      Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
          .toAbsolutePath();

  @TempDir Path temporaryDirectory;

  @Test
  void directoryConfigNeedsNoModelOrJdtFieldsAndFailedReportRemainsInspectable() throws Exception {
    Path sourceRoot = Files.createDirectory(physicalTemporaryDirectory().resolve("source"));
    Path config = writeConfig(sourceRoot);

    CliResult prepared = execute(config, "prepare-source", "--format", "json");

    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isEqualTo(3);
    JsonNode envelope = JSON.readTree(prepared.stdout());
    assertThat(envelope.path("runId").asText()).startsWith("analysis-run:");
    assertThat(envelope.path("persistenceStatus").asText()).isEqualTo("SAVED");
    assertThat(envelope.path("readiness").asText()).isEqualTo("NO_ANALYZABLE_TEXT");
    assertThat(envelope.path("outputRef").asText()).isNotBlank();
    String runId = envelope.path("runId").asText();

    Files.delete(sourceRoot);
    CliResult inspected = execute(config, "inspect", "--run", runId);
    assertThat(inspected.exitCode()).isZero();
    assertThat(inspected.stdout()).contains(runId, "FAILED", "NO_ANALYZABLE_TEXT", "SAVED");

    CliResult resultArtifact =
        execute(
            config,
            "artifact",
            "--run",
            runId,
            "--key",
            "source-preparation-result",
            "--max-bytes",
            "4096");
    assertThat(resultArtifact.exitCode())
        .withFailMessage("stage=%s", resultArtifact.stderr())
        .isZero();
    JsonNode result = JSON.readTree(resultArtifact.stdout());
    assertThat(result.path("readiness").asText()).isEqualTo("NO_ANALYZABLE_TEXT");
  }

  @Test
  void newAndNamedExcludeArgumentsAreValidatedBeforeAnyRunIsCreated() throws Exception {
    Path sourceRoot = Files.createDirectory(physicalTemporaryDirectory().resolve("source"));
    Path config = writeConfig(sourceRoot);
    String fullRunId = "analysis-run:" + "a".repeat(64);

    List<List<String>> invalidCommands =
        List.of(
            List.of("--base-preparation", fullRunId),
            List.of("--exclude-file", "src/Legacy.java"),
            List.of(
                "--base-preparation",
                fullRunId,
                "--refresh-file",
                "src/Legacy.java",
                "--exclude-file",
                "src/Legacy.java"));

    for (List<String> options : invalidCommands) {
      CliResult result = execute(config, concat("prepare-source", options));
      assertThat(result.exitCode()).isNotZero();
      assertThat(result.stdout()).doesNotContain("runId");
      try (Stream<Path> runStoreEntries =
          Files.list(physicalTemporaryDirectory().resolve("runs"))) {
        assertThat(runStoreEntries.toList()).isEmpty();
      }
    }
  }

  @Test
  void namedFileExclusionCreatesASecondInspectableVersionWithoutTheLiveSourceRoot()
      throws Exception {
    Path sourceRoot = Files.createDirectory(physicalTemporaryDirectory().resolve("source"));
    Path sourceFiles = Files.createDirectory(sourceRoot.resolve("src"));
    Files.writeString(sourceFiles.resolve("Keep.java"), "final class Keep {}\n");
    Files.writeString(sourceFiles.resolve("Remove.java"), "final class Remove {}\n");
    Path config = writeConfig(sourceRoot);

    CliResult firstPreparation = execute(config, "prepare-source", "--format", "json");
    assertThat(firstPreparation.exitCode())
        .withFailMessage("stage=%s", firstPreparation.stderr())
        .isZero();
    JsonNode firstEnvelope = JSON.readTree(firstPreparation.stdout());
    assertThat(firstEnvelope.path("persistenceStatus").asText()).isEqualTo("SAVED");
    assertThat(firstEnvelope.path("readiness").asText()).isEqualTo("READY");
    String baseRunId = firstEnvelope.path("runId").asText();

    Files.delete(sourceFiles.resolve("Keep.java"));
    Files.delete(sourceFiles.resolve("Remove.java"));
    Files.delete(sourceFiles);
    Files.delete(sourceRoot);

    CliResult excluded =
        execute(
            config,
            "prepare-source",
            "--base-preparation",
            baseRunId,
            "--exclude-file",
            "src/Remove.java",
            "--format",
            "json");

    assertThat(excluded.exitCode()).isZero();
    JsonNode excludedEnvelope = JSON.readTree(excluded.stdout());
    assertThat(excludedEnvelope.path("runId").asText())
        .startsWith("analysis-run:")
        .isNotEqualTo(baseRunId);
    assertThat(excludedEnvelope.path("persistenceStatus").asText()).isEqualTo("SAVED");
    assertThat(excludedEnvelope.path("readiness").asText()).isEqualTo("READY_WITH_EXCLUSIONS");

    String excludedRunId = excludedEnvelope.path("runId").asText();
    CliResult inspected = execute(config, "inspect", "--run", excludedRunId);
    assertThat(inspected.exitCode()).isZero();
    assertThat(inspected.stdout())
        .contains(excludedRunId, "READY_WITH_EXCLUSIONS", "src/Remove.java");

    CliResult resultArtifact =
        execute(
            config,
            "artifact",
            "--run",
            excludedRunId,
            "--key",
            "source-preparation-result",
            "--max-bytes",
            "8192");
    assertThat(resultArtifact.exitCode()).isZero();
    JsonNode result = JSON.readTree(resultArtifact.stdout());
    assertThat(result.path("readiness").asText()).isEqualTo("READY_WITH_EXCLUSIONS");
    assertThat(result.path("summary").path("excludedKnownFiles").asInt()).isEqualTo(1);

    CliResult inventoryArtifact =
        readArtifact(config, excludedRunId, "source-preparation-inventory", "8192");
    assertThat(inventoryArtifact.exitCode())
        .withFailMessage("stage=%s", inventoryArtifact.stderr())
        .isZero();
    assertThat(inventoryArtifact.stdout()).contains("src/Remove.java", "EXCLUDED_BY_USER");
  }

  @Test
  void reportableSingleFileProblemIsSavedAndInspectableAsStructuredIssue() throws Exception {
    Path sourceRoot = Files.createDirectory(physicalTemporaryDirectory().resolve("issue-source"));
    Path sourceFiles = Files.createDirectory(sourceRoot.resolve("src"));
    Files.writeString(sourceFiles.resolve("TooLarge.java"), "xx", StandardCharsets.UTF_8);
    Path config =
        writeConfig(sourceRoot, "issue-source-preparation.yaml", "DIRECTORY", null, null, 1L);

    CliResult prepared = execute(config, "prepare-source", "--format", "json");

    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isEqualTo(3);
    JsonNode envelope = JSON.readTree(prepared.stdout());
    String runId = envelope.path("runId").asText();
    assertThat(runId).startsWith("analysis-run:");
    assertThat(envelope.path("persistenceStatus").asText()).isEqualTo("SAVED");
    assertThat(envelope.path("readiness").asText()).isEqualTo("NEEDS_DECISION");
    assertThat(envelope.path("outputRef").asText()).isNotBlank();
    assertThat(envelope.path("issuesComplete").asBoolean()).isTrue();
    assertThat(envelope.path("issues")).hasSize(1);
    JsonNode issueSummary = envelope.path("issues").get(0);
    assertThat(issueSummary.path("code").asText())
        .isEqualTo("VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED");
    assertThat(issueSummary.path("relativePath").asText()).isEqualTo("src/TooLarge.java");
    assertThat(issueSummary.path("resolution").asText()).isEqualTo("OPEN");
    assertThat(issueSummary.path("allowedActions").isArray()).isTrue();
    assertThat(issueSummary.path("allowedActions").size()).isEqualTo(1);
    assertThat(issueSummary.path("allowedActions").toString()).contains("NEW_PREPARATION");

    CliResult inspected = execute(config, "inspect", "--run", runId);
    assertThat(inspected.exitCode()).isZero();
    assertThat(inspected.stdout())
        .contains(
            runId, envelope.path("outputRef").asText(), "SAVED", "NEEDS_DECISION", "issueCount=1");

    CliResult issuesArtifact = readArtifact(config, runId, "source-preparation-issues", "8192");
    assertThat(issuesArtifact.exitCode())
        .withFailMessage("stage=%s", issuesArtifact.stderr())
        .isZero();
    List<String> issueRows = issuesArtifact.stdout().lines().toList();
    assertThat(issueRows).hasSize(1);
    JsonNode persistedIssue = JSON.readTree(issueRows.get(0));
    assertThat(persistedIssue.path("code").asText())
        .isEqualTo("VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED");
    assertThat(persistedIssue.path("relativePath").asText()).isEqualTo("src/TooLarge.java");
    assertThat(persistedIssue.path("resolution").asText()).isEqualTo("OPEN");
    assertThat(persistedIssue.path("allowedActions").isArray()).isTrue();
    assertThat(persistedIssue.path("allowedActions")).hasSize(1);
    assertThat(persistedIssue.path("allowedActions").toString()).contains("NEW_PREPARATION");
  }

  @Test
  void rejectsRunStoreNestedInsideDirectorySourceBeforeCreatingRunOrWritingIntoSource()
      throws Exception {
    Path physicalRoot = physicalTemporaryDirectory();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("overlap-source"));
    Path nestedRunStore = sourceRoot.resolve("run-store");
    Path preparationWorkspace = physicalRoot.resolve("preparations");
    Path config =
        writeConfig(
            sourceRoot,
            "overlap-source-preparation.yaml",
            "DIRECTORY",
            null,
            null,
            65536L,
            nestedRunStore);
    List<Path> sourceBefore = tree(sourceRoot);
    List<Path> nestedRunStoreBefore = tree(nestedRunStore);
    List<Path> workspaceBefore = tree(preparationWorkspace);

    CliResult prepared = execute(config, "prepare-source", "--format", "json");

    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isEqualTo(2);
    assertThat(prepared.stdout()).doesNotContain("analysis-run:");
    assertThat(tree(sourceRoot)).containsExactlyElementsOf(sourceBefore);
    assertThat(tree(nestedRunStore)).containsExactlyElementsOf(nestedRunStoreBefore);
    assertThat(tree(preparationWorkspace)).containsExactlyElementsOf(workspaceBefore);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void reportsIssueTotalAndSafeArtifactLocatorWhenJsonSummaryIsCapped() throws Exception {
    Path gitExecutable = Path.of("/usr/bin/git");
    GitFixture initial =
        createGitFixture(
            physicalTemporaryDirectory().resolve("many-issues-git"),
            gitExecutable,
            "src/Available.java",
            "final class Available {}\n",
            "src/Excluded.java",
            "final class Excluded {}\n");
    GitFixture git = withCommittedSymlinks(initial, 21);
    Path config =
        writeConfig(
            git.repository(),
            "many-issues-source-preparation.yaml",
            "GIT_COMMIT",
            git.commitId(),
            git.gitExecutable(),
            65536L,
            null,
            null,
            List.of(
                new SourcePreparationTarget(
                    "src/Excluded.java", SourcePreparationTarget.Kind.FILE)));

    CliResult prepared = execute(config, "prepare-source", "--format", "json");

    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode envelope = JSON.readTree(prepared.stdout());
    String runId = envelope.path("runId").asText();
    String outputRef = envelope.path("outputRef").asText();
    assertThat(runId).startsWith("analysis-run:");
    assertThat(outputRef).isNotBlank();
    assertThat(envelope.path("issueCount").asInt()).isEqualTo(21);
    assertThat(envelope.path("issues")).hasSize(20);
    assertThat(envelope.path("issuesComplete").asBoolean()).isFalse();
    assertThat(envelope.path("diagnosticLocations").isArray()).isTrue();
    assertThat(envelope.path("diagnosticLocations").size()).isGreaterThan(0);
    JsonNode issuesLocation = null;
    for (JsonNode location : envelope.path("diagnosticLocations")) {
      if ("source-preparation-issues".equals(location.path("artifactKey").asText())) {
        issuesLocation = location;
        break;
      }
    }
    assertThat(issuesLocation).isNotNull();
    assertThat(issuesLocation.path("runId").asText()).isEqualTo(runId);
    assertThat(issuesLocation.path("outputRef").asText()).isEqualTo(outputRef);
    assertThat(envelope.path("diagnosticLocations").toString())
        .doesNotContain(
            git.repository().toString(),
            physicalTemporaryDirectory().resolve("preparations").toString(),
            physicalTemporaryDirectory().resolve("runs").toString());

    CliResult issuesArtifact = readArtifact(config, runId, "source-preparation-issues", "65536");
    assertThat(issuesArtifact.exitCode())
        .withFailMessage("stage=%s", issuesArtifact.stderr())
        .isZero();
    List<String> issueRows = issuesArtifact.stdout().lines().toList();
    assertThat(issueRows).hasSize(21);
    for (String row : issueRows) {
      assertThat(JSON.readTree(row).path("code").asText()).isEqualTo("UNSUPPORTED_ENTRY");
    }

    CliResult inspected = execute(config, "inspect", "--run", runId);
    assertThat(inspected.exitCode()).isZero();
    assertThat(inspected.stdout())
        .contains(
            "verifiedTextFiles=1",
            "excludedKnownFiles=1",
            "issueCount=21",
            "unknownSubtrees=0",
            "uncheckedKnownFiles=0");

    CliResult text = execute(config, "prepare-source", "--format", "text");
    assertThat(text.exitCode()).withFailMessage("stage=%s", text.stderr()).isZero();
    assertThat(text.stdout())
        .contains(
            "verifiedTextFiles=1",
            "excludedKnownFiles=1",
            "issueCount=21",
            "unknownSubtrees=0",
            "uncheckedKnownFiles=0");
  }

  @Test
  void maxBytesFailureReturnsSafeSavedArtifactLocatorWithoutPhysicalPaths() throws Exception {
    Path sourceRoot =
        Files.createDirectory(physicalTemporaryDirectory().resolve("oversize-artifact-source"));
    Files.createDirectories(sourceRoot.resolve("src"));
    Files.writeString(sourceRoot.resolve("src/Keep.java"), "final class Keep {}\n");
    Path config = writeConfig(sourceRoot);
    CliResult prepared = execute(config, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode envelope = JSON.readTree(prepared.stdout());
    String runId = envelope.path("runId").asText();
    String outputRef = envelope.path("outputRef").asText();

    CliResult oversized = readArtifact(config, runId, "source-preparation-result", "1");

    assertThat(oversized.exitCode()).isNotZero();
    assertThat(oversized.stderr())
        .contains(runId, "source-preparation-result", outputRef)
        .doesNotContain(
            sourceRoot.toString(),
            physicalTemporaryDirectory().resolve("preparations").toString(),
            physicalTemporaryDirectory().resolve("runs").toString());
  }

  @Test
  void rejectsPhysicalOverlapHiddenBySymlinkSourceRootBeforeCreatingRunOrWriting()
      throws Exception {
    Path physicalRoot = physicalTemporaryDirectory();
    Path actualSource = Files.createDirectory(physicalRoot.resolve("physical-source-alias"));
    Files.createDirectories(actualSource.resolve("src"));
    Files.writeString(actualSource.resolve("src/Keep.java"), "final class Keep {}\n");
    Path sourceAlias = physicalRoot.resolve("source-root-alias");
    try {
      Files.createSymbolicLink(sourceAlias, actualSource);
    } catch (IOException | UnsupportedOperationException unavailable) {
      Assumptions.assumeTrue(false, "temporary filesystem does not support symbolic links");
    }
    Path overlappingPreparationWorkspace = actualSource.resolve("preparation-workspace");
    Path config =
        writeConfig(
            sourceAlias,
            "physical-overlap-source-preparation.yaml",
            "DIRECTORY",
            null,
            null,
            65536L,
            null,
            overlappingPreparationWorkspace,
            List.of());
    List<Path> sourceBefore = tree(actualSource);
    List<Path> preparationBefore = tree(overlappingPreparationWorkspace);
    List<Path> runStoreBefore = tree(physicalRoot.resolve("runs"));

    CliResult prepared = execute(config, "prepare-source", "--format", "json");

    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isEqualTo(2);
    assertThat(prepared.stdout()).doesNotContain("analysis-run:");
    assertThat(tree(actualSource)).containsExactlyElementsOf(sourceBefore);
    assertThat(tree(overlappingPreparationWorkspace)).containsExactlyElementsOf(preparationBefore);
    assertThat(tree(physicalRoot.resolve("runs"))).containsExactlyElementsOf(runStoreBefore);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void fixedGitCommitSupportsFirstPreparationRefreshInspectionAndAllFourArtifactQueries()
      throws Exception {
    Path gitExecutable = Path.of("/usr/bin/git");
    GitFixture git =
        createGitFixture(
            physicalTemporaryDirectory().resolve("git-source"),
            gitExecutable,
            "src/Refresh.java",
            "final class Refresh { int value = 1; }\n",
            "src/Keep.java",
            "final class Keep {}\n");
    Path config =
        writeConfig(
            git.repository(),
            "git-source-preparation.yaml",
            "GIT_COMMIT",
            git.commitId(),
            git.gitExecutable());

    CliResult prepared = execute(config, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    assertThat(preparedEnvelope.path("persistenceStatus").asText()).isEqualTo("SAVED");
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String baseRunId = preparedEnvelope.path("runId").asText();
    JsonNode baseResult = artifactJson(config, baseRunId, "source-preparation-result");
    String baseSourceVersion = baseResult.path("sourceVersionId").asText();

    byte[] committedBytes =
        "final class Refresh { int value = 1; }\n".getBytes(StandardCharsets.UTF_8);
    byte[] workingTreeBytes =
        "final class Refresh { int value = 2; }\n".getBytes(StandardCharsets.UTF_8);
    Files.write(git.repository().resolve("src/Refresh.java"), workingTreeBytes);

    CliResult refreshed =
        execute(
            config,
            "prepare-source",
            "--base-preparation",
            baseRunId,
            "--refresh-file",
            "src/Refresh.java",
            "--format",
            "json");
    assertThat(refreshed.exitCode()).isZero();
    JsonNode refreshedEnvelope = JSON.readTree(refreshed.stdout());
    assertThat(refreshedEnvelope.path("persistenceStatus").asText()).isEqualTo("SAVED");
    assertThat(refreshedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String refreshedRunId = refreshedEnvelope.path("runId").asText();
    assertThat(refreshedRunId).isNotEqualTo(baseRunId);

    CliResult inspected = execute(config, "inspect", "--run", refreshedRunId);
    assertThat(inspected.exitCode()).isZero();
    assertThat(inspected.stdout()).contains(refreshedRunId, "READY");

    JsonNode input = artifactJson(config, refreshedRunId, "source-preparation-input");
    assertThat(input.path("operation").asText()).isEqualTo("REFRESH");

    CliResult inventoryArtifact =
        readArtifact(config, refreshedRunId, "source-preparation-inventory", "16384");
    assertThat(inventoryArtifact.exitCode()).isZero();
    String committedDigest = sha256(committedBytes);
    String workingTreeDigest = sha256(workingTreeBytes);
    assertThat(inventoryArtifact.stdout())
        .contains("src/Refresh.java", committedDigest)
        .doesNotContain(workingTreeDigest);

    CliResult issues = readArtifact(config, refreshedRunId, "source-preparation-issues", "16384");
    assertThat(issues.exitCode()).isZero();
    assertThat(issues.stdout()).isEmpty();

    JsonNode refreshedResult = artifactJson(config, refreshedRunId, "source-preparation-result");
    assertThat(refreshedResult.path("readiness").asText()).isEqualTo("READY");
    assertThat(refreshedResult.path("sourceVersionId").asText()).isNotEqualTo(baseSourceVersion);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void rejectsBaseFromAnotherGitRootWithoutCreatingAThirdRun() throws Exception {
    Path gitExecutable = Path.of("/usr/bin/git");
    GitFixture baseGit =
        createGitFixture(
            physicalTemporaryDirectory().resolve("base-git-source"),
            gitExecutable,
            "src/Keep.java",
            "final class Keep {}\n");
    GitFixture otherGit =
        createGitFixture(
            physicalTemporaryDirectory().resolve("other-git-source"),
            gitExecutable,
            "src/Keep.java",
            "final class Keep { int other = 1; }\n");
    Path baseConfig =
        writeConfig(
            baseGit.repository(),
            "base-git-source.yaml",
            "GIT_COMMIT",
            baseGit.commitId(),
            baseGit.gitExecutable());
    Path otherConfig =
        writeConfig(
            otherGit.repository(),
            "other-git-source.yaml",
            "GIT_COMMIT",
            otherGit.commitId(),
            otherGit.gitExecutable());

    CliResult base = execute(baseConfig, "prepare-source", "--format", "json");
    assertThat(base.exitCode()).withFailMessage("stage=%s", base.stderr()).isZero();
    String baseRunId = JSON.readTree(base.stdout()).path("runId").asText();
    List<Path> beforeAttempt = tree(physicalTemporaryDirectory().resolve("runs"));

    CliResult wrongBase =
        execute(
            otherConfig,
            "prepare-source",
            "--base-preparation",
            baseRunId,
            "--refresh-file",
            "src/Keep.java",
            "--format",
            "json");

    assertThat(wrongBase.exitCode()).isNotZero();
    assertThat(wrongBase.stdout()).doesNotContain("analysis-run:");
    assertThat(tree(physicalTemporaryDirectory().resolve("runs")))
        .containsExactlyElementsOf(beforeAttempt);
  }

  private Path writeConfig(Path sourceRoot) throws Exception {
    return writeConfig(sourceRoot, "source-preparation.yaml", "DIRECTORY", null, null, 65536L);
  }

  private Path writeConfig(
      Path sourceRoot,
      String configFileName,
      String sourceKind,
      String commitId,
      Path gitExecutable)
      throws Exception {
    return writeConfig(sourceRoot, configFileName, sourceKind, commitId, gitExecutable, 65536L);
  }

  private Path writeConfig(
      Path sourceRoot,
      String configFileName,
      String sourceKind,
      String commitId,
      Path gitExecutable,
      long maxTotalBytes)
      throws Exception {
    return writeConfig(
        sourceRoot, configFileName, sourceKind, commitId, gitExecutable, maxTotalBytes, null);
  }

  private Path writeConfig(
      Path sourceRoot,
      String configFileName,
      String sourceKind,
      String commitId,
      Path gitExecutable,
      long maxTotalBytes,
      Path configuredRunStore)
      throws Exception {
    return writeConfig(
        sourceRoot,
        configFileName,
        sourceKind,
        commitId,
        gitExecutable,
        maxTotalBytes,
        configuredRunStore,
        null,
        List.of());
  }

  private Path writeConfig(
      Path sourceRoot,
      String configFileName,
      String sourceKind,
      String commitId,
      Path gitExecutable,
      long maxTotalBytes,
      Path configuredRunStore,
      Path configuredPreparationWorkspace,
      List<SourcePreparationTarget> declaredExclusions)
      throws Exception {
    Path physicalRoot = physicalTemporaryDirectory();
    Path config = physicalRoot.resolve(configFileName).toAbsolutePath();
    Path preparationWorkspace =
        configuredPreparationWorkspace == null
            ? physicalRoot.resolve("preparations").toAbsolutePath()
            : configuredPreparationWorkspace.toAbsolutePath();
    Path runStore =
        configuredRunStore == null
            ? physicalRoot.resolve("runs").toAbsolutePath()
            : configuredRunStore.toAbsolutePath();
    Files.createDirectories(preparationWorkspace);
    Files.createDirectories(runStore);
    String gitSourceFields = "";
    if ("GIT_COMMIT".equals(sourceKind)) {
      gitSourceFields =
          "  commit: \""
              + commitId
              + "\"\n  gitExecutable: \""
              + yamlString(gitExecutable)
              + "\"\n";
    }
    String exclusions =
        declaredExclusions.isEmpty()
            ? "[]"
            : "\n"
                + String.join(
                    "\n",
                    declaredExclusions.stream()
                        .map(
                            target ->
                                "  - path: \""
                                    + target.relativePath()
                                    + "\"\n    kind: "
                                    + target.kind().name())
                        .toList());
    String yaml =
        """
        schemaVersion: source-preparation-config-v1
        source:
          kind: %s
          identity: cli-test-project
          root: "%s"
        %s
        exclusions: %s
        limits:
          maxFiles: 32
          maxTotalBytes: %d
        paths:
          preparationWorkspace: "%s"
          runStore: "%s"
        policyRegistry: "%s"
        """
            .formatted(
                sourceKind,
                yamlString(sourceRoot.toAbsolutePath()),
                gitSourceFields,
                exclusions,
                maxTotalBytes,
                yamlString(preparationWorkspace),
                yamlString(runStore),
                yamlString(POLICY_SET));
    Files.writeString(config, yaml, StandardCharsets.UTF_8);
    return config;
  }

  private static CliResult execute(Path config, String... command) {
    return execute(config, List.of(command));
  }

  private static CliResult execute(Path config, List<String> command) {
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    List<String> arguments =
        Stream.concat(Stream.of("--config", config.toString()), command.stream()).toList();
    int exitCode =
        SourceAnalysisCli.executeConfigured(
            arguments.toArray(String[]::new),
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8));
    return new CliResult(
        exitCode,
        outputBytes.toString(StandardCharsets.UTF_8),
        errorBytes.toString(StandardCharsets.UTF_8));
  }

  private static List<String> concat(String first, List<String> rest) {
    return Stream.concat(Stream.of(first), rest.stream()).toList();
  }

  private static JsonNode artifactJson(Path config, String runId, String key) throws Exception {
    CliResult artifact = readArtifact(config, runId, key, "16384");
    assertThat(artifact.exitCode()).withFailMessage("stage=%s", artifact.stderr()).isZero();
    return JSON.readTree(artifact.stdout());
  }

  private static CliResult readArtifact(Path config, String runId, String key, String maxBytes) {
    return execute(config, "artifact", "--run", runId, "--key", key, "--max-bytes", maxBytes);
  }

  private GitFixture createGitFixture(
      Path repository,
      Path gitExecutable,
      String firstPath,
      String firstContent,
      String... otherPathContentPairs)
      throws Exception {
    Files.createDirectories(repository);
    runGit(gitExecutable, repository, "init", "--quiet");
    runGit(gitExecutable, repository, "config", "--local", "user.name", "Source Prep Test");
    runGit(
        gitExecutable,
        repository,
        "config",
        "--local",
        "user.email",
        "source-prep@example.invalid");
    runGit(gitExecutable, repository, "config", "--local", "commit.gpgsign", "false");
    writeSourceFile(repository, firstPath, firstContent);
    if (otherPathContentPairs.length % 2 != 0) {
      throw new IllegalArgumentException("Git fixture paths and contents must be paired");
    }
    for (int index = 0; index < otherPathContentPairs.length; index += 2) {
      writeSourceFile(repository, otherPathContentPairs[index], otherPathContentPairs[index + 1]);
    }
    runGit(gitExecutable, repository, "add", "--all");
    runGit(gitExecutable, repository, "commit", "--quiet", "-m", "source preparation fixture");
    String commitId = runGit(gitExecutable, repository, "rev-parse", "HEAD").trim();
    return new GitFixture(repository.toRealPath(), commitId, gitExecutable.toRealPath());
  }

  private GitFixture withCommittedSymlinks(GitFixture fixture, int count) throws Exception {
    for (int index = 0; index < count; index++) {
      String name = "Link%02d.java".formatted(index);
      Path link = fixture.repository().resolve("unsupported").resolve(name);
      Files.createDirectories(link.getParent());
      try {
        Files.createSymbolicLink(link, Path.of("missing-target-%02d".formatted(index)));
      } catch (IOException | UnsupportedOperationException unavailable) {
        Assumptions.assumeTrue(false, "temporary filesystem does not support symbolic links");
      }
    }
    runGit(fixture.gitExecutable(), fixture.repository(), "add", "--all");
    runGit(
        fixture.gitExecutable(),
        fixture.repository(),
        "commit",
        "--quiet",
        "-m",
        "source preparation unsupported entry fixture");
    String commitId =
        runGit(fixture.gitExecutable(), fixture.repository(), "rev-parse", "HEAD").trim();
    return new GitFixture(fixture.repository(), commitId, fixture.gitExecutable());
  }

  private static void writeSourceFile(Path root, String relativePath, String content)
      throws Exception {
    Path file = root.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  private static String runGit(Path executable, Path repository, String... gitArguments)
      throws Exception {
    List<String> command = new ArrayList<>();
    command.add(executable.toString());
    command.addAll(List.of(gitArguments));
    Process process =
        new ProcessBuilder(command)
            .directory(repository.toFile())
            .redirectErrorStream(true)
            .start();
    byte[] output = process.getInputStream().readAllBytes();
    int exitCode = process.waitFor();
    String text = new String(output, StandardCharsets.UTF_8);
    if (exitCode != 0) {
      throw new AssertionError("fixed Git fixture command failed: " + text);
    }
    return text;
  }

  private static List<Path> tree(Path root) throws Exception {
    try (Stream<Path> paths = Files.walk(root)) {
      return paths.map(root::relativize).sorted().toList();
    }
  }

  private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static String yamlString(Path path) {
    return path.toString().replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private Path physicalTemporaryDirectory() throws IOException {
    return temporaryDirectory.toRealPath();
  }

  private record GitFixture(Path repository, String commitId, Path gitExecutable) {}

  private record CliResult(int exitCode, String stdout, String stderr) {}
}
