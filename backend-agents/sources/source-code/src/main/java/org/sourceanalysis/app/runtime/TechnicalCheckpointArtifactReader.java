package org.sourceanalysis.app.runtime;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Canonical-store adapter for the closed public technical artifact-file set. */
public final class TechnicalCheckpointArtifactReader implements CompletedTechnicalArtifactReader {

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;

  public TechnicalCheckpointArtifactReader(
      CanonicalModuleArtifactStore modules, CanonicalAnalysisStepArtifactStore analysisSteps) {
    this.modules = Objects.requireNonNull(modules, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
  }

  @Override
  public ArtifactView read(
      AnalysisRunId runId,
      AnalysisRunOutput output,
      TechnicalArtifactQueryKey technicalArtifactQueryKey,
      int maxBytes) {
    if (runId == null
        || output == null
        || technicalArtifactQueryKey == null
        || maxBytes <= 0
        || output.technicalOutput() == null) {
      throw invalid();
    }
    TechnicalRunOutput technical = output.technicalOutput();
    List<VerifiedCanonicalPayload> payloads = payloads(technical, technicalArtifactQueryKey);
    VerifiedCanonicalPayload payload =
        payloads.stream()
            .filter(
                candidate ->
                    candidate.descriptor().fileName().equals(technicalArtifactQueryKey.fileName())
                        && candidate
                            .descriptor()
                            .artifactType()
                            .equals(technicalArtifactQueryKey.artifactType())
                        && candidate
                            .descriptor()
                            .schemaVersion()
                            .equals(technicalArtifactQueryKey.schemaVersion()))
            .reduce(
                (left, right) -> {
                  throw invalid();
                })
            .orElseThrow(TechnicalCheckpointArtifactReader::invalid);
    if (payload.canonicalUtf8().size() > maxBytes) {
      throw new IllegalStateException("TECHNICAL_ARTIFACT_QUERY_BUDGET_EXCEEDED");
    }
    return ArtifactView.technical(
        runId,
        technicalArtifactQueryKey,
        new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256()),
        payload.descriptor().schemaVersion(),
        payload.descriptor().mediaType().wireValue(),
        strictUtf8(payload.canonicalUtf8().copyToByteArray()));
  }

  private List<VerifiedCanonicalPayload> payloads(
      TechnicalRunOutput technical, TechnicalArtifactQueryKey key) {
    return switch (key.publicationSource()) {
      case READINESS_REPORT ->
          modulePayloads(
              technical.readinessReport(),
              AnalysisStepKey.APPLICATION_DISCOVERY,
              5,
              "java-analysis-readiness");
      case FRONTEND_INDEX ->
          modulePayloads(
              technical.frontendIndex(),
              AnalysisStepKey.APPLICATION_DISCOVERY,
              6,
              "frontend-http-discovery");
      case APPLICATION_DISCOVERY ->
          stepPayloads(technical.applicationDiscovery(), AnalysisStepKey.APPLICATION_DISCOVERY);
      case NAVIGATION -> stepPayloads(technical.navigation(), AnalysisStepKey.PROGRAM_GRAPHS);
      case PERSISTENCE -> stepPayloads(technical.persistence(), AnalysisStepKey.PROVEN_CODE_FACTS);
      case READING_MATERIALS ->
          stepPayloads(technical.readingMaterials(), AnalysisStepKey.BUSINESS_FLOWS);
    };
  }

  private List<VerifiedCanonicalPayload> modulePayloads(
      ModulePublicationReference reference,
      AnalysisStepKey expectedStep,
      int expectedModuleNumber,
      String expectedModuleKey) {
    if (!(reference != null && reference.address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != expectedStep
        || address.moduleNumber() != expectedModuleNumber
        || !expectedModuleKey.equals(address.moduleKey())) {
      throw invalid();
    }
    return modules.reopen(reference).payloads();
  }

  private List<VerifiedCanonicalPayload> stepPayloads(
      AnalysisStepPublicationReference reference, AnalysisStepKey expectedStep) {
    if (reference == null
        || reference.address() == null
        || reference.address().analysisStepKey() != expectedStep) {
      throw invalid();
    }
    return analysisSteps.reopen(reference).semanticPayloads();
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
      throw new IllegalStateException("TECHNICAL_ARTIFACT_QUERY_INVALID", invalid);
    }
  }

  private static IllegalStateException invalid() {
    return new IllegalStateException("TECHNICAL_ARTIFACT_QUERY_INVALID");
  }
}
