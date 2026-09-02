package org.sourceanalysis.app.artifact;

import java.util.regex.Pattern;

/** A validated generic content identifier with a safe ASCII wire representation. */
public record ArtifactId(String value) {

  private static final Pattern WIRE_VALUE = Pattern.compile("[a-z][a-z0-9-]{0,47}:[0-9a-f]{64}");

  public ArtifactId {
    if (value == null || !WIRE_VALUE.matcher(value).matches()) {
      throw new IllegalArgumentException("artifact ID must use the canonical content-ID grammar");
    }
  }

  /** Parses a canonical generic content identifier. */
  public static ArtifactId parse(String wireValue) {
    return new ArtifactId(wireValue);
  }

  @Override
  public String toString() {
    return value;
  }
}
