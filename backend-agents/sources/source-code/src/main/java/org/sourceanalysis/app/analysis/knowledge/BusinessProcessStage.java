package org.sourceanalysis.app.analysis.knowledge;

/** A model-reviewed business stage; its order is an interpretation, not a Java-derived proof. */
public record BusinessProcessStage(int order, String activityId, String description) {

  public BusinessProcessStage {
    if (order < 1
        || activityId == null
        || activityId.isBlank()
        || description == null
        || description.isBlank()) {
      throw new IllegalArgumentException("business process stage is invalid");
    }
  }
}
