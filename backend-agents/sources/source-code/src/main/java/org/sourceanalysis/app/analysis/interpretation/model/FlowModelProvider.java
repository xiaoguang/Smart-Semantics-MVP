package org.sourceanalysis.app.analysis.interpretation.model;

/** The only M5 transport seam; adapters may not alter a frozen task's canonical input. */
public interface FlowModelProvider {
  FlowModelProviderResponse respond(FlowModelTask task);
}
