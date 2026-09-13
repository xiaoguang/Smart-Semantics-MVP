package org.sourceanalysis.app.analysis.code.javaparser;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.JavaCodeEngine;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;

/** Retained syntax-first Java engine; it never starts JDT or silently falls back to it. */
public final class JavaParserCodeEngine implements JavaCodeEngine {

  public JavaParserCodeEngine(EffectiveEngineConfiguration configuration) {
    Objects.requireNonNull(configuration, "effective engine configuration");
    if (!EffectiveEngineConfiguration.JAVAPARSER.equals(configuration.javaEngine())) {
      throw new IllegalArgumentException("JavaParser code engine requires JavaParser selection");
    }
  }

  @Override
  public JavaCodeSession open(VerifiedJavaProject project) {
    return JavaParserProjectSession.open(project);
  }
}
