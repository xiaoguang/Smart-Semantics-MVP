package org.sourceanalysis.app.analysis.flow.compiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Compiles one complete, entry-rooted Flow from fresh-reopened program graphs and proven Facts. */
public final class EntryRootedFlowCompiler {

  private static final Comparator<String> UTF8_ORDER =
      Comparator.comparing(
          value -> value.getBytes(StandardCharsets.UTF_8), EntryRootedFlowCompiler::compare);

  private final PersistedFlowCompilationInputReader inputs;

  /** Creates the M1 public seam with the one store used to fresh-reopen its public predecessors. */
  public EntryRootedFlowCompiler(CanonicalAnalysisStepArtifactStore analysisSteps) {
    inputs = new PersistedFlowCompilationInputReader(analysisSteps);
  }

  /**
   * Compiles the entire discovered-entry denominator without reading source files, a worktree, or a
   * predecessor's in-memory graph.
   */
  public FlowCompilation compile(
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference graphs,
      ProvenCodeFactsReference facts,
      FlowCompilationProfile profile) {
    Objects.requireNonNull(discovery, "application discovery");
    Objects.requireNonNull(graphs, "program graphs");
    Objects.requireNonNull(facts, "proven code facts");
    Objects.requireNonNull(profile, "Flow compilation profile");
    PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened =
        inputs.reopen(discovery, graphs, facts);
    if (reopened.entries().size() > profile.maxFlows()) {
      throw new IllegalArgumentException("BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED");
    }

    List<FlowCompilation.EntryDisposition> dispositions = new ArrayList<>();
    List<FlowCompilation.FlowSlice> flows = new ArrayList<>();
    List<FlowCompilation.FlowGap> flowGaps = upstreamFlowGaps(reopened);
    for (PersistedFlowCompilationInputReader.FlowEntry entry : reopened.entries()) {
      try {
        FlowCompilation.FlowSlice flow = compileEntry(entry, reopened, profile);
        flows.add(flow);
        dispositions.add(
            new FlowCompilation.EntryDisposition(
                entry.entryId(), "COMPILED", flow.flowSliceId(), flow.gapIds()));
      } catch (ProcessJoinSignalIntegrityException integrity) {
        throw integrity;
      } catch (IllegalArgumentException uncompiled) {
        String reasonCode = uncompiled.getMessage();
        if (reasonCode == null || reasonCode.isBlank()) {
          reasonCode = "FLOW_COMPILATION_UNSATISFIABLE";
        }
        String gapId =
            contentId(
                "flow-gap",
                reasonCode,
                entry.entryId(),
                profile.profileRef().artifactId().value(),
                profile.profileRef().sha256().value());
        flowGaps.add(
            new FlowCompilation.FlowGap(
                gapId, "ENTRY", reasonCode, List.of(entry.entryId()), List.of()));
        dispositions.add(
            new FlowCompilation.EntryDisposition(
                entry.entryId(), "GAP", null, List.of(gapId), reasonCode));
      }
    }
    return new FlowCompilation(profile, dispositions, flows, flowGaps);
  }

  private static List<FlowCompilation.FlowGap> upstreamFlowGaps(
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    return new ArrayList<>(
        reopened.gapsByEntry().values().stream()
            .flatMap(List::stream)
            .map(
                gap ->
                    new FlowCompilation.FlowGap(
                        gap.gapId(),
                        "FLOW",
                        gap.code(),
                        gap.affectedEntryIds(),
                        gap.evidenceNodeIds()))
            .sorted(Comparator.comparing(FlowCompilation.FlowGap::gapId, UTF8_ORDER))
            .toList());
  }

  private static FlowCompilation.FlowSlice compileEntry(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened,
      FlowCompilationProfile profile) {
    List<PersistedFlowCompilationInputReader.ControlNode> roots =
        reopened.controlNodesById().values().stream()
            .filter(
                value ->
                    "ENTRY".equals(value.kind()) && value.owners().equals(List.of(entry.entryId())))
            .toList();
    if (roots.size() != 1) throw broken("FLOW_GRAPH_REFERENCE_BROKEN");
    List<PersistedFlowCompilationInputReader.PersistedFact> facts =
        reopened.factsByEntry().getOrDefault(entry.entryId(), List.of());
    List<String> factIds =
        facts.stream()
            .map(PersistedFlowCompilationInputReader.PersistedFact::factId)
            .sorted(UTF8_ORDER)
            .toList();
    List<String> atomIds =
        facts.stream()
            .flatMap(value -> value.atoms().stream())
            .map(PersistedFlowCompilationInputReader.PersistedAtom::atomId)
            .sorted(UTF8_ORDER)
            .toList();
    List<String> proofIds =
        facts.stream()
            .flatMap(value -> value.atoms().stream())
            .map(PersistedFlowCompilationInputReader.PersistedAtom::proofId)
            .sorted(UTF8_ORDER)
            .toList();
    List<String> gapIds =
        reopened.gapsByEntry().getOrDefault(entry.entryId(), List.of()).stream()
            .map(PersistedFlowCompilationInputReader.PersistedGap::gapId)
            .sorted(UTF8_ORDER)
            .toList();
    List<TraversalPath> paths = traverse(entry, roots.get(0), facts, reopened, profile);
    List<FlowCompilation.OutcomePath> outcomes =
        paths.stream()
            .map(
                path ->
                    new FlowCompilation.OutcomePath(
                        contentId(
                            "outcome",
                            entry.entryId(),
                            String.join("\u0000", path.nodeIds()),
                            decisionsIdentity(path.decisions()),
                            path.terminalNodeId(),
                            path.terminalKind(),
                            String.join("\u0000", factIds),
                            String.join("\u0000", atomIds),
                            String.join("\u0000", proofIds)),
                        path.decisions(),
                        path.terminalNodeId(),
                        path.terminalKind(),
                        factIds,
                        atomIds,
                        proofIds))
            .toList();
    String rootNodeId = roots.get(0).nodeId();
    String flowSliceId =
        contentId(
            "flow",
            entry.entryId(),
            entry.trigger(),
            rootNodeId,
            profile.profileRef().artifactId().value(),
            profile.profileRef().sha256().value(),
            String.join("\u0000", factIds),
            String.join("\u0000", atomIds),
            String.join("\u0000", gapIds),
            String.join(
                "\u0000",
                outcomes.stream().map(FlowCompilation.OutcomePath::outcomePathId).toList()));
    List<FlowCompilation.ProcessJoinSignalV1> processJoinSignals =
        compileProcessJoinSignals(entry, facts, flowSliceId, paths, reopened, profile);
    return new FlowCompilation.FlowSlice(
        flowSliceId,
        entry.entryId(),
        entry.trigger(),
        rootNodeId,
        longestCommonPrefix(paths.stream().map(TraversalPath::nodeIds).toList()),
        factIds,
        atomIds,
        outcomes,
        gapIds,
        processJoinSignals);
  }

  private static List<FlowCompilation.ProcessJoinSignalV1> compileProcessJoinSignals(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      String flowSliceId,
      List<TraversalPath> paths,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened,
      FlowCompilationProfile profile) {
    List<FlowCompilation.ProcessJoinSignalV1> signals = new ArrayList<>();
    Map<CallTuple, ExactCall> exactCalls = exactCalls(entry, facts, flowSliceId, reopened);
    signals.addAll(exactCalls.values().stream().map(ExactCall::signal).toList());
    Set<String> boundaryInvocationIds = new java.util.HashSet<>();
    for (PersistedFlowCompilationInputReader.PersistedFact fact : facts) {
      if (!"JAVA_BOUNDARY_INVOCATION".equals(fact.kind())) continue;
      if (!boundaryInvocationIds.add(boundaryFor(entry, fact, reopened).invocationCallId())) {
        throw signalInvalid();
      }
    }
    for (ExactCall exactCall : exactCalls.values()) {
      if (!boundaryInvocationIds.contains(exactCall.tuple().invocationCallId())) {
        signals.addAll(
            counterConditionsForExactCall(entry, facts, exactCall, flowSliceId, paths, reopened));
      }
    }
    Map<String, ExactCall> exactCallsByInvocation = new HashMap<>();
    for (ExactCall exactCall : exactCalls.values()) {
      if (exactCallsByInvocation.put(exactCall.tuple().invocationCallId(), exactCall) != null) {
        throw signalInvalid();
      }
    }
    for (PersistedFlowCompilationInputReader.PersistedFact fact : facts) {
      if (!"JAVA_BOUNDARY_INVOCATION".equals(fact.kind())) continue;
      PersistedFlowCompilationInputReader.BoundaryInvocation boundary =
          boundaryFor(entry, fact, reopened);
      Map<String, PersistedFlowCompilationInputReader.PersistedAtom> atoms =
          validatedBoundaryAtoms(fact, boundary);
      SignalBasis typeBasis =
          closedBasis(fact, List.of(atoms.get("STATIC_TARGET_TYPE")), List.of(), reopened);
      signals.add(
          signal(
              flowSliceId,
              "JAVA_TYPE_ANCHOR",
              "JAVA_TYPE",
              boundary.staticTargetType(),
              "REFERENCES",
              "GENERIC_TECHNICAL",
              "STATIC_STRUCTURE",
              typeBasis,
              List.of()));

      List<PersistedFlowCompilationInputReader.PersistedAtom> callAtoms =
          List.of(
              atoms.get("INVOCATION_CALL_ID"),
              atoms.get("STATIC_TARGET_TYPE"),
              atoms.get("STATIC_TARGET_METHOD"),
              atoms.get("STATIC_TARGET_SIGNATURE"));
      SignalBasis callBasis = closedBasis(fact, callAtoms, List.of(), reopened);
      String callKey = boundary.staticTargetType() + "#" + boundary.staticTargetSignature();
      CallTuple boundaryTuple =
          new CallTuple(entry.entryId(), boundary.invocationCallId(), callKey);
      ExactCall exactByInvocation = exactCallsByInvocation.get(boundary.invocationCallId());
      if (exactByInvocation != null && !boundaryTuple.equals(exactByInvocation.tuple())) {
        throw signalInvalid();
      }
      if (!exactCalls.containsKey(boundaryTuple)) {
        signals.add(
            signal(
                flowSliceId,
                "EXPLICIT_CALL",
                "CALL_TARGET",
                callKey,
                "INVOKES",
                "GENERIC_TECHNICAL",
                "FROZEN_JAVA",
                callBasis,
                List.of()));
      }

      PersistedFlowCompilationInputReader.PersistedGap externalEffectGap =
          externalEffectGap(entry, fact, boundary, reopened);
      SignalBasis externalEffectBasis =
          closedBasis(fact, callAtoms, externalEffectGap.evidenceNodeIds(), reopened);
      signals.add(
          signal(
              flowSliceId,
              "EXTERNAL_EFFECT_GAP",
              "CALL_TARGET",
              callKey,
              "BLOCKS",
              "GENERIC_TECHNICAL",
              "GAP_ONLY",
              externalEffectBasis,
              List.of(externalEffectGap.gapId())));

      FlowCompilation.ProcessJoinSignalV1 counter =
          counterCondition(
              entry,
              facts,
              fact,
              boundary,
              atoms,
              callAtoms,
              callKey,
              flowSliceId,
              paths,
              reopened);
      if (counter != null) signals.add(counter);
    }
    List<FlowCompilation.ProcessJoinSignalV1> ordered =
        signals.stream()
            .sorted(
                Comparator.comparing(
                    FlowCompilation.ProcessJoinSignalV1::processJoinSignalId, UTF8_ORDER))
            .toList();
    if (ordered.size()
        != ordered.stream()
            .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
            .distinct()
            .count()) {
      throw signalInvalid();
    }
    if (ordered.size() > profile.maxProcessJoinSignalsPerFlow()
        || ordered.stream()
            .anyMatch(
                signal -> basisReferenceCount(signal) > profile.maxProcessJoinSignalBasisRefs())) {
      throw broken("BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED");
    }
    validateSignalFlowClosure(flowSliceId, facts, ordered, reopened);
    return ordered;
  }

  private static Map<CallTuple, ExactCall> exactCalls(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      String flowSliceId,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    Map<CallTuple, ExactCall> values = new HashMap<>();
    for (PersistedFlowCompilationInputReader.PersistedFact fact : facts) {
      if (!"JAVA_EXACT_CALL".equals(fact.kind())) continue;
      if (!entry.entryId().equals(fact.entryId())) throw signalFlowMismatch();
      Map<String, PersistedFlowCompilationInputReader.PersistedAtom> atoms =
          validatedExactCallAtoms(fact);
      List<PersistedFlowCompilationInputReader.PersistedAtom> callAtoms =
          List.of(
              atoms.get("INVOCATION_CALL_ID"),
              atoms.get("STATIC_TARGET_TYPE"),
              atoms.get("STATIC_TARGET_METHOD"),
              atoms.get("STATIC_TARGET_SIGNATURE"));
      String callKey =
          atoms.get("STATIC_TARGET_TYPE").canonicalValue()
              + "#"
              + atoms.get("STATIC_TARGET_SIGNATURE").canonicalValue();
      CallTuple tuple =
          new CallTuple(entry.entryId(), atoms.get("INVOCATION_CALL_ID").canonicalValue(), callKey);
      ExactCall exactCall =
          new ExactCall(
              tuple,
              signal(
                  flowSliceId,
                  "EXPLICIT_CALL",
                  "CALL_TARGET",
                  callKey,
                  "INVOKES",
                  "GENERIC_TECHNICAL",
                  "FROZEN_JAVA",
                  closedBasis(fact, callAtoms, List.of(), reopened),
                  List.of()),
              fact,
              callAtoms);
      if (values.put(tuple, exactCall) != null) throw signalInvalid();
    }
    return Map.copyOf(values);
  }

  private static List<FlowCompilation.ProcessJoinSignalV1> counterConditionsForExactCall(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      ExactCall exactCall,
      String flowSliceId,
      List<TraversalPath> paths,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    Map<String, List<TraversalPath>> pathsByGuard = new HashMap<>();
    for (TraversalPath path : paths) {
      for (FlowCompilation.BranchDecision decision : path.decisions()) {
        pathsByGuard
            .computeIfAbsent(decision.guardNodeId(), ignored -> new ArrayList<>())
            .add(path);
      }
    }
    List<FlowCompilation.ProcessJoinSignalV1> counters = new ArrayList<>();
    for (Map.Entry<String, List<TraversalPath>> guardEntry : pathsByGuard.entrySet()) {
      String guardNodeId = guardEntry.getKey();
      List<TraversalPath> guardPaths = guardEntry.getValue();
      List<TraversalPath> callPaths =
          guardPaths.stream()
              .filter(path -> path.nodeIds().contains(exactCall.tuple().invocationCallId()))
              .toList();
      if (callPaths.isEmpty()) continue;
      List<FlowCompilation.BranchDecision> callDecisions =
          callPaths.stream()
              .flatMap(path -> path.decisions().stream())
              .filter(decision -> guardNodeId.equals(decision.guardNodeId()))
              .toList();
      Set<String> polarities =
          callDecisions.stream()
              .map(FlowCompilation.BranchDecision::polarity)
              .collect(java.util.stream.Collectors.toSet());
      if (polarities.size() != 1) continue;
      String polarity = polarities.iterator().next();
      String oppositePolarity = "TRUE".equals(polarity) ? "FALSE" : "TRUE";
      boolean oppositeExists =
          guardPaths.stream()
              .anyMatch(
                  path ->
                      recordsGuardPolarity(
                          path,
                          guardNodeId,
                          callDecisions.get(0).conditionAtomId(),
                          oppositePolarity));
      boolean oppositeCalls =
          guardPaths.stream()
              .filter(
                  path ->
                      recordsGuardPolarity(
                          path,
                          guardNodeId,
                          callDecisions.get(0).conditionAtomId(),
                          oppositePolarity))
              .anyMatch(path -> path.nodeIds().contains(exactCall.tuple().invocationCallId()));
      if (!oppositeExists || oppositeCalls) continue;
      FlowCompilation.ProcessJoinSignalV1 counter =
          exactCallCounterCondition(
              entry,
              facts,
              exactCall,
              flowSliceId,
              guardNodeId,
              callDecisions.get(0).conditionAtomId(),
              reopened);
      if (counter != null) counters.add(counter);
    }
    return counters.stream()
        .sorted(
            Comparator.comparing(
                FlowCompilation.ProcessJoinSignalV1::processJoinSignalId, UTF8_ORDER))
        .toList();
  }

  private static FlowCompilation.ProcessJoinSignalV1 exactCallCounterCondition(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      ExactCall exactCall,
      String flowSliceId,
      String guardNodeId,
      String conditionAtomId,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    PersistedFlowCompilationInputReader.ControlNode guard =
        reopened.controlNodesById().get(guardNodeId);
    if (guard == null
        || !"GUARD".equals(guard.kind())
        || !guard.owners().equals(List.of(entry.entryId()))) {
      throw signalInvalid();
    }
    List<PersistedFlowCompilationInputReader.PersistedFact> guardFacts =
        facts.stream()
            .filter(fact -> "JAVA_GUARD_CONDITION".equals(fact.kind()))
            .filter(fact -> entry.entryId().equals(fact.entryId()))
            .filter(fact -> fact.subjectNodeIds().equals(List.of(guardNodeId)))
            .toList();
    if (guardFacts.size() != 1) return null;
    PersistedFlowCompilationInputReader.PersistedFact guardFact = guardFacts.get(0);
    List<PersistedFlowCompilationInputReader.PersistedAtom> conditionAtoms =
        guardFact.atoms().stream()
            .filter(atom -> conditionAtomId.equals(atom.atomId()))
            .filter(atom -> "CONTROL_CONDITION".equals(atom.name()))
            .filter(atom -> "CONDITION".equals(atom.role()))
            .filter(atom -> "STRING".equals(atom.valueType()))
            .filter(atom -> guard.normalizedCondition().equals(atom.canonicalValue()))
            .toList();
    if (conditionAtoms.size() != 1) return null;
    SignalBasis guardBasis = closedBasis(guardFact, conditionAtoms, List.of(), reopened);
    SignalBasis callBasis =
        closedBasis(exactCall.fact(), exactCall.callAtoms(), List.of(), reopened);
    return signal(
        flowSliceId,
        "COUNTER_CONDITION",
        "CALL_TARGET",
        exactCall.tuple().anchorKey(),
        "BLOCKS",
        "GENERIC_TECHNICAL",
        "FROZEN_JAVA",
        unionBasis(guardBasis, callBasis),
        List.of());
  }

  private static Map<String, PersistedFlowCompilationInputReader.PersistedAtom>
      validatedExactCallAtoms(PersistedFlowCompilationInputReader.PersistedFact fact) {
    Map<String, AtomRequirement> expected =
        Map.of(
            "INVOCATION_CALL_ID", new AtomRequirement("RELATIONSHIP", "SYMBOL_REF"),
            "STATIC_TARGET_TYPE", new AtomRequirement("ATTRIBUTE", "STRING"),
            "STATIC_TARGET_METHOD", new AtomRequirement("ATTRIBUTE", "STRING"),
            "STATIC_TARGET_SIGNATURE", new AtomRequirement("ATTRIBUTE", "STRING"));
    Map<String, PersistedFlowCompilationInputReader.PersistedAtom> atoms = new HashMap<>();
    for (PersistedFlowCompilationInputReader.PersistedAtom atom : fact.atoms()) {
      AtomRequirement requirement = expected.get(atom.name());
      if (requirement == null
          || !requirement.role().equals(atom.role())
          || !requirement.valueType().equals(atom.valueType())
          || atoms.put(atom.name(), atom) != null) {
        throw signalInvalid();
      }
    }
    if (!atoms.keySet().equals(expected.keySet())
        || !atoms
            .get("STATIC_TARGET_SIGNATURE")
            .canonicalValue()
            .startsWith(atoms.get("STATIC_TARGET_METHOD").canonicalValue() + "(")
        || !atoms.get("STATIC_TARGET_SIGNATURE").canonicalValue().endsWith(")")) {
      throw signalInvalid();
    }
    return Map.copyOf(atoms);
  }

  private static FlowCompilation.ProcessJoinSignalV1 counterCondition(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      PersistedFlowCompilationInputReader.PersistedFact boundaryFact,
      PersistedFlowCompilationInputReader.BoundaryInvocation boundary,
      Map<String, PersistedFlowCompilationInputReader.PersistedAtom> boundaryAtoms,
      List<PersistedFlowCompilationInputReader.PersistedAtom> callAtoms,
      String callKey,
      String flowSliceId,
      List<TraversalPath> paths,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    if (boundary.guardNodeId() == null) return null;
    if (!Set.of("TRUE", "FALSE").contains(boundary.polarity())) throw signalInvalid();
    PersistedFlowCompilationInputReader.ControlNode guard =
        reopened.controlNodesById().get(boundary.guardNodeId());
    if (guard == null
        || !"GUARD".equals(guard.kind())
        || !guard.owners().equals(List.of(entry.entryId()))) {
      throw signalInvalid();
    }
    List<PersistedFlowCompilationInputReader.PersistedFact> guardFacts =
        facts.stream()
            .filter(fact -> "JAVA_GUARD_CONDITION".equals(fact.kind()))
            .filter(fact -> entry.entryId().equals(fact.entryId()))
            .filter(fact -> fact.subjectNodeIds().equals(List.of(boundary.guardNodeId())))
            .toList();
    if (guardFacts.size() != 1) return null;
    PersistedFlowCompilationInputReader.PersistedFact guardFact = guardFacts.get(0);
    List<PersistedFlowCompilationInputReader.PersistedAtom> conditionAtoms =
        guardFact.atoms().stream().filter(atom -> "CONTROL_CONDITION".equals(atom.name())).toList();
    if (conditionAtoms.size() != 1) throw signalInvalid();
    PersistedFlowCompilationInputReader.PersistedAtom conditionAtom = conditionAtoms.get(0);
    if (!"CONDITION".equals(conditionAtom.role())
        || !"STRING".equals(conditionAtom.valueType())
        || !guard.normalizedCondition().equals(conditionAtom.canonicalValue())) {
      throw signalInvalid();
    }

    List<TraversalPath> guardPaths =
        paths.stream()
            .filter(
                path ->
                    path.decisions().stream()
                        .anyMatch(
                            decision -> boundary.guardNodeId().equals(decision.guardNodeId())))
            .toList();
    if (guardPaths.stream()
        .anyMatch(
            path -> !recordsOnlyGuardAtom(path, boundary.guardNodeId(), conditionAtom.atomId()))) {
      return null;
    }
    List<TraversalPath> recordedPolarityPaths =
        guardPaths.stream()
            .filter(
                path ->
                    recordsGuardPolarity(
                        path, boundary.guardNodeId(), conditionAtom.atomId(), boundary.polarity()))
            .toList();
    String oppositePolarity = "TRUE".equals(boundary.polarity()) ? "FALSE" : "TRUE";
    List<TraversalPath> oppositePolarityPaths =
        guardPaths.stream()
            .filter(
                path ->
                    recordsGuardPolarity(
                        path, boundary.guardNodeId(), conditionAtom.atomId(), oppositePolarity))
            .toList();
    if (recordedPolarityPaths.isEmpty()
        || oppositePolarityPaths.isEmpty()
        || recordedPolarityPaths.stream()
            .noneMatch(path -> path.nodeIds().contains(boundary.invocationCallId()))
        || oppositePolarityPaths.stream()
            .anyMatch(path -> path.nodeIds().contains(boundary.invocationCallId()))) {
      return null;
    }

    List<PersistedFlowCompilationInputReader.PersistedAtom> counterBoundaryAtoms =
        new ArrayList<>(callAtoms);
    counterBoundaryAtoms.add(boundaryAtoms.get("CONTROL_CONTEXT"));
    SignalBasis guardBasis = closedBasis(guardFact, List.of(conditionAtom), List.of(), reopened);
    SignalBasis boundaryBasis =
        closedBasis(boundaryFact, counterBoundaryAtoms, List.of(), reopened);
    return signal(
        flowSliceId,
        "COUNTER_CONDITION",
        "CALL_TARGET",
        callKey,
        "BLOCKS",
        "GENERIC_TECHNICAL",
        "FROZEN_JAVA",
        unionBasis(guardBasis, boundaryBasis),
        List.of());
  }

  private static boolean recordsOnlyGuardAtom(
      TraversalPath path, String guardNodeId, String conditionAtomId) {
    List<FlowCompilation.BranchDecision> decisions =
        path.decisions().stream()
            .filter(decision -> guardNodeId.equals(decision.guardNodeId()))
            .toList();
    return decisions.size() == 1 && conditionAtomId.equals(decisions.get(0).conditionAtomId());
  }

  private static boolean recordsGuardPolarity(
      TraversalPath path, String guardNodeId, String conditionAtomId, String polarity) {
    return recordsOnlyGuardAtom(path, guardNodeId, conditionAtomId)
        && polarity.equals(
            path.decisions().stream()
                .filter(decision -> guardNodeId.equals(decision.guardNodeId()))
                .findFirst()
                .orElseThrow(EntryRootedFlowCompiler::signalInvalid)
                .polarity());
  }

  private static SignalBasis unionBasis(SignalBasis left, SignalBasis right) {
    List<SourceLocatorV1> sourceLocators = new ArrayList<>(left.sourceLocators());
    sourceLocators.addAll(right.sourceLocators());
    return new SignalBasis(
        orderedUnion(concat(left.factIds(), right.factIds())),
        orderedUnion(concat(left.atomIds(), right.atomIds())),
        orderedUnion(concat(left.proofIds(), right.proofIds())),
        orderedUnion(concat(left.evidenceNodeIds(), right.evidenceNodeIds())),
        sourceLocators.stream()
            .distinct()
            .sorted(
                Comparator.comparing(SourceLocatorV1::path)
                    .thenComparingLong(SourceLocatorV1::startByte)
                    .thenComparingLong(SourceLocatorV1::endByteExclusive))
            .toList());
  }

  private static <T> List<T> concat(List<T> left, List<T> right) {
    List<T> values = new ArrayList<>(left);
    values.addAll(right);
    return values;
  }

  private static PersistedFlowCompilationInputReader.BoundaryInvocation boundaryFor(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      PersistedFlowCompilationInputReader.PersistedFact fact,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    List<PersistedFlowCompilationInputReader.BoundaryInvocation> boundaries =
        fact.subjectNodeIds().stream()
            .map(reopened.boundariesByNodeId()::get)
            .filter(Objects::nonNull)
            .toList();
    if (!entry.entryId().equals(fact.entryId())
        || boundaries.size() != 1
        || !boundaries.get(0).owningEntryIds().contains(entry.entryId())) {
      throw signalFlowMismatch();
    }
    return boundaries.get(0);
  }

  private static Map<String, PersistedFlowCompilationInputReader.PersistedAtom>
      validatedBoundaryAtoms(
          PersistedFlowCompilationInputReader.PersistedFact fact,
          PersistedFlowCompilationInputReader.BoundaryInvocation boundary) {
    Map<String, AtomRequirement> expected =
        Map.of(
            "INVOCATION_CALL_ID", new AtomRequirement("RELATIONSHIP", "SYMBOL_REF"),
            "STATIC_TARGET_TYPE", new AtomRequirement("ATTRIBUTE", "STRING"),
            "STATIC_TARGET_METHOD", new AtomRequirement("ATTRIBUTE", "STRING"),
            "STATIC_TARGET_SIGNATURE", new AtomRequirement("ATTRIBUTE", "STRING"),
            "ORDERED_ARGUMENTS", new AtomRequirement("RELATIONSHIP", "SYMBOL_REF"),
            "JAVA_LOCAL_ORIGINS", new AtomRequirement("RELATIONSHIP", "SYMBOL_REF"),
            "CONTROL_CONTEXT", new AtomRequirement("CONDITION", "SYMBOL_REF"),
            "INVOCATION_EVIDENCE", new AtomRequirement("RELATIONSHIP", "SYMBOL_REF"));
    Map<String, PersistedFlowCompilationInputReader.PersistedAtom> atoms = new HashMap<>();
    for (PersistedFlowCompilationInputReader.PersistedAtom atom : fact.atoms()) {
      AtomRequirement requirement = expected.get(atom.name());
      if (requirement == null
          || !requirement.role().equals(atom.role())
          || !requirement.valueType().equals(atom.valueType())
          || atoms.put(atom.name(), atom) != null) {
        throw signalInvalid();
      }
    }
    if (!atoms.keySet().equals(expected.keySet())
        || !boundary.invocationCallId().equals(atoms.get("INVOCATION_CALL_ID").canonicalValue())
        || !boundary.staticTargetType().equals(atoms.get("STATIC_TARGET_TYPE").canonicalValue())
        || !boundary.staticTargetMethod().equals(atoms.get("STATIC_TARGET_METHOD").canonicalValue())
        || !boundary
            .staticTargetSignature()
            .equals(atoms.get("STATIC_TARGET_SIGNATURE").canonicalValue())
        || !boundary
            .controlBlockNodeId()
            .concat(boundary.guardNodeId() == null ? "" : "|" + boundary.guardNodeId())
            .equals(atoms.get("CONTROL_CONTEXT").canonicalValue())
        || !boundary.staticTargetSignature().startsWith(boundary.staticTargetMethod() + "(")
        || !boundary.staticTargetSignature().endsWith(")")) {
      throw signalInvalid();
    }
    return Map.copyOf(atoms);
  }

  private static PersistedFlowCompilationInputReader.PersistedGap externalEffectGap(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      PersistedFlowCompilationInputReader.PersistedFact fact,
      PersistedFlowCompilationInputReader.BoundaryInvocation boundary,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    List<PersistedFlowCompilationInputReader.PersistedGap> matches =
        reopened.gapsByEntry().getOrDefault(entry.entryId(), List.of()).stream()
            .filter(gap -> "EXTERNAL_EFFECT".equals(gap.kind()))
            .filter(gap -> "DATA_FLOW_BINDING_UNPROVEN".equals(gap.code()))
            .filter(
                gap ->
                    gap.affectedCandidateDenominatorKeys()
                        .equals(List.of(fact.candidateDenominatorKey())))
            .filter(gap -> gap.affectedEntryIds().equals(List.of(entry.entryId())))
            .toList();
    if (matches.size() != 1) throw externalEffectUnproven();
    PersistedFlowCompilationInputReader.PersistedGap gap = matches.get(0);
    List<SourceLocatorV1> gapLocators =
        gap.evidenceNodeIds().stream()
            .map(reopened.evidenceNodesById()::get)
            .filter(Objects::nonNull)
            .map(PersistedFlowCompilationInputReader.PersistedEvidenceNode::sourceLocator)
            .filter(Objects::nonNull)
            .toList();
    if (!gap.evidenceNodeIds().stream().allMatch(reopened.evidenceNodesById()::containsKey)
        || !gapLocators.contains(boundary.sourceLocator())) {
      throw externalEffectUnproven();
    }
    return gap;
  }

  private static SignalBasis closedBasis(
      PersistedFlowCompilationInputReader.PersistedFact fact,
      List<PersistedFlowCompilationInputReader.PersistedAtom> atoms,
      List<String> additionalEvidenceNodeIds,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    if (atoms.isEmpty()) throw signalInvalid();
    List<String> proofIds = new ArrayList<>();
    List<String> evidenceNodeIds = new ArrayList<>(additionalEvidenceNodeIds);
    for (PersistedFlowCompilationInputReader.PersistedAtom atom : atoms) {
      PersistedFlowCompilationInputReader.PersistedProof proof =
          reopened.proofsById().get(atom.proofId());
      if (proof == null
          || !"CLOSED".equals(proof.status())
          || !fact.factId().equals(proof.factId())
          || !atom.atomId().equals(proof.atomId())
          || !proof.requiredEvidenceNodeIds().contains(proof.rootEvidenceNodeId())
          || !proof.requiredEvidenceNodeIds().stream()
              .allMatch(reopened.evidenceNodesById()::containsKey)) {
        throw signalInvalid();
      }
      proofIds.add(proof.proofId());
      evidenceNodeIds.addAll(proof.requiredEvidenceNodeIds());
    }
    if (!additionalEvidenceNodeIds.stream().allMatch(reopened.evidenceNodesById()::containsKey)) {
      throw signalInvalid();
    }
    List<String> orderedEvidenceNodeIds = orderedUnion(evidenceNodeIds);
    List<SourceLocatorV1> sourceLocators =
        orderedEvidenceNodeIds.stream()
            .map(reopened.evidenceNodesById()::get)
            .filter(Objects::nonNull)
            .filter(node -> node.sourceLocator() != null)
            .map(PersistedFlowCompilationInputReader.PersistedEvidenceNode::sourceLocator)
            .distinct()
            .sorted(
                Comparator.comparing(SourceLocatorV1::path)
                    .thenComparingLong(SourceLocatorV1::startByte)
                    .thenComparingLong(SourceLocatorV1::endByteExclusive))
            .toList();
    if (sourceLocators.isEmpty()) throw signalInvalid();
    return new SignalBasis(
        List.of(fact.factId()),
        orderedDistinct(
            atoms.stream().map(PersistedFlowCompilationInputReader.PersistedAtom::atomId).toList()),
        orderedDistinct(proofIds),
        orderedEvidenceNodeIds,
        sourceLocators);
  }

  private static FlowCompilation.ProcessJoinSignalV1 signal(
      String flowSliceId,
      String signalKind,
      String anchorKind,
      String anchorKey,
      String direction,
      String specificity,
      String claimScope,
      SignalBasis basis,
      List<String> gapIds) {
    try {
      return FlowCompilation.ProcessJoinSignalV1.create(
          flowSliceId,
          signalKind,
          anchorKind,
          anchorKey,
          direction,
          specificity,
          claimScope,
          basis.factIds(),
          basis.atomIds(),
          basis.proofIds(),
          basis.evidenceNodeIds(),
          basis.sourceLocators(),
          gapIds);
    } catch (IllegalArgumentException malformed) {
      throw signalInvalid();
    }
  }

  private static void validateSignalFlowClosure(
      String flowSliceId,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      List<FlowCompilation.ProcessJoinSignalV1> signals,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    Map<String, PersistedFlowCompilationInputReader.PersistedFact> factsById = new HashMap<>();
    Map<String, String> factByAtomId = new HashMap<>();
    for (PersistedFlowCompilationInputReader.PersistedFact fact : facts) {
      factsById.put(fact.factId(), fact);
      for (PersistedFlowCompilationInputReader.PersistedAtom atom : fact.atoms()) {
        if (factByAtomId.put(atom.atomId(), fact.factId()) != null) throw signalFlowMismatch();
      }
    }
    for (FlowCompilation.ProcessJoinSignalV1 signal : signals) {
      if (!flowSliceId.equals(signal.flowSliceId())
          || !signal.factIds().stream().allMatch(factsById::containsKey)
          || !signal.atomIds().stream()
              .allMatch(atomId -> signal.factIds().contains(factByAtomId.get(atomId)))) {
        throw signalFlowMismatch();
      }
      for (String atomId : signal.atomIds()) {
        String factId = factByAtomId.get(atomId);
        PersistedFlowCompilationInputReader.PersistedAtom atom =
            factsById.get(factId).atoms().stream()
                .filter(candidate -> atomId.equals(candidate.atomId()))
                .findFirst()
                .orElseThrow(EntryRootedFlowCompiler::signalFlowMismatch);
        PersistedFlowCompilationInputReader.PersistedProof proof =
            reopened.proofsById().get(atom.proofId());
        if (proof == null
            || !signal.proofIds().contains(proof.proofId())
            || !factId.equals(proof.factId())
            || !atomId.equals(proof.atomId())) {
          throw signalFlowMismatch();
        }
      }
    }
  }

  private static int basisReferenceCount(FlowCompilation.ProcessJoinSignalV1 signal) {
    return signal.factIds().size()
        + signal.atomIds().size()
        + signal.proofIds().size()
        + signal.evidenceNodeIds().size()
        + signal.sourceLocators().size()
        + signal.gapIds().size();
  }

  private static List<String> orderedDistinct(List<String> values) {
    if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw signalInvalid();
    }
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (ordered.size() != ordered.stream().distinct().count()) throw signalInvalid();
    return ordered;
  }

  private static List<String> orderedUnion(List<String> values) {
    if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw signalInvalid();
    }
    return values.stream().distinct().sorted(UTF8_ORDER).toList();
  }

  private static List<TraversalPath> traverse(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      PersistedFlowCompilationInputReader.ControlNode root,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened,
      FlowCompilationProfile profile) {
    List<TraversalPath> outcomes = new ArrayList<>();
    PersistedFlowCompilationInputReader.ControlTraversal traversal =
        reopened.controlTraversalsByEntry().get(entry.entryId());
    if (traversal == null || !traversal.nodeIds().contains(root.nodeId())) {
      throw broken("FLOW_GRAPH_REFERENCE_BROKEN");
    }
    traverse(
        entry,
        facts,
        reopened,
        profile,
        traversal,
        new TraversalPath(List.of(root.nodeId()), List.of(), List.of(), List.of(), List.of(), null),
        outcomes);
    if (outcomes.isEmpty() || outcomes.size() > profile.maxOutcomesPerFlow()) {
      throw broken(
          outcomes.size() > profile.maxOutcomesPerFlow()
              ? "BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED"
              : "FLOW_OUTCOME_CLOSURE_BROKEN");
    }
    return List.copyOf(outcomes);
  }

  private static void traverse(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened,
      FlowCompilationProfile profile,
      PersistedFlowCompilationInputReader.ControlTraversal traversal,
      TraversalPath path,
      List<TraversalPath> outcomes) {
    String currentNodeId = path.currentNodeId();
    PersistedFlowCompilationInputReader.ControlNode current =
        reopened.controlNodesById().get(currentNodeId);
    if (!traversal.nodeIds().contains(currentNodeId)
        || (current != null && !current.owners().contains(entry.entryId()))) {
      throw broken("FLOW_GRAPH_REFERENCE_BROKEN");
    }
    if (current != null && "CALLEE_RETURN_TERMINAL".equals(current.kind())) {
      returnFromCallee(entry, facts, reopened, profile, traversal, path, outcomes);
      return;
    }
    if (current != null
        && ("ENTRY_RETURN_TERMINAL".equals(current.kind())
            || "THROW_TERMINAL".equals(current.kind()))) {
      outcomes.add(path.terminated(current.kind()));
      return;
    }
    if (path.nodeIds().size() > profile.maxFlowNodes()
        || path.edgeIds().size() >= profile.maxFlowEdges()
        || path.edgeIds().size() >= profile.maxTraversalDepth()) {
      throw broken("BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED");
    }
    List<PersistedFlowCompilationInputReader.ControlEdge> outgoing =
        reopened.controlEdgesById().values().stream()
            .filter(edge -> currentNodeId.equals(edge.fromNodeId()))
            .filter(edge -> belongsToTraversal(edge, traversal))
            .filter(edge -> !path.edgeIds().contains(edge.edgeId()))
            .sorted(
                Comparator.comparing(
                    PersistedFlowCompilationInputReader.ControlEdge::edgeId, UTF8_ORDER))
            .toList();
    List<PersistedFlowCompilationInputReader.ControlEdge> structural =
        outgoing.stream()
            .filter(edge -> !"RETURN".equals(edge.kind()))
            .filter(edge -> !path.completedCallEdgeIds().contains(edge.edgeId()))
            .toList();
    if (structural.isEmpty()) {
      returnFromCallee(entry, facts, reopened, profile, traversal, path, outcomes);
      return;
    }
    List<PersistedFlowCompilationInputReader.ControlEdge> selected =
        preferredSuccessors(structural, reopened.controlNodesById());
    for (PersistedFlowCompilationInputReader.ControlEdge edge : selected) {
      List<FlowCompilation.BranchDecision> decisions = new ArrayList<>(path.decisions());
      if ("TRUE".equals(edge.kind()) || "FALSE".equals(edge.kind())) {
        decisions.add(branchDecision(entry, edge, facts, reopened.controlNodesById()));
      }
      List<String> nextNodes = new ArrayList<>(path.nodeIds());
      nextNodes.add(edge.toNodeId());
      List<String> nextEdges = new ArrayList<>(path.edgeIds());
      nextEdges.add(edge.edgeId());
      List<CallFrame> callStack = new ArrayList<>(path.callStack());
      if ("CALL".equals(edge.kind())) {
        callStack.add(new CallFrame(edge.edgeId(), edge.fromNodeId(), edge.toNodeId()));
      }
      traverse(
          entry,
          facts,
          reopened,
          profile,
          traversal,
          new TraversalPath(
              nextNodes, decisions, nextEdges, callStack, path.completedCallEdgeIds(), null),
          outcomes);
    }
  }

  private static void returnFromCallee(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened,
      FlowCompilationProfile profile,
      PersistedFlowCompilationInputReader.ControlTraversal traversal,
      TraversalPath path,
      List<TraversalPath> outcomes) {
    if (path.callStack().isEmpty()) throw broken("FLOW_OUTCOME_CLOSURE_BROKEN");
    CallFrame frame = path.callStack().get(path.callStack().size() - 1);
    List<PersistedFlowCompilationInputReader.ControlEdge> returns =
        reopened.controlEdgesById().values().stream()
            .filter(edge -> "RETURN".equals(edge.kind()))
            .filter(edge -> frame.calleeNodeId().equals(edge.fromNodeId()))
            .filter(edge -> frame.callerNodeId().equals(edge.toNodeId()))
            .filter(edge -> belongsToTraversal(edge, traversal))
            .filter(edge -> !path.edgeIds().contains(edge.edgeId()))
            .sorted(
                Comparator.comparing(
                    PersistedFlowCompilationInputReader.ControlEdge::edgeId, UTF8_ORDER))
            .toList();
    if (returns.size() != 1) throw broken("FLOW_RETURN_REFERENCE_BROKEN");
    List<String> nodes = new ArrayList<>(path.nodeIds());
    nodes.add(frame.callerNodeId());
    List<String> edges = new ArrayList<>(path.edgeIds());
    edges.add(returns.get(0).edgeId());
    List<CallFrame> remaining = new ArrayList<>(path.callStack());
    remaining.remove(remaining.size() - 1);
    List<String> completed = new ArrayList<>(path.completedCallEdgeIds());
    completed.add(frame.callEdgeId());
    traverse(
        entry,
        facts,
        reopened,
        profile,
        traversal,
        new TraversalPath(nodes, path.decisions(), edges, remaining, completed, null),
        outcomes);
  }

  private static boolean belongsToTraversal(
      PersistedFlowCompilationInputReader.ControlEdge edge,
      PersistedFlowCompilationInputReader.ControlTraversal traversal) {
    return traversal.edgeIds().contains(edge.edgeId())
        && traversal.nodeIds().contains(edge.fromNodeId())
        && traversal.nodeIds().contains(edge.toNodeId());
  }

  private static List<PersistedFlowCompilationInputReader.ControlEdge> preferredSuccessors(
      List<PersistedFlowCompilationInputReader.ControlEdge> outgoing,
      Map<String, PersistedFlowCompilationInputReader.ControlNode> nodes) {
    List<PersistedFlowCompilationInputReader.ControlEdge> guardEntries =
        outgoing.stream()
            .filter(
                edge -> {
                  PersistedFlowCompilationInputReader.ControlNode target =
                      nodes.get(edge.toNodeId());
                  return target != null && "GUARD".equals(target.kind());
                })
            .toList();
    if (!guardEntries.isEmpty()) return guardEntries;
    List<PersistedFlowCompilationInputReader.ControlEdge> calls =
        outgoing.stream().filter(edge -> "CALL".equals(edge.kind())).toList();
    return calls.isEmpty() ? outgoing : calls;
  }

  private static FlowCompilation.BranchDecision branchDecision(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      PersistedFlowCompilationInputReader.ControlEdge edge,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      Map<String, PersistedFlowCompilationInputReader.ControlNode> nodes) {
    PersistedFlowCompilationInputReader.ControlNode guard = nodes.get(edge.guardNodeId());
    if (guard == null
        || !"GUARD".equals(guard.kind())
        || !guard.nodeId().equals(edge.fromNodeId())
        || !guard.owners().contains(entry.entryId())
        || !edge.kind().equals(edge.polarity())) {
      throw broken("FLOW_GUARD_REFERENCE_BROKEN");
    }
    List<PersistedFlowCompilationInputReader.PersistedAtom> conditionAtoms =
        facts.stream()
            .filter(fact -> "JAVA_GUARD_CONDITION".equals(fact.kind()))
            .filter(fact -> fact.subjectNodeIds().contains(guard.nodeId()))
            .flatMap(fact -> fact.atoms().stream())
            .filter(atom -> "CONTROL_CONDITION".equals(atom.name()))
            .filter(atom -> guard.normalizedCondition().equals(atom.canonicalValue()))
            .sorted(
                Comparator.comparing(
                    PersistedFlowCompilationInputReader.PersistedAtom::atomId, UTF8_ORDER))
            .toList();
    if (conditionAtoms.size() != 1) {
      throw broken(
          conditionAtoms.isEmpty()
              ? "FLOW_CONDITION_ATOM_UNPROVEN"
              : "FLOW_CONDITION_ATOM_AMBIGUOUS");
    }
    return new FlowCompilation.BranchDecision(
        guard.nodeId(),
        conditionAtoms.get(0).atomId(),
        edge.polarity(),
        guard.normalizedCondition());
  }

  private static String decisionsIdentity(List<FlowCompilation.BranchDecision> decisions) {
    return decisions.stream()
        .map(
            decision ->
                String.join(
                    "\u0001",
                    decision.guardNodeId(),
                    decision.conditionAtomId(),
                    decision.polarity(),
                    decision.normalizedCondition()))
        .collect(java.util.stream.Collectors.joining("\u0000"));
  }

  private static List<String> longestCommonPrefix(List<List<String>> paths) {
    if (paths.isEmpty()) throw broken("FLOW_OUTCOME_CLOSURE_BROKEN");
    List<String> prefix = new ArrayList<>(paths.get(0));
    for (int pathIndex = 1; pathIndex < paths.size(); pathIndex++) {
      List<String> next = paths.get(pathIndex);
      int common = 0;
      while (common < prefix.size()
          && common < next.size()
          && prefix.get(common).equals(next.get(common))) {
        common++;
      }
      prefix = new ArrayList<>(prefix.subList(0, common));
    }
    if (prefix.isEmpty()) throw broken("FLOW_ROOT_CLOSURE_BROKEN");
    return List.copyOf(prefix);
  }

  private static String contentId(String prefix, String... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(frame(prefix));
      for (String value : values) digest.update(frame(value));
      return prefix + ":" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
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

  private static ProcessJoinSignalIntegrityException signalInvalid() {
    return new ProcessJoinSignalIntegrityException("PROCESS_JOIN_SIGNAL_BASIS_INVALID");
  }

  private static ProcessJoinSignalIntegrityException signalFlowMismatch() {
    return new ProcessJoinSignalIntegrityException("PROCESS_JOIN_SIGNAL_FLOW_MISMATCH");
  }

  private static ProcessJoinSignalIntegrityException externalEffectUnproven() {
    return new ProcessJoinSignalIntegrityException("PROCESS_JOIN_SIGNAL_EXTERNAL_EFFECT_UNPROVEN");
  }

  private static IllegalArgumentException broken(String code) {
    return new IllegalArgumentException(code);
  }

  private record AtomRequirement(String role, String valueType) {}

  private record SignalBasis(
      List<String> factIds,
      List<String> atomIds,
      List<String> proofIds,
      List<String> evidenceNodeIds,
      List<SourceLocatorV1> sourceLocators) {}

  private record CallTuple(String entryId, String invocationCallId, String anchorKey) {}

  private record ExactCall(
      CallTuple tuple,
      FlowCompilation.ProcessJoinSignalV1 signal,
      PersistedFlowCompilationInputReader.PersistedFact fact,
      List<PersistedFlowCompilationInputReader.PersistedAtom> callAtoms) {}

  private static final class ProcessJoinSignalIntegrityException extends IllegalArgumentException {

    private ProcessJoinSignalIntegrityException(String code) {
      super(code);
    }
  }

  private record TraversalPath(
      List<String> nodeIds,
      List<FlowCompilation.BranchDecision> decisions,
      List<String> edgeIds,
      List<CallFrame> callStack,
      List<String> completedCallEdgeIds,
      String terminalKind) {

    private TraversalPath {
      nodeIds = List.copyOf(nodeIds);
      decisions = List.copyOf(decisions);
      edgeIds = List.copyOf(edgeIds);
      callStack = List.copyOf(callStack);
      completedCallEdgeIds = List.copyOf(completedCallEdgeIds);
    }

    private String currentNodeId() {
      return nodeIds.get(nodeIds.size() - 1);
    }

    private String terminalNodeId() {
      return currentNodeId();
    }

    private TraversalPath terminated(String kind) {
      if (kind == null || kind.isBlank() || terminalKind != null) {
        throw broken("FLOW_OUTCOME_CLOSURE_BROKEN");
      }
      return new TraversalPath(nodeIds, decisions, edgeIds, callStack, completedCallEdgeIds, kind);
    }
  }

  private record CallFrame(String callEdgeId, String callerNodeId, String calleeNodeId) {}
}
