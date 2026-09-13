package org.sourceanalysis.app.adapter.provider;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Explicit OpenAI Responses API settings; the API key is never read from global configuration. */
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public record OpenAiResponsesProfile(
    URI endpoint, String apiKey, String model, String reasoningEffort, Duration timeout) {

  public OpenAiResponsesProfile {
    endpoint = Objects.requireNonNull(endpoint, "OpenAI endpoint");
    String scheme = endpoint.getScheme();
    boolean loopbackHttp =
        "http".equals(scheme)
            && ("127.0.0.1".equals(endpoint.getHost())
                || "localhost".equalsIgnoreCase(endpoint.getHost()));
    if (!("https".equals(scheme) || loopbackHttp)
        || endpoint.getHost() == null
        || endpoint.getUserInfo() != null
        || endpoint.getFragment() != null) {
      throw new IllegalArgumentException("OpenAI endpoint must be HTTPS or loopback HTTP");
    }
    required(apiKey, "API key");
    required(model, "model");
    required(reasoningEffort, "reasoning effort");
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("OpenAI timeout must be positive");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
