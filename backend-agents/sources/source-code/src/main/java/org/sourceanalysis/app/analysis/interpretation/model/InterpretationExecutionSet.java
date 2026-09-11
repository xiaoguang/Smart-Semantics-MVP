package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.List;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Complete M5 result: actual calls plus a final disposition for every eligible Flow. */
public record InterpretationExecutionSet(
    String executionSetId,
    String flowTaskSetId,
    ModulePublicationReference registryProposalExecutionRef,
    ModulePublicationReference registryPublicationRef,
    ModulePublicationReference flowTaskSetPublicationRef,
    List<ModelRound> rounds,
    List<GenerationReceipt> generationReceipts,
    List<FlowInterpretationCandidate> candidates,
    List<ModelTaskDisposition> modelTaskDispositions,
    List<FlowInterpretationDisposition> flowDispositions) {}
