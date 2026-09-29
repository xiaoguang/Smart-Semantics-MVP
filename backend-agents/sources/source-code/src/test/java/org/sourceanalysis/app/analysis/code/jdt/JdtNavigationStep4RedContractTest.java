package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sourceanalysis.app.analysis.code.CodeEngineException;

/** RED contracts for the bounded Step04 JDT navigation corrections. */
class JdtNavigationStep4RedContractTest {

  @Test
  void syntaxV4ProjectsNonRecoveredBindingObservationOnCallsAndDeclarations() {
    assertThat(JdtSyntaxProtocol.VERSION).isEqualTo("jdt-syntax-v4");

    RecordComponent callBinding =
        Arrays.stream(JdtSyntaxProtocol.CallSiteView.class.getRecordComponents())
            .filter(component -> "binding".equals(component.getName()))
            .findFirst()
            .orElse(null);
    RecordComponent declarationBinding =
        Arrays.stream(JdtSyntaxProtocol.Declaration.class.getRecordComponents())
            .filter(component -> "binding".equals(component.getName()))
            .findFirst()
            .orElse(null);
    assertThat(callBinding)
        .as("CallSiteView must carry the optional JDT binding projection")
        .isNotNull();
    assertThat(declarationBinding)
        .as("Declaration must carry the optional JDT binding projection")
        .isNotNull();
    assertThat(callBinding.getType()).isEqualTo(declarationBinding.getType());
    assertThat(callBinding.getType().isRecord()).isTrue();
    assertThat(Arrays.stream(callBinding.getType().getRecordComponents()))
        .extracting(RecordComponent::getName)
        .contains("state", "declarationKey", "declaringTypeKey", "typeOrigin", "displayIdentity");

    RecordComponent state =
        Arrays.stream(callBinding.getType().getRecordComponents())
            .filter(component -> "state".equals(component.getName()))
            .findFirst()
            .orElseThrow();
    assertThat(state.getType().isEnum()).isTrue();
    assertThat(Arrays.stream(state.getType().getEnumConstants()))
        .extracting(Object::toString)
        .contains("RESOLVED", "ABSENT", "RECOVERED", "UNSUPPORTED_CALL_KIND");
  }

  @Test
  void syntaxV4BindingObservationIsAPathFreeValueProjection() {
    Class<?> bindingType = bindingRecordType();
    assertThat(bindingType.getRecordComponents())
        .extracting(RecordComponent::getName)
        .contains("state", "declarationKey", "declaringTypeKey", "typeOrigin", "displayIdentity");
    assertThat(bindingType.getRecordComponents())
        .extracting(RecordComponent::getType)
        .noneMatch(
            type ->
                type.getName().startsWith("org.eclipse.jdt.core.dom.")
                    || type.getName().startsWith("org.eclipse.lsp4j."));
    assertThat(bindingType.getDeclaredConstructors())
        .anySatisfy(constructor -> assertThat(constructor.getParameterCount()).isEqualTo(5));
  }

  private static Class<?> bindingRecordType() {
    RecordComponent callBinding =
        Arrays.stream(JdtSyntaxProtocol.CallSiteView.class.getRecordComponents())
            .filter(component -> "binding".equals(component.getName()))
            .findFirst()
            .orElse(null);
    RecordComponent declarationBinding =
        Arrays.stream(JdtSyntaxProtocol.Declaration.class.getRecordComponents())
            .filter(component -> "binding".equals(component.getName()))
            .findFirst()
            .orElse(null);
    assertThat(callBinding).isNotNull();
    assertThat(declarationBinding).isNotNull();
    assertThat(callBinding.getType()).isEqualTo(declarationBinding.getType());
    assertThat(callBinding.getType().isRecord()).isTrue();
    return callBinding.getType();
  }

  @Test
  void usesTheExactCallSiteWhenAnOuterHierarchyRangeAlsoContainsAnInnerCall() {
    String caller = "class Controller { void run() { page.setPageSize(Convert.toInt(value)); } }\n";
    String pageDomain = "class PageDomain { void setPageSize(int value) {} }\n";
    String convert = "class Convert { static int toInt(String value) { return 1; } }\n";
    InMemorySources sources =
        new InMemorySources(
            Map.of(
                "Controller.java", caller,
                "PageDomain.java", pageDomain,
                "Convert.java", convert));
    int outerStart = caller.indexOf("page.setPageSize");
    int outerNavigation = caller.indexOf("setPageSize", outerStart);
    int innerStart = caller.indexOf("Convert.toInt");
    int innerNavigation = caller.indexOf("toInt", innerStart);
    JdtSyntaxProtocol.CallSiteView outer =
        call(caller, "call:outer", outerStart, outerNavigation, "page.setPageSize");
    JdtSyntaxProtocol.CallSiteView inner =
        call(caller, "call:inner", innerStart, innerNavigation, "Convert.toInt");
    JdtNavigationResolver.Location outerTarget =
        sources.location(
            "PageDomain.java", pageDomain.indexOf("setPageSize"), "PageDomain.setPageSize");
    JdtNavigationResolver.Location innerTarget =
        sources.location("Convert.java", convert.indexOf("toInt"), "Convert.toInt");
    RoutingGateway gateway = new RoutingGateway();
    gateway.outgoing =
        List.of(
            new JdtNavigationResolver.OutgoingCall(
                outerTarget,
                List.of(
                    sources.range(
                        caller, outerStart, callExpressionEnd(caller, outerStart) - outerStart))),
            new JdtNavigationResolver.OutgoingCall(
                innerTarget, List.of(sources.range(caller, innerStart, "Convert.toInt".length()))));
    gateway.definition(outerNavigation, outerTarget);
    gateway.definition(innerNavigation, innerTarget);

    List<JdtNavigationResolver.ResolvedCall> resolved =
        new JdtNavigationResolver(gateway, sources)
            .resolve("Controller.java", caller, method(caller, "run"), List.of(outer, inner));

    JdtNavigationResolver.ResolvedCall outerResolution = resolved.get(0);
    JdtNavigationResolver.ResolvedCall innerResolution = resolved.get(1);
    assertThat(outerResolution.candidates())
        .extracting(JdtNavigationResolver.Candidate::displayName)
        .containsExactly("PageDomain.setPageSize");
    assertThat(innerResolution.candidates())
        .extracting(JdtNavigationResolver.Candidate::displayName)
        .containsExactly("Convert.toInt");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdt://contents/java.base/java/lang/String.class",
        "jdt://contents/maven/org.example/library/Widget.class"
      })
  void classifiesAConfirmedJdkOrThirdPartyBinaryAsExternal(String binaryUri) {
    String caller = "class Controller { void run() { String.valueOf(value); } }\n";
    InMemorySources sources = new InMemorySources(Map.of("Controller.java", caller));
    int callStart = caller.indexOf("String.valueOf");
    int navigation = caller.indexOf("valueOf", callStart);
    JdtNavigationResolver.Location binary =
        new JdtNavigationResolver.Location(
            binaryUri,
            new JdtNavigationResolver.TextRange(
                new JdtNavigationResolver.Position(0, 0),
                new JdtNavigationResolver.Position(0, 12)),
            new JdtNavigationResolver.TextRange(
                new JdtNavigationResolver.Position(0, 0), new JdtNavigationResolver.Position(0, 7)),
            "java.lang.String.valueOf");
    RoutingGateway gateway = new RoutingGateway();
    gateway.definition(navigation, binary);

    JdtNavigationResolver.ResolvedCall resolved =
        new JdtNavigationResolver(gateway, sources)
            .resolve(
                "Controller.java",
                caller,
                method(caller, "run"),
                List.of(call(caller, "call:binary", callStart, navigation, "String.valueOf")))
            .get(0);

    assertThat(resolved.status()).isEqualTo("EXTERNAL");
    assertThat(resolved.diagnostics()).noneMatch(value -> value.startsWith("OUTSIDE_SNAPSHOT:"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"timeout", "rpc-exception"})
  void classifiesRequiredRpcFailureAsQueryFailedAndRetainsLocalObservation(String failureKind) {
    String caller = "class Controller { void run() { service.save(value); } }\n";
    String service = "interface Service { void save(String value); }\n";
    InMemorySources sources =
        new InMemorySources(Map.of("Controller.java", caller, "Service.java", service));
    int callStart = caller.indexOf("service.save");
    int navigation = caller.indexOf("save", callStart);
    JdtNavigationResolver.Location localTarget =
        sources.location("Service.java", service.indexOf("save"), "Service.save");
    RoutingGateway gateway = new RoutingGateway();
    gateway.outgoing =
        List.of(
            new JdtNavigationResolver.OutgoingCall(
                localTarget,
                List.of(sources.range(caller, callStart, "service.save(value)".length()))));
    Throwable cause =
        failureKind.equals("timeout")
            ? new TimeoutException("simulated JDT request timeout")
            : new IllegalStateException("simulated JDT request exception");
    gateway.definitionFailure =
        new CodeEngineException(
            CodeEngineException.JDT_QUERY_FAILED, "simulated " + failureKind, cause);

    assertThatCode(
            () ->
                gateway.lastResolution =
                    new JdtNavigationResolver(gateway, sources)
                        .resolve(
                            "Controller.java",
                            caller,
                            method(caller, "run"),
                            List.of(
                                call(caller, "call:failed", callStart, navigation, "service.save")))
                        .get(0))
        .doesNotThrowAnyException();

    assertThat(gateway.lastResolution.status()).isEqualTo("QUERY_FAILED");
    assertThat(gateway.lastResolution.candidates())
        .extracting(JdtNavigationResolver.Candidate::displayName)
        .contains("Service.save");
  }

  @Test
  void retainsEachLegalOverloadAndBothInterfaceImplementations() {
    String caller = "class Controller { void run() { service.save(1); service.save(\"x\"); } }\n";
    String service = "interface Service { void save(int value); void save(String value); }\n";
    String first =
        "class FirstService implements Service { public void save(int value) {} public void save(String value) {} }\n";
    String second =
        "class SecondService implements Service { public void save(int value) {} public void save(String value) {} }\n";
    InMemorySources sources =
        new InMemorySources(
            Map.of(
                "Controller.java", caller,
                "Service.java", service,
                "FirstService.java", first,
                "SecondService.java", second));
    int firstCall = caller.indexOf("service.save");
    int secondCall = caller.indexOf("service.save", firstCall + 1);
    int firstNavigation = caller.indexOf("save", firstCall);
    int secondNavigation = caller.indexOf("save", secondCall);
    JdtNavigationResolver.Location intDeclaration =
        sources.location("Service.java", service.indexOf("save(int"), "Service.save(int)");
    JdtNavigationResolver.Location stringDeclaration =
        sources.location("Service.java", service.indexOf("save(String"), "Service.save(String)");
    JdtNavigationResolver.Location firstInt =
        sources.location("FirstService.java", first.indexOf("save(int"), "FirstService.save(int)");
    JdtNavigationResolver.Location secondInt =
        sources.location(
            "SecondService.java", second.indexOf("save(int"), "SecondService.save(int)");
    JdtNavigationResolver.Location firstString =
        sources.location(
            "FirstService.java", first.indexOf("save(String"), "FirstService.save(String)");
    JdtNavigationResolver.Location secondString =
        sources.location(
            "SecondService.java", second.indexOf("save(String"), "SecondService.save(String)");
    RoutingGateway gateway = new RoutingGateway();
    gateway.definition(firstNavigation, intDeclaration);
    gateway.definition(secondNavigation, stringDeclaration);
    gateway.implementation(firstNavigation, firstInt, secondInt);
    gateway.implementation(secondNavigation, firstString, secondString);

    List<JdtNavigationResolver.ResolvedCall> resolved =
        new JdtNavigationResolver(gateway, sources)
            .resolve(
                "Controller.java",
                caller,
                method(caller, "run"),
                List.of(
                    call(caller, "call:int", firstCall, firstNavigation, "service.save"),
                    call(caller, "call:string", secondCall, secondNavigation, "service.save")));

    assertThat(resolved)
        .extracting(JdtNavigationResolver.ResolvedCall::status)
        .containsExactly("CANDIDATES", "CANDIDATES");
    assertThat(resolved.get(0).candidates())
        .extracting(JdtNavigationResolver.Candidate::displayName)
        .containsExactly("FirstService.save(int)", "SecondService.save(int)", "Service.save(int)");
    assertThat(resolved.get(1).candidates())
        .extracting(JdtNavigationResolver.Candidate::displayName)
        .containsExactly(
            "FirstService.save(String)", "SecondService.save(String)", "Service.save(String)");
  }

  private static JdtSyntaxProtocol.Declaration method(String source, String name) {
    int nameOffset = source.indexOf(name);
    int start = source.indexOf("void " + name);
    int end = source.lastIndexOf('}') + 1;
    return new JdtSyntaxProtocol.Declaration(
        "decl:" + name,
        "METHOD",
        name,
        "Controller",
        null,
        source.substring(start, end),
        List.of(),
        List.of(),
        List.of(),
        "void",
        range(nameOffset, name.length()),
        range(start, end - start),
        source.substring(start, end),
        true);
  }

  private static JdtSyntaxProtocol.CallSiteView call(
      String source, String id, int start, int navigation, String expressionPrefix) {
    int expressionEnd = callExpressionEnd(source, start);
    return new JdtSyntaxProtocol.CallSiteView(
        id,
        "decl:run",
        "METHOD",
        range(start, expressionEnd - start),
        range(navigation, expressionPrefix.substring(expressionPrefix.indexOf('.') + 1).length()),
        source.substring(start, expressionEnd),
        expressionPrefix.substring(0, expressionPrefix.indexOf('.')),
        List.of(),
        false);
  }

  private static int callExpressionEnd(String source, int start) {
    int opening = source.indexOf('(', start);
    if (opening < 0) {
      throw new IllegalArgumentException("call fixture has no argument list");
    }
    int depth = 0;
    for (int index = opening; index < source.length(); index++) {
      char value = source.charAt(index);
      if (value == '(') {
        depth++;
      } else if (value == ')' && --depth == 0) {
        return index + 1;
      }
    }
    throw new IllegalArgumentException("call fixture has unbalanced parentheses");
  }

  private static JdtSyntaxProtocol.SourceRange range(int start, int length) {
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
      String source = values.get(path);
      return source == null ? null : new JdtNavigationResolver.SourceDocument(path, source);
    }

    JdtNavigationResolver.Location location(String path, int navigationStart, String displayName) {
      String source = values.get(path);
      return new JdtNavigationResolver.Location(
          uri(path),
          textRange(source, 0, source.length()),
          textRange(
              source,
              navigationStart,
              displayName.substring(displayName.indexOf('.') + 1).length()),
          displayName);
    }

    JdtNavigationResolver.TextRange range(String source, int start, int length) {
      return textRange(source, start, length);
    }

    private JdtNavigationResolver.TextRange textRange(String source, int start, int length) {
      return new JdtNavigationResolver.TextRange(
          position(source, start), position(source, start + length));
    }

    private JdtNavigationResolver.Position position(String source, int offset) {
      int line = 0;
      int lineStart = 0;
      for (int index = 0; index < offset; index++) {
        if (source.charAt(index) == '\n') {
          line++;
          lineStart = index + 1;
        }
      }
      return new JdtNavigationResolver.Position(line, offset - lineStart);
    }

    @Override
    public String uri(String path) {
      return "memory:///" + path;
    }
  }

  private static final class RoutingGateway implements JdtNavigationResolver.Gateway {
    private final Map<Integer, List<JdtNavigationResolver.Location>> definitions =
        new LinkedHashMap<>();
    private final Map<Integer, List<JdtNavigationResolver.Location>> implementations =
        new LinkedHashMap<>();
    private List<JdtNavigationResolver.OutgoingCall> outgoing = List.of();
    private CodeEngineException definitionFailure;
    private JdtNavigationResolver.ResolvedCall lastResolution;

    private RoutingGateway() {}

    void definition(int navigationOffset, JdtNavigationResolver.Location location) {
      definitions.put(navigationOffset, List.of(location));
    }

    void implementation(int navigationOffset, JdtNavigationResolver.Location... locations) {
      implementations.put(navigationOffset, List.of(locations));
    }

    @Override
    public List<JdtNavigationResolver.OutgoingCall> outgoingCalls(
        String uri, JdtNavigationResolver.Position position) {
      return outgoing;
    }

    @Override
    public List<JdtNavigationResolver.Location> definitions(
        String uri, JdtNavigationResolver.Position position) {
      if (definitionFailure != null) {
        throw definitionFailure;
      }
      return definitions.getOrDefault(position.character(), List.of());
    }

    @Override
    public List<JdtNavigationResolver.Location> implementations(
        String uri, JdtNavigationResolver.Position position) {
      return implementations.getOrDefault(position.character(), List.of());
    }
  }
}
