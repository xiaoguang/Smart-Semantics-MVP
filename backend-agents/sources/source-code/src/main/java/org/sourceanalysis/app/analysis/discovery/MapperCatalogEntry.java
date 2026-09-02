package org.sourceanalysis.app.analysis.discovery;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** One exact-name Java/XML Mapper candidate pairing that later graph work must still prove. */
public record MapperCatalogEntry(
    ArtifactId catalogEntryId,
    String javaInterfaceFqn,
    List<MapperMethodCandidate> javaMethodCandidates,
    String xmlResourcePath,
    String xmlNamespace,
    List<MapperStatementCandidate> xmlStatementCandidates,
    String bindingState) {

  public MapperCatalogEntry {
    Objects.requireNonNull(catalogEntryId, "catalog entry ID");
    if (javaInterfaceFqn == null
        || javaInterfaceFqn.isBlank()
        || xmlResourcePath == null
        || xmlResourcePath.isBlank()
        || xmlNamespace == null
        || xmlNamespace.isBlank()
        || !"CANDIDATE_NOT_YET_BOUND".equals(bindingState)) {
      throw new IllegalArgumentException("Mapper catalog entry has invalid candidate identity");
    }
    javaMethodCandidates = List.copyOf(javaMethodCandidates);
    xmlStatementCandidates = List.copyOf(xmlStatementCandidates);
  }
}
