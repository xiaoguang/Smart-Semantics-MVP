package org.sourceanalysis.app.analysis.code.jdt;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
  private final Object lifecycleLock = new Object();
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

  static JdtProjectSession open(
      JdtProjectBinding binding,
      JdtLanguageServerClient languageServer,
      JdtProcessIsolation isolation) {
    Objects.requireNonNull(binding, "JDT project binding");
    Objects.requireNonNull(languageServer, "JDT language server");
    Objects.requireNonNull(isolation, "JDT process isolation");
    if (languageServer.isolation() != isolation) {
      throw new IllegalArgumentException(
          "JDT session and client must share one process isolation boundary");
    }
    VerifiedJavaProject project = binding.project();
    project.validateForJdt();
    Path workspace = null;
    try {
      workspace = Files.createTempDirectory("source-analysis-jdt-");
      Path projectRoot = Files.createDirectories(workspace.resolve("project"));
      Path languageServerDataDirectory =
          Files.createDirectories(workspace.resolve("language-server-data"));
      project.projectSourcesInto(projectRoot);
      writeControlledProjectMetadata(project, binding.targetRuntime(), projectRoot);
      languageServer.start(binding, projectRoot, languageServerDataDirectory);
      requireReadBackTargetRuntime(
          languageServer.readProjectSettings(projectRoot.toUri().toString()),
          binding.targetRuntime());
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
  public JavaDeclarationCatalog catalog() {
    synchronized (lifecycleLock) {
      ensureOpen();
      return collector().catalog();
    }
  }

  @Override
  public EntryCodeContext collect(EntrySeed entry) {
    synchronized (lifecycleLock) {
      Objects.requireNonNull(entry, "entry seed");
      ensureOpen();
      return collector().collect(entry);
    }
  }

  @Override
  public EngineDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public void close() {
    synchronized (lifecycleLock) {
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
  }

  private void ensureOpen() {
    if (closed) {
      throw new CodeEngineException(
          CodeEngineException.JDT_QUERY_FAILED, "JDT project session is already closed");
    }
  }

  private EntryCodeCollector collector() {
    if (collector == null) {
      syntaxHelper =
          languageServer.openSyntaxHelper(JdtSyntaxHelperArtifact.locate(), project, projectRoot);
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
    writeControlledProjectMetadata(project, null, projectRoot);
  }

  private static void writeControlledProjectMetadata(
      VerifiedJavaProject project, JdtTargetRuntime targetRuntime, Path projectRoot)
      throws IOException {
    writeControlledProjectMetadata(
        project, targetRuntime, projectRoot, defaultProjectName(project), List.of());
  }

  static void writeControlledProjectMetadata(
      VerifiedJavaProject project,
      JdtTargetRuntime targetRuntime,
      Path projectRoot,
      String projectName,
      List<String> dependencyProjectNames)
      throws IOException {
    Objects.requireNonNull(project, "verified Java project");
    Objects.requireNonNull(projectRoot, "JDT project root");
    if (projectName == null || projectName.isBlank()) {
      throw new IllegalArgumentException("JDT project name is required");
    }
    List<String> dependencies = List.copyOf(Objects.requireNonNull(dependencyProjectNames));
    if (dependencies.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException("JDT project dependency names are required");
    }
    String projectDependencies =
        dependencies.stream()
            .map(name -> "<project>" + xml(name) + "</project>")
            .collect(java.util.stream.Collectors.joining());
    Files.writeString(
        projectRoot.resolve(".project"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <projectDescription>
          <name>${PROJECT_NAME}</name>
          <comment></comment>
          <projects>${PROJECT_DEPENDENCIES}</projects>
          <buildSpec>
            <buildCommand>
              <name>org.eclipse.jdt.core.javabuilder</name>
              <arguments></arguments>
            </buildCommand>
          </buildSpec>
          <natures><nature>org.eclipse.jdt.core.javanature</nature></natures>
        </projectDescription>
        """
            .replace("${PROJECT_NAME}", xml(projectName))
            .replace("${PROJECT_DEPENDENCIES}", projectDependencies),
        StandardCharsets.UTF_8);

    StringBuilder classpath =
        new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<classpath>\n");
    for (String sourceRoot : project.sourceRoots()) {
      classpath
          .append("  <classpathentry kind=\"src\" path=\"")
          .append(xml(sourceRoot))
          .append("\"/>\n");
    }
    for (String dependencyProjectName : dependencies) {
      classpath
          .append("  <classpathentry kind=\"src\" path=\"/")
          .append(xml(dependencyProjectName))
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
                    + "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/"
                    + (targetRuntime == null
                        ? "JavaSE-" + project.sourceLevel()
                        : targetRuntime.executionEnvironmentName())))
        .append("\"/>\n")
        .append("  <classpathentry kind=\"output\" path=\".jdt-output\"/>\n")
        .append("</classpath>\n");
    Files.writeString(projectRoot.resolve(".classpath"), classpath, StandardCharsets.UTF_8);

    Path settings = Files.createDirectories(projectRoot.resolve(".settings"));
    Files.writeString(
        settings.resolve("org.eclipse.jdt.core.prefs"),
        """
        eclipse.preferences.version=1
        org.eclipse.jdt.core.compiler.codegen.targetPlatform=${SOURCE_LEVEL}
        org.eclipse.jdt.core.compiler.compliance=${SOURCE_LEVEL}
        org.eclipse.jdt.core.compiler.source=${SOURCE_LEVEL}
        """
            .replace("${SOURCE_LEVEL}", project.sourceLevel()),
        StandardCharsets.UTF_8);
  }

  private static String defaultProjectName(VerifiedJavaProject project) {
    return "source-analysis-" + Integer.toUnsignedString(project.snapshotId().hashCode(), 36);
  }

  private static String xml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "&apos;");
  }

  static void requireReadBackTargetRuntime(
      com.fasterxml.jackson.databind.JsonNode settings, JdtTargetRuntime targetRuntime) {
    String location = settings.path("org.eclipse.jdt.ls.core.vm.location").textValue();
    if (location == null || location.isBlank()) {
      throw new CodeEngineException(
          CodeEngineException.JDT_PROTOCOL_INVALID,
          "JDT project-settings response has no target VM location");
    }
    try {
      Path observed = Path.of(location).toRealPath();
      if (!observed.equals(targetRuntime.targetJdkHome())) {
        throw new CodeEngineException(
            CodeEngineException.ENGINE_CONFIGURATION_INVALID,
            "JDT project VM does not match the selected target runtime");
      }
    } catch (IOException | RuntimeException failure) {
      if (failure instanceof CodeEngineException codeEngineFailure) {
        throw codeEngineFailure;
      }
      throw new CodeEngineException(
          CodeEngineException.ENGINE_CONFIGURATION_INVALID,
          "JDT project VM location cannot be verified",
          failure);
    }
  }

  static void requireReadBackClasspath(
      com.fasterxml.jackson.databind.JsonNode settings,
      VerifiedJavaProject project,
      Path projectRoot) {
    requireReadBackClasspath(settings, project, projectRoot, List.of());
  }

  static void requireReadBackClasspath(
      com.fasterxml.jackson.databind.JsonNode settings,
      VerifiedJavaProject project,
      Path projectRoot,
      List<String> expectedProjectNames) {
    Objects.requireNonNull(settings, "JDT project settings");
    Objects.requireNonNull(project, "verified Java project");
    Objects.requireNonNull(projectRoot, "JDT project root");
    Set<String> expectedProjects = expectedProjectNames(expectedProjectNames);
    com.fasterxml.jackson.databind.JsonNode entries =
        settings.path("org.eclipse.jdt.ls.core.classpathEntries");
    if (!entries.isArray()) {
      throw new CodeEngineException(
          CodeEngineException.JDT_PROTOCOL_INVALID,
          "JDT project-settings response has no classpath entries");
    }
    Set<Path> sourceRoots = new LinkedHashSet<>();
    Set<Path> libraries = new LinkedHashSet<>();
    Set<String> referencedProjects = new LinkedHashSet<>();
    for (com.fasterxml.jackson.databind.JsonNode entry : entries) {
      String value = entry.path("path").textValue();
      if (value == null || value.isBlank()) {
        throw new CodeEngineException(
            CodeEngineException.JDT_PROTOCOL_INVALID,
            "JDT project-settings classpath entry has no path");
      }
      int kind = entry.path("kind").asInt(-1);
      if (kind == 3) {
        String projectName = workspaceProjectName(value);
        if (projectName != null && expectedProjects.contains(projectName)) {
          referencedProjects.add(projectName);
        } else {
          sourceRoots.add(readBackFilesystemPath(value));
        }
      } else if (kind == 1) {
        libraries.add(readBackFilesystemPath(value));
      } else if (kind == 2) {
        referencedProjects.add(requireWorkspaceProjectName(value));
      }
    }
    try {
      Set<Path> expectedSourceRoots = new LinkedHashSet<>();
      for (String sourceRoot : project.sourceRoots()) {
        expectedSourceRoots.add(projectRoot.resolve(sourceRoot).toRealPath());
      }
      Set<Path> expectedLibraries = new LinkedHashSet<>();
      for (Path library : project.classpath()) {
        expectedLibraries.add(library.toRealPath());
      }
      if (!sourceRoots.containsAll(expectedSourceRoots)
          || !libraries.containsAll(expectedLibraries)
          || !referencedProjects.equals(expectedProjects)) {
        throw new CodeEngineException(
            CodeEngineException.ENGINE_CONFIGURATION_INVALID,
            "JDT project classpath does not include the selected module inputs");
      }
    } catch (IOException invalid) {
      throw new CodeEngineException(
          CodeEngineException.ENGINE_CONFIGURATION_INVALID,
          "JDT project classpath inputs cannot be verified",
          invalid);
    }
  }

  private static Path readBackFilesystemPath(String value) {
    try {
      return (value.startsWith("file:") ? Path.of(URI.create(value)) : Path.of(value)).toRealPath();
    } catch (IOException | IllegalArgumentException invalid) {
      throw new CodeEngineException(
          CodeEngineException.JDT_PROTOCOL_INVALID,
          "JDT project-settings classpath entry cannot be verified",
          invalid);
    }
  }

  private static Set<String> expectedProjectNames(List<String> projectNames) {
    Set<String> result = new LinkedHashSet<>();
    for (String projectName : List.copyOf(Objects.requireNonNull(projectNames))) {
      if (!result.add(requireWorkspaceProjectName("/" + projectName))) {
        throw new IllegalArgumentException("JDT project dependency names cannot repeat");
      }
    }
    return Set.copyOf(result);
  }

  private static String workspaceProjectName(String value) {
    if (value == null || !value.startsWith("/") || value.indexOf('/', 1) >= 0) {
      return null;
    }
    String projectName = value.substring(1);
    return projectName.isBlank() ? null : projectName;
  }

  private static String requireWorkspaceProjectName(String value) {
    String projectName = workspaceProjectName(value);
    if (projectName == null) {
      throw new CodeEngineException(
          CodeEngineException.JDT_PROTOCOL_INVALID,
          "JDT project-settings project entry has an invalid workspace project path");
    }
    return projectName;
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
