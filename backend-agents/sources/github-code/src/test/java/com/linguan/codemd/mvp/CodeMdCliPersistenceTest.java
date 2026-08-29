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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeMdCliPersistenceTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Test
    void generateUsesOnlyRecordedRoundsAndTraceReadsTheArchivedWorkspace() throws Exception {
        Path root = Files.createTempDirectory("mvp-cli-recorded-");
        MvpFixtures.Fixture fixture = MvpFixtures.valid(root);
        Path workspace = root.resolve("workspace");
        Path recordedR1 = root.resolve("recorded-r1.json");
        Path recordedR2 = root.resolve("recorded-r2.json");
        writeRecordedRounds(fixture, recordedR1, recordedR2);

        int generateExit = new CommandLine(new CodeMdCli()).execute(
                "generate",
                "--manifest", fixture.manifest().toString(),
                "--snapshot-root", fixture.snapshotRoot().toString(),
                "--workspace", workspace.toString(),
                "--recorded-r1", recordedR1.toString(),
                "--recorded-r2", recordedR2.toString());

        assertEquals(0, generateExit);
        JsonNode candidate = parseJson(workspace.resolve("candidate.json"));
        String candidateId = candidate.path("candidateId").asText();
        assertTrue(!candidateId.isBlank(), "generate must persist a usable candidate ID");

        ByteArrayOutputStream traceOutput = new ByteArrayOutputStream();
        CommandLine traceCommand = new CommandLine(new CodeMdCli());
        traceCommand.setOut(new PrintWriter(traceOutput, true, StandardCharsets.UTF_8));
        int traceExit = traceCommand.execute(
                "trace",
                "--workspace", workspace.toString(),
                "--candidate-id", candidateId,
                "--item-key", MvpFixtures.TRACE_ITEM_KEY);

        assertEquals(0, traceExit);
        String traceText = traceOutput.toString(StandardCharsets.UTF_8);
        assertTrue(traceText.contains(MvpFixtures.TRACE_ITEM_KEY));
        assertTrue(traceText.contains("src/main/java/example/OrderController.java"),
                "trace must resolve the locator from archived JSON after a fresh CLI instance");
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

    private static JsonNode parseJson(Path path) throws IOException {
        JsonNode node = JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
        assertTrue(node != null && node.isObject(), () -> path + " must contain a JSON object");
        return node;
    }
}
