package org.sourceanalysis.app.analysis.knowledge;

/**
 * Deep Step07 seam that discovers, reconstructs, and consolidates repository business processes.
 */
public interface BusinessProcessDiscovery {

  ProcessDiscoveryResult discover(ProcessDiscoveryRequest request);
}
