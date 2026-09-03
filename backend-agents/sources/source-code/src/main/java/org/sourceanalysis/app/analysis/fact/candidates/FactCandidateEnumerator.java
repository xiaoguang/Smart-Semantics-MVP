package org.sourceanalysis.app.analysis.fact.candidates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.graph.ProgramGraphKind;

/**
 * Enumerates only exact frozen-Java boundary invocation candidates from persisted public graphs.
 *
 * <p>This module does not choose a Proof, admit a Fact, inspect source text, parse canonical
 * values, or infer any external system behavior.
 */
public final class FactCandidateEnumerator {

  private static final String JAVA_BOUNDARY_KIND = "JAVA_BOUNDARY_INVOCATION";
  private static final String DATA_ARGUMENT_EDGE = "ARGUMENT_TO_BOUNDARY";
  private static final String CALL_SITE = "CALL_SITE";
  private static final String CALL_TARGET = "CALL_TARGET";
  private static final String BASIC_BLOCK = "BASIC_BLOCK";
  private static final String GUARD = "GUARD";
  private static final String EXACT = "EXACT";
  private static final String NOT_APPLICABLE_REASON = "DATA_FLOW_BINDING_UNPROVEN";

  /** Enumerates the complete applicable/not-applicable denominator governed by the supplied registry. */
  public FactCandidateSet enumerate(FactCandidateInputs inputs, FactRegistry registry) {
    Objects.requireNonNull(inputs, "fact candidate inputs");
    Objects.requireNonNull(registry, "fact registry");

    FactCandidateInputs.PublicProgramGraph data = inputs.graph(ProgramGraphKind.DATA_FLOW);
    List<FactCandidateSet.FactCandidate> candidates = new ArrayList<>();
    List<FactCandidateSet.NotApplicableDisposition> notApplicable = new ArrayList<>();
    data.nodesById().values().stream()
        .filter(node -> JAVA_BOUNDARY_KIND.equals(node.kind()))
        .sorted(Comparator.comparing(FactCandidateInputs.PublicProgramNode::nodeId))
        .forEach(
            boundary ->
                boundary.owningEntryIds().stream()
                    .filter(inputs.entryIds()::contains)
                    .sorted()
                    .forEach(
                        entryId ->
                            registry.templates().forEach(
                                template ->
                                    enumerateTemplate(
                                        inputs,
                                        template,
                                        entryId,
                                        boundary,
                                        candidates,
                                        notApplicable))));
    return FactCandidateSet.create(inputs.sourceGraphRoots(), candidates, notApplicable);
  }

  private static void enumerateTemplate(
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

  private static CandidatePath exactPath(
      FactCandidateInputs inputs,
      String entryId,
      FactCandidateInputs.PublicProgramNode boundary) {
    List<String> missing = new ArrayList<>();
    FactCandidateInputs.BoundaryInvocation invocation = boundary.boundaryInvocation();
    if (invocation == null) {
      return CandidatePath.missing("BOUNDARY_INVOCATION_VARIANT");
    }

    FactCandidateInputs.PublicProgramGraph calls = inputs.graph(ProgramGraphKind.CALL);
    FactCandidateInputs.PublicProgramGraph control = inputs.graph(ProgramGraphKind.CONTROL_FLOW);
    FactCandidateInputs.PublicProgramGraph data = inputs.graph(ProgramGraphKind.DATA_FLOW);

    FactCandidateInputs.PublicProgramNode callSite = calls.nodesById().get(invocation.invocationCallId());
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
      if (guard == null || !GUARD.equals(guard.kind()) || !guard.owningEntryIds().contains(entryId)) {
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
        bindings.stream()
            .map(binding -> data.edgesById().get(binding.argumentEdgeId()))
            .toList()) {
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

  private static SubjectRequest programNodeEvidenceRequest(FactCandidateInputs inputs, String nodeId) {
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
}
