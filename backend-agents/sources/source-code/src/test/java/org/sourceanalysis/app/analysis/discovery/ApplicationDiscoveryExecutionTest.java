package org.sourceanalysis.app.analysis.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

class ApplicationDiscoveryExecutionTest {

  @TempDir Path temporaryDirectory;

  @Test
  void exposesOnePathFreeExecutorForThePersistedM1ToM4DiscoveryChain() {
    Class<?> executor =
        typeOrNull("org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryExecutor");
    Class<?> request =
        typeOrNull("org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryRequest");

    assertThat(executor)
        .as("Stage 02 needs one explicit execution seam instead of an in-memory test-only chain")
        .isNotNull();
    assertThat(request).as("the execution input must be a closed typed request").isNotNull();
    assertThat(executor.getDeclaredConstructors())
        .as("the execution seam cannot accept a caller-owned filesystem path")
        .allSatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));
  }

  @Test
  void executesM1ToM4ThroughThePersistedProfileBoundaryForOneFrozenSpringMyBatisRepository() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextSet source = source(controls);
    AtomicInteger reopenCount = new AtomicInteger();
    VerifiedSourceTextReader sourceHandle =
        reference -> {
          assertThat(reference).isEqualTo(frozenSource);
          reopenCount.incrementAndGet();
          return source;
        };

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(4, 100_000, 300_000, 10));
      FileSystemCanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(4, 100_000, 300_000, 10));

      ApplicationDiscoveryReference result =
          new ApplicationDiscoveryExecutor(sourceHandle, modules, steps)
              .execute(
                  new ApplicationDiscoveryRequest(
                      new AnalysisStepPublicationAddress(
                          frozenSource.publication().address().runId(),
                          AnalysisStepKey.APPLICATION_DISCOVERY),
                      frozenSource,
                      DiscoveryProfile.standard()));

      var reopened = steps.reopen(result.publication());
      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "application-profile.json",
              "capability-report.json",
              "entry-points.jsonl",
              "mapper-catalog.jsonl");
      assertThat(reopenCount.get())
          .as("M1, persisted profile verification, M2 and M3 each reopen verified source bytes")
          .isEqualTo(4);
    }
  }

  @Test
  void closesRepositoryEntryCoverageOnlyForCompleteFullyAccountedSource() {
    assertAll(
        () ->
            assertCoverageClosure(
                "complete", "COMPLETE_CAPTURE", true, true, temporaryDirectory.resolve("complete")),
        () ->
            assertCoverageClosure(
                "bounded",
                "BOUNDED_PATH_SET",
                false,
                false,
                temporaryDirectory.resolve("bounded")));
  }

  private void assertCoverageClosure(
      String caseName,
      String scopeKind,
      boolean completionEligible,
      boolean expectedClosed,
      Path storeDirectory)
      throws java.io.IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    VerifiedSourceInventoryReference frozenSource = frozenSource();
    VerifiedSourceTextSet source = source(controls, scopeKind, completionEligible);
    VerifiedSourceTextReader sourceHandle = reference -> source;
    Files.createDirectory(storeDirectory);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(storeDirectory)) {
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(4, 100_000, 300_000, 10));
      FileSystemCanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(4, 100_000, 300_000, 10));

      ApplicationDiscoveryReference result =
          new ApplicationDiscoveryExecutor(sourceHandle, modules, steps)
              .execute(
                  new ApplicationDiscoveryRequest(
                      new AnalysisStepPublicationAddress(
                          frozenSource.publication().address().runId(),
                          AnalysisStepKey.APPLICATION_DISCOVERY),
                      frozenSource,
                      DiscoveryProfile.standard()));

      var reopened = steps.reopen(result.publication());
      var entryPayload =
          reopened.semanticPayloads().stream()
              .filter(payload -> payload.descriptor().fileName().equals("entry-points.jsonl"))
              .findFirst()
              .orElseThrow();
      var mapperPayload =
          reopened.semanticPayloads().stream()
              .filter(payload -> payload.descriptor().fileName().equals("mapper-catalog.jsonl"))
              .findFirst()
              .orElseThrow();
      JsonNode capability =
          canonicalJson.parseCanonical(
              reopened.semanticPayloads().stream()
                  .filter(
                      payload -> payload.descriptor().fileName().equals("capability-report.json"))
                  .findFirst()
                  .orElseThrow()
                  .canonicalUtf8());
      long entryCount =
          new String(entryPayload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8)
              .strip()
              .lines()
              .count();
      long mapperCatalogEntryCount =
          new String(mapperPayload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8)
              .strip()
              .lines()
              .count();

      assertThat(entryCount).as(caseName + " must discover local entries").isPositive();
      assertThat(mapperCatalogEntryCount)
          .as(caseName + " must discover local mapper catalog entries")
          .isPositive();
      assertThat(capability.at("/httpEntrySites").size())
          .as(caseName + " must publish HTTP site accounting")
          .isPositive();
      assertThat(capability.at("/httpEntryShardReceipts").size())
          .as(caseName + " must publish HTTP shard accounting")
          .isPositive();
      assertThat(capability.at("/mapperCatalogSites").size())
          .as(caseName + " must publish mapper site accounting")
          .isPositive();
      assertThat(capability.at("/mapperCatalogShardReceipts").size())
          .as(caseName + " must publish mapper shard accounting")
          .isPositive();
      assertThat(capability.at("/repositoryEntryCoverage/entryCount").intValue())
          .isEqualTo(entryCount);
      assertThat(capability.at("/repositoryEntryCoverage/mapperCatalogEntryCount").intValue())
          .isEqualTo(mapperCatalogEntryCount);
      assertThat(capability.at("/repositoryEntryCoverage/httpEntrySiteCount").intValue())
          .isEqualTo(capability.at("/httpEntrySites").size());
      assertThat(capability.at("/repositoryEntryCoverage/mapperCatalogSiteCount").intValue())
          .isEqualTo(capability.at("/mapperCatalogSites").size());

      JsonNode closed = capability.at("/repositoryEntryCoverage/closed");
      assertThat(closed.isBoolean())
          .as(caseName + " repositoryEntryCoverage.closed must be a boolean")
          .isTrue();
      assertThat(closed.booleanValue())
          .as(caseName + " repositoryEntryCoverage.closed")
          .isEqualTo(expectedClosed);
    }
  }

  private static VerifiedSourceTextSet source(ArtifactControls controls) {
    return new VerifiedSourceTextSet(
        "snapshot:" + "1".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", '2'),
        reference("verified-source-inventory-source-inventory", '3'),
        reference("verified-snapshot", '4'),
        controls,
        List.of(
            document(
                "pom.xml",
                """
                <project><modelVersion>4.0.0</modelVersion><properties><maven.compiler.release>17</maven.compiler.release></properties><dependencies><dependency><artifactId>spring-webmvc</artifactId></dependency><dependency><artifactId>mybatis-spring</artifactId></dependency></dependencies></project>
                """),
            document(
                "src/main/resources/application.yml",
                "mybatis:\n  mapper-locations: classpath:mapper/*.xml\n"),
            document(
                "src/main/java/com/example/DepotHeadController.java",
                """
                package com.example;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                @RequestMapping("/depotHead")
                public class DepotHeadController {
                  @PostMapping("/batchSetStatus")
                  public String batchSetStatus(String status, String ids) { return "ok"; }
                }
                """),
            document(
                "src/main/java/com/example/DepotHeadMapper.java",
                """
                package com.example;
                public interface DepotHeadMapper { int updateStatus(String status); }
                """),
            document(
                "src/main/resources/mapper/DepotHeadMapper.xml",
                """
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="com.example.DepotHeadMapper"><update id="updateStatus">update jsh_depot_head set status = #{status}</update></mapper>
                """)));
  }

  private static VerifiedSourceTextSet source(
      ArtifactControls controls, String scopeKind, boolean completionEligible) {
    return new VerifiedSourceTextSet(
        "snapshot:" + "1".repeat(64),
        scopeKind,
        completionEligible,
        reference("capability-profile", '2'),
        reference("verified-source-inventory-source-inventory", '3'),
        reference("verified-snapshot", '4'),
        controls,
        List.of(
            document(
                "pom.xml",
                """
                <project><modelVersion>4.0.0</modelVersion><properties><maven.compiler.release>17</maven.compiler.release></properties><dependencies><dependency><artifactId>spring-webmvc</artifactId></dependency><dependency><artifactId>mybatis-spring</artifactId></dependency></dependencies></project>
                """),
            document(
                "src/main/resources/application.yml",
                "mybatis:\n  mapper-locations: classpath:mapper/*.xml\n"),
            document(
                "src/main/java/com/example/DepotHeadController.java",
                """
                package com.example;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                @RequestMapping("/depotHead")
                public class DepotHeadController {
                  @PostMapping("/batchSetStatus")
                  public String batchSetStatus(String status, String ids) { return "ok"; }
                }
                """),
            document(
                "src/main/java/com/example/DepotHeadMapper.java",
                """
                package com.example;
                public interface DepotHeadMapper { int updateStatus(String status); }
                """),
            document(
                "src/main/resources/mapper/DepotHeadMapper.xml",
                """
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="com.example.DepotHeadMapper"><update id="updateStatus">update jsh_depot_head set status = #{status}</update></mapper>
                """)));
  }

  private static VerifiedSourceTextDocument document(String path, String source) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    String digest = sha256(bytes);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse("file:" + sha256((path + "\n" + digest).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        "text/plain",
        bytes.length,
        Sha256Digest.parse(digest),
        ImmutableBytes.copyOf(bytes));
  }

  private static VerifiedSourceInventoryReference frozenSource() {
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "5".repeat(64));
    return new VerifiedSourceInventoryReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + "6".repeat(64)),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + "7".repeat(64)),
            digest('8')));
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(digest('9'), digest('a'), digest('b'), null, policies.reference());
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)), digest(digit));
  }

  private static Sha256Digest digest(char digit) {
    return Sha256Digest.parse(String.valueOf(digit).repeat(64));
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec canonicalJson) {
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    policy(
        policies,
        "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
        "application-discovery-application-profile-v2",
        "application-profile",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        policies,
        "APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT",
        "application-discovery-application-profile-draft-v2",
        "application-profile",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        policies,
        "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
        "application-discovery-capability-report-v2",
        "capability-report",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        policies,
        "APPLICATION_DISCOVERY_ENTRY_POINTS",
        "application-discovery-entry-points-v2",
        "entry-points",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        policies,
        "APPLICATION_DISCOVERY_HTTP_ENTRY_DISCOVERY",
        "application-discovery-http-entry-discovery-v2",
        "http-entry-discovery",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        policies,
        "APPLICATION_DISCOVERY_MAPPER_CATALOG",
        "application-discovery-mapper-catalog-v2",
        "mapper-catalog",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        policies,
        "APPLICATION_DISCOVERY_MAPPER_CATALOG_DRAFT",
        "application-discovery-mapper-catalog-draft-v2",
        "mapper-catalog",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    withoutId.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + sha256(
                frame(
                    "canonical-artifact-policy-registry-id-v2",
                    canonicalJson.encodeCanonical(withoutId).copyToByteArray())));
    return CanonicalArtifactPolicyRegistry.load(
        canonicalJson.encodeCanonical(withoutId), canonicalJson);
  }

  private static void policy(
      ArrayNode policies,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      String mediaType,
      String envelopeKind,
      boolean emptyJsonlAllowed) {
    policies
        .addObject()
        .put("artifactType", artifactType)
        .put("schemaVersion", schemaVersion)
        .put("artifactIdPrefix", artifactIdPrefix)
        .put("mediaType", mediaType)
        .put("envelopeKind", envelopeKind)
        .put("emptyJsonlAllowed", emptyJsonlAllowed)
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
  }

  private static byte[] frame(String domain, byte[] value) {
    return concatenate(frame(domain.getBytes(StandardCharsets.UTF_8)), frame(value));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
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
