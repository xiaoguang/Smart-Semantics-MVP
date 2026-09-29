package org.sourceanalysis.app.capture.preparation;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceIssue;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;

/** The newly observed facts for named refresh targets, never a complete source inventory. */
record SourcePreparationTargetFragment(
    SourcePreparationResult.InspectionStatus inspectionStatus,
    boolean targetEnumerationComplete,
    List<SourceEntry> entries,
    List<SourceIssue> issues,
    List<String> unknownSubtrees,
    List<SourcePreparationTarget> unmatchedExclusions) {

  SourcePreparationTargetFragment {
    Objects.requireNonNull(inspectionStatus, "target inspection status");
    entries = List.copyOf(entries);
    issues = List.copyOf(issues);
    unknownSubtrees = List.copyOf(unknownSubtrees);
    unmatchedExclusions = List.copyOf(unmatchedExclusions);
    new SourcePreparationResult(
        inspectionStatus,
        targetEnumerationComplete,
        entries,
        issues,
        unknownSubtrees,
        unmatchedExclusions);
  }
}
