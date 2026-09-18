package org.sourceanalysis.research.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline caller for Gate A. It reads only an explicit frozen snapshot manifest, its matching blob
 * directory, and a previously saved Java index; it never opens a checkout or starts JDT.
 */
public final class SnapshotProbeCli {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern PARAM_ALIAS = Pattern.compile("@Param\\s*\\(\\s*\\\"([^\\\"]+)\\\"\\s*\\)");

  private SnapshotProbeCli() {}

  public static void main(String[] arguments) throws Exception {
    Map<String, Path> options = options(arguments);
    Path manifest = required(options, "--snapshot-manifest");
    Path blobs = required(options, "--blobs-dir");
    Path javaIndex = required(options, "--java-code-index");
    Path output = required(options, "--output");

    List<MapperResource> resources = mapperResources(manifest, blobs);
    List<MapperMethodDescriptor> methods = mapperMethods(javaIndex);
    PersistenceProbeResult result = new PersistenceToolProbe().analyze(resources, methods);

    Files.createDirectories(output.toAbsolutePath().getParent());
    try (var writer =
        Files.newBufferedWriter(
            output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      JSON.enable(SerializationFeature.INDENT_OUTPUT).writeValue(writer, result);
    }
  }

  private static Map<String, Path> options(String[] arguments) {
    if (arguments.length == 0 || arguments.length % 2 != 0) {
      throw new IllegalArgumentException(
          "Usage: SnapshotProbeCli --snapshot-manifest <jsonl> --blobs-dir <dir> --java-code-index <jsonl> --output <json>");
    }
    Map<String, Path> options = new LinkedHashMap<>();
    for (int index = 0; index < arguments.length; index += 2) {
      String name = arguments[index];
      if (!List.of("--snapshot-manifest", "--blobs-dir", "--java-code-index", "--output").contains(name)
          || options.put(name, Path.of(arguments[index + 1])) != null) {
        throw new IllegalArgumentException("Unknown or repeated option: " + name);
      }
    }
    return options;
  }

  private static Path required(Map<String, Path> options, String name) {
    Path value = options.get(name);
    if (value == null) {
      throw new IllegalArgumentException("Missing required option: " + name);
    }
    return value;
  }

  private static List<MapperResource> mapperResources(Path manifest, Path blobs) throws IOException {
    List<MapperResource> resources = new ArrayList<>();
    try (var lines = Files.lines(manifest, StandardCharsets.UTF_8)) {
      Iterator<String> iterator = lines.iterator();
      while (iterator.hasNext()) {
        JsonNode entry = JSON.readTree(iterator.next());
        String sourcePath = text(entry, "path");
        if (!sourcePath.endsWith(".xml") || !"ANALYZABLE_TEXT".equals(text(entry, "analysisDisposition"))) {
          continue;
        }
        String sha256 = text(entry, "sha256");
        long sizeBytes = entry.path("sizeBytes").asLong(-1);
        Path blob = blobs.resolve(sha256);
        verifyBlob(blob, sha256, sizeBytes);
        String rawXml = Files.readString(blob, StandardCharsets.UTF_8);
        resources.add(new MapperResource(sourcePath, rawXml));
      }
    }
    return List.copyOf(resources);
  }

  private static void verifyBlob(Path blob, String expectedSha256, long expectedSizeBytes) throws IOException {
    if (!expectedSha256.matches("[0-9a-f]{64}")) {
      throw new IOException("Manifest SHA-256 is malformed.");
    }
    if (!Files.isRegularFile(blob)) {
      throw new IOException("Frozen blob is missing: " + blob.getFileName());
    }
    if (Files.size(blob) != expectedSizeBytes) {
      throw new IOException("Frozen blob size differs from manifest: " + blob.getFileName());
    }
    String actualSha256;
    try {
      actualSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(blob)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
    if (!expectedSha256.equals(actualSha256)) {
      throw new IOException("Frozen blob SHA-256 differs from manifest: " + blob.getFileName());
    }
  }

  private static List<MapperMethodDescriptor> mapperMethods(Path javaIndex) throws IOException {
    List<MapperMethodDescriptor> methods = new ArrayList<>();
    try (var lines = Files.lines(javaIndex, StandardCharsets.UTF_8)) {
      Iterator<String> iterator = lines.iterator();
      while (iterator.hasNext()) {
        JsonNode record = JSON.readTree(iterator.next());
        if (!"METHOD".equals(text(record, "recordType"))) {
          continue;
        }
        JsonNode declaration = record.path("payload").path("declaration");
        if (declaration.isMissingNode()) {
          continue;
        }
        String declaringType = text(declaration, "declaringType");
        String methodKey = text(declaration, "methodKey");
        String name = text(declaration, "name");
        if (declaringType.isBlank() || methodKey.isBlank() || name.isBlank()) {
          continue;
        }
        methods.add(
            new MapperMethodDescriptor(declaringType, methodKey, name, mapperParameters(declaration.path("parameters"))));
      }
    }
    return List.copyOf(methods);
  }

  private static List<MapperParameter> mapperParameters(JsonNode parameters) {
    List<MapperParameter> result = new ArrayList<>();
    for (JsonNode parameter : parameters) {
      String name = text(parameter, "name");
      if (name.isBlank()) {
        continue;
      }
      result.add(new MapperParameter(name, paramAlias(parameter.path("annotationTexts"))));
    }
    return result;
  }

  private static String paramAlias(JsonNode annotationTexts) {
    for (JsonNode annotation : annotationTexts) {
      Matcher matcher = PARAM_ALIAS.matcher(annotation.asText());
      if (matcher.find()) {
        return matcher.group(1);
      }
    }
    return null;
  }

  private static String text(JsonNode node, String field) {
    return node.path(field).asText("");
  }
}
