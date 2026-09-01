package com.linguan.codemd.target.artifacts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalModuleArtifactStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void installsAnExactPolicyVerifiedPayloadIdempotentlyAndFreshlyReopensIt() throws Exception {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies = FoundationStage01Fixture.policies(codec);
        ModuleInstallRequest request = FoundationStage01Fixture.publisherRequest(policies);

        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore store =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, policies, new ArtifactStoreLimits(3, 4096, 8192, 8));

            InstalledModulePublication installed = store.install(request);
            InstalledModulePublication reinstalled = store.install(request);
            ReopenedModulePublication reopened = store.reopen(installed.reference());

            assertEquals("INSTALLED", installed.disposition());
            assertEquals("ALREADY_INSTALLED", reinstalled.disposition());
            assertEquals(installed.reference(), reinstalled.reference());
            assertEquals(3, reopened.payloads().size());
            assertArrayEquals(
                    request.payloads().get(1).canonicalUtf8().copyToByteArray(),
                    reopened.payloads().get(1).canonicalUtf8().copyToByteArray());
        }
    }
}
