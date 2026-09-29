package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * Filesystem-backed private staging for preparation bytes.
 *
 * <p>Only {@link #complete()} promotes a temporary write to the stable staging path. Failed or
 * unstable source reads leave their temporary bytes available for private diagnostics and never
 * return an accepted blob reference.
 */
final class StagedSourcePreparationBlobSink implements SourcePreparationBlobSink {

  private final Path outputRoot;
  private final Path blobRoot;

  StagedSourcePreparationBlobSink(Path outputRoot) throws IOException {
    this.outputRoot = requirePrivateOutputRoot(outputRoot);
    this.blobRoot = this.outputRoot.resolve("source-preparation-blobs");
    ensurePrivateDirectories(blobRoot);
  }

  @Override
  public SourcePreparationBlobWriter begin(String relativePath, long expectedSizeBytes)
      throws IOException {
    if (expectedSizeBytes < 0L) {
      throw new IOException("source preparation blob size cannot be negative");
    }
    Path relative = safeRelativePath(relativePath);
    Path destination = blobRoot.resolve(relative).normalize();
    if (!destination.startsWith(blobRoot)) {
      throw new IOException("source preparation blob path escapes staging");
    }
    Path parent = destination.getParent();
    Path fileName = destination.getFileName();
    if (parent == null || fileName == null) {
      throw new IOException("source preparation blob path has no staging parent");
    }
    ensurePrivateDirectories(parent);
    Path temporary = destination.resolveSibling(fileName + ".partial-" + UUID.randomUUID());
    OutputStream output =
        Files.newOutputStream(
            temporary,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS);
    return new StagedBlobWriter(output, temporary, destination, expectedSizeBytes);
  }

  private static Path requirePrivateOutputRoot(Path candidate) throws IOException {
    Objects.requireNonNull(candidate, "source preparation output root");
    Path normalized = candidate.toAbsolutePath().normalize();
    ensureNoFollowDirectoryPath(normalized);
    return normalized;
  }

  private static void ensureNoFollowDirectoryPath(Path directory) throws IOException {
    Path root = directory.getRoot();
    if (root == null) {
      throw new IOException("source preparation output root must be absolute");
    }
    BasicFileAttributes rootAttributes =
        Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!rootAttributes.isDirectory() || rootAttributes.isSymbolicLink()) {
      throw new IOException("source preparation output root has an unsafe filesystem root");
    }
    Path current = root;
    for (Path segment : root.relativize(directory)) {
      current = current.resolve(segment);
      try {
        BasicFileAttributes attributes =
            Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
          throw new IOException("source preparation output root is not a real directory");
        }
      } catch (NoSuchFileException missing) {
        try {
          Files.createDirectory(current);
        } catch (FileAlreadyExistsException raced) {
          // A no-follow recheck below decides whether the raced entry is safe.
          if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            throw raced;
          }
        }
        BasicFileAttributes attributes =
            Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
          throw new IOException("source preparation output root is not a real directory");
        }
      }
    }
  }

  private static Path safeRelativePath(String path) throws IOException {
    if (path == null
        || path.isBlank()
        || path.startsWith("/")
        || path.indexOf('\\') >= 0
        || path.indexOf('\u0000') >= 0) {
      throw new IOException("source preparation blob path is not canonical");
    }
    Path result = Path.of("");
    for (String segment : path.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        throw new IOException("source preparation blob path is not canonical");
      }
      result = result.resolve(segment);
    }
    return result;
  }

  private void ensurePrivateDirectories(Path directory) throws IOException {
    if (!directory.startsWith(outputRoot)) {
      throw new IOException("source preparation staging directory escapes output root");
    }
    Path current = outputRoot;
    for (Path segment : outputRoot.relativize(directory)) {
      current = current.resolve(segment);
      ensureOnePrivateDirectory(current);
    }
  }

  private void ensureOnePrivateDirectory(Path directory) throws IOException {
    try {
      BasicFileAttributes attributes =
          Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
        throw new IOException("source preparation staging directory is unsafe");
      }
      return;
    } catch (NoSuchFileException missing) {
      // Create only this checked child. The no-follow recheck below rejects a raced symlink.
      try {
        Files.createDirectory(directory);
      } catch (FileAlreadyExistsException raced) {
        // The no-follow recheck below decides whether the raced entry is safe.
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
          throw raced;
        }
      }
    }
    BasicFileAttributes attributes =
        Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
      throw new IOException("source preparation staging directory is unsafe");
    }
  }

  private static final class StagedBlobWriter implements SourcePreparationBlobWriter {

    private final OutputStream output;
    private final Path temporary;
    private final Path destination;
    private final long expectedSizeBytes;
    private final MessageDigest digest;
    private long sizeBytes;
    private boolean closed;
    private boolean completed;

    private StagedBlobWriter(
        OutputStream output, Path temporary, Path destination, long expectedSizeBytes) {
      this.output = output;
      this.temporary = temporary;
      this.destination = destination;
      this.expectedSizeBytes = expectedSizeBytes;
      try {
        this.digest = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException unavailable) {
        throw new IllegalStateException("SHA-256 is unavailable", unavailable);
      }
    }

    @Override
    public void write(ByteBuffer chunk) throws IOException {
      if (closed || completed) {
        throw new IOException("source preparation blob writer is closed");
      }
      ByteBuffer copy = chunk.slice();
      byte[] bytes = new byte[copy.remaining()];
      copy.get(bytes);
      output.write(bytes);
      digest.update(bytes);
      sizeBytes = Math.addExact(sizeBytes, bytes.length);
    }

    @Override
    public ArtifactReference complete() throws IOException {
      if (completed) {
        throw new IOException("source preparation blob writer completed twice");
      }
      if (closed) {
        throw new IOException("source preparation blob writer is closed");
      }
      if (sizeBytes != expectedSizeBytes) {
        throw new IOException("source preparation blob size differs from expected source bytes");
      }
      output.close();
      closed = true;
      try {
        Files.move(
            temporary,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }
      completed = true;
      String sha256 = HexFormat.of().formatHex(digest.digest());
      return new ArtifactReference(
          ArtifactId.parse("source-blob:" + sha256), Sha256Digest.parse(sha256));
    }

    @Override
    public void close() throws IOException {
      if (!closed) {
        output.close();
        closed = true;
      }
    }
  }
}
