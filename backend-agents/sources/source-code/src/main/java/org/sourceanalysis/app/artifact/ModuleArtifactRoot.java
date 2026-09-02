package org.sourceanalysis.app.artifact;

import java.util.regex.Pattern;

/** A validated module artifact root identifier with a fixed safe wire prefix. */
public record ModuleArtifactRoot(String value) {

  private static final Pattern WIRE_VALUE = Pattern.compile("module-root:[0-9a-f]{64}");

  public ModuleArtifactRoot {
    if (value == null || !WIRE_VALUE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "module artifact root must use the canonical module-root grammar");
    }
  }

  /** Parses a canonical module artifact root identifier. */
  public static ModuleArtifactRoot parse(String wireValue) {
    return new ModuleArtifactRoot(wireValue);
  }

  @Override
  public String toString() {
    return value;
  }
}
