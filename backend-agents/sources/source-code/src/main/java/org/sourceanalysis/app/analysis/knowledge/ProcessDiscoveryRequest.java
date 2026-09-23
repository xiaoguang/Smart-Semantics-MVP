package org.sourceanalysis.app.analysis.knowledge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityPacketCompletion;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Immutable reviewed-activity and saved-source input for one process-discovery execution. */
public record ProcessDiscoveryRequest(
    ActivityExplanationResult activities,
    BusinessMaterialBuildResult materials,
    CodeReadingMaterialSet codeReadingMaterials,
    AnalysisStepPublicationReference codeReadingMaterialCheckpoint,
    ProcessDiscoveryProfile profile,
    AnalysisRunId outputRunId,
    VerifiedSourceInventoryReference sourceInventoryReference,
    VerifiedSourceTextReader sourceTextReader,
    ImmutableBytes savedCatalogInput,
    String focusQuestion) {

  public ProcessDiscoveryRequest {
    activities = Objects.requireNonNull(activities, "reviewed activities");
    profile = Objects.requireNonNull(profile, "process discovery profile");
    outputRunId = Objects.requireNonNull(outputRunId, "process output run ID");
    if ((sourceInventoryReference == null) != (sourceTextReader == null)) {
      throw new IllegalArgumentException(
          "process source inventory reference and text reader must be supplied together");
    }
    boolean legacy = materials != null;
    boolean step05 = codeReadingMaterials != null && codeReadingMaterialCheckpoint != null;
    if (legacy == step05
        || (codeReadingMaterials == null) != (codeReadingMaterialCheckpoint == null)) {
      throw new IllegalArgumentException("PROCESS_DISCOVERY_MATERIAL_SOURCE_INVALID");
    }
    String expectedActivitySource = legacy ? "BUSINESS_MATERIALS" : "CODE_READING_MATERIALS";
    if (activities.reviewedActivities().stream()
        .anyMatch(activity -> !expectedActivitySource.equals(activity.materialSource()))) {
      throw new IllegalArgumentException("PROCESS_DISCOVERY_ACTIVITY_MATERIAL_SOURCE_MISMATCH");
    }
    if (step05) {
      if (codeReadingMaterialCheckpoint.address().analysisStepKey()
              != AnalysisStepKey.BUSINESS_FLOWS
          || sourceInventoryReference == null
          || !sourceInventoryReference.equals(codeReadingMaterials.header().sourceInventory())) {
        throw new IllegalArgumentException("PROCESS_DISCOVERY_STEP05_INPUT_INVALID");
      }
      requireCompletePacketCompletion(activities, codeReadingMaterials);
      Map<String, CodeReadingMaterialSet.CoverageStatus> sourceCoverage =
          codeReadingMaterials.coverage().stream()
              .collect(
                  java.util.stream.Collectors.toUnmodifiableMap(
                      CodeReadingMaterialSet.EntryCoverage::entryId,
                      CodeReadingMaterialSet.EntryCoverage::status));
      if (!activities.unexplainedActivityEntries().isEmpty()
          || activities.coverage().stream()
              .anyMatch(
                  coverage ->
                      sourceCoverage.get(coverage.entryId())
                              != CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED
                          && ("NOT_ANALYZED".equals(coverage.disposition())
                              || coverage.requiredScopeIncomplete()))
          || !activities.coverage().stream()
              .map(coverage -> coverage.entryId())
              .collect(java.util.stream.Collectors.toUnmodifiableSet())
              .equals(sourceCoverage.keySet())) {
        throw new IllegalArgumentException("PROCESS_DISCOVERY_PARTIAL_ACTIVITY_INPUT");
      }
    }
  }

  private static void requireCompletePacketCompletion(
      ActivityExplanationResult activities, CodeReadingMaterialSet codeReadingMaterials) {
    List<ActivityPacketCompletion> completions =
        activities
            .packetCompletion()
            .orElseThrow(
                () -> new IllegalArgumentException("PROCESS_DISCOVERY_PARTIAL_ACTIVITY_INPUT"));
    if (completions.stream()
        .anyMatch(
            completion ->
                completion.completion() != ActivityPacketCompletion.Completion.COMPLETE)) {
      throw new IllegalArgumentException("PROCESS_DISCOVERY_PARTIAL_ACTIVITY_INPUT");
    }
    Map<String, Set<String>> expectedEntries = new HashMap<>();
    for (CodeReadingMaterialSet.Packet packet : codeReadingMaterials.packets()) {
      if (expectedEntries.put(
              packet.packetId(),
              entryIdSet(packet.entries().stream().map(entry -> entry.entryId()).toList()))
          != null) {
        throw new IllegalArgumentException("PROCESS_DISCOVERY_STEP05_INPUT_INVALID");
      }
    }
    Map<String, Set<String>> actualEntries = new HashMap<>();
    for (ActivityPacketCompletion completion : completions) {
      if (actualEntries.put(completion.packetId(), entryIdSet(completion.entryIds())) != null) {
        throw new IllegalArgumentException("PROCESS_DISCOVERY_STEP05_INPUT_INVALID");
      }
    }
    if (!expectedEntries.equals(actualEntries)) {
      throw new IllegalArgumentException("PROCESS_DISCOVERY_STEP05_INPUT_INVALID");
    }
  }

  private static Set<String> entryIdSet(List<String> entryIds) {
    Set<String> distinctEntryIds = new HashSet<>(entryIds);
    if (distinctEntryIds.size() != entryIds.size()) {
      throw new IllegalArgumentException("PROCESS_DISCOVERY_STEP05_INPUT_INVALID");
    }
    return Set.copyOf(distinctEntryIds);
  }

  /** Preserves the historical M10-backed full request shape. */
  public ProcessDiscoveryRequest(
      ActivityExplanationResult activities,
      BusinessMaterialBuildResult materials,
      ProcessDiscoveryProfile profile,
      AnalysisRunId outputRunId,
      VerifiedSourceInventoryReference sourceInventoryReference,
      VerifiedSourceTextReader sourceTextReader,
      ImmutableBytes savedCatalogInput,
      String focusQuestion) {
    this(
        activities,
        materials,
        null,
        null,
        profile,
        outputRunId,
        sourceInventoryReference,
        sourceTextReader,
        savedCatalogInput,
        focusQuestion);
  }

  /** Creates the explicit Step05 reading-material request branch. */
  public ProcessDiscoveryRequest(
      ActivityExplanationResult activities,
      CodeReadingMaterialSet codeReadingMaterials,
      AnalysisStepPublicationReference codeReadingMaterialCheckpoint,
      ProcessDiscoveryProfile profile,
      AnalysisRunId outputRunId,
      VerifiedSourceInventoryReference sourceInventoryReference,
      VerifiedSourceTextReader sourceTextReader,
      ImmutableBytes savedCatalogInput,
      String focusQuestion) {
    this(
        activities,
        null,
        codeReadingMaterials,
        codeReadingMaterialCheckpoint,
        profile,
        outputRunId,
        sourceInventoryReference,
        sourceTextReader,
        savedCatalogInput,
        focusQuestion);
  }

  /** Returns whether this request uses the saved Step05 reading-material protocol. */
  public boolean usesCodeReadingMaterials() {
    return codeReadingMaterials != null;
  }

  /** Convenience form for executions without the optional cross-object reading inputs. */
  public ProcessDiscoveryRequest(
      ActivityExplanationResult activities,
      BusinessMaterialBuildResult materials,
      ProcessDiscoveryProfile profile,
      AnalysisRunId outputRunId) {
    this(activities, materials, null, null, profile, outputRunId, null, null, null, null);
  }

  /** Convenience form for direct tests whose output shares the activity checkpoint owner. */
  public ProcessDiscoveryRequest(
      ActivityExplanationResult activities,
      BusinessMaterialBuildResult materials,
      ProcessDiscoveryProfile profile) {
    this(
        activities,
        materials,
        null,
        null,
        profile,
        ((AnalysisStepModuleAddress)
                Objects.requireNonNull(activities.checkpoint(), "activity checkpoint").address())
            .runId(),
        null,
        null,
        null,
        null);
  }
}
