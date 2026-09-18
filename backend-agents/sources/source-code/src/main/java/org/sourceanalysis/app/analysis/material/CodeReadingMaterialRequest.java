package org.sourceanalysis.app.analysis.material;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** Immutable already-read inputs for Step 05 material organization. */
public record CodeReadingMaterialRequest(
    VerifiedSourceInventoryReference sourceInventory,
    ProgramGraphsReference navigationPublication,
    AnalysisStepPublicationReference persistencePublication,
    JavaCodeIndex javaCodeIndex,
    PersistenceMaterialIndex persistenceIndex,
    CodeReadingMaterialProfile profile) {

  public CodeReadingMaterialRequest {
    sourceInventory = Objects.requireNonNull(sourceInventory, "verified source inventory");
    navigationPublication = Objects.requireNonNull(navigationPublication, "navigation publication");
    persistencePublication =
        Objects.requireNonNull(persistencePublication, "persistence publication");
    javaCodeIndex = Objects.requireNonNull(javaCodeIndex, "Java code index");
    persistenceIndex = Objects.requireNonNull(persistenceIndex, "persistence material index");
    profile = Objects.requireNonNull(profile, "reading material profile");
  }
}
