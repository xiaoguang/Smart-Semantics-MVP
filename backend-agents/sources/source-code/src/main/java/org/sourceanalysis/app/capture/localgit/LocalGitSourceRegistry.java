package org.sourceanalysis.app.capture.localgit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
 * Private, nofollow-safe reader for the immutable documents created by local Git capture.
 *
 * <p>It accepts only a content identifier and returns rootless capture metadata. It neither reads a
 * worktree nor exposes the private workspace location or source blob path. Inventory composition
 * can request an opaque private byte handle for one fresh-reopened registration.
 */
public final class LocalGitSourceRegistry {

  private static final String REGISTRATION_SCHEMA = "source-registration-v1";
  private static final String RECEIPT_SCHEMA = "local-git-capture-receipt-v1";
  private static final String MANIFEST_SCHEMA = "local-git-snapshot-entry-v1";
  private static final long MAX_CAPTURE_DOCUMENT_BYTES = 16L * 1024L * 1024L;
  private static final Set<String> REGISTRATION_FIELDS =
      Set.of(
          "captureReceiptRef",
          "commitId",
          "declaredRepositoryIdentity",
          "regularFileCount",
          "schemaVersion",
          "snapshotId",
          "snapshotManifestRef",
          "sourceRegistrationId");
  private static final Set<String> RECEIPT_FIELDS =
      Set.of(
          "analyzableTextFileCount",
          "capturePolicyRef",
          "captureReceiptId",
          "commitId",
          "declaredRepositoryIdentity",
          "networkAccess",
          "nonAnalyzableMediaFileCount",
          "objectFormat",
          "regularFileCount",
          "schemaVersion",
          "snapshotId",
          "snapshotManifestRef",
          "treeObjectId",
          "unsupportedTreeEntryCount",
          "worktreeRead");
  private static final Set<String> MANIFEST_FIELDS =
      Set.of(
          "analysisDisposition",
          "blobObjectId",
          "gitMode",
          "mediaType",
          "path",
          "schemaVersion",
          "sha256",
          "sizeBytes",
          "textEncoding");
  private static final Set<String> REFERENCE_FIELDS = Set.of("artifactId", "sha256");
  private static final Comparator<String> UTF8_ORDER = LocalGitSourceRegistry::compareUtf8;

  private final Path workspace;
  private final CanonicalJsonCodec canonicalJson;

  /** Opens the already-existing private capture workspace without exposing it from this object. */
  public LocalGitSourceRegistry(Path privateCaptureWorkspace) {
    this.workspace = validatedWorkspace(privateCaptureWorkspace);
    this.canonicalJson = new CanonicalJsonCodec();
  }

  /**
   * Fresh-reopens one complete registration, receipt and manifest by its exact content identifier.
   */
  public RegisteredSourceCapture reopen(ArtifactId sourceRegistrationId) {
    if (sourceRegistrationId == null
        || !sourceRegistrationId.value().startsWith("source-registration:")) {
      throw failure("SOURCE_REGISTRATION_NOT_FOUND");
    }
    try {
      ImmutableBytes registrationBytes =
          readRegular(
              workspace
                  .resolve("registrations")
                  .resolve(sourceRegistrationId.value())
                  .resolve("source-registration.json"));
      ObjectNode registration = parseObject(registrationBytes, REGISTRATION_FIELDS);
      requireText(registration, "schemaVersion", REGISTRATION_SCHEMA);
      requireText(registration, "sourceRegistrationId", sourceRegistrationId.value());
      verifySelfIdentifiedDocument(registration, "sourceRegistrationId", "source-registration");
      ArtifactReference registrationRef =
          new ArtifactReference(
              sourceRegistrationId, new Sha256Digest(sha256(registrationBytes.copyToByteArray())));
      String snapshotId = requiredSnapshotId(registration, "snapshotId");
      ArtifactReference registrationReceiptRef =
          requiredReference(registration, "captureReceiptRef");
      ArtifactReference registrationManifestRef =
          requiredReference(registration, "snapshotManifestRef");
      String commitId = requiredSha1(registration, "commitId");
      String declaredRepositoryIdentity = requiredText(registration, "declaredRepositoryIdentity");
      int regularFileCount = requiredNonnegativeInt(registration, "regularFileCount");

      Path snapshotDirectory = workspace.resolve("snapshots").resolve(snapshotId);
      ImmutableBytes receiptBytes = readRegular(snapshotDirectory.resolve("capture-receipt.json"));
      ObjectNode receipt = parseObject(receiptBytes, RECEIPT_FIELDS);
      requireText(receipt, "schemaVersion", RECEIPT_SCHEMA);
      requireText(receipt, "captureReceiptId", registrationReceiptRef.artifactId().value());
      verifySelfIdentifiedDocument(receipt, "captureReceiptId", "capture-receipt");
      verifyReferenceBytes(registrationReceiptRef, receiptBytes, "capture-receipt");
      verifyReceiptMatchesRegistration(
          receipt,
          snapshotId,
          commitId,
          declaredRepositoryIdentity,
          regularFileCount,
          registrationManifestRef);

      ImmutableBytes manifestBytes =
          readRegular(snapshotDirectory.resolve("snapshot-manifest.jsonl"));
      verifyContentAddressedReferenceBytes(
          registrationManifestRef, manifestBytes, "snapshot-manifest");
      List<RegisteredSourceFile> manifestEntries = parseManifest(manifestBytes);
      verifyManifestAccounting(receipt, manifestEntries, regularFileCount);

      return new RegisteredSourceCapture(
          registrationRef,
          declaredRepositoryIdentity,
          commitId,
          snapshotId,
          registrationReceiptRef,
          registrationManifestRef,
          manifestEntries);
    } catch (SourceRegistrationRegistryException failure) {
      throw failure;
    } catch (RuntimeException | IOException failure) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  /**
   * Opens one opaque private byte handle after fresh-reopening the registration and capture proof.
   */
  public RegisteredSourceSnapshot openSnapshot(ArtifactId sourceRegistrationId) {
    RegisteredSourceCapture capture = reopen(sourceRegistrationId);
    try {
      Path snapshotDirectory = workspace.resolve("snapshots").resolve(capture.snapshotId());
      requireNoSymlinkPath(snapshotDirectory);
      if (!Files.isDirectory(snapshotDirectory, LinkOption.NOFOLLOW_LINKS)) {
        throw failure("SOURCE_HANDLE_INVALID");
      }
      return new RegisteredSourceSnapshot(capture, snapshotDirectory);
    } catch (SourceRegistrationRegistryException failure) {
      throw failure;
    } catch (IOException failure) {
      throw failure("SOURCE_HANDLE_INVALID");
    }
  }

  private Path validatedWorkspace(Path candidate) {
    if (candidate == null || !candidate.isAbsolute()) {
      throw failure("SOURCE_HANDLE_INVALID");
    }
    try {
      Path real = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
      requireNoSymlinkPath(real);
      if (!Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)) {
        throw failure("SOURCE_HANDLE_INVALID");
      }
      return real;
    } catch (IOException failure) {
      throw failure("SOURCE_HANDLE_INVALID");
    }
  }

  private ImmutableBytes readRegular(Path path) throws IOException {
    requireNoSymlinkPath(path);
    BasicFileAttributes attributes =
        Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
      throw failure("SOURCE_HANDLE_INVALID");
    }
    if (attributes.size() > MAX_CAPTURE_DOCUMENT_BYTES) {
      throw failure("SOURCE_HANDLE_INVALID");
    }
    return ImmutableBytes.copyOf(Files.readAllBytes(path));
  }

  private ObjectNode parseObject(ImmutableBytes bytes, Set<String> expectedFields) {
    JsonNode parsed = canonicalJson.parseCanonical(bytes);
    if (!(parsed instanceof ObjectNode object)) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    requireExactFields(object, expectedFields);
    return object;
  }

  private void verifySelfIdentifiedDocument(ObjectNode document, String idField, String prefix) {
    ObjectNode withoutId = document.deepCopy();
    withoutId.remove(idField);
    String expected =
        artifactId(prefix, canonicalJson.encodeCanonical(withoutId).copyToByteArray()).value();
    if (!expected.equals(requiredText(document, idField))) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private void verifyReferenceBytes(
      ArtifactReference reference, ImmutableBytes bytes, String expectedPrefix) {
    if (!reference.artifactId().value().startsWith(expectedPrefix + ":")
        || !reference.sha256().value().equals(sha256(bytes.copyToByteArray()))) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private void verifyContentAddressedReferenceBytes(
      ArtifactReference reference, ImmutableBytes bytes, String expectedPrefix) {
    verifyReferenceBytes(reference, bytes, expectedPrefix);
    if (!reference.artifactId().equals(artifactId(expectedPrefix, bytes.copyToByteArray()))) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private void verifyReceiptMatchesRegistration(
      ObjectNode receipt,
      String snapshotId,
      String commitId,
      String declaredRepositoryIdentity,
      int regularFileCount,
      ArtifactReference manifestReference) {
    if (!snapshotId.equals(requiredSnapshotId(receipt, "snapshotId"))
        || !commitId.equals(requiredSha1(receipt, "commitId"))
        || !declaredRepositoryIdentity.equals(requiredText(receipt, "declaredRepositoryIdentity"))
        || regularFileCount != requiredNonnegativeInt(receipt, "regularFileCount")
        || !manifestReference.equals(requiredReference(receipt, "snapshotManifestRef"))
        || !"SHA1".equals(requiredText(receipt, "objectFormat"))
        || !"FORBIDDEN".equals(requiredText(receipt, "worktreeRead"))
        || !"DISABLED".equals(requiredText(receipt, "networkAccess"))
        || requiredNonnegativeInt(receipt, "unsupportedTreeEntryCount") != 0) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private List<RegisteredSourceFile> parseManifest(ImmutableBytes bytes) {
    byte[] raw = bytes.copyToByteArray();
    if (raw.length == 0 || raw[raw.length - 1] != '\n') {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    List<RegisteredSourceFile> entries = new ArrayList<>();
    int start = 0;
    for (int index = 0; index < raw.length; index++) {
      if (raw[index] != '\n') {
        continue;
      }
      if (index == start) {
        throw failure("CAPTURE_IDENTITY_INVALID");
      }
      ObjectNode entry =
          parseObject(
              ImmutableBytes.copyOf(Arrays.copyOfRange(raw, start, index)), MANIFEST_FIELDS);
      requireText(entry, "schemaVersion", MANIFEST_SCHEMA);
      String path = requiredText(entry, "path");
      validatePath(path);
      entries.add(
          new RegisteredSourceFile(
              path,
              requiredText(entry, "gitMode"),
              requiredSha1(entry, "blobObjectId"),
              requiredText(entry, "mediaType"),
              requiredNonnegativeLong(entry, "sizeBytes"),
              Sha256Digest.parse(requiredText(entry, "sha256")),
              requiredText(entry, "analysisDisposition"),
              requiredNullableText(entry, "textEncoding")));
      start = index + 1;
    }
    entries.sort(Comparator.comparing(RegisteredSourceFile::path, UTF8_ORDER));
    String previous = null;
    for (RegisteredSourceFile entry : entries) {
      if (entry.path().equals(previous)) {
        throw failure("CAPTURE_IDENTITY_INVALID");
      }
      previous = entry.path();
    }
    return List.copyOf(entries);
  }

  private void verifyManifestAccounting(
      ObjectNode receipt, List<RegisteredSourceFile> entries, int registrationFileCount) {
    long text =
        entries.stream()
            .filter(entry -> entry.analysisDisposition().equals("ANALYZABLE_TEXT"))
            .count();
    long media =
        entries.stream()
            .filter(entry -> entry.analysisDisposition().equals("NON_ANALYZABLE_MEDIA"))
            .count();
    if (entries.size() != registrationFileCount
        || entries.size() != requiredNonnegativeInt(receipt, "regularFileCount")
        || text != requiredNonnegativeInt(receipt, "analyzableTextFileCount")
        || media != requiredNonnegativeInt(receipt, "nonAnalyzableMediaFileCount")
        || text + media != entries.size()) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private static ArtifactReference requiredReference(ObjectNode parent, String fieldName) {
    JsonNode node = parent.get(fieldName);
    if (!(node instanceof ObjectNode reference)) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    requireExactFields(reference, REFERENCE_FIELDS);
    return new ArtifactReference(
        ArtifactId.parse(requiredText(reference, "artifactId")),
        Sha256Digest.parse(requiredText(reference, "sha256")));
  }

  private static void requireExactFields(ObjectNode object, Set<String> expectedFields) {
    Set<String> actualFields = new HashSet<>();
    object.fieldNames().forEachRemaining(actualFields::add);
    if (!actualFields.equals(expectedFields)) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private static String requiredText(ObjectNode object, String fieldName) {
    JsonNode value = object.get(fieldName);
    if (value == null || !value.isTextual()) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    return value.textValue();
  }

  private static void requireText(ObjectNode object, String fieldName, String expectedValue) {
    if (!expectedValue.equals(requiredText(object, fieldName))) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private static String requiredNullableText(ObjectNode object, String fieldName) {
    JsonNode value = object.get(fieldName);
    if (value == null || (!value.isTextual() && !value.isNull())) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    return value.isNull() ? null : value.textValue();
  }

  private static String requiredSha1(ObjectNode object, String fieldName) {
    String value = requiredText(object, fieldName);
    if (!value.matches("[0-9a-f]{40}")) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    return value;
  }

  private static String requiredSnapshotId(ObjectNode object, String fieldName) {
    String value = requiredText(object, fieldName);
    if (!value.matches("snapshot:[0-9a-f]{64}")) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    return value;
  }

  private static int requiredNonnegativeInt(ObjectNode object, String fieldName) {
    JsonNode value = object.get(fieldName);
    if (value == null || !value.canConvertToInt() || value.asLong() < 0L) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    return value.intValue();
  }

  private static long requiredNonnegativeLong(ObjectNode object, String fieldName) {
    JsonNode value = object.get(fieldName);
    if (value == null || !value.canConvertToLong() || value.asLong() < 0L) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    return value.longValue();
  }

  private static void validatePath(String path) {
    if (path.isBlank()
        || path.startsWith("/")
        || path.indexOf('\\') >= 0
        || Arrays.stream(path.split("/", -1))
            .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private static void requireNoSymlinkPath(Path path) throws IOException {
    Path current = path.getRoot();
    for (Path segment : path) {
      current = current.resolve(segment);
      if (Files.isSymbolicLink(current)) {
        throw failure("SOURCE_HANDLE_INVALID");
      }
    }
  }

  private static ArtifactId artifactId(String prefix, byte[] bytes) {
    return ArtifactId.parse(
        prefix + ":" + sha256(concatenate(frame(prefix + "-id-v1"), frame(bytes))));
  }

  private static int compareUtf8(String left, String right) {
    byte[] leftBytes = left.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] rightBytes = right.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
    return frame(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static SourceRegistrationRegistryException failure(String code) {
    return new SourceRegistrationRegistryException(code);
  }
}
