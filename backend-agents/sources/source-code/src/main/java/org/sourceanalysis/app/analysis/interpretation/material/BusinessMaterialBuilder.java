package org.sourceanalysis.app.analysis.interpretation.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
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
  private static final Set<String> MODEL_SAFE_ATOM_ROLES =
      Set.of("STATIC_TARGET_TYPE", "STATIC_TARGET_METHOD", "STATIC_TARGET_SIGNATURE");
  private static final int MAX_CODE_OUTLINE_OBSERVATIONS = 12;
  private static final int MAX_CODE_OUTLINE_CALLS = 6;
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
      if (!request.usesPublishedFlows()) {
        return buildFromDiscoveredEntries(request);
      }
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

  private BusinessMaterialBuildResult buildFromDiscoveredEntries(
      BuildBusinessMaterialsRequest request) {
    ReopenedAnalysisStepPublication sourcePublication =
        analysisSteps.reopen(request.sourceInventory().publication());
    ReopenedAnalysisStepPublication discoveryPublication =
        analysisSteps.reopen(request.applicationDiscovery().publication());
    requireDirectEntryBasis(request, sourcePublication, discoveryPublication);
    VerifiedSourceTextSet source = sourceReader.reopen(request.sourceInventory());
    Map<String, EntryMetadata> metadata = entryMetadata(discoveryPublication);
    List<EntryDisposition> entries =
        metadata.values().stream()
            .map(
                entry ->
                    new EntryDisposition(
                        entry.entryId(), "FLOW_UNAVAILABLE", null, List.of("FLOW_NOT_AVAILABLE")))
            .sorted(Comparator.comparing(EntryDisposition::entryId, UTF8_ORDER))
            .toList();
    BusinessMaterialSet materialSet =
        materialSet(
            new MaterialInput(entries, Map.of(), metadata, sourceContexts(metadata, source)),
            request.profile());
    ModulePublicationReference checkpoint =
        install(
            sourcePublication.reference().address().runId(),
            discoveryPublication.receipt().controls(),
            upstreamPayloads(sourcePublication, discoveryPublication),
            materialSet);
    return new BusinessMaterialBuildResult(materialSet, checkpoint);
  }

  private static void requireDirectEntryBasis(
      BuildBusinessMaterialsRequest request,
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery) {
    if (source.reference().address().analysisStepKey() != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY
        || discovery.reference().address().analysisStepKey()
            != AnalysisStepKey.APPLICATION_DISCOVERY
        || !source.reference().equals(request.sourceInventory().publication())
        || !discovery.reference().equals(request.applicationDiscovery().publication())
        || !source.reference().address().runId().equals(discovery.reference().address().runId())
        || !source.receipt().controls().equals(discovery.receipt().controls())
        || !discovery.receipt().upstreamAnalysisStepReferences().contains(source.reference())) {
      throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
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
    if (dispositions == null || capsules == null) {
      throw failure("BUSINESS_MATERIAL_INPUT_INVALID");
    }
    List<EntryDisposition> entries = entryDispositions(dispositions);
    Map<String, Capsule> capsuleByEntry = capsules(capsules, source);
    Map<String, EntryMetadata> metadata = entryMetadataFromFlows(flows);
    return new MaterialInput(entries, capsuleByEntry, metadata, sourceContexts(metadata, source));
  }

  private BusinessMaterialSet materialSet(MaterialInput input, BusinessMaterialProfile profile) {
    SourceRefAllocator allocator = new SourceRefAllocator();
    List<BusinessMaterial> materials = new ArrayList<>();
    List<BusinessMaterialEntryCoverage> coverage = new ArrayList<>();
    for (EntryDisposition entry : input.entries()) {
      Capsule capsule = input.capsuleByEntry().get(entry.entryId());
      if (!"COMPILED".equals(entry.disposition()) || capsule == null) {
        BusinessMaterial fallback =
            fallbackMaterial(
                entry,
                input.entryMetadata().get(entry.entryId()),
                input.sourceContextsByHandlerFqn(),
                allocator,
                profile);
        if (fallback == null) {
          coverage.add(
              new BusinessMaterialEntryCoverage(
                  entry.entryId(), "NOT_MATERIALIZED", null, "FLOW_NOT_COMPILED"));
        } else {
          materials.add(fallback);
          coverage.add(
              new BusinessMaterialEntryCoverage(
                  entry.entryId(),
                  "MATERIAL_WITH_GAPS",
                  fallback.materialId(),
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
      materials.add(candidate.material());
      coverage.add(
          new BusinessMaterialEntryCoverage(
              entry.entryId(),
              candidate.material().limitations().isEmpty()
                  ? "ANALYZED_MATERIAL"
                  : "MATERIAL_WITH_GAPS",
              candidate.material().materialId(),
              candidate.reasonCode()));
    }
    materials.sort(Comparator.comparing(BusinessMaterial::materialId, UTF8_ORDER));
    coverage.sort(Comparator.comparing(BusinessMaterialEntryCoverage::entryId, UTF8_ORDER));
    String setId =
        "business-material-set:"
            + sha256(frame("business-material-set-v1"), frame(jsonl(materials, coverage)));
    return new BusinessMaterialSet(setId, materials, coverage);
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
    List<String> observations =
        mergeObservations(codeOutlineObservations(capsule.spans(), references), observations(capsule));
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

  private BusinessMaterial fallbackMaterial(
      EntryDisposition entry,
      EntryMetadata metadata,
      Map<String, List<SourceSpan>> sourceContextsByHandlerFqn,
      SourceRefAllocator allocator,
      BusinessMaterialProfile profile) {
    if (metadata == null) {
      return null;
    }
    List<SourceSpan> contextSpans = sourceContextsByHandlerFqn.get(metadata.handlerFqn());
    if (contextSpans == null || contextSpans.isEmpty()) {
      return null;
    }
    List<SourceReference> references = new ArrayList<>();
    for (SourceSpan span : contextSpans) {
      if (references.size() == profile.maxSourceRefsPerMaterial()) {
        break;
      }
      references.addAll(
          allocator.references(
              span,
              profile.maxLinesPerRef(),
              profile.maxSourceRefsPerMaterial() - references.size()));
    }
    String materialId =
        "material:"
            + sha256(
                frame("business-material-fallback-v1"),
                frame(entry.entryId()),
                frame(metadata.handlerFqn()));
    String context = "已发现 HTTP 入口 " + metadata.method() + " " + metadata.route() + " 对应入口处理方法。";
    List<String> observations = new ArrayList<>();
    observations.add("已定位 HTTP 入口 " + metadata.method() + " " + metadata.route());
    observations.add("已定位入口处理方法 " + metadata.handlerFqn());
    if (references.size() > 1) {
      observations.add("已附上入口直接调用的同仓库方法实现，供理解局部处理结果。");
    }
    observations =
        new ArrayList<>(
            mergeObservations(
                observations, codeOutlineObservations(contextSpans, references)));
    List<String> limitations =
        List.of(
            "技术流程尚未完整编译：" + String.join("、", entry.gapIds()), "本材料来自冻结源码中的入口处理方法，不把它伪装成已编译 Flow。");
    ModelActivityPacket packet =
        new ModelActivityPacket(
            "已发现 HTTP 入口 " + metadata.method() + " " + metadata.route() + "。请仅依据本包片段和观察，解释其局部业务活动。",
            List.copyOf(observations),
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
        limitations,
        packet);
  }

  private Map<String, List<SourceSpan>> sourceContexts(
      Map<String, EntryMetadata> entryMetadata, VerifiedSourceTextSet source) {
    Set<String> handlers =
        entryMetadata.values().stream()
            .map(EntryMetadata::handlerFqn)
            .filter(value -> value.contains("#"))
            .collect(java.util.stream.Collectors.toSet());
    if (handlers.isEmpty()) {
      return Map.of();
    }
    JavaParser parser = new JavaParser();
    Map<String, List<TypeContext>> types = new HashMap<>();
    for (VerifiedSourceTextDocument document : source.documents()) {
      if (!document.path().endsWith(".java")) {
        continue;
      }
      String text = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      var parsed = parser.parse(text).getResult();
      if (parsed.isEmpty()) {
        continue;
      }
      CompilationUnit unit = parsed.get();
      String packageName =
          unit.getPackageDeclaration().map(value -> value.getNameAsString()).orElse("");
      Map<String, String> explicitImports = explicitImports(unit);
      for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
        String qualifiedType =
            packageName.isEmpty()
                ? type.getNameAsString()
                : packageName + "." + type.getNameAsString();
        Map<String, String> fieldTypes = fieldTypes(type, packageName, explicitImports);
        List<MethodContext> methods = new ArrayList<>();
        for (MethodDeclaration method : type.getMethods()) {
          if (method.getRange().isEmpty()) {
            continue;
          }
          int startLine = method.getRange().orElseThrow().begin.line;
          int endLine = method.getRange().orElseThrow().end.line;
          methods.add(
              new MethodContext(
                  method,
                  new SourceSpan(
                      document.path(), startLine, endLine, lines(text, startLine, endLine))));
        }
        types
            .computeIfAbsent(qualifiedType, ignored -> new ArrayList<>())
            .add(new TypeContext(qualifiedType, fieldTypes, methods));
      }
    }
    Map<String, List<SourceSpan>> contexts = new HashMap<>();
    for (String handler : handlers) {
      int separator = handler.lastIndexOf('#');
      List<TypeContext> ownerCandidates = types.get(handler.substring(0, separator));
      if (ownerCandidates == null || ownerCandidates.isEmpty()) {
        continue;
      }
      // Preserve the prior handler-only fallback behavior: the first frozen source occurrence
      // supplies the handler text. In contrast, optional target context below fails closed when
      // a receiver type is not unique across Maven modules.
      TypeContext owner = ownerCandidates.get(0);
      String methodName = handler.substring(separator + 1);
      MethodContext method =
          owner.methods().stream()
              .filter(value -> value.method().getNameAsString().equals(methodName))
              .findFirst()
              .orElse(null);
      if (method == null) {
        continue;
      }
      List<SourceSpan> values = new ArrayList<>();
      values.add(method.span());
      directTargetSpans(owner, method, types)
          .forEach(
              value -> {
                if (!values.contains(value)) {
                  values.add(value);
                }
              });
      contexts.put(handler, List.copyOf(values));
    }
    return Map.copyOf(contexts);
  }

  private static Map<String, String> explicitImports(CompilationUnit unit) {
    Map<String, String> values = new HashMap<>();
    unit.getImports().stream()
        .filter(value -> !value.isAsterisk() && !value.isStatic())
        .forEach(
            value -> {
              String qualified = value.getNameAsString();
              int separator = qualified.lastIndexOf('.');
              values.put(qualified.substring(separator + 1), qualified);
            });
    return Map.copyOf(values);
  }

  private static Map<String, String> fieldTypes(
      ClassOrInterfaceDeclaration type, String packageName, Map<String, String> explicitImports) {
    Map<String, String> values = new HashMap<>();
    for (FieldDeclaration field : type.getFields()) {
      String typeName =
          resolveType(field.getElementType().asString(), packageName, explicitImports);
      for (VariableDeclarator variable : field.getVariables()) {
        if (values.put(variable.getNameAsString(), typeName) != null) {
          throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
        }
      }
    }
    return Map.copyOf(values);
  }

  private static String resolveType(
      String rawType, String packageName, Map<String, String> explicitImports) {
    String simple = rawType.replaceAll("<.*>", "").strip();
    if (simple.contains(".") || simple.isEmpty()) {
      return simple;
    }
    String imported = explicitImports.get(simple);
    if (imported != null) {
      return imported;
    }
    return packageName.isEmpty() ? simple : packageName + "." + simple;
  }

  private static List<SourceSpan> directTargetSpans(
      TypeContext owner, MethodContext source, Map<String, List<TypeContext>> types) {
    List<SourceSpan> values = new ArrayList<>();
    for (MethodCallExpr call : source.method().findAll(MethodCallExpr.class)) {
      if (!(call.getScope().orElse(null) instanceof NameExpr receiver)) {
        continue;
      }
      String receiverType = owner.fieldTypes().get(receiver.getNameAsString());
      List<TypeContext> targetOwners = types.get(receiverType);
      if (targetOwners == null || targetOwners.size() != 1) {
        continue;
      }
      TypeContext targetOwner = targetOwners.get(0);
      List<MethodContext> candidates =
          targetOwner.methods().stream()
              .filter(value -> value.method().getBody().isPresent())
              .filter(value -> value.method().getNameAsString().equals(call.getNameAsString()))
              .filter(value -> value.method().getParameters().size() == call.getArguments().size())
              .toList();
      if (candidates.size() == 1 && !values.contains(candidates.get(0).span())) {
        values.add(candidates.get(0).span());
      }
    }
    return List.copyOf(values);
  }

  private static String lines(String text, int startLine, int endLine) {
    String[] values = text.split("\\R", -1);
    if (startLine < 1 || endLine < startLine || endLine > values.length) {
      throw failure("BUSINESS_MATERIAL_SOURCE_BINDING_INVALID");
    }
    return String.join("\n", Arrays.copyOfRange(values, startLine - 1, endLine));
  }

  private List<String> observations(Capsule capsule) {
    List<String> values = new ArrayList<>();
    values.add("入口触发方式为 " + capsule.trigger());
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

  /**
   * Converts only the already selected frozen snippets into short Java-syntax observations.
   *
   * <p>This is deliberately not a business classifier: parameters, conditions, calls and terminal
   * statements are copied from the parsed snippet, while naming the business activity remains the
   * model's task. A malformed or non-Java selected snippet simply contributes no outline item; its
   * original short reference remains available to the model.
   */
  private static List<String> codeOutlineObservations(
      List<SourceSpan> selectedSpans, List<SourceReference> references) {
    JavaParser parser = new JavaParser();
    List<String> snippets =
        java.util.stream.Stream.concat(
                selectedSpans.stream().map(SourceSpan::snippet),
                references.stream().map(SourceReference::snippet))
            .distinct()
            .toList();
    List<MethodDeclaration> methods = new ArrayList<>();
    Set<String> methodBodies = new HashSet<>();
    for (String snippet : snippets) {
      CompilationUnit unit = parseSnippet(parser, snippet);
      if (unit == null) {
        continue;
      }
      for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
        if (methodBodies.add(method.toString())) {
          methods.add(method);
        }
      }
    }
    List<String> values = new ArrayList<>();
    methods.stream()
        .filter(method -> !method.getParameters().isEmpty())
        .forEach(
            method ->
                addOutlineObservation(
                    values,
                    "源码输入：方法 "
                        + method.getNameAsString()
                        + " 接收参数 "
                        + method.getParameters().stream()
                            .map(parameter -> parameter.getNameAsString())
                            .collect(java.util.stream.Collectors.joining("、"))
                        + "。"));
    methods.stream()
        .flatMap(method -> method.findAll(MethodCallExpr.class).stream())
        .filter(MethodCallExpr::hasScope)
        .filter(BusinessMaterialBuilder::isStateOrPersistenceCall)
        .forEach(call -> addOutlineObservation(values, "源码调用：" + compactCode(call.toString()) + "。"));
    methods.stream()
        .flatMap(method -> method.findAll(IfStmt.class).stream())
        .forEach(
            condition ->
                addOutlineObservation(
                    values, "源码条件：" + compactCode(condition.getCondition().toString()) + "。"));
    methods.stream()
        .map(BusinessMaterialBuilder::firstDirectCallAfterLastGuard)
        .flatMap(java.util.Optional::stream)
        .forEach(call -> addOutlineObservation(values, "源码调用：" + compactCode(call.toString()) + "。"));
    methods.stream()
        .flatMap(method -> method.findAll(MethodCallExpr.class).stream())
        .filter(MethodCallExpr::hasScope)
        .forEach(call -> addOutlineObservation(values, "源码调用：" + compactCode(call.toString()) + "。"));
    methods.stream()
        .flatMap(method -> method.findAll(ReturnStmt.class).stream())
        .forEach(
            terminal ->
                addOutlineObservation(
                    values,
                    "源码终止：return"
                        + terminal
                            .getExpression()
                            .map(value -> " " + compactCode(value.toString()))
                            .orElse("")
                        + "。"));
    methods.stream()
        .flatMap(method -> method.findAll(ThrowStmt.class).stream())
        .forEach(
            terminal ->
                addOutlineObservation(
                    values,
                    "源码终止：throw " + compactCode(terminal.getExpression().toString()) + "。"));
    return mergeObservations(values, List.of());
  }

  private static boolean isStateOrPersistenceCall(MethodCallExpr call) {
    String name = call.getNameAsString().toLowerCase(java.util.Locale.ROOT);
    return name.startsWith("set")
        || name.contains("update")
        || name.contains("insert")
        || name.contains("save")
        || name.contains("delete")
        || name.contains("remove")
        || name.contains("persist");
  }

  private static java.util.Optional<MethodCallExpr> firstDirectCallAfterLastGuard(
      MethodDeclaration method) {
    int lastGuardEndLine =
        method.findAll(IfStmt.class).stream()
            .map(IfStmt::getRange)
            .flatMap(java.util.Optional::stream)
            .mapToInt(range -> range.end.line)
            .max()
            .orElse(-1);
    if (lastGuardEndLine < 0) {
      return java.util.Optional.empty();
    }
    return method.findAll(MethodCallExpr.class).stream()
        .filter(MethodCallExpr::hasScope)
        .filter(call -> call.getRange().isPresent())
        .filter(call -> call.getRange().orElseThrow().begin.line > lastGuardEndLine)
        .findFirst();
  }

  private static void addOutlineObservation(List<String> values, String observation) {
    if (values.size() < MAX_CODE_OUTLINE_OBSERVATIONS && !values.contains(observation)) {
      values.add(observation);
    }
  }

  private static CompilationUnit parseSnippet(JavaParser parser, String snippet) {
    var direct = parser.parse(snippet).getResult();
    if (direct.isPresent() && !direct.orElseThrow().findAll(MethodDeclaration.class).isEmpty()) {
      return direct.orElseThrow();
    }
    var wrappedType =
        parser
        .parse("class SourceMaterialSnippet {\n" + snippet + "\n}")
        .getResult()
        .orElse(null);
    if (wrappedType != null && !wrappedType.findAll(MethodDeclaration.class).isEmpty()) {
      return wrappedType;
    }
    return parser
        .parse("class SourceMaterialSnippet { void selected() {\n" + snippet + "\n} }")
        .getResult()
        .orElse(null);
  }

  private static String compactCode(String value) {
    return value.replaceAll("\\s+", " ").strip();
  }

  private static List<String> mergeObservations(
      List<String> primary, List<String> secondary) {
    return java.util.stream.Stream.concat(primary.stream(), secondary.stream())
        .filter(value -> !value.isBlank())
        .distinct()
        .limit(MAX_CODE_OUTLINE_OBSERVATIONS)
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
      values.add("为保持局部活动上下文，本材料仅选择了预算内的来源片段。");
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
      Capsule capsule =
          new Capsule(
              text(node, "flowSliceId"),
              text(entry, "trigger"),
              text(node, "modelEligibility"),
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
              text(node, "method"),
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
      Map<String, List<SourceSpan>> sourceContextsByHandlerFqn) {}

  private record EntryDisposition(
      String entryId, String disposition, String flowSliceId, List<String> gapIds) {}

  private record EntryMetadata(String entryId, String method, String route, String handlerFqn) {}

  private record SourceSpan(String file, int startLine, int endLine, String snippet) {
    String sortKey() {
      return file + "\u0000" + startLine + "\u0000" + endLine + "\u0000" + snippet;
    }
  }

  private record MethodContext(MethodDeclaration method, SourceSpan span) {}

  private record TypeContext(
      String qualifiedType, Map<String, String> fieldTypes, List<MethodContext> methods) {}

  private record Atom(String role, String name, String value) {}

  private record Signal(String signalKind, String anchorKey) {}

  private record Capsule(
      String flowSliceId,
      String trigger,
      String modelEligibility,
      List<SourceSpan> spans,
      List<Atom> atoms,
      List<String> proofIds,
      List<Signal> signals,
      List<String> gapReasons) {}

  private record MaterialCandidate(BusinessMaterial material, String reasonCode) {}

  private static final class SourceRefAllocator {
    private final Map<String, SourceReference> byLocation = new LinkedHashMap<>();

    List<SourceReference> references(SourceSpan span, int maxLines, int maximum) {
      if (maximum < 1) {
        return List.of();
      }
      List<SourceReference> values = new ArrayList<>();
      String head = limitedSnippet(span.snippet(), maxLines);
      values.add(reference(span.file(), span.startLine(), head));
      if (values.size() == maximum || lineCount(span.snippet()) <= maxLines) {
        return List.copyOf(values);
      }
      for (SnippetAnchor anchor : laterDirectCallAnchors(span, maxLines, maximum - values.size())) {
        SourceReference reference = reference(span.file(), anchor.startLine(), anchor.snippet());
        if (!values.contains(reference)) {
          values.add(reference);
        }
      }
      return List.copyOf(values);
    }

    private static List<SnippetAnchor> laterDirectCallAnchors(
        SourceSpan span, int headLines, int maximum) {
      JavaParser parser = new JavaParser();
      var parsed = parser.parse(span.snippet()).getResult();
      if (parsed.isEmpty()) {
        return List.of();
      }
      List<SnippetAnchor> eligible =
          parsed.orElseThrow().findAll(MethodCallExpr.class).stream()
              .filter(call -> call.getScope().isPresent())
              .filter(call -> call.getRange().isPresent())
              .map(call -> call.getRange().orElseThrow())
              .filter(range -> range.begin.line > headLines)
              .map(
                  range ->
                      new SnippetAnchor(
                          span.startLine() + range.begin.line - 1,
                          lines(span.snippet(), range.begin.line, range.end.line)))
              .toList();
      if (eligible.isEmpty()) {
        return List.of();
      }
      if (maximum == 1) {
        return List.of(eligible.get(eligible.size() - 1));
      }
      List<SnippetAnchor> selected = new ArrayList<>();
      selected.add(eligible.get(0));
      SnippetAnchor last = eligible.get(eligible.size() - 1);
      if (!selected.contains(last)) {
        selected.add(last);
      }
      return selected.stream().limit(maximum).toList();
    }

    private SourceReference reference(String file, int startLine, String snippet) {
      int endLine = startLine + lineCount(snippet) - 1;
      String key =
          file + "\u0000" + startLine + "\u0000" + endLine + "\u0000" + snippet;
      return byLocation.computeIfAbsent(
          key,
          ignored ->
              new SourceReference(
                  "S" + (byLocation.size() + 1), file, startLine, endLine, snippet));
    }

    private static String limitedSnippet(String source, int maxLines) {
      String[] lines = source.split("\\R", -1);
      int count = Math.min(maxLines, lines.length);
      return String.join("\n", Arrays.copyOf(lines, count)).stripTrailing();
    }

    private static int lineCount(String value) {
      return (int) value.lines().count();
    }

    private record SnippetAnchor(int startLine, String snippet) {}
  }
}
