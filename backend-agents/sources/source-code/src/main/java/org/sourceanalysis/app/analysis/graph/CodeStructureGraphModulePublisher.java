package org.sourceanalysis.app.analysis.graph;

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
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;

/** Installs the receipt-last module artifact for one independent code-structure graph draft. */
public final class CodeStructureGraphModulePublisher {

  private static final String ARTIFACT_TYPE = "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT";
  private static final String ARTIFACT_PREFIX = "code-structure-graph";
  private static final Comparator<String> UTF8_ORDER =
      CodeStructureGraphModulePublisher::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  /** Creates the publisher with the one permitted module-artifact storage dependency. */
  public CodeStructureGraphModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    canonicalJson = new CanonicalJsonCodec();
  }

  /**
   * Installs the canonical code-structure module result and returns its typed reopening reference.
   */
  public CodeStructureGraphDraftReference publish(
      AnalysisStepModuleAddress destination,
      CodeStructureSource source,
      CodeStructureDiscovery discovery,
      CodeStructureGraphDraft draft) {
    requireDestination(destination);
    Objects.requireNonNull(source, "code-structure source");
    Objects.requireNonNull(discovery, "code-structure discovery");
    Objects.requireNonNull(draft, "code-structure draft");
    requireDraftClosure(source, discovery, draft);
    List<ArtifactReference> upstream =
        sortedReferences(
            List.of(
                source.sourceInventoryRef(),
                source.verifiedSnapshotRef(),
                discovery.applicationProfileRef(),
                discovery.capabilityReportRef(),
                discovery.entryPointsRef(),
                discovery.mapperCatalogRef(),
                draft.graphProfileRef()));
    List<String> gapRefs = gapReferences(draft.coverage());
    ModuleCompletionStatus status =
        gapRefs.isEmpty()
            ? ModuleCompletionStatus.SUCCEEDED
            : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
    InstalledModulePublication installed =
        moduleArtifacts.install(
            new ModuleInstallRequest(
                destination,
                "v1",
                upstream,
                source.controls(),
                status,
                gapRefs,
                List.of(
                    payload(destination, upstream, source.controls(), draft, status, gapRefs))));
    return new CodeStructureGraphDraftReference(installed.reference());
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress destination,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      CodeStructureGraphDraft draft,
      ModuleCompletionStatus status,
      List<String> gapRefs) {
    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", CodeStructureGraphDraft.SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(destination));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(controls));
    withoutArtifactId.set("completion", completion(status, gapRefs));
    withoutArtifactId.set("payload", body(draft));
    String artifactId =
        ARTIFACT_PREFIX
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(CodeStructureGraphDraft.SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode envelope = withoutArtifactId.deepCopy();
    envelope.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        "code-structure-draft.json",
        ARTIFACT_TYPE,
        CodeStructureGraphDraft.SCHEMA_VERSION,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private static ObjectNode body(CodeStructureGraphDraft draft) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("graphKind", draft.graphKind().name());
    body.put("graphId", draft.graphId().value());
    body.put("snapshotId", draft.snapshotId());
    body.put("applicationProfileId", draft.applicationProfileId().value());
    body.set("graphProfileRef", reference(draft.graphProfileRef()));
    ArrayNode entryIds = body.putArray("entryIds");
    draft.entryIds().forEach(value -> entryIds.add(value.value()));
    ArrayNode nodes = body.putArray("nodes");
    draft.nodes().forEach(node -> nodes.add(node(node)));
    ArrayNode edges = body.putArray("edges");
    draft.edges().forEach(edge -> edges.add(edge(edge)));
    ArrayNode provenanceDrafts = body.putArray("provenanceDrafts");
    draft.provenanceDrafts().forEach(provenance -> provenanceDrafts.add(provenance(provenance)));
    body.set("coverage", coverage(draft.coverage()));
    return body;
  }

  private static ObjectNode node(DraftProgramNode node) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("nodeId", node.nodeId().value());
    value.put("kind", node.kind().name());
    value.put("canonicalValue", node.canonicalValue());
    ids(value.putArray("owningEntryIds"), node.owningEntryIds());
    ids(value.putArray("evidenceDraftRefs"), node.evidenceDraftRefs());
    return value;
  }

  private static ObjectNode edge(DraftProgramEdge edge) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("edgeId", edge.edgeId().value());
    value.put("kind", edge.kind().name());
    value.put("fromNodeId", edge.fromNodeId().value());
    value.put("toNodeId", edge.toNodeId().value());
    value.put("ruleId", edge.ruleId());
    value.put("resolution", edge.resolution().name());
    if (edge.guardNodeId() == null) {
      value.putNull("guardNodeId");
    } else {
      value.put("guardNodeId", edge.guardNodeId().value());
    }
    if (edge.polarity() == null) {
      value.putNull("polarity");
    } else {
      value.put("polarity", edge.polarity());
    }
    ids(value.putArray("evidenceDraftRefs"), edge.evidenceDraftRefs());
    return value;
  }

  private static ObjectNode provenance(ProvenanceDraftV1 provenance) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("provenanceDraftId", provenance.provenanceDraftId().value());
    value.put("ruleId", provenance.ruleId());
    ObjectNode locator = value.putObject("sourceLocator");
    locator.put("fileId", provenance.sourceLocator().fileId().value());
    locator.put("path", provenance.sourceLocator().path());
    locator.put("startByte", provenance.sourceLocator().startByte());
    locator.put("endByteExclusive", provenance.sourceLocator().endByteExclusive());
    locator.put("startLine", provenance.sourceLocator().startLine());
    locator.put("startColumn", provenance.sourceLocator().startColumn());
    locator.put("endLine", provenance.sourceLocator().endLine());
    locator.put("endColumn", provenance.sourceLocator().endColumn());
    value.put("sourceFileSha256", provenance.sourceFileSha256().value());
    value.put("excerptSha256", provenance.excerptSha256().value());
    return value;
  }

  private static ObjectNode coverage(GraphCoverage coverage) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    ids(value.putArray("candidateElementIds"), coverage.candidateElementIds());
    ids(value.putArray("exactElementIds"), coverage.exactElementIds());
    ArrayNode gaps = value.putArray("gapDispositions");
    coverage
        .gapDispositions()
        .forEach(
            gap ->
                gaps.addObject()
                    .put("candidateElementId", gap.candidateElementId().value())
                    .put("gapId", gap.gapId().value()));
    ArrayNode exclusions = value.putArray("exclusionDispositions");
    coverage
        .exclusionDispositions()
        .forEach(
            exclusion -> {
              ObjectNode item = exclusions.addObject();
              item.put("candidateElementId", exclusion.candidateElementId().value());
              item.put("reasonCode", exclusion.reasonCode());
              ids(item.putArray("evidenceDraftRefs"), exclusion.evidenceDraftRefs());
            });
    ids(value.putArray("scopeGapIds"), coverage.scopeGapIds());
    value.put("closed", coverage.closed());
    return value;
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode producer = JsonNodeFactory.instance.objectNode();
    ObjectNode producerAddress = producer.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", address.runId().value());
    producerAddress.put("analysisStepKey", address.analysisStepKey().wireValue());
    producerAddress.put("moduleNumber", address.moduleNumber());
    producerAddress.put("moduleKey", address.moduleKey());
    producer.put("moduleVersion", "v1");
    return producer;
  }

  private static ObjectNode controls(ArtifactControls controls) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("toolchainSha256", controls.toolchainSha256().value());
    value.put("profileSha256", controls.profileSha256().value());
    value.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) {
      value.putNull("promptBundleSha256");
    } else {
      value.put("promptBundleSha256", controls.promptBundleSha256().value());
    }
    value
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
    return value;
  }

  private static ObjectNode completion(ModuleCompletionStatus status, List<String> gapRefs) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("status", status.name());
    ArrayNode gaps = value.putArray("gapRefs");
    gapRefs.forEach(gaps::add);
    value.putNull("failureRef");
    return value;
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode references = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> references.add(reference(value)));
    return references;
  }

  private static ObjectNode reference(ArtifactReference value) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", value.artifactId().value())
        .put("sha256", value.sha256().value());
  }

  private static void ids(ArrayNode destination, List<ArtifactId> values) {
    values.forEach(value -> destination.add(value.value()));
  }

  private static List<ArtifactReference> sortedReferences(List<ArtifactReference> values) {
    List<ArtifactReference> ordered = new ArrayList<>(values);
    ordered.sort(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER));
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException("code-structure upstream references must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<String> gapReferences(GraphCoverage coverage) {
    List<String> values = new ArrayList<>();
    coverage.gapDispositions().forEach(gap -> values.add(gap.gapId().value()));
    coverage.scopeGapIds().forEach(gap -> values.add(gap.value()));
    values.sort(UTF8_ORDER);
    if (values.size() != values.stream().distinct().count()) {
      throw new IllegalArgumentException("code-structure gap references must be distinct");
    }
    return List.copyOf(values);
  }

  private static void requireDestination(AnalysisStepModuleAddress destination) {
    if (destination == null
        || destination.analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
        || destination.moduleNumber() != 1
        || !"code-structure".equals(destination.moduleKey())) {
      throw new IllegalArgumentException("code-structure module destination is invalid");
    }
  }

  private static void requireDraftClosure(
      CodeStructureSource source, CodeStructureDiscovery discovery, CodeStructureGraphDraft draft) {
    if (!source.snapshotId().equals(draft.snapshotId())
        || !discovery.applicationProfileId().equals(draft.applicationProfileId())
        || !discovery.entryIds().equals(draft.entryIds())) {
      throw new IllegalArgumentException("code-structure draft input closure is invalid");
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
