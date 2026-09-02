package org.sourceanalysis.app.artifact;

import java.util.regex.Pattern;

/** A validated SHA-256 digest with a canonical lowercase hexadecimal representation. */
public record Sha256Digest(String value) {

  private static final Pattern WIRE_VALUE = Pattern.compile("[0-9a-f]{64}");

  public Sha256Digest {
    if (value == null || !WIRE_VALUE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "SHA-256 digest must be 64 lowercase hexadecimal characters");
    }
  }

  /** Parses a canonical SHA-256 digest. */
  public static Sha256Digest parse(String wireValue) {
    return new Sha256Digest(wireValue);
  }

  @Override
  public String toString() {
    return value;
  }
}
