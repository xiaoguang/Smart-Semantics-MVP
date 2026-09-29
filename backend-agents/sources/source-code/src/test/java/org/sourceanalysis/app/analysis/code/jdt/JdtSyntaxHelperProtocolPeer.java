package org.sourceanalysis.app.analysis.code.jdt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny JDK-only child-process peer used to inspect the host's JSONL request. */
public final class JdtSyntaxHelperProtocolPeer {

  private static final String SUPPORTED_VERSION = "jdt-syntax-v4";

  private JdtSyntaxHelperProtocolPeer() {}

  public static void main(String[] args) throws IOException {
    BufferedReader input =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    String request = input.readLine();
    if (request == null) {
      System.exit(2);
      return;
    }
    String protocolVersion = stringField(request, "protocolVersion");
    if (!SUPPORTED_VERSION.equals(protocolVersion)) {
      System.exit(3);
      return;
    }
    String message =
        "protocol="
            + protocolVersion
            + "|targetJdk="
            + stringField(request, "targetJdkVersion")
            + "|platform="
            + arrayField(request, "targetPlatformEntries")
            + "|sourcepath="
            + arrayField(request, "sourcepathEntries")
            + "|classpath="
            + arrayField(request, "classpathEntries");
    System.out.println(response(request, message));
    System.out.flush();
  }

  private static String response(String request, String message) {
    return "{\"protocolVersion\":\""
        + stringField(request, "protocolVersion")
        + "\",\"requestId\":\""
        + stringField(request, "requestId")
        + "\",\"sourceKey\":\""
        + stringField(request, "sourceKey")
        + "\",\"sourceSha256\":\""
        + stringField(request, "sourceSha256")
        + "\",\"packageName\":null,\"imports\":[],\"declarations\":[],"
        + "\"annotations\":[],\"callSites\":[],\"controls\":[],\"exits\":[],"
        + "\"diagnostics\":[{\"code\":\"WIRE_ENVIRONMENT\",\"severity\":\"INFO\","
        + "\"message\":\""
        + escape(message)
        + "\",\"sourceRange\":{\"startOffsetUtf16\":0,\"lengthUtf16\":0,"
        + "\"startLine\":1,\"endLine\":1}}]}";
  }

  private static String stringField(String json, String name) {
    Pattern pattern =
        Pattern.compile("\\\"" + Pattern.quote(name) + "\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    Matcher matcher = pattern.matcher(json);
    if (!matcher.find()) {
      throw new IllegalArgumentException("missing JSON string field: " + name);
    }
    return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
  }

  private static String arrayField(String json, String name) {
    String field = "\"" + name + "\":";
    int start = json.indexOf(field);
    if (start < 0) {
      throw new IllegalArgumentException("missing JSON array field: " + name);
    }
    start += field.length();
    while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
      start++;
    }
    if (start >= json.length() || json.charAt(start) != '[') {
      throw new IllegalArgumentException("invalid JSON array field: " + name);
    }
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int index = start; index < json.length(); index++) {
      char current = json.charAt(index);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (current == '\\') {
          escaped = true;
        } else if (current == '"') {
          inString = false;
        }
      } else if (current == '"') {
        inString = true;
      } else if (current == '[') {
        depth++;
      } else if (current == ']' && --depth == 0) {
        return json.substring(start, index + 1);
      }
    }
    throw new IllegalArgumentException("unterminated JSON array field: " + name);
  }

  private static String escape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
