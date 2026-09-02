package org.sourceanalysis.app.analysis.discovery;

/** The versioned, closed rule set used by application discovery. */
public record DiscoveryProfile(String ruleVersion) {

  private static final DiscoveryProfile STANDARD = new DiscoveryProfile("application-discovery-v2");

  public DiscoveryProfile {
    if (ruleVersion == null || ruleVersion.isBlank()) {
      throw new IllegalArgumentException("discovery rule version is required");
    }
  }

  /** Returns the only currently supported static Java/Spring MVC/MyBatis rule set. */
  public static DiscoveryProfile standard() {
    return STANDARD;
  }
}
