package org.sourceanalysis.app.analysis.interpretation.activity;

/** Per-packet input limits and bounded navigation limits for Step05 Activity reading. */
public record ActivityReadingProfile(
    int maxModelInputBytes,
    int maxModelOutputBytes,
    int maxNavigationPages,
    int maxReadingRounds,
    int maxSlicesPerPacket) {

  // actualDraft and missingEntryKeys are added to the original canonical object for REVIEW.
  private static final int REVIEW_WRAPPER_RESERVE_BYTES = 256;
  public static final int DEFAULT_MAX_NAVIGATION_PAGES = 128;
  public static final int DEFAULT_MAX_READING_ROUNDS = 4;
  public static final int DEFAULT_MAX_SLICES_PER_PACKET = 32;

  public ActivityReadingProfile {
    if (maxModelInputBytes <= 0
        || maxModelOutputBytes <= 0
        || maxNavigationPages <= 0
        || maxReadingRounds <= 0
        || maxSlicesPerPacket <= 0) {
      throw new IllegalArgumentException("activity reading limits must be positive");
    }
  }

  public static ActivityReadingProfile defaults(int maxModelInputBytes, int maxModelOutputBytes) {
    return new ActivityReadingProfile(
        maxModelInputBytes,
        maxModelOutputBytes,
        DEFAULT_MAX_NAVIGATION_PAGES,
        DEFAULT_MAX_READING_ROUNDS,
        DEFAULT_MAX_SLICES_PER_PACKET);
  }

  public boolean fitsDraftAndMaximumReview(int draftInputBytes) {
    return fitsDraftAndMaximumReview(draftInputBytes, maxModelInputBytes, maxModelOutputBytes);
  }

  public int maxDraftPacketBytes() {
    return Math.max(0, maxModelInputBytes - maxModelOutputBytes - REVIEW_WRAPPER_RESERVE_BYTES);
  }

  public static boolean fitsDraftAndMaximumReview(
      int draftInputBytes, int maxInputBytes, int maxOutputBytes) {
    return (long) draftInputBytes + maxOutputBytes + REVIEW_WRAPPER_RESERVE_BYTES <= maxInputBytes;
  }
}
