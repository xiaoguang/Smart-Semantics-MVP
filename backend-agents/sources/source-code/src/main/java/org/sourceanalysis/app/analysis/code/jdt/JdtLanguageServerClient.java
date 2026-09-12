package org.sourceanalysis.app.analysis.code.jdt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.lsp4j.DeclarationParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;

/** Owns the single JDT language-server process for one JDT project session. */
final class JdtLanguageServerClient implements AutoCloseable {

  private static final Duration EARLY_EXIT_CHECK = Duration.ofMillis(100);
  private static final Set<SymbolKind> DECLARATION_SYMBOL_KINDS =
      Set.of(SymbolKind.Class, SymbolKind.Interface, SymbolKind.Enum, SymbolKind.Struct);

  private final EffectiveEngineConfiguration.JdtConfiguration configuration;
  private final JdtProcessIsolation isolation;
  private Process process;
  private LanguageServer languageServer;
  private Future<Void> listening;

  JdtLanguageServerClient(EffectiveEngineConfiguration.JdtConfiguration configuration) {
    this(configuration, JdtProcessIsolation.system());
  }

  JdtLanguageServerClient(
      EffectiveEngineConfiguration.JdtConfiguration configuration, JdtProcessIsolation isolation) {
    this.configuration = java.util.Objects.requireNonNull(configuration, "JDT configuration");
    this.isolation = java.util.Objects.requireNonNull(isolation, "JDT process isolation");
  }

  synchronized void start(
      VerifiedJavaProject project, Path projectRoot, Path languageServerDataDirectory) {
    if (process != null) {
      throw new IllegalStateException("JDT language server is already started");
    }
    try {
      Files.createDirectories(languageServerDataDirectory);
      Process started = isolation.start(command(languageServerDataDirectory), projectRoot);
      process = started;
      if (started.waitFor(EARLY_EXIT_CHECK.toMillis(), TimeUnit.MILLISECONDS)) {
        throw indexFailed("JDT language server exited during startup", null);
      }

      Launcher<LanguageServer> launcher =
          LSPLauncher.createClientLauncher(
              new SessionLanguageClient(), started.getInputStream(), started.getOutputStream());
      listening = launcher.startListening();
      languageServer = launcher.getRemoteProxy();

      InitializeParams initialize = new InitializeParams();
      initialize.setRootUri(projectRoot.toUri().toString());
      initialize.setWorkspaceFolders(
          List.of(new WorkspaceFolder(projectRoot.toUri().toString(), "source-analysis-project")));
      initialize.setInitializationOptions(
          Map.of(
              "java.autobuild.enabled", false,
              "java.import.eclipse.enabled", false,
              "java.import.gradle.enabled", false,
              "java.import.maven.enabled", false,
              "java.configuration.updateBuildConfiguration", "disabled",
              "sourceAnalysis.snapshotId", project.snapshotId(),
              "sourceAnalysis.sourceLevel", project.sourceLevel()));
      awaitIndexReady(languageServer.initialize(initialize));
      languageServer.initialized(new InitializedParams());
      declarationProbe(project, projectRoot);
    } catch (IOException failure) {
      CodeEngineException startupFailure =
          new CodeEngineException(
              CodeEngineException.JDT_TOOL_UNAVAILABLE,
              "JDT language server could not start",
              failure);
      stopAfterFailedStart(startupFailure);
      throw startupFailure;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      CodeEngineException startupFailure =
          indexFailed("JDT language-server startup was interrupted", failure);
      stopAfterFailedStart(startupFailure);
      throw startupFailure;
    } catch (ExecutionException | TimeoutException failure) {
      CodeEngineException startupFailure =
          indexFailed("JDT language server did not initialize its project index", failure);
      stopAfterFailedStart(startupFailure);
      throw startupFailure;
    } catch (CodeEngineException failure) {
      stopAfterFailedStart(failure);
      throw failure;
    }
  }

  Map<String, String> toolVersions() {
    return Map.of(
        "jdtls", configuration.distributionIdentity(),
        "jdk", configuration.javaVersion());
  }

  JdtProcessIsolation isolation() {
    return isolation;
  }

  @Override
  public synchronized void close() {
    CodeEngineException failure = null;
    if (languageServer != null) {
      try {
        languageServer
            .shutdown()
            .get(configuration.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
        languageServer.exit();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        failure = indexFailed("JDT language-server shutdown was interrupted", interrupted);
      } catch (ExecutionException | TimeoutException shutdownFailure) {
        failure = indexFailed("JDT language server did not shut down cleanly", shutdownFailure);
      }
    }
    if (process != null && !isolation.stop(process, configuration.shutdownTimeout())) {
      failure = indexFailed("JDT language-server process remained alive after shutdown", null);
    }
    process = null;
    languageServer = null;
    listening = null;
    if (failure != null) {
      throw failure;
    }
  }

  private List<String> command(Path languageServerDataDirectory) {
    return List.of(
        configuration.javaHome().resolve("bin").resolve("java").toString(),
        "-Declipse.application=org.eclipse.jdt.ls.core.id1",
        "-Dosgi.bundles.defaultStartLevel=4",
        "-Declipse.product=org.eclipse.jdt.ls.core.product",
        "-jar",
        configuration.launcherJar().toString(),
        "-configuration",
        configuration.platformConfiguration().toString(),
        "-data",
        languageServerDataDirectory.toString());
  }

  private void stopAfterFailedStart(CodeEngineException startupFailure) {
    boolean stopped = true;
    if (process != null) {
      stopped = isolation.stop(process, configuration.shutdownTimeout());
      if (!stopped) {
        startupFailure.addSuppressed(
            indexFailed(
                "JDT language-server process remained alive after failed startup cleanup (stop=false)",
                null));
      }
    }
    if (stopped) {
      process = null;
    }
    languageServer = null;
    listening = null;
  }

  private void awaitIndexReady(Future<?> initialization)
      throws InterruptedException, ExecutionException, TimeoutException {
    long deadline = System.nanoTime() + configuration.startupTimeout().toNanos();
    while (true) {
      long remainingNanos = deadline - System.nanoTime();
      if (remainingNanos <= 0L) {
        throw new TimeoutException("JDT initialization deadline elapsed");
      }
      long waitMillis = Math.max(1L, Math.min(100L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
      try {
        initialization.get(waitMillis, TimeUnit.MILLISECONDS);
        return;
      } catch (TimeoutException waiting) {
        if (process == null || !process.isAlive()) {
          throw indexFailed("JDT language server exited before initialization", waiting);
        }
      }
    }
  }

  private void declarationProbe(VerifiedJavaProject project, Path projectRoot)
      throws IOException, InterruptedException, ExecutionException, TimeoutException {
    DeclarationProbeTarget target = null;
    for (String sourceEntry : project.sourceEntries()) {
      if (!sourceEntry.endsWith(".java")) {
        continue;
      }
      String uri = projectRoot.resolve(sourceEntry).toUri().toString();
      List<Either<SymbolInformation, DocumentSymbol>> symbols =
          languageServer
              .getTextDocumentService()
              .documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(uri)))
              .get(configuration.queryTimeout().toMillis(), TimeUnit.MILLISECONDS);
      Position position = firstDeclarationPosition(symbols, uri);
      if (position != null) {
        target = new DeclarationProbeTarget(uri, position);
        break;
      }
    }
    if (target == null) {
      throw indexFailed("JDT returned no declaration symbol for readiness probe", null);
    }
    DeclarationParams probe = new DeclarationParams();
    probe.setTextDocument(new TextDocumentIdentifier(target.uri()));
    probe.setPosition(target.position());
    Either<
            List<? extends org.eclipse.lsp4j.Location>,
            List<? extends org.eclipse.lsp4j.LocationLink>>
        declaration =
            languageServer
                .getTextDocumentService()
                .declaration(probe)
                .get(configuration.queryTimeout().toMillis(), TimeUnit.MILLISECONDS);
    List<?> locations =
        declaration == null
            ? null
            : declaration.isLeft() ? declaration.getLeft() : declaration.getRight();
    if (locations == null || locations.isEmpty()) {
      throw indexFailed("JDT declaration readiness probe returned no declaration", null);
    }
  }

  private static Position firstDeclarationPosition(
      List<Either<SymbolInformation, DocumentSymbol>> symbols, String documentUri) {
    if (symbols == null) {
      return null;
    }
    for (Either<SymbolInformation, DocumentSymbol> symbol : symbols) {
      if (symbol == null) {
        continue;
      }
      if (symbol.isLeft()) {
        SymbolInformation information = symbol.getLeft();
        if (information != null
            && DECLARATION_SYMBOL_KINDS.contains(information.getKind())
            && information.getLocation() != null
            && documentUri.equals(information.getLocation().getUri())
            && information.getLocation().getRange() != null) {
          return information.getLocation().getRange().getStart();
        }
      } else {
        Position position = firstDeclarationPosition(symbol.getRight());
        if (position != null) {
          return position;
        }
      }
    }
    return null;
  }

  private static Position firstDeclarationPosition(DocumentSymbol symbol) {
    if (symbol == null) {
      return null;
    }
    if (DECLARATION_SYMBOL_KINDS.contains(symbol.getKind()) && symbol.getSelectionRange() != null) {
      return symbol.getSelectionRange().getStart();
    }
    if (symbol.getChildren() != null) {
      for (DocumentSymbol child : symbol.getChildren()) {
        Position position = firstDeclarationPosition(child);
        if (position != null) {
          return position;
        }
      }
    }
    return null;
  }

  private record DeclarationProbeTarget(String uri, Position position) {}

  private static CodeEngineException indexFailed(String detail, Throwable cause) {
    return new CodeEngineException(CodeEngineException.JDT_INDEX_FAILED, detail, cause);
  }

  private static final class SessionLanguageClient implements LanguageClient {

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {}

    @Override
    public void showMessage(MessageParams message) {}

    @Override
    public java.util.concurrent.CompletableFuture<MessageActionItem> showMessageRequest(
        org.eclipse.lsp4j.ShowMessageRequestParams request) {
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {}
  }
}
