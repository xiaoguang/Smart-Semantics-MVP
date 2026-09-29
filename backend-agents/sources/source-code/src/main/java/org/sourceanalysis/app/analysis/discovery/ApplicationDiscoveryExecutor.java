package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;

/** Executes the fixed M1–M4 application-discovery workflow over one frozen source inventory. */
public final class ApplicationDiscoveryExecutor {

  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore stepArtifacts;
  private final CanonicalAnalysisStepArtifactStore sourceStepArtifacts;
  private final JavaCodeSession javaCodeSession;
  private final MapperXmlResourceView mapperXmlResourceView;

  /** Creates an executor whose Java declarations come only from the selected engine session. */
  public ApplicationDiscoveryExecutor(
      VerifiedSourceTextReader sourceReader,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts,
      JavaCodeSession javaCodeSession) {
    this(sourceReader, moduleArtifacts, stepArtifacts, stepArtifacts, javaCodeSession);
  }

  /**
   * Creates the technical cross-policy seam. The source store reopens R0 only; the regular step
   * store remains the sole owner of M1–M4 and the final R1 Step02 receipt.
   */
  public ApplicationDiscoveryExecutor(
      VerifiedSourceTextReader sourceReader,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts,
      CanonicalAnalysisStepArtifactStore sourceStepArtifacts,
      JavaCodeSession javaCodeSession) {
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.stepArtifacts = Objects.requireNonNull(stepArtifacts, "analysis step artifact store");
    this.sourceStepArtifacts =
        Objects.requireNonNull(sourceStepArtifacts, "source analysis-step artifact store");
    this.javaCodeSession = Objects.requireNonNull(javaCodeSession, "Java code session");
    this.mapperXmlResourceView = null;
  }

  /**
   * Creates a JDT discovery executor that consumes one workflow-owned, source-bound Mapper XML view
   * rather than reparsing XML for the mapper catalog.
   */
  public ApplicationDiscoveryExecutor(
      VerifiedSourceTextReader sourceReader,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts,
      JavaCodeSession javaCodeSession,
      MapperXmlResourceView mapperXmlResourceView) {
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.stepArtifacts = Objects.requireNonNull(stepArtifacts, "analysis step artifact store");
    this.sourceStepArtifacts = stepArtifacts;
    this.javaCodeSession = Objects.requireNonNull(javaCodeSession, "Java code session");
    this.mapperXmlResourceView =
        Objects.requireNonNull(mapperXmlResourceView, "mapper XML resource view");
  }

  /** Runs M1 through M4 in their sole allowed order. */
  public ApplicationDiscoveryReference execute(ApplicationDiscoveryRequest request) {
    return executeInternal(request, null).publication();
  }

  /**
   * Executes the technical R1 producer over immutable R0 source bytes.
   *
   * <p>The explicit controls are the owner of every Step02 module and final receipt; the R0
   * verified-source reference remains the only source provenance.
   */
  public TechnicalApplicationDiscovery executeTechnical(
      ApplicationDiscoveryRequest request, ArtifactControls executionControls) {
    return executeInternal(
        request, Objects.requireNonNull(executionControls, "technical execution controls"));
  }

  private TechnicalApplicationDiscovery executeInternal(
      ApplicationDiscoveryRequest request, ArtifactControls executionControls) {
    try {
      requireDestination(request, executionControls != null);
      VerifiedSourceTextSet technicalSource =
          executionControls == null ? null : sourceReader.reopen(request.verifiedSourceInventory());
      ApplicationProfile detected =
          new ApplicationProfileDetector(sourceReader)
              .detect(
                  request.verifiedSourceInventory(), request.discoveryProfile(), executionControls);
      ApplicationProfileDraftReference profileDraft =
          new ApplicationProfileModulePublisher(moduleArtifacts)
              .publish(
                  new org.sourceanalysis.app.artifact.AnalysisStepModuleAddress(
                      request.destination().runId(),
                      org.sourceanalysis.app.artifact.AnalysisStepKey.APPLICATION_DISCOVERY,
                      1,
                      "application-profile"),
                  detected);
      ApplicationProfile profile =
          executionControls == null
              ? new PersistedApplicationProfileReader(moduleArtifacts, sourceReader)
                  .reopen(profileDraft, request.verifiedSourceInventory())
              : new PersistedApplicationProfileReader(moduleArtifacts, sourceReader)
                  .reopenTechnical(
                      profileDraft, request.verifiedSourceInventory(), executionControls);
      JavaDeclarationCatalog javaCatalog = javaCodeSession.catalog();
      HttpEntryDiscovery entries =
          executionControls == null
              ? new SpringHttpEntryDiscoverer(sourceReader)
                  .discoverEntries(profile, request.verifiedSourceInventory(), javaCatalog)
              : new SpringHttpEntryDiscoverer(sourceReader)
                  .discoverEntries(
                      profile, request.verifiedSourceInventory(), javaCatalog, executionControls);
      HttpEntryDiscoveryDraftReference entryDraft =
          new HttpEntryDiscoveryModulePublisher(moduleArtifacts)
              .publish(
                  new org.sourceanalysis.app.artifact.AnalysisStepModuleAddress(
                      request.destination().runId(),
                      org.sourceanalysis.app.artifact.AnalysisStepKey.APPLICATION_DISCOVERY,
                      2,
                      "http-entry"),
                  profileDraft,
                  profile,
                  entries);
      MapperCatalogDiscovery catalog =
          catalogMappers(profile, request, javaCatalog, executionControls);
      MapperCatalogDraftReference catalogDraft =
          new MapperCatalogModulePublisher(moduleArtifacts)
              .publish(
                  new org.sourceanalysis.app.artifact.AnalysisStepModuleAddress(
                      request.destination().runId(),
                      org.sourceanalysis.app.artifact.AnalysisStepKey.APPLICATION_DISCOVERY,
                      3,
                      "mapper-catalog"),
                  profileDraft,
                  profile,
                  catalog);
      ApplicationDiscoveryPublicationRequest publicationRequest =
          new ApplicationDiscoveryPublicationRequest(
              request.destination(),
              request.verifiedSourceInventory(),
              profileDraft,
              entryDraft,
              catalogDraft);
      ApplicationDiscoveryReference publication =
          executionControls == null
              ? new ApplicationDiscoveryPublicationSpecifier(moduleArtifacts, stepArtifacts)
                  .publish(publicationRequest)
              : new ApplicationDiscoveryPublicationSpecifier(
                      moduleArtifacts, stepArtifacts, sourceStepArtifacts)
                  .publishTechnical(
                      publicationRequest,
                      technicalSource.sourceInventoryRef(),
                      technicalSource.verifiedSnapshotRef());
      return new TechnicalApplicationDiscovery(publication, entries);
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_EXECUTION_INVALID", failure);
    }
  }

  private static void requireDestination(ApplicationDiscoveryRequest request, boolean technical) {
    if (request == null
        || request.destination().analysisStepKey()
            != org.sourceanalysis.app.artifact.AnalysisStepKey.APPLICATION_DISCOVERY
        || (!technical
            && !request
                .destination()
                .runId()
                .equals(request.verifiedSourceInventory().publication().address().runId()))) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_EXECUTION_INVALID");
    }
  }

  private MapperCatalogDiscovery catalogMappers(
      ApplicationProfile profile,
      ApplicationDiscoveryRequest request,
      JavaDeclarationCatalog javaCatalog,
      ArtifactControls executionControls) {
    MapperCapabilityCataloger cataloger = new MapperCapabilityCataloger(sourceReader);
    if (mapperXmlResourceView == null) {
      return executionControls == null
          ? cataloger.catalogMappers(profile, request.verifiedSourceInventory(), javaCatalog)
          : cataloger.catalogMappers(
              profile, request.verifiedSourceInventory(), javaCatalog, executionControls);
    }
    VerifiedSourceTextSet source = sourceReader.reopen(request.verifiedSourceInventory());
    return executionControls == null
        ? cataloger.catalogMappers(profile, source, javaCatalog, mapperXmlResourceView)
        : cataloger.catalogMappers(
            profile, source, javaCatalog, mapperXmlResourceView, executionControls);
  }
}
