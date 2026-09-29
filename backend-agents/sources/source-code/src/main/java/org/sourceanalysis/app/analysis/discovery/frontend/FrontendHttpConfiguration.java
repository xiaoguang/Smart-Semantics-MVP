package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.List;
import java.util.Map;

/** The selected frontend roots, static aliases, and explicit address mappings for one discovery. */
public record FrontendHttpConfiguration(
    List<String> sourceRoots,
    Map<String, String> staticAliases,
    List<FrontendHttpAddressMapping> httpMappings) {

  public FrontendHttpConfiguration {
    sourceRoots = List.copyOf(sourceRoots);
    staticAliases = Map.copyOf(staticAliases);
    httpMappings = List.copyOf(httpMappings);
    if (sourceRoots.isEmpty()
        || sourceRoots.stream().anyMatch(root -> root == null || root.isBlank())) {
      throw new IllegalArgumentException("frontend configuration requires selected source roots");
    }
  }
}
