package org.sourceanalysis.app.analysis.code.jdt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticCodeDescription;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;

/** Owns the single JDT language-server process for one JDT project session. */
final class JdtLanguageServerClient implements AutoCloseable, JdtNavigationResolver.Gateway {

  private static final Duration EARLY_EXIT_CHECK = Duration.ofMillis(100);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String PROJECT_SETTINGS_COMMAND = "java.project.getSettings";
  private static final List<String> PROJECT_SETTINGS_KEYS =
      List.of("org.eclipse.jdt.ls.core.vm.location", "org.eclipse.jdt.ls.core.classpathEntries");
  private static final Set<SymbolKind> DECLARATION_SYMBOL_KINDS =
      Set.of(SymbolKind.Class, SymbolKind.Interface, SymbolKind.Enum, SymbolKind.Struct);

  private final EffectiveEngineConfiguration.JdtConfiguration configuration;
  private final JdtProcessIsolation isolation;
  private Process process;
  private JdtServer languageServer;
  private Future<Void> listening;
  private final Set<String> openedDocuments = new java.util.LinkedHashSet<>();
  private final Map<NavigationQueryKey, CachedNavigationQuery<?>> navigationCache =
      new LinkedHashMap<>();
  private final Object diagnosticLock = new Object();
  private final List<DiagnosticEvent> diagnosticEvents = new ArrayList<>();
  private Path navigationJournal;
  private Path retainedNavigationJournal;
  private int physicalQueryCount;
  private int cacheHitCount;
  private int uniqueQueryKeyCount;

  JdtLanguageServerClient(EffectiveEngineConfiguration.JdtConfiguration configuration) {
    this(configuration, JdtProcessIsolation.system());
  }

  JdtLanguageServerClient(
      EffectiveEngineConfiguration.JdtConfiguration configuration, JdtProcessIsolation isolation) {
    this.configuration = java.util.Objects.requireNonNull(configuration, "JDT configuration");
    this.isolation = java.util.Objects.requireNonNull(isolation, "JDT process isolation");
  }

  synchronized void start(
      VerifiedJavaProject project, Path projectRoot, Path languageServerDataDirectory) {
    start(project, null, projectRoot, languageServerDataDirectory);
  }

  synchronized void start(
      JdtProjectBinding binding, Path projectRoot, Path languageServerDataDirectory) {
    Objects.requireNonNull(binding, "JDT project binding");
    start(binding.project(), binding.targetRuntime(), projectRoot, languageServerDataDirectory);
  }

  synchronized void start(
      JdtWorkspaceBinding binding,
      Path workspaceRoot,
      Map<String, Path> projectRoots,
      Path languageServerDataDirectory) {
    Objects.requireNonNull(binding, "JDT workspace binding");
    Objects.requireNonNull(workspaceRoot, "JDT workspace root");
    Map<String, Path> roots = Map.copyOf(Objects.requireNonNull(projectRoots, "JDT project roots"));
    if (process != null) {
      throw new IllegalStateException("JDT language server is already started");
    }
    try {
      if (roots.size() != binding.modules().size()
          || binding.modules().stream()
              .anyMatch(module -> !roots.containsKey(module.modulePath()))) {
        throw new IllegalArgumentException("JDT workspace project roots do not match its modules");
      }
      Files.createDirectories(languageServerDataDirectory);
      initializeNavigationState(languageServerDataDirectory);
      Process started = isolation.start(command(languageServerDataDirectory), workspaceRoot);
      process = started;
      if (started.waitFor(EARLY_EXIT_CHECK.toMillis(), TimeUnit.MILLISECONDS)) {
        throw indexFailed("JDT language server exited during startup", null);
      }

      SessionLanguageClient client = new SessionLanguageClient();
      Launcher<JdtServer> launcher =
          new Launcher.Builder<JdtServer>()
              .setLocalService(client)
              .setRemoteInterface(JdtServer.class)
              .setInput(started.getInputStream())
              .setOutput(started.getOutputStream())
              .create();
      listening = launcher.startListening();
      languageServer = launcher.getRemoteProxy();

      InitializeParams initialize = new InitializeParams();
      initialize.setRootUri(workspaceRoot.toUri().toString());
      initialize.setWorkspaceFolders(
          binding.modules().stream()
              .map(
                  module ->
                      new WorkspaceFolder(
                          roots.get(module.modulePath()).toUri().toString(),
                          binding.projectName(module.modulePath())))
              .toList());
      initialize.setInitializationOptions(
          Map.of(
              "settings",
              initializationSettings(
                  binding.modules().stream()
                      .map(JdtWorkspaceBinding.ModuleBinding::targetRuntime)
                      .toList()),
              "sourceAnalysis.snapshotId",
              binding.snapshotId()));
      awaitIndexReady(languageServer.initialize(initialize));
      languageServer.initialized(new InitializedParams());
      for (JdtWorkspaceBinding.ModuleBinding module : binding.modules()) {
        declarationProbe(module.project(), roots.get(module.modulePath()), client);
      }
    } catch (IOException failure) {
      CodeEngineException startupFailure =
          new CodeEngineException(
              CodeEngineException.JDT_TOOL_UNAVAILABLE,
              "JDT language server could not start",
              failure);
      stopAfterFailedStart(startupFailure);
      throw startupFailure;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      CodeEngineException startupFailure =
          indexFailed("JDT language-server startup was interrupted", failure);
      stopAfterFailedStart(startupFailure);
      throw startupFailure;
    } catch (ExecutionException | TimeoutException failure) {
      CodeEngineException startupFailure =
          indexFailed("JDT language server did not initialize its project index", failure);
      stopAfterFailedStart(startupFailure);
      throw startupFailure;
    } catch (CodeEngineException failure) {
      stopAfterFailedStart(failure);
      throw failure;
    }
  }

  private void start(
      VerifiedJavaProject project,
      JdtTargetRuntime targetRuntime,
      Path projectRoot,
      Path languageServerDataDirectory) {
    if (process != null) {
      throw new IllegalStateException("JDT language server is already started");
    }
    try {
      Files.createDirectories(languageServerDataDirectory);
      initializeNavigationState(languageServerDataDirectory);
      Process started = isolation.start(command(languageServerDataDirectory), projectRoot);
      process = started;
      if (started.waitFor(EARLY_EXIT_CHECK.toMillis(), TimeUnit.MILLISECONDS)) {
        throw indexFailed("JDT language server exited during startup", null);
      }

      SessionLanguageClient client = new SessionLanguageClient();
      Launcher<JdtServer> launcher =
          new Launcher.Builder<JdtServer>()
              .setLocalService(client)
              .setRemoteInterface(JdtServer.class)
              .setInput(started.getInputStream())
              .setOutput(started.getOutputStream())
              .create();
      listening = launcher.startListening();
      languageServer = launcher.getRemoteProxy();

      InitializeParams initialize = new InitializeParams();
      initialize.setRootUri(projectRoot.toUri().toString());
      initialize.setWorkspaceFolders(
          List.of(new WorkspaceFolder(projectRoot.toUri().toString(), "source-analysis-project")));
      initialize.setInitializationOptions(
          Map.of(
              "settings", initializationSettings(targetRuntime),
              "sourceAnalysis.snapshotId", project.snapshotId(),
              "sourceAnalysis.sourceLevel", project.sourceLevel()));
      awaitIndexReady(languageServer.initialize(initialize));
      languageServer.initialized(new InitializedParams());
      declarationProbe(project, projectRoot, client);
    } catch (IOException failure) {
      CodeEngineException startupFailure =
          new CodeEngineException(
              CodeEngineException.JDT_TOOL_UNAVAILABLE,
              "JDT language server could not start",
              failure);
      stopAfterFailedStart(startupFailure);
      throw startupFailure;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      CodeEngineException startupFailure =
          indexFailed("JDT language-server startup was interrupted", failure);
      stopAfterFailedStart(startupFailure);
      throw startupFailure;
    } catch (ExecutionException | TimeoutException failure) {
      CodeEngineException startupFailure =
          indexFailed("JDT language server did not initialize its project index", failure);
      stopAfterFailedStart(startupFailure);
      throw startupFailure;
    } catch (CodeEngineException failure) {
      stopAfterFailedStart(failure);
      throw failure;
    }
  }

  Map<String, String> toolVersions() {
    return Map.of(
        "jdtls", configuration.distributionIdentity(),
        "jdk", configuration.javaVersion());
  }

  JdtProcessIsolation isolation() {
    return isolation;
  }

  synchronized void openDocument(String uri, String text) {
    ensureStarted();
    if (openedDocuments.add(uri)) {
      languageServer.didOpen(
          new DidOpenTextDocumentParams(
              new TextDocumentItem(uri, "java", 1, Objects.requireNonNull(text))));
    }
  }

  /** Reads one imported project's JDT settings without treating them as a readiness verdict. */
  synchronized JsonNode readProjectSettings(String projectUri) {
    ensureStarted();
    if (projectUri == null || projectUri.isBlank()) {
      throw protocolInvalid("JDT project-settings readback requires a project URI", null);
    }
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("command", PROJECT_SETTINGS_COMMAND);
    request.put("arguments", List.of(projectUri, PROJECT_SETTINGS_KEYS));
    JsonElement response =
        awaitQuery(languageServer.executeCommand(request), "read the JDT project settings");
    JsonNode settings = jsonResponse(response, "project-settings");
    if (!settings.isObject()) {
      throw protocolInvalid("JDT project-settings response is not an object", null);
    }
    for (String key : PROJECT_SETTINGS_KEYS) {
      if (!settings.hasNonNull(key)) {
        throw protocolInvalid("JDT project-settings response omitted " + key, null);
      }
    }
    return settings;
  }

  JdtSyntaxHelperClient openSyntaxHelper(
      Path helperJar, VerifiedJavaProject project, Path projectRoot) {
    throw new CodeEngineException(
        CodeEngineException.ENGINE_CONFIGURATION_INVALID,
        "JDT syntax helper requires a verified per-module target platform; "
            + "the current single-project projection cannot provide one");
  }

  JdtSyntaxHelperClient openSyntaxHelper(
      Path helperJar,
      VerifiedJavaProject project,
      JdtTargetRuntime targetRuntime,
      Path projectRoot) {
    ensureStarted();
    Objects.requireNonNull(project, "JDT syntax helper project");
    Objects.requireNonNull(targetRuntime, "JDT syntax helper target runtime");
    Objects.requireNonNull(projectRoot, "JDT syntax helper project root");
    List<Path> sourcepathEntries =
        project.sourceRoots().stream()
            .map(projectRoot::resolve)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .toList();
    return JdtSyntaxHelperClient.start(
        configuration.javaHome(),
        helperJar,
        configuration.queryTimeout(),
        configuration.shutdownTimeout(),
        sourcepathEntries,
        project.classpath(),
        targetRuntime.targetJdkVersion(),
        targetRuntime.targetPlatformEntries());
  }

  @Override
  public synchronized List<JdtNavigationResolver.OutgoingCall> outgoingCalls(
      String uri, JdtNavigationResolver.Position position) {
    ensureStarted();
    NavigationQueryKey prepareKey =
        NavigationQueryKey.atPosition("PREPARE_CALL_HIERARCHY", uri, position);
    CallHierarchyPrepareParams prepareRequest =
        new CallHierarchyPrepareParams(new TextDocumentIdentifier(uri), lspPosition(position));
    List<CallHierarchyItem> prepared =
        cachedNavigationQuery(
            prepareKey,
            prepareRequest,
            () -> languageServer.prepareCallHierarchy(prepareRequest),
            "prepare call hierarchy",
            JdtLanguageServerClient::preparedHierarchyItems);
    if (prepared.isEmpty()) {
      return List.of();
    }
    List<JdtNavigationResolver.OutgoingCall> result = new java.util.ArrayList<>();
    for (CallHierarchyItem item : prepared) {
      NavigationQueryKey outgoingKey =
          NavigationQueryKey.forIdentity("OUTGOING_CALLS", callHierarchyIdentity(item));
      CallHierarchyOutgoingCallsParams outgoingRequest = new CallHierarchyOutgoingCallsParams(item);
      List<JdtNavigationResolver.OutgoingCall> outgoing =
          cachedNavigationQuery(
              outgoingKey,
              outgoingRequest,
              () -> languageServer.callHierarchyOutgoingCalls(outgoingRequest),
              "read outgoing calls",
              JdtLanguageServerClient::outgoingCallResults);
      result.addAll(outgoing);
    }
    return List.copyOf(result);
  }

  @Override
  public synchronized List<JdtNavigationResolver.Location> definitions(
      String uri, JdtNavigationResolver.Position position) {
    ensureStarted();
    Map<String, Object> request = navigationParams(uri, position);
    return cachedNavigationQuery(
        NavigationQueryKey.atPosition("DEFINITION", uri, position),
        request,
        () -> languageServer.definition(request),
        "resolve definition",
        JdtLanguageServerClient::rawLocations);
  }

  @Override
  public synchronized List<JdtNavigationResolver.Location> implementations(
      String uri, JdtNavigationResolver.Position position) {
    ensureStarted();
    Map<String, Object> request = navigationParams(uri, position);
    return cachedNavigationQuery(
        NavigationQueryKey.atPosition("IMPLEMENTATION", uri, position),
        request,
        () -> languageServer.implementation(request),
        "resolve implementation",
        JdtLanguageServerClient::rawLocations);
  }

  synchronized NavigationQueryStatistics queryStatistics() {
    return new NavigationQueryStatistics(physicalQueryCount, cacheHitCount, uniqueQueryKeyCount);
  }

  /** Returns the immutable callbacks observed by this language-server session, in arrival order. */
  List<DiagnosticEvent> diagnosticEvents() {
    synchronized (diagnosticLock) {
      return List.copyOf(diagnosticEvents);
    }
  }

  private void recordDiagnosticEvent(PublishDiagnosticsParams diagnostics) {
    synchronized (diagnosticLock) {
      diagnosticEvents.add(
          new DiagnosticEvent(
              diagnostics.getUri(),
              diagnostics.getVersion(),
              diagnostics.getDiagnostics(),
              diagnosticEvents.size() + 1L));
    }
  }

  private static List<Diagnostic> copyDiagnostics(List<Diagnostic> diagnostics) {
    if (diagnostics == null || diagnostics.isEmpty()) {
      return List.of();
    }
    List<Diagnostic> copies = new ArrayList<>(diagnostics.size());
    for (Diagnostic diagnostic : diagnostics) {
      copies.add(copyDiagnostic(Objects.requireNonNull(diagnostic, "JDT diagnostic")));
    }
    return List.copyOf(copies);
  }

  private static Diagnostic copyDiagnostic(Diagnostic diagnostic) {
    Diagnostic copy = new Diagnostic();
    copy.setRange(copyRange(diagnostic.getRange()));
    copy.setSeverity(diagnostic.getSeverity());
    copy.setCode(copyCode(diagnostic.getCode()));
    copy.setCodeDescription(copyCodeDescription(diagnostic.getCodeDescription()));
    copy.setSource(diagnostic.getSource());
    copy.setMessage(copyMessage(diagnostic.getMessage()));
    copy.setTags(diagnostic.getTags() == null ? null : List.copyOf(diagnostic.getTags()));
    copy.setRelatedInformation(copyRelatedInformation(diagnostic.getRelatedInformation()));
    copy.setData(diagnostic.getData() == null ? null : journalValue(diagnostic.getData()));
    return copy;
  }

  private static Range copyRange(Range range) {
    if (range == null) {
      return null;
    }
    return new Range(copyPosition(range.getStart()), copyPosition(range.getEnd()));
  }

  private static Position copyPosition(Position position) {
    return position == null ? null : new Position(position.getLine(), position.getCharacter());
  }

  private static Either<String, Integer> copyCode(Either<String, Integer> code) {
    if (code == null) {
      return null;
    }
    return code.isLeft() ? Either.forLeft(code.getLeft()) : Either.forRight(code.getRight());
  }

  private static DiagnosticCodeDescription copyCodeDescription(
      DiagnosticCodeDescription description) {
    return description == null ? null : new DiagnosticCodeDescription(description.getHref());
  }

  private static Either<String, MarkupContent> copyMessage(Either<String, MarkupContent> message) {
    if (message == null) {
      return null;
    }
    if (message.isLeft()) {
      return Either.forLeft(message.getLeft());
    }
    MarkupContent markup = message.getRight();
    return Either.forRight(
        markup == null ? null : new MarkupContent(markup.getKind(), markup.getValue()));
  }

  private static List<DiagnosticRelatedInformation> copyRelatedInformation(
      List<DiagnosticRelatedInformation> relatedInformation) {
    if (relatedInformation == null || relatedInformation.isEmpty()) {
      return List.of();
    }
    List<DiagnosticRelatedInformation> copies = new ArrayList<>(relatedInformation.size());
    for (DiagnosticRelatedInformation related : relatedInformation) {
      DiagnosticRelatedInformation value =
          Objects.requireNonNull(related, "JDT diagnostic related information");
      Location location = value.getLocation();
      copies.add(
          new DiagnosticRelatedInformation(
              location == null
                  ? null
                  : new Location(location.getUri(), copyRange(location.getRange())),
              value.getMessage()));
    }
    return List.copyOf(copies);
  }

  synchronized Path retainedNavigationJournal() {
    return retainedNavigationJournal;
  }

  @Override
  public synchronized void close() {
    CodeEngineException failure = null;
    if (languageServer != null) {
      try {
        languageServer
            .shutdown()
            .get(configuration.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
        languageServer.exit();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        failure = indexFailed("JDT language-server shutdown was interrupted", interrupted);
      } catch (ExecutionException | TimeoutException shutdownFailure) {
        failure = indexFailed("JDT language server did not shut down cleanly", shutdownFailure);
      }
    }
    if (process != null && !isolation.stop(process, configuration.shutdownTimeout())) {
      failure = indexFailed("JDT language-server process remained alive after shutdown", null);
    }
    if (listening != null) {
      listening.cancel(true);
    }
    try {
      retainNavigationDiagnostics();
    } catch (CodeEngineException diagnosticFailure) {
      if (failure == null) {
        failure = diagnosticFailure;
      } else {
        failure.addSuppressed(diagnosticFailure);
      }
    }
    process = null;
    languageServer = null;
    listening = null;
    openedDocuments.clear();
    releaseNavigationCache();
    if (failure != null) {
      throw failure;
    }
  }

  private List<String> command(Path languageServerDataDirectory) {
    return List.of(
        configuration.javaHome().resolve("bin").resolve("java").toString(),
        "-Declipse.application=org.eclipse.jdt.ls.core.id1",
        "-Dosgi.bundles.defaultStartLevel=4",
        "-Declipse.product=org.eclipse.jdt.ls.core.product",
        "-jar",
        configuration.launcherJar().toString(),
        "-configuration",
        configuration.platformConfiguration().toString(),
        "-data",
        languageServerDataDirectory.toString());
  }

  private void stopAfterFailedStart(CodeEngineException startupFailure) {
    boolean stopped = true;
    if (process != null) {
      stopped = isolation.stop(process, configuration.shutdownTimeout());
      if (!stopped) {
        startupFailure.addSuppressed(
            indexFailed(
                "JDT language-server process remained alive after failed startup cleanup"
                    + " (stop=false)",
                null));
      }
    }
    if (stopped) {
      process = null;
    }
    languageServer = null;
    listening = null;
    resetNavigationState();
  }

  private void awaitIndexReady(Future<?> initialization)
      throws InterruptedException, ExecutionException, TimeoutException {
    long deadline = System.nanoTime() + configuration.startupTimeout().toNanos();
    while (true) {
      long remainingNanos = deadline - System.nanoTime();
      if (remainingNanos <= 0L) {
        throw new TimeoutException("JDT initialization deadline elapsed");
      }
      long waitMillis = Math.max(1L, Math.min(100L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
      try {
        initialization.get(waitMillis, TimeUnit.MILLISECONDS);
        return;
      } catch (TimeoutException waiting) {
        if (process == null || !process.isAlive()) {
          throw indexFailed("JDT language server exited before initialization", waiting);
        }
      }
    }
  }

  private void declarationProbe(
      VerifiedJavaProject project, Path projectRoot, SessionLanguageClient client)
      throws IOException, InterruptedException, ExecutionException, TimeoutException {
    DeclarationProbeTarget target = null;
    long deadline = System.nanoTime() + configuration.startupTimeout().toNanos();
    String sourceEntry =
        project.sourceEntries().stream()
            .filter(path -> path.endsWith(".java"))
            .findFirst()
            .orElseThrow(
                () -> indexFailed("JDT project contains no Java source for readiness", null));
    Path sourcePath = projectRoot.resolve(sourceEntry);
    String uri = sourcePath.toUri().toString();
    openDocument(uri, Files.readString(sourcePath, java.nio.charset.StandardCharsets.UTF_8));
    while (target == null && System.nanoTime() < deadline) {
      if (client.serviceReady()) {
        return;
      }
      JsonElement symbols =
          languageServer
              .documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(uri)))
              .get(configuration.queryTimeout().toMillis(), TimeUnit.MILLISECONDS);
      Position position = firstDeclarationPosition(symbols, uri);
      if (position != null) {
        target = new DeclarationProbeTarget(uri, position);
      }
      if (target == null) {
        if (process == null || !process.isAlive()) {
          throw indexFailed(
              "JDT language server exited before its declaration index was ready", null);
        }
        Thread.sleep(100L);
      }
    }
    if (target == null) {
      throw indexFailed("JDT returned no declaration symbol for readiness probe", null);
    }
    JsonElement declaration =
        languageServer
            .declaration(navigationParams(target.uri(), position(target.position())))
            .get(configuration.queryTimeout().toMillis(), TimeUnit.MILLISECONDS);
    if (rawLocations(declaration).isEmpty()) {
      throw indexFailed("JDT declaration readiness probe returned no declaration", null);
    }
  }

  private static Position firstDeclarationPosition(JsonElement symbols, String documentUri) {
    if (symbols == null || symbols.isJsonNull()) {
      return null;
    }
    JsonNode root;
    try {
      root = JSON.readTree(symbols.toString());
    } catch (IOException invalidJson) {
      return null;
    }
    return firstDeclarationPosition(root, documentUri);
  }

  private static Position firstDeclarationPosition(JsonNode symbol, String documentUri) {
    if (symbol == null || symbol.isNull()) {
      return null;
    }
    if (symbol.isArray()) {
      for (JsonNode child : symbol) {
        Position position = firstDeclarationPosition(child, documentUri);
        if (position != null) {
          return position;
        }
      }
      return null;
    }
    int kind = symbol.path("kind").asInt(-1);
    boolean declarationKind =
        DECLARATION_SYMBOL_KINDS.stream().anyMatch(value -> value.getValue() == kind);
    JsonNode selection = symbol.path("selectionRange");
    if (!selection.isObject()) {
      JsonNode location = symbol.path("location");
      if (documentUri.equals(location.path("uri").asText())) {
        selection = location.path("range");
      }
    }
    if (declarationKind && selection.path("start").isObject()) {
      return new Position(
          selection.path("start").path("line").asInt(),
          selection.path("start").path("character").asInt());
    }
    JsonNode children = symbol.path("children");
    if (children.isArray()) {
      for (JsonNode child : children) {
        Position position = firstDeclarationPosition(child, documentUri);
        if (position != null) {
          return position;
        }
      }
    }
    return null;
  }

  private record DeclarationProbeTarget(String uri, Position position) {}

  private static Map<String, Object> initializationSettings(JdtTargetRuntime targetRuntime) {
    return targetRuntime == null
        ? initializationSettings(List.of())
        : initializationSettings(List.of(targetRuntime));
  }

  private static Map<String, Object> initializationSettings(List<JdtTargetRuntime> targetRuntimes) {
    List<JdtTargetRuntime> runtimes = List.copyOf(Objects.requireNonNull(targetRuntimes));
    Map<String, Object> configuration =
        new LinkedHashMap<>(Map.of("updateBuildConfiguration", "disabled"));
    if (!runtimes.isEmpty()) {
      Map<String, Path> homesByExecutionEnvironment = new LinkedHashMap<>();
      for (JdtTargetRuntime runtime : runtimes) {
        Path prior =
            homesByExecutionEnvironment.putIfAbsent(
                runtime.executionEnvironmentName(), runtime.targetJdkHome());
        if (prior != null && !prior.equals(runtime.targetJdkHome())) {
          throw new IllegalArgumentException(
              "JDT runtime settings cannot map one execution environment to multiple JDK homes");
        }
      }
      configuration.put(
          "runtimes",
          homesByExecutionEnvironment.entrySet().stream()
              .map(
                  entry ->
                      Map.of(
                          "name",
                          entry.getKey(),
                          "path",
                          entry.getValue().toString(),
                          "default",
                          entry
                              .getKey()
                              .equals(homesByExecutionEnvironment.keySet().iterator().next())))
              .toList());
    }
    return Map.of(
        "java",
        Map.of(
            "autobuild", Map.of("enabled", false),
            "import",
                Map.of(
                    "maven", Map.of("enabled", false),
                    "gradle",
                        Map.of("enabled", false, "annotationProcessing", Map.of("enabled", false))),
            "configuration", Map.copyOf(configuration),
            "maven", Map.of("downloadSources", false),
            "eclipse", Map.of("downloadSources", false)));
  }

  private void ensureStarted() {
    if (languageServer == null || process == null || !process.isAlive()) {
      throw queryFailed("JDT language server is not available for navigation", null);
    }
  }

  private <T> T awaitQuery(java.util.concurrent.CompletableFuture<T> query, String operation) {
    try {
      return query.get(configuration.queryTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw queryFailed(
          "JDT navigation was interrupted while attempting to " + operation, interrupted);
    } catch (ExecutionException | TimeoutException failure) {
      throw queryFailed("JDT could not " + operation, failure);
    }
  }

  private static JsonNode jsonResponse(JsonElement response, String responseDescription) {
    if (response == null || response.isJsonNull()) {
      throw protocolInvalid("JDT returned no " + responseDescription + " response", null);
    }
    try {
      return JSON.readTree(response.toString());
    } catch (IOException invalidJson) {
      throw protocolInvalid("JDT returned malformed " + responseDescription + " JSON", invalidJson);
    }
  }

  @SuppressWarnings("unchecked")
  private <S, T> T cachedNavigationQuery(
      NavigationQueryKey key,
      Object request,
      java.util.function.Supplier<CompletableFuture<S>> query,
      String operationDescription,
      Function<S, T> responseNormalizer) {
    CachedNavigationQuery<T> cached = (CachedNavigationQuery<T>) navigationCache.get(key);
    if (cached != null) {
      cacheHitCount++;
      appendCacheHit(key);
    } else {
      physicalQueryCount++;
      uniqueQueryKeyCount++;
      long startedAtNanos = System.nanoTime();
      CompletableFuture<S> rawResponse;
      try {
        rawResponse = query.get();
      } catch (RuntimeException failure) {
        CodeEngineException finalFailure = finalQueryFailure(operationDescription, failure);
        appendExchange(
            key,
            request,
            null,
            finalFailure,
            elapsedNanos(startedAtNanos, System.nanoTime()));
        navigationCache.put(key, CachedNavigationQuery.failure(finalFailure));
        throw finalFailure;
      }
      cached =
          CachedNavigationQuery.pending(
              new PendingNavigationQuery(
                  rawResponse.handle(
                      (response, failure) ->
                          new NavigationQueryCompletion(response, failure, System.nanoTime())),
                  startedAtNanos));
      navigationCache.put(key, cached);
    }
    if (cached.pending() == null) {
      return cached.replay();
    }
    return observePendingNavigationQuery(
        key, request, cached.pending(), operationDescription, responseNormalizer);
  }

  @SuppressWarnings("unchecked")
  private <S, T> T observePendingNavigationQuery(
      NavigationQueryKey key,
      Object request,
      PendingNavigationQuery pending,
      String operationDescription,
      Function<S, T> responseNormalizer) {
    NavigationQueryCompletion completion;
    try {
      completion =
          pending
              .completion()
              .get(configuration.queryTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      CodeEngineException waitFailure =
          queryFailed(
              "JDT navigation was interrupted while attempting to " + operationDescription,
              interrupted);
      appendWaitInterrupted(key, pending, waitFailure);
      throw waitFailure;
    } catch (TimeoutException timeout) {
      CodeEngineException waitFailure = queryFailed("JDT could not " + operationDescription, timeout);
      appendWaitTimeout(key, pending, waitFailure);
      throw waitFailure;
    } catch (ExecutionException completionFailure) {
      CodeEngineException finalFailure = finalQueryFailure(operationDescription, completionFailure);
      appendExchange(
          key,
          request,
          null,
          finalFailure,
          elapsedNanos(pending.startedAtNanos(), System.nanoTime()));
      navigationCache.put(key, CachedNavigationQuery.failure(finalFailure));
      throw finalFailure;
    }

    long elapsedNanos = elapsedNanos(pending.startedAtNanos(), completion.completedAtNanos());
    try {
      if (completion.failure() != null) {
        throw finalQueryFailure(operationDescription, completion.failure());
      }
      S rawResponse = (S) completion.rawResponse();
      T response = responseNormalizer.apply(rawResponse);
      appendExchange(key, request, rawResponse, null, elapsedNanos);
      navigationCache.put(key, CachedNavigationQuery.success(response));
      return response;
    } catch (CodeEngineException failure) {
      appendExchange(key, request, null, failure, elapsedNanos);
      navigationCache.put(key, CachedNavigationQuery.failure(failure));
      throw failure;
    }
  }

  private void initializeNavigationState(Path languageServerDataDirectory) throws IOException {
    navigationCache.clear();
    physicalQueryCount = 0;
    cacheHitCount = 0;
    uniqueQueryKeyCount = 0;
    retainedNavigationJournal = null;
    navigationJournal =
        languageServerDataDirectory.resolve("source-analysis-navigation-query-journal.jsonl");
    Files.deleteIfExists(navigationJournal);
  }

  private void resetNavigationState() {
    navigationCache.clear();
    physicalQueryCount = 0;
    cacheHitCount = 0;
    uniqueQueryKeyCount = 0;
    navigationJournal = null;
    retainedNavigationJournal = null;
  }

  private void releaseNavigationCache() {
    navigationCache.clear();
    navigationJournal = null;
  }

  private void retainNavigationDiagnostics() {
    if (navigationJournal == null) {
      return;
    }
    var summary = JSON.createObjectNode();
    summary.put("kind", "SESSION_SUMMARY");
    summary.put("physicalQueryCount", physicalQueryCount);
    summary.put("cacheHitCount", cacheHitCount);
    summary.put("uniqueQueryKeyCount", uniqueQueryKeyCount);
    appendJournalRecord(summary, null);
    try {
      Path retained = Files.createTempFile("source-analysis-jdt-navigation-", ".jsonl");
      Files.copy(navigationJournal, retained, StandardCopyOption.REPLACE_EXISTING);
      retainedNavigationJournal = retained;
    } catch (IOException failure) {
      throw queryFailed("JDT navigation journal could not be retained", failure);
    }
  }

  private void appendExchange(
      NavigationQueryKey key,
      Object request,
      Object response,
      CodeEngineException failure,
      long elapsedNanos) {
    var record = JSON.createObjectNode();
    record.put("kind", "QUERY_EXCHANGE");
    record.put("queryKey", key.display());
    record.put("operation", key.operation());
    record.put("outcome", failure == null ? "SUCCESS" : "FAILURE");
    record.put("elapsedNanos", elapsedNanos);
    record.set("request", journalValue(request));
    if (failure == null) {
      record.set("response", journalValue(response));
    } else {
      record.put("failureCode", failure.code());
      record.put("failureMessage", failure.getMessage());
      appendFailureCause(record, failure);
    }
    appendJournalRecord(record, failure);
  }

  private void appendWaitTimeout(
      NavigationQueryKey key, PendingNavigationQuery pending, CodeEngineException failure) {
    appendWaitFailure("WAIT_TIMEOUT", key, pending, failure);
  }

  private void appendWaitInterrupted(
      NavigationQueryKey key, PendingNavigationQuery pending, CodeEngineException failure) {
    appendWaitFailure("WAIT_INTERRUPTED", key, pending, failure);
  }

  private void appendWaitFailure(
      String kind, NavigationQueryKey key, PendingNavigationQuery pending, CodeEngineException failure) {
    var record = JSON.createObjectNode();
    record.put("kind", kind);
    record.put("queryKey", key.display());
    record.put("operation", key.operation());
    record.put("elapsedNanos", elapsedNanos(pending.startedAtNanos(), System.nanoTime()));
    record.put("failureCode", failure.code());
    record.put("failureMessage", failure.getMessage());
    appendFailureCause(record, failure);
    appendJournalRecord(record, failure);
  }

  private static void appendFailureCause(
      com.fasterxml.jackson.databind.node.ObjectNode record, CodeEngineException failure) {
    Throwable cause = failure.getCause();
    if (cause != null) {
      record.put("failureCause", cause.getClass().getName() + ": " + cause.getMessage());
    }
  }

  private static CodeEngineException finalQueryFailure(
      String operationDescription, Throwable failure) {
    return failure instanceof CodeEngineException codeEngineFailure
        ? codeEngineFailure
        : queryFailed("JDT could not " + operationDescription, failure);
  }

  private static long elapsedNanos(long startedAtNanos, long endedAtNanos) {
    return Math.max(0L, endedAtNanos - startedAtNanos);
  }

  private void appendCacheHit(NavigationQueryKey key) {
    var record = JSON.createObjectNode();
    record.put("kind", "CACHE_HIT");
    record.put("queryKey", key.display());
    record.put("operation", key.operation());
    appendJournalRecord(record, null);
  }

  private void appendJournalRecord(JsonNode record, CodeEngineException queryFailure) {
    if (navigationJournal == null) {
      throw queryFailed("JDT navigation journal is not initialized", null);
    }
    try {
      Files.writeString(
          navigationJournal,
          JSON.writeValueAsString(record) + '\n',
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.APPEND);
    } catch (IOException journalFailure) {
      if (queryFailure != null) {
        queryFailure.addSuppressed(journalFailure);
        return;
      }
      throw queryFailed("JDT navigation journal could not be written", journalFailure);
    }
  }

  private static JsonNode journalValue(Object value) {
    if (value == null) {
      return JSON.nullNode();
    }
    if (value instanceof JsonElement jsonElement) {
      try {
        return JSON.readTree(jsonElement.toString());
      } catch (IOException invalidJson) {
        throw queryFailed("JDT navigation result could not be journaled", invalidJson);
      }
    }
    return JSON.valueToTree(value);
  }

  private static String callHierarchyIdentity(CallHierarchyItem item) {
    if (item == null) {
      throw queryFailed("JDT returned a null call-hierarchy item", null);
    }
    try {
      return JSON.writeValueAsString(item);
    } catch (IOException failure) {
      throw queryFailed("JDT call-hierarchy item could not be identified", failure);
    }
  }

  private static List<CallHierarchyItem> preparedHierarchyItems(List<CallHierarchyItem> items) {
    if (items == null) {
      return List.of();
    }
    for (CallHierarchyItem item : items) {
      if (item == null) {
        throw queryFailed("JDT returned a null call-hierarchy item", null);
      }
      callHierarchyIdentity(item);
    }
    return List.copyOf(items);
  }

  private static List<JdtNavigationResolver.OutgoingCall> outgoingCallResults(
      List<CallHierarchyOutgoingCall> outgoing) {
    if (outgoing == null) {
      return List.of();
    }
    List<JdtNavigationResolver.OutgoingCall> results = new java.util.ArrayList<>();
    for (CallHierarchyOutgoingCall edge : outgoing) {
      if (edge == null || edge.getTo() == null) {
        throw queryFailed("JDT returned an outgoing call without a target", null);
      }
      JdtNavigationResolver.Location target = location(edge.getTo());
      List<JdtNavigationResolver.TextRange> fromRanges =
          edge.getFromRanges() == null
              ? List.of()
              : edge.getFromRanges().stream().map(JdtLanguageServerClient::range).toList();
      results.add(new JdtNavigationResolver.OutgoingCall(target, fromRanges));
    }
    return List.copyOf(results);
  }

  private static List<JdtNavigationResolver.Location> rawLocations(JsonElement result) {
    JsonNode root;
    try {
      root = result == null ? null : JSON.readTree(result.toString());
    } catch (IOException invalidJson) {
      throw queryFailed("JDT returned malformed navigation JSON", invalidJson);
    }
    if (root == null || root.isNull()) {
      return List.of();
    }
    if (root.isObject() && (root.has("left") || root.has("right"))) {
      JsonNode left = root.path("left");
      root = left.isArray() ? left : root.path("right");
      if (root.isMissingNode() || root.isNull()) {
        return List.of();
      }
    }
    List<JdtNavigationResolver.Location> resultLocations = new java.util.ArrayList<>();
    if (root.isArray()) {
      root.forEach(
          value -> {
            if (!value.isNull()) {
              resultLocations.add(rawLocation(value));
            }
          });
    } else if (root.isObject()) {
      resultLocations.add(rawLocation(root));
    } else {
      throw queryFailed("JDT returned an unsupported navigation result", null);
    }
    return List.copyOf(resultLocations);
  }

  private static Map<String, Object> navigationParams(
      String uri, JdtNavigationResolver.Position position) {
    return Map.of(
        "textDocument", Map.of("uri", uri),
        "position", Map.of("line", position.line(), "character", position.character()));
  }

  private static JdtNavigationResolver.Location rawLocation(JsonNode value) {
    if (value.hasNonNull("targetUri")) {
      String uri = value.path("targetUri").asText();
      JdtNavigationResolver.TextRange target = rawRange(value.path("targetRange"));
      JdtNavigationResolver.TextRange selection = rawRange(value.path("targetSelectionRange"));
      return new JdtNavigationResolver.Location(uri, target, selection, rawDisplay(uri, selection));
    }
    if (value.hasNonNull("uri")) {
      String uri = value.path("uri").asText();
      JdtNavigationResolver.TextRange target = rawRange(value.path("range"));
      return new JdtNavigationResolver.Location(uri, target, target, rawDisplay(uri, target));
    }
    throw queryFailed("JDT returned an incomplete navigation location: " + value, null);
  }

  private static JdtNavigationResolver.TextRange rawRange(JsonNode value) {
    if (!value.isObject() || !value.path("start").isObject() || !value.path("end").isObject()) {
      throw queryFailed("JDT returned an incomplete navigation range", null);
    }
    return new JdtNavigationResolver.TextRange(
        new JdtNavigationResolver.Position(
            value.path("start").path("line").asInt(-1),
            value.path("start").path("character").asInt(-1)),
        new JdtNavigationResolver.Position(
            value.path("end").path("line").asInt(-1),
            value.path("end").path("character").asInt(-1)));
  }

  private static String rawDisplay(String uri, JdtNavigationResolver.TextRange selectionRange) {
    return uri + '#' + selectionRange.start().line() + ':' + selectionRange.start().character();
  }

  private static JdtNavigationResolver.Location location(CallHierarchyItem value) {
    if (value.getUri() == null || value.getRange() == null || value.getSelectionRange() == null) {
      throw queryFailed("JDT returned an incomplete call-hierarchy target", null);
    }
    String display =
        value.getDetail() == null || value.getDetail().isBlank()
            ? value.getName()
            : value.getDetail() + '.' + value.getName();
    return new JdtNavigationResolver.Location(
        value.getUri(), range(value.getRange()), range(value.getSelectionRange()), display);
  }

  private static JdtNavigationResolver.Position position(Position value) {
    return new JdtNavigationResolver.Position(value.getLine(), value.getCharacter());
  }

  private static Position lspPosition(JdtNavigationResolver.Position value) {
    return new Position(value.line(), value.character());
  }

  private static JdtNavigationResolver.TextRange range(org.eclipse.lsp4j.Range value) {
    if (value == null || value.getStart() == null || value.getEnd() == null) {
      throw queryFailed("JDT returned an incomplete source range", null);
    }
    return new JdtNavigationResolver.TextRange(
        position(value.getStart()), position(value.getEnd()));
  }

  private static CodeEngineException indexFailed(String detail, Throwable cause) {
    return new CodeEngineException(CodeEngineException.JDT_INDEX_FAILED, detail, cause);
  }

  private static CodeEngineException queryFailed(String detail, Throwable cause) {
    return new CodeEngineException(CodeEngineException.JDT_QUERY_FAILED, detail, cause);
  }

  private static CodeEngineException protocolInvalid(String detail, Throwable cause) {
    return new CodeEngineException(CodeEngineException.JDT_PROTOCOL_INVALID, detail, cause);
  }

  record NavigationQueryStatistics(
      int physicalQueryCount, int cacheHitCount, int uniqueQueryKeyCount) {}

  private record NavigationQueryKey(String operation, String identity) {

    private NavigationQueryKey {
      Objects.requireNonNull(operation, "navigation operation");
      Objects.requireNonNull(identity, "navigation identity");
    }

    static NavigationQueryKey atPosition(
        String operation, String uri, JdtNavigationResolver.Position position) {
      Objects.requireNonNull(uri, "navigation URI");
      Objects.requireNonNull(position, "navigation position");
      return forIdentity(operation, uri + '#' + position.line() + ':' + position.character());
    }

    static NavigationQueryKey forIdentity(String operation, String identity) {
      return new NavigationQueryKey(operation, identity);
    }

    String display() {
      return operation + '|' + identity;
    }
  }

  private record CachedNavigationQuery<T>(
      T value, CodeEngineException failure, PendingNavigationQuery pending) {

    static <T> CachedNavigationQuery<T> success(T value) {
      return new CachedNavigationQuery<>(value, null, null);
    }

    static <T> CachedNavigationQuery<T> failure(CodeEngineException failure) {
      return new CachedNavigationQuery<>(null, Objects.requireNonNull(failure), null);
    }

    static <T> CachedNavigationQuery<T> pending(PendingNavigationQuery pending) {
      return new CachedNavigationQuery<>(null, null, Objects.requireNonNull(pending));
    }

    T replay() {
      if (pending != null) {
        throw new IllegalStateException("pending navigation query must be observed before replay");
      }
      if (failure != null) {
        throw failure;
      }
      return value;
    }
  }

  private record PendingNavigationQuery(
      CompletableFuture<NavigationQueryCompletion> completion, long startedAtNanos) {

    private PendingNavigationQuery {
      Objects.requireNonNull(completion, "navigation query completion");
    }
  }

  private record NavigationQueryCompletion(
      Object rawResponse, Throwable failure, long completedAtNanos) {}

  /** Minimal JDT protocol used by this session, with ambiguous location arrays kept as raw JSON. */
  private interface JdtServer {

    @JsonRequest("initialize")
    CompletableFuture<InitializeResult> initialize(InitializeParams params);

    @JsonNotification("initialized")
    void initialized(InitializedParams params);

    @JsonRequest("shutdown")
    CompletableFuture<Object> shutdown();

    @JsonNotification("exit")
    void exit();

    @JsonNotification(value = "textDocument/didOpen", useSegment = false)
    void didOpen(DidOpenTextDocumentParams params);

    @JsonRequest(value = "textDocument/documentSymbol", useSegment = false)
    CompletableFuture<JsonElement> documentSymbol(DocumentSymbolParams params);

    @JsonRequest(value = "textDocument/declaration", useSegment = false)
    CompletableFuture<JsonElement> declaration(Map<String, Object> params);

    @JsonRequest(value = "textDocument/definition", useSegment = false)
    CompletableFuture<JsonElement> definition(Map<String, Object> params);

    @JsonRequest(value = "textDocument/implementation", useSegment = false)
    CompletableFuture<JsonElement> implementation(Map<String, Object> params);

    @JsonRequest(value = "workspace/executeCommand", useSegment = false)
    CompletableFuture<JsonElement> executeCommand(Map<String, Object> params);

    @JsonRequest(value = "textDocument/prepareCallHierarchy", useSegment = false)
    CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(
        CallHierarchyPrepareParams params);

    @JsonRequest(value = "callHierarchy/outgoingCalls", useSegment = false)
    CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
        CallHierarchyOutgoingCallsParams params);
  }

  private final class SessionLanguageClient implements LanguageClient {

    private volatile boolean serviceReady;

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
      recordDiagnosticEvent(Objects.requireNonNull(diagnostics, "JDT diagnostic callback"));
    }

    @Override
    public void showMessage(MessageParams message) {}

    @Override
    public java.util.concurrent.CompletableFuture<MessageActionItem> showMessageRequest(
        org.eclipse.lsp4j.ShowMessageRequestParams request) {
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {}

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configuration) {
      int count = configuration.getItems() == null ? 0 : configuration.getItems().size();
      return CompletableFuture.completedFuture(java.util.Collections.nCopies(count, Map.of()));
    }

    @JsonNotification("language/status")
    public void status(Object value) {
      if (String.valueOf(value).contains("ServiceReady")) {
        serviceReady = true;
      }
    }

    @JsonNotification("language/eventNotification")
    public void eventNotification(Object value) {}

    boolean serviceReady() {
      return serviceReady;
    }
  }

  record DiagnosticEvent(String uri, Integer version, List<Diagnostic> diagnostics, long sequence) {

    DiagnosticEvent {
      if (uri == null || uri.isBlank() || sequence <= 0) {
        throw new IllegalArgumentException("JDT diagnostic event is incomplete");
      }
      diagnostics = copyDiagnostics(diagnostics);
    }

    @Override
    public List<Diagnostic> diagnostics() {
      return copyDiagnostics(diagnostics);
    }
  }
}
