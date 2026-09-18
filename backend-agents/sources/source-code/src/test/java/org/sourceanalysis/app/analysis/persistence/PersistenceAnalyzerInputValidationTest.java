package org.sourceanalysis.app.analysis.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
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

/** RED coverage for persistence input ownership and frozen-resource boundaries. */
class PersistenceAnalyzerInputValidationTest {

  private static final String SNAPSHOT = "snapshot:" + "1".repeat(64);
  private static final String XML_PATH = "src/main/resources/mapper/NeutralMapper.xml";
  private static final String MISSING_XML_PATH = "src/main/resources/mapper/MissingMapper.xml";
  private static final String JAVA_PATH = "src/main/java/example/persistence/NeutralMapper.java";

  @Test
  void rejectsMapperCatalogResourceOutsideVerifiedFrozenSourceInsteadOfReturningNoHit() {
    VerifiedSourceTextSet source =
        sourceSet(
            SNAPSHOT,
            reference("verified-snapshot", "source"),
            Map.of(JAVA_PATH, "interface NeutralMapper {}\n"));
    PersistenceAnalysisRequest request =
        new PersistenceAnalysisRequest(
            javaCodeIndex(SNAPSHOT, source.verifiedSnapshotRef()),
            navigationPublication(),
            source,
            List.of(
                new MapperCatalogEntry(
                    id("mapper-catalog-entry", "missing"),
                    "example.persistence.NeutralMapper",
                    List.of(),
                    MISSING_XML_PATH,
                    "example.persistence.NeutralMapper",
                    List.of(),
                    "CANDIDATE_NOT_YET_BOUND")),
            enabledConfiguration());

    // A corrupt/out-of-inventory frozen source is an input failure, not an empty analysis result.
    assertThatThrownBy(() -> new DefaultPersistenceAnalyzer().analyze(request))
        .hasMessageContaining("MAPPER_RESOURCE_UNAVAILABLE");
  }

  @Test
  void rejectsJavaIndexAndFrozenSourceWhenVerifiedSnapshotReferencesDisagree() {
    ArtifactReference javaSnapshot = reference("verified-snapshot", "java");
    ArtifactReference sourceSnapshot = reference("verified-snapshot", "source");
    VerifiedSourceTextSet source =
        sourceSet(
            SNAPSHOT,
            sourceSnapshot,
            Map.of(XML_PATH, "<mapper namespace=\"example.persistence.NeutralMapper\"/>"));

    assertThatThrownBy(
            () ->
                new PersistenceAnalysisRequest(
                    javaCodeIndex(SNAPSHOT, javaSnapshot),
                    navigationPublication(),
                    source,
                    List.of(),
                    enabledConfiguration()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("one verified snapshot");
  }

  private static PersistenceConfiguration enabledConfiguration() {
    return new PersistenceConfiguration(
        List.of(new PersistenceConfiguration.Plugin("mybatis", "jsqlparser")));
  }

  private static JavaCodeIndex javaCodeIndex(String snapshot, ArtifactReference snapshotRef) {
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            snapshot, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "input validation fixture has no technical enrichment",
            List.of(),
            List.of(),
            null);
    return new JavaCodeIndex(
        new EngineDescriptor("jdt", "fixture", Map.of("fixture", "1"), "17", List.of()),
        snapshot,
        snapshotRef,
        catalog,
        List.of(),
        enhancements);
  }

  private static VerifiedSourceTextSet sourceSet(
      String snapshot, ArtifactReference verifiedSnapshotRef, Map<String, String> documents) {
    return new VerifiedSourceTextSet(
        snapshot,
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", "input-validation"),
        reference("source-inventory", "input-validation"),
        verifiedSnapshotRef,
        controls(),
        documents.entrySet().stream()
            .map(entry -> document(entry.getKey(), entry.getValue()))
            .toList());
  }

  private static VerifiedSourceTextDocument document(String path, String source) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    return new VerifiedSourceTextDocument(
        id("file", path),
        path,
        "100644",
        path.endsWith(".xml") ? "application/xml" : "text/plain",
        bytes.length,
        new Sha256Digest(digest(bytes)),
        ImmutableBytes.copyOf(bytes));
  }

  private static ProgramGraphsReference navigationPublication() {
    AnalysisRunId run = new AnalysisRunId("analysis-run:" + "3".repeat(64));
    return new ProgramGraphsReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(run, AnalysisStepKey.PROGRAM_GRAPHS),
            new AnalysisStepArtifactRoot("analysis-step-root:" + "4".repeat(64)),
            new AnalysisStepReceiptId("analysis-step-receipt:" + "5".repeat(64)),
            new Sha256Digest("6".repeat(64))));
  }

  private static ArtifactControls controls() {
    return new ArtifactControls(
        new Sha256Digest("7".repeat(64)),
        new Sha256Digest("8".repeat(64)),
        new Sha256Digest("9".repeat(64)),
        null,
        new ArtifactPolicyRegistryReference(
            id("artifact-policy-registry", "input-validation"), new Sha256Digest("a".repeat(64))));
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
