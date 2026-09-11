package org.sourceanalysis.app.analysis.interpretation.material;

import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;

/**
 * Path-free request to project one published technical Flow set into readable business material.
 */
public record BuildBusinessMaterialsRequest(
    BusinessFlowsReference businessFlows, BusinessMaterialProfile profile) {

  public BuildBusinessMaterialsRequest {
    if (businessFlows == null || profile == null) {
      throw new IllegalArgumentException(
          "business material request requires published business flows");
    }
  }
}
