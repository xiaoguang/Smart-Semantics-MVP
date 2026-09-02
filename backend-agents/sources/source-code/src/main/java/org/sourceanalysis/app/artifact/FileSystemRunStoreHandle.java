package org.sourceanalysis.app.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/** Package-private filesystem root ownership retained behind the opaque public handle. */
final class FileSystemRunStoreHandle implements RunStoreHandle {

  private final Path root;
  private boolean closed;

  private FileSystemRunStoreHandle(Path root) {
    this.root = root;
  }

  static RunStoreHandle openEmptyTemporaryDirectory(Path emptyTemporaryDirectory) {
    Objects.requireNonNull(emptyTemporaryDirectory, "emptyTemporaryDirectory");
    try {
      if (Files.isSymbolicLink(emptyTemporaryDirectory)
          || !Files.isDirectory(emptyTemporaryDirectory, LinkOption.NOFOLLOW_LINKS)
          || !Files.exists(emptyTemporaryDirectory, LinkOption.NOFOLLOW_LINKS)
          || containsAnyEntry(emptyTemporaryDirectory)) {
        throw new IllegalArgumentException(
            "test store root must be an existing empty non-symlink directory");
      }
      return new FileSystemRunStoreHandle(emptyTemporaryDirectory);
    } catch (IOException failure) {
      throw new IllegalArgumentException("test store root cannot be inspected", failure);
    }
  }

  private static boolean containsAnyEntry(Path root) throws IOException {
    try (Stream<Path> entries = Files.list(root)) {
      return entries.findAny().isPresent();
    }
  }

  Path rootForStore() {
    if (closed) {
      throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID");
    }
    return root;
  }

  @Override
  public void close() {
    closed = true;
  }
}
