package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ContextRequestAssociation;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EntryDescriptor;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.FormalCallSite;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.FrontendContextSource;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** A closed, source-bound model reading packet with reversible packet-local references. */
public final class OntologyReadingPacket {
  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final Comparator<ImmutableBytes> CANONICAL_ATOM_ORDER =
      (left, right) -> Arrays.compareUnsigned(left.copyToByteArray(), right.copyToByteArray());
  private final String sourceIdentity;
  private final String packetId;
  private final ImmutableBytes canonicalInput;
  private final ImmutableBytes modelInput;
  private final Map<String, Map<String, Integer>> entryLimitations;
  private final Map<String, EntryDescriptor> entryDescriptors;
  private final List<PackedUnit> units;
  private final Map<String, PackedUnit> byLocalRef;
  private final PacketFormat format;
  private final Map<String, String> evidenceUnitRefs;
  private final Map<String, String> entryRefs;
  private final Map<UnitHandle, String> localRefsByUse;
  private final List<FormalCallSite> formalCallSites;
  private final List<FormalContextSource> formalContextSources;
  private final List<FormalContextRequest> formalContextRequests;
  private final ImmutableBytes visibleClues;
  private final PacketCost cost;
  private final ImmutableBytes bundleDecision;

  private OntologyReadingPacket(
      String sourceIdentity,
      List<PackedUnit> units,
      Map<String, Map<String, Integer>> entryLimitations,
      Map<String, EntryDescriptor> entryDescriptors,
      PacketFormat format,
      Map<String, String> evidenceUnitRefs,
      Map<String, String> entryRefs,
      Map<UnitHandle, String> localRefsByUse,
      List<FormalCallSite> formalCallSites,
      List<FormalContextSource> formalContextSources,
      List<FormalContextRequest> formalContextRequests) {
    this(
        sourceIdentity,
        units,
        entryLimitations,
        entryDescriptors,
        format,
        evidenceUnitRefs,
        entryRefs,
        localRefsByUse,
        formalCallSites,
        formalContextSources,
        formalContextRequests,
        JSON.encodeCanonical(new ObjectMapper().createArrayNode()));
  }

  private OntologyReadingPacket(
      String sourceIdentity,
      List<PackedUnit> units,
      Map<String, Map<String, Integer>> entryLimitations,
      Map<String, EntryDescriptor> entryDescriptors,
      PacketFormat format,
      Map<String, String> evidenceUnitRefs,
      Map<String, String> entryRefs,
      Map<UnitHandle, String> localRefsByUse,
      List<FormalCallSite> formalCallSites,
      List<FormalContextSource> formalContextSources,
      List<FormalContextRequest> formalContextRequests,
      ImmutableBytes visibleClues) {
    this(
        sourceIdentity,
        units,
        entryLimitations,
        entryDescriptors,
        format,
        evidenceUnitRefs,
        entryRefs,
        localRefsByUse,
        formalCallSites,
        formalContextSources,
        formalContextRequests,
        visibleClues,
        null);
  }

  private OntologyReadingPacket(
      String sourceIdentity,
      List<PackedUnit> units,
      Map<String, Map<String, Integer>> entryLimitations,
      Map<String, EntryDescriptor> entryDescriptors,
      PacketFormat format,
      Map<String, String> evidenceUnitRefs,
      Map<String, String> entryRefs,
      Map<UnitHandle, String> localRefsByUse,
      List<FormalCallSite> formalCallSites,
      List<FormalContextSource> formalContextSources,
      List<FormalContextRequest> formalContextRequests,
      ImmutableBytes visibleClues,
      ImmutableBytes bundleDecision) {
    this.sourceIdentity = sourceIdentity;
    this.units = List.copyOf(units);
    this.entryLimitations = Map.copyOf(entryLimitations);
    this.entryDescriptors = Map.copyOf(entryDescriptors);
    this.format = Objects.requireNonNull(format, "packet format");
    this.evidenceUnitRefs = Map.copyOf(evidenceUnitRefs);
    this.entryRefs = Map.copyOf(entryRefs);
    this.localRefsByUse = Map.copyOf(localRefsByUse);
    this.formalCallSites = List.copyOf(formalCallSites);
    this.formalContextSources = List.copyOf(formalContextSources);
    this.formalContextRequests = List.copyOf(formalContextRequests);
    this.visibleClues = Objects.requireNonNull(visibleClues, "visible clue context");
    this.bundleDecision = bundleDecision;
    Map<String, PackedUnit> local = new LinkedHashMap<>();
    for (PackedUnit unit : units) {
      if (local.putIfAbsent(unit.localRef(), unit) != null) {
        throw new IllegalArgumentException("ONTOLOGY_LOCAL_REFERENCE_DUPLICATE");
      }
    }
    byLocalRef = Map.copyOf(local);
    canonicalInput = JSON.encodeCanonical(identityDocument());
    packetId = "ontology-packet:" + sha256(canonicalInput.copyToByteArray());
    modelInput = JSON.encodeCanonical(modelDocument());
    cost =
        new PacketCost(
            this.units.stream()
                .mapToInt(
                    unit ->
                        format.isLinkBundle()
                            ? fullBodyBytes(unit.kind(), unit.content())
                            : unit.canonicalJson().size())
                .sum(),
            modelInput.size(),
            canonicalInput.size(),
            this.units.stream()
                .filter(unit -> unit.kind() == UnitKind.JAVA_CALL)
                .mapToInt(unit -> unit.canonicalJson().size())
                .sum());
  }

  /** Attaches actual selected K meanings; short K identifiers alone are not model evidence. */
  public OntologyReadingPacket withVisibleClues(OntologyEvidenceCorpus corpus, List<String> refs) {
    if (!format.hasSharedCalls() || !sourceIdentity.equals(corpus.sourceIdentity())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_CLUE_REFERENCE_INVALID");
    }
    ArrayNode context = new ObjectMapper().createArrayNode();
    for (String ref : new TreeSet<>(refs)) {
      var clue = corpus.aliases().clue(ref);
      ObjectNode item = context.addObject();
      item.put("ref", ref);
      item.put("kind", clue.kind().name());
      item.put("value", clue.keyDisplay());
      item.put("totalUses", clue.totalUses());
      ArrayNode uses = item.putArray("selectedUses");
      for (UnitHandle use : corpus.aliases().clueUses(ref)) {
        String localRef = localRefsByUse.get(use);
        if (localRef != null) {
          ObjectNode row = uses.addObject();
          row.put("sourceRef", localRef);
          row.put("entryRef", corpus.aliases().entryRef(use.entryId()));
        }
      }
    }
    return new OntologyReadingPacket(
        sourceIdentity,
        units,
        entryLimitations,
        entryDescriptors,
        format,
        evidenceUnitRefs,
        entryRefs,
        localRefsByUse,
        formalCallSites,
        formalContextSources,
        formalContextRequests,
        JSON.encodeCanonical(context),
        bundleDecision);
  }

  public static OntologyReadingPacket of(String sourceIdentity, List<EvidenceUnit> selected) {
    if (sourceIdentity == null
        || sourceIdentity.isBlank()
        || selected == null
        || selected.isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_READING_PACKET_EMPTY");
    }
    Map<UnitIdentity, TreeSet<String>> uses = new LinkedHashMap<>();
    Map<String, Map<String, Integer>> entryLimitations = new LinkedHashMap<>();
    Map<String, EntryDescriptor> entryDescriptors = new LinkedHashMap<>();
    for (EvidenceUnit unit : selected) {
      Objects.requireNonNull(unit, "selected evidence unit");
      if (unit.entryId() == null
          || unit.entryId().isBlank()
          || unit.kind() == null
          || unit.originalId() == null
          || unit.originalId().isBlank()
          || unit.canonicalJson() == null) {
        throw new IllegalArgumentException("ONTOLOGY_READING_UNIT_INVALID");
      }
      UnitIdentity identity =
          new UnitIdentity(unit.kind(), unit.originalId(), unit.canonicalJson());
      uses.computeIfAbsent(identity, unused -> new TreeSet<>()).add(unit.entryId());
      Map<String, Integer> previous =
          entryLimitations.putIfAbsent(unit.entryId(), unit.limitationCounts());
      if (previous != null && !previous.equals(unit.limitationCounts())) {
        throw new IllegalArgumentException("ONTOLOGY_ENTRY_LIMITATIONS_CONFLICT");
      }
      EntryDescriptor oldDescriptor =
          entryDescriptors.putIfAbsent(unit.entryId(), unit.entryDescriptor());
      if (oldDescriptor != null && !oldDescriptor.equals(unit.entryDescriptor())) {
        throw new IllegalArgumentException("ONTOLOGY_ENTRY_DESCRIPTOR_CONFLICT");
      }
    }
    List<UnitIdentity> ordered = new ArrayList<>(uses.keySet());
    ordered.sort(
        Comparator.comparing(UnitIdentity::kind)
            .thenComparing(UnitIdentity::originalId)
            .thenComparing(identity -> sha256(identity.canonicalJson().copyToByteArray())));
    List<PackedUnit> packed = new ArrayList<>();
    Map<UnitKind, Integer> counters = new LinkedHashMap<>();
    for (UnitIdentity identity : ordered) {
      int ordinal = counters.merge(identity.kind(), 1, Integer::sum);
      packed.add(
          new PackedUnit(
              prefix(identity.kind()) + ordinal,
              identity.kind(),
              identity.originalId(),
              List.copyOf(uses.get(identity)),
              identity.canonicalJson()));
    }
    return new OntologyReadingPacket(
        sourceIdentity,
        packed,
        entryLimitations,
        entryDescriptors,
        PacketFormat.LEGACY_V2,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        List.of(),
        List.of());
  }

  /**
   * Builds the formal v3 projection from exact Corpus handles. Full selected units remain private
   * and reversible through Corpus U/E aliases; only S references are model-visible.
   */
  public static OntologyReadingPacket formal(
      OntologyEvidenceCorpus corpus, List<UnitHandle> selected, int maxUnitUtf8Bytes) {
    return formal(corpus, selected, maxUnitUtf8Bytes, PacketFormat.FORMAL_V3);
  }

  /**
   * Builds the saved-rule-v2 formal packet family. Its separate format identity keeps later v4
   * mechanical projections from reopening or rewriting a v3 packet.
   */
  public static OntologyReadingPacket formalV4(
      OntologyEvidenceCorpus corpus, List<UnitHandle> selected, int maxUnitUtf8Bytes) {
    return formal(corpus, selected, maxUnitUtf8Bytes, PacketFormat.FORMAL_V4);
  }

  /** New-format packet with exact shared call rows and independently retained entry uses. */
  public static OntologyReadingPacket formalV5(
      OntologyEvidenceCorpus corpus, List<UnitHandle> selected, int maxUnitUtf8Bytes) {
    return formal(corpus, selected, maxUnitUtf8Bytes, PacketFormat.FORMAL_V5);
  }

  /** Freezes the already selected technical bundle; it does not invent a selection or replay it. */
  public static OntologyReadingPacket formalV6(
      OntologyEvidenceCorpus corpus,
      List<UnitHandle> selected,
      int maxUnitUtf8Bytes,
      JsonNode bundleDecision) {
    return formalV6(corpus, selected, maxUnitUtf8Bytes, bundleDecision, true);
  }

  static OntologyReadingPacket restoreFormalV6(
      OntologyEvidenceCorpus corpus,
      List<UnitHandle> selected,
      int maxUnitUtf8Bytes,
      JsonNode bundleDecision) {
    return formalV6(corpus, selected, maxUnitUtf8Bytes, bundleDecision, false);
  }

  private static OntologyReadingPacket formalV6(
      OntologyEvidenceCorpus corpus,
      List<UnitHandle> selected,
      int maxUnitUtf8Bytes,
      JsonNode bundleDecision,
      boolean expandSourceContexts) {
    return formalBundle(
        corpus,
        selected,
        maxUnitUtf8Bytes,
        bundleDecision,
        expandSourceContexts,
        PacketFormat.FORMAL_V6);
  }

  /** Additive lean profile: complete sources stay private and only selected call detail is sent. */
  public static OntologyReadingPacket formalV7(
      OntologyEvidenceCorpus corpus,
      List<UnitHandle> selected,
      int maxUnitUtf8Bytes,
      JsonNode bundleDecision) {
    return formalBundle(
        corpus, selected, maxUnitUtf8Bytes, bundleDecision, true, PacketFormat.FORMAL_V7);
  }

  static OntologyReadingPacket restoreFormalV7(
      OntologyEvidenceCorpus corpus,
      List<UnitHandle> selected,
      int maxUnitUtf8Bytes,
      JsonNode bundleDecision) {
    return formalBundle(
        corpus, selected, maxUnitUtf8Bytes, bundleDecision, false, PacketFormat.FORMAL_V7);
  }

  private static OntologyReadingPacket formalBundle(
      OntologyEvidenceCorpus corpus,
      List<UnitHandle> selected,
      int maxUnitUtf8Bytes,
      JsonNode bundleDecision,
      boolean expandSourceContexts,
      PacketFormat format) {
    boolean typeMaterial =
        format == PacketFormat.FORMAL_V7
            && bundleDecision != null
            && "object-type-material-rule-v1".equals(bundleDecision.path("ruleVersion").asText());
    if (bundleDecision == null
        || !bundleDecision.isObject()
        || !(typeMaterial
                ? "object-type-material-rule-v1"
                : format == PacketFormat.FORMAL_V7 ? "link-bundle-rule-v2" : "link-bundle-rule-v1")
            .equals(bundleDecision.path("ruleVersion").asText())
        || !bundleDecision
            .path("anchorRef")
            .asText()
            .matches(typeMaterial ? "pair:[0-9a-f]{64}" : "K[1-9][0-9]*")
        || !Set.of("EXPLICIT", "MODEL").contains(bundleDecision.path("selectionOrigin").asText())
        || !bundleDecision.path("cost").isObject()) {
      throw new IllegalArgumentException("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
    }
    Set<String> decisionFields =
        Set.of(
            "ruleVersion",
            "anchorRef",
            "selectionOrigin",
            "seedUses",
            "derivedUses",
            "derivedEntries",
            "groups",
            "requiredButUnread",
            "unreadCandidates",
            "cost");
    Set<String> actualFields = new LinkedHashSet<>();
    bundleDecision.fieldNames().forEachRemaining(actualFields::add);
    if (!actualFields.equals(decisionFields)) {
      throw new IllegalArgumentException("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
    }
    for (String field :
        List.of(
            "seedUses",
            "derivedUses",
            "derivedEntries",
            "groups",
            "requiredButUnread",
            "unreadCandidates")) {
      if (!bundleDecision.path(field).isArray()) {
        throw new IllegalArgumentException("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
      }
    }
    validateBundleGroups(bundleDecision);
    if (typeMaterial
        && (!"EXPLICIT".equals(bundleDecision.path("selectionOrigin").asText())
            || !bundleDecision.path("groups").isEmpty())) {
      throw new IllegalArgumentException("ONTOLOGY_OBJECT_TYPE_MATERIAL_DECISION_INVALID");
    }
    OntologyReadingPacket packet =
        formal(corpus, selected, maxUnitUtf8Bytes, format, expandSourceContexts);
    return new OntologyReadingPacket(
        packet.sourceIdentity,
        packet.units,
        packet.entryLimitations,
        packet.entryDescriptors,
        packet.format,
        packet.evidenceUnitRefs,
        packet.entryRefs,
        packet.localRefsByUse,
        packet.formalCallSites,
        packet.formalContextSources,
        packet.formalContextRequests,
        packet.visibleClues,
        JSON.encodeCanonical(bundleDecision));
  }

  private static void validateBundleGroups(JsonNode decision) {
    int ordinal = 0;
    Set<String> groupFields =
        Set.of(
            "groupRef",
            "matchKind",
            "technicalRefs",
            "unitUses",
            "pageInstanceRef",
            "unitBytes",
            "projectedIncrementBytes",
            "outcome",
            "issueCode");
    for (JsonNode group : decision.path("groups")) {
      Set<String> actual = new LinkedHashSet<>();
      group.fieldNames().forEachRemaining(actual::add);
      if (!group.isObject()
          || !actual.equals(groupFields)
          || !("G" + (++ordinal)).equals(group.path("groupRef").asText())
          || !Set.of("PAGE_CONTEXT", "ANCHOR_SOURCE", "LEXICAL_MATCH", "STRUCTURED_REFERENCE")
              .contains(group.path("matchKind").asText())
          || !Set.of("INCLUDED", "UNAVAILABLE", "CAPACITY_BLOCKED")
              .contains(group.path("outcome").asText())
          || !group.path("technicalRefs").isArray()
          || group.path("technicalRefs").size() != 1
          || !decision.path("anchorRef").equals(group.path("technicalRefs").get(0))
          || !group.path("unitUses").isArray()
          || !group.path("projectedIncrementBytes").canConvertToInt()
          || !group.path("projectedIncrementBytes").isIntegralNumber()
          || group.path("projectedIncrementBytes").asInt() < 0
          || !(group.path("pageInstanceRef").isNull() || group.path("pageInstanceRef").isTextual())
          || !(group.path("unitBytes").isNull()
              || (group.path("unitBytes").isIntegralNumber()
                  && group.path("unitBytes").canConvertToInt()
                  && group.path("unitBytes").asInt() >= 0))) {
        throw new IllegalArgumentException("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
      }
      boolean included = "INCLUDED".equals(group.path("outcome").asText());
      if (included
          ? (!group.path("issueCode").isNull()
              || group.path("unitUses").isEmpty()
              || group.path("unitBytes").isNull())
          : (!group.path("issueCode").isTextual() || group.path("issueCode").asText().isBlank())) {
        throw new IllegalArgumentException("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
      }
      for (JsonNode use : group.path("unitUses")) {
        if (!use.isObject()
            || use.size() != 2
            || !use.path("unitRef").asText().matches("U[1-9][0-9]*")
            || !use.path("entryRef").asText().matches("E[1-9][0-9]*")) {
          throw new IllegalArgumentException("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
        }
      }
    }
    for (var fields = decision.path("cost").fields(); fields.hasNext(); ) {
      var field = fields.next();
      if (!Set.of("sourceBytes", "projectionBytes").contains(field.getKey())
          || !field.getValue().isIntegralNumber()
          || !field.getValue().canConvertToInt()
          || field.getValue().asInt() < 0) {
        throw new IllegalArgumentException("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
      }
    }
  }

  static int fullBodyBytes(UnitKind kind, JsonNode source) {
    String text =
        switch (kind) {
          case JAVA_METHOD -> source.path("source").path("text").asText();
          case FRONTEND_UNIT -> source.path("text").asText();
          case XML_STATEMENT ->
              source.path("xmlSubtree").isTextual()
                  ? source.path("xmlSubtree").asText()
                  : source.path("xmlSubtree").toString();
          case XML_RESOURCE -> source.path("rawSource").asText();
          case SQL_ANALYSIS -> source.path("analysisCopy").asText();
          case SOURCE_REFERENCE -> source.path("sourceText").asText();
          case SCHEMA_SOURCE -> source.path("sourceText").asText();
          default -> "";
        };
    return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  }

  private static OntologyReadingPacket formal(
      OntologyEvidenceCorpus corpus,
      List<UnitHandle> selected,
      int maxUnitUtf8Bytes,
      PacketFormat format) {
    return formal(corpus, selected, maxUnitUtf8Bytes, format, true);
  }

  /** Replays an already frozen v5 set, without expanding its automatically added source units. */
  static OntologyReadingPacket restoreFormalV5(
      OntologyEvidenceCorpus corpus, List<UnitHandle> frozen, int maxUnitUtf8Bytes) {
    return formal(corpus, frozen, maxUnitUtf8Bytes, PacketFormat.FORMAL_V5, false);
  }

  private static OntologyReadingPacket formal(
      OntologyEvidenceCorpus corpus,
      List<UnitHandle> selected,
      int maxUnitUtf8Bytes,
      PacketFormat format,
      boolean expandSourceContexts) {
    if (corpus == null || selected == null || selected.isEmpty() || maxUnitUtf8Bytes < 1) {
      throw new IllegalArgumentException("ONTOLOGY_READING_PACKET_EMPTY");
    }
    OntologyEvidenceCorpus.AliasCatalog aliases = corpus.aliases();
    Map<UnitHandle, List<FrontendContextSource>> contextSources = new LinkedHashMap<>();
    LinkedHashSet<UnitHandle> expanded = new LinkedHashSet<>();
    for (UnitHandle handle : selected) {
      Objects.requireNonNull(handle, "selected unit handle");
      // Preserve the established selected-handle failure before resolving page dependencies.
      aliases.unitRef(handle);
      expanded.add(handle);
      if (format.hasSharedCalls()
          && expandSourceContexts
          && handle.kind() == UnitKind.FRONTEND_UNIT) {
        for (UnitHandle context : corpus.frontendPageContexts(handle)) {
          expanded.add(context);
          List<FrontendContextSource> required = corpus.frontendContextSources(context);
          contextSources.put(context, required);
          required.forEach(source -> expanded.add(source.unit()));
        }
      }
      if (handle.kind() == UnitKind.FRONTEND_PAGE_CONTEXT) {
        List<FrontendContextSource> required = corpus.frontendContextSources(handle);
        contextSources.put(handle, required);
        required.forEach(source -> expanded.add(source.unit()));
      }
    }
    Map<PacketUnitGroup, LinkedHashSet<UnitHandle>> usesByEvidenceRef = new TreeMap<>();
    Map<UnitHandle, EvidenceUnit> evidenceByUse = new LinkedHashMap<>();
    Map<String, Map<String, Integer>> entryLimitations = new LinkedHashMap<>();
    Map<String, EntryDescriptor> entryDescriptors = new LinkedHashMap<>();
    Map<String, String> entryRefs = new LinkedHashMap<>();
    for (UnitHandle handle : expanded) {
      String evidenceRef = aliases.unitRef(handle);
      String entryRef = aliases.entryRef(handle.entryId());
      EvidenceUnit evidence = aliases.read(evidenceRef, entryRef);
      int unitBytes =
          (format.isLinkBundle()
              ? fullBodyBytes(evidence.kind(), evidence.content())
              : evidence.canonicalJson().size());
      if (unitBytes > maxUnitUtf8Bytes) {
        if (format == PacketFormat.FORMAL_V7) {
          ObjectNode cost = new ObjectMapper().createObjectNode();
          cost.put("boundary", "COMPLETE_UNIT_BODY");
          cost.put("measuredBytes", unitBytes);
          cost.put("limitBytes", maxUnitUtf8Bytes);
          throw new OntologyTypedTaskRunner.FormalPreparationFailure(
              "ONTOLOGY_UNIT_TOO_LARGE", "PREPARE", evidenceRef, cost);
        }
        throw new IllegalArgumentException("ONTOLOGY_UNIT_TOO_LARGE");
      }
      evidenceByUse.put(handle, evidence);
      PacketUnitGroup packetGroup =
          new PacketUnitGroup(
              evidenceRef,
              handle.kind() == UnitKind.FRONTEND_PAGE_CONTEXT ? handle.entryId() : null);
      usesByEvidenceRef.computeIfAbsent(packetGroup, ignored -> new LinkedHashSet<>()).add(handle);
      Map<String, Integer> previous =
          entryLimitations.putIfAbsent(handle.entryId(), evidence.limitationCounts());
      if (previous != null && !previous.equals(evidence.limitationCounts())) {
        throw new IllegalArgumentException("ONTOLOGY_ENTRY_LIMITATIONS_CONFLICT");
      }
      EntryDescriptor previousDescriptor =
          entryDescriptors.putIfAbsent(handle.entryId(), evidence.entryDescriptor());
      if (previousDescriptor != null && !previousDescriptor.equals(evidence.entryDescriptor())) {
        throw new IllegalArgumentException("ONTOLOGY_ENTRY_DESCRIPTOR_CONFLICT");
      }
      entryRefs.put(handle.entryId(), entryRef);
    }

    List<PackedUnit> packed = new ArrayList<>();
    Map<String, String> evidenceUnitRefs = new LinkedHashMap<>();
    Map<UnitHandle, String> localRefsByUse = new LinkedHashMap<>();
    for (Map.Entry<PacketUnitGroup, LinkedHashSet<UnitHandle>> entry :
        usesByEvidenceRef.entrySet()) {
      List<UnitHandle> uses = new ArrayList<>(entry.getValue());
      uses.sort(unitHandleOrder());
      EvidenceUnit evidence = evidenceByUse.get(uses.get(0));
      String localRef = "S" + (packed.size() + 1);
      packed.add(
          new PackedUnit(
              localRef,
              evidence.kind(),
              evidence.originalId(),
              uses.stream().map(UnitHandle::entryId).distinct().sorted().toList(),
              evidence.canonicalJson()));
      evidenceUnitRefs.put(localRef, entry.getKey().evidenceUnitRef());
      uses.forEach(use -> localRefsByUse.put(use, localRef));
    }
    List<FormalContextSource> formalContextSources = new ArrayList<>();
    List<FormalContextRequest> formalContextRequests = new ArrayList<>();
    Map<ContextRequestIdentity, String> requestRefs = new LinkedHashMap<>();
    List<UnitHandle> orderedContexts = new ArrayList<>(contextSources.keySet());
    orderedContexts.sort(unitHandleOrder());
    for (UnitHandle context : orderedContexts) {
      String contextRef = localRefsByUse.get(context);
      if (contextRef == null) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REFERENCE_MISSING");
      }
      for (FrontendContextSource source : contextSources.get(context)) {
        String sourceRef = localRefsByUse.get(source.unit());
        if (sourceRef == null) {
          throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_MISSING");
        }
        formalContextSources.add(
            new FormalContextSource(context, contextRef, source.unitRef(), sourceRef));
      }
      for (ContextRequestAssociation association : corpus.contextRequestAssociations(context)) {
        ContextRequestIdentity identity =
            new ContextRequestIdentity(
                association.requestId(),
                association.associationStatus(),
                association.resolution(),
                association.reason(),
                association.candidateEntryCount(),
                association.requestCanonicalJson());
        String requestRef =
            requestRefs.computeIfAbsent(identity, ignored -> "R" + (requestRefs.size() + 1));
        formalContextRequests.add(
            new FormalContextRequest(context, contextRef, requestRef, association));
      }
    }
    List<FormalCallSite> formalCallSites =
        corpus.formalCallSites(
            Set.copyOf(evidenceByUse.keySet()), format == PacketFormat.FORMAL_V7);
    return new OntologyReadingPacket(
        corpus.sourceIdentity(),
        packed,
        entryLimitations,
        entryDescriptors,
        format,
        evidenceUnitRefs,
        entryRefs,
        localRefsByUse,
        formalCallSites,
        formalContextSources,
        formalContextRequests);
  }

  public String sourceIdentity() {
    return sourceIdentity;
  }

  ImmutableBytes bundleDecision() {
    return bundleDecision;
  }

  public String packetId() {
    return packetId;
  }

  /** Exact private source identity, usage map, and complete selected units. */
  public ImmutableBytes canonicalInput() {
    return canonicalInput;
  }

  /** Compact model-facing projection; canonicalInput remains the complete private identity. */
  public ImmutableBytes modelInput() {
    return modelInput;
  }

  /** Exact model projection schema bound into the formal task identity. */
  public String modelProjectionVersion() {
    return format.modelSchemaVersion();
  }

  /** Formal call metadata encoding bound into the formal task identity. */
  public String callContextEncoding() {
    if (!format.isFormal()) {
      throw new IllegalStateException("ONTOLOGY_FORMAL_PACKET_REQUIRED");
    }
    return format == PacketFormat.FORMAL_V7
        ? "EXACT_LEAN_LINK_BUNDLE_V2"
        : format == PacketFormat.FORMAL_V6
            ? "EXACT_LINK_BUNDLE_V1"
            : format == PacketFormat.FORMAL_V5 ? "EXACT_ROWS_WITH_USES_V1" : "EXACT_ATOMS_V1";
  }

  public List<PackedUnit> units() {
    return units;
  }

  /** Measured UTF-8 canonical-unit, model, private, and explicitly selected call-detail bytes. */
  public PacketCost cost() {
    return cost;
  }

  public PackedUnit resolve(String localRef) {
    PackedUnit unit = byLocalRef.get(localRef);
    if (unit == null) {
      throw new IllegalArgumentException("ONTOLOGY_LOCAL_REFERENCE_UNKNOWN");
    }
    return unit;
  }

  /** Returns the existing formal Corpus unit alias for one packet-local source reference. */
  public String evidenceUnitRef(String localRef) {
    resolve(localRef);
    String evidenceUnitRef = evidenceUnitRefs.get(localRef);
    if (evidenceUnitRef == null) {
      throw new IllegalArgumentException("ONTOLOGY_LOCAL_REFERENCE_UNKNOWN");
    }
    return evidenceUnitRef;
  }

  /**
   * Returns the existing formal entry aliases for the physical entry uses of one source reference.
   */
  public List<String> entryRefs(String localRef) {
    List<String> aliases = new ArrayList<>();
    for (String entryId : resolve(localRef).entryUses()) {
      String entryRef = entryRefs.get(entryId);
      if (entryRef == null) {
        throw new IllegalArgumentException("ONTOLOGY_LOCAL_REFERENCE_UNKNOWN");
      }
      aliases.add(entryRef);
    }
    return List.copyOf(aliases);
  }

  private ObjectNode identityDocument() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root = mapper.createObjectNode();
    if (format.isFormal()) {
      root.put("schemaVersion", format.privateSchemaVersion());
    }
    if (format.hasSharedCalls()) {
      root.set("visibleClues", JSON.parseCanonical(visibleClues));
    }
    if (format.isLinkBundle() && bundleDecision != null) {
      root.set("bundleDecision", JSON.parseCanonical(bundleDecision));
    }
    root.put("sourceIdentity", sourceIdentity);
    ArrayNode content = root.putArray("units");
    for (PackedUnit unit : units) {
      ObjectNode item = content.addObject();
      item.put("localRef", unit.localRef());
      item.put("kind", unit.kind().name());
      item.put("originalId", unit.originalId());
      if (format.isFormal()) {
        item.put("evidenceUnitRef", evidenceUnitRefs.get(unit.localRef()));
      }
      ArrayNode entryUses = item.putArray("entryUses");
      unit.entryUses().forEach(entryUses::add);
      item.set("content", JSON.parseCanonical(unit.canonicalJson()));
    }
    ObjectNode contexts = root.putObject("entryLimitations");
    entryLimitations.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> contexts.set(entry.getKey(), mapper.valueToTree(entry.getValue())));
    ObjectNode descriptors = root.putObject("entryDescriptors");
    entryDescriptors.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> descriptors.set(entry.getKey(), mapper.valueToTree(entry.getValue())));
    if (format.isFormal()) {
      ObjectNode aliasEntries = root.putObject("entryAliases");
      entryRefs.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(entry -> aliasEntries.put(entry.getKey(), entry.getValue()));
      ArrayNode callSites = root.putArray("formalCallSites");
      for (FormalCallSite call : formalCallSites) {
        ObjectNode item = callSites.addObject();
        item.set("caller", unitHandleDocument(call.caller()));
        item.set("call", unitHandleDocument(call.call()));
        item.put("resolution", call.status());
        if (!call.selectedTargets().isEmpty()) {
          ArrayNode selectedTargets = item.putArray("selectedTargets");
          call.selectedTargets().forEach(target -> selectedTargets.add(unitHandleDocument(target)));
        }
        item.set("canonicalCall", JSON.parseCanonical(call.canonicalCall()));
      }
      ArrayNode contextSources = root.putArray("formalContextSources");
      for (FormalContextSource source : formalContextSources) {
        ObjectNode item = contextSources.addObject();
        item.set("context", unitHandleDocument(source.context()));
        item.put("contextRef", source.contextRef());
        item.put("unitRef", source.contextUnitRef());
        item.put("sourceRef", source.sourceRef());
      }
      ArrayNode contextRequests = root.putArray("formalContextRequests");
      for (FormalContextRequest request : formalContextRequests) {
        ContextRequestAssociation association = request.association();
        ObjectNode item = contextRequests.addObject();
        item.set("context", unitHandleDocument(request.context()));
        item.put("contextRef", request.contextRef());
        item.put("requestRef", request.requestRef());
        item.put("requestId", association.requestId());
        item.put("associationStatus", association.associationStatus());
        item.put("resolution", association.resolution());
        if (association.reason() == null) {
          item.putNull("reason");
        } else {
          item.put("reason", association.reason());
        }
        item.put("candidateEntryCount", association.candidateEntryCount());
        item.set("request", JSON.parseCanonical(association.requestCanonicalJson()));
      }
    }
    return root;
  }

  private ObjectNode modelDocument() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", format.modelSchemaVersion());
    if (format.isLinkBundle() && bundleDecision != null) {
      JsonNode decision = JSON.parseCanonical(bundleDecision);
      OntologyModelProjection.projectUnreadCandidates(root, decision.path("unreadCandidates"));
      root.set("requiredButUnread", decision.path("requiredButUnread").deepCopy());
    }
    if (format.hasSharedCalls()) {
      root.set("visibleClues", JSON.parseCanonical(visibleClues));
    }
    if (format.isFormal()) {
      root.put("callContextEncoding", callContextEncoding());
      root.put(
          "callEvidenceInstruction",
          (format.hasSharedCalls()
                  ? "callUses in ordinal order reference callRows by rowRef (C); site columns are"
                      + " startOffsetUtf16,lengthUtf16,startLine,endLine. Each use keeps its entry,"
                      + " caller and selected targets. "
                  : "")
              + "targetRefs resolve in callEvidence.targets and observationRefs resolve in"
              + " callEvidence.observations; CT and CO are packet-local lookup keys for call"
              + " metadata only, not source citations, evidence references, object identifiers, or"
              + " ontology definitions.");
    }
    Map<String, String> visibleEntryRefs = visibleEntryRefs();
    writeEntryContexts(root, mapper, visibleEntryRefs);
    writeModelUnits(root, visibleEntryRefs);
    if (format.isFormal()) {
      writeFormalCallEvidence(root);
      if (format.hasSharedCalls()) {
        writeSharedCallRows(root);
      }
    }
    if (format.isLinkBundle()) OntologyModelProjection.internFrontendObservations(root);
    if (format == PacketFormat.FORMAL_V7) {
      root.put("projectionScope", "COMPLETE_SELECTED_BODIES_AND_USES_WITH_SELECTED_CALL_DETAILS");
      root.put("unselectedCallDetails", "PRIVATE_FORMAL_CALL_SITES");
      ArrayNode omittedFields = root.putArray("privateConvenienceFields");
      List.of("signature", "returnTypeText", "annotations", "controls", "exits")
          .forEach(omittedFields::add);
      ObjectNode unread = root.putObject("unreadSummary");
      unread.put("selectedCallUses", root.path("callUses").size());
      unread.put(
          "privateUnselectedCallUses", formalCallSites.size() - root.path("callUses").size());
      unread.put("privateDirectory", "formalCallSites");
      unread.put("unreadCandidates", root.path("unreadCandidates").size());
      unread.put("requiredButUnread", root.path("requiredButUnread").size());
    }
    return root;
  }

  private Map<String, String> visibleEntryRefs() {
    Map<String, String> visibleEntryRefs = new LinkedHashMap<>();
    if (format.isFormal()) {
      entryRefs.entrySet().stream()
          .sorted(Map.Entry.comparingByValue())
          .forEach(entry -> visibleEntryRefs.put(entry.getKey(), entry.getValue()));
    } else {
      TreeSet<String> entryIds = new TreeSet<>();
      units.forEach(unit -> entryIds.addAll(unit.entryUses()));
      for (String entryId : entryIds) {
        visibleEntryRefs.put(entryId, "E" + (visibleEntryRefs.size() + 1));
      }
    }
    return visibleEntryRefs;
  }

  private void writeEntryContexts(
      ObjectNode root, ObjectMapper mapper, Map<String, String> visibleEntryRefs) {
    ArrayNode contexts = root.putArray("entryContexts");
    visibleEntryRefs.forEach(
        (entryId, ref) -> {
          ObjectNode context = contexts.addObject();
          context.put("entryRef", ref);
          EntryDescriptor descriptor =
              entryDescriptors.getOrDefault(entryId, new EntryDescriptor("", "", ""));
          if (!descriptor.method().isBlank()) {
            context.put("method", descriptor.method());
          }
          if (!descriptor.route().isBlank()) {
            context.put("route", descriptor.route());
          }
          if (!descriptor.handlerFqn().isBlank()) {
            context.put("handler", descriptor.handlerFqn());
          }
          context.set(
              "limitationCounts",
              mapper.valueToTree(entryLimitations.getOrDefault(entryId, Map.of())));
        });
  }

  private void writeModelUnits(ObjectNode root, Map<String, String> visibleEntryRefs) {
    ArrayNode content = root.putArray("units");
    for (PackedUnit unit : units) {
      ObjectNode item = content.addObject();
      item.put("ref", unit.localRef());
      item.put("kind", unit.kind().name());
      ArrayNode uses = item.putArray("entryUses");
      unit.entryUses().forEach(entryId -> uses.add(visibleEntryRefs.get(entryId)));
      JsonNode projected = modelUnitContent(unit);
      if (format.isFormal() && unit.kind() == UnitKind.FRONTEND_PAGE_CONTEXT) {
        addContextRequestAssociations((ObjectNode) projected, unit.localRef());
      }
      item.set("content", projected);
    }
  }

  private JsonNode modelUnitContent(PackedUnit unit) {
    if (format.isLinkBundle()) {
      ObjectNode result =
          (ObjectNode)
              OntologyModelProjection.projectFormalV4(
                  unit.kind(),
                  unit.content(),
                  contextSourceRefs(unit.localRef()),
                  contextRequestRefs(unit.localRef()),
                  hasVisibleFormalCallContext(unit.localRef()));
      if (unit.kind() == UnitKind.JAVA_METHOD) {
        result.remove(List.of("signature", "returnTypeText", "controls", "exits", "annotations"));
      }
      return result;
    }
    return format == PacketFormat.FORMAL_V3
        ? OntologyModelProjection.projectFormal(
            unit.kind(),
            unit.content(),
            contextSourceRefs(unit.localRef()),
            contextRequestRefs(unit.localRef()))
        : format == PacketFormat.FORMAL_V4 || format == PacketFormat.FORMAL_V5
            ? OntologyModelProjection.projectFormalV4(
                unit.kind(),
                unit.content(),
                contextSourceRefs(unit.localRef()),
                contextRequestRefs(unit.localRef()),
                hasVisibleFormalCallContext(unit.localRef()))
            : OntologyModelProjection.project(unit.kind(), unit.content());
  }

  private void writeFormalCallEvidence(ObjectNode root) {
    Map<ImmutableBytes, JsonNode> targetAtoms = new TreeMap<>(CANONICAL_ATOM_ORDER);
    Map<ImmutableBytes, JsonNode> observationAtoms = new TreeMap<>(CANONICAL_ATOM_ORDER);
    for (FormalCallSite call : formalCallSites) {
      String callerRef = localRefsByUse.get(call.caller());
      String callRef = localRefsByUse.get(call.call());
      if (callerRef == null
          || !(requiresObservationContext(call.status())
              || (hasV4CallDetails() && callRef != null))) {
        continue;
      }
      JsonNode canonicalCall = JSON.parseCanonical(call.canonicalCall());
      if (format != PacketFormat.FORMAL_V7 || hasSelectedCallDetail(call)) {
        collectCallEvidenceAtoms(targetAtoms, projectedCallTargets(canonicalCall));
      }
      collectCallEvidenceAtoms(
          observationAtoms,
          OntologyModelProjection.projectCallObservations(canonicalCall.path("observations")));
    }
    ObjectNode callEvidence = root.putObject("callEvidence");
    CallEvidenceReferences references =
        new CallEvidenceReferences(
            writeCallEvidenceDictionary(callEvidence.putObject("targets"), "CT", targetAtoms),
            writeCallEvidenceDictionary(
                callEvidence.putObject("observations"), "CO", observationAtoms));
    writeFormalCallContexts(root.putArray("callContext"), references);
  }

  private void writeFormalCallContexts(ArrayNode callContext, CallEvidenceReferences references) {
    for (FormalCallSite call : formalCallSites) {
      String callerRef = localRefsByUse.get(call.caller());
      if (callerRef == null) {
        continue;
      }
      ObjectNode item = callContext.addObject();
      item.put("fromRef", callerRef);
      item.put("resolution", call.status());
      JsonNode canonicalCall = JSON.parseCanonical(call.canonicalCall());
      String selectedCallRef = localRefsByUse.get(call.call());
      boolean callDetailsVisible =
          requiresObservationContext(call.status())
              || (hasV4CallDetails() && selectedCallRef != null);
      if (selectedCallRef != null) {
        item.put("callRef", selectedCallRef);
      }
      writeSelectedTargets(item, call, canonicalCall);
      if (canonicalCall.has("actualArguments")) {
        item.set("actualArguments", canonicalCall.path("actualArguments").deepCopy());
      }
      String detail = canonicalCall.path("resolutionDetail").asText();
      if (!detail.isBlank()) {
        item.put("detail", detail);
      }
      ArrayNode targetRefValues = item.putArray("targetRefs");
      ArrayNode observationRefValues = item.putArray("observationRefs");
      if (callDetailsVisible) {
        if (format != PacketFormat.FORMAL_V7 || hasSelectedCallDetail(call)) {
          addCallEvidenceReferences(
              targetRefValues, references.targets(), projectedCallTargets(canonicalCall));
        }
        addCallEvidenceReferences(
            observationRefValues,
            references.observations(),
            OntologyModelProjection.projectCallObservations(canonicalCall.path("observations")));
      }
      item.set("site", OntologyModelProjection.projectCallSite(canonicalCall.path("site")));
    }
  }

  private void writeSelectedTargets(ObjectNode item, FormalCallSite call, JsonNode canonicalCall) {
    if (call.selectedTargets().isEmpty()) {
      return;
    }
    ArrayNode selectedTargets = item.putArray("selectedTargets");
    for (UnitHandle selectedTarget : call.selectedTargets()) {
      String targetRef = localRefsByUse.get(selectedTarget);
      if (targetRef == null) {
        continue;
      }
      JsonNode target = selectedTarget(canonicalCall, selectedTarget);
      ObjectNode selectedTargetNode = selectedTargets.addObject();
      selectedTargetNode.put("targetRef", targetRef);
      if (target.has("roles")) {
        selectedTargetNode.set("roles", target.path("roles").deepCopy());
      }
      if (hasV4CallDetails()) {
        if (target.has("displayName")) {
          selectedTargetNode.set("displayName", target.path("displayName").deepCopy());
        }
        if (target.has("expansion")) {
          selectedTargetNode.set("expansion", target.path("expansion").deepCopy());
        }
        if (target.has("reason")) {
          selectedTargetNode.set("reason", target.path("reason").deepCopy());
        }
      }
      if (target.has("argumentAssociations")) {
        selectedTargetNode.set(
            "argumentAssociations", target.path("argumentAssociations").deepCopy());
      }
      // v3 retains its historical convenience copy. In v4 selectedTargets is the complete
      // reversible target mapping, so a second first-target/argument copy adds no fact.
      if (format == PacketFormat.FORMAL_V3 && !item.has("targetRef")) {
        item.put("targetRef", targetRef);
        if (target.has("argumentAssociations")) {
          item.set("argumentAssociations", target.path("argumentAssociations").deepCopy());
        }
      }
    }
  }

  private JsonNode projectedCallTargets(JsonNode canonicalCall) {
    return hasV4CallDetails()
        ? OntologyModelProjection.projectCallTargetsV4(canonicalCall.path("targets"))
        : OntologyModelProjection.projectCallTargets(canonicalCall.path("targets"));
  }

  private boolean hasV4CallDetails() {
    return format == PacketFormat.FORMAL_V4
        || format == PacketFormat.FORMAL_V5
        || format.isLinkBundle();
  }

  private boolean hasSelectedCallDetail(FormalCallSite call) {
    return localRefsByUse.containsKey(call.call()) || !call.selectedTargets().isEmpty();
  }

  /**
   * Share only exact call facts from the same physical caller and full saved observation. S/E
   * references and original traversal order stay in uses; semantic facts are never unioned.
   */
  private void writeSharedCallRows(ObjectNode root) {
    JsonNode contexts = root.remove("callContext");
    ArrayNode rows = root.putArray("callRows");
    ArrayNode uses = root.putArray("callUses");
    ArrayNode limitations =
        format == PacketFormat.FORMAL_V7 ? root.putArray("limitationRows") : null;
    Map<ImmutableBytes, String> rowRefs = new LinkedHashMap<>();
    int contextIndex = 0;
    for (FormalCallSite call : formalCallSites) {
      String callerRef = localRefsByUse.get(call.caller());
      if (callerRef == null) {
        continue;
      }
      ObjectNode row = ((ObjectNode) contexts.get(contextIndex++)).deepCopy();
      if (format == PacketFormat.FORMAL_V7 && !hasSelectedCallDetail(call)) {
        if ("LOCATED".equals(row.path("resolution").asText())) continue;
        ObjectNode limitation = limitations.addObject();
        limitation.put("entryRef", entryRefs.get(call.caller().entryId()));
        for (String field : List.of("fromRef", "resolution", "detail", "observationRefs")) {
          if (row.has(field)) limitation.set(field, row.path(field).deepCopy());
        }
        ArrayNode site = limitation.putArray("site");
        for (String field : List.of("startOffsetUtf16", "lengthUtf16", "startLine", "endLine"))
          site.add(row.path("site").path(field).deepCopy());
        continue;
      }
      ObjectNode use = uses.addObject();
      use.put("ordinal", uses.size() - 1);
      use.put("entryRef", entryRefs.get(call.caller().entryId()));
      use.set("fromRef", row.remove("fromRef"));
      if (row.has("callRef")) {
        use.set("callRef", row.remove("callRef"));
      }
      if (row.has("selectedTargets")) {
        ArrayNode targetUses = use.putArray("selectedTargetRefs");
        for (JsonNode target : row.path("selectedTargets")) {
          targetUses.add(((ObjectNode) target).remove("targetRef"));
        }
      }
      JsonNode site = row.path("site");
      ArrayNode compactSite = row.putArray("site");
      for (String field : List.of("startOffsetUtf16", "lengthUtf16", "startLine", "endLine")) {
        compactSite.add(site.path(field).deepCopy());
      }
      ObjectNode identity = new ObjectMapper().createObjectNode();
      identity.put("callerUnit", evidenceUnitRefs.get(callerRef));
      ObjectNode savedCall = ((ObjectNode) JSON.parseCanonical(call.canonicalCall())).deepCopy();
      savedCall.remove("callKey");
      identity.set("savedCall", savedCall);
      ArrayNode selectedIdentities = identity.putArray("selectedTargetUnits");
      for (UnitHandle target : call.selectedTargets()) {
        String targetRef = localRefsByUse.get(target);
        if (targetRef != null) {
          selectedIdentities.add(evidenceUnitRefs.get(targetRef));
        }
      }
      identity.set("row", row);
      ImmutableBytes key = JSON.encodeCanonical(identity);
      String rowRef = rowRefs.get(key);
      if (rowRef == null) {
        rowRef = "C" + (rowRefs.size() + 1);
        rowRefs.put(key, rowRef);
        row.put("ref", rowRef);
        rows.add(row);
      }
      use.put("rowRef", rowRef);
    }
    if (contextIndex != contexts.size()) {
      throw new IllegalStateException("ONTOLOGY_CALL_USE_MAPPING_INVALID");
    }
  }

  private static void collectCallEvidenceAtoms(
      Map<ImmutableBytes, JsonNode> atoms, JsonNode values) {
    for (JsonNode value : values) {
      ImmutableBytes canonical = JSON.encodeCanonical(value);
      atoms.putIfAbsent(canonical, value.deepCopy());
    }
  }

  private static Map<ImmutableBytes, String> writeCallEvidenceDictionary(
      ObjectNode dictionary, String prefix, Map<ImmutableBytes, JsonNode> atoms) {
    Map<ImmutableBytes, String> refs = new LinkedHashMap<>();
    int sequence = 1;
    for (Map.Entry<ImmutableBytes, JsonNode> atom : atoms.entrySet()) {
      String ref = prefix + sequence++;
      dictionary.set(ref, atom.getValue().deepCopy());
      refs.put(atom.getKey(), ref);
    }
    return Map.copyOf(refs);
  }

  private static void addCallEvidenceReferences(
      ArrayNode references, Map<ImmutableBytes, String> refs, JsonNode values) {
    for (JsonNode value : values) {
      String ref = refs.get(JSON.encodeCanonical(value));
      if (ref == null) {
        throw new IllegalStateException("ONTOLOGY_CALL_EVIDENCE_REFERENCE_INVALID");
      }
      references.add(ref);
    }
  }

  private Map<String, String> contextSourceRefs(String contextRef) {
    Map<String, String> result = new LinkedHashMap<>();
    for (FormalContextSource source : formalContextSources) {
      if (!contextRef.equals(source.contextRef())) {
        continue;
      }
      String previous = result.putIfAbsent(source.contextUnitRef(), source.sourceRef());
      if (previous != null && !previous.equals(source.sourceRef())) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_CONFLICT");
      }
    }
    return Map.copyOf(result);
  }

  /**
   * A selected call may defer detail only when this exact packet also exposes its caller context.
   */
  private boolean hasVisibleFormalCallContext(String callRef) {
    for (FormalCallSite call : formalCallSites) {
      if (callRef.equals(localRefsByUse.get(call.call()))
          && localRefsByUse.get(call.caller()) != null) {
        return true;
      }
    }
    return false;
  }

  private Map<String, String> contextRequestRefs(String contextRef) {
    Map<String, String> result = new LinkedHashMap<>();
    for (FormalContextRequest request : formalContextRequests) {
      if (!contextRef.equals(request.contextRef())) {
        continue;
      }
      String previous = result.putIfAbsent(request.association().requestId(), request.requestRef());
      if (previous != null && !previous.equals(request.requestRef())) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REQUEST_ASSOCIATION_CONFLICT");
      }
    }
    return Map.copyOf(result);
  }

  private void addContextRequestAssociations(ObjectNode context, String contextRef) {
    ArrayNode associations = context.putArray("requestAssociations");
    for (FormalContextRequest request : formalContextRequests) {
      if (!contextRef.equals(request.contextRef())) {
        continue;
      }
      ContextRequestAssociation association = request.association();
      ObjectNode projected =
          OntologyModelProjection.projectFormalFrontendRequestEnvelope(
              association.associationStatus(),
              association.resolution(),
              association.reason(),
              association.candidateEntryCount(),
              JSON.parseCanonical(association.requestCanonicalJson()));
      projected.put("requestRef", request.requestRef());
      associations.add(projected);
    }
  }

  private static Comparator<UnitHandle> unitHandleOrder() {
    return Comparator.comparing(UnitHandle::entryId)
        .thenComparing(handle -> handle.kind().name())
        .thenComparing(UnitHandle::originalId);
  }

  private static ObjectNode unitHandleDocument(UnitHandle handle) {
    ObjectNode item = new ObjectMapper().createObjectNode();
    item.put("entryId", handle.entryId());
    item.put("kind", handle.kind().name());
    item.put("originalId", handle.originalId());
    return item;
  }

  private static JsonNode selectedTarget(JsonNode call, UnitHandle selectedTarget) {
    for (JsonNode target : call.path("targets")) {
      if (selectedTarget.originalId().equals(target.path("methodKey").asText())) {
        return target;
      }
    }
    return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
  }

  private static boolean requiresObservationContext(String status) {
    return "CANDIDATES".equals(status)
        || "UNRESOLVED".equals(status)
        || "QUERY_FAILED".equals(status)
        || "NAVIGATION_CONFLICT".equals(status);
  }

  private static String prefix(UnitKind kind) {
    return switch (kind) {
      case JAVA_METHOD -> "J";
      case JAVA_CALL -> "C";
      case FRONTEND_UNIT -> "U";
      case FRONTEND_PAGE_CONTEXT -> "P";
      case FRONTEND_REQUEST_USE -> "R";
      case FRONTEND_CANDIDATE_REQUEST_USE -> "V";
      case PERSISTENCE_BINDING -> "B";
      case XML_STATEMENT -> "X";
      case XML_RESOURCE -> "S";
      case SQL_ANALYSIS -> "Q";
      case SOURCE_REFERENCE -> "E";
      case SCHEMA_SOURCE -> "D";
    };
  }

  static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        hex.append(Character.forDigit((value >>> 4) & 15, 16));
        hex.append(Character.forDigit(value & 15, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException("SHA-256 unavailable", missing);
    }
  }

  private record UnitIdentity(UnitKind kind, String originalId, ImmutableBytes canonicalJson) {}

  /**
   * Ordinary complete bodies remain deduplicated by their real Corpus U. A page-context record is
   * metadata whose request associations belong to one actual entry use, so only that kind carries
   * its entry scope while keeping the stored evidence U unchanged.
   */
  private record PacketUnitGroup(String evidenceUnitRef, String pageContextEntryId)
      implements Comparable<PacketUnitGroup> {
    private PacketUnitGroup {
      if (evidenceUnitRef == null || evidenceUnitRef.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_READING_PACKET_GROUP_INVALID");
      }
      if (pageContextEntryId != null && pageContextEntryId.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_READING_PACKET_GROUP_INVALID");
      }
    }

    @Override
    public int compareTo(PacketUnitGroup other) {
      int evidence = evidenceUnitRef.compareTo(other.evidenceUnitRef);
      if (evidence != 0) {
        return evidence;
      }
      if (pageContextEntryId == null) {
        return other.pageContextEntryId == null ? 0 : -1;
      }
      if (other.pageContextEntryId == null) {
        return 1;
      }
      return pageContextEntryId.compareTo(other.pageContextEntryId);
    }
  }

  private record ContextRequestIdentity(
      String requestId,
      String associationStatus,
      String resolution,
      String reason,
      int candidateEntryCount,
      ImmutableBytes requestCanonicalJson) {}

  private record FormalContextSource(
      UnitHandle context, String contextRef, String contextUnitRef, String sourceRef) {}

  private record FormalContextRequest(
      UnitHandle context,
      String contextRef,
      String requestRef,
      ContextRequestAssociation association) {}

  private record CallEvidenceReferences(
      Map<ImmutableBytes, String> targets, Map<ImmutableBytes, String> observations) {}

  private enum PacketFormat {
    LEGACY_V2,
    FORMAL_V3,
    FORMAL_V4,
    FORMAL_V5,
    FORMAL_V6,
    FORMAL_V7;

    private boolean isLinkBundle() {
      return this == FORMAL_V6 || this == FORMAL_V7;
    }

    private boolean hasSharedCalls() {
      return this == FORMAL_V5 || isLinkBundle();
    }

    private boolean isFormal() {
      return this != LEGACY_V2;
    }

    private String privateSchemaVersion() {
      return switch (this) {
        case FORMAL_V3 -> "ontology-reading-packet-v3";
        case FORMAL_V4 -> "ontology-reading-packet-v4";
        case FORMAL_V5 -> "ontology-reading-packet-v5";
        case FORMAL_V6 -> "ontology-reading-packet-v6";
        case FORMAL_V7 -> "ontology-reading-packet-v7";
        case LEGACY_V2 -> throw new IllegalStateException("ONTOLOGY_FORMAL_PACKET_REQUIRED");
      };
    }

    private String modelSchemaVersion() {
      return switch (this) {
        case LEGACY_V2 -> "ontology-model-reading-v2";
        case FORMAL_V3 -> "ontology-model-reading-v3";
        case FORMAL_V4 -> "ontology-model-reading-v4";
        case FORMAL_V5 -> "ontology-model-reading-v5";
        case FORMAL_V6 -> "ontology-model-reading-v6";
        case FORMAL_V7 -> "ontology-model-reading-v7";
      };
    }
  }

  public record PacketCost(
      int fullSourceBytes,
      int modelInputBytes,
      int privateInputBytes,
      int explicitCallDetailBytes) {
    public PacketCost {
      if (fullSourceBytes < 0
          || modelInputBytes < 0
          || privateInputBytes < 0
          || explicitCallDetailBytes < 0) {
        throw new IllegalArgumentException("ONTOLOGY_READING_COST_INVALID");
      }
    }
  }

  public record PackedUnit(
      String localRef,
      UnitKind kind,
      String originalId,
      List<String> entryUses,
      ImmutableBytes canonicalJson) {
    public PackedUnit {
      entryUses = List.copyOf(entryUses);
    }

    public JsonNode content() {
      return JSON.parseCanonical(canonicalJson);
    }
  }
}
