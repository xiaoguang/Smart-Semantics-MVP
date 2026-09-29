package org.sourceanalysis.app.analysis.material.publish;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;

/**
 * Historical Packet wire identifiers retained for strict Step05 reader and artifact compatibility.
 *
 * <p>The v1/v2 Packet writers were retired with config-v2 production. New technical runs publish
 * entry-evidence-v1 through {@link EntryEvidencePublisher}; this class deliberately exposes no
 * publication operation.
 */
public final class CodeReadingMaterialPublisher {

  public static final String FILE_NAME = "code-reading-materials.jsonl";
  public static final String ARTIFACT_TYPE = "CODE_READING_MATERIAL_SET";
  public static final String SCHEMA_VERSION = "code-reading-material-set-v1";
  public static final String TECHNICAL_SCHEMA_VERSION = "code-reading-material-set-v2";
  static final String LEGACY_PRODUCER = "code-reading-materials-v1";
  static final String TECHNICAL_PRODUCER = "code-reading-materials-v2";

  private CodeReadingMaterialPublisher() {}

  static String diagnosticKey(PersistenceMaterialIndex.Diagnostic diagnostic) {
    return "diagnostic:"
        + sha256(
            diagnostic.code()
                + "\u0000"
                + diagnostic.subjectRef()
                + "\u0000"
                + diagnostic.detail());
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
