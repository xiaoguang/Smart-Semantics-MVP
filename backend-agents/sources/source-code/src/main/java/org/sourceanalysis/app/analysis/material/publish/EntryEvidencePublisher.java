package org.sourceanalysis.app.analysis.material.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndexModulePublisher;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpRequestRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageContext;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageSourceUnit;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/**
 * Receipt-last publisher for the bounded Step05 entry-evidence directory.
 *
 * <p>It owns only the new v3 module key. Historical code-reading-material packets keep their
 * existing publisher and readers.
 */
public final class EntryEvidencePublisher {

  public static final String MODULE_KEY = "entry-evidence";
  public static final String MODULE_VERSION = "v3";
  public static final String PRODUCER = "entry-evidence-v1";
  public static final String ENTRY_FILE_PREFIX = "entry-";
  public static final String ENTRY_TYPE = "ENTRY_EVIDENCE";
  public static final String ENTRY_SCHEMA = "entry-evidence-v1";
  public static final String INDEX_FILE = "entry-evidence-index.json";
  public static final String INDEX_TYPE = "ENTRY_EVIDENCE_INDEX";
  public static final String INDEX_SCHEMA = "entry-evidence-index-v1";
  public static final String COVERAGE_FILE = "frontend-coverage.jsonl";
  public static final String COVERAGE_TYPE = "FRONTEND_EVIDENCE_COVERAGE";
  public static final String COVERAGE_SCHEMA = "frontend-evidence-coverage-v1";
  public static final String V2_MODULE_VERSION = "v4";
  public static final String V2_PRODUCER = "entry-evidence-v2";
  public static final String V2_ENTRY_SCHEMA = "entry-evidence-v2";
  public static final String V2_INDEX_SCHEMA = "entry-evidence-index-v2";
  public static final String V2_COVERAGE_SCHEMA = "frontend-evidence-coverage-v2";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Comparator<String> UTF8_ORDER = EntryEvidencePublisher::compareUtf8;

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;
  private final CanonicalAnalysisStepArtifactStore sourceSteps;
  private final CanonicalAnalysisStepArtifactStore backendSteps;
  private final CanonicalAnalysisStepArtifactStore persistenceSteps;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public EntryEvidencePublisher(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      CanonicalAnalysisStepArtifactStore sourceSteps) {
    this(modules, steps, sourceSteps, steps, steps);
  }

  /**
   * Uses explicit saved R2 and R3 stores when an R4 producer cites predecessors governed by
   * distinct policy registries. The three-argument constructor retains the historical single-store
   * behavior.
   */
  public EntryEvidencePublisher(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      CanonicalAnalysisStepArtifactStore sourceSteps,
      CanonicalAnalysisStepArtifactStore backendSteps,
      CanonicalAnalysisStepArtifactStore persistenceSteps) {
    this.modules = Objects.requireNonNull(modules, "entry-evidence module store");
    this.steps = Objects.requireNonNull(steps, "entry-evidence analysis-step store");
    this.sourceSteps =
        Objects.requireNonNull(sourceSteps, "entry-evidence source analysis-step store");
    this.backendSteps =
        Objects.requireNonNull(backendSteps, "entry-evidence backend analysis-step store");
    this.persistenceSteps =
        Objects.requireNonNull(persistenceSteps, "entry-evidence persistence analysis-step store");
  }

  /**
   * Installs an R4-owned entry-evidence set after proving its exact R0/R1/R2/R3 predecessor chain.
   */
  public AnalysisStepPublicationReference publishTechnicalV3(
      AnalysisRunId destinationRun,
      VerifiedSourceInventoryReference source,
      SelectedSourceBasis sourceBasis,
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference navigation,
      AnalysisStepPublicationReference persistence,
      ModulePublicationReference frontend,
      ArtifactControls frontendControls,
      ArtifactControls backendControls,
      ArtifactControls persistenceControls,
      ArtifactControls r4Controls,
      EntryEvidenceSet set) {
    try {
      Objects.requireNonNull(destinationRun, "R4 destination run");
      Objects.requireNonNull(source, "R0 source inventory");
      Objects.requireNonNull(sourceBasis, "R0 selected source basis");
      Objects.requireNonNull(discovery, "R2 application discovery");
      Objects.requireNonNull(navigation, "R2 Java navigation");
      Objects.requireNonNull(persistence, "R3 persistence publication");
      Objects.requireNonNull(frontend, "R1 frontend publication");
      Objects.requireNonNull(frontendControls, "frontend R1 controls");
      Objects.requireNonNull(backendControls, "R2 controls");
      Objects.requireNonNull(persistenceControls, "R3 controls");
      Objects.requireNonNull(r4Controls, "R4 controls");
      Objects.requireNonNull(set, "entry-evidence set");
      if (!set.header().sourceInventory().equals(source)
          || !set.header().applicationDiscovery().equals(discovery)
          || !set.header().navigationPublication().equals(navigation)
          || !set.header().persistencePublication().equals(persistence)
          || !set.header().frontendPublication().equals(frontend)
          || !set.frontendPageContextCoverage().isEmpty()) {
        throw invalid();
      }
      if (sourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1
          || !sourceBasis.preparedSource().publication().equals(source.publication())
          || !sourceBasis.snapshotId().value().equals(set.header().sourceSnapshotId())) {
        throw invalid();
      }
      ReopenedAnalysisStepPublication sourceStep =
          reopen(source.publication(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, sourceSteps);
      ReopenedAnalysisStepPublication discoveryStep =
          reopen(discovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY, steps);
      ReopenedAnalysisStepPublication navigationStep =
          reopen(navigation.publication(), AnalysisStepKey.PROGRAM_GRAPHS, steps);
      ReopenedAnalysisStepPublication persistenceStep =
          reopen(persistence, AnalysisStepKey.PROVEN_CODE_FACTS, steps);
      ReopenedModulePublication frontendModule = modules.reopen(frontend);
      if (!(frontend.address() instanceof AnalysisStepModuleAddress frontendAddress)) {
        throw invalid();
      }
      // Do not let a caller combine an otherwise well-shaped module-6 receipt with a different
      // prepared R0. This strict reopen proves the saved frontend header and its upstream
      // artifacts against the exact selected basis before R4 can cite it.
      FrontendHttpIndex frontendIndex =
          new FrontendHttpIndexModulePublisher(modules)
              .reopenV2(frontend, frontendAddress.runId(), sourceBasis, frontendControls);
      requireFrontendDenominator(set, frontendIndex);
      requireTechnicalPredecessors(
          destinationRun,
          sourceStep,
          discoveryStep,
          navigationStep,
          persistenceStep,
          frontendModule,
          frontend,
          frontendControls,
          backendControls,
          persistenceControls);

      List<CanonicalModulePayload> payloads = payloads(set, sourceBasis);
      requireBudget(payloads, set);
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              destinationRun, AnalysisStepKey.BUSINESS_FLOWS, 4, MODULE_KEY);
      InstalledModulePublication module =
          modules.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  upstreamPayloadReferences(
                      sourceStep, discoveryStep, navigationStep, persistenceStep, frontendModule),
                  r4Controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  payloads));
      InstalledAnalysisStepPublication step =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      destinationRun, AnalysisStepKey.BUSINESS_FLOWS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(
                      sourceStep.reference(),
                      discoveryStep.reference(),
                      navigationStep.reference(),
                      persistenceStep.reference()),
                  r4Controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  payloads.stream().map(EntryEvidencePublisher::stepPayload).toList(),
                  null));
      ReopenedAnalysisStepPublication reopened = steps.reopen(step.reference());
      if (!reopened.reference().equals(step.reference())
          || !reopened.receipt().controls().equals(r4Controls)
          || reopened.semanticPayloads().size() != payloads.size()) {
        throw invalid();
      }
      return step.reference();
    } catch (RuntimeException failure) {
      if (failure instanceof CapacityExceededException
          || (failure instanceof IllegalArgumentException
              && "ENTRY_EVIDENCE_PUBLICATION_INVALID".equals(failure.getMessage()))) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  /**
   * Installs the additive R4 v2 entry-evidence family from an exact R1 v3 frontend index.
   *
   * <p>The v1 method remains its own strict path: it reopens only R1 v2 and retains its original
   * module/version/schema bytes. This method restores the finite page contexts from the saved R1 v3
   * index and projects their existing request membership without claiming selection-to-save
   * causality.
   */
  public AnalysisStepPublicationReference publishTechnicalV4(
      AnalysisRunId destinationRun,
      VerifiedSourceInventoryReference source,
      SelectedSourceBasis sourceBasis,
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference navigation,
      AnalysisStepPublicationReference persistence,
      ModulePublicationReference frontend,
      ArtifactControls frontendControls,
      ArtifactControls backendControls,
      ArtifactControls persistenceControls,
      ArtifactControls r4Controls,
      EntryEvidenceSet set) {
    try {
      Objects.requireNonNull(destinationRun, "R4 destination run");
      Objects.requireNonNull(source, "R0 source inventory");
      Objects.requireNonNull(sourceBasis, "R0 selected source basis");
      Objects.requireNonNull(discovery, "R2 application discovery");
      Objects.requireNonNull(navigation, "R2 Java navigation");
      Objects.requireNonNull(persistence, "R3 persistence publication");
      Objects.requireNonNull(frontend, "R1 frontend publication");
      Objects.requireNonNull(frontendControls, "frontend R1 controls");
      Objects.requireNonNull(backendControls, "R2 controls");
      Objects.requireNonNull(persistenceControls, "R3 controls");
      Objects.requireNonNull(r4Controls, "R4 controls");
      Objects.requireNonNull(set, "entry-evidence set");
      if (!set.header().sourceInventory().equals(source)
          || !set.header().applicationDiscovery().equals(discovery)
          || !set.header().navigationPublication().equals(navigation)
          || !set.header().persistencePublication().equals(persistence)
          || !set.header().frontendPublication().equals(frontend)
          || sourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1
          || !sourceBasis.preparedSource().publication().equals(source.publication())
          || !sourceBasis.snapshotId().value().equals(set.header().sourceSnapshotId())) {
        throw invalid();
      }
      ReopenedAnalysisStepPublication sourceStep =
          reopen(source.publication(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, sourceSteps);
      ReopenedAnalysisStepPublication discoveryStep =
          reopen(discovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY, backendSteps);
      ReopenedAnalysisStepPublication navigationStep =
          reopen(navigation.publication(), AnalysisStepKey.PROGRAM_GRAPHS, backendSteps);
      ReopenedAnalysisStepPublication persistenceStep =
          reopen(persistence, AnalysisStepKey.PROVEN_CODE_FACTS, persistenceSteps);
      ReopenedModulePublication frontendModule = modules.reopen(frontend);
      if (!(frontend.address() instanceof AnalysisStepModuleAddress frontendAddress)) {
        throw invalid();
      }
      FrontendHttpIndex frontendIndex =
          new FrontendHttpIndexModulePublisher(modules)
              .reopenV3(frontend, frontendAddress.runId(), sourceBasis, frontendControls);
      EntryEvidenceSet versionedSet = withFrontendPageContexts(set, frontendIndex);
      requireFrontendDenominator(versionedSet, frontendIndex);
      requireFrontendPageContexts(versionedSet, frontendIndex);
      requireTechnicalPredecessors(
          destinationRun,
          sourceStep,
          discoveryStep,
          navigationStep,
          persistenceStep,
          frontendModule,
          frontend,
          frontendControls,
          backendControls,
          persistenceControls);

      List<CanonicalModulePayload> payloads = payloadsV2(versionedSet, sourceBasis);
      requireBudget(payloads, versionedSet);
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              destinationRun, AnalysisStepKey.BUSINESS_FLOWS, 4, MODULE_KEY);
      InstalledModulePublication module =
          modules.install(
              new ModuleInstallRequest(
                  address,
                  V2_MODULE_VERSION,
                  upstreamPayloadReferences(
                      sourceStep, discoveryStep, navigationStep, persistenceStep, frontendModule),
                  r4Controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  payloads));
      InstalledAnalysisStepPublication step =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      destinationRun, AnalysisStepKey.BUSINESS_FLOWS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(
                      sourceStep.reference(),
                      discoveryStep.reference(),
                      navigationStep.reference(),
                      persistenceStep.reference()),
                  r4Controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  payloads.stream().map(EntryEvidencePublisher::stepPayload).toList(),
                  null));
      ReopenedAnalysisStepPublication reopened = steps.reopen(step.reference());
      if (!reopened.reference().equals(step.reference())
          || !reopened.receipt().controls().equals(r4Controls)
          || reopened.semanticPayloads().size() != payloads.size()) {
        throw invalid();
      }
      return step.reference();
    } catch (RuntimeException failure) {
      if (failure instanceof CapacityExceededException
          || (failure instanceof IllegalArgumentException
              && "ENTRY_EVIDENCE_PUBLICATION_INVALID".equals(failure.getMessage()))) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  private List<CanonicalModulePayload> payloads(
      EntryEvidenceSet set, SelectedSourceBasis sourceBasis) {
    List<CanonicalModulePayload> payloads = new ArrayList<>();
    ObjectNode header = headerDocument(set.header(), sourceBasis);
    for (EntryEvidenceSet.Entry entry : set.entries()) {
      ObjectNode document = entryDocumentV1(entry, header);
      document.put("schemaVersion", ENTRY_SCHEMA);
      document.put("producer", PRODUCER);
      document.set("header", header.deepCopy());
      payloads.add(
          standalonePayload(entryFileName(entry.entryId()), ENTRY_TYPE, ENTRY_SCHEMA, document));
    }
    ObjectNode index = JsonNodeFactory.instance.objectNode();
    index.put("schemaVersion", INDEX_SCHEMA);
    index.put("producer", PRODUCER);
    index.set("header", header.deepCopy());
    var entries = index.putArray("entries");
    for (EntryEvidenceSet.Entry entry : set.entries()) {
      ObjectNode item = entries.addObject();
      item.put("entryId", entry.entryId());
      item.put("file", entryFileName(entry.entryId()));
      item.put("assemblyStatus", entry.assemblyStatus().name());
      item.put("route", entry.entry().route());
      item.set("methodCondition", MAPPER.valueToTree(entry.entry().methodCondition()));
      item.put("handlerFqn", entry.entry().handlerFqn());
      item.put("methodKey", entry.entry().methodKey());
      item.put("limitationCount", entry.limitations().size());
    }
    payloads.add(standalonePayload(INDEX_FILE, INDEX_TYPE, INDEX_SCHEMA, index));
    payloads.add(coveragePayload(set, header));
    payloads.sort(Comparator.comparing(CanonicalModulePayload::fileName, UTF8_ORDER));
    return List.copyOf(payloads);
  }

  private List<CanonicalModulePayload> payloadsV2(
      EntryEvidenceSet set, SelectedSourceBasis sourceBasis) {
    List<CanonicalModulePayload> payloads = new ArrayList<>();
    ObjectNode header = headerDocumentV2(set, sourceBasis);
    for (EntryEvidenceSet.Entry entry : set.entries()) {
      ObjectNode document = entryDocumentV2(entry, header);
      document.put("schemaVersion", V2_ENTRY_SCHEMA);
      document.put("producer", V2_PRODUCER);
      document.set("header", header.deepCopy());
      payloads.add(
          standalonePayload(entryFileName(entry.entryId()), ENTRY_TYPE, V2_ENTRY_SCHEMA, document));
    }
    ObjectNode index = JsonNodeFactory.instance.objectNode();
    index.put("schemaVersion", V2_INDEX_SCHEMA);
    index.put("producer", V2_PRODUCER);
    index.set("header", header.deepCopy());
    var entries = index.putArray("entries");
    for (EntryEvidenceSet.Entry entry : set.entries()) {
      ObjectNode item = entries.addObject();
      item.put("entryId", entry.entryId());
      item.put("file", entryFileName(entry.entryId()));
      item.put("assemblyStatus", entry.assemblyStatus().name());
      item.put("route", entry.entry().route());
      item.set("methodCondition", MAPPER.valueToTree(entry.entry().methodCondition()));
      item.put("handlerFqn", entry.entry().handlerFqn());
      item.put("methodKey", entry.entry().methodKey());
      item.put("limitationCount", entry.limitations().size());
    }
    payloads.add(standalonePayload(INDEX_FILE, INDEX_TYPE, V2_INDEX_SCHEMA, index));
    payloads.add(coveragePayloadV2(set, header));
    payloads.sort(Comparator.comparing(CanonicalModulePayload::fileName, UTF8_ORDER));
    return List.copyOf(payloads);
  }

  /**
   * Writes the self-contained entry contract explicitly instead of relying on a consumer to infer
   * its R0/R1/R2/R3 lineage from the adjacent directory header. The duplicated values are checked
   * by {@link EntryEvidenceReader} on every reopen.
   */
  private static ObjectNode entryDocumentV1(EntryEvidenceSet.Entry entry, ObjectNode header) {
    return entryDocument(entry, header, frontendDocumentV1(entry.frontend()));
  }

  private static ObjectNode entryDocumentV2(EntryEvidenceSet.Entry entry, ObjectNode header) {
    return entryDocument(entry, header, MAPPER.valueToTree(entry.frontend()));
  }

  private static ObjectNode entryDocument(
      EntryEvidenceSet.Entry entry, ObjectNode header, ObjectNode frontend) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("entryId", entry.entryId());
    document.set("entry", httpEntry(entry.entry()));
    document.put("assemblyStatus", entry.assemblyStatus().name());
    document.set("coverage", MAPPER.valueToTree(entry.coverage()));
    document.set("frontend", frontend);
    document.set("java", MAPPER.valueToTree(entry.java()));
    document.set("persistence", MAPPER.valueToTree(entry.persistence()));
    document.set("limitations", MAPPER.valueToTree(entry.limitations()));
    document.set("sourceBasis", header.get("sourceBasis").deepCopy());
    ObjectNode upstream = document.putObject("upstream");
    upstream.set("applicationDiscovery", header.get("applicationDiscovery").deepCopy());
    upstream.set("navigationPublication", header.get("navigationPublication").deepCopy());
    upstream.set("persistencePublication", header.get("persistencePublication").deepCopy());
    upstream.set("frontendPublication", header.get("frontendPublication").deepCopy());
    // Keep this explicit even though it is also a record component: an entry document is a
    // standalone wire contract, not an accidental Jackson projection of an implementation type.
    document.set("sourceRefs", MAPPER.valueToTree(entry.sourceRefs()));
    return document;
  }

  /** V1 remains a closed wire shape even though the in-memory DTO has additive v2 components. */
  private static ObjectNode frontendDocumentV1(EntryEvidenceSet.Frontend frontend) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    ArrayNode requestUses = document.putArray("requestUses");
    frontend.requestUses().forEach(use -> requestUses.add(requestUseDocumentV1(use)));
    ArrayNode candidateRequestUses = document.putArray("candidateRequestUses");
    frontend
        .candidateRequestUses()
        .forEach(use -> candidateRequestUses.add(requestUseDocumentV1(use)));
    document.set("units", MAPPER.valueToTree(frontend.units()));
    return document;
  }

  private static ObjectNode requestUseDocumentV1(EntryEvidenceSet.RequestUse use) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.set("request", MAPPER.valueToTree(use.request()));
    document.put("resolution", use.resolution().name());
    ArrayNode candidates = document.putArray("candidateEntryIds");
    use.candidateEntryIds().forEach(candidates::add);
    ArrayNode sourceUnitIds = document.putArray("sourceUnitIds");
    use.sourceUnitIds().forEach(sourceUnitIds::add);
    if (use.reason() == null) {
      document.putNull("reason");
    } else {
      document.put("reason", use.reason());
    }
    return document;
  }

  /**
   * The generic JSON mapper cannot represent {@link ImmutableBytes} source excerpt content. Keep
   * the established source-excerpt wire form so a standalone entry retains the actual route
   * evidence rather than an opaque implementation object.
   */
  private static ObjectNode httpEntry(
      org.sourceanalysis.app.analysis.discovery.HttpEntryPoint entry) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("entryId", entry.entryId().value());
    document.put("kind", entry.kind().name());
    document.put("protocol", entry.protocol());
    document.put("method", entry.method());
    ObjectNode methodCondition = document.putObject("methodCondition");
    methodCondition.put("kind", entry.methodCondition().kind().name());
    var methods = methodCondition.putArray("methods");
    entry.methodCondition().methods().forEach(methods::add);
    document.put("route", entry.route());
    var routeParts = document.putArray("routeParts");
    entry.routeParts().forEach(routeParts::add);
    document.put("handlerFqn", entry.handlerFqn());
    document.put("methodKey", entry.methodKey());
    ObjectNode methodRange = document.putObject("methodRange");
    methodRange.put("startOffsetUtf16", entry.methodRange().startOffsetUtf16());
    methodRange.put("lengthUtf16", entry.methodRange().lengthUtf16());
    methodRange.put("startLine", entry.methodRange().startLine());
    methodRange.put("endLine", entry.methodRange().endLine());
    var parameters = document.putArray("parameterNames");
    entry.parameterNames().forEach(parameters::add);
    var excerpts = document.putArray("routeSourceExcerpts");
    for (SourceExcerptV1 excerpt : entry.routeSourceExcerpts()) {
      excerpts.add(sourceExcerpt(excerpt));
    }
    return document;
  }

  /**
   * Mirrors the established source-excerpt wire contract without exposing {@link ImmutableBytes}.
   */
  private static ObjectNode sourceExcerpt(SourceExcerptV1 excerpt) {
    ObjectNode item = JsonNodeFactory.instance.objectNode();
    ObjectNode locator = item.putObject("locator");
    locator.put("fileId", excerpt.locator().fileId().value());
    locator.put("path", excerpt.locator().path());
    locator.put("startByte", excerpt.locator().startByte());
    locator.put("endByteExclusive", excerpt.locator().endByteExclusive());
    locator.put("startLine", excerpt.locator().startLine());
    locator.put("startColumn", excerpt.locator().startColumn());
    locator.put("endLine", excerpt.locator().endLine());
    locator.put("endColumn", excerpt.locator().endColumn());
    item.put("rawUtf8", new String(excerpt.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8));
    item.put("rawUtf8Sha256", excerpt.rawUtf8Sha256().value());
    return item;
  }

  /**
   * Uses the same explicit, path-free source-basis form as the v2 frontend index. Entry evidence
   * must retain the selected R0 basis itself, rather than substituting the weaker R0 receipt
   * reference held by its assembled-domain header.
   */
  private static ObjectNode sourceBasisNode(SelectedSourceBasis basis) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("kind", basis.kind().name());
    node.put("snapshotId", basis.snapshotId().value());
    node.put("effectiveScopeDigest", basis.effectiveScopeDigest().value());
    if (basis.kind() == SelectedSourceBasis.Kind.PREPARED_V1) {
      PreparedSourceReference prepared = basis.preparedSource();
      ObjectNode preparedNode = node.putObject("preparedSource");
      preparedNode.put("sourceVersionId", prepared.sourceVersionId().value());
      preparedNode.set("publication", publicationNode(prepared));
      preparedNode.set("schemaBundleRef", referenceNode(prepared.schemaBundleRef()));
      preparedNode.set("artifactPolicyRegistryRef", policyNode(prepared));
      node.putNull("legacyCapture");
    } else {
      SourceRegistrationReference legacy = basis.legacyCapture();
      ObjectNode legacyNode = node.putObject("legacyCapture");
      legacyNode.put("sourceRegistrationId", legacy.sourceRegistrationId().value());
      legacyNode.put("snapshotId", legacy.snapshotId());
      legacyNode.set("snapshotManifestRef", referenceNode(legacy.snapshotManifestRef()));
      legacyNode.set("captureReceiptRef", referenceNode(legacy.captureReceiptRef()));
      node.putNull("preparedSource");
    }
    return node;
  }

  private static ObjectNode headerDocument(
      EntryEvidenceSet.Header header, SelectedSourceBasis sourceBasis) {
    ObjectNode document = MAPPER.valueToTree(header);
    document.set("sourceBasis", sourceBasisNode(sourceBasis));
    return document;
  }

  private static ObjectNode headerDocumentV2(
      EntryEvidenceSet set, SelectedSourceBasis sourceBasis) {
    ObjectNode document = headerDocument(set.header(), sourceBasis);
    document.put("frontendPageContextCount", set.frontendPageContextCoverage().size());
    return document;
  }

  private static ObjectNode publicationNode(PreparedSourceReference prepared) {
    var publication = prepared.publication();
    ObjectNode node = MAPPER.createObjectNode();
    node.putObject("address")
        .put("runId", publication.address().runId().value())
        .put("analysisStepKey", publication.address().analysisStepKey().wireValue());
    node.put("analysisStepArtifactRoot", publication.analysisStepArtifactRoot().value());
    node.put("analysisStepReceiptId", publication.analysisStepReceiptId().value());
    node.put("analysisStepReceiptSha256", publication.analysisStepReceiptSha256().value());
    return node;
  }

  private static ObjectNode policyNode(PreparedSourceReference prepared) {
    return MAPPER
        .createObjectNode()
        .put("artifactId", prepared.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", prepared.artifactPolicyRegistryRef().sha256().value());
  }

  private static ObjectNode referenceNode(ArtifactReference reference) {
    return MAPPER
        .createObjectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private CanonicalModulePayload standalonePayload(
      String fileName, String artifactType, String schemaVersion, ObjectNode document) {
    // Standalone JSON has a self-describing identity. Unlike JSONL coverage, the canonical store
    // verifies these three top-level fields before it admits the module receipt.
    ObjectNode withoutArtifactId = document.deepCopy();
    withoutArtifactId.put("schemaVersion", schemaVersion);
    withoutArtifactId.put("artifactType", artifactType);
    String artifactId =
        modules
                .resolveArtifactPolicy(new ArtifactPolicyKey(artifactType, schemaVersion))
                .artifactIdPrefix()
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(json.encodeCanonical(withoutArtifactId).copyToByteArray())));
    withoutArtifactId.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(withoutArtifactId));
  }

  private CanonicalModulePayload coveragePayload(EntryEvidenceSet set, ObjectNode headerDocument) {
    StringBuilder content = new StringBuilder();
    ObjectNode header = JsonNodeFactory.instance.objectNode();
    header.put("schemaVersion", COVERAGE_SCHEMA);
    header.put("recordType", "HEADER");
    header.put("producer", PRODUCER);
    header.set("header", headerDocument.deepCopy());
    appendJsonLine(content, header);
    for (EntryEvidenceSet.FrontendCoverage coverage : set.frontendCoverage()) {
      ObjectNode line = JsonNodeFactory.instance.objectNode();
      line.put("schemaVersion", COVERAGE_SCHEMA);
      line.put("recordType", "REQUEST_COVERAGE");
      line.set("payload", coverageDocument(coverage));
      appendJsonLine(content, line);
    }
    ImmutableBytes bytes =
        ImmutableBytes.copyOf(content.toString().getBytes(StandardCharsets.UTF_8));
    return payload(
        COVERAGE_FILE,
        COVERAGE_TYPE,
        COVERAGE_SCHEMA,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes,
        "canonical-jsonl-artifact-id-v1");
  }

  /**
   * The v2 coverage wire retains page-context membership even for a uniquely matched request.
   * Version one deliberately compacts that case to an entry file, so its bytes and reader contract
   * remain unchanged.
   */
  private CanonicalModulePayload coveragePayloadV2(
      EntryEvidenceSet set, ObjectNode headerDocument) {
    StringBuilder content = new StringBuilder();
    ObjectNode header = JsonNodeFactory.instance.objectNode();
    header.put("schemaVersion", V2_COVERAGE_SCHEMA);
    header.put("recordType", "HEADER");
    header.put("producer", V2_PRODUCER);
    header.set("header", headerDocument.deepCopy());
    appendJsonLine(content, header);
    for (EntryEvidenceSet.FrontendCoverage coverage : set.frontendCoverage()) {
      ObjectNode line = JsonNodeFactory.instance.objectNode();
      line.put("schemaVersion", V2_COVERAGE_SCHEMA);
      line.put("recordType", "REQUEST_COVERAGE");
      line.set("payload", MAPPER.valueToTree(coverage));
      appendJsonLine(content, line);
    }
    for (EntryEvidenceSet.FrontendPageContextCoverage coverage :
        set.frontendPageContextCoverage()) {
      ObjectNode line = JsonNodeFactory.instance.objectNode();
      line.put("schemaVersion", V2_COVERAGE_SCHEMA);
      line.put("recordType", "PAGE_CONTEXT_COVERAGE");
      line.set("payload", MAPPER.valueToTree(coverage));
      appendJsonLine(content, line);
    }
    return payload(
        COVERAGE_FILE,
        COVERAGE_TYPE,
        V2_COVERAGE_SCHEMA,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(content.toString().getBytes(StandardCharsets.UTF_8)),
        "canonical-jsonl-artifact-id-v1");
  }

  /**
   * A uniquely included request is represented by its verified entry file, not a second copy of its
   * complete frontend evidence. All other resolutions retain the original request and source units
   * because the coverage line is their only durable destination.
   */
  private static ObjectNode coverageDocument(EntryEvidenceSet.FrontendCoverage coverage) {
    if (coverage.resolution()
            == org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord.Resolution
                .MATCHED_UNIQUE
        && coverage.includedEntryIds().size() == 1) {
      ObjectNode document = JsonNodeFactory.instance.objectNode();
      document.put("requestId", coverage.requestId());
      document.put("resolution", coverage.resolution().name());
      var candidates = document.putArray("entryIds");
      coverage.entryIds().forEach(candidates::add);
      var included = document.putArray("includedEntryIds");
      coverage.includedEntryIds().forEach(included::add);
      document.put("entryFile", entryFileName(coverage.includedEntryIds().get(0)));
      return document;
    }
    return coverageDocumentV1(coverage);
  }

  private static ObjectNode coverageDocumentV1(EntryEvidenceSet.FrontendCoverage coverage) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("requestId", coverage.requestId());
    document.set("request", MAPPER.valueToTree(coverage.request()));
    document.put("resolution", coverage.resolution().name());
    ArrayNode entries = document.putArray("entryIds");
    coverage.entryIds().forEach(entries::add);
    ArrayNode includedEntries = document.putArray("includedEntryIds");
    coverage.includedEntryIds().forEach(includedEntries::add);
    document.set("units", MAPPER.valueToTree(coverage.units()));
    if (coverage.reason() == null) {
      document.putNull("reason");
    } else {
      document.put("reason", coverage.reason());
    }
    return document;
  }

  private void appendJsonLine(StringBuilder content, ObjectNode line) {
    content.append(
        new String(json.encodeCanonical(line).copyToByteArray(), StandardCharsets.UTF_8));
    content.append('\n');
  }

  private CanonicalModulePayload payload(
      String fileName,
      String artifactType,
      String schemaVersion,
      CanonicalMediaType mediaType,
      ImmutableBytes bytes,
      String identityDomain) {
    String prefix =
        modules
            .resolveArtifactPolicy(new ArtifactPolicyKey(artifactType, schemaVersion))
            .artifactIdPrefix();
    ArtifactId artifactId =
        ArtifactId.parse(
            prefix
                + ":"
                + sha256(
                    concatenate(
                        frame(identityDomain),
                        frame(schemaVersion),
                        frame(artifactType),
                        frame(bytes.copyToByteArray()))));
    return new CanonicalModulePayload(
        fileName, artifactType, schemaVersion, artifactId, mediaType, bytes);
  }

  private static void requireBudget(List<CanonicalModulePayload> payloads, EntryEvidenceSet set) {
    long total = 0L;
    for (CanonicalModulePayload payload : payloads) {
      long size = payload.canonicalUtf8().size();
      if (ENTRY_TYPE.equals(payload.artifactType())
          && size > set.header().profile().maxEntryUtf8Bytes()) {
        String fileName = payload.fileName();
        String entryId =
            "entry:"
                + fileName.substring(
                    ENTRY_FILE_PREFIX.length(), fileName.length() - ".json".length());
        throw new CapacityExceededException(
            "ENTRY_EVIDENCE_ENTRY_BYTE_LIMIT_EXCEEDED",
            entryId,
            set.header().profile().maxEntryUtf8Bytes(),
            size);
      }
      total = Math.addExact(total, size);
    }
    if (total > set.header().profile().maxPublicationUtf8Bytes()) {
      throw new CapacityExceededException(
          "ENTRY_EVIDENCE_PUBLICATION_BYTE_LIMIT_EXCEEDED",
          "publication",
          set.header().profile().maxPublicationUtf8Bytes(),
          total);
    }
  }

  private static void requireTechnicalPredecessors(
      AnalysisRunId destinationRun,
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation,
      ReopenedAnalysisStepPublication persistence,
      ReopenedModulePublication frontend,
      ModulePublicationReference frontendReference,
      ArtifactControls frontendControls,
      ArtifactControls backendControls,
      ArtifactControls persistenceControls) {
    if (!frontend.reference().equals(frontendReference)
        || !(frontend.reference().address() instanceof AnalysisStepModuleAddress frontendAddress)
        || frontendAddress.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || frontendAddress.moduleNumber() != 6
        || !"frontend-http-discovery".equals(frontendAddress.moduleKey())
        || !frontend.receipt().controls().equals(frontendControls)
        || source.reference().address().runId().equals(discovery.reference().address().runId())
        || frontendAddress.runId().equals(source.reference().address().runId())
        || frontendAddress.runId().equals(discovery.reference().address().runId())
        || frontendAddress.runId().equals(persistence.reference().address().runId())
        || frontendAddress.runId().equals(destinationRun)
        || !discovery.reference().address().runId().equals(navigation.reference().address().runId())
        || persistence.reference().address().runId().equals(source.reference().address().runId())
        || persistence.reference().address().runId().equals(discovery.reference().address().runId())
        || destinationRun.equals(source.reference().address().runId())
        || destinationRun.equals(discovery.reference().address().runId())
        || destinationRun.equals(persistence.reference().address().runId())
        || !discovery.receipt().controls().equals(backendControls)
        || !navigation.receipt().controls().equals(backendControls)
        || !persistence.receipt().controls().equals(persistenceControls)
        || !discovery.receipt().upstreamAnalysisStepReferences().equals(List.of(source.reference()))
        || !navigation
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference()))
        || !persistence
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference(), navigation.reference()))) {
      throw invalid();
    }
  }

  /**
   * The entry directory may only describe the saved frontend denominator, never a request that a
   * caller constructed beside an otherwise valid module-6 receipt. This also makes a DISABLED index
   * observably empty rather than a vehicle for synthetic unmatched coverage.
   */
  private static void requireFrontendDenominator(EntryEvidenceSet set, FrontendHttpIndex index) {
    if (set.header().frontendStatus() != index.status()
        || !set.header().frontendFiles().equals(index.files())
        || !set.header().frontendDiagnostics().equals(index.diagnostics())) {
      throw invalid();
    }
    Map<String, FrontendHttpRequestRecord> requests = new LinkedHashMap<>();
    for (FrontendHttpRequestRecord request : index.requests()) {
      if (requests.putIfAbsent(request.requestId(), request) != null) {
        throw invalid();
      }
    }
    Map<String, EntryEvidenceSet.FrontendCoverage> coverageByRequest = new LinkedHashMap<>();
    for (EntryEvidenceSet.FrontendCoverage coverage : set.frontendCoverage()) {
      if (coverageByRequest.putIfAbsent(coverage.requestId(), coverage) != null
          || !coverage.request().equals(requests.get(coverage.requestId()))) {
        throw invalid();
      }
    }
    if (!coverageByRequest.keySet().equals(requests.keySet())) throw invalid();
    for (EntryEvidenceSet.Entry entry : set.entries()) {
      if (!usesSavedRequests(entry.frontend().requestUses(), requests)
          || !usesSavedRequests(entry.frontend().candidateRequestUses(), requests)) {
        throw invalid();
      }
    }
  }

  /**
   * Rehydrates the finite R1 v3 context collection into an otherwise valid assembled R4 set.
   * Membership is only the saved {@code context.requestIds()} relation; it is not a data-flow or
   * selection-to-save conclusion.
   */
  private static EntryEvidenceSet withFrontendPageContexts(
      EntryEvidenceSet set, FrontendHttpIndex index) {
    List<FrontendPageContext> pageContexts = List.copyOf(index.pageContexts());
    Map<String, List<String>> contextIdsByRequest = contextIdsByRequest(pageContexts);
    Map<String, FrontendPageContext> pageContextsById = pageContextsById(pageContexts);
    List<EntryEvidenceSet.Entry> entries =
        set.entries().stream()
            .map(
                entry ->
                    new EntryEvidenceSet.Entry(
                        entry.entryId(),
                        entry.entry(),
                        entry.assemblyStatus(),
                        entry.coverage(),
                        withFrontendPageContexts(
                            entry.frontend(), contextIdsByRequest, pageContextsById),
                        entry.java(),
                        entry.persistence(),
                        entry.sourceRefs(),
                        entry.limitations()))
            .toList();
    List<EntryEvidenceSet.FrontendCoverage> coverage =
        set.frontendCoverage().stream()
            .map(value -> withFrontendPageContexts(value, contextIdsByRequest))
            .toList();
    List<EntryEvidenceSet.FrontendPageContextCoverage> pageContextCoverage =
        withFrontendPageContextCoverage(
            set.frontendPageContextCoverage(), pageContexts, entries, coverage);
    return new EntryEvidenceSet(set.header(), entries, coverage, pageContextCoverage);
  }

  private static List<EntryEvidenceSet.FrontendPageContextCoverage> withFrontendPageContextCoverage(
      List<EntryEvidenceSet.FrontendPageContextCoverage> existing,
      List<FrontendPageContext> pageContexts,
      List<EntryEvidenceSet.Entry> entries,
      List<EntryEvidenceSet.FrontendCoverage> requestCoverage) {
    Map<String, EntryEvidenceSet.FrontendPageContextCoverage> existingById = new LinkedHashMap<>();
    Map<String, FrontendSourceUnits.Unit> availableUnits = new LinkedHashMap<>();
    for (EntryEvidenceSet.Entry entry : entries) {
      entry.frontend().units().forEach(unit -> putFrontendUnit(availableUnits, unit));
    }
    for (EntryEvidenceSet.FrontendCoverage coverage : requestCoverage) {
      coverage.units().forEach(unit -> putFrontendUnit(availableUnits, unit));
    }
    for (EntryEvidenceSet.FrontendPageContextCoverage coverage : existing) {
      if (existingById.putIfAbsent(coverage.contextId(), coverage) != null) {
        throw invalid();
      }
      coverage.units().forEach(unit -> putFrontendUnit(availableUnits, unit));
    }
    List<EntryEvidenceSet.FrontendPageContextCoverage> values = new ArrayList<>();
    for (FrontendPageContext context :
        pageContexts.stream()
            .sorted(Comparator.comparing(FrontendPageContext::contextId, UTF8_ORDER))
            .toList()) {
      boolean requestMembership = !context.requestIds().isEmpty();
      EntryEvidenceSet.FrontendPageContextCoverage expected =
          new EntryEvidenceSet.FrontendPageContextCoverage(
              context.contextId(),
              context,
              contextUnits(context, availableUnits),
              includedEntryIds(context.contextId(), entries),
              requestMembership
                  ? EntryEvidenceSet.FrontendPageContextCoverage.Disposition.REQUEST_MEMBERSHIP
                  : EntryEvidenceSet.FrontendPageContextCoverage.Disposition.NO_REQUEST_MEMBERSHIP,
              requestMembership ? null : "no saved HTTP request member");
      EntryEvidenceSet.FrontendPageContextCoverage actual =
          existingById.remove(context.contextId());
      if (actual != null && !actual.equals(expected)) {
        throw invalid();
      }
      values.add(expected);
    }
    if (!existingById.isEmpty()) {
      throw invalid();
    }
    return List.copyOf(values);
  }

  private static void putFrontendUnit(
      Map<String, FrontendSourceUnits.Unit> units, FrontendSourceUnits.Unit unit) {
    FrontendSourceUnits.Unit previous = units.putIfAbsent(unit.sourceUnitId(), unit);
    if (previous != null && !previous.equals(unit)) {
      throw invalid();
    }
  }

  private static List<FrontendSourceUnits.Unit> contextUnits(
      FrontendPageContext context, Map<String, FrontendSourceUnits.Unit> availableUnits) {
    Map<String, FrontendSourceUnits.Unit> selected = new LinkedHashMap<>();
    for (FrontendPageSourceUnit sourceUnit : context.sourceUnits()) {
      List<FrontendSourceUnits.Unit> matches =
          availableUnits.values().stream()
              .filter(unit -> sourceUnit.sourcePath().equals(unit.path()))
              .filter(unit -> sourceUnit.sourceSha256().equals(unit.sourceSha256()))
              .filter(unit -> sourceUnit.sourceUnitRange().equals(unit.sourceUnitRange()))
              .filter(unit -> sourceUnit.sourceUnitKind() == unit.sourceUnitKind())
              .toList();
      if (matches.size() != 1) {
        throw invalid();
      }
      FrontendSourceUnits.Unit prior =
          selected.putIfAbsent(matches.get(0).sourceUnitId(), matches.get(0));
      if (prior != null && !prior.equals(matches.get(0))) {
        throw invalid();
      }
    }
    return selected.values().stream()
        .sorted(Comparator.comparing(FrontendSourceUnits.Unit::sourceUnitId, UTF8_ORDER))
        .toList();
  }

  private static List<String> includedEntryIds(
      String contextId, List<EntryEvidenceSet.Entry> entries) {
    return entries.stream()
        .filter(
            entry ->
                entry.frontend().pageContexts().stream()
                    .anyMatch(context -> contextId.equals(context.contextId())))
        .map(EntryEvidenceSet.Entry::entryId)
        .sorted(UTF8_ORDER)
        .toList();
  }

  private static EntryEvidenceSet.Frontend withFrontendPageContexts(
      EntryEvidenceSet.Frontend frontend,
      Map<String, List<String>> contextIdsByRequest,
      Map<String, FrontendPageContext> pageContextsById) {
    List<EntryEvidenceSet.RequestUse> requestUses =
        withFrontendPageContexts(frontend.requestUses(), contextIdsByRequest);
    List<EntryEvidenceSet.RequestUse> candidateRequestUses =
        withFrontendPageContexts(frontend.candidateRequestUses(), contextIdsByRequest);
    List<FrontendPageContext> pageContexts =
        pageContextsFor(requestUses, candidateRequestUses, pageContextsById);
    if (!frontend.pageContexts().isEmpty() && !frontend.pageContexts().equals(pageContexts)) {
      throw invalid();
    }
    return new EntryEvidenceSet.Frontend(
        requestUses, candidateRequestUses, frontend.units(), pageContexts);
  }

  private static List<EntryEvidenceSet.RequestUse> withFrontendPageContexts(
      List<EntryEvidenceSet.RequestUse> uses, Map<String, List<String>> contextIdsByRequest) {
    return uses.stream()
        .map(
            use -> {
              List<String> contextIds =
                  contextIdsFor(use.request().requestId(), contextIdsByRequest);
              if (!use.pageContexts().isEmpty() && !use.pageContexts().equals(contextIds)) {
                throw invalid();
              }
              return new EntryEvidenceSet.RequestUse(
                  use.request(),
                  use.resolution(),
                  use.candidateEntryIds(),
                  use.sourceUnitIds(),
                  contextIds,
                  use.reason());
            })
        .toList();
  }

  private static EntryEvidenceSet.FrontendCoverage withFrontendPageContexts(
      EntryEvidenceSet.FrontendCoverage coverage, Map<String, List<String>> contextIdsByRequest) {
    List<String> contextIds = contextIdsFor(coverage.requestId(), contextIdsByRequest);
    if (!coverage.pageContexts().isEmpty() && !coverage.pageContexts().equals(contextIds)) {
      throw invalid();
    }
    return new EntryEvidenceSet.FrontendCoverage(
        coverage.requestId(),
        coverage.request(),
        coverage.resolution(),
        coverage.entryIds(),
        coverage.includedEntryIds(),
        coverage.units(),
        contextIds,
        coverage.reason());
  }

  private static Map<String, List<String>> contextIdsByRequest(
      List<FrontendPageContext> pageContexts) {
    Map<String, List<String>> values = new LinkedHashMap<>();
    for (FrontendPageContext context : pageContexts) {
      for (String requestId : context.requestIds()) {
        values.computeIfAbsent(requestId, ignored -> new ArrayList<>()).add(context.contextId());
      }
    }
    values.replaceAll((requestId, contextIds) -> contextIds.stream().sorted(UTF8_ORDER).toList());
    return Map.copyOf(values);
  }

  private static List<String> contextIdsFor(
      String requestId, Map<String, List<String>> contextIdsByRequest) {
    return contextIdsByRequest.getOrDefault(requestId, List.of());
  }

  private static Map<String, FrontendPageContext> pageContextsById(
      List<FrontendPageContext> pageContexts) {
    Map<String, FrontendPageContext> values = new LinkedHashMap<>();
    for (FrontendPageContext context : pageContexts) {
      if (values.putIfAbsent(context.contextId(), context) != null) {
        throw invalid();
      }
    }
    return Map.copyOf(values);
  }

  private static List<FrontendPageContext> pageContextsFor(
      List<EntryEvidenceSet.RequestUse> requestUses,
      List<EntryEvidenceSet.RequestUse> candidateRequestUses,
      Map<String, FrontendPageContext> pageContextsById) {
    Map<String, FrontendPageContext> selected = new LinkedHashMap<>();
    java.util.stream.Stream.concat(requestUses.stream(), candidateRequestUses.stream())
        .flatMap(use -> use.pageContexts().stream())
        .forEach(
            contextId -> {
              FrontendPageContext context = pageContextsById.get(contextId);
              if (context == null) {
                throw invalid();
              }
              selected.putIfAbsent(contextId, context);
            });
    return selected.values().stream()
        .sorted(Comparator.comparing(FrontendPageContext::contextId, UTF8_ORDER))
        .toList();
  }

  private static void requireFrontendPageContexts(EntryEvidenceSet set, FrontendHttpIndex index) {
    List<FrontendPageContext> pageContexts = List.copyOf(index.pageContexts());
    Map<String, List<String>> contextIdsByRequest = contextIdsByRequest(pageContexts);
    Map<String, FrontendPageContext> pageContextsById = pageContextsById(pageContexts);
    for (EntryEvidenceSet.Entry entry : set.entries()) {
      EntryEvidenceSet.Frontend frontend = entry.frontend();
      if (!frontend
              .pageContexts()
              .equals(
                  pageContextsFor(
                      frontend.requestUses(), frontend.candidateRequestUses(), pageContextsById))
          || !hasExpectedPageContexts(frontend.requestUses(), contextIdsByRequest)
          || !hasExpectedPageContexts(frontend.candidateRequestUses(), contextIdsByRequest)) {
        throw invalid();
      }
    }
    for (EntryEvidenceSet.FrontendCoverage coverage : set.frontendCoverage()) {
      if (!coverage
          .pageContexts()
          .equals(contextIdsFor(coverage.requestId(), contextIdsByRequest))) {
        throw invalid();
      }
    }
    Map<String, EntryEvidenceSet.FrontendPageContextCoverage> coverageByContext =
        new LinkedHashMap<>();
    for (EntryEvidenceSet.FrontendPageContextCoverage coverage :
        set.frontendPageContextCoverage()) {
      if (coverageByContext.putIfAbsent(coverage.contextId(), coverage) != null) {
        throw invalid();
      }
    }
    if (!coverageByContext.keySet().equals(pageContextsById.keySet())) {
      throw invalid();
    }
    for (FrontendPageContext context : pageContexts) {
      EntryEvidenceSet.FrontendPageContextCoverage coverage =
          coverageByContext.get(context.contextId());
      boolean requestMembership = !context.requestIds().isEmpty();
      if (coverage == null
          || !coverage.context().equals(context)
          || !coverage
              .includedEntryIds()
              .equals(includedEntryIds(context.contextId(), set.entries()))
          || coverage.disposition()
              != (requestMembership
                  ? EntryEvidenceSet.FrontendPageContextCoverage.Disposition.REQUEST_MEMBERSHIP
                  : EntryEvidenceSet.FrontendPageContextCoverage.Disposition.NO_REQUEST_MEMBERSHIP)
          || (!requestMembership && coverage.reason() == null)) {
        throw invalid();
      }
    }
  }

  private static boolean hasExpectedPageContexts(
      List<EntryEvidenceSet.RequestUse> uses, Map<String, List<String>> contextIdsByRequest) {
    return uses.stream()
        .allMatch(
            use ->
                use.pageContexts()
                    .equals(contextIdsFor(use.request().requestId(), contextIdsByRequest)));
  }

  private static boolean usesSavedRequests(
      List<EntryEvidenceSet.RequestUse> uses, Map<String, FrontendHttpRequestRecord> requests) {
    for (EntryEvidenceSet.RequestUse use : uses) {
      if (!use.request().equals(requests.get(use.request().requestId()))) {
        return false;
      }
    }
    return true;
  }

  private static ReopenedAnalysisStepPublication reopen(
      AnalysisStepPublicationReference reference,
      AnalysisStepKey expectedStep,
      CanonicalAnalysisStepArtifactStore store) {
    ReopenedAnalysisStepPublication reopened = store.reopen(reference);
    if (!reopened.reference().equals(reference)
        || reference.address().analysisStepKey() != expectedStep) {
      throw invalid();
    }
    return reopened;
  }

  private static List<ArtifactReference> upstreamPayloadReferences(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation,
      ReopenedAnalysisStepPublication persistence,
      ReopenedModulePublication frontend) {
    Map<String, ArtifactReference> values = new LinkedHashMap<>();
    java.util.stream.Stream.of(source, discovery, navigation, persistence)
        .flatMap(step -> step.semanticPayloads().stream())
        .forEach(
            payload ->
                putReference(
                    values, payload.descriptor().artifactId(), payload.descriptor().sha256()));
    frontend
        .payloads()
        .forEach(
            payload ->
                putReference(
                    values, payload.descriptor().artifactId(), payload.descriptor().sha256()));
    return values.values().stream()
        .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
        .toList();
  }

  private static void putReference(
      Map<String, ArtifactReference> values,
      ArtifactId artifactId,
      org.sourceanalysis.app.artifact.Sha256Digest sha256) {
    ArtifactReference value = new ArtifactReference(artifactId, sha256);
    ArtifactReference prior = values.putIfAbsent(artifactId.value(), value);
    if (prior != null && !prior.equals(value)) {
      throw invalid();
    }
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

  public static String entryFileName(String entryId) {
    if (entryId == null || !entryId.matches("entry:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("entry-evidence entry ID is invalid");
    }
    return ENTRY_FILE_PREFIX + entryId.substring("entry:".length()) + ".json";
  }

  /**
   * A resource failure that callers can report without manufacturing a successful Step05 receipt.
   */
  public static final class CapacityExceededException extends IllegalArgumentException {
    private final String code;
    private final String subject;
    private final long limit;
    private final long actual;

    private CapacityExceededException(String code, String subject, long limit, long actual) {
      super(code);
      this.code = code;
      this.subject = subject;
      this.limit = limit;
      this.actual = actual;
    }

    public String code() {
      return code;
    }

    public String subject() {
      return subject;
    }

    public long limit() {
      return limit;
    }

    public long actual() {
      return actual;
    }
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("ENTRY_EVIDENCE_PUBLICATION_INVALID");
  }

  private static IllegalArgumentException invalid(Throwable cause) {
    return new IllegalArgumentException("ENTRY_EVIDENCE_PUBLICATION_INVALID", cause);
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
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
    int size = 0;
    for (byte[] value : values) size = Math.addExact(size, value.length);
    ByteBuffer bytes = ByteBuffer.allocate(size);
    for (byte[] value : values) bytes.put(value);
    return bytes.array();
  }

  private static int compareUtf8(String first, String second) {
    return java.util.Arrays.compareUnsigned(
        first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
  }
}
