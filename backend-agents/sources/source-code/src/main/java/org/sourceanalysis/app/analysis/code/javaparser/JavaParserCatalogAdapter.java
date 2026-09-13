package org.sourceanalysis.app.analysis.code.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;

/** Maps JavaParser syntax into the engine-neutral declaration catalog without symbol solving. */
final class JavaParserCatalogAdapter {

  private static final String FALLBACK_VERSION = "3.28.2";

  ProjectModel parse(
      String snapshotId, List<String> sourceEntries, Path projectRoot, String sourceLevel) {
    Map<String, ParsedSource> sources = new LinkedHashMap<>();
    Map<String, String> diagnostics = new LinkedHashMap<>();
    JavaParser parser = new JavaParser(configuration(sourceLevel));
    for (String sourcePath :
        sourceEntries.stream().filter(path -> path.endsWith(".java")).sorted().toList()) {
      Path file = projectRoot.resolve(sourcePath).normalize();
      try {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        var parsed = parser.parse(text);
        if (parsed.getResult().isEmpty()) {
          diagnostics.put(sourcePath, "PARSE_UNAVAILABLE");
          continue;
        }
        if (!parsed.getProblems().isEmpty()) {
          diagnostics.put(sourcePath, parsed.getProblems().get(0).getMessage());
        }
        sources.put(
            sourcePath, new ParsedSource(sourcePath, text, parsed.getResult().orElseThrow()));
      } catch (IOException | RuntimeException failure) {
        diagnostics.put(sourcePath, "PARSE_UNAVAILABLE:" + failure.getMessage());
      }
    }
    return model(snapshotId, sourceEntries, sources, diagnostics);
  }

  static String toolVersion() {
    String implementationVersion = JavaParser.class.getPackage().getImplementationVersion();
    return implementationVersion == null || implementationVersion.isBlank()
        ? FALLBACK_VERSION
        : implementationVersion;
  }

  private static ParserConfiguration configuration(String sourceLevel) {
    ParserConfiguration configuration = new ParserConfiguration();
    try {
      configuration.setLanguageLevel(
          ParserConfiguration.LanguageLevel.valueOf("JAVA_" + sourceLevel));
    } catch (IllegalArgumentException unsupported) {
      configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }
    return configuration;
  }

  private static ProjectModel model(
      String snapshotId,
      List<String> allEntries,
      Map<String, ParsedSource> sources,
      Map<String, String> diagnostics) {
    List<JavaDeclarationCatalog.TypeDeclaration> types = new ArrayList<>();
    List<JavaDeclarationCatalog.MethodDeclarationView> methods = new ArrayList<>();
    List<JavaDeclarationCatalog.AnnotationView> annotations = new ArrayList<>();
    List<JavaDeclarationCatalog.FieldDeclarationView> fields = new ArrayList<>();
    Map<String, ParsedMethod> parsedMethods = new LinkedHashMap<>();
    Map<Node, List<String>> annotationKeysByOwner = new IdentityHashMap<>();

    for (ParsedSource source : sources.values()) {
      for (AnnotationExpr annotation : source.unit().findAll(AnnotationExpr.class)) {
        Node owner = annotationOwner(annotation);
        if (owner == null) {
          continue;
        }
        String key = key("annotation", source.path(), range(source.text(), annotation));
        annotationKeysByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(key);
        annotations.add(
            new JavaDeclarationCatalog.AnnotationView(
                key,
                annotation.getNameAsString(),
                qualifiedAnnotation(source.unit(), annotation),
                annotationMemberSource(annotation),
                range(source.text(), annotation),
                range(source.text(), annotation.getName()),
                annotationValues(annotation),
                source.path()));
      }
    }

    for (ParsedSource source : sources.values()) {
      for (TypeDeclaration<?> type : source.unit().findAll(TypeDeclaration.class)) {
        if (type.findAncestor(TypeDeclaration.class).isPresent()) {
          continue;
        }
        addTypeTree(
            source, type, null, annotationKeysByOwner, types, methods, fields, parsedMethods);
      }
    }

    Comparator<JavaDeclarationCatalog.MethodDeclarationView> methodOrder =
        Comparator.comparing(JavaDeclarationCatalog.MethodDeclarationView::sourcePath)
            .thenComparingInt(value -> value.sourceRange().startOffsetUtf16());
    methods.sort(methodOrder);
    types.sort(
        Comparator.comparing(JavaDeclarationCatalog.TypeDeclaration::sourcePath)
            .thenComparingInt(value -> value.sourceRange().startOffsetUtf16()));
    annotations.sort(
        Comparator.comparing(JavaDeclarationCatalog.AnnotationView::sourcePath)
            .thenComparingInt(value -> value.sourceRange().startOffsetUtf16()));
    fields.sort(
        Comparator.comparing(JavaDeclarationCatalog.FieldDeclarationView::sourcePath)
            .thenComparingInt(value -> value.sourceRange().startOffsetUtf16()));
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            snapshotId,
            allEntries.stream().filter(path -> path.endsWith(".java")).sorted().toList(),
            types,
            methods,
            annotations,
            fields,
            diagnostics);
    return new ProjectModel(catalog, Map.copyOf(sources), Map.copyOf(parsedMethods));
  }

  private static void addTypeTree(
      ParsedSource source,
      TypeDeclaration<?> type,
      String enclosingType,
      Map<Node, List<String>> annotationKeysByOwner,
      List<JavaDeclarationCatalog.TypeDeclaration> types,
      List<JavaDeclarationCatalog.MethodDeclarationView> methods,
      List<JavaDeclarationCatalog.FieldDeclarationView> fields,
      Map<String, ParsedMethod> parsedMethods) {
    String qualifiedName = qualifiedType(source.unit(), enclosingType, type.getNameAsString());
    List<String> fieldKeys = new ArrayList<>();
    for (FieldDeclaration field : type.getFields()) {
      for (var variable : field.getVariables()) {
        String key = key("field", source.path(), range(source.text(), variable));
        fieldKeys.add(key);
        fields.add(
            new JavaDeclarationCatalog.FieldDeclarationView(
                key,
                variable.getNameAsString(),
                variable.getTypeAsString(),
                annotationKeysByOwner.getOrDefault(field, List.of()),
                variable.getInitializer().map(Node::toString).orElse(null),
                source.path(),
                range(source.text(), variable)));
      }
    }
    List<String> methodKeys = new ArrayList<>();
    for (BodyDeclaration<?> member : type.getMembers()) {
      if (!(member instanceof CallableDeclaration<?> callable)) {
        continue;
      }
      SourceRange sourceRange = range(source.text(), callable);
      String methodKey = key("method", source.path(), sourceRange);
      methodKeys.add(methodKey);
      boolean constructor = callable instanceof ConstructorDeclaration;
      String name = callable.getNameAsString();
      String returnType =
          constructor ? qualifiedName : ((MethodDeclaration) callable).getTypeAsString();
      boolean hasBody = constructor || ((MethodDeclaration) callable).getBody().isPresent();
      List<JavaDeclarationCatalog.ParameterView> parameters =
          java.util.stream.IntStream.range(0, callable.getParameters().size())
              .mapToObj(
                  ordinal -> {
                    var parameter = callable.getParameter(ordinal);
                    return new JavaDeclarationCatalog.ParameterView(
                        ordinal,
                        parameter.getNameAsString(),
                        parameter.getTypeAsString(),
                        parameter.isVarArgs(),
                        parameter.getAnnotations().stream().map(Node::toString).toList());
                  })
              .toList();
      JavaDeclarationCatalog.MethodDeclarationView view =
          new JavaDeclarationCatalog.MethodDeclarationView(
              methodKey,
              qualifiedName,
              name,
              constructor ? "CONSTRUCTOR" : "METHOD",
              callable.getModifiers().stream()
                  .map(modifier -> modifier.getKeyword().asString())
                  .sorted()
                  .toList(),
              parameters,
              returnType,
              annotationKeysByOwner.getOrDefault(callable, List.of()),
              source.path(),
              sourceRange,
              hasBody);
      methods.add(view);
      parsedMethods.put(methodKey, new ParsedMethod(view, source, callable));
    }
    List<String> supertypes = new ArrayList<>();
    if (type instanceof ClassOrInterfaceDeclaration declaration) {
      declaration.getExtendedTypes().forEach(value -> supertypes.add(value.toString()));
      declaration.getImplementedTypes().forEach(value -> supertypes.add(value.toString()));
    } else if (type instanceof EnumDeclaration declaration) {
      declaration.getImplementedTypes().forEach(value -> supertypes.add(value.toString()));
    } else if (type instanceof RecordDeclaration declaration) {
      declaration.getImplementedTypes().forEach(value -> supertypes.add(value.toString()));
    }
    types.add(
        new JavaDeclarationCatalog.TypeDeclaration(
            source.path(),
            range(source.text(), type),
            qualifiedName,
            typeKind(type),
            annotationKeysByOwner.getOrDefault(type, List.of()),
            fieldKeys,
            methodKeys,
            supertypes));
    for (BodyDeclaration<?> member : type.getMembers()) {
      if (member instanceof TypeDeclaration<?> nested) {
        addTypeTree(
            source,
            nested,
            qualifiedName,
            annotationKeysByOwner,
            types,
            methods,
            fields,
            parsedMethods);
      }
    }
  }

  private static String typeKind(TypeDeclaration<?> type) {
    if (type instanceof ClassOrInterfaceDeclaration declaration) {
      return declaration.isInterface() ? "INTERFACE" : "CLASS";
    }
    if (type instanceof EnumDeclaration) {
      return "ENUM";
    }
    if (type instanceof RecordDeclaration) {
      return "RECORD";
    }
    if (type instanceof AnnotationDeclaration) {
      return "ANNOTATION";
    }
    return "TYPE";
  }

  private static String qualifiedType(
      CompilationUnit unit, String enclosingType, String simpleName) {
    if (enclosingType != null) {
      return enclosingType + "." + simpleName;
    }
    String packageName =
        unit.getPackageDeclaration().map(value -> value.getNameAsString() + ".").orElse("");
    return packageName + simpleName;
  }

  private static Node annotationOwner(AnnotationExpr annotation) {
    return annotation
        .getParentNode()
        .filter(
            node ->
                node instanceof TypeDeclaration<?>
                    || node instanceof CallableDeclaration<?>
                    || node instanceof FieldDeclaration)
        .orElse(null);
  }

  private static String qualifiedAnnotation(CompilationUnit unit, AnnotationExpr annotation) {
    String name = annotation.getNameAsString();
    if (name.contains(".")) {
      return name;
    }
    var explicit =
        unit.getImports().stream()
            .filter(imported -> !imported.isStatic() && !imported.isAsterisk())
            .map(imported -> imported.getNameAsString())
            .filter(imported -> imported.endsWith("." + name))
            .toList();
    if (explicit.size() == 1) {
      return explicit.get(0);
    }
    var wildcard =
        unit.getImports().stream()
            .filter(imported -> !imported.isStatic() && imported.isAsterisk())
            .map(imported -> imported.getNameAsString() + "." + name)
            .toList();
    return wildcard.size() == 1 ? wildcard.get(0) : null;
  }

  private static String annotationMemberSource(AnnotationExpr annotation) {
    if (annotation.isMarkerAnnotationExpr()) {
      return "";
    }
    if (annotation.isSingleMemberAnnotationExpr()) {
      return annotation.asSingleMemberAnnotationExpr().getMemberValue().toString();
    }
    return annotation.asNormalAnnotationExpr().getPairs().toString();
  }

  private static Map<String, Object> annotationValues(AnnotationExpr annotation) {
    Map<String, Object> values = new LinkedHashMap<>();
    if (annotation.isSingleMemberAnnotationExpr()) {
      values.put("value", staticValue(annotation.asSingleMemberAnnotationExpr().getMemberValue()));
    } else if (annotation.isNormalAnnotationExpr()) {
      annotation
          .asNormalAnnotationExpr()
          .getPairs()
          .forEach(pair -> values.put(pair.getNameAsString(), staticValue(pair.getValue())));
    }
    return Map.copyOf(values);
  }

  private static Map<String, Object> staticValue(Expression expression) {
    Map<String, Object> value = new LinkedHashMap<>();
    if (expression instanceof StringLiteralExpr literal) {
      value.put("kind", "STRING");
      value.put("value", literal.getValue());
    } else if (expression instanceof ArrayInitializerExpr array) {
      value.put("kind", "ARRAY");
      value.put(
          "elements",
          array.getValues().stream().map(JavaParserCatalogAdapter::staticValue).toList());
    } else if (expression.isFieldAccessExpr() || expression.isNameExpr()) {
      value.put("kind", "SYMBOL");
      value.put("value", expression.toString());
    } else {
      value.put("kind", "SOURCE");
      value.put("value", expression.toString());
    }
    return Map.copyOf(value);
  }

  static SourceRange range(String source, Node node) {
    var range = node.getRange().orElseThrow(() -> invalid("source node has no range"));
    int start = offset(source, range.begin.line, range.begin.column);
    int inclusiveEnd = offset(source, range.end.line, range.end.column);
    int end = Math.min(source.length(), inclusiveEnd + 1);
    return new SourceRange(start, end - start, range.begin.line, range.end.line);
  }

  private static int offset(String source, int line, int column) {
    int offset = 0;
    for (int current = 1; current < line; current++) {
      int newline = source.indexOf('\n', offset);
      if (newline < 0) {
        throw invalid("source position line is outside source");
      }
      offset = newline + 1;
    }
    int result = offset + column - 1;
    if (result < 0 || result > source.length()) {
      throw invalid("source position column is outside source");
    }
    return result;
  }

  private static String key(String prefix, String path, SourceRange range) {
    return prefix
        + ":"
        + digest(path + "\u0000" + range.startOffsetUtf16() + "\u0000" + range.lengthUtf16());
  }

  private static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static CodeEngineException invalid(String detail) {
    return new CodeEngineException(CodeEngineException.SOURCE_INVALID, detail);
  }

  record ParsedSource(String path, String text, CompilationUnit unit) {
    ParsedSource {
      Objects.requireNonNull(path, "source path");
      Objects.requireNonNull(text, "source text");
      Objects.requireNonNull(unit, "compilation unit");
    }
  }

  record ParsedMethod(
      JavaDeclarationCatalog.MethodDeclarationView view,
      ParsedSource source,
      CallableDeclaration<?> declaration) {}

  record ProjectModel(
      JavaDeclarationCatalog catalog,
      Map<String, ParsedSource> sources,
      Map<String, ParsedMethod> methods) {}
}
