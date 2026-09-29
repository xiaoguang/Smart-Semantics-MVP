package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;

/** Fresh-reopens full prepared-source facts for Maven file-profile activation only. */
public final class PreparedVerifiedSourceFileActivationRangeReader {

  private final SourcePreparationReader preparations;

  public PreparedVerifiedSourceFileActivationRangeReader(SourcePreparationReader preparations) {
    this.preparations = Objects.requireNonNull(preparations, "source preparation reader");
  }

  /**
   * Returns a range bound to the exact same prepared source version and inventory artifact as
   * {@code sourceTexts}; a text projection alone cannot establish this range.
   */
  public VerifiedSourceFileActivationRange reopen(
      VerifiedSourceInventoryReference frozenSource, VerifiedSourceTextSet sourceTexts) {
    try {
      Objects.requireNonNull(frozenSource, "verified source inventory reference");
      Objects.requireNonNull(sourceTexts, "verified source texts");
      SavedSourcePreparation saved = preparations.reopen(frozenSource.publication());
      if (!frozenSource.publication().equals(saved.reportReference())
          || saved.sourceVersionReference() == null
          || !frozenSource.publication().equals(saved.sourceVersionReference().publication())
          || saved.publicationFacts() == null
          || !saved
              .sourceVersionReference()
              .sourceVersionId()
              .value()
              .equals(sourceTexts.snapshotId())
          || !saved.publicationFacts().sourceInventoryRef().equals(sourceTexts.sourceInventoryRef())
          || (saved.assessment().readiness() != SourcePreparationReadiness.READY
              && saved.assessment().readiness()
                  != SourcePreparationReadiness.READY_WITH_EXCLUSIONS)) {
        throw invalid();
      }
      return new VerifiedSourceFileActivationRange(
          saved.sourceVersionReference().sourceVersionId().value(),
          saved.publicationFacts().sourceInventoryRef(),
          saved.result());
    } catch (IllegalArgumentException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw invalid();
    }
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("PREPARED_SOURCE_FILE_ACTIVATION_RANGE_REOPEN_INVALID");
  }
}
