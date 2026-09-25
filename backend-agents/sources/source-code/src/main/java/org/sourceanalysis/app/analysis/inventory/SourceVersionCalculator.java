package org.sourceanalysis.app.analysis.inventory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Pure canonical identities for regular source files and whole prepared source versions. */
public final class SourceVersionCalculator {

  private static final Comparator<String> UTF8_ORDER = SourceVersionCalculator::compareUtf8;

  private SourceVersionCalculator() {}

  /** Calculates the source-file-v2 identity from stable regular-file facts only. */
  public static ArtifactId fileId(
      String relativePath,
      long sizeBytes,
      Sha256Digest sha256,
      SourceOriginAttributes originAttributes) {
    if (sizeBytes < 0L || sha256 == null || originAttributes == null) {
      throw new IllegalArgumentException("source file identity requires complete stable metadata");
    }
    requireLiteralRelativePath(relativePath);
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("format", "source-file-v2");
    identity.put("path", relativePath);
    identity.put("entryKind", SourceEntry.Kind.REGULAR_FILE.name());
    identity.put("sizeBytes", sizeBytes);
    identity.put("sha256", sha256.value());
    identity.set("originAttributes", originAttributes(originAttributes));
    return ArtifactId.parse(
        "source-file:"
            + sha256(new CanonicalJsonCodec().encodeCanonical(identity).copyToByteArray()));
  }

  /**
   * Calculates one immutable source basis without rereading source bytes or diagnostic
   * observations.
   */
  public static ArtifactId sourceVersionId(
      SourcePreparationRequest request, SourcePreparationResult result) {
    Objects.requireNonNull(request, "source-preparation request");
    Objects.requireNonNull(result, "source-preparation result");
    ObjectNode basis = JsonNodeFactory.instance.objectNode();
    basis.put("format", "source-preparation-basis-v1");
    basis.set("origin", origin(request.origin()));
    if (request.basePreparation() == null) {
      basis.putNull("parentSourceVersionId");
    } else {
      basis.put("parentSourceVersionId", request.basePreparation().sourceVersionId().value());
    }
    basis.put("operation", request.operation().name());
    basis.set("targets", targets(request.targets()));
    basis.set("declaredExclusions", targets(request.declaredExclusions()));
    basis.set("effectiveExclusions", targets(request.effectiveExclusions()));
    ObjectNode policy = basis.putObject("policy");
    policy.put("artifactId", request.policyRef().artifactId().value());
    policy.put("sha256", request.policyRef().sha256().value());
    basis.put("inspectionStatus", result.inspectionStatus().name());
    basis.put("enumerationComplete", result.enumerationComplete());
    basis.set("entries", entries(result.entries()));
    basis.set("issues", issues(result.issues()));
    ArrayNode unknownSubtrees = basis.putArray("unknownSubtrees");
    result.unknownSubtrees().stream().sorted(UTF8_ORDER).forEach(unknownSubtrees::add);
    basis.set("unmatchedExclusions", targets(result.unmatchedExclusions()));
    return ArtifactId.parse(
        "snapshot:" + sha256(new CanonicalJsonCodec().encodeCanonical(basis).copyToByteArray()));
  }

  private static ObjectNode origin(SourceOrigin sourceOrigin) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("kind", sourceOrigin.kind().name());
    value.put("logicalIdentity", sourceOrigin.logicalIdentity());
    if (sourceOrigin instanceof GitCommitSourceOrigin gitOrigin) {
      value.put("commitId", gitOrigin.commitId());
    } else {
      value.putNull("commitId");
    }
    return value;
  }

  private static ArrayNode targets(List<SourcePreparationTarget> targets) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    targets.stream()
        .sorted(
            Comparator.comparing(SourcePreparationTarget::relativePath, UTF8_ORDER)
                .thenComparing(target -> target.kind().name(), UTF8_ORDER))
        .forEach(
            target -> {
              ObjectNode value = values.addObject();
              value.put("relativePath", target.relativePath());
              value.put("kind", target.kind().name());
            });
    return values;
  }

  private static ArrayNode entries(List<SourceEntry> entries) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    entries.stream()
        .sorted(Comparator.comparing(SourceEntry::relativePath, UTF8_ORDER))
        .forEach(
            entry -> {
              ObjectNode value = values.addObject();
              value.put("relativePath", entry.relativePath());
              value.put("entryKind", entry.entryKind().name());
              value.put("disposition", entry.disposition().name());
              nullableLong(value, "sizeBytes", entry.sizeBytes());
              nullableDigest(value, "sha256", entry.sha256());
              nullableArtifactId(value, "fileId", entry.fileId());
              nullableText(value, "textEncoding", entry.textEncoding());
              value.set("originAttributes", originAttributes(entry.originAttributes()));
              if (entry.inheritedFrom() == null) {
                value.putNull("inheritedFrom");
              } else {
                ObjectNode inherited = value.putObject("inheritedFrom");
                inherited.put(
                    "baseSourceVersion", entry.inheritedFrom().baseSourceVersion().value());
                nullableArtifactId(inherited, "fileId", entry.inheritedFrom().fileId());
              }
              if (entry.exclusion() == null) {
                value.putNull("exclusion");
              } else {
                ObjectNode exclusion = value.putObject("exclusion");
                exclusion.put("category", entry.exclusion().category());
                exclusion.put("decision", entry.exclusion().decision().name());
                exclusion.put("coveredPath", entry.exclusion().coveredPath());
              }
            });
    return values;
  }

  private static ArrayNode issues(List<SourceIssue> issues) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    issues.stream()
        .sorted(
            Comparator.comparing((SourceIssue issue) -> issue.code().name(), UTF8_ORDER)
                .thenComparing(issue -> issue.category().name(), UTF8_ORDER)
                .thenComparing(issue -> issue.scope().name(), UTF8_ORDER)
                .thenComparing(SourceIssue::relativePath, Comparator.nullsFirst(UTF8_ORDER))
                .thenComparing(issue -> issue.operation().name(), UTF8_ORDER)
                .thenComparing(issue -> issue.resolution().name(), UTF8_ORDER))
        .forEach(
            issue -> {
              ObjectNode value = values.addObject();
              value.put("code", issue.code().name());
              value.put("category", issue.category().name());
              value.put("scope", issue.scope().name());
              nullableText(value, "relativePath", issue.relativePath());
              value.put("operation", issue.operation().name());
              value.put("resolution", issue.resolution().name());
            });
    return values;
  }

  private static ObjectNode originAttributes(SourceOriginAttributes originAttributes) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("kind", originAttributes.kind().name());
    nullableText(value, "gitMode", originAttributes.gitMode());
    nullableText(value, "gitBlobObjectId", originAttributes.gitBlobObjectId());
    if (originAttributes.executable() == null) {
      value.putNull("executable");
    } else {
      value.put("executable", originAttributes.executable());
    }
    return value;
  }

  private static void nullableLong(ObjectNode target, String field, Long value) {
    if (value == null) {
      target.putNull(field);
    } else {
      target.put(field, value);
    }
  }

  private static void nullableDigest(ObjectNode target, String field, Sha256Digest value) {
    if (value == null) {
      target.putNull(field);
    } else {
      target.put(field, value.value());
    }
  }

  private static void nullableArtifactId(ObjectNode target, String field, ArtifactId value) {
    if (value == null) {
      target.putNull(field);
    } else {
      target.put(field, value.value());
    }
  }

  private static void nullableText(ObjectNode target, String field, String value) {
    if (value == null) {
      target.putNull(field);
    } else {
      target.put(field, value);
    }
  }

  private static void requireLiteralRelativePath(String path) {
    SourcePreparationPaths.requireLiteralRelativePath(path, "source file identity path");
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

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
