package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;

/**
 * Projects explicitly selected, already verified R0 SQL files into the finite optional O0 DDL
 * evidence. It intentionally has no source discovery, statement recovery, or database behavior.
 */
public final class OntologySchemaEvidence {

  public static final String SCHEMA_VERSION = "schema-evidence-v1";
  private static final String PROJECTION_RULE_VERSION = "schema-evidence-projection-v1";

  private OntologySchemaEvidence() {}

  /**
   * Parses each configured verified-text file once as a complete JSqlParser input and records only
   * complete supported CREATE TABLE declarations. A malformed or mixed file remains a file-level
   * observation; it never contributes a model-readable source unit.
   */
  public static ObjectNode project(
      VerifiedSourceTextSet source, List<Path> configuredPaths, OntologyEvidenceCorpus corpus) {
    if (source == null || configuredPaths == null || corpus == null) {
      throw new IllegalArgumentException("ONTOLOGY_SCHEMA_SOURCE_INVALID");
    }
    Map<String, VerifiedSourceTextDocument> documents =
        configuredDocuments(source, configuredPaths);
    List<String> paths = configuredPaths.stream().map(Path::toString).sorted().distinct().toList();
    ObjectNode evidence = JsonNodeFactory.instance.objectNode();
    evidence.put("schemaVersion", SCHEMA_VERSION);
    ObjectNode parser = evidence.putObject("parser");
    parser.put("library", "JSqlParser");
    String version = CCJSqlParserUtil.class.getPackage().getImplementationVersion();
    parser.put("version", version == null || version.isBlank() ? "5.3" : version);
    parser.put("projectionRuleVersion", PROJECTION_RULE_VERSION);
    ArrayNode files = evidence.putArray("files");
    for (String path : paths) {
      VerifiedSourceTextDocument document = documents.get(path);
      if (document == null) {
        throw new IllegalArgumentException("ONTOLOGY_SCHEMA_SOURCE_INVALID");
      }
      files.add(projectFile(document, corpus));
    }
    return evidence;
  }

  /**
   * Performs only the O0 source-membership gate: every configured path must already be an admitted,
   * verified-text R0 member. Parsing deliberately remains in {@link #project} after the O0 run is
   * queued, so an unsupported complete file is saved as an honest report instead of being treated
   * as an input error.
   */
  public static void requireVerifiedTextMembers(
      VerifiedSourceTextSet source, List<Path> configuredPaths) {
    if (source == null || configuredPaths == null) {
      throw new IllegalArgumentException("ONTOLOGY_SCHEMA_SOURCE_INVALID");
    }
    configuredDocuments(source, configuredPaths);
  }

  private static Map<String, VerifiedSourceTextDocument> configuredDocuments(
      VerifiedSourceTextSet source, List<Path> configuredPaths) {
    Map<String, VerifiedSourceTextDocument> documents = new LinkedHashMap<>();
    source.documents().forEach(document -> documents.put(document.path(), document));
    for (Path configured : configuredPaths) {
      if (configured == null || !documents.containsKey(configured.toString())) {
        throw new IllegalArgumentException("ONTOLOGY_SCHEMA_SOURCE_INVALID");
      }
    }
    return Map.copyOf(documents);
  }

  private static ObjectNode projectFile(
      VerifiedSourceTextDocument document, OntologyEvidenceCorpus corpus) {
    String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    ObjectNode file = JsonNodeFactory.instance.objectNode();
    file.put("path", document.path());
    file.put("fileId", document.fileId().value());
    file.put("sha256", document.sha256().value());
    file.put("byteLength", document.sizeBytes());
    ObjectNode range = file.putObject("range");
    range.put("startOffsetUtf16", 0);
    range.put("lengthUtf16", source.length());
    ArrayNode declarations = file.putArray("declarations");
    ArrayNode diagnostics = file.putArray("diagnostics");
    ArrayNode limitations = file.putArray("limitations");
    limitations.add("FULL_FILE_RANGE_ONLY");
    ArrayNode entryUses = file.putArray("entryUses");
    try {
      Statements statements = CCJSqlParserUtil.parseStatements(source);
      List<Statement> parsed = statements.getStatements();
      if (parsed.isEmpty()
          || parsed.stream().anyMatch(statement -> !(statement instanceof CreateTable))) {
        file.put("parseStatus", "UNSUPPORTED");
        file.put("structuralCoverage", "UNKNOWN");
        file.put("modelEligible", false);
        limitations.add("COMPLETE_FILE_IS_NOT_SUPPORTED_CREATE_TABLE_ONLY");
        return file;
      }
      boolean complete = true;
      LinkedHashMap<String, OntologyEvidenceCorpus.SchemaEntryUse> uses = new LinkedHashMap<>();
      for (Statement statement : parsed) {
        CreateProjection projection = projectCreateTable((CreateTable) statement);
        if (!projection.complete()) {
          complete = false;
          continue;
        }
        declarations.add(projection.declaration());
        for (OntologyEvidenceCorpus.SchemaEntryUse use :
            corpus.schemaEntryUses(projection.tableName())) {
          uses.putIfAbsent(schemaEntryUseKey(use), use);
        }
      }
      if (!complete || declarations.isEmpty()) {
        declarations.removeAll();
        entryUses.removeAll();
        file.put("parseStatus", "UNSUPPORTED");
        file.put("structuralCoverage", "UNKNOWN");
        file.put("modelEligible", false);
        limitations.add("UNSUPPORTED_CREATE_TABLE_CONSTRAINT");
        return file;
      }
      uses.values().stream()
          .sorted(
              Comparator.comparing(OntologyEvidenceCorpus.SchemaEntryUse::entryId)
                  .thenComparing(OntologyEvidenceCorpus.SchemaEntryUse::sqlUnitId)
                  .thenComparing(OntologyEvidenceCorpus.SchemaEntryUse::matchedTableValue))
          .forEach(use -> entryUses.add(entryUse(use)));
      file.put("parseStatus", "PARSED");
      file.put("structuralCoverage", "COMPLETE");
      file.put("modelEligible", !uses.isEmpty());
      if (uses.isEmpty()) {
        limitations.add("NO_MATCHABLE_TABLE_OBSERVATION");
      } else {
        limitations.add("TABLE_MATCH_CANDIDATE");
      }
      return file;
    } catch (JSQLParserException | RuntimeException invalid) {
      file.put("parseStatus", "UNKNOWN");
      file.put("structuralCoverage", "UNKNOWN");
      file.put("modelEligible", false);
      ObjectNode diagnostic = diagnostics.addObject();
      diagnostic.put("code", "WHOLE_FILE_PARSE_FAILED");
      diagnostic.put("parserMessage", safeMessage(invalid));
      limitations.add("WHOLE_FILE_PARSE_FAILED");
      return file;
    }
  }

  private static CreateProjection projectCreateTable(CreateTable create) {
    String tableName = create.getTable() == null ? "" : create.getTable().getFullyQualifiedName();
    if (tableName == null || tableName.isBlank()) {
      return CreateProjection.unsupported();
    }
    ObjectNode declaration = JsonNodeFactory.instance.objectNode();
    declaration.put("tableName", tableName);
    declaration.put("evidenceNature", "DDL_DECLARED");
    ArrayNode columns = declaration.putArray("columns");
    List<ColumnDefinition> definitions = create.getColumnDefinitions();
    if (definitions == null || definitions.isEmpty()) {
      return CreateProjection.unsupported();
    }
    for (ColumnDefinition definition : definitions) {
      if (definition.getColumnName() == null
          || definition.getColumnName().isBlank()
          || definition.getColDataType() == null
          || hasUnsupportedInlineConstraint(definition)) {
        return CreateProjection.unsupported();
      }
      ObjectNode column = columns.addObject();
      column.put("name", definition.getColumnName());
      column.put("type", definition.getColDataType().toString());
      column.put("declaredNullability", nullability(definition));
    }
    ArrayNode constraints = declaration.putArray("constraints");
    List<Index> indexes = create.getIndexes();
    if (indexes != null) {
      for (Index index : indexes) {
        String kind = constraintKind(index);
        if (kind == null) {
          return CreateProjection.unsupported();
        }
        ObjectNode constraint = constraints.addObject();
        constraint.put("kind", kind);
        if (index.getName() == null || index.getName().isBlank()) {
          constraint.putNull("name");
        } else {
          constraint.put("name", index.getName());
        }
        ArrayNode columnsNode = constraint.putArray("columns");
        if (index.getColumnsNames() != null) {
          index.getColumnsNames().forEach(columnsNode::add);
        }
        if (index instanceof ForeignKeyIndex foreign) {
          constraint.put("referencedTable", foreign.getTable().getFullyQualifiedName());
          ArrayNode referencedColumns = constraint.putArray("referencedColumns");
          if (foreign.getReferencedColumnNames() != null) {
            foreign.getReferencedColumnNames().forEach(referencedColumns::add);
          }
        } else {
          constraint.putNull("referencedTable");
          constraint.putArray("referencedColumns");
        }
      }
    }
    return new CreateProjection(tableName, declaration, true);
  }

  private static String nullability(ColumnDefinition definition) {
    List<String> specifications = definition.getColumnSpecs();
    if (specifications == null) {
      return "UNKNOWN";
    }
    boolean nullable = false;
    for (int index = 0; index < specifications.size(); index++) {
      String token = normalizedSpec(specifications.get(index));
      if ("NOT NULL".equals(token)
          || ("NOT".equals(token)
              && index + 1 < specifications.size()
              && "NULL".equals(normalizedSpec(specifications.get(index + 1))))) {
        return "NOT_NULL";
      }
      if ("NULL".equals(token)) {
        nullable = true;
      }
    }
    return nullable ? "NULLABLE" : "UNKNOWN";
  }

  /**
   * ColumnDefinition exposes these clauses as parser tokens/specifications, but the first DDL
   * projection has no field for inline key/reference/check semantics. Refusing the complete file is
   * more truthful than emitting COMPLETE while silently dropping one. Exact tokens avoid mistaking
   * quoted DEFAULT text (for example {@code 'NOT NULL'}) for a declaration.
   */
  private static boolean hasUnsupportedInlineConstraint(ColumnDefinition definition) {
    List<String> specifications = definition.getColumnSpecs();
    if (specifications == null) {
      return false;
    }
    for (String specification : specifications) {
      String token = normalizedSpec(specification);
      if ("PRIMARY".equals(token)
          || token.startsWith("PRIMARY ")
          || "UNIQUE".equals(token)
          || token.startsWith("UNIQUE ")
          || "FOREIGN".equals(token)
          || token.startsWith("FOREIGN ")
          || "REFERENCES".equals(token)
          || token.startsWith("REFERENCES ")
          || "CHECK".equals(token)
          || token.startsWith("CHECK ")) {
        return true;
      }
    }
    return false;
  }

  private static String normalizedSpec(String specification) {
    return specification == null ? "" : specification.trim().toUpperCase(Locale.ROOT);
  }

  private static String constraintKind(Index index) {
    if (index instanceof ForeignKeyIndex) {
      return "FOREIGN_KEY";
    }
    String type = index.getType();
    if (type == null) {
      return null;
    }
    return switch (type.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim()) {
      case "PRIMARY KEY" -> "PRIMARY_KEY";
      case "UNIQUE", "UNIQUE KEY" -> "UNIQUE";
      case "INDEX", "KEY" -> "INDEX";
      default -> null;
    };
  }

  private static ObjectNode entryUse(OntologyEvidenceCorpus.SchemaEntryUse use) {
    ObjectNode entryUse = JsonNodeFactory.instance.objectNode();
    entryUse.put("entryId", use.entryId());
    entryUse.put("matchedTableValue", use.matchedTableValue());
    entryUse.put("sqlUnitId", use.sqlUnitId());
    entryUse.put("sqlStatus", use.sqlStatus());
    entryUse.put("associationStatus", "TABLE_MATCH_CANDIDATE");
    return entryUse;
  }

  private static String schemaEntryUseKey(OntologyEvidenceCorpus.SchemaEntryUse use) {
    return use.entryId() + "\n" + use.sqlUnitId() + "\n" + use.matchedTableValue();
  }

  private static String safeMessage(Exception failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
  }

  private record CreateProjection(String tableName, ObjectNode declaration, boolean complete) {
    private static CreateProjection unsupported() {
      return new CreateProjection("", JsonNodeFactory.instance.objectNode(), false);
    }
  }
}
