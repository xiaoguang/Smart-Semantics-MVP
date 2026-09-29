package org.sourceanalysis.app.capture.localgit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constrained, local Git-plumbing access to the immutable objects of one exact SHA-1 commit.
 *
 * <p>Every command gets an empty, private environment and a watchdog that remains active while
 * stdout is consumed. Streams own their subprocesses: reaching EOF checks its exit status, while
 * closing early forcibly reaps it. This is deliberately an internal seam rather than a public
 * source-analysis API.
 */
public final class ConstrainedGitObjectAccess implements FixedGitObjectAccess {

  private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);
  private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
  private static final Pattern TREE_HEADER = Pattern.compile("(\\d{6}) ([A-Za-z]+) ([0-9a-f]{40})");
  private static final int MAX_TREE_RECORD_BYTES = 1024 * 1024;
  private static final int MAX_SMALL_OUTPUT_BYTES = 4096;

  private final Path privateWorkspace;
  private final Path gitExecutable;
  private final Duration commandTimeout;

  /** Creates production constrained access using the fixed two-minute command bound. */
  public ConstrainedGitObjectAccess(Path privateWorkspace, Path trustedGitExecutable)
      throws IOException {
    this(privateWorkspace, trustedGitExecutable, COMMAND_TIMEOUT);
  }

  /** Package-visible timeout override solely for deterministic controlled-process tests. */
  ConstrainedGitObjectAccess(
      Path privateWorkspace, Path trustedGitExecutable, Duration commandTimeout)
      throws IOException {
    if (commandTimeout == null || commandTimeout.isZero() || commandTimeout.isNegative()) {
      throw failure("LOCAL_GIT_REPOSITORY_INVALID");
    }
    this.privateWorkspace = requirePrivateWorkspace(privateWorkspace);
    this.gitExecutable = requireGitExecutable(trustedGitExecutable);
    this.commandTimeout = commandTimeout;
  }

  @Override
  public Session open(Path repository, String exactCommitId) throws IOException {
    if (repository == null || exactCommitId == null || !SHA1.matcher(exactCommitId).matches()) {
      throw failure("LOCAL_GIT_REPOSITORY_INVALID");
    }
    try {
      Path repositoryRoot = repository.toRealPath(LinkOption.NOFOLLOW_LINKS);
      requireDirectoryWithoutLinks(repositoryRoot, "LOCAL_GIT_REPOSITORY_INVALID");
      Path gitDirectory = validatedGitDirectory(repositoryRoot);
      rejectUnsupportedObjectSources(gitDirectory);
      runSmall(
          gitDirectory,
          List.of("cat-file", "-e", exactCommitId + "^{commit}"),
          "LOCAL_GIT_COMMIT_NOT_FOUND");
      String objectFormat =
          exactLine(
              runSmall(
                  gitDirectory,
                  List.of("rev-parse", "--show-object-format"),
                  "LOCAL_GIT_OBJECT_CORRUPT"));
      if (!objectFormat.equals("sha1")) {
        throw failure("LOCAL_GIT_REPOSITORY_INVALID");
      }
      String treeObjectId =
          exactLine(
              runSmall(
                  gitDirectory,
                  List.of("rev-parse", exactCommitId + "^{tree}"),
                  "LOCAL_GIT_OBJECT_CORRUPT"));
      if (!SHA1.matcher(treeObjectId).matches()) {
        throw failure("LOCAL_GIT_OBJECT_CORRUPT");
      }
      return new SessionImpl(gitDirectory, exactCommitId, treeObjectId);
    } catch (LocalGitCaptureException failure) {
      throw new IOException(failure);
    }
  }

  private Path requirePrivateWorkspace(Path candidate) throws IOException {
    if (candidate == null || !candidate.isAbsolute()) {
      throw failure("LOCAL_GIT_REPOSITORY_INVALID");
    }
    ensurePrivateDirectoryPath(candidate.toAbsolutePath().normalize());
    return candidate.toAbsolutePath().normalize();
  }

  private Path requireGitExecutable(Path candidate) throws IOException {
    if (candidate == null || !candidate.isAbsolute()) {
      throw failure("LOCAL_GIT_REPOSITORY_INVALID");
    }
    try {
      Path real = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
      BasicFileAttributes attributes =
          Files.readAttributes(real, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!attributes.isRegularFile() || attributes.isSymbolicLink() || !Files.isExecutable(real)) {
        throw failure("LOCAL_GIT_REPOSITORY_INVALID");
      }
      return real;
    } catch (NoSuchFileException missing) {
      throw failure("LOCAL_GIT_REPOSITORY_INVALID");
    }
  }

  private Path validatedGitDirectory(Path repositoryRoot) throws IOException {
    Path gitDirectory = repositoryRoot.resolve(".git");
    requireDirectoryWithoutLinks(gitDirectory, "LOCAL_GIT_REPOSITORY_INVALID");
    Path real = gitDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
    requireDirectoryWithoutLinks(real, "LOCAL_GIT_REPOSITORY_INVALID");
    requireDirectoryWithoutLinks(real.resolve("objects"), "LOCAL_GIT_REPOSITORY_INVALID");
    return real;
  }

  private void rejectUnsupportedObjectSources(Path gitDirectory) throws IOException {
    Path alternates = gitDirectory.resolve("objects/info/alternates");
    if (Files.exists(alternates, LinkOption.NOFOLLOW_LINKS)) {
      throw failure("LOCAL_GIT_ALTERNATES_UNSUPPORTED");
    }
    if (Files.exists(gitDirectory.resolve("shallow"), LinkOption.NOFOLLOW_LINKS)
        || Files.exists(gitDirectory.resolve("info/grafts"), LinkOption.NOFOLLOW_LINKS)
        || containsAnyEntry(gitDirectory.resolve("refs/replace"))) {
      throw failure("LOCAL_GIT_OBJECT_CORRUPT");
    }
    Path config = gitDirectory.resolve("config");
    if (!Files.exists(config, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    BasicFileAttributes attributes =
        Files.readAttributes(config, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
      throw failure("LOCAL_GIT_REPOSITORY_INVALID");
    }
    String content =
        Files.readString(config, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
    if (content.contains("partialclone")
        || content.contains("promisor")
        || Pattern.compile("(?m)^\\s*\\[\\s*include(?:if)?(?:\\s|\\])").matcher(content).find()) {
      throw failure("LOCAL_GIT_PROMISOR_UNSUPPORTED");
    }
  }

  private boolean containsAnyEntry(Path directory) throws IOException {
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    requireDirectoryWithoutLinks(directory, "LOCAL_GIT_REPOSITORY_INVALID");
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
      return entries.iterator().hasNext();
    }
  }

  private byte[] runSmall(Path gitDirectory, List<String> arguments, String failureCode)
      throws IOException {
    try (GitExecution execution = start(gitDirectory, arguments, failureCode);
        InputStream standardOut = execution.stream()) {
      return readBounded(standardOut, MAX_SMALL_OUTPUT_BYTES, execution);
    }
  }

  private GitExecution start(Path gitDirectory, List<String> arguments, String failureCode)
      throws IOException {
    Path privateHome = null;
    try {
      privateHome =
          Files.createDirectory(privateWorkspace.resolve(".git-env-" + UUID.randomUUID()));
      Path globalConfig = privateHome.resolve("global-config");
      Files.createFile(globalConfig);
      Path xdg = privateHome.resolve("xdg");
      Files.createDirectory(xdg);

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
      environment.put("XDG_CONFIG_HOME", xdg.toString());
      environment.put("GIT_CONFIG_NOSYSTEM", "1");
      environment.put("GIT_CONFIG_GLOBAL", globalConfig.toString());
      environment.put("GIT_NO_LAZY_FETCH", "1");
      environment.put("GIT_TERMINAL_PROMPT", "0");
      environment.put("GIT_OPTIONAL_LOCKS", "0");
      environment.put("GIT_PAGER", "cat");
      environment.put("PAGER", "cat");
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      return new GitExecution(builder.start(), privateHome, failureCode);
    } catch (IOException failure) {
      if (privateHome != null) {
        deletePrivateTree(privateHome);
      }
      throw failure("LOCAL_GIT_REPOSITORY_INVALID");
    }
  }

  private static byte[] readBounded(InputStream input, int maximumBytes, GitExecution execution)
      throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[512];
    while (true) {
      int read = input.read(buffer);
      if (read < 0) {
        return output.toByteArray();
      }
      if (read == 0) {
        continue;
      }
      if (output.size() > maximumBytes - read) {
        execution.abort();
        throw failure("LOCAL_GIT_OBJECT_CORRUPT");
      }
      output.write(buffer, 0, read);
    }
  }

  private static String exactLine(byte[] bytes) throws IOException {
    String value;
    try {
      value =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
    } catch (CharacterCodingException invalid) {
      throw failure("LOCAL_GIT_OBJECT_CORRUPT");
    }
    if (!value.endsWith("\n") || value.indexOf('\n') != value.length() - 1) {
      throw failure("LOCAL_GIT_OBJECT_CORRUPT");
    }
    return value.substring(0, value.length() - 1);
  }

  private static void ensurePrivateDirectoryPath(Path directory) throws IOException {
    Path root = directory.getRoot();
    if (root == null) {
      throw failure("LOCAL_GIT_REPOSITORY_INVALID");
    }
    BasicFileAttributes rootAttributes =
        Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!rootAttributes.isDirectory() || rootAttributes.isSymbolicLink()) {
      throw failure("LOCAL_GIT_REPOSITORY_INVALID");
    }
    Path current = root;
    for (Path segment : root.relativize(directory)) {
      current = current.resolve(segment);
      try {
        BasicFileAttributes attributes =
            Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
          throw failure("LOCAL_GIT_REPOSITORY_INVALID");
        }
      } catch (NoSuchFileException missing) {
        try {
          Files.createDirectory(current);
        } catch (FileAlreadyExistsException raced) {
          // The no-follow recheck below decides whether the raced entry is safe.
          if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            throw raced;
          }
        }
        BasicFileAttributes attributes =
            Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
          throw failure("LOCAL_GIT_REPOSITORY_INVALID");
        }
      }
    }
  }

  private static void requireDirectoryWithoutLinks(Path directory, String code) throws IOException {
    BasicFileAttributes attributes =
        Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
      throw failure(code);
    }
    Path root = directory.getRoot();
    if (root == null) {
      throw failure(code);
    }
    Path current = root;
    for (Path segment : root.relativize(directory.toAbsolutePath().normalize())) {
      current = current.resolve(segment);
      if (Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
          .isSymbolicLink()) {
        throw failure(code);
      }
    }
  }

  private static IOException failure(String code) {
    return new IOException(new LocalGitCaptureException(code));
  }

  private static void deletePrivateTree(Path root) {
    try {
      Files.walkFileTree(
          root,
          new SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(
                Path file, BasicFileAttributes attributes) throws IOException {
              Files.deleteIfExists(file);
              return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(
                Path directory, IOException failure) throws IOException {
              if (failure != null) {
                throw failure;
              }
              Files.deleteIfExists(directory);
              return java.nio.file.FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException ignored) {
      // A stale private environment is never reused and cannot affect public preparation facts.
    }
  }

  private final class SessionImpl implements Session {

    private final Path gitDirectory;
    private final String exactCommitId;
    private final String treeObjectId;
    private final Set<String> listedBlobObjectIds = new HashSet<>();
    private final Set<GitExecution> blobExecutions = new HashSet<>();
    private TreeDirectoryStream entries;
    private boolean closed;

    private SessionImpl(Path gitDirectory, String exactCommitId, String treeObjectId) {
      this.gitDirectory = gitDirectory;
      this.exactCommitId = exactCommitId;
      this.treeObjectId = treeObjectId;
    }

    @Override
    public String treeObjectId() {
      return treeObjectId;
    }

    @Override
    public DirectoryStream<TreeEntry> entries() throws IOException {
      requireOpen();
      if (entries != null) {
        throw failure("LOCAL_GIT_OBJECT_CORRUPT");
      }
      entries =
          new TreeDirectoryStream(
              start(
                  gitDirectory,
                  List.of("ls-tree", "-r", "-z", "--full-tree", exactCommitId),
                  "LOCAL_GIT_OBJECT_CORRUPT"),
              listedBlobObjectIds);
      return entries;
    }

    @Override
    public BlobInput openBlob(String objectId) throws IOException {
      requireOpen();
      if (objectId == null || !listedBlobObjectIds.contains(objectId)) {
        throw failure("LOCAL_GIT_OBJECT_CORRUPT");
      }
      String size =
          exactLine(
              runSmall(
                  gitDirectory, List.of("cat-file", "-s", objectId), "LOCAL_GIT_OBJECT_CORRUPT"));
      long sizeBytes;
      try {
        sizeBytes = Long.parseLong(size);
      } catch (NumberFormatException invalid) {
        throw failure("LOCAL_GIT_OBJECT_CORRUPT");
      }
      if (sizeBytes < 0L) {
        throw failure("LOCAL_GIT_OBJECT_CORRUPT");
      }
      GitExecution execution =
          start(gitDirectory, List.of("cat-file", "blob", objectId), "LOCAL_GIT_OBJECT_CORRUPT");
      blobExecutions.add(execution);
      return new BlobInput(sizeBytes, new BlobStream(execution));
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      IOException firstFailure = null;
      if (entries != null) {
        try {
          entries.close();
        } catch (IOException failure) {
          firstFailure = failure;
        }
      }
      for (GitExecution execution : Set.copyOf(blobExecutions)) {
        try {
          execution.abort();
        } catch (IOException failure) {
          if (firstFailure == null) {
            firstFailure = failure;
          }
        }
      }
      if (firstFailure != null) {
        throw firstFailure;
      }
    }

    private void requireOpen() throws IOException {
      if (closed) {
        throw failure("LOCAL_GIT_OBJECT_CORRUPT");
      }
    }
  }

  private static final class TreeDirectoryStream implements DirectoryStream<TreeEntry> {

    private final GitExecution execution;
    private final InputStream input;
    private final Set<String> listedBlobObjectIds;
    private boolean iteratorProvided;
    private boolean closed;
    private IOException failure;

    private TreeDirectoryStream(GitExecution execution, Set<String> listedBlobObjectIds) {
      this.execution = execution;
      this.input = execution.stream();
      this.listedBlobObjectIds = listedBlobObjectIds;
    }

    @Override
    public java.util.Iterator<TreeEntry> iterator() {
      if (iteratorProvided) {
        throw new IllegalStateException("Git tree stream supports one iterator");
      }
      iteratorProvided = true;
      return new java.util.Iterator<>() {
        private TreeEntry next;
        private boolean attempted;

        @Override
        public boolean hasNext() {
          if (next != null) {
            return true;
          }
          if (attempted || closed) {
            return false;
          }
          attempted = true;
          try {
            next = readEntry();
            return next != null;
          } catch (IOException readFailure) {
            failure = readFailure;
            return false;
          }
        }

        @Override
        public TreeEntry next() {
          if (!hasNext()) {
            throw new java.util.NoSuchElementException();
          }
          TreeEntry result = next;
          next = null;
          attempted = false;
          if (result.type().equals("blob")) {
            listedBlobObjectIds.add(result.objectId());
          }
          return result;
        }
      };
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        if (failure != null) {
          throw failure;
        }
        return;
      }
      closed = true;
      IOException closeFailure = null;
      try {
        input.close();
      } catch (IOException closeError) {
        closeFailure = closeError;
      }
      if (failure != null) {
        throw failure;
      }
      if (closeFailure != null) {
        throw closeFailure;
      }
    }

    private TreeEntry readEntry() throws IOException {
      ByteArrayOutputStream record = new ByteArrayOutputStream();
      while (true) {
        int value = input.read();
        if (value < 0) {
          if (record.size() != 0) {
            throw failure("LOCAL_GIT_OBJECT_CORRUPT");
          }
          return null;
        }
        if (value == 0) {
          return parseTreeRecord(record.toByteArray());
        }
        if (record.size() == MAX_TREE_RECORD_BYTES) {
          throw failure("LOCAL_GIT_OBJECT_CORRUPT");
        }
        record.write(value);
      }
    }
  }

  private static TreeEntry parseTreeRecord(byte[] record) throws IOException {
    int tab = -1;
    for (int index = 0; index < record.length; index++) {
      if (record[index] == '\t') {
        tab = index;
        break;
      }
    }
    if (tab <= 0 || tab == record.length - 1) {
      throw failure("LOCAL_GIT_OBJECT_CORRUPT");
    }
    String header = strictUtf8(record, 0, tab);
    Matcher matcher = TREE_HEADER.matcher(header);
    if (!matcher.matches()) {
      throw failure("LOCAL_GIT_OBJECT_CORRUPT");
    }
    String path = strictUtf8(record, tab + 1, record.length - tab - 1);
    try {
      return new TreeEntry(matcher.group(1), matcher.group(2), matcher.group(3), path);
    } catch (IllegalArgumentException invalid) {
      throw failure("LOCAL_GIT_OBJECT_CORRUPT");
    }
  }

  private static String strictUtf8(byte[] bytes, int offset, int length) throws IOException {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes, offset, length))
          .toString();
    } catch (CharacterCodingException invalid) {
      throw failure("LOCAL_GIT_OBJECT_CORRUPT");
    }
  }

  private static final class BlobStream extends InputStream {

    private final GitExecution execution;
    private final InputStream input;
    private boolean closed;

    private BlobStream(GitExecution execution) {
      this.execution = execution;
      this.input = execution.stream();
    }

    @Override
    public int read() throws IOException {
      int value = input.read();
      if (value < 0) {
        execution.awaitSuccess();
      }
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      int read = input.read(bytes, offset, length);
      if (read < 0) {
        execution.awaitSuccess();
      }
      return read;
    }

    @Override
    public void close() throws IOException {
      if (!closed) {
        closed = true;
        input.close();
        execution.abort();
      }
    }
  }

  private final class GitExecution implements AutoCloseable {

    private final Process process;
    private final Path privateHome;
    private final String failureCode;
    private final AtomicBoolean timedOut = new AtomicBoolean();
    private final Thread watchdog;
    private boolean aborted;
    private boolean completed;
    private boolean cleaned;

    private GitExecution(Process process, Path privateHome, String failureCode) {
      this.process = Objects.requireNonNull(process, "Git process");
      this.privateHome = Objects.requireNonNull(privateHome, "Git private environment");
      this.failureCode = Objects.requireNonNull(failureCode, "Git failure code");
      this.watchdog =
          new Thread(
              () -> {
                try {
                  Thread.sleep(commandTimeout.toMillis());
                  if (process.isAlive()) {
                    timedOut.set(true);
                    process.destroyForcibly();
                  }
                } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                }
              },
              "source-preparation-git-timeout");
      this.watchdog.setDaemon(true);
      this.watchdog.start();
    }

    private InputStream stream() {
      return new ProcessStream();
    }

    private synchronized void awaitSuccess() throws IOException {
      if (aborted) {
        return;
      }
      if (!completed) {
        try {
          process.waitFor();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          abort();
          throw failure("LOCAL_GIT_OBJECT_CORRUPT");
        }
        completed = true;
        finish();
      }
      if (timedOut.get() || process.exitValue() != 0) {
        throw failure(timedOut.get() ? "LOCAL_GIT_OBJECT_CORRUPT" : failureCode);
      }
    }

    private synchronized void abort() throws IOException {
      if (aborted || completed) {
        return;
      }
      aborted = true;
      if (process.isAlive()) {
        process.destroyForcibly();
      }
      try {
        process.waitFor(Math.max(1L, commandTimeout.toMillis()), TimeUnit.MILLISECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw failure("LOCAL_GIT_OBJECT_CORRUPT");
      } finally {
        finish();
      }
    }

    private synchronized void finish() {
      watchdog.interrupt();
      if (!cleaned) {
        cleaned = true;
        deletePrivateTree(privateHome);
      }
    }

    @Override
    public void close() throws IOException {
      abort();
    }

    private final class ProcessStream extends InputStream {

      private final InputStream standardOut = process.getInputStream();
      private boolean streamClosed;

      @Override
      public int read() throws IOException {
        int value = standardOut.read();
        if (value < 0) {
          awaitSuccess();
        }
        return value;
      }

      @Override
      public int read(byte[] bytes, int offset, int length) throws IOException {
        int read = standardOut.read(bytes, offset, length);
        if (read < 0) {
          awaitSuccess();
        }
        return read;
      }

      @Override
      public void close() throws IOException {
        if (!streamClosed) {
          streamClosed = true;
          standardOut.close();
          abort();
        }
      }
    }
  }
}
