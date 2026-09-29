package org.sourceanalysis.app.analysis.material.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndexModulePublisher;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpRequestRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendWrapperCall;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialMarkdown;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialReader;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/**
 * Fresh-reopens Step 05 reading materials without selecting, parsing, or analyzing material again.
 */
public final class CodeReadingMaterialReader {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> V1_RECORD_TYPES = Set.of("HEADER", "PACKET", "ENTRY_COVERAGE");
  private static final Set<String> V2_RECORD_TYPES =
      Set.of("HEADER", "PACKET", "ENTRY_COVERAGE", "FRONTEND_COVERAGE");
  private static final Set<String> PRODUCERS =
      Set.of(
          CodeReadingMaterialPublisher.LEGACY_PRODUCER,
          CodeReadingMaterialPublisher.TECHNICAL_PRODUCER);

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;
  private final CanonicalAnalysisStepArtifactStore sourceSteps;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public CodeReadingMaterialReader(CanonicalAnalysisStepArtifactStore steps) {
    this(null, steps, steps);
  }

  /** Uses the source-preparation store only for exact technical R0 receipt validation. */
  public CodeReadingMaterialReader(
      CanonicalAnalysisStepArtifactStore steps, CanonicalAnalysisStepArtifactStore sourceSteps) {
    this(null, steps, sourceSteps);
  }

  /** Adds the module store required to fresh-reopen technical v2's R1 frontend index. */
  public CodeReadingMaterialReader(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      CanonicalAnalysisStepArtifactStore sourceSteps) {
    this.modules = modules;
    this.steps = Objects.requireNonNull(steps, "analysis step artifact store");
    this.sourceSteps = Objects.requireNonNull(sourceSteps, "source analysis-step artifact store");
  }

  /** Hydrates saved references through Step 03/04 readers without rebuilding material selection. */
  public CodeReadingMaterialSet reopen(AnalysisStepPublicationReference reference) {
    try {
      Objects.requireNonNull(reference, "code reading material publication");
      ReopenedAnalysisStepPublication material = steps.reopen(reference);
      if (!material.reference().equals(reference)
          || reference.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
          || material.semanticPayloads().size() != 1) {
        throw invalid();
      }
      VerifiedCanonicalPayload payload = material.semanticPayloads().get(0);
      String schemaVersion = requirePayloadDescriptor(payload);
      SavedMaterial saved = parse(payload.canonicalUtf8(), schemaVersion);
      ReopenedAnalysisStepPublication navigation =
          reopen(
              saved.header().navigationPublication().publication(), AnalysisStepKey.PROGRAM_GRAPHS);
      ReopenedAnalysisStepPublication persistence =
          reopen(saved.header().persistencePublication(), AnalysisStepKey.PROVEN_CODE_FACTS);

      if (CodeReadingMaterialPublisher.LEGACY_PRODUCER.equals(saved.producer())) {
        ReopenedAnalysisStepPublication source =
            reopen(
                saved.header().sourceInventory().publication(),
                AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
        ReopenedAnalysisStepPublication discovery =
            reopen(saved.discovery().publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
        requireUpstreamChain(material, source, discovery, navigation, persistence);
      }

      JavaCodeIndex javaIndex =
          new JavaCodeIndexReader(steps).reopen(saved.header().navigationPublication(), navigation);
      PersistenceMaterialIndex persistenceIndex =
          new PersistenceMaterialReader(steps)
              .reopen(saved.header().persistencePublication(), persistence);
      if (!saved.header().sourceSnapshotId().equals(javaIndex.snapshotId())
          || !saved.header().sourceSnapshotId().equals(persistenceIndex.header().sourceSnapshotId())
          || !saved
              .header()
              .navigationPublication()
              .equals(persistenceIndex.header().navigationPublication())) {
        throw invalid();
      }
      return hydrate(saved, javaIndex, persistenceIndex);
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "CODE_READING_MATERIAL_SET_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  /**
   * Fresh-reopens technical v2 against the complete selected R0 basis and its persisted R1
   * module-six frontend index. Historical material remains readable through {@link
   * #reopen(AnalysisStepPublicationReference)}; it cannot use this strict v2 path.
   */
  public CodeReadingMaterialSet reopenTechnical(
      AnalysisStepPublicationReference reference,
      AnalysisRunId expectedR3,
      SelectedSourceBasis expectedR0Basis,
      ApplicationDiscoveryReference expectedR1Discovery,
      ProgramGraphsReference expectedR1Navigation,
      AnalysisStepPublicationReference expectedR2Persistence,
      ArtifactControls r1Controls,
      ArtifactControls r2Controls,
      ArtifactControls r3Controls) {
    try {
      Objects.requireNonNull(expectedR0Basis, "complete R0 source basis");
      if (expectedR0Basis.kind() != SelectedSourceBasis.Kind.PREPARED_V1) {
        throw invalid();
      }
      return reopenTechnical(
          reference,
          expectedR3,
          new VerifiedSourceInventoryReference(expectedR0Basis.preparedSource().publication()),
          expectedR0Basis,
          expectedR1Discovery,
          expectedR1Navigation,
          expectedR2Persistence,
          r1Controls,
          r2Controls,
          r3Controls);
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "CODE_READING_MATERIAL_SET_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  private CodeReadingMaterialSet reopenTechnical(
      AnalysisStepPublicationReference reference,
      AnalysisRunId expectedR3,
      VerifiedSourceInventoryReference expectedR0,
      SelectedSourceBasis expectedR0Basis,
      ApplicationDiscoveryReference expectedR1Discovery,
      ProgramGraphsReference expectedR1Navigation,
      AnalysisStepPublicationReference expectedR2Persistence,
      ArtifactControls r1Controls,
      ArtifactControls r2Controls,
      ArtifactControls r3Controls) {
    try {
      Objects.requireNonNull(reference, "code reading material publication");
      Objects.requireNonNull(expectedR3, "R3 run ID");
      Objects.requireNonNull(expectedR0, "R0 source");
      Objects.requireNonNull(expectedR1Discovery, "R1 discovery");
      Objects.requireNonNull(expectedR1Navigation, "R1 navigation");
      Objects.requireNonNull(expectedR2Persistence, "R2 persistence");
      Objects.requireNonNull(r1Controls, "R1 controls");
      Objects.requireNonNull(r2Controls, "R2 controls");
      Objects.requireNonNull(r3Controls, "R3 controls");
      ReopenedAnalysisStepPublication source = sourceSteps.reopen(expectedR0.publication());
      ReopenedAnalysisStepPublication discovery = steps.reopen(expectedR1Discovery.publication());
      ReopenedAnalysisStepPublication navigation = steps.reopen(expectedR1Navigation.publication());
      ReopenedAnalysisStepPublication persistence = steps.reopen(expectedR2Persistence);
      ReopenedAnalysisStepPublication material = steps.reopen(reference);
      if (!source.reference().equals(expectedR0.publication())
          || source.reference().address().analysisStepKey()
              != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY
          || !discovery.reference().equals(expectedR1Discovery.publication())
          || discovery.reference().address().analysisStepKey()
              != AnalysisStepKey.APPLICATION_DISCOVERY
          || !navigation.reference().equals(expectedR1Navigation.publication())
          || navigation.reference().address().analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
          || !persistence.reference().equals(expectedR2Persistence)
          || persistence.reference().address().analysisStepKey()
              != AnalysisStepKey.PROVEN_CODE_FACTS
          || !material.reference().equals(reference)
          || reference.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
          || !reference.address().runId().equals(expectedR3)
          || !discovery.receipt().controls().equals(r1Controls)
          || !navigation.receipt().controls().equals(r1Controls)
          || !persistence.receipt().controls().equals(r2Controls)
          || !material.receipt().controls().equals(r3Controls)
          || source.reference().address().runId().equals(discovery.reference().address().runId())
          || !discovery
              .reference()
              .address()
              .runId()
              .equals(navigation.reference().address().runId())
          || persistence.reference().address().runId().equals(source.reference().address().runId())
          || persistence
              .reference()
              .address()
              .runId()
              .equals(discovery.reference().address().runId())
          || expectedR3.equals(source.reference().address().runId())
          || expectedR3.equals(discovery.reference().address().runId())
          || expectedR3.equals(persistence.reference().address().runId())
          || !discovery
              .receipt()
              .upstreamAnalysisStepReferences()
              .equals(List.of(source.reference()))
          || !navigation
              .receipt()
              .upstreamAnalysisStepReferences()
              .equals(List.of(source.reference(), discovery.reference()))
          || !persistence
              .receipt()
              .upstreamAnalysisStepReferences()
              .equals(List.of(source.reference(), discovery.reference(), navigation.reference()))
          || !material
              .receipt()
              .upstreamAnalysisStepReferences()
              .equals(
                  List.of(
                      source.reference(),
                      discovery.reference(),
                      navigation.reference(),
                      persistence.reference()))) {
        throw invalid();
      }
      if (material.semanticPayloads().size() != 1) {
        throw invalid();
      }
      VerifiedCanonicalPayload payload = material.semanticPayloads().get(0);
      String schemaVersion = requirePayloadDescriptor(payload);
      if (!CodeReadingMaterialPublisher.TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)) {
        throw invalid();
      }
      SavedMaterial saved = parse(payload.canonicalUtf8(), schemaVersion);
      if (!CodeReadingMaterialPublisher.TECHNICAL_PRODUCER.equals(saved.producer())
          || !saved.header().sourceInventory().equals(expectedR0)
          || !saved.header().navigationPublication().equals(expectedR1Navigation)
          || !saved.header().persistencePublication().equals(expectedR2Persistence)
          || !saved.discovery().equals(expectedR1Discovery)) {
        throw invalid();
      }
      JavaCodeIndex javaIndex =
          new JavaCodeIndexReader(steps).reopen(expectedR1Navigation, navigation);
      PersistenceMaterialIndex persistenceIndex =
          new PersistenceMaterialReader(steps, sourceSteps)
              .reopenTechnical(
                  expectedR2Persistence,
                  persistence.reference().address().runId(),
                  expectedR0,
                  expectedR1Discovery,
                  expectedR1Navigation,
                  r1Controls,
                  r2Controls);
      if (!saved.header().sourceSnapshotId().equals(javaIndex.snapshotId())
          || !saved.header().sourceSnapshotId().equals(persistenceIndex.header().sourceSnapshotId())
          || !saved
              .header()
              .navigationPublication()
              .equals(persistenceIndex.header().navigationPublication())) {
        throw invalid();
      }
      CodeReadingMaterialSet materialSet = hydrate(saved, javaIndex, persistenceIndex);
      if (expectedR0Basis != null) {
        FrontendHttpIndex frontendIndex =
            reopenFrontendIndex(
                saved.header().frontendPublication(),
                expectedR1Discovery.publication().address().runId(),
                expectedR0Basis,
                r1Controls);
        requireFrontendSelection(materialSet, frontendIndex);
      }
      return materialSet;
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "CODE_READING_MATERIAL_SET_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  private FrontendHttpIndex reopenFrontendIndex(
      ModulePublicationReference reference,
      AnalysisRunId expectedR1,
      SelectedSourceBasis expectedR0Basis,
      ArtifactControls r1Controls) {
    if (modules == null || reference == null) {
      throw invalid();
    }
    return new FrontendHttpIndexModulePublisher(modules)
        .reopen(reference, expectedR1, expectedR0Basis, r1Controls);
  }

  private static void requireFrontendSelection(
      CodeReadingMaterialSet materialSet, FrontendHttpIndex frontendIndex) {
    Map<String, FrontendHttpRequestRecord> requests = new LinkedHashMap<>();
    for (FrontendHttpRequestRecord request : frontendIndex.requests()) {
      putUnique(requests, request.requestId(), request);
    }
    Map<String, FrontendEntryLinkRecord> links = new LinkedHashMap<>();
    for (FrontendEntryLinkRecord link : frontendIndex.entryLinks()) {
      putUnique(links, link.requestId(), link);
    }
    Map<String, FrontendWrapperCall> wrappers = new LinkedHashMap<>();
    for (FrontendHttpRequestRecord request : frontendIndex.requests()) {
      for (FrontendWrapperCall wrapper : request.wrapperPath()) {
        String identity = frontendUnitIdentity(wrapper);
        FrontendWrapperCall previous = wrappers.putIfAbsent(identity, wrapper);
        if (previous != null && !previous.equals(wrapper)) {
          throw invalid();
        }
      }
    }

    Map<String, CodeReadingMaterialSet.FrontendCoverage> coverage = new LinkedHashMap<>();
    for (CodeReadingMaterialSet.FrontendCoverage value : materialSet.frontendCoverage()) {
      putUnique(coverage, value.requestId(), value);
    }
    if (!coverage.keySet().equals(requests.keySet())) {
      throw invalid();
    }

    Set<String> selectedRequests = new HashSet<>();
    for (CodeReadingMaterialSet.Packet packet : materialSet.packets()) {
      Map<String, FrontendSourceUnits.Unit> units = new LinkedHashMap<>();
      for (FrontendSourceUnits.Unit unit : packet.frontendSelection().sourceUnits()) {
        putUnique(units, unit.sourceUnitId(), unit);
        if (!unitMatchesWrapper(unit, wrappers.get(unit.sourceUnitId()))) {
          throw invalid();
        }
      }
      for (CodeReadingMaterialSet.FrontendRequestUse use :
          packet.frontendSelection().requestUses()) {
        if (!selectedRequests.add(use.requestId())
            || !use.request().equals(requests.get(use.requestId()))
            || !use.entryLink().equals(links.get(use.requestId()))
            || !units.containsKey(use.sourceUnitId())
            || coverage.get(use.requestId()).status()
                != CodeReadingMaterialSet.FrontendCoverage.Status.SELECTED) {
          throw invalid();
        }
      }
    }
    for (CodeReadingMaterialSet.FrontendCoverage value : coverage.values()) {
      if ((value.status() == CodeReadingMaterialSet.FrontendCoverage.Status.SELECTED)
          != selectedRequests.contains(value.requestId())) {
        throw invalid();
      }
    }
  }

  private static String frontendUnitIdentity(FrontendWrapperCall wrapper) {
    var range = wrapper.sourceUnitRange();
    return wrapper.sourcePath()
        + "@"
        + wrapper.sourceSha256()
        + ":"
        + range.startOffsetUtf16()
        + ":"
        + range.lengthUtf16()
        + ":"
        + wrapper.sourceUnitKind().name();
  }

  private static boolean unitMatchesWrapper(
      FrontendSourceUnits.Unit unit, FrontendWrapperCall wrapper) {
    return wrapper != null
        && unit.path().equals(wrapper.sourcePath())
        && unit.sourceSha256().equals(wrapper.sourceSha256())
        && unit.sourceUnitRange().equals(wrapper.sourceUnitRange())
        && unit.sourceUnitKind() == wrapper.sourceUnitKind();
  }

  private ReopenedAnalysisStepPublication reopen(
      AnalysisStepPublicationReference reference, AnalysisStepKey expectedStep) {
    ReopenedAnalysisStepPublication reopened = steps.reopen(reference);
    if (!reopened.reference().equals(reference)
        || reference.address().analysisStepKey() != expectedStep) {
      throw invalid();
    }
    return reopened;
  }

  private static String requirePayloadDescriptor(VerifiedCanonicalPayload payload) {
    if (!CodeReadingMaterialPublisher.FILE_NAME.equals(payload.descriptor().fileName())
        || !CodeReadingMaterialPublisher.ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
        || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_X_NDJSON) {
      throw invalid();
    }
    String schemaVersion = payload.descriptor().schemaVersion();
    if (!CodeReadingMaterialPublisher.SCHEMA_VERSION.equals(schemaVersion)
        && !CodeReadingMaterialPublisher.TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)) {
      throw invalid();
    }
    return schemaVersion;
  }

  private static void requireUpstreamChain(
      ReopenedAnalysisStepPublication material,
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation,
      ReopenedAnalysisStepPublication persistence) {
    if (!sameRun(source, discovery, navigation, persistence, material)
        || !sameControls(source, discovery, navigation, persistence, material)
        || !discovery.receipt().upstreamAnalysisStepReferences().equals(List.of(source.reference()))
        || !navigation
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference()))
        || !persistence
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference(), navigation.reference()))
        || !material
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(
                List.of(
                    source.reference(),
                    discovery.reference(),
                    navigation.reference(),
                    persistence.reference()))) {
      throw invalid();
    }
  }

  private static boolean sameRun(
      ReopenedAnalysisStepPublication first, ReopenedAnalysisStepPublication... rest) {
    return java.util.stream.Stream.of(rest)
        .allMatch(
            value ->
                first.reference().address().runId().equals(value.reference().address().runId()));
  }

  private static boolean sameControls(
      ReopenedAnalysisStepPublication first, ReopenedAnalysisStepPublication... rest) {
    return java.util.stream.Stream.of(rest)
        .allMatch(value -> first.receipt().controls().equals(value.receipt().controls()));
  }

  private CodeReadingMaterialSet hydrate(
      SavedMaterial saved, JavaCodeIndex javaIndex, PersistenceMaterialIndex persistenceIndex) {
    Map<String, EntrySeed> entries = entrySeeds(javaIndex);
    Map<String, EntryCodeContext.MethodCode> methods = methods(javaIndex);
    Map<EntryCallKey, EntryCodeContext.CallSite> calls = calls(javaIndex);
    PersistenceReferences persistence = PersistenceReferences.from(persistenceIndex);

    List<CodeReadingMaterialSet.Packet> packets = new ArrayList<>();
    for (SavedPacket packet : saved.packets()) {
      List<EntrySeed> packetEntries =
          packet.entryIds().stream().map(key -> required(entries, key)).toList();
      List<EntryCodeContext.MethodCode> packetMethods =
          packet.methodKeys().stream().map(key -> required(methods, key)).toList();
      List<CodeReadingMaterialSet.EntryCall> packetCalls =
          packet.callKeys().stream()
              .map(
                  key ->
                      new CodeReadingMaterialSet.EntryCall(
                          key.entryId(),
                          required(calls, new EntryCallKey(key.entryId(), key.callKey()))))
              .toList();
      CodeReadingMaterialSet.PersistenceSelection selection =
          new CodeReadingMaterialSet.PersistenceSelection(
              packet.persistence().resourcePaths().stream()
                  .map(key -> required(persistence.resources(), key))
                  .toList(),
              packet.persistence().statementRefs().stream()
                  .map(key -> required(persistence.statements(), key))
                  .toList(),
              packet.persistence().bindingMethodKeys().stream()
                  .map(key -> required(persistence.bindings(), key))
                  .toList(),
              packet.persistence().sqlAnalysisStatementRefs().stream()
                  .map(key -> required(persistence.sqlAnalyses(), key))
                  .toList(),
              packet.persistence().diagnosticKeys().stream()
                  .map(key -> required(persistence.diagnostics(), key))
                  .toList());
      CodeReadingMaterialSet.Packet hydrated =
          new CodeReadingMaterialSet.Packet(
              packet.packetId(),
              packetEntries,
              packetMethods,
              packetCalls,
              selection,
              packet.sourceReferences(),
              packet.unselectedUnits(),
              packet.limitations(),
              packet.selfContainedUtf8Bytes(),
              packet.frontendSelection());
      if (CodeReadingMaterialMarkdown.renderPacket(hydrated).getBytes(StandardCharsets.UTF_8).length
          != hydrated.selfContainedUtf8Bytes()) {
        throw invalid();
      }
      packets.add(hydrated);
    }
    Map<String, Set<String>> entriesByPacket = new LinkedHashMap<>();
    for (CodeReadingMaterialSet.Packet packet : packets) {
      entriesByPacket.put(
          packet.packetId(),
          packet.entries().stream()
              .map(EntrySeed::entryId)
              .collect(java.util.stream.Collectors.toSet()));
    }
    List<CodeReadingMaterialSet.EntryCoverage> coverage = new ArrayList<>();
    for (CodeReadingMaterialSet.EntryCoverage value : saved.coverage()) {
      required(entries, value.entryId());
      if (value.packetIds().stream()
          .anyMatch(
              packetId ->
                  !entriesByPacket.containsKey(packetId)
                      || !entriesByPacket.get(packetId).contains(value.entryId()))) {
        throw invalid();
      }
      coverage.add(value);
    }
    if (!coverage.stream()
        .map(CodeReadingMaterialSet.EntryCoverage::entryId)
        .collect(java.util.stream.Collectors.toSet())
        .equals(entries.keySet())) {
      throw invalid();
    }
    return new CodeReadingMaterialSet(saved.header(), packets, coverage, saved.frontendCoverage());
  }

  private SavedMaterial parse(ImmutableBytes bytes, String schemaVersion) {
    List<Line> lines = lines(bytes, schemaVersion);
    if (lines.isEmpty() || !"HEADER".equals(lines.get(0).type())) {
      throw invalid();
    }
    Line headerLine = only(lines, "HEADER");
    if (!"header".equals(headerLine.key())) {
      throw invalid();
    }
    Set<String> headerFields =
        new HashSet<>(
            Set.of(
                "producer",
                "sourceInventory",
                "applicationDiscovery",
                "navigationPublication",
                "persistencePublication",
                "sourceSnapshotId",
                "profile"));
    if (CodeReadingMaterialPublisher.TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)) {
      headerFields.add("frontendPublication");
    }
    requireFields(headerLine.payload(), headerFields);
    String producer = text(headerLine.payload(), "producer");
    if (!PRODUCERS.contains(producer)) {
      throw invalid();
    }
    if (CodeReadingMaterialPublisher.TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)
        != CodeReadingMaterialPublisher.TECHNICAL_PRODUCER.equals(producer)) {
      throw invalid();
    }
    CodeReadingMaterialSet.Header header =
        new CodeReadingMaterialSet.Header(
            convert(
                headerLine.payload().get("sourceInventory"),
                VerifiedSourceInventoryReference.class),
            convert(
                headerLine.payload().get("navigationPublication"), ProgramGraphsReference.class),
            convert(
                headerLine.payload().get("persistencePublication"),
                AnalysisStepPublicationReference.class),
            text(headerLine.payload(), "sourceSnapshotId"),
            convert(headerLine.payload().get("profile"), CodeReadingMaterialProfile.class),
            CodeReadingMaterialPublisher.TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)
                ? frontendPublication(headerLine.payload().get("frontendPublication"))
                : null);
    ApplicationDiscoveryReference discovery =
        convert(
            headerLine.payload().get("applicationDiscovery"), ApplicationDiscoveryReference.class);

    List<SavedPacket> packets = new ArrayList<>();
    List<CodeReadingMaterialSet.EntryCoverage> coverage = new ArrayList<>();
    List<CodeReadingMaterialSet.FrontendCoverage> frontendCoverage = new ArrayList<>();
    boolean seenCoverage = false;
    boolean seenFrontendCoverage = false;
    for (int index = 1; index < lines.size(); index++) {
      Line line = lines.get(index);
      if ("PACKET".equals(line.type())) {
        if (seenCoverage || seenFrontendCoverage) {
          throw invalid();
        }
        packets.add(parsePacket(line, schemaVersion));
      } else if ("ENTRY_COVERAGE".equals(line.type())) {
        if (seenFrontendCoverage) {
          throw invalid();
        }
        seenCoverage = true;
        CodeReadingMaterialSet.EntryCoverage value =
            convert(line.payload(), CodeReadingMaterialSet.EntryCoverage.class);
        if (!line.key().equals(value.entryId())) {
          throw invalid();
        }
        coverage.add(value);
      } else if ("FRONTEND_COVERAGE".equals(line.type())
          && CodeReadingMaterialPublisher.TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)) {
        seenFrontendCoverage = true;
        CodeReadingMaterialSet.FrontendCoverage value =
            convert(line.payload(), CodeReadingMaterialSet.FrontendCoverage.class);
        if (!line.key().equals(value.requestId())) {
          throw invalid();
        }
        frontendCoverage.add(value);
      } else {
        throw invalid();
      }
    }
    return new SavedMaterial(
        producer,
        header,
        discovery,
        List.copyOf(packets),
        List.copyOf(coverage),
        List.copyOf(frontendCoverage));
  }

  private SavedPacket parsePacket(Line line, String schemaVersion) {
    Set<String> packetFields =
        new HashSet<>(
            Set.of(
                "packetId",
                "entryIds",
                "methodKeys",
                "callKeys",
                "persistence",
                "sourceReferences",
                "unselectedUnits",
                "limitations",
                "selfContainedUtf8Bytes"));
    if (CodeReadingMaterialPublisher.TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)) {
      packetFields.add("frontendSelection");
    }
    requireFields(line.payload(), packetFields);
    String packetId = text(line.payload(), "packetId");
    if (!line.key().equals(packetId)) {
      throw invalid();
    }
    List<CallKey> callKeys = new ArrayList<>();
    for (JsonNode call : array(line.payload().get("callKeys"))) {
      if (!(call instanceof ObjectNode key)) {
        throw invalid();
      }
      requireFields(key, Set.of("entryId", "callKey"));
      callKeys.add(new CallKey(text(key, "entryId"), text(key, "callKey")));
    }
    return new SavedPacket(
        packetId,
        strings(line.payload().get("entryIds")),
        strings(line.payload().get("methodKeys")),
        List.copyOf(callKeys),
        persistence(line.payload().get("persistence")),
        convertList(
            line.payload().get("sourceReferences"), CodeReadingMaterialSet.SourceReference.class),
        convertList(
            line.payload().get("unselectedUnits"), CodeReadingMaterialSet.UnselectedUnit.class),
        strings(line.payload().get("limitations")),
        nonNegativeLong(line.payload().get("selfContainedUtf8Bytes")),
        CodeReadingMaterialPublisher.TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)
            ? convert(
                line.payload().get("frontendSelection"),
                CodeReadingMaterialSet.FrontendSelection.class)
            : CodeReadingMaterialSet.FrontendSelection.empty());
  }

  private static SavedPersistence persistence(JsonNode value) {
    if (!(value instanceof ObjectNode node)) {
      throw invalid();
    }
    requireFields(
        node,
        Set.of(
            "resourcePaths",
            "statementRefs",
            "bindingMethodKeys",
            "sqlAnalysisStatementRefs",
            "diagnosticKeys"));
    return new SavedPersistence(
        strings(node.get("resourcePaths")),
        strings(node.get("statementRefs")),
        strings(node.get("bindingMethodKeys")),
        strings(node.get("sqlAnalysisStatementRefs")),
        strings(node.get("diagnosticKeys")));
  }

  private static ModulePublicationReference frontendPublication(JsonNode value) {
    if (!(value instanceof ObjectNode node)
        || !fields(node)
            .equals(
                Set.of("address", "moduleArtifactRoot", "moduleReceiptId", "moduleReceiptSha256"))
        || !(node.get("address") instanceof ObjectNode address)
        || !fields(address)
            .equals(Set.of("kind", "runId", "analysisStepKey", "moduleNumber", "moduleKey"))
        || !"ANALYSIS_STEP".equals(text(address, "kind"))
        || !address.get("moduleNumber").canConvertToInt()) {
      throw invalid();
    }
    try {
      AnalysisStepModuleAddress moduleAddress =
          new AnalysisStepModuleAddress(
              AnalysisRunId.parse(text(address, "runId")),
              AnalysisStepKey.parse(text(address, "analysisStepKey")),
              address.get("moduleNumber").intValue(),
              text(address, "moduleKey"));
      if (moduleAddress.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
          || moduleAddress.moduleNumber() != 6
          || !"frontend-http-discovery".equals(moduleAddress.moduleKey())) {
        throw invalid();
      }
      return new ModulePublicationReference(
          moduleAddress,
          ModuleArtifactRoot.parse(text(node, "moduleArtifactRoot")),
          ModuleReceiptId.parse(text(node, "moduleReceiptId")),
          org.sourceanalysis.app.artifact.Sha256Digest.parse(text(node, "moduleReceiptSha256")));
    } catch (IllegalArgumentException failure) {
      throw invalid();
    }
  }

  private List<Line> lines(ImmutableBytes bytes, String schemaVersion) {
    String content = new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
    if (content.isEmpty() || !content.endsWith("\n")) {
      throw invalid();
    }
    List<Line> result = new ArrayList<>();
    Set<String> identities = new HashSet<>();
    for (String text : content.substring(0, content.length() - 1).split("\n", -1)) {
      if (text.isBlank()) {
        throw invalid();
      }
      JsonNode parsed =
          json.parseCanonical(ImmutableBytes.copyOf(text.getBytes(StandardCharsets.UTF_8)));
      if (!(parsed instanceof ObjectNode line)) {
        throw invalid();
      }
      requireFields(line, Set.of("schemaVersion", "recordType", "key", "payload"));
      String type = text(line, "recordType");
      String key = text(line, "key");
      if (!schemaVersion.equals(text(line, "schemaVersion"))
          || !recordTypes(schemaVersion).contains(type)
          || !(line.get("payload") instanceof ObjectNode payload)
          || !identities.add(type + "\u0000" + key)) {
        throw invalid();
      }
      result.add(new Line(type, key, payload));
    }
    return List.copyOf(result);
  }

  private static Set<String> recordTypes(String schemaVersion) {
    return CodeReadingMaterialPublisher.SCHEMA_VERSION.equals(schemaVersion)
        ? V1_RECORD_TYPES
        : V2_RECORD_TYPES;
  }

  private static Map<String, EntrySeed> entrySeeds(JavaCodeIndex index) {
    Map<String, EntrySeed> result = new LinkedHashMap<>();
    for (JavaCodeIndex.EntryCollection entry : index.entries()) {
      putUnique(result, entry.seed().entryId(), entry.seed());
    }
    return result;
  }

  private static Map<String, EntryCodeContext.MethodCode> methods(JavaCodeIndex index) {
    Map<String, EntryCodeContext.MethodCode> result = new LinkedHashMap<>();
    for (JavaCodeIndex.EntryCollection entry : index.entries()) {
      if (entry.context() == null) {
        continue;
      }
      for (EntryCodeContext.MethodCode method : entry.context().methods()) {
        putConsistent(result, method.methodKey(), method);
      }
    }
    return result;
  }

  private static Map<EntryCallKey, EntryCodeContext.CallSite> calls(JavaCodeIndex index) {
    Map<EntryCallKey, EntryCodeContext.CallSite> result = new HashMap<>();
    for (JavaCodeIndex.EntryCollection entry : index.entries()) {
      if (entry.context() == null) {
        continue;
      }
      for (EntryCodeContext.CallSite call : entry.context().calls()) {
        putConsistent(result, new EntryCallKey(entry.seed().entryId(), call.callKey()), call);
      }
    }
    return result;
  }

  private static <K, V> void putUnique(Map<K, V> values, K key, V value) {
    if (values.put(key, value) != null) {
      throw invalid();
    }
  }

  private static <K, V> void putConsistent(Map<K, V> values, K key, V value) {
    V previous = values.putIfAbsent(key, value);
    if (previous != null && !previous.equals(value)) {
      throw invalid();
    }
  }

  private static <K, V> V required(Map<K, V> values, K key) {
    V value = values.get(key);
    if (value == null) {
      throw invalid();
    }
    return value;
  }

  private static Line only(List<Line> lines, String type) {
    List<Line> matches = lines.stream().filter(line -> type.equals(line.type())).toList();
    if (matches.size() != 1) {
      throw invalid();
    }
    return matches.get(0);
  }

  private static void requireFields(ObjectNode node, Set<String> expected) {
    if (!fields(node).equals(expected)) {
      throw invalid();
    }
  }

  private static Set<String> fields(ObjectNode node) {
    Set<String> actual = new HashSet<>();
    node.fieldNames().forEachRemaining(actual::add);
    return actual;
  }

  private static String text(ObjectNode node, String name) {
    JsonNode value = node.get(name);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw invalid();
    }
    return value.textValue();
  }

  private static ArrayNode array(JsonNode value) {
    if (!(value instanceof ArrayNode array)) {
      throw invalid();
    }
    return array;
  }

  private static List<String> strings(JsonNode value) {
    List<String> result = new ArrayList<>();
    for (JsonNode item : array(value)) {
      if (!item.isTextual() || item.textValue().isBlank()) {
        throw invalid();
      }
      result.add(item.textValue());
    }
    return List.copyOf(result);
  }

  private static long nonNegativeLong(JsonNode value) {
    if (value == null
        || !value.isIntegralNumber()
        || !value.canConvertToLong()
        || value.longValue() < 0L) {
      throw invalid();
    }
    return value.longValue();
  }

  private static <T> T convert(JsonNode value, Class<T> type) {
    try {
      return MAPPER.treeToValue(value, type);
    } catch (JsonProcessingException | IllegalArgumentException failure) {
      throw invalid();
    }
  }

  private static <T> List<T> convertList(JsonNode value, Class<T> type) {
    List<T> result = new ArrayList<>();
    for (JsonNode item : array(value)) {
      result.add(convert(item, type));
    }
    return List.copyOf(result);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("CODE_READING_MATERIAL_SET_INVALID");
  }

  private static IllegalArgumentException invalid(Throwable cause) {
    return new IllegalArgumentException("CODE_READING_MATERIAL_SET_INVALID", cause);
  }

  private record Line(String type, String key, ObjectNode payload) {}

  private record EntryCallKey(String entryId, String callKey) {}

  private record CallKey(String entryId, String callKey) {}

  private record SavedPersistence(
      List<String> resourcePaths,
      List<String> statementRefs,
      List<String> bindingMethodKeys,
      List<String> sqlAnalysisStatementRefs,
      List<String> diagnosticKeys) {}

  private record SavedPacket(
      String packetId,
      List<String> entryIds,
      List<String> methodKeys,
      List<CallKey> callKeys,
      SavedPersistence persistence,
      List<CodeReadingMaterialSet.SourceReference> sourceReferences,
      List<CodeReadingMaterialSet.UnselectedUnit> unselectedUnits,
      List<String> limitations,
      long selfContainedUtf8Bytes,
      CodeReadingMaterialSet.FrontendSelection frontendSelection) {}

  private record SavedMaterial(
      String producer,
      CodeReadingMaterialSet.Header header,
      ApplicationDiscoveryReference discovery,
      List<SavedPacket> packets,
      List<CodeReadingMaterialSet.EntryCoverage> coverage,
      List<CodeReadingMaterialSet.FrontendCoverage> frontendCoverage) {}

  private record PersistenceReferences(
      Map<String, PersistenceMaterialIndex.Resource> resources,
      Map<String, PersistenceMaterialIndex.Statement> statements,
      Map<String, PersistenceMaterialIndex.JavaBinding> bindings,
      Map<String, PersistenceMaterialIndex.SqlAnalysis> sqlAnalyses,
      Map<String, PersistenceMaterialIndex.Diagnostic> diagnostics) {

    private static PersistenceReferences from(PersistenceMaterialIndex index) {
      return new PersistenceReferences(
          map(index.resources(), PersistenceMaterialIndex.Resource::resourcePath),
          map(index.statements(), PersistenceMaterialIndex.Statement::statementRef),
          map(index.bindings(), PersistenceMaterialIndex.JavaBinding::methodKey),
          map(index.sqlAnalyses(), PersistenceMaterialIndex.SqlAnalysis::statementRef),
          map(index.diagnostics(), CodeReadingMaterialPublisher::diagnosticKey));
    }

    private static <T> Map<String, T> map(List<T> values, Function<T, String> key) {
      Map<String, T> result = new LinkedHashMap<>();
      for (T value : values) {
        putUnique(result, key.apply(value), value);
      }
      return result;
    }
  }
}
