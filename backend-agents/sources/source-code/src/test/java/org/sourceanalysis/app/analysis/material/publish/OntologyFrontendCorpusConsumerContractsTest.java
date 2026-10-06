package org.sourceanalysis.app.analysis.material.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
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

/** Parked direct consumer contracts for saved R4 frontend page contexts. */
final class OntologyFrontendCorpusConsumerContractsTest {
  private static final int MAX_UNIT_BYTES = 8 * 1024 * 1024;
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void selectedContextPacketExpandsExactBodiesDeduplicatesSharedBodyAndReopensDeterministically()
      throws Exception {
    try (FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture fixture =
        FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture.complete()) {
      EntryEvidenceSet set = fixture.v2SetWithSharedPageBodyInstances();
      AnalysisStepPublicationReference r4 = fixture.publishR4V2(set);
      EntryEvidenceReader reader = new EntryEvidenceReader(fixture.modules(), fixture.steps());
      OntologyEvidenceCorpus corpus = OntologyEvidenceCorpus.open(reader, r4);

      UnitHandle canvas =
          new UnitHandle(fixture.entryId(), UnitKind.FRONTEND_PAGE_CONTEXT, "context:canvas");
      UnitHandle canvasCopy =
          new UnitHandle(fixture.entryId(), UnitKind.FRONTEND_PAGE_CONTEXT, "context:canvas-copy");
      OntologyReadingPacket packet =
          OntologyReadingPacket.formal(corpus, List.of(canvas, canvasCopy), MAX_UNIT_BYTES);

      List<OntologyReadingPacket.PackedUnit> contexts =
          packet.units().stream()
              .filter(unit -> unit.kind() == UnitKind.FRONTEND_PAGE_CONTEXT)
              .toList();
      assertThat(contexts)
          .extracting(OntologyReadingPacket.PackedUnit::originalId)
          .containsExactly("context:canvas", "context:canvas-copy");
      assertThat(contexts)
          .extracting(unit -> unit.content().path("instanceKey").asText())
          .containsExactly("page:canvas", "page:canvas-copy");

      List<OntologyReadingPacket.PackedUnit> sourceUnits =
          packet.units().stream().filter(unit -> unit.kind() == UnitKind.FRONTEND_UNIT).toList();
      for (String sourceUnitId :
          List.of("unit:canvas-page", "unit:choice-panel", "unit:canvas-mixin")) {
        JsonNode exactSavedBody =
            corpus.read(fixture.entryId(), UnitKind.FRONTEND_UNIT, sourceUnitId).content();
        assertThat(
                sourceUnits.stream()
                    .filter(unit -> sourceUnitId.equals(unit.originalId()))
                    .toList())
            .singleElement()
            .satisfies(unit -> assertThat(unit.content()).isEqualTo(exactSavedBody));
      }

      JsonNode savedSharedBody =
          corpus.read(fixture.entryId(), UnitKind.FRONTEND_UNIT, "unit:choice-panel").content();
      assertThat(
              sourceUnits.stream().filter(unit -> unit.content().equals(savedSharedBody)).count())
          .isEqualTo(1);
      String modelInput = utf8(packet.modelInput());
      assertThat(modelInput)
          .contains(
              "this.selected = second",
              "this.model.id",
              "httpAction(url, formData, method)",
              "page:canvas",
              "page:canvas-copy")
          .doesNotContain("context:canvas", "context:canvas-copy", "unit:choice-panel");
      assertThat(packet.units())
          .allSatisfy(unit -> assertThat(unit.localRef()).matches("S[1-9][0-9]*"));
      Set<String> localRefs =
          packet.units().stream()
              .map(OntologyReadingPacket.PackedUnit::localRef)
              .collect(Collectors.toSet());
      assertThat(countLocalReferenceOccurrences(JSON.readTree(modelInput), localRefs))
          .isGreaterThan(packet.units().size());
      assertThat(packet.cost().fullSourceBytes())
          .isGreaterThan(
              corpus
                  .read(fixture.entryId(), UnitKind.FRONTEND_PAGE_CONTEXT, "context:canvas")
                  .canonicalJson()
                  .size());
      assertThat(packet.cost().fullSourceBytes())
          .isEqualTo(packet.units().stream().mapToInt(unit -> unit.canonicalJson().size()).sum());
      assertThat(packet.cost().modelInputBytes()).isEqualTo(packet.modelInput().size());
      assertThat(packet.cost().privateInputBytes()).isEqualTo(packet.canonicalInput().size());

      OntologyEvidenceCorpus reopenedCorpus = OntologyEvidenceCorpus.open(reader, r4);
      OntologyReadingPacket reopened =
          OntologyReadingPacket.formal(reopenedCorpus, List.of(canvas, canvasCopy), MAX_UNIT_BYTES);
      assertThat(reopened.sourceIdentity()).isEqualTo(packet.sourceIdentity());
      assertThat(reopened.packetId()).isEqualTo(packet.packetId());
      assertThat(reopened.canonicalInput()).isEqualTo(packet.canonicalInput());
      assertThat(reopened.modelInput()).isEqualTo(packet.modelInput());
      assertThat(reopened.cost()).isEqualTo(packet.cost());
    }
  }

  @Test
  void wrongEntryAndMissingSameEntryContextBodyAreRejected() {
    try (FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture fixture =
        FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture.complete()) {
      AnalysisStepPublicationReference r4 = fixture.publishR4V2(fixture.v2Set());
      EntryEvidenceReader reader = new EntryEvidenceReader(fixture.modules(), fixture.steps());
      OntologyEvidenceCorpus corpus = OntologyEvidenceCorpus.open(reader, r4);

      assertThatThrownBy(
              () ->
                  OntologyReadingPacket.formal(
                      corpus,
                      List.of(
                          new UnitHandle(
                              "entry:" + "9".repeat(64),
                              UnitKind.FRONTEND_PAGE_CONTEXT,
                              "context:canvas")),
                      MAX_UNIT_BYTES))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ONTOLOGY_ALIAS_REFERENCE_INVALID");

      EntryEvidenceSet missingBody = withoutSourceUnit(fixture.v2Set(), "unit:choice-panel");
      assertThatThrownBy(() -> fixture.publishR4V2(missingBody))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ENTRY_EVIDENCE_PUBLICATION_INVALID");
    }
  }

  @Test
  void historicalR4V1StillOpensWithItsOriginalFrontendUnitsAndNoSyntheticContexts()
      throws Exception {
    try (FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture fixture =
        FrontendIndexAndEntryEvidenceV2ContractsTest.FrontendTask3Fixture.complete()) {
      AnalysisStepPublicationReference historical =
          new EntryEvidencePublisher(fixture.modules(), fixture.steps(), fixture.sourceSteps())
              .publishTechnicalV3(
                  fixture.destinationRun(),
                  fixture.source(),
                  fixture.sourceBasis(),
                  fixture.discovery(),
                  fixture.navigation(),
                  fixture.persistence(),
                  fixture.historicalFrontendPublication(),
                  fixture.frontendControls(),
                  fixture.backendControls(),
                  fixture.persistenceControls(),
                  fixture.r4Controls(),
                  fixture.v1Set());
      EntryEvidenceReader reader = new EntryEvidenceReader(fixture.modules(), fixture.steps());
      OntologyEvidenceCorpus corpus = OntologyEvidenceCorpus.open(reader, historical);

      List<UnitHandle> units = corpus.entryUnits(fixture.entryId(), 0, 100).items();
      assertThat(units).noneMatch(unit -> "FRONTEND_PAGE_CONTEXT".equals(unit.kind().name()));
      UnitHandle oldFrontendSource =
          units.stream()
              .filter(unit -> unit.kind() == UnitKind.FRONTEND_UNIT)
              .filter(unit -> "unit:canvas-page".equals(unit.originalId()))
              .findFirst()
              .orElseThrow();
      OntologyReadingPacket packet =
          OntologyReadingPacket.formal(corpus, List.of(oldFrontendSource), MAX_UNIT_BYTES);
      assertThat(packet.units())
          .singleElement()
          .satisfies(
              unit ->
                  assertThat(unit.content().path("text").asText())
                      .contains("@chosen=\"onChosen\""));
    }
  }

  private static EntryEvidenceSet withoutSourceUnit(EntryEvidenceSet set, String sourceUnitId) {
    Entry original = set.entries().get(0);
    Frontend frontend = original.frontend();
    List<org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits.Unit> entryUnits =
        frontend.units().stream()
            .filter(unit -> !sourceUnitId.equals(unit.sourceUnitId()))
            .toList();
    Frontend incompleteFrontend =
        new Frontend(
            frontend.requestUses(),
            frontend.candidateRequestUses(),
            entryUnits,
            frontend.pageContexts());
    Entry incompleteEntry =
        new Entry(
            original.entryId(),
            original.entry(),
            original.assemblyStatus(),
            original.coverage(),
            incompleteFrontend,
            original.java(),
            original.persistence(),
            original.sourceRefs(),
            original.limitations());
    List<FrontendCoverage> incompleteCoverage =
        set.frontendCoverage().stream()
            .map(
                coverage ->
                    new FrontendCoverage(
                        coverage.requestId(),
                        coverage.request(),
                        coverage.resolution(),
                        coverage.entryIds(),
                        coverage.includedEntryIds(),
                        coverage.units().stream()
                            .filter(unit -> !sourceUnitId.equals(unit.sourceUnitId()))
                            .toList(),
                        coverage.pageContexts(),
                        coverage.reason()))
            .toList();
    return new EntryEvidenceSet(set.header(), List.of(incompleteEntry), incompleteCoverage);
  }

  private static String utf8(ImmutableBytes bytes) {
    return new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
  }

  private static int countLocalReferenceOccurrences(JsonNode node, Set<String> localRefs) {
    if (node.isTextual()) {
      return localRefs.contains(node.asText()) ? 1 : 0;
    }
    int count = 0;
    if (node.isContainerNode()) {
      for (JsonNode child : node) {
        count += countLocalReferenceOccurrences(child, localRefs);
      }
    }
    return count;
  }
}
