package org.sourceanalysis.app.analysis.inventory;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
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
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** M3 projection from verified M1/M2 module publications to the public source inventory set. */
final class VerifiedSourceInventoryPublicationSpecifier {

  private static final String SOURCE_INPUT_TYPE = "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT";
  private static final String SOURCE_INPUT_SCHEMA = "verified-source-inventory-source-input-v2";
  private static final String INVENTORY_TYPE = "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY";
  private static final String INVENTORY_SCHEMA = "verified-source-inventory-source-inventory-v2";
  private static final String SNAPSHOT_TYPE = "VERIFIED_SNAPSHOT";
  private static final String SNAPSHOT_SCHEMA = "verified-snapshot-v2";
  private static final String ADMITTED_TYPE = "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST";
  private static final String ADMITTED_SCHEMA =
      "verified-source-inventory-admitted-source-request-v2";
  private static final String INDEX_TYPE = "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX";
  private static final String INDEX_SCHEMA = "verified-source-inventory-verified-source-index-v2";
  private static final Comparator<String> UTF8_ORDER =
      Comparator.comparing(
          value -> value.getBytes(StandardCharsets.UTF_8),
          VerifiedSourceInventoryPublicationSpecifier::compareBytes);

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;
  private final AnalysisInputArtifactReader inputs;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  VerifiedSourceInventoryPublicationSpecifier(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      AnalysisInputArtifactReader inputs) {
    this.modules = require(modules);
    this.steps = require(steps);
    this.inputs = require(inputs);
  }

  VerifiedSourceInventoryReference publish(
      VerifiedSourceInventoryPublicationSpecificationInputV1 specification) {
    try {
      requireDestination(specification.destination());
      ReopenedModulePublication admittedPublication =
          modules.reopen(specification.admittedSourceRequestPublication());
      ReopenedModulePublication indexPublication =
          modules.reopen(specification.verifiedSourceIndexPublication());
      requireAddress(admittedPublication, specification.destination(), 1, "request-admission");
      requireAddress(indexPublication, specification.destination(), 2, "source-index");
      requireSameControls(admittedPublication, indexPublication);

      RunRequest runRequest = readRunRequest(specification.analysisRunRequestRef());
      FrozenRequest frozenRequest = readFrozenRequest(specification.frozenRepositoryRequestRef());
      Admitted admitted = readAdmitted(admittedPublication);
      Indexed indexed = readIndex(indexPublication);
      requireClosure(
          specification, runRequest, frozenRequest, admittedPublication, admitted, indexed);

      List<CanonicalModulePayload> payloads =
          payloads(specification, runRequest, frozenRequest, admitted, indexed);
      List<ArtifactReference> upstream =
          List.of(
                  descriptorReference(admittedPublication),
                  descriptorReference(indexPublication),
                  specification.analysisRunRequestRef(),
                  specification.frozenRepositoryRequestRef())
              .stream()
              .sorted(Comparator.comparing(reference -> reference.artifactId().value(), UTF8_ORDER))
              .toList();
      ArtifactControls controls = admittedPublication.receipt().controls();
      InstalledModulePublication publisher =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      specification.destination().runId(),
                      AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
                      3,
                      "publish"),
                  "v2",
                  upstream,
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  payloads));
      InstalledAnalysisStepPublication publication =
          steps.install(
              new AnalysisStepInstallRequest(
                  specification.destination(),
                  new AnalysisStepPublisherModuleProvenance(publisher.reference()),
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  payloads.stream().map(this::analysisStepPayload).toList(),
                  null));
      return new VerifiedSourceInventoryReference(publication.reference());
    } catch (VerifiedSourceInventoryPublicationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("VERIFIED_SOURCE_INVENTORY_PUBLICATION_INVALID");
    }
  }

  private List<CanonicalModulePayload> payloads(
      VerifiedSourceInventoryPublicationSpecificationInputV1 specification,
      RunRequest runRequest,
      FrozenRequest frozenRequest,
      Admitted admitted,
      Indexed indexed) {
    ObjectNode sourceInput = JsonNodeFactory.instance.objectNode();
    sourceInput.put("schemaVersion", SOURCE_INPUT_SCHEMA);
    sourceInput.put("artifactType", SOURCE_INPUT_TYPE);
    sourceInput.put(
        "sourceInputId",
        sourceInputId(specification, admitted.sourceRegistrationId(), indexed.snapshotId()));
    sourceInput.put("sourceRegistrationId", admitted.sourceRegistrationId().value());
    sourceInput.set(
        "frozenRepositoryRequestRef", referenceNode(specification.frozenRepositoryRequestRef()));
    sourceInput.set("captureReceiptRef", referenceNode(frozenRequest.captureReceiptRef()));
    sourceInput.set("snapshotManifestRef", referenceNode(frozenRequest.snapshotManifestRef()));
    sourceInput.set("inventoryScope", scopeNode(frozenRequest.scope()));
    sourceInput.put("repositoryCompletionEligible", admitted.repositoryCompletionEligible());
    sourceInput.set("profileBundleRef", referenceNode(runRequest.profileBundleRef()));
    sourceInput.set("resourceBudgetRef", referenceNode(runRequest.resourceBudgetRef()));
    sourceInput.set("toolchainRef", referenceNode(runRequest.toolchainRef()));
    sourceInput.set("schemaBundleRef", referenceNode(runRequest.schemaBundleRef()));
    sourceInput.set("promptBundleRef", referenceNode(runRequest.promptBundleRef()));
    sourceInput.set(
        "artifactPolicyRegistryRef", referenceNode(runRequest.artifactPolicyRegistryRef()));

    ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
    snapshot.put("schemaVersion", SNAPSHOT_SCHEMA);
    snapshot.put("artifactType", SNAPSHOT_TYPE);
    snapshot.put("snapshotId", indexed.snapshotId());
    snapshot.put("declaredRepositoryIdentity", admitted.repositoryUrl());
    snapshot.put("objectFormat", "SHA1");
    snapshot.put("originRevision", admitted.revision());
    snapshot.set("captureReceiptRef", referenceNode(frozenRequest.captureReceiptRef()));
    snapshot.set("snapshotManifestRef", referenceNode(frozenRequest.snapshotManifestRef()));
    snapshot.set("inventoryScope", scopeNode(frozenRequest.scope()));
    snapshot.put("repositoryCompletionEligible", admitted.repositoryCompletionEligible());
    snapshot.set("verificationPolicyRef", referenceNode(frozenRequest.verificationPolicyRef()));
    snapshot.set("capabilityProfileRef", referenceNode(frozenRequest.capabilityProfileRef()));
    snapshot.set("resourceBudgetRef", referenceNode(frozenRequest.resourceBudgetRef()));
    snapshot.put("trackedRegularFileCount", admitted.files().size());
    snapshot.put("verifiedRegularFileCount", indexed.files().size());
    snapshot.put("unverifiedRegularFileCount", 0);
    snapshot.put("analyzableTextFileCount", indexed.textCount());
    snapshot.put("nonAnalyzableMediaFileCount", indexed.mediaCount());
    snapshot.set("trackedRegularFileIds", ids(indexed.files()));
    snapshot.set("verifiedRegularFileIds", ids(indexed.files()));
    snapshot.putArray("unverifiedRegularFileIds");
    snapshot.set("analyzableTextFileIds", ids(indexed.textFiles()));
    snapshot.set("nonAnalyzableMediaFileIds", ids(indexed.mediaFiles()));
    snapshot.set("shardReceipts", indexed.shards().deepCopy());
    ObjectNode accounting = snapshot.putObject("accountingProof");
    accounting.put("trackedEqualsVerifiedUnionUnverified", true);
    accounting.put("verifiedEqualsAnalyzableTextUnionNonAnalyzableMedia", true);
    snapshot.put("sourceIntegrity", "VERIFIED");

    return List.of(
        standalone(
            "source-input.json",
            SOURCE_INPUT_TYPE,
            SOURCE_INPUT_SCHEMA,
            "verified-source-inventory-source-input",
            sourceInput),
        inventory(indexed.files()),
        standalone(
            "verified-snapshot.json",
            SNAPSHOT_TYPE,
            SNAPSHOT_SCHEMA,
            "verified-snapshot",
            snapshot));
  }

  private CanonicalModulePayload standalone(
      String fileName, String type, String schema, String prefix, ObjectNode withoutArtifactId) {
    String id =
        prefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(json.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode document = withoutArtifactId.deepCopy();
    document.put("artifactId", id);
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(document));
  }

  private CanonicalModulePayload inventory(List<File> files) {
    StringBuilder lines = new StringBuilder();
    for (File file : files) {
      ObjectNode entry = JsonNodeFactory.instance.objectNode();
      entry.put("schemaVersion", "source-inventory-entry-v2");
      entry.put("fileId", file.id().value());
      entry.put("path", file.path());
      entry.put("gitMode", file.gitMode());
      entry.put("mediaType", file.mediaType());
      entry.put("sizeBytes", file.sizeBytes());
      entry.put("sha256", file.sha256().value());
      entry.put("analysisDisposition", file.disposition());
      if (file.encoding() == null) {
        entry.putNull("textEncoding");
        entry.putNull("lineIndexDigest");
      } else {
        entry.put("textEncoding", file.encoding());
        entry.put("lineIndexDigest", file.lineIndexDigest().value());
      }
      lines.append(
          new String(json.encodeCanonical(entry).copyToByteArray(), StandardCharsets.UTF_8));
      lines.append('\n');
    }
    ImmutableBytes bytes = ImmutableBytes.copyOf(lines.toString().getBytes(StandardCharsets.UTF_8));
    String id =
        "verified-source-inventory-source-inventory:"
            + sha256(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(INVENTORY_SCHEMA),
                    frame(INVENTORY_TYPE),
                    frame(bytes.copyToByteArray())));
    return new CanonicalModulePayload(
        "source-inventory.jsonl",
        INVENTORY_TYPE,
        INVENTORY_SCHEMA,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes);
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

  private RunRequest readRunRequest(ArtifactReference reference) {
    ImmutableBytes bytes = input(reference, "run-request");
    ObjectNode node = object(bytes);
    requireFields(
        node,
        Set.of(
            "approvedFindingRefs",
            "artifactPolicyRegistryRef",
            "candidateSeriesRef",
            "frozenRepositoryRequestRef",
            "organizationRegistrySeedRef",
            "parentCandidateRef",
            "profileBundleRef",
            "promptBundleRef",
            "readerCandidateRound",
            "resourceBudgetRef",
            "schemaBundleRef",
            "schemaVersion",
            "sourceRegistrationId",
            "toolchainRef"));
    require(node, "schemaVersion", "analysis-run-request-v2");
    if (!"ROUND_1".equals(text(node, "readerCandidateRound"))
        || !node.get("organizationRegistrySeedRef").isNull()
        || !node.get("parentCandidateRef").isNull()
        || !(node.get("approvedFindingRefs") instanceof ArrayNode array)
        || !array.isEmpty()
        || !reference
            .artifactId()
            .value()
            .equals(
                "run-request:"
                    + sha256(
                        concatenate(
                            frame("analysis-run-request-id-v2"),
                            frame(bytes.copyToByteArray()))))) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_CLOSURE_INVALID");
    }
    return new RunRequest(
        artifactId(node, "sourceRegistrationId"),
        reference(node, "frozenRepositoryRequestRef"),
        reference(node, "profileBundleRef"),
        reference(node, "resourceBudgetRef"),
        reference(node, "toolchainRef"),
        reference(node, "schemaBundleRef"),
        reference(node, "promptBundleRef"),
        reference(node, "artifactPolicyRegistryRef"));
  }

  private FrozenRequest readFrozenRequest(ArtifactReference reference) {
    ObjectNode node = object(input(reference, "frozen-request"));
    requireFields(
        node,
        Set.of(
            "schemaVersion",
            "expectedOrigin",
            "captureReceiptRef",
            "snapshotManifestRef",
            "inventoryScope",
            "verificationPolicyRef",
            "capabilityProfileRef",
            "resourceBudgetRef"));
    require(node, "schemaVersion", "frozen-repository-request-v2");
    ObjectNode origin = object(node, "expectedOrigin");
    requireFields(origin, Set.of("kind", "repositoryUrl", "revision40"));
    require(origin, "kind", "GIT_SHA1_COMMIT");
    ObjectNode scope = object(node, "inventoryScope");
    requireFields(scope, Set.of("kind", "scopeRoot", "declaredPathCount"));
    require(scope, "kind", "COMPLETE_CAPTURE");
    if (!scope.get("scopeRoot").isNull()) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    return new FrozenRequest(
        text(origin, "repositoryUrl"),
        sha1(origin, "revision40"),
        new Scope("COMPLETE_CAPTURE", positive(scope, "declaredPathCount")),
        reference(node, "captureReceiptRef"),
        reference(node, "snapshotManifestRef"),
        reference(node, "verificationPolicyRef"),
        reference(node, "capabilityProfileRef"),
        reference(node, "resourceBudgetRef"));
  }

  private Admitted readAdmitted(ReopenedModulePublication publication) {
    ObjectNode node = moduleBody(exactPayload(publication, ADMITTED_TYPE, ADMITTED_SCHEMA));
    requireFields(
        node,
        Set.of(
            "requestIdentity",
            "sourceRegistrationId",
            "originRepositoryUrl",
            "originRevision",
            "inventoryScope",
            "repositoryCompletionEligible",
            "declaredPathCount",
            "files"));
    ObjectNode scope = object(node, "inventoryScope");
    requireFields(scope, Set.of("kind", "scopeRoot"));
    require(scope, "kind", "COMPLETE_CAPTURE");
    if (!scope.get("scopeRoot").isNull()) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
    int count = positive(node, "declaredPathCount");
    if (!(node.get("files") instanceof ArrayNode files) || files.size() != count) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
    List<File> parsed = new ArrayList<>();
    for (JsonNode value : files) {
      ObjectNode file = object(value);
      requireFields(
          file,
          Set.of(
              "path",
              "gitMode",
              "mediaType",
              "sizeBytes",
              "sha256",
              "analysisDisposition",
              "textEncoding"));
      parsed.add(
          new File(
              null,
              path(file),
              text(file, "gitMode"),
              text(file, "mediaType"),
              nonnegative(file, "sizeBytes"),
              digest(file, "sha256"),
              text(file, "analysisDisposition"),
              nullableText(file, "textEncoding"),
              null));
    }
    requirePathOrder(parsed);
    return new Admitted(
        artifactId(node, "sourceRegistrationId"),
        text(node, "originRepositoryUrl"),
        sha1(node, "originRevision"),
        bool(node, "repositoryCompletionEligible"),
        parsed);
  }

  private Indexed readIndex(ReopenedModulePublication publication) {
    ObjectNode node = moduleBody(exactPayload(publication, INDEX_TYPE, INDEX_SCHEMA));
    requireFields(
        node,
        Set.of(
            "snapshotId",
            "requestArtifactId",
            "verifiedRegularFileCount",
            "analyzableTextFileCount",
            "nonAnalyzableMediaFileCount",
            "verifiedFiles",
            "shardReceipts",
            "sourceIntegrity"));
    String snapshotId = text(node, "snapshotId");
    if (!snapshotId.matches("snapshot:[0-9a-f]{64}")
        || !"VERIFIED".equals(text(node, "sourceIntegrity"))) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
    int verified = positive(node, "verifiedRegularFileCount");
    int text = nonnegativeInt(node, "analyzableTextFileCount");
    int media = nonnegativeInt(node, "nonAnalyzableMediaFileCount");
    if (!(node.get("verifiedFiles") instanceof ArrayNode files)
        || files.size() != verified
        || text + media != verified) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
    List<File> parsed = new ArrayList<>();
    for (JsonNode value : files) {
      ObjectNode file = object(value);
      requireFields(
          file,
          Set.of(
              "fileId",
              "path",
              "gitMode",
              "mediaType",
              "sizeBytes",
              "sha256",
              "analysisDisposition",
              "textEncoding",
              "lineIndexDigest"));
      String disposition = text(file, "analysisDisposition");
      String encoding = nullableText(file, "textEncoding");
      String lineIndex = nullableText(file, "lineIndexDigest");
      if (("ANALYZABLE_TEXT".equals(disposition)
              && (!"UTF-8".equals(encoding) || lineIndex == null))
          || ("NON_ANALYZABLE_MEDIA".equals(disposition)
              && (encoding != null || lineIndex != null))) {
        throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
      }
      parsed.add(
          new File(
              artifactId(file, "fileId"),
              path(file),
              text(file, "gitMode"),
              text(file, "mediaType"),
              nonnegative(file, "sizeBytes"),
              digest(file, "sha256"),
              disposition,
              encoding,
              lineIndex == null ? null : Sha256Digest.parse(lineIndex)));
    }
    requirePathOrder(parsed);
    ArrayNode shards = array(node, "shardReceipts");
    requireShards(shards, parsed);
    return new Indexed(
        snapshotId, artifactId(node, "requestArtifactId"), text, media, parsed, shards);
  }

  private void requireClosure(
      VerifiedSourceInventoryPublicationSpecificationInputV1 specification,
      RunRequest run,
      FrozenRequest frozen,
      ReopenedModulePublication admittedPublication,
      Admitted admitted,
      Indexed indexed) {
    if (!run.frozenRequest().equals(specification.frozenRepositoryRequestRef())
        || !run.sourceRegistrationId().equals(admitted.sourceRegistrationId())
        || !run.resourceBudgetRef().equals(frozen.resourceBudgetRef())
        || !frozen.repositoryUrl().equals(admitted.repositoryUrl())
        || !frozen.revision().equals(admitted.revision())
        || frozen.scope().count() != admitted.files().size()
        || !indexed.requestArtifactId().equals(soleDescriptor(admittedPublication).artifactId())) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_CLOSURE_INVALID");
    }
    Map<String, File> byPath = new HashMap<>();
    admitted.files().forEach(file -> byPath.put(file.path(), file));
    for (File verified : indexed.files()) {
      File original = byPath.remove(verified.path());
      if (original == null || !original.matches(verified)) {
        throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_CLOSURE_INVALID");
      }
    }
    if (!byPath.isEmpty()) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_CLOSURE_INVALID");
    }
  }

  private static void requireDestination(AnalysisStepPublicationAddress address) {
    if (address == null || address.analysisStepKey() != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY) {
      throw failure("VERIFIED_SOURCE_INVENTORY_PUBLICATION_INPUT_INVALID");
    }
  }

  private static void requireAddress(
      ReopenedModulePublication publication,
      AnalysisStepPublicationAddress destination,
      int moduleNumber,
      String moduleKey) {
    if (!(publication.reference().address() instanceof AnalysisStepModuleAddress address)
        || !address.runId().equals(destination.runId())
        || address.analysisStepKey() != destination.analysisStepKey()
        || address.moduleNumber() != moduleNumber
        || !address.moduleKey().equals(moduleKey)) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
  }

  private static void requireSameControls(
      ReopenedModulePublication first, ReopenedModulePublication second) {
    if (!first.receipt().controls().equals(second.receipt().controls())
        || first.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || second.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || !first.receipt().gapRefs().isEmpty()
        || !second.receipt().gapRefs().isEmpty()) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
  }

  private ImmutableBytes input(ArtifactReference reference, String prefix) {
    if (reference == null || !reference.artifactId().value().startsWith(prefix + ":")) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    ImmutableBytes bytes = inputs.reopen(reference);
    if (bytes == null || !sha256(bytes.copyToByteArray()).equals(reference.sha256().value())) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_CLOSURE_INVALID");
    }
    return bytes;
  }

  private static VerifiedCanonicalPayload exactPayload(
      ReopenedModulePublication publication, String type, String schema) {
    if (publication.payloads().size() != 1) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!type.equals(payload.descriptor().artifactType())
        || !schema.equals(payload.descriptor().schemaVersion())) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
    return payload;
  }

  private static ObjectNode moduleBody(VerifiedCanonicalPayload payload) {
    ObjectNode envelope = object(payload.canonicalUtf8());
    return object(envelope, "payload");
  }

  private static ArtifactReference descriptorReference(ReopenedModulePublication publication) {
    ArtifactDescriptor descriptor = soleDescriptor(publication);
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private static ArtifactDescriptor soleDescriptor(ReopenedModulePublication publication) {
    if (publication.payloads().size() != 1) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
    return publication.payloads().get(0).descriptor();
  }

  private String sourceInputId(
      VerifiedSourceInventoryPublicationSpecificationInputV1 specification,
      ArtifactId sourceRegistrationId,
      String snapshotId) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("runId", specification.destination().runId().value());
    material.put("sourceRegistrationId", sourceRegistrationId.value());
    material.put("snapshotId", snapshotId);
    material.set("analysisRunRequestRef", referenceNode(specification.analysisRunRequestRef()));
    material.set(
        "frozenRepositoryRequestRef", referenceNode(specification.frozenRepositoryRequestRef()));
    return "source-input:"
        + sha256(
            concatenate(
                frame("verified-source-inventory-source-input-id-v2"),
                frame(json.encodeCanonical(material).copyToByteArray())));
  }

  private static ArrayNode ids(List<File> files) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    files.stream().map(file -> file.id().value()).sorted(UTF8_ORDER).forEach(result::add);
    return result;
  }

  private static ObjectNode scopeNode(Scope scope) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("kind", scope.kind());
    result.putNull("scopeRoot");
    return result;
  }

  private static ObjectNode referenceNode(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static void requireShards(ArrayNode shards, List<File> files) {
    if (shards.isEmpty()) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
    Set<String> expected = new HashSet<>();
    files.forEach(file -> expected.add(file.id().value()));
    Set<String> denominator = new HashSet<>();
    Set<String> verified = new HashSet<>();
    for (JsonNode node : shards) {
      ObjectNode shard = object(node);
      requireFields(
          shard, Set.of("shardId", "denominatorFileIds", "verifiedFileIds", "status", "gapIds"));
      if (!text(shard, "shardId").matches("source-shard:[0-9a-f]{64}")
          || !"SUCCEEDED".equals(text(shard, "status"))
          || !array(shard, "gapIds").isEmpty()) {
        throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
      }
      uniqueIds(array(shard, "denominatorFileIds"), denominator);
      uniqueIds(array(shard, "verifiedFileIds"), verified);
    }
    if (!expected.equals(denominator) || !expected.equals(verified)) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
  }

  private static void uniqueIds(ArrayNode values, Set<String> target) {
    String previous = null;
    for (JsonNode value : values) {
      if (!value.isTextual()
          || !value.textValue().matches("file:[0-9a-f]{64}")
          || (previous != null && UTF8_ORDER.compare(previous, value.textValue()) >= 0)
          || !target.add(value.textValue())) {
        throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
      }
      previous = value.textValue();
    }
  }

  private static void requirePathOrder(List<File> files) {
    String previous = null;
    for (File file : files) {
      if (file.path().startsWith("/")
          || file.path().indexOf('\\') >= 0
          || (previous != null && UTF8_ORDER.compare(previous, file.path()) >= 0)) {
        throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
      }
      previous = file.path();
    }
  }

  private static ObjectNode object(ImmutableBytes bytes) {
    try {
      JsonNode node = new CanonicalJsonCodec().parseCanonical(bytes);
      return object(node);
    } catch (RuntimeException failure) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
  }

  private static ObjectNode object(ObjectNode parent, String field) {
    return object(parent.get(field));
  }

  private static ObjectNode object(JsonNode node) {
    if (!(node instanceof ObjectNode object)) {
      throw failure("VERIFIED_SOURCE_INVENTORY_UPSTREAM_INVALID");
    }
    return object;
  }

  private static ArrayNode array(ObjectNode node, String field) {
    if (!(node.get(field) instanceof ArrayNode array)) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    return array;
  }

  private static void requireFields(ObjectNode object, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
  }

  private static void require(ObjectNode object, String field, String expected) {
    if (!expected.equals(text(object, field))) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
  }

  private static String text(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isTextual()) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    return value.textValue();
  }

  private static String nullableText(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || (!value.isNull() && !value.isTextual())) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    return value.isNull() ? null : value.textValue();
  }

  private static boolean bool(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isBoolean()) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    return value.booleanValue();
  }

  private static long nonnegative(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null
        || !value.isIntegralNumber()
        || !value.canConvertToLong()
        || value.longValue() < 0) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    return value.longValue();
  }

  private static int nonnegativeInt(ObjectNode object, String field) {
    long value = nonnegative(object, field);
    if (value > Integer.MAX_VALUE) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    return (int) value;
  }

  private static int positive(ObjectNode object, String field) {
    int value = nonnegativeInt(object, field);
    if (value == 0) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    return value;
  }

  private static ArtifactId artifactId(ObjectNode object, String field) {
    try {
      return ArtifactId.parse(text(object, field));
    } catch (IllegalArgumentException invalid) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
  }

  private static Sha256Digest digest(ObjectNode object, String field) {
    try {
      return Sha256Digest.parse(text(object, field));
    } catch (IllegalArgumentException invalid) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
  }

  private static ArtifactReference reference(ObjectNode object, String field) {
    ObjectNode reference = object(object, field);
    requireFields(reference, Set.of("artifactId", "sha256"));
    return new ArtifactReference(artifactId(reference, "artifactId"), digest(reference, "sha256"));
  }

  private static String sha1(ObjectNode object, String field) {
    String value = text(object, field);
    if (!value.matches("[0-9a-f]{40}")) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    return value;
  }

  private static String path(ObjectNode object) {
    String value = text(object, "path");
    if (value.isBlank() || value.startsWith("/") || value.indexOf('\\') >= 0) {
      throw failure("VERIFIED_SOURCE_INVENTORY_INPUT_INVALID");
    }
    return value;
  }

  private static <T> T require(T value) {
    if (value == null) {
      throw new IllegalArgumentException("required dependency");
    }
    return value;
  }

  private static int compareBytes(byte[] first, byte[] second) {
    int common = Math.min(first.length, second.length);
    for (int index = 0; index < common; index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(first.length, second.length);
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

  private static VerifiedSourceInventoryPublicationException failure(String code) {
    return new VerifiedSourceInventoryPublicationException(code);
  }

  private record RunRequest(
      ArtifactId sourceRegistrationId,
      ArtifactReference frozenRequest,
      ArtifactReference profileBundleRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference toolchainRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference promptBundleRef,
      ArtifactReference artifactPolicyRegistryRef) {}

  private record FrozenRequest(
      String repositoryUrl,
      String revision,
      Scope scope,
      ArtifactReference captureReceiptRef,
      ArtifactReference snapshotManifestRef,
      ArtifactReference verificationPolicyRef,
      ArtifactReference capabilityProfileRef,
      ArtifactReference resourceBudgetRef) {}

  private record Scope(String kind, int count) {}

  private record Admitted(
      ArtifactId sourceRegistrationId,
      String repositoryUrl,
      String revision,
      boolean repositoryCompletionEligible,
      List<File> files) {}

  private record Indexed(
      String snapshotId,
      ArtifactId requestArtifactId,
      int textCount,
      int mediaCount,
      List<File> files,
      ArrayNode shards) {

    private List<File> textFiles() {
      return files.stream().filter(file -> "ANALYZABLE_TEXT".equals(file.disposition())).toList();
    }

    private List<File> mediaFiles() {
      return files.stream()
          .filter(file -> "NON_ANALYZABLE_MEDIA".equals(file.disposition()))
          .toList();
    }
  }

  private record File(
      ArtifactId id,
      String path,
      String gitMode,
      String mediaType,
      long sizeBytes,
      Sha256Digest sha256,
      String disposition,
      String encoding,
      Sha256Digest lineIndexDigest) {

    private boolean matches(File verified) {
      return gitMode.equals(verified.gitMode)
          && mediaType.equals(verified.mediaType)
          && sizeBytes == verified.sizeBytes
          && sha256.equals(verified.sha256)
          && disposition.equals(verified.disposition)
          && java.util.Objects.equals(encoding, verified.encoding);
    }
  }
}
