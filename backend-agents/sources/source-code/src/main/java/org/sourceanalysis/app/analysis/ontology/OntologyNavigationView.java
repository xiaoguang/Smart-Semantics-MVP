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
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueDisclosure;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EntryCluePage;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EntrySummary;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.NavigationClue;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.NavigationPage;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.SearchMatch;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.SearchResult;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitNavigationMetadata;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitPage;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** A private, bounded model-navigation view with view-scoped reversible references. */
final class OntologyNavigationView {
  private static final int SURVEY_CLUES_PER_KIND = 2;
  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String privateViewId;
  private final String modelViewId;
  private final ObjectNode visible;
  private final Map<String, Target> visibleTargets;
  private final Map<String, Target> fullTargets;
  private final Map<String, String> ownerEntryRef;
  private final Map<String, String> controllerRefByEntry;
  private final Map<String, String> readableUnitRefByClue;

  private OntologyNavigationView(
      String privateViewId,
      ObjectNode visible,
      Map<String, Target> visibleTargets,
      Map<String, Target> fullTargets,
      Map<String, String> ownerEntryRef,
      Map<String, String> controllerRefByEntry,
      Map<String, String> readableUnitRefByClue) {
    this.privateViewId = privateViewId;
    this.modelViewId = modelViewId(privateViewId);
    this.visible = visible;
    this.visibleTargets = Map.copyOf(visibleTargets);
    this.fullTargets = Map.copyOf(fullTargets);
    this.ownerEntryRef = Map.copyOf(ownerEntryRef);
    this.controllerRefByEntry = Map.copyOf(controllerRefByEntry);
    this.readableUnitRefByClue = Map.copyOf(readableUnitRefByClue);
  }

  static OntologyNavigationView survey(
      String sourceIdentity, OntologyEvidenceCorpus corpus, NavigationPage page) {
    Objects.requireNonNull(corpus, "ontology corpus");
    Objects.requireNonNull(page, "navigation page");
    String privateViewId =
        viewId(
            "survey",
            sourceIdentity
                + "\u0000"
                + page.offset()
                + "\u0000"
                + page.limit()
                + "\u0000"
                + page.totalEntries());
    String modelViewId = modelViewId(privateViewId);
    ObjectNode document = MAPPER.createObjectNode();
    document.put("viewId", modelViewId);
    document.put("pageOffset", page.offset());
    document.put("limit", page.limit());
    ObjectNode disclosure = document.putObject("pageDisclosure");
    disclosure.put("totalEntries", page.totalEntries());
    disclosure.put("shownEntries", page.entries().size());
    disclosure.put("unreadEntries", page.unreadEntries());
    ArrayNode entries = document.putArray("entries");
    Map<String, Target> targets = new LinkedHashMap<>();
    Map<String, String> owners = new LinkedHashMap<>();
    Map<String, String> controllerRefs = new LinkedHashMap<>();
    Map<String, String> readableRefs = new LinkedHashMap<>();
    int nextUnit = 1;
    int nextClue = 1;
    for (int index = 0; index < page.entries().size(); index++) {
      EntrySummary entry = page.entries().get(index);
      String entryRef = "E" + (index + 1);
      ObjectNode card = entries.addObject();
      card.set("entryRef", refNode(modelViewId, entryRef));
      card.put("method", entry.method());
      card.put("route", entry.route());
      card.put("handlerFqn", entry.handlerFqn());
      card.put("assemblyStatus", entry.assemblyStatus());
      card.put("limitationCount", entry.limitationCount());
      targets.put(entryRef, Target.entry(entry.entryId()));
      owners.put(entryRef, entryRef);
      if (!entry.methodKey().isBlank()) {
        UnitHandle controller =
            new UnitHandle(entry.entryId(), UnitKind.JAVA_METHOD, entry.methodKey());
        try {
          int bytes =
              corpus
                  .read(controller.entryId(), controller.kind(), controller.originalId())
                  .canonicalJson()
                  .size();
          String controllerRef = "J" + nextUnit++;
          card.set("controllerUnitRef", refNode(modelViewId, controllerRef));
          card.put("controllerUnitBytes", bytes);
          targets.put(controllerRef, Target.unit(controller));
          owners.put(controllerRef, entryRef);
          controllerRefs.put(entryRef, controllerRef);
        } catch (IllegalArgumentException unavailable) {
          if (!"ONTOLOGY_UNIT_NOT_FOUND".equals(unavailable.getMessage())) {
            throw unavailable;
          }
          card.putNull("controllerUnitRef");
          card.putNull("controllerUnitBytes");
        }
      } else {
        card.putNull("controllerUnitRef");
        card.putNull("controllerUnitBytes");
      }
      EntryCluePage clues = corpus.entryClues(entry.entryId(), SURVEY_CLUES_PER_KIND);
      card.put("sqlAnalysesWithoutAst", clues.sqlAnalysesWithoutAst());
      ObjectNode clueDisclosure = card.putObject("clueDisclosure");
      for (ClueKind kind : ClueKind.values()) {
        ClueDisclosure counts = clues.disclosure().get(kind);
        ObjectNode kindDisclosure = clueDisclosure.putObject(kind.name());
        kindDisclosure.put("total", counts.total());
        kindDisclosure.put("shown", counts.shown());
        kindDisclosure.put("unread", counts.unread());
      }
      ArrayNode visibleClues = card.putArray("clues");
      for (NavigationClue clue : clues.clues()) {
        String clueRef = "L" + nextClue++;
        ObjectNode item = visibleClues.addObject();
        item.set("ref", refNode(modelViewId, clueRef));
        item.put("kind", clue.kind().name());
        item.put("keyDisplay", clue.keyDisplay());
        item.put("totalUses", clue.totalUses());
        item.put("unitBytes", clue.unitBytes());
        String readableRef = "U" + nextUnit++;
        item.set("readableUnitRef", refNode(modelViewId, readableRef));
        targets.put(clueRef, Target.clue(clue));
        targets.put(readableRef, Target.unit(clue.readableUnit()));
        owners.put(clueRef, entryRef);
        owners.put(readableRef, entryRef);
        readableRefs.put(clueRef, readableRef);
      }
    }
    return new OntologyNavigationView(
        privateViewId, document, targets, targets, owners, controllerRefs, readableRefs);
  }

  static OntologyNavigationView packet(OntologyReadingPacket packet) {
    Objects.requireNonNull(packet, "ontology reading packet");
    String privateViewId = "packet:" + packet.packetId();
    String modelViewId = modelViewId(privateViewId);
    ObjectNode document = MAPPER.createObjectNode();
    document.put("viewId", modelViewId);
    ArrayNode units = document.putArray("units");
    JsonNode compact = JSON.parseCanonical(packet.modelInput());
    document.set("entryContexts", compact.path("entryContexts").deepCopy());
    Map<String, Target> targets = new LinkedHashMap<>();
    for (int index = 0; index < packet.units().size(); index++) {
      OntologyReadingPacket.PackedUnit packed = packet.units().get(index);
      JsonNode projected = compact.path("units").get(index);
      ObjectNode item = units.addObject();
      item.set("ref", refNode(modelViewId, packed.localRef()));
      item.put("kind", packed.kind().name());
      item.set("entryUses", projected.path("entryUses").deepCopy());
      item.set("content", projected.path("content").deepCopy());
      targets.put(packed.localRef(), Target.packet(packed));
    }
    return new OntologyNavigationView(
        privateViewId, document, targets, targets, Map.of(), Map.of(), Map.of());
  }

  static OntologyNavigationView query(
      String sourceIdentity,
      OntologyEvidenceCorpus corpus,
      String queryKind,
      String lookupKey,
      UnitPage page) {
    String privateViewId =
        viewId(
            "query",
            sourceIdentity
                + "\u0000"
                + queryKind
                + "\u0000"
                + lookupKey
                + "\u0000"
                + page.offset()
                + "\u0000"
                + page.limit());
    String modelViewId = modelViewId(privateViewId);
    ObjectNode document = MAPPER.createObjectNode();
    document.put("viewId", modelViewId);
    document.put("queryKind", queryKind);
    document.put("offset", page.offset());
    document.put("limit", page.limit());
    document.put("total", page.total());
    document.put("unread", page.unread());
    ArrayNode items = document.putArray("items");
    Map<String, Target> targets = new LinkedHashMap<>();
    Map<String, String> entryRefs = new LinkedHashMap<>();
    int nextClue = 1;
    for (int index = 0; index < page.items().size(); index++) {
      UnitHandle handle = page.items().get(index);
      String ref = "U" + (index + 1);
      ObjectNode item = items.addObject();
      item.set("ref", refNode(modelViewId, ref));
      item.put("kind", handle.kind().name());
      UnitNavigationMetadata metadata = unitMetadata(corpus, handle);
      item.put("keyDisplay", metadata.keyDisplay());
      if (metadata.unitBytes() == null) {
        item.putNull("unitBytes");
      } else {
        item.put("unitBytes", metadata.unitBytes());
      }
      addEntryNavigation(corpus, modelViewId, item, targets, entryRefs, handle.entryId());
      targets.put(ref, Target.unit(handle));
      nextClue = addLookupClue(corpus, modelViewId, item, targets, handle, metadata, nextClue);
    }
    return new OntologyNavigationView(
        privateViewId, document, targets, targets, Map.of(), Map.of(), Map.of());
  }

  static OntologyNavigationView literalSearch(
      String sourceIdentity, OntologyEvidenceCorpus corpus, String query, SearchResult result) {
    String privateViewId =
        viewId(
            "literal-search",
            sourceIdentity
                + "\u0000"
                + query
                + "\u0000"
                + result.offset()
                + "\u0000"
                + result.limit()
                + "\u0000"
                + result.totalMatches());
    String modelViewId = modelViewId(privateViewId);
    ObjectNode document = MAPPER.createObjectNode();
    document.put("viewId", modelViewId);
    document.put("offset", result.offset());
    document.put("limit", result.limit());
    document.put("total", result.totalMatches());
    document.put("unread", result.totalMatches() - result.offset() - result.matches().size());
    ArrayNode matches = document.putArray("matches");
    Map<String, Target> targets = new LinkedHashMap<>();
    Map<String, String> entryRefs = new LinkedHashMap<>();
    int nextClue = 1;
    for (int index = 0; index < result.matches().size(); index++) {
      SearchMatch match = result.matches().get(index);
      String ref = "U" + (index + 1);
      ObjectNode item = matches.addObject();
      item.set("ref", refNode(modelViewId, ref));
      item.put("kind", match.kind().name());
      item.put("excerpt", match.excerpt());
      UnitHandle handle = new UnitHandle(match.entryId(), match.kind(), match.originalId());
      UnitNavigationMetadata metadata = unitMetadata(corpus, handle);
      item.put("keyDisplay", metadata.keyDisplay());
      if (metadata.unitBytes() == null) {
        item.putNull("unitBytes");
      } else {
        item.put("unitBytes", metadata.unitBytes());
      }
      addEntryNavigation(corpus, modelViewId, item, targets, entryRefs, match.entryId());
      targets.put(ref, Target.unit(handle));
      nextClue = addLookupClue(corpus, modelViewId, item, targets, handle, metadata, nextClue);
    }
    return new OntologyNavigationView(
        privateViewId, document, targets, targets, Map.of(), Map.of(), Map.of());
  }

  String privateViewId() {
    return privateViewId;
  }

  String modelViewId() {
    return modelViewId;
  }

  JsonNode visible() {
    return visible.deepCopy();
  }

  JsonNode visibleMapping() {
    return mapping(visibleTargets);
  }

  Map<String, Target> visibleReferenceTargets() {
    return visibleTargets;
  }

  JsonNode fullMapping() {
    return mapping(fullTargets);
  }

  String fullMappingFingerprint() {
    return sha256(fullMapping());
  }

  OntologyNavigationView project(Set<ViewRef> requested) {
    Set<String> visibleRefs = new LinkedHashSet<>();
    for (ViewRef reference : requested) {
      if (!modelViewId.equals(reference.viewId())) {
        throw new IllegalArgumentException("ONTOLOGY_VIEW_REFERENCE_UNKNOWN");
      }
      if (!visibleTargets.containsKey(reference.ref())) {
        throw new IllegalArgumentException("ONTOLOGY_VIEW_REFERENCE_UNKNOWN");
      }
      visibleRefs.add(reference.ref());
      String entryRef = ownerEntryRef.get(reference.ref());
      if (entryRef != null) {
        visibleRefs.add(entryRef);
        String controllerRef = controllerRefByEntry.get(entryRef);
        if (controllerRef != null) {
          visibleRefs.add(controllerRef);
        }
      }
      String readableRef = readableUnitRefByClue.get(reference.ref());
      if (readableRef != null) {
        visibleRefs.add(readableRef);
      }
    }
    ObjectNode projected = visible.deepCopy();
    ArrayNode projectedEntries = (ArrayNode) projected.path("entries");
    List<JsonNode> kept = new ArrayList<>();
    for (JsonNode item : projectedEntries) {
      String entryRef = item.path("entryRef").path("ref").asText();
      if (!visibleRefs.contains(entryRef)) {
        continue;
      }
      ObjectNode card = (ObjectNode) item.deepCopy();
      ArrayNode clues = (ArrayNode) card.path("clues");
      List<JsonNode> keptClues = new ArrayList<>();
      for (JsonNode clue : clues) {
        if (visibleRefs.contains(clue.path("ref").path("ref").asText())) {
          keptClues.add(clue);
        }
      }
      clues.removeAll();
      clues.addAll(keptClues);
      updateClueDisclosure(card, keptClues);
      kept.add(card);
    }
    projectedEntries.removeAll();
    projectedEntries.addAll(kept);
    ObjectNode disclosure = (ObjectNode) projected.path("pageDisclosure");
    disclosure.put("shownEntries", kept.size());
    disclosure.put("unreadEntries", disclosure.path("totalEntries").asInt() - kept.size());
    Map<String, Target> projectedTargets = new LinkedHashMap<>();
    for (String ref : visibleRefs) {
      Target target = visibleTargets.get(ref);
      if (target != null) {
        projectedTargets.put(ref, target);
      }
    }
    return new OntologyNavigationView(
        privateViewId,
        projected,
        projectedTargets,
        fullTargets,
        ownerEntryRef,
        controllerRefByEntry,
        readableUnitRefByClue);
  }

  Target resolve(ViewRef reference) {
    if (!modelViewId.equals(reference.viewId())) {
      throw new IllegalArgumentException("ONTOLOGY_VIEW_REFERENCE_UNKNOWN");
    }
    Target target = visibleTargets.get(reference.ref());
    if (target == null) {
      throw new IllegalArgumentException("ONTOLOGY_VIEW_REFERENCE_UNKNOWN");
    }
    return target;
  }

  static Target resolve(List<OntologyNavigationView> views, ViewRef reference) {
    OntologyNavigationView matchingView = null;
    Target target = null;
    for (OntologyNavigationView view : views) {
      if (view.modelViewId.equals(reference.viewId())) {
        if (matchingView != null) {
          requireCompatibleModelView(matchingView, view);
        }
        matchingView = view;
        Target resolved = view.visibleTargets.get(reference.ref());
        if (target == null && resolved != null) {
          target = resolved;
        }
      }
    }
    if (target != null) {
      return target;
    }
    throw new IllegalArgumentException("ONTOLOGY_VIEW_REFERENCE_UNKNOWN");
  }

  static ViewRef parse(JsonNode value) {
    if (!value.isObject()
        || !value.path("viewId").isTextual()
        || !value.path("ref").isTextual()
        || value.size() != 2) {
      throw new IllegalArgumentException("ONTOLOGY_VIEW_REFERENCE_INVALID");
    }
    return new ViewRef(value.path("viewId").asText(), value.path("ref").asText());
  }

  static JsonNode sourceBasis(String sourceIdentity, List<OntologyNavigationView> views) {
    ObjectNode basis = MAPPER.createObjectNode();
    basis.put("sourceIdentity", sourceIdentity);
    ArrayNode mappings = basis.putArray("navigationMappings");
    distinctCompatibleViews(views).stream()
        .sorted(Comparator.comparing(OntologyNavigationView::privateViewId))
        .forEach(
            view -> {
              ObjectNode item = mappings.addObject();
              item.put("modelViewId", view.modelViewId());
              item.put("privateViewId", view.privateViewId());
              item.put("visibleMappingFingerprint", sha256(view.visibleMapping()));
              item.put("fullMappingFingerprint", view.fullMappingFingerprint());
              item.set("fullMapping", view.fullMapping());
            });
    return basis;
  }

  private static List<OntologyNavigationView> distinctCompatibleViews(
      List<OntologyNavigationView> views) {
    Map<String, OntologyNavigationView> byModelViewId = new LinkedHashMap<>();
    for (OntologyNavigationView view : views) {
      OntologyNavigationView existing = byModelViewId.putIfAbsent(view.modelViewId, view);
      if (existing != null) {
        requireCompatibleModelView(existing, view);
      }
    }
    return List.copyOf(byModelViewId.values());
  }

  private static void requireCompatibleModelView(
      OntologyNavigationView first, OntologyNavigationView second) {
    if (!first.privateViewId.equals(second.privateViewId)
        || !first.fullMappingFingerprint().equals(second.fullMappingFingerprint())) {
      throw new IllegalArgumentException("ONTOLOGY_VIEW_ID_COLLISION");
    }
  }

  private JsonNode mapping(Map<String, Target> targets) {
    ObjectNode document = MAPPER.createObjectNode();
    document.put("viewId", privateViewId);
    ArrayNode refs = document.putArray("refs");
    targets.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              ObjectNode item = refs.addObject();
              item.put("ref", entry.getKey());
              entry.getValue().writeMapping(item);
            });
    return document;
  }

  private static void updateClueDisclosure(ObjectNode card, List<JsonNode> clues) {
    Map<String, Integer> shown = new LinkedHashMap<>();
    for (JsonNode clue : clues) {
      shown.merge(clue.path("kind").asText(), 1, Integer::sum);
    }
    ObjectNode disclosure = (ObjectNode) card.path("clueDisclosure");
    for (ClueKind kind : ClueKind.values()) {
      ObjectNode item = (ObjectNode) disclosure.path(kind.name());
      int count = shown.getOrDefault(kind.name(), 0);
      item.put("shown", count);
      item.put("unread", item.path("total").asInt() - count);
    }
  }

  private static ObjectNode refNode(String viewId, String ref) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("viewId", viewId);
    node.put("ref", ref);
    return node;
  }

  private static void addEntryNavigation(
      OntologyEvidenceCorpus corpus,
      String viewId,
      ObjectNode item,
      Map<String, Target> targets,
      Map<String, String> entryRefs,
      String entryId) {
    String entryRef = entryRefs.get(entryId);
    if (entryRef == null) {
      entryRef = "E" + (entryRefs.size() + 1);
      entryRefs.put(entryId, entryRef);
      targets.put(entryRef, Target.entry(entryId));
    }
    EntrySummary entry = corpus.entrySummary(entryId);
    item.set("entryRef", refNode(viewId, entryRef));
    item.put("entryMethod", entry.method());
    item.put("entryRoute", entry.route());
    item.put("entryHandlerFqn", entry.handlerFqn());
  }

  private static int addLookupClue(
      OntologyEvidenceCorpus corpus,
      String viewId,
      ObjectNode item,
      Map<String, Target> targets,
      UnitHandle handle,
      UnitNavigationMetadata metadata,
      int nextClue) {
    NavigationClue clue = lookupClue(corpus, handle, metadata);
    if (clue == null) {
      return nextClue;
    }
    String clueRef = "L" + nextClue;
    item.set("lookupClueRef", refNode(viewId, clueRef));
    targets.put(clueRef, Target.clue(clue));
    return nextClue + 1;
  }

  private static NavigationClue lookupClue(
      OntologyEvidenceCorpus corpus, UnitHandle handle, UnitNavigationMetadata metadata) {
    if (metadata.unitBytes() == null) {
      return null;
    }
    return switch (handle.kind()) {
      case JAVA_METHOD ->
          new NavigationClue(
              ClueKind.METHOD,
              metadata.keyDisplay(),
              handle.originalId(),
              handle,
              corpus.methodUses(handle.originalId(), 0, 1).total(),
              metadata.unitBytes());
      case XML_STATEMENT ->
          new NavigationClue(
              ClueKind.STATEMENT,
              metadata.keyDisplay(),
              handle.originalId(),
              handle,
              corpus.statementUses(handle.originalId(), 0, 1).total(),
              metadata.unitBytes());
      default -> null;
    };
  }

  private static UnitNavigationMetadata unitMetadata(
      OntologyEvidenceCorpus corpus, UnitHandle handle) {
    try {
      return corpus.unitMetadata(handle);
    } catch (IllegalArgumentException unavailable) {
      if ("ONTOLOGY_UNIT_NOT_FOUND".equals(unavailable.getMessage())
          || "ONTOLOGY_UNIT_AMBIGUOUS".equals(unavailable.getMessage())) {
        return new UnitNavigationMetadata(handle.kind().name(), null);
      }
      throw unavailable;
    }
  }

  private static String viewId(String kind, String input) {
    return "ontology-view:"
        + kind
        + ":"
        + OntologyReadingPacket.sha256(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String modelViewId(String privateViewId) {
    String digest =
        OntologyReadingPacket.sha256(
            privateViewId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return "V" + digest.substring(0, 12);
  }

  private static String sha256(JsonNode value) {
    return "sha256:" + OntologyReadingPacket.sha256(JSON.encodeCanonical(value).copyToByteArray());
  }

  record ViewRef(String viewId, String ref) {
    ViewRef {
      if (viewId == null || viewId.isBlank() || ref == null || ref.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_VIEW_REFERENCE_INVALID");
      }
    }
  }

  enum ReferenceKind {
    ENTRY,
    UNIT,
    CLUE,
    PACKET_UNIT
  }

  static final class Target {
    private final ReferenceKind kind;
    private final String entryId;
    private final UnitHandle unit;
    private final NavigationClue clue;
    private final OntologyReadingPacket.PackedUnit packet;

    private Target(
        ReferenceKind kind,
        String entryId,
        UnitHandle unit,
        NavigationClue clue,
        OntologyReadingPacket.PackedUnit packet) {
      this.kind = kind;
      this.entryId = entryId;
      this.unit = unit;
      this.clue = clue;
      this.packet = packet;
    }

    static Target entry(String entryId) {
      return new Target(ReferenceKind.ENTRY, entryId, null, null, null);
    }

    static Target unit(UnitHandle unit) {
      return new Target(ReferenceKind.UNIT, null, unit, null, null);
    }

    static Target clue(NavigationClue clue) {
      return new Target(ReferenceKind.CLUE, null, null, clue, null);
    }

    static Target packet(OntologyReadingPacket.PackedUnit packet) {
      return new Target(ReferenceKind.PACKET_UNIT, null, null, null, packet);
    }

    ReferenceKind kind() {
      return kind;
    }

    String entryId() {
      return entryId;
    }

    UnitHandle unit() {
      return unit;
    }

    NavigationClue clue() {
      return clue;
    }

    OntologyReadingPacket.PackedUnit packet() {
      return packet;
    }

    private void writeMapping(ObjectNode item) {
      item.put("kind", kind.name());
      if (entryId != null) {
        item.put("entryId", entryId);
      }
      if (unit != null) {
        writeUnit(item.putObject("unit"), unit);
      }
      if (clue != null) {
        item.put("clueKind", clue.kind().name());
        item.put("lookupKey", clue.lookupKey());
        writeUnit(item.putObject("readableUnit"), clue.readableUnit());
      }
      if (packet != null) {
        item.put("packetKind", packet.kind().name());
        item.put("originalId", packet.originalId());
        item.set("entryUses", MAPPER.valueToTree(packet.entryUses()));
      }
    }

    private static void writeUnit(ObjectNode item, UnitHandle unit) {
      item.put("entryId", unit.entryId());
      item.put("kind", unit.kind().name());
      item.put("originalId", unit.originalId());
    }
  }
}
