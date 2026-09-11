package org.sourceanalysis.app.analysis.interpretation.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * M6's program-only compiler of cross-Flow candidate material.
 *
 * <p>It reads only receipt-verified Stage 03–05 publications and the frozen M3 registry. It does
 * not read source bytes, call a provider, infer an execution order, or create a business-process
 * hypothesis.
 */
public final class CrossFlowCandidateCompiler {

  private static final Comparator<String> UTF8_ORDER = CrossFlowCandidateCompiler::compareUtf8;
  private static final Pattern METHOD_TARGET =
      Pattern.compile(".+#[A-Za-z_$][A-Za-z0-9_$]*\\([^)]*\\)");
  private static final String ENTRY_ROOT_RULE = "control-flow-entry-root-v1";
  private static final String REGISTRY_FILE = "repository-interpretation-registry.json";
  private static final String REGISTRY_TYPE =
      "FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY_MODULE";
  private static final String REGISTRY_SCHEMA =
      "flow-interpretation-repository-interpretation-registry-module-v1";

  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final ProcessInputArtifactReader inputArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the M6 compiler over receipt-verified artifact stores. */
  public CrossFlowCandidateCompiler(
      CanonicalAnalysisStepArtifactStore analysisSteps,
      CanonicalModuleArtifactStore moduleArtifacts,
      ProcessInputArtifactReader inputArtifacts) {
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis-step artifact store");
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.inputArtifacts = Objects.requireNonNull(inputArtifacts, "process input artifact reader");
  }

  /** Compiles deterministic candidate relations and groups without invoking a model. */
  public CrossFlowCandidateCompilation compileCandidates(
      CrossFlowCandidateCompilationRequest request) {
    try {
      Objects.requireNonNull(request, "cross-Flow request");
      ReopenedAnalysisStepPublication graphs = reopenGraphs(request.programGraphs());
      ReopenedAnalysisStepPublication facts = reopenFacts(request.provenCodeFacts());
      ReopenedAnalysisStepPublication flows = reopenFlows(request.businessFlows());
      ReopenedModulePublication registry =
          reopenRegistry(request.repositoryInterpretationRegistryPublication());
      requireSharedLineage(graphs, facts, flows, registry);
      ProcessInputControls processInputs =
          reopenProcessInputControls(request.analysisRunRequestRef(), flows.receipt().controls());

      GraphMaterial graphMaterial = readGraphs(graphs);
      FactMaterial factMaterial = readFacts(facts, graphMaterial);
      FlowMaterial flowMaterial = readFlows(flows);
      verifySignalProofClosure(flowMaterial, factMaterial, graphMaterial);
      List<RegistryItem> registryItems = readRegistry(registry, request.businessFlows());
      Map<String, String> entryTargets = entryTargets(flowMaterial.flowsById(), graphMaterial);
      List<SemanticCueSupport> semanticCues =
          semanticCues(
              flowMaterial,
              factMaterial,
              graphMaterial,
              registryItems,
              processInputs.processCueProfileRef());
      List<ProcessCandidateRelationV2> relations =
          candidateRelations(flowMaterial, entryTargets, semanticCues);
      List<ProcessEvidenceGroupV2> groups =
          groups(flowMaterial, registryItems, relations, semanticCues, processInputs.limits());
      List<String> flowIds = sorted(flowMaterial.flowsById().keySet());
      CrossFlowCandidateAccountingV1 accounting =
          new CrossFlowCandidateAccountingV1(
              flowIds,
              relations.stream().map(ProcessCandidateRelationV2::candidateRelationId).toList(),
              groups.stream().map(ProcessEvidenceGroupV2::processEvidenceGroupId).toList(),
              List.of(),
              flowIds.size(),
              relations.size(),
              groups.size(),
              0,
              true);
      return new CrossFlowCandidateCompilation(
          compilationId(request, flowIds, relations, groups),
          request.programGraphs().publication(),
          request.provenCodeFacts().publication(),
          request.businessFlows().publication(),
          request.repositoryInterpretationRegistryPublication(),
          request.analysisRunRequestRef(),
          flowIds,
          relations,
          groups,
          List.of(),
          processInputs.limits(),
          accounting,
          true);
    } catch (CrossFlowCandidateCompilationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID", failure);
    }
  }

  private ProcessInputControls reopenProcessInputControls(
      ArtifactReference runRequestReference, ArtifactControls controls) {
    JsonNode runRequest = reopenInput(runRequestReference, "FLOW_INTERPRETATION_INPUT_INVALID");
    requireExactFields(
        runRequest,
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
            "toolchainRef"),
        "FLOW_INTERPRETATION_INPUT_INVALID");
    if (!"analysis-run-request-v2".equals(text(runRequest, "schemaVersion"))) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    ArtifactReference profileReference =
        reference(runRequest, "profileBundleRef", "FLOW_INTERPRETATION_INPUT_INVALID");
    ArtifactReference budgetReference =
        reference(runRequest, "resourceBudgetRef", "FLOW_INTERPRETATION_INPUT_INVALID");
    if (!profileReference.sha256().equals(controls.profileSha256())) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    JsonNode profile = reopenInput(profileReference, "PROCESS_CUE_PROFILE_INVALID");
    JsonNode interpretation = object(profile, "flowInterpretation", "PROCESS_CUE_PROFILE_INVALID");
    requireExactFields(
        interpretation,
        Set.of("crossFlowCandidateRuleVersion", "processCueProfile", "processModelRuntimeRef"),
        "PROCESS_CUE_PROFILE_INVALID");
    if (!"flow-interpretation-cross-flow-candidate-rules-v1"
        .equals(text(interpretation, "crossFlowCandidateRuleVersion"))) {
      throw failure("PROCESS_CUE_PROFILE_INVALID");
    }
    ArtifactReference processModelRuntimeRef =
        reference(interpretation, "processModelRuntimeRef", "PROCESS_CUE_PROFILE_INVALID");
    JsonNode cueProfile =
        object(interpretation, "processCueProfile", "PROCESS_CUE_PROFILE_INVALID");
    requireExactFields(
        cueProfile,
        Set.of("schemaVersion", "normalizationRule", "entryVerbLexicon", "stateWordLexicon"),
        "PROCESS_CUE_PROFILE_INVALID");
    if (!"flow-interpretation-process-cue-profile-v1".equals(text(cueProfile, "schemaVersion"))
        || !"R0_NFC_EXACT_V1".equals(text(cueProfile, "normalizationRule"))) {
      throw failure("PROCESS_CUE_PROFILE_INVALID");
    }
    verifyLexicon(cueProfile, "entryVerbLexicon");
    verifyLexicon(cueProfile, "stateWordLexicon");
    JsonNode budget = reopenInput(budgetReference, "FLOW_INTERPRETATION_INPUT_INVALID");
    JsonNode budgetInterpretation =
        object(budget, "flowInterpretation", "FLOW_INTERPRETATION_INPUT_INVALID");
    return new ProcessInputControls(
        profileReference,
        processModelRuntimeRef,
        new ProcessMaterialLimitsV1(
            positiveInt(budgetInterpretation, "maxFlowsPerProcessGroup"),
            positiveInt(budgetInterpretation, "maxRelationsPerProcessGroup"),
            positiveInt(budgetInterpretation, "maxProcessJoinSignals"),
            positiveInt(budgetInterpretation, "maxRegistryItems"),
            positiveInt(budgetInterpretation, "maxProcessInputBytes"),
            positiveInt(budgetInterpretation, "maxProcessHypotheses"),
            positiveInt(budgetInterpretation, "maxClaimsPerProcessHypothesis"),
            positiveInt(budgetInterpretation, "maxReaderSlots")));
  }

  private JsonNode reopenInput(ArtifactReference reference, String failureCode) {
    try {
      if (reference == null) throw failure(failureCode);
      ImmutableBytes bytes = inputArtifacts.reopen(reference);
      if (bytes == null) throw failure(failureCode);
      return canonicalJson.parseCanonical(bytes);
    } catch (CrossFlowCandidateCompilationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure(failureCode, failure);
    }
  }

  private static JsonNode object(JsonNode source, String field, String failureCode) {
    JsonNode value = source.get(field);
    if (value == null || !value.isObject()) throw failure(failureCode);
    return value;
  }

  private static void requireExactFields(
      JsonNode source, Set<String> expected, String failureCode) {
    Set<String> actual = new HashSet<>();
    source.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw failure(failureCode);
  }

  private static ArtifactReference reference(JsonNode source, String field, String failureCode) {
    JsonNode value = object(source, field, failureCode);
    requireExactFields(value, Set.of("artifactId", "sha256"), failureCode);
    try {
      return new ArtifactReference(
          ArtifactId.parse(text(value, "artifactId")),
          new org.sourceanalysis.app.artifact.Sha256Digest(text(value, "sha256")));
    } catch (RuntimeException invalid) {
      throw failure(failureCode, invalid);
    }
  }

  private static void verifyLexicon(JsonNode profile, String field) {
    List<String> values =
        array(profile, field).stream()
            .map(
                value -> {
                  if (!value.isTextual()
                      || value.textValue().isBlank()
                      || !Normalizer.isNormalized(value.textValue(), Normalizer.Form.NFC)) {
                    throw failure("PROCESS_CUE_PROFILE_INVALID");
                  }
                  return value.textValue();
                })
            .toList();
    if (!values.equals(sorted(values)) || values.size() != new HashSet<>(values).size()) {
      throw failure("PROCESS_CUE_PROFILE_INVALID");
    }
  }

  private static int positiveInt(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.canConvertToInt() || value.intValue() < 1) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    return value.intValue();
  }

  private ReopenedAnalysisStepPublication reopenGraphs(ProgramGraphsReference reference) {
    return reopenStep(
        reference == null ? null : reference.publication(), AnalysisStepKey.PROGRAM_GRAPHS);
  }

  private ReopenedAnalysisStepPublication reopenFacts(ProvenCodeFactsReference reference) {
    return reopenStep(
        reference == null ? null : reference.publication(), AnalysisStepKey.PROVEN_CODE_FACTS);
  }

  private ReopenedAnalysisStepPublication reopenFlows(BusinessFlowsReference reference) {
    return reopenStep(
        reference == null ? null : reference.publication(), AnalysisStepKey.BUSINESS_FLOWS);
  }

  private ReopenedAnalysisStepPublication reopenStep(
      org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reference,
      AnalysisStepKey key) {
    if (reference == null || reference.address().analysisStepKey() != key) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    ReopenedAnalysisStepPublication opened = analysisSteps.reopen(reference);
    if (!reference.equals(opened.reference())
        || opened.receipt().address().analysisStepKey() != key
        || !reference.analysisStepArtifactRoot().equals(opened.receipt().analysisStepArtifactRoot())
        || !reference.analysisStepReceiptId().equals(opened.receipt().analysisStepReceiptId())) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    return opened;
  }

  private ReopenedModulePublication reopenRegistry(ModulePublicationReference reference) {
    if (reference == null) throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    ReopenedModulePublication opened = moduleArtifacts.reopen(reference);
    if (!reference.equals(opened.reference())
        || !(opened.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 3
        || !"registry-freezer".equals(address.moduleKey())
        || opened.payloads().size() != 1) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    ArtifactDescriptor descriptor = opened.payloads().get(0).descriptor();
    if (!REGISTRY_FILE.equals(descriptor.fileName())
        || !REGISTRY_TYPE.equals(descriptor.artifactType())
        || !REGISTRY_SCHEMA.equals(descriptor.schemaVersion())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return opened;
  }

  private void requireSharedLineage(
      ReopenedAnalysisStepPublication graphs,
      ReopenedAnalysisStepPublication facts,
      ReopenedAnalysisStepPublication flows,
      ReopenedModulePublication registry) {
    if (!graphs.reference().address().runId().equals(facts.reference().address().runId())
        || !graphs.reference().address().runId().equals(flows.reference().address().runId())
        || !graphs.receipt().controls().equals(facts.receipt().controls())
        || !graphs.receipt().controls().equals(flows.receipt().controls())
        || !flows.receipt().upstreamAnalysisStepReferences().contains(graphs.reference())
        || !flows.receipt().upstreamAnalysisStepReferences().contains(facts.reference())
        || !registry.receipt().address().runId().equals(flows.reference().address().runId())
        || !registry.receipt().controls().equals(flows.receipt().controls())) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
  }

  private GraphMaterial readGraphs(ReopenedAnalysisStepPublication publication) {
    if (publication.semanticPayloads().size() != 7) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    JsonNode structure = payload(publication, "code-structure-graph.json");
    JsonNode control = payload(publication, "control-flow-graph.json");
    JsonNode evidence = payload(publication, "evidence-graph.json");
    requireEnvelope(
        structure, "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH", "program-graphs-code-structure-graph-v1");
    requireEnvelope(
        control, "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH", "program-graphs-control-flow-graph-v2");
    requireEnvelope(evidence, "PROGRAM_GRAPHS_EVIDENCE_GRAPH", "program-graphs-evidence-graph-v3");
    Map<String, StructureNode> structureNodes = new HashMap<>();
    for (JsonNode node : array(structure, "nodes")) {
      String id = identifier(node, "nodeId");
      if (structureNodes.put(
              id, new StructureNode(id, text(node, "kind"), text(node, "canonicalValue")))
          != null) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
    }
    Map<String, ControlNode> controlNodes = new HashMap<>();
    for (JsonNode node : array(control, "nodes")) {
      String id = identifier(node, "nodeId");
      if (controlNodes.put(
              id, new ControlNode(id, text(node, "kind"), identifiers(node, "owningEntryIds")))
          != null) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
    }
    Map<String, ControlEdge> controlEdges = new HashMap<>();
    for (JsonNode edge : array(control, "edges")) {
      String id = identifier(edge, "edgeId");
      if (controlEdges.put(
              id,
              new ControlEdge(
                  id,
                  text(edge, "kind"),
                  identifier(edge, "fromNodeId"),
                  identifier(edge, "toNodeId"),
                  text(edge, "ruleId"),
                  text(edge, "resolution"),
                  nullableIdentifier(edge, "guardNodeId"),
                  nullableText(edge, "polarity")))
          != null) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
    }
    Map<String, Traversal> traversals = new HashMap<>();
    for (JsonNode traversal : array(control, "semanticTraversalOrder")) {
      String entryId = identifier(traversal, "entryId");
      if (traversals.put(
              entryId,
              new Traversal(identifiers(traversal, "nodeIds"), identifiers(traversal, "edgeIds")))
          != null) {
        throw failure("PROCESS_ENTRY_TARGET_INVALID");
      }
    }
    Map<String, EvidenceNode> evidenceNodes = new HashMap<>();
    for (JsonNode node : array(evidence, "nodes")) {
      String id = identifier(node, "evidenceNodeId");
      JsonNode sourceExcerpt = node.get("sourceExcerpt");
      SourceLocatorV1 locator =
          sourceExcerpt == null || sourceExcerpt.isNull()
              ? null
              : locator(sourceExcerpt.path("locator"));
      if (evidenceNodes.put(id, new EvidenceNode(id, locator)) != null) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
    }
    return new GraphMaterial(
        Map.copyOf(structureNodes),
        Map.copyOf(controlNodes),
        Map.copyOf(controlEdges),
        Map.copyOf(traversals),
        Map.copyOf(evidenceNodes));
  }

  /* Test seam for reopening the three persisted inputs that establish signal evidence closure. */
  private FlowMaterial readVerifiedFlows(
      ReopenedAnalysisStepPublication flows,
      ReopenedAnalysisStepPublication facts,
      ReopenedAnalysisStepPublication graphs) {
    GraphMaterial graphMaterial = readGraphs(graphs);
    FactMaterial factMaterial = readFacts(facts, graphMaterial);
    FlowMaterial flowMaterial = readFlows(flows);
    verifySignalProofClosure(flowMaterial, factMaterial, graphMaterial);
    return flowMaterial;
  }

  private FactMaterial readFacts(
      ReopenedAnalysisStepPublication publication, GraphMaterial graphMaterial) {
    if (publication.semanticPayloads().size() != 4) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    JsonNode provenFacts = payload(publication, "proven-facts.json");
    JsonNode proofPack = payload(publication, "proof-pack.json");
    requireEnvelope(
        provenFacts, "PROVEN_CODE_FACTS_PROVEN_FACTS", "proven-code-facts-proven-facts-v3");
    requireEnvelope(proofPack, "PROVEN_CODE_FACTS_PROOF_PACK", "proven-code-facts-proof-pack-v3");
    Map<String, AtomOwnership> atoms = new HashMap<>();
    Set<String> factIds = new HashSet<>();
    for (JsonNode fact : array(provenFacts, "codeFacts")) {
      String factId = identifier(fact, "factId");
      if (!factIds.add(factId)) throw failure("PROCESS_PROOF_CLOSURE_INVALID");
      for (JsonNode atom : array(fact, "atoms")) {
        String atomId = identifier(atom, "atomId");
        String proofId = identifier(atom, "proofId");
        if (atoms.put(atomId, new AtomOwnership(atomId, factId, proofId)) != null) {
          throw failure("PROCESS_PROOF_CLOSURE_INVALID");
        }
      }
    }
    Map<String, ClosedProof> proofs = new HashMap<>();
    for (JsonNode proof : array(proofPack, "atomProofs")) {
      String proofId = identifier(proof, "proofId");
      String factId = identifier(proof, "factId");
      String atomId = identifier(proof, "atomId");
      List<String> evidenceIds = identifiers(proof, "requiredEvidenceNodeIds");
      String rootEvidenceId = identifier(proof, "rootEvidenceNodeId");
      AtomOwnership atom = atoms.get(atomId);
      if (!"CLOSED".equals(text(proof, "status"))
          || atom == null
          || !factId.equals(atom.factId())
          || !proofId.equals(atom.proofId())
          || evidenceIds.isEmpty()
          || !evidenceIds.contains(rootEvidenceId)
          || evidenceIds.stream().anyMatch(id -> graphMaterial.evidenceNodes().get(id) == null)) {
        throw failure("PROCESS_PROOF_CLOSURE_INVALID");
      }
      if (proofs.put(proofId, new ClosedProof(proofId, factId, atomId, evidenceIds)) != null) {
        throw failure("PROCESS_PROOF_CLOSURE_INVALID");
      }
    }
    if (atoms.values().stream().anyMatch(atom -> !proofs.containsKey(atom.proofId()))) {
      throw failure("PROCESS_PROOF_CLOSURE_INVALID");
    }
    return new FactMaterial(Set.copyOf(factIds), Map.copyOf(atoms), Map.copyOf(proofs));
  }

  private FlowMaterial readFlows(ReopenedAnalysisStepPublication publication) {
    if (publication.semanticPayloads().size() != 5) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    JsonNode flowDocument = payload(publication, "flow-slices.json");
    JsonNode coverage = payload(publication, "flow-coverage.json");
    requireEnvelope(flowDocument, "BUSINESS_FLOWS_FLOW_SLICES", "business-flows-flow-slices-v4");
    requireEnvelope(coverage, "BUSINESS_FLOWS_FLOW_COVERAGE", "business-flows-flow-coverage-v1");
    Map<String, Capsule> capsules = new HashMap<>();
    for (JsonNode capsule : jsonLines(payloadBytes(publication, "evidence-capsules.jsonl"))) {
      requireLineEnvelope(
          capsule, "BUSINESS_FLOWS_EVIDENCE_CAPSULE", "business-flows-evidence-capsule-v6");
      String flowId = identifier(capsule, "flowSliceId");
      Capsule value =
          new Capsule(
              identifier(capsule, "evidenceCapsuleId"),
              text(capsule, "modelEligibility"),
              identifiers(capsule, "modelIneligibilityGapIds"),
              identifiers(capsule, "registryProposalBasisAtomIds"),
              identifiers(capsule, "registryProposalBasisGapIds"),
              identifiers(capsule, "modelEvidenceSpanIds"),
              identifiers(capsule, "projectionObligationIds"),
              signals(capsule, flowId),
              identifiersFromObjects(capsule, "factViews", "factId"),
              identifiersFromObjects(capsule, "gapViews", "gapId"),
              identifiersFromObjects(capsule, "outcomePathViews", "outcomePathId"),
              capsuleSpans(capsule),
              capsuleObligations(capsule));
      if (capsules.put(flowId, value) != null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    Map<String, Flow> flows = new HashMap<>();
    for (JsonNode value : array(flowDocument, "flowSlices")) {
      String flowId = identifier(value, "flowSliceId");
      Capsule capsule = capsules.get(flowId);
      if (capsule == null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      List<Signal> flowSignals = signals(value, flowId);
      if (!flowSignals.equals(capsule.signals())) {
        // The public Flow and Capsule are required to retain the exact same signal list.
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      if (flows.put(
              flowId,
              new Flow(
                  flowId,
                  identifier(value, "entryId"),
                  identifier(value, "rootNodeId"),
                  flowSignals,
                  capsule))
          != null) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
    }
    List<String> coverageFlowIds = identifiers(coverage, "flowSliceIds");
    if (!coverageFlowIds.equals(sorted(flows.keySet()))
        || !capsules.keySet().equals(flows.keySet())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return new FlowMaterial(Map.copyOf(flows));
  }

  private List<CapsuleSpan> capsuleSpans(JsonNode capsule) {
    Map<String, CapsuleSpan> values = new HashMap<>();
    for (JsonNode span : array(capsule, "modelEvidenceSpans")) {
      String spanId = identifier(span, "spanId");
      SourceLocatorV1 locator = locator(span.path("sourceExcerpt").path("locator"));
      CapsuleSpan value =
          new CapsuleSpan(
              spanId,
              locator,
              identifiers(span, "supportedAtomIds"),
              identifiers(span, "supportedProcessJoinSignalIds"));
      if (values.put(spanId, value) != null) throw failure("PROCESS_PROOF_CLOSURE_INVALID");
    }
    return values.values().stream()
        .sorted(Comparator.comparing(CapsuleSpan::spanId, UTF8_ORDER))
        .toList();
  }

  private List<CapsuleObligation> capsuleObligations(JsonNode capsule) {
    Map<String, CapsuleObligation> values = new HashMap<>();
    for (JsonNode obligation : array(capsule, "projectionObligations")) {
      String obligationId = identifier(obligation, "obligationId");
      CapsuleObligation value =
          new CapsuleObligation(
              obligationId,
              text(obligation, "kind"),
              identifier(obligation, "semanticItemId"),
              identifiers(obligation, "satisfyingSpanIds"));
      if (values.put(obligationId, value) != null) {
        throw failure("PROCESS_PROOF_CLOSURE_INVALID");
      }
    }
    return values.values().stream()
        .sorted(Comparator.comparing(CapsuleObligation::obligationId, UTF8_ORDER))
        .toList();
  }

  private void verifySignalProofClosure(
      FlowMaterial material, FactMaterial factMaterial, GraphMaterial graphMaterial) {
    for (Flow flow : material.flowsById().values()) {
      for (Signal signal : flow.signals()) {
        verifySignalProofClosure(signal, factMaterial, graphMaterial);
      }
      verifyCapsuleProjectionClosure(flow.capsule(), flow.signals(), graphMaterial);
    }
  }

  private void verifySignalProofClosure(Signal signal, FactMaterial facts, GraphMaterial graphs) {
    if (signal.factIds().isEmpty()
        || signal.atomIds().isEmpty()
        || signal.proofIds().isEmpty()
        || signal.evidenceNodeIds().isEmpty()
        || signal.sourceLocators().isEmpty()
        || !facts.factIds().containsAll(signal.factIds())
        || signal.evidenceNodeIds().stream()
            .anyMatch(id -> graphs.evidenceNodes().get(id) == null)) {
      throw failure("PROCESS_PROOF_CLOSURE_INVALID");
    }
    for (String atomId : signal.atomIds()) {
      AtomOwnership atom = facts.atomsById().get(atomId);
      if (atom == null
          || !signal.factIds().contains(atom.factId())
          || !signal.proofIds().contains(atom.proofId())) {
        throw failure("PROCESS_PROOF_CLOSURE_INVALID");
      }
    }
    for (String proofId : signal.proofIds()) {
      ClosedProof proof = facts.proofsById().get(proofId);
      if (proof == null
          || !signal.factIds().contains(proof.factId())
          || !signal.atomIds().contains(proof.atomId())
          || !signal.evidenceNodeIds().containsAll(proof.requiredEvidenceNodeIds())) {
        throw failure("PROCESS_PROOF_CLOSURE_INVALID");
      }
    }
    Set<SourceLocatorV1> evidenceLocators = new HashSet<>();
    for (String evidenceId : signal.evidenceNodeIds()) {
      EvidenceNode evidence = graphs.evidenceNodes().get(evidenceId);
      if (evidence == null) {
        throw failure("PROCESS_PROOF_CLOSURE_INVALID");
      }
      if (evidence.sourceLocator() != null) {
        evidenceLocators.add(evidence.sourceLocator());
      }
    }
    if (evidenceLocators.isEmpty() || !evidenceLocators.containsAll(signal.sourceLocators())) {
      throw failure("PROCESS_PROOF_CLOSURE_INVALID");
    }
  }

  private void verifyCapsuleProjectionClosure(
      Capsule capsule, List<Signal> signals, GraphMaterial graphs) {
    Map<String, CapsuleSpan> spans = new HashMap<>();
    for (CapsuleSpan span : capsule.spans()) {
      if (spans.put(span.spanId(), span) != null) throw failure("PROCESS_PROOF_CLOSURE_INVALID");
    }
    Map<String, CapsuleObligation> obligations = new HashMap<>();
    for (CapsuleObligation obligation : capsule.obligations()) {
      if (obligations.put(obligation.obligationId(), obligation) != null) {
        throw failure("PROCESS_PROOF_CLOSURE_INVALID");
      }
    }
    if (!sorted(spans.keySet()).equals(capsule.modelEvidenceSpanIds())
        || !sorted(obligations.keySet()).equals(capsule.projectionObligationIds())) {
      throw failure("PROCESS_PROOF_CLOSURE_INVALID");
    }
    for (Signal signal : signals) {
      List<CapsuleSpan> supporting =
          spans.values().stream()
              .filter(
                  span ->
                      span.supportedProcessJoinSignalIds().contains(signal.processJoinSignalId()))
              .sorted(Comparator.comparing(CapsuleSpan::spanId, UTF8_ORDER))
              .toList();
      List<CapsuleObligation> signalObligations =
          obligations.values().stream()
              .filter(value -> "PROCESS_JOIN_SIGNAL_BASIS".equals(value.kind()))
              .filter(value -> signal.processJoinSignalId().equals(value.semanticItemId()))
              .toList();
      if (supporting.isEmpty()
          || signalObligations.size() != 1
          || !supporting.stream()
              .map(CapsuleSpan::spanId)
              .toList()
              .equals(signalObligations.get(0).satisfyingSpanIds())) {
        throw failure("PROCESS_PROOF_CLOSURE_INVALID");
      }
      for (CapsuleSpan span : supporting) {
        if (!signal.sourceLocators().contains(span.sourceLocator())
            || graphs.evidenceNodes().values().stream()
                .noneMatch(value -> span.sourceLocator().equals(value.sourceLocator()))) {
          throw failure("PROCESS_PROOF_CLOSURE_INVALID");
        }
      }
    }
  }

  private List<RegistryItem> readRegistry(
      ReopenedModulePublication publication, BusinessFlowsReference businessFlows) {
    JsonNode envelope = canonicalJson.parseCanonical(publication.payloads().get(0).canonicalUtf8());
    JsonNode value = envelope.path("payload");
    if (!value.isObject() || !booleanValue(value, "closed")) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    List<RegistryItem> items = new ArrayList<>();
    for (JsonNode item : array(value, "items")) {
      String key = text(item, "provisionalKey");
      String flowId = identifier(item, "flowSliceId");
      String capsuleId = identifier(item, "evidenceCapsuleId");
      String kind = text(item, "proposalKind");
      String label = text(item, "normalizedLabel");
      if (!Normalizer.isNormalized(label, Normalizer.Form.NFC)) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      items.add(
          new RegistryItem(
              key,
              flowId,
              capsuleId,
              kind,
              label,
              identifiers(item, "basisAtomIds"),
              identifiers(item, "basisGapIds")));
    }
    return items.stream()
        .sorted(Comparator.comparing(RegistryItem::registryItemId, UTF8_ORDER))
        .toList();
  }

  private Map<String, String> entryTargets(Map<String, Flow> flows, GraphMaterial graph) {
    Map<String, String> values = new HashMap<>();
    for (Flow flow : flows.values()) {
      ControlNode root = graph.controlNodes().get(flow.rootNodeId());
      Traversal traversal = graph.traversals().get(flow.entryId());
      if (root == null
          || !"ENTRY".equals(root.kind())
          || !root.owningEntryIds().contains(flow.entryId())
          || traversal == null
          || !traversal.nodeIds().contains(flow.rootNodeId())) {
        throw failure("PROCESS_ENTRY_TARGET_INVALID");
      }
      List<ControlEdge> roots =
          graph.controlEdges().values().stream()
              .filter(edge -> flow.rootNodeId().equals(edge.fromNodeId()))
              .filter(edge -> "NEXT".equals(edge.kind()))
              .filter(edge -> ENTRY_ROOT_RULE.equals(edge.ruleId()))
              .filter(edge -> "EXACT".equals(edge.resolution()))
              .filter(edge -> edge.guardNodeId() == null && edge.polarity() == null)
              .filter(edge -> traversal.edgeIds().contains(edge.edgeId()))
              .toList();
      if (roots.size() != 1) throw failure("PROCESS_ENTRY_TARGET_INVALID");
      StructureNode target = graph.structureNodes().get(roots.get(0).toNodeId());
      if (target == null
          || !"METHOD".equals(target.kind())
          || !METHOD_TARGET.matcher(target.canonicalValue()).matches()) {
        throw failure("PROCESS_ENTRY_TARGET_INVALID");
      }
      values.put(flow.flowSliceId(), target.canonicalValue());
    }
    return Map.copyOf(values);
  }

  private List<ProcessCandidateRelationV2> candidateRelations(
      FlowMaterial material,
      Map<String, String> entryTargets,
      List<SemanticCueSupport> semanticCues) {
    Map<FlowPair, List<PairInput>> bases = new LinkedHashMap<>();
    List<Flow> flows =
        material.flowsById().values().stream()
            .sorted(Comparator.comparing(Flow::flowSliceId, UTF8_ORDER))
            .toList();
    for (Flow caller : flows) {
      for (Signal signal : caller.signals()) {
        if (!("EXPLICIT_CALL".equals(signal.signalKind())
            && "INVOKES".equals(signal.direction())
            && "CALL_TARGET".equals(signal.anchorKind()))) {
          continue;
        }
        for (Flow callee : flows) {
          if (caller.flowSliceId().equals(callee.flowSliceId())
              || !signal.anchorKey().equals(entryTargets.get(callee.flowSliceId()))) {
            continue;
          }
          FlowPair pair = FlowPair.of(caller.flowSliceId(), callee.flowSliceId());
          boolean callerLeft = pair.left().equals(caller.flowSliceId());
          List<Signal> callerCounters =
              blockingSignals(caller, signal.anchorKind(), signal.anchorKey());
          List<Signal> calleeCounters =
              blockingSignals(callee, signal.anchorKind(), signal.anchorKey());
          bases
              .computeIfAbsent(pair, ignored -> new ArrayList<>())
              .add(
                  PairInput.direct(
                      signal,
                      callerLeft ? "LEFT_TO_RIGHT" : "RIGHT_TO_LEFT",
                      callerLeft ? List.of(signal.processJoinSignalId()) : List.of(),
                      callerLeft ? List.of() : List.of(signal.processJoinSignalId()),
                      callerLeft ? callerCounters : calleeCounters,
                      callerLeft ? calleeCounters : callerCounters));
        }
      }
    }
    for (SemanticCueSupport cue : semanticCues) {
      FlowPair pair = FlowPair.of(cue.cue().leftFlowSliceId(), cue.cue().rightFlowSliceId());
      bases.computeIfAbsent(pair, ignored -> new ArrayList<>()).add(PairInput.cue(cue));
    }
    List<ProcessCandidateRelationV2> result = new ArrayList<>();
    for (Map.Entry<FlowPair, List<PairInput>> entry : bases.entrySet()) {
      result.add(relation(entry.getKey(), entry.getValue()));
    }
    return result.stream()
        .sorted(Comparator.comparing(ProcessCandidateRelationV2::candidateRelationId, UTF8_ORDER))
        .toList();
  }

  private List<Signal> blockingSignals(Flow flow, String anchorKind, String anchorKey) {
    return flow.signals().stream()
        .filter(signal -> "BLOCKS".equals(signal.direction()))
        .filter(
            signal ->
                "COUNTER_CONDITION".equals(signal.signalKind())
                    || "CONFLICT_STATE".equals(signal.signalKind())
                    || "EXTERNAL_EFFECT_GAP".equals(signal.signalKind()))
        .filter(signal -> anchorKind.equals(signal.anchorKind()))
        .filter(signal -> anchorKey.equals(signal.anchorKey()))
        .sorted(Comparator.comparing(Signal::processJoinSignalId, UTF8_ORDER))
        .toList();
  }

  private List<SemanticCueSupport> semanticCues(
      FlowMaterial flows,
      FactMaterial facts,
      GraphMaterial graphs,
      List<RegistryItem> registryItems,
      ArtifactReference processCueProfileRef) {
    List<RegistryItem> terms =
        registryItems.stream()
            .filter(item -> "BUSINESS_TERM".equals(item.proposalKind()))
            .filter(item -> validCueBasis(item, flows, facts))
            .sorted(Comparator.comparing(RegistryItem::registryItemId, UTF8_ORDER))
            .toList();
    List<SemanticCueSupport> values = new ArrayList<>();
    for (int leftIndex = 0; leftIndex < terms.size(); leftIndex++) {
      RegistryItem left = terms.get(leftIndex);
      for (int rightIndex = leftIndex + 1; rightIndex < terms.size(); rightIndex++) {
        RegistryItem right = terms.get(rightIndex);
        if (left.flowSliceId().equals(right.flowSliceId())
            || !left.normalizedLabel().equals(right.normalizedLabel())) {
          continue;
        }
        RegistryItem first =
            UTF8_ORDER.compare(left.flowSliceId(), right.flowSliceId()) < 0 ? left : right;
        RegistryItem second = first == left ? right : left;
        ProcessSemanticCueV1 cue = semanticCue(first, second, processCueProfileRef);
        values.add(new SemanticCueSupport(cue, cueEvidence(first, second, facts, graphs)));
      }
    }
    return values.stream()
        .sorted(Comparator.comparing(value -> value.cue().processSemanticCueId(), UTF8_ORDER))
        .toList();
  }

  private boolean validCueBasis(RegistryItem item, FlowMaterial flows, FactMaterial facts) {
    Flow flow = flows.flowsById().get(item.flowSliceId());
    if (flow == null
        || !item.evidenceCapsuleId().equals(flow.capsule().evidenceCapsuleId())
        || item.basisAtomIds().isEmpty()
        || !flow.capsule().registryProposalBasisAtomIds().containsAll(item.basisAtomIds())) {
      return false;
    }
    for (String atomId : item.basisAtomIds()) {
      AtomOwnership atom = facts.atomsById().get(atomId);
      if (atom == null
          || !flow.capsule().factIds().contains(atom.factId())
          || facts.proofsById().get(atom.proofId()) == null) {
        return false;
      }
    }
    return true;
  }

  private ProcessSemanticCueV1 semanticCue(
      RegistryItem left, RegistryItem right, ArtifactReference processCueProfileRef) {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("cueKind", "REGISTRY_BUSINESS_TERM");
    identity.put("leftFlowSliceId", left.flowSliceId());
    identity.put("rightFlowSliceId", right.flowSliceId());
    identity.put("leftRegistryItemId", left.registryItemId());
    identity.put("rightRegistryItemId", right.registryItemId());
    identity.put("normalizedCueKey", left.normalizedLabel());
    strings(identity.putArray("leftBasisAtomIds"), left.basisAtomIds());
    strings(identity.putArray("rightBasisAtomIds"), right.basisAtomIds());
    identity.put("processCueProfileArtifactId", processCueProfileRef.artifactId().value());
    identity.put("processCueProfileSha256", processCueProfileRef.sha256().value());
    return new ProcessSemanticCueV1(
        contentId("process-semantic-cue", identity),
        "REGISTRY_BUSINESS_TERM",
        left.flowSliceId(),
        right.flowSliceId(),
        left.registryItemId(),
        right.registryItemId(),
        left.provisionalKey(),
        right.provisionalKey(),
        left.normalizedLabel(),
        left.basisAtomIds(),
        right.basisAtomIds(),
        null,
        null,
        List.of(),
        List.of(),
        processCueProfileRef,
        true);
  }

  private CueEvidence cueEvidence(
      RegistryItem left, RegistryItem right, FactMaterial facts, GraphMaterial graphs) {
    List<String> atoms = new ArrayList<>();
    atoms.addAll(left.basisAtomIds());
    atoms.addAll(right.basisAtomIds());
    List<String> factIds = new ArrayList<>();
    List<String> proofIds = new ArrayList<>();
    List<String> evidenceIds = new ArrayList<>();
    List<SourceLocatorV1> locators = new ArrayList<>();
    for (String atomId : sortedDistinct(atoms)) {
      AtomOwnership atom = facts.atomsById().get(atomId);
      ClosedProof proof = facts.proofsById().get(atom.proofId());
      factIds.add(atom.factId());
      proofIds.add(proof.proofId());
      evidenceIds.addAll(proof.requiredEvidenceNodeIds());
      for (String evidenceId : proof.requiredEvidenceNodeIds()) {
        SourceLocatorV1 locator = graphs.evidenceNodes().get(evidenceId).sourceLocator();
        if (locator != null) locators.add(locator);
      }
    }
    if (locators.isEmpty()) throw failure("PROCESS_PROOF_CLOSURE_INVALID");
    return new CueEvidence(
        sortedDistinct(factIds),
        sortedDistinct(proofIds),
        sortedDistinct(evidenceIds),
        locators.stream()
            .distinct()
            .sorted(
                Comparator.comparing(SourceLocatorV1::path)
                    .thenComparingLong(SourceLocatorV1::startByte))
            .toList());
  }

  private ProcessCandidateRelationV2 relation(FlowPair pair, List<PairInput> inputs) {
    List<ProcessRelationPositivePairBasisV1> positiveBases = new ArrayList<>();
    List<String> supportSignals = new ArrayList<>();
    List<String> cueIds = new ArrayList<>();
    List<String> factIds = new ArrayList<>();
    List<String> proofIds = new ArrayList<>();
    List<String> evidenceIds = new ArrayList<>();
    List<SourceLocatorV1> locators = new ArrayList<>();
    List<String> gapIds = new ArrayList<>();
    List<String> counterSignalIds = new ArrayList<>();
    List<ProcessRelationCounterBasisV1> counterBases = new ArrayList<>();
    boolean hasProven = false;
    Set<String> directions = new HashSet<>();
    for (PairInput input : inputs) {
      positiveBases.add(input.basis());
      if (input.signal() != null) supportSignals.add(input.signal().processJoinSignalId());
      if ("PROVEN_HANDOFF".equals(input.basis().signalLevel())) {
        hasProven = true;
        directions.add(input.direction());
      }
      factIds.addAll(input.factIds());
      proofIds.addAll(input.proofIds());
      evidenceIds.addAll(input.evidenceNodeIds());
      locators.addAll(input.sourceLocators());
      gapIds.addAll(input.gapIds());
      if (input.cue() != null) cueIds.add(input.cue().processSemanticCueId());
      for (String counterKind : input.counterKinds()) {
        List<Signal> leftCounters = input.leftCounters(counterKind);
        List<Signal> rightCounters = input.rightCounters(counterKind);
        counterBases.add(
            new ProcessRelationCounterBasisV1(
                counterKind,
                input.basis(),
                leftCounters.stream().map(Signal::processJoinSignalId).toList(),
                rightCounters.stream().map(Signal::processJoinSignalId).toList()));
        appendCounterClosure(leftCounters, factIds, proofIds, evidenceIds, locators, gapIds);
        appendCounterClosure(rightCounters, factIds, proofIds, evidenceIds, locators, gapIds);
        counterSignalIds.addAll(leftCounters.stream().map(Signal::processJoinSignalId).toList());
        counterSignalIds.addAll(rightCounters.stream().map(Signal::processJoinSignalId).toList());
      }
    }
    List<String> orderedSupport = sortedDistinct(supportSignals);
    List<String> orderedCues = sortedDistinct(cueIds);
    List<String> orderedCounters = sortedDistinct(counterSignalIds);
    String direction =
        hasProven && directions.size() == 1 ? directions.iterator().next() : "UNDIRECTED";
    String strongest = hasProven ? "PROVEN_HANDOFF" : "SEMANTIC_CUE";
    String use = hasProven && orderedCounters.isEmpty() ? "PROCESS_CANDIDATE" : "PENDING_ONLY";
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("leftFlowSliceId", pair.left());
    identity.put("rightFlowSliceId", pair.right());
    identity.put("strongestSignalLevel", strongest);
    identity.put("direction", direction);
    strings(identity.putArray("supportingProcessJoinSignalIds"), orderedSupport);
    strings(identity.putArray("processSemanticCueIds"), orderedCues);
    strings(identity.putArray("counterProcessJoinSignalIds"), orderedCounters);
    String id = contentId("process-relation", identity);
    return new ProcessCandidateRelationV2(
        id,
        pair.left(),
        pair.right(),
        strongest,
        direction,
        use,
        positiveBases.stream()
            .sorted(Comparator.comparing(value -> value.anchorKey(), UTF8_ORDER))
            .toList(),
        counterBases.stream()
            .sorted(
                Comparator.comparing(ProcessRelationCounterBasisV1::counterKind, UTF8_ORDER)
                    .thenComparing(
                        value -> String.join("\u0000", value.leftCounterProcessJoinSignalIds()),
                        UTF8_ORDER)
                    .thenComparing(
                        value -> String.join("\u0000", value.rightCounterProcessJoinSignalIds()),
                        UTF8_ORDER))
            .toList(),
        orderedSupport,
        orderedCues,
        orderedCounters,
        orderedCounters,
        sortedDistinct(factIds),
        sortedDistinct(proofIds),
        sortedDistinct(evidenceIds),
        locators.stream()
            .distinct()
            .sorted(
                Comparator.comparing(SourceLocatorV1::path)
                    .thenComparingLong(SourceLocatorV1::startByte))
            .toList(),
        sortedDistinct(gapIds));
  }

  private static void appendCounterClosure(
      List<Signal> signals,
      List<String> factIds,
      List<String> proofIds,
      List<String> evidenceIds,
      List<SourceLocatorV1> locators,
      List<String> gapIds) {
    for (Signal signal : signals) {
      factIds.addAll(signal.factIds());
      proofIds.addAll(signal.proofIds());
      evidenceIds.addAll(signal.evidenceNodeIds());
      locators.addAll(signal.sourceLocators());
      gapIds.addAll(signal.gapIds());
    }
  }

  private List<ProcessEvidenceGroupV2> groups(
      FlowMaterial material,
      List<RegistryItem> registryItems,
      List<ProcessCandidateRelationV2> relations,
      List<SemanticCueSupport> semanticCues,
      ProcessMaterialLimitsV1 limits) {
    Map<String, Set<String>> neighbours = new HashMap<>();
    material.flowsById().keySet().forEach(flowId -> neighbours.put(flowId, new LinkedHashSet<>()));
    for (ProcessCandidateRelationV2 relation : relations) {
      neighbours.get(relation.leftFlowSliceId()).add(relation.rightFlowSliceId());
      neighbours.get(relation.rightFlowSliceId()).add(relation.leftFlowSliceId());
    }
    Set<String> visited = new HashSet<>();
    List<ProcessEvidenceGroupV2> values = new ArrayList<>();
    for (String root : sorted(material.flowsById().keySet())) {
      if (!visited.add(root)) continue;
      ArrayDeque<String> pending = new ArrayDeque<>();
      pending.add(root);
      List<String> members = new ArrayList<>();
      while (!pending.isEmpty()) {
        String next = pending.removeFirst();
        members.add(next);
        for (String neighbour : sorted(neighbours.get(next))) {
          if (visited.add(neighbour)) pending.addLast(neighbour);
        }
      }
      members = sorted(members);
      Set<String> memberSet = Set.copyOf(members);
      List<ProcessCandidateRelationV2> groupRelations =
          relations.stream()
              .filter(
                  value ->
                      memberSet.contains(value.leftFlowSliceId())
                          && memberSet.contains(value.rightFlowSliceId()))
              .sorted(
                  Comparator.comparing(ProcessCandidateRelationV2::candidateRelationId, UTF8_ORDER))
              .toList();
      values.add(group(material, registryItems, members, groupRelations, semanticCues, limits));
    }
    List<ProcessEvidenceGroupV2> ordered =
        values.stream()
            .sorted(
                Comparator.comparing(ProcessEvidenceGroupV2::processEvidenceGroupId, UTF8_ORDER))
            .toList();
    if (ordered.size() != visited.size() && relations.isEmpty()) {
      throw failure("PROCESS_GROUP_COVERAGE_BROKEN");
    }
    return ordered;
  }

  private ProcessEvidenceGroupV2 group(
      FlowMaterial material,
      List<RegistryItem> registryItems,
      List<String> members,
      List<ProcessCandidateRelationV2> relations,
      List<SemanticCueSupport> semanticCues,
      ProcessMaterialLimitsV1 limits) {
    Set<String> memberSet = Set.copyOf(members);
    List<ProcessSemanticCueV1> cues = new ArrayList<>();
    List<String> support = new ArrayList<>();
    List<String> counters = new ArrayList<>();
    for (ProcessCandidateRelationV2 relation : relations) {
      support.addAll(relation.supportingProcessJoinSignalIds());
      counters.addAll(relation.counterProcessJoinSignalIds());
    }
    List<RegistryItem> groupItems =
        registryItems.stream()
            .filter(item -> memberSet.contains(item.flowSliceId()))
            .sorted(Comparator.comparing(RegistryItem::registryItemId, UTF8_ORDER))
            .toList();
    Set<String> relationCueIds = new HashSet<>();
    relations.forEach(relation -> relationCueIds.addAll(relation.processSemanticCueIds()));
    cues.addAll(
        semanticCues.stream()
            .map(SemanticCueSupport::cue)
            .filter(cue -> relationCueIds.contains(cue.processSemanticCueId()))
            .sorted(Comparator.comparing(ProcessSemanticCueV1::processSemanticCueId, UTF8_ORDER))
            .toList());
    List<String> ineligibility = new ArrayList<>();
    boolean modelSafe = true;
    List<ProcessPersistedFlowViewV1> views = new ArrayList<>();
    for (String flowId : members) {
      Flow flow = material.flowsById().get(flowId);
      Capsule capsule = flow.capsule();
      if (!"ELIGIBLE".equals(capsule.modelEligibility())) {
        modelSafe = false;
        ineligibility.addAll(capsule.modelIneligibilityGapIds());
      }
      views.add(
          new ProcessPersistedFlowViewV1(
              flowId,
              capsule.evidenceCapsuleId(),
              flow.entryId(),
              capsule.factIds(),
              capsule.gapIds(),
              capsule.outcomePathIds(),
              capsule.signals().stream().map(Signal::processJoinSignalId).toList(),
              capsule.modelEvidenceSpanIds(),
              capsule.projectionObligationIds()));
    }
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    strings(identity.putArray("memberFlowSliceIds"), members);
    strings(
        identity.putArray("candidateRelationIds"),
        relations.stream().map(ProcessCandidateRelationV2::candidateRelationId).toList());
    String groupId = contentId("process-evidence-group", identity);
    List<ProcessRegistryItemViewV1> itemViews =
        groupItems.stream()
            .map(
                item ->
                    new ProcessRegistryItemViewV1(
                        item.registryItemId(),
                        item.flowSliceId(),
                        item.provisionalKey(),
                        item.proposalKind(),
                        item.normalizedLabel(),
                        item.basisAtomIds(),
                        item.basisGapIds()))
            .toList();
    return new ProcessEvidenceGroupV2(
        groupId,
        relations.isEmpty() ? "SINGLETON" : "CONNECTED_COMPONENT",
        members,
        relations,
        cues,
        sortedDistinct(support),
        sortedDistinct(counters),
        groupItems.stream().map(RegistryItem::registryItemId).toList(),
        modelSafe ? "MODEL_SAFE" : "MODEL_INELIGIBLE",
        sortedDistinct(ineligibility),
        new ProcessPersistedMaterialV1(views, relations, itemViews, limits));
  }

  private String compilationId(
      CrossFlowCandidateCompilationRequest request,
      List<String> flowIds,
      List<ProcessCandidateRelationV2> relations,
      List<ProcessEvidenceGroupV2> groups) {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put(
        "programGraphsRoot",
        request.programGraphs().publication().analysisStepArtifactRoot().value());
    identity.put(
        "provenCodeFactsRoot",
        request.provenCodeFacts().publication().analysisStepArtifactRoot().value());
    identity.put(
        "businessFlowsRoot",
        request.businessFlows().publication().analysisStepArtifactRoot().value());
    identity.put(
        "registryRoot",
        request.repositoryInterpretationRegistryPublication().moduleArtifactRoot().value());
    identity.put(
        "analysisRunRequestArtifactId", request.analysisRunRequestRef().artifactId().value());
    identity.put("analysisRunRequestSha256", request.analysisRunRequestRef().sha256().value());
    strings(identity.putArray("flowSliceIds"), flowIds);
    strings(
        identity.putArray("candidateRelationIds"),
        relations.stream().map(ProcessCandidateRelationV2::candidateRelationId).toList());
    strings(
        identity.putArray("processEvidenceGroupIds"),
        groups.stream().map(ProcessEvidenceGroupV2::processEvidenceGroupId).toList());
    return contentId("cross-flow-candidate-compilation", identity);
  }

  private JsonNode payload(ReopenedAnalysisStepPublication publication, String fileName) {
    return canonicalJson.parseCanonical(payloadBytes(publication, fileName));
  }

  private ImmutableBytes payloadBytes(
      ReopenedAnalysisStepPublication publication, String fileName) {
    List<VerifiedCanonicalPayload> values =
        publication.semanticPayloads().stream()
            .filter(payload -> fileName.equals(payload.descriptor().fileName()))
            .toList();
    if (values.size() != 1) throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    return values.get(0).canonicalUtf8();
  }

  private void requireEnvelope(JsonNode value, String type, String schema) {
    if (!type.equals(text(value, "artifactType")) || !schema.equals(text(value, "schemaVersion"))) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    identifier(value, "artifactId");
  }

  private void requireLineEnvelope(JsonNode value, String type, String schema) {
    if (!type.equals(text(value, "artifactType")) || !schema.equals(text(value, "schemaVersion"))) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
  }

  private List<JsonNode> jsonLines(ImmutableBytes bytes) {
    String text = new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
    if (text.isEmpty()) return List.of();
    if (!text.endsWith("\n")) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    List<JsonNode> values = new ArrayList<>();
    for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
      if (line.isEmpty()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      values.add(
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))));
    }
    return List.copyOf(values);
  }

  private List<Signal> signals(JsonNode owner, String flowId) {
    List<Signal> values = new ArrayList<>();
    for (JsonNode signal : array(owner, "processJoinSignals")) {
      if (!flowId.equals(identifier(signal, "flowSliceId"))) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      values.add(
          new Signal(
              identifier(signal, "processJoinSignalId"),
              text(signal, "signalKind"),
              text(signal, "anchorKind"),
              text(signal, "anchorKey"),
              text(signal, "direction"),
              identifiers(signal, "factIds"),
              identifiers(signal, "atomIds"),
              identifiers(signal, "proofIds"),
              identifiers(signal, "evidenceNodeIds"),
              locators(signal),
              identifiers(signal, "gapIds")));
    }
    return values.stream()
        .sorted(Comparator.comparing(Signal::processJoinSignalId, UTF8_ORDER))
        .toList();
  }

  private List<SourceLocatorV1> locators(JsonNode signal) {
    List<SourceLocatorV1> values = new ArrayList<>();
    for (JsonNode locator : array(signal, "sourceLocators")) {
      values.add(locator(locator));
    }
    return values.stream()
        .sorted(
            Comparator.comparing(SourceLocatorV1::path)
                .thenComparingLong(SourceLocatorV1::startByte))
        .toList();
  }

  private static SourceLocatorV1 locator(JsonNode value) {
    return new SourceLocatorV1(
        ArtifactId.parse(identifier(value, "fileId")),
        text(value, "path"),
        longValue(value, "startByte"),
        longValue(value, "endByteExclusive"),
        intValue(value, "startLine"),
        intValue(value, "startColumn"),
        intValue(value, "endLine"),
        intValue(value, "endColumn"));
  }

  private static List<String> identifiersFromObjects(JsonNode owner, String field, String idField) {
    return array(owner, field).stream()
        .map(value -> identifier(value, idField))
        .sorted(UTF8_ORDER)
        .toList();
  }

  private static List<JsonNode> array(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (!(value instanceof ArrayNode array)) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    List<JsonNode> result = new ArrayList<>();
    array.forEach(result::add);
    return List.copyOf(result);
  }

  private static List<String> identifiers(JsonNode source, String field) {
    List<String> values =
        array(source, field).stream()
            .map(
                value -> {
                  if (!value.isTextual()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
                  return ArtifactId.parse(value.textValue()).value();
                })
            .toList();
    return sortedDistinct(values);
  }

  private static String identifier(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isTextual()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return ArtifactId.parse(value.textValue()).value();
  }

  private static String nullableIdentifier(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || value.isNull()) return null;
    if (!value.isTextual()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return ArtifactId.parse(value.textValue()).value();
  }

  private static String text(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return value.textValue();
  }

  private static String nullableText(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || value.isNull()) return null;
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return value.textValue();
  }

  private static boolean booleanValue(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isBoolean()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return value.booleanValue();
  }

  private static long longValue(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.canConvertToLong())
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return value.longValue();
  }

  private static int intValue(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.canConvertToInt()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return value.intValue();
  }

  private static List<String> sorted(Set<String> values) {
    return values.stream().sorted(UTF8_ORDER).toList();
  }

  private static List<String> sorted(List<String> values) {
    return values.stream().sorted(UTF8_ORDER).toList();
  }

  private static List<String> sortedDistinct(List<String> values) {
    List<String> result = values.stream().sorted(UTF8_ORDER).toList();
    if (result.size() != result.stream().distinct().count()) {
      return result.stream().distinct().toList();
    }
    return result;
  }

  private String contentId(String prefix, JsonNode value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(frame(prefix));
      digest.update(frame(canonicalJson.encodeCanonical(value).copyToByteArray()));
      return prefix + ":" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
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

  private static int compareUtf8(String left, String right) {
    byte[] first = left.getBytes(StandardCharsets.UTF_8);
    byte[] second = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(first.length, second.length);
  }

  private static CrossFlowCandidateCompilationException failure(String code) {
    return new CrossFlowCandidateCompilationException(code);
  }

  private static CrossFlowCandidateCompilationException failure(String code, Throwable cause) {
    return new CrossFlowCandidateCompilationException(code, cause);
  }

  private record StructureNode(String nodeId, String kind, String canonicalValue) {}

  private record ControlNode(String nodeId, String kind, List<String> owningEntryIds) {}

  private record ControlEdge(
      String edgeId,
      String kind,
      String fromNodeId,
      String toNodeId,
      String ruleId,
      String resolution,
      String guardNodeId,
      String polarity) {}

  private record Traversal(List<String> nodeIds, List<String> edgeIds) {}

  private record GraphMaterial(
      Map<String, StructureNode> structureNodes,
      Map<String, ControlNode> controlNodes,
      Map<String, ControlEdge> controlEdges,
      Map<String, Traversal> traversals,
      Map<String, EvidenceNode> evidenceNodes) {}

  private record EvidenceNode(String evidenceNodeId, SourceLocatorV1 sourceLocator) {}

  private record AtomOwnership(String atomId, String factId, String proofId) {}

  private record ClosedProof(
      String proofId, String factId, String atomId, List<String> requiredEvidenceNodeIds) {}

  private record FactMaterial(
      Set<String> factIds,
      Map<String, AtomOwnership> atomsById,
      Map<String, ClosedProof> proofsById) {}

  private record ProcessInputControls(
      ArtifactReference processCueProfileRef,
      ArtifactReference processModelRuntimeRef,
      ProcessMaterialLimitsV1 limits) {}

  private record CueEvidence(
      List<String> factIds,
      List<String> proofIds,
      List<String> evidenceNodeIds,
      List<SourceLocatorV1> sourceLocators) {}

  private record SemanticCueSupport(ProcessSemanticCueV1 cue, CueEvidence evidence) {}

  private record Signal(
      String processJoinSignalId,
      String signalKind,
      String anchorKind,
      String anchorKey,
      String direction,
      List<String> factIds,
      List<String> atomIds,
      List<String> proofIds,
      List<String> evidenceNodeIds,
      List<SourceLocatorV1> sourceLocators,
      List<String> gapIds) {}

  private record Capsule(
      String evidenceCapsuleId,
      String modelEligibility,
      List<String> modelIneligibilityGapIds,
      List<String> registryProposalBasisAtomIds,
      List<String> registryProposalBasisGapIds,
      List<String> modelEvidenceSpanIds,
      List<String> projectionObligationIds,
      List<Signal> signals,
      List<String> factIds,
      List<String> gapIds,
      List<String> outcomePathIds,
      List<CapsuleSpan> spans,
      List<CapsuleObligation> obligations) {}

  private record CapsuleSpan(
      String spanId,
      SourceLocatorV1 sourceLocator,
      List<String> supportedAtomIds,
      List<String> supportedProcessJoinSignalIds) {}

  private record CapsuleObligation(
      String obligationId, String kind, String semanticItemId, List<String> satisfyingSpanIds) {}

  private record Flow(
      String flowSliceId,
      String entryId,
      String rootNodeId,
      List<Signal> signals,
      Capsule capsule) {}

  private record FlowMaterial(Map<String, Flow> flowsById) {}

  private record RegistryItem(
      String registryItemId,
      String flowSliceId,
      String evidenceCapsuleId,
      String proposalKind,
      String normalizedLabel,
      List<String> basisAtomIds,
      List<String> basisGapIds) {
    private String provisionalKey() {
      return registryItemId;
    }
  }

  private record FlowPair(String left, String right) {
    private static FlowPair of(String first, String second) {
      if (first.equals(second)) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      return UTF8_ORDER.compare(first, second) < 0
          ? new FlowPair(first, second)
          : new FlowPair(second, first);
    }
  }

  private record PairInput(
      Signal signal,
      ProcessSemanticCueV1 cue,
      ProcessRelationPositivePairBasisV1 basis,
      String direction,
      List<String> factIds,
      List<String> proofIds,
      List<String> evidenceNodeIds,
      List<SourceLocatorV1> sourceLocators,
      List<String> gapIds,
      List<Signal> leftCounterSignals,
      List<Signal> rightCounterSignals) {
    private static PairInput direct(
        Signal signal,
        String direction,
        List<String> leftSignals,
        List<String> rightSignals,
        List<Signal> leftCounterSignals,
        List<Signal> rightCounterSignals) {
      return new PairInput(
          signal,
          null,
          new ProcessRelationPositivePairBasisV1(
              "EXPLICIT_CALL_TO_ENTRY",
              "PROVEN_HANDOFF",
              leftSignals,
              rightSignals,
              List.of(),
              "CALL_TARGET",
              signal.anchorKey(),
              direction),
          direction,
          signal.factIds(),
          signal.proofIds(),
          signal.evidenceNodeIds(),
          signal.sourceLocators(),
          signal.gapIds(),
          leftCounterSignals,
          rightCounterSignals);
    }

    private static PairInput cue(SemanticCueSupport support) {
      ProcessSemanticCueV1 cue = support.cue();
      return new PairInput(
          null,
          cue,
          new ProcessRelationPositivePairBasisV1(
              "SEMANTIC_CUE",
              "SEMANTIC_CUE",
              List.of(),
              List.of(),
              List.of(cue.processSemanticCueId()),
              "REGISTRY_TERM",
              cue.normalizedCueKey(),
              "UNDIRECTED"),
          "UNDIRECTED",
          support.evidence().factIds(),
          support.evidence().proofIds(),
          support.evidence().evidenceNodeIds(),
          support.evidence().sourceLocators(),
          List.of(),
          List.of(),
          List.of());
    }

    private List<String> counterKinds() {
      return java.util.stream.Stream.concat(
              leftCounterSignals.stream(), rightCounterSignals.stream())
          .map(Signal::signalKind)
          .distinct()
          .sorted(UTF8_ORDER)
          .toList();
    }

    private List<Signal> leftCounters(String counterKind) {
      return leftCounterSignals.stream()
          .filter(signal -> counterKind.equals(signal.signalKind()))
          .toList();
    }

    private List<Signal> rightCounters(String counterKind) {
      return rightCounterSignals.stream()
          .filter(signal -> counterKind.equals(signal.signalKind()))
          .toList();
    }
  }
}
