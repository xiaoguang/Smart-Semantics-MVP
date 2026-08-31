package com.linguan.codemd.stage03;

import com.linguan.codemd.stage01.CodeFact;
import com.linguan.codemd.stage01.FactAtom;
import com.linguan.codemd.stage01.Proof;
import com.linguan.codemd.stage01.ProofNode;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage01.VerifiedFile;
import com.linguan.codemd.stage02.AllowedAtomView;
import com.linguan.codemd.stage02.AllowedFactView;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage02.FlowSlice;
import com.linguan.codemd.stage02.ModelEvidenceSpan;
import com.linguan.codemd.stage02.OutcomePath;
import com.linguan.codemd.stage02.ProjectionObligation;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02ResourceBudget;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public-record closure contract for the internal pre-provider Capsule gate. */
class Stage03CapsuleClosureTest {

    @Test
    void honestSingleFlowCapsuleHasIndependentProofSpanAndObligationClosure() throws Exception {
        Scenario scenario = scenario();
        assertIndependentClosureInputs(scenario);

        assertDoesNotThrow(() -> Stage03CapsuleClosureValidator.validate(scenario.stage01Request(),
                scenario.stage01(), scenario.stage02(), scenario.flow(), scenario.capsule()));
    }

    @Test
    void everySingleFieldClosureMutationFailsWithStableCapsuleClosureCode() throws Exception {
        Scenario scenario = scenario();
        assertIndependentClosureInputs(scenario);

        List<Mutation> mutations = List.of(
                new Mutation("unknown proof pack", capsule -> copyCapsule(capsule,
                        "proof-pack:unknown", capsule.allowedFacts(), capsule.modelEvidenceSpans(),
                        capsule.projectionObligations())),
                new Mutation("unknown atom proof", Stage03CapsuleClosureTest::unknownAtomProof),
                new Mutation("wrong source hash", Stage03CapsuleClosureTest::wrongSourceHash),
                new Mutation("wrong excerpt hash", Stage03CapsuleClosureTest::wrongExcerptHash),
                new Mutation("wrong excerpt", Stage03CapsuleClosureTest::wrongExcerpt),
                new Mutation("unknown satisfying span", Stage03CapsuleClosureTest::unknownSatisfyingSpan),
                new Mutation("unsupported obligation subject", Stage03CapsuleClosureTest::unsupportedSubject));

        Executable[] checks = mutations.stream().map(mutation -> (Executable) () -> {
            Stage03Exception failure = assertThrows(Stage03Exception.class,
                    () -> Stage03CapsuleClosureValidator.validate(scenario.stage01Request(), scenario.stage01(),
                            scenario.stage02(), scenario.flow(), mutation.apply().apply(scenario.capsule())));
            assertEquals(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN, failure.code(), mutation.label());
        }).toArray(Executable[]::new);
        org.junit.jupiter.api.Assertions.assertAll("Capsule closure mutations", checks);
    }

    private static void assertIndependentClosureInputs(Scenario scenario) throws Exception {
        EvidenceCapsule capsule = scenario.capsule();
        FlowSlice flow = scenario.flow();
        String proofPackId = scenario.stage01().provenSourceFacts().proofPack().proofPackId();
        assertEquals(proofPackId, capsule.proofPackId());
        assertEquals(flow.flowSliceId(), capsule.flowSliceId());
        assertEquals(Set.copyOf(flow.atomIds()), capsule.allowedFacts().stream()
                .flatMap(fact -> fact.atoms().stream()).map(AllowedAtomView::atomId).collect(Collectors.toSet()));
        assertEquals(Set.copyOf(flow.outcomePaths().stream().map(OutcomePath::outcomePathId).toList()),
                Set.copyOf(capsule.outcomePathIds()));

        Map<String, CodeFact> facts = scenario.stage01().provenSourceFacts().provenFactSet().codeFacts().stream()
                .collect(Collectors.toMap(CodeFact::factId, value -> value));
        Map<String, Proof> proofs = scenario.stage01().provenSourceFacts().proofPack().proofs().stream()
                .collect(Collectors.toMap(Proof::proofId, value -> value));
        Set<String> proofNodeIds = scenario.stage01().provenSourceFacts().proofPack().nodes().stream()
                .map(ProofNode::proofNodeId).collect(Collectors.toSet());
        Set<String> proofEdgeIds = scenario.stage01().provenSourceFacts().proofPack().edges().stream()
                .map(edge -> edge.proofEdgeId()).collect(Collectors.toSet());
        for (AllowedFactView fact : capsule.allowedFacts()) {
            CodeFact sourceFact = facts.get(fact.factId());
            assertNotNull(sourceFact, "Capsule fact must be backed by Stage01 CodeFact");
            assertEquals(sourceFact.kind(), fact.kind());
            Map<String, FactAtom> sourceAtoms = sourceFact.atoms().stream()
                    .collect(Collectors.toMap(FactAtom::atomId, value -> value));
            for (AllowedAtomView atom : fact.atoms()) {
                FactAtom sourceAtom = sourceAtoms.get(atom.atomId());
                assertNotNull(sourceAtom, "Capsule atom must be backed by the same fact");
                assertEquals(sourceAtom.proofId(), atom.proofId());
                Proof proof = proofs.get(atom.proofId());
                assertNotNull(proof, "Capsule atom proof must be in the Stage01 proof pack");
                assertEquals(fact.factId(), proof.factId());
                assertEquals(atom.atomId(), proof.atomId());
                assertEquals("CLOSED", proof.status());
                assertTrue(proofNodeIds.contains(proof.rootProofNodeId()));
                assertTrue(proofNodeIds.containsAll(proof.requiredProofNodeIds()));
                assertTrue(proofEdgeIds.containsAll(proof.requiredProofEdgeIds()));
            }
        }

        Map<String, VerifiedFile> verifiedFiles = scenario.stage01().verifiedSnapshot().files().stream()
                .collect(Collectors.toMap(VerifiedFile::path, value -> value));
        Map<String, ModelEvidenceSpan> spans = capsule.modelEvidenceSpans().stream()
                .collect(Collectors.toMap(ModelEvidenceSpan::modelEvidenceSpanId, value -> value));
        for (ModelEvidenceSpan span : capsule.modelEvidenceSpans()) {
            VerifiedFile file = verifiedFiles.get(span.locator().path());
            assertNotNull(file, "span locator must resolve to a frozen source file");
            assertEquals(file.sha256(), span.sourceFileSha256());
            byte[] bytes = Files.readAllBytes(scenario.stage01Request().frozenRepositoryRequest().snapshotRoot()
                    .resolve(span.locator().path()));
            assertTrue(span.locator().startByte() >= 0);
            assertTrue(span.locator().endByteExclusive() > span.locator().startByte());
            assertTrue(span.locator().endByteExclusive() <= bytes.length);
            String excerpt = new String(Arrays.copyOfRange(bytes, span.locator().startByte(),
                    span.locator().endByteExclusive()), StandardCharsets.UTF_8);
            assertEquals(excerpt, span.excerpt());
            assertEquals(sha256(excerpt.getBytes(StandardCharsets.UTF_8)), span.excerptSha256());
        }
        for (ProjectionObligation obligation : capsule.projectionObligations()) {
            assertFalse(obligation.satisfyingSpanIds().isEmpty());
            Set<String> supported = new HashSet<>();
            for (String spanId : obligation.satisfyingSpanIds()) {
                ModelEvidenceSpan span = spans.get(spanId);
                assertNotNull(span, "obligation must reference a Capsule span");
                supported.addAll(span.supportedAtomIds());
                supported.addAll(span.supportedOutcomePathIds());
            }
            assertTrue(supported.contains(obligation.subjectId()),
                    "obligation subject must be supported by its satisfying span set");
        }
    }

    private static EvidenceCapsule unknownAtomProof(EvidenceCapsule capsule) {
        AllowedFactView selectedFact = capsule.allowedFacts().get(0);
        String selectedAtomId = selectedFact.atoms().get(0).atomId();
        List<AllowedFactView> facts = capsule.allowedFacts().stream().map(fact -> new AllowedFactView(fact.factId(),
                fact.kind(), fact.atoms().stream().map(atom -> atom.atomId().equals(selectedAtomId)
                        ? new AllowedAtomView(atom.atomId(), atom.role(), atom.name(), atom.value(), "proof:unknown")
                        : atom).toList())).toList();
        return copyCapsule(capsule, capsule.proofPackId(), facts, capsule.modelEvidenceSpans(),
                capsule.projectionObligations());
    }

    private static EvidenceCapsule wrongSourceHash(EvidenceCapsule capsule) {
        ModelEvidenceSpan selected = capsule.modelEvidenceSpans().get(0);
        List<ModelEvidenceSpan> spans = capsule.modelEvidenceSpans().stream().map(span ->
                span.modelEvidenceSpanId().equals(selected.modelEvidenceSpanId()) ? new ModelEvidenceSpan(
                        span.modelEvidenceSpanId(), span.locator(), "source-hash:unknown", span.excerpt(),
                        span.excerptSha256(), span.supportedAtomIds(), span.supportedOutcomePathIds()) : span).toList();
        return copyCapsule(capsule, capsule.proofPackId(), capsule.allowedFacts(), spans,
                capsule.projectionObligations());
    }

    private static EvidenceCapsule wrongExcerptHash(EvidenceCapsule capsule) {
        ModelEvidenceSpan selected = capsule.modelEvidenceSpans().get(0);
        List<ModelEvidenceSpan> spans = capsule.modelEvidenceSpans().stream().map(span ->
                span.modelEvidenceSpanId().equals(selected.modelEvidenceSpanId()) ? new ModelEvidenceSpan(
                        span.modelEvidenceSpanId(), span.locator(), span.sourceFileSha256(), span.excerpt(),
                        "excerpt-hash:unknown", span.supportedAtomIds(), span.supportedOutcomePathIds()) : span).toList();
        return copyCapsule(capsule, capsule.proofPackId(), capsule.allowedFacts(), spans,
                capsule.projectionObligations());
    }

    private static EvidenceCapsule wrongExcerpt(EvidenceCapsule capsule) {
        ModelEvidenceSpan selected = capsule.modelEvidenceSpans().get(0);
        List<ModelEvidenceSpan> spans = capsule.modelEvidenceSpans().stream().map(span ->
                span.modelEvidenceSpanId().equals(selected.modelEvidenceSpanId()) ? new ModelEvidenceSpan(
                        span.modelEvidenceSpanId(), span.locator(), span.sourceFileSha256(),
                        span.excerpt() + "tampered", span.excerptSha256(), span.supportedAtomIds(),
                        span.supportedOutcomePathIds()) : span).toList();
        return copyCapsule(capsule, capsule.proofPackId(), capsule.allowedFacts(), spans,
                capsule.projectionObligations());
    }

    private static EvidenceCapsule unknownSatisfyingSpan(EvidenceCapsule capsule) {
        ProjectionObligation selected = capsule.projectionObligations().get(0);
        List<ProjectionObligation> obligations = new ArrayList<>(capsule.projectionObligations());
        obligations.set(0, new ProjectionObligation(selected.obligationId(), selected.kind(), selected.subjectId(),
                List.of("model-evidence-span:unknown")));
        return copyCapsule(capsule, capsule.proofPackId(), capsule.allowedFacts(), capsule.modelEvidenceSpans(),
                obligations);
    }

    private static EvidenceCapsule unsupportedSubject(EvidenceCapsule capsule) {
        ProjectionObligation selected = capsule.projectionObligations().get(0);
        List<ProjectionObligation> obligations = new ArrayList<>(capsule.projectionObligations());
        obligations.set(0, new ProjectionObligation(selected.obligationId(), selected.kind(), "subject:unknown",
                selected.satisfyingSpanIds()));
        return copyCapsule(capsule, capsule.proofPackId(), capsule.allowedFacts(), capsule.modelEvidenceSpans(),
                obligations);
    }

    private static EvidenceCapsule copyCapsule(EvidenceCapsule original, String proofPackId,
                                                List<AllowedFactView> facts, List<ModelEvidenceSpan> spans,
                                                List<ProjectionObligation> obligations) {
        return new EvidenceCapsule(original.schemaVersion(), original.evidenceCapsuleId(), original.flowSliceId(),
                proofPackId, original.projectionProfileRef(), original.outcomePathIds(), facts,
                original.allowedGaps(), spans, obligations, original.budgetUsage());
    }

    private static Scenario scenario() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(Files.createTempDirectory("stage03-capsule-closure-"));
        Stage01Request stage01Request = Stage03Fixtures.stage01Request(root);
        Stage01Result stage01 = new Stage01Analyzer().analyze(stage01Request);
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(stage01Request, stage01.stage01ResultId(),
                new Stage02ResourceBudget(128, 64, 20_000, 40_000, 128, 64, 16_384, 262_144, 256));
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        assertEquals(1, stage02.flowSlices().size(), "closure fixture must compile exactly one Flow");
        assertEquals(1, stage02.evidenceCapsules().size(), "closure fixture must produce exactly one Capsule");
        FlowSlice flow = stage02.flowSlices().get(0);
        EvidenceCapsule capsule = stage02.evidenceCapsules().stream()
                .filter(candidate -> candidate.flowSliceId().equals(flow.flowSliceId())).findFirst().orElseThrow();
        assertFalse(capsule.modelEvidenceSpans().isEmpty(), "closure fixture needs evidence spans");
        assertFalse(capsule.projectionObligations().isEmpty(), "closure fixture needs projection obligations");
        return new Scenario(stage01Request, stage01, stage02, flow, capsule);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record Scenario(Stage01Request stage01Request, Stage01Result stage01, Stage02Result stage02,
                             FlowSlice flow, EvidenceCapsule capsule) {
    }

    private record Mutation(String label, UnaryOperator<EvidenceCapsule> apply) {
    }
}
