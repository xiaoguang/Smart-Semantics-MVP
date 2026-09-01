package com.linguan.codemd.target.artifacts;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalPublicationStoreHardeningTest {
    @TempDir Path temporaryDirectory;

    @Test
    void rejectsADifferentRequestAtAnAlreadyAddressedModuleAsACollision() {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies = FoundationStage01Fixture.policies(codec);
        ModuleInstallRequest first = FoundationStage01Fixture.publisherRequest(policies);
        ModuleInstallRequest changed =
                new ModuleInstallRequest(
                        first.address(),
                        "target-v2",
                        first.upstreamArtifacts(),
                        first.controls(),
                        first.status(),
                        first.gapRefs(),
                        first.payloads());

        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore store =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));
            store.install(first);

            ArtifactStoreException exception =
                    assertThrows(ArtifactStoreException.class, () -> store.install(changed));

            assertTrue(
                    exception.getMessage().startsWith("MODULE_PUBLICATION_COLLISION:"),
                    exception::getMessage);
        }
    }

    @Test
    void rejectsATamperedModulePayloadOnFreshReopen() throws Exception {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies = FoundationStage01Fixture.policies(codec);
        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore store =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));
            ModulePublicationReference reference =
                    store.install(FoundationStage01Fixture.publisherRequest(policies)).reference();
            Files.writeString(
                    moduleDirectory().resolve("source-input.json"), "tampered", StandardCharsets.UTF_8);

            ArtifactStoreException exception =
                    assertThrows(ArtifactStoreException.class, () -> store.reopen(reference));

            assertTrue(exception.getMessage().startsWith("MODULE_PUBLICATION_INVALID:"), exception::getMessage);
        }
    }

    @Test
    void rejectsPartialStagePublicFilesBeforeAReceiptCanBeInstalled() throws Exception {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies = FoundationStage01Fixture.policies(codec);
        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore moduleStore =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));
            ModulePublicationReference publisher =
                    moduleStore.install(FoundationStage01Fixture.publisherRequest(policies)).reference();
            Files.writeString(
                    stageDirectory().resolve("source-input.json"), "partial", StandardCharsets.UTF_8);
            CanonicalStageArtifactStore stageStore =
                    new FileSystemCanonicalStageArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));

            ArtifactStoreException exception =
                    assertThrows(
                            ArtifactStoreException.class,
                            () -> stageStore.install(FoundationStage01Fixture.stageRequest(policies, publisher)));

            assertTrue(exception.getMessage().startsWith("STAGE_PUBLICATION_COLLISION:"), exception::getMessage);
        }
    }

    @Test
    void rejectsASymlinkedStageSemanticFileOnFreshReopen() throws Exception {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies = FoundationStage01Fixture.policies(codec);
        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore moduleStore =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));
            ModulePublicationReference publisher =
                    moduleStore.install(FoundationStage01Fixture.publisherRequest(policies)).reference();
            CanonicalStageArtifactStore stageStore =
                    new FileSystemCanonicalStageArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));
            StagePublicationReference reference =
                    stageStore.install(FoundationStage01Fixture.stageRequest(policies, publisher)).reference();
            Path sourceInput = stageDirectory().resolve("source-input.json");
            Files.delete(sourceInput);
            Files.createSymbolicLink(sourceInput, Path.of("untrusted-target"));

            ArtifactStoreException exception =
                    assertThrows(ArtifactStoreException.class, () -> stageStore.reopen(reference));

            assertTrue(exception.getMessage().startsWith("STAGE_PUBLICATION_INVALID:"), exception::getMessage);
        }
    }

    @Test
    void writesTheDocumentedTopLevelStageAddressIntoTheStageReceipt() throws Exception {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies = FoundationStage01Fixture.policies(codec);
        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore moduleStore =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));
            ModulePublicationReference publisher =
                    moduleStore.install(FoundationStage01Fixture.publisherRequest(policies)).reference();
            CanonicalStageArtifactStore stageStore =
                    new FileSystemCanonicalStageArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));
            stageStore.install(FoundationStage01Fixture.stageRequest(policies, publisher));
            JsonNode receipt =
                    new ObjectMapper().readTree(Files.readAllBytes(stageDirectory().resolve("stage-receipt.json")));

            assertTrue(receipt.has("runId"));
            assertTrue(receipt.has("stageNumber"));
            assertTrue(receipt.has("stageKey"));
            assertTrue(!receipt.has("address"));
        }
    }

    private Path moduleDirectory() {
        return stageDirectory().resolve("modules").resolve("03-publish");
    }

    private Path stageDirectory() {
        return temporaryDirectory
                .resolve("runs")
                .resolve("analysis-run:" + "a".repeat(64))
                .resolve("stages")
                .resolve("01-freeze-source");
    }
}
