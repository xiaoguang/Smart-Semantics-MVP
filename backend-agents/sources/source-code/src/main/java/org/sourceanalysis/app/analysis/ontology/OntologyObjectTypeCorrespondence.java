package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.analysis.ontology.OntologyTypedTaskRunner.FormalResult;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Narrow candidate and material preparation. A candidate never authorizes type unification. */
public final class OntologyObjectTypeCorrespondence {
  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Comparator<Endpoint> ORDER = Comparator.comparing(Endpoint::identityKey);

  private OntologyObjectTypeCorrespondence() {}

  public record ReviewedObjects(String identificationRun, FormalResult result) {
    public ReviewedObjects {
      if (identificationRun == null
          || identificationRun.isBlank()
          || result == null
          || result.status() != OntologyTypedTaskRunner.FormalStatus.REVIEWED)
        throw new IllegalArgumentException("ONTOLOGY_OBJECT_TYPE_SOURCE_INVALID");
    }
  }

  public record Endpoint(String identificationRun, FormalResult result, String localId) {
    public Endpoint {
      if (identificationRun == null
          || identificationRun.isBlank()
          || result == null
          || localId == null
          || localId.isBlank())
        throw new IllegalArgumentException("ONTOLOGY_OBJECT_TYPE_ENDPOINT_INVALID");
    }

    public JsonNode definition() {
      for (JsonNode object : result.definitionDocument().path("definitions").path("objects"))
        if (localId.equals(object.path("localId").asText())) return object.deepCopy();
      throw new IllegalArgumentException("ONTOLOGY_OBJECT_TYPE_ENDPOINT_INVALID");
    }

    private String identityKey() {
      return result.identity().corpusIdentity()
          + "\u0000"
          + identificationRun
          + "\u0000"
          + result.identity().producingTaskId()
          + "\u0000"
          + result.identity().reviewVersion()
          + "\u0000"
          + localId;
    }
  }

  public record Candidate(String pairRef, Endpoint left, Endpoint right, List<String> signals) {
    public Candidate {
      signals = List.copyOf(signals);
    }
  }

  public record Prepared(OntologyReadingPacket packet, ImmutableBytes binding) {}

  public record Plan(
      OntologySelectionReader.Selection selection,
      Map<String, Candidate> candidates,
      ImmutableBytes rows) {
    public Plan {
      candidates = Map.copyOf(candidates);
    }
  }

  /** Expand only the declared questions using their actually reviewed source tasks. */
  public static Plan plan(
      OntologySelectionReader.Selection selection,
      Function<OntologySelectionReader.Question, List<ReviewedObjects>> sources) {
    Map<String, Candidate> candidates = new LinkedHashMap<>();
    ArrayNode rows = MAPPER.createArrayNode();
    if (selection.relationProfile()
        != OntologySelectionReader.RelationProfile.OBJECT_TYPE_CORRESPONDENCE)
      return new Plan(selection, Map.of(), JSON.encodeCanonical(rows));
    List<OntologySelectionReader.Question> questions = new ArrayList<>();
    for (var parent : selection.questions()) {
      List<ReviewedObjects> reviewed = sources.apply(parent);
      List<Candidate> pairs = reviewed == null ? List.of() : candidates(reviewed);
      if (pairs.isEmpty()) {
        questions.add(parent);
        continue;
      }
      for (var pair : pairs) {
        String taskId = parent.taskId() + ":" + pair.pairRef();
        questions.add(
            new OntologySelectionReader.Question(
                parent.questionId() + ":" + pair.pairRef(),
                parent.question(),
                taskId,
                parent.readingMode(),
                parent.entryRefs(),
                List.of(),
                parent.unitUses(),
                parent.requiredUnitUses(),
                parent.objectSources()));
        if (candidates.putIfAbsent(taskId, pair) != null)
          throw new IllegalArgumentException("ONTOLOGY_OBJECT_TYPE_CANDIDATE_INVALID");
        ObjectNode row = rows.addObject();
        row.put("parentQuestionId", parent.questionId());
        row.put("declaredTaskId", parent.taskId());
        row.put("taskId", taskId);
        row.put("pairRef", pair.pairRef());
        ArrayNode signals = row.putArray("signals");
        pair.signals().forEach(signals::add);
        row.set("left", endpointDocument(pair.left()));
        row.set("right", endpointDocument(pair.right()));
      }
    }
    return new Plan(
        new OntologySelectionReader.Selection(
            selection.schemaVersion(),
            selection.operation(),
            selection.corpusRun(),
            selection.identificationRuns(),
            selection.relationRuns(),
            questions,
            selection.relationProfile()),
        candidates,
        JSON.encodeCanonical(rows));
  }

  private static ObjectNode endpointDocument(Endpoint endpoint) {
    ObjectNode row = MAPPER.createObjectNode();
    row.put("identificationRun", endpoint.identificationRun());
    row.put("questionId", endpoint.result().questionId());
    row.put("corpusIdentity", endpoint.result().identity().corpusIdentity());
    row.put("producingTaskId", endpoint.result().identity().producingTaskId());
    row.put("reviewVersion", endpoint.result().identity().reviewVersion());
    row.put("localId", endpoint.localId());
    return row;
  }

  public static OntologyTaskOutcome.FailureReason preparationFailure(
      boolean hasCandidate,
      int dispatched,
      int maximum,
      List<OntologyTaskOutcome.TaskReference> dependencies) {
    if (!hasCandidate)
      return new OntologyTaskOutcome.FailureReason(
          "NO_TYPE_CANDIDATES",
          OntologyTaskOutcome.Category.MATERIAL,
          "PREPARE",
          null,
          null,
          List.of(),
          List.of());
    if (dispatched + 2 > maximum)
      return new OntologyTaskOutcome.FailureReason(
          OntologyCallBudgetProvider.DispatchLimitExceeded.CODE,
          OntologyTaskOutcome.Category.DISPATCH_LIMIT,
          "PREPARE",
          null,
          null,
          List.of(),
          dependencies);
    return null;
  }

  public static List<Candidate> candidates(List<ReviewedObjects> sources) {
    List<Endpoint> endpoints = new ArrayList<>();
    String corpus = null;
    String content = null;
    for (ReviewedObjects source : sources) {
      FormalResult result = source.result();
      if (corpus == null) {
        corpus = result.identity().corpusIdentity();
        content = result.packet().sourceIdentity();
      } else if (!corpus.equals(result.identity().corpusIdentity())
          || !content.equals(result.packet().sourceIdentity()))
        throw new IllegalArgumentException("ONTOLOGY_OBJECT_TYPE_SOURCE_INVALID");
      for (JsonNode object : result.definitionDocument().path("definitions").path("objects"))
        endpoints.add(
            new Endpoint(source.identificationRun(), result, object.path("localId").asText()));
    }
    endpoints.sort(ORDER);
    List<Candidate> candidates = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (int i = 0; i < endpoints.size(); i++)
      for (int j = i + 1; j < endpoints.size(); j++) {
        Endpoint left = endpoints.get(i);
        Endpoint right = endpoints.get(j);
        if (left.result().identity().equals(right.result().identity())) continue;
        List<String> signals = new ArrayList<>();
        String name = left.definition().path("name").asText();
        if (!name.isBlank() && name.equals(right.definition().path("name").asText()))
          signals.add("EXACT_NAME");
        Set<String> backing = bindings(left.definition());
        backing.retainAll(bindings(right.definition()));
        if (!backing.isEmpty()) signals.add("EXACT_BACKING");
        Set<String> technical = technicalRefs(left);
        technical.retainAll(technicalRefs(right));
        if (!technical.isEmpty()) signals.add("ACTUAL_TECHNICAL_REFERENCE");
        if (signals.isEmpty()) continue;
        String pair =
            "pair:"
                + OntologyReadingPacket.sha256(
                    (left.identityKey() + "\u0001" + right.identityKey())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (seen.add(pair)) candidates.add(new Candidate(pair, left, right, signals));
      }
    return List.copyOf(candidates);
  }

  public static Prepared prepare(
      OntologyEvidenceCorpus corpus,
      Candidate candidate,
      List<OntologyScopeReader.UnitUse> explicit,
      List<OntologyScopeReader.UnitUse> required,
      int maxUnitBytes) {
    if (!candidate.left().result().packet().sourceIdentity().equals(corpus.sourceIdentity())
        || !candidate.right().result().packet().sourceIdentity().equals(corpus.sourceIdentity())
        || !candidate
            .left()
            .result()
            .identity()
            .corpusIdentity()
            .equals(candidate.right().result().identity().corpusIdentity()))
      throw new IllegalArgumentException("ONTOLOGY_OBJECT_TYPE_SOURCE_INVALID");
    Set<UnitHandle> left = preferredSources(corpus, candidate.left());
    Set<UnitHandle> right = preferredSources(corpus, candidate.right());
    if (left.isEmpty() || right.isEmpty())
      throw new OntologyTypedTaskRunner.FormalPreparationFailure(
          "ONTOLOGY_OBJECT_TYPE_SOURCE_UNAVAILABLE", "PREPARE", candidate.pairRef());
    Set<UnitHandle> selected = new LinkedHashSet<>(left);
    selected.addAll(right);
    Set<String> leftEntries = sourceEntries(candidate.left());
    Set<String> rightEntries = sourceEntries(candidate.right());
    for (var use : java.util.stream.Stream.concat(explicit.stream(), required.stream()).toList()) {
      var unit = corpus.aliases().unit(use.unitRef());
      corpus.aliases().read(use.unitRef(), use.entryRef());
      var handle =
          new UnitHandle(
              corpus.aliases().entry(use.entryRef()).entryId(), unit.kind(), unit.originalId());
      boolean onLeft = leftEntries.contains(handle.entryId());
      boolean onRight = rightEntries.contains(handle.entryId());
      if (!onLeft && !onRight)
        throw new OntologyTypedTaskRunner.FormalPreparationFailure(
            "ONTOLOGY_OBJECT_TYPE_SOURCE_UNAVAILABLE", "PREPARE", use.unitRef());
      if (onLeft) left.add(handle);
      if (onRight) right.add(handle);
      selected.add(handle);
    }
    ObjectNode decision = MAPPER.createObjectNode();
    decision.put("ruleVersion", "object-type-material-rule-v1");
    decision.put("anchorRef", candidate.pairRef());
    decision.put("selectionOrigin", "EXPLICIT");
    for (String field :
        List.of(
            "seedUses",
            "derivedUses",
            "derivedEntries",
            "groups",
            "requiredButUnread",
            "unreadCandidates")) decision.putArray(field);
    decision.putObject("cost");
    OntologyReadingPacket packet =
        OntologyReadingPacket.formalV7(corpus, List.copyOf(selected), maxUnitBytes, decision);
    ObjectNode binding = MAPPER.createObjectNode();
    binding.put("schemaVersion", "ontology-object-type-binding-v1");
    binding.put("pairRef", candidate.pairRef());
    ArrayNode unread = binding.putArray("notReadInThisComparison");
    binding.set("left", endpointBinding(candidate.left(), packet, left, unread, "B1"));
    binding.set("right", endpointBinding(candidate.right(), packet, right, unread, "B2"));
    return new Prepared(packet, JSON.encodeCanonical(binding));
  }

  private static Set<String> sourceEntries(Endpoint endpoint) {
    Set<String> entries = new LinkedHashSet<>();
    for (String ref : citedRefs(endpoint.definition()))
      entries.addAll(endpoint.result().packet().resolve(ref).entryUses());
    return entries;
  }

  private static ObjectNode endpointBinding(
      Endpoint endpoint,
      OntologyReadingPacket packet,
      Set<UnitHandle> selected,
      ArrayNode unread,
      String catalogRef) {
    ObjectNode result = MAPPER.createObjectNode();
    result.put("catalogRef", catalogRef);
    result.put("identificationRun", endpoint.identificationRun());
    result.put("questionId", endpoint.result().questionId());
    result.put("corpusIdentity", endpoint.result().identity().corpusIdentity());
    result.put("producingTaskId", endpoint.result().identity().producingTaskId());
    result.put("reviewVersion", endpoint.result().identity().reviewVersion());
    result.put("localId", endpoint.localId());
    result.set("definition", endpoint.definition());
    ArrayNode refs = result.putArray("sourceRefs");
    for (var unit : packet.units())
      if (selected.stream()
          .anyMatch(
              handle ->
                  handle.kind() == unit.kind()
                      && handle.originalId().equals(unit.originalId())
                      && unit.entryUses().contains(handle.entryId()))) refs.add(unit.localRef());
    ArrayNode upstream = result.putArray("upstreamEvidenceRefs");
    for (String ref : citedRefs(endpoint.definition())) {
      upstream.add(ref);
      var unit = endpoint.result().packet().resolve(ref);
      if (unit.entryUses().stream()
          .noneMatch(
              entry -> selected.contains(new UnitHandle(entry, unit.kind(), unit.originalId())))) {
        ObjectNode item = unread.addObject();
        item.put("catalogRef", catalogRef);
        item.put("upstreamRef", ref);
        item.put("unitRef", endpoint.result().packet().evidenceUnitRef(ref));
        item.put("reason", "UPSTREAM_ONLY_NOT_READ_IN_THIS_COMPARISON");
      }
    }
    return result;
  }

  private static Set<UnitHandle> preferredSources(
      OntologyEvidenceCorpus corpus, Endpoint endpoint) {
    Set<UnitHandle> all = new LinkedHashSet<>();
    for (String ref : citedRefs(endpoint.definition())) {
      var unit = endpoint.result().packet().resolve(ref);
      for (String entry : unit.entryUses()) {
        UnitHandle handle = new UnitHandle(entry, unit.kind(), unit.originalId());
        corpus.read(entry, unit.kind(), unit.originalId());
        all.add(handle);
      }
    }
    Set<UnitHandle> frontend =
        all.stream()
            .filter(
                handle ->
                    handle.kind() == UnitKind.FRONTEND_UNIT
                        || handle.kind() == UnitKind.FRONTEND_PAGE_CONTEXT)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    Set<UnitHandle> selected = frontend.isEmpty() ? all : frontend;
    for (UnitHandle unit : List.copyOf(selected)) {
      for (UnitHandle context : corpus.frontendPageContexts(unit)) {
        selected.add(context);
        corpus.frontendContextSources(context).forEach(source -> selected.add(source.unit()));
      }
      if (unit.kind() == UnitKind.FRONTEND_PAGE_CONTEXT)
        corpus.frontendContextSources(unit).forEach(source -> selected.add(source.unit()));
    }
    return selected;
  }

  private static Set<String> technicalRefs(Endpoint endpoint) {
    Set<String> result = new LinkedHashSet<>();
    for (String ref : citedRefs(endpoint.definition()))
      result.add(endpoint.result().packet().evidenceUnitRef(ref));
    return result;
  }

  private static Set<String> bindings(JsonNode definition) {
    Set<String> result = new LinkedHashSet<>();
    for (JsonNode backing : definition.path("backing")) {
      String kind = backing.path("kind").asText();
      String owner = backing.path("owner").asText();
      String name = backing.path("name").asText();
      if (!kind.isBlank() && !owner.isBlank() && !name.isBlank() && !"DERIVED".equals(kind))
        result.add(kind + "\u0000" + owner + "\u0000" + name);
    }
    return result;
  }

  private static Set<String> citedRefs(JsonNode node) {
    Set<String> refs = new LinkedHashSet<>();
    collectRefs(node, refs);
    return refs;
  }

  private static void collectRefs(JsonNode node, Set<String> refs) {
    if (node.isObject())
      node.properties()
          .forEach(
              field -> {
                if ("evidenceRefs".equals(field.getKey()))
                  for (JsonNode ref : field.getValue()) {
                    if (ref.asText().matches("S[1-9][0-9]*")) refs.add(ref.asText());
                  }
                else collectRefs(field.getValue(), refs);
              });
    else if (node.isArray()) node.forEach(value -> collectRefs(value, refs));
  }
}
