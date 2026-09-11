package org.sourceanalysis.app.runtime;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Canonical-store adapter for the closed, publicly queryable business checkpoint file set. */
public final class BusinessCheckpointArtifactReader implements CompletedBusinessArtifactReader {

  private final CanonicalModuleArtifactStore artifacts;

  public BusinessCheckpointArtifactReader(CanonicalModuleArtifactStore artifacts) {
    this.artifacts = Objects.requireNonNull(artifacts, "module artifact store");
  }

  @Override
  public ArtifactView read(
      AnalysisRunId runId,
      AnalysisRunOutput output,
      BusinessOutputArtifactKey businessOutputArtifactKey,
      int maxBytes) {
    if (runId == null || output == null || businessOutputArtifactKey == null || maxBytes <= 0) {
      throw new IllegalArgumentException("business artifact query is invalid");
    }
    ModulePublicationReference checkpoint = businessOutputArtifactKey.checkpoint(output);
    ReopenedModulePublication publication = artifacts.reopen(checkpoint);
    VerifiedCanonicalPayload payload =
        publication.payloads().stream()
            .filter(
                value -> value.descriptor().fileName().equals(businessOutputArtifactKey.fileName()))
            .reduce(
                (left, right) -> {
                  throw new IllegalStateException("BUSINESS_ARTIFACT_QUERY_INVALID");
                })
            .orElseThrow(() -> new IllegalStateException("BUSINESS_ARTIFACT_QUERY_INVALID"));
    if (payload.canonicalUtf8().size() > maxBytes) {
      throw new IllegalStateException("BUSINESS_ARTIFACT_QUERY_BUDGET_EXCEEDED");
    }
    String content = strictUtf8(payload.canonicalUtf8().copyToByteArray());
    return new ArtifactView(
        runId,
        businessOutputArtifactKey,
        new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256()),
        payload.descriptor().schemaVersion(),
        payload.descriptor().mediaType().wireValue(),
        content);
  }

  private static String strictUtf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(java.nio.ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException invalid) {
      throw new IllegalStateException("BUSINESS_ARTIFACT_QUERY_INVALID", invalid);
    }
  }
}
