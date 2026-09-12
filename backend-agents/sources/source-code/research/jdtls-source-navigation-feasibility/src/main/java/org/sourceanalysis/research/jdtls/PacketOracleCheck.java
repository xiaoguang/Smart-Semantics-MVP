package org.sourceanalysis.research.jdtls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Validates a persisted navigation packet against a separately supplied immutable expectation file. */
public final class PacketOracleCheck {

  private static final ObjectMapper JSON = new ObjectMapper();

  private PacketOracleCheck() {}

  public static boolean check(Path packet, Path oracle) {
    try {
      JsonNode packetRoot = JSON.readTree(Files.readString(packet, StandardCharsets.UTF_8));
      JsonNode expectationRoot = JSON.readTree(Files.readString(oracle, StandardCharsets.UTF_8));
      return isObject(packetRoot)
          && isObject(expectationRoot)
          && sameText(packetRoot, expectationRoot, "snapshotCommit")
          && entryMatches(packetRoot.path("entry"), expectationRoot.path("entry"))
          && sourcesMatch(packetRoot, expectationRoot.path("sources"));
    } catch (IOException | RuntimeException malformedInput) {
      return false;
    }
  }

  private static boolean entryMatches(JsonNode packetEntry, JsonNode expectedEntry) {
    return isObject(packetEntry)
        && isObject(expectedEntry)
        && sameText(packetEntry, expectedEntry, "method")
        && sameText(packetEntry, expectedEntry, "path")
        && expectedEntry.path("controller").asText().equals(packetEntry.path("file").asText())
        && sameRange(packetEntry.path("range"), expectedEntry.path("range"));
  }

  private static boolean sourcesMatch(JsonNode packetRoot, JsonNode sources) {
    if (!sources.isArray() || !packetRoot.path("methods").isArray() || !packetRoot.path("calls").isArray()) {
      return false;
    }
    for (JsonNode source : sources) {
      if (!isObject(source) || !source.hasNonNull("path")) {
        return false;
      }
      if (source.has("expectedMethods")
          && !expectedMethodsMatch(packetRoot.path("methods"), source.path("path").asText(), source.path("expectedMethods"))) {
        return false;
      }
      if (source.has("entryRange")
          && !hasCompleteMethod(
              packetRoot.path("methods"), source.path("path").asText(), source.path("entryRange"))) {
        return false;
      }
      if (source.has("expectedRange")
          && !optionalRangeMatches(packetRoot.path("methods"), source)) {
        return false;
      }
    }
    return callsRetainActualArguments(packetRoot.path("calls"));
  }

  private static boolean expectedMethodsMatch(JsonNode methods, String path, JsonNode expectedMethods) {
    if (!expectedMethods.isArray()) {
      return false;
    }
    for (JsonNode expected : expectedMethods) {
      JsonNode actual = findMethod(methods, path, expected.path("range"));
      if (actual == null || !completeSnippet(actual, expected.path("range"))) {
        return false;
      }
      for (JsonNode requiredText : expected.path("requiredText")) {
        if (!actual.path("snippet").asText().contains(requiredText.asText())) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean optionalRangeMatches(JsonNode methods, JsonNode expectedSource) {
    JsonNode actual =
        findMethod(methods, expectedSource.path("path").asText(), expectedSource.path("expectedRange"));
    if (actual == null) {
      return true;
    }
    if (!completeSnippet(actual, expectedSource.path("expectedRange"))) {
      return false;
    }
    for (JsonNode requiredText : expectedSource.path("requiredText")) {
      if (!actual.path("snippet").asText().contains(requiredText.asText())) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasCompleteMethod(JsonNode methods, String path, JsonNode range) {
    JsonNode actual = findMethod(methods, path, range);
    return actual != null && completeSnippet(actual, range);
  }

  private static JsonNode findMethod(JsonNode methods, String path, JsonNode range) {
    for (JsonNode method : methods) {
      if (path.equals(method.path("path").asText()) && sameRange(method.path("range"), range)) {
        return method;
      }
    }
    return null;
  }

  private static boolean completeSnippet(JsonNode method, JsonNode range) {
    if (!method.hasNonNull("snippet") || !sameRange(method.path("range"), range)) {
      return false;
    }
    int expectedLines = range.path("endLine").asInt() - range.path("startLine").asInt() + 1;
    String snippet = method.path("snippet").asText();
    return expectedLines > 0 && snippet.split("\\R", -1).length >= expectedLines;
  }

  private static boolean callsRetainActualArguments(JsonNode calls) {
    for (JsonNode call : calls) {
      if (!isObject(call) || !call.hasNonNull("text") || !call.path("actualArguments").isArray()) {
        return false;
      }
      List<String> actuals = new ArrayList<>();
      for (JsonNode actual : call.path("actualArguments")) {
        if (!actual.isTextual() || actual.asText().isBlank()) {
          return false;
        }
        actuals.add(actual.asText());
      }
      if (!actuals.isEmpty() && actuals.stream().anyMatch(actual -> !call.path("text").asText().contains(actual))) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameText(JsonNode left, JsonNode right, String field) {
    return left.hasNonNull(field)
        && right.hasNonNull(field)
        && left.path(field).asText().equals(right.path(field).asText());
  }

  private static boolean sameRange(JsonNode left, JsonNode right) {
    return isObject(left)
        && isObject(right)
        && left.path("startLine").asInt(-1) == right.path("startLine").asInt(-2)
        && left.path("endLine").asInt(-1) == right.path("endLine").asInt(-2);
  }

  private static boolean isObject(JsonNode node) {
    return node != null && node.isObject();
  }
}
