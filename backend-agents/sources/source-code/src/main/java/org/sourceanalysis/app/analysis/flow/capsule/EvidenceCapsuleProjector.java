package org.sourceanalysis.app.analysis.flow.capsule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Deterministically projects each persisted Flow into the exact frozen source material a model may
 * read. It reopens predecessors only; it never re-parses a repository or infers a source span.
 */
public final class EvidenceCapsuleProjector {

  private static final Comparator<String> UTF8_ORDER =
      Comparator.comparing(
          value -> value.getBytes(StandardCharsets.UTF_8), EvidenceCapsuleProjector::compare);
  private static final String M1_TYPE = "BUSINESS_FLOWS_FLOW_COMPILATION";
  private static final String M1_SCHEMA = "business-flows-flow-compilation-v6";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Comparator<SourceLocatorV1> SOURCE_LOCATOR_ORDER =
      Comparator.comparing(SourceLocatorV1::path)
          .thenComparingLong(SourceLocatorV1::startByte)
          .thenComparingLong(SourceLocatorV1::endByteExclusive);

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the M2 projector with its two canonical stores and verified-source reader. */
  public EvidenceCapsuleProjector(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps,
      VerifiedSourceTextReader sourceReader) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
  }

  /**
   * Projects a complete persisted M1 compilation without using the compiler's in-memory result.
   * Every selected source span is a direct requirement of a closed atom proof or a terminal path.
   */
  public CapsuleProjection project(
      ModulePublicationReference flowCompilation,
      VerifiedSourceInventoryReference source,
      ProgramGraphsReference graphs,
      ProvenCodeFactsReference facts,
      CapsuleProjectionProfile profile) {
    try {
      Objects.requireNonNull(flowCompilation, "flow compilation");
      Objects.requireNonNull(source, "source inventory");
      Objects.requireNonNull(graphs, "program graphs");
      Objects.requireNonNull(facts, "proven code facts");
      Objects.requireNonNull(profile, "projection profile");

      ReopenedModulePublication compilationPublication = moduleArtifacts.reopen(flowCompilation);
      ArtifactReference compilationPayload =
          requireCompilation(flowCompilation, compilationPublication);
      ReopenedAnalysisStepPublication sourceStep =
          reopen(source, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
      ReopenedAnalysisStepPublication graphStep = reopen(graphs, AnalysisStepKey.PROGRAM_GRAPHS);
      ReopenedAnalysisStepPublication factStep = reopen(facts, AnalysisStepKey.PROVEN_CODE_FACTS);
      requireSharedRunAndControls(sourceStep, graphStep, factStep, compilationPublication);

      JavaCodeIndex codeIndex = reopenCodeIndex(graphs, graphStep);
      ArtifactReference codeIndexReference = codeIndexReference(graphStep);

      VerifiedSourceTextSet texts = sourceReader.reopen(source);
      requireSourceTextSet(texts, sourceStep);
      if (isNavigationOnly(graphStep, factStep)) {
        return projectNavigationContexts(
            compilationPublication,
            compilationPayload,
            texts,
            profile,
            codeIndex,
            codeIndexReference);
      }
      Inputs inputs =
          inputs(compilationPublication, graphStep, factStep, texts, codeIndex, codeIndexReference);
      if (inputs.flows().size() > profile.maxCapsules()) {
        throw failure("BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED");
      }

      Map<String, SpanBuilder> selectedSpans = new LinkedHashMap<>();
      List<CapsuleProjection.EvidenceCapsule> capsules = new ArrayList<>();
      List<CapsuleProjection.ProjectionObligation> obligations = new ArrayList<>();
      for (PersistedFlow flow : inputs.flows()) {
        ProjectedFlow projected = projectFlow(flow, inputs, profile, selectedSpans, obligations);
        capsules.add(projected.capsule());
      }
      List<CapsuleProjection.ModelEvidenceSpan> spans =
          selectedSpans.values().stream()
              .map(SpanBuilder::materialize)
              .sorted(Comparator.comparing(CapsuleProjection.ModelEvidenceSpan::spanId, UTF8_ORDER))
              .toList();
      requireProjectionClosure(capsules, obligations, spans);
      return new CapsuleProjection(
          profile, compilationPayload, inputs.proofPackRef(), capsules, spans, obligations);
    } catch (CapsuleProjectionException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
  }

  private CapsuleProjection projectNavigationContexts(
      ReopenedModulePublication compilation,
      ArtifactReference compilationPayload,
      VerifiedSourceTextSet texts,
      CapsuleProjectionProfile profile,
      JavaCodeIndex codeIndex,
      ArtifactReference codeIndexReference) {
    JsonNode envelope = canonicalJson.parseCanonical(compilation.payloads().get(0).canonicalUtf8());
    requireHeader(envelope, M1_TYPE, M1_SCHEMA);
    List<FlowCompilation.EntryContext> contexts =
        parseEntryContexts(object(envelope, "payload"), codeIndex, codeIndexReference)
            .values()
            .stream()
            .sorted(Comparator.comparing(FlowCompilation.EntryContext::entryId, UTF8_ORDER))
            .toList();
    long collected =
        contexts.stream().filter(value -> "COLLECTED".equals(value.collectionStatus())).count();
    if (collected > profile.maxCapsules()) {
      throw failure("BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED");
    }
    Map<String, VerifiedSourceTextDocument> documents =
        texts.documents().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    VerifiedSourceTextDocument::path, value -> value));
    List<CapsuleProjection.EvidenceCapsule> capsules = new ArrayList<>();
    for (FlowCompilation.EntryContext context : contexts) {
      if (!"COLLECTED".equals(context.collectionStatus())) continue;
      validateCodeContextSources(context.codeContext(), documents);
      long sourceBytes = codeContextBytes(context.codeContext());
      boolean exceeds = sourceBytes > profile.maxCapsuleUtf8Bytes();
      String gapId =
          exceeds
              ? contentId(
                  "flow-gap",
                  "CAPSULE_BUDGET_NO_SAFE_SPLIT",
                  context.entryId(),
                  profile.profileRef().artifactId().value())
              : null;
      List<CapsuleProjection.FlowGapView> gapViews =
          gapId == null
              ? List.of()
              : List.of(
                  new CapsuleProjection.FlowGapView(
                      gapId,
                      "ENTRY_CONTEXT",
                      "CAPSULE_BUDGET_NO_SAFE_SPLIT",
                      List.of(context.entryId()),
                      List.of(),
                      "CAPSULE_PROJECTION",
                      null));
      capsules.add(
          new CapsuleProjection.EvidenceCapsule(
              contentId(
                  "evidence-capsule",
                  context.entryContextId(),
                  context.codeContext().entryMethodKey()),
              null,
              null,
              exceeds ? "INELIGIBLE" : "ELIGIBLE",
              gapId == null ? List.of() : List.of(gapId),
              new CapsuleProjection.FlowEntryView(
                  context.entryId(),
                  context.trigger(),
                  context.codeContext().entryMethodKey(),
                  List.of()),
              context,
              List.of(),
              gapViews,
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              new CapsuleProjection.BudgetUsage(
                  context.codeContext().methods().size()
                      + context.codeContext().supportingSources().size(),
                  sourceBytes)));
    }
    return new CapsuleProjection(profile, compilationPayload, null, capsules, List.of(), List.of());
  }

  private static boolean isNavigationOnly(
      ReopenedAnalysisStepPublication graphs, ReopenedAnalysisStepPublication facts) {
    return graphs.semanticPayloads().size() == 1
        && "java-code-index.jsonl".equals(graphs.semanticPayloads().get(0).descriptor().fileName())
        && facts.semanticPayloads().size() == 1
        && "fact-accounting.json".equals(facts.semanticPayloads().get(0).descriptor().fileName())
        && "proven-code-facts-fact-accounting-v4"
            .equals(facts.semanticPayloads().get(0).descriptor().schemaVersion());
  }

  private static void validateCodeContextSources(
      EntryCodeContext context, Map<String, VerifiedSourceTextDocument> documents) {
    List<EntryCodeContext.SourceSource> sources = new ArrayList<>();
    context.methods().forEach(value -> sources.add(value.source()));
    context.supportingSources().forEach(value -> sources.add(value.source()));
    for (EntryCodeContext.SourceSource source : sources) {
      VerifiedSourceTextDocument document = documents.get(source.path());
      if (document == null) throw failure("EVIDENCE_SOURCE_REOPEN_MISMATCH");
      String text = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      int start = source.startOffsetUtf16();
      int end = Math.addExact(start, source.lengthUtf16());
      if (end > text.length() || !text.substring(start, end).equals(source.text())) {
        throw failure("EVIDENCE_SOURCE_REOPEN_MISMATCH");
      }
    }
  }

  private static long codeContextBytes(EntryCodeContext context) {
    return java.util.stream.Stream.concat(
            context.methods().stream().map(value -> value.source().text()),
            context.supportingSources().stream().map(value -> value.source().text()))
        .mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length)
        .sum();
  }

  private ProjectedFlow projectFlow(
      PersistedFlow flow,
      Inputs inputs,
      CapsuleProjectionProfile profile,
      Map<String, SpanBuilder> selectedSpans,
      List<CapsuleProjection.ProjectionObligation> obligations) {
    List<PersistedFact> facts = exactFacts(flow, inputs.factsById());
    List<PersistedGap> gaps = exactGaps(flow, inputs.gapsById());
    List<CapsuleProjection.FlowFactView> factViews =
        facts.stream()
            .map(
                fact ->
                    new CapsuleProjection.FlowFactView(
                        fact.factId(),
                        fact.kind(),
                        fact.subjectNodeIds(),
                        fact.atoms().stream()
                            .map(
                                atom ->
                                    new CapsuleProjection.FlowAtomView(
                                        atom.atomId(),
                                        atom.role(),
                                        atom.name(),
                                        atom.valueType(),
                                        atom.canonicalValue(),
                                        atom.proofId()))
                            .toList(),
                        inputs.provenFactsRef()))
            .toList();
    List<CapsuleProjection.FlowGapView> gapViews =
        new ArrayList<>(
            gaps.stream()
                .map(
                    gap ->
                        new CapsuleProjection.FlowGapView(
                            gap.gapId(),
                            "FACT",
                            gap.code(),
                            gap.affectedCandidateDenominatorKeys(),
                            gap.evidenceNodeIds().isEmpty()
                                ? List.of()
                                : List.of(inputs.evidenceGraphRef()),
                            "PROVEN_CODE_FACTS_GAP_LEDGER",
                            inputs.gapLedgerRef()))
                .toList());
    List<CapsuleProjection.FlowOutcomePathView> outcomeViews =
        flow.outcomes().stream().map(EvidenceCapsuleProjector::outcomeView).toList();

    Set<String> flowSpanIds = new HashSet<>();
    List<String> obligationIds = new ArrayList<>();
    for (PersistedFact fact : facts) {
      for (PersistedAtom atom : fact.atoms()) {
        PersistedProof proof = inputs.proofsById().get(atom.proofId());
        if (proof == null || !atom.atomId().equals(proof.atomId())) {
          throw failure("FLOW_FACT_PROOF_REFERENCE_BROKEN");
        }
        List<String> spanIds =
            selectSourceSpans(
                proof.requiredEvidenceNodeIds(),
                atom.atomId(),
                null,
                null,
                flow.flowSliceId(),
                inputs,
                profile,
                selectedSpans,
                flowSpanIds);
        String obligationId =
            contentId("projection-obligation", "ATOM_DIRECT_SEMANTICS", atom.atomId());
        obligations.add(
            new CapsuleProjection.ProjectionObligation(
                obligationId, "ATOM_DIRECT_SEMANTICS", atom.atomId(), spanIds));
        obligationIds.add(obligationId);
      }
    }
    for (PersistedOutcome outcome : flow.outcomes()) {
      PersistedControlNode terminal = inputs.controlNodesById().get(outcome.terminalNodeId());
      if (terminal == null || terminal.evidenceNodeIds().isEmpty()) {
        throw failure("FLOW_OUTCOME_CLOSURE_BROKEN");
      }
      List<String> spanIds =
          selectSourceSpans(
              terminal.evidenceNodeIds(),
              null,
              outcome.outcomePathId(),
              null,
              flow.flowSliceId(),
              inputs,
              profile,
              selectedSpans,
              flowSpanIds);
      String obligationId =
          contentId("projection-obligation", "OUTCOME_TERMINAL", outcome.outcomePathId());
      obligations.add(
          new CapsuleProjection.ProjectionObligation(
              obligationId, "OUTCOME_TERMINAL", outcome.outcomePathId(), spanIds));
      obligationIds.add(obligationId);
    }
    List<FlowCompilation.ProcessJoinSignalV1> processJoinSignals = flow.processJoinSignals();
    for (FlowCompilation.ProcessJoinSignalV1 signal : processJoinSignals) {
      List<String> spanIds =
          selectProcessJoinSignalSpans(
              signal, flow.flowSliceId(), inputs, profile, selectedSpans, flowSpanIds);
      String obligationId =
          contentId(
              "projection-obligation", "PROCESS_JOIN_SIGNAL_BASIS", signal.processJoinSignalId());
      obligations.add(
          new CapsuleProjection.ProjectionObligation(
              obligationId, "PROCESS_JOIN_SIGNAL_BASIS", signal.processJoinSignalId(), spanIds));
      obligationIds.add(obligationId);
    }
    PersistedControlNode root = inputs.controlNodesById().get(flow.rootNodeId());
    if (root == null || root.evidenceNodeIds().isEmpty()) {
      throw failure("FLOW_GRAPH_REFERENCE_BROKEN");
    }
    List<String> orderedSpanIds = flowSpanIds.stream().sorted(UTF8_ORDER).toList();
    long totalBytes =
        orderedSpanIds.stream()
            .map(selectedSpans::get)
            .mapToLong(value -> value.sourceExcerpt().rawUtf8().size())
            .sum();
    boolean exceedsBudget =
        orderedSpanIds.size() > profile.maxSpansPerCapsule()
            || totalBytes > profile.maxCapsuleUtf8Bytes()
            || orderedSpanIds.stream()
                .map(selectedSpans::get)
                .anyMatch(span -> span.sourceExcerpt().rawUtf8().size() > profile.maxSpanBytes());
    List<String> modelIneligibilityGapIds = List.of();
    String modelEligibility = "ELIGIBLE";
    if (exceedsBudget) {
      String gapId =
          contentId(
              "flow-gap",
              "CAPSULE_BUDGET_NO_SAFE_SPLIT",
              flow.flowSliceId(),
              profile.profileRef().artifactId().value(),
              profile.profileRef().sha256().value());
      modelIneligibilityGapIds = List.of(gapId);
      modelEligibility = "INELIGIBLE";
      gapViews.add(
          new CapsuleProjection.FlowGapView(
              gapId,
              "FLOW",
              "CAPSULE_BUDGET_NO_SAFE_SPLIT",
              List.of(flow.flowSliceId()),
              List.of(),
              "CAPSULE_PROJECTION",
              null));
    }
    List<String> processJoinSignalIds =
        processJoinSignals.stream()
            .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
            .sorted(UTF8_ORDER)
            .toList();
    return new ProjectedFlow(
        new CapsuleProjection.EvidenceCapsule(
            contentId(
                "evidence-capsule",
                flow.flowSliceId(),
                inputs.proofPackRef().artifactId().value(),
                inputs.proofPackRef().sha256().value(),
                String.join("\u0000", processJoinSignalIds),
                String.join("\u0000", orderedSpanIds),
                String.join("\u0000", obligationIds.stream().sorted(UTF8_ORDER).toList())),
            flow.flowSliceId(),
            inputs.proofPackRef().artifactId().value(),
            modelEligibility,
            modelIneligibilityGapIds,
            new CapsuleProjection.FlowEntryView(
                flow.entryId(), flow.trigger(), flow.rootNodeId(), root.evidenceNodeIds()),
            flow.entryContext(),
            factViews,
            gapViews,
            outcomeViews,
            processJoinSignals,
            orderedSpanIds,
            obligationIds.stream().sorted(UTF8_ORDER).toList(),
            new CapsuleProjection.BudgetUsage(orderedSpanIds.size(), totalBytes)));
  }

  private static List<PersistedFact> exactFacts(
      PersistedFlow flow, Map<String, PersistedFact> factsById) {
    List<PersistedFact> values = new ArrayList<>();
    for (String factId : flow.factIds()) {
      PersistedFact fact = factsById.get(factId);
      if (fact == null || !flow.entryId().equals(fact.entryId())) {
        throw failure("FLOW_FACT_PROOF_REFERENCE_BROKEN");
      }
      values.add(fact);
    }
    List<String> atoms =
        values.stream()
            .flatMap(value -> value.atoms().stream())
            .map(PersistedAtom::atomId)
            .sorted(UTF8_ORDER)
            .toList();
    if (!atoms.equals(flow.atomIds())) throw failure("FLOW_FACT_PROOF_REFERENCE_BROKEN");
    return List.copyOf(values);
  }

  private static List<PersistedGap> exactGaps(
      PersistedFlow flow, Map<String, PersistedGap> gapsById) {
    List<PersistedGap> values = new ArrayList<>();
    for (String gapId : flow.gapIds()) {
      PersistedGap gap = gapsById.get(gapId);
      if (gap == null || !gap.affectedEntryIds().equals(List.of(flow.entryId()))) {
        throw failure("FLOW_FACT_PROOF_REFERENCE_BROKEN");
      }
      values.add(gap);
    }
    return List.copyOf(values);
  }

  private static CapsuleProjection.FlowOutcomePathView outcomeView(PersistedOutcome outcome) {
    return new CapsuleProjection.FlowOutcomePathView(
        outcome.outcomePathId(),
        outcome.decisions().stream()
            .map(
                value ->
                    new CapsuleProjection.BranchDecisionView(
                        value.guardNodeId(),
                        value.conditionAtomId(),
                        value.polarity(),
                        value.normalizedCondition()))
            .toList(),
        outcome.terminalNodeId(),
        outcome.terminalKind(),
        outcome.terminalFactIds(),
        outcome.requiredAtomIds(),
        outcome.requiredProofIds());
  }

  private static List<String> selectSourceSpans(
      List<String> evidenceNodeIds,
      String atomId,
      String outcomePathId,
      String processJoinSignalId,
      String flowSliceId,
      Inputs inputs,
      CapsuleProjectionProfile profile,
      Map<String, SpanBuilder> selectedSpans,
      Set<String> flowSpanIds) {
    List<String> spanIds = new ArrayList<>();
    for (String evidenceNodeId : evidenceNodeIds) {
      PersistedEvidence evidence = inputs.evidenceById().get(evidenceNodeId);
      if (evidence == null) throw failure("EVIDENCE_PROJECTION_UNSATISFIABLE");
      // Rule-application nodes explain how a Proof was derived. A capsule must expose only the
      // corresponding frozen source excerpts to a model, not an implementation-rule token.
      if (evidence.sourceExcerpt() == null) continue;
      String spanId = flowRootedSpanId(flowSliceId, evidenceNodeId);
      SpanBuilder builder =
          selectedSpans.computeIfAbsent(
              spanId,
              ignored ->
                  new SpanBuilder(spanId, flowSliceId, evidenceNodeId, evidence.sourceExcerpt()));
      builder.requireOwningFlow(flowSliceId);
      if (atomId != null) builder.supportedAtomIds.add(atomId);
      if (outcomePathId != null) builder.supportedOutcomePathIds.add(outcomePathId);
      if (processJoinSignalId != null)
        builder.supportedProcessJoinSignalIds.add(processJoinSignalId);
      spanIds.add(spanId);
      flowSpanIds.add(spanId);
    }
    List<String> ordered = spanIds.stream().sorted(UTF8_ORDER).distinct().toList();
    if (ordered.isEmpty()) throw failure("EVIDENCE_PROJECTION_UNSATISFIABLE");
    return ordered;
  }

  private static List<String> selectProcessJoinSignalSpans(
      FlowCompilation.ProcessJoinSignalV1 signal,
      String flowSliceId,
      Inputs inputs,
      CapsuleProjectionProfile profile,
      Map<String, SpanBuilder> selectedSpans,
      Set<String> flowSpanIds) {
    if (!flowSliceId.equals(signal.flowSliceId())) {
      throw failure("PROCESS_JOIN_SIGNAL_FLOW_MISMATCH");
    }
    selectSourceSpans(
        signal.evidenceNodeIds(),
        null,
        null,
        signal.processJoinSignalId(),
        flowSliceId,
        inputs,
        profile,
        selectedSpans,
        flowSpanIds);
    List<String> matchingSpanIds =
        flowSpanIds.stream()
            .filter(
                spanId -> {
                  SpanBuilder span = selectedSpans.get(spanId);
                  return span != null
                      && signal.sourceLocators().contains(span.sourceExcerpt().locator());
                })
            .sorted(UTF8_ORDER)
            .toList();
    if (matchingSpanIds.isEmpty()) throw failure("EVIDENCE_PROJECTION_UNSATISFIABLE");
    for (String spanId : matchingSpanIds) {
      selectedSpans.get(spanId).supportedProcessJoinSignalIds.add(signal.processJoinSignalId());
    }
    return matchingSpanIds;
  }

  private Inputs inputs(
      ReopenedModulePublication compilation,
      ReopenedAnalysisStepPublication graphs,
      ReopenedAnalysisStepPublication facts,
      VerifiedSourceTextSet texts,
      JavaCodeIndex codeIndex,
      ArtifactReference codeIndexReference) {
    VerifiedCanonicalPayload m1Payload = compilation.payloads().get(0);
    List<PersistedFlow> flows =
        parseFlows(
            canonicalJson.parseCanonical(m1Payload.canonicalUtf8()), codeIndex, codeIndexReference);
    Map<String, VerifiedCanonicalPayload> graphPayloads =
        payloads(
            graphs,
            Map.of(
                "control-flow-graph.json",
                new PayloadSpec(
                    "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH",
                    "program-graphs-control-flow-graph-v2",
                    CanonicalMediaType.APPLICATION_JSON),
                "evidence-graph.json",
                new PayloadSpec(
                    "PROGRAM_GRAPHS_EVIDENCE_GRAPH",
                    "program-graphs-evidence-graph-v3",
                    CanonicalMediaType.APPLICATION_JSON)));
    Map<String, VerifiedCanonicalPayload> factPayloads =
        payloads(
            facts,
            Map.of(
                "proven-facts.json",
                new PayloadSpec(
                    "PROVEN_CODE_FACTS_PROVEN_FACTS",
                    "proven-code-facts-proven-facts-v3",
                    CanonicalMediaType.APPLICATION_JSON),
                "proof-pack.json",
                new PayloadSpec(
                    "PROVEN_CODE_FACTS_PROOF_PACK",
                    "proven-code-facts-proof-pack-v3",
                    CanonicalMediaType.APPLICATION_JSON),
                "gap-ledger.json",
                new PayloadSpec(
                    "PROVEN_CODE_FACTS_GAP_LEDGER",
                    "proven-code-facts-gap-ledger-v3",
                    CanonicalMediaType.APPLICATION_JSON)));
    Map<String, PersistedControlNode> controlNodes =
        parseControlNodes(
            canonicalJson.parseCanonical(
                graphPayloads.get("control-flow-graph.json").canonicalUtf8()));
    Map<String, PersistedEvidence> evidence =
        parseEvidence(
            canonicalJson.parseCanonical(graphPayloads.get("evidence-graph.json").canonicalUtf8()),
            texts);
    Map<String, PersistedFact> codeFacts =
        parseFacts(
            canonicalJson.parseCanonical(factPayloads.get("proven-facts.json").canonicalUtf8()));
    Map<String, PersistedProof> proofs =
        parseProofs(
            canonicalJson.parseCanonical(factPayloads.get("proof-pack.json").canonicalUtf8()),
            evidence);
    Map<String, PersistedGap> gaps =
        parseGaps(
            canonicalJson.parseCanonical(factPayloads.get("gap-ledger.json").canonicalUtf8()),
            evidence);
    ArtifactReference proofPack = reference(factPayloads.get("proof-pack.json"));
    ArtifactReference provenFacts = reference(factPayloads.get("proven-facts.json"));
    ArtifactReference gapLedger = reference(factPayloads.get("gap-ledger.json"));
    ArtifactReference evidenceGraph = reference(graphPayloads.get("evidence-graph.json"));
    requireFlowReferences(flows, controlNodes, evidence, codeFacts, proofs, gaps);
    return new Inputs(
        flows,
        controlNodes,
        evidence,
        codeFacts,
        proofs,
        gaps,
        proofPack,
        provenFacts,
        gapLedger,
        evidenceGraph);
  }

  private static ArtifactReference requireCompilation(
      ModulePublicationReference reference, ReopenedModulePublication reopened) {
    if (!reference.equals(reopened.reference())
        || reopened.payloads().size() != 1
        || reopened.receipt().payloadArtifacts().size() != 1
        || !(reopened.receipt().address()
            instanceof org.sourceanalysis.app.artifact.AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
        || address.moduleNumber() != 1
        || !"flow-compiler".equals(address.moduleKey())) {
      throw failure("UPSTREAM_ARTIFACT_REPLAY_MISMATCH");
    }
    VerifiedCanonicalPayload payload = reopened.payloads().get(0);
    if (!"flow-compilation.json".equals(payload.descriptor().fileName())
        || !M1_TYPE.equals(payload.descriptor().artifactType())
        || !M1_SCHEMA.equals(payload.descriptor().schemaVersion())
        || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_JSON
        || !payload.descriptor().equals(reopened.receipt().payloadArtifacts().get(0))) {
      throw failure("UPSTREAM_ARTIFACT_REPLAY_MISMATCH");
    }
    return reference(payload);
  }

  private ReopenedAnalysisStepPublication reopen(
      Object typedReference, AnalysisStepKey expectedStep) {
    org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reference;
    if (typedReference instanceof VerifiedSourceInventoryReference value) {
      reference = value.publication();
    } else if (typedReference instanceof ProgramGraphsReference value) {
      reference = value.publication();
    } else if (typedReference instanceof ProvenCodeFactsReference value) {
      reference = value.publication();
    } else {
      throw failure("BUSINESS_FLOWS_REQUEST_INVALID");
    }
    if (reference.address().analysisStepKey() != expectedStep) {
      throw failure("UPSTREAM_ARTIFACT_REPLAY_MISMATCH");
    }
    ReopenedAnalysisStepPublication reopened = analysisSteps.reopen(reference);
    if (!reference.equals(reopened.reference())
        || reopened.receipt().address().analysisStepKey() != expectedStep
        || !reference
            .analysisStepArtifactRoot()
            .equals(reopened.receipt().analysisStepArtifactRoot())
        || !reference.analysisStepReceiptId().equals(reopened.receipt().analysisStepReceiptId())) {
      throw failure("UPSTREAM_ARTIFACT_REPLAY_MISMATCH");
    }
    return reopened;
  }

  private JavaCodeIndex reopenCodeIndex(
      ProgramGraphsReference graphs, ReopenedAnalysisStepPublication graphStep) {
    ArtifactReference reference = codeIndexReference(graphStep);
    return reference == null ? null : new JavaCodeIndexReader(analysisSteps).reopen(graphs);
  }

  private static ArtifactReference codeIndexReference(ReopenedAnalysisStepPublication graphStep) {
    List<VerifiedCanonicalPayload> indexes =
        graphStep.semanticPayloads().stream()
            .filter(value -> "java-code-index.jsonl".equals(value.descriptor().fileName()))
            .toList();
    if (indexes.isEmpty()) return null;
    if (indexes.size() != 1
        || !"PROGRAM_GRAPHS_JAVA_CODE_INDEX".equals(indexes.get(0).descriptor().artifactType())
        || !"java-code-index-v2".equals(indexes.get(0).descriptor().schemaVersion())
        || indexes.get(0).descriptor().mediaType() != CanonicalMediaType.APPLICATION_X_NDJSON) {
      throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
    }
    return reference(indexes.get(0));
  }

  private static void requireSharedRunAndControls(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication graphs,
      ReopenedAnalysisStepPublication facts,
      ReopenedModulePublication compilation) {
    if (!source.reference().address().runId().equals(graphs.reference().address().runId())
        || !source.reference().address().runId().equals(facts.reference().address().runId())
        || !source.receipt().controls().equals(graphs.receipt().controls())
        || !source.receipt().controls().equals(facts.receipt().controls())
        || !graphs.receipt().upstreamAnalysisStepReferences().contains(source.reference())
        || !facts.receipt().upstreamAnalysisStepReferences().contains(source.reference())
        || !facts.receipt().upstreamAnalysisStepReferences().contains(graphs.reference())
        || !compilation.receipt().controls().equals(source.receipt().controls())) {
      throw failure("UPSTREAM_ARTIFACT_REPLAY_MISMATCH");
    }
  }

  private static void requireSourceTextSet(
      VerifiedSourceTextSet texts, ReopenedAnalysisStepPublication source) {
    if (texts == null
        || !texts.controls().equals(source.receipt().controls())
        || texts.documents().isEmpty()
        || texts.documents().stream().map(VerifiedSourceTextDocument::fileId).distinct().count()
            != texts.documents().size()) {
      throw failure("EVIDENCE_SOURCE_REOPEN_MISMATCH");
    }
  }

  private Map<String, VerifiedCanonicalPayload> payloads(
      ReopenedAnalysisStepPublication publication, Map<String, PayloadSpec> required) {
    Map<String, VerifiedCanonicalPayload> values = new HashMap<>();
    for (VerifiedCanonicalPayload payload : publication.semanticPayloads()) {
      PayloadSpec expectation = required.get(payload.descriptor().fileName());
      if (expectation != null) {
        ArtifactDescriptor descriptor = payload.descriptor();
        if (!expectation.artifactType().equals(descriptor.artifactType())
            || !expectation.schemaVersion().equals(descriptor.schemaVersion())
            || expectation.mediaType() != descriptor.mediaType()
            || values.put(descriptor.fileName(), payload) != null) {
          throw failure("UPSTREAM_ARTIFACT_REPLAY_MISMATCH");
        }
      }
    }
    if (!values.keySet().equals(required.keySet()))
      throw failure("UPSTREAM_ARTIFACT_REPLAY_MISMATCH");
    return Map.copyOf(values);
  }

  private List<PersistedFlow> parseFlows(
      JsonNode envelope, JavaCodeIndex codeIndex, ArtifactReference codeIndexReference) {
    requireHeader(envelope, M1_TYPE, M1_SCHEMA);
    JsonNode payload = object(envelope, "payload");
    Map<String, FlowCompilation.EntryContext> contexts =
        parseEntryContexts(payload, codeIndex, codeIndexReference);
    List<PersistedFlow> flows = new ArrayList<>();
    for (JsonNode value : array(payload, "flowSlices")) {
      List<PersistedOutcome> outcomes = new ArrayList<>();
      for (JsonNode outcome : array(value, "outcomePaths")) outcomes.add(parseOutcome(outcome));
      List<FlowCompilation.ProcessJoinSignalV1> processJoinSignals = new ArrayList<>();
      for (JsonNode signal : array(value, "processJoinSignals")) {
        processJoinSignals.add(parseProcessJoinSignal(signal));
      }
      String entryId = id(value, "entryId");
      String flowSliceId = id(value, "flowSliceId");
      FlowCompilation.EntryContext entryContext = contexts.get(entryId);
      if (entryContext == null || !flowSliceId.equals(entryContext.flowSliceId())) {
        throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
      }
      flows.add(
          new PersistedFlow(
              flowSliceId,
              entryId,
              text(value, "trigger"),
              id(value, "rootNodeId"),
              ids(value, "factIds"),
              ids(value, "atomIds"),
              ordered(outcomes, PersistedOutcome::outcomePathId),
              ids(value, "gapIds"),
              entryContext,
              ordered(
                  processJoinSignals, FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)));
    }
    List<PersistedFlow> ordered = ordered(flows, PersistedFlow::flowSliceId);
    return ordered;
  }

  private static Map<String, FlowCompilation.EntryContext> parseEntryContexts(
      JsonNode payload, JavaCodeIndex codeIndex, ArtifactReference codeIndexReference) {
    Map<String, FlowCompilation.EntryContext> values = new HashMap<>();
    for (JsonNode value : array(payload, "entryContexts")) {
      FlowCompilation.EntryContext context;
      try {
        context = hydrateEntryContext(value, codeIndex, codeIndexReference);
      } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
        throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
      }
      if (values.put(context.entryId(), context) != null) {
        throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
      }
    }
    return Map.copyOf(values);
  }

  /** Resolves the sole persisted Step05 indirection without rerunning a Java code engine. */
  private static FlowCompilation.EntryContext hydrateEntryContext(
      JsonNode value, JavaCodeIndex codeIndex, ArtifactReference codeIndexReference)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    if (!(value instanceof com.fasterxml.jackson.databind.node.ObjectNode stored)
        || stored.has("codeContext")) {
      throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
    }
    String entryId = id(stored, "entryId");
    String collectionStatus = text(stored, "collectionStatus");
    JsonNode reference = stored.get("codeContextRef");
    EntryCodeContext codeContext = null;
    if ("COLLECTED".equals(collectionStatus)) {
      if (codeIndex == null
          || codeIndexReference == null
          || reference == null
          || !reference.isObject()
          || !entryId.equals(id(reference, "entryId"))
          || !codeIndexReference.equals(readReference(reference, "indexArtifact"))) {
        throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
      }
      codeContext =
          codeIndex.entries().stream()
              .filter(entry -> entryId.equals(entry.seed().entryId()))
              .map(JavaCodeIndex.EntryCollection::context)
              .filter(Objects::nonNull)
              .findFirst()
              .orElseThrow(() -> failure("FLOW_CONTEXT_REFERENCE_BROKEN"));
    } else if ("NOT_COLLECTED".equals(collectionStatus)) {
      if (reference == null || !reference.isNull()) {
        throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
      }
    } else {
      throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
    }
    com.fasterxml.jackson.databind.node.ObjectNode hydrated = stored.deepCopy();
    hydrated.remove("codeContextRef");
    hydrated.set(
        "codeContext",
        codeContext == null
            ? com.fasterxml.jackson.databind.node.NullNode.instance
            : MAPPER.valueToTree(codeContext));
    return MAPPER.treeToValue(hydrated, FlowCompilation.EntryContext.class);
  }

  private static PersistedOutcome parseOutcome(JsonNode value) {
    List<PersistedDecision> decisions = new ArrayList<>();
    for (JsonNode decision : array(value, "decisions")) {
      decisions.add(
          new PersistedDecision(
              id(decision, "guardNodeId"),
              id(decision, "conditionAtomId"),
              text(decision, "polarity"),
              text(decision, "normalizedCondition")));
    }
    return new PersistedOutcome(
        id(value, "outcomePathId"),
        List.copyOf(decisions),
        id(value, "terminalNodeId"),
        text(value, "terminalKind"),
        ids(value, "terminalFactIds"),
        ids(value, "requiredAtomIds"),
        ids(value, "requiredProofIds"));
  }

  private static FlowCompilation.ProcessJoinSignalV1 parseProcessJoinSignal(JsonNode value) {
    try {
      requireProcessJoinSignalFields(value);
      return new FlowCompilation.ProcessJoinSignalV1(
          id(value, "processJoinSignalId"),
          id(value, "flowSliceId"),
          text(value, "signalKind"),
          text(value, "anchorKind"),
          text(value, "anchorKey"),
          text(value, "direction"),
          text(value, "specificity"),
          text(value, "claimScope"),
          ids(value, "factIds"),
          ids(value, "atomIds"),
          ids(value, "proofIds"),
          ids(value, "evidenceNodeIds"),
          sourceLocators(value, "sourceLocators"),
          ids(value, "gapIds"));
    } catch (CapsuleProjectionException failure) {
      throw failure;
    } catch (RuntimeException malformed) {
      throw failure("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
    }
  }

  private static Map<String, PersistedControlNode> parseControlNodes(JsonNode graph) {
    requireHeader(
        graph, "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH", "program-graphs-control-flow-graph-v2");
    Map<String, PersistedControlNode> values = new HashMap<>();
    for (JsonNode node : array(graph, "nodes")) {
      String nodeId = id(node, "nodeId");
      List<String> evidenceNodeIds = ids(node, "evidenceNodeIds");
      if (values.put(nodeId, new PersistedControlNode(nodeId, text(node, "kind"), evidenceNodeIds))
          != null) {
        throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
      }
    }
    return Map.copyOf(values);
  }

  private static Map<String, PersistedEvidence> parseEvidence(
      JsonNode graph, VerifiedSourceTextSet texts) {
    requireHeader(graph, "PROGRAM_GRAPHS_EVIDENCE_GRAPH", "program-graphs-evidence-graph-v3");
    Map<String, VerifiedSourceTextDocument> documents = new HashMap<>();
    for (VerifiedSourceTextDocument document : texts.documents()) {
      documents.put(document.fileId().value(), document);
    }
    Map<String, PersistedEvidence> values = new HashMap<>();
    for (JsonNode node : array(graph, "nodes")) {
      String evidenceNodeId = id(node, "evidenceNodeId");
      SourceExcerptV1 excerpt = null;
      JsonNode source = node.get("sourceExcerpt");
      if (source != null && !source.isNull()) {
        excerpt = sourceExcerpt(source, documents);
      }
      if (values.put(evidenceNodeId, new PersistedEvidence(evidenceNodeId, excerpt)) != null) {
        throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
      }
    }
    return Map.copyOf(values);
  }

  private static SourceExcerptV1 sourceExcerpt(
      JsonNode value, Map<String, VerifiedSourceTextDocument> documents) {
    JsonNode locatorValue = object(value, "locator");
    SourceLocatorV1 locator =
        new SourceLocatorV1(
            ArtifactId.parse(text(locatorValue, "fileId")),
            text(locatorValue, "path"),
            longValue(locatorValue, "startByte"),
            longValue(locatorValue, "endByteExclusive"),
            integer(locatorValue, "startLine"),
            integer(locatorValue, "startColumn"),
            integer(locatorValue, "endLine"),
            integer(locatorValue, "endColumn"));
    SourceExcerptV1 excerpt =
        new SourceExcerptV1(
            locator,
            ImmutableBytes.copyOf(text(value, "rawUtf8").getBytes(StandardCharsets.UTF_8)),
            new Sha256Digest(text(value, "rawUtf8Sha256")));
    VerifiedSourceTextDocument source = documents.get(locator.fileId().value());
    if (source == null
        || !source.path().equals(locator.path())
        || locator.endByteExclusive() > source.rawUtf8().size()) {
      throw failure("EVIDENCE_SOURCE_REOPEN_MISMATCH");
    }
    byte[] original = source.rawUtf8().copyToByteArray();
    byte[] actual =
        java.util.Arrays.copyOfRange(
            original, (int) locator.startByte(), (int) locator.endByteExclusive());
    if (!java.util.Arrays.equals(actual, excerpt.rawUtf8().copyToByteArray())) {
      throw failure("EVIDENCE_SOURCE_REOPEN_MISMATCH");
    }
    return excerpt;
  }

  private static List<SourceLocatorV1> sourceLocators(JsonNode source, String field) {
    List<SourceLocatorV1> values =
        array(source, field).stream().map(EvidenceCapsuleProjector::sourceLocator).toList();
    List<SourceLocatorV1> ordered = orderedLocators(values);
    if (!values.equals(ordered)) throw failure("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
    return ordered;
  }

  private static SourceLocatorV1 sourceLocator(JsonNode value) {
    requireProcessJoinSignalLocatorFields(value);
    try {
      return new SourceLocatorV1(
          ArtifactId.parse(text(value, "fileId")),
          text(value, "path"),
          longValue(value, "startByte"),
          longValue(value, "endByteExclusive"),
          integer(value, "startLine"),
          integer(value, "startColumn"),
          integer(value, "endLine"),
          integer(value, "endColumn"));
    } catch (RuntimeException malformed) {
      throw failure("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
    }
  }

  private static List<SourceLocatorV1> orderedLocators(List<SourceLocatorV1> values) {
    List<SourceLocatorV1> ordered = values.stream().sorted(SOURCE_LOCATOR_ORDER).toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw failure("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
    }
    return ordered;
  }

  private static Map<String, PersistedFact> parseFacts(JsonNode payload) {
    requireHeader(payload, "PROVEN_CODE_FACTS_PROVEN_FACTS", "proven-code-facts-proven-facts-v3");
    Map<String, PersistedFact> values = new HashMap<>();
    for (JsonNode fact : array(payload, "codeFacts")) {
      List<PersistedAtom> atoms = new ArrayList<>();
      for (JsonNode atom : array(fact, "atoms")) {
        JsonNode atomValue = object(atom, "value");
        atoms.add(
            new PersistedAtom(
                id(atom, "atomId"),
                text(atom, "role"),
                text(atom, "name"),
                text(atomValue, "type"),
                text(atomValue, "canonical"),
                id(atom, "proofId")));
      }
      String candidateKey = text(fact, "candidateDenominatorKey");
      int separator = candidateKey.indexOf('|');
      if (separator < 1) throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
      String entryId = ArtifactId.parse(candidateKey.substring(0, separator)).value();
      PersistedFact value =
          new PersistedFact(
              id(fact, "factId"),
              entryId,
              text(fact, "kind"),
              ids(fact, "subjectNodeIds"),
              ordered(atoms, PersistedAtom::atomId));
      if (values.put(value.factId(), value) != null)
        throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
    return Map.copyOf(values);
  }

  private static Map<String, PersistedProof> parseProofs(
      JsonNode payload, Map<String, PersistedEvidence> evidenceById) {
    requireHeader(payload, "PROVEN_CODE_FACTS_PROOF_PACK", "proven-code-facts-proof-pack-v3");
    Map<String, PersistedProof> values = new HashMap<>();
    for (JsonNode proof : array(payload, "atomProofs")) {
      if (!"CLOSED".equals(text(proof, "status")))
        throw failure("FLOW_FACT_PROOF_REFERENCE_BROKEN");
      List<String> required = ids(proof, "requiredEvidenceNodeIds");
      if (required.isEmpty() || required.stream().anyMatch(id -> !evidenceById.containsKey(id))) {
        throw failure("FLOW_FACT_PROOF_REFERENCE_BROKEN");
      }
      PersistedProof value =
          new PersistedProof(id(proof, "proofId"), id(proof, "atomId"), required);
      if (values.put(value.proofId(), value) != null)
        throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
    return Map.copyOf(values);
  }

  private static Map<String, PersistedGap> parseGaps(
      JsonNode payload, Map<String, PersistedEvidence> evidenceById) {
    requireHeader(payload, "PROVEN_CODE_FACTS_GAP_LEDGER", "proven-code-facts-gap-ledger-v3");
    Map<String, PersistedGap> values = new HashMap<>();
    for (JsonNode gap : array(payload, "gaps")) {
      PersistedGap value =
          new PersistedGap(
              id(gap, "gapId"),
              text(gap, "code"),
              ids(gap, "affectedEntryIds"),
              opaqueTexts(gap, "affectedCandidateDenominatorKeys"),
              ids(gap, "evidenceNodeIds"));
      if (value.evidenceNodeIds().stream().anyMatch(id -> !evidenceById.containsKey(id))) {
        throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
      }
      if (values.put(value.gapId(), value) != null)
        throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
    return Map.copyOf(values);
  }

  private static void requireFlowReferences(
      List<PersistedFlow> flows,
      Map<String, PersistedControlNode> controls,
      Map<String, PersistedEvidence> evidence,
      Map<String, PersistedFact> facts,
      Map<String, PersistedProof> proofs,
      Map<String, PersistedGap> gaps) {
    for (PersistedFlow flow : flows) {
      if (!controls.containsKey(flow.rootNodeId())
          || flow.outcomes().stream()
              .anyMatch(value -> !controls.containsKey(value.terminalNodeId()))
          || flow.factIds().stream().anyMatch(id -> !facts.containsKey(id))
          || flow.gapIds().stream().anyMatch(id -> !gaps.containsKey(id))
          || flow.outcomes().stream()
              .flatMap(value -> value.requiredProofIds().stream())
              .anyMatch(id -> !proofs.containsKey(id))) {
        throw failure("FLOW_GRAPH_REFERENCE_BROKEN");
      }
      requireProcessJoinSignalClosure(flow, evidence, facts, proofs, gaps);
    }
  }

  private static void requireProcessJoinSignalClosure(
      PersistedFlow flow,
      Map<String, PersistedEvidence> evidence,
      Map<String, PersistedFact> facts,
      Map<String, PersistedProof> proofs,
      Map<String, PersistedGap> gaps) {
    Map<String, PersistedFact> ownedFacts = new HashMap<>();
    Map<String, String> factIdsByAtomId = new HashMap<>();
    Map<String, PersistedAtom> atomsById = new HashMap<>();
    for (String factId : flow.factIds()) {
      PersistedFact fact = facts.get(factId);
      if (fact == null
          || !flow.entryId().equals(fact.entryId())
          || ownedFacts.put(factId, fact) != null) {
        throw failure("PROCESS_JOIN_SIGNAL_FLOW_MISMATCH");
      }
      for (PersistedAtom atom : fact.atoms()) {
        if (factIdsByAtomId.put(atom.atomId(), factId) != null
            || atomsById.put(atom.atomId(), atom) != null) {
          throw failure("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
        }
      }
    }
    for (FlowCompilation.ProcessJoinSignalV1 signal : flow.processJoinSignals()) {
      if (!flow.flowSliceId().equals(signal.flowSliceId())
          || !signal.factIds().stream().allMatch(ownedFacts::containsKey)
          || !signal.gapIds().stream().allMatch(flow.gapIds()::contains)) {
        throw failure("PROCESS_JOIN_SIGNAL_FLOW_MISMATCH");
      }
      if (signal.atomIds().stream().anyMatch(atomId -> !atomsById.containsKey(atomId))) {
        throw failure("PROCESS_JOIN_SIGNAL_FLOW_MISMATCH");
      }
      List<String> expectedFactIds =
          signal.atomIds().stream()
              .map(factIdsByAtomId::get)
              .distinct()
              .sorted(UTF8_ORDER)
              .toList();
      if (!signal.factIds().equals(expectedFactIds)) {
        throw failure("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
      }
      List<String> expectedProofIds =
          signal.atomIds().stream()
              .map(atomsById::get)
              .map(PersistedAtom::proofId)
              .distinct()
              .sorted(UTF8_ORDER)
              .toList();
      if (!signal.proofIds().equals(expectedProofIds)) {
        throw failure("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
      }
      Set<String> expectedEvidenceIds = new HashSet<>();
      for (String proofId : signal.proofIds()) {
        PersistedProof proof = proofs.get(proofId);
        if (proof == null
            || !signal.atomIds().contains(proof.atomId())
            || !proof.requiredEvidenceNodeIds().stream().allMatch(evidence::containsKey)) {
          throw failure("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
        }
        expectedEvidenceIds.addAll(proof.requiredEvidenceNodeIds());
      }
      for (String gapId : signal.gapIds()) {
        PersistedGap gap = gaps.get(gapId);
        if (gap == null
            || !gap.affectedEntryIds().equals(List.of(flow.entryId()))
            || !gap.evidenceNodeIds().stream().allMatch(evidence::containsKey)) {
          throw failure("PROCESS_JOIN_SIGNAL_FLOW_MISMATCH");
        }
        expectedEvidenceIds.addAll(gap.evidenceNodeIds());
      }
      List<String> orderedEvidenceIds = expectedEvidenceIds.stream().sorted(UTF8_ORDER).toList();
      if (orderedEvidenceIds.isEmpty() || !signal.evidenceNodeIds().equals(orderedEvidenceIds)) {
        throw failure("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
      }
      List<SourceLocatorV1> expectedLocators =
          orderedLocators(
              orderedEvidenceIds.stream()
                  .map(evidence::get)
                  .map(PersistedEvidence::sourceExcerpt)
                  .filter(Objects::nonNull)
                  .map(SourceExcerptV1::locator)
                  .distinct()
                  .toList());
      if (expectedLocators.isEmpty() || !signal.sourceLocators().equals(expectedLocators)) {
        throw failure("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
      }
    }
  }

  private static void requireProjectionClosure(
      List<CapsuleProjection.EvidenceCapsule> capsules,
      List<CapsuleProjection.ProjectionObligation> obligations,
      List<CapsuleProjection.ModelEvidenceSpan> spans) {
    Set<String> spanIds =
        spans.stream()
            .map(CapsuleProjection.ModelEvidenceSpan::spanId)
            .collect(java.util.stream.Collectors.toSet());
    if (capsules.size()
            != capsules.stream()
                .map(CapsuleProjection.EvidenceCapsule::flowSliceId)
                .distinct()
                .count()
        || obligations.size()
            != obligations.stream()
                .map(CapsuleProjection.ProjectionObligation::obligationId)
                .distinct()
                .count()
        || obligations.stream()
            .flatMap(value -> value.satisfyingSpanIds().stream())
            .anyMatch(id -> !spanIds.contains(id))) {
      throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
  }

  private static ArtifactReference reference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static ArtifactReference readReference(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isObject()) throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
    try {
      return new ArtifactReference(
          ArtifactId.parse(text(value, "artifactId")), new Sha256Digest(text(value, "sha256")));
    } catch (RuntimeException invalid) {
      throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
    }
  }

  private static void requireHeader(JsonNode value, String type, String schema) {
    if (!schema.equals(text(value, "schemaVersion")) || !type.equals(text(value, "artifactType"))) {
      throw failure("UPSTREAM_ARTIFACT_REPLAY_MISMATCH");
    }
  }

  private static void requireProcessJoinSignalFields(JsonNode value) {
    requireExactFields(
        value,
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
            "gapIds"),
        "PROCESS_JOIN_SIGNAL_BASIS_INVALID");
  }

  private static void requireProcessJoinSignalLocatorFields(JsonNode value) {
    requireExactFields(
        value,
        Set.of(
            "fileId",
            "path",
            "startByte",
            "endByteExclusive",
            "startLine",
            "startColumn",
            "endLine",
            "endColumn"),
        "PROCESS_JOIN_SIGNAL_BASIS_INVALID");
  }

  private static void requireExactFields(JsonNode value, Set<String> expected, String code) {
    if (value == null || !value.isObject()) throw failure(code);
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw failure(code);
  }

  private static JsonNode object(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isObject()) throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    return value;
  }

  private static List<JsonNode> array(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isArray()) throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    List<JsonNode> values = new ArrayList<>();
    value.forEach(values::add);
    return List.copyOf(values);
  }

  private static String text(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
    return value.textValue();
  }

  private static boolean booleanValue(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isBoolean()) {
      throw failure("FLOW_CONTEXT_REFERENCE_BROKEN");
    }
    return value.booleanValue();
  }

  private static String id(JsonNode source, String field) {
    try {
      return ArtifactId.parse(text(source, field)).value();
    } catch (RuntimeException invalid) {
      throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
  }

  private static List<String> ids(JsonNode source, String field) {
    List<String> values =
        array(source, field).stream()
            .map(value -> ArtifactId.parse(text(value)))
            .map(ArtifactId::value)
            .toList();
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (!ordered.equals(values) || ordered.size() != new HashSet<>(ordered).size()) {
      throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
    return ordered;
  }

  private static List<String> opaqueTexts(JsonNode source, String field) {
    List<String> values =
        array(source, field).stream().map(EvidenceCapsuleProjector::text).toList();
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (!ordered.equals(values) || ordered.size() != new HashSet<>(ordered).size()) {
      throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
    return ordered;
  }

  /** Preserves argument ordinal and repeated values; this is not a set-shaped reference field. */
  private static List<String> textSequence(JsonNode source, String field) {
    return array(source, field).stream().map(EvidenceCapsuleProjector::text).toList();
  }

  private static String text(JsonNode value) {
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
    return value.textValue();
  }

  private static int integer(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.canConvertToInt())
      throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    return value.intValue();
  }

  private static long longValue(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.canConvertToLong())
      throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    return value.longValue();
  }

  private static <T> List<T> ordered(List<T> values, java.util.function.Function<T, String> key) {
    List<T> ordered = values.stream().sorted(Comparator.comparing(key, UTF8_ORDER)).toList();
    if (ordered.size() != ordered.stream().map(key).distinct().count()) {
      throw failure("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
    }
    return ordered;
  }

  private static String contentId(String prefix, String... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(frame(prefix));
      for (String value : values) digest.update(frame(value));
      return prefix + ":" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static String flowRootedSpanId(String flowSliceId, String evidenceNodeId) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(frame("business-flows-model-evidence-span-id-v2"));
      digest.update(frame(flowSliceId));
      digest.update(frame(evidenceNodeId));
      return "model-evidence-span:" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static int compare(byte[] left, byte[] right) {
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int compared =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (compared != 0) return compared;
    }
    return Integer.compare(left.length, right.length);
  }

  private static CapsuleProjectionException failure(String code) {
    return new CapsuleProjectionException(code);
  }

  private record PayloadSpec(
      String artifactType, String schemaVersion, CanonicalMediaType mediaType) {}

  private record Inputs(
      List<PersistedFlow> flows,
      Map<String, PersistedControlNode> controlNodesById,
      Map<String, PersistedEvidence> evidenceById,
      Map<String, PersistedFact> factsById,
      Map<String, PersistedProof> proofsById,
      Map<String, PersistedGap> gapsById,
      ArtifactReference proofPackRef,
      ArtifactReference provenFactsRef,
      ArtifactReference gapLedgerRef,
      ArtifactReference evidenceGraphRef) {}

  private record PersistedFlow(
      String flowSliceId,
      String entryId,
      String trigger,
      String rootNodeId,
      List<String> factIds,
      List<String> atomIds,
      List<PersistedOutcome> outcomes,
      List<String> gapIds,
      FlowCompilation.EntryContext entryContext,
      List<FlowCompilation.ProcessJoinSignalV1> processJoinSignals) {}

  private record PersistedOutcome(
      String outcomePathId,
      List<PersistedDecision> decisions,
      String terminalNodeId,
      String terminalKind,
      List<String> terminalFactIds,
      List<String> requiredAtomIds,
      List<String> requiredProofIds) {}

  private record PersistedDecision(
      String guardNodeId, String conditionAtomId, String polarity, String normalizedCondition) {}

  private record PersistedControlNode(String nodeId, String kind, List<String> evidenceNodeIds) {}

  private record PersistedEvidence(String evidenceNodeId, SourceExcerptV1 sourceExcerpt) {}

  private record PersistedFact(
      String factId,
      String entryId,
      String kind,
      List<String> subjectNodeIds,
      List<PersistedAtom> atoms) {}

  private record PersistedAtom(
      String atomId,
      String role,
      String name,
      String valueType,
      String canonicalValue,
      String proofId) {}

  private record PersistedProof(
      String proofId, String atomId, List<String> requiredEvidenceNodeIds) {}

  private record PersistedGap(
      String gapId,
      String code,
      List<String> affectedEntryIds,
      List<String> affectedCandidateDenominatorKeys,
      List<String> evidenceNodeIds) {}

  private record ProjectedFlow(CapsuleProjection.EvidenceCapsule capsule) {}

  private static final class SpanBuilder {
    private final String spanId;
    private final String flowSliceId;
    private final String evidenceNodeId;
    private final SourceExcerptV1 sourceExcerpt;
    private final Set<String> supportedAtomIds = new HashSet<>();
    private final Set<String> supportedOutcomePathIds = new HashSet<>();
    private final Set<String> supportedProcessJoinSignalIds = new HashSet<>();

    private SpanBuilder(
        String spanId, String flowSliceId, String evidenceNodeId, SourceExcerptV1 sourceExcerpt) {
      this.spanId = spanId;
      this.flowSliceId = flowSliceId;
      this.evidenceNodeId = evidenceNodeId;
      this.sourceExcerpt = sourceExcerpt;
    }

    private void requireOwningFlow(String candidateFlowSliceId) {
      if (!flowSliceId.equals(candidateFlowSliceId)) {
        throw failure("PROCESS_JOIN_SIGNAL_FLOW_MISMATCH");
      }
    }

    private SourceExcerptV1 sourceExcerpt() {
      return sourceExcerpt;
    }

    private CapsuleProjection.ModelEvidenceSpan materialize() {
      return new CapsuleProjection.ModelEvidenceSpan(
          spanId,
          evidenceNodeId,
          sourceExcerpt,
          supportedAtomIds.stream().sorted(UTF8_ORDER).toList(),
          supportedOutcomePathIds.stream().sorted(UTF8_ORDER).toList(),
          supportedProcessJoinSignalIds.stream().sorted(UTF8_ORDER).toList());
    }
  }
}
