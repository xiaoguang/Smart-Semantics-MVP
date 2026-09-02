package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** One static configuration signal and its exact source basis. */
public record ConfigSignal(
    ConfigSignalKind kind,
    SourceExcerptV1 sourceExcerpt,
    String value,
    SignalDisposition disposition,
    String reasonCode) {

  public ConfigSignal {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(sourceExcerpt, "source excerpt");
    Objects.requireNonNull(disposition, "disposition");
  }
}
