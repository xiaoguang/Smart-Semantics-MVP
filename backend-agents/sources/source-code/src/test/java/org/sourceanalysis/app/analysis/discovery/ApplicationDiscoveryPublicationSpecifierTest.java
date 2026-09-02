package org.sourceanalysis.app.analysis.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
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
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

class ApplicationDiscoveryPublicationSpecifierTest {

  @TempDir Path temporaryDirectory;

  @Test
  void exposesThePathFreePublicationSeamForTheCompleteApplicationDiscoveryStep() {
    Class<?> specifier =
        typeOrNull(
            "org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryPublicationSpecifier");
    Class<?> reference =
        typeOrNull("org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference");

    assertThat(specifier)
        .as("M4 must be the explicit boundary that publishes the completed discovery step")
        .isNotNull();
    assertThat(reference)
        .as("a successful M4 publication must return a typed application-discovery reference")
        .isNotNull();
    assertThat(specifier.getDeclaredConstructors())
        .as("the publication seam receives trusted stores and never a caller-owned filesystem path")
        .allSatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));
    assertThat(specifier.getDeclaredMethods())
        .extracting(method -> method.getName())
        .contains("publish");
  }

  @Test
  void freshReopensM1ToM3AndPublishesTheExactFourDiscoveryPayloadsPlusReceipt() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    ApplicationProfile profile = profile(controls);
    HttpEntryDiscovery entries = entries();
    MapperCatalogDiscovery catalog = new MapperCatalogDiscovery(List.of(), List.of(), List.of());
    AnalysisRunId runId = runId();

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(4, 100_000, 300_000, 10));
      FileSystemCanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(4, 100_000, 300_000, 10));
      ApplicationProfileDraftReference profileDraft =
          new ApplicationProfileModulePublisher(modules)
              .publish(address(runId, 1, "application-profile"), profile);
      HttpEntryDiscoveryDraftReference entryDraft =
          new HttpEntryDiscoveryModulePublisher(modules)
              .publish(address(runId, 2, "http-entry"), profileDraft, profile, entries);
      MapperCatalogDraftReference catalogDraft =
          new MapperCatalogModulePublisher(modules)
              .publish(address(runId, 3, "mapper-catalog"), profileDraft, profile, catalog);
      VerifiedSourceInventoryReference sourceInventory = frozenSource(runId);

      ApplicationDiscoveryReference discovery =
          new ApplicationDiscoveryPublicationSpecifier(modules, steps)
              .publish(
                  new ApplicationDiscoveryPublicationRequest(
                      new AnalysisStepPublicationAddress(
                          runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                      sourceInventory,
                      profileDraft,
                      entryDraft,
                      catalogDraft));
      ApplicationDiscoveryReference repeated =
          new ApplicationDiscoveryPublicationSpecifier(modules, steps)
              .publish(
                  new ApplicationDiscoveryPublicationRequest(
                      new AnalysisStepPublicationAddress(
                          runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                      sourceInventory,
                      profileDraft,
                      entryDraft,
                      catalogDraft));

      var reopened = steps.reopen(discovery.publication());
      assertThat(repeated).isEqualTo(discovery);
      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "application-profile.json",
              "capability-report.json",
              "entry-points.jsonl",
              "mapper-catalog.jsonl");
      assertThat(reopened.receipt().upstreamAnalysisStepReferences())
          .containsExactly(sourceInventory.publication());
      assertThat(reopened.receipt().publicationProvenance().toString())
          .contains("moduleNumber=4", "moduleKey=publish");
      JsonNode publishedProfile =
          canonicalJson.parseCanonical(
              payload(reopened, "application-profile.json").canonicalUtf8());
      assertThat(publishedProfile.get("applicationProfileId").textValue())
          .isEqualTo(profile.applicationProfileId().value());
      assertThat(jsonl(payload(reopened, "entry-points.jsonl"), canonicalJson)).hasSize(1);
      assertThat(jsonl(payload(reopened, "mapper-catalog.jsonl"), canonicalJson)).isEmpty();
    }
  }

  @Test
  void rejectsM2WhosePublishedApplicationProfileIdentityDisagreesWithM1AndM3() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    ApplicationProfile profile = profile(controls);
    ApplicationProfile disagreeingProfile =
        new ApplicationProfile(
            id("application-profile", '9'),
            profile.snapshotId(),
            profile.inventoryScopeKind(),
            profile.repositoryCompletionEligible(),
            profile.language(),
            profile.languageVersion(),
            profile.frameworkSignals(),
            profile.configSignals(),
            profile.capabilityProfileRef(),
            profile.sourceInventoryRef(),
            profile.verifiedSnapshotRef(),
            profile.controls());
    AnalysisRunId runId = runId();

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(4, 100_000, 300_000, 10));
      FileSystemCanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(4, 100_000, 300_000, 10));
      ApplicationProfileDraftReference profileDraft =
          new ApplicationProfileModulePublisher(modules)
              .publish(address(runId, 1, "application-profile"), profile);
      HttpEntryDiscoveryDraftReference entryDraft =
          new HttpEntryDiscoveryModulePublisher(modules)
              .publish(
                  address(runId, 2, "http-entry"), profileDraft, disagreeingProfile, entries());
      MapperCatalogDraftReference catalogDraft =
          new MapperCatalogModulePublisher(modules)
              .publish(
                  address(runId, 3, "mapper-catalog"),
                  profileDraft,
                  profile,
                  new MapperCatalogDiscovery(List.of(), List.of(), List.of()));

      assertThatThrownBy(
              () ->
                  new ApplicationDiscoveryPublicationSpecifier(modules, steps)
                      .publish(
                          new ApplicationDiscoveryPublicationRequest(
                              new AnalysisStepPublicationAddress(
                                  runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                              frozenSource(runId),
                              profileDraft,
                              entryDraft,
                              catalogDraft)))
          .isInstanceOfSatisfying(
              ApplicationDiscoveryException.class,
              failure ->
                  assertThat(failure.code()).isEqualTo("APPLICATION_DISCOVERY_UPSTREAM_INVALID"));
    }
  }

  @Test
  void preservesAnEmptyEntryDenominatorAsAnEvidenceBoundNoEntryGapInsteadOfOmittingFiles() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    ApplicationProfile profile = profile(controls);
    AnalysisRunId runId = runId();

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(4, 100_000, 300_000, 10));
      FileSystemCanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle, canonicalJson, policies, new ArtifactStoreLimits(4, 100_000, 300_000, 10));
      ApplicationProfileDraftReference profileDraft =
          new ApplicationProfileModulePublisher(modules)
              .publish(address(runId, 1, "application-profile"), profile);
      HttpEntryDiscoveryDraftReference entryDraft =
          new HttpEntryDiscoveryModulePublisher(modules)
              .publish(
                  address(runId, 2, "http-entry"),
                  profileDraft,
                  profile,
                  new HttpEntryDiscovery(List.of(), List.of(), List.of()));
      MapperCatalogDraftReference catalogDraft =
          new MapperCatalogModulePublisher(modules)
              .publish(
                  address(runId, 3, "mapper-catalog"),
                  profileDraft,
                  profile,
                  new MapperCatalogDiscovery(List.of(), List.of(), List.of()));

      ApplicationDiscoveryReference discovery =
          new ApplicationDiscoveryPublicationSpecifier(modules, steps)
              .publish(
                  new ApplicationDiscoveryPublicationRequest(
                      new AnalysisStepPublicationAddress(
                          runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                      frozenSource(runId),
                      profileDraft,
                      entryDraft,
                      catalogDraft));

      var reopened = steps.reopen(discovery.publication());
      assertThat(reopened.receipt().status()).isEqualTo(ModuleCompletionStatus.SUCCEEDED_WITH_GAPS);
      assertThat(reopened.receipt().gapRefs()).hasSize(1);
      assertThat(jsonl(payload(reopened, "entry-points.jsonl"), canonicalJson)).isEmpty();
      JsonNode capability =
          canonicalJson.parseCanonical(payload(reopened, "capability-report.json").canonicalUtf8());
      assertThat(capability.at("/repositoryEntryCoverage/noEntryDiscovered").booleanValue())
          .isTrue();
      assertThat(capability.at("/noEntryDisposition/reasonCode").textValue())
          .isEqualTo("NO_ENTRY_DISCOVERED");
      assertThat(capability.at("/noEntryDisposition/gapId").textValue())
          .isEqualTo(reopened.receipt().gapRefs().get(0));
    }
  }

  private static org.sourceanalysis.app.artifact.VerifiedCanonicalPayload payload(
      org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication publication, String name) {
    return publication.semanticPayloads().stream()
        .filter(payload -> payload.descriptor().fileName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static List<JsonNode> jsonl(
      org.sourceanalysis.app.artifact.VerifiedCanonicalPayload payload,
      CanonicalJsonCodec canonicalJson) {
    String body =
        new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8).strip();
    if (body.isEmpty()) {
      return List.of();
    }
    return body.lines()
        .map(
            line ->
                canonicalJson.parseCanonical(
                    ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
        .toList();
  }

  private static ApplicationProfile profile(ArtifactControls controls) {
    return new ApplicationProfile(
        id("application-profile", '1'),
        "snapshot:" + "2".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        ApplicationLanguage.JAVA,
        17,
        List.of(),
        List.of(),
        reference("capability-profile", '3'),
        reference("verified-source-inventory-source-inventory", '4'),
        reference("verified-snapshot", '5'),
        controls);
  }

  private static HttpEntryDiscovery entries() {
    SourceExcerptV1 classRoute =
        excerpt(
            "src/main/java/com/example/DepotHeadController.java",
            "@RequestMapping(\"/depotHead\")");
    SourceExcerptV1 methodRoute =
        excerpt(
            "src/main/java/com/example/DepotHeadController.java",
            "@PostMapping(\"/batchSetStatus\")");
    ArtifactId entryId = id("entry", '6');
    ArtifactId siteId = id("site", '7');
    return new HttpEntryDiscovery(
        List.of(
            new HttpEntryPoint(
                entryId,
                HttpEntryKind.SPRING_MVC_HTTP,
                "HTTP",
                "POST",
                "/depotHead/batchSetStatus",
                List.of("/depotHead", "/batchSetStatus"),
                "com.example.DepotHeadController#batchSetStatus",
                List.of("status", "ids"),
                List.of(classRoute, methodRoute))),
        List.of(
            new HttpEntrySite(
                siteId, methodRoute, List.of(entryId), SignalDisposition.SUPPORTED, null, null)),
        List.of(
            new HttpEntryShardReceipt(
                id("http-shard", '8'), List.of(siteId), List.of(siteId), "SUCCEEDED", List.of())));
  }

  private static SourceExcerptV1 excerpt(String path, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    ArtifactId fileId = id("file", path.hashCode() < 0 ? '9' : 'a');
    return new SourceExcerptV1(
        new SourceLocatorV1(fileId, path, 0, bytes.length, 1, 1, 1, bytes.length + 1),
        ImmutableBytes.copyOf(bytes),
        Sha256Digest.parse(sha256(bytes)));
  }

  private static VerifiedSourceInventoryReference frozenSource(AnalysisRunId runId) {
    return new VerifiedSourceInventoryReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + "b".repeat(64)),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + "c".repeat(64)),
            digest('d')));
  }

  private static AnalysisStepModuleAddress address(
      AnalysisRunId runId, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        runId, AnalysisStepKey.APPLICATION_DISCOVERY, moduleNumber, moduleKey);
  }

  private static AnalysisRunId runId() {
    return AnalysisRunId.parse("analysis-run:" + "e".repeat(64));
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(digest('f'), digest('0'), digest('1'), null, policies.reference());
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(id(prefix, digit), digest(digit));
  }

  private static ArtifactId id(String prefix, char digit) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64));
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
