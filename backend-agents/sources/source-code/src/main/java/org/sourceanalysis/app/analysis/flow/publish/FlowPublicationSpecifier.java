package org.sourceanalysis.app.analysis.flow.publish;

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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
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
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * M3 join: publishes the complete repository denominator as five formal business-flow files.
 * It does not repair a Flow or Capsule and does not read source code.
 */
public final class FlowPublicationSpecifier {

  private static final Comparator<String> UTF8_ORDER = FlowPublicationSpecifier::compareUtf8;
  private static final String MODULE_VERSION = "v1";
  private static final String FLOW_SLICES_TYPE = "BUSINESS_FLOWS_FLOW_SLICES";
  private static final String FLOW_SLICES_SCHEMA = "business-flows-flow-slices-v1";
  private static final String COVERAGE_TYPE = "BUSINESS_FLOWS_FLOW_COVERAGE";
  private static final String COVERAGE_SCHEMA = "business-flows-flow-coverage-v1";
  private static final String ENTRY_TYPE = "BUSINESS_FLOWS_ENTRY_DISPOSITION";
  private static final String ENTRY_SCHEMA = "business-flows-entry-disposition-v1";
  private static final String CAPSULE_TYPE = "BUSINESS_FLOWS_EVIDENCE_CAPSULE";
  private static final String CAPSULE_SCHEMA = "business-flows-evidence-capsule-v1";
  private static final String GAP_TYPE = "BUSINESS_FLOWS_FLOW_GAP";
  private static final String GAP_SCHEMA = "business-flows-flow-gap-v1";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the only M3 specifier with receipt-last module and semantic-step stores. */
  public FlowPublicationSpecifier(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
  }

  /** Joins the exact persisted M1 and M2 artifacts and installs the five-file public set. */
  public BusinessFlowsReference specify(
      VerifiedSourceInventoryReference source,
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference graphs,
      ProvenCodeFactsReference facts,
      ModulePublicationReference flowCompilation,
      ModulePublicationReference capsuleProjection) {
    try {
      Objects.requireNonNull(source, "source inventory");
      Objects.requireNonNull(discovery, "application discovery");
      Objects.requireNonNull(graphs, "program graphs");
      Objects.requireNonNull(facts, "proven code facts");
      ReopenedAnalysisStepPublication sourceStep = reopen(source.publication(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
      ReopenedAnalysisStepPublication discoveryStep = reopen(discovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
      ReopenedAnalysisStepPublication graphStep = reopen(graphs.publication(), AnalysisStepKey.PROGRAM_GRAPHS);
      ReopenedAnalysisStepPublication factStep = reopen(facts.publication(), AnalysisStepKey.PROVEN_CODE_FACTS);
      requireLineage(sourceStep, discoveryStep, graphStep, factStep);
      ReopenedModulePublication compiler = moduleArtifacts.reopen(flowCompilation);
      ReopenedModulePublication projector = moduleArtifacts.reopen(capsuleProjection);
      ArtifactReference compilerPayload =
          requireModule(
              compiler,
              1,
              "flow-compiler",
              "flow-compilation.json",
              "BUSINESS_FLOWS_FLOW_COMPILATION",
              "business-flows-flow-compilation-v1",
              source.publication().address().runId(),
              sourceStep.receipt().controls());
      ArtifactReference projectorPayload =
          requireModule(
              projector,
              2,
              "capsule-projector",
              "capsule-projection.json",
              "BUSINESS_FLOWS_CAPSULE_PROJECTION",
              "business-flows-capsule-projection-v4",
              source.publication().address().runId(),
              sourceStep.receipt().controls());
      ArtifactReference proofPack =
          factStep.semanticPayloads().stream()
              .filter(value -> "proof-pack.json".equals(value.descriptor().fileName()))
              .findFirst()
              .map(value -> new ArtifactReference(value.descriptor().artifactId(), value.descriptor().sha256()))
              .orElseThrow(FlowPublicationSpecifier::failure);
      Material material = material(compiler, projector, compilerPayload, projectorPayload, proofPack);
      List<CanonicalModulePayload> payloads = payloads(material);
      List<String> gaps = material.gapIds();
      ModuleCompletionStatus status = gaps.isEmpty() ? ModuleCompletionStatus.SUCCEEDED : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
      AnalysisStepModuleAddress address = new AnalysisStepModuleAddress(source.publication().address().runId(), AnalysisStepKey.BUSINESS_FLOWS, 3, "publish");
      InstalledModulePublication module =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  List.of(compilerPayload, projectorPayload).stream()
                      .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
                      .toList(),
                  sourceStep.receipt().controls(),
                  status,
                  gaps,
                  payloads));
      InstalledAnalysisStepPublication step =
          analysisSteps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(address.runId(), AnalysisStepKey.BUSINESS_FLOWS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(source.publication(), discovery.publication(), graphs.publication(), facts.publication()),
                  sourceStep.receipt().controls(),
                  status,
                  gaps,
                  payloads.stream().map(FlowPublicationSpecifier::stepPayload).toList(),
                  null));
      ReopenedAnalysisStepPublication reopened = analysisSteps.reopen(step.reference());
      if (!step.reference().equals(reopened.reference()) || reopened.semanticPayloads().size() != 5) {
        throw failure();
      }
      return new BusinessFlowsReference(step.reference());
    } catch (FlowPublicationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure();
    }
  }

  private Material material(
      ReopenedModulePublication compiler,
      ReopenedModulePublication projector,
      ArtifactReference compilerPayload,
      ArtifactReference projectorPayload,
      ArtifactReference proofPack) {
    JsonNode flowEnvelope = canonicalJson.parseCanonical(compiler.payloads().get(0).canonicalUtf8());
    JsonNode capsuleEnvelope = canonicalJson.parseCanonical(projector.payloads().get(0).canonicalUtf8());
    JsonNode flowPayload = object(flowEnvelope, "payload");
    JsonNode capsulePayload = object(capsuleEnvelope, "payload");
    if (!compilerPayload.equals(readReference(capsulePayload, "flowCompilationRef"))
        || !proofPack.equals(readReference(capsulePayload, "proofPackRef"))) {
      throw failure();
    }
    List<JsonNode> flows = sortedObjects(array(flowPayload, "flowSlices"), "flowSliceId");
    List<JsonNode> dispositions = sortedObjects(array(flowPayload, "entryDispositions"), "entryId");
    List<JsonNode> compilationGaps = sortedObjects(array(flowPayload, "flowGaps"), "gapId");
    List<JsonNode> capsules = sortedObjects(array(capsulePayload, "capsules"), "flowSliceId");
    Set<String> flowIds = ids(flows, "flowSliceId");
    Set<String> capsuleFlowIds = ids(capsules, "flowSliceId");
    List<JsonNode> compiledDispositions =
        dispositions.stream()
            .filter(value -> "COMPILED".equals(text(value, "disposition")))
            .toList();
    Set<String> compiledFlowIds =
        compiledDispositions.stream()
            .map(value -> id(value, "flowSliceId"))
            .collect(java.util.stream.Collectors.toSet());
    if (!flowIds.equals(capsuleFlowIds)
        || !flowIds.equals(compiledFlowIds)
        || compiledDispositions.stream()
            .anyMatch(value -> value.get("flowSliceId") == null || value.get("flowSliceId").isNull())
        || dispositions.stream()
            .filter(value -> !"COMPILED".equals(text(value, "disposition")))
            .anyMatch(
                value ->
                    value.get("flowSliceId") == null
                        || !value.get("flowSliceId").isNull()
                        || (!"GAP".equals(text(value, "disposition"))
                            && !"EXCLUDED".equals(text(value, "disposition")))
                        || value.get("reasonCode") == null
                        || value.get("reasonCode").isNull()
                        || text(value, "reasonCode").isBlank()
                        || identifierArray(value, "gapIds").isEmpty())) {
      throw failure();
    }
    List<String> modelEligibleFlowIds = new ArrayList<>();
    List<String> modelIneligibleFlowIds = new ArrayList<>();
    Map<String, List<String>> modelIneligibilityByFlow = new HashMap<>();
    for (JsonNode capsule : capsules) {
      String flowSliceId = id(capsule, "flowSliceId");
      String eligibility = text(capsule, "modelEligibility");
      List<String> ineligibilityGapIds = identifierArray(capsule, "modelIneligibilityGapIds");
      if (!proofPack.artifactId().value().equals(text(capsule, "proofPackId"))) {
        throw failure();
      } else if ("ELIGIBLE".equals(eligibility) && ineligibilityGapIds.isEmpty()) {
        modelEligibleFlowIds.add(flowSliceId);
      } else if ("INELIGIBLE".equals(eligibility) && !ineligibilityGapIds.isEmpty()) {
        modelIneligibleFlowIds.add(flowSliceId);
        modelIneligibilityByFlow.put(flowSliceId, ineligibilityGapIds);
      } else {
        throw failure();
      }
    }
    Map<String, JsonNode> gapsById = new HashMap<>();
    compilationGaps.forEach(value -> gapsById.put(id(value, "gapId"), value));
    for (JsonNode capsule : capsules) {
      for (JsonNode capsuleGap : array(capsule, "gapViews")) {
        String gapId = id(capsuleGap, "gapId");
        // M1 is authoritative for an upstream proven Gap. M2 narrows it to the owning Flow,
        // while a M2-only ID represents a projection outcome such as a context budget overflow.
        gapsById.putIfAbsent(gapId, capsuleGap);
      }
    }
    List<JsonNode> orderedGaps =
        gapsById.values().stream().sorted(Comparator.comparing(value -> id(value, "gapId"), UTF8_ORDER)).toList();
    Set<String> publishedGapIds = orderedGaps.stream().map(value -> id(value, "gapId")).collect(java.util.stream.Collectors.toSet());
    if (modelIneligibilityByFlow.values().stream().flatMap(List::stream).anyMatch(id -> !publishedGapIds.contains(id))) {
      throw failure();
    }
    return new Material(
        compilerPayload,
        projectorPayload,
        flows,
        dispositions,
        capsules,
        orderedGaps,
        orderedGaps.stream().map(value -> id(value, "gapId")).toList(),
        modelEligibleFlowIds.stream().sorted(UTF8_ORDER).toList(),
        modelIneligibleFlowIds.stream().sorted(UTF8_ORDER).toList(),
        Map.copyOf(modelIneligibilityByFlow));
  }

  private List<CanonicalModulePayload> payloads(Material material) {
    List<CanonicalModulePayload> values = new ArrayList<>();
    ObjectNode flows = JsonNodeFactory.instance.objectNode();
    flows.set("flowCompilationRef", reference(material.compilerPayload()));
    flows.set("capsuleProjectionRef", reference(material.projectorPayload()));
    ArrayNode flowItems = flows.putArray("flowSlices");
    material.flows().forEach(value -> flowItems.add(value.deepCopy()));
    values.add(standalone("flow-slices.json", FLOW_SLICES_TYPE, FLOW_SLICES_SCHEMA, "business-flows-flow-slices", flows));

    ObjectNode coverage = JsonNodeFactory.instance.objectNode();
    strings(coverage.putArray("entryIds"), material.dispositions().stream().map(value -> id(value, "entryId")).toList());
    strings(
        coverage.putArray("compiledEntryIds"),
        entryIdsWithDisposition(material.dispositions(), "COMPILED"));
    strings(coverage.putArray("gappedEntryIds"), entryIdsWithDisposition(material.dispositions(), "GAP"));
    strings(
        coverage.putArray("excludedEntryIds"), entryIdsWithDisposition(material.dispositions(), "EXCLUDED"));
    strings(coverage.putArray("flowSliceIds"), material.flows().stream().map(value -> id(value, "flowSliceId")).toList());
    strings(coverage.putArray("capsuleIds"), material.capsules().stream().map(value -> id(value, "evidenceCapsuleId")).toList());
    strings(coverage.putArray("modelEligibleFlowSliceIds"), material.modelEligibleFlowIds());
    strings(coverage.putArray("modelIneligibleFlowSliceIds"), material.modelIneligibleFlowIds());
    strings(
        coverage.putArray("modelIneligibilityGapIds"),
        material.modelIneligibilityByFlow().values().stream()
            .flatMap(List::stream)
            .sorted(UTF8_ORDER)
            .toList());
    ArrayNode ineligibilityByFlow = coverage.putArray("modelIneligibilityByFlow");
    material.modelIneligibleFlowIds().forEach(
        flowSliceId -> {
          ObjectNode item = ineligibilityByFlow.addObject();
          item.put("flowSliceId", flowSliceId);
          strings(item.putArray("gapIds"), material.modelIneligibilityByFlow().get(flowSliceId));
        });
    strings(coverage.putArray("gapIds"), material.gapIds());
    coverage.put("closed", true);
    values.add(standalone("flow-coverage.json", COVERAGE_TYPE, COVERAGE_SCHEMA, "business-flows-flow-coverage", coverage));

    values.add(jsonl("entry-dispositions.jsonl", ENTRY_TYPE, ENTRY_SCHEMA, "business-flows-entry-disposition", material.dispositions()));
    values.add(jsonl("evidence-capsules.jsonl", CAPSULE_TYPE, CAPSULE_SCHEMA, "business-flows-evidence-capsule", material.capsules()));
    values.add(jsonl("flow-gaps.jsonl", GAP_TYPE, GAP_SCHEMA, "business-flows-flow-gap", material.gaps()));
    return values.stream().sorted(Comparator.comparing(CanonicalModulePayload::fileName, UTF8_ORDER)).toList();
  }

  private CanonicalModulePayload standalone(
      String fileName, String type, String schema, String prefix, ObjectNode body) {
    ObjectNode document = body.deepCopy();
    document.put("schemaVersion", schema);
    document.put("artifactType", type);
    document.put("artifactId", "pending");
    ObjectNode withoutId = document.deepCopy();
    withoutId.remove("artifactId");
    ArtifactId id = ArtifactId.parse(prefix + ":" + sha256(frame("canonical-standalone-json-artifact-id-v1"), frame(schema), frame(type), frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray())));
    document.put("artifactId", id.value());
    return new CanonicalModulePayload(fileName, type, schema, id, CanonicalMediaType.APPLICATION_JSON, canonicalJson.encodeCanonical(document));
  }

  private CanonicalModulePayload jsonl(
      String fileName, String type, String schema, String prefix, List<JsonNode> lines) {
    StringBuilder result = new StringBuilder();
    for (JsonNode value : lines) {
      ObjectNode line = ((ObjectNode) value).deepCopy();
      line.put("schemaVersion", schema);
      line.put("artifactType", type);
      result.append(new String(canonicalJson.encodeCanonical(line).copyToByteArray(), StandardCharsets.UTF_8));
      result.append('\n');
    }
    byte[] bytes = result.toString().getBytes(StandardCharsets.UTF_8);
    ArtifactId id = ArtifactId.parse(prefix + ":" + sha256(frame("canonical-jsonl-artifact-id-v1"), frame(schema), frame(type), frame(bytes)));
    return new CanonicalModulePayload(fileName, type, schema, id, CanonicalMediaType.APPLICATION_X_NDJSON, ImmutableBytes.copyOf(bytes));
  }

  private ReopenedAnalysisStepPublication reopen(
      org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reference,
      AnalysisStepKey expected) {
    if (reference == null || reference.address().analysisStepKey() != expected) throw failure();
    ReopenedAnalysisStepPublication reopened = analysisSteps.reopen(reference);
    if (!reference.equals(reopened.reference()) || reopened.receipt().address().analysisStepKey() != expected) {
      throw failure();
    }
    return reopened;
  }

  private static void requireLineage(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication graphs,
      ReopenedAnalysisStepPublication facts) {
    if (!source.reference().address().runId().equals(discovery.reference().address().runId())
        || !source.reference().address().runId().equals(graphs.reference().address().runId())
        || !source.reference().address().runId().equals(facts.reference().address().runId())
        || !source.receipt().controls().equals(discovery.receipt().controls())
        || !source.receipt().controls().equals(graphs.receipt().controls())
        || !source.receipt().controls().equals(facts.receipt().controls())) throw failure();
  }

  private static ArtifactReference requireModule(
      ReopenedModulePublication publication,
      int number,
      String key,
      String fileName,
      String type,
      String schema,
      org.sourceanalysis.app.artifact.AnalysisRunId expectedRunId,
      ArtifactControls expectedControls) {
    if (publication.payloads().size() != 1
        || !(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
        || address.moduleNumber() != number
        || !key.equals(address.moduleKey())
        || !expectedRunId.equals(address.runId())
        || !expectedControls.equals(publication.receipt().controls())) throw failure();
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!fileName.equals(payload.descriptor().fileName())
        || !type.equals(payload.descriptor().artifactType())
        || !schema.equals(payload.descriptor().schemaVersion())) throw failure();
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static CanonicalAnalysisStepPayload stepPayload(CanonicalModulePayload payload) {
    return new CanonicalAnalysisStepPayload(payload.fileName(), payload.artifactType(), payload.schemaVersion(), payload.artifactId(), payload.mediaType(), payload.canonicalUtf8());
  }

  private static ObjectNode reference(ArtifactReference value) {
    return JsonNodeFactory.instance.objectNode().put("artifactId", value.artifactId().value()).put("sha256", value.sha256().value());
  }

  private static ArtifactReference readReference(JsonNode source, String field) {
    JsonNode value = object(source, field);
    try {
      return new ArtifactReference(ArtifactId.parse(text(value, "artifactId")), new Sha256Digest(text(value, "sha256")));
    } catch (RuntimeException invalid) {
      throw failure();
    }
  }

  private static List<JsonNode> sortedObjects(List<JsonNode> values, String idField) {
    List<JsonNode> result = values.stream().filter(JsonNode::isObject).sorted(Comparator.comparing(value -> id(value, idField), UTF8_ORDER)).toList();
    if (result.size() != values.size() || result.size() != result.stream().map(value -> id(value, idField)).distinct().count()) throw failure();
    return result;
  }

  private static Set<String> ids(List<JsonNode> values, String field) {
    return values.stream().map(value -> id(value, field)).collect(java.util.stream.Collectors.toCollection(HashSet::new));
  }

  private static JsonNode object(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isObject()) throw failure();
    return value;
  }

  private static List<JsonNode> array(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isArray()) throw failure();
    List<JsonNode> result = new ArrayList<>();
    value.forEach(result::add);
    return List.copyOf(result);
  }

  private static String id(JsonNode source, String field) {
    try {
      return ArtifactId.parse(text(source, field)).value();
    } catch (RuntimeException invalid) {
      throw failure();
    }
  }

  private static String text(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) throw failure();
    return value.textValue();
  }

  private static List<String> identifierArray(JsonNode source, String field) {
    List<String> values =
        array(source, field).stream()
            .map(
                value -> {
                  if (!value.isTextual()) throw failure();
                  try {
                    return ArtifactId.parse(value.textValue()).value();
                  } catch (RuntimeException invalid) {
                    throw failure();
                  }
                })
            .sorted(UTF8_ORDER)
            .toList();
    if (values.size() != values.stream().distinct().count()) throw failure();
    return values;
  }

  private static List<String> entryIdsWithDisposition(List<JsonNode> dispositions, String expected) {
    return dispositions.stream()
        .filter(value -> expected.equals(text(value, "disposition")))
        .map(value -> id(value, "entryId"))
        .toList();
  }

  private static void strings(ArrayNode array, List<String> values) {
    values.forEach(array::add);
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) digest.update(value);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] bytes) {
    return ByteBuffer.allocate(Long.BYTES + bytes.length).order(ByteOrder.BIG_ENDIAN).putLong(bytes.length).put(bytes).array();
  }

  private static int compareUtf8(String left, String right) {
    byte[] first = left.getBytes(StandardCharsets.UTF_8);
    byte[] second = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int result = Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (result != 0) return result;
    }
    return Integer.compare(first.length, second.length);
  }

  private static FlowPublicationException failure() {
    return new FlowPublicationException("FLOW_ACCOUNTING_INVARIANT_BROKEN");
  }

  private record Material(
      ArtifactReference compilerPayload,
      ArtifactReference projectorPayload,
      List<JsonNode> flows,
      List<JsonNode> dispositions,
      List<JsonNode> capsules,
      List<JsonNode> gaps,
      List<String> gapIds,
      List<String> modelEligibleFlowIds,
      List<String> modelIneligibleFlowIds,
      Map<String, List<String>> modelIneligibilityByFlow) {}
}
