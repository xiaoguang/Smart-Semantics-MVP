package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;

/** Executes the fixed M1–M4 application-discovery workflow over one frozen source inventory. */
public final class ApplicationDiscoveryExecutor {

  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore stepArtifacts;
  private final JavaCodeSession javaCodeSession;

  /** Creates a path-free executor over verified source bytes and the canonical stores. */
  public ApplicationDiscoveryExecutor(
      VerifiedSourceTextReader sourceReader,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts) {
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.stepArtifacts = Objects.requireNonNull(stepArtifacts, "analysis step artifact store");
    this.javaCodeSession = null;
  }

  /** Creates an executor whose Java declarations come only from the selected engine session. */
  public ApplicationDiscoveryExecutor(
      VerifiedSourceTextReader sourceReader,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts,
      JavaCodeSession javaCodeSession) {
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.stepArtifacts = Objects.requireNonNull(stepArtifacts, "analysis step artifact store");
    this.javaCodeSession = Objects.requireNonNull(javaCodeSession, "Java code session");
  }

  /** Runs M1 through M4 in their sole allowed order. */
  public ApplicationDiscoveryReference execute(ApplicationDiscoveryRequest request) {
    try {
      requireDestination(request);
      ApplicationProfile detected =
          new ApplicationProfileDetector(sourceReader)
              .detect(request.verifiedSourceInventory(), request.discoveryProfile());
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
          new PersistedApplicationProfileReader(moduleArtifacts, sourceReader)
              .reopen(profileDraft, request.verifiedSourceInventory());
      JavaDeclarationCatalog javaCatalog =
          javaCodeSession == null ? null : javaCodeSession.catalog();
      HttpEntryDiscovery entries =
          javaCatalog == null
              ? new SpringHttpEntryDiscoverer(sourceReader)
                  .discoverEntries(profile, request.verifiedSourceInventory())
              : new SpringHttpEntryDiscoverer(sourceReader)
                  .discoverEntries(profile, request.verifiedSourceInventory(), javaCatalog);
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
          javaCatalog == null
              ? new MapperCapabilityCataloger(sourceReader)
                  .catalogMappers(profile, request.verifiedSourceInventory())
              : new MapperCapabilityCataloger(sourceReader)
                  .catalogMappers(profile, request.verifiedSourceInventory(), javaCatalog);
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
      return new ApplicationDiscoveryPublicationSpecifier(moduleArtifacts, stepArtifacts)
          .publish(
              new ApplicationDiscoveryPublicationRequest(
                  request.destination(),
                  request.verifiedSourceInventory(),
                  profileDraft,
                  entryDraft,
                  catalogDraft));
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_EXECUTION_INVALID");
    }
  }

  private static void requireDestination(ApplicationDiscoveryRequest request) {
    if (request == null
        || request.destination().analysisStepKey()
            != org.sourceanalysis.app.artifact.AnalysisStepKey.APPLICATION_DISCOVERY
        || !request
            .destination()
            .runId()
            .equals(request.verifiedSourceInventory().publication().address().runId())) {
      throw new ApplicationDiscoveryException("APPLICATION_DISCOVERY_EXECUTION_INVALID");
    }
  }
}
