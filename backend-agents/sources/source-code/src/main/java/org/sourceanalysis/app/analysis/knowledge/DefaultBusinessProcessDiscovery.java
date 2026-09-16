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
    List<CandidateResult> candidateResults =
        reconstructCandidates(sample.catalog.candidates(), sample.corpus, sample.request.profile());
    Consolidated consolidated =
        consolidate(sample.catalog, candidateResults, sample.corpus, sample.request.profile());
    return new ProcessDiscoveryResult(
        consolidated.catalog(),
        consolidated.coverage(),
        sample.corpus.sourceReferences(),
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
    CatalogResult catalog = discoverCatalog(cards, request.profile());
    return new CatalogSample(request, corpus, catalog);
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

    List<IndexedCandidateSelection> selections = new ArrayList<>();
    for (int ordinal = 0; ordinal < sample.catalog.candidates().size(); ordinal++) {
      Candidate candidate = sample.catalog.candidates().get(ordinal);
      if (!selected.contains(candidate.candidateId())) {
        continue;
      }
      ObjectNode input = processInput(candidate, sample.corpus);
      if (candidate.uses().size() > sample.request.profile().maxActivitiesPerCandidate()
          || canonicalJson.encodeCanonical(input).size()
              > sample.request.profile().maxModelInputBytes()) {
        throw failure("PROCESS_ACCEPTANCE_SAMPLE_CANDIDATE_CAPACITY_EXCEEDED");
      }
      selections.add(new IndexedCandidateSelection(ordinal, candidate));
    }

    if (modelJobs == null) {
      for (IndexedCandidateSelection selection : selections) {
        reconstruct(
            selection.candidate(),
            sample.corpus,
            sample.request.profile(),
            binding("processGroup", selection.ordinal()));
      }
    } else {
      List<BoundedModelJobExecutor.ModelJob<IndexedCandidate>> jobs = new ArrayList<>();
      for (IndexedCandidateSelection selection : selections) {
        Candidate candidate = selection.candidate();
        int ordinal = selection.ordinal();
        ObjectNode input = processInput(candidate, sample.corpus);
        ObjectNode schema = processSchema(candidate, sample.corpus);
        ModelJobProviderBinding binding = binding("processGroup", ordinal);
        jobs.add(
            new BoundedModelJobExecutor.ModelJob<>(
                "candidate-" + idSuffix(candidate.candidateId()),
                inputFingerprint(
                    PROCESS_DRAFT,
                    PROCESS_REVIEW,
                    input,
                    schema,
                    sample.request.profile(),
                    binding),
                binding,
                () ->
                    new IndexedCandidate(
                        ordinal,
                        reconstruct(candidate, sample.corpus, sample.request.profile(), binding))));
      }
      executor().execute(jobs, ignored -> {});
    }
    return selections.stream()
        .map(IndexedCandidateSelection::candidate)
        .map(Candidate::candidateId)
        .toList();
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

  private List<CandidateResult> reconstructCandidates(
      List<Candidate> candidates, FrozenCorpus corpus, ProcessDiscoveryProfile profile) {
    if (modelJobs == null) {
      List<CandidateResult> results = new ArrayList<>();
      for (int index = 0; index < candidates.size(); index++) {
        results.add(
            reconstruct(candidates.get(index), corpus, profile, binding("processGroup", index)));
      }
      return List.copyOf(results);
    }
    List<BoundedModelJobExecutor.ModelJob<IndexedCandidate>> jobs = new ArrayList<>();
    for (int index = 0; index < candidates.size(); index++) {
      Candidate candidate = candidates.get(index);
      if (candidate.uses().size() > profile.maxActivitiesPerCandidate()
          || canonicalJson.encodeCanonical(processInput(candidate, corpus)).size()
              > profile.maxModelInputBytes()) {
        continue;
      }
      int ordinal = index;
      ObjectNode input = processInput(candidate, corpus);
      ObjectNode schema = processSchema(candidate, corpus);
      ModelJobProviderBinding binding = binding("processGroup", ordinal);
      jobs.add(
          new BoundedModelJobExecutor.ModelJob<>(
              "candidate-" + idSuffix(candidate.candidateId()),
              inputFingerprint(PROCESS_DRAFT, PROCESS_REVIEW, input, schema, profile, binding),
              binding,
              () ->
                  new IndexedCandidate(ordinal, reconstruct(candidate, corpus, profile, binding))));
    }
    Map<Integer, CandidateResult> completed =
        executor().execute(jobs, ignored -> {}).stream()
            .map(BoundedModelJobExecutor.CompletedJob::result)
            .collect(Collectors.toMap(IndexedCandidate::ordinal, IndexedCandidate::value));
    List<CandidateResult> ordered = new ArrayList<>();
    for (int index = 0; index < candidates.size(); index++) {
      ordered.add(
          completed.getOrDefault(
              index,
              CandidateResult.notProcessed(candidates.get(index), "NOT_PROCESSED_CAPACITY")));
    }
    return List.copyOf(ordered);
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

  private CandidateResult reconstruct(
      Candidate candidate,
      FrozenCorpus corpus,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding) {
    if (candidate.uses().size() > profile.maxActivitiesPerCandidate()) {
      return CandidateResult.notProcessed(candidate, "NOT_PROCESSED_CAPACITY");
    }
    ObjectNode input = processInput(candidate, corpus);
    if (canonicalJson.encodeCanonical(input).size() > profile.maxModelInputBytes()) {
      return CandidateResult.notProcessed(candidate, "NOT_PROCESSED_CAPACITY");
    }
    ObjectNode schema = processSchema(candidate, corpus);
    String taskBase = "business-process-" + idSuffix(candidate.candidateId());
    String fingerprint =
        inputFingerprint(PROCESS_DRAFT, PROCESS_REVIEW, input, schema, profile, binding);
    ReviewedPair reused = reopenPair("business-process", taskBase, fingerprint, binding);
    if (reused != null) {
      parseCandidateSourceRequests(reused.draft().value(), candidate, corpus, profile);
      ParsedCandidateDraft reviewed =
          parseCandidate(reused.review().value(), candidate, corpus, profile);
      return new CandidateResult(
          candidate,
          reviewed.disposition(),
          reviewed.reason(),
          reviewed.processes(),
          reused.review().value());
    }
    ModelCall draft = call(PROCESS_DRAFT, taskBase + "-draft", input, schema, profile, binding);
    List<String> requestedSourceRefs =
        parseCandidateSourceRequests(draft.value(), candidate, corpus, profile);
    ObjectNode reviewInput = input.deepCopy();
    reviewInput.set("actualDraft", draft.value());
    ArrayNode excerpts = reviewInput.putArray("resolvedSourceExcerpts");
    int chars = 0;
    for (String ref : requestedSourceRefs) {
      SourceReference source = corpus.source(ref);
      chars += source.snippet().length();
      if (chars > profile.maxRequestedSourceChars()) {
        throw failure("PROCESS_SOURCE_REQUEST_CAPACITY_EXCEEDED");
      }
      sourceJson(excerpts.addObject(), source);
    }
    requireInputCapacity(reviewInput, profile, "PROCESS_REVIEW_INPUT_CAPACITY_EXCEEDED");
    ModelCall review =
        call(
            PROCESS_REVIEW,
            "business-process-" + idSuffix(candidate.candidateId()) + "-review",
            reviewInput,
            schema,
            profile,
            binding);
    if (!draft.runtimeIdentity().equals(review.runtimeIdentity())) {
      throw failure("PROCESS_JOB_RUNTIME_IDENTITY_MISMATCH");
    }
    ParsedCandidateDraft reviewed = parseCandidate(review.value(), candidate, corpus, profile);
    CandidateResult result =
        new CandidateResult(
            candidate,
            reviewed.disposition(),
            reviewed.reason(),
            reviewed.processes(),
            review.value());
    savePair("business-process", taskBase, fingerprint, binding, draft, review);
    return result;
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
      candidates.add(new Candidate(candidateId, localId, name, purpose, List.copyOf(uses)));
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
      ObjectNode value, Candidate candidate, FrozenCorpus corpus, ProcessDiscoveryProfile profile) {
    String disposition = text(value, "disposition");
    if (!CANDIDATE_DISPOSITIONS.contains(disposition)
        || "NOT_PROCESSED_CAPACITY".equals(disposition)) {
      throw failure("PROCESS_CANDIDATE_DISPOSITION_INVALID");
    }
    List<String> requested = parseCandidateSourceRequests(value, candidate, corpus, profile);
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
      processes.add(process(process, candidate, corpus));
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
    return new ParsedCandidateDraft(
        disposition, text(value, "reason"), requested, List.copyOf(processes));
  }

  private List<String> parseCandidateSourceRequests(
      ObjectNode value, Candidate candidate, FrozenCorpus corpus, ProcessDiscoveryProfile profile) {
    List<String> requested = strings(value, "requestedSourceRefs");
    if (requested.size() > profile.maxRequestedSourceRefs()
        || requested.stream().distinct().count() != requested.size()) {
      throw failure("PROCESS_SOURCE_REQUEST_INVALID");
    }
    Set<String> allowedSources = candidateSourceRefs(candidate, corpus);
    requireSubset(requested, allowedSources, "PROCESS_SOURCE_REQUEST_OUT_OF_SCOPE");
    return requested;
  }

  private RepositoryBusinessProcessCatalog.BusinessProcess process(
      ObjectNode value, Candidate candidate, FrozenCorpus corpus) {
    String processId = "business-process:" + sha256(canonicalJson.encodeCanonical(value));
    Set<String> candidateIds =
        candidate.uses().stream().map(CandidateUse::activityId).collect(Collectors.toSet());
    Set<String> allowedStatements =
        candidateIds.stream()
            .flatMap(activityId -> corpus.statementHandles(activityId).stream())
            .collect(Collectors.toSet());
    Set<String> allowedSources = candidateSourceRefs(candidate, corpus);
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
      Set<String> activityStatements = new HashSet<>(corpus.statementHandles(activityId));
      statementRefs =
          statementRefs.stream().filter(activityStatements::contains).distinct().toList();
      List<String> sourceRefs = strings(use, "sourceRefs").stream().distinct().toList();
      requireSubset(sourceRefs, allowedSources, "PROCESS_SOURCE_REFERENCE_INVALID");
      Set<String> activitySources = new HashSet<>(corpus.activitySourceRefs(activityId));
      sourceRefs = sourceRefs.stream().filter(activitySources::contains).distinct().toList();
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

  private ObjectNode processInput(Candidate candidate, FrozenCorpus corpus) {
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.set("candidate", candidate.toJson());
    ArrayNode activities = input.putArray("activities");
    candidate.uses().stream()
        .map(CandidateUse::activityId)
        .distinct()
        .sorted(UTF8_ORDER)
        .map(corpus::activityJson)
        .forEach(activities::add);
    ArrayNode refs = input.putArray("allowlistedSourceRefs");
    candidateSourceRefs(candidate, corpus).stream()
        .sorted(UTF8_ORDER)
        .map(corpus::source)
        .forEach(
            source ->
                sourceDirectoryJson(
                    refs.addObject(),
                    source,
                    candidateActivityIds(candidate, corpus, source.ref())));
    input.put("instruction", "索引卡只用于选择成员；请从完整活动保留具体条件、状态值、拒绝路径、结果与不确定关系。");
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

  private ObjectNode processSchema(Candidate candidate, FrozenCorpus corpus) {
    ObjectNode root = objectSchema();
    ObjectNode properties = root.putObject("properties");
    properties.set(
        "disposition",
        enumSchema(
            CANDIDATE_DISPOSITIONS.stream()
                .filter(value -> !"NOT_PROCESSED_CAPACITY".equals(value))
                .sorted()
                .toList()));
    properties.set("reason", textSchema());
    properties.set("requestedSourceRefs", enumArraySchema(candidateSourceRefs(candidate, corpus)));
    properties.set("processes", arraySchema(detailedProcessSchema(candidate, corpus)));
    required(root, "disposition", "reason", "requestedSourceRefs", "processes");
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

  private ObjectNode detailedProcessSchema(Candidate candidate, FrozenCorpus corpus) {
    ObjectNode process = objectSchema();
    ObjectNode properties = process.putObject("properties");
    properties.set("processLocalId", textSchema());
    properties.set("name", textSchema());
    properties.set("purpose", textSchema());
    properties.set("scope", textSchema());
    properties.set("participants", stringsSchema());
    properties.set("businessObjects", stringsSchema());
    properties.set("activityUses", arraySchema(activityUseSchema(candidate, corpus)));
    properties.set("stages", arraySchema(stageSchema(candidate, corpus)));
    properties.set("branches", stringsSchema());
    properties.set("businessRules", arraySchema(ruleSchema(candidate, corpus)));
    properties.set("endResults", stringsSchema());
    properties.set("supportActivityUseLocalIds", stringsSchema());
    properties.set("knowledgeItems", arraySchema(knowledgeSchema(candidate, corpus)));
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

  private ObjectNode activityUseSchema(Candidate candidate, FrozenCorpus corpus) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("useLocalId", textSchema());
    properties.set(
        "activityId", enumSchema(candidate.uses().stream().map(CandidateUse::activityId).toList()));
    properties.set("role", enumSchema(CANDIDATE_ROLES.stream().sorted().toList()));
    properties.set("variant", textSchema());
    properties.set("statementRefs", enumArraySchema(candidateStatementRefs(candidate, corpus)));
    properties.set("sourceRefs", enumArraySchema(candidateSourceRefs(candidate, corpus)));
    required(schema, "useLocalId", "activityId", "role", "variant", "statementRefs", "sourceRefs");
    return schema;
  }

  private ObjectNode stageSchema(Candidate candidate, FrozenCorpus corpus) {
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
    properties.set("statementRefs", enumArraySchema(candidateStatementRefs(candidate, corpus)));
    properties.set("sourceRefs", enumArraySchema(candidateSourceRefs(candidate, corpus)));
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

  private ObjectNode ruleSchema(Candidate candidate, FrozenCorpus corpus) {
    ObjectNode schema = objectSchema();
    ObjectNode properties = schema.putObject("properties");
    properties.set("subject", textSchema());
    properties.set("when", textSchema());
    properties.set("actionOrDecision", textSchema());
    ObjectNode otherwise = textSchema();
    ArrayNode types = otherwise.putArray("type");
    types.removeAll();
    types.add("string").add("null");
    properties.set("otherwise", otherwise);
    properties.set("result", textSchema());
    properties.set("certainty", enumSchema(CERTAINTIES.stream().sorted().toList()));
    ObjectNode activityUseLocalIds = stringsSchema();
    activityUseLocalIds.put("minItems", 1);
    properties.set("activityUseLocalIds", activityUseLocalIds);
    properties.set("statementRefs", enumArraySchema(candidateStatementRefs(candidate, corpus)));
    properties.set("sourceRefs", enumArraySchema(candidateSourceRefs(candidate, corpus)));
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

  private ObjectNode knowledgeSchema(Candidate candidate, FrozenCorpus corpus) {
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
    properties.set("statementRefs", enumArraySchema(candidateStatementRefs(candidate, corpus)));
    properties.set("sourceRefs", enumArraySchema(candidateSourceRefs(candidate, corpus)));
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

  private static void required(ObjectNode schema, String... fields) {
    ArrayNode required = schema.putArray("required");
    for (String field : fields) {
      required.add(field);
    }
  }

  private static List<String> cardIds(List<ActivityIndexCard> cards) {
    return cards.stream().map(ActivityIndexCard::activityId).toList();
  }

  private static Set<String> candidateSourceRefs(Candidate candidate, FrozenCorpus corpus) {
    return candidate.uses().stream()
        .flatMap(use -> corpus.activitySourceRefs(use.activityId()).stream())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static Set<String> candidateStatementRefs(Candidate candidate, FrozenCorpus corpus) {
    return candidate.uses().stream()
        .flatMap(use -> corpus.statementHandles(use.activityId()).stream())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static List<String> candidateActivityIds(
      Candidate candidate, FrozenCorpus corpus, String sourceRef) {
    return candidate.uses().stream()
        .map(CandidateUse::activityId)
        .filter(activityId -> corpus.activitySourceRefs(activityId).contains(sourceRef))
        .distinct()
        .sorted(UTF8_ORDER)
        .toList();
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

  private static int positiveInt(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.canConvertToInt() || node.intValue() < 1) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return node.intValue();
  }

  private static List<String> strings(ObjectNode value, String field) {
    ArrayNode array = array(value, field);
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

  private record IndexedCandidateSelection(int ordinal, Candidate candidate) {}

  static final class CatalogSample {
    private final ProcessDiscoveryRequest request;
    private final FrozenCorpus corpus;
    private final CatalogResult catalog;

    private CatalogSample(
        ProcessDiscoveryRequest request, FrozenCorpus corpus, CatalogResult catalog) {
      this.request = request;
      this.corpus = corpus;
      this.catalog = catalog;
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
      String candidateId, String localId, String name, String purpose, List<CandidateUse> uses) {
    ObjectNode toJson() {
      ObjectNode value = JsonNodeFactory.instance.objectNode();
      value.put("candidateId", candidateId);
      value.put("name", name);
      value.put("purpose", purpose);
      ArrayNode values = value.putArray("activityUses");
      uses.forEach(use -> values.add(use.toJson()));
      return value;
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
      List<String> requestedSourceRefs,
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
          SourceReference previous = sources.putIfAbsent(ref, source);
          if (previous != null && !previous.equals(source)) {
            throw failure("PROCESS_CORPUS_SOURCE_IDENTITY_COLLISION");
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
      ArrayNode handles = value.putArray("statementHandles");
      statementsByActivity.get(activityId).keySet().stream()
          .sorted(UTF8_ORDER)
          .forEach(handles::add);
      ArrayNode statementValues = value.putArray("statements");
      statementsByActivity.get(activityId).entrySet().stream()
          .sorted(Map.Entry.comparingByKey(UTF8_ORDER))
          .forEach(
              entry -> {
                ObjectNode statement = statementValues.addObject();
                statement.put("handle", entry.getKey());
                statement.put("text", entry.getValue());
              });
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

  private static void sourceDirectoryJson(
      ObjectNode value, SourceReference source, List<String> activityIds) {
    value.put("ref", source.ref());
    strings(value.putArray("activityIds"), activityIds);
    strings(value.putArray("openingLines"), openingLines(source.snippet()));
  }

  private static List<String> openingLines(String snippet) {
    List<String> lines = new ArrayList<>();
    int lineStart = 0;
    for (int index = 0; index < snippet.length() && lines.size() < 8; index++) {
      char character = snippet.charAt(index);
      if (character == '\n' || character == '\r') {
        lines.add(snippet.substring(lineStart, index));
        if (character == '\r'
            && index + 1 < snippet.length()
            && snippet.charAt(index + 1) == '\n') {
          index++;
        }
        lineStart = index + 1;
      }
    }
    if (lines.size() < 8 && lineStart < snippet.length()) {
      lines.add(snippet.substring(lineStart));
    }
    return List.copyOf(lines);
  }

  private static void sourceJson(ObjectNode value, SourceReference source) {
    value.put("ref", source.ref());
    value.put("file", source.file());
    value.put("startLine", source.startLine());
    value.put("endLine", source.endLine());
    value.put("snippet", source.snippet());
  }
}
