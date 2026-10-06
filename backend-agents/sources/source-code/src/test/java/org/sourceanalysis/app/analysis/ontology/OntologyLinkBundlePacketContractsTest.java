package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyLinkBundlePacketContractsTest {
  private static final String ENTRY = "entry:" + "a".repeat(64);
  private static final String BODY = "void save() { store.save(sourceKey); }";
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void newPacketBudgetsFullBodyRatherThanPrivateConvenienceMetadata() {
    OntologyEvidenceCorpus corpus = corpus();
    List<UnitHandle> uses = List.of(new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save"));
    assertThatThrownBy(() -> OntologyReadingPacket.formalV5(corpus, uses, 100))
        .hasMessage("ONTOLOGY_UNIT_TOO_LARGE");

    OntologyReadingPacket packet = OntologyReadingPacket.formalV6(corpus, uses, 100, decision());

    JsonNode model = json.parseCanonical(packet.modelInput());
    assertThat(model.path("schemaVersion").asText()).isEqualTo("ontology-model-reading-v6");
    assertThat(model.path("units").get(0).path("content").path("sourceText").asText())
        .isEqualTo(BODY);
    assertThat(model.toString()).doesNotContain("private-only-metadata");
    assertThat(json.parseCanonical(packet.canonicalInput()).path("bundleDecision"))
        .isEqualTo(decision());
    assertThat(packet.resolve("S1").content().path("signature").asText())
        .contains("private-only-metadata");
  }

  @Test
  void incompleteOrUnknownBundleDecisionIsRejectedBeforeProjection() {
    List<UnitHandle> uses = List.of(new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save"));
    ObjectNode missing = decision();
    missing.remove("unreadCandidates");
    assertThatThrownBy(() -> OntologyReadingPacket.formalV6(corpus(), uses, 100, missing))
        .hasMessage("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
    ObjectNode unknown = decision();
    unknown.put("silentlyExpanded", true);
    assertThatThrownBy(() -> OntologyReadingPacket.formalV6(corpus(), uses, 100, unknown))
        .hasMessage("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
    ObjectNode origin = decision();
    origin.put("selectionOrigin", "GUESSED");
    assertThatThrownBy(() -> OntologyReadingPacket.formalV6(corpus(), uses, 100, origin))
        .hasMessage("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
  }

  @Test
  void malformedInnerBundleGroupsCannotAcquireAFrozenIdentity() {
    List<UnitHandle> uses = List.of(new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save"));
    ObjectNode invalid = decision();
    invalid
        .withArray("groups")
        .addObject()
        .put("groupRef", "G1")
        .put("matchKind", "BUSINESS_CONFIRMED")
        .put("outcome", "INCLUDED");
    assertThatThrownBy(() -> OntologyReadingPacket.formalV6(corpus(), uses, 100, invalid))
        .hasMessage("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
    ObjectNode negativeCost = decision();
    ((ObjectNode) negativeCost.path("cost")).put("projectionBytes", -1);
    assertThatThrownBy(() -> OntologyReadingPacket.formalV6(corpus(), uses, 100, negativeCost))
        .hasMessage("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
  }

  @Test
  void oversizedBodyIsRejectedWithoutTruncation() {
    assertThatThrownBy(
            () ->
                OntologyReadingPacket.formalV6(
                    corpus(),
                    List.of(new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save")),
                    1,
                    decision()))
        .hasMessage("ONTOLOGY_UNIT_TOO_LARGE");
  }

  @Test
  void changedBundleDecisionChangesIdentityWithoutChangingOriginalBody() {
    List<UnitHandle> uses = List.of(new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save"));
    ObjectNode changed = decision();
    changed.put("selectionOrigin", "MODEL");
    OntologyReadingPacket first = OntologyReadingPacket.formalV6(corpus(), uses, 100, decision());
    OntologyReadingPacket second = OntologyReadingPacket.formalV6(corpus(), uses, 100, changed);
    assertThat(first.packetId()).isNotEqualTo(second.packetId());
    assertThat(first.resolve("S1").canonicalJson()).isEqualTo(second.resolve("S1").canonicalJson());
  }

  @Test
  void restoringFrozenV6RetainsDecisionAndBodyWithoutRunningSelectionAgain() {
    List<UnitHandle> uses = List.of(new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save"));
    OntologyReadingPacket frozen = OntologyReadingPacket.formalV6(corpus(), uses, 100, decision());
    OntologyReadingPacket restored =
        OntologyReadingPacket.restoreFormalV6(
            corpus(),
            uses,
            100,
            json.parseCanonical(frozen.canonicalInput()).path("bundleDecision"));
    assertThat(restored.canonicalInput()).isEqualTo(frozen.canonicalInput());
    assertThat(restored.modelInput()).isEqualTo(frozen.modelInput());
    assertThat(restored.packetId()).isEqualTo(frozen.packetId());
  }

  @Test
  void preparedSourceReferenceBudgetsItsActualTopLevelBody() {
    OntologyEvidenceCorpus corpus = corpus(true);
    List<UnitHandle> uses =
        List.of(new UnitHandle(ENTRY, UnitKind.SOURCE_REFERENCE, "source:helper"));
    assertThatThrownBy(() -> OntologyReadingPacket.formalV6(corpus, uses, 20, decision()))
        .hasMessage("ONTOLOGY_UNIT_TOO_LARGE");
    OntologyReadingPacket packet = OntologyReadingPacket.formalV6(corpus, uses, 1000, decision());
    assertThat(packet.cost().fullSourceBytes()).isEqualTo("helper body".repeat(10).length());
  }

  @Test
  void unreadCandidatesAreVisibleWithoutBeingPromotedToReadSourceCitations() {
    OntologyEvidenceCorpus corpus = corpus(true);
    UnitHandle read = new UnitHandle(ENTRY, UnitKind.SOURCE_REFERENCE, "source:helper");
    UnitHandle unread = new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save");
    ObjectNode selection = decision();
    selection
        .withArray("unreadCandidates")
        .addObject()
        .put("unitRef", corpus.aliases().unitRef(unread))
        .put("entryRef", corpus.aliases().entryRef(ENTRY))
        .put("matchKind", "LEXICAL_MATCH")
        .put("reason", "Candidate has not been read.");
    OntologyReadingPacket packet =
        OntologyReadingPacket.formalV6(corpus, List.of(read), 1000, selection);
    JsonNode model = json.parseCanonical(packet.modelInput());
    assertThat(model.path("unreadCandidates")).isEqualTo(selection.path("unreadCandidates"));
    assertThat(model.path("requiredButUnread")).isEqualTo(selection.path("requiredButUnread"));
    assertThat(packet.units()).hasSize(1);
    assertThat(model.path("units").get(0).path("kind").asText()).isEqualTo("SOURCE_REFERENCE");
  }

  private ObjectNode decision() {
    ObjectNode value = mapper.createObjectNode();
    value.put("ruleVersion", "link-bundle-rule-v1");
    value.put("anchorRef", "K1");
    value.put("selectionOrigin", "EXPLICIT");
    value.putArray("seedUses");
    value.putArray("derivedUses");
    value.putArray("derivedEntries");
    value.putArray("groups");
    value.putArray("requiredButUnread");
    value.putArray("unreadCandidates");
    value.putObject("cost");
    return value;
  }

  private OntologyEvidenceCorpus corpus() {
    return corpus(false);
  }

  private OntologyEvidenceCorpus corpus(boolean preparedReference) {
    ObjectNode header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", ENTRY);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putObject("entry").put("method", "POST").put("route", "/records");
    ObjectNode method = entry.putObject("java").putArray("methods").addObject();
    method.put("methodKey", "method:save");
    method.put("name", "save");
    method.put("signature", "private-only-metadata".repeat(200));
    method.putObject("source").put("text", BODY);
    ((ObjectNode) entry.path("java")).putArray("calls");
    entry.putObject("frontend").putArray("units");
    entry.putObject("persistence").putArray("statements");
    entry.putArray("sourceRefs");
    if (preparedReference) {
      ObjectNode reference =
          ((com.fasterxml.jackson.databind.node.ArrayNode) entry.path("sourceRefs")).addObject();
      reference.put("reference", "source:helper");
      reference.put("sourceText", "helper body".repeat(10));
      reference.putObject("preparedBody").put("kind", "FULL_FILE").putNull("range");
    }
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            json.encodeCanonical(header),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(new EntryEvidenceReader.EntryDocument(ENTRY, json.encodeCanonical(entry)))));
  }
}
