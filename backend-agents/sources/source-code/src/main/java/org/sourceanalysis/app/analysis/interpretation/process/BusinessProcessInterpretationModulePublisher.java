package org.sourceanalysis.app.analysis.interpretation.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Receipt-last M8 checkpoint publisher for one bounded P1/P2 process interpretation shard. */
public final class BusinessProcessInterpretationModulePublisher {

  private static final String FILE_NAME = "process-interpretation-checkpoint.json";
  private static final String ARTIFACT_TYPE =
      "FLOW_INTERPRETATION_PROCESS_INTERPRETATION_CHECKPOINT";
  private static final String SCHEMA_VERSION =
      "flow-interpretation-process-interpretation-checkpoint-v1";
  private static final String ARTIFACT_PREFIX = "process-interpretation-checkpoint";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final BusinessProcessInterpretationRunner runner;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the receipt-last publisher over a fresh-reopened M7 process task publication. */
  public BusinessProcessInterpretationModulePublisher(
      CanonicalModuleArtifactStore moduleArtifacts, ProcessInputArtifactReader inputArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.runner =
        new BusinessProcessInterpretationRunner(
            moduleArtifacts,
            Objects.requireNonNull(inputArtifacts, "process input artifact reader"));
  }

  /** Runs one bounded P1 dispatch and installs either its accepted P2 KEEP or terminal P1 Gap. */
  public ModulePublicationReference runAndPublish(
      BusinessProcessInterpretationRequest request, ProcessModelProvider provider) {
    try {
      Objects.requireNonNull(request, "process interpretation request");
      Objects.requireNonNull(provider, "process model provider");
      ReopenedModulePublication m7 = reopenM7(request.businessProcessTaskPublication());
      BusinessProcessInterpretationRunner.CheckpointTerminal terminal =
          runner.runCheckpointTerminal(request, provider);
      AnalysisStepModuleAddress m7Address = (AnalysisStepModuleAddress) m7.receipt().address();
      AnalysisStepModuleAddress m8Address =
          new AnalysisStepModuleAddress(
              m7Address.runId(),
              AnalysisStepKey.FLOW_INTERPRETATION,
              8,
              "business-process-interpretation-runner");
      ArtifactControls controls = m7.receipt().controls();
      ArtifactReference m7PayloadReference = reference(m7.payloads().get(0));
      CanonicalModulePayload payload;
      List<String> gapIds;
      if (terminal instanceof BusinessProcessInterpretationRunner.P1GapTerminal gap) {
        payload = gapPayload(m8Address, List.of(m7PayloadReference), controls, request, gap);
        gapIds = gap.upstreamGapIds();
      } else if (terminal
          instanceof BusinessProcessInterpretationRunner.P1SuccessTerminal success) {
        payload =
            successPayload(m8Address, List.of(m7PayloadReference), controls, request, success);
        gapIds = List.of();
      } else {
        throw new BusinessProcessInterpretationException("PROCESS_MODEL_REFERENCE_INVALID");
      }
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  m8Address,
                  "v1",
                  List.of(m7PayloadReference),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  gapIds,
                  List.of(payload)));
      verifyInstalled(installed.reference(), m8Address, request.taskShardId());
      return installed.reference();
    } catch (BusinessProcessInterpretationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new BusinessProcessInterpretationException("PROCESS_MODEL_REFERENCE_INVALID", failure);
    }
  }

  private ReopenedModulePublication reopenM7(ModulePublicationReference reference) {
    if (reference == null) {
      throw new BusinessProcessInterpretationException("PROCESS_MODEL_REFERENCE_INVALID");
    }
    ReopenedModulePublication publication = moduleArtifacts.reopen(reference);
    if (!reference.equals(publication.reference())
        || !(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 7
        || !"business-process-task-compiler".equals(address.moduleKey())
        || publication.payloads().size() != 1) {
      throw new BusinessProcessInterpretationException("PROCESS_MODEL_REFERENCE_INVALID");
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!"process-task-shards.json".equals(payload.descriptor().fileName())
        || !"FLOW_INTERPRETATION_PROCESS_TASK_SHARDS".equals(payload.descriptor().artifactType())
        || !"flow-interpretation-process-task-shards-v1"
            .equals(payload.descriptor().schemaVersion())) {
      throw new BusinessProcessInterpretationException("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return publication;
  }

  CanonicalModulePayload gapPayload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      BusinessProcessInterpretationRequest request,
      BusinessProcessInterpretationRunner.P1GapTerminal terminal) {
    String p1RequestSha = sha256(terminal.p1ApplicationRequest().copyToByteArray());
    String p2RequestSha = sha256(terminal.p2ApplicationRequest().copyToByteArray());
    String p1ResponseSha = sha256(terminal.p1Response().canonicalResponseJson().copyToByteArray());
    String p1TaskId = contentId("process-model-task", request.taskShardId(), "P1", p1RequestSha);
    String p2TaskId = contentId("process-model-task", request.taskShardId(), "P2", p2RequestSha);
    String receiptId =
        contentId(
            "generation-receipt",
            p1TaskId,
            p1RequestSha,
            p1ResponseSha,
            terminal.p1Response().observedRuntime().sha256().value());
    String roundId = contentId("process-model-round", p1TaskId, p1ResponseSha, receiptId);

    ObjectNode body = JsonNodeFactory.instance.objectNode();
    moduleReference(
        body.putObject("businessProcessTaskPublicationRef"),
        request.businessProcessTaskPublication());
    body.put("taskShardId", request.taskShardId());
    ArrayNode tasks = body.putArray("processModelTasks");
    task(
        tasks.addObject(),
        p1TaskId,
        "PROCESS_P1_HYPOTHESIS",
        request.taskShardId(),
        1,
        terminal.p1ApplicationRequest(),
        p1RequestSha);
    task(
        tasks.addObject(),
        p2TaskId,
        "PROCESS_P2_PRECISION_REVIEW",
        request.taskShardId(),
        2,
        terminal.p2ApplicationRequest(),
        p2RequestSha);

    ObjectNode round = body.putArray("processModelRounds").addObject();
    round.put("processModelRoundId", roundId);
    round.put("taskSpecId", p1TaskId);
    round.put("taskKind", "PROCESS_P1_HYPOTHESIS");
    round.put("taskShardId", request.taskShardId());
    round.put("roundOrdinal", 1);
    round.put("requestSha256", p1RequestSha);
    round.put("responseSha256", p1ResponseSha);
    round.put("responseKind", "P1_GAP");
    round.putArray("businessProcessHypothesisIds");
    round.putArray("processHypothesisReviews");
    strings(round.putArray("gapIds"), terminal.upstreamGapIds());
    round.putNull("failureCode");
    round.put("generationReceiptId", receiptId);

    ObjectNode receipt = body.putArray("generationReceipts").addObject();
    receipt.put("generationReceiptId", receiptId);
    receipt.put("generationKind", "PROCESS_P1_HYPOTHESIS");
    receipt.put("taskSpecId", p1TaskId);
    receipt.putNull("flowSliceId");
    receipt.put("taskShardId", request.taskShardId());
    receipt.put("requestSha256", p1RequestSha);
    receipt.put("responseSha256", p1ResponseSha);
    reference(receipt.putObject("expectedRuntime"), request.expectedRuntime());
    reference(receipt.putObject("observedRuntime"), terminal.p1Response().observedRuntime());
    receipt.put("started", true);
    receipt.put("completed", true);

    ArrayNode dispositions = body.putArray("taskDispositions");
    taskDisposition(
        dispositions.addObject(),
        p1TaskId,
        request.taskShardId(),
        "P1",
        "RESPONSE_GAP",
        roundId,
        receiptId,
        null,
        terminal.upstreamGapIds(),
        "PROCESS_P1_RESPONSE_GAP");
    taskDisposition(
        dispositions.addObject(),
        p2TaskId,
        request.taskShardId(),
        "P2",
        "NOT_RUN_UPSTREAM_FAILED",
        null,
        null,
        p1TaskId,
        terminal.upstreamGapIds(),
        "PROCESS_P1_RESPONSE_GAP");
    body.putArray("businessProcessHypotheses");

    ObjectNode disposition = body.putObject("processInterpretationDisposition");
    disposition.put("executionKind", "MODEL_TASKS");
    disposition.put("p1TaskId", p1TaskId);
    disposition.put("p2TaskId", p2TaskId);
    disposition.put("disposition", "GAP");
    disposition.putArray("proposedBusinessProcessHypothesisIds");
    disposition.putArray("retainedBusinessProcessHypothesisIds");
    disposition.putArray("narrowedBusinessProcessHypothesisIds");
    disposition.putArray("droppedBusinessProcessHypothesisIds");
    disposition.putArray("pendingBusinessProcessHypothesisIds");
    disposition.putArray("p2GapBusinessProcessHypothesisIds");
    disposition.putArray("p2FailedBusinessProcessHypothesisIds");
    disposition.putArray("processGaps");
    strings(disposition.putArray("gapIds"), terminal.upstreamGapIds());
    disposition.putNull("failureRef");
    disposition.put("reasonCode", "PROCESS_P1_RESPONSE_GAP");
    body.put("providerCallCount", 1);
    body.put("closed", true);

    ObjectNode envelope = JsonNodeFactory.instance.objectNode();
    envelope.put("schemaVersion", SCHEMA_VERSION);
    envelope.put("artifactType", ARTIFACT_TYPE);
    envelope.set("producer", producer(address));
    envelope.set("upstreamArtifacts", references(upstream));
    envelope.set("controls", controls(controls));
    envelope.set("completion", completion(terminal.upstreamGapIds()));
    envelope.set("payload", body);
    ArtifactId artifactId =
        ArtifactId.parse(
            ARTIFACT_PREFIX
                + ":"
                + sha256(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(envelope).copyToByteArray())));
    envelope.put("artifactId", artifactId.value());
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        artifactId,
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  /**
   * Projects the already-validated P1/P2 KEEP terminal into the bounded checkpoint wire.
   *
   * <p>The aggregate M8 publisher reuses this package-visible projector for its one model-safe
   * shard. It does not call this publisher's fixed-address install path.
   */
  CanonicalModulePayload successPayload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      BusinessProcessInterpretationRequest request,
      BusinessProcessInterpretationRunner.P1SuccessTerminal terminal) {
    String p1RequestSha = sha256(terminal.p1ApplicationRequest().copyToByteArray());
    String p2RequestSha = sha256(terminal.p2ApplicationRequest().copyToByteArray());
    String p1ResponseSha = sha256(terminal.p1Response().canonicalResponseJson().copyToByteArray());
    String p2ResponseSha = sha256(terminal.p2Response().canonicalResponseJson().copyToByteArray());
    String p1TaskId = contentId("process-model-task", request.taskShardId(), "P1", p1RequestSha);
    String p2TaskId = contentId("process-model-task", request.taskShardId(), "P2", p2RequestSha);
    String p1ReceiptId =
        contentId(
            "generation-receipt",
            p1TaskId,
            p1RequestSha,
            p1ResponseSha,
            terminal.p1Response().observedRuntime().sha256().value());
    String p2ReceiptId =
        contentId(
            "generation-receipt",
            p2TaskId,
            p2RequestSha,
            p2ResponseSha,
            terminal.p2Response().observedRuntime().sha256().value());
    String p1RoundId = contentId("process-model-round", p1TaskId, p1ResponseSha, p1ReceiptId);
    String p2RoundId = contentId("process-model-round", p2TaskId, p2ResponseSha, p2ReceiptId);
    String hypothesisId =
        contentId("business-process-hypothesis", request.taskShardId(), p1TaskId, p1RoundId);
    String claimId = contentId("process-claim", hypothesisId, "L01", "F01", "F02", "TRANSITION");
    String reviewId =
        contentId("process-hypothesis-review", p2RoundId, hypothesisId, claimId, "KEEP");

    ObjectNode body = JsonNodeFactory.instance.objectNode();
    moduleReference(
        body.putObject("businessProcessTaskPublicationRef"),
        request.businessProcessTaskPublication());
    body.put("taskShardId", request.taskShardId());
    ArrayNode tasks = body.putArray("processModelTasks");
    task(
        tasks.addObject(),
        p1TaskId,
        "PROCESS_P1_HYPOTHESIS",
        request.taskShardId(),
        1,
        terminal.p1ApplicationRequest(),
        p1RequestSha);
    task(
        tasks.addObject(),
        p2TaskId,
        "PROCESS_P2_PRECISION_REVIEW",
        request.taskShardId(),
        2,
        terminal.p2ApplicationRequest(),
        p2RequestSha);

    ArrayNode rounds = body.putArray("processModelRounds");
    successRound(
        rounds.addObject(),
        p1RoundId,
        p1TaskId,
        "PROCESS_P1_HYPOTHESIS",
        request.taskShardId(),
        1,
        p1RequestSha,
        p1ResponseSha,
        "P1_HYPOTHESES",
        hypothesisId,
        p1ReceiptId,
        null);
    successRound(
        rounds.addObject(),
        p2RoundId,
        p2TaskId,
        "PROCESS_P2_PRECISION_REVIEW",
        request.taskShardId(),
        2,
        p2RequestSha,
        p2ResponseSha,
        "P2_REVIEWS",
        hypothesisId,
        p2ReceiptId,
        reviewId);

    ArrayNode receipts = body.putArray("generationReceipts");
    receipt(
        receipts.addObject(),
        p1ReceiptId,
        "PROCESS_P1_HYPOTHESIS",
        p1TaskId,
        request.taskShardId(),
        p1RequestSha,
        p1ResponseSha,
        request.expectedRuntime(),
        terminal.p1Response().observedRuntime());
    receipt(
        receipts.addObject(),
        p2ReceiptId,
        "PROCESS_P2_PRECISION_REVIEW",
        p2TaskId,
        request.taskShardId(),
        p2RequestSha,
        p2ResponseSha,
        request.expectedRuntime(),
        terminal.p2Response().observedRuntime());

    ArrayNode dispositions = body.putArray("taskDispositions");
    taskDisposition(
        dispositions.addObject(),
        p1TaskId,
        request.taskShardId(),
        "P1",
        "RESPONSE_ACCEPTED",
        p1RoundId,
        p1ReceiptId,
        null,
        List.of(),
        null);
    taskDisposition(
        dispositions.addObject(),
        p2TaskId,
        request.taskShardId(),
        "P2",
        "RESPONSE_ACCEPTED",
        p2RoundId,
        p2ReceiptId,
        null,
        List.of(),
        null);

    ObjectNode hypothesis = body.putArray("businessProcessHypotheses").addObject();
    hypothesis.put("businessProcessHypothesisId", hypothesisId);
    hypothesis.put("taskShardId", request.taskShardId());
    hypothesis.put("p1TaskSpecId", p1TaskId);
    hypothesis.put("p1RoundId", p1RoundId);
    hypothesis.put("p2TaskSpecId", p2TaskId);
    hypothesis.put("p2RoundId", p2RoundId);
    strings(hypothesis.putArray("flowKeys"), List.of("F01", "F02"));
    strings(hypothesis.putArray("relationKeys"), List.of("L01"));
    ObjectNode claim = hypothesis.putArray("claims").addObject();
    claim.put("processClaimId", claimId);
    strings(claim.putArray("basisKeys"), List.of("L01"));
    claim.put("claimKind", "TRANSITION");
    strings(claim.putArray("objectKeys"), List.of("F02"));
    claim.put("predicate", "MAY_HAND_OFF_TO");
    strings(claim.putArray("subjectKeys"), List.of("F01"));
    ObjectNode review = hypothesis.putObject("review");
    review.put("processHypothesisReviewId", reviewId);
    review.put("hypothesisKey", "H01");
    review.put("decision", "KEEP");
    strings(review.putArray("retainedClaimKeys"), List.of("PC01"));

    ObjectNode disposition = body.putObject("processInterpretationDisposition");
    disposition.put("executionKind", "MODEL_TASKS");
    disposition.put("p1TaskId", p1TaskId);
    disposition.put("p2TaskId", p2TaskId);
    disposition.put("disposition", "READY_FOR_ADMISSION");
    strings(disposition.putArray("proposedBusinessProcessHypothesisIds"), List.of(hypothesisId));
    strings(disposition.putArray("retainedBusinessProcessHypothesisIds"), List.of(hypothesisId));
    disposition.putArray("narrowedBusinessProcessHypothesisIds");
    disposition.putArray("droppedBusinessProcessHypothesisIds");
    disposition.putArray("pendingBusinessProcessHypothesisIds");
    disposition.putArray("p2GapBusinessProcessHypothesisIds");
    disposition.putArray("p2FailedBusinessProcessHypothesisIds");
    disposition.putArray("processGaps");
    disposition.putArray("gapIds");
    disposition.putNull("failureRef");
    disposition.putNull("reasonCode");
    body.put("providerCallCount", 2);
    body.put("closed", true);

    ObjectNode envelope = JsonNodeFactory.instance.objectNode();
    envelope.put("schemaVersion", SCHEMA_VERSION);
    envelope.put("artifactType", ARTIFACT_TYPE);
    envelope.set("producer", producer(address));
    envelope.set("upstreamArtifacts", references(upstream));
    envelope.set("controls", controls(controls));
    envelope.set("completion", completion(List.of()));
    envelope.set("payload", body);
    ArtifactId artifactId =
        ArtifactId.parse(
            ARTIFACT_PREFIX
                + ":"
                + sha256(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(envelope).copyToByteArray())));
    envelope.put("artifactId", artifactId.value());
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        artifactId,
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private void verifyInstalled(
      ModulePublicationReference reference,
      AnalysisStepModuleAddress expectedAddress,
      String expectedShardId) {
    ReopenedModulePublication reopened = moduleArtifacts.reopen(reference);
    if (!reference.equals(reopened.reference())
        || !expectedAddress.equals(reopened.receipt().address())
        || reopened.payloads().size() != 1) {
      throw new BusinessProcessInterpretationException("PROCESS_MODEL_REFERENCE_INVALID");
    }
    VerifiedCanonicalPayload payload = reopened.payloads().get(0);
    JsonNode body = canonicalJson.parseCanonical(payload.canonicalUtf8()).path("payload");
    boolean gap = "GAP".equals(body.at("/processInterpretationDisposition/disposition").asText());
    boolean accepted =
        "READY_FOR_ADMISSION"
            .equals(body.at("/processInterpretationDisposition/disposition").asText());
    if (!FILE_NAME.equals(payload.descriptor().fileName())
        || !ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
        || !SCHEMA_VERSION.equals(payload.descriptor().schemaVersion())
        || !expectedShardId.equals(body.path("taskShardId").asText())
        || body.path("processModelTasks").size() != 2
        || body.path("taskDispositions").size() != 2
        || !body.path("closed").asBoolean(false)
        || (!gap && !accepted)
        || (gap
            && (body.path("processModelRounds").size() != 1
                || body.path("generationReceipts").size() != 1
                || body.path("businessProcessHypotheses").size() != 0
                || body.path("providerCallCount").asInt(-1) != 1))
        || (accepted
            && (body.path("processModelRounds").size() != 2
                || body.path("generationReceipts").size() != 2
                || body.path("businessProcessHypotheses").size() != 1
                || body.path("providerCallCount").asInt(-1) != 2))) {
      throw new BusinessProcessInterpretationException("PROCESS_MODEL_REFERENCE_INVALID");
    }
  }

  private static void successRound(
      ObjectNode target,
      String roundId,
      String taskId,
      String taskKind,
      String shardId,
      int ordinal,
      String requestSha,
      String responseSha,
      String responseKind,
      String hypothesisId,
      String receiptId,
      String reviewId) {
    target.put("processModelRoundId", roundId);
    target.put("taskSpecId", taskId);
    target.put("taskKind", taskKind);
    target.put("taskShardId", shardId);
    target.put("roundOrdinal", ordinal);
    target.put("requestSha256", requestSha);
    target.put("responseSha256", responseSha);
    target.put("responseKind", responseKind);
    strings(target.putArray("businessProcessHypothesisIds"), List.of(hypothesisId));
    ArrayNode reviews = target.putArray("processHypothesisReviews");
    if (reviewId != null) {
      ObjectNode review = reviews.addObject();
      review.put("processHypothesisReviewId", reviewId);
      review.put("hypothesisKey", "H01");
      review.put("decision", "KEEP");
      strings(review.putArray("retainedClaimKeys"), List.of("PC01"));
    }
    target.putArray("gapIds");
    target.putNull("failureCode");
    target.put("generationReceiptId", receiptId);
  }

  private static void receipt(
      ObjectNode target,
      String receiptId,
      String generationKind,
      String taskId,
      String shardId,
      String requestSha,
      String responseSha,
      ArtifactReference expectedRuntime,
      ArtifactReference observedRuntime) {
    target.put("generationReceiptId", receiptId);
    target.put("generationKind", generationKind);
    target.put("taskSpecId", taskId);
    target.putNull("flowSliceId");
    target.put("taskShardId", shardId);
    target.put("requestSha256", requestSha);
    target.put("responseSha256", responseSha);
    reference(target.putObject("expectedRuntime"), expectedRuntime);
    reference(target.putObject("observedRuntime"), observedRuntime);
    target.put("started", true);
    target.put("completed", true);
  }

  private void task(
      ObjectNode target,
      String taskId,
      String taskKind,
      String shardId,
      int ordinal,
      ImmutableBytes applicationRequest,
      String requestSha) {
    target.put("taskSpecId", taskId);
    target.put("taskKind", taskKind);
    target.put("taskShardId", shardId);
    target.put("taskOrdinal", ordinal);
    target.set("applicationRequest", canonicalJson.parseCanonical(applicationRequest));
    target.put("inputJsonSha256", requestSha);
  }

  private static void taskDisposition(
      ObjectNode target,
      String taskId,
      String shardId,
      String round,
      String state,
      String modelRoundId,
      String receiptId,
      String upstreamTaskId,
      List<String> gapIds,
      String reasonCode) {
    target.put("taskSpecId", taskId);
    target.put("taskScopeKind", "PROCESS_SHARD");
    target.put("taskShardId", shardId);
    target.put("round", round);
    target.put("state", state);
    nullable(target, "modelRoundId", modelRoundId);
    nullable(target, "generationReceiptId", receiptId);
    nullable(target, "upstreamTaskSpecId", upstreamTaskId);
    strings(target.putArray("gapIds"), gapIds);
    target.putNull("failureRef");
    nullable(target, "reasonCode", reasonCode);
  }

  private static void moduleReference(ObjectNode target, ModulePublicationReference reference) {
    target.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    target.put("moduleReceiptId", reference.moduleReceiptId().value());
    target.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
  }

  private static ArtifactReference reference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static void reference(ObjectNode target, ArtifactReference value) {
    target.put("artifactId", value.artifactId().value());
    target.put("sha256", value.sha256().value());
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> reference(result.addObject(), value));
    return result;
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result
        .putObject("address")
        .put("kind", "ANALYSIS_STEP")
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue())
        .put("moduleNumber", address.moduleNumber())
        .put("moduleKey", address.moduleKey());
    result.put("moduleVersion", "v1");
    return result;
  }

  private static ObjectNode controls(ArtifactControls values) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("toolchainSha256", values.toolchainSha256().value());
    result.put("profileSha256", values.profileSha256().value());
    result.put("schemaBundleSha256", values.schemaBundleSha256().value());
    if (values.promptBundleSha256() == null) {
      result.putNull("promptBundleSha256");
    } else {
      result.put("promptBundleSha256", values.promptBundleSha256().value());
    }
    result
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", values.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", values.artifactPolicyRegistryRef().sha256().value());
    return result;
  }

  private static ObjectNode completion(List<String> gapIds) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("status", ModuleCompletionStatus.SUCCEEDED.name());
    strings(result.putArray("gapRefs"), gapIds);
    result.putNull("failureRef");
    return result;
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.stream().sorted().forEach(target::add);
  }

  private static void nullable(ObjectNode target, String field, String value) {
    if (value == null) {
      target.putNull(field);
    } else {
      target.put(field, value);
    }
  }

  private static String contentId(String prefix, String... values) {
    byte[][] framed = new byte[values.length + 1][];
    framed[0] = frame(prefix + "-id-v1");
    for (int index = 0; index < values.length; index++) {
      framed[index + 1] = frame(values[index]);
    }
    return prefix + ":" + sha256(framed);
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
      return HexFormat.of().formatHex(digest.digest());
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
}
