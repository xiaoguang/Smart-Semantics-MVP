package org.sourceanalysis.app.analysis.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
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

class ApplicationProfileDetectorTest {

  @Test
  void detectsStaticJavaSpringMvcAndMyBatisSignalsFromVerifiedPomAndConfiguration() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceContentHandle sourceHandle =
        reference -> {
          assertThat(reference).isEqualTo(frozenSource);
          return sourceTextSet(
              List.of(
                  text(
                      "pom.xml",
                      """
                      <project>
                        <modelVersion>4.0.0</modelVersion>
                        <properties><maven.compiler.release>8</maven.compiler.release></properties>
                        <dependencies>
                          <dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId></dependency>
                          <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId></dependency>
                        </dependencies>
                      </project>
                      """),
                  text(
                      "src/main/resources/application.yml",
                      """
                      mybatis:
                        mapper-locations: classpath:mapper_xml/*.xml
                      """)));
        };

    ApplicationProfile profile =
        new ApplicationProfileDetector(sourceHandle)
            .detect(frozenSource, DiscoveryProfile.standard());

    assertThat(profile.snapshotId()).isEqualTo("snapshot:" + "a".repeat(64));
    assertThat(profile.repositoryCompletionEligible()).isTrue();
    assertThat(profile.language()).isEqualTo(ApplicationLanguage.JAVA);
    assertThat(profile.languageVersion()).isEqualTo(8);
    assertThat(profile.frameworkSignals())
        .extracting(FrameworkSignal::kind)
        .containsExactly(FrameworkSignalKind.MYBATIS, FrameworkSignalKind.SPRING_MVC);
    assertThat(profile.configSignals())
        .singleElement()
        .satisfies(
            signal -> {
              assertThat(signal.kind()).isEqualTo(ConfigSignalKind.MYBATIS_MAPPER_LOCATION);
              assertThat(signal.value()).isEqualTo("classpath:mapper_xml/*.xml");
              assertThat(signal.sourceExcerpt().locator().path())
                  .isEqualTo("src/main/resources/application.yml");
            });
  }

  @Test
  void rejectsConflictingJavaReleaseSignalsFromTheSameVerifiedRepository() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceContentHandle sourceHandle =
        reference ->
            sourceTextSet(
                List.of(
                    text(
                        "pom.xml",
                        """
                        <project><properties><maven.compiler.release>8</maven.compiler.release></properties></project>
                        """),
                    text(
                        "module/pom.xml",
                        """
                        <project><properties><maven.compiler.release>17</maven.compiler.release></properties></project>
                        """)));

    assertThatThrownBy(
            () ->
                new ApplicationProfileDetector(sourceHandle)
                    .detect(frozenSource, DiscoveryProfile.standard()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("APPLICATION_PROFILE_CONFLICT");
  }

  @Test
  void doesNotTreatAnUnrelatedMapperLocationsKeyAsMyBatisConfiguration() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceContentHandle sourceHandle =
        reference ->
            sourceTextSet(
                List.of(
                    text(
                        "pom.xml",
                        """
                        <project><dependencies><dependency><artifactId>spring-webmvc</artifactId></dependency></dependencies></project>
                        """),
                    text(
                        "src/main/resources/application.yml",
                        """
                        unrelated-integration:
                          mapper-locations: classpath:not-a-mybatis-mapper/*.xml
                        """)));

    ApplicationProfile profile =
        new ApplicationProfileDetector(sourceHandle)
            .detect(frozenSource, DiscoveryProfile.standard());

    assertThat(profile.configSignals()).isEmpty();
  }

  @Test
  void usesMavenCompilerSourceWhenReleaseIsNotDeclared() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceContentHandle sourceHandle =
        reference ->
            sourceTextSet(
                List.of(
                    text(
                        "pom.xml",
                        """
                        <project><properties><maven.compiler.source>17</maven.compiler.source></properties></project>
                        """)));

    ApplicationProfile profile =
        new ApplicationProfileDetector(sourceHandle)
            .detect(frozenSource, DiscoveryProfile.standard());

    assertThat(profile.languageVersion()).isEqualTo(17);
  }

  @Test
  void givesEquivalentVerifiedDocumentsOneCanonicalProfileIdentityRegardlessOfReadOrder() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextDocument pom =
        text(
            "pom.xml",
            """
            <project>
              <properties><maven.compiler.release>8</maven.compiler.release></properties>
              <dependencies><dependency><artifactId>spring-webmvc</artifactId></dependency></dependencies>
            </project>
            """);
    VerifiedSourceTextDocument config =
        text(
            "src/main/resources/application.yml",
            """
            mybatis:
              mapper-locations: classpath:mapper_xml/*.xml
            """);

    ApplicationProfile first =
        new ApplicationProfileDetector(reference -> sourceTextSet(List.of(pom, config)))
            .detect(frozenSource, DiscoveryProfile.standard());
    ApplicationProfile second =
        new ApplicationProfileDetector(reference -> sourceTextSet(List.of(config, pom)))
            .detect(frozenSource, DiscoveryProfile.standard());

    assertThat(first.inventoryScopeKind()).isEqualTo("COMPLETE_CAPTURE");
    assertThat(first.applicationProfileId()).isEqualTo(second.applicationProfileId());
  }

  @Test
  void doesNotTreatAnArbitraryXmlFilenameEndingInPomXmlAsAMavenModel() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceContentHandle sourceHandle =
        reference ->
            sourceTextSet(
                List.of(
                    text("pom.xml", "<project/>"),
                    text(
                        "src/main/resources/not-a-pom.xml",
                        """
                        <project><dependencies><dependency><artifactId>spring-webmvc</artifactId></dependency></dependencies></project>
                        """)));

    ApplicationProfile profile =
        new ApplicationProfileDetector(sourceHandle)
            .detect(frozenSource, DiscoveryProfile.standard());

    assertThat(profile.frameworkSignals()).isEmpty();
  }

  @Test
  void includesThePublishedControlIdentityInTheCanonicalProfileIdentity() {
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    List<VerifiedSourceTextDocument> documents = List.of(text("pom.xml", "<project/>"));

    ApplicationProfile first =
        new ApplicationProfileDetector(reference -> sourceTextSet(controls('a'), documents))
            .detect(frozenSource, DiscoveryProfile.standard());
    ApplicationProfile second =
        new ApplicationProfileDetector(reference -> sourceTextSet(controls('b'), documents))
            .detect(frozenSource, DiscoveryProfile.standard());

    assertThat(first.applicationProfileId()).isNotEqualTo(second.applicationProfileId());
  }

  private static VerifiedSourceInventoryReference frozenSource() {
    return new VerifiedSourceInventoryReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(
                AnalysisRunId.parse("analysis-run:" + "1".repeat(64)),
                AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + "2".repeat(64)),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + "3".repeat(64)),
            Sha256Digest.parse("4".repeat(64))));
  }

  private static VerifiedSourceTextDocument text(String path, String content) {
    byte[] rawUtf8 = content.getBytes(StandardCharsets.UTF_8);
    String sha256 = sha256(rawUtf8);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse(
            "file:" + sha256((path + "\\n" + sha256).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        path.endsWith(".xml") ? "application/xml" : "text/plain",
        rawUtf8.length,
        Sha256Digest.parse(sha256),
        ImmutableBytes.copyOf(rawUtf8));
  }

  private static VerifiedSourceTextSet sourceTextSet(List<VerifiedSourceTextDocument> documents) {
    return sourceTextSet(controls('a'), documents);
  }

  private static VerifiedSourceTextSet sourceTextSet(
      ArtifactControls controls, List<VerifiedSourceTextDocument> documents) {
    return new VerifiedSourceTextSet(
        "snapshot:" + "a".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", '5'),
        reference("verified-source-inventory-source-inventory", '6'),
        reference("verified-snapshot", '7'),
        controls,
        documents);
  }

  private static ArtifactControls controls(char toolchainDigit) {
    return new ArtifactControls(
        Sha256Digest.parse(String.valueOf(toolchainDigit).repeat(64)),
        Sha256Digest.parse("b".repeat(64)),
        Sha256Digest.parse("c".repeat(64)),
        null,
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse("artifact-policy-registry:" + "d".repeat(64)),
            Sha256Digest.parse("d".repeat(64))));
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)),
        Sha256Digest.parse(String.valueOf(digit).repeat(64)));
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
