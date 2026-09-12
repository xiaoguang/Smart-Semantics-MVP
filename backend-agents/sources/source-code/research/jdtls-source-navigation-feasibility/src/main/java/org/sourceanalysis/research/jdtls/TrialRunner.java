package org.sourceanalysis.research.jdtls;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.Range;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

/** Executes the two approved entries in one isolated JDT LS stdio session. */
final class TrialRunner {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String COMMIT = "8c30ce7861570458920175e200bb2a6442713580";
  private static final Duration LIMIT = Duration.ofMinutes(10);
  private static final int METHOD_LIMIT = 30;
  private static final int DEPTH_LIMIT = 4;

  void run(Path trialRoot, String requestedCases) throws Exception {
    List<EntrySpec> entries = EntrySpec.parse(requestedCases);
    if (entries.size() != 2 || !"registration".equals(entries.get(0).name()) || !"financial".equals(entries.get(1).name())) {
      throw new IllegalArgumentException("cases must be registration,financial in that order");
    }
    JsonNode toolManifest = readToolManifest(trialRoot);
    Projection projection = Projection.loadAndVerify(trialRoot, COMMIT);
    ToolEvidence toolEvidence = ToolEvidence.verify(trialRoot, toolManifest);
    RequestJournal journal = new RequestJournal(trialRoot.resolve("runtime/logs/requests.jsonl"));
    RecordingClient client = new RecordingClient();
    try (JdtSession session = JdtSession.open(trialRoot, toolManifest, client, journal)) {
      session.initialize(projection.root().getParent());
      session.awaitQuiescence(LIMIT);
      CaseResult registration = runCase(session, projection, entries.get(0), trialRoot, journal);
      if (!registration.primaryPassed()) {
        writeVerdict(trialRoot, "FAIL", "registration primary gate failed; financial phase was not entered", toolEvidence, client);
        return;
      }
      CaseResult financial = runCase(session, projection, entries.get(1), trialRoot, journal);
      writeVerdict(
          trialRoot,
          financial.primaryPassed() ? "PASS" : "FAIL",
          financial.primaryPassed() ? "both primary packet gates passed" : "financial primary gate failed",
          toolEvidence,
          client);
    } finally {
      journal.close();
      updateRuntimeStatus(trialRoot, toolManifest);
    }
  }

  private static CaseResult runCase(
      JdtSession session, Projection projection, EntrySpec entry, Path trialRoot, RequestJournal journal)
      throws Exception {
    Path output = trialRoot.resolve("results").resolve(entry.name());
    Files.createDirectories(output);
    int requestOffset = journal.size();
    ObjectNode packet = new GenericWalker(session, projection).walk(entry, METHOD_LIMIT, DEPTH_LIMIT, LIMIT);
    Path packetPath = output.resolve("packet.json");
    atomicJson(packetPath, packet);
    String digest = sha256(Files.readAllBytes(packetPath));
    Files.writeString(output.resolve("packet.sha256"), digest + "  packet.json\n", StandardCharsets.UTF_8);
    journal.copyFrom(requestOffset, output.resolve("requests.jsonl"));
    atomicJson(output.resolve("diagnostics.json"), session.client().diagnostics());
    boolean accepted = PacketOracleCheck.check(packetPath, checkerPath(entry.name()));
    Files.writeString(output.resolve("primary-gate.txt"), accepted ? "PASS\n" : "FAIL\n", StandardCharsets.UTF_8);
    return new CaseResult(accepted, packetPath, digest);
  }

  private static Path checkerPath(String name) {
    return Path.of("checks", name + "-oracle.json");
  }

  private static JsonNode readToolManifest(Path trialRoot) throws IOException {
    Path manifest = trialRoot.resolve("tool-manifest.json");
    JsonNode root = JSON.readTree(Files.readString(manifest, StandardCharsets.UTF_8));
    if (!root.isObject()) {
      throw new IOException("tool manifest is not an object");
    }
    return root;
  }

  private static void updateRuntimeStatus(Path trialRoot, JsonNode toolManifest) throws IOException {
    ObjectNode root = (ObjectNode) toolManifest.deepCopy();
    ObjectNode server = (ObjectNode) root.path("jdtLanguageServer");
    server.put("launched", true);
    server.put("runtimeStatus", "LAUNCHED_IN_SANDBOXED_OFFLINE_TRIAL");
    server.put("runtimeRecordedAt", Instant.now().toString());
    root.put("selectionStatus", "APPROVED_PINNED_VERIFIED_AND_LAUNCHED");
    atomicJson(trialRoot.resolve("tool-manifest.json"), root);
  }

  private static void writeVerdict(
      Path trialRoot,
      String status,
      String summary,
      ToolEvidence tools,
      RecordingClient client)
      throws IOException {
    ObjectNode verdict = JSON.createObjectNode();
    verdict.put("status", status);
    verdict.put("summary", summary);
    verdict.put("networkPolicy", "SANDBOX_RESTRICTED_DURING_JDT_SESSION");
    verdict.set("toolEvidence", tools.toJson());
    verdict.set("diagnostics", client.diagnostics());
    atomicJson(trialRoot.resolve("results/verdict/verdict.json"), verdict);
  }

  private record CaseResult(boolean primaryPassed, Path packet, String sha256) {}

  private record EntrySpec(String name, String method, String route, String originalPath, int start, int end) {
    static List<EntrySpec> parse(String cases) {
      List<EntrySpec> entries = new ArrayList<>();
      for (String name : cases.split(",")) {
        entries.add(from(name.trim()));
      }
      return entries;
    }

    private static EntrySpec from(String name) {
      return switch (name) {
        case "registration" ->
            new EntrySpec(
                name,
                "POST",
                "/user/registerUser",
                "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
                357,
                367);
        case "financial" ->
            new EntrySpec(
                name,
                "GET",
                "/accountHead/getFinancialBillNoByBillId",
                "jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java",
                181,
                196);
        default -> throw new IllegalArgumentException("unsupported approved entry: " + name);
      };
    }
  }

  static final class Projection {
    private final Path root;
    private final Map<String, String> originalToProjected;
    private final Map<String, String> projectedToOriginal;

    private Projection(Path root, Map<String, String> originalToProjected) {
      this.root = root;
      this.originalToProjected = Map.copyOf(originalToProjected);
      Map<String, String> reverse = new LinkedHashMap<>();
      originalToProjected.forEach(
          (original, projected) -> {
            if (reverse.put(projected, original) != null) {
              throw new IllegalArgumentException("duplicate projected path");
            }
          });
      this.projectedToOriginal = Map.copyOf(reverse);
    }

    static Projection loadAndVerify(Path trialRoot, String commit) throws Exception {
      Path manifestPath = trialRoot.resolve("input/projection-manifest.json");
      JsonNode manifest = JSON.readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));
      if (!commit.equals(manifest.path("snapshotCommit").asText())) {
        throw new IOException("projection commit does not match the approved snapshot");
      }
      Path root = trialRoot.resolve(manifest.path("projectionRoot").asText()).toAbsolutePath().normalize();
      Path repository = trialRoot.getParent().resolve("jshERP-" + commit);
      Map<String, String> paths = new LinkedHashMap<>();
      Set<String> selected = new LinkedHashSet<>();
      for (JsonNode source : manifest.path("sources")) {
        String original = source.path("originalPath").asText();
        String projected = source.path("projectedPath").asText();
        if (original.isBlank() || projected.isBlank() || !selected.add(original)) {
          throw new IOException("invalid projection manifest source entry");
        }
        byte[] bytes = git(repository, "cat-file", "blob", commit + ":" + original);
        String blob = gitText(repository, "rev-parse", commit + ":" + original).trim();
        Path projectedFile = root.resolve(projected).normalize();
        if (!projectedFile.startsWith(root)
            || !Files.isRegularFile(projectedFile)
            || !blob.equals(source.path("gitBlob").asText())
            || !sha256(bytes).equals(source.path("sha256").asText())
            || !sha256(Files.readAllBytes(projectedFile)).equals(source.path("sha256").asText())) {
          throw new IOException("projection source verification failed: " + original);
        }
        paths.put(original, projected);
      }
      if (paths.size() != manifest.path("sourceCount").asInt(-1)) {
        throw new IOException("projection manifest source count drift");
      }
      return new Projection(root, paths);
    }

    Path projected(String original) throws IOException {
      String projected = originalToProjected.get(original);
      if (projected == null) {
        throw new IOException("entry is outside the verified projection");
      }
      return root.resolve(projected);
    }

    String original(URI uri) {
      try {
        Path file = Path.of(uri).toAbsolutePath().normalize();
        if (!file.startsWith(root)) {
          return null;
        }
        return projectedToOriginal.get(root.relativize(file).toString().replace('\\', '/'));
      } catch (IllegalArgumentException invalidUri) {
        return null;
      }
    }

    Path root() {
      return root;
    }
  }

  private static final class ToolEvidence {
    private final String archiveHash;
    private final String embeddedCommit;
    private final int launcherCount;

    private ToolEvidence(String archiveHash, String embeddedCommit, int launcherCount) {
      this.archiveHash = archiveHash;
      this.embeddedCommit = embeddedCommit;
      this.launcherCount = launcherCount;
    }

    static ToolEvidence verify(Path trialRoot, JsonNode manifest) throws Exception {
      JsonNode server = manifest.path("jdtLanguageServer");
      Path archive = trialRoot.resolve("tools/downloads").resolve(server.path("archiveName").asText());
      if (!Files.isRegularFile(archive) || !sha256(Files.readAllBytes(archive)).equals(server.path("sha256").asText())) {
        throw new IOException("selected server archive checksum failed");
      }
      Path expectedLauncher = Path.of(server.path("expectedEquinoxLauncher").asText());
      Path install = Path.of(server.path("installDirectory").asText());
      int launcherCount;
      try (var files = Files.list(install.resolve("plugins"))) {
        launcherCount = (int) files.filter(path -> path.getFileName().toString().startsWith("org.eclipse.equinox.launcher_")).count();
      }
      if (launcherCount != 1 || !Files.isRegularFile(expectedLauncher) || !Files.isExecutable(Path.of(server.path("serverExecutable").asText()))) {
        throw new IOException("selected server layout verification failed");
      }
      Path core =
          install
              .resolve("plugins")
              .resolve("org.eclipse.jdt.ls.core_" + server.path("milestone").asText() + ".202609031315.jar");
      try (JarFile jar = new JarFile(core.toFile())) {
        Manifest jarManifest = jar.getManifest();
        String source = jarManifest.getMainAttributes().getValue("Eclipse-SourceReferences");
        if (source == null || !source.contains(server.path("releaseSourceCommit").asText())) {
          throw new IOException("selected server embedded source identity failed");
        }
      }
      return new ToolEvidence(sha256(Files.readAllBytes(archive)), server.path("releaseSourceCommit").asText(), launcherCount);
    }

    ObjectNode toJson() {
      ObjectNode json = JSON.createObjectNode();
      json.put("archiveSha256", archiveHash);
      json.put("embeddedSourceCommit", embeddedCommit);
      json.put("launcherCount", launcherCount);
      return json;
    }
  }

  static final class JdtSession implements AutoCloseable {
    private final Process process;
    private final ExecutorService executor;
    private final Launcher<LanguageServer> launcher;
    private final RemoteEndpoint endpoint;
    private final RecordingClient client;
    private final RequestJournal journal;
    private final Future<?> stderr;
    private final Future<Void> listening;
    private final Path processTreeLog;
    private boolean processTreeUnavailable;

    private JdtSession(
        Process process,
        ExecutorService executor,
        Launcher<LanguageServer> launcher,
        RecordingClient client,
        RequestJournal journal,
        Future<?> stderr,
        Future<Void> listening,
        Path processTreeLog) {
      this.process = process;
      this.executor = executor;
      this.launcher = launcher;
      this.endpoint = launcher.getRemoteEndpoint();
      this.client = client;
      this.journal = journal;
      this.stderr = stderr;
      this.listening = listening;
      this.processTreeLog = processTreeLog;
    }

    static JdtSession open(
        Path trialRoot, JsonNode manifest, RecordingClient client, RequestJournal journal) throws IOException {
      JsonNode server = manifest.path("jdtLanguageServer");
      JsonNode runtime = manifest.path("launchRuntime");
      Map<String, String> isolatedRuntime = prepareJdtRuntime(trialRoot, manifest);
      Path executable = Path.of(server.path("serverExecutable").asText());
      Path configuration = trialRoot.resolve("runtime/configuration");
      Path data = trialRoot.resolve("runtime/data");
      List<String> command =
          List.of(
              "env",
              "-u",
              "CLIENT_PORT",
              "-u",
              "CLIENT_HOST",
              executable.toString(),
              "-configuration",
              configuration.toString(),
              "-data",
              data.toString());
      Files.writeString(
          trialRoot.resolve("runtime/logs/command.txt"),
          "XDG_CACHE_HOME=" + isolatedRuntime.get("XDG_CACHE_HOME") + "\n" + String.join(" ", command) + "\n",
          StandardCharsets.UTF_8);
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.environment().put("JAVA_HOME", runtime.path("javaHome").asText());
      processBuilder.environment().put("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
      processBuilder.environment().put("JDTLS_NETWORK_POLICY", "SANDBOX_RESTRICTED");
      processBuilder.environment().putAll(isolatedRuntime);
      Process process = processBuilder.start();
      Path processTreeLog = trialRoot.resolve("runtime/logs/process-tree.txt");
      Files.writeString(
          processTreeLog,
          "rootPid=" + process.pid() + "\nrootCommand=" + process.info().commandLine().orElse("UNAVAILABLE") + "\n",
          StandardCharsets.UTF_8);
      ExecutorService executor = Executors.newCachedThreadPool();
      Path stderrPath = trialRoot.resolve("runtime/logs/server-stderr.log");
      Future<?> stderr = executor.submit(() -> copy(process.getErrorStream(), stderrPath));
      Launcher<LanguageServer> launcher =
          new Launcher.Builder<LanguageServer>()
              .setLocalService(client)
              .setRemoteInterface(LanguageServer.class)
              .setInput(process.getInputStream())
              .setOutput(process.getOutputStream())
              .setExecutorService(executor)
              .configureGson(GsonBuilder::serializeNulls)
              .create();
      Future<Void> listening = launcher.startListening();
      return new JdtSession(process, executor, launcher, client, journal, stderr, listening, processTreeLog);
    }

    void initialize(Path projectionRoot) throws Exception {
      Map<String, Object> settings = initializationSettings();
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("processId", null);
      params.put("rootUri", projectionRoot.toUri().toString());
      params.put(
          "capabilities",
          Map.of(
              "textDocument",
              Map.of(
                  "documentSymbol", Map.of("hierarchicalDocumentSymbolSupport", true),
                  "declaration", Map.of("linkSupport", true),
                  "definition", Map.of("linkSupport", true),
                  "implementation", Map.of("linkSupport", true),
                  "callHierarchy", Map.of("dynamicRegistration", false))));
      params.put("initializationOptions", Map.of("settings", settings));
      request("initialize", params, LIMIT);
      notify("initialized", Map.of());
      notify("workspace/didChangeConfiguration", Map.of("settings", settings));
    }

    void awaitQuiescence(Duration maximum) throws Exception {
      Instant deadline = Instant.now().plus(maximum);
      while (Instant.now().isBefore(deadline)) {
        rejectForbiddenChildProcess();
        rejectForbiddenActivity();
        if (!process.isAlive()) {
          throw new IOException("JDT LS exited during initialization");
        }
        if (client.serviceReady()) {
          return;
        }
        Thread.sleep(250);
      }
      throw new IOException("JDT LS did not become quiescent within the approved limit");
    }

    JsonNode request(String method, Object params, Duration timeout) throws Exception {
      rejectForbiddenActivity();
      try {
        CompletableFuture<?> future = endpoint.request(method, params);
        Object response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        JsonNode result = JSON.valueToTree(response);
        journal.recordExchange(method, params, result, null);
        rejectForbiddenActivity();
        return result;
      } catch (Exception failure) {
        journal.recordExchange(method, params, null, failure);
        throw failure;
      }
    }

    void notify(String method, Object params) throws IOException {
      endpoint.notify(method, params);
      journal.record("notification", method, params);
    }

    RecordingClient client() {
      return client;
    }

    private void rejectForbiddenChildProcess() throws IOException {
      try {
        for (ProcessHandle descendant : process.toHandle().descendants().toList()) {
          String command = descendant.info().commandLine().orElse("").toLowerCase(Locale.ROOT);
          if (command.contains("mvn")
              || command.contains("maven")
              || command.contains("gradle")
              || command.contains("annotation processor")) {
            throw new IOException("forbidden child process started by JDT LS");
          }
        }
      } catch (RuntimeException sandboxRestriction) {
        if (!processTreeUnavailable) {
          Files.writeString(
              processTreeLog,
              "descendants=PROCESS_TREE_UNAVAILABLE_SANDBOX\n",
              StandardCharsets.UTF_8,
              java.nio.file.StandardOpenOption.APPEND);
          processTreeUnavailable = true;
        }
      }
    }

    private void rejectForbiddenActivity() throws IOException {
      String detail = client.forbiddenActivity();
      if (detail != null) {
        throw new IOException("forbidden JDT LS activity detected: " + detail);
      }
    }

    @Override
    public void close() {
      try {
        endpoint.request("shutdown", Map.of()).get(20, TimeUnit.SECONDS);
        endpoint.notify("exit", Map.of());
      } catch (Exception ignored) {
        process.destroy();
      }
      if (process.isAlive()) {
        process.destroy();
      }
      try {
        process.waitFor(20, TimeUnit.SECONDS);
        stderr.get(20, TimeUnit.SECONDS);
        listening.get(20, TimeUnit.SECONDS);
      } catch (Exception ignored) {
        process.destroyForcibly();
      } finally {
        executor.shutdownNow();
      }
    }
  }

  private static final class GenericWalker {
    private final JdtSession session;
    private final Projection projection;
    private final Map<String, List<SymbolRange>> symbols = new LinkedHashMap<>();
    private final Set<String> visited = new LinkedHashSet<>();
    private final ObjectNode packet = JSON.createObjectNode();
    private final ArrayNode methods = packet.putArray("methods");
    private final ArrayNode calls = packet.putArray("calls");
    private final ArrayNode diagnostics = packet.putArray("diagnostics");

    private GenericWalker(JdtSession session, Projection projection) {
      this.session = session;
      this.projection = projection;
    }

    ObjectNode walk(EntrySpec entry, int maxMethods, int maxDepth, Duration timeout) throws Exception {
      packet.put("packetVersion", "jdtls-source-navigation-feasibility-packet-v1");
      packet.put("snapshotCommit", COMMIT);
      ObjectNode entryNode = packet.putObject("entry");
      entryNode.put("method", entry.method());
      entryNode.put("path", entry.route());
      entryNode.put("file", entry.originalPath());
      entryNode.putObject("range").put("startLine", entry.start()).put("endLine", entry.end());
      packet.putObject("limits").put("maxMethods", maxMethods).put("maxDepth", maxDepth).put("timeoutSeconds", timeout.toSeconds());
      visit(new SymbolLocation(entry.originalPath(), entry.start(), entry.end()), 0, maxMethods, maxDepth, timeout, true);
      return packet;
    }

    private void visit(
        SymbolLocation location,
        int depth,
        int maxMethods,
        int maxDepth,
        Duration timeout,
        boolean entry)
        throws Exception {
      String key = location.path() + ":" + location.start() + ":" + location.end();
      if (!visited.add(key)) {
        return;
      }
      if (visited.size() > maxMethods || depth > maxDepth) {
        diagnostics.add("WALK_LIMIT_REACHED:" + key);
        return;
      }
      Path file = projection.projected(location.path());
      String text = Files.readString(file, StandardCharsets.UTF_8);
      String uri = file.toUri().toString();
      session.notify(
          "textDocument/didOpen",
          Map.of(
              "textDocument",
              Map.of("uri", uri, "languageId", "java", "version", 1, "text", text)));
      List<SymbolRange> documentSymbols = documentSymbols(uri, timeout);
      SymbolRange body = findBody(documentSymbols, location);
      if (body == null) {
        diagnostics.add("FULL_BODY_NOT_FOUND:" + key);
        return;
      }
      MethodDeclaration method = syntaxMethod(text, body);
      methods.add(methodJson(location.path(), body, method.getDeclarationAsString(false, false, false), text));
      List<MethodCallExpr> outgoing = method.findAll(MethodCallExpr.class).stream().filter(call -> within(call.getRange().orElse(null), body)).toList();
      for (MethodCallExpr call : outgoing) {
        ObjectNode callNode = callJson(location.path(), call);
        ArrayNode candidateNodes = callNode.putArray("candidates");
        Set<SymbolLocation> candidates = new LinkedHashSet<>();
        Map<String, Object> position =
            Map.of(
                "textDocument", Map.of("uri", uri),
                "position",
                Map.of(
                    "line", call.getName().getBegin().orElseThrow().line - 1,
                    "character", call.getName().getBegin().orElseThrow().column - 1));
        for (String request : List.of("textDocument/declaration", "textDocument/definition", "textDocument/implementation")) {
          JsonNode response = session.request(request, position, timeout);
          for (SymbolLocation candidate : locations(response)) {
            ObjectNode candidateNode = candidateNodes.addObject();
            candidateNode.put("path", candidate.path());
            candidateNode.putObject("range").put("startLine", candidate.start()).put("endLine", candidate.end());
            candidates.add(candidate);
          }
        }
        if (entry) {
          requestCallHierarchy(uri, body, timeout);
        }
        if (candidates.isEmpty()) {
          callNode.put("resolution", "NONE");
          callNode.put("disposition", "UNRESOLVED");
          continue;
        }
        callNode.put("resolution", candidates.size() == 1 ? "UNIQUE" : "CANDIDATES");
        callNode.put("disposition", depth >= maxDepth ? "DEPTH_LIMIT" : "EXPANDED");
        if (depth < maxDepth) {
          for (SymbolLocation candidate : candidates) {
            visit(candidate, depth + 1, maxMethods, maxDepth, timeout, false);
          }
        }
      }
    }

    private List<SymbolRange> documentSymbols(String uri, Duration timeout) throws Exception {
      List<SymbolRange> existing = symbols.get(uri);
      if (existing != null) {
        return existing;
      }
      JsonNode response = session.request("textDocument/documentSymbol", Map.of("textDocument", Map.of("uri", uri)), timeout);
      List<SymbolRange> parsed = new ArrayList<>();
      collectSymbols(response, parsed);
      symbols.put(uri, parsed);
      return parsed;
    }

    private void requestCallHierarchy(String uri, SymbolRange body, Duration timeout) throws Exception {
      Map<String, Object> params =
          Map.of(
              "textDocument", Map.of("uri", uri),
              "position", Map.of("line", body.start() - 1, "character", 0));
      JsonNode prepared = session.request("textDocument/prepareCallHierarchy", params, timeout);
      if (!prepared.isArray() || prepared.isEmpty()) {
        diagnostics.add("CALL_HIERARCHY_EMPTY");
        return;
      }
      for (JsonNode item : prepared) {
        session.request("callHierarchy/outgoingCalls", Map.of("item", JSON.convertValue(item, Object.class)), timeout);
      }
    }

    private List<SymbolLocation> locations(JsonNode response) {
      List<SymbolLocation> found = new ArrayList<>();
      collectLocations(response, found);
      return found.stream().filter(location -> projection.originalToProjected.containsKey(location.path())).distinct().toList();
    }

    private void collectLocations(JsonNode node, List<SymbolLocation> found) {
      if (node == null || node.isNull()) {
        return;
      }
      if (node.isArray()) {
        node.forEach(item -> collectLocations(item, found));
        return;
      }
      String uri = node.path("uri").asText(node.path("targetUri").asText());
      JsonNode range = node.has("targetRange") ? node.path("targetRange") : node.path("range");
      if (!uri.isBlank() && range.isObject()) {
        String original = projection.original(URI.create(uri));
        if (original != null) {
          found.add(new SymbolLocation(original, range.path("start").path("line").asInt() + 1, range.path("end").path("line").asInt() + 1));
        }
      }
    }

    private void collectSymbols(JsonNode node, List<SymbolRange> output) {
      if (node == null || node.isNull()) {
        return;
      }
      if (node.isArray()) {
        node.forEach(child -> collectSymbols(child, output));
        return;
      }
      JsonNode range = node.path("range");
      if (!range.isObject()) {
        range = node.path("location").path("range");
      }
      if (range.isObject()) {
        output.add(
            new SymbolRange(
                range.path("start").path("line").asInt() + 1,
                range.path("end").path("line").asInt() + 1,
                node.path("name").asText()));
      }
      JsonNode children = node.get("children");
      if (children != null) {
        collectSymbols(children, output);
      }
    }

    private static SymbolRange findBody(List<SymbolRange> symbols, SymbolLocation location) {
      return symbols.stream()
          .filter(symbol -> symbol.start() <= location.start() && symbol.end() >= location.end())
          .min(Comparator.comparingInt(symbol -> symbol.end() - symbol.start()))
          .orElse(null);
    }

    private static MethodDeclaration syntaxMethod(String text, SymbolRange body) throws IOException {
      CompilationUnit unit = new JavaParser().parse(text).getResult().orElseThrow(() -> new IOException("syntax parse failed"));
      return unit.findAll(MethodDeclaration.class).stream()
          .filter(
              method ->
                  within(
                      new Range(
                          new Position(body.start(), 1), new Position(body.end(), Integer.MAX_VALUE)),
                      method.getRange().orElse(null)))
          .min(Comparator.comparingInt(method -> method.getRange().orElseThrow().end.line - method.getRange().orElseThrow().begin.line))
          .orElseThrow(() -> new IOException("document symbol did not identify a method body"));
    }

    private static boolean within(Range inner, SymbolRange outer) {
      return inner != null && inner.begin.line >= outer.start() && inner.end.line <= outer.end();
    }

    private static boolean within(Range inner, Range outer) {
      return inner != null && outer != null && outer.begin.isBeforeOrEqual(inner.begin) && outer.end.isAfterOrEqual(inner.end);
    }

    private static ObjectNode methodJson(String path, SymbolRange body, String symbol, String text) {
      ObjectNode method = JSON.createObjectNode();
      method.put("symbol", symbol);
      method.put("path", path);
      method.putObject("range").put("startLine", body.start()).put("endLine", body.end());
      method.put("snippet", lines(text, body.start(), body.end()));
      return method;
    }

    private static ObjectNode callJson(String path, MethodCallExpr call) {
      ObjectNode result = JSON.createObjectNode();
      result.put("path", path);
      result.put("line", call.getName().getBegin().orElseThrow().line);
      result.put("text", call.toString());
      ArrayNode actuals = result.putArray("actualArguments");
      call.getArguments().forEach(argument -> actuals.add(argument.toString()));
      return result;
    }

    private static String lines(String text, int start, int end) {
      String[] all = text.split("\\R", -1);
      return String.join("\n", Arrays.copyOfRange(all, start - 1, end));
    }
  }

  private record SymbolLocation(String path, int start, int end) {}

  private record SymbolRange(int start, int end, String name) {}

  static final class RequestJournal implements AutoCloseable {
    private final Path global;
    private final List<String> lines = new ArrayList<>();

    RequestJournal(Path global) throws IOException {
      this.global = global;
      Files.createDirectories(global.getParent());
      Files.deleteIfExists(global);
      Files.createFile(global);
    }

    synchronized void record(String kind, String method, Object params) throws IOException {
      ObjectNode line = JSON.createObjectNode();
      line.put("kind", kind);
      line.put("method", method);
      line.set("params", JSON.valueToTree(params));
      String json = JSON.writeValueAsString(line);
      lines.add(json);
      Files.writeString(global, json + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
    }

    synchronized void recordExchange(
        String method, Object params, JsonNode result, Exception failure) throws IOException {
      ObjectNode line = JSON.createObjectNode();
      line.put("kind", "request-response");
      line.put("method", method);
      line.set("params", JSON.valueToTree(params));
      if (failure == null) {
        line.set("result", result);
      } else {
        ObjectNode error = line.putObject("error");
        error.put("type", failure.getClass().getName());
        error.put("message", String.valueOf(failure.getMessage()));
      }
      String json = JSON.writeValueAsString(line);
      lines.add(json);
      Files.writeString(global, json + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
    }

    synchronized int size() {
      return lines.size();
    }

    synchronized void copyFrom(int index, Path target) throws IOException {
      Files.write(target, lines.subList(index, lines.size()), StandardCharsets.UTF_8);
      if (lines.size() > index) {
        Files.writeString(target, "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
      }
    }

    @Override
    public void close() {}
  }

  static final class RecordingClient implements LanguageClient {
    private final ArrayNode diagnostics = JSON.createArrayNode();
    private volatile Instant lastActivity = Instant.now();
    private volatile boolean serviceReady;
    private volatile String forbiddenActivity;

    @Override
    public void telemetryEvent(Object object) {
      activity("telemetry", object);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams params) {
      activity("publishDiagnostics", params);
    }

    @Override
    public void showMessage(MessageParams params) {
      activity("showMessage", params);
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
      activity("showMessageRequest", params);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams params) {
      activity("logMessage", params);
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
      activity("applyEdit", params);
      return CompletableFuture.completedFuture(new ApplyWorkspaceEditResponse(false));
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams params) {
      int size = params.getItems() == null ? 0 : params.getItems().size();
      return CompletableFuture.completedFuture(Collections.nCopies(size, Map.of()));
    }

    @JsonNotification("language/status")
    public void status(Object value) {
      activity("language/status", value);
    }

    synchronized ArrayNode diagnostics() {
      return diagnostics.deepCopy();
    }

    Instant lastActivity() {
      return lastActivity;
    }

    boolean serviceReady() {
      return serviceReady;
    }

    String forbiddenActivity() {
      return forbiddenActivity;
    }

    private synchronized void activity(String type, Object value) {
      ObjectNode event = diagnostics.addObject();
      event.put("type", type);
      event.set("value", JSON.valueToTree(value));
      if ("language/status".equals(type) && event.path("value").toString().contains("ServiceReady")) {
        serviceReady = true;
      }
      String text = event.path("value").toString().toLowerCase(Locale.ROOT);
      if (text.contains("cannot download published gradle versions")
          || text.contains("services.gradle.org")
          || text.contains("maven import")
          || text.contains("annotation processor")) {
        forbiddenActivity = text;
      }
      lastActivity = Instant.now();
    }
  }

  private static Map<String, Object> initializationSettings() {
    Map<String, Object> imports = new LinkedHashMap<>();
    imports.put("maven", Map.of("enabled", false));
    imports.put("gradle", Map.of("enabled", false, "annotationProcessing", Map.of("enabled", false)));
    Map<String, Object> java = new LinkedHashMap<>();
    java.put("import", imports);
    java.put("autobuild", Map.of("enabled", false));
    java.put("project", Map.of("sourcePaths", List.of("src"), "referencedLibraries", List.of()));
    java.put("maven", Map.of("downloadSources", false));
    java.put("eclipse", Map.of("downloadSources", false));
    java.put("configuration", Map.of("updateBuildConfiguration", "disabled"));
    return Map.of("java", java);
  }

  static Map<String, String> prepareJdtRuntime(Path trialRoot, JsonNode manifest) throws IOException {
    Path install =
        Path.of(manifest.path("jdtLanguageServer").path("installDirectory").asText())
            .toAbsolutePath()
            .normalize();
    List<Path> coreBundles;
    try (var plugins = Files.list(install.resolve("plugins"))) {
      coreBundles =
          plugins
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().matches("org\\.eclipse\\.jdt\\.ls\\.core_.+\\.jar"))
              .toList();
    }
    if (coreBundles.size() != 1) {
      throw new IOException("selected JDT distribution must contain exactly one core bundle");
    }
    byte[] catalog;
    try (JarFile core = new JarFile(coreBundles.get(0).toFile())) {
      JarEntry entry = core.getJarEntry("gradle/checksums/versions.json");
      if (entry == null) {
        throw new IOException("selected JDT distribution lacks the embedded Gradle version catalog");
      }
      try (InputStream input = core.getInputStream(entry)) {
        catalog = input.readAllBytes();
      }
    }
    JsonNode parsed = JSON.readTree(catalog);
    if (!parsed.isArray() || parsed.isEmpty()) {
      throw new IOException("embedded Gradle version catalog is not a non-empty array");
    }
    Path cacheHome = trialRoot.resolve("runtime/cache").toAbsolutePath().normalize();
    Path destination = cacheHome.resolve("tooling/gradle/versions.json");
    Files.createDirectories(destination.getParent());
    Path temporary = Files.createTempFile(destination.getParent(), "versions-", ".json");
    try {
      Files.write(temporary, catalog);
      Files.move(
          temporary,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
    return Map.of("XDG_CACHE_HOME", cacheHome.toString());
  }

  private static void copy(InputStream source, Path target) {
    try (source; OutputStream output = Files.newOutputStream(target)) {
      source.transferTo(output);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static byte[] git(Path repository, String... arguments) throws IOException {
    List<String> command = new ArrayList<>();
    command.add("git");
    command.add("-C");
    command.add(repository.toString());
    command.addAll(List.of(arguments));
    ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
    processBuilder.environment().put("GIT_NO_LAZY_FETCH", "1");
    Process process = processBuilder.start();
    byte[] output;
    try (InputStream input = process.getInputStream()) {
      output = input.readAllBytes();
    }
    try {
      if (process.waitFor() != 0) {
        throw new IOException("frozen Git verification failed");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted during frozen Git verification", interrupted);
    }
    return output;
  }

  private static String gitText(Path repository, String... arguments) throws IOException {
    return new String(git(repository, arguments), StandardCharsets.UTF_8);
  }

  private static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        hex.append(String.format(Locale.ROOT, "%02x", value));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }

  private static void atomicJson(Path target, JsonNode json) throws IOException {
    Path temporary = Files.createTempFile(target.getParent(), "write-", ".json");
    try {
      Files.writeString(temporary, JSON.writeValueAsString(json), StandardCharsets.UTF_8);
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
