package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CanonicalJsonCodecTest {

  @Test
  void encodesCanonicalObjectWithUtf8ByteOrderedKeys() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode value = mapper.createObjectNode();

    ArrayNode orderedArray = mapper.createArrayNode();
    orderedArray.add("first");
    orderedArray.add(7);
    orderedArray.add(false);

    ObjectNode nestedObject = mapper.createObjectNode();
    nestedObject.put("b", 2);
    nestedObject.put("a", 1);
    nestedObject.put("ñ", "sí");

    value.set("zeta", orderedArray);
    value.put("é", "café");
    value.putNull("nothing");
    value.put("unsigned", 42);
    value.put("signed", -17);
    value.set("nested", nestedObject);

    ImmutableBytes encoded = new CanonicalJsonCodec().encodeCanonical(value);
    byte[] actual = encoded.copyToByteArray();

    // Hand-authored canonical compact JSON: UTF-8, no BOM, and no final LF.
    byte[] expected =
        "{\"nested\":{\"a\":1,\"b\":2,\"ñ\":\"sí\"},\"nothing\":null,\"signed\":-17,\"unsigned\":42,\"zeta\":[\"first\",7,false],\"é\":\"café\"}"
            .getBytes(StandardCharsets.UTF_8);

    assertThat(actual).containsExactly(expected);
    assertThat(actual[actual.length - 1]).isNotEqualTo((byte) '\n');
    assertThat(actual[0]).isNotEqualTo((byte) 0xef);
  }

  @Test
  void parseCanonicalRejectsOtherwiseValidJsonWithInsignificantWhitespace() {
    ImmutableBytes nonCanonical =
        ImmutableBytes.copyOf("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> new CanonicalJsonCodec().parseCanonical(nonCanonical))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
