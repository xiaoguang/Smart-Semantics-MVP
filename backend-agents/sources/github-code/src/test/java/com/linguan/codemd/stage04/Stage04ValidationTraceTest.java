package com.linguan.codemd.stage04;

import com.linguan.codemd.stage01.CodeFact;
import com.linguan.codemd.stage01.FactAtom;
import com.linguan.codemd.stage01.Proof;
import com.linguan.codemd.stage01.ProofNode;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage01.VerifiedFile;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage02.ModelEvidenceSpan;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage03.ReaderItem;
import com.linguan.codemd.stage03.ReaderSection;
import com.linguan.codemd.stage03.Stage03Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 04 D1 RED tracer for fresh deterministic Candidate validation and
 * factual Trace.  The source registry is deliberately an in-memory test
 * binding; all expected source values come from Stage01 records and reopened
 * frozen bytes rather than from the validator's implementation.
 */
class Stage04ValidationTraceTest {
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);

    @Test
    void freshValidationAndFactTraceReopenSourceAndRejectBothSourceAndArchiveDrift() throws Exception {
        Fixture fixture = fixture("stage04-validation-trace-");
        Path candidateDirectory = candidateDirectory(fixture.workspace(), fixture.candidate());
        Map<String, byte[]> beforeRepeat = snapshot(candidateDirectory);

        CandidateValidationService validation = new CandidateValidationService(fixture.workspace(),
                fixture.registry(), LIMITS);
        ValidationReceipt first = validation.validate(fixture.candidate());
        assertTrue(first.valid(), "an honest assembled Candidate validates in a fresh service instance");
        assertFalse(first.checks().isEmpty(), "validation returns typed checks rather than a bare boolean");
        assertTrue(first.checks().stream().allMatch(check -> "PASS".equals(check.result())));
        assertEquals(1, validationReceiptFiles(fixture.workspace()).size(),
                "the first receipt is written outside the immutable Candidate directory");

        ValidationReceipt repeated = new CandidateValidationService(fixture.workspace(), fixture.registry(), LIMITS)
                .validate(fixture.candidate());
        assertEquals(first, repeated, "the same validated bytes are create-if-absent idempotent");
        Map<String, byte[]> afterRepeat = snapshot(candidateDirectory);
        assertEquals(beforeRepeat.keySet(), afterRepeat.keySet(),
                "validation never changes the installed Candidate artifact set");
        beforeRepeat.forEach((artifact, bytes) -> assertArrayEquals(bytes, afterRepeat.get(artifact),
                "validation never rewrites Candidate bytes: " + artifact));
        assertEquals(1, validationReceiptFiles(fixture.workspace()).size(),
                "repeating the same validation does not append a duplicate receipt");

        ReaderAnchor anchor = fixture.readerAnchor();
        TraceView trace = new CandidateTraceResolver(fixture.workspace(), fixture.registry(), LIMITS)
                .trace(new TraceQuery(fixture.candidate().candidateId(), anchor.item().readerItemKey()));
        List<String> hopIds = trace.hops().stream()
                .flatMap(hop -> Stream.of(hop.sourceId(), hop.targetId()))
                .collect(Collectors.toList());
        assertTrue(hopIds.contains(anchor.atom().atomId()));
        assertTrue(hopIds.contains(anchor.fact().factId()));
        assertTrue(hopIds.contains(anchor.proof().proofId()));
        assertTrue(hopIds.contains(anchor.proofNode().proofNodeId()));
        assertEquals(anchor.item().readerItemKey(), trace.readerItemKey());
        assertFalse(trace.sourceSpans().isEmpty(), "a factual item must have a verified source span");
        assertExactReopenedSpan(trace, fixture.stage01Result(), fixture.stage01Request(), anchor.proofNode());

        Path sourcePath = fixture.stage01Request().frozenRepositoryRequest().snapshotRoot()
                .resolve(anchor.proofNode().locator().path());
        byte[] sourceBeforeDrift = Files.readAllBytes(sourcePath);
        Files.write(sourcePath, changedByte(sourceBeforeDrift));
        ValidationReceipt sourceDrift = validation.validate(fixture.candidate());
        assertFalse(sourceDrift.valid());
        assertHasFinding(sourceDrift, "SNAPSHOT_REOPEN_MISMATCH");
        M8Exception sourceTraceFailure = assertThrows(M8Exception.class,
                () -> new CandidateTraceResolver(fixture.workspace(), fixture.registry(), LIMITS)
                        .trace(new TraceQuery(fixture.candidate().candidateId(), anchor.item().readerItemKey())));
        assertEquals("SNAPSHOT_REOPEN_MISMATCH", sourceTraceFailure.failureCode());
        Files.write(sourcePath, sourceBeforeDrift);

        byte[] originalDocument = Files.readAllBytes(candidateDirectory.resolve("document.md"));
        byte[] tamperedDocument = concat(originalDocument, "\n篡改归档\n".getBytes(StandardCharsets.UTF_8));
        Files.write(candidateDirectory.resolve("document.md"), tamperedDocument);
        ValidationReceipt archiveDrift = validation.validate(fixture.candidate());
        assertFalse(archiveDrift.valid());
        assertHasFinding(archiveDrift, "DOCUMENT_HASH_MISMATCH");
        assertNotEquals(sourceDrift.validationReceiptId(), archiveDrift.validationReceiptId(),
                "each distinct invalid byte state gets a distinct append-only receipt");
        assertArrayEquals(tamperedDocument, Files.readAllBytes(candidateDirectory.resolve("document.md")),
                "validation reports archive tampering without repairing the immutable Candidate");
        assertTrue(validationReceiptFiles(fixture.workspace()).size() >= 3,
                "valid, source-drift, and archive-drift receipts remain separately archived");
    }

    @Test
    void unknownReaderItemFailsClosedWithoutSourceFallback() throws Exception {
        Fixture fixture = fixture("stage04-validation-trace-unknown-");
        CandidateTraceResolver resolver = new CandidateTraceResolver(fixture.workspace(), fixture.registry(), LIMITS);

        M8Exception failure = assertThrows(M8Exception.class,
                () -> resolver.trace(new TraceQuery(fixture.candidate().candidateId(), "reader-item:unknown")));
        assertEquals("TRACE_CLOSURE_BROKEN", failure.failureCode());
    }

    private static Fixture fixture(String prefix) throws Exception {
        Stage04CandidateFixture.Fixture candidate = Stage04CandidateFixture.create(prefix).install();
        return new Fixture(candidate.archiveWorkspace(), candidate.stage01Request(), candidate.stage01Result(),
                candidate.stage02Result(), candidate.stage03Result(), candidate.candidate(), candidate.registry());
    }

    private static ReaderAnchor readerAnchor(Fixture fixture) {
        List<ReaderItem> items = fixture.stage03Result().nineSectionPlan().sections().stream()
                .flatMap(section -> section.items().stream())
                .filter(item -> !item.basisAtomIds().isEmpty())
                .toList();
        for (ReaderItem item : items) {
            String atomId = item.basisAtomIds().get(0);
            EvidenceCapsule capsule = fixture.stage02Result().evidenceCapsules().stream()
                    .filter(candidate -> candidate.modelEvidenceSpans().stream()
                            .anyMatch(span -> span.supportedAtomIds().contains(atomId)))
                    .findFirst().orElse(null);
            if (capsule == null) {
                continue;
            }
            CodeFact fact = fixture.stage01Result().provenSourceFacts().provenFactSet().codeFacts().stream()
                    .filter(candidate -> candidate.atoms().stream().anyMatch(atom -> atom.atomId().equals(atomId)))
                    .findFirst().orElse(null);
            if (fact == null) {
                continue;
            }
            FactAtom atom = fact.atoms().stream().filter(candidate -> candidate.atomId().equals(atomId)).findFirst()
                    .orElseThrow();
            Proof proof = fixture.stage01Result().provenSourceFacts().proofPack().proofs().stream()
                    .filter(candidate -> candidate.proofId().equals(atom.proofId())).findFirst().orElseThrow();
            ProofNode proofNode = fixture.stage01Result().provenSourceFacts().proofPack().nodes().stream()
                    .filter(candidate -> candidate.proofNodeId().equals(proof.rootProofNodeId())).findFirst()
                    .orElseThrow();
            return new ReaderAnchor(item, fact, atom, proof, proofNode);
        }
        throw new AssertionError("synthetic Stage03 fixture has no factual ReaderItem with a Capsule span");
    }

    private static void assertExactReopenedSpan(TraceView trace, Stage01Result stage01,
                                                 Stage01Request request, ProofNode expected) throws IOException {
        Path sourcePath = request.frozenRepositoryRequest().snapshotRoot().resolve(expected.locator().path());
        byte[] source = Files.readAllBytes(sourcePath);
        byte[] slice = Arrays.copyOfRange(source, expected.locator().startByte(), expected.locator().endByteExclusive());
        String excerpt = new String(slice, StandardCharsets.UTF_8);
        VerifiedFile file = stage01.verifiedSnapshot().files().stream()
                .filter(candidate -> candidate.path().equals(expected.locator().path())).findFirst().orElseThrow();

        assertTrue(trace.sourceSpans().stream().anyMatch(span ->
                expected.locator().path().equals(span.relativePath())
                        && expected.locator().startByte() == span.startByte()
                        && expected.locator().endByteExclusive() == span.endByteExclusive()
                        && expected.locator().startLine() == span.startLine()
                        && expected.locator().startColumn() == span.startColumn()
                        && expected.locator().endLine() == span.endLine()
                        && expected.locator().endColumn() == span.endColumn()
                        && file.sha256().equals(span.sourceFileSha256())
                        && expected.spanSha256().equals(span.spanSha256())
                        && excerpt.equals(span.excerpt())
                        && sha256(slice).equals(span.excerptSha256())),
                "Trace must expose the exact reopened file/span bytes, hashes and line/column locator");
        assertTrue(trace.sourceSpans().stream().noneMatch(span -> span.relativePath().startsWith("/")),
                "Trace source paths remain relative to the registered snapshot");
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private static Map<String, byte[]> snapshot(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.collect(Collectors.toMap(path -> path.getFileName().toString(), path -> {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }, (left, right) -> left, LinkedHashMap::new));
        }
    }

    private static List<Path> validationReceiptFiles(Path workspace) throws IOException {
        Path validations = workspace.resolve("validations");
        if (!Files.isDirectory(validations)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(validations)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private static void assertHasFinding(ValidationReceipt receipt, String findingCode) {
        assertTrue(receipt.checks().stream().anyMatch(check -> "FAIL".equals(check.result())
                        && findingCode.equals(check.findingCode())),
                () -> "validation must expose stable finding " + findingCode + ": " + receipt);
    }

    private static byte[] changedByte(byte[] original) {
        byte[] changed = original.clone();
        int index = changed.length == 0 ? 0 : changed.length - 1;
        if (changed.length == 0) {
            return "x".getBytes(StandardCharsets.UTF_8);
        }
        changed[index] = (byte) (changed[index] ^ 0x01);
        return changed;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record Fixture(Path workspace, Stage01Request stage01Request, Stage01Result stage01Result,
                           Stage02Result stage02Result, Stage03Result stage03Result,
                           CandidateReference candidate, SourceRegistry registry) {
        ReaderAnchor readerAnchor() {
            return Stage04ValidationTraceTest.readerAnchor(this);
        }
    }

    private record ReaderAnchor(ReaderItem item, CodeFact fact, FactAtom atom, Proof proof, ProofNode proofNode) {
    }
}
