package org.sourceanalysis.app.artifact;

/** The closed set of canonical artifact envelope kinds. */
public enum CanonicalEnvelopeKind {
  MODULE_ARTIFACT_JSON,
  STANDALONE_JSON,
  CANONICAL_JSONL,
  RAW_UTF8;

  /** Parses one exact envelope-kind wire value. */
  public static CanonicalEnvelopeKind parse(String wireValue) {
    try {
      return valueOf(wireValue);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("unknown canonical artifact envelope kind");
    }
  }
}
