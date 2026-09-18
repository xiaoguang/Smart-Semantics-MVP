package org.sourceanalysis.app.analysis.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.persistence.DefaultPersistenceAnalyzer;
import org.sourceanalysis.app.analysis.persistence.PersistenceAnalysisRequest;
import org.sourceanalysis.app.analysis.persistence.PersistenceConfiguration;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
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

/** Contract for reusing one run-owned secure XML view from discovery through persistence. */
class MapperXmlResourceViewReuseTest {

  private static final String SNAPSHOT = "snapshot:" + "1".repeat(64);
  private static final String NAMESPACE = "example.persistence.NeutralMapper";
  private static final String SHARED_NAMESPACE = "example.persistence.SharedFragments";
  private static final String JAVA_PATH = "src/main/java/example/persistence/NeutralMapper.java";
  private static final String XML_PATH = "src/main/resources/mapper/NeutralMapper.xml";
  private static final String SHARED_XML_PATH = "src/main/resources/mapper/SharedFragments.xml";

  @Test
  void oneSecureViewFeedsMapperDiscoveryAndPersistenceForCrossFileFragmentWithoutJavaBinding() {
    String javaSource =
        "package example.persistence;\n"
            + "public interface NeutralMapper {\n"
            + "  Record find(String label);\n"
            + "}\n";
    String mapperXml =
        "<mapper namespace=\""
            + NAMESPACE
            + "\">\n"
            + "  <select id=\"find\">SELECT <include refid=\""
            + SHARED_NAMESPACE
            + ".baseColumns\"/> FROM neutral_table</select>\n"
            + "</mapper>\n";
    String sharedXml =
        "<mapper namespace=\""
            + SHARED_NAMESPACE
            + "\">\n"
            + "  <sql id=\"baseColumns\">id, label</sql>\n"
            + "</mapper>\n";
    Fixture fixture = fixture(javaSource, mapperXml, sharedXml);
    VerifiedSourceTextSet source = fixture.source();
    MapperXmlResourceView sharedView = MapperXmlResourceView.open(source);
    ApplicationProfile profile = profile(fixture.mapperJava(), fixture.controls());
    JavaDeclarationCatalog javaCatalog = fixture.javaCatalog();

    MapperCatalogDiscovery discovery =
        new MapperCapabilityCataloger(fixture.sourceReader())
            .catalogMappers(profile, source, javaCatalog, sharedView);

    assertThat(discovery.entries())
        .extracting(MapperCatalogEntry::xmlNamespace)
        .containsExactly(NAMESPACE);
    assertThat(discovery.sites())
        .filteredOn(site -> "XML_NAMESPACE_NO_JAVA_INTERFACE".equals(site.reasonCode()))
        .singleElement()
        .satisfies(
            site -> assertThat(site.primaryExcerpt().locator().path()).isEqualTo(SHARED_XML_PATH));

    JavaCodeIndex javaIndex =
        new JavaCodeIndex(
            new EngineDescriptor(
                "jdt", "view-reuse-fixture", Map.of("jdt", "fixture"), "17", List.of()),
            SNAPSHOT,
            source.verifiedSnapshotRef(),
            javaCatalog,
            List.of(),
            new EntryCodeContext.TechnicalEnhancements(
                EntryCodeContext.Availability.NOT_PRODUCED,
                "shared XML view test does not collect Java entries",
                List.of(),
                List.of(),
                null));
    PersistenceMaterialIndex result =
        new DefaultPersistenceAnalyzer(sharedView)
            .analyze(
                new PersistenceAnalysisRequest(
                    javaIndex,
                    fixture.navigation(),
                    source,
                    discovery.entries(),
                    new PersistenceConfiguration(
                        List.of(new PersistenceConfiguration.Plugin("mybatis", "jsqlparser")))));

    assertThat(result.resources())
        .extracting(PersistenceMaterialIndex.Resource::resourcePath)
        .containsExactlyInAnyOrder(XML_PATH, SHARED_XML_PATH);
    assertThat(result.resources())
        .filteredOn(resource -> XML_PATH.equals(resource.resourcePath()))
        .singleElement()
        .satisfies(
            resource ->
                assertThat(resource.dependencyResourcePaths()).containsExactly(SHARED_XML_PATH));
    assertThat(result.statements())
        .singleElement()
        .satisfies(
            statement ->
                assertThat(statement.dependencyRefs())
                    .extracting(PersistenceMaterialIndex.DependencyRef::reference)
                    .containsExactly(SHARED_NAMESPACE + ".baseColumns"));
    assertThat(result.bindings())
        .extracting(PersistenceMaterialIndex.JavaBinding::javaInterfaceFqn)
        .containsExactly(NAMESPACE)
        .doesNotContain(SHARED_NAMESPACE);

    MapperXmlResourceView.MapperXmlResource mainResource =
        sharedView.mapperResource(XML_PATH).orElseThrow();
    assertThat(mainResource.rawSource()).isEqualTo(mapperXml);
    assertThat(mainResource.document().rawUtf8().copyToByteArray())
        .containsExactly(mapperXml.getBytes(StandardCharsets.UTF_8));
    assertThat(mainResource.parsedDocument().getElementsByTagName("include"))
        .satisfies(
            includes -> {
              assertThat(includes.getLength()).isEqualTo(1);
              org.w3c.dom.Element include = (org.w3c.dom.Element) includes.item(0);
              assertThat(include.getAttribute("refid"))
                  .isEqualTo(SHARED_NAMESPACE + ".baseColumns");
            });
    MapperXmlResourceView.MapperXmlResource fragmentResource =
        sharedView.mapperResource(SHARED_XML_PATH).orElseThrow();
    assertThat(fragmentResource.rawSource()).isEqualTo(sharedXml);
    assertThat(fragmentResource.document().rawUtf8().copyToByteArray())
        .containsExactly(sharedXml.getBytes(StandardCharsets.UTF_8));
  }

  private static Fixture fixture(String javaSource, String mapperXml, String sharedXml) {
    ArtifactControls controls = controls();
    ArtifactReference capabilityProfile = reference("capability-profile", 'a');
    ArtifactReference sourceInventory = reference("source-inventory", 'b');
    ArtifactReference snapshotReference = reference("verified-snapshot", 'c');
    VerifiedSourceTextDocument mapperJava = document(JAVA_PATH, javaSource, "text/x-java-source");
    VerifiedSourceTextDocument mapper = document(XML_PATH, mapperXml, "application/xml");
    VerifiedSourceTextDocument shared = document(SHARED_XML_PATH, sharedXml, "application/xml");
    VerifiedSourceTextSet source =
        new VerifiedSourceTextSet(
            SNAPSHOT,
            "COMPLETE_CAPTURE",
            true,
            capabilityProfile,
            sourceInventory,
            snapshotReference,
            controls,
            List.of(mapperJava, mapper, shared));
    return new Fixture(source, mapperJava, controls, javaCatalog(javaSource), navigation());
  }

  private static ApplicationProfile profile(
      VerifiedSourceTextDocument mapperJava, ArtifactControls controls) {
    SourceExcerptV1 excerpt = excerpt(mapperJava, "package");
    return new ApplicationProfile(
        ArtifactId.parse("application-profile:" + "d".repeat(64)),
        SNAPSHOT,
        "COMPLETE_CAPTURE",
        true,
        ApplicationLanguage.JAVA,
        17,
        List.of(
            new FrameworkSignal(
                FrameworkSignalKind.MYBATIS, excerpt, SignalDisposition.SUPPORTED, null)),
        List.of(),
        reference("capability-profile", 'a'),
        reference("source-inventory", 'b'),
        reference("verified-snapshot", 'c'),
        controls);
  }

  private static JavaDeclarationCatalog javaCatalog(String javaSource) {
    String methodKey = "method:neutral-find";
    return new JavaDeclarationCatalog(
        SNAPSHOT,
        List.of(JAVA_PATH),
        List.of(
            new JavaDeclarationCatalog.TypeDeclaration(
                JAVA_PATH,
                new SourceRange(0, javaSource.length(), 1, 4),
                NAMESPACE,
                "INTERFACE",
                List.of(),
                List.of(),
                List.of(methodKey),
                List.of())),
        List.of(
            new JavaDeclarationCatalog.MethodDeclarationView(
                methodKey,
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
                new SourceRange(0, javaSource.length(), 1, 4),
                false)),
        List.of(),
        List.of(),
        Map.of());
  }

  private static VerifiedSourceTextDocument document(String path, String source, String mediaType) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse("file:" + digest(path + source)),
        path,
        "100644",
        mediaType,
        bytes.length,
        new Sha256Digest(digest(bytes)),
        ImmutableBytes.copyOf(bytes));
  }

  private static SourceExcerptV1 excerpt(VerifiedSourceTextDocument document, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return new SourceExcerptV1(
        new SourceLocatorV1(
            document.fileId(),
            document.path(),
            0,
            bytes.length,
            1,
            1,
            1,
            Math.max(1, bytes.length)),
        ImmutableBytes.copyOf(bytes),
        new Sha256Digest(digest(bytes)));
  }

  private static ArtifactControls controls() {
    return new ArtifactControls(
        new Sha256Digest("1".repeat(64)),
        new Sha256Digest("2".repeat(64)),
        new Sha256Digest("3".repeat(64)),
        null,
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse("artifact-policy-registry:" + "4".repeat(64)),
            new Sha256Digest("5".repeat(64))));
  }

  private static ProgramGraphsReference navigation() {
    AnalysisRunId run = new AnalysisRunId("analysis-run:" + "6".repeat(64));
    return new ProgramGraphsReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(run, AnalysisStepKey.PROGRAM_GRAPHS),
            new AnalysisStepArtifactRoot("analysis-step-root:" + "7".repeat(64)),
            new AnalysisStepReceiptId("analysis-step-receipt:" + "8".repeat(64)),
            new Sha256Digest("9".repeat(64))));
  }

  private static ArtifactReference reference(String prefix, char fill) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(fill).repeat(64)),
        new Sha256Digest(String.valueOf(fill).repeat(64)));
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private record Fixture(
      VerifiedSourceTextSet source,
      VerifiedSourceTextDocument mapperJava,
      ArtifactControls controls,
      JavaDeclarationCatalog javaCatalog,
      ProgramGraphsReference navigation) {

    private VerifiedSourceTextReader sourceReader() {
      return ignored -> source;
    }
  }
}
