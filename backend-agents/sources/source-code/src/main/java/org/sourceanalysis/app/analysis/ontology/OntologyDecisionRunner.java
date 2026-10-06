package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.NavigationPage;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Bounded survey and reading decisions; it never treats a decision as reviewed ontology fact. */
public final class OntologyDecisionRunner {
  private static final String ROOT = "/org/sourceanalysis/app/analysis/ontology/";
  private final StructuredModelProvider provider;
  private final int maxRequestBytes;
  private final int maxOutputBytes;
  private final OntologyJobResultStore store;
  private final FormalReadingMaterial formalReadingMaterial;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, List<OntologyNavigationView>> navigationViewsByDecisionKey =
      new LinkedHashMap<>();
  private final Map<String, List<OntologyNavigationView>> navigationViewsBySelectedQuestion =
      new LinkedHashMap<>();

  public OntologyDecisionRunner(
      StructuredModelProvider provider, int maxRequestBytes, int maxOutputBytes) {
    this(provider, maxRequestBytes, maxOutputBytes, null, defaultFormalReadingMaterial());
  }

  /**
   * Allows the later runtime to bind the immutable configured formal prompt and schema snapshot.
   */
  public OntologyDecisionRunner(
      StructuredModelProvider provider,
      int maxRequestBytes,
      int maxOutputBytes,
      FormalReadingMaterial formalReadingMaterial) {
    this(provider, maxRequestBytes, maxOutputBytes, null, formalReadingMaterial);
  }

  public OntologyDecisionRunner(
      StructuredModelProvider provider,
      int maxRequestBytes,
      int maxOutputBytes,
      OntologyJobResultStore store) {
    this(provider, maxRequestBytes, maxOutputBytes, store, defaultFormalReadingMaterial());
  }

  public OntologyDecisionRunner(
      StructuredModelProvider provider,
      int maxRequestBytes,
      int maxOutputBytes,
      OntologyJobResultStore store,
      FormalReadingMaterial formalReadingMaterial) {
    this.provider = provider;
    if (maxRequestBytes < 1 || maxOutputBytes < 1) {
      throw new IllegalArgumentException("ONTOLOGY_TASK_CAPACITY_INVALID");
    }
    this.maxRequestBytes = maxRequestBytes;
    this.maxOutputBytes = maxOutputBytes;
    this.store = store;
    this.formalReadingMaterial =
        Objects.requireNonNull(formalReadingMaterial, "formal reading material");
  }

  public Decision survey(String scope, OntologyEvidenceCorpus corpus, NavigationPage page) {
    if (scope == null || scope.isBlank() || corpus == null || page == null) {
      throw new IllegalArgumentException("ONTOLOGY_SURVEY_INPUT_INVALID");
    }
    String sourceIdentity = corpus.sourceIdentity();
    ObjectNode input = mapper.createObjectNode();
    OntologyNavigationView navigationView =
        OntologyNavigationView.survey(sourceIdentity, corpus, page);
    input.put("schemaVersion", "ontology-survey-input-v2");
    input.put("scope", scope);
    input.put("sourceIdentity", sourceIdentity);
    input.set("navigationView", navigationView.visible());
    Decision result =
        call(
            "SURVEY",
            input,
            "survey-v2.txt",
            "survey-v2.schema.json",
            OntologyNavigationView.sourceBasis(sourceIdentity, List.of(navigationView)));
    try {
      JsonNode rawOutput = json.parseCanonical(result.output());
      ArrayNode corrections = mapper.createArrayNode();
      JsonNode output = normalizeSurveyReferences(rawOutput, navigationView, corrections);
      Set<String> questionIds = new HashSet<>();
      for (JsonNode hypothesis : output.path("systemHypotheses")) {
        requireReferences(
            hypothesis.path("observedEntryRefs"),
            List.of(navigationView),
            OntologyNavigationView.ReferenceKind.ENTRY);
      }
      for (JsonNode question : output.path("questions")) {
        if (!questionIds.add(question.path("questionId").asText())) {
          throw new IllegalArgumentException("ONTOLOGY_SURVEY_QUESTION_DUPLICATE");
        }
        requireReferences(
            question.path("candidateEntryRefs"),
            List.of(navigationView),
            OntologyNavigationView.ReferenceKind.ENTRY);
        requireReferences(
            question.path("clueRefs"),
            List.of(navigationView),
            OntologyNavigationView.ReferenceKind.CLUE);
      }
      Decision validated =
          new Decision(
              result.kind(),
              result.jobKey(),
              result.input(),
              json.encodeCanonical(output),
              result.runtimeIdentity());
      if (store != null && !corrections.isEmpty()) {
        store.surveyReferenceCorrections(result.jobKey(), corrections);
      }
      navigationViewsByDecisionKey.put(validated.jobKey(), List.of(navigationView));
      saveSuccess("survey", input, validated, sourceIdentity, List.of(navigationView));
      return validated;
    } catch (RuntimeException invalid) {
      if (store != null) {
        store.failed(result.jobKey(), invalid);
      }
      throw invalid;
    }
  }

  /** Prepares the formal survey envelope without constructing or calling a Provider. */
  public PreparedDecision prepareFormalSurvey(
      OntologyEvidenceCorpus corpus, int maxNavigationEntries, FormalDecisionMaterial material) {
    Objects.requireNonNull(corpus, "ontology corpus");
    Objects.requireNonNull(material, "formal survey material");
    if (maxNavigationEntries < 1) {
      throw new IllegalArgumentException("ONTOLOGY_SURVEY_INPUT_INVALID");
    }
    OntologyReadingCoordinator.FormalVisibleScope visible =
        OntologyReadingCoordinator.FormalVisibleScope.forReading(
            corpus, Set.of(), Set.of(), Set.of(), maxNavigationEntries, List.of());
    ObjectNode input = mapper.createObjectNode();
    input.put("schemaVersion", "ontology-survey-input-v3");
    input.put("maxNavigationEntries", maxNavigationEntries);
    input.set("visibleScope", visibleDocument(visible, List.of()));
    return prepare(
        "FORMAL_SURVEY",
        input,
        material.prompt(),
        material.validationSchema(),
        "ontology-survey-response-v3",
        formalSourceBasis(corpus),
        material.requestedMaxOutputTokens());
  }

  /** Prepares the formal question-selection envelope without constructing or calling a Provider. */
  public PreparedDecision prepareFormalPrioritize(
      OntologyEvidenceCorpus corpus,
      List<FormalSurveyQuestion> questions,
      int maxQuestions,
      int maxTaskKindsPerQuestion,
      FormalDecisionMaterial material) {
    return prepareFormalPrioritize(
        corpus, questions, maxQuestions, maxTaskKindsPerQuestion, material, null);
  }

  /** A v2 purpose is explicit input; the historical envelope remains byte-identical without it. */
  public PreparedDecision prepareFormalPrioritize(
      OntologyEvidenceCorpus corpus,
      List<FormalSurveyQuestion> questions,
      int maxQuestions,
      int maxTaskKindsPerQuestion,
      FormalDecisionMaterial material,
      OntologyScopeReader.Purpose purpose) {
    return prepareFormalPrioritize(
        corpus, questions, maxQuestions, maxTaskKindsPerQuestion, material, purpose, false);
  }

  /** The explicit joint-link family never changes historical priority envelopes. */
  public PreparedDecision prepareFormalPrioritize(
      OntologyEvidenceCorpus corpus,
      List<FormalSurveyQuestion> questions,
      int maxQuestions,
      int maxTaskKindsPerQuestion,
      FormalDecisionMaterial material,
      OntologyScopeReader.Purpose purpose,
      boolean jointLinkFamily) {
    Objects.requireNonNull(corpus, "ontology corpus");
    Objects.requireNonNull(questions, "formal survey questions");
    Objects.requireNonNull(material, "formal prioritize material");
    if (questions.isEmpty() || maxQuestions < 1 || maxTaskKindsPerQuestion < 1) {
      throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_INPUT_INVALID");
    }
    ObjectNode input = mapper.createObjectNode();
    input.put(
        "schemaVersion",
        jointLinkFamily ? "ontology-prioritize-input-v5" : "ontology-prioritize-input-v4");
    input.put("maxQuestions", maxQuestions);
    input.put("maxTaskKindsPerQuestion", maxTaskKindsPerQuestion);
    if (purpose != null) input.put("purpose", purpose.name());
    ArrayNode values = input.putArray("questions");
    for (FormalSurveyQuestion question : questions) {
      ObjectNode value = values.addObject();
      value.put("questionRef", question.questionRef());
      value.put("question", question.question());
      ArrayNode entries = value.putArray("candidateEntryRefs");
      question.candidateEntryRefs().forEach(entries::add);
      ArrayNode clues = value.putArray("clueRefs");
      question.clueRefs().forEach(clues::add);
      if (jointLinkFamily) {
        ArrayNode anchors = value.putArray("linkAnchorRefs");
        for (String ref : question.clueRefs()) {
          corpus.aliases().clue(ref);
          anchors.add(ref);
        }
      }
    }
    ImmutableBytes validationSchema = material.validationSchema();
    if (jointLinkFamily) {
      ObjectNode schema = (ObjectNode) json.parseCanonical(validationSchema).deepCopy();
      ((ObjectNode) schema.path("properties").path("schemaVersion"))
          .put("const", "ontology-prioritize-response-v5");
      ObjectNode selected = (ObjectNode) schema.path("$defs").path("selected");
      ((ArrayNode) selected.path("required")).add("linkAnchorRefs");
      ObjectNode properties = (ObjectNode) selected.path("properties");
      ObjectNode anchors = properties.putObject("linkAnchorRefs");
      anchors
          .put("type", "array")
          .put("uniqueItems", true)
          .put("maxItems", maxTaskKindsPerQuestion);
      anchors.putObject("items").put("type", "string").put("pattern", "^K[1-9][0-9]*$");
      ObjectNode taskKinds = (ObjectNode) properties.path("taskKinds");
      taskKinds.put("minItems", 1).put("maxItems", maxTaskKindsPerQuestion);
      ArrayNode kinds = ((ObjectNode) taskKinds.path("items")).putArray("enum").add("OBJECT");
      if (purpose == OntologyScopeReader.Purpose.SKELETON) kinds.add("LINK");
      else kinds.add("ACTION").add("ANALYTIC");
      validationSchema = json.encodeCanonical(schema);
    } else if (purpose == OntologyScopeReader.Purpose.SKELETON) {
      ObjectNode schema = (ObjectNode) json.parseCanonical(validationSchema);
      ObjectNode taskKinds =
          (ObjectNode) schema.path("$defs").path("selected").path("properties").path("taskKinds");
      taskKinds.put("minItems", 1);
      taskKinds.put("maxItems", 1);
      ((ObjectNode) taskKinds.path("items")).putArray("enum").add("OBJECT");
      validationSchema = json.encodeCanonical(schema);
    }
    return prepare(
        "FORMAL_PRIORITIZE",
        input,
        material.prompt(),
        validationSchema,
        jointLinkFamily ? "ontology-prioritize-response-v5" : "ontology-prioritize-response-v4",
        formalSourceBasis(corpus),
        material.requestedMaxOutputTokens());
  }

  /** Dispatches the exact immutable decision prepared before Provider construction. */
  public Decision execute(PreparedDecision prepared) {
    Objects.requireNonNull(prepared, "prepared formal decision");
    if (provider == null) {
      throw new IllegalStateException("ONTOLOGY_DECISION_PROVIDER_REQUIRED");
    }
    try {
      if (store != null) {
        store.request(
            prepared.jobKey(),
            prepared.kind().toLowerCase(java.util.Locale.ROOT),
            prepared.request());
      }
      StructuredModelResponse response;
      try {
        response = provider.generate(prepared.request());
      } catch (StructuredModelProviderFailure failure) {
        if (store != null) {
          store.providerFailure(
              prepared.jobKey(), prepared.kind().toLowerCase(java.util.Locale.ROOT), failure);
        }
        throw failure;
      }
      if (store != null) {
        store.response(
            prepared.jobKey(), prepared.kind().toLowerCase(java.util.Locale.ROOT), response);
      }
      if (response.responseJson().size() > maxOutputBytes) {
        throw new DecisionModelOutputFailure(
            "RESPONSE_BUDGET_EXCEEDED", formalDecisionStage(prepared.kind()), null);
      }
      JsonNode output;
      try {
        output = json.parseStrictJson(response.responseJson());
      } catch (IllegalArgumentException invalidJson) {
        throw new DecisionModelOutputFailure(
            "INVALID_JSON", formalDecisionStage(prepared.kind()), invalidJson);
      }
      Schema compiled =
          SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
              .getSchema(json.parseCanonical(prepared.validationSchema()));
      if (!compiled.validate(output).isEmpty()
          || !prepared
              .expectedOutputSchemaVersion()
              .equals(output.path("schemaVersion").asText())) {
        throw new DecisionModelOutputFailure(
            "ONTOLOGY_DECISION_INVALID", formalDecisionStage(prepared.kind()), null);
      }
      return new Decision(
          prepared.kind(),
          prepared.jobKey(),
          prepared.input(),
          json.encodeCanonical(output),
          response.runtimeIdentity());
    } catch (RuntimeException failure) {
      if (store != null) {
        store.failed(prepared.jobKey(), failure);
      }
      throw failure;
    }
  }

  /**
   * Records a validated formal survey or prioritization response in the existing private journal.
   */
  public void saveFormalDecision(
      String stage, PreparedDecision prepared, Decision decision, OntologyEvidenceCorpus corpus) {
    if (!prepared.jobKey().equals(decision.jobKey())
        || !prepared.kind().equals(decision.kind())
        || !prepared.input().equals(decision.input())) {
      throw new IllegalArgumentException("ONTOLOGY_DECISION_IDENTITY_INVALID");
    }
    saveSuccess(
        stage,
        (ObjectNode) json.parseCanonical(prepared.input()),
        decision,
        corpus.sourceIdentity(),
        List.of());
  }

  /** Uses the formal-survey schema while retaining the actual configured prompt snapshot. */
  public static FormalDecisionMaterial formalSurveyMaterial(String prompt) {
    return formalSurveyMaterial(prompt, null);
  }

  /** Uses the formal-survey schema and the configured output-token limit for a formal run. */
  public static FormalDecisionMaterial formalSurveyMaterial(
      String prompt, Integer requestedMaxOutputTokens) {
    return new FormalDecisionMaterial(
        prompt,
        ImmutableBytes.copyOf(
            resource("formal-survey-v1.schema.json").getBytes(StandardCharsets.UTF_8)),
        requestedMaxOutputTokens);
  }

  /** Uses the formal-prioritize schema while retaining the actual configured prompt snapshot. */
  public static FormalDecisionMaterial formalPrioritizeMaterial(String prompt) {
    return formalPrioritizeMaterial(prompt, null);
  }

  /** Uses the formal-prioritize schema and the configured output-token limit for a formal run. */
  public static FormalDecisionMaterial formalPrioritizeMaterial(
      String prompt, Integer requestedMaxOutputTokens) {
    return new FormalDecisionMaterial(
        prompt,
        ImmutableBytes.copyOf(
            resource("formal-prioritize-v1.schema.json").getBytes(StandardCharsets.UTF_8)),
        requestedMaxOutputTokens);
  }

  /** Uses the formal-reading schema while retaining the actual configured prompt snapshot. */
  public static FormalReadingMaterial formalReadingMaterial(String prompt) {
    return formalReadingMaterial(prompt, null);
  }

  public static FormalReadingMaterial formalReadingMaterialV4(
      String prompt, Integer requestedMaxOutputTokens) {
    return new FormalReadingMaterial(
        prompt,
        ImmutableBytes.copyOf(
            resource("formal-reading-v2.schema.json").getBytes(StandardCharsets.UTF_8)),
        null,
        requestedMaxOutputTokens);
  }

  /** Uses the formal-reading schema and the configured output-token limit for a formal run. */
  public static FormalReadingMaterial formalReadingMaterial(
      String prompt, Integer requestedMaxOutputTokens) {
    return new FormalReadingMaterial(
        prompt,
        ImmutableBytes.copyOf(
            resource("formal-reading-v1.schema.json").getBytes(StandardCharsets.UTF_8)),
        null,
        requestedMaxOutputTokens);
  }

  public Decision readingCheck(
      String questionId,
      String question,
      String sourceIdentity,
      OntologyReadingPacket packet,
      List<OntologyNavigationView> navigationViews,
      int maxPageItems) {
    return readingCheck(
        questionId, question, sourceIdentity, packet, navigationViews, maxPageItems, 1);
  }

  FormalDecision formalReadingCheck(
      String questionId,
      String question,
      OntologyReadingCoordinator.FormalTask task,
      OntologyReadingCoordinator.FormalVisibleScope visible,
      List<OntologyReadingCoordinator.FormalUnitUse> activeUnits,
      int maxActionsPerRound,
      int maxNavigationEntries,
      int maxUnitBytes,
      int maxReadingRequestBytes,
      int remainingReadingDecisions,
      OntologyReadingPacket activePacket) {
    if (questionId == null
        || questionId.isBlank()
        || question == null
        || question.isBlank()
        || task == null
        || visible == null
        || activeUnits == null
        || maxActionsPerRound < 1
        || maxNavigationEntries < 1
        || maxUnitBytes < 1
        || maxReadingRequestBytes < 1
        || remainingReadingDecisions < 1) {
      throw new IllegalArgumentException("ONTOLOGY_READING_INPUT_INVALID");
    }
    ObjectNode input = mapper.createObjectNode();
    boolean businessLinks = visible.corpus().usesBusinessLinkNavigation();
    input.put(
        "schemaVersion", businessLinks ? "ontology-reading-input-v4" : "ontology-reading-input-v3");
    input.put("questionId", questionId);
    input.put("question", question);
    input.put("taskId", task.taskId());
    input.put("taskKind", task.taskKind().name());
    input.put("readingMode", task.readingMode().name());
    input.put("maxActionsPerRound", maxActionsPerRound);
    input.put("maxNavigationEntries", maxNavigationEntries);
    input.put("remainingReadingDecisions", remainingReadingDecisions);
    if (businessLinks) {
      input.put("maxUnitBytes", maxUnitBytes);
      input.put("maxRequestBytes", maxReadingRequestBytes);
    }
    input.set("visibleScope", visibleDocument(visible, activeUnits));
    if (activePacket == null) {
      input.putNull("readingPacket");
    } else {
      input.set("readingPacket", json.parseCanonical(activePacket.modelInput()));
    }
    ImmutableBytes validationSchema =
        businessLinks
            ? boundedFormalReadingSchema(maxActionsPerRound, maxNavigationEntries)
            : formalReadingMaterial.validationSchema();
    Decision result;
    try {
      result =
          call(
              "FORMAL_READING",
              input,
              formalReadingMaterial.prompt(),
              validationSchema,
              businessLinks ? "reading-response-v4" : "reading-response-v3",
              formalSourceBasis(visible.corpus()),
              formalReadingMaterial.requestedMaxOutputTokens());
    } catch (DecisionModelOutputFailure invalidResponse) {
      throw formalReadingModelOutputFailure(invalidResponse);
    }
    JsonNode output;
    try {
      output = json.parseCanonical(result.output());
      OntologyReadingCoordinator.validateFormalResponse(
          visible.corpus(), output, visible, maxActionsPerRound, maxNavigationEntries);
    } catch (IllegalArgumentException invalid) {
      if (store != null) {
        store.failed(result.jobKey(), invalid);
      }
      throw formalReadingModelOutputFailure(invalid);
    }
    if (store != null) {
      store.formalDecision(
          result.jobKey(),
          input,
          output,
          result.runtimeIdentity(),
          visible.corpus().sourceIdentity(),
          formalSourceBasis(visible.corpus()));
    }
    return new FormalDecision(result, formalReadingMaterial.prompt(), validationSchema);
  }

  private ImmutableBytes boundedFormalReadingSchema(int maxActions, int maxNavigation) {
    ObjectNode schema =
        (ObjectNode) json.parseCanonical(formalReadingMaterial.validationSchema()).deepCopy();
    ((ObjectNode) schema.path("properties").path("actions")).put("maxItems", maxActions);
    for (String action : List.of("query", "literalSearch")) {
      ((ObjectNode) schema.path("$defs").path(action).path("properties").path("limit"))
          .put("maximum", maxNavigation);
    }
    return json.encodeCanonical(schema);
  }

  private static FormalReadingModelOutputFailure formalReadingModelOutputFailure(
      DecisionModelOutputFailure invalidResponse) {
    return new FormalReadingModelOutputFailure(invalidResponse.code(), invalidResponse);
  }

  private static FormalReadingModelOutputFailure formalReadingModelOutputFailure(
      IllegalArgumentException invalidResponse) {
    return new FormalReadingModelOutputFailure(
        "ONTOLOGY_READING_RESPONSE_INVALID", invalidResponse);
  }

  private ObjectNode visibleDocument(
      OntologyReadingCoordinator.FormalVisibleScope visible,
      List<OntologyReadingCoordinator.FormalUnitUse> activeUnits) {
    ObjectNode document = mapper.createObjectNode();
    ArrayNode displayedEntries = document.putArray("displayedEntryRefs");
    visible.displayedEntries().forEach(displayedEntries::add);
    ArrayNode displayedClues = document.putArray("displayedClueRefs");
    visible.displayedClues().forEach(displayedClues::add);
    ArrayNode entries = document.putArray("selectedEntryRefs");
    visible.selectedEntries().forEach(entries::add);
    ArrayNode clues = document.putArray("selectedClueRefs");
    visible.selectedClues().forEach(clues::add);
    ArrayNode navigation = document.putArray("navigation");
    Map<OntologyReadingCoordinator.FormalUnitUse, OntologyReadingPacket.PacketCost> costs =
        new HashMap<>();
    visible
        .navigation()
        .forEach(card -> navigation.add(formalNavigationDocument(visible.corpus(), card, costs)));
    ArrayNode observations = document.putArray("queryObservations");
    visible
        .queryObservations()
        .forEach(
            observation ->
                observations.add(
                    formalQueryObservationDocument(visible.corpus(), observation, costs)));
    ArrayNode available = document.putArray("availableUnitUses");
    visible.availableUnits().forEach(use -> available.add(formalUnitUseDocument(use)));
    ArrayNode active = document.putArray("activeUnitUses");
    activeUnits.forEach(use -> active.add(formalUnitUseDocument(use)));
    ArrayNode required = document.putArray("requiredUnitUses");
    visible.requiredUnits().forEach(use -> required.add(formalUnitUseDocument(use)));
    return document;
  }

  private ObjectNode formalSourceBasis(OntologyEvidenceCorpus corpus) {
    ObjectNode basis = mapper.createObjectNode();
    basis.put("sourceIdentity", corpus.sourceIdentity());
    basis.put(
        "aliasMappingSha256",
        OntologyReadingPacket.sha256(corpus.aliases().canonicalMapping().copyToByteArray()));
    if (formalReadingMaterial.contextIdentity() != null) {
      basis.put("formalContextIdentity", formalReadingMaterial.contextIdentity());
    }
    return basis;
  }

  private ObjectNode formalNavigationDocument(
      OntologyEvidenceCorpus corpus,
      OntologyReadingCoordinator.FormalNavigationEntry card,
      Map<OntologyReadingCoordinator.FormalUnitUse, OntologyReadingPacket.PacketCost> costs) {
    ObjectNode document = mapper.createObjectNode();
    document.put("entryRef", card.entryRef());
    document.put("method", card.entry().method());
    document.put("route", card.entry().route());
    document.put("handlerFqn", card.entry().handlerFqn());
    document.put("assemblyStatus", card.entry().assemblyStatus());
    document.put("limitationCount", card.entry().limitationCount());
    if (card.controllerUnit() == null) {
      document.putNull("controllerUnit");
    } else {
      document.set(
          "controllerUnit",
          formalUnitNavigationDocument(corpus, card.controllerUnit(), null, costs));
    }
    ArrayNode clues = document.putArray("clues");
    card.clues()
        .forEach(
            item -> {
              ObjectNode clue = clues.addObject();
              clue.put("clueRef", item.clueRef());
              clue.put("kind", item.clue().kind().name());
              clue.put("keyDisplay", item.clue().keyDisplay());
              clue.put("totalUses", item.clue().totalUses());
              clue.set(
                  "readableUnit",
                  formalUnitNavigationDocument(corpus, item.readableUnit(), null, costs));
            });
    ArrayNode disclosure = document.putArray("clueDisclosure");
    card.clueDisclosure().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            item -> {
              ObjectNode value = disclosure.addObject();
              value.put("kind", item.getKey().name());
              value.put("total", item.getValue().total());
              value.put("shown", item.getValue().shown());
              value.put("unread", item.getValue().unread());
            });
    document.put("sqlAnalysesWithoutAst", card.sqlAnalysesWithoutAst());
    return document;
  }

  private ObjectNode formalQueryObservationDocument(
      OntologyEvidenceCorpus corpus,
      OntologyReadingCoordinator.FormalQueryObservation observation,
      Map<OntologyReadingCoordinator.FormalUnitUse, OntologyReadingPacket.PacketCost> costs) {
    ObjectNode document = mapper.createObjectNode();
    document.put("queryKind", observation.queryKind());
    if (observation.keyRef() == null) {
      document.putNull("keyRef");
    } else {
      document.put("keyRef", observation.keyRef());
    }
    if (observation.literal() == null) {
      document.putNull("query");
    } else {
      document.put("query", observation.literal());
    }
    document.put("offset", observation.offset());
    document.put("limit", observation.limit());
    document.put("total", observation.total());
    ArrayNode items = document.putArray("items");
    observation
        .items()
        .forEach(
            item -> {
              ObjectNode value =
                  formalUnitNavigationDocument(corpus, item.unitUse(), item.excerpt(), costs);
              ArrayNode clueRefs = value.putArray("clueRefs");
              item.clueRefs().forEach(clueRefs::add);
              items.add(value);
            });
    return document;
  }

  private ObjectNode formalUnitNavigationDocument(
      OntologyEvidenceCorpus corpus,
      OntologyReadingCoordinator.FormalUnitUse use,
      String excerpt,
      Map<OntologyReadingCoordinator.FormalUnitUse, OntologyReadingPacket.PacketCost> costs) {
    ObjectNode document = formalUnitUseDocument(use);
    OntologyEvidenceCorpus.UnitHandle canonical = corpus.aliases().unit(use.unitRef());
    OntologyEvidenceCorpus.UnitHandle handle =
        new OntologyEvidenceCorpus.UnitHandle(
            corpus.aliases().entry(use.entryRef()).entryId(),
            canonical.kind(),
            canonical.originalId());
    OntologyEvidenceCorpus.UnitNavigationMetadata metadata = corpus.unitMetadata(handle);
    document.put("kind", handle.kind().name());
    document.put("keyDisplay", metadata.keyDisplay());
    OntologyReadingPacket.PacketCost cost =
        costs.computeIfAbsent(
            use,
            ignored ->
                (corpus.usesBusinessLinkNavigation()
                        ? OntologyReadingPacket.formalV5(corpus, List.of(handle), Integer.MAX_VALUE)
                        : OntologyReadingPacket.formal(corpus, List.of(handle), Integer.MAX_VALUE))
                    .cost());
    ObjectNode measured = document.putObject("formalPacketCost");
    measured.put("fullSourceBytes", cost.fullSourceBytes());
    measured.put("modelInputBytes", cost.modelInputBytes());
    measured.put("privateInputBytes", cost.privateInputBytes());
    measured.put("explicitCallDetailBytes", cost.explicitCallDetailBytes());
    if (excerpt != null) {
      document.put("excerpt", excerpt);
    }
    return document;
  }

  private ObjectNode formalUnitUseDocument(OntologyReadingCoordinator.FormalUnitUse use) {
    ObjectNode document = mapper.createObjectNode();
    document.put("unitRef", use.unitRef());
    document.put("entryRef", use.entryRef());
    return document;
  }

  public Decision readingCheck(
      String questionId,
      String question,
      String sourceIdentity,
      OntologyReadingPacket packet,
      List<OntologyNavigationView> navigationViews,
      int maxPageItems,
      int remainingReadingDecisions) {
    if (questionId == null
        || !questionId.matches("Q[0-9]+")
        || question == null
        || question.isBlank()
        || sourceIdentity == null
        || sourceIdentity.isBlank()
        || navigationViews == null
        || maxPageItems < 1
        || remainingReadingDecisions < 1) {
      throw new IllegalArgumentException("ONTOLOGY_READING_INPUT_INVALID");
    }
    List<OntologyNavigationView> visible = new java.util.ArrayList<>(navigationViews);
    OntologyNavigationView packetView =
        packet == null ? null : OntologyNavigationView.packet(packet);
    if (packetView != null) {
      visible.add(packetView);
    }
    ObjectNode input = mapper.createObjectNode();
    input.put("schemaVersion", "ontology-reading-input-v2");
    input.put("questionId", questionId);
    input.put("question", question);
    input.put("maxPageItems", maxPageItems);
    input.put("remainingReadingDecisions", remainingReadingDecisions);
    if (packet == null) {
      input.putNull("packetId");
      input.putNull("readingPacket");
    } else {
      input.put("packetId", "current-reading-packet");
      input.set("readingPacket", packetView.visible());
    }
    ArrayNode views = input.putArray("navigationViews");
    navigationViews.forEach(view -> views.add(view.visible()));
    input.set("referenceOptions", readingReferenceOptions(navigationViews, packetView));
    Decision result =
        call(
            "READING",
            input,
            "reading-v3.txt",
            "reading-v2.schema.json",
            OntologyNavigationView.sourceBasis(sourceIdentity, visible));
    try {
      JsonNode output = json.parseCanonical(result.output());
      if (!questionId.equals(output.path("questionId").asText())) {
        throw new IllegalArgumentException("ONTOLOGY_READING_QUESTION_MISMATCH");
      }
      for (JsonNode ref : output.path("retainedRefs")) {
        OntologyNavigationView.Target target =
            OntologyNavigationView.resolve(visible, OntologyNavigationView.parse(ref));
        if (target.kind() != OntologyNavigationView.ReferenceKind.PACKET_UNIT) {
          throw new IllegalArgumentException("ONTOLOGY_READING_RETENTION_REFERENCE_INVALID");
        }
      }
      for (JsonNode requested : output.path("readRequests")) {
        OntologyNavigationView.Target target =
            OntologyNavigationView.resolve(
                visible, OntologyNavigationView.parse(requested.path("unitRef")));
        if (target.kind() != OntologyNavigationView.ReferenceKind.UNIT) {
          throw new IllegalArgumentException("ONTOLOGY_READING_UNIT_REFERENCE_INVALID");
        }
      }
      for (JsonNode query : output.path("queries")) {
        validateQuery(
            query.path("kind").asText(),
            OntologyNavigationView.resolve(
                visible, OntologyNavigationView.parse(query.path("keyRef"))));
      }
      if (packet == null
          && ("READY_TO_EXTRACT".equals(output.path("decision").asText())
              || output.path("retainedRefs").size() > 0)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_EMPTY_PACKET_INVALID");
      }
      if ("READY_TO_EXTRACT".equals(output.path("decision").asText())
          && (output.path("readRequests").size() > 0
              || output.path("queries").size() > 0
              || output.path("literalSearches").size() > 0)) {
        throw new IllegalArgumentException("ONTOLOGY_READING_READY_WITH_REQUEST_INVALID");
      }
      navigationViewsByDecisionKey.put(result.jobKey(), List.copyOf(visible));
      saveSuccess("reading", input, result, sourceIdentity, visible);
      return result;
    } catch (RuntimeException invalid) {
      if (store != null) {
        store.failed(result.jobKey(), invalid);
      }
      throw invalid;
    }
  }

  private ObjectNode readingReferenceOptions(
      List<OntologyNavigationView> navigationViews, OntologyNavigationView packetView) {
    ObjectNode options = mapper.createObjectNode();
    ArrayNode retainable = options.putArray("retainablePacketUnits");
    ArrayNode readable = options.putArray("readableNavigationUnits");
    ObjectNode queryKeys = options.putObject("queryKeys");
    List<String> queryKinds =
        List.of(
            "ENTRY_UNITS",
            "METHOD_USES",
            "STATEMENT_USES",
            "TABLE_STATEMENTS",
            "COLUMN_STATEMENTS");
    queryKinds.forEach(queryKeys::putArray);
    if (packetView != null) {
      addReadingChoices(packetView, retainable, null, queryKeys, queryKinds);
    }
    for (OntologyNavigationView view : navigationViews) {
      addReadingChoices(view, null, readable, queryKeys, queryKinds);
    }
    return options;
  }

  private void addReadingChoices(
      OntologyNavigationView view,
      ArrayNode retainable,
      ArrayNode readable,
      ObjectNode queryKeys,
      List<String> queryKinds) {
    view.visibleReferenceTargets().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              ObjectNode ref = mapper.createObjectNode();
              ref.put("viewId", view.modelViewId());
              ref.put("ref", entry.getKey());
              OntologyNavigationView.Target target = entry.getValue();
              if (retainable != null
                  && target.kind() == OntologyNavigationView.ReferenceKind.PACKET_UNIT) {
                retainable.add(ref.deepCopy());
              }
              if (readable != null && target.kind() == OntologyNavigationView.ReferenceKind.UNIT) {
                readable.add(ref.deepCopy());
              }
              for (String queryKind : queryKinds) {
                if (validQueryReference(queryKind, target)) {
                  ((ArrayNode) queryKeys.path(queryKind)).add(ref.deepCopy());
                }
              }
            });
  }

  private static void validateQuery(String kind, OntologyNavigationView.Target target) {
    if (!validQueryReference(kind, target)) {
      throw new IllegalArgumentException("ONTOLOGY_READING_QUERY_REFERENCE_INVALID");
    }
  }

  private static boolean validQueryReference(String kind, OntologyNavigationView.Target target) {
    return switch (kind) {
      case "ENTRY_UNITS" -> target.kind() == OntologyNavigationView.ReferenceKind.ENTRY;
      case "METHOD_USES" ->
          (target.kind() == OntologyNavigationView.ReferenceKind.CLUE
                  && target.clue().kind() == OntologyEvidenceCorpus.ClueKind.METHOD)
              || (target.kind() == OntologyNavigationView.ReferenceKind.PACKET_UNIT
                  && target.packet().kind() == OntologyEvidenceCorpus.UnitKind.JAVA_METHOD);
      case "STATEMENT_USES" ->
          (target.kind() == OntologyNavigationView.ReferenceKind.CLUE
                  && target.clue().kind() == OntologyEvidenceCorpus.ClueKind.STATEMENT)
              || (target.kind() == OntologyNavigationView.ReferenceKind.PACKET_UNIT
                  && target.packet().kind() == OntologyEvidenceCorpus.UnitKind.XML_STATEMENT);
      case "TABLE_STATEMENTS" ->
          target.kind() == OntologyNavigationView.ReferenceKind.CLUE
              && target.clue().kind() == OntologyEvidenceCorpus.ClueKind.TABLE;
      case "COLUMN_STATEMENTS" ->
          target.kind() == OntologyNavigationView.ReferenceKind.CLUE
              && target.clue().kind() == OntologyEvidenceCorpus.ClueKind.COLUMN;
      default -> false;
    };
  }

  /** Selects a bounded set of questions only after every navigation page has been surveyed. */
  public Decision prioritize(
      String scope,
      String sourceIdentity,
      List<Decision> surveys,
      int maxQuestions,
      int maxTaskKindsPerQuestion) {
    return prioritize(
        scope,
        sourceIdentity,
        surveys,
        maxQuestions,
        maxTaskKindsPerQuestion,
        "prioritize-v3.txt",
        false);
  }

  public Decision prioritizeAssemblableScope(
      String scope, String sourceIdentity, List<Decision> surveys) {
    return prioritize(scope, sourceIdentity, surveys, 4, 4, "prioritize-assemblable-v1.txt", true);
  }

  private Decision prioritize(
      String scope,
      String sourceIdentity,
      List<Decision> surveys,
      int maxQuestions,
      int maxTaskKindsPerQuestion,
      String promptFile,
      boolean requireAssemblableCoverage) {
    if (scope == null
        || scope.isBlank()
        || sourceIdentity == null
        || sourceIdentity.isBlank()
        || surveys == null
        || surveys.isEmpty()
        || maxQuestions < 1
        || maxTaskKindsPerQuestion < 1) {
      throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_INPUT_INVALID");
    }
    ObjectNode input = mapper.createObjectNode();
    input.put("schemaVersion", "ontology-prioritize-input-v3");
    input.put("scope", scope);
    input.put("sourceIdentity", sourceIdentity);
    input.put("maxQuestions", maxQuestions);
    input.put("maxTaskKindsPerQuestion", maxTaskKindsPerQuestion);
    if (requireAssemblableCoverage) {
      ArrayNode required = input.putArray("requiredTaskKinds");
      required.add("OBJECT").add("RELATE").add("ACTION").add("ANALYTIC");
    }
    ArrayNode pages = input.putArray("pages");
    Set<String> known = new LinkedHashSet<>();
    Map<String, JsonNode> surveyQuestionByKey = new LinkedHashMap<>();
    Map<String, OntologyNavigationView> surveyViewByQuestionKey = new LinkedHashMap<>();
    List<OntologyNavigationView> originalSurveyViews = new java.util.ArrayList<>();
    int nextOffset = 0;
    int total = -1;
    for (Decision survey : surveys) {
      if (!"SURVEY".equals(survey.kind())) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_SURVEY_INVALID");
      }
      JsonNode surveyInput = json.parseCanonical(survey.input());
      JsonNode navigation = surveyInput.path("navigationView");
      JsonNode surveyOutput = json.parseCanonical(survey.output());
      int offset = navigation.path("pageOffset").asInt(-1);
      int pageTotal = navigation.path("pageDisclosure").path("totalEntries").asInt(-1);
      int count = navigation.path("pageDisclosure").path("shownEntries").asInt(-1);
      if (!scope.equals(surveyInput.path("scope").asText())
          || !sourceIdentity.equals(surveyInput.path("sourceIdentity").asText())
          || offset != nextOffset
          || pageTotal < 0
          || (total >= 0 && total != pageTotal)
          || count < 1) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_SURVEY_INVALID");
      }
      total = pageTotal;
      nextOffset += count;
      List<OntologyNavigationView> surveyViews = viewsFor(survey);
      if (surveyViews.size() != 1) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_SURVEY_INVALID");
      }
      OntologyNavigationView surveyView = surveyViews.get(0);
      originalSurveyViews.add(surveyView);
      for (JsonNode question : surveyOutput.path("questions")) {
        String questionKey = questionKey(offset, question.path("questionId").asText());
        if (!known.add(questionKey)) {
          throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_SURVEY_INVALID");
        }
        surveyQuestionByKey.put(questionKey, question.deepCopy());
        surveyViewByQuestionKey.put(questionKey, surveyView);
      }
      ObjectNode item = pages.addObject();
      item.put("pageOffset", offset);
      item.put("entryCount", count);
      item.put("totalEntries", total);
      item.set("survey", surveyOutput);
    }
    if (nextOffset != total) {
      throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_UNREAD_PAGES");
    }
    Decision result =
        call(
            "PRIORITIZE",
            input,
            promptFile,
            "prioritize-v3.schema.json",
            "ontology-prioritize-v3",
            OntologyNavigationView.sourceBasis(sourceIdentity, originalSurveyViews));
    try {
      JsonNode output = json.parseCanonical(result.output());
      Set<String> handled = new HashSet<>();
      Map<String, List<OntologyNavigationView>> selectedQuestionViews = new LinkedHashMap<>();
      Map<String, Set<OntologyNavigationView.ViewRef>> selectedRefsByView = new LinkedHashMap<>();
      Map<String, OntologyNavigationView> originalViewById = new LinkedHashMap<>();
      if (output.path("selectedQuestions").size() > maxQuestions) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_LIMIT_EXCEEDED");
      }
      for (JsonNode selected : output.path("selectedQuestions")) {
        if (selected.path("taskKinds").size() > maxTaskKindsPerQuestion) {
          throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_TASK_LIMIT_EXCEEDED");
        }
        if (!handled.add(
            selected.path("pageOffset").asInt(-1) + ":" + selected.path("questionId").asText())) {
          throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_DUPLICATE");
        }
        String selectedKey =
            questionKey(
                selected.path("pageOffset").asInt(-1), selected.path("questionId").asText());
        JsonNode originalQuestion = surveyQuestionByKey.get(selectedKey);
        OntologyNavigationView originalView = surveyViewByQuestionKey.get(selectedKey);
        if (originalQuestion == null || originalView == null) {
          throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
        }
        requireSelectedCandidateReferences(
            selected.path("candidateEntryRefs"), originalQuestion, originalView);
        Set<OntologyNavigationView.ViewRef> questionRefs =
            questionNavigationReferences(originalQuestion);
        selectedQuestionViews.put(selectedKey, List.of(originalView.project(questionRefs)));
        originalViewById.putIfAbsent(originalView.modelViewId(), originalView);
        selectedRefsByView
            .computeIfAbsent(originalView.modelViewId(), unused -> new LinkedHashSet<>())
            .addAll(questionRefs);
      }
      for (JsonNode deferred : output.path("deferredQuestions")) {
        if (!handled.add(
            deferred.path("pageOffset").asInt(-1) + ":" + deferred.path("questionId").asText())) {
          throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_DUPLICATE");
        }
      }
      if (!known.containsAll(handled)) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
      }
      ObjectNode normalized = output.deepCopy();
      ArrayNode deferred = (ArrayNode) normalized.path("deferredQuestions");
      for (String key : known) {
        if (handled.add(key)) {
          int separator = key.indexOf(':');
          ObjectNode implicit = deferred.addObject();
          implicit.put("pageOffset", Integer.parseInt(key.substring(0, separator)));
          implicit.put("questionId", key.substring(separator + 1));
          implicit.put("reason", "NOT_SELECTED_WITHIN_EXPERIMENT_LIMIT");
        }
      }
      Decision completed =
          new Decision(
              result.kind(),
              result.jobKey(),
              result.input(),
              json.encodeCanonical(normalized),
              result.runtimeIdentity());
      List<OntologyNavigationView> combinedSelectedViews = new java.util.ArrayList<>();
      selectedRefsByView.forEach(
          (viewId, references) ->
              combinedSelectedViews.add(originalViewById.get(viewId).project(references)));
      navigationViewsByDecisionKey.put(completed.jobKey(), List.copyOf(combinedSelectedViews));
      selectedQuestionViews.forEach(
          (key, views) ->
              navigationViewsBySelectedQuestion.put(
                  selectedQuestionKey(completed, key), List.copyOf(views)));
      saveSuccess("prioritize", input, completed, sourceIdentity, originalSurveyViews);
      return completed;
    } catch (RuntimeException invalid) {
      if (store != null) {
        store.failed(result.jobKey(), invalid);
      }
      throw invalid;
    }
  }

  private Decision call(
      String kind, ObjectNode input, String promptName, String schemaName, JsonNode sourceBasis) {
    return call(
        kind,
        input,
        promptName,
        schemaName,
        "ontology-" + kind.toLowerCase(java.util.Locale.ROOT) + "-v2",
        sourceBasis);
  }

  private Decision call(
      String kind,
      ObjectNode input,
      String promptName,
      String schemaName,
      String expectedOutputSchemaVersion,
      JsonNode sourceBasis) {
    String prompt = resource(promptName);
    ImmutableBytes validationSchema =
        json.canonicalizeStrictJson(
            ImmutableBytes.copyOf(resource(schemaName).getBytes(StandardCharsets.UTF_8)));
    return call(kind, input, prompt, validationSchema, expectedOutputSchemaVersion, sourceBasis);
  }

  private Decision call(
      String kind,
      ObjectNode input,
      String prompt,
      ImmutableBytes validationSchema,
      String expectedOutputSchemaVersion,
      JsonNode sourceBasis) {
    return call(
        kind, input, prompt, validationSchema, expectedOutputSchemaVersion, sourceBasis, null);
  }

  private Decision call(
      String kind,
      ObjectNode input,
      String prompt,
      ImmutableBytes validationSchema,
      String expectedOutputSchemaVersion,
      JsonNode sourceBasis,
      Integer requestedMaxOutputTokens) {
    return execute(
        prepare(
            kind,
            input,
            prompt,
            validationSchema,
            expectedOutputSchemaVersion,
            sourceBasis,
            requestedMaxOutputTokens));
  }

  private PreparedDecision prepare(
      String kind,
      ObjectNode input,
      String prompt,
      ImmutableBytes validationSchema,
      String expectedOutputSchemaVersion,
      JsonNode sourceBasis,
      Integer requestedMaxOutputTokens) {
    ImmutableBytes canonicalInput = json.encodeCanonical(input);
    ImmutableBytes canonicalValidationSchema = json.canonicalizeStrictJson(validationSchema);
    ImmutableBytes providerSchema = OntologyProviderSchema.from(canonicalValidationSchema);
    long envelope =
        (long) canonicalInput.size()
            + prompt.getBytes(StandardCharsets.UTF_8).length
            + providerSchema.size()
            + maxOutputBytes;
    if (envelope > maxRequestBytes) {
      throw new IllegalArgumentException("ONTOLOGY_TASK_INPUT_TOO_LARGE");
    }
    ObjectNode identity = mapper.createObjectNode();
    identity.put("kind", kind);
    identity.set("input", json.parseCanonical(canonicalInput));
    identity.put("prompt", prompt);
    identity.set("validationSchema", json.parseCanonical(canonicalValidationSchema));
    identity.set("providerSchema", json.parseCanonical(providerSchema));
    if (sourceBasis != null) {
      identity.set("sourceBasis", sourceBasis);
    }
    identity.put("maxOutputBytes", maxOutputBytes);
    if (requestedMaxOutputTokens != null) {
      identity.put("requestedMaxOutputTokens", requestedMaxOutputTokens);
    }
    String digest = OntologyReadingPacket.sha256(json.encodeCanonical(identity).copyToByteArray());
    String jobKey = "ontology-decision-" + digest;
    StructuredModelRequest request =
        new StructuredModelRequest(
            "ontology-" + kind.toLowerCase(java.util.Locale.ROOT) + "-" + digest.substring(0, 20),
            "ONTOLOGY_" + kind,
            prompt,
            canonicalInput,
            providerSchema,
            maxOutputBytes,
            requestedMaxOutputTokens);
    return new PreparedDecision(
        kind,
        jobKey,
        canonicalInput,
        request,
        canonicalValidationSchema,
        expectedOutputSchemaVersion);
  }

  private void saveSuccess(
      String stage,
      ObjectNode input,
      Decision decision,
      String sourceIdentity,
      List<OntologyNavigationView> views) {
    if (store != null) {
      JsonNode output = json.parseCanonical(decision.output());
      store.success(decision.jobKey(), stage, output, decision.runtimeIdentity());
      store.decision(
          decision.jobKey(),
          stage,
          input,
          output,
          decision.runtimeIdentity(),
          OntologyNavigationView.sourceBasis(sourceIdentity, views));
    }
  }

  List<OntologyNavigationView> viewsFor(Decision decision) {
    List<OntologyNavigationView> views = navigationViewsByDecisionKey.get(decision.jobKey());
    if (views == null) {
      throw new IllegalArgumentException("ONTOLOGY_DECISION_VIEW_UNAVAILABLE");
    }
    return views;
  }

  List<OntologyNavigationView> viewsFor(Decision decision, int pageOffset, String questionId) {
    List<OntologyNavigationView> views =
        navigationViewsBySelectedQuestion.get(
            selectedQuestionKey(decision, questionKey(pageOffset, questionId)));
    if (views == null) {
      throw new IllegalArgumentException("ONTOLOGY_DECISION_VIEW_UNAVAILABLE");
    }
    return views;
  }

  UnitHandle resolveUnit(Decision decision, JsonNode reference) {
    OntologyNavigationView.Target target =
        OntologyNavigationView.resolve(viewsFor(decision), OntologyNavigationView.parse(reference));
    if (target.kind() != OntologyNavigationView.ReferenceKind.UNIT) {
      throw new IllegalArgumentException("ONTOLOGY_VIEW_REFERENCE_CATEGORY_INVALID");
    }
    return target.unit();
  }

  void saveReadingObservation(
      Decision decision,
      int round,
      String questionId,
      String sourceIdentity,
      OntologyReadingCoordinator.Status status,
      OntologyReadingPacket packet,
      List<OntologyNavigationView> navigationViews,
      List<String> unresolved,
      String issueCode) {
    if (!"READING".equals(decision.kind())) {
      throw new IllegalArgumentException("ONTOLOGY_READING_OBSERVATION_INVALID");
    }
    if (store != null) {
      store.readingObservation(
          decision.jobKey(),
          round,
          questionId,
          sourceIdentity,
          status,
          packet,
          navigationViews,
          unresolved,
          issueCode);
    }
  }

  void saveInitialReadingObservation(
      String questionId,
      String question,
      String sourceIdentity,
      OntologyReadingPacket packet,
      List<OntologyNavigationView> navigationViews) {
    if (store != null) {
      store.initialReadingObservation(
          questionId, question, sourceIdentity, packet, navigationViews);
    }
  }

  void saveFormalReadingState(
      FormalDecision decision, OntologyReadingCoordinator.FormalResult result) {
    if (store != null && decision != null) {
      store.formalReadingState(
          decision.decision().jobKey(),
          result.status(),
          result.state(),
          result.unresolved(),
          result.issueCode(),
          result.frozenPacket(),
          json.parseCanonical(decision.decision().input()).path("visibleScope"),
          "ontology-reading-input-v4"
              .equals(
                  json.parseCanonical(decision.decision().input()).path("schemaVersion").asText()));
    }
  }

  /** Persists a formal state that ended before a validated model decision existed. */
  void saveFormalReadingObservation(
      OntologyReadingCoordinator.FormalQuestion question,
      OntologyReadingCoordinator.FormalTask task,
      OntologyEvidenceCorpus corpus,
      OntologyReadingCoordinator.FormalResult result,
      OntologyReadingPacket material,
      OntologyReadingCoordinator.FormalVisibleScope dispatchedVisibleScope,
      StructuredModelProviderFailure failure) {
    if (store == null) {
      return;
    }
    ObjectNode sourceBasis = formalSourceBasis(corpus);
    ObjectNode identity = mapper.createObjectNode();
    identity.put("phase", "formal-reading-observation-v1");
    identity.put("questionId", question.questionId());
    identity.put("question", question.question());
    identity.put("taskId", task.taskId());
    identity.put("taskKind", task.taskKind().name());
    identity.put("readingMode", task.readingMode().name());
    ObjectNode scope = identity.putObject("scope");
    scope.set("entryRefs", mapper.valueToTree(question.entryRefs()));
    scope.set("clueRefs", mapper.valueToTree(question.clueRefs()));
    scope.set("unitUses", mapper.valueToTree(task.unitUses()));
    scope.set("requiredUnitUses", mapper.valueToTree(task.requiredUnitUses()));
    identity.set("sourceBasis", sourceBasis);
    identity.set("state", mapper.valueToTree(result.state()));
    identity.put("status", result.status().name());
    identity.put("issueCode", result.issueCode());
    if (result.bundleDecision() != null) {
      identity.set("bundleDecision", result.bundleDecision());
    }
    if (material == null) {
      identity.putNull("readingPacket");
    } else {
      identity.set("readingPacket", json.parseCanonical(material.canonicalInput()));
    }
    String jobKey =
        "ontology-formal-observation-"
            + OntologyReadingPacket.sha256(json.encodeCanonical(identity).copyToByteArray());
    String observationKind =
        failure == null
            ? "PREPARATION"
            : failure.requestStarted()
                ? "PROVIDER_FAILURE_DISPATCHED"
                : "PROVIDER_FAILURE_PRE_DISPATCH";
    JsonNode visibleScope =
        dispatchedVisibleScope == null
            ? null
            : visibleDocument(
                dispatchedVisibleScope, dispatchedVisibleScope.activeUnits().stream().toList());
    store.formalReadingObservation(
        jobKey,
        observationKind,
        result.status(),
        result.state(),
        result.unresolved(),
        result.issueCode(),
        material,
        visibleScope,
        identity,
        sourceBasis,
        failure,
        corpus.usesBusinessLinkNavigation());
  }

  private static Set<OntologyNavigationView.ViewRef> questionNavigationReferences(
      JsonNode surveyQuestion) {
    Set<OntologyNavigationView.ViewRef> references = new LinkedHashSet<>();
    surveyQuestion
        .path("candidateEntryRefs")
        .forEach(reference -> references.add(OntologyNavigationView.parse(reference)));
    surveyQuestion
        .path("clueRefs")
        .forEach(reference -> references.add(OntologyNavigationView.parse(reference)));
    return references;
  }

  private static void requireSelectedCandidateReferences(
      JsonNode selectedReferences, JsonNode surveyQuestion, OntologyNavigationView surveyView) {
    Set<OntologyNavigationView.ViewRef> permitted = new LinkedHashSet<>();
    surveyQuestion
        .path("candidateEntryRefs")
        .forEach(reference -> permitted.add(OntologyNavigationView.parse(reference)));
    for (JsonNode reference : selectedReferences) {
      OntologyNavigationView.ViewRef parsed = OntologyNavigationView.parse(reference);
      if (!permitted.contains(parsed)
          || OntologyNavigationView.resolve(List.of(surveyView), parsed).kind()
              != OntologyNavigationView.ReferenceKind.ENTRY) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
      }
    }
  }

  private static String questionKey(int pageOffset, String questionId) {
    if (pageOffset < 0 || questionId == null || !questionId.matches("Q[0-9]+")) {
      throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
    }
    return pageOffset + ":" + questionId;
  }

  private static String selectedQuestionKey(Decision decision, String questionKey) {
    return decision.jobKey() + "\u0000" + questionKey;
  }

  private static void requireReferences(
      JsonNode references,
      List<OntologyNavigationView> views,
      OntologyNavigationView.ReferenceKind expectedKind) {
    for (JsonNode reference : references) {
      OntologyNavigationView.Target target =
          OntologyNavigationView.resolve(views, OntologyNavigationView.parse(reference));
      if (target.kind() != expectedKind) {
        throw new IllegalArgumentException("ONTOLOGY_VIEW_REFERENCE_CATEGORY_INVALID");
      }
    }
  }

  /** Repairs only a copied view ID when the short reference resolves uniquely in this one page. */
  private JsonNode normalizeSurveyReferences(
      JsonNode rawOutput, OntologyNavigationView view, ArrayNode corrections) {
    ObjectNode normalized = rawOutput.deepCopy();
    String actualViewId = view.visible().path("viewId").asText();
    for (JsonNode hypothesis : normalized.path("systemHypotheses")) {
      normalizeSurveyArray(
          hypothesis.path("observedEntryRefs"),
          view,
          actualViewId,
          OntologyNavigationView.ReferenceKind.ENTRY,
          "systemHypotheses.observedEntryRefs",
          corrections);
    }
    for (JsonNode question : normalized.path("questions")) {
      String questionId = question.path("questionId").asText();
      normalizeSurveyArray(
          question.path("candidateEntryRefs"),
          view,
          actualViewId,
          OntologyNavigationView.ReferenceKind.ENTRY,
          questionId + ".candidateEntryRefs",
          corrections);
      normalizeSurveyArray(
          question.path("clueRefs"),
          view,
          actualViewId,
          OntologyNavigationView.ReferenceKind.CLUE,
          questionId + ".clueRefs",
          corrections);
    }
    return normalized;
  }

  private static void normalizeSurveyArray(
      JsonNode references,
      OntologyNavigationView view,
      String actualViewId,
      OntologyNavigationView.ReferenceKind expectedKind,
      String location,
      ArrayNode corrections) {
    for (JsonNode reference : references) {
      OntologyNavigationView.ViewRef parsed = OntologyNavigationView.parse(reference);
      if (actualViewId.equals(parsed.viewId())) {
        continue;
      }
      OntologyNavigationView.Target target =
          view.resolve(new OntologyNavigationView.ViewRef(actualViewId, parsed.ref()));
      if (target.kind() != expectedKind) {
        throw new IllegalArgumentException("ONTOLOGY_VIEW_REFERENCE_CATEGORY_INVALID");
      }
      ((ObjectNode) reference).put("viewId", actualViewId);
      ObjectNode correction = corrections.addObject();
      correction.put("location", location);
      correction.put("ref", parsed.ref());
      correction.put("originalViewId", parsed.viewId());
      correction.put("correctedViewId", actualViewId);
      correction.put("expectedKind", expectedKind.name());
    }
  }

  private static String resource(String name) {
    try (InputStream input = OntologyDecisionRunner.class.getResourceAsStream(ROOT + name)) {
      if (input == null) {
        throw new IllegalStateException("ONTOLOGY_TASK_RESOURCE_MISSING");
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (IOException unreadable) {
      throw new IllegalStateException("ONTOLOGY_TASK_RESOURCE_UNREADABLE", unreadable);
    }
  }

  private static FormalReadingMaterial defaultFormalReadingMaterial() {
    return new FormalReadingMaterial(
        resource("formal-reading-v1.txt"),
        ImmutableBytes.copyOf(
            resource("formal-reading-v1.schema.json").getBytes(StandardCharsets.UTF_8)));
  }

  public record Decision(
      String kind,
      String jobKey,
      ImmutableBytes input,
      ImmutableBytes output,
      ModelRuntimeIdentityV1 runtimeIdentity) {}

  /** A normal Provider response failed only the exact decision-output contract. */
  public static final class DecisionModelOutputFailure extends RuntimeException {
    private final String code;
    private final String stage;

    private DecisionModelOutputFailure(String code, String stage, Throwable cause) {
      super(code, cause);
      if (code == null || code.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_DECISION_FAILURE_CODE_INVALID");
      }
      this.code = code;
      this.stage = stage;
    }

    public String code() {
      return code;
    }

    public String stage() {
      return stage;
    }
  }

  private static String formalDecisionStage(String kind) {
    return kind.startsWith("FORMAL_") ? kind.substring("FORMAL_".length()) : kind;
  }

  /**
   * A returned reading decision is invalid locally; source, storage, and Provider failures differ.
   */
  public static final class FormalReadingModelOutputFailure extends RuntimeException {
    private final OntologyTaskOutcome.FailureReason reason;

    private FormalReadingModelOutputFailure(String code, Throwable cause) {
      super(code, cause);
      reason =
          new OntologyTaskOutcome.FailureReason(
              code,
              OntologyTaskOutcome.Category.MODEL_OUTPUT,
              "READING",
              null,
              null,
              List.of(),
              List.of());
    }

    public OntologyTaskOutcome.FailureReason reason() {
      return reason;
    }
  }

  /**
   * Exact provider-free decision envelope, including the request identity validated at dispatch.
   */
  public record PreparedDecision(
      String kind,
      String jobKey,
      ImmutableBytes input,
      StructuredModelRequest request,
      ImmutableBytes validationSchema,
      String expectedOutputSchemaVersion) {
    public PreparedDecision {
      if (kind == null
          || kind.isBlank()
          || jobKey == null
          || jobKey.isBlank()
          || input == null
          || request == null
          || validationSchema == null
          || expectedOutputSchemaVersion == null
          || expectedOutputSchemaVersion.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_DECISION_PREPARATION_INVALID");
      }
    }
  }

  /** One program-assigned survey question and its exact visible E/K source mapping. */
  public record FormalSurveyQuestion(
      String questionRef, String question, List<String> candidateEntryRefs, List<String> clueRefs) {
    public FormalSurveyQuestion {
      if (questionRef == null
          || !questionRef.matches("Q[1-9][0-9]*")
          || question == null
          || question.isBlank()
          || candidateEntryRefs == null
          || clueRefs == null) {
        throw new IllegalArgumentException("ONTOLOGY_SURVEY_QUESTION_INVALID");
      }
      candidateEntryRefs = List.copyOf(candidateEntryRefs);
      clueRefs = List.copyOf(clueRefs);
    }
  }

  /** Immutable configured prompt/schema material for a formal non-typed decision. */
  public record FormalDecisionMaterial(
      String prompt, ImmutableBytes validationSchema, Integer requestedMaxOutputTokens) {
    public FormalDecisionMaterial(String prompt, ImmutableBytes validationSchema) {
      this(prompt, validationSchema, null);
    }

    public FormalDecisionMaterial {
      if (prompt == null
          || prompt.isBlank()
          || validationSchema == null
          || (requestedMaxOutputTokens != null && requestedMaxOutputTokens < 1)) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_DECISION_MATERIAL_INVALID");
      }
      validationSchema = new CanonicalJsonCodec().canonicalizeStrictJson(validationSchema);
    }
  }

  /** Immutable prompt/schema snapshot whose content is included in each formal job identity. */
  public record FormalReadingMaterial(
      String prompt,
      ImmutableBytes validationSchema,
      String contextIdentity,
      Integer requestedMaxOutputTokens) {
    public FormalReadingMaterial(String prompt, ImmutableBytes validationSchema) {
      this(prompt, validationSchema, null, null);
    }

    public FormalReadingMaterial(
        String prompt, ImmutableBytes validationSchema, String contextIdentity) {
      this(prompt, validationSchema, contextIdentity, null);
    }

    public FormalReadingMaterial {
      if (prompt == null
          || prompt.isBlank()
          || validationSchema == null
          || (contextIdentity != null && contextIdentity.isBlank())
          || (requestedMaxOutputTokens != null && requestedMaxOutputTokens < 1)) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_READING_MATERIAL_INVALID");
      }
      validationSchema = new CanonicalJsonCodec().canonicalizeStrictJson(validationSchema);
    }
  }

  record FormalDecision(Decision decision, String prompt, ImmutableBytes validationSchema) {}
}
