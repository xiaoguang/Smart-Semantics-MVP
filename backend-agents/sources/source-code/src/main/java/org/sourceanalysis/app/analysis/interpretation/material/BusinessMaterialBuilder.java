package org.sourceanalysis.app.analysis.interpretation.material;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Converts persisted technical Flow/Capsule output into short, coherent model-reading packets.
 *
 * <p>The builder deliberately does not infer a business purpose, actor, or business sequence. It
 * verifies source excerpts against the frozen source reader, preserves program-side locations, and
 * removes paths, hashes, and Proof detail from the companion model packet.
 */
public final class BusinessMaterialBuilder {

  private static final String FILE_NAME = "business-materials.jsonl";
  private static final String ARTIFACT_TYPE = "FLOW_INTERPRETATION_BUSINESS_MATERIAL";
  private static final String SCHEMA_VERSION = "flow-interpretation-business-material-v1";
  private static final String ARTIFACT_PREFIX = "business-materials";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> MODEL_SAFE_ATOM_ROLES =
      Set.of("STATIC_TARGET_TYPE", "STATIC_TARGET_METHOD", "STATIC_TARGET_SIGNATURE");
  private static final Comparator<String> UTF8_ORDER = BusinessMaterialBuilder::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the sole source-to-material seam; callers cannot pass filesystem paths. */
  public BusinessMaterialBuilder(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps,
      VerifiedSourceTextReader sourceReader) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
  }

  /** Builds and persists all readable material before any model-provider call can occur. */
  public BusinessMaterialBuildResult build(BuildBusinessMaterialsRequest request) {
    try {
      Objects.requireNonNull(request, "business material request");
      ReopenedAnalysisStepPublication flows = reopenFlows(request.businessFlows());
      VerifiedSourceTextSet source = reopenSource(flows);
      MaterialInput input = materialInput(flows, source);
      BusinessMaterialSet materialSet = materialSet(input, request.profile());
      ModulePublicationReference checkpoint = install(flows, materialSet);
      return new BusinessMaterialBuildResult(materialSet, checkpoint);
    } catch (BusinessMaterialException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID", failure);
    }
  }

  private ReopenedAnalysisStepPublication reopenFlows(BusinessFlowsReference reference) {
    ReopenedAnalysisStepPublication flows = analysisSteps.reopen(reference.publication());
    if (!reference.publication().equals(flows.reference())
        || flows.reference().address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
        || flows.semanticPayloads().size() != 5) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    return flows;
  }

  private VerifiedSourceTextSet reopenSource(ReopenedAnalysisStepPublication flows) {
    AnalysisStepPublicationReference sourcePublication =
        flows.receipt().upstreamAnalysisStepReferences().stream()
            .filter(
                value ->
                    value.address().analysisStepKey() == AnalysisStepKey.VERIFIED_SOURCE_INVENTORY)
            .findFirst()
            .orElseThrow(() -> failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID"));
    return sourceReader.reopen(new VerifiedSourceInventoryReference(sourcePublication));
  }

  private MaterialInput materialInput(
      ReopenedAnalysisStepPublication flows, VerifiedSourceTextSet source) {
    Map<String, VerifiedCanonicalPayload> payloads = byFileName(flows.semanticPayloads());
    VerifiedCanonicalPayload dispositions = payloads.get("entry-dispositions.jsonl");
    VerifiedCanonicalPayload capsules = payloads.get("evidence-capsules.jsonl");
    VerifiedCanonicalPayload flowSlices = payloads.get("flow-slices.json");
    if (dispositions == null || capsules == null || flowSlices == null) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    List<EntryDisposition> entries = entryDispositions(dispositions);
    Map<String, Capsule> capsuleByEntry = capsules(capsules, source);
    Map<String, EntryMetadata> metadata = entryMetadataFromFlows(flows);
    Map<String, EntryContext> entryContexts = entryContexts(flowSlices);
    if (!entryContexts
        .keySet()
        .equals(
            entries.stream()
                .map(EntryDisposition::entryId)
                .collect(java.util.stream.Collectors.toSet()))) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    return new MaterialInput(entries, capsuleByEntry, metadata, entryContexts, source);
  }

  private BusinessMaterialSet materialSet(MaterialInput input, BusinessMaterialProfile profile) {
    SourceRefAllocator allocator = new SourceRefAllocator();
    List<EntryMaterial> entryMaterials = new ArrayList<>();
    List<BusinessMaterialEntryCoverage> coverage = new ArrayList<>();
    for (EntryDisposition entry : input.entries()) {
      Capsule capsule = input.capsuleByEntry().get(entry.entryId());
      if (!"COMPILED".equals(entry.disposition()) || capsule == null) {
        EntryContext entryContext = input.entryContextsByEntryId().get(entry.entryId());
        BusinessMaterial fallback =
            entryContext == null
                ? null
                : fallbackMaterialFromPersistedContext(
                    entry,
                    input.entryMetadata().get(entry.entryId()),
                    entryContext,
                    input.source(),
                    allocator,
                    profile);
        if (fallback == null) {
          coverage.add(
              new BusinessMaterialEntryCoverage(
                  entry.entryId(), "NOT_MATERIALIZED", null, "FLOW_NOT_COMPILED"));
        } else {
          entryMaterials.add(
              new EntryMaterial(
                  entry,
                  input.entryMetadata().get(entry.entryId()),
                  fallback,
                  "MATERIAL_WITH_GAPS",
                  "ENTRY_SOURCE_FALLBACK"));
        }
        continue;
      }
      MaterialCandidate candidate = materialCandidate(entry, capsule, allocator, profile);
      if (candidate == null) {
        coverage.add(
            new BusinessMaterialEntryCoverage(
                entry.entryId(), "NOT_MATERIALIZED", null, "SOURCE_MATERIAL_OVER_BUDGET"));
        continue;
      }
      entryMaterials.add(
          new EntryMaterial(
              entry,
              input.entryMetadata().get(entry.entryId()),
              candidate.material(),
              candidate.material().hasSubstantiveLimitation()
                  ? "MATERIAL_WITH_GAPS"
                  : "ANALYZED_MATERIAL",
              candidate.reasonCode()));
    }
    List<GroupedMaterial> grouped = groupMaterials(entryMaterials, profile);
    List<BusinessMaterial> materials = new ArrayList<>();
    for (GroupedMaterial group : grouped) {
      materials.add(group.material());
      for (EntryMaterial member : group.members()) {
        coverage.add(
            new BusinessMaterialEntryCoverage(
                member.entry().entryId(),
                group.material().hasSubstantiveLimitation()
                    ? "MATERIAL_WITH_GAPS"
                    : member.coverageDisposition(),
                group.material().materialId(),
                member.reasonCode()));
      }
    }
    materials.sort(Comparator.comparing(BusinessMaterial::materialId, UTF8_ORDER));
    coverage.sort(Comparator.comparing(BusinessMaterialEntryCoverage::entryId, UTF8_ORDER));
    String setId =
        "business-material-set:"
            + sha256(frame("business-material-set-v1"), frame(jsonl(materials, coverage)));
    return new BusinessMaterialSet(setId, materials, coverage);
  }

  /**
   * Groups already-built entry context packets only to reduce repeated model reading.
   *
   * <p>A group is bounded by handler class, material mode, entry count, source-reference budget,
   * and final packet size. It deliberately does not claim a business purpose, sequence, shared
   * identity, or process; those remain model work in the next Module.
   */
  private List<GroupedMaterial> groupMaterials(
      List<EntryMaterial> entryMaterials, BusinessMaterialProfile profile) {
    Map<String, List<EntryMaterial>> byStructure = new LinkedHashMap<>();
    entryMaterials.stream()
        .sorted(Comparator.comparing(value -> value.entry().entryId(), UTF8_ORDER))
        .forEach(
            entry ->
                byStructure
                    .computeIfAbsent(groupingKey(entry), ignored -> new ArrayList<>())
                    .add(entry));
    List<GroupedMaterial> groups = new ArrayList<>();
    for (List<EntryMaterial> sameStructure : byStructure.values()) {
      List<EntryMaterial> current = new ArrayList<>();
      for (EntryMaterial next : sameStructure) {
        List<EntryMaterial> trial = new ArrayList<>(current);
        trial.add(next);
        BusinessMaterial combined = combinedMaterial(trial, profile);
        if (combined != null) {
          current = trial;
          continue;
        }
        if (current.isEmpty()) {
          groups.add(new GroupedMaterial(next.material(), List.of(next)));
          continue;
        }
        groups.add(
            new GroupedMaterial(
                combinedMaterialOrOriginal(current, profile), List.copyOf(current)));
        current = new ArrayList<>(List.of(next));
      }
      if (!current.isEmpty()) {
        groups.add(
            new GroupedMaterial(
                combinedMaterialOrOriginal(current, profile), List.copyOf(current)));
      }
    }
    return groups.stream()
        .sorted(Comparator.comparing(value -> value.material().materialId(), UTF8_ORDER))
        .toList();
  }

  private static String groupingKey(EntryMaterial entry) {
    if (entry.material().materialMode() == BusinessMaterialMode.NAVIGATED_SOURCE) {
      return "navigated-entry\u0000" + entry.entry().entryId();
    }
    String handler = entry.metadata() == null ? null : entry.metadata().handlerFqn();
    if (handler == null || handler.isBlank()) {
      return "entry\u0000" + entry.entry().entryId();
    }
    int methodSeparator = handler.indexOf('#');
    String owner = methodSeparator < 0 ? handler : handler.substring(0, methodSeparator);
    return "handler\u0000" + owner + "\u0000" + entry.material().materialMode().name();
  }

  private static BusinessMaterial combinedMaterialOrOriginal(
      List<EntryMaterial> members, BusinessMaterialProfile profile) {
    BusinessMaterial combined = combinedMaterial(members, profile);
    if (combined != null) {
      return combined;
    }
    if (members.size() == 1) {
      return members.get(0).material();
    }
    throw failure("BUSINESS_MATERIAL_GROUPING_INVALID");
  }

  private static BusinessMaterial combinedMaterial(
      List<EntryMaterial> members, BusinessMaterialProfile profile) {
    if (members.isEmpty()
        || members.size() > profile.maxEntriesPerMaterial()
        || members.size() > profile.maxSourceRefsPerMaterial()) {
      return null;
    }
    if (members.size() == 1) {
      return members.get(0).material();
    }
    BusinessMaterialMode mode = members.get(0).material().materialMode();
    if (members.stream().anyMatch(member -> member.material().materialMode() != mode)) {
      return null;
    }
    List<String> entryIds =
        members.stream().map(member -> member.entry().entryId()).sorted(UTF8_ORDER).toList();
    List<SourceReference> references =
        selectedGroupReferences(members, profile.maxSourceRefsPerMaterial());
    if (references.size() < members.size()) {
      return null;
    }
    List<String> observations =
        distinctStrings(
            members.stream().flatMap(member -> member.material().technicalObservations().stream()));
    List<String> limitations =
        distinctStrings(
            members.stream().flatMap(member -> member.material().limitations().stream()));
    List<String> flowRefs =
        distinctStrings(members.stream().flatMap(member -> member.material().flowRefs().stream()));
    List<String> proofRefs =
        distinctStrings(
            members.stream().flatMap(member -> member.material().technicalProofRefs().stream()));
    String context =
        "本材料包包括 "
            + members.size()
            + " 个相关 HTTP 入口：\n"
            + members.stream()
                .map(member -> member.material().context())
                .collect(java.util.stream.Collectors.joining("\n"));
    String modelContext =
        "本包包含以下相关 HTTP 入口：\n"
            + java.util.stream.IntStream.range(0, members.size())
                .mapToObj(
                    index ->
                        "入口 E"
                            + (index + 1)
                            + "：\n"
                            + members.get(index).material().modelPacket().context())
                .collect(java.util.stream.Collectors.joining("\n"))
            + "\n请分别解释每个入口的局部业务活动；只有片段明确支持时才说明它们之间的关系。";
    ModelActivityPacket packet =
        new ModelActivityPacket(
            modelContext,
            modelObservations(observations),
            references.stream()
                .map(
                    reference ->
                        new ModelActivityPacket.AllowlistedReference(
                            reference.ref(), reference.snippet()))
                .toList(),
            modelLimitations(limitations));
    if (packetCharacterCount(packet) > profile.maxMaterialChars()) {
      return null;
    }
    String materialId =
        "material:"
            + sha256(
                frame("business-material-group-v1"),
                frame(String.join("\u0000", entryIds)),
                frame(String.join("\u0000", flowRefs)));
    return new BusinessMaterial(
        materialId,
        entryIds,
        mode,
        context,
        observations,
        references,
        flowRefs,
        proofRefs,
        limitations,
        packet);
  }

  private static List<SourceReference> selectedGroupReferences(
      List<EntryMaterial> members, int maximum) {
    LinkedHashMap<String, SourceReference> selected = new LinkedHashMap<>();
    members.stream()
        .flatMap(member -> essentialReferences(member.material().sourceRefs()).stream())
        .sorted(Comparator.comparing(SourceReference::ref, UTF8_ORDER))
        .forEach(reference -> selected.putIfAbsent(reference.ref(), reference));
    if (selected.size() > maximum) {
      return List.of();
    }
    return selected.values().stream()
        .sorted(Comparator.comparing(SourceReference::ref, UTF8_ORDER))
        .toList();
  }

  /**
   * Retains every distinct source region needed to read an entry while removing nested duplicate
   * excerpts. For example, a method excerpt already contains its parameter and an individual call
   * line, so keeping all three would spend the group budget without adding source context.
   */
  private static List<SourceReference> essentialReferences(List<SourceReference> references) {
    return references.stream()
        .filter(
            candidate ->
                references.stream()
                    .noneMatch(
                        container ->
                            !container.ref().equals(candidate.ref())
                                && sameFile(container, candidate)
                                && contains(container, candidate)))
        .sorted(Comparator.comparing(SourceReference::ref, UTF8_ORDER))
        .toList();
  }

  private static boolean sameFile(SourceReference left, SourceReference right) {
    return left.file().equals(right.file());
  }

  private static boolean contains(SourceReference container, SourceReference candidate) {
    return container.startLine() <= candidate.startLine()
        && container.endLine() >= candidate.endLine()
        && (container.startLine() < candidate.startLine()
            || container.endLine() > candidate.endLine());
  }

  private static List<String> distinctStrings(java.util.stream.Stream<String> values) {
    return values.filter(value -> !value.isBlank()).distinct().toList();
  }

  private static int packetCharacterCount(ModelActivityPacket packet) {
    return packet.context().length()
        + packet.technicalObservations().stream().mapToInt(String::length).sum()
        + packet.allowlistedRefs().stream()
            .mapToInt(value -> value.ref().length() + value.snippet().length())
            .sum()
        + packet.limitations().stream().mapToInt(String::length).sum();
  }

  private MaterialCandidate materialCandidate(
      EntryDisposition entry,
      Capsule capsule,
      SourceRefAllocator allocator,
      BusinessMaterialProfile profile) {
    List<SourceReference> references = new ArrayList<>();
    Set<String> referenceIds = new HashSet<>();
    for (SourceSpan span : capsule.spans()) {
      if (references.size() == profile.maxSourceRefsPerMaterial()) {
        break;
      }
      for (SourceReference reference :
          allocator.references(
              span,
              profile.maxLinesPerRef(),
              profile.maxSourceRefsPerMaterial() - references.size())) {
        if (referenceIds.add(reference.ref())) {
          references.add(reference);
        }
      }
    }
    if (references.isEmpty()) {
      return null;
    }
    List<String> observations = observations(capsule);
    List<String> limitations = limitations(entry, capsule, profile, references, observations);
    String materialId =
        "material:"
            + sha256(
                frame("business-material-v1"),
                frame(entry.entryId()),
                frame(capsule.flowSliceId()));
    String context = "已发现入口 " + capsule.trigger() + " 对应技术流程 " + capsule.flowSliceId() + "。";
    String modelContext = "已发现 HTTP 入口 " + capsule.trigger() + "。请仅依据本包片段和观察，解释其局部业务活动。";
    List<ModelActivityPacket.AllowlistedReference> modelRefs =
        references.stream()
            .map(
                value -> new ModelActivityPacket.AllowlistedReference(value.ref(), value.snippet()))
            .toList();
    ModelActivityPacket packet =
        new ModelActivityPacket(
            modelContext,
            modelObservations(observations),
            modelRefs,
            modelLimitations(limitations));
    int characterCount =
        packet.context().length()
            + packet.technicalObservations().stream().mapToInt(String::length).sum()
            + packet.allowlistedRefs().stream()
                .mapToInt(value -> value.ref().length() + value.snippet().length())
                .sum()
            + packet.limitations().stream().mapToInt(String::length).sum();
    if (characterCount > profile.maxMaterialChars()) {
      return null;
    }
    BusinessMaterial material =
        new BusinessMaterial(
            materialId,
            List.of(entry.entryId()),
            BusinessMaterialMode.FLOW_PREFERRED,
            context,
            observations,
            references,
            List.of(capsule.flowSliceId()),
            capsule.proofIds(),
            limitations,
            packet);
    return new MaterialCandidate(
        material, limitations.isEmpty() ? null : "TECHNICAL_GAPS_RETAINED");
  }

  private BusinessMaterial fallbackMaterialFromPersistedContext(
      EntryDisposition entry,
      EntryMetadata metadata,
      EntryContext entryContext,
      VerifiedSourceTextSet source,
      SourceRefAllocator allocator,
      BusinessMaterialProfile profile) {
    if (metadata == null) {
      return null;
    }
    if (entryContext.codeContext() != null) {
      return materialFromNavigatedContext(
          entry, metadata, entryContext, source, allocator, profile);
    }
    Map<String, VerifiedSourceTextDocument> documents = new HashMap<>();
    source.documents().forEach(document -> documents.put(document.fileId().value(), document));
    List<SourceSpan> contextSpans = sourceSpans(entryContext.sourceLocators(), documents);
    if (contextSpans.isEmpty()) {
      return null;
    }
    List<SourceReference> references = new ArrayList<>();
    Set<String> referenceIds = new HashSet<>();
    for (SourceSpan span : contextSpans) {
      if (references.size() == profile.maxSourceRefsPerMaterial()) {
        break;
      }
      for (SourceReference reference :
          allocator.references(
              span,
              profile.maxLinesPerRef(),
              profile.maxSourceRefsPerMaterial() - references.size())) {
        if (referenceIds.add(reference.ref())) {
          references.add(reference);
        }
      }
    }
    if (references.isEmpty()) {
      return null;
    }
    String materialId =
        "material:"
            + sha256(
                frame("business-material-persisted-context-v1"),
                frame(entry.entryId()),
                frame(entryContext.entryContextId()));
    String context = "已发现 HTTP 入口 " + metadata.method() + " " + metadata.route() + "，技术流程尚未完整编译。";
    List<String> observations = new ArrayList<>();
    observations.add("已定位 HTTP 入口 " + metadata.method() + " " + metadata.route());
    observations.addAll(entryContextObservations(entryContext));
    observations = new ArrayList<>(distinctStrings(observations.stream()));
    List<String> limitations = new ArrayList<>(entryContext.limitations());
    limitations.add("技术流程尚未完整编译：" + String.join("、", entry.gapIds()));
    limitations.add("本材料来自 Step05 已保存的入口上下文，不把它伪装成已编译 Flow。");
    ModelActivityPacket packet =
        new ModelActivityPacket(
            "已发现 HTTP 入口 " + metadata.method() + " " + metadata.route() + "。请仅依据本包片段和观察，解释其局部业务活动。",
            modelObservations(observations),
            references.stream()
                .map(
                    reference ->
                        new ModelActivityPacket.AllowlistedReference(
                            reference.ref(), reference.snippet()))
                .toList(),
            modelLimitations(limitations));
    int characterCount =
        packet.context().length()
            + packet.technicalObservations().stream().mapToInt(String::length).sum()
            + packet.allowlistedRefs().stream()
                .mapToInt(value -> value.ref().length() + value.snippet().length())
                .sum()
            + packet.limitations().stream().mapToInt(String::length).sum();
    if (characterCount > profile.maxMaterialChars()) {
      return null;
    }
    return new BusinessMaterial(
        materialId,
        List.of(entry.entryId()),
        BusinessMaterialMode.ENTRY_SOURCE_FALLBACK,
        context,
        List.copyOf(observations),
        references,
        List.of(),
        List.of(),
        List.copyOf(limitations),
        packet);
  }

  private BusinessMaterial materialFromNavigatedContext(
      EntryDisposition entry,
      EntryMetadata metadata,
      EntryContext persisted,
      VerifiedSourceTextSet source,
      SourceRefAllocator allocator,
      BusinessMaterialProfile profile) {
    EntryCodeContext code = persisted.codeContext();
    List<SourceSpan> spans = navigatedSourceSpans(code, source);
    List<SourceReference> references = new ArrayList<>();
    for (SourceSpan span : spans) {
      int remaining = profile.maxSourceRefsPerMaterial() - references.size();
      if (remaining < 1) {
        return null;
      }
      List<SourceReference> selected =
          allocator.references(span, profile.maxLinesPerRef(), remaining);
      if (selected.size() < SourceRefAllocator.requiredChunks(span, profile.maxLinesPerRef())) {
        return null;
      }
      references.addAll(selected);
    }
    if (references.isEmpty()) {
      return null;
    }
    List<String> observations = navigatedObservations(code);
    List<String> limitations =
        java.util.stream.Stream.concat(
                persisted.limitations().stream(),
                code.limitations().stream().map(value -> value.code() + "：" + value.detail()))
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    ModelActivityPacket packet =
        new ModelActivityPacket(
            "入口 E1：HTTP "
                + metadata.method()
                + " "
                + metadata.route()
                + "。以下 M、C、S 编号属于本入口；请阅读完整方法、调用候选、条件和返回后解释局部业务活动。",
            modelObservations(observations),
            references.stream()
                .map(
                    value ->
                        new ModelActivityPacket.AllowlistedReference(value.ref(), value.snippet()))
                .toList(),
            modelLimitations(limitations));
    if (packetCharacterCount(packet) > profile.maxMaterialChars()) {
      return null;
    }
    String materialId =
        "material:"
            + sha256(
                frame("business-material-navigated-source-v1"),
                frame(entry.entryId()),
                frame(persisted.entryContextId()));
    return new BusinessMaterial(
        materialId,
        List.of(entry.entryId()),
        BusinessMaterialMode.NAVIGATED_SOURCE,
        "已取得入口 " + metadata.method() + " " + metadata.route() + " 的仓库内完整调用源码。",
        observations,
        references,
        code.technicalEnhancements().flowRef() == null
            ? List.of()
            : List.of(code.technicalEnhancements().flowRef()),
        code.technicalEnhancements().factRefs(),
        limitations,
        packet);
  }

  private List<SourceSpan> navigatedSourceSpans(
      EntryCodeContext context, VerifiedSourceTextSet source) {
    Map<String, VerifiedSourceTextDocument> documents =
        source.documents().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    VerifiedSourceTextDocument::path, value -> value));
    List<EntryCodeContext.SourceSource> sources = new ArrayList<>();
    context.methods().stream()
        .filter(EntryCodeContext.MethodCode::bodyPresent)
        .map(EntryCodeContext.MethodCode::source)
        .forEach(sources::add);
    context.supportingSources().stream()
        .map(EntryCodeContext.SupportingSource::source)
        .forEach(sources::add);
    return sources.stream()
        .map(value -> verifiedNavigatedSpan(value, documents))
        .distinct()
        .sorted(Comparator.comparing(SourceSpan::sortKey, UTF8_ORDER))
        .toList();
  }

  private static SourceSpan verifiedNavigatedSpan(
      EntryCodeContext.SourceSource source, Map<String, VerifiedSourceTextDocument> documents) {
    VerifiedSourceTextDocument document = documents.get(source.path());
    if (document == null) {
      throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
    }
    String full = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    int end = Math.addExact(source.startOffsetUtf16(), source.lengthUtf16());
    if (end > full.length()
        || !full.substring(source.startOffsetUtf16(), end).equals(source.text())) {
      throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
    }
    return new SourceSpan(source.path(), source.startLine(), source.endLine(), source.text());
  }

  private static List<String> navigatedObservations(EntryCodeContext context) {
    Map<String, String> methodIds = new LinkedHashMap<>();
    for (int index = 0; index < context.methods().size(); index++) {
      methodIds.put(context.methods().get(index).methodKey(), "M" + (index + 1));
    }
    List<String> values = new ArrayList<>();
    for (EntryCodeContext.MethodCode method : context.methods()) {
      String methodId = methodIds.get(method.methodKey());
      String parameters =
          method.parameters().stream()
              .map(value -> "形参[" + value.ordinal() + "]=" + value.name() + ":" + value.typeText())
              .collect(java.util.stream.Collectors.joining("，"));
      values.add(
          methodId
              + " 完整方法（"
              + method.declaringType()
              + "）："
              + method.signature()
              + (parameters.isBlank() ? "。" : "；" + parameters + "。"));
      for (EntryCodeContext.Control control : method.controls()) {
        values.add(
            methodId
                + " 条件："
                + control.kind()
                + (control.expression() == null ? "" : " " + control.expression())
                + "。");
      }
      for (EntryCodeContext.Exit exit : method.exits()) {
        values.add(
            methodId
                + " 终止："
                + exit.kind()
                + (exit.expression() == null ? "" : " " + exit.expression())
                + "。");
      }
    }
    for (int index = 0; index < context.calls().size(); index++) {
      EntryCodeContext.CallSite call = context.calls().get(index);
      String actuals =
          call.actualArguments().stream()
              .map(value -> "实参[" + value.ordinal() + "]=" + value.expression())
              .collect(java.util.stream.Collectors.joining("，"));
      String targets =
          call.targets().stream()
              .map(target -> targetObservation(target, methodIds, context))
              .collect(java.util.stream.Collectors.joining("；"));
      values.add(
          "C"
              + (index + 1)
              + " 调用："
              + methodIds.get(call.callerMethodKey())
              + " 执行 "
              + call.expression()
              + (actuals.isBlank() ? "" : "；" + actuals)
              + "；候选="
              + (targets.isBlank() ? "无（" + call.resolutionDetail() + "）" : targets)
              + (call.deferred() ? "；延迟执行" : "")
              + "。");
    }
    context.supportingSources().stream()
        .forEach(value -> values.add("辅助源码：" + value.kind() + "，用途=" + value.reason() + "。"));
    return List.copyOf(values);
  }

  private static String targetObservation(
      EntryCodeContext.CallTarget target, Map<String, String> methodIds, EntryCodeContext context) {
    String targetId =
        target.methodKey() == null
            ? target.displayName()
            : methodIds.getOrDefault(target.methodKey(), target.displayName());
    EntryCodeContext.MethodCode targetMethod =
        target.methodKey() == null
            ? null
            : context.methods().stream()
                .filter(value -> value.methodKey().equals(target.methodKey()))
                .findFirst()
                .orElse(null);
    String associations =
        target.argumentAssociations().stream()
            .map(
                value -> {
                  String actual =
                      value.actualOrdinals().stream()
                          .map(String::valueOf)
                          .collect(java.util.stream.Collectors.joining(","));
                  String formal =
                      value.formalOrdinal() == null
                          ? "未知"
                          : formalDisplay(targetMethod, value.formalOrdinal());
                  return "实参[" + actual + "]→" + formal;
                })
            .collect(java.util.stream.Collectors.joining("，"));
    return targetId
        + "["
        + target.expansion()
        + "]"
        + (associations.isBlank() ? "" : " " + associations)
        + (target.reason() == null ? "" : "，停止原因=" + target.reason());
  }

  private static String formalDisplay(EntryCodeContext.MethodCode method, int ordinal) {
    if (method == null || ordinal >= method.parameters().size()) {
      return "形参[" + ordinal + "]=未知";
    }
    JavaDeclarationCatalog.ParameterView parameter = method.parameters().get(ordinal);
    return "形参[" + ordinal + "]=" + parameter.name() + ":" + parameter.typeText();
  }

  private List<String> observations(Capsule capsule) {
    List<String> values = new ArrayList<>();
    values.add("入口触发方式为 " + capsule.trigger());
    values.addAll(entryContextObservations(capsule.entryContext()));
    capsule.atoms().stream()
        .filter(atom -> MODEL_SAFE_ATOM_ROLES.contains(atom.role()))
        .limit(8)
        .forEach(
            atom -> values.add("代码观察：" + atom.name() + "（" + atom.role() + "）的值为 " + atom.value()));
    capsule.signals().stream()
        .filter(signal -> "EXPLICIT_CALL".equals(signal.signalKind()))
        .limit(4)
        .forEach(signal -> values.add("代码观察：存在明确调用 " + signal.anchorKey()));
    return values.stream().distinct().toList();
  }

  private static List<String> entryContextObservations(EntryContext context) {
    List<String> values = new ArrayList<>();
    if (context.entrySignature() != null) {
      values.add("源码输入：入口签名 " + context.entrySignature() + "。");
    }
    context
        .calls()
        .forEach(
            call -> {
              values.add(
                  "代码路径："
                      + call.callerSignature()
                      + " 将参数 "
                      + String.join("、", call.argumentExpressions())
                      + " 传给 "
                      + call.targetSignature()
                      + (call.boundary() ? "（离开当前 Java 实现边界）。" : "。"));
              values.add(
                  "源码调用："
                      + call.targetSignature()
                      + "("
                      + String.join("、", call.argumentExpressions())
                      + ")。"
                      + (call.boundary() ? "该调用到达仓库外边界。" : ""));
            });
    context
        .controls()
        .forEach(
            control ->
                values.add(
                    "源码条件："
                        + control.ownerSignature()
                        + " 在 "
                        + control.condition()
                        + " 时改变后续处理。"));
    context
        .returns()
        .forEach(terminal -> values.add("源码终止：存在 " + terminal.terminalKind() + " 返回路径。"));
    return List.copyOf(values);
  }

  private static List<String> mergeObservations(List<String> primary, List<String> secondary) {
    return java.util.stream.Stream.concat(primary.stream(), secondary.stream())
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  private static List<String> modelObservations(List<String> observations) {
    return observations.stream()
        .filter(
            value ->
                !value.contains("evidence-node:")
                    && !value.contains("data-flow-node:")
                    && !value.contains("program-graph:")
                    && !value.contains("java-parameter-symbol-v1:")
                    && !value.contains("call-node:"))
        .toList();
  }

  private static List<String> modelLimitations(List<String> limitations) {
    return limitations.stream()
        .map(
            value ->
                value.startsWith("技术分析仍保留缺口：")
                        || value.startsWith("入口的技术流程处置保留了 Gap：")
                        || value.startsWith("技术流程尚未完整编译：")
                    ? "部分输入、数据传递或外部效果在静态源码中尚未确认；请将相关结论列为待确认。"
                    : value)
        .distinct()
        .toList();
  }

  private static List<String> limitations(
      EntryDisposition entry,
      Capsule capsule,
      BusinessMaterialProfile profile,
      List<SourceReference> references,
      List<String> observations) {
    List<String> values = new ArrayList<>();
    if (!capsule.gapReasons().isEmpty()) {
      values.add("技术分析仍保留缺口：" + String.join("、", capsule.gapReasons()));
    }
    if (!"ELIGIBLE".equals(capsule.modelEligibility())) {
      values.add("原技术 Capsule 未满足旧模型预算；当前材料只保留已验证的短片段。");
    }
    if (references.size() < capsule.spans().size()) {
      values.add(BusinessMaterial.SNIPPET_BUDGET_NOTICE);
    }
    if (!entry.gapIds().isEmpty()) {
      values.add("入口的技术流程处置保留了 Gap：" + String.join("、", entry.gapIds()));
    }
    return values.stream().distinct().toList();
  }

  private ModulePublicationReference install(
      ReopenedAnalysisStepPublication flows, BusinessMaterialSet materialSet) {
    return install(
        flows.reference().address().runId(),
        flows.receipt().controls(),
        upstreamPayloads(flows),
        materialSet);
  }

  private ModulePublicationReference install(
      org.sourceanalysis.app.artifact.AnalysisRunId runId,
      org.sourceanalysis.app.artifact.ArtifactControls controls,
      List<ArtifactReference> upstream,
      BusinessMaterialSet materialSet) {
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.FLOW_INTERPRETATION, 10, "business-material-builder");
    CanonicalModulePayload payload = payload(materialSet);
    InstalledModulePublication installed =
        moduleArtifacts.install(
            new ModuleInstallRequest(
                address,
                "v1",
                upstream,
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(payload)));
    return installed.reference();
  }

  private static List<ArtifactReference> upstreamPayloads(
      ReopenedAnalysisStepPublication... publications) {
    List<ArtifactReference> upstream = new ArrayList<>();
    for (ReopenedAnalysisStepPublication publication : publications) {
      publication
          .semanticPayloads()
          .forEach(
              value ->
                  upstream.add(
                      new ArtifactReference(
                          value.descriptor().artifactId(), value.descriptor().sha256())));
    }
    upstream.sort(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER));
    if (upstream.stream().map(ArtifactReference::artifactId).distinct().count()
        != upstream.size()) {
      throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
    }
    return List.copyOf(upstream);
  }

  private CanonicalModulePayload payload(BusinessMaterialSet set) {
    byte[] bytes = jsonl(set.materials(), set.entryCoverage());
    ArtifactId id =
        ArtifactId.parse(
            ARTIFACT_PREFIX
                + ":"
                + sha256(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(bytes)));
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        id,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private byte[] jsonl(
      List<BusinessMaterial> materials, List<BusinessMaterialEntryCoverage> coverage) {
    StringBuilder values = new StringBuilder();
    for (BusinessMaterial material : materials) {
      values.append(
          new String(
              canonicalJson.encodeCanonical(materialJson(material)).copyToByteArray(),
              StandardCharsets.UTF_8));
      values.append('\n');
    }
    for (BusinessMaterialEntryCoverage entryCoverage : coverage) {
      values.append(
          new String(
              canonicalJson.encodeCanonical(coverageJson(entryCoverage)).copyToByteArray(),
              StandardCharsets.UTF_8));
      values.append('\n');
    }
    return values.toString().getBytes(StandardCharsets.UTF_8);
  }

  private ObjectNode materialJson(BusinessMaterial material) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("recordType", "BUSINESS_MATERIAL");
    value.put("schemaVersion", "flow-interpretation-business-material-v1");
    value.put("materialId", material.materialId());
    strings(value.putArray("entryIds"), material.entryIds());
    value.put("materialMode", material.materialMode().name());
    value.put("context", material.context());
    strings(value.putArray("technicalObservations"), material.technicalObservations());
    ArrayNode refs = value.putArray("sourceRefs");
    material.sourceRefs().forEach(ref -> sourceRef(refs.addObject(), ref));
    strings(value.putArray("flowRefs"), material.flowRefs());
    strings(value.putArray("technicalProofRefs"), material.technicalProofRefs());
    strings(value.putArray("limitations"), material.limitations());
    modelPacket(value.putObject("modelPacket"), material.modelPacket());
    return value;
  }

  private static ObjectNode coverageJson(BusinessMaterialEntryCoverage coverage) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("recordType", "ENTRY_COVERAGE");
    value.put("schemaVersion", "flow-interpretation-business-material-v1");
    value.put("entryId", coverage.entryId());
    value.put("disposition", coverage.disposition());
    if (coverage.materialId() == null) {
      value.putNull("materialId");
    } else {
      value.put("materialId", coverage.materialId());
    }
    if (coverage.reasonCode() == null) {
      value.putNull("reasonCode");
    } else {
      value.put("reasonCode", coverage.reasonCode());
    }
    return value;
  }

  private static void modelPacket(ObjectNode node, ModelActivityPacket packet) {
    node.put("context", packet.context());
    strings(node.putArray("technicalObservations"), packet.technicalObservations());
    ArrayNode references = node.putArray("allowlistedRefs");
    packet
        .allowlistedRefs()
        .forEach(
            reference ->
                references
                    .addObject()
                    .put("ref", reference.ref())
                    .put("snippet", reference.snippet()));
    strings(node.putArray("limitations"), packet.limitations());
  }

  private static void sourceRef(ObjectNode node, SourceReference reference) {
    node.put("ref", reference.ref());
    node.put("file", reference.file());
    node.put("startLine", reference.startLine());
    node.put("endLine", reference.endLine());
    node.put("snippet", reference.snippet());
  }

  private List<EntryDisposition> entryDispositions(VerifiedCanonicalPayload payload) {
    List<EntryDisposition> values = new ArrayList<>();
    for (JsonNode line : jsonLines(payload)) {
      String entryId = text(line, "entryId");
      String disposition = text(line, "disposition");
      String flowSliceId = nullableText(line, "flowSliceId");
      List<String> gapIds = strings(line.path("gapIds"));
      values.add(new EntryDisposition(entryId, disposition, flowSliceId, gapIds));
    }
    values.sort(Comparator.comparing(EntryDisposition::entryId, UTF8_ORDER));
    if (values.stream().map(EntryDisposition::entryId).distinct().count() != values.size()) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    return values;
  }

  private Map<String, Capsule> capsules(
      VerifiedCanonicalPayload payload, VerifiedSourceTextSet source) {
    Map<String, VerifiedSourceTextDocument> documents = new HashMap<>();
    source.documents().forEach(document -> documents.put(document.fileId().value(), document));
    Map<String, Capsule> values = new LinkedHashMap<>();
    for (JsonNode node : jsonLines(payload)) {
      JsonNode entry = object(node, "entryView");
      String entryId = text(entry, "entryId");
      List<SourceSpan> spans = new ArrayList<>();
      for (JsonNode span : array(node, "modelEvidenceSpans")) {
        spans.add(sourceSpan(span, documents));
      }
      List<Atom> atoms = new ArrayList<>();
      List<String> proofIds = new ArrayList<>();
      for (JsonNode fact : array(node, "factViews")) {
        for (JsonNode atom : array(fact, "atoms")) {
          atoms.add(
              new Atom(
                  text(atom, "role"),
                  text(atom, "name"),
                  text(object(atom, "value"), "canonical")));
          proofIds.add(text(atom, "proofId"));
        }
      }
      List<Signal> signals = new ArrayList<>();
      for (JsonNode signal : array(node, "processJoinSignals")) {
        signals.add(new Signal(text(signal, "signalKind"), text(signal, "anchorKey")));
      }
      List<String> gapReasons =
          array(node, "gapViews").stream()
              .map(value -> text(value, "reasonCode"))
              .distinct()
              .toList();
      EntryContext context = entryContext(object(node, "entryContext"));
      String flowSliceId = nullableText(node, "flowSliceId");
      if (!entryId.equals(context.entryId())
          || !Objects.equals(flowSliceId, context.flowSliceId())) {
        throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
      }
      spans.addAll(sourceSpans(context.sourceLocators(), documents));
      Capsule capsule =
          new Capsule(
              flowSliceId,
              text(entry, "trigger"),
              text(node, "modelEligibility"),
              context,
              spans.stream().sorted(Comparator.comparing(SourceSpan::sortKey, UTF8_ORDER)).toList(),
              atoms,
              proofIds.stream().distinct().sorted(UTF8_ORDER).toList(),
              signals,
              gapReasons);
      if (values.put(entryId, capsule) != null) {
        throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
      }
    }
    return Map.copyOf(values);
  }

  private Map<String, EntryContext> entryContexts(VerifiedCanonicalPayload flowSlices) {
    JsonNode document = canonicalJson.parseCanonical(flowSlices.canonicalUtf8());
    Map<String, EntryContext> values = new HashMap<>();
    for (JsonNode contextNode : array(document, "entryContexts")) {
      EntryContext context = entryContext(contextNode);
      if (values.put(context.entryId(), context) != null) {
        throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
      }
    }
    return Map.copyOf(values);
  }

  private static EntryContext entryContext(JsonNode node) {
    EntryCodeContext codeContext = null;
    JsonNode codeContextNode = node.path("codeContext");
    if (!codeContextNode.isMissingNode() && !codeContextNode.isNull()) {
      if (!codeContextNode.isObject()) {
        throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
      }
      try {
        codeContext = MAPPER.treeToValue(codeContextNode, EntryCodeContext.class);
      } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
        throw failure("BUSINESS_MATERIAL_INPUT_INVALID", new IllegalArgumentException(invalid));
      }
    }
    JsonNode strict = node.path("strictTechnicalContext");
    List<CallContext> calls = new ArrayList<>();
    List<ControlContext> controls = new ArrayList<>();
    List<ReturnContext> returns = new ArrayList<>();
    List<SourceLocatorV1> sourceLocators = List.of();
    String entrySignature = null;
    if (!strict.isMissingNode() && !strict.isNull()) {
      if (!strict.isObject()) {
        throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
      }
      entrySignature = text(strict, "entrySignature");
      for (JsonNode call : array(strict, "calls")) {
        calls.add(
            new CallContext(
                text(call, "callerSignature"),
                text(call, "targetSignature"),
                strings(call.path("argumentExpressions")),
                text(call, "resolution"),
                call.path("boundary").asBoolean(false)));
      }
      for (JsonNode control : array(strict, "controls")) {
        controls.add(
            new ControlContext(text(control, "ownerSignature"), text(control, "condition")));
      }
      for (JsonNode terminal : array(strict, "returns")) {
        returns.add(new ReturnContext(text(terminal, "terminalKind")));
      }
      sourceLocators = sourceLocators(strict, "sourceLocators");
    }
    if (codeContext == null && (strict.isMissingNode() || strict.isNull())) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    return new EntryContext(
        text(node, "entryContextId"),
        text(node, "entryId"),
        nullableText(node, "flowSliceId"),
        text(node, "collectionStatus"),
        nullableText(node, "collectionReason"),
        codeContext,
        entrySignature,
        List.copyOf(calls),
        List.copyOf(controls),
        List.copyOf(returns),
        sourceLocators,
        strings(node.path("limitations")));
  }

  private static List<SourceLocatorV1> sourceLocators(JsonNode node, String field) {
    List<SourceLocatorV1> values = new ArrayList<>();
    for (JsonNode locator : array(node, field)) {
      try {
        JsonNode fileId = locator.path("fileId");
        values.add(
            new SourceLocatorV1(
                ArtifactId.parse(
                    fileId.isObject() ? text(fileId, "value") : text(locator, "fileId")),
                text(locator, "path"),
                longValue(locator, "startByte"),
                longValue(locator, "endByteExclusive"),
                integer(locator, "startLine"),
                integer(locator, "startColumn"),
                integer(locator, "endLine"),
                integer(locator, "endColumn")));
      } catch (RuntimeException invalid) {
        throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
      }
    }
    return values.stream()
        .distinct()
        .sorted(
            Comparator.comparing(SourceLocatorV1::path, UTF8_ORDER)
                .thenComparingLong(SourceLocatorV1::startByte)
                .thenComparingLong(SourceLocatorV1::endByteExclusive))
        .toList();
  }

  private List<SourceSpan> sourceSpans(
      List<SourceLocatorV1> locators, Map<String, VerifiedSourceTextDocument> documents) {
    return locators.stream()
        .map(locator -> sourceSpan(locator, documents))
        .distinct()
        .sorted(Comparator.comparing(SourceSpan::sortKey, UTF8_ORDER))
        .toList();
  }

  private SourceSpan sourceSpan(
      SourceLocatorV1 locator, Map<String, VerifiedSourceTextDocument> documents) {
    VerifiedSourceTextDocument document = documents.get(locator.fileId().value());
    if (document == null
        || !document.path().equals(locator.path())
        || locator.endByteExclusive() > document.rawUtf8().size()) {
      throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
    }
    byte[] bytes = document.rawUtf8().copyToByteArray();
    String snippet =
        new String(
            Arrays.copyOfRange(bytes, (int) locator.startByte(), (int) locator.endByteExclusive()),
            StandardCharsets.UTF_8);
    return new SourceSpan(locator.path(), locator.startLine(), locator.endLine(), snippet);
  }

  private SourceSpan sourceSpan(JsonNode span, Map<String, VerifiedSourceTextDocument> documents) {
    JsonNode excerpt = object(span, "sourceExcerpt");
    JsonNode locator = object(excerpt, "locator");
    String fileId = text(locator, "fileId");
    VerifiedSourceTextDocument document = documents.get(fileId);
    int start = integer(locator, "startByte");
    int end = integer(locator, "endByteExclusive");
    if (document == null
        || !document.path().equals(text(locator, "path"))
        || start < 0
        || end < start
        || end > document.rawUtf8().size()) {
      throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
    }
    byte[] expected = Arrays.copyOfRange(document.rawUtf8().copyToByteArray(), start, end);
    String persisted = text(excerpt, "rawUtf8");
    if (!Arrays.equals(expected, persisted.getBytes(StandardCharsets.UTF_8))) {
      throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
    }
    return new SourceSpan(
        document.path(), integer(locator, "startLine"), integer(locator, "endLine"), persisted);
  }

  private Map<String, VerifiedCanonicalPayload> byFileName(
      List<VerifiedCanonicalPayload> payloads) {
    Map<String, VerifiedCanonicalPayload> values = new HashMap<>();
    for (VerifiedCanonicalPayload payload : payloads) {
      if (values.put(payload.descriptor().fileName(), payload) != null) {
        throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
      }
    }
    return Map.copyOf(values);
  }

  private Map<String, EntryMetadata> entryMetadataFromFlows(ReopenedAnalysisStepPublication flows) {
    AnalysisStepPublicationReference discoveryReference =
        flows.receipt().upstreamAnalysisStepReferences().stream()
            .filter(
                value -> value.address().analysisStepKey() == AnalysisStepKey.APPLICATION_DISCOVERY)
            .findFirst()
            .orElseThrow(() -> failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID"));
    return entryMetadata(analysisSteps.reopen(discoveryReference));
  }

  private Map<String, EntryMetadata> entryMetadata(ReopenedAnalysisStepPublication discovery) {
    if (discovery.reference().address().analysisStepKey()
        != AnalysisStepKey.APPLICATION_DISCOVERY) {
      throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
    }
    VerifiedCanonicalPayload entries =
        discovery.semanticPayloads().stream()
            .filter(value -> "entry-points.jsonl".equals(value.descriptor().fileName()))
            .findFirst()
            .orElseThrow(() -> failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID"));
    Map<String, EntryMetadata> values = new HashMap<>();
    for (JsonNode node : jsonLines(entries)) {
      EntryMetadata metadata =
          new EntryMetadata(
              text(node, "entryId"),
              methodConditionDisplay(node),
              text(node, "route"),
              text(node, "handlerFqn"));
      if (values.put(metadata.entryId(), metadata) != null) {
        throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
      }
    }
    return Map.copyOf(values);
  }

  private List<JsonNode> jsonLines(VerifiedCanonicalPayload payload) {
    byte[] bytes = payload.canonicalUtf8().copyToByteArray();
    if (bytes.length == 0) {
      return List.of();
    }
    String text = new String(bytes, StandardCharsets.UTF_8);
    if (!text.endsWith("\n")) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    List<JsonNode> values = new ArrayList<>();
    for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
      values.add(
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))));
    }
    return values;
  }

  private static ObjectNode object(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (!(value instanceof ObjectNode object)) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    return object;
  }

  private static List<JsonNode> array(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (!(value instanceof ArrayNode array)) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    List<JsonNode> values = new ArrayList<>();
    array.forEach(values::add);
    return values;
  }

  private static List<String> strings(JsonNode node) {
    if (!(node instanceof ArrayNode array)) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    List<String> values = new ArrayList<>();
    array.forEach(
        value -> {
          if (!value.isTextual()) {
            throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
          }
          values.add(value.textValue());
        });
    return values.stream().distinct().sorted(UTF8_ORDER).toList();
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    return value.textValue();
  }

  private static String methodConditionDisplay(JsonNode node) {
    ObjectNode condition = object(node, "methodCondition");
    String kind = text(condition, "kind");
    List<String> methods = strings(condition.get("methods"));
    if ("UNRESTRICTED".equals(kind) && methods.isEmpty()) return "UNRESTRICTED";
    if ("EXPLICIT".equals(kind) && !methods.isEmpty()) return String.join(",", methods);
    throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
  }

  private static String nullableText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    return value.textValue();
  }

  private static int integer(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.canConvertToInt()) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    return value.intValue();
  }

  private static long longValue(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.canConvertToLong() || value.longValue() < 0) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    return value.longValue();
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
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

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison = Integer.compare(left[index] & 0xff, right[index] & 0xff);
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private static BusinessMaterialException failure(String code) {
    return new BusinessMaterialException(code);
  }

  private static BusinessMaterialException failure(String code, RuntimeException cause) {
    return new BusinessMaterialException(code, cause);
  }

  private record MaterialInput(
      List<EntryDisposition> entries,
      Map<String, Capsule> capsuleByEntry,
      Map<String, EntryMetadata> entryMetadata,
      Map<String, EntryContext> entryContextsByEntryId,
      VerifiedSourceTextSet source) {}

  private record EntryDisposition(
      String entryId, String disposition, String flowSliceId, List<String> gapIds) {}

  private record EntryMetadata(String entryId, String method, String route, String handlerFqn) {}

  private record SourceSpan(String file, int startLine, int endLine, String snippet) {
    String sortKey() {
      return file + "\u0000" + startLine + "\u0000" + endLine + "\u0000" + snippet;
    }
  }

  private record Atom(String role, String name, String value) {}

  private record Signal(String signalKind, String anchorKey) {}

  private record Capsule(
      String flowSliceId,
      String trigger,
      String modelEligibility,
      EntryContext entryContext,
      List<SourceSpan> spans,
      List<Atom> atoms,
      List<String> proofIds,
      List<Signal> signals,
      List<String> gapReasons) {}

  private record EntryContext(
      String entryContextId,
      String entryId,
      String flowSliceId,
      String collectionStatus,
      String collectionReason,
      EntryCodeContext codeContext,
      String entrySignature,
      List<CallContext> calls,
      List<ControlContext> controls,
      List<ReturnContext> returns,
      List<SourceLocatorV1> sourceLocators,
      List<String> limitations) {}

  private record CallContext(
      String callerSignature,
      String targetSignature,
      List<String> argumentExpressions,
      String resolution,
      boolean boundary) {}

  private record ControlContext(String ownerSignature, String condition) {}

  private record ReturnContext(String terminalKind) {}

  private record MaterialCandidate(BusinessMaterial material, String reasonCode) {}

  private record EntryMaterial(
      EntryDisposition entry,
      EntryMetadata metadata,
      BusinessMaterial material,
      String coverageDisposition,
      String reasonCode) {}

  private record GroupedMaterial(BusinessMaterial material, List<EntryMaterial> members) {}

  private static final class SourceRefAllocator {
    private final Map<String, SourceReference> byLocation = new LinkedHashMap<>();

    List<SourceReference> references(SourceSpan span, int maxLines, int maximum) {
      if (maximum < 1) {
        return List.of();
      }
      List<SourceReference> values = new ArrayList<>();
      String[] lines = span.snippet().split("\\R", -1);
      for (int start = 0; start < lines.length && values.size() < maximum; start += maxLines) {
        int end = Math.min(start + maxLines, lines.length);
        String snippet = String.join("\n", Arrays.copyOfRange(lines, start, end)).stripTrailing();
        if (!snippet.isBlank()) {
          values.add(reference(span.file(), span.startLine() + start, snippet));
        }
      }
      return List.copyOf(values);
    }

    static int requiredChunks(SourceSpan span, int maxLines) {
      String[] lines = span.snippet().split("\\R", -1);
      int required = 0;
      for (int start = 0; start < lines.length; start += maxLines) {
        int end = Math.min(start + maxLines, lines.length);
        String snippet = String.join("\n", Arrays.copyOfRange(lines, start, end)).stripTrailing();
        if (!snippet.isBlank()) {
          required++;
        }
      }
      return required;
    }

    private SourceReference reference(String file, int startLine, String snippet) {
      int endLine = startLine + lineCount(snippet) - 1;
      String key = file + "\u0000" + startLine + "\u0000" + endLine + "\u0000" + snippet;
      return byLocation.computeIfAbsent(
          key,
          ignored ->
              new SourceReference(
                  "S" + (byLocation.size() + 1), file, startLine, endLine, snippet));
    }

    private static int lineCount(String value) {
      return value.split("\\R", -1).length;
    }
  }
}
