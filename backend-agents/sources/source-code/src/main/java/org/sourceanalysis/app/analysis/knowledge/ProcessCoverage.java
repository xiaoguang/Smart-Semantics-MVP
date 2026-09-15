package org.sourceanalysis.app.analysis.knowledge;

import java.util.List;

/** Closed denominators and semantic delivery status for one process catalog. */
public record ProcessCoverage(
    List<ActivityDisposition> activityDispositions,
    List<CandidateDisposition> candidateDispositions,
    List<ReviewedProcessDisposition> reviewedProcessDispositions,
    String coverageStatus,
    String semanticDeliveryStatus) {

  public ProcessCoverage {
    activityDispositions = List.copyOf(activityDispositions);
    candidateDispositions = List.copyOf(candidateDispositions);
    reviewedProcessDispositions = List.copyOf(reviewedProcessDispositions);
    if (!"CLOSED".equals(coverageStatus)) {
      throw new IllegalArgumentException("process coverage must be closed");
    }
    if (!List.of("COMPLETE", "PARTIAL").contains(semanticDeliveryStatus)) {
      throw new IllegalArgumentException("process semantic delivery status is invalid");
    }
    requireDistinct(activityDispositions.stream().map(ActivityDisposition::activityId).toList());
    requireDistinct(candidateDispositions.stream().map(CandidateDisposition::candidateId).toList());
    requireDistinct(
        reviewedProcessDispositions.stream().map(ReviewedProcessDisposition::processId).toList());
  }

  private static void requireDistinct(List<String> values) {
    if (values.stream().distinct().count() != values.size()) {
      throw new IllegalArgumentException("process coverage contains duplicate identities");
    }
  }

  public record ActivityDisposition(
      String activityId, String name, String disposition, String reason) {
    public ActivityDisposition {
      require(activityId, "activity ID");
      require(name, "activity name");
      if (!List.of(
              "PROCESS_MEMBER",
              "SUPPORT_ONLY",
              "STANDALONE",
              "UNCLASSIFIED",
              "NOT_PROCESSED_CAPACITY")
          .contains(disposition)) {
        throw new IllegalArgumentException("activity disposition is invalid");
      }
      require(reason, "activity disposition reason");
    }
  }

  public record CandidateDisposition(String candidateId, String disposition, String reason) {
    public CandidateDisposition {
      require(candidateId, "candidate ID");
      if (!List.of(
              "RECONSTRUCTED",
              "SPLIT",
              "SUPPORT_ONLY",
              "INSUFFICIENT_MATERIAL",
              "NOT_PROCESSED_CAPACITY")
          .contains(disposition)) {
        throw new IllegalArgumentException("candidate disposition is invalid");
      }
      require(reason, "candidate disposition reason");
    }
  }

  public record ReviewedProcessDisposition(
      String processId, String disposition, String targetProcessId, String reason) {
    public ReviewedProcessDisposition {
      require(processId, "process ID");
      if (!List.of("PUBLISHED", "MERGED_INTO", "REJECTED").contains(disposition)) {
        throw new IllegalArgumentException("reviewed process disposition is invalid");
      }
      if ("MERGED_INTO".equals(disposition) != (targetProcessId != null)) {
        throw new IllegalArgumentException("merged process target is invalid");
      }
      require(reason, "reviewed process disposition reason");
    }
  }

  private static void require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
