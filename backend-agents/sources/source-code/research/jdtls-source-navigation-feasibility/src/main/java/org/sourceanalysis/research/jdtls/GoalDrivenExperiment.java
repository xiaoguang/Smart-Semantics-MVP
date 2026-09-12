package org.sourceanalysis.research.jdtls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A throwaway JDT-first experiment. It intentionally does not depend on Source Agent production
 * packages, previous packet schemas, or oracle target lists.
 */
final class GoalDrivenExperiment {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String COMMIT = "8c30ce7861570458920175e200bb2a6442713580";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
  private static final Duration READY_TIMEOUT = Duration.ofMinutes(2);
  private static final int MAX_METHODS = 300;
  private static final int MAX_DEPTH = 20;

  private GoalDrivenExperiment() {}

  static void run(Path trialRoot, Path outputRoot) throws Exception {
    if (Files.exists(outputRoot)) {
      throw new IOException("goal-driven output already exists: " + outputRoot);
    }
    createRuntimeDirectories(outputRoot);
    JsonNode toolManifest = JSON.readTree(Files.readString(trialRoot.resolve("tool-manifest.json")));
    TrialRunner.Projection projection = TrialRunner.Projection.loadAndVerify(trialRoot, COMMIT);
    TrialRunner.RequestJournal journal =
        new TrialRunner.RequestJournal(outputRoot.resolve("raw-exchanges.jsonl"));
    TrialRunner.RecordingClient client = new TrialRunner.RecordingClient();
    try (TrialRunner.JdtSession session = TrialRunner.JdtSession.open(outputRoot, toolManifest, client, journal)) {
      session.initialize(projection.root().getParent());
      session.awaitQuiescence(READY_TIMEOUT);
      for (Entry entry : Entry.all()) {
        int start = journal.size();
        ObjectNode packet = new SourceWalker(session, projection).walk(entry);
        Path caseRoot = outputRoot.resolve(entry.name());
        Files.createDirectories(caseRoot);
        atomicJson(caseRoot.resolve("packet.json"), packet);
        Files.writeString(
            caseRoot.resolve("packet.sha256"),
            sha256(Files.readAllBytes(caseRoot.resolve("packet.json"))) + "  packet.json\n",
            StandardCharsets.UTF_8);
        journal.copyFrom(start, caseRoot.resolve("raw-exchanges.jsonl"));
      }
      atomicJson(outputRoot.resolve("client-diagnostics.json"), client.diagnostics());
    } finally {
      journal.close();
    }
  }

  private static void createRuntimeDirectories(Path root) throws IOException {
    for (String directory : List.of("runtime/configuration", "runtime/data", "runtime/logs")) {
      Files.createDirectories(root.resolve(directory));
    }
  }

  static List<JsonNode> locationValues(JsonNode payload) {
    List<JsonNode> locations = new ArrayList<>();
    collectLocationValues(payload, locations);
    return List.copyOf(locations);
  }

  private static void collectLocationValues(JsonNode payload, List<JsonNode> locations) {
    if (payload == null || payload.isNull()) {
      return;
    }
    if (payload.isArray()) {
      payload.forEach(value -> collectLocationValues(value, locations));
      return;
    }
    if (payload.has("left") || payload.has("right")) {
      collectLocationValues(payload.path("left"), locations);
      collectLocationValues(payload.path("right"), locations);
      return;
    }
    if (payload.has("uri") || payload.has("targetUri")) {
      locations.add(payload);
    }
  }

  private static final class SourceWalker {
    private final TrialRunner.JdtSession session;
    private final TrialRunner.Projection projection;
    private final Set<String> openedUris = new HashSet<>();
    private final Set<String> visited = new LinkedHashSet<>();
    private final Map<String, ObjectNode> methodByKey = new LinkedHashMap<>();
    private final ObjectNode packet = JSON.createObjectNode();
    private final ArrayNode methods = packet.putArray("methods");
    private final ArrayNode calls = packet.putArray("calls");
    private final ArrayNode diagnostics = packet.putArray("diagnostics");

    SourceWalker(TrialRunner.JdtSession session, TrialRunner.Projection projection) {
      this.session = session;
      this.projection = projection;
    }

    ObjectNode walk(Entry entry) throws Exception {
      packet.put("packetVersion", "jdt-goal-driven-source-context-v1");
      packet.put("snapshotCommit", COMMIT);
      ObjectNode entryJson = packet.putObject("entry");
      entryJson.put("route", entry.route());
      entryJson.put("file", entry.originalPath());
      entryJson.putObject("range").put("startLine", entry.startLine()).put("endLine", entry.endLine());
      packet.putObject("limits").put("maxMethods", MAX_METHODS).put("maxDepth", MAX_DEPTH);

      Path entryFile = projection.projected(entry.originalPath());
      ObjectNode entryMethod =
          GoalDrivenSourceReader.readIntersecting(
              Files.readString(entryFile, StandardCharsets.UTF_8),
              entry.originalPath(),
              entry.startLine() - 1,
              entry.endLine() - 1);
      Deque<MethodTask> pending = new ArrayDeque<>();
      pending.add(MethodTask.from(entry.originalPath(), entryMethod, 0));
      while (!pending.isEmpty()) {
        MethodTask task = pending.removeFirst();
        String key = task.key();
        if (!visited.add(key)) {
          continue;
        }
        if (visited.size() > MAX_METHODS || task.depth() > MAX_DEPTH) {
          diagnostics.add("RESOURCE_LIMIT:" + key);
          continue;
        }
        ObjectNode current = read(task);
        current.put("id", "M" + methods.size() + 1);
        current.put("depth", task.depth());
        methods.add(current);
        methodByKey.put(key, current);
        String uri = open(task.path());
        List<Target> hierarchyTargets = hierarchyTargets(uri, current);
        for (JsonNode lexicalCall : current.path("calls")) {
          ObjectNode call = calls.addObject();
          call.put("callerMethodId", current.path("id").asText());
          call.set("call", lexicalCall.deepCopy());
          ArrayNode candidates = call.putArray("candidates");
          List<Target> targets = targetsForCall(uri, lexicalCall, hierarchyTargets);
          if (targets.isEmpty()) {
            call.put("disposition", "UNRESOLVED");
            continue;
          }
          boolean hasLocal = false;
          for (Target target : targets) {
            ObjectNode candidate = candidates.addObject();
            target.writeTo(candidate);
            if (target.repositoryPath() == null) {
              candidate.put("disposition", "EXTERNAL_OR_UNAVAILABLE_BOUNDARY");
              continue;
            }
            hasLocal = true;
            try {
              ObjectNode targetMethod = read(target.toTask(task.depth() + 1));
              candidate.put("methodName", targetMethod.path("name").asText());
              candidate.set("formalParameters", targetMethod.path("formalParameters").deepCopy());
              candidate.set("sourceRange", targetMethod.path("range").deepCopy());
              pending.addLast(target.toTask(task.depth() + 1));
              candidate.put("disposition", "QUEUED_FOR_SOURCE_EXPANSION");
            } catch (IOException notAMethod) {
              candidate.put("disposition", "REPOSITORY_LOCATION_NOT_A_METHOD");
              candidate.put("detail", notAMethod.getMessage());
              diagnostics.add("LOCATION_NOT_METHOD:" + target.repositoryPath());
            }
          }
          call.put("disposition", hasLocal ? "EXPANDED_OR_QUEUED" : "EXTERNAL_BOUNDARY");
        }
      }
      packet.set("diagnostics", diagnostics);
      return packet;
    }

    private ObjectNode read(MethodTask task) throws IOException {
      Path file = projection.projected(task.path());
      return GoalDrivenSourceReader.read(
          Files.readString(file, StandardCharsets.UTF_8), task.path(), task.zeroBasedLine(), task.zeroBasedCharacter());
    }

    private String open(String originalPath) throws Exception {
      Path file = projection.projected(originalPath);
      String uri = file.toUri().toString();
      if (openedUris.add(uri)) {
        session.notify(
            "textDocument/didOpen",
            Map.of(
                "textDocument",
                Map.of(
                    "uri", uri,
                    "languageId", "java",
                    "version", 1,
                    "text", Files.readString(file, StandardCharsets.UTF_8))));
      }
      return uri;
    }

    private List<Target> hierarchyTargets(String uri, ObjectNode method) throws Exception {
      JsonNode nameRange = method.path("nameRange");
      JsonNode prepared =
          session.request(
              "textDocument/prepareCallHierarchy",
              Map.of(
                  "textDocument", Map.of("uri", uri),
                  "position",
                      Map.of(
                          "line", nameRange.path("startLine").asInt() - 1,
                          "character", nameRange.path("startColumn").asInt() - 1)),
              REQUEST_TIMEOUT);
      List<Target> targets = new ArrayList<>();
      if (!prepared.isArray()) {
        return targets;
      }
      for (JsonNode item : prepared) {
        JsonNode outgoing =
            session.request(
                "callHierarchy/outgoingCalls", Map.of("item", JSON.convertValue(item, Object.class)), REQUEST_TIMEOUT);
        if (outgoing.isArray()) {
          for (JsonNode edge : outgoing) {
            Target target = Target.from(edge.path("to"), projection, "CALL_HIERARCHY");
            if (target != null) {
              targets.add(target.withFromRanges(edge.path("fromRanges")));
            }
          }
        }
      }
      return deduplicate(targets);
    }

    private List<Target> targetsForCall(String uri, JsonNode call, List<Target> hierarchyTargets)
        throws Exception {
      List<Target> matched =
          hierarchyTargets.stream().filter(target -> target.matches(call.path("range"))).toList();
      if (!matched.isEmpty()) {
        return matched;
      }
      JsonNode navigationRange =
          call.path("navigationRange").isObject() ? call.path("navigationRange") : call.path("range");
      Map<String, Object> position =
          Map.of(
              "textDocument", Map.of("uri", uri),
              "position",
                  Map.of(
                      "line", navigationRange.path("startLine").asInt() - 1,
                      "character", navigationRange.path("startColumn").asInt() - 1));
      List<Target> resolved = new ArrayList<>();
      for (String request : List.of("textDocument/definition", "textDocument/implementation")) {
        collectTargets(session.request(request, position, REQUEST_TIMEOUT), request, resolved);
      }
      return deduplicate(resolved);
    }

    private void collectTargets(JsonNode result, String source, List<Target> into) {
      for (JsonNode location : locationValues(result)) {
        Target target = Target.from(location, projection, source);
        if (target != null) {
          into.add(target);
        }
      }
    }

    private static List<Target> deduplicate(List<Target> targets) {
      return targets.stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  Target::key, value -> value, (left, right) -> left, LinkedHashMap::new))
          .values()
          .stream()
          .toList();
    }
  }

  private record Entry(String name, String route, String originalPath, int startLine, int endLine) {
    static List<Entry> all() {
      return List.of(
          new Entry(
              "registration",
              "/user/registerUser",
              "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
              357,
              367),
          new Entry(
              "financial",
              "/accountHead/getFinancialBillNoByBillId",
              "jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java",
              181,
              196));
    }
  }

  private record MethodTask(String path, int zeroBasedLine, int zeroBasedCharacter, int depth) {
    static MethodTask from(String path, ObjectNode method, int depth) {
      JsonNode nameRange = method.path("nameRange");
      return new MethodTask(
          path,
          nameRange.path("startLine").asInt() - 1,
          nameRange.path("startColumn").asInt() - 1,
          depth);
    }

    String key() {
      return path + ":" + zeroBasedLine + ":" + zeroBasedCharacter;
    }
  }

  private record Target(
      String uri,
      JsonNode range,
      String repositoryPath,
      String source,
      JsonNode fromRanges) {
    static Target from(JsonNode location, TrialRunner.Projection projection, String source) {
      String uri = location.path("targetUri").asText(location.path("uri").asText());
      JsonNode range =
          location.has("targetSelectionRange")
              ? location.path("targetSelectionRange")
              : location.has("selectionRange")
                  ? location.path("selectionRange")
                  : location.path("targetRange");
      if (!range.isObject()) {
        range = location.path("range");
      }
      if (uri.isBlank() || !range.isObject()) {
        return null;
      }
      String original = projection.original(URI.create(uri));
      return new Target(uri, range.deepCopy(), original, source, JSON.createArrayNode());
    }

    Target withFromRanges(JsonNode newFromRanges) {
      return new Target(uri, range, repositoryPath, source, newFromRanges.deepCopy());
    }

    boolean matches(JsonNode callRange) {
      if (!fromRanges.isArray()) {
        return false;
      }
      int startLine = callRange.path("startLine").asInt() - 1;
      int endLine = callRange.path("endLine").asInt() - 1;
      int startCharacter = callRange.path("startColumn").asInt() - 1;
      // JavaParser columns are one-based and inclusive; LSP end characters are zero-based
      // and exclusive. Therefore the numeric end column is the same on a single source line.
      int endCharacter = callRange.path("endColumn").asInt();
      for (JsonNode from : fromRanges) {
        int fromStart = from.path("start").path("line").asInt(-1);
        int fromEnd = from.path("end").path("line").asInt(-1);
        int fromStartCharacter = from.path("start").path("character").asInt(-1);
        int fromEndCharacter = from.path("end").path("character").asInt(-1);
        if (fromStart == startLine
            && fromEnd == endLine
            && fromStartCharacter == startCharacter
            && fromEndCharacter == endCharacter) {
          return true;
        }
      }
      return false;
    }

    MethodTask toTask(int depth) {
      return new MethodTask(
          repositoryPath,
          range.path("start").path("line").asInt(),
          range.path("start").path("character").asInt(),
          depth);
    }

    String key() {
      return uri + ":" + range.path("start").path("line").asInt() + ":" + range.path("start").path("character").asInt();
    }

    void writeTo(ObjectNode output) {
      output.put("source", source);
      output.put("uri", uri);
      if (repositoryPath != null) {
        output.put("repositoryPath", repositoryPath);
      }
      output.set("range", range.deepCopy());
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

  private static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder result = new StringBuilder();
      for (byte value : digest) {
        result.append(String.format("%02x", value));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }
}
