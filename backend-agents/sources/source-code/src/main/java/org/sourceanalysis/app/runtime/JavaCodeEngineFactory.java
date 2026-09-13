package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.JavaCodeEngine;
import org.sourceanalysis.app.analysis.code.javaparser.JavaParserCodeEngine;
import org.sourceanalysis.app.analysis.code.jdt.JdtCodeEngine;

/** Selects exactly one configured Java engine; stage one deliberately has no fallback path. */
public final class JavaCodeEngineFactory {

  public JavaCodeEngine create(EffectiveEngineConfiguration configuration) {
    Objects.requireNonNull(configuration, "effective engine configuration");
    if (EffectiveEngineConfiguration.JDT.equals(configuration.javaEngine())) {
      return new JdtCodeEngine(configuration);
    }
    return new JavaParserCodeEngine(configuration);
  }
}
