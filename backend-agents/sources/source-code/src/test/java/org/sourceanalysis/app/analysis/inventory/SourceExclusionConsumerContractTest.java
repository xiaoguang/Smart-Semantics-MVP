package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.cli.SourcePreparationPolicyFixture;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.discovery.MapperXmlResourceView;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.persistence.PersistenceAnalysisRequest;
import org.sourceanalysis.app.analysis.persistence.PersistenceConfiguration;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.preparation.CapturedSourcePreparation;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.capture.preparation.SourcePreparationToolIdentity;

/** Proves excluded files do not reach consumers through the public prepared-source reader. */
class SourceExclusionConsumerContractTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ArtifactStoreLimits STORE_LIMITS =
      new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 16);
  private static final String INCLUDED_JAVA = "src/main/java/fixture/IncludedMapper.java";
  private static final String EXCLUDED_JAVA = "src/main/java/fixture/ExcludedMapper.java";
  private static final String INCLUDED_XML = "src/main/resources/mapper/IncludedMapper.xml";
  private static final String EXCLUDED_XML = "src/main/resources/mapper/ExcludedMapper.xml";
  private static final String SNAPSHOT = "snapshot:" + "1".repeat(64);

  @TempDir Path temporaryDirectory;

  @Test
  void preparedReaderWithExclusionsConstrainsJavaAndPersistenceConsumers() throws Exception {
    PreparedFixture fixture = prepareAndReopen();
    VerifiedSourceTextSet sourceTexts = fixture.sourceTexts();

    assertThat(fixture.saved().assessment().readiness())
        .isEqualTo(SourcePreparationReadiness.READY_WITH_EXCLUSIONS);
    assertThat(fixture.saved().result().entries())
        .filteredOn(entry -> entry.disposition() == SourceEntry.Disposition.EXCLUDED_BY_USER)
        .extracting(SourceEntry::relativePath)
        .containsExactlyInAnyOrder(EXCLUDED_JAVA, EXCLUDED_XML);
    assertThat(fixture.saved().result().entries())
        .filteredOn(entry -> entry.disposition() == SourceEntry.Disposition.EXCLUDED_BY_USER)
        .allSatisfy(entry -> assertThat(entry.blobRef()).isNotNull());

    assertThat(sourceTexts.documents())
        .extracting(VerifiedSourceTextDocument::path)
        .containsExactlyInAnyOrder(INCLUDED_JAVA, INCLUDED_XML)
        .doesNotContain(EXCLUDED_JAVA, EXCLUDED_XML);
    assertThat(sourceTexts.documents())
        .extracting(document -> text(document.rawUtf8()))
        .contains(
            "package fixture;\npublic interface IncludedMapper {}\n",
            "<mapper namespace=\"fixture.IncludedMapper\"><select id=\"find\">SELECT 1</select></mapper>")
        .doesNotContain(
            "package fixture;\npublic interface ExcludedMapper {}\n",
            "<mapper namespace=\"fixture.ExcludedMapper\"><select id=\"find\">SELECT 2</select></mapper>");

    VerifiedJavaProject javaProject =
        VerifiedJavaProject.fromVerifiedSourceTextSet(
            sourceTexts, List.of("src/main/java"), List.of(), "17");
    assertThat(javaProject.sourceEntries())
        .contains(INCLUDED_JAVA, INCLUDED_XML)
        .doesNotContain(EXCLUDED_JAVA, EXCLUDED_XML);

    PersistenceAnalysisRequest persistenceRequest = persistenceRequest(sourceTexts);
    assertThat(persistenceRequest.frozenSource()).isSameAs(sourceTexts);
    assertThat(persistenceRequest.javaCodeIndex().snapshotId()).isEqualTo(sourceTexts.snapshotId());
    assertThat(persistenceRequest.javaCodeIndex().snapshotRef())
        .isEqualTo(sourceTexts.verifiedSnapshotRef());
    assertThat(persistenceRequest.mapperCatalog())
        .extracting(MapperCatalogEntry::xmlResourcePath)
        .containsExactly(INCLUDED_XML);

    MapperXmlResourceView xmlResources =
        MapperXmlResourceView.open(persistenceRequest.frozenSource());
    xmlResources.requireSameFrozenSource(sourceTexts);
    assertThat(xmlResources.mapperResource(INCLUDED_XML)).isPresent();
    assertThat(xmlResources.mapperResource(EXCLUDED_XML)).isEmpty();
    assertThat(xmlResources.rejectedResource(EXCLUDED_XML)).isEmpty();
  }

  private PreparedFixture prepareAndReopen() throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("source"));
    SourceOrigin origin =
        new DirectorySourceOrigin("fixture.invalid/source-exclusion", sourceRoot.toRealPath());
    Map<String, byte[]> bytes =
        Map.of(
            INCLUDED_JAVA,
            bytes("package fixture;\npublic interface IncludedMapper {}\n"),
            EXCLUDED_JAVA,
            bytes("package fixture;\npublic interface ExcludedMapper {}\n"),
            INCLUDED_XML,
            bytes(
                "<mapper namespace=\"fixture.IncludedMapper\"><select id=\"find\">SELECT 1</select></mapper>"),
            EXCLUDED_XML,
            bytes(
                "<mapper namespace=\"fixture.ExcludedMapper\"><select id=\"find\">SELECT 2</select></mapper>"));
    List<SourcePreparationTarget> exclusions =
        List.of(
            new SourcePreparationTarget(EXCLUDED_JAVA, SourcePreparationTarget.Kind.FILE),
            new SourcePreparationTarget(EXCLUDED_XML, SourcePreparationTarget.Kind.FILE));
    SourcePreparationRequest request =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            origin,
            null,
            List.of(),
            exclusions,
            exclusions,
            new SourcePreparationLimits(20, 1_000_000L),
            policyRef(policies));
    List<SourceEntry> entries =
        List.of(
            entry(INCLUDED_JAVA, bytes.get(INCLUDED_JAVA), false),
            entry(EXCLUDED_JAVA, bytes.get(EXCLUDED_JAVA), true),
            entry(INCLUDED_XML, bytes.get(INCLUDED_XML), false),
            entry(EXCLUDED_XML, bytes.get(EXCLUDED_XML), true));
    SourcePreparationResult result =
        new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.COMPLETED,
            true,
            entries,
            List.of(),
            List.of(),
            List.of());
    Path storeRoot = Files.createDirectory(temporaryDirectory.resolve("store"));
    Path archiveRoot = temporaryDirectory.resolve("archive");
    PreparedSourceArchive archive = new PreparedSourceArchive(archiveRoot);
    CapturedSourcePreparation captured =
        new CapturedSourcePreparation(
            request,
            result,
            sourceEntry -> {
              byte[] content = bytes.get(sourceEntry.relativePath());
              if (content == null
                  || sourceEntry.disposition() != SourceEntry.Disposition.VERIFIED_TEXT) {
                throw new IOException("No included source bytes for " + sourceEntry.relativePath());
              }
              return new ByteArrayInputStream(content);
            },
            new SourcePreparationToolIdentity(
                "verified-source-inventory/v3",
                "fixture",
                Sha256Digest.parse("c".repeat(64)),
                "fixture-vendor",
                "fixture-java",
                null));

    SavedSourcePreparation saved;
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      saved =
          new SourcePreparationPublisher(modules, steps, archive, policies)
              .publish(runId('a'), captured);
    }

    VerifiedSourceTextSet sourceTexts;
    try (RunStoreHandle handle = RunStoreBootstrap.open(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, JSON, policies, STORE_LIMITS);
      SourcePreparationReader preparationReader =
          new SourcePreparationReader(modules, steps, archive);
      sourceTexts =
          new PreparedVerifiedSourceTextReader(preparationReader, archive)
              .reopen(
                  new VerifiedSourceInventoryReference(
                      saved.sourceVersionReference().publication()));
    }
    return new PreparedFixture(saved, sourceTexts);
  }

  private static PersistenceAnalysisRequest persistenceRequest(VerifiedSourceTextSet sourceTexts) {
    JavaDeclarationCatalog declarations =
        new JavaDeclarationCatalog(
            sourceTexts.snapshotId(),
            List.of(INCLUDED_JAVA),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of());
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "consumer contract fixture does not run navigation enrichment",
            List.of(),
            List.of(),
            null);
    JavaCodeIndex javaCodeIndex =
        new JavaCodeIndex(
            new EngineDescriptor("jdt", "fixture", Map.of("fixture", "1"), "17", List.of()),
            sourceTexts.snapshotId(),
            sourceTexts.verifiedSnapshotRef(),
            declarations,
            List.of(),
            enhancements);
    AnalysisRunId runId = runId('b');
    ProgramGraphsReference navigation =
        new ProgramGraphsReference(
            new AnalysisStepPublicationReference(
                new AnalysisStepPublicationAddress(runId, AnalysisStepKey.PROGRAM_GRAPHS),
                new AnalysisStepArtifactRoot("analysis-step-root:" + "d".repeat(64)),
                new AnalysisStepReceiptId("analysis-step-receipt:" + "e".repeat(64)),
                Sha256Digest.parse("f".repeat(64))));
    MapperCatalogEntry mapper =
        new MapperCatalogEntry(
            artifactId("mapper-catalog-entry", '2'),
            "fixture.IncludedMapper",
            List.of(),
            INCLUDED_XML,
            "fixture.IncludedMapper",
            List.of(),
            "CANDIDATE_NOT_YET_BOUND");
    return new PersistenceAnalysisRequest(
        javaCodeIndex,
        navigation,
        sourceTexts,
        List.of(mapper),
        PersistenceConfiguration.disabled());
  }

  private CanonicalArtifactPolicyRegistry policies() {
    return SourcePreparationPolicyFixture.load(
        Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
            .toAbsolutePath(),
        JSON);
  }

  private static SourceEntry entry(String path, byte[] content, boolean excluded) {
    Sha256Digest digest = digest(content);
    SourceOriginAttributes attributes =
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null);
    ArtifactReference blobRef =
        new ArtifactReference(artifactId("source-blob", digest.value()), digest);
    ArtifactId fileId = SourceVersionCalculator.fileId(path, content.length, digest, attributes);
    SourceObservation observation =
        new SourceObservation((long) content.length, digest, fileId, null, "fixture:" + path);
    SourceEntry verified =
        new SourceEntry(
            path,
            SourceEntry.Kind.REGULAR_FILE,
            SourceEntry.Disposition.VERIFIED_TEXT,
            (long) content.length,
            digest,
            blobRef,
            fileId,
            "UTF-8",
            attributes,
            new SourceEntryObservations(observation, observation),
            List.of(),
            null,
            null);
    if (!excluded) {
      return verified;
    }
    return new SourceEntry(
        verified.relativePath(),
        verified.entryKind(),
        SourceEntry.Disposition.EXCLUDED_BY_USER,
        verified.sizeBytes(),
        verified.sha256(),
        verified.blobRef(),
        verified.fileId(),
        null,
        verified.originAttributes(),
        verified.observations(),
        List.of(),
        null,
        new SourceEntryExclusion(
            "USER_DECLARED", SourcePreparationOperation.NEW, verified.relativePath()));
  }

  private static ArtifactReference policyRef(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256());
  }

  private static ArtifactId artifactId(String type, String identity) {
    return ArtifactId.parse(type + ":" + identity);
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String text(ImmutableBytes value) {
    return new String(value.copyToByteArray(), StandardCharsets.UTF_8);
  }

  private static Sha256Digest digest(byte[] value) {
    try {
      return Sha256Digest.parse(
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static AnalysisRunId runId(char identity) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(identity).repeat(64));
  }

  private static ArtifactId artifactId(String type, char identity) {
    return artifactId(type, String.valueOf(identity).repeat(64));
  }

  private record PreparedFixture(SavedSourcePreparation saved, VerifiedSourceTextSet sourceTexts) {}
}
