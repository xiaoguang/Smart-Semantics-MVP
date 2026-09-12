package org.sourceanalysis.app.analysis.interpretation.material;

/** Describes whether material came from a closed technical Flow or a safe source fallback. */
public enum BusinessMaterialMode {
  FLOW_PREFERRED,
  NAVIGATED_SOURCE,
  ENTRY_SOURCE_FALLBACK
}
