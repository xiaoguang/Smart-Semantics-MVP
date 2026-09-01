package org.sourceanalysis.app.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Pattern;

/** Validates the closed header required by the current analysis wire. */
public final class AnalysisWireFormatGuard {

  private static final String WIRE_KIND = "SOURCE_ANALYSIS";
  private static final String WIRE_VERSION = "v1";
  private static final String LEGACY_RECEIPT_FILE = "stage-receipt.json";
  private static final String LEGACY_MAVEN_GROUP = "com.linguan.codemd";
  private static final String LEGACY_MAVEN_ARTIFACT = "github-code-to-markdown";
  private static final String LEGACY_JAVA_PACKAGE = "com.linguan.codemd";
  private static final Pattern LEGACY_NUMBERED_STAGE_KEY =
      Pattern.compile("stage\\d{2}", Pattern.CASE_INSENSITIVE);
  private static final Pattern LEGACY_STAGE_TYPE_OR_SCHEMA =
      Pattern.compile("stage(?:\\d{2})?(?:[-_].+)?", Pattern.CASE_INSENSITIVE);

  /**
   * Requires an object whose current wire header is exact. Additional object fields remain the
   * responsibility of the owner-specific descriptor validator.
   *
   * @param descriptor the descriptor to admit to the current wire
   * @throws UnsupportedAnalysisWireException when the descriptor is not on the current wire
   */
  public void requireCurrentWire(JsonNode descriptor) {
    if (!hasCurrentHeader(descriptor) || hasPreResetStructuralDiscriminator(descriptor)) {
      throw new UnsupportedAnalysisWireException();
    }
  }

  private boolean hasCurrentHeader(JsonNode descriptor) {
    return descriptor != null
        && descriptor.isObject()
        && textualFieldEquals(descriptor, "wireKind", WIRE_KIND)
        && textualFieldEquals(descriptor, "wireVersion", WIRE_VERSION);
  }

  private boolean hasPreResetStructuralDiscriminator(JsonNode descriptor) {
    return hasLegacyPathIdentity(descriptor)
        || textualFieldMatches(descriptor, "analysisStepKey", LEGACY_NUMBERED_STAGE_KEY)
        || textualFieldMatches(descriptor, "artifactType", LEGACY_STAGE_TYPE_OR_SCHEMA)
        || textualFieldMatches(descriptor, "schemaVersion", LEGACY_STAGE_TYPE_OR_SCHEMA)
        || hasLegacyReceiptName(descriptor)
        || hasLegacyMavenOrPackageIdentity(descriptor)
        || descriptor.has("wireAlias");
  }

  private boolean hasLegacyPathIdentity(JsonNode descriptor) {
    String path = textualFieldValue(descriptor, "path");
    if (path == null) {
      return false;
    }

    String[] pathSegments = path.split("[\\\\/]+");
    for (int index = 0; index < pathSegments.length; index++) {
      if ("stages".equals(pathSegments[index])
          || ("sources".equals(pathSegments[index])
              && index + 1 < pathSegments.length
              && "github-code".equals(pathSegments[index + 1]))) {
        return true;
      }
    }
    return false;
  }

  private boolean hasLegacyReceiptName(JsonNode descriptor) {
    return textualFieldEquals(descriptor, "receiptFile", LEGACY_RECEIPT_FILE)
        || textualFieldEquals(descriptor, "fileName", LEGACY_RECEIPT_FILE);
  }

  private boolean hasLegacyMavenOrPackageIdentity(JsonNode descriptor) {
    return textualFieldEquals(descriptor, "groupId", LEGACY_MAVEN_GROUP)
        || textualFieldEquals(descriptor, "artifactId", LEGACY_MAVEN_ARTIFACT)
        || hasLegacyJavaPackage(descriptor);
  }

  private boolean hasLegacyJavaPackage(JsonNode descriptor) {
    String javaPackage = textualFieldValue(descriptor, "javaPackage");
    return javaPackage != null
        && (LEGACY_JAVA_PACKAGE.equals(javaPackage)
            || javaPackage.startsWith(LEGACY_JAVA_PACKAGE + "."));
  }

  private boolean textualFieldMatches(JsonNode descriptor, String fieldName, Pattern pattern) {
    String value = textualFieldValue(descriptor, fieldName);
    return value != null && pattern.matcher(value).matches();
  }

  private boolean textualFieldEquals(JsonNode descriptor, String fieldName, String expectedValue) {
    String value = textualFieldValue(descriptor, fieldName);
    return expectedValue.equals(value);
  }

  private String textualFieldValue(JsonNode descriptor, String fieldName) {
    JsonNode field = descriptor.get(fieldName);
    return field != null && field.isTextual() ? field.textValue() : null;
  }
}
