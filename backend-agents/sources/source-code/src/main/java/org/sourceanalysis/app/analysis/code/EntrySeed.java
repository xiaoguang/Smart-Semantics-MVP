package org.sourceanalysis.app.analysis.code;

import java.util.Objects;

/** An engine-neutral, overload-safe entry selected by application discovery. */
public record EntrySeed(String entryId, String methodKey, SourceRange methodRange, String trigger) {

  public EntrySeed {
    entryId = requireText(entryId, "entry ID");
    methodKey = requireText(methodKey, "method key");
    methodRange = Objects.requireNonNull(methodRange, "method range");
    trigger = requireText(trigger, "entry trigger");
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }
}
