package org.sourceanalysis.app.analysis.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * Deterministically admits one exact analysis request against a registered immutable capture.
 *
 * <p>This module validates request, capture and profile identities only. It deliberately neither
 * reads source bytes nor accepts a filesystem path; source-byte verification belongs to the next
 * inventory module.
 */
public final class FrozenRequestAdmission {

  private static final String REQUEST_SCHEMA = "analysis-run-request-v2";
  private static final String REQUEST_ID_DOMAIN = "analysis-run-request-id-v2";
  private static final Comparator<CapturedRegularFile> PATH_ORDER =
      Comparator.comparing(CapturedRegularFile::path, FrozenRequestAdmission::compareUtf8);
  private static final Set<String> REQUEST_FIELDS =
      Set.of(
          "approvedFindingRefs",
          "artifactPolicyRegistryRef",
          "candidateSeriesRef",
          "frozenRepositoryRequestRef",
          "organizationRegistrySeedRef",
          "parentCandidateRef",
          "profileBundleRef",
          "promptBundleRef",
          "readerCandidateRound",
          "resourceBudgetRef",
          "schemaBundleRef",
          "schemaVersion",
          "sourceRegistrationId",
          "toolchainRef");
  private static final Set<String> REFERENCE_FIELDS = Set.of("artifactId", "sha256");

  private final CanonicalJsonCodec canonicalJson;

  /** Creates the strict request-admission seam with its own fixed canonical JSON implementation. */
  public FrozenRequestAdmission() {
    this.canonicalJson = new CanonicalJsonCodec();
  }

  /**
   * Parses one exact canonical request and admits only its registered capture and configured
   * limits.
   *
   * @throws FrozenRequestAdmissionException with a stable, path-free code when admission fails
   */
  public AdmittedSourceRequest admit(
      byte[] exactRequestJson, CaptureReceiptView receipt, ProfileView profile) {
    if (exactRequestJson == null || receipt == null || profile == null) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }

    try {
      ParsedRunRequest request = parseRequest(exactRequestJson);
      validateCaptureBinding(request, receipt);
      validateProfileBinding(request, profile);

      List<CapturedRegularFile> files = new ArrayList<>(receipt.regularFiles());
      files.sort(PATH_ORDER);
      validateInventory(files, profile);

      List<ArtifactId> textFileIds =
          files.stream()
              .filter(
                  file -> file.analysisDisposition() == SourceAnalysisDisposition.ANALYZABLE_TEXT)
              .map(CapturedRegularFile::fileId)
              .toList();
      List<ArtifactId> mediaFileIds =
          files.stream()
              .filter(
                  file ->
                      file.analysisDisposition() == SourceAnalysisDisposition.NON_ANALYZABLE_MEDIA)
              .map(CapturedRegularFile::fileId)
              .toList();
      boolean complete =
          receipt.inventoryScope().kind() == InventoryScope.Kind.COMPLETE_CAPTURE
              && receipt.completeCaptureProven();

      return new AdmittedSourceRequest(
          request.requestIdentity(),
          request.sourceRegistrationId(),
          receipt.declaredRepositoryIdentity(),
          receipt.commitId(),
          request.frozenRepositoryRequestRef(),
          receipt.captureReceiptRef(),
          receipt.snapshotManifestRef(),
          receipt.inventoryScope(),
          complete,
          files.size(),
          files,
          textFileIds,
          mediaFileIds,
          request.controls());
    } catch (FrozenRequestAdmissionException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
  }

  private ParsedRunRequest parseRequest(byte[] exactRequestJson) {
    JsonNode parsed;
    try {
      parsed = canonicalJson.parseCanonical(ImmutableBytes.copyOf(exactRequestJson));
    } catch (RuntimeException invalid) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
    if (!(parsed instanceof ObjectNode request)) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
    requireExactFields(request, REQUEST_FIELDS);
    requireText(request, "schemaVersion", REQUEST_SCHEMA);
    ArtifactId sourceRegistrationId =
        ArtifactId.parse(requiredText(request, "sourceRegistrationId"));
    ArtifactReference frozenRequest = requiredReference(request, "frozenRepositoryRequestRef");
    ArtifactReference profile = requiredReference(request, "profileBundleRef");
    ArtifactReference budget = requiredReference(request, "resourceBudgetRef");
    ArtifactReference toolchain = requiredReference(request, "toolchainRef");
    ArtifactReference schemaBundle = requiredReference(request, "schemaBundleRef");
    ArtifactReference promptBundle = requiredReference(request, "promptBundleRef");
    ArtifactReference artifactPolicy = requiredReference(request, "artifactPolicyRegistryRef");
    requiredReference(request, "candidateSeriesRef");
    requireNullableReference(request, "organizationRegistrySeedRef");
    validateReaderCandidateRound(request);

    String requestIdentity =
        "run-request:" + sha256Hex(concatenate(frame(REQUEST_ID_DOMAIN), frame(exactRequestJson)));
    return new ParsedRunRequest(
        requestIdentity,
        sourceRegistrationId,
        frozenRequest,
        new RunRequestControls(
            profile, budget, toolchain, schemaBundle, promptBundle, artifactPolicy));
  }

  private static void validateCaptureBinding(ParsedRunRequest request, CaptureReceiptView receipt) {
    if (!request.sourceRegistrationId().equals(receipt.sourceRegistrationId())
        || !request.frozenRepositoryRequestRef().equals(receipt.frozenRepositoryRequestRef())) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    if (receipt.inventoryScope().kind() == InventoryScope.Kind.BOUNDED_PATH_SET
        && receipt.completeCaptureProven()) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private static void validateProfileBinding(ParsedRunRequest request, ProfileView profile) {
    if (!request.controls().profileBundleRef().equals(profile.profileBundleRef())
        || !request.controls().resourceBudgetRef().equals(profile.resourceBudgetRef())) {
      throw failure("PROFILE_REFERENCE_INVALID");
    }
  }

  private static void validateInventory(List<CapturedRegularFile> files, ProfileView profile) {
    if (files.isEmpty()) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
    if (files.size() > profile.maxSourceFiles()) {
      throw failure("VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED");
    }

    Set<String> paths = new HashSet<>();
    Set<ArtifactId> fileIds = new HashSet<>();
    long totalBytes = 0L;
    for (CapturedRegularFile file : files) {
      validateRepositoryRelativePath(file.path());
      if (!paths.add(file.path())) {
        throw failure("DUPLICATE_SOURCE_PATH");
      }
      if (!fileIds.add(file.fileId())) {
        throw failure("CAPTURE_IDENTITY_INVALID");
      }
      try {
        totalBytes = Math.addExact(totalBytes, file.sizeBytes());
      } catch (ArithmeticException overflow) {
        throw failure("VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED");
      }
      if (totalBytes > profile.maxSourceBytes()) {
        throw failure("VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED");
      }
    }
  }

  private static void validateRepositoryRelativePath(String path) {
    if (path.startsWith("/")
        || path.indexOf('\\') >= 0
        || Arrays.stream(path.split("/", -1))
            .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
      throw failure("SOURCE_PATH_INVALID");
    }
  }

  private static ArtifactReference requiredReference(ObjectNode parent, String fieldName) {
    JsonNode node = parent.get(fieldName);
    if (!(node instanceof ObjectNode reference)) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
    requireExactFields(reference, REFERENCE_FIELDS);
    return new ArtifactReference(
        ArtifactId.parse(requiredText(reference, "artifactId")),
        Sha256Digest.parse(requiredText(reference, "sha256")));
  }

  private static void validateReaderCandidateRound(ObjectNode request) {
    String readerCandidateRound = requiredText(request, "readerCandidateRound");
    if ("ROUND_1".equals(readerCandidateRound)) {
      requireNull(request, "parentCandidateRef");
      requireEmptyReferences(request, "approvedFindingRefs");
      return;
    }
    if ("ROUND_2".equals(readerCandidateRound)) {
      requiredReference(request, "parentCandidateRef");
      requireSortedNonemptyReferences(request, "approvedFindingRefs");
      return;
    }
    throw failure("REQUEST_SCHEMA_INVALID");
  }

  private static void requireEmptyReferences(ObjectNode parent, String fieldName) {
    JsonNode node = parent.get(fieldName);
    if (!(node instanceof ArrayNode array) || !array.isEmpty()) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
  }

  private static void requireSortedNonemptyReferences(ObjectNode parent, String fieldName) {
    JsonNode node = parent.get(fieldName);
    if (!(node instanceof ArrayNode array) || array.isEmpty()) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
    ArtifactReference preceding = null;
    for (JsonNode entry : array) {
      if (!(entry instanceof ObjectNode referenceNode)) {
        throw failure("REQUEST_SCHEMA_INVALID");
      }
      requireExactFields(referenceNode, REFERENCE_FIELDS);
      ArtifactReference reference =
          new ArtifactReference(
              ArtifactId.parse(requiredText(referenceNode, "artifactId")),
              Sha256Digest.parse(requiredText(referenceNode, "sha256")));
      if (preceding != null
          && compareUtf8(preceding.artifactId().value(), reference.artifactId().value()) >= 0) {
        throw failure("REQUEST_SCHEMA_INVALID");
      }
      preceding = reference;
    }
  }

  private static void requireNullableReference(ObjectNode parent, String fieldName) {
    JsonNode node = parent.get(fieldName);
    if (node == null) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
    if (!node.isNull()) {
      requiredReference(parent, fieldName);
    }
  }

  private static void requireNull(ObjectNode parent, String fieldName) {
    if (parent.get(fieldName) == null || !parent.get(fieldName).isNull()) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
  }

  private static void requireText(ObjectNode parent, String fieldName, String expected) {
    if (!expected.equals(requiredText(parent, fieldName))) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
  }

  private static String requiredText(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null || !value.isTextual()) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
    return value.textValue();
  }

  private static void requireExactFields(ObjectNode node, Set<String> expectedFields) {
    Set<String> actualFields = new HashSet<>();
    node.fieldNames().forEachRemaining(actualFields::add);
    if (!actualFields.equals(expectedFields)) {
      throw failure("REQUEST_SCHEMA_INVALID");
    }
  }

  private static int compareUtf8(String left, String right) {
    byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
    byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
    int common = Math.min(leftBytes.length, rightBytes.length);
    for (int index = 0; index < common; index++) {
      int comparison =
          Integer.compare(
              Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(leftBytes.length, rightBytes.length);
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static String sha256Hex(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static FrozenRequestAdmissionException failure(String code) {
    return new FrozenRequestAdmissionException(code);
  }

  private record ParsedRunRequest(
      String requestIdentity,
      ArtifactId sourceRegistrationId,
      ArtifactReference frozenRepositoryRequestRef,
      RunRequestControls controls) {}
}
