package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryPublicationRequest;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryPublicationSpecifier;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.ApplicationLanguage;
import org.sourceanalysis.app.analysis.discovery.ApplicationProfile;
import org.sourceanalysis.app.analysis.discovery.ApplicationProfileDraftReference;
import org.sourceanalysis.app.analysis.discovery.ApplicationProfileModulePublisher;
import org.sourceanalysis.app.analysis.discovery.HttpEntryDiscovery;
import org.sourceanalysis.app.analysis.discovery.HttpEntryDiscoveryDraftReference;
import org.sourceanalysis.app.analysis.discovery.HttpEntryDiscoveryModulePublisher;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.HttpEntryShardReceipt;
import org.sourceanalysis.app.analysis.discovery.HttpEntrySite;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogDiscovery;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogDraftReference;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogModulePublisher;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogShardReceipt;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogSite;
import org.sourceanalysis.app.analysis.discovery.MapperMethodCandidate;
import org.sourceanalysis.app.analysis.discovery.MapperStatementCandidate;
import org.sourceanalysis.app.analysis.discovery.SignalDisposition;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Test-only persisted graph fixture for Fact M1.
 *
 * <p>The fixture deliberately uses the current discovery and program-graph publishers. It is not a
 * JSON fixture and does not expose graph drafts to the Fact test. Two HTTP handlers each call a
 * different Java interface boundary, so the published data-flow graph has two distinct boundary
 * invocation nodes with disjoint entry ownership.
 */
public final class ProgramGraphsPublicFixture implements AutoCloseable {

  private static final String CONTROLLER_PATH = "src/main/java/com/example/OrderController.java";
  private static final String SYNTHETIC_CONTROLLER_PATH =
      "src/main/java/com/example/SyntheticReplenishmentController.java";
  private static final String MAPPER_PATH = "src/main/java/com/example/OrderMapper.java";
  private static final String MAPPER_XML_PATH = "src/main/resources/mapper/OrderMapper.xml";

  private final RunStoreHandle handle;
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore stepArtifacts;
  private final VerifiedSourceInventoryReference sourceInventory;
  private final ApplicationDiscoveryReference applicationDiscovery;
  private final ProgramGraphsReference programGraphs;
  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalArtifactPolicyRegistry artifactPolicies;
  private final ArtifactControls artifactControls;

  private ProgramGraphsPublicFixture(
      RunStoreHandle handle,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts,
      VerifiedSourceInventoryReference sourceInventory,
      ApplicationDiscoveryReference applicationDiscovery,
      ProgramGraphsReference programGraphs,
      VerifiedSourceTextReader sourceReader,
      CanonicalArtifactPolicyRegistry artifactPolicies,
      ArtifactControls artifactControls) {
    this.handle = handle;
    this.moduleArtifacts = moduleArtifacts;
    this.stepArtifacts = stepArtifacts;
    this.sourceInventory = sourceInventory;
    this.applicationDiscovery = applicationDiscovery;
    this.programGraphs = programGraphs;
    this.sourceReader = sourceReader;
    this.artifactPolicies = artifactPolicies;
    this.artifactControls = artifactControls;
  }

  /** Creates one real canonical source/discovery/graph publication with two entries and bounds. */
  public static ProgramGraphsPublicFixture create(Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, false);
  }

  /** Creates the persisted source/discovery prefix without installing a Step 03 publication. */
  public static ProgramGraphsPublicFixture createForJavaCodeIndex(Path emptyTemporaryDirectory) {
    return create(
        emptyTemporaryDirectory,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false);
  }

  /** Creates the guarded persisted source/discovery prefix without installing Step 03. */
  public static ProgramGraphsPublicFixture createForGuardedJavaCodeIndex(
      Path emptyTemporaryDirectory) {
    return create(
        emptyTemporaryDirectory,
        true,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false);
  }

  /**
   * Creates the same two-entry source with bounded repository scope and no completion eligibility.
   */
  public static ProgramGraphsPublicFixture createWithBoundedPathSet(Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, false, false, false, false, true);
  }

  /** Creates the persisted two-entry fixture with one real Java guard in {@code approve}. */
  public static ProgramGraphsPublicFixture createWithGuardedApprove(Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, true);
  }

  /**
   * Creates the guarded fixture with the important direct call after the normal short-snippet
   * window. It exercises generic source-material selection, not a domain rule.
   */
  public static ProgramGraphsPublicFixture createWithLongGuardedApprove(
      Path emptyTemporaryDirectory) {
    return create(
        emptyTemporaryDirectory, false, false, false, false, false, false, false, false, true);
  }

  /**
   * Creates the persisted two-entry fixture with a natural guarded boundary call in {@code
   * approve}.
   */
  public static ProgramGraphsPublicFixture createWithGuardedElseApprove(
      Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, false, true);
  }

  /** Creates two HTTP roots with one shared Java call-site and an exact target METHOD. */
  public static ProgramGraphsPublicFixture createWithSharedJavaCall(Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, false, false, true);
  }

  /** Creates two HTTP roots where the exact handoff is guarded by a Java condition. */
  public static ProgramGraphsPublicFixture createWithGuardedSharedJavaCall(
      Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, false, false, false, false, false, false, true);
  }

  /** Creates three HTTP roots joined by two exact Java handoffs for M7 partition acceptance. */
  public static ProgramGraphsPublicFixture createWithChainedJavaCalls(
      Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, false, false, false, false, false, true);
  }

  /** Creates a persisted Java repository with no discovered HTTP-entry denominator. */
  public static ProgramGraphsPublicFixture createWithoutHttpEntries(Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, false, false, false, false, false, false, false, true);
  }

  /** Creates a synthetic unit fixture with seven independently declared HTTP entry methods. */
  public static ProgramGraphsPublicFixture createSyntheticReplenishmentToSettlement(
      Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, false, false, false, true);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory, boolean guardedApprove) {
    return create(emptyTemporaryDirectory, guardedApprove, false, false);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory, boolean guardedApprove, boolean guardedElseApprove) {
    return create(emptyTemporaryDirectory, guardedApprove, guardedElseApprove, false);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall) {
    return create(
        emptyTemporaryDirectory, guardedApprove, guardedElseApprove, sharedJavaCall, false);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries) {
    return create(
        emptyTemporaryDirectory,
        guardedApprove,
        guardedElseApprove,
        sharedJavaCall,
        syntheticSevenEntries,
        false);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries,
      boolean boundedPathSet) {
    return create(
        emptyTemporaryDirectory,
        guardedApprove,
        guardedElseApprove,
        sharedJavaCall,
        syntheticSevenEntries,
        boundedPathSet,
        false);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries,
      boolean boundedPathSet,
      boolean chainedJavaCalls) {
    return create(
        emptyTemporaryDirectory,
        guardedApprove,
        guardedElseApprove,
        sharedJavaCall,
        syntheticSevenEntries,
        boundedPathSet,
        chainedJavaCalls,
        false);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries,
      boolean boundedPathSet,
      boolean chainedJavaCalls,
      boolean guardedSharedJavaCall) {
    return create(
        emptyTemporaryDirectory,
        guardedApprove,
        guardedElseApprove,
        sharedJavaCall,
        syntheticSevenEntries,
        boundedPathSet,
        chainedJavaCalls,
        guardedSharedJavaCall,
        false);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries,
      boolean boundedPathSet,
      boolean chainedJavaCalls,
      boolean guardedSharedJavaCall,
      boolean withoutHttpEntries) {
    return create(
        emptyTemporaryDirectory,
        guardedApprove,
        guardedElseApprove,
        sharedJavaCall,
        syntheticSevenEntries,
        boundedPathSet,
        chainedJavaCalls,
        guardedSharedJavaCall,
        withoutHttpEntries,
        false);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries,
      boolean boundedPathSet,
      boolean chainedJavaCalls,
      boolean guardedSharedJavaCall,
      boolean withoutHttpEntries,
      boolean longGuardedApprove) {
    return create(
        emptyTemporaryDirectory,
        guardedApprove,
        guardedElseApprove,
        sharedJavaCall,
        syntheticSevenEntries,
        boundedPathSet,
        chainedJavaCalls,
        guardedSharedJavaCall,
        withoutHttpEntries,
        longGuardedApprove,
        true);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries,
      boolean boundedPathSet,
      boolean chainedJavaCalls,
      boolean guardedSharedJavaCall,
      boolean withoutHttpEntries,
      boolean longGuardedApprove,
      boolean publishLegacyGraphs) {
    createEmptyTestStoreDirectory(emptyTemporaryDirectory);
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    String fixtureKey =
        boundedPathSet
            ? "fact-bounded-path-set"
            : syntheticSevenEntries
                ? "synthetic-seven-entry"
                : chainedJavaCalls
                    ? "fact-three-entry-chain"
                    : guardedSharedJavaCall
                        ? "fact-guarded-shared-java-call"
                        : longGuardedApprove ? "fact-long-guarded-approve" : "fact-two-entry";
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest(fixtureKey + "-run"));
    RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory);
    try {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24));
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24));
      SourceMaterial source =
          source(
              controls,
              guardedApprove,
              guardedElseApprove,
              sharedJavaCall,
              syntheticSevenEntries,
              boundedPathSet,
              chainedJavaCalls,
              guardedSharedJavaCall,
              longGuardedApprove);
      if (withoutHttpEntries) {
        source =
            new SourceMaterial(
                source.verifiedSource(),
                List.of(),
                source.mapperCatalog(),
                source.documents(),
                source.snapshotId(),
                source.fixtureKey() + "-without-http-entries");
      }
      List<CanonicalModulePayload> sourcePayloads = sourcePayloads(canonicalJson);
      source = withPublishedSourceReferences(source, sourcePayloads);
      InstalledModulePublication sourceModule =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 3, "publish"),
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  sourcePayloads));
      var sourceStep =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
                  new AnalysisStepPublisherModuleProvenance(sourceModule.reference()),
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  toStepPayloads(sourcePayloads),
                  null));

      VerifiedSourceInventoryReference sourceReference =
          new VerifiedSourceInventoryReference(sourceStep.reference());
      ApplicationProfile profile = discoveryProfile(source);
      ApplicationProfileDraftReference profileDraft =
          new ApplicationProfileModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.APPLICATION_DISCOVERY, 1, "application-profile"),
                  profile);
      HttpEntryDiscoveryDraftReference entryDraft =
          new HttpEntryDiscoveryModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.APPLICATION_DISCOVERY, 2, "http-entry"),
                  profileDraft,
                  profile,
                  discoveryEntries(source));
      MapperCatalogDraftReference mapperDraft =
          new MapperCatalogModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.APPLICATION_DISCOVERY, 3, "mapper-catalog"),
                  profileDraft,
                  profile,
                  discoveryMappers(source));
      ApplicationDiscoveryReference discoveryReference =
          new ApplicationDiscoveryPublicationSpecifier(modules, steps)
              .publish(
                  new ApplicationDiscoveryPublicationRequest(
                      new AnalysisStepPublicationAddress(
                          runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                      sourceReference,
                      profileDraft,
                      entryDraft,
                      mapperDraft));
      ProgramGraphsReference graphReference = null;
      if (publishLegacyGraphs) {
        ArtifactReference graphProfile = reference("graph-profile", fixtureKey + "-profile");
        graphReference =
            new ProgramGraphsExecution(source.reader(), modules, steps)
                .execute(sourceReference, discoveryReference, graphProfile, controls);
      }
      return new ProgramGraphsPublicFixture(
          handle,
          modules,
          steps,
          sourceReference,
          discoveryReference,
          graphReference,
          source.reader(),
          policies,
          controls);
    } catch (RuntimeException failure) {
      handle.close();
      throw failure;
    }
  }

  private static void createEmptyTestStoreDirectory(Path emptyTemporaryDirectory) {
    try {
      Files.createDirectory(emptyTemporaryDirectory);
    } catch (java.nio.file.FileAlreadyExistsException alreadyExists) {
      // The store bootstrap below verifies that an existing directory is empty and not a symlink.
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("cannot create test store directory", failure);
    }
  }

  public CanonicalAnalysisStepArtifactStore stepArtifacts() {
    return stepArtifacts;
  }

  /** Returns the exact module store that owns the fixture's persisted predecessor modules. */
  public CanonicalModuleArtifactStore moduleArtifacts() {
    return moduleArtifacts;
  }

  public VerifiedSourceInventoryReference sourceInventory() {
    return sourceInventory;
  }

  public ApplicationDiscoveryReference applicationDiscovery() {
    return applicationDiscovery;
  }

  public ProgramGraphsReference programGraphs() {
    return programGraphs;
  }

  public VerifiedSourceTextReader sourceReader() {
    return sourceReader;
  }

  /** Returns the exact policy registry used to publish every fixture predecessor. */
  public CanonicalArtifactPolicyRegistry artifactPolicies() {
    return artifactPolicies;
  }

  /** Creates the complete test-only policy set required by production Step 01–05 executors. */
  public static CanonicalArtifactPolicyRegistry policiesForRuntimeTest(
      CanonicalJsonCodec canonicalJson) {
    return policies(canonicalJson);
  }

  /** Creates controls bound to {@link #policiesForRuntimeTest(CanonicalJsonCodec)}. */
  public static ArtifactControls controlsForRuntimeTest(CanonicalArtifactPolicyRegistry policies) {
    return controls(policies);
  }

  /** Returns the exact controls recorded by the fixture's source/discovery/graph receipts. */
  public ArtifactControls artifactControls() {
    return artifactControls;
  }

  /**
   * Reinstalls a complete public graph publication after a test-only JSON graph mutation.
   *
   * <p>The mutation receives only the five graph payloads, not Fact JSON or graph drafts. Source
   * and discovery are copied into a fresh run so the Fact reader must reopen the mutated public
   * publication through the same canonical stores as production.
   */
  public static PersistedGraphMutation republishMutatedGraphs(
      ProgramGraphsPublicFixture base,
      Path mutationRoot,
      BiFunction<List<CanonicalModulePayload>, CanonicalJsonCodec, List<CanonicalModulePayload>>
          mutation) {
    try {
      Files.createDirectory(mutationRoot);
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = policies(json);
      ArtifactControls controls = controls(policies);
      RunStoreHandle handle = RunStoreBootstrap.openForTest(mutationRoot);
      ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, json, policies, limits);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, json, policies, limits);
      ReopenedAnalysisStepPublication source =
          base.stepArtifacts.reopen(base.sourceInventory.publication());
      ReopenedAnalysisStepPublication discovery =
          base.stepArtifacts.reopen(base.applicationDiscovery.publication());
      ReopenedAnalysisStepPublication graph =
          base.stepArtifacts.reopen(base.programGraphs.publication());
      AnalysisRunId runId =
          AnalysisRunId.parse("analysis-run:" + digest("fact-graph-mutation:" + mutationRoot));
      InstalledAnalysisStepPublication sourceStep =
          copyPublication(source, runId, 3, modules, steps, List.of(), controls);
      InstalledAnalysisStepPublication discoveryStep =
          copyPublication(
              discovery, runId, 4, modules, steps, List.of(sourceStep.reference()), controls);
      List<CanonicalModulePayload> original = modulePayloads(graph.semanticPayloads());
      List<CanonicalModulePayload> changed = List.copyOf(mutation.apply(original, json));
      InstalledModulePublication graphModule =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 6, "publish"),
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  changed));
      InstalledAnalysisStepPublication graphStep =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(runId, AnalysisStepKey.PROGRAM_GRAPHS),
                  new AnalysisStepPublisherModuleProvenance(graphModule.reference()),
                  List.of(sourceStep.reference(), discoveryStep.reference()),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  changed.stream().map(ProgramGraphsPublicFixture::stepPayload).toList(),
                  null));
      return new PersistedGraphMutation(
          handle,
          steps,
          new VerifiedSourceInventoryReference(sourceStep.reference()),
          new ApplicationDiscoveryReference(discoveryStep.reference()),
          new ProgramGraphsReference(graphStep.reference()),
          base.sourceReader);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("cannot create persisted graph mutation fixture", failure);
    }
  }

  /**
   * Reinstalls a complete public discovery and graph publication after a test-only discovery
   * payload mutation.
   *
   * <p>The graph payloads are copied unchanged, but are published downstream of the mutated
   * discovery publication. This keeps the publication roots, controls, schemas, and graph lineage
   * valid while allowing a test to isolate a discovery-to-source reference mismatch.
   */
  public static PersistedGraphMutation republishMutatedDiscovery(
      ProgramGraphsPublicFixture base,
      Path mutationRoot,
      BiFunction<List<CanonicalModulePayload>, CanonicalJsonCodec, List<CanonicalModulePayload>>
          mutation) {
    try {
      Files.createDirectory(mutationRoot);
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = policies(json);
      ArtifactControls controls = controls(policies);
      RunStoreHandle handle = RunStoreBootstrap.openForTest(mutationRoot);
      ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, json, policies, limits);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, json, policies, limits);
      ReopenedAnalysisStepPublication source =
          base.stepArtifacts.reopen(base.sourceInventory.publication());
      ReopenedAnalysisStepPublication discovery =
          base.stepArtifacts.reopen(base.applicationDiscovery.publication());
      ReopenedAnalysisStepPublication graph =
          base.stepArtifacts.reopen(base.programGraphs.publication());
      AnalysisRunId runId =
          AnalysisRunId.parse("analysis-run:" + digest("fact-discovery-mutation:" + mutationRoot));
      InstalledAnalysisStepPublication sourceStep =
          copyPublication(source, runId, 3, modules, steps, List.of(), controls);
      List<CanonicalModulePayload> changedDiscovery =
          List.copyOf(mutation.apply(modulePayloads(discovery.semanticPayloads()), json));
      InstalledAnalysisStepPublication discoveryStep =
          copyPublication(
              runId,
              discovery.reference().address().analysisStepKey(),
              4,
              modules,
              steps,
              List.of(sourceStep.reference()),
              controls,
              changedDiscovery);
      InstalledAnalysisStepPublication graphStep =
          copyPublication(
              graph,
              runId,
              6,
              modules,
              steps,
              List.of(sourceStep.reference(), discoveryStep.reference()),
              controls);
      return new PersistedGraphMutation(
          handle,
          steps,
          new VerifiedSourceInventoryReference(sourceStep.reference()),
          new ApplicationDiscoveryReference(discoveryStep.reference()),
          new ProgramGraphsReference(graphStep.reference()),
          base.sourceReader);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(
          "cannot create persisted discovery mutation fixture", failure);
    }
  }

  /** Rebuilds a standalone graph JSON payload after a test mutation. */
  public static CanonicalModulePayload rebuildStandaloneGraphPayload(
      CanonicalModulePayload original, ObjectNode document, CanonicalJsonCodec json) {
    String prefix =
        original.artifactId().value().substring(0, original.artifactId().value().lastIndexOf(':'));
    ObjectNode withoutId = document.deepCopy();
    withoutId.remove("artifactId");
    String artifactId =
        prefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(original.schemaVersion()),
                    frame(original.artifactType()),
                    frame(json.encodeCanonical(withoutId).copyToByteArray())));
    document.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        original.fileName(),
        original.artifactType(),
        original.schemaVersion(),
        ArtifactId.parse(artifactId),
        original.mediaType(),
        json.encodeCanonical(document));
  }

  /** Returns a new index payload whose graph references match the supplied graph payloads. */
  public static CanonicalModulePayload rebuildGraphIndex(
      CanonicalModulePayload originalIndex,
      List<CanonicalModulePayload> graphPayloads,
      CanonicalJsonCodec json) {
    ObjectNode document = (ObjectNode) json.parseCanonical(originalIndex.canonicalUtf8());
    for (JsonNode descriptor : document.path("graphs")) {
      String fileName = descriptor.path("fileName").asText();
      graphPayloads.stream()
          .filter(payload -> payload.fileName().equals(fileName))
          .findFirst()
          .ifPresent(
              payload -> {
                ObjectNode reference = (ObjectNode) descriptor.path("artifactRef");
                reference.put("artifactId", payload.artifactId().value());
                reference.put("sha256", digest(payload.canonicalUtf8().copyToByteArray()));
              });
    }
    return rebuildStandaloneGraphPayload(originalIndex, document, json);
  }

  private static InstalledAnalysisStepPublication copyPublication(
      ReopenedAnalysisStepPublication original,
      AnalysisRunId runId,
      int moduleNumber,
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      List<org.sourceanalysis.app.artifact.AnalysisStepPublicationReference> upstream,
      ArtifactControls controls) {
    return copyPublication(
        runId,
        original.reference().address().analysisStepKey(),
        moduleNumber,
        modules,
        steps,
        upstream,
        controls,
        modulePayloads(original.semanticPayloads()));
  }

  private static InstalledAnalysisStepPublication copyPublication(
      AnalysisRunId runId,
      AnalysisStepKey stepKey,
      int moduleNumber,
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      List<org.sourceanalysis.app.artifact.AnalysisStepPublicationReference> upstream,
      ArtifactControls controls,
      List<CanonicalModulePayload> payloads) {
    InstalledModulePublication module =
        modules.install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(runId, stepKey, moduleNumber, "publish"),
                "v1",
                List.of(),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                payloads));
    return steps.install(
        new AnalysisStepInstallRequest(
            new AnalysisStepPublicationAddress(runId, stepKey),
            new AnalysisStepPublisherModuleProvenance(module.reference()),
            upstream,
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            List.of(),
            payloads.stream().map(ProgramGraphsPublicFixture::stepPayload).toList(),
            null));
  }

  private static List<CanonicalModulePayload> modulePayloads(
      List<org.sourceanalysis.app.artifact.VerifiedCanonicalPayload> payloads) {
    return payloads.stream()
        .map(
            payload ->
                new CanonicalModulePayload(
                    payload.descriptor().fileName(),
                    payload.descriptor().artifactType(),
                    payload.descriptor().schemaVersion(),
                    payload.descriptor().artifactId(),
                    payload.descriptor().mediaType(),
                    payload.canonicalUtf8()))
        .toList();
  }

  private static CanonicalAnalysisStepPayload stepPayload(CanonicalModulePayload payload) {
    return new CanonicalAnalysisStepPayload(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8());
  }

  /** Handles the fresh store and references produced by {@link #republishMutatedGraphs}. */
  public static final class PersistedGraphMutation implements AutoCloseable {
    private final RunStoreHandle handle;
    private final CanonicalAnalysisStepArtifactStore steps;
    private final VerifiedSourceInventoryReference source;
    private final ApplicationDiscoveryReference discovery;
    private final ProgramGraphsReference graphs;
    private final VerifiedSourceTextReader sourceReader;

    private PersistedGraphMutation(
        RunStoreHandle handle,
        CanonicalAnalysisStepArtifactStore steps,
        VerifiedSourceInventoryReference source,
        ApplicationDiscoveryReference discovery,
        ProgramGraphsReference graphs,
        VerifiedSourceTextReader sourceReader) {
      this.handle = handle;
      this.steps = steps;
      this.source = source;
      this.discovery = discovery;
      this.graphs = graphs;
      this.sourceReader = sourceReader;
    }

    public CanonicalAnalysisStepArtifactStore steps() {
      return steps;
    }

    public VerifiedSourceInventoryReference source() {
      return source;
    }

    public ApplicationDiscoveryReference discovery() {
      return discovery;
    }

    public ProgramGraphsReference graphs() {
      return graphs;
    }

    public VerifiedSourceTextReader sourceReader() {
      return sourceReader;
    }

    @Override
    public void close() {
      handle.close();
    }
  }

  @Override
  public void close() {
    handle.close();
  }

  private static SourceMaterial source(ArtifactControls controls, boolean guardedApprove) {
    return source(controls, guardedApprove, false, false, false);
  }

  private static SourceMaterial source(
      ArtifactControls controls, boolean guardedApprove, boolean guardedElseApprove) {
    return source(controls, guardedApprove, guardedElseApprove, false, false);
  }

  private static SourceMaterial source(
      ArtifactControls controls,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries) {
    return source(
        controls, guardedApprove, guardedElseApprove, sharedJavaCall, syntheticSevenEntries, false);
  }

  private static SourceMaterial source(
      ArtifactControls controls,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries,
      boolean boundedPathSet) {
    return source(
        controls,
        guardedApprove,
        guardedElseApprove,
        sharedJavaCall,
        syntheticSevenEntries,
        boundedPathSet,
        false);
  }

  private static SourceMaterial source(
      ArtifactControls controls,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries,
      boolean boundedPathSet,
      boolean chainedJavaCalls) {
    return source(
        controls,
        guardedApprove,
        guardedElseApprove,
        sharedJavaCall,
        syntheticSevenEntries,
        boundedPathSet,
        chainedJavaCalls,
        false);
  }

  private static SourceMaterial source(
      ArtifactControls controls,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries,
      boolean boundedPathSet,
      boolean chainedJavaCalls,
      boolean guardedSharedJavaCall) {
    return source(
        controls,
        guardedApprove,
        guardedElseApprove,
        sharedJavaCall,
        syntheticSevenEntries,
        boundedPathSet,
        chainedJavaCalls,
        guardedSharedJavaCall,
        false);
  }

  private static SourceMaterial source(
      ArtifactControls controls,
      boolean guardedApprove,
      boolean guardedElseApprove,
      boolean sharedJavaCall,
      boolean syntheticSevenEntries,
      boolean boundedPathSet,
      boolean chainedJavaCalls,
      boolean guardedSharedJavaCall,
      boolean longGuardedApprove) {
    String controller =
        syntheticSevenEntries
            ? """
        package com.example;

        class SyntheticReplenishmentController {
          private final SubmitReplenishmentBoundary submitReplenishmentBoundary = null;
          private final StoreApprovalBoundary storeApprovalBoundary = null;
          private final RegionalPurchaseOrderBoundary regionalPurchaseOrderBoundary = null;
          private final PurchaseOrderExpenseBoundary purchaseOrderExpenseBoundary = null;
          private final ProcurementLogisticsBoundary procurementLogisticsBoundary = null;
          private final InventoryReceiptBoundary inventoryReceiptBoundary = null;
          private final MonthlySettlementBoundary monthlySettlementBoundary = null;

          void submitReplenishment(String status) {
            submitReplenishmentBoundary.submitReplenishment(status);
          }

          void approveAtStore(String status) {
            storeApprovalBoundary.approveAtStore(status);
          }

          void approveRegionAndCreatePurchaseOrder(String status) {
            regionalPurchaseOrderBoundary.approveRegionAndCreatePurchaseOrder(status);
          }

          void approvePurchaseOrderAndProcessExpense(String status) {
            purchaseOrderExpenseBoundary.approvePurchaseOrderAndProcessExpense(status);
          }

          void executePurchaseAndRegisterLogistics(String status) {
            procurementLogisticsBoundary.executePurchaseAndRegisterLogistics(status);
          }

          void receiveAndRegisterInventory(String status) {
            inventoryReceiptBoundary.receiveAndRegisterInventory(status);
          }

          void generateConfirmAndSettleMonthlyBill(String status) {
            monthlySettlementBoundary.generateConfirmAndSettleMonthlyBill(status);
          }
        }

        interface SubmitReplenishmentBoundary {
          void submitReplenishment(String status);
        }

        interface StoreApprovalBoundary {
          void approveAtStore(String status);
        }

        interface RegionalPurchaseOrderBoundary {
          void approveRegionAndCreatePurchaseOrder(String status);
        }

        interface PurchaseOrderExpenseBoundary {
          void approvePurchaseOrderAndProcessExpense(String status);
        }

        interface ProcurementLogisticsBoundary {
          void executePurchaseAndRegisterLogistics(String status);
        }

        interface InventoryReceiptBoundary {
          void receiveAndRegisterInventory(String status);
        }

        interface MonthlySettlementBoundary {
          void generateConfirmAndSettleMonthlyBill(String status);
        }
        """
            : chainedJavaCalls
                ? """
        package com.example;

        class OrderController {
          private final OrderService orderService = new OrderService();

          void approve(String status) {
            orderService.dispatch(status);
          }
        }

        class OrderService {
          private final ApprovalGateway approvalGateway = new ApprovalGateway();

          void dispatch(String status) {
            approvalGateway.record(status);
          }
        }

        class ApprovalGateway {
          private final ApprovalClient approvalClient = null;

          void record(String status) {
            approvalClient.record(status);
          }
        }

        interface ApprovalClient {
          void record(String status);
        }
        """
                : guardedSharedJavaCall
                    ? """
        package com.example;

        class OrderController {
          private final OrderService orderService = new OrderService();

          void approve(String status) {
            if (status == null) {
              return;
            } else {
              orderService.dispatch(status);
            }
          }
        }

        class OrderService {
          private final ApprovalClient approvalClient = null;

          void dispatch(String status) {
            approvalClient.record(status);
          }
        }

        interface ApprovalClient {
          void record(String status);
        }
        """
                    : sharedJavaCall
                        ? """
        package com.example;

        class OrderController {
          private final OrderService orderService = new OrderService();

          void approve(String status) {
            orderService.dispatch(status);
          }
        }

        class OrderService {
          private final ApprovalClient approvalClient = null;

          void dispatch(String status) {
            approvalClient.record(status);
          }
        }

        interface ApprovalClient {
          void record(String status);
        }
        """
                        : guardedElseApprove
                            ? """
        package com.example;

        class OrderController {
          private final OrderService orderService = new OrderService();

          void approve(String status) {
            orderService.approve(status);
          }

          void cancel(String status) {
            orderService.cancel(status);
          }
        }

        class OrderService {
          private final ApprovalClient approvalClient = null;
          private final CancellationClient cancellationClient = null;

          void approve(String status) {
            if (status == null) {
              return;
            } else {
              approvalClient.record(status);
            }
          }

          void cancel(String status) {
            cancellationClient.record(status);
          }
        }

        interface ApprovalClient {
          void record(String status);
        }

        interface CancellationClient {
          void record(String status);
        }
        """
                            : longGuardedApprove
                                ? """
        package com.example;

        class OrderController {
          private final OrderService orderService = new OrderService();

          void approve(String status) {
            orderService.approve(status);
          }

          void cancel(String status) {
            orderService.cancel(status);
          }
        }

        class OrderService {
          private final ApprovalClient approvalClient = null;
          private final AuditClient auditClient = null;
          private final CancellationClient cancellationClient = null;
          private final MetadataClient metadataClient = null;
          private final OrderRecord record = new OrderRecord();
          private final OrderMapper orderMapper = null;

          void approve(String status) {
            if (status == null) {
              return;
            }
            String normalized = status.trim();
            int first = 1;
            int second = first + 1;
            int third = second + 1;
            int fourth = third + 1;
            int fifth = fourth + 1;
            int sixth = fifth + 1;
            int seventh = sixth + 1;
            int eighth = seventh + 1;
            int ninth = eighth + 1;
            int tenth = ninth + 1;
            int eleventh = tenth + 1;
            int twelfth = eleventh + 1;
            int thirteenth = twelfth + 1;
            int fourteenth = thirteenth + 1;
            int fifteenth = fourteenth + 1;
            int sixteenth = fifteenth + 1;
            if (normalized.isEmpty()) {
              return;
            }
            approvalClient.record(normalized);
            metadataClient.lookup(normalized);
            metadataClient.lookupAgain(normalized);
            record.setStatus(normalized);
            orderMapper.update(record);
            auditClient.record(normalized);
          }

          void cancel(String status) {
            cancellationClient.record(status);
          }
        }

        interface ApprovalClient {
          void record(String status);
        }

        interface AuditClient {
          void record(String status);
        }

        interface MetadataClient {
          void lookup(String status);
          void lookupAgain(String status);
        }

        class OrderRecord {
          void setStatus(String status) {}
        }

        interface OrderMapper {
          void update(OrderRecord record);
        }

        interface CancellationClient {
          void record(String status);
        }
        """
                                : guardedApprove
                                    ? """
        package com.example;

        class OrderController {
          private final OrderService orderService = new OrderService();

          void approve(String status) {
            orderService.approve(status);
          }

          void cancel(String status) {
            orderService.cancel(status);
          }
        }

        class OrderService {
          private final ApprovalClient approvalClient = null;
          private final CancellationClient cancellationClient = null;

          void approve(String status) {
            if (status == null) {
              return;
            }
            approvalClient.record(status);
          }

          void cancel(String status) {
            cancellationClient.record(status);
          }
        }

        interface ApprovalClient {
          void record(String status);
        }

        interface CancellationClient {
          void record(String status);
        }
        """
                                    : """
        package com.example;

        class OrderController {
          private final OrderService orderService = new OrderService();

          void approve(String status) {
            %s
            orderService.approve(status);
          }

          void cancel(String status) {
            orderService.cancel(status);
          }
        }

        class OrderService {
          private final ApprovalClient approvalClient = null;
          private final CancellationClient cancellationClient = null;

          void approve(String status) {
            approvalClient.record(status);
          }

          void cancel(String status) {
            cancellationClient.record(status);
          }
        }

        interface ApprovalClient {
          void record(String status);
        }

        interface CancellationClient {
          void record(String status);
        }
        """
                                        .formatted(
                                            guardedApprove
                                                ? "if (status == null) { return; }"
                                                : "");
    String mapper =
        """
        package com.example;

        interface OrderMapper {
          void noop(String status);
        }
        """;
    String mapperXml =
        """
        <?xml version="1.0" encoding="UTF-8" ?>
        <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
        <mapper namespace="com.example.OrderMapper">
          <update id="noop">
            UPDATE orders SET status = #{status}
          </update>
        </mapper>
        """;
    String controllerPath = syntheticSevenEntries ? SYNTHETIC_CONTROLLER_PATH : CONTROLLER_PATH;
    Map<String, String> documents =
        Map.of(controllerPath, controller, MAPPER_PATH, mapper, MAPPER_XML_PATH, mapperXml);
    List<VerifiedSourceTextDocument> verifiedDocuments =
        documents.entrySet().stream()
            .map(entry -> verifiedDocument(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(VerifiedSourceTextDocument::path))
            .toList();
    String fixtureKey =
        boundedPathSet
            ? "fact-bounded-path-set"
            : syntheticSevenEntries
                ? "synthetic-seven-entry"
                : chainedJavaCalls
                    ? "fact-three-entry-chain"
                    : guardedSharedJavaCall ? "fact-guarded-shared-java-call" : "fact-two-entry";
    String snapshotId = "snapshot:" + digest(fixtureKey + "-snapshot");
    VerifiedSourceTextSet verifiedSource =
        new VerifiedSourceTextSet(
            snapshotId,
            boundedPathSet ? "BOUNDED_PATH_SET" : "COMPLETE_CAPTURE",
            !boundedPathSet,
            reference("capability-profile", fixtureKey + "-capability"),
            reference("source-inventory", fixtureKey + "-inventory"),
            reference("verified-snapshot", fixtureKey + "-snapshot"),
            controls,
            verifiedDocuments);
    List<HttpEntryPoint> entries =
        syntheticSevenEntries
            ? List.of(
                syntheticEntry(
                    "submit-replenishment", "submitReplenishment", documents, controllerPath),
                syntheticEntry("approve-at-store", "approveAtStore", documents, controllerPath),
                syntheticEntry(
                    "approve-region-and-create-purchase-order",
                    "approveRegionAndCreatePurchaseOrder",
                    documents,
                    controllerPath),
                syntheticEntry(
                    "approve-purchase-order-and-process-expense",
                    "approvePurchaseOrderAndProcessExpense",
                    documents,
                    controllerPath),
                syntheticEntry(
                    "execute-purchase-and-register-logistics",
                    "executePurchaseAndRegisterLogistics",
                    documents,
                    controllerPath),
                syntheticEntry(
                    "receive-and-register-inventory",
                    "receiveAndRegisterInventory",
                    documents,
                    controllerPath),
                syntheticEntry(
                    "generate-confirm-and-settle-monthly-bill",
                    "generateConfirmAndSettleMonthlyBill",
                    documents,
                    controllerPath))
            : chainedJavaCalls
                ? List.of(
                    entry(
                        "approve",
                        "/orders/approve",
                        "approve",
                        excerpt(documents, controllerPath, "class OrderController"),
                        excerpt(documents, controllerPath, "void approve")),
                    entryForHandler(
                        "dispatch",
                        "/orders/dispatch",
                        "dispatch",
                        "com.example.OrderService#dispatch",
                        excerpt(documents, controllerPath, "class OrderService"),
                        excerpt(documents, controllerPath, "void dispatch")),
                    entryForHandler(
                        "record",
                        "/orders/record",
                        "record",
                        "com.example.ApprovalGateway#record",
                        excerpt(documents, controllerPath, "class ApprovalGateway"),
                        excerpt(documents, controllerPath, "void record")))
                : sharedJavaCall || guardedSharedJavaCall
                    ? List.of(
                        entry(
                            "approve",
                            "/orders/approve",
                            "approve",
                            excerpt(documents, controllerPath, "class OrderController"),
                            excerpt(documents, controllerPath, "void approve")),
                        entryForHandler(
                            "dispatch",
                            "/orders/dispatch",
                            "dispatch",
                            "com.example.OrderService#dispatch",
                            excerpt(documents, controllerPath, "class OrderService"),
                            excerpt(documents, controllerPath, "void dispatch")))
                    : List.of(
                        entry(
                            "approve",
                            "/orders/approve",
                            "approve",
                            excerpt(documents, controllerPath, "class OrderController"),
                            excerpt(documents, controllerPath, "void approve")),
                        entry(
                            "cancel",
                            "/orders/cancel",
                            "cancel",
                            excerpt(documents, controllerPath, "class OrderController"),
                            excerpt(documents, controllerPath, "void cancel")));
    MapperCatalogEntry mapperCatalog =
        new MapperCatalogEntry(
            id("mapper-catalog-entry", "order"),
            "com.example.OrderMapper",
            List.of(
                new MapperMethodCandidate(
                    id("mapper-method", "order-noop"),
                    "noop(java.lang.String)",
                    excerpt(documents, MAPPER_PATH, "void noop(String status);"))),
            MAPPER_XML_PATH,
            "com.example.OrderMapper",
            List.of(
                new MapperStatementCandidate(
                    id("mapper-statement", "order-noop"),
                    "noop",
                    "update",
                    excerpt(documents, MAPPER_XML_PATH, "id=\"noop\""))),
            "CANDIDATE_NOT_YET_BOUND");
    return new SourceMaterial(
        verifiedSource, entries, mapperCatalog, documents, snapshotId, fixtureKey);
  }

  private static VerifiedSourceTextDocument verifiedDocument(String path, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return new VerifiedSourceTextDocument(
        id("file", path),
        path,
        "100644",
        "text/plain",
        bytes.length,
        new Sha256Digest(digest(bytes)),
        ImmutableBytes.copyOf(bytes));
  }

  private static HttpEntryPoint entry(
      String key,
      String route,
      String method,
      SourceExcerptV1 classExcerpt,
      SourceExcerptV1 methodExcerpt) {
    return entryForHandler(
        key, route, method, "com.example.OrderController#" + method, classExcerpt, methodExcerpt);
  }

  private static HttpEntryPoint syntheticEntry(
      String key, String method, Map<String, String> documents, String controllerPath) {
    return entryForHandler(
        key,
        "/synthetic/" + method,
        method,
        "com.example.SyntheticReplenishmentController#" + method,
        List.of("/synthetic", "/" + method),
        List.of("status"),
        excerpt(documents, controllerPath, "class SyntheticReplenishmentController"),
        excerpt(documents, controllerPath, "void " + method));
  }

  private static HttpEntryPoint entryForHandler(
      String key,
      String route,
      String method,
      String handlerFqn,
      SourceExcerptV1 classExcerpt,
      SourceExcerptV1 methodExcerpt) {
    return entryForHandler(
        key,
        route,
        method,
        handlerFqn,
        List.of("/orders", "/" + method),
        List.of("status"),
        classExcerpt,
        methodExcerpt);
  }

  private static HttpEntryPoint entryForHandler(
      String key,
      String route,
      String method,
      String handlerFqn,
      List<String> routeParts,
      List<String> parameterNames,
      SourceExcerptV1 classExcerpt,
      SourceExcerptV1 methodExcerpt) {
    return new HttpEntryPoint(
        id("entry", key),
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        "POST",
        route,
        routeParts,
        handlerFqn,
        "method:" + id("method", key + handlerFqn).value().substring("method:".length()),
        new org.sourceanalysis.app.analysis.code.SourceRange(0, 1, 1, 1),
        parameterNames,
        List.of(classExcerpt, methodExcerpt));
  }

  private static List<CanonicalModulePayload> sourcePayloads(CanonicalJsonCodec json) {
    return List.of(
        standalonePayload(
            json,
            "source-input.json",
            "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
            "verified-source-inventory-source-input-v2",
            "verified-source-inventory-source-input"),
        jsonlPayload(
            json,
            "source-inventory.jsonl",
            "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
            "verified-source-inventory-source-inventory-v2",
            "verified-source-inventory-source-inventory",
            List.of(JsonNodeFactory.instance.objectNode())),
        standalonePayload(
            json,
            "verified-snapshot.json",
            "VERIFIED_SNAPSHOT",
            "verified-snapshot-v2",
            "verified-snapshot"));
  }

  private static SourceMaterial withPublishedSourceReferences(
      SourceMaterial source, List<CanonicalModulePayload> sourcePayloads) {
    VerifiedSourceTextSet previous = source.verifiedSource();
    VerifiedSourceTextSet aligned =
        new VerifiedSourceTextSet(
            previous.snapshotId(),
            previous.inventoryScopeKind(),
            previous.repositoryCompletionEligible(),
            previous.capabilityProfileRef(),
            sourceArtifact(sourcePayloads, "source-inventory.jsonl"),
            sourceArtifact(sourcePayloads, "verified-snapshot.json"),
            previous.controls(),
            previous.documents());
    return new SourceMaterial(
        aligned,
        source.entries(),
        source.mapperCatalog(),
        source.documents(),
        source.snapshotId(),
        source.fixtureKey());
  }

  private static ApplicationProfile discoveryProfile(SourceMaterial source) {
    VerifiedSourceTextSet verifiedSource = source.verifiedSource();
    return new ApplicationProfile(
        id("application-profile", source.fixtureKey()),
        source.snapshotId(),
        verifiedSource.inventoryScopeKind(),
        verifiedSource.repositoryCompletionEligible(),
        ApplicationLanguage.JAVA,
        17,
        List.of(),
        List.of(),
        verifiedSource.capabilityProfileRef(),
        verifiedSource.sourceInventoryRef(),
        verifiedSource.verifiedSnapshotRef(),
        verifiedSource.controls());
  }

  private static HttpEntryDiscovery discoveryEntries(SourceMaterial source) {
    List<HttpEntryPoint> entries =
        source.entries().stream()
            .sorted(Comparator.comparing(entry -> entry.entryId().value()))
            .toList();
    List<HttpEntrySite> sites =
        entries.stream()
            .map(
                entry ->
                    new HttpEntrySite(
                        id("http-entry-site", entry.entryId().value()),
                        entry.routeSourceExcerpts().get(entry.routeSourceExcerpts().size() - 1),
                        List.of(entry.entryId()),
                        SignalDisposition.SUPPORTED,
                        null,
                        null))
            .sorted(Comparator.comparing(site -> site.siteId().value()))
            .toList();
    List<ArtifactId> siteIds = sites.stream().map(HttpEntrySite::siteId).toList();
    return new HttpEntryDiscovery(
        entries,
        sites,
        List.of(
            new HttpEntryShardReceipt(
                id("http-entry-shard", source.fixtureKey()),
                siteIds,
                siteIds,
                "SUCCEEDED",
                List.of())));
  }

  private static MapperCatalogDiscovery discoveryMappers(SourceMaterial source) {
    MapperCatalogEntry entry = source.mapperCatalog();
    SourceExcerptV1 primaryExcerpt =
        entry.javaMethodCandidates().isEmpty()
            ? entry.xmlStatementCandidates().stream().findFirst().orElseThrow().declarationExcerpt()
            : entry.javaMethodCandidates().get(0).declarationExcerpt();
    MapperCatalogSite site =
        new MapperCatalogSite(
            id("mapper-catalog-site", entry.catalogEntryId().value()),
            primaryExcerpt,
            SignalDisposition.SUPPORTED,
            null,
            null);
    ArtifactId siteId = site.siteId();
    return new MapperCatalogDiscovery(
        List.of(entry),
        List.of(site),
        List.of(
            new MapperCatalogShardReceipt(
                id("mapper-catalog-shard", source.fixtureKey()),
                List.of(siteId),
                List.of(siteId),
                "SUCCEEDED",
                List.of())));
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec json, String fileName, String type, String schema, String prefix) {
    return standaloneBody(
        json, fileName, type, schema, prefix, JsonNodeFactory.instance.objectNode());
  }

  private static CanonicalModulePayload standaloneBody(
      CanonicalJsonCodec json,
      String fileName,
      String type,
      String schema,
      String prefix,
      ObjectNode body) {
    ObjectNode withoutId = body.deepCopy();
    withoutId.put("schemaVersion", schema);
    withoutId.put("artifactType", type);
    String artifactId =
        prefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(json.encodeCanonical(withoutId).copyToByteArray())));
    withoutId.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(withoutId));
  }

  private static CanonicalModulePayload jsonlPayload(
      CanonicalJsonCodec json,
      String fileName,
      String type,
      String schema,
      String prefix,
      List<ObjectNode> rawLines) {
    return jsonlBody(json, fileName, type, schema, prefix, rawLines);
  }

  private static CanonicalModulePayload jsonlBody(
      CanonicalJsonCodec json,
      String fileName,
      String type,
      String schema,
      String prefix,
      List<ObjectNode> rawLines) {
    List<ObjectNode> lines = new ArrayList<>();
    for (ObjectNode raw : rawLines) {
      ObjectNode line = raw.deepCopy();
      line.put("schemaVersion", schema);
      line.put("artifactType", type);
      lines.add(line);
    }
    byte[] bytes =
        lines.stream()
            .sorted(Comparator.comparing(value -> value.toString()))
            .map(value -> json.encodeCanonical(value).copyToByteArray())
            .reduce(
                new byte[0],
                (left, right) ->
                    concatenate(concatenate(left, right), "\n".getBytes(StandardCharsets.UTF_8)));
    String artifactId =
        prefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(bytes)));
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private static ArtifactReference sourceArtifact(
      List<CanonicalModulePayload> payloads, String fileName) {
    CanonicalModulePayload payload =
        payloads.stream()
            .filter(value -> value.fileName().equals(fileName))
            .findFirst()
            .orElseThrow();
    return new ArtifactReference(
        payload.artifactId(), new Sha256Digest(digest(payload.canonicalUtf8().copyToByteArray())));
  }

  private static List<CanonicalAnalysisStepPayload> toStepPayloads(
      List<CanonicalModulePayload> payloads) {
    return payloads.stream()
        .map(
            payload ->
                new CanonicalAnalysisStepPayload(
                    payload.fileName(),
                    payload.artifactType(),
                    payload.schemaVersion(),
                    payload.artifactId(),
                    payload.mediaType(),
                    payload.canonicalUtf8()))
        .toList();
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec json) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode entries = document.putArray("policies");
    policy(
        entries,
        "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
        "application-discovery-application-profile-v2",
        "application-profile",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT",
        "application-discovery-application-profile-draft-v2",
        "application-profile",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
        "application-discovery-capability-report-v2",
        "capability-report",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "APPLICATION_DISCOVERY_ENTRY_POINTS",
        "application-discovery-entry-points-v3",
        "entry-points",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "APPLICATION_DISCOVERY_HTTP_ENTRY_DISCOVERY",
        "application-discovery-http-entry-discovery-v3",
        "http-entry-discovery",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "APPLICATION_DISCOVERY_MAPPER_CATALOG",
        "application-discovery-mapper-catalog-v2",
        "mapper-catalog",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        false);
    policy(
        entries,
        "APPLICATION_DISCOVERY_MAPPER_CATALOG_DRAFT",
        "application-discovery-mapper-catalog-draft-v2",
        "mapper-catalog",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CALL_GRAPH",
        "program-graphs-call-graph-v1",
        "program-graphs-call-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT",
        CallGraphDraft.SCHEMA_VERSION,
        "call-graph",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH",
        "program-graphs-code-structure-graph-v1",
        "program-graphs-code-structure-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT",
        CodeStructureGraphDraft.SCHEMA_VERSION,
        "code-structure-graph",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH",
        "program-graphs-control-flow-graph-v2",
        "program-graphs-control-flow-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT",
        ControlFlowGraphDraft.SCHEMA_VERSION,
        "control-flow-graph",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_DATA_FLOW_GRAPH",
        "program-graphs-data-flow-graph-v2",
        "program-graphs-data-flow-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_DATA_FLOW_DRAFT",
        DataFlowGraphDraft.SCHEMA_VERSION,
        "data-flow-graph",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_EVIDENCE_GRAPH",
        "program-graphs-evidence-graph-v3",
        "program-graphs-evidence-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT",
        EvidenceGraphDraft.SCHEMA_VERSION,
        "evidence-graph",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_GRAPH_GAP",
        "program-graphs-graph-gap-v1",
        "program-graphs-graph-gaps",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "PROGRAM_GRAPHS_GRAPH_INDEX",
        "program-graphs-graph-index-v2",
        "program-graphs-graph-index",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_JAVA_CODE_INDEX",
        "java-code-index-v2",
        "java-code-index",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        false);
    policy(
        entries,
        "PROVEN_CODE_FACTS_FACT_CANDIDATE_SET",
        "proven-code-facts-fact-candidate-set-v3",
        "proven-code-facts-fact-candidate-set",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROVEN_CODE_FACTS_FACT_ACCOUNTING",
        "proven-code-facts-fact-accounting-v3",
        "proven-code-facts-fact-accounting",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROVEN_CODE_FACTS_FACT_ACCOUNTING",
        "proven-code-facts-fact-accounting-v4",
        "proven-code-facts-fact-accounting",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROVEN_CODE_FACTS_GAP_LEDGER",
        "proven-code-facts-gap-ledger-v3",
        "proven-code-facts-gap-ledger",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROVEN_CODE_FACTS_PROOF_PACK",
        "proven-code-facts-proof-pack-v3",
        "proven-code-facts-proof-pack",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROVEN_CODE_FACTS_PROOF_DECISION_SET",
        "proven-code-facts-proof-decision-set-v3",
        "proven-code-facts-proof-decision-set",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROVEN_CODE_FACTS_PROVEN_FACTS",
        "proven-code-facts-proven-facts-v3",
        "proven-code-facts-proven-facts",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "BUSINESS_FLOWS_FLOW_COMPILATION",
        "business-flows-flow-compilation-v6",
        "business-flows-flow-compilation",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "BUSINESS_FLOWS_CAPSULE_PROJECTION",
        "business-flows-capsule-projection-v11",
        "business-flows-capsule-projection",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "BUSINESS_FLOWS_FLOW_SLICES",
        "business-flows-flow-slices-v6",
        "business-flows-flow-slices",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "BUSINESS_FLOWS_FLOW_COVERAGE",
        "business-flows-flow-coverage-v2",
        "business-flows-flow-coverage",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "BUSINESS_FLOWS_ENTRY_DISPOSITION",
        "business-flows-entry-disposition-v2",
        "business-flows-entry-disposition",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "BUSINESS_FLOWS_EVIDENCE_CAPSULE",
        "business-flows-evidence-capsule-v9",
        "business-flows-evidence-capsule",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "BUSINESS_FLOWS_FLOW_GAP",
        "business-flows-flow-gap-v2",
        "business-flows-flow-gap",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "FLOW_INTERPRETATION_BUSINESS_MATERIAL",
        "flow-interpretation-business-material-v1",
        "business-materials",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "FLOW_INTERPRETATION_ACTIVITY_COVERAGE",
        "flow-interpretation-activity-coverage-v2",
        "activity-coverage",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "FLOW_INTERPRETATION_ACTIVITY_EXPLANATIONS",
        "flow-interpretation-activity-explanations-v1",
        "activity-explanations",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESSES",
        "repository-knowledge-business-processes-v1",
        "business-processes",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "REPOSITORY_KNOWLEDGE_PROCESS_COVERAGE",
        "repository-knowledge-process-coverage-v2",
        "process-coverage",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "REPOSITORY_KNOWLEDGE_BUSINESS_KNOWLEDGE",
        "repository-knowledge-business-knowledge-v2",
        "repository-business-knowledge",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "BUSINESS_DOCUMENT_REPORT",
        "business-document-report-v1",
        "business-report",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "BUSINESS_DOCUMENT_MARKDOWN",
        "business-document-markdown-v2",
        "business-document-markdown",
        "text/markdown",
        "RAW_UTF8",
        false);
    policy(
        entries,
        "BUSINESS_DOCUMENT_VALIDATION",
        "business-document-validation-v1",
        "report-validation",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "BUSINESS_DOCUMENT_SOURCE_REFERENCES",
        "business-document-source-references-v1",
        "source-refs",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "VERIFIED_SNAPSHOT",
        "verified-snapshot-v2",
        "verified-snapshot",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
        "verified-source-inventory-source-input-v2",
        "verified-source-inventory-source-input",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
        "verified-source-inventory-source-inventory-v2",
        "verified-source-inventory-source-inventory",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        false);
    List<ObjectNode> ordered = new ArrayList<>();
    entries.forEach(value -> ordered.add((ObjectNode) value));
    ordered.sort(Comparator.comparing(value -> value.get("artifactType").textValue()));
    entries.removeAll();
    ordered.forEach(entries::add);
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + digest(
                concatenate(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(json.encodeCanonical(document).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(json.encodeCanonical(document), json);
  }

  private static void policy(
      ArrayNode entries,
      String type,
      String schema,
      String prefix,
      String media,
      String envelope,
      boolean emptyJsonl) {
    entries
        .addObject()
        .put("artifactType", type)
        .put("schemaVersion", schema)
        .put("artifactIdPrefix", prefix)
        .put("mediaType", media)
        .put("envelopeKind", envelope)
        .put("emptyJsonlAllowed", emptyJsonl)
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        new Sha256Digest(digest("toolchain")),
        new Sha256Digest(digest("profile")),
        new Sha256Digest(digest("schema")),
        null,
        policies.reference());
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest(value)), new Sha256Digest(digest(value)));
  }

  private static SourceExcerptV1 excerpt(Map<String, String> documents, String path, String token) {
    String source = documents.get(path);
    int startCharacter = source.indexOf(token);
    if (startCharacter < 0) throw new IllegalArgumentException("fixture token is absent: " + token);
    long startByte = source.substring(0, startCharacter).getBytes(StandardCharsets.UTF_8).length;
    int startLine =
        1
            + (int)
                source
                    .substring(0, startCharacter)
                    .chars()
                    .filter(character -> character == '\n')
                    .count();
    int lineStart = source.lastIndexOf('\n', startCharacter - 1) + 1;
    int startColumn = startCharacter - lineStart + 1;
    byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
    return new SourceExcerptV1(
        new SourceLocatorV1(
            id("file", path),
            path,
            startByte,
            startByte + bytes.length,
            startLine,
            startColumn,
            startLine,
            startColumn + token.length()),
        ImmutableBytes.copyOf(bytes),
        new Sha256Digest(digest(bytes)));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) length += value.length;
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static ArtifactId id(String prefix, String value) {
    return ArtifactId.parse(prefix + ":" + digest(value));
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record SourceMaterial(
      VerifiedSourceTextSet verifiedSource,
      List<HttpEntryPoint> entries,
      MapperCatalogEntry mapperCatalog,
      Map<String, String> documents,
      String snapshotId,
      String fixtureKey) {

    private SourceMaterial {
      entries = List.copyOf(entries);
      documents = Map.copyOf(documents);
    }

    private VerifiedSourceTextReader reader() {
      return ignored -> verifiedSource;
    }
  }
}
