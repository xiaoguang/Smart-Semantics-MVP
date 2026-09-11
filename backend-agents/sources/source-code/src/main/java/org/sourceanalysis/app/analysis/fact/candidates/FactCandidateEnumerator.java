package org.sourceanalysis.app.analysis.fact.candidates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.graph.ProgramGraphKind;

/**
 * Enumerates exact frozen-Java boundary and guard-condition candidates from persisted public
 * graphs.
 *
 * <p>This module does not choose a Proof, admit a Fact, inspect source text, parse canonical
 * values, or infer any external system behavior.
 */
public final class FactCandidateEnumerator {

  private static final String JAVA_BOUNDARY_KIND = "JAVA_BOUNDARY_INVOCATION";
  private static final String JAVA_GUARD_KIND = "JAVA_GUARD_CONDITION";
  private static final String JAVA_EXACT_CALL_KIND = "JAVA_EXACT_CALL";
  private static final String DATA_ARGUMENT_EDGE = "ARGUMENT_TO_BOUNDARY";
  private static final String CALL_SITE = "CALL_SITE";
  private static final String CALL_TARGET = "CALL_TARGET";
  private static final String METHOD = "METHOD";
  private static final String BASIC_BLOCK = "BASIC_BLOCK";
  private static final String GUARD = "GUARD";
  private static final String EXACT = "EXACT";
  private static final String STATIC_FIELD_RECEIVER_CALL_RULE =
      "java-static-field-receiver-call-v1";
  private static final String NOT_APPLICABLE_REASON = "DATA_FLOW_BINDING_UNPROVEN";
  private static final String EXACT_CALL_TARGET_NOT_METHOD = "JAVA_EXACT_CALL_TARGET_NOT_METHOD";
  private static final String REQUIRED_ATOM_MISSING = "REQUIRED_ATOM_MISSING";
  private static final String PROOF_NOT_CLOSED = "PROOF_NOT_CLOSED";
  private static final String TARGET_METHOD_CANONICAL = "TARGET_METHOD_CANONICAL";
  private static final String CALL_SITE_EVIDENCE = "CALL_SITE_EVIDENCE";
  private static final String CALL_TARGET_EDGE_EVIDENCE = "CALL_TARGET_EDGE_EVIDENCE";
  private static final String TARGET_METHOD_EVIDENCE = "TARGET_METHOD_EVIDENCE";

  /**
   * Enumerates the complete applicable/not-applicable denominator governed by the supplied
   * registry.
   */
  public FactCandidateSet enumerate(FactCandidateInputs inputs, FactRegistry registry) {
    Objects.requireNonNull(inputs, "fact candidate inputs");
    Objects.requireNonNull(registry, "fact registry");

    List<FactCandidateSet.FactCandidate> candidates = new ArrayList<>();
    List<FactCandidateSet.NotApplicableDisposition> notApplicable = new ArrayList<>();
    for (FactRegistry.FactTemplate template : registry.templates()) {
      if (JAVA_BOUNDARY_KIND.equals(template.kind())) {
        enumerateBoundaryCandidates(inputs, template, candidates, notApplicable);
      } else if (JAVA_GUARD_KIND.equals(template.kind())) {
        enumerateGuardCandidates(inputs, template, candidates, notApplicable);
      } else if (JAVA_EXACT_CALL_KIND.equals(template.kind())) {
        enumerateExactCallCandidates(inputs, template, candidates, notApplicable);
      } else {
        throw new IllegalArgumentException("FACT_KIND_UNSUPPORTED");
      }
    }
    return FactCandidateSet.create(inputs.sourceGraphRoots(), candidates, notApplicable);
  }

  private static void enumerateBoundaryCandidates(
      FactCandidateInputs inputs,
      FactRegistry.FactTemplate template,
      List<FactCandidateSet.FactCandidate> candidates,
      List<FactCandidateSet.NotApplicableDisposition> notApplicable) {
    inputs.graph(ProgramGraphKind.DATA_FLOW).nodesById().values().stream()
        .filter(node -> JAVA_BOUNDARY_KIND.equals(node.kind()))
        .sorted(Comparator.comparing(FactCandidateInputs.PublicProgramNode::nodeId))
        .forEach(
            boundary ->
                boundary.owningEntryIds().stream()
                    .filter(inputs.entryIds()::contains)
                    .sorted()
                    .forEach(
                        entryId ->
                            enumerateBoundaryTemplate(
                                inputs, template, entryId, boundary, candidates, notApplicable)));
  }

  private static void enumerateBoundaryTemplate(
      FactCandidateInputs inputs,
      FactRegistry.FactTemplate template,
      String entryId,
      FactCandidateInputs.PublicProgramNode boundary,
      List<FactCandidateSet.FactCandidate> candidates,
      List<FactCandidateSet.NotApplicableDisposition> notApplicable) {
    CandidatePath path = exactPath(inputs, entryId, boundary);
    if (!path.missingRoles().isEmpty()) {
      notApplicable.add(
          new FactCandidateSet.NotApplicableDisposition(
              entryId,
              boundary.nodeId(),
              template.candidateFactKey(),
              path.missingRoles(),
              NOT_APPLICABLE_REASON));
      return;
    }
    FactCandidateInputs.BoundaryInvocation invocation = boundary.boundaryInvocation();
    candidates.add(
        new FactCandidateSet.FactCandidate(
            template.candidateFactKey(),
            entryId,
            template.kind(),
            boundary.nodeId(),
            invocation.invocationCallId(),
            invocation.callTargetEdgeId(),
            invocation.staticTargetType(),
            invocation.staticTargetMethod(),
            invocation.staticTargetSignature(),
            path.orderedArgumentEdgeIds(),
            path.argumentBindings(),
            invocation.controlContext().basicBlockNodeId(),
            invocation.controlContext().guardNodeId(),
            path.evidenceBySubject(),
            template.requiredAtoms().stream()
                .map(
                    atom ->
                        new FactCandidateSet.RequiredAtom(
                            atom.atomKey(),
                            atom.role(),
                            atom.valueType(),
                            atom.expectedEvidenceKinds()))
                .toList()));
  }

  private static void enumerateGuardCandidates(
      FactCandidateInputs inputs,
      FactRegistry.FactTemplate template,
      List<FactCandidateSet.FactCandidate> candidates,
      List<FactCandidateSet.NotApplicableDisposition> notApplicable) {
    inputs.graph(ProgramGraphKind.CONTROL_FLOW).nodesById().values().stream()
        .filter(node -> GUARD.equals(node.kind()))
        .sorted(Comparator.comparing(FactCandidateInputs.PublicProgramNode::nodeId))
        .forEach(
            guard ->
                guard.owningEntryIds().stream()
                    .filter(inputs.entryIds()::contains)
                    .sorted()
                    .forEach(
                        entryId ->
                            enumerateGuardTemplate(
                                inputs, template, entryId, guard, candidates, notApplicable)));
  }

  private static void enumerateGuardTemplate(
      FactCandidateInputs inputs,
      FactRegistry.FactTemplate template,
      String entryId,
      FactCandidateInputs.PublicProgramNode guard,
      List<FactCandidateSet.FactCandidate> candidates,
      List<FactCandidateSet.NotApplicableDisposition> notApplicable) {
    GuardPath path = exactGuardPath(inputs, entryId, guard);
    if (!path.missingRoles().isEmpty()) {
      notApplicable.add(
          new FactCandidateSet.NotApplicableDisposition(
              entryId,
              guard.nodeId(),
              template.candidateFactKey(),
              path.missingRoles(),
              "GUARD_CONDITION_UNPROVEN"));
      return;
    }
    candidates.add(
        new FactCandidateSet.FactCandidate(
            template.candidateFactKey(),
            entryId,
            template.kind(),
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            null,
            path.evidenceBySubject(),
            template.requiredAtoms().stream()
                .map(
                    atom ->
                        new FactCandidateSet.RequiredAtom(
                            atom.atomKey(),
                            atom.role(),
                            atom.valueType(),
                            atom.expectedEvidenceKinds()))
                .toList(),
            guard.nodeId(),
            guard.normalizedCondition(),
            path.branchEdgeIds()));
  }

  private static void enumerateExactCallCandidates(
      FactCandidateInputs inputs,
      FactRegistry.FactTemplate template,
      List<FactCandidateSet.FactCandidate> candidates,
      List<FactCandidateSet.NotApplicableDisposition> notApplicable) {
    inputs.graph(ProgramGraphKind.CALL).nodesById().values().stream()
        .filter(node -> CALL_SITE.equals(node.kind()))
        .sorted(Comparator.comparing(FactCandidateInputs.PublicProgramNode::nodeId))
        .forEach(
            callSite ->
                callSite.owningEntryIds().stream()
                    .filter(inputs.entryIds()::contains)
                    .sorted()
                    .forEach(
                        entryId ->
                            enumerateExactCallTemplate(
                                inputs, template, entryId, callSite, candidates, notApplicable)));
  }

  private static void enumerateExactCallTemplate(
      FactCandidateInputs inputs,
      FactRegistry.FactTemplate template,
      String entryId,
      FactCandidateInputs.PublicProgramNode callSite,
      List<FactCandidateSet.FactCandidate> candidates,
      List<FactCandidateSet.NotApplicableDisposition> notApplicable) {
    FactCandidateInputs.PublicProgramGraph calls = inputs.graph(ProgramGraphKind.CALL);
    List<FactCandidateInputs.PublicProgramEdge> exactTargets =
        calls.edgesById().values().stream()
            .filter(edge -> CALL_TARGET.equals(edge.kind()))
            .filter(edge -> EXACT.equals(edge.resolution()))
            .filter(edge -> STATIC_FIELD_RECEIVER_CALL_RULE.equals(edge.ruleId()))
            .filter(edge -> callSite.nodeId().equals(edge.fromNodeId()))
            .sorted(Comparator.comparing(FactCandidateInputs.PublicProgramEdge::edgeId))
            .toList();
    if (exactTargets.size() != 1) return;

    FactCandidateInputs.PublicProgramEdge callTarget = exactTargets.get(0);
    FactCandidateInputs.PublicProgramNode targetMethod =
        inputs.graph(ProgramGraphKind.CODE_STRUCTURE).nodesById().get(callTarget.toNodeId());
    if (targetMethod == null || !METHOD.equals(targetMethod.kind())) {
      notApplicable.add(
          new FactCandidateSet.NotApplicableDisposition(
              entryId,
              callTarget.edgeId(),
              template.candidateFactKey(),
              List.of("TARGET_METHOD"),
              EXACT_CALL_TARGET_NOT_METHOD));
      return;
    }
    if (!isExactTargetCanonical(targetMethod.canonicalValue())) {
      notApplicable.add(
          new FactCandidateSet.NotApplicableDisposition(
              entryId,
              callTarget.edgeId(),
              template.candidateFactKey(),
              List.of(TARGET_METHOD_CANONICAL),
              REQUIRED_ATOM_MISSING));
      return;
    }

    ExactCallPath path = exactCallPath(inputs, callSite, callTarget, targetMethod);
    if (!path.missingRoles().isEmpty()) {
      notApplicable.add(
          new FactCandidateSet.NotApplicableDisposition(
              entryId,
              callTarget.edgeId(),
              template.candidateFactKey(),
              path.missingRoles(),
              PROOF_NOT_CLOSED));
      return;
    }
    candidates.add(
        new FactCandidateSet.FactCandidate(
            template.candidateFactKey(),
            entryId,
            template.kind(),
            callSite.nodeId(),
            callTarget.edgeId(),
            targetMethod.nodeId(),
            targetMethod.canonicalValue(),
            path.evidenceBySubject(),
            template.requiredAtoms().stream()
                .map(
                    atom ->
                        new FactCandidateSet.RequiredAtom(
                            atom.atomKey(),
                            atom.role(),
                            atom.valueType(),
                            atom.expectedEvidenceKinds()))
                .toList()));
  }

  private static boolean isExactTargetCanonical(String canonicalMethod) {
    int hash = canonicalMethod.indexOf('#');
    int parameterStart = canonicalMethod.indexOf('(', hash + 1);
    return hash > 0 && parameterStart > hash + 1 && canonicalMethod.endsWith(")");
  }

  private static ExactCallPath exactCallPath(
      FactCandidateInputs inputs,
      FactCandidateInputs.PublicProgramNode callSite,
      FactCandidateInputs.PublicProgramEdge callTarget,
      FactCandidateInputs.PublicProgramNode targetMethod) {
    List<ExactCallEvidenceRequest> requests =
        List.of(
            new ExactCallEvidenceRequest(
                new SubjectRequest(
                    ProgramGraphKind.CALL,
                    FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
                    callSite.nodeId(),
                    callSite.sourceEvidenceNodeIds()),
                CALL_SITE_EVIDENCE),
            new ExactCallEvidenceRequest(
                new SubjectRequest(
                    ProgramGraphKind.CALL,
                    FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_EDGE,
                    callTarget.edgeId(),
                    callTarget.sourceEvidenceNodeIds()),
                CALL_TARGET_EDGE_EVIDENCE),
            new ExactCallEvidenceRequest(
                new SubjectRequest(
                    ProgramGraphKind.CODE_STRUCTURE,
                    FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
                    targetMethod.nodeId(),
                    targetMethod.sourceEvidenceNodeIds()),
                TARGET_METHOD_EVIDENCE));
    List<String> missingRoles = new ArrayList<>();
    List<FactCandidateSet.SubjectEvidenceBinding> evidence = new ArrayList<>();
    for (ExactCallEvidenceRequest request : requests) {
      SubjectRequest subject = request.subject();
      FactCandidateInputs.SubjectEvidence closure =
          inputs
              .evidenceGraph()
              .closureFor(
                  subject.graphKind(),
                  subject.supportKind(),
                  subject.subjectElementId(),
                  subject.sourceEvidenceNodeIds());
      if (closure == null) {
        missingRoles.add(request.missingRole());
      } else {
        evidence.add(
            new FactCandidateSet.SubjectEvidenceBinding(
                closure.subjectElementId(),
                closure.sourceEvidenceNodeIds(),
                closure.ruleApplicationEvidenceNodeIds()));
      }
    }
    return new ExactCallPath(missingRoles, evidence);
  }

  private static GuardPath exactGuardPath(
      FactCandidateInputs inputs, String entryId, FactCandidateInputs.PublicProgramNode guard) {
    FactCandidateInputs.PublicProgramGraph control = inputs.graph(ProgramGraphKind.CONTROL_FLOW);
    List<String> missing = new ArrayList<>();
    List<FactCandidateInputs.PublicProgramEdge> branches =
        control.edgesById().values().stream()
            .filter(edge -> guard.nodeId().equals(edge.guardNodeId()))
            .filter(edge -> guard.nodeId().equals(edge.fromNodeId()))
            .filter(edge -> "TRUE".equals(edge.kind()) || "FALSE".equals(edge.kind()))
            .filter(edge -> edge.kind().equals(edge.polarity()))
            .filter(
                edge -> {
                  FactCandidateInputs.PublicProgramNode target =
                      uniqueProgramNode(inputs, edge.toNodeId());
                  return target != null && target.owningEntryIds().contains(entryId);
                })
            .sorted(Comparator.comparing(FactCandidateInputs.PublicProgramEdge::edgeId))
            .toList();
    if (branches.size() != 2
        || branches.stream().map(FactCandidateInputs.PublicProgramEdge::kind).distinct().count()
            != 2) {
      missing.add("GUARD_BRANCHES");
    }
    FactCandidateInputs.SubjectEvidence closure =
        inputs
            .evidenceGraph()
            .closureFor(
                ProgramGraphKind.CONTROL_FLOW,
                FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
                guard.nodeId(),
                guard.sourceEvidenceNodeIds());
    if (closure == null || !hasGuardRulePair(inputs, guard.nodeId(), closure)) {
      missing.add("GUARD_EVIDENCE");
    }
    if (!missing.isEmpty()) return new GuardPath(missing, List.of(), List.of());
    return new GuardPath(
        List.of(),
        branches.stream().map(FactCandidateInputs.PublicProgramEdge::edgeId).toList(),
        List.of(
            new FactCandidateSet.SubjectEvidenceBinding(
                closure.subjectElementId(),
                closure.sourceEvidenceNodeIds(),
                closure.ruleApplicationEvidenceNodeIds())));
  }

  private static FactCandidateInputs.PublicProgramNode uniqueProgramNode(
      FactCandidateInputs inputs, String nodeId) {
    List<FactCandidateInputs.PublicProgramNode> matches =
        inputs.programGraphs().values().stream()
            .map(graph -> graph.nodesById().get(nodeId))
            .filter(Objects::nonNull)
            .toList();
    return matches.size() == 1 ? matches.get(0) : null;
  }

  private static boolean hasGuardRulePair(
      FactCandidateInputs inputs, String guardNodeId, FactCandidateInputs.SubjectEvidence closure) {
    return inputs.evidenceGraph().edges().stream()
        .filter(edge -> guardNodeId.equals(edge.subjectProgramElementId()))
        .filter(edge -> closure.sourceEvidenceNodeIds().contains(edge.sourceEvidenceNodeId()))
        .filter(
            edge ->
                closure
                    .ruleApplicationEvidenceNodeIds()
                    .contains(edge.ruleApplicationEvidenceNodeId()))
        .map(edge -> inputs.evidenceGraph().nodesById().get(edge.ruleApplicationEvidenceNodeId()))
        .filter(Objects::nonNull)
        .map(FactCandidateInputs.EvidenceNode::ruleApplication)
        .filter(Objects::nonNull)
        .anyMatch(
            rule ->
                "control-flow-if-guard-v1".equals(rule.ruleId())
                    && "v1".equals(rule.ruleVersion())
                    && rule.inputProgramElementIds().contains(guardNodeId));
  }

  private static CandidatePath exactPath(
      FactCandidateInputs inputs, String entryId, FactCandidateInputs.PublicProgramNode boundary) {
    List<String> missing = new ArrayList<>();
    FactCandidateInputs.BoundaryInvocation invocation = boundary.boundaryInvocation();
    if (invocation == null) {
      return CandidatePath.missing("BOUNDARY_INVOCATION_VARIANT");
    }

    FactCandidateInputs.PublicProgramGraph calls = inputs.graph(ProgramGraphKind.CALL);
    FactCandidateInputs.PublicProgramGraph control = inputs.graph(ProgramGraphKind.CONTROL_FLOW);
    FactCandidateInputs.PublicProgramGraph data = inputs.graph(ProgramGraphKind.DATA_FLOW);

    FactCandidateInputs.PublicProgramNode callSite =
        calls.nodesById().get(invocation.invocationCallId());
    if (callSite == null
        || !CALL_SITE.equals(callSite.kind())
        || !callSite.owningEntryIds().contains(entryId)) {
      missing.add("CALL_SITE");
    }
    FactCandidateInputs.PublicProgramEdge callTarget =
        calls.edgesById().get(invocation.callTargetEdgeId());
    if (callTarget == null
        || !CALL_TARGET.equals(callTarget.kind())
        || !EXACT.equals(callTarget.resolution())
        || !invocation.invocationCallId().equals(callTarget.fromNodeId())) {
      missing.add("CALL_TARGET");
    }

    List<FactCandidateSet.BoundaryArgumentBinding> bindings = new ArrayList<>();
    List<String> argumentEdgeIds = new ArrayList<>();
    for (FactCandidateInputs.BoundaryArgument argument : invocation.orderedArguments()) {
      List<FactCandidateInputs.PublicProgramEdge> matches =
          data.edgesById().values().stream()
              .filter(edge -> DATA_ARGUMENT_EDGE.equals(edge.kind()))
              .filter(edge -> EXACT.equals(edge.resolution()))
              .filter(edge -> boundary.nodeId().equals(edge.toNodeId()))
              .filter(edge -> argument.argumentNodeId().equals(edge.fromNodeId()))
              .toList();
      if (matches.size() != 1) {
        missing.add("ARGUMENT_TO_BOUNDARY:" + argument.ordinal());
      } else {
        FactCandidateInputs.PublicProgramEdge edge = matches.get(0);
        argumentEdgeIds.add(edge.edgeId());
        bindings.add(
            new FactCandidateSet.BoundaryArgumentBinding(
                argument.ordinal(),
                argument.argumentNodeId(),
                edge.edgeId(),
                argument.javaLocalOriginNodeIds()));
      }
    }

    FactCandidateInputs.PublicProgramNode block =
        control.nodesById().get(invocation.controlContext().basicBlockNodeId());
    if (block == null
        || !BASIC_BLOCK.equals(block.kind())
        || !block.owningEntryIds().contains(entryId)) {
      missing.add("CONTROL_BASIC_BLOCK");
    }
    if (invocation.controlContext().guardNodeId() != null) {
      FactCandidateInputs.PublicProgramNode guard =
          control.nodesById().get(invocation.controlContext().guardNodeId());
      if (guard == null
          || !GUARD.equals(guard.kind())
          || !guard.owningEntryIds().contains(entryId)) {
        missing.add("CONTROL_GUARD");
      }
    }

    List<SubjectRequest> evidenceRequests = new ArrayList<>();
    if (callSite != null) {
      evidenceRequests.add(
          new SubjectRequest(
              ProgramGraphKind.CALL,
              FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
              callSite.nodeId(),
              callSite.sourceEvidenceNodeIds()));
    }
    evidenceRequests.add(
        new SubjectRequest(
            ProgramGraphKind.DATA_FLOW,
            FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
            boundary.nodeId(),
            boundary.sourceEvidenceNodeIds()));
    if (callTarget != null) {
      evidenceRequests.add(
          new SubjectRequest(
              ProgramGraphKind.CALL,
              FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_EDGE,
              callTarget.edgeId(),
              callTarget.sourceEvidenceNodeIds()));
    }
    for (FactCandidateInputs.PublicProgramEdge argumentEdge :
        bindings.stream().map(binding -> data.edgesById().get(binding.argumentEdgeId())).toList()) {
      evidenceRequests.add(
          new SubjectRequest(
              ProgramGraphKind.DATA_FLOW,
              FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_EDGE,
              argumentEdge.edgeId(),
              argumentEdge.sourceEvidenceNodeIds()));
    }
    for (FactCandidateSet.BoundaryArgumentBinding binding : bindings) {
      for (String localOriginNodeId : binding.javaLocalOriginNodeIds()) {
        SubjectRequest localOrigin = programNodeEvidenceRequest(inputs, localOriginNodeId);
        if (localOrigin == null) {
          missing.add("JAVA_LOCAL_ORIGIN:" + localOriginNodeId);
        } else {
          evidenceRequests.add(localOrigin);
        }
      }
    }
    if (block != null) {
      evidenceRequests.add(
          new SubjectRequest(
              ProgramGraphKind.CONTROL_FLOW,
              FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
              block.nodeId(),
              block.sourceEvidenceNodeIds()));
    }
    if (invocation.controlContext().guardNodeId() != null) {
      FactCandidateInputs.PublicProgramNode guard =
          control.nodesById().get(invocation.controlContext().guardNodeId());
      if (guard != null) {
        evidenceRequests.add(
            new SubjectRequest(
                ProgramGraphKind.CONTROL_FLOW,
                FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
                guard.nodeId(),
                guard.sourceEvidenceNodeIds()));
      }
    }

    List<FactCandidateSet.SubjectEvidenceBinding> evidence = new ArrayList<>();
    Set<String> evidenceSubjects = new HashSet<>();
    for (SubjectRequest request : evidenceRequests) {
      if (!evidenceSubjects.add(request.subjectElementId())) continue;
      FactCandidateInputs.SubjectEvidence closure =
          inputs
              .evidenceGraph()
              .closureFor(
                  request.graphKind(),
                  request.supportKind(),
                  request.subjectElementId(),
                  request.sourceEvidenceNodeIds());
      if (closure == null) {
        missing.add("EVIDENCE:" + request.subjectElementId());
      } else {
        evidence.add(
            new FactCandidateSet.SubjectEvidenceBinding(
                closure.subjectElementId(),
                closure.sourceEvidenceNodeIds(),
                closure.ruleApplicationEvidenceNodeIds()));
      }
    }
    if (!missing.isEmpty()) return new CandidatePath(missing, List.of(), List.of(), List.of());
    return new CandidatePath(List.of(), argumentEdgeIds, bindings, evidence);
  }

  private static SubjectRequest programNodeEvidenceRequest(
      FactCandidateInputs inputs, String nodeId) {
    SubjectRequest result = null;
    for (ProgramGraphKind graphKind :
        List.of(
            ProgramGraphKind.CODE_STRUCTURE,
            ProgramGraphKind.CALL,
            ProgramGraphKind.CONTROL_FLOW,
            ProgramGraphKind.DATA_FLOW)) {
      FactCandidateInputs.PublicProgramNode node = inputs.graph(graphKind).nodesById().get(nodeId);
      if (node == null) continue;
      if (result != null) return null;
      result =
          new SubjectRequest(
              graphKind,
              FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
              node.nodeId(),
              node.sourceEvidenceNodeIds());
    }
    return result;
  }

  private record SubjectRequest(
      ProgramGraphKind graphKind,
      FactCandidateInputs.EvidenceSupportKind supportKind,
      String subjectElementId,
      List<String> sourceEvidenceNodeIds) {}

  private record ExactCallEvidenceRequest(SubjectRequest subject, String missingRole) {}

  private record ExactCallPath(
      List<String> missingRoles, List<FactCandidateSet.SubjectEvidenceBinding> evidenceBySubject) {

    private ExactCallPath {
      missingRoles = List.copyOf(missingRoles);
      evidenceBySubject = List.copyOf(evidenceBySubject);
    }
  }

  private record CandidatePath(
      List<String> missingRoles,
      List<String> orderedArgumentEdgeIds,
      List<FactCandidateSet.BoundaryArgumentBinding> argumentBindings,
      List<FactCandidateSet.SubjectEvidenceBinding> evidenceBySubject) {

    private CandidatePath {
      missingRoles = List.copyOf(missingRoles);
      orderedArgumentEdgeIds = List.copyOf(orderedArgumentEdgeIds);
      argumentBindings = List.copyOf(argumentBindings);
      evidenceBySubject = List.copyOf(evidenceBySubject);
    }

    private static CandidatePath missing(String role) {
      return new CandidatePath(List.of(role), List.of(), List.of(), List.of());
    }
  }

  private record GuardPath(
      List<String> missingRoles,
      List<String> branchEdgeIds,
      List<FactCandidateSet.SubjectEvidenceBinding> evidenceBySubject) {

    private GuardPath {
      missingRoles = List.copyOf(missingRoles);
      branchEdgeIds = List.copyOf(branchEdgeIds);
      evidenceBySubject = List.copyOf(evidenceBySubject);
    }
  }
}
