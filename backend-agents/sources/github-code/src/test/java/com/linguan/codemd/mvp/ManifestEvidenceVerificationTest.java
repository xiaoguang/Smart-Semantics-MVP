package com.linguan.codemd.mvp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestEvidenceVerificationTest {
    @Test
    void changedFrozenSourceBytesFailBeforeInterpretation() throws Exception {
        Path root = Files.createTempDirectory("mvp-source-tamper-");
        MvpFixtures.Fixture fixture = MvpFixtures.valid(root);
        MvpFixtures.tamperSource(fixture);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> generate(fixture));

        assertTrue(failure.getMessage().contains("SOURCE_HASH_MISMATCH"),
                () -> "expected a source hash failure, got: " + failure.getMessage());
    }

    @Test
    void changedEvidenceExcerptDigestFailsClosed() throws Exception {
        Path root = Files.createTempDirectory("mvp-evidence-tamper-");
        MvpFixtures.Fixture fixture = MvpFixtures.valid(root);
        MvpFixtures.tamperExcerptHash(fixture);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> generate(fixture));

        assertTrue(failure.getMessage().contains("EXCERPT_HASH_MISMATCH"),
                () -> "expected an excerpt hash failure, got: " + failure.getMessage());
    }

    private CandidateReference generate(MvpFixtures.Fixture fixture) {
        CodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent();
        return agent.generate(new GenerationRequest(
                fixture.manifest(), fixture.snapshotRoot(), MvpFixtures.validProvider()));
    }
}
