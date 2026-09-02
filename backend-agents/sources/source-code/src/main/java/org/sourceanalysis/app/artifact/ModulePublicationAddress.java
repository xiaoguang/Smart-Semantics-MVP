package org.sourceanalysis.app.artifact;

/** The closed, path-free address of a named module publication. */
public sealed interface ModulePublicationAddress
    permits AnalysisStepModuleAddress, ValidationModuleAddress {

  /** Returns the execution identity that owns this module publication. */
  AnalysisRunId runId();

  /** Returns the one-based registered module position within its owner. */
  int moduleNumber();

  /** Returns the exact registered lowercase module key. */
  String moduleKey();
}
