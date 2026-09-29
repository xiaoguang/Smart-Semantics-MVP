package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.inventory.GitCommitSourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceEntryExclusion;
import org.sourceanalysis.app.analysis.inventory.SourceIssue;
import org.sourceanalysis.app.analysis.inventory.SourceOriginAttributes;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;
import org.sourceanalysis.app.analysis.inventory.SourceVersionCalculator;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.capture.localgit.ConstrainedGitObjectAccess;
import org.sourceanalysis.app.capture.localgit.FixedGitObjectAccess;

/** Reads the tree and blobs of one validated commit, never its working-tree files. */
final class GitSourceOriginReader implements SourceOriginReader {

  private final FixedGitObjectAccess objects;
  private final Path outputRoot;

  GitSourceOriginReader(Path outputRoot, Path trustedGitExecutable) throws IOException {
    this(
        new ConstrainedGitObjectAccess(outputRoot.resolve("git-private"), trustedGitExecutable),
        outputRoot);
  }

  GitSourceOriginReader(FixedGitObjectAccess objects, Path outputRoot) {
    this.objects = Objects.requireNonNull(objects, "fixed Git object access");
    this.outputRoot =
        Objects.requireNonNull(outputRoot, "preparation output root").toAbsolutePath().normalize();
  }

  @Override
  public SourcePreparationResult read(
      SourcePreparationRequest request, SourcePreparationBlobSink sink) {
    State state = new State(request);
    if (request == null
        || sink == null
        || !(request.origin() instanceof GitCommitSourceOrigin origin)
        || request.operation() != SourcePreparationOperation.NEW) {
      state.failRequest();
      return state.result();
    }
    Path sourceRoot = origin.canonicalRoot();
    if (sourceRoot.startsWith(outputRoot) || outputRoot.startsWith(sourceRoot)) {
      state.failRequest();
      return state.result();
    }
    try (FixedGitObjectAccess.Session session = objects.open(sourceRoot, origin.commitId());
        DirectoryStream<FixedGitObjectAccess.TreeEntry> entries = session.entries()) {
      for (FixedGitObjectAccess.TreeEntry treeEntry : entries) {
        if (state.stopped) {
          break;
        }
        inspect(treeEntry, session, sink, state);
      }
    } catch (IOException | java.nio.file.DirectoryIteratorException failure) {
      state.failRoot();
    }
    return state.result();
  }

  @Override
  public SourcePreparationTargetFragment readTargets(
      SourcePreparationRequest request, SourcePreparationBlobSink sink) {
    State state = new State(request);
    if (request == null
        || sink == null
        || !(request.origin() instanceof GitCommitSourceOrigin origin)) {
      state.failRequest();
      return fragment(state.result());
    }
    Path sourceRoot = origin.canonicalRoot();
    if (sourceRoot.startsWith(outputRoot) || outputRoot.startsWith(sourceRoot)) {
      state.failRequest();
      return fragment(state.result());
    }
    try (FixedGitObjectAccess.Session session = objects.open(sourceRoot, origin.commitId());
        DirectoryStream<FixedGitObjectAccess.TreeEntry> entries = session.entries()) {
      for (FixedGitObjectAccess.TreeEntry treeEntry : entries) {
        if (state.stopped) {
          break;
        }
        if (matchesTarget(treeEntry.path(), request.targets())) {
          inspect(treeEntry, session, sink, state);
        }
      }
    } catch (IOException | java.nio.file.DirectoryIteratorException failure) {
      state.failRoot();
    }
    return fragment(state.result());
  }

  private static boolean matchesTarget(String path, List<SourcePreparationTarget> targets) {
    return targets.stream()
        .anyMatch(
            target ->
                path.equals(target.relativePath())
                    || (target.kind() == SourcePreparationTarget.Kind.DIRECTORY
                        && path.startsWith(target.relativePath() + "/")));
  }

  private static SourcePreparationTargetFragment fragment(SourcePreparationResult result) {
    return new SourcePreparationTargetFragment(
        result.inspectionStatus(),
        result.enumerationComplete(),
        result.entries(),
        result.issues(),
        result.unknownSubtrees(),
        result.unmatchedExclusions());
  }

  private void inspect(
      FixedGitObjectAccess.TreeEntry tree,
      FixedGitObjectAccess.Session session,
      SourcePreparationBlobSink sink,
      State state) {
    String path = tree.path();
    SourceOriginAttributes attributes =
        new SourceOriginAttributes(
            SourceOriginAttributes.Kind.GIT_COMMIT,
            tree.mode(),
            tree.type().equals("blob") ? tree.objectId() : null,
            null);
    SourcePreparationTarget exclusion = state.exclusion(path);
    if (exclusion != null) {
      state.matched.add(exclusion);
      state.add(
          entry(
              path,
              kind(tree),
              SourceEntry.Disposition.EXCLUDED_BY_USER,
              attributes,
              null,
              null,
              null,
              List.of(),
              new SourceEntryExclusion(
                  "USER_DECLARED", state.request.operation(), exclusion.relativePath())));
      return;
    }
    if (tree.mode().equals("120000")) {
      String issue =
          state.issue(
              SourceIssue.Code.UNSUPPORTED_ENTRY,
              SourceIssue.Category.UNSUPPORTED,
              SourceIssue.Scope.FILE,
              path,
              SourceIssue.Operation.LIST_DIRECTORY,
              SourceIssue.Resolution.INFORMATIONAL,
              Set.of(),
              "Git symbolic link is listed but not followed");
      state.add(
          entry(
              path,
              SourceEntry.Kind.SYMLINK,
              SourceEntry.Disposition.SKIPPED_SYMLINK,
              attributes,
              null,
              null,
              null,
              List.of(issue),
              null));
      return;
    }
    if (tree.mode().equals("160000")
        || !tree.type().equals("blob")
        || !(tree.mode().equals("100644") || tree.mode().equals("100755"))) {
      String issue =
          state.issue(
              SourceIssue.Code.UNSUPPORTED_ENTRY,
              SourceIssue.Category.UNSUPPORTED,
              SourceIssue.Scope.FILE,
              path,
              SourceIssue.Operation.LIST_DIRECTORY,
              SourceIssue.Resolution.OPEN,
              Set.of(SourceIssue.AllowedAction.EXCLUDE_FILE),
              "Git tree entry is not a supported regular source file");
      state.add(
          entry(
              path,
              kind(tree),
              SourceEntry.Disposition.UNSUPPORTED,
              attributes,
              null,
              null,
              null,
              List.of(issue),
              null));
      return;
    }
    readBlob(tree, attributes, session, sink, state);
  }

  private void readBlob(
      FixedGitObjectAccess.TreeEntry tree,
      SourceOriginAttributes attributes,
      FixedGitObjectAccess.Session session,
      SourcePreparationBlobSink sink,
      State state) {
    String path = tree.path();
    if (state.attempts >= state.request.limits().maxFiles()
        || state.bytesRead > state.request.limits().maxTotalBytes()) {
      state.limit(path, attributes);
      return;
    }
    state.attempts++;
    SourcePreparationBlobWriter writer = null;
    boolean writerCloseAttempted = false;
    boolean accepted = false;
    try (FixedGitObjectAccess.BlobInput blob = session.openBlob(tree.objectId())) {
      long remaining = state.request.limits().maxTotalBytes() - state.bytesRead;
      if (blob.sizeBytes() > remaining) {
        state.limit(path, attributes);
        return;
      }
      try {
        writer = sink.begin(path, blob.sizeBytes());
      } catch (IOException outputFailure) {
        state.failOutput(path, attributes);
        return;
      }
      SourcePreparationBlobWriter currentWriter = writer;
      InputStream counted = new CountingInputStream(blob.stream(), state);
      SourcePreparationByteChecker.CheckedContent content;
      try {
        content =
            SourcePreparationByteChecker.copy(
                counted, remaining, chunk -> writeStaged(currentWriter, chunk));
      } catch (SourcePreparationByteChecker.ResourceLimitExceeded limit) {
        writerCloseAttempted = true;
        if (!closeWriter(writer)) {
          state.failOutput(path, attributes);
          return;
        }
        state.limit(path, attributes);
        return;
      } catch (StagingFailure outputFailure) {
        writerCloseAttempted = true;
        closeWriter(writer);
        state.failOutput(path, attributes);
        return;
      }
      if (content.sizeBytes() != blob.sizeBytes()) {
        writerCloseAttempted = true;
        state.failFile(
            path, attributes, SourceIssue.Code.SOURCE_SIZE_MISMATCH, !closeWriter(writer));
        return;
      }
      ArtifactReference staged;
      try {
        staged = writer.complete();
        if (staged == null || !content.sha256().equals(staged.sha256())) {
          throw new IOException("staged Git bytes differ from source bytes");
        }
      } catch (IOException outputFailure) {
        writerCloseAttempted = true;
        closeWriter(writer);
        state.failOutput(path, attributes);
        return;
      }
      writerCloseAttempted = true;
      if (!closeWriter(writer)) {
        state.failOutput(path, attributes);
        return;
      }
      state.add(
          entry(
              path,
              SourceEntry.Kind.REGULAR_FILE,
              content.utf8Text()
                  ? SourceEntry.Disposition.VERIFIED_TEXT
                  : SourceEntry.Disposition.VERIFIED_MEDIA,
              attributes,
              content,
              staged,
              SourceVersionCalculator.fileId(
                  path, content.sizeBytes(), content.sha256(), attributes),
              List.of(),
              null));
      accepted = true;
    } catch (IOException inputFailure) {
      if (!accepted && !state.recordedPaths.contains(path)) {
        writerCloseAttempted = writer != null;
        state.failFile(
            path,
            attributes,
            SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
            writer != null && !closeWriter(writer));
      }
    } finally {
      if (writer != null && !writerCloseAttempted) {
        try {
          writer.close();
        } catch (IOException outputFailure) {
          if (!state.recordedPaths.contains(path)) {
            state.failOutput(path, attributes);
          }
        }
      }
    }
  }

  private static boolean closeWriter(SourcePreparationBlobWriter writer) {
    try {
      writer.close();
      return true;
    } catch (IOException failure) {
      return false;
    }
  }

  private static void writeStaged(SourcePreparationBlobWriter writer, ByteBuffer bytes)
      throws IOException {
    try {
      writer.write(bytes);
    } catch (IOException failure) {
      throw new StagingFailure(failure);
    }
  }

  private static SourceEntry.Kind kind(FixedGitObjectAccess.TreeEntry entry) {
    if (entry.mode().equals("120000")) {
      return SourceEntry.Kind.SYMLINK;
    }
    if (entry.mode().equals("160000")) {
      return SourceEntry.Kind.SUBMODULE;
    }
    return entry.type().equals("blob") ? SourceEntry.Kind.REGULAR_FILE : SourceEntry.Kind.OTHER;
  }

  private static SourceEntry entry(
      String path,
      SourceEntry.Kind kind,
      SourceEntry.Disposition disposition,
      SourceOriginAttributes attributes,
      SourcePreparationByteChecker.CheckedContent content,
      ArtifactReference blob,
      org.sourceanalysis.app.artifact.ArtifactId fileId,
      List<String> issueIds,
      SourceEntryExclusion exclusion) {
    return new SourceEntry(
        path,
        kind,
        disposition,
        content == null ? null : content.sizeBytes(),
        content == null ? null : content.sha256(),
        blob,
        fileId,
        disposition == SourceEntry.Disposition.VERIFIED_TEXT ? "UTF-8" : null,
        attributes,
        null,
        issueIds,
        null,
        exclusion);
  }

  private static final class CountingInputStream extends java.io.FilterInputStream {
    private final State state;

    private CountingInputStream(InputStream source, State state) {
      super(source);
      this.state = state;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      int read = in.read(bytes, offset, length);
      if (read > 0) {
        state.bytesRead += read;
      }
      return read;
    }

    @Override
    public int read() throws IOException {
      int value = in.read();
      if (value >= 0) {
        state.bytesRead++;
      }
      return value;
    }
  }

  private static final class StagingFailure extends IOException {
    private StagingFailure(IOException cause) {
      super(cause);
    }
  }

  private static final class State {
    private final SourcePreparationRequest request;
    private final List<SourceEntry> entries = new ArrayList<>();
    private final List<SourceIssue> issues = new ArrayList<>();
    private final Set<String> recordedPaths = new HashSet<>();
    private final Set<SourcePreparationTarget> matched = new HashSet<>();
    private boolean stopped;
    private boolean enumerationComplete = true;
    private SourcePreparationResult.InspectionStatus inspection =
        SourcePreparationResult.InspectionStatus.COMPLETED;
    private int attempts;
    private long bytesRead;

    private State(SourcePreparationRequest request) {
      this.request = request;
    }

    private void add(SourceEntry source) {
      if (!recordedPaths.add(source.relativePath())) {
        throw new IllegalStateException("duplicate Git source path");
      }
      entries.add(source);
    }

    private SourcePreparationTarget exclusion(String path) {
      for (SourcePreparationTarget target : request.effectiveExclusions()) {
        if (path.equals(target.relativePath())
            || (target.kind() == SourcePreparationTarget.Kind.DIRECTORY
                && path.startsWith(target.relativePath() + "/"))) {
          return target;
        }
      }
      return null;
    }

    private String issue(
        SourceIssue.Code code,
        SourceIssue.Category category,
        SourceIssue.Scope scope,
        String path,
        SourceIssue.Operation operation,
        SourceIssue.Resolution resolution,
        Set<SourceIssue.AllowedAction> actions,
        String message) {
      String identity = code + "|" + category + "|" + scope + "|" + path + "|" + operation;
      String id = "source-issue:" + hash(identity);
      issues.add(
          new SourceIssue(
              id,
              code,
              category,
              scope,
              path,
              operation,
              message,
              null,
              null,
              resolution,
              actions,
              null));
      return id;
    }

    private void failRequest() {
      if (request != null) {
        issue(
            SourceIssue.Code.SOURCE_PATH_INVALID,
            SourceIssue.Category.REQUEST,
            SourceIssue.Scope.REQUEST,
            null,
            SourceIssue.Operation.VALIDATE_REQUEST,
            SourceIssue.Resolution.OPEN,
            Set.of(SourceIssue.AllowedAction.FIX_CONFIGURATION),
            "Git source preparation request or output root is invalid");
      }
      enumerationComplete = false;
      stopped = true;
    }

    private void failRoot() {
      issue(
          SourceIssue.Code.CAPTURE_IDENTITY_INVALID,
          SourceIssue.Category.ACCESS,
          SourceIssue.Scope.ROOT,
          null,
          SourceIssue.Operation.VERIFY_IDENTITY,
          SourceIssue.Resolution.OPEN,
          Set.of(SourceIssue.AllowedAction.NEW_PREPARATION),
          "fixed Git commit or object source cannot be verified");
      enumerationComplete = false;
      inspection = SourcePreparationResult.InspectionStatus.ABORTED;
      stopped = true;
    }

    private void failFile(
        String path,
        SourceOriginAttributes attributes,
        SourceIssue.Code code,
        boolean outputCloseFailed) {
      List<String> issueIds = new ArrayList<>();
      issueIds.add(
          issue(
              code,
              code == SourceIssue.Code.SOURCE_SIZE_MISMATCH
                  ? SourceIssue.Category.SOURCE_CHANGE
                  : SourceIssue.Category.ACCESS,
              SourceIssue.Scope.FILE,
              path,
              SourceIssue.Operation.READ_INPUT,
              SourceIssue.Resolution.OPEN,
              Set.of(
                  SourceIssue.AllowedAction.REFRESH_FILE, SourceIssue.AllowedAction.EXCLUDE_FILE),
              "Git blob cannot be read or verified"));
      if (outputCloseFailed) {
        issueIds.add(outputFailureIssue());
        abortForOutputFailure();
      }
      add(
          entry(
              path,
              SourceEntry.Kind.REGULAR_FILE,
              SourceEntry.Disposition.UNAVAILABLE,
              attributes,
              null,
              null,
              null,
              issueIds,
              null));
    }

    private void limit(String path, SourceOriginAttributes attributes) {
      String id =
          issue(
              SourceIssue.Code.VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED,
              SourceIssue.Category.RESOURCE_LIMIT,
              SourceIssue.Scope.FILE,
              path,
              SourceIssue.Operation.READ_INPUT,
              SourceIssue.Resolution.OPEN,
              Set.of(SourceIssue.AllowedAction.NEW_PREPARATION),
              "Git source resource limit left this file unread");
      add(
          entry(
              path,
              SourceEntry.Kind.REGULAR_FILE,
              SourceEntry.Disposition.UNCHECKED,
              attributes,
              null,
              null,
              null,
              List.of(id),
              null));
      inspection = SourcePreparationResult.InspectionStatus.ABORTED;
      enumerationComplete = false;
      stopped = true;
    }

    private void failOutput(String path, SourceOriginAttributes attributes) {
      String id = outputFailureIssue();
      add(
          entry(
              path,
              SourceEntry.Kind.REGULAR_FILE,
              SourceEntry.Disposition.UNCHECKED,
              attributes,
              null,
              null,
              null,
              List.of(id),
              null));
      abortForOutputFailure();
    }

    private String outputFailureIssue() {
      return issue(
          SourceIssue.Code.SOURCE_OUTPUT_FAILED,
          SourceIssue.Category.OUTPUT,
          SourceIssue.Scope.OUTPUT,
          null,
          SourceIssue.Operation.SAVE_OUTPUT,
          SourceIssue.Resolution.OPEN,
          Set.of(SourceIssue.AllowedAction.FIX_OUTPUT),
          "Git source bytes cannot be saved in private output");
    }

    private void abortForOutputFailure() {
      inspection = SourcePreparationResult.InspectionStatus.ABORTED;
      enumerationComplete = false;
      stopped = true;
    }

    private SourcePreparationResult result() {
      if (request == null) {
        return new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.ABORTED,
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of());
      }
      List<SourcePreparationTarget> unmatched =
          request.effectiveExclusions().stream()
              .filter(target -> !matched.contains(target))
              .toList();
      entries.sort(Comparator.comparing(SourceEntry::relativePath));
      return new SourcePreparationResult(
          inspection, enumerationComplete, entries, issues, List.of(), unmatched);
    }

    private static String hash(String value) {
      try {
        byte[] bytes =
            MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(bytes);
      } catch (NoSuchAlgorithmException impossible) {
        throw new IllegalStateException("SHA-256 unavailable", impossible);
      }
    }
  }
}
