package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Shared typed carrier for one closed, source-located local program-graph Gap. */
public record GraphGapDraft(
    ArtifactId gapId,
    String reasonCode,
    List<ArtifactId> affectedEntryIds,
    List<ArtifactId> candidateElementIds,
    SourceLocatorV1 sourceLocator) {

  public GraphGapDraft {
    Objects.requireNonNull(gapId, "graph gap ID");
    if (reasonCode == null || reasonCode.isBlank()) {
      throw new IllegalArgumentException("graph gap reason is required");
    }
    affectedEntryIds = ordered(affectedEntryIds, "graph gap entries");
    candidateElementIds = ordered(candidateElementIds, "graph gap candidates");
    if (candidateElementIds.isEmpty()) {
      throw new IllegalArgumentException("graph gap must affect a candidate");
    }
    Objects.requireNonNull(sourceLocator, "graph gap source locator");
  }

  /** Creates one local Gap with the approved, versioned, framed identity. */
  public static GraphGapDraft forLocalOccurrence(
      ProgramGraphKind graphKind,
      String reasonCode,
      List<ArtifactId> affectedEntryIds,
      List<ArtifactId> candidateElementIds,
      SourceLocatorV1 sourceLocator) {
    Objects.requireNonNull(graphKind, "graph gap graph kind");
    if (reasonCode == null || reasonCode.isBlank()) {
      throw new IllegalArgumentException("graph gap reason is required");
    }
    List<ArtifactId> orderedEntries = ordered(affectedEntryIds, "graph gap entries");
    List<ArtifactId> orderedCandidates = ordered(candidateElementIds, "graph gap candidates");
    if (orderedEntries.isEmpty() && graphKind != ProgramGraphKind.CODE_STRUCTURE) {
      throw new IllegalArgumentException("graph gap must affect an entry");
    }
    if (orderedCandidates.isEmpty()) {
      throw new IllegalArgumentException("graph gap must affect a candidate");
    }
    Objects.requireNonNull(sourceLocator, "graph gap source locator");
    return new GraphGapDraft(
        ArtifactId.parse(
            "graph-gap:"
                + sha256(
                    concatenate(
                        frame("program-graph-local-gap-id-v1"),
                        frame(
                            identityMaterial(
                                graphKind,
                                reasonCode,
                                orderedEntries,
                                orderedCandidates,
                                sourceLocator))))),
        reasonCode,
        orderedEntries,
        orderedCandidates,
        sourceLocator);
  }

  static void requireIdentity(ProgramGraphKind graphKind, GraphGapDraft draft) {
    Objects.requireNonNull(graphKind, "graph gap graph kind");
    Objects.requireNonNull(draft, "graph gap draft");
    ArtifactId expected =
        ArtifactId.parse(
            "graph-gap:"
                + sha256(
                    concatenate(
                        frame("program-graph-local-gap-id-v1"),
                        frame(
                            identityMaterial(
                                graphKind,
                                draft.reasonCode(),
                                draft.affectedEntryIds(),
                                draft.candidateElementIds(),
                                draft.sourceLocator())))));
    if (!expected.equals(draft.gapId())) {
      throw new IllegalArgumentException("graph gap identity is invalid");
    }
  }

  private static List<ArtifactId> ordered(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static byte[] identityMaterial(
      ProgramGraphKind graphKind,
      String reasonCode,
      List<ArtifactId> affectedEntryIds,
      List<ArtifactId> candidateElementIds,
      SourceLocatorV1 sourceLocator) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("graphKind", graphKind.name());
    material.put("reasonCode", reasonCode);
    ids(material.putArray("affectedEntryIds"), affectedEntryIds);
    ids(material.putArray("candidateElementIds"), candidateElementIds);
    ObjectNode locator = material.putObject("sourceLocator");
    locator.put("fileId", sourceLocator.fileId().value());
    locator.put("path", sourceLocator.path());
    locator.put("startByte", sourceLocator.startByte());
    locator.put("endByteExclusive", sourceLocator.endByteExclusive());
    locator.put("startLine", sourceLocator.startLine());
    locator.put("startColumn", sourceLocator.startColumn());
    locator.put("endLine", sourceLocator.endLine());
    locator.put("endColumn", sourceLocator.endColumn());
    return new CanonicalJsonCodec().encodeCanonical(material).copyToByteArray();
  }

  private static void ids(ArrayNode target, List<ArtifactId> values) {
    values.forEach(value -> target.add(value.value()));
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
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }
}
