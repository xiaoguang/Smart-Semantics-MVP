package org.sourceanalysis.app.analysis.material;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/**
 * Already-reopened Step05 inputs for the entry-evidence assembler.
 *
 * <p>The request deliberately contains source-derived values rather than paths, parsers, or a tool
 * session. It is therefore valid to assemble after the original checkout and JDT process are gone.
 */
public record EntryEvidenceRequest(
    VerifiedSourceInventoryReference sourceInventory,
    ApplicationDiscoveryReference applicationDiscovery,
    ProgramGraphsReference navigationPublication,
    AnalysisStepPublicationReference persistencePublication,
    ModulePublicationReference frontendPublication,
    List<HttpEntryPoint> entries,
    JavaCodeIndex javaCodeIndex,
    PersistenceMaterialIndex persistenceIndex,
    FrontendHttpIndex frontendIndex,
    FrontendSourceUnits frontendSourceUnits,
    List<EntryEvidenceHttpMapping> httpMappings,
    EntryEvidenceProfile profile) {

  /** Retains callers that have no configured explicit frontend address mapping. */
  public EntryEvidenceRequest(
      VerifiedSourceInventoryReference sourceInventory,
      ApplicationDiscoveryReference applicationDiscovery,
      ProgramGraphsReference navigationPublication,
      AnalysisStepPublicationReference persistencePublication,
      ModulePublicationReference frontendPublication,
      List<HttpEntryPoint> entries,
      JavaCodeIndex javaCodeIndex,
      PersistenceMaterialIndex persistenceIndex,
      FrontendHttpIndex frontendIndex,
      FrontendSourceUnits frontendSourceUnits,
      EntryEvidenceProfile profile) {
    this(
        sourceInventory,
        applicationDiscovery,
        navigationPublication,
        persistencePublication,
        frontendPublication,
        entries,
        javaCodeIndex,
        persistenceIndex,
        frontendIndex,
        frontendSourceUnits,
        List.of(),
        profile);
  }

  public EntryEvidenceRequest {
    sourceInventory = Objects.requireNonNull(sourceInventory, "entry-evidence source inventory");
    applicationDiscovery =
        Objects.requireNonNull(applicationDiscovery, "entry-evidence application discovery");
    navigationPublication =
        Objects.requireNonNull(navigationPublication, "entry-evidence navigation publication");
    persistencePublication =
        Objects.requireNonNull(persistencePublication, "entry-evidence persistence publication");
    frontendPublication =
        Objects.requireNonNull(frontendPublication, "entry-evidence frontend publication");
    entries = List.copyOf(Objects.requireNonNull(entries, "entry-evidence HTTP entries"));
    javaCodeIndex = Objects.requireNonNull(javaCodeIndex, "entry-evidence Java index");
    persistenceIndex = Objects.requireNonNull(persistenceIndex, "entry-evidence persistence index");
    frontendIndex = Objects.requireNonNull(frontendIndex, "entry-evidence frontend index");
    frontendSourceUnits =
        Objects.requireNonNull(frontendSourceUnits, "entry-evidence frontend source units");
    httpMappings =
        List.copyOf(Objects.requireNonNull(httpMappings, "entry-evidence HTTP mappings"));
    profile = Objects.requireNonNull(profile, "entry-evidence profile");
    if (!sourceInventory.equals(frontendSourceUnits.sourceInventory())
        || !frontendPublication.equals(frontendSourceUnits.frontendPublication())) {
      throw new IllegalArgumentException("entry-evidence frontend units have the wrong upstream");
    }
  }
}
