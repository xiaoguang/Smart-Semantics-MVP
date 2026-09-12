package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdtNavigationResolverTest {

  @Test
  void mergesNavigationEvidenceAndKeepsEveryImplementationCandidate() {
    String caller = "class Controller { void register() { service.save(user); } }\n";
    String service = "interface Service { void save(User user); }\n";
    String first = "class FirstService implements Service { public void save(User user) {} }\n";
    String second = "class SecondService implements Service { public void save(User user) {} }\n";
    InMemorySources sources =
        new InMemorySources(
            Map.of(
                "Controller.java", caller,
                "Service.java", service,
                "FirstService.java", first,
                "SecondService.java", second));
    JdtNavigationResolver.Location declaration =
        sources.location("Service.java", service.indexOf("void save"), "Service.save");
    JdtNavigationResolver.Location selectionOnlyDefinition =
        new JdtNavigationResolver.Location(
            declaration.uri(),
            declaration.selectionRange(),
            declaration.selectionRange(),
            declaration.displayName());
    JdtNavigationResolver.Location implementation1 =
        sources.location("FirstService.java", first.indexOf("void save"), "FirstService.save");
    JdtNavigationResolver.Location implementation2 =
        sources.location("SecondService.java", second.indexOf("void save"), "SecondService.save");
    FakeGateway gateway =
        new FakeGateway(
            List.of(
                new JdtNavigationResolver.OutgoingCall(
                    declaration,
                    List.of(sources.range("Controller.java", caller.indexOf("save"), 4)))),
            List.of(selectionOnlyDefinition),
            List.of(implementation2, implementation1));
    JdtSyntaxProtocol.CallSiteView call = call(caller, "call:one", caller.indexOf("service.save"));
    JdtSyntaxProtocol.Declaration owner = method(caller, "register");

    JdtNavigationResolver.ResolvedCall resolved =
        new JdtNavigationResolver(gateway, sources)
            .resolve("Controller.java", caller, owner, List.of(call))
            .get(0);

    assertThat(resolved.callLocalId()).isEqualTo("call:one");
    assertThat(resolved.status()).isEqualTo("CANDIDATES");
    assertThat(resolved.candidates())
        .extracting(JdtNavigationResolver.Candidate::sourcePath)
        .containsExactly("FirstService.java", "SecondService.java", "Service.java");
    JdtNavigationResolver.Candidate interfaceCandidate =
        resolved.candidates().stream()
            .filter(candidate -> candidate.sourcePath().equals("Service.java"))
            .findFirst()
            .orElseThrow();
    assertThat(interfaceCandidate.roles()).containsExactly("DECLARATION");
    assertThat(interfaceCandidate.navigationKinds())
        .containsExactly("CALL_HIERARCHY", "DEFINITION");
    assertThat(resolved.candidates().stream().filter(c -> c.roles().contains("IMPLEMENTATION")))
        .hasSize(2);
    assertThat(gateway.implementationQueries).isEqualTo(1);
  }

  @Test
  void retainsDuplicateCallOccurrencesAndReportsOutsideAndConflictingLocations() {
    String caller =
        "class Controller { void register() { service.save(one); service.save(two); } }\n";
    String service = "interface Service { void save(User user); }\n";
    InMemorySources sources =
        new InMemorySources(Map.of("Controller.java", caller, "Service.java", service));
    int target = service.indexOf("void save");
    JdtNavigationResolver.Location one = sources.location("Service.java", target, "Service.save");
    JdtNavigationResolver.Location conflicting =
        new JdtNavigationResolver.Location(
            one.uri(),
            sources.range("Service.java", 0, service.length() - 1),
            one.selectionRange(),
            one.displayName());
    JdtNavigationResolver.Location outside =
        new JdtNavigationResolver.Location(
            "file:///outside/Other.java", one.targetRange(), one.selectionRange(), "Other.save");
    FakeGateway gateway = new FakeGateway(List.of(), List.of(one, conflicting, outside), List.of());
    int first = caller.indexOf("service.save");
    int second = caller.indexOf("service.save", first + 1);

    List<JdtNavigationResolver.ResolvedCall> resolved =
        new JdtNavigationResolver(gateway, sources)
            .resolve(
                "Controller.java",
                caller,
                method(caller, "register"),
                List.of(call(caller, "call:one", first), call(caller, "call:two", second)));

    assertThat(resolved)
        .extracting(JdtNavigationResolver.ResolvedCall::callLocalId)
        .containsExactly("call:one", "call:two");
    assertThat(resolved)
        .allSatisfy(
            call -> {
              assertThat(call.status()).isEqualTo("NAVIGATION_CONFLICT");
              assertThat(call.candidates()).hasSize(2);
              assertThat(call.diagnostics())
                  .anyMatch(value -> value.startsWith("OUTSIDE_SNAPSHOT:"));
            });
  }

  private static JdtSyntaxProtocol.Declaration method(String source, String name) {
    int nameOffset = source.indexOf(name);
    int start = source.indexOf("void " + name);
    return new JdtSyntaxProtocol.Declaration(
        "decl:method",
        "METHOD",
        name,
        "Controller",
        null,
        source.substring(start, source.lastIndexOf('}') + 1),
        List.of(),
        List.of(),
        List.of(),
        "void",
        range(source, nameOffset, name.length()),
        range(source, start, source.lastIndexOf('}') + 1 - start),
        source.substring(start, source.lastIndexOf('}') + 1),
        true);
  }

  private static JdtSyntaxProtocol.CallSiteView call(String source, String id, int start) {
    int navigation = source.indexOf("save", start);
    int end = source.indexOf(')', start) + 1;
    return new JdtSyntaxProtocol.CallSiteView(
        id,
        "decl:method",
        "METHOD",
        range(source, start, end - start),
        range(source, navigation, 4),
        source.substring(start, end),
        "service",
        List.of(source.substring(source.indexOf('(', start) + 1, end - 1)),
        false);
  }

  private static JdtSyntaxProtocol.SourceRange range(String source, int start, int length) {
    int startLine = 1 + Math.toIntExact(source.substring(0, start).lines().count() - 1);
    int end = start + length;
    int endLine = 1 + Math.toIntExact(source.substring(0, end).lines().count() - 1);
    return new JdtSyntaxProtocol.SourceRange(start, length, startLine, endLine);
  }

  private static final class FakeGateway implements JdtNavigationResolver.Gateway {
    private final List<JdtNavigationResolver.OutgoingCall> outgoing;
    private final List<JdtNavigationResolver.Location> definitions;
    private final List<JdtNavigationResolver.Location> implementations;
    private int implementationQueries;

    private FakeGateway(
        List<JdtNavigationResolver.OutgoingCall> outgoing,
        List<JdtNavigationResolver.Location> definitions,
        List<JdtNavigationResolver.Location> implementations) {
      this.outgoing = outgoing;
      this.definitions = definitions;
      this.implementations = implementations;
    }

    @Override
    public List<JdtNavigationResolver.OutgoingCall> outgoingCalls(
        String uri, JdtNavigationResolver.Position position) {
      return outgoing;
    }

    @Override
    public List<JdtNavigationResolver.Location> definitions(
        String uri, JdtNavigationResolver.Position position) {
      return definitions;
    }

    @Override
    public List<JdtNavigationResolver.Location> implementations(
        String uri, JdtNavigationResolver.Position position) {
      implementationQueries++;
      return implementations;
    }
  }

  private static final class InMemorySources implements JdtNavigationResolver.SourceAccess {
    private final Map<String, String> sources;

    private InMemorySources(Map<String, String> sources) {
      this.sources = sources;
    }

    @Override
    public JdtNavigationResolver.SourceDocument open(String uri) {
      String prefix = "memory:///";
      if (!uri.startsWith(prefix)) {
        return null;
      }
      String path = uri.substring(prefix.length());
      String source = sources.get(path);
      return source == null ? null : new JdtNavigationResolver.SourceDocument(path, source);
    }

    JdtNavigationResolver.Location location(String path, int start, String displayName) {
      String source = sources.get(path);
      return new JdtNavigationResolver.Location(
          "memory:///" + path,
          range(path, 0, source.length()),
          range(path, start, displayName.substring(displayName.indexOf('.') + 1).length()),
          displayName);
    }

    JdtNavigationResolver.TextRange range(String path, int start, int length) {
      String source = sources.get(path);
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
  }
}
