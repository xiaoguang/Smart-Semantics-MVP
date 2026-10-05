package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Experimental extract → source-review runner; it neither publishes nor calls upstream tools. */
public final class OntologyTaskRunner {
  private static final String RESOURCE_ROOT = "/org/sourceanalysis/app/analysis/ontology/";
  private final StructuredModelProvider provider;
  private final int maxRequestBytes;
  private final int maxOutputBytes;
  private final OntologyJobResultStore store;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();
  private final OntologyDefinitionValidator validator = new OntologyDefinitionValidator();

  public OntologyTaskRunner(
      StructuredModelProvider provider, int maxRequestBytes, int maxOutputBytes) {
    this(provider, maxRequestBytes, maxOutputBytes, null);
  }

  public OntologyTaskRunner(
      StructuredModelProvider provider,
      int maxRequestBytes,
      int maxOutputBytes,
      OntologyJobResultStore store) {
    this.provider = Objects.requireNonNull(provider, "ontology model provider");
    if (maxRequestBytes < 1 || maxOutputBytes < 1) {
      throw new IllegalArgumentException("ONTOLOGY_TASK_CAPACITY_INVALID");
    }
    this.maxRequestBytes = maxRequestBytes;
    this.maxOutputBytes = maxOutputBytes;
    this.store = store;
  }

  public Result run(TaskKind kind, String question, OntologyReadingPacket packet) {
    Objects.requireNonNull(kind, "ontology task kind");
    Objects.requireNonNull(packet, "ontology reading packet");
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_QUESTION_INVALID");
    }
    String jobKey = OntologyJobResultStore.jobKey(kind, question, packet, maxOutputBytes);
    try {
      return runStages(kind, question, packet, jobKey);
    } catch (RuntimeException failure) {
      if (store != null) {
        store.failed(jobKey, failure);
      }
      throw failure;
    }
  }

  private Result runStages(
      TaskKind kind, String question, OntologyReadingPacket packet, String jobKey) {
    ObjectNode initial = input(kind, question, packet);
    String extractPrompt = resource(kind.promptFile());
    ImmutableBytes candidateSchema = validator.schema(OntologyDefinitionValidator.Stage.EXTRACT);
    StructuredModelResponse draftResponse =
        call(
            kind,
            "EXTRACT",
            jobKey,
            initial,
            extractPrompt,
            OntologyProviderSchema.from(candidateSchema));
    JsonNode draft =
        validator.validate(
            draftResponse.responseJson(),
            kind,
            OntologyDefinitionValidator.Stage.EXTRACT,
            packet,
            maxOutputBytes);
    if (store != null) {
      store.success(jobKey, "extract", draft, draftResponse.runtimeIdentity());
    }

    ObjectNode reviewInput = initial.deepCopy();
    reviewInput.set("actualDraft", draft);
    String reviewPrompt = resource("review-v1.txt");
    ImmutableBytes reviewSchema = validator.schema(OntologyDefinitionValidator.Stage.REVIEW);
    StructuredModelResponse reviewResponse =
        call(
            kind,
            "REVIEW",
            jobKey,
            reviewInput,
            reviewPrompt,
            OntologyProviderSchema.from(reviewSchema));
    JsonNode review =
        validator.validate(
            reviewResponse.responseJson(),
            kind,
            OntologyDefinitionValidator.Stage.REVIEW,
            packet,
            maxOutputBytes);
    validator.validatePair(draft, review, kind, packet);
    if (!draftResponse.runtimeIdentity().equals(reviewResponse.runtimeIdentity())) {
      throw new IllegalArgumentException("ONTOLOGY_MODEL_RUNTIME_CHANGED");
    }
    if (store != null) {
      store.success(jobKey, "review", review, reviewResponse.runtimeIdentity());
    }
    Result result =
        new Result(
            kind,
            question,
            packet.packetId(),
            json.encodeCanonical(draft),
            json.encodeCanonical(review),
            draftResponse.runtimeIdentity(),
            reviewResponse.runtimeIdentity());
    if (store != null) {
      store.completed(jobKey, packet, result, maxOutputBytes);
    }
    return result;
  }

  private ObjectNode input(TaskKind kind, String question, OntologyReadingPacket packet) {
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-task-input-v1");
    root.put("taskKind", kind.name());
    root.put("question", question);
    root.set("readingPacket", json.parseCanonical(packet.modelInput()));
    ArrayNode allowlist = root.putArray("allowedRefs");
    packet.units().forEach(unit -> allowlist.add(unit.localRef()));
    return root;
  }

  private StructuredModelResponse call(
      TaskKind kind,
      String phase,
      String jobKey,
      ObjectNode input,
      String prompt,
      ImmutableBytes outputSchema) {
    ImmutableBytes canonicalInput = json.encodeCanonical(input);
    long envelopeBytes =
        (long) canonicalInput.size()
            + prompt.getBytes(StandardCharsets.UTF_8).length
            + outputSchema.size()
            + maxOutputBytes;
    if (envelopeBytes > maxRequestBytes) {
      throw new IllegalArgumentException("ONTOLOGY_TASK_INPUT_TOO_LARGE");
    }
    String taskId =
        "ontology-"
            + kind.name().toLowerCase(java.util.Locale.ROOT)
            + "-"
            + phase.toLowerCase(java.util.Locale.ROOT)
            + "-"
            + OntologyReadingPacket.sha256(canonicalInput.copyToByteArray()).substring(0, 20);
    StructuredModelRequest request =
        new StructuredModelRequest(
            taskId,
            "ONTOLOGY_" + kind.name() + "_" + phase,
            prompt,
            canonicalInput,
            outputSchema,
            maxOutputBytes);
    if (store != null) {
      store.request(jobKey, phase.toLowerCase(java.util.Locale.ROOT), request);
    }
    StructuredModelResponse response;
    try {
      response = provider.generate(request);
    } catch (StructuredModelProviderFailure failure) {
      if (store != null) {
        store.providerFailure(jobKey, phase.toLowerCase(java.util.Locale.ROOT), failure);
      }
      throw failure;
    }
    if (store != null) {
      store.response(jobKey, phase.toLowerCase(java.util.Locale.ROOT), response);
    }
    return response;
  }

  static String resource(String name) {
    try (InputStream input = OntologyTaskRunner.class.getResourceAsStream(RESOURCE_ROOT + name)) {
      if (input == null) {
        throw new IllegalStateException("ONTOLOGY_TASK_RESOURCE_MISSING: " + name);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (IOException unreadable) {
      throw new IllegalStateException("ONTOLOGY_TASK_RESOURCE_UNREADABLE: " + name, unreadable);
    }
  }

  public enum TaskKind {
    OBJECT("object-extract-v1.txt"),
    ACTION("action-extract-v1.txt"),
    ANALYTIC("analytic-extract-v1.txt"),
    RELATE("relate-v1.txt");

    private final String promptFile;

    TaskKind(String promptFile) {
      this.promptFile = promptFile;
    }

    String promptFile() {
      return promptFile;
    }
  }

  public record Result(
      TaskKind kind,
      String question,
      String packetId,
      ImmutableBytes draft,
      ImmutableBytes finalDefinitions,
      ModelRuntimeIdentityV1 draftRuntime,
      ModelRuntimeIdentityV1 reviewRuntime) {}
}
