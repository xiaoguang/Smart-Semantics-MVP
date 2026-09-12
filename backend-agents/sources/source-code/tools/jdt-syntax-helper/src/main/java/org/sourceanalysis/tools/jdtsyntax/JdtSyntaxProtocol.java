package org.sourceanalysis.tools.jdtsyntax;

import java.util.List;

/** Private JSONL protocol between the Java 17 host and the JDT Core helper. */
public final class JdtSyntaxProtocol {

  public static final String VERSION = "jdt-syntax-v1";
  public static final String DESCRIBE_COMPILATION_UNIT = "DESCRIBE_COMPILATION_UNIT";

  private JdtSyntaxProtocol() {}

  public record Request(
      String protocolVersion,
      String operation,
      String requestId,
      String sourceKey,
      String languageLevel,
      String sourceSha256,
      String text) {}

  public record Response(
      String protocolVersion,
      String requestId,
      String sourceKey,
      String sourceSha256,
      String packageName,
      List<ImportView> imports,
      List<Declaration> declarations,
      List<CallSiteView> callSites,
      List<ControlView> controls,
      List<ExitView> exits,
      List<Diagnostic> diagnostics) {}

  public record ImportView(
      String text, String name, boolean onDemand, boolean isStatic, SourceRange sourceRange) {}

  public record Declaration(
      String localId,
      String kind,
      String name,
      String declaringTypeName,
      String enclosingDeclarationId,
      String signature,
      List<String> modifiers,
      List<String> annotations,
      List<ParameterView> parameters,
      String returnTypeText,
      SourceRange sourceRange,
      String sourceText,
      boolean bodyPresent) {}

  public record ParameterView(
      int ordinal,
      String name,
      String typeText,
      boolean varArgs,
      List<String> annotationTexts,
      SourceRange sourceRange) {}

  public record CallSiteView(
      String localId,
      String ownerDeclarationId,
      String kind,
      SourceRange sourceRange,
      SourceRange navigationRange,
      String expression,
      String receiverExpression,
      List<String> actualArguments,
      boolean deferred) {}

  public record ControlView(
      String ownerDeclarationId,
      int index,
      String kind,
      String expression,
      SourceRange sourceRange,
      Integer parentControlIndex) {}

  public record ExitView(
      String ownerDeclarationId, String kind, String expression, SourceRange sourceRange) {}

  public record Diagnostic(String code, String severity, String message, SourceRange sourceRange) {}

  public record SourceRange(int startOffsetUtf16, int lengthUtf16, int startLine, int endLine) {
    public int endOffsetUtf16() {
      return startOffsetUtf16 + lengthUtf16;
    }
  }

  public static final class ProtocolException extends RuntimeException {
    public ProtocolException(String message) {
      super(message);
    }

    public ProtocolException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
