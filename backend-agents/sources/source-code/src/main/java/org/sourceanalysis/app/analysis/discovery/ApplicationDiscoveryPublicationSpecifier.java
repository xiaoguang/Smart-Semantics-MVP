package org.sourceanalysis.app.analysis.discovery;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Publishes the completed application-discovery step from its persisted M1–M3 results. */
public final class ApplicationDiscoveryPublicationSpecifier {

  private static final String PROFILE_DRAFT_TYPE =
      "APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT";
  private static final String PROFILE_DRAFT_SCHEMA =
      "application-discovery-application-profile-draft-v2";
  private static final String ENTRY_DRAFT_TYPE = "APPLICATION_DISCOVERY_HTTP_ENTRY_DISCOVERY";
  private static final String ENTRY_DRAFT_SCHEMA = "application-discovery-http-entry-discovery-v2";
  private static final String MAPPER_DRAFT_TYPE = "APPLICATION_DISCOVERY_MAPPER_CATALOG_DRAFT";
  private static final String MAPPER_DRAFT_SCHEMA = "application-discovery-mapper-catalog-draft-v2";
  private static final String PROFILE_TYPE = "APPLICATION_DISCOVERY_APPLICATION_PROFILE";
  private static final String PROFILE_SCHEMA = "application-discovery-application-profile-v2";
  private static final String CAPABILITY_TYPE = "APPLICATION_DISCOVERY_CAPABILITY_REPORT";
  private static final String CAPABILITY_SCHEMA = "application-discovery-capability-report-v2";
  private static final String ENTRY_TYPE = "APPLICATION_DISCOVERY_ENTRY_POINTS";
  private static final String ENTRY_SCHEMA = "application-discovery-entry-points-v2";
  private static final String MAPPER_TYPE = "APPLICATION_DISCOVERY_MAPPER_CATALOG";
  private static final String MAPPER_SCHEMA = "application-discovery-mapper-catalog-v2";
  private static final Comparator<String> UTF8_ORDER =
      ApplicationDiscoveryPublicationSpecifier::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore stepArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  /** Creates the path-free boundary over the trusted module and analysis-step stores. */
  public ApplicationDiscoveryPublicationSpecifier(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.stepArtifacts = Objects.requireNonNull(stepArtifacts, "analysis step artifact store");
    this.canonicalJson = new CanonicalJsonCodec();
  }

  /** Publishes the exact semantic application-discovery files from fresh-reopened module inputs. */
  public ApplicationDiscoveryReference publish(ApplicationDiscoveryPublicationRequest request) {
    try {
      requireDestination(request.destination());
      ReopenedModulePublication profile =
          moduleArtifacts.reopen(request.applicationProfile().publication());
      ReopenedModulePublication entries =
          moduleArtifacts.reopen(request.httpEntryDiscovery().publication());
      ReopenedModulePublication mapper =
          moduleArtifacts.reopen(request.mapperCatalog().publication());
      requireModule(
          profile,
          request.destination(),
          1,
          "application-profile",
          PROFILE_DRAFT_TYPE,
          PROFILE_DRAFT_SCHEMA);
      requireModule(
          entries, request.destination(), 2, "http-entry", ENTRY_DRAFT_TYPE, ENTRY_DRAFT_SCHEMA);
      requireModule(
          mapper,
          request.destination(),
          3,
          "mapper-catalog",
          MAPPER_DRAFT_TYPE,
          MAPPER_DRAFT_SCHEMA);
      requireSameControls(profile, entries, mapper);

      ObjectNode profileBody = moduleBody(profile, PROFILE_DRAFT_TYPE, PROFILE_DRAFT_SCHEMA);
      ObjectNode entryBody = moduleBody(entries, ENTRY_DRAFT_TYPE, ENTRY_DRAFT_SCHEMA);
      ObjectNode mapperBody = moduleBody(mapper, MAPPER_DRAFT_TYPE, MAPPER_DRAFT_SCHEMA);
      String applicationProfileId = text(profileBody, "applicationProfileId");
      requireSameApplicationProfile(applicationProfileId, entryBody, mapperBody);
      requireInventoryClosure(request, profile, entries, mapper);
      requireSiteAndShardClosure(entryBody, mapperBody);

      String noEntryGapId =
          array(entryBody, "entries").isEmpty()
              ? noEntryGapId(request.verifiedSourceInventory().publication(), applicationProfileId)
              : null;
      List<String> gapRefs = mergedGapRefs(entries, mapper, noEntryGapId);
      ModuleCompletionStatus status =
          gapRefs.isEmpty()
              ? ModuleCompletionStatus.SUCCEEDED
              : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
      List<CanonicalModulePayload> semanticPayloads =
          payloads(
              request,
              profile,
              entries,
              mapper,
              profileBody,
              entryBody,
              mapperBody,
              applicationProfileId,
              gapRefs,
              noEntryGapId);
      List<ArtifactReference> upstream =
          sortedReferences(
              List.of(
                  descriptorReference(profile),
                  descriptorReference(entries),
                  descriptorReference(mapper)));
      InstalledModulePublication publisher =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      request.destination().runId(),
                      AnalysisStepKey.APPLICATION_DISCOVERY,
                      4,
                      "publish"),
                  "v2",
                  upstream,
                  profile.receipt().controls(),
                  status,
                  gapRefs,
                  semanticPayloads));
      InstalledAnalysisStepPublication publication =
          stepArtifacts.install(
              new AnalysisStepInstallRequest(
                  request.destination(),
                  new AnalysisStepPublisherModuleProvenance(publisher.reference()),
                  List.of(request.verifiedSourceInventory().publication()),
                  profile.receipt().controls(),
                  status,
                  gapRefs,
                  semanticPayloads.stream().map(this::analysisStepPayload).toList(),
                  null));
      return new ApplicationDiscoveryReference(publication.reference());
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_PUBLICATION_INVALID");
    }
  }

  private List<CanonicalModulePayload> payloads(
      ApplicationDiscoveryPublicationRequest request,
      ReopenedModulePublication profile,
      ReopenedModulePublication entries,
      ReopenedModulePublication mapper,
      ObjectNode profileBody,
      ObjectNode entryBody,
      ObjectNode mapperBody,
      String applicationProfileId,
      List<String> gapRefs,
      String noEntryGapId) {
    ObjectNode finalProfile = profileBody.deepCopy();
    finalProfile.put("schemaVersion", PROFILE_SCHEMA);
    finalProfile.put("artifactType", PROFILE_TYPE);

    List<ObjectNode> entryLines = arrayObjects(entryBody, "entries");
    List<ObjectNode> mapperLines = arrayObjects(mapperBody, "catalogEntries");
    for (ObjectNode entry : entryLines) {
      entry.put("schemaVersion", "application-discovery-entry-point-v2");
    }
    for (ObjectNode catalogEntry : mapperLines) {
      catalogEntry.put("schemaVersion", "application-discovery-mapper-catalog-entry-v2");
    }

    ObjectNode capability = JsonNodeFactory.instance.objectNode();
    capability.put("schemaVersion", CAPABILITY_SCHEMA);
    capability.put("artifactType", CAPABILITY_TYPE);
    capability.put("applicationProfileId", applicationProfileId);
    capability.set("applicationProfileDraftRef", referenceNode(descriptorReference(profile)));
    capability.set("httpEntryDiscoveryDraftRef", referenceNode(descriptorReference(entries)));
    capability.set("mapperCatalogDraftRef", referenceNode(descriptorReference(mapper)));
    capability.set(
        "verifiedSourceInventoryPublicationRef",
        analysisStepReferenceNode(request.verifiedSourceInventory().publication()));
    capability.set("httpEntrySites", entryBody.get("sites").deepCopy());
    capability.set("mapperCatalogSites", mapperBody.get("sites").deepCopy());
    capability.set("httpEntryShardReceipts", entryBody.get("shardReceipts").deepCopy());
    capability.set("mapperCatalogShardReceipts", mapperBody.get("shardReceipts").deepCopy());
    ObjectNode coverage = capability.putObject("repositoryEntryCoverage");
    coverage.set("entryIds", ids(entryLines, "entryId"));
    coverage.set("mapperCatalogEntryIds", ids(mapperLines, "catalogEntryId"));
    coverage.put("entryCount", entryLines.size());
    coverage.put("mapperCatalogEntryCount", mapperLines.size());
    coverage.put("httpEntrySiteCount", array(entryBody, "sites").size());
    coverage.put("mapperCatalogSiteCount", array(mapperBody, "sites").size());
    coverage.put("noEntryDiscovered", entryLines.isEmpty());
    ArrayNode capabilityGaps = capability.putArray("gapRefs");
    gapRefs.forEach(capabilityGaps::add);
    if (noEntryGapId == null) {
      capability.putNull("noEntryDisposition");
    } else {
      ObjectNode noEntry = capability.putObject("noEntryDisposition");
      noEntry.put("gapId", noEntryGapId);
      noEntry.put("reasonCode", "NO_ENTRY_DISCOVERED");
      noEntry.set(
          "sourceInventoryPublicationRef",
          analysisStepReferenceNode(request.verifiedSourceInventory().publication()));
    }

    return List.of(
        standalone(
            "application-profile.json",
            PROFILE_TYPE,
            PROFILE_SCHEMA,
            "application-profile",
            finalProfile),
        standalone(
            "capability-report.json",
            CAPABILITY_TYPE,
            CAPABILITY_SCHEMA,
            "capability-report",
            capability),
        jsonl("entry-points.jsonl", ENTRY_TYPE, ENTRY_SCHEMA, "entry-points", entryLines),
        jsonl("mapper-catalog.jsonl", MAPPER_TYPE, MAPPER_SCHEMA, "mapper-catalog", mapperLines));
  }

  private CanonicalModulePayload standalone(
      String fileName, String artifactType, String schemaVersion, String prefix, ObjectNode body) {
    ObjectNode withoutArtifactId = body.deepCopy();
    withoutArtifactId.remove("artifactId");
    String artifactId =
        prefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode document = withoutArtifactId.deepCopy();
    document.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(document));
  }

  private CanonicalModulePayload jsonl(
      String fileName,
      String artifactType,
      String schemaVersion,
      String prefix,
      List<ObjectNode> entries) {
    StringBuilder lines = new StringBuilder();
    for (ObjectNode entry : entries) {
      lines.append(
          new String(
              canonicalJson.encodeCanonical(entry).copyToByteArray(), StandardCharsets.UTF_8));
      lines.append('\n');
    }
    ImmutableBytes bytes = ImmutableBytes.copyOf(lines.toString().getBytes(StandardCharsets.UTF_8));
    String artifactId =
        prefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(bytes.copyToByteArray())));
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes);
  }

  private void requireDestination(AnalysisStepPublicationAddress destination) {
    if (destination == null
        || destination.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_PUBLICATION_INVALID");
    }
  }

  private void requireModule(
      ReopenedModulePublication publication,
      AnalysisStepPublicationAddress destination,
      int moduleNumber,
      String moduleKey,
      String artifactType,
      String schemaVersion) {
    if (!(publication.reference().address() instanceof AnalysisStepModuleAddress address)
        || !address.runId().equals(destination.runId())
        || address.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || address.moduleNumber() != moduleNumber
        || !moduleKey.equals(address.moduleKey())
        || publication.payloads().size() != 1) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!artifactType.equals(payload.descriptor().artifactType())
        || !schemaVersion.equals(payload.descriptor().schemaVersion())) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
    }
  }

  private static void requireSameControls(
      ReopenedModulePublication first, ReopenedModulePublication... rest) {
    for (ReopenedModulePublication candidate : rest) {
      if (!first.receipt().controls().equals(candidate.receipt().controls())) {
        throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
      }
    }
  }

  private ObjectNode moduleBody(
      ReopenedModulePublication publication, String artifactType, String schemaVersion) {
    ObjectNode envelope =
        canonicalJson.parseCanonical(publication.payloads().get(0).canonicalUtf8()).deepCopy();
    if (!artifactType.equals(text(envelope, "artifactType"))
        || !schemaVersion.equals(text(envelope, "schemaVersion"))
        || !publication
            .payloads()
            .get(0)
            .descriptor()
            .artifactId()
            .value()
            .equals(text(envelope, "artifactId"))
        || !(envelope.get("payload") instanceof ObjectNode body)) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
    }
    return body.deepCopy();
  }

  private static void requireSameApplicationProfile(
      String applicationProfileId, ObjectNode entries, ObjectNode mapper) {
    if (!applicationProfileId.equals(text(entries, "applicationProfileId"))
        || !applicationProfileId.equals(text(mapper, "applicationProfileId"))) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
    }
  }

  private static void requireInventoryClosure(
      ApplicationDiscoveryPublicationRequest request,
      ReopenedModulePublication profile,
      ReopenedModulePublication entries,
      ReopenedModulePublication mapper) {
    ArtifactReference inventory =
        profile.receipt().upstreamArtifacts().stream()
            .filter(
                reference ->
                    reference
                        .artifactId()
                        .value()
                        .startsWith("verified-source-inventory-source-inventory:"))
            .findFirst()
            .orElseThrow(
                () -> new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID"));
    ArtifactReference snapshot =
        profile.receipt().upstreamArtifacts().stream()
            .filter(reference -> reference.artifactId().value().startsWith("verified-snapshot:"))
            .findFirst()
            .orElseThrow(
                () -> new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID"));
    for (ReopenedModulePublication publication : List.of(entries, mapper)) {
      if (!publication.receipt().upstreamArtifacts().contains(inventory)
          || !publication.receipt().upstreamArtifacts().contains(snapshot)) {
        throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
      }
    }
    if (request.verifiedSourceInventory().publication().address().analysisStepKey()
        != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
    }
  }

  private static void requireSiteAndShardClosure(ObjectNode entries, ObjectNode mapper) {
    requireShardClosure(entries, "sites", "shardReceipts");
    requireShardClosure(mapper, "sites", "shardReceipts");
  }

  private static void requireShardClosure(ObjectNode body, String sitesField, String shardsField) {
    Set<String> sites = values(array(body, sitesField), "siteId");
    Set<String> seen = new HashSet<>();
    for (JsonNode shardValue : array(body, shardsField)) {
      ObjectNode shard = object(shardValue);
      for (JsonNode siteId : array(shard, "denominatorSiteIds")) {
        if (!siteId.isTextual() || !seen.add(siteId.textValue())) {
          throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_ACCOUNTING_INVALID");
        }
      }
      if (!sameStrings(array(shard, "denominatorSiteIds"), array(shard, "dispositionSiteIds"))) {
        throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_ACCOUNTING_INVALID");
      }
    }
    if (!seen.equals(sites)) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_ACCOUNTING_INVALID");
    }
  }

  private static List<String> mergedGapRefs(
      ReopenedModulePublication entries, ReopenedModulePublication mapper, String noEntryGapId) {
    List<String> gaps = new ArrayList<>();
    gaps.addAll(entries.receipt().gapRefs());
    gaps.addAll(mapper.receipt().gapRefs());
    if (noEntryGapId != null) {
      gaps.add(noEntryGapId);
    }
    gaps.sort(UTF8_ORDER);
    String previous = null;
    for (String gap : gaps) {
      if (previous != null && previous.equals(gap)) {
        throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_ACCOUNTING_INVALID");
      }
      previous = gap;
    }
    return List.copyOf(gaps);
  }

  private static String noEntryGapId(
      AnalysisStepPublicationReference sourceInventory, String applicationProfileId) {
    return "gap:"
        + sha256(
            concatenate(
                frame("application-discovery-no-entry-gap-v1"),
                frame(applicationProfileId),
                frame(sourceInventory.analysisStepArtifactRoot().value())));
  }

  private CanonicalAnalysisStepPayload analysisStepPayload(CanonicalModulePayload payload) {
    return new CanonicalAnalysisStepPayload(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8());
  }

  private static ArtifactReference descriptorReference(ReopenedModulePublication publication) {
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static List<ArtifactReference> sortedReferences(List<ArtifactReference> values) {
    List<ArtifactReference> sorted = new ArrayList<>(values);
    sorted.sort(Comparator.comparing(reference -> reference.artifactId().value(), UTF8_ORDER));
    if (sorted.stream().distinct().count() != sorted.size()) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
    }
    return List.copyOf(sorted);
  }

  private static ArrayNode ids(List<ObjectNode> items, String fieldName) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    items.stream().map(item -> text(item, fieldName)).sorted(UTF8_ORDER).forEach(values::add);
    return values;
  }

  private static ObjectNode referenceNode(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static ObjectNode analysisStepReferenceNode(AnalysisStepPublicationReference reference) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("analysisStepKey", reference.address().analysisStepKey().wireValue());
    node.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    node.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    node.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
    return node;
  }

  private static List<ObjectNode> arrayObjects(ObjectNode node, String fieldName) {
    List<ObjectNode> objects = new ArrayList<>();
    for (JsonNode item : array(node, fieldName)) {
      objects.add(object(item).deepCopy());
    }
    return objects;
  }

  private static Set<String> values(ArrayNode values, String fieldName) {
    Set<String> result = new HashSet<>();
    for (JsonNode value : values) {
      String item = text(object(value), fieldName);
      if (!result.add(item)) {
        throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_ACCOUNTING_INVALID");
      }
    }
    return result;
  }

  private static boolean sameStrings(ArrayNode first, ArrayNode second) {
    if (first.size() != second.size()) {
      return false;
    }
    for (int index = 0; index < first.size(); index++) {
      if (!first.get(index).equals(second.get(index))) {
        return false;
      }
    }
    return true;
  }

  private static ObjectNode object(JsonNode node) {
    if (node instanceof ObjectNode object) {
      return object;
    }
    throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
  }

  private static ArrayNode array(ObjectNode node, String fieldName) {
    if (node.get(fieldName) instanceof ArrayNode array) {
      return array;
    }
    throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
  }

  private static String text(ObjectNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_UPSTREAM_INVALID");
    }
    return value.textValue();
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
