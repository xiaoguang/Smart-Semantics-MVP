package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;

class EntryCodeCollectorTest {

  @Test
  void resolvedCallBindingMustMatchDirectDeclarationBeforeCollectorExpandsIt() {
    Fixture fixture =
        withBindings(
            Fixture.standard(),
            Map.of(
                "save-1", BindingSpec.resolved("binding:Service.save"),
                "save-2", BindingSpec.resolved("binding:Service.save")),
            Map.of(
                "service-save", BindingSpec.resolved("binding:Service.save"),
                "impl-save", BindingSpec.resolved("binding:ServiceImpl.save")));

    EntryCodeCollector collector = fixture.collector(CollectionBudget.standard());
    JavaDeclarationCatalog.MethodDeclarationView entry =
        collector.catalog().methods().stream()
            .filter(method -> method.sourcePath().equals("Controller.java"))
            .findFirst()
            .orElseThrow();
    EntryCodeContext context =
        collector.collect(
            new EntrySeed("entry:binding-match", entry.methodKey(), entry.sourceRange(), "HTTP"));

    assertThat(context.methods())
        .extracting(EntryCodeContext.MethodCode::name)
        .contains("start", "save");
    assertThat(
            context.calls().stream()
                .filter(call -> call.expression().startsWith("service.save"))
                .toList())
        .hasSize(2)
        .allSatisfy(
            call -> {
              assertThat(call.targets()).hasSize(2);
              assertThat(call.targets())
                  .filteredOn(target -> "Service.save".equals(target.displayName()))
                  .singleElement()
                  .extracting(EntryCodeContext.CallTarget::expansion)
                  .isEqualTo("DECLARATION_ONLY");
              assertThat(call.targets())
                  .filteredOn(target -> "ServiceImpl.save".equals(target.displayName()))
                  .singleElement()
                  .extracting(EntryCodeContext.CallTarget::expansion)
                  .isEqualTo("BODY_INCLUDED");
            });
  }

  @Test
  void mismatchedDirectBindingRemainsObservationAndCannotExpandARepositoryTarget() {
    Fixture fixture =
        withBindings(
            Fixture.standard(),
            Map.of(
                "save-1", BindingSpec.resolved("binding:Other.save"),
                "save-2", BindingSpec.resolved("binding:Other.save")),
            Map.of(
                "service-save", BindingSpec.resolved("binding:Service.save"),
                "impl-save", BindingSpec.resolved("binding:ServiceImpl.save")));

    EntryCodeCollector collector = fixture.collector(CollectionBudget.standard());
    JavaDeclarationCatalog.MethodDeclarationView entry =
        collector.catalog().methods().stream()
            .filter(method -> method.sourcePath().equals("Controller.java"))
            .findFirst()
            .orElseThrow();
    EntryCodeContext context =
        collector.collect(
            new EntrySeed(
                "entry:binding-mismatch", entry.methodKey(), entry.sourceRange(), "HTTP"));

    assertThat(context.methods())
        .extracting(EntryCodeContext.MethodCode::name)
        .containsExactly("start");
    assertThat(
            context.calls().stream()
                .filter(call -> call.expression().startsWith("service.save"))
                .toList())
        .hasSize(2)
        .allSatisfy(
            call ->
                assertThat(call.targets())
                    .hasSize(2)
                    .allSatisfy(
                        target -> assertThat(target.expansion()).isNotEqualTo("BODY_INCLUDED")));
  }

  @Test
  void singleMismatchedDirectBindingIsNotReportedAsLocated() {
    Fixture fixture =
        withBindings(
            Fixture.uniqueDeclaration(),
            Map.of(
                "save-1", BindingSpec.resolved("binding:Other.save"),
                "save-2", BindingSpec.resolved("binding:Other.save")),
            Map.of("service-save", BindingSpec.resolved("binding:Service.save")));

    EntryCodeCollector collector = fixture.collector(CollectionBudget.standard());
    JavaDeclarationCatalog.MethodDeclarationView entry =
        collector.catalog().methods().stream()
            .filter(method -> method.sourcePath().equals("Controller.java"))
            .findFirst()
            .orElseThrow();
    EntryCodeContext context =
        collector.collect(
            new EntrySeed(
                "entry:single-binding-mismatch", entry.methodKey(), entry.sourceRange(), "HTTP"));

    assertThat(context.calls())
        .filteredOn(call -> call.expression().startsWith("service.save"))
        .hasSize(2)
        .allSatisfy(
            call -> {
              assertThat(call.resolution()).isEqualTo("NAVIGATION_CONFLICT");
              assertThat(call.targets())
                  .singleElement()
                  .satisfies(
                      target -> {
                        assertThat(target.expansion()).isEqualTo("NOT_EXPANDED");
                        assertThat(target.reason()).isEqualTo("BINDING_DECLARATION_MISMATCH");
                      });
              assertThat(call.observations())
                  .extracting(EntryCodeContext.CallObservation::detail)
                  .contains("BINDING_DECLARATION_MISMATCH");
            });
  }

  @Test
  void absentOrRecoveredBindingMayUseOnlyOneExactDefinitionAsALimitedFallback() {
    for (BindingSpec callBinding : List.of(BindingSpec.absent(), BindingSpec.recovered())) {
      Fixture fixture =
          withBindings(
              Fixture.uniqueDeclaration(),
              Map.of("save-1", callBinding, "save-2", callBinding),
              Map.of("service-save", BindingSpec.resolved("binding:Service.save")));
      EntryCodeCollector collector = fixture.collector(CollectionBudget.standard());
      JavaDeclarationCatalog.MethodDeclarationView entry =
          collector.catalog().methods().stream()
              .filter(method -> method.sourcePath().equals("Controller.java"))
              .findFirst()
              .orElseThrow();
      EntryCodeContext context =
          collector.collect(
              new EntrySeed(
                  "entry:binding-fallback-" + callBinding.state(),
                  entry.methodKey(),
                  entry.sourceRange(),
                  "HTTP"));

      assertThat(context.methods()).extracting(EntryCodeContext.MethodCode::name).contains("save");
      assertThat(
              context.calls().stream()
                  .filter(call -> call.expression().startsWith("service.save"))
                  .toList())
          .hasSize(2)
          .allSatisfy(
              call ->
                  assertThat(call.targets())
                      .singleElement()
                      .extracting(EntryCodeContext.CallTarget::expansion)
                      .isEqualTo("DECLARATION_ONLY"));
    }
  }

  @Test
  void nestedCallsUseTheirOwnBindingAndDoNotBorrowTheOuterTarget() {
    Fixture fixture =
        withBindings(
            Fixture.nested(),
            Map.of(
                "outer", BindingSpec.resolved("binding:PageDomain.setPageSize"),
                "inner", BindingSpec.resolved("binding:Convert.toInt")),
            Map.of(
                "page-size", BindingSpec.resolved("binding:PageDomain.setPageSize"),
                "to-int", BindingSpec.resolved("binding:Convert.toInt")));
    EntryCodeCollector collector = fixture.collector(CollectionBudget.standard());
    JavaDeclarationCatalog.MethodDeclarationView entry =
        collector.catalog().methods().stream()
            .filter(method -> method.sourcePath().equals("Controller.java"))
            .findFirst()
            .orElseThrow();
    EntryCodeContext context =
        collector.collect(
            new EntrySeed("entry:nested-binding", entry.methodKey(), entry.sourceRange(), "HTTP"));

    assertThat(context.methods())
        .extracting(EntryCodeContext.MethodCode::name)
        .contains("run", "setPageSize", "toInt");
    EntryCodeContext.CallSite outer =
        context.calls().stream()
            .filter(call -> call.expression().startsWith("page.setPageSize"))
            .findFirst()
            .orElseThrow();
    EntryCodeContext.CallSite inner =
        context.calls().stream()
            .filter(call -> call.expression().startsWith("Convert.toInt"))
            .findFirst()
            .orElseThrow();
    assertThat(outer.targets())
        .singleElement()
        .extracting(EntryCodeContext.CallTarget::displayName)
        .isEqualTo("PageDomain.setPageSize");
    assertThat(inner.targets()).hasSize(2);
    assertThat(inner.targets())
        .filteredOn(target -> "PageDomain.setPageSize".equals(target.displayName()))
        .singleElement()
        .satisfies(
            target -> {
              assertThat(target.expansion()).isEqualTo("NOT_EXPANDED");
              assertThat(target.reason()).isEqualTo("NAVIGATION_CONFLICT_NOT_EXPANDED");
            });
    assertThat(
            inner.targets().stream()
                .filter(target -> "BODY_INCLUDED".equals(target.expansion()))
                .toList())
        .singleElement()
        .extracting(EntryCodeContext.CallTarget::displayName)
        .isEqualTo("Convert.toInt");
  }

  @Test
  void resolvedBinaryBindingWithNoRepositoryLocationsIsAnExternalObservation() {
    Fixture fixture =
        withBindings(
            Fixture.external(),
            Map.of(
                "value-of",
                BindingSpec.binary(
                    "java.base/java.lang.String.valueOf(java.lang.String)",
                    "java.lang.String.valueOf")),
            Map.of());

    EntryCodeCollector collector = fixture.collector(CollectionBudget.standard());
    JavaDeclarationCatalog.MethodDeclarationView entry =
        collector.catalog().methods().stream()
            .filter(method -> method.sourcePath().equals("Controller.java"))
            .findFirst()
            .orElseThrow();
    EntryCodeContext context =
        collector.collect(
            new EntrySeed(
                "entry:external-string-value-of", entry.methodKey(), entry.sourceRange(), "HTTP"));

    assertThat(context.calls())
        .singleElement()
        .satisfies(
            external -> {
              assertThat(external.resolution()).isEqualTo("EXTERNAL");
              assertThat(external.targets()).isEmpty();
              Object observations = recordComponent(external, "observations");
              assertThat(containsStringValue(observations, "java.lang.String.valueOf"))
                  .as("v3 call observation must retain the Core binary method identity")
                  .isTrue();
            });
    assertThat(context.limitations())
        .extracting(EntryCodeContext.Limitation::code)
        .doesNotContain("UNRESOLVED_CALL");
  }

  @Test
  void externalObservationMustBeConfirmedBinaryIdentity() {
    SourceRange range = new SourceRange(0, 16, 1, 1);

    assertThatThrownBy(
            () ->
                new EntryCodeContext.CallSite(
                    "call:malformed-external-association",
                    "method:entry",
                    "METHOD",
                    range,
                    range,
                    "String.valueOf",
                    "String",
                    List.of(),
                    List.of(),
                    false,
                    List.of(),
                    "EXTERNAL",
                    "malformed external observation",
                    List.of(
                        new EntryCodeContext.CallObservation(
                            "EXTERNAL_BINARY_BINDING",
                            "JDT_CORE_BINDING",
                            "BINARY",
                            range,
                            "UNCONFIRMED",
                            "java.base/java.lang.String.valueOf(java.lang.String)",
                            "java.lang.String",
                            "BINARY",
                            "java.lang.String.valueOf",
                            "unconfirmed"))))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                new EntryCodeContext.CallSite(
                    "call:malformed-external-origin",
                    "method:entry",
                    "METHOD",
                    range,
                    range,
                    "String.valueOf",
                    "String",
                    List.of(),
                    List.of(),
                    false,
                    List.of(),
                    "EXTERNAL",
                    "malformed external observation",
                    List.of(
                        new EntryCodeContext.CallObservation(
                            "EXTERNAL_BINARY_BINDING",
                            "JDT_CORE_BINDING",
                            "SOURCE",
                            range,
                            "CONFIRMED",
                            "java.base/java.lang.String.valueOf(java.lang.String)",
                            "java.lang.String",
                            "SOURCE",
                            "java.lang.String.valueOf",
                            "source is not a Core binary identity"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void emptyRepositoryLocationsWithoutBindingRemainUnresolved() {
    Fixture fixture =
        withBindings(Fixture.external(), Map.of("value-of", BindingSpec.absent()), Map.of());

    EntryCodeCollector collector = fixture.collector(CollectionBudget.standard());
    JavaDeclarationCatalog.MethodDeclarationView entry =
        collector.catalog().methods().stream()
            .filter(method -> method.sourcePath().equals("Controller.java"))
            .findFirst()
            .orElseThrow();
    EntryCodeContext context =
        collector.collect(
            new EntrySeed(
                "entry:unresolved-string-value-of",
                entry.methodKey(),
                entry.sourceRange(),
                "HTTP"));

    assertThat(context.calls()).hasSize(1);
    EntryCodeContext.CallSite unresolved = context.calls().get(0);
    assertThat(unresolved.resolution()).isEqualTo("UNRESOLVED");
    assertThat(unresolved.targets()).isEmpty();
    assertThat(context.limitations())
        .extracting(EntryCodeContext.Limitation::code)
        .contains("UNRESOLVED_CALL");
  }

  @Test
  void requiredNavigationFailureWinsOverBinaryExternalClassification() {
    Fixture fixture = Fixture.external();
    fixture.gateway().definitionFailure =
        new CodeEngineException(
            CodeEngineException.JDT_QUERY_FAILED, "simulated binary definition query failure");
    fixture =
        withBindings(
            fixture,
            Map.of(
                "value-of",
                BindingSpec.binary(
                    "java.base/java.lang.String.valueOf(java.lang.String)",
                    "java.lang.String.valueOf")),
            Map.of());

    EntryCodeCollector collector = fixture.collector(CollectionBudget.standard());
    JavaDeclarationCatalog.MethodDeclarationView entry =
        collector.catalog().methods().stream()
            .filter(method -> method.sourcePath().equals("Controller.java"))
            .findFirst()
            .orElseThrow();
    EntryCodeContext context =
        collector.collect(
            new EntrySeed(
                "entry:binary-query-failure", entry.methodKey(), entry.sourceRange(), "HTTP"));

    assertThat(context.calls())
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.resolution()).isEqualTo("QUERY_FAILED");
              assertThat(call.targets()).isEmpty();
              assertThat(
                      containsStringValue(
                          recordComponent(call, "observations"), "java.lang.String.valueOf"))
                  .isTrue();
            });
    assertThat(context.limitations())
        .extracting(EntryCodeContext.Limitation::code)
        .contains("QUERY_FAILED");
  }

  @Test
  void collectsCompleteBodiesDuplicateCallsCandidatesArgumentsControlsAndBoundaries() {
    Fixture fixture = Fixture.standard();
    EntryCodeCollector collector = fixture.collector(CollectionBudget.standard());
    JavaDeclarationCatalog.MethodDeclarationView entry =
        collector.catalog().methods().stream()
            .filter(method -> method.sourcePath().equals("Controller.java"))
            .findFirst()
            .orElseThrow();

    EntryCodeContext context =
        collector.collect(
            new EntrySeed("entry:register", entry.methodKey(), entry.sourceRange(), "HTTP"));

    assertThat(context.entryMethodKey()).isEqualTo(entry.methodKey());
    assertThat(context.methods())
        .extracting(EntryCodeContext.MethodCode::name)
        .containsExactly("start", "save", "save");
    assertThat(context.methods().get(2).source().text())
        .contains("if (id == null)")
        .contains("repository.find(id)");
    assertThat(context.methods().get(2).controls())
        .extracting(EntryCodeContext.Control::kind)
        .containsExactly("IF");
    assertThat(context.methods().get(2).exits())
        .extracting(EntryCodeContext.Exit::kind)
        .containsExactly("THROW", "RETURN");

    List<EntryCodeContext.CallSite> controllerCalls =
        context.calls().stream()
            .filter(call -> call.expression().startsWith("service.save"))
            .toList();
    assertThat(controllerCalls).hasSize(2);
    assertThat(controllerCalls)
        .extracting(EntryCodeContext.CallSite::callKey)
        .doesNotHaveDuplicates();
    assertThat(controllerCalls)
        .allSatisfy(
            call -> {
              assertThat(call.actualArguments())
                  .extracting(EntryCodeContext.ActualArgument::expression)
                  .containsExactly("id");
              assertThat(call.targets()).hasSize(2);
              assertThat(call.targets().stream().flatMap(target -> target.roles().stream()))
                  .contains("DECLARATION", "IMPLEMENTATION");
              assertThat(
                      call.targets().stream()
                          .flatMap(target -> target.argumentAssociations().stream()))
                  .allSatisfy(
                      association -> {
                        assertThat(association.actualOrdinals()).containsExactly(0);
                        assertThat(association.formalOrdinal()).isZero();
                      });
            });
    assertThat(context.calls())
        .anySatisfy(
            call -> {
              if (call.expression().equals("repository.find(id)")) {
                assertThat(call.resolution()).isEqualTo("UNRESOLVED");
              }
            });
    assertThat(context.limitations())
        .extracting(EntryCodeContext.Limitation::code)
        .contains("UNRESOLVED_CALL");
  }

  @Test
  void representsCyclesByMethodKeysAndDoesNotDuplicateBodies() {
    Fixture fixture = Fixture.cycle();
    EntryCodeCollector collector = fixture.collector(CollectionBudget.standard());
    JavaDeclarationCatalog.MethodDeclarationView entry = collector.catalog().methods().get(0);

    EntryCodeContext context =
        collector.collect(
            new EntrySeed("entry:cycle", entry.methodKey(), entry.sourceRange(), "HTTP"));

    assertThat(context.methods()).hasSize(2);
    assertThat(context.methods())
        .extracting(EntryCodeContext.MethodCode::methodKey)
        .doesNotHaveDuplicates();
    assertThat(context.calls()).hasSize(2);
    assertThat(context.calls())
        .allSatisfy(
            call -> {
              assertThat(call.targets()).singleElement();
              assertThat(call.targets().get(0).expansion()).isEqualTo("BODY_INCLUDED");
            });
  }

  @Test
  void failsCatalogWhenSyntaxHelperProtocolFailsInsteadOfRecordingFileGap() {
    Fixture fixture = Fixture.standard();
    CodeEngineException protocolFailure =
        new CodeEngineException(
            CodeEngineException.JDT_SYNTAX_PROTOCOL_INVALID, "invalid syntax-helper response");
    EntryCodeCollector collector =
        fixture.collector(
            CollectionBudget.standard(),
            (path, level, source) -> {
              if (path.equals("Controller.java")) {
                throw protocolFailure;
              }
              return fixture.syntax().get(path);
            });

    assertThatThrownBy(collector::catalog)
        .isInstanceOfSatisfying(
            CodeEngineException.class,
            failure ->
                assertThat(failure.code())
                    .isEqualTo(CodeEngineException.JDT_SYNTAX_PROTOCOL_INVALID));
  }

  @Test
  void retainsReturnedSourceSyntaxDiagnosticAsAFileLocalCatalogLimitation() {
    Fixture fixture = Fixture.standard();
    String path = "Controller.java";
    JdtSyntaxProtocol.Response original = fixture.syntax().get(path);
    Map<String, JdtSyntaxProtocol.Response> syntax = new LinkedHashMap<>(fixture.syntax());
    syntax.put(
        path,
        new JdtSyntaxProtocol.Response(
            original.protocolVersion(),
            original.requestId(),
            original.sourceKey(),
            original.sourceSha256(),
            original.packageName(),
            original.imports(),
            original.declarations(),
            original.annotations(),
            original.callSites(),
            original.controls(),
            original.exits(),
            List.of(
                new JdtSyntaxProtocol.Diagnostic(
                    "SOURCE_PARSE_ERROR",
                    "ERROR",
                    "unexpected token",
                    new JdtSyntaxProtocol.SourceRange(0, 1, 1, 1)))));
    Fixture diagnosticFixture =
        new Fixture(fixture.sources(), Map.copyOf(syntax), fixture.gateway());

    JavaDeclarationCatalog catalog =
        diagnosticFixture.collector(CollectionBudget.standard()).catalog();

    assertThat(catalog.fileDiagnostics())
        .containsEntry(path, "SOURCE_PARSE_ERROR:unexpected token");
    assertThat(catalog.methods())
        .extracting(JavaDeclarationCatalog.MethodDeclarationView::sourcePath)
        .contains("Controller.java", "Service.java", "ServiceImpl.java");
  }

  private record BindingSpec(
      String state,
      String declarationKey,
      String declaringTypeKey,
      String typeOrigin,
      String displayIdentity) {

    static BindingSpec resolved(String key) {
      int separator = key.lastIndexOf('.');
      String type = separator < 0 ? key : key.substring(0, separator);
      return new BindingSpec("RESOLVED", key, type, "SOURCE", key);
    }

    static BindingSpec binary(String key, String displayIdentity) {
      return new BindingSpec("RESOLVED", key, "java.lang.String", "BINARY", displayIdentity);
    }

    static BindingSpec absent() {
      return new BindingSpec("ABSENT", null, null, "UNKNOWN", null);
    }

    static BindingSpec recovered() {
      return new BindingSpec("RECOVERED", null, null, "UNKNOWN", null);
    }
  }

  private static Fixture withBindings(
      Fixture fixture,
      Map<String, BindingSpec> callBindings,
      Map<String, BindingSpec> declarationBindings) {
    Class<?> callBindingType = bindingType(JdtSyntaxProtocol.CallSiteView.class);
    Class<?> declarationBindingType = bindingType(JdtSyntaxProtocol.Declaration.class);
    assertThat(declarationBindingType).isEqualTo(callBindingType);
    Map<String, JdtSyntaxProtocol.Response> responses = new LinkedHashMap<>();
    for (Map.Entry<String, JdtSyntaxProtocol.Response> source : fixture.syntax().entrySet()) {
      JdtSyntaxProtocol.Response response = source.getValue();
      List<JdtSyntaxProtocol.Declaration> declarations =
          response.declarations().stream()
              .map(
                  declaration ->
                      copyRecord(
                          declaration,
                          Map.of(
                              "binding",
                              binding(
                                  declarationBindingType,
                                  declarationBindings.getOrDefault(
                                      declaration.localId(),
                                      BindingSpec.resolved("binding:" + declaration.localId()))))))
              .toList();
      List<JdtSyntaxProtocol.CallSiteView> calls =
          response.callSites().stream()
              .map(
                  call ->
                      copyRecord(
                          call,
                          Map.of(
                              "binding",
                              binding(
                                  callBindingType,
                                  callBindings.getOrDefault(
                                      call.localId(),
                                      BindingSpec.resolved("binding:" + call.localId()))))))
              .toList();
      responses.put(
          source.getKey(),
          copyRecord(response, Map.of("declarations", declarations, "callSites", calls)));
    }
    return new Fixture(fixture.sources(), Map.copyOf(responses), fixture.gateway());
  }

  private static Class<?> bindingType(Class<?> recordType) {
    return java.util.Arrays.stream(recordType.getRecordComponents())
        .filter(component -> "binding".equals(component.getName()))
        .map(RecordComponent::getType)
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "jdt-syntax-v4 binding component is required on "
                        + recordType.getSimpleName()));
  }

  private static Object binding(Class<?> bindingType, BindingSpec spec) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (RecordComponent component : bindingType.getRecordComponents()) {
      Object value =
          switch (component.getName()) {
            case "state" -> enumValue(component.getType(), spec.state());
            case "declarationKey" -> spec.declarationKey();
            case "declaringTypeKey" -> spec.declaringTypeKey();
            case "typeOrigin" -> enumValue(component.getType(), spec.typeOrigin());
            case "displayIdentity" -> spec.displayIdentity();
            default -> null;
          };
      values.put(component.getName(), value);
    }
    return newRecord(bindingType, values);
  }

  private static Object enumValue(Class<?> type, String value) {
    if (type.isEnum()) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Object constant = Enum.valueOf((Class) type, value);
      return constant;
    }
    return value;
  }

  private static <T> T copyRecord(T original, Map<String, Object> replacements) {
    Class<?> type = original.getClass();
    Map<String, Object> values = new LinkedHashMap<>();
    for (RecordComponent component : type.getRecordComponents()) {
      try {
        component.getAccessor().setAccessible(true);
        values.put(
            component.getName(),
            replacements.containsKey(component.getName())
                ? replacements.get(component.getName())
                : component.getAccessor().invoke(original));
      } catch (ReflectiveOperationException failure) {
        throw new AssertionError("cannot read record component " + component.getName(), failure);
      }
    }
    @SuppressWarnings("unchecked")
    T copy = (T) newRecord(type, values);
    return copy;
  }

  private static Object newRecord(Class<?> type, Map<String, Object> values) {
    try {
      RecordComponent[] components = type.getRecordComponents();
      Class<?>[] parameterTypes =
          java.util.Arrays.stream(components)
              .map(RecordComponent::getType)
              .toArray(Class<?>[]::new);
      Object[] arguments =
          java.util.Arrays.stream(components)
              .map(component -> values.get(component.getName()))
              .toArray();
      Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
      constructor.setAccessible(true);
      return constructor.newInstance(arguments);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("cannot construct " + type.getName(), failure);
    }
  }

  private static Object recordComponent(Object record, String name) {
    RecordComponent component =
        java.util.Arrays.stream(record.getClass().getRecordComponents())
            .filter(value -> name.equals(value.getName()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "v3 CallSite must expose structured " + name + " observations"));
    try {
      component.getAccessor().setAccessible(true);
      return component.getAccessor().invoke(record);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("cannot read CallSite component " + name, failure);
    }
  }

  private static boolean containsStringValue(Object value, String expected) {
    if (value == null) {
      return false;
    }
    if (value instanceof String text) {
      return text.contains(expected);
    }
    if (value instanceof Iterable<?> values) {
      for (Object item : values) {
        if (containsStringValue(item, expected)) {
          return true;
        }
      }
      return false;
    }
    if (!value.getClass().isRecord()) {
      return false;
    }
    for (RecordComponent component : value.getClass().getRecordComponents()) {
      try {
        component.getAccessor().setAccessible(true);
        if (containsStringValue(component.getAccessor().invoke(value), expected)) {
          return true;
        }
      } catch (ReflectiveOperationException failure) {
        throw new AssertionError("cannot inspect structured observation", failure);
      }
    }
    return false;
  }

  private record Fixture(
      InMemorySources sources,
      Map<String, JdtSyntaxProtocol.Response> syntax,
      RoutingGateway gateway) {

    static Fixture standard() {
      String controller =
          "class Controller { void start(String id) { service.save(id); service.save(id); } }\n";
      String service = "interface Service { Result save(String id); }\n";
      String implementation =
          "class ServiceImpl implements Service { Result save(String id) { if (id == null) { throw bad(); } return repository.find(id); } }\n";
      InMemorySources sources =
          new InMemorySources(
              Map.of(
                  "Controller.java", controller,
                  "Service.java", service,
                  "ServiceImpl.java", implementation));
      JdtSyntaxProtocol.Response controllerSyntax =
          unit(
              "Controller.java",
              controller,
              method(controller, "controller-start", "Controller", "start", "void start", true),
              List.of(
                  call(controller, "controller-start", "save-1", "service.save(id)", 0),
                  call(controller, "controller-start", "save-2", "service.save(id)", 1)),
              List.of(),
              List.of());
      JdtSyntaxProtocol.Declaration serviceMethod =
          method(service, "service-save", "Service", "save", "Result save", false);
      JdtSyntaxProtocol.Response serviceSyntax =
          unit("Service.java", service, serviceMethod, List.of(), List.of(), List.of());
      JdtSyntaxProtocol.Declaration implementationMethod =
          method(implementation, "impl-save", "ServiceImpl", "save", "Result save", true);
      int ifStart = implementation.indexOf("if (");
      int ifEnd = implementation.indexOf("return") - 1;
      int throwStart = implementation.indexOf("throw");
      int throwEnd = implementation.indexOf(';', throwStart) + 1;
      int returnStart = implementation.indexOf("return");
      int returnEnd = implementation.indexOf(';', returnStart) + 1;
      JdtSyntaxProtocol.Response implementationSyntax =
          unit(
              "ServiceImpl.java",
              implementation,
              implementationMethod,
              List.of(call(implementation, "impl-save", "find", "repository.find(id)", 0)),
              List.of(
                  new JdtSyntaxProtocol.ControlView(
                      "impl-save",
                      0,
                      "IF",
                      "id == null",
                      range(implementation, ifStart, ifEnd - ifStart),
                      null)),
              List.of(
                  new JdtSyntaxProtocol.ExitView(
                      "impl-save",
                      "THROW",
                      "bad()",
                      range(implementation, throwStart, throwEnd - throwStart)),
                  new JdtSyntaxProtocol.ExitView(
                      "impl-save",
                      "RETURN",
                      "repository.find(id)",
                      range(implementation, returnStart, returnEnd - returnStart))));
      RoutingGateway gateway = new RoutingGateway(sources);
      gateway.route(
          "Controller.java",
          "save",
          List.of(sources.location("Service.java", serviceMethod, "Service.save")),
          List.of(sources.location("ServiceImpl.java", implementationMethod, "ServiceImpl.save")));
      return new Fixture(
          sources,
          Map.of(
              "Controller.java", controllerSyntax,
              "Service.java", serviceSyntax,
              "ServiceImpl.java", implementationSyntax),
          gateway);
    }

    static Fixture uniqueDeclaration() {
      Fixture fixture = standard();
      JdtSyntaxProtocol.Declaration serviceMethod =
          fixture.syntax().get("Service.java").declarations().get(0);
      fixture
          .gateway()
          .route(
              "Controller.java",
              "save",
              List.of(fixture.sources().location("Service.java", serviceMethod, "Service.save")),
              List.of());
      return fixture;
    }

    static Fixture nested() {
      String controller =
          "class Controller { void run(String id) { page.setPageSize(Convert.toInt(id)); } }\n";
      String page = "class PageDomain { void setPageSize(String id) { } }\n";
      String convert = "class Convert { void toInt(String id) { } }\n";
      InMemorySources sources =
          new InMemorySources(
              Map.of(
                  "Controller.java", controller,
                  "PageDomain.java", page,
                  "Convert.java", convert));
      JdtSyntaxProtocol.Declaration run =
          method(controller, "controller-run", "Controller", "run", "void run", true);
      JdtSyntaxProtocol.Declaration pageMethod =
          method(page, "page-size", "PageDomain", "setPageSize", "void setPageSize", true);
      JdtSyntaxProtocol.Declaration convertMethod =
          method(convert, "to-int", "Convert", "toInt", "void toInt", true);
      JdtSyntaxProtocol.Response controllerSyntax =
          unit(
              "Controller.java",
              controller,
              run,
              List.of(
                  call(
                      controller,
                      "controller-run",
                      "outer",
                      "page.setPageSize(Convert.toInt(id))",
                      0),
                  call(controller, "controller-run", "inner", "Convert.toInt(id)", 0)),
              List.of(),
              List.of());
      JdtSyntaxProtocol.Response pageSyntax =
          unit("PageDomain.java", page, pageMethod, List.of(), List.of(), List.of());
      JdtSyntaxProtocol.Response convertSyntax =
          unit("Convert.java", convert, convertMethod, List.of(), List.of(), List.of());
      RoutingGateway gateway = new RoutingGateway(sources);
      gateway.route(
          "Controller.java",
          "setPageSize",
          List.of(sources.location("PageDomain.java", pageMethod, "PageDomain.setPageSize")),
          List.of());
      gateway.route(
          "Controller.java",
          "toInt",
          List.of(sources.location("Convert.java", convertMethod, "Convert.toInt")),
          List.of());
      int outerStart = controller.indexOf("page.setPageSize");
      int innerStart = controller.indexOf("Convert.toInt");
      int innerEnd = controller.indexOf("))", innerStart) + 2;
      gateway.hierarchy(
          "Controller.java",
          run.navigationRange().startOffsetUtf16(),
          new JdtNavigationResolver.OutgoingCall(
              sources.location("PageDomain.java", pageMethod, "PageDomain.setPageSize"),
              List.of(
                  sources.textRange(
                      "Controller.java",
                      new JdtSyntaxProtocol.SourceRange(
                          outerStart, innerEnd - outerStart, 1, 1)))));
      return new Fixture(
          sources,
          Map.of(
              "Controller.java", controllerSyntax,
              "PageDomain.java", pageSyntax,
              "Convert.java", convertSyntax),
          gateway);
    }

    static Fixture external() {
      String controller = "class Controller { void run(String id) { String.valueOf(id); } }\n";
      InMemorySources sources = new InMemorySources(Map.of("Controller.java", controller));
      JdtSyntaxProtocol.Declaration run =
          method(controller, "controller-run", "Controller", "run", "void run", true);
      JdtSyntaxProtocol.Response controllerSyntax =
          unit(
              "Controller.java",
              controller,
              run,
              List.of(call(controller, "controller-run", "value-of", "String.valueOf(id)", 0)),
              List.of(),
              List.of());
      return new Fixture(
          sources, Map.of("Controller.java", controllerSyntax), new RoutingGateway(sources));
    }

    static Fixture cycle() {
      String first = "class First { void first() { second.second(); } }\n";
      String second = "class Second { void second() { first.first(); } }\n";
      InMemorySources sources =
          new InMemorySources(Map.of("First.java", first, "Second.java", second));
      JdtSyntaxProtocol.Declaration firstMethod =
          method(first, "first", "First", "first", "void first", true);
      JdtSyntaxProtocol.Declaration secondMethod =
          method(second, "second", "Second", "second", "void second", true);
      Map<String, JdtSyntaxProtocol.Response> syntax =
          Map.of(
              "First.java",
              unit(
                  "First.java",
                  first,
                  firstMethod,
                  List.of(call(first, "first", "to-second", "second.second()", 0)),
                  List.of(),
                  List.of()),
              "Second.java",
              unit(
                  "Second.java",
                  second,
                  secondMethod,
                  List.of(call(second, "second", "to-first", "first.first()", 0)),
                  List.of(),
                  List.of()));
      RoutingGateway gateway = new RoutingGateway(sources);
      gateway.route(
          "First.java",
          "second",
          List.of(sources.location("Second.java", secondMethod, "Second.second")),
          List.of());
      gateway.route(
          "Second.java",
          "first",
          List.of(sources.location("First.java", firstMethod, "First.first")),
          List.of());
      return new Fixture(sources, syntax, gateway);
    }

    EntryCodeCollector collector(CollectionBudget budget) {
      return collector(budget, (path, level, source) -> syntax.get(path));
    }

    EntryCodeCollector collector(
        CollectionBudget budget, EntryCodeCollector.SyntaxAccess syntaxAccess) {
      JdtNavigationResolver resolver = new JdtNavigationResolver(gateway, sources);
      return new EntryCodeCollector(
          "snapshot:test",
          "17",
          syntax.keySet().stream().sorted().toList(),
          sources,
          syntaxAccess,
          resolver,
          budget);
    }
  }

  private static JdtSyntaxProtocol.Response unit(
      String path,
      String source,
      JdtSyntaxProtocol.Declaration declaration,
      List<JdtSyntaxProtocol.CallSiteView> calls,
      List<JdtSyntaxProtocol.ControlView> controls,
      List<JdtSyntaxProtocol.ExitView> exits) {
    return new JdtSyntaxProtocol.Response(
        JdtSyntaxProtocol.VERSION,
        "test",
        path,
        "0".repeat(64),
        null,
        List.of(),
        List.of(declaration),
        List.of(),
        calls,
        controls,
        exits,
        List.of());
  }

  private static JdtSyntaxProtocol.Declaration method(
      String source,
      String localId,
      String type,
      String name,
      String declarationStart,
      boolean body) {
    int start = source.indexOf(declarationStart);
    int end = body ? source.lastIndexOf('}') : source.indexOf(';', start) + 1;
    int nameStart = source.indexOf(name, start);
    return new JdtSyntaxProtocol.Declaration(
        localId,
        "METHOD",
        name,
        type,
        null,
        source.substring(start, end),
        List.of(),
        List.of(),
        List.of(
            new JdtSyntaxProtocol.ParameterView(
                0,
                "id",
                "String",
                false,
                List.of(),
                range(source, source.indexOf("String id", start), 9))),
        "Result",
        range(source, nameStart, name.length()),
        range(source, start, end - start),
        source.substring(start, end),
        body);
  }

  private static JdtSyntaxProtocol.CallSiteView call(
      String source, String owner, String id, String expression, int occurrence) {
    int start = nth(source, expression, occurrence);
    int nameStart =
        source.indexOf(
            expression.substring(expression.indexOf('.') + 1, expression.indexOf('(')), start);
    String arguments =
        expression.substring(expression.indexOf('(') + 1, expression.lastIndexOf(')'));
    return new JdtSyntaxProtocol.CallSiteView(
        id,
        owner,
        "METHOD",
        range(source, start, expression.length()),
        range(source, nameStart, expression.indexOf('(') - expression.indexOf('.') - 1),
        expression,
        expression.substring(0, expression.indexOf('.')),
        arguments.isBlank() ? List.of() : List.of(arguments),
        false);
  }

  private static int nth(String source, String text, int occurrence) {
    int result = -1;
    for (int index = 0; index <= occurrence; index++) {
      result = source.indexOf(text, result + 1);
    }
    return result;
  }

  private static JdtSyntaxProtocol.SourceRange range(String source, int start, int length) {
    return new JdtSyntaxProtocol.SourceRange(start, length, 1, 1);
  }

  private static final class InMemorySources implements JdtNavigationResolver.SourceAccess {
    private final Map<String, String> values;

    private InMemorySources(Map<String, String> values) {
      this.values = values;
    }

    @Override
    public JdtNavigationResolver.SourceDocument open(String uri) {
      String prefix = "memory:///";
      if (!uri.startsWith(prefix)) {
        return null;
      }
      String path = uri.substring(prefix.length());
      String text = values.get(path);
      return text == null ? null : new JdtNavigationResolver.SourceDocument(path, text);
    }

    JdtNavigationResolver.Location location(
        String path, JdtSyntaxProtocol.Declaration declaration, String display) {
      return new JdtNavigationResolver.Location(
          uri(path),
          textRange(path, declaration.sourceRange()),
          textRange(path, declaration.navigationRange()),
          display);
    }

    JdtNavigationResolver.TextRange textRange(
        String path, JdtSyntaxProtocol.SourceRange sourceRange) {
      return new JdtNavigationResolver.TextRange(
          position(values.get(path), sourceRange.startOffsetUtf16()),
          position(values.get(path), sourceRange.endOffsetUtf16()));
    }

    JdtNavigationResolver.Position position(String source, int offset) {
      return new JdtNavigationResolver.Position(0, offset);
    }
  }

  private static final class RoutingGateway implements JdtNavigationResolver.Gateway {
    private final InMemorySources sources;
    private final Map<String, List<JdtNavigationResolver.Location>> definitions =
        new LinkedHashMap<>();
    private final Map<String, List<JdtNavigationResolver.Location>> implementations =
        new LinkedHashMap<>();
    private final Map<String, List<JdtNavigationResolver.OutgoingCall>> outgoing =
        new LinkedHashMap<>();
    private CodeEngineException definitionFailure;

    private RoutingGateway(InMemorySources sources) {
      this.sources = sources;
    }

    void route(
        String path,
        String callName,
        List<JdtNavigationResolver.Location> declarationTargets,
        List<JdtNavigationResolver.Location> implementationTargets) {
      String text = sources.values.get(path);
      int start = 0;
      while ((start = text.indexOf(callName, start)) >= 0) {
        String key = sources.uri(path) + ':' + start;
        definitions.put(key, declarationTargets);
        implementations.put(key, implementationTargets);
        start += callName.length();
      }
    }

    void hierarchy(
        String path, int ownerNavigationOffset, JdtNavigationResolver.OutgoingCall call) {
      outgoing.put(sources.uri(path) + ':' + ownerNavigationOffset, List.of(call));
    }

    @Override
    public List<JdtNavigationResolver.OutgoingCall> outgoingCalls(
        String uri, JdtNavigationResolver.Position position) {
      return outgoing.getOrDefault(uri + ':' + position.character(), List.of());
    }

    @Override
    public List<JdtNavigationResolver.Location> definitions(
        String uri, JdtNavigationResolver.Position position) {
      if (definitionFailure != null) {
        throw definitionFailure;
      }
      return definitions.getOrDefault(uri + ':' + position.character(), List.of());
    }

    @Override
    public List<JdtNavigationResolver.Location> implementations(
        String uri, JdtNavigationResolver.Position position) {
      return implementations.getOrDefault(uri + ':' + position.character(), List.of());
    }
  }
}
