package org.sourceanalysis.app.analysis.interpretation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Executes persisted R1/R2 tasks once each and admits only same-Flow frozen keys. */
public final class InterpretationRunner {

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the M5 runner over receipt-last module publications. */
  public InterpretationRunner(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  /** Runs R1 then R2 for every R0-ready Flow; a started-call failure never retries. */
  public InterpretationExecutionSet runInterpretations(
      ModulePublicationReference r0ExecutionPublication,
      ModulePublicationReference registryPublication,
      ModulePublicationReference flowTaskSetPublication,
      FlowModelProvider provider) {
    try {
      Objects.requireNonNull(provider, "flow model provider");
      Map<String, R0Disposition> r0 = reopenR0(r0ExecutionPublication);
      Registry registry = reopenRegistry(registryPublication);
      ReopenedTasks reopenedTasks = reopenTasks(flowTaskSetPublication, registry);
      List<FlowModelTask> tasks = reopenedTasks.tasks();
      Map<String, FlowModelTask> r1 = new HashMap<>();
      Map<String, FlowModelTask> r2 = new HashMap<>();
      for (FlowModelTask task : tasks) {
        Map<String, FlowModelTask> target = "R1".equals(task.round()) ? r1 : r2;
        if (target.put(task.flowSliceId(), task) != null)
          throw failure("INTERPRETATION_COVERAGE_BROKEN");
      }
      List<ModelRound> rounds = new ArrayList<>();
      List<GenerationReceipt> receipts = new ArrayList<>();
      List<FlowInterpretationCandidate> candidates = new ArrayList<>();
      List<ModelTaskDisposition> taskDispositions = new ArrayList<>();
      List<FlowInterpretationDisposition> finalDispositions = new ArrayList<>();
      for (String flowId : r0.keySet().stream().sorted().toList()) {
        R0Disposition r0Disposition = r0.get(flowId);
        if (!"READY_FOR_FREEZE".equals(r0Disposition.disposition())) {
          finalDispositions.add(
              finalDisposition(
                  flowId,
                  r0Disposition.disposition().equals("GAP") ? "GAP" : "FAILED",
                  null,
                  null,
                  null,
                  r0Disposition.gapIds(),
                  r0Disposition.reasonCode()));
          continue;
        }
        FlowModelTask r1Task = r1.get(flowId);
        FlowModelTask r2Task = r2.get(flowId);
        if (r1Task == null || r2Task == null || !registry.readyFlows().contains(flowId)) {
          throw failure("INTERPRETATION_COVERAGE_BROKEN");
        }
        CallResult r1Result = call(provider, r1Task);
        rounds.add(r1Result.round());
        receipts.add(r1Result.receipt());
        R1Response r1Response = parseR1(r1Task, r1Result.response(), registry);
        ModelTaskDisposition r1Disposition =
            new ModelTaskDisposition(
                r1Task.taskSpecId(),
                flowId,
                "R1",
                r1Response.state(),
                r1Result.round().modelRoundId(),
                r1Result.receipt().generationReceiptId(),
                null,
                r1Response.gapIds(),
                r1Response.reasonCode());
        taskDispositions.add(r1Disposition);
        if (!"RESPONSE_ACCEPTED".equals(r1Response.state())) {
          ModelTaskDisposition r2NotRun =
              new ModelTaskDisposition(
                  r2Task.taskSpecId(),
                  flowId,
                  "R2",
                  "NOT_RUN_UPSTREAM_FAILED",
                  null,
                  null,
                  r1Task.taskSpecId(),
                  r1Response.gapIds(),
                  r1Response.reasonCode());
          taskDispositions.add(r2NotRun);
          finalDispositions.add(
              finalDisposition(
                  flowId,
                  r1Response.state().equals("RESPONSE_GAP") ? "GAP" : "FAILED",
                  r1Disposition,
                  r2NotRun,
                  null,
                  r1Response.gapIds(),
                  r1Response.reasonCode()));
          continue;
        }
        CallResult r2Result = call(provider, r2Task);
        rounds.add(r2Result.round());
        receipts.add(r2Result.receipt());
        List<ReviewedSelection> reviews =
            parseR2(r2Task, r2Result.response(), r1Response.selections(), registry);
        ModelTaskDisposition r2Disposition =
            new ModelTaskDisposition(
                r2Task.taskSpecId(),
                flowId,
                "R2",
                "RESPONSE_ACCEPTED",
                r2Result.round().modelRoundId(),
                r2Result.receipt().generationReceiptId(),
                null,
                List.of(),
                null);
        taskDispositions.add(r2Disposition);
        List<InterpretationProposal> flowProposals = new ArrayList<>();
        for (ReviewedSelection review : reviews) {
          String registryProposalId = registry.proposalIdByKey().get(review.key());
          InterpretationProposal provisional =
              new InterpretationProposal(
                  "interpretation-proposal:placeholder",
                  registryProposalId,
                  flowId,
                  review.key(),
                  review.key(),
                  review.atomIds(),
                  review.gapIds(),
                  review.decision());
          flowProposals.add(
              new InterpretationProposal(
                  FlowInterpretationIdentity.proposalId(provisional),
                  registryProposalId,
                  flowId,
                  review.key(),
                  review.key(),
                  review.atomIds(),
                  review.gapIds(),
                  review.decision()));
        }
        flowProposals.sort(Comparator.comparing(InterpretationProposal::interpretationProposalId));
        String candidateId =
            FlowInterpretationIdentity.candidateId(
                flowId,
                r1Task.evidenceCapsuleId(),
                r1Result.round().modelRoundId(),
                r2Result.round().modelRoundId(),
                flowProposals);
        candidates.add(
            new FlowInterpretationCandidate(
                candidateId,
                flowId,
                r1Task.evidenceCapsuleId(),
                r1Result.round().modelRoundId(),
                r2Result.round().modelRoundId(),
                flowProposals));
        finalDispositions.add(
            finalDisposition(
                flowId,
                "READY_FOR_ADMISSION",
                r1Disposition,
                r2Disposition,
                candidateId,
                List.of(),
                null));
      }
      if (!r1.keySet().equals(registry.readyFlows()) || !r2.keySet().equals(registry.readyFlows()))
        throw failure("INTERPRETATION_COVERAGE_BROKEN");
      rounds.sort(Comparator.comparing(ModelRound::taskSpecId));
      receipts.sort(Comparator.comparing(GenerationReceipt::taskSpecId));
      candidates.sort(Comparator.comparing(FlowInterpretationCandidate::flowSliceId));
      taskDispositions.sort(Comparator.comparing(ModelTaskDisposition::taskSpecId));
      finalDispositions.sort(Comparator.comparing(FlowInterpretationDisposition::flowSliceId));
      return new InterpretationExecutionSet(
          FlowInterpretationIdentity.executionSetId(
              reopenedTasks.flowTaskSetId(),
              rounds,
              receipts,
              candidates,
              taskDispositions,
              finalDispositions),
          reopenedTasks.flowTaskSetId(),
          r0ExecutionPublication,
          registryPublication,
          flowTaskSetPublication,
          rounds,
          receipts,
          candidates,
          taskDispositions,
          finalDispositions);
    } catch (FlowModelTaskException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("MODEL_RESPONSE_INVALID", failure);
    }
  }

  private CallResult call(FlowModelProvider provider, FlowModelTask task) {
    FlowModelProviderResponse response;
    try {
      response = Objects.requireNonNull(provider.respond(task), "provider response");
    } catch (RuntimeException failure) {
      throw failure("PROVIDER_FAILURE_AFTER_START", failure);
    }
    if (!task.expectedRuntime().equals(response.observedRuntime()))
      throw failure("MODEL_RUNTIME_IDENTITY_MISMATCH");
    Sha256Digest responseSha =
        new Sha256Digest(sha256(response.canonicalResponseJson().copyToByteArray()));
    String roundId =
        contentId("model-round", List.of(task.taskSpecId(), task.round(), responseSha.value()));
    ModelRound round = new ModelRound(roundId, task.taskSpecId(), task.round(), responseSha);
    GenerationReceipt receipt = GenerationReceipt.completed(task, responseSha);
    return new CallResult(round, receipt, response.canonicalResponseJson());
  }

  private R1Response parseR1(FlowModelTask task, ImmutableBytes responseBytes, Registry registry) {
    JsonNode response = canonicalJson.parseCanonical(responseBytes);
    if (!"flow-interpretation-model-response-v1".equals(text(response, "schemaVersion")))
      throw failure("MODEL_RESPONSE_INVALID");
    String kind = text(response, "kind");
    if ("R1_INTERPRETATION_GAP".equals(kind))
      return new R1Response(
          "RESPONSE_GAP", List.of(), identifiers(response, "gapIds"), text(response, "reasonCode"));
    if ("R1_INTERPRETATION_FAILED".equals(kind))
      return new R1Response("RESPONSE_FAILED", List.of(), List.of(), text(response, "reasonCode"));
    if (!"R1_INTERPRETATION_RESPONSE".equals(kind)) throw failure("MODEL_RESPONSE_INVALID");
    List<Selection> values = selections(array(response, "selections"), task, registry, null);
    return new R1Response("RESPONSE_ACCEPTED", values, List.of(), null);
  }

  private List<ReviewedSelection> parseR2(
      FlowModelTask task, ImmutableBytes responseBytes, List<Selection> r1, Registry registry) {
    JsonNode response = canonicalJson.parseCanonical(responseBytes);
    if (!"flow-interpretation-model-response-v1".equals(text(response, "schemaVersion"))
        || !"R2_PRECISION_REVIEW_RESPONSE".equals(text(response, "kind")))
      throw failure("MODEL_RESPONSE_INVALID");
    Map<String, Selection> r1ByKey = new HashMap<>();
    for (Selection selection : r1) {
      if (r1ByKey.put(selection.key(), selection) != null) {
        throw failure("MODEL_RESPONSE_INVALID");
      }
    }
    List<ReviewedSelection> values = new ArrayList<>();
    for (JsonNode review : array(response, "reviews")) {
      String key = text(review, "selectedKey");
      String decision = text(review, "r2Decision");
      if (!List.of("KEEP", "NARROW", "DROP", "NEEDS_EVIDENCE").contains(decision)) {
        throw failure("MODEL_REVIEW_EXPANDED");
      }
      Selection r1Selection = r1ByKey.remove(key);
      if (r1Selection == null) throw failure("MODEL_REVIEW_EXPANDED");
      List<String> atoms = identifiers(review, "basisAtomIds");
      List<String> gaps = identifiers(review, "basisGapIds");
      validateBasis(task, key, atoms, gaps, registry);
      if (!r1Selection.atomIds().containsAll(atoms) || !r1Selection.gapIds().containsAll(gaps)) {
        throw failure("MODEL_REVIEW_EXPANDED");
      }
      values.add(new ReviewedSelection(key, decision, atoms, gaps));
    }
    if (!r1ByKey.isEmpty()) throw failure("MODEL_REVIEW_NOT_CLOSED");
    values.sort(Comparator.comparing(ReviewedSelection::key));
    return values;
  }

  private List<Selection> selections(
      ArrayNode entries, FlowModelTask task, Registry registry, Object ignored) {
    if (entries.isEmpty()) throw failure("MODEL_RESPONSE_INVALID");
    List<Selection> result = new ArrayList<>();
    Set<String> keys = new HashSet<>();
    for (JsonNode selection : entries) {
      String key = text(selection, "selectedKey");
      List<String> atoms = identifiers(selection, "basisAtomIds");
      List<String> gaps = identifiers(selection, "basisGapIds");
      if (!keys.add(key)) throw failure("MODEL_REFERENCE_INVALID");
      validateBasis(task, key, atoms, gaps, registry);
      result.add(new Selection(key, atoms, gaps));
    }
    result.sort(Comparator.comparing(Selection::key));
    return result;
  }

  private static void validateBasis(
      FlowModelTask task, String key, List<String> atoms, List<String> gaps, Registry registry) {
    RegistryItem item = registry.itemsByKey().get(key);
    if (item == null
        || !task.allowedKeys().contains(key)
        || !task.flowSliceId().equals(item.flowSliceId())
        || !task.evidenceCapsuleId().equals(item.evidenceCapsuleId())
        || !item.basisAtomIds().containsAll(atoms)
        || !item.basisGapIds().containsAll(gaps)) {
      throw failure("MODEL_REFERENCE_INVALID");
    }
  }

  private Map<String, R0Disposition> reopenR0(ModulePublicationReference reference) {
    JsonNode body =
        body(
            reference,
            2,
            "registry-proposal-runner",
            "registry-proposal-execution-set.json",
            "FLOW_INTERPRETATION_REGISTRY_PROPOSAL_EXECUTION_SET",
            "flow-interpretation-registry-proposal-execution-set-v3");
    Map<String, R0Disposition> values = new HashMap<>();
    for (JsonNode value : array(body, "flowDispositions")) {
      String flow = identifier(value, "flowSliceId");
      if (values.put(
              flow,
              new R0Disposition(
                  text(value, "disposition"),
                  identifiers(value, "gapIds"),
                  nullable(value, "reasonCode")))
          != null) throw failure("INTERPRETATION_COVERAGE_BROKEN");
    }
    return Map.copyOf(values);
  }

  private Registry reopenRegistry(ModulePublicationReference reference) {
    JsonNode body =
        body(
            reference,
            3,
            "registry-freezer",
            "repository-interpretation-registry.json",
            "FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY_MODULE",
            "flow-interpretation-repository-interpretation-registry-module-v1");
    Map<String, String> proposal = new HashMap<>();
    Map<String, RegistryItem> items = new HashMap<>();
    Set<String> ready = new HashSet<>();
    for (JsonNode item : array(body, "items")) {
      String key = text(item, "provisionalKey");
      String proposalId = identifier(item, "registryProposalId");
      if (proposal.put(key, proposalId) != null) throw failure("MODEL_REFERENCE_INVALID");
      RegistryItem registryItem =
          new RegistryItem(
              proposalId,
              identifier(item, "flowSliceId"),
              identifier(item, "evidenceCapsuleId"),
              identifiers(item, "basisAtomIds"),
              identifiers(item, "basisGapIds"));
      if (registryItem.basisAtomIds().isEmpty() && registryItem.basisGapIds().isEmpty()) {
        throw failure("MODEL_REFERENCE_INVALID");
      }
      items.put(key, registryItem);
    }
    for (JsonNode disposition : array(body, "flowDispositions"))
      if ("READY_FOR_FREEZE".equals(text(disposition, "disposition")))
        ready.add(identifier(disposition, "flowSliceId"));
    return new Registry(proposal, Map.copyOf(items), Set.copyOf(ready));
  }

  private ReopenedTasks reopenTasks(ModulePublicationReference reference, Registry registry) {
    JsonNode body =
        body(
            reference,
            4,
            "flow-task-compiler",
            "flow-task-set.json",
            "FLOW_INTERPRETATION_FLOW_TASK_SET",
            "flow-interpretation-flow-task-set-v4");
    String flowTaskSetId = identifier(body, "flowTaskSetId");
    List<FlowModelTask> values = new ArrayList<>();
    for (JsonNode task : array(body, "tasks")) {
      ImmutableBytes input = canonicalJson.encodeCanonical(task.get("inputJson"));
      Sha256Digest digest = new Sha256Digest(text(task, "inputJsonSha256"));
      if (!digest.equals(new Sha256Digest(sha256(input.copyToByteArray()))))
        throw failure("MODEL_TASK_HASH_MISMATCH");
      FlowModelTask materialized =
          new FlowModelTask(
              identifier(task, "taskSpecId"),
              text(task, "taskKind"),
              text(task, "round"),
              identifier(task, "flowSliceId"),
              identifier(task, "evidenceCapsuleId"),
              identifier(task, "isolatedSessionKey"),
              strings(task, "allowedKeys"),
              input,
              digest,
              new Sha256Digest(text(task, "outputSchemaSha256")),
              new Sha256Digest(text(task, "promptBundleSha256")),
              text(task, "configuredAdapterId"),
              text(task, "configuredAuthMode"),
              reference(task.get("expectedRuntimeRef")),
              runtime(task.get("expectedRuntime")));
      if (!materialized.taskSpecId().equals(FlowInterpretationIdentity.taskId(materialized))) {
        throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
      }
      validateTaskRegistryInput(materialized, task.get("inputJson"), registry);
      values.add(materialized);
    }
    values.sort(
        Comparator.comparing(FlowModelTask::flowSliceId).thenComparing(FlowModelTask::round));
    return new ReopenedTasks(flowTaskSetId, List.copyOf(values));
  }

  private static void validateTaskRegistryInput(
      FlowModelTask task, JsonNode input, Registry registry) {
    if (!input.isObject()
        || !task.flowSliceId().equals(identifier(input, "flowSliceId"))
        || !task.evidenceCapsuleId().equals(identifier(input, "evidenceCapsuleId"))
        || !("R1".equals(task.round()) && "R1_INTERPRETATION_INPUT".equals(text(input, "kind")))
            && !("R2".equals(task.round()) && "R2_REVIEW_INPUT".equals(text(input, "kind")))) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    Map<String, RegistryItem> allowed = new HashMap<>();
    for (JsonNode item : array(input, "allowedRegistryItems")) {
      String key = text(item, "provisionalKey");
      RegistryItem registryItem = registry.itemsByKey().get(key);
      RegistryItem taskItem =
          new RegistryItem(
              identifier(item, "registryProposalId"),
              identifier(item, "flowSliceId"),
              identifier(item, "evidenceCapsuleId"),
              identifiers(item, "basisAtomIds"),
              identifiers(item, "basisGapIds"));
      if (registryItem == null
          || !registryItem.equals(taskItem)
          || !task.flowSliceId().equals(taskItem.flowSliceId())
          || !task.evidenceCapsuleId().equals(taskItem.evidenceCapsuleId())
          || allowed.put(key, taskItem) != null) {
        throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
      }
    }
    if (!task.allowedKeys().equals(allowed.keySet().stream().sorted().toList())) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
  }

  private JsonNode body(
      ModulePublicationReference reference,
      int number,
      String key,
      String file,
      String type,
      String schema) {
    ReopenedModulePublication publication = moduleArtifacts.reopen(reference);
    if (!(publication.receipt().address()
            instanceof org.sourceanalysis.app.artifact.AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != number
        || !key.equals(address.moduleKey())
        || publication.payloads().size() != 1) throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!file.equals(payload.descriptor().fileName())
        || !type.equals(payload.descriptor().artifactType())
        || !schema.equals(payload.descriptor().schemaVersion()))
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    return canonicalJson.parseCanonical(payload.canonicalUtf8()).path("payload");
  }

  private static FlowInterpretationDisposition finalDisposition(
      String flow,
      String disposition,
      ModelTaskDisposition r1,
      ModelTaskDisposition r2,
      String candidate,
      List<String> gaps,
      String reason) {
    return new FlowInterpretationDisposition(
        contentId(
            "flow-interpretation-disposition",
            List.of(
                flow,
                disposition,
                r1 == null ? "" : r1.taskSpecId(),
                r2 == null ? "" : r2.taskSpecId(),
                candidate == null ? "" : candidate,
                String.join("|", gaps),
                reason == null ? "" : reason)),
        flow,
        disposition,
        r1,
        r2,
        candidate,
        List.copyOf(gaps),
        reason);
  }

  private static ArrayNode array(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (!(value instanceof ArrayNode array)) throw failure("MODEL_RESPONSE_INVALID");
    return array;
  }

  private static String text(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank())
      throw failure("MODEL_RESPONSE_INVALID");
    return value.textValue();
  }

  private static String nullable(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || value.isNull()) return null;
    return text(source, field);
  }

  private static String identifier(JsonNode source, String field) {
    try {
      return ArtifactId.parse(text(source, field)).value();
    } catch (RuntimeException invalid) {
      throw failure("MODEL_RESPONSE_INVALID");
    }
  }

  private static List<String> identifiers(JsonNode source, String field) {
    List<String> values = new ArrayList<>();
    for (JsonNode value : array(source, field))
      values.add(ArtifactId.parse(textValue(value)).value());
    values.sort(String::compareTo);
    if (values.size() != new HashSet<>(values).size()) throw failure("MODEL_RESPONSE_INVALID");
    return List.copyOf(values);
  }

  private static List<String> strings(JsonNode source, String field) {
    List<String> values = new ArrayList<>();
    for (JsonNode value : array(source, field)) values.add(textValue(value));
    values.sort(String::compareTo);
    if (values.size() != new HashSet<>(values).size()) throw failure("MODEL_RESPONSE_INVALID");
    return List.copyOf(values);
  }

  private static String textValue(JsonNode value) {
    if (!value.isTextual() || value.textValue().isBlank()) throw failure("MODEL_RESPONSE_INVALID");
    return value.textValue();
  }

  private static ArtifactReference reference(JsonNode value) {
    return new ArtifactReference(
        ArtifactId.parse(text(value, "artifactId")), new Sha256Digest(text(value, "sha256")));
  }

  private static org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1 runtime(
      JsonNode value) {
    return new org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1(
        text(value, "upstreamProvider"),
        text(value, "model"),
        text(value, "reasoningEffort"),
        text(value, "sandbox"));
  }

  private static String contentId(String prefix, List<String> values) {
    byte[][] frames = new byte[values.size() + 1][];
    frames[0] = frame(prefix);
    for (int i = 0; i < values.size(); i++) frames[i + 1] = frame(values.get(i));
    return prefix + ":" + sha256(frames);
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) digest.update(value);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }

  private static FlowModelTaskException failure(String code) {
    return new FlowModelTaskException(code);
  }

  private static FlowModelTaskException failure(String code, Throwable cause) {
    return new FlowModelTaskException(code, cause);
  }

  private record CallResult(ModelRound round, GenerationReceipt receipt, ImmutableBytes response) {}

  private record Selection(String key, List<String> atomIds, List<String> gapIds) {}

  private record ReviewedSelection(
      String key, String decision, List<String> atomIds, List<String> gapIds) {}

  private record R1Response(
      String state, List<Selection> selections, List<String> gapIds, String reasonCode) {}

  private record R0Disposition(String disposition, List<String> gapIds, String reasonCode) {}

  private record ReopenedTasks(String flowTaskSetId, List<FlowModelTask> tasks) {}

  private record Registry(
      Map<String, String> proposalIdByKey,
      Map<String, RegistryItem> itemsByKey,
      Set<String> readyFlows) {}

  private record RegistryItem(
      String registryProposalId,
      String flowSliceId,
      String evidenceCapsuleId,
      List<String> basisAtomIds,
      List<String> basisGapIds) {}
}
