package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.UnitUse;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Literal evidence is navigable, but never silently promoted to a bound business field. */
final class OntologyCoherentLiteralCandidatesContractsTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void literalFrontendHitRequiresAnExactSavedPageContext() {
    String id = "entry:" + "c".repeat(64);
    ObjectNode root = mapper.createObjectNode();
    root.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode document = entry(id, "method:origin", "String origin() { return originKey; }");
    ((ObjectNode) document.path("java").path("methods").get(0))
        .putArray("exits")
        .addObject()
        .put("kind", "RETURN")
        .put("expression", "originKey");
    String body = "onSelect(row) { this.originKey = row.id; }";
    ObjectNode source =
        ((com.fasterxml.jackson.databind.node.ArrayNode) document.path("frontend").path("units"))
            .addObject();
    source.put("sourceUnitId", "source:callback");
    source.put("path", "pages/records.vue");
    source.put("sourceSha256", "d".repeat(64));
    source.put("sourceUnitKind", "METHOD");
    source.put("text", body);
    source.putObject("sourceUnitRange").put("offsetUtf16", 0).put("lengthUtf16", body.length());
    ObjectNode context =
        ((ObjectNode) document.path("frontend"))
            .putArray("pageContexts")
            .addObject()
            .put("contextId", "context:records")
            .put("pagePath", "pages/records.vue");
    ObjectNode use = context.putArray("sourceUnits").addObject();
    use.put("unitRef", "source:callback");
    use.put("sourcePath", source.path("path").asText());
    use.put("sourceSha256", source.path("sourceSha256").asText());
    use.put("sourceUnitKind", "METHOD");
    use.set("sourceUnitRange", source.path("sourceUnitRange").deepCopy());
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            new EntryEvidenceReader.Directory(
                json.encodeCanonical(root),
                ImmutableBytes.copyOf(new byte[0]),
                List.of(
                    new EntryEvidenceReader.EntryDocument(id, json.encodeCanonical(document)))));
    String clue = corpus.aliases().clueRef(ClueKind.METHOD, "method:origin");
    var task =
        new OntologyScopeReader.Task(
            "T1",
            OntologyScopeReader.TaskKind.LINK,
            OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE,
            List.of(),
            List.of(),
            List.of(clue));
    var question =
        new OntologyScopeReader.Question(
            "Q1",
            "Inspect saved connections",
            List.of(corpus.aliases().entryRef(id)),
            List.of(clue),
            List.of(task));

    var bundle = OntologyCoherentLinkBundle.prepare(corpus, question, task, 100_000, 500_000);

    assertThat(bundle.packet().units())
        .anySatisfy(
            unit -> {
              assertThat(unit.kind()).isEqualTo(UnitKind.FRONTEND_UNIT);
              assertThat(unit.content().path("text").asText()).isEqualTo(body);
            });
    assertThat(bundle.packet().units())
        .anySatisfy(unit -> assertThat(unit.kind()).isEqualTo(UnitKind.FRONTEND_PAGE_CONTEXT));
    assertThat(bundle.decision().path("unreadCandidates")).isEmpty();
  }

  @Test
  void crossEntryLiteralHitWithExactSavedPageContextIsIncludedAsLexicalCandidate() {
    CrossEntryFixture fixture = crossEntryFixture(true);

    OntologyCoherentLinkBundle.Result bundle = prepare(fixture);

    assertThat(bundle.issueCode()).isNull();
    assertThat(bundle.packet()).isNotNull();
    assertThat(hasUse(bundle.decision().path("derivedUses"), fixture.frontendUse())).isTrue();
    assertThat(hasUse(bundle.decision().path("derivedUses"), fixture.contextUse())).isTrue();
    assertThat(bundle.decision().path("derivedEntries"))
        .contains(mapper.getNodeFactory().textNode(fixture.frontendUse().entryRef()));
    assertThat(bundle.decision().path("unreadCandidates")).isEmpty();

    JsonNode sourceGroup = groupFor(bundle.decision().path("groups"), fixture.frontendUse());
    assertThat(sourceGroup.path("matchKind").asText()).isEqualTo("LEXICAL_MATCH");
    assertThat(sourceGroup.path("matchKind").asText()).isNotEqualTo("ANCHOR_SOURCE");
    JsonNode contextGroup = groupFor(bundle.decision().path("groups"), fixture.contextUse());
    assertThat(contextGroup.path("matchKind").asText()).isEqualTo("PAGE_CONTEXT");

    assertThat(bundle.packet().units())
        .anySatisfy(
            unit -> {
              assertThat(unit.kind()).isEqualTo(UnitKind.FRONTEND_UNIT);
              assertThat(unit.originalId()).isEqualTo(fixture.frontendId());
              assertThat(unit.content().path("text").asText()).isEqualTo(fixture.frontendBody());
            });
    assertThat(bundle.packet().units())
        .anySatisfy(
            unit -> {
              assertThat(unit.kind()).isEqualTo(UnitKind.FRONTEND_PAGE_CONTEXT);
              assertThat(unit.originalId()).isEqualTo(fixture.contextId());
            });
  }

  @Test
  void crossEntryLiteralHitWithoutExactSavedPageContextRemainsUnread() {
    CrossEntryFixture fixture = crossEntryFixture(false);

    OntologyCoherentLinkBundle.Result bundle = prepare(fixture);

    assertThat(bundle.issueCode()).isNull();
    assertThat(bundle.packet()).isNotNull();
    assertThat(hasUse(bundle.decision().path("derivedUses"), fixture.frontendUse())).isFalse();
    assertThat(bundle.decision().path("unreadCandidates"))
        .anySatisfy(
            candidate -> {
              assertThat(candidate.path("unitRef").asText())
                  .isEqualTo(fixture.frontendUse().unitRef());
              assertThat(candidate.path("entryRef").asText())
                  .isEqualTo(fixture.frontendUse().entryRef());
              assertThat(candidate.path("matchKind").asText()).isEqualTo("LEXICAL_MATCH");
              assertThat(candidate.path("queries"))
                  .contains(mapper.getNodeFactory().textNode(fixture.identifier()));
            });
    assertThat(bundle.packet().units())
        .noneSatisfy(unit -> assertThat(unit.originalId()).isEqualTo(fixture.frontendId()));
    assertThat(
            new String(
                bundle.packet().modelInput().copyToByteArray(),
                java.nio.charset.StandardCharsets.UTF_8))
        .doesNotContain(fixture.frontendBody());
  }

  @Test
  void bundleFollowsOnlyOneExactLocatedJavaTargetAndLeavesUnconfirmedTargetsAtBoundary() {
    String entryId = "entry:" + "f".repeat(64);
    String callerKey = "method:saveDetails";
    String getterKey = "method:getLinkApply";
    String helperKey = "method:helper";
    String conflictedKey = "method:getConflicted";
    String unresolvedKey = "method:getUnresolved";
    String nestedKey = "method:nestedHelper";
    String callerBody = "void saveDetails() { depotHead.getLinkApply(); }";
    String getterBody = "public String getLinkApply() { return linkApply; }";
    String helperBody = "String helper() { return helperValue; }";
    String conflictedBody = "String getConflicted() { return conflictValue; }";
    String unresolvedBody = "String getUnresolved() { return unresolvedValue; }";
    String nestedBody = "String nestedHelper() { return nestedValue; }";
    ObjectNode document = entry(entryId, callerKey, callerBody);
    addReturningMethod(document, getterKey, getterBody, "linkApply");
    addReturningMethod(document, helperKey, helperBody, "helperValue");
    addReturningMethod(document, conflictedKey, conflictedBody, "conflictValue");
    addReturningMethod(document, unresolvedKey, unresolvedBody, "unresolvedValue");
    addReturningMethod(document, nestedKey, nestedBody, "nestedValue");
    addCall(document, "call:located-getter", callerKey, "LOCATED", "getLinkApply()", getterKey);
    addCall(document, "call:located-helper", callerKey, "LOCATED", "helper()", helperKey);
    addCall(
        document,
        "call:conflicting-target",
        callerKey,
        "NAVIGATION_CONFLICT",
        "getConflicted()",
        conflictedKey);
    addCall(
        document,
        "call:unresolved-target",
        callerKey,
        "CANDIDATES",
        "getUnresolved()",
        unresolvedKey);
    addCall(document, "call:nested-target", getterKey, "LOCATED", "nestedHelper()", nestedKey);
    OntologyEvidenceCorpus corpus = corpus(document);
    String entryRef = corpus.aliases().entryRef(entryId);
    String anchorRef = corpus.aliases().clueRef(ClueKind.METHOD, callerKey);
    UnitUse seed = use(corpus, entryId, UnitKind.JAVA_METHOD, callerKey);
    UnitUse getter = use(corpus, entryId, UnitKind.JAVA_METHOD, getterKey);
    UnitUse helper = use(corpus, entryId, UnitKind.JAVA_METHOD, helperKey);
    UnitUse conflicted = use(corpus, entryId, UnitKind.JAVA_METHOD, conflictedKey);
    UnitUse unresolved = use(corpus, entryId, UnitKind.JAVA_METHOD, unresolvedKey);
    UnitUse nested = use(corpus, entryId, UnitKind.JAVA_METHOD, nestedKey);
    var task =
        new org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.Task(
            "T1",
            org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.TaskKind.LINK,
            org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.ReadingMode
                .TECHNICAL_BUNDLE,
            List.of(seed),
            List.of(),
            List.of(anchorRef));
    var question =
        new org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.Question(
            "Q1",
            "Read the saved caller and its exact target",
            List.of(entryRef),
            List.of(anchorRef),
            List.of(task));

    OntologyCoherentLinkBundle.Result bundle =
        OntologyCoherentLinkBundle.prepare(corpus, question, task, 100_000, 500_000);

    assertThat(bundle.issueCode()).isNull();
    assertThat(hasUse(bundle.decision().path("derivedUses"), getter)).isTrue();
    assertThat(hasUse(bundle.decision().path("derivedUses"), helper)).isFalse();
    assertThat(hasUse(bundle.decision().path("derivedUses"), conflicted)).isFalse();
    assertThat(hasUse(bundle.decision().path("derivedUses"), unresolved)).isFalse();
    assertThat(hasUse(bundle.decision().path("derivedUses"), nested)).isFalse();
    assertThat(bundle.packet().units())
        .anySatisfy(
            unit -> {
              assertThat(unit.kind()).isEqualTo(UnitKind.JAVA_METHOD);
              assertThat(unit.originalId()).isEqualTo(getterKey);
              assertThat(unit.content().path("source").path("text").asText()).isEqualTo(getterBody);
            });
    assertThat(bundle.packet().units())
        .noneSatisfy(
            unit ->
                assertThat(unit.originalId())
                    .isIn(helperKey, conflictedKey, unresolvedKey, nestedKey));
    JsonNode model = json.parseCanonical(bundle.packet().modelInput());
    assertThat(OntologyModelProjection.decodeCallRows(model))
        .anySatisfy(
            call -> {
              assertThat(call.path("resolution").asText()).isEqualTo("NAVIGATION_CONFLICT");
            });
  }

  @Test
  void incomingCallerParameterDoesNotAttachAnUnrelatedPageWithOnlyThatLiteral() {
    String anchorEntryId = "entry:" + "f".repeat(64);
    String frontendEntryId = "entry:" + "e".repeat(64);
    String getterKey = "method:getLinkApply";
    String callerKey = "method:saveDetails";
    String getterBody = "public String getLinkApply() { return linkApply; }";
    String callerBody =
        "void saveDetails(String rows) { dao.update(depotHead.getLinkApply(), rows); }";
    String frontendId = "source:unrelated-rows-page";
    String contextId = "context:unrelated-rows-page";
    String frontendPath = "pages/unrelated-rows.vue";
    String frontendBody = "function renderRows(rows) { return rows; }";

    ObjectNode anchor = entry(anchorEntryId, getterKey, getterBody);
    ((ObjectNode) anchor.path("java").path("methods").get(0))
        .putArray("exits")
        .addObject()
        .put("kind", "RETURN")
        .put("expression", "linkApply");
    ObjectNode caller = addMethod(anchor, callerKey, callerBody);
    caller.putArray("parameters").addObject().put("name", "rows");
    addCall(
        anchor,
        "call:incoming-getter",
        callerKey,
        "LOCATED",
        "depotHead.getLinkApply()",
        getterKey);

    ObjectNode frontendEntry =
        entry(frontendEntryId, "method:unrelated", "void inspectRows() { log('not read'); }");
    ObjectNode frontend = (ObjectNode) frontendEntry.path("frontend");
    ObjectNode source = frontend.withArray("units").addObject();
    source.put("sourceUnitId", frontendId);
    source.put("path", frontendPath);
    source.put("sourceSha256", "e".repeat(64));
    source.put("sourceUnitKind", "FUNCTION");
    source.put("text", frontendBody);
    source
        .putObject("sourceUnitRange")
        .put("offsetUtf16", 0)
        .put("lengthUtf16", frontendBody.length());
    ObjectNode context = frontend.withArray("pageContexts").addObject();
    context.put("contextId", contextId);
    context.put("pagePath", frontendPath);
    ObjectNode contextUnit = context.putArray("sourceUnits").addObject();
    contextUnit.put("unitRef", "private:unrelated-rows-page");
    contextUnit.put("sourcePath", frontendPath);
    contextUnit.put("sourceSha256", source.path("sourceSha256").asText());
    contextUnit.put("sourceUnitKind", source.path("sourceUnitKind").asText());
    contextUnit.set("sourceUnitRange", source.path("sourceUnitRange").deepCopy());

    OntologyEvidenceCorpus corpus = corpus(frontendEntry, anchor);
    String clue = corpus.aliases().clueRef(ClueKind.METHOD, getterKey);
    UnitUse getter = use(corpus, anchorEntryId, UnitKind.JAVA_METHOD, getterKey);
    UnitUse incomingCaller = use(corpus, anchorEntryId, UnitKind.JAVA_METHOD, callerKey);
    UnitUse unrelatedFrontend = use(corpus, frontendEntryId, UnitKind.FRONTEND_UNIT, frontendId);
    UnitUse unrelatedContext =
        use(corpus, frontendEntryId, UnitKind.FRONTEND_PAGE_CONTEXT, contextId);
    var task =
        new org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.Task(
            "T1",
            org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.TaskKind.LINK,
            org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.ReadingMode
                .TECHNICAL_BUNDLE,
            List.of(getter),
            List.of(),
            List.of(clue));
    var question =
        new org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.Question(
            "Q1",
            "Read the getter's incoming caller",
            List.of(corpus.aliases().entryRef(anchorEntryId)),
            List.of(clue),
            List.of(task));

    OntologyCoherentLinkBundle.Result bundle =
        OntologyCoherentLinkBundle.prepare(corpus, question, task, 100_000, 500_000);

    assertThat(bundle.issueCode()).isNull();
    assertThat(hasUse(bundle.decision().path("derivedUses"), incomingCaller)).isTrue();
    assertThat(bundle.packet().units())
        .anySatisfy(
            unit -> {
              assertThat(unit.kind()).isEqualTo(UnitKind.JAVA_METHOD);
              assertThat(unit.originalId()).isEqualTo(callerKey);
              assertThat(unit.content().path("source").path("text").asText()).isEqualTo(callerBody);
            });
    assertThat(hasUse(bundle.decision().path("derivedUses"), unrelatedFrontend)).isFalse();
    assertThat(hasUse(bundle.decision().path("derivedUses"), unrelatedContext)).isFalse();
    assertThat(bundle.packet().units())
        .noneSatisfy(unit -> assertThat(unit.originalId()).isIn(frontendId, contextId));
    assertThat(bundle.decision().path("unreadCandidates"))
        .anySatisfy(
            candidate -> {
              assertThat(candidate.path("unitRef").asText()).isEqualTo(unrelatedFrontend.unitRef());
              assertThat(candidate.path("entryRef").asText())
                  .isEqualTo(unrelatedFrontend.entryRef());
              assertThat(candidate.path("queries"))
                  .contains(mapper.getNodeFactory().textNode("rows"));
              assertThat(candidate.path("queries")).hasSize(1);
            });
  }

  @Test
  void savedReturnIdentifierDisclosesAllLiteralHitsWithoutReadingUnrelatedBodies() {
    String first = "entry:" + "a".repeat(64);
    String second = "entry:" + "b".repeat(64);
    ObjectNode root = mapper.createObjectNode();
    root.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode selected =
        entry(first, "method:origin", "public String origin() { return originKey; }");
    ((ObjectNode) selected.path("java").path("methods").get(0))
        .putArray("exits")
        .addObject()
        .put("kind", "RETURN")
        .put("expression", "originKey");
    ObjectNode unrelated = entry(second, "method:unrelated", "void inspect() { log(originKey); }");
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            new EntryEvidenceReader.Directory(
                json.encodeCanonical(root),
                ImmutableBytes.copyOf(new byte[0]),
                List.of(
                    new EntryEvidenceReader.EntryDocument(first, json.encodeCanonical(selected)),
                    new EntryEvidenceReader.EntryDocument(
                        second, json.encodeCanonical(unrelated)))));
    UnitHandle seed = new UnitHandle(first, UnitKind.JAVA_METHOD, "method:origin");
    UnitHandle other = new UnitHandle(second, UnitKind.JAVA_METHOD, "method:unrelated");
    String clue = corpus.aliases().clueRef(ClueKind.METHOD, "method:origin");
    var task =
        new OntologyScopeReader.Task(
            "T1",
            OntologyScopeReader.TaskKind.LINK,
            OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE,
            List.of(
                new OntologyScopeReader.UnitUse(
                    corpus.aliases().unitRef(seed), corpus.aliases().entryRef(first))),
            List.of(),
            List.of(clue));
    var question =
        new OntologyScopeReader.Question(
            "Q1",
            "Inspect the saved connection",
            List.of(corpus.aliases().entryRef(first)),
            List.of(clue),
            List.of(task));

    var bundle = OntologyCoherentLinkBundle.prepare(corpus, question, task, 100_000, 500_000);

    assertThat(bundle.packet().units()).hasSize(1);
    JsonNode candidates = bundle.decision().path("unreadCandidates");
    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).path("unitRef").asText())
        .isEqualTo(corpus.aliases().unitRef(other));
    assertThat(candidates.get(0).path("entryRef").asText())
        .isEqualTo(corpus.aliases().entryRef(second));
    assertThat(candidates.get(0).path("matchKind").asText()).isEqualTo("LEXICAL_MATCH");
    assertThat(candidates.get(0).path("queries"))
        .contains(mapper.getNodeFactory().textNode("originKey"));
    assertThat(
            OntologyModelProjection.decodeUnreadCandidates(
                json.parseCanonical(bundle.packet().modelInput()), candidates))
        .isEqualTo(candidates);
    assertThat(
            new String(
                bundle.packet().modelInput().copyToByteArray(),
                java.nio.charset.StandardCharsets.UTF_8))
        .doesNotContain("log(originKey)");
  }

  private ObjectNode entry(String id, String key, String body) {
    ObjectNode value = mapper.createObjectNode();
    value.put("entryId", id);
    value.putObject("sourceBasis").put("kind", "PREPARED_V1");
    value.put("assemblyStatus", "ASSEMBLED");
    value.putObject("entry").put("method", "GET").put("route", "/records");
    ObjectNode method = value.putObject("java").putArray("methods").addObject();
    method.put("methodKey", key);
    method.put("name", key.substring(key.indexOf(':') + 1));
    method.putObject("source").put("text", body);
    ((ObjectNode) value.path("java")).putArray("calls");
    value.putObject("frontend").putArray("units");
    value.putObject("persistence").putArray("statements");
    value.putArray("sourceRefs");
    return value;
  }

  private OntologyEvidenceCorpus corpus(ObjectNode... documents) {
    ObjectNode root = mapper.createObjectNode();
    root.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    List<EntryEvidenceReader.EntryDocument> entries =
        List.of(documents).stream()
            .map(
                document ->
                    new EntryEvidenceReader.EntryDocument(
                        document.path("entryId").asText(), json.encodeCanonical(document)))
            .toList();
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            json.encodeCanonical(root), ImmutableBytes.copyOf(new byte[0]), entries));
  }

  private CrossEntryFixture crossEntryFixture(boolean withPageContext) {
    String anchorEntryId = "entry:" + "a".repeat(64);
    String frontendEntryId = "entry:" + "b".repeat(64);
    String identifier = "originKey";
    String frontendId = "source:cross-entry-callback";
    String contextId = "context:cross-entry-records";
    String frontendBody = "onSelect(row) { this.originKey = row.id; }";
    ObjectNode anchor =
        entry(anchorEntryId, "method:origin", "String origin() { return originKey; }");
    ((ObjectNode) anchor.path("java").path("methods").get(0))
        .putArray("exits")
        .addObject()
        .put("kind", "RETURN")
        .put("expression", identifier);
    ObjectNode frontendEntry =
        entry(frontendEntryId, "method:unrelated", "void inspect() { log('unrelated'); }");
    ObjectNode frontend = (ObjectNode) frontendEntry.path("frontend");
    ObjectNode source = frontend.withArray("units").addObject();
    source.put("sourceUnitId", frontendId);
    source.put("path", "pages/records.vue");
    source.put("sourceSha256", "e".repeat(64));
    source.put("sourceUnitKind", "FUNCTION");
    source.put("text", frontendBody);
    ObjectNode range = source.putObject("sourceUnitRange");
    range.put("offsetUtf16", 0);
    range.put("lengthUtf16", frontendBody.length());
    UnitUse contextUse = null;
    if (withPageContext) {
      ObjectNode context = frontend.withArray("pageContexts").addObject();
      context.put("contextId", contextId);
      context.put("pagePath", "pages/records.vue");
      ObjectNode contextUnit = context.putArray("sourceUnits").addObject();
      contextUnit.put("unitRef", "private:cross-entry-callback");
      contextUnit.put("sourcePath", source.path("path").asText());
      contextUnit.put("sourceSha256", source.path("sourceSha256").asText());
      contextUnit.put("sourceUnitKind", source.path("sourceUnitKind").asText());
      contextUnit.set("sourceUnitRange", source.path("sourceUnitRange").deepCopy());
    }
    OntologyEvidenceCorpus corpus = corpus(anchor, frontendEntry);
    String clue = corpus.aliases().clueRef(ClueKind.METHOD, "method:origin");
    UnitUse seed = use(corpus, anchorEntryId, UnitKind.JAVA_METHOD, "method:origin");
    String anchorEntryRef = corpus.aliases().entryRef(anchorEntryId);
    String frontendEntryRef = corpus.aliases().entryRef(frontendEntryId);
    UnitUse frontendUse = use(corpus, frontendEntryId, UnitKind.FRONTEND_UNIT, frontendId);
    if (withPageContext) {
      contextUse = use(corpus, frontendEntryId, UnitKind.FRONTEND_PAGE_CONTEXT, contextId);
    }
    var task =
        new org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.Task(
            "T1",
            org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.TaskKind.LINK,
            org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.ReadingMode
                .TECHNICAL_BUNDLE,
            List.of(seed),
            List.of(),
            List.of(clue));
    var question =
        new org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.Question(
            "Q1",
            "Inspect the saved identifier",
            List.of(anchorEntryRef),
            List.of(clue),
            List.of(task));
    return new CrossEntryFixture(
        corpus,
        question,
        task,
        frontendUse,
        contextUse,
        frontendId,
        contextId,
        frontendBody,
        identifier);
  }

  private OntologyCoherentLinkBundle.Result prepare(CrossEntryFixture fixture) {
    return OntologyCoherentLinkBundle.prepare(
        fixture.corpus(), fixture.question(), fixture.task(), 100_000, 500_000);
  }

  private UnitUse use(
      OntologyEvidenceCorpus corpus, String entryId, UnitKind kind, String originalId) {
    UnitHandle handle = new UnitHandle(entryId, kind, originalId);
    return new UnitUse(corpus.aliases().unitRef(handle), corpus.aliases().entryRef(entryId));
  }

  private boolean hasUse(JsonNode uses, UnitUse expected) {
    for (JsonNode use : uses) {
      if (expected.unitRef().equals(use.path("unitRef").asText())
          && expected.entryRef().equals(use.path("entryRef").asText())) return true;
    }
    return false;
  }

  private JsonNode groupFor(JsonNode groups, UnitUse expected) {
    for (JsonNode group : groups) {
      if (hasUse(group.path("unitUses"), expected)) return group;
    }
    throw new AssertionError("No selected group for " + expected);
  }

  private ObjectNode addMethod(ObjectNode document, String key, String body) {
    ObjectNode method = ((ArrayNode) document.path("java").path("methods")).addObject();
    method.put("methodKey", key);
    method.put("name", key.substring(key.indexOf(':') + 1));
    method.putObject("source").put("text", body);
    return method;
  }

  private void addReturningMethod(ObjectNode document, String key, String body, String expression) {
    addMethod(document, key, body)
        .putArray("exits")
        .addObject()
        .put("kind", "RETURN")
        .put("expression", expression);
  }

  private void addCall(
      ObjectNode document,
      String key,
      String callerKey,
      String resolution,
      String expression,
      String targetKey) {
    ObjectNode call = ((ArrayNode) document.path("java").path("calls")).addObject();
    call.put("callKey", key);
    call.put("callerMethodKey", callerKey);
    call.put("kind", "METHOD");
    call.put("expression", expression);
    call.putNull("receiverExpression");
    call.putObject("site")
        .put("startLine", 1)
        .put("endLine", 1)
        .put("startOffsetUtf16", 0)
        .put("lengthUtf16", expression.length());
    call.putObject("navigationSite")
        .put("startLine", 1)
        .put("endLine", 1)
        .put("startOffsetUtf16", 0)
        .put("lengthUtf16", expression.length());
    call.putArray("actualArguments");
    call.putArray("enclosingControlIndexes");
    call.put("deferred", false);
    call.put("resolution", resolution);
    if (!"LOCATED".equals(resolution))
      call.put("resolutionDetail", resolution + " remains unresolved");
    ObjectNode target = call.putArray("targets").addObject();
    target.put("methodKey", targetKey);
    target.put("displayName", targetKey);
    target.putArray("roles").add("DECLARATION");
    target.putArray("navigationKinds").add("DEFINITION");
    target.put("expansion", "BODY_INCLUDED");
    target.putArray("argumentAssociations");
    call.putArray("observations");
  }

  private record CrossEntryFixture(
      OntologyEvidenceCorpus corpus,
      org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.Question question,
      org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.Task task,
      UnitUse frontendUse,
      UnitUse contextUse,
      String frontendId,
      String contextId,
      String frontendBody,
      String identifier) {}
}
