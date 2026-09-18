package org.sourceanalysis.app.analysis.persistence;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;

/**
 * Immutable persistence reading material. Mapper resource bytes are retained once in {@link
 * Resource}; statements point to that resource and retain a structured XML subtree instead of a
 * flattened dynamic-SQL interpretation.
 */
public record PersistenceMaterialIndex(
    Header header,
    List<Resource> resources,
    List<Statement> statements,
    List<JavaBinding> bindings,
    List<SqlAnalysis> sqlAnalyses,
    List<Diagnostic> diagnostics) {

  public PersistenceMaterialIndex {
    header = Objects.requireNonNull(header, "persistence index header");
    resources = immutable(resources, "persistence resources");
    statements = immutable(statements, "persistence statements");
    bindings = immutable(bindings, "persistence bindings");
    sqlAnalyses = immutable(sqlAnalyses, "SQL analyses");
    diagnostics = immutable(diagnostics, "persistence diagnostics");
    if (header.status() == Status.DISABLED
        && (!resources.isEmpty()
            || !statements.isEmpty()
            || !bindings.isEmpty()
            || !sqlAnalyses.isEmpty()
            || !diagnostics.isEmpty())) {
      throw new IllegalArgumentException("disabled persistence analysis may contain only a header");
    }
  }

  private static <T> List<T> immutable(List<T> values, String label) {
    List<T> copy = List.copyOf(Objects.requireNonNull(values, label));
    if (copy.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(label + " cannot contain null values");
    }
    return copy;
  }

  /** Metadata for this one same-snapshot analysis pass. */
  public record Header(
      Status status,
      String sourceSnapshotId,
      ProgramGraphsReference navigationPublication,
      List<Tool> tools) {

    public Header {
      status = Objects.requireNonNull(status, "persistence analysis status");
      if (sourceSnapshotId == null || !sourceSnapshotId.matches("snapshot:[0-9a-f]{64}")) {
        throw new IllegalArgumentException("persistence source snapshot must be canonical");
      }
      navigationPublication =
          Objects.requireNonNull(navigationPublication, "navigation publication");
      tools = immutable(tools, "persistence tools");
      if (status == Status.DISABLED && !tools.isEmpty()) {
        throw new IllegalArgumentException("disabled persistence analysis cannot load tools");
      }
    }
  }

  public enum Status {
    ENABLED,
    DISABLED
  }

  /** A tool actually used for an enabled pass; no third-party object is persisted. */
  public record Tool(String name, String version) {

    public Tool {
      if (name == null || name.isBlank() || version == null || version.isBlank()) {
        throw new IllegalArgumentException("persistence tool identity is required");
      }
    }
  }

  /** One frozen mapper XML resource, including its raw source and static resource edges once. */
  public record Resource(
      String resourcePath,
      String namespace,
      String rawSource,
      List<String> dependencyResourcePaths) {

    public Resource {
      required(resourcePath, "Mapper resource path");
      required(namespace, "Mapper namespace");
      rawSource = Objects.requireNonNull(rawSource, "Mapper raw source");
      dependencyResourcePaths = immutable(dependencyResourcePaths, "Mapper resource dependencies");
      dependencyResourcePaths.forEach(path -> required(path, "Mapper resource dependency path"));
      if (new LinkedHashSet<>(dependencyResourcePaths).size() != dependencyResourcePaths.size()) {
        throw new IllegalArgumentException("Mapper resource dependency paths must be unique");
      }
    }
  }

  /**
   * One XML statement variant. {@code xmlSubtree} preserves the statement root and its elements,
   * text, and CDATA in source order; it is a structural projection, not another raw-source copy or
   * a runtime SQL plan.
   */
  public record Statement(
      String statementRef,
      String resourceRef,
      String namespace,
      String statementId,
      String statementKind,
      String databaseId,
      XmlNode xmlSubtree,
      List<DependencyRef> dependencyRefs) {

    public Statement {
      required(statementRef, "Statement reference");
      required(resourceRef, "Statement resource reference");
      required(namespace, "Statement namespace");
      required(statementId, "Statement ID");
      required(statementKind, "Statement XML kind");
      xmlSubtree = Objects.requireNonNull(xmlSubtree, "Statement XML subtree");
      dependencyRefs = immutable(dependencyRefs, "Statement dependency references");
    }
  }

  /** A source-order-preserving DOM projection for a statement or included fragment. */
  public record XmlNode(
      XmlNodeKind kind,
      String elementName,
      Map<String, String> attributes,
      String content,
      List<XmlNode> children) {

    public XmlNode {
      kind = Objects.requireNonNull(kind, "XML node kind");
      attributes = Map.copyOf(Objects.requireNonNull(attributes, "XML node attributes"));
      children = immutable(children, "XML node children");
      if (kind == XmlNodeKind.ELEMENT) {
        required(elementName, "XML element name");
        if (content != null) {
          throw new IllegalArgumentException(
              "an XML element projection stores text as child nodes");
        }
      } else if (elementName != null
          || !attributes.isEmpty()
          || !children.isEmpty()
          || content == null) {
        throw new IllegalArgumentException("a non-element XML node must contain only text content");
      }
    }
  }

  public enum XmlNodeKind {
    ELEMENT,
    TEXT,
    CDATA,
    COMMENT
  }

  /** A statement-local dependency reference, retained even when it cannot be resolved safely. */
  public record DependencyRef(String kind, String reference, String resolution) {

    public DependencyRef {
      required(kind, "Dependency kind");
      required(reference, "Dependency reference");
      required(resolution, "Dependency resolution");
    }
  }

  /** A candidate Java mapper method paired only with matching XML statement variants. */
  public record JavaBinding(
      String javaInterfaceFqn,
      String methodKey,
      String methodSignature,
      String candidateNature,
      List<ParameterBinding> parameters,
      List<StatementRef> statementRefs,
      List<String> limitations) {

    public JavaBinding {
      required(javaInterfaceFqn, "Java mapper interface");
      required(methodKey, "Java mapper method key");
      required(methodSignature, "Java mapper method signature");
      required(candidateNature, "Java binding candidate nature");
      parameters = immutable(parameters, "Java binding parameters");
      statementRefs = immutable(statementRefs, "Java binding statement references");
      limitations = immutable(limitations, "Java binding limitations");
    }
  }

  /** One source-visible Java parameter and the XML placeholder paths that mention it. */
  public record ParameterBinding(
      int ordinal,
      String name,
      String typeText,
      List<String> annotationTexts,
      List<String> placeholderPaths,
      List<String> limitations) {

    public ParameterBinding {
      if (ordinal < 0) {
        throw new IllegalArgumentException("Java parameter ordinal cannot be negative");
      }
      required(name, "Java parameter name");
      required(typeText, "Java parameter type");
      annotationTexts = immutable(annotationTexts, "Java parameter annotations");
      placeholderPaths = immutable(placeholderPaths, "XML placeholder paths");
      limitations = immutable(limitations, "Java parameter limitations");
    }
  }

  /** A reference to one statement variant rather than a guessed runtime selection. */
  public record StatementRef(String statementRef, String databaseId) {

    public StatementRef {
      required(statementRef, "Statement reference");
    }
  }

  /** A parser-derived SQL projection that retains its analysis-copy transformations and status. */
  public record SqlAnalysis(
      String statementRef,
      String analysisCopy,
      List<String> transformations,
      SqlAstNode ast,
      SqlStatus status,
      String reason) {

    public SqlAnalysis {
      required(statementRef, "SQL analysis statement reference");
      transformations = immutable(transformations, "SQL analysis transformations");
      status = Objects.requireNonNull(status, "SQL analysis status");
      if (status == SqlStatus.PARSED && ast == null) {
        throw new IllegalArgumentException("a parsed SQL analysis requires an AST projection");
      }
      if (status != SqlStatus.PARSED && (reason == null || reason.isBlank())) {
        throw new IllegalArgumentException("a non-parsed SQL analysis requires a reason");
      }
    }
  }

  /** A neutral hierarchical AST projection; third-party parser types are never persisted. */
  public record SqlAstNode(
      String kind, String value, Map<String, String> attributes, List<SqlAstNode> children) {

    public SqlAstNode {
      required(kind, "SQL AST node kind");
      attributes = Map.copyOf(Objects.requireNonNull(attributes, "SQL AST node attributes"));
      children = immutable(children, "SQL AST node children");
    }
  }

  public enum SqlStatus {
    PARSED,
    PARTIAL,
    UNSUPPORTED
  }

  /** A resource, statement, binding, or SQL limitation retained for later reading. */
  public record Diagnostic(String code, String subjectRef, String detail) {

    public Diagnostic {
      required(code, "Persistence diagnostic code");
      required(subjectRef, "Persistence diagnostic subject");
      required(detail, "Persistence diagnostic detail");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
