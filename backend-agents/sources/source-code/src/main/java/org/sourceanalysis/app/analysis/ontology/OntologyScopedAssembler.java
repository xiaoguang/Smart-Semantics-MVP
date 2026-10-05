package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Assembles reviewed typed fields only; it never interprets a prose description. */
public final class OntologyScopedAssembler {
  private static final List<String> FORMAL_FIELDS =
      List.of("objects", "links", "operations", "rules", "dimensions", "measures", "metrics");
  private static final Map<String, String> FORMAL_OUTPUT_FIELDS =
      Map.of(
          "objects", "objectTypes",
          "links", "linkTypes",
          "operations", "operations",
          "rules", "rules",
          "dimensions", "dimensions",
          "measures", "measures",
          "metrics", "metrics");
  private static final List<String> FIELDS =
      List.of("objects", "links", "operations", "dimensions", "measures", "metrics");
  private static final Map<String, String> PREFIXES =
      Map.of(
          "objects", "object",
          "links", "link",
          "operations", "operation",
          "dimensions", "dimension",
          "measures", "measure",
          "metrics", "metric");
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final OntologyTypedDefinitionValidator validator = new OntologyTypedDefinitionValidator();

  public Assembly assemble(List<ReviewedTask> reviewedTasks) {
    return assemble(reviewedTasks, List.of());
  }

  public Assembly assemble(List<ReviewedTask> reviewedTasks, List<String> readingUnknowns) {
    if (reviewedTasks == null || reviewedTasks.isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_EMPTY");
    }
    if (readingUnknowns == null
        || readingUnknowns.stream().anyMatch(item -> item == null || item.isBlank())) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_READING_UNKNOWN_INVALID");
    }
    String sourceIdentity = reviewedTasks.get(0).packet().sourceIdentity();
    List<ReviewedTask> ordered = new ArrayList<>(reviewedTasks);
    ordered.sort(
        Comparator.comparing((ReviewedTask task) -> task.result().kind().name())
            .thenComparing(task -> task.packet().packetId())
            .thenComparing(task -> task.result().question()));
    Map<String, String> globalByLocal = new LinkedHashMap<>();
    Map<String, JsonNode> definitionsByLocal = new HashMap<>();
    for (ReviewedTask task : ordered) {
      verifyTask(task, sourceIdentity);
      JsonNode review = json.parseCanonical(task.result().review());
      for (String field : FIELDS) {
        for (JsonNode definition : review.path(field)) {
          String localId = definition.path("localId").asText();
          if (definitionsByLocal.putIfAbsent(localId, definition) != null) {
            throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_LOCAL_ID_CONFLICT");
          }
          String basis =
              sourceIdentity + "\n" + task.packet().packetId() + "\n" + field + "\n" + localId;
          globalByLocal.put(
              localId,
              "ontology-"
                  + PREFIXES.get(field)
                  + ":"
                  + OntologyReadingPacket.sha256(
                      basis.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
      }
    }
    Set<String> knownObjects = new HashSet<>();
    for (String localId : globalByLocal.keySet()) {
      if (localId.startsWith("O")) {
        knownObjects.add(localId);
      }
    }

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode ontology = mapper.createObjectNode();
    ontology.put("schemaVersion", "ontology-experimental-scope-v1");
    ontology.put("status", "DRAFT_REVIEWABLE");
    ontology.put("sourceIdentity", sourceIdentity);
    Map<String, ObjectNode> sources = new LinkedHashMap<>();
    Map<String, List<ObjectNode>> definitions = new LinkedHashMap<>();
    FIELDS.forEach(field -> definitions.put(field, new ArrayList<>()));
    Set<String> unresolved = new java.util.TreeSet<>(readingUnknowns);
    for (ReviewedTask task : ordered) {
      JsonNode draft = json.parseCanonical(task.result().draft());
      JsonNode review = json.parseCanonical(task.result().review());
      validator.validatePair(draft, review, task.result().kind(), task.packet(), knownObjects);
      review.path("unresolvedQuestions").forEach(item -> unresolved.add(item.asText()));
      for (String field : FIELDS) {
        for (JsonNode item : review.path(field)) {
          ObjectNode definition = item.deepCopy();
          String localId = definition.path("localId").asText();
          definition.put("globalId", globalByLocal.get(localId));
          mapReferences(definition, task.packet(), globalByLocal, sources);
          definitions.get(field).add(definition);
        }
      }
    }
    for (String field : FIELDS) {
      ArrayNode items = ontology.putArray(field);
      definitions.get(field).stream()
          .sorted(Comparator.comparing(item -> item.path("globalId").asText()))
          .forEach(items::add);
    }
    ArrayNode unknowns = ontology.putArray("unresolvedQuestions");
    unresolved.forEach(unknowns::add);
    ObjectNode sourceIndex = mapper.createObjectNode();
    sourceIndex.put("schemaVersion", "ontology-experimental-source-index-v1");
    sourceIndex.put("sourceIdentity", sourceIdentity);
    ArrayNode indexed = sourceIndex.putArray("sources");
    sources.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> indexed.add(entry.getValue()));
    return new Assembly(json.encodeCanonical(ontology), json.encodeCanonical(sourceIndex));
  }

  /**
   * Assembles only reopened formal results under an independently supplied admitted-Corpus binding.
   * This is a pure four-file boundary: installation and receipt validation remain later owners.
   */
  public FormalAssembly assembleFormal(FormalInput input) {
    if (input == null) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
    }
    List<FormalReview> reviews = formalReviews(input);
    Map<DefinitionKey, FormalDefinition> definitions = new TreeMap<>();
    Map<DefinitionKey, String> globals = new TreeMap<>();
    Map<PropertyKey, String> properties = new TreeMap<>();
    for (FormalReview review : reviews) {
      JsonNode definitionRoot = review.document().path("definitions");
      if (!definitionRoot.isObject()) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
      }
      for (String field : FORMAL_FIELDS) {
        JsonNode items = definitionRoot.get(field);
        if (items == null) continue;
        if (!items.isArray()) {
          throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
        }
        for (JsonNode item : items) {
          if (!item.isObject() || item.path("localId").asText().isBlank()) {
            throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
          }
          DefinitionKey key =
              new DefinitionKey(review.result().identity(), item.path("localId").asText());
          FormalDefinition definition =
              new FormalDefinition(key, field, (ObjectNode) item.deepCopy(), review);
          if (definitions.putIfAbsent(key, definition) != null) {
            throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_DEFINITION_CONFLICT");
          }
          globals.put(key, globalId(field, key));
          if ("objects".equals(field)) {
            for (JsonNode property : item.path("properties")) {
              String localId = property.path("localId").asText();
              if (!property.isObject() || localId.isBlank()) {
                throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
              }
              PropertyKey propertyKey = new PropertyKey(key, localId);
              if (properties.putIfAbsent(propertyKey, globalPropertyId(propertyKey)) != null) {
                throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_DEFINITION_CONFLICT");
              }
            }
          }
        }
      }
    }
    Map<DefinitionKey, String> canonical = canonicalObjects(reviews, definitions, globals);
    Map<String, ObjectNode> sources = new TreeMap<>();
    Map<String, List<ObjectNode>> published = new LinkedHashMap<>();
    Map<DefinitionKey, ObjectNode> publishedByKey = new TreeMap<>();
    FORMAL_FIELDS.forEach(field -> published.put(field, new ArrayList<>()));
    for (FormalDefinition definition : definitions.values()) {
      ObjectNode value = definition.value().deepCopy();
      value.put("globalId", globals.get(definition.key()));
      if ("objects".equals(definition.field())) {
        value.put("canonicalObjectRef", canonical.get(definition.key()));
      }
      mapFormalNode(value, definition, definitions, globals, properties, canonical, sources);
      if ("objects".equals(definition.field())) {
        for (JsonNode property : value.path("properties")) {
          PropertyKey propertyKey =
              new PropertyKey(definition.key(), property.path("localId").asText());
          ObjectNode propertyObject = (ObjectNode) property;
          propertyObject.put("globalId", requiredProperty(properties, propertyKey));
          propertyObject.put("originalOwnerObjectRef", globals.get(definition.key()));
          propertyObject.put("ownerObjectRef", canonical.get(definition.key()));
        }
      }
      published.get(definition.field()).add(value);
      publishedByKey.put(definition.key(), value);
    }
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode ontology = mapper.createObjectNode();
    ontology.put("schemaVersion", "ontology-v1");
    ontology.put("publicationStatus", "DRAFT_REVIEWABLE");
    ObjectNode source = ontology.putObject("source");
    source.put("corpusIdentity", input.binding().corpusIdentity());
    source.put("contentSourceIdentity", input.binding().contentSourceIdentity());
    for (String field : FORMAL_FIELDS) {
      ArrayNode output = ontology.putArray(FORMAL_OUTPUT_FIELDS.get(field));
      published.get(field).stream()
          .sorted(Comparator.comparing(value -> value.path("globalId").asText()))
          .forEach(output::add);
    }
    ArrayNode analysisExtensions = ontology.putArray("analysisExtensions");
    List<ObjectNode> extensionRows = new ArrayList<>();
    List<ObjectNode> unknownRows = new ArrayList<>();
    for (FormalDefinition definition : definitions.values()) {
      String outputField = FORMAL_OUTPUT_FIELDS.get(definition.field());
      ObjectNode publishedDefinition = publishedByKey.get(definition.key());
      if ("ANALYTIC_EXTENSION".equals(publishedDefinition.path("origin").asText())
          || ("metrics".equals(definition.field())
              && "ANALYTIC_EXTENSION"
                  .equals(publishedDefinition.path("implementationStatus").asText()))) {
        ObjectNode extension = mapper.createObjectNode();
        extension.put("globalId", globals.get(definition.key()));
        extension.put("definitionType", outputField);
        extension.put("producingTaskId", definition.key().identity().producingTaskId());
        extension.put("reviewVersion", definition.key().identity().reviewVersion());
        extensionRows.add(extension);
      }
      collectDefinitionUnknowns(
          publishedDefinition,
          "$." + outputField + "[" + definition.key().localId() + "]",
          globals.get(definition.key()),
          definition.key().identity(),
          unknownRows);
    }
    extensionRows.stream()
        .sorted(
            Comparator.comparing((ObjectNode row) -> row.path("globalId").asText())
                .thenComparing(row -> row.path("definitionType").asText()))
        .forEach(analysisExtensions::add);
    ArrayNode unknowns = ontology.putArray("unknowns");
    ArrayNode definitionIndex = ontology.putArray("definitionIndex");
    for (FormalDefinition definition : definitions.values()) {
      ObjectNode index = definitionIndex.addObject();
      index.put("globalId", globals.get(definition.key()));
      index.put("corpusIdentity", definition.key().identity().corpusIdentity());
      index.put("producingTaskId", definition.key().identity().producingTaskId());
      index.put("localId", definition.key().localId());
      index.put("reviewVersion", definition.key().identity().reviewVersion());
      index.put("definitionType", definition.field());
      if ("objects".equals(definition.field())) {
        index.put("canonicalObjectRef", canonical.get(definition.key()));
      }
    }
    List<AssemblyIssue> issues = canonicalIssues(reviews, definitions, globals);
    ImmutableBytes review =
        reviewDocument(reviews, definitions, globals, properties, canonical, sources, issues);
    JsonNode reviewedUnresolved = json.parseCanonical(review).path("unresolved");
    for (int index = 0; index < reviewedUnresolved.size(); index++) {
      JsonNode item = reviewedUnresolved.get(index);
      ObjectNode row = mapper.createObjectNode();
      row.put("path", "$.unresolved[" + index + "]");
      row.set("item", item.deepCopy());
      row.put("producingTaskId", item.path("producingTaskId").asText());
      row.put("reviewVersion", item.path("reviewVersion").asText());
      unknownRows.add(row);
    }
    unknownRows.stream()
        .sorted(
            Comparator.comparing((ObjectNode row) -> row.path("producingTaskId").asText())
                .thenComparing(row -> row.path("reviewVersion").asText())
                .thenComparing(row -> row.path("path").asText()))
        .forEach(unknowns::add);
    ImmutableBytes sourceIndex = sourceIndex(sources);
    ImmutableBytes coverage = coverageDocument(input.coverage(), reviews, issues);
    return new FormalAssembly(json.encodeCanonical(ontology), coverage, sourceIndex, review);
  }

  private List<FormalReview> formalReviews(FormalInput input) {
    List<FormalReview> reviews = new ArrayList<>();
    Set<DefinitionKey> resultIdentities = new HashSet<>();
    for (OntologyTypedTaskRunner.FormalResult result : input.results()) {
      if (result == null
          || result.status() != OntologyTypedTaskRunner.FormalStatus.REVIEWED
          || !input.binding().corpusIdentity().equals(result.identity().corpusIdentity())
          || !input.binding().contentSourceIdentity().equals(result.packet().sourceIdentity())
          || !result.extractRuntimeIdentity().equals(result.reviewRuntimeIdentity())) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
      }
      JsonNode document = json.parseCanonical(result.review());
      String actualReviewVersion =
          "review-v3-"
              + OntologyReadingPacket.sha256(json.encodeCanonical(document).copyToByteArray());
      if (!actualReviewVersion.equals(result.identity().reviewVersion())) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
      }
      DefinitionKey resultKey = new DefinitionKey(result.identity(), "__result__");
      if (!resultIdentities.add(resultKey)) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_DEFINITION_CONFLICT");
      }
      reviews.add(new FormalReview(result, document, catalog(result.catalogMapping())));
    }
    reviews.sort(Comparator.comparing(review -> review.result().identity().producingTaskId()));
    return List.copyOf(reviews);
  }

  private Map<String, DefinitionKey> catalog(ImmutableBytes catalogMapping) {
    JsonNode document = json.parseCanonical(catalogMapping);
    if (!"ontology-reviewed-catalog-v3".equals(document.path("schemaVersion").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
    }
    Map<String, DefinitionKey> result = new LinkedHashMap<>();
    for (JsonNode entry : document.path("entries")) {
      String ref = entry.path("catalogRef").asText();
      JsonNode identity = entry.path("identity");
      DefinitionKey key =
          new DefinitionKey(
              new OntologyTypedTaskRunner.FormalIdentity(
                  identity.path("corpusIdentity").asText(),
                  identity.path("producingTaskId").asText(),
                  identity.path("reviewVersion").asText()),
              identity.path("localId").asText());
      if (!ref.matches("B[1-9][0-9]*") || result.putIfAbsent(ref, key) != null) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
      }
    }
    return Map.copyOf(result);
  }

  private Map<DefinitionKey, String> canonicalObjects(
      List<FormalReview> reviews,
      Map<DefinitionKey, FormalDefinition> definitions,
      Map<DefinitionKey, String> globals) {
    Map<DefinitionKey, String> canonical = new TreeMap<>();
    for (FormalDefinition definition : definitions.values()) {
      if ("objects".equals(definition.field())) {
        canonical.put(definition.key(), globals.get(definition.key()));
      }
    }
    for (AssemblyComponent component : identityComponents(reviews, definitions)) {
      if (component.canonicalChoices().size() == 1) {
        String selected = component.canonicalChoices().iterator().next();
        component.objects().forEach(key -> canonical.put(key, selected));
      }
    }
    return Map.copyOf(canonical);
  }

  private List<AssemblyIssue> canonicalIssues(
      List<FormalReview> reviews,
      Map<DefinitionKey, FormalDefinition> definitions,
      Map<DefinitionKey, String> globals) {
    List<AssemblyIssue> issues = new ArrayList<>();
    for (AssemblyComponent component : identityComponents(reviews, definitions)) {
      if (component.canonicalChoices().size() > 1) {
        issues.add(
            new AssemblyIssue(
                "IDENTITY_CANONICAL_CONFLICT",
                component.objects().stream().map(globals::get).sorted().toList(),
                component.producingTaskIds().stream().sorted().toList(),
                "Conflicting reviewed SAME_OBJECT canonical choices were retained without program"
                    + " selection."));
      }
    }
    issues.sort(
        Comparator.comparing(AssemblyIssue::code)
            .thenComparing(issue -> issue.definitionRefs().toString()));
    return List.copyOf(issues);
  }

  private List<AssemblyComponent> identityComponents(
      List<FormalReview> reviews, Map<DefinitionKey, FormalDefinition> definitions) {
    List<IdentityDecision> decisions = new ArrayList<>();
    for (FormalReview review : reviews) {
      for (JsonNode decision : review.document().path("identityDecisions")) {
        if (!"SAME_OBJECT".equals(decision.path("kind").asText())) continue;
        DefinitionKey left =
            resolveDefinition(decision.path("leftRef").asText(), review, definitions);
        DefinitionKey right =
            resolveDefinition(decision.path("rightRef").asText(), review, definitions);
        DefinitionKey selected =
            resolveDefinition(decision.path("canonicalRef").asText(), review, definitions);
        if (!"objects".equals(definitions.get(left).field())
            || !"objects".equals(definitions.get(right).field())
            || !selected.equals(left) && !selected.equals(right)) {
          throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
        }
        decisions.add(
            new IdentityDecision(
                left, right, selected, review.result().identity().producingTaskId()));
      }
    }
    Map<DefinitionKey, List<IdentityDecision>> byObject = new TreeMap<>();
    decisions.forEach(
        decision -> {
          byObject.computeIfAbsent(decision.left(), ignored -> new ArrayList<>()).add(decision);
          byObject.computeIfAbsent(decision.right(), ignored -> new ArrayList<>()).add(decision);
        });
    List<AssemblyComponent> components = new ArrayList<>();
    Set<DefinitionKey> visited = new HashSet<>();
    for (DefinitionKey start : byObject.keySet()) {
      if (!visited.add(start)) continue;
      Set<DefinitionKey> objects = new TreeSet<>();
      Set<IdentityDecision> componentDecisions = new LinkedHashSet<>();
      Deque<DefinitionKey> queue = new ArrayDeque<>();
      queue.add(start);
      while (!queue.isEmpty()) {
        DefinitionKey current = queue.removeFirst();
        objects.add(current);
        for (IdentityDecision decision : byObject.getOrDefault(current, List.of())) {
          componentDecisions.add(decision);
          DefinitionKey other =
              decision.left().equals(current) ? decision.right() : decision.left();
          if (visited.add(other)) queue.add(other);
        }
      }
      Set<String> choices = new TreeSet<>();
      Set<String> producing = new TreeSet<>();
      for (IdentityDecision decision : componentDecisions) {
        choices.add(
            decision.canonical().identity().producingTaskId()
                + "\u0000"
                + decision.canonical().localId());
        producing.add(decision.producingTaskId());
      }
      Map<String, String> globals = new HashMap<>();
      for (DefinitionKey object : objects)
        globals.put(
            object.identity().producingTaskId() + "\u0000" + object.localId(),
            globalId("objects", object));
      components.add(
          new AssemblyComponent(
              objects,
              choices.stream()
                  .map(globals::get)
                  .collect(java.util.stream.Collectors.toCollection(TreeSet::new)),
              producing));
    }
    return List.copyOf(components);
  }

  private void mapFormalNode(
      JsonNode node,
      FormalDefinition owner,
      Map<DefinitionKey, FormalDefinition> definitions,
      Map<DefinitionKey, String> globals,
      Map<PropertyKey, String> properties,
      Map<DefinitionKey, String> canonical,
      Map<String, ObjectNode> sources) {
    if (node instanceof ObjectNode object) {
      List<String> names = new ArrayList<>();
      object.fieldNames().forEachRemaining(names::add);
      for (String name : names) {
        JsonNode value = object.get(name);
        if ("evidenceRefs".equals(name)) {
          ArrayNode mapped = new ObjectMapper().createArrayNode();
          if (!value.isArray())
            throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
          for (JsonNode reference : value)
            mapped.add(sourceRef(owner, reference.asText(), sources));
          object.set(name, mapped);
        } else if (formalReferenceField(name)) {
          object.set(
              name,
              mapReferenceValue(name, value, owner, definitions, globals, properties, canonical));
        } else {
          mapFormalNode(value, owner, definitions, globals, properties, canonical, sources);
        }
      }
    } else if (node.isArray()) {
      node.forEach(
          item -> mapFormalNode(item, owner, definitions, globals, properties, canonical, sources));
    }
  }

  private static void collectDefinitionUnknowns(
      JsonNode node,
      String path,
      String definitionRef,
      OntologyTypedTaskRunner.FormalIdentity identity,
      List<ObjectNode> rows) {
    if (!node.isObject()) {
      return;
    }
    JsonNode unknowns = node.get("unknowns");
    if (unknowns != null) {
      if (!unknowns.isArray()) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
      }
      for (int index = 0; index < unknowns.size(); index++) {
        ObjectNode row = new ObjectMapper().createObjectNode();
        row.put("definitionRef", definitionRef);
        row.put("path", path + ".unknowns[" + index + "]");
        row.set("item", unknowns.get(index).deepCopy());
        row.put("producingTaskId", identity.producingTaskId());
        row.put("reviewVersion", identity.reviewVersion());
        rows.add(row);
      }
    }
    List<String> fields = new ArrayList<>();
    node.fieldNames().forEachRemaining(fields::add);
    fields.sort(String::compareTo);
    for (String field : fields) {
      if ("unknowns".equals(field)) {
        continue;
      }
      JsonNode value = node.get(field);
      if (value.isObject()) {
        collectDefinitionUnknowns(value, path + "." + field, definitionRef, identity, rows);
      } else if (value.isArray()) {
        for (int index = 0; index < value.size(); index++) {
          collectDefinitionUnknowns(
              value.get(index),
              path + "." + field + "[" + index + "]",
              definitionRef,
              identity,
              rows);
        }
      }
    }
  }

  private JsonNode mapReferenceValue(
      String field,
      JsonNode value,
      FormalDefinition owner,
      Map<DefinitionKey, FormalDefinition> definitions,
      Map<DefinitionKey, String> globals,
      Map<PropertyKey, String> properties,
      Map<DefinitionKey, String> canonical) {
    if (value.isNull()) return value.deepCopy();
    if (value.isTextual()) {
      return new ObjectMapper()
          .getNodeFactory()
          .textNode(
              mapReference(
                  field, value.asText(), owner, definitions, globals, properties, canonical));
    }
    if (value.isArray()) {
      ArrayNode mapped = new ObjectMapper().createArrayNode();
      for (JsonNode item : value) {
        if (!item.isTextual())
          throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
        mapped.add(
            mapReference(field, item.asText(), owner, definitions, globals, properties, canonical));
      }
      return mapped;
    }
    throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
  }

  private String mapReference(
      String field,
      String reference,
      FormalDefinition owner,
      Map<DefinitionKey, FormalDefinition> definitions,
      Map<DefinitionKey, String> globals,
      Map<PropertyKey, String> properties,
      Map<DefinitionKey, String> canonical) {
    int separator = reference.indexOf('.');
    String definitionReference = separator < 0 ? reference : reference.substring(0, separator);
    String propertyLocalId = separator < 0 ? null : reference.substring(separator + 1);
    DefinitionKey target =
        "relatedLocalDefinitionRefs".equals(field)
            ? resolveLocalDefinition(definitionReference, owner, definitions)
            : resolveDefinition(definitionReference, owner.review(), definitions);
    FormalDefinition targetDefinition = definitions.get(target);
    if (targetDefinition == null || (separator >= 0 && propertyLocalId.isBlank())) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    }
    if ("relatedLocalDefinitionRefs".equals(field) && propertyLocalId != null) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    }
    if (propertyReferenceField(field)) {
      if (propertyLocalId == null || !"objects".equals(targetDefinition.field())) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
      }
      return requiredProperty(properties, new PropertyKey(target, propertyLocalId));
    }
    if (objectReferenceField(field)) {
      if (propertyLocalId != null || !"objects".equals(targetDefinition.field())) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
      }
      return requiredCanonical(canonical, target);
    }
    if ("ownerRef".equals(field)) {
      if (propertyLocalId != null) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
      }
      if ("objects".equals(targetDefinition.field())) {
        return requiredCanonical(canonical, target);
      }
      if (!"operations".equals(targetDefinition.field())) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
      }
      return requiredGlobal(globals, target);
    }
    if ("componentMeasureRefs".equals(field) && !"measures".equals(targetDefinition.field())) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    }
    if ("dimensionRefs".equals(field) && !"dimensions".equals(targetDefinition.field())) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    }
    if (propertyLocalId != null) {
      return requiredProperty(properties, new PropertyKey(target, propertyLocalId));
    }
    String mapped = globals.get(target);
    if (mapped == null)
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    return mapped;
  }

  private DefinitionKey resolveLocalDefinition(
      String reference, FormalDefinition owner, Map<DefinitionKey, FormalDefinition> definitions) {
    if (!reference.matches("[OALRDVM][1-9][0-9]*")) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    }
    DefinitionKey key = new DefinitionKey(owner.key().identity(), reference);
    if (!definitions.containsKey(key)) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    }
    return key;
  }

  private DefinitionKey resolveDefinition(
      String reference, FormalReview review, Map<DefinitionKey, FormalDefinition> definitions) {
    DefinitionKey key;
    if (reference.matches("B[1-9][0-9]*")) {
      key = review.catalog().get(reference);
    } else if (reference.matches("[OALRDVM][1-9][0-9]*")) {
      key = new DefinitionKey(review.result().identity(), reference);
    } else {
      key = null;
    }
    if (key == null || !definitions.containsKey(key)) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    }
    return key;
  }

  private String sourceRef(
      FormalDefinition owner, String reference, Map<String, ObjectNode> sources) {
    OntologyReadingPacket.PackedUnit unit;
    try {
      unit = owner.review().result().packet().resolve(reference);
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED", invalid);
    }
    String basis =
        owner.key().identity().corpusIdentity()
            + "\n"
            + owner.key().identity().producingTaskId()
            + "\n"
            + owner.key().identity().reviewVersion()
            + "\n"
            + owner.review().result().packet().packetId()
            + "\n"
            + reference;
    String sourceRef =
        "ontology-source:"
            + OntologyReadingPacket.sha256(basis.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    sources.computeIfAbsent(
        sourceRef,
        ignored -> {
          ObjectNode row = new ObjectMapper().createObjectNode();
          row.put("schemaVersion", "ontology-source-v1");
          row.put("sourceRef", sourceRef);
          OntologyReadingPacket packet = owner.review().result().packet();
          row.put("sourceIdentity", packet.sourceIdentity());
          row.put("corpusIdentity", owner.key().identity().corpusIdentity());
          row.put("producingTaskId", owner.key().identity().producingTaskId());
          row.put("reviewVersion", owner.key().identity().reviewVersion());
          row.put("packetId", packet.packetId());
          row.put("localRef", reference);
          row.put("evidenceUnitRef", packet.evidenceUnitRef(reference));
          ArrayNode entryRefs = row.putArray("entryRefs");
          packet.entryRefs(reference).forEach(entryRefs::add);
          row.put("kind", unit.kind().name());
          row.put("originalId", unit.originalId());
          if (unit.kind() == OntologyEvidenceCorpus.UnitKind.SCHEMA_SOURCE) {
            JsonNode schema = unit.content();
            row.put("path", schema.path("path").asText());
            row.put("sha256", schema.path("sha256").asText());
            row.set("range", schema.path("range").deepCopy());
          }
          ArrayNode uses = row.putArray("entryUses");
          unit.entryUses().stream().sorted().forEach(uses::add);
          return row;
        });
    return sourceRef;
  }

  private ImmutableBytes sourceIndex(Map<String, ObjectNode> sources) {
    if (sources.isEmpty()) return ImmutableBytes.copyOf(new byte[0]);
    List<String> rows = new ArrayList<>();
    sources
        .values()
        .forEach(
            row ->
                rows.add(
                    new String(
                        json.encodeCanonical(row).copyToByteArray(),
                        java.nio.charset.StandardCharsets.UTF_8)));
    return ImmutableBytes.copyOf(
        (String.join("\n", rows) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private ImmutableBytes reviewDocument(
      List<FormalReview> reviews,
      Map<DefinitionKey, FormalDefinition> definitions,
      Map<DefinitionKey, String> globals,
      Map<PropertyKey, String> properties,
      Map<DefinitionKey, String> canonical,
      Map<String, ObjectNode> sources,
      List<AssemblyIssue> issues) {
    ObjectNode review = new ObjectMapper().createObjectNode();
    review.put("schemaVersion", "ontology-review-v1");
    review.put("publicationStatus", "DRAFT_REVIEWABLE");
    review.put("humanAcceptanceStatus", "NOT_REVIEWED");
    ArrayNode taskResults = review.putArray("taskResults");
    ArrayNode decisions = review.putArray("identityDecisions");
    ArrayNode unresolved = review.putArray("unresolved");
    for (FormalReview item : reviews) {
      ObjectNode task = taskResults.addObject();
      task.put("corpusIdentity", item.result().identity().corpusIdentity());
      task.put("producingTaskId", item.result().identity().producingTaskId());
      task.put("reviewVersion", item.result().identity().reviewVersion());
      task.put("taskKind", item.result().kind().name());
      task.put("extractRuntime", item.result().extractRuntimeIdentity().model());
      task.put("reviewRuntime", item.result().reviewRuntimeIdentity().model());
      task.set(
          "extractRuntimeIdentity",
          new ObjectMapper().valueToTree(item.result().extractRuntimeIdentity()));
      task.set(
          "reviewRuntimeIdentity",
          new ObjectMapper().valueToTree(item.result().reviewRuntimeIdentity()));
      FormalDefinition reviewOwner =
          new FormalDefinition(
              new DefinitionKey(item.result().identity(), "__review__"),
              "review",
              new ObjectMapper().createObjectNode(),
              item);
      ArrayNode corrections = task.putArray("corrections");
      for (JsonNode correction : item.document().path("corrections")) {
        ObjectNode mapped = (ObjectNode) correction.deepCopy();
        mapFormalNode(mapped, reviewOwner, definitions, globals, properties, canonical, sources);
        reviewIdentity(mapped, item);
        corrections.add(mapped);
      }
      for (JsonNode decision : item.document().path("identityDecisions")) {
        ObjectNode mapped = (ObjectNode) decision.deepCopy();
        mapFormalNode(mapped, reviewOwner, definitions, globals, properties, canonical, sources);
        reviewIdentity(mapped, item);
        if ("SAME_OBJECT".equals(decision.path("kind").asText())) {
          DefinitionKey selected =
              resolveDefinition(decision.path("canonicalRef").asText(), item, definitions);
          FormalDefinition selectedDefinition = definitions.get(selected);
          if (selectedDefinition == null || !"objects".equals(selectedDefinition.field())) {
            throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
          }
          mapped.put("resolvedCanonicalObjectRef", requiredCanonical(canonical, selected));
        } else {
          mapped.putNull("resolvedCanonicalObjectRef");
        }
        decisions.add(mapped);
      }
      for (JsonNode itemUnresolved : item.document().path("unresolved")) {
        ObjectNode mapped = (ObjectNode) itemUnresolved.deepCopy();
        mapFormalNode(mapped, reviewOwner, definitions, globals, properties, canonical, sources);
        reviewIdentity(mapped, item);
        unresolved.add(mapped);
      }
    }
    ArrayNode assemblyIssues = review.putArray("assemblyIssues");
    for (AssemblyIssue issue : issues) {
      ObjectNode item = assemblyIssues.addObject();
      item.put("code", issue.code());
      ArrayNode refs = item.putArray("definitionRefs");
      issue.definitionRefs().forEach(refs::add);
      ArrayNode producing = item.putArray("producingTaskIds");
      issue.producingTaskIds().forEach(producing::add);
      item.put("detail", issue.detail());
    }
    return json.encodeCanonical(review);
  }

  private void reviewIdentity(ObjectNode document, FormalReview review) {
    document.put("corpusIdentity", review.result().identity().corpusIdentity());
    document.put("producingTaskId", review.result().identity().producingTaskId());
    document.put("reviewVersion", review.result().identity().reviewVersion());
  }

  private ImmutableBytes coverageDocument(
      ScopedCoverage coverage, List<FormalReview> reviews, List<AssemblyIssue> issues) {
    Map<String, OntologyTypedTaskRunner.FormalIdentity> actual = new TreeMap<>();
    reviews.forEach(
        review -> {
          OntologyTypedTaskRunner.FormalIdentity identity = review.result().identity();
          actual.putIfAbsent(coverageIdentity(identity), identity);
        });
    Set<String> reviewed = new TreeSet<>();
    boolean incomplete = !issues.isEmpty();
    ObjectNode output = new ObjectMapper().createObjectNode();
    output.put("schemaVersion", "ontology-coverage-v1");
    ObjectNode denominators = output.putObject("inputDenominators");
    denominators.put("entries", coverage.inputDenominators().entries());
    denominators.put("frontendRequests", coverage.inputDenominators().frontendRequests());
    denominators.put("ddlSources", coverage.inputDenominators().ddlSources());
    ArrayNode reading = output.putArray("readingDispositions");
    List<ReadingDisposition> readingItems = new ArrayList<>(coverage.readingDispositions());
    readingItems.sort(
        Comparator.comparing(ReadingDisposition::taskId)
            .thenComparing(ReadingDisposition::entryRef)
            .thenComparing(ReadingDisposition::unitRef));
    for (ReadingDisposition item : readingItems) {
      ObjectNode value = reading.addObject();
      value.put("taskId", item.taskId());
      value.put("entryRef", item.entryRef());
      value.put("unitRef", item.unitRef());
      value.put("status", item.status().name());
      value.put("reason", item.reason());
      incomplete |=
          item.status() == ReadingDispositionStatus.REQUIRED_UNREAD
              || item.status() == ReadingDispositionStatus.TOO_LARGE;
    }
    ArrayNode tasks = output.putArray("taskDispositions");
    List<TaskDisposition> taskItems = new ArrayList<>(coverage.taskDispositions());
    taskItems.sort(Comparator.comparing(TaskDisposition::taskId));
    for (TaskDisposition item : taskItems) {
      if (item.status() == TaskDispositionStatus.REVIEWED) {
        String identity = reviewedCoverageIdentity(item, actual);
        if (identity == null || !reviewed.add(identity)) {
          throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
        }
      } else if (item.status() == TaskDispositionStatus.UNPROCESSED) {
        if (item.producingTaskId() != null)
          throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
      } else if (item.producingTaskId() == null) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
      }
      ObjectNode value = tasks.addObject();
      value.put("taskId", item.taskId());
      if (item.producingTaskId() == null) value.putNull("producingTaskId");
      else value.put("producingTaskId", item.producingTaskId());
      value.put("status", item.status().name());
      value.put("reason", item.reason());
      incomplete |=
          item.status() == TaskDispositionStatus.UNPROCESSED
              || item.status() == TaskDispositionStatus.REJECTED
              || item.status() == TaskDispositionStatus.UNDETERMINED;
    }
    if (!reviewed.equals(actual.keySet()))
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
    output.put("scope", "SCOPED");
    boolean unaccountedInput =
        coverage.inputDenominators().entries() > 0
            || coverage.inputDenominators().frontendRequests() > 0
            || coverage.inputDenominators().ddlSources() > 0;
    unaccountedInput &= reviews.isEmpty() && taskItems.isEmpty();
    output.put(
        "coverageStatus",
        incomplete ? "INCOMPLETE" : unaccountedInput ? "UNDETERMINED" : "COMPLETE");
    output.put("semanticExhaustiveness", "UNDETERMINED");
    return json.encodeCanonical(output);
  }

  private static String reviewedCoverageIdentity(
      TaskDisposition item, Map<String, OntologyTypedTaskRunner.FormalIdentity> actual) {
    if (item.producingTaskId() == null) {
      return null;
    }
    if (item.reviewedResultIdentity() != null) {
      String identity = coverageIdentity(item.reviewedResultIdentity());
      return actual.containsKey(identity) ? identity : null;
    }
    String selected = null;
    for (Map.Entry<String, OntologyTypedTaskRunner.FormalIdentity> candidate : actual.entrySet()) {
      if (!item.producingTaskId().equals(candidate.getValue().producingTaskId())) {
        continue;
      }
      if (selected != null) {
        return null;
      }
      selected = candidate.getKey();
    }
    return selected;
  }

  private static String coverageIdentity(OntologyTypedTaskRunner.FormalIdentity identity) {
    return identity.corpusIdentity()
        + "\u0000"
        + identity.producingTaskId()
        + "\u0000"
        + identity.reviewVersion();
  }

  private static boolean formalReferenceField(String field) {
    return Set.of(
            "definitionRef",
            "propertyRef",
            "keyRefs",
            "targetObjectRef",
            "targetObjectRefs",
            "fromObjectRef",
            "toObjectRef",
            "ownerObjectRef",
            "sourceObjectRef",
            "ownerRefs",
            "ownerRef",
            "componentMeasureRefs",
            "dimensionRefs",
            "knownDefinitionRefs",
            "relatedLocalDefinitionRefs",
            "leftRef",
            "rightRef",
            "canonicalRef")
        .contains(field);
  }

  private static boolean propertyReferenceField(String field) {
    return "propertyRef".equals(field) || "keyRefs".equals(field);
  }

  private static boolean objectReferenceField(String field) {
    return Set.of(
            "targetObjectRef",
            "targetObjectRefs",
            "fromObjectRef",
            "toObjectRef",
            "ownerObjectRef",
            "sourceObjectRef",
            "ownerRefs")
        .contains(field);
  }

  private static String globalId(String field, DefinitionKey key) {
    String prefix = PREFIXES.getOrDefault(field, field.substring(0, field.length() - 1));
    String basis =
        key.identity().corpusIdentity()
            + "\n"
            + key.identity().producingTaskId()
            + "\n"
            + key.identity().reviewVersion()
            + "\n"
            + field
            + "\n"
            + key.localId();
    return "ontology-"
        + prefix
        + ":"
        + OntologyReadingPacket.sha256(basis.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String globalPropertyId(PropertyKey key) {
    String basis =
        key.owner().identity().corpusIdentity()
            + "\n"
            + key.owner().identity().producingTaskId()
            + "\n"
            + key.owner().identity().reviewVersion()
            + "\n"
            + key.owner().localId()
            + "\n"
            + key.localId();
    return "ontology-property:"
        + OntologyReadingPacket.sha256(basis.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String requiredProperty(Map<PropertyKey, String> properties, PropertyKey key) {
    String property = properties.get(key);
    if (property == null)
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    return property;
  }

  private static String requiredCanonical(Map<DefinitionKey, String> canonical, DefinitionKey key) {
    String value = canonical.get(key);
    if (value == null) throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    return value;
  }

  private static String requiredGlobal(Map<DefinitionKey, String> globals, DefinitionKey key) {
    String value = globals.get(key);
    if (value == null) throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    return value;
  }

  private void verifyTask(ReviewedTask task, String sourceIdentity) {
    if (task == null || task.packet() == null || task.result() == null) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_TASK_MISSING");
    }
    if (!sourceIdentity.equals(task.packet().sourceIdentity())
        || !sourceIdentity.equals(task.result().sourceIdentity())
        || !task.packet().packetId().equals(task.result().packetId())) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_SOURCE_MISMATCH");
    }
  }

  private void mapReferences(
      JsonNode node,
      OntologyReadingPacket packet,
      Map<String, String> globalByLocal,
      Map<String, ObjectNode> sources) {
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      List<String> fields = new ArrayList<>();
      object.fieldNames().forEachRemaining(fields::add);
      for (String field : fields) {
        JsonNode value = object.get(field);
        if ("evidenceRefs".equals(field)) {
          ArrayNode refs = object.putArray(field);
          value.forEach(
              ref -> {
                OntologyReadingPacket.PackedUnit unit = packet.resolve(ref.asText());
                String sourceRef =
                    "ontology-source:"
                        + OntologyReadingPacket.sha256(
                            (packet.sourceIdentity()
                                    + "\n"
                                    + packet.packetId()
                                    + "\n"
                                    + ref.asText())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                refs.add(sourceRef);
                sources.computeIfAbsent(
                    sourceRef,
                    unused -> {
                      ObjectNode indexed = new ObjectMapper().createObjectNode();
                      indexed.put("sourceRef", sourceRef);
                      indexed.put("packetId", packet.packetId());
                      indexed.put("localRef", ref.asText());
                      indexed.put("kind", unit.kind().name());
                      indexed.put("originalId", unit.originalId());
                      if (unit.kind() == OntologyEvidenceCorpus.UnitKind.SCHEMA_SOURCE) {
                        JsonNode schema = unit.content();
                        indexed.put("path", schema.path("path").asText());
                        indexed.put("sha256", schema.path("sha256").asText());
                        indexed.set("range", schema.path("range").deepCopy());
                      }
                      ArrayNode uses = indexed.putArray("entryUses");
                      unit.entryUses().forEach(uses::add);
                      return indexed;
                    });
              });
        } else if (isObjectRefField(field) || isDefinitionRefField(field)) {
          if (value.isTextual() && !value.asText().isBlank()) {
            object.put(field, globalRef(value.asText(), globalByLocal));
          } else if (value.isArray()) {
            ArrayNode refs = object.putArray(field);
            value.forEach(ref -> refs.add(globalRef(ref.asText(), globalByLocal)));
          }
        } else {
          mapReferences(value, packet, globalByLocal, sources);
        }
      }
    } else if (node.isArray()) {
      node.forEach(item -> mapReferences(item, packet, globalByLocal, sources));
    }
  }

  private static boolean isObjectRefField(String field) {
    return Set.of(
            "fromObjectRef",
            "toObjectRef",
            "ownerObjectRef",
            "sourceObjectRef",
            "targetObjectRef",
            "targetObjectRefs")
        .contains(field);
  }

  private static boolean isDefinitionRefField(String field) {
    return Set.of("componentMeasureRefs", "dimensionRefs").contains(field);
  }

  private static String globalRef(String localId, Map<String, String> globalByLocal) {
    String mapped = globalByLocal.get(localId);
    if (mapped == null) {
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_DANGLING_REFERENCE");
    }
    return mapped;
  }

  public record ReviewedTask(OntologyReadingPacket packet, OntologyTypedTaskRunner.Result result) {}

  public record Assembly(ImmutableBytes ontologyJson, ImmutableBytes sourceIndexJson) {}

  public record FormalInput(
      OntologyTypedTaskRunner.FormalCorpusBinding binding,
      List<OntologyTypedTaskRunner.FormalResult> results,
      ScopedCoverage coverage) {
    public FormalInput {
      binding = Objects.requireNonNull(binding, "formal corpus binding");
      results = List.copyOf(Objects.requireNonNull(results, "formal results"));
      coverage = Objects.requireNonNull(coverage, "scoped coverage");
    }
  }

  public record FormalAssembly(
      ImmutableBytes ontology,
      ImmutableBytes coverage,
      ImmutableBytes sourceIndex,
      ImmutableBytes review) {
    public FormalAssembly {
      ontology = Objects.requireNonNull(ontology, "formal ontology");
      coverage = Objects.requireNonNull(coverage, "formal coverage");
      sourceIndex = Objects.requireNonNull(sourceIndex, "formal source index");
      review = Objects.requireNonNull(review, "formal review");
    }
  }

  public record InputDenominators(int entries, int frontendRequests, int ddlSources) {
    public InputDenominators {
      if (entries < 0 || frontendRequests < 0 || ddlSources < 0) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_COVERAGE_INVALID");
      }
    }
  }

  public record ScopedCoverage(
      InputDenominators inputDenominators,
      List<ReadingDisposition> readingDispositions,
      List<TaskDisposition> taskDispositions) {
    public ScopedCoverage {
      inputDenominators = Objects.requireNonNull(inputDenominators, "input denominators");
      readingDispositions =
          List.copyOf(Objects.requireNonNull(readingDispositions, "reading dispositions"));
      taskDispositions = List.copyOf(Objects.requireNonNull(taskDispositions, "task dispositions"));
    }
  }

  public enum ReadingDispositionStatus {
    PREPARED,
    READ,
    REQUIRED_UNREAD,
    EXCLUDED,
    UNSUPPORTED,
    TOO_LARGE
  }

  public record ReadingDisposition(
      String taskId,
      String entryRef,
      String unitRef,
      ReadingDispositionStatus status,
      String reason) {
    public ReadingDisposition {
      requireAssemblyText(taskId, "reading task ID");
      requireAssemblyText(entryRef, "reading entry reference");
      requireAssemblyText(unitRef, "reading unit reference");
      status = Objects.requireNonNull(status, "reading disposition status");
      requireAssemblyText(reason, "reading disposition reason");
    }
  }

  public enum TaskDispositionStatus {
    REVIEWED,
    REJECTED,
    UNPROCESSED,
    UNDETERMINED
  }

  public record TaskDisposition(
      String taskId,
      String producingTaskId,
      TaskDispositionStatus status,
      String reason,
      OntologyTypedTaskRunner.FormalIdentity reviewedResultIdentity) {
    public TaskDisposition(
        String taskId, String producingTaskId, TaskDispositionStatus status, String reason) {
      this(taskId, producingTaskId, status, reason, null);
    }

    public TaskDisposition {
      requireAssemblyText(taskId, "task disposition ID");
      status = Objects.requireNonNull(status, "task disposition status");
      requireAssemblyText(reason, "task disposition reason");
      if (status == TaskDispositionStatus.UNPROCESSED) {
        if (producingTaskId != null)
          throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_COVERAGE_INVALID");
      } else {
        requireAssemblyText(producingTaskId, "producing task ID");
      }
      if (reviewedResultIdentity != null
          && (status != TaskDispositionStatus.REVIEWED
              || !producingTaskId.equals(reviewedResultIdentity.producingTaskId()))) {
        throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_COVERAGE_INVALID");
      }
    }
  }

  private record FormalReview(
      OntologyTypedTaskRunner.FormalResult result,
      JsonNode document,
      Map<String, DefinitionKey> catalog) {}

  private record FormalDefinition(
      DefinitionKey key, String field, ObjectNode value, FormalReview review) {}

  private record DefinitionKey(OntologyTypedTaskRunner.FormalIdentity identity, String localId)
      implements Comparable<DefinitionKey> {
    private DefinitionKey {
      identity = Objects.requireNonNull(identity, "definition identity");
      requireAssemblyText(localId, "definition local ID");
    }

    @Override
    public int compareTo(DefinitionKey other) {
      int corpus = identity.corpusIdentity().compareTo(other.identity.corpusIdentity());
      if (corpus != 0) return corpus;
      int task = identity.producingTaskId().compareTo(other.identity.producingTaskId());
      if (task != 0) return task;
      int review = identity.reviewVersion().compareTo(other.identity.reviewVersion());
      return review != 0 ? review : localId.compareTo(other.localId);
    }
  }

  private record PropertyKey(DefinitionKey owner, String localId)
      implements Comparable<PropertyKey> {
    private PropertyKey {
      owner = Objects.requireNonNull(owner, "property owner");
      requireAssemblyText(localId, "property local ID");
    }

    @Override
    public int compareTo(PropertyKey other) {
      int ownerOrder = owner.compareTo(other.owner);
      return ownerOrder != 0 ? ownerOrder : localId.compareTo(other.localId);
    }
  }

  private record IdentityDecision(
      DefinitionKey left, DefinitionKey right, DefinitionKey canonical, String producingTaskId) {}

  private record AssemblyComponent(
      Set<DefinitionKey> objects, Set<String> canonicalChoices, Set<String> producingTaskIds) {}

  private record AssemblyIssue(
      String code, List<String> definitionRefs, List<String> producingTaskIds, String detail) {}

  private static void requireAssemblyText(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("ONTOLOGY_ASSEMBLY_COVERAGE_INVALID");
  }
}
