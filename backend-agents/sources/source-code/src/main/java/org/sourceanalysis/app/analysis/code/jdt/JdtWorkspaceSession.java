package org.sourceanalysis.app.analysis.code.jdt;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;

/** Owns one multi-project JDT LS workspace for one immutable verified-source module binding. */
final class JdtWorkspaceSession implements JavaCodeSession {

  private final JdtWorkspaceBinding binding;
  private final Path workspace;
  private final Map<String, Path> projectRoots;
  private final EngineDescriptor descriptor;
  private final JdtLanguageServerClient languageServer;
  private final Object lifecycleLock = new Object();
  private EntryCodeCollector collector;
  private WorkspaceSyntaxAccess syntaxAccess;
  private boolean closed;

  private JdtWorkspaceSession(
      JdtWorkspaceBinding binding,
      Path workspace,
      Map<String, Path> projectRoots,
      EngineDescriptor descriptor,
      JdtLanguageServerClient languageServer) {
    this.binding = binding;
    this.workspace = workspace;
    this.projectRoots = Map.copyOf(projectRoots);
    this.descriptor = descriptor;
    this.languageServer = languageServer;
  }

  static JdtWorkspaceSession open(
      JdtWorkspaceBinding binding,
      JdtLanguageServerClient languageServer,
      JdtProcessIsolation isolation) {
    Objects.requireNonNull(binding, "JDT workspace binding");
    Objects.requireNonNull(languageServer, "JDT language server");
    Objects.requireNonNull(isolation, "JDT process isolation");
    if (languageServer.isolation() != isolation) {
      throw new IllegalArgumentException(
          "JDT workspace session and client must share one process isolation boundary");
    }
    Path workspace = null;
    try {
      for (JdtWorkspaceBinding.ModuleBinding module : binding.modules()) {
        module.project().validateForJdt();
      }
      workspace = Files.createTempDirectory("source-analysis-jdt-workspace-");
      Path projectsRoot = Files.createDirectories(workspace.resolve("projects"));
      Path languageServerDataDirectory =
          Files.createDirectories(workspace.resolve("language-server-data"));
      Map<String, Path> roots = new LinkedHashMap<>();
      for (JdtWorkspaceBinding.ModuleBinding module : binding.modules()) {
        Path projectRoot =
            Files.createDirectories(projectsRoot.resolve(binding.projectName(module.modulePath())));
        module.project().projectSourcesInto(projectRoot);
        List<String> dependencyProjectNames =
            module.sourceModuleDependencyPaths().stream().map(binding::projectName).toList();
        JdtProjectSession.writeControlledProjectMetadata(
            module.project(),
            module.targetRuntime(),
            projectRoot,
            binding.projectName(module.modulePath()),
            dependencyProjectNames);
        roots.put(module.modulePath(), projectRoot);
      }
      languageServer.start(binding, workspace, roots, languageServerDataDirectory);
      for (JdtWorkspaceBinding.ModuleBinding module : binding.modules()) {
        com.fasterxml.jackson.databind.JsonNode settings =
            languageServer.readProjectSettings(roots.get(module.modulePath()).toUri().toString());
        JdtProjectSession.requireReadBackTargetRuntime(settings, module.targetRuntime());
        JdtProjectSession.requireReadBackClasspath(
            settings,
            module.project(),
            roots.get(module.modulePath()),
            module.sourceModuleDependencyPaths().stream().map(binding::projectName).toList());
      }
      return new JdtWorkspaceSession(
          binding,
          workspace,
          roots,
          new EngineDescriptor(
              "jdt",
              "jdt-workspace-session-v1",
              languageServer.toolVersions(),
              "MODULE_SPECIFIC",
              List.of(
                  "JDT_LANGUAGE_SERVER",
                  "MULTI_PROJECT_WORKSPACE",
                  "PER_MODULE_TARGET_RUNTIME_READBACK",
                  "PER_MODULE_CLASSPATH_READBACK",
                  "PER_MODULE_SYNTAX_HELPERS")),
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
          "JDT workspace projection could not be prepared",
          failure);
    }
  }

  Path projectRoot(String modulePath) {
    Path projectRoot = projectRoots.get(modulePath);
    if (projectRoot == null) {
      throw new IllegalArgumentException("JDT workspace module is unknown");
    }
    return projectRoot;
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
      JavaDeclarationCatalog catalog = collector().catalog();
      List<JavaDeclarationCatalog.MethodDeclarationView> owners =
          catalog.methods().stream()
              .filter(method -> entry.methodKey().equals(method.methodKey()))
              .filter(method -> entry.methodRange().equals(method.sourceRange()))
              .filter(this::isBoundSource)
              .toList();
      if (owners.size() != 1) {
        throw new IllegalArgumentException(
            "entry seed does not identify one workspace catalog declaration");
      }
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
      if (syntaxAccess != null) {
        try {
          syntaxAccess.close();
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
                "JDT workspace cleanup failed",
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
          CodeEngineException.JDT_QUERY_FAILED, "JDT workspace session is already closed");
    }
  }

  private boolean isBoundSource(JavaDeclarationCatalog.MethodDeclarationView method) {
    return binding.modules().stream()
        .anyMatch(module -> module.project().sourceEntries().contains(method.sourcePath()));
  }

  private EntryCodeCollector collector() {
    if (collector == null) {
      WorkspaceSourceAccess sourceAccess =
          new WorkspaceSourceAccess(binding, projectRoots, languageServer);
      syntaxAccess = new WorkspaceSyntaxAccess(binding, projectRoots, languageServer);
      JdtNavigationResolver navigation = new JdtNavigationResolver(languageServer, sourceAccess);
      List<String> sourcePaths =
          binding.modules().stream()
              .flatMap(module -> module.project().sourceEntries().stream())
              .filter(path -> path.endsWith(".java"))
              .sorted()
              .toList();
      collector =
          new EntryCodeCollector(
              binding.snapshotId(),
              "MODULE_SPECIFIC",
              sourcePaths,
              sourceAccess,
              syntaxAccess,
              navigation,
              CollectionBudget.standard());
    }
    return collector;
  }

  private static final class WorkspaceSourceAccess implements JdtNavigationResolver.SourceAccess {

    private final Map<String, ProjectedModule> modulesBySourcePath;
    private final JdtLanguageServerClient languageServer;

    private WorkspaceSourceAccess(
        JdtWorkspaceBinding binding,
        Map<String, Path> projectRoots,
        JdtLanguageServerClient languageServer) {
      this.languageServer = Objects.requireNonNull(languageServer, "JDT language server");
      Map<String, ProjectedModule> sourceOwners = new LinkedHashMap<>();
      for (JdtWorkspaceBinding.ModuleBinding module : binding.modules()) {
        Path root = projectRoot(projectRoots, module.modulePath());
        ProjectedModule projected = new ProjectedModule(root);
        for (String sourcePath : module.project().sourceEntries()) {
          if (sourceOwners.putIfAbsent(sourcePath, projected) != null) {
            throw new IllegalArgumentException("JDT workspace source ownership is ambiguous");
          }
        }
      }
      modulesBySourcePath = Map.copyOf(sourceOwners);
    }

    @Override
    public JdtNavigationResolver.SourceDocument open(String uri) {
      try {
        URI parsed = URI.create(uri);
        if (!"file".equalsIgnoreCase(parsed.getScheme())) {
          return null;
        }
        Path path = Path.of(parsed).toRealPath();
        for (Map.Entry<String, ProjectedModule> owner : modulesBySourcePath.entrySet()) {
          ProjectedModule module = owner.getValue();
          if (path.startsWith(module.root())
              && path.equals(module.root().resolve(owner.getKey()).toRealPath())) {
            return new JdtNavigationResolver.SourceDocument(
                owner.getKey(), Files.readString(path, StandardCharsets.UTF_8));
          }
        }
        return null;
      } catch (IOException | IllegalArgumentException failure) {
        return null;
      }
    }

    @Override
    public String uri(String sourcePath) {
      ProjectedModule owner = modulesBySourcePath.get(sourcePath);
      if (owner == null) {
        throw new IllegalArgumentException("source path is not admitted by the JDT workspace");
      }
      return owner.root().resolve(sourcePath).normalize().toUri().toString();
    }

    @Override
    public void activate(String uri) {
      JdtNavigationResolver.SourceDocument document = open(uri);
      if (document == null) {
        throw new CodeEngineException(
            CodeEngineException.SOURCE_INVALID,
            "JDT navigation source is outside the admitted workspace snapshot");
      }
      languageServer.openDocument(uri, document.text());
    }
  }

  private static final class WorkspaceSyntaxAccess
      implements EntryCodeCollector.SyntaxAccess, AutoCloseable {

    private final Map<String, JdtWorkspaceBinding.ModuleBinding> modulesBySourcePath;
    private final Map<String, Path> projectRoots;
    private final JdtLanguageServerClient languageServer;
    private final Map<String, JdtSyntaxHelperClient> helpersByModulePath = new LinkedHashMap<>();

    private WorkspaceSyntaxAccess(
        JdtWorkspaceBinding binding,
        Map<String, Path> projectRoots,
        JdtLanguageServerClient languageServer) {
      Map<String, JdtWorkspaceBinding.ModuleBinding> sourceOwners = new LinkedHashMap<>();
      for (JdtWorkspaceBinding.ModuleBinding module : binding.modules()) {
        for (String sourcePath : module.project().sourceEntries()) {
          if (sourceOwners.putIfAbsent(sourcePath, module) != null) {
            throw new IllegalArgumentException(
                "JDT workspace syntax source ownership is ambiguous");
          }
        }
      }
      modulesBySourcePath = Map.copyOf(sourceOwners);
      this.projectRoots = Map.copyOf(projectRoots);
      this.languageServer = Objects.requireNonNull(languageServer, "JDT language server");
    }

    @Override
    public synchronized JdtSyntaxProtocol.Response describe(
        String sourcePath, String ignoredLanguageLevel, String source) {
      JdtWorkspaceBinding.ModuleBinding module = modulesBySourcePath.get(sourcePath);
      if (module == null) {
        throw new CodeEngineException(
            CodeEngineException.SOURCE_INVALID,
            "JDT syntax source is outside the admitted workspace");
      }
      JdtSyntaxHelperClient helper =
          helpersByModulePath.computeIfAbsent(
              module.modulePath(),
              ignored ->
                  languageServer.openSyntaxHelper(
                      JdtSyntaxHelperArtifact.locate(),
                      module.project(),
                      module.targetRuntime(),
                      projectRoot(projectRoots, module.modulePath())));
      return helper.describe(sourcePath, module.project().sourceLevel(), source);
    }

    @Override
    public synchronized void close() {
      RuntimeException failure = null;
      for (JdtSyntaxHelperClient helper : new ArrayList<>(helpersByModulePath.values())) {
        try {
          helper.close();
        } catch (RuntimeException closeFailure) {
          if (failure == null) {
            failure = closeFailure;
          } else {
            failure.addSuppressed(closeFailure);
          }
        }
      }
      helpersByModulePath.clear();
      if (failure != null) {
        throw failure;
      }
    }
  }

  private static Path projectRoot(Map<String, Path> roots, String modulePath) {
    Path root = roots.get(modulePath);
    if (root == null) {
      throw new IllegalArgumentException("JDT workspace module has no projected root");
    }
    try {
      return root.toRealPath();
    } catch (IOException failure) {
      throw new CodeEngineException(
          CodeEngineException.SOURCE_INVALID,
          "JDT workspace projected root cannot be canonicalized",
          failure);
    }
  }

  private record ProjectedModule(Path root) {}

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
