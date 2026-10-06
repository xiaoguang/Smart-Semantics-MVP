package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Mechanical saved-reference selection only; no Provider or business interpretation. */
public final class OntologyCoherentLinkBundle {
  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Comparator<UnitHandle> ORDER =
      Comparator.comparing(UnitHandle::entryId)
          .thenComparing(handle -> handle.kind().name())
          .thenComparing(UnitHandle::originalId);

  private OntologyCoherentLinkBundle() {}

  public static Result prepare(
      OntologyEvidenceCorpus corpus,
      OntologyScopeReader.Question question,
      OntologyScopeReader.Task task,
      int maxUnitBytes,
      int maxRequestBytes) {
    return prepare(
        corpus,
        question,
        task,
        maxUnitBytes,
        maxRequestBytes,
        OntologyScopeReader.SelectionMode.EXPLICIT);
  }

  public static Result prepare(
      OntologyEvidenceCorpus corpus,
      OntologyScopeReader.Question question,
      OntologyScopeReader.Task task,
      int maxUnitBytes,
      int maxRequestBytes,
      OntologyScopeReader.SelectionMode selectionMode) {
    if (selectionMode == null)
      throw new IllegalArgumentException("ONTOLOGY_LINK_BUNDLE_INPUT_INVALID");
    if (task.taskKind() != OntologyScopeReader.TaskKind.LINK
        || task.readingMode() != OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE
        || task.anchorRefs().size() != 1
        || !question.clueRefs().containsAll(task.anchorRefs())
        || maxUnitBytes < 1
        || maxRequestBytes < 1) {
      throw new IllegalArgumentException("ONTOLOGY_LINK_BUNDLE_INPUT_INVALID");
    }
    var aliases = corpus.aliases();
    String anchorRef = task.anchorRefs().get(0);
    var anchor = aliases.clue(anchorRef);
    Set<UnitHandle> seeds = uses(corpus, task.unitUses());
    if (seeds.isEmpty()) seeds.add(anchor.readableUnit());
    Set<UnitHandle> required = uses(corpus, task.requiredUnitUses());
    Set<UnitHandle> anchorUses = Set.copyOf(aliases.clueUses(anchorRef));
    Set<UnitHandle> selected = new LinkedHashSet<>(seeds);
    selected.addAll(anchorUses);
    selected.addAll(required);
    Set<UnitHandle> initial = Set.copyOf(selected);
    Set<UnitHandle> directJava = directJavaReferences(corpus, initial);
    selected.addAll(directJava);
    selected.addAll(persistenceGroup(corpus, Set.copyOf(selected)));
    Set<String> expansionTerms = new java.util.TreeSet<>(savedIdentifiers(corpus, initial));
    // Preserve the complete incoming caller, without turning its generic parameters into new
    // automatic page seeds. Actual returned identifiers in selected accessor declarations remain
    // useful lexical hints; neither kind of hint proves a field binding.
    Set<UnitHandle> accessors =
        directJava.stream()
            .filter(unit -> hasReturnedIdentifier(corpus, unit))
            .collect(java.util.stream.Collectors.toSet());
    expansionTerms.addAll(savedIdentifiers(corpus, accessors, false));
    Set<UnitHandle> literalSources =
        includeAssociatedFrontendLiteralHits(corpus, selected, expansionTerms);
    Set<UnitHandle> pageContexts = new LinkedHashSet<>();
    for (UnitHandle source : List.copyOf(selected)) {
      if (source.kind() == UnitKind.FRONTEND_PAGE_CONTEXT) {
        pageContexts.add(source);
        corpus
            .frontendContextSources(source)
            .forEach(dependency -> selected.add(dependency.unit()));
      }
      for (UnitHandle context : corpus.frontendPageContexts(source)) {
        pageContexts.add(context);
        selected.add(context);
        corpus
            .frontendContextSources(context)
            .forEach(dependency -> selected.add(dependency.unit()));
      }
    }

    ObjectNode decision = MAPPER.createObjectNode();
    decision.put("ruleVersion", "link-bundle-rule-v1");
    decision.put("anchorRef", anchorRef);
    decision.put("selectionOrigin", selectionMode.name());
    writeUses(decision.putArray("seedUses"), seeds, corpus);
    Set<UnitHandle> derived = new LinkedHashSet<>(selected);
    derived.removeAll(seeds);
    writeUses(decision.putArray("derivedUses"), derived, corpus);
    ArrayNode entries = decision.putArray("derivedEntries");
    selected.stream()
        .map(handle -> aliases.entryRef(handle.entryId()))
        .distinct()
        .sorted()
        .filter(ref -> !question.entryRefs().contains(ref))
        .forEach(entries::add);
    ArrayNode groups = decision.putArray("groups");
    ArrayNode unread = decision.putArray("requiredButUnread");
    ArrayNode candidates = decision.putArray("unreadCandidates");
    ObjectNode cost = decision.putObject("cost");
    addSavedXmlDependencies(corpus, selected, unread);
    discloseLiteralCandidates(corpus, Set.copyOf(selected), candidates);
    // Dependency sources are derived, never fabricated model read actions.
    derived = new LinkedHashSet<>(selected);
    derived.removeAll(seeds);
    decision.withArray("derivedUses").removeAll();
    writeUses(decision.withArray("derivedUses"), derived, corpus);

    Map<String, List<UnitHandle>> grouped = new TreeMap<>();
    for (UnitHandle handle : selected.stream().sorted(ORDER).toList()) {
      String key =
          aliases.unitRef(handle)
              + (handle.kind() == UnitKind.FRONTEND_PAGE_CONTEXT
                  ? ":" + aliases.entryRef(handle.entryId())
                  : "");
      grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(handle);
    }
    Set<UnitHandle> prefix = new LinkedHashSet<>();
    int previousProjectionBytes = 0;
    boolean unitBlocked = false;
    for (List<UnitHandle> handles : grouped.values()) {
      UnitHandle first = handles.get(0);
      ObjectNode group = groups.addObject();
      group.put("groupRef", "G" + groups.size());
      group.put(
          "matchKind",
          pageContexts.contains(first)
              ? "PAGE_CONTEXT"
              : anchorUses.contains(first)
                  ? "ANCHOR_SOURCE"
                  : literalSources.contains(first) ? "LEXICAL_MATCH" : "STRUCTURED_REFERENCE");
      group.putArray("technicalRefs").add(anchorRef);
      writeUses(group.putArray("unitUses"), Set.copyOf(handles), corpus);
      if (first.kind() == UnitKind.FRONTEND_PAGE_CONTEXT)
        group.put("pageInstanceRef", first.originalId());
      else group.putNull("pageInstanceRef");
      int bodyBytes =
          OntologyReadingPacket.fullBodyBytes(
              first.kind(),
              corpus.read(first.entryId(), first.kind(), first.originalId()).content());
      group.put("unitBytes", bodyBytes);
      unitBlocked |= bodyBytes > maxUnitBytes;
      group.put("outcome", "INCLUDED");
      group.putNull("issueCode");
      // The provisional decision used to measure this prefix must already have a legal shape.
      group.put("projectedIncrementBytes", 0);
      prefix.addAll(handles);
      // Context dependencies are inseparable when measuring a complete group.
      for (UnitHandle handle : handles)
        if (handle.kind() == UnitKind.FRONTEND_PAGE_CONTEXT)
          corpus.frontendContextSources(handle).forEach(source -> prefix.add(source.unit()));
      int projectionBytes =
          OntologyReadingPacket.restoreFormalV6(
                  corpus, prefix.stream().sorted(ORDER).toList(), Integer.MAX_VALUE, decision)
              .withVisibleClues(corpus, task.anchorRefs())
              .modelInput()
              .size();
      group.put("projectedIncrementBytes", projectionBytes - previousProjectionBytes);
      previousProjectionBytes = projectionBytes;
    }
    for (JsonNode missing : unread) {
      ObjectNode group = groups.addObject();
      group.put("groupRef", "G" + groups.size());
      group.put("matchKind", "STRUCTURED_REFERENCE");
      group.putArray("technicalRefs").add(anchorRef);
      group.putArray("unitUses");
      group.putNull("pageInstanceRef");
      group.put("outcome", "UNAVAILABLE");
      group.put("issueCode", missing.path("issueCode").asText());
      group.putNull("unitBytes");
      group.put("projectedIncrementBytes", 0);
    }
    OntologyReadingPacket candidate =
        OntologyReadingPacket.restoreFormalV6(
                corpus, selected.stream().sorted(ORDER).toList(), Integer.MAX_VALUE, decision)
            .withVisibleClues(corpus, task.anchorRefs());
    cost.put("sourceBytes", candidate.cost().fullSourceBytes());
    cost.put("projectionBytes", candidate.modelInput().size());
    if (unitBlocked || candidate.modelInput().size() > maxRequestBytes) {
      for (JsonNode group : groups) {
        if (!"INCLUDED".equals(group.path("outcome").asText())) continue;
        ((ObjectNode) group).put("outcome", "CAPACITY_BLOCKED");
        ((ObjectNode) group).put("issueCode", "LINK_BUNDLE_TOO_LARGE");
      }
      writeUses(unread, required, corpus);
      return new Result(null, JSON.encodeCanonical(decision), "LINK_BUNDLE_TOO_LARGE");
    }
    OntologyReadingPacket frozen =
        OntologyReadingPacket.restoreFormalV6(
                corpus, selected.stream().sorted(ORDER).toList(), maxUnitBytes, decision)
            .withVisibleClues(corpus, task.anchorRefs());
    return new Result(frozen, JSON.encodeCanonical(decision), null);
  }

  private static Set<UnitHandle> uses(
      OntologyEvidenceCorpus corpus, List<OntologyScopeReader.UnitUse> uses) {
    Set<UnitHandle> result = new LinkedHashSet<>();
    for (var use : uses) {
      var canonical = corpus.aliases().unit(use.unitRef());
      var handle =
          new UnitHandle(
              corpus.aliases().entry(use.entryRef()).entryId(),
              canonical.kind(),
              canonical.originalId());
      corpus.aliases().read(use.unitRef(), use.entryRef());
      result.add(handle);
    }
    return result;
  }

  /** Uses saved resource identities only; no XML parsing or dependency-name inference. */
  private static void addSavedXmlDependencies(
      OntologyEvidenceCorpus corpus, Set<UnitHandle> selected, ArrayNode unread) {
    Map<ImmutableBytes, ObjectNode> missing =
        new TreeMap<>(
            (left, right) ->
                java.util.Arrays.compareUnsigned(left.copyToByteArray(), right.copyToByteArray()));
    for (UnitHandle statement : selected.stream().sorted(ORDER).toList()) {
      if (statement.kind() != UnitKind.XML_STATEMENT) continue;
      JsonNode content =
          corpus.read(statement.entryId(), statement.kind(), statement.originalId()).content();
      if (content.path("dependencyRefs").isEmpty()) continue;
      String resourcePath = content.path("resourceRef").asText();
      List<UnitHandle> resources =
          corpus.entryUnits(statement.entryId(), 0, Integer.MAX_VALUE).items().stream()
              .filter(unit -> unit.kind() == UnitKind.XML_RESOURCE)
              .toList();
      UnitHandle matched = null;
      for (UnitHandle resource : resources) {
        JsonNode resourceContent =
            corpus.read(resource.entryId(), resource.kind(), resource.originalId()).content();
        if (resourcePath.equals(resourceContent.path("resourcePath").asText())) {
          if (matched != null)
            throw new IllegalArgumentException("ONTOLOGY_XML_DEPENDENCY_SOURCE_CONFLICT");
          matched = resource;
        }
      }
      if (matched != null) {
        selected.add(matched);
        JsonNode matchedContent =
            corpus.read(matched.entryId(), matched.kind(), matched.originalId()).content();
        // Follow only this saved resource's direct dependencies, not dependencies of new resources.
        for (JsonNode dependencyPath : matchedContent.path("dependencyResourcePaths")) {
          String path = dependencyPath.asText();
          List<UnitHandle> dependencies =
              resources.stream()
                  .filter(
                      resource ->
                          path.equals(
                              corpus
                                  .read(resource.entryId(), resource.kind(), resource.originalId())
                                  .content()
                                  .path("resourcePath")
                                  .asText()))
                  .toList();
          if (dependencies.size() > 1)
            throw new IllegalArgumentException("ONTOLOGY_XML_DEPENDENCY_SOURCE_CONFLICT");
          if (dependencies.size() == 1) selected.add(dependencies.get(0));
          else {
            ObjectNode issue = MAPPER.createObjectNode();
            issue.put("entryRef", corpus.aliases().entryRef(statement.entryId()));
            issue.put("resourcePath", path);
            issue.put("requiredByResourcePath", resourcePath);
            issue.put("issueCode", "XML_DEPENDENCY_RESOURCE_UNAVAILABLE");
            missing.putIfAbsent(JSON.encodeCanonical(issue), issue);
          }
        }
      } else {
        ObjectNode issue = MAPPER.createObjectNode();
        issue.put("entryRef", corpus.aliases().entryRef(statement.entryId()));
        issue.put("resourcePath", resourcePath);
        issue.set("dependencyRefs", content.path("dependencyRefs").deepCopy());
        issue.put("issueCode", "XML_DEPENDENCY_RESOURCE_UNAVAILABLE");
        missing.putIfAbsent(JSON.encodeCanonical(issue), issue);
      }
    }
    missing.values().forEach(unread::add);
  }

  /** Exact saved identifiers only; same spelling does not establish a field or object binding. */
  private static Set<UnitHandle> includeAssociatedFrontendLiteralHits(
      OntologyEvidenceCorpus corpus, Set<UnitHandle> selected, Set<String> terms) {
    Set<UnitHandle> literalSources = new LinkedHashSet<>();
    // The source must have its own exact saved physical page context. It need not belong to
    // an anchor entry: UI establishment and backend consumption can be different requests.
    // This remains lexical candidate evidence, not a field binding or a confirmed relation.
    for (var result : corpus.searchLiteralBatch(terms, 0, Integer.MAX_VALUE).values()) {
      for (var match : result.matches()) {
        UnitHandle handle = new UnitHandle(match.entryId(), match.kind(), match.originalId());
        if (handle.kind() == UnitKind.FRONTEND_UNIT
            && !corpus.frontendPageContexts(handle).isEmpty()
            && selected.add(handle)) literalSources.add(handle);
      }
    }
    return Set.copyOf(literalSources);
  }

  /** One saved edge only. Conflicting candidates cannot become a located target by selection. */
  private static Set<UnitHandle> directJavaReferences(
      OntologyEvidenceCorpus corpus, Set<UnitHandle> initial) {
    Set<UnitHandle> result = new LinkedHashSet<>();
    for (String entry : initial.stream().map(UnitHandle::entryId).distinct().sorted().toList()) {
      List<UnitHandle> available = corpus.entryUnits(entry, 0, Integer.MAX_VALUE).items();
      Map<String, UnitHandle> methods = new TreeMap<>();
      available.stream()
          .filter(unit -> unit.kind() == UnitKind.JAVA_METHOD)
          .forEach(unit -> methods.put(unit.originalId(), unit));
      for (UnitHandle callHandle : available) {
        if (callHandle.kind() != UnitKind.JAVA_CALL) continue;
        JsonNode call = corpus.read(entry, callHandle.kind(), callHandle.originalId()).content();
        if (!"LOCATED".equals(call.path("resolution").asText())) continue;
        UnitHandle caller = methods.get(call.path("callerMethodKey").asText());
        if (caller == null) continue;
        for (JsonNode target : call.path("targets")) {
          UnitHandle method = methods.get(target.path("methodKey").asText());
          if (method == null) continue;
          // Incoming references restore the complete caller, but its other helpers are not
          // followed.
          if (initial.contains(method)) result.add(caller);
          // Outgoing reads are limited to actual saved identifier-returning declarations.
          if (initial.contains(caller) && hasReturnedIdentifier(corpus, method)) result.add(method);
        }
      }
    }
    return result;
  }

  private static boolean hasReturnedIdentifier(OntologyEvidenceCorpus corpus, UnitHandle method) {
    JsonNode content = corpus.read(method.entryId(), method.kind(), method.originalId()).content();
    if (!content.path("name").asText().matches("(?:get|is)[\\p{Lu}].*")) return false;
    for (JsonNode exit : content.path("exits")) {
      if ("RETURN".equals(exit.path("kind").asText())
          && exit.path("expression")
              .asText()
              .matches("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*")) return true;
    }
    return false;
  }

  private static void discloseLiteralCandidates(
      OntologyEvidenceCorpus corpus, Set<UnitHandle> selected, ArrayNode candidates) {
    Set<String> terms = savedIdentifiers(corpus, selected);
    Map<UnitHandle, Set<String>> hits = new TreeMap<>(ORDER);
    for (var result : corpus.searchLiteralBatch(terms, 0, Integer.MAX_VALUE).entrySet()) {
      for (var match : result.getValue().matches()) {
        UnitHandle handle = new UnitHandle(match.entryId(), match.kind(), match.originalId());
        if (!selected.contains(handle)) {
          hits.computeIfAbsent(handle, ignored -> new java.util.TreeSet<>()).add(result.getKey());
        }
      }
    }
    for (var hit : hits.entrySet()) {
      ObjectNode candidate = candidates.addObject();
      candidate.put("unitRef", corpus.aliases().unitRef(hit.getKey()));
      candidate.put("entryRef", corpus.aliases().entryRef(hit.getKey().entryId()));
      candidate.put("kind", hit.getKey().kind().name());
      candidate.put("matchKind", "LEXICAL_MATCH");
      candidate.set("queries", MAPPER.valueToTree(hit.getValue()));
      candidate.put(
          "reason", "Same literal only; body unread and no field or business binding confirmed.");
    }
  }

  private static Set<String> savedIdentifiers(
      OntologyEvidenceCorpus corpus, Set<UnitHandle> selected) {
    return savedIdentifiers(corpus, selected, true);
  }

  private static Set<String> savedIdentifiers(
      OntologyEvidenceCorpus corpus, Set<UnitHandle> selected, boolean includeParameters) {
    Set<String> terms = new java.util.TreeSet<>();
    for (UnitHandle handle : selected.stream().sorted(ORDER).toList()) {
      if (handle.kind() != UnitKind.JAVA_METHOD) continue;
      JsonNode content =
          corpus.read(handle.entryId(), handle.kind(), handle.originalId()).content();
      for (JsonNode exit : content.path("exits")) {
        String expression = exit.path("expression").asText();
        if ("RETURN".equals(exit.path("kind").asText())
            && expression.matches("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*")) {
          terms.add(expression);
        }
      }
      if (includeParameters) {
        for (JsonNode parameter : content.path("parameters")) {
          String name = parameter.path("name").asText();
          if (name.matches("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"))
            terms.add(name);
        }
      }
    }
    return terms;
  }

  /** Join only saved entry-local Mapper identities; no method-name or SQL-text inference. */
  private static Set<UnitHandle> persistenceGroup(
      OntologyEvidenceCorpus corpus, Set<UnitHandle> seeds) {
    Set<UnitHandle> result = new LinkedHashSet<>();
    for (String entry : seeds.stream().map(UnitHandle::entryId).distinct().sorted().toList()) {
      List<UnitHandle> available = corpus.entryUnits(entry, 0, Integer.MAX_VALUE).items();
      Set<String> methodKeys = new LinkedHashSet<>();
      Set<ImmutableBytes> variants = new LinkedHashSet<>();
      for (UnitHandle seed : seeds) {
        if (!entry.equals(seed.entryId())) continue;
        if (seed.kind() == UnitKind.JAVA_METHOD || seed.kind() == UnitKind.PERSISTENCE_BINDING) {
          methodKeys.add(seed.originalId());
        } else if (seed.kind() == UnitKind.XML_STATEMENT || seed.kind() == UnitKind.SQL_ANALYSIS) {
          variants.add(
              statementIdentity(corpus.read(entry, seed.kind(), seed.originalId()).content()));
        }
      }
      Set<String> anchorMethods = Set.copyOf(methodKeys);
      Set<ImmutableBytes> anchorVariants = Set.copyOf(variants);
      for (UnitHandle handle : available) {
        if (handle.kind() != UnitKind.PERSISTENCE_BINDING) continue;
        JsonNode binding = corpus.read(entry, handle.kind(), handle.originalId()).content();
        boolean matching = anchorMethods.contains(binding.path("methodKey").asText());
        for (JsonNode ref : binding.path("statementRefs")) {
          matching |= anchorVariants.contains(statementIdentity(ref));
        }
        if (!matching) continue;
        result.add(handle);
        methodKeys.add(binding.path("methodKey").asText());
        for (JsonNode ref : binding.path("statementRefs")) variants.add(statementIdentity(ref));
      }
      for (UnitHandle handle : available) {
        if (handle.kind() == UnitKind.JAVA_METHOD && methodKeys.contains(handle.originalId())) {
          result.add(handle);
        } else if (handle.kind() == UnitKind.XML_STATEMENT
            || handle.kind() == UnitKind.SQL_ANALYSIS) {
          JsonNode content = corpus.read(entry, handle.kind(), handle.originalId()).content();
          if (variants.contains(statementIdentity(content))) result.add(handle);
        }
      }
    }
    return result;
  }

  private static ImmutableBytes statementIdentity(JsonNode content) {
    ObjectNode identity = MAPPER.createObjectNode();
    identity.put("statementRef", content.path("statementRef").asText());
    if (content.path("databaseId").isNull() || content.path("databaseId").isMissingNode()) {
      identity.putNull("databaseId");
    } else {
      identity.set("databaseId", content.path("databaseId").deepCopy());
    }
    return JSON.encodeCanonical(identity);
  }

  private static void writeUses(
      ArrayNode target, Set<UnitHandle> uses, OntologyEvidenceCorpus corpus) {
    for (UnitHandle handle : uses.stream().sorted(ORDER).toList()) {
      ObjectNode use = target.addObject();
      use.put("unitRef", corpus.aliases().unitRef(handle));
      use.put("entryRef", corpus.aliases().entryRef(handle.entryId()));
    }
  }

  public record Result(
      OntologyReadingPacket packet, ImmutableBytes decisionDocument, String issueCode) {
    public JsonNode decision() {
      return JSON.parseCanonical(decisionDocument);
    }
  }
}
