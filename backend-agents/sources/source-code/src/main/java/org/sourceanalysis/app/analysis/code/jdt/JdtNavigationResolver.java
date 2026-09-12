package org.sourceanalysis.app.analysis.code.jdt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves JDT LS navigation responses without guessing Java names or dropping candidates. */
final class JdtNavigationResolver {

  private static final List<String> ROLE_ORDER = List.of("DECLARATION", "IMPLEMENTATION");
  private static final List<String> NAVIGATION_ORDER =
      List.of("CALL_HIERARCHY", "DEFINITION", "IMPLEMENTATION");

  private final Gateway gateway;
  private final SourceAccess sources;

  JdtNavigationResolver(Gateway gateway, SourceAccess sources) {
    this.gateway = Objects.requireNonNull(gateway, "navigation gateway");
    this.sources = Objects.requireNonNull(sources, "navigation sources");
  }

  List<ResolvedCall> resolve(
      String callerPath,
      String callerSource,
      JdtSyntaxProtocol.Declaration owner,
      List<JdtSyntaxProtocol.CallSiteView> calls) {
    requireText(callerPath, "caller source path");
    Objects.requireNonNull(callerSource, "caller source");
    Objects.requireNonNull(owner, "owner declaration");
    List<JdtSyntaxProtocol.CallSiteView> orderedCalls =
        List.copyOf(Objects.requireNonNull(calls, "call sites"));
    String callerUri = sources.uri(callerPath);
    sources.activate(callerUri);
    List<OutgoingCall> outgoing =
        owner.navigationRange() == null
            ? List.of()
            : safeOutgoing(
                callerUri, position(callerSource, owner.navigationRange().startOffsetUtf16()));

    List<ResolvedCall> result = new ArrayList<>();
    for (JdtSyntaxProtocol.CallSiteView call : orderedCalls) {
      if (call.navigationRange() == null) {
        result.add(
            new ResolvedCall(
                call.localId(),
                List.of(),
                "UNRESOLVED",
                List.of("MISSING_AST_NAVIGATION_RANGE:" + call.localId())));
        continue;
      }
      Position query = position(callerSource, call.navigationRange().startOffsetUtf16());
      List<SourcedLocation> locations = new ArrayList<>();
      for (OutgoingCall edge : outgoing) {
        if (edge.fromRanges().stream().anyMatch(range -> contains(range, query))) {
          locations.add(new SourcedLocation(edge.target(), "DECLARATION", "CALL_HIERARCHY"));
        }
      }
      gateway
          .definitions(callerUri, query)
          .forEach(
              location ->
                  locations.add(new SourcedLocation(location, "DECLARATION", "DEFINITION")));
      gateway
          .implementations(callerUri, query)
          .forEach(
              location ->
                  locations.add(new SourcedLocation(location, "IMPLEMENTATION", "IMPLEMENTATION")));
      result.add(normalize(call.localId(), locations));
    }
    return List.copyOf(result);
  }

  List<Location> definitionsAt(
      String sourcePath, String sourceText, JdtSyntaxProtocol.SourceRange navigationRange) {
    requireText(sourcePath, "definition source path");
    Objects.requireNonNull(sourceText, "definition source");
    Objects.requireNonNull(navigationRange, "definition navigation range");
    String uri = sources.uri(sourcePath);
    sources.activate(uri);
    List<Location> locations =
        gateway.definitions(uri, position(sourceText, navigationRange.startOffsetUtf16()));
    return locations == null ? List.of() : List.copyOf(locations);
  }

  private ResolvedCall normalize(String callId, List<SourcedLocation> locations) {
    Map<String, MutableCandidate> byExactLocation = new LinkedHashMap<>();
    List<String> diagnostics = new ArrayList<>();
    for (SourcedLocation sourced : locations) {
      Location location = sourced.location();
      SourceDocument document = sources.open(location.uri());
      if (document == null) {
        diagnostics.add("OUTSIDE_SNAPSHOT:" + location.uri());
        continue;
      }
      JdtSyntaxProtocol.SourceRange target;
      JdtSyntaxProtocol.SourceRange selection;
      try {
        target = sourceRange(document.text(), location.targetRange());
        selection = sourceRange(document.text(), location.selectionRange());
      } catch (IllegalArgumentException invalidRange) {
        diagnostics.add("INVALID_TARGET_RANGE:" + location.uri());
        continue;
      }
      String exactKey =
          document.path()
              + '|'
              + target.startOffsetUtf16()
              + ':'
              + target.lengthUtf16()
              + '|'
              + selection.startOffsetUtf16()
              + ':'
              + selection.lengthUtf16();
      MutableCandidate candidate =
          byExactLocation.computeIfAbsent(
              exactKey,
              ignored ->
                  new MutableCandidate(
                      document.path(),
                      target,
                      selection,
                      location.displayName(),
                      new LinkedHashSet<>(),
                      new LinkedHashSet<>()));
      candidate.roles().add(sourced.role());
      candidate.navigationKinds().add(sourced.navigationKind());
    }

    List<Candidate> exactCandidates =
        byExactLocation.values().stream()
            .map(MutableCandidate::freeze)
            .sorted(
                Comparator.comparing(Candidate::sourcePath)
                    .thenComparing(candidate -> candidate.selectionRange().startOffsetUtf16())
                    .thenComparing(candidate -> candidate.targetRange().startOffsetUtf16())
                    .thenComparing(Candidate::displayName))
            .toList();
    List<Candidate> candidates = collapseSelectionOnlyLocations(exactCandidates);
    Map<String, Long> targetShapesPerSelection =
        candidates.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    candidate ->
                        candidate.sourcePath()
                            + '|'
                            + candidate.selectionRange().startOffsetUtf16()
                            + ':'
                            + candidate.selectionRange().lengthUtf16(),
                    LinkedHashMap::new,
                    java.util.stream.Collectors.mapping(
                        candidate ->
                            candidate.targetRange().startOffsetUtf16()
                                + ":"
                                + candidate.targetRange().lengthUtf16(),
                        java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toSet(), values -> (long) values.size()))));
    boolean conflict = targetShapesPerSelection.values().stream().anyMatch(count -> count > 1L);
    if (conflict) {
      diagnostics.add("NAVIGATION_CONFLICT:" + callId);
    }
    String status =
        conflict
            ? "NAVIGATION_CONFLICT"
            : candidates.isEmpty()
                ? "UNRESOLVED"
                : candidates.size() == 1 ? "LOCATED" : "CANDIDATES";
    return new ResolvedCall(callId, candidates, status, List.copyOf(diagnostics));
  }

  private static List<Candidate> collapseSelectionOnlyLocations(List<Candidate> candidates) {
    Map<String, List<Candidate>> bySelection =
        candidates.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    candidate ->
                        candidate.sourcePath()
                            + '|'
                            + candidate.selectionRange().startOffsetUtf16()
                            + ':'
                            + candidate.selectionRange().lengthUtf16(),
                    LinkedHashMap::new,
                    java.util.stream.Collectors.toList()));
    List<Candidate> result = new ArrayList<>();
    for (List<Candidate> group : bySelection.values()) {
      List<Candidate> enclosing =
          group.stream()
              .filter(candidate -> !sameRange(candidate.targetRange(), candidate.selectionRange()))
              .toList();
      if (group.size() > 1 && enclosing.size() == 1) {
        Candidate target = enclosing.get(0);
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        LinkedHashSet<String> navigationKinds = new LinkedHashSet<>();
        group.forEach(
            candidate -> {
              roles.addAll(candidate.roles());
              navigationKinds.addAll(candidate.navigationKinds());
            });
        result.add(
            new Candidate(
                target.sourcePath(),
                target.targetRange(),
                target.selectionRange(),
                target.displayName(),
                ordered(roles, ROLE_ORDER),
                ordered(navigationKinds, NAVIGATION_ORDER)));
      } else {
        result.addAll(group);
      }
    }
    return result.stream()
        .sorted(
            Comparator.comparing(Candidate::sourcePath)
                .thenComparing(candidate -> candidate.selectionRange().startOffsetUtf16())
                .thenComparing(candidate -> candidate.targetRange().startOffsetUtf16())
                .thenComparing(Candidate::displayName))
        .toList();
  }

  private static boolean sameRange(
      JdtSyntaxProtocol.SourceRange left, JdtSyntaxProtocol.SourceRange right) {
    return left.startOffsetUtf16() == right.startOffsetUtf16()
        && left.lengthUtf16() == right.lengthUtf16();
  }

  private List<OutgoingCall> safeOutgoing(String uri, Position position) {
    List<OutgoingCall> calls = gateway.outgoingCalls(uri, position);
    return calls == null ? List.of() : List.copyOf(calls);
  }

  private static Position position(String source, int offset) {
    if (offset < 0 || offset > source.length()) {
      throw new IllegalArgumentException("source navigation offset is outside the document");
    }
    int line = 0;
    int lineStart = 0;
    for (int index = 0; index < offset; index++) {
      char value = source.charAt(index);
      if (value == '\n') {
        line++;
        lineStart = index + 1;
      }
    }
    return new Position(line, offset - lineStart);
  }

  private static JdtSyntaxProtocol.SourceRange sourceRange(String source, TextRange range) {
    Objects.requireNonNull(range, "navigation range");
    int start = offset(source, range.start());
    int end = offset(source, range.end());
    if (end < start) {
      throw new IllegalArgumentException("navigation range is reversed");
    }
    return new JdtSyntaxProtocol.SourceRange(
        start, end - start, range.start().line() + 1, range.end().line() + 1);
  }

  private static int offset(String source, Position position) {
    Objects.requireNonNull(position, "navigation position");
    if (position.line() < 0 || position.character() < 0) {
      throw new IllegalArgumentException("navigation position is negative");
    }
    int line = 0;
    int lineStart = 0;
    while (line < position.line()) {
      int newline = source.indexOf('\n', lineStart);
      if (newline < 0) {
        throw new IllegalArgumentException("navigation line is outside the document");
      }
      line++;
      lineStart = newline + 1;
    }
    int result = lineStart + position.character();
    int lineEnd = source.indexOf('\n', lineStart);
    if (lineEnd < 0) {
      lineEnd = source.length();
    }
    if (result > lineEnd) {
      throw new IllegalArgumentException("navigation character is outside the line");
    }
    return result;
  }

  private static boolean contains(TextRange range, Position point) {
    return compare(range.start(), point) <= 0 && compare(point, range.end()) <= 0;
  }

  private static int compare(Position left, Position right) {
    int line = Integer.compare(left.line(), right.line());
    return line == 0 ? Integer.compare(left.character(), right.character()) : line;
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }

  interface Gateway {
    List<OutgoingCall> outgoingCalls(String uri, Position position);

    List<Location> definitions(String uri, Position position);

    List<Location> implementations(String uri, Position position);
  }

  interface SourceAccess {
    SourceDocument open(String uri);

    default void activate(String uri) {}

    default String uri(String sourcePath) {
      return "memory:///" + sourcePath;
    }
  }

  record SourceDocument(String path, String text) {
    SourceDocument {
      requireText(path, "source document path");
      Objects.requireNonNull(text, "source document text");
    }
  }

  record Position(int line, int character) {
    Position {
      if (line < 0 || character < 0) {
        throw new IllegalArgumentException("navigation position cannot be negative");
      }
    }
  }

  record TextRange(Position start, Position end) {
    TextRange {
      Objects.requireNonNull(start, "range start");
      Objects.requireNonNull(end, "range end");
      if (compare(start, end) > 0) {
        throw new IllegalArgumentException("navigation range must be ordered");
      }
    }
  }

  record Location(String uri, TextRange targetRange, TextRange selectionRange, String displayName) {
    Location {
      requireText(uri, "target URI");
      Objects.requireNonNull(targetRange, "target range");
      Objects.requireNonNull(selectionRange, "target selection range");
      requireText(displayName, "target display name");
    }
  }

  record OutgoingCall(Location target, List<TextRange> fromRanges) {
    OutgoingCall {
      Objects.requireNonNull(target, "outgoing target");
      fromRanges = List.copyOf(Objects.requireNonNull(fromRanges, "outgoing from ranges"));
    }
  }

  record Candidate(
      String sourcePath,
      JdtSyntaxProtocol.SourceRange targetRange,
      JdtSyntaxProtocol.SourceRange selectionRange,
      String displayName,
      List<String> roles,
      List<String> navigationKinds) {}

  record ResolvedCall(
      String callLocalId, List<Candidate> candidates, String status, List<String> diagnostics) {
    ResolvedCall {
      requireText(callLocalId, "call local ID");
      candidates = List.copyOf(Objects.requireNonNull(candidates, "resolved candidates"));
      diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "navigation diagnostics"));
    }
  }

  private record SourcedLocation(Location location, String role, String navigationKind) {}

  private record MutableCandidate(
      String sourcePath,
      JdtSyntaxProtocol.SourceRange targetRange,
      JdtSyntaxProtocol.SourceRange selectionRange,
      String displayName,
      LinkedHashSet<String> roles,
      LinkedHashSet<String> navigationKinds) {

    private Candidate freeze() {
      return new Candidate(
          sourcePath,
          targetRange,
          selectionRange,
          displayName,
          ordered(roles, ROLE_ORDER),
          ordered(navigationKinds, NAVIGATION_ORDER));
    }
  }

  private static List<String> ordered(LinkedHashSet<String> values, List<String> order) {
    return values.stream().sorted(Comparator.comparingInt(order::indexOf)).toList();
  }
}
