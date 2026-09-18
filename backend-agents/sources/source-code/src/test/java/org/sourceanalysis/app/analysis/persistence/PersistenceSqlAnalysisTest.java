package org.sourceanalysis.app.analysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.discovery.MapperMethodCandidate;
import org.sourceanalysis.app.analysis.discovery.MapperStatementCandidate;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** RED contract for SQL projection and persistence-source retention at the analyzer seam. */
class PersistenceSqlAnalysisTest {

  private static final String SNAPSHOT = "snapshot:" + "a".repeat(64);
  private static final String NAMESPACE = "example.persistence.NeutralMapper";
  private static final String XML_PATH = "src/main/resources/mapper/NeutralMapper.xml";
  private static final String JAVA_PATH = "src/main/java/example/persistence/NeutralMapper.java";

  @Test
  void parsesStaticAggregateAndLeftJoinWithDistinctOnAndWhereHierarchy() {
    String xml =
        mapper(
            "<select id=\"aggregate\">\n"
                + "  SELECT SUM(o.amount) AS total\n"
                + "  FROM neutral_orders o\n"
                + "  LEFT JOIN neutral_lines l ON l.order_id = o.id\n"
                + "  WHERE o.status = #{status}\n"
                + "</select>\n");
    PersistenceMaterialIndex result =
        analyze(xml, List.of(method("aggregate", "method:aggregate", parameter("status"))));

    assertThat(result.sqlAnalyses()).hasSize(1);
    PersistenceMaterialIndex.SqlAnalysis analysis = result.sqlAnalyses().get(0);
    assertThat(analysis.status()).isEqualTo(PersistenceMaterialIndex.SqlStatus.PARSED);
    assertThat(analysis.ast().kind()).isEqualTo("SELECT");
    assertThat(analysis.analysisCopy()).contains("SUM(o.amount)");

    PersistenceMaterialIndex.SqlAstNode aggregate = firstKind(analysis.ast(), "AGGREGATE");
    assertThat(flattenAstValues(aggregate)).containsIgnoringCase("SUM");
    PersistenceMaterialIndex.SqlAstNode join = firstKind(analysis.ast(), "JOIN");
    assertThat(flattenAstValues(firstKind(join, "ON"))).contains("order_id");
    PersistenceMaterialIndex.SqlAstNode where = firstKind(analysis.ast(), "WHERE");
    assertThat(flattenAstValues(where)).contains("status");
    assertThat(containsKind(where, "ON"))
        .as("JOIN ON remains a join child, not flattened into WHERE")
        .isFalse();
  }

  @Test
  void keepsDynamicConditionAsPartialOrderedXmlAndRetainsAliasParameterOriginals() {
    String xml =
        mapper(
            "<select id=\"dynamic\">\n"
                + "  SELECT id, label FROM neutral_records\n"
                + "  <where><if test=\"status != null\"><![CDATA[AND status = #{status}]]></if></where>\n"
                + "</select>\n");
    PersistenceMaterialIndex result =
        analyze(
            xml,
            List.of(
                method(
                    "dynamic",
                    "method:dynamic",
                    new ParameterSpec(
                        "status", "java.lang.String", List.of("@Param(\"status\")")))));

    assertThat(result.resources())
        .singleElement()
        .satisfies(resource -> assertThat(resource.rawSource()).isEqualTo(xml));
    assertThat(result.statements())
        .singleElement()
        .satisfies(
            statement -> {
              assertThat(hasElement(statement.xmlSubtree(), "where")).isTrue();
              assertThat(hasElement(statement.xmlSubtree(), "if")).isTrue();
              assertThat(
                      hasKind(statement.xmlSubtree(), PersistenceMaterialIndex.XmlNodeKind.CDATA))
                  .isTrue();
              assertThat(flattenContent(statement.xmlSubtree())).contains("#{status}");
            });
    assertThat(result.sqlAnalyses())
        .singleElement()
        .satisfies(
            analysis -> {
              assertThat(analysis.status()).isEqualTo(PersistenceMaterialIndex.SqlStatus.PARTIAL);
              assertThat(analysis.reason()).isNotBlank();
            });

    assertThat(result.bindings())
        .singleElement()
        .satisfies(
            binding -> {
              assertThat(binding.methodKey()).isEqualTo("method:dynamic");
              assertThat(binding.parameters())
                  .singleElement()
                  .satisfies(
                      parameter -> {
                        assertThat(parameter.name()).isEqualTo("status");
                        assertThat(parameter.annotationTexts()).contains("@Param(\"status\")");
                        assertThat(parameter.placeholderPaths()).contains("status");
                      });
            });
  }

  @Test
  void projectsCompleteDynamicWhereFragmentAlongsideTheStaticPartialAst() {
    String xml =
        mapper(
            "<select id=\"dynamicWhere\">\n"
                + "  SELECT id FROM neutral_records WHERE active = 1\n"
                + "  <if test=\"status != null\">AND status = #{status}</if>\n"
                + "</select>\n");
    PersistenceMaterialIndex result =
        analyze(xml, List.of(method("dynamicWhere", "method:dynamic-where", parameter("status"))));

    assertThat(result.sqlAnalyses())
        .singleElement()
        .satisfies(
            analysis -> {
              assertThat(analysis.status()).isEqualTo(PersistenceMaterialIndex.SqlStatus.PARTIAL);
              assertThat(analysis.ast()).isNotNull();
              assertThat(hasAstNodeWithContent(analysis.ast(), "status != null", "status ="))
                  .as("the partial AST retains the dynamic condition and its complete SQL fragment")
                  .isTrue();
            });
  }

  @Test
  void doesNotInferAliasFromAnAnnotationThatIsNotParam() {
    String xml =
        mapper(
            "<select id=\"notParam\">SELECT id FROM neutral_records WHERE label = #{alias}</select>\n");
    PersistenceMaterialIndex result =
        analyze(
            xml,
            List.of(
                method(
                    "notParam",
                    "method:not-param",
                    new ParameterSpec(
                        "status", "java.lang.String", List.of("@NotParam(\"alias\")")))));

    assertThat(result.bindings())
        .singleElement()
        .satisfies(
            binding -> {
              assertThat(binding.parameters())
                  .singleElement()
                  .satisfies(
                      parameter -> {
                        assertThat(parameter.annotationTexts()).contains("@NotParam(\"alias\")");
                        assertThat(parameter.placeholderPaths()).doesNotContain("alias");
                      });
            });
  }

  @Test
  void retainsIncludeDependencyAndFragmentConditionPlaceholderWithoutFlatteningOriginalXml() {
    String xml =
        mapper(
            "<sql id=\"basePredicate\"><if test=\"tenantId != null\"><![CDATA[AND tenant_id = #{tenantId}]]></if></sql>\n"
                + "<select id=\"withInclude\">\n"
                + "  SELECT id FROM neutral_records <where><include refid=\"basePredicate\"/></where>\n"
                + "</select>\n");
    PersistenceMaterialIndex result =
        analyze(xml, List.of(method("withInclude", "method:with-include", parameter("tenantId"))));

    assertThat(result.resources())
        .singleElement()
        .satisfies(resource -> assertThat(resource.rawSource()).isEqualTo(xml));
    assertThat(result.statements())
        .singleElement()
        .satisfies(
            statement -> {
              assertThat(statement.dependencyRefs())
                  .extracting(PersistenceMaterialIndex.DependencyRef::reference)
                  .contains("basePredicate");
              assertThat(hasElement(statement.xmlSubtree(), "include")).isTrue();
              assertThat(firstElement(statement.xmlSubtree(), "include").attributes())
                  .containsEntry("refid", "basePredicate");
            });
    assertThat(result.resources().get(0).rawSource())
        .contains("<if test=\"tenantId != null\">")
        .contains("#{tenantId}");
  }

  @Test
  void rejectsExternalEntityWithoutReadingFrozenTempSentinel(@TempDir Path temporary)
      throws IOException {
    Path sentinel = temporary.resolve("external-entity-sentinel.txt");
    Files.writeString(sentinel, "DO_NOT_READ_EXTERNAL_SENTINEL", StandardCharsets.UTF_8);
    String xml =
        "<?xml version=\"1.0\"?>\n"
            + "<!DOCTYPE mapper [ <!ENTITY sentinel SYSTEM \""
            + sentinel.toUri()
            + "\"> ]>\n"
            + "<mapper namespace=\""
            + NAMESPACE
            + "\"><select id=\"unsafe\">SELECT '&sentinel;'</select></mapper>\n";
    PersistenceMaterialIndex result =
        Assertions.assertDoesNotThrow(
            () -> analyze(xml, List.of(method("unsafe", "method:unsafe", parameter("value")))));

    assertThat(result.resources()).isEmpty();
    assertThat(result.diagnostics())
        .extracting(PersistenceMaterialIndex.Diagnostic::code)
        .contains("XML_SECURITY_REJECTED");
    assertThat(result.diagnostics())
        .extracting(PersistenceMaterialIndex.Diagnostic::detail)
        .allSatisfy(detail -> assertThat(detail).doesNotContain("DO_NOT_READ_EXTERNAL_SENTINEL"));
  }

  @Test
  void staticInsertUpdateAndSubqueryEitherProjectRootAstOrReportExplicitUnsupported() {
    String xml =
        mapper(
            "<insert id=\"insertRow\">INSERT INTO neutral_records (id, label) VALUES (#{id}, #{label})</insert>\n"
                + "<update id=\"updateRow\">UPDATE neutral_records SET label = #{label} WHERE id = #{id}</update>\n"
                + "<select id=\"nested\">SELECT id FROM (SELECT id FROM neutral_records) nested_records WHERE id = #{id}</select>\n");
    PersistenceMaterialIndex result =
        analyze(
            xml,
            List.of(
                method("insertRow", "method:insert", parameter("id"), parameter("label")),
                method("updateRow", "method:update", parameter("id"), parameter("label")),
                method("nested", "method:nested", parameter("id"))));

    assertThat(result.sqlAnalyses()).hasSize(3);
    List<String> expectedRoots = List.of("INSERT", "UPDATE", "SELECT");
    for (int index = 0; index < result.sqlAnalyses().size(); index++) {
      PersistenceMaterialIndex.SqlAnalysis analysis = result.sqlAnalyses().get(index);
      assertThat(analysis.status())
          .as("ordinary static %s SQL should have a structured AST", expectedRoots.get(index))
          .isEqualTo(PersistenceMaterialIndex.SqlStatus.PARSED);
      assertThat(analysis.ast().kind()).isEqualTo(expectedRoots.get(index));
      if (index == 2) {
        assertThat(
                containsKind(analysis.ast(), "SUBQUERY") || countKind(analysis.ast(), "SELECT") > 1)
            .as("subquery remains nested below the SELECT root")
            .isTrue();
      }
    }
  }

  @Test
  void staticInsertSelectDoesNotCrashAndRetainsSelectSourceHierarchy() {
    String xml =
        mapper(
            "<insert id=\"insertFromSelect\">"
                + "INSERT INTO neutral_archive (id, label) "
                + "SELECT id, label FROM neutral_records WHERE tenant_id = #{tenantId}"
                + "</insert>\n");

    PersistenceMaterialIndex result =
        Assertions.assertDoesNotThrow(
            () ->
                analyze(
                    xml,
                    List.of(
                        method(
                            "insertFromSelect",
                            "method:insert-from-select",
                            parameter("tenantId")))));

    assertThat(result.sqlAnalyses())
        .singleElement()
        .satisfies(
            analysis -> {
              assertThat(analysis.status()).isEqualTo(PersistenceMaterialIndex.SqlStatus.PARSED);
              assertThat(analysis.ast().kind()).isEqualTo("INSERT");
              PersistenceMaterialIndex.SqlAstNode source =
                  firstKind(analysis.ast(), "SELECT_SOURCE");
              assertThat(firstKind(source, "SELECT")).isNotNull();
              assertThat(flattenAstValues(firstKind(source, "WHERE"))).contains("tenant_id");
            });
  }

  @Test
  void unionAllRetainsBothBranchesAndOperationMetadataOrReportsAnExplicitLimitation() {
    String xml =
        mapper(
            "<select id=\"unionAll\">"
                + "SELECT id FROM neutral_records WHERE status = #{status} "
                + "UNION ALL "
                + "SELECT id FROM neutral_archive WHERE status = #{status}"
                + "</select>\n");

    PersistenceMaterialIndex result =
        Assertions.assertDoesNotThrow(
            () ->
                analyze(xml, List.of(method("unionAll", "method:union-all", parameter("status")))));

    PersistenceMaterialIndex.SqlAnalysis analysis = result.sqlAnalyses().get(0);
    assertThat(analysis.status()).isNotNull();
    if (analysis.status() == PersistenceMaterialIndex.SqlStatus.PARSED) {
      assertThat(analysis.ast().kind()).isEqualTo("SET_OPERATION");
      assertThat(countKind(analysis.ast(), "SELECT"))
          .as("a parsed UNION ALL must retain both query branches")
          .isEqualTo(2);
      assertThat(flattenAstMetadata(analysis.ast()))
          .as("a parsed set operation must retain UNION ALL metadata")
          .containsIgnoringCase("UNION")
          .containsIgnoringCase("ALL");
    } else {
      assertThat(analysis.reason())
          .as("an incomplete set-operation projection must be explicit")
          .isNotBlank();
    }
  }

  private static PersistenceMaterialIndex analyze(String xml, List<MethodSpec> methods) {
    String javaSource = javaSource(methods);
    VerifiedSourceTextSet source = sourceSet(Map.of(XML_PATH, xml, JAVA_PATH, javaSource));
    List<MapperMethodCandidate> methodCandidates =
        methods.stream()
            .map(
                method ->
                    new MapperMethodCandidate(
                        id("mapper-method", method.methodKey()),
                        method.signature(),
                        excerpt(JAVA_PATH, javaSource, method.name() + "(")))
            .toList();
    List<String> statementIds = statementIds(xml);
    List<MapperStatementCandidate> statementCandidates =
        statementIds.stream()
            .map(
                id ->
                    new MapperStatementCandidate(
                        id("mapper-statement", id),
                        id,
                        statementKind(xml, id),
                        excerpt(XML_PATH, xml, "id=\"" + id + "\"")))
            .toList();
    MapperCatalogEntry catalogEntry =
        new MapperCatalogEntry(
            id("mapper-catalog-entry", "sql-analysis"),
            NAMESPACE,
            methodCandidates,
            XML_PATH,
            NAMESPACE,
            statementCandidates,
            "CANDIDATE_NOT_YET_BOUND");
    PersistenceAnalysisRequest request =
        new PersistenceAnalysisRequest(
            javaCodeIndex(source.verifiedSnapshotRef(), methods),
            navigationPublication(),
            source,
            List.of(catalogEntry),
            new PersistenceConfiguration(
                List.of(new PersistenceConfiguration.Plugin("mybatis", "jsqlparser"))));
    return new DefaultPersistenceAnalyzer().analyze(request);
  }

  private static String mapper(String statements) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<mapper namespace=\""
        + NAMESPACE
        + "\">\n"
        + statements
        + "</mapper>\n";
  }

  private static String javaSource(List<MethodSpec> methods) {
    StringBuilder source =
        new StringBuilder("package example.persistence;\ninterface NeutralMapper {\n");
    for (MethodSpec method : methods) {
      source.append("  Record ").append(method.name()).append("(");
      for (int index = 0; index < method.parameters().size(); index++) {
        if (index > 0) source.append(", ");
        ParameterSpec parameter = method.parameters().get(index);
        for (String annotation : parameter.annotations()) source.append(annotation).append(' ');
        source.append(parameter.typeText()).append(' ').append(parameter.name());
      }
      source.append(");\n");
    }
    return source.append("}\n").toString();
  }

  private static List<String> statementIds(String xml) {
    return extractIds(xml);
  }

  private static List<String> extractIds(String xml) {
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("<(?:select|insert|update|delete)\\s+id=\\\"([^\"]+)\\\"")
            .matcher(xml);
    java.util.ArrayList<String> ids = new java.util.ArrayList<>();
    while (matcher.find()) ids.add(matcher.group(1));
    return ids;
  }

  private static String statementKind(String xml, String id) {
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("<(select|insert|update|delete)\\s+id=\\\"" + id + "\\\"")
            .matcher(xml);
    return matcher.find() ? matcher.group(1) : "select";
  }

  private static MethodSpec method(String name, String methodKey, ParameterSpec... parameters) {
    List<ParameterSpec> parameterList = List.of(parameters);
    String signature =
        name
            + "("
            + parameterList.stream()
                .map(ParameterSpec::typeText)
                .reduce((left, right) -> left + "," + right)
                .orElse("")
            + ")";
    return new MethodSpec(name, methodKey, signature, parameterList);
  }

  private static ParameterSpec parameter(String name) {
    return new ParameterSpec(name, "java.lang.String", List.of());
  }

  private static JavaCodeIndex javaCodeIndex(
      ArtifactReference verifiedSnapshotRef, List<MethodSpec> methods) {
    List<JavaDeclarationCatalog.MethodDeclarationView> declarations =
        methods.stream()
            .map(
                method ->
                    new JavaDeclarationCatalog.MethodDeclarationView(
                        method.methodKey(),
                        NAMESPACE,
                        method.name(),
                        "METHOD",
                        List.of("public"),
                        method.parameters().stream()
                            .map(
                                parameter ->
                                    new JavaDeclarationCatalog.ParameterView(
                                        method.parameters().indexOf(parameter),
                                        parameter.name(),
                                        parameter.typeText(),
                                        false,
                                        parameter.annotations()))
                            .toList(),
                        "example.persistence.Record",
                        List.of(),
                        JAVA_PATH,
                        new SourceRange(0, 1, 1, 1),
                        false))
            .toList();
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            SNAPSHOT, List.of(JAVA_PATH), List.of(), declarations, List.of(), List.of(), Map.of());
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "SQL projection fixture has no technical enrichment",
            List.of(),
            List.of(),
            null);
    return new JavaCodeIndex(
        new EngineDescriptor("jdt", "fixture", Map.of("fixture", "1"), "17", List.of()),
        SNAPSHOT,
        verifiedSnapshotRef,
        catalog,
        List.of(),
        enhancements);
  }

  private static VerifiedSourceTextSet sourceSet(Map<String, String> sourceByPath) {
    return new VerifiedSourceTextSet(
        SNAPSHOT,
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", "sql-fixture"),
        reference("source-inventory", "sql-fixture"),
        reference("verified-snapshot", "sql-fixture"),
        controls(),
        sourceByPath.entrySet().stream()
            .map(entry -> document(entry.getKey(), entry.getValue()))
            .toList());
  }

  private static VerifiedSourceTextDocument document(String path, String source) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    return new VerifiedSourceTextDocument(
        id("file", path),
        path,
        "100644",
        path.endsWith(".xml") ? "application/xml" : "text/x-java-source",
        bytes.length,
        new Sha256Digest(digest(bytes)),
        ImmutableBytes.copyOf(bytes));
  }

  private static SourceExcerptV1 excerpt(String path, String source, String token) {
    byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
    return new SourceExcerptV1(
        new SourceLocatorV1(
            id("file", path), path, 0, bytes.length, 1, 1, 1, Math.max(1, token.length())),
        ImmutableBytes.copyOf(bytes),
        new Sha256Digest(digest(bytes)));
  }

  private static ProgramGraphsReference navigationPublication() {
    AnalysisRunId run = new AnalysisRunId("analysis-run:" + "b".repeat(64));
    return new ProgramGraphsReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(run, AnalysisStepKey.PROGRAM_GRAPHS),
            new AnalysisStepArtifactRoot("analysis-step-root:" + "c".repeat(64)),
            new AnalysisStepReceiptId("analysis-step-receipt:" + "d".repeat(64)),
            new Sha256Digest("e".repeat(64))));
  }

  private static ArtifactControls controls() {
    return new ArtifactControls(
        new Sha256Digest("f".repeat(64)),
        new Sha256Digest("0".repeat(64)),
        new Sha256Digest("1".repeat(64)),
        null,
        new ArtifactPolicyRegistryReference(
            id("artifact-policy-registry", "sql-fixture"), new Sha256Digest("2".repeat(64))));
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(id(prefix, value), new Sha256Digest(digest(value)));
  }

  private static ArtifactId id(String prefix, String value) {
    return new ArtifactId(prefix + ":" + digest(value));
  }

  private static PersistenceMaterialIndex.SqlAstNode firstKind(
      PersistenceMaterialIndex.SqlAstNode root, String kind) {
    if (kind.equals(root.kind())) return root;
    return root.children().stream()
        .map(child -> firstKindOrNull(child, kind))
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing SQL AST kind " + kind));
  }

  private static PersistenceMaterialIndex.SqlAstNode firstKindOrNull(
      PersistenceMaterialIndex.SqlAstNode root, String kind) {
    if (kind.equals(root.kind())) return root;
    return root.children().stream()
        .map(child -> firstKindOrNull(child, kind))
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static boolean containsKind(PersistenceMaterialIndex.SqlAstNode root, String kind) {
    return kind.equals(root.kind())
        || root.children().stream().anyMatch(child -> containsKind(child, kind));
  }

  private static String flattenAstValues(PersistenceMaterialIndex.SqlAstNode root) {
    String own = root.value() == null ? "" : root.value();
    return own
        + root.children().stream()
            .map(PersistenceSqlAnalysisTest::flattenAstValues)
            .reduce("", String::concat);
  }

  private static String flattenAstMetadata(PersistenceMaterialIndex.SqlAstNode root) {
    String ownValue = root.value() == null ? "" : root.value();
    String ownAttributes =
        root.attributes().entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce("", (left, right) -> left + right);
    return ownValue
        + ownAttributes
        + root.children().stream()
            .map(PersistenceSqlAnalysisTest::flattenAstMetadata)
            .reduce("", String::concat);
  }

  private static boolean hasAstNodeWithContent(
      PersistenceMaterialIndex.SqlAstNode root, String... expectedFragments) {
    String ownValue = root.value() == null ? "" : root.value();
    String ownAttributes =
        root.attributes().entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce("", (left, right) -> left + right);
    String ownContent = ownValue + ownAttributes;
    boolean ownMatches = java.util.Arrays.stream(expectedFragments).allMatch(ownContent::contains);
    return ownMatches
        || root.children().stream()
            .anyMatch(child -> hasAstNodeWithContent(child, expectedFragments));
  }

  private static int countKind(PersistenceMaterialIndex.SqlAstNode root, String kind) {
    return (kind.equals(root.kind()) ? 1 : 0)
        + root.children().stream().mapToInt(child -> countKind(child, kind)).sum();
  }

  private static boolean hasElement(PersistenceMaterialIndex.XmlNode root, String name) {
    return (root.kind() == PersistenceMaterialIndex.XmlNodeKind.ELEMENT
            && name.equals(root.elementName()))
        || root.children().stream().anyMatch(child -> hasElement(child, name));
  }

  private static PersistenceMaterialIndex.XmlNode firstElement(
      PersistenceMaterialIndex.XmlNode root, String name) {
    if (root.kind() == PersistenceMaterialIndex.XmlNodeKind.ELEMENT
        && name.equals(root.elementName())) {
      return root;
    }
    return root.children().stream()
        .map(child -> firstElementOrNull(child, name))
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing XML element " + name));
  }

  private static PersistenceMaterialIndex.XmlNode firstElementOrNull(
      PersistenceMaterialIndex.XmlNode root, String name) {
    if (root.kind() == PersistenceMaterialIndex.XmlNodeKind.ELEMENT
        && name.equals(root.elementName())) {
      return root;
    }
    return root.children().stream()
        .map(child -> firstElementOrNull(child, name))
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static boolean hasKind(
      PersistenceMaterialIndex.XmlNode root, PersistenceMaterialIndex.XmlNodeKind kind) {
    return root.kind() == kind || root.children().stream().anyMatch(child -> hasKind(child, kind));
  }

  private static String flattenContent(PersistenceMaterialIndex.XmlNode root) {
    String own = root.content() == null ? "" : root.content();
    return own
        + root.children().stream()
            .map(PersistenceSqlAnalysisTest::flattenContent)
            .reduce("", String::concat);
  }

  private record MethodSpec(
      String name, String methodKey, String signature, List<ParameterSpec> parameters) {
    private MethodSpec {
      parameters = List.copyOf(parameters);
    }
  }

  private record ParameterSpec(String name, String typeText, List<String> annotations) {
    private ParameterSpec {
      annotations = List.copyOf(annotations);
    }
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
