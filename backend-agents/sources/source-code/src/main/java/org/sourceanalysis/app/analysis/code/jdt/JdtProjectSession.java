package org.sourceanalysis.app.analysis.code.jdt;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;

/** A single verified-source projection and its owned JDT language-server process. */
public final class JdtProjectSession implements JavaCodeSession {

  private final VerifiedJavaProject project;
  private final Path workspace;
  private final Path projectRoot;
  private final Path languageServerDataDirectory;
  private final EngineDescriptor descriptor;
  private final JdtLanguageServerClient languageServer;
  private EntryCodeCollector collector;
  private JdtSyntaxHelperClient syntaxHelper;
  private boolean closed;

  private JdtProjectSession(
      VerifiedJavaProject project,
      Path workspace,
      Path projectRoot,
      Path languageServerDataDirectory,
      EngineDescriptor descriptor,
      JdtLanguageServerClient languageServer) {
    this.project = project;
    this.workspace = workspace;
    this.projectRoot = projectRoot;
    this.languageServerDataDirectory = languageServerDataDirectory;
    this.descriptor = descriptor;
    this.languageServer = languageServer;
  }

  static JdtProjectSession open(
      VerifiedJavaProject project,
      JdtLanguageServerClient languageServer,
      JdtProcessIsolation isolation) {
    Objects.requireNonNull(project, "verified Java project");
    Objects.requireNonNull(languageServer, "JDT language server");
    Objects.requireNonNull(isolation, "JDT process isolation");
    if (languageServer.isolation() != isolation) {
      throw new IllegalArgumentException(
          "JDT session and client must share one process isolation boundary");
    }
    project.validateForJdt();
    Path workspace = null;
    try {
      workspace = Files.createTempDirectory("source-analysis-jdt-");
      Path projectRoot = Files.createDirectories(workspace.resolve("project"));
      Path languageServerDataDirectory =
          Files.createDirectories(workspace.resolve("language-server-data"));
      project.projectSourcesInto(projectRoot);
      writeControlledProjectMetadata(project, projectRoot);
      languageServer.start(project, projectRoot, languageServerDataDirectory);
      return new JdtProjectSession(
          project,
          workspace,
          projectRoot,
          languageServerDataDirectory,
          new EngineDescriptor(
              "jdt",
              "jdt-session-v1",
              languageServer.toolVersions(),
              project.sourceLevel(),
              List.of("JDT_LANGUAGE_SERVER", "DECLARATION_READINESS_PROBE")),
          languageServer);
    } catch (IOException | RuntimeException failure) {
      try {
        languageServer.close();
      } catch (RuntimeException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      try {
        deleteWorkspace(workspace);
      } catch (IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      if (failure instanceof CodeEngineException codeEngineFailure) {
        throw codeEngineFailure;
      }
      throw new CodeEngineException(
          CodeEngineException.SOURCE_INVALID,
          "JDT session workspace could not be prepared",
          failure);
    }
  }

  public String snapshotId() {
    return project.snapshotId();
  }

  /** The session-owned parent used only to observe deterministic cleanup in integration tests. */
  public Path workspace() {
    return workspace;
  }

  Path projectRoot() {
    return projectRoot;
  }

  Path languageServerDataDirectory() {
    return languageServerDataDirectory;
  }

  @Override
  public synchronized JavaDeclarationCatalog catalog() {
    ensureOpen();
    return collector().catalog();
  }

  @Override
  public synchronized EntryCodeContext collect(EntrySeed entry) {
    Objects.requireNonNull(entry, "entry seed");
    ensureOpen();
    return collector().collect(entry);
  }

  @Override
  public EngineDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    RuntimeException failure = null;
    if (syntaxHelper != null) {
      try {
        syntaxHelper.close();
      } catch (RuntimeException closeFailure) {
        failure = closeFailure;
      }
    }
    try {
      languageServer.close();
    } catch (RuntimeException closeFailure) {
      if (failure == null) {
        failure = closeFailure;
      } else {
        failure.addSuppressed(closeFailure);
      }
    }
    try {
      deleteWorkspace(workspace);
    } catch (IOException cleanupFailure) {
      CodeEngineException observableCleanupFailure =
          new CodeEngineException(
              CodeEngineException.JDT_INDEX_FAILED,
              "JDT session-owned workspace cleanup failed",
              cleanupFailure);
      if (failure == null) {
        failure = observableCleanupFailure;
      } else {
        failure.addSuppressed(observableCleanupFailure);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new CodeEngineException(
          CodeEngineException.JDT_QUERY_FAILED, "JDT project session is already closed");
    }
  }

  private EntryCodeCollector collector() {
    if (collector == null) {
      syntaxHelper = languageServer.openSyntaxHelper(JdtSyntaxHelperArtifact.locate());
      ProjectedSourceAccess sourceAccess =
          new ProjectedSourceAccess(projectRoot, project.sourceEntries(), languageServer);
      JdtNavigationResolver navigation = new JdtNavigationResolver(languageServer, sourceAccess);
      collector =
          new EntryCodeCollector(
              project.snapshotId(),
              project.sourceLevel(),
              project.sourceEntries().stream().filter(path -> path.endsWith(".java")).toList(),
              sourceAccess,
              syntaxHelper::describe,
              navigation,
              CollectionBudget.standard());
    }
    return collector;
  }

  private static final class ProjectedSourceAccess implements JdtNavigationResolver.SourceAccess {

    private final Path root;
    private final java.util.Set<String> admitted;
    private final JdtLanguageServerClient languageServer;

    private ProjectedSourceAccess(
        Path root, List<String> sourceEntries, JdtLanguageServerClient languageServer) {
      try {
        this.root = root.toRealPath();
      } catch (IOException failure) {
        throw new CodeEngineException(
            CodeEngineException.SOURCE_INVALID,
            "JDT projected source root cannot be canonicalized",
            failure);
      }
      this.admitted = java.util.Set.copyOf(sourceEntries);
      this.languageServer = languageServer;
    }

    @Override
    public JdtNavigationResolver.SourceDocument open(String uri) {
      try {
        URI parsed = URI.create(uri);
        if (!"file".equalsIgnoreCase(parsed.getScheme())) {
          return null;
        }
        Path path = Path.of(parsed).toRealPath();
        if (!path.startsWith(root)) {
          return null;
        }
        String relative = root.relativize(path).toString().replace('\\', '/');
        if (!admitted.contains(relative) || !Files.isRegularFile(path)) {
          return null;
        }
        return new JdtNavigationResolver.SourceDocument(
            relative, Files.readString(path, StandardCharsets.UTF_8));
      } catch (IOException | IllegalArgumentException failure) {
        return null;
      }
    }

    @Override
    public String uri(String sourcePath) {
      if (!admitted.contains(sourcePath)) {
        throw new IllegalArgumentException("source path is not admitted by the snapshot");
      }
      return root.resolve(sourcePath).normalize().toUri().toString();
    }

    @Override
    public void activate(String uri) {
      JdtNavigationResolver.SourceDocument document = open(uri);
      if (document == null) {
        throw new CodeEngineException(
            CodeEngineException.SOURCE_INVALID,
            "JDT navigation source is outside the admitted snapshot");
      }
      languageServer.openDocument(uri, document.text());
    }
  }

  private static void writeControlledProjectMetadata(VerifiedJavaProject project, Path projectRoot)
      throws IOException {
    String projectName =
        "source-analysis-" + Integer.toUnsignedString(project.snapshotId().hashCode(), 36);
    Files.writeString(
        projectRoot.resolve(".project"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <projectDescription>
          <name>%s</name>
          <comment></comment>
          <projects></projects>
          <buildSpec></buildSpec>
          <natures><nature>org.eclipse.jdt.core.javanature</nature></natures>
        </projectDescription>
        """
            .formatted(xml(projectName)),
        StandardCharsets.UTF_8);

    StringBuilder classpath =
        new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<classpath>\n");
    for (String sourceRoot : project.sourceRoots()) {
      classpath
          .append("  <classpathentry kind=\"src\" path=\"")
          .append(xml(sourceRoot))
          .append("\"/>\n");
    }
    for (Path classpathEntry : project.classpath()) {
      classpath
          .append("  <classpathentry kind=\"lib\" path=\"")
          .append(xml(classpathEntry.toString()))
          .append("\"/>\n");
    }
    classpath
        .append("  <classpathentry kind=\"con\" path=\"")
        .append(
            xml(
                "org.eclipse.jdt.launching.JRE_CONTAINER/"
                    + "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-"
                    + project.sourceLevel()))
        .append("\"/>\n")
        .append("  <classpathentry kind=\"output\" path=\".jdt-output\"/>\n")
        .append("</classpath>\n");
    Files.writeString(projectRoot.resolve(".classpath"), classpath, StandardCharsets.UTF_8);

    Path settings = Files.createDirectories(projectRoot.resolve(".settings"));
    Files.writeString(
        settings.resolve("org.eclipse.jdt.core.prefs"),
        """
        eclipse.preferences.version=1
        org.eclipse.jdt.core.compiler.codegen.targetPlatform=%s
        org.eclipse.jdt.core.compiler.compliance=%s
        org.eclipse.jdt.core.compiler.source=%s
        """
            .formatted(project.sourceLevel(), project.sourceLevel(), project.sourceLevel()),
        StandardCharsets.UTF_8);
  }

  private static String xml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "&apos;");
  }

  private static void deleteWorkspace(Path workspace) throws IOException {
    if (workspace == null || !Files.exists(workspace)) {
      return;
    }
    Files.walkFileTree(
        workspace,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path directory, IOException failure)
              throws IOException {
            if (failure != null) {
              throw failure;
            }
            Files.delete(directory);
            return FileVisitResult.CONTINUE;
          }
        });
  }
}
