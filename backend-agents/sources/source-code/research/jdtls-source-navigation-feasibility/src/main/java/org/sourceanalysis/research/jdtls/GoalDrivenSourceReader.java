package org.sourceanalysis.research.jdtls;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Turns one JDT-returned source position into the enclosing frozen Java method. */
final class GoalDrivenSourceReader {

  private static final ObjectMapper JSON = new ObjectMapper();

  private GoalDrivenSourceReader() {}

  static ObjectNode read(String source, String repositoryPath, int zeroBasedLine, int zeroBasedCharacter)
      throws IOException {
    CompilationUnit unit =
        new JavaParser()
            .parse(source)
            .getResult()
            .orElseThrow(() -> new IOException("Java syntax parse failed: " + repositoryPath));
    Position anchor = new Position(zeroBasedLine + 1, zeroBasedCharacter + 1);
    MethodDeclaration method =
        unit.findAll(MethodDeclaration.class).stream()
            .filter(candidate -> owns(candidate, anchor))
            .min(Comparator.comparingInt(GoalDrivenSourceReader::span))
            .orElseThrow(
                () ->
                    new IOException(
                        "JDT location is not inside a Java method: "
                            + repositoryPath
                            + ":"
                            + (zeroBasedLine + 1)
                            + ":"
                            + (zeroBasedCharacter + 1)));
    Range range = method.getRange().orElseThrow();

    ObjectNode result = JSON.createObjectNode();
    result.put("path", repositoryPath);
    result.put("name", method.getNameAsString());
    result.put("signature", method.getDeclarationAsString(false, false, false));
    ObjectNode sourceRange = result.putObject("range");
    sourceRange.put("startLine", range.begin.line);
    sourceRange.put("startColumn", range.begin.column);
    sourceRange.put("endLine", range.end.line);
    sourceRange.put("endColumn", range.end.column);
    Range nameRange = method.getName().getRange().orElseThrow();
    ObjectNode methodNameRange = result.putObject("nameRange");
    methodNameRange.put("startLine", nameRange.begin.line);
    methodNameRange.put("startColumn", nameRange.begin.column);
    methodNameRange.put("endLine", nameRange.end.line);
    methodNameRange.put("endColumn", nameRange.end.column);
    result.put("snippet", slice(source, range));

    ArrayNode formals = result.putArray("formalParameters");
    method
        .getParameters()
        .forEach(
            parameter -> {
              ObjectNode formal = formals.addObject();
              formal.put("name", parameter.getNameAsString());
              formal.put("type", parameter.getTypeAsString());
              formal.put("varArgs", parameter.isVarArgs());
            });

    List<CallRecord> calls = new ArrayList<>();
    method
        .findAll(MethodCallExpr.class)
        .forEach(
            call -> {
              if (inside(call.getRange().orElse(null), range)) {
                calls.add(CallRecord.method(call));
              }
            });
    method
        .findAll(ObjectCreationExpr.class)
        .forEach(
            call -> {
              if (inside(call.getRange().orElse(null), range)) {
                calls.add(CallRecord.constructor(call));
              }
            });
    method
        .findAll(MethodReferenceExpr.class)
        .forEach(
            call -> {
              if (inside(call.getRange().orElse(null), range)) {
                calls.add(CallRecord.reference(call));
              }
            });
    calls.sort(Comparator.comparing(CallRecord::range, GoalDrivenSourceReader::compareRanges));
    ArrayNode entries = result.putArray("calls");
    for (CallRecord call : calls) {
      ObjectNode entry = entries.addObject();
      entry.put("kind", call.kind());
      entry.put("text", call.text());
      ObjectNode callRange = entry.putObject("range");
      callRange.put("startLine", call.range().begin.line);
      callRange.put("startColumn", call.range().begin.column);
      callRange.put("endLine", call.range().end.line);
      callRange.put("endColumn", call.range().end.column);
      ObjectNode navigationRange = entry.putObject("navigationRange");
      navigationRange.put("startLine", call.navigationRange().begin.line);
      navigationRange.put("startColumn", call.navigationRange().begin.column);
      navigationRange.put("endLine", call.navigationRange().end.line);
      navigationRange.put("endColumn", call.navigationRange().end.column);
      ArrayNode actuals = entry.putArray("actualArguments");
      call.arguments().forEach(actuals::add);
    }
    return result;
  }

  static ObjectNode readIntersecting(
      String source, String repositoryPath, int zeroBasedStartLine, int zeroBasedEndLine)
      throws IOException {
    CompilationUnit unit =
        new JavaParser()
            .parse(source)
            .getResult()
            .orElseThrow(() -> new IOException("Java syntax parse failed: " + repositoryPath));
    MethodDeclaration method =
        unit.findAll(MethodDeclaration.class).stream()
            .filter(candidate -> intersects(candidate.getRange().orElse(null), zeroBasedStartLine + 1, zeroBasedEndLine + 1))
            .min(Comparator.comparingInt(GoalDrivenSourceReader::span))
            .orElseThrow(
                () ->
                    new IOException(
                        "approved entry span is not inside a Java method: " + repositoryPath));
    Position anchor = method.getName().getBegin().orElseThrow();
    return read(source, repositoryPath, anchor.line - 1, anchor.column - 1);
  }

  private static boolean contains(Range range, Position position) {
    return range != null && range.begin.isBeforeOrEqual(position) && range.end.isAfterOrEqual(position);
  }

  private static boolean owns(MethodDeclaration candidate, Position anchor) {
    return contains(candidate.getRange().orElse(null), anchor)
        || candidate
            .getJavadocComment()
            .map(comment -> contains(comment.getRange().orElse(null), anchor))
            .orElse(false)
        || candidate.getAnnotations().stream()
            .anyMatch(annotation -> contains(annotation.getRange().orElse(null), anchor));
  }

  private static boolean inside(Range child, Range parent) {
    return child != null
        && parent != null
        && parent.begin.isBeforeOrEqual(child.begin)
        && parent.end.isAfterOrEqual(child.end);
  }

  private static boolean intersects(Range range, int startLine, int endLine) {
    return range != null && range.begin.line <= endLine && range.end.line >= startLine;
  }

  private static int span(MethodDeclaration method) {
    Range range = method.getRange().orElseThrow();
    return (range.end.line - range.begin.line) * 10000 + range.end.column - range.begin.column;
  }

  private static int compareRanges(Range left, Range right) {
    int line = Integer.compare(left.begin.line, right.begin.line);
    return line == 0 ? Integer.compare(left.begin.column, right.begin.column) : line;
  }

  private static String slice(String source, Range range) {
    String[] lines = source.split("\\R", -1);
    StringBuilder result = new StringBuilder();
    for (int line = range.begin.line; line <= range.end.line; line++) {
      String value = lines[line - 1];
      int first = line == range.begin.line ? range.begin.column - 1 : 0;
      int last = line == range.end.line ? range.end.column : value.length();
      result.append(value, first, Math.min(last, value.length()));
      if (line < range.end.line) {
        result.append('\n');
      }
    }
    return result.toString();
  }

  private record CallRecord(
      String kind, String text, Range range, Range navigationRange, List<String> arguments) {
    static CallRecord method(MethodCallExpr expression) {
      return new CallRecord(
          "METHOD_CALL",
          expression.toString(),
          expression.getRange().orElseThrow(),
          expression.getName().getRange().orElseThrow(),
          expression.getArguments().stream().map(Expression::toString).toList());
    }

    static CallRecord constructor(ObjectCreationExpr expression) {
      return new CallRecord(
          "CONSTRUCTOR_CALL",
          expression.toString(),
          expression.getRange().orElseThrow(),
          expression.getType().getRange().orElseThrow(),
          expression.getArguments().stream().map(Expression::toString).toList());
    }

    static CallRecord reference(MethodReferenceExpr expression) {
      return new CallRecord(
          "METHOD_REFERENCE",
          expression.toString(),
          expression.getRange().orElseThrow(),
          expression.getRange().orElseThrow(),
          List.of());
    }
  }
}
