package org.sourceanalysis.app.analysis.code.jdt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
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
  private static final Set<SymbolKind> DECLARATION_SYMBOL_KINDS =
      Set.of(SymbolKind.Class, SymbolKind.Interface, SymbolKind.Enum, SymbolKind.Struct);

  private final EffectiveEngineConfiguration.JdtConfiguration configuration;
  private final JdtProcessIsolation isolation;
  private Process process;
  private JdtServer languageServer;
  private Future<Void> listening;
  private final Set<String> openedDocuments = new java.util.LinkedHashSet<>();

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
    if (process != null) {
      throw new IllegalStateException("JDT language server is already started");
    }
    try {
      Files.createDirectories(languageServerDataDirectory);
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
              "settings", initializationSettings(),
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

  JdtSyntaxHelperClient openSyntaxHelper(
      Path helperJar, VerifiedJavaProject project, Path projectRoot) {
    return JdtSyntaxHelperClient.start(
        configuration.javaHome(),
        helperJar,
        configuration.queryTimeout(),
        configuration.shutdownTimeout(),
        project.sourceRoots().stream().map(projectRoot::resolve).toList(),
        project.classpath());
  }

  @Override
  public synchronized List<JdtNavigationResolver.OutgoingCall> outgoingCalls(
      String uri, JdtNavigationResolver.Position position) {
    ensureStarted();
    var prepared =
        awaitQuery(
            languageServer.prepareCallHierarchy(
                new CallHierarchyPrepareParams(
                    new TextDocumentIdentifier(uri), lspPosition(position))),
            "prepare call hierarchy");
    if (prepared == null || prepared.isEmpty()) {
      return List.of();
    }
    List<JdtNavigationResolver.OutgoingCall> result = new java.util.ArrayList<>();
    for (CallHierarchyItem item : prepared) {
      List<CallHierarchyOutgoingCall> outgoing =
          awaitQuery(
              languageServer.callHierarchyOutgoingCalls(new CallHierarchyOutgoingCallsParams(item)),
              "read outgoing calls");
      if (outgoing == null) {
        continue;
      }
      for (CallHierarchyOutgoingCall edge : outgoing) {
        if (edge == null || edge.getTo() == null) {
          throw queryFailed("JDT returned an outgoing call without a target", null);
        }
        JdtNavigationResolver.Location target = location(edge.getTo());
        List<JdtNavigationResolver.TextRange> fromRanges =
            edge.getFromRanges() == null
                ? List.of()
                : edge.getFromRanges().stream().map(JdtLanguageServerClient::range).toList();
        result.add(new JdtNavigationResolver.OutgoingCall(target, fromRanges));
      }
    }
    return List.copyOf(result);
  }

  @Override
  public synchronized List<JdtNavigationResolver.Location> definitions(
      String uri, JdtNavigationResolver.Position position) {
    ensureStarted();
    return rawLocations(
        awaitQuery(
            languageServer.definition(navigationParams(uri, position)), "resolve definition"));
  }

  @Override
  public synchronized List<JdtNavigationResolver.Location> implementations(
      String uri, JdtNavigationResolver.Position position) {
    ensureStarted();
    return rawLocations(
        awaitQuery(
            languageServer.implementation(navigationParams(uri, position)),
            "resolve implementation"));
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
    process = null;
    languageServer = null;
    listening = null;
    openedDocuments.clear();
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
                "JDT language-server process remained alive after failed startup cleanup (stop=false)",
                null));
      }
    }
    if (stopped) {
      process = null;
    }
    languageServer = null;
    listening = null;
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

  private static Map<String, Object> initializationSettings() {
    return Map.of(
        "java",
        Map.of(
            "autobuild", Map.of("enabled", false),
            "import",
                Map.of(
                    "maven", Map.of("enabled", false),
                    "gradle",
                        Map.of("enabled", false, "annotationProcessing", Map.of("enabled", false))),
            "configuration", Map.of("updateBuildConfiguration", "disabled"),
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

    @JsonRequest(value = "textDocument/prepareCallHierarchy", useSegment = false)
    CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(
        CallHierarchyPrepareParams params);

    @JsonRequest(value = "callHierarchy/outgoingCalls", useSegment = false)
    CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
        CallHierarchyOutgoingCallsParams params);
  }

  private static final class SessionLanguageClient implements LanguageClient {

    private volatile boolean serviceReady;

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {}

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
}
