package org.sourceanalysis.app.analysis.code.jdt;

import java.util.List;
import java.util.Map;

/** Private JSONL protocol shared by the Java 17 host and the standalone JDT Core helper. */
final class JdtSyntaxProtocol {

  static final String VERSION = "jdt-syntax-v2";
  static final String DESCRIBE_COMPILATION_UNIT = "DESCRIBE_COMPILATION_UNIT";

  private JdtSyntaxProtocol() {}

  record Request(
      String protocolVersion,
      String operation,
      String requestId,
      String sourceKey,
      String languageLevel,
      String sourceSha256,
      List<String> sourcepathEntries,
      List<String> classpathEntries,
      String text) {}

  record Response(
      String protocolVersion,
      String requestId,
      String sourceKey,
      String sourceSha256,
      String packageName,
      List<ImportView> imports,
      List<Declaration> declarations,
      List<AnnotationView> annotations,
      List<CallSiteView> callSites,
      List<ControlView> controls,
      List<ExitView> exits,
      List<Diagnostic> diagnostics) {}

  record ImportView(
      String text, String name, boolean onDemand, boolean isStatic, SourceRange sourceRange) {}

  record AnnotationView(
      String localId,
      String ownerDeclarationId,
      String nameText,
      String qualifiedName,
      SourceRange sourceRange,
      SourceRange nameSelection,
      String memberSource,
      Map<String, StaticValue> staticValues) {}

  record StaticValue(String kind, String source, String value, List<StaticValue> elements) {}

  record Declaration(
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
      SourceRange navigationRange,
      SourceRange sourceRange,
      String sourceText,
      boolean bodyPresent) {}

  record ParameterView(
      int ordinal,
      String name,
      String typeText,
      boolean varArgs,
      List<String> annotationTexts,
      SourceRange sourceRange) {}

  record CallSiteView(
      String localId,
      String ownerDeclarationId,
      String kind,
      SourceRange sourceRange,
      SourceRange navigationRange,
      String expression,
      String receiverExpression,
      List<String> actualArguments,
      boolean deferred) {}

  record ControlView(
      String ownerDeclarationId,
      int index,
      String kind,
      String expression,
      SourceRange sourceRange,
      Integer parentControlIndex) {}

  record ExitView(
      String ownerDeclarationId, String kind, String expression, SourceRange sourceRange) {}

  record Diagnostic(String code, String severity, String message, SourceRange sourceRange) {}

  record SourceRange(int startOffsetUtf16, int lengthUtf16, int startLine, int endLine) {
    int endOffsetUtf16() {
      return startOffsetUtf16 + lengthUtf16;
    }
  }
}
