package org.sourceanalysis.app.analysis.code;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Schema-versioned engine-neutral source material for one selected entry. */
public record EntryCodeContext(
    String schemaVersion,
    String entryId,
    String entryMethodKey,
    List<MethodCode> methods,
    List<CallSite> calls,
    List<SupportingSource> supportingSources,
    List<Limitation> limitations,
    TechnicalEnhancements technicalEnhancements) {

  public static final String SCHEMA_VERSION = "entry-code-context-v1";

  public EntryCodeContext {
    if (!SCHEMA_VERSION.equals(schemaVersion)) {
      throw new IllegalArgumentException("entry code context schema version is unsupported");
    }
    entryId = required(entryId, "entry context ID");
    entryMethodKey = required(entryMethodKey, "entry method key");
    methods = immutable(methods, "context methods");
    calls = immutable(calls, "context calls");
    requireElementsOfType(methods, MethodCode.class, "context methods");
    requireElementsOfType(calls, CallSite.class, "context calls");
    methods = immutableDistinct(methods, "context methods", MethodCode::methodKey);
    calls = immutableDistinct(calls, "context calls", CallSite::callKey);
    supportingSources = immutable(supportingSources, "context supporting sources");
    limitations = immutable(limitations, "context limitations");
    technicalEnhancements = Objects.requireNonNull(technicalEnhancements, "technical enhancements");

    java.util.Map<String, MethodCode> methodsByKey =
        methods.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    MethodCode::methodKey, java.util.function.Function.identity()));
    Set<String> methodKeys = methodsByKey.keySet();
    if (!methodKeys.contains(entryMethodKey)) {
      throw new IllegalArgumentException("entry context must contain its entry method");
    }
    Set<String> callKeys =
        calls.stream().map(CallSite::callKey).collect(java.util.stream.Collectors.toSet());
    for (CallSite call : calls) {
      MethodCode caller = methodsByKey.get(call.callerMethodKey());
      if (caller == null) {
        throw new IllegalArgumentException(
            "call caller method key must reference a context method");
      }
      if (call.enclosingControlIndexes().stream()
          .anyMatch(index -> index >= caller.controls().size())) {
        throw new IllegalArgumentException(
            "call enclosing control index must reference a caller control");
      }
      for (CallTarget target : call.targets()) {
        if (target.methodKey() != null && !methodKeys.contains(target.methodKey())) {
          throw new IllegalArgumentException(
              "call target method key must reference a context method");
        }
        MethodCode targetMethod =
            target.methodKey() == null ? null : methodsByKey.get(target.methodKey());
        if ("BODY_INCLUDED".equals(target.expansion())
            && (targetMethod == null || !targetMethod.bodyPresent())) {
          throw new IllegalArgumentException(
              "body-included call target must reference a context method with a body");
        }
        for (ArgumentAssociation association : target.argumentAssociations()) {
          if (association.actualOrdinals().stream()
              .anyMatch(ordinal -> ordinal >= call.actualArguments().size())) {
            throw new IllegalArgumentException(
                "argument association actual ordinal must reference a call argument");
          }
          if (association.formalOrdinal() != null
              && (targetMethod == null
                  || association.formalOrdinal() >= targetMethod.parameters().size())) {
            throw new IllegalArgumentException(
                "argument association formal ordinal must reference a target parameter");
          }
        }
      }
    }
    for (SupportingSource source : supportingSources) {
      if (!methodKeys.containsAll(source.relatedMethodKeys())) {
        throw new IllegalArgumentException(
            "supporting source method keys must reference context methods");
      }
    }
    for (Limitation limitation : limitations) {
      if (!methodKeys.containsAll(limitation.methodKeys())) {
        throw new IllegalArgumentException("limitation method keys must reference context methods");
      }
      if (!callKeys.containsAll(limitation.callKeys())) {
        throw new IllegalArgumentException("limitation call keys must reference context calls");
      }
    }
  }

  /** A complete source declaration, including its original text when a body is present. */
  public record MethodCode(
      String methodKey,
      String kind,
      String declaringType,
      String name,
      String signature,
      String enclosingMethodKey,
      List<JavaDeclarationCatalog.ParameterView> parameters,
      String returnTypeText,
      List<String> modifiers,
      List<String> annotations,
      SourceSource source,
      boolean bodyPresent,
      List<Control> controls,
      List<Exit> exits) {

    public MethodCode {
      methodKey = required(methodKey, "method code key");
      if (!Set.of("METHOD", "CONSTRUCTOR", "INITIALIZER", "LAMBDA").contains(kind)) {
        throw new IllegalArgumentException("method code kind is unsupported");
      }
      if (name != null && name.isBlank()) {
        throw new IllegalArgumentException("method code name cannot be blank");
      }
      if (("METHOD".equals(kind) || "CONSTRUCTOR".equals(kind)) && name == null) {
        throw new IllegalArgumentException("named method code requires a name");
      }
      signature = required(signature, "method code signature");
      if (enclosingMethodKey != null && enclosingMethodKey.isBlank()) {
        throw new IllegalArgumentException("enclosing method key cannot be blank");
      }
      parameters = immutable(parameters, "method code parameters");
      for (int index = 0; index < parameters.size(); index++) {
        if (parameters.get(index).ordinal() != index) {
          throw new IllegalArgumentException("method parameter ordinals must be ordered");
        }
      }
      modifiers = immutableDistinct(modifiers, "method code modifiers", value -> value);
      annotations = immutableDistinct(annotations, "method code annotations", value -> value);
      source = Objects.requireNonNull(source, "method code source");
      if (source.text().isBlank()) {
        throw new IllegalArgumentException("method declaration source text is required");
      }
      controls = immutable(controls, "method code controls");
      exits = immutable(exits, "method code exits");
      for (int index = 0; index < controls.size(); index++) {
        Integer parent = controls.get(index).parentControlIndex();
        if (parent != null && (parent < 0 || parent >= index)) {
          throw new IllegalArgumentException(
              "control parent index must reference an earlier control");
        }
      }
    }

    /** Compatibility constructor for the pre-Task-1.2 typed test seam. */
    public MethodCode(
        String methodKey,
        String kind,
        String declaringType,
        String name,
        List<JavaDeclarationCatalog.ParameterView> parameters,
        String returnTypeText,
        SourceSource source,
        boolean bodyPresent) {
      this(
          methodKey,
          kind,
          declaringType,
          name,
          Objects.requireNonNull(source, "method code source").text(),
          null,
          parameters,
          returnTypeText,
          List.of(),
          List.of(),
          source,
          bodyPresent,
          List.of(),
          List.of());
    }
  }

  /** A control-flow syntax marker; it is a navigation hint, not a proof of execution. */
  public record Control(
      String kind, String expression, SourceRange sourceRange, Integer parentControlIndex) {

    public Control {
      if (!Set.of("IF", "ELSE", "TRY", "CATCH", "FINALLY", "LOOP", "SWITCH", "SYNCHRONIZED")
          .contains(kind)) {
        throw new IllegalArgumentException("control kind is unsupported");
      }
      expression = expression == null ? null : expression.strip();
      sourceRange = Objects.requireNonNull(sourceRange, "control source range");
    }
  }

  /** A return or throw syntax marker; it is a source observation, not a runtime result. */
  public record Exit(String kind, String expression, SourceRange sourceRange) {

    public Exit {
      if (!Set.of("RETURN", "THROW").contains(kind)) {
        throw new IllegalArgumentException("exit kind is unsupported");
      }
      expression = expression == null ? null : expression.strip();
      sourceRange = Objects.requireNonNull(sourceRange, "exit source range");
    }
  }

  /** An individual physical source call occurrence, not a de-duplicated spelling. */
  public record CallSite(
      String callKey,
      String callerMethodKey,
      String kind,
      SourceRange site,
      SourceRange navigationSite,
      String expression,
      String receiverExpression,
      List<ActualArgument> actualArguments,
      List<Integer> enclosingControlIndexes,
      boolean deferred,
      List<CallTarget> targets,
      String resolution,
      String resolutionDetail) {

    public CallSite {
      callKey = required(callKey, "call key");
      callerMethodKey = required(callerMethodKey, "call caller method key");
      if (!Set.of(
              "METHOD",
              "SUPER_METHOD",
              "CONSTRUCTOR",
              "THIS_CONSTRUCTOR",
              "SUPER_CONSTRUCTOR",
              "METHOD_REFERENCE",
              "CONSTRUCTOR_REFERENCE")
          .contains(kind)) {
        throw new IllegalArgumentException("call kind is unsupported");
      }
      site = Objects.requireNonNull(site, "call site");
      expression = required(expression, "call expression");
      if (receiverExpression != null && receiverExpression.isBlank()) {
        throw new IllegalArgumentException("call receiver expression cannot be blank");
      }
      actualArguments = immutable(actualArguments, "call actual arguments");
      for (int index = 0; index < actualArguments.size(); index++) {
        if (actualArguments.get(index).ordinal() != index) {
          throw new IllegalArgumentException("call actual argument ordinals must be ordered");
        }
      }
      enclosingControlIndexes =
          immutable(enclosingControlIndexes, "call enclosing control indexes");
      if (enclosingControlIndexes.stream().anyMatch(index -> index < 0)
          || new LinkedHashSet<>(enclosingControlIndexes).size()
              != enclosingControlIndexes.size()) {
        throw new IllegalArgumentException(
            "call enclosing control indexes must be distinct and nonnegative");
      }
      targets = immutableDistinct(targets, "call targets", CallTarget::identity);
      if (!Set.of("LOCATED", "CANDIDATES", "UNRESOLVED").contains(resolution)) {
        throw new IllegalArgumentException("call resolution is unsupported");
      }
      if ("LOCATED".equals(resolution) && targets.size() != 1) {
        throw new IllegalArgumentException("located call resolution requires one target");
      }
      if (!"LOCATED".equals(resolution)
          && (resolutionDetail == null || resolutionDetail.isBlank())) {
        throw new IllegalArgumentException("non-located call resolution needs detail");
      }
      if ("UNRESOLVED".equals(resolution) && !targets.isEmpty()) {
        throw new IllegalArgumentException("unresolved call resolution cannot carry targets");
      }
    }

    /** Compatibility constructor for the pre-Task-1.2 typed test seam. */
    public CallSite(
        String callKey,
        String callerMethodKey,
        String kind,
        SourceRange site,
        SourceRange navigationSite,
        String expression,
        List<CallTarget> targets) {
      this(
          callKey,
          callerMethodKey,
          kind,
          site,
          navigationSite,
          expression,
          null,
          List.of(),
          List.of(),
          "METHOD_REFERENCE".equals(kind) || "CONSTRUCTOR_REFERENCE".equals(kind),
          targets,
          targets.size() == 1 ? "LOCATED" : targets.isEmpty() ? "UNRESOLVED" : "CANDIDATES",
          targets.size() == 1 ? null : "navigation has not produced one unique declaration");
    }
  }

  /** An ordered actual source expression, retained even when it repeats another argument. */
  public record ActualArgument(int ordinal, String expression) {

    public ActualArgument {
      if (ordinal < 0) {
        throw new IllegalArgumentException("actual argument ordinal cannot be negative");
      }
      expression = required(expression, "actual argument expression");
    }
  }

  /** A repository candidate or explicit external/unresolved call boundary. */
  public record CallTarget(
      String methodKey,
      List<String> roles,
      String displayName,
      List<String> navigationKinds,
      String expansion,
      String reason,
      List<ArgumentAssociation> argumentAssociations) {

    public CallTarget {
      roles = immutableDistinct(roles, "call target roles", value -> value);
      if (roles.isEmpty() || !Set.of("DECLARATION", "IMPLEMENTATION").containsAll(roles)) {
        throw new IllegalArgumentException("call target roles are unsupported or empty");
      }
      displayName = required(displayName, "call target display name");
      navigationKinds =
          immutableDistinct(navigationKinds, "call target navigation kinds", value -> value);
      if (navigationKinds.isEmpty()
          || !Set.of("CALL_HIERARCHY", "DEFINITION", "IMPLEMENTATION", "ENGINE_BINDING")
              .containsAll(navigationKinds)) {
        throw new IllegalArgumentException("call target navigation kinds are unsupported or empty");
      }
      if (!Set.of("BODY_INCLUDED", "DECLARATION_ONLY", "EXTERNAL", "UNRESOLVED", "NOT_EXPANDED")
          .contains(expansion)) {
        throw new IllegalArgumentException("call target expansion is unsupported");
      }
      if ("BODY_INCLUDED".equals(expansion) && reason != null) {
        throw new IllegalArgumentException("included call target must not carry a stop reason");
      }
      if (!"BODY_INCLUDED".equals(expansion) && (reason == null || reason.isBlank())) {
        throw new IllegalArgumentException("unexpanded call target needs a reason");
      }
      argumentAssociations = immutable(argumentAssociations, "call target argument associations");
    }

    /** Compatibility constructor for the pre-Task-1.2 typed test seam. */
    public CallTarget(String methodKey, List<String> roles, String expansion, String reason) {
      this(
          methodKey,
          roles,
          methodKey == null ? "external target" : methodKey,
          List.of("ENGINE_BINDING"),
          expansion,
          reason,
          List.of());
    }

    private String identity() {
      return methodKey == null ? displayName + "|" + expansion : methodKey;
    }
  }

  /** An explicit actual-to-formal correspondence; it is not an interprocedural data-flow proof. */
  public record ArgumentAssociation(
      List<Integer> actualOrdinals, Integer formalOrdinal, String kind) {

    public ArgumentAssociation {
      actualOrdinals = immutable(actualOrdinals, "argument association actual ordinals");
      if (actualOrdinals.stream().anyMatch(ordinal -> ordinal < 0)
          || new LinkedHashSet<>(actualOrdinals).size() != actualOrdinals.size()) {
        throw new IllegalArgumentException(
            "argument association actual ordinals must be distinct and nonnegative");
      }
      if (formalOrdinal != null && formalOrdinal < 0) {
        throw new IllegalArgumentException(
            "argument association formal ordinal cannot be negative");
      }
      if (!Set.of("POSITIONAL", "VARARGS", "UNKNOWN").contains(kind)) {
        throw new IllegalArgumentException("argument association kind is unsupported");
      }
    }
  }

  /** A supporting frozen source fragment such as configuration, field, or mapper declaration. */
  public record SupportingSource(
      String kind, SourceSource source, List<String> relatedMethodKeys, String reason) {

    public SupportingSource {
      kind = required(kind, "supporting source kind");
      source = Objects.requireNonNull(source, "supporting source");
      relatedMethodKeys =
          immutableDistinct(relatedMethodKeys, "supporting source method keys", value -> value);
      reason = required(reason, "supporting source reason");
    }
  }

  /** A bounded, object-linked limitation rather than an invented empty success. */
  public record Limitation(
      String code, String detail, List<String> methodKeys, List<String> callKeys) {

    public Limitation {
      code = required(code, "limitation code");
      detail = required(detail, "limitation detail");
      methodKeys = immutableDistinct(methodKeys, "limitation method keys", value -> value);
      callKeys = immutableDistinct(callKeys, "limitation call keys", value -> value);
    }
  }

  /** Source text accompanied by its snapshot-relative location. */
  public record SourceSource(
      String path, int startLine, int endLine, int startOffsetUtf16, int lengthUtf16, String text) {

    public SourceSource {
      path = relativePath(path, "source path");
      new SourceRange(startOffsetUtf16, lengthUtf16, startLine, endLine);
      text = Objects.requireNonNull(text, "source text");
    }

    /** Compatibility constructor retaining the previous explicit range input. */
    public SourceSource(String path, SourceRange range, String text) {
      this(
          path,
          Objects.requireNonNull(range, "source range").startLine(),
          range.endLine(),
          range.startOffsetUtf16(),
          range.lengthUtf16(),
          text);
    }
  }

  /** Strict graph/fact enrichment is separately available or explicitly not produced. */
  public record TechnicalEnhancements(
      Availability availability,
      String reason,
      List<String> graphRefs,
      List<String> factRefs,
      String flowRef) {

    public TechnicalEnhancements {
      availability = Objects.requireNonNull(availability, "technical enhancement availability");
      graphRefs =
          immutableDistinct(graphRefs, "technical enhancement graph references", value -> value);
      factRefs =
          immutableDistinct(factRefs, "technical enhancement fact references", value -> value);
      if (flowRef != null && flowRef.isBlank()) {
        throw new IllegalArgumentException("technical enhancement flow reference cannot be blank");
      }
      if (availability == Availability.NOT_PRODUCED) {
        reason = required(reason, "not-produced technical enhancement reason");
        if (!graphRefs.isEmpty() || !factRefs.isEmpty() || flowRef != null) {
          throw new IllegalArgumentException(
              "not-produced enhancements cannot carry strict references");
        }
      } else {
        if (reason != null) {
          throw new IllegalArgumentException("available enhancements cannot carry a reason");
        }
        if (graphRefs.isEmpty() && factRefs.isEmpty() && flowRef == null) {
          throw new IllegalArgumentException(
              "available enhancements require a real same-snapshot reference");
        }
      }
    }
  }

  public enum Availability {
    AVAILABLE,
    NOT_PRODUCED
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }

  private static String relativePath(String value, String label) {
    String path = required(value, label);
    if (path.startsWith("/") || path.contains("..")) {
      throw new IllegalArgumentException(label + " must be snapshot relative");
    }
    return path;
  }

  private static <T> List<T> immutable(List<T> values, String label) {
    List<T> copied = List.copyOf(Objects.requireNonNull(values, label));
    if (copied.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(label + " cannot contain null values");
    }
    return copied;
  }

  private static <T> List<T> immutableDistinct(
      List<T> values, String label, java.util.function.Function<T, String> key) {
    List<T> copied = immutable(values, label);
    Set<String> keys = new LinkedHashSet<>();
    for (T value : copied) {
      String identity = required(key.apply(value), label + " identity");
      if (!keys.add(identity)) {
        throw new IllegalArgumentException(label + " cannot contain duplicate identities");
      }
    }
    return copied;
  }

  private static void requireElementsOfType(List<?> values, Class<?> type, String label) {
    if (values.stream().anyMatch(value -> !type.isInstance(value))) {
      throw new IllegalArgumentException(label + " must contain typed records");
    }
  }
}
