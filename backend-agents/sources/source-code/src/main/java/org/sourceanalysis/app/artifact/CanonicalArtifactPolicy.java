package org.sourceanalysis.app.artifact;

import java.util.regex.Pattern;

/** One immutable, exact policy governing one artifact type and schema version. */
public record CanonicalArtifactPolicy(
    ArtifactPolicyKey key,
    String artifactIdPrefix,
    CanonicalMediaType mediaType,
    CanonicalEnvelopeKind envelopeKind,
    boolean emptyJsonlAllowed,
    PublicContentExposure publicContentExposure) {

  private static final Pattern ARTIFACT_ID_PREFIX = Pattern.compile("[a-z][a-z0-9-]{0,47}");

  public CanonicalArtifactPolicy {
    if (key == null
        || artifactIdPrefix == null
        || !ARTIFACT_ID_PREFIX.matcher(artifactIdPrefix).matches()
        || mediaType == null
        || envelopeKind == null
        || publicContentExposure == null) {
      throw new IllegalArgumentException("canonical artifact policy has invalid required values");
    }
    requireCompatibleMediaAndEnvelope(mediaType, envelopeKind, emptyJsonlAllowed);
  }

  private static void requireCompatibleMediaAndEnvelope(
      CanonicalMediaType mediaType, CanonicalEnvelopeKind envelopeKind, boolean emptyJsonlAllowed) {
    boolean valid =
        switch (envelopeKind) {
          case MODULE_ARTIFACT_JSON, STANDALONE_JSON ->
              mediaType == CanonicalMediaType.APPLICATION_JSON && !emptyJsonlAllowed;
          case CANONICAL_JSONL -> mediaType == CanonicalMediaType.APPLICATION_X_NDJSON;
          case RAW_UTF8 -> mediaType == CanonicalMediaType.TEXT_MARKDOWN && !emptyJsonlAllowed;
        };
    if (!valid) {
      throw new IllegalArgumentException(
          "canonical artifact policy has an invalid media/envelope combination");
    }
  }
}
