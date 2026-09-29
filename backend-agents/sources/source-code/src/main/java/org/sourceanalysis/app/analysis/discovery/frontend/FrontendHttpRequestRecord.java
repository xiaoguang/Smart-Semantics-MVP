package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.SourceRange;

/** One admitted frontend HTTP request, retaining its page instance and finite wrapper chain. */
public record FrontendHttpRequestRecord(
    String requestId,
    String pagePath,
    String sourceSha256,
    String instanceKey,
    SourceRange callRange,
    String httpMethod,
    String rawUrlExpression,
    String resolvedPath,
    String requestOrigin,
    List<FrontendWrapperCall> wrapperPath,
    List<FrontendArgumentBinding> argumentBindings,
    String baseUrlExpression,
    String baseUrlStaticFallback) {

  public FrontendHttpRequestRecord {
    Objects.requireNonNull(requestId, "request ID");
    Objects.requireNonNull(pagePath, "page path");
    Objects.requireNonNull(sourceSha256, "source SHA-256");
    Objects.requireNonNull(instanceKey, "page instance key");
    Objects.requireNonNull(callRange, "request call range");
    Objects.requireNonNull(httpMethod, "HTTP method");
    Objects.requireNonNull(rawUrlExpression, "raw URL expression");
    wrapperPath = List.copyOf(wrapperPath);
    argumentBindings = List.copyOf(argumentBindings);
  }
}
