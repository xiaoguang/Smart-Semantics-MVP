package org.sourceanalysis.app.runtime;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidencePublisher;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
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
    return read(runId, output, technicalArtifactQueryKey, null, maxBytes);
  }

  @Override
  public ArtifactView read(
      AnalysisRunId runId,
      AnalysisRunOutput output,
      TechnicalArtifactQueryKey technicalArtifactQueryKey,
      String entryId,
      int maxBytes) {
    if (runId == null
        || output == null
        || technicalArtifactQueryKey == null
        || maxBytes <= 0
        || output.technicalOutput() == null) {
      throw invalid();
    }
    TechnicalRunOutput technical = output.technicalOutput();
    boolean entryEvidence = isEntryEvidence(technicalArtifactQueryKey);
    if ((requiresEntryId(technicalArtifactQueryKey)
            && (entryId == null || !entryId.matches("entry:[0-9a-f]{64}")))
        || (!requiresEntryId(technicalArtifactQueryKey) && entryId != null)) {
      throw invalid();
    }
    if (entryEvidence
        && (technical.wireVersion() != AnalysisRunRequest.TechnicalWireVersion.V5
            || technical.operation() != AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS
            || technical.readingMaterials() == null)) {
      throw invalid();
    }
    EntryEvidenceReader reader = new EntryEvidenceReader(modules, analysisSteps);
    EntryEvidenceReader.EntryDocument selectedEntry =
        requiresEntryId(technicalArtifactQueryKey)
            ? (isEntryEvidenceV2(technicalArtifactQueryKey)
                ? reader.readV2(technical.readingMaterials(), entryId)
                : reader.read(technical.readingMaterials(), entryId))
            : null;
    if (entryEvidence && selectedEntry == null) {
      // Index and coverage queries still fresh-reopen the complete closure before exposing one
      // member, rather than trusting a filename from the command line.
      if (isEntryEvidenceV2(technicalArtifactQueryKey)) {
        reader.reopenV2(technical.readingMaterials());
      } else {
        reader.reopen(technical.readingMaterials());
      }
    }
    List<VerifiedCanonicalPayload> payloads = payloads(technical, technicalArtifactQueryKey);
    String requiredFile =
        selectedEntry == null
            ? technicalArtifactQueryKey.fileName()
            : EntryEvidencePublisher.entryFileName(selectedEntry.entryId());
    VerifiedCanonicalPayload payload =
        payloads.stream()
            .filter(
                candidate ->
                    candidate.descriptor().fileName().equals(requiredFile)
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
    if (selectedEntry != null && !selectedEntry.canonicalJson().equals(payload.canonicalUtf8())) {
      throw invalid();
    }
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

  private static boolean isEntryEvidence(TechnicalArtifactQueryKey key) {
    return key == TechnicalArtifactQueryKey.ENTRY_EVIDENCE_INDEX
        || key == TechnicalArtifactQueryKey.ENTRY_EVIDENCE
        || key == TechnicalArtifactQueryKey.FRONTEND_EVIDENCE_COVERAGE
        || key == TechnicalArtifactQueryKey.ENTRY_EVIDENCE_INDEX_V2
        || key == TechnicalArtifactQueryKey.ENTRY_EVIDENCE_V2
        || key == TechnicalArtifactQueryKey.FRONTEND_EVIDENCE_COVERAGE_V2;
  }

  private static boolean isEntryEvidenceV2(TechnicalArtifactQueryKey key) {
    return key == TechnicalArtifactQueryKey.ENTRY_EVIDENCE_INDEX_V2
        || key == TechnicalArtifactQueryKey.ENTRY_EVIDENCE_V2
        || key == TechnicalArtifactQueryKey.FRONTEND_EVIDENCE_COVERAGE_V2;
  }

  private static boolean requiresEntryId(TechnicalArtifactQueryKey key) {
    return key == TechnicalArtifactQueryKey.ENTRY_EVIDENCE
        || key == TechnicalArtifactQueryKey.ENTRY_EVIDENCE_V2;
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
