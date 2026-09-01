package com.linguan.codemd.target.artifacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleArtifactEnvelopeTest {
    private static final String RUN_ID = "analysis-run:" + "a".repeat(64);

    @TempDir Path temporaryDirectory;

    @Test
    void writesAndFreshlyParsesTheCanonicalM1Envelope() throws Exception {
        CanonicalArtifactPolicy policy = new CanonicalArtifactPolicy(
                new ArtifactPolicyKey("STAGE01_ADMITTED_SOURCE_REQUEST", "stage01-admitted-source-request-v2"),
                "source-request",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false,
                "METADATA_ONLY");
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("sourceRegistrationId", "source-registration:" + "b".repeat(64));
        payload.put("declaredPathCount", 3);

        ModuleArtifactEnvelope written = ModuleArtifactEnvelope.write(
                new ModuleArtifactEnvelopeDraft(
                        new StageModuleAddress(RUN_ID, 1, "freeze-source", 1, "request-admission"),
                        "v2",
                        List.of(reference("capture-receipt", 'c'), reference("snapshot-manifest", 'd')),
                        controls(),
                        "SUCCEEDED",
                        List.of(),
                        payload),
                policy);
        ModuleArtifactEnvelope reopened = ModuleArtifactEnvelope.parse(written.canonicalUtf8(), policy);

        assertThat(reopened.reference()).isEqualTo(written.reference());
        assertThat(reopened.payload().get("declaredPathCount").intValue()).isEqualTo(3);
        assertThat(reopened.payload().get("sourceRegistrationId").textValue())
                .isEqualTo("source-registration:" + "b".repeat(64));
        assertThat(new ObjectMapper().readTree(written.canonicalUtf8().copyToByteArray()).fieldNames())
                .toIterable()
                .containsExactly(
                        "artifactId",
                        "artifactType",
                        "completion",
                        "controls",
                        "payload",
                        "producer",
                        "schemaVersion",
                        "upstreamArtifacts");
    }

    @Test
    void moduleStoreRejectsAJsonObjectThatHasTheRightGenericIdButAnInvalidModuleCompletion()
            throws Exception {
        CanonicalJsonCodec codec = new CanonicalJsonCodec();
        CanonicalArtifactPolicy policy = modulePolicy();
        ArtifactPolicyRegistryReference registryReference =
                new ArtifactPolicyRegistryReference(
                        "artifact-policy-registry:" + "5".repeat(64), "5".repeat(64));
        CanonicalArtifactPolicyRegistry registry = fixedRegistry(policy, registryReference);
        ModuleArtifactEnvelope valid =
                ModuleArtifactEnvelope.write(
                        new ModuleArtifactEnvelopeDraft(
                                new StageModuleAddress(RUN_ID, 1, "freeze-source", 1, "request-admission"),
                                "v2",
                                List.of(),
                                controls(registryReference),
                                "SUCCEEDED",
                                List.of(),
                                new ObjectMapper().createObjectNode().put("sourceFileCount", 3)),
                        policy);
        ObjectNode malformed =
                (ObjectNode) new ObjectMapper().readTree(valid.canonicalUtf8().copyToByteArray());
        malformed.withObject("completion").put("failureRef", "failure:" + "6".repeat(64));
        malformed.remove("artifactId");
        malformed.put("artifactId", canonicalModuleId(policy, codec, malformed));
        ImmutableBytes malformedBytes = codec.canonicalize(malformed);
        ModuleInstallRequest request =
                new ModuleInstallRequest(
                        new StageModuleAddress(RUN_ID, 1, "freeze-source", 1, "request-admission"),
                        "v2",
                        List.of(),
                        controls(registryReference),
                        "SUCCEEDED",
                        List.of(),
                        List.of(
                                new CanonicalModulePayload(
                                        "request-admission.json",
                                        policy.key().artifactType(),
                                        policy.key().schemaVersion(),
                                        malformed.get("artifactId").textValue(),
                                        "application/json",
                                        malformedBytes)));

        try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
            CanonicalModuleArtifactStore store =
                    new FileSystemCanonicalModuleArtifactStore(
                            handle, codec, registry, new ArtifactStoreLimits(1, 4096, 4096, 4));

            ArtifactStoreException exception =
                    assertThrows(ArtifactStoreException.class, () -> store.install(request));

            assertThat(exception.getMessage()).startsWith("MODULE_PUBLICATION_INVALID:");
        }
    }

    private static ArtifactControls controls() {
        return controls(
                new ArtifactPolicyRegistryReference(
                        "artifact-policy-registry:" + "4".repeat(64), "4".repeat(64)));
    }

    private static ArtifactControls controls(ArtifactPolicyRegistryReference registryReference) {
        return new ArtifactControls(
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                null,
                registryReference);
    }

    private static ArtifactReference reference(String prefix, char fill) {
        String digest = String.valueOf(fill).repeat(64);
        return new ArtifactReference(prefix + ":" + digest, digest);
    }

    private static CanonicalArtifactPolicy modulePolicy() {
        return new CanonicalArtifactPolicy(
                new ArtifactPolicyKey("STAGE01_ADMITTED_SOURCE_REQUEST", "stage01-admitted-source-request-v2"),
                "source-request",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false,
                "METADATA_ONLY");
    }

    private static CanonicalArtifactPolicyRegistry fixedRegistry(
            CanonicalArtifactPolicy policy, ArtifactPolicyRegistryReference reference) {
        return new CanonicalArtifactPolicyRegistry() {
            @Override
            public ArtifactPolicyRegistryReference reference() {
                return reference;
            }

            @Override
            public CanonicalArtifactPolicy resolve(ArtifactPolicyKey key) {
                if (!policy.key().equals(key)) {
                    throw new ArtifactStoreException("ARTIFACT_POLICY_NOT_FOUND", "fixture policy is not registered");
                }
                return policy;
            }
        };
    }

    private static String canonicalModuleId(
            CanonicalArtifactPolicy policy, CanonicalJsonCodec codec, ObjectNode withoutArtifactId) {
        return policy.artifactIdPrefix()
                + ":"
                + ArtifactValues.digest(
                        frame("canonical-module-artifact-id-v1"),
                        frame(policy.key().schemaVersion()),
                        frame(policy.key().artifactType()),
                        frame(codec.canonicalize(withoutArtifactId).copyToByteArray()));
    }

    private static byte[] frame(String value) {
        return frame(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] frame(byte[] value) {
        return ByteBuffer.allocate(Long.BYTES + value.length).putLong(value.length).put(value).array();
    }
}
