package org.sourceanalysis.app.analysis.interpretation.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
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

/** Executes persisted R0 tasks once each and accepts only same-Capsule proposal basis. */
public final class RegistryProposalRunner {

  private static final String FILE_NAME = "registry-proposal-task-set.json";
  private static final String ARTIFACT_TYPE = "FLOW_INTERPRETATION_REGISTRY_PROPOSAL_TASK_SET";
  private static final String SCHEMA_VERSION = "flow-interpretation-registry-proposal-task-set-v2";
  private static final Comparator<String> UTF8_ORDER = RegistryProposalRunner::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the R0 runner over receipt-last M1 module storage. */
  public RegistryProposalRunner(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  /** Starts one provider call per task in the fresh-reopened M1 task set. */
  public RegistryProposalExecutionSet runRegistryProposals(
      ModulePublicationReference persistedTaskSet, RegistryProposalProvider provider) {
    try {
      Objects.requireNonNull(persistedTaskSet, "persisted registry proposal task set");
      Objects.requireNonNull(provider, "registry proposal provider");
      List<RegistryProposalTask> tasks = reopenTasks(persistedTaskSet);
      List<RegistryProposalRound> rounds = new ArrayList<>();
      List<RegistryProposalGenerationReceipt> receipts = new ArrayList<>();
      List<BusinessRegistryProposal> proposals = new ArrayList<>();
      List<RegistryProposalFlowDisposition> dispositions = new ArrayList<>();
      for (RegistryProposalTask task : tasks) {
        RegistryProposalProviderResponse response;
        try {
          response = Objects.requireNonNull(provider.propose(task), "provider response");
        } catch (RuntimeException providerFailure) {
          throw new RegistryProposalTaskCompilationException(
              "PROVIDER_FAILURE_AFTER_START", providerFailure);
        }
        if (!task.expectedRuntime().equals(response.observedRuntime())) {
          throw failure("MODEL_RUNTIME_IDENTITY_MISMATCH");
        }
        ValidatedTaskResponse taskResponse = validate(task, response.canonicalResponseJson());
        List<BusinessRegistryProposal> taskProposals = taskResponse.proposals();
        Sha256Digest responseSha =
            new Sha256Digest(sha256(response.canonicalResponseJson().copyToByteArray()));
        String roundId =
            contentId("registry-proposal-round", List.of(task.taskSpecId(), responseSha.value()));
        RegistryProposalGenerationReceipt receipt =
            RegistryProposalGenerationReceipt.completedR0(
                task.taskSpecId(),
                task.flowSliceId(),
                task.inputJsonSha256(),
                responseSha,
                task.configuredAdapterId(),
                task.configuredAuthMode(),
                task.expectedRuntime(),
                response.observedRuntime());
        rounds.add(new RegistryProposalRound(roundId, task.taskSpecId(), responseSha));
        receipts.add(receipt);
        proposals.addAll(taskProposals);
        dispositions.add(
            new RegistryProposalFlowDisposition(
                contentId(
                    "registry-proposal-disposition",
                    List.of(
                        task.flowSliceId(),
                        task.taskSpecId(),
                        roundId,
                        receipt.generationReceiptId(),
                        String.join("|", taskResponse.gapIds()),
                        taskResponse.reasonCode() == null ? "" : taskResponse.reasonCode())),
                task.flowSliceId(),
                taskResponse.disposition(),
                task.taskSpecId(),
                roundId,
                receipt.generationReceiptId(),
                taskProposals.stream().map(BusinessRegistryProposal::registryProposalId).toList(),
                taskResponse.gapIds(),
                taskResponse.reasonCode()));
      }
      return new RegistryProposalExecutionSet(
          contentId(
              "registry-proposal-execution-set",
              List.of(
                  persistedTaskSet.moduleArtifactRoot().value(),
                  rounds.stream()
                      .map(RegistryProposalRound::registryProposalRoundId)
                      .collect(java.util.stream.Collectors.joining("|")))),
          persistedTaskSet,
          rounds,
          receipts,
          proposals,
          dispositions);
    } catch (RegistryProposalTaskCompilationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new RegistryProposalTaskCompilationException(
          "REGISTRY_PROPOSAL_RESPONSE_INVALID", failure);
    }
  }

  private List<RegistryProposalTask> reopenTasks(ModulePublicationReference reference) {
    ReopenedModulePublication publication = moduleArtifacts.reopen(reference);
    if (!reference.equals(publication.reference())
        || !(publication.receipt().address()
            instanceof org.sourceanalysis.app.artifact.AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 1
        || !"registry-task-compiler".equals(address.moduleKey())
        || publication.payloads().size() != 1) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!FILE_NAME.equals(payload.descriptor().fileName())
        || !ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
        || !SCHEMA_VERSION.equals(payload.descriptor().schemaVersion())) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    JsonNode document = canonicalJson.parseCanonical(payload.canonicalUtf8());
    JsonNode taskNodes = document.path("payload").path("tasks");
    if (!taskNodes.isArray()) throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    List<RegistryProposalTask> tasks = new ArrayList<>();
    for (JsonNode node : taskNodes) tasks.add(task(node));
    tasks.sort(Comparator.comparing(RegistryProposalTask::flowSliceId, UTF8_ORDER));
    if (tasks.size() != tasks.stream().map(RegistryProposalTask::flowSliceId).distinct().count()) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    return List.copyOf(tasks);
  }

  private RegistryProposalTask task(JsonNode node) {
    ImmutableBytes input = canonicalJson.encodeCanonical(field(node, "inputJson"));
    Sha256Digest inputSha = digest(node, "inputJsonSha256");
    if (!inputSha.equals(new Sha256Digest(sha256(input.copyToByteArray())))) {
      throw failure("MODEL_TASK_HASH_MISMATCH");
    }
    return new RegistryProposalTask(
        identifier(node, "taskSpecId"),
        text(node, "taskKind"),
        identifier(node, "flowSliceId"),
        identifier(node, "evidenceCapsuleId"),
        identifier(node, "isolatedSessionKey"),
        input,
        inputSha,
        digest(node, "outputSchemaSha256"),
        digest(node, "promptBundleSha256"),
        text(node, "configuredAdapterId"),
        text(node, "configuredAuthMode"),
        reference(field(node, "expectedRuntimeRef")),
        runtime(field(node, "expectedRuntime")));
  }

  private ValidatedTaskResponse validate(RegistryProposalTask task, ImmutableBytes responseBytes) {
    JsonNode response = canonicalJson.parseCanonical(responseBytes);
    if (!"flow-interpretation-registry-proposal-response-v1"
        .equals(text(response, "schemaVersion"))) {
      throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
    }
    String responseKind = text(response, "kind");
    if ("R0_REGISTRY_PROPOSAL_FAILED".equals(responseKind)) {
      requireExactFields(response, Set.of("schemaVersion", "kind", "reasonCode"));
      String reasonCode = text(response, "reasonCode");
      if (!List.of("MODEL_CANNOT_COMPLETE", "RESPONSE_POLICY_REJECTED").contains(reasonCode)) {
        throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
      }
      return new ValidatedTaskResponse("FAILED", List.of(), List.of(), reasonCode);
    }
    JsonNode input = canonicalJson.parseCanonical(task.inputJson());
    if (!task.flowSliceId().equals(identifier(input, "flowSliceId"))
        || !task.evidenceCapsuleId().equals(identifier(input, "evidenceCapsuleId"))) {
      throw failure("REGISTRY_PROPOSAL_REFERENCE_INVALID");
    }
    JsonNode capsule = field(input, "capsuleView");
    Set<String> allowedAtoms =
        new HashSet<>(identifierArray(capsule, "registryProposalBasisAtomIds"));
    Set<String> allowedGaps =
        new HashSet<>(identifierArray(capsule, "registryProposalBasisGapIds"));
    if ("R0_REGISTRY_PROPOSAL_GAP".equals(responseKind)) {
      requireExactFields(response, Set.of("schemaVersion", "kind", "gapIds", "reasonCode"));
      List<String> gapIds = identifierArray(response, "gapIds");
      String reasonCode = text(response, "reasonCode");
      if (gapIds.isEmpty()
          || !allowedGaps.containsAll(gapIds)
          || !List.of("INSUFFICIENT_EVIDENCE", "UNRESOLVED_BUSINESS_TERM").contains(reasonCode)) {
        throw failure("REGISTRY_PROPOSAL_REFERENCE_INVALID");
      }
      return new ValidatedTaskResponse("GAP", List.of(), gapIds, reasonCode);
    }
    if (!"R0_REGISTRY_PROPOSAL_RESPONSE".equals(responseKind)) {
      throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
    }
    requireExactFields(response, Set.of("schemaVersion", "kind", "proposals"));
    ArrayNode responseProposals = array(response, "proposals");
    if (responseProposals.isEmpty()) throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
    List<BusinessRegistryProposal> result = new ArrayList<>();
    for (JsonNode proposal : responseProposals) {
      Set<String> actualFields = new HashSet<>();
      proposal.fieldNames().forEachRemaining(actualFields::add);
      if (!actualFields.equals(
          Set.of(
              "proposalKind",
              "label",
              "purpose",
              "basisAtomIds",
              "basisGapIds",
              "sourceSeedKey"))) {
        throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
      }
      String proposalKind = text(proposal, "proposalKind");
      if (!List.of("BUSINESS_TERM", "CLAIM", "QUESTION").contains(proposalKind)) {
        throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
      }
      String label = text(proposal, "label");
      String purpose = text(proposal, "purpose");
      List<String> atomIds = identifierArray(proposal, "basisAtomIds");
      List<String> gapIds = identifierArray(proposal, "basisGapIds");
      if ((atomIds.isEmpty() && gapIds.isEmpty())
          || !allowedAtoms.containsAll(atomIds)
          || !allowedGaps.containsAll(gapIds)
          || proposal.get("sourceSeedKey") == null
          || (!proposal.get("sourceSeedKey").isNull()
              && !proposal.get("sourceSeedKey").isTextual())) {
        throw failure("REGISTRY_PROPOSAL_REFERENCE_INVALID");
      }
      String sourceSeedKey =
          proposal.get("sourceSeedKey").isNull() ? null : proposal.get("sourceSeedKey").textValue();
      result.add(
          new BusinessRegistryProposal(
              contentId(
                  "registry-proposal",
                  List.of(
                      task.taskSpecId(),
                      proposalKind,
                      label,
                      purpose,
                      String.join("|", atomIds),
                      String.join("|", gapIds))),
              task.taskSpecId(),
              task.flowSliceId(),
              task.evidenceCapsuleId(),
              proposalKind,
              label,
              purpose,
              atomIds,
              gapIds,
              sourceSeedKey));
    }
    return new ValidatedTaskResponse(
        "READY_FOR_FREEZE",
        result.stream()
            .sorted(Comparator.comparing(BusinessRegistryProposal::registryProposalId, UTF8_ORDER))
            .toList(),
        List.of(),
        null);
  }

  private static JsonNode field(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (value == null || value.isNull()) throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
    return value;
  }

  private static ArrayNode array(JsonNode source, String name) {
    JsonNode value = field(source, name);
    if (!(value instanceof ArrayNode array)) throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
    return array;
  }

  private static String identifier(JsonNode source, String name) {
    try {
      return ArtifactId.parse(text(source, name)).value();
    } catch (RuntimeException invalid) {
      throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
    }
  }

  private static List<String> identifierArray(JsonNode source, String name) {
    List<String> result = new ArrayList<>();
    for (JsonNode item : array(source, name)) {
      if (!item.isTextual()) throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
      result.add(ArtifactId.parse(item.textValue()).value());
    }
    result.sort(UTF8_ORDER);
    if (result.size() != new HashSet<>(result).size()) {
      throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
    }
    return List.copyOf(result);
  }

  private static Sha256Digest digest(JsonNode source, String name) {
    try {
      return new Sha256Digest(text(source, name));
    } catch (RuntimeException invalid) {
      throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
    }
  }

  private static ArtifactReference reference(JsonNode source) {
    return new ArtifactReference(
        ArtifactId.parse(text(source, "artifactId")), new Sha256Digest(text(source, "sha256")));
  }

  private static ModelRuntimeIdentityV1 runtime(JsonNode source) {
    Set<String> actual = new HashSet<>();
    source.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(Set.of("upstreamProvider", "model", "reasoningEffort", "sandbox"))) {
      throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
    }
    return new ModelRuntimeIdentityV1(
        text(source, "upstreamProvider"),
        text(source, "model"),
        text(source, "reasoningEffort"),
        text(source, "sandbox"));
  }

  private static String text(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
    }
    return value.textValue();
  }

  private static void requireExactFields(JsonNode source, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    source.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw failure("REGISTRY_PROPOSAL_RESPONSE_INVALID");
  }

  private static String contentId(String prefix, List<String> values) {
    byte[][] framed = new byte[values.size() + 1][];
    framed[0] = frame(prefix);
    for (int index = 0; index < values.size(); index++)
      framed[index + 1] = frame(values.get(index));
    return prefix + ":" + sha256(framed);
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

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) digest.update(value);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static int compareUtf8(String left, String right) {
    byte[] first = left.getBytes(StandardCharsets.UTF_8);
    byte[] second = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(first.length, second.length);
  }

  private static RegistryProposalTaskCompilationException failure(String code) {
    return new RegistryProposalTaskCompilationException(code);
  }

  private record ValidatedTaskResponse(
      String disposition,
      List<BusinessRegistryProposal> proposals,
      List<String> gapIds,
      String reasonCode) {}
}
