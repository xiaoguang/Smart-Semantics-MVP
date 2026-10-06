package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.Question;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.ReadingMode;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.Task;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.TaskKind;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader.UnitUse;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Provider-free contracts for the deterministic mechanical LINK material bundle. */
final class OntologyCoherentLinkBundleContractsTest {
  private static final String SNAPSHOT = "coherent-link-bundle-contracts";
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void exposedPacketReferenceCannotChangeFrozenSourceDecisionOrModelInput() {
    Fixture fixture = fixture(2);
    Question question =
        question(fixture, fixture.entryRefs(), task(fixture, List.of(fixture.use(0)), List.of()));
    var result = prepare(fixture, question, 100_000, 500_000);
    var packet = result.packet();
    var inputBefore = packet.modelInput();
    var privateBefore = packet.canonicalInput();
    var decisionBefore = result.decisionDocument();
    var contentBefore = packet.units().get(0).canonicalJson();
    ((com.fasterxml.jackson.databind.node.ObjectNode) packet.units().get(0).content()).removeAll();
    ((com.fasterxml.jackson.databind.node.ObjectNode) result.decision()).removeAll();
    byte[] detachedInput = inputBefore.copyToByteArray();
    detachedInput[0] = 0;
    assertThatThrownBy(() -> packet.units().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(result.packet().modelInput()).isEqualTo(inputBefore);
    assertThat(result.packet().canonicalInput()).isEqualTo(privateBefore);
    assertThat(result.decisionDocument()).isEqualTo(decisionBefore);
    assertThat(result.packet().units().get(0).canonicalJson()).isEqualTo(contentBefore);
  }

  @Test
  void exactAnchorUsesExpandBeyondSeedWithoutTurningSelectionIntoSemanticLinks() {
    Fixture fixture = fixture(5);
    List<String> entries = fixture.entryRefs();
    UnitUse seed = fixture.use(0);
    UnitUse required = fixture.use(4);
    Question question =
        question(fixture, List.of(entries.get(0)), task(fixture, List.of(seed), List.of(required)));

    OntologyCoherentLinkBundle.Result result = prepare(fixture, question, 100_000, 500_000);

    assertThat(result.issueCode()).isNull();
    assertThat(result.packet()).isNotNull();
    assertThat(hasUse(result.decision().path("seedUses"), seed)).isTrue();
    for (int index = 1; index < fixture.bodies().size(); index++) {
      assertThat(hasUse(result.decision().path("derivedUses"), fixture.use(index)))
          .as("the saved K must expand the exact method use in E%d", index + 1)
          .isTrue();
      assertThat(result.decision().path("derivedEntries"))
          .contains(mapper.getNodeFactory().textNode(entries.get(index)));
    }
    assertThat(result.decision().path("requiredButUnread")).isEmpty();

    JsonNode model = json.parseCanonical(result.packet().modelInput());
    List<String> projectedBodies = new ArrayList<>();
    for (JsonNode unit : model.path("units")) {
      JsonNode sourceText = unit.path("content").path("sourceText");
      if (sourceText.isTextual()) {
        projectedBodies.add(sourceText.asText());
      }
    }
    assertThat(projectedBodies).containsExactlyInAnyOrderElementsOf(fixture.bodies());
    assertThat(model.has("objects")).isFalse();
    assertThat(model.has("links")).isFalse();
    assertThat(model.has("definitions")).isFalse();
  }

  @Test
  void decisionAndFrozenPacketOrderingDoNotDependOnInputUseOrder() {
    Fixture fixture = fixture(5);
    List<String> entries = fixture.entryRefs();
    List<UnitUse> seedUses = new ArrayList<>(List.of(fixture.use(0), fixture.use(2)));
    List<UnitUse> requiredUses = new ArrayList<>(List.of(fixture.use(3), fixture.use(4)));
    OntologyCoherentLinkBundle.Result first =
        prepare(
            fixture,
            question(fixture, entries, task(fixture, seedUses, requiredUses)),
            100_000,
            500_000);

    Collections.reverse(seedUses);
    Collections.reverse(requiredUses);
    List<String> reversedEntries = new ArrayList<>(entries);
    Collections.reverse(reversedEntries);
    OntologyCoherentLinkBundle.Result reordered =
        prepare(
            fixture,
            question(fixture, reversedEntries, task(fixture, seedUses, requiredUses)),
            100_000,
            500_000);

    assertThat(reordered.issueCode()).isNull();
    assertThat(reordered.decision()).isEqualTo(first.decision());
    assertThat(reordered.packet().modelInput()).isEqualTo(first.packet().modelInput());
    assertThat(reordered.packet().canonicalInput()).isEqualTo(first.packet().canonicalInput());
  }

  @Test
  void overCapacityRetainsEveryExactUseAndFullSourceCostWithoutReturningATruncatedPacket() {
    Fixture fixture = fixture(8);
    List<String> entries = fixture.entryRefs();
    Question question =
        question(
            fixture, List.of(entries.get(0)), task(fixture, List.of(fixture.use(0)), List.of()));
    int fullSourceBytes =
        fixture.bodies().stream()
            .mapToInt(body -> body.getBytes(StandardCharsets.UTF_8).length)
            .sum();

    OntologyCoherentLinkBundle.Result result = prepare(fixture, question, 1, 500_000);

    assertThat(result.packet()).isNull();
    assertThat(result.issueCode()).isEqualTo("LINK_BUNDLE_TOO_LARGE");
    assertThat(result.decision().path("groups")).isNotEmpty();
    assertThat(result.decision().path("cost").path("sourceBytes").asInt())
        .isEqualTo(fullSourceBytes);
    for (int index = 1; index < fixture.bodies().size(); index++) {
      assertThat(hasUse(result.decision().path("derivedUses"), fixture.use(index)))
          .as("capacity failure must not silently truncate K fan-out at E%d", index + 1)
          .isTrue();
    }
    long groupedSourceBytes = 0;
    for (JsonNode group : result.decision().path("groups")) {
      groupedSourceBytes += group.path("unitBytes").asLong();
    }
    assertThat(groupedSourceBytes).isEqualTo(fullSourceBytes);
  }

  private OntologyCoherentLinkBundle.Result prepare(
      Fixture fixture, Question question, int maxUnitBytes, int maxRequestBytes) {
    return OntologyCoherentLinkBundle.prepare(
        fixture.corpus(), question, question.tasks().get(0), maxUnitBytes, maxRequestBytes);
  }

  private Question question(Fixture fixture, List<String> entryRefs, Task task) {
    return new Question(
        "Q1",
        "Trace this technical method without deciding business meaning",
        entryRefs,
        List.of(fixture.anchorRef()),
        List.of(task));
  }

  private Task task(Fixture fixture, List<UnitUse> uses, List<UnitUse> required) {
    return new Task(
        "T1",
        TaskKind.LINK,
        ReadingMode.TECHNICAL_BUNDLE,
        uses,
        required,
        List.of(fixture.anchorRef()));
  }

  private boolean hasUse(JsonNode uses, UnitUse expected) {
    for (JsonNode use : uses) {
      if (expected.unitRef().equals(use.path("unitRef").asText())
          && expected.entryRef().equals(use.path("entryRef").asText())) {
        return true;
      }
    }
    return false;
  }

  private Fixture fixture(int entryCount) {
    String snapshot = SNAPSHOT + "-" + entryCount;
    OntologyFormalTypedTaskContractsTest reusableFixture =
        new OntologyFormalTypedTaskContractsTest();
    List<String> bodies = new ArrayList<>();
    for (int index = 0; index < entryCount; index++) {
      bodies.add("public void fixture() { use(\"record-" + index + "\"); }");
    }
    OntologyEvidenceCorpus corpus = reusableFixture.corpus(snapshot, bodies);
    List<String> entryRefs = new ArrayList<>();
    List<UnitUse> uses = new ArrayList<>();
    for (int index = 0; index < entryCount; index++) {
      String entryId = reusableFixture.entryId(snapshot, index);
      entryRefs.add(corpus.aliases().entryRef(entryId));
      UnitHandle handle = new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:fixture");
      uses.add(new UnitUse(corpus.aliases().unitRef(handle), corpus.aliases().entryRef(entryId)));
    }
    String anchorRef = corpus.aliases().clueRef(ClueKind.METHOD, "method:fixture");
    return new Fixture(
        corpus, List.copyOf(bodies), List.copyOf(entryRefs), List.copyOf(uses), anchorRef);
  }

  private record Fixture(
      OntologyEvidenceCorpus corpus,
      List<String> bodies,
      List<String> entryRefs,
      List<UnitUse> uses,
      String anchorRef) {
    private UnitUse use(int index) {
      return uses.get(index);
    }
  }
}
