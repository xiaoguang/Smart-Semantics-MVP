package org.sourceanalysis.app.adapter.provider;

import java.util.Objects;
import java.util.Optional;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** A bounded model request's machine-readable failure and local completion state. */
public final class StructuredModelProviderFailure extends IllegalStateException {

  private final String reasonCode;
  private final boolean requestStarted;
  private final boolean requestEnded;
  private final ImmutableBytes rawResponse;

  public StructuredModelProviderFailure(
      String reasonCode, boolean requestStarted, boolean requestEnded) {
    this(reasonCode, requestStarted, requestEnded, reasonCode, null);
  }

  public StructuredModelProviderFailure(
      String reasonCode,
      boolean requestStarted,
      boolean requestEnded,
      String message,
      Throwable cause) {
    this(reasonCode, requestStarted, requestEnded, message, cause, null);
  }

  /** Carries bounded private failure output, which may be a response or CLI diagnostic. */
  public StructuredModelProviderFailure(
      String reasonCode,
      boolean requestStarted,
      boolean requestEnded,
      String message,
      Throwable cause,
      ImmutableBytes rawResponse) {
    super(Objects.requireNonNull(message, "message"), cause);
    this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
    this.requestStarted = requestStarted;
    this.requestEnded = requestEnded;
    this.rawResponse = rawResponse;
    if (requestEnded && !requestStarted) {
      throw new IllegalArgumentException("a request cannot end before it starts");
    }
    if (rawResponse != null && (!requestStarted || !requestEnded)) {
      throw new IllegalArgumentException("response bytes require a completed request");
    }
  }

  public String reasonCode() {
    return reasonCode;
  }

  public boolean requestStarted() {
    return requestStarted;
  }

  public boolean requestEnded() {
    return requestEnded;
  }

  public Optional<ImmutableBytes> rawResponse() {
    return Optional.ofNullable(rawResponse);
  }
}
