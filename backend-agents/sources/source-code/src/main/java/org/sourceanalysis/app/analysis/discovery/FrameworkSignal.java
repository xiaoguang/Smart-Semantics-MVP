package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** One static framework-dependency signal and its exact source basis. */
public record FrameworkSignal(
    FrameworkSignalKind kind,
    SourceExcerptV1 sourceExcerpt,
    SignalDisposition disposition,
    String reasonCode) {

  public FrameworkSignal {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(sourceExcerpt, "source excerpt");
    Objects.requireNonNull(disposition, "disposition");
  }
}
