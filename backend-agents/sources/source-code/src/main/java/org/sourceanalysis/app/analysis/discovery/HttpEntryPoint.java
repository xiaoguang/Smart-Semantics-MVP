package org.sourceanalysis.app.analysis.discovery;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** One statically composed Spring MVC HTTP entry and its exact route evidence. */
public record HttpEntryPoint(
    ArtifactId entryId,
    HttpEntryKind kind,
    String protocol,
    HttpMethodCondition methodCondition,
    String route,
    List<String> routeParts,
    String handlerFqn,
    String methodKey,
    SourceRange methodRange,
    List<String> parameterNames,
    List<SourceExcerptV1> routeSourceExcerpts) {

  /** Convenience constructor for static explicit-method fixtures. */
  public HttpEntryPoint(
      ArtifactId entryId,
      HttpEntryKind kind,
      String protocol,
      String explicitMethod,
      String route,
      List<String> routeParts,
      String handlerFqn,
      String methodKey,
      SourceRange methodRange,
      List<String> parameterNames,
      List<SourceExcerptV1> routeSourceExcerpts) {
    this(
        entryId,
        kind,
        protocol,
        HttpMethodCondition.explicit(List.of(explicitMethod)),
        route,
        routeParts,
        handlerFqn,
        methodKey,
        methodRange,
        parameterNames,
        routeSourceExcerpts);
  }

  public HttpEntryPoint {
    Objects.requireNonNull(entryId, "entry ID");
    Objects.requireNonNull(kind, "entry kind");
    if (!"HTTP".equals(protocol)
        || methodCondition == null
        || route == null
        || !route.startsWith("/")
        || handlerFqn == null
        || handlerFqn.isBlank()
        || methodKey == null
        || methodKey.isBlank()
        || methodRange == null) {
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

  /** Readable method-condition text for logging and business-material display only. */
  public String method() {
    return methodCondition.display();
  }
}
