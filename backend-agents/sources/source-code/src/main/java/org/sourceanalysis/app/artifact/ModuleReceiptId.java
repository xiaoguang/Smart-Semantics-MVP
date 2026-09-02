package org.sourceanalysis.app.artifact;

import java.util.regex.Pattern;

/** A validated module receipt identifier with a fixed safe wire prefix. */
public record ModuleReceiptId(String value) {

  private static final Pattern WIRE_VALUE = Pattern.compile("module-receipt:[0-9a-f]{64}");

  public ModuleReceiptId {
    if (value == null || !WIRE_VALUE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "module receipt ID must use the canonical module-receipt grammar");
    }
  }

  /** Parses a canonical module receipt identifier. */
  public static ModuleReceiptId parse(String wireValue) {
    return new ModuleReceiptId(wireValue);
  }

  @Override
  public String toString() {
    return value;
  }
}
