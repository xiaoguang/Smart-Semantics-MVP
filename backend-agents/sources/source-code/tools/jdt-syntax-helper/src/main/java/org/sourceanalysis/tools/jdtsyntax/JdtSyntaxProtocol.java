package org.sourceanalysis.tools.jdtsyntax;

import java.util.List;
import java.util.Map;

/** Private JSONL protocol between the Java 17 host and the JDT Core helper. */
public final class JdtSyntaxProtocol {

  public static final String VERSION = "jdt-syntax-v4";
  public static final String DESCRIBE_COMPILATION_UNIT = "DESCRIBE_COMPILATION_UNIT";

  private JdtSyntaxProtocol() {}

  public record Request(
      String protocolVersion,
      String operation,
      String requestId,
      String sourceKey,
      String languageLevel,
      String sourceSha256,
      List<String> sourcepathEntries,
      List<String> classpathEntries,
      String targetJdkVersion,
      List<String> targetPlatformEntries,
      String text) {}

  public record Response(
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

  public record ImportView(
      String text, String name, boolean onDemand, boolean isStatic, SourceRange sourceRange) {}

  public record AnnotationView(
      String localId,
      String ownerDeclarationId,
      String nameText,
      String qualifiedName,
      SourceRange sourceRange,
      SourceRange nameSelection,
      String memberSource,
      Map<String, StaticValue> staticValues) {}

  public record StaticValue(String kind, String source, String value, List<StaticValue> elements) {}

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
      SourceRange navigationRange,
      SourceRange sourceRange,
      String sourceText,
      boolean bodyPresent,
      BindingObservation binding) {

    public Declaration {
      if (binding == null) {
        throw new IllegalArgumentException("declaration binding observation is required");
      }
    }

    public Declaration(
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
        boolean bodyPresent) {
      this(
          localId,
          kind,
          name,
          declaringTypeName,
          enclosingDeclarationId,
          signature,
          modifiers,
          annotations,
          parameters,
          returnTypeText,
          navigationRange,
          sourceRange,
          sourceText,
          bodyPresent,
          BindingObservation.absent());
    }
  }

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
      boolean deferred,
      BindingObservation binding) {

    public CallSiteView {
      if (binding == null) {
        throw new IllegalArgumentException("call-site binding observation is required");
      }
    }

    public CallSiteView(
        String localId,
        String ownerDeclarationId,
        String kind,
        SourceRange sourceRange,
        SourceRange navigationRange,
        String expression,
        String receiverExpression,
        List<String> actualArguments,
        boolean deferred) {
      this(
          localId,
          ownerDeclarationId,
          kind,
          sourceRange,
          navigationRange,
          expression,
          receiverExpression,
          actualArguments,
          deferred,
          BindingObservation.absent());
    }
  }

  public enum BindingState {
    RESOLVED,
    ABSENT,
    RECOVERED,
    UNSUPPORTED_CALL_KIND
  }

  public record BindingObservation(
      BindingState state,
      String declarationKey,
      String declaringTypeKey,
      String typeOrigin,
      String displayIdentity) {

    public BindingObservation {
      if (state == null) {
        throw new IllegalArgumentException("binding state is required");
      }
      if (state == BindingState.RESOLVED
          && (blank(declarationKey)
              || blank(declaringTypeKey)
              || blank(typeOrigin)
              || blank(displayIdentity))) {
        throw new IllegalArgumentException("resolved binding observation requires an identity");
      }
    }

    public static BindingObservation absent() {
      return new BindingObservation(BindingState.ABSENT, null, null, null, null);
    }

    private static boolean blank(String value) {
      return value == null || value.isBlank();
    }
  }

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
