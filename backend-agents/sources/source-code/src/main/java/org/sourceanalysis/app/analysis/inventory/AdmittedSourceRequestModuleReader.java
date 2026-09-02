package org.sourceanalysis.app.analysis.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Strictly reopens the M1 payload and projects only the fields M2 is allowed to consume. */
final class AdmittedSourceRequestModuleReader {

  private static final String ARTIFACT_TYPE = "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST";
  private static final String SCHEMA_VERSION =
      "verified-source-inventory-admitted-source-request-v2";
  private static final Set<String> ENVELOPE_FIELDS =
      Set.of(
          "artifactId",
          "artifactType",
          "completion",
          "controls",
          "payload",
          "producer",
          "schemaVersion",
          "upstreamArtifacts");
  private static final Set<String> BODY_FIELDS =
      Set.of(
          "declaredPathCount",
          "files",
          "inventoryScope",
          "originRepositoryUrl",
          "originRevision",
          "repositoryCompletionEligible",
          "requestIdentity",
          "sourceRegistrationId");
  private static final Set<String> SCOPE_FIELDS = Set.of("kind", "scopeRoot");
  private static final Set<String> FILE_FIELDS =
      Set.of(
          "analysisDisposition",
          "gitMode",
          "mediaType",
          "path",
          "sha256",
          "sizeBytes",
          "textEncoding");
  private static final Comparator<String> UTF8_ORDER =
      (first, second) -> compareUtf8(first, second);

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  AdmittedSourceRequestModuleReader(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifacts");
    this.canonicalJson = new CanonicalJsonCodec();
  }

  VerifiedSourceIndexInput read(ModulePublicationReference publicationReference) {
    if (publicationReference == null) {
      throw failure();
    }
    ReopenedModulePublication publication = moduleArtifacts.reopen(publicationReference);
    requireM1Publication(publication);
    VerifiedCanonicalPayload payload = requiredPayload(publication);
    try {
      JsonNode parsed = canonicalJson.parseCanonical(payload.canonicalUtf8());
      if (!(parsed instanceof ObjectNode envelope)) {
        throw failure();
      }
      requireExactFields(envelope, ENVELOPE_FIELDS);
      requireText(envelope, "artifactType", ARTIFACT_TYPE);
      requireText(envelope, "schemaVersion", SCHEMA_VERSION);
      if (!(envelope.get("payload") instanceof ObjectNode body)) {
        throw failure();
      }
      return input(
          body,
          registrationReference(publication, body),
          requiredUpstreamReference(publication, "capture-receipt"),
          requiredUpstreamReference(publication, "snapshot-manifest"));
    } catch (VerifiedSourceIndexException failure) {
      throw failure;
    } catch (RuntimeException invalid) {
      throw failure();
    }
  }

  private static void requireM1Publication(ReopenedModulePublication publication) {
    if (!(publication.reference().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY
        || address.moduleNumber() != 1
        || !"request-admission".equals(address.moduleKey())
        || publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || !publication.receipt().gapRefs().isEmpty()) {
      throw failure();
    }
  }

  private static VerifiedCanonicalPayload requiredPayload(ReopenedModulePublication publication) {
    List<VerifiedCanonicalPayload> payloads = publication.payloads();
    if (payloads.size() != 1) {
      throw failure();
    }
    VerifiedCanonicalPayload payload = payloads.get(0);
    if (!"admitted-source-request.json".equals(payload.descriptor().fileName())
        || !ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
        || !SCHEMA_VERSION.equals(payload.descriptor().schemaVersion())) {
      throw failure();
    }
    return payload;
  }

  private static ArtifactReference registrationReference(
      ReopenedModulePublication publication, ObjectNode body) {
    ArtifactId sourceRegistrationId = ArtifactId.parse(requiredText(body, "sourceRegistrationId"));
    List<ArtifactReference> matching =
        publication.receipt().upstreamArtifacts().stream()
            .filter(reference -> reference.artifactId().equals(sourceRegistrationId))
            .toList();
    if (matching.size() != 1) {
      throw failure();
    }
    return matching.get(0);
  }

  private static ArtifactReference requiredUpstreamReference(
      ReopenedModulePublication publication, String artifactIdPrefix) {
    List<ArtifactReference> matching =
        publication.receipt().upstreamArtifacts().stream()
            .filter(reference -> reference.artifactId().value().startsWith(artifactIdPrefix + ":"))
            .toList();
    if (matching.size() != 1) {
      throw failure();
    }
    return matching.get(0);
  }

  private static VerifiedSourceIndexInput input(
      ObjectNode body,
      ArtifactReference sourceRegistrationRef,
      ArtifactReference captureReceiptRef,
      ArtifactReference snapshotManifestRef) {
    requireExactFields(body, BODY_FIELDS);
    InventoryScope scope = scope(requiredObject(body, "inventoryScope"));
    boolean repositoryCompletionEligible = requiredBoolean(body, "repositoryCompletionEligible");
    if (repositoryCompletionEligible && scope.kind() != InventoryScope.Kind.COMPLETE_CAPTURE) {
      throw failure();
    }
    List<AdmittedSourceFile> files = files(requiredArray(body, "files"));
    return new VerifiedSourceIndexInput(
        requiredText(body, "requestIdentity"),
        sourceRegistrationRef,
        requiredText(body, "originRepositoryUrl"),
        requiredText(body, "originRevision"),
        captureReceiptRef,
        snapshotManifestRef,
        scope,
        repositoryCompletionEligible,
        requiredNonnegativeInt(body, "declaredPathCount"),
        files);
  }

  private static InventoryScope scope(ObjectNode scope) {
    requireExactFields(scope, SCOPE_FIELDS);
    String kind = requiredText(scope, "kind");
    JsonNode scopeRoot = scope.get("scopeRoot");
    if (scopeRoot == null) {
      throw failure();
    }
    if ("COMPLETE_CAPTURE".equals(kind) && scopeRoot.isNull()) {
      return InventoryScope.completeCapture();
    }
    if ("BOUNDED_PATH_SET".equals(kind) && scopeRoot.isTextual()) {
      return InventoryScope.boundedPathSet(scopeRoot.textValue());
    }
    throw failure();
  }

  private static List<AdmittedSourceFile> files(ArrayNode nodes) {
    if (nodes.isEmpty()) {
      throw failure();
    }
    List<AdmittedSourceFile> files = new java.util.ArrayList<>(nodes.size());
    for (JsonNode node : nodes) {
      files.add(file(node));
    }
    files = List.copyOf(files);
    String previous = null;
    Set<String> paths = new HashSet<>();
    for (AdmittedSourceFile file : files) {
      validateSafePath(file.path());
      if (!paths.add(file.path())
          || (previous != null && UTF8_ORDER.compare(previous, file.path()) >= 0)) {
        throw failure();
      }
      previous = file.path();
    }
    return files;
  }

  private static AdmittedSourceFile file(JsonNode value) {
    if (!(value instanceof ObjectNode file)) {
      throw failure();
    }
    requireExactFields(file, FILE_FIELDS);
    JsonNode textEncoding = file.get("textEncoding");
    if (textEncoding == null || (!textEncoding.isNull() && !textEncoding.isTextual())) {
      throw failure();
    }
    try {
      return new AdmittedSourceFile(
          requiredText(file, "path"),
          requiredText(file, "gitMode"),
          requiredText(file, "mediaType"),
          requiredNonnegativeLong(file, "sizeBytes"),
          Sha256Digest.parse(requiredText(file, "sha256")),
          SourceAnalysisDisposition.valueOf(requiredText(file, "analysisDisposition")),
          textEncoding.isNull() ? null : textEncoding.textValue());
    } catch (IllegalArgumentException invalid) {
      throw failure();
    }
  }

  private static ObjectNode requiredObject(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (!(value instanceof ObjectNode object)) {
      throw failure();
    }
    return object;
  }

  private static ArrayNode requiredArray(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (!(value instanceof ArrayNode array)) {
      throw failure();
    }
    return array;
  }

  private static String requiredText(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null || !value.isTextual()) {
      throw failure();
    }
    return value.textValue();
  }

  private static void requireText(ObjectNode parent, String fieldName, String expected) {
    if (!expected.equals(requiredText(parent, fieldName))) {
      throw failure();
    }
  }

  private static boolean requiredBoolean(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null || !value.isBoolean()) {
      throw failure();
    }
    return value.booleanValue();
  }

  private static int requiredNonnegativeInt(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null
        || !value.canConvertToInt()
        || !value.isIntegralNumber()
        || value.intValue() < 0) {
      throw failure();
    }
    return value.intValue();
  }

  private static long requiredNonnegativeLong(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null
        || !value.canConvertToLong()
        || !value.isIntegralNumber()
        || value.longValue() < 0L) {
      throw failure();
    }
    return value.longValue();
  }

  private static void requireExactFields(ObjectNode object, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure();
    }
  }

  private static void validateSafePath(String path) {
    if (path == null
        || path.isBlank()
        || path.startsWith("/")
        || path.indexOf('\\') >= 0
        || java.util.Arrays.stream(path.split("/", -1))
            .anyMatch(
                segment -> segment.isEmpty() || ".".equals(segment) || "..".equals(segment))) {
      throw failure();
    }
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private static VerifiedSourceIndexException failure() {
    return new VerifiedSourceIndexException("CAPTURE_IDENTITY_INVALID");
  }
}
