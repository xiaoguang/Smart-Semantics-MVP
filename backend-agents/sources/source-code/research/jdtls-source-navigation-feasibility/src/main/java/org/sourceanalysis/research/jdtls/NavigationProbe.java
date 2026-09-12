package org.sourceanalysis.research.jdtls;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.Range;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Frozen Git-object materialization and syntax-only request planning for the isolated trial. */
public final class NavigationProbe {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String JAVA_ROOT_MARKER = "/src/main/java/";
  private static final Pattern MAIN_JAVA = Pattern.compile(".+/src/main/java/.+\\.java");

  private NavigationProbe() {}

  public static void materialize(Path snapshotRepository, String snapshotCommit, Path trialRoot)
      throws IOException {
    Objects.requireNonNull(snapshotRepository, "snapshotRepository");
    Objects.requireNonNull(snapshotCommit, "snapshotCommit");
    Objects.requireNonNull(trialRoot, "trialRoot");
    requireCommit(snapshotRepository, snapshotCommit);
    createTrialDirectories(trialRoot);

    Path input = trialRoot.resolve("input");
    Path projection = trialRoot.resolve("projection");
    if (Files.exists(input.resolve("projection-manifest.json")) || hasRegularFiles(projection)) {
      throw new IOException("trial output already contains a projection");
    }

    Path archive = input.resolve("jshERP-" + snapshotCommit + ".tar");
    runGit(snapshotRepository, List.of("archive", "--format=tar", "--output=" + archive, snapshotCommit));
    List<String> sources = sourcePaths(snapshotRepository, snapshotCommit);
    if (sources.isEmpty()) {
      throw new IOException("frozen commit has no main Java sources");
    }

    Path sourceRoot = projection.resolve("src");
    Map<String, String> projectedOwners = new LinkedHashMap<>();
    ArrayNode manifestSources = JSON.createArrayNode();
    for (String source : sources) {
      String projected = projectedPath(source);
      String previous = projectedOwners.putIfAbsent(projected, source);
      if (previous != null) {
        throw new IOException("projection path collision: " + previous + " and " + source);
      }
      byte[] bytes = gitBytes(snapshotRepository, "cat-file", "blob", snapshotCommit + ":" + source);
      String blob = gitText(snapshotRepository, "rev-parse", snapshotCommit + ":" + source).trim();
      Path target = sourceRoot.resolve(projected).normalize();
      if (!target.startsWith(sourceRoot)) {
        throw new IOException("unsafe projected source path: " + source);
      }
      Files.createDirectories(target.getParent());
      Files.write(target, bytes);
      ObjectNode entry = manifestSources.addObject();
      entry.put("originalPath", source);
      entry.put("projectedPath", projected);
      entry.put("gitBlob", blob);
      entry.put("sha256", sha256(bytes));
    }
    rejectProhibitedInputs(projection);
    ObjectNode manifest = JSON.createObjectNode();
    manifest.put("snapshotCommit", snapshotCommit);
    manifest.put("projectionRoot", "projection/src");
    manifest.put("sourceCount", sources.size());
    manifest.set("sources", manifestSources);
    atomicJson(input.resolve("projection-manifest.json"), manifest);
  }

  public static String probe(
      String snapshotCommit,
      Path projectionRoot,
      String entryFile,
      int entryStartLine,
      int entryEndLine,
      int maxMethods,
      int maxDepth,
      Duration timeout)
      throws IOException {
    if (entryStartLine < 1
        || entryEndLine < entryStartLine
        || maxMethods < 1
        || maxDepth < 1
        || timeout == null
        || timeout.isNegative()
        || timeout.isZero()) {
      throw new IllegalArgumentException("invalid navigation bounds");
    }
    Path normalizedRoot = projectionRoot.toAbsolutePath().normalize();
    Path entry = normalizedRoot.resolve(entryFile).normalize();
    if (!entry.startsWith(normalizedRoot) || !Files.isRegularFile(entry)) {
      throw new IOException("entry file is outside the projection");
    }
    String text = Files.readString(entry, StandardCharsets.UTF_8);
    ParseResult<CompilationUnit> parsed = new JavaParser().parse(text);
    CompilationUnit unit = parsed.getResult().orElseThrow(() -> new IOException("Java syntax parse failed"));
    MethodDeclaration method = entryMethod(unit, entryStartLine, entryEndLine);

    ObjectNode packet = JSON.createObjectNode();
    packet.put("packetVersion", "jdtls-source-navigation-feasibility-packet-v1");
    packet.put("snapshotCommit", snapshotCommit);
    ObjectNode entryNode = packet.putObject("entry");
    entryNode.put("file", entryFile.replace('\\', '/'));
    entryNode.putObject("range").put("startLine", entryStartLine).put("endLine", entryEndLine);
    ArrayNode methods = packet.putArray("methods");
    ObjectNode methodNode = methods.addObject();
    methodNode.put("symbol", method.getDeclarationAsString(false, false, false));
    methodNode.put("path", entryFile.replace('\\', '/'));
    methodNode.putObject("range").put("startLine", entryStartLine).put("endLine", entryEndLine);
    methodNode.put("snippet", textRange(text, entryStartLine, entryEndLine));
    ArrayNode calls = packet.putArray("calls");
    for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
      if (inside(call.getRange().orElse(null), method.getRange().orElse(null))) {
        ObjectNode callNode = calls.addObject();
        callNode.put("path", entryFile.replace('\\', '/'));
        callNode.put("line", call.getName().getBegin().map(position -> position.line).orElse(entryStartLine));
        callNode.put("text", call.toString());
        ArrayNode actualArguments = callNode.putArray("actualArguments");
        call.getArguments().forEach(argument -> actualArguments.add(argument.toString()));
        callNode.put("resolution", "NOT_REQUESTED");
        callNode.put("disposition", "RECORDED");
      }
    }
    packet.putArray("diagnostics");
    packet
        .putObject("limits")
        .put("maxMethods", maxMethods)
        .put("maxDepth", maxDepth)
        .put("timeoutSeconds", timeout.toSeconds());
    return JSON.writeValueAsString(packet);
  }

  public static void main(String[] arguments) throws Exception {
    if (arguments.length == 0) {
      throw new IllegalArgumentException("expected materialize, probe, or goal-driven command");
    }
    Map<String, String> options = parseOptions(arguments);
    Path trialRoot = Path.of(required(options, "--trial-root")).toAbsolutePath().normalize();
    if ("materialize".equals(arguments[0])) {
      String commit = required(options, "--commit");
      Path snapshotRepository =
          Path.of(options.getOrDefault("--snapshot-repository", trialRoot.getParent().resolve("jshERP-" + commit).toString()));
      materialize(snapshotRepository, commit, trialRoot);
      return;
    }
    if ("probe".equals(arguments[0])) {
      new TrialRunner().run(trialRoot, required(options, "--cases"));
      return;
    }
    if ("goal-driven".equals(arguments[0])) {
      GoalDrivenExperiment.run(
          trialRoot, Path.of(required(options, "--output-root")).toAbsolutePath().normalize());
      return;
    }
    throw new IllegalArgumentException("unsupported command: " + arguments[0]);
  }

  private static MethodDeclaration entryMethod(CompilationUnit unit, int startLine, int endLine)
      throws IOException {
    return unit.findAll(MethodDeclaration.class).stream()
        .filter(
            method ->
                overlaps(
                    new Range(new Position(startLine, 1), new Position(endLine, Integer.MAX_VALUE)),
                    method.getRange().orElse(null)))
        .min(Comparator.comparingInt(method -> method.getRange().orElseThrow().end.line - method.getRange().orElseThrow().begin.line))
        .orElseThrow(() -> new IOException("entry range is not a method declaration"));
  }

  private static boolean inside(Range inner, Range outer) {
    if (inner == null || outer == null) {
      return false;
    }
    return outer.begin.isBeforeOrEqual(inner.begin) && outer.end.isAfterOrEqual(inner.end);
  }

  private static boolean overlaps(Range left, Range right) {
    return left != null
        && right != null
        && left.begin.isBeforeOrEqual(right.end)
        && left.end.isAfterOrEqual(right.begin);
  }

  private static String textRange(String text, int startLine, int endLine) {
    String[] lines = text.split("\\R", -1);
    if (endLine > lines.length) {
      throw new IllegalArgumentException("entry range exceeds source");
    }
    return String.join("\n", java.util.Arrays.copyOfRange(lines, startLine - 1, endLine));
  }

  private static void createTrialDirectories(Path trialRoot) throws IOException {
    for (String directory :
        List.of(
            "input",
            "runtime/configuration",
            "runtime/data",
            "runtime/logs",
            "results/registration",
            "results/financial",
            "results/structure",
            "results/verdict")) {
      Files.createDirectories(trialRoot.resolve(directory));
    }
  }

  private static boolean hasRegularFiles(Path path) throws IOException {
    if (!Files.exists(path)) {
      return false;
    }
    try (var walked = Files.walk(path)) {
      return walked.anyMatch(Files::isRegularFile);
    }
  }

  private static List<String> sourcePaths(Path snapshotRepository, String snapshotCommit)
      throws IOException {
    List<String> paths = new ArrayList<>();
    for (String path : gitText(snapshotRepository, "ls-tree", "-r", "--name-only", snapshotCommit).split("\\R")) {
      if (MAIN_JAVA.matcher(path).matches()) {
        paths.add(path);
      }
    }
    paths.sort(String::compareTo);
    return paths;
  }

  private static String projectedPath(String sourcePath) throws IOException {
    int marker = sourcePath.indexOf(JAVA_ROOT_MARKER);
    if (marker < 0) {
      throw new IOException("selected source lacks Java root: " + sourcePath);
    }
    return sourcePath.substring(marker + JAVA_ROOT_MARKER.length());
  }

  private static void rejectProhibitedInputs(Path projection) throws IOException {
    List<String> prohibited =
        List.of(
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
            "settings.gradle.kts",
            ".project",
            ".classpath",
            ".factorypath",
            ".settings",
            "target",
            "bin",
            ".apt_generated",
            "generated-sources",
            "annotation-processors");
    try (var walked = Files.walk(projection)) {
      for (Path path : walked.toList()) {
        for (Path component : projection.relativize(path)) {
          String name = component.toString().toLowerCase(Locale.ROOT);
          if (prohibited.contains(name) || name.endsWith(".factorypath") || name.endsWith(".classpath")) {
            throw new IOException("prohibited projection input: " + path);
          }
        }
      }
    }
  }

  private static void requireCommit(Path repository, String commit) throws IOException {
    runGit(repository, List.of("cat-file", "-e", commit + "^{commit}"));
  }

  private static String gitText(Path repository, String... command) throws IOException {
    return new String(gitBytes(repository, command), StandardCharsets.UTF_8);
  }

  private static byte[] gitBytes(Path repository, String... command) throws IOException {
    return runGit(repository, List.of(command));
  }

  private static byte[] runGit(Path repository, List<String> command) throws IOException {
    List<String> invocation = new ArrayList<>();
    invocation.add("git");
    invocation.add("-C");
    invocation.add(repository.toString());
    invocation.addAll(command);
    ProcessBuilder processBuilder = new ProcessBuilder(invocation).redirectErrorStream(true);
    processBuilder.environment().put("GIT_NO_LAZY_FETCH", "1");
    Process process = processBuilder.start();
    byte[] output;
    try (InputStream input = process.getInputStream()) {
      output = input.readAllBytes();
    }
    try {
      if (process.waitFor() != 0) {
        throw new IOException("Git object operation failed: " + String.join(" ", invocation));
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while reading frozen Git object", interrupted);
    }
    return output;
  }

  private static void atomicJson(Path destination, ObjectNode json) throws IOException {
    Path temporary = Files.createTempFile(destination.getParent(), "projection-", ".json");
    try {
      Files.writeString(temporary, JSON.writeValueAsString(json), StandardCharsets.UTF_8);
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        hex.append(String.format(Locale.ROOT, "%02x", value));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static Map<String, String> parseOptions(String[] arguments) {
    Map<String, String> options = new LinkedHashMap<>();
    for (int index = 1; index < arguments.length; index += 2) {
      if (!arguments[index].startsWith("--") || index + 1 >= arguments.length) {
        throw new IllegalArgumentException("options must be key/value pairs");
      }
      options.put(arguments[index], arguments[index + 1]);
    }
    return options;
  }

  private static String required(Map<String, String> options, String key) {
    String value = options.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("missing " + key);
    }
    return value;
  }
}
