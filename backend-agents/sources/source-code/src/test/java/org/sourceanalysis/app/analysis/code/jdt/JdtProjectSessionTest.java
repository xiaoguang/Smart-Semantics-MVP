package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;

/** Contract tests for verified-source JDT project projection and session cleanup. */
class JdtProjectSessionTest {

  private static final String SOURCE = "class Example {}\n";
  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path temporaryDirectory;

  @Test
  void bindsOneSessionToTheSnapshotRootsClasspathLanguageLevelAndFingerprint() throws Exception {
    ProjectFixture fixture = projectFixture();
    VerifiedJavaProject project = fixture.verifiedProject();
    FakeSessionHarness harness = fakeSessionHarness();

    try (JdtProjectSession session = harness.open(project)) {
      assertThat(project.snapshotId()).isEqualTo(fixture.sourceTexts().snapshotId());
      assertThat(project.sourceLevel()).isEqualTo("17");
      assertThat(project.sourceRoots()).containsExactly("src/main/java");
      assertThat(project.classpath())
          .containsExactly(fixture.classpathEntry().toAbsolutePath().normalize());
      assertThat(project.fingerprint()).matches("[0-9a-f]{64}");
      assertThat(project.fingerprint()).isEqualTo(fixture.verifiedProject().fingerprint());

      assertThat(session.snapshotId()).isEqualTo(project.snapshotId());
      assertThat(session.descriptor().languageLevel()).isEqualTo(project.sourceLevel());
      assertThat(harness.starter().startCount()).isEqualTo(1);
    }
  }

  @Test
  void rejectsSourceRootsOutsideTheVerifiedSnapshotBeforeStartingJdt() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness();

    assertThatThrownBy(
            () -> {
              VerifiedJavaProject project =
                  VerifiedJavaProject.fromVerifiedSourceTextSet(
                      fixture.sourceTexts(),
                      List.of("src/main/java/../outside"),
                      List.of(fixture.classpathEntry()),
                      "17");
              JdtProjectSession.open(project, harness.client(), harness.isolation());
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source root");
    assertThat(harness.starter().startCount())
        .as("an invalid verified source root must fail before JDT startup")
        .isZero();
  }

  @Test
  void rejectsAbsoluteSourceRootsBeforeStartingJdt() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness();

    assertThatThrownBy(
            () ->
                VerifiedJavaProject.fromVerifiedSourceTextSet(
                    fixture.sourceTexts(),
                    List.of(fixture.snapshotRoot().resolve("src/main/java")),
                    List.of(fixture.classpathEntry()),
                    "17"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source root");
    assertThat(harness.starter().startCount()).isZero();
  }

  @Test
  void keepsMultipleSnapshotRelativeSourceRootsAsSeparateClasspathEntries() throws Exception {
    ProjectFixture fixture = projectFixtureWithTwoSourceRoots();

    VerifiedJavaProject project =
        VerifiedJavaProject.fromVerifiedSourceTextSet(
            fixture.sourceTexts(),
            List.of("src/main/java", "src/test/java"),
            List.of(fixture.classpathEntry()),
            "17");

    assertThat(project.sourceRoots()).containsExactly("src/main/java", "src/test/java");
  }

  @Test
  void rejectsPathTraversalInSnapshotEntriesBeforeStartingJdt() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness();

    assertThatThrownBy(
            () -> {
              VerifiedSourceTextSet sourceTexts = fixture.sourceTextsWithPath("../outside.java");
              VerifiedJavaProject project =
                  VerifiedJavaProject.fromVerifiedSourceTextSet(
                      sourceTexts,
                      List.of("src/main/java"),
                      List.of(fixture.classpathEntry()),
                      "17");
              JdtProjectSession.open(project, harness.client(), harness.isolation());
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source path");
    assertThat(harness.starter().startCount())
        .as("a traversal source entry must fail before JDT startup")
        .isZero();
  }

  @Test
  void rejectsAbsoluteSourceEntriesBeforeStartingJdt() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness();

    assertThatThrownBy(
            () -> {
              VerifiedSourceTextSet sourceTexts =
                  fixture.sourceTextsWithPath(
                      fixture.snapshotRoot().resolve("outside.java").toString());
              VerifiedJavaProject project =
                  VerifiedJavaProject.fromVerifiedSourceTextSet(
                      sourceTexts,
                      List.of("src/main/java"),
                      List.of(fixture.classpathEntry()),
                      "17");
              JdtProjectSession.open(project, harness.client(), harness.isolation());
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source path");
    assertThat(harness.starter().startCount())
        .as("an absolute source entry must fail before JDT startup")
        .isZero();
  }

  @Test
  void classpathFingerprintIncludesEffectiveOrderAndFileContents() throws Exception {
    ProjectFixture fixture = projectFixture();
    Path secondClasspathEntry = Files.createFile(fixture.snapshotRoot().resolve("second.jar"));
    Files.writeString(fixture.classpathEntry(), "first-classpath-content");
    Files.writeString(secondClasspathEntry, "second-classpath-content");

    VerifiedJavaProject forward =
        VerifiedJavaProject.fromVerifiedSourceTextSet(
            fixture.sourceTexts(),
            List.of("src/main/java"),
            List.of(fixture.classpathEntry(), secondClasspathEntry),
            "17");
    VerifiedJavaProject reverse =
        VerifiedJavaProject.fromVerifiedSourceTextSet(
            fixture.sourceTexts(),
            List.of("src/main/java"),
            List.of(secondClasspathEntry, fixture.classpathEntry()),
            "17");
    Files.writeString(fixture.classpathEntry(), "drifted-classpath-content");
    VerifiedJavaProject changedContent =
        VerifiedJavaProject.fromVerifiedSourceTextSet(
            fixture.sourceTexts(),
            List.of("src/main/java"),
            List.of(fixture.classpathEntry(), secondClasspathEntry),
            "17");

    assertThat(forward.fingerprint()).isNotEqualTo(reverse.fingerprint());
    assertThat(forward.fingerprint()).isNotEqualTo(changedContent.fingerprint());
  }

  @Test
  void rejectsClasspathContentDriftBeforeStartingJdt() throws Exception {
    ProjectFixture fixture = projectFixture();
    VerifiedJavaProject project = fixture.verifiedProject();
    Files.writeString(fixture.classpathEntry(), "changed-after-verification");
    FakeSessionHarness harness = fakeSessionHarness();

    Throwable failure = openAndCaptureFailure(harness, project);

    assertThat(failure)
        .as("classpath content drift must fail before JDT startup")
        .isInstanceOf(CodeEngineException.class);
    assertThat(harness.starter().startCount()).isZero();
  }

  @Test
  void readinessUsesAReliableDeclarationPositionBeforeOpeningTheSession() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness(StartupBehavior.RELIABLE_DECLARATION);

    try (JdtProjectSession ignored = harness.open(fixture.verifiedProject())) {
      assertThat(harness.starter().lastProcess().declarationCharacter())
          .as("readiness must query a declaration-bearing position, not file origin")
          .isEqualTo(6);
    }
  }

  @Test
  void readinessUsesJdtDocumentSymbolsInsteadOfMatchingADeclarationInCommentText()
      throws Exception {
    ProjectFixture fixture = projectFixtureWithCommentBeforeDeclaration();
    FakeSessionHarness harness = fakeSessionHarness(StartupBehavior.DOCUMENT_SYMBOL_READINESS);
    Throwable failure = openAndCaptureFailure(harness, fixture.verifiedProject());

    assertThat(failure).as("a document-symbol readiness probe must open the session").isNull();
    if (failure == null) {
      assertThat(harness.starter().lastProcess().sawDocumentSymbols())
          .as("readiness must ask JDT for document symbols")
          .isTrue();
      assertThat(harness.starter().lastProcess().declarationLine())
          .as("readiness must use the declaration-bearing symbol line")
          .isEqualTo(1);
      assertThat(harness.starter().lastProcess().declarationCharacter())
          .as("readiness must use the declaration-bearing symbol selection")
          .isEqualTo(6);
    }
  }

  @Test
  void anEmptyDeclarationReadinessResponseCannotOpenASession() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness(StartupBehavior.EMPTY_DECLARATION);

    Throwable failure = openAndCaptureFailure(harness, fixture.verifiedProject());

    assertThat(failure)
        .as("an empty readiness declaration response is not an open-success signal")
        .isInstanceOf(CodeEngineException.class);
  }

  @Test
  void anUnreadyOrTimedOutDeclarationProbeCannotOpenASession() throws Exception {
    ProjectFixture fixture = projectFixture();

    Throwable unready =
        openAndCaptureFailure(
            fakeSessionHarness(StartupBehavior.UNREADY), fixture.verifiedProject());
    Throwable timeout =
        openAndCaptureFailure(
            fakeSessionHarness(StartupBehavior.DECLARATION_TIMEOUT), fixture.verifiedProject());

    assertThat(unready).isInstanceOf(CodeEngineException.class);
    assertThat(timeout).isInstanceOf(CodeEngineException.class);
  }

  @Test
  void startupCleanupFailureRemainsObservableWhenTheProcessCannotBeStopped() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness(StartupBehavior.STICKY_DECLARATION_TIMEOUT);

    Throwable failure = openAndCaptureFailure(harness, fixture.verifiedProject());

    assertThat(failure).isInstanceOf(CodeEngineException.class);
    assertThat(harness.starter().lastProcess()).isNotNull();
    assertThat(harness.starter().lastProcess().isAlive())
        .as("the test process deliberately refuses stop so its state remains observable")
        .isTrue();
    assertThat(hasCleanupEvidence(failure))
        .as("startup stop=false must be observable as cleanup failure or suppressed evidence")
        .isTrue();
  }

  @Test
  void descriptorReportsActualToolIdentitiesInsteadOfVersionlessDirectoryNames() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness();

    try (JdtProjectSession session = harness.open(fixture.verifiedProject())) {
      assertThat(session.descriptor().toolVersions().values())
          .anyMatch(value -> value.contains("21.0.8"));
      assertThat(session.descriptor().toolVersions().values())
          .anyMatch(value -> value.contains("1.0"));
      assertThat(session.descriptor().toolVersions().values())
          .noneMatch(value -> value.equals("jdk") || value.equals("jdtls"));
    }
  }

  @Test
  void closeIsIdempotentAndRemovesOnlyTheSessionOwnedWorkspace() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness();
    JdtProjectSession session = harness.open(fixture.verifiedProject());
    Path ownedWorkspace = session.workspace();

    assertThat(Files.exists(ownedWorkspace)).isTrue();
    session.close();
    session.close();

    assertThat(Files.exists(ownedWorkspace)).isFalse();
    assertThat(harness.starter().lastProcess()).isNotNull();
    assertThat(harness.starter().lastProcess().isAlive())
        .as("owned fake JDT process must be stopped")
        .isFalse();
    assertThat(Files.exists(fixture.snapshotRoot())).isTrue();
    assertThat(Files.readString(fixture.sourceFile())).isEqualTo(SOURCE);
  }

  @Test
  void cachesNavigationByOperationAndExactPositionWithinOneSession() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness();

    try (JdtProjectSession ignored = harness.open(fixture.verifiedProject())) {
      String uri = "file:///Example.java";
      JdtNavigationResolver.Position first = new JdtNavigationResolver.Position(2, 7);
      JdtNavigationResolver.Position second = new JdtNavigationResolver.Position(2, 8);

      assertThat(harness.client().definitions(uri, first)).hasSize(1);
      assertThat(harness.client().definitions(uri, first)).hasSize(1);
      assertThat(harness.client().definitions(uri, second)).hasSize(1);
      assertThat(harness.client().implementations(uri, first)).hasSize(1);
      assertThat(harness.client().implementations(uri, first)).hasSize(1);

      assertThat(harness.starter().lastProcess().requestCount("textDocument/definition"))
          .as("the exact definition query is sent once, while another position stays independent")
          .isEqualTo(2);
      assertThat(harness.starter().lastProcess().requestCount("textDocument/implementation"))
          .as("definition and implementation use distinct cache keys")
          .isEqualTo(1);
    }
  }

  @Test
  void cachesLegalEmptyAndFailedNavigationWithoutImplicitRetry() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness emptyHarness = fakeSessionHarness(StartupBehavior.EMPTY_NAVIGATION);

    try (JdtProjectSession ignored = emptyHarness.open(fixture.verifiedProject())) {
      JdtNavigationResolver.Position position = new JdtNavigationResolver.Position(3, 4);
      assertThat(emptyHarness.client().definitions("file:///Example.java", position)).isEmpty();
      assertThat(emptyHarness.client().definitions("file:///Example.java", position)).isEmpty();
      assertThat(emptyHarness.starter().lastProcess().requestCount("textDocument/definition"))
          .as("a legal empty response is a cacheable result")
          .isEqualTo(1);
    }

    FakeSessionHarness failureHarness = fakeSessionHarness(StartupBehavior.FAILED_NAVIGATION);
    try (JdtProjectSession ignored = failureHarness.open(fixture.verifiedProject())) {
      JdtNavigationResolver.Position position = new JdtNavigationResolver.Position(4, 5);
      assertThatThrownBy(
              () -> failureHarness.client().definitions("file:///Example.java", position))
          .isInstanceOf(CodeEngineException.class);
      assertThatThrownBy(
              () -> failureHarness.client().definitions("file:///Example.java", position))
          .isInstanceOf(CodeEngineException.class);
      assertThat(failureHarness.starter().lastProcess().requestCount("textDocument/definition"))
          .as("a failed physical query remains observable and is not retried implicitly")
          .isEqualTo(1);
    }
  }

  @Test
  void validatesNavigationResponsesBeforeCachingOrJournalingSuccess() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness(StartupBehavior.MALFORMED_NAVIGATION);

    try (JdtProjectSession session = harness.open(fixture.verifiedProject())) {
      JdtNavigationResolver.Position position = new JdtNavigationResolver.Position(4, 6);
      assertThatThrownBy(() -> harness.client().definitions("file:///Example.java", position))
          .isInstanceOf(CodeEngineException.class)
          .hasMessageContaining("unsupported navigation result");
      assertThatThrownBy(() -> harness.client().definitions("file:///Example.java", position))
          .isInstanceOf(CodeEngineException.class)
          .hasMessageContaining("unsupported navigation result");

      assertThat(harness.starter().lastProcess().requestCount("textDocument/definition"))
          .as("a malformed physical response is retained as one failed query without a retry")
          .isEqualTo(1);
      Path journal =
          session
              .languageServerDataDirectory()
              .resolve("source-analysis-navigation-query-journal.jsonl");
      List<JsonNode> exchanges =
          Files.readAllLines(journal, StandardCharsets.UTF_8).stream()
              .map(JdtProjectSessionTest::json)
              .filter(record -> "QUERY_EXCHANGE".equals(record.path("kind").textValue()))
              .toList();
      assertThat(exchanges).hasSize(1);
      assertThat(exchanges.get(0).path("outcome").textValue()).isEqualTo("FAILURE");
      assertThat(exchanges.get(0).has("response")).isFalse();
    }
  }

  @Test
  void cachesPrepareAndEveryOutgoingHierarchyItemSeparately() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness();

    try (JdtProjectSession ignored = harness.open(fixture.verifiedProject())) {
      JdtNavigationResolver.Position position = new JdtNavigationResolver.Position(5, 6);
      assertThat(harness.client().outgoingCalls("file:///Example.java", position)).hasSize(2);
      assertThat(harness.client().outgoingCalls("file:///Example.java", position)).hasSize(2);

      assertThat(harness.starter().lastProcess().requestCount("textDocument/prepareCallHierarchy"))
          .isEqualTo(1);
      assertThat(harness.starter().lastProcess().requestCount("callHierarchy/outgoingCalls"))
          .as("both prepared hierarchy items are retained, and each physical query runs once")
          .isEqualTo(2);
    }
  }

  @Test
  void aNewProjectSessionStartsWithAnEmptyNavigationCache() throws Exception {
    ProjectFixture fixture = projectFixture();
    JdtNavigationResolver.Position position = new JdtNavigationResolver.Position(6, 7);

    FakeSessionHarness first = fakeSessionHarness();
    try (JdtProjectSession ignored = first.open(fixture.verifiedProject())) {
      first.client().definitions("file:///Example.java", position);
      first.client().definitions("file:///Example.java", position);
      assertThat(first.starter().lastProcess().requestCount("textDocument/definition"))
          .isEqualTo(1);
    }

    FakeSessionHarness second = fakeSessionHarness();
    try (JdtProjectSession ignored = second.open(fixture.verifiedProject())) {
      second.client().definitions("file:///Example.java", position);
      assertThat(second.starter().lastProcess().requestCount("textDocument/definition"))
          .as("cache lifetime is exactly one JDT project session")
          .isEqualTo(1);
    }
  }

  @Test
  void recordsOneRawExchangeAndAKeyOnlyHitForRepeatedNavigation() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness();

    try (JdtProjectSession session = harness.open(fixture.verifiedProject())) {
      JdtNavigationResolver.Position position = new JdtNavigationResolver.Position(7, 8);
      harness.client().definitions("file:///Example.java", position);
      harness.client().definitions("file:///Example.java", position);

      Path journal =
          session
              .languageServerDataDirectory()
              .resolve("source-analysis-navigation-query-journal.jsonl");
      assertThat(journal).isRegularFile();
      List<JsonNode> records =
          Files.readAllLines(journal, StandardCharsets.UTF_8).stream()
              .map(JdtProjectSessionTest::json)
              .toList();
      assertThat(records).hasSize(2);
      assertThat(records.get(0).path("kind").textValue()).isEqualTo("QUERY_EXCHANGE");
      assertThat(records.get(0).path("operation").textValue()).isEqualTo("DEFINITION");
      assertThat(records.get(0).path("request").isObject()).isTrue();
      assertThat(records.get(0).path("response").toString()).contains("Target.java");
      assertThat(records.get(1).path("kind").textValue()).isEqualTo("CACHE_HIT");
      assertThat(records.get(1).path("queryKey").textValue())
          .isEqualTo(records.get(0).path("queryKey").textValue());
      assertThat(records.get(1).has("request")).isFalse();
      assertThat(records.get(1).has("response")).isFalse();

      JsonNode statistics = queryStatistics(harness.client());
      assertThat(statistics.path("physicalQueryCount").intValue()).isEqualTo(1);
      assertThat(statistics.path("cacheHitCount").intValue()).isEqualTo(1);
      assertThat(statistics.path("uniqueQueryKeyCount").intValue()).isEqualTo(1);
    }
  }

  @Test
  void retainsNavigationJournalAndStatisticsAfterSessionClose() throws Exception {
    ProjectFixture fixture = projectFixture();
    FakeSessionHarness harness = fakeSessionHarness();
    JdtProjectSession session = harness.open(fixture.verifiedProject());
    JdtNavigationResolver.Position position = new JdtNavigationResolver.Position(8, 9);
    harness.client().definitions("file:///Example.java", position);
    harness.client().definitions("file:///Example.java", position);

    session.close();

    Path retained = retainedNavigationJournal(harness.client());
    assertThat(retained).isRegularFile();
    assertThat(Files.readString(retained, StandardCharsets.UTF_8))
        .contains("QUERY_EXCHANGE", "CACHE_HIT", "SESSION_SUMMARY");
    JsonNode statistics = queryStatistics(harness.client());
    assertThat(statistics.path("physicalQueryCount").intValue()).isEqualTo(1);
    assertThat(statistics.path("cacheHitCount").intValue()).isEqualTo(1);
    assertThat(statistics.path("uniqueQueryKeyCount").intValue()).isEqualTo(1);
  }

  private static JsonNode queryStatistics(JdtLanguageServerClient client) {
    java.lang.reflect.Method method =
        java.util.Arrays.stream(client.getClass().getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals("queryStatistics"))
            .findFirst()
            .orElse(null);
    assertThat(method)
        .as("the JDT client exposes a package-private immutable statistics snapshot")
        .isNotNull();
    if (method == null) {
      return JSON.createObjectNode();
    }
    try {
      method.setAccessible(true);
      return JSON.valueToTree(method.invoke(client));
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("could not read JDT navigation query statistics", failure);
    }
  }

  private static Path retainedNavigationJournal(JdtLanguageServerClient client) {
    java.lang.reflect.Method method =
        java.util.Arrays.stream(client.getClass().getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals("retainedNavigationJournal"))
            .findFirst()
            .orElse(null);
    assertThat(method)
        .as("the session exposes its retained private navigation journal inside the JDT package")
        .isNotNull();
    if (method == null) {
      return Path.of("missing-navigation-journal");
    }
    try {
      method.setAccessible(true);
      return (Path) method.invoke(client);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("could not read retained JDT navigation journal", failure);
    }
  }

  private static JsonNode json(String value) {
    try {
      return JSON.readTree(value);
    } catch (IOException failure) {
      throw new AssertionError("invalid navigation journal JSON", failure);
    }
  }

  private ProjectFixture projectFixture() throws IOException {
    Path snapshotRoot = Files.createDirectories(temporaryDirectory.resolve("snapshot"));
    Path sourceFile = Files.writeString(snapshotRoot.resolve("source-sentinel.java"), SOURCE);
    Path classpathEntry = Files.createFile(snapshotRoot.resolve("approved-dependency.jar"));
    VerifiedSourceTextDocument document =
        document("src/main/java/com/example/Example.java", SOURCE);
    return new ProjectFixture(snapshotRoot, sourceFile, classpathEntry, sourceTexts(document));
  }

  private ProjectFixture projectFixtureWithTwoSourceRoots() throws IOException {
    Path snapshotRoot = Files.createDirectories(temporaryDirectory.resolve("two-root-snapshot"));
    Path sourceFile = Files.writeString(snapshotRoot.resolve("source-sentinel.java"), SOURCE);
    Path classpathEntry = Files.createFile(snapshotRoot.resolve("approved-dependency.jar"));
    VerifiedSourceTextDocument mainDocument =
        document("src/main/java/com/example/Example.java", SOURCE);
    VerifiedSourceTextDocument testDocument =
        document("src/test/java/com/example/ExampleTest.java", "class ExampleTest {}\n");
    return new ProjectFixture(
        snapshotRoot, sourceFile, classpathEntry, sourceTexts(List.of(mainDocument, testDocument)));
  }

  private ProjectFixture projectFixtureWithCommentBeforeDeclaration() throws IOException {
    Path snapshotRoot = Files.createDirectories(temporaryDirectory.resolve("comment-snapshot"));
    Path sourceFile = Files.writeString(snapshotRoot.resolve("source-sentinel.java"), SOURCE);
    Path classpathEntry = Files.createFile(snapshotRoot.resolve("approved-dependency.jar"));
    String source = "// class Fake {}\nclass Example {}\n";
    VerifiedSourceTextDocument document =
        document("src/main/java/com/example/Example.java", source);
    return new ProjectFixture(snapshotRoot, sourceFile, classpathEntry, sourceTexts(document));
  }

  private FakeSessionHarness fakeSessionHarness() throws IOException {
    return fakeSessionHarness(StartupBehavior.NORMAL);
  }

  private FakeSessionHarness fakeSessionHarness(StartupBehavior behavior) throws IOException {
    Path installation = Files.createDirectories(temporaryDirectory.resolve("tools/jdtls"));
    Path javaHome = Files.createDirectories(temporaryDirectory.resolve("tools/jdk"));
    Path java = Files.createDirectories(javaHome.resolve("bin")).resolve("java");
    Files.createDirectories(installation.resolve("plugins"));
    Files.writeString(
        installation.resolve("plugins/org.eclipse.equinox.launcher_1.0.jar"), "launcher\n");
    Files.writeString(
        installation.resolve("plugins/org.eclipse.jdt.ls.core_1.0.jar"), "jdt-ls-core\n");
    Files.writeString(installation.resolve("plugins/org.eclipse.jdt.core_1.0.jar"), "jdt-core\n");
    Files.createDirectories(installation.resolve("config_linux"));
    Files.createDirectories(installation.resolve("config_mac"));
    Files.createDirectories(installation.resolve("config_win"));
    Files.writeString(
        java,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"-version\" ]; then\n"
            + "  echo 'openjdk version \"21.0.8\"' >&2\n"
            + "  exit 0\n"
            + "fi\n"
            + "exit 71\n",
        StandardCharsets.UTF_8);
    assertThat(java.toFile().setExecutable(true, false)).isTrue();
    EffectiveEngineConfiguration.JdtConfiguration configuration =
        new EffectiveEngineConfiguration.JdtConfiguration(
            installation,
            javaHome,
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            Duration.ofSeconds(2));
    FakeProcessStarter starter = new FakeProcessStarter(behavior);
    JdtProcessIsolation isolation = new JdtProcessIsolation(starter);
    JdtLanguageServerClient client = new JdtLanguageServerClient(configuration, isolation);
    return new FakeSessionHarness(starter, isolation, client);
  }

  private static VerifiedSourceTextSet sourceTexts(VerifiedSourceTextDocument document) {
    return sourceTexts(List.of(document));
  }

  private static VerifiedSourceTextSet sourceTexts(List<VerifiedSourceTextDocument> documents) {
    String seed =
        documents.stream()
            .map(document -> document.path() + ":" + document.sha256().value())
            .reduce("", (left, right) -> left + right);
    return new VerifiedSourceTextSet(
        "snapshot:" + digest(seed),
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", seed),
        reference("source-inventory", seed),
        reference("verified-snapshot", seed),
        controls(seed),
        documents);
  }

  private static Throwable openAndCaptureFailure(
      FakeSessionHarness harness, VerifiedJavaProject project) {
    JdtProjectSession session = null;
    try {
      session = harness.open(project);
      return null;
    } catch (Throwable failure) {
      return failure;
    } finally {
      if (session != null) {
        session.close();
      }
    }
  }

  private static boolean hasCleanupEvidence(Throwable failure) {
    if (failure.getSuppressed().length > 0) {
      return true;
    }
    for (Throwable current = failure; current != null; current = current.getCause()) {
      String message = current.getMessage();
      if (message != null
          && (message.toLowerCase().contains("cleanup")
              || message.toLowerCase().contains("remained alive")
              || message.toLowerCase().contains("stop=false"))) {
        return true;
      }
    }
    return false;
  }

  private static VerifiedSourceTextDocument document(String path, String source) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    return new VerifiedSourceTextDocument(
        new ArtifactId("file:" + digest(path)),
        path,
        "100644",
        "text/plain",
        bytes.length,
        new Sha256Digest(digest(bytes)),
        ImmutableBytes.copyOf(bytes));
  }

  private static ArtifactReference reference(String prefix, String seed) {
    return new ArtifactReference(
        new ArtifactId(prefix + ":" + digest(seed + prefix)),
        new Sha256Digest(digest(seed + "-sha")));
  }

  private static ArtifactControls controls(String seed) {
    return new ArtifactControls(
        new Sha256Digest(digest(seed + "-toolchain")),
        new Sha256Digest(digest(seed + "-profile")),
        new Sha256Digest(digest(seed + "-schema")),
        null,
        new ArtifactPolicyRegistryReference(
            new ArtifactId("artifact-policy-registry:" + digest(seed + "-registry")),
            new Sha256Digest(digest(seed + "-registry-sha"))));
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private record ProjectFixture(
      Path snapshotRoot, Path sourceFile, Path classpathEntry, VerifiedSourceTextSet sourceTexts) {

    VerifiedJavaProject verifiedProject() {
      return VerifiedJavaProject.fromVerifiedSourceTextSet(
          sourceTexts, List.of("src/main/java"), List.of(classpathEntry), "17");
    }

    VerifiedSourceTextSet sourceTextsWithPath(String path) {
      return JdtProjectSessionTest.sourceTexts(document(path, SOURCE));
    }
  }

  private record FakeSessionHarness(
      FakeProcessStarter starter, JdtProcessIsolation isolation, JdtLanguageServerClient client) {

    JdtProjectSession open(VerifiedJavaProject project) {
      return JdtProjectSession.open(project, client, isolation);
    }
  }

  private enum StartupBehavior {
    NORMAL,
    RELIABLE_DECLARATION,
    DOCUMENT_SYMBOL_READINESS,
    EMPTY_DECLARATION,
    UNREADY,
    DECLARATION_TIMEOUT,
    STICKY_DECLARATION_TIMEOUT,
    EMPTY_NAVIGATION,
    FAILED_NAVIGATION,
    MALFORMED_NAVIGATION
  }

  private static final class FakeProcessStarter implements JdtProcessIsolation.ProcessStarter {

    private final StartupBehavior behavior;
    private final AtomicInteger startCount = new AtomicInteger();
    private volatile FakeJdtProcess lastProcess;

    private FakeProcessStarter(StartupBehavior behavior) {
      this.behavior = behavior;
    }

    @Override
    public Process start(List<String> command, Path workingDirectory) throws IOException {
      FakeJdtProcess process = new FakeJdtProcess(behavior);
      lastProcess = process;
      startCount.incrementAndGet();
      return process;
    }

    int startCount() {
      return startCount.get();
    }

    FakeJdtProcess lastProcess() {
      return lastProcess;
    }
  }

  private static final class FakeJdtProcess extends Process {

    private final java.io.PipedInputStream clientInput = new java.io.PipedInputStream(64 * 1024);
    private final java.io.PipedOutputStream serverOutput =
        new java.io.PipedOutputStream(clientInput);
    private final java.io.PipedInputStream serverInput = new java.io.PipedInputStream(64 * 1024);
    private final java.io.PipedOutputStream clientOutput =
        new java.io.PipedOutputStream(serverInput);
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final StartupBehavior behavior;
    private final Thread serverThread;
    private volatile int exitCode;
    private volatile int declarationCharacter = -1;
    private volatile int declarationLine = -1;
    private volatile boolean sawDocumentSymbols;
    private final Map<String, AtomicInteger> requestCounts =
        new java.util.concurrent.ConcurrentHashMap<>();

    FakeJdtProcess(StartupBehavior behavior) throws IOException {
      this.behavior = behavior;
      serverThread = new Thread(this::serve, "fake-jdt-language-server");
      serverThread.setDaemon(true);
      serverThread.start();
    }

    @Override
    public OutputStream getOutputStream() {
      return clientOutput;
    }

    @Override
    public InputStream getInputStream() {
      return clientInput;
    }

    @Override
    public InputStream getErrorStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() throws InterruptedException {
      serverThread.join();
      return exitCode;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
      serverThread.join(unit.toMillis(timeout));
      return !isAlive();
    }

    @Override
    public int exitValue() {
      if (isAlive()) {
        throw new IllegalThreadStateException("fake JDT process is still running");
      }
      return exitCode;
    }

    @Override
    public void destroy() {
      if (behavior != StartupBehavior.STICKY_DECLARATION_TIMEOUT) {
        terminate(0);
      }
    }

    @Override
    public Process destroyForcibly() {
      if (behavior != StartupBehavior.STICKY_DECLARATION_TIMEOUT) {
        terminate(0);
      }
      return this;
    }

    @Override
    public boolean isAlive() {
      return alive.get();
    }

    private void serve() {
      try {
        while (alive.get()) {
          int contentLength = readContentLength(serverInput);
          if (contentLength < 0) {
            return;
          }
          byte[] bytes = serverInput.readNBytes(contentLength);
          if (bytes.length != contentLength) {
            return;
          }
          JsonObject request =
              JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
          String method = request.has("method") ? request.get("method").getAsString() : "";
          requestCounts.computeIfAbsent(method, ignored -> new AtomicInteger()).incrementAndGet();
          if ("exit".equals(method)) {
            return;
          }
          if (request.has("id")) {
            respond(request, method);
          }
        }
      } catch (IOException | RuntimeException ignored) {
        // Closing a fake process interrupts its protocol loop during session cleanup.
      } finally {
        terminate(0);
      }
    }

    private void respond(JsonObject request, String method) throws IOException {
      if ("initialize".equals(method) && behavior == StartupBehavior.UNREADY) {
        return;
      }
      if ("textDocument/declaration".equals(method)) {
        JsonObject params = request.getAsJsonObject("params");
        JsonObject position = params.getAsJsonObject("position");
        declarationLine = position.get("line").getAsInt();
        declarationCharacter = position.get("character").getAsInt();
        if (behavior == StartupBehavior.DECLARATION_TIMEOUT
            || behavior == StartupBehavior.STICKY_DECLARATION_TIMEOUT) {
          return;
        }
      }
      if ("textDocument/definition".equals(method)
          && behavior == StartupBehavior.FAILED_NAVIGATION) {
        respondWithError(request, -32603, "navigation failed");
        return;
      }
      String result =
          switch (method) {
            case "initialize" -> "{\"capabilities\":{}}";
            case "textDocument/documentSymbol" -> {
              sawDocumentSymbols = true;
              yield "[{\"name\":\"Example\",\"kind\":5,"
                  + "\"range\":{\"start\":{\"line\":1,\"character\":0},"
                  + "\"end\":{\"line\":1,\"character\":16}},"
                  + "\"selectionRange\":{\"start\":{\"line\":1,\"character\":6},"
                  + "\"end\":{\"line\":1,\"character\":13}}}]";
            }
            case "textDocument/declaration" ->
                behavior == StartupBehavior.NORMAL
                        || behavior == StartupBehavior.EMPTY_NAVIGATION
                        || behavior == StartupBehavior.FAILED_NAVIGATION
                        || behavior == StartupBehavior.MALFORMED_NAVIGATION
                        || (behavior == StartupBehavior.RELIABLE_DECLARATION
                            && declarationCharacter == 6)
                        || (behavior == StartupBehavior.DOCUMENT_SYMBOL_READINESS
                            && declarationLine == 1
                            && declarationCharacter == 6)
                    ? "[{\"uri\":\"file:///Example.java\",\"range\":{\"start\":{\"line\":0,\"character\":0},\"end\":{\"line\":0,\"character\":1}}}]"
                    : "[]";
            case "textDocument/definition", "textDocument/implementation" ->
                behavior == StartupBehavior.EMPTY_NAVIGATION
                    ? "[]"
                    : behavior == StartupBehavior.MALFORMED_NAVIGATION
                        ? "true"
                        : "[{\"uri\":\"file:///Target.java\",\"range\":{\"start\":{\"line\":1,\"character\":2},\"end\":{\"line\":1,\"character\":8}}}]";
            case "textDocument/prepareCallHierarchy" ->
                "[{\"name\":\"first\",\"kind\":6,\"uri\":\"file:///Example.java\","
                    + "\"range\":{\"start\":{\"line\":0,\"character\":0},\"end\":{\"line\":9,\"character\":0}},"
                    + "\"selectionRange\":{\"start\":{\"line\":0,\"character\":1},\"end\":{\"line\":0,\"character\":6}}},"
                    + "{\"name\":\"second\",\"kind\":6,\"uri\":\"file:///Example.java\","
                    + "\"range\":{\"start\":{\"line\":10,\"character\":0},\"end\":{\"line\":19,\"character\":0}},"
                    + "\"selectionRange\":{\"start\":{\"line\":10,\"character\":1},\"end\":{\"line\":10,\"character\":7}}}]";
            case "callHierarchy/outgoingCalls" -> outgoingResponse(request);
            case "shutdown" -> "null";
            default -> "null";
          };
      String response =
          "{\"jsonrpc\":\"2.0\",\"id\":" + request.get("id") + ",\"result\":" + result + "}";
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      synchronized (serverOutput) {
        serverOutput.write(
            ("Content-Length: " + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        serverOutput.write(bytes);
        serverOutput.flush();
      }
    }

    private static String outgoingResponse(JsonObject request) {
      String name =
          request.getAsJsonObject("params").getAsJsonObject("item").get("name").getAsString();
      int line = "first".equals(name) ? 20 : 30;
      return "[{\"to\":{\"name\":\"target-"
          + name
          + "\",\"kind\":6,\"uri\":\"file:///Target.java\","
          + "\"range\":{\"start\":{\"line\":"
          + line
          + ",\"character\":0},\"end\":{\"line\":"
          + line
          + ",\"character\":9}},"
          + "\"selectionRange\":{\"start\":{\"line\":"
          + line
          + ",\"character\":1},\"end\":{\"line\":"
          + line
          + ",\"character\":8}}},"
          + "\"fromRanges\":[{\"start\":{\"line\":5,\"character\":6},\"end\":{\"line\":5,\"character\":12}}]}]";
    }

    private void respondWithError(JsonObject request, int code, String message) throws IOException {
      String response =
          "{\"jsonrpc\":\"2.0\",\"id\":"
              + request.get("id")
              + ",\"error\":{\"code\":"
              + code
              + ",\"message\":\""
              + message
              + "\"}}";
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      synchronized (serverOutput) {
        serverOutput.write(
            ("Content-Length: " + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        serverOutput.write(bytes);
        serverOutput.flush();
      }
    }

    int requestCount(String method) {
      AtomicInteger count = requestCounts.get(method);
      return count == null ? 0 : count.get();
    }

    int declarationCharacter() {
      return declarationCharacter;
    }

    int declarationLine() {
      return declarationLine;
    }

    boolean sawDocumentSymbols() {
      return sawDocumentSymbols;
    }

    private static int readContentLength(InputStream input) throws IOException {
      int contentLength = -1;
      String line;
      while ((line = readAsciiLine(input)) != null && !line.isEmpty()) {
        int separator = line.indexOf(':');
        if (separator >= 0
            && "content-length".equalsIgnoreCase(line.substring(0, separator).trim())) {
          contentLength = Integer.parseInt(line.substring(separator + 1).trim());
        }
      }
      return line == null ? -1 : contentLength;
    }

    private static String readAsciiLine(InputStream input) throws IOException {
      ByteArrayOutputStream line = new ByteArrayOutputStream();
      int value;
      while ((value = input.read()) >= 0) {
        if (value == '\n') {
          byte[] bytes = line.toByteArray();
          int length =
              bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
          return new String(bytes, 0, length, StandardCharsets.US_ASCII);
        }
        line.write(value);
      }
      return line.size() == 0 ? null : new String(line.toByteArray(), StandardCharsets.US_ASCII);
    }

    private void terminate(int code) {
      if (!alive.compareAndSet(true, false)) {
        return;
      }
      exitCode = code;
      closeQuietly(serverInput);
      closeQuietly(clientOutput);
      closeQuietly(serverOutput);
      closeQuietly(clientInput);
    }

    private static void closeQuietly(AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception ignored) {
        // Cleanup is best effort for the in-memory fake process streams.
      }
    }
  }
}
