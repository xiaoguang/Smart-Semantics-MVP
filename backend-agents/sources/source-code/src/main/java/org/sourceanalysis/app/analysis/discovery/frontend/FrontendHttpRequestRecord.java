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
    List<FrontendSupportingSourceUnit> supportingSourceUnits,
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
    // Older frontend-index-v2 HTTP_REQUEST records did not carry per-request ownership. They
    // remain readable; the index-level SOURCE_UNIT records are still retained for inspection.
    supportingSourceUnits =
        supportingSourceUnits == null ? List.of() : List.copyOf(supportingSourceUnits);
    argumentBindings = List.copyOf(argumentBindings);
  }

  /** Retains request records written before supporting source units were request-owned. */
  public FrontendHttpRequestRecord(
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
    this(
        requestId,
        pagePath,
        sourceSha256,
        instanceKey,
        callRange,
        httpMethod,
        rawUrlExpression,
        resolvedPath,
        requestOrigin,
        wrapperPath,
        List.of(),
        argumentBindings,
        baseUrlExpression,
        baseUrlStaticFallback);
  }
}
