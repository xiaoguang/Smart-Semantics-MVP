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
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
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
  private static final Pattern TREE_ENTRY =
      Pattern.compile("(100644|100755|120000|160000|[0-9]{6}) ([a-z]+) ([0-9a-f]{40})");
  private static final Pattern CONFIG_INCLUDE_SECTION =
      Pattern.compile("(?m)^\\s*\\[\\s*include(?:if)?(?:\\s|\\])");
  private static final Comparator<String> UTF8_ORDER =
      (left, right) ->
          compareUnsigned(
              left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);

  private final Path captureWorkspace;
  private final Path gitExecutable;
  private final CanonicalJsonCodec canonicalJson;

  /**
   * Creates an adapter with an explicit absolute Git executable, primarily for controlled hosts.
   */
  public LocalGitCommitCaptureAdapter(Path captureWorkspace, Path gitExecutable) {
    this.captureWorkspace = validatedPrivateDirectory(captureWorkspace);
    this.gitExecutable = validatedGitExecutable(gitExecutable);
    this.canonicalJson = new CanonicalJsonCodec();
  }

  @Override
  public SourceRegistrationReference capture(LocalGitCaptureRequest request) {
    Objects.requireNonNull(request, "request");
    try {
      Path repository = request.repositoryPath().toRealPath(LinkOption.NOFOLLOW_LINKS);
      Path gitDirectory = validatedGitDirectory(repository);
      rejectUnsupportedObjectSources(gitDirectory);
      verifyCommitExists(gitDirectory, request.commitId());
      requireSha1ObjectFormat(gitDirectory);

      List<CapturedEntry> entries = captureEntries(gitDirectory, request.commitId());
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
          receiptBytes(request, gitDirectory, snapshotId, manifestReference, entries);
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

  private Path validatedPrivateDirectory(Path candidate) {
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

  private Path validatedGitExecutable(Path candidate) {
    if (candidate == null || !candidate.isAbsolute()) {
      throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID");
    }
    try {
      Path real = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
      if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS) || !Files.isExecutable(real)) {
        throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID");
      }
      return real;
    } catch (IOException exception) {
      throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID");
    }
  }

  private Path validatedGitDirectory(Path repository) throws IOException {
    if (!Files.isDirectory(repository, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(repository)) {
      throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID");
    }
    Path gitDirectory = repository.resolve(".git");
    if (!Files.isDirectory(gitDirectory, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(gitDirectory)) {
      throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID");
    }
    Path real = gitDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
    requireNoSymlinkPath(real);
    if (!Files.isDirectory(real.resolve("objects"), LinkOption.NOFOLLOW_LINKS)) {
      throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID");
    }
    return real;
  }

  private void rejectUnsupportedObjectSources(Path gitDirectory) throws IOException {
    if (Files.exists(gitDirectory.resolve("objects/info/alternates"), LinkOption.NOFOLLOW_LINKS)) {
      throw new LocalGitCaptureException("LOCAL_GIT_ALTERNATES_UNSUPPORTED");
    }
    if (Files.exists(gitDirectory.resolve("shallow"), LinkOption.NOFOLLOW_LINKS)
        || Files.exists(gitDirectory.resolve("info/grafts"), LinkOption.NOFOLLOW_LINKS)
        || containsAnyFile(gitDirectory.resolve("refs/replace"))) {
      throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
    }
    Path config = gitDirectory.resolve("config");
    if (Files.exists(config, LinkOption.NOFOLLOW_LINKS)) {
      requireRegularNoFollow(config);
      String content =
          Files.readString(config, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
      if (content.contains("partialclone")
          || content.contains("promisor")
          || CONFIG_INCLUDE_SECTION.matcher(content).find()) {
        throw new LocalGitCaptureException("LOCAL_GIT_PROMISOR_UNSUPPORTED");
      }
    }
  }

  private boolean containsAnyFile(Path directory) throws IOException {
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    requireNoSymlinkPath(directory);
    try (var children = Files.list(directory)) {
      return children.findAny().isPresent();
    }
  }

  private void verifyCommitExists(Path gitDirectory, String commitId) {
    runGit(gitDirectory, List.of("cat-file", "-e", commitId + "^{commit}"), false);
  }

  private List<CapturedEntry> captureEntries(Path gitDirectory, String commitId) {
    byte[] treeListing =
        runGit(gitDirectory, List.of("ls-tree", "-r", "-z", "--full-tree", commitId), true);
    List<CapturedEntry> entries = new ArrayList<>();
    for (byte[] rawEntry : splitNul(treeListing)) {
      TreeEntry treeEntry = parseTreeEntry(rawEntry);
      if (treeEntry.mode().equals("120000")
          || treeEntry.mode().equals("160000")
          || !treeEntry.type().equals("blob")) {
        throw new LocalGitCaptureException("LOCAL_GIT_TREE_ENTRY_UNSUPPORTED");
      }
      if (!treeEntry.mode().equals("100644") && !treeEntry.mode().equals("100755")) {
        throw new LocalGitCaptureException("LOCAL_GIT_TREE_ENTRY_UNSUPPORTED");
      }
      byte[] bytes = runGit(gitDirectory, List.of("cat-file", "blob", treeEntry.objectId()), true);
      entries.add(CapturedEntry.from(treeEntry, bytes));
    }
    return entries;
  }

  private TreeEntry parseTreeEntry(byte[] rawEntry) {
    int tab = indexOf(rawEntry, (byte) '\t');
    if (tab <= 0 || tab == rawEntry.length - 1) {
      throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
    }
    String header = strictUtf8(Arrays.copyOfRange(rawEntry, 0, tab));
    var matcher = TREE_ENTRY.matcher(header);
    if (!matcher.matches()) {
      throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
    }
    String path = strictUtf8(Arrays.copyOfRange(rawEntry, tab + 1, rawEntry.length));
    validateRepositoryRelativePath(path);
    return new TreeEntry(matcher.group(1), matcher.group(2), matcher.group(3), path);
  }

  private void validateRepositoryRelativePath(String path) {
    if (path.isBlank()
        || path.startsWith("/")
        || path.indexOf('\\') >= 0
        || path.indexOf('\u0000') >= 0
        || Arrays.stream(path.split("/", -1))
            .anyMatch(
                segment -> segment.isEmpty() || segment.equals(".") || segment.equals(".."))) {
      throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
    }
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
      Path gitDirectory,
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
    receipt.put("objectFormat", objectFormat(gitDirectory));
    receipt.put("regularFileCount", entries.size());
    receipt.put("schemaVersion", RECEIPT_SCHEMA);
    receipt.put("snapshotId", snapshotId);
    receipt.set("snapshotManifestRef", referenceNode(manifestReference));
    receipt.put("treeObjectId", treeObjectId(gitDirectory, request.commitId()));
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

  private byte[] runGit(Path gitDirectory, List<String> arguments, boolean captureStdout) {
    Path privateHome = null;
    try {
      privateHome = Files.createTempDirectory(captureWorkspace, ".git-env-");
      Files.createFile(privateHome.resolve("global-config"));
      Files.createDirectory(privateHome.resolve("xdg"));
      List<String> command = new ArrayList<>();
      command.add(gitExecutable.toString());
      command.add("--git-dir=" + gitDirectory);
      command.add("--no-replace-objects");
      command.addAll(arguments);
      ProcessBuilder builder = new ProcessBuilder(command);
      Map<String, String> environment = builder.environment();
      environment.clear();
      environment.put("LC_ALL", "C");
      environment.put("LANG", "C");
      environment.put("HOME", privateHome.toString());
      environment.put("XDG_CONFIG_HOME", privateHome.resolve("xdg").toString());
      environment.put("GIT_CONFIG_NOSYSTEM", "1");
      environment.put("GIT_CONFIG_GLOBAL", privateHome.resolve("global-config").toString());
      environment.put("GIT_NO_LAZY_FETCH", "1");
      environment.put("GIT_TERMINAL_PROMPT", "0");
      environment.put("GIT_OPTIONAL_LOCKS", "0");
      environment.put("GIT_PAGER", "cat");
      environment.put("PAGER", "cat");
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      Process process = builder.start();
      byte[] stdout = captureStdout ? process.getInputStream().readAllBytes() : new byte[0];
      boolean completed =
          process.waitFor(COMMAND_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      if (!completed) {
        process.destroyForcibly();
        throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
      }
      if (process.exitValue() != 0) {
        throw new LocalGitCaptureException("LOCAL_GIT_COMMIT_NOT_FOUND");
      }
      return stdout;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
    } catch (IOException exception) {
      throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID");
    } finally {
      if (privateHome != null) {
        deleteStaging(privateHome);
      }
    }
  }

  private String treeObjectId(Path gitDirectory, String commitId) {
    return strictUtf8(runGit(gitDirectory, List.of("rev-parse", commitId + "^{tree}"), true))
        .trim();
  }

  private String objectFormat(Path gitDirectory) {
    return strictUtf8(runGit(gitDirectory, List.of("rev-parse", "--show-object-format"), true))
            .trim()
            .equals("sha1")
        ? "SHA1"
        : "SHA256";
  }

  private void requireSha1ObjectFormat(Path gitDirectory) {
    if (!objectFormat(gitDirectory).equals("SHA1")) {
      throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID");
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

  private static List<byte[]> splitNul(byte[] bytes) {
    List<byte[]> result = new ArrayList<>();
    int start = 0;
    for (int index = 0; index < bytes.length; index++) {
      if (bytes[index] == 0) {
        result.add(Arrays.copyOfRange(bytes, start, index));
        start = index + 1;
      }
    }
    if (start != bytes.length) {
      throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT");
    }
    return result;
  }

  private static int indexOf(byte[] bytes, byte target) {
    for (int index = 0; index < bytes.length; index++) {
      if (bytes[index] == target) {
        return index;
      }
    }
    return -1;
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

  private static void requireNoSymlinkPath(Path path) throws IOException {
    Path current = path.getRoot();
    for (Path segment : path) {
      current = current.resolve(segment);
      if (Files.isSymbolicLink(current)) {
        throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID");
      }
    }
  }

  private static void requireRegularNoFollow(Path path) throws IOException {
    BasicFileAttributes attributes =
        Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
      throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID");
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

  private record TreeEntry(String mode, String type, String objectId, String path) {}

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

    private static CapturedEntry from(TreeEntry treeEntry, byte[] bytes) {
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
