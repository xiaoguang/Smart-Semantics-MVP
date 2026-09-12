package org.sourceanalysis.app.analysis.code.jdt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;

/** Builds one coherent entry context from JDT Core syntax and JDT LS navigation. */
final class EntryCodeCollector {

  private final String snapshotId;
  private final String languageLevel;
  private final List<String> sourcePaths;
  private final JdtNavigationResolver.SourceAccess sources;
  private final SyntaxAccess syntax;
  private final JdtNavigationResolver navigation;
  private final CollectionBudget budget;
  private final Clock clock;
  private volatile ProjectIndex cachedIndex;

  EntryCodeCollector(
      String snapshotId,
      String languageLevel,
      List<String> sourcePaths,
      JdtNavigationResolver.SourceAccess sources,
      SyntaxAccess syntax,
      JdtNavigationResolver navigation,
      CollectionBudget budget) {
    this(
        snapshotId,
        languageLevel,
        sourcePaths,
        sources,
        syntax,
        navigation,
        budget,
        Clock.systemUTC());
  }

  EntryCodeCollector(
      String snapshotId,
      String languageLevel,
      List<String> sourcePaths,
      JdtNavigationResolver.SourceAccess sources,
      SyntaxAccess syntax,
      JdtNavigationResolver navigation,
      CollectionBudget budget,
      Clock clock) {
    this.snapshotId = required(snapshotId, "snapshot ID");
    this.languageLevel = required(languageLevel, "language level");
    this.sourcePaths =
        List.copyOf(Objects.requireNonNull(sourcePaths, "JDT source paths")).stream()
            .sorted()
            .toList();
    this.sources = Objects.requireNonNull(sources, "JDT projected sources");
    this.syntax = Objects.requireNonNull(syntax, "JDT syntax access");
    this.navigation = Objects.requireNonNull(navigation, "JDT navigation resolver");
    this.budget = Objects.requireNonNull(budget, "JDT collection budget");
    this.clock = Objects.requireNonNull(clock, "JDT collection clock");
  }

  JavaDeclarationCatalog catalog() {
    return index().catalog();
  }

  EntryCodeContext collect(EntrySeed entry) {
    Objects.requireNonNull(entry, "entry seed");
    ProjectIndex index = index();
    MethodInfo root = index.methodsByKey().get(entry.methodKey());
    if (root == null || !sameRange(root.declaration().sourceRange(), entry.methodRange())) {
      throw new IllegalArgumentException("entry seed does not identify one catalog declaration");
    }

    Instant started = clock.instant();
    ArrayDeque<PendingMethod> pending = new ArrayDeque<>();
    pending.add(new PendingMethod(root, 0));
    Map<String, MethodInfo> included = new LinkedHashMap<>();
    List<RawCall> rawCalls = new ArrayList<>();
    List<EntryCodeContext.Limitation> limitations = new ArrayList<>();
    long sourceChars = 0L;

    while (!pending.isEmpty()) {
      PendingMethod next = pending.removeFirst();
      MethodInfo method = next.method();
      if (included.containsKey(method.methodKey())) {
        continue;
      }
      String stop = stopReason(next.depth(), included.size(), sourceChars, started);
      if (stop != null && !method.methodKey().equals(root.methodKey())) {
        limitations.add(
            new EntryCodeContext.Limitation(
                stop,
                "JDT did not expand " + method.methodKey() + " because " + stop,
                List.of(),
                List.of()));
        continue;
      }
      included.put(method.methodKey(), method);
      sourceChars += method.declaration().sourceText().length();
      if (!method.declaration().bodyPresent()) {
        continue;
      }

      List<JdtSyntaxProtocol.CallSiteView> ownedCalls =
          method.response().callSites().stream()
              .filter(call -> call.ownerDeclarationId().equals(method.declaration().localId()))
              .sorted(Comparator.comparingInt(call -> call.sourceRange().startOffsetUtf16()))
              .toList();
      List<JdtNavigationResolver.ResolvedCall> resolved =
          navigation.resolve(
              method.sourcePath(), method.sourceText(), method.declaration(), ownedCalls);
      Map<String, JdtNavigationResolver.ResolvedCall> byCall =
          resolved.stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      JdtNavigationResolver.ResolvedCall::callLocalId,
                      value -> value,
                      (left, right) -> left,
                      LinkedHashMap::new));
      for (JdtSyntaxProtocol.CallSiteView call : ownedCalls) {
        JdtNavigationResolver.ResolvedCall resolution = byCall.get(call.localId());
        List<ResolvedTarget> targets = new ArrayList<>();
        if (resolution != null) {
          for (JdtNavigationResolver.Candidate candidate : resolution.candidates()) {
            MethodInfo target = index.methodAt(candidate.sourcePath(), candidate.selectionRange());
            targets.add(new ResolvedTarget(candidate, target));
            if (target != null
                && next.depth() < budget.maxDepth()
                && !included.containsKey(target.methodKey())) {
              pending.addLast(new PendingMethod(target, next.depth() + 1));
            }
          }
          if (!resolution.diagnostics().isEmpty()) {
            limitations.add(
                new EntryCodeContext.Limitation(
                    resolution.status(),
                    String.join("; ", resolution.diagnostics()),
                    List.of(method.methodKey()),
                    List.of(callKey(method.sourcePath(), call.sourceRange()))));
          }
        }
        rawCalls.add(new RawCall(method, call, resolution, List.copyOf(targets)));
      }
    }

    List<EntryCodeContext.MethodCode> methods =
        included.values().stream().map(this::methodCode).toList();
    Set<String> includedKeys = included.keySet();
    List<EntryCodeContext.CallSite> calls =
        rawCalls.stream().map(call -> callSite(call, includedKeys, limitations)).toList();
    return new EntryCodeContext(
        EntryCodeContext.SCHEMA_VERSION,
        entry.entryId(),
        root.methodKey(),
        methods,
        calls,
        List.of(),
        deduplicateLimitations(limitations),
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_JDT_COLLECTOR",
            List.of(),
            List.of(),
            null));
  }

  private ProjectIndex index() {
    ProjectIndex current = cachedIndex;
    if (current != null) {
      return current;
    }
    synchronized (this) {
      if (cachedIndex == null) {
        cachedIndex = buildIndex();
      }
      return cachedIndex;
    }
  }

  private ProjectIndex buildIndex() {
    Map<String, JdtSyntaxProtocol.Response> responses = new LinkedHashMap<>();
    Map<String, String> texts = new LinkedHashMap<>();
    Map<String, MethodInfo> methods = new LinkedHashMap<>();
    Map<String, String> diagnostics = new LinkedHashMap<>();
    for (String path : sourcePaths) {
      JdtNavigationResolver.SourceDocument source = sources.open(sources.uri(path));
      if (source == null || !path.equals(source.path())) {
        diagnostics.put(path, "SOURCE_NOT_AVAILABLE");
        continue;
      }
      try {
        JdtSyntaxProtocol.Response response = syntax.describe(path, languageLevel, source.text());
        responses.put(path, response);
        texts.put(path, source.text());
        List<JdtSyntaxProtocol.Diagnostic> errors =
            response.diagnostics().stream()
                .filter(value -> "ERROR".equals(value.severity()))
                .toList();
        if (!errors.isEmpty()) {
          diagnostics.put(path, errors.get(0).code() + ":" + errors.get(0).message());
        }
      } catch (RuntimeException failure) {
        diagnostics.put(path, "SYNTAX_UNAVAILABLE:" + failure.getMessage());
      }
    }
    for (Map.Entry<String, JdtSyntaxProtocol.Response> source : responses.entrySet()) {
      String path = source.getKey();
      String text = texts.get(path);
      for (JdtSyntaxProtocol.Declaration declaration : source.getValue().declarations()) {
        if (!callable(declaration.kind())) {
          continue;
        }
        String methodKey = methodKey(path, declaration);
        methods.put(
            methodKey, new MethodInfo(methodKey, path, text, source.getValue(), declaration));
      }
    }
    Map<String, List<String>> annotationKeysByOwner = new LinkedHashMap<>();
    List<JavaDeclarationCatalog.AnnotationView> annotationViews = new ArrayList<>();
    for (Map.Entry<String, JdtSyntaxProtocol.Response> source : responses.entrySet()) {
      for (JdtSyntaxProtocol.AnnotationView annotation : source.getValue().annotations()) {
        String annotationKey = annotationKey(source.getKey(), annotation);
        if (annotation.ownerDeclarationId() != null) {
          annotationKeysByOwner
              .computeIfAbsent(
                  ownerKey(source.getKey(), annotation.ownerDeclarationId()),
                  ignored -> new ArrayList<>())
              .add(annotationKey);
        }
        annotationViews.add(
            new JavaDeclarationCatalog.AnnotationView(
                annotationKey,
                annotation.nameText(),
                annotationQualifiedName(
                    source.getKey(),
                    texts.get(source.getKey()),
                    source.getValue(),
                    annotation,
                    responses),
                annotation.memberSource(),
                sourceRange(annotation.sourceRange()),
                sourceRange(annotation.nameSelection()),
                annotationValues(annotation.staticValues()),
                source.getKey()));
      }
    }
    List<JavaDeclarationCatalog.MethodDeclarationView> methodViews =
        methods.values().stream()
            .map(method -> methodView(method, annotationKeysByOwner))
            .sorted(
                Comparator.comparing(JavaDeclarationCatalog.MethodDeclarationView::sourcePath)
                    .thenComparing(method -> method.sourceRange().startOffsetUtf16()))
            .toList();
    List<JavaDeclarationCatalog.FieldDeclarationView> fieldViews =
        responses.entrySet().stream()
            .flatMap(
                source ->
                    source.getValue().declarations().stream()
                        .filter(declaration -> "FIELD".equals(declaration.kind()))
                        .map(
                            declaration ->
                                fieldView(source.getKey(), declaration, annotationKeysByOwner)))
            .sorted(
                Comparator.comparing(JavaDeclarationCatalog.FieldDeclarationView::sourcePath)
                    .thenComparing(field -> field.sourceRange().startOffsetUtf16()))
            .toList();
    Map<String, List<String>> fieldKeysByType = new LinkedHashMap<>();
    for (Map.Entry<String, JdtSyntaxProtocol.Response> source : responses.entrySet()) {
      for (JdtSyntaxProtocol.Declaration declaration : source.getValue().declarations()) {
        if ("FIELD".equals(declaration.kind())) {
          fieldKeysByType
              .computeIfAbsent(
                  qualifiedDeclaringType(source.getValue(), declaration),
                  ignored -> new ArrayList<>())
              .add(fieldKey(source.getKey(), declaration));
        }
      }
    }
    Map<String, List<String>> methodKeysByType = new LinkedHashMap<>();
    methods
        .values()
        .forEach(
            method ->
                methodKeysByType
                    .computeIfAbsent(
                        qualifiedDeclaringType(method.response(), method.declaration()),
                        ignored -> new ArrayList<>())
                    .add(method.methodKey()));
    List<JavaDeclarationCatalog.TypeDeclaration> typeViews =
        responses.entrySet().stream()
            .flatMap(
                source ->
                    source.getValue().declarations().stream()
                        .filter(declaration -> typeDeclaration(declaration.kind()))
                        .map(
                            declaration ->
                                typeView(
                                    source.getKey(),
                                    source.getValue(),
                                    declaration,
                                    annotationKeysByOwner,
                                    fieldKeysByType,
                                    methodKeysByType)))
            .sorted(
                Comparator.comparing(JavaDeclarationCatalog.TypeDeclaration::sourcePath)
                    .thenComparing(type -> type.sourceRange().startOffsetUtf16()))
            .toList();
    annotationViews.sort(
        Comparator.comparing(JavaDeclarationCatalog.AnnotationView::sourcePath)
            .thenComparing(annotation -> annotation.sourceRange().startOffsetUtf16()));
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            snapshotId,
            sourcePaths,
            typeViews,
            methodViews,
            List.copyOf(annotationViews),
            fieldViews,
            diagnostics);
    return new ProjectIndex(catalog, Map.copyOf(responses), Map.copyOf(texts), Map.copyOf(methods));
  }

  private JavaDeclarationCatalog.MethodDeclarationView methodView(
      MethodInfo method, Map<String, List<String>> annotationKeysByOwner) {
    JdtSyntaxProtocol.Declaration declaration = method.declaration();
    return new JavaDeclarationCatalog.MethodDeclarationView(
        method.methodKey(),
        qualifiedDeclaringType(method.response(), declaration),
        declaration.name(),
        declaration.kind(),
        declaration.modifiers(),
        declaration.parameters().stream().map(EntryCodeCollector::parameter).toList(),
        declaration.returnTypeText(),
        annotationKeys(annotationKeysByOwner, method.sourcePath(), declaration.localId()),
        method.sourcePath(),
        sourceRange(declaration.sourceRange()),
        declaration.bodyPresent());
  }

  private static JavaDeclarationCatalog.TypeDeclaration typeView(
      String sourcePath,
      JdtSyntaxProtocol.Response response,
      JdtSyntaxProtocol.Declaration declaration,
      Map<String, List<String>> annotationKeysByOwner,
      Map<String, List<String>> fieldKeysByType,
      Map<String, List<String>> methodKeysByType) {
    String qualifiedName = qualifiedType(response, declaration);
    return new JavaDeclarationCatalog.TypeDeclaration(
        sourcePath,
        sourceRange(declaration.sourceRange()),
        qualifiedName,
        declaration.kind(),
        annotationKeys(annotationKeysByOwner, sourcePath, declaration.localId()),
        List.copyOf(fieldKeysByType.getOrDefault(qualifiedName, List.of())),
        List.copyOf(methodKeysByType.getOrDefault(qualifiedName, List.of())),
        List.of());
  }

  private static JavaDeclarationCatalog.FieldDeclarationView fieldView(
      String sourcePath,
      JdtSyntaxProtocol.Declaration declaration,
      Map<String, List<String>> annotationKeysByOwner) {
    return new JavaDeclarationCatalog.FieldDeclarationView(
        fieldKey(sourcePath, declaration),
        declaration.name(),
        declaration.returnTypeText(),
        annotationKeys(annotationKeysByOwner, sourcePath, declaration.localId()),
        declaration.bodyPresent() ? declaration.sourceText() : null,
        sourcePath,
        sourceRange(declaration.sourceRange()));
  }

  private EntryCodeContext.MethodCode methodCode(MethodInfo method) {
    JdtSyntaxProtocol.Declaration declaration = method.declaration();
    List<JdtSyntaxProtocol.ControlView> controls =
        method.response().controls().stream()
            .filter(control -> control.ownerDeclarationId().equals(declaration.localId()))
            .sorted(Comparator.comparingInt(JdtSyntaxProtocol.ControlView::index))
            .toList();
    List<EntryCodeContext.Control> normalizedControls =
        controls.stream()
            .map(
                control ->
                    new EntryCodeContext.Control(
                        control.kind(),
                        control.expression(),
                        sourceRange(control.sourceRange()),
                        control.parentControlIndex()))
            .toList();
    List<EntryCodeContext.Exit> exits =
        method.response().exits().stream()
            .filter(exit -> exit.ownerDeclarationId().equals(declaration.localId()))
            .map(
                exit ->
                    new EntryCodeContext.Exit(
                        exit.kind(), exit.expression(), sourceRange(exit.sourceRange())))
            .toList();
    String enclosing =
        declaration.enclosingDeclarationId() == null
            ? null
            : method.response().declarations().stream()
                .filter(value -> value.localId().equals(declaration.enclosingDeclarationId()))
                .filter(value -> callable(value.kind()))
                .findFirst()
                .map(value -> methodKey(method.sourcePath(), value))
                .orElse(null);
    return new EntryCodeContext.MethodCode(
        method.methodKey(),
        declaration.kind(),
        declaration.declaringTypeName(),
        declaration.name(),
        declaration.signature(),
        enclosing,
        declaration.parameters().stream().map(EntryCodeCollector::parameter).toList(),
        declaration.returnTypeText(),
        declaration.modifiers(),
        declaration.annotations(),
        new EntryCodeContext.SourceSource(
            method.sourcePath(), sourceRange(declaration.sourceRange()), declaration.sourceText()),
        declaration.bodyPresent(),
        normalizedControls,
        exits);
  }

  private EntryCodeContext.CallSite callSite(
      RawCall raw, Set<String> includedKeys, List<EntryCodeContext.Limitation> limitations) {
    String key = callKey(raw.caller().sourcePath(), raw.call().sourceRange());
    Map<String, EntryCodeContext.CallTarget> targetsByIdentity = new LinkedHashMap<>();
    for (ResolvedTarget resolved : raw.targets()) {
      MethodInfo target = resolved.method();
      boolean included = target != null && includedKeys.contains(target.methodKey());
      String expansion;
      String reason;
      if (included && target.declaration().bodyPresent()) {
        expansion = "BODY_INCLUDED";
        reason = null;
      } else if (included) {
        expansion = "DECLARATION_ONLY";
        reason = "TARGET_DECLARATION_HAS_NO_BODY";
      } else {
        expansion = "NOT_EXPANDED";
        reason =
            target == null ? "TARGET_LOCATION_IS_NOT_A_CALLABLE_DECLARATION" : "COLLECTION_LIMIT";
      }
      List<EntryCodeContext.ArgumentAssociation> associations =
          included ? associations(raw.call(), target.declaration()) : List.of();
      EntryCodeContext.CallTarget candidate =
          new EntryCodeContext.CallTarget(
              included ? target.methodKey() : null,
              resolved.candidate().roles(),
              resolved.candidate().displayName(),
              resolved.candidate().navigationKinds(),
              expansion,
              reason,
              associations);
      String identity =
          candidate.methodKey() == null
              ? candidate.displayName() + '|' + candidate.expansion()
              : candidate.methodKey();
      targetsByIdentity.merge(identity, candidate, EntryCodeCollector::mergeTarget);
    }
    List<EntryCodeContext.CallTarget> targets = List.copyOf(targetsByIdentity.values());
    String resolution;
    String detail;
    if (targets.isEmpty()) {
      resolution = "UNRESOLVED";
      detail = "JDT returned no repository callable target";
      limitations.add(
          new EntryCodeContext.Limitation(
              "UNRESOLVED_CALL", detail, List.of(raw.caller().methodKey()), List.of(key)));
    } else if (targets.size() == 1
        && raw.resolution() != null
        && "LOCATED".equals(raw.resolution().status())) {
      resolution = "LOCATED";
      detail = null;
    } else {
      resolution = "CANDIDATES";
      detail =
          raw.resolution() != null && "NAVIGATION_CONFLICT".equals(raw.resolution().status())
              ? "NAVIGATION_CONFLICT"
              : "JDT returned multiple or non-unique target candidates";
    }
    List<Integer> enclosingControls =
        raw.caller().response().controls().stream()
            .filter(control -> control.ownerDeclarationId().equals(raw.call().ownerDeclarationId()))
            .filter(control -> contains(control.sourceRange(), raw.call().sourceRange()))
            .map(JdtSyntaxProtocol.ControlView::index)
            .sorted()
            .toList();
    return new EntryCodeContext.CallSite(
        key,
        raw.caller().methodKey(),
        raw.call().kind(),
        sourceRange(raw.call().sourceRange()),
        sourceRange(raw.call().navigationRange()),
        raw.call().expression(),
        raw.call().receiverExpression(),
        java.util.stream.IntStream.range(0, raw.call().actualArguments().size())
            .mapToObj(
                index ->
                    new EntryCodeContext.ActualArgument(
                        index, raw.call().actualArguments().get(index)))
            .toList(),
        enclosingControls,
        raw.call().deferred(),
        targets,
        resolution,
        detail);
  }

  private static EntryCodeContext.CallTarget mergeTarget(
      EntryCodeContext.CallTarget left, EntryCodeContext.CallTarget right) {
    LinkedHashSet<String> roles = new LinkedHashSet<>(left.roles());
    roles.addAll(right.roles());
    LinkedHashSet<String> navigationKinds = new LinkedHashSet<>(left.navigationKinds());
    navigationKinds.addAll(right.navigationKinds());
    return new EntryCodeContext.CallTarget(
        left.methodKey(),
        List.copyOf(roles),
        left.displayName(),
        List.copyOf(navigationKinds),
        left.expansion(),
        left.reason(),
        left.argumentAssociations());
  }

  private String stopReason(int depth, int methodCount, long sourceChars, Instant started) {
    if (depth > budget.maxDepth()) {
      return "DEPTH_LIMIT";
    }
    if (methodCount >= budget.maxMethods()) {
      return "METHOD_LIMIT";
    }
    if (sourceChars >= budget.maxSourceChars()) {
      return "SOURCE_SIZE_LIMIT";
    }
    if (java.time.Duration.between(started, clock.instant()).compareTo(budget.maxElapsed()) > 0) {
      return "TIME_LIMIT";
    }
    return null;
  }

  private static List<EntryCodeContext.ArgumentAssociation> associations(
      JdtSyntaxProtocol.CallSiteView call, JdtSyntaxProtocol.Declaration target) {
    if (target.parameters().isEmpty() || call.actualArguments().isEmpty()) {
      return List.of();
    }
    List<EntryCodeContext.ArgumentAssociation> result = new ArrayList<>();
    int formalCount = target.parameters().size();
    for (int formal = 0; formal < formalCount; formal++) {
      JdtSyntaxProtocol.ParameterView parameter = target.parameters().get(formal);
      if (parameter.varArgs()) {
        List<Integer> actuals =
            java.util.stream.IntStream.range(formal, call.actualArguments().size())
                .boxed()
                .toList();
        result.add(new EntryCodeContext.ArgumentAssociation(actuals, formal, "VARARGS"));
        break;
      }
      if (formal < call.actualArguments().size()) {
        result.add(new EntryCodeContext.ArgumentAssociation(List.of(formal), formal, "POSITIONAL"));
      }
    }
    return List.copyOf(result);
  }

  private static JavaDeclarationCatalog.ParameterView parameter(
      JdtSyntaxProtocol.ParameterView value) {
    return new JavaDeclarationCatalog.ParameterView(
        value.ordinal(), value.name(), value.typeText(), value.varArgs(), value.annotationTexts());
  }

  private String annotationQualifiedName(
      String sourcePath,
      String sourceText,
      JdtSyntaxProtocol.Response response,
      JdtSyntaxProtocol.AnnotationView annotation,
      Map<String, JdtSyntaxProtocol.Response> responses) {
    if (annotation.qualifiedName() != null && !annotation.qualifiedName().isBlank()) {
      return annotation.qualifiedName();
    }
    if (annotation.nameText().contains(".")) {
      return annotation.nameText();
    }
    List<String> explicit =
        response.imports().stream()
            .filter(value -> !value.isStatic() && !value.onDemand())
            .map(JdtSyntaxProtocol.ImportView::name)
            .filter(value -> value.endsWith("." + annotation.nameText()))
            .distinct()
            .toList();
    if (explicit.size() == 1) {
      return explicit.get(0);
    }
    String local = localAnnotationName(response, annotation.nameText());
    if (local != null) {
      return local;
    }
    try {
      List<JdtNavigationResolver.Location> definitionLocations =
          navigation.definitionsAt(sourcePath, sourceText, annotation.nameSelection());
      List<String> resolved =
          definitionLocations.stream()
              .map(location -> annotationDefinitionName(location, annotation.nameText(), responses))
              .filter(Objects::nonNull)
              .distinct()
              .toList();
      if (resolved.size() == 1) {
        return resolved.get(0);
      }
    } catch (RuntimeException ignored) {
      // The annotation remains a located candidate with unresolved identity.
    }
    List<String> wildcardCandidates =
        response.imports().stream()
            .filter(value -> !value.isStatic() && value.onDemand())
            .map(value -> value.name() + "." + annotation.nameText())
            .filter(candidate -> sourceTypeExists(candidate, responses))
            .distinct()
            .toList();
    return wildcardCandidates.size() == 1 ? wildcardCandidates.get(0) : null;
  }

  private String annotationDefinitionName(
      JdtNavigationResolver.Location location,
      String expectedSimpleName,
      Map<String, JdtSyntaxProtocol.Response> responses) {
    JdtNavigationResolver.SourceDocument source = sources.open(location.uri());
    if (source != null) {
      JdtSyntaxProtocol.Response response = responses.get(source.path());
      if (response == null) {
        return null;
      }
      JdtSyntaxProtocol.SourceRange selection =
          navigationRange(source.text(), location.selectionRange());
      return response.declarations().stream()
          .filter(declaration -> typeDeclaration(declaration.kind()))
          .filter(declaration -> expectedSimpleName.equals(declaration.name()))
          .filter(declaration -> contains(declaration.sourceRange(), selection))
          .map(declaration -> qualifiedType(response, declaration))
          .findFirst()
          .orElse(null);
    }
    return externalClassName(location.uri(), expectedSimpleName);
  }

  private static JdtSyntaxProtocol.SourceRange navigationRange(
      String source, JdtNavigationResolver.TextRange range) {
    int start = offset(source, range.start());
    int end = offset(source, range.end());
    return new JdtSyntaxProtocol.SourceRange(
        start, end - start, range.start().line() + 1, range.end().line() + 1);
  }

  private static int offset(String source, JdtNavigationResolver.Position position) {
    int start = 0;
    for (int line = 0; line < position.line(); line++) {
      int newline = source.indexOf('\n', start);
      if (newline < 0) {
        throw new IllegalArgumentException("navigation line is outside the source");
      }
      start = newline + 1;
    }
    int result = start + position.character();
    int lineEnd = source.indexOf('\n', start);
    if (lineEnd < 0) {
      lineEnd = source.length();
    }
    if (result < start || result > lineEnd) {
      throw new IllegalArgumentException("navigation character is outside the source");
    }
    return result;
  }

  private static String externalClassName(String uri, String expectedSimpleName) {
    String path;
    try {
      path = URI.create(uri).getPath();
    } catch (IllegalArgumentException invalid) {
      return null;
    }
    if (path == null) {
      return null;
    }
    String suffix = "/" + expectedSimpleName + ".class";
    int end = path.indexOf(suffix);
    if (end < 0) {
      return null;
    }
    String prefix = path.substring(0, end);
    int archive = prefix.lastIndexOf(".jar/");
    if (archive >= 0) {
      prefix = prefix.substring(archive + ".jar/".length());
    } else {
      int packageStart = packageStart(prefix);
      if (packageStart < 0) {
        return null;
      }
      prefix = prefix.substring(packageStart + 1);
    }
    if (prefix.isBlank()) {
      return null;
    }
    return prefix.replace('/', '.').replace('$', '.') + "." + expectedSimpleName;
  }

  private static int packageStart(String value) {
    return java.util.stream.Stream.of(
            "/org/", "/com/", "/net/", "/io/", "/java/", "/javax/", "/jakarta/")
        .mapToInt(value::lastIndexOf)
        .filter(index -> index >= 0)
        .min()
        .orElse(-1);
  }

  private static String localAnnotationName(
      JdtSyntaxProtocol.Response response, String simpleName) {
    return response.declarations().stream()
        .filter(declaration -> "ANNOTATION_TYPE".equals(declaration.kind()))
        .filter(declaration -> simpleName.equals(declaration.name()))
        .map(declaration -> qualifiedType(response, declaration))
        .findFirst()
        .orElse(null);
  }

  private static boolean sourceTypeExists(
      String qualifiedName, Map<String, JdtSyntaxProtocol.Response> responses) {
    return responses.values().stream()
        .flatMap(
            response ->
                response.declarations().stream()
                    .map(declaration -> Map.entry(response, declaration)))
        .filter(entry -> typeDeclaration(entry.getValue().kind()))
        .anyMatch(entry -> qualifiedName.equals(qualifiedType(entry.getKey(), entry.getValue())));
  }

  private static Map<String, Object> annotationValues(
      Map<String, JdtSyntaxProtocol.StaticValue> values) {
    Map<String, Object> result = new LinkedHashMap<>();
    values.forEach((key, value) -> result.put(key, annotationValue(value)));
    return Map.copyOf(result);
  }

  private static Object annotationValue(JdtSyntaxProtocol.StaticValue value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("kind", value.kind());
    result.put("source", value.source());
    if (value.value() != null) {
      result.put("value", value.value());
    }
    if (value.elements() != null && !value.elements().isEmpty()) {
      result.put(
          "elements", value.elements().stream().map(EntryCodeCollector::annotationValue).toList());
    } else if ("ARRAY".equals(value.kind())) {
      result.put("elements", List.of());
    }
    return Map.copyOf(result);
  }

  private static List<String> annotationKeys(
      Map<String, List<String>> byOwner, String sourcePath, String ownerLocalId) {
    return List.copyOf(byOwner.getOrDefault(ownerKey(sourcePath, ownerLocalId), List.of()));
  }

  private static String ownerKey(String sourcePath, String localId) {
    return sourcePath + '|' + localId;
  }

  private static String annotationKey(
      String sourcePath, JdtSyntaxProtocol.AnnotationView annotation) {
    return "annotation:"
        + identity(
            "jdt-annotation-v1",
            sourcePath,
            Integer.toString(annotation.sourceRange().startOffsetUtf16()),
            Integer.toString(annotation.sourceRange().lengthUtf16()));
  }

  private static String fieldKey(String sourcePath, JdtSyntaxProtocol.Declaration declaration) {
    return "field:"
        + identity(
            "jdt-field-v1",
            sourcePath,
            Integer.toString(declaration.sourceRange().startOffsetUtf16()),
            declaration.name());
  }

  private static String qualifiedDeclaringType(
      JdtSyntaxProtocol.Response response, JdtSyntaxProtocol.Declaration declaration) {
    String declaring = declaration.declaringTypeName();
    if (declaring == null || declaring.isBlank()) {
      return null;
    }
    return qualify(response.packageName(), declaring);
  }

  private static String qualifiedType(
      JdtSyntaxProtocol.Response response, JdtSyntaxProtocol.Declaration declaration) {
    String local =
        declaration.declaringTypeName() == null || declaration.declaringTypeName().isBlank()
            ? declaration.name()
            : declaration.declaringTypeName() + "." + declaration.name();
    return qualify(response.packageName(), local);
  }

  private static String qualify(String packageName, String localName) {
    return packageName == null || packageName.isBlank() ? localName : packageName + "." + localName;
  }

  private static boolean typeDeclaration(String kind) {
    return Set.of("CLASS", "INTERFACE", "ENUM", "RECORD", "ANNOTATION_TYPE").contains(kind);
  }

  private static SourceRange sourceRange(JdtSyntaxProtocol.SourceRange value) {
    return new SourceRange(
        value.startOffsetUtf16(), value.lengthUtf16(), value.startLine(), value.endLine());
  }

  private static boolean contains(
      JdtSyntaxProtocol.SourceRange outer, JdtSyntaxProtocol.SourceRange inner) {
    return outer.startOffsetUtf16() <= inner.startOffsetUtf16()
        && outer.endOffsetUtf16() >= inner.endOffsetUtf16();
  }

  private static boolean sameRange(JdtSyntaxProtocol.SourceRange left, SourceRange right) {
    return left.startOffsetUtf16() == right.startOffsetUtf16()
        && left.lengthUtf16() == right.lengthUtf16()
        && left.startLine() == right.startLine()
        && left.endLine() == right.endLine();
  }

  private static boolean callable(String kind) {
    return Set.of("METHOD", "CONSTRUCTOR", "INITIALIZER", "LAMBDA").contains(kind);
  }

  private static String methodKey(String path, JdtSyntaxProtocol.Declaration declaration) {
    return "method:"
        + identity(
            "jdt-method-v1",
            path,
            declaration.kind(),
            Integer.toString(declaration.sourceRange().startOffsetUtf16()),
            Integer.toString(declaration.sourceRange().lengthUtf16()));
  }

  private static String callKey(String path, JdtSyntaxProtocol.SourceRange range) {
    return "call:"
        + identity(
            "jdt-call-v1",
            path,
            Integer.toString(range.startOffsetUtf16()),
            Integer.toString(range.lengthUtf16()));
  }

  private static String identity(String... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String value : values) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static List<EntryCodeContext.Limitation> deduplicateLimitations(
      List<EntryCodeContext.Limitation> values) {
    Map<String, EntryCodeContext.Limitation> unique = new LinkedHashMap<>();
    for (EntryCodeContext.Limitation value : values) {
      unique.putIfAbsent(
          value.code()
              + '|'
              + value.detail()
              + '|'
              + String.join(",", value.methodKeys())
              + '|'
              + String.join(",", value.callKeys()),
          value);
    }
    return List.copyOf(unique.values());
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }

  interface SyntaxAccess {
    JdtSyntaxProtocol.Response describe(String sourcePath, String languageLevel, String source);
  }

  private record MethodInfo(
      String methodKey,
      String sourcePath,
      String sourceText,
      JdtSyntaxProtocol.Response response,
      JdtSyntaxProtocol.Declaration declaration) {}

  private record PendingMethod(MethodInfo method, int depth) {}

  private record ResolvedTarget(JdtNavigationResolver.Candidate candidate, MethodInfo method) {}

  private record RawCall(
      MethodInfo caller,
      JdtSyntaxProtocol.CallSiteView call,
      JdtNavigationResolver.ResolvedCall resolution,
      List<ResolvedTarget> targets) {}

  private record ProjectIndex(
      JavaDeclarationCatalog catalog,
      Map<String, JdtSyntaxProtocol.Response> responses,
      Map<String, String> texts,
      Map<String, MethodInfo> methodsByKey) {

    private MethodInfo methodAt(String sourcePath, JdtSyntaxProtocol.SourceRange selectionRange) {
      return methodsByKey.values().stream()
          .filter(method -> method.sourcePath().equals(sourcePath))
          .filter(method -> contains(method.declaration().sourceRange(), selectionRange))
          .min(Comparator.comparingInt(method -> method.declaration().sourceRange().lengthUtf16()))
          .orElse(null);
    }
  }
}
