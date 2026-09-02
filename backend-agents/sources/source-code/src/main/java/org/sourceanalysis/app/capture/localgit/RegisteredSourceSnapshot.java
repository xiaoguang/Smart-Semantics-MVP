package org.sourceanalysis.app.capture.localgit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Opaque, private source-byte handle for one fresh-reopened registered capture.
 *
 * <p>The handle exposes no storage location. Only inventory code may use it to obtain bytes for a
 * manifest member; it never reads a worktree or accepts a caller-supplied path.
 */
public final class RegisteredSourceSnapshot {

  private final RegisteredSourceCapture capture;
  private final Path snapshotDirectory;

  RegisteredSourceSnapshot(RegisteredSourceCapture capture, Path snapshotDirectory) {
    this.capture = Objects.requireNonNull(capture, "registered source capture");
    this.snapshotDirectory =
        Objects.requireNonNull(snapshotDirectory, "private snapshot directory");
  }

  /** Returns the already-verified, path-free registration metadata for this private handle. */
  public RegisteredSourceCapture capture() {
    return capture;
  }

  /**
   * Reads one exact manifest-member blob without exposing its private location.
   *
   * @throws SourceRegistrationRegistryException when the requested entry is not a member or the
   *     sealed blob is missing, non-regular, a symlink, or has a different declared size
   */
  public ImmutableBytes read(RegisteredSourceFile manifestEntry) {
    if (manifestEntry == null || !capture.manifestEntries().contains(manifestEntry)) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    try {
      Path blob = snapshotDirectory.resolve("blobs").resolve(manifestEntry.sha256().value());
      requireNoSymlinkPath(blob);
      BasicFileAttributes attributes =
          Files.readAttributes(blob, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
        throw failure("SOURCE_NOT_REGULAR");
      }
      if (attributes.size() != manifestEntry.sizeBytes()) {
        throw failure("SOURCE_SIZE_MISMATCH");
      }
      return ImmutableBytes.copyOf(Files.readAllBytes(blob));
    } catch (SourceRegistrationRegistryException failure) {
      throw failure;
    } catch (IOException failure) {
      throw failure("SOURCE_HANDLE_INVALID");
    }
  }

  private static void requireNoSymlinkPath(Path path) throws IOException {
    Path current = path.getRoot();
    for (Path segment : path) {
      current = current.resolve(segment);
      if (Files.isSymbolicLink(current)) {
        throw failure("SYMLINK_FORBIDDEN");
      }
    }
  }

  private static SourceRegistrationRegistryException failure(String code) {
    return new SourceRegistrationRegistryException(code);
  }
}
