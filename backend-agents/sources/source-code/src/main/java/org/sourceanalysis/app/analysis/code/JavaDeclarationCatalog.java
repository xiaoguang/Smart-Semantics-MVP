package org.sourceanalysis.app.analysis.code;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The neutral, snapshot-bound declaration catalog returned by a selected Java engine. */
public record JavaDeclarationCatalog(
    String snapshotId,
    List<String> files,
    List<TypeDeclaration> types,
    List<MethodDeclarationView> methods,
    List<AnnotationView> annotations,
    List<FieldDeclarationView> fields,
    Map<String, String> fileDiagnostics) {

  public JavaDeclarationCatalog {
    snapshotId = required(snapshotId, "catalog snapshot identity");
    files = immutable(files, "catalog files");
    types = immutable(types, "catalog types");
    methods = immutable(methods, "catalog methods");
    annotations = immutable(annotations, "catalog annotations");
    fields = immutable(fields, "catalog fields");
    fileDiagnostics = Map.copyOf(Objects.requireNonNull(fileDiagnostics, "catalog diagnostics"));
    if (fileDiagnostics.keySet().stream().anyMatch(key -> key == null || key.isBlank())) {
      throw new IllegalArgumentException("catalog diagnostic keys must be nonblank");
    }
  }

  /** A source-visible type without guessed binding identity. */
  public record TypeDeclaration(
      String sourcePath,
      SourceRange sourceRange,
      String qualifiedName,
      String kind,
      List<String> annotationKeys,
      List<String> fieldKeys,
      List<String> methodKeys,
      List<String> supertypeTexts) {

    public TypeDeclaration {
      sourcePath = relativePath(sourcePath, "type source path");
      sourceRange = Objects.requireNonNull(sourceRange, "type source range");
      kind = required(kind, "type kind");
      annotationKeys = immutable(annotationKeys, "type annotation keys");
      fieldKeys = immutable(fieldKeys, "type field keys");
      methodKeys = immutable(methodKeys, "type method keys");
      supertypeTexts = immutable(supertypeTexts, "type supertypes");
    }
  }

  /** A stable, engine-neutral method declaration view used to form entry seeds. */
  public record MethodDeclarationView(
      String methodKey,
      String declaringType,
      String name,
      String kind,
      List<String> modifiers,
      List<ParameterView> parameters,
      String returnTypeText,
      List<String> annotationKeys,
      String sourcePath,
      SourceRange sourceRange,
      boolean hasBody) {

    public MethodDeclarationView {
      methodKey = required(methodKey, "method key");
      kind = required(kind, "method kind");
      modifiers = immutable(modifiers, "method modifiers");
      parameters = immutable(parameters, "method parameters");
      annotationKeys = immutable(annotationKeys, "method annotation keys");
      sourcePath = relativePath(sourcePath, "method source path");
      sourceRange = Objects.requireNonNull(sourceRange, "method source range");
    }

    /** Compatibility constructor for callers that predate source modifiers. */
    public MethodDeclarationView(
        String methodKey,
        String declaringType,
        String name,
        String kind,
        List<ParameterView> parameters,
        String returnTypeText,
        List<String> annotationKeys,
        String sourcePath,
        SourceRange sourceRange,
        boolean hasBody) {
      this(
          methodKey,
          declaringType,
          name,
          kind,
          List.of(),
          parameters,
          returnTypeText,
          annotationKeys,
          sourcePath,
          sourceRange,
          hasBody);
    }
  }

  /** A source annotation with optional tool-confirmed identity. */
  public record AnnotationView(
      String annotationKey,
      String nameText,
      String qualifiedName,
      String memberSource,
      SourceRange sourceRange,
      SourceRange nameSelection,
      Map<String, Object> staticValues,
      String sourcePath) {

    public AnnotationView {
      annotationKey = required(annotationKey, "annotation key");
      nameText = required(nameText, "annotation name");
      sourceRange = Objects.requireNonNull(sourceRange, "annotation source range");
      nameSelection = Objects.requireNonNull(nameSelection, "annotation name selection");
      staticValues = Map.copyOf(Objects.requireNonNull(staticValues, "annotation static values"));
      if (sourcePath != null) {
        sourcePath = relativePath(sourcePath, "annotation source path");
      }
    }

    /** Compatibility constructor for callers that predate static values and source path fields. */
    public AnnotationView(
        String annotationKey,
        String nameText,
        String qualifiedName,
        String memberSource,
        SourceRange sourceRange,
        SourceRange nameSelection) {
      this(
          annotationKey,
          nameText,
          qualifiedName,
          memberSource,
          sourceRange,
          nameSelection,
          Map.of(),
          null);
    }
  }

  /** A source-visible field declaration; its initializer remains raw source text when present. */
  public record FieldDeclarationView(
      String fieldKey,
      String name,
      String typeText,
      List<String> annotationKeys,
      String initializerText,
      String sourcePath,
      SourceRange sourceRange) {

    public FieldDeclarationView {
      fieldKey = required(fieldKey, "field key");
      name = required(name, "field name");
      typeText = required(typeText, "field type");
      annotationKeys = immutable(annotationKeys, "field annotation keys");
      sourcePath = relativePath(sourcePath, "field source path");
      sourceRange = Objects.requireNonNull(sourceRange, "field source range");
    }
  }

  /** An ordered source parameter; duplicate names or types are intentionally not collapsed. */
  public record ParameterView(
      int ordinal, String name, String typeText, boolean varArgs, List<String> annotationTexts) {

    public ParameterView {
      if (ordinal < 0) {
        throw new IllegalArgumentException("parameter ordinal cannot be negative");
      }
      name = required(name, "parameter name");
      typeText = required(typeText, "parameter type");
      annotationTexts = immutable(annotationTexts, "parameter annotation texts");
    }
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
}
