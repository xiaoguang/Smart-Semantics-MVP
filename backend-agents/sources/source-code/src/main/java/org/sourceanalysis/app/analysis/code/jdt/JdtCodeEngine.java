package org.sourceanalysis.app.analysis.code.jdt;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.JavaCodeEngine;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaCompilationEnvironment;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;

/** The stage-one JDT-only engine; startup or index errors are never routed to JavaParser. */
public final class JdtCodeEngine implements JavaCodeEngine {

  private final EffectiveEngineConfiguration.JdtConfiguration configuration;

  public JdtCodeEngine(EffectiveEngineConfiguration effectiveConfiguration) {
    Objects.requireNonNull(effectiveConfiguration, "effective engine configuration");
    if (!EffectiveEngineConfiguration.JDT.equals(effectiveConfiguration.javaEngine())) {
      throw new IllegalArgumentException("JDT code engine requires JDT configuration");
    }
    configuration = effectiveConfiguration.jdt();
  }

  @Override
  public JavaCodeSession open(VerifiedJavaProject project) {
    throw new CodeEngineException(
        CodeEngineException.ENGINE_CONFIGURATION_INVALID,
        "JDT code engine requires an explicit verified target runtime binding");
  }

  /** Opens one JDT session only after binding its verified project to a target runtime. */
  public JavaCodeSession open(JdtProjectBinding binding) {
    Objects.requireNonNull(binding, "JDT project binding");
    JdtProcessIsolation isolation = JdtProcessIsolation.system();
    JdtLanguageServerClient languageServer = new JdtLanguageServerClient(configuration, isolation);
    return JdtProjectSession.open(binding, languageServer, isolation);
  }

  /** Opens one shared JDT workspace from the exact module environments admitted by readiness. */
  public JavaCodeSession open(JavaCompilationEnvironment environment) {
    return open(JdtWorkspaceBinding.from(environment));
  }

  /** Opens one shared LS workspace after the code-layer composer has verified every module. */
  JavaCodeSession open(JdtWorkspaceBinding binding) {
    Objects.requireNonNull(binding, "JDT workspace binding");
    JdtProcessIsolation isolation = JdtProcessIsolation.system();
    JdtLanguageServerClient languageServer = new JdtLanguageServerClient(configuration, isolation);
    return JdtWorkspaceSession.open(binding, languageServer, isolation);
  }
}
