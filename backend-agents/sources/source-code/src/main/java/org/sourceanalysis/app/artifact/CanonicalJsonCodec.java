package org.sourceanalysis.app.artifact;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Encodes complete JSON values as the project's compact canonical UTF-8 representation. */
public final class CanonicalJsonCodec {

  private final ObjectReader strictCanonicalReader;

  /** Creates a codec with the fixed canonical JSON rules. */
  public CanonicalJsonCodec() {
    JsonFactory factory =
        JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    strictCanonicalReader =
        new ObjectMapper(factory).reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  }

  /**
   * Encodes {@code value} as compact UTF-8 without a byte-order mark, insignificant whitespace, or
   * final line feed.
   *
   * @throws IllegalArgumentException when a value is not representable by the canonical JSON rules
   */
  public ImmutableBytes encodeCanonical(JsonNode value) {
    Objects.requireNonNull(value, "value");

    StringBuilder encoded = new StringBuilder();
    appendCanonical(value, encoded);
    return ImmutableBytes.copyOf(encoded.toString().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Parses canonical JSON bytes after proving that this codec reproduces those exact bytes.
   *
   * @throws IllegalArgumentException when the bytes are malformed, noncanonical, or not one JSON
   *     value
   */
  public JsonNode parseCanonical(ImmutableBytes canonicalUtf8) {
    Objects.requireNonNull(canonicalUtf8, "canonicalUtf8");

    byte[] input = canonicalUtf8.copyToByteArray();
    rejectByteOrderMark(input);
    requireStrictUtf8(input);

    JsonNode parsed;
    try {
      parsed = strictCanonicalReader.readTree(input);
    } catch (IOException exception) {
      throw new IllegalArgumentException("canonical JSON cannot be parsed", exception);
    }
    if (parsed == null) {
      throw new IllegalArgumentException("canonical JSON must contain one value");
    }

    if (!Arrays.equals(input, encodeCanonical(parsed).copyToByteArray())) {
      throw new IllegalArgumentException("JSON bytes are not canonical");
    }
    return parsed;
  }

  private void rejectByteOrderMark(byte[] input) {
    if (input.length >= 3
        && Byte.toUnsignedInt(input[0]) == 0xef
        && Byte.toUnsignedInt(input[1]) == 0xbb
        && Byte.toUnsignedInt(input[2]) == 0xbf) {
      throw new IllegalArgumentException("canonical JSON must not contain a byte-order mark");
    }
  }

  private void requireStrictUtf8(byte[] input) {
    try {
      StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(input));
    } catch (CharacterCodingException exception) {
      throw new IllegalArgumentException("canonical JSON must be strict UTF-8", exception);
    }
  }

  private void appendCanonical(JsonNode value, StringBuilder encoded) {
    if (value.isObject()) {
      appendObject(value, encoded);
    } else if (value.isArray()) {
      appendArray(value, encoded);
    } else if (value.isTextual()) {
      appendString(value.textValue(), encoded);
    } else if (value.isIntegralNumber()) {
      encoded.append(value.bigIntegerValue());
    } else if (value.isBoolean()) {
      encoded.append(value.booleanValue());
    } else if (value.isNull()) {
      encoded.append("null");
    } else {
      throw new IllegalArgumentException("value is not a canonical JSON value");
    }
  }

  private void appendObject(JsonNode value, StringBuilder encoded) {
    List<ObjectField> fields = new ArrayList<>();
    value
        .fields()
        .forEachRemaining(
            field ->
                fields.add(
                    new ObjectField(field.getKey(), field.getValue(), utf8Bytes(field.getKey()))));
    fields.sort(
        Comparator.comparing(ObjectField::utf8Name, CanonicalJsonCodec::compareUnsignedBytes));

    encoded.append('{');
    for (int index = 0; index < fields.size(); index++) {
      if (index > 0) {
        encoded.append(',');
      }
      ObjectField field = fields.get(index);
      appendString(field.name(), encoded);
      encoded.append(':');
      appendCanonical(field.value(), encoded);
    }
    encoded.append('}');
  }

  private void appendArray(JsonNode value, StringBuilder encoded) {
    encoded.append('[');
    for (int index = 0; index < value.size(); index++) {
      if (index > 0) {
        encoded.append(',');
      }
      appendCanonical(value.get(index), encoded);
    }
    encoded.append(']');
  }

  private void appendString(String value, StringBuilder encoded) {
    requireUnicodeScalars(value);
    encoded.append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"' -> encoded.append("\\\"");
        case '\\' -> encoded.append("\\\\");
        case '\b' -> encoded.append("\\b");
        case '\t' -> encoded.append("\\t");
        case '\n' -> encoded.append("\\n");
        case '\f' -> encoded.append("\\f");
        case '\r' -> encoded.append("\\r");
        default -> {
          if (character <= 0x001f) {
            appendControlEscape(character, encoded);
          } else {
            encoded.append(character);
          }
        }
      }
    }
    encoded.append('"');
  }

  private byte[] utf8Bytes(String value) {
    requireUnicodeScalars(value);
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private void requireUnicodeScalars(String value) {
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (Character.isHighSurrogate(character)) {
        if (index + 1 == value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          throw new IllegalArgumentException("string contains an unpaired surrogate");
        }
        index++;
      } else if (Character.isLowSurrogate(character)) {
        throw new IllegalArgumentException("string contains an unpaired surrogate");
      }
    }
  }

  private void appendControlEscape(char character, StringBuilder encoded) {
    encoded.append("\\u00");
    encoded.append(Character.forDigit((character >>> 4) & 0x0f, 16));
    encoded.append(Character.forDigit(character & 0x0f, 16));
  }

  private static int compareUnsignedBytes(byte[] first, byte[] second) {
    int commonLength = Math.min(first.length, second.length);
    for (int index = 0; index < commonLength; index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(first.length, second.length);
  }

  private record ObjectField(String name, JsonNode value, byte[] utf8Name) {}
}
