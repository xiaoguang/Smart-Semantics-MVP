package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.cli.CodeMdCli;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Final acceptance probes for bounded reads at the remaining untrusted seams. */
class Stage04FinalAuditBoundsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits FIXTURE_LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private static final String SIZE_LIMIT = M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name();
    private static final String TOKEN_ENV = "CODE_MD_SERVER_TOKEN";
    private static final String CANDIDATE_ID = "candidate:" + "c".repeat(64);
    private static final String REGISTRATION_ID = "source-registration:" + "a".repeat(64);

    @Test
    void existingCandidateCollisionRejectsOversizedArtifactWithBoundedStableCode() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                "stage04-final-bounds-candidate-").install();
        Path destination = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        sparse(destination.resolve("registry-bundle.json"), FIXTURE_LIMITS.maxSidecarBytes() + 1L);

        M8Exception failure = assertThrows(M8Exception.class,
                () -> new FilesystemCandidateStore(fixture.archiveWorkspace(), FIXTURE_LIMITS)
                        .install(fixture.bundle()));
        assertEquals(SIZE_LIMIT, failure.failureCode(),
                "an existing artifact must be size-checked before collision comparison");
    }

    @Test
    void validationReceiptCollisionRejectsOversizedExistingReceiptWithBoundedStableCode() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                "stage04-final-bounds-receipt-").install();
        ValidationReceipt first = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                FIXTURE_LIMITS).validate(fixture.candidate());
        Path receipt = fixture.archiveWorkspace().resolve("validations")
                .resolve(CandidateArchive.candidateDigest(fixture.candidate().candidateId()))
                .resolve(first.validationReceiptId().substring("validation-receipt:".length()) + ".json");
        assertTrue(Files.isRegularFile(receipt), "the first validation must persist its deterministic receipt");
        sparse(receipt, FIXTURE_LIMITS.maxSidecarBytes() + 1L);

        M8Exception failure = assertThrows(M8Exception.class,
                () -> new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(), FIXTURE_LIMITS)
                        .validate(fixture.candidate()));
        assertEquals(SIZE_LIMIT, failure.failureCode(),
                "an existing receipt must be size-checked before create-if-absent comparison");
    }

    @Test
    void candidateArchiveAggregateBudgetRejectsLimitPlusOneWithoutLargeAllocation() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                "stage04-final-bounds-aggregate-").install();
        Path destination = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        long total;
        long largest;
        try (Stream<Path> files = Files.list(destination)) {
            List<Path> paths = files.toList();
            total = paths.stream().mapToLong(path -> size(path)).sum();
            largest = paths.stream().mapToLong(path -> size(path)).max().orElseThrow();
        }
        CandidateStoreLimits aggregateLimit = new CandidateStoreLimits(Math.max(1, total - 1), largest + 1);

        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                aggregateLimit).validate(fixture.candidate());
        assertFalse(validation.valid(), "a candidate whose aggregate is limit+1 must be invalid");
        assertTrue(validation.checks().stream().anyMatch(check -> SIZE_LIMIT.equals(check.findingCode())),
                "aggregate overflow must retain the stable candidate-size finding");
    }

    @Test
    void loopbackAndTargetCliRejectSparseOversizedConfigOnAllConstructiblePaths() throws Exception {
        Path root = Files.createTempDirectory("stage04-final-bounds-config-");
        Path sparseConfig = root.resolve("oversized-agent.toml");
        sparse(sparseConfig, CandidateValidationSupport.DEFAULT_UNTRUSTED_RECORD_BYTES + 1L);
        NoopAgent agent = new NoopAgent();
        CandidateArtifactReader reader = new CandidateArtifactReader() {
            @Override
            public CandidateReference candidate(String candidateId) {
                throw Stage04Validation.failure(M8FailureCode.NOT_IMPLEMENTED);
            }

            @Override
            public String markdown(String candidateId) {
                throw Stage04Validation.failure(M8FailureCode.NOT_IMPLEMENTED);
            }
        };

        RuntimeException loopbackFailure = assertThrows(RuntimeException.class,
                () -> new LoopbackHttpServer(sparseConfig, agent, reader,
                        Map.of(TOKEN_ENV, "test-only-token")));
        assertEquals(SIZE_LIMIT, CandidateArtifactReader.failureCode(loopbackFailure),
                "loopback construction must fail before TOML parsing allocates the file");

        Invocation generate = invokeCli(agent, reader, "generate", "--config", sparseConfig.toString(),
                "--source", REGISTRATION_ID);
        assertEquals(2, generate.exitCode());
        assertEquals(SIZE_LIMIT, object(generate.stdout()).path("code").asText(),
                "target generate must preserve the bounded config failure code");

        Invocation validate = invokeCli(agent, reader, "validate", "--config", sparseConfig.toString(),
                "--candidate", CANDIDATE_ID);
        assertEquals(2, validate.exitCode());
        assertEquals(SIZE_LIMIT, object(validate.stdout()).path("code").asText(),
                "target validate must preserve the bounded config failure code");
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private static void sparse(Path path, long size) throws Exception {
        assertTrue(size > 0);
        Files.createDirectories(path.getParent());
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.position(size - 1);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
        assertEquals(size, Files.size(path));
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static Invocation invokeCli(CodeToMarkdownAgent agent, CandidateArtifactReader reader,
                                        String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine command = new CommandLine(new CodeMdCli(agent, reader));
        command.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
        command.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
        return new Invocation(command.execute(args), stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private static JsonNode object(String stdout) throws Exception {
        JsonNode value = JSON.readTree(stdout);
        assertNotNull(value, "target CLI must print one JSON object");
        assertTrue(value.isObject(), "target CLI must print one JSON object");
        return value;
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }

    private static final class NoopAgent implements CodeToMarkdownAgent {
        @Override
        public CandidateReference generateCandidate(FrozenRepositoryRequest request) {
            throw Stage04Validation.failure(M8FailureCode.NOT_IMPLEMENTED);
        }

        @Override
        public CandidateReference improveCandidate(ImprovementRequest request) {
            throw Stage04Validation.failure(M8FailureCode.NOT_IMPLEMENTED);
        }

        @Override
        public ValidationReceipt validateCandidate(CandidateReference candidate) {
            throw Stage04Validation.failure(M8FailureCode.NOT_IMPLEMENTED);
        }

        @Override
        public TraceView trace(TraceQuery query) {
            throw Stage04Validation.failure(M8FailureCode.NOT_IMPLEMENTED);
        }
    }
}
