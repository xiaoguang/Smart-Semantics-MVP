package org.sourceanalysis.app.analysis.code.jdt;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;

/** Binds one verified Java module projection to its required target runtime. */
public record JdtProjectBinding(VerifiedJavaProject project, JdtTargetRuntime targetRuntime) {

  public JdtProjectBinding {
    Objects.requireNonNull(project, "verified Java project");
    Objects.requireNonNull(targetRuntime, "JDT target runtime");
  }
}
