package org.sourceanalysis.app.analysis.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.BoundedModelJobExecutor;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Default Step07 implementation over already reviewed activities and saved source references. */
public final class DefaultBusinessProcessDiscovery implements BusinessProcessDiscovery {

  static final String CATALOG_DRAFT = "BUSINESS_CATALOG_DRAFT";
  static final String CATALOG_REVIEW = "BUSINESS_CATALOG_REVIEW";
  static final String CATALOG_SHARD_DRAFT = "BUSINESS_CATALOG_SHARD_DRAFT";
  static final String CATALOG_SHARD_REVIEW = "BUSINESS_CATALOG_SHARD_REVIEW";
  static final String CATALOG_MERGE_DRAFT = "BUSINESS_CATALOG_MERGE_DRAFT";
  static final String CATALOG_MERGE_REVIEW = "BUSINESS_CATALOG_MERGE_REVIEW";
  static final String MATERIAL_SELECTION = "PROCESS_MATERIAL_SELECTION";
  static final String READING_CHECK = "PROCESS_READING_CHECK";
  static final String PROCESS_DRAFT = "BUSINESS_PROCESS_DRAFT";
  static final String PROCESS_REVIEW = "BUSINESS_PROCESS_REVIEW";
  static final String CONSOLIDATION_DRAFT = "BUSINESS_PROCESS_CONSOLIDATION_DRAFT";
  static final String CONSOLIDATION_REVIEW = "BUSINESS_PROCESS_CONSOLIDATION_REVIEW";

  private static final Set<String> ACTIVITY_DISPOSITIONS =
      Set.of(
          "PROCESS_MEMBER", "SUPPORT_ONLY", "STANDALONE", "UNCLASSIFIED", "NOT_PROCESSED_CAPACITY");
  private static final Set<String> CANDIDATE_ROLES =
      Set.of("CORE", "OPTIONAL", "ROLLBACK", "SUPPORT", "QUERY", "ANALYTICS");
  private static final Set<String> CANDIDATE_DISPOSITIONS =
      Set.of(
          "RECONSTRUCTED",
          "SPLIT",
          "SUPPORT_ONLY",
          "INSUFFICIENT_MATERIAL",
          "NOT_PROCESSED_CAPACITY");
  private static final Set<String> CERTAINTIES = Set.of("CONFIRMED", "INFERRED", "UNRESOLVED");
  private static final Comparator<String> UTF8_ORDER =
      (left, right) -> {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < length; index++) {
          int compared =
              Integer.compare(
                  Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
          if (compared != 0) {
            return compared;
          }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
      };
  private static final Comparator<SourceIdentity> SOURCE_IDENTITY_ORDER =
      Comparator.comparing(SourceIdentity::file, UTF8_ORDER)
          .thenComparingInt(SourceIdentity::startLine)
          .thenComparingInt(SourceIdentity::endLine)
          .thenComparing(SourceIdentity::snippet, UTF8_ORDER);

  private final StructuredModelProvider provider;
  private final ModelJobExecutionConfiguration modelJobs;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public DefaultBusinessProcessDiscovery(StructuredModelProvider provider) {
    this.provider = Objects.requireNonNull(provider, "structured model provider");
    this.modelJobs = null;
  }

  private DefaultBusinessProcessDiscovery(ModelJobExecutionConfiguration modelJobs) {
    this.provider = null;
    this.modelJobs = Objects.requireNonNull(modelJobs, "model job execution configuration");
  }

  /**
   * Creates process discovery using the run's configured routing, concurrency, and reuse source.
   */
  public static DefaultBusinessProcessDiscovery forExecution(
      ModelJobExecutionConfiguration modelJobs) {
    return new DefaultBusinessProcessDiscovery(modelJobs);
  }

  @Override
  public ProcessDiscoveryResult discover(ProcessDiscoveryRequest request) {
    CatalogSample sample = discoverCatalogSample(request);
    List<ReadingPacket> packets = prepareReadingPackets(sample, sample.catalog.candidates());
    CatalogResult catalog = finalCatalog(sample, packets);
    SourceNormalization sourceNormalization = normalizeSources(sample.corpus, packets);
    List<CandidateResult> candidateResults =
        reconstructReadingPackets(
            packets, sourceNormalization, sample.corpus, sample.request.profile());
    Consolidated consolidated =
        consolidate(catalog, candidateResults, sample.corpus, sample.request.profile());
    return new ProcessDiscoveryResult(
        consolidated.catalog(),
        consolidated.coverage(),
        packetSources(sample.corpus, sourceNormalization.finalPacketList()),
        sample.request.outputRunId(),
        sample.request.activities().checkpoint(),
        sample.request.materials().checkpoint());
  }

  /**
   * Opens the fixed corpus and completes the real catalog stage without reconstructing candidates.
   */
  CatalogSample discoverCatalogSample(ProcessDiscoveryRequest request) {
    Objects.requireNonNull(request, "process discovery request");
    FrozenCorpus corpus = FrozenCorpus.open(request.activities(), request.materials());
    List<ActivityIndexCard> cards =
        corpus.activities().stream().map(ActivityIndexCard::from).toList();
    CatalogResult savedOrNewCatalog =
        request.savedCatalogInput() == null
            ? discoverCatalog(cards, request.profile())
            : reopenCatalogInput(request.savedCatalogInput(), cards);
    FrozenProcessSourceCorpus sourceText =
        request.sourceTextReader() == null
            ? null
            : new FrozenProcessSourceCorpus(
                request.sourceTextReader().reopen(request.sourceInventoryReference()));
    MaterialSelection selection =
        selectMaterials(savedOrNewCatalog, cards, corpus, sourceText, request);
    CatalogResult catalog =
        selection.catalogWith(selection.candidates(), selection.changedDispositions(), cards);
    return new CatalogSample(request, corpus, cards, catalog, selection, sourceText);
  }

  /**
   * Reconstructs a chosen catalog subset while preserving each candidate's full-catalog ordinal.
   */
  List<String> reconstructSelected(CatalogSample sample, List<String> selectedCandidateIds) {
    Objects.requireNonNull(sample, "catalog sample");
    Objects.requireNonNull(selectedCandidateIds, "selected candidate IDs");
    if (selectedCandidateIds.isEmpty()
        || new LinkedHashSet<>(selectedCandidateIds).size() != selectedCandidateIds.size()) {
      throw failure("PROCESS_ACCEPTANCE_SAMPLE_SELECTION_INVALID");
    }
    Set<String> selected = Set.copyOf(selectedCandidateIds);
    Set<String> known =
        sample.catalog.candidates().stream()
            .map(Candidate::candidateId)
            .collect(Collectors.toSet());
    if (!known.containsAll(selected)) {
      throw failure("PROCESS_ACCEPTANCE_SAMPLE_UNKNOWN_CANDIDATE");
    }

    List<Candidate> candidates =
        sample.catalog.candidates().stream()
            .filter(candidate -> selected.contains(candidate.candidateId()))
            .toList();
    List<ReadingPacket> packets = prepareReadingPackets(sample, candidates);
    reconstructReadingPackets(
        packets, normalizeSources(sample.corpus, packets), sample.corpus, sample.request.profile());
    return packets.stream().map(packet -> packet.candidate().candidateId()).toList();
  }

  private CatalogResult reopenCatalogInput(
      ImmutableBytes savedCatalogInput, List<ActivityIndexCard> cards) {
    JsonNode parsed = canonicalJson.parseCanonical(savedCatalogInput);
    if (!(parsed instanceof ObjectNode saved)
        || !"model-job-reviewed-result-v2".equals(nullableText(saved.path("schemaVersion")))
        || !"COMPLETED".equals(nullableText(saved.path("status")))
        || !(saved.path("draft") instanceof ObjectNode draft)
        || !(saved.path("review") instanceof ObjectNode review)) {
      throw failure("PROCESS_CATALOG_INPUT_INVALID");
    }
    return parseMergedCatalogReview(draft, review, cards);
  }

  private MaterialSelection selectMaterials(
      CatalogResult catalog,
      List<ActivityIndexCard> cards,
      FrozenCorpus corpus,
      FrozenProcessSourceCorpus sourceText,
      ProcessDiscoveryRequest request) {
    ObjectNode input =
        materialSelectionInput(catalog, cards, corpus, sourceText, request.focusQuestion());
    requireInputCapacity(
        input, request.profile(), "PROCESS_MATERIAL_SELECTION_INPUT_CAPACITY_EXCEEDED");
    ModelCall response =
        singleDecision(
            MATERIAL_SELECTION,
            "process-material-selection",
            input,
            materialSelectionSchema(cards, corpus, sourceText),
            request.profile(),
            binding("repositorySummary", 0),
            "process-reading-selection");
    return parseMaterialSelection(response.value(), catalog, cards);
  }

  private ObjectNode materialSelectionInput(
      CatalogResult catalog,
      List<ActivityIndexCard> cards,
      FrozenCorpus corpus,
      FrozenProcessSourceCorpus sourceText,
      String focusQuestion) {
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.put("task", MATERIAL_SELECTION);
    ArrayNode candidates = input.putArray("savedCatalogCandidates");
    catalog.candidates().forEach(candidate -> candidates.add(candidate.toJson()));
    ArrayNode dispositions = input.putArray("savedActivityDispositions");
    catalog
        .activityDispositions()
        .forEach(
            disposition -> {
              ObjectNode item = dispositions.addObject();
              item.put("activityId", disposition.activityId());
              item.put("disposition", disposition.disposition());
              item.put("reason", disposition.reason());
            });
    ArrayNode activities = input.putArray("activityIndexCards");
    cards.forEach(card -> activities.add(card.toNavigationJson(corpus)));
    ArrayNode files = input.putArray("files");
    if (sourceText != null) {
      sourceText
          .files()
          .forEach(
              file -> {
                ObjectNode item = files.addObject();
                item.put("fileKey", file.fileKey());
                item.put("path", file.path());
                item.put("mediaType", file.mediaType());
                item.put("sizeBytes", file.sizeBytes());
                item.put("lineCount", file.lineCount());
              });
    }
    if (focusQuestion == null) {
      input.putNull("focusQuestion");
    } else {
      input.put("focusQuestion", focusQuestion);
    }
    input.put("instruction", "只提出跨活动候选和实际阅读请求；旧目录是输入，不重新执行目录发现，也不编写详细过程。");
    return input;
  }

  private MaterialSelection parseMaterialSelection(
      ObjectNode value, CatalogResult baseline, List<ActivityIndexCard> cards) {
    Set<String> activityIds =
        cards.stream().map(ActivityIndexCard::activityId).collect(Collectors.toSet());
    Map<String, Candidate> changes = new LinkedHashMap<>();
    Map<String, List<SourceReadRequest>> initialRequests = new LinkedHashMap<>();
    for (JsonNode item : array(value, "candidateChanges")) {
      ObjectNode change = object(item);
      String localId = text(change, "candidateLocalId");
      Candidate candidate = candidate(change, activityIds, true);
      if (changes.put(localId, candidate) != null) {
        throw failure("PROCESS_MATERIAL_SELECTION_DUPLICATE_CANDIDATE");
      }
      initialRequests.put(localId, sourceReadRequests(array(change, "initialReadingRequests")));
    }
    Map<String, Candidate> original =
        baseline.candidates().stream()
            .collect(Collectors.toMap(Candidate::localId, Function.identity()));
    Set<String> replaced = new HashSet<>();
    Set<String> decided = new HashSet<>();
    for (JsonNode item : array(value, "oldCandidateDecisions")) {
      ObjectNode decision = object(item);
      String localId = text(decision, "candidateLocalId");
      String disposition = text(decision, "disposition");
      if (!original.containsKey(localId) || !decided.add(localId)) {
        throw failure("PROCESS_MATERIAL_SELECTION_OLD_CANDIDATE_INVALID");
      }
      if ("KEEP".equals(disposition)) {
        if (!array(decision, "replacementCandidateLocalIds").isEmpty()) {
          throw failure("PROCESS_MATERIAL_SELECTION_OLD_CANDIDATE_INVALID");
        }
      } else if ("REPLACE".equals(disposition)) {
        List<String> replacements = strings(decision, "replacementCandidateLocalIds");
        if (replacements.isEmpty() || !changes.keySet().containsAll(replacements)) {
          throw failure("PROCESS_MATERIAL_SELECTION_OLD_CANDIDATE_INVALID");
        }
        replaced.add(localId);
      } else {
        throw failure("PROCESS_MATERIAL_SELECTION_OLD_CANDIDATE_INVALID");
      }
      text(decision, "reason");
    }
    List<Candidate> candidates = new ArrayList<>();
    original.values().stream()
        .filter(candidate -> !replaced.contains(candidate.localId()))
        .forEach(candidates::add);
    candidates.addAll(changes.values());
    if (candidates.stream().map(Candidate::candidateId).distinct().count() != candidates.size()) {
      throw failure("PROCESS_MATERIAL_SELECTION_DUPLICATE_CANDIDATE");
    }
    candidates.sort(Comparator.comparing(Candidate::candidateId, UTF8_ORDER));
    return new MaterialSelection(
        baseline,
        List.copyOf(candidates),
        Map.copyOf(initialRequests),
        activityDispositions(array(value, "changedActivityDispositions"), cards));
  }

  private ReadingPacket prepareReadingPacket(
      int ordinal,
      Candidate candidate,
      List<SourceReadRequest> initialRequests,
      List<ProcessCoverage.ActivityDisposition> selectionChanges,
      List<ActivityIndexCard> cards,
      FrozenCorpus corpus,
      FrozenProcessSourceCorpus sourceText,
      ProcessDiscoveryRequest request) {
    PacketMaterials initial =
        packetMaterials(
            candidate,
            candidate.contextActivityIds(),
            initialRequests,
            corpus,
            sourceText,
            List.of());
    ReadingPacket initialPacket =
        new ReadingPacket(
            ordinal,
            candidate,
            initial.activityIds(),
            initial.sources(),
            initial.limitations(),
            selectionChanges,
            List.of());
    ObjectNode input = readingCheckInput(initialPacket, corpus, cards, sourceText);
    requireInputCapacity(input, request.profile(), "PROCESS_READING_CHECK_INPUT_CAPACITY_EXCEEDED");
    ModelCall response =
        singleDecision(
            READING_CHECK,
            "process-reading-check-" + idSuffix(candidate.candidateId()),
            input,
            readingCheckSchema(initialPacket, corpus, cards, sourceText),
            request.profile(),
            binding("processGroup", ordinal),
            "process-reading-check");
    ReadingCheck check = parseReadingCheck(response.value(), candidate, cards);
    PacketMaterials supplemented =
        packetMaterials(
            check.candidate(),
            check.contextActivityIds(),
            initialRequests,
            corpus,
            sourceText,
            check.supplementaryRequests());
    List<ProcessCoverage.ActivityDisposition> changes = new ArrayList<>(selectionChanges);
    changes.addAll(check.changedDispositions());
    return new ReadingPacket(
        ordinal,
        check.candidate(),
        supplemented.activityIds(),
        supplemented.sources(),
        supplemented.limitations(),
        List.copyOf(changes),
        check.unresolvedQuestions());
  }

  private List<ReadingPacket> prepareReadingPackets(
      CatalogSample sample, List<Candidate> candidates) {
    List<IndexedCandidateSelection> selected = new ArrayList<>();
    for (int ordinal = 0; ordinal < sample.catalog.candidates().size(); ordinal++) {
      Candidate candidate = sample.catalog.candidates().get(ordinal);
      if (candidates.contains(candidate)) {
        selected.add(new IndexedCandidateSelection(ordinal, candidate));
      }
    }
    if (modelJobs == null) {
      return selected.stream()
          .map(
              selection ->
                  prepareReadingPacket(
                      selection.ordinal(),
                      selection.candidate(),
                      sample
                          .selection
                          .initialRequests()
                          .getOrDefault(selection.candidate().localId(), List.of()),
                      sample.selection.changedDispositions(),
                      sample.cards,
                      sample.corpus,
                      sample.sourceText,
                      sample.request))
          .toList();
    }
    List<BoundedModelJobExecutor.ModelJob<ReadingPacket>> jobs = new ArrayList<>();
    for (IndexedCandidateSelection selection : selected) {
      Candidate candidate = selection.candidate();
      int ordinal = selection.ordinal();
      ModelJobProviderBinding binding = binding("processGroup", ordinal);
      ObjectNode packetSeed = JsonNodeFactory.instance.objectNode();
      packetSeed.put("candidateId", candidate.candidateId());
      packetSeed.put("ordinal", ordinal);
      jobs.add(
          new BoundedModelJobExecutor.ModelJob<>(
              "process-reading-check-" + idSuffix(candidate.candidateId()),
              sha256(canonicalJson.encodeCanonical(packetSeed)),
              binding,
              () ->
                  prepareReadingPacket(
                      ordinal,
                      candidate,
                      sample
                          .selection
                          .initialRequests()
                          .getOrDefault(candidate.localId(), List.of()),
                      sample.selection.changedDispositions(),
                      sample.cards,
                      sample.corpus,
                      sample.sourceText,
                      sample.request)));
    }
    return executor().execute(jobs, ignored -> {}).stream()
        .map(BoundedModelJobExecutor.CompletedJob::result)
        .sorted(Comparator.comparingInt(ReadingPacket::ordinal))
        .toList();
  }

  private CatalogResult finalCatalog(CatalogSample sample, List<ReadingPacket> packets) {
    return sample.selection.catalogWith(
        packets.stream().map(ReadingPacket::candidate).toList(),
        packets.stream().flatMap(packet -> packet.changedDispositions().stream()).toList(),
        sample.cards);
  }

  private ObjectNode materialSelectionSchema(
      List<ActivityIndexCard> cards, FrozenCorpus corpus, FrozenProcessSourceCorpus sourceText) {
    ObjectNode root = objectSchema();
    ObjectNode properties = root.putObject("properties");
    properties.set(
        "candidateChanges", arraySchema(selectionCandidateSchema(cards, corpus, sourceText)));
    properties.set("oldCandidateDecisions", arraySchema(oldCandidateDecisionSchema(cards)));
    properties.set("changedActivityDispositions", arraySchema(activityDispositionSchema(cards)));
    required(root, "candidateChanges", "oldCandidateDecisions", "changedActivityDispositions");
    return root;
  }

  private ObjectNode readingCheckSchema(
      ReadingPacket packet,
      FrozenCorpus corpus,
      List<ActivityIndexCard> cards,
      FrozenProcessSourceCorpus sourceText) {
    ObjectNode root = objectSchema();
    List<String> activityIds = cardIds(cards);
    List<String> fileKeys =
        sourceText == null
            ? List.of()
            : sourceText.files().stream()
                .map(FrozenProcessSourceCorpus.SourceFile::fileKey)
                .sorted(UTF8_ORDER)
                .toList();
    ObjectNode definitions = root.putObject("$defs");
    definitions.set("globalActivityId", enumSchema(activityIds));
    definitions.set("frozenFileKey", enumSchema(fileKeys));
    ObjectNode properties = root.putObject("properties");
    properties.set("name", nullableTextSchema());
    properties.set("purpose", nullableTextSchema());
    properties.set("scope", nullableTextSchema());
    ObjectNode activityUses = arraySchema(readingCheckCandidateUseSchema());
    activityUses.put("minItems", 1);
    properties.set("activityUses", activityUses);
    properties.set("contextActivityIds", enumArrayReferenceSchema("globalActivityId", activityIds));
    properties.set(
        "supplementaryRequests", arraySchema(readingCheckSourceReadRequestSchema(corpus)));
    properties.set("unresolvedQuestions", stringsSchema());
    properties.set(
        "changedActivityDispositions", arraySchema(readingCheckActivityDispositionSchema()));
    required(
        root,
        "name",
        "purpose",
        "scope",
        "activityUses",
        "contextActivityIds",
        "supplementaryRequests",
        "unresolvedQuestions",
        "changedActivityDispositions");
    return root;
  }

  private ObjectNode readingCheckCandidateUseSchema() {
    ObjectNode use = objectSchema();
    ObjectNode properties = use.putObject("properties");
    properties.set("activityId", definitionReference("globalActivityId"));
    properties.set("role", enumSchema(CANDIDATE_ROLES.stream().sorted().toList()));
    properties.set("variant", textSchema());
    required(use, "activityId", "role", "variant");
    return use;
  }

  private ObjectNode readingCheckActivityDispositionSchema() {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("activityId", definitionReference("globalActivityId"));
    properties.set("disposition", enumSchema(ACTIVITY_DISPOSITIONS.stream().sorted().toList()));
    properties.set("reason", textSchema());
    required(schema, "activityId", "disposition", "reason");
    return schema;
  }

  private ObjectNode readingCheckSourceReadRequestSchema(FrozenCorpus corpus) {
    List<String> sourceRefs =
        corpus.sourceReferences().stream().map(SourceReference::ref).sorted(UTF8_ORDER).toList();
    ObjectNode variants = JsonNodeFactory.instance.objectNode();
    ArrayNode anyOf = variants.putArray("anyOf");
    anyOf.add(sourceReadRequestVariant("SOURCE_REF", enumSchema(sourceRefs), null, false, false));
    anyOf.add(
        sourceReadRequestVariant(
            "WHOLE_FILE", null, definitionReference("frozenFileKey"), false, false));
    anyOf.add(
        sourceReadRequestVariant(
            "FILE_RANGE", null, definitionReference("frozenFileKey"), true, false));
    anyOf.add(
        sourceReadRequestVariant(
            "LITERAL_SEARCH", null, definitionReference("frozenFileKey"), false, true));
    return variants;
  }

  private ObjectNode selectionCandidateSchema(
      List<ActivityIndexCard> cards, FrozenCorpus corpus, FrozenProcessSourceCorpus sourceText) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("candidateLocalId", textSchema());
    properties.set("name", textSchema());
    properties.set("purpose", textSchema());
    properties.set("scope", textSchema());
    properties.set("activityUses", arraySchema(candidateUseSchema(cards)));
    properties.set("contextActivityIds", enumArraySchema(cardIds(cards)));
    properties.set(
        "initialReadingRequests", arraySchema(sourceReadRequestSchema(corpus, sourceText)));
    required(
        schema,
        "candidateLocalId",
        "name",
        "purpose",
        "scope",
        "activityUses",
        "contextActivityIds",
        "initialReadingRequests");
    return schema;
  }

  private ObjectNode oldCandidateDecisionSchema(List<ActivityIndexCard> cards) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("candidateLocalId", textSchema());
    properties.set("disposition", enumSchema(List.of("KEEP", "REPLACE")));
    properties.set("replacementCandidateLocalIds", stringsSchema());
    properties.set("reason", textSchema());
    required(schema, "candidateLocalId", "disposition", "replacementCandidateLocalIds", "reason");
    return schema;
  }

  private ObjectNode candidateUseSchema(List<ActivityIndexCard> cards) {
    ObjectNode use = objectSchema();
    ObjectNode properties = use.putObject("properties");
    properties.set("activityId", enumSchema(cardIds(cards)));
    properties.set("role", enumSchema(CANDIDATE_ROLES.stream().sorted().toList()));
    properties.set("variant", textSchema());
    required(use, "activityId", "role", "variant");
    return use;
  }

  private ObjectNode sourceReadRequestSchema(
      FrozenCorpus corpus, FrozenProcessSourceCorpus sourceText) {
    List<String> sourceRefs =
        corpus.sourceReferences().stream().map(SourceReference::ref).sorted(UTF8_ORDER).toList();
    List<String> fileKeys =
        sourceText == null
            ? List.of()
            : sourceText.files().stream()
                .map(FrozenProcessSourceCorpus.SourceFile::fileKey)
                .sorted(UTF8_ORDER)
                .toList();
    ObjectNode variants = JsonNodeFactory.instance.objectNode();
    ArrayNode anyOf = variants.putArray("anyOf");
    anyOf.add(sourceReadRequestVariant("SOURCE_REF", enumSchema(sourceRefs), null, false, false));
    anyOf.add(sourceReadRequestVariant("WHOLE_FILE", null, enumSchema(fileKeys), false, false));
    anyOf.add(sourceReadRequestVariant("FILE_RANGE", null, enumSchema(fileKeys), true, false));
    anyOf.add(sourceReadRequestVariant("LITERAL_SEARCH", null, enumSchema(fileKeys), false, true));
    return variants;
  }

  private ObjectNode sourceReadRequestVariant(
      String kind, ObjectNode sourceRef, ObjectNode fileKey, boolean range, boolean literalSearch) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("requestId", textSchema());
    properties.set("kind", enumSchema(List.of(kind)));
    if (sourceRef != null) {
      properties.set("sourceRef", sourceRef);
      required(schema, "requestId", "kind", "sourceRef", "purpose");
    } else {
      properties.set("fileKey", fileKey);
      if (range) {
        properties.set("startLine", integerSchema());
        properties.set("endLine", integerSchema());
        required(schema, "requestId", "kind", "fileKey", "startLine", "endLine", "purpose");
      } else if (literalSearch) {
        properties.set("literal", textSchema());
        ObjectNode contextLines = JsonNodeFactory.instance.objectNode();
        contextLines.put("type", "integer");
        contextLines.put("minimum", 0);
        properties.set("contextLines", contextLines);
        required(schema, "requestId", "kind", "fileKey", "literal", "contextLines", "purpose");
      } else {
        required(schema, "requestId", "kind", "fileKey", "purpose");
      }
    }
    properties.set("purpose", textSchema());
    return schema;
  }

  private Candidate candidate(ObjectNode value, Set<String> activityIds, boolean requiresScope) {
    String localId = text(value, "candidateLocalId");
    List<CandidateUse> uses = candidateUses(array(value, "activityUses"), activityIds);
    String name = text(value, "name");
    String purpose = text(value, "purpose");
    String scope = requiresScope ? text(value, "scope") : purpose;
    List<String> contexts =
        requiresScope
            ? contextActivityIds(array(value, "contextActivityIds"), activityIds, uses)
            : List.of();
    return new Candidate(
        candidateId(name, purpose, uses), localId, name, purpose, scope, uses, contexts);
  }

  private List<CandidateUse> candidateUses(ArrayNode values, Set<String> activityIds) {
    List<CandidateUse> uses = new ArrayList<>();
    Set<CandidateActivityVariant> unique = new HashSet<>();
    for (JsonNode item : values) {
      ObjectNode value = object(item);
      String activityId = text(value, "activityId");
      String role = text(value, "role");
      String variant = text(value, "variant");
      if (!activityIds.contains(activityId)
          || !CANDIDATE_ROLES.contains(role)
          || !unique.add(new CandidateActivityVariant(activityId, variant))) {
        throw failure("PROCESS_READING_CANDIDATE_INVALID");
      }
      uses.add(new CandidateUse(activityId, role, variant));
    }
    if (uses.isEmpty()) {
      throw failure("PROCESS_READING_CANDIDATE_INVALID");
    }
    return List.copyOf(uses);
  }

  private static List<String> contextActivityIds(
      ArrayNode values, Set<String> activityIds, List<CandidateUse> uses) {
    Set<String> members = uses.stream().map(CandidateUse::activityId).collect(Collectors.toSet());
    List<String> contexts = strings(values).stream().distinct().sorted(UTF8_ORDER).toList();
    if (contexts.size() != values.size()
        || !activityIds.containsAll(contexts)
        || contexts.stream().anyMatch(members::contains)) {
      throw failure("PROCESS_READING_CONTEXT_ACTIVITY_INVALID");
    }
    return contexts;
  }

  private List<ProcessCoverage.ActivityDisposition> activityDispositions(
      ArrayNode values, List<ActivityIndexCard> cards) {
    Map<String, ActivityIndexCard> cardsById =
        cards.stream()
            .collect(Collectors.toMap(ActivityIndexCard::activityId, Function.identity()));
    List<ProcessCoverage.ActivityDisposition> dispositions = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JsonNode item : values) {
      ObjectNode value = object(item);
      String activityId = text(value, "activityId");
      String disposition = text(value, "disposition");
      ActivityIndexCard card = cardsById.get(activityId);
      if (card == null || !seen.add(activityId) || !ACTIVITY_DISPOSITIONS.contains(disposition)) {
        throw failure("PROCESS_READING_ACTIVITY_DISPOSITION_INVALID");
      }
      dispositions.add(
          new ProcessCoverage.ActivityDisposition(
              activityId, card.name(), disposition, text(value, "reason")));
    }
    return List.copyOf(dispositions);
  }

  private List<SourceReadRequest> sourceReadRequests(ArrayNode values) {
    List<SourceReadRequest> requests = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode item : values) {
      ObjectNode value = object(item);
      String requestId = text(value, "requestId");
      String kind = text(value, "kind");
      if (!ids.add(requestId)
          || !Set.of("SOURCE_REF", "WHOLE_FILE", "FILE_RANGE", "LITERAL_SEARCH").contains(kind)) {
        throw failure("PROCESS_READING_REQUEST_INVALID");
      }
      String sourceRef = optionalNullableText(value.path("sourceRef"));
      String fileKey = optionalNullableText(value.path("fileKey"));
      String literal = optionalNullableText(value.path("literal"));
      int startLine = optionalPositiveInt(value.path("startLine"));
      int endLine = optionalPositiveInt(value.path("endLine"));
      int contextLines = optionalNonNegativeInt(value.path("contextLines"));
      if (("SOURCE_REF".equals(kind) && sourceRef == null)
          || (("WHOLE_FILE".equals(kind)
                  || "FILE_RANGE".equals(kind)
                  || "LITERAL_SEARCH".equals(kind))
              && fileKey == null)
          || ("FILE_RANGE".equals(kind) && (startLine < 1 || endLine < startLine))
          || ("LITERAL_SEARCH".equals(kind) && (literal == null || contextLines < 0))) {
        throw failure("PROCESS_READING_REQUEST_INVALID");
      }
      text(value, "purpose");
      requests.add(
          new SourceReadRequest(
              requestId, kind, sourceRef, fileKey, startLine, endLine, literal, contextLines));
    }
    requests.sort(Comparator.comparing(SourceReadRequest::requestId, UTF8_ORDER));
    return List.copyOf(requests);
  }

  private PacketMaterials packetMaterials(
      Candidate candidate,
      List<String> contextActivityIds,
      List<SourceReadRequest> initialRequests,
      FrozenCorpus corpus,
      FrozenProcessSourceCorpus sourceText,
      List<SourceReadRequest> supplementaryRequests) {
    List<SourceReadRequest> requests = new ArrayList<>(initialRequests);
    requests.addAll(supplementaryRequests);
    Set<String> memberIds =
        candidate.uses().stream().map(CandidateUse::activityId).collect(Collectors.toSet());
    Set<String> activityIds = new LinkedHashSet<>(memberIds);
    for (String activityId : contextActivityIds) {
      if (!corpus.activityIds().contains(activityId) || memberIds.contains(activityId)) {
        throw failure("PROCESS_READING_CONTEXT_ACTIVITY_INVALID");
      }
      activityIds.add(activityId);
    }
    Map<String, SourceReference> sources = new LinkedHashMap<>();
    Set<String> reservedSourceReferences =
        corpus.sourceReferences().stream()
            .map(SourceReference::ref)
            .collect(Collectors.toUnmodifiableSet());
    List<String> limitations = new ArrayList<>();
    int nextLocalSource = 1;
    for (SourceReadRequest request : requests) {
      if ("SOURCE_REF".equals(request.kind())) {
        sources.put(request.sourceRef(), corpus.source(request.sourceRef()));
      } else if (sourceText == null) {
        limitations.add(request.requestId() + ": no frozen text reader was supplied");
      } else if ("WHOLE_FILE".equals(request.kind())) {
        FrozenProcessSourceCorpus.SourceText read =
            sourceText.read(request.fileKey(), FrozenProcessSourceCorpus.ReadRange.wholeFile());
        nextLocalSource =
            addReadSource(
                sources,
                reservedSourceReferences,
                nextLocalSource,
                read.path(),
                read.startLine(),
                read.endLine(),
                read.text());
      } else if ("FILE_RANGE".equals(request.kind())) {
        FrozenProcessSourceCorpus.SourceText read =
            sourceText.read(
                request.fileKey(),
                FrozenProcessSourceCorpus.ReadRange.of(request.startLine(), request.endLine()));
        nextLocalSource =
            addReadSource(
                sources,
                reservedSourceReferences,
                nextLocalSource,
                read.path(),
                read.startLine(),
                read.endLine(),
                read.text());
      } else {
        List<FrozenProcessSourceCorpus.SearchHit> hits =
            sourceText.search(
                List.of(request.fileKey()), request.literal(), request.contextLines());
        if (hits.isEmpty()) {
          limitations.add(request.requestId() + ": literal not found");
        }
        for (FrozenProcessSourceCorpus.SearchHit hit : hits) {
          nextLocalSource =
              addReadSource(
                  sources,
                  reservedSourceReferences,
                  nextLocalSource,
                  hit.path(),
                  hit.startLine(),
                  hit.endLine(),
                  hit.text());
        }
      }
    }
    return new PacketMaterials(
        activityIds.stream().sorted(UTF8_ORDER).toList(),
        sources.values().stream()
            .sorted(Comparator.comparing(SourceReference::ref, UTF8_ORDER))
            .toList(),
        List.copyOf(limitations));
  }

  private int addReadSource(
      Map<String, SourceReference> sources,
      Set<String> reservedSourceReferences,
      int nextLocalSource,
      String file,
      int startLine,
      int endLine,
      String snippet) {
    for (SourceReference source : sources.values()) {
      if (source.file().equals(file)
          && source.startLine() == startLine
          && source.endLine() == endLine
          && source.snippet().equals(snippet)) {
        return nextLocalSource;
      }
    }
    String ref = "S" + nextLocalSource;
    while (sources.containsKey(ref) || reservedSourceReferences.contains(ref)) {
      ref = "S" + ++nextLocalSource;
    }
    sources.put(ref, new SourceReference(ref, file, startLine, endLine, snippet));
    return nextLocalSource + 1;
  }

  private ObjectNode readingCheckInput(
      ReadingPacket packet,
      FrozenCorpus corpus,
      List<ActivityIndexCard> cards,
      FrozenProcessSourceCorpus sourceText) {
    ObjectNode input = packetInput(packet, corpus);
    input.put("task", READING_CHECK);
    ArrayNode navigation = input.putArray("activityIndexCards");
    cards.forEach(card -> navigation.add(card.toNavigationJson(corpus)));
    ArrayNode files = input.putArray("files");
    if (sourceText != null) {
      sourceText
          .files()
          .forEach(
              file -> {
                ObjectNode value = files.addObject();
                value.put("fileKey", file.fileKey());
                value.put("path", file.path());
                value.put("mediaType", file.mediaType());
                value.put("lineCount", file.lineCount());
              });
    }
    return input;
  }

  private ReadingCheck parseReadingCheck(
      ObjectNode value, Candidate previous, List<ActivityIndexCard> cards) {
    Set<String> activityIds =
        cards.stream().map(ActivityIndexCard::activityId).collect(Collectors.toSet());
    List<CandidateUse> uses = candidateUses(array(value, "activityUses"), activityIds);
    String name = optionalText(value.path("name"), previous.name());
    String purpose = optionalText(value.path("purpose"), previous.purpose());
    String scope = optionalText(value.path("scope"), previous.scope());
    List<String> contexts =
        contextActivityIds(array(value, "contextActivityIds"), activityIds, uses);
    Candidate candidate =
        new Candidate(
            candidateId(name, purpose, uses),
            previous.localId(),
            name,
            purpose,
            scope,
            uses,
            contexts);
    return new ReadingCheck(
        candidate,
        contexts,
        sourceReadRequests(array(value, "supplementaryRequests")),
        activityDispositions(array(value, "changedActivityDispositions"), cards),
        strings(value, "unresolvedQuestions"));
  }

  private static int optionalPositiveInt(JsonNode value) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      return 0;
    }
    return value.isInt() && value.intValue() > 0 ? value.intValue() : -1;
  }

  private static int optionalNonNegativeInt(JsonNode value) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      return 0;
    }
    return value.isInt() && value.intValue() >= 0 ? value.intValue() : -1;
  }

  private CatalogResult discoverCatalog(
      List<ActivityIndexCard> cards, ProcessDiscoveryProfile profile) {
    List<List<ActivityIndexCard>> shards = catalogShards(cards, profile);
    if (shards.size() == 1) {
      ObjectNode input = catalogInput(shards.get(0), List.of());
      ObjectNode reviewed =
          draftAndReview(
              CATALOG_DRAFT,
              CATALOG_REVIEW,
              "business-catalog",
              input,
              catalogSchema(shards.get(0)),
              profile,
              binding("repositorySummary", 0),
              "process-catalog");
      return parseCatalog(reviewed, cards);
    }
    List<ObjectNode> shardCatalogs = discoverCatalogShards(shards, profile);
    ObjectNode mergeInput = catalogInput(cards, shardCatalogs);
    requireInputCapacity(mergeInput, profile, "PROCESS_CATALOG_MERGE_INPUT_CAPACITY_EXCEEDED");
    ReviewedPair reviewed =
        draftAndReviewPair(
            CATALOG_MERGE_DRAFT,
            CATALOG_MERGE_REVIEW,
            "business-catalog-merge",
            mergeInput,
            mergedCatalogSchema(cards),
            profile,
            binding("repositorySummary", 0),
            "process-catalog");
    return parseMergedCatalogReview(reviewed.draft().value(), reviewed.review().value(), cards);
  }

  private List<ObjectNode> discoverCatalogShards(
      List<List<ActivityIndexCard>> shards, ProcessDiscoveryProfile profile) {
    if (modelJobs == null) {
      List<ObjectNode> result = new ArrayList<>();
      for (int index = 0; index < shards.size(); index++) {
        result.add(discoverCatalogShard(shards.get(index), index, profile));
      }
      return List.copyOf(result);
    }
    List<BoundedModelJobExecutor.ModelJob<IndexedJson>> jobs = new ArrayList<>();
    for (int index = 0; index < shards.size(); index++) {
      int ordinal = index;
      ObjectNode input = catalogInput(shards.get(index), List.of());
      ObjectNode schema = catalogSchema(shards.get(index));
      ModelJobProviderBinding binding = binding("processGroup", ordinal);
      jobs.add(
          new BoundedModelJobExecutor.ModelJob<>(
              "catalog-shard-" + ordinal,
              inputFingerprint(
                  CATALOG_SHARD_DRAFT, CATALOG_SHARD_REVIEW, input, schema, profile, binding),
              binding,
              () ->
                  new IndexedJson(
                      ordinal, discoverCatalogShard(shards.get(ordinal), ordinal, profile))));
    }
    return executor().execute(jobs, ignored -> {}).stream()
        .map(BoundedModelJobExecutor.CompletedJob::result)
        .sorted(Comparator.comparingInt(IndexedJson::ordinal))
        .map(IndexedJson::value)
        .toList();
  }

  private ObjectNode discoverCatalogShard(
      List<ActivityIndexCard> shard, int ordinal, ProcessDiscoveryProfile profile) {
    ObjectNode reviewed =
        draftAndReview(
            CATALOG_SHARD_DRAFT,
            CATALOG_SHARD_REVIEW,
            "business-catalog-shard-" + ordinal,
            catalogInput(shard, List.of()),
            catalogSchema(shard),
            profile,
            binding("processGroup", ordinal),
            "process-catalog");
    parseCatalog(reviewed, shard);
    return reviewed;
  }

  private List<CandidateResult> reconstructReadingPackets(
      List<ReadingPacket> packets,
      SourceNormalization sourceNormalization,
      FrozenCorpus corpus,
      ProcessDiscoveryProfile profile) {
    Map<Integer, CandidateResult> completedByOrdinal = new LinkedHashMap<>();
    if (modelJobs != null) {
      List<BoundedModelJobExecutor.ModelJob<IndexedRawCandidate>> jobs = new ArrayList<>();
      for (ReadingPacket packet : packets) {
        ModelJobProviderBinding binding = binding("processGroup", packet.ordinal());
        ObjectNode packetSeed = JsonNodeFactory.instance.objectNode();
        packetSeed.put("candidateId", packet.candidate().candidateId());
        packetSeed.put("ordinal", packet.ordinal());
        jobs.add(
            new BoundedModelJobExecutor.ModelJob<>(
                "candidate-" + idSuffix(packet.candidate().candidateId()),
                sha256(canonicalJson.encodeCanonical(packetSeed)),
                binding,
                () ->
                    new IndexedRawCandidate(
                        packet.ordinal(), reconstructRaw(packet, corpus, profile, binding))));
      }
      executor()
          .execute(
              jobs,
              completed -> {
                IndexedRawCandidate raw = completed.result();
                CandidateResult candidate =
                    finalizeCandidate(raw.value(), sourceNormalization, corpus, profile);
                if (completedByOrdinal.put(raw.ordinal(), candidate) != null) {
                  throw failure("PROCESS_READING_PACKET_ORDINAL_DUPLICATE");
                }
              });
    } else {
      for (ReadingPacket packet : packets) {
        CandidateResult candidate =
            finalizeCandidate(
                reconstructRaw(packet, corpus, profile, binding("processGroup", packet.ordinal())),
                sourceNormalization,
                corpus,
                profile);
        if (completedByOrdinal.put(packet.ordinal(), candidate) != null) {
          throw failure("PROCESS_READING_PACKET_ORDINAL_DUPLICATE");
        }
      }
    }
    return completedByOrdinal.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(Map.Entry::getValue)
        .toList();
  }

  private RawCandidateResult reconstructRaw(
      ReadingPacket packet,
      FrozenCorpus corpus,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding) {
    Candidate candidate = packet.candidate();
    if (candidate.uses().size() > profile.maxActivitiesPerCandidate()) {
      return RawCandidateResult.notProcessed(packet, "NOT_PROCESSED_CAPACITY");
    }
    ObjectNode input = processInput(packet, corpus);
    if (canonicalJson.encodeCanonical(input).size() > profile.maxModelInputBytes()) {
      return RawCandidateResult.notProcessed(packet, "NOT_PROCESSED_CAPACITY");
    }
    ObjectNode schema = processSchema(packet, corpus);
    String taskBase = "business-process-" + idSuffix(candidate.candidateId());
    String fingerprint =
        inputFingerprint(PROCESS_DRAFT, PROCESS_REVIEW, input, schema, profile, binding);
    ReviewedPair reused = reopenProcessPair(taskBase, fingerprint, binding);
    if (reused != null) {
      return new RawCandidateResult(
          packet, reused.draft(), reused.review(), fingerprint, binding, true);
    }
    ModelCall draft = call(PROCESS_DRAFT, taskBase + "-draft", input, schema, profile, binding);
    ObjectNode reviewInput = input.deepCopy();
    reviewInput.set("actualDraft", draft.value());
    requireInputCapacity(reviewInput, profile, "PROCESS_REVIEW_INPUT_CAPACITY_EXCEEDED");
    ModelCall review =
        call(PROCESS_REVIEW, taskBase + "-review", reviewInput, schema, profile, binding);
    if (!draft.runtimeIdentity().equals(review.runtimeIdentity())) {
      throw failure("PROCESS_JOB_RUNTIME_IDENTITY_MISMATCH");
    }
    return new RawCandidateResult(packet, draft, review, fingerprint, binding, false);
  }

  private CandidateResult finalizeCandidate(
      RawCandidateResult raw,
      SourceNormalization sourceNormalization,
      FrozenCorpus corpus,
      ProcessDiscoveryProfile profile) {
    if (raw.review() == null) {
      return CandidateResult.notProcessed(raw.packet().candidate(), raw.capacityDisposition());
    }
    ReadingPacket finalPacket = sourceNormalization.finalPacket(raw.packet().ordinal());
    List<SourceReferenceMapping> sourceMapping =
        sourceNormalization.mappingFor(raw.packet().ordinal());
    ObjectNode remappedReview = remapSourceReferences(raw.review().value(), sourceMapping);
    ParsedCandidateDraft reviewed = parseCandidate(remappedReview, finalPacket, corpus, profile);
    saveProcessPair(
        "business-process-" + idSuffix(raw.packet().candidate().candidateId()),
        raw.inputFingerprint(),
        raw.binding(),
        raw.draft(),
        raw.review(),
        raw.packet(),
        corpus,
        sourceMapping,
        raw.reused());
    return new CandidateResult(
        raw.packet().candidate(),
        reviewed.disposition(),
        reviewed.reason(),
        reviewed.processes(),
        remappedReview);
  }

  private List<List<ActivityIndexCard>> catalogShards(
      List<ActivityIndexCard> cards, ProcessDiscoveryProfile profile) {
    if (cards.isEmpty()) {
      return List.of(List.of());
    }
    List<List<ActivityIndexCard>> result = new ArrayList<>();
    List<ActivityIndexCard> current = new ArrayList<>();
    for (ActivityIndexCard card : cards) {
      List<ActivityIndexCard> proposed = new ArrayList<>(current);
      proposed.add(card);
      boolean countExceeded = proposed.size() > profile.maxCardsPerCatalogShard();
      boolean bytesExceeded =
          canonicalJson.encodeCanonical(catalogInput(proposed, List.of())).size()
              > profile.maxModelInputBytes();
      if (!current.isEmpty() && (countExceeded || bytesExceeded)) {
        result.add(List.copyOf(current));
        current.clear();
        current.add(card);
        requireInputCapacity(
            catalogInput(current, List.of()),
            profile,
            "PROCESS_CATALOG_CARD_INPUT_CAPACITY_EXCEEDED");
      } else {
        current.add(card);
      }
    }
    if (!current.isEmpty()) {
      result.add(List.copyOf(current));
    }
    return List.copyOf(result);
  }

  private Consolidated consolidate(
      CatalogResult catalog,
      List<CandidateResult> candidates,
      FrozenCorpus corpus,
      ProcessDiscoveryProfile profile) {
    List<RepositoryBusinessProcessCatalog.BusinessProcess> reviewedProcesses =
        candidates.stream().flatMap(value -> value.processes().stream()).toList();
    ConsolidationDecision decision;
    if (reviewedProcesses.isEmpty()) {
      decision = new ConsolidationDecision(List.of(), List.of(), List.of(), List.of());
    } else {
      ObjectNode input = consolidationInput(catalog, reviewedProcesses);
      requireInputCapacity(input, profile, "PROCESS_CONSOLIDATION_INPUT_CAPACITY_EXCEEDED");
      ReviewedPair reviewed =
          draftAndReviewPair(
              CONSOLIDATION_DRAFT,
              CONSOLIDATION_REVIEW,
              "business-process-consolidation",
              input,
              consolidationSchema(reviewedProcesses),
              profile,
              binding("repositorySummary", 0),
              "process-consolidation");
      decision =
          parseConsolidation(
              completeConsolidationDecisions(reviewed.review().value(), reviewedProcesses),
              reviewedProcesses);
      decision = preserveNonLosslessMerges(decision, reviewedProcesses);
    }
    RepositoryBusinessProcessCatalog finalCatalog =
        applyConsolidation(catalog, reviewedProcesses, decision, corpus);
    ProcessCoverage coverage = coverage(catalog, candidates, decision, finalCatalog, corpus);
    return new Consolidated(finalCatalog, coverage);
  }

  private ObjectNode draftAndReview(
      String draftKind,
      String reviewKind,
      String taskBase,
      ObjectNode input,
      ObjectNode schema,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding,
      String phase) {
    return draftAndReviewPair(
            draftKind, reviewKind, taskBase, input, schema, profile, binding, phase)
        .review()
        .value();
  }

  private ReviewedPair draftAndReviewPair(
      String draftKind,
      String reviewKind,
      String taskBase,
      ObjectNode input,
      ObjectNode schema,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding,
      String phase) {
    requireInputCapacity(input, profile, "PROCESS_MODEL_INPUT_CAPACITY_EXCEEDED");
    String fingerprint = inputFingerprint(draftKind, reviewKind, input, schema, profile, binding);
    ReviewedPair reused = reopenPair(phase, taskBase, fingerprint, binding);
    if (reused != null) {
      return reused;
    }
    ModelCall draft = call(draftKind, taskBase + "-draft", input, schema, profile, binding);
    ObjectNode reviewInput = input.deepCopy();
    reviewInput.set("actualDraft", draft.value());
    requireInputCapacity(reviewInput, profile, "PROCESS_MODEL_REVIEW_INPUT_CAPACITY_EXCEEDED");
    ModelCall review =
        call(reviewKind, taskBase + "-review", reviewInput, schema, profile, binding);
    if (!draft.runtimeIdentity().equals(review.runtimeIdentity())) {
      throw failure("PROCESS_JOB_RUNTIME_IDENTITY_MISMATCH");
    }
    savePair(phase, taskBase, fingerprint, binding, draft, review);
    return new ReviewedPair(draft, review);
  }

  private ModelCall call(
      String taskKind,
      String taskId,
      ObjectNode input,
      ObjectNode schema,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding) {
    StructuredModelResponse response =
        binding
            .provider()
            .generate(
                new StructuredModelRequest(
                    taskId,
                    taskKind,
                    BusinessProcessPromptCatalog.instructionsFor(taskKind),
                    canonicalJson.encodeCanonical(input),
                    canonicalJson.encodeCanonical(schema),
                    profile.maxModelOutputBytes()));
    if (binding.expectedRuntimeIdentity() != null
        && !binding.expectedRuntimeIdentity().equals(response.runtimeIdentity())) {
      throw failure("PROCESS_JOB_RUNTIME_IDENTITY_MISMATCH");
    }
    JsonNode parsed = canonicalJson.parseCanonical(response.responseJson());
    if (!(parsed instanceof ObjectNode value)) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return new ModelCall(value, response.runtimeIdentity());
  }

  /** Executes one saved reading decision; unlike a process pair it has no DRAFT/REVIEW wrapper. */
  private ModelCall singleDecision(
      String taskKind,
      String jobKey,
      ObjectNode input,
      ObjectNode schema,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding,
      String phase) {
    String fingerprint = decisionFingerprint(taskKind, input, schema, profile, binding);
    if (modelJobs != null && modelJobs.reuseFromModelBatchId() != null) {
      PrivateModelJobResultStore source =
          new PrivateModelJobResultStore(
              modelJobs.journalDirectory(), modelJobs.reuseFromModelBatchId(), phase);
      ObjectNode saved =
          source
              .readCompletedDecision(
                  jobKey, fingerprint, binding.quotaScope(), binding.expectedRuntimeIdentity())
              .orElse(null);
      if (saved != null) {
        ObjectNode copied = saved.deepCopy();
        copied.put("runId", modelJobs.runId().value());
        copied.put("reusedFromModelBatchId", modelJobs.reuseFromModelBatchId().value());
        new PrivateModelJobResultStore(modelJobs.journalDirectory(), modelJobs.runId(), phase)
            .writeDecision(jobKey, copied);
        return new ModelCall(object(saved.path("response")), binding.expectedRuntimeIdentity());
      }
    }
    ModelCall response = call(taskKind, jobKey, input, schema, profile, binding);
    if (modelJobs != null) {
      ObjectNode record = JsonNodeFactory.instance.objectNode();
      record.put("schemaVersion", "process-reading-decision-v1");
      record.put("status", "COMPLETED");
      record.put("runId", modelJobs.runId().value());
      record.put("phase", phase);
      record.put("taskKind", taskKind);
      record.put("jobKey", jobKey);
      record.put("inputFingerprint", fingerprint);
      record.put("providerBindingKey", binding.key());
      record.put("quotaScope", binding.quotaScope());
      record.put("producerVersion", "v3");
      ObjectNode identity = record.putObject("runtimeIdentity");
      identity.put("upstreamProvider", response.runtimeIdentity().upstreamProvider());
      identity.put("model", response.runtimeIdentity().model());
      identity.put("reasoningEffort", response.runtimeIdentity().reasoningEffort());
      identity.put("sandbox", response.runtimeIdentity().sandbox());
      record.set("input", input);
      record.set("response", response.value());
      new PrivateModelJobResultStore(modelJobs.journalDirectory(), modelJobs.runId(), phase)
          .writeDecision(jobKey, record);
    }
    return response;
  }

  private ModelJobProviderBinding binding(String phase, int ordinal) {
    if (modelJobs == null) {
      return new ModelJobProviderBinding(
          "single-provider", "direct-single-provider", 1, provider, null);
    }
    return modelJobs.binding(phase, ordinal).forJobOrdinal(ordinal);
  }

  private BoundedModelJobExecutor executor() {
    Map<String, Integer> caps =
        modelJobs.providers().values().stream()
            .collect(
                Collectors.toMap(
                    ModelJobProviderBinding::key, ModelJobProviderBinding::maxConcurrentJobs));
    return new BoundedModelJobExecutor(modelJobs.maxConcurrentJobs(), caps);
  }

  private String inputFingerprint(
      String draftKind,
      String reviewKind,
      ObjectNode input,
      ObjectNode schema,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", "business-process-job-input-fingerprint-v2");
    value.put("moduleVersion", "repository-business-process-catalog-v2");
    value.put("providerBindingKey", binding.key());
    value.put("quotaScope", binding.quotaScope());
    value.put("inputSha256", sha256(canonicalJson.encodeCanonical(input)));
    value.put("draftInstructions", BusinessProcessPromptCatalog.instructionsFor(draftKind));
    value.put("reviewInstructions", BusinessProcessPromptCatalog.instructionsFor(reviewKind));
    value.put("outputSchemaSha256", sha256(canonicalJson.encodeCanonical(schema)));
    value.put("maxModelInputBytes", profile.maxModelInputBytes());
    value.put("maxModelOutputBytes", profile.maxModelOutputBytes());
    if (binding.expectedRuntimeIdentity() != null) {
      ObjectNode identity = value.putObject("expectedRuntimeIdentity");
      identity.put("upstreamProvider", binding.expectedRuntimeIdentity().upstreamProvider());
      identity.put("model", binding.expectedRuntimeIdentity().model());
      identity.put("reasoningEffort", binding.expectedRuntimeIdentity().reasoningEffort());
      identity.put("sandbox", binding.expectedRuntimeIdentity().sandbox());
    }
    return sha256(canonicalJson.encodeCanonical(value));
  }

  private String decisionFingerprint(
      String taskKind,
      ObjectNode input,
      ObjectNode schema,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", "process-reading-decision-input-v1");
    value.put("producerVersion", "v3");
    value.put("taskKind", taskKind);
    value.put("providerBindingKey", binding.key());
    value.put("quotaScope", binding.quotaScope());
    value.put("inputSha256", sha256(canonicalJson.encodeCanonical(input)));
    value.put("instructions", BusinessProcessPromptCatalog.instructionsFor(taskKind));
    value.put("outputSchemaSha256", sha256(canonicalJson.encodeCanonical(schema)));
    value.put("maxModelInputBytes", profile.maxModelInputBytes());
    value.put("maxModelOutputBytes", profile.maxModelOutputBytes());
    if (binding.expectedRuntimeIdentity() != null) {
      ObjectNode identity = value.putObject("expectedRuntimeIdentity");
      identity.put("upstreamProvider", binding.expectedRuntimeIdentity().upstreamProvider());
      identity.put("model", binding.expectedRuntimeIdentity().model());
      identity.put("reasoningEffort", binding.expectedRuntimeIdentity().reasoningEffort());
      identity.put("sandbox", binding.expectedRuntimeIdentity().sandbox());
    }
    return sha256(canonicalJson.encodeCanonical(value));
  }

  private ReviewedPair reopenPair(
      String phase, String jobKey, String inputFingerprint, ModelJobProviderBinding binding) {
    if (modelJobs == null || modelJobs.reuseFromModelBatchId() == null) {
      return null;
    }
    ObjectNode saved =
        new PrivateModelJobResultStore(
                modelJobs.journalDirectory(), modelJobs.reuseFromModelBatchId(), phase)
            .readCompleted(
                jobKey, inputFingerprint, binding.quotaScope(), binding.expectedRuntimeIdentity())
            .orElse(null);
    if (saved == null) {
      return null;
    }
    ModelCall draft = new ModelCall(object(saved.path("draft")), binding.expectedRuntimeIdentity());
    ModelCall review =
        new ModelCall(object(saved.path("review")), binding.expectedRuntimeIdentity());
    ObjectNode copied = saved.deepCopy();
    copied.put("runId", modelJobs.runId().value());
    copied.put("reusedFromModelBatchId", modelJobs.reuseFromModelBatchId().value());
    new PrivateModelJobResultStore(modelJobs.journalDirectory(), modelJobs.runId(), phase)
        .write(jobKey, copied);
    return new ReviewedPair(draft, review);
  }

  private ReviewedPair reopenProcessPair(
      String jobKey, String inputFingerprint, ModelJobProviderBinding binding) {
    if (modelJobs == null || modelJobs.reuseFromModelBatchId() == null) {
      return null;
    }
    ObjectNode saved =
        new PrivateModelJobResultStore(
                modelJobs.journalDirectory(), modelJobs.reuseFromModelBatchId(), "business-process")
            .readCompleted(
                jobKey, inputFingerprint, binding.quotaScope(), binding.expectedRuntimeIdentity())
            .orElse(null);
    if (saved == null) {
      return null;
    }
    requireReusableProcessPair(saved, jobKey, binding);
    return new ReviewedPair(
        new ModelCall(object(saved.path("draft")), binding.expectedRuntimeIdentity()),
        new ModelCall(object(saved.path("review")), binding.expectedRuntimeIdentity()));
  }

  /** Validates the extra immutable packet contract owned only by cross-object process reuse. */
  private void requireReusableProcessPair(
      ObjectNode saved, String jobKey, ModelJobProviderBinding binding) {
    if (!modelJobs.reuseFromModelBatchId().value().equals(nullableText(saved.path("runId")))
        || !"business-process".equals(nullableText(saved.path("phase")))
        || !jobKey.equals(nullableText(saved.path("jobKey")))
        || !binding.key().equals(nullableText(saved.path("providerBindingKey")))
        || !(saved.path("readingPacket") instanceof ObjectNode packet)
        || !"process-reading-packet-v1".equals(nullableText(packet.path("schemaVersion")))
        || !(saved.path("sourceReferenceMapping") instanceof ArrayNode)) {
      throw failure("MODEL_JOB_RESULT_INVALID");
    }
  }

  private void savePair(
      String phase,
      String jobKey,
      String inputFingerprint,
      ModelJobProviderBinding binding,
      ModelCall draft,
      ModelCall review) {
    if (modelJobs == null) {
      return;
    }
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", "model-job-reviewed-result-v2");
    record.put("status", "COMPLETED");
    record.put("runId", modelJobs.runId().value());
    record.put("phase", phase);
    record.put("jobKey", jobKey);
    record.put("inputFingerprint", inputFingerprint);
    record.put("providerBindingKey", binding.key());
    record.put("quotaScope", binding.quotaScope());
    ObjectNode identity = record.putObject("runtimeIdentity");
    identity.put("upstreamProvider", review.runtimeIdentity().upstreamProvider());
    identity.put("model", review.runtimeIdentity().model());
    identity.put("reasoningEffort", review.runtimeIdentity().reasoningEffort());
    identity.put("sandbox", review.runtimeIdentity().sandbox());
    record.set("draft", draft.value());
    record.set("review", review.value());
    record.putNull("reusedFromModelBatchId");
    new PrivateModelJobResultStore(modelJobs.journalDirectory(), modelJobs.runId(), phase)
        .write(jobKey, record);
  }

  private void saveProcessPair(
      String jobKey,
      String inputFingerprint,
      ModelJobProviderBinding binding,
      ModelCall draft,
      ModelCall review,
      ReadingPacket packet,
      FrozenCorpus corpus,
      List<SourceReferenceMapping> sourceReferenceMapping,
      boolean reused) {
    if (modelJobs == null) {
      return;
    }
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", "model-job-reviewed-result-v2");
    record.put("status", "COMPLETED");
    record.put("runId", modelJobs.runId().value());
    record.put("phase", "business-process");
    record.put("jobKey", jobKey);
    record.put("inputFingerprint", inputFingerprint);
    record.put("providerBindingKey", binding.key());
    record.put("quotaScope", binding.quotaScope());
    ObjectNode identity = record.putObject("runtimeIdentity");
    identity.put("upstreamProvider", review.runtimeIdentity().upstreamProvider());
    identity.put("model", review.runtimeIdentity().model());
    identity.put("reasoningEffort", review.runtimeIdentity().reasoningEffort());
    identity.put("sandbox", review.runtimeIdentity().sandbox());
    record.set("draft", draft.value());
    record.set("review", review.value());
    record.set("readingPacket", packetInput(packet, corpus).path("readingPacket"));
    ArrayNode mapping = record.putArray("sourceReferenceMapping");
    sourceReferenceMapping.forEach(
        source -> {
          ObjectNode value = mapping.addObject();
          value.put("localRef", source.localRef());
          value.put("finalRef", source.finalRef());
          value.put("file", source.source().file());
          value.put("startLine", source.source().startLine());
          value.put("endLine", source.source().endLine());
          value.put("snippet", source.source().snippet());
        });
    if (reused) {
      record.put("reusedFromModelBatchId", modelJobs.reuseFromModelBatchId().value());
    } else {
      record.putNull("reusedFromModelBatchId");
    }
    new PrivateModelJobResultStore(
            modelJobs.journalDirectory(), modelJobs.runId(), "business-process")
        .write(jobKey, record);
  }

  private CatalogResult parseCatalog(ObjectNode value, List<ActivityIndexCard> expectedCards) {
    Set<String> expectedIds =
        expectedCards.stream().map(ActivityIndexCard::activityId).collect(Collectors.toSet());
    List<AreaSeed> areas = new ArrayList<>();
    Set<String> areaLocalIds = new HashSet<>();
    for (JsonNode item : array(value, "businessAreas")) {
      ObjectNode area = object(item);
      String localId = text(area, "areaLocalId");
      if (!areaLocalIds.add(localId)) {
        throw failure("PROCESS_CATALOG_DUPLICATE_AREA");
      }
      List<String> activityIds = strings(area, "activityIds").stream().distinct().toList();
      requireSubset(activityIds, expectedIds, "PROCESS_CATALOG_UNKNOWN_ACTIVITY");
      areas.add(new AreaSeed(localId, text(area, "name"), text(area, "purpose"), activityIds));
    }
    List<RepositoryBusinessProcessCatalog.BusinessAlias> aliases = new ArrayList<>();
    Set<String> canonicalAliasNames = new HashSet<>();
    for (JsonNode item : array(value, "aliases")) {
      ObjectNode alias = object(item);
      String canonicalName = text(alias, "canonicalName");
      if (!canonicalAliasNames.add(canonicalName)) {
        throw failure("PROCESS_CATALOG_DUPLICATE_ALIAS");
      }
      List<String> observedAliases = strings(alias, "aliases");
      if (observedAliases.stream().distinct().count() != observedAliases.size()) {
        throw failure("PROCESS_CATALOG_DUPLICATE_ALIAS_VALUE");
      }
      aliases.add(
          new RepositoryBusinessProcessCatalog.BusinessAlias(canonicalName, observedAliases));
    }
    List<Candidate> candidates = new ArrayList<>();
    Set<String> localCandidateIds = new HashSet<>();
    for (JsonNode item : array(value, "candidateProcesses")) {
      ObjectNode candidate = object(item);
      String localId = text(candidate, "candidateLocalId");
      if (!localCandidateIds.add(localId)) {
        throw failure("PROCESS_CATALOG_DUPLICATE_CANDIDATE");
      }
      List<CandidateUse> uses = new ArrayList<>();
      Set<CandidateActivityVariant> usedActivityVariants = new HashSet<>();
      for (JsonNode useValue : array(candidate, "activityUses")) {
        ObjectNode use = object(useValue);
        String activityId = text(use, "activityId");
        String variant = text(use, "variant");
        if (!expectedIds.contains(activityId)
            || !usedActivityVariants.add(new CandidateActivityVariant(activityId, variant))) {
          throw failure("PROCESS_CATALOG_INVALID_CANDIDATE_MEMBERSHIP");
        }
        String role = text(use, "role");
        if (!CANDIDATE_ROLES.contains(role)) {
          throw failure("PROCESS_CATALOG_INVALID_ACTIVITY_ROLE");
        }
        uses.add(new CandidateUse(activityId, role, variant));
      }
      if (uses.isEmpty()) {
        throw failure("PROCESS_CATALOG_EMPTY_CANDIDATE");
      }
      String name = text(candidate, "name");
      String purpose = text(candidate, "purpose");
      String candidateId = candidateId(name, purpose, uses);
      candidates.add(
          new Candidate(
              candidateId, localId, name, purpose, purpose, List.copyOf(uses), List.of()));
    }
    List<ProcessCoverage.ActivityDisposition> dispositions = new ArrayList<>();
    Set<String> dispositionIds = new HashSet<>();
    for (JsonNode item : array(value, "activityDispositions")) {
      ObjectNode disposition = object(item);
      String activityId = text(disposition, "activityId");
      String kind = text(disposition, "disposition");
      if (!expectedIds.contains(activityId)
          || !dispositionIds.add(activityId)
          || !ACTIVITY_DISPOSITIONS.contains(kind)) {
        throw failure("PROCESS_CATALOG_ACTIVITY_DISPOSITION_INVALID");
      }
      boolean member =
          candidates.stream()
              .flatMap(candidate -> candidate.uses().stream())
              .anyMatch(use -> use.activityId().equals(activityId));
      String reason = text(disposition, "reason");
      if (member) {
        kind = "PROCESS_MEMBER";
      } else if ("PROCESS_MEMBER".equals(kind)) {
        throw failure("PROCESS_CATALOG_PROCESS_MEMBER_WITHOUT_CANDIDATE");
      }
      String activityName =
          expectedCards.stream()
              .filter(card -> activityId.equals(card.activityId()))
              .map(ActivityIndexCard::name)
              .findFirst()
              .orElseThrow(() -> failure("PROCESS_CATALOG_ACTIVITY_NAME_MISSING"));
      dispositions.add(
          new ProcessCoverage.ActivityDisposition(activityId, activityName, kind, reason));
    }
    if (!dispositionIds.equals(expectedIds)) {
      throw failure("PROCESS_CATALOG_ACTIVITY_DENOMINATOR_OPEN");
    }
    return new CatalogResult(
        List.copyOf(areas),
        aliases.stream()
            .sorted(
                Comparator.comparing(
                    RepositoryBusinessProcessCatalog.BusinessAlias::canonicalName, UTF8_ORDER))
            .toList(),
        candidates.stream().sorted(Comparator.comparing(Candidate::candidateId)).toList(),
        dispositions.stream()
            .sorted(Comparator.comparing(ProcessCoverage.ActivityDisposition::activityId))
            .toList(),
        strings(value, "unresolvedQuestions"));
  }

  private CatalogResult parseMergedCatalogReview(
      ObjectNode draft, ObjectNode review, List<ActivityIndexCard> expectedCards) {
    Set<String> expectedIds =
        expectedCards.stream().map(ActivityIndexCard::activityId).collect(Collectors.toSet());
    List<String> reviewedDispositionIds = new ArrayList<>();
    for (JsonNode value : array(review, "activityDispositions")) {
      reviewedDispositionIds.add(text(object(value), "activityId"));
    }
    if (reviewedDispositionIds.size() == expectedIds.size()
        && reviewedDispositionIds.stream().distinct().count() == expectedIds.size()
        && expectedIds.containsAll(reviewedDispositionIds)) {
      return parseCatalog(review, expectedCards);
    }

    Map<String, ProcessCoverage.ActivityDisposition> draftDispositions =
        catalogDispositionLedger(draft, expectedCards);
    Set<String> reviewedMembers = new HashSet<>();
    for (JsonNode candidateValue : array(review, "candidateProcesses")) {
      for (JsonNode useValue : array(object(candidateValue), "activityUses")) {
        reviewedMembers.add(text(object(useValue), "activityId"));
      }
    }

    ObjectNode reconciled = review.deepCopy();
    ArrayNode dispositions = reconciled.putArray("activityDispositions");
    expectedCards.stream()
        .sorted(Comparator.comparing(ActivityIndexCard::activityId, UTF8_ORDER))
        .forEach(
            card -> {
              ProcessCoverage.ActivityDisposition baseline =
                  draftDispositions.get(card.activityId());
              ObjectNode disposition = dispositions.addObject();
              disposition.put("activityId", card.activityId());
              if (reviewedMembers.contains(card.activityId())) {
                disposition.put("disposition", "PROCESS_MEMBER");
                disposition.put("reason", baseline.reason());
              } else if ("PROCESS_MEMBER".equals(baseline.disposition())) {
                disposition.put("disposition", "UNCLASSIFIED");
                disposition.put("reason", "目录审阅将该活动移出全部候选，未提供新的过程归属。");
              } else {
                disposition.put("disposition", baseline.disposition());
                disposition.put("reason", baseline.reason());
              }
            });
    return parseCatalog(reconciled, expectedCards);
  }

  private Map<String, ProcessCoverage.ActivityDisposition> catalogDispositionLedger(
      ObjectNode value, List<ActivityIndexCard> expectedCards) {
    Map<String, ActivityIndexCard> cardsById =
        expectedCards.stream()
            .collect(Collectors.toMap(ActivityIndexCard::activityId, Function.identity()));
    Map<String, ProcessCoverage.ActivityDisposition> dispositions = new LinkedHashMap<>();
    for (JsonNode item : array(value, "activityDispositions")) {
      ObjectNode disposition = object(item);
      String activityId = text(disposition, "activityId");
      String kind = text(disposition, "disposition");
      ActivityIndexCard card = cardsById.get(activityId);
      if (card == null
          || dispositions.containsKey(activityId)
          || !ACTIVITY_DISPOSITIONS.contains(kind)) {
        throw failure("PROCESS_CATALOG_ACTIVITY_DISPOSITION_INVALID");
      }
      dispositions.put(
          activityId,
          new ProcessCoverage.ActivityDisposition(
              activityId, card.name(), kind, text(disposition, "reason")));
    }
    if (!dispositions.keySet().equals(cardsById.keySet())) {
      throw failure("PROCESS_CATALOG_ACTIVITY_DENOMINATOR_OPEN");
    }
    return Map.copyOf(dispositions);
  }

  private ParsedCandidateDraft parseCandidate(
      ObjectNode value,
      ReadingPacket packet,
      FrozenCorpus corpus,
      ProcessDiscoveryProfile profile) {
    Candidate candidate = packet.candidate();
    String disposition = text(value, "disposition");
    if (!CANDIDATE_DISPOSITIONS.contains(disposition)
        || "NOT_PROCESSED_CAPACITY".equals(disposition)) {
      throw failure("PROCESS_CANDIDATE_DISPOSITION_INVALID");
    }
    ArrayNode processValues = array(value, "processes");
    boolean expectsProcesses = Set.of("RECONSTRUCTED", "SPLIT").contains(disposition);
    if (expectsProcesses != !processValues.isEmpty()) {
      throw failure("PROCESS_CANDIDATE_OUTPUT_DISPOSITION_MISMATCH");
    }
    if (expectsProcesses) {
      Set<String> expectedActivityIds =
          candidate.uses().stream().map(CandidateUse::activityId).collect(Collectors.toSet());
      Set<String> responseActivityIds = new HashSet<>();
      for (JsonNode processValue : processValues) {
        for (JsonNode useValue : array(object(processValue), "activityUses")) {
          responseActivityIds.add(text(object(useValue), "activityId"));
        }
      }
      if (expectedActivityIds.containsAll(responseActivityIds)
          && !responseActivityIds.equals(expectedActivityIds)) {
        throw failure("PROCESS_CANDIDATE_ACTIVITY_COVERAGE_OPEN");
      }
    }
    List<RepositoryBusinessProcessCatalog.BusinessProcess> processes = new ArrayList<>();
    Set<String> processLocalIds = new HashSet<>();
    for (JsonNode processValue : processValues) {
      ObjectNode process = object(processValue);
      if (!processLocalIds.add(text(process, "processLocalId"))) {
        throw failure("PROCESS_CANDIDATE_DUPLICATE_PROCESS");
      }
      processes.add(process(process, packet, corpus));
    }
    if (processes.size() > profile.maxProcessesPerCandidate()) {
      throw failure("PROCESS_CANDIDATE_OUTPUT_CAPACITY_EXCEEDED");
    }
    if (expectsProcesses) {
      Set<String> expectedActivityIds =
          candidate.uses().stream().map(CandidateUse::activityId).collect(Collectors.toSet());
      Set<String> usedActivityIds =
          processes.stream()
              .flatMap(process -> process.activityUses().stream())
              .map(RepositoryBusinessProcessCatalog.ActivityUse::activityId)
              .collect(Collectors.toSet());
      if (!usedActivityIds.equals(expectedActivityIds)) {
        throw failure("PROCESS_CANDIDATE_ACTIVITY_COVERAGE_OPEN");
      }
    }
    return new ParsedCandidateDraft(disposition, text(value, "reason"), List.copyOf(processes));
  }

  private RepositoryBusinessProcessCatalog.BusinessProcess process(
      ObjectNode value, ReadingPacket packet, FrozenCorpus corpus) {
    Candidate candidate = packet.candidate();
    String processId = "business-process:" + sha256(canonicalJson.encodeCanonical(value));
    Set<String> candidateIds =
        candidate.uses().stream().map(CandidateUse::activityId).collect(Collectors.toSet());
    Set<String> allowedStatements =
        packet.activityIds().stream()
            .flatMap(activityId -> corpus.statementHandles(activityId).stream())
            .collect(Collectors.toSet());
    Set<String> allowedSources =
        packet.sources().stream().map(SourceReference::ref).collect(Collectors.toSet());
    Map<String, RepositoryBusinessProcessCatalog.ActivityUse> useByLocalId = new LinkedHashMap<>();
    for (JsonNode item : array(value, "activityUses")) {
      ObjectNode use = object(item);
      String localId = text(use, "useLocalId");
      String activityId = text(use, "activityId");
      String role = text(use, "role");
      String variant = text(use, "variant");
      if (!candidateIds.contains(activityId) || !CANDIDATE_ROLES.contains(role)) {
        throw failure("PROCESS_ACTIVITY_USE_INVALID");
      }
      List<String> statementRefs = strings(use, "statementRefs").stream().distinct().toList();
      requireSubset(statementRefs, allowedStatements, "PROCESS_STATEMENT_REFERENCE_INVALID");
      List<String> sourceRefs = strings(use, "sourceRefs").stream().distinct().toList();
      requireSubset(sourceRefs, allowedSources, "PROCESS_SOURCE_REFERENCE_INVALID");
      String useId =
          "activity-use:"
              + sha256(
                  canonicalJson.encodeCanonical(
                      activityUseIdentity(
                          candidate.candidateId(),
                          activityId,
                          role,
                          variant,
                          statementRefs,
                          sourceRefs)));
      if (useByLocalId.put(
              localId,
              new RepositoryBusinessProcessCatalog.ActivityUse(
                  useId, activityId, role, variant, statementRefs, sourceRefs))
          != null) {
        throw failure("PROCESS_DUPLICATE_ACTIVITY_USE");
      }
    }
    if (useByLocalId.isEmpty()) {
      throw failure("PROCESS_ACTIVITY_USE_REQUIRED");
    }
    List<RepositoryBusinessProcessCatalog.ProcessStage> stages = new ArrayList<>();
    Set<Integer> orders = new HashSet<>();
    for (JsonNode item : array(value, "stages")) {
      ObjectNode stage = object(item);
      int order = positiveInt(stage, "order");
      if (!orders.add(order)) {
        throw failure("PROCESS_STAGE_ORDER_DUPLICATE");
      }
      List<String> localUseIds = strings(stage, "activityUseLocalIds");
      if (!useByLocalId.keySet().containsAll(localUseIds)) {
        throw failure("PROCESS_STAGE_ACTIVITY_USE_INVALID");
      }
      List<String> statementRefs = strings(stage, "statementRefs").stream().distinct().toList();
      List<String> sourceRefs = strings(stage, "sourceRefs").stream().distinct().toList();
      requireSubset(statementRefs, allowedStatements, "PROCESS_STAGE_STATEMENT_REFERENCE_INVALID");
      requireSubset(sourceRefs, allowedSources, "PROCESS_STAGE_SOURCE_REFERENCE_INVALID");
      String certainty = certainty(stage);
      requireConfirmedBasis(certainty, statementRefs, sourceRefs);
      stages.add(
          new RepositoryBusinessProcessCatalog.ProcessStage(
              order,
              text(stage, "name"),
              text(stage, "narrative"),
              localUseIds.stream()
                  .map(useByLocalId::get)
                  .map(RepositoryBusinessProcessCatalog.ActivityUse::activityUseId)
                  .toList(),
              strings(stage, "entryConditions"),
              strings(stage, "actions"),
              strings(stage, "stateChanges"),
              strings(stage, "rejectionConditions"),
              strings(stage, "outcomes"),
              strings(stage, "transitions"),
              certainty,
              statementRefs,
              sourceRefs));
    }
    stages.sort(Comparator.comparingInt(RepositoryBusinessProcessCatalog.ProcessStage::order));
    for (int index = 0; index < stages.size(); index++) {
      if (stages.get(index).order() != index + 1) {
        throw failure("PROCESS_STAGE_ORDER_NOT_CONTIGUOUS");
      }
    }
    List<RepositoryBusinessProcessCatalog.BusinessRule> rules = new ArrayList<>();
    for (JsonNode item : array(value, "businessRules")) {
      ObjectNode rule = object(item);
      List<String> localUseIds = strings(rule, "activityUseLocalIds");
      if (localUseIds.isEmpty()
          || localUseIds.stream().distinct().count() != localUseIds.size()
          || !useByLocalId.keySet().containsAll(localUseIds)) {
        throw failure("PROCESS_RULE_ACTIVITY_USE_INVALID");
      }
      List<RepositoryBusinessProcessCatalog.ActivityUse> ruleUses =
          localUseIds.stream().map(useByLocalId::get).toList();
      List<String> statementRefs = strings(rule, "statementRefs").stream().distinct().toList();
      List<String> sourceRefs = strings(rule, "sourceRefs").stream().distinct().toList();
      requireSubset(statementRefs, allowedStatements, "PROCESS_RULE_STATEMENT_REFERENCE_INVALID");
      requireSubset(sourceRefs, allowedSources, "PROCESS_RULE_SOURCE_REFERENCE_INVALID");
      String certainty = certainty(rule);
      requireConfirmedBasis(certainty, statementRefs, sourceRefs);
      rules.add(
          new RepositoryBusinessProcessCatalog.BusinessRule(
              text(rule, "subject"),
              text(rule, "when"),
              text(rule, "actionOrDecision"),
              nullableText(rule.path("otherwise")),
              text(rule, "result"),
              certainty,
              ruleUses.stream()
                  .map(RepositoryBusinessProcessCatalog.ActivityUse::activityUseId)
                  .toList(),
              statementRefs,
              sourceRefs));
    }
    List<RepositoryBusinessProcessCatalog.KnowledgeItem> knowledge = new ArrayList<>();
    for (JsonNode item : array(value, "knowledgeItems")) {
      ObjectNode knowledgeValue = object(item);
      List<String> statementRefs =
          strings(knowledgeValue, "statementRefs").stream().distinct().toList();
      List<String> sourceRefs = strings(knowledgeValue, "sourceRefs").stream().distinct().toList();
      requireSubset(
          statementRefs, allowedStatements, "PROCESS_KNOWLEDGE_STATEMENT_REFERENCE_INVALID");
      requireSubset(sourceRefs, allowedSources, "PROCESS_KNOWLEDGE_SOURCE_REFERENCE_INVALID");
      String certainty = certainty(knowledgeValue);
      requireConfirmedBasis(certainty, statementRefs, sourceRefs);
      knowledge.add(
          new RepositoryBusinessProcessCatalog.KnowledgeItem(
              text(knowledgeValue, "kind"),
              text(knowledgeValue, "text"),
              processId,
              certainty,
              statementRefs,
              sourceRefs));
    }
    List<String> supportIds = strings(value, "supportActivityUseLocalIds");
    if (!useByLocalId.keySet().containsAll(supportIds)) {
      throw failure("PROCESS_SUPPORT_ACTIVITY_USE_INVALID");
    }
    List<String> processSources =
        java.util.stream.Stream.of(
                useByLocalId.values().stream().flatMap(use -> use.sourceRefs().stream()),
                stages.stream().flatMap(stage -> stage.sourceRefs().stream()),
                rules.stream().flatMap(rule -> rule.sourceRefs().stream()),
                knowledge.stream().flatMap(item -> item.sourceRefs().stream()))
            .flatMap(Function.identity())
            .distinct()
            .sorted(UTF8_ORDER)
            .toList();
    return new RepositoryBusinessProcessCatalog.BusinessProcess(
        processId,
        text(value, "name"),
        text(value, "purpose"),
        text(value, "scope"),
        strings(value, "participants"),
        strings(value, "businessObjects"),
        List.copyOf(useByLocalId.values()),
        stages,
        strings(value, "branches"),
        rules,
        strings(value, "endResults"),
        supportIds.stream()
            .map(useByLocalId::get)
            .map(RepositoryBusinessProcessCatalog.ActivityUse::activityUseId)
            .toList(),
        knowledge,
        strings(value, "pendingConnections"),
        processSources);
  }

  private ConsolidationDecision parseConsolidation(
      ObjectNode value, List<RepositoryBusinessProcessCatalog.BusinessProcess> processes) {
    Set<String> processIds =
        processes.stream()
            .map(RepositoryBusinessProcessCatalog.BusinessProcess::processId)
            .collect(Collectors.toSet());
    List<AreaDecision> areas = new ArrayList<>();
    Set<String> areaIds = new HashSet<>();
    for (JsonNode item : array(value, "businessAreas")) {
      ObjectNode area = object(item);
      String areaId = text(area, "areaId");
      if (!areaIds.add(areaId)) {
        throw failure("PROCESS_CONSOLIDATION_DUPLICATE_AREA");
      }
      List<String> ids = strings(area, "processIds");
      requireSubset(ids, processIds, "PROCESS_CONSOLIDATION_AREA_PROCESS_INVALID");
      areas.add(new AreaDecision(areaId, text(area, "name"), text(area, "purpose"), ids));
    }
    List<ProcessDecision> decisions = new ArrayList<>();
    Set<String> decided = new HashSet<>();
    for (JsonNode item : array(value, "processDecisions")) {
      ObjectNode decision = object(item);
      String processId = text(decision, "processId");
      String disposition = text(decision, "disposition");
      String target = nullableText(decision.path("targetProcessId"));
      if (!processIds.contains(processId)
          || !decided.add(processId)
          || !Set.of("KEEP", "MERGE_INTO", "REJECT").contains(disposition)
          || ("MERGE_INTO".equals(disposition) != (target != null))
          || (target != null && (!processIds.contains(target) || target.equals(processId)))) {
        throw failure("PROCESS_CONSOLIDATION_DECISION_INVALID");
      }
      decisions.add(new ProcessDecision(processId, disposition, target, text(decision, "reason")));
    }
    if (!decided.equals(processIds)) {
      throw failure("PROCESS_CONSOLIDATION_PROCESS_DENOMINATOR_OPEN");
    }
    Map<String, ProcessDecision> byId =
        decisions.stream()
            .collect(Collectors.toMap(ProcessDecision::processId, Function.identity()));
    for (ProcessDecision decision : decisions) {
      if (decision.targetProcessId() != null
          && !"KEEP".equals(byId.get(decision.targetProcessId()).disposition())) {
        throw failure("PROCESS_CONSOLIDATION_MERGE_TARGET_INVALID");
      }
    }
    List<RepositoryBusinessProcessCatalog.ProcessRelation> relations = new ArrayList<>();
    Set<String> allowedSourceRefs =
        processes.stream()
            .flatMap(process -> process.sourceRefs().stream())
            .collect(Collectors.toSet());
    for (JsonNode item : array(value, "processRelations")) {
      ObjectNode relation = object(item);
      String from = text(relation, "fromProcessId");
      String to = text(relation, "toProcessId");
      List<String> refs = strings(relation, "sourceRefs");
      if (!processIds.contains(from) || !processIds.contains(to) || from.equals(to)) {
        throw failure("PROCESS_CONSOLIDATION_RELATION_INVALID");
      }
      requireSubset(refs, allowedSourceRefs, "PROCESS_CONSOLIDATION_SOURCE_REFERENCE_INVALID");
      relations.add(
          new RepositoryBusinessProcessCatalog.ProcessRelation(
              from,
              to,
              text(relation, "relationType"),
              text(relation, "description"),
              certainty(relation),
              refs));
    }
    return new ConsolidationDecision(
        List.copyOf(areas),
        List.copyOf(decisions),
        List.copyOf(relations),
        strings(value, "pendingConfirmations"));
  }

  private RepositoryBusinessProcessCatalog applyConsolidation(
      CatalogResult catalog,
      List<RepositoryBusinessProcessCatalog.BusinessProcess> processes,
      ConsolidationDecision consolidation,
      FrozenCorpus corpus) {
    Map<String, RepositoryBusinessProcessCatalog.BusinessProcess> byId =
        processes.stream()
            .collect(
                Collectors.toMap(
                    RepositoryBusinessProcessCatalog.BusinessProcess::processId,
                    Function.identity()));
    Map<String, List<RepositoryBusinessProcessCatalog.BusinessProcess>> merges = new HashMap<>();
    Set<String> kept = new HashSet<>();
    for (ProcessDecision decision : consolidation.decisions()) {
      if ("KEEP".equals(decision.disposition())) {
        kept.add(decision.processId());
      } else if ("MERGE_INTO".equals(decision.disposition())) {
        merges
            .computeIfAbsent(decision.targetProcessId(), ignored -> new ArrayList<>())
            .add(byId.get(decision.processId()));
      }
    }
    List<RepositoryBusinessProcessCatalog.BusinessProcess> published = new ArrayList<>();
    for (String processId : kept.stream().sorted(UTF8_ORDER).toList()) {
      published.add(merge(byId.get(processId), merges.getOrDefault(processId, List.of())));
    }
    Map<String, String> finalId = new HashMap<>();
    for (ProcessDecision decision : consolidation.decisions()) {
      if ("KEEP".equals(decision.disposition())) {
        finalId.put(decision.processId(), decision.processId());
      } else if ("MERGE_INTO".equals(decision.disposition())) {
        finalId.put(decision.processId(), decision.targetProcessId());
      }
    }
    List<RepositoryBusinessProcessCatalog.ProcessRelation> relations =
        consolidation.relations().stream()
            .filter(
                relation ->
                    finalId.containsKey(relation.fromProcessId())
                        && finalId.containsKey(relation.toProcessId()))
            .map(
                relation ->
                    new RepositoryBusinessProcessCatalog.ProcessRelation(
                        finalId.get(relation.fromProcessId()),
                        finalId.get(relation.toProcessId()),
                        relation.relationType(),
                        relation.description(),
                        relation.certainty(),
                        relation.sourceRefs()))
            .filter(relation -> !relation.fromProcessId().equals(relation.toProcessId()))
            .distinct()
            .toList();
    List<RepositoryBusinessProcessCatalog.BusinessArea> areas =
        consolidation.areas().stream()
            .map(
                area ->
                    new RepositoryBusinessProcessCatalog.BusinessArea(
                        area.areaId(),
                        area.name(),
                        area.purpose(),
                        area.processIds().stream()
                            .map(finalId::get)
                            .filter(Objects::nonNull)
                            .distinct()
                            .sorted(UTF8_ORDER)
                            .toList()))
            .toList();
    List<String> standalone = activityIds(catalog, "STANDALONE");
    List<String> unclassified = activityIds(catalog, "UNCLASSIFIED");
    List<RepositoryBusinessProcessCatalog.KnowledgeItem> directKnowledge =
        catalog.activityDispositions().stream()
            .filter(
                disposition ->
                    Set.of("SUPPORT_ONLY", "STANDALONE", "UNCLASSIFIED")
                        .contains(disposition.disposition()))
            .flatMap(
                disposition ->
                    corpus
                        .directKnowledge(disposition.activityId(), disposition.disposition())
                        .stream())
            .toList();
    List<String> pending =
        java.util.stream.Stream.of(
                catalog.unresolvedQuestions().stream(),
                published.stream().flatMap(process -> process.pendingConnections().stream()),
                consolidation.pendingConfirmations().stream())
            .flatMap(Function.identity())
            .distinct()
            .toList();
    return new RepositoryBusinessProcessCatalog(
        areas,
        catalog.aliases(),
        published,
        relations,
        standalone,
        unclassified,
        directKnowledge,
        pending);
  }

  private static ConsolidationDecision preserveNonLosslessMerges(
      ConsolidationDecision consolidation,
      List<RepositoryBusinessProcessCatalog.BusinessProcess> processes) {
    Map<String, RepositoryBusinessProcessCatalog.BusinessProcess> byId =
        processes.stream()
            .collect(
                Collectors.toMap(
                    RepositoryBusinessProcessCatalog.BusinessProcess::processId,
                    Function.identity()));
    List<ProcessDecision> decisions =
        consolidation.decisions().stream()
            .map(
                decision -> {
                  if (!"MERGE_INTO".equals(decision.disposition())) {
                    return decision;
                  }
                  RepositoryBusinessProcessCatalog.BusinessProcess target =
                      byId.get(decision.targetProcessId());
                  RepositoryBusinessProcessCatalog.BusinessProcess source =
                      byId.get(decision.processId());
                  if (isLosslessMerge(target, source)) {
                    return decision;
                  }
                  return new ProcessDecision(
                      decision.processId(),
                      "KEEP",
                      null,
                      decision.reason() + "；机械合并会改变阶段或过程含义，因此保留原完整过程。");
                })
            .toList();
    return new ConsolidationDecision(
        consolidation.areas(),
        decisions,
        consolidation.relations(),
        consolidation.pendingConfirmations());
  }

  private ProcessCoverage coverage(
      CatalogResult catalog,
      List<CandidateResult> candidates,
      ConsolidationDecision consolidation,
      RepositoryBusinessProcessCatalog finalCatalog,
      FrozenCorpus corpus) {
    List<ProcessCoverage.CandidateDisposition> candidateCoverage =
        candidates.stream()
            .map(
                candidate ->
                    new ProcessCoverage.CandidateDisposition(
                        candidate.candidate().candidateId(),
                        candidate.disposition(),
                        candidate.reason()))
            .sorted(Comparator.comparing(ProcessCoverage.CandidateDisposition::candidateId))
            .toList();
    List<ProcessCoverage.ReviewedProcessDisposition> processCoverage =
        consolidation.decisions().stream()
            .map(
                decision ->
                    new ProcessCoverage.ReviewedProcessDisposition(
                        decision.processId(),
                        switch (decision.disposition()) {
                          case "KEEP" -> "PUBLISHED";
                          case "MERGE_INTO" -> "MERGED_INTO";
                          case "REJECT" -> "REJECTED";
                          default -> throw failure("PROCESS_CONSOLIDATION_DECISION_INVALID");
                        },
                        decision.targetProcessId(),
                        decision.reason()))
            .sorted(Comparator.comparing(ProcessCoverage.ReviewedProcessDisposition::processId))
            .toList();
    boolean partial =
        catalog.activityDispositions().stream()
                .anyMatch(
                    value ->
                        Set.of("UNCLASSIFIED", "NOT_PROCESSED_CAPACITY")
                            .contains(value.disposition()))
            || candidateCoverage.stream()
                .anyMatch(
                    value ->
                        Set.of("INSUFFICIENT_MATERIAL", "NOT_PROCESSED_CAPACITY")
                            .contains(value.disposition()))
            || !corpus.unexplainedEntryIds().isEmpty();
    Set<String> coveredActivityIds =
        catalog.activityDispositions().stream()
            .map(ProcessCoverage.ActivityDisposition::activityId)
            .collect(Collectors.toSet());
    if (!coveredActivityIds.equals(corpus.activityIds())) {
      throw failure("PROCESS_ACTIVITY_DENOMINATOR_OPEN");
    }
    Set<String> publishedIds =
        finalCatalog.processes().stream()
            .map(RepositoryBusinessProcessCatalog.BusinessProcess::processId)
            .collect(Collectors.toSet());
    Set<String> expectedPublished =
        processCoverage.stream()
            .filter(value -> "PUBLISHED".equals(value.disposition()))
            .map(ProcessCoverage.ReviewedProcessDisposition::processId)
            .collect(Collectors.toSet());
    if (!publishedIds.equals(expectedPublished)) {
      throw failure("PROCESS_PUBLICATION_PROCESS_DENOMINATOR_OPEN");
    }
    return new ProcessCoverage(
        catalog.activityDispositions(),
        candidateCoverage,
        processCoverage,
        "CLOSED",
        partial ? "PARTIAL" : "COMPLETE");
  }

  private ObjectNode catalogInput(
      List<ActivityIndexCard> cards, List<ObjectNode> reviewedShardCatalogs) {
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.put("task", reviewedShardCatalogs.isEmpty() ? "DISCOVER_CATALOG" : "MERGE_CATALOG");
    ArrayNode cardValues = input.putArray("activityIndexCards");
    cards.forEach(card -> cardValues.add(card.toJson()));
    ArrayNode shards = input.putArray("reviewedShardCatalogs");
    reviewedShardCatalogs.forEach(shards::add);
    input.put("instruction", "只发现材料中存在的业务领域和候选关系，不使用预设行业词，不在本轮编写详细过程。");
    return input;
  }

  private static List<SourceReference> packetSources(
      FrozenCorpus corpus, List<ReadingPacket> packets) {
    Map<String, SourceReference> sources = new LinkedHashMap<>();
    corpus.sourceReferences().forEach(source -> sources.put(source.ref(), source));
    for (ReadingPacket packet : packets) {
      for (SourceReference source : packet.sources()) {
        SourceReference previous = sources.putIfAbsent(source.ref(), source);
        if (previous != null && !previous.equals(source)) {
          throw failure("PROCESS_READING_SOURCE_REFERENCE_COLLISION");
        }
      }
    }
    return sources.values().stream()
        .sorted(Comparator.comparing(SourceReference::ref, UTF8_ORDER))
        .toList();
  }

  private SourceNormalization normalizeSources(FrozenCorpus corpus, List<ReadingPacket> packets) {
    Set<String> corpusReferences =
        corpus.sourceReferences().stream()
            .map(SourceReference::ref)
            .collect(Collectors.toUnmodifiableSet());
    Map<SourceIdentity, SourceReference> newlyReadSources = new LinkedHashMap<>();
    for (ReadingPacket packet : packets) {
      for (SourceReference source : packet.sources()) {
        if (!corpusReferences.contains(source.ref())) {
          SourceIdentity identity = SourceIdentity.from(source);
          SourceReference previous = newlyReadSources.putIfAbsent(identity, source);
          if (previous != null && !sameSourceIdentity(previous, source)) {
            throw failure("PROCESS_READING_SOURCE_IDENTITY_INVALID");
          }
        }
      }
    }
    List<SourceIdentity> identities = new ArrayList<>(newlyReadSources.keySet());
    identities.sort(SOURCE_IDENTITY_ORDER);
    Map<SourceIdentity, String> finalRefs = new LinkedHashMap<>();
    int next = 1;
    for (SourceIdentity identity : identities) {
      String reference = "S" + next;
      while (corpusReferences.contains(reference)) {
        reference = "S" + ++next;
      }
      finalRefs.put(identity, reference);
      next++;
    }
    Map<Integer, ReadingPacket> finalPackets = new LinkedHashMap<>();
    Map<Integer, List<SourceReferenceMapping>> mappings = new LinkedHashMap<>();
    for (ReadingPacket packet : packets) {
      List<SourceReferenceMapping> mapping = new ArrayList<>();
      List<SourceReference> sources = new ArrayList<>();
      for (SourceReference source : packet.sources()) {
        String finalRef =
            corpusReferences.contains(source.ref())
                ? source.ref()
                : finalRefs.get(SourceIdentity.from(source));
        if (finalRef == null) {
          throw failure("PROCESS_READING_SOURCE_REFERENCE_MAPPING_INVALID");
        }
        mapping.add(new SourceReferenceMapping(source.ref(), finalRef, source));
        sources.add(
            new SourceReference(
                finalRef, source.file(), source.startLine(), source.endLine(), source.snippet()));
      }
      mapping.sort(Comparator.comparing(SourceReferenceMapping::localRef, UTF8_ORDER));
      sources.sort(Comparator.comparing(SourceReference::ref, UTF8_ORDER));
      if (finalPackets.put(
                  packet.ordinal(),
                  new ReadingPacket(
                      packet.ordinal(),
                      packet.candidate(),
                      packet.activityIds(),
                      List.copyOf(sources),
                      packet.limitations(),
                      packet.changedDispositions(),
                      packet.unresolvedQuestions()))
              != null
          || mappings.put(packet.ordinal(), List.copyOf(mapping)) != null) {
        throw failure("PROCESS_READING_PACKET_ORDINAL_DUPLICATE");
      }
    }
    return new SourceNormalization(Map.copyOf(finalPackets), Map.copyOf(mappings));
  }

  private static boolean sameSourceIdentity(SourceReference left, SourceReference right) {
    return SourceIdentity.from(left).equals(SourceIdentity.from(right));
  }

  private ObjectNode remapSourceReferences(
      ObjectNode response, List<SourceReferenceMapping> sourceReferenceMapping) {
    Map<String, String> mappings = new LinkedHashMap<>();
    for (SourceReferenceMapping mapping : sourceReferenceMapping) {
      if (mappings.put(mapping.localRef(), mapping.finalRef()) != null) {
        throw failure("PROCESS_READING_SOURCE_REFERENCE_MAPPING_INVALID");
      }
    }
    ObjectNode remapped = response.deepCopy();
    remapSourceReferences(remapped, mappings);
    return remapped;
  }

  private static void remapSourceReferences(JsonNode value, Map<String, String> mappings) {
    if (value instanceof ArrayNode array) {
      for (int index = 0; index < array.size(); index++) {
        remapSourceReferences(array.get(index), mappings);
      }
      return;
    }
    if (!(value instanceof ObjectNode object)) {
      return;
    }
    java.util.Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      if ("sourceRefs".equals(entry.getKey()) && entry.getValue() instanceof ArrayNode refs) {
        for (int index = 0; index < refs.size(); index++) {
          JsonNode localRef = refs.get(index);
          if (localRef.isTextual()) {
            String finalRef = mappings.get(localRef.textValue());
            if (finalRef == null) {
              throw failure("PROCESS_SOURCE_REFERENCE_INVALID");
            }
            refs.set(index, JsonNodeFactory.instance.textNode(finalRef));
          }
        }
      } else {
        remapSourceReferences(entry.getValue(), mappings);
      }
    }
  }

  private ObjectNode processInput(ReadingPacket packet, FrozenCorpus corpus) {
    ObjectNode input = packetInput(packet, corpus);
    input.put("instruction", "只根据完整阅读包重建过程；保留具体条件、状态值、拒绝路径、结果与未解决问题，不再请求源码。");
    return input;
  }

  private ObjectNode packetInput(ReadingPacket packet, FrozenCorpus corpus) {
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.set("candidate", packet.candidate().toJson());
    ObjectNode readingPacket = input.putObject("readingPacket");
    readingPacket.put("schemaVersion", "process-reading-packet-v1");
    readingPacket.set("candidate", packet.candidate().toJson());
    ArrayNode reviewedActivities = readingPacket.putArray("reviewedActivities");
    packet.activityIds().stream()
        .sorted(UTF8_ORDER)
        .map(corpus::activityJson)
        .forEach(reviewedActivities::add);
    ArrayNode statementDirectory = readingPacket.putArray("statementDirectory");
    packet.activityIds().stream()
        .flatMap(activityId -> corpus.statementHandles(activityId).stream())
        .distinct()
        .sorted(UTF8_ORDER)
        .forEach(statementDirectory::add);
    ArrayNode excerpts = readingPacket.putArray("sourceExcerpts");
    packet.sources().forEach(source -> sourceJson(excerpts.addObject(), source));
    strings(readingPacket.putArray("readingLimitations"), packet.limitations());
    strings(readingPacket.putArray("unresolvedQuestions"), packet.unresolvedQuestions());
    return input;
  }

  private ObjectNode consolidationInput(
      CatalogResult catalog, List<RepositoryBusinessProcessCatalog.BusinessProcess> processes) {
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    ArrayNode processValues = input.putArray("processes");
    processes.stream()
        .sorted(Comparator.comparing(RepositoryBusinessProcessCatalog.BusinessProcess::processId))
        .map(this::consolidationProcessJson)
        .forEach(processValues::add);
    ArrayNode areas = input.putArray("businessAreas");
    for (AreaSeed seed : catalog.areas()) {
      ObjectNode area = areas.addObject();
      area.put("areaId", "business-area:" + sha256(seed.name() + "\n" + seed.purpose()));
      area.put("name", seed.name());
      area.put("purpose", seed.purpose());
      ArrayNode processIds = area.putArray("processIds");
      processes.stream()
          .filter(
              process ->
                  process.activityUses().stream()
                      .anyMatch(use -> seed.activityIds().contains(use.activityId())))
          .map(RepositoryBusinessProcessCatalog.BusinessProcess::processId)
          .sorted(UTF8_ORDER)
          .forEach(processIds::add);
    }
    input.put("instruction", "只裁决重复、父子、相关、替代和拒绝关系；不得重写或压缩已审阶段、规则和来源。");
    return input;
  }

  private ObjectNode consolidationProcessJson(
      RepositoryBusinessProcessCatalog.BusinessProcess process) {
    ObjectNode value = processJson(process);
    removeEvidenceFields(value.path("activityUses"));
    removeEvidenceFields(value.path("stages"));
    removeEvidenceFields(value.path("businessRules"));
    removeEvidenceFields(value.path("knowledgeItems"));
    return value;
  }

  private static ObjectNode completeConsolidationDecisions(
      ObjectNode review, List<RepositoryBusinessProcessCatalog.BusinessProcess> reviewedProcesses) {
    ObjectNode completed = review.deepCopy();
    ArrayNode decisions = (ArrayNode) completed.path("processDecisions");
    Set<String> decided = new HashSet<>();
    decisions.forEach(item -> decided.add(item.path("processId").asText()));
    reviewedProcesses.stream()
        .map(RepositoryBusinessProcessCatalog.BusinessProcess::processId)
        .filter(processId -> !decided.contains(processId))
        .sorted(UTF8_ORDER)
        .forEach(
            processId -> {
              ObjectNode decision = decisions.addObject();
              decision.put("processId", processId);
              decision.put("disposition", "KEEP");
              decision.putNull("targetProcessId");
              decision.put("reason", "归并审阅未返回该过程的处置；保留原完整已审过程。");
            });
    return completed;
  }

  private static void removeEvidenceFields(JsonNode values) {
    for (JsonNode item : values) {
      if (item instanceof ObjectNode object) {
        object.remove(List.of("statementRefs", "sourceRefs"));
      }
    }
  }

  private ObjectNode processJson(RepositoryBusinessProcessCatalog.BusinessProcess process) {
    return (ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(process);
  }

  private ObjectNode catalogSchema(List<ActivityIndexCard> cards) {
    ObjectNode root = objectSchema();
    ObjectNode properties = root.putObject("properties");
    properties.set("businessAreas", arraySchema(areaSchema(cards)));
    properties.set("aliases", arraySchema(aliasSchema()));
    properties.set("candidateProcesses", arraySchema(candidateSchema(cards)));
    properties.set("activityDispositions", arraySchema(activityDispositionSchema(cards)));
    properties.set("unresolvedQuestions", stringsSchema());
    required(
        root,
        "businessAreas",
        "aliases",
        "candidateProcesses",
        "activityDispositions",
        "unresolvedQuestions");
    return root;
  }

  private ObjectNode mergedCatalogSchema(List<ActivityIndexCard> cards) {
    ObjectNode schema = catalogSchema(cards);
    ObjectNode dispositions = (ObjectNode) schema.path("properties").path("activityDispositions");
    dispositions.put("minItems", cards.size());
    dispositions.put("maxItems", cards.size());
    return schema;
  }

  private ObjectNode processSchema(ReadingPacket packet, FrozenCorpus corpus) {
    Set<String> statementRefs = packetStatementRefs(packet, corpus);
    Set<String> sourceRefs = packetSourceRefs(packet);
    ObjectNode root = objectSchema();
    ObjectNode definitions = root.putObject("$defs");
    definitions.set(
        "statementRef", statementRefs.isEmpty() ? textSchema() : enumSchema(statementRefs));
    definitions.set("sourceRef", sourceRefs.isEmpty() ? textSchema() : enumSchema(sourceRefs));
    ObjectNode properties = root.putObject("properties");
    properties.set(
        "disposition",
        enumSchema(
            CANDIDATE_DISPOSITIONS.stream()
                .filter(value -> !"NOT_PROCESSED_CAPACITY".equals(value))
                .sorted()
                .toList()));
    properties.set("reason", textSchema());
    properties.set(
        "processes",
        arraySchema(
            detailedProcessSchema(
                packet.candidate(),
                packet.candidate().uses().stream().map(CandidateUse::activityId).toList(),
                statementRefs,
                sourceRefs)));
    required(root, "disposition", "reason", "processes");
    return root;
  }

  private ObjectNode consolidationSchema(
      List<RepositoryBusinessProcessCatalog.BusinessProcess> processes) {
    Set<String> ids =
        processes.stream()
            .map(RepositoryBusinessProcessCatalog.BusinessProcess::processId)
            .collect(Collectors.toSet());
    ObjectNode root = objectSchema();
    ObjectNode properties = root.putObject("properties");
    properties.set("businessAreas", arraySchema(consolidatedAreaSchema(ids)));
    properties.set("processDecisions", arraySchema(processDecisionSchema(ids)));
    List<String> sourceRefs =
        processes.stream()
            .flatMap(process -> process.sourceRefs().stream())
            .distinct()
            .sorted(UTF8_ORDER)
            .toList();
    properties.set("processRelations", arraySchema(processRelationSchema(ids, sourceRefs)));
    properties.set("pendingConfirmations", stringsSchema());
    required(root, "businessAreas", "processDecisions", "processRelations", "pendingConfirmations");
    return root;
  }

  private ObjectNode areaSchema(List<ActivityIndexCard> cards) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("areaLocalId", textSchema());
    properties.set("name", textSchema());
    properties.set("purpose", textSchema());
    properties.set("activityIds", enumArraySchema(cardIds(cards)));
    required(schema, "areaLocalId", "name", "purpose", "activityIds");
    return schema;
  }

  private ObjectNode aliasSchema() {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("canonicalName", textSchema());
    properties.set("aliases", stringsSchema());
    required(schema, "canonicalName", "aliases");
    return schema;
  }

  private ObjectNode candidateSchema(List<ActivityIndexCard> cards) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("candidateLocalId", textSchema());
    properties.set("name", textSchema());
    properties.set("purpose", textSchema());
    ObjectNode use = objectSchema();
    ObjectNode useProperties = use.putObject("properties");
    useProperties.set("activityId", enumSchema(cardIds(cards)));
    useProperties.set("role", enumSchema(CANDIDATE_ROLES.stream().sorted().toList()));
    useProperties.set("variant", textSchema());
    required(use, "activityId", "role", "variant");
    properties.set("activityUses", arraySchema(use));
    required(schema, "candidateLocalId", "name", "purpose", "activityUses");
    return schema;
  }

  private ObjectNode activityDispositionSchema(List<ActivityIndexCard> cards) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("activityId", enumSchema(cardIds(cards)));
    properties.set("disposition", enumSchema(ACTIVITY_DISPOSITIONS.stream().sorted().toList()));
    properties.set("reason", textSchema());
    required(schema, "activityId", "disposition", "reason");
    return schema;
  }

  private ObjectNode detailedProcessSchema(
      Candidate candidate,
      List<String> memberActivityIds,
      Set<String> statementRefs,
      Set<String> sourceRefs) {
    ObjectNode process = objectSchema();
    ObjectNode properties = process.putObject("properties");
    properties.set("processLocalId", textSchema());
    properties.set("name", textSchema());
    properties.set("purpose", textSchema());
    properties.set("scope", textSchema());
    properties.set("participants", stringsSchema());
    properties.set("businessObjects", stringsSchema());
    properties.set(
        "activityUses",
        arraySchema(activityUseSchema(memberActivityIds, statementRefs, sourceRefs)));
    properties.set("stages", arraySchema(stageSchema(statementRefs, sourceRefs)));
    properties.set("branches", stringsSchema());
    properties.set("businessRules", arraySchema(ruleSchema(statementRefs, sourceRefs)));
    properties.set("endResults", stringsSchema());
    properties.set("supportActivityUseLocalIds", stringsSchema());
    properties.set("knowledgeItems", arraySchema(knowledgeSchema(statementRefs, sourceRefs)));
    properties.set("pendingConnections", stringsSchema());
    required(
        process,
        "processLocalId",
        "name",
        "purpose",
        "scope",
        "participants",
        "businessObjects",
        "activityUses",
        "stages",
        "branches",
        "businessRules",
        "endResults",
        "supportActivityUseLocalIds",
        "knowledgeItems",
        "pendingConnections");
    return process;
  }

  private ObjectNode activityUseSchema(
      List<String> memberActivityIds, Set<String> statementRefs, Set<String> sourceRefs) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("useLocalId", textSchema());
    properties.set("activityId", enumSchema(memberActivityIds));
    properties.set("role", enumSchema(CANDIDATE_ROLES.stream().sorted().toList()));
    properties.set("variant", textSchema());
    properties.set("statementRefs", enumArrayReferenceSchema("statementRef", statementRefs));
    properties.set("sourceRefs", enumArrayReferenceSchema("sourceRef", sourceRefs));
    required(schema, "useLocalId", "activityId", "role", "variant", "statementRefs", "sourceRefs");
    return schema;
  }

  private ObjectNode stageSchema(Set<String> statementRefs, Set<String> sourceRefs) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("order", integerSchema());
    properties.set("name", textSchema());
    properties.set("narrative", textSchema());
    properties.set("activityUseLocalIds", stringsSchema());
    properties.set("entryConditions", stringsSchema());
    properties.set("actions", stringsSchema());
    properties.set("stateChanges", stringsSchema());
    properties.set("rejectionConditions", stringsSchema());
    properties.set("outcomes", stringsSchema());
    properties.set("transitions", stringsSchema());
    properties.set("certainty", enumSchema(CERTAINTIES.stream().sorted().toList()));
    properties.set("statementRefs", enumArrayReferenceSchema("statementRef", statementRefs));
    properties.set("sourceRefs", enumArrayReferenceSchema("sourceRef", sourceRefs));
    required(
        schema,
        "order",
        "name",
        "narrative",
        "activityUseLocalIds",
        "entryConditions",
        "actions",
        "stateChanges",
        "rejectionConditions",
        "outcomes",
        "transitions",
        "certainty",
        "statementRefs",
        "sourceRefs");
    return schema;
  }

  private ObjectNode ruleSchema(Set<String> statementRefs, Set<String> sourceRefs) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("subject", textSchema());
    properties.set("when", textSchema());
    properties.set("actionOrDecision", textSchema());
    properties.set("otherwise", nullableTextSchema());
    properties.set("result", textSchema());
    properties.set("certainty", enumSchema(CERTAINTIES.stream().sorted().toList()));
    ObjectNode activityUseLocalIds = stringsSchema();
    activityUseLocalIds.put("minItems", 1);
    properties.set("activityUseLocalIds", activityUseLocalIds);
    properties.set("statementRefs", enumArrayReferenceSchema("statementRef", statementRefs));
    properties.set("sourceRefs", enumArrayReferenceSchema("sourceRef", sourceRefs));
    required(
        schema,
        "subject",
        "when",
        "actionOrDecision",
        "otherwise",
        "result",
        "certainty",
        "activityUseLocalIds",
        "statementRefs",
        "sourceRefs");
    return schema;
  }

  private ObjectNode knowledgeSchema(Set<String> statementRefs, Set<String> sourceRefs) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set(
        "kind",
        enumSchema(
            List.of(
                "OBJECT",
                "FIELD_OR_DIMENSION",
                "OBJECT_RELATION",
                "FORMULA_OR_METRIC",
                "QUESTION")));
    properties.set("text", textSchema());
    properties.set("certainty", enumSchema(CERTAINTIES.stream().sorted().toList()));
    properties.set("statementRefs", enumArrayReferenceSchema("statementRef", statementRefs));
    properties.set("sourceRefs", enumArrayReferenceSchema("sourceRef", sourceRefs));
    required(schema, "kind", "text", "certainty", "statementRefs", "sourceRefs");
    return schema;
  }

  private ObjectNode consolidatedAreaSchema(Set<String> processIds) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("areaId", textSchema());
    properties.set("name", textSchema());
    properties.set("purpose", textSchema());
    properties.set("processIds", enumArraySchema(processIds));
    required(schema, "areaId", "name", "purpose", "processIds");
    return schema;
  }

  private ObjectNode processDecisionSchema(Set<String> processIds) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("processId", enumSchema(processIds));
    properties.set("disposition", enumSchema(List.of("KEEP", "MERGE_INTO", "REJECT")));
    ObjectNode target = JsonNodeFactory.instance.objectNode();
    ArrayNode targetVariants = target.putArray("anyOf");
    targetVariants.add(enumSchema(processIds));
    targetVariants.addObject().put("type", "null");
    properties.set("targetProcessId", target);
    properties.set("reason", textSchema());
    required(schema, "processId", "disposition", "targetProcessId", "reason");
    return schema;
  }

  private ObjectNode processRelationSchema(Set<String> processIds, List<String> sourceRefs) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("fromProcessId", enumSchema(processIds));
    properties.set("toProcessId", enumSchema(processIds));
    properties.set("relationType", enumSchema(List.of("PARENT_CHILD", "RELATED", "ALTERNATIVE")));
    properties.set("description", textSchema());
    properties.set("certainty", enumSchema(CERTAINTIES.stream().sorted().toList()));
    properties.set("sourceRefs", enumArraySchema(sourceRefs));
    required(
        schema,
        "fromProcessId",
        "toProcessId",
        "relationType",
        "description",
        "certainty",
        "sourceRefs");
    return schema;
  }

  private static ObjectNode objectSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    return schema;
  }

  private static ObjectNode textSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "string");
    schema.put("minLength", 1);
    return schema;
  }

  private static ObjectNode nullableTextSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    ArrayNode types = schema.putArray("type");
    types.add("string").add("null");
    return schema;
  }

  private static ObjectNode integerSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "integer");
    schema.put("minimum", 1);
    return schema;
  }

  private static ObjectNode stringsSchema() {
    return arraySchema(textSchema());
  }

  private static ObjectNode arraySchema(ObjectNode item) {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "array");
    schema.set("items", item);
    return schema;
  }

  private static ObjectNode enumSchema(Iterable<String> values) {
    ObjectNode schema = textSchema();
    ArrayNode allowed = schema.putArray("enum");
    List<String> stableValues = new ArrayList<>();
    values.forEach(stableValues::add);
    stableValues.stream().distinct().sorted(UTF8_ORDER).forEach(allowed::add);
    return schema;
  }

  private static ObjectNode enumArraySchema(Iterable<String> values) {
    List<String> allowed = new ArrayList<>();
    values.forEach(allowed::add);
    ObjectNode schema = arraySchema(allowed.isEmpty() ? textSchema() : enumSchema(allowed));
    if (allowed.isEmpty()) {
      schema.put("maxItems", 0);
    }
    return schema;
  }

  private static ObjectNode enumArrayReferenceSchema(
      String definitionName, Iterable<String> values) {
    List<String> allowed = new ArrayList<>();
    values.forEach(allowed::add);
    ObjectNode schema = arraySchema(definitionReference(definitionName));
    if (allowed.isEmpty()) {
      schema.put("maxItems", 0);
    }
    return schema;
  }

  private static ObjectNode definitionReference(String definitionName) {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("$ref", "#/$defs/" + definitionName);
    return schema;
  }

  private static void required(ObjectNode schema, String... fields) {
    ArrayNode required = schema.putArray("required");
    for (String field : fields) {
      required.add(field);
    }
  }

  private static List<String> cardIds(List<ActivityIndexCard> cards) {
    return cards.stream().map(ActivityIndexCard::activityId).toList();
  }

  private static Set<String> packetStatementRefs(ReadingPacket packet, FrozenCorpus corpus) {
    return packet.activityIds().stream()
        .flatMap(activityId -> corpus.statementHandles(activityId).stream())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static Set<String> packetSourceRefs(ReadingPacket packet) {
    return packet.sources().stream()
        .map(SourceReference::ref)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static void requireInputCapacity(
      ObjectNode input, ProcessDiscoveryProfile profile, String code) {
    if (new CanonicalJsonCodec().encodeCanonical(input).size() > profile.maxModelInputBytes()) {
      throw failure(code);
    }
  }

  private static void requireConfirmedBasis(
      String certainty, List<String> statementRefs, List<String> sourceRefs) {
    if ("CONFIRMED".equals(certainty) && statementRefs.isEmpty() && sourceRefs.isEmpty()) {
      throw failure("CONFIRMED_PROCESS_CLAIM_REQUIRES_SOURCE");
    }
  }

  private static String certainty(ObjectNode value) {
    String certainty = text(value, "certainty");
    if (!CERTAINTIES.contains(certainty)) {
      throw failure("PROCESS_CERTAINTY_INVALID");
    }
    return certainty;
  }

  private static List<String> activityIds(CatalogResult catalog, String disposition) {
    return catalog.activityDispositions().stream()
        .filter(value -> disposition.equals(value.disposition()))
        .map(ProcessCoverage.ActivityDisposition::activityId)
        .sorted(UTF8_ORDER)
        .toList();
  }

  private static ObjectNode activityUseIdentity(
      String candidateId,
      String activityId,
      String role,
      String variant,
      List<String> statementRefs,
      List<String> sourceRefs) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("candidateId", candidateId);
    value.put("activityId", activityId);
    value.put("role", role);
    value.put("variant", variant);
    ArrayNode statements = value.putArray("statementRefs");
    statementRefs.stream().sorted(UTF8_ORDER).forEach(statements::add);
    ArrayNode sources = value.putArray("sourceRefs");
    sourceRefs.stream().sorted(UTF8_ORDER).forEach(sources::add);
    return value;
  }

  private static String candidateId(String name, String purpose, List<CandidateUse> uses) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("name", name);
    value.put("purpose", purpose);
    ArrayNode members = value.putArray("activityUses");
    uses.stream()
        .sorted(
            Comparator.comparing(CandidateUse::activityId)
                .thenComparing(CandidateUse::role)
                .thenComparing(CandidateUse::variant))
        .forEach(use -> members.add(use.toJson()));
    return "process-candidate:" + sha256(new CanonicalJsonCodec().encodeCanonical(value));
  }

  private static boolean isLosslessMerge(
      RepositoryBusinessProcessCatalog.BusinessProcess target,
      RepositoryBusinessProcessCatalog.BusinessProcess source) {
    Map<String, ActivityUseTuple> targetUses = activityUseTuples(target);
    Map<String, ActivityUseTuple> sourceUses = activityUseTuples(source);
    return normalizedStages(target, targetUses).equals(normalizedStages(source, sourceUses))
        && processIdentity(target).equals(processIdentity(source));
  }

  private static ProcessIdentity processIdentity(
      RepositoryBusinessProcessCatalog.BusinessProcess process) {
    return new ProcessIdentity(process.name(), process.purpose(), process.scope());
  }

  private static RepositoryBusinessProcessCatalog.BusinessProcess merge(
      RepositoryBusinessProcessCatalog.BusinessProcess primary,
      List<RepositoryBusinessProcessCatalog.BusinessProcess> duplicates) {
    RepositoryBusinessProcessCatalog.BusinessProcess merged = primary;
    for (RepositoryBusinessProcessCatalog.BusinessProcess duplicate : duplicates) {
      merged = merge(merged, duplicate);
    }
    return merged;
  }

  private static RepositoryBusinessProcessCatalog.BusinessProcess merge(
      RepositoryBusinessProcessCatalog.BusinessProcess target,
      RepositoryBusinessProcessCatalog.BusinessProcess source) {
    ActivityUseMerge uses = mergeActivityUses(target.activityUses(), source.activityUses());
    return new RepositoryBusinessProcessCatalog.BusinessProcess(
        target.processId(),
        target.name(),
        target.purpose(),
        target.scope(),
        unionStrings(target.participants(), source.participants()),
        unionStrings(target.businessObjects(), source.businessObjects()),
        uses.activityUses(),
        target.stages(),
        unionStrings(target.branches(), source.branches()),
        mergeRules(target.businessRules(), source.businessRules(), uses.sourceActivityUseIdMap()),
        unionStrings(target.endResults(), source.endResults()),
        unionStrings(
            target.supportActivityUseIds(),
            remappedUseIds(source.supportActivityUseIds(), uses.sourceActivityUseIdMap())),
        mergeKnowledgeItems(target.processId(), target.knowledgeItems(), source.knowledgeItems()),
        unionStrings(target.pendingConnections(), source.pendingConnections()),
        unionStrings(target.sourceRefs(), source.sourceRefs()));
  }

  private static ActivityUseMerge mergeActivityUses(
      List<RepositoryBusinessProcessCatalog.ActivityUse> target,
      List<RepositoryBusinessProcessCatalog.ActivityUse> source) {
    List<RepositoryBusinessProcessCatalog.ActivityUse> merged = new ArrayList<>(target);
    Map<ActivityUseTuple, Integer> targetIndexes = new LinkedHashMap<>();
    for (int index = 0; index < merged.size(); index++) {
      targetIndexes.putIfAbsent(activityUseTuple(merged.get(index)), index);
    }
    Map<String, String> sourceUseIds = new LinkedHashMap<>();
    for (RepositoryBusinessProcessCatalog.ActivityUse sourceUse : source) {
      ActivityUseTuple tuple = activityUseTuple(sourceUse);
      Integer targetIndex = targetIndexes.get(tuple);
      if (targetIndex == null) {
        targetIndexes.put(tuple, merged.size());
        merged.add(sourceUse);
        sourceUseIds.put(sourceUse.activityUseId(), sourceUse.activityUseId());
        continue;
      }
      RepositoryBusinessProcessCatalog.ActivityUse targetUse = merged.get(targetIndex);
      merged.set(
          targetIndex,
          new RepositoryBusinessProcessCatalog.ActivityUse(
              targetUse.activityUseId(),
              targetUse.activityId(),
              targetUse.role(),
              targetUse.variant(),
              unionStrings(targetUse.statementRefs(), sourceUse.statementRefs()),
              unionStrings(targetUse.sourceRefs(), sourceUse.sourceRefs())));
      sourceUseIds.put(sourceUse.activityUseId(), targetUse.activityUseId());
    }
    return new ActivityUseMerge(List.copyOf(merged), Map.copyOf(sourceUseIds));
  }

  private static List<RepositoryBusinessProcessCatalog.BusinessRule> mergeRules(
      List<RepositoryBusinessProcessCatalog.BusinessRule> target,
      List<RepositoryBusinessProcessCatalog.BusinessRule> source,
      Map<String, String> sourceUseIds) {
    List<RepositoryBusinessProcessCatalog.BusinessRule> merged = new ArrayList<>(target);
    for (RepositoryBusinessProcessCatalog.BusinessRule sourceRule : source) {
      RepositoryBusinessProcessCatalog.BusinessRule remapped =
          new RepositoryBusinessProcessCatalog.BusinessRule(
              sourceRule.subject(),
              sourceRule.when(),
              sourceRule.actionOrDecision(),
              sourceRule.otherwise(),
              sourceRule.result(),
              sourceRule.certainty(),
              remappedUseIds(sourceRule.activityUseIds(), sourceUseIds),
              sourceRule.statementRefs(),
              sourceRule.sourceRefs());
      if (!merged.contains(remapped)) {
        merged.add(remapped);
      }
    }
    return List.copyOf(merged);
  }

  private static List<RepositoryBusinessProcessCatalog.KnowledgeItem> mergeKnowledgeItems(
      String targetProcessId,
      List<RepositoryBusinessProcessCatalog.KnowledgeItem> target,
      List<RepositoryBusinessProcessCatalog.KnowledgeItem> source) {
    List<RepositoryBusinessProcessCatalog.KnowledgeItem> merged = new ArrayList<>(target);
    for (RepositoryBusinessProcessCatalog.KnowledgeItem sourceItem : source) {
      RepositoryBusinessProcessCatalog.KnowledgeItem remapped =
          new RepositoryBusinessProcessCatalog.KnowledgeItem(
              sourceItem.kind(),
              sourceItem.text(),
              targetProcessId,
              sourceItem.certainty(),
              sourceItem.statementRefs(),
              sourceItem.sourceRefs());
      if (!merged.contains(remapped)) {
        merged.add(remapped);
      }
    }
    return List.copyOf(merged);
  }

  private static List<String> remappedUseIds(
      List<String> sourceUseIds, Map<String, String> sourceUseIdMap) {
    return sourceUseIds.stream()
        .map(
            sourceUseId -> {
              String targetUseId = sourceUseIdMap.get(sourceUseId);
              if (targetUseId == null) {
                throw failure("PROCESS_CONSOLIDATION_MERGE_NOT_LOSSLESS");
              }
              return targetUseId;
            })
        .toList();
  }

  private static List<String> unionStrings(List<String> target, List<String> source) {
    LinkedHashSet<String> merged = new LinkedHashSet<>(target);
    merged.addAll(source);
    return List.copyOf(merged);
  }

  private static ActivityUseTuple activityUseTuple(
      RepositoryBusinessProcessCatalog.ActivityUse activityUse) {
    return new ActivityUseTuple(
        activityUse.activityId(), activityUse.variant(), activityUse.role());
  }

  private static Map<String, ActivityUseTuple> activityUseTuples(
      RepositoryBusinessProcessCatalog.BusinessProcess process) {
    Map<String, ActivityUseTuple> tuples = new LinkedHashMap<>();
    for (RepositoryBusinessProcessCatalog.ActivityUse use : process.activityUses()) {
      tuples.put(
          use.activityUseId(), new ActivityUseTuple(use.activityId(), use.variant(), use.role()));
    }
    return Map.copyOf(tuples);
  }

  private static List<NormalizedStage> normalizedStages(
      RepositoryBusinessProcessCatalog.BusinessProcess process,
      Map<String, ActivityUseTuple> activityUses) {
    return process.stages().stream()
        .map(
            stage ->
                new NormalizedStage(
                    stage.order(),
                    stage.name(),
                    stage.narrative(),
                    normalizedUseIds(stage.activityUseIds(), activityUses),
                    stage.entryConditions(),
                    stage.actions(),
                    stage.stateChanges(),
                    stage.rejectionConditions(),
                    stage.outcomes(),
                    stage.transitions(),
                    stage.certainty(),
                    stage.statementRefs(),
                    stage.sourceRefs()))
        .toList();
  }

  private static List<ActivityUseTuple> normalizedUseIds(
      List<String> activityUseIds, Map<String, ActivityUseTuple> activityUses) {
    return activityUseIds.stream()
        .map(
            activityUseId -> {
              ActivityUseTuple activityUse = activityUses.get(activityUseId);
              if (activityUse == null) {
                throw failure("PROCESS_CONSOLIDATION_MERGE_NOT_LOSSLESS");
              }
              return activityUse;
            })
        .toList();
  }

  private static ArrayNode array(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!(node instanceof ArrayNode array)) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return array;
  }

  private static ObjectNode object(JsonNode value) {
    if (!(value instanceof ObjectNode object)) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return object;
  }

  private static String text(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.textValue().isBlank()) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return node.textValue();
  }

  private static String nullableText(JsonNode value) {
    if (value.isNull()) {
      return null;
    }
    if (!value.isTextual()) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return value.textValue();
  }

  private static String optionalNullableText(JsonNode value) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      return null;
    }
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return value.textValue();
  }

  private static String optionalText(JsonNode value, String fallback) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      return fallback;
    }
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return value.textValue();
  }

  private static int positiveInt(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.canConvertToInt() || node.intValue() < 1) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return node.intValue();
  }

  private static List<String> strings(ObjectNode value, String field) {
    return strings(array(value, field));
  }

  private static List<String> strings(ArrayNode array) {
    List<String> result = new ArrayList<>();
    for (JsonNode item : array) {
      if (!item.isTextual() || item.textValue().isBlank()) {
        throw failure("PROCESS_MODEL_RESPONSE_INVALID");
      }
      result.add(item.textValue());
    }
    return List.copyOf(result);
  }

  private static void requireSubset(List<String> values, Set<String> allowed, String code) {
    if (values.stream().distinct().count() != values.size()
        || values.stream().anyMatch(value -> !allowed.contains(value))) {
      throw failure(code);
    }
  }

  private static String idSuffix(String id) {
    int separator = id.indexOf(':');
    return separator < 0 ? id : id.substring(separator + 1);
  }

  private static String sha256(String value) {
    return sha256(ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static String sha256(ImmutableBytes bytes) {
    try {
      return java.util.HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copyToByteArray()));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static IllegalArgumentException failure(String code) {
    return new IllegalArgumentException(code);
  }

  private record ModelCall(ObjectNode value, ModelRuntimeIdentityV1 runtimeIdentity) {}

  private record ReviewedPair(ModelCall draft, ModelCall review) {}

  private record IndexedJson(int ordinal, ObjectNode value) {}

  private record IndexedCandidate(int ordinal, CandidateResult value) {}

  private record IndexedRawCandidate(int ordinal, RawCandidateResult value) {}

  private record IndexedCandidateSelection(int ordinal, Candidate candidate) {}

  static final class CatalogSample {
    private final ProcessDiscoveryRequest request;
    private final FrozenCorpus corpus;
    private final List<ActivityIndexCard> cards;
    private final CatalogResult catalog;
    private final MaterialSelection selection;
    private final FrozenProcessSourceCorpus sourceText;

    private CatalogSample(
        ProcessDiscoveryRequest request,
        FrozenCorpus corpus,
        List<ActivityIndexCard> cards,
        CatalogResult catalog,
        MaterialSelection selection,
        FrozenProcessSourceCorpus sourceText) {
      this.request = request;
      this.corpus = corpus;
      this.cards = cards;
      this.catalog = catalog;
      this.selection = selection;
      this.sourceText = sourceText;
    }

    List<String> candidateIds() {
      return catalog.candidates().stream().map(Candidate::candidateId).toList();
    }
  }

  private record AreaSeed(String localId, String name, String purpose, List<String> activityIds) {}

  private record AreaDecision(
      String areaId, String name, String purpose, List<String> processIds) {}

  private record CandidateUse(String activityId, String role, String variant) {
    ObjectNode toJson() {
      ObjectNode value = JsonNodeFactory.instance.objectNode();
      value.put("activityId", activityId);
      value.put("role", role);
      value.put("variant", variant);
      return value;
    }
  }

  private record CandidateActivityVariant(String activityId, String variant) {}

  private record Candidate(
      String candidateId,
      String localId,
      String name,
      String purpose,
      String scope,
      List<CandidateUse> uses,
      List<String> contextActivityIds) {
    ObjectNode toJson() {
      ObjectNode value = JsonNodeFactory.instance.objectNode();
      value.put("candidateId", candidateId);
      value.put("candidateLocalId", localId);
      value.put("name", name);
      value.put("purpose", purpose);
      value.put("scope", scope);
      ArrayNode values = value.putArray("activityUses");
      uses.forEach(use -> values.add(use.toJson()));
      strings(value.putArray("contextActivityIds"), contextActivityIds);
      return value;
    }
  }

  private record MaterialSelection(
      CatalogResult baseline,
      List<Candidate> candidates,
      Map<String, List<SourceReadRequest>> initialRequests,
      List<ProcessCoverage.ActivityDisposition> changedDispositions) {
    CatalogResult catalogWith(
        List<Candidate> finalCandidates,
        List<ProcessCoverage.ActivityDisposition> additionalChanges,
        List<ActivityIndexCard> cards) {
      Map<String, ProcessCoverage.ActivityDisposition> baselineById =
          baseline.activityDispositions().stream()
              .collect(
                  Collectors.toMap(
                      ProcessCoverage.ActivityDisposition::activityId, Function.identity()));
      Map<String, ProcessCoverage.ActivityDisposition> changes = new LinkedHashMap<>();
      java.util.stream.Stream.concat(changedDispositions.stream(), additionalChanges.stream())
          .forEach(
              change -> {
                ProcessCoverage.ActivityDisposition previous =
                    changes.put(change.activityId(), change);
                if (previous != null && !previous.equals(change)) {
                  throw failure("PROCESS_READING_ACTIVITY_DISPOSITION_INVALID");
                }
              });
      Set<String> members =
          finalCandidates.stream()
              .flatMap(candidate -> candidate.uses().stream())
              .map(CandidateUse::activityId)
              .collect(Collectors.toSet());
      List<ProcessCoverage.ActivityDisposition> dispositions = new ArrayList<>();
      for (ActivityIndexCard card : cards) {
        ProcessCoverage.ActivityDisposition baselineDisposition =
            baselineById.get(card.activityId());
        ProcessCoverage.ActivityDisposition changed = changes.get(card.activityId());
        if (baselineDisposition == null) {
          throw failure("PROCESS_CATALOG_ACTIVITY_DENOMINATOR_OPEN");
        }
        if (members.contains(card.activityId())) {
          dispositions.add(
              new ProcessCoverage.ActivityDisposition(
                  card.activityId(), card.name(), "PROCESS_MEMBER", baselineDisposition.reason()));
        } else if (changed != null && !"PROCESS_MEMBER".equals(changed.disposition())) {
          dispositions.add(changed);
        } else if ("PROCESS_MEMBER".equals(baselineDisposition.disposition())) {
          throw failure("PROCESS_READING_REMOVED_MEMBER_DISPOSITION_REQUIRED");
        } else {
          dispositions.add(baselineDisposition);
        }
      }
      return new CatalogResult(
          baseline.areas(),
          baseline.aliases(),
          finalCandidates.stream()
              .sorted(Comparator.comparing(Candidate::candidateId, UTF8_ORDER))
              .toList(),
          dispositions.stream()
              .sorted(
                  Comparator.comparing(ProcessCoverage.ActivityDisposition::activityId, UTF8_ORDER))
              .toList(),
          baseline.unresolvedQuestions());
    }
  }

  private record SourceReadRequest(
      String requestId,
      String kind,
      String sourceRef,
      String fileKey,
      int startLine,
      int endLine,
      String literal,
      int contextLines) {}

  private record PacketMaterials(
      List<String> activityIds, List<SourceReference> sources, List<String> limitations) {}

  private record ReadingCheck(
      Candidate candidate,
      List<String> contextActivityIds,
      List<SourceReadRequest> supplementaryRequests,
      List<ProcessCoverage.ActivityDisposition> changedDispositions,
      List<String> unresolvedQuestions) {}

  private record ReadingPacket(
      int ordinal,
      Candidate candidate,
      List<String> activityIds,
      List<SourceReference> sources,
      List<String> limitations,
      List<ProcessCoverage.ActivityDisposition> changedDispositions,
      List<String> unresolvedQuestions) {}

  private record SourceIdentity(String file, int startLine, int endLine, String snippet) {
    static SourceIdentity from(SourceReference source) {
      return new SourceIdentity(
          source.file(), source.startLine(), source.endLine(), source.snippet());
    }
  }

  private record SourceReferenceMapping(String localRef, String finalRef, SourceReference source) {}

  private record SourceNormalization(
      Map<Integer, ReadingPacket> finalPackets,
      Map<Integer, List<SourceReferenceMapping>> mappings) {
    ReadingPacket finalPacket(int ordinal) {
      ReadingPacket packet = finalPackets.get(ordinal);
      if (packet == null) {
        throw failure("PROCESS_READING_SOURCE_REFERENCE_MAPPING_INVALID");
      }
      return packet;
    }

    List<SourceReferenceMapping> mappingFor(int ordinal) {
      List<SourceReferenceMapping> mapping = mappings.get(ordinal);
      if (mapping == null) {
        throw failure("PROCESS_READING_SOURCE_REFERENCE_MAPPING_INVALID");
      }
      return mapping;
    }

    List<ReadingPacket> finalPacketList() {
      return finalPackets.values().stream()
          .sorted(Comparator.comparingInt(ReadingPacket::ordinal))
          .toList();
    }
  }

  private record CatalogResult(
      List<AreaSeed> areas,
      List<RepositoryBusinessProcessCatalog.BusinessAlias> aliases,
      List<Candidate> candidates,
      List<ProcessCoverage.ActivityDisposition> activityDispositions,
      List<String> unresolvedQuestions) {}

  private record ParsedCandidateDraft(
      String disposition,
      String reason,
      List<RepositoryBusinessProcessCatalog.BusinessProcess> processes) {}

  private record CandidateResult(
      Candidate candidate,
      String disposition,
      String reason,
      List<RepositoryBusinessProcessCatalog.BusinessProcess> processes,
      ObjectNode reviewedResponse) {
    static CandidateResult notProcessed(Candidate candidate, String disposition) {
      return new CandidateResult(candidate, disposition, "候选材料超过单任务容量", List.of(), null);
    }
  }

  private record RawCandidateResult(
      ReadingPacket packet,
      ModelCall draft,
      ModelCall review,
      String inputFingerprint,
      ModelJobProviderBinding binding,
      boolean reused,
      String capacityDisposition) {
    RawCandidateResult(
        ReadingPacket packet,
        ModelCall draft,
        ModelCall review,
        String inputFingerprint,
        ModelJobProviderBinding binding,
        boolean reused) {
      this(packet, draft, review, inputFingerprint, binding, reused, null);
    }

    static RawCandidateResult notProcessed(ReadingPacket packet, String disposition) {
      return new RawCandidateResult(packet, null, null, null, null, false, disposition);
    }
  }

  private record ProcessDecision(
      String processId, String disposition, String targetProcessId, String reason) {}

  private record ConsolidationDecision(
      List<AreaDecision> areas,
      List<ProcessDecision> decisions,
      List<RepositoryBusinessProcessCatalog.ProcessRelation> relations,
      List<String> pendingConfirmations) {}

  private record Consolidated(RepositoryBusinessProcessCatalog catalog, ProcessCoverage coverage) {}

  private record ProcessIdentity(String name, String purpose, String scope) {}

  private record ActivityUseTuple(String activityId, String variant, String role) {}

  private record ActivityUseMerge(
      List<RepositoryBusinessProcessCatalog.ActivityUse> activityUses,
      Map<String, String> sourceActivityUseIdMap) {}

  private record NormalizedStage(
      int order,
      String name,
      String narrative,
      List<ActivityUseTuple> activityUses,
      List<String> entryConditions,
      List<String> actions,
      List<String> stateChanges,
      List<String> rejectionConditions,
      List<String> outcomes,
      List<String> transitions,
      String certainty,
      List<String> statementRefs,
      List<String> sourceRefs) {}

  private record ActivityIndexCard(
      String activityId,
      String name,
      String businessPurpose,
      List<String> participants,
      List<String> businessObjects,
      List<String> triggerOrInput,
      List<String> conditions,
      List<String> activitySteps,
      List<String> codeDefinedResults,
      List<String> businessRules,
      List<String> terms,
      List<String> scopeLimitations) {
    static ActivityIndexCard from(ReviewedActivity activity) {
      return new ActivityIndexCard(
          activity.activityId(),
          activity.name(),
          activity.businessPurpose(),
          activity.participants(),
          activity.businessObjects(),
          activity.triggerOrInput(),
          activity.conditions(),
          activity.activitySteps(),
          activity.codeDefinedResults(),
          activity.businessRules(),
          activity.terms(),
          activity.scopeLimitations());
    }

    ObjectNode toJson() {
      ObjectNode value = JsonNodeFactory.instance.objectNode();
      value.put("activityId", activityId);
      value.put("name", name);
      value.put("businessPurpose", businessPurpose);
      strings(value.putArray("participants"), participants);
      strings(value.putArray("businessObjects"), businessObjects);
      strings(value.putArray("triggerOrInput"), triggerOrInput);
      strings(value.putArray("conditions"), conditions);
      strings(value.putArray("activitySteps"), activitySteps);
      strings(value.putArray("codeDefinedResults"), codeDefinedResults);
      strings(value.putArray("businessRules"), businessRules);
      strings(value.putArray("terms"), terms);
      strings(value.putArray("scopeLimitations"), scopeLimitations);
      return value;
    }

    ObjectNode toNavigationJson(FrozenCorpus corpus) {
      ObjectNode value = JsonNodeFactory.instance.objectNode();
      value.put("activityId", activityId);
      value.put("name", name);
      value.put("businessPurpose", businessPurpose);
      strings(value.putArray("businessObjects"), businessObjects);
      strings(value.putArray("terms"), terms);
      value.put("statementCount", corpus.statementHandles(activityId).size());
      strings(
          value.putArray("sourceRefs"),
          corpus.activitySourceRefs(activityId).stream().sorted(UTF8_ORDER).toList());
      return value;
    }
  }

  private static final class FrozenCorpus {
    private static final List<String> LIST_FIELDS =
        List.of(
            "participants",
            "businessObjects",
            "triggerOrInput",
            "conditions",
            "activitySteps",
            "codeDefinedResults",
            "businessRules",
            "formulasOrMetrics",
            "terms",
            "questions",
            "scopeLimitations");

    private final List<ReviewedActivity> activities;
    private final Map<String, ReviewedActivity> activitiesById;
    private final Map<String, SourceReference> sourcesByRef;
    private final Map<String, Set<String>> sourceRefsByActivity;
    private final Map<String, Map<String, String>> statementsByActivity;
    private final Set<String> unexplainedEntryIds;

    private FrozenCorpus(
        List<ReviewedActivity> activities,
        Map<String, ReviewedActivity> activitiesById,
        Map<String, SourceReference> sourcesByRef,
        Map<String, Set<String>> sourceRefsByActivity,
        Map<String, Map<String, String>> statementsByActivity,
        Set<String> unexplainedEntryIds) {
      this.activities = activities;
      this.activitiesById = activitiesById;
      this.sourcesByRef = sourcesByRef;
      this.sourceRefsByActivity = sourceRefsByActivity;
      this.statementsByActivity = statementsByActivity;
      this.unexplainedEntryIds = unexplainedEntryIds;
    }

    static FrozenCorpus open(
        ActivityExplanationResult activityResult, BusinessMaterialBuildResult materialResult) {
      List<ReviewedActivity> activities =
          activityResult.reviewedActivities().stream()
              .sorted(Comparator.comparing(ReviewedActivity::activityId, UTF8_ORDER))
              .toList();
      Map<String, ReviewedActivity> byId = new LinkedHashMap<>();
      for (ReviewedActivity activity : activities) {
        if (byId.put(activity.activityId(), activity) != null) {
          throw failure("PROCESS_CORPUS_DUPLICATE_ACTIVITY");
        }
      }
      Set<String> coveredActivities =
          activityResult.coverage().stream()
              .flatMap(value -> value.activityIds().stream())
              .collect(Collectors.toSet());
      if (!coveredActivities.equals(byId.keySet())) {
        throw failure("PROCESS_CORPUS_ACTIVITY_COVERAGE_OPEN");
      }
      Map<String, BusinessMaterial> materialsById =
          materialResult.materialSet().materials().stream()
              .collect(Collectors.toMap(BusinessMaterial::materialId, Function.identity()));
      Map<String, SourceReference> sources = new LinkedHashMap<>();
      for (BusinessMaterial material : materialResult.materialSet().materials()) {
        for (SourceReference source : material.sourceRefs()) {
          SourceReference previous = sources.putIfAbsent(source.ref(), source);
          if (previous != null && !previous.equals(source)) {
            throw failure("PROCESS_CORPUS_SOURCE_IDENTITY_COLLISION");
          }
        }
      }
      Map<String, Set<String>> activitySources = new LinkedHashMap<>();
      Map<String, Map<String, String>> statements = new LinkedHashMap<>();
      for (ReviewedActivity activity : activities) {
        BusinessMaterial material = materialsById.get(activity.materialId());
        if (material == null) {
          throw failure("PROCESS_CORPUS_ACTIVITY_MATERIAL_MISSING");
        }
        Map<String, SourceReference> materialSources =
            material.sourceRefs().stream()
                .collect(Collectors.toMap(SourceReference::ref, Function.identity()));
        Set<String> refs = new LinkedHashSet<>();
        for (String ref : activity.sourceRefs()) {
          SourceReference source = materialSources.get(ref);
          if (source == null) {
            throw failure("PROCESS_CORPUS_ACTIVITY_SOURCE_MISSING");
          }
          refs.add(ref);
        }
        activitySources.put(activity.activityId(), Set.copyOf(refs));
        statements.put(activity.activityId(), statementMap(activity));
      }
      Set<String> unexplained =
          activityResult.unexplainedActivityEntries().stream()
              .map(value -> value.entryId())
              .collect(Collectors.toUnmodifiableSet());
      return new FrozenCorpus(
          activities,
          Map.copyOf(byId),
          Map.copyOf(sources),
          Map.copyOf(activitySources),
          Map.copyOf(statements),
          unexplained);
    }

    private static Map<String, String> statementMap(ReviewedActivity activity) {
      Map<String, String> values = new LinkedHashMap<>();
      values.put(activity.activityId() + "/businessPurpose", activity.businessPurpose());
      Map<String, List<String>> lists = new LinkedHashMap<>();
      lists.put("participants", activity.participants());
      lists.put("businessObjects", activity.businessObjects());
      lists.put("triggerOrInput", activity.triggerOrInput());
      lists.put("conditions", activity.conditions());
      lists.put("activitySteps", activity.activitySteps());
      lists.put("codeDefinedResults", activity.codeDefinedResults());
      lists.put("businessRules", activity.businessRules());
      lists.put("formulasOrMetrics", activity.formulasOrMetrics());
      lists.put("terms", activity.terms());
      lists.put("questions", activity.questions());
      lists.put("scopeLimitations", activity.scopeLimitations());
      for (String field : LIST_FIELDS) {
        List<String> items = lists.get(field);
        for (int index = 0; index < items.size(); index++) {
          values.put(activity.activityId() + "/" + field + "/" + index, items.get(index));
        }
      }
      return Map.copyOf(values);
    }

    List<ReviewedActivity> activities() {
      return activities;
    }

    Set<String> activityIds() {
      return activitiesById.keySet();
    }

    Set<String> unexplainedEntryIds() {
      return unexplainedEntryIds;
    }

    SourceReference source(String ref) {
      SourceReference source = sourcesByRef.get(ref);
      if (source == null) {
        throw failure("PROCESS_CORPUS_UNKNOWN_SOURCE_REFERENCE");
      }
      return source;
    }

    List<SourceReference> sourceReferences() {
      return sourcesByRef.values().stream()
          .sorted(Comparator.comparing(SourceReference::ref, UTF8_ORDER))
          .toList();
    }

    Set<String> activitySourceRefs(String activityId) {
      Set<String> refs = sourceRefsByActivity.get(activityId);
      if (refs == null) {
        throw failure("PROCESS_CORPUS_UNKNOWN_ACTIVITY");
      }
      return refs;
    }

    Set<String> statementHandles(String activityId) {
      Map<String, String> statements = statementsByActivity.get(activityId);
      if (statements == null) {
        throw failure("PROCESS_CORPUS_UNKNOWN_ACTIVITY");
      }
      return statements.keySet();
    }

    ObjectNode activityJson(String activityId) {
      ReviewedActivity activity = activitiesById.get(activityId);
      if (activity == null) {
        throw failure("PROCESS_CORPUS_UNKNOWN_ACTIVITY");
      }
      ObjectNode value = JsonNodeFactory.instance.objectNode();
      value.put("activityId", activity.activityId());
      value.put("name", activity.name());
      value.put("businessPurpose", activity.businessPurpose());
      strings(value.putArray("participants"), activity.participants());
      strings(value.putArray("businessObjects"), activity.businessObjects());
      strings(value.putArray("triggerOrInput"), activity.triggerOrInput());
      strings(value.putArray("conditions"), activity.conditions());
      strings(value.putArray("activitySteps"), activity.activitySteps());
      strings(value.putArray("codeDefinedResults"), activity.codeDefinedResults());
      strings(value.putArray("businessRules"), activity.businessRules());
      strings(value.putArray("formulasOrMetrics"), activity.formulasOrMetrics());
      strings(value.putArray("terms"), activity.terms());
      value.put("certainty", activity.certainty());
      strings(value.putArray("sourceRefs"), activity.sourceRefs());
      strings(value.putArray("questions"), activity.questions());
      strings(value.putArray("scopeLimitations"), activity.scopeLimitations());
      return value;
    }

    List<RepositoryBusinessProcessCatalog.KnowledgeItem> directKnowledge(
        String activityId, String disposition) {
      ReviewedActivity activity = activitiesById.get(activityId);
      if (activity == null) {
        throw failure("PROCESS_CORPUS_UNKNOWN_ACTIVITY");
      }
      List<RepositoryBusinessProcessCatalog.KnowledgeItem> values = new ArrayList<>();
      addKnowledge(
          values, activity, disposition, "OBJECT", "businessObjects", activity.businessObjects());
      addKnowledge(values, activity, disposition, "FIELD_OR_DIMENSION", "terms", activity.terms());
      addKnowledge(
          values,
          activity,
          disposition,
          "FORMULA_OR_METRIC",
          "formulasOrMetrics",
          activity.formulasOrMetrics());
      addKnowledge(values, activity, disposition, "QUESTION", "questions", activity.questions());
      return List.copyOf(values);
    }

    private static void addKnowledge(
        List<RepositoryBusinessProcessCatalog.KnowledgeItem> target,
        ReviewedActivity activity,
        String disposition,
        String kind,
        String field,
        List<String> values) {
      for (int index = 0; index < values.size(); index++) {
        target.add(
            new RepositoryBusinessProcessCatalog.KnowledgeItem(
                kind,
                values.get(index),
                activity.activityId() + "#" + disposition,
                "DIRECT_CODE_BEHAVIOR".equals(activity.certainty()) ? "CONFIRMED" : "INFERRED",
                List.of(activity.activityId() + "/" + field + "/" + index),
                activity.sourceRefs()));
      }
    }
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  private static void sourceJson(ObjectNode value, SourceReference source) {
    value.put("ref", source.ref());
    value.put("file", source.file());
    value.put("startLine", source.startLine());
    value.put("endLine", source.endLine());
    value.put("snippet", source.snippet());
  }
}
