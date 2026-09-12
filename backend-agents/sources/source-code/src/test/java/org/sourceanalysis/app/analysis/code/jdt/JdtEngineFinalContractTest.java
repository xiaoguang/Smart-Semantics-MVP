package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;

/** Final Task 1 RED tests for the neutral Java-engine material contract. */
class JdtEngineFinalContractTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void serializedNeutralMaterialCarriesTheCompleteMethodCallTargetAndCatalogContract() {
    EntryCodeContext.MethodCode entryMethod = method("method:entry");
    EntryCodeContext.MethodCode calleeMethod = method("method:callee");
    EntryCodeContext.CallTarget target =
        new EntryCodeContext.CallTarget(
            "method:callee", List.of("DECLARATION"), "BODY_INCLUDED", null);
    EntryCodeContext.CallSite call =
        new EntryCodeContext.CallSite(
            "call:entry:1",
            "method:entry",
            "METHOD",
            range(30, 21, 2, 2),
            range(38, 6, 2, 2),
            "service.handle(request)",
            List.of(target));
    EntryCodeContext context =
        new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            "entry:example",
            "method:entry",
            List.of(entryMethod, calleeMethod),
            List.of(call),
            List.of(),
            List.of(),
            new EntryCodeContext.TechnicalEnhancements(
                EntryCodeContext.Availability.NOT_PRODUCED,
                "strict graph is not produced by the selected engine",
                List.of(),
                List.of(),
                null));

    JsonNode contextJson = JSON.valueToTree(context);
    Set<String> methodFields = fieldNames(contextJson.at("/methods/0"));
    Set<String> sourceFields = fieldNames(contextJson.at("/methods/0/source"));
    Set<String> callFields = fieldNames(contextJson.at("/calls/0"));
    Set<String> targetFields = fieldNames(contextJson.at("/calls/0/targets/0"));

    assertThat(methodFields)
        .contains(
            "methodKey",
            "signature",
            "enclosingMethodKey",
            "modifiers",
            "annotations",
            "controls",
            "exits");
    assertThat(sourceFields)
        .contains("path", "startLine", "endLine", "startOffsetUtf16", "lengthUtf16", "text");
    assertThat(callFields)
        .contains(
            "receiverExpression",
            "actualArguments",
            "enclosingControlIndexes",
            "deferred",
            "resolution",
            "resolutionDetail");
    assertThat(targetFields).contains("displayName", "navigationKinds", "argumentAssociations");

    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            "snapshot:example",
            List.of("src/main/java/example/Controller.java"),
            List.of(
                new JavaDeclarationCatalog.TypeDeclaration(
                    "src/main/java/example/Controller.java",
                    range(0, 90, 1, 5),
                    "example.Controller",
                    "CLASS",
                    List.of("annotation:rest-controller"),
                    List.of("field:service"),
                    List.of("method:entry"),
                    List.of())),
            List.of(
                new JavaDeclarationCatalog.MethodDeclarationView(
                    "method:entry",
                    "example.Controller",
                    "handle",
                    "METHOD",
                    List.of(
                        new JavaDeclarationCatalog.ParameterView(
                            0, "request", "Request", false, List.of())),
                    "Response",
                    List.of("annotation:post-mapping"),
                    "src/main/java/example/Controller.java",
                    range(0, 90, 1, 5),
                    true)),
            List.of(
                new JavaDeclarationCatalog.AnnotationView(
                    "annotation:post-mapping",
                    "PostMapping",
                    "org.springframework.web.bind.annotation.PostMapping",
                    "value=\"/example\", method=RequestMethod.POST",
                    range(0, 35, 1, 1),
                    range(1, 11, 1, 1))),
            List.of(
                new JavaDeclarationCatalog.FieldDeclarationView(
                    "field:service",
                    "service",
                    "ExampleService",
                    List.of(),
                    null,
                    "src/main/java/example/Controller.java",
                    range(40, 25, 3, 3))),
            Map.of());

    JsonNode catalogJson = JSON.valueToTree(catalog);
    assertThat(fieldNames(catalogJson.at("/types/0"))).contains("sourcePath", "sourceRange");
    assertThat(fieldNames(catalogJson.at("/methods/0"))).contains("modifiers", "sourcePath");
    assertThat(fieldNames(catalogJson.at("/annotations/0"))).contains("staticValues", "sourcePath");
  }

  @Test
  void parameterSerializationCarriesSourceAnnotationTexts() {
    JsonNode parameter =
        JSON.valueToTree(
            new JavaDeclarationCatalog.ParameterView(
                0, "request", "Request", false, List.of("@Valid")));

    assertThat(fieldNames(parameter)).contains("annotationTexts");
  }

  @Test
  void entryContextRejectsDuplicateMethodKeys() {
    EntryCodeContext.MethodCode first = method("method:entry");
    EntryCodeContext.MethodCode duplicate = method("method:entry");

    assertThatThrownBy(
            () ->
                context(
                    "method:entry",
                    List.of(first, duplicate),
                    List.of(),
                    notProducedEnhancements()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate");
  }

  @Test
  void entryContextRejectsDanglingCallerAndTargetReferences() {
    EntryCodeContext.CallSite danglingCaller =
        new EntryCodeContext.CallSite(
            "call:dangling-caller",
            "method:missing",
            "METHOD",
            range(30, 4, 2, 2),
            range(30, 4, 2, 2),
            "missing()",
            List.of());
    EntryCodeContext.CallSite danglingTarget =
        new EntryCodeContext.CallSite(
            "call:dangling-target",
            "method:entry",
            "METHOD",
            range(30, 4, 2, 2),
            range(30, 4, 2, 2),
            "missing()",
            List.of(
                new EntryCodeContext.CallTarget(
                    "method:missing",
                    List.of("DECLARATION"),
                    "DECLARATION_ONLY",
                    "missing source declaration")));

    assertThatThrownBy(
            () ->
                context(
                    "method:entry",
                    List.of(method("method:entry")),
                    List.of(danglingCaller),
                    notProducedEnhancements()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("caller");
    assertThatThrownBy(
            () ->
                context(
                    "method:entry",
                    List.of(method("method:entry")),
                    List.of(danglingTarget),
                    notProducedEnhancements()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("target");
  }

  @Test
  void availableEnhancementsRequireRealSameSnapshotReferences() {
    assertThatThrownBy(
            () ->
                context(
                    "method:entry",
                    List.of(method("method:entry")),
                    List.of(),
                    new EntryCodeContext.TechnicalEnhancements(
                        EntryCodeContext.Availability.AVAILABLE, null, List.of(), List.of(), null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reference");
  }

  @Test
  void entryContextRejectsAIncludedTargetWhenItsReferencedMethodHasNoBody() {
    EntryCodeContext.CallTarget target =
        new EntryCodeContext.CallTarget(
            "method:callee",
            List.of("DECLARATION"),
            "callee",
            List.of("ENGINE_BINDING"),
            "BODY_INCLUDED",
            null,
            List.of());
    EntryCodeContext.CallSite call = call("call:body", target, List.of(), List.of());

    assertThatThrownBy(
            () ->
                context(
                    "method:entry",
                    List.of(method("method:entry"), methodWithoutBody("method:callee")),
                    List.of(call),
                    notProducedEnhancements()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("body");
  }

  @Test
  void entryContextRejectsOutOfRangeControlAndArgumentAssociations() {
    EntryCodeContext.CallTarget target =
        new EntryCodeContext.CallTarget(
            "method:callee",
            List.of("IMPLEMENTATION"),
            "callee",
            List.of("ENGINE_BINDING"),
            "BODY_INCLUDED",
            null,
            List.of());
    EntryCodeContext.CallSite invalidControl = call("call:control", target, List.of(), List.of(1));

    assertThatThrownBy(
            () ->
                context(
                    "method:entry",
                    List.of(methodWithControl("method:entry"), method("method:callee")),
                    List.of(invalidControl),
                    notProducedEnhancements()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("control");

    EntryCodeContext.CallTarget invalidActual = targetWithAssociation(List.of(1), 0);
    EntryCodeContext.CallSite invalidActualCall =
        call(
            "call:actual",
            invalidActual,
            List.of(new EntryCodeContext.ActualArgument(0, "request")),
            List.of());
    assertThatThrownBy(
            () ->
                context(
                    "method:entry",
                    List.of(method("method:entry"), method("method:callee")),
                    List.of(invalidActualCall),
                    notProducedEnhancements()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actual");

    EntryCodeContext.CallTarget invalidFormal = targetWithAssociation(List.of(0), 1);
    EntryCodeContext.CallSite invalidFormalCall =
        call(
            "call:formal",
            invalidFormal,
            List.of(new EntryCodeContext.ActualArgument(0, "request")),
            List.of());
    assertThatThrownBy(
            () ->
                context(
                    "method:entry",
                    List.of(method("method:entry"), method("method:callee")),
                    List.of(invalidFormalCall),
                    notProducedEnhancements()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("formal");
  }

  private static EntryCodeContext.CallSite call(
      String callKey,
      EntryCodeContext.CallTarget target,
      List<EntryCodeContext.ActualArgument> actualArguments,
      List<Integer> enclosingControlIndexes) {
    return new EntryCodeContext.CallSite(
        callKey,
        "method:entry",
        "METHOD",
        range(30, 21, 2, 2),
        range(38, 6, 2, 2),
        "service.handle(request)",
        "service",
        actualArguments,
        enclosingControlIndexes,
        false,
        List.of(target),
        "LOCATED",
        null);
  }

  private static EntryCodeContext.CallTarget targetWithAssociation(
      List<Integer> actualOrdinals, int formalOrdinal) {
    return new EntryCodeContext.CallTarget(
        "method:callee",
        List.of("IMPLEMENTATION"),
        "callee",
        List.of("ENGINE_BINDING"),
        "BODY_INCLUDED",
        null,
        List.of(
            new EntryCodeContext.ArgumentAssociation(actualOrdinals, formalOrdinal, "POSITIONAL")));
  }

  private static EntryCodeContext context(
      String entryMethodKey,
      List<EntryCodeContext.MethodCode> methods,
      List<EntryCodeContext.CallSite> calls,
      EntryCodeContext.TechnicalEnhancements enhancements) {
    return new EntryCodeContext(
        EntryCodeContext.SCHEMA_VERSION,
        "entry:example",
        entryMethodKey,
        methods,
        calls,
        List.of(),
        List.of(),
        enhancements);
  }

  private static EntryCodeContext.TechnicalEnhancements notProducedEnhancements() {
    return new EntryCodeContext.TechnicalEnhancements(
        EntryCodeContext.Availability.NOT_PRODUCED, "not produced", List.of(), List.of(), null);
  }

  private static EntryCodeContext.MethodCode method(String methodKey) {
    return new EntryCodeContext.MethodCode(
        methodKey,
        "METHOD",
        "example.Controller",
        "handle",
        List.of(
            new JavaDeclarationCatalog.ParameterView(0, "request", "Request", false, List.of())),
        "Response",
        new EntryCodeContext.SourceSource(
            "src/main/java/example/Controller.java",
            range(0, 90, 1, 5),
            "Response handle(Request request) { return service.handle(request); }"),
        true);
  }

  private static EntryCodeContext.MethodCode methodWithoutBody(String methodKey) {
    return new EntryCodeContext.MethodCode(
        methodKey,
        "METHOD",
        "example.Service",
        "handle",
        List.of(
            new JavaDeclarationCatalog.ParameterView(0, "request", "Request", false, List.of())),
        "Response",
        new EntryCodeContext.SourceSource(
            "src/main/java/example/Service.java",
            range(0, 38, 1, 1),
            "Response handle(Request request);"),
        false);
  }

  private static EntryCodeContext.MethodCode methodWithControl(String methodKey) {
    return new EntryCodeContext.MethodCode(
        methodKey,
        "METHOD",
        "example.Controller",
        "handle",
        "Response handle(Request request) { if (ready) return service.handle(request); }",
        null,
        List.of(
            new JavaDeclarationCatalog.ParameterView(0, "request", "Request", false, List.of())),
        "Response",
        List.of(),
        List.of(),
        new EntryCodeContext.SourceSource(
            "src/main/java/example/Controller.java",
            range(0, 90, 1, 5),
            "Response handle(Request request) { if (ready) return service.handle(request); }"),
        true,
        List.of(new EntryCodeContext.Control("IF", "ready", range(30, 12, 2, 2), null)),
        List.of());
  }

  private static SourceRange range(int offset, int length, int startLine, int endLine) {
    return new SourceRange(offset, length, startLine, endLine);
  }

  private static Set<String> fieldNames(JsonNode object) {
    return StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(object.fieldNames(), 0), false)
        .collect(Collectors.toSet());
  }
}
