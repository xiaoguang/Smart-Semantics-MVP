package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

class ControlFlowGraphGapCarrierTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void carriesAnUnsupportedLoopGapThroughV4IdentityAndBidirectionalCoverage() {
    ControlFlowGraphDraft first;
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithStatusLoop(temporaryDirectory)) {
      first = build(fixture);

      assertThat(first.schemaVersion()).isEqualTo("program-graphs-control-flow-draft-v4");
      assertThat(first.gapDrafts()).singleElement();
      GraphGapDraft gap = first.gapDrafts().get(0);
      assertThat(gap.reasonCode()).isEqualTo("LOOP_SLICE_NOT_INSTALLED");
      assertThat(gap.affectedEntryIds()).containsExactly(fixture.entryId());
      assertThat(gap.candidateElementIds()).hasSize(1);

      assertThat(first.terminalDispositions()).singleElement();
      ControlFlowTerminalDisposition terminal = first.terminalDispositions().get(0);
      ArtifactId candidate = gap.candidateElementIds().get(0);
      assertThat(candidate).isEqualTo(profileStopCandidate(terminal.terminalNodeId()));
      assertThat(gap.gapId()).isEqualTo(graphGapId(gap));
      assertThat(terminal.dispositionKind()).isEqualTo(ControlFlowTerminalDispositionKind.GAP);
      assertThat(terminal.candidateElementId()).isEqualTo(candidate);
      assertThat(terminal.gapId()).isEqualTo(gap.gapId());
      assertThat(first.coverage().gapDispositions())
          .singleElement()
          .satisfies(
              disposition -> {
                assertThat(disposition.candidateElementId()).isEqualTo(candidate);
                assertThat(disposition.gapId()).isEqualTo(gap.gapId());
              });
      assertThat(first.coverage().gapDispositions())
          .extracting(GraphGapDisposition::candidateElementId)
          .containsExactlyElementsOf(gap.candidateElementIds());

      assertThat(gap.sourceLocator().path())
          .isEqualTo("src/main/java/com/example/DepotHeadService.java");
      CodeStructureSourceDocument sourceDocument =
          fixture.reopenedInputs().source().documents().stream()
              .filter(document -> document.path().equals(gap.sourceLocator().path()))
              .findFirst()
              .orElseThrow();
      assertThat(gap.sourceLocator().fileId()).isEqualTo(sourceDocument.fileId());
      byte[] sourceBytes = sourceDocument.rawUtf8().copyToByteArray();
      String locatedText =
          new String(
              Arrays.copyOfRange(
                  sourceBytes,
                  Math.toIntExact(gap.sourceLocator().startByte()),
                  Math.toIntExact(gap.sourceLocator().endByteExclusive())),
              StandardCharsets.UTF_8);
      assertThat(locatedText).contains("while (status != null)");

      ControlFlowGraphDraft second = build(fixture);
      assertThat(second).isEqualTo(first);
      assertThat(second.graphId()).isEqualTo(first.graphId());

      GraphGapDraft changedReasonGap =
          GraphGapDraft.forLocalOccurrence(
              ProgramGraphKind.CONTROL_FLOW,
              "LOOP_SLICE_NOT_INSTALLED_ALT",
              gap.affectedEntryIds(),
              gap.candidateElementIds(),
              gap.sourceLocator());
      assertThat(changedReasonGap.gapId())
          .as("changing the carrier reason must change carrier identity")
          .isNotEqualTo(gap.gapId());
    }
  }

  private static ControlFlowGraphDraft build(ControlFlowGraphBuilderTest.Fixture fixture) {
    return new ControlFlowGraphBuilder()
        .buildControlFlow(
            new ControlFlowInputs(fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
            new ControlFlowGraphProfile(fixture.graphProfileRef()));
  }

  private static ArtifactId profileStopCandidate(ArtifactId terminalNodeId) {
    return ArtifactId.parse(
        "control-flow-profile-stop-successor-v1:"
            + sha256(
                concatenate(
                    frame("control-flow-profile-stop-successor-v1"),
                    frame(terminalNodeId.value()))));
  }

  private static ArtifactId graphGapId(GraphGapDraft gap) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("graphKind", "CONTROL_FLOW");
    material.put("reasonCode", gap.reasonCode());
    ArrayNode entries = material.putArray("affectedEntryIds");
    gap.affectedEntryIds().forEach(entry -> entries.add(entry.value()));
    ArrayNode candidates = material.putArray("candidateElementIds");
    gap.candidateElementIds().forEach(candidate -> candidates.add(candidate.value()));
    ObjectNode locator = material.putObject("sourceLocator");
    locator.put("fileId", gap.sourceLocator().fileId().value());
    locator.put("path", gap.sourceLocator().path());
    locator.put("startByte", gap.sourceLocator().startByte());
    locator.put("endByteExclusive", gap.sourceLocator().endByteExclusive());
    locator.put("startLine", gap.sourceLocator().startLine());
    locator.put("startColumn", gap.sourceLocator().startColumn());
    locator.put("endLine", gap.sourceLocator().endLine());
    locator.put("endColumn", gap.sourceLocator().endColumn());
    byte[] canonicalMaterial = new CanonicalJsonCodec().encodeCanonical(material).copyToByteArray();
    return ArtifactId.parse(
        "graph-gap:"
            + sha256(
                concatenate(frame("program-graph-local-gap-id-v1"), frame(canonicalMaterial))));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = Arrays.stream(values).mapToInt(value -> value.length).sum();
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }
}
