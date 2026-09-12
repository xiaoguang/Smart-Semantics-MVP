package org.sourceanalysis.app.analysis.code.jdt;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.JavaCodeEngine;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
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
    JdtProcessIsolation isolation = JdtProcessIsolation.system();
    JdtLanguageServerClient languageServer = new JdtLanguageServerClient(configuration, isolation);
    return JdtProjectSession.open(project, languageServer, isolation);
  }
}
