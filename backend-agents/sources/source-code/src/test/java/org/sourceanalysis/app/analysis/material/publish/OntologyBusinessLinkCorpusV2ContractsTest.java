package org.sourceanalysis.app.analysis.material.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet.Entry;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet.Java;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.NavigationClue;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.analysis.ontology.OntologyReadingPacket;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** Direct contracts for opt-in control clues and reverse lookup of saved R4 page contexts. */
final class OntologyBusinessLinkCorpusV2ContractsTest {
  private static final int MAX_SOURCE_BYTES = 8 * 1024 * 1024;
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void businessLinkNavigationBuildsKFromSavedControlsWithoutChangingTheLegacyCorpus()
      throws Exception {
    try (FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture fixture =
        FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture.complete()) {
      EntryEvidenceSet controlledSet = withSavedControlBranches(fixture.v2Set());
      AnalysisStepPublicationReference r4 = fixture.publishR4V2(controlledSet);
      EntryEvidenceReader reader = new EntryEvidenceReader(fixture.modules(), fixture.steps());
      OntologyEvidenceCorpus legacy = OntologyEvidenceCorpus.open(reader, r4);
      UnitHandle method =
          new UnitHandle(fixture.entryId(), UnitKind.JAVA_METHOD, "method:canvasSave");

      JsonNode savedMethod =
          legacy.read(method.entryId(), method.kind(), method.originalId()).content();
      assertThat(expressions(savedMethod.path("controls")))
          .containsExactly("externalRecordToken != null", "renamedEntityReference != null");
      assertThat(legacy.entryClues(fixture.entryId(), 100).clues())
          .noneMatch(clue -> "CONTROL_REFERENCE".equals(clue.kind().name()));
      var legacyAliases = legacy.aliases().canonicalMapping();

      OntologyEvidenceCorpus businessLinkCorpus = legacy.withBusinessLinkNavigation();
      assertThat(businessLinkCorpus).isNotSameAs(legacy);
      assertThat(legacy.aliases().canonicalMapping()).isEqualTo(legacyAliases);

      ClueKind controlKind = ClueKind.valueOf("CONTROL_REFERENCE");
      List<NavigationClue> controlClues =
          businessLinkCorpus.entryClues(fixture.entryId(), 100).clues().stream()
              .filter(clue -> clue.kind() == controlKind)
              .toList();
      assertThat(controlClues).hasSize(2);
      assertThat(controlClues)
          .anySatisfy(
              clue -> assertThat(clue.keyDisplay()).contains("externalRecordToken != null"));
      assertThat(controlClues)
          .anySatisfy(
              clue -> assertThat(clue.keyDisplay()).contains("renamedEntityReference != null"));

      for (NavigationClue clue : controlClues) {
        assertThat(clue.readableUnit()).isEqualTo(method);
        String clueRef = businessLinkCorpus.aliases().clueRef(controlKind, clue.lookupKey());
        assertThat(clueRef).matches("K[1-9][0-9]*");
        assertThat(businessLinkCorpus.aliases().clue(clueRef)).isEqualTo(clue);
        assertThat(businessLinkCorpus.aliases().clueUses(clueRef)).containsExactly(method);
      }
      assertThat(controlClues)
          .extracting(NavigationClue::lookupKey)
          .containsExactlyInAnyOrder(
              "externalRecordToken != null", "renamedEntityReference != null");
    }
  }

  @Test
  void formalV5SelectingSharedSourceExpandsOnlyItsExactPageContextsAndCompleteSources()
      throws Exception {
    try (FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture fixture =
        FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture.complete()) {
      AnalysisStepPublicationReference r4 =
          fixture.publishR4V2(fixture.v2SetWithSharedPageBodyInstances());
      EntryEvidenceReader reader = new EntryEvidenceReader(fixture.modules(), fixture.steps());
      OntologyEvidenceCorpus corpus =
          OntologyEvidenceCorpus.open(reader, r4).withBusinessLinkNavigation();
      UnitHandle selectedSharedSource =
          new UnitHandle(fixture.entryId(), UnitKind.FRONTEND_UNIT, "unit:choice-panel");

      OntologyReadingPacket packet =
          OntologyReadingPacket.formalV5(corpus, List.of(selectedSharedSource), MAX_SOURCE_BYTES);
      List<OntologyReadingPacket.PackedUnit> contexts =
          packet.units().stream()
              .filter(unit -> unit.kind() == UnitKind.FRONTEND_PAGE_CONTEXT)
              .toList();
      assertThat(contexts)
          .extracting(OntologyReadingPacket.PackedUnit::originalId)
          .containsExactlyInAnyOrder("context:canvas", "context:canvas-copy", "context:other");

      List<OntologyReadingPacket.PackedUnit> sourceUnits =
          packet.units().stream().filter(unit -> unit.kind() == UnitKind.FRONTEND_UNIT).toList();
      assertThat(sourceUnits)
          .extracting(OntologyReadingPacket.PackedUnit::originalId)
          .containsExactlyInAnyOrder(
              "unit:canvas-page",
              "unit:choice-panel",
              "unit:canvas-mixin",
              "unit:action-client",
              "unit:transport",
              "unit:other-page");
      for (OntologyReadingPacket.PackedUnit sourceUnit : sourceUnits) {
        assertThat(sourceUnit.content())
            .isEqualTo(
                corpus
                    .read(fixture.entryId(), UnitKind.FRONTEND_UNIT, sourceUnit.originalId())
                    .content());
      }

      JsonNode model = JSON.readTree(packet.modelInput().copyToByteArray());
      Map<String, JsonNode> modelUnits = modelUnitsByRef(model);
      Map<String, JsonNode> modelContexts = modelContextsByInstance(model);
      assertThat(modelContexts.keySet())
          .containsExactlyInAnyOrder("page:canvas", "page:canvas-copy", "page:other");
      assertThat(modelContexts.get("page:canvas").path("ref").asText())
          .isNotEqualTo(modelContexts.get("page:canvas-copy").path("ref").asText());

      assertContextSourcesResolveToCompleteBodies(
          packet, corpus, fixture.entryId(), modelUnits, modelContexts.get("page:canvas"), 5);
      assertContextSourcesResolveToCompleteBodies(
          packet, corpus, fixture.entryId(), modelUnits, modelContexts.get("page:canvas-copy"), 5);
      assertContextSourcesResolveToCompleteBodies(
          packet, corpus, fixture.entryId(), modelUnits, modelContexts.get("page:other"), 2);

      JsonNode canvas = modelContexts.get("page:canvas").path("content");
      JsonNode canvasCopy = modelContexts.get("page:canvas-copy").path("content");
      JsonNode other = modelContexts.get("page:other").path("content");
      assertThat(conditionExpressions(canvas))
          .containsExactlyInAnyOrder("this.model.id|FALSE", "this.model.id|TRUE");
      assertThat(conditionExpressions(canvasCopy)).containsExactly("this.model.id|FALSE");
      assertThat(conditionExpressions(other)).isEmpty();
      assertThat(requestMethods(modelContexts.get("page:canvas")))
          .containsExactlyInAnyOrder("POST", "PUT");
      assertThat(requestMethods(modelContexts.get("page:canvas-copy"))).containsExactly("POST");
      assertThat(requestMethods(modelContexts.get("page:other"))).containsExactly("GET");

      JsonNode canvasEmit = observationByKind(canvas, "COMPONENT_EMIT");
      JsonNode otherEmit = observationByKind(other, "COMPONENT_EMIT");
      assertThat(texts(canvasEmit.path("actualArguments")))
          .containsExactly("rows", "selectedId", "'panel-source'");
      assertThat(texts(otherEmit.path("actualArguments")))
          .containsExactly("rows", "otherId", "'other-source'");
      JsonNode canvasCallback = observationByKind(canvas, "EVENT_CALLBACK_BINDING");
      JsonNode copiedCallback = observationByKind(canvasCopy, "EVENT_CALLBACK_BINDING");
      assertThat(texts(canvasCallback.path("formalParameters"))).containsExactly("first", "second");
      assertThat(texts(canvasCallback.path("actualArguments"))).isEmpty();
      assertNotPassedBindings(canvasCallback);
      assertNotPassedBindings(copiedCallback);

      UnitHandle otherPageSource =
          new UnitHandle(fixture.entryId(), UnitKind.FRONTEND_UNIT, "unit:other-page");
      OntologyReadingPacket otherPagePacket =
          OntologyReadingPacket.formalV5(corpus, List.of(otherPageSource), MAX_SOURCE_BYTES);
      Map<String, JsonNode> otherPageContexts =
          modelContextsByInstance(JSON.readTree(otherPagePacket.modelInput().copyToByteArray()));
      assertThat(otherPageContexts.keySet())
          .containsExactlyInAnyOrder("page:other", "page:unrequested");
      JsonNode unmatchedAssociation =
          otherPageContexts.get("page:other").path("content").path("requestAssociations").get(0);
      assertThat(unmatchedAssociation.path("associationStatus").asText())
          .isEqualTo("COVERAGE_ONLY");
      assertThat(unmatchedAssociation.path("resolution").asText()).isEqualTo("NO_MATCH");
      assertThat(unmatchedAssociation.path("request").path("httpMethod").asText()).isEqualTo("GET");
      assertThat(
              otherPageContexts
                  .get("page:unrequested")
                  .path("content")
                  .path("requestAssociations")
                  .size())
          .isZero();
    }
  }

  @Test
  void reverseSourceLookupReturnsOnlyExactEntryContextsAndKeepsTheirSavedValuesSeparate()
      throws Exception {
    try (FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture fixture =
        FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture.complete()) {
      AnalysisStepPublicationReference r4 =
          fixture.publishR4V2(fixture.v2SetWithSharedPageBodyInstances());
      EntryEvidenceReader reader = new EntryEvidenceReader(fixture.modules(), fixture.steps());
      OntologyEvidenceCorpus corpus =
          OntologyEvidenceCorpus.open(reader, r4).withBusinessLinkNavigation();

      UnitHandle sharedComponent =
          new UnitHandle(fixture.entryId(), UnitKind.FRONTEND_UNIT, "unit:choice-panel");
      assertThat(corpus.frontendPageContexts(sharedComponent))
          .containsExactlyInAnyOrder(
              context(fixture.entryId(), "context:canvas"),
              context(fixture.entryId(), "context:canvas-copy"),
              context(fixture.entryId(), "context:other"));

      UnitHandle otherPage =
          new UnitHandle(fixture.entryId(), UnitKind.FRONTEND_UNIT, "unit:other-page");
      assertThat(corpus.frontendPageContexts(otherPage))
          .containsExactlyInAnyOrder(
              context(fixture.entryId(), "context:other"),
              context(fixture.entryId(), "context:unrequested"));
      assertThat(
              corpus.frontendPageContexts(
                  new UnitHandle(fixture.entryId(), UnitKind.FRONTEND_UNIT, "unit:missing")))
          .isEmpty();
      assertThat(
              corpus.frontendPageContexts(
                  new UnitHandle(
                      "entry:" + "9".repeat(64), UnitKind.FRONTEND_UNIT, "unit:choice-panel")))
          .isEmpty();

      JsonNode canvas = readContext(corpus, fixture.entryId(), "context:canvas");
      JsonNode canvasCopy = readContext(corpus, fixture.entryId(), "context:canvas-copy");
      JsonNode other = readContext(corpus, fixture.entryId(), "context:other");
      JsonNode unrequested = readContext(corpus, fixture.entryId(), "context:unrequested");

      assertThat(texts(canvas.path("requestIds")))
          .containsExactly("request:canvas-post", "request:canvas-put");
      assertThat(conditionByRequest(canvas))
          .containsEntry("request:canvas-post", "this.model.id|FALSE")
          .containsEntry("request:canvas-put", "this.model.id|TRUE");
      assertThat(texts(canvasCopy.path("requestIds"))).containsExactly("request:canvas-post");
      assertThat(conditionByRequest(canvasCopy))
          .containsOnlyKeys("request:canvas-post")
          .containsEntry("request:canvas-post", "this.model.id|FALSE");
      assertThat(texts(other.path("requestIds"))).containsExactly("request:other-unmatched");
      assertThat(other.path("requestConditions").size()).isZero();
      assertThat(texts(unrequested.path("requestIds"))).isEmpty();
      assertThat(unrequested.path("requestConditions").size()).isZero();

      JsonNode canvasEmit = observation(canvas, "canvas-emit");
      JsonNode otherEmit = observation(other, "other-emit");
      assertThat(texts(canvasEmit.path("actualArguments")))
          .containsExactly("rows", "selectedId", "'panel-source'");
      assertThat(texts(otherEmit.path("actualArguments")))
          .containsExactly("rows", "otherId", "'other-source'");
      assertThat(texts(canvasEmit.path("formalParameters"))).isEmpty();
      assertThat(texts(otherEmit.path("formalParameters"))).isEmpty();

      JsonNode callback = observation(canvas, "canvas-callback");
      assertThat(texts(callback.path("actualArguments"))).isEmpty();
      assertThat(texts(callback.path("formalParameters"))).containsExactly("first", "second");
      assertThat(nodes(callback.path("argumentBindings")))
          .allSatisfy(
              binding -> {
                assertThat(binding.path("disposition").asText()).isEqualTo("NOT_PASSED");
                assertThat(binding.path("expression").isNull()).isTrue();
              });
      assertThat(callback.path("argumentBindings").size()).isEqualTo(2);
    }
  }

  private static EntryEvidenceSet withSavedControlBranches(EntryEvidenceSet set) {
    Entry original = set.entries().get(0);
    EntryCodeContext.MethodCode prior = original.java().methods().get(0);
    String sourceText =
        "public void save() { if (externalRecordToken != null) { persist(); } "
            + "if (renamedEntityReference != null) { persist(); } }";
    String firstExpression = "externalRecordToken != null";
    String secondExpression = "renamedEntityReference != null";
    List<EntryCodeContext.Control> controls =
        List.of(control(sourceText, firstExpression), control(sourceText, secondExpression));
    SourceRange wholeMethod = new SourceRange(0, sourceText.length(), 1, 1);
    EntryCodeContext.MethodCode withControls =
        new EntryCodeContext.MethodCode(
            prior.methodKey(),
            prior.kind(),
            prior.declaringType(),
            prior.name(),
            prior.signature(),
            prior.enclosingMethodKey(),
            prior.parameters(),
            prior.returnTypeText(),
            prior.modifiers(),
            prior.annotations(),
            new EntryCodeContext.SourceSource(prior.source().path(), wholeMethod, sourceText),
            prior.bodyPresent(),
            controls,
            prior.exits());
    Java java =
        new Java(
            List.of(withControls),
            original.java().calls(),
            original.java().observations(),
            original.java().supportingSources(),
            original.java().technicalEnhancements());
    Entry controlledEntry =
        new Entry(
            original.entryId(),
            original.entry(),
            original.assemblyStatus(),
            original.coverage(),
            original.frontend(),
            java,
            original.persistence(),
            original.sourceRefs(),
            original.limitations());
    return new EntryEvidenceSet(
        set.header(),
        List.of(controlledEntry),
        set.frontendCoverage(),
        set.frontendPageContextCoverage());
  }

  private static EntryCodeContext.Control control(String source, String expression) {
    int offset = source.indexOf(expression);
    if (offset < 0) {
      throw new IllegalArgumentException("fixture control expression is absent from source");
    }
    return new EntryCodeContext.Control(
        "IF", expression, new SourceRange(offset, expression.length(), 1, 1), null);
  }

  private static UnitHandle context(String entryId, String contextId) {
    return new UnitHandle(entryId, UnitKind.FRONTEND_PAGE_CONTEXT, contextId);
  }

  private static JsonNode readContext(
      OntologyEvidenceCorpus corpus, String entryId, String contextId) {
    return corpus.read(entryId, UnitKind.FRONTEND_PAGE_CONTEXT, contextId).content();
  }

  private static Map<String, JsonNode> modelUnitsByRef(JsonNode model) {
    Map<String, JsonNode> result = new LinkedHashMap<>();
    for (JsonNode unit : model.path("units")) {
      result.put(unit.path("ref").asText(), unit);
    }
    return result;
  }

  private static Map<String, JsonNode> modelContextsByInstance(JsonNode model) {
    Map<String, JsonNode> result = new LinkedHashMap<>();
    for (JsonNode unit : model.path("units")) {
      if (!UnitKind.FRONTEND_PAGE_CONTEXT.name().equals(unit.path("kind").asText())) {
        continue;
      }
      result.put(unit.path("content").path("instanceKey").asText(), unit);
    }
    return result;
  }

  private static void assertContextSourcesResolveToCompleteBodies(
      OntologyReadingPacket packet,
      OntologyEvidenceCorpus corpus,
      String entryId,
      Map<String, JsonNode> modelUnits,
      JsonNode contextUnit,
      int expectedSources) {
    assertThat(contextUnit.path("kind").asText()).isEqualTo(UnitKind.FRONTEND_PAGE_CONTEXT.name());
    List<JsonNode> sources = nodes(contextUnit.path("content").path("sourceUnits"));
    assertThat(sources).hasSize(expectedSources);
    for (JsonNode contextSource : sources) {
      String sourceRef = contextSource.path("sourceRef").asText();
      JsonNode modelSource = modelUnits.get(sourceRef);
      assertThat(modelSource).isNotNull();
      assertThat(modelSource.path("kind").asText()).isEqualTo(UnitKind.FRONTEND_UNIT.name());
      OntologyReadingPacket.PackedUnit savedSource = packet.resolve(sourceRef);
      assertThat(savedSource.kind()).isEqualTo(UnitKind.FRONTEND_UNIT);
      JsonNode completeSavedBody =
          corpus.read(entryId, UnitKind.FRONTEND_UNIT, savedSource.originalId()).content();
      assertThat(savedSource.content()).isEqualTo(completeSavedBody);
      assertThat(modelSource.path("content").path("text").asText())
          .isEqualTo(completeSavedBody.path("text").asText())
          .isNotBlank();
    }
  }

  private static List<String> conditionExpressions(JsonNode context) {
    List<String> result = new ArrayList<>();
    for (JsonNode condition : context.path("requestConditions")) {
      result.add(condition.path("expression").asText() + "|" + condition.path("branch").asText());
    }
    return result;
  }

  private static List<String> requestMethods(JsonNode contextUnit) {
    List<String> result = new ArrayList<>();
    for (JsonNode association : contextUnit.path("content").path("requestAssociations")) {
      result.add(association.path("request").path("httpMethod").asText());
    }
    return result;
  }

  private static JsonNode observationByKind(JsonNode context, String kind) {
    List<JsonNode> matches = new ArrayList<>();
    for (JsonNode observation : context.path("observations")) {
      if (kind.equals(observation.path("kind").asText())) {
        matches.add(observation);
      }
    }
    assertThat(matches).as("projected observation %s", kind).hasSize(1);
    return matches.get(0);
  }

  private static void assertNotPassedBindings(JsonNode observation) {
    List<JsonNode> bindings = nodes(observation.path("argumentBindings"));
    assertThat(bindings).hasSize(2);
    assertThat(bindings)
        .allSatisfy(
            binding -> {
              assertThat(binding.path("disposition").asText()).isEqualTo("NOT_PASSED");
              assertThat(binding.path("expression").isNull()).isTrue();
            });
  }

  private static Map<String, String> conditionByRequest(JsonNode context) {
    Map<String, String> result = new LinkedHashMap<>();
    for (JsonNode condition : context.path("requestConditions")) {
      result.put(
          condition.path("requestId").asText(),
          condition.path("expression").asText() + "|" + condition.path("branch").asText());
    }
    return result;
  }

  private static JsonNode observation(JsonNode context, String id) {
    List<JsonNode> matches = new ArrayList<>();
    for (JsonNode observation : context.path("observations")) {
      if (id.equals(observation.path("observationId").asText())) {
        matches.add(observation);
      }
    }
    assertThat(matches).as("saved observation %s", id).hasSize(1);
    return matches.get(0);
  }

  private static List<String> expressions(JsonNode controls) {
    List<String> expressions = new ArrayList<>();
    for (JsonNode control : controls) {
      expressions.add(control.path("expression").asText());
    }
    return expressions;
  }

  private static List<String> texts(JsonNode values) {
    List<String> result = new ArrayList<>();
    for (JsonNode value : values) {
      result.add(value.asText());
    }
    return result;
  }

  private static List<JsonNode> nodes(JsonNode values) {
    List<JsonNode> result = new ArrayList<>();
    for (JsonNode value : values) {
      result.add(value);
    }
    return result;
  }
}
