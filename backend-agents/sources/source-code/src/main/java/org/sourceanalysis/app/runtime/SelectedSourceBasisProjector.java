package org.sourceanalysis.app.runtime;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.InventoryScope;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadinessEvaluator;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;
import org.sourceanalysis.app.analysis.inventory.SourceVersionCalculator;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;

/** Projects an already-verified source and its exact effective scope into an analysis-run basis. */
public final class SelectedSourceBasisProjector {

  private static final String FORMAT = "selected-source-effective-scope-v1";
  private static final Comparator<String> UTF8_ORDER = SelectedSourceBasisProjector::compareUtf8;

  private SelectedSourceBasisProjector() {}

  /** Selects a fresh-reopened prepared source only when its full report is ready for reading. */
  public static SelectedSourceBasis fromPrepared(SavedSourcePreparation saved) {
    Objects.requireNonNull(saved, "saved source preparation");
    SourcePreparationReadiness readiness = saved.assessment().readiness();
    if (readiness != SourcePreparationReadiness.READY
        && readiness != SourcePreparationReadiness.READY_WITH_EXCLUSIONS) {
      throw new IllegalArgumentException("SOURCE_PREPARATION_NOT_READY");
    }
    PreparedSourceReference source = saved.sourceVersionReference();
    if (source == null
        || !source.publication().equals(saved.reportReference())
        || !saved.assessment().equals(SourcePreparationReadinessEvaluator.assess(saved.result()))
        || !source
            .sourceVersionId()
            .equals(SourceVersionCalculator.sourceVersionId(saved.request(), saved.result()))) {
      throw new IllegalArgumentException("SOURCE_PREPARATION_REFERENCE_INVALID");
    }
    InventoryScope.Kind scopeKind =
        readiness == SourcePreparationReadiness.READY
            ? InventoryScope.Kind.COMPLETE_CAPTURE
            : InventoryScope.Kind.BOUNDED_PATH_SET;
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.PREPARED_V1,
        source,
        null,
        source.sourceVersionId(),
        scopeDigest(scopeKind, null, saved.request().effectiveExclusions()));
  }

  /** Selects an exact reopened legacy registration and its separately verified frozen scope. */
  public static SelectedSourceBasis fromLegacy(
      SourceRegistrationReference capture, InventoryScope verifiedFrozenScope) {
    Objects.requireNonNull(capture, "legacy source capture");
    Objects.requireNonNull(verifiedFrozenScope, "verified frozen inventory scope");
    ArtifactId snapshotId = ArtifactId.parse(capture.snapshotId());
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.LEGACY_CAPTURE_V1,
        null,
        capture,
        snapshotId,
        scopeDigest(verifiedFrozenScope.kind(), verifiedFrozenScope.scopeRoot(), List.of()));
  }

  private static Sha256Digest scopeDigest(
      InventoryScope.Kind scopeKind, String scopeRoot, List<SourcePreparationTarget> exclusions) {
    ObjectNode scope = JsonNodeFactory.instance.objectNode();
    scope.put("format", FORMAT);
    scope.put("scopeKind", scopeKind.name());
    if (scopeRoot == null) {
      scope.putNull("scopeRoot");
    } else {
      scope.put("scopeRoot", scopeRoot);
    }
    ArrayNode effectiveExclusions = scope.putArray("effectiveExclusions");
    exclusions.stream()
        .sorted(
            Comparator.comparing(SourcePreparationTarget::relativePath, UTF8_ORDER)
                .thenComparing(target -> target.kind().name(), UTF8_ORDER))
        .forEach(
            target -> {
              ObjectNode item = effectiveExclusions.addObject();
              item.put("relativePath", target.relativePath());
              item.put("kind", target.kind().name());
            });
    byte[] canonicalJson = new CanonicalJsonCodec().encodeCanonical(scope).copyToByteArray();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(frame(FORMAT.getBytes(StandardCharsets.UTF_8)));
      digest.update(frame(canonicalJson));
      return Sha256Digest.parse(HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static byte[] frame(byte[] bytes) {
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }
}
