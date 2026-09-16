package org.sourceanalysis.app.analysis.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryExecutor;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryRequest;
import org.sourceanalysis.app.analysis.discovery.DiscoveryProfile;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.fact.proofs.AtomicProofBuilder;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSet;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.proofs.ProofRuleRegistry;
import org.sourceanalysis.app.analysis.fact.publish.FactLedgerPublicationSpecifier;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjection;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionModulePublisher;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.capsule.EvidenceCapsuleProjector;
import org.sourceanalysis.app.analysis.flow.compiler.EntryRootedFlowCompiler;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.publish.FlowPublicationSpecifier;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequest;
import org.sourceanalysis.app.capture.localgit.LocalGitCommitCaptureAdapter;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceFile;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;

/** Opt-in, real-filesystem Step01→05 acceptance over the one approved jshERP commit. */
class FixedRepositoryBusinessFlowsIT {

  private static final String ACCEPTANCE_PROPERTY = "sourceanalysis.fixedRepositoryAcceptance";
  private static final String SOURCE_PROPERTY = "sourceanalysis.fixedRepositoryPath";
  private static final String WORKSPACE_PROPERTY = "sourceanalysis.fixedRepositoryWorkspace";
  private static final String ORIGIN = "https://github.com/jishenghua/jshERP.git";
  private static final String COMMIT = "8c30ce7861570458920175e200bb2a6442713580";
  private static final String CONFIG_RESOURCE =
      "/analysis/flow/fixed-repository/fixed-repository-acceptance-config.json";
  private static final Set<String> REQUIRED_POLICIES =
      Set.of(
          "APPLICATION_DISCOVERY_APPLICATION_PROFILE|application-discovery-application-profile-v2",
          "APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT|application-discovery-application-profile-draft-v2",
          "APPLICATION_DISCOVERY_CAPABILITY_REPORT|application-discovery-capability-report-v2",
          "APPLICATION_DISCOVERY_ENTRY_POINTS|application-discovery-entry-points-v3",
          "APPLICATION_DISCOVERY_HTTP_ENTRY_DISCOVERY|application-discovery-http-entry-discovery-v3",
          "APPLICATION_DISCOVERY_MAPPER_CATALOG|application-discovery-mapper-catalog-v2",
          "APPLICATION_DISCOVERY_MAPPER_CATALOG_DRAFT|application-discovery-mapper-catalog-draft-v2",
          "BUSINESS_FLOWS_CAPSULE_PROJECTION|business-flows-capsule-projection-v11",
          "BUSINESS_FLOWS_EVIDENCE_CAPSULE|business-flows-evidence-capsule-v9",
          "BUSINESS_FLOWS_ENTRY_DISPOSITION|business-flows-entry-disposition-v2",
          "BUSINESS_FLOWS_FLOW_COMPILATION|business-flows-flow-compilation-v6",
          "BUSINESS_FLOWS_FLOW_COVERAGE|business-flows-flow-coverage-v2",
          "BUSINESS_FLOWS_FLOW_GAP|business-flows-flow-gap-v2",
          "BUSINESS_FLOWS_FLOW_SLICES|business-flows-flow-slices-v6",
          "FLOW_INTERPRETATION_BUSINESS_MATERIAL|flow-interpretation-business-material-v1",
          "PROGRAM_GRAPHS_CALL_GRAPH|program-graphs-call-graph-v1",
          "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT|program-graphs-call-graph-draft-v3",
          "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT|program-graphs-code-structure-draft-v3",
          "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH|program-graphs-code-structure-graph-v1",
          "PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT|program-graphs-control-flow-draft-v4",
          "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH|program-graphs-control-flow-graph-v2",
          "PROGRAM_GRAPHS_DATA_FLOW_DRAFT|program-graphs-data-flow-draft-v3",
          "PROGRAM_GRAPHS_DATA_FLOW_GRAPH|program-graphs-data-flow-graph-v2",
          "PROGRAM_GRAPHS_EVIDENCE_GRAPH|program-graphs-evidence-graph-v3",
          "PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT|program-graphs-evidence-graph-draft-v3",
          "PROGRAM_GRAPHS_GRAPH_GAP|program-graphs-graph-gap-v1",
          "PROGRAM_GRAPHS_GRAPH_INDEX|program-graphs-graph-index-v2",
          "PROVEN_CODE_FACTS_FACT_ACCOUNTING|proven-code-facts-fact-accounting-v3",
          "PROVEN_CODE_FACTS_FACT_CANDIDATE_SET|proven-code-facts-fact-candidate-set-v3",
          "PROVEN_CODE_FACTS_GAP_LEDGER|proven-code-facts-gap-ledger-v3",
          "PROVEN_CODE_FACTS_PROOF_DECISION_SET|proven-code-facts-proof-decision-set-v3",
          "PROVEN_CODE_FACTS_PROOF_PACK|proven-code-facts-proof-pack-v3",
          "PROVEN_CODE_FACTS_PROVEN_FACTS|proven-code-facts-proven-facts-v3",
          "VERIFIED_SNAPSHOT|verified-snapshot-v2",
          "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST|verified-source-inventory-admitted-source-request-v2",
          "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT|verified-source-inventory-source-input-v2",
          "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY|verified-source-inventory-source-inventory-v2",
          "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX|verified-source-inventory-verified-source-index-v2");

  @Test
  void loadsAndValidatesFrozenAcceptanceOracleBeforeAnySourceRead() throws Exception {
    AcceptanceConfig config = AcceptanceConfig.load();
    assertThat(config.policies.reference().artifactId().value())
        .startsWith("artifact-policy-registry:");
    assertThat(config.discoveryProfile.ruleVersion()).isEqualTo("application-discovery-v2");
    assertThat(
            config
                .policies
                .resolve(
                    new ArtifactPolicyKey(
                        "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST",
                        "verified-source-inventory-admitted-source-request-v2"))
                .artifactIdPrefix())
        .isEqualTo("source-request");
    assertThat(
            config
                .policies
                .resolve(
                    new ArtifactPolicyKey(
                        "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
                        "application-discovery-application-profile-v2"))
                .artifactIdPrefix())
        .isEqualTo("application-discovery-application-profile");
    assertThat(
            config
                .policies
                .resolve(
                    new ArtifactPolicyKey(
                        "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
                        "application-discovery-capability-report-v2"))
                .artifactIdPrefix())
        .isEqualTo("application-discovery-capability-report");
    assertThat(
            config
                .policies
                .resolve(
                    new ArtifactPolicyKey(
                        "APPLICATION_DISCOVERY_ENTRY_POINTS",
                        "application-discovery-entry-points-v3"))
                .artifactIdPrefix())
        .isEqualTo("application-discovery-entry-points");
    assertThat(
            config
                .policies
                .resolve(
                    new ArtifactPolicyKey(
                        "APPLICATION_DISCOVERY_MAPPER_CATALOG",
                        "application-discovery-mapper-catalog-v2"))
                .artifactIdPrefix())
        .isEqualTo("application-discovery-mapper-catalog");
    assertThat(
            config
                .policies
                .resolve(
                    new ArtifactPolicyKey(
                        "FLOW_INTERPRETATION_BUSINESS_MATERIAL",
                        "flow-interpretation-business-material-v1"))
                .artifactIdPrefix())
        .isEqualTo("business-materials");
    assertThat(config.factRegistry.templates()).hasSize(3);
    assertThat(config.proofRules.allowances()).hasSize(8);
  }

  @Test
  void runsTheFrozenStep01Through05ChainOnlyWhenExplicitlyOptedIn() throws Exception {
    assertThat(System.getProperty(ACCEPTANCE_PROPERTY))
        .as("direct selection requires explicit fixed-repository opt-in")
        .isEqualTo("true");
    AcceptanceConfig config = AcceptanceConfig.load();
    Path sourcePath = requiredAbsolutePath(SOURCE_PROPERTY);
    Path workspace = requiredWorkspace();
    String status = "FAIL";
    List<StageEvidence> evidence = new ArrayList<>();
    RegisteredSourceCapture capture = null;
    ReopenedAnalysisStepPublication publication = null;
    SourceAccounting accounting = null;
    String currentStage = null;
    try {
      SourceRegistrationReference registration =
          new LocalGitCommitCaptureAdapter(workspace.resolve("capture"), Path.of("/usr/bin/git"))
              .capture(
                  new LocalGitCaptureRequest(
                      ORIGIN,
                      COMMIT,
                      sourcePath,
                      config.capturePolicy.ref(),
                      config.resourceBudget.ref()));
      LocalGitSourceRegistry registry = new LocalGitSourceRegistry(workspace.resolve("capture"));
      capture = registry.reopen(registration.sourceRegistrationId());
      assertThat(capture.commitId()).isEqualTo(COMMIT);
      assertThat(capture.declaredRepositoryIdentity()).isEqualTo(ORIGIN);

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      InputArtifacts inputArtifacts = inputArtifacts(canonicalJson, config, capture, registration);
      CaptureReceiptView receipt = captureReceipt(capture, inputArtifacts.frozenRequest());
      AdmittedSourceRequest admitted =
          new FrozenRequestAdmission()
              .admit(
                  inputArtifacts.runRequestBytes().copyToByteArray(),
                  receipt,
                  new ProfileView(
                      config.profileBundle.ref(),
                      config.resourceBudget.ref(),
                      config.inventoryMaxFiles,
                      config.inventoryMaxBytes));

      Path storeDirectory = workspace.resolve("stores");
      Files.createDirectories(storeDirectory);
      try (RunStoreHandle handle = RunStoreBootstrap.openForTest(storeDirectory)) {
        CanonicalModuleArtifactStore modules =
            new FileSystemCanonicalModuleArtifactStore(
                handle, canonicalJson, config.policies, config.storeLimits);
        CanonicalAnalysisStepArtifactStore steps =
            new FileSystemCanonicalAnalysisStepArtifactStore(
                handle, canonicalJson, config.policies, config.storeLimits);
        currentStage = "inventory-M1-admitted-source-request";
        markAttempt(evidence, currentStage);
        ModulePublicationReference inventoryM1 =
            new AdmittedSourceRequestModulePublisher(modules)
                .publish(
                    new AdmittedSourceRequestPublicationInput(
                        inputArtifacts.runId,
                        inputArtifacts.runRequest,
                        capture.sourceRegistrationRef(),
                        config.verificationPolicy.ref(),
                        config.capabilityProfile.ref(),
                        sorted(
                            List.of(
                                inputArtifacts.runRequest,
                                capture.sourceRegistrationRef(),
                                inputArtifacts.frozenRequest,
                                capture.captureReceiptRef(),
                                capture.snapshotManifestRef(),
                                config.verificationPolicy.ref(),
                                config.capabilityProfile.ref(),
                                config.resourceBudget.ref())),
                        admitted));
        recordModule(modules, "inventory-M1-admitted-source-request", inventoryM1, evidence);
        currentStage = "inventory-M2-verified-source-index";
        markAttempt(evidence, currentStage);
        ModulePublicationReference inventoryM2 =
            new VerifiedSourceIndexModulePublisher(modules).publish(inventoryM1, registry);
        recordModule(modules, "inventory-M2-verified-source-index", inventoryM2, evidence);
        currentStage = "inventory-M3-verified-source-inventory";
        markAttempt(evidence, currentStage);
        VerifiedSourceInventoryReference inventory =
            new VerifiedSourceInventoryPublicationSpecifier(modules, steps, inputArtifacts::reopen)
                .publish(
                    new VerifiedSourceInventoryPublicationSpecificationInputV1(
                        new AnalysisStepPublicationAddress(
                            inputArtifacts.runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
                        inventoryM1,
                        inventoryM2,
                        inputArtifacts.runRequest,
                        inputArtifacts.frozenRequest));
        recordStep(
            steps, "inventory-M3-verified-source-inventory", inventory.publication(), evidence);
        accounting = verifyInventorySnapshot(steps.reopen(inventory.publication()), canonicalJson);
        VerifiedSourceTextReader sourceReader =
            new PersistedVerifiedSourceTextReader(steps, registry);

        currentStage = "discovery-M4-application-discovery";
        markAttempt(evidence, currentStage);
        ApplicationDiscoveryReference discovery =
            new ApplicationDiscoveryExecutor(sourceReader, modules, steps)
                .execute(
                    new ApplicationDiscoveryRequest(
                        new AnalysisStepPublicationAddress(
                            inputArtifacts.runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                        inventory,
                        config.discoveryProfile));
        recordStep(steps, "discovery-M4-application-discovery", discovery.publication(), evidence);
        ReopenedAnalysisStepPublication discoveryPublication =
            steps.reopen(discovery.publication());
        Set<String> discoveryEntryIds = discoveryEntryIds(discoveryPublication, canonicalJson);
        currentStage = "graphs-M6-program-graphs";
        markAttempt(evidence, currentStage);
        ProgramGraphsReference graphs =
            new ProgramGraphsExecution(sourceReader, modules, steps)
                .execute(inventory, discovery, config.graphProfile.ref(), config.controls);
        recordStep(steps, "graphs-M6-program-graphs", graphs.publication(), evidence);

        currentStage = "facts-M1-candidates";
        markAttempt(evidence, currentStage);
        FactCandidateInputs factInputs =
            new PersistedFactCandidateInputReader(steps, sourceReader)
                .reopen(inventory, discovery, graphs);
        FactCandidateSet candidates =
            new FactCandidateEnumerator().enumerate(factInputs, config.factRegistry);
        ModulePublicationReference candidatePublication =
            new FactCandidateSetModulePublisher(modules)
                .publish(
                    new AnalysisStepModuleAddress(
                        inputArtifacts.runId, AnalysisStepKey.PROVEN_CODE_FACTS, 1, "candidates"),
                    factInputs,
                    candidates);
        recordModule(modules, "facts-M1-candidates", candidatePublication, evidence);
        currentStage = "facts-M2-proofs";
        markAttempt(evidence, currentStage);
        ProofDecisionSet decisions =
            new AtomicProofBuilder(sourceReader)
                .prove(candidates, factInputs, inventory, config.proofRules);
        ModulePublicationReference proofPublication =
            new ProofDecisionSetModulePublisher(modules)
                .publish(
                    new AnalysisStepModuleAddress(
                        inputArtifacts.runId, AnalysisStepKey.PROVEN_CODE_FACTS, 2, "proofs"),
                    factInputs,
                    candidatePublication,
                    decisions);
        recordModule(modules, "facts-M2-proofs", proofPublication, evidence);
        currentStage = "facts-M3-proven-code-facts";
        markAttempt(evidence, currentStage);
        ProvenCodeFactsReference facts =
            new FactLedgerPublicationSpecifier(modules, steps)
                .specifyCandidatesAndProofs(
                    factInputs,
                    candidatePublication,
                    proofPublication,
                    inventory,
                    discovery,
                    graphs);
        recordStep(steps, "facts-M3-proven-code-facts", facts.publication(), evidence);
        verifyFactAccounting(steps.reopen(facts.publication()), canonicalJson);

        currentStage = "flows-M1-compilation";
        markAttempt(evidence, currentStage);
        FlowCompilation compilation =
            new EntryRootedFlowCompiler(steps)
                .compile(discovery, graphs, facts, config.flowProfileTyped);
        ModulePublicationReference flowCompilation =
            new FlowCompilationModulePublisher(modules, steps)
                .publish(discovery, graphs, facts, compilation);
        recordModule(modules, "flows-M1-compilation", flowCompilation, evidence);
        currentStage = "flows-M2-capsule-projection";
        markAttempt(evidence, currentStage);
        CapsuleProjection projection =
            new EvidenceCapsuleProjector(modules, steps, sourceReader)
                .project(flowCompilation, inventory, graphs, facts, config.capsuleProfile);
        ModulePublicationReference capsuleProjection =
            new CapsuleProjectionModulePublisher(modules, steps, sourceReader)
                .publish(flowCompilation, inventory, graphs, facts, projection);
        recordModule(modules, "flows-M2-capsule-projection", capsuleProjection, evidence);
        currentStage = "flows-M3-business-flows";
        markAttempt(evidence, currentStage);
        BusinessFlowsReference businessFlows =
            new FlowPublicationSpecifier(modules, steps, sourceReader)
                .specify(inventory, discovery, graphs, facts, flowCompilation, capsuleProjection);

        publication = steps.reopen(businessFlows.publication());
        recordStep(steps, "flows-M3-business-flows", businessFlows.publication(), evidence);
        assertThat(publication.semanticPayloads())
            .extracting(payload -> payload.descriptor().fileName())
            .containsExactlyInAnyOrder(
                "flow-coverage.json",
                "flow-slices.json",
                "entry-dispositions.jsonl",
                "evidence-capsules.jsonl",
                "flow-gaps.jsonl");
        assertThat(publication.receipt().status())
            .isIn(ModuleCompletionStatus.SUCCEEDED, ModuleCompletionStatus.SUCCEEDED_WITH_GAPS);
        verifyBusinessFlowClosure(publication, discoveryEntryIds, canonicalJson);

        currentStage = "interpretation-M1-business-materials";
        markAttempt(evidence, currentStage);
        BusinessMaterialBuildResult materials =
            new BusinessMaterialBuilder(modules, steps, sourceReader)
                .build(
                    new BuildBusinessMaterialsRequest(
                        businessFlows, new BusinessMaterialProfile(24, 80, 48_000)));
        recordModule(
            modules, "interpretation-M1-business-materials", materials.checkpoint(), evidence);
        assertThat(materials.materialSet().entryCoverage())
            .hasSize(discoveryEntryIds.size())
            .allSatisfy(
                coverage ->
                    assertThat(coverage.disposition())
                        .isIn("ANALYZED_MATERIAL", "MATERIAL_WITH_GAPS", "NOT_MATERIALIZED"));
        assertThat(materials.materialSet().materials())
            .as("a nonempty fixed repository must produce inspectable model-reading material")
            .isNotEmpty();
        status =
            publication.receipt().status() == ModuleCompletionStatus.SUCCEEDED ? "COMPLETE" : "GAP";
        writeReport(workspace, status, capture, publication, evidence, accounting, canonicalJson);
      }
    } catch (Throwable failure) {
      if (currentStage != null) markFailure(evidence, currentStage);
      writeReportSafely(
          workspace,
          status,
          failure,
          capture,
          publication,
          evidence,
          accounting,
          new CanonicalJsonCodec());
      throw failure;
    }
  }

  private static Path requiredAbsolutePath(String property) {
    String value = System.getProperty(property);
    assertThat(value).as(property + " must be supplied").isNotBlank();
    Path path = Path.of(value);
    assertThat(path.isAbsolute()).as(property + " must be absolute").isTrue();
    return path.normalize();
  }

  private static Path requiredWorkspace() throws IOException {
    Path workspace = requiredAbsolutePath(WORKSPACE_PROPERTY);
    Path moduleRoot = Path.of("").toAbsolutePath().normalize();
    Path ignoredRoot = moduleRoot.resolve(".workspace").normalize();
    assertThat(workspace)
        .as("workspace must be a child of this module's ignored .workspace")
        .startsWith(ignoredRoot);
    assertThat(workspace).isNotEqualTo(ignoredRoot);
    Path ignoreFile = moduleRoot.resolve(".gitignore");
    assertThat(Files.readString(ignoreFile)).containsPattern("(?m)^\\s*\\.workspace/?\\s*$");
    for (Path ancestor = workspace;
        ancestor != null && ancestor.startsWith(moduleRoot);
        ancestor = ancestor.getParent()) {
      assertThat(Files.isSymbolicLink(ancestor))
          .as("workspace ancestor must not be a symbolic link: " + ancestor)
          .isFalse();
      if (ancestor.equals(moduleRoot)) break;
    }
    if (Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
      assertThat(Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)).isTrue();
      assertThat(Files.isSymbolicLink(workspace)).isFalse();
      try (var entries = Files.list(workspace)) {
        assertThat(entries.findAny()).as("workspace must start empty").isEmpty();
      }
    } else {
      Files.createDirectories(workspace);
    }
    return workspace;
  }

  private static CaptureReceiptView captureReceipt(
      RegisteredSourceCapture capture, ArtifactReference frozenRequest) {
    List<CapturedRegularFile> files =
        capture.manifestEntries().stream()
            .map(FixedRepositoryBusinessFlowsIT::capturedFile)
            .sorted(Comparator.comparing(CapturedRegularFile::path))
            .toList();
    return new CaptureReceiptView(
        capture.sourceRegistrationRef().artifactId(),
        capture.declaredRepositoryIdentity(),
        capture.commitId(),
        frozenRequest,
        capture.captureReceiptRef(),
        capture.snapshotManifestRef(),
        InventoryScope.completeCapture(),
        true,
        files);
  }

  private static CapturedRegularFile capturedFile(RegisteredSourceFile file) {
    return new CapturedRegularFile(
        fileId(file.path(), file.gitMode(), file.sizeBytes(), file.sha256()),
        file.path(),
        file.gitMode(),
        file.mediaType(),
        file.sizeBytes(),
        file.sha256(),
        SourceAnalysisDisposition.valueOf(file.analysisDisposition()),
        file.textEncoding());
  }

  private static ArtifactId fileId(
      String path, String gitMode, long sizeBytes, Sha256Digest sha256) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("gitMode", gitMode);
    material.put("path", path);
    material.put("sha256", sha256.value());
    material.put("sizeBytes", sizeBytes);
    return ArtifactId.parse(
        "file:"
            + sha256(
                concatenate(
                    frame("verified-source-file-id-v1"),
                    frame(new CanonicalJsonCodec().encodeCanonical(material).copyToByteArray()))));
  }

  private static InputArtifacts inputArtifacts(
      CanonicalJsonCodec json,
      AcceptanceConfig config,
      RegisteredSourceCapture capture,
      SourceRegistrationReference registration) {
    ObjectNode frozen = JsonNodeFactory.instance.objectNode();
    frozen
        .putObject("expectedOrigin")
        .put("kind", "GIT_SHA1_COMMIT")
        .put("repositoryUrl", ORIGIN)
        .put("revision40", COMMIT);
    frozen.set("captureReceiptRef", referenceNode(capture.captureReceiptRef()));
    frozen.set("snapshotManifestRef", referenceNode(capture.snapshotManifestRef()));
    frozen.putObject("inventoryScope").put("kind", "COMPLETE_CAPTURE").putNull("scopeRoot");
    frozen.with("inventoryScope").put("declaredPathCount", capture.manifestEntries().size());
    frozen.put("schemaVersion", "frozen-repository-request-v2");
    frozen.set("verificationPolicyRef", referenceNode(config.verificationPolicy.ref()));
    frozen.set("capabilityProfileRef", referenceNode(config.capabilityProfile.ref()));
    frozen.set("resourceBudgetRef", referenceNode(config.resourceBudget.ref()));
    ImmutableBytes frozenBytes = json.encodeCanonical(frozen);
    ArtifactReference frozenRequest = contentReference("frozen-request", frozenBytes);

    ObjectNode request = JsonNodeFactory.instance.objectNode();
    request.putArray("approvedFindingRefs");
    request.set(
        "artifactPolicyRegistryRef",
        referenceNode(
            new ArtifactReference(
                config.policies.reference().artifactId(), config.policies.reference().sha256())));
    request.set("candidateSeriesRef", referenceNode(config.candidateSeries.ref()));
    request.set("frozenRepositoryRequestRef", referenceNode(frozenRequest));
    request.putNull("organizationRegistrySeedRef");
    request.putNull("parentCandidateRef");
    request.set("profileBundleRef", referenceNode(config.profileBundle.ref()));
    request.set("promptBundleRef", referenceNode(config.promptBundle.ref()));
    request.put("readerCandidateRound", "ROUND_1");
    request.set("resourceBudgetRef", referenceNode(config.resourceBudget.ref()));
    request.set("schemaBundleRef", referenceNode(config.schemaBundle.ref()));
    request.put("schemaVersion", "analysis-run-request-v2");
    request.put("sourceRegistrationId", registration.sourceRegistrationId().value());
    request.set("toolchainRef", referenceNode(config.toolchain.ref()));
    ImmutableBytes requestBytes = json.encodeCanonical(request);
    String requestDigest = sha256(requestBytes.copyToByteArray());
    ArtifactReference runRequest =
        new ArtifactReference(
            ArtifactId.parse(
                "run-request:"
                    + sha256(
                        concatenate(
                            frame("analysis-run-request-id-v2"),
                            frame(requestBytes.copyToByteArray())))),
            new Sha256Digest(requestDigest));
    return new InputArtifacts(
        freshRunId(),
        runRequest,
        requestBytes,
        frozenRequest,
        frozenBytes,
        Map.of(runRequest, requestBytes, frozenRequest, frozenBytes));
  }

  private static AnalysisRunId freshRunId() {
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    return AnalysisRunId.parse("analysis-run:" + java.util.HexFormat.of().formatHex(random));
  }

  private static void recordModule(
      CanonicalModuleArtifactStore modules,
      String stage,
      ModulePublicationReference reference,
      List<StageEvidence> evidence) {
    ReopenedModulePublication reopened = modules.reopen(reference);
    replaceStage(
        evidence,
        new StageEvidence(
            stage,
            reopened.receipt().status().name(),
            List.of(
                reopened.reference().moduleArtifactRoot().value(),
                reopened.reference().moduleReceiptId().value()),
            reopened.payloads().stream().map(payload -> payload.descriptor().fileName()).toList()));
  }

  private static void recordStep(
      CanonicalAnalysisStepArtifactStore steps,
      String stage,
      org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reference,
      List<StageEvidence> evidence) {
    ReopenedAnalysisStepPublication reopened = steps.reopen(reference);
    replaceStage(
        evidence,
        new StageEvidence(
            stage,
            reopened.receipt().status().name(),
            List.of(
                reopened.reference().analysisStepArtifactRoot().value(),
                reopened.reference().analysisStepReceiptId().value()),
            reopened.semanticPayloads().stream()
                .map(payload -> payload.descriptor().fileName())
                .toList()));
  }

  private static void markAttempt(List<StageEvidence> evidence, String stage) {
    replaceStage(evidence, new StageEvidence(stage, "ATTEMPTED", List.of(), List.of()));
  }

  private static void markFailure(List<StageEvidence> evidence, String stage) {
    StageEvidence prior =
        evidence.stream().filter(value -> value.stage().equals(stage)).findFirst().orElse(null);
    replaceStage(
        evidence,
        new StageEvidence(
            stage,
            "FAILED",
            prior == null ? List.of() : prior.references(),
            prior == null ? List.of() : prior.files()));
  }

  private static void replaceStage(List<StageEvidence> evidence, StageEvidence replacement) {
    evidence.removeIf(value -> value.stage().equals(replacement.stage()));
    evidence.add(replacement);
  }

  private static SourceAccounting verifyInventorySnapshot(
      ReopenedAnalysisStepPublication publication, CanonicalJsonCodec json) {
    JsonNode snapshot = directPayload(publication, "verified-snapshot.json", json);
    int tracked = requiredInt(snapshot, "trackedRegularFileCount");
    int verified = requiredInt(snapshot, "verifiedRegularFileCount");
    int unverified = requiredInt(snapshot, "unverifiedRegularFileCount");
    int text = requiredInt(snapshot, "analyzableTextFileCount");
    int media = requiredInt(snapshot, "nonAnalyzableMediaFileCount");
    assertThat(tracked).isEqualTo(verified + unverified);
    assertThat(verified).isEqualTo(text + media);
    assertThat(unverified).isZero();
    return new SourceAccounting(tracked, verified, unverified, text, media, true);
  }

  private static void verifyFactAccounting(
      ReopenedAnalysisStepPublication publication, CanonicalJsonCodec json) {
    JsonNode accounting = directPayload(publication, "fact-accounting.json", json);
    int candidates = requiredInt(accounting, "candidateFactCount");
    int admitted = requiredInt(accounting, "admittedFactCount");
    int rejected = requiredInt(accounting, "rejectedFactCount");
    int candidateAtoms = requiredInt(accounting, "candidateAtomCount");
    assertThat(candidates).isEqualTo(admitted + rejected);
    assertThat(candidateAtoms)
        .isEqualTo(
            requiredInt(accounting, "admittedAtomDispositionCount")
                + requiredInt(accounting, "rejectedAtomCount"));
    assertThat(requiredInt(accounting, "provenFactAtomCount"))
        .isEqualTo(requiredInt(accounting, "admittedAtomDispositionCount"));
    assertThat(accounting.path("candidateDenominatorKeys")).hasSize(candidates);
    assertThat(accounting.path("admittedFactIds")).hasSize(admitted);
    assertThat(accounting.path("rejectedCandidateDenominatorKeys")).hasSize(rejected);
  }

  private static Set<String> discoveryEntryIds(
      ReopenedAnalysisStepPublication publication, CanonicalJsonCodec json) {
    Set<String> ids = new HashSet<>();
    for (JsonNode entry : jsonLines(payloadBytes(publication, "entry-points.jsonl"), json)) {
      assertThat(entry.path("entryId").isTextual()).isTrue();
      assertThat(ids.add(entry.path("entryId").textValue())).isTrue();
    }
    return ids;
  }

  private static void verifyBusinessFlowClosure(
      ReopenedAnalysisStepPublication publication,
      Set<String> discoveryEntryIds,
      CanonicalJsonCodec json) {
    JsonNode coverage = directPayload(publication, "flow-coverage.json", json);
    assertThat(coverage.path("closed").asBoolean()).isTrue();
    Set<String> entries = strings(coverage.path("entryIds"));
    assertThat(entries).containsExactlyInAnyOrderElementsOf(discoveryEntryIds);
    JsonNode flowSlices = directPayload(publication, "flow-slices.json", json);
    Map<String, Set<String>> flowGapIdsByFlow = new HashMap<>();
    Set<String> flowSlicePayloadIds = new HashSet<>();
    for (JsonNode flow : flowSlices.path("flowSlices")) {
      String flowId = flow.path("flowSliceId").textValue();
      assertThat(flowSlicePayloadIds.add(flowId)).isTrue();
      flowGapIdsByFlow.put(flowId, strings(flow.path("gapIds")));
    }
    List<JsonNode> dispositions =
        jsonLines(payloadBytes(publication, "entry-dispositions.jsonl"), json);
    Set<String> dispositionIds = new HashSet<>();
    Set<String> compiledEntries = new HashSet<>();
    Set<String> gappedEntries = new HashSet<>();
    Set<String> excludedEntries = new HashSet<>();
    for (JsonNode disposition : dispositions) {
      assertThat(dispositionIds.add(disposition.path("entryId").textValue())).isTrue();
      String entryId = disposition.path("entryId").textValue();
      String dispositionKind = disposition.path("disposition").textValue();
      Set<String> gapIds = strings(disposition.path("gapIds"));
      if ("COMPILED".equals(dispositionKind)) {
        assertThat(disposition.path("flowSliceId").isTextual()).isTrue();
        String flowId = disposition.path("flowSliceId").textValue();
        assertThat(flowGapIdsByFlow).containsKey(flowId);
        assertThat(gapIds).containsExactlyInAnyOrderElementsOf(flowGapIdsByFlow.get(flowId));
        compiledEntries.add(entryId);
      } else if ("GAP".equals(dispositionKind)) {
        assertThat(disposition.path("flowSliceId").isNull()).isTrue();
        assertThat(gapIds).isNotEmpty();
        gappedEntries.add(entryId);
      } else if ("EXCLUDED".equals(dispositionKind)) {
        assertThat(disposition.path("flowSliceId").isNull()).isTrue();
        assertThat(gapIds).isNotEmpty();
        excludedEntries.add(entryId);
      } else {
        fail("unknown entry disposition: " + dispositionKind);
      }
    }
    assertThat(dispositionIds).containsExactlyInAnyOrderElementsOf(entries);
    assertThat(strings(coverage.path("compiledEntryIds")))
        .containsExactlyInAnyOrderElementsOf(compiledEntries);
    assertThat(strings(coverage.path("gappedEntryIds")))
        .containsExactlyInAnyOrderElementsOf(gappedEntries);
    assertThat(strings(coverage.path("excludedEntryIds")))
        .containsExactlyInAnyOrderElementsOf(excludedEntries);
    Set<String> entryPartition = new HashSet<>(compiledEntries);
    entryPartition.addAll(gappedEntries);
    entryPartition.addAll(excludedEntries);
    assertThat(entryPartition).containsExactlyInAnyOrderElementsOf(discoveryEntryIds);
    Set<String> flows = strings(coverage.path("flowSliceIds"));
    assertThat(flowSlicePayloadIds).containsExactlyInAnyOrderElementsOf(flows);
    Set<String> capsuleFlows = new HashSet<>();
    List<JsonNode> capsules = jsonLines(payloadBytes(publication, "evidence-capsules.jsonl"), json);
    Set<String> capsuleIds = new HashSet<>();
    Map<String, Set<String>> expectedIneligibilityByFlow = new HashMap<>();
    for (JsonNode mapping : coverage.path("modelIneligibilityByFlow")) {
      String flowId = mapping.path("flowSliceId").textValue();
      assertThat(expectedIneligibilityByFlow.put(flowId, strings(mapping.path("gapIds")))).isNull();
      assertThat(expectedIneligibilityByFlow.get(flowId)).isNotEmpty();
    }
    for (JsonNode capsule : capsules) {
      assertThat(capsuleIds.add(capsule.path("evidenceCapsuleId").textValue())).isTrue();
      assertThat(capsuleFlows.add(capsule.path("flowSliceId").textValue())).isTrue();
      String flowId = capsule.path("flowSliceId").textValue();
      Set<String> capsuleGaps = strings(capsule.path("modelIneligibilityGapIds"));
      if ("ELIGIBLE".equals(capsule.path("modelEligibility").textValue())) {
        assertThat(capsuleGaps).isEmpty();
      } else {
        assertThat(capsule.path("modelEligibility").textValue()).isEqualTo("INELIGIBLE");
        assertThat(expectedIneligibilityByFlow).containsKey(flowId);
        assertThat(capsuleGaps)
            .containsExactlyInAnyOrderElementsOf(expectedIneligibilityByFlow.get(flowId));
      }
    }
    assertThat(capsuleFlows).containsExactlyInAnyOrderElementsOf(flows);
    assertThat(strings(coverage.path("capsuleIds")))
        .containsExactlyInAnyOrderElementsOf(capsuleIds);
    Set<String> eligible = strings(coverage.path("modelEligibleFlowSliceIds"));
    Set<String> ineligible = strings(coverage.path("modelIneligibleFlowSliceIds"));
    if (!ineligible.isEmpty()) {
      assertThat(eligible).doesNotContainAnyElementsOf(ineligible);
    }
    Set<String> partition = new HashSet<>(eligible);
    partition.addAll(ineligible);
    assertThat(partition).containsExactlyInAnyOrderElementsOf(flows);
    assertThat(expectedIneligibilityByFlow.keySet())
        .containsExactlyInAnyOrderElementsOf(ineligible);
    Set<String> mappedGaps =
        expectedIneligibilityByFlow.values().stream()
            .flatMap(Set::stream)
            .collect(java.util.stream.Collectors.toSet());
    assertThat(strings(coverage.path("modelIneligibilityGapIds")))
        .containsExactlyInAnyOrderElementsOf(mappedGaps);
    List<JsonNode> flowGaps = jsonLines(payloadBytes(publication, "flow-gaps.jsonl"), json);
    Set<String> flowGapIds = new HashSet<>();
    for (JsonNode gap : flowGaps) {
      assertThat(flowGapIds.add(gap.path("gapId").textValue())).isTrue();
    }
    assertThat(strings(coverage.path("gapIds"))).containsExactlyInAnyOrderElementsOf(flowGapIds);
    assertThat(mappedGaps).isSubsetOf(flowGapIds);
  }

  private static void writeReport(
      Path workspace,
      String status,
      RegisteredSourceCapture capture,
      ReopenedAnalysisStepPublication publication,
      List<StageEvidence> evidence,
      SourceAccounting accounting,
      CanonicalJsonCodec json)
      throws IOException {
    ObjectNode report = baseReport(status, capture, publication);
    if (accounting != null) putAccounting(report, accounting);
    report.set("stages", stageNodes(evidence));
    if (publication != null) {
      report.set(
          "semanticFiles",
          JsonNodeFactory.instance
              .arrayNode()
              .addAll(
                  publication.semanticPayloads().stream()
                      .map(
                          payload ->
                              JsonNodeFactory.instance.textNode(payload.descriptor().fileName()))
                      .collect(Collectors.toList())));
      putPublicationAccounting(report, publication, json);
    }
    report.put(
        "reportRunId",
        publication == null ? "NOT_RUN" : publication.reference().address().runId().value());
    writeCanonical(workspace.resolve("fixed-repository-acceptance-report.json"), report);
  }

  private static void writeReportSafely(
      Path workspace,
      String status,
      Throwable failure,
      RegisteredSourceCapture capture,
      ReopenedAnalysisStepPublication publication,
      List<StageEvidence> evidence,
      SourceAccounting accounting,
      CanonicalJsonCodec json) {
    try {
      if (workspace != null && Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)) {
        ObjectNode report = baseReport(status, capture, publication);
        report.put(
            "error", failure.getClass().getName() + ": " + String.valueOf(failure.getMessage()));
        if (accounting != null) putAccounting(report, accounting);
        report.set("stages", stageNodes(evidence));
        if (publication != null) {
          try {
            putPublicationAccounting(report, publication, json);
          } catch (RuntimeException ignored) {
            report.put("publicationAccountingStatus", "UNAVAILABLE");
          }
        }
        writeCanonical(workspace.resolve("fixed-repository-acceptance-report.json"), report);
      }
    } catch (IOException ignored) {
      // Preserve the original pipeline failure; report writing is evidence only.
    }
  }

  private static ObjectNode baseReport(
      String status, RegisteredSourceCapture capture, ReopenedAnalysisStepPublication publication) {
    ObjectNode report = JsonNodeFactory.instance.objectNode();
    report.put("schemaVersion", "fixed-repository-acceptance-report-v1");
    report.put("status", status);
    report.put("captureCompleted", capture != null);
    report.put("businessFlowPublicationReached", publication != null);
    if (capture != null) {
      report.put("commitId", capture.commitId());
      report.put("declaredRepositoryIdentity", capture.declaredRepositoryIdentity());
      report.put("trackedRegularFileCount", capture.manifestEntries().size());
      ObjectNode refs = report.putObject("captureReferences");
      refs.set("sourceRegistrationRef", referenceNode(capture.sourceRegistrationRef()));
      refs.put("sourceRegistrationId", capture.sourceRegistrationRef().artifactId().value());
      refs.set("captureReceiptRef", referenceNode(capture.captureReceiptRef()));
      refs.set("snapshotManifestRef", referenceNode(capture.snapshotManifestRef()));
    }
    if (publication != null) report.put("publicationStatus", publication.receipt().status().name());
    return report;
  }

  private static void putAccounting(ObjectNode report, SourceAccounting accounting) {
    ObjectNode node = report.putObject("sourceAccounting");
    node.put("completeCapture", accounting.completeCapture());
    node.put("trackedRegularFileCount", accounting.tracked());
    node.put("verifiedRegularFileCount", accounting.verified());
    node.put("unverifiedRegularFileCount", accounting.unverified());
    node.put("analyzableTextFileCount", accounting.text());
    node.put("nonAnalyzableMediaFileCount", accounting.media());
    node.put("repositoryEligible", accounting.completeCapture() && accounting.unverified() == 0);
  }

  private static void putPublicationAccounting(
      ObjectNode report, ReopenedAnalysisStepPublication publication, CanonicalJsonCodec json) {
    List<JsonNode> dispositions =
        jsonLines(payloadBytes(publication, "entry-dispositions.jsonl"), json);
    ObjectNode entries = report.putObject("entryAccounting");
    int compiled = 0;
    int gapped = 0;
    int excluded = 0;
    int gapReferences = 0;
    for (JsonNode disposition : dispositions) {
      switch (disposition.path("disposition").textValue()) {
        case "COMPILED" -> compiled++;
        case "GAP" -> gapped++;
        case "EXCLUDED" -> excluded++;
        default -> entries.put("status", "INVALID");
      }
      gapReferences += strings(disposition.path("gapIds")).size();
    }
    entries.put("status", entries.has("status") ? entries.get("status").textValue() : "VERIFIED");
    entries.put("denominatorCount", dispositions.size());
    entries.put("compiledCount", compiled);
    entries.put("gapCount", gapped);
    entries.put("excludedCount", excluded);
    entries.put("gapReferenceCount", gapReferences);

    JsonNode coverage = directPayload(publication, "flow-coverage.json", json);
    Set<String> flows = strings(coverage.path("flowSliceIds"));
    Set<String> eligible = strings(coverage.path("modelEligibleFlowSliceIds"));
    Set<String> ineligible = strings(coverage.path("modelIneligibleFlowSliceIds"));
    Set<String> capsuleFlows = new HashSet<>();
    int capsuleCount = 0;
    for (JsonNode capsule : jsonLines(payloadBytes(publication, "evidence-capsules.jsonl"), json)) {
      capsuleCount++;
      capsuleFlows.add(capsule.path("flowSliceId").textValue());
    }
    ObjectNode flowAccounting = report.putObject("flowAccounting");
    flowAccounting.put("flowSliceCount", flows.size());
    flowAccounting.put("capsuleCount", capsuleCount);
    flowAccounting.put("eligibleCount", eligible.size());
    flowAccounting.put("ineligibleCount", ineligible.size());
    flowAccounting.put("gapCount", strings(coverage.path("gapIds")).size());
    flowAccounting.put(
        "modelIneligibilityGapCount", strings(coverage.path("modelIneligibilityGapIds")).size());
    flowAccounting.put("flowCapsuleBijection", flows.equals(capsuleFlows));
  }

  private static ArrayNode stageNodes(List<StageEvidence> evidence) {
    ArrayNode stages = JsonNodeFactory.instance.arrayNode();
    List<String> planned =
        List.of(
            "inventory-M1-admitted-source-request",
            "inventory-M2-verified-source-index",
            "inventory-M3-verified-source-inventory",
            "discovery-M4-application-discovery",
            "graphs-M6-program-graphs",
            "facts-M1-candidates",
            "facts-M2-proofs",
            "facts-M3-proven-code-facts",
            "flows-M1-compilation",
            "flows-M2-capsule-projection",
            "flows-M3-business-flows");
    for (String stage : planned) {
      StageEvidence found =
          evidence.stream().filter(value -> value.stage().equals(stage)).findFirst().orElse(null);
      ObjectNode node = stages.addObject().put("stage", stage);
      if (found == null) {
        node.put("status", "NOT_RUN");
        node.putArray("references");
        node.putArray("files");
      } else {
        node.put("status", found.status());
        ArrayNode references = node.putArray("references");
        found.references().forEach(references::add);
        ArrayNode files = node.putArray("files");
        found.files().forEach(files::add);
      }
    }
    return stages;
  }

  private static JsonNode directPayload(
      ReopenedAnalysisStepPublication publication, String fileName, CanonicalJsonCodec json) {
    return json.parseCanonical(payloadBytes(publication, fileName));
  }

  private static ImmutableBytes payloadBytes(
      ReopenedAnalysisStepPublication publication, String fileName) {
    return publication.semanticPayloads().stream()
        .filter(payload -> payload.descriptor().fileName().equals(fileName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("missing semantic payload: " + fileName))
        .canonicalUtf8();
  }

  private static List<JsonNode> jsonLines(ImmutableBytes bytes, CanonicalJsonCodec json) {
    String text = new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
    if (text.isEmpty()) return List.of();
    return Arrays.stream(text.split("\\n", -1))
        .filter(line -> !line.isEmpty())
        .map(
            line ->
                json.parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
        .toList();
  }

  private static int requiredInt(JsonNode node, String field) {
    assertThat(node.has(field) && node.get(field).canConvertToInt())
        .as("required integer field: " + field)
        .isTrue();
    return node.get(field).intValue();
  }

  private static Set<String> strings(JsonNode node) {
    assertThat(node.isArray()).isTrue();
    Set<String> values = new HashSet<>();
    node.forEach(
        value -> {
          assertThat(value.isTextual()).isTrue();
          assertThat(values.add(value.textValue())).isTrue();
        });
    return values;
  }

  private static void writeCanonical(Path path, ObjectNode node) throws IOException {
    Files.write(path, new CanonicalJsonCodec().encodeCanonical(node).copyToByteArray());
  }

  private record StageEvidence(
      String stage, String status, List<String> references, List<String> files) {}

  private record SourceAccounting(
      int tracked, int verified, int unverified, int text, int media, boolean completeCapture) {}

  private static ObjectNode referenceNode(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static ArtifactReference contentReference(String prefix, ImmutableBytes bytes) {
    String digest = sha256(bytes.copyToByteArray());
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + digest), new Sha256Digest(digest));
  }

  private static List<ArtifactReference> sorted(List<ArtifactReference> references) {
    return references.stream()
        .sorted(Comparator.comparing(reference -> reference.artifactId().value()))
        .toList();
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
    int length = Arrays.stream(values).mapToInt(value -> value.length).sum();
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record InputArtifacts(
      AnalysisRunId runId,
      ArtifactReference runRequest,
      ImmutableBytes runRequestBytes,
      ArtifactReference frozenRequest,
      ImmutableBytes frozenRequestBytes,
      Map<ArtifactReference, ImmutableBytes> inputs) {

    private ImmutableBytes reopen(ArtifactReference reference) {
      ImmutableBytes bytes = inputs.get(reference);
      if (bytes == null) throw new IllegalArgumentException("unknown acceptance input reference");
      return bytes;
    }
  }

  private record ReferenceSpec(ArtifactReference ref, ImmutableBytes bytes) {}

  private record AcceptanceConfig(
      CanonicalArtifactPolicyRegistry policies,
      ArtifactControls controls,
      ReferenceSpec capturePolicy,
      ReferenceSpec candidateSeries,
      ReferenceSpec capabilityProfile,
      ReferenceSpec flowProfile,
      ReferenceSpec graphProfile,
      ReferenceSpec profileBundle,
      ReferenceSpec promptBundle,
      ReferenceSpec resourceBudget,
      ReferenceSpec schemaBundle,
      ReferenceSpec toolchain,
      ReferenceSpec verificationPolicy,
      ReferenceSpec discoveryProfileRef,
      ReferenceSpec factRegistryRef,
      ReferenceSpec proofRuleRegistryRef,
      int inventoryMaxFiles,
      long inventoryMaxBytes,
      DiscoveryProfile discoveryProfile,
      FactRegistry factRegistry,
      ProofRuleRegistry proofRules,
      FlowCompilationProfile flowProfileTyped,
      CapsuleProjectionProfile capsuleProfile,
      ArtifactStoreLimits storeLimits) {

    private static AcceptanceConfig load() throws IOException {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      ImmutableBytes configBytes;
      try (InputStream input =
          FixedRepositoryBusinessFlowsIT.class.getResourceAsStream(CONFIG_RESOURCE)) {
        if (input == null) throw new IllegalArgumentException("acceptance config is missing");
        configBytes = canonicalClasspathResource(input);
      }
      JsonNode configNode = json.parseCanonical(configBytes);
      if (!(configNode instanceof ObjectNode config))
        throw new IllegalArgumentException("config must be object");
      requireFields(
          config,
          Set.of(
              "artifactPolicyRegistryBase64",
              "controls",
              "profiles",
              "references",
              "schemaVersion"));
      requireText(config, "schemaVersion", "fixed-repository-acceptance-config-v1");
      ImmutableBytes registryBytes =
          ImmutableBytes.copyOf(
              Base64.getDecoder().decode(config.get("artifactPolicyRegistryBase64").textValue()));
      CanonicalArtifactPolicyRegistry policies =
          CanonicalArtifactPolicyRegistry.load(registryBytes, json);
      if (REQUIRED_POLICIES.size() != 38)
        throw new IllegalStateException("policy oracle size drift");
      JsonNode registryNode = json.parseCanonical(registryBytes);
      Set<String> actualPolicies = new HashSet<>();
      registryNode
          .get("policies")
          .forEach(
              policy ->
                  actualPolicies.add(
                      policy.get("artifactType").textValue()
                          + "|"
                          + policy.get("schemaVersion").textValue()));
      if (!actualPolicies.equals(REQUIRED_POLICIES))
        throw new IllegalArgumentException("policy union drift");
      for (String key : REQUIRED_POLICIES) {
        String[] parts = key.split("\\|", -1);
        policies.resolve(new ArtifactPolicyKey(parts[0], parts[1]));
      }
      JsonNode controls = config.get("controls");
      requireFields(
          (ObjectNode) controls,
          Set.of(
              "artifactPolicyRegistryRef",
              "profileBundleRef",
              "promptBundleRef",
              "resourceBudgetRef",
              "schemaBundleRef",
              "toolchainRef"));
      ReferenceSpec profile = referenceSpec((ObjectNode) controls.get("profileBundleRef"));
      ReferenceSpec prompt = referenceSpec((ObjectNode) controls.get("promptBundleRef"));
      ReferenceSpec budget = referenceSpec((ObjectNode) controls.get("resourceBudgetRef"));
      ReferenceSpec schema = referenceSpec((ObjectNode) controls.get("schemaBundleRef"));
      ReferenceSpec toolchain = referenceSpec((ObjectNode) controls.get("toolchainRef"));
      ArtifactReference policyReference =
          parseReference((ObjectNode) controls.get("artifactPolicyRegistryRef"));
      if (!policyReference.artifactId().equals(policies.reference().artifactId())
          || !policyReference.sha256().equals(policies.reference().sha256())) {
        throw new IllegalArgumentException("policy registry reference drift");
      }
      ArtifactControls artifactControls =
          new ArtifactControls(
              toolchain.ref().sha256(),
              profile.ref().sha256(),
              schema.ref().sha256(),
              prompt.ref().sha256(),
              policies.reference());

      ObjectNode refs = (ObjectNode) config.get("references");
      requireFields(
          refs,
          Set.of(
              "capturePolicyRef",
              "candidateSeriesRef",
              "capabilityProfileRef",
              "flowProfileRef",
              "graphProfileRef",
              "verificationPolicyRef",
              "capsuleProfileRef",
              "discoveryProfileRef",
              "factRegistryRef",
              "proofRuleRegistryRef"));
      ReferenceSpec capturePolicy = referenceSpec((ObjectNode) refs.get("capturePolicyRef"));
      ReferenceSpec candidateSeries = referenceSpec((ObjectNode) refs.get("candidateSeriesRef"));
      ReferenceSpec capabilityProfile =
          referenceSpec((ObjectNode) refs.get("capabilityProfileRef"));
      ReferenceSpec flowProfile = referenceSpec((ObjectNode) refs.get("flowProfileRef"));
      ReferenceSpec graphProfile = referenceSpec((ObjectNode) refs.get("graphProfileRef"));
      ReferenceSpec verificationPolicy =
          referenceSpec((ObjectNode) refs.get("verificationPolicyRef"));
      ReferenceSpec discoveryProfileRef =
          referenceSpec((ObjectNode) refs.get("discoveryProfileRef"));
      ReferenceSpec factRegistryRef = referenceSpec((ObjectNode) refs.get("factRegistryRef"));
      ReferenceSpec proofRuleRegistryRef =
          referenceSpec((ObjectNode) refs.get("proofRuleRegistryRef"));
      DiscoveryProfile discoveryProfile = DiscoveryProfile.standard();
      FactRegistry factRegistry = FactRegistry.standardJavaFacts();
      ProofRuleRegistry proofRules = ProofRuleRegistry.standardJavaBoundary();
      assertReferenceBytes(discoveryProfileRef, canonicalDiscoveryProfile(discoveryProfile));
      assertReferenceBytes(factRegistryRef, canonicalFactRegistry(factRegistry));
      assertReferenceBytes(proofRuleRegistryRef, canonicalProofRuleRegistry(proofRules));

      ObjectNode profiles = (ObjectNode) config.get("profiles");
      requireFields(profiles, Set.of("capsule", "flow", "inventory", "store"));
      ObjectNode inventory = (ObjectNode) profiles.get("inventory");
      requireFields(inventory, Set.of("maxSourceBytes", "maxSourceFiles"));
      int maxFiles = inventory.get("maxSourceFiles").intValue();
      long maxBytes = inventory.get("maxSourceBytes").longValue();
      ObjectNode budgetDocument = (ObjectNode) json.parseCanonical(budget.bytes);
      if (budgetDocument.get("maxSourceFiles").intValue() != maxFiles
          || budgetDocument.get("maxSourceBytes").longValue() != maxBytes) {
        throw new IllegalArgumentException("inventory typed profile drift");
      }

      ObjectNode flow = (ObjectNode) profiles.get("flow");
      requireFields(
          flow,
          Set.of(
              "maxFlows",
              "maxOutcomesPerFlow",
              "maxFlowNodes",
              "maxFlowEdges",
              "maxTraversalDepth",
              "maxProcessJoinSignalsPerFlow",
              "maxProcessJoinSignalBasisRefs"));
      FlowCompilationProfile flowTyped =
          new FlowCompilationProfile(
              flowProfile.ref(),
              flow.get("maxFlows").intValue(),
              flow.get("maxOutcomesPerFlow").intValue(),
              flow.get("maxFlowNodes").intValue(),
              flow.get("maxFlowEdges").intValue(),
              flow.get("maxTraversalDepth").intValue(),
              flow.get("maxProcessJoinSignalsPerFlow").intValue(),
              flow.get("maxProcessJoinSignalBasisRefs").intValue());
      assertProfileFields(
          json.parseCanonical(flowProfile.bytes),
          flow,
          Set.of(
              "maxFlows",
              "maxOutcomesPerFlow",
              "maxFlowNodes",
              "maxFlowEdges",
              "maxTraversalDepth",
              "maxProcessJoinSignalsPerFlow",
              "maxProcessJoinSignalBasisRefs"));

      ObjectNode capsule = (ObjectNode) profiles.get("capsule");
      requireFields(
          capsule,
          Set.of("maxCapsules", "maxSpansPerCapsule", "maxSpanBytes", "maxCapsuleUtf8Bytes"));
      ReferenceSpec capsuleRef = referenceSpec((ObjectNode) refs.get("capsuleProfileRef"));
      CapsuleProjectionProfile capsuleTyped =
          new CapsuleProjectionProfile(
              capsuleRef.ref(),
              capsule.get("maxCapsules").intValue(),
              capsule.get("maxSpansPerCapsule").intValue(),
              capsule.get("maxSpanBytes").intValue(),
              capsule.get("maxCapsuleUtf8Bytes").intValue());
      assertProfileFields(
          json.parseCanonical(capsuleRef.bytes),
          capsule,
          Set.of("maxCapsules", "maxSpansPerCapsule", "maxSpanBytes", "maxCapsuleUtf8Bytes"));

      ObjectNode store = (ObjectNode) profiles.get("store");
      requireFields(
          store,
          Set.of(
              "maxArtifactBytes", "maxDirectoryEntries", "maxPayloadFiles", "maxPublicationBytes"));
      ArtifactStoreLimits limits =
          new ArtifactStoreLimits(
              store.get("maxPayloadFiles").intValue(),
              store.get("maxArtifactBytes").longValue(),
              store.get("maxPublicationBytes").longValue(),
              store.get("maxDirectoryEntries").intValue());
      return new AcceptanceConfig(
          policies,
          artifactControls,
          capturePolicy,
          candidateSeries,
          capabilityProfile,
          flowProfile,
          graphProfile,
          profile,
          prompt,
          budget,
          schema,
          toolchain,
          verificationPolicy,
          discoveryProfileRef,
          factRegistryRef,
          proofRuleRegistryRef,
          maxFiles,
          maxBytes,
          discoveryProfile,
          factRegistry,
          proofRules,
          flowTyped,
          capsuleTyped,
          limits);
    }

    private static ReferenceSpec referenceSpec(ObjectNode node) {
      requireFields(node, Set.of("artifactId", "content", "sha256"));
      byte[] bytes = node.get("content").textValue().getBytes(StandardCharsets.UTF_8);
      new CanonicalJsonCodec().parseCanonical(ImmutableBytes.copyOf(bytes));
      String digest = sha256(bytes);
      if (!digest.equals(node.get("sha256").textValue()))
        throw new IllegalArgumentException("reference digest drift");
      ArtifactReference reference = parseReference(node);
      if (!reference.sha256().value().equals(digest)
          || !reference.artifactId().value().endsWith(":" + digest))
        throw new IllegalArgumentException("reference identity drift");
      return new ReferenceSpec(reference, ImmutableBytes.copyOf(bytes));
    }
  }

  private static ImmutableBytes canonicalClasspathResource(InputStream input) throws IOException {
    byte[] bytes = input.readAllBytes();
    if (bytes.length > 0 && bytes[bytes.length - 1] == '\n') {
      bytes = Arrays.copyOf(bytes, bytes.length - 1);
    }
    return ImmutableBytes.copyOf(bytes);
  }

  private static void assertReferenceBytes(ReferenceSpec reference, ImmutableBytes expected) {
    assertThat(reference.bytes().copyToByteArray()).containsExactly(expected.copyToByteArray());
  }

  private static void assertProfileFields(
      JsonNode actualNode, ObjectNode expected, Set<String> fields) {
    assertThat(actualNode).isInstanceOf(ObjectNode.class);
    ObjectNode actual = (ObjectNode) actualNode;
    Set<String> allowed = new HashSet<>(fields);
    allowed.add("schemaVersion");
    Set<String> actualFields = new HashSet<>();
    actual.fieldNames().forEachRemaining(actualFields::add);
    assertThat(actualFields).isEqualTo(allowed);
    for (String field : fields) {
      assertThat(actual.get(field)).isEqualTo(expected.get(field));
    }
  }

  private static ImmutableBytes canonicalDiscoveryProfile(DiscoveryProfile profile) {
    return new CanonicalJsonCodec()
        .encodeCanonical(
            JsonNodeFactory.instance.objectNode().put("ruleVersion", profile.ruleVersion()));
  }

  private static ImmutableBytes canonicalFactRegistry(FactRegistry registry) {
    ObjectNode root =
        JsonNodeFactory.instance.objectNode().put("schemaVersion", registry.schemaVersion());
    ArrayNode templates = root.putArray("templates");
    for (FactRegistry.FactTemplate template : registry.templates()) {
      ObjectNode item =
          templates
              .addObject()
              .put("candidateFactKey", template.candidateFactKey())
              .put("kind", template.kind());
      ArrayNode atoms = item.putArray("requiredAtoms");
      for (FactRegistry.RequiredAtomTemplate atom : template.requiredAtoms()) {
        ObjectNode atomNode =
            atoms
                .addObject()
                .put("atomKey", atom.atomKey())
                .put("role", atom.role())
                .put("valueType", atom.valueType());
        ArrayNode evidence = atomNode.putArray("expectedEvidenceKinds");
        atom.expectedEvidenceKinds().forEach(evidence::add);
      }
    }
    return new CanonicalJsonCodec().encodeCanonical(root);
  }

  private static ImmutableBytes canonicalProofRuleRegistry(ProofRuleRegistry registry) {
    ObjectNode root =
        JsonNodeFactory.instance.objectNode().put("schemaVersion", registry.schemaVersion());
    ArrayNode allowances = root.putArray("allowances");
    for (ProofRuleRegistry.RuleAllowance allowance : registry.allowances()) {
      ObjectNode item =
          allowances.addObject().put("subjectCategory", allowance.subjectCategory().name());
      ArrayNode ids = item.putArray("ruleIds");
      allowance.ruleIds().forEach(ids::add);
      item.put("ruleVersion", allowance.ruleVersion());
    }
    return new CanonicalJsonCodec().encodeCanonical(root);
  }

  private static ArtifactReference parseReference(ObjectNode node) {
    Set<String> actual = new HashSet<>();
    node.fieldNames().forEachRemaining(actual::add);
    assertThat(actual)
        .as("reference fields must be exact artifact identity or identity-plus-content")
        .isIn(Set.of("artifactId", "sha256"), Set.of("artifactId", "content", "sha256"));
    return new ArtifactReference(
        ArtifactId.parse(node.get("artifactId").textValue()),
        Sha256Digest.parse(node.get("sha256").textValue()));
  }

  private static void requireText(ObjectNode node, String name, String expected) {
    if (!node.has(name) || !expected.equals(node.get(name).textValue()))
      throw new IllegalArgumentException("config value drift: " + name);
  }

  private static void requireFields(ObjectNode node, Set<String> expected, String... optional) {
    if (node == null) throw new IllegalArgumentException("config object missing");
    Set<String> actual = new HashSet<>();
    node.fieldNames().forEachRemaining(actual::add);
    Set<String> allowed = new HashSet<>(expected);
    allowed.addAll(List.of(optional));
    if (!actual.equals(allowed))
      throw new IllegalArgumentException("config fields drift: " + actual);
  }
}
