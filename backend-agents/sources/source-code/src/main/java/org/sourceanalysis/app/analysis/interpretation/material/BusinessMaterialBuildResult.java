package org.sourceanalysis.app.analysis.interpretation.material;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** A readable material set together with the persisted checkpoint that must precede model calls. */
public record BusinessMaterialBuildResult(
    BusinessMaterialSet materialSet, ModulePublicationReference checkpoint) {

  public BusinessMaterialBuildResult {
    Objects.requireNonNull(materialSet, "business material set");
    Objects.requireNonNull(checkpoint, "business material checkpoint");
  }
}
