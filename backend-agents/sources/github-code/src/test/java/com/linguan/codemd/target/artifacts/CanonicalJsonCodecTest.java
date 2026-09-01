package com.linguan.codemd.target.artifacts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Contract tests for DESIGN §13.3 canonical JSON bytes. */
class CanonicalJsonCodecTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void canonicalizesNestedObjectKeysWithoutReorderingArrays() throws Exception {
        JsonNode value = JSON.readTree("""
                {"z":[{"value":"第二","id":"second"},{"value":"第一","id":"first"}],
                 "text":"mañana", "a":{"z":2,"a":1}}
                """);

        ImmutableBytes canonical = new CanonicalJsonCodec().canonicalize(value);

        assertArrayEquals(
                "{\"a\":{\"a\":1,\"z\":2},\"text\":\"mañana\",\"z\":[{\"id\":\"second\",\"value\":\"第二\"},{\"id\":\"first\",\"value\":\"第一\"}]}"
                        .getBytes(StandardCharsets.UTF_8),
                canonical.copyToByteArray());
    }
}
