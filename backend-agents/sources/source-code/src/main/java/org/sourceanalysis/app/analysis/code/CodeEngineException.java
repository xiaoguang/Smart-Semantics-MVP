package org.sourceanalysis.app.analysis.code;

import java.util.List;
import java.util.Objects;

/** A stable engine failure that never substitutes an empty result for an unsafe operation. */
public final class CodeEngineException extends RuntimeException {

  public static final String ENGINE_CONFIGURATION_INVALID = "ENGINE_CONFIGURATION_INVALID";
  public static final String ENGINE_NOT_INTEGRATED = "ENGINE_NOT_INTEGRATED";
  public static final String JDT_TOOL_UNAVAILABLE = "JDT_TOOL_UNAVAILABLE";
  public static final String JDT_INDEX_FAILED = "JDT_INDEX_FAILED";
  public static final String JDT_QUERY_FAILED = "JDT_QUERY_FAILED";
  public static final String JDT_PROTOCOL_INVALID = "JDT_PROTOCOL_INVALID";
  public static final String JDT_SYNTAX_TIMEOUT = "JDT_SYNTAX_TIMEOUT";
  public static final String JDT_SYNTAX_PROTOCOL_INVALID = "JDT_SYNTAX_PROTOCOL_INVALID";
  public static final String JDT_SYNTAX_PROCESS_FAILED = "JDT_SYNTAX_PROCESS_FAILED";
  public static final String JDT_SYNTAX_SHUTDOWN_TIMEOUT = "JDT_SYNTAX_SHUTDOWN_TIMEOUT";
  public static final String SOURCE_INVALID = "SOURCE_INVALID";

  private final String code;
  private final String detail;
  private final List<String> affectedSourceKeys;

  public CodeEngineException(String code, String detail) {
    this(code, detail, List.of(), null);
  }

  public CodeEngineException(String code, String detail, Throwable cause) {
    this(code, detail, List.of(), cause);
  }

  public CodeEngineException(
      String code, String detail, List<String> affectedSourceKeys, Throwable cause) {
    super(message(code, detail), cause);
    this.code = requireKnownCode(code);
    this.detail = requireDetail(detail);
    this.affectedSourceKeys = List.copyOf(Objects.requireNonNull(affectedSourceKeys));
  }

  public String code() {
    return code;
  }

  public String detail() {
    return detail;
  }

  public List<String> affectedSourceKeys() {
    return affectedSourceKeys;
  }

  private static String message(String code, String detail) {
    return requireKnownCode(code) + ": " + requireDetail(detail);
  }

  private static String requireKnownCode(String code) {
    if (!List.of(
            ENGINE_CONFIGURATION_INVALID,
            ENGINE_NOT_INTEGRATED,
            JDT_TOOL_UNAVAILABLE,
            JDT_INDEX_FAILED,
            JDT_QUERY_FAILED,
            JDT_PROTOCOL_INVALID,
            JDT_SYNTAX_TIMEOUT,
            JDT_SYNTAX_PROTOCOL_INVALID,
            JDT_SYNTAX_PROCESS_FAILED,
            JDT_SYNTAX_SHUTDOWN_TIMEOUT,
            SOURCE_INVALID)
        .contains(code)) {
      throw new IllegalArgumentException("unknown code engine failure code");
    }
    return code;
  }

  private static String requireDetail(String detail) {
    if (detail == null || detail.isBlank()) {
      throw new IllegalArgumentException("code engine failure detail is required");
    }
    return detail;
  }
}
