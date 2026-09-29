package org.sourceanalysis.app.capture.localgit;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * Constrained Git-plumbing capture for a local, immutable commit tree.
 *
 * <p>The adapter never invokes a shell, does not inherit ambient process variables, and reads only
 * raw objects through a validated local Git directory. Its capture workspace is a private storage
 * dependency and never enters public identities or JSON artifacts.
 */
public final class LocalGitCommitCaptureAdapter implements LocalSourceCapture {

  private static final String RECEIPT_SCHEMA = "local-git-capture-receipt-v1";
  private static final String REGISTRATION_SCHEMA = "source-registration-v1";
  private static final String MANIFEST_SCHEMA = "local-git-snapshot-entry-v1";
  private static final Comparator<String> UTF8_ORDER =
      (left, right) ->
          compareUnsigned(
              left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  private final Path captureWorkspace;
  private final FixedGitObjectAccess gitObjectAccess;
  private final CanonicalJsonCodec canonicalJson;

  /**
   * Creates an adapter with an explicit absolute Git executable, primarily for controlled hosts.
   */
  public LocalGitCommitCaptureAdapter(Path captureWorkspace, Path gitExecutable) {
    this(captureWorkspace, newObjectAccess(captureWorkspace, gitExecutable));
  }

  LocalGitCommitCaptureAdapter(Path captureWorkspace, FixedGitObjectAccess gitObjectAccess) {
    this.captureWorkspace = validatedPrivateDirectory(captureWorkspace);
    this.gitObjectAccess = Objects.requireNonNull(gitObjectAccess, "gitObjectAccess");
    this.canonicalJson = new CanonicalJsonCodec();
  }

  private static FixedGitObjectAccess newObjectAccess(Path workspace, Path executable) {
    Path validatedWorkspace = validatedPrivateDirectory(workspace);
    try {
      return new ConstrainedGitObjectAccess(validatedWorkspace.resolve("git-objects"), executable);
    } catch (IOException failure) {
      throw gitFailure(failure);
    }
  }

  @Override
  public SourceRegistrationReference capture(LocalGitCaptureRequest request) {
    Objects.requireNonNull(request, "request");
    try {
      GitCapture gitCapture = readCommit(request);
      List<CapturedEntry> entries = gitCapture.entries();
      if (entries.isEmpty()) {
        throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
      }
      entries.sort(Comparator.comparing(CapturedEntry::path, UTF8_ORDER));
      ensureDistinctPaths(entries);

      ImmutableBytes manifest = manifestBytes(entries);
      ArtifactReference manifestReference =
          artifactReference("snapshot-manifest", manifest.copyToByteArray());
      String snapshotId = snapshotId(request, entries, manifestReference);
      IdentifiedDocument receipt =
          receiptBytes(request, gitCapture.treeObjectId(), snapshotId, manifestReference, entries);
      ArtifactReference receiptReference =
          new ArtifactReference(
              receipt.id(), new Sha256Digest(sha256(receipt.bytes().copyToByteArray())));
      IdentifiedDocument registration =
          registrationBytes(request, snapshotId, manifestReference, receiptReference, entries);

      installSnapshot(snapshotId, manifest, receipt.bytes(), entries);
      installRegistration(registration.id(), registration.bytes());
      return new SourceRegistrationReference(
          registration.id(), snapshotId, manifestReference, receiptReference);
    } catch (LocalGitCaptureException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED");
    }
  }

  private GitCapture readCommit(LocalGitCaptureRequest request) {
    try (FixedGitObjectAccess.Session session =
        gitObjectAccess.open(request.repositoryPath(), request.commitId())) {
      List<CapturedEntry> entries = captureEntries(session);
      return new GitCapture(session.treeObjectId(), entries);
    } catch (IOException failure) {
      throw gitFailure(failure);
    }
  }

  private static LocalGitCaptureException gitFailure(IOException failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof LocalGitCaptureException captureFailure) {
        return captureFailure;
      }
    }
    return new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
  }

  private static Path validatedPrivateDirectory(Path candidate) {
    if (candidate == null || !candidate.isAbsolute()) {
      throw new LocalGitCaptureException("LOCAL_GIT_REQUEST_INVALID");
    }
    try {
      Files.createDirectories(candidate);
      Path real = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
      if (!Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(real)) {
        throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED");
      }
      return real;
    } catch (IOException exception) {
      throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED");
    }
  }

  private List<CapturedEntry> captureEntries(FixedGitObjectAccess.Session session)
      throws IOException {
    List<CapturedEntry> entries = new ArrayList<>();
    try (var tree = session.entries()) {
      for (FixedGitObjectAccess.TreeEntry treeEntry : tree) {
        if (treeEntry.mode().equals("120000")
            || treeEntry.mode().equals("160000")
            || !treeEntry.type().equals("blob")) {
          throw new LocalGitCaptureException("LOCAL_GIT_TREE_ENTRY_UNSUPPORTED");
        }
        if (!treeEntry.mode().equals("100644") && !treeEntry.mode().equals("100755")) {
          throw new LocalGitCaptureException("LOCAL_GIT_TREE_ENTRY_UNSUPPORTED");
        }
        try (FixedGitObjectAccess.BlobInput blob = session.openBlob(treeEntry.objectId())) {
          byte[] bytes = blob.stream().readAllBytes();
          if (bytes.length != blob.sizeBytes()) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
          }
          entries.add(CapturedEntry.from(treeEntry, bytes));
        }
      }
    }
    return entries;
  }

  private ImmutableBytes manifestBytes(List<CapturedEntry> entries) {
    StringBuilder lines = new StringBuilder();
    for (CapturedEntry entry : entries) {
      ObjectNode node = JsonNodeFactory.instance.objectNode();
      node.put("analysisDisposition", entry.analysisDisposition());
      node.put("blobObjectId", entry.objectId());
      node.put("gitMode", entry.mode());
      node.put("mediaType", entry.mediaType());
      node.put("path", entry.path());
      node.put("schemaVersion", MANIFEST_SCHEMA);
      node.put("sha256", entry.sha256());
      node.put("sizeBytes", entry.bytes().length);
      if (entry.textEncoding() == null) {
        node.putNull("textEncoding");
      } else {
        node.put("textEncoding", entry.textEncoding());
      }
      lines.append(
          new String(
              canonicalJson.encodeCanonical(node).copyToByteArray(), StandardCharsets.UTF_8));
      lines.append('\n');
    }
    return ImmutableBytes.copyOf(lines.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String snapshotId(
      LocalGitCaptureRequest request,
      List<CapturedEntry> entries,
      ArtifactReference manifestReference) {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("commitId", request.commitId());
    identity.put("declaredRepositoryIdentity", request.declaredRepositoryIdentity());
    identity.set("capturePolicyRef", referenceNode(request.capturePolicyRef()));
    identity.set("resourceBudgetRef", referenceNode(request.resourceBudgetRef()));
    identity.set("snapshotManifestRef", referenceNode(manifestReference));
    ArrayNode identityEntries = identity.putArray("entries");
    for (CapturedEntry entry : entries) {
      ObjectNode node = identityEntries.addObject();
      node.put("analysisDisposition", entry.analysisDisposition());
      node.put("gitMode", entry.mode());
      node.put("mediaType", entry.mediaType());
      node.put("path", entry.path());
      node.put("sha256", entry.sha256());
      node.put("sizeBytes", entry.bytes().length);
      if (entry.textEncoding() == null) {
        node.putNull("textEncoding");
      } else {
        node.put("textEncoding", entry.textEncoding());
      }
    }
    return "snapshot:"
        + sha256(
            frame("local-git-snapshot-id-v1"),
            frame(canonicalJson.encodeCanonical(identity).copyToByteArray()));
  }

  private IdentifiedDocument receiptBytes(
      LocalGitCaptureRequest request,
      String treeObjectId,
      String snapshotId,
      ArtifactReference manifestReference,
      List<CapturedEntry> entries) {
    ObjectNode receipt = JsonNodeFactory.instance.objectNode();
    receipt.put("analyzableTextFileCount", count(entries, "ANALYZABLE_TEXT"));
    receipt.set("capturePolicyRef", referenceNode(request.capturePolicyRef()));
    receipt.put("commitId", request.commitId());
    receipt.put("declaredRepositoryIdentity", request.declaredRepositoryIdentity());
    receipt.put("networkAccess", "DISABLED");
    receipt.put("nonAnalyzableMediaFileCount", count(entries, "NON_ANALYZABLE_MEDIA"));
    receipt.put("objectFormat", "SHA1");
    receipt.put("regularFileCount", entries.size());
    receipt.put("schemaVersion", RECEIPT_SCHEMA);
    receipt.put("snapshotId", snapshotId);
    receipt.set("snapshotManifestRef", referenceNode(manifestReference));
    receipt.put("treeObjectId", treeObjectId);
    receipt.put("unsupportedTreeEntryCount", 0);
    receipt.put("worktreeRead", "FORBIDDEN");
    ArtifactId receiptId =
        artifactId("capture-receipt", canonicalJson.encodeCanonical(receipt).copyToByteArray());
    receipt.put("captureReceiptId", receiptId.value());
    return new IdentifiedDocument(receiptId, canonicalJson.encodeCanonical(receipt));
  }

  private IdentifiedDocument registrationBytes(
      LocalGitCaptureRequest request,
      String snapshotId,
      ArtifactReference manifestReference,
      ArtifactReference receiptReference,
      List<CapturedEntry> entries) {
    ObjectNode registration = JsonNodeFactory.instance.objectNode();
    registration.set("captureReceiptRef", referenceNode(receiptReference));
    registration.put("commitId", request.commitId());
    registration.put("declaredRepositoryIdentity", request.declaredRepositoryIdentity());
    registration.put("regularFileCount", entries.size());
    registration.put("schemaVersion", REGISTRATION_SCHEMA);
    registration.put("snapshotId", snapshotId);
    registration.set("snapshotManifestRef", referenceNode(manifestReference));
    ArtifactId registrationId =
        artifactId(
            "source-registration", canonicalJson.encodeCanonical(registration).copyToByteArray());
    registration.put("sourceRegistrationId", registrationId.value());
    return new IdentifiedDocument(registrationId, canonicalJson.encodeCanonical(registration));
  }

  private void installSnapshot(
      String snapshotId,
      ImmutableBytes manifest,
      ImmutableBytes receipt,
      List<CapturedEntry> entries)
      throws IOException {
    Path snapshots = captureWorkspace.resolve("snapshots");
    Files.createDirectories(snapshots);
    Path destination = snapshots.resolve(snapshotId);
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      verifyInstalledSnapshot(destination, manifest, receipt, entries);
      return;
    }
    Path staging = snapshots.resolve(".staging-" + UUID.randomUUID());
    Files.createDirectory(staging);
    try {
      Files.write(staging.resolve("snapshot-manifest.jsonl"), manifest.copyToByteArray());
      Files.write(staging.resolve("capture-receipt.json"), receipt.copyToByteArray());
      Path blobs = staging.resolve("blobs");
      Files.createDirectory(blobs);
      for (CapturedEntry entry : entries) {
        Files.write(blobs.resolve(entry.sha256()), entry.bytes());
      }
      moveAtomically(staging, destination);
    } catch (IOException exception) {
      deleteStaging(staging);
      throw exception;
    }
  }

  private void installRegistration(ArtifactId registrationId, ImmutableBytes registration)
      throws IOException {
    Path registrations = captureWorkspace.resolve("registrations");
    Files.createDirectories(registrations);
    Path destination = registrations.resolve(registrationId.value());
    Path file = destination.resolve("source-registration.json");
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)
          || !Arrays.equals(Files.readAllBytes(file), registration.copyToByteArray())) {
        throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED");
      }
      return;
    }
    Path staging = registrations.resolve(".staging-" + UUID.randomUUID());
    Files.createDirectory(staging);
    try {
      Files.write(staging.resolve("source-registration.json"), registration.copyToByteArray());
      moveAtomically(staging, destination);
    } catch (IOException exception) {
      deleteStaging(staging);
      throw exception;
    }
  }

  private void verifyInstalledSnapshot(
      Path destination,
      ImmutableBytes manifest,
      ImmutableBytes receipt,
      List<CapturedEntry> entries)
      throws IOException {
    if (!Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)
        || !Arrays.equals(
            Files.readAllBytes(destination.resolve("snapshot-manifest.jsonl")),
            manifest.copyToByteArray())
        || !Arrays.equals(
            Files.readAllBytes(destination.resolve("capture-receipt.json")),
            receipt.copyToByteArray())) {
      throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED");
    }
    for (CapturedEntry entry : entries) {
      if (!Arrays.equals(
          Files.readAllBytes(destination.resolve("blobs").resolve(entry.sha256())),
          entry.bytes())) {
        throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED");
      }
    }
  }

  private void moveAtomically(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED");
    }
  }

  private void deleteStaging(Path staging) {
    try (var paths = Files.walk(staging)) {
      paths.sorted(Comparator.reverseOrder()).forEach(this::deleteIfPresent);
    } catch (IOException ignored) {
      // A failed private staging cleanup cannot alter a public capture result.
    }
  }

  private void deleteIfPresent(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // The next fresh capture validates the addressed destination; stale staging is never read.
    }
  }

  private ObjectNode referenceNode(ArtifactReference reference) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("artifactId", reference.artifactId().value());
    node.put("sha256", reference.sha256().value());
    return node;
  }

  private ArtifactReference artifactReference(String prefix, byte[] bytes) {
    return new ArtifactReference(artifactId(prefix, bytes), new Sha256Digest(sha256(bytes)));
  }

  private ArtifactId artifactId(String prefix, byte[] bytes) {
    return new ArtifactId(prefix + ":" + sha256(frame(prefix + "-id-v1"), frame(bytes)));
  }

  private int count(List<CapturedEntry> entries, String disposition) {
    return (int)
        entries.stream().filter(entry -> entry.analysisDisposition().equals(disposition)).count();
  }

  private static byte[] frame(String text) {
    return frame(text.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] bytes) {
    return ByteBuffer.allocate(Long.BYTES + bytes.length).putLong(bytes.length).put(bytes).array();
  }

  private static String sha256(byte[]... parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] part : parts) {
        digest.update(part);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
    }
  }

  private static String strictUtf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException exception) {
      throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
    }
  }

  private static boolean isAnalyzableText(byte[] bytes) {
    for (byte value : bytes) {
      int unsigned = Byte.toUnsignedInt(value);
      if (unsigned == 0
          || (unsigned < 0x20 && unsigned != '\n' && unsigned != '\r' && unsigned != '\t')) {
        return false;
      }
    }
    try {
      strictUtf8(bytes);
      return true;
    } catch (LocalGitCaptureException exception) {
      return false;
    }
  }

  private static void ensureDistinctPaths(List<CapturedEntry> entries) {
    String previous = null;
    for (CapturedEntry entry : entries) {
      if (entry.path().equals(previous)) {
        throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
      }
      previous = entry.path();
    }
  }

  private static int compareUnsigned(byte[] left, byte[] right) {
    int common = Math.min(left.length, right.length);
    for (int index = 0; index < common; index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private record GitCapture(String treeObjectId, List<CapturedEntry> entries) {}

  private record IdentifiedDocument(ArtifactId id, ImmutableBytes bytes) {}

  private record CapturedEntry(
      String mode,
      String objectId,
      String path,
      byte[] bytes,
      String sha256,
      String mediaType,
      String analysisDisposition,
      String textEncoding) {

    private static CapturedEntry from(FixedGitObjectAccess.TreeEntry treeEntry, byte[] bytes) {
      boolean text = isAnalyzableText(bytes);
      return new CapturedEntry(
          treeEntry.mode(),
          treeEntry.objectId(),
          treeEntry.path(),
          Arrays.copyOf(bytes, bytes.length),
          LocalGitCommitCaptureAdapter.sha256(bytes),
          text ? "text/plain" : "application/octet-stream",
          text ? "ANALYZABLE_TEXT" : "NON_ANALYZABLE_MEDIA",
          text ? "UTF-8" : null);
    }
  }
}
