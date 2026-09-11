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
import java.util.Arrays;
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
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
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
 * M3 join: publishes the complete repository denominator as five formal business-flow files. It
 * does not repair a Flow or Capsule and does not read source code.
 */
public final class FlowPublicationSpecifier {

  private static final Comparator<String> UTF8_ORDER = FlowPublicationSpecifier::compareUtf8;
  private static final String MODULE_VERSION = "v2";
  private static final String FLOW_SLICES_TYPE = "BUSINESS_FLOWS_FLOW_SLICES";
  private static final String FLOW_SLICES_SCHEMA = "business-flows-flow-slices-v4";
  private static final String COVERAGE_TYPE = "BUSINESS_FLOWS_FLOW_COVERAGE";
  private static final String COVERAGE_SCHEMA = "business-flows-flow-coverage-v1";
  private static final String ENTRY_TYPE = "BUSINESS_FLOWS_ENTRY_DISPOSITION";
  private static final String ENTRY_SCHEMA = "business-flows-entry-disposition-v1";
  private static final String CAPSULE_TYPE = "BUSINESS_FLOWS_EVIDENCE_CAPSULE";
  private static final String CAPSULE_SCHEMA = "business-flows-evidence-capsule-v7";
  private static final String GAP_TYPE = "BUSINESS_FLOWS_FLOW_GAP";
  private static final String GAP_SCHEMA = "business-flows-flow-gap-v2";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the only M3 specifier with receipt-last module and semantic-step stores. */
  public FlowPublicationSpecifier(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps,
      VerifiedSourceTextReader sourceReader) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
    Objects.requireNonNull(sourceReader, "verified source reader");
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
      ReopenedAnalysisStepPublication sourceStep =
          reopen(source.publication(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
      ReopenedAnalysisStepPublication discoveryStep =
          reopen(discovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
      ReopenedAnalysisStepPublication graphStep =
          reopen(graphs.publication(), AnalysisStepKey.PROGRAM_GRAPHS);
      ReopenedAnalysisStepPublication factStep =
          reopen(facts.publication(), AnalysisStepKey.PROVEN_CODE_FACTS);
      requireLineage(sourceStep, discoveryStep, graphStep, factStep);
      DiscoveryCoverage discoveryCoverage = requireDiscoveryCoverage(discoveryStep);
      ReopenedModulePublication compiler = moduleArtifacts.reopen(flowCompilation);
      ReopenedModulePublication projector = moduleArtifacts.reopen(capsuleProjection);
      ArtifactReference compilerPayload =
          requireModule(
              compiler,
              1,
              "flow-compiler",
              "flow-compilation.json",
              "BUSINESS_FLOWS_FLOW_COMPILATION",
              "business-flows-flow-compilation-v4",
              source.publication().address().runId(),
              sourceStep.receipt().controls());
      ArtifactReference projectorPayload =
          requireModule(
              projector,
              2,
              "capsule-projector",
              "capsule-projection.json",
              "BUSINESS_FLOWS_CAPSULE_PROJECTION",
              "business-flows-capsule-projection-v9",
              source.publication().address().runId(),
              sourceStep.receipt().controls());
      FlowProvenanceSources sources = sourceRecords(factStep, graphStep);
      Material material = material(compiler, projector, compilerPayload, projectorPayload, sources);
      LocalFlowCoverage localCoverage =
          requireLocalFlowCoverage(compiler, material, discoveryCoverage.entryIds());
      boolean publicAccountingClosed =
          requirePublicAccounting(material, discoveryCoverage.entryIds());
      List<CanonicalModulePayload> payloads =
          payloads(
              material,
              discoveryCoverage.closed() && localCoverage.closed() && publicAccountingClosed);
      List<String> gaps = material.gapIds();
      ModuleCompletionStatus status =
          gaps.isEmpty()
              ? ModuleCompletionStatus.SUCCEEDED
              : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              source.publication().address().runId(), AnalysisStepKey.BUSINESS_FLOWS, 3, "publish");
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
                  new AnalysisStepPublicationAddress(
                      address.runId(), AnalysisStepKey.BUSINESS_FLOWS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(
                      source.publication(),
                      discovery.publication(),
                      graphs.publication(),
                      facts.publication()),
                  sourceStep.receipt().controls(),
                  status,
                  gaps,
                  payloads.stream().map(FlowPublicationSpecifier::stepPayload).toList(),
                  null));
      ReopenedAnalysisStepPublication reopened = analysisSteps.reopen(step.reference());
      if (!step.reference().equals(reopened.reference())
          || reopened.semanticPayloads().size() != 5) {
        throw failure();
      }
      return new BusinessFlowsReference(step.reference());
    } catch (FlowPublicationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure();
    }
  }

  private DiscoveryCoverage requireDiscoveryCoverage(ReopenedAnalysisStepPublication discovery) {
    VerifiedCanonicalPayload profilePayload =
        semanticPayload(
            discovery,
            "application-profile.json",
            "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
            "application-discovery-application-profile-v2");
    VerifiedCanonicalPayload entryPayload =
        semanticPayload(
            discovery,
            "entry-points.jsonl",
            "APPLICATION_DISCOVERY_ENTRY_POINTS",
            "application-discovery-entry-points-v2",
            CanonicalMediaType.APPLICATION_X_NDJSON);
    VerifiedCanonicalPayload capabilityPayload =
        semanticPayload(
            discovery,
            "capability-report.json",
            "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
            "application-discovery-capability-report-v2");
    JsonNode profile = canonicalJson.parseCanonical(profilePayload.canonicalUtf8());
    JsonNode capability = canonicalJson.parseCanonical(capabilityPayload.canonicalUtf8());
    ArtifactReference profileReference = payloadReference(profilePayload);
    ArtifactReference capabilityReference = payloadReference(capabilityPayload);
    requireStandaloneHeader(
        profile,
        profileReference,
        "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
        "application-discovery-application-profile-v2");
    requireStandaloneHeader(
        capability,
        capabilityReference,
        "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
        "application-discovery-capability-report-v2");
    String applicationProfileId = id(profile, "applicationProfileId");
    if (!applicationProfileId.equals(id(capability, "applicationProfileId"))) throw failure();
    String inventoryScopeKind = text(profile, "inventoryScopeKind");
    boolean repositoryCompletionEligible = requiredBoolean(profile, "repositoryCompletionEligible");
    if ((!"COMPLETE_CAPTURE".equals(inventoryScopeKind)
            && !"BOUNDED_PATH_SET".equals(inventoryScopeKind))
        || (repositoryCompletionEligible && !"COMPLETE_CAPTURE".equals(inventoryScopeKind))) {
      throw failure();
    }
    List<String> entryIds = new ArrayList<>();
    for (JsonNode entry : parseJsonLines(entryPayload)) {
      requireJsonLineHeader(entry, "application-discovery-entry-point-v2");
      entryIds.add(id(entry, "entryId"));
    }
    List<String> orderedEntryIds = entryIds.stream().sorted(UTF8_ORDER).toList();
    if (!orderedEntryIds.equals(entryIds)
        || orderedEntryIds.size() != orderedEntryIds.stream().distinct().count()) {
      throw failure();
    }
    JsonNode coverage = object(capability, "repositoryEntryCoverage");
    List<String> coverageEntryIds = orderedIdentifierArray(coverage, "entryIds");
    if (!orderedEntryIds.equals(coverageEntryIds)
        || nonnegativeInt(coverage, "entryCount") != coverageEntryIds.size()
        || requiredBoolean(coverage, "noEntryDiscovered") != coverageEntryIds.isEmpty()) {
      throw failure();
    }
    boolean closed = requiredBoolean(coverage, "closed");
    if (closed != ("COMPLETE_CAPTURE".equals(inventoryScopeKind) && repositoryCompletionEligible)) {
      throw failure();
    }
    return new DiscoveryCoverage(orderedEntryIds, closed);
  }

  private LocalFlowCoverage requireLocalFlowCoverage(
      ReopenedModulePublication compiler, Material material, List<String> discoveryEntryIds) {
    JsonNode envelope = canonicalJson.parseCanonical(compiler.payloads().get(0).canonicalUtf8());
    JsonNode flowPayload = object(envelope, "payload");
    JsonNode coverage = object(flowPayload, "coverage");
    requireExactFields(
        coverage,
        Set.of(
            "entryIds",
            "compiledEntryIds",
            "gappedEntryIds",
            "excludedEntryIds",
            "flowSliceIds",
            "outcomePathIds",
            "factIds",
            "atomIds",
            "gapIds",
            "processJoinSignalIds",
            "closed"));
    List<String> coverageEntryIds = orderedIdentifierArray(coverage, "entryIds");
    if (!discoveryEntryIds.equals(coverageEntryIds)
        || !entryIdsWithDisposition(material.dispositions(), "COMPILED")
            .equals(orderedIdentifierArray(coverage, "compiledEntryIds"))
        || !entryIdsWithDisposition(material.dispositions(), "GAP")
            .equals(orderedIdentifierArray(coverage, "gappedEntryIds"))
        || !entryIdsWithDisposition(material.dispositions(), "EXCLUDED")
            .equals(orderedIdentifierArray(coverage, "excludedEntryIds"))
        || !material.flows().stream()
            .map(value -> id(value, "flowSliceId"))
            .toList()
            .equals(orderedIdentifierArray(coverage, "flowSliceIds"))
        || !flowOutcomePathIds(material.flows())
            .equals(orderedIdentifierArray(coverage, "outcomePathIds"))
        || !flowIdentifiers(material.flows(), "factIds")
            .equals(orderedIdentifierArray(coverage, "factIds"))
        || !flowIdentifiers(material.flows(), "atomIds")
            .equals(orderedIdentifierArray(coverage, "atomIds"))
        || !sortedObjects(array(flowPayload, "flowGaps"), "gapId").stream()
            .map(value -> id(value, "gapId"))
            .toList()
            .equals(orderedIdentifierArray(coverage, "gapIds"))
        || !material
            .processJoinSignalIds()
            .equals(orderedIdentifierArray(coverage, "processJoinSignalIds"))) {
      throw failure();
    }
    return new LocalFlowCoverage(requiredBoolean(coverage, "closed"));
  }

  private static boolean requirePublicAccounting(
      Material material, List<String> discoveryEntryIds) {
    List<String> dispositionEntryIds =
        material.dispositions().stream().map(value -> id(value, "entryId")).toList();
    List<String> classifiedEntryIds = new ArrayList<>();
    classifiedEntryIds.addAll(entryIdsWithDisposition(material.dispositions(), "COMPILED"));
    classifiedEntryIds.addAll(entryIdsWithDisposition(material.dispositions(), "GAP"));
    classifiedEntryIds.addAll(entryIdsWithDisposition(material.dispositions(), "EXCLUDED"));
    classifiedEntryIds.sort(UTF8_ORDER);
    if (!discoveryEntryIds.equals(dispositionEntryIds)
        || !discoveryEntryIds.equals(classifiedEntryIds)
        || classifiedEntryIds.size() != new HashSet<>(classifiedEntryIds).size()) {
      throw failure();
    }
    return true;
  }

  private Material material(
      ReopenedModulePublication compiler,
      ReopenedModulePublication projector,
      ArtifactReference compilerPayload,
      ArtifactReference projectorPayload,
      FlowProvenanceSources sources) {
    JsonNode flowEnvelope =
        canonicalJson.parseCanonical(compiler.payloads().get(0).canonicalUtf8());
    JsonNode capsuleEnvelope =
        canonicalJson.parseCanonical(projector.payloads().get(0).canonicalUtf8());
    JsonNode flowPayload = object(flowEnvelope, "payload");
    JsonNode capsulePayload = object(capsuleEnvelope, "payload");
    if (!"v6".equals(text(object(capsuleEnvelope, "producer"), "moduleVersion"))
        || !compilerPayload.equals(readReference(capsulePayload, "flowCompilationRef"))
        || !sources.proofPack().equals(readReference(capsulePayload, "proofPackRef"))) {
      throw failure();
    }
    List<JsonNode> flows = sortedObjects(array(flowPayload, "flowSlices"), "flowSliceId");
    List<JsonNode> dispositions = sortedObjects(array(flowPayload, "entryDispositions"), "entryId");
    List<JsonNode> entryContexts = sortedObjects(array(flowPayload, "entryContexts"), "entryId");
    List<JsonNode> compilationGaps = sortedObjects(array(flowPayload, "flowGaps"), "gapId");
    List<JsonNode> capsules = sortedObjects(array(capsulePayload, "capsules"), "flowSliceId");
    Map<String, JsonNode> spansById =
        indexed(array(capsulePayload, "modelEvidenceSpans"), "spanId");
    Map<String, JsonNode> obligationsById =
        indexed(array(capsulePayload, "projectionObligations"), "obligationId");
    Set<String> flowIds = ids(flows, "flowSliceId");
    Set<String> capsuleFlowIds = ids(capsules, "flowSliceId");
    Map<String, JsonNode> flowsById = indexed(flows, "flowSliceId");
    Map<String, JsonNode> capsulesByFlowId = indexed(capsules, "flowSliceId");
    List<JsonNode> compiledDispositions =
        dispositions.stream()
            .filter(value -> "COMPILED".equals(text(value, "disposition")))
            .toList();
    if (!ids(entryContexts, "entryId").equals(ids(dispositions, "entryId"))
        || entryContexts.stream()
            .anyMatch(
                context -> {
                  JsonNode contextFlowSliceId = context.get("flowSliceId");
                  if (contextFlowSliceId == null) return true;
                  if (contextFlowSliceId.isNull()) {
                    return compiledDispositions.stream()
                        .anyMatch(
                            disposition ->
                                id(disposition, "entryId").equals(id(context, "entryId")));
                  }
                  return compiledDispositions.stream()
                      .noneMatch(
                          disposition ->
                              id(disposition, "entryId").equals(id(context, "entryId"))
                                  && id(disposition, "flowSliceId")
                                      .equals(id(context, "flowSliceId")));
                })) {
      throw failure();
    }
    Set<String> compiledFlowIds =
        compiledDispositions.stream()
            .map(value -> id(value, "flowSliceId"))
            .collect(java.util.stream.Collectors.toSet());
    if (!flowIds.equals(capsuleFlowIds)
        || !flowIds.equals(compiledFlowIds)
        || compiledDispositions.stream()
            .anyMatch(
                value -> value.get("flowSliceId") == null || value.get("flowSliceId").isNull())
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
      if (!sources.proofPack().artifactId().value().equals(text(capsule, "proofPackId"))) {
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
    List<JsonNode> orderedGaps = normalizeGaps(compilationGaps, capsules, sources);
    Set<String> publishedGapIds =
        orderedGaps.stream()
            .map(value -> id(value, "gapId"))
            .collect(java.util.stream.Collectors.toSet());
    if (modelIneligibilityByFlow.values().stream()
        .flatMap(List::stream)
        .anyMatch(id -> !publishedGapIds.contains(id))) {
      throw failure();
    }
    List<String> processJoinSignalIds = new ArrayList<>();
    for (Map.Entry<String, JsonNode> flow : flowsById.entrySet()) {
      JsonNode capsule = capsulesByFlowId.get(flow.getKey());
      if (capsule == null) throw failure();
      List<String> flowSignalIds = processJoinSignalIds(flow.getValue(), flow.getKey());
      processJoinSignalIds(capsule, flow.getKey());
      if (!Arrays.equals(
          canonicalJson
              .encodeCanonical(flow.getValue().path("processJoinSignals"))
              .copyToByteArray(),
          canonicalJson.encodeCanonical(capsule.path("processJoinSignals")).copyToByteArray())) {
        throw failure();
      }
      processJoinSignalIds.addAll(flowSignalIds);
    }
    validateFactOrigins(capsules, sources);
    List<JsonNode> publicCapsules = completePublicCapsules(capsules, spansById, obligationsById);
    return new Material(
        compilerPayload,
        projectorPayload,
        flows,
        entryContexts,
        dispositions,
        publicCapsules,
        orderedGaps,
        orderedGaps.stream().map(value -> id(value, "gapId")).toList(),
        processJoinSignalIds.stream().distinct().sorted(UTF8_ORDER).toList(),
        modelEligibleFlowIds.stream().sorted(UTF8_ORDER).toList(),
        modelIneligibleFlowIds.stream().sorted(UTF8_ORDER).toList(),
        Map.copyOf(modelIneligibilityByFlow));
  }

  private FlowProvenanceSources sourceRecords(
      ReopenedAnalysisStepPublication factStep, ReopenedAnalysisStepPublication graphStep) {
    VerifiedCanonicalPayload proofPack =
        semanticPayload(
            factStep,
            "proof-pack.json",
            "PROVEN_CODE_FACTS_PROOF_PACK",
            "proven-code-facts-proof-pack-v3");
    VerifiedCanonicalPayload provenFacts =
        semanticPayload(
            factStep,
            "proven-facts.json",
            "PROVEN_CODE_FACTS_PROVEN_FACTS",
            "proven-code-facts-proven-facts-v3");
    VerifiedCanonicalPayload gapLedger =
        semanticPayload(
            factStep,
            "gap-ledger.json",
            "PROVEN_CODE_FACTS_GAP_LEDGER",
            "proven-code-facts-gap-ledger-v3");
    VerifiedCanonicalPayload evidenceGraph =
        semanticPayload(
            graphStep,
            "evidence-graph.json",
            "PROGRAM_GRAPHS_EVIDENCE_GRAPH",
            "program-graphs-evidence-graph-v3");
    ArtifactReference provenFactsRef = payloadReference(provenFacts);
    ArtifactReference gapLedgerRef = payloadReference(gapLedger);
    ArtifactReference evidenceGraphRef = payloadReference(evidenceGraph);
    JsonNode facts = canonicalJson.parseCanonical(provenFacts.canonicalUtf8());
    JsonNode ledger = canonicalJson.parseCanonical(gapLedger.canonicalUtf8());
    JsonNode evidence = canonicalJson.parseCanonical(evidenceGraph.canonicalUtf8());
    requireStandaloneHeader(
        facts,
        provenFactsRef,
        "PROVEN_CODE_FACTS_PROVEN_FACTS",
        "proven-code-facts-proven-facts-v3");
    requireStandaloneHeader(
        ledger, gapLedgerRef, "PROVEN_CODE_FACTS_GAP_LEDGER", "proven-code-facts-gap-ledger-v3");
    requireStandaloneHeader(
        evidence,
        evidenceGraphRef,
        "PROGRAM_GRAPHS_EVIDENCE_GRAPH",
        "program-graphs-evidence-graph-v3");
    Set<String> factIds = ids(sortedObjects(array(facts, "codeFacts"), "factId"), "factId");
    Set<String> evidenceNodeIds =
        ids(sortedObjects(array(evidence, "nodes"), "evidenceNodeId"), "evidenceNodeId");
    Map<String, SourceLedgerGap> ledgerGaps = new HashMap<>();
    for (JsonNode value : array(ledger, "gaps")) {
      requireExactFields(
          value,
          Set.of(
              "gapId",
              "kind",
              "code",
              "affectedEntryIds",
              "affectedCandidateDenominatorKeys",
              "evidenceNodeIds",
              "missingRequirement",
              "impact",
              "closureRequirement"));
      SourceLedgerGap gap =
          new SourceLedgerGap(
              id(value, "gapId"),
              text(value, "code"),
              orderedIdentifierArray(value, "affectedEntryIds"),
              opaqueTexts(value, "affectedCandidateDenominatorKeys"),
              orderedIdentifierArray(value, "evidenceNodeIds"));
      if (gap.evidenceNodeIds().stream().anyMatch(id -> !evidenceNodeIds.contains(id))
          || ledgerGaps.put(gap.gapId(), gap) != null) {
        throw failure();
      }
    }
    return new FlowProvenanceSources(
        payloadReference(proofPack),
        provenFactsRef,
        gapLedgerRef,
        evidenceGraphRef,
        Set.copyOf(factIds),
        Set.copyOf(evidenceNodeIds),
        Map.copyOf(ledgerGaps));
  }

  private static List<JsonNode> normalizeGaps(
      List<JsonNode> compilationGaps, List<JsonNode> capsules, FlowProvenanceSources sources) {
    Map<String, JsonNode> compilerGapsById = indexed(compilationGaps, "gapId");
    Map<String, JsonNode> capsuleGapsById = new HashMap<>();
    for (JsonNode capsule : capsules) {
      for (JsonNode gap : array(capsule, "gapViews")) {
        String gapId = id(gap, "gapId");
        if (capsuleGapsById.put(gapId, gap) != null) throw failure();
      }
    }
    Set<String> gapIds = new HashSet<>(compilerGapsById.keySet());
    gapIds.addAll(capsuleGapsById.keySet());
    List<JsonNode> values = new ArrayList<>();
    for (String gapId : gapIds) {
      JsonNode compilerGap = compilerGapsById.get(gapId);
      JsonNode capsuleGap = capsuleGapsById.get(gapId);
      SourceLedgerGap sourceGap = sources.ledgerGapsById().get(gapId);
      if (sourceGap != null) {
        if (compilerGap != null) validateCompilerSourceGap(compilerGap, sourceGap);
        if (capsuleGap != null) validateCapsuleSourceGap(capsuleGap, sourceGap, sources);
        values.add(normalizeSourceGap(sourceGap, sources));
      } else if (compilerGap != null) {
        values.add(normalizeCompilerGap(compilerGap, sources));
      } else {
        values.add(normalizeProjectionGap(capsuleGap));
      }
    }
    return values.stream()
        .sorted(Comparator.comparing(value -> id(value, "gapId"), UTF8_ORDER))
        .toList();
  }

  private static ObjectNode normalizeSourceGap(SourceLedgerGap gap, FlowProvenanceSources sources) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("gapId", gap.gapId());
    value.put("scope", "FACT");
    value.put("reasonCode", gap.code());
    strings(value.putArray("affectedSemanticIds"), gap.affectedCandidateDenominatorKeys());
    references(
        value.putArray("evidenceRefs"),
        gap.evidenceNodeIds().isEmpty() ? List.of() : List.of(sources.evidenceGraphRef()));
    value.put("originKind", "PROVEN_CODE_FACTS_GAP_LEDGER");
    value.set("originGapLedgerRef", reference(sources.gapLedgerRef()));
    return value;
  }

  private static ObjectNode normalizeCompilerGap(JsonNode gap, FlowProvenanceSources sources) {
    requireExactFields(
        gap, Set.of("gapId", "scope", "reasonCode", "affectedSemanticIds", "evidenceNodeIds"));
    String gapId = id(gap, "gapId");
    String rawScope = text(gap, "scope");
    if (!"ENTRY".equals(rawScope) && !"FLOW".equals(rawScope)) throw failure();
    List<String> affected = orderedIdentifierArray(gap, "affectedSemanticIds");
    List<String> evidenceNodeIds = orderedIdentifierArray(gap, "evidenceNodeIds");
    if (evidenceNodeIds.stream().anyMatch(id -> !sources.evidenceNodeIds().contains(id))) {
      throw failure();
    }
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("gapId", gapId);
    value.put("scope", "FLOW");
    value.put("reasonCode", text(gap, "reasonCode"));
    strings(value.putArray("affectedSemanticIds"), affected);
    references(
        value.putArray("evidenceRefs"),
        evidenceNodeIds.isEmpty() ? List.of() : List.of(sources.evidenceGraphRef()));
    value.put("originKind", "FLOW_COMPILATION");
    value.putNull("originGapLedgerRef");
    return value;
  }

  private static ObjectNode normalizeProjectionGap(JsonNode gap) {
    requireGapViewFields(gap);
    String originKind = text(gap, "originKind");
    ArtifactReference ledger = nullableReference(gap, "originGapLedgerRef");
    if (!"CAPSULE_PROJECTION".equals(originKind) || ledger != null) throw failure();
    opaqueTexts(gap, "affectedSemanticIds");
    orderedReferences(gap, "evidenceRefs");
    return ((ObjectNode) gap).deepCopy();
  }

  private static void validateCompilerSourceGap(JsonNode gap, SourceLedgerGap source) {
    requireExactFields(
        gap, Set.of("gapId", "scope", "reasonCode", "affectedSemanticIds", "evidenceNodeIds"));
    if (!"FLOW".equals(text(gap, "scope"))
        || !source.code().equals(text(gap, "reasonCode"))
        || !source.affectedEntryIds().equals(orderedIdentifierArray(gap, "affectedSemanticIds"))
        || !source.evidenceNodeIds().equals(orderedIdentifierArray(gap, "evidenceNodeIds"))) {
      throw failure();
    }
  }

  private static void validateCapsuleSourceGap(
      JsonNode gap, SourceLedgerGap source, FlowProvenanceSources sources) {
    requireGapViewFields(gap);
    List<ArtifactReference> expectedEvidence =
        source.evidenceNodeIds().isEmpty() ? List.of() : List.of(sources.evidenceGraphRef());
    if (!"FACT".equals(text(gap, "scope"))
        || !source.code().equals(text(gap, "reasonCode"))
        || !source
            .affectedCandidateDenominatorKeys()
            .equals(opaqueTexts(gap, "affectedSemanticIds"))
        || !expectedEvidence.equals(orderedReferences(gap, "evidenceRefs"))
        || !"PROVEN_CODE_FACTS_GAP_LEDGER".equals(text(gap, "originKind"))
        || !sources.gapLedgerRef().equals(nullableReference(gap, "originGapLedgerRef"))) {
      throw failure();
    }
  }

  private static void validateFactOrigins(List<JsonNode> capsules, FlowProvenanceSources sources) {
    for (JsonNode capsule : capsules) {
      for (JsonNode fact : array(capsule, "factViews")) {
        if (!sources.factIds().contains(id(fact, "factId"))
            || !sources.provenFactsRef().equals(readReference(fact, "originFactArtifactRef"))) {
          throw failure();
        }
      }
    }
  }

  private static List<JsonNode> completePublicCapsules(
      List<JsonNode> capsules,
      Map<String, JsonNode> spansById,
      Map<String, JsonNode> obligationsById) {
    Set<String> usedSpanIds = new HashSet<>();
    Set<String> usedObligationIds = new HashSet<>();
    List<JsonNode> result = new ArrayList<>();
    for (JsonNode capsule : capsules) {
      ObjectNode publicCapsule = ((ObjectNode) capsule).deepCopy();
      String flowSliceId = id(capsule, "flowSliceId");
      List<String> signalIds = processJoinSignalIds(capsule, flowSliceId);
      List<String> spanIds = identifierArray(capsule, "modelEvidenceSpanIds");
      List<String> obligationIds = identifierArray(capsule, "projectionObligationIds");
      Map<String, List<String>> supportingSpanIdsBySignal = new HashMap<>();
      Map<String, List<JsonNode>> obligationsBySignal = new HashMap<>();
      ArrayNode spans = publicCapsule.putArray("modelEvidenceSpans");
      for (String spanId : spanIds) {
        JsonNode span = spansById.get(spanId);
        if (span == null || !usedSpanIds.add(spanId)) throw failure();
        for (String signalId : orderedIdentifierArray(span, "supportedProcessJoinSignalIds")) {
          if (!signalIds.contains(signalId)) throw failure();
          supportingSpanIdsBySignal
              .computeIfAbsent(signalId, ignored -> new ArrayList<>())
              .add(spanId);
        }
        spans.add(span.deepCopy());
      }
      ArrayNode obligations = publicCapsule.putArray("projectionObligations");
      for (String obligationId : obligationIds) {
        JsonNode obligation = obligationsById.get(obligationId);
        if (obligation == null || !usedObligationIds.add(obligationId)) throw failure();
        List<String> satisfyingSpanIds = identifierArray(obligation, "satisfyingSpanIds");
        if (satisfyingSpanIds.isEmpty() || !spanIds.containsAll(satisfyingSpanIds)) throw failure();
        if ("PROCESS_JOIN_SIGNAL_BASIS".equals(text(obligation, "kind"))) {
          String signalId = id(obligation, "semanticItemId");
          if (!signalIds.contains(signalId)) throw failure();
          obligationsBySignal
              .computeIfAbsent(signalId, ignored -> new ArrayList<>())
              .add(obligation);
        }
        obligations.add(obligation.deepCopy());
      }
      for (String signalId : signalIds) {
        List<String> supportingSpanIds =
            supportingSpanIdsBySignal.getOrDefault(signalId, List.of());
        List<JsonNode> signalObligations = obligationsBySignal.getOrDefault(signalId, List.of());
        if (supportingSpanIds.isEmpty()
            || signalObligations.size() != 1
            || !supportingSpanIds.equals(
                identifierArray(signalObligations.get(0), "satisfyingSpanIds"))) {
          throw failure();
        }
      }
      result.add(publicCapsule);
    }
    if (usedSpanIds.size() != spansById.size()
        || usedObligationIds.size() != obligationsById.size()) {
      throw failure();
    }
    return result.stream()
        .sorted(Comparator.comparing(value -> id(value, "flowSliceId"), UTF8_ORDER))
        .toList();
  }

  private List<CanonicalModulePayload> payloads(Material material, boolean closed) {
    List<CanonicalModulePayload> values = new ArrayList<>();
    ObjectNode flows = JsonNodeFactory.instance.objectNode();
    flows.set("flowCompilationRef", reference(material.compilerPayload()));
    flows.set("capsuleProjectionRef", reference(material.projectorPayload()));
    ArrayNode flowItems = flows.putArray("flowSlices");
    material.flows().forEach(value -> flowItems.add(value.deepCopy()));
    ArrayNode contexts = flows.putArray("entryContexts");
    material.entryContexts().forEach(value -> contexts.add(value.deepCopy()));
    values.add(
        standalone(
            "flow-slices.json",
            FLOW_SLICES_TYPE,
            FLOW_SLICES_SCHEMA,
            "business-flows-flow-slices",
            flows));

    ObjectNode coverage = JsonNodeFactory.instance.objectNode();
    strings(
        coverage.putArray("entryIds"),
        material.dispositions().stream().map(value -> id(value, "entryId")).toList());
    strings(
        coverage.putArray("compiledEntryIds"),
        entryIdsWithDisposition(material.dispositions(), "COMPILED"));
    strings(
        coverage.putArray("gappedEntryIds"),
        entryIdsWithDisposition(material.dispositions(), "GAP"));
    strings(
        coverage.putArray("excludedEntryIds"),
        entryIdsWithDisposition(material.dispositions(), "EXCLUDED"));
    strings(
        coverage.putArray("flowSliceIds"),
        material.flows().stream().map(value -> id(value, "flowSliceId")).toList());
    strings(coverage.putArray("processJoinSignalIds"), material.processJoinSignalIds());
    strings(
        coverage.putArray("capsuleIds"),
        material.capsules().stream().map(value -> id(value, "evidenceCapsuleId")).toList());
    strings(coverage.putArray("modelEligibleFlowSliceIds"), material.modelEligibleFlowIds());
    strings(coverage.putArray("modelIneligibleFlowSliceIds"), material.modelIneligibleFlowIds());
    strings(
        coverage.putArray("modelIneligibilityGapIds"),
        material.modelIneligibilityByFlow().values().stream()
            .flatMap(List::stream)
            .sorted(UTF8_ORDER)
            .toList());
    ArrayNode ineligibilityByFlow = coverage.putArray("modelIneligibilityByFlow");
    material
        .modelIneligibleFlowIds()
        .forEach(
            flowSliceId -> {
              ObjectNode item = ineligibilityByFlow.addObject();
              item.put("flowSliceId", flowSliceId);
              strings(
                  item.putArray("gapIds"), material.modelIneligibilityByFlow().get(flowSliceId));
            });
    strings(coverage.putArray("gapIds"), material.gapIds());
    coverage.put("closed", closed);
    values.add(
        standalone(
            "flow-coverage.json",
            COVERAGE_TYPE,
            COVERAGE_SCHEMA,
            "business-flows-flow-coverage",
            coverage));

    values.add(
        jsonl(
            "entry-dispositions.jsonl",
            ENTRY_TYPE,
            ENTRY_SCHEMA,
            "business-flows-entry-disposition",
            material.dispositions()));
    values.add(
        jsonl(
            "evidence-capsules.jsonl",
            CAPSULE_TYPE,
            CAPSULE_SCHEMA,
            "business-flows-evidence-capsule",
            material.capsules()));
    values.add(
        jsonl("flow-gaps.jsonl", GAP_TYPE, GAP_SCHEMA, "business-flows-flow-gap", material.gaps()));
    return values.stream()
        .sorted(Comparator.comparing(CanonicalModulePayload::fileName, UTF8_ORDER))
        .toList();
  }

  private CanonicalModulePayload standalone(
      String fileName, String type, String schema, String prefix, ObjectNode body) {
    ObjectNode document = body.deepCopy();
    document.put("schemaVersion", schema);
    document.put("artifactType", type);
    document.put("artifactId", "pending");
    ObjectNode withoutId = document.deepCopy();
    withoutId.remove("artifactId");
    ArtifactId id =
        ArtifactId.parse(
            prefix
                + ":"
                + sha256(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray())));
    document.put("artifactId", id.value());
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        id,
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(document));
  }

  private CanonicalModulePayload jsonl(
      String fileName, String type, String schema, String prefix, List<JsonNode> lines) {
    StringBuilder result = new StringBuilder();
    for (JsonNode value : lines) {
      ObjectNode line = ((ObjectNode) value).deepCopy();
      line.put("schemaVersion", schema);
      line.put("artifactType", type);
      result.append(
          new String(
              canonicalJson.encodeCanonical(line).copyToByteArray(), StandardCharsets.UTF_8));
      result.append('\n');
    }
    byte[] bytes = result.toString().getBytes(StandardCharsets.UTF_8);
    ArtifactId id =
        ArtifactId.parse(
            prefix
                + ":"
                + sha256(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(bytes)));
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        id,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private ReopenedAnalysisStepPublication reopen(
      org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reference,
      AnalysisStepKey expected) {
    if (reference == null || reference.address().analysisStepKey() != expected) throw failure();
    ReopenedAnalysisStepPublication reopened = analysisSteps.reopen(reference);
    if (!reference.equals(reopened.reference())
        || reopened.receipt().address().analysisStepKey() != expected) {
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

  private static VerifiedCanonicalPayload semanticPayload(
      ReopenedAnalysisStepPublication step, String fileName, String type, String schema) {
    return semanticPayload(step, fileName, type, schema, CanonicalMediaType.APPLICATION_JSON);
  }

  private static VerifiedCanonicalPayload semanticPayload(
      ReopenedAnalysisStepPublication step,
      String fileName,
      String type,
      String schema,
      CanonicalMediaType mediaType) {
    List<VerifiedCanonicalPayload> values =
        step.semanticPayloads().stream()
            .filter(value -> fileName.equals(value.descriptor().fileName()))
            .toList();
    if (values.size() != 1) throw failure();
    VerifiedCanonicalPayload value = values.get(0);
    if (!type.equals(value.descriptor().artifactType())
        || !schema.equals(value.descriptor().schemaVersion())
        || value.descriptor().mediaType() != mediaType) {
      throw failure();
    }
    return value;
  }

  private List<JsonNode> parseJsonLines(VerifiedCanonicalPayload payload) {
    String bytes = new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    if (bytes.isEmpty()) return List.of();
    if (!bytes.endsWith("\n")) throw failure();
    String[] lines = bytes.substring(0, bytes.length() - 1).split("\n", -1);
    List<JsonNode> values = new ArrayList<>();
    for (String line : lines) {
      if (line.isEmpty()) throw failure();
      values.add(
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))));
    }
    return List.copyOf(values);
  }

  private static ArtifactReference payloadReference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static void requireStandaloneHeader(
      JsonNode value, ArtifactReference expected, String type, String schema) {
    if (!schema.equals(text(value, "schemaVersion"))
        || !type.equals(text(value, "artifactType"))
        || !expected.artifactId().value().equals(id(value, "artifactId"))) {
      throw failure();
    }
  }

  private static CanonicalAnalysisStepPayload stepPayload(CanonicalModulePayload payload) {
    return new CanonicalAnalysisStepPayload(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8());
  }

  private static ObjectNode reference(ArtifactReference value) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", value.artifactId().value())
        .put("sha256", value.sha256().value());
  }

  private static void references(ArrayNode target, List<ArtifactReference> values) {
    values.forEach(value -> target.add(reference(value)));
  }

  private static ArtifactReference readReference(JsonNode source, String field) {
    JsonNode value = object(source, field);
    try {
      return new ArtifactReference(
          ArtifactId.parse(text(value, "artifactId")), new Sha256Digest(text(value, "sha256")));
    } catch (RuntimeException invalid) {
      throw failure();
    }
  }

  private static CapsuleProjectionProfile projectionProfile(JsonNode payload) {
    JsonNode profile = object(payload, "capsuleProjectionProfile");
    requireExactFields(
        profile,
        Set.of(
            "profileRef",
            "maxCapsules",
            "maxSpansPerCapsule",
            "maxSpanBytes",
            "maxCapsuleUtf8Bytes"));
    return new CapsuleProjectionProfile(
        readReference(profile, "profileRef"),
        positiveInt(profile, "maxCapsules"),
        positiveInt(profile, "maxSpansPerCapsule"),
        positiveInt(profile, "maxSpanBytes"),
        positiveInt(profile, "maxCapsuleUtf8Bytes"));
  }

  private static int positiveInt(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isInt() || value.intValue() < 1) throw failure();
    return value.intValue();
  }

  private static ArtifactReference nullableReference(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null) throw failure();
    return value.isNull() ? null : readReference(source, field);
  }

  private static List<ArtifactReference> orderedReferences(JsonNode source, String field) {
    List<ArtifactReference> values =
        array(source, field).stream()
            .map(
                value -> {
                  try {
                    return new ArtifactReference(
                        ArtifactId.parse(text(value, "artifactId")),
                        new Sha256Digest(text(value, "sha256")));
                  } catch (RuntimeException invalid) {
                    throw failure();
                  }
                })
            .toList();
    List<ArtifactReference> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
            .toList();
    if (!values.equals(ordered)
        || values.size()
            != values.stream().map(value -> value.artifactId().value()).distinct().count()) {
      throw failure();
    }
    return values;
  }

  private static List<JsonNode> sortedObjects(List<JsonNode> values, String idField) {
    List<JsonNode> result =
        values.stream()
            .filter(JsonNode::isObject)
            .sorted(Comparator.comparing(value -> id(value, idField), UTF8_ORDER))
            .toList();
    if (result.size() != values.size()
        || result.size() != result.stream().map(value -> id(value, idField)).distinct().count())
      throw failure();
    return result;
  }

  private static Set<String> ids(List<JsonNode> values, String field) {
    return values.stream()
        .map(value -> id(value, field))
        .collect(java.util.stream.Collectors.toCollection(HashSet::new));
  }

  private static Map<String, JsonNode> indexed(List<JsonNode> values, String idField) {
    Map<String, JsonNode> result = new HashMap<>();
    for (JsonNode value : values) {
      String id = id(value, idField);
      if (result.put(id, value) != null) throw failure();
    }
    return Map.copyOf(result);
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

  private static void requireJsonLineHeader(JsonNode value, String schema) {
    if (!value.isObject()
        || !schema.equals(text(value, "schemaVersion"))
        || value.has("artifactType")
        || value.has("artifactId")) {
      throw failure();
    }
  }

  private static boolean requiredBoolean(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isBoolean()) throw failure();
    return value.booleanValue();
  }

  private static int nonnegativeInt(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isInt() || value.intValue() < 0) throw failure();
    return value.intValue();
  }

  private static List<String> flowOutcomePathIds(List<JsonNode> flows) {
    return flows.stream()
        .flatMap(value -> array(value, "outcomePaths").stream())
        .map(value -> id(value, "outcomePathId"))
        .sorted(UTF8_ORDER)
        .toList();
  }

  private static List<String> flowIdentifiers(List<JsonNode> flows, String field) {
    return flows.stream()
        .flatMap(value -> orderedIdentifierArray(value, field).stream())
        .sorted(UTF8_ORDER)
        .toList();
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

  private static List<String> processJoinSignalIds(JsonNode owner, String flowSliceId) {
    List<String> values = new ArrayList<>();
    for (JsonNode signal : array(owner, "processJoinSignals")) {
      requireProcessJoinSignalFields(signal);
      String signalId = id(signal, "processJoinSignalId");
      if (!flowSliceId.equals(id(signal, "flowSliceId"))) throw failure();
      text(signal, "signalKind");
      text(signal, "anchorKind");
      text(signal, "anchorKey");
      text(signal, "direction");
      text(signal, "specificity");
      text(signal, "claimScope");
      orderedIdentifierArray(signal, "factIds");
      orderedIdentifierArray(signal, "atomIds");
      orderedIdentifierArray(signal, "proofIds");
      orderedIdentifierArray(signal, "evidenceNodeIds");
      sourceLocators(signal);
      orderedIdentifierArray(signal, "gapIds");
      values.add(signalId);
    }
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (!values.equals(ordered) || ordered.size() != ordered.stream().distinct().count()) {
      throw failure();
    }
    return ordered;
  }

  private static List<String> orderedIdentifierArray(JsonNode source, String field) {
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
            .toList();
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (!values.equals(ordered) || ordered.size() != ordered.stream().distinct().count()) {
      throw failure();
    }
    return ordered;
  }

  private static List<String> opaqueTexts(JsonNode source, String field) {
    List<String> values =
        array(source, field).stream()
            .map(
                value -> {
                  if (!value.isTextual() || value.textValue().isBlank()) throw failure();
                  return value.textValue();
                })
            .toList();
    if (values.size() != values.stream().distinct().count()) throw failure();
    return values;
  }

  private static void sourceLocators(JsonNode signal) {
    for (JsonNode locator : array(signal, "sourceLocators")) {
      requireExactFields(
          locator,
          Set.of(
              "fileId",
              "path",
              "startByte",
              "endByteExclusive",
              "startLine",
              "startColumn",
              "endLine",
              "endColumn"));
      id(locator, "fileId");
      text(locator, "path");
      if (!locator.path("startByte").canConvertToLong()
          || !locator.path("endByteExclusive").canConvertToLong()
          || !locator.path("startLine").canConvertToInt()
          || !locator.path("startColumn").canConvertToInt()
          || !locator.path("endLine").canConvertToInt()
          || !locator.path("endColumn").canConvertToInt()) {
        throw failure();
      }
    }
  }

  private static void requireProcessJoinSignalFields(JsonNode signal) {
    requireExactFields(
        signal,
        Set.of(
            "processJoinSignalId",
            "flowSliceId",
            "signalKind",
            "anchorKind",
            "anchorKey",
            "direction",
            "specificity",
            "claimScope",
            "factIds",
            "atomIds",
            "proofIds",
            "evidenceNodeIds",
            "sourceLocators",
            "gapIds"));
  }

  private static void requireGapViewFields(JsonNode gap) {
    requireExactFields(
        gap,
        Set.of(
            "gapId",
            "scope",
            "reasonCode",
            "affectedSemanticIds",
            "evidenceRefs",
            "originKind",
            "originGapLedgerRef"));
  }

  private static void requireExactFields(JsonNode value, Set<String> expected) {
    if (value == null || !value.isObject()) throw failure();
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw failure();
  }

  private static List<String> entryIdsWithDisposition(
      List<JsonNode> dispositions, String expected) {
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
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static int compareUtf8(String left, String right) {
    byte[] first = left.getBytes(StandardCharsets.UTF_8);
    byte[] second = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int result =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
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
      List<JsonNode> entryContexts,
      List<JsonNode> dispositions,
      List<JsonNode> capsules,
      List<JsonNode> gaps,
      List<String> gapIds,
      List<String> processJoinSignalIds,
      List<String> modelEligibleFlowIds,
      List<String> modelIneligibleFlowIds,
      Map<String, List<String>> modelIneligibilityByFlow) {}

  private record DiscoveryCoverage(List<String> entryIds, boolean closed) {}

  private record LocalFlowCoverage(boolean closed) {}

  private record SourceLedgerGap(
      String gapId,
      String code,
      List<String> affectedEntryIds,
      List<String> affectedCandidateDenominatorKeys,
      List<String> evidenceNodeIds) {}

  private record FlowProvenanceSources(
      ArtifactReference proofPack,
      ArtifactReference provenFactsRef,
      ArtifactReference gapLedgerRef,
      ArtifactReference evidenceGraphRef,
      Set<String> factIds,
      Set<String> evidenceNodeIds,
      Map<String, SourceLedgerGap> ledgerGapsById) {}
}
