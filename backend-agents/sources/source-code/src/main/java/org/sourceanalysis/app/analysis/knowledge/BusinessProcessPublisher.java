package org.sourceanalysis.app.analysis.knowledge;

/** Publishes one closed repository business-process discovery result. */
public interface BusinessProcessPublisher {

  BusinessProcessPublication publish(ProcessDiscoveryResult result);
}
