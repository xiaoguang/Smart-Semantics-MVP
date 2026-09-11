package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** Program-only reverse mapping from a dry packet key to its verified internal material. */
public record ReaderKeyBindingV1(
    String readerKey, String keyKind, List<String> internalReferenceIds) {

  public ReaderKeyBindingV1 {
    required(readerKey, "reader key");
    required(keyKind, "reader key kind");
    internalReferenceIds = List.copyOf(Objects.requireNonNull(internalReferenceIds));
    if (internalReferenceIds.isEmpty()) {
      throw new IllegalArgumentException("reader key binding requires internal references");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required");
  }
}
