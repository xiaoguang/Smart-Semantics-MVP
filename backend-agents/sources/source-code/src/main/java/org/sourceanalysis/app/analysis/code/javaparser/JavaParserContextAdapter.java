package org.sourceanalysis.app.analysis.code.javaparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;

/**
 * Produces engine-neutral entry material from JavaParser syntax and deliberately limited binding.
 */
final class JavaParserContextAdapter {

  private final JavaParserCatalogAdapter.ProjectModel project;

  JavaParserContextAdapter(JavaParserCatalogAdapter.ProjectModel project) {
    this.project = Objects.requireNonNull(project, "JavaParser project model");
  }

  EntryCodeContext collect(EntrySeed entry) {
    Objects.requireNonNull(entry, "entry seed");
    JavaParserCatalogAdapter.ParsedMethod root = project.methods().get(entry.methodKey());
    if (root == null || !root.view().sourceRange().equals(entry.methodRange())) {
      throw new IllegalArgumentException("entry seed does not identify one JavaParser declaration");
    }

    Map<String, JavaParserCatalogAdapter.ParsedMethod> included = new LinkedHashMap<>();
    List<PendingCall> calls = new ArrayList<>();
    List<EntryCodeContext.Limitation> limitations = new ArrayList<>();
    ArrayDeque<JavaParserCatalogAdapter.ParsedMethod> pending = new ArrayDeque<>();
    pending.add(root);
    while (!pending.isEmpty()) {
      JavaParserCatalogAdapter.ParsedMethod method = pending.removeFirst();
      if (included.putIfAbsent(method.view().methodKey(), method) != null) {
        continue;
      }
      for (MethodCallExpr call : owned(method, MethodCallExpr.class)) {
        List<JavaParserCatalogAdapter.ParsedMethod> targets = methodTargets(method, call);
        targets.stream()
            .filter(target -> !included.containsKey(target.view().methodKey()))
            .forEach(pending::addLast);
        calls.add(new PendingCall(method, call, targets));
      }
      for (ObjectCreationExpr call : owned(method, ObjectCreationExpr.class)) {
        List<JavaParserCatalogAdapter.ParsedMethod> targets = constructorTargets(method, call);
        targets.stream()
            .filter(target -> !included.containsKey(target.view().methodKey()))
            .forEach(pending::addLast);
        calls.add(new PendingCall(method, call, targets));
      }
    }

    List<EntryCodeContext.MethodCode> methods =
        included.values().stream()
            .sorted(
                Comparator.comparing(
                        (JavaParserCatalogAdapter.ParsedMethod value) -> value.source().path())
                    .thenComparingInt(value -> value.view().sourceRange().startOffsetUtf16()))
            .map(this::methodCode)
            .toList();
    Set<String> includedKeys = included.keySet();
    List<EntryCodeContext.CallSite> callSites =
        calls.stream()
            .sorted(
                Comparator.comparing((PendingCall value) -> value.caller().source().path())
                    .thenComparingInt(
                        value -> range(value.caller(), value.call()).startOffsetUtf16()))
            .map(value -> callSite(value, includedKeys, limitations))
            .toList();
    return new EntryCodeContext(
        EntryCodeContext.SCHEMA_VERSION,
        entry.entryId(),
        root.view().methodKey(),
        methods,
        callSites,
        List.of(),
        deduplicate(limitations),
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "STRICT_GRAPH_ENRICHMENT_NOT_ATTACHED_TO_JAVAPARSER_CONTEXT",
            List.of(),
            List.of(),
            null));
  }

  private EntryCodeContext.MethodCode methodCode(JavaParserCatalogAdapter.ParsedMethod method) {
    CallableDeclaration<?> declaration = method.declaration();
    List<EntryCodeContext.Control> controls = controls(method);
    List<EntryCodeContext.Exit> exits = new ArrayList<>();
    for (ReturnStmt value : owned(method, ReturnStmt.class)) {
      exits.add(
          new EntryCodeContext.Exit(
              "RETURN",
              value.getExpression().map(Node::toString).orElse(null),
              range(method, value)));
    }
    for (ThrowStmt value : owned(method, ThrowStmt.class)) {
      exits.add(
          new EntryCodeContext.Exit(
              "THROW", value.getExpression().toString(), range(method, value)));
    }
    exits.sort(Comparator.comparingInt(value -> value.sourceRange().startOffsetUtf16()));
    SourceRange sourceRange = method.view().sourceRange();
    String sourceText = slice(method.source().text(), sourceRange);
    return new EntryCodeContext.MethodCode(
        method.view().methodKey(),
        method.view().kind(),
        method.view().declaringType(),
        method.view().name(),
        declaration.getDeclarationAsString(true, true, true),
        null,
        method.view().parameters(),
        method.view().returnTypeText(),
        method.view().modifiers(),
        declaration.getAnnotations().stream().map(Node::toString).toList(),
        new EntryCodeContext.SourceSource(method.source().path(), sourceRange, sourceText),
        method.view().hasBody(),
        controls,
        exits);
  }

  private List<EntryCodeContext.Control> controls(JavaParserCatalogAdapter.ParsedMethod method) {
    List<EntryCodeContext.Control> controls = new ArrayList<>();
    for (IfStmt value : owned(method, IfStmt.class)) {
      controls.add(
          new EntryCodeContext.Control(
              "IF", value.getCondition().toString(), range(method, value), null));
      if (value.getElseStmt().isPresent()) {
        controls.add(
            new EntryCodeContext.Control(
                "ELSE", null, range(method, value.getElseStmt().orElseThrow()), null));
      }
    }
    for (TryStmt value : owned(method, TryStmt.class)) {
      controls.add(new EntryCodeContext.Control("TRY", null, range(method, value), null));
    }
    for (CatchClause value : owned(method, CatchClause.class)) {
      controls.add(
          new EntryCodeContext.Control(
              "CATCH", value.getParameter().toString(), range(method, value), null));
    }
    for (ForStmt value : owned(method, ForStmt.class)) {
      controls.add(
          new EntryCodeContext.Control(
              "LOOP",
              value.getCompare().map(Node::toString).orElse(null),
              range(method, value),
              null));
    }
    for (ForEachStmt value : owned(method, ForEachStmt.class)) {
      controls.add(
          new EntryCodeContext.Control(
              "LOOP",
              value.getVariable() + " : " + value.getIterable(),
              range(method, value),
              null));
    }
    for (WhileStmt value : owned(method, WhileStmt.class)) {
      controls.add(
          new EntryCodeContext.Control(
              "LOOP", value.getCondition().toString(), range(method, value), null));
    }
    for (DoStmt value : owned(method, DoStmt.class)) {
      controls.add(
          new EntryCodeContext.Control(
              "LOOP", value.getCondition().toString(), range(method, value), null));
    }
    for (SwitchStmt value : owned(method, SwitchStmt.class)) {
      controls.add(
          new EntryCodeContext.Control(
              "SWITCH", value.getSelector().toString(), range(method, value), null));
    }
    for (SynchronizedStmt value : owned(method, SynchronizedStmt.class)) {
      controls.add(
          new EntryCodeContext.Control(
              "SYNCHRONIZED", value.getExpression().toString(), range(method, value), null));
    }
    controls.sort(Comparator.comparingInt(value -> value.sourceRange().startOffsetUtf16()));
    return List.copyOf(controls);
  }

  private EntryCodeContext.CallSite callSite(
      PendingCall pending,
      Set<String> includedKeys,
      List<EntryCodeContext.Limitation> limitations) {
    JavaParserCatalogAdapter.ParsedMethod caller = pending.caller();
    Node call = pending.call();
    SourceRange site = range(caller, call);
    List<String> argumentTexts = arguments(call);
    List<EntryCodeContext.CallTarget> targets =
        pending.targets().stream()
            .map(
                target ->
                    new EntryCodeContext.CallTarget(
                        target.view().methodKey(),
                        List.of("DECLARATION"),
                        target.view().declaringType() + "#" + target.view().name(),
                        List.of("ENGINE_BINDING"),
                        target.view().hasBody() && includedKeys.contains(target.view().methodKey())
                            ? "BODY_INCLUDED"
                            : "DECLARATION_ONLY",
                        target.view().hasBody() && includedKeys.contains(target.view().methodKey())
                            ? null
                            : "JavaParser catalog declaration has no source body",
                        associations(argumentTexts.size(), target.view().parameters().size())))
            .toList();
    String resolution;
    String detail;
    if (targets.size() == 1) {
      resolution = "LOCATED";
      detail = null;
    } else if (targets.size() > 1) {
      resolution = "CANDIDATES";
      detail = "JavaParser retained multiple syntax-compatible repository declarations";
    } else {
      resolution = "UNRESOLVED";
      detail = "JavaParser baseline could not bind this source call without symbol solving";
    }
    String callKey = key("call", caller.source().path(), site);
    if (targets.isEmpty()) {
      limitations.add(
          new EntryCodeContext.Limitation(
              "JAVAPARSER_CALL_UNRESOLVED",
              detail,
              List.of(caller.view().methodKey()),
              List.of(callKey)));
    }
    List<EntryCodeContext.Control> controls = controls(caller);
    List<Integer> enclosingControls =
        java.util.stream.IntStream.range(0, controls.size())
            .filter(index -> contains(controls.get(index).sourceRange(), site))
            .boxed()
            .toList();
    String receiver =
        call instanceof MethodCallExpr methodCall
            ? methodCall.getScope().map(Node::toString).orElse(null)
            : ((ObjectCreationExpr) call).getTypeAsString();
    SourceRange navigationSite =
        call instanceof MethodCallExpr methodCall
            ? range(caller, methodCall.getName())
            : range(caller, ((ObjectCreationExpr) call).getType());
    return new EntryCodeContext.CallSite(
        callKey,
        caller.view().methodKey(),
        call instanceof ObjectCreationExpr ? "CONSTRUCTOR" : "METHOD",
        site,
        navigationSite,
        slice(caller.source().text(), site),
        receiver,
        java.util.stream.IntStream.range(0, argumentTexts.size())
            .mapToObj(index -> new EntryCodeContext.ActualArgument(index, argumentTexts.get(index)))
            .toList(),
        enclosingControls,
        false,
        targets,
        resolution,
        detail);
  }

  private List<JavaParserCatalogAdapter.ParsedMethod> methodTargets(
      JavaParserCatalogAdapter.ParsedMethod caller, MethodCallExpr call) {
    String declaringType = targetType(caller, call);
    if (declaringType == null) {
      return List.of();
    }
    return project.methods().values().stream()
        .filter(method -> method.view().declaringType().equals(declaringType))
        .filter(method -> method.view().name().equals(call.getNameAsString()))
        .filter(method -> compatibleArity(method.view().parameters(), call.getArguments().size()))
        .sorted(Comparator.comparing(method -> method.view().methodKey()))
        .toList();
  }

  private List<JavaParserCatalogAdapter.ParsedMethod> constructorTargets(
      JavaParserCatalogAdapter.ParsedMethod caller, ObjectCreationExpr call) {
    String declaringType = resolveType(caller, call.getTypeAsString());
    if (declaringType == null) {
      return List.of();
    }
    return project.methods().values().stream()
        .filter(method -> method.view().declaringType().equals(declaringType))
        .filter(method -> "CONSTRUCTOR".equals(method.view().kind()))
        .filter(method -> compatibleArity(method.view().parameters(), call.getArguments().size()))
        .sorted(Comparator.comparing(method -> method.view().methodKey()))
        .toList();
  }

  private String targetType(JavaParserCatalogAdapter.ParsedMethod caller, MethodCallExpr call) {
    if (call.getScope().isEmpty() || call.getScope().orElseThrow() instanceof ThisExpr) {
      return caller.view().declaringType();
    }
    if (!(call.getScope().orElseThrow() instanceof NameExpr name)) {
      return null;
    }
    TypeDeclaration<?> type = caller.declaration().findAncestor(TypeDeclaration.class).orElse(null);
    if (type != null) {
      for (FieldDeclaration field : type.getFields()) {
        var variable =
            field.getVariables().stream()
                .filter(candidate -> candidate.getNameAsString().equals(name.getNameAsString()))
                .findFirst();
        if (variable.isPresent()) {
          return resolveType(caller, variable.orElseThrow().getTypeAsString());
        }
      }
    }
    return resolveType(caller, name.getNameAsString());
  }

  private String resolveType(JavaParserCatalogAdapter.ParsedMethod caller, String sourceType) {
    String simple = sourceType.replaceAll("<.*>", "").replace("[]", "").strip();
    if (simple.contains(".")) {
      return project.methods().values().stream()
          .map(value -> value.view().declaringType())
          .filter(simple::equals)
          .findFirst()
          .orElse(null);
    }
    List<String> explicit =
        caller.source().unit().getImports().stream()
            .filter(imported -> !imported.isStatic() && !imported.isAsterisk())
            .map(imported -> imported.getNameAsString())
            .filter(imported -> imported.endsWith("." + simple))
            .toList();
    if (explicit.size() == 1 && hasType(explicit.get(0))) {
      return explicit.get(0);
    }
    String packageName =
        caller
            .source()
            .unit()
            .getPackageDeclaration()
            .map(value -> value.getNameAsString() + ".")
            .orElse("");
    String samePackage = packageName + simple;
    return hasType(samePackage) ? samePackage : null;
  }

  private boolean hasType(String qualifiedName) {
    return project.catalog().types().stream()
        .anyMatch(type -> qualifiedName.equals(type.qualifiedName()));
  }

  private static boolean compatibleArity(
      List<JavaDeclarationCatalog.ParameterView> parameters, int actualCount) {
    if (parameters.isEmpty()) {
      return actualCount == 0;
    }
    return parameters.get(parameters.size() - 1).varArgs()
        ? actualCount >= parameters.size() - 1
        : actualCount == parameters.size();
  }

  private static List<EntryCodeContext.ArgumentAssociation> associations(
      int actualCount, int formalCount) {
    int matched = Math.min(actualCount, formalCount);
    return java.util.stream.IntStream.range(0, matched)
        .mapToObj(
            index -> new EntryCodeContext.ArgumentAssociation(List.of(index), index, "POSITIONAL"))
        .toList();
  }

  private static List<String> arguments(Node call) {
    if (call instanceof MethodCallExpr methodCall) {
      return methodCall.getArguments().stream().map(Node::toString).toList();
    }
    return ((ObjectCreationExpr) call).getArguments().stream().map(Node::toString).toList();
  }

  private static <T extends Node> List<T> owned(
      JavaParserCatalogAdapter.ParsedMethod method, Class<T> type) {
    return method.declaration().findAll(type).stream()
        .filter(
            node ->
                node.findAncestor(CallableDeclaration.class)
                    .map(owner -> owner == method.declaration())
                    .orElse(false))
        .toList();
  }

  private static SourceRange range(JavaParserCatalogAdapter.ParsedMethod method, Node node) {
    return JavaParserCatalogAdapter.range(method.source().text(), node);
  }

  private static boolean contains(SourceRange outer, SourceRange inner) {
    long outerEnd = (long) outer.startOffsetUtf16() + outer.lengthUtf16();
    long innerEnd = (long) inner.startOffsetUtf16() + inner.lengthUtf16();
    return outer.startOffsetUtf16() <= inner.startOffsetUtf16() && innerEnd <= outerEnd;
  }

  private static String slice(String source, SourceRange range) {
    return source.substring(
        range.startOffsetUtf16(), range.startOffsetUtf16() + range.lengthUtf16());
  }

  private static String key(String prefix, String path, SourceRange range) {
    String input = path + "\u0000" + range.startOffsetUtf16() + "\u0000" + range.lengthUtf16();
    try {
      return prefix
          + ":"
          + HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static List<EntryCodeContext.Limitation> deduplicate(
      List<EntryCodeContext.Limitation> limitations) {
    Map<String, EntryCodeContext.Limitation> unique = new LinkedHashMap<>();
    limitations.forEach(
        limitation ->
            unique.putIfAbsent(
                limitation.code()
                    + "\u0000"
                    + limitation.methodKeys()
                    + "\u0000"
                    + limitation.callKeys(),
                limitation));
    return List.copyOf(unique.values());
  }

  private record PendingCall(
      JavaParserCatalogAdapter.ParsedMethod caller,
      Node call,
      List<JavaParserCatalogAdapter.ParsedMethod> targets) {}
}
