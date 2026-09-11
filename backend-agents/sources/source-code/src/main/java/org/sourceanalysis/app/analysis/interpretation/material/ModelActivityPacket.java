package org.sourceanalysis.app.analysis.interpretation.material;

import java.util.List;

/** The deliberately small, source-location-free packet that may be sent to an activity model. */
public record ModelActivityPacket(
    String context,
    List<String> technicalObservations,
    List<AllowlistedReference> allowlistedRefs,
    List<String> limitations) {

  public ModelActivityPacket {
    require(context, "context");
    technicalObservations = List.copyOf(technicalObservations);
    allowlistedRefs = List.copyOf(allowlistedRefs);
    limitations = List.copyOf(limitations);
    if (technicalObservations.isEmpty() || allowlistedRefs.isEmpty()) {
      throw new IllegalArgumentException("model packet must contain observations and source refs");
    }
  }

  /**
   * A source snippet with its short ref only; path, line, hash, and Proof never leave the program.
   */
  public record AllowlistedReference(String ref, String snippet) {
    public AllowlistedReference {
      require(ref, "source reference");
      require(snippet, "source snippet");
    }
  }

  private static void require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
