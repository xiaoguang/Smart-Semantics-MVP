package org.sourceanalysis.app.analysis.interpretation.process;

/** The only M8 transport seam; implementations receive dry application bytes and nothing else. */
public interface ProcessModelProvider {

  ProcessModelProviderResponse respond(ProcessModelProviderRequest request);
}
