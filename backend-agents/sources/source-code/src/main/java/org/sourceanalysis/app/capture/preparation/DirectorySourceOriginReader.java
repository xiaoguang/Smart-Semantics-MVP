package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceEntryExclusion;
import org.sourceanalysis.app.analysis.inventory.SourceEntryObservations;
import org.sourceanalysis.app.analysis.inventory.SourceIssue;
import org.sourceanalysis.app.analysis.inventory.SourceObservation;
import org.sourceanalysis.app.analysis.inventory.SourceOriginAttributes;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;
import org.sourceanalysis.app.analysis.inventory.SourceVersionCalculator;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Reads a real directory without following links and stages accepted bytes exactly once. */
final class DirectorySourceOriginReader implements SourceOriginReader {

  private static final Comparator<Path> PATH_ORDER =
      Comparator.comparing(
          DirectorySourceOriginReader::fileName, DirectorySourceOriginReader::compareUtf8);
  private static final SourceOriginAttributes DIRECTORY_ATTRIBUTES =
      new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null);

  private final DirectorySourceAccess access;
  private final Path outputRoot;

  /** Production constructor using the JDK no-follow filesystem boundary. */
  DirectorySourceOriginReader(Path outputRoot) {
    this(new JdkDirectorySourceAccess(), outputRoot);
  }

  /** Package-private constructor for narrowly injected directory/list/input failures in tests. */
  DirectorySourceOriginReader(DirectorySourceAccess access, Path outputRoot) {
    this.access = Objects.requireNonNull(access, "directory source access");
    this.outputRoot =
        Objects.requireNonNull(outputRoot, "source preparation output root")
            .toAbsolutePath()
            .normalize();
  }

  @Override
  public SourcePreparationResult read(
      SourcePreparationRequest request, SourcePreparationBlobSink blobSink) {
    ResultState state = new ResultState(request);
    if (request == null || blobSink == null) {
      state.requestFailure("directory preparation request and blob sink are required");
      return state.result();
    }
    if (!(request.origin() instanceof DirectorySourceOrigin directoryOrigin)) {
      state.requestFailure("directory reader requires a directory source origin");
      return state.result();
    }
    Path sourceRoot = directoryOrigin.canonicalRoot();
    if (overlaps(sourceRoot, outputRoot)) {
      state.requestFailure("source root and preparation output root must be independent");
      return state.result();
    }
    if (!validateOutputRoot(state)) {
      return state.result();
    }
    BasicFileAttributes rootAttributes;
    try {
      rootAttributes = access.readAttributes(sourceRoot);
    } catch (NoSuchFileException missing) {
      state.rootFailure(
          SourceIssue.Code.SOURCE_ROOT_NOT_FOUND, "source directory root does not exist");
      return state.result();
    } catch (IOException failure) {
      state.rootFailure(
          SourceIssue.Code.SOURCE_ROOT_LIST_FAILED,
          "source directory root attributes could not be read");
      return state.result();
    }
    if (rootAttributes.isSymbolicLink()
        || !rootAttributes.isDirectory()
        || !safeAncestors(sourceRoot)) {
      state.rootFailure(
          SourceIssue.Code.SOURCE_ROOT_LIST_FAILED,
          "source root must be a real directory without symbolic-link ancestors");
      return state.result();
    }

    walkDirectory(sourceRoot, sourceRoot, null, state, blobSink);
    return state.result();
  }

  @Override
  public SourcePreparationTargetFragment readTargets(
      SourcePreparationRequest request, SourcePreparationBlobSink blobSink) {
    ResultState state = new ResultState(request);
    if (request == null || blobSink == null) {
      state.requestFailure("directory target request and blob sink are required");
      return fragment(state.result());
    }
    if (!(request.origin() instanceof DirectorySourceOrigin directoryOrigin)) {
      state.requestFailure("directory target reader requires a directory source origin");
      return fragment(state.result());
    }
    Path sourceRoot = directoryOrigin.canonicalRoot();
    if (overlaps(sourceRoot, outputRoot) || !validateOutputRoot(state)) {
      if (overlaps(sourceRoot, outputRoot)) {
        state.requestFailure("source root and preparation output root must be independent");
      }
      return fragment(state.result());
    }
    BasicFileAttributes rootAttributes;
    try {
      rootAttributes = access.readAttributes(sourceRoot);
    } catch (NoSuchFileException missing) {
      state.rootFailure(
          SourceIssue.Code.SOURCE_ROOT_NOT_FOUND, "source directory root does not exist");
      return fragment(state.result());
    } catch (IOException failure) {
      state.rootFailure(
          SourceIssue.Code.SOURCE_ROOT_LIST_FAILED,
          "source directory root attributes could not be read");
      return fragment(state.result());
    }
    if (rootAttributes.isSymbolicLink()
        || !rootAttributes.isDirectory()
        || !safeAncestors(sourceRoot)) {
      state.rootFailure(
          SourceIssue.Code.SOURCE_ROOT_LIST_FAILED,
          "source root must be a real directory without symbolic-link ancestors");
      return fragment(state.result());
    }

    for (SourcePreparationTarget target : request.targets()) {
      if (state.stopped()) {
        break;
      }
      Path targetPath = sourceRoot.resolve(target.relativePath()).normalize();
      if (!targetPath.startsWith(sourceRoot) || !safeAncestors(targetPath.getParent())) {
        state.targetFailure(
            target, SourceIssue.Code.SOURCE_PATH_INVALID, "refresh target is unsafe");
        continue;
      }
      BasicFileAttributes attributes;
      try {
        attributes = access.readAttributes(targetPath);
      } catch (NoSuchFileException missing) {
        state.targetFailure(
            target, SourceIssue.Code.SOURCE_ENTRY_MISSING, "refresh target is not present");
        continue;
      } catch (IOException failure) {
        state.targetFailure(
            target,
            SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
            "refresh target cannot be inspected");
        continue;
      }
      if ((target.kind() == SourcePreparationTarget.Kind.FILE && !attributes.isRegularFile())
          || (target.kind() == SourcePreparationTarget.Kind.DIRECTORY
              && !attributes.isDirectory())) {
        state.targetFailure(
            target,
            SourceIssue.Code.SOURCE_ENTRY_TYPE_CHANGED,
            "refresh target type no longer matches the saved target");
        continue;
      }
      if (attributes.isDirectory()) {
        walkDirectory(sourceRoot, targetPath, target.relativePath(), state, blobSink);
      } else {
        inspectChild(sourceRoot, targetPath, target.relativePath(), state, blobSink);
      }
    }
    return fragment(state.result());
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

  private boolean validateOutputRoot(ResultState state) {
    try {
      BasicFileAttributes attributes =
          Files.readAttributes(outputRoot, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!attributes.isDirectory() || attributes.isSymbolicLink() || !safeAncestors(outputRoot)) {
        state.outputFailure("source preparation output root must be a real private directory");
        return false;
      }
      return true;
    } catch (IOException failure) {
      state.outputFailure("source preparation output root cannot be inspected");
      return false;
    }
  }

  private void walkDirectory(
      Path sourceRoot,
      Path directory,
      String relativePath,
      ResultState state,
      SourcePreparationBlobSink blobSink) {
    if (state.stopped()) {
      return;
    }
    if (relativePath != null && !safeAncestors(directory)) {
      state.directoryListingFailure(relativePath);
      return;
    }
    List<Path> children;
    try {
      children = listChildren(directory);
    } catch (IOException failure) {
      if (relativePath == null) {
        state.rootFailure(
            SourceIssue.Code.SOURCE_ROOT_LIST_FAILED, "source directory root cannot be listed");
      } else {
        state.directoryListingFailure(relativePath);
      }
      return;
    }
    if (relativePath != null) {
      state.addEntry(
          entry(
              relativePath,
              SourceEntry.Kind.DIRECTORY,
              SourceEntry.Disposition.ENUMERATED_DIRECTORY,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(),
              null));
    }
    for (Path child : children) {
      if (state.stopped()) {
        return;
      }
      String childPath = relativePath(sourceRoot, child);
      if (childPath == null) {
        state.pathFailure("directory listing returned a path outside the source root");
        return;
      }
      inspectChild(sourceRoot, child, childPath, state, blobSink);
    }
  }

  private void inspectChild(
      Path sourceRoot,
      Path child,
      String relativePath,
      ResultState state,
      SourcePreparationBlobSink blobSink) {
    BasicFileAttributes attributes;
    try {
      attributes = access.readAttributes(child);
    } catch (IOException failure) {
      state.attributeFailure(relativePath);
      return;
    }

    SourcePreparationTarget exclusion =
        matchingExclusion(state.request(), relativePath, attributes);
    if (exclusion != null) {
      state.excluded(relativePath, attributes, exclusion);
      return;
    }
    if (isGitMetadata(relativePath)) {
      state.gitMetadata(relativePath, attributes);
      return;
    }
    if (attributes.isSymbolicLink()) {
      state.symlink(relativePath);
      return;
    }
    if (attributes.isDirectory()) {
      walkDirectory(sourceRoot, child, relativePath, state, blobSink);
      return;
    }
    if (!attributes.isRegularFile()) {
      state.unsupported(relativePath, kind(attributes));
      return;
    }
    readRegularFile(child, relativePath, attributes, state, blobSink);
  }

  private void readRegularFile(
      Path path,
      String relativePath,
      BasicFileAttributes beforeAttributes,
      ResultState state,
      SourcePreparationBlobSink blobSink) {
    if (state.attemptedFiles() >= state.request().limits().maxFiles()) {
      state.resourceLimit(relativePath, SourceEntry.Kind.REGULAR_FILE);
      return;
    }
    long remainingBytes = state.request().limits().maxTotalBytes() - state.bytesRead();
    if (beforeAttributes.size() > remainingBytes) {
      state.resourceLimit(relativePath, SourceEntry.Kind.REGULAR_FILE);
      return;
    }

    SourcePreparationBlobWriter writer;
    try {
      writer = blobSink.begin(relativePath, beforeAttributes.size());
    } catch (IOException failure) {
      state.outputFailureForEntry(
          relativePath, SourceEntry.Kind.REGULAR_FILE, "staged output cannot begin");
      return;
    }

    SourcePreparationByteChecker.CheckedContent checked;
    state.startInputAttempt();
    try (InputStream input = new CountingInputStream(access.openInput(path), state)) {
      checked =
          SourcePreparationByteChecker.copy(
              input, remainingBytes, chunk -> writeChunk(writer, chunk));
    } catch (SourcePreparationByteChecker.ResourceLimitExceeded limited) {
      if (!closeAfterRejectedSource(writer)) {
        state.outputFailureForEntry(
            relativePath, SourceEntry.Kind.REGULAR_FILE, "staged output close failed");
        return;
      }
      state.resourceLimit(relativePath, SourceEntry.Kind.REGULAR_FILE);
      return;
    } catch (OutputWriteFailure failure) {
      if (!closeAfterRejectedSource(writer)) {
        state.outputFailureForEntry(
            relativePath, SourceEntry.Kind.REGULAR_FILE, "staged output close failed");
        return;
      }
      state.outputFailureForEntry(
          relativePath, SourceEntry.Kind.REGULAR_FILE, "staged output write failed");
      return;
    } catch (IOException failure) {
      List<String> issueIds = new ArrayList<>();
      issueIds.add(state.sourceReadFailure(relativePath));
      if (!closeWriter(writer)) {
        issueIds.add(state.outputFailureIssue("staged output close failed"));
        state.stopForOutputFailure();
      }
      state.addEntry(
          entry(
              relativePath,
              SourceEntry.Kind.REGULAR_FILE,
              SourceEntry.Disposition.UNAVAILABLE,
              null,
              null,
              null,
              null,
              null,
              null,
              issueIds,
              null));
      return;
    }

    BasicFileAttributes afterAttributes;
    try {
      afterAttributes = access.readAttributes(path);
    } catch (NoSuchFileException missing) {
      rejectChangedFile(
          writer,
          relativePath,
          beforeAttributes,
          checked,
          SourceIssue.Code.SOURCE_ENTRY_MISSING,
          "source file disappeared after reading",
          state);
      return;
    } catch (IOException failure) {
      rejectChangedFile(
          writer,
          relativePath,
          beforeAttributes,
          checked,
          SourceIssue.Code.SOURCE_CHANGED_DURING_READ,
          "source file attributes could not be reread after streaming",
          state);
      return;
    }
    if (!afterAttributes.isRegularFile() || afterAttributes.isSymbolicLink()) {
      rejectChangedFile(
          writer,
          relativePath,
          beforeAttributes,
          checked,
          SourceIssue.Code.SOURCE_ENTRY_TYPE_CHANGED,
          "source file changed type during reading",
          state);
      return;
    }
    if (checked.sizeBytes() != beforeAttributes.size()
        || checked.sizeBytes() != afterAttributes.size()) {
      rejectChangedFile(
          writer,
          relativePath,
          beforeAttributes,
          checked,
          SourceIssue.Code.SOURCE_SIZE_MISMATCH,
          "source file size changed while reading",
          state);
      return;
    }
    if (changedIdentity(beforeAttributes, afterAttributes)) {
      rejectChangedFile(
          writer,
          relativePath,
          beforeAttributes,
          checked,
          SourceIssue.Code.SOURCE_CHANGED_DURING_READ,
          "source file identity changed while reading",
          state);
      return;
    }

    ArtifactReference blobRef;
    try {
      blobRef = writer.complete();
      if (blobRef == null || !checked.sha256().equals(blobRef.sha256())) {
        throw new IOException("staged output does not identify streamed bytes");
      }
    } catch (IOException failure) {
      if (!closeAfterRejectedSource(writer)) {
        state.outputFailureForEntry(
            relativePath, SourceEntry.Kind.REGULAR_FILE, "staged output close failed");
        return;
      }
      state.outputFailureForEntry(
          relativePath, SourceEntry.Kind.REGULAR_FILE, "staged output cannot complete");
      return;
    }
    if (!closeWriter(writer)) {
      state.outputFailureForEntry(
          relativePath, SourceEntry.Kind.REGULAR_FILE, "staged output close failed");
      return;
    }

    SourceOriginAttributes originAttributes = DIRECTORY_ATTRIBUTES;
    state.addEntry(
        entry(
            relativePath,
            SourceEntry.Kind.REGULAR_FILE,
            checked.utf8Text()
                ? SourceEntry.Disposition.VERIFIED_TEXT
                : SourceEntry.Disposition.VERIFIED_MEDIA,
            checked.sizeBytes(),
            checked.sha256(),
            blobRef,
            SourceVersionCalculator.fileId(
                relativePath, checked.sizeBytes(), checked.sha256(), originAttributes),
            checked.utf8Text() ? "UTF-8" : null,
            new SourceEntryObservations(
                observation(beforeAttributes), observation(afterAttributes)),
            List.of(),
            null));
  }

  /** Counts actual input bytes even when a later read or source identity check fails. */
  private static final class CountingInputStream extends java.io.FilterInputStream {
    private final ResultState state;

    private CountingInputStream(InputStream input, ResultState state) {
      super(input);
      this.state = state;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      int count = in.read(bytes, offset, length);
      if (count > 0) {
        state.recordBytesRead(count);
      }
      return count;
    }

    @Override
    public int read() throws IOException {
      int value = in.read();
      if (value >= 0) {
        state.recordBytesRead(1);
      }
      return value;
    }
  }

  private void rejectChangedFile(
      SourcePreparationBlobWriter writer,
      String relativePath,
      BasicFileAttributes beforeAttributes,
      SourcePreparationByteChecker.CheckedContent checked,
      SourceIssue.Code code,
      String message,
      ResultState state) {
    List<String> issueIds = new ArrayList<>();
    issueIds.add(
        state.sourceChangeFailure(
            relativePath,
            code,
            message,
            observation(beforeAttributes),
            new SourceObservation(checked.sizeBytes(), checked.sha256(), null, null, null)));
    if (!closeWriter(writer)) {
      issueIds.add(state.outputFailureIssue("staged output close failed"));
      state.stopForOutputFailure();
    }
    state.addEntry(
        entry(
            relativePath,
            SourceEntry.Kind.REGULAR_FILE,
            SourceEntry.Disposition.UNAVAILABLE,
            null,
            null,
            null,
            null,
            null,
            new SourceEntryObservations(
                observation(beforeAttributes),
                new SourceObservation(checked.sizeBytes(), checked.sha256(), null, null, null)),
            issueIds,
            null));
  }

  private static void writeChunk(SourcePreparationBlobWriter writer, java.nio.ByteBuffer chunk)
      throws IOException {
    try {
      writer.write(chunk);
    } catch (IOException failure) {
      throw new OutputWriteFailure(failure);
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

  private static boolean closeAfterRejectedSource(SourcePreparationBlobWriter writer) {
    return closeWriter(writer);
  }

  private List<Path> listChildren(Path directory) throws IOException {
    List<Path> children = new ArrayList<>();
    try (DirectoryStream<Path> stream = access.openDirectory(directory)) {
      try {
        for (Path child : stream) {
          children.add(child);
        }
      } catch (DirectoryIteratorException failure) {
        throw failure.getCause();
      }
    }
    children.sort(PATH_ORDER);
    return children;
  }

  private static String relativePath(Path sourceRoot, Path child) {
    Path normalized = child.toAbsolutePath().normalize();
    if (!normalized.startsWith(sourceRoot)) {
      return null;
    }
    Path relative = sourceRoot.relativize(normalized);
    String value = relative.toString().replace(child.getFileSystem().getSeparator(), "/");
    return isCanonicalRelativePath(value) ? value : null;
  }

  private static boolean overlaps(Path first, Path second) {
    return first.startsWith(second) || second.startsWith(first);
  }

  private static boolean safeAncestors(Path path) {
    if (path == null) {
      return false;
    }
    Path current = path.getRoot();
    if (current == null) {
      return false;
    }
    for (Path segment : current.relativize(path.toAbsolutePath().normalize())) {
      current = current.resolve(segment);
      try {
        if (Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
            .isSymbolicLink()) {
          return false;
        }
      } catch (IOException failure) {
        return false;
      }
    }
    return true;
  }

  private static boolean isGitMetadata(String relativePath) {
    for (String segment : relativePath.split("/")) {
      if (segment.equals(".git")) {
        return true;
      }
    }
    return false;
  }

  private static SourcePreparationTarget matchingExclusion(
      SourcePreparationRequest request, String relativePath, BasicFileAttributes attributes) {
    SourcePreparationTarget.Kind actualKind =
        attributes.isDirectory()
            ? SourcePreparationTarget.Kind.DIRECTORY
            : SourcePreparationTarget.Kind.FILE;
    return request.effectiveExclusions().stream()
        .filter(target -> target.relativePath().equals(relativePath) && target.kind() == actualKind)
        .findFirst()
        .orElse(null);
  }

  private static SourceEntry.Kind kind(BasicFileAttributes attributes) {
    if (attributes.isDirectory()) {
      return SourceEntry.Kind.DIRECTORY;
    }
    if (attributes.isSymbolicLink()) {
      return SourceEntry.Kind.SYMLINK;
    }
    if (attributes.isRegularFile()) {
      return SourceEntry.Kind.REGULAR_FILE;
    }
    return SourceEntry.Kind.OTHER;
  }

  private static SourceEntry entry(
      String relativePath,
      SourceEntry.Kind kind,
      SourceEntry.Disposition disposition,
      Long sizeBytes,
      org.sourceanalysis.app.artifact.Sha256Digest sha256,
      ArtifactReference blobRef,
      org.sourceanalysis.app.artifact.ArtifactId fileId,
      String textEncoding,
      SourceEntryObservations observations,
      List<String> issueIds,
      SourceEntryExclusion exclusion) {
    return new SourceEntry(
        relativePath,
        kind,
        disposition,
        sizeBytes,
        sha256,
        blobRef,
        fileId,
        textEncoding,
        DIRECTORY_ATTRIBUTES,
        observations,
        issueIds,
        null,
        exclusion);
  }

  private static SourceObservation observation(BasicFileAttributes attributes) {
    Object key = attributes.fileKey();
    Instant modifiedAt = attributes.lastModifiedTime().toInstant();
    return new SourceObservation(
        attributes.size(), null, null, modifiedAt, key == null ? null : key.toString());
  }

  private static boolean changedIdentity(BasicFileAttributes before, BasicFileAttributes after) {
    if (!before.lastModifiedTime().equals(after.lastModifiedTime())) {
      return true;
    }
    Object beforeKey = before.fileKey();
    Object afterKey = after.fileKey();
    return beforeKey != null && afterKey != null && !beforeKey.equals(afterKey);
  }

  private static String fileName(Path value) {
    Path name = value.getFileName();
    return name == null ? "" : name.toString();
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] right = second.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private static boolean isCanonicalRelativePath(String value) {
    if (value == null || value.isBlank() || value.startsWith("/") || value.indexOf('\\') >= 0) {
      return false;
    }
    for (String segment : value.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        return false;
      }
    }
    return true;
  }

  private static final class OutputWriteFailure extends IOException {
    private OutputWriteFailure(IOException cause) {
      super(cause.getMessage(), cause);
    }
  }

  private static final class ResultState {

    private final SourcePreparationRequest request;
    private final List<SourceEntry> entries = new ArrayList<>();
    private final List<SourceIssue> issues = new ArrayList<>();
    private final Set<String> entryPaths = new HashSet<>();
    private final Set<String> unknownSubtrees = new HashSet<>();
    private final Set<SourcePreparationTarget> matchedExclusions = new HashSet<>();
    private boolean enumerationComplete = true;
    private SourcePreparationResult.InspectionStatus inspectionStatus =
        SourcePreparationResult.InspectionStatus.COMPLETED;
    private boolean stopped;
    private int attemptedFiles;
    private long bytesRead;

    private ResultState(SourcePreparationRequest request) {
      this.request = request;
    }

    private SourcePreparationRequest request() {
      return request;
    }

    private boolean stopped() {
      return stopped;
    }

    private int attemptedFiles() {
      return attemptedFiles;
    }

    private long bytesRead() {
      return bytesRead;
    }

    private void startInputAttempt() {
      attemptedFiles++;
    }

    private void recordBytesRead(int count) {
      bytesRead += count;
    }

    private void addEntry(SourceEntry entry) {
      if (!entryPaths.add(entry.relativePath())) {
        throw new IllegalStateException("directory reader recorded a duplicate source path");
      }
      entries.add(entry);
    }

    private void requestFailure(String message) {
      if (request != null) {
        issue(
            SourceIssue.Code.SOURCE_PATH_INVALID,
            SourceIssue.Category.REQUEST,
            SourceIssue.Scope.REQUEST,
            null,
            SourceIssue.Operation.VALIDATE_REQUEST,
            message,
            null,
            null,
            SourceIssue.Resolution.OPEN,
            Set.of(SourceIssue.AllowedAction.FIX_CONFIGURATION));
      }
      enumerationComplete = false;
    }

    private void pathFailure(String message) {
      issue(
          SourceIssue.Code.SOURCE_PATH_INVALID,
          SourceIssue.Category.REQUEST,
          SourceIssue.Scope.REQUEST,
          null,
          SourceIssue.Operation.LIST_DIRECTORY,
          message,
          null,
          null,
          SourceIssue.Resolution.OPEN,
          Set.of(SourceIssue.AllowedAction.FIX_CONFIGURATION));
      enumerationComplete = false;
      stopped = true;
    }

    private void rootFailure(SourceIssue.Code code, String message) {
      issue(
          code,
          SourceIssue.Category.ACCESS,
          SourceIssue.Scope.ROOT,
          null,
          SourceIssue.Operation.LIST_DIRECTORY,
          message,
          null,
          null,
          SourceIssue.Resolution.OPEN,
          Set.of(SourceIssue.AllowedAction.NEW_PREPARATION));
      enumerationComplete = false;
      stopped = true;
    }

    private void outputFailure(String message) {
      issue(
          SourceIssue.Code.SOURCE_OUTPUT_FAILED,
          SourceIssue.Category.OUTPUT,
          SourceIssue.Scope.OUTPUT,
          null,
          SourceIssue.Operation.SAVE_OUTPUT,
          message,
          null,
          null,
          SourceIssue.Resolution.OPEN,
          Set.of(SourceIssue.AllowedAction.FIX_OUTPUT));
      stopForOutputFailure();
    }

    private void stopForOutputFailure() {
      inspectionStatus = SourcePreparationResult.InspectionStatus.ABORTED;
      enumerationComplete = false;
      stopped = true;
    }

    private void directoryListingFailure(String relativePath) {
      String issue =
          issue(
              SourceIssue.Code.SOURCE_DIRECTORY_LIST_FAILED,
              SourceIssue.Category.ACCESS,
              SourceIssue.Scope.DIRECTORY,
              relativePath,
              SourceIssue.Operation.LIST_DIRECTORY,
              "source directory cannot be listed",
              null,
              null,
              SourceIssue.Resolution.OPEN,
              Set.of(
                  SourceIssue.AllowedAction.REFRESH_DIRECTORY,
                  SourceIssue.AllowedAction.EXCLUDE_DIRECTORY));
      addEntry(
          entry(
              relativePath,
              SourceEntry.Kind.DIRECTORY,
              SourceEntry.Disposition.UNAVAILABLE,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(issue),
              null));
      unknownSubtrees.add(relativePath);
      enumerationComplete = false;
    }

    private void attributeFailure(String relativePath) {
      String issue =
          issue(
              SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
              SourceIssue.Category.ACCESS,
              SourceIssue.Scope.DIRECTORY,
              relativePath,
              SourceIssue.Operation.LIST_DIRECTORY,
              "source entry kind cannot be determined while enumerating its parent directory",
              null,
              null,
              SourceIssue.Resolution.OPEN,
              Set.of(
                  SourceIssue.AllowedAction.REFRESH_FILE,
                  SourceIssue.AllowedAction.REFRESH_DIRECTORY,
                  SourceIssue.AllowedAction.EXCLUDE_FILE,
                  SourceIssue.AllowedAction.EXCLUDE_DIRECTORY));
      addEntry(
          entry(
              relativePath,
              SourceEntry.Kind.UNKNOWN,
              SourceEntry.Disposition.UNAVAILABLE,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(issue),
              null));
      unknownSubtrees.add(relativePath);
      enumerationComplete = false;
    }

    private void targetFailure(
        SourcePreparationTarget target, SourceIssue.Code code, String message) {
      SourceEntry.Kind kind =
          target.kind() == SourcePreparationTarget.Kind.DIRECTORY
              ? SourceEntry.Kind.DIRECTORY
              : SourceEntry.Kind.REGULAR_FILE;
      SourceIssue.Scope scope =
          target.kind() == SourcePreparationTarget.Kind.DIRECTORY
              ? SourceIssue.Scope.DIRECTORY
              : SourceIssue.Scope.FILE;
      Set<SourceIssue.AllowedAction> actions =
          target.kind() == SourcePreparationTarget.Kind.DIRECTORY
              ? Set.of(
                  SourceIssue.AllowedAction.REFRESH_DIRECTORY,
                  SourceIssue.AllowedAction.EXCLUDE_DIRECTORY)
              : Set.of(
                  SourceIssue.AllowedAction.REFRESH_FILE, SourceIssue.AllowedAction.EXCLUDE_FILE);
      String issue =
          issue(
              code,
              code == SourceIssue.Code.SOURCE_PATH_INVALID
                  ? SourceIssue.Category.REQUEST
                  : SourceIssue.Category.ACCESS,
              scope,
              target.relativePath(),
              SourceIssue.Operation.LIST_DIRECTORY,
              message,
              null,
              null,
              SourceIssue.Resolution.OPEN,
              actions);
      addEntry(
          entry(
              target.relativePath(),
              kind,
              SourceEntry.Disposition.UNAVAILABLE,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(issue),
              null));
      if (scope == SourceIssue.Scope.DIRECTORY) {
        unknownSubtrees.add(target.relativePath());
        enumerationComplete = false;
      }
    }

    private String sourceReadFailure(String relativePath) {
      return issue(
          SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
          SourceIssue.Category.ACCESS,
          SourceIssue.Scope.FILE,
          relativePath,
          SourceIssue.Operation.READ_INPUT,
          "source file cannot be read",
          null,
          null,
          SourceIssue.Resolution.OPEN,
          Set.of(SourceIssue.AllowedAction.REFRESH_FILE, SourceIssue.AllowedAction.EXCLUDE_FILE));
    }

    private String sourceChangeFailure(
        String relativePath,
        SourceIssue.Code code,
        String message,
        SourceObservation expected,
        SourceObservation observed) {
      return issue(
          code,
          SourceIssue.Category.SOURCE_CHANGE,
          SourceIssue.Scope.FILE,
          relativePath,
          SourceIssue.Operation.VERIFY_IDENTITY,
          message,
          expected,
          observed,
          SourceIssue.Resolution.OPEN,
          Set.of(SourceIssue.AllowedAction.REFRESH_FILE, SourceIssue.AllowedAction.EXCLUDE_FILE));
    }

    private void resourceLimit(String relativePath, SourceEntry.Kind kind) {
      String issue =
          issue(
              SourceIssue.Code.VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED,
              SourceIssue.Category.RESOURCE_LIMIT,
              SourceIssue.Scope.FILE,
              relativePath,
              SourceIssue.Operation.READ_INPUT,
              "source preparation resource limit stopped this file",
              null,
              null,
              SourceIssue.Resolution.OPEN,
              Set.of(SourceIssue.AllowedAction.NEW_PREPARATION));
      addEntry(
          entry(
              relativePath,
              kind,
              SourceEntry.Disposition.UNCHECKED,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(issue),
              null));
      inspectionStatus = SourcePreparationResult.InspectionStatus.ABORTED;
      enumerationComplete = false;
      stopped = true;
    }

    private void outputFailureForEntry(String relativePath, SourceEntry.Kind kind, String message) {
      String issue = outputFailureIssue(message);
      addEntry(
          entry(
              relativePath,
              kind,
              SourceEntry.Disposition.UNCHECKED,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(issue),
              null));
      stopForOutputFailure();
    }

    private String outputFailureIssue(String message) {
      return issue(
          SourceIssue.Code.SOURCE_OUTPUT_FAILED,
          SourceIssue.Category.OUTPUT,
          SourceIssue.Scope.OUTPUT,
          null,
          SourceIssue.Operation.SAVE_OUTPUT,
          message,
          null,
          null,
          SourceIssue.Resolution.OPEN,
          Set.of(SourceIssue.AllowedAction.FIX_OUTPUT));
    }

    private void excluded(
        String relativePath, BasicFileAttributes attributes, SourcePreparationTarget exclusion) {
      matchedExclusions.add(exclusion);
      addEntry(
          entry(
              relativePath,
              kind(attributes),
              SourceEntry.Disposition.EXCLUDED_BY_USER,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(),
              new SourceEntryExclusion("USER_DECLARED", request.operation(), relativePath)));
      if (attributes.isDirectory()) {
        unknownSubtrees.add(relativePath);
        enumerationComplete = false;
      }
    }

    private void gitMetadata(String relativePath, BasicFileAttributes attributes) {
      addEntry(
          entry(
              relativePath,
              kind(attributes),
              SourceEntry.Disposition.SKIPPED_GIT_METADATA,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(),
              null));
      if (attributes.isDirectory()) {
        unknownSubtrees.add(relativePath);
        enumerationComplete = false;
      }
    }

    private void symlink(String relativePath) {
      String issue =
          issue(
              SourceIssue.Code.UNSUPPORTED_ENTRY,
              SourceIssue.Category.UNSUPPORTED,
              SourceIssue.Scope.FILE,
              relativePath,
              SourceIssue.Operation.LIST_DIRECTORY,
              "symbolic link is listed but never followed",
              null,
              null,
              SourceIssue.Resolution.INFORMATIONAL,
              Set.of());
      addEntry(
          entry(
              relativePath,
              SourceEntry.Kind.SYMLINK,
              SourceEntry.Disposition.SKIPPED_SYMLINK,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(issue),
              null));
    }

    private void unsupported(String relativePath, SourceEntry.Kind kind) {
      String issue =
          issue(
              SourceIssue.Code.UNSUPPORTED_ENTRY,
              SourceIssue.Category.UNSUPPORTED,
              SourceIssue.Scope.FILE,
              relativePath,
              SourceIssue.Operation.LIST_DIRECTORY,
              "source entry kind is not supported for preparation",
              null,
              null,
              SourceIssue.Resolution.OPEN,
              Set.of(SourceIssue.AllowedAction.EXCLUDE_FILE));
      addEntry(
          entry(
              relativePath,
              kind,
              SourceEntry.Disposition.UNSUPPORTED,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(issue),
              null));
    }

    private String issue(
        SourceIssue.Code code,
        SourceIssue.Category category,
        SourceIssue.Scope scope,
        String relativePath,
        SourceIssue.Operation operation,
        String message,
        SourceObservation expected,
        SourceObservation observed,
        SourceIssue.Resolution resolution,
        Set<SourceIssue.AllowedAction> allowedActions) {
      String issueId =
          "source-issue:"
              + sha256(
                  code.name()
                      + "|"
                      + category.name()
                      + "|"
                      + scope.name()
                      + "|"
                      + String.valueOf(relativePath)
                      + "|"
                      + operation.name()
                      + "|"
                      + resolution.name()
                      + "|"
                      + message);
      issues.add(
          new SourceIssue(
              issueId,
              code,
              category,
              scope,
              relativePath,
              operation,
              message,
              expected,
              observed,
              resolution,
              allowedActions,
              null));
      return issueId;
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
              .filter(target -> !matchedExclusions.contains(target))
              .toList();
      return new SourcePreparationResult(
          inspectionStatus,
          enumerationComplete,
          entries,
          issues,
          unknownSubtrees.stream().sorted(DirectorySourceOriginReader::compareUtf8).toList(),
          unmatched);
    }

    private static String sha256(String value) {
      try {
        return java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      } catch (java.security.NoSuchAlgorithmException unavailable) {
        throw new IllegalStateException("SHA-256 is unavailable", unavailable);
      }
    }
  }
}
