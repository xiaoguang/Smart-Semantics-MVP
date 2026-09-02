package org.sourceanalysis.app.analysis.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

class MapperCapabilityCatalogerTest {

  @Test
  void exposesAPathFreeStaticMapperCatalogSeam() {
    Class<?> cataloger =
        typeOrNull("org.sourceanalysis.app.analysis.discovery.MapperCapabilityCataloger");
    Class<?> catalog =
        typeOrNull("org.sourceanalysis.app.analysis.discovery.MapperCatalogDiscovery");

    assertThat(cataloger)
        .as("M3 must catalog verified Mapper candidates before program graphs bind them")
        .isNotNull();
    assertThat(catalog).as("M3 requires a typed candidate-catalog result").isNotNull();
    assertThat(cataloger.getDeclaredConstructors())
        .as("Mapper cataloging must not accept a caller filesystem path")
        .allSatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));
  }

  @Test
  void catalogsMatchingMapperJavaAndStandardDoctypeXmlAsUnboundCandidates() {
    VerifiedSourceTextDocument mapperJava =
        text(
            "src/main/java/com/example/DepotHeadMapper.java",
            """
            package com.example;

            public interface DepotHeadMapper {
              int updateByExampleSelective(DepotHead record, DepotHeadExample example);
            }
            """);
    VerifiedSourceTextDocument mapperXml =
        text(
            "src/main/resources/mapper/DepotHeadMapper.xml",
            """
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.DepotHeadMapper">
              <update id="updateByExampleSelective">
                update jsh_depot_head set status = #{record.status}
              </update>
            </mapper>
            """);
    ArtifactControls controls = controls();
    ApplicationProfile profile = profile(mapperJava, controls);
    VerifiedSourceContentHandle sourceHandle =
        reference -> sourceTextSet(mapperJava, mapperXml, controls);

    MapperCatalogDiscovery catalog =
        new MapperCapabilityCataloger(sourceHandle).catalogMappers(profile, frozenSource());

    assertThat(catalog.entries())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.javaInterfaceFqn()).isEqualTo("com.example.DepotHeadMapper");
              assertThat(entry.xmlResourcePath())
                  .isEqualTo("src/main/resources/mapper/DepotHeadMapper.xml");
              assertThat(entry.xmlNamespace()).isEqualTo("com.example.DepotHeadMapper");
              assertThat(entry.bindingState()).isEqualTo("CANDIDATE_NOT_YET_BOUND");
              assertThat(entry.javaMethodCandidates())
                  .extracting(MapperMethodCandidate::signature)
                  .containsExactly(
                      "com.example.DepotHeadMapper#updateByExampleSelective(DepotHead,DepotHeadExample)");
              assertThat(entry.xmlStatementCandidates())
                  .extracting(MapperStatementCandidate::statementId)
                  .containsExactly("updateByExampleSelective");
            });
    assertThat(catalog.sites())
        .singleElement()
        .satisfies(
            site -> {
              assertThat(site.disposition()).isEqualTo(SignalDisposition.SUPPORTED);
              assertThat(site.reasonCode()).isNull();
              assertThat(site.gapId()).isNull();
              assertThat(site.primaryExcerpt().rawUtf8().copyToByteArray())
                  .isEqualTo(
                      "namespace=\"com.example.DepotHeadMapper\"".getBytes(StandardCharsets.UTF_8));
            });
  }

  @Test
  void recordsBothSidesOfANamespaceMismatchAsExplicitMapperGaps() {
    VerifiedSourceTextDocument mapperJava =
        text(
            "src/main/java/com/example/DepotHeadMapper.java",
            """
            package com.example;

            public interface DepotHeadMapper {
              int updateByExampleSelective(DepotHead record);
            }
            """);
    VerifiedSourceTextDocument mapperXml =
        text(
            "src/main/resources/mapper/DepotHeadMapper.xml",
            """
            <mapper namespace="com.example.OtherMapper">
              <update id="updateByExampleSelective">update jsh_depot_head set status = 1</update>
            </mapper>
            """);
    ArtifactControls controls = controls();
    ApplicationProfile profile = profile(mapperJava, controls);
    VerifiedSourceContentHandle sourceHandle =
        reference -> sourceTextSet(mapperJava, mapperXml, controls);

    MapperCatalogDiscovery catalog =
        new MapperCapabilityCataloger(sourceHandle).catalogMappers(profile, frozenSource());

    assertThat(catalog.entries()).isEmpty();
    assertThat(catalog.sites())
        .extracting(MapperCatalogSite::reasonCode)
        .containsExactlyInAnyOrder(
            "XML_NAMESPACE_NO_JAVA_INTERFACE", "JAVA_INTERFACE_NO_XML_NAMESPACE");
    assertThat(catalog.sites()).allSatisfy(site -> assertThat(site.gapId()).isNotNull());
  }

  @Test
  void rejectsAnExternalXmlEntityBeforeCatalogingAnyMapperCandidate() {
    VerifiedSourceTextDocument mapperJava =
        text(
            "src/main/java/com/example/DepotHeadMapper.java",
            """
            package com.example;

            public interface DepotHeadMapper {}
            """);
    VerifiedSourceTextDocument mapperXml =
        text(
            "src/main/resources/mapper/DepotHeadMapper.xml",
            """
            <!DOCTYPE mapper [<!ENTITY secret SYSTEM "file:///not-allowed">]>
            <mapper namespace="com.example.DepotHeadMapper">
              <select id="find">&secret;</select>
            </mapper>
            """);
    ArtifactControls controls = controls();
    ApplicationProfile profile = profile(mapperJava, controls);
    VerifiedSourceContentHandle sourceHandle =
        reference -> sourceTextSet(mapperJava, mapperXml, controls);

    assertThatThrownBy(
            () ->
                new MapperCapabilityCataloger(sourceHandle).catalogMappers(profile, frozenSource()))
        .isInstanceOfSatisfying(
            ApplicationDiscoveryException.class,
            failure -> assertThat(failure.code()).isEqualTo("XML_EXTERNAL_RESOLUTION_ATTEMPT"));
  }

  private static ApplicationProfile profile(
      VerifiedSourceTextDocument document, ArtifactControls controls) {
    SourceExcerptV1 frameworkExcerpt =
        new SourceExcerptV1(
            new SourceLocatorV1(document.fileId(), document.path(), 0, 7, 1, 1, 1, 8),
            ImmutableBytes.copyOf("package".getBytes(StandardCharsets.UTF_8)),
            Sha256Digest.parse(sha256("package".getBytes(StandardCharsets.UTF_8))));
    return new ApplicationProfile(
        ArtifactId.parse("application-profile:" + "1".repeat(64)),
        "snapshot:" + "2".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        ApplicationLanguage.JAVA,
        8,
        List.of(
            new FrameworkSignal(
                FrameworkSignalKind.MYBATIS, frameworkExcerpt, SignalDisposition.SUPPORTED, null)),
        List.of(),
        reference("capability-profile", '3'),
        reference("verified-source-inventory-source-inventory", '4'),
        reference("verified-snapshot", '5'),
        controls);
  }

  private static VerifiedSourceTextSet sourceTextSet(
      VerifiedSourceTextDocument mapperJava,
      VerifiedSourceTextDocument mapperXml,
      ArtifactControls controls) {
    return new VerifiedSourceTextSet(
        "snapshot:" + "2".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", '3'),
        reference("verified-source-inventory-source-inventory", '4'),
        reference("verified-snapshot", '5'),
        controls,
        List.of(mapperJava, mapperXml));
  }

  private static VerifiedSourceInventoryReference frozenSource() {
    return new VerifiedSourceInventoryReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(
                AnalysisRunId.parse("analysis-run:" + "6".repeat(64)),
                AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + "7".repeat(64)),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + "8".repeat(64)),
            digest('9')));
  }

  private static VerifiedSourceTextDocument text(String path, String content) {
    byte[] rawUtf8 = content.getBytes(StandardCharsets.UTF_8);
    String sha256 = sha256(rawUtf8);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse(
            "file:" + sha256((path + "\\n" + sha256).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        "text/plain",
        rawUtf8.length,
        Sha256Digest.parse(sha256),
        ImmutableBytes.copyOf(rawUtf8));
  }

  private static ArtifactControls controls() {
    return new ArtifactControls(
        digest('a'),
        digest('b'),
        digest('c'),
        null,
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse("artifact-policy-registry:" + "d".repeat(64)), digest('d')));
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)), digest(digit));
  }

  private static Sha256Digest digest(char digit) {
    return Sha256Digest.parse(String.valueOf(digit).repeat(64));
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static Class<?> typeOrNull(String qualifiedName) {
    try {
      return Class.forName(qualifiedName);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }
}
