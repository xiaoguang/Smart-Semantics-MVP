package com.linguan.codemd.stage03;

import com.linguan.codemd.stage01.CodeFact;
import com.linguan.codemd.stage01.ExpectationGap;
import com.linguan.codemd.stage01.FactAtom;
import com.linguan.codemd.stage01.Proof;
import com.linguan.codemd.stage01.ProofEdge;
import com.linguan.codemd.stage01.ProofLocator;
import com.linguan.codemd.stage01.ProofNode;
import com.linguan.codemd.stage01.ProofPack;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage01.VerifiedFile;
import com.linguan.codemd.stage02.AllowedAtomView;
import com.linguan.codemd.stage02.AllowedFactView;
import com.linguan.codemd.stage02.AllowedGapView;
import com.linguan.codemd.stage02.CapsuleBudgetUsage;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage02.FlowGap;
import com.linguan.codemd.stage02.FlowSlice;
import com.linguan.codemd.stage02.FlowStep;
import com.linguan.codemd.stage02.ModelEvidenceSpan;
import com.linguan.codemd.stage02.OutcomePath;
import com.linguan.codemd.stage02.ProjectionObligation;
import com.linguan.codemd.stage02.Stage02Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/** Reopens the public M1--M4 evidence chain before an M5 provider task may be created. */
final class Stage03CapsuleClosureValidator {
    private static final String EVIDENCE_SCHEMA = "evidence-capsule-v1";
    private static final String EXPECTATION_GAP_REASON = "EXPECTATION_GAP_IN_FLOW_SCOPE";

    private Stage03CapsuleClosureValidator() {
    }

    static void validate(Stage01Request stage01Request, Stage01Result stage01, Stage02Result stage02,
                         FlowSlice flow, EvidenceCapsule capsule) {
        try {
            require(stage01Request != null && stage01 != null && stage02 != null && flow != null && capsule != null);
            require(stage01Request.frozenRepositoryRequest() != null
                    && stage01Request.frozenRepositoryRequest().snapshotRoot() != null);
            require("stage01-result-v1".equals(stage01.schemaVersion())
                    && "stage02-result-v1".equals(stage02.schemaVersion())
                    && !blank(stage01.stage01ResultId())
                    && stage01.stage01ResultId().equals(stage02.stage01ResultId()));

            FrozenSources sources = FrozenSources.of(stage01Request, stage01);
            ProofIndex proofs = ProofIndex.of(stage01, sources);
            Stage02Index stage02Index = Stage02Index.of(stage02);
            stage02Index.requireCurrent(flow, capsule);
            validateFlow(flow, stage01, stage02, stage02Index, proofs);
            validateCapsule(stage01, stage02, flow, capsule, proofs, sources);
        } catch (Stage03Exception ignored) {
            throw broken();
        } catch (IOException | RuntimeException ignored) {
            throw broken();
        }
    }

    private static void validateFlow(FlowSlice flow, Stage01Result stage01, Stage02Result stage02,
                                     Stage02Index stage02Index, ProofIndex proofs) {
        requireText(flow.flowSliceId());
        requireText(flow.entryId());
        require(flow.trigger() != null);
        requireText(flow.rootNodeId());
        require(flow.parentFlowSliceId() == null || stage02Index.flows().containsKey(flow.parentFlowSliceId()));
        Set<String> children = ids(flow.childFlowSliceIds());
        for (String childId : children) {
            FlowSlice child = stage02Index.flows().get(childId);
            require(child != null && flow.flowSliceId().equals(child.parentFlowSliceId()));
        }

        Set<String> factIds = ids(flow.factIds());
        Set<String> atomIds = ids(flow.atomIds());
        require(!factIds.isEmpty() && !atomIds.isEmpty());
        for (String factId : factIds) {
            require(proofs.facts().containsKey(factId));
        }
        for (String atomId : atomIds) {
            FactAtom atom = proofs.atoms().get(atomId);
            require(atom != null && factIds.contains(proofs.atomFactIds().get(atomId)));
        }

        Set<String> stepIds = new HashSet<>();
        for (FlowStep step : requireValues(flow.sharedSteps())) {
            requireText(step.flowStepId());
            require(stepIds.add(step.flowStepId()));
            require(ids(step.factIds()).stream().allMatch(factIds::contains));
            require(ids(step.atomIds()).stream().allMatch(atomIds::contains));
        }

        Map<String, OutcomePath> outcomes = unique(requireValues(flow.outcomePaths()), OutcomePath::outcomePathId);
        require(!outcomes.isEmpty());
        for (OutcomePath outcome : outcomes.values()) {
            validateOutcome(outcome, factIds, atomIds, proofs);
        }

        Map<String, ExpectationGap> expectations = unique(requireValues(stage01.provenSourceFacts().gapLedger()
                        .expectationGaps()),
                ExpectationGap::gapId);
        Map<String, FlowGap> flowGaps = unique(requireValues(stage02.flowGaps()), FlowGap::flowGapId);
        for (String gapId : ids(flow.gapIds())) {
            if (expectations.containsKey(gapId)) {
                continue;
            }
            FlowGap gap = flowGaps.get(gapId);
            require(gap != null && flow.entryId().equals(gap.entryId()) && !blank(gap.code()));
        }
    }

    private static void validateOutcome(OutcomePath outcome, Set<String> factIds, Set<String> atomIds,
                                        ProofIndex proofs) {
        requireText(outcome.outcomePathId());
        requireText(outcome.terminalNodeId());
        require("RETURN".equals(outcome.terminalKind()) || "THROW".equals(outcome.terminalKind()));
        require(ids(outcome.terminalFactIds()).stream().allMatch(factIds::contains));
        Set<String> requiredAtoms = ids(outcome.requiredAtomIds());
        require(!requiredAtoms.isEmpty() && atomIds.containsAll(requiredAtoms));
        Set<String> requiredProofs = ids(outcome.requiredProofIds());
        Set<String> expectedProofs = new TreeSet<>();
        for (String atomId : requiredAtoms) {
            FactAtom atom = proofs.atoms().get(atomId);
            require(atom != null);
            expectedProofs.add(atom.proofId());
        }
        require(expectedProofs.equals(requiredProofs));
        for (String proofId : requiredProofs) {
            Proof proof = proofs.proofs().get(proofId);
            require(proof != null && requiredAtoms.contains(proof.atomId())
                    && factIds.contains(proof.factId()));
        }
        for (com.linguan.codemd.stage02.BranchDecision decision : requireValues(outcome.decisions())) {
            requireText(decision.guardNodeId());
            require(atomIds.contains(decision.conditionAtomId()));
            require("TRUE".equals(decision.polarity()) || "FALSE".equals(decision.polarity()));
            requireText(decision.normalizedCondition());
        }
    }

    private static void validateCapsule(Stage01Result stage01, Stage02Result stage02, FlowSlice flow,
                                        EvidenceCapsule capsule, ProofIndex proofs, FrozenSources sources)
            throws IOException {
        require(EVIDENCE_SCHEMA.equals(capsule.schemaVersion()));
        requireText(capsule.evidenceCapsuleId());
        require(flow.flowSliceId().equals(capsule.flowSliceId()));
        require(stage02.evidenceProjectionProfileRef() != null
                && stage02.evidenceProjectionProfileRef().equals(capsule.projectionProfileRef()));
        require(proofs.proofPackId().equals(capsule.proofPackId()));

        Set<String> flowFacts = ids(flow.factIds());
        Set<String> flowAtoms = ids(flow.atomIds());
        Map<String, AllowedFactView> allowedFacts = unique(requireValues(capsule.allowedFacts()),
                AllowedFactView::factId);
        require(flowFacts.equals(allowedFacts.keySet()));
        Set<String> capsuleAtoms = new TreeSet<>();
        for (AllowedFactView fact : allowedFacts.values()) {
            CodeFact sourceFact = proofs.facts().get(fact.factId());
            require(sourceFact != null && Objects.equals(sourceFact.kind(), fact.kind()));
            Map<String, FactAtom> sourceAtoms = new TreeMapView<>(sourceFact.atoms(), FactAtom::atomId).values();
            Map<String, AllowedAtomView> allowedAtoms = unique(requireValues(fact.atoms()), AllowedAtomView::atomId);
            require(sourceAtoms.keySet().equals(allowedAtoms.keySet()));
            for (AllowedAtomView atom : allowedAtoms.values()) {
                FactAtom sourceAtom = sourceAtoms.get(atom.atomId());
                require(sourceAtom != null && exactAtom(sourceAtom, atom));
                Proof proof = proofs.proofs().get(atom.proofId());
                require(proof != null && fact.factId().equals(proof.factId()) && atom.atomId().equals(proof.atomId()));
                require(capsuleAtoms.add(atom.atomId()));
            }
        }
        require(flowAtoms.equals(capsuleAtoms));

        Set<String> outcomeIds = ids(flow.outcomePaths().stream().map(OutcomePath::outcomePathId).toList());
        require(outcomeIds.equals(ids(capsule.outcomePathIds())));
        validateGaps(stage01, stage02, flow, capsule);

        Map<String, ModelEvidenceSpan> spans = unique(requireValues(capsule.modelEvidenceSpans()),
                ModelEvidenceSpan::modelEvidenceSpanId);
        require(!spans.isEmpty());
        int byteCount = 0;
        for (ModelEvidenceSpan span : spans.values()) {
            byteCount += validateSpan(stage01, capsule, span, flowAtoms, outcomeIds, sources);
        }
        validateNonOverlapping(spans.values());
        validateObligations(stage01, capsule, spans, flowAtoms, outcomeIds);
        require(capsule.budgetUsage() != null && capsule.budgetUsage().equals(new CapsuleBudgetUsage(spans.size(),
                byteCount)));
        String expectedCapsuleId = "evidence-capsule:" + sha256("evidence-capsule-v1\n" + flow.flowSliceId()
                + "\n" + proofs.proofPackId() + "\n" + capsule.projectionProfileRef() + "\n"
                + sorted(flow.outcomePaths().stream().map(OutcomePath::outcomePathId).toList()) + "\n"
                + canonical(capsule.allowedFacts()) + "\n" + canonical(capsule.allowedGaps()) + "\n"
                + canonical(capsule.modelEvidenceSpans()) + "\n" + canonical(capsule.projectionObligations())
                + "\n" + capsule.budgetUsage());
        require(expectedCapsuleId.equals(capsule.evidenceCapsuleId()));
    }

    private static boolean exactAtom(FactAtom source, AllowedAtomView allowed) {
        return Objects.equals(source.atomId(), allowed.atomId())
                && Objects.equals(source.role(), allowed.role())
                && Objects.equals(source.name(), allowed.name())
                && Objects.equals(source.value(), allowed.value())
                && Objects.equals(source.proofId(), allowed.proofId());
    }

    private static void validateGaps(Stage01Result stage01, Stage02Result stage02, FlowSlice flow,
                                     EvidenceCapsule capsule) {
        Map<String, ExpectationGap> expectations = unique(requireValues(stage01.provenSourceFacts().gapLedger()
                .expectationGaps()), ExpectationGap::gapId);
        Set<String> flowGapIds = ids(flow.gapIds());
        Set<String> capsuleGapIds = new TreeSet<>();
        for (AllowedGapView gap : requireValues(capsule.allowedGaps())) {
            requireText(gap.gapId());
            require(capsuleGapIds.add(gap.gapId()));
            ExpectationGap source = expectations.get(gap.gapId());
            require(source != null && flowGapIds.contains(gap.gapId())
                    && Objects.equals(source.expectationId(), gap.code())
                    && EXPECTATION_GAP_REASON.equals(gap.reasonCode()));
        }
        Map<String, FlowGap> flowGaps = unique(requireValues(stage02.flowGaps()), FlowGap::flowGapId);
        for (String gapId : flowGapIds) {
            if (expectations.containsKey(gapId)) {
                continue;
            }
            FlowGap gap = flowGaps.get(gapId);
            require(gap != null && flow.entryId().equals(gap.entryId()));
        }
    }

    private static int validateSpan(Stage01Result stage01, EvidenceCapsule capsule, ModelEvidenceSpan span,
                                    Set<String> localAtoms, Set<String> localOutcomes, FrozenSources sources)
            throws IOException {
        requireText(span.modelEvidenceSpanId());
        require(span.locator() != null);
        byte[] bytes = sources.bytes(span.locator());
        validateLocator(span.locator(), bytes);
        require(sources.file(span.locator().path()).sha256().equals(span.sourceFileSha256()));
        String excerpt = new String(Arrays.copyOfRange(bytes, span.locator().startByte(),
                span.locator().endByteExclusive()), StandardCharsets.UTF_8);
        require(excerpt.equals(span.excerpt()));
        String excerptHash = sha256(excerpt.getBytes(StandardCharsets.UTF_8));
        require(excerptHash.equals(span.excerptSha256()));
        String expectedSpanId = "model-evidence-span:" + sha256(stage01.verifiedSnapshot().snapshotId() + "\n"
                + span.locator() + "\n" + span.sourceFileSha256() + "\n" + span.excerptSha256() + "\n"
                + capsule.projectionProfileRef());
        require(expectedSpanId.equals(span.modelEvidenceSpanId()));
        Set<String> supportedAtoms = ids(span.supportedAtomIds());
        Set<String> supportedOutcomes = ids(span.supportedOutcomePathIds());
        require(!supportedAtoms.isEmpty() || !supportedOutcomes.isEmpty());
        require(localAtoms.containsAll(supportedAtoms) && localOutcomes.containsAll(supportedOutcomes));
        return span.excerpt().getBytes(StandardCharsets.UTF_8).length;
    }

    private static void validateNonOverlapping(Collection<ModelEvidenceSpan> spans) {
        List<ModelEvidenceSpan> ordered = spans.stream().sorted(Comparator.comparing((ModelEvidenceSpan span) ->
                span.locator().path()).thenComparingInt(span -> span.locator().startByte())).toList();
        for (int index = 1; index < ordered.size(); index++) {
            ModelEvidenceSpan previous = ordered.get(index - 1);
            ModelEvidenceSpan current = ordered.get(index);
            require(!previous.locator().path().equals(current.locator().path())
                    || previous.locator().endByteExclusive() <= current.locator().startByte());
        }
    }

    private static void validateObligations(Stage01Result stage01, EvidenceCapsule capsule,
                                            Map<String, ModelEvidenceSpan> spans, Set<String> atomIds,
                                            Set<String> outcomeIds) {
        Map<String, ProjectionObligation> obligations = unique(requireValues(capsule.projectionObligations()),
                ProjectionObligation::obligationId);
        Set<String> expectedSubjects = new TreeSet<>(atomIds);
        expectedSubjects.addAll(outcomeIds);
        Map<String, ProjectionObligation> bySubject = new HashMap<>();
        Set<String> referencedSpans = new TreeSet<>();
        for (ProjectionObligation obligation : obligations.values()) {
            requireText(obligation.kind());
            requireText(obligation.subjectId());
            require(bySubject.put(obligation.subjectId(), obligation) == null);
            Set<String> satisfying = ids(obligation.satisfyingSpanIds());
            require(!satisfying.isEmpty());
            Set<String> supported = new TreeSet<>();
            for (String spanId : satisfying) {
                ModelEvidenceSpan span = spans.get(spanId);
                require(span != null);
                referencedSpans.add(spanId);
                supported.addAll(span.supportedAtomIds());
                supported.addAll(span.supportedOutcomePathIds());
            }
            require(supported.contains(obligation.subjectId()));
            require((atomIds.contains(obligation.subjectId()) && "ATOM_DIRECT_SEMANTICS".equals(obligation.kind()))
                    || (outcomeIds.contains(obligation.subjectId()) && "OUTCOME_TERMINAL".equals(obligation.kind())));
            String expectedId = "projection-obligation:" + sha256(obligation.kind() + "\n"
                    + obligation.subjectId() + "\n" + sorted(satisfying));
            require(expectedId.equals(obligation.obligationId()));
        }
        require(expectedSubjects.equals(bySubject.keySet()));
        require(referencedSpans.equals(spans.keySet()));
    }

    private static void validateLocator(ProofLocator locator, byte[] bytes) {
        validateByteBounds(locator, bytes);
        String prefix = new String(bytes, 0, locator.startByte(), StandardCharsets.UTF_8);
        String content = new String(bytes, locator.startByte(), locator.endByteExclusive() - locator.startByte(),
                StandardCharsets.UTF_8);
        int startLine = 1 + (int) prefix.chars().filter(value -> value == '\n').count();
        int startColumn = prefix.length() - prefix.lastIndexOf('\n');
        int lineBreak = content.lastIndexOf('\n');
        int endLine = startLine + (int) content.chars().filter(value -> value == '\n').count();
        int endColumn = lineBreak >= 0 ? content.length() - lineBreak : startColumn + content.length();
        require(locator.startLine() == startLine && locator.startColumn() == startColumn
                && locator.endLine() == endLine && locator.endColumn() == endColumn);
    }

    private static void validateByteBounds(ProofLocator locator, byte[] bytes) {
        require(locator != null && bytes != null);
        requireText(locator.path());
        require(locator.startByte() >= 0 && locator.endByteExclusive() > locator.startByte()
                && locator.endByteExclusive() <= bytes.length);
    }

    private static final class Stage02Index {
        private final Map<String, FlowSlice> flows;
        private final Map<String, EvidenceCapsule> capsulesByFlow;

        private Stage02Index(Map<String, FlowSlice> flows, Map<String, EvidenceCapsule> capsulesByFlow) {
            this.flows = flows;
            this.capsulesByFlow = capsulesByFlow;
        }

        static Stage02Index of(Stage02Result stage02) {
            Map<String, FlowSlice> flows = unique(requireValues(stage02.flowSlices()), FlowSlice::flowSliceId);
            Map<String, EvidenceCapsule> capsulesByFlow = unique(requireValues(stage02.evidenceCapsules()),
                    EvidenceCapsule::flowSliceId);
            Set<String> evidenceIds = ids(stage02.evidenceCapsules().stream().map(EvidenceCapsule::evidenceCapsuleId)
                    .toList());
            require(evidenceIds.size() == capsulesByFlow.size() && flows.keySet().equals(capsulesByFlow.keySet()));
            return new Stage02Index(flows, capsulesByFlow);
        }

        Map<String, FlowSlice> flows() {
            return flows;
        }

        void requireCurrent(FlowSlice flow, EvidenceCapsule capsule) {
            require(flow.equals(flows.get(flow.flowSliceId())));
            require(capsule.equals(capsulesByFlow.get(flow.flowSliceId())));
        }
    }

    private static final class ProofIndex {
        private final String proofPackId;
        private final Map<String, CodeFact> facts;
        private final Map<String, FactAtom> atoms;
        private final Map<String, String> atomFactIds;
        private final Map<String, Proof> proofs;

        private ProofIndex(String proofPackId, Map<String, CodeFact> facts, Map<String, FactAtom> atoms,
                           Map<String, String> atomFactIds, Map<String, Proof> proofs) {
            this.proofPackId = proofPackId;
            this.facts = facts;
            this.atoms = atoms;
            this.atomFactIds = atomFactIds;
            this.proofs = proofs;
        }

        static ProofIndex of(Stage01Result stage01, FrozenSources sources) throws IOException {
            require(stage01.provenSourceFacts() != null && stage01.provenSourceFacts().provenFactSet() != null
                    && stage01.provenSourceFacts().proofPack() != null);
            ProofPack pack = stage01.provenSourceFacts().proofPack();
            requireText(pack.proofPackId());
            Map<String, CodeFact> facts = unique(requireValues(stage01.provenSourceFacts().provenFactSet()
                    .codeFacts()), CodeFact::factId);
            Map<String, FactAtom> atoms = new LinkedHashMap<>();
            Map<String, String> atomFactIds = new LinkedHashMap<>();
            for (CodeFact fact : facts.values()) {
                requireText(fact.kind());
                for (FactAtom atom : requireValues(fact.atoms())) {
                    requireText(atom.atomId());
                    requireText(atom.role());
                    requireText(atom.name());
                    require(atom.value() != null && !blank(atom.value().type()) && !blank(atom.value().canonical()));
                    require(pack.proofPackId().equals(atom.proofPackId()));
                    require(atoms.put(atom.atomId(), atom) == null);
                    atomFactIds.put(atom.atomId(), fact.factId());
                }
            }
            Map<String, ProofNode> nodes = unique(requireValues(pack.nodes()), ProofNode::proofNodeId);
            Map<String, ProofEdge> edges = unique(requireValues(pack.edges()), ProofEdge::proofEdgeId);
            Map<String, Proof> proofs = unique(requireValues(pack.proofs()), Proof::proofId);
            for (ProofEdge edge : edges.values()) {
                requireText(edge.repositoryEdgeId());
                requireText(edge.ruleId());
                require(nodes.containsKey(edge.fromProofNodeId()) && nodes.containsKey(edge.toProofNodeId()));
            }
            for (Proof proof : proofs.values()) {
                validateProof(proof, facts, atoms, nodes, edges, sources);
            }
            require(atoms.size() == proofs.size());
            for (FactAtom atom : atoms.values()) {
                Proof proof = proofs.get(atom.proofId());
                require(proof != null && atom.atomId().equals(proof.atomId())
                        && atomFactIds.get(atom.atomId()).equals(proof.factId()));
            }
            return new ProofIndex(pack.proofPackId(), Map.copyOf(facts), Map.copyOf(atoms),
                    Map.copyOf(atomFactIds), Map.copyOf(proofs));
        }

        private static void validateProof(Proof proof, Map<String, CodeFact> facts, Map<String, FactAtom> atoms,
                                          Map<String, ProofNode> nodes, Map<String, ProofEdge> edges,
                                          FrozenSources sources) throws IOException {
            requireText(proof.proofId());
            require("CLOSED".equals(proof.status()));
            FactAtom atom = atoms.get(proof.atomId());
            require(atom != null && facts.containsKey(proof.factId()) && proof.proofId().equals(atom.proofId()));
            Set<String> nodeIds = ids(proof.requiredProofNodeIds());
            require(nodeIds.contains(proof.rootProofNodeId()));
            for (String nodeId : nodeIds) {
                ProofNode node = nodes.get(nodeId);
                require(node != null && !blank(node.repositoryNodeId()) && node.locator() != null);
                sources.validateProofNode(node);
            }
            for (String edgeId : ids(proof.requiredProofEdgeIds())) {
                ProofEdge edge = edges.get(edgeId);
                require(edge != null && nodeIds.contains(edge.fromProofNodeId()) && nodeIds.contains(edge.toProofNodeId()));
            }
        }

        String proofPackId() {
            return proofPackId;
        }

        Map<String, CodeFact> facts() {
            return facts;
        }

        Map<String, FactAtom> atoms() {
            return atoms;
        }

        Map<String, String> atomFactIds() {
            return atomFactIds;
        }

        Map<String, Proof> proofs() {
            return proofs;
        }
    }

    private static final class FrozenSources {
        private final Path root;
        private final Map<String, VerifiedFile> files;
        private final Map<String, byte[]> bytes = new HashMap<>();

        private FrozenSources(Path root, Map<String, VerifiedFile> files) {
            this.root = root;
            this.files = files;
        }

        static FrozenSources of(Stage01Request request, Stage01Result result) {
            require(result.verifiedSnapshot() != null);
            Map<String, VerifiedFile> files = unique(requireValues(result.verifiedSnapshot().files()),
                    VerifiedFile::path);
            for (VerifiedFile file : files.values()) {
                requireText(file.path());
                require(file.sizeBytes() >= 0 && !blank(file.sha256()));
            }
            Path root = request.frozenRepositoryRequest().snapshotRoot().toAbsolutePath().normalize();
            return new FrozenSources(root, Map.copyOf(files));
        }

        VerifiedFile file(String path) {
            VerifiedFile file = files.get(path);
            require(file != null);
            return file;
        }

        byte[] bytes(ProofLocator locator) throws IOException {
            require(locator != null);
            return bytes(locator.path());
        }

        private byte[] bytes(String path) throws IOException {
            requireText(path);
            byte[] cached = bytes.get(path);
            if (cached != null) {
                return cached;
            }
            VerifiedFile file = file(path);
            Path resolved = root.resolve(path).normalize();
            require(resolved.startsWith(root));
            byte[] reopened = Files.readAllBytes(resolved);
            require(reopened.length == file.sizeBytes() && sha256(reopened).equals(file.sha256()));
            bytes.put(path, reopened);
            return reopened;
        }

        void validateProofNode(ProofNode node) throws IOException {
            byte[] source = bytes(node.locator());
            validateByteBounds(node.locator(), source);
            require(file(node.locator().path()).sha256().equals(node.sourceFileSha256()));
            require(sha256(Arrays.copyOfRange(source, node.locator().startByte(), node.locator().endByteExclusive()))
                    .equals(node.spanSha256()));
        }
    }

    private static <T> Map<String, T> unique(List<T> values, Function<T, String> id) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : requireValues(values)) {
            require(value != null);
            String key = id.apply(value);
            requireText(key);
            require(result.put(key, value) == null);
        }
        return Map.copyOf(result);
    }

    private static <T> List<T> requireValues(List<T> values) {
        require(values != null);
        for (T value : values) {
            require(value != null);
        }
        return values;
    }

    private static Set<String> ids(Collection<String> values) {
        require(values != null);
        Set<String> result = new TreeSet<>();
        for (String value : values) {
            requireText(value);
            require(result.add(value));
        }
        return result;
    }

    private static List<String> sorted(Collection<String> values) {
        return ids(values).stream().toList();
    }

    private static String canonical(Collection<?> values) {
        require(values != null);
        return values.stream().map(value -> {
            require(value != null);
            return value.toString();
        }).sorted().reduce("", (left, right) -> left + "\n" + right);
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw broken();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireText(String value) {
        require(!blank(value));
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw broken();
        }
    }

    private static Stage03Exception broken() {
        return new Stage03Exception(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN);
    }

    private static final class TreeMapView<T> {
        private final Map<String, T> values;

        private TreeMapView(List<T> source, Function<T, String> id) {
            Map<String, T> result = new java.util.TreeMap<>();
            for (T value : requireValues(source)) {
                require(value != null);
                String key = id.apply(value);
                requireText(key);
                require(result.put(key, value) == null);
            }
            values = Map.copyOf(result);
        }

        private Map<String, T> values() {
            return values;
        }
    }
}
