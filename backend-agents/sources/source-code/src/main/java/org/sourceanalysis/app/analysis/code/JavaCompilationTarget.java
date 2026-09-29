package org.sourceanalysis.app.analysis.code;

/** Explicit source/target or release setting supplied by an external compilation input. */
public record JavaCompilationTarget(String release, String source, String target) {

  public JavaCompilationTarget {
    release = normalizedVersion(release);
    source = normalizedVersion(source);
    target = normalizedVersion(target);
    if (release != null) {
      if (source != null || target != null) {
        throw new IllegalArgumentException(
            "compiler release cannot be combined with source or target");
      }
    } else if (source == null
        || target == null
        || Integer.parseInt(source) > Integer.parseInt(target)) {
      throw new IllegalArgumentException("compiler source and target must be compatible");
    }
  }

  public String sourceLevel() {
    return release != null ? release : source;
  }

  public boolean acceptsPlatformVersion(String platformVersion) {
    String normalizedPlatform = normalizedVersion(platformVersion);
    if (release != null) {
      return release.equals(normalizedPlatform);
    }
    return Integer.parseInt(normalizedPlatform) >= Integer.parseInt(target);
  }

  static String normalizedVersion(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.startsWith("1.")) {
      normalized = normalized.substring(2);
    }
    int dot = normalized.indexOf('.');
    if (dot >= 0) {
      normalized = normalized.substring(0, dot);
    }
    try {
      if (Integer.parseInt(normalized) <= 0) {
        throw new IllegalArgumentException("compiler version must be positive");
      }
      return normalized;
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("compiler version must be numeric", invalid);
    }
  }
}
