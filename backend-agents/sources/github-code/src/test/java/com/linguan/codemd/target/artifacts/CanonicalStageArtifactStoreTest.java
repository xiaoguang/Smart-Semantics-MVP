package com.linguan.codemd.target.artifacts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalStageArtifactStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void installsAndFreshlyReopensAStageSemanticSetWithThePublisherProvenance() throws Exception {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies = FoundationStage01Fixture.policies(codec);

        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore moduleStore =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));
            ModulePublicationReference publisherReference =
                    moduleStore.install(FoundationStage01Fixture.publisherRequest(policies)).reference();
            StageInstallRequest request = FoundationStage01Fixture.stageRequest(policies, publisherReference);
            CanonicalStageArtifactStore store =
                    new FileSystemCanonicalStageArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));
            InstalledStagePublication installed = store.install(request);
            ReopenedStagePublication reopened = store.reopen(installed.reference());

            assertEquals("INSTALLED", installed.disposition());
            assertEquals(installed.reference(), reopened.reference());
            assertEquals(3, reopened.semanticPayloads().size());
            assertEquals(
                    "STAGE01_SOURCE_INPUT", reopened.semanticPayloads().get(0).descriptor().artifactType());
        }
    }
}
