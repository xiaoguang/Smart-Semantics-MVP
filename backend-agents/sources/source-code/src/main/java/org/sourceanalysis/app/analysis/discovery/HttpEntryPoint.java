package org.sourceanalysis.app.analysis.discovery;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** One statically composed Spring MVC HTTP entry and its exact route evidence. */
public record HttpEntryPoint(
    ArtifactId entryId,
    HttpEntryKind kind,
    String protocol,
    String method,
    String route,
    List<String> routeParts,
    String handlerFqn,
    List<String> parameterNames,
    List<SourceExcerptV1> routeSourceExcerpts) {

  public HttpEntryPoint {
    Objects.requireNonNull(entryId, "entry ID");
    Objects.requireNonNull(kind, "entry kind");
    if (!"HTTP".equals(protocol)
        || method == null
        || method.isBlank()
        || route == null
        || !route.startsWith("/")
        || handlerFqn == null
        || handlerFqn.isBlank()) {
      throw new IllegalArgumentException("HTTP entry has invalid required values");
    }
    routeParts = List.copyOf(routeParts);
    parameterNames = List.copyOf(parameterNames);
    routeSourceExcerpts = List.copyOf(routeSourceExcerpts);
    if (routeParts.size() != 2 || routeSourceExcerpts.size() != 2) {
      throw new IllegalArgumentException(
          "HTTP entry must preserve class and method route evidence");
    }
  }
}
