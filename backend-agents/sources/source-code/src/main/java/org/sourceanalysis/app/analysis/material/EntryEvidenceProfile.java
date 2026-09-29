package org.sourceanalysis.app.analysis.material;

/**
 * Explicit canonical-JSON limits for one Step05 entry-evidence publication.
 *
 * <p>These limits intentionally do not reuse {@link CodeReadingMaterialProfile}: entry evidence is
 * one self-contained JSON document per backend entry, never a Markdown packet shared by several
 * entries.
 */
public record EntryEvidenceProfile(
    long maxEntryUtf8Bytes, long maxPublicationUtf8Bytes, int maxEntries) {

  public EntryEvidenceProfile {
    if (maxEntryUtf8Bytes < 1L || maxPublicationUtf8Bytes < 1L || maxEntries < 1) {
      throw new IllegalArgumentException("entry-evidence limits must be positive");
    }
    if (maxEntryUtf8Bytes > maxPublicationUtf8Bytes) {
      throw new IllegalArgumentException(
          "an entry-evidence document limit cannot exceed the publication limit");
    }
  }
}
