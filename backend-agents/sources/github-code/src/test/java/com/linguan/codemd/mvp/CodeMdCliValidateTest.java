package com.linguan.codemd.mvp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.cli.CodeMdCli;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeMdCliValidateTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Test
    void freshCliInstanceValidatesRecordedWorkspaceAndPrintsJsonReceipt() throws Exception {
        Path root = Files.createTempDirectory("mvp-cli-validate-valid-");
        MvpFixtures.Fixture fixture = MvpFixtures.valid(root);
        Path workspace = root.resolve("workspace");
        Path recordedR1 = root.resolve("recorded-r1.json");
        Path recordedR2 = root.resolve("recorded-r2.json");
        writeRecordedRounds(fixture, recordedR1, recordedR2);

        assertEquals(0, executeGenerate(fixture, workspace, recordedR1, recordedR2));
        String candidateId = parseJson(workspace.resolve("candidate.json"))
                .path("candidateId").asText();
        assertFalse(candidateId.isBlank());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine freshCli = commandLine(output);
        int validateExit = freshCli.execute(
                "validate",
                "--workspace", workspace.toString(),
                "--candidate-id", candidateId);

        assertEquals(0, validateExit);
        JsonNode outputReceipt = assertDoesNotThrow(
                () -> parseJson(output.toString(StandardCharsets.UTF_8)),
                "validate must print a valid JSON receipt");
        assertTrue(outputReceipt.path("valid").asBoolean(false),
                "validate must print a valid JSON receipt");
        assertEquals(candidateId, outputReceipt.path("candidateId").asText());
        assertTrue(parseJson(workspace.resolve("validation-receipt.json"))
                        .path("valid").asBoolean(false),
                "validate must persist a valid receipt");
    }

    @Test
    void freshCliInstanceRejectsTamperedMarkdownWithoutRepairingIt() throws Exception {
        Path root = Files.createTempDirectory("mvp-cli-validate-tamper-");
        MvpFixtures.Fixture fixture = MvpFixtures.valid(root);
        Path workspace = root.resolve("workspace");
        Path recordedR1 = root.resolve("recorded-r1.json");
        Path recordedR2 = root.resolve("recorded-r2.json");
        writeRecordedRounds(fixture, recordedR1, recordedR2);

        assertEquals(0, executeGenerate(fixture, workspace, recordedR1, recordedR2));
        String candidateId = parseJson(workspace.resolve("candidate.json"))
                .path("candidateId").asText();
        Path document = workspace.resolve("document.md");
        byte[] tamperedDocument = (Files.readString(document, StandardCharsets.UTF_8)
                + "篡改\n").getBytes(StandardCharsets.UTF_8);
        Files.write(document, tamperedDocument);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine freshCli = commandLine(output);
        int validateExit = freshCli.execute(
                "validate",
                "--workspace", workspace.toString(),
                "--candidate-id", candidateId);

        assertNotEquals(0, validateExit,
                "tampered Markdown must make validate return a nonzero exit code");
        JsonNode outputReceipt = assertDoesNotThrow(
                () -> parseJson(output.toString(StandardCharsets.UTF_8)),
                "validate must print a JSON receipt even when the candidate is invalid");
        assertFalse(outputReceipt.path("valid").asBoolean(true),
                "tampered Markdown must produce a JSON receipt with valid=false");
        assertTrue(outputReceipt.path("findings").toString().contains("DOCUMENT_HASH_MISMATCH"),
                "receipt must explain the tampered document");

        JsonNode persistedReceipt = parseJson(workspace.resolve("validation-receipt.json"));
        assertFalse(persistedReceipt.path("valid").asBoolean(true));
        assertTrue(persistedReceipt.path("findings").toString()
                        .contains("DOCUMENT_HASH_MISMATCH"));
        assertArrayEquals(tamperedDocument, Files.readAllBytes(document),
                "validation must not repair the tampered Markdown");
    }

    private static int executeGenerate(MvpFixtures.Fixture fixture, Path workspace,
                                       Path recordedR1, Path recordedR2) {
        return commandLine(new ByteArrayOutputStream()).execute(
                "generate",
                "--manifest", fixture.manifest().toString(),
                "--snapshot-root", fixture.snapshotRoot().toString(),
                "--workspace", workspace.toString(),
                "--recorded-r1", recordedR1.toString(),
                "--recorded-r2", recordedR2.toString());
    }

    private static CommandLine commandLine(ByteArrayOutputStream output) {
        CommandLine command = new CommandLine(new CodeMdCli());
        command.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        command.setErr(new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        return command;
    }

    private static JsonNode parseJson(Path path) throws IOException {
        return parseJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static JsonNode parseJson(String text) throws IOException {
        JsonNode node = JSON.readTree(text);
        assertTrue(node != null && node.isObject(), "expected a JSON object receipt");
        return node;
    }

    private static void writeRecordedRounds(MvpFixtures.Fixture fixture,
                                             Path recordedR1, Path recordedR2) throws Exception {
        List<String> responses = new ArrayList<>();
        ModelProvider recorder = task -> {
            String response = MvpFixtures.validProvider().execute(task);
            responses.add(response);
            return response;
        };
        CodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent();
        agent.generate(new GenerationRequest(fixture.manifest(), fixture.snapshotRoot(), recorder));
        assertEquals(2, responses.size());
        JSON.readTree(responses.get(0));
        JSON.readTree(responses.get(1));
        Files.writeString(recordedR1, responses.get(0), StandardCharsets.UTF_8);
        Files.writeString(recordedR2, responses.get(1), StandardCharsets.UTF_8);
    }
}
