package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.SourceRange;

/**
 * A finite syntax-tool observation for one frontend request occurrence. Its source identity is
 * verified by {@link FrontendHttpDiscoverer} against the supplied R0 text set.
 */
public record FrontendRequestObservation(
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
    String diagnosticCode,
    String baseUrlExpression,
    String baseUrlStaticFallback) {

  public FrontendRequestObservation {
    if (requestId == null
        || requestId.isBlank()
        || pagePath == null
        || pagePath.isBlank()
        || sourceSha256 == null
        || !sourceSha256.matches("[0-9a-f]{64}")
        || instanceKey == null
        || instanceKey.isBlank()
        || httpMethod == null
        || httpMethod.isBlank()
        || rawUrlExpression == null
        || rawUrlExpression.isBlank()) {
      throw new IllegalArgumentException("frontend request observation identity is invalid");
    }
    Objects.requireNonNull(callRange, "request call range");
    wrapperPath = List.copyOf(wrapperPath);
    supportingSourceUnits = List.copyOf(supportingSourceUnits);
    argumentBindings = List.copyOf(argumentBindings);
  }

  /** Retains syntax-tool fixtures that predate explicitly projected supporting source units. */
  public FrontendRequestObservation(
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
      String diagnosticCode,
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
        diagnosticCode,
        baseUrlExpression,
        baseUrlStaticFallback);
  }
}
