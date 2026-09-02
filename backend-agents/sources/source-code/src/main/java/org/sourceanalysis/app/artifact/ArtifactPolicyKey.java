package org.sourceanalysis.app.artifact;

/** An exact artifact type and schema-version lookup key in a policy registry. */
public record ArtifactPolicyKey(String artifactType, String schemaVersion) {

  public ArtifactPolicyKey {
    requireWireValue(artifactType, "artifact type");
    requireWireValue(schemaVersion, "schema version");
  }

  private static void requireWireValue(String value, String label) {
    if (value == null || value.isEmpty() || !value.equals(value.trim())) {
      throw new IllegalArgumentException(label + " must be a nonblank exact wire value");
    }
    for (int index = 0; index < value.length(); index++) {
      if (Character.isISOControl(value.charAt(index))) {
        throw new IllegalArgumentException(label + " must not contain control characters");
      }
    }
  }
}
