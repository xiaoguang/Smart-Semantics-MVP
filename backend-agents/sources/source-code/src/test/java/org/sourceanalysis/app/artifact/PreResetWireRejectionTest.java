package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Defines the fail-closed boundary between the current and pre-reset analysis wire formats. */
class PreResetWireRejectionTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @ParameterizedTest(name = "{0} is not a current analysis descriptor")
  @MethodSource("preResetDescriptors")
  void rejectsPreResetAndUnknownDescriptorsWithOneStableCode(
      String description, JsonNode descriptor) {
    AnalysisWireFormatGuard guard = new AnalysisWireFormatGuard();

    assertThatThrownBy(() -> guard.requireCurrentWire(descriptor))
        .isInstanceOf(UnsupportedAnalysisWireException.class)
        .extracting(exception -> ((UnsupportedAnalysisWireException) exception).code())
        .isEqualTo("UNSUPPORTED_ANALYSIS_WIRE");
  }

  @Test
  void acceptsCurrentEnvelopeHeaderWithAdditionalDescriptorFields() {
    JsonNode descriptor =
        json(
            "{"
                + "\"wireKind\":\"SOURCE_ANALYSIS\","
                + "\"wireVersion\":\"v1\","
                + "\"artifactType\":\"SOURCE_INPUT\","
                + "\"schemaVersion\":\"source-input-v1\""
                + "}");
    AnalysisWireFormatGuard guard = new AnalysisWireFormatGuard();

    assertThatCode(() -> guard.requireCurrentWire(descriptor)).doesNotThrowAnyException();
  }

  static Stream<Arguments> preResetDescriptors() {
    return Stream.of(
        Arguments.of(
            "old stages directory marker",
            json(
                "{"
                    + "\"path\":\"runs/run-1/stages/01-source\","
                    + "\"artifactType\":\"STAGE01_SOURCE_INPUT\""
                    + "}")),
        Arguments.of(
            "old numeric step key",
            json(
                "{"
                    + "\"analysisStepKey\":\"stage01\","
                    + "\"schemaVersion\":\"stage01-source-v1\""
                    + "}")),
        Arguments.of(
            "old receipt filename",
            json(
                "{"
                    + "\"receiptFile\":\"stage-receipt.json\","
                    + "\"artifactType\":\"STAGE_RECEIPT\""
                    + "}")),
        Arguments.of(
            "old schema version",
            json(
                "{"
                    + "\"wireKind\":\"SOURCE_ANALYSIS\","
                    + "\"wireVersion\":\"v0\","
                    + "\"schemaVersion\":\"stage-receipt-v1\""
                    + "}")),
        Arguments.of(
            "old Maven and package identity",
            json(
                "{"
                    + "\"groupId\":\"com.linguan\","
                    + "\"artifactId\":\"github-code-to-markdown\","
                    + "\"javaPackage\":\"com.linguan.codemd\""
                    + "}")),
        Arguments.of(
            "unknown wire alias",
            json("{" + "\"wireKind\":\"SOURCE_CODE_ANALYSIS\"," + "\"wireVersion\":\"v1\"" + "}")));
  }

  static Stream<Arguments> currentHeaderWithPreResetDiscriminators() {
    return Stream.of(
        Arguments.of(
            "current header with old stages directory marker",
            json(
                "{"
                    + "\"wireKind\":\"SOURCE_ANALYSIS\","
                    + "\"wireVersion\":\"v1\","
                    + "\"path\":\"runs/run-1/stages/01-source\","
                    + "\"artifactType\":\"STAGE01_SOURCE_INPUT\""
                    + "}")),
        Arguments.of(
            "current header with old numeric step key",
            json(
                "{"
                    + "\"wireKind\":\"SOURCE_ANALYSIS\","
                    + "\"wireVersion\":\"v1\","
                    + "\"analysisStepKey\":\"stage01\","
                    + "\"schemaVersion\":\"stage01-source-v1\""
                    + "}")),
        Arguments.of(
            "current header with old receipt filename",
            json(
                "{"
                    + "\"wireKind\":\"SOURCE_ANALYSIS\","
                    + "\"wireVersion\":\"v1\","
                    + "\"receiptFile\":\"stage-receipt.json\","
                    + "\"artifactType\":\"STAGE_RECEIPT\""
                    + "}")),
        Arguments.of(
            "current header with old schema version",
            json(
                "{"
                    + "\"wireKind\":\"SOURCE_ANALYSIS\","
                    + "\"wireVersion\":\"v1\","
                    + "\"schemaVersion\":\"stage-receipt-v1\""
                    + "}")),
        Arguments.of(
            "current header with old Maven and package identity",
            json(
                "{"
                    + "\"wireKind\":\"SOURCE_ANALYSIS\","
                    + "\"wireVersion\":\"v1\","
                    + "\"groupId\":\"com.linguan\","
                    + "\"artifactId\":\"github-code-to-markdown\","
                    + "\"javaPackage\":\"com.linguan.codemd\""
                    + "}")),
        Arguments.of(
            "current header with unknown wire alias",
            json(
                "{"
                    + "\"wireKind\":\"SOURCE_ANALYSIS\","
                    + "\"wireVersion\":\"v1\","
                    + "\"wireAlias\":\"SOURCE_CODE_ANALYSIS\""
                    + "}")));
  }

  @ParameterizedTest(name = "{0} remains unsupported even with the current envelope")
  @MethodSource("currentHeaderWithPreResetDiscriminators")
  void rejectsPreResetDiscriminatorsWhenWrappedByCurrentHeader(
      String description, JsonNode descriptor) {
    AnalysisWireFormatGuard guard = new AnalysisWireFormatGuard();

    assertThatThrownBy(() -> guard.requireCurrentWire(descriptor))
        .isInstanceOf(UnsupportedAnalysisWireException.class)
        .extracting(exception -> ((UnsupportedAnalysisWireException) exception).code())
        .isEqualTo("UNSUPPORTED_ANALYSIS_WIRE");
  }

  private static JsonNode json(String source) {
    try {
      return JSON.readTree(source);
    } catch (Exception exception) {
      throw new AssertionError("test descriptor must be valid JSON", exception);
    }
  }
}
