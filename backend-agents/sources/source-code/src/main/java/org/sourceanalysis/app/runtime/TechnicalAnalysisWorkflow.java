package org.sourceanalysis.app.runtime;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryExecutor;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryRequest;
import org.sourceanalysis.app.analysis.discovery.DiscoveryProfile;
import org.sourceanalysis.app.analysis.discovery.MapperXmlResourceView;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialRequest;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.material.DefaultCodeReadingMaterialBuilder;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialPublisher;
import org.sourceanalysis.app.analysis.persistence.DefaultPersistenceAnalyzer;
import org.sourceanalysis.app.analysis.persistence.PersistenceAnalysisRequest;
import org.sourceanalysis.app.analysis.persistence.PersistenceConfiguration;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialPublisher;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;

/** Coordinates the JDT Step02--05 technical route over one verified source inventory. */
public final class TechnicalAnalysisWorkflow {

  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore stepArtifacts;
  private VerifiedSourceInventoryReference mapperXmlViewInventory;
  private MapperXmlResourceView mapperXmlResourceView;

  /** Creates the path-free JDT workflow over verified source and canonical publications. */
  public TechnicalAnalysisWorkflow(
      VerifiedSourceTextReader sourceReader,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts) {
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.stepArtifacts = Objects.requireNonNull(stepArtifacts, "analysis step artifact store");
  }

  /** Runs JDT-backed application discovery and creates the run-owned mapper XML view. */
  public TechnicalDiscoveryWorkflowResult discover(
      VerifiedSourceInventoryReference verifiedSourceInventory,
      DiscoveryProfile discoveryProfile,
      JavaCodeSession javaCodeSession) {
    Objects.requireNonNull(verifiedSourceInventory, "verified source inventory");
    Objects.requireNonNull(discoveryProfile, "discovery profile");
    Objects.requireNonNull(javaCodeSession, "Java code session");
    MapperXmlResourceView mapperResources =
        initializeMapperXmlResourceView(verifiedSourceInventory);
    ApplicationDiscoveryReference discovery =
        new ApplicationDiscoveryExecutor(
                sourceReader, moduleArtifacts, stepArtifacts, javaCodeSession, mapperResources)
            .execute(
                new ApplicationDiscoveryRequest(
                    new AnalysisStepPublicationAddress(
                        verifiedSourceInventory.publication().address().runId(),
                        AnalysisStepKey.APPLICATION_DISCOVERY),
                    verifiedSourceInventory,
                    discoveryProfile));
    return new TechnicalDiscoveryWorkflowResult(verifiedSourceInventory, discovery);
  }

  /** Publishes JDT navigation, persistence, and bounded reading materials for all entries. */
  public TechnicalAnalysisWorkflowResult continueAfterDiscovery(
      TechnicalDiscoveryWorkflowResult discoveryResult,
      JavaCodeSession javaCodeSession,
      ArtifactControls artifactControls,
      PersistenceConfiguration persistenceConfiguration,
      CodeReadingMaterialProfile readingMaterialProfile) {
    return continueAfterDiscovery(
        discoveryResult,
        javaCodeSession,
        artifactControls,
        persistenceConfiguration,
        readingMaterialProfile,
        List.of());
  }

  /** Publishes the same route for an explicit, ordered selected-entry sample. */
  public TechnicalAnalysisWorkflowResult continueAfterDiscovery(
      TechnicalDiscoveryWorkflowResult discoveryResult,
      JavaCodeSession javaCodeSession,
      ArtifactControls artifactControls,
      PersistenceConfiguration persistenceConfiguration,
      CodeReadingMaterialProfile readingMaterialProfile,
      List<String> selectedEntryIds) {
    Objects.requireNonNull(discoveryResult, "technical discovery result");
    Objects.requireNonNull(javaCodeSession, "Java code session");
    Objects.requireNonNull(artifactControls, "artifact controls");
    Objects.requireNonNull(persistenceConfiguration, "persistence configuration");
    Objects.requireNonNull(readingMaterialProfile, "reading material profile");
    Objects.requireNonNull(selectedEntryIds, "selected entry IDs");
    if (!"jdt".equals(javaCodeSession.descriptor().engineId())) {
      throw new IllegalArgumentException("reading-material continuation requires the JDT engine");
    }

    VerifiedSourceInventoryReference verifiedSourceInventory =
        discoveryResult.verifiedSourceInventory();
    ApplicationDiscoveryReference discovery = discoveryResult.applicationDiscovery();
    ProgramGraphsExecution programGraphs =
        new ProgramGraphsExecution(sourceReader, moduleArtifacts, stepArtifacts);
    ProgramGraphsReference navigation =
        selectedEntryIds.isEmpty()
            ? programGraphs.execute(
                verifiedSourceInventory, discovery, javaCodeSession, artifactControls)
            : programGraphs.execute(
                verifiedSourceInventory,
                discovery,
                javaCodeSession,
                artifactControls,
                selectedEntryIds);
    JavaCodeIndex javaIndex = new JavaCodeIndexReader(stepArtifacts).reopen(navigation);
    VerifiedSourceTextSet frozenSource = sourceReader.reopen(verifiedSourceInventory);

    PersistenceMaterialIndex persistenceIndex;
    if (persistenceConfiguration.enabled()) {
      MapperXmlResourceView mapperResources =
          mapperXmlResourceViewFor(verifiedSourceInventory, frozenSource);
      persistenceIndex =
          new DefaultPersistenceAnalyzer(mapperResources)
              .analyze(
                  new PersistenceAnalysisRequest(
                      javaIndex,
                      navigation,
                      frozenSource,
                      programGraphs.reopenMapperCatalog(
                          verifiedSourceInventory, discovery, artifactControls),
                      persistenceConfiguration));
    } else {
      persistenceIndex =
          new DefaultPersistenceAnalyzer()
              .analyze(
                  new PersistenceAnalysisRequest(
                      javaIndex, navigation, frozenSource, List.of(), persistenceConfiguration));
    }
    var persistence =
        new PersistenceMaterialPublisher(moduleArtifacts, stepArtifacts)
            .publish(verifiedSourceInventory, discovery, artifactControls, persistenceIndex);
    CodeReadingMaterialSet materials =
        new DefaultCodeReadingMaterialBuilder()
            .build(
                new CodeReadingMaterialRequest(
                    verifiedSourceInventory,
                    navigation,
                    persistence,
                    javaIndex,
                    persistenceIndex,
                    readingMaterialProfile));
    var readingMaterials =
        new CodeReadingMaterialPublisher(moduleArtifacts, stepArtifacts)
            .publish(discovery, artifactControls, materials);
    return new TechnicalAnalysisWorkflowResult(
        verifiedSourceInventory, discovery, navigation, persistence, readingMaterials);
  }

  private MapperXmlResourceView initializeMapperXmlResourceView(
      VerifiedSourceInventoryReference verifiedSourceInventory) {
    MapperXmlResourceView mapperResources =
        MapperXmlResourceView.open(sourceReader.reopen(verifiedSourceInventory));
    mapperXmlViewInventory = verifiedSourceInventory;
    mapperXmlResourceView = mapperResources;
    return mapperResources;
  }

  private MapperXmlResourceView mapperXmlResourceViewFor(
      VerifiedSourceInventoryReference verifiedSourceInventory,
      VerifiedSourceTextSet frozenSource) {
    if (mapperXmlResourceView != null && verifiedSourceInventory.equals(mapperXmlViewInventory)) {
      mapperXmlResourceView.requireSameFrozenSource(frozenSource);
      return mapperXmlResourceView;
    }
    return MapperXmlResourceView.open(frozenSource);
  }
}
