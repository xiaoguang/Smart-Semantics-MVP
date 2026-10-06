package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.AliasCatalog;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct RED contracts for formal ontology material, aliases, and compact reading packets. */
class OntologyFormalMaterialContractsTest {
  private static final String FIRST = "entry:" + "0".repeat(64);
  private static final String SECOND = "entry:" + "1".repeat(64);
  private static final String THIRD = "entry:" + "2".repeat(64);
  private static final String SHARED_METHOD = "method:shared";
  private static final String CALLER_METHOD = "method:caller";
  private static final String TARGET_METHOD = "method:target";
  private static final String SECOND_TARGET_METHOD = "method:target-implementation";
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void formalV5CarriesSelectedClueMeaningAndRestoresItsExactMapping() throws Exception {
    OntologyEvidenceCorpus corpus = repeatedCallCorpus(false).withBusinessLinkNavigation();
    OntologyReadingPacket packet =
        OntologyReadingPacket.formalV5(corpus, repeatedCallSelection(), 500_000);
    String clue = corpus.aliases().clueRef(ClueKind.METHOD, "method:first-reader");
    OntologyReadingPacket contextual = packet.withVisibleClues(corpus, List.of(clue));
    JsonNode model = json.parseCanonical(contextual.modelInput());
    assertThat(model.path("visibleClues").get(0).path("ref").asText()).isEqualTo(clue);
    assertThat(model.path("visibleClues").get(0).path("kind").asText()).isEqualTo("METHOD");
    assertThat(model.path("visibleClues").get(0).path("value").asText())
        .isEqualTo(corpus.aliases().clue(clue).keyDisplay());
    assertThat(model.path("units"))
        .isEqualTo(json.parseCanonical(packet.modelInput()).path("units"));
    assertThat(contextual.packetId()).isNotEqualTo(packet.packetId());
    assertThat(packet.withVisibleClues(corpus, List.of(clue, clue)))
        .extracting(OntologyReadingPacket::packetId)
        .isEqualTo(contextual.packetId());
  }

  @Test
  void formalV5StoresSharedCallRowsOnceButKeepsEveryEntryUse() {
    OntologyEvidenceCorpus corpus = repeatedCallCorpus(false);
    List<UnitHandle> selected = repeatedCallSelection();
    OntologyReadingPacket historical = OntologyReadingPacket.formalV4(corpus, selected, 500_000);
    OntologyReadingPacket compact = OntologyReadingPacket.formalV5(corpus, selected, 500_000);
    JsonNode oldModel = json.parseCanonical(historical.modelInput());
    JsonNode model = json.parseCanonical(compact.modelInput());

    assertThat(model.path("schemaVersion").asText()).isEqualTo("ontology-model-reading-v5");
    assertThat(compact.callContextEncoding()).isEqualTo("EXACT_ROWS_WITH_USES_V1");
    assertThat(model.has("callContext")).isFalse();
    assertThat(model.path("callRows")).hasSize(5);
    assertThat(model.path("callUses")).hasSize(10);
    assertThat(textValues(model.path("callUses"), "entryRef"))
        .containsExactlyInAnyOrder("E1", "E1", "E1", "E1", "E1", "E2", "E2", "E2", "E2", "E2");
    assertThat(model.path("units")).isEqualTo(oldModel.path("units"));
    assertThat(model.path("callEvidence")).isEqualTo(oldModel.path("callEvidence"));
    ArrayNode restored = OntologyModelProjection.decodeCallRows(model);
    ObjectNode bundleDecision = new ObjectMapper().createObjectNode();
    bundleDecision.put("ruleVersion", "link-bundle-rule-v1");
    bundleDecision.put("anchorRef", "K1");
    bundleDecision.put("selectionOrigin", "EXPLICIT");
    bundleDecision.putArray("unreadCandidates");
    bundleDecision.putArray("requiredButUnread");
    bundleDecision.putArray("seedUses");
    bundleDecision.putArray("derivedUses");
    bundleDecision.putArray("derivedEntries");
    bundleDecision.putArray("groups");
    bundleDecision.putObject("cost");
    OntologyReadingPacket jointPacket =
        OntologyReadingPacket.formalV6(corpus, selected, 500_000, bundleDecision);
    assertThat(
            OntologyModelProjection.decodeCallRows(json.parseCanonical(jointPacket.modelInput())))
        .isEqualTo(restored);
    assertThat(restored).hasSize(oldModel.path("callContext").size());
    for (int index = 0; index < restored.size(); index++) {
      ObjectNode context = (ObjectNode) restored.get(index);
      assertThat(context.remove("entryRef").asText()).isEqualTo(index < 5 ? "E1" : "E2");
      assertThat(context).isEqualTo(oldModel.path("callContext").get(index));
    }
    assertThat(compact.cost().modelInputBytes()).isLessThan(historical.cost().modelInputBytes());
    assertThat(compact.packetId()).isNotEqualTo(historical.packetId());
    assertThat(json.parseCanonical(historical.modelInput()).path("schemaVersion").asText())
        .isEqualTo("ontology-model-reading-v4");
  }

  @Test
  void formalV5NeverMergesDifferentArgumentsAtTheSamePhysicalSite() {
    OntologyReadingPacket packet =
        OntologyReadingPacket.formalV5(repeatedCallCorpus(true), repeatedCallSelection(), 500_000);
    JsonNode model = json.parseCanonical(packet.modelInput());
    assertThat(model.path("callRows")).hasSize(6);
    assertThat(model.path("callUses")).hasSize(10);
    assertThat(model.toString()).contains("otherRecordId");
  }

  @Test
  void formalV5DecoderRejectsDanglingRowsAndReorderedUseOrdinals() {
    OntologyReadingPacket packet =
        OntologyReadingPacket.formalV5(repeatedCallCorpus(false), repeatedCallSelection(), 500_000);
    ObjectNode model = (ObjectNode) json.parseCanonical(packet.modelInput());
    ((ObjectNode) model.path("callUses").get(0)).put("rowRef", "C999");
    assertThatThrownBy(() -> OntologyModelProjection.decodeCallRows(model))
        .hasMessage("ONTOLOGY_CALL_USES_INVALID");
    ObjectNode reordered = (ObjectNode) json.parseCanonical(packet.modelInput());
    ((ObjectNode) reordered.path("callUses").get(1)).put("ordinal", 0);
    assertThatThrownBy(() -> OntologyModelProjection.decodeCallRows(reordered))
        .hasMessage("ONTOLOGY_CALL_USES_INVALID");
  }

  @Test
  void formalV5RestoresEverySelectedTargetForAPolymorphicCall() {
    OntologyEvidenceCorpus corpus = sameEntryMultipleTargetsCorpus();
    UnitHandle caller = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, CALLER_METHOD);
    UnitHandle target = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, TARGET_METHOD);
    UnitHandle implementation = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, SECOND_TARGET_METHOD);
    OntologyReadingPacket historical =
        OntologyReadingPacket.formalV4(corpus, List.of(caller, target, implementation), 500_000);
    OntologyReadingPacket compact =
        OntologyReadingPacket.formalV5(corpus, List.of(caller, target, implementation), 500_000);
    JsonNode historicalModel = json.parseCanonical(historical.modelInput());
    JsonNode compactModel = json.parseCanonical(compact.modelInput());
    ArrayNode restored = OntologyModelProjection.decodeCallRows(compactModel);
    String historicalCallerRef = localRef(historical, caller);
    String compactCallerRef = localRef(compact, caller);
    JsonNode expected =
        StreamSupport.stream(historicalModel.path("callContext").spliterator(), false)
            .filter(
                context ->
                    historicalCallerRef.equals(context.path("fromRef").asText())
                        && context.path("selectedTargets").size() == 2)
            .findFirst()
            .orElseThrow();
    JsonNode actual =
        StreamSupport.stream(restored.spliterator(), false)
            .filter(
                context ->
                    compactCallerRef.equals(context.path("fromRef").asText())
                        && context.path("selectedTargets").size() == 2)
            .findFirst()
            .orElseThrow();

    assertThat(textValues(actual.path("selectedTargets"), "targetRef"))
        .containsExactly(localRef(compact, target), localRef(compact, implementation));
    assertThat(actual.path("selectedTargets")).isEqualTo(expected.path("selectedTargets"));
  }

  @Test
  void formalV5KeepsSamePositionCallsSeparateWhenTheirStateOrCandidateSetDiffers()
      throws Exception {
    OntologyEvidenceCorpus corpus = samePhysicalCallVariantsCorpus();
    UnitHandle firstCaller = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, CALLER_METHOD);
    UnitHandle secondCaller = new UnitHandle(SECOND, UnitKind.JAVA_METHOD, CALLER_METHOD);
    UnitHandle thirdCaller = new UnitHandle(THIRD, UnitKind.JAVA_METHOD, CALLER_METHOD);
    List<UnitHandle> selected =
        List.of(
            firstCaller,
            new UnitHandle(FIRST, UnitKind.JAVA_METHOD, TARGET_METHOD),
            new UnitHandle(FIRST, UnitKind.JAVA_METHOD, SECOND_TARGET_METHOD),
            secondCaller,
            new UnitHandle(SECOND, UnitKind.JAVA_METHOD, TARGET_METHOD),
            new UnitHandle(SECOND, UnitKind.JAVA_METHOD, SECOND_TARGET_METHOD),
            thirdCaller,
            new UnitHandle(THIRD, UnitKind.JAVA_METHOD, TARGET_METHOD),
            new UnitHandle(THIRD, UnitKind.JAVA_METHOD, SECOND_TARGET_METHOD));

    JsonNode firstSavedCall = corpus.read(FIRST, UnitKind.JAVA_CALL, "call:normal-00").content();
    JsonNode secondSavedCall = corpus.read(SECOND, UnitKind.JAVA_CALL, "call:normal-00").content();
    JsonNode thirdSavedCall = corpus.read(THIRD, UnitKind.JAVA_CALL, "call:normal-00").content();
    assertThat(secondSavedCall.path("site")).isEqualTo(firstSavedCall.path("site"));
    assertThat(thirdSavedCall.path("site")).isEqualTo(firstSavedCall.path("site"));
    assertThat(secondSavedCall.path("actualArguments"))
        .isEqualTo(firstSavedCall.path("actualArguments"));
    assertThat(thirdSavedCall.path("actualArguments"))
        .isEqualTo(firstSavedCall.path("actualArguments"));
    assertThat(textValues(firstSavedCall.path("actualArguments"), "expression"))
        .containsExactly("customerId");
    assertThat(firstSavedCall.path("resolution").asText()).isEqualTo("LOCATED");
    assertThat(secondSavedCall.path("resolution").asText()).isEqualTo("CANDIDATES");
    assertThat(thirdSavedCall.path("resolution").asText()).isEqualTo("CANDIDATES");

    OntologyReadingPacket historical = OntologyReadingPacket.formalV4(corpus, selected, 500_000);
    OntologyReadingPacket compact = OntologyReadingPacket.formalV5(corpus, selected, 500_000);
    JsonNode historicalModel = json.parseCanonical(historical.modelInput());
    JsonNode compactModel = json.parseCanonical(compact.modelInput());
    ArrayNode restored = OntologyModelProjection.decodeCallRows(compactModel);
    assertThat(restored).hasSize(historicalModel.path("callContext").size());
    for (int index = 0; index < restored.size(); index++) {
      ObjectNode context = (ObjectNode) restored.get(index).deepCopy();
      context.remove("entryRef");
      assertThat(context).isEqualTo(historicalModel.path("callContext").get(index));
    }

    Map<String, JsonNode> rowsByRef = new LinkedHashMap<>();
    for (JsonNode row : compactModel.path("callRows")) {
      rowsByRef.put(row.path("ref").asText(), row);
    }
    Map<String, String> entryRefs =
        Map.of(
            FIRST, corpus.aliases().entryRef(FIRST),
            SECOND, corpus.aliases().entryRef(SECOND),
            THIRD, corpus.aliases().entryRef(THIRD));
    Map<String, String> callUsesByEntry = new LinkedHashMap<>();
    String firstCallerRef = localRef(compact, firstCaller);
    for (JsonNode use : compactModel.path("callUses")) {
      String entryId =
          entryRefs.entrySet().stream()
              .filter(entry -> entry.getValue().equals(use.path("entryRef").asText()))
              .map(Map.Entry::getKey)
              .findFirst()
              .orElse(null);
      if (entryId == null || !firstCallerRef.equals(use.path("fromRef").asText())) {
        continue;
      }
      JsonNode row = rowsByRef.get(use.path("rowRef").asText());
      if (row != null && samePhysicalSite(row.path("site"), firstSavedCall.path("site"))) {
        callUsesByEntry.put(entryId, use.path("rowRef").asText());
      }
    }
    assertThat(callUsesByEntry.keySet()).containsExactlyInAnyOrder(FIRST, SECOND, THIRD);
    assertThat(new HashSet<>(callUsesByEntry.values())).hasSize(3);
    assertThat(rowsByRef.get(callUsesByEntry.get(FIRST)).path("resolution").asText())
        .isEqualTo("LOCATED");
    assertThat(rowsByRef.get(callUsesByEntry.get(SECOND)).path("resolution").asText())
        .isEqualTo("CANDIDATES");
    assertThat(rowsByRef.get(callUsesByEntry.get(THIRD)).path("resolution").asText())
        .isEqualTo("CANDIDATES");
    assertThat(callEvidenceTargetNames(compactModel, rowsByRef.get(callUsesByEntry.get(SECOND))))
        .containsExactly("candidate-alpha", "candidate-beta");
    assertThat(callEvidenceTargetNames(compactModel, rowsByRef.get(callUsesByEntry.get(THIRD))))
        .containsExactly("candidate-alpha", "candidate-gamma");
  }

  private List<String> textValues(JsonNode rows, String field) {
    return StreamSupport.stream(rows.spliterator(), false)
        .map(row -> row.path(field).asText())
        .toList();
  }

  private List<UnitHandle> repeatedCallSelection() {
    return List.of(
        new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:first-reader"),
        new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:first-target"),
        new UnitHandle(SECOND, UnitKind.JAVA_METHOD, "method:first-reader"),
        new UnitHandle(SECOND, UnitKind.JAVA_METHOD, "method:first-target"));
  }

  private OntologyEvidenceCorpus repeatedCallCorpus(boolean differentArguments) {
    EntryEvidenceReader.EntryDocument first = denseCallEntry(FIRST, "first", false);
    ObjectNode second = (ObjectNode) json.parseCanonical(first.canonicalJson());
    second.put("entryId", SECOND);
    ((ObjectNode) second.path("entry")).put("route", "/neutral/second-use");
    if (differentArguments) {
      ((ObjectNode) second.path("java").path("calls").get(0).path("actualArguments").get(0))
          .put("expression", "otherRecordId");
    }
    return verifiedCorpus(
        first, new EntryEvidenceReader.EntryDocument(SECOND, json.encodeCanonical(second)));
  }

  @Test
  void corpusAliasesAreStableAcrossPagesAndUnitEnumerationOrder() {
    OntologyEvidenceCorpus ordered = corpus(false);
    OntologyEvidenceCorpus reordered = corpus(true);

    AliasCatalog first = ordered.aliases();
    AliasCatalog second = reordered.aliases();
    assertThat(first.entryRef(FIRST)).isEqualTo(second.entryRef(FIRST));
    assertThat(first.entryRef(SECOND)).isEqualTo(second.entryRef(SECOND));
    assertThat(first.canonicalMapping()).isEqualTo(second.canonicalMapping());

    assertThat(ordered.navigation(0, 1).entries()).hasSize(1);
    assertThat(ordered.navigation(1, 1).entries()).hasSize(1);
    assertThat(first.entry(first.entryRef(FIRST)).entryId()).isEqualTo(FIRST);
    assertThat(first.entry(first.entryRef(SECOND)).entryId()).isEqualTo(SECOND);
  }

  @Test
  void aliasesKeepJavaVueXmlVariantsAndDistinctEntryUsesSeparate() {
    OntologyEvidenceCorpus corpus = corpus(false);
    AliasCatalog aliases = corpus.aliases();

    UnitHandle firstMethod = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, SHARED_METHOD);
    UnitHandle secondMethod = new UnitHandle(SECOND, UnitKind.JAVA_METHOD, SHARED_METHOD);
    String firstMethodRef = aliases.unitRef(firstMethod);
    assertThat(firstMethodRef).isEqualTo(aliases.unitRef(secondMethod));
    assertThat(aliases.unitUses(firstMethodRef))
        .containsExactlyInAnyOrder(firstMethod, secondMethod);

    UnitHandle vue = new UnitHandle(FIRST, UnitKind.FRONTEND_UNIT, "vue:order-page");
    String vueRef = aliases.unitRef(vue);
    assertThat(vueRef)
        .isNotEqualTo(
            aliases.unitRef(
                new UnitHandle(SECOND, UnitKind.FRONTEND_UNIT, "vue:order-page-second")));
    assertThat(aliases.unit(vueRef).kind()).isEqualTo(UnitKind.FRONTEND_UNIT);
    assertThat(
            corpus
                .read(FIRST, UnitKind.FRONTEND_UNIT, "vue:order-page")
                .content()
                .path("text")
                .asText())
        .isEqualTo("export function saveOrder(row) { return row.linkId; }");

    List<UnitHandle> variants =
        corpus.entryUnits(FIRST, 0, 50).items().stream()
            .filter(handle -> handle.kind() == UnitKind.XML_STATEMENT)
            .toList();
    assertThat(variants).hasSize(2);
    assertThat(aliases.unitRef(variants.get(0))).isNotEqualTo(aliases.unitRef(variants.get(1)));
    JsonNode exactVariant =
        aliases.read(aliases.unitRef(variants.get(0)), aliases.entryRef(FIRST)).content();
    assertThat(exactVariant.path("databaseId").isNull()).isTrue();
    assertThat(
            aliases
                .read(aliases.unitRef(variants.get(1)), aliases.entryRef(FIRST))
                .content()
                .path("databaseId")
                .asText())
        .isEqualTo("mysql");
    assertThatThrownBy(() -> corpus.read(FIRST, UnitKind.XML_STATEMENT, "mapper:order#find"))
        .hasMessage("ONTOLOGY_UNIT_AMBIGUOUS");

    var statementClue =
        corpus.entryClues(FIRST, 20).clues().stream()
            .filter(clue -> clue.kind() == ClueKind.STATEMENT)
            .toList();
    assertThat(statementClue).hasSize(2);
    String firstClueRef = aliases.clueRef(ClueKind.STATEMENT, statementClue.get(0).lookupKey());
    String secondClueRef = aliases.clueRef(ClueKind.STATEMENT, statementClue.get(1).lookupKey());
    assertThat(firstClueRef).isNotEqualTo(secondClueRef);
    assertThat(aliases.clue(firstClueRef).lookupKey()).isEqualTo(statementClue.get(0).lookupKey());
  }

  @Test
  void formalReadingPacketUsesUniformSRefsAndRetainsExactJavaVueXmlBytes() {
    OntologyEvidenceCorpus corpus = corpus(false);
    List<UnitHandle> selected =
        List.of(
            new UnitHandle(FIRST, UnitKind.JAVA_METHOD, SHARED_METHOD),
            new UnitHandle(FIRST, UnitKind.FRONTEND_UNIT, "vue:order-page"),
            xmlVariant(corpus, 0));

    OntologyReadingPacket packet = OntologyReadingPacket.formal(corpus, selected, 20_000);
    OntologyReadingPacket reordered =
        OntologyReadingPacket.formal(
            corpus, List.of(selected.get(2), selected.get(0), selected.get(1)), 20_000);

    assertThat(packet.packetId()).isEqualTo(reordered.packetId());
    assertThat(packet.units()).allMatch(unit -> unit.localRef().startsWith("S"));
    assertThatThrownBy(() -> packet.resolve("J1")).hasMessage("ONTOLOGY_LOCAL_REFERENCE_UNKNOWN");

    JsonNode model = json.parseCanonical(packet.modelInput());
    assertThat(model.path("schemaVersion").asText()).isEqualTo("ontology-model-reading-v3");
    assertThat(model.toString()).doesNotContain(FIRST).doesNotContain("variant:");
    for (JsonNode unit : model.path("units")) {
      assertThat(unit.path("ref").asText()).startsWith("S");
    }

    for (OntologyReadingPacket.PackedUnit packed : packet.units()) {
      assertThat(packet.resolve(packed.localRef()).canonicalJson())
          .isEqualTo(packed.canonicalJson());
    }
    String javaText = null;
    String vueText = null;
    String xmlElementName = null;
    String xmlText = null;
    for (JsonNode unit : model.path("units")) {
      if ("JAVA_METHOD".equals(unit.path("kind").asText())) {
        javaText = unit.path("content").path("sourceText").asText();
      } else if ("FRONTEND_UNIT".equals(unit.path("kind").asText())) {
        vueText = unit.path("content").path("text").asText();
      } else if ("XML_STATEMENT".equals(unit.path("kind").asText())) {
        JsonNode xmlSubtree = unit.path("content").path("xmlSubtree");
        xmlElementName = xmlSubtree.path("elementName").asText();
        xmlText = xmlSubtree.path("children").get(0).path("content").asText();
      }
    }
    assertThat(javaText).isEqualTo("public void shared() { return; }");
    assertThat(vueText).isEqualTo("export function saveOrder(row) { return row.linkId; }");
    assertThat(xmlElementName).isEqualTo("select");
    assertThat(xmlText).isEqualTo("#{id}");
    assertThat(json.parseCanonical(packet.canonicalInput()).toString()).contains(FIRST);
  }

  @Test
  void formalPacketReportsMeasuredCostsAndRejectsOversizedCompleteUnits() {
    OntologyEvidenceCorpus corpus = corpus(false);
    List<UnitHandle> selected = List.of(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, SHARED_METHOD));
    OntologyReadingPacket packet = OntologyReadingPacket.formal(corpus, selected, 20_000);

    assertThat(packet.cost().fullSourceBytes()).isGreaterThan(0);
    assertThat(packet.cost().modelInputBytes()).isGreaterThan(0);
    assertThat(packet.cost().privateInputBytes()).isEqualTo(packet.canonicalInput().size());
    assertThat(packet.cost().modelInputBytes()).isEqualTo(packet.modelInput().size());
    assertThat(packet.cost().explicitCallDetailBytes()).isZero();
    assertThatThrownBy(() -> OntologyReadingPacket.formal(corpus, selected, 8))
        .hasMessage("ONTOLOGY_UNIT_TOO_LARGE");
  }

  @Test
  void highFanoutProjectionKeepsExceptionalCallPositionsButDefersNormalCallJsonToUReadBack() {
    OntologyEvidenceCorpus corpus = corpus(false);
    AliasCatalog aliases = corpus.aliases();
    OntologyReadingPacket packet =
        OntologyReadingPacket.formal(
            corpus, List.of(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, SHARED_METHOD)), 50_000);

    JsonNode model = json.parseCanonical(packet.modelInput());
    String visible = model.toString();
    assertThat(visible).contains("UNRESOLVED", "NAVIGATION_CONFLICT", "QUERY_FAILED");
    assertThat(visible).contains("startLine", "endLine", "startOffsetUtf16", "lengthUtf16");
    assertThat(visible).doesNotContain("call:normal-00").doesNotContain("normal-helper-00");
    assertThat(model.path("callContext")).isNotEmpty();

    UnitHandle normalCall = new UnitHandle(FIRST, UnitKind.JAVA_CALL, "call:normal-00");
    String normalCallRef = aliases.unitRef(normalCall);
    UnitHandle readBack = aliases.unit(normalCallRef);
    JsonNode fullDetail =
        corpus.read(readBack.entryId(), readBack.kind(), readBack.originalId()).content();
    assertThat(fullDetail.path("callKey").asText()).isEqualTo("call:normal-00");
    assertThat(fullDetail.path("targets")).isNotEmpty();
    assertThat(fullDetail.path("observations")).isNotEmpty();
  }

  @Test
  void formalCallEvidenceDictionariesPreserveEverySiteAndReduceRepeatedProjectionBytes() {
    OntologyEvidenceCorpus corpus = denseCallCorpus(false);
    List<UnitHandle> selected = denseSelectedUnits();
    OntologyReadingPacket packet = OntologyReadingPacket.formal(corpus, selected, 500_000);
    JsonNode model = json.parseCanonical(packet.modelInput());
    JsonNode callEvidence = model.path("callEvidence");
    JsonNode targetDictionary = callEvidence.path("targets");
    JsonNode observationDictionary = callEvidence.path("observations");

    assertThat(model.path("schemaVersion").asText()).isEqualTo("ontology-model-reading-v3");
    assertThat(callEvidence.isObject()).isTrue();
    assertThat(targetDictionary.isObject()).isTrue();
    assertThat(observationDictionary.isObject()).isTrue();
    assertDictionaryUsesCanonicalValueOrder(targetDictionary, "CT");
    assertDictionaryUsesCanonicalValueOrder(observationDictionary, "CO");
    assertThat(dictionaryKeys(targetDictionary)).hasSize(1);
    assertThat(dictionaryKeys(observationDictionary)).hasSize(4);

    Map<String, String> ownerByCallerRef = new HashMap<>();
    Map<String, String> targetKeyByEntry = new LinkedHashMap<>();
    for (String entryId : List.of(FIRST, SECOND)) {
      String prefix = FIRST.equals(entryId) ? "first" : "second";
      String callerKey = "method:" + prefix + "-reader";
      String targetKey = "method:" + prefix + "-target";
      targetKeyByEntry.put(entryId, targetKey);
      String callerRef = localRef(packet, new UnitHandle(entryId, UnitKind.JAVA_METHOD, callerKey));
      ownerByCallerRef.put(callerRef, entryId);
      String entryRef = corpus.aliases().entryRef(entryId);
      assertThat(packet.entryRefs(callerRef)).containsExactly(entryRef);
      JsonNode callerUnit = modelUnit(model, callerRef);
      assertThat(textValues(callerUnit.path("entryUses"))).containsExactly(entryRef);
      assertThat(callerUnit.path("content").path("sourceText").asText())
          .isEqualTo(denseSource(prefix));
      String targetRef = localRef(packet, new UnitHandle(entryId, UnitKind.JAVA_METHOD, targetKey));
      assertThat(textValues(modelUnit(model, targetRef).path("entryUses")))
          .containsExactly(entryRef);
    }
    assertThat(ownerByCallerRef).hasSize(2);

    Map<String, JsonNode> sourceCallsBySite = new LinkedHashMap<>();
    for (Map.Entry<String, String> owner : ownerByCallerRef.entrySet()) {
      String entryId = owner.getValue();
      for (UnitHandle call : corpus.entryUnits(entryId, 0, 500).items()) {
        if (call.kind() != UnitKind.JAVA_CALL) {
          continue;
        }
        JsonNode savedCall = corpus.read(entryId, call.kind(), call.originalId()).content();
        String siteKey = callSiteKey(owner.getKey(), savedCall);
        assertThat(sourceCallsBySite.putIfAbsent(siteKey, savedCall)).isNull();
      }
    }

    Set<String> seenCallSites = new HashSet<>();
    Set<String> usedTargetRefs = new LinkedHashSet<>();
    Set<String> usedObservationRefs = new LinkedHashSet<>();
    Map<String, Integer> statusCounts = new HashMap<>();
    for (JsonNode context : model.path("callContext")) {
      String callerRef = context.path("fromRef").asText();
      String entryId = ownerByCallerRef.get(callerRef);
      assertThat(entryId)
          .as("each call row must remain under its original entry method")
          .isNotNull();
      JsonNode sourceCall = sourceCallsBySite.get(callSiteKey(callerRef, context.path("site")));
      assertThat(sourceCall).as("every saved call site must have one projected row").isNotNull();
      String siteKey = callSiteKey(callerRef, context.path("site"));
      assertThat(seenCallSites.add(siteKey)).as("one row per physical call site").isTrue();
      String status = sourceCall.path("resolution").asText();
      statusCounts.merge(status, 1, Integer::sum);

      assertThat(context.path("resolution").asText()).isEqualTo(status);
      assertThat(context.path("site")).isEqualTo(projectedSite(sourceCall.path("site")));
      assertThat(context.path("actualArguments")).isEqualTo(sourceCall.path("actualArguments"));
      if (sourceCall.hasNonNull("resolutionDetail")) {
        assertThat(context.path("detail").asText())
            .isEqualTo(sourceCall.path("resolutionDetail").asText());
      } else {
        assertThat(context.has("detail")).isFalse();
      }
      assertThat(context.has("targets")).isFalse();
      assertThat(context.has("observations")).isFalse();
      assertThat(context.path("targetRefs").isArray()).isTrue();
      assertThat(context.path("observationRefs").isArray()).isTrue();

      ArrayNode expectedTargets =
          hasExceptionalCallContext(status)
              ? projectedTargets(sourceCall.path("targets"))
              : mapper.createArrayNode();
      ArrayNode expectedObservations =
          hasExceptionalCallContext(status)
              ? projectedObservations(sourceCall.path("observations"))
              : mapper.createArrayNode();
      ArrayNode resolvedTargets = dereference(context.path("targetRefs"), targetDictionary);
      ArrayNode resolvedObservations =
          dereference(context.path("observationRefs"), observationDictionary);
      assertThat(resolvedTargets).isEqualTo(expectedTargets);
      assertThat(resolvedObservations).isEqualTo(expectedObservations);

      context.path("targetRefs").forEach(ref -> usedTargetRefs.add(ref.asText()));
      context.path("observationRefs").forEach(ref -> usedObservationRefs.add(ref.asText()));
      assertThat(
              StreamSupport.stream(context.path("targetRefs").spliterator(), false)
                  .allMatch(ref -> ref.asText().startsWith("CT")))
          .isTrue();
      assertThat(
              StreamSupport.stream(context.path("observationRefs").spliterator(), false)
                  .allMatch(ref -> ref.asText().startsWith("CO")))
          .isTrue();

      if (sourceCall.path("callKey").asText().endsWith("-selected")) {
        UnitHandle selectedCall =
            new UnitHandle(entryId, UnitKind.JAVA_CALL, sourceCall.path("callKey").asText());
        UnitHandle target =
            new UnitHandle(entryId, UnitKind.JAVA_METHOD, targetKeyByEntry.get(entryId));
        String targetRef = localRef(packet, target);
        JsonNode originalTarget = sourceCall.path("targets").get(0);
        assertThat(context.path("callRef").asText()).isEqualTo(localRef(packet, selectedCall));
        assertThat(context.path("selectedTargets").size()).isEqualTo(1);
        assertThat(context.path("selectedTargets").get(0).path("targetRef").asText())
            .isEqualTo(targetRef);
        assertThat(context.path("selectedTargets").get(0).path("roles"))
            .isEqualTo(originalTarget.path("roles"));
        assertThat(context.path("selectedTargets").get(0).path("argumentAssociations"))
            .isEqualTo(originalTarget.path("argumentAssociations"));
        assertThat(context.path("targetRef").asText()).isEqualTo(targetRef);
        assertThat(context.path("argumentAssociations"))
            .isEqualTo(originalTarget.path("argumentAssociations"));
      } else {
        assertThat(context.has("callRef")).isFalse();
        assertThat(context.has("selectedTargets")).isFalse();
      }
    }

    assertThat(seenCallSites).hasSameSizeAs(sourceCallsBySite.keySet());
    assertThat(statusCounts)
        .containsEntry("LOCATED", 2)
        .containsEntry("CANDIDATES", 2)
        .containsEntry("UNRESOLVED", 2)
        .containsEntry("NAVIGATION_CONFLICT", 2)
        .containsEntry("QUERY_FAILED", 2);
    assertThat(usedTargetRefs)
        .containsExactlyInAnyOrderElementsOf(dictionaryKeys(targetDictionary));
    assertThat(usedObservationRefs)
        .containsExactlyInAnyOrderElementsOf(dictionaryKeys(observationDictionary));
    for (String status : List.of("CANDIDATES", "NAVIGATION_CONFLICT", "QUERY_FAILED")) {
      JsonNode repeated =
          StreamSupport.stream(model.path("callContext").spliterator(), false)
              .filter(context -> status.equals(context.path("resolution").asText()))
              .findFirst()
              .orElseThrow();
      assertThat(repeated.path("targetRefs").size()).isEqualTo(2);
      assertThat(repeated.path("targetRefs").get(0)).isEqualTo(repeated.path("targetRefs").get(1));
    }
    JsonNode unresolved =
        StreamSupport.stream(model.path("callContext").spliterator(), false)
            .filter(context -> "UNRESOLVED".equals(context.path("resolution").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(unresolved.path("targetRefs").size()).isZero();
    assertThat(unresolved.path("observationRefs").size()).isEqualTo(2);
    assertThat(unresolved.path("observationRefs").get(0))
        .isEqualTo(unresolved.path("observationRefs").get(1));

    ObjectNode inlineEquivalent = inlineCallEvidence(model);
    int inlineBytes = json.encodeCanonical(inlineEquivalent).size();
    int compactBytes = packet.modelInput().size();
    assertThat(packet.cost().modelInputBytes()).isEqualTo(compactBytes);
    assertThat(inlineBytes - compactBytes)
        .as("interning repeated exact atoms must measurably shrink this dense neutral packet")
        .isPositive();

    OntologyEvidenceCorpus reorderedCorpus = denseCallCorpus(true);
    List<UnitHandle> reversedSelection = new ArrayList<>(denseSelectedUnits());
    java.util.Collections.reverse(reversedSelection);
    OntologyReadingPacket reorderedPacket =
        OntologyReadingPacket.formal(reorderedCorpus, reversedSelection, 500_000);
    assertThat(reorderedPacket.modelInput()).isEqualTo(packet.modelInput());

    OntologyReadingPacket emptyCallPacket =
        OntologyReadingPacket.formal(
            sameEntryCallerTargetCorpus("customerId"),
            List.of(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, CALLER_METHOD)),
            50_000);
    JsonNode emptyCallModel = json.parseCanonical(emptyCallPacket.modelInput());
    assertThat(emptyCallModel.path("callContext").size()).isZero();
    assertThat(emptyCallModel.path("callEvidence").path("targets").isObject()).isTrue();
    assertThat(emptyCallModel.path("callEvidence").path("targets").size()).isZero();
    assertThat(emptyCallModel.path("callEvidence").path("observations").isObject()).isTrue();
    assertThat(emptyCallModel.path("callEvidence").path("observations").size()).isZero();

    OntologyEvidenceCorpus legacyCorpus = corpus(false);
    OntologyReadingPacket legacyPacket =
        OntologyReadingPacket.of(
            legacyCorpus.sourceIdentity(),
            List.of(legacyCorpus.read(FIRST, UnitKind.JAVA_METHOD, SHARED_METHOD)));
    JsonNode legacyModel = json.parseCanonical(legacyPacket.modelInput());
    assertThat(legacyModel.path("schemaVersion").asText()).isEqualTo("ontology-model-reading-v2");
    assertThat(legacyModel.has("callEvidence")).isFalse();
  }

  @Test
  void formalV4RetainsFullMethodAndReversibleCallEvidenceWhileCompactingTheModelPacket() {
    OntologyEvidenceCorpus corpus = denseCallCorpus(false, true);
    List<UnitHandle> selected = denseSelectedUnits();
    OntologyReadingPacket v3Before = OntologyReadingPacket.formal(corpus, selected, 500_000);
    ImmutableBytes originalV3Model = v3Before.modelInput();
    assertThat(json.parseCanonical(originalV3Model).path("schemaVersion").asText())
        .isEqualTo("ontology-model-reading-v3");

    OntologyReadingPacket v4 = OntologyReadingPacket.formalV4(corpus, selected, 500_000);
    JsonNode model = json.parseCanonical(v4.modelInput());
    assertThat(model.path("schemaVersion").asText()).isEqualTo("ontology-model-reading-v4");
    assertThat(model.path("callEvidence").path("targets").isObject()).isTrue();
    assertThat(model.path("callEvidence").path("observations").isObject()).isTrue();

    String entryId = FIRST;
    String prefix = "first";
    UnitHandle caller = new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:first-reader");
    UnitHandle target = new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:first-target");
    UnitHandle selectedCall = new UnitHandle(entryId, UnitKind.JAVA_CALL, "call:first-selected");
    String callerRef = localRef(v4, caller);
    String targetRef = localRef(v4, target);
    String callRef = localRef(v4, selectedCall);
    String entryRef = corpus.aliases().entryRef(entryId);
    JsonNode callerUnit = modelUnit(model, callerRef);
    JsonNode callerContent = callerUnit.path("content");
    assertThat(callerContent.path("sourceText").asText())
        .isEqualTo(denseSourceWithDuplicateControls(prefix));
    assertThat(textValues(callerUnit.path("entryUses"))).containsExactly(entryRef);
    assertThat(callerContent.path("controls")).hasSize(2);
    assertThat(callerContent.path("controls").get(0).path("kind").asText()).isEqualTo("IF");
    assertThat(callerContent.path("controls").get(1).path("kind").asText()).isEqualTo("IF");
    assertThat(callerContent.path("controls").get(0).path("expression").asText())
        .isEqualTo("recordId != null");
    assertThat(callerContent.path("controls").get(1).path("expression").asText())
        .isEqualTo("recordId != null");
    assertThat(callerContent.path("controls").get(0).path("sourceRange"))
        .isNotEqualTo(callerContent.path("controls").get(1).path("sourceRange"));
    assertThat(callerContent.path("exits")).hasSize(2);
    assertThat(callerContent.path("exits").get(0).path("kind").asText()).isEqualTo("RETURN");
    assertThat(callerContent.path("exits").get(1).path("kind").asText()).isEqualTo("RETURN");
    assertThat(callerContent.path("exits").get(0).path("sourceRange"))
        .isNotEqualTo(callerContent.path("exits").get(1).path("sourceRange"));

    JsonNode selectedCallUnit = modelUnit(model, callRef);
    assertThat(textValues(selectedCallUnit.path("content").path("enclosingControlIndexes")))
        .containsExactly("0", "1");
    JsonNode savedCall =
        corpus.read(entryId, UnitKind.JAVA_CALL, selectedCall.originalId()).content();
    JsonNode selectedContext =
        StreamSupport.stream(model.path("callContext").spliterator(), false)
            .filter(context -> callRef.equals(context.path("callRef").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(selectedContext.path("fromRef").asText()).isEqualTo(callerRef);
    assertThat(selectedContext.path("site")).isEqualTo(projectedSite(savedCall.path("site")));
    assertThat(selectedContext.path("actualArguments"))
        .isEqualTo(savedCall.path("actualArguments"));
    assertThat(selectedContext.path("selectedTargets")).hasSize(1);
    assertThat(selectedContext.path("selectedTargets").get(0).path("targetRef").asText())
        .isEqualTo(targetRef);
    assertThat(selectedContext.path("selectedTargets").get(0).path("argumentAssociations"))
        .isEqualTo(savedCall.path("targets").path(0).path("argumentAssociations"));
    assertThat(selectedContext.has("targetRef")).isFalse();
    assertThat(selectedContext.has("argumentAssociations")).isFalse();
    assertThat(textValues(modelUnit(model, targetRef).path("entryUses"))).containsExactly(entryRef);

    JsonNode privateInput = json.parseCanonical(v4.canonicalInput());
    JsonNode privateCaller =
        StreamSupport.stream(privateInput.path("units").spliterator(), false)
            .filter(unit -> callerRef.equals(unit.path("localRef").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(privateCaller.path("content").path("source").path("text").asText())
        .isEqualTo(denseSourceWithDuplicateControls(prefix));
    JsonNode reversibleCall =
        StreamSupport.stream(privateInput.path("formalCallSites").spliterator(), false)
            .filter(
                site ->
                    selectedCall.originalId().equals(site.path("call").path("originalId").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(reversibleCall.path("caller").path("originalId").asText())
        .isEqualTo(caller.originalId());
    assertThat(reversibleCall.path("canonicalCall").path("site")).isEqualTo(savedCall.path("site"));
    assertThat(reversibleCall.path("canonicalCall").path("actualArguments"))
        .isEqualTo(savedCall.path("actualArguments"));

    OntologyReadingPacket v3After = OntologyReadingPacket.formal(corpus, selected, 500_000);
    assertThat(v3After.modelProjectionVersion()).isEqualTo("ontology-model-reading-v3");
    assertThat(v3After.modelInput()).isEqualTo(originalV3Model);
    assertThat(v3Before.modelInput()).isEqualTo(originalV3Model);
    assertThat(v4.modelInput().size())
        .as("v4 must reduce repeated dense formal evidence without dropping selected source facts")
        .isLessThan(originalV3Model.size());
  }

  @Test
  void formalCallTargetMustBeSelectedUnderTheSameEntryAsItsCaller() {
    OntologyEvidenceCorpus corpus = crossEntryTargetCorpus();
    UnitHandle caller = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, SHARED_METHOD);
    UnitHandle target = new UnitHandle(SECOND, UnitKind.JAVA_METHOD, TARGET_METHOD);
    UnitHandle selectedCall = new UnitHandle(FIRST, UnitKind.JAVA_CALL, "call:normal-00");
    OntologyReadingPacket packet =
        OntologyReadingPacket.formal(corpus, List.of(caller, target, selectedCall), 50_000);

    JsonNode model = json.parseCanonical(packet.modelInput());
    String callerRef = localRef(packet, caller);
    String targetRef = localRef(packet, target);
    boolean callerContextFound = false;
    for (JsonNode context : model.path("callContext")) {
      if (callerRef.equals(context.path("fromRef").asText())) {
        callerContextFound = true;
        assertThat(context.path("targetRef").asText())
            .as("a target from another entry must not become a caller link")
            .isNotEqualTo(targetRef);
      }
    }
    assertThat(callerContextFound).isTrue();
  }

  @Test
  void formalCallContextRetainsSameEntryArgumentsAndSelectedTargetAssociations() {
    OntologyEvidenceCorpus corpus = sameEntryCallerTargetCorpus("customerId");
    UnitHandle caller = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, CALLER_METHOD);
    UnitHandle target = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, TARGET_METHOD);
    OntologyReadingPacket packet =
        OntologyReadingPacket.formal(corpus, List.of(caller, target), 50_000);

    JsonNode model = json.parseCanonical(packet.modelInput());
    String callerRef = localRef(packet, caller);
    String targetRef = localRef(packet, target);
    JsonNode context = null;
    for (JsonNode item : model.path("callContext")) {
      if (callerRef.equals(item.path("fromRef").asText())) {
        context = item;
        break;
      }
    }
    assertThat(context).isNotNull();
    assertThat(context.path("targetRef").asText()).isEqualTo(targetRef);
    assertThat(context.path("actualArguments").get(0).path("ordinal").asInt()).isEqualTo(0);
    assertThat(context.path("actualArguments").get(0).path("expression").asText())
        .isEqualTo("customerId");
    assertThat(context.path("argumentAssociations").get(0).path("actualOrdinals").get(0).asInt())
        .isEqualTo(0);
    assertThat(context.path("argumentAssociations").get(0).path("formalOrdinal").asInt())
        .isEqualTo(0);
    assertThat(json.parseCanonical(packet.canonicalInput()).path("formalCallSites")).isNotEmpty();
  }

  @Test
  void formalPacketIdentityChangesWhenOnlyCallActualArgumentsChange() {
    OntologyEvidenceCorpus firstCorpus = sameEntryCallerTargetCorpus("customerId");
    OntologyEvidenceCorpus secondCorpus = sameEntryCallerTargetCorpus("orderId");
    List<UnitHandle> selected =
        List.of(
            new UnitHandle(FIRST, UnitKind.JAVA_METHOD, CALLER_METHOD),
            new UnitHandle(FIRST, UnitKind.JAVA_METHOD, TARGET_METHOD));

    OntologyReadingPacket first = OntologyReadingPacket.formal(firstCorpus, selected, 50_000);
    OntologyReadingPacket second = OntologyReadingPacket.formal(secondCorpus, selected, 50_000);

    assertThat(first.packetId()).isNotEqualTo(second.packetId());
    assertThat(first.canonicalInput()).isNotEqualTo(second.canonicalInput());
    assertThat(packedUnit(first, CALLER_METHOD).canonicalJson())
        .isEqualTo(packedUnit(second, CALLER_METHOD).canonicalJson());
    assertThat(packedUnit(first, CALLER_METHOD).content().path("source").path("text").asText())
        .isEqualTo(
            packedUnit(second, CALLER_METHOD).content().path("source").path("text").asText());
  }

  @Test
  void formalCallContextPreservesEverySelectedSameEntryTarget() {
    OntologyEvidenceCorpus corpus = sameEntryMultipleTargetsCorpus();
    UnitHandle caller = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, CALLER_METHOD);
    UnitHandle target = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, TARGET_METHOD);
    UnitHandle implementation = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, SECOND_TARGET_METHOD);
    OntologyReadingPacket packet =
        OntologyReadingPacket.formal(corpus, List.of(caller, target, implementation), 50_000);

    JsonNode model = json.parseCanonical(packet.modelInput());
    String callerRef = localRef(packet, caller);
    String targetRef = localRef(packet, target);
    String implementationRef = localRef(packet, implementation);
    JsonNode context = null;
    for (JsonNode item : model.path("callContext")) {
      if (callerRef.equals(item.path("fromRef").asText())) {
        context = item;
        break;
      }
    }
    assertThat(context).isNotNull();
    assertThat(context.path("selectedTargets").size()).isEqualTo(2);
    boolean targetFound = false;
    boolean implementationFound = false;
    for (JsonNode selected : context.path("selectedTargets")) {
      assertThat(selected.path("roles")).isNotEmpty();
      assertThat(selected.path("argumentAssociations")).isNotEmpty();
      if (targetRef.equals(selected.path("targetRef").asText())) {
        targetFound = true;
      }
      if (implementationRef.equals(selected.path("targetRef").asText())) {
        implementationFound = true;
      }
    }
    assertThat(targetFound).isTrue();
    assertThat(implementationFound).isTrue();
  }

  @Test
  void sqlAnalysisVariantsExposeStableUHandlesAndAstCluesRetainEveryReadableVariant() {
    OntologyEvidenceCorpus corpus = sqlAnalysisCorpus();
    AliasCatalog aliases = corpus.aliases();
    List<UnitHandle> analyses =
        corpus.entryUnits(FIRST, 0, 50).items().stream()
            .filter(handle -> handle.kind() == UnitKind.SQL_ANALYSIS)
            .toList();

    assertThat(analyses).hasSize(2);
    assertThat(
            corpus.entryUnits(FIRST, 0, 50).items().stream()
                .filter(handle -> handle.kind() == UnitKind.XML_STATEMENT)
                .map(handle -> corpus.read(FIRST, handle.kind(), handle.originalId()).content())
                .map(
                    statement ->
                        statement.path("databaseId").isNull()
                            ? null
                            : statement.path("databaseId").asText())
                .toList())
        .containsExactly((String) null, "mysql");
    List<String> copies =
        analyses.stream()
            .map(handle -> aliases.read(aliases.unitRef(handle), aliases.entryRef(FIRST)).content())
            .map(content -> content.path("analysisCopy").asText())
            .sorted()
            .toList();
    assertThat(copies)
        .containsExactly(
            "SELECT id FROM orders /* mysql */ WHERE order_id = #{id}",
            "SELECT id FROM orders WHERE order_id = #{id}");
    for (UnitHandle analysis : analyses) {
      String ref = aliases.unitRef(analysis);
      UnitHandle secondUse = new UnitHandle(SECOND, UnitKind.SQL_ANALYSIS, analysis.originalId());
      assertThat(aliases.unitUses(ref)).containsExactlyInAnyOrder(analysis, secondUse);
    }

    List<String> searchedCopies =
        corpus.searchLiteral("SELECT id FROM orders", 0, 20).matches().stream()
            .map(
                match -> {
                  UnitHandle handle =
                      new UnitHandle(match.entryId(), match.kind(), match.originalId());
                  return aliases
                      .read(aliases.unitRef(handle), aliases.entryRef(match.entryId()))
                      .content()
                      .path("analysisCopy")
                      .asText();
                })
            .distinct()
            .sorted()
            .toList();
    assertThat(searchedCopies)
        .containsExactly(
            "SELECT id FROM orders /* mysql */ WHERE order_id = #{id}",
            "SELECT id FROM orders WHERE order_id = #{id}");

    List<OntologyEvidenceCorpus.NavigationClue> tableClues =
        corpus.entryClues(FIRST, 20).clues().stream()
            .filter(clue -> clue.kind() == ClueKind.TABLE)
            .toList();
    List<OntologyEvidenceCorpus.NavigationClue> columnClues =
        corpus.entryClues(FIRST, 20).clues().stream()
            .filter(clue -> clue.kind() == ClueKind.COLUMN)
            .toList();
    assertThat(tableClues).hasSize(1);
    assertThat(columnClues).hasSize(1);
    assertThat(tableClues.get(0).totalUses()).isEqualTo(4);
    assertThat(columnClues.get(0).totalUses()).isEqualTo(4);
    assertThat(aliases.unitRef(tableClues.get(0).readableUnit()))
        .isIn(analyses.stream().map(aliases::unitRef).toList());
    assertThat(aliases.unitRef(columnClues.get(0).readableUnit()))
        .isIn(analyses.stream().map(aliases::unitRef).toList());

    JsonNode mapping = json.parseCanonical(aliases.canonicalMapping());
    List<String> expectedUnitRefs = analyses.stream().map(aliases::unitRef).sorted().toList();
    assertThat(arrayTexts(mappingClue(mapping, ClueKind.TABLE, "orders").path("readableUnitRefs")))
        .containsExactlyElementsOf(expectedUnitRefs);
    assertThat(
            arrayTexts(mappingClue(mapping, ClueKind.COLUMN, "order_id").path("readableUnitRefs")))
        .containsExactlyElementsOf(expectedUnitRefs);
  }

  @Test
  void formalFrontendRequestProjectionDropsWrapperIdentityButKeepsSiteAndRequestContext() {
    OntologyEvidenceCorpus corpus = frontendRequestCorpus();
    UnitHandle request =
        new UnitHandle(FIRST, UnitKind.FRONTEND_REQUEST_USE, "frontend:save-order");
    OntologyReadingPacket packet = OntologyReadingPacket.formal(corpus, List.of(request), 50_000);

    JsonNode projected =
        json.parseCanonical(packet.modelInput()).path("units").get(0).path("content");
    JsonNode projectedRequest = projected.path("request");
    JsonNode projectedWrapper = projectedRequest.path("wrapperPath").get(0);
    assertThat(projectedWrapper.path("sourceSha256").isMissingNode()).isTrue();
    assertThat(projectedWrapper.path("fromUnit").isMissingNode()).isTrue();
    assertThat(projectedWrapper.path("toUnit").isMissingNode()).isTrue();
    assertThat(projectedWrapper.path("sourcePath").asText()).isEqualTo("src/pages/Order.vue");
    assertThat(projectedWrapper.path("sourceUnitRange").path("startLine").asInt()).isEqualTo(20);
    assertThat(projectedWrapper.path("sourceUnitKind").asText()).isEqualTo("FUNCTION");
    assertThat(projectedRequest.path("pagePath").asText()).isEqualTo("src/pages/Order.vue");
    assertThat(projectedRequest.path("instanceKey").asText()).isEqualTo("order-page");
    assertThat(projectedRequest.path("httpMethod").asText()).isEqualTo("POST");
    assertThat(projectedRequest.path("rawUrlExpression").asText()).isEqualTo("saveOrder(row)");
    assertThat(projectedRequest.path("resolvedPath").asText()).isEqualTo("/orders/save");
    assertThat(projectedRequest.path("argumentBindings").get(0).path("expression").asText())
        .isEqualTo("row.id");

    JsonNode source = corpus.read(FIRST, request.kind(), request.originalId()).content();
    JsonNode legacy = OntologyModelProjection.project(UnitKind.FRONTEND_REQUEST_USE, source);
    JsonNode legacyWrapper = legacy.path("request").path("wrapperPath").get(0);
    assertThat(legacyWrapper.path("sourceSha256").asText()).isEqualTo("a".repeat(64));
    assertThat(legacyWrapper.path("fromUnit").asText()).isEqualTo("private:order-page");
    assertThat(legacyWrapper.path("toUnit").asText()).isEqualTo("private:http-client");
  }

  @Test
  void exceptionalCallContextRetainsCandidateFieldsWithoutCreatingTargetLink() {
    OntologyEvidenceCorpus corpus = corpus(false);
    UnitHandle method = new UnitHandle(FIRST, UnitKind.JAVA_METHOD, SHARED_METHOD);
    OntologyReadingPacket packet = OntologyReadingPacket.formal(corpus, List.of(method), 50_000);

    JsonNode model = json.parseCanonical(packet.modelInput());
    JsonNode conflict = null;
    for (JsonNode context : model.path("callContext")) {
      if ("NAVIGATION_CONFLICT".equals(context.path("resolution").asText())) {
        conflict = context;
        break;
      }
    }
    assertThat(conflict).isNotNull();
    JsonNode targetDictionary = model.path("callEvidence").path("targets");
    JsonNode candidateRef = conflict.path("targetRefs").get(0);
    assertThat(candidateRef).isNotNull();
    JsonNode candidate = targetDictionary.path(candidateRef.asText());
    assertThat(candidate.path("displayName").asText()).isEqualTo("conflicting-helper");
    assertThat(arrayTexts(candidate.path("roles"))).containsExactly("DECLARATION");
    assertThat(candidate.path("reason").asText())
        .isEqualTo("NAVIGATION_CONFLICT candidate requires review");
    assertThat(conflict.path("targetRef").isMissingNode()).isTrue();
  }

  private OntologyEvidenceCorpus denseCallCorpus(boolean reverseCalls) {
    return denseCallCorpus(reverseCalls, false);
  }

  private OntologyEvidenceCorpus denseCallCorpus(
      boolean reverseCalls, boolean duplicateControlsAndExits) {
    EntryEvidenceReader.EntryDocument first =
        denseCallEntry(FIRST, "first", reverseCalls, duplicateControlsAndExits);
    EntryEvidenceReader.EntryDocument second =
        denseCallEntry(SECOND, "second", reverseCalls, duplicateControlsAndExits);
    return verifiedCorpus(first, second);
  }

  private EntryEvidenceReader.EntryDocument denseCallEntry(
      String entryId, String prefix, boolean reverseCalls) {
    return denseCallEntry(entryId, prefix, reverseCalls, false);
  }

  private EntryEvidenceReader.EntryDocument denseCallEntry(
      String entryId, String prefix, boolean reverseCalls, boolean duplicateControlsAndExits) {
    ObjectNode entry =
        (ObjectNode)
            json.parseCanonical(
                document(entryId, "/neutral/" + prefix, "vue:" + prefix, false).canonicalJson());
    String callerKey = "method:" + prefix + "-reader";
    String targetKey = "method:" + prefix + "-target";
    String sourceText =
        duplicateControlsAndExits ? denseSourceWithDuplicateControls(prefix) : denseSource(prefix);

    ObjectNode httpEntry = (ObjectNode) entry.path("entry");
    httpEntry.put("methodKey", callerKey);
    ObjectNode java = (ObjectNode) entry.path("java");
    ObjectNode caller = (ObjectNode) java.path("methods").get(0);
    configureDenseMethod(
        caller, callerKey, prefix + "Reader", "src/" + prefix + "/Reader.java", sourceText);
    if (duplicateControlsAndExits) {
      configureDenseDuplicateControlsAndExits(caller, sourceText);
    }
    addDenseStringParameter(caller, prefix + "Reader");
    ObjectNode target = caller.deepCopy();
    configureDenseMethod(
        target,
        targetKey,
        prefix + "Target",
        "src/" + prefix + "/Target.java",
        "public Object " + prefix + "Target(String recordId) { return recordId; }");
    addDenseStringParameter(target, prefix + "Target");
    ((ArrayNode) java.path("methods")).add(target);
    if (entry.path("persistence").path("bindings").isArray()
        && entry.path("persistence").path("bindings").size() > 0) {
      ((ObjectNode) entry.path("persistence").path("bindings").get(0)).put("methodKey", callerKey);
    }

    List<ObjectNode> calls =
        List.of(
            denseCall(
                prefix, callerKey, "selected", "LOCATED", prefix + "Target", sourceText, targetKey),
            denseCall(
                prefix,
                callerKey,
                "candidate",
                "CANDIDATES",
                prefix + "Candidate",
                sourceText,
                null),
            denseCall(
                prefix,
                callerKey,
                "unresolved",
                "UNRESOLVED",
                prefix + "Unresolved",
                sourceText,
                null),
            denseCall(
                prefix,
                callerKey,
                "conflict",
                "NAVIGATION_CONFLICT",
                prefix + "Conflict",
                sourceText,
                null),
            denseCall(
                prefix,
                callerKey,
                "failure",
                "QUERY_FAILED",
                prefix + "Failure",
                sourceText,
                null));
    if (duplicateControlsAndExits) {
      ((ArrayNode) calls.get(0).path("enclosingControlIndexes")).add(0).add(1);
    }
    ArrayNode callArray = java.putArray("calls");
    if (reverseCalls) {
      for (int index = calls.size() - 1; index >= 0; index--) {
        callArray.add(calls.get(index));
      }
    } else {
      calls.forEach(callArray::add);
    }
    return new EntryEvidenceReader.EntryDocument(entryId, json.encodeCanonical(entry));
  }

  private void configureDenseMethod(
      ObjectNode method, String methodKey, String name, String path, String sourceText) {
    method.put("methodKey", methodKey);
    method.put("name", name);
    method.put("signature", name + "()");
    ObjectNode source = (ObjectNode) method.path("source");
    int newlineCount = (int) sourceText.chars().filter(value -> value == '\n').count();
    source.put("path", path);
    source.put("startLine", 1);
    source.put("endLine", newlineCount + (sourceText.endsWith("\n") ? 0 : 1));
    source.put("startOffsetUtf16", 0);
    source.put("lengthUtf16", sourceText.length());
    source.put("text", sourceText);
  }

  private void configureDenseDuplicateControlsAndExits(ObjectNode method, String sourceText) {
    String condition = "if (recordId != null)";
    int firstCondition = sourceText.indexOf(condition);
    int secondCondition = sourceText.indexOf(condition, firstCondition + condition.length());
    assertThat(firstCondition).isGreaterThanOrEqualTo(0);
    assertThat(secondCondition).isGreaterThan(firstCondition);
    ArrayNode controls = (ArrayNode) method.path("controls");
    for (int index = 0; index < 2; index++) {
      int start = index == 0 ? firstCondition : secondCondition;
      ObjectNode control = controls.addObject();
      control.put("kind", "IF");
      control.put("expression", "recordId != null");
      control.set("sourceRange", denseSourceRange(sourceText, start, start + condition.length()));
      if (index == 0) {
        control.putNull("parentControlIndex");
      } else {
        control.put("parentControlIndex", 0);
      }
    }

    String exitText = "return;";
    int firstExit = sourceText.indexOf(exitText);
    int secondExit = sourceText.indexOf(exitText, firstExit + exitText.length());
    assertThat(firstExit).isGreaterThanOrEqualTo(0);
    assertThat(secondExit).isGreaterThan(firstExit);
    ArrayNode exits = (ArrayNode) method.path("exits");
    for (int start : List.of(firstExit, secondExit)) {
      ObjectNode exit = exits.addObject();
      exit.put("kind", "RETURN");
      exit.putNull("expression");
      exit.set("sourceRange", denseSourceRange(sourceText, start, start + exitText.length()));
    }
  }

  private ObjectNode denseSourceRange(String sourceText, int start, int end) {
    ObjectNode range = mapper.createObjectNode();
    int startLine = 1 + (int) sourceText.substring(0, start).chars().filter(c -> c == '\n').count();
    int endLine = 1 + (int) sourceText.substring(0, end - 1).chars().filter(c -> c == '\n').count();
    range.put("startLine", startLine);
    range.put("endLine", endLine);
    range.put("startOffsetUtf16", start);
    range.put("lengthUtf16", end - start);
    return range;
  }

  private String denseSource(String prefix) {
    return "public void "
        + prefix
        + "Reader(String recordId) {\n"
        + "  "
        + prefix
        + "Target(recordId);\n"
        + "  "
        + prefix
        + "Candidate();\n"
        + "  "
        + prefix
        + "Unresolved();\n"
        + "  "
        + prefix
        + "Conflict();\n"
        + "  "
        + prefix
        + "Failure();\n"
        + "}\n";
  }

  private String denseSourceWithDuplicateControls(String prefix) {
    return "public void "
        + prefix
        + "Reader(String recordId) {\n"
        + "  if (recordId != null) {\n"
        + "    if (recordId != null) {\n"
        + "      "
        + prefix
        + "Target(recordId);\n"
        + "    }\n"
        + "  }\n"
        + "  "
        + prefix
        + "Candidate();\n"
        + "  "
        + prefix
        + "Unresolved();\n"
        + "  "
        + prefix
        + "Conflict();\n"
        + "  "
        + prefix
        + "Failure();\n"
        + "  if (recordId != null) { return; }\n"
        + "  if (recordId != null) { return; }\n"
        + "}\n";
  }

  private ObjectNode denseCall(
      String prefix,
      String callerKey,
      String suffix,
      String status,
      String helper,
      String sourceText,
      String locatedTargetKey) {
    String expression = "LOCATED".equals(status) ? helper + "(recordId)" : helper + "()";
    int offset = sourceText.indexOf(expression);
    if (offset < 0) {
      throw new AssertionError("fixture call expression is absent from its complete source");
    }
    int line =
        (int) sourceText.substring(0, offset).chars().filter(value -> value == '\n').count() + 1;
    ObjectNode call = call("call:" + prefix + "-" + suffix, line, status, helper);
    call.put("callerMethodKey", callerKey);
    call.put("expression", expression);
    setDenseRange((ObjectNode) call.path("site"), line, offset, expression.length());
    setDenseRange((ObjectNode) call.path("navigationSite"), line, offset, expression.length());

    ArrayNode actualArguments = call.putArray("actualArguments");
    if ("LOCATED".equals(status)) {
      actualArguments.addObject().put("ordinal", 0).put("expression", "recordId");
      ObjectNode target = (ObjectNode) call.path("targets").get(0);
      target.put("methodKey", locatedTargetKey);
      target.put("displayName", prefix + "Target");
      ArrayNode associations = target.putArray("argumentAssociations");
      associations.addObject().putArray("actualOrdinals").add(0);
      ObjectNode association = (ObjectNode) associations.get(0);
      association.put("formalOrdinal", 0);
      association.put("kind", "POSITIONAL");
      return call;
    }

    ArrayNode targets = call.putArray("targets");
    if (!"UNRESOLVED".equals(status)) {
      for (int index = 0; index < 2; index++) {
        ObjectNode candidate = targets.addObject();
        candidate.putNull("methodKey");
        candidate.put("displayName", "candidate-target");
        candidate.putArray("roles").add("DECLARATION");
        candidate.putArray("navigationKinds").add("CALL_HIERARCHY");
        candidate.put("expansion", "NOT_EXPANDED");
        candidate.put("reason", "Neutral candidate retained for review.");
        candidate.putArray("argumentAssociations");
      }
    }
    ArrayNode observations = call.putArray("observations");
    for (int index = 0; index < 2; index++) {
      ObjectNode observation = observations.addObject();
      observation.put("code", "OBS_" + status);
      observation.put("association", "QUERY_FAILED".equals(status) ? "FAILED" : "UNCONFIRMED");
      observation.put("detail", "Neutral saved observation for " + status + ".");
      observation.put("displayIdentity", "neutral-helper");
      observation.put("typeOrigin", "SOURCE");
    }
    return call;
  }

  private void setDenseRange(ObjectNode range, int line, int offset, int length) {
    range.put("startLine", line);
    range.put("endLine", line);
    range.put("startOffsetUtf16", offset);
    range.put("lengthUtf16", length);
  }

  private void addDenseStringParameter(ObjectNode method, String signatureName) {
    method.put("signature", signatureName + "(java.lang.String)");
    method
        .putArray("parameters")
        .addObject()
        .put("ordinal", 0)
        .put("name", "recordId")
        .put("typeText", "java.lang.String");
  }

  private List<UnitHandle> denseSelectedUnits() {
    List<UnitHandle> selected = new ArrayList<>();
    for (String entryId : List.of(FIRST, SECOND)) {
      String prefix = FIRST.equals(entryId) ? "first" : "second";
      selected.add(new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:" + prefix + "-reader"));
      selected.add(new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:" + prefix + "-target"));
      selected.add(new UnitHandle(entryId, UnitKind.JAVA_CALL, "call:" + prefix + "-selected"));
    }
    return selected;
  }

  private void assertDictionaryUsesCanonicalValueOrder(JsonNode dictionary, String prefix) {
    List<String> keys = dictionaryKeys(dictionary);
    List<String> canonicalValues =
        keys.stream().map(key -> canonicalJson(dictionary.path(key))).toList();
    List<String> sortedValues = new ArrayList<>(canonicalValues);
    sortedValues.sort(Comparator.naturalOrder());
    assertThat(canonicalValues).containsExactlyElementsOf(sortedValues);
    assertThat(new HashSet<>(canonicalValues)).hasSameSizeAs(canonicalValues);
    List<String> expectedKeys = new ArrayList<>();
    for (int index = 1; index <= keys.size(); index++) {
      expectedKeys.add(prefix + index);
    }
    assertThat(keys).containsExactlyElementsOf(expectedKeys);
  }

  private List<String> dictionaryKeys(JsonNode dictionary) {
    List<String> keys = new ArrayList<>();
    dictionary.fieldNames().forEachRemaining(keys::add);
    keys.sort(Comparator.naturalOrder());
    return List.copyOf(keys);
  }

  private List<String> textValues(JsonNode values) {
    List<String> text = new ArrayList<>();
    values.forEach(value -> text.add(value.asText()));
    return List.copyOf(text);
  }

  private JsonNode modelUnit(JsonNode model, String localRef) {
    for (JsonNode unit : model.path("units")) {
      if (localRef.equals(unit.path("ref").asText())) {
        return unit;
      }
    }
    throw new AssertionError("model unit is missing " + localRef);
  }

  private String callSiteKey(String callerRef, JsonNode callOrSite) {
    JsonNode site = callOrSite.has("site") ? callOrSite.path("site") : callOrSite;
    return callerRef
        + "|"
        + site.path("startOffsetUtf16").asInt()
        + "|"
        + site.path("lengthUtf16").asInt();
  }

  private boolean hasExceptionalCallContext(String status) {
    return List.of("CANDIDATES", "UNRESOLVED", "NAVIGATION_CONFLICT", "QUERY_FAILED")
        .contains(status);
  }

  private ObjectNode projectedSite(JsonNode source) {
    ObjectNode projected = mapper.createObjectNode();
    copyFields(source, projected, "startLine", "endLine", "startOffsetUtf16", "lengthUtf16");
    return projected;
  }

  private ArrayNode projectedTargets(JsonNode source) {
    ArrayNode projected = mapper.createArrayNode();
    for (JsonNode target : source) {
      ObjectNode item = projected.addObject();
      copyFields(target, item, "displayName", "roles", "reason", "expansion");
    }
    return projected;
  }

  private ArrayNode projectedObservations(JsonNode source) {
    ArrayNode projected = mapper.createArrayNode();
    for (JsonNode observation : source) {
      ObjectNode item = projected.addObject();
      copyFields(
          observation, item, "code", "association", "detail", "displayIdentity", "typeOrigin");
    }
    return projected;
  }

  private void copyFields(JsonNode source, ObjectNode target, String... fields) {
    for (String field : fields) {
      if (source.has(field)) {
        target.set(field, source.path(field).deepCopy());
      }
    }
  }

  private ArrayNode dereference(JsonNode refs, JsonNode dictionary) {
    ArrayNode values = mapper.createArrayNode();
    for (JsonNode ref : refs) {
      JsonNode value = dictionary.get(ref.asText());
      if (value == null) {
        throw new AssertionError(
            "call evidence reference has no dictionary value: " + ref.asText());
      }
      values.add(value.deepCopy());
    }
    return values;
  }

  private ObjectNode inlineCallEvidence(JsonNode model) {
    ObjectNode inline = ((ObjectNode) model).deepCopy();
    JsonNode targetDictionary = model.path("callEvidence").path("targets");
    JsonNode observationDictionary = model.path("callEvidence").path("observations");
    for (JsonNode item : inline.path("callContext")) {
      ObjectNode context = (ObjectNode) item;
      String status = context.path("resolution").asText();
      if (hasExceptionalCallContext(status)) {
        context.set("targets", dereference(context.path("targetRefs"), targetDictionary));
        context.set(
            "observations", dereference(context.path("observationRefs"), observationDictionary));
      }
      context.remove("targetRefs");
      context.remove("observationRefs");
    }
    inline.remove("callEvidence");
    return inline;
  }

  private String canonicalJson(JsonNode node) {
    return new String(json.encodeCanonical(node).copyToByteArray(), StandardCharsets.UTF_8);
  }

  private String localRef(OntologyReadingPacket packet, UnitHandle handle) {
    return packet.units().stream()
        .filter(
            unit -> unit.kind() == handle.kind() && unit.originalId().equals(handle.originalId()))
        .findFirst()
        .orElseThrow()
        .localRef();
  }

  private JsonNode mappingClue(JsonNode mapping, ClueKind kind, String lookupKey) {
    for (JsonNode clue : mapping.path("clues")) {
      if (kind.name().equals(clue.path("kind").asText())
          && lookupKey.equals(clue.path("lookupKey").asText())) {
        return clue;
      }
    }
    throw new AssertionError("missing clue " + kind + ":" + lookupKey);
  }

  private List<String> arrayTexts(JsonNode values) {
    return StreamSupport.stream(values.spliterator(), false).map(JsonNode::asText).toList();
  }

  private OntologyEvidenceCorpus sqlAnalysisCorpus() {
    ObjectNode entry =
        (ObjectNode)
            json.parseCanonical(
                document(FIRST, "/orders/first", "vue:order-page", false).canonicalJson());
    ArrayNode analyses = (ArrayNode) entry.path("persistence").path("sqlAnalyses");
    analyses.add(sqlAnalysis("SELECT id FROM orders WHERE order_id = #{id}"));
    analyses.add(sqlAnalysis("SELECT id FROM orders /* mysql */ WHERE order_id = #{id}"));
    ObjectNode second =
        (ObjectNode)
            json.parseCanonical(
                document(SECOND, "/orders/second", "vue:order-page-second", false).canonicalJson());
    ArrayNode secondAnalyses = (ArrayNode) second.path("persistence").path("sqlAnalyses");
    secondAnalyses.add(sqlAnalysis("SELECT id FROM orders WHERE order_id = #{id}"));
    secondAnalyses.add(sqlAnalysis("SELECT id FROM orders /* mysql */ WHERE order_id = #{id}"));
    return verifiedCorpus(
        new EntryEvidenceReader.EntryDocument(FIRST, json.encodeCanonical(entry)),
        new EntryEvidenceReader.EntryDocument(SECOND, json.encodeCanonical(second)));
  }

  private ObjectNode sqlAnalysis(String analysisCopy) {
    ObjectNode analysis = mapper.createObjectNode();
    analysis.put("statementRef", "mapper:order#find");
    analysis.put("status", "PARSED");
    analysis.put("reason", "AST extracted from saved SQL");
    analysis.put("analysisCopy", analysisCopy);
    ObjectNode ast = analysis.putObject("ast");
    ast.put("kind", "ROOT");
    ast.putNull("value");
    ArrayNode children = ast.putArray("children");
    children.addObject().put("kind", "TABLE").put("value", "orders").putArray("children");
    children.addObject().put("kind", "COLUMN").put("value", "order_id").putArray("children");
    analysis.putArray("transformations");
    return analysis;
  }

  private OntologyEvidenceCorpus frontendRequestCorpus() {
    ObjectNode entry =
        (ObjectNode)
            json.parseCanonical(
                document(FIRST, "/orders/first", "vue:order-page", false).canonicalJson());
    ArrayNode uses = (ArrayNode) entry.path("frontend").path("requestUses");
    ObjectNode use = uses.addObject();
    use.put("resolution", "MATCHED_UNIQUE");
    use.put("reason", "static request wrapper");
    use.putArray("candidateEntryIds").add(FIRST);
    ObjectNode request = use.putObject("request");
    request.put("requestId", "frontend:save-order");
    request.put("pagePath", "src/pages/Order.vue");
    request.put("sourceSha256", "b".repeat(64));
    request.put("instanceKey", "order-page");
    request.set("callRange", sourceRange(30, 4));
    request.put("httpMethod", "POST");
    request.put("rawUrlExpression", "saveOrder(row)");
    request.put("resolvedPath", "/orders/save");
    request.put("requestOrigin", "frontend");
    request.put("baseUrlExpression", "api");
    request.put("baseUrlStaticFallback", "/api");
    request.putArray("argumentBindings").addObject().put("ordinal", 0).put("expression", "row.id");
    ArrayNode wrappers = request.putArray("wrapperPath");
    ObjectNode wrapper = wrappers.addObject();
    wrapper.put("sourcePath", "src/pages/Order.vue");
    wrapper.put("sourceSha256", "a".repeat(64));
    wrapper.set("callRange", sourceRange(24, 5));
    wrapper.set("sourceUnitRange", sourceRange(20, 35));
    wrapper.put("sourceUnitKind", "FUNCTION");
    wrapper.put("fromUnit", "private:order-page");
    wrapper.put("toUnit", "private:http-client");
    return verifiedCorpus(
        new EntryEvidenceReader.EntryDocument(FIRST, json.encodeCanonical(entry)));
  }

  private ObjectNode sourceRange(int startLine, int length) {
    ObjectNode range = mapper.createObjectNode();
    range.put("startLine", startLine);
    range.put("endLine", startLine);
    range.put("startOffsetUtf16", startLine * 10);
    range.put("lengthUtf16", length);
    return range;
  }

  private OntologyReadingPacket.PackedUnit packedUnit(
      OntologyReadingPacket packet, String originalId) {
    return packet.units().stream()
        .filter(unit -> unit.kind() == UnitKind.JAVA_METHOD && unit.originalId().equals(originalId))
        .findFirst()
        .orElseThrow();
  }

  private OntologyEvidenceCorpus crossEntryTargetCorpus() {
    ObjectNode first =
        (ObjectNode)
            json.parseCanonical(
                document(FIRST, "/orders/first", "vue:order-page", false).canonicalJson());
    ObjectNode second =
        (ObjectNode)
            json.parseCanonical(
                document(SECOND, "/orders/second", "vue:order-page-second", false).canonicalJson());
    configureCall(first, SHARED_METHOD, TARGET_METHOD, "customerId");
    ObjectNode secondMethod = (ObjectNode) second.path("java").path("methods").get(0);
    secondMethod.put("methodKey", TARGET_METHOD);
    ((ObjectNode) second.path("entry")).put("methodKey", TARGET_METHOD);
    return verifiedCorpus(
        new EntryEvidenceReader.EntryDocument(FIRST, json.encodeCanonical(first)),
        new EntryEvidenceReader.EntryDocument(SECOND, json.encodeCanonical(second)));
  }

  private OntologyEvidenceCorpus sameEntryCallerTargetCorpus(String argument) {
    ObjectNode first =
        (ObjectNode)
            json.parseCanonical(
                document(FIRST, "/orders/first", "vue:order-page", false).canonicalJson());
    ObjectNode entry = (ObjectNode) first.path("entry");
    entry.put("methodKey", CALLER_METHOD);
    ObjectNode caller = (ObjectNode) first.path("java").path("methods").get(0);
    caller.put("methodKey", CALLER_METHOD);
    caller.put("name", "caller");
    caller.put("signature", "caller(java.lang.String)");
    ObjectNode target = caller.deepCopy();
    target.put("methodKey", TARGET_METHOD);
    target.put("name", "target");
    target.put("signature", "target(java.lang.String)");
    ((ArrayNode) first.path("java").path("methods")).add(target);
    configureCall(first, CALLER_METHOD, TARGET_METHOD, argument);
    return verifiedCorpus(
        new EntryEvidenceReader.EntryDocument(FIRST, json.encodeCanonical(first)));
  }

  private OntologyEvidenceCorpus sameEntryMultipleTargetsCorpus() {
    return verifiedCorpus(
        new EntryEvidenceReader.EntryDocument(
            FIRST, json.encodeCanonical(sameEntryMultipleTargetsEntry())));
  }

  private ObjectNode sameEntryMultipleTargetsEntry() {
    ObjectNode first =
        (ObjectNode)
            json.parseCanonical(
                document(FIRST, "/orders/first", "vue:order-page", false).canonicalJson());
    ObjectNode entry = (ObjectNode) first.path("entry");
    entry.put("methodKey", CALLER_METHOD);
    ObjectNode caller = (ObjectNode) first.path("java").path("methods").get(0);
    caller.put("methodKey", CALLER_METHOD);
    caller.put("name", "caller");
    caller.put("signature", "caller(java.lang.String)");
    ObjectNode target = caller.deepCopy();
    target.put("methodKey", TARGET_METHOD);
    target.put("name", "target");
    target.put("signature", "target(java.lang.String)");
    ObjectNode implementation = caller.deepCopy();
    implementation.put("methodKey", SECOND_TARGET_METHOD);
    implementation.put("name", "targetImplementation");
    implementation.put("signature", "targetImplementation(java.lang.String)");
    ArrayNode methods = (ArrayNode) first.path("java").path("methods");
    methods.add(target);
    methods.add(implementation);
    configureCall(first, CALLER_METHOD, TARGET_METHOD, "customerId");
    ObjectNode call = (ObjectNode) first.path("java").path("calls").get(0);
    ObjectNode secondTarget = ((ArrayNode) call.path("targets")).addObject();
    secondTarget.put("methodKey", SECOND_TARGET_METHOD);
    secondTarget.put("displayName", "targetImplementation");
    secondTarget.putArray("roles").add("DECLARATION");
    secondTarget.putArray("navigationKinds").add("IMPLEMENTATION");
    secondTarget.put("expansion", "BODY_INCLUDED");
    secondTarget.putArray("argumentAssociations").addObject().putArray("actualOrdinals").add(0);
    ObjectNode association = (ObjectNode) secondTarget.path("argumentAssociations").get(0);
    association.put("formalOrdinal", 0);
    association.put("kind", "POSITIONAL");
    return first;
  }

  private OntologyEvidenceCorpus samePhysicalCallVariantsCorpus() {
    ObjectNode first = sameEntryMultipleTargetsEntry();
    ObjectNode second = first.deepCopy();
    second.put("entryId", SECOND);
    ObjectNode third = first.deepCopy();
    third.put("entryId", THIRD);
    makeCandidateCall(second, "candidate-alpha", "candidate-beta");
    makeCandidateCall(third, "candidate-alpha", "candidate-gamma");
    return verifiedCorpus(
        new EntryEvidenceReader.EntryDocument(FIRST, json.encodeCanonical(first)),
        new EntryEvidenceReader.EntryDocument(SECOND, json.encodeCanonical(second)),
        new EntryEvidenceReader.EntryDocument(THIRD, json.encodeCanonical(third)));
  }

  private void makeCandidateCall(ObjectNode entry, String firstCandidate, String secondCandidate) {
    ObjectNode candidateCall = null;
    for (JsonNode call : entry.path("java").path("calls")) {
      if ("call:normal-00".equals(call.path("callKey").asText())) {
        candidateCall = (ObjectNode) call;
        break;
      }
    }
    if (candidateCall == null) {
      throw new AssertionError("fixture candidate call is absent");
    }
    candidateCall.put("resolution", "CANDIDATES");
    candidateCall.put("resolutionDetail", "The saved call has multiple possible targets.");
    ArrayNode targets = candidateCall.putArray("targets");
    for (String displayName : List.of(firstCandidate, secondCandidate)) {
      ObjectNode target = targets.addObject();
      target.putNull("methodKey");
      target.put("displayName", displayName);
      target.putArray("roles").add("DECLARATION");
      target.putArray("navigationKinds").add("CALL_HIERARCHY");
      target.put("expansion", "NOT_EXPANDED");
      target.put("reason", "The saved call remains a candidate for review.");
      target.putArray("argumentAssociations");
    }
    ArrayNode observations = candidateCall.putArray("observations");
    ObjectNode observation = observations.addObject();
    observation.put("code", "CANDIDATES");
    observation.put("operation", "JDT_LANGUAGE_SERVER");
    observation.put("uriKind", "SOURCE");
    observation.set("sourceRange", candidateCall.path("site").deepCopy());
    observation.put("association", "UNCONFIRMED");
    observation.put("detail", "Two saved candidate targets remain unresolved.");
    observation.put("declarationKey", "candidate-target");
    observation.put("declaringTypeKey", "com.example.OrderService");
    observation.put("displayIdentity", "candidate-targets");
    observation.put("typeOrigin", "SOURCE");
  }

  private boolean samePhysicalSite(JsonNode rowSite, JsonNode savedSite) {
    return rowSite.isArray()
        && rowSite.size() == 4
        && rowSite.get(0).asInt() == savedSite.path("startOffsetUtf16").asInt()
        && rowSite.get(1).asInt() == savedSite.path("lengthUtf16").asInt()
        && rowSite.get(2).asInt() == savedSite.path("startLine").asInt()
        && rowSite.get(3).asInt() == savedSite.path("endLine").asInt();
  }

  private List<String> callEvidenceTargetNames(JsonNode model, JsonNode row) {
    List<String> names = new ArrayList<>();
    for (JsonNode ref : row.path("targetRefs")) {
      names.add(
          model
              .path("callEvidence")
              .path("targets")
              .path(ref.asText())
              .path("displayName")
              .asText());
    }
    return List.copyOf(names);
  }

  private void configureCall(
      ObjectNode entry, String callerMethodKey, String targetMethodKey, String argument) {
    ObjectNode call = (ObjectNode) entry.path("java").path("calls").get(0);
    call.put("callerMethodKey", callerMethodKey);
    ArrayNode actualArguments = call.putArray("actualArguments");
    actualArguments.addObject().put("ordinal", 0).put("expression", argument);
    ObjectNode target = (ObjectNode) call.path("targets").get(0);
    target.put("methodKey", targetMethodKey);
    target.putArray("argumentAssociations").addObject().putArray("actualOrdinals").add(0);
    ObjectNode association = (ObjectNode) target.path("argumentAssociations").get(0);
    association.put("formalOrdinal", 0);
    association.put("kind", "POSITIONAL");
  }

  private OntologyEvidenceCorpus verifiedCorpus(EntryEvidenceReader.EntryDocument... entries) {
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            canonicalDirectoryIndex(), bytes("{}"), List.of(entries)));
  }

  private UnitHandle xmlVariant(OntologyEvidenceCorpus corpus, int index) {
    return corpus.entryUnits(FIRST, 0, 50).items().stream()
        .filter(handle -> handle.kind() == UnitKind.XML_STATEMENT)
        .toList()
        .get(index);
  }

  private OntologyEvidenceCorpus corpus(boolean reverse) {
    EntryEvidenceReader.EntryDocument first =
        document(FIRST, "/orders/first", "vue:order-page", reverse);
    EntryEvidenceReader.EntryDocument second =
        document(SECOND, "/orders/second", "vue:order-page-second", reverse);
    // Directory entries must remain entryId UTF-8 ascending; only each entry's unit enumeration
    // changes so this contract exercises alias stability without violating reader framing.
    List<EntryEvidenceReader.EntryDocument> documents = List.of(first, second);
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(canonicalDirectoryIndex(), bytes("{}"), documents));
  }

  private ImmutableBytes canonicalDirectoryIndex() {
    ObjectNode index = mapper.createObjectNode();
    ObjectNode header = index.putObject("header");
    header.putObject("sourceBasis").put("kind", "PREPARED_V1");
    header.put("sourceSnapshotId", "snapshot:fixed");
    header.putObject("sourceInventory");
    return json.encodeCanonical(index);
  }

  private EntryEvidenceReader.EntryDocument document(
      String entryId, String route, String frontendId, boolean reverseUnits) {
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", entryId);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.put("assemblyStatus", "ASSEMBLED");
    ObjectNode http = entry.putObject("entry");
    http.put("method", "POST");
    http.put("route", route);
    http.put("handlerFqn", "com.example.OrderController#save");
    http.put("methodKey", SHARED_METHOD);

    ObjectNode frontend = entry.putObject("frontend");
    ArrayNode units = frontend.putArray("units");
    ObjectNode vue = units.addObject();
    vue.put("sourceUnitId", frontendId);
    vue.put("sourceUnitKind", "FUNCTION");
    vue.put("text", "export function saveOrder(row) { return row.linkId; }");
    frontend.putArray("requestUses");
    frontend.putArray("candidateRequestUses");

    ObjectNode java = entry.putObject("java");
    ArrayNode methods = java.putArray("methods");
    ObjectNode method = methods.addObject();
    method.put("methodKey", SHARED_METHOD);
    method.put("name", "shared");
    method.put("declaringType", "com.example.OrderController");
    method.put("signature", "shared()");
    method.put("returnTypeText", "void");
    method.putArray("parameters");
    ObjectNode methodSource = method.putObject("source");
    methodSource.put("path", "src/OrderController.java");
    methodSource.put("startLine", 10);
    methodSource.put("endLine", 10);
    methodSource.put("startOffsetUtf16", 100);
    methodSource.put("lengthUtf16", 32);
    methodSource.put("text", "public void shared() { return; }");
    method.putNull("enclosingMethodKey");
    method.putArray("modifiers").add("public");
    method.putArray("annotations");
    method.putArray("controls");
    method.putArray("exits");
    List<ObjectNode> callUnits = new java.util.ArrayList<>();
    for (int index = 0; index < 12; index++) {
      String callKey = String.format("call:normal-%02d", index);
      callUnits.add(
          call(callKey, index + 10, "LOCATED", "normal-helper-" + String.format("%02d", index)));
    }
    callUnits.add(call("call:unknown", 40, "UNRESOLVED", "unknown-helper"));
    callUnits.add(call("call:conflict", 41, "NAVIGATION_CONFLICT", "conflicting-helper"));
    callUnits.add(call("call:failure", 42, "QUERY_FAILED", "query-failed-helper"));
    ArrayNode calls = java.putArray("calls");
    if (reverseUnits) {
      for (int index = callUnits.size() - 1; index >= 0; index--) {
        calls.add(callUnits.get(index));
      }
    } else {
      calls.addAll(callUnits);
    }

    ObjectNode persistence = entry.putObject("persistence");
    ArrayNode bindings = persistence.putArray("bindings");
    ObjectNode binding = bindings.addObject();
    binding.put("methodKey", SHARED_METHOD);
    ArrayNode refs = binding.putArray("statementRefs");
    refs.addObject().put("statementRef", "mapper:order#find").putNull("databaseId");
    ObjectNode defaultStatement = mapper.createObjectNode();
    defaultStatement.put("statementRef", "mapper:order#find");
    defaultStatement.putNull("databaseId");
    defaultStatement.put("namespace", "mapper.order");
    defaultStatement.put("statementId", "find");
    defaultStatement.put("statementKind", "SELECT");
    defaultStatement.set("xmlSubtree", xmlSubtree());
    ObjectNode mysqlStatement = mapper.createObjectNode();
    mysqlStatement.put("statementRef", "mapper:order#find");
    mysqlStatement.put("databaseId", "mysql");
    mysqlStatement.put("namespace", "mapper.order");
    mysqlStatement.put("statementId", "find");
    mysqlStatement.put("statementKind", "SELECT");
    mysqlStatement.set("xmlSubtree", xmlSubtree());
    ArrayNode statements = persistence.putArray("statements");
    if (reverseUnits) {
      statements.add(mysqlStatement);
      statements.add(defaultStatement);
    } else {
      statements.add(defaultStatement);
      statements.add(mysqlStatement);
    }
    persistence.putArray("resources");
    persistence.putArray("sqlAnalyses");
    entry.putArray("limitations");
    entry.putArray("sourceRefs");
    return new EntryEvidenceReader.EntryDocument(entryId, json.encodeCanonical(entry));
  }

  private ObjectNode call(String key, int line, String resolution, String helper) {
    ObjectNode call = mapper.createObjectNode();
    call.put("callKey", key);
    call.put("callerMethodKey", SHARED_METHOD);
    call.put("kind", "METHOD");
    ObjectNode site = call.putObject("site");
    site.put("startLine", line);
    site.put("endLine", line);
    site.put("startOffsetUtf16", line * 3);
    site.put("lengthUtf16", 12);
    ObjectNode navigationSite = call.putObject("navigationSite");
    navigationSite.put("startLine", line);
    navigationSite.put("endLine", line);
    navigationSite.put("startOffsetUtf16", line * 3);
    navigationSite.put("lengthUtf16", 12);
    call.put("expression", helper + "()");
    call.putNull("receiverExpression");
    call.putArray("actualArguments");
    call.putArray("enclosingControlIndexes");
    call.put("deferred", false);
    call.put("resolution", resolution);
    if (!"LOCATED".equals(resolution)) {
      call.put("resolutionDetail", resolution + " detail");
    }
    ArrayNode targets = call.putArray("targets");
    if ("LOCATED".equals(resolution)) {
      ObjectNode target = targets.addObject();
      target.put("methodKey", "method:" + helper);
      target.put("displayName", helper);
      target.putArray("roles").add("DECLARATION");
      target.putArray("navigationKinds").add("DEFINITION");
      target.put("expansion", "BODY_INCLUDED");
      target.putArray("argumentAssociations");
    } else if ("NAVIGATION_CONFLICT".equals(resolution) || "QUERY_FAILED".equals(resolution)) {
      ObjectNode target = targets.addObject();
      target.putNull("methodKey");
      target.put("displayName", helper);
      target.putArray("roles").add("DECLARATION");
      target.putArray("navigationKinds").add("CALL_HIERARCHY");
      target.put("expansion", "NOT_EXPANDED");
      target.put("reason", resolution + " candidate requires review");
      target.putArray("argumentAssociations");
    }
    ArrayNode observations = call.putArray("observations");
    ObjectNode observation = observations.addObject();
    observation.put("code", "LOCATED".equals(resolution) ? "CONFIRMED_BINDING" : resolution);
    observation.put(
        "operation", "LOCATED".equals(resolution) ? "JDT_CORE_BINDING" : "JDT_LANGUAGE_SERVER");
    observation.put("uriKind", "SOURCE");
    ObjectNode observationRange = observation.putObject("sourceRange");
    observationRange.put("startLine", line);
    observationRange.put("endLine", line);
    observationRange.put("startOffsetUtf16", line * 3);
    observationRange.put("lengthUtf16", 12);
    observation.put(
        "association",
        "LOCATED".equals(resolution)
            ? "CONFIRMED"
            : "QUERY_FAILED".equals(resolution) ? "FAILED" : "UNCONFIRMED");
    observation.put("declarationKey", "LOCATED".equals(resolution) ? "method:" + helper : helper);
    observation.put("declaringTypeKey", "com.example.OrderService");
    observation.put("typeOrigin", "SOURCE");
    observation.put("displayIdentity", helper);
    observation.put("detail", resolution + " observation");
    return call;
  }

  private ObjectNode xmlSubtree() {
    ObjectNode root = mapper.createObjectNode();
    root.put("kind", "ELEMENT");
    root.put("elementName", "select");
    root.putObject("attributes").put("id", "find");
    root.putNull("content");
    ArrayNode children = root.putArray("children");
    ObjectNode text = children.addObject();
    text.put("kind", "TEXT");
    text.putNull("elementName");
    text.putObject("attributes");
    text.put("content", "#{id}");
    text.putArray("children");
    return root;
  }

  private ImmutableBytes bytes(String value) {
    return ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8));
  }
}
