package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;

/** All immutable inputs needed to discover frontend HTTP requests for one admitted source basis. */
public record FrontendHttpDiscoveryRequest(
    VerifiedSourceTextSet sourceTexts,
    FrontendHttpConfiguration configuration,
    List<HttpEntryPoint> httpEntries,
    boolean backendDiscoveryExecuted) {

  public FrontendHttpDiscoveryRequest {
    Objects.requireNonNull(sourceTexts, "source texts");
    Objects.requireNonNull(configuration, "frontend configuration");
    httpEntries = List.copyOf(httpEntries);
  }
}
