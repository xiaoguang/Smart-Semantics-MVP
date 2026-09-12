package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;

class EntryCodeCollectorTest {

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
      JdtNavigationResolver resolver = new JdtNavigationResolver(gateway, sources);
      return new EntryCodeCollector(
          "snapshot:test",
          "17",
          syntax.keySet().stream().sorted().toList(),
          sources,
          (path, level, source) -> syntax.get(path),
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

    @Override
    public List<JdtNavigationResolver.OutgoingCall> outgoingCalls(
        String uri, JdtNavigationResolver.Position position) {
      return List.of();
    }

    @Override
    public List<JdtNavigationResolver.Location> definitions(
        String uri, JdtNavigationResolver.Position position) {
      return definitions.getOrDefault(uri + ':' + position.character(), List.of());
    }

    @Override
    public List<JdtNavigationResolver.Location> implementations(
        String uri, JdtNavigationResolver.Position position) {
      return implementations.getOrDefault(uri + ':' + position.character(), List.of());
    }
  }
}
