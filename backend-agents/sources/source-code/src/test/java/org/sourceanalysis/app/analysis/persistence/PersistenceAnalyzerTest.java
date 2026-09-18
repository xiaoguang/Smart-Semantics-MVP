package org.sourceanalysis.app.analysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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

/** RED contract for the optional, source-only persistence material analyzer. */
class PersistenceAnalyzerTest {

  private static final String SNAPSHOT = "snapshot:" + "1".repeat(64);
  private static final String NAMESPACE = "example.persistence.NeutralMapper";
  private static final String XML_PATH = "src/main/resources/mapper/NeutralMapper.xml";
  private static final String SHARED_XML_PATH = "src/main/resources/mapper/SharedFragments.xml";
  private static final String JAVA_PATH = "src/main/java/example/persistence/NeutralMapper.java";
  private static final String JAVA_SOURCE =
      "package example.persistence;\n"
          + "interface NeutralMapper {\n"
          + "  Record find(@Param(\"label\") String label);\n"
          + "  Record missing(String value);\n"
          + "}\n";

  @Test
  void disabledConfigurationDoesNotParseUnsafeXmlOrPublishPersistenceRecords() {
    String unsafeXml =
        "<!DOCTYPE mapper [ <!ENTITY external SYSTEM \"file:///etc/passwd\"> ]>"
            + "<mapper namespace=\""
            + NAMESPACE
            + "\"><select id=\"find\">&external;</select>";
    PersistenceAnalysisRequest request =
        request(
            sourceSet(Map.of(XML_PATH, unsafeXml)), List.of(), PersistenceConfiguration.disabled());

    PersistenceMaterialIndex result =
        Assertions.assertDoesNotThrow(() -> new DefaultPersistenceAnalyzer().analyze(request));

    // Empty plugins are a deliberate no-op: the frozen XML is not parsed at all.
    assertThat(String.valueOf(result.header().status())).isEqualTo("DISABLED");
    assertThat(result.header().sourceSnapshotId()).isEqualTo(SNAPSHOT);
    assertThat(result.header().navigationPublication()).isEqualTo(request.navigationPublication());
    assertThat(result.header().tools()).isEmpty();
    assertThat(result.resources()).isEmpty();
    assertThat(result.statements()).isEmpty();
    assertThat(result.bindings()).isEmpty();
    assertThat(result.sqlAnalyses()).isEmpty();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  void
      associatesExactNamespaceAndMethodWithEveryDatabaseIdVariantAndKeepsUnknownCandidatesUnbound() {
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<mapper namespace=\""
            + NAMESPACE
            + "\">\n"
            + "  <sql id=\"baseColumns\">id, label</sql>\n"
            + "  <select id=\"find\" databaseId=\"default\">\n"
            + "    SELECT <include refid=\"baseColumns\"/> FROM neutral_table\n"
            + "    <where><if test=\"label != null\">AND label = #{label}</if></where>\n"
            + "  </select>\n"
            + "  <select id=\"find\" databaseId=\"postgres\">\n"
            + "    SELECT <include refid=\"baseColumns\"/> FROM neutral_table\n"
            + "    <where><if test=\"label != null\">AND label = #{label}</if></where>\n"
            + "  </select>\n"
            + "</mapper>\n";
    MapperCatalogEntry exactCandidate =
        new MapperCatalogEntry(
            id("mapper-catalog-entry", "exact"),
            NAMESPACE,
            List.of(
                new MapperMethodCandidate(
                    id("mapper-method", "find"),
                    "find(java.lang.String)",
                    excerpt(JAVA_PATH, JAVA_SOURCE, "find(")),
                new MapperMethodCandidate(
                    id("mapper-method", "unknown-method"),
                    "missing(java.lang.String)",
                    excerpt(JAVA_PATH, JAVA_SOURCE, "missing("))),
            XML_PATH,
            NAMESPACE,
            List.of(
                new MapperStatementCandidate(
                    id("mapper-statement", "find"),
                    "find",
                    "select",
                    excerpt(XML_PATH, xml, "id=\"find\"")),
                new MapperStatementCandidate(
                    id("mapper-statement", "unknown-statement"),
                    "missing",
                    "select",
                    excerpt(XML_PATH, xml, "<mapper"))),
            "CANDIDATE_NOT_YET_BOUND");
    MapperCatalogEntry wrongNamespaceCandidate =
        new MapperCatalogEntry(
            id("mapper-catalog-entry", "wrong-namespace"),
            "example.persistence.OtherMapper",
            List.of(
                new MapperMethodCandidate(
                    id("mapper-method", "other"),
                    "find(java.lang.String)",
                    excerpt(JAVA_PATH, JAVA_SOURCE, "find("))),
            XML_PATH,
            "example.persistence.OtherMapper",
            List.of(
                new MapperStatementCandidate(
                    id("mapper-statement", "other"),
                    "find",
                    "select",
                    excerpt(XML_PATH, xml, "<mapper"))),
            "CANDIDATE_NOT_YET_BOUND");

    PersistenceAnalysisRequest request =
        request(
            sourceSet(Map.of(XML_PATH, xml, JAVA_PATH, JAVA_SOURCE)),
            List.of(exactCandidate, wrongNamespaceCandidate),
            new PersistenceConfiguration(
                List.of(new PersistenceConfiguration.Plugin("mybatis", "jsqlparser"))));

    PersistenceMaterialIndex result =
        Assertions.assertDoesNotThrow(() -> new DefaultPersistenceAnalyzer().analyze(request));

    // One frozen source document is the canonical raw resource, even with two statement variants.
    assertThat(result.resources()).hasSize(1);
    assertThat(result.resources().get(0).resourcePath()).isEqualTo(XML_PATH);
    assertThat(result.resources().get(0).namespace()).isEqualTo(NAMESPACE);
    assertThat(result.resources().get(0).rawSource()).isEqualTo(xml);

    assertThat(result.statements()).hasSize(2);
    assertThat(result.statements())
        .extracting(PersistenceMaterialIndex.Statement::statementId)
        .containsOnly("find");
    assertThat(result.statements())
        .extracting(PersistenceMaterialIndex.Statement::databaseId)
        .containsExactly("default", "postgres");
    assertThat(result.statements())
        .allSatisfy(
            statement -> {
              assertThat(statement.resourceRef()).isEqualTo(XML_PATH);
              assertThat(statement.dependencyRefs())
                  .extracting(PersistenceMaterialIndex.DependencyRef::reference)
                  .contains("baseColumns");
              assertThat(hasElementNamed(statement.xmlSubtree(), "if"))
                  .as("dynamic condition remains an ordered XML element")
                  .isTrue();
            });

    // Only exact namespace + method + statement candidates bind; unknown inputs are not guessed.
    assertThat(result.bindings()).hasSize(1);
    PersistenceMaterialIndex.JavaBinding binding = result.bindings().get(0);
    assertThat(binding.javaInterfaceFqn()).isEqualTo(NAMESPACE);
    assertThat(binding.methodKey()).isEqualTo("method:neutral-find");
    assertThat(binding.parameters()).hasSize(1);
    assertThat(binding.parameters().get(0).name()).isEqualTo("label");
    assertThat(binding.parameters().get(0).annotationTexts()).contains("@Param(\"label\")");
    assertThat(binding.statementRefs()).hasSize(2);
    assertThat(binding.statementRefs())
        .extracting(PersistenceMaterialIndex.StatementRef::databaseId)
        .containsExactly("default", "postgres");
    assertThat(String.valueOf(result.header().status())).isEqualTo("ENABLED");
  }

  @Test
  void retainsFrozenCrossFileIncludeFragmentWithoutInventingJavaMapperBinding() {
    String mapperXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<mapper namespace=\""
            + NAMESPACE
            + "\">\n"
            + "  <select id=\"find\">SELECT <include refid=\"example.persistence.SharedFragments.baseColumns\"/>"
            + " FROM neutral_table</select>\n"
            + "</mapper>\n";
    String fragmentXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<mapper namespace=\"example.persistence.SharedFragments\">\n"
            + "  <sql id=\"baseColumns\">id, label</sql>\n"
            + "</mapper>\n";
    MapperCatalogEntry mapperCandidate =
        new MapperCatalogEntry(
            id("mapper-catalog-entry", "cross-file"),
            NAMESPACE,
            List.of(
                new MapperMethodCandidate(
                    id("mapper-method", "find"),
                    "find(java.lang.String)",
                    excerpt(JAVA_PATH, JAVA_SOURCE, "find("))),
            XML_PATH,
            NAMESPACE,
            List.of(
                new MapperStatementCandidate(
                    id("mapper-statement", "find"),
                    "find",
                    "select",
                    excerpt(XML_PATH, mapperXml, "id=\"find\""))),
            "CANDIDATE_NOT_YET_BOUND");
    PersistenceAnalysisRequest request =
        request(
            sourceSet(
                Map.of(
                    XML_PATH, mapperXml,
                    SHARED_XML_PATH, fragmentXml,
                    JAVA_PATH, JAVA_SOURCE)),
            List.of(mapperCandidate),
            new PersistenceConfiguration(
                List.of(new PersistenceConfiguration.Plugin("mybatis", "jsqlparser"))));

    PersistenceMaterialIndex result =
        Assertions.assertDoesNotThrow(() -> new DefaultPersistenceAnalyzer().analyze(request));

    assertThat(result.resources())
        .extracting(PersistenceMaterialIndex.Resource::resourcePath)
        .containsExactlyInAnyOrder(XML_PATH, SHARED_XML_PATH);
    assertThat(result.resources())
        .filteredOn(resource -> SHARED_XML_PATH.equals(resource.resourcePath()))
        .singleElement()
        .satisfies(
            resource -> {
              assertThat(resource.namespace()).isEqualTo("example.persistence.SharedFragments");
              assertThat(resource.rawSource()).isEqualTo(fragmentXml);
            });
    assertThat(result.resources())
        .filteredOn(resource -> XML_PATH.equals(resource.resourcePath()))
        .singleElement()
        .satisfies(
            resource ->
                assertThat(resource.dependencyResourcePaths()).containsExactly(SHARED_XML_PATH));
    assertThat(result.statements())
        .singleElement()
        .satisfies(
            statement -> {
              assertThat(statement.resourceRef()).isEqualTo(XML_PATH);
              assertThat(statement.dependencyRefs())
                  .singleElement()
                  .satisfies(
                      dependency -> {
                        assertThat(dependency.kind()).isEqualTo("INCLUDE");
                        assertThat(dependency.reference())
                            .isEqualTo("example.persistence.SharedFragments.baseColumns");
                        assertThat(dependency.resolution()).isEqualTo("RESOLVED_STATIC");
                      });
              assertThat(hasElementNamed(statement.xmlSubtree(), "include")).isTrue();
            });
    assertThat(result.bindings())
        .extracting(PersistenceMaterialIndex.JavaBinding::javaInterfaceFqn)
        .containsExactly(NAMESPACE)
        .doesNotContain("example.persistence.SharedFragments");
  }

  @Test
  void retainsCrossFileResultMapInheritanceChainWithoutInventingSharedJavaBindings() {
    String mapperXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<mapper namespace=\""
            + NAMESPACE
            + "\">\n"
            + "  <select id=\"find\" resultMap=\"example.persistence.ChildMap.child\">"
            + "SELECT id FROM neutral_table</select>\n"
            + "</mapper>\n";
    String childMapXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<mapper namespace=\"example.persistence.ChildMap\">\n"
            + "  <resultMap id=\"child\" extends=\"example.persistence.BaseMap.base\">\n"
            + "    <id property=\"id\" column=\"id\"/>\n"
            + "  </resultMap>\n"
            + "</mapper>\n";
    String baseMapXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<mapper namespace=\"example.persistence.BaseMap\">\n"
            + "  <resultMap id=\"base\" type=\"example.persistence.Record\">\n"
            + "    <result property=\"id\" column=\"id\"/>\n"
            + "  </resultMap>\n"
            + "</mapper>\n";
    String childMapPath = "src/main/resources/mapper/ChildMap.xml";
    String baseMapPath = "src/main/resources/mapper/BaseMap.xml";
    MapperCatalogEntry mapperCandidate =
        new MapperCatalogEntry(
            id("mapper-catalog-entry", "result-map-chain"),
            NAMESPACE,
            List.of(
                new MapperMethodCandidate(
                    id("mapper-method", "find-result-map"),
                    "find(java.lang.String)",
                    excerpt(JAVA_PATH, JAVA_SOURCE, "find("))),
            XML_PATH,
            NAMESPACE,
            List.of(
                new MapperStatementCandidate(
                    id("mapper-statement", "find-result-map"),
                    "find",
                    "select",
                    excerpt(XML_PATH, mapperXml, "id=\"find\""))),
            "CANDIDATE_NOT_YET_BOUND");

    PersistenceAnalysisRequest request =
        request(
            sourceSet(
                Map.of(
                    XML_PATH, mapperXml,
                    childMapPath, childMapXml,
                    baseMapPath, baseMapXml,
                    JAVA_PATH, JAVA_SOURCE)),
            List.of(mapperCandidate),
            new PersistenceConfiguration(
                List.of(new PersistenceConfiguration.Plugin("mybatis", "jsqlparser"))));

    PersistenceMaterialIndex result =
        Assertions.assertDoesNotThrow(() -> new DefaultPersistenceAnalyzer().analyze(request));

    assertThat(result.resources())
        .extracting(PersistenceMaterialIndex.Resource::resourcePath)
        .containsExactlyInAnyOrder(XML_PATH, childMapPath, baseMapPath);
    assertThat(result.resources())
        .filteredOn(resource -> XML_PATH.equals(resource.resourcePath()))
        .singleElement()
        .satisfies(
            resource -> {
              assertThat(resource.rawSource()).isEqualTo(mapperXml);
              assertThat(resource.dependencyResourcePaths()).containsExactly(childMapPath);
            });
    assertThat(result.resources())
        .filteredOn(resource -> childMapPath.equals(resource.resourcePath()))
        .singleElement()
        .satisfies(
            resource -> {
              assertThat(resource.rawSource()).isEqualTo(childMapXml);
              assertThat(resource.dependencyResourcePaths()).containsExactly(baseMapPath);
            });
    assertThat(result.resources())
        .filteredOn(resource -> baseMapPath.equals(resource.resourcePath()))
        .singleElement()
        .satisfies(resource -> assertThat(resource.rawSource()).isEqualTo(baseMapXml));
    assertThat(result.statements())
        .singleElement()
        .satisfies(
            statement -> {
              assertThat(statement.resourceRef()).isEqualTo(XML_PATH);
              assertThat(statement.dependencyRefs())
                  .extracting(PersistenceMaterialIndex.DependencyRef::reference)
                  .contains("example.persistence.ChildMap.child");
            });
    assertThat(result.bindings())
        .extracting(PersistenceMaterialIndex.JavaBinding::javaInterfaceFqn)
        .containsExactly(NAMESPACE)
        .doesNotContain("example.persistence.ChildMap", "example.persistence.BaseMap");
  }

  private static PersistenceAnalysisRequest request(
      VerifiedSourceTextSet source,
      List<MapperCatalogEntry> mapperCatalog,
      PersistenceConfiguration configuration) {
    return new PersistenceAnalysisRequest(
        javaCodeIndex(source.verifiedSnapshotRef()),
        navigationPublication(),
        source,
        mapperCatalog,
        configuration);
  }

  private static JavaCodeIndex javaCodeIndex(ArtifactReference verifiedSnapshotRef) {
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            SNAPSHOT,
            List.of(JAVA_PATH),
            List.of(),
            List.of(
                new JavaDeclarationCatalog.MethodDeclarationView(
                    "method:neutral-find",
                    NAMESPACE,
                    "find",
                    "METHOD",
                    List.of("public"),
                    List.of(
                        new JavaDeclarationCatalog.ParameterView(
                            0, "label", "java.lang.String", false, List.of("@Param(\"label\")"))),
                    "example.persistence.Record",
                    List.of(),
                    JAVA_PATH,
                    new SourceRange(0, JAVA_SOURCE.length(), 1, 4),
                    false),
                new JavaDeclarationCatalog.MethodDeclarationView(
                    "method:neutral-missing",
                    NAMESPACE,
                    "missing",
                    "METHOD",
                    List.of("public"),
                    List.of(
                        new JavaDeclarationCatalog.ParameterView(
                            0, "value", "java.lang.String", false, List.of())),
                    "example.persistence.Record",
                    List.of(),
                    JAVA_PATH,
                    new SourceRange(0, JAVA_SOURCE.length(), 1, 4),
                    false)),
            List.of(),
            List.of(),
            Map.of());
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "persistence analyzer fixture has no technical enrichment",
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

  private static ProgramGraphsReference navigationPublication() {
    AnalysisRunId run = new AnalysisRunId("analysis-run:" + "2".repeat(64));
    return new ProgramGraphsReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(run, AnalysisStepKey.PROGRAM_GRAPHS),
            new AnalysisStepArtifactRoot("analysis-step-root:" + "3".repeat(64)),
            new AnalysisStepReceiptId("analysis-step-receipt:" + "4".repeat(64)),
            new Sha256Digest("5".repeat(64))));
  }

  private static VerifiedSourceTextSet sourceSet(Map<String, String> sourceByPath) {
    List<VerifiedSourceTextDocument> documents =
        sourceByPath.entrySet().stream()
            .map(entry -> document(entry.getKey(), entry.getValue()))
            .toList();
    return new VerifiedSourceTextSet(
        SNAPSHOT,
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", "fixture"),
        reference("source-inventory", "fixture"),
        reference("verified-snapshot", "fixture"),
        controls(),
        documents);
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

  private static boolean hasElementNamed(
      PersistenceMaterialIndex.XmlNode node, String expectedName) {
    return (node.kind() == PersistenceMaterialIndex.XmlNodeKind.ELEMENT
            && expectedName.equals(node.elementName()))
        || node.children().stream().anyMatch(child -> hasElementNamed(child, expectedName));
  }

  private static ArtifactControls controls() {
    return new ArtifactControls(
        new Sha256Digest("6".repeat(64)),
        new Sha256Digest("7".repeat(64)),
        new Sha256Digest("8".repeat(64)),
        null,
        new ArtifactPolicyRegistryReference(
            id("artifact-policy-registry", "fixture"), new Sha256Digest("9".repeat(64))));
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(id(prefix, value), new Sha256Digest(digest(value)));
  }

  private static ArtifactId id(String prefix, String value) {
    return new ArtifactId(prefix + ":" + digest(value));
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
