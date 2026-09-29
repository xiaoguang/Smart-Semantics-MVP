package org.sourceanalysis.app.capture.localgit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Internal, validated access to one exact local Git commit's immutable tree and blob objects.
 *
 * <p>Opening a session performs all repository, object-source, SHA-1, and commit checks before a
 * caller can enumerate paths or open a blob. It is intentionally not a public analysis API.
 */
public interface FixedGitObjectAccess {

  Session open(Path repository, String exactCommitId) throws IOException;

  interface Session extends AutoCloseable {

    String treeObjectId();

    DirectoryStream<TreeEntry> entries() throws IOException;

    BlobInput openBlob(String objectId) throws IOException;

    @Override
    void close() throws IOException;
  }

  /** One NUL-delimited flattened tree record from the validated commit. */
  record TreeEntry(String mode, String type, String objectId, String path) {

    public TreeEntry {
      if (mode == null || mode.isBlank() || type == null || type.isBlank()) {
        throw new IllegalArgumentException("Git tree entry mode and type are required");
      }
      if (objectId == null || !objectId.matches("[0-9a-f]{40}")) {
        throw new IllegalArgumentException("Git tree entry object ID must be a lowercase SHA-1");
      }
      if (path == null
          || path.isBlank()
          || path.startsWith("/")
          || path.indexOf('\\') >= 0
          || path.indexOf('\u0000') >= 0) {
        throw new IllegalArgumentException("Git tree entry path must be repository relative");
      }
      for (String segment : path.split("/", -1)) {
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
          throw new IllegalArgumentException("Git tree entry path must be canonical");
        }
      }
    }
  }

  /** A known-length blob stream whose close releases its owned constrained Git subprocess. */
  record BlobInput(long sizeBytes, InputStream stream) implements AutoCloseable {

    public BlobInput {
      if (sizeBytes < 0L) {
        throw new IllegalArgumentException("Git blob size cannot be negative");
      }
      Objects.requireNonNull(stream, "Git blob stream");
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }
  }
}
