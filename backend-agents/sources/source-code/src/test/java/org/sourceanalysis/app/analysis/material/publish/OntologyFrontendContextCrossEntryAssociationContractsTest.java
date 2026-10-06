package org.sourceanalysis.app.analysis.material.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet.Entry;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet.Frontend;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet.FrontendCoverage;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.analysis.ontology.OntologyReadingPacket;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Parked direct regression for one R1 page context linked through two valid R4 entries. */
final class OntologyFrontendContextCrossEntryAssociationContractsTest {
  private static final int MAX_UNIT_BYTES = 8 * 1024 * 1024;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SECOND_ENTRY_ID = "entry:" + "2".repeat(64);
  private static final String POST_ROUTE = "/canvas/record/add";
  private static final String PUT_ROUTE = "/canvas/record/edit";

  @Test
  void sharedSavedContextKeepsEntryScopedAssociationsAndOneCopyOfEachBody() throws Exception {
    try (FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture fixture =
        FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture.complete()) {
      EntryEvidenceSet twoEntrySet = withPutRequestOwnedBySecondEntry(fixture.v2Set());
      AnalysisStepPublicationReference r4 = fixture.publishR4V2(twoEntrySet);
      EntryEvidenceReader reader = new EntryEvidenceReader(fixture.modules(), fixture.steps());
      OntologyEvidenceCorpus corpus = OntologyEvidenceCorpus.open(reader, r4);

      UnitHandle postEntryContext =
          new UnitHandle(fixture.entryId(), UnitKind.FRONTEND_PAGE_CONTEXT, "context:canvas");
      UnitHandle putEntryContext =
          new UnitHandle(SECOND_ENTRY_ID, UnitKind.FRONTEND_PAGE_CONTEXT, "context:canvas");
      OntologyReadingPacket forward =
          OntologyReadingPacket.formal(
              corpus, List.of(postEntryContext, putEntryContext), MAX_UNIT_BYTES);
      OntologyReadingPacket reverse =
          OntologyReadingPacket.formal(
              corpus, List.of(putEntryContext, postEntryContext), MAX_UNIT_BYTES);

      assertThat(forward.canonicalInput()).isEqualTo(reverse.canonicalInput());
      assertThat(forward.modelInput()).isEqualTo(reverse.modelInput());
      assertThat(forward.packetId()).isEqualTo(reverse.packetId());
      assertThat(forward.cost()).isEqualTo(reverse.cost());

      List<OntologyReadingPacket.PackedUnit> contextUnits =
          forward.units().stream()
              .filter(unit -> unit.kind() == UnitKind.FRONTEND_PAGE_CONTEXT)
              .toList();
      assertThat(contextUnits).hasSize(2);
      assertThat(contextUnits)
          .extracting(OntologyReadingPacket.PackedUnit::originalId)
          .containsExactly("context:canvas", "context:canvas");
      assertThat(contextUnits.stream().map(OntologyReadingPacket.PackedUnit::localRef).distinct())
          .hasSize(2);
      assertThat(contextUnits)
          .extracting(OntologyReadingPacket.PackedUnit::entryUses)
          .containsExactlyInAnyOrder(List.of(fixture.entryId()), List.of(SECOND_ENTRY_ID));

      JsonNode privatePacket = JSON.readTree(utf8(forward.canonicalInput()));
      Map<String, String> corpusUnitRefByLocalRef = new HashMap<>();
      for (JsonNode item : privatePacket.path("units")) {
        corpusUnitRefByLocalRef.put(
            item.path("localRef").asText(), item.path("evidenceUnitRef").asText());
      }
      assertThat(
              contextUnits.stream()
                  .map(unit -> corpusUnitRefByLocalRef.get(unit.localRef()))
                  .distinct())
          .hasSize(1)
          .allSatisfy(ref -> assertThat(ref).matches("U[1-9][0-9]*"));

      List<OntologyReadingPacket.PackedUnit> choiceBodies =
          forward.units().stream()
              .filter(unit -> unit.kind() == UnitKind.FRONTEND_UNIT)
              .filter(unit -> "unit:choice-panel".equals(unit.originalId()))
              .toList();
      assertThat(choiceBodies)
          .singleElement()
          .satisfies(
              body -> {
                assertThat(body.entryUses()).containsExactly(fixture.entryId(), SECOND_ENTRY_ID);
                assertThat(body.content())
                    .isEqualTo(
                        corpus
                            .read(fixture.entryId(), UnitKind.FRONTEND_UNIT, "unit:choice-panel")
                            .content());
              });

      List<String> expectedSourceUnitIds =
          fixture.pageContexts().get(0).sourceUnits().stream()
              .map(
                  contextSource ->
                      twoEntrySet.entries().get(0).frontend().units().stream()
                          .filter(unit -> unit.path().equals(contextSource.sourcePath()))
                          .filter(unit -> unit.sourceSha256().equals(contextSource.sourceSha256()))
                          .filter(
                              unit ->
                                  unit.sourceUnitRange().equals(contextSource.sourceUnitRange()))
                          .filter(unit -> unit.sourceUnitKind() == contextSource.sourceUnitKind())
                          .map(unit -> unit.sourceUnitId())
                          .findFirst()
                          .orElseThrow())
              .sorted()
              .toList();
      assertThat(expectedSourceUnitIds).hasSize(5);
      assertThat(fixture.pageContexts().get(0).requestIds())
          .containsExactly("request:canvas-post", "request:canvas-put");
      Set<String> expectedSourceRefs = new java.util.LinkedHashSet<>();
      for (String sourceUnitId : expectedSourceUnitIds) {
        List<OntologyReadingPacket.PackedUnit> bodies =
            forward.units().stream()
                .filter(unit -> unit.kind() == UnitKind.FRONTEND_UNIT)
                .filter(unit -> sourceUnitId.equals(unit.originalId()))
                .toList();
        assertThat(bodies)
            .singleElement()
            .satisfies(
                body -> {
                  assertThat(body.entryUses()).containsExactly(fixture.entryId(), SECOND_ENTRY_ID);
                  assertThat(body.content())
                      .isEqualTo(
                          corpus
                              .read(fixture.entryId(), UnitKind.FRONTEND_UNIT, sourceUnitId)
                              .content());
                  expectedSourceRefs.add(body.localRef());
                });
      }

      JsonNode model = JSON.readTree(utf8(forward.modelInput()));
      Map<String, String> routeByEntryRef = new LinkedHashMap<>();
      for (JsonNode entry : model.path("entryContexts")) {
        routeByEntryRef.put(entry.path("entryRef").asText(), entry.path("route").asText());
      }
      List<String> bothEntryRefs =
          routeByEntryRef.entrySet().stream()
              .filter(
                  entry ->
                      POST_ROUTE.equals(entry.getValue()) || PUT_ROUTE.equals(entry.getValue()))
              .map(Map.Entry::getKey)
              .sorted()
              .toList();
      Map<String, JsonNode> contextByRoute = new LinkedHashMap<>();
      for (JsonNode unit : model.path("units")) {
        if (!UnitKind.FRONTEND_PAGE_CONTEXT.name().equals(unit.path("kind").asText())) {
          continue;
        }
        assertThat(unit.path("entryUses").size()).isEqualTo(1);
        String route = routeByEntryRef.get(unit.path("entryUses").get(0).asText());
        contextByRoute.put(route, unit);
      }
      assertThat(contextByRoute.keySet()).containsExactlyInAnyOrder(POST_ROUTE, PUT_ROUTE);
      assertContextAssociation(
          contextByRoute.get(POST_ROUTE), "POST", "MATCHED", "PUT", "COVERAGE_ONLY");
      assertContextAssociation(
          contextByRoute.get(PUT_ROUTE), "PUT", "MATCHED", "POST", "COVERAGE_ONLY");

      Map<String, JsonNode> modelUnitsByRef = new HashMap<>();
      for (JsonNode unit : model.path("units")) {
        modelUnitsByRef.put(unit.path("ref").asText(), unit);
      }
      for (JsonNode contextUnit : contextByRoute.values()) {
        Set<String> sourceRefs =
            iterable(contextUnit.path("content").path("sourceUnits")).stream()
                .map(source -> source.path("sourceRef").asText())
                .collect(Collectors.toSet());
        assertThat(sourceRefs).containsExactlyInAnyOrderElementsOf(expectedSourceRefs);
        for (String sourceRef : sourceRefs) {
          JsonNode source = modelUnitsByRef.get(sourceRef);
          assertThat(source).isNotNull();
          assertThat(source.path("kind").asText()).isEqualTo(UnitKind.FRONTEND_UNIT.name());
          assertThat(iterable(source.path("entryUses")).stream().map(JsonNode::asText).toList())
              .containsExactlyElementsOf(bothEntryRefs);
        }
      }

      assertThat(forward.cost().fullSourceBytes())
          .isEqualTo(forward.units().stream().mapToInt(unit -> unit.canonicalJson().size()).sum());
      assertThat(forward.cost().modelInputBytes()).isEqualTo(forward.modelInput().size());
    }
  }

  private static void assertContextAssociation(
      JsonNode contextUnit,
      String matchedMethod,
      String matchedStatus,
      String coverageOnlyMethod,
      String coverageOnlyStatus) {
    assertThat(contextUnit).isNotNull();
    Map<String, JsonNode> associationsByMethod = new LinkedHashMap<>();
    for (JsonNode association : contextUnit.path("content").path("requestAssociations")) {
      associationsByMethod.put(
          association.path("request").path("httpMethod").asText(), association);
    }
    assertThat(associationsByMethod.keySet()).containsExactlyInAnyOrder("POST", "PUT");
    assertThat(associationsByMethod.get(matchedMethod).path("associationStatus").asText())
        .isEqualTo(matchedStatus);
    assertThat(associationsByMethod.get(coverageOnlyMethod).path("associationStatus").asText())
        .isEqualTo(coverageOnlyStatus);

    Set<String> associationRefs =
        associationsByMethod.values().stream()
            .map(association -> association.path("requestRef").asText())
            .collect(Collectors.toSet());
    Set<String> conditionRefs =
        iterable(contextUnit.path("content").path("requestConditions")).stream()
            .map(condition -> condition.path("requestRef").asText())
            .collect(Collectors.toSet());
    assertThat(conditionRefs).containsExactlyInAnyOrderElementsOf(associationRefs);
    assertThat(associationRefs).hasSize(2);
    assertThat(conditionRefs).hasSize(2);
  }

  private static EntryEvidenceSet withPutRequestOwnedBySecondEntry(EntryEvidenceSet set) {
    Entry original = set.entries().get(0);
    FrontendCoverage putCoverage =
        set.frontendCoverage().stream()
            .filter(coverage -> "request:canvas-put".equals(coverage.requestId()))
            .findFirst()
            .orElseThrow();
    HttpEntryPoint originalEntry = original.entry();
    HttpEntryPoint putEntry =
        new HttpEntryPoint(
            org.sourceanalysis.app.artifact.ArtifactId.parse(SECOND_ENTRY_ID),
            originalEntry.kind(),
            originalEntry.protocol(),
            putCoverage.request().httpMethod(),
            putCoverage.request().resolvedPath(),
            originalEntry.routeParts(),
            originalEntry.handlerFqn(),
            originalEntry.methodKey(),
            originalEntry.methodRange(),
            originalEntry.parameterNames(),
            originalEntry.routeSourceExcerpts());
    List<String> sharedSourceUnitIds = original.frontend().requestUses().get(0).sourceUnitIds();
    Frontend putFrontend =
        new Frontend(
            List.of(
                new EntryEvidenceSet.RequestUse(
                    putCoverage.request(),
                    FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE,
                    List.of(SECOND_ENTRY_ID),
                    sharedSourceUnitIds,
                    null)),
            List.of(),
            original.frontend().units());
    Entry secondEntry =
        new Entry(
            SECOND_ENTRY_ID,
            putEntry,
            original.assemblyStatus(),
            original.coverage(),
            putFrontend,
            original.java(),
            original.persistence(),
            original.sourceRefs(),
            original.limitations());

    List<FrontendCoverage> coverage = new ArrayList<>();
    for (FrontendCoverage item : set.frontendCoverage()) {
      if (item.requestId().equals(putCoverage.requestId())) {
        coverage.add(
            new FrontendCoverage(
                item.requestId(),
                item.request(),
                FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE,
                List.of(SECOND_ENTRY_ID),
                List.of(SECOND_ENTRY_ID),
                List.of(),
                null));
      } else {
        coverage.add(item);
      }
    }
    return new EntryEvidenceSet(set.header(), List.of(original, secondEntry), coverage);
  }

  private static List<JsonNode> iterable(JsonNode node) {
    List<JsonNode> values = new ArrayList<>();
    node.forEach(values::add);
    return List.copyOf(values);
  }

  private static String utf8(ImmutableBytes bytes) {
    return new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
  }
}
