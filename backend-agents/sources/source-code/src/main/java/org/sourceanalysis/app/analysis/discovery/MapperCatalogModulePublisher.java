package org.sourceanalysis.app.analysis.discovery;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** Owns receipt-last persistence for one canonical MyBatis Mapper candidate catalog. */
public final class MapperCatalogModulePublisher {

  private static final String ARTIFACT_TYPE = "APPLICATION_DISCOVERY_MAPPER_CATALOG_DRAFT";
  private static final String SCHEMA_VERSION = "application-discovery-mapper-catalog-draft-v2";
  private static final String ARTIFACT_PREFIX = "mapper-catalog";
  private static final String PROFILE_ARTIFACT_TYPE =
      "APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT";
  private static final String PROFILE_SCHEMA_VERSION =
      "application-discovery-application-profile-draft-v2";
  private static final Comparator<String> UTF8_ORDER = MapperCatalogModulePublisher::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  /** Creates the publisher with the only permitted module-artifact storage dependency. */
  public MapperCatalogModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.canonicalJson = new CanonicalJsonCodec();
  }

  /** Installs one canonical M3 Mapper catalog draft and returns only its typed reopen reference. */
  public MapperCatalogDraftReference publish(
      AnalysisStepModuleAddress destination,
      ApplicationProfileDraftReference applicationProfileDraft,
      ApplicationProfile profile,
      MapperCatalogDiscovery catalog) {
    try {
      requireDestination(destination);
      Objects.requireNonNull(applicationProfileDraft, "application profile draft");
      Objects.requireNonNull(profile, "application profile");
      Objects.requireNonNull(catalog, "Mapper catalog");
      ArtifactReference profileArtifact = requireProfileDraft(applicationProfileDraft, profile);
      List<ArtifactReference> upstream =
          sortedReferences(
              List.of(
                  profileArtifact, profile.sourceInventoryRef(), profile.verifiedSnapshotRef()));
      List<String> gapRefs =
          catalog.sites().stream()
              .map(MapperCatalogSite::gapId)
              .filter(Objects::nonNull)
              .map(ArtifactId::value)
              .sorted(UTF8_ORDER)
              .toList();
      ModuleCompletionStatus status =
          gapRefs.isEmpty()
              ? ModuleCompletionStatus.SUCCEEDED
              : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  destination,
                  "v2",
                  upstream,
                  profile.controls(),
                  status,
                  gapRefs,
                  List.of(payload(destination, upstream, profile, catalog, status, gapRefs))));
      return new MapperCatalogDraftReference(installed.reference());
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("MAPPER_CATALOG_PUBLICATION_INVALID");
    }
  }

  private ArtifactReference requireProfileDraft(
      ApplicationProfileDraftReference applicationProfileDraft, ApplicationProfile profile) {
    ReopenedModulePublication reopened =
        moduleArtifacts.reopen(applicationProfileDraft.publication());
    if (!reopened.receipt().controls().equals(profile.controls())
        || reopened.payloads().size() != 1
        || !reopened.receipt().upstreamArtifacts().contains(profile.sourceInventoryRef())
        || !reopened.receipt().upstreamArtifacts().contains(profile.verifiedSnapshotRef())) {
      throw new ApplicationDiscoveryException("APPLICATION_PROFILE_REOPEN_MISMATCH");
    }
    ArtifactDescriptor descriptor = reopened.payloads().get(0).descriptor();
    if (!PROFILE_ARTIFACT_TYPE.equals(descriptor.artifactType())
        || !PROFILE_SCHEMA_VERSION.equals(descriptor.schemaVersion())) {
      throw new ApplicationDiscoveryException("APPLICATION_PROFILE_REOPEN_MISMATCH");
    }
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ApplicationProfile profile,
      MapperCatalogDiscovery catalog,
      ModuleCompletionStatus status,
      List<String> gapRefs) {
    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(address));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(profile.controls()));
    withoutArtifactId.set("completion", completion(status, gapRefs));
    withoutArtifactId.set("payload", body(profile, catalog));
    String artifactId =
        ARTIFACT_PREFIX
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode envelope = withoutArtifactId.deepCopy();
    envelope.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        "mapper-catalog-draft.json",
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private static ObjectNode body(ApplicationProfile profile, MapperCatalogDiscovery catalog) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("applicationProfileId", profile.applicationProfileId().value());
    ArrayNode entries = body.putArray("catalogEntries");
    for (MapperCatalogEntry entry : catalog.entries()) {
      ObjectNode item = entries.addObject();
      item.put("catalogEntryId", entry.catalogEntryId().value());
      item.put("javaInterfaceFqn", entry.javaInterfaceFqn());
      ArrayNode methods = item.putArray("javaMethodCandidates");
      for (MapperMethodCandidate method : entry.javaMethodCandidates()) {
        ObjectNode methodNode = methods.addObject();
        methodNode.put("methodCandidateId", method.methodCandidateId().value());
        methodNode.put("signature", method.signature());
        methodNode.set("declarationExcerpt", sourceExcerpt(method.declarationExcerpt()));
      }
      item.put("xmlResourcePath", entry.xmlResourcePath());
      item.put("xmlNamespace", entry.xmlNamespace());
      ArrayNode statements = item.putArray("xmlStatementCandidates");
      for (MapperStatementCandidate statement : entry.xmlStatementCandidates()) {
        ObjectNode statementNode = statements.addObject();
        statementNode.put("statementCandidateId", statement.statementCandidateId().value());
        statementNode.put("statementId", statement.statementId());
        statementNode.put("statementKind", statement.statementKind());
        statementNode.set("declarationExcerpt", sourceExcerpt(statement.declarationExcerpt()));
      }
      item.put("bindingState", entry.bindingState());
    }
    ArrayNode sites = body.putArray("sites");
    for (MapperCatalogSite site : catalog.sites()) {
      ObjectNode item = sites.addObject();
      item.put("siteId", site.siteId().value());
      item.put("kind", "MAPPER_RESOURCE_DECLARATION");
      item.set("primaryLocator", sourceLocator(site.primaryExcerpt()));
      item.putArray("affectedEntryIds");
      item.put("disposition", site.disposition().name());
      if (site.reasonCode() == null) {
        item.putNull("reasonCode");
      } else {
        item.put("reasonCode", site.reasonCode());
      }
      if (site.gapId() == null) {
        item.putNull("gapId");
      } else {
        item.put("gapId", site.gapId().value());
      }
      ArrayNode evidenceRefs = item.putArray("evidenceRefs");
      ObjectNode evidence = evidenceRefs.addObject();
      evidence.put("kind", "SOURCE_EXCERPT");
      evidence.set("sourceExcerpt", sourceExcerpt(site.primaryExcerpt()));
      evidence.putNull("artifactEvidence");
    }
    ArrayNode shardReceipts = body.putArray("shardReceipts");
    for (MapperCatalogShardReceipt receipt : catalog.shardReceipts()) {
      ObjectNode item = shardReceipts.addObject();
      item.put("shardId", receipt.shardId().value());
      arrayOfIds(item.putArray("denominatorSiteIds"), receipt.denominatorSiteIds());
      arrayOfIds(item.putArray("dispositionSiteIds"), receipt.dispositionSiteIds());
      item.put("status", receipt.status());
      arrayOfIds(item.putArray("gapIds"), receipt.gapIds());
    }
    ObjectNode denominator = body.putObject("denominator");
    arrayOfIds(
        denominator.putArray("catalogEntryIds"),
        catalog.entries().stream().map(MapperCatalogEntry::catalogEntryId).toList());
    arrayOfIds(
        denominator.putArray("siteIds"),
        catalog.sites().stream().map(MapperCatalogSite::siteId).toList());
    return body;
  }

  private static void arrayOfIds(ArrayNode values, List<ArtifactId> ids) {
    ids.forEach(id -> values.add(id.value()));
  }

  private static ObjectNode sourceExcerpt(SourceExcerptV1 excerpt) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.set("locator", sourceLocator(excerpt));
    node.put("rawUtf8", new String(excerpt.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8));
    node.put("rawUtf8Sha256", excerpt.rawUtf8Sha256().value());
    return node;
  }

  private static ObjectNode sourceLocator(SourceExcerptV1 excerpt) {
    ObjectNode locator = JsonNodeFactory.instance.objectNode();
    locator.put("fileId", excerpt.locator().fileId().value());
    locator.put("path", excerpt.locator().path());
    locator.put("startByte", excerpt.locator().startByte());
    locator.put("endByteExclusive", excerpt.locator().endByteExclusive());
    locator.put("startLine", excerpt.locator().startLine());
    locator.put("startColumn", excerpt.locator().startColumn());
    locator.put("endLine", excerpt.locator().endLine());
    locator.put("endColumn", excerpt.locator().endColumn());
    return locator;
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode producer = JsonNodeFactory.instance.objectNode();
    ObjectNode producerAddress = producer.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", address.runId().value());
    producerAddress.put("analysisStepKey", address.analysisStepKey().wireValue());
    producerAddress.put("moduleNumber", address.moduleNumber());
    producerAddress.put("moduleKey", address.moduleKey());
    producer.put("moduleVersion", "v2");
    return producer;
  }

  private static ArrayNode references(List<ArtifactReference> upstream) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    upstream.forEach(reference -> values.add(reference(reference)));
    return values;
  }

  private static ObjectNode reference(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static ObjectNode controls(ArtifactControls controls) {
    ObjectNode values = JsonNodeFactory.instance.objectNode();
    values.put("toolchainSha256", controls.toolchainSha256().value());
    values.put("profileSha256", controls.profileSha256().value());
    values.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) {
      values.putNull("promptBundleSha256");
    } else {
      values.put("promptBundleSha256", controls.promptBundleSha256().value());
    }
    values
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
    return values;
  }

  private static ObjectNode completion(ModuleCompletionStatus status, List<String> gapRefs) {
    ObjectNode completion = JsonNodeFactory.instance.objectNode();
    completion.put("status", status.name());
    ArrayNode values = completion.putArray("gapRefs");
    gapRefs.forEach(values::add);
    completion.putNull("failureRef");
    return completion;
  }

  private static List<ArtifactReference> sortedReferences(List<ArtifactReference> references) {
    List<ArtifactReference> sorted = new ArrayList<>(references);
    sorted.sort(Comparator.comparing(reference -> reference.artifactId().value(), UTF8_ORDER));
    if (sorted.stream().distinct().count() != sorted.size()) {
      throw new ApplicationDiscoveryException("MAPPER_CATALOG_PUBLICATION_INVALID");
    }
    return List.copyOf(sorted);
  }

  private static void requireDestination(AnalysisStepModuleAddress destination) {
    if (destination == null
        || destination.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || destination.moduleNumber() != 3
        || !"mapper-catalog".equals(destination.moduleKey())) {
      throw new ApplicationDiscoveryException("MAPPER_CATALOG_PUBLICATION_INVALID");
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }
}
