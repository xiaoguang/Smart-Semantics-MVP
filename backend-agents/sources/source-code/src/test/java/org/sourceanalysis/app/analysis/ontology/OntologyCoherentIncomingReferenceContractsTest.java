package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyCoherentIncomingReferenceContractsTest {
  @Test
  void confirmedCallerOfAnchorIsReadButItsOtherHelpersAreNotRecursivelyExpanded() {
    var mapper = new ObjectMapper();
    var json = new CanonicalJsonCodec();
    String entryId = "entry:" + "e".repeat(64);
    var header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    var entry = mapper.createObjectNode();
    entry.put("entryId", entryId).put("assemblyStatus", "ASSEMBLED");
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.putObject("entry").put("method", "POST").put("route", "/records");
    var java = entry.putObject("java");
    var methods = java.putArray("methods");
    var accessor = methods.addObject().put("methodKey", "method:origin").put("name", "origin");
    accessor.putObject("source").put("text", "String origin() { return originKey; }");
    accessor.putArray("exits").addObject().put("kind", "RETURN").put("expression", "originKey");
    methods
        .addObject()
        .put("methodKey", "method:save")
        .put("name", "save")
        .putObject("source")
        .put("text", "void save() { row.origin(); helper(); }");
    methods
        .addObject()
        .put("methodKey", "method:helper")
        .put("name", "helper")
        .putObject("source")
        .put("text", "void helper() { unrelated(); }");
    methods
        .addObject()
        .put("methodKey", "method:conflict")
        .put("name", "conflict")
        .putObject("source")
        .put("text", "void conflict() { wrong(); }");
    var calls = java.putArray("calls");
    for (String caller : List.of("save", "conflict")) {
      var call =
          calls
              .addObject()
              .put("callKey", "call:" + caller)
              .put("callerMethodKey", "method:" + caller)
              .put("resolution", caller.equals("save") ? "LOCATED" : "NAVIGATION_CONFLICT");
      call.putArray("targets").addObject().put("methodKey", "method:origin");
    }
    calls
        .addObject()
        .put("callKey", "call:helper")
        .put("callerMethodKey", "method:save")
        .put("resolution", "LOCATED")
        .putArray("targets")
        .addObject()
        .put("methodKey", "method:helper");
    for (var call : calls) {
      ((com.fasterxml.jackson.databind.node.ObjectNode) call)
          .putObject("site")
          .put("startOffsetUtf16", 0)
          .put("lengthUtf16", 1)
          .put("startLine", 1)
          .put("endLine", 1);
    }
    entry.putObject("frontend").putArray("units");
    entry.putObject("persistence").putArray("statements");
    entry.putArray("sourceRefs");
    var corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            new EntryEvidenceReader.Directory(
                json.encodeCanonical(header),
                ImmutableBytes.copyOf(new byte[0]),
                List.of(
                    new EntryEvidenceReader.EntryDocument(entryId, json.encodeCanonical(entry)))));
    String anchor = corpus.aliases().clueRef(ClueKind.METHOD, "method:origin");
    var task =
        new OntologyScopeReader.Task(
            "T1",
            OntologyScopeReader.TaskKind.LINK,
            OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE,
            List.of(),
            List.of(),
            List.of(anchor));
    var question =
        new OntologyScopeReader.Question(
            "Q1",
            "Inspect saved references",
            List.of(corpus.aliases().entryRef(entryId)),
            List.of(anchor),
            List.of(task));
    var bundle = OntologyCoherentLinkBundle.prepare(corpus, question, task, 100000, 500000);
    assertThat(bundle.packet().units())
        .filteredOn(unit -> unit.kind() == UnitKind.JAVA_METHOD)
        .extracting(OntologyReadingPacket.PackedUnit::originalId)
        .containsExactlyInAnyOrder("method:origin", "method:save");
  }
}
