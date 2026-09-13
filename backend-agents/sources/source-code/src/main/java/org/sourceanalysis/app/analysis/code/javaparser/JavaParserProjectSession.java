package org.sourceanalysis.app.analysis.code.javaparser;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;

/** One snapshot-bound JavaParser session over an owned projection of verified source bytes. */
public final class JavaParserProjectSession implements JavaCodeSession {

  private final Path workspace;
  private final JavaParserCatalogAdapter.ProjectModel project;
  private final EngineDescriptor descriptor;
  private boolean closed;

  private JavaParserProjectSession(
      Path workspace, JavaParserCatalogAdapter.ProjectModel project, String languageLevel) {
    this.workspace = workspace;
    this.project = project;
    descriptor =
        new EngineDescriptor(
            "javaparser",
            "javaparser-adapter-v1",
            Map.of("javaparser-core", JavaParserCatalogAdapter.toolVersion()),
            languageLevel,
            List.of(
                "SYNTAX_DECLARATION_CATALOG",
                "METHOD_SOURCE",
                "SYNTAX_CALL_SITES",
                "LIMITED_STATIC_TARGETS"));
  }

  static JavaParserProjectSession open(VerifiedJavaProject verifiedProject) {
    Objects.requireNonNull(verifiedProject, "verified Java project");
    verifiedProject.validateForJdt();
    Path workspace = null;
    try {
      workspace = Files.createTempDirectory("source-analysis-javaparser-");
      Path projection = Files.createDirectories(workspace.resolve("project"));
      verifiedProject.projectSourcesInto(projection);
      JavaParserCatalogAdapter.ProjectModel project =
          new JavaParserCatalogAdapter()
              .parse(
                  verifiedProject.snapshotId(),
                  verifiedProject.sourceEntries(),
                  projection,
                  verifiedProject.sourceLevel());
      return new JavaParserProjectSession(workspace, project, verifiedProject.sourceLevel());
    } catch (IOException | RuntimeException failure) {
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
          "JavaParser session workspace could not be prepared",
          failure);
    }
  }

  @Override
  public synchronized JavaDeclarationCatalog catalog() {
    ensureOpen();
    return project.catalog();
  }

  @Override
  public synchronized EntryCodeContext collect(EntrySeed entry) {
    ensureOpen();
    return new JavaParserContextAdapter(project).collect(entry);
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
    try {
      deleteWorkspace(workspace);
    } catch (IOException failure) {
      throw new CodeEngineException(
          CodeEngineException.SOURCE_INVALID,
          "JavaParser session workspace cleanup failed",
          failure);
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new CodeEngineException(
          CodeEngineException.SOURCE_INVALID, "JavaParser project session is already closed");
    }
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
